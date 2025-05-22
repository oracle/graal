/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

final class SymbolsImpl extends Symbols {
    // Set generous initial capacity, these are going to be hit a lot.
    private final ConcurrentHashMap<ByteSequence, Symbol<?>> strongMap;
    private final WeakHashMap<ByteSequence, WeakReference<Symbol<?>>> weakMap;
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    SymbolsImpl(int initialStrongSize, int initialWeakSize) {
        if (initialWeakSize > 0) {
            this.strongMap = new ConcurrentHashMap<>(initialStrongSize);
        } else {
            this.strongMap = new ConcurrentHashMap<>();
        }
        if (initialWeakSize > 0) {
            this.weakMap = new WeakHashMap<>(initialWeakSize);
        } else {
            this.weakMap = new WeakHashMap<>();
        }
    }

    SymbolsImpl(Set<Symbol<?>> existingSymbols, int initialStrongSize, int initialWeakSize) {
        this(initialStrongSize, initialWeakSize);
        for (Symbol<?> symbol : existingSymbols) {
            this.strongMap.put(symbol, symbol);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    @TruffleBoundary
    <T> Symbol<T> lookup(ByteSequence byteSequence) {
        // Lock-free fast path, common symbols are usually strongly referenced e.g.
        // Ljava/lang/Object;
        Symbol<T> result = (Symbol<T>) strongMap.get(byteSequence);
        if (result != null) {
            return result;
        }
        readWriteLock.readLock().lock();
        try {
            result = (Symbol<T>) strongMap.get(byteSequence);
            if (result != null) {
                return result;
            }
            WeakReference<Symbol<?>> weakValue = weakMap.get(byteSequence);
            if (weakValue != null) {
                return (Symbol<T>) weakValue.get();
            } else {
                return null;
            }
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    @TruffleBoundary
    @SuppressWarnings("unchecked")
    @Override
    <T> Symbol<T> getOrCreate(ByteSequence byteSequence, boolean ensureStrongReference) {
        // Lock-free fast path, common symbols are usually strongly referenced e.g.
        // Ljava/lang/Object;
        Symbol<T> symbol = (Symbol<T>) strongMap.get(byteSequence);
        if (symbol != null) {
            return symbol;
        }

        readWriteLock.writeLock().lock();
        try {
            // Must peek again within the lock because the symbol may have been promoted from weak
            // to strong by another thread; querying only the weak map wouldn't be correct.
            symbol = (Symbol<T>) strongMap.get(byteSequence);
            if (symbol != null) {
                return symbol;
            }

            if (ensureStrongReference) {
                WeakReference<Symbol<?>> weakValue = weakMap.remove(byteSequence);
                if (weakValue != null) {
                    // Promote weak symbol to strong.
                    symbol = (Symbol<T>) weakValue.get();
                    // The weak symbol may have been collected.
                    if (symbol != null) {
                        strongMap.put(symbol, symbol);
                        return symbol;
                    }
                }

                // Create new strong symbol.
                symbol = createSymbolInstanceUnsafe(byteSequence);
                strongMap.put(symbol, symbol);
                return symbol;
            } else {
                WeakReference<Symbol<?>> weakValue = weakMap.get(byteSequence);
                if (weakValue != null) {
                    symbol = (Symbol<T>) weakValue.get();
                    // The weak symbol may have been collected.
                    if (symbol != null) {
                        return symbol;
                    }
                }

                // Create new weak symbol.
                symbol = createSymbolInstanceUnsafe(byteSequence);
                weakMap.put(symbol, new WeakReference<>(symbol));
                return symbol;
            }
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @TruffleBoundary
    @Override
    boolean isWeak(Symbol<?> symbol) {
        assert lookup(symbol) == symbol;
        return !strongMap.containsKey(symbol);
    }

    @Override
    boolean verify() {
        readWriteLock.writeLock().lock();
        try {
            Set<ByteSequence> weakKeys = weakMap.keySet();
            Set<ByteSequence> strongKeys = strongMap.keySet();
            return weakKeys.stream().allMatch(key -> key instanceof Symbol) &&
                            strongKeys.stream().allMatch(key -> key instanceof Symbol) &&

                            weakKeys.stream().noneMatch(strongMap::containsKey) &&
                            strongKeys.stream().noneMatch(weakMap::containsKey) &&

                            weakKeys.stream().allMatch(key -> key == weakMap.get(key).get()) &&
                            strongKeys.stream().allMatch(key -> key == strongMap.get(key)) &&

                            weakKeys.stream().allMatch(key -> isWeak((Symbol<?>) key)) &&
                            strongKeys.stream().noneMatch(key -> isWeak((Symbol<?>) key));
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }
}
