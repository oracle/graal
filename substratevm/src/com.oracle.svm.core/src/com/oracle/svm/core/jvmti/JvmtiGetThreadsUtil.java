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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.core.jvmti.headers.JThread;
import com.oracle.svm.core.jvmti.headers.JThreadPointer;
import com.oracle.svm.core.jvmti.headers.JThreadPointerPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiError;
import com.oracle.svm.core.jvmti.utils.JvmtiUtils;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMThreads;

public final class JvmtiGetThreadsUtil {

    private final JvmtiGetThreadsOperation operation;
    private static final int INITIAL_THREAD_BUFFER_CAPACITY = 16;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JvmtiGetThreadsUtil() {
        this.operation = new JvmtiGetThreadsOperation();
    }

    public static int getAllThreads(CIntPointer threadsCountPtr, JThreadPointerPointer threadsPtr) {
        return ImageSingletons.lookup(JvmtiGetThreadsUtil.class).getAllThreadsInternal(threadsCountPtr, threadsPtr);
    }

    public static int getCurrentThread(JThreadPointer threadPtr) {
        return ImageSingletons.lookup(JvmtiGetThreadsUtil.class).getCurrentThreadsInternal(threadPtr);
    }

    @Uninterruptible(reason = "jvmti GetCurrentThread")
    private int getCurrentThreadsInternal(JThreadPointer threadPtr) {
        Thread currentThread = Thread.currentThread();
        JThread jthread = (JThread) JNIObjectHandles.createLocal(currentThread);
        ((Pointer) threadPtr).writeWord(0, jthread);
        return JvmtiError.JVMTI_ERROR_NONE.getCValue();
    }

    private int getAllThreadsInternal(CIntPointer threadsCountPtr, JThreadPointerPointer threadsPtr) {
        int size = SizeOf.get(JvmtiGetAllThreadsVMOperationData.class);
        JvmtiGetAllThreadsVMOperationData data = StackValue.get(size);
        UnmanagedMemoryUtil.fill((Pointer) data, WordFactory.unsigned(size), (byte) 0);

        data.setCIntPointer(threadsCountPtr);
        data.setJThreadPointerPointer(threadsPtr.rawValue());

        operation.enqueue(data);
        return data.getJvmtiError();
    }

    // TODO @dprcci Is it better to allocate a bound max memory array in advance and realloc?

    @RawStructure
    private interface JvmtiGetAllThreadsVMOperationData extends NativeVMOperationData {
        @RawField
        void setJThreadPointerPointer(long ptr);

        @RawField
        long getJThreadPointerPointer();

        @RawField
        int getJvmtiError();

        @RawField
        void setJvmtiError(int error);

        @RawField
        void setCIntPointer(CIntPointer ptr);

        @RawField
        CIntPointer getCIntPointer();
    }

    private static class JvmtiGetThreadsOperation extends NativeVMOperation {
        JvmtiGetThreadsOperation() {
            super(VMOperationInfos.get(JvmtiGetThreadsUtil.JvmtiGetThreadsOperation.class, "Get stack trace jvmti", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate(NativeVMOperationData data) {
            getAllThreads((JvmtiGetAllThreadsVMOperationData) data);
        }
    }

    @Uninterruptible(reason = "jvmti GetAllThreads")
    static int getNumberOfThreads() {
        int nbOfThreads = 0;
        for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
            Thread thread = PlatformThreads.fromVMThread(isolateThread);
            // for consistency and clarity
            if (thread == null || thread.isVirtual() || !JavaThreads.isAlive(thread)) {
                continue;
            }
            nbOfThreads++;
        }
        return nbOfThreads;
    }

    @Uninterruptible(reason = "jvmti GetAllThreads")
    private static void getAllThreads(JvmtiGetAllThreadsVMOperationData data) {
        JThreadPointer arrayPtr = JvmtiUtils.allocateWordBuffer(INITIAL_THREAD_BUFFER_CAPACITY);
        int currentArraySize = INITIAL_THREAD_BUFFER_CAPACITY;
        int nbWritten = 0;
        for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
            Thread thread = PlatformThreads.fromVMThread(isolateThread);
            // "Get all live platform threads that are attached to the VM"
            if (thread == null || thread.isVirtual() || !JavaThreads.isAlive(thread)) {
                continue;
            }
            if (nbWritten == currentArraySize) {
                arrayPtr = JvmtiUtils.growWordBuffer(arrayPtr, INITIAL_THREAD_BUFFER_CAPACITY);
                currentArraySize += INITIAL_THREAD_BUFFER_CAPACITY;
            }
            JNIObjectHandle threadHandle = JNIObjectHandles.createLocal(thread);
            JvmtiUtils.writeWordAtIdxInBuffer(arrayPtr, nbWritten, threadHandle);
            nbWritten++;
        }

        JThreadPointerPointer threadsPtr = WordFactory.unsigned(data.getJThreadPointerPointer());
        CIntPointer threadsCountPtr = data.getCIntPointer();

        ((Pointer) threadsPtr).writeWord(0, arrayPtr);
        threadsCountPtr.write(nbWritten);
        data.setJvmtiError(JvmtiError.JVMTI_ERROR_NONE.getCValue());
    }

}
