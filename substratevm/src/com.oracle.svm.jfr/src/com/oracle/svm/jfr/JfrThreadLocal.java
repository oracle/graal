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
package com.oracle.svm.jfr;

import com.oracle.svm.core.thread.VMOperation;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.thread.Target_java_lang_Thread;
import com.oracle.svm.core.thread.ThreadListener;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.util.VMError;

import jdk.jfr.internal.EventWriter;

/**
 * This class holds various JFR-specific thread local values.
 *
 * Each thread uses both a Java and a native {@link JfrBuffer}:
 * <ul>
 * <li>The Java buffer is accessed by JFR events that are implemented as Java classes and written
 * using {@link EventWriter}.</li>
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
    private static final FastThreadLocalObject<Target_jdk_jfr_internal_EventWriter> javaEventWriter = FastThreadLocalFactory.createObject(Target_jdk_jfr_internal_EventWriter.class);
    private static final FastThreadLocalWord<JfrBuffer> javaBuffer = FastThreadLocalFactory.createWord();
    private static final FastThreadLocalWord<JfrBuffer> nativeBuffer = FastThreadLocalFactory.createWord();
    private static final FastThreadLocalWord<UnsignedWord> dataLost = FastThreadLocalFactory.createWord();
    private static final FastThreadLocalLong traceId = FastThreadLocalFactory.createLong();

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
        // we are always able access that value without having to go through a heap-allocated Java
        // object.
        Target_java_lang_Thread t = SubstrateUtil.cast(javaThread, Target_java_lang_Thread.class);
        traceId.set(isolateThread, t.getId());
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    @Override
    public void afterThreadExit(IsolateThread isolateThread, Thread javaThread) {
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
        traceId.set(isolateThread, 0);
        dataLost.set(isolateThread, WordFactory.unsigned(0));
        javaEventWriter.set(isolateThread, null);

        freeBuffer(javaBuffer.get(isolateThread));
        javaBuffer.set(isolateThread, WordFactory.nullPointer());

        freeBuffer(nativeBuffer.get(isolateThread));
        nativeBuffer.set(isolateThread, WordFactory.nullPointer());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getTraceId(IsolateThread isolateThread) {
        return traceId.get(isolateThread);
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
        long jfrThreadId = SubstrateJVM.get().getThreadId(CurrentIsolate.getCurrentThread());
        Target_jdk_jfr_internal_EventWriter result = new Target_jdk_jfr_internal_EventWriter(startPos, maxPos, addressOfPos, jfrThreadId, true);
        javaEventWriter.set(result);

        return result;
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public JfrBuffer getJavaBuffer() {
        VMError.guarantee(traceId.get() > 0, "Thread local JFR data must be initialized");
        JfrBuffer result = javaBuffer.get();
        if (result.isNull()) {
            result = JfrBufferAccess.allocate(WordFactory.unsigned(threadLocalBufferSize));
            javaBuffer.set(result);
        }
        return result;
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.", callerMustBe = true)
    public JfrBuffer getNativeBuffer() {
        VMError.guarantee(traceId.get() > 0, "Thread local JFR data must be initialized");
        JfrBuffer result = nativeBuffer.get();
        if (result.isNull()) {
            result = JfrBufferAccess.allocate(WordFactory.unsigned(threadLocalBufferSize));
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
        if (SubstrateJVM.isRecording() && SubstrateJVM.get().isEnabled(JfrEvents.DataLossEvent)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initialize(data, buffer);

            JfrNativeEventWriter.beginEventWrite(data, false);
            JfrNativeEventWriter.putLong(data, JfrEvents.DataLossEvent.getId());
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
            JfrNativeEventWriter.putLong(data, unflushedSize.rawValue());
            JfrNativeEventWriter.putLong(data, totalDataLoss.rawValue());
            JfrNativeEventWriter.endEventWrite(data, false);
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
