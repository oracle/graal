/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.TargetClass;

/**
 * These substitutions are necessary because in JDK 11 the static initializations of these fields
 * are copies of the corresponding (recomputed) fields of {@link jdk.internal.misc.Unsafe}. But
 * copying a recomputed value during image building does not recompute the value. See GR-12640.
 */
@TargetClass(value = sun.misc.Unsafe.class, onlyWith = JDK11OrLater.class)
final class Target_sun_misc_Unsafe_JDK11OrLater {

    /* { Checkstyle: stop */

    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = boolean[].class, isFinal = true) //
    private static int ARRAY_BOOLEAN_BASE_OFFSET;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = byte[].class, isFinal = true) //
    private static int ARRAY_BYTE_BASE_OFFSET;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = short[].class, isFinal = true) //
    private static int ARRAY_SHORT_BASE_OFFSET;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = char[].class, isFinal = true) //
    private static int ARRAY_CHAR_BASE_OFFSET;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = int[].class, isFinal = true) //
    private static int ARRAY_INT_BASE_OFFSET;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = long[].class, isFinal = true) //
    private static int ARRAY_LONG_BASE_OFFSET;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = float[].class, isFinal = true) //
    private static int ARRAY_FLOAT_BASE_OFFSET;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = double[].class, isFinal = true) //
    private static int ARRAY_DOUBLE_BASE_OFFSET;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayBaseOffset, declClass = Object[].class, isFinal = true) //
    private static int ARRAY_OBJECT_BASE_OFFSET;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexScale, declClass = boolean[].class, isFinal = true) //
    private static int ARRAY_BOOLEAN_INDEX_SCALE;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexScale, declClass = byte[].class, isFinal = true) //
    private static int ARRAY_BYTE_INDEX_SCALE;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexScale, declClass = short[].class, isFinal = true) //
    private static int ARRAY_SHORT_INDEX_SCALE;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexScale, declClass = char[].class, isFinal = true) //
    private static int ARRAY_CHAR_INDEX_SCALE;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexScale, declClass = int[].class, isFinal = true) //
    private static int ARRAY_INT_INDEX_SCALE;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexScale, declClass = long[].class, isFinal = true) //
    private static int ARRAY_LONG_INDEX_SCALE;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexScale, declClass = float[].class, isFinal = true) //
    private static int ARRAY_FLOAT_INDEX_SCALE;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexScale, declClass = double[].class, isFinal = true) //
    private static int ARRAY_DOUBLE_INDEX_SCALE;

    @Alias @RecomputeFieldValue(kind = Kind.ArrayIndexScale, declClass = Object[].class, isFinal = true) //
    private static int ARRAY_OBJECT_INDEX_SCALE;

    /* } Checkstyle: resume */

}
