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

import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import com.oracle.svm.core.jfr.JfrBufferNodeLinkedList;

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
    @RawStructure
    public interface JfrBufferNode extends com.oracle.svm.core.jdk.UninterruptibleEntry {
        @RawField
        JfrBuffer getValue();
        @RawField
        void setValue(JfrBuffer value);

        @RawField
        IsolateThread getThread();
        @RawField
        void setThread(IsolateThread thread);

        @RawField
        boolean getAlive();
        @RawField
        void setAlive(boolean alive);
        @RawField
        int getAcquired();

        @RawField
        void setAcquired(int value);

        @org.graalvm.nativeimage.c.struct.RawFieldOffset
        static int offsetOfAcquired() {
            throw VMError.unimplemented(); // replaced
        }
    }

    private static final FastThreadLocalObject<Target_jdk_jfr_internal_EventWriter> javaEventWriter = FastThreadLocalFactory.createObject(Target_jdk_jfr_internal_EventWriter.class,
                    "JfrThreadLocal.javaEventWriter");
    // *** holds a pointer to the buffer that's on the heap
//    private static final FastThreadLocalWord<JfrBuffer> javaBuffer = FastThreadLocalFactory.createWord("JfrThreadLocal.javaBuffer");
    private static final FastThreadLocalWord<JfrBufferNode> javaBufferNode = FastThreadLocalFactory.createWord("JfrThreadLocal.javaBufferNode");
//    private static final FastThreadLocalWord<JfrBuffer> nativeBuffer = FastThreadLocalFactory.createWord("JfrThreadLocal.nativeBuffer");
    private static final FastThreadLocalWord<JfrBufferNode> nativeBufferNode = FastThreadLocalFactory.createWord("JfrThreadLocal.nativeBufferNode");
    private static final FastThreadLocalWord<UnsignedWord> dataLost = FastThreadLocalFactory.createWord("JfrThreadLocal.dataLost");
    private static final FastThreadLocalLong threadId = FastThreadLocalFactory.createLong("JfrThreadLocal.threadId");
    private static final FastThreadLocalLong parentThreadId = FastThreadLocalFactory.createLong("JfrThreadLocal.parentThreadId");

    private long threadLocalBufferSize;
    private static JfrBufferNodeLinkedList javaBufferList;
    private static JfrBufferNodeLinkedList nativeBufferList;
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static JfrBufferNodeLinkedList getNativeBufferList(){
        return nativeBufferList;
    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static JfrBufferNodeLinkedList getJavaBufferList(){
        return javaBufferList;
    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void lockNative(){
        int count = 0;
        while(!JfrBufferNodeLinkedList.acquire(nativeBufferNode.get())){
            count++;
            com.oracle.svm.core.util.VMError.guarantee(count < 100000, "^^^24");
        }
    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void unlockNative(){
        JfrBufferNodeLinkedList.release(nativeBufferNode.get());
    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void lockJava(){
        int count =0;
        while(!JfrBufferNodeLinkedList.acquire(javaBufferNode.get())){
            count++;
            com.oracle.svm.core.util.VMError.guarantee(count < 100000, "^^^25");
        }
    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    public static void unlockJava(){
        JfrBufferNodeLinkedList.release(javaBufferNode.get());
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrThreadLocal() {
    }

    public void initialize(long bufferSize) { // *** at runtime?
        this.threadLocalBufferSize = bufferSize;
        javaBufferList = new JfrBufferNodeLinkedList();
        nativeBufferList = new JfrBufferNodeLinkedList();
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
        /**
         *  *** Why do we need locks here? Avoid simultaneously flushing to disk TLB and promoting it (here).
         *  // *** no blocking on java buffer bc it is null! When guarantee nonNull, it blocks!   doesn't seem like adding the isolateThread param makes a diff
         * Still not sure why the JFR buffer spinlock was blocking
         */

        // Emit ThreadEnd event after thread.run() finishes.
        ThreadEndEvent.emit(isolateThread);
        JfrBufferNode jbn = javaBufferNode.get(isolateThread);
        JfrBufferNode nbn = nativeBufferNode.get(isolateThread);

        if (jbn.isNonNull()) {
            JfrBuffer jb = jbn.getValue();
            assert jb.isNonNull() && jbn.getAlive();
//            while(!JfrBufferNodeLinkedList.acquire(jbn));

//            if (SubstrateJVM.isRecording()) {
//                if (jb.isNonNull()) {
//                    flush(jb, WordFactory.unsigned(0), 0);
//                }
//            }
            jbn.setAlive(false); // TODO: should this be atomic?
//            JfrBufferAccess.free(jb);
//            JfrBufferNodeLinkedList.release(jbn);
        }


        if (nbn.isNonNull()) {
            JfrBuffer nb = nbn.getValue();
            assert nb.isNonNull()  && nbn.getAlive();
//            while(!JfrBufferNodeLinkedList.acquire(nbn));

            // Flush all buffers if necessary.
//            if (SubstrateJVM.isRecording()) {
//                if (nb.isNonNull()) {
//                    flush(nb, WordFactory.unsigned(0), 0);
//                }
//            }
            nbn.setAlive(false);
//            JfrBufferAccess.free(nb);
//            JfrBufferNodeLinkedList.release(nbn);
        }


        // Free and reset all data.
        threadId.set(isolateThread, 0);
        parentThreadId.set(isolateThread, 0);
        dataLost.set(isolateThread, WordFactory.unsigned(0));
        javaEventWriter.set(isolateThread, null);
        javaBufferNode.set(isolateThread, WordFactory.nullPointer());
        nativeBufferNode.set(isolateThread, WordFactory.nullPointer());
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
//        assert javaBuffer.get().isNull();
        assert javaBufferNode.get().isNull();

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
    private static UnsignedWord getHeaderSize() {
        return com.oracle.svm.core.util.UnsignedUtils.roundUp(org.graalvm.nativeimage.c.struct.SizeOf.unsigned(JfrBufferNode.class), WordFactory.unsigned(com.oracle.svm.core.config.ConfigurationValues.getTarget().wordSize));
    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    private static JfrBufferNode allocate(com.oracle.svm.core.jfr.JfrBuffer buffer) {
        JfrBufferNode node = org.graalvm.nativeimage.ImageSingletons.lookup(org.graalvm.nativeimage.impl.UnmanagedMemorySupport.class).malloc(getHeaderSize());
        VMError.guarantee(node.isNonNull());
        node.setValue(buffer);
        node.setAlive(true);
        return node;
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public JfrBuffer getJavaBuffer() {
        VMError.guarantee(threadId.get() > 0, "Thread local JFR data must be initialized");
//        JfrBuffer result = javaBuffer.get();
        JfrBufferNode result = javaBufferNode.get();
        if (result.isNull()) {
            JfrBuffer buffer = JfrBufferAccess.allocate(WordFactory.unsigned(threadLocalBufferSize), JfrBufferType.THREAD_LOCAL_JAVA);
//            javaBuffer.set(result);
            result = allocate(buffer);
            result.setThread(CurrentIsolate.getCurrentThread());
            javaBufferNode.set(result);
            javaBufferList.addNode(result);
        }
        return result.getValue();
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.", callerMustBe = true)
    public JfrBuffer getNativeBuffer() {
        VMError.guarantee(threadId.get() > 0, "Thread local JFR data must be initialized");
//        JfrBuffer result = nativeBuffer.get();
        JfrBufferNode result = nativeBufferNode.get();
        if (result.isNull()) {
            JfrBuffer buffer = JfrBufferAccess.allocate(WordFactory.unsigned(threadLocalBufferSize), JfrBufferType.THREAD_LOCAL_NATIVE);
//            nativeBuffer.set(result);
            result = allocate(buffer);
            nativeBufferNode.set(result);
            nativeBufferList.addNode(result);
        }
        return result.getValue();
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.", callerMustBe = true)
    public static JfrBuffer getJavaBuffer(IsolateThread thread) {
        assert (VMOperation.isInProgressAtSafepoint());
        JfrBufferNode result = javaBufferNode.get(thread);
//        return javaBuffer.get(thread);
        return result.getValue();
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.", callerMustBe = true)
    public static JfrBuffer getNativeBuffer(IsolateThread thread) {
        assert (VMOperation.isInProgressAtSafepoint());
        JfrBufferNode result = nativeBufferNode.get(thread);
//        return nativeBuffer.get(thread);
        return result.getValue();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static void notifyEventWriter(IsolateThread thread) {
        if (javaEventWriter.get(thread) != null) {
            javaEventWriter.get(thread).notified = true;
        }
    }
    @Uninterruptible(reason = "Called from uninterruptible code.")
    private static boolean someNodeLocked() {
        if (javaBufferNode.get().isNull()) {
            return nativeBufferNode.get().getAcquired() == 1;
        } else if (nativeBufferNode.get().isNull()) {
            return javaBufferNode.get().getAcquired() == 1;
        } else {
            return (javaBufferNode.get().getAcquired() + nativeBufferNode.get().getAcquired() > 0); // One of the locks must be held
        }
    }
    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static JfrBuffer flush(JfrBuffer threadLocalBuffer, UnsignedWord uncommitted, int requested) {
//        com.oracle.svm.core.util.VMError.guarantee(someNodeLocked(), "^^^14");//assert someNodeLocked(); // new
        com.oracle.svm.core.util.VMError.guarantee(threadLocalBuffer.isNonNull(), "^^^15");//assert threadLocalBuffer.isNonNull();
        com.oracle.svm.core.util.VMError.guarantee(!com.oracle.svm.core.thread.VMOperation.isInProgressAtSafepoint() , "^^^70");//assert !acquire();

        int count =0;
        while(!JfrBufferAccess.acquire(threadLocalBuffer)); {// new
            count++;
            VMError.guarantee(count < 20000, "^^^60");
        }

        UnsignedWord unflushedSize = JfrBufferAccess.getUnflushedSize(threadLocalBuffer);
        if (unflushedSize.aboveThan(0)) {
            JfrGlobalMemory globalMemory = SubstrateJVM.getGlobalMemory();
            if (!globalMemory.write(threadLocalBuffer, unflushedSize)) {
                JfrBufferAccess.reinitialize(threadLocalBuffer);
                VMError.guarantee(false, "^^^71");
                writeDataLoss(threadLocalBuffer, unflushedSize);
                JfrBufferAccess.release(threadLocalBuffer);// new
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
        if (threadLocalBuffer.getSize().aboveOrEqual(uncommitted.add(requested))) { // *** do we have enough space now?
            JfrBufferAccess.release(threadLocalBuffer);// new
            return threadLocalBuffer;
        }
        JfrBufferAccess.release(threadLocalBuffer);// new
        VMError.guarantee(false, "^^^72");
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
