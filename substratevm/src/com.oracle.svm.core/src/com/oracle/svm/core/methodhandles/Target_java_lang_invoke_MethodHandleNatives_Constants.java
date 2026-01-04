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
package com.oracle.svm.core.methodhandles;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "java.lang.invoke.MethodHandleNatives", innerClass = "Constants")
public final class Target_java_lang_invoke_MethodHandleNatives_Constants {
    // Checkstyle: stop
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static int MN_IS_METHOD;
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static int MN_IS_CONSTRUCTOR;
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static int MN_IS_FIELD;
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static int MN_IS_TYPE;
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static int MN_CALLER_SENSITIVE;
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static int MN_REFERENCE_KIND_SHIFT;

    /*
     * Constant pool reference-kind codes, as used by CONSTANT_MethodHandle CP entries.
     */
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static byte REF_NONE;  // null
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static byte REF_getField;
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static byte REF_getStatic;
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static byte REF_putField;
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static byte REF_putStatic;
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static byte REF_invokeVirtual;
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static byte REF_invokeStatic;
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static byte REF_invokeSpecial;
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static byte REF_newInvokeSpecial;
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static byte REF_invokeInterface;
    @Alias @RecomputeFieldValue(isFinal = true, kind = Kind.None) public static byte REF_LIMIT;
    // Checkstyle: resume
}
