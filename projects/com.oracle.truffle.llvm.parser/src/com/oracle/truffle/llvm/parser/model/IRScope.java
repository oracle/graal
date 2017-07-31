/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.model;

import com.oracle.truffle.llvm.parser.metadata.MetadataList;
import com.oracle.truffle.llvm.parser.model.symbols.Symbols;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

import java.util.ArrayList;
import java.util.List;

public abstract class IRScope {

    private final Symbols symbols = new Symbols();
    private final List<Type> valueTypes = new ArrayList<>();
    private final MetadataList metadata = new MetadataList();

    protected IRScope() {
    }

    public void addSymbol(Symbol symbol, Type type) {
        symbols.addSymbol(symbol);
        valueTypes.add(type);
    }

    public boolean isValueForwardRef(long index) {
        return index >= valueTypes.size();
    }

    public int getNextValueIndex() {
        return valueTypes.size();
    }

    public Type getValueType(int i) {
        if (i < valueTypes.size()) {
            return valueTypes.get(i);
        } else {
            return null;
        }
    }

    public void nameSymbol(int index, String argName) {
        symbols.setSymbolName(index, argName);
    }

    public Symbols getSymbols() {
        return symbols;
    }

    public void initialize(IRScope other) {
        valueTypes.addAll(other.valueTypes);
        symbols.addSymbols(other.symbols);
        metadata.initialize(other.metadata);
    }

    public abstract void nameBlock(int index, String name);

    public MetadataList getMetadata() {
        return metadata;
    }
}
