/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2021, Alibaba Group Holding Limited. All rights reserved.
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
package com.oracle.svm.agent;

import com.oracle.svm.core.util.JavaClassUtil;
import com.oracle.svm.jni.nativeapi.JNIEnvironment;

import java.security.NoSuchAlgorithmException;

import static com.oracle.svm.jvmtiagentbase.Support.getMethodFullNameAtFrame;

/**
 * Support dynamic class loading that is implemented by java.lang.ClassLoader.defineClass.
 */
public class ClassLoaderDefineClassSupport {

    private static String calculateGeneratedClassSHA(byte[] values) {
        String generatedClassHashCode;
        try {
            generatedClassHashCode = JavaClassUtil.getSHAWithoutSourceFileInfo(values);
        } catch (NoSuchAlgorithmException e) {
            generatedClassHashCode = null;
        }
        return generatedClassHashCode;
    }

    public static void trace(TraceWriter traceWriter, byte[] classContents, String generatedClassName, Object result) {
        assert classContents != null;
        if (generatedClassName != null && result != null) {
            // Trace dynamically generated class in config file
            traceWriter.traceCall("classDefiner", "onClassFileLoadHook", null, null, null, result, generatedClassName.replace('/', '.'), calculateGeneratedClassSHA(classContents), classContents);
        }
    }

    public static StringBuilder getStackTrace(JNIEnvironment jni) {
        StringBuilder trace = new StringBuilder();
        int i = 0;
        int maxDepth = 20;
        while (i < maxDepth) {
            String methodName = getMethodFullNameAtFrame(jni, i++);
            if (methodName == null) {
                break;
            }
            trace.append("    ").append(methodName).append("\n");
        }
        if (i >= maxDepth) {
            trace.append("    ").append("...").append("\n");
        }
        return trace;
    }
}
