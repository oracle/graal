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
import static com.oracle.svm.jvmtiagentbase.Support.checkPhase;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.jvmtiagentbase.JvmtiAgentBase;
import com.oracle.svm.jvmtiagentbase.Support;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiError;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiFrameInfo;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiLineNumberEntry;

public class JavaStackTraceCreator {
    private static final int LINE_NUMBER_UNAVAILABLE = -1;

    private final JvmtiEnv jvmti;
    private final JNIEnvironment jni;

    public JavaStackTraceCreator(JvmtiEnv jvmti, JNIEnvironment jni) {
        this.jvmti = jvmti;
        this.jni = jni;
    }

    protected void inspectStackTraceElementMethod(@SuppressWarnings("unused") JNIMethodId jMethodID) {
    }

    private int getCurrentThreadStackFrameCount() {
        CIntPointer countPointer = StackValue.get(CIntPointer.class);
        checkPhase(jvmti.getFunctions().GetFrameCount().invoke(jvmti, nullHandle(), countPointer));
        return countPointer.read();
    }

    private String getSourceFileName(JNIObjectHandle clazz) {
        CCharPointerPointer sourceFileNamePointer = StackValue.get(CCharPointerPointer.class);
        JvmtiError errorCode = jvmti.getFunctions().GetSourceFileName().invoke(jvmti, clazz, sourceFileNamePointer);
        if (errorCode == JvmtiError.JVMTI_ERROR_NONE) {
            String sourceFileName = Support.fromCString(sourceFileNamePointer.read());
            jvmti.getFunctions().Deallocate().invoke(jvmti, sourceFileNamePointer.read());
            return sourceFileName;
        } else {
            return null;
        }
    }

    private static int getFrameSourceLineNumber(JvmtiEnv jvmti, JvmtiFrameInfo frameInfo) {
        CIntPointer entryCountPointer = StackValue.get(CIntPointer.class);
        WordPointer lineEntryTablePointer = StackValue.get(WordPointer.class);
        JvmtiError errorCode = jvmti.getFunctions().GetLineNumberTable().invoke(jvmti, frameInfo.getMethod(), entryCountPointer, lineEntryTablePointer);
        if (errorCode == JvmtiError.JVMTI_ERROR_MUST_POSSESS_CAPABILITY || errorCode == JvmtiError.JVMTI_ERROR_ABSENT_INFORMATION) {
            return LINE_NUMBER_UNAVAILABLE;
        }
        checkPhase(errorCode);

        int entryCount = entryCountPointer.read();
        Pointer lineEntryTable = lineEntryTablePointer.read();
        VMError.guarantee(lineEntryTable.isNonNull());
        int previousLineNumber = LINE_NUMBER_UNAVAILABLE;
        for (int i = 0; i < entryCount; ++i) {
            JvmtiLineNumberEntry entry = (JvmtiLineNumberEntry) lineEntryTable.add(i * SizeOf.get(JvmtiLineNumberEntry.class));
            if (entry.getStartLocation() > frameInfo.getLocation()) {
                break;
            }
            previousLineNumber = entry.getLineNumber();
        }

        jvmti.getFunctions().Deallocate().invoke(jvmti, lineEntryTable);
        return previousLineNumber;
    }

    private StackTraceElement constructStackTraceElement(JvmtiFrameInfo frameInfo) {
        JNIObjectHandle declaringClass = Support.getMethodDeclaringClass(frameInfo.getMethod());

        String methodName = Support.getMethodNameOr(frameInfo.getMethod(), "");
        String declaringClassName = Support.getClassNameOr(jni, declaringClass, "", "");

        CCharPointer isNativePtr = StackValue.get(CCharPointer.class);

        String fileName = null;
        int lineNumber = LINE_NUMBER_UNAVAILABLE;
        JvmtiError errorCode = jvmti.getFunctions().IsMethodNative().invoke(jvmti, frameInfo.getMethod(), isNativePtr);
        if (errorCode == JvmtiError.JVMTI_ERROR_NONE && isNativePtr.read() == 0) {
            fileName = getSourceFileName(declaringClass);
            lineNumber = getFrameSourceLineNumber(jvmti, frameInfo);
        }
        return new StackTraceElement(declaringClassName, methodName, fileName, lineNumber);
    }

    public JNIObjectHandle getStackTraceArray() {
        int threadStackFrameCount = getCurrentThreadStackFrameCount();
        int frameInfoSize = SizeOf.get(JvmtiFrameInfo.class);
        Pointer stackFramesPtr = UnmanagedMemory.malloc(frameInfoSize * threadStackFrameCount);
        CIntPointer readStackFramesPtr = StackValue.get(CIntPointer.class);
        try {
            checkPhase(jvmti.getFunctions().GetStackTrace().invoke(jvmti, nullHandle(), 0, threadStackFrameCount, (WordPointer) stackFramesPtr, readStackFramesPtr));
            VMError.guarantee(readStackFramesPtr.read() == threadStackFrameCount);

            NativeImageDiagnosticsAgent agent = JvmtiAgentBase.singleton();
            JNIObjectHandle stackTraceArray = jni.getFunctions().getNewObjectArray().invoke(jni, threadStackFrameCount, agent.handles().javaLangStackTraceElement, nullHandle());
            for (int i = 0; i < threadStackFrameCount; ++i) {
                JvmtiFrameInfo frameInfo = (JvmtiFrameInfo) stackFramesPtr.add(i * frameInfoSize);
                StackTraceElement stackTraceElement = constructStackTraceElement(frameInfo);

                JNIObjectHandle classNameHandle = Support.toJniString(jni, stackTraceElement.getClassName());
                JNIObjectHandle methodNameHandle = Support.toJniString(jni, stackTraceElement.getMethodName());
                JNIObjectHandle sourceFileNameHandle = Support.toJniString(jni, stackTraceElement.getFileName());
                int lineNumber = stackTraceElement.getLineNumber();

                JNIObjectHandle stackTraceElementHandle = Support.newObjectLLLJ(jni, agent.handles().javaLangStackTraceElement, agent.handles().javaLangStackTraceElementCtor4, classNameHandle,
                                methodNameHandle,
                                sourceFileNameHandle, lineNumber);
                jni.getFunctions().getSetObjectArrayElement().invoke(jni, stackTraceArray, i, stackTraceElementHandle);

                inspectStackTraceElementMethod(frameInfo.getMethod());
            }

            return stackTraceArray;
        } finally {
            UnmanagedMemory.free(stackFramesPtr);
        }
    }
}
