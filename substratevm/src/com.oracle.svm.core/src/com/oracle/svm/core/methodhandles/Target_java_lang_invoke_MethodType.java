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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;

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
    static Target_java_lang_invoke_MethodType_ConcurrentWeakInternSet internTable;

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

@TargetClass(value = java.lang.invoke.MethodType.class, innerClass = "ConcurrentWeakInternSet")
final class Target_java_lang_invoke_MethodType_ConcurrentWeakInternSet {
}

/**
 * The substitutions are needed to replace identity comparison ({@code ==}) with
 * {@code MethodType.equal} calls. We do not keep
 * {@link Target_java_lang_invoke_MethodType#internTable}, so we cannot guarantee identity.
 */
@TargetClass(className = "java.lang.invoke.Invokers")
final class Target_java_lang_invoke_Invokers {
    @Substitute
    static void checkExactType(MethodHandle mh, MethodType expected) {
        if (!expected.equals(mh.type())) {
            throw new WrongMethodTypeException("expected " + expected + " but found " + mh.type());
        }
    }

    @Substitute
    static MethodHandle checkVarHandleGenericType(Target_java_lang_invoke_VarHandle handle, Target_java_lang_invoke_VarHandle_AccessDescriptor ad) {
        // Test for exact match on invoker types
        // TODO match with erased types and add cast of return value to lambda form
        MethodHandle mh = handle.getMethodHandle(ad.mode);
        if (mh.type().equals(ad.symbolicMethodTypeInvoker)) {
            return mh;
        } else {
            return mh.asType(ad.symbolicMethodTypeInvoker);
        }
    }

    @Substitute
    static MethodHandle checkVarHandleExactType(Target_java_lang_invoke_VarHandle handle, Target_java_lang_invoke_VarHandle_AccessDescriptor ad) {
        MethodHandle mh = handle.getMethodHandle(ad.mode);
        MethodType mt = mh.type();
        if (!mt.equals(ad.symbolicMethodTypeInvoker)) {
            throw newWrongMethodTypeException(mt, ad.symbolicMethodTypeInvoker);
        }
        return mh;
    }

    @Alias
    static native WrongMethodTypeException newWrongMethodTypeException(MethodType actual, MethodType expected);
}

@TargetClass(className = "java.lang.invoke.InvokerBytecodeGenerator")
final class Target_java_lang_invoke_InvokerBytecodeGenerator {
    @SuppressWarnings("unused")
    @Substitute
    static Target_java_lang_invoke_MemberName generateLambdaFormInterpreterEntryPoint(MethodType mt) {
        return null; /* Prevent runtime compilation of invokers */
    }
}
