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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.jfr.events.ThreadEndEvent;
import com.oracle.svm.core.jfr.events.ThreadStartEvent;
import com.oracle.svm.core.sampler.SamplerBuffer;
import com.oracle.svm.core.sampler.SamplerSampleWriterData;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.Target_java_lang_Thread;
import com.oracle.svm.core.thread.ThreadListener;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;

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

    /* Non-thread-local fields. */
    private static final JfrBufferList javaBufferList = new JfrBufferList();
    private static final JfrBufferList nativeBufferList = new JfrBufferList();
    private long threadLocalBufferSize;

    @Fold
    public static JfrBufferList getNativeBufferList() {
        return nativeBufferList;
    }

    @Fold
    public static JfrBufferList getJavaBufferList() {
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
        JfrBufferNode javaNode = javaBufferNode.get(isolateThread);
        javaBufferNode.set(isolateThread, WordFactory.nullPointer());

        JfrBufferNode nativeNode = nativeBufferNode.get(isolateThread);
        nativeBufferNode.set(isolateThread, WordFactory.nullPointer());

        flushAndFreeBuffer(javaNode);
        flushAndFreeBuffer(nativeNode);

        /* Clear the other event-related thread-locals. */
        dataLost.set(isolateThread, WordFactory.unsigned(0));
        javaEventWriter.set(isolateThread, null);

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

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void flushAndFreeBuffer(JfrBufferNode node) {
        if (node.isNull()) {
            return;
        }

        /* Free the JFRBuffer but leave the node alive as it still needed. */
        JfrBufferNodeAccess.lock(node);
        try {
            JfrBuffer buffer = node.getBuffer();
            node.setBuffer(WordFactory.nullPointer());

            flush0(buffer, WordFactory.unsigned(0), 0);
            JfrBufferAccess.free(buffer);
        } finally {
            JfrBufferNodeAccess.unlock(node);
        }
    }

    public void teardown() {
        getNativeBufferList().teardown();
        getJavaBufferList().teardown();
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
        JfrBufferNode node = javaBufferNode.get();
        if (node.isNull()) {
            JfrBuffer buffer = JfrBufferAccess.allocate(WordFactory.unsigned(threadLocalBufferSize), JfrBufferType.THREAD_LOCAL_JAVA);
            if (buffer.isNull()) {
                return WordFactory.nullPointer();
            }

            node = javaBufferList.addNode(buffer);
            if (node.isNull()) {
                JfrBufferAccess.free(buffer);
                return WordFactory.nullPointer();
            }
            javaBufferNode.set(node);
        }
        return node.getBuffer();
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.", callerMustBe = true)
    public JfrBuffer getNativeBuffer() {
        JfrBufferNode node = nativeBufferNode.get();
        if (node.isNull()) {
            JfrBuffer buffer = JfrBufferAccess.allocate(WordFactory.unsigned(threadLocalBufferSize), JfrBufferType.THREAD_LOCAL_NATIVE);
            if (buffer.isNull()) {
                return WordFactory.nullPointer();
            }

            node = nativeBufferList.addNode(buffer);
            if (node.isNull()) {
                JfrBufferAccess.free(buffer);
                return WordFactory.nullPointer();
            }
            nativeBufferNode.set(node);
        }
        return node.getBuffer();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void notifyEventWriter(IsolateThread thread) {
        if (javaEventWriter.get(thread) != null) {
            javaEventWriter.get(thread).notified = true;
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static JfrBuffer flush(JfrBuffer buffer, UnsignedWord uncommitted, int requested) {
        assert buffer.isNonNull();
        assert JfrBufferAccess.isThreadLocal(buffer);

        /* Acquire the buffer because a streaming flush could be in progress. */
        JfrBufferNode node = buffer.getNode();
        JfrBufferNodeAccess.lock(node);
        try {
            return flush0(buffer, uncommitted, requested);
        } finally {
            JfrBufferNodeAccess.unlock(node);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static JfrBuffer flush0(JfrBuffer buffer, UnsignedWord uncommitted, int requested) {
        UnsignedWord unflushedSize = JfrBufferAccess.getUnflushedSize(buffer);
        if (unflushedSize.aboveThan(0)) {
            if (!SubstrateJVM.getGlobalMemory().write(buffer, unflushedSize, false)) {
                JfrBufferAccess.reinitialize(buffer);
                writeDataLoss(buffer, unflushedSize);
                return WordFactory.nullPointer();
            }
        }

        if (uncommitted.aboveThan(0)) {
            /* Copy all uncommitted memory to the start of the thread local buffer. */
            assert JfrBufferAccess.getDataStart(buffer).add(uncommitted).belowOrEqual(JfrBufferAccess.getDataEnd(buffer));
            UnmanagedMemoryUtil.copy(buffer.getCommittedPos(), JfrBufferAccess.getDataStart(buffer), uncommitted);
        }
        JfrBufferAccess.reinitialize(buffer);
        assert JfrBufferAccess.getUnflushedSize(buffer).equal(0);
        if (buffer.getSize().aboveOrEqual(uncommitted.add(requested))) {
            return buffer;
        }
        return WordFactory.nullPointer();
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

    /**
     * This method excludes/includes a thread from JFR (emitting events and sampling). Unlike in
     * hotspot, only the current thread may be excluded/included. TODO: possibly modify this method
     * to match hotspot behaviour.
     */
    public void setExcluded(Thread thread, boolean excluded) {
        if (!thread.equals(Thread.currentThread())) {
            return;
        }
        IsolateThread currentIsolateThread = CurrentIsolate.getCurrentThread();
        Target_java_lang_Thread tjlt = SubstrateUtil.cast(thread, Target_java_lang_Thread.class);
        tjlt.jfrExcluded = excluded;

        if (javaEventWriter.get(currentIsolateThread) != null && !JavaThreads.isVirtual(thread)) {
            javaEventWriter.get(currentIsolateThread).excluded = excluded;
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
