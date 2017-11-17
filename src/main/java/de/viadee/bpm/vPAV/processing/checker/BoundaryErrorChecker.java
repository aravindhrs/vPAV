/**
 * Copyright � 2017, viadee Unternehmensberatung GmbH All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met: 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer. 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or other materials provided with the
 * distribution. 3. All advertising materials mentioning features or use of this software must display the following
 * acknowledgement: This product includes software developed by the viadee Unternehmensberatung GmbH. 4. Neither the
 * name of the viadee Unternehmensberatung GmbH nor the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY <viadee Unternehmensberatung GmbH> ''AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package de.viadee.bpm.vPAV.processing.checker;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.types.Resource;
import org.camunda.bpm.model.bpmn.impl.BpmnModelConstants;
import org.camunda.bpm.model.bpmn.instance.BaseElement;

import de.odysseus.el.tree.IdentifierNode;
import de.odysseus.el.tree.Tree;
import de.odysseus.el.tree.TreeBuilder;
import de.odysseus.el.tree.impl.Builder;
import de.viadee.bpm.vPAV.BPMNScanner;
import de.viadee.bpm.vPAV.ConstantsConfig;
import de.viadee.bpm.vPAV.RuntimeConfig;
import de.viadee.bpm.vPAV.config.model.Rule;
import de.viadee.bpm.vPAV.processing.CheckName;
import de.viadee.bpm.vPAV.processing.model.data.BpmnElement;
import de.viadee.bpm.vPAV.processing.model.data.CheckerIssue;
import de.viadee.bpm.vPAV.processing.model.data.CriticalityEnum;

public class BoundaryErrorChecker extends AbstractElementChecker {

    private static Logger logger = Logger.getLogger(BoundaryErrorChecker.class.getName());

    private final String camunda_class = "camunda:class";

    private final String camunda_dexp = "camunda:delegateExpression";

    public BoundaryErrorChecker(final Rule rule, BPMNScanner bpmnScanner) {
        super(rule, bpmnScanner);
    }

    @Override
    public Collection<CheckerIssue> check(BpmnElement element) {

        final Collection<CheckerIssue> issues = new ArrayList<CheckerIssue>();
        final BaseElement bpmnElement = element.getBaseElement();

        if (bpmnElement.getElementType().getTypeName()
                .equals(BpmnModelConstants.BPMN_ELEMENT_BOUNDARY_EVENT)) {

            final Map<String, String> errorEventDef = bpmnScanner.getErrorEvent(bpmnElement.getId());

            // Map<String, String> errorEventDef -> "errorRef" , "camunda:errorMessageVariable"
            for (Map.Entry<String, String> entry : errorEventDef.entrySet()) {

                // If no ID is returned, create issue
                if (entry.getKey() == null || entry.getKey().isEmpty()) {
                    issues.add(new CheckerIssue(rule.getName(), CriticalityEnum.ERROR,
                            element.getProcessdefinition(), null, bpmnElement.getAttributeValue("id"),
                            bpmnElement.getAttributeValue("name"), null, null, null,
                            "BoundaryErrorEvent '" + CheckName.checkName(bpmnElement)
                                    + "' with no error specifications"));
                    continue;
                }

                // If ID of error gets returned, we check for the contents of the error
                else if (entry.getKey() != null && !entry.getKey().isEmpty()) {

                    // Map<String, String> errorDef -> "name" , "errorCode"
                    final Map<String, String> errorDef = bpmnScanner.getErrorDef(entry.getKey());

                    for (Map.Entry<String, String> errorDefEntry : errorDef.entrySet()) {
                        if (errorDefEntry.getKey() == null || errorDefEntry.getKey().isEmpty()) {
                            issues.add(new CheckerIssue(rule.getName(), CriticalityEnum.WARNING,
                                    element.getProcessdefinition(), null, bpmnElement.getAttributeValue("id"),
                                    bpmnElement.getAttributeValue("name"), null, null, null,
                                    "BoundaryErrorEvent '" + CheckName.checkName(bpmnElement)
                                            + "' with no ErrorName"));
                        }
                        // ErrorCode
                        if (errorDefEntry.getValue() != null && !errorDefEntry.getValue().isEmpty()) {
                            final String mappedTaskId = bpmnScanner.getErrorEventMapping(bpmnElement.getId());
                            final String implementation = bpmnScanner.getImplementation(mappedTaskId);
                            final String implementationRef = bpmnScanner.getImplementationReference(mappedTaskId,
                                    implementation);

                            if (implementation != null && !implementation.isEmpty()) {
                                if (implementation.equals(camunda_class)) {
                                    if (!readResourceFile(implementationRef, errorDefEntry.getValue())) {
                                        issues.add(new CheckerIssue(rule.getName(), CriticalityEnum.ERROR,
                                                element.getProcessdefinition(), null,
                                                bpmnElement.getAttributeValue("id"),
                                                bpmnElement.getAttributeValue("name"), null, null, null,
                                                "ErrorCode of '" + CheckName.checkName(bpmnElement)
                                                        + "' does not match with throwing declaration of class '"
                                                        + implementationRef + "'"));
                                    }
                                } else if (implementation.equals(camunda_dexp)) {
                                    // check validity of a bean
                                    checkBeanMapping(element, issues, bpmnElement, errorDefEntry, implementationRef);

                                }

                            }

                        } else if (errorDefEntry.getValue() == null || errorDefEntry.getValue().isEmpty()) {
                            issues.add(new CheckerIssue(rule.getName(), CriticalityEnum.ERROR,
                                    element.getProcessdefinition(), null, bpmnElement.getAttributeValue("id"),
                                    bpmnElement.getAttributeValue("name"), null, null, null,
                                    "BoundaryErrorEvent '" + CheckName.checkName(bpmnElement)
                                            + "' with no ErrorCode"));
                        }
                    }
                }

                // No ErrorMessageVariable has been specified
                if (entry.getValue() == null || entry.getValue().isEmpty()) {
                    issues.add(new CheckerIssue(rule.getName(), CriticalityEnum.WARNING,
                            element.getProcessdefinition(), null, bpmnElement.getAttributeValue("id"),
                            bpmnElement.getAttributeValue("name"), null, null, null,
                            "BoundaryErrorEvent '" + CheckName.checkName(bpmnElement)
                                    + "' with no ErrorMessageVariable"));
                }
            }

            // No ErrorCodeVariable has been specified
            final String errorCode = bpmnScanner.getErrorCodeVar(bpmnElement.getId());
            if (errorCode == null || errorCode.isEmpty()) {
                issues.add(new CheckerIssue(rule.getName(), CriticalityEnum.WARNING,
                        element.getProcessdefinition(), null, bpmnElement.getAttributeValue("id"),
                        bpmnElement.getAttributeValue("name"), null, null, null,
                        "BoundaryErrorEvent '" + CheckName.checkName(bpmnElement) + "' with no ErrorCodeVariable"));
            }
        }

        return issues;
    }

    /**
     * In case a bean mapping exists, we check for validity of a bean, so the event can be mapped against the respective
     * task If the class or bean can be resolved, the ErrorCode gets validated
     *
     * @param element
     * @param issues
     * @param bpmnElement
     * @param errorDefEntry
     * @param implementationRef
     */
    private void checkBeanMapping(BpmnElement element, final Collection<CheckerIssue> issues,
            final BaseElement bpmnElement, Map.Entry<String, String> errorDefEntry, final String implementationRef) {
        if (RuntimeConfig.getInstance().getBeanMapping() != null) {
            final TreeBuilder treeBuilder = new Builder();
            final Tree tree = treeBuilder.build(implementationRef);
            final Iterable<IdentifierNode> identifierNodes = tree.getIdentifierNodes();
            // if beanMapping ${...} reference
            if (identifierNodes.iterator().hasNext()) {
                for (final IdentifierNode node : identifierNodes) {
                    final String classFile = RuntimeConfig.getInstance().getBeanMapping()
                            .get(node.getName());
                    // correct beanmapping was found -> check if class exists
                    if (classFile != null && classFile.trim().length() > 0) {
                        if (checkClassFile(classFile)) {
                            if (!readResourceFile(classFile, errorDefEntry.getValue())) {
                                issues.add(new CheckerIssue(rule.getName(),
                                        CriticalityEnum.ERROR,
                                        element.getProcessdefinition(), null,
                                        bpmnElement.getAttributeValue("id"),
                                        bpmnElement.getAttributeValue("name"), null, null,
                                        null,
                                        "ErrorCode of '" + CheckName.checkName(bpmnElement)
                                                + "' does not match with throwing declaration of bean '"
                                                + node.getName() + "'"));
                            }
                        } else {
                            issues.add(new CheckerIssue(rule.getName(),
                                    CriticalityEnum.ERROR,
                                    element.getProcessdefinition(), null,
                                    bpmnElement.getAttributeValue("id"),
                                    bpmnElement.getAttributeValue("name"), null, null,
                                    null,
                                    "Corresponding class of associated task could not be loaded or found."));
                        }
                    } else {
                        // incorrect beanmapping
                        issues.add(new CheckerIssue(rule.getName(), CriticalityEnum.ERROR,
                                element.getProcessdefinition(), null,
                                bpmnElement.getAttributeValue("id"),
                                bpmnElement.getAttributeValue("name"), null, null, null,
                                "Due to incorrect beanmapping for delegate expression: '"
                                        + implementationRef
                                        + "' the BoundaryErrorEvent can not be linked to class."));
                    }
                }
            }
        } else {
            if (!checkClassFile(implementationRef)) {
                issues.add(new CheckerIssue(rule.getName(), CriticalityEnum.ERROR,
                        element.getProcessdefinition(), null,
                        bpmnElement.getAttributeValue("id"),
                        bpmnElement.getAttributeValue("name"), null, null, null,
                        "Class for '" + implementationRef
                                + "' could not be found and therefore not linked to BoundaryErrorEvent '"
                                + CheckName.checkName(bpmnElement) + "'."));
            }
        }
    }

    /**
     * Reads a resource and retrieves content as String
     *
     * @param className
     * @param errorCode
     * @return boolean
     */
    private boolean readResourceFile(final String className, final String errorCode) {

        final String fileName = className.replaceAll("\\.", "/") + ".java";

        boolean matchingErrorCode = false;

        if (fileName != null && fileName.trim().length() > 0) {
            try {
                final DirectoryScanner scanner = new DirectoryScanner();

                if (RuntimeConfig.getInstance().isTest()) {
                    if (fileName.endsWith(".java"))
                        scanner.setBasedir(ConstantsConfig.TEST_JAVAPATH);
                    else
                        scanner.setBasedir(ConstantsConfig.TEST_BASEPATH);
                } else {
                    if (fileName.endsWith(".java"))
                        scanner.setBasedir(ConstantsConfig.JAVAPATH);
                    else
                        scanner.setBasedir(ConstantsConfig.BASEPATH);
                }

                Resource s = scanner.getResource(fileName);

                if (s.isExists()) {
                    InputStreamReader resource = new InputStreamReader(new FileInputStream(s.toString()));
                    final String methodBody = IOUtils.toString(resource);
                    return validateContent(methodBody, errorCode);
                } else {
                    logger.warning("Class " + fileName + " could not be read or does not exist");
                }
            } catch (final IOException ex) {
                logger.warning("Resource '" + fileName + "' could not be read: " + ex.getMessage());
            }
        }

        return matchingErrorCode;
    }

    /**
     * Check methodBody for content and return true if a "throw new BpmnError.." declaration is found
     *
     * @param errorCode
     * @param methodBody
     * @return boolean
     */
    private boolean validateContent(final String methodBody, final String errorCode) {

        if (methodBody != null && !methodBody.isEmpty()) {
            if (methodBody.contains("throw new BpmnError")) {
                String temp = methodBody.substring(methodBody.indexOf("throw new BpmnError"));
                temp = temp.substring(0, temp.indexOf(";") + 1);

                final String delErrorCode = temp.substring(temp.indexOf("\"") + 1, temp.lastIndexOf("\""));
                if (delErrorCode.equals(errorCode)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if class reference for a given element exists
     *
     * @param className
     * @return boolean
     */
    private boolean checkClassFile(final String className) {

        @SuppressWarnings("unused")
        final String classPath = className.replaceAll("\\.", "/") + ".java";

        try {
            RuntimeConfig.getInstance().getClassLoader().loadClass(className);
        } catch (final ClassNotFoundException e) {
            return false;

        }
        return true;
    }

}