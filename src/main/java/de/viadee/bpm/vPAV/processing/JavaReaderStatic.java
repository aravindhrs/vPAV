/**
 * BSD 3-Clause License
 *
 * Copyright © 2019, viadee Unternehmensberatung AG
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.viadee.bpm.vPAV.processing;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import de.viadee.bpm.vPAV.FileScanner;
import de.viadee.bpm.vPAV.RuntimeConfig;
import de.viadee.bpm.vPAV.constants.BpmnConstants;
import de.viadee.bpm.vPAV.processing.code.flow.BpmnElement;
import de.viadee.bpm.vPAV.processing.code.flow.ControlFlowGraph;
import de.viadee.bpm.vPAV.processing.code.flow.Node;
import de.viadee.bpm.vPAV.processing.model.data.*;
import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants;
import soot.*;
import soot.JastAddJ.Opt;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.options.Options;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.ClassicCompleteBlockGraph;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

class JavaReaderStatic {

	private static final Logger LOGGER = Logger.getLogger(JavaReaderStatic.class.getName());

	private VariablesExtractor variablesExtractor;

	JavaReaderStatic() {
		setupSoot();
		variablesExtractor = new VariablesExtractor(this);
	}

	/**
	 * Checks a java delegate for process variable references with static code
	 * analysis (read/write/delete).
	 * <p>
	 * Constraints: names, which only could be determined at runtime, can't be
	 * analyzed. e.g. execution.setVariable(execution.getActivityId() + "-" +
	 * execution.getEventName(), true)
	 *
	 * @param fileScanner      FileScanner
	 * @param classFile        Name of the class
	 * @param element          Bpmn element
	 * @param chapter          ElementChapter
	 * @param fieldType        KnownElementFieldType
	 * @param scopeId          Scope of the element
	 * @param controlFlowGraph Control flow graph
	 * @return Map of process variables from the referenced delegate
	 */
	ListMultimap<String, ProcessVariableOperation> getVariablesFromJavaDelegate(final FileScanner fileScanner,
																				final String classFile, final BpmnElement element, final ElementChapter chapter,
																				final KnownElementFieldType fieldType, final String scopeId, final ControlFlowGraph controlFlowGraph) {

		final ListMultimap<String, ProcessVariableOperation> variables = ArrayListMultimap.create();

		if (classFile != null && classFile.trim().length() > 0) {

			final String sootPath = FileScanner.getSootPath();

			System.setProperty("soot.class.path", sootPath);

			final Set<String> classPaths = fileScanner.getJavaResourcesFileInputStream();

			if (element.getBaseElement().getAttributeValueNs(BpmnModelConstants.CAMUNDA_NS,
					BpmnConstants.ATTR_VAR_MAPPING_CLASS) != null
					|| element.getBaseElement().getAttributeValueNs(BpmnModelConstants.CAMUNDA_NS,
					BpmnConstants.ATTR_VAR_MAPPING_DELEGATE) != null) {
				// Delegate Variable Mapping
				variables.putAll(classFetcher(classPaths, classFile, "mapInputVariables", classFile, element,
						ElementChapter.InputImplementation, fieldType, scopeId, controlFlowGraph));
				variables.putAll(classFetcher(classPaths, classFile, "mapOutputVariables", classFile, element,
						ElementChapter.OutputImplementation, fieldType, scopeId, controlFlowGraph));
			} else {
				// Java Delegate or Listener
				variables.putAll(classFetcher(classPaths, classFile, "execute", classFile, element, chapter, fieldType,
						scopeId, controlFlowGraph));
				variables.putAll(classFetcher(classPaths, classFile, "notify", classFile, element, chapter, fieldType,
						scopeId, controlFlowGraph));
			}
		}
		return variables;
	}


	/**
	 * Retrieves variables from a class
	 *
	 * @param className        Name of the class that potentially declares process variables
	 * @param element          BpmnElement
	 * @param resourceFilePath Path of the BPMN model
	 * @param entryPoint       Current entry point
	 * @return Map of process variable operations
	 */
	ListMultimap<String, ProcessVariableOperation> getVariablesFromClass(String className, final BpmnElement element,
																		 final String resourceFilePath, final EntryPoint entryPoint) {

		final ListMultimap<String, ProcessVariableOperation> initialOperations = ArrayListMultimap.create();

		if (className != null && className.trim().length() > 0) {
			className = cleanString(className, true);
			SootClass sootClass = Scene.v().forceResolve(className, SootClass.SIGNATURES);

			if (sootClass != null) {
				sootClass.setApplicationClass();
				Scene.v().loadNecessaryClasses();
				for (SootMethod method : sootClass.getMethods()) {
					if (method.getName().equals(entryPoint.getMethodName())) {
						final Body body = method.retrieveActiveBody();
						initialOperations.putAll(variablesExtractor.checkWriteAccess(body, element, resourceFilePath, entryPoint));
					}
				}
			}
		}
		return initialOperations;
	}

	/**
	 * Starting by the main JavaDelegate, statically analyses the classes
	 * implemented for the bpmn element.
	 *
	 * @param classPaths       Set of classes that is included in inter-procedural analysis
	 * @param className        Name of currently analysed class
	 * @param methodName       Name of currently analysed method
	 * @param classFile        Location path of class
	 * @param element          Bpmn element
	 * @param chapter          ElementChapter
	 * @param fieldType        KnownElementFieldType
	 * @param scopeId          Scope of the element
	 * @param controlFlowGraph Control flow graph
	 * @return Map of process variables for a given class
	 */
	private ListMultimap<String, ProcessVariableOperation> classFetcher(final Set<String> classPaths,
																		final String className, final String methodName, final String classFile, final BpmnElement element,
																		final ElementChapter chapter, final KnownElementFieldType fieldType, final String scopeId,
																		final ControlFlowGraph controlFlowGraph) {

		ListMultimap<String, ProcessVariableOperation> processVariables = ArrayListMultimap.create();

		OutSetCFG outSet = new OutSetCFG(new ArrayList<>());

		List<Value> args = new ArrayList<>();

		classFetcherRecursive(classPaths, className, methodName, classFile, element, chapter, fieldType, scopeId,
				outSet, null, "", args, controlFlowGraph, null);

		if (outSet.getAllProcessVariables().size() > 0) {
			processVariables.putAll(outSet.getAllProcessVariables());
		}

		return processVariables;
	}

	/**
	 * Recursively follow call hierarchy and obtain method bodies
	 *
	 * @param classPaths       Set of classes that is included in inter-procedural analysis
	 * @param className        Name of currently analysed class
	 * @param methodName       Name of currently analysed method
	 * @param classFile        Location path of class
	 * @param element          Bpmn element
	 * @param chapter          ElementChapter
	 * @param fieldType        KnownElementFieldType
	 * @param scopeId          Scope of the element
	 * @param outSet           Callgraph information
	 * @param originalBlock    VariableBlock
	 * @param assignmentStmt   Assignment statement (left side)
	 * @param args             List of arguments
	 * @param controlFlowGraph Control flow graph
	 */
	void classFetcherRecursive(final Set<String> classPaths, String className, final String methodName,
							   final String classFile, final BpmnElement element, final ElementChapter chapter,
							   final KnownElementFieldType fieldType, final String scopeId, OutSetCFG outSet,
							   final VariableBlock originalBlock, final String assignmentStmt, final List<Value> args,
							   final ControlFlowGraph controlFlowGraph, final SootMethod sootMethod) {

		className = cleanString(className, true);
		SootClass sootClass = Scene.v().forceResolve(className, SootClass.SIGNATURES);

		if (sootClass != null) {

			sootClass.setApplicationClass();
			Scene.v().loadNecessaryClasses();

			// Retrieve the method and its body based on the used interface
			List<Type> parameterTypes = new ArrayList<>();
			RefType delegateExecutionType = RefType.v("org.camunda.bpm.engine.delegate.DelegateExecution");
			RefType activityExecutionType = RefType.v("org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution");
			RefType delegateTaskType = RefType.v("org.camunda.bpm.engine.delegate.DelegateTask");
			RefType mapVariablesType = RefType.v("org.camunda.bpm.engine.variable.VariableMap");
			VoidType returnType = VoidType.v();

			switch (methodName) {
				case "execute":
					for (SootClass clazz : sootClass.getInterfaces()) {
						if (clazz.getName()
								.equals("org.camunda.bpm.engine.impl.bpmn.behavior.AbstractBpmnActivityBehavior")) {
							parameterTypes.add(activityExecutionType);
						} else if (clazz.getName().equals("org.camunda.bpm.engine.delegate.JavaDelegate")) {
							parameterTypes.add(delegateExecutionType);
						}
					}
					break;
				case "notify":
					for (SootClass clazz : sootClass.getInterfaces()) {
						if (clazz.getName().equals("org.camunda.bpm.engine.delegate.TaskListener")) {
							parameterTypes.add(delegateTaskType);
						} else if (clazz.getName().equals("org.camunda.bpm.engine.delegate.ExecutionListener")) {
							parameterTypes.add(delegateExecutionType);
						}
					}
					break;
				case "mapInputVariables":
				case "mapOutputVariables":
					parameterTypes.add(delegateExecutionType);
					parameterTypes.add(mapVariablesType);
					break;
				default:
					retrieveCustomMethod(sootClass, classPaths, classFile, element, chapter, fieldType, scopeId, outSet,
							originalBlock, assignmentStmt, args, controlFlowGraph, sootMethod, methodName);
					return;
			}
			retrieveMethod(classPaths, methodName, classFile, element, chapter, fieldType, scopeId, outSet,
					originalBlock, sootClass, parameterTypes, returnType, assignmentStmt, args, controlFlowGraph);

		} else {
			LOGGER.warning("Class " + classFile + " was not found by Soot");
		}

	}

	/**
	 * Retrieve given camunda methods to obtain a Soot representation of said method
	 * to analyse its body
	 *
	 * @param classPaths     Set of classes that is included in inter-procedural analysis
	 * @param methodName     Name of currently analysed method
	 * @param classFile      Location path of class
	 * @param element        Bpmn element
	 * @param chapter        ElementChapter
	 * @param fieldType      KnownElementFieldType
	 * @param scopeId        Scope of the element
	 * @param outSet         Callgraph information
	 * @param originalBlock  VariableBlock
	 * @param sootClass      Soot representation of given class
	 * @param parameterTypes Soot representation of parameters
	 * @param returnType     Soot Representation of return type
	 */
	private void retrieveMethod(final Set<String> classPaths, final String methodName, final String classFile,
								final BpmnElement element, final ElementChapter chapter, final KnownElementFieldType fieldType,
								final String scopeId, OutSetCFG outSet, final VariableBlock originalBlock, final SootClass sootClass,
								final List<Type> parameterTypes, final VoidType returnType, final String assignmentStmt,
								final List<Value> args, final ControlFlowGraph controlFlowGraph) {

		SootMethod method = sootClass.getMethodUnsafe(methodName, parameterTypes, returnType);

		if (method != null) {
			fetchMethodBody(classPaths, classFile, element, chapter, fieldType, scopeId, outSet, originalBlock, method,
					assignmentStmt, args, controlFlowGraph);
		} else {
			method = sootClass.getMethodByNameUnsafe(methodName);
			if (method != null) {
				fetchMethodBody(classPaths, classFile, element, chapter, fieldType, scopeId, outSet, originalBlock,
						method, assignmentStmt, args, controlFlowGraph);
			} else {
				LOGGER.warning("In class " + classFile + " - " + methodName + " method was not found by Soot");
			}
		}
	}

	/**
	 * Retrieve given custom methods to obtain a Soot representation of said method
	 * to analyse its body
	 *
	 * @param sootClass     Soot representation of class
	 * @param classPaths    Set of classes that is included in inter-procedural analysis
	 * @param classFile     Location path of class
	 * @param element       Bpmn element
	 * @param chapter       ElementChapter
	 * @param fieldType     KnownElementFieldType
	 * @param scopeId       Scope of the element
	 * @param outSet        Callgraph information
	 * @param originalBlock VariableBlock
	 */
	private void retrieveCustomMethod(final SootClass sootClass, final Set<String> classPaths, final String classFile,
									  final BpmnElement element, final ElementChapter chapter, final KnownElementFieldType fieldType,
									  final String scopeId, OutSetCFG outSet, final VariableBlock originalBlock, final String assignmentStmt,
									  final List<Value> args, final ControlFlowGraph controlFlowGraph, final SootMethod sootMethod,
									  final String methodName) {

		if (sootMethod != null) {
			fetchMethodBody(classPaths, classFile, element, chapter, fieldType, scopeId, outSet, originalBlock,
					sootMethod, assignmentStmt, args, controlFlowGraph);
		} else {
			for (SootMethod method : sootClass.getMethods()) {
				if (method != null) {
					if (method.getName().equals(methodName)) {
						fetchMethodBody(classPaths, classFile, element, chapter, fieldType, scopeId, outSet,
								originalBlock, method, assignmentStmt, args, controlFlowGraph);
					}
				}
			}
		}
	}

	/**
	 * Retrieve given custom methods to obtain a Soot representation of said method
	 * to analyse its body
	 *
	 * @param classPaths    Set of classes that is included in inter-procedural analysis
	 * @param classFile     Location path of class
	 * @param element       Bpmn element
	 * @param chapter       ElementChapter
	 * @param fieldType     KnownElementFieldType
	 * @param scopeId       Scope of the element
	 * @param outSet        Callgraph information
	 * @param originalBlock VariableBlock
	 * @param method        Soot representation of a given method
	 */
	private void fetchMethodBody(final Set<String> classPaths, final String classFile, final BpmnElement element,
								 final ElementChapter chapter, final KnownElementFieldType fieldType, final String scopeId, OutSetCFG outSet,
								 final VariableBlock originalBlock, final SootMethod method, final String assignmentStmt,
								 final List<Value> args, final ControlFlowGraph controlFlowGraph) {

		final Body body = method.retrieveActiveBody();

		BlockGraph graph = new ClassicCompleteBlockGraph(body);
		// Prepare call graph for inter-procedural recursive call
		List<SootMethod> entryPoints = new ArrayList<>();
		entryPoints.add(method);
		Scene.v().setEntryPoints(entryPoints);

		PackManager.v().getPack("cg").apply();
		CallGraph cg = Scene.v().getCallGraph();

		final List<Block> graphHeads = graph.getHeads();

		for (int i = 0; i < graphHeads.size(); i++) {
			outSet = graphIterator(classPaths, cg, graph, outSet, element, chapter, fieldType, classFile, scopeId,
					originalBlock, assignmentStmt, args, controlFlowGraph);
		}
	}

	/**
	 * Iterate through the control-flow graph with an iterative data-flow analysis
	 * logic
	 *
	 * @param classPaths    Set of classes that is included in inter-procedural analysis
	 * @param cg            Soot ControlFlowGraph
	 * @param graph         Control Flow graph of method
	 * @param outSet        OUT set of CFG
	 * @param element       Bpmn element
	 * @param chapter       ElementChapter
	 * @param fieldType     KnownElementFieldType
	 * @param filePath      ResourceFilePath for ProcessVariableOperation
	 * @param scopeId       Scope of BpmnElement
	 * @param originalBlock VariableBlock
	 * @return OutSetCFG which contains data flow information
	 */
	private OutSetCFG graphIterator(final Set<String> classPaths, final CallGraph cg, final BlockGraph graph,
									OutSetCFG outSet, final BpmnElement element, final ElementChapter chapter,
									final KnownElementFieldType fieldType, final String filePath, final String scopeId,
									VariableBlock originalBlock, final String assignmentStmt, final List<Value> args,
									final ControlFlowGraph controlFlowGraph) {

		for (Block block : graph.getBlocks()) {
			Node node = new Node(controlFlowGraph, element, block, chapter);
			controlFlowGraph.addNode(node);

			// Collect the functions Unit by Unit via the blockIterator
			final VariableBlock vb = variablesExtractor.blockIterator(classPaths, cg, block, outSet, element, chapter, fieldType, filePath,
					scopeId, originalBlock, assignmentStmt, args, controlFlowGraph, node);

			// depending if outset already has that Block, only add variables,
			// if not, then add the whole vb
			if (outSet.getVariableBlock(vb.getBlock()) == null) {
				outSet.addVariableBlock(vb);
			}
		}

		return outSet;
	}

	/**
	 * Strips unnecessary characters and returns cleaned name
	 *
	 * @param className Classname to be stripped of unused chars
	 * @return cleaned String
	 */
	private String cleanString(String className, boolean dot) {
		className = ProcessVariablesScanner.cleanString(className, dot);
		return className;
	}

	static void setupSoot() {
		final String sootPath = FileScanner.getSootPath();
		System.setProperty("soot.class.path", sootPath);
		G.reset();
	/*	if(RuntimeConfig.getInstance().sootUseClasspath()){
			Options.v().set_soot_classpath(sootPath);
		//	Options.v().set_process_dir(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));

		}
		else {
			// Use modulepath
		//	Options.v().set_prepend_classpath(true);
			Options.v().set_soot_modulepath("VIRTUAL_FS_FOR_JDK");
		//	Options.v().set_process_dir(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));

		} */
		Options.v().set_prepend_classpath(true);
		Options.v().set_soot_modulepath("VIRTUAL_FS_FOR_JDK");
		Options.v().set_process_dir(Arrays.asList(System.getProperty("java.class.path").split(File.pathSeparator)));

		Options.v().set_whole_program(true);
		Options.v().set_allow_phantom_refs(true);
	/*	ArrayList<String> excludedClasses = new ArrayList<>();
		excludedClasses.add("java.*");
		excludedClasses.add("sun.*");
		excludedClasses.add("jdk.*");
		excludedClasses.add("javax.*");
		Options.v().set_exclude(excludedClasses);
		Options.v().set_no_bodies_for_excluded(true); */
	//	Scene.v().extendSootClassPath(Scene.v().defaultClassPath());
	}
}
