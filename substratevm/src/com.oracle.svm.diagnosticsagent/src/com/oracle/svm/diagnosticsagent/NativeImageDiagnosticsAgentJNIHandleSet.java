/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.diagnosticsagent;

import static com.oracle.svm.core.jni.JNIObjectHandles.nullHandle;

import org.graalvm.word.WordFactory;

import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.jvmtiagentbase.JNIHandleSet;

public class NativeImageDiagnosticsAgentJNIHandleSet extends JNIHandleSet {

    private JNIObjectHandle classInitializationTracking = nullHandle();
    private JNIMethodId reportClassInitialized = WordFactory.nullPointer();
    private JNIMethodId reportObjectInstantiated = WordFactory.nullPointer();

    final JNIObjectHandle javaLangStackTraceElement;
    final JNIMethodId javaLangStackTraceElementCtor4;

    public NativeImageDiagnosticsAgentJNIHandleSet(JNIEnvironment env) {
        super(env);
        javaLangStackTraceElement = newClassGlobalRef(env, "java/lang/StackTraceElement");
        javaLangStackTraceElementCtor4 = getMethodId(env, javaLangStackTraceElement, "<init>", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)V", false);
    }

    public void initializeTrackingSupportHandles(JNIEnvironment env) {
        assert classInitializationTracking.equal(nullHandle()) && reportClassInitialized.isNull() && reportObjectInstantiated.isNull() : "Attempt to reinitialize tracking support handles.";
        classInitializationTracking = newClassGlobalRef(env, "org/graalvm/nativeimage/impl/clinit/ClassInitializationTracking");
        reportClassInitialized = getMethodId(env, classInitializationTracking, "reportClassInitialized", "(Ljava/lang/Class;[Ljava/lang/StackTraceElement;)V", true);
        reportObjectInstantiated = getMethodId(env, classInitializationTracking, "reportObjectInstantiated", "(Ljava/lang/Object;[Ljava/lang/StackTraceElement;)V", true);
    }

    public JNIObjectHandle getClassInitializationTrackingClassHandle() {
        assert classInitializationTracking.notEqual(nullHandle()) : "Attempt to access uninitialized class handle.";
        return classInitializationTracking;
    }

    public JNIMethodId getReportClassInitializedMethodId() {
        assert reportClassInitialized.isNonNull() : "Attempt to access an uninitialized method handle.";
        return reportClassInitialized;
    }

    public JNIMethodId getReportObjectInstantiatedMethodId() {
        assert reportObjectInstantiated.isNonNull() : "Attempt to access an uninitialized method handle.";
        return reportObjectInstantiated;
    }
}
