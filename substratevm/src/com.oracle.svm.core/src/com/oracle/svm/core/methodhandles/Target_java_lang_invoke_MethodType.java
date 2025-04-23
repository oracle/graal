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
package com.oracle.svm.core.methodhandles;

import java.lang.invoke.MethodType;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;

@TargetClass(java.lang.invoke.MethodType.class)
final class Target_java_lang_invoke_MethodType {

    /**
     * This field is lazily initialized. We need a stable value, otherwise the initialization can
     * happen just during image heap writing.
     */
    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    private Target_java_lang_invoke_Invokers invokers;

    /**
     * This field is used as a cache, so the value can be re-computed at run time when needed.
     */
    @Alias @RecomputeFieldValue(kind = Kind.Reset) //
    private String methodDescriptor;
}

@TargetClass(className = "java.lang.invoke.Invokers")
final class Target_java_lang_invoke_Invokers {

    /**
     * Substitute to remove the {@code @DontInline} from the original method.
     */
    @Substitute
    static void maybeCustomize(Target_java_lang_invoke_MethodHandle mh) {
        /*
         * MethodHandle.maybeCustomized() is _currently_ substituted by an empty method. We still
         * call it here and represent the original behavior to make it future-proof.
         */
        mh.maybeCustomize();
    }
}

@TargetClass(className = "java.lang.invoke.InvokerBytecodeGenerator")
final class Target_java_lang_invoke_InvokerBytecodeGenerator {
    @SuppressWarnings("unused")
    @Substitute
    static Target_java_lang_invoke_MemberName generateLambdaFormInterpreterEntryPoint(MethodType mt) {
        return null; /* Prevent runtime compilation of invokers */
    }
}
