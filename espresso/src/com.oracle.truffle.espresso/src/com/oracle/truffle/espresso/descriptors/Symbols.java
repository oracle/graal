/*
 * Copyright (c) 2007, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * A copy is created (almost) only if the symbol doesn't exist. This allows copy-less
 * symbol-ification for utf8 entries during .class file parsing.
 */
public final class Symbols {
    // Set generous initial capacity, this one is going to be hit a lot.
    private final ConcurrentHashMap<SymbolKey, Symbol<?>> symbols = new ConcurrentHashMap<>();

    public Symbols() {
        /* nop */
    }

    public Symbols(Symbols seed) {
        this.symbols.putAll(seed.symbols);
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    <T> Symbol<T> lookup(ByteSequence sequence) {
        return (Symbol<T>) symbols.get(new SymbolKey(sequence));
    }

    <T> Symbol<T> lookup(String str) {
        return lookup(ByteSequence.create(str));
    }

    @SuppressWarnings("unchecked")
    @TruffleBoundary
    <T> Symbol<T> symbolify(final ByteSequence sequence) {
        final SymbolKey key = new SymbolKey(sequence);
        return (Symbol<T>) symbols.computeIfAbsent(key, new Function<SymbolKey, Symbol<?>>() {
            @Override
            public Symbol<?> apply(SymbolKey unused) {
                // Create Symbol<?>
                final byte[] bytes = Arrays.copyOfRange(sequence.getUnderlyingBytes(),
                                sequence.offset(),
                                sequence.offset() + sequence.length());
                Symbol<?> computed = new Symbol<>(bytes, sequence.hashCode());
                // Swap the byte sequence, which could be holding a large underlying byte array, by
                // a fresh symbol.
                //
                // ConcurrentHashMap provides no guarantees about how many times the mapping
                // function can be potentially called. In the worst case it's possible to end up
                // with a key.seq != computed e.g. two different copies of the symbol.
                // This wastes space but remains correct since key.seq never leaks out of the
                // symbol map and it's byte-equals to the computed value.
                // It doesn't keep the underlying byte array (which can be large e.g. .class file
                // contents) from being collected.
                key.seq = computed;
                return computed;
            }
        });
    }
}
