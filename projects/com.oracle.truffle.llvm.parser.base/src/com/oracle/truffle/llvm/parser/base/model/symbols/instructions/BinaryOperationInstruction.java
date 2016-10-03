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

import com.oracle.truffle.llvm.parser.base.model.enums.BinaryOperator;
import com.oracle.truffle.llvm.parser.base.model.enums.Flag;
import com.oracle.truffle.llvm.parser.base.model.symbols.Symbol;
import com.oracle.truffle.llvm.parser.base.model.symbols.Symbols;
import com.oracle.truffle.llvm.parser.base.model.types.FloatingPointType;
import com.oracle.truffle.llvm.parser.base.model.types.Type;
import com.oracle.truffle.llvm.parser.base.model.types.VectorType;
import com.oracle.truffle.llvm.parser.base.model.visitors.InstructionVisitor;

public final class BinaryOperationInstruction extends ValueInstruction {

    private final BinaryOperator operator;

    private final Flag[] flags;

    private Symbol lhs;

    private Symbol rhs;

    private BinaryOperationInstruction(Type type, BinaryOperator operator, Flag[] flags) {
        super(type);
        this.operator = operator;
        this.flags = flags;
    }

    @Override
    public void accept(InstructionVisitor visitor) {
        visitor.visit(this);
    }

    public Flag[] getFlags() {
        return flags;
    }

    public Symbol getLHS() {
        return lhs;
    }

    public BinaryOperator getOperator() {
        return operator;
    }

    public Symbol getRHS() {
        return rhs;
    }

    @Override
    public void replace(Symbol original, Symbol replacement) {
        if (lhs == original) {
            lhs = replacement;
        }
        if (rhs == original) {
            rhs = replacement;
        }
    }

    public static BinaryOperationInstruction fromSymbols(Symbols symbols, Type type, int opcode, int flags, int lhs, int rhs) {
        final boolean isFloatingPoint = type instanceof FloatingPointType || (type instanceof VectorType && ((VectorType) type).getElementType() instanceof FloatingPointType);
        final BinaryOperator operator = BinaryOperator.decode(opcode, isFloatingPoint);
        final BinaryOperationInstruction inst = new BinaryOperationInstruction(type, operator, Flag.decode(operator, flags));
        inst.lhs = symbols.getSymbol(lhs, inst);
        inst.rhs = symbols.getSymbol(rhs, inst);
        return inst;
    }
}
