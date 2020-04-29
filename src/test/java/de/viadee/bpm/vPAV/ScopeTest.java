/*
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
package de.viadee.bpm.vPAV;

import com.cronutils.model.Cron;
import de.viadee.bpm.vPAV.config.model.RuleSet;
import de.viadee.bpm.vPAV.constants.ConfigConstants;
import de.viadee.bpm.vPAV.processing.ElementGraphBuilder;
import de.viadee.bpm.vPAV.processing.JavaReaderStatic;
import de.viadee.bpm.vPAV.processing.ProcessVariableReader;
import de.viadee.bpm.vPAV.processing.ProcessVariablesScanner;
import de.viadee.bpm.vPAV.processing.code.flow.BasicNode;
import de.viadee.bpm.vPAV.processing.code.flow.BpmnElement;
import de.viadee.bpm.vPAV.processing.code.flow.ControlFlowGraph;
import de.viadee.bpm.vPAV.processing.code.flow.FlowAnalysis;
import de.viadee.bpm.vPAV.processing.model.data.ProcessVariableOperation;
import de.viadee.bpm.vPAV.processing.model.graph.Graph;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.BaseElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import soot.Scene;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

public class ScopeTest {

    @BeforeClass
    public static void setupSoot() throws MalformedURLException {
        final File file = new File(".");
        final String currentPath = file.toURI().toURL().toString();
        final URL classUrl = new URL(currentPath + "src/test/java");
        final URL[] classUrls = { classUrl };
        ClassLoader cl = new URLClassLoader(classUrls);
        RuntimeConfig.getInstance().setClassLoader(cl);
        RuntimeConfig.getInstance().getResource("en_US");
        RuntimeConfig.getInstance().setTest(true);
        FileScanner.setupSootClassPaths(new LinkedList<>());
        JavaReaderStatic.setupSoot();
        Scene.v().loadNecessaryClasses();
    }

    @Before
    public void setupProperties() {
        Properties myProperties = new Properties();
        myProperties.put("scanpath", ConfigConstants.TEST_TARGET_PATH);
        ConfigConstants.getInstance().setProperties(myProperties);
    }

    @Test
    public void testScopeListener() {
        BpmnModelInstance modelInstance = Bpmn.createProcess("MyProcess").startEvent().serviceTask("MyServiceTask")
                .camundaExecutionListenerExpression("start", "${execution.setVariable('var',true)}").endEvent().done();
        ProcessVariableReader reader = new ProcessVariableReader(null, null);
        BpmnElement element = getBpmnElement(modelInstance.getModelElementById("MyServiceTask"));

        reader.getVariablesFromElement(element, new BasicNode[1]);
        Assert.assertEquals(1, element.getControlFlowGraph().getNodes().size());
        // Variables are normally set globally
        Assert.assertEquals("MyProcess",
                element.getControlFlowGraph().getNodes().values().iterator().next().getDefined().values().iterator()
                        .next()
                        .getScopeId());
    }

    @Test
    public void testScopeDelegate() {
        BpmnModelInstance modelInstance = Bpmn.createProcess("MyProcess").startEvent().serviceTask("MyServiceTask")
                .camundaClass("de.viadee.bpm.vPAV.delegates.TestDelegate")
                .endEvent().done();
        ProcessVariableReader reader = new ProcessVariableReader(null, null);
        BpmnElement element = getBpmnElement(modelInstance.getModelElementById("MyServiceTask"));

        reader.getVariablesFromElement(element, new BasicNode[1]);
        // Variables are normally set globally
        Assert.assertEquals(1, element.getControlFlowGraph().getNodes().size());
        Assert.assertEquals("MyProcess",
                element.getControlFlowGraph().getNodes().values().iterator().next().getDefined().values().iterator()
                        .next()
                        .getScopeId());
    }

    @Test
    public void testScopeIOParameter() {
        BpmnModelInstance modelInstance = Bpmn.createProcess("MyProcess").startEvent().serviceTask("MyServiceTask")
                .camundaInputParameter("myInputParameter", "${globalReadVariable}")
                .camundaOutputParameter("myOutputParameter", "myValue")
                .endEvent().done();
        ProcessVariableReader reader = new ProcessVariableReader(null, null);
        BpmnElement element = getBpmnElement(modelInstance.getModelElementById("MyServiceTask"));

        reader.getVariablesFromElement(element, new BasicNode[1]);
        Assert.assertEquals(3, element.getControlFlowGraph().getNodes().size());
        Iterator<BasicNode> iterator = element.getControlFlowGraph().getNodes().values().iterator();
        BasicNode inputNodeValue = iterator.next();
        BasicNode inputNodeName = iterator.next();
        BasicNode outputNode = iterator.next();
        // Input parameter is only available in Service Task
        Assert.assertEquals("myInputParameter", inputNodeName.getDefined().values().iterator().next().getName());
        Assert.assertEquals("MyServiceTask", inputNodeName.getDefined().values().iterator().next().getScopeId());
        Assert.assertEquals("globalReadVariable", inputNodeValue.getUsed().values().iterator().next().getName());
        Assert.assertEquals("MyProcess", inputNodeValue.getUsed().values().iterator().next().getScopeId());

        // Output parameter is globally accessible
        Assert.assertEquals("myOutputParameter", outputNode.getDefined().values().iterator().next().getName());
        Assert.assertEquals("MyProcess", outputNode.getDefined().values().iterator().next().getScopeId());

    }

    @Test
    public void testScopeVariableMapping() {
        BpmnModelInstance modelInstance = Bpmn.createProcess("MyProcess").startEvent().callActivity("MyCallActivity")
                .camundaVariableMappingClass("de.viadee.bpm.vPAV.delegates.DelegatedVarMapping")
                .calledElement("MyCalledProcess").endEvent().done();

        ProcessVariableReader reader = new ProcessVariableReader(null, null);
        BpmnElement element = getBpmnElement(modelInstance.getModelElementById("MyCallActivity"));

        reader.getVariablesFromElement(element, new BasicNode[1]);
        Assert.assertEquals(2, element.getControlFlowGraph().getNodes().size());
        Iterator<BasicNode> iterator = element.getControlFlowGraph().getNodes().values().iterator();
        BasicNode inputVariables = iterator.next();
        BasicNode outputVariables = iterator.next();
        // In mapped variable is only available in called process
        Assert.assertEquals("inMapping", inputVariables.getDefined().values().iterator().next().getName());
        Assert.assertEquals("MyCalledProcess", inputVariables.getDefined().values().iterator().next().getScopeId());

        // Out mapped variable is only available in normal process
        Assert.assertEquals("outMapping", outputVariables.getDefined().values().iterator().next().getName());
        Assert.assertEquals("MyProcess", outputVariables.getDefined().values().iterator().next().getScopeId());
    }

    @Test
    public void testScopeSubprocess() {
        // Variables in Subprocesses are globally available if  execution.setVariableLocal is not used (not supported yet)
        BpmnModelInstance modelInstance = Bpmn.createProcess("MyProcess").startEvent().subProcess()
                .embeddedSubProcess().startEvent()
                .serviceTask("MyServiceTask").camundaExpression("${execution.setVariable('test', true)}").endEvent()
                .subProcessDone()
                .endEvent()
                .done();

        ProcessVariableReader reader = new ProcessVariableReader(null, null);
        BpmnElement element = getBpmnElement(modelInstance.getModelElementById("MyServiceTask"));

        reader.getVariablesFromElement(element, new BasicNode[1]);
        Assert.assertEquals(1, element.getControlFlowGraph().getNodes().size());
        Assert.assertEquals("test",
                element.getControlFlowGraph().getNodes().values().iterator().next().getDefined().values().iterator()
                        .next().getName());
        Assert.assertEquals("MyProcess",
                element.getControlFlowGraph().getNodes().values().iterator().next().getDefined().values().iterator()
                        .next().getScopeId());
        // TODO check more than only delegates in service tasks
    }

    @Test
    public void testScopeCallActivity() {
        BpmnModelInstance modelInstance = Bpmn.createProcess().startEvent().callActivity()
                .calledElement("calledProcess")
                .endEvent().done();

        final Map<String, String> processIdToPathMap = new HashMap<>();
        processIdToPathMap.put("calledProcess", "ModelWithWriteExpression.bpmn");
        ElementGraphBuilder graphBuilder = new ElementGraphBuilder(null, processIdToPathMap);
        FlowAnalysis flowAnalysis = new FlowAnalysis();
        FileScanner fileScanner = new FileScanner(new RuleSet());
        fileScanner.setScanPath(ConfigConstants.TEST_JAVAPATH);
        Collection<Graph> graphs = graphBuilder.createProcessGraph(fileScanner, modelInstance, "", new ArrayList<>(),
                new ProcessVariablesScanner(null), flowAnalysis);

        flowAnalysis.analyze(graphs);
        Assert.assertEquals("test",
                flowAnalysis.getNodes().get("MyServiceTask__0").getDefined().values().iterator().next().getName());
        Assert.assertEquals("calledProcess",
                flowAnalysis.getNodes().get("MyServiceTask__0").getDefined().values().iterator().next().getScopeId());
    }

    @Test
    public void testScopeConsideredInAnalysisInputParameters() {
        ElementGraphBuilder graphBuilder = new ElementGraphBuilder(null, null);
        FlowAnalysis flowAnalysis = new FlowAnalysis();
        FileScanner fileScanner = new FileScanner(new RuleSet());
        fileScanner.setScanPath(ConfigConstants.TEST_JAVAPATH);

        // Test that input parameters are not passed beyond scope
        BpmnModelInstance modelInstance = Bpmn.createProcess("MyProcess").startEvent().serviceTask("MyServiceTask")
                .camundaInputParameter("myInputParameter", "${globalReadVariable}")
                .camundaOutputParameter("myOutputParameter", "myValue").sequenceFlowId("MySequenceFlow")
                .endEvent().done();
        Collection<Graph> graphs = graphBuilder.createProcessGraph(fileScanner, modelInstance, "", new ArrayList<>(),
                new ProcessVariablesScanner(null), flowAnalysis);
        flowAnalysis.analyze(graphs);

        // Only myOutputParameter is accessible after the service task
        Assert.assertEquals(1, flowAnalysis.getNodes().get("MySequenceFlow").getInUnused().size());
        Assert.assertEquals("myOutputParameter",
                flowAnalysis.getNodes().get("MySequenceFlow").getInUnused().values().iterator().next().getName());
        Assert.assertEquals(0, flowAnalysis.getNodes().get("MySequenceFlow").getInUsed().size());
    }

    @Test
    public void testScopeConsideredInAnalysisSubprocess() {
        ElementGraphBuilder graphBuilder = new ElementGraphBuilder(null, null);
        FlowAnalysis flowAnalysis = new FlowAnalysis();
        FileScanner fileScanner = new FileScanner(new RuleSet());
        fileScanner.setScanPath(ConfigConstants.TEST_JAVAPATH);

        // Test that global variables are accessible in subprocesses and that variables are accessible outside
        BpmnModelInstance modelInstance = Bpmn.createProcess("MyProcess").startEvent().serviceTask()
                .camundaExpression("${execution.setVariable('globalVar', true)}").subProcess()
                .embeddedSubProcess().startEvent("MyStartEvent")
                .serviceTask("MyServiceTask").camundaExpression("${execution.setVariable('test', true)}").endEvent()
                .subProcessDone()
                .endEvent("MyEndEvent")
                .done();
        flowAnalysis = new FlowAnalysis();
        Collection<Graph> graphs = graphBuilder.createProcessGraph(fileScanner, modelInstance, "", new ArrayList<>(),
                new ProcessVariablesScanner(null), flowAnalysis);
        flowAnalysis.analyze(graphs);
        Assert.assertEquals(1, flowAnalysis.getNodes().get("MyServiceTask__0").getInUnused().size());
        Assert.assertEquals("globalVar",
                flowAnalysis.getNodes().get("MyServiceTask__0").getInUnused().values().iterator().next().getName());
        Assert.assertEquals(2, flowAnalysis.getNodes().get("MyEndEvent").getInUnused().size());
        Iterator<ProcessVariableOperation> iter = flowAnalysis.getNodes().get("MyEndEvent").getInUnused().values()
                .iterator();
        Assert.assertEquals("globalVar", iter.next().getName());
        Assert.assertEquals("test", iter.next().getName());
    }

    @Test
    public void testScopeConsideredInAnalysisCallActivity() {
        final Map<String, String> processIdToPathMap = new HashMap<>();
        processIdToPathMap.put("calledProcess", "ModelWithWriteExpression.bpmn");
        ElementGraphBuilder graphBuilder = new ElementGraphBuilder(null, processIdToPathMap);
        FlowAnalysis flowAnalysis = new FlowAnalysis();
        FileScanner fileScanner = new FileScanner(new RuleSet());
        fileScanner.setScanPath(ConfigConstants.TEST_JAVAPATH);

        // Test that variables in called processes are scoped
        BpmnModelInstance modelInstance = Bpmn.createProcess().startEvent().serviceTask()
                .camundaExpression("${execution.setVariable('globalVar',true)}").callActivity()
                .calledElement("calledProcess")
                .endEvent("MyEndEvent").done();
        Collection<Graph> graphs = graphBuilder.createProcessGraph(fileScanner, modelInstance, "", new ArrayList<>(),
                new ProcessVariablesScanner(null), flowAnalysis);
        flowAnalysis.analyze(graphs);

        // Variable set in caller process is not available in called process
        Assert.assertEquals(0, flowAnalysis.getNodes().get("MyServiceTask__0").getInUnused().size());
        Assert.assertEquals(0, flowAnalysis.getNodes().get("MyServiceTask__0").getInUsed().size());

        // Variable set in called process is not available in caller process
        Assert.assertEquals(1, flowAnalysis.getNodes().get("MyEndEvent").getInUnused().size());
        Assert.assertEquals("globalVar",
                flowAnalysis.getNodes().get("MyEndEvent").getInUnused().values().iterator().next().getName());
    }

    private BpmnElement getBpmnElement(BaseElement element) {
        return new BpmnElement(null, element, new ControlFlowGraph(), new FlowAnalysis());
    }

}