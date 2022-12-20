/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.util;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AtomicUtils {

    public static <T, V> boolean atomicSet(T holder, V value, AtomicReferenceFieldUpdater<T, V> updater) {
        Objects.requireNonNull(value, "The value parameter of AtomicUtils.atomicSet() should not be null.");
        if (updater.get(holder) == null) {
            return updater.compareAndSet(holder, null, value);
        }
        return false;
    }

    public static <T, V> boolean atomicSetAndRun(T holder, V value, AtomicReferenceFieldUpdater<T, V> updater, Runnable task) {
        Objects.requireNonNull(value, "The value parameter of AtomicUtils.atomicSetAndRun() should not be null.");
        if (updater.get(holder) == null) {
            boolean firstAttempt = updater.compareAndSet(holder, null, value);
            if (firstAttempt) {
                task.run();
            }
            return firstAttempt;
        }
        return false;
    }

    public static <T, V> boolean isSet(T holder, AtomicReferenceFieldUpdater<T, V> updater) {
        return updater.get(holder) != null;
    }

    /**
     * Atomically set the field to 1 if the current value is 0.
     *
     * Performing a read before the compare and set is more cache friendly.
     * 
     * @return {@code true} if successful.
     */
    public static <T> boolean atomicMark(T holder, AtomicIntegerFieldUpdater<T> updater) {
        if (updater.get(holder) == 0) {
            return updater.compareAndSet(holder, 0, 1);
        }
        return false;
    }

    /**
     * Return true if the field is set to 1, false otherwise.
     */
    public static <T> boolean isSet(T holder, AtomicIntegerFieldUpdater<T> updater) {
        return updater.get(holder) == 1;
    }

    /**
     * Utility to lazily produce and initialize an object stored in an {@link AtomicReference}. The
     * {@code supplier} may be invoked multiple times, but the {@code initializer} is guaranteed to
     * only be invoked for the winning value. Note that other threads can see the state of the
     * object returned by the {@code supplier} before the {@code initializer} has finished or even
     * started to be executed.
     */
    public static <T> T produceAndSetValue(AtomicReference<T> reference, Supplier<T> supplier, Consumer<T> initializer) {
        if (reference.get() == null) {
            T value = supplier.get();
            if (reference.compareAndSet(null, value)) {
                /* Only the winning object is initialized. */
                initializer.accept(value);
            }
        }
        return reference.get();
    }

}
