/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.espresso.classfile.constantpool.Utf8Constant;

/**
 * Global Utf8Constant table.
 */
public final class Utf8ConstantTable {
    private final Symbols symbols;

    // TODO(peterssen): Set generous initial capacity.
    private final ConcurrentHashMap<Symbol<?>, Utf8Constant> cache = new ConcurrentHashMap<>();

    public Utf8ConstantTable(Symbols symbols) {
        this.symbols = symbols;
    }

    public Utf8Constant getOrCreate(ByteSequence bytes) {
        CompilerAsserts.neverPartOfCompilation();
        return cache.computeIfAbsent(symbols.symbolify(bytes), new Function<Symbol<?>, Utf8Constant>() {
            @Override
            public Utf8Constant apply(Symbol<?> value) {
                return new Utf8Constant(value);
            }
        });
    }
}
