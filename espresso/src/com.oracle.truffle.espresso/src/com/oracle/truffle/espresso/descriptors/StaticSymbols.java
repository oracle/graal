/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.descriptors;

import com.oracle.truffle.espresso.descriptors.Symbol.Name;
import com.oracle.truffle.espresso.descriptors.Symbol.Signature;
import com.oracle.truffle.espresso.descriptors.Symbol.Type;
import com.oracle.truffle.espresso.meta.EspressoError;

/**
 * Global symbols for Espresso.
 * 
 * <p>
 * To be populated in static initializers, always before the first runtime symbol table is spawned.
 *
 * Once the first runtime symbol table is created, this table is frozen and no more symbols can be
 * added. The frozen symbols are used as seed to create new runtime symbol tables.
 */
public final class StaticSymbols {

    private StaticSymbols() {
        /* no instances */
    }

    private static boolean frozen = false;
    private static final Symbols symbols = new Symbols();

    public static Symbol<Name> putName(String name) {
        EspressoError.guarantee(!isFrozen(), "static symbols are frozen");
        EspressoError.guarantee(!name.isEmpty(), "empty name");
        return symbols.symbolify(ByteSequence.create(name));
    }

    public static Symbol<Type> putType(String internalName) {
        EspressoError.guarantee(!isFrozen(), "static symbols are frozen");
        return symbols.symbolify(ByteSequence.create(Types.checkType(internalName)));
    }

    @SafeVarargs
    public static Symbol<Signature> putSignature(Symbol<Type> returnType, Symbol<Type>... parameterTypes) {
        EspressoError.guarantee(!isFrozen(), "static symbols are frozen");
        return symbols.symbolify(ByteSequence.wrap(Signatures.buildSignatureBytes(returnType, parameterTypes)));
    }

    public static boolean isFrozen() {
        return frozen;
    }

    public static Symbols freeze() {
        frozen = true;
        return symbols;
    }
}
