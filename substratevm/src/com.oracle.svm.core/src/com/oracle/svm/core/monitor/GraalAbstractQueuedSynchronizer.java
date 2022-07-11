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

import jdk.internal.misc.Unsafe;

import java.util.Date;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractOwnableSynchronizer;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.LockSupport;

/*
GraalAbstractQueuedSynchronizer is derived from the AbstractQueuedSynchronizer class in the jdk 19 sources. Only the methods required for
substrateVM monitor support have been kept. Additional functionality specific to substrateVM has been added.
 */
public class GraalAbstractQueuedSynchronizer extends AbstractOwnableSynchronizer {
    static final int WAITING = 1; // must be 1
    static final int CANCELLED = 0x80000000; // must be negative
    static final int COND = 2; // in a condition wait
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long STATE = U.objectFieldOffset(GraalAbstractQueuedSynchronizer.class, "state");
    private static final long HEAD = U.objectFieldOffset(GraalAbstractQueuedSynchronizer.class, "head");
    private static final long TAIL = U.objectFieldOffset(GraalAbstractQueuedSynchronizer.class, "tail");
    private volatile int state;
    private transient volatile GraalAbstractQueuedSynchronizer.Node head;
    private transient volatile GraalAbstractQueuedSynchronizer.Node tail;

    private static void signalNext(GraalAbstractQueuedSynchronizer.Node h) {
        GraalAbstractQueuedSynchronizer.Node s;
        if (h != null && (s = h.next) != null && s.status != 0) {
            s.getAndUnsetStatus(WAITING);
            LockSupport.unpark(s.waiter);
        }
    }

    private static void signalNextIfShared(GraalAbstractQueuedSynchronizer.Node h) {
        GraalAbstractQueuedSynchronizer.Node s;
        if (h != null && (s = h.next) != null && (s instanceof GraalAbstractQueuedSynchronizer.SharedNode)
                && s.status != 0) {
            s.getAndUnsetStatus(WAITING);
            LockSupport.unpark(s.waiter);
        }
    }

    public final int getState() {
        return state;
    }

    protected final void setState(int newState) {
        this.state = newState;
    }

    protected final Thread getExclusiveOwnerThread1() {
        return getExclusiveOwnerThread();
    }

    protected final void setExclusiveOwnerThread1(Thread thread) {
        setExclusiveOwnerThread(thread);
    }

    public final void acquire(int arg) {
        if (!tryAcquire(arg)) {
            acquire(null, arg, false, false, false, 0L);
        }

    }

    final int acquire(GraalAbstractQueuedSynchronizer.Node node, int arg, boolean shared, boolean interruptible,
                      boolean timed, long time) {
        Thread current = Thread.currentThread();
        byte spins = 0;
        byte postSpins = 0; // retries upon unpark of first thread
        boolean interrupted = false;
        boolean first = false;
        GraalAbstractQueuedSynchronizer.Node pred = null; // predecessor of node when enqueued

        /*
         * Repeatedly: Check if node now first if so, ensure head stable, else ensure
         * valid predecessor if node is first or not yet enqueued, try acquiring else if
         * node not yet created, create it else if not yet enqueued, try once to enqueue
         * else if woken from park, retry (up to postSpins times) else if WAITING status
         * not set, set and retry else park and clear WAITING status, and check
         * cancellation
         */

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
                    if (shared) {
                        acquired = (tryAcquireShared(arg) >= 0);
                    } else {
                        acquired = tryAcquire(arg);
                    }
                } catch (Throwable ex) {
                    cancelAcquire(node, interrupted, false);
                    throw ex;
                }
                if (acquired) {
                    if (first) {
                        node.prev = null;
                        head = node;
                        pred.next = null;
                        node.waiter = null;
                        if (shared) {
                            signalNextIfShared(node);
                        }
                        if (interrupted) {
                            current.interrupt();
                        }
                    }
                    return 1;
                }
            }
            if (node == null) { // allocate; retry before enqueue
                if (shared) {
                    node = new GraalAbstractQueuedSynchronizer.SharedNode();
                } else {
                    node = new GraalAbstractQueuedSynchronizer.ExclusiveNode();
                }
            } else if (pred == null) { // try to enqueue
                node.waiter = current;
                GraalAbstractQueuedSynchronizer.Node t = tail;
                node.setPrevRelaxed(t); // avoid unnecessary fence
                if (t == null) {
                    tryInitializeHead();
                } else if (!casTail(t, node)) {
                    node.setPrevRelaxed(null); // back out
                } else {
                    t.next = node;
                }
            } else if (first && spins != 0) {
                --spins; // reduce unfairness on rewaits
                Thread.onSpinWait();
            } else if (node.status == 0) {
                node.status = WAITING; // enable signal and recheck
            } else {
                long nanos;
                spins = postSpins = (byte) ((postSpins << 1) | 1);
                if (!timed) {
                    LockSupport.park(this);
                } else if ((nanos = time - System.nanoTime()) > 0L) {
                    LockSupport.parkNanos(this, nanos);
                } else {
                    break;
                }
                node.clearStatus();
                if ((interrupted |= Thread.interrupted()) && interruptible) {
                    break;
                }
            }
        }
        return cancelAcquire(node, interrupted, interruptible);
    }

    private void cleanQueue() {
        for (;;) { // restart point
            for (GraalAbstractQueuedSynchronizer.Node q = tail, s = null, p, n;;) { // (p, q, s) triples
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

    private int cancelAcquire(GraalAbstractQueuedSynchronizer.Node node, boolean interrupted, boolean interruptible) {
        if (node != null) {
            node.waiter = null;
            node.status = CANCELLED;
            if (node.prev != null) {
                cleanQueue();
            }
        }
        if (interrupted) {
            if (interruptible) {
                return CANCELLED;
            } else {
                Thread.currentThread().interrupt();
            }
        }
        return 0;
    }

    private boolean casTail(GraalAbstractQueuedSynchronizer.Node c, GraalAbstractQueuedSynchronizer.Node v) {
        return U.compareAndSetReference(this, TAIL, c, v);
    }

    private void tryInitializeHead() {
        GraalAbstractQueuedSynchronizer.Node h = new GraalAbstractQueuedSynchronizer.ExclusiveNode();
        if (U.compareAndSetReference(this, HEAD, null, h)) {
            tail = h;
        }
    }

    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    protected final boolean compareAndSetState(int expect, int update) {
        return U.compareAndSetInt(this, STATE, expect, update);
    }

    public final boolean release(int arg) {
        if (this.tryRelease(arg)) {
            signalNext(this.head);
            return true;
        } else {
            return false;
        }
    }

    final boolean isEnqueued(GraalAbstractQueuedSynchronizer.Node node) {
        for (GraalAbstractQueuedSynchronizer.Node t = tail; t != null; t = t.prev) {
            if (t == node) {
                return true;
            }
        }
        return false;
    }

    final void enqueue(GraalAbstractQueuedSynchronizer.Node node) {
        if (node != null) {
            for (;;) {
                GraalAbstractQueuedSynchronizer.Node t = tail;
                node.setPrevRelaxed(t); // avoid unnecessary fence
                if (t == null) { // initialize
                    tryInitializeHead();
                } else if (casTail(t, node)) {
                    t.next = node;
                    if (t.status < 0) { // wake up to clean link
                        LockSupport.unpark(node.waiter);
                    }
                    break;
                }
            }
        }
    }

    static final class ExclusiveNode extends GraalAbstractQueuedSynchronizer.Node {
    }

    static final class SharedNode extends GraalAbstractQueuedSynchronizer.Node {
    }

    abstract static class Node {
        private static final long STATUS;
        private static final long NEXT;
        private static final long PREV;

        static {
            STATUS = GraalAbstractQueuedSynchronizer.U.objectFieldOffset(GraalAbstractQueuedSynchronizer.Node.class,
                    "status");
            NEXT = GraalAbstractQueuedSynchronizer.U.objectFieldOffset(GraalAbstractQueuedSynchronizer.Node.class,
                    "next");
            PREV = GraalAbstractQueuedSynchronizer.U.objectFieldOffset(GraalAbstractQueuedSynchronizer.Node.class,
                    "prev");
        }

        volatile GraalAbstractQueuedSynchronizer.Node prev;
        volatile GraalAbstractQueuedSynchronizer.Node next;
        Thread waiter;
        volatile int status;

        Node() {
        }

        final boolean casPrev(GraalAbstractQueuedSynchronizer.Node c, GraalAbstractQueuedSynchronizer.Node v) {
            return GraalAbstractQueuedSynchronizer.U.weakCompareAndSetReference(this, PREV, c, v);
        }

        final boolean casNext(GraalAbstractQueuedSynchronizer.Node c, GraalAbstractQueuedSynchronizer.Node v) {
            return GraalAbstractQueuedSynchronizer.U.weakCompareAndSetReference(this, NEXT, c, v);
        }

        final int getAndUnsetStatus(int v) {
            return GraalAbstractQueuedSynchronizer.U.getAndBitwiseAndInt(this, STATUS, ~v);
        }

        final void setPrevRelaxed(GraalAbstractQueuedSynchronizer.Node p) {
            GraalAbstractQueuedSynchronizer.U.putReference(this, PREV, p);
        }

        final void setStatusRelaxed(int s) {
            GraalAbstractQueuedSynchronizer.U.putInt(this, STATUS, s);
        }

        final void clearStatus() {
            GraalAbstractQueuedSynchronizer.U.putIntOpaque(this, STATUS, 0);
        }
    }

    static final class ConditionNode extends GraalAbstractQueuedSynchronizer.Node
            implements ForkJoinPool.ManagedBlocker {
        GraalAbstractQueuedSynchronizer.ConditionNode nextWaiter; // link to next waiting node

        /**
         * Allows Conditions to be used in ForkJoinPools without risking fixed pool
         * exhaustion. This is usable only for untimed Condition waits, not timed
         * versions.
         */
        // Checkstyle: allow Thread.isInterrupted"
        public boolean isReleasable() {
            return status <= 1 || Thread.currentThread().isInterrupted();
        }
        // Checkstyle: disallow Thread.isInterrupted"

        public boolean block() {
            while (!isReleasable()) {
                LockSupport.park();
            }
            return true;
        }
    }

    public class GraalConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        /**
         * First node of condition queue.
         */
        private transient GraalAbstractQueuedSynchronizer.ConditionNode firstWaiter;
        /**
         * Last node of condition queue.
         */
        private transient GraalAbstractQueuedSynchronizer.ConditionNode lastWaiter;

        public GraalConditionObject() {
        }

        public GraalAbstractQueuedSynchronizer getOuter() {
            return GraalAbstractQueuedSynchronizer.this;
        }

        // Signalling methods

        /**
         * Removes and transfers one or all waiters to sync queue.
         */
        private void doSignal(GraalAbstractQueuedSynchronizer.ConditionNode first, boolean all) {
            while (first != null) {
                GraalAbstractQueuedSynchronizer.ConditionNode next = first.nextWaiter;
                if ((firstWaiter = next) == null) {
                    lastWaiter = null;
                }
                if ((first.getAndUnsetStatus(COND) & COND) != 0) {
                    enqueue(first);
                    if (!all) {
                        break;
                    }
                }
                first = next;
            }
        }

        /**
         * Moves the longest-waiting thread, if one exists, from the wait queue for this
         * condition to the wait queue for the owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively} returns
         *                                      {@code false}
         */
        public final void signal() {
            GraalAbstractQueuedSynchronizer.ConditionNode first = firstWaiter;
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            if (first != null) {
                doSignal(first, false);
            }
        }

        /**
         * Moves all threads from the wait queue for this condition to the wait queue
         * for the owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively} returns
         *                                      {@code false}
         */
        public final void signalAll() {
            GraalAbstractQueuedSynchronizer.ConditionNode first = firstWaiter;
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            if (first != null) {
                doSignal(first, true);
            }
        }

        private boolean canReacquire(GraalAbstractQueuedSynchronizer.ConditionNode node) {
            // check links, not status to avoid enqueue race
            GraalAbstractQueuedSynchronizer.Node p; // traverse unless known to be bidirectionally linked
            return node != null && (p = node.prev) != null && (p.next == node || isEnqueued(node));
        }

        private void unlinkCancelledWaiters(GraalAbstractQueuedSynchronizer.ConditionNode node) {
            if (node == null || node.nextWaiter != null || node == lastWaiter) {
                GraalAbstractQueuedSynchronizer.ConditionNode w = firstWaiter;
                GraalAbstractQueuedSynchronizer.ConditionNode trail = null;
                while (w != null) {
                    GraalAbstractQueuedSynchronizer.ConditionNode next = w.nextWaiter;
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

        public final void awaitUninterruptibly() {
            throw new UnsupportedOperationException();
        }

        public final boolean awaitUntil(Date deadline) {
            throw new UnsupportedOperationException();
        }

        public final void await() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            GraalAbstractQueuedSynchronizer.ConditionNode node = new GraalAbstractQueuedSynchronizer.ConditionNode();
            int savedState = enableWait(node);
            LockSupport.setCurrentBlocker(this); // for back-compatibility
            boolean interrupted = false;
            boolean cancelled = false;
            boolean rejected = false;
            while (!canReacquire(node)) {
                if (interrupted |= Thread.interrupted()) {
                    if (cancelled = (node.getAndUnsetStatus(COND) & COND) != 0) {
                        break; // else interrupted after signal
                    }
                } else if ((node.status & COND) != 0) {
                    try {
                        if (rejected) {
                            node.block();
                        } else {
                            ForkJoinPool.managedBlock(node);
                        }
                    } catch (RejectedExecutionException ex) {
                        rejected = true;
                    } catch (InterruptedException ie) {
                        interrupted = true;
                    }
                } else {
                    Thread.onSpinWait(); // awoke while enqueuing
                }
            }
            LockSupport.setCurrentBlocker(null);
            node.clearStatus();
            acquire(node, savedState, false, false, false, 0L);
            if (interrupted) {
                if (cancelled) {
                    unlinkCancelledWaiters(node);
                    throw new InterruptedException();
                }
                Thread.currentThread().interrupt();
            }
        }

        public final boolean await(long time, TimeUnit unit) throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            GraalAbstractQueuedSynchronizer.ConditionNode node = new GraalAbstractQueuedSynchronizer.ConditionNode();
            int savedState = enableWait(node);
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
            acquire(node, savedState, false, false, false, 0L);
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

        public final long awaitNanos(long nanosTimeout) throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        private int enableWait(GraalAbstractQueuedSynchronizer.ConditionNode node) {
            if (isHeldExclusively()) {
                node.waiter = Thread.currentThread();
                node.setStatusRelaxed(COND | WAITING);
                GraalAbstractQueuedSynchronizer.ConditionNode last = lastWaiter;
                if (last == null) {
                    firstWaiter = node;
                } else {
                    last.nextWaiter = node;
                }
                lastWaiter = node;
                int savedState = getState();
                if (release(savedState)) {
                    return savedState;
                }
            }
            node.status = CANCELLED; // lock not held or inconsistent
            throw new IllegalMonitorStateException();
        }
    }
}
