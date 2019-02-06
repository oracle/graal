/*
 * Copyright (c) 2007, 2012, Oracle and/or its affiliates. All rights reserved.
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

public final class Symbols {
    // Set generous initial capacity, this one is going to be hit a lot.
    private final ConcurrentHashMap<SymbolKey, Symbol<?>> symbols = new ConcurrentHashMap<>();

    private Symbol<?> lookup(ByteSequence sequence) {
        return symbols.get(new SymbolKey(sequence));
    }

    private Symbol<?> symbolify(final ByteSequence sequence) {
        final SymbolKey key = new SymbolKey(sequence);
        return symbols.computeIfAbsent(key, __ -> {
            // Create Symbol<?>
            final byte[] bytes = Arrays.copyOfRange(sequence.getUnderlyingBytes(),
                            sequence.offset(),
                            sequence.offset() + sequence.length());
            Symbol<?> computed = new Symbol(bytes, sequence.hashCode());
            // Swap byte sequence (possibly holding large byte array) by fresh symbol.
            key.seq = computed;
            return computed;
        });
    }
}
