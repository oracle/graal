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

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.WordBase;

/**
 * Represents a fast thread local variable of type {@link WordBase word}. See
 * {@link FastThreadLocalFactory} for details and restrictions of VM thread local variables.
 */
@SuppressWarnings({"unused"})
public final class FastThreadLocalWord<T extends WordBase> extends FastThreadLocal {

    @Platforms(Platform.HOSTED_ONLY.class)
    FastThreadLocalWord(String name) {
        super(name);
    }

    public T get() {
        throw new IllegalArgumentException("Value of VM thread local variable cannot be accessed during native image generation");
    }

    public T get(IsolateThread thread) {
        throw new IllegalArgumentException("Value of VM thread local variable cannot be accessed during native image generation");
    }

    public void set(T value) {
        throw new IllegalArgumentException("Value of VM thread local variable cannot be accessed during native image generation");
    }

    public void set(IsolateThread thread, T value) {
        throw new IllegalArgumentException("Value of VM thread local variable cannot be accessed during native image generation");
    }

    public T getVolatile() {
        throw new IllegalArgumentException("Value of VM thread local variable cannot be accessed during native image generation");
    }

    public T getVolatile(IsolateThread thread) {
        throw new IllegalArgumentException("Value of VM thread local variable cannot be accessed during native image generation");
    }

    public void setVolatile(T value) {
        throw new IllegalArgumentException("Value of VM thread local variable cannot be accessed during native image generation");
    }

    public void setVolatile(IsolateThread thread, T value) {
        throw new IllegalArgumentException("Value of VM thread local variable cannot be accessed during native image generation");
    }

    public boolean compareAndSet(T expect, T update) {
        throw new IllegalArgumentException("Value of VM thread local variable cannot be accessed during native image generation");
    }

    public boolean compareAndSet(IsolateThread thread, T expect, T update) {
        throw new IllegalArgumentException("Value of VM thread local variable cannot be accessed during native image generation");
    }

    @SuppressWarnings("static-method")
    public WordPointer getAddress() {
        throw new IllegalArgumentException("VM thread local variable cannot be accessed during native image generation");
    }

    @SuppressWarnings("static-method")
    public WordPointer getAddress(IsolateThread thread) {
        throw new IllegalArgumentException("VM thread local variable cannot be accessed during native image generation");
    }
}
