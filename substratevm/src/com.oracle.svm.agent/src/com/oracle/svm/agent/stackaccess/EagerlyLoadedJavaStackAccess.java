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
package com.oracle.svm.agent.stackaccess;

import static com.oracle.svm.core.jni.JNIObjectHandles.nullHandle;
import static com.oracle.svm.jvmtiagentbase.Support.check;

import java.util.function.Supplier;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.UnmanagedMemory;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.jvmtiagentbase.Support;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiEnv;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiError;
import com.oracle.svm.jvmtiagentbase.jvmti.JvmtiFrameInfo;

/**
 * Utility class for walking the stack of the current thread using JVMTI.
 */
public final class EagerlyLoadedJavaStackAccess extends InterceptedState {

    private static final int STACK_FRAMES_PER_ITERATION = 256;

    private final JNIMethodId[] fullJavaStackTrace;

    private EagerlyLoadedJavaStackAccess() {
        fullJavaStackTrace = EagerlyLoadedJavaStackAccess.getCurrentThreadStackTrace();
    }

    private static int getCurrentThreadStackFrameCount(JvmtiEnv jvmti) {
        CIntPointer countPointer = StackValue.get(CIntPointer.class);
        JvmtiError error = jvmti.getFunctions().GetFrameCount().invoke(jvmti, nullHandle(), countPointer);
        if (error.equals(JvmtiError.JVMTI_ERROR_WRONG_PHASE)) {
            return -1;
        }
        check(error);
        return countPointer.read();
    }

    /**
     * Returns the stack trace of the current thread.
     *
     * The returned array contains the current thread's stack, with the first element representing
     * the top of the stack. Elements in the array correspond to the raw value of JNIMethodId of the
     * particular method on the stack.
     *
     */
    private static JNIMethodId[] getCurrentThreadStackTrace() {
        JvmtiEnv jvmti = Support.jvmtiEnv();
        int threadStackFrameCount = getCurrentThreadStackFrameCount(jvmti);
        if (threadStackFrameCount < 0) {
            return new JNIMethodId[0];
        }
        /*
         * If we are unlucky, we may have a phase change while the stack trace entries are being
         * initialized.
         */
        boolean wrongPhase = false;
        int iterationCount = threadStackFrameCount / STACK_FRAMES_PER_ITERATION;
        if (threadStackFrameCount % STACK_FRAMES_PER_ITERATION > 0) {
            iterationCount = iterationCount + 1;
        }

        JNIMethodId[] stackTraceJMethodIDs = new JNIMethodId[threadStackFrameCount];

        int frameInfoSize = SizeOf.get(JvmtiFrameInfo.class);
        Pointer stackFramesBuffer = UnmanagedMemory.malloc(frameInfoSize * STACK_FRAMES_PER_ITERATION);
        CIntPointer readFrameCount = StackValue.get(CIntPointer.class);

        /* Build the stack trace incrementally in chunks of STACK_FRAMES_PER_ITERATION frames. */
        for (int i = 0; i < iterationCount; ++i) {
            JvmtiError error = jvmti.getFunctions().GetStackTrace().invoke(jvmti, nullHandle(), i * STACK_FRAMES_PER_ITERATION, STACK_FRAMES_PER_ITERATION, (WordPointer) stackFramesBuffer,
                            readFrameCount);
            if (error.equals(JvmtiError.JVMTI_ERROR_WRONG_PHASE)) {
                wrongPhase = true;
                break;
            }
            check(error);

            for (int j = 0; j < readFrameCount.read(); ++j) {
                JvmtiFrameInfo frameInfo = (JvmtiFrameInfo) stackFramesBuffer.add(j * frameInfoSize);
                stackTraceJMethodIDs[i * STACK_FRAMES_PER_ITERATION + j] = frameInfo.getMethod();
            }
        }

        UnmanagedMemory.free(stackFramesBuffer);
        return wrongPhase ? null : stackTraceJMethodIDs;
    }

    @Override
    public JNIMethodId getCallerMethod(int depth) {
        if (fullJavaStackTrace == null) {
            return nullHandle();
        }
        assert depth >= 0;
        if (depth >= fullJavaStackTrace.length) {
            return WordFactory.nullPointer();
        }
        return fullJavaStackTrace[depth];
    }

    @Override
    public JNIMethodId[] getFullStackTraceOrNull() {
        return fullJavaStackTrace;
    }

    public static Supplier<InterceptedState> stackAccessSupplier() {
        return EagerlyLoadedJavaStackAccess::new;
    }
}
