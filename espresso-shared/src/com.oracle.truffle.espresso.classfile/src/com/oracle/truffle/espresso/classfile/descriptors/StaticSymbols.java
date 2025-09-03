/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.espresso.classfile.descriptors;

import java.util.HashSet;
import java.util.Set;

import org.graalvm.collections.EconomicMap;

/**
 * To be populated in static initializers, always before the first runtime symbol table is spawned.
 *
 * <p>
 * Once the first runtime symbol table is created, this table is frozen and no more symbols can be
 * added. The frozen symbols are used as seed to create new runtime symbol tables.
 */
public final class StaticSymbols {

    private boolean frozen = false;
    private final EconomicMap<ByteSequence, Symbol<?>> symbols;

    public StaticSymbols(StaticSymbols seed, int initialCapacity) {
        this.symbols = EconomicMap.create(initialCapacity);
        this.symbols.putAll(seed.symbols);
    }

    public StaticSymbols(int initialCapacity) {
        this.symbols = EconomicMap.create(initialCapacity);
    }

    public Symbol<Name> putName(String nameString) {
        ErrorUtil.guarantee(!nameString.isEmpty(), "empty name");
        ByteSequence byteSequence = ByteSequence.create(nameString);
        return getOrCreateSymbol(byteSequence);
    }

    public Symbol<Type> putType(String internalName) {
        ByteSequence byteSequence = ByteSequence.create(internalName);
        ErrorUtil.guarantee(Validation.validTypeDescriptor(byteSequence, true), "invalid type");
        return getOrCreateSymbol(byteSequence);
    }

    @SafeVarargs
    public final Symbol<Signature> putSignature(Symbol<Type> returnType, Symbol<Type>... parameterTypes) {
        ByteSequence signatureBytes = SignatureSymbols.createSignature(returnType, parameterTypes);
        ErrorUtil.guarantee(Validation.validSignatureDescriptor(signatureBytes), "invalid signature");
        return getOrCreateSymbol(signatureBytes);
    }

    public boolean isFrozen() {
        return frozen;
    }

    public Set<Symbol<?>> freeze() {
        frozen = true;
        Set<Symbol<?>> result = new HashSet<>(symbols.size());
        symbols.getValues().forEach(result::add);
        return result;
    }

    @SuppressWarnings("unchecked")
    private <T> Symbol<T> getOrCreateSymbol(ByteSequence byteSequence) {
        Symbol<T> symbol = (Symbol<T>) symbols.get(byteSequence);
        if (symbol != null) {
            return symbol;
        }
        ErrorUtil.guarantee(!isFrozen(), "static symbols are frozen");
        symbol = Symbols.createSymbolInstanceUnsafe(byteSequence);
        symbols.put(symbol, symbol);
        return symbol;
    }
}
