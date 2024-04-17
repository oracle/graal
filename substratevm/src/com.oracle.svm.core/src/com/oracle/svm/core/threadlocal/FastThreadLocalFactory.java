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
import org.graalvm.word.WordBase;

/**
 * This class contains factory methods to create fast thread local variables. A thread local
 * variable is represented as an object, with different classes for primitive {@code int} (class
 * {@link FastThreadLocalInt}), primitive {@code long} (class {@link FastThreadLocalLong}),
 * {@link Object} (class {@link FastThreadLocalObject}), and {@link WordBase word} (class
 * {@link FastThreadLocalWord}) values. Access to such thread local variables is significantly
 * faster than regular Java {@link ThreadLocal} variables. However, there are several restrictions:
 * <ul>
 * <li>The thread local object must be created during native image generation. Otherwise, the size
 * of the {@link IsolateThread} data structure would not be a compile time constant.</li>
 * <li>It is not possible to access the value of a thread local variable during native image
 * generation. Attempts to do so will result in an error during native image generation.</li>
 * <li>The initial value of a thread local variable is 0 or {@code null} for every newly created
 * thread. There is no possibility to specify an initial value.</li>
 * <li>A thread local variable created by this factory must be assigned to one {@code static final}
 * field. The name of that field is used as the name of the local variable. The name is used to sort
 * the variables in the {@link IsolateThread} data structure, to make the layout deterministic and
 * reproducible.</li>
 * <li>When accessing the value, the thread local variable must be a compile time constant. This is
 * fulfilled when the value is accessed from the {@code static final} field, which is the intended
 * use case.</li>
 * <li>Thread locals of other threads may only be accessed at a safepoint. This restriction is
 * necessary as the other thread could otherwise exit at any time, which frees the memory of the
 * thread locals.</li>
 * <li>Thread locals only exist for platform threads. Virtual threads will therefore see the thread
 * locals of their current carrier thread.</li>
 * </ul>
 * <p>
 * The implementation of fast thread local variables and the way the data is stored is
 * implementation specific and transparent for users of it. However, the access is fast and never
 * requires object allocation.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public final class FastThreadLocalFactory {

    private FastThreadLocalFactory() {
    }

    /**
     * Creates a new fast thread local variable of the primitive type {@code int}.
     */
    public static FastThreadLocalInt createInt(String name) {
        return new FastThreadLocalInt(name);
    }

    @Deprecated
    public static FastThreadLocalInt createInt() {
        return new FastThreadLocalInt("FastThreadLocalInt");
    }

    /**
     * Creates a new fast thread local variable of the primitive type {@code long}.
     */
    public static FastThreadLocalLong createLong(String name) {
        return new FastThreadLocalLong(name);
    }

    @Deprecated
    public static FastThreadLocalLong createLong() {
        return new FastThreadLocalLong("FastThreadLocalLong");
    }

    /**
     * Creates a new fast thread local variable of type {@link WordBase word}.
     */
    public static <T extends WordBase> FastThreadLocalWord<T> createWord(String name) {
        return new FastThreadLocalWord<>(name);
    }

    @Deprecated
    public static <T extends WordBase> FastThreadLocalWord<T> createWord() {
        return new FastThreadLocalWord<>("FastThreadLocalWord");
    }

    /**
     * Creates a new fast thread local variable of type {@link Object}.
     */
    public static <T> FastThreadLocalObject<T> createObject(Class<T> valueClass, String name) {
        return new FastThreadLocalObject<>(valueClass, name);
    }

    @Deprecated
    public static <T> FastThreadLocalObject<T> createObject(Class<T> valueClass) {
        return new FastThreadLocalObject<>(valueClass, "FastThreadLocalObject");
    }

    /**
     * Creates a new fast thread local memory block that has a user-defined size.
     */
    public static <T extends PointerBase> FastThreadLocalBytes<T> createBytes(IntSupplier sizeSupplier, String name) {
        return new FastThreadLocalBytes<>(sizeSupplier, name);
    }

    @Deprecated
    public static <T extends PointerBase> FastThreadLocalBytes<T> createBytes(IntSupplier sizeSupplier) {
        return new FastThreadLocalBytes<>(sizeSupplier, "FastThreadLocalBytes");
    }
}
