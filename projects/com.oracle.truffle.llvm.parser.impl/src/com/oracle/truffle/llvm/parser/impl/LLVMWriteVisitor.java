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
package com.oracle.truffle.llvm.parser.impl;

import java.util.HashSet;
import java.util.Set;

import com.intel.llvm.ireditor.lLVM_IR.BasicBlock;
import com.intel.llvm.ireditor.lLVM_IR.FunctionDef;
import com.intel.llvm.ireditor.lLVM_IR.Instruction;
import com.intel.llvm.ireditor.lLVM_IR.MiddleInstruction;
import com.intel.llvm.ireditor.lLVM_IR.NamedMiddleInstruction;

public final class LLVMWriteVisitor {

    private final FunctionDef function;
    private final Set<String> writtenVariables = new HashSet<>();

    private LLVMWriteVisitor(FunctionDef function) {
        this.function = function;
    }

    public static Set<String> visit(FunctionDef function) {
        return new LLVMWriteVisitor(function).visitFunction();
    }

    private Set<String> visitFunction() {
        for (BasicBlock block : function.getBasicBlocks()) {
            for (Instruction instr : block.getInstructions()) {
                visitInstruction(instr);
            }
        }
        return writtenVariables;
    }

    private void visitInstruction(Instruction instr) {
        if (instr instanceof MiddleInstruction) {
            MiddleInstruction middleInstr = (MiddleInstruction) instr;
            if (middleInstr.getInstruction() instanceof NamedMiddleInstruction) {
                NamedMiddleInstruction namedMiddleInstr = (NamedMiddleInstruction) middleInstr.getInstruction();
                writtenVariables.add(namedMiddleInstr.getName());
            }
        }
    }

}
