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
package com.oracle.truffle.llvm.parser.metadata;

import com.oracle.truffle.llvm.parser.listeners.Metadata;
import com.oracle.truffle.llvm.parser.model.IRScope;
import com.oracle.truffle.llvm.runtime.types.MetaType;
import com.oracle.truffle.llvm.runtime.types.Type;
import com.oracle.truffle.llvm.runtime.types.VoidType;
import com.oracle.truffle.llvm.runtime.types.symbols.Symbol;

public final class MDValue implements MDBaseNode, Symbol {

    private final Type type;
    private Symbol value;

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    private MDValue(Type type) {
        this.type = type;
    }

    @Override
    public Type getType() {
        return type;
    }

    public Symbol getValue() {
        return value;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
    }

    @Override
    public void replace(Symbol oldValue, Symbol newValue) {
        if (value == oldValue) {
            value = newValue;
        }
    }

    @Override
    public String toString() {
        return String.format("Value (%s)", value);
    }

    private static final int VALUE_ARGINDEX_TYPE = 0;
    private static final int VALUE_ARGINDEX_VALUE = 1;

    public static MDBaseNode create(long[] args, Metadata md) {
        final Type type = md.getTypeById(args[VALUE_ARGINDEX_TYPE]);
        if (type == MetaType.METADATA || VoidType.INSTANCE.equals(type)) {
            return MDVoidNode.VOID;
        }
        final MDValue value = new MDValue(type);
        value.value = md.getContainer().getSymbols().getSymbol((int) args[VALUE_ARGINDEX_VALUE], value);
        return value;
    }

    public static MDValue create(Type type, long index, IRScope scope) {
        final MDValue value = new MDValue(type);
        value.value = scope.getSymbols().getSymbol((int) index, value);
        return value;
    }

    public static MDValue create(Symbol symbol) {
        final MDValue value = new MDValue(symbol.getType());
        value.value = symbol;
        return value;
    }
}
