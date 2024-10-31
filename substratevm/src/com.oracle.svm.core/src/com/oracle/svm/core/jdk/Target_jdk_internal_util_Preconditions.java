/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;
import java.util.function.BiFunction;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.annotate.AnnotateOriginal;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(jdk.internal.util.Preconditions.class)
final class Target_jdk_internal_util_Preconditions {
    @AnnotateOriginal
    @AlwaysInline("fast path performance")
    private static native int checkIndex(int index, int length, BiFunction<String, List<Number>, RuntimeException> oobef);

    @AnnotateOriginal
    @AlwaysInline("fast path performance")
    private static native long checkIndex(long index, long length, BiFunction<String, List<Number>, RuntimeException> oobef);

    @AnnotateOriginal
    @NeverInline("slow path code size")
    private static native RuntimeException outOfBoundsCheckIndex(BiFunction<String, List<Number>, ? extends RuntimeException> oobe, int index, int length);

    @AnnotateOriginal
    @NeverInline("slow path code size")
    private static native RuntimeException outOfBoundsCheckIndex(BiFunction<String, List<Number>, ? extends RuntimeException> oobe, long index, long length);
}
