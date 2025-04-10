/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.metrickeys;

import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.MetricKey;

/**
 * A utility class that represents the namespace for {@link MetricKey}s that are used for the Boot
 * Image Heap metric tracking through the
 * {@linkplain com.oracle.svm.hosted.webimage.logging.LoggerScope Logging API}. The metrics tracked
 * are the number of specific constant types that can be found in the image heap and the size those
 * constant types would occupy in the JVM.
 */
public class BootHeapMetricKeys {
    /**
     * Represents the total number of constants that are of an object type.
     */
    public static final CounterKey NUM_OBJECTS = DebugContext.counter("boot-heap-number-of-objects");

    /**
     * Represents the total number of constants that are of an array type.
     */
    public static final CounterKey NUM_ARRAYS = DebugContext.counter("boot-heap-number-of-arrays");

    /**
     * Represents the total number of constants that are of a primitive type.
     */
    public static final CounterKey NUM_PRIMITIVES = DebugContext.counter("boot-heap-number-of-primitives");

    /**
     * Represents the total number of constants that are strings.
     */
    public static final CounterKey NUM_STRINGS = DebugContext.counter("boot-heap-number-of-strings");

    /**
     * Represents the total number of constants that are method pointers.
     */
    public static final CounterKey NUM_METHOD_POINTERS = DebugContext.counter("boot-heap-number-of-method-pointers");

    /**
     * Represents the total size that constant objects would occupy in the JVM.
     */
    public static final CounterKey JVM_OBJECTS_SIZE = DebugContext.counter("boot-heap-size-of-objects");

    /**
     * Represents the total size that constant arrays would occupy in the JVM.
     */
    public static final CounterKey JVM_ARRAYS_SIZE = DebugContext.counter("boot-heap-size-of-arrays");

    /**
     * Represents the total size that constant primitives would occupy in the JVM.
     */
    public static final CounterKey JVM_PRIMITIVES_SIZE = DebugContext.counter("boot-heap-size-of-primitives");

    /**
     * Represents the total size that constant strings would occupy in the JVM.
     */
    public static final CounterKey JVM_STRINGS_SIZE = DebugContext.counter("boot-heap-size-of-strings");

    /**
     * Represents the total size that constant method pointers would occupy in the JVM.
     */
    public static final CounterKey JVM_METHOD_POINTERS_SIZE = DebugContext.counter("boot-heap-size-of-method-pointers");

    /**
     * Represents the total size that all constant types would occupy in the JVM.
     */
    public static final CounterKey JVM_CONSTANTS_SIZE = DebugContext.counter("boot-heap-size-of-constants");
}
