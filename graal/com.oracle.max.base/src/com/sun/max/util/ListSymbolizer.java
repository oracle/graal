/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.util;

import java.util.*;

import com.sun.max.program.*;

/**
 */
final class ListSymbolizer<S extends Symbol> implements Symbolizer<S> {

    private final Class<S> symbolType;
    private final List<S> symbols;

    ListSymbolizer(Class<S> symbolType, List<S> symbols) {
        if (symbolType.getName().startsWith("com.sun.max.asm") && Symbolizer.Static.hasPackageExternalAccessibleConstructors(symbolType)) {
            // This test ensures that values passed for symbolic parameters of methods in the
            // generated assemblers are guaranteed to be legal (assuming client code does not
            // inject its own classes into the package where the symbol classes are defined).
            throw ProgramError.unexpected("type of assembler symbol can have values constructed outside of defining package: " + symbolType);
        }
        this.symbolType = symbolType;
        this.symbols = symbols;
        ProgramError.check(!symbols.isEmpty());
    }

    public Class<S> type() {
        return symbolType;
    }

    public int numberOfValues() {
        return symbols.size();
    }

    public S fromValue(int value) {
        for (S symbol : symbols) {
            if (symbol.value() == value) {
                return symbol;
            }
        }
        return null;
    }

    public Iterator<S> iterator() {
        return symbols.iterator();
    }
}
