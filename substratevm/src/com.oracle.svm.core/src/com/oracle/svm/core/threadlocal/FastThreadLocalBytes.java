/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.threadlocal;

import java.util.function.IntSupplier;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.PointerBase;

/**
 * Represents a fast thread local memory block that has a user-defined size. See
 * {@link FastThreadLocalFactory} for details and restrictions of VM thread local variables.
 * <p>
 * The returned address is to memory that is guaranteed to not move, i.e., repeated calls of this
 * method for the same thread are guaranteed to return the same result.
 * <p>
 * The size must be a compile-time constant. It is computed after the static analysis, i.e., the
 * provided {@link #getSizeSupplier()} is invoked after static analysis. This allows to depend on
 * static analysis results when providing the size.
 */
@SuppressWarnings({"unused"})
public final class FastThreadLocalBytes<T extends PointerBase> extends FastThreadLocal {

    @Platforms(Platform.HOSTED_ONLY.class)//
    private final IntSupplier sizeSupplier;

    @Platforms(Platform.HOSTED_ONLY.class)
    FastThreadLocalBytes(IntSupplier sizeSupplier, String name) {
        super(name);
        this.sizeSupplier = sizeSupplier;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public IntSupplier getSizeSupplier() {
        return sizeSupplier;
    }

    public T getAddress() {
        throw new IllegalArgumentException("VM thread local variable cannot be accessed during native image generation");
    }

    public T getAddress(IsolateThread thread) {
        throw new IllegalArgumentException("VM thread local variable cannot be accessed during native image generation");
    }
}
