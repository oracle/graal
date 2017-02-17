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
package com.oracle.truffle.llvm.parser.model.symbols.constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.oracle.truffle.llvm.parser.model.symbols.Symbols;
import com.oracle.truffle.llvm.parser.model.visitors.ConstantVisitor;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

public final class GetElementPointerConstant extends AbstractConstant {

    private final boolean isInbounds;

    private Symbol base;

    private final List<Symbol> indices = new ArrayList<>();

    private GetElementPointerConstant(Type type, boolean isInbounds) {
        super(type);
        this.isInbounds = isInbounds;
    }

    @Override
    public void accept(ConstantVisitor visitor) {
        visitor.visit(this);
    }

    public Symbol getBasePointer() {
        return base;
    }

    public List<Symbol> getIndices() {
        return Collections.unmodifiableList(indices);
    }

    public boolean isInbounds() {
        return isInbounds;
    }

    @Override
    public void replace(Symbol original, Symbol replacement) {
        if (base == original) {
            base = replacement;
        }
        for (int i = 0; i < indices.size(); i++) {
            if (indices.get(i) == original) {
                indices.set(i, replacement);
            }
        }
    }

    public static GetElementPointerConstant fromSymbols(Symbols symbols, Type type, int pointer, int[] indices, boolean isInbounds) {
        final GetElementPointerConstant constant = new GetElementPointerConstant(type, isInbounds);

        constant.base = symbols.getSymbol(pointer, constant);
        for (int index : indices) {
            constant.indices.add(symbols.getSymbol(index, constant));
        }
        return constant;
    }
}
