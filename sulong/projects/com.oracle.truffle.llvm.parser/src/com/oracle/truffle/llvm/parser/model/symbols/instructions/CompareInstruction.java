/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.SymbolTable;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.CompareOperator;
import com.oracle.truffle.llvm.runtime.types.PrimitiveType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;

public final class CompareInstruction extends ValueInstruction {
    private static final long INTEGER_OPERATOR_FLAG = 32L;

    private static final CompareOperator[] FP_COMPARISONS = {CompareOperator.FP_FALSE, CompareOperator.FP_ORDERED_EQUAL, CompareOperator.FP_ORDERED_GREATER_THAN,
                    CompareOperator.FP_ORDERED_GREATER_OR_EQUAL, CompareOperator.FP_ORDERED_LESS_THAN, CompareOperator.FP_ORDERED_LESS_OR_EQUAL, CompareOperator.FP_ORDERED_NOT_EQUAL,
                    CompareOperator.FP_ORDERED, CompareOperator.FP_UNORDERED, CompareOperator.FP_UNORDERED_EQUAL, CompareOperator.FP_UNORDERED_GREATER_THAN,
                    CompareOperator.FP_UNORDERED_GREATER_OR_EQUAL, CompareOperator.FP_UNORDERED_LESS_THAN, CompareOperator.FP_UNORDERED_LESS_OR_EQUAL, CompareOperator.FP_UNORDERED_NOT_EQUAL,
                    CompareOperator.FP_TRUE};

    private static final CompareOperator[] INT_COMPARISONS = {CompareOperator.INT_EQUAL, CompareOperator.INT_NOT_EQUAL, CompareOperator.INT_UNSIGNED_GREATER_THAN,
                    CompareOperator.INT_UNSIGNED_GREATER_OR_EQUAL, CompareOperator.INT_UNSIGNED_LESS_THAN, CompareOperator.INT_UNSIGNED_LESS_OR_EQUAL, CompareOperator.INT_SIGNED_GREATER_THAN,
                    CompareOperator.INT_SIGNED_GREATER_OR_EQUAL, CompareOperator.INT_SIGNED_LESS_THAN, CompareOperator.INT_SIGNED_LESS_OR_EQUAL};

    private final CompareOperator operator;

    private SymbolImpl lhs;

    private SymbolImpl rhs;

    private CompareInstruction(Type type, CompareOperator operator) {
        super(calculateResultType(type));
        this.operator = operator;
    }

    private static Type calculateResultType(Type type) {
        // The comparison performed always yields either an i1 or vector of i1 as result
        if (type instanceof VectorType) {
            return new VectorType(PrimitiveType.I1, ((VectorType) type).getNumberOfElementsInt());
        }
        return PrimitiveType.I1;
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    public SymbolImpl getLHS() {
        return lhs;
    }

    public CompareOperator getOperator() {
        return operator;
    }

    public SymbolImpl getRHS() {
        return rhs;
    }

    @Override
    public void replace(SymbolImpl original, SymbolImpl replacement) {
        if (lhs == original) {
            lhs = replacement;
        }
        if (rhs == original) {
            rhs = replacement;
        }
    }

    public static CompareInstruction fromSymbols(SymbolTable symbols, Type type, int opcode, int lhs, int rhs) {
        final CompareInstruction cmpInst = new CompareInstruction(type, decodeCompareOperator(opcode));
        cmpInst.lhs = symbols.getForwardReferenced(lhs, cmpInst);
        cmpInst.rhs = symbols.getForwardReferenced(rhs, cmpInst);
        return cmpInst;
    }

    public static CompareOperator decodeCompareOperator(long opcode) {
        if (opcode >= 0 && opcode < FP_COMPARISONS.length) {
            return FP_COMPARISONS[(int) opcode];
        } else {
            long iopcode = opcode - INTEGER_OPERATOR_FLAG;
            if (opcode >= 0 && iopcode < INT_COMPARISONS.length) {
                return INT_COMPARISONS[(int) iopcode];
            }
        }
        return null;
    }
}
