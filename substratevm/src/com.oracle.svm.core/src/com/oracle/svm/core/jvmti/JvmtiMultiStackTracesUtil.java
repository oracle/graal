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

import static com.oracle.svm.core.jvmti.JvmtiThreadStateUtil.getThreadState;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CConst;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jvmti.headers.JThread;
import com.oracle.svm.core.jvmti.headers.JThreadPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiError;
import com.oracle.svm.core.jvmti.headers.JvmtiFrameInfo;
import com.oracle.svm.core.jvmti.headers.JvmtiFrameInfoPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiStackInfo;
import com.oracle.svm.core.jvmti.headers.JvmtiStackInfoPointer;
import com.oracle.svm.core.jvmti.headers.JvmtiStackInfoPointerPointer;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMThreads;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;

//TODO @dprcci Group all those thread related classed at some point (or create sub package ?)
public final class JvmtiMultiStackTracesUtil {
    private final JvmtiGetAllStackTracesOperation allStackTracesOperation;
    private final JvmtiGetListStackTracesOperation listStackTracesOperation;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JvmtiMultiStackTracesUtil() {
        this.allStackTracesOperation = new JvmtiGetAllStackTracesOperation();
        this.listStackTracesOperation = new JvmtiGetListStackTracesOperation();
    }

    public static int getAllStackTraces(int maxFrameCount, JvmtiStackInfoPointerPointer stackInfoPtr, CIntPointer threadCountPtr) {
        return ImageSingletons.lookup(JvmtiMultiStackTracesUtil.class).getAllStackTracesInternal(maxFrameCount, stackInfoPtr, threadCountPtr);
    }

    public static int getListStackTraces(int threadCount, @CConst JThreadPointer threadListHead, int maxFrameCount, JvmtiStackInfoPointerPointer stackInfoPtr) {
        return ImageSingletons.lookup(JvmtiMultiStackTracesUtil.class).getListStackTracesInternal(threadCount, threadListHead, maxFrameCount, stackInfoPtr);
    }

    // TODO @dprcci The problem with reusing the existing code is that a thread list (java) cannot
    // be used as no memory can be allocated.
    // Using a stack array is not an option as the size must be a compile time constant. The only
    // option would be to allocated UnamangedMemory?
    private int getListStackTracesInternal(int threadCount, @CConst JThreadPointer threadListHead, int maxFrameCount, JvmtiStackInfoPointerPointer stackInfoPtr) {

        int size = SizeOf.get(JvmtiGetListStackTracesVMOperationData.class);
        JvmtiGetListStackTracesVMOperationData data = StackValue.get(size);
        UnmanagedMemoryUtil.fill((Pointer) data, WordFactory.unsigned(size), (byte) 0);

        data.setStackInfoPointerPointer(stackInfoPtr);
        data.setMaxFrameCount(maxFrameCount);
        data.setNbThreads(threadCount);
        data.setThreadListHead(threadListHead);

        listStackTracesOperation.enqueue(data);
        return data.getJvmtiError();
    }

    @RawStructure
    private interface JvmtiGetListStackTracesVMOperationData extends NativeVMOperationData {
        @RawField
        void setStackInfoPointerPointer(JvmtiStackInfoPointerPointer ptr);

        @RawField
        JvmtiStackInfoPointerPointer getStackInfoPointerPointer();

        @RawField
        int getJvmtiError();

        @RawField
        void setJvmtiError(int error);

        @RawField
        void setNbThreads(int nbThreads);

        @RawField
        int getNbThreads();

        @RawField
        void setThreadListHead(JThreadPointer head);

        @RawField
        JThreadPointer getThreadListHead();

        @RawField
        void setMaxFrameCount(int maxFrameCount);

        @RawField
        int getMaxFrameCount();
    }

    // TODO @dprcci cannot allocate memory
    private static class JvmtiGetListStackTracesOperation extends NativeVMOperation {
        JvmtiGetListStackTracesOperation() {
            super(VMOperationInfos.get(JvmtiGetListStackTracesOperation.class, "Get stack trace jvmti", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate(NativeVMOperationData data) {
            getListStackTraces((JvmtiGetListStackTracesVMOperationData) data);
        }
    }

    private static void getListStackTraces(JvmtiGetListStackTracesVMOperationData data) {
        int maxFrameCount = data.getMaxFrameCount();
        int nbThreads = data.getNbThreads();
        JThreadPointer threadListHead = data.getThreadListHead();

        JvmtiStackInfoPointer stackInfoBuffer = allocateStackInfoBuffer(nbThreads, maxFrameCount);
        if (stackInfoBuffer.isNull()) {
            data.setJvmtiError(JvmtiError.JVMTI_ERROR_INTERNAL.getCValue());
            return;
        }

        int nbWritten = fillStackInfoBuffer(nbThreads, threadListHead, maxFrameCount, stackInfoBuffer);
        if (nbWritten != nbThreads) {
            cleanup(stackInfoBuffer);
            data.setJvmtiError(JvmtiError.JVMTI_ERROR_INVALID_THREAD.getCValue());
            return;
        }

        ((Pointer) data.getStackInfoPointerPointer()).writeWord(0, stackInfoBuffer);
        data.setJvmtiError(JvmtiError.JVMTI_ERROR_NONE.getCValue());
    }

    private static int fillStackInfoBuffer(int nbThreads, JThreadPointer threadListHead, int maxFrameCount, JvmtiStackInfoPointer stackInfoBuffer) {
        int nbWritten = 0;
        for (int i = 0; i < nbThreads; i++) {
            JThread currentJThread = readThreadAtIdx(threadListHead, i);
            Thread currentThread;

            Pointer threadPtr = StackValue.get(ConfigurationValues.getTarget().wordSize);
            if (getThreadFromHandle(currentJThread, threadPtr) != 0) {
                break;
            }
            currentThread = (Thread) threadPtr.readObject(0);
            JvmtiStackInfo currentStackInfo = readStackInfoAt(stackInfoBuffer, i, maxFrameCount);
            fillStackInfo(currentStackInfo, currentThread, maxFrameCount);
            nbWritten++;
        }
        return nbWritten;
    }

    private static JThread readThreadAtIdx(JThreadPointer head, int index) {
        return ((Pointer) head).readWord(index * ConfigurationValues.getTarget().wordSize);
    }

    private int getAllStackTracesInternal(int maxFrameCount, JvmtiStackInfoPointerPointer stackInfoPtr, CIntPointer threadCountPtr) {
        int size = SizeOf.get(JvmtiGetAllStackTracesVMOperationData.class);
        JvmtiGetAllStackTracesVMOperationData data = StackValue.get(size);
        UnmanagedMemoryUtil.fill((Pointer) data, WordFactory.unsigned(size), (byte) 0);

        data.setStackInfoPointerPointer(stackInfoPtr);
        data.setMaxFrameCount(maxFrameCount);
        data.setCIntPointer(threadCountPtr);

        allStackTracesOperation.enqueue(data);
        return data.getJvmtiError();
    }

    @RawStructure
    private interface JvmtiGetAllStackTracesVMOperationData extends NativeVMOperationData {
        @RawField
        void setStackInfoPointerPointer(JvmtiStackInfoPointerPointer ptr);

        @RawField
        JvmtiStackInfoPointerPointer getStackInfoPointerPointer();

        @RawField
        int getJvmtiError();

        @RawField
        void setJvmtiError(int error);

        @RawField
        void setCIntPointer(CIntPointer ptr);

        @RawField
        CIntPointer getCIntPointer();

        @RawField
        void setMaxFrameCount(int maxFrameCount);

        @RawField
        int getMaxFrameCount();
    }

    // TODO @dprcci cannot allocate memory
    private static class JvmtiGetAllStackTracesOperation extends NativeVMOperation {
        JvmtiGetAllStackTracesOperation() {
            super(VMOperationInfos.get(JvmtiGetAllStackTracesOperation.class, "Get stack trace jvmti", SystemEffect.SAFEPOINT));
        }

        @Override
        // @RestrictHeapAccess(reason = "jvmti", access = RestrictHeapAccess.Access.NO_ALLOCATION)
        protected void operate(NativeVMOperationData data) {
            getAllStackTraces((JvmtiGetAllStackTracesVMOperationData) data);
        }
    }

    private static void getAllStackTraces(JvmtiGetAllStackTracesVMOperationData data) {

        int maxFrameCount = data.getMaxFrameCount();
        int nbThreads = JvmtiGetThreadsUtil.getNumberOfThreads();

        JvmtiStackInfoPointer stackInfoBuffer = allocateStackInfoBuffer(nbThreads, maxFrameCount);
        if (stackInfoBuffer.isNull()) {
            data.setJvmtiError(JvmtiError.JVMTI_ERROR_INTERNAL.getCValue());
            return;
        }

        int nbStackInfo = fillStackInfoBuffer(stackInfoBuffer, nbThreads, maxFrameCount);
        if (nbStackInfo != nbThreads) {
            // TODO @dprcci if error means more threads are being written to than expected. Only
            // "nbThreads" have been allocated
            cleanup(stackInfoBuffer);
            data.setJvmtiError(JvmtiError.JVMTI_ERROR_INTERNAL.getCValue());
            return;
        }

        ((Pointer) data.getStackInfoPointerPointer()).writeWord(0, stackInfoBuffer);
        ((Pointer) data.getCIntPointer()).writeInt(0, nbStackInfo);
        data.setJvmtiError(JvmtiError.JVMTI_ERROR_NONE.getCValue());
    }

    private static int fillStackInfoBuffer(JvmtiStackInfoPointer stackInfoBuffer, int nbThreads, int maxFrameCount) {
        int nbWritten = 0;
        for (IsolateThread isolateThread = VMThreads.firstThread(); isolateThread.isNonNull(); isolateThread = VMThreads.nextThread(isolateThread)) {
            Thread thread = PlatformThreads.fromVMThread(isolateThread);
            // "Get all live platform threads that are attached to the VM"
            if (thread == null || thread.isVirtual()) {
                continue;
            }
            if (nbWritten >= nbThreads) {
                return -1;
            }
            JvmtiStackInfo currentStackInfo = readStackInfoAt(stackInfoBuffer, nbWritten, maxFrameCount);
            fillStackInfo(currentStackInfo, thread, maxFrameCount);
            nbWritten++;
        }
        return nbWritten;
    }

    private static void fillStackInfo(JvmtiStackInfo stackInfo, Thread thread, int maxFrameCount) {
        JThread jthread = (JThread) JNIObjectHandles.createLocal(thread);
        stackInfo.setThread(jthread);

        CIntPointer nbFramesPr = StackValue.get(CIntPointer.class);
        JvmtiStackTraceUtil.getStackTrace(jthread, 0, maxFrameCount, stackInfo.getFrameInfo(), nbFramesPr);
        stackInfo.setFrameCount(nbFramesPr.read());

        int threadState = getThreadState(thread);
        stackInfo.setState(threadState);
    }

    private static JvmtiStackInfoPointer allocateStackInfoBuffer(int bufferSize, int maxFrameCount) {
        int totalBufferSize = bufferSize * stackInfoOffsetWithFrameInfoBuffer(maxFrameCount);
        JvmtiStackInfoPointer allocatedBufferPtr = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(totalBufferSize));
        if (allocatedBufferPtr.isNull()) {
            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(allocatedBufferPtr);
            return WordFactory.nullPointer();
        }
        for (int i = 0; i < bufferSize; i++) {
            JvmtiStackInfo currentStackInfo = readStackInfoAt(allocatedBufferPtr, i, maxFrameCount);
            JvmtiFrameInfoPointer currentFrame = readFrameInfoAt(allocatedBufferPtr, i, maxFrameCount);
            currentStackInfo.setFrameInfo(currentFrame);
        }
        return allocatedBufferPtr;
    }

    // free up to (including) the failed allocated FrameInfo
    private static void cleanup(JvmtiStackInfoPointer stackInfoBuffer) {
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(stackInfoBuffer);
    }

    private static JvmtiFrameInfoPointer readFrameInfoAt(JvmtiStackInfoPointer arrayPtr, int index, int maxFrameCount) {
        return (JvmtiFrameInfoPointer) ((Pointer) arrayPtr).add(index * stackInfoOffsetWithFrameInfoBuffer(maxFrameCount) + stackInfoOffset());
    }

    private static JvmtiStackInfo readStackInfoAt(JvmtiStackInfoPointer arrayPtr, int index, int maxFrameCount) {
        return (JvmtiStackInfo) ((Pointer) arrayPtr).add(index * stackInfoOffsetWithFrameInfoBuffer(maxFrameCount));
    }

    @Fold
    static int stackInfoOffset() {
        return NumUtil.roundUp(SizeOf.get(JvmtiStackInfo.class), ConfigurationValues.getTarget().wordSize);
    }

    static int stackInfoOffsetWithFrameInfoBuffer(int maxFrameInfo) {
        return NumUtil.roundUp(stackInfoOffset() + (SizeOf.get(JvmtiFrameInfo.class) * maxFrameInfo), ConfigurationValues.getTarget().wordSize);
    }

    private static int getThreadFromHandle(JThread handle, Pointer result) {
        Thread thread;
        try {
            Object threadReference = JNIObjectHandles.getObject(handle);
            thread = (Thread) threadReference;
        } catch (IllegalArgumentException | ClassCastException e) {
            return -1;
        }
        result.writeObject(0, thread);
        return 0;
    }

}
