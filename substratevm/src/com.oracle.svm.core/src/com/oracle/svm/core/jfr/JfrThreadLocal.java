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

import org.graalvm.compiler.api.replacements.Fold;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.jfr.events.ThreadEndEvent;
import com.oracle.svm.core.jfr.events.ThreadStartEvent;
import com.oracle.svm.core.sampler.SamplerBuffer;
import com.oracle.svm.core.sampler.SamplerSampleWriterData;
import com.oracle.svm.core.thread.ThreadListener;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.thread.Target_java_lang_Thread;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.jfr.JfrBufferNodeLinkedList.JfrBufferNode;

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
 * could otherwise destroy a Java-level JFR event. All methods that access a {@link JfrBuffer} must
 * be uninterruptible to avoid races with JFR code that is executed at a safepoint (such code may
 * access and modify the buffers of other threads).
 *
 * Additionally, each thread may store stack trace data in a {@link SamplerBuffer}. This buffer is
 * used for both JFR stack traces and JFR sampling. All methods that access a {@link SamplerBuffer}
 * must be uninterruptible to avoid races with JFR code that is executed at a safepoint (such code
 * may access and modify the buffers of other threads). Sometimes, it is additionally necessary to
 * disable sampling temporarily to avoid that the sampler modifies the buffer unexpectedly.
 */
public class JfrThreadLocal implements ThreadListener {
    /* Event-related thread-locals. */
    private static final FastThreadLocalObject<Target_jdk_jfr_internal_EventWriter> javaEventWriter = FastThreadLocalFactory.createObject(Target_jdk_jfr_internal_EventWriter.class,
                    "JfrThreadLocal.javaEventWriter");
    private static final FastThreadLocalWord<JfrBufferNode> javaBufferNode = FastThreadLocalFactory.createWord("JfrThreadLocal.javaBufferNode");
    private static final FastThreadLocalWord<JfrBufferNode> nativeBufferNode = FastThreadLocalFactory.createWord("JfrThreadLocal.nativeBufferNode");
    private static final FastThreadLocalWord<UnsignedWord> dataLost = FastThreadLocalFactory.createWord("JfrThreadLocal.dataLost");

    /* Stacktrace-related thread-locals. */
    private static final FastThreadLocalWord<SamplerBuffer> samplerBuffer = FastThreadLocalFactory.createWord("JfrThreadLocal.samplerBuffer");
    private static final FastThreadLocalLong missedSamples = FastThreadLocalFactory.createLong("JfrThreadLocal.missedSamples");
    private static final FastThreadLocalLong unparseableStacks = FastThreadLocalFactory.createLong("JfrThreadLocal.unparseableStacks");
    private static final FastThreadLocalWord<SamplerSampleWriterData> samplerWriterData = FastThreadLocalFactory.createWord("JfrThreadLocal.samplerWriterData");
    private static final JfrBufferNodeLinkedList javaBufferList = new JfrBufferNodeLinkedList();
    private static final JfrBufferNodeLinkedList nativeBufferList = new JfrBufferNodeLinkedList();
    private long threadLocalBufferSize;

    @Fold
    public static JfrBufferNodeLinkedList getNativeBufferList() {
        return nativeBufferList;
    }

    @Fold
    public static JfrBufferNodeLinkedList getJavaBufferList() {
        return javaBufferList;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrThreadLocal() {
    }

    public void initialize(long bufferSize) {
        this.threadLocalBufferSize = bufferSize;
    }

    @Uninterruptible(reason = "Only uninterruptible code may be executed before the thread is fully started.")
    @Override

    public void beforeThreadStart(IsolateThread isolateThread, Thread javaThread) {
        if (SubstrateJVM.get().isRecording()) {
            SubstrateJVM.getThreadRepo().registerThread(javaThread);
            ThreadStartEvent.emit(javaThread);
        }
    }

    @Uninterruptible(reason = "Only uninterruptible code may be executed after Thread.exit.")
    @Override
    public void afterThreadExit(IsolateThread isolateThread, Thread javaThread) {
        if (SubstrateJVM.get().isRecording()) {
            ThreadEndEvent.emit(javaThread);
            stopRecording(isolateThread);
        }
    }

    @Uninterruptible(reason = "Accesses various JFR buffers.")
    public static void stopRecording(IsolateThread isolateThread) {
        /* Flush event buffers. From this point onwards, no further JFR events may be emitted. */

        JfrBufferNode jbn = javaBufferNode.get(isolateThread);
        JfrBufferNode nbn = nativeBufferNode.get(isolateThread);

        if (jbn.isNonNull()) {
            JfrBuffer jb = jbn.getValue();
            assert jb.isNonNull() && jbn.getAlive();
            flush(jb, WordFactory.unsigned(0), 0);
            jbn.setAlive(false);

        }
        if (nbn.isNonNull()) {
            JfrBuffer nb = nbn.getValue();
            assert nb.isNonNull() && nbn.getAlive();
            flush(nb, WordFactory.unsigned(0), 0);
            nbn.setAlive(false);
        }

        /* Clear event-related thread-locals. */
        dataLost.set(isolateThread, WordFactory.unsigned(0));
        javaEventWriter.set(isolateThread, null);
        javaBufferNode.set(isolateThread, WordFactory.nullPointer());
        nativeBufferNode.set(isolateThread, WordFactory.nullPointer());

        /* Clear stacktrace-related thread-locals. */
        missedSamples.set(isolateThread, 0);
        unparseableStacks.set(isolateThread, 0);
        assert samplerWriterData.get(isolateThread).isNull();

        SamplerBuffer buffer = samplerBuffer.get(isolateThread);
        if (buffer.isNonNull()) {
            SubstrateJVM.getSamplerBufferPool().pushFullBuffer(buffer);
            samplerBuffer.set(isolateThread, WordFactory.nullPointer());
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getThreadLocalBufferSize() {
        return threadLocalBufferSize;
    }

    public Target_jdk_jfr_internal_EventWriter getEventWriter() {
        return javaEventWriter.get();
    }

    /**
     * If a safepoint happens in this method, the state that another thread can see is always
     * sufficiently consistent as the JFR buffer is still empty. So, this method does not need to be
     * uninterruptible.
     */
    public Target_jdk_jfr_internal_EventWriter newEventWriter() {
        assert javaEventWriter.get() == null;
        assert javaBufferNode.get().isNull();

        JfrBuffer buffer = getJavaBuffer();
        if (buffer.isNull()) {
            throw new OutOfMemoryError("OOME for thread local buffer");
        }

        assert JfrBufferAccess.isEmpty(buffer) : "a fresh JFR buffer must be empty";
        long startPos = buffer.getCommittedPos().rawValue();
        long maxPos = JfrBufferAccess.getDataEnd(buffer).rawValue();
        long addressOfPos = JfrBufferAccess.getAddressOfCommittedPos(buffer).rawValue();
        long jfrThreadId = SubstrateJVM.getCurrentThreadId();
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
        JfrBufferNode result = javaBufferNode.get();
        if (result.isNull()) {
            JfrBuffer buffer = JfrBufferAccess.allocate(WordFactory.unsigned(threadLocalBufferSize), JfrBufferType.THREAD_LOCAL_JAVA);
            if (buffer.isNull()) {
                return WordFactory.nullPointer();
            }
            result = javaBufferList.addNode(buffer, CurrentIsolate.getCurrentThread());
            if (result.isNull()) {
                JfrBufferAccess.free(buffer);
                return WordFactory.nullPointer();
            }
            javaBufferNode.set(result);
        }
        return result.getValue();
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.", callerMustBe = true)
    public JfrBuffer getNativeBuffer() {
        JfrBufferNode result = nativeBufferNode.get();
        if (result.isNull()) {
            JfrBuffer buffer = JfrBufferAccess.allocate(WordFactory.unsigned(threadLocalBufferSize), JfrBufferType.THREAD_LOCAL_NATIVE);
            if (buffer.isNull()) {
                return WordFactory.nullPointer();
            }
            result = nativeBufferList.addNode(buffer, CurrentIsolate.getCurrentThread());
            if (result.isNull()) {
                JfrBufferAccess.free(buffer);
                return WordFactory.nullPointer();
            }
            nativeBufferNode.set(result);
        }
        return result.getValue();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void notifyEventWriter(IsolateThread thread) {
        if (javaEventWriter.get(thread) != null) {
            javaEventWriter.get(thread).notified = true;
        }
    }

    /**
     * This method only copies the JFR buffer's unflushed data to the global buffers. This can be
     * used outside a safepoint from the flushing thread while other threads continue writing
     * events.
     */
    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static boolean flushNoReset(JfrBuffer threadLocalBuffer) {
        JfrBufferAccess.tryLock(threadLocalBuffer, Integer.MAX_VALUE);
        try {
            UnsignedWord unflushedSize = JfrBufferAccess.getUnflushedSize(threadLocalBuffer);
            if (unflushedSize.aboveThan(0)) {
                JfrGlobalMemory globalMemory = SubstrateJVM.getGlobalMemory();
                // flushedPos is increased in JfrGlobalMemory.write
                if (!globalMemory.write(threadLocalBuffer, unflushedSize, true)) {
                    return false;
                }
            }
            return true;
        } finally {
            JfrBufferAccess.unlock(threadLocalBuffer);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static JfrBuffer flush(JfrBuffer threadLocalBuffer, UnsignedWord uncommitted, int requested) {
        assert threadLocalBuffer.isNonNull();

        // Needed for race between streaming flush and promotion
        JfrBufferAccess.tryLock(threadLocalBuffer, Integer.MAX_VALUE);
        try {
            UnsignedWord unflushedSize = JfrBufferAccess.getUnflushedSize(threadLocalBuffer);
            if (unflushedSize.aboveThan(0)) {
                JfrGlobalMemory globalMemory = SubstrateJVM.getGlobalMemory();
                if (!globalMemory.write(threadLocalBuffer, unflushedSize, false)) {
                    JfrBufferAccess.reinitialize(threadLocalBuffer);
                    writeDataLoss(threadLocalBuffer, unflushedSize);
                    return WordFactory.nullPointer();
                }
            }

            if (uncommitted.aboveThan(0)) {
                /* Copy all uncommitted memory to the start of the thread local buffer. */
                assert JfrBufferAccess.getDataStart(threadLocalBuffer).add(uncommitted).belowOrEqual(JfrBufferAccess.getDataEnd(threadLocalBuffer));
                UnmanagedMemoryUtil.copy(threadLocalBuffer.getCommittedPos(), JfrBufferAccess.getDataStart(threadLocalBuffer), uncommitted);
            }
            JfrBufferAccess.reinitialize(threadLocalBuffer);
            assert JfrBufferAccess.getUnflushedSize(threadLocalBuffer).equal(0);
            if (threadLocalBuffer.getSize().aboveOrEqual(uncommitted.add(requested))) {
                return threadLocalBuffer;
            }

            return WordFactory.nullPointer();
        } finally {
            JfrBufferAccess.unlock(threadLocalBuffer);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void writeDataLoss(JfrBuffer buffer, UnsignedWord unflushedSize) {
        assert buffer.isNonNull();
        assert unflushedSize.aboveThan(0);
        UnsignedWord totalDataLoss = increaseDataLost(unflushedSize);
        if (JfrEvent.DataLoss.shouldEmit()) {
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

    public void teardown() {
        JfrBufferNodeLinkedList nativeBuffers = getNativeBufferList();
        if (nativeBuffers != null) {
            nativeBuffers.teardown();
        }
        JfrBufferNodeLinkedList javaBuffers = getJavaBufferList();
        if (javaBuffers != null) {
            javaBuffers.teardown();
        }
    }

    public void exclude(Thread thread) {
        if (!thread.equals(Thread.currentThread())) {
            return;
        }
        IsolateThread currentIsolateThread = CurrentIsolate.getCurrentThread();
        Target_java_lang_Thread tjlt = SubstrateUtil.cast(thread, Target_java_lang_Thread.class);
        tjlt.jfrExcluded = true;

        if (javaEventWriter.get(currentIsolateThread) != null && !JavaThreads.isVirtual(thread)) {
            javaEventWriter.get(currentIsolateThread).excluded = true;
        }
    }

    public void include(Thread thread) {
        if (!thread.equals(Thread.currentThread())) {
            return;
        }

        IsolateThread currentIsolateThread = CurrentIsolate.getCurrentThread();
        Target_java_lang_Thread tjlt = SubstrateUtil.cast(thread, Target_java_lang_Thread.class);
        tjlt.jfrExcluded = false;

        if (javaEventWriter.get(currentIsolateThread) != null && !JavaThreads.isVirtual(thread)) {
            javaEventWriter.get(currentIsolateThread).excluded = false;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.")
    public boolean isCurrentThreadExcluded() {
        Target_java_lang_Thread tjlt = SubstrateUtil.cast(Thread.currentThread(), Target_java_lang_Thread.class);
        return tjlt.jfrExcluded;
    }

    @Uninterruptible(reason = "Accesses a sampler buffer.", callerMustBe = true)
    public static SamplerBuffer getSamplerBuffer() {
        return getSamplerBuffer(CurrentIsolate.getCurrentThread());
    }

    @Uninterruptible(reason = "Accesses a sampler buffer.", callerMustBe = true)
    public static SamplerBuffer getSamplerBuffer(IsolateThread thread) {
        assert CurrentIsolate.getCurrentThread() == thread || VMOperation.isInProgressAtSafepoint();
        return samplerBuffer.get(thread);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setSamplerBuffer(SamplerBuffer buffer) {
        samplerBuffer.set(buffer);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void increaseMissedSamples() {
        missedSamples.set(getMissedSamples() + 1);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getMissedSamples() {
        return missedSamples.get();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void increaseUnparseableStacks() {
        unparseableStacks.set(getUnparseableStacks() + 1);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getUnparseableStacks() {
        return unparseableStacks.get();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void setSamplerWriterData(SamplerSampleWriterData data) {
        assert samplerWriterData.get().isNull() || data.isNull();
        samplerWriterData.set(data);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static SamplerSampleWriterData getSamplerWriterData() {
        return samplerWriterData.get();
    }
}
