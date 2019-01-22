/**
 * BSD 3-Clause License
 *
 * Copyright © 2018, viadee Unternehmensberatung AG
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

import de.viadee.bpm.vPAV.FileScanner;
import de.viadee.bpm.vPAV.constants.CamundaMethodServices;
import de.viadee.bpm.vPAV.processing.model.data.*;
import org.camunda.bpm.engine.variable.VariableMap;
import soot.*;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeStmt;
import soot.jimple.StringConstant;
import soot.jimple.internal.JInterfaceInvokeExpr;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.options.Options;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.ClassicCompleteBlockGraph;

import java.util.*;
import java.util.logging.Logger;

public class JavaReaderStatic implements JavaReader {

	public static final Logger LOGGER = Logger.getLogger(JavaReaderStatic.class.getName());

	/**
	 * Checks a java delegate for process variable references with static code
	 * analysis (read/write/delete).
	 *
	 * Constraints: names, which only could be determined at runtime, can't be
	 * analyzed. e.g. execution.setVariable(execution.getActivityId() + "-" +
	 * execution.getEventName(), true)
	 *
	 * @param fileScanner
	 *            FileScanner
	 * @param classFile
	 *            Name of the class
	 * @param element
	 *            Bpmn element
	 * @param chapter
	 *            ElementChapter
	 * @param fieldType
	 *            KnownElementFieldType
	 * @param scopeId
	 *            Scope of the element
	 * @return Map of process variables from the referenced delegate
	 */
	public LinkedHashMap<String, ProcessVariableOperation> getVariablesFromJavaDelegate(final FileScanner fileScanner,
			final String classFile, final BpmnElement element, final ElementChapter chapter,
			final KnownElementFieldType fieldType, final String scopeId) {

		final LinkedHashMap<String, ProcessVariableOperation> variables = new LinkedHashMap<>();

		if (classFile != null && classFile.trim().length() > 0) {

			final String sootPath = FileScanner.getSootPath();

			System.setProperty("soot.class.path", sootPath);

			final Set<String> classPaths = fileScanner.getJavaResourcesFileInputStream();
			final ArrayList<String> delegateMethods = new ArrayList<>();
			delegateMethods.add("execute");
			delegateMethods.add("notify");
			delegateMethods.add("mapInputVariables");
			delegateMethods.add("mapOutputVariables");

			for (String delegateMethodName : delegateMethods) {
				variables.putAll(classFetcher(classPaths, classFile, delegateMethodName, classFile, element, chapter,
						fieldType, scopeId));
			}
		}
		return variables;
	}

	/**
	 *
	 * Retrieves variables from a class
	 *
	 * @param className
	 *            Name of the class that potentially declares process variables
	 * @param scanner
	 *            OuterProcessVariablesScanner
	 * @param element
	 *            BpmnElement
	 * @param resourceFilePath
	 *            Path of the BPMN model
	 * @return Map of process variable operations
	 */
	public LinkedHashMap<String, ProcessVariableOperation> getVariablesFromClass(String className,
			final ProcessVariablesScanner scanner, final BpmnElement element, final String resourceFilePath,
			final Map.Entry<String, Map<String, String>> map) {

		final LinkedHashMap<String, ProcessVariableOperation> initialOperations = new LinkedHashMap<>();

		if (className != null && className.trim().length() > 0) {
			final String sootPath = FileScanner.getSootPath();
			System.setProperty("soot.class.path", sootPath);

			className = cleanString(className, true);

			Options.v().set_whole_program(true);
			Options.v().set_allow_phantom_refs(true);

			SootClass sootClass = Scene.v().forceResolve(className, SootClass.SIGNATURES);

			if (sootClass != null) {
				sootClass.setApplicationClass();
				Scene.v().loadNecessaryClasses();
				for (SootMethod method : sootClass.getMethods()) {
					for (Map.Entry<String, String> entry : map.getValue().entrySet()) {
						if (method.getName().equals(entry.getKey())) {
							final Body body = method.retrieveActiveBody();
							initialOperations.putAll(checkWriteAccess(body, element, resourceFilePath, map));
						}
					}
				}
			}
		}
		return initialOperations;
	}

	/**
	 * 
	 * Checks for WRITE operations on process variables
	 * 
	 * @param body
	 *            Soot representation of a method's body
	 * @param element
	 *            BpmnElement
	 * @param resourceFilePath
	 *            Path of the BPMN model
	 * @return Map of process variable operations
	 */
	private Map<String, ProcessVariableOperation> checkWriteAccess(final Body body, final BpmnElement element,
			final String resourceFilePath, final Map.Entry<String, Map<String, String>> map) {

		final LinkedHashMap<String, ProcessVariableOperation> initialOperations = new LinkedHashMap<>();

		for (Map.Entry<String, String> entry : map.getValue().entrySet()) {
			if (body.getMethod().getName().equals(entry.getKey())) {
				final PatchingChain<Unit> pc = body.getUnits();
				String assignment = "";
				String invoke = "";
				
				for (Unit unit : pc) {
					if (unit instanceof AssignStmt) {
						final String rightBox = ((AssignStmt) unit).getRightOpBox().getValue().toString();
						final String leftBox = ((AssignStmt) unit).getLeftOpBox().getValue().toString();
						
						if (rightBox.contains(CamundaMethodServices.VARIABLE_MAP + " createVariables()")) {
							assignment = leftBox;
						}		
						
						if (rightBox.contains(map.getKey()) && rightBox.contains(invoke)) {
							return initialOperations;
						}			
						
						if (((AssignStmt) unit).getRightOpBox().getValue() instanceof JInterfaceInvokeExpr) {
							final JInterfaceInvokeExpr expr = (JInterfaceInvokeExpr) ((AssignStmt) unit)
									.getRightOpBox().getValue();
							if (expr != null) {
								if (expr.getMethodRef().getDeclaringClass().equals(Scene.v()
										.forceResolve(VariableMap.class.getName(), SootClass.SIGNATURES))) {
									initialOperations.putAll(parseInitialExpression(expr, element, resourceFilePath));
									invoke = leftBox;
								}
                                if (checkArgBoxes(map, assignment, invoke, expr)) return initialOperations;
                            }
						}
					}	
					if (unit instanceof InvokeStmt) {
						if (((InvokeStmt) unit).getInvokeExprBox().getValue() instanceof JInterfaceInvokeExpr) {
							final JInterfaceInvokeExpr expr = (JInterfaceInvokeExpr) ((InvokeStmt) unit)
									.getInvokeExprBox().getValue();
							if (expr != null) {
                                if (checkArgBoxes(map, assignment, invoke, expr)) return initialOperations;
                            }
						}
					}
				}
			}
		}		
		return initialOperations;
	}

    /**
     *
     * Check whether or not the second or third argument contain a reference to the variable map
     *
     * @param map Current entry
     * @param assignment Current assigned variable
     * @param invoke Current invocation
     * @param expr Current expression
     * @return True/False based on whether the second or third argument refers to the variable map
     */
    private boolean checkArgBoxes(Map.Entry<String, Map<String, String>> map, String assignment, String invoke, JInterfaceInvokeExpr expr) {
        if (expr.getMethodRef().getName().equals(map.getKey())) {
            if (!assignment.isEmpty()) {
                if (expr.getArgBox(1).getValue().toString().equals(invoke)) {
                    return true;
                }
                if (expr.getArgBox(2).getValue().toString().equals(invoke)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
	 *
	 * Starting by the main JavaDelegate, statically analyses the classes
	 * implemented for the bpmn element.
	 *
	 * @param classPaths
	 *            Set of classes that is included in inter-procedural analysis
	 * @param className
	 *            Name of currently analysed class
	 * @param methodName
	 *            Name of currently analysed method
	 * @param classFile
	 *            Location path of class
	 * @param element
	 *            Bpmn element
	 * @param chapter
	 *            ElementChapter
	 * @param fieldType
	 *            KnownElementFieldType
	 * @param scopeId
	 *            Scope of the element
	 * @return Map of process variables for a given class
	 */
	public Map<String, ProcessVariableOperation> classFetcher(final Set<String> classPaths, final String className,
			final String methodName, final String classFile, final BpmnElement element, final ElementChapter chapter,
			final KnownElementFieldType fieldType, final String scopeId) {

		Map<String, ProcessVariableOperation> processVariables = new HashMap<String, ProcessVariableOperation>();

		OutSetCFG outSet = new OutSetCFG(new ArrayList<VariableBlock>());

		classFetcherRecursive(classPaths, className, methodName, classFile, element, chapter, fieldType, scopeId,
				outSet, null);

		processVariables.putAll(outSet.getAllProcessVariables());

		// Add Java code level anomalies to BpmnElement so later it is included into
		try {
			addAnomaliesFoundInSourceCode(element, outSet);
		} catch (Exception e) {
			// TODO: handle exception
		}

		return processVariables;

	}

	/**
	 * Recursively follow call hierarchy and obtain method bodies
	 * 
	 * @param classPaths
	 *            Set of classes that is included in inter-procedural analysis
	 * @param className
	 *            Name of currently analysed class
	 * @param methodName
	 *            Name of currently analysed method
	 * @param classFile
	 *            Location path of class
	 * @param element
	 *            Bpmn element
	 * @param chapter
	 *            ElementChapter
	 * @param fieldType
	 *            KnownElementFieldType
	 * @param scopeId
	 *            Scope of the element
	 * @param outSet
	 *            Callgraph information
	 * @param originalBlock
	 *            VariableBlock
	 * @return OutSetCFG which contains data flow information
	 */
	public OutSetCFG classFetcherRecursive(final Set<String> classPaths, String className, final String methodName,
			final String classFile, final BpmnElement element, final ElementChapter chapter,
			final KnownElementFieldType fieldType, final String scopeId, OutSetCFG outSet,
			final VariableBlock originalBlock) {

		className = cleanString(className, true);

		Options.v().set_whole_program(true);
		Options.v().set_allow_phantom_refs(true);

		SootClass sootClass = Scene.v().forceResolve(className, SootClass.SIGNATURES);

		if (sootClass != null) {

			sootClass.setApplicationClass();
			Scene.v().loadNecessaryClasses();

			// Retrieve the method and its body based on the used interface
			List<Type> parameterTypes = new ArrayList<Type>();
			RefType delegateExecutionType = RefType.v("org.camunda.bpm.engine.delegate.DelegateExecution");
			RefType delegateTaskType = RefType.v("org.camunda.bpm.engine.delegate.DelegateTask");
			RefType mapVariablesType = RefType.v("org.camunda.bpm.engine.variable.VariableMap");
			VoidType returnType = VoidType.v();

			switch (methodName) {
			case "execute":
				parameterTypes.add((Type) delegateExecutionType);
				outSet = retrieveMethod(classPaths, className, methodName, classFile, element, chapter, fieldType,
						scopeId, outSet, originalBlock, sootClass, parameterTypes, returnType);
				break;
			case "notify":
				for (SootClass clazz : sootClass.getInterfaces()) {
					if (clazz.getName().equals("org.camunda.bpm.engine.delegate.TaskListener")) {
						parameterTypes.add((Type) delegateTaskType);
					} else if (clazz.getName().equals("org.camunda.bpm.engine.delegate.ExecutionListener")) {
						parameterTypes.add((Type) delegateExecutionType);
					}
				}
				outSet = retrieveMethod(classPaths, className, methodName, classFile, element, chapter, fieldType,
						scopeId, outSet, originalBlock, sootClass, parameterTypes, returnType);
				break;
			case "mapInputVariables":
				parameterTypes.add((Type) delegateExecutionType);
				parameterTypes.add((Type) mapVariablesType);
				outSet = retrieveMethod(classPaths, className, methodName, classFile, element, chapter, fieldType,
						scopeId, outSet, originalBlock, sootClass, parameterTypes, returnType);
				break;
			case "mapOutputVariables":
				parameterTypes.add((Type) delegateExecutionType);
				parameterTypes.add((Type) mapVariablesType);
				outSet = retrieveMethod(classPaths, className, methodName, classFile, element, chapter, fieldType,
						scopeId, outSet, originalBlock, sootClass, parameterTypes, returnType);
				break;
			default:
				outSet = retrieveCustomMethod(sootClass, classPaths, className, methodName, classFile, element, chapter,
						fieldType, scopeId, outSet, originalBlock);
				break;
			}

		} else {
			LOGGER.warning("Class " + classFile + " was not found by Soot");
		}

		return outSet;

	}

	/**
	 * 
	 * Retrieve given camunda methods to obtain a Soot representation of said method
	 * to analyse its body
	 * 
	 * @param classPaths
	 *            Set of classes that is included in inter-procedural analysis
	 * @param className
	 *            Name of currently analysed class
	 * @param methodName
	 *            Name of currently analysed method
	 * @param classFile
	 *            Location path of class
	 * @param element
	 *            Bpmn element
	 * @param chapter
	 *            ElementChapter
	 * @param fieldType
	 *            KnownElementFieldType
	 * @param scopeId
	 *            Scope of the element
	 * @param outSet
	 *            Callgraph information
	 * @param originalBlock
	 *            VariableBlock
	 * @param sootClass
	 *            Soot representation of given class
	 * @param parameterTypes
	 *            Soot representation of parameters
	 * @param returnType
	 *            Soot Representation of return type
	 * @return OutSetCFG which contains data flow information
	 */
	private OutSetCFG retrieveMethod(final Set<String> classPaths, String className, final String methodName,
			final String classFile, final BpmnElement element, final ElementChapter chapter,
			final KnownElementFieldType fieldType, final String scopeId, OutSetCFG outSet,
			final VariableBlock originalBlock, final SootClass sootClass, final List<Type> parameterTypes,
			final VoidType returnType) {

		SootMethod method = sootClass.getMethodUnsafe(methodName, parameterTypes, returnType);

		if (method != null) {
			outSet = fetchMethodBody(classPaths, className, classFile, element, chapter, fieldType, scopeId,
					outSet, originalBlock, method);
		} else {
			method = sootClass.getMethodByNameUnsafe(methodName);
			if (method != null) {
				outSet = fetchMethodBody(classPaths, className, classFile, element, chapter, fieldType,
						scopeId, outSet, originalBlock, method);
			} else {
				LOGGER.warning("In class " + classFile + " - " + methodName + " method was not found by Soot");
			}
		}
		return outSet;
	}

	/**
	 * 
	 * Retrieve given custom methods to obtain a Soot representation of said method
	 * to analyse its body
	 * 
	 * @param classPaths
	 *            Set of classes that is included in inter-procedural analysis
	 * @param className
	 *            Name of currently analysed class
	 * @param methodName
	 *            Name of currently analysed method
	 * @param classFile
	 *            Location path of class
	 * @param element
	 *            Bpmn element
	 * @param chapter
	 *            ElementChapter
	 * @param fieldType
	 *            KnownElementFieldType
	 * @param scopeId
	 *            Scope of the element
	 * @param outSet
	 *            Callgraph information
	 * @param originalBlock
	 *            VariableBlock
	 * @param sootClass
	 *            Soot representation of given class
	 * @return OutSetCFG which contains data flow information
	 */
	private OutSetCFG retrieveCustomMethod(final SootClass sootClass, final Set<String> classPaths, String className,
			final String methodName, final String classFile, final BpmnElement element, final ElementChapter chapter,
			final KnownElementFieldType fieldType, final String scopeId, OutSetCFG outSet,
			final VariableBlock originalBlock) {

		for (SootMethod method : sootClass.getMethods()) {
			if (method.getName().equals(methodName)) {
				outSet = fetchMethodBody(classPaths, className, classFile, element, chapter, fieldType,
						scopeId, outSet, originalBlock, method);
			}
		}
		return outSet;
	}

	/**
	 * 
	 * Retrieve given custom methods to obtain a Soot representation of said method
	 * to analyse its body
	 * 
	 * @param classPaths
	 *            Set of classes that is included in inter-procedural analysis
	 * @param className
	 *            Name of currently analysed class
	 * @param classFile
	 *            Location path of class
	 * @param element
	 *            Bpmn element
	 * @param chapter
	 *            ElementChapter
	 * @param fieldType
	 *            KnownElementFieldType
	 * @param scopeId
	 *            Scope of the element
	 * @param outSet
	 *            Callgraph information
	 * @param originalBlock
	 *            VariableBlock
	 * @param method
	 *            Soot representation of a given method
	 * @return OutSetCFG which contains data flow information
	 */
	private OutSetCFG fetchMethodBody(final Set<String> classPaths, final String className,
			final String classFile, final BpmnElement element, final ElementChapter chapter,
			final KnownElementFieldType fieldType, final String scopeId, OutSetCFG outSet,
			final VariableBlock originalBlock, final SootMethod method) {

		final Body body = method.retrieveActiveBody();

		BlockGraph graph = new ClassicCompleteBlockGraph(body);

		// Prepare call graph for inter-procedural recursive call
		List<SootMethod> entryPoints = new ArrayList<SootMethod>();
		entryPoints.add(method);
		Scene.v().setEntryPoints(entryPoints);

		PackManager.v().getPack("cg").apply();
		CallGraph cg = Scene.v().getCallGraph();

		final List<Block> graphHeads = graph.getHeads();

		for (Block head : graphHeads) {
			outSet = graphIterator(classPaths, cg, graph, outSet, element, chapter, fieldType,
					classFile, scopeId, originalBlock, className);
		}

		return outSet;
	}

	/**
	 * Iterate through the control-flow graph with an iterative data-flow analysis
	 * logic
	 *
	 * @param classPaths
	 *            Set of classes that is included in inter-procedural analysis
	 * @param cg
	 *            Soot CallGraph
	 * @param graph
	 *            Control Flow graph of method
	 * @param outSet
	 *            OUT set of CFG
	 * @param element
	 *            Bpmn element
	 * @param chapter
	 *            ElementChapter
	 * @param fieldType
	 *            KnownElementFieldType
	 * @param filePath
	 *            ResourceFilePath for ProcessVariableOperation
	 * @param scopeId
	 *            Scope of BpmnElement
	 * @param originalBlock
	 *            VariableBlock
	 * @param oldClassName
	 *            Classname
	 * @return OutSetCFG which contains data flow information
	 */
	private OutSetCFG graphIterator(final Set<String> classPaths, CallGraph cg, BlockGraph graph,
            OutSetCFG outSet, final BpmnElement element, final ElementChapter chapter,
			final KnownElementFieldType fieldType, final String filePath, final String scopeId,
			VariableBlock originalBlock, String oldClassName) {

		final Iterator<Block> graphIterator = graph.iterator();

		Block block;
		while (graphIterator.hasNext()) {
			block = graphIterator.next();

			// Collect the functions Unit by Unit via the blockIterator
			final VariableBlock vb = blockIterator(classPaths, cg, block, outSet, element, chapter, fieldType, filePath,
					scopeId, originalBlock, oldClassName);

			// depending if outset already has that Block, only add varibles,
			// if not, then add the whole vb

			if (outSet.getVariableBlock(vb.getBlock()) == null) {
				outSet.addVariableBlock(vb);
			}
		}

		return outSet;
	}

	/**
	 * Iterator through the source code line by line, collecting the
	 * ProcessVariables Camunda methods are interface invocations appearing either
	 * in Assign statement or Invoke statement Constraint: Only String constants can
	 * be precisely recognized.
	 *
	 * @param classPaths
	 *            Set of classes that is included in inter-procedural analysis
	 * @param cg
	 *            Soot CallGraph
	 * @param block
	 *            Block from CFG
	 * @param outSet
	 *            OUT set of CFG
	 * @param element
	 *            BpmnElement
	 * @param chapter
	 *            ElementChapter
	 * @param fieldType
	 *            KnownElementFieldType
	 * @param filePath
	 *            ResourceFilePath for ProcessVariableOperation
	 * @param scopeId
	 *            Scope of BpmnElement
	 * @param variableBlock
	 *            VariableBlock
	 * @param oldClassName
	 *            Classname
	 * @return VariableBlock
	 */
	private VariableBlock blockIterator(final Set<String> classPaths, final CallGraph cg, final Block block,
			OutSetCFG outSet, final BpmnElement element, final ElementChapter chapter,
			final KnownElementFieldType fieldType, final String filePath, final String scopeId,
			VariableBlock variableBlock, String oldClassName) {

		if (variableBlock == null) {
			variableBlock = new VariableBlock(block, new ArrayList<ProcessVariableOperation>());
		}

		final Iterator<Unit> unitIt = block.iterator();

		Unit unit;
		while (unitIt.hasNext()) {
			unit = unitIt.next();

			if (cg != null & (unit instanceof InvokeStmt || unit instanceof AssignStmt)) {

				Iterator<soot.jimple.toolkits.callgraph.Edge> sources = cg.edgesOutOf(unit);

				Edge src;
				while (sources.hasNext()) {
					src = sources.next();
					String methodName = src.tgt().getName();
					String className = src.tgt().getDeclaringClass().getName();
					className = cleanString(className, false);

					if (methodName.equals("getLogger")
							|| (methodName.equals("<clinit>") && cleanString(className, true).equals(oldClassName))) {
						continue;
					}

					if (classPaths.contains(className) || className.contains("$")) {
						G.reset();
						classFetcherRecursive(classPaths, className, methodName, className, element, chapter, fieldType,
								scopeId, outSet, variableBlock);

					}
				}

				if (unit instanceof InvokeStmt) {

					if (((InvokeStmt) unit).getInvokeExprBox().getValue() instanceof JInterfaceInvokeExpr) {

						JInterfaceInvokeExpr expr = (JInterfaceInvokeExpr) ((InvokeStmt) unit).getInvokeExprBox()
								.getValue();
						if (expr != null) {
							parseExpression(expr, variableBlock, element, chapter, fieldType, filePath, scopeId);
						}

					}
				}
			}
			if (unit instanceof AssignStmt) {

				if (((AssignStmt) unit).getRightOpBox().getValue() instanceof JInterfaceInvokeExpr) {

					JInterfaceInvokeExpr expr = (JInterfaceInvokeExpr) ((AssignStmt) unit).getRightOpBox().getValue();

					if (expr != null) {
						parseExpression(expr, variableBlock, element, chapter, fieldType, filePath, scopeId);
					}
				}

			}
		}

		return variableBlock;
	}

	/**
	 *
	 * Special parsing of statements to find Process Variable operations.
	 * 
	 * @param expr
	 *            Expression Unit from Statement
	 * @param variableBlock
	 *            current VariableBlock
	 * @param element
	 *            BpmnElement
	 * @param chapter
	 *            ElementChapter
	 * @param fieldType
	 *            KnownElementFieldType
	 * @param filePath
	 *            ResourceFilePath for ProcessVariableOperation
	 * @param scopeId
	 *            Scope of BpmnElement
	 */
	private void parseExpression(JInterfaceInvokeExpr expr, VariableBlock variableBlock, BpmnElement element,
			ElementChapter chapter, KnownElementFieldType fieldType, String filePath, String scopeId) {

		String functionName = expr.getMethodRef().getName();
		int numberOfArg = expr.getArgCount();
		String baseBox = expr.getBaseBox().getValue().getType().toString();

		CamundaProcessVariableFunctions foundMethod = CamundaProcessVariableFunctions
				.findByNameAndNumberOfBoxes(functionName, baseBox, numberOfArg);

		if (foundMethod != null) {

			int location = foundMethod.getLocation() - 1;
			VariableOperation type = foundMethod.getOperationType();

			if (expr.getArgBox(location).getValue() instanceof StringConstant) {

				StringConstant variableName = (StringConstant) expr.getArgBox(location).getValue();
				String name = variableName.value;

				variableBlock.addProcessVariable(
						new ProcessVariableOperation(name, element, chapter, fieldType, filePath, type, scopeId));
			}
		}
	}

	/**
	 * 
	 * Parsing of initially discovered statements to find Process Variable
	 * operations.
	 * 
	 * @param expr
	 *            Expression Unit from Statement
	 * @param element
	 *            Current BPMN Element
	 * @param resourceFilePath
	 *            Filepath of model
	 * @return inital operations
	 */
	private Map<String, ProcessVariableOperation> parseInitialExpression(final JInterfaceInvokeExpr expr, final BpmnElement element,
			final String resourceFilePath) {

		final LinkedHashMap<String, ProcessVariableOperation> initialOperations = new LinkedHashMap<>();
		
		final String functionName = expr.getMethodRef().getName();
		final int numberOfArg = expr.getArgCount();
		final String baseBox = expr.getBaseBox().getValue().getType().toString();

		final CamundaProcessVariableFunctions foundMethod = CamundaProcessVariableFunctions
				.findByNameAndNumberOfBoxes(functionName, baseBox, numberOfArg);

		if (foundMethod != null) {
			final int location = foundMethod.getLocation() - 1;
			final VariableOperation type = foundMethod.getOperationType();
			if (expr.getArgBox(location).getValue() instanceof StringConstant) {	
				final StringConstant variableName = (StringConstant) expr.getArgBox(location).getValue();
				final String name = variableName.value;
				initialOperations.put(name, new ProcessVariableOperation(expr.getArg(0).toString(), element,
						ElementChapter.Code, KnownElementFieldType.Initial, resourceFilePath, type,
						element.getBaseElement().getId()));
			} 
		}
		return initialOperations;
	}

	/**
	 * 
	 * Find anomalies inside a Bpmn element as well.
	 * 
	 * @param element
	 *            BpmnElement
	 * @param outSet
	 *            OUT set of CFG
	 */
	private void addAnomaliesFoundInSourceCode(final BpmnElement element, final OutSetCFG outSet) {
		for (VariableBlock vb : outSet.getAllVariableBlocks()) {
			if (vb.getBlock().getIndexInMethod() == 0) {
				addAnomaliesFoundInPathsRecursive(element, vb.getBlock(), new LinkedList<String>(), outSet,
						new LinkedList<ProcessVariableOperation>(), "");
			}
		}
	}

	/**
	 *
	 * Build LinkedList of ProcessVariableOperations recursively based on CFG paths.
	 * 
	 * @param element
	 *            BpmnElement
	 * @param currentBlock
	 *            Block from CFG
	 * @param currentPath
	 *            List of so far visited Blocks
	 * @param outSet
	 *            OUT set of CFG
	 * @param predecessorVariablesList
	 *            Chain of Process Variables along the path
	 * @param edge
	 *            Current edge between two Blocks
	 */
	private void addAnomaliesFoundInPathsRecursive(final BpmnElement element, final Block currentBlock,
			final LinkedList<String> currentPath, final OutSetCFG outSet,
			final LinkedList<ProcessVariableOperation> predecessorVariablesList, final String edge) {

		// List<AnomalyContainer> foundAnomalies = new ArrayList<AnomalyContainer>();
		if (edge != null && edge != "") {
			currentPath.add(edge);
		}

		// get the VariableBlock
		VariableBlock variableBlock = outSet.getVariableBlock(currentBlock);

		// set IN + this Variables as OUT
		LinkedList<ProcessVariableOperation> usedVariables = new LinkedList<ProcessVariableOperation>();
		usedVariables.addAll(variableBlock.getAllProcessVariables());

		// prepare for statement by statement comparison
		Set<ProcessVariableOperation> pvSet = new HashSet<ProcessVariableOperation>(usedVariables);
		for (ProcessVariableOperation pV : pvSet) {
			// create unique lists for operations on same variable
			LinkedList<ProcessVariableOperation> innerList = new LinkedList<ProcessVariableOperation>();
			for (ProcessVariableOperation variable : usedVariables) {
				if (pV.getName().equals(variable.getName())) {
					innerList.add(variable);
				}
			}
			checkStatementByStatement(element, innerList);
		}

		// Based on last appearance of Variable decide on UR, DD, DU anomaly
		for (ProcessVariableOperation variable : usedVariables) {
			if (predecessorVariablesList.lastIndexOf(variable) >= 0) {
				ProcessVariableOperation lastAppearance = predecessorVariablesList
						.get(predecessorVariablesList.lastIndexOf(variable));
				checkAnomaly(element, variable, lastAppearance);
			}
		}

		// Prepare new chain of Variables including this Block's variables for
		// successors
		predecessorVariablesList.addAll(usedVariables);

		List<Block> successors = currentBlock.getSuccs();
		for (Block successor : successors) {
			String newEdge = currentBlock.toShortString() + successor.toShortString();
			int occurrence = Collections.frequency(currentPath, newEdge);
			if (occurrence < 2) {
				addAnomaliesFoundInPathsRecursive(element, successor, currentPath, outSet, predecessorVariablesList,
						newEdge);
			}
		}

		currentPath.removeLast();
		for (ProcessVariableOperation pv : variableBlock.getAllProcessVariables()) {
			predecessorVariablesList.removeLastOccurrence(pv);
		}

	}

	/**
	 * Checks for anomalies occurring between elements
	 * 
	 * @param element
	 *            Current BpmnElement
	 * @param innerList
	 *            List of all variable operations (same name)
	 */
	private void checkStatementByStatement(final BpmnElement element,
			final LinkedList<ProcessVariableOperation> innerList) {

		if (innerList.size() >= 2) {
			ProcessVariableOperation prev = null;
			for (ProcessVariableOperation curr : innerList) {
				if (prev == null) {
					prev = curr;
					continue;
				}
				checkAnomaly(element, curr, prev);
			}
		}

	}

	/**
	 * Check for dataflow anomaly between current and previous variable operation
	 * 
	 * @param element
	 *            Current BpmnElement
	 * @param curr
	 *            current operation
	 * @param last
	 *            previous operation
	 */
	private void checkAnomaly(final BpmnElement element, ProcessVariableOperation curr, ProcessVariableOperation last) {
		if (urSourceCode(last, curr)) {
			element.addSourceCodeAnomaly(
					new AnomalyContainer(curr.getName(), Anomaly.UR, element.getBaseElement().getId(), curr));
		}

		if (ddSourceCode(last, curr)) {
			element.addSourceCodeAnomaly(
					new AnomalyContainer(curr.getName(), Anomaly.DD, element.getBaseElement().getId(), curr));
		}

		if (duSourceCode(last, curr)) {
			element.addSourceCodeAnomaly(
					new AnomalyContainer(curr.getName(), Anomaly.DU, element.getBaseElement().getId(), curr));
		}
	}

	/**
	 * UR anomaly: second last operation of PV is DELETE, last operation is READ
	 * 
	 * @param prev
	 *            Previous ProcessVariable
	 * @param curr
	 *            Current ProcessVariable
	 * @return true/false
	 */
	private boolean urSourceCode(final ProcessVariableOperation prev, final ProcessVariableOperation curr) {
		if (curr.getOperation().equals(VariableOperation.READ)
				&& prev.getOperation().equals(VariableOperation.DELETE)) {
			return true;
		}
		return false;
	}

	/**
	 * DD anomaly: second last operation of PV is DEFINE, last operation is DELETE
	 * 
	 * @param prev
	 *            Previous ProcessVariable
	 * @param curr
	 *            Current ProcessVariable
	 * @return true/false
	 */
	private boolean ddSourceCode(final ProcessVariableOperation prev, final ProcessVariableOperation curr) {
		if (curr.getOperation().equals(VariableOperation.WRITE)
				&& prev.getOperation().equals(VariableOperation.WRITE)) {
			return true;
		}
		return false;
	}

	/**
	 * DU anomaly: second last operation of PV is DEFINE, last operation is DELETE
	 * 
	 * @param prev
	 *            Previous ProcessVariable
	 * @param curr
	 *            Current ProcessVariable
	 * @return true/false
	 */
	private boolean duSourceCode(final ProcessVariableOperation prev, final ProcessVariableOperation curr) {
		if (curr.getOperation().equals(VariableOperation.DELETE)
				&& prev.getOperation().equals(VariableOperation.WRITE)) {
			return true;
		}
		return false;
	}

	/**
	 * Strips unnecessary characters and returns cleaned name
	 * 
	 * @param className
	 *            Classname to be stripped of unused chars
	 * @return cleaned String
	 */
	private String cleanString(String className, boolean dot) {
		className = ProcessVariablesScanner.cleanString(className, dot);
		return className;
	}

}
