/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.reflectionagent;

import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.jvmtiagentbase.JNIHandleSet;
import com.oracle.svm.jvmtiagentbase.Support;

import static com.oracle.svm.core.jni.JNIObjectHandles.nullHandle;

public class NativeImageReflectionAgentJNIHandleSet extends JNIHandleSet {

    final JNIObjectHandle classLoader;
    final JNIObjectHandle jdkInternalReflectDelegatingClassLoader;

    final JNIObjectHandle systemClassLoader;
    final JNIObjectHandle platformClassLoader;
    final JNIObjectHandle builtinAppClassLoader;

    @SuppressWarnings("this-escape")
    public NativeImageReflectionAgentJNIHandleSet(JNIEnvironment env) {
        super(env);
        classLoader = newClassGlobalRef(env, "java/lang/ClassLoader");

        JNIObjectHandle reflectLoader = findClassOptional(env, "jdk/internal/reflect/DelegatingClassLoader");
        jdkInternalReflectDelegatingClassLoader = reflectLoader.equal(nullHandle()) ? nullHandle() : newTrackedGlobalRef(env, reflectLoader);

        JNIMethodId getSystemClassLoader = getMethodId(env, classLoader, "getSystemClassLoader", "()Ljava/lang/ClassLoader;", true);
        systemClassLoader = newTrackedGlobalRef(env, Support.callObjectMethod(env, classLoader, getSystemClassLoader));

        JNIMethodId getPlatformClassLoader = getMethodIdOptional(env, classLoader, "getPlatformClassLoader", "()Ljava/lang/ClassLoader;", true);
        platformClassLoader = getPlatformClassLoader.equal(nullHandle()) ? nullHandle() : newTrackedGlobalRef(env, Support.callObjectMethod(env, classLoader, getPlatformClassLoader));

        JNIMethodId getBuiltinAppClassLoader = getMethodIdOptional(env, classLoader, "getBuiltinAppClassLoader", "()Ljava/lang/ClassLoader;", true);
        builtinAppClassLoader = getBuiltinAppClassLoader.equal(nullHandle()) ? nullHandle() : newTrackedGlobalRef(env, Support.callObjectMethod(env, classLoader, getBuiltinAppClassLoader));
    }
}
