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
package de.viadee.bpm.vPAV.processing.model.data;

import de.viadee.bpm.vPAV.constants.CamundaMethodServices;

/**
 *
 * Enum storing Camunda methods of ProcessVariable operations.
 *
 * name and numberOfArgBoxes are for identification locationOfBox and operationType are for finding and storing the
 * ProcessVariable
 *
 */
public enum CamundaProcessVariableFunctions {

    SetVariable("setVariable", CamundaMethodServices.DELEGATE, 2, 1, VariableOperation.WRITE),
    SetVariable2("setVariable", CamundaMethodServices.RUNTIME, 3, 1, VariableOperation.WRITE),
    SetVariableD("setVariable", CamundaMethodServices.DELEGATE_TASK, 2, 1, VariableOperation.WRITE),
    SetVariableS("setVariable", CamundaMethodServices.SCOPE, 2, 1, VariableOperation.WRITE),
    SetVariableLocale("setVariableLocal", CamundaMethodServices.SCOPE, 2, 1, VariableOperation.WRITE),
    SetVariableLocale2("setVariableLocal", CamundaMethodServices.DELEGATE_TASK, 2, 1, VariableOperation.WRITE),
    SetVariableA("setVariable", CamundaMethodServices.ACTIVITY_EXECUTION, 2, 1, VariableOperation.WRITE),
    GetVariable("getVariable", CamundaMethodServices.DELEGATE, 1, 1, VariableOperation.READ),
    GetVariable2("getVariable", CamundaMethodServices.DELEGATE, 2, 1, VariableOperation.READ),
    GetVariable3("getVariable", CamundaMethodServices.DELEGATE_TASK, 1, 1, VariableOperation.READ),
    GetVariableS("getVariable", CamundaMethodServices.SCOPE, 1, 1, VariableOperation.READ),
    GetVariableLocal("getVariableLocal", CamundaMethodServices.DELEGATE_TASK, 1, 1, VariableOperation.READ),
    GetVariableLocalTyped("getVariableLocalTyped", CamundaMethodServices.DELEGATE_TASK, 1, 1, VariableOperation.READ),
    GetVariableLocalTyped2("getVariableLocalTyped", CamundaMethodServices.DELEGATE_TASK, 2, 1, VariableOperation.READ),
    GetVariableA("getVariable", CamundaMethodServices.ACTIVITY_EXECUTION, 1, 1, VariableOperation.READ),
    GetVariableA2("getVariable", CamundaMethodServices.ACTIVITY_EXECUTION, 2, 1, VariableOperation.READ),
    RemoveVariable("removeVariable", CamundaMethodServices.DELEGATE, 1, 1, VariableOperation.DELETE),
    RemoveVariable2("removeVariable", CamundaMethodServices.DELEGATE_TASK, 1, 1, VariableOperation.DELETE),
    RemoveVariableLocal("removeVariableLocal", CamundaMethodServices.DELEGATE_TASK, 1, 1, VariableOperation.DELETE),
    RemoveVariableA("removeVariable", CamundaMethodServices.ACTIVITY_EXECUTION, 1, 1, VariableOperation.DELETE),
    Put("put", CamundaMethodServices.VARIABLE_MAP, 2, 1, VariableOperation.WRITE),
    Put2("put", CamundaMethodServices.MAP, 2, 1, VariableOperation.WRITE),
    PutValue("putValue", CamundaMethodServices.VARIABLE_MAP, 2, 1, VariableOperation.WRITE),
    PutValueTyped("putValueTyped", CamundaMethodServices.VARIABLE_MAP, 2, 1, VariableOperation.WRITE),
    GetValue("getValue", CamundaMethodServices.VARIABLE_MAP, 2, 1, VariableOperation.READ),
    GetValueTyped("getValueTyped", CamundaMethodServices.VARIABLE_MAP, 1, 1, VariableOperation.READ),
    Remove("remove", CamundaMethodServices.VARIABLE_MAP, 1, 1, VariableOperation.DELETE),
    Remove2("remove", CamundaMethodServices.VARIABLE_MAP, 2, 1, VariableOperation.DELETE);

    private String name;

    private String service;

    private int numberOfArgBoxes;

    private int locationOfBox;

    private VariableOperation operationType;

    /**
     *
     * @param name
     *            - of the Camunda method
     * @param number
     *            - of parameter the method takes
     * @param loc
     *            - which parameter is the ProcessVariable name
     * @param type
     *            - VariableOperation
     */
    CamundaProcessVariableFunctions(final String name, final String service, int number, int loc,
            VariableOperation type) {
        this.name = name;
        this.service = service;
        this.numberOfArgBoxes = number;
        this.locationOfBox = loc;
        this.operationType = type;
    }

    public String getName() {
        return name;
    }

    public String getService() {
        return service;
    }

    public int getNumberOfArgBoxes() {
        return numberOfArgBoxes;
    }

    public int getLocation() {
        return locationOfBox;
    }

    public VariableOperation getOperationType() {
        return operationType;
    }

    public static CamundaProcessVariableFunctions findByNameAndNumberOfBoxes(final String name, final String service,
            int numberOfBoxes) {

        for (CamundaProcessVariableFunctions f : values()) {
            if (f.getName().equals(name) && f.getNumberOfArgBoxes() == numberOfBoxes
                    && f.getService().equals(service)) {
                return f;
            }
        }
        return null;
    }

}
