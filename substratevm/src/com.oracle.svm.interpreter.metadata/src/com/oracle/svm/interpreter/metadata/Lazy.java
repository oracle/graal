/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.metadata;

/**
 * Represents a lazily computed value. Ensures that a single thread runs the computation.
 */
public final class Lazy<T> {

    @FunctionalInterface
    public interface LazySupplier<T> {
        T get();
    }

    private volatile T ref;
    private final LazySupplier<T> supplier;

    private Lazy(LazySupplier<T> supplier) {
        this.supplier = supplier;
    }

    /**
     * If the supplier returns <code>null</code>, {@link NullPointerException} is thrown. Exceptions
     * thrown by the supplier will be propagated. If the supplier returns a non-null object, it will
     * be cached and the computation is considered finished. The supplier is guaranteed to run on a
     * single thread. A successful computation ({@link LazySupplier#get()} returns a non-null
     * object) is guaranteed to be executed only once.
     *
     * @return the computed object, guaranteed to be non-null
     */
    public T get() {
        T localRef = ref;
        if (localRef == null) {
            synchronized (this) {
                localRef = ref;
                if (localRef == null) {
                    localRef = MetadataUtil.requireNonNull(supplier.get());
                    ref = localRef;
                }
            }
        }
        return localRef;
    }

    /**
     * (Not so) Lazy value that does not run a computation.
     */
    public static <T> Lazy<T> value(T nonNullValue) {
        Lazy<T> result = new Lazy<>(null);
        result.ref = MetadataUtil.requireNonNull(nonNullValue);
        return result;
    }

    /**
     * @param supplier if the supplier returns null, {@link #get()} will throw
     *            {@link NullPointerException}
     */
    public static <V> Lazy<V> of(LazySupplier<V> supplier) {
        return new Lazy<>(MetadataUtil.requireNonNull(supplier));
    }
}
