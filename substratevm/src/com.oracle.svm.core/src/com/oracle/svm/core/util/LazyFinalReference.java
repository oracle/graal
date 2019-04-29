/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.util;

import java.util.function.Supplier;

import org.graalvm.compiler.serviceprovider.GraalUnsafeAccess;

// Checkstyle: stop
import sun.misc.Unsafe;
// Checkstyle: resume

/**
 * An object reference that is set lazily to the reference returned by the provided {@link Supplier}
 * in a thread-safe manner: {@link Supplier#get()} might be called more than once from different
 * threads, but {@link #get()} will always return the same reference.
 */
public final class LazyFinalReference<T> {
    private static final Object UNINITIALIZED = new Object();

    private static final Unsafe UNSAFE = GraalUnsafeAccess.getUnsafe();

    private static final long VALUE_OFFSET;
    static {
        try {
            VALUE_OFFSET = UNSAFE.objectFieldOffset(LazyFinalReference.class.getDeclaredField("value"));
        } catch (Throwable ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private final Supplier<T> supplier;

    /**
     * Not required to be volatile because the value will be eventually consistent and inconsistency
     * is primitive to handle.
     */
    @SuppressWarnings("unchecked") private T value = (T) UNINITIALIZED;

    public LazyFinalReference(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    @SuppressWarnings("unchecked")
    public T get() {
        T v = value;
        if (v == UNINITIALIZED) {
            // Try volatile read first in case of memory inconsistency to avoid Supplier call
            v = (T) UNSAFE.getObjectVolatile(this, VALUE_OFFSET);
            if (v == UNINITIALIZED) {
                T obj = supplier.get();

                if (UNSAFE.compareAndSwapObject(this, VALUE_OFFSET, UNINITIALIZED, obj)) {
                    v = obj;
                } else {
                    v = (T) UNSAFE.getObjectVolatile(this, VALUE_OFFSET);
                }
                assert v != UNINITIALIZED;
            }
        }
        return v;
    }
}
