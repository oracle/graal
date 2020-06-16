/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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

@TargetClass(java.lang.invoke.MethodType.class)
final class Target_java_lang_invoke_MethodType {

    /**
     * This map contains MethodType instances that refer to classes of the image generator. Starting
     * with a new empty set at run time avoids bringing over unnecessary cache entries.
     *
     * Since MethodHandle is not supported yet at run time, we could also disable the usage of
     * MethodType completely. But this recomputation seems less intrusive.
     */
    @Alias @RecomputeFieldValue(kind = Kind.NewInstance, declClassName = "java.lang.invoke.MethodType$ConcurrentWeakInternSet") //
    private static Target_java_lang_invoke_MethodType_ConcurrentWeakInternSet internTable;

    /**
     * This field is lazily initialized. We need a stable value, otherwise the initialization can
     * happen just during image heap writing.
     */
    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    private Target_java_lang_invoke_Invokers invokers;
}

@TargetClass(value = java.lang.invoke.MethodType.class, innerClass = "ConcurrentWeakInternSet")
final class Target_java_lang_invoke_MethodType_ConcurrentWeakInternSet {
}

@TargetClass(className = "java.lang.invoke.Invokers")
final class Target_java_lang_invoke_Invokers {
}
