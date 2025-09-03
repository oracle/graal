/*
 * Copyright (c) 2007, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.Set;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * A symbol table to manages symbol interning with optimized memory usage. This class provides
 * functionality for looking up and creating symbols from byte sequences and strings, with support
 * for weak and strong references.
 *
 * <p>
 * The class is designed to minimize copying during symbol creation, particularly during .class file
 * parsing. A new copy of the input data is created only when the symbol doesn't already exist in
 * the table.
 */
public abstract sealed class Symbols permits SymbolsImpl {
    public static Symbols fromExisting(Set<Symbol<?>> existingSymbols, int initialSymbolTable) {
        return fromExisting(existingSymbols, initialSymbolTable, initialSymbolTable);
    }

    public static Symbols fromExisting(Set<Symbol<?>> existingSymbols, int initialStrongSize, int initialWeakSize) {
        return new SymbolsImpl(existingSymbols, initialStrongSize, initialWeakSize);
    }

    /**
     * Looks up a symbol in the table or returns null if the symbol doesn't exist.
     */
    @TruffleBoundary
    abstract <T> Symbol<T> lookup(ByteSequence byteSequence);

    /**
     * Looks up a symbol in the table or returns {@code null} if the symbol doesn't exist.
     */
    @TruffleBoundary
    <T> Symbol<T> lookup(String string) {
        return lookup(ByteSequence.create(string));
    }

    /**
     * Looks up a symbol in the table creating and storing a new symbol if it does not already
     * exist. By default, new symbols are weakly referenced by the symbol table, allowing to be
     * garbage collected when no longer strongly referenced externally.
     *
     * @param byteSequence the key used to identify the symbol (non-null)
     * @return an existing symbol if found, otherwise a newly created symbol (weakly referenced)
     * @see #getOrCreate(ByteSequence, boolean) to create strongly-referenced symbols
     */
    @TruffleBoundary
    <T> Symbol<T> getOrCreate(ByteSequence byteSequence) {
        return getOrCreate(byteSequence, false);
    }

    /**
     * Looks up or creates a weakly or strongly referenced symbol. This method can also be used to
     * promote a weak symbol to a strong symbol.
     *
     * <p>
     * When {@code ensureStrongReference} is {@code true}, the returned symbol is guaranteed to be
     * strongly referenced by the table, preventing garbage collection even if no external
     * references exist e.g. if the table already contains the symbol, but it is weak, it will be
     * promoted to a strong symbol.
     *
     * @param byteSequence the key used to identify the symbol (non-null)
     * @param ensureStrongReference if {@code true}, guarantees that the returned symbol is strongly
     *            referenced by the symbol table
     * @return an existing symbol if found, otherwise a newly created symbol
     */
    @TruffleBoundary
    abstract <T> Symbol<T> getOrCreate(ByteSequence byteSequence, boolean ensureStrongReference);

    /**
     * Checks if a symbol is weakly referenced in the table.
     */
    @TruffleBoundary
    abstract boolean isWeak(Symbol<?> symbol);

    abstract boolean verify();

    /**
     * This method should NOT be used directly. Symbols created via this method may violate the
     * uniqueness property. Note that symbol uniqueness, is not strictly global, but tied to a
     * symbol table.
     */
    @SuppressWarnings("unchecked")
    static <T> Symbol<T> createSymbolInstanceUnsafe(ByteSequence byteSequence) {
        if (byteSequence instanceof Symbol<?> symbol) {
            return (Symbol<T>) symbol; // already a symbol
        }
        final byte[] rawBytes = Arrays.copyOfRange(byteSequence.getUnderlyingBytes(), byteSequence.offset(), byteSequence.offset() + byteSequence.length());
        return new Symbol<>(rawBytes, byteSequence.hashCode());
    }

    /**
     * Helper class for testing purposes only. Provides direct access to protected methods of the
     * Symbols class.
     */
    public static class TestHelper {
        public static <T> Symbol<T> lookup(Symbols symbols, ByteSequence byteSequence) {
            return symbols.lookup(byteSequence);
        }

        public static <T> Symbol<T> lookup(Symbols symbols, String string) {
            return symbols.lookup(string);
        }

        public static <T> Symbol<T> getOrCreate(Symbols symbols, ByteSequence byteSequence) {
            return symbols.getOrCreate(byteSequence);
        }

        public static <T> Symbol<T> getOrCreate(Symbols symbols, ByteSequence byteSequence, boolean returnStrongSymbol) {
            return symbols.getOrCreate(byteSequence, returnStrongSymbol);
        }

        public static boolean isWeak(Symbols symbols, Symbol<?> symbol) {
            return symbols.isWeak(symbol);
        }

        public static boolean verify(Symbols symbols) {
            return symbols.verify();
        }
    }
}
