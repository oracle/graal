/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.svm.core.annotate.NeverInline;
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
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.TimeUtils;

/** A feeble version of java.lang.ref.ReferenceQueue. */
@UnknownClass
public final class FeebleReferenceList<T> {

    /** The head of the list of FeebleReference<T>. */
    private final UninterruptibleUtils.AtomicReference<FeebleReference<? extends T>> head;

    /**
     * This mutex is used by the GC and the application. The application must hold this mutex only
     * in uninterruptible code to prevent the case that a safepoint can be initiated while the
     * application holds the mutex. Otherwise, we risk deadlocks between the application and the GC.
     */
    private static final VMMutex REF_MUTEX = new VMMutex();
    private static final VMCondition REF_CONDITION = new VMCondition(REF_MUTEX);

    /** A pre-allocated InterruptedException. */
    private static final InterruptedException preallocatedInterruptedException = new InterruptedException();

    /** A factory. */
    public static <T> FeebleReferenceList<T> factory() {
        return new FeebleReferenceList<>();
    }

    /** Constructor for subclasses. */
    private FeebleReferenceList() {
        head = new UninterruptibleUtils.AtomicReference<>(null);
    }

    /** Whether the list is empty at the time of the call. */
    public boolean isEmpty() {
        return getHead() == null;
    }

    /**
     * Push FeebleReference on to this list if it is not already on a list. Each reference can only
     * be enqueued once. Calls to this method may race with the collector to enqueue a reference.
     * One call to this method may also race with other threaded trying to enqueue the same
     * reference, or trying to enqueue other references on the same queue.
     * <p>
     * The race to enqueue a reference is resolved by having only the thread that can clear the list
     * slot enqueue the reference on the queue.
     * <p>
     * The race to enqueue other references on the same queue is resolved by the compare-and-set of
     * the sampled head.
     */
    public boolean push(FeebleReference<?> fr) {
        /*
         * Clear the list field of the FeebleReference so it can not be pushed again, to avoiding
         * A-B-A problems. Only the winner of the race to clear the list field will push the
         * FeebleReference to the list.
         */
        final FeebleReferenceList<?> clearedList = fr.clearList();
        if (clearedList != null) {
            /* I won the race. */
            assert clearedList == this : "Pushing to the wrong list.";
            assert !fr.isEnlisted() : "Pushing a FeebleReference that is already on a list.";
            for (; /* return */;) {
                final FeebleReference<? extends T> sampleHead = getHead();
                fr.listPrepend(sampleHead);
                if (compareAndSetHead(sampleHead, FeebleReference.uncheckedNarrow(fr))) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * List manipulation methods.
     */

    /**
     * Pop a FeebleReference off of this list. This method may be called by multiple threads, and so
     * has to worry about races. So as to not worry about intervening pushes by the collector, this
     * method is uninterruptible.
     */
    @Uninterruptible(reason = "List is pushed to during collections.")
    public FeebleReference<? extends T> pop() {
        final FeebleReference<? extends T> result;
        for (; /* break */;) {
            FeebleReference<? extends T> sampleHead = getHead();
            if (sampleHead == null) {
                result = null;
                break;
            }
            FeebleReference<? extends T> sampleNext = sampleHead.listGetNext();
            if (compareAndSetHead(sampleHead, sampleNext)) {
                result = sampleHead;
                clean(result);
                break;
            }
        }
        return result;
    }

    /**
     * Removes the next reference object in this queue, blocking until either one becomes available
     * or the given timeout period expires.
     */
    public FeebleReference<? extends T> remove(long timeoutMillis) throws IllegalArgumentException, InterruptedException {
        /* Sanity check argument. */
        if (timeoutMillis < 0L) {
            throw new IllegalArgumentException("FeebleReferenceList.remove: Negative timeout.");
        }
        FeebleReference<? extends T> result;
        if (SubstrateOptions.MultiThreaded.getValue()) {
            /*
             * This method blocks, so it can not run inside a VMOperation. Also, trying to grab the
             * VMThreads mutex inside a VMOperation will try to re-lock the mutex, which would
             * deadlock, if I am lucky.
             */
            VMOperation.guaranteeNotInProgress("Calling FeebleReferenceList.remove inside a VMOperation would block.");
            /* The multi-threaded version either returns quickly or blocks until notified. */
            final long timeoutNanos = TimeUtils.millisToNanos(timeoutMillis);
            result = popWithBlocking(timeoutNanos);
        } else {
            /* The single-threaded version can not wait for notification. */
            result = pop();
        }
        return result;
    }

    private FeebleReference<? extends T> popWithBlocking(long timeoutNanos) throws InterruptedException {
        long remainingNanos = timeoutNanos;
        for (; /* break */;) {

            /* Check if we can pop an element from the queue and return it. */
            FeebleReference<? extends T> result = pop();
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
             * threads that are currently waiting for feeble references.
             * - Thread A executes await() and gets blocked.
             */

            /* Either wait forever or for however long is requested. */
            if (timeoutNanos == 0) {
                await();
            } else {
                remainingNanos = await(remainingNanos);
                if (remainingNanos <= 0) {
                    /* Timeout. */
                    return null;
                }
            }
        }
    }

    public FeebleReference<? extends T> remove() throws InterruptedException {
        return remove(0L);
    }

    /*
     * Manipulations of head.
     */

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private FeebleReference<? extends T> getHead() {
        return head.get();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean compareAndSetHead(FeebleReference<? extends T> expect, FeebleReference<? extends T> update) {
        return head.compareAndSet(expect, update);
    }

    /** Await with thread status change. */
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

    /** Await(long) with thread status change. */
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

    /**
     * I manually transition to native at the start of the method and manually transition from
     * native at the end of the method. The transition to native will set up a Java frame anchor for
     * the next warmer method on the call stack. That means that the collector will not see (or
     * update) any object references in the stack called by this method until I return from native.
     * From here until I become uninterruptible I must only reference objects that are in the native
     * image heap, or ones allocated on the stack since those will not be collected or moved. The
     * transition from native in {@link CFunctionEpilogueNode#cFunctionEpilogue(int)} might block if
     * a safepoint is in progress when I reach that call.
     * <p>
     * This could be
     *
     * <pre>
     * cFunctionPrologue();
     * try {
     *     awaitInNative();
     * } finally {
     *     cFunctionEpilogue();
     * }
     * </pre>
     *
     * but there is some concern about how much the <code>finally</code> will distort the code
     * shape. And there is nothing I can do if the {@link #awaitInNative()} fails in some obscure
     * way. Similarly, I could add asserts about the thread state on the way out, but there is not
     * much I can do if things go wrong.
     */
    @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode")
    private static void awaitWithTransition() {
        CFunctionPrologueNode.cFunctionPrologue(StatusSupport.STATUS_IN_NATIVE);
        awaitInNative();
        CFunctionEpilogueNode.cFunctionEpilogue(StatusSupport.STATUS_IN_NATIVE);
    }

    @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode")
    private static long awaitWithTransition(long waitNanos) {
        CFunctionPrologueNode.cFunctionPrologue(StatusSupport.STATUS_IN_NATIVE);
        final long result = awaitInNative(waitNanos);
        CFunctionEpilogueNode.cFunctionEpilogue(StatusSupport.STATUS_IN_NATIVE);
        return result;
    }

    /**
     * Wait until a signal is available, in native. Once I get the lock I will run until
     * {@link VMCondition#blockNoTransition()} releases the mutex or I return and transition out of
     * native.
     * <p>
     * This method is never inlined so that its return address serves as the return address for the
     * Java frame anchor set up in {@link #awaitWithTransition()}, so that if a collection happens
     * when this code is running, the collector can walk the stack of this thread.
     * <p>
     * See the note about object references in {@link #awaitWithTransition()}.
     */
    @Uninterruptible(reason = "Must not stop while in native.")
    @NeverInline("Provide a return address for the Java frame anchor.")
    private static void awaitInNative() {
        REF_MUTEX.lockNoTransition();
        try {
            /*
             * Wait until the condition is notified, unlocking the mutex. Do not transition back to
             * Java on return, because I do not want to stop in this method.
             */
            REF_CONDITION.blockNoTransition();
        } finally {
            REF_MUTEX.unlock();
        }
    }

    @Uninterruptible(reason = "Must not stop while in native.")
    @NeverInline("Provide a return address for the Java frame anchor.")
    private static long awaitInNative(long waitNanos) {
        long result = waitNanos;
        REF_MUTEX.lockNoTransition();
        try {
            /*
             * Wait until the condition is notified, unlocking the mutex. Do not transition back to
             * Java on return, because I do not want to stop in this method.
             */
            result = REF_CONDITION.blockNoTransition(waitNanos);
        } finally {
            REF_MUTEX.unlock();
        }
        return result;
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

    public static void signalWaiters() {
        assert VMOperation.isInProgressAtSafepoint() : "must only be called by the GC";
        REF_MUTEX.lock();
        try {
            REF_CONDITION.broadcast();
        } finally {
            REF_MUTEX.unlock();
        }
    }

    /** Clean the list state that is kept in a FeebleReference. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected static void clean(FeebleReference<?> fr) {
        fr.listRemove();
    }
}
