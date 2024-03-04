/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmti;

import static com.oracle.svm.core.jvmti.headers.JvmtiThreadState.JVMTI_THREAD_STATE_ALIVE;
import static com.oracle.svm.core.jvmti.headers.JvmtiThreadState.JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER;
import static com.oracle.svm.core.jvmti.headers.JvmtiThreadState.JVMTI_THREAD_STATE_INTERRUPTED;
import static com.oracle.svm.core.jvmti.headers.JvmtiThreadState.JVMTI_THREAD_STATE_IN_NATIVE;
import static com.oracle.svm.core.jvmti.headers.JvmtiThreadState.JVMTI_THREAD_STATE_IN_OBJECT_WAIT;
import static com.oracle.svm.core.jvmti.headers.JvmtiThreadState.JVMTI_THREAD_STATE_PARKED;
import static com.oracle.svm.core.jvmti.headers.JvmtiThreadState.JVMTI_THREAD_STATE_RUNNABLE;
import static com.oracle.svm.core.jvmti.headers.JvmtiThreadState.JVMTI_THREAD_STATE_SLEEPING;
import static com.oracle.svm.core.jvmti.headers.JvmtiThreadState.JVMTI_THREAD_STATE_TERMINATED;
import static com.oracle.svm.core.jvmti.headers.JvmtiThreadState.JVMTI_THREAD_STATE_WAITING;
import static com.oracle.svm.core.jvmti.headers.JvmtiThreadState.JVMTI_THREAD_STATE_WAITING_INDEFINITELY;
import static com.oracle.svm.core.jvmti.headers.JvmtiThreadState.JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jvmti.headers.JThread;
import com.oracle.svm.core.jvmti.headers.JvmtiError;
import com.oracle.svm.core.monitor.MonitorSupport;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.ThreadStatus;
import com.oracle.svm.core.thread.VMThreads;

public final class JvmtiThreadStateUtil {
    private final JvmtiGetThreadStateOperation stateOperation;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JvmtiThreadStateUtil() {
        this.stateOperation = new JvmtiGetThreadStateOperation();
    }

    public static int getThreadState(JThread jthread, CIntPointer statePtr) {
        return ImageSingletons.lookup(JvmtiThreadStateUtil.class).getThreadStateInternal(jthread, statePtr);
    }

    private int getThreadStateInternal(JThread jthread, CIntPointer statePtr) {
        int size = SizeOf.get(JvmtiGetThreadStateOperationData.class);
        JvmtiGetThreadStateOperationData data = StackValue.get(size);
        UnmanagedMemoryUtil.fill((Pointer) data, WordFactory.unsigned(size), (byte) 0);

        data.setJThreadPointer(jthread);
        data.setThreadStatePointer(statePtr);
        stateOperation.enqueue(data);
        return data.getJvmtiError();
    }

    @RawStructure
    private interface JvmtiGetThreadStateOperationData extends NativeVMOperationData {
        @RawField
        void setThreadStatePointer(CIntPointer ptr);

        @RawField
        CIntPointer getThreadStatePointer();

        @RawField
        int getJvmtiError();

        @RawField
        void setJvmtiError(int error);

        @RawField
        void setJThreadPointer(JThread ptr);

        @RawField
        JThread getJThreadPointer();
    }

    // TODO @dprcci cannot allocate memory
    private static class JvmtiGetThreadStateOperation extends NativeVMOperation {
        JvmtiGetThreadStateOperation() {
            super(VMOperationInfos.get(JvmtiGetThreadStateOperation.class, "Get stack trace jvmti", SystemEffect.SAFEPOINT));
        }

        @Override
        // @RestrictHeapAccess(reason = "jvmti", access = RestrictHeapAccess.Access.NO_ALLOCATION)
        protected void operate(NativeVMOperationData data) {
            getThreadState((JvmtiGetThreadStateOperationData) data);
        }
    }

    private static void getThreadState(JvmtiGetThreadStateOperationData data) {
        JThread jthread = data.getJThreadPointer();
        Thread thread;
        try {
            Object threadReference = JNIObjectHandles.getObject(jthread);
            thread = (Thread) threadReference;
        } catch (IllegalArgumentException | ClassCastException e) {
            data.setJvmtiError(JvmtiError.JVMTI_ERROR_INVALID_THREAD.getCValue());
            return;
        }
        if (thread == null) {
            thread = JavaThreads.getCurrentThreadOrNull();
            if (thread == null) {
                data.setJvmtiError(JvmtiError.JVMTI_ERROR_INTERNAL.getCValue());
                return;
            }
        }
        int result = getThreadState(thread);
        // TODO @dprcci better and more consistent error handling
        if (result == -1) {
            data.setJvmtiError(JvmtiError.JVMTI_ERROR_INTERNAL.getCValue());
            return;
        }
        data.getThreadStatePointer().write(result);
        data.setJvmtiError(JvmtiError.JVMTI_ERROR_NONE.getCValue());
    }

    // TODO @dprcci check
    protected static int getThreadState(Thread thread) {
        int result = 0;

        Thread.State state = thread.getState();
        if (!thread.isAlive()) {
            return (state == Thread.State.TERMINATED) ? JVMTI_THREAD_STATE_TERMINATED.getValue() : 0;
        }

        result |= JVMTI_THREAD_STATE_ALIVE.getValue();

        // threads cannot be suspended

        // Interrupted
        if (thread.isInterrupted()) { // if(JavaThreads.isInterrupted(thread)){
            result |= JVMTI_THREAD_STATE_INTERRUPTED.getValue();
        }
        // TODO @dprcci Correct IsoalteThread but doesn't work because it is put into safe point
        if (VMThreads.StatusSupport.getStatusVolatile(PlatformThreads.getIsolateThread(thread)) == VMThreads.StatusSupport.STATUS_IN_NATIVE) {
            result |= JVMTI_THREAD_STATE_IN_NATIVE.getValue();
        }

        if (state == Thread.State.RUNNABLE) {
            result |= JVMTI_THREAD_STATE_RUNNABLE.getValue();
            return result;
        }
        if (state == Thread.State.BLOCKED) {
            result |= JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER.getValue();
            return result;
        }

        // Waiting
        boolean timedWait;
        if ((timedWait = (state != Thread.State.WAITING)) && state != Thread.State.TIMED_WAITING) {
            return -1;
        }

        result |= JVMTI_THREAD_STATE_WAITING.getValue();
        result |= (timedWait) ? JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT.getValue() : JVMTI_THREAD_STATE_WAITING_INDEFINITELY.getValue();

        // Waiting reason
        int waitingStatus = MonitorSupport.singleton().getParkedThreadStatus(thread, timedWait);
        if (waitingStatus == ThreadStatus.IN_OBJECT_WAIT) {
            result |= JVMTI_THREAD_STATE_IN_OBJECT_WAIT.getValue();
        } else if (waitingStatus == ThreadStatus.PARKED || waitingStatus == ThreadStatus.PARKED_TIMED) {
            result |= JVMTI_THREAD_STATE_PARKED.getValue();
        }
        // Sleeping
        if (PlatformThreads.getThreadStatus(thread) == ThreadStatus.SLEEPING) {
            result |= JVMTI_THREAD_STATE_SLEEPING.getValue();
        }
        return result;
    }
}
