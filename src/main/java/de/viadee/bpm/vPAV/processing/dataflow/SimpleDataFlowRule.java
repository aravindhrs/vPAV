/**
 * BSD 3-Clause License
 *
 * Copyright © 2018, viadee Unternehmensberatung GmbH
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
package de.viadee.bpm.vPAV.processing.dataflow;

import de.viadee.bpm.vPAV.processing.model.data.ProcessVariable;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class SimpleDataFlowRule implements DataFlowRule {
    private static final String RULE_VIOLATION_DESCRIPTION_TEMPLATE = "Rule 'process variables%s should be %s' was violated %s times:\n";
    private static final String RULE_DESCRIPTION_TEMPLATE = "Process variables%s should be %s";
    private static final String VIOLATION_TEMPLATE = "'%s' needed to be %s%s";
    private final DescribedPredicateEvaluator<ProcessVariable> constraint;
    private final DescribedPredicateEvaluator<ProcessVariable> condition;

    SimpleDataFlowRule(DescribedPredicateEvaluator<ProcessVariable> constraint, DescribedPredicateEvaluator<ProcessVariable> condition) {
        this.constraint = constraint;
        this.condition = condition;
    }

    public Collection<EvaluationResult<ProcessVariable>> evaluate(Collection<ProcessVariable> variables) {
        Stream<ProcessVariable> variableStream = variables.stream();
        if (constraint != null)
            variableStream = variableStream.filter(p -> constraint.evaluate(p).isFulfilled());
        List<EvaluationResult<ProcessVariable>> results = variableStream
                .map(condition::evaluate)
                .collect(Collectors.toList());
        return results;
    }

    public void check(Collection<ProcessVariable> variables) {
        assertNoViolations(evaluate(variables));
    }

    private void assertNoViolations(Collection<EvaluationResult<ProcessVariable>> result) {
        List<EvaluationResult<ProcessVariable>> violations = result.stream()
                .filter(r -> !r.isFulfilled())
                .collect(Collectors.toList());
        if (violations.size() > 0) {
            String ruleDescription = createRuleDescription(violations.size());

            String violationsString = violations.stream()
                    .map(this::createViolationMessage)
                    .collect(Collectors.joining("\n"));
            throw new AssertionError( ruleDescription + violationsString);
        }
    }

    public String getRuleDescription() {
        String constraintDescription = constraint != null ?
                " that are " + constraint.getDescription() :
                "";
        return String.format(RULE_DESCRIPTION_TEMPLATE, constraintDescription, condition.getDescription());
    }

    private String createRuleDescription(int violationCount) {
        String constraintDescription = constraint != null ?
                " that are " + constraint.getDescription() :
                "";
        return String.format(RULE_VIOLATION_DESCRIPTION_TEMPLATE, constraintDescription, condition.getDescription(), violationCount);
    }

    private String createViolationMessage(EvaluationResult<ProcessVariable> result) {
        String violationMessage = result.getViolationMessage().isPresent() ?
                " but was " + result.getViolationMessage().get() :
                "";
        return String.format(VIOLATION_TEMPLATE,
                result.getEvaluatedVariable().getName(), condition.getDescription(), violationMessage);
    }
}
