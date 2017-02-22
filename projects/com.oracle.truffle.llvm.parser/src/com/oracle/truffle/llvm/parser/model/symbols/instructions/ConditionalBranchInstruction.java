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
package com.oracle.truffle.llvm.parser.model.symbols.instructions;

import java.util.Arrays;
import java.util.List;

import com.oracle.truffle.llvm.parser.model.blocks.InstructionBlock;
import com.oracle.truffle.llvm.parser.model.symbols.Symbols;
import com.oracle.truffle.llvm.parser.model.visitors.InstructionVisitor;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

public final class ConditionalBranchInstruction extends VoidInstruction implements TerminatingInstruction {

    private Symbol condition;

    private final InstructionBlock trueSuccessor;

    private final InstructionBlock falseSuccessor;

    private ConditionalBranchInstruction(InstructionBlock trueSuccessor, InstructionBlock falseSuccessor) {
        this.trueSuccessor = trueSuccessor;
        this.falseSuccessor = falseSuccessor;
    }

    @Override
    public void accept(InstructionVisitor visitor) {
        visitor.visit(this);
    }

    public Symbol getCondition() {
        return condition;
    }

    public InstructionBlock getFalseSuccessor() {
        return falseSuccessor;
    }

    public InstructionBlock getTrueSuccessor() {
        return trueSuccessor;
    }

    @Override
    public List<InstructionBlock> getSuccessors() {
        return Arrays.asList(trueSuccessor, falseSuccessor);
    }

    @Override
    public void replace(Symbol original, Symbol replacement) {
        if (condition == original) {
            condition = replacement;
        }
    }

    public static ConditionalBranchInstruction fromSymbols(Symbols symbols, int conditionIndex, InstructionBlock trueSuccessor, InstructionBlock falseSuccessor) {
        final ConditionalBranchInstruction inst = new ConditionalBranchInstruction(trueSuccessor, falseSuccessor);
        inst.condition = symbols.getSymbol(conditionIndex, inst);
        return inst;
    }

    @Override
    public boolean hasName() {
        return false;
    }
}
