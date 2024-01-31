/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.heap.value;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Interface for accessing hosted heap values. It provides mechanisms for accessing hosted heap
 * values either eagerly or lazily.
 * <p/>
 * Eager heap values are objects whose state doesn't change during heap snapshotting. Their value is
 * immediately available.
 * <p/>
 * Lazy heap values are objects whose state can change during heap snapshotting, therefore reading
 * the actual value is delayed. Instead, both a value supplier and an availability supplier are
 * installed. The implementation guarantees that the value is indeed available, by checking the
 * availability supplier, before it attempts to retrieve it.
 */
public interface ValueSupplier<V> {

    static <V> ValueSupplier<V> eagerValue(V value) {
        return new EagerValueSupplier<>(Objects.requireNonNull(value));
    }

    static <V> ValueSupplier<V> lazyValue(Supplier<V> valueSupplier, BooleanSupplier isAvailable) {
        return new LazyValueSupplier<>(valueSupplier, isAvailable);
    }

    /** Checks if the value is available. */
    boolean isAvailable();

    /**
     * Retrieves the value, if available. Attempting to access a value before it is available
     * results in error.
     */
    V get();
}
