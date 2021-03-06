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
package de.viadee.bpm.vPAV.processing.code;

import de.viadee.bpm.vPAV.BpmnScanner;
import de.viadee.bpm.vPAV.FileScanner;
import de.viadee.bpm.vPAV.RuntimeConfig;
import de.viadee.bpm.vPAV.config.model.RuleSet;
import de.viadee.bpm.vPAV.constants.ConfigConstants;
import de.viadee.bpm.vPAV.processing.ElementGraphBuilder;
import de.viadee.bpm.vPAV.processing.ProcessVariablesScanner;
import de.viadee.bpm.vPAV.processing.code.flow.AnalysisElement;
import de.viadee.bpm.vPAV.processing.code.flow.FlowAnalysis;
import de.viadee.bpm.vPAV.processing.code.flow.NodeDecorator;
import de.viadee.bpm.vPAV.processing.model.data.AnomalyContainer;
import de.viadee.bpm.vPAV.processing.model.graph.Graph;
import de.viadee.bpm.vPAV.processing.model.graph.Path;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.impl.instance.ServiceTaskImpl;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class RecursionTest {

    private static final String BASE_PATH = "src/test/resources/";

    @BeforeClass
    public static void setup() throws IOException {
        final File file = new File(".");
        final String currentPath = file.toURI().toURL().toString();
        final URL classUrl = new URL(currentPath + "src/test/java");
        final URL[] classUrls = { classUrl };
        ClassLoader cl = new URLClassLoader(classUrls);
        RuntimeConfig.getInstance().setClassLoader(cl);
        RuntimeConfig.getInstance().getResource("en_US");
        RuntimeConfig.getInstance().setTest(true);
    }

    @Test
    public void recursionTest() {
        final ProcessVariablesScanner scanner = new ProcessVariablesScanner(null);
        Properties myProperties = new Properties();
        myProperties.put("scanpath", ConfigConstants.TEST_TARGET_PATH);
        ConfigConstants.getInstance().setProperties(myProperties);
        final FileScanner fileScanner = new FileScanner(new RuleSet());
        final String PATH = BASE_PATH + "ModelWithDelegate_UR.bpmn";
        final File processDefinition = new File(PATH);

        // parse bpmn model and set delegate
        final BpmnModelInstance modelInstance = Bpmn.readModelFromFile(processDefinition);
        ServiceTaskImpl serviceTask = modelInstance.getModelElementById("ServiceTask_108g52x");
        serviceTask.setCamundaClass("de.viadee.bpm.vPAV.delegates.RecursiveDelegate");

        final ElementGraphBuilder graphBuilder = new ElementGraphBuilder(null, null, null, null, new BpmnScanner(PATH));

        // create data flow graphs
        final Collection<String> calledElementHierarchy = new ArrayList<>();
        FlowAnalysis flowAnalysis = new FlowAnalysis();
        final Collection<Graph> graphCollection = graphBuilder.createProcessGraph(fileScanner, modelInstance,
                processDefinition.getPath(), calledElementHierarchy, scanner, flowAnalysis);

        flowAnalysis.analyze(graphCollection);
        LinkedHashMap<String, AnalysisElement> nodes = flowAnalysis.getNodes();
        // Start from end event and go to start.
        AnalysisElement endEvent = nodes.get("EndEvent_13uioac");
        AnalysisElement sequenceFlow2 = endEvent.getPredecessors().get(0);
        AnalysisElement taskDelegateElse = sequenceFlow2.getPredecessors().get(0);
        AnalysisElement taskDelegateIf = taskDelegateElse.getPredecessors().get(0);
        AnalysisElement taskDelegateExecute = taskDelegateElse.getPredecessors().get(1);
        AnalysisElement sequenceFlow1 = taskDelegateExecute.getPredecessors().get(0);
        AnalysisElement startEvent = sequenceFlow1.getPredecessors().get(0);

        assertEquals("Last sequence flow should have exactly one predecessor (else node).", 1,sequenceFlow2.getPredecessors().size());
        assertEquals("Else node should have two predecessors due to recursion",2, taskDelegateElse.getPredecessors().size());
        assertEquals("If node should have two predecessors due to recursion", 2, taskDelegateIf.getPredecessors().size());
        assertEquals("If node should be a predecessor of itself", ((NodeDecorator)taskDelegateIf).getDecoratedNode(), ((NodeDecorator) taskDelegateIf.getPredecessors().get(1)).getDecoratedNode());

        // TODO check anomalies but at the moment we cannot recognize them correctly if the graph includes a loop
    }
}
