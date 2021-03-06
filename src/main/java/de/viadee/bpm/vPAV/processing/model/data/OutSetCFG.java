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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import soot.toolkits.graph.Block;

import java.util.List;

/**
 * 
 * helper class storing information for Data-flow analysis algorithm
 * 
 * 
 *
 */
public class OutSetCFG {

    private List<VariableBlock> variableBlocks;

    public OutSetCFG(List<VariableBlock> vbs) {

        this.variableBlocks = vbs;
    }

    public VariableBlock getVariableBlock(Block b) {

        for (VariableBlock vb : variableBlocks) {

            if (vb.getBlock().equals(b)) {
                return vb;
            }
        }
        return null;
    }

    public void addVariableBlock(VariableBlock vb) {

        this.variableBlocks.add(vb);
    }

    public ListMultimap<String, ProcessVariableOperation> getAllProcessVariables() {

    	ListMultimap<String, ProcessVariableOperation> variables = ArrayListMultimap.create();

        for (VariableBlock vb : variableBlocks) {

            variables.putAll(vb.getProcessVariablesMapped());

        }
        return variables;
    }

    public List<VariableBlock> getAllVariableBlocks() {

        return variableBlocks;
    }

}
