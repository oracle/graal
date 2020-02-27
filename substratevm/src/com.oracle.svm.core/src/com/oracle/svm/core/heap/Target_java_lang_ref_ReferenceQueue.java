/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Inject;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.annotate.UnknownClass;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.ThreadStatus;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.TimeUtils;

@UnknownClass
@TargetClass(java.lang.ref.ReferenceQueue.class)
@Substitute
public final class Target_java_lang_ref_ReferenceQueue<T> {
    /**
     * This mutex is used by the GC and the application. The application must hold this mutex only
     * in uninterruptible code to prevent the case that a safepoint can be initiated while the
     * application holds the mutex. Otherwise, we risk deadlocks between the application and the GC.
     */
    private static final VMMutex REF_MUTEX = new VMMutex();
    private static final VMCondition REF_CONDITION = new VMCondition(REF_MUTEX);

    private static final InterruptedException preallocatedInterruptedException = new InterruptedException();

    @Inject @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClass = UninterruptibleUtils.AtomicReference.class) //
    private final UninterruptibleUtils.AtomicReference<Target_java_lang_ref_Reference<? extends T>> queueHead;

    @Substitute
    public Target_java_lang_ref_ReferenceQueue() {
        queueHead = new UninterruptibleUtils.AtomicReference<>(null);
    }

    @Substitute
    public Target_java_lang_ref_Reference<? extends T> poll() {
        return doPoll();
    }

    @Substitute
    public Target_java_lang_ref_Reference<? extends T> remove() throws InterruptedException {
        return doRemove(0L);
    }

    @Substitute
    public Target_java_lang_ref_Reference<? extends T> remove(long timeoutMillis) throws InterruptedException {
        return doRemove(timeoutMillis);
    }

    @Substitute
    public boolean enqueue(Target_java_lang_ref_Reference<? extends T> ref) {
        /*
         * Atomically clear the queue so it can not be enqueued again, to avoid A-B-A problems. Only
         * the winner of the race to clear the list field will push the Reference to the list.
         */
        Target_java_lang_ref_ReferenceQueue<?> clearedQueue = ReferenceInternals.clearFutureQueue(ref);
        if (clearedQueue == null) {
            return false;
        }
        assert clearedQueue == this : "Trying to enqueue in the wrong queue";
        assert !ReferenceInternals.testIsEnqueued(ref) : "Trying to enqueue a Reference that is already on a queue";
        Target_java_lang_ref_Reference<? extends T> head;
        do {
            head = getHead();
            ReferenceInternals.setQueueNext(ref, head);
        } while (!queueHead.compareAndSet(head, ref));
        return true;
    }

    public boolean isEmpty() {
        return getHead() == null;
    }

    @Uninterruptible(reason = "Queue is modified during collections.")
    public Target_java_lang_ref_Reference<? extends T> doPoll() {
        while (true) {
            Target_java_lang_ref_Reference<? extends T> head = getHead();
            if (head == null) {
                return null;
            }
            @SuppressWarnings("unchecked")
            Target_java_lang_ref_Reference<? extends T> next = (Target_java_lang_ref_Reference<? extends T>) ReferenceInternals.getQueueNext(head);
            if (queueHead.compareAndSet(head, next)) {
                clearQueuedState(head);
                return head;
            }
        }
    }

    public Target_java_lang_ref_Reference<? extends T> doRemove(long timeoutMillis) throws IllegalArgumentException, InterruptedException {
        VMOperationControl.guaranteeOkayToBlock("ReferenceQueue.remove can block");
        if (timeoutMillis < 0L) {
            throw new IllegalArgumentException("Negative timeout value");
        }
        if (!SubstrateOptions.MultiThreaded.getValue()) {
            return doPoll(); // must not block when single-threaded
        }
        long remainingNanos = TimeUtils.millisToNanos(timeoutMillis);
        while (true) {
            Target_java_lang_ref_Reference<? extends T> result = doPoll();
            if (result != null) {
                return result;
            }

            /*-
             * TODO: We have a potential race condition here, see GR-17276:
             * - Thread A executes pop(), the queue is empty, null is returned.
             * - Thread A gets blocked by a safepoint between pop() and the VMCondition.block()
             * that is called somewhere in await().
             * - The GC pushes elements to the queue and sends a notification that new elements
             * were queued
             * - Thread A executes VMCondition.block() and blocks even though new elements were
             * queued in the meanwhile.
             */
            if (Thread.interrupted()) {
                throw preallocatedInterruptedException;
            }
            /*-
             * TODO: We have a potential race condition here, see GR-17276:
             * - Thread A executes Thread.interrupted() - this returns false.
             * - Thread B interrupts thread A. This sets the interrupted flag and wakes up all
             * threads that are currently waiting for enqueued references.
             * - Thread A executes await() and gets blocked.
             */

            if (timeoutMillis == 0) {
                await();
            } else {
                remainingNanos = await(remainingNanos);
                if (remainingNanos <= 0) {
                    return null;
                }
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private Target_java_lang_ref_Reference<? extends T> getHead() {
        return queueHead.get();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected static void clearQueuedState(Target_java_lang_ref_Reference<?> ref) {
        ReferenceInternals.clearQueueNext(ref);
    }

    private static void await() {
        final Thread thread = Thread.currentThread();
        final int oldStatus = JavaThreads.getThreadStatus(thread);
        JavaThreads.setThreadStatus(thread, ThreadStatus.PARKED);
        try {
            awaitWithTransition();
        } finally {
            JavaThreads.setThreadStatus(thread, oldStatus);
        }
    }

    private static long await(long waitNanos) {
        final Thread thread = Thread.currentThread();
        final int oldStatus = JavaThreads.getThreadStatus(thread);
        JavaThreads.setThreadStatus(thread, ThreadStatus.PARKED_TIMED);
        try {
            return awaitWithTransition(waitNanos);
        } finally {
            JavaThreads.setThreadStatus(thread, oldStatus);
        }
    }

    public static void signalWaiters() {
        VMOperation.guaranteeGCInProgress("Must only be called during garbage collection");
        REF_MUTEX.lock();
        try {
            REF_CONDITION.broadcast();
        } finally {
            REF_MUTEX.unlock();
        }
    }

    @Uninterruptible(reason = "No GC is allowed while holding the lock - otherwise the application and the GC could deadlock.")
    public static void interruptWaiters() {
        REF_MUTEX.lockNoTransition();
        try {
            REF_CONDITION.broadcast();
        } finally {
            REF_MUTEX.unlock();
        }
    }

    /**
     * Manually transitions to native at the start of the method and back at the end of the method.
     * The transition to native will set up a Java frame anchor. Potential garbage collections will
     * not see (or update) any object references until returning from native, so code must only
     * reference objects that will not be collected or moved. The transitions can block if a
     * safepoint is in progress.
     */
    @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode")
    private static void awaitWithTransition() {
        CFunctionPrologueNode.cFunctionPrologue(VMThreads.StatusSupport.STATUS_IN_NATIVE);
        awaitInNative();
        CFunctionEpilogueNode.cFunctionEpilogue(VMThreads.StatusSupport.STATUS_IN_NATIVE);
    }

    @Uninterruptible(reason = "Must not stop while in native.")
    @NeverInline("Provide a return address for the Java frame anchor.")
    private static void awaitInNative() {
        REF_MUTEX.lockNoTransition();
        try {
            REF_CONDITION.blockNoTransition();
        } finally {
            REF_MUTEX.unlock();
        }
    }

    @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode")
    private static long awaitWithTransition(long waitNanos) {
        CFunctionPrologueNode.cFunctionPrologue(VMThreads.StatusSupport.STATUS_IN_NATIVE);
        final long result = awaitInNative(waitNanos);
        CFunctionEpilogueNode.cFunctionEpilogue(VMThreads.StatusSupport.STATUS_IN_NATIVE);
        return result;
    }

    @Uninterruptible(reason = "Must not stop while in native.")
    @NeverInline("Provide a return address for the Java frame anchor.")
    private static long awaitInNative(long waitNanos) {
        REF_MUTEX.lockNoTransition();
        try {
            return REF_CONDITION.blockNoTransition(waitNanos);
        } finally {
            REF_MUTEX.unlock();
        }
    }
}
