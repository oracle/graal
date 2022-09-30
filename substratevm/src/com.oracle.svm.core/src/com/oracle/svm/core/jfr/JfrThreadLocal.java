/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jfr;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.events.ExecutionSampleEvent;
import com.oracle.svm.core.jfr.events.ThreadEndEvent;
import com.oracle.svm.core.jfr.events.ThreadStartEvent;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.Target_java_lang_Thread;
import com.oracle.svm.core.thread.ThreadListener;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.util.VMError;

/**
 * This class holds various JFR-specific thread local values.
 *
 * Each thread uses both a Java and a native {@link JfrBuffer}:
 * <ul>
 * <li>The Java buffer is accessed by JFR events that are implemented as Java classes and written
 * using {@code EventWriter}.</li>
 * <li>The native buffer is accessed when {@link JfrNativeEventWriter} is used to write an
 * event.</li>
 * </ul>
 *
 * It is necessary to have separate buffers as a native JFR event (e.g., a GC or an allocation)
 * could otherwise destroy an Java-level JFR event. All methods that access a {@link JfrBuffer} must
 * be uninterruptible to avoid races with JFR code that is executed at a safepoint (such code may
 * modify the buffers of other threads).
 */
public class JfrThreadLocal implements ThreadListener {
    private static final FastThreadLocalObject<Target_jdk_jfr_internal_EventWriter> javaEventWriter = FastThreadLocalFactory.createObject(Target_jdk_jfr_internal_EventWriter.class,
                    "JfrThreadLocal.javaEventWriter");
    private static final FastThreadLocalWord<JfrBuffer> javaBuffer = FastThreadLocalFactory.createWord("JfrThreadLocal.javaBuffer");
    private static final FastThreadLocalWord<JfrBuffer> nativeBuffer = FastThreadLocalFactory.createWord("JfrThreadLocal.nativeBuffer");
    private static final FastThreadLocalWord<UnsignedWord> dataLost = FastThreadLocalFactory.createWord("JfrThreadLocal.dataLost");
    private static final FastThreadLocalLong threadId = FastThreadLocalFactory.createLong("JfrThreadLocal.threadId");
    private static final FastThreadLocalLong parentThreadId = FastThreadLocalFactory.createLong("JfrThreadLocal.parentThreadId");

    private long threadLocalBufferSize;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrThreadLocal() {
    }

    public void initialize(long bufferSize) {
        this.threadLocalBufferSize = bufferSize;
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    @Override
    public void beforeThreadRun(IsolateThread isolateThread, Thread javaThread) {
        // We copy the thread id to a thread-local in the IsolateThread. This is necessary so that
        // we are always able to access that value without having to go through a heap-allocated
        // Java object.
        Target_java_lang_Thread t = SubstrateUtil.cast(javaThread, Target_java_lang_Thread.class);
        threadId.set(isolateThread, t.getId());
        parentThreadId.set(isolateThread, JavaThreads.getParentThreadId(javaThread));

        SubstrateJVM.getThreadRepo().registerThread(javaThread);

        // Emit ThreadStart event before thread.run().
        ThreadStartEvent.emit(isolateThread);

        // Register ExecutionSampleEvent after ThreadStart event and before thread.run().
        ExecutionSampleEvent.tryToRegisterExecutionSampleEventCallback();
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    @Override
    public void afterThreadExit(IsolateThread isolateThread, Thread javaThread) {
        // Emit ThreadEnd event after thread.run() finishes.
        ThreadEndEvent.emit(isolateThread);

        // Flush all buffers if necessary.
        if (SubstrateJVM.isRecording()) {
            JfrBuffer jb = javaBuffer.get(isolateThread);
            if (jb.isNonNull()) {
                flush(jb, WordFactory.unsigned(0), 0);
            }

            JfrBuffer nb = nativeBuffer.get(isolateThread);
            if (nb.isNonNull()) {
                flush(nb, WordFactory.unsigned(0), 0);
            }
        }

        // Free and reset all data.
        threadId.set(isolateThread, 0);
        parentThreadId.set(isolateThread, 0);
        dataLost.set(isolateThread, WordFactory.unsigned(0));
        javaEventWriter.set(isolateThread, null);

        freeBuffer(javaBuffer.get(isolateThread));
        javaBuffer.set(isolateThread, WordFactory.nullPointer());

        freeBuffer(nativeBuffer.get(isolateThread));
        nativeBuffer.set(isolateThread, WordFactory.nullPointer());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getTraceId(IsolateThread isolateThread) {
        return threadId.get(isolateThread);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getParentThreadId(IsolateThread isolateThread) {
        return parentThreadId.get(isolateThread);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getThreadLocalBufferSize() {
        return threadLocalBufferSize;
    }

    public Target_jdk_jfr_internal_EventWriter getEventWriter() {
        return javaEventWriter.get();
    }

    // If a safepoint happens in this method, the state that another thread can see is always
    // sufficiently consistent as the JFR buffer is still empty. So, this method does not need to be
    // uninterruptible.
    public Target_jdk_jfr_internal_EventWriter newEventWriter() {
        assert javaEventWriter.get() == null;
        assert javaBuffer.get().isNull();

        JfrBuffer buffer = getJavaBuffer();
        if (buffer.isNull()) {
            throw new OutOfMemoryError("OOME for thread local buffer");
        }

        assert JfrBufferAccess.isEmpty(buffer) : "a fresh JFR buffer must be empty";
        long startPos = buffer.getPos().rawValue();
        long maxPos = JfrBufferAccess.getDataEnd(buffer).rawValue();
        long addressOfPos = JfrBufferAccess.getAddressOfPos(buffer).rawValue();
        long jfrThreadId = SubstrateJVM.getThreadId(CurrentIsolate.getCurrentThread());
        Target_jdk_jfr_internal_EventWriter result;
        if (JavaVersionUtil.JAVA_SPEC >= 19) {
            result = new Target_jdk_jfr_internal_EventWriter(startPos, maxPos, addressOfPos, jfrThreadId, true, false);
        } else {
            result = new Target_jdk_jfr_internal_EventWriter(startPos, maxPos, addressOfPos, jfrThreadId, true);
        }
        javaEventWriter.set(result);

        return result;
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public JfrBuffer getJavaBuffer() {
        VMError.guarantee(threadId.get() > 0, "Thread local JFR data must be initialized");
        JfrBuffer result = javaBuffer.get();
        if (result.isNull()) {
            result = JfrBufferAccess.allocate(WordFactory.unsigned(threadLocalBufferSize), JfrBufferType.THREAD_LOCAL_JAVA);
            javaBuffer.set(result);
        }
        return result;
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.", callerMustBe = true)
    public JfrBuffer getNativeBuffer() {
        VMError.guarantee(threadId.get() > 0, "Thread local JFR data must be initialized");
        JfrBuffer result = nativeBuffer.get();
        if (result.isNull()) {
            result = JfrBufferAccess.allocate(WordFactory.unsigned(threadLocalBufferSize), JfrBufferType.THREAD_LOCAL_NATIVE);
            nativeBuffer.set(result);
        }
        return result;
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.", callerMustBe = true)
    public static JfrBuffer getJavaBuffer(IsolateThread thread) {
        assert (VMOperation.isInProgressAtSafepoint());
        return javaBuffer.get(thread);
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.", callerMustBe = true)
    public static JfrBuffer getNativeBuffer(IsolateThread thread) {
        assert (VMOperation.isInProgressAtSafepoint());
        return nativeBuffer.get(thread);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void notifyEventWriter(IsolateThread thread) {
        if (javaEventWriter.get(thread) != null) {
            javaEventWriter.get(thread).notified = true;
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static JfrBuffer flush(JfrBuffer threadLocalBuffer, UnsignedWord uncommitted, int requested) {
        assert threadLocalBuffer.isNonNull();

        UnsignedWord unflushedSize = JfrBufferAccess.getUnflushedSize(threadLocalBuffer);
        if (unflushedSize.aboveThan(0)) {
            JfrGlobalMemory globalMemory = SubstrateJVM.getGlobalMemory();
            if (!globalMemory.write(threadLocalBuffer, unflushedSize)) {
                JfrBufferAccess.reinitialize(threadLocalBuffer);
                writeDataLoss(threadLocalBuffer, unflushedSize);
                return WordFactory.nullPointer();
            }
        }

        if (uncommitted.aboveThan(0)) {
            // Copy all uncommitted memory to the start of the thread local buffer.
            assert JfrBufferAccess.getDataStart(threadLocalBuffer).add(uncommitted).belowOrEqual(JfrBufferAccess.getDataEnd(threadLocalBuffer));
            UnmanagedMemoryUtil.copy(threadLocalBuffer.getPos(), JfrBufferAccess.getDataStart(threadLocalBuffer), uncommitted);
        }
        JfrBufferAccess.reinitialize(threadLocalBuffer);
        assert JfrBufferAccess.getUnflushedSize(threadLocalBuffer).equal(0);
        if (threadLocalBuffer.getSize().aboveOrEqual(uncommitted.add(requested))) {
            return threadLocalBuffer;
        }
        return WordFactory.nullPointer();
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void writeDataLoss(JfrBuffer buffer, UnsignedWord unflushedSize) {
        assert buffer.isNonNull();
        assert unflushedSize.aboveThan(0);
        UnsignedWord totalDataLoss = increaseDataLost(unflushedSize);
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvent.DataLoss)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initialize(data, buffer);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.DataLoss);
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
            JfrNativeEventWriter.putLong(data, unflushedSize.rawValue());
            JfrNativeEventWriter.putLong(data, totalDataLoss.rawValue());
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord increaseDataLost(UnsignedWord delta) {
        UnsignedWord result = dataLost.get().add(delta);
        dataLost.set(result);
        return result;
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void freeBuffer(JfrBuffer buffer) {
        JfrBufferAccess.free(buffer);
    }
}
