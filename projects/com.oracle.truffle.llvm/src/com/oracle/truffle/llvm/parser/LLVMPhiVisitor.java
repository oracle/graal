/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.emf.common.util.EList;

import com.intel.llvm.ireditor.lLVM_IR.BasicBlock;
import com.intel.llvm.ireditor.lLVM_IR.BasicBlockRef;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.Instruction;
import com.intel.llvm.ireditor.lLVM_IR.Instruction_phi;
import com.intel.llvm.ireditor.lLVM_IR.StartingInstruction;
import com.intel.llvm.ireditor.lLVM_IR.Type;
import com.intel.llvm.ireditor.lLVM_IR.ValueRef;

/**
 * This class finds a list of phis that reference a given block.
 */
public class LLVMPhiVisitor {

    private final Map<BasicBlock, List<Phi>> basicBlockReferences = new HashMap<>();

    static class Phi {

        private final ValueRef valueRef;
        private final Type type;
        private final String assignTo;
        private final StartingInstruction startingInstr;

        Phi(String assignTo, ValueRef valueRef, Type type, StartingInstruction startingInstr) {
            this.assignTo = assignTo;
            this.valueRef = valueRef;
            this.type = type;
            this.startingInstr = startingInstr;
        }

        public ValueRef getValueRef() {
            return valueRef;
        }

        public Type getType() {
            return type;
        }

        public String getAssignTo() {
            return assignTo;
        }

        public StartingInstruction getStartingInstr() {
            return startingInstr;
        }

    }

    public static Map<BasicBlock, List<Phi>> visit(FunctionDef function) {
        return new LLVMPhiVisitor().visitFunction(function);
    }

    private Map<BasicBlock, List<Phi>> visitFunction(FunctionDef function) {
        EList<BasicBlock> basicBlocks = function.getBasicBlocks();
        for (BasicBlock block : basicBlocks) {
            basicBlockReferences.put(block, new ArrayList<Phi>());
        }
        for (BasicBlock block : basicBlocks) {
            for (Instruction instr : block.getInstructions()) {
                visitInstruction(instr);
            }
        }
        return basicBlockReferences;
    }

    private void visitInstruction(Instruction instr) {
        if (instr instanceof StartingInstruction) {
            StartingInstruction startingInstr = (StartingInstruction) instr;
            Instruction_phi phi = startingInstr.getInstruction();
            EList<BasicBlockRef> labels = phi.getLabels();
            int valueIndex = 0;
            for (BasicBlockRef blockRef : labels) {
                BasicBlock block = blockRef.getRef();
                List<Phi> valueRefs = basicBlockReferences.get(block);
                ValueRef valueRef = phi.getValues().get(valueIndex++);
                valueRefs.add(new Phi(startingInstr.getName(), valueRef, phi.getType(), startingInstr));
            }
        }
    }

}
