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
package com.oracle.truffle.llvm.parser.model.symbols.constants.aggregate;

import com.oracle.truffle.llvm.parser.LLVMParserRuntime;
import com.oracle.truffle.llvm.parser.model.visitors.SymbolVisitor;
import com.oracle.truffle.llvm.runtime.GetStackSpaceFactory;
import com.oracle.truffle.llvm.runtime.datalayout.DataLayout;
import com.oracle.truffle.llvm.runtime.nodes.api.LLVMExpressionNode;
import com.oracle.truffle.llvm.runtime.types.StructureType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.Type.TypeOverflowException;

public final class StructureConstant extends AggregateConstant {

    StructureConstant(StructureType type, int valueCount) {
        super(type, valueCount);
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    public Type getElementType(int index) {
        return getElement(index).getType();
    }

    public boolean isPacked() {
        return ((StructureType) getType()).isPacked();
    }

    @Override
    public String toString() {
        return String.format("{%s}", super.toString());
    }

    @Override
    public LLVMExpressionNode createNode(LLVMParserRuntime runtime, DataLayout dataLayout, GetStackSpaceFactory stackFactory) {
        int elementCount = getElementCount();
        Type[] types = new Type[elementCount];
        LLVMExpressionNode[] constants = new LLVMExpressionNode[elementCount];
        for (int i = 0; i < elementCount; i++) {
            types[i] = getElementType(i);
            constants[i] = getElement(i).createNode(runtime, dataLayout, stackFactory);
        }
        return runtime.getNodeFactory().createStructureConstantNode(getType(), stackFactory, isPacked(), types, constants);
    }

    @Override
    public void addToBuffer(Buffer buffer, LLVMParserRuntime runtime, DataLayout dataLayout, GetStackSpaceFactory stackFactory) throws TypeOverflowException {
        long startOffset = buffer.getBuffer().position();
        long offset = 0;
        boolean packed = isPacked();
        for (int i = 0; i < getElementCount(); i++) {
            Type resolvedType = getElementType(i);
            if (!packed) {
                int padding = Type.getPadding(offset, resolvedType, dataLayout);
                for (int j = 0; j < padding; j++) {
                    buffer.getBuffer().put((byte) 0);
                }
                offset = Type.addUnsignedExact(offset, padding);
            }
            getElement(i).addToBuffer(buffer, runtime, dataLayout, stackFactory);
            offset = Type.addUnsignedExact(offset, resolvedType.getSize(dataLayout));
        }
        buffer.getBuffer().position((int) (startOffset + getType().getSize(dataLayout)));
    }
}
