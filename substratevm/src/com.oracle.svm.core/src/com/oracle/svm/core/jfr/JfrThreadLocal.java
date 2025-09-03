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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.JavaMainWrapper;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.jfr.events.ThreadCPULoadEvent;
import com.oracle.svm.core.jfr.events.ThreadEndEvent;
import com.oracle.svm.core.jfr.events.ThreadStartEvent;
import com.oracle.svm.core.sampler.SamplerBuffer;
import com.oracle.svm.core.sampler.SamplerStatistics;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.Target_java_lang_Thread;
import com.oracle.svm.core.thread.ThreadListener;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * This class holds various JFR-specific thread local values.
 *
 * Each thread may use both a Java and a native {@link JfrBuffer}:
 * <ul>
 * <li>The Java buffer is accessed when the JDK class {@code EventWriter} emits a Java-level JFR
 * event. When JFR recording is stopped, we can't free the Java buffer right away because the class
 * {@code EventWriter} is not uninterruptible (i.e., the thread may continue writing data to the
 * buffer, even after JFR recording was already stopped). Therefore, we mark the buffer as retired
 * (see {@link JfrBufferAccess#setRetired}) and either reinstate it at a later point in time or free
 * it when the thread exits.</li>
 * <li>The native buffer is accessed when the VM tries to emit a VM-level JFR event (see
 * {@link JfrNativeEventWriter}). Native buffers are freed right away when JFR recording stops.</li>
 * </ul>
 *
 * It is necessary to have separate buffers as a native JFR event (e.g., a GC or an allocation)
 * could otherwise destroy a Java-level JFR event.
 *
 * Additionally, each thread may store stack trace data in a {@link SamplerBuffer}. This buffer is
 * used for both JFR stack traces and JFR sampling. All methods that access a {@link SamplerBuffer}
 * must be uninterruptible to avoid races with JFR code that is executed at a safepoint (such code
 * may access and modify the buffers of other threads). Sometimes, it is additionally necessary to
 * disable sampling temporarily to avoid that the sampler modifies the buffer unexpectedly.
 */
public class JfrThreadLocal implements ThreadListener {
    /* Event-related thread-locals. */
    private static final FastThreadLocalObject<Target_jdk_jfr_internal_event_EventWriter> javaEventWriter = FastThreadLocalFactory.createObject(Target_jdk_jfr_internal_event_EventWriter.class,
                    "JfrThreadLocal.javaEventWriter");
    private static final FastThreadLocalWord<JfrBuffer> javaBuffer = FastThreadLocalFactory.createWord("JfrThreadLocal.javaBuffer");
    private static final FastThreadLocalWord<JfrBuffer> nativeBuffer = FastThreadLocalFactory.createWord("JfrThreadLocal.nativeBuffer");
    private static final FastThreadLocalWord<UnsignedWord> dataLost = FastThreadLocalFactory.createWord("JfrThreadLocal.dataLost");
    private static final FastThreadLocalInt notified = FastThreadLocalFactory.createInt("JfrThreadLocal.notified");

    /* Stacktrace-related thread-locals. */
    private static final FastThreadLocalWord<SamplerBuffer> samplerBuffer = FastThreadLocalFactory.createWord("JfrThreadLocal.samplerBuffer");
    private static final FastThreadLocalLong missedSamples = FastThreadLocalFactory.createLong("JfrThreadLocal.missedSamples");
    private static final FastThreadLocalLong unparseableStacks = FastThreadLocalFactory.createLong("JfrThreadLocal.unparseableStacks");

    /* Non-thread-local fields. */
    private static final JfrBufferList javaBufferList = new JfrBufferList();
    private static final JfrBufferList nativeBufferList = new JfrBufferList();
    private UnsignedWord threadLocalBufferSize;

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

    public void initialize(UnsignedWord bufferSize) {
        this.threadLocalBufferSize = bufferSize;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getThreadLocalBufferSize() {
        return threadLocalBufferSize;
    }

    public void teardown() {
        getNativeBufferList().teardown();
        getJavaBufferList().teardown();
    }

    @Uninterruptible(reason = "Only uninterruptible code may be executed before the thread is fully started.")
    @Override
    public void beforeThreadStart(IsolateThread isolateThread, Thread javaThread) {
        if (SubstrateJVM.get().isRecording()) {
            SubstrateJVM.getThreadRepo().registerThread(javaThread);
            ThreadCPULoadEvent.initWallclockTime(isolateThread);
            ThreadStartEvent.emit(javaThread);
        }
    }

    @Uninterruptible(reason = "Only uninterruptible code may be executed after Thread.exit.")
    @Override
    public void afterThreadExit(IsolateThread isolateThread, Thread javaThread) {
        if (SubstrateJVM.get().isRecording()) {
            ThreadEndEvent.emit(javaThread);
            ThreadCPULoadEvent.emit(isolateThread);
        }

        /*
         * The thread may still reference a retired Java-level JFR buffer that needs to be freed
         * (i.e., a buffer that couldn't be freed when recording was stopped). So, we always try to
         * free the Java-level JFR buffer, no matter if recording is currently active or not.
         */
        stopRecording(isolateThread, true);
    }

    @Uninterruptible(reason = "Accesses various JFR buffers.")
    public static void stopRecording(IsolateThread isolateThread, boolean freeJavaBuffer) {
        /* Flush event buffers. From this point onwards, no further JFR events may be emitted. */
        JfrBuffer nb = nativeBuffer.get(isolateThread);
        nativeBuffer.set(isolateThread, Word.nullPointer());
        flushToGlobalMemoryAndFreeBuffer(nb);

        JfrBuffer jb = javaBuffer.get(isolateThread);
        javaBuffer.set(isolateThread, Word.nullPointer());
        if (freeJavaBuffer) {
            flushToGlobalMemoryAndFreeBuffer(jb);
        } else {
            flushToGlobalMemoryAndRetireBuffer(jb);
        }

        /* Clear the other event-related thread-locals. */
        javaEventWriter.set(isolateThread, null);
        dataLost.set(isolateThread, Word.unsigned(0));

        /* Clear stacktrace-related thread-locals. */
        SamplerStatistics.singleton().addMissedSamples(getMissedSamples(isolateThread));
        missedSamples.set(isolateThread, 0);
        SamplerStatistics.singleton().addUnparseableSamples(getUnparseableStacks(isolateThread));
        unparseableStacks.set(isolateThread, 0);

        SamplerBuffer buffer = samplerBuffer.get(isolateThread);
        if (buffer.isNonNull()) {
            SubstrateJVM.getSamplerBufferPool().pushFullBuffer(buffer);
            samplerBuffer.set(isolateThread, Word.nullPointer());
        }
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private static void flushToGlobalMemoryAndFreeBuffer(JfrBuffer buffer) {
        if (buffer.isNull()) {
            return;
        }

        /* Retired buffers can be freed right away. */
        JfrBufferNode node = buffer.getNode();
        if (node.isNull()) {
            assert JfrBufferAccess.isRetired(buffer);
            JfrBufferAccess.free(buffer);
            return;
        }

        /* Free the buffer but leave the node alive as it may still be needed. */
        JfrBufferNodeAccess.lockNoTransition(node);
        try {
            flushToGlobalMemory0(buffer, Word.unsigned(0), 0);
            node.setBuffer(Word.nullPointer());
            JfrBufferAccess.free(buffer);
        } finally {
            JfrBufferNodeAccess.unlock(node);
        }
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private static void flushToGlobalMemoryAndRetireBuffer(JfrBuffer buffer) {
        assert VMOperation.isInProgressAtSafepoint();
        if (buffer.isNull()) {
            return;
        }

        JfrBufferNode node = buffer.getNode();
        JfrBufferNodeAccess.lockNoTransition(node);
        try {
            flushToGlobalMemory0(buffer, Word.unsigned(0), 0);
            JfrBufferAccess.setRetired(buffer);
        } finally {
            JfrBufferNodeAccess.unlock(node);
        }
    }

    /**
     * This method excludes/includes a thread from JFR (emitting events and sampling). At the
     * moment, only the current thread may be excluded/included. See GR-44616.
     */
    public static void setExcluded(Thread thread, boolean excluded) {
        if (thread == null || thread != JavaThreads.getCurrentThreadOrNull()) {
            return;
        }
        IsolateThread currentIsolateThread = CurrentIsolate.getCurrentThread();
        Target_java_lang_Thread tjlt = SubstrateUtil.cast(thread, Target_java_lang_Thread.class);
        tjlt.jfrExcluded = excluded;

        if (javaEventWriter.get(currentIsolateThread) != null && !JavaThreads.isVirtual(thread)) {
            javaEventWriter.get(currentIsolateThread).excluded = excluded;
        }
    }

    /**
     * Allocation JFR events can be emitted along the allocation slow path. In some cases, when the
     * slow path may be taken, a {@link Thread} object may not yet be assigned to the current
     * thread, see {@link PlatformThreads#ensureCurrentAssigned(String, ThreadGroup, boolean)} where
     * a {@link Thread} object must be created before it can be assigned to the current thread. This
     * may happen during shutdown in {@link JavaMainWrapper}. Therefore, this method must account
     * for the case where {@link JavaThreads#getCurrentThreadOrNull()} returns null.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isThreadExcluded(Thread thread) {
        if (thread == null) {
            return true;
        }
        Target_java_lang_Thread tjlt = SubstrateUtil.cast(thread, Target_java_lang_Thread.class);
        return tjlt.jfrExcluded;
    }

    public static Target_jdk_jfr_internal_event_EventWriter getEventWriter() {
        Target_jdk_jfr_internal_event_EventWriter eventWriter = javaEventWriter.get();
        /*
         * EventWriter objects cache various thread-specific values. Virtual threads use the
         * EventWriter object of their carrier thread, so we need to update all cached values so
         * that they match the virtual thread.
         */
        if (eventWriter != null && eventWriter.threadID != SubstrateJVM.getCurrentThreadId()) {
            eventWriter.threadID = SubstrateJVM.getCurrentThreadId();
            Target_java_lang_Thread tjlt = SubstrateUtil.cast(Thread.currentThread(), Target_java_lang_Thread.class);
            eventWriter.excluded = tjlt.jfrExcluded;
        }
        return eventWriter;
    }

    /**
     * If a safepoint happens in this method, the state that another thread can see is always
     * sufficiently consistent as the JFR buffer is still empty. So, this method does not need to be
     * uninterruptible.
     */
    public Target_jdk_jfr_internal_event_EventWriter newEventWriter() {
        assert javaEventWriter.get() == null;

        JfrBuffer buffer = reinstateJavaBuffer(getJavaBuffer());
        if (buffer.isNull()) {
            throw new OutOfMemoryError("OOME for thread local buffer");
        }

        Target_jdk_jfr_internal_event_EventWriter result = JfrEventWriterAccess.newEventWriter(buffer, isThreadExcluded(JavaThreads.getCurrentThreadOrNull()));
        javaEventWriter.set(result);
        return result;
    }

    /**
     * If recording is started and stopped multiple times, then we may get a retired buffer instead
     * of a new one. Retired buffers may have an invalid state and must be reset before adding them
     * to the list of thread-local Java buffers.
     */
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private static JfrBuffer reinstateJavaBuffer(JfrBuffer buffer) {
        if (buffer.isNull()) {
            return Word.nullPointer();
        }

        JfrBufferNode node = buffer.getNode();
        if (node.isNull()) {
            assert JfrBufferAccess.isRetired(buffer);
            JfrBufferAccess.reinitialize(buffer);
            JfrBufferAccess.clearRetired(buffer);

            node = javaBufferList.addNode(buffer);
            if (node.isNull()) {
                return Word.nullPointer();
            }
        }

        return buffer;
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public JfrBuffer getExistingJavaBuffer() {
        return javaBuffer.get();
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public JfrBuffer getJavaBuffer() {
        JfrBuffer buffer = javaBuffer.get();
        if (buffer.isNull()) {
            buffer = JfrBufferAccess.allocate(threadLocalBufferSize, JfrBufferType.THREAD_LOCAL_JAVA);
            if (buffer.isNull()) {
                return Word.nullPointer();
            }

            JfrBufferNode node = javaBufferList.addNode(buffer);
            if (node.isNull()) {
                JfrBufferAccess.free(buffer);
                return Word.nullPointer();
            }
            javaBuffer.set(buffer);
        }
        return buffer;
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.", callerMustBe = true)
    public JfrBuffer getNativeBuffer() {
        JfrBuffer buffer = nativeBuffer.get();
        if (buffer.isNull()) {
            buffer = JfrBufferAccess.allocate(threadLocalBufferSize, JfrBufferType.THREAD_LOCAL_NATIVE);
            if (buffer.isNull()) {
                return Word.nullPointer();
            }

            JfrBufferNode node = nativeBufferList.addNode(buffer);
            if (node.isNull()) {
                JfrBufferAccess.free(buffer);
                return Word.nullPointer();
            }
            nativeBuffer.set(buffer);
        }
        return buffer;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isNotified() {
        return notified.get() != 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void notifyEventWriter(IsolateThread thread) {
        notified.set(thread, 1);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void clearNotification() {
        notified.set(0);
    }

    /**
     * May only be called for thread-local buffers and the current thread must own the thread-local
     * buffer.
     */
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public static JfrBuffer flushToGlobalMemory(JfrBuffer buffer, UnsignedWord uncommitted, int requested) {
        assert buffer.isNonNull();
        assert JfrBufferAccess.isThreadLocal(buffer);

        /* Skip retired buffers. */
        JfrBufferNode node = buffer.getNode();
        if (node.isNull()) {
            assert JfrBufferAccess.isRetired(buffer);
            return Word.nullPointer();
        }

        JfrBufferNodeAccess.lockNoTransition(node);
        try {
            return flushToGlobalMemory0(buffer, uncommitted, requested);
        } finally {
            JfrBufferNodeAccess.unlock(node);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static JfrBuffer flushToGlobalMemory0(JfrBuffer buffer, UnsignedWord uncommitted, int requested) {
        assert buffer.isNonNull();
        assert JfrBufferNodeAccess.isLockedByCurrentThread(buffer.getNode());

        UnsignedWord unflushedSize = JfrBufferAccess.getUnflushedSize(buffer);
        if (!SubstrateJVM.getGlobalMemory().write(buffer, unflushedSize, false)) {
            JfrBufferAccess.reinitialize(buffer);
            writeDataLoss(buffer, unflushedSize);
            return Word.nullPointer();
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
        return Word.nullPointer();
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

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long getMissedSamples(IsolateThread thread) {
        return missedSamples.get(thread);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void increaseUnparseableStacks() {
        unparseableStacks.set(getUnparseableStacks() + 1);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getUnparseableStacks() {
        return unparseableStacks.get();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static long getUnparseableStacks(IsolateThread thread) {
        return unparseableStacks.get(thread);
    }
}
