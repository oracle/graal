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
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.parser.nodes.LLVMSymbolReadResolver;
import com.oracle.truffle.llvm.runtime.CommonNodeFactory;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class GetElementPointerConstant extends AbstractConstant {

    private final boolean isInbounds;

    private Constant base;

    private final Constant[] indices;

    private GetElementPointerConstant(Type type, boolean isInbounds, int size) {
        super(type);
        this.isInbounds = isInbounds;
        indices = new Constant[size];
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    public SymbolImpl getBasePointer() {
        return base;
    }

    public SymbolImpl[] getIndices() {
        return indices;
    }

    public boolean isInbounds() {
        return isInbounds;
    }

    @Override
    public void replace(SymbolImpl original, SymbolImpl replacement) {
        if (base == original) {
            base = (Constant) replacement;
        }
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] == original) {
                indices[i] = (Constant) replacement;
            }
        }
    }

    public static GetElementPointerConstant fromSymbols(SymbolTable symbols, Type type, int pointer, int[] indices, boolean isInbounds) {
        final GetElementPointerConstant constant = new GetElementPointerConstant(type, isInbounds, indices.length);

        constant.base = (Constant) symbols.getForwardReferenced(pointer, constant);
        for (int i = 0; i < indices.length; i++) {
            constant.indices[i] = (Constant) symbols.getForwardReferenced(indices[i], constant);
        }
        return constant;
    }

    @Override
    public LLVMExpressionNode createNode(LLVMParserRuntime runtime, DataLayout dataLayout, GetStackSpaceFactory stackFactory) {
        LLVMExpressionNode[] indexNodes = new LLVMExpressionNode[indices.length];
        Long[] indexConstants = new Long[indices.length];
        Type[] indexTypes = new Type[indices.length];

        for (int i = indices.length - 1; i >= 0; i--) {
            Constant indexSymbol = indices[i];
            indexConstants[i] = LLVMSymbolReadResolver.evaluateLongIntegerConstant(indexSymbol);
            indexTypes[i] = indexSymbol.getType();
            if (indexConstants[i] == null) {
                indexNodes[i] = indexSymbol.createNode(runtime, dataLayout, stackFactory);
            }
        }

        LLVMExpressionNode currentAddress = base.createNode(runtime, dataLayout, stackFactory);
        Type currentType = base.getType();
        return CommonNodeFactory.createNestedElementPointerNode(runtime.getNodeFactory(), dataLayout, indexNodes, indexConstants, indexTypes, currentAddress, currentType);
    }
}
