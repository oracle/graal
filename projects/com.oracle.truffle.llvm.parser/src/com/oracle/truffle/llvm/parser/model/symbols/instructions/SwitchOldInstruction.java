/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.model.symbols.instructions;

import com.oracle.truffle.llvm.parser.model.SymbolTable;
import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.functions.FunctionDefinition;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;

public final class SwitchOldInstruction extends VoidInstruction implements TerminatingInstruction {

    private SymbolImpl condition;

    private final InstructionBlock defaultBlock;

    private final long[] cases;

    private final InstructionBlock[] blocks;

    private SwitchOldInstruction(InstructionBlock defaultBlock, long[] cases, InstructionBlock[] blocks) {
        this.defaultBlock = defaultBlock;
        this.cases = cases;
        this.blocks = blocks;
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    public InstructionBlock getCaseBlock(int index) {
        return blocks[index];
    }

    public int getCaseCount() {
        return cases.length;
    }

    public long getCaseValue(int index) {
        return cases[index];
    }

    public SymbolImpl getCondition() {
        return condition;
    }

    public InstructionBlock getDefaultBlock() {
        return defaultBlock;
    }

    @Override
    public int getSuccessorCount() {
        return blocks.length + 1;
    }

    @Override
    public InstructionBlock getSuccessor(int index) {
        assert index >= 0 && index < getSuccessorCount();
        if (index >= blocks.length) {
            return defaultBlock;
        } else {
            return blocks[index];
        }
    }

    @Override
    public void replace(SymbolImpl original, SymbolImpl replacement) {
        if (condition == original) {
            condition = replacement;
        }
    }

    public static SwitchOldInstruction generate(FunctionDefinition function, SymbolTable symbols, int condition, int defaultBlock, long[] cases, int[] targetBlocks) {
        final InstructionBlock[] blocks = new InstructionBlock[targetBlocks.length];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = function.getBlock(targetBlocks[i]);
        }

        final SwitchOldInstruction inst = new SwitchOldInstruction(function.getBlock(defaultBlock), cases, blocks);
        inst.condition = symbols.getForwardReferenced(condition, inst);
        return inst;
    }
}
