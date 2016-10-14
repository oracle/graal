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
package com.oracle.truffle.llvm.parser.base.model.symbols.instructions;

import com.oracle.truffle.llvm.parser.base.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.base.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.base.model.symbols.Symbol;
import com.oracle.truffle.llvm.parser.base.model.visitors.InstructionVisitor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class IndirectBranchInstruction implements VoidInstruction, TerminatingInstruction {

    private Symbol address;

    private final InstructionBlock[] successors;

    private IndirectBranchInstruction(InstructionBlock[] successors) {
        this.successors = successors;
    }

    @Override
    public void accept(InstructionVisitor visitor) {
        visitor.visit(this);
    }

    public Symbol getAddress() {
        return address;
    }

    public int getSuccessorCount() {
        return successors.length;
    }

    public InstructionBlock getSuccessor(int index) {
        return successors[index];
    }

    @Override
    public List<InstructionBlock> getSuccessors() {
        return Arrays.stream(successors).collect(Collectors.toList());
    }

    @Override
    public void replace(Symbol original, Symbol replacement) {
        if (address == original) {
            address = replacement;
        }
    }

    public static IndirectBranchInstruction generate(FunctionDefinition function, int address, int[] successors) {
        final InstructionBlock[] blocks = new InstructionBlock[successors.length];
        for (int i = 0; i < successors.length; i++) {
            blocks[i] = function.getBlock(successors[i]);
        }
        final IndirectBranchInstruction inst = new IndirectBranchInstruction(blocks);
        inst.address = function.getSymbols().getSymbol(address, inst);
        return inst;
    }
}
