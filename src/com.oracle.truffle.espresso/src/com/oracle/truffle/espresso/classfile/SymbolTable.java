/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.classfile;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

/**
 * Symbol cache.
 */
public final class SymbolTable {
    /**
     * Searching and adding entries to this map is only performed by {@linkplain #make(String) one
     * method} which is synchronized.
     */
    private final EconomicMap<String, Utf8Constant> symbols = EconomicMap.create(Equivalence.DEFAULT);

    public synchronized Utf8Constant lookup(String key) {
        return symbols.get(key);
    }

    public synchronized Utf8Constant make(String key) {
        Utf8Constant value = symbols.get(key);
        if (value == null) {
            value = new Utf8Constant(key);
            symbols.put(key, value);
        }
        return value;
    }

    public final Utf8Constant INIT = make("<init>");
    public final Utf8Constant CLINIT = make("<clinit>");
    public final Utf8Constant FINALIZE = make("finalize");
}
