/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.agent.predicatedconfig;

import static com.oracle.svm.jvmtiagentbase.Support.check;
import static org.graalvm.word.WordFactory.nullPointer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jni.nativeapi.JNIObjectHandle;
import com.oracle.svm.jvmtiagentbase.Support;

/**
 * Efficiently keeps a record of Java methods encountered during a stack walk.
 */
public class MethodInfoRecordKeeper {

    /* Should we keep method info records? */
    private final boolean shouldTrackMethodInfo;

    /* Maps the raw value of a JMethodID to its describing MethodInfo class */
    private final Map<Long, MethodInfo> jMethodIdToMethodInfoMap = new ConcurrentHashMap<>();
    /* Maps the class signature to its describing ClassInfo class */
    private final Map<String, ClassInfo> classSignatureToClassInfoMap = new ConcurrentHashMap<>();

    public MethodInfoRecordKeeper(boolean shouldTrackMethodInfo) {
        this.shouldTrackMethodInfo = shouldTrackMethodInfo;
    }

    public MethodInfo[] getStackTraceInfo(JNIMethodId[] stackTrace) {
        MethodInfo[] methodInfoTrace = new MethodInfo[stackTrace.length];
        for (int i = 0; i < stackTrace.length; ++i) {
            methodInfoTrace[i] = getMethodInfo(stackTrace[i].rawValue());
        }
        return methodInfoTrace;
    }

    /**
     * Returns Java method information associated with a given jMethodId.
     *
     * @param rawJMethodIdValue Raw jMethodId value.
     * @return MethodInfo object that uniquely describes the given method.
     */
    private MethodInfo getMethodInfo(long rawJMethodIdValue) {
        assert shouldTrackMethodInfo;
        if (jMethodIdToMethodInfoMap.containsKey(rawJMethodIdValue)) {
            return jMethodIdToMethodInfoMap.get(rawJMethodIdValue);
        }
        return findOrCreateMethodInfo(rawJMethodIdValue);
    }

    /**
     * Finds or creates a new MethodInfo object describing the Java method.
     *
     * Note that multiple jMethodIds may point to the same Java method. This can, for example,
     * happen if a single class is loaded by two different classloaders. In order to correctly
     * process this case, we keep a track of encountered classes and their methods.
     *
     * @param rawJMethodIdValue Raw jMethodId value.
     * @return MethodInfo object that uniquely describes the given method.
     */
    private MethodInfo findOrCreateMethodInfo(long rawJMethodIdValue) {
        String declaringClassSignature = getMethodDeclaringClassSignature(rawJMethodIdValue);
        ClassInfo classInfo = findOrCreateClassInfo(declaringClassSignature);
        MethodInfo methodInfo = classInfo.findOrCreateMethodInfo(rawJMethodIdValue);
        jMethodIdToMethodInfoMap.putIfAbsent(rawJMethodIdValue, methodInfo);
        return methodInfo;
    }

    static String getJavaStringAndFreeNativeString(CCharPointer nativeString) {
        String javaString = Support.fromCString(nativeString);
        Support.jvmtiFunctions().Deallocate().invoke(Support.jvmtiEnv(), nativeString);
        return javaString;
    }

    private static String getMethodDeclaringClassSignature(long rawJMethodIdValue) {
        JNIMethodId jMethodId = WordFactory.pointer(rawJMethodIdValue);

        JNIObjectHandle declaringClass = Support.getMethodDeclaringClass(jMethodId);
        CCharPointerPointer signaturePointer = StackValue.get(CCharPointerPointer.class);
        check(Support.jvmtiFunctions().GetClassSignature().invoke(Support.jvmtiEnv(), declaringClass, signaturePointer, nullPointer()));
        return getJavaStringAndFreeNativeString(signaturePointer.read());
    }

    private ClassInfo findOrCreateClassInfo(String classSignature) {
        classSignatureToClassInfoMap.computeIfAbsent(classSignature, ClassInfo::new);
        return classSignatureToClassInfoMap.get(classSignature);
    }

}
