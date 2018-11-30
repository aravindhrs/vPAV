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
package de.viadee.bpm.vPAV.processing.model.data;

import java.util.Collection;
import java.util.Map;

/**
 * Represents an inital process variable operation.
 *
 */
public class InitialProcessVariableOperation {

    private String filePath;

    private Map<String, Boolean> initialProcessVariables;

    private Map<String, Collection<String>> messageIdToVariableMap;

    private boolean initialized;

    public InitialProcessVariableOperation(final String filePath, final Map<String, Boolean> initialProcessVariables,
                                           final Map<String, Collection<String>> messageIdToVariableMap) {
        super();
        this.filePath = filePath;
        this.initialProcessVariables = initialProcessVariables;
        this.messageIdToVariableMap = messageIdToVariableMap;
        this.initialized = initialized;
    }


    public String getFilePath() { return filePath; }

    public Map<String, Boolean> getInitialProcessVariables() {
        return initialProcessVariables;
    }

    public Map<String, Collection<String>> getMessageIdToVariableMap() {
        return messageIdToVariableMap;
    }

    public boolean isInitialized() {
        return initialized;
    }

}
