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

import com.oracle.svm.jni.nativeapi.JNIMethodId;
import com.oracle.svm.jvmtiagentbase.Support;
import jdk.vm.ci.meta.MetaUtil;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.word.WordFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static com.oracle.svm.jvmtiagentbase.Support.check;
import static org.graalvm.word.WordFactory.nullPointer;

class ClassInfo {

    final String className;
    final Map<String, MethodInfo> nameAndSignatureToMethodInfoMap;

    ClassInfo(String classSignature) {
        this.className = MetaUtil.internalNameToJava(classSignature, true, false);
        this.nameAndSignatureToMethodInfoMap = new ConcurrentHashMap<>();
    }

    MethodInfo findOrCreateMethodInfo(long rawJMethodIdValue) {
        JNIMethodId jMethodId = WordFactory.pointer(rawJMethodIdValue);

        CCharPointerPointer methodNamePtr = StackValue.get(CCharPointerPointer.class);
        CCharPointerPointer methodSignaturePtr = StackValue.get(CCharPointerPointer.class);

        check(Support.jvmtiFunctions().GetMethodName().invoke(Support.jvmtiEnv(), jMethodId, methodNamePtr, methodSignaturePtr, nullPointer()));
        String methodName = MethodInfoRecordKeeper.getJavaStringAndFreeNativeString(methodNamePtr.read());
        String methodSignature = MethodInfoRecordKeeper.getJavaStringAndFreeNativeString(methodSignaturePtr.read());
        String methodNameAndSignature = combineMethodNameAndSignature(methodName, methodSignature);

        nameAndSignatureToMethodInfoMap.computeIfAbsent(methodNameAndSignature, nameAndSignature -> new MethodInfo(methodName, methodSignature, this));
        return nameAndSignatureToMethodInfoMap.get(methodNameAndSignature);
    }

    private static String combineMethodNameAndSignature(String methodName, String methodSignature) {
        return methodName + "-" + methodSignature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ClassInfo classInfo = (ClassInfo) o;
        return className.equals(classInfo.className);
    }

    @Override
    public int hashCode() {
        return Objects.hash(className);
    }
}
