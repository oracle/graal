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
package com.oracle.truffle.espresso.classfile.descriptors;

import java.lang.ref.WeakReference;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.espresso.classfile.constantpool.Utf8Constant;

/**
 * Global Utf8Constant table.
 *
 * <p>
 * A proper implementation would require an ephemeron e.g. consider the reference to Utf8Constant
 * value strong iff the Symbol key is strongly reachable. But ephemerons cannot be implemented in
 * Java. In this particular case, Utf8Constant(s) are strongly referenced by constant pools of
 * loaded classes. Ut8Constants are likely to stay alive for a long time, unless classes are
 * unloaded; which should be rare. In case the Utf8Constant value is collected, it is just
 * re-created again since it can be re-constructed from the key/symbol.
 */
public final class Utf8ConstantTable {
    private final Symbols symbols;
    private final WeakHashMap<ByteSequence, WeakReference<Utf8Constant>> weakMap;
    private final ConcurrentHashMap<ByteSequence, Utf8Constant> strongMap;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public Utf8ConstantTable(Symbols symbols, int initialCapacity) {
        this.symbols = symbols;
        this.weakMap = new WeakHashMap<>(initialCapacity);
        this.strongMap = new ConcurrentHashMap<>(initialCapacity);
    }

    @TruffleBoundary
    Utf8Constant lookup(ByteSequence byteSequence) {
        // First check: Lock-free access to strongMap (common case)
        Utf8Constant result = strongMap.get(byteSequence);
        if (result != null) {
            return result;
        }

        lock.readLock().lock();
        try {
            // Recheck strongMap under the lock, in case the symbol was promoted from weak to
            // strong.
            result = strongMap.get(byteSequence);
            if (result != null) {
                return result;
            }
            WeakReference<Utf8Constant> weakRef = weakMap.get(byteSequence);
            if (weakRef != null) {
                result = weakRef.get();
            }
            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    @TruffleBoundary
    public Utf8Constant getOrCreate(ByteSequence byteSequence, boolean ensureStrongReference) {
        // Lock-free fast path, common symbols are usually strongly referenced e.g.
        // Ljava/lang/Object;
        Utf8Constant result = strongMap.get(byteSequence);
        if (result != null) {
            return result;
        }

        Symbol<?> symbol = symbols.getOrCreate(byteSequence, ensureStrongReference);

        lock.writeLock().lock();
        try {
            // Recheck strongMap under lock.
            result = strongMap.get(symbol);
            if (result != null) {
                return result;
            }

            // Optimization to reduce the number of weak references, if the associated symbol is
            // already strongly-referenced, then the Utf8Constant must be also strongly-referenced.
            if (ensureStrongReference || !symbols.isWeak(symbol)) {
                WeakReference<Utf8Constant> weakRef = weakMap.remove(symbol);
                if (weakRef != null) {
                    // Promote it to strong reference.
                    result = weakRef.get();
                    // The weak reference may have been collected.
                    if (result != null) {
                        var previous = strongMap.put(symbol, result);
                        assert previous == null;
                        return result;
                    }
                }
                result = new Utf8Constant(symbol);
                strongMap.put(symbol, result);
                return result;
            } else {
                WeakReference<Utf8Constant> weakRef = weakMap.get(symbol);
                if (weakRef != null) {
                    // Already a weak reference.
                    result = weakRef.get();
                    // The weak reference may have been collected.
                    if (result != null) {
                        return result;
                    }
                }
                result = new Utf8Constant(symbol);
                weakMap.put(symbol, new WeakReference<>(result));
                return result;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
