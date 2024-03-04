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

import static com.oracle.svm.core.thread.JavaThreads.isVirtual;
import static com.oracle.svm.core.thread.PlatformThreads.getCarrierSPOrElse;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.function.CodePointer;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CIntPointer;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.code.FrameInfoQueryResult;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.jdk.StackTraceUtils;
import com.oracle.svm.core.jni.JNIObjectHandles;
import com.oracle.svm.core.jni.access.JNIReflectionDictionary;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.core.jvmti.headers.JThread;
import com.oracle.svm.core.jvmti.headers.JvmtiError;
import com.oracle.svm.core.jvmti.headers.JvmtiFrameInfo;
import com.oracle.svm.core.jvmti.headers.JvmtiFrameInfoPointer;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.stack.JavaStackFrameVisitor;
import com.oracle.svm.core.stack.JavaStackWalker;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMOperation;

public final class JvmtiStackTraceUtil {
    private static final int MAX_TEMP_BUFFER_SIZE = 64;
    private static final long NATIVE_METHOD_LOCATION = -1;
    private final VMMutex mutex;
    private final JvmtiStackTraceVisitor stackTraceVisitor;
    private final JvmtiFrameCountVisitor frameCountVisitor;
    private final JvmtiStackTraceOperation stackTraceOperation;
    private final JvmtiFrameCountOperation frameCountOperation;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JvmtiStackTraceUtil() {
        this.mutex = new VMMutex("jvmti GetStackTrace Visitor");
        this.stackTraceVisitor = new JvmtiStackTraceVisitor();
        this.frameCountVisitor = new JvmtiFrameCountVisitor();
        this.stackTraceOperation = new JvmtiStackTraceOperation();
        this.frameCountOperation = new JvmtiFrameCountOperation();
    }

    public static JvmtiError getStackTrace(JThread jthread, int startDepth, int maxFrameCount, JvmtiFrameInfoPointer frameBuffer, CIntPointer countPtr) {
        return ImageSingletons.lookup(JvmtiStackTraceUtil.class).getStackTraceInternal(jthread, startDepth, maxFrameCount, frameBuffer, countPtr);
    }

    public static JvmtiError getFrameCount(JThread jthread, CIntPointer countPtr) {
        return ImageSingletons.lookup(JvmtiStackTraceUtil.class).getFrameCountInternal(jthread, countPtr);
    }

    private JvmtiError getStackTraceInternal(JThread jthread, int startDepth, int maxFrameCount, JvmtiFrameInfoPointer frameBuffer, CIntPointer countPtr) {
        try {
            mutex.lock();

            // TODO @dprcci correct that logic? Do we want the program to crash
            JvmtiError error = verifyJThreadHandle(jthread);
            if (error != JvmtiError.JVMTI_ERROR_NONE) {
                return error;
            }

            if (!stackTraceVisitor.initialize(startDepth, maxFrameCount, frameBuffer)) {
                return JvmtiError.JVMTI_ERROR_INTERNAL;
            }

            int size = SizeOf.get(JvmtiStackWalkVMOperationData.class);
            JvmtiStackWalkVMOperationData data = StackValue.get(size);
            UnmanagedMemoryUtil.fill((Pointer) data, WordFactory.unsigned(size), (byte) 0);

            data.setThreadHandle(jthread);
            // JNIReflectionDictionary.singleton().dump(true, "egg");
            stackTraceOperation.enqueue(data);

            if (startDepth < 0) {
                stackTraceVisitor.fillResultBuffer();
            }
            countPtr.write(stackTraceVisitor.nbCollectedFrames);
            return JvmtiError.JVMTI_ERROR_NONE;
        } finally {
            mutex.unlock();
        }
    }

    @RawStructure
    private interface JvmtiStackWalkVMOperationData extends NativeVMOperationData {
        @RawField
        JThread getThreadHandle();

        @RawField
        void setThreadHandle(JThread jthread);
    }

    private class JvmtiStackTraceOperation extends NativeVMOperation {
        JvmtiStackTraceOperation() {
            super(VMOperationInfos.get(JvmtiStackTraceUtil.JvmtiStackTraceOperation.class, "Get stack trace jvmti", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate(NativeVMOperationData data) {
            visitStackTrace((JvmtiStackWalkVMOperationData) data, stackTraceVisitor);
        }
    }

    @NeverInline(value = "jvmti GetStackTrace")
    private void visitStackTrace(JvmtiStackWalkVMOperationData data, JavaStackFrameVisitor visitor) {
        assert VMOperation.isInProgressAtSafepoint();

        Thread thread = JNIObjectHandles.getObject(data.getThreadHandle());
        if (isVirtual(thread)) {
            return;
        }
        Pointer callerSP = KnownIntrinsics.readCallerStackPointer();
        IsolateThread isolateThread = PlatformThreads.getIsolateThread(thread);

        Pointer carrierSP = getCarrierSPOrElse(thread, WordFactory.nullPointer());
        if (isolateThread == CurrentIsolate.getCurrentThread()) {
            Pointer startSP = carrierSP.isNonNull() ? carrierSP : callerSP;
            Pointer endSP = WordFactory.nullPointer();
            JavaStackWalker.walkCurrentThread(startSP, endSP, visitor);
            return;
        }

        if (carrierSP.isNonNull()) { // mounted virtual thread, skip its frames
            CodePointer carrierIP = FrameAccess.singleton().readReturnAddress(carrierSP);
            Pointer endSP = WordFactory.nullPointer();
            assert VMOperation.isInProgressAtSafepoint();
            JavaStackWalker.walkThreadAtSafepoint(carrierSP, endSP, carrierIP, visitor);
            return;
        }
        if (isolateThread.isNull()) { // recently launched thread
            return;
        }
        Pointer endSP = WordFactory.nullPointer();
        JavaStackWalker.walkThread(isolateThread, endSP, visitor, null);
    }

    public static boolean startDepthIsOutOfBound(int startDepth, int maxBound) {
        boolean overOrEqualsMaxSize = startDepth > 0 && startDepth >= maxBound;
        boolean belowMinSize = startDepth < 0 && startDepth < -maxBound;
        return overOrEqualsMaxSize || belowMinSize;
    }

    static class JvmtiStackTraceVisitor extends JavaStackFrameVisitor {
        private JvmtiFrameInfoPointer jvmtiFramePtr;
        private JvmtiFrameInfoPointer jvmtiFramePtrTemp;
        private int nbFramesVisited;
        private int startDepth;
        private int maxFrameCount;
        private int nbCollectedFrames;

        private JvmtiStackTraceVisitor() {
        }

        boolean initialize(int startDepth, int maxFrameCount, JvmtiFrameInfoPointer frameBuffer) {
            this.jvmtiFramePtr = frameBuffer;
            this.startDepth = startDepth;
            this.maxFrameCount = maxFrameCount;
            this.nbFramesVisited = 0;
            this.nbCollectedFrames = 0;
            if (startDepth >= 0) {
                return true;
            }
            if (-startDepth > MAX_TEMP_BUFFER_SIZE) {
                return false;
            }
            jvmtiFramePtrTemp = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(SizeOf.get(JvmtiFrameInfo.class) * (-startDepth)));
            return true;
        }

        @Override
        public boolean visitFrame(FrameInfoQueryResult frameInfo) {
            if (!StackTraceUtils.shouldShowFrame(frameInfo, false, true, false)) {
                /* Always ignore the frame. It is an internal frame of the VM. */
                return true;

                // TODO @dprcci useful? expand?
            } else if (Throwable.class.isAssignableFrom(frameInfo.getSourceClass())) {
                /*
                 * We are still in the constructor invocation chain at the beginning of the stack
                 * trace, which is also filtered by the Java HotSpot VM.
                 */
                return true;
            }

            /*
             * if the index is negative, the bottom the stack is the reference point, therefor we
             * have to go all the way down. We reuse the same buffer to avoid useless memory
             * allocation
             */
            if (startDepth < 0) {
                int index = nbCollectedFrames % (-startDepth);
                assert jvmtiFramePtrTemp.isNonNull();
                nbCollectedFrames += convertToJvmtiFrameInfo(frameInfo, jvmtiFramePtrTemp, index, true);
                return true;
            }

            // TODO @dprcci What is the expected behaviour? Should a frame not containing a
            // registered method be counted when going to startDepth?
            if (nbCollectedFrames < maxFrameCount) {
                int addedFrame = convertToJvmtiFrameInfo(frameInfo, jvmtiFramePtr, nbCollectedFrames, nbFramesVisited + 1 > startDepth);
                nbFramesVisited += addedFrame;
                if (nbFramesVisited > startDepth) {
                    nbCollectedFrames += addedFrame;
                }
            }
            return nbCollectedFrames < maxFrameCount;
        }

        private void fillResultBuffer() {
            int bufferSize = -startDepth;
            int resultSize = Math.min(bufferSize, maxFrameCount);
            int oldestIndex = (maxFrameCount > nbCollectedFrames) ? 0 : nbCollectedFrames % bufferSize;
            for (int i = 0; i < resultSize; i++) {
                JvmtiFrameInfo res = getJvmtiFrameInfoAtIdx(jvmtiFramePtr, i);
                JvmtiFrameInfo tmp = getJvmtiFrameInfoAtIdx(jvmtiFramePtrTemp, (oldestIndex + i) % bufferSize);
                res.setMethod(tmp.getMethod());
                res.setLocation(tmp.getLocation());
            }
            this.nbCollectedFrames = resultSize;
            ImageSingletons.lookup(UnmanagedMemorySupport.class).free(jvmtiFramePtrTemp);
        }

        private int convertToJvmtiFrameInfo(FrameInfoQueryResult frameInfo, JvmtiFrameInfoPointer jvmtiFrameInfo, int index, boolean fill) {
            long location = frameInfo.getBci();
            String methodName = frameInfo.getSourceMethodName();
            Class<?> methodClass = frameInfo.getSourceClass();

            JNIMethodId jMethodId = JNIReflectionDictionary.singleton().toMethodID(methodClass, methodName);
            // JNIMethodId jMethodId = JNIReflectionDictionary.singleton().getRandomMethodID();
            // Method is not (should it be?) exposed to user, do not take it into account
            if (jMethodId.isNull()) {
                return 0;
            }
            if (!fill) {
                return 1;
            }
            location = JNIReflectionDictionary.isMethodNative(jMethodId) ? NATIVE_METHOD_LOCATION : location;
            JvmtiFrameInfo currentFrame = getJvmtiFrameInfoAtIdx(jvmtiFrameInfo, index);
            currentFrame.setLocation(location);
            currentFrame.setMethod(jMethodId);
            return 1;
        }

        private JvmtiFrameInfo getJvmtiFrameInfoAtIdx(JvmtiFrameInfoPointer jvmtiFrameInfo, int index) {
            return (JvmtiFrameInfo) ((Pointer) jvmtiFrameInfo).add(index * SizeOf.get(JvmtiFrameInfo.class));
        }
    }

    // Frame Count

    private JvmtiError getFrameCountInternal(JThread jthread, CIntPointer countPtr) {
        try {
            mutex.lock();
            JvmtiError error = verifyJThreadHandle(jthread);
            if (error != JvmtiError.JVMTI_ERROR_NONE) {
                return error;
            }

            int size = SizeOf.get(JvmtiStackWalkVMOperationData.class);
            JvmtiStackWalkVMOperationData data = StackValue.get(size);
            UnmanagedMemoryUtil.fill((Pointer) data, WordFactory.unsigned(size), (byte) 0);

            data.setThreadHandle(jthread);
            frameCountOperation.enqueue(data);

            countPtr.write(frameCountVisitor.count);
            return JvmtiError.JVMTI_ERROR_NONE;
        } finally {
            mutex.unlock();
        }
    }

    private class JvmtiFrameCountOperation extends NativeVMOperation {
        JvmtiFrameCountOperation() {
            super(VMOperationInfos.get(JvmtiStackTraceUtil.JvmtiFrameCountOperation.class, "Get stack trace jvmti", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate(NativeVMOperationData data) {
            frameCountVisitor.count = 0;
            visitStackTrace((JvmtiStackWalkVMOperationData) data, frameCountVisitor);
        }
    }

    static class JvmtiFrameCountVisitor extends JavaStackFrameVisitor {
        private int count = 0;

        private JvmtiFrameCountVisitor() {
        }

        @Override
        public boolean visitFrame(FrameInfoQueryResult frameInfo) {
            if (!StackTraceUtils.shouldShowFrame(frameInfo, false, true, false)) {
                return true;
            } else if (Throwable.class.isAssignableFrom(frameInfo.getSourceClass())) {
                return true;
            }
            // TODO @dprcci collect only frames with contain accessible information?
            this.count += 1;
            return true;
        }
    }

    // Helpers

    private static JvmtiError verifyJThreadHandle(JThread jthread) {
        Thread thread;
        if (jthread.equal(WordFactory.nullPointer())) {
            thread = JavaThreads.getCurrentThreadOrNull();
        } else {
            try {
                Object threadReference = JNIObjectHandles.getObject(jthread);
                thread = (Thread) threadReference;
            } catch (IllegalArgumentException | ClassCastException e) {
                return JvmtiError.JVMTI_ERROR_INVALID_THREAD;
            }
        }
        if (thread == null) {
            return JvmtiError.JVMTI_ERROR_INVALID_THREAD;
        }
        if (!thread.isAlive()) {
            return JvmtiError.JVMTI_ERROR_THREAD_NOT_ALIVE;
        }
        return JvmtiError.JVMTI_ERROR_NONE;
    }
}
