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
package com.oracle.truffle.llvm.parser.model.symbols.constants;

import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.parser.model.SymbolTable;
import com.oracle.truffle.llvm.parser.model.enums.BinaryOperator;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.parser.util.LLVMBitcodeTypeHelper;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VectorType;

public final class BinaryOperationConstant extends AbstractConstant {

    private final BinaryOperator operator;
    private Constant lhs;
    private Constant rhs;

    private BinaryOperationConstant(Type type, BinaryOperator operator) {
        super(type);
        this.operator = operator;
        this.lhs = null;
        this.rhs = null;
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public void replace(SymbolImpl original, SymbolImpl replacement) {
        if (lhs == original) {
            lhs = (Constant) replacement;
        }
        if (rhs == original) {
            rhs = (Constant) replacement;
        }
    }

    public static BinaryOperationConstant fromSymbols(SymbolTable symbols, Type type, int opcode, int lhs, int rhs) {
        boolean isFloatingPoint = Type.isFloatingpointType(type) || (type instanceof VectorType && Type.isFloatingpointType(((VectorType) type).getElementType()));
        BinaryOperator operator = BinaryOperator.decode(opcode, isFloatingPoint);
        BinaryOperationConstant constant = new BinaryOperationConstant(type, operator);
        constant.lhs = (Constant) symbols.getForwardReferenced(lhs, constant);
        constant.rhs = (Constant) symbols.getForwardReferenced(rhs, constant);
        return constant;
    }

    @Override
    public LLVMExpressionNode createNode(LLVMParserRuntime runtime, DataLayout dataLayout, GetStackSpaceFactory stackFactory) {
        return LLVMBitcodeTypeHelper.createArithmeticInstruction(lhs.createNode(runtime, dataLayout, stackFactory), rhs.createNode(runtime, dataLayout, stackFactory), operator, getType(),
                        runtime.getNodeFactory());
    }
}
