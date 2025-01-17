/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * To be populated in static initializers, always before the first runtime symbol table is spawned.
 *
 * <p>
 * Once the first runtime symbol table is created, this table is frozen and no more symbols can be
 * added. The frozen symbols are used as seed to create new runtime symbol tables.
 */
public final class StaticSymbols {

    private boolean frozen = false;
    private final Symbols symbols;

    public StaticSymbols(StaticSymbols seed) {
        this.symbols = new Symbols(seed.freeze());
    }

    public StaticSymbols() {
        this.symbols = new Symbols();
    }

    public Symbol<Name> putName(String nameString) {
        ErrorUtil.guarantee(!nameString.isEmpty(), "empty name");
        ByteSequence byteSequence = ByteSequence.create(nameString);
        Symbol<Name> name = symbols.lookup(byteSequence);
        if (name != null) {
            return name;
        }
        ErrorUtil.guarantee(!isFrozen(), "static symbols are frozen");
        return symbols.symbolify(byteSequence);
    }

    public Symbol<Type> putType(String internalName) {
        ByteSequence byteSequence = ByteSequence.create(internalName);
        ErrorUtil.guarantee(Validation.validTypeDescriptor(byteSequence, true), "invalid type");
        Symbol<Type> type = symbols.lookup(byteSequence);
        if (type != null) {
            return type;
        }
        ErrorUtil.guarantee(!isFrozen(), "static symbols are frozen");
        return symbols.symbolify(byteSequence);
    }

    @SafeVarargs
    public final Symbol<Signature> putSignature(Symbol<Type> returnType, Symbol<Type>... parameterTypes) {
        ByteSequence signatureBytes = SignatureSymbols.createSignature(returnType, parameterTypes);
        ErrorUtil.guarantee(Validation.validSignatureDescriptor(signatureBytes, false), "invalid signature");
        Symbol<Signature> signature = symbols.lookup(signatureBytes);
        if (signature != null) {
            return signature;
        }
        ErrorUtil.guarantee(!isFrozen(), "static symbols are frozen");
        return symbols.symbolify(signatureBytes);
    }

    public boolean isFrozen() {
        return frozen;
    }

    public Symbols freeze() {
        frozen = true;
        return symbols;
    }
}
