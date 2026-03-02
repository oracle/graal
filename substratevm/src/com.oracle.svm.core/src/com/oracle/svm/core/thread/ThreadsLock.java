/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.thread;

import static com.oracle.svm.guest.staging.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalInt;
import com.oracle.svm.guest.staging.Uninterruptible;

import jdk.internal.misc.Unsafe;

/**
 * A custom 3-mode lock that protects the thread list and is used by both the application and VM
 * operations. At first glance, this implementation looks similar to a read-write lock but there are
 * a few crucial differences, e.g., readers can coexist with writers that have non-exclusive write
 * access, which isn't typical behavior for a read-write lock.
 *
 * <p>
 * This lock has side effects on the VM as a whole, which makes it dangerous (i.e., wrong usages
 * will result in deadlocks). So, please only use it when absolutely necessary. For many use cases,
 * it is preferable to use VM operations (e.g., {@link JavaVMOperation}) with a safepoint instead.
 * If you need to acquire the lock directly (either for performance or correctness reasons), please
 * prefer read access (see {@link ThreadsLock#lockRead()}) as this is safest in terms of deadlocks,
 * while it still allows walking the thread list.
 *
 * <p>
 * Supported access (see JavaDoc of the linked methods for more details):
 * <ul>
 * <li>Read access, see {@link #lockRead} and {@link #unlockRead}. Typically used when a thread
 * needs to iterate over the thread list.</li>
 * <li>Non-exclusive write access, see {@link #lockWriteNonExclusive} and
 * {@link #unlockWriteNonExclusive}. This is for example used when a thread attaches to an isolate
 * or when an isolate needs a safepoint.</li>
 * <li>Exclusive write access, see {@link #lockWriteExclusive} and {@link #unlockWriteExclusive}.
 * Typically used when a thread detaches from an isolate.</li>
 * </ul>
 *
 * Note that a single thread may hold this lock in both read and non-exclusive write access at the
 * same time (e.g., when upgrading from read access to write access without releasing the lock in
 * between). Upgrading to exclusive-write access is not supported as this could result in deadlocks.
 *
 * <p>
 * A VM operation that needs a safepoint always acquires and holds this lock in non-exclusive write
 * access for its entire duration. At a safepoint, it is therefore safe to walk the thread list, and
 * it is guaranteed that the thread list won't change as long as the VM operation is in progress.
 *
 * <p>
 * A lot of the slow path code in this class does an explicit thread status transition to
 * {@link StatusSupport#STATUS_IN_NATIVE}. This is necessary because blocking is not allowed while
 * executing uninterruptible code as this can cause deadlocks if the VM operation thread needs a
 * safepoint. After changing the thread status to native, it is safe to execute potentially blocking
 * operations. Eventually, we need to do a transition back to {@link StatusSupport#STATUS_IN_JAVA},
 * which executes a safepoint check. Because of this, the locking methods are <b>not</b> fully
 * uninterruptible.
 *
 * <p>
 * Methods that directly use {@link CFunctionPrologueNode} need to be static. So, pretty much all
 * state in this class is static as well to make accessing easier. This is fine because this class
 * and all its state is only used at run-time, and only in code that needs to be in the base layer
 * anyways.
 *
 * <p>
 * The VM operation thread (either dedicated or temporary) may only acquire this lock with read or
 * non-exclusive write access, but never with exclusive write access. Otherwise, we could see
 * deadlocks with application threads that acquired the lock with read access from interruptible
 * code (e.g., an application thread may need to queue a VM operation such as a GC).
 *
 * <p>
 * While this mechanism is substantially different to thread SMR on HotSpot, it still gives similar
 * guarantees.
 *
 * Deadlock example 1:
 * <ul>
 * <li>Thread A acquires the ThreadsLock with write access (either non-exclusive or exclusive).</li>
 * <li>Thread B queues a VM operation that needs a safepoint and therefore acquires the
 * corresponding VM operation queue mutex.
 * <li>Thread A allocates an object and the allocation wants to trigger a GC. So, a VM operation
 * needs to be queued, and thread A tries to acquire the VM operation queue mutex. However, thread A
 * is blocked because thread B holds that mutex.</li>
 * <li>Thread B needs to initiate a safepoint before executing the VM operation. So, it tries to
 * acquire the ThreadsLock with non-exclusive write access and is blocked because thread A holds
 * that lock.</li>
 * </ul>
 *
 * Deadlock example 2:
 * <ul>
 * <li>Thread A acquires the ThreadsLock with write access (either non-exclusive or exclusive).</li>
 * <li>Thread A allocates an object and the allocation wants to trigger a GC. So, a VM operation is
 * queued and thread A blocks until the VM operation is completed.</li>
 * <li>The dedicated VM operation thread needs to initiate a safepoint for the execution of the VM
 * operation. So, it tries to acquire the ThreadsLock with non-exclusive write access and is blocked
 * because thread A still holds the ThreadsLock.</li>
 * </ul>
 */
public final class ThreadsLock {
    private static final int NUM_READERS_SHIFT = 0;
    private static final int NUM_READERS_BITS = 32;
    private static final int NON_EXCLUSIVE_WRITER_ACTIVE_SHIFT = NUM_READERS_SHIFT + NUM_READERS_BITS;
    private static final int EXCLUSIVE_WRITER_ACTIVE_SHIFT = NON_EXCLUSIVE_WRITER_ACTIVE_SHIFT + 1;

    private static final long NUM_READERS_MASK = ((1L << NUM_READERS_BITS) - 1L) << NUM_READERS_SHIFT;
    private static final long NUM_READERS_INC = 1L << NUM_READERS_SHIFT;

    private static final long NON_EXCLUSIVE_WRITER_ACTIVE_BIT = 1L << NON_EXCLUSIVE_WRITER_ACTIVE_SHIFT;
    private static final long EXCLUSIVE_WRITER_ACTIVE_BIT = 1L << EXCLUSIVE_WRITER_ACTIVE_SHIFT;

    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long STATE_OFFSET = U.objectFieldOffset(ThreadsLock.class, "state");

    private static final VMMutex mutex = new VMMutex("thread");
    private static final VMCondition slowPathCondition = new VMCondition(mutex);
    private static final VMCondition changeCondition = new VMCondition(mutex);
    /** Only updated via {@link #casState} so that always the same memory barriers are used. */
    private static volatile long state;

    @Platforms(Platform.HOSTED_ONLY.class)
    public ThreadsLock() {
    }

    /** Returns {@code true} if the current thread holds the lock with any access. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean hasAccess() {
        return ThreadsLockAccess.hasAccess();
    }

    /** Returns {@code true} if the current thread holds the lock with at least read access. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean hasReadAccess() {
        return ThreadsLockAccess.hasReadAccess();
    }

    /**
     * Returns {@code true} if the current thread holds the lock with at least non-exclusive write
     * access.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean hasWriteAccess() {
        return ThreadsLockAccess.hasWriteAccess();
    }

    /**
     * Returns {@code true} if the current thread holds the lock with non-exclusive write access.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean hasNonExclusiveWriteAccess() {
        return ThreadsLockAccess.hasNonExclusiveWriteAccessBit();
    }

    /** Returns {@code true} if the current thread holds the lock with exclusive write access. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static boolean hasExclusiveWriteAccess() {
        return ThreadsLockAccess.hasExclusiveWriteAccessBit();
    }

    /**
     * Acquires the lock once there is no exclusive writer.
     * <p>
     * Holding the lock with read access prevents attached threads from detaching and makes it safe
     * to iterate over the thread list. Be aware that readers can coexist with writers that have
     * non-exclusive write access. Therefore, the head of the thread list (or other values protected
     * by the ThreadsLock, such as the thread counts) may change at any time even while holding this
     * lock with read access.
     * <p>
     * This method may be called from either interruptible or uninterruptible code. Be aware though
     * that this method itself is not necessarily fully uninterruptible (see class-level JavaDoc for
     * more details).
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void lockRead() {
        assert !ThreadsLockAccess.hasAccess() || ThreadsLockAccess.hasNonExclusiveWriteAccessBit() : "could deadlock";

        if (!fastPathLockRead()) {
            slowPathLockRead();
        }
        ThreadsLockAccess.addReadAccess();
    }

    /**
     * Similar to {@link #lockRead} but without tracking any lock ownership information and without
     * doing any thread status transitions. This method can therefore also be used for unattached
     * threads.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void lockReadNoTransitionUnspecifiedOwner() {
        if (!fastPathLockRead()) {
            slowPathLockRead0();
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean fastPathLockRead() {
        long s;
        long n;
        do {
            s = state;
            if (isExclusiveWriterActive(s)) {
                return false;
            }

            n = s + NUM_READERS_INC;
            assert numReaders(n) > 0 : "overflow";
        } while (!casState(s, n));

        return true;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode.")
    private static void slowPathLockRead() {
        CFunctionPrologueNode.cFunctionPrologue(StatusSupport.STATUS_IN_NATIVE);
        slowPathLockRead0();
        CFunctionEpilogueNode.cFunctionEpilogue(StatusSupport.STATUS_IN_NATIVE);
    }

    @Uninterruptible(reason = "Must not execute any safepoint checks while in native.")
    @NeverInline("Provide a return address for the Java frame anchor.")
    private static void slowPathLockRead0() {
        mutex.lockNoTransitionUnspecifiedOwner();
        try {
            while (!fastPathLockRead()) {
                slowPathCondition.blockNoTransitionUnspecifiedOwner();
            }
        } finally {
            mutex.unlockNoTransitionUnspecifiedOwner();
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void unlockRead() {
        ThreadsLockAccess.removeReadAccess();
        long s = getAndAdd(-NUM_READERS_INC);

        /* No readers and no writer, so notify waiting threads. */
        if (numReaders(s) == 1 && !isAnyWriterActive(s)) {
            notifySlowPathThreadsInNative();
        }
    }

    /**
     * Similar to {@link #unlockRead} but without tracking any lock ownership information and
     * without doing any thread status transitions. This method can therefore also be used for
     * unattached threads.
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void unlockReadNoTransitionUnspecifiedOwner() {
        long s = getAndAdd(-NUM_READERS_INC);

        /* No readers and no writer, so notify waiting threads. */
        if (numReaders(s) == 1 && !isAnyWriterActive(s)) {
            notifySlowPathThreads0();
        }
    }

    /**
     * Acquires the lock once there are no other writers. Use this method only if absolutely
     * necessary as it is easy to create deadlocks.
     * <p>
     * Holding the lock with non-exclusive write access prevents safepoints. Besides that, it also
     * prevents new threads from attaching and already attached threads from detaching. Note that
     * there can be an arbitrary number of concurrent readers already holding the lock or acquiring
     * the lock at any time, so it is <b>not</b> possible to remove any entries from the thread
     * list.
     * <p>
     * All code that is executed while holding the ThreadsLock with non-exclusive write access
     * should ideally be fully {@link Uninterruptible}. Be aware though that this method itself is
     * not fully uninterruptible (see class-level JavaDoc for more details).
     */
    @Uninterruptible(reason = "Acquires the ThreadsLock with non-exclusive write access.", callerMustBe = true)
    public static void lockWriteNonExclusive() {
        assert !ThreadsLockAccess.hasAccess() || ThreadsLockAccess.hasReadAccessBit() : "could deadlock";

        if (!fastPathLockWriteNonExclusive()) {
            slowPathLockWriteNonExclusiveInNative();
        }
        ThreadsLockAccess.addNonExclusiveWriteAccess();
    }

    /** Similar to {@link #unlockRead} but without doing any thread status transitions. */
    @Uninterruptible(reason = "Acquires the ThreadsLock with non-exclusive write access.", callerMustBe = true)
    public static void lockWriteNonExclusiveNoTransition() {
        assert !ThreadsLockAccess.hasAccess() || ThreadsLockAccess.hasReadAccessBit() : "could deadlock";

        if (!fastPathLockWriteNonExclusive()) {
            slowPathLockWriteNonExclusiveInNative0();
        }
        ThreadsLockAccess.addNonExclusiveWriteAccess();
    }

    @Uninterruptible(reason = "Acquires the ThreadsLock with non-exclusive write access.", callerMustBe = true)
    private static boolean fastPathLockWriteNonExclusive() {
        long s;
        long n;
        do {
            s = state;
            if (isAnyWriterActive(s)) {
                return false;
            }

            n = s | NON_EXCLUSIVE_WRITER_ACTIVE_BIT;
        } while (!casState(s, n));

        return true;
    }

    @Uninterruptible(reason = "Acquires the ThreadsLock with non-exclusive write access.", callerMustBe = true)
    @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode.")
    private static void slowPathLockWriteNonExclusiveInNative() {
        CFunctionPrologueNode.cFunctionPrologue(StatusSupport.STATUS_IN_NATIVE);
        slowPathLockWriteNonExclusiveInNative0();
        CFunctionEpilogueNode.cFunctionEpilogue(StatusSupport.STATUS_IN_NATIVE);
    }

    @Uninterruptible(reason = "Must not execute any safepoint checks while in native.")
    @NeverInline("Provide a return address for the Java frame anchor.")
    private static void slowPathLockWriteNonExclusiveInNative0() {
        mutex.lockNoTransitionUnspecifiedOwner();
        try {
            while (!fastPathLockWriteNonExclusive()) {
                slowPathCondition.blockNoTransitionUnspecifiedOwner();
            }
        } finally {
            mutex.unlockNoTransitionUnspecifiedOwner();
        }
    }

    @Uninterruptible(reason = "Holds the ThreadsLock with non-exclusive write access.", callerMustBe = true)
    public static void unlockWriteNonExclusive() {
        ThreadsLockAccess.removeNonExclusiveWriteAccess();
        long s = clearBits(NON_EXCLUSIVE_WRITER_ACTIVE_BIT);

        /* No readers and no writer, so notify waiting threads. */
        if (numReaders(s) == 0) {
            notifySlowPathThreadsInNative();
        }
    }

    /** Similar to {@link #unlockRead} but without doing any thread status transitions. */
    @Uninterruptible(reason = "Holds the ThreadsLock with non-exclusive write access.", callerMustBe = true)
    public static void unlockWriteNonExclusiveNoTransition() {
        unlockWriteNonExclusiveNoTransition(false);
    }

    @Uninterruptible(reason = "Holds the ThreadsLock with non-exclusive write access.", callerMustBe = true)
    public static void unlockWriteNonExclusiveNoTransition(boolean alreadyOwnsMutex) {
        ThreadsLockAccess.removeNonExclusiveWriteAccess();
        long s = clearBits(NON_EXCLUSIVE_WRITER_ACTIVE_BIT);

        /* No readers and no writer, so notify waiting threads. */
        if (numReaders(s) == 0) {
            if (alreadyOwnsMutex) {
                slowPathCondition.broadcast();
            } else {
                notifySlowPathThreads0();
            }
        }
    }

    /**
     * Acquires the lock exclusively once there are no other readers and writers. Use this method
     * only if absolutely necessary as it is easy to create deadlocks.
     * <p>
     * Holding the lock with exclusive write access prevents safepoints. Besides that, it also
     * prevents new threads from attaching and already attached threads from detaching. This method
     * is the only way to remove entries from the thread list.
     * <p>
     * Note that this method is prone to starvation if other threads keep acquiring the lock with
     * read or non-exclusive write access.
     * <p>
     * All code that is executed while holding the ThreadsLock with exclusive write access should
     * ideally be fully {@link Uninterruptible}. Be aware though that this method itself is not
     * fully uninterruptible (see class-level JavaDoc for more details).
     */
    @Uninterruptible(reason = "Acquires the ThreadsLock with exclusive write access.", callerMustBe = true)
    static void lockWriteExclusive() {
        assert !ThreadsLockAccess.hasAccess() : "could deadlock";
        assert !VMOperation.isInProgress() : "could deadlock if another thread called lockRead() from interruptible code";

        if (!fastPathLockWriteExclusive()) {
            slowPathLockWriteExclusiveInNative();
        }
        ThreadsLockAccess.addExclusiveWriteAccess();
    }

    @Uninterruptible(reason = "Acquires the ThreadsLock with exclusive write access.", callerMustBe = true)
    private static boolean fastPathLockWriteExclusive() {
        long s;
        long n;
        do {
            s = state;
            if (numReaders(s) > 0 || isAnyWriterActive(s)) {
                return false;
            }

            n = s | EXCLUSIVE_WRITER_ACTIVE_BIT;
        } while (!casState(s, n));

        return true;
    }

    @Uninterruptible(reason = "Acquires the ThreadsLock with exclusive write access.", callerMustBe = true)
    @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode.")
    private static void slowPathLockWriteExclusiveInNative() {
        CFunctionPrologueNode.cFunctionPrologue(StatusSupport.STATUS_IN_NATIVE);
        slowPathLockWriteExclusiveInNative0();
        CFunctionEpilogueNode.cFunctionEpilogue(StatusSupport.STATUS_IN_NATIVE);
    }

    @Uninterruptible(reason = "Must not execute any safepoint checks while in native.")
    @NeverInline("Provide a return address for the Java frame anchor.")
    private static void slowPathLockWriteExclusiveInNative0() {
        mutex.lockNoTransition();
        try {
            while (!fastPathLockWriteExclusive()) {
                slowPathCondition.blockNoTransition();
            }
        } finally {
            mutex.unlock();
        }
    }

    @Uninterruptible(reason = "Holds the ThreadsLock with exclusive write access.", callerMustBe = true)
    static void unlockWriteExclusive() {
        ThreadsLockAccess.removeExclusiveWriteAccess();
        clearBits(EXCLUSIVE_WRITER_ACTIVE_BIT);
        notifySlowPathThreadsInNative();
    }

    @Uninterruptible(reason = "Acquires the mutex.")
    @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode.")
    private static void notifySlowPathThreadsInNative() {
        CFunctionPrologueNode.cFunctionPrologue(StatusSupport.STATUS_IN_NATIVE);
        notifySlowPathThreads0();
        CFunctionEpilogueNode.cFunctionEpilogue(StatusSupport.STATUS_IN_NATIVE);
    }

    @Uninterruptible(reason = "Must not stop while in native.")
    @NeverInline("Provide a return address for the Java frame anchor.")
    private static void notifySlowPathThreads0() {
        mutex.lockNoTransitionUnspecifiedOwner();
        try {
            slowPathCondition.broadcast();
        } finally {
            mutex.unlockNoTransitionUnspecifiedOwner();
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static long clearBits(long pattern) {
        long s;
        long n;
        do {
            s = state;
            n = (s & ~pattern);
        } while (!casState(s, n));

        return s;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static long getAndAdd(long delta) {
        long s;
        do {
            s = state;
        } while (!casState(s, s + delta));

        return s;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean casState(long expectedValue, long newValue) {
        /* Always use the static fields from the base layer. */
        Object staticPrimitiveFieldBase = StaticFieldsSupport.getStaticPrimitiveFieldsAtRuntime(0);
        return U.compareAndSetLong(staticPrimitiveFieldBase, STATE_OFFSET, expectedValue, newValue);
    }

    /** Notifies waiting threads that the thread list or thread count changed. */
    @Uninterruptible(reason = "Holds the ThreadsLock with write access.", callerMustBe = true)
    static void broadcastChange() {
        assert hasWriteAccess();

        mutex.lockNoTransition();
        try {
            changeCondition.broadcast();
        } finally {
            mutex.unlock();
        }
    }

    /**
     * The calling thread must hold the ThreadsLock with non-exclusive write access. Waits in native
     * code until the thread list or thread count changed. Spurious wake-ups may occur.
     */
    @Uninterruptible(reason = "Holds the ThreadsLock with write access.", callerMustBe = true)
    static void waitForChangeInNative() {
        assert hasNonExclusiveWriteAccess();
        waitForChangeInNative0();
    }

    @Uninterruptible(reason = "Holds the ThreadsLock with write access.", callerMustBe = true)
    @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode.")
    private static void waitForChangeInNative0() {
        CFunctionPrologueNode.cFunctionPrologue(StatusSupport.STATUS_IN_NATIVE);
        waitForChangeInNative1();
        CFunctionEpilogueNode.cFunctionEpilogue(StatusSupport.STATUS_IN_NATIVE);
    }

    @Uninterruptible(reason = "Must not execute any safepoint checks while in native.")
    @NeverInline("Provide a return address for the Java frame anchor.")
    private static void waitForChangeInNative1() {
        /* Acquire the mutex to prevent lost updates. */
        mutex.lockNoTransition();
        try {
            /* Release the lock. */
            unlockWriteNonExclusiveNoTransition(true);

            /* Block until notified or spurious wake-up. */
            changeCondition.blockNoTransition();
        } finally {
            mutex.unlock();
        }

        /* Re-acquire the lock with the same access as before. */
        lockWriteNonExclusiveNoTransition();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static int numReaders(long s) {
        return (int) ((s & NUM_READERS_MASK) >>> NUM_READERS_SHIFT);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isAnyWriterActive(long s) {
        long writerBits = NON_EXCLUSIVE_WRITER_ACTIVE_BIT | EXCLUSIVE_WRITER_ACTIVE_BIT;
        return (s & writerBits) != 0L;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isExclusiveWriterActive(long s) {
        return (s & EXCLUSIVE_WRITER_ACTIVE_BIT) != 0L;
    }

    private static final class ThreadsLockAccess {
        private static final int NO_ACCESS = 0;
        private static final int READ_ACCESS_BIT = 1;
        private static final int NON_EXCLUSIVE_WRITE_ACCESS_BIT = 2;
        private static final int EXCLUSIVE_WRITE_ACCESS_BIT = 4;

        private static final FastThreadLocalInt accessTL = FastThreadLocalFactory.createInt("ThreadsLock.access");

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static boolean hasAccess() {
            return accessTL.get() != NO_ACCESS;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static boolean hasReadAccess() {
            return accessTL.get() >= READ_ACCESS_BIT;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static boolean hasWriteAccess() {
            return accessTL.get() >= NON_EXCLUSIVE_WRITE_ACCESS_BIT;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static boolean hasReadAccessBit() {
            return (accessTL.get() & READ_ACCESS_BIT) != 0;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static boolean hasNonExclusiveWriteAccessBit() {
            return (accessTL.get() & NON_EXCLUSIVE_WRITE_ACCESS_BIT) != 0;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static boolean hasExclusiveWriteAccessBit() {
            return (accessTL.get() & EXCLUSIVE_WRITE_ACCESS_BIT) != 0;
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static void addReadAccess() {
            assert !hasReadAccessBit();
            accessTL.set(accessTL.get() | READ_ACCESS_BIT);
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static void removeReadAccess() {
            assert hasReadAccessBit();
            accessTL.set(accessTL.get() & ~READ_ACCESS_BIT);
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static void addNonExclusiveWriteAccess() {
            assert !hasNonExclusiveWriteAccessBit();
            accessTL.set(accessTL.get() | NON_EXCLUSIVE_WRITE_ACCESS_BIT);
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static void removeNonExclusiveWriteAccess() {
            assert hasNonExclusiveWriteAccessBit();
            accessTL.set(accessTL.get() & ~NON_EXCLUSIVE_WRITE_ACCESS_BIT);
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static void addExclusiveWriteAccess() {
            assert !hasExclusiveWriteAccessBit();
            accessTL.set(accessTL.get() | EXCLUSIVE_WRITE_ACCESS_BIT);
        }

        @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
        private static void removeExclusiveWriteAccess() {
            assert hasExclusiveWriteAccessBit();
            accessTL.set(accessTL.get() & ~EXCLUSIVE_WRITE_ACCESS_BIT);
        }
    }
}
