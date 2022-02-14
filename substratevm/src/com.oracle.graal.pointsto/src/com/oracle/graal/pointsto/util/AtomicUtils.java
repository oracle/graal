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
package com.oracle.graal.pointsto.util;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class AtomicUtils {

    public static boolean atomicMark(AtomicBoolean flag) {
        return flag.compareAndSet(false, true);
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
