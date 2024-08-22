/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.monitor;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.JavaMonitorWaitEvent;
import com.oracle.svm.core.thread.JavaThreads;

import jdk.internal.misc.Unsafe;

/**
 * {@link JavaMonitorQueuedSynchronizer} is based on the code of
 * {@link java.util.concurrent.locks.AbstractQueuedLongSynchronizer} as of JDK 24+11. This class
 * could be merged with {@link JavaMonitor} but we keep it separate because that way diffing against
 * the JDK sources is easier.
 * 
 * Only the relevant methods from the JDK sources have been kept. Some additional Native
 * Image-specific functionality has been added.
 * 
 * Main differences to the JDK implementation:
 * <ul>
 * <li>No need to store an owner {@linkplain Thread thread object}.</li>
 * <li>There is no need to support any locking modes besides the non-fair exclusive one.</li>
 * <li>The visibility of methods and fields is reduced to hide implementation details.</li>
 * <li>We explicitly treat ForkJoinPool threads in the same way as any other threads because
 * notify/wait should always work the same regardless of the involved thread (see
 * {@link java.util.concurrent.ForkJoinPool#managedBlock}).</li>
 * </ul>
 */
abstract class JavaMonitorQueuedSynchronizer {
    // Node status bits, also used as argument and return values
    static final int WAITING = 1; // must be 1
    static final int CANCELLED = 0x80000000; // must be negative
    static final int COND = 2; // in a condition wait

    /** Return value of {@link #trySpinAcquire} if successfully acquired. */
    protected static final int SPIN_SUCCESS = -1;

    // see AbstractQueuedLongSynchronizer.Node
    abstract static class Node {
        volatile Node prev; // initially attached via casTail
        volatile Node next; // visibly nonnull when signallable
        Thread waiter; // visibly nonnull when enqueued
        volatile int status; // written by owner, atomic bit ops by others

        // see AbstractQueuedLongSynchronizer.Node.casPrev(Node, Node)
        final boolean casPrev(Node c, Node v) { // for cleanQueue
            return U.weakCompareAndSetReference(this, PREV, c, v);
        }

        // see AbstractQueuedLongSynchronizer.Node.casNext(Node, Node)
        final boolean casNext(Node c, Node v) { // for cleanQueue
            return U.weakCompareAndSetReference(this, NEXT, c, v);
        }

        // see AbstractQueuedLongSynchronizer.Node.getAndUnsetStatus(int)
        final int getAndUnsetStatus(int v) { // for signalling
            return U.getAndBitwiseAndInt(this, STATUS, ~v);
        }

        // see AbstractQueuedLongSynchronizer.Node.setPrevRelaxed(Node)
        final void setPrevRelaxed(Node p) { // for off-queue assignment
            U.putReference(this, PREV, p);
        }

        // see AbstractQueuedLongSynchronizer.Node.setStatusRelaxed(int)
        final void setStatusRelaxed(int s) { // for off-queue assignment
            U.putInt(this, STATUS, s);
        }

        // see AbstractQueuedLongSynchronizer.Node.clearStatus()
        final void clearStatus() { // for reducing unneeded signals
            U.putIntOpaque(this, STATUS, 0);
        }

        private static final long STATUS = U.objectFieldOffset(Node.class, "status");
        private static final long NEXT = U.objectFieldOffset(Node.class, "next");
        private static final long PREV = U.objectFieldOffset(Node.class, "prev");
    }

    // see AbstractQueuedLongSynchronizer.ExclusiveNode
    static final class ExclusiveNode extends Node {
    }

    // see AbstractQueuedLongSynchronizer.ConditionNode
    static final class ConditionNode extends Node {
        ConditionNode nextWaiter; // link to next waiting node
        long notifierJfrTid;

        // see AbstractQueuedLongSynchronizer.ConditionNode.isReleasable()
        public boolean isReleasable() {
            return status <= 1 || JavaThreads.isInterrupted(Thread.currentThread());
        }

        // see AbstractQueuedLongSynchronizer.ConditionNode.block()
        public boolean block() {
            while (!isReleasable()) {
                /*
                 * In AbstractQueuedLongSynchronizer, callers use LockSupport.setCurrentBlocker() to
                 * stay compatible with the behavior of former code. We need the blocker object only
                 * to set the correct thread state while parking and can pass it to park() here.
                 */
                LockSupport.park(this);
            }
            return true;
        }
    }

    private transient volatile Node head;
    private transient volatile Node tail;
    private volatile long state;

    // see AbstractQueuedLongSynchronizer.getState()
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected final long getState() {
        return state;
    }

    // see AbstractQueuedLongSynchronizer.setState(long)
    protected final void setState(long newState) {
        state = newState;
    }

    /**
     * Used for {@linkplain JavaMonitorConditionObject#await conditional waiting}. Returns the
     * number of acquisitions, which is subsequently passed to {@link #tryRelease} to entirely
     * release ownership, and later to {@link #tryAcquire} to regain ownership after waiting.
     *
     * Note that {@code AbstractQueuedLongSynchronizer} calls {@link #getState()} instead, assuming
     * that the state encodes the acquisition count. This method allows for more flexibility in
     * implementations.
     */
    protected abstract long getAcquisitions();

    // see AbstractQueuedLongSynchronizer.compareAndSetState(long, long)
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected final boolean compareAndSetState(long expect, long update) {
        return U.compareAndSetLong(this, STATE, expect, update);
    }

    // see AbstractQueuedLongSynchronizer.casTail(Node, Node)
    private boolean casTail(Node c, Node v) {
        return U.compareAndSetReference(this, TAIL, c, v);
    }

    // see AbstractQueuedLongSynchronizer.tryInitializeHead()
    private Node tryInitializeHead() {
        for (Node h = null, t;;) {
            if ((t = tail) != null) {
                return t;
            } else if (head != null) {
                Thread.onSpinWait();
            } else {
                if (h == null) {
                    try {
                        h = allocateExclusiveNode();
                    } catch (OutOfMemoryError oome) {
                        return null;
                    }
                }
                if (U.compareAndSetReference(this, HEAD, null, h)) {
                    return tail = h;
                }
            }
        }
    }

    // see AbstractQueuedLongSynchronizer.enqueue(Node)
    final void enqueue(ConditionNode node) {
        if (node != null) {
            boolean unpark = false;
            for (Node t;;) {
                if ((t = tail) == null && (t = tryInitializeHead()) == null) {
                    unpark = true; // wake up to spin on OOME
                    break;
                }
                node.setPrevRelaxed(t); // avoid unnecessary fence
                if (casTail(t, node)) {
                    t.next = node;
                    if (t.status < 0) { // wake up to clean link
                        unpark = true;
                    }
                    break;
                }
            }
            if (unpark) {
                LockSupport.unpark(node.waiter);
            }
        }
    }

    // see AbstractQueuedLongSynchronizer.isEnqueued(Node)
    final boolean isEnqueued(Node node) {
        for (Node t = tail; t != null; t = t.prev) {
            if (t == node) {
                return true;
            }
        }
        return false;
    }

    // see AbstractQueuedLongSynchronizer.signalNext(Node)
    private static void signalNext(Node h) {
        Node s;
        if (h != null && (s = h.next) != null && s.status != 0) {
            s.getAndUnsetStatus(WAITING);
            LockSupport.unpark(s.waiter);
        }
    }

    protected boolean hasReleaseSuccessor() {
        Node h = head;
        if (h == null) {
            return false;
        }
        Node s = h.next;
        return s != null && s.status != 0;
    }

    protected void signalReleaseSuccessor() {
        signalNext(head);
    }

    /**
     * Attempt to acquire as part of spinning, returning {@link #SPIN_SUCCESS} if successful, else
     * the number of remaining attempts.
     */
    protected int trySpinAcquire(int spins, long arg) {
        assert spins > 0;
        if (tryAcquire(arg)) {
            return SPIN_SUCCESS;
        }
        return spins - 1;
    }

    /**
     * Given the number of previous park operations, returns the number of attempts for
     * {@link #trySpinAcquire}, provided that the thread is eligible to acquire the lock (not queued
     * yet or first in the queue).
     */
    protected int getSpinAttempts(int parks) {
        if (parks < 0 || parks > 8) {
            return 0xff;
        }
        return (1 << parks) - 1;
    }

    // see AbstractQueuedLongSynchronizer.acquire(Node, long, false, false, false, 0L)
    @SuppressWarnings("all")
    final int acquire(Node node, long arg) {
        Thread current = Thread.currentThread();
        /* Spinning logic is SVM-specific. */
        int parks = 0;
        int spins = getSpinAttempts(parks);
        boolean first = false;
        Node pred = null; // predecessor of node when enqueued

        for (;;) {
            if (!first && (pred = (node == null) ? null : node.prev) != null && !(first = (head == pred))) {
                if (pred.status < 0) {
                    cleanQueue(); // predecessor cancelled
                    continue;
                } else if (pred.prev == null) {
                    Thread.onSpinWait(); // ensure serialization
                    continue;
                }
            }
            if (first || pred == null) {
                boolean acquired;
                try {
                    if (spins > 0) {
                        /* Spinning logic is SVM-specific. */
                        spins = trySpinAcquire(spins, arg);
                        acquired = (spins == SPIN_SUCCESS);
                        assert !acquired || isHeldExclusively();
                    } else {
                        acquired = tryAcquire(arg);
                    }
                } catch (Throwable ex) {
                    cancelAcquire(node);
                    throw ex;
                }
                if (acquired) {
                    if (first) {
                        node.prev = null;
                        head = node;
                        pred.next = null;
                        node.waiter = null;
                    }
                    return 1;
                }
            }
            Node t;
            if ((t = tail) == null) { // initialize queue
                if (tryInitializeHead() == null) {
                    return acquireOnOOME(arg);
                }
            } else if (node == null) { // allocate; retry before enqueue
                try {
                    node = allocateExclusiveNode();
                } catch (OutOfMemoryError oome) {
                    return acquireOnOOME(arg);
                }
            } else if (pred == null) { // try to enqueue
                node.waiter = current;
                node.setPrevRelaxed(t); // avoid unnecessary fence
                if (!casTail(t, node)) {
                    node.setPrevRelaxed(null); // back out
                } else {
                    t.next = node;
                }
            } else if (first && spins > 0) {
                Thread.onSpinWait();
            } else if (node.status == 0) {
                node.status = WAITING; // enable signal and recheck
            } else {
                /* Spinning logic is SVM-specific. */
                parks++;
                spins = getSpinAttempts(parks);
                try {
                    LockSupport.park(this);
                } catch (Error | RuntimeException ex) {
                    cancelAcquire(node); // cancel & rethrow
                    throw ex;
                }
                node.clearStatus();
            }
        }
    }

    // see AbstractQueuedLongSynchronizer.acquireOnOOME(false, long)
    private int acquireOnOOME(long arg) {
        for (long nanos = 1L;;) {
            if (tryAcquire(arg)) {
                return 1;
            }
            U.park(false, nanos); // must use Unsafe park to sleep
            if (nanos < 1L << 30) { // max about 1 second
                nanos <<= 1;
            }
        }
    }

    // see AbstractQueuedLongSynchronizer.cleanQueue()
    private void cleanQueue() {
        for (;;) { // restart point
            for (Node q = tail, s = null, p, n;;) { // (p, q, s) triples
                if (q == null || (p = q.prev) == null) {
                    return; // end of list
                }
                if (s == null ? tail != q : (s.prev != q || s.status < 0)) {
                    break; // inconsistent
                }
                if (q.status < 0) { // cancelled
                    if ((s == null ? casTail(q, p) : s.casPrev(q, p)) && q.prev == p) {
                        p.casNext(q, s); // OK if fails
                        if (p.prev == null) {
                            signalNext(p);
                        }
                    }
                    break;
                }
                if ((n = p.next) != q) { // help finish
                    if (n != null && q.prev == p) {
                        p.casNext(n, q);
                        if (p.prev == null) {
                            signalNext(p);
                        }
                    }
                    break;
                }
                s = q;
                q = q.prev;
            }
        }
    }

    @NeverInline("Can be removed once GR-51172 is resolved")
    private static ExclusiveNode allocateExclusiveNode() {
        return new ExclusiveNode();
    }

    // see AbstractQueuedLongSynchronizer.cancelAcquire(Node, false, false)
    private int cancelAcquire(Node node) {
        if (node != null) {
            node.waiter = null;
            node.status = CANCELLED;
            if (node.prev != null) {
                cleanQueue();
            }
        }
        return 0;
    }

    // see AbstractQueuedLongSynchronizer.tryAcquire(long)
    protected abstract boolean tryAcquire(long arg);

    // see AbstractQueuedLongSynchronizer.tryRelease(long)
    protected abstract boolean tryRelease(long arg);

    // see AbstractQueuedLongSynchronizer.isHeldExclusively()
    protected abstract boolean isHeldExclusively();

    // see AbstractQueuedLongSynchronizer.acquire(long)
    protected final void acquire(long arg) {
        if (!tryAcquire(arg)) {
            acquire(null, arg);
        }
    }

    // see AbstractQueuedLongSynchronizer.release(long)
    protected final boolean release(long arg) {
        if (tryRelease(arg)) {
            signalNext(head);
            return true;
        }
        return false;
    }

    // see AbstractQueuedLongSynchronizer.ConditionObject
    public final class JavaMonitorConditionObject {
        private transient ConditionNode firstWaiter;
        private transient ConditionNode lastWaiter;

        static final long OOME_COND_WAIT_DELAY = 10L * 1000L * 1000L; // 10 ms

        JavaMonitorConditionObject() {
        }

        // see AbstractQueuedLongSynchronizer.ConditionObject.doSignal(ConditionNode, boolean)
        @SuppressWarnings("all")
        private void doSignal(ConditionNode first, boolean all) {
            while (first != null) {
                ConditionNode next = first.nextWaiter;

                if ((firstWaiter = next) == null) {
                    lastWaiter = null;
                } else {
                    first.nextWaiter = null; // GC assistance
                }
                if ((first.getAndUnsetStatus(COND) & COND) != 0) {
                    /* JFR-related code is SVM-specific. */
                    first.notifierJfrTid = SubstrateJVM.getCurrentThreadId();
                    enqueue(first);
                    if (!all) {
                        break;
                    }
                }

                first = next;
            }
        }

        // see AbstractQueuedLongSynchronizer.ConditionObject.signal()
        public void signal() {
            ConditionNode first = firstWaiter;
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            } else if (first != null) {
                doSignal(first, false);
            }
        }

        // see AbstractQueuedLongSynchronizer.ConditionObject.signalAll()
        public void signalAll() {
            ConditionNode first = firstWaiter;
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            } else if (first != null) {
                doSignal(first, true);
            }
        }

        // see AbstractQueuedLongSynchronizer.ConditionObject.enableWait()
        private long enableWait(ConditionNode node) {
            if (isHeldExclusively()) {
                node.waiter = Thread.currentThread();
                node.setStatusRelaxed(COND | WAITING);
                ConditionNode last = lastWaiter;
                if (last == null) {
                    firstWaiter = node;
                } else {
                    last.nextWaiter = node;
                }
                lastWaiter = node;
                long savedAcquisitions = getAcquisitions();
                if (release(savedAcquisitions)) {
                    return savedAcquisitions;
                }
            }
            node.status = CANCELLED; // lock not held or inconsistent
            throw new IllegalMonitorStateException();
        }

        // see AbstractQueuedLongSynchronizer.ConditionObject.canReacquire(ConditionNode)
        private boolean canReacquire(ConditionNode node) {
            // check links, not status to avoid enqueue race
            Node p; // traverse unless known to be bidirectionally linked
            return node != null && (p = node.prev) != null &&
                            (p.next == node || isEnqueued(node));
        }

        // see AbstractQueuedLongSynchronizer.ConditionObject.unlinkCancelledWaiters(ConditionNode)
        private void unlinkCancelledWaiters(ConditionNode node) {
            if (node == null || node.nextWaiter != null || node == lastWaiter) {
                ConditionNode w = firstWaiter;
                ConditionNode trail = null;
                while (w != null) {
                    ConditionNode next = w.nextWaiter;
                    if ((w.status & COND) == 0) {
                        w.nextWaiter = null;
                        if (trail == null) {
                            firstWaiter = next;
                        } else {
                            trail.nextWaiter = next;
                        }
                        if (next == null) {
                            lastWaiter = trail;
                        }
                    } else {
                        trail = w;
                    }
                    w = next;
                }
            }
        }

        // see AbstractQueuedLongSynchronizer.ConditionObject.newConditionNode()
        private ConditionNode newConditionNode() {
            long savedState;
            if (tryInitializeHead() != null) {
                try {
                    return allocateConditionNode();
                } catch (OutOfMemoryError oome) {
                }
            }
            // fall through if encountered OutOfMemoryError
            if (!isHeldExclusively() || !release(savedState = getState())) {
                throw new IllegalMonitorStateException();
            }
            U.park(false, OOME_COND_WAIT_DELAY);
            acquireOnOOME(savedState);
            return null;
        }

        @NeverInline("Can be removed once GR-51172 is resolved")
        private static ConditionNode allocateConditionNode() {
            return new ConditionNode();
        }

        // see AbstractQueuedLongSynchronizer.ConditionObject.await()
        @SuppressWarnings("all")
        public void await(Object obj) throws InterruptedException {
            /* JFR-related code is SVM-specific. */
            long startTicks = JfrTicks.elapsedTicks();
            if (Thread.interrupted()) {
                JavaMonitorWaitEvent.emit(startTicks, obj, 0, 0L, false);
                throw new InterruptedException();
            }
            ConditionNode node = newConditionNode();
            if (node == null) {
                return;
            }
            long savedAcquisitions = enableWait(node);
            boolean interrupted = false;
            boolean cancelled = false;
            while (!canReacquire(node)) {
                if (interrupted |= Thread.interrupted()) {
                    if (cancelled = (node.getAndUnsetStatus(COND) & COND) != 0) {
                        break; // else interrupted after signal
                    }
                } else if ((node.status & COND) != 0) {
                    node.block();
                } else {
                    Thread.onSpinWait(); // awoke while enqueuing
                }
            }
            node.clearStatus();
            // waiting is done, emit wait event
            JavaMonitorWaitEvent.emit(startTicks, obj, node.notifierJfrTid, 0L, false);
            acquire(node, savedAcquisitions);
            if (interrupted) {
                if (cancelled) {
                    unlinkCancelledWaiters(node);
                    throw new InterruptedException();
                }
                Thread.currentThread().interrupt();
            }
        }

        // see AbstractQueuedLongSynchronizer.ConditionObject.await(long, TimeUnit)
        @SuppressWarnings("all")
        public boolean await(Object obj, long time, TimeUnit unit) throws InterruptedException {
            /* JFR-related code is SVM-specific. */
            long startTicks = JfrTicks.elapsedTicks();
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted()) {
                JavaMonitorWaitEvent.emit(startTicks, obj, 0, 0L, false);
                throw new InterruptedException();
            }
            ConditionNode node = newConditionNode();
            if (node == null) {
                return false;
            }
            long savedAcquisitions = enableWait(node);
            long nanos = (nanosTimeout < 0L) ? 0L : nanosTimeout;
            long deadline = System.nanoTime() + nanos;
            boolean cancelled = false;
            boolean interrupted = false;
            while (!canReacquire(node)) {
                if ((interrupted |= Thread.interrupted()) || (nanos = deadline - System.nanoTime()) <= 0L) {
                    if (cancelled = (node.getAndUnsetStatus(COND) & COND) != 0) {
                        break;
                    }
                } else {
                    LockSupport.parkNanos(this, nanos);
                }
            }
            node.clearStatus();
            // waiting is done, emit wait event
            JavaMonitorWaitEvent.emit(startTicks, obj, node.notifierJfrTid, time, cancelled);
            acquire(node, savedAcquisitions);
            if (cancelled) {
                unlinkCancelledWaiters(node);
                if (interrupted) {
                    throw new InterruptedException();
                }
            } else if (interrupted) {
                Thread.currentThread().interrupt();
            }
            return !cancelled;
        }
    }

    // Unsafe
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long STATE = U.objectFieldOffset(JavaMonitorQueuedSynchronizer.class, "state");
    private static final long HEAD = U.objectFieldOffset(JavaMonitorQueuedSynchronizer.class, "head");
    private static final long TAIL = U.objectFieldOffset(JavaMonitorQueuedSynchronizer.class, "tail");
}
