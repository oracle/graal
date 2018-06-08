/*
 * Copyright (c) 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.text;

import java.util.LinkedList;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.BranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ConditionalBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgDeclareInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.DbgValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.FenceInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.IndirectBranchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.Instruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ResumeInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ReturnInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.StoreInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.SwitchOldInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.UnreachableInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.ValueInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidCallInstruction;
import com.oracle.truffle.llvm.parser.model.symbols.instructions.VoidInvokeInstruction;
import com.oracle.truffle.llvm.parser.model.visitors.ValueInstructionVisitor;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

public class LLInstructionMapper {

    private static final class Mapper extends ValueInstructionVisitor {

        private final Source llSource;
        private final LLVMSourceLocation parentLocation;
        private final LinkedList<LLSourceMap.Instruction> llInstructions;

        private Mapper(Source llSource, LLVMSourceLocation parentLocation, LinkedList<LLSourceMap.Instruction> llInstructions) {
            this.llSource = llSource;
            this.parentLocation = parentLocation;
            this.llInstructions = llInstructions;
        }

        LLVMSourceLocation toLocation(LLSourceMap.Instruction instruction) {
            return instruction.toSourceLocation(llSource, parentLocation);
        }

        @Override
        public void visitValueInstruction(ValueInstruction value) {
            final LLSourceMap.Instruction expected = llInstructions.peekFirst();
            if (expected == null) {
                return;
            }

            if (value.getName().equals(expected.getDescriptor())) {
                value.setSourceLocation(toLocation(expected));
                llInstructions.pollFirst();
            }
        }

        private void checkUnnamedInstruction(String opCode, Instruction inst) {
            final LLSourceMap.Instruction expected = llInstructions.peekFirst();
            if (expected == null) {
                return;
            }

            if (opCode.equals(expected.getDescriptor())) {
                inst.setSourceLocation(toLocation(expected));
                llInstructions.pollFirst();
            }
        }

        @Override
        public void visit(BranchInstruction inst) {
            checkUnnamedInstruction("br", inst);
        }

        @Override
        public void visit(ConditionalBranchInstruction inst) {
            checkUnnamedInstruction("br", inst);
        }

        @Override
        public void visit(IndirectBranchInstruction inst) {
            checkUnnamedInstruction("indirectbr", inst);
        }

        @Override
        public void visit(ReturnInstruction inst) {
            checkUnnamedInstruction("ret", inst);
        }

        @Override
        public void visit(StoreInstruction inst) {
            checkUnnamedInstruction("store", inst);
        }

        @Override
        public void visit(SwitchInstruction inst) {
            checkUnnamedInstruction("switch", inst);
        }

        @Override
        public void visit(SwitchOldInstruction inst) {
            checkUnnamedInstruction("switch", inst);
        }

        @Override
        public void visit(UnreachableInstruction inst) {
            checkUnnamedInstruction("unreachable", inst);
        }

        @Override
        public void visit(VoidCallInstruction inst) {
            checkUnnamedInstruction("call", inst);
        }

        @Override
        public void visit(VoidInvokeInstruction inst) {
            checkUnnamedInstruction("invoke", inst);
        }

        @Override
        public void visit(ResumeInstruction inst) {
            checkUnnamedInstruction("resume", inst);
        }

        @Override
        public void visit(FenceInstruction inst) {
            checkUnnamedInstruction("fence", inst);
        }

        @Override
        public void visit(DbgDeclareInstruction inst) {
            checkUnnamedInstruction("call", inst);
        }

        @Override
        public void visit(DbgValueInstruction inst) {
            checkUnnamedInstruction("call", inst);
        }
    }

    static void setSourceLocations(LLSourceMap sourceMap, FunctionDefinition functionDefinition) {
        final LLSourceMap.Function function = sourceMap.getFunction(functionDefinition.getName());
        if (function == null) {
            return;
        }

        // TODO set function source section
        functionDefinition.accept(new Mapper(sourceMap.getLLSource(), function.toSourceLocation(sourceMap.getLLSource()), function.getInstructionList()));
        sourceMap.clearFunction(function);
    }
}
