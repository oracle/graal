/*
 * Copyright (c) 2016, 2022, Oracle and/or its affiliates.
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
import com.oracle.truffle.llvm.runtime.types.Type;

public final class GetElementPointerInstruction extends ValueInstruction {

    private final Type baseType;

    private SymbolImpl base;

    private final SymbolImpl[] indices;

    private final boolean isInbounds;

    private GetElementPointerInstruction(Type type, Type baseType, boolean isInbounds, int numIndices) {
        super(type);
        this.baseType = baseType;
        this.indices = new SymbolImpl[numIndices];
        this.isInbounds = isInbounds;
    }

    @Override
    public void accept(SymbolVisitor visitor) {
        visitor.visit(this);
    }

    public SymbolImpl getBasePointer() {
        return base;
    }

    public Type getBaseType() {
        return baseType;
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
            base = replacement;
        }
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] == original) {
                indices[i] = replacement;
            }
        }
    }

    public static GetElementPointerInstruction fromSymbols(SymbolTable symbols, Type type, Type baseType, int pointer, int[] indices, boolean isInbounds) {
        final GetElementPointerInstruction inst = new GetElementPointerInstruction(type, baseType, isInbounds, indices.length);
        inst.base = symbols.getForwardReferenced(pointer, inst);
        for (int i = 0; i < indices.length; i++) {
            inst.indices[i] = symbols.getForwardReferenced(indices[i], inst);
        }
        return inst;
    }
}
