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

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.jdk.JDK11OrEarlier;
import com.oracle.svm.core.jdk.JDK17OrLater;

import jdk.internal.misc.Unsafe;

/**
 * {@link JavaMonitorAbstractQueuedSynchronizer} is derived from the class
 * {@link java.util.concurrent.locks.AbstractQueuedSynchronizer} in the JDK 19 sources. Only the
 * relevant methods from the JDK sources have been kept. Some additional Native Image-specific
 * functionality has been added.
 *
 * Git commit hash: f640fc5a1eb876a657d0de011dcd9b9a42b88eec. JDK tag: jdk-19+30
 *
 * Main differences to the JDK implementation:
 * <ul>
 * <li>There is no need to support any locking modes besides the non-fair exclusive one.</li>
 * <li>We explicitly treat ForkJoinPool threads in the same way as any other threads because
 * notify/wait should always work the same regardless of the involved thread (see
 * {@link java.util.concurrent.ForkJoinPool#managedBlock}).</li>
 * </ul>
 */
abstract class JavaMonitorAbstractQueuedSynchronizer {
    static final int WAITING = 1; // must be 1
    static final int CANCELLED = 0x80000000; // must be negative
    static final int COND = 2; // in a condition wait

    // see AbstractQueuedSynchronizer.Node
    abstract static class Node {
        volatile Node prev;
        volatile Node next;
        Thread waiter;
        volatile int status;

        // see AbstractQueuedSynchronizer.Node.casPrev(Node, Node)
        final boolean casPrev(Node c, Node v) {
            return weakCompareAndSetReference(this, PREV, c, v);
        }

        // see AbstractQueuedSynchronizer.Node.casNext(Node, Node)
        final boolean casNext(Node c, Node v) {
            return weakCompareAndSetReference(this, NEXT, c, v);
        }

        // see AbstractQueuedSynchronizer.Node.getAndUnsetStatus(int)
        final int getAndUnsetStatus(int v) {
            return U.getAndBitwiseAndInt(this, STATUS, ~v);
        }

        // see AbstractQueuedSynchronizer.Node.setPrevRelaxed(Node)
        final void setPrevRelaxed(Node p) {
            putReference(this, PREV, p);
        }

        // see AbstractQueuedSynchronizer.Node.setStatusRelaxed(int)
        final void setStatusRelaxed(int s) {
            U.putInt(this, STATUS, s);
        }

        // see AbstractQueuedSynchronizer.Node.clearStatus()
        final void clearStatus() {
            U.putIntOpaque(this, STATUS, 0);
        }

        private static final long STATUS = U.objectFieldOffset(Node.class, "status");
        private static final long NEXT = U.objectFieldOffset(Node.class, "next");
        private static final long PREV = U.objectFieldOffset(Node.class, "prev");
    }

    // see AbstractQueuedSynchronizer.ExclusiveNode
    static final class ExclusiveNode extends Node {
    }

    // see AbstractQueuedSynchronizer.ConditionNode
    static final class ConditionNode extends Node {
        ConditionNode nextWaiter;

        // see AbstractQueuedSynchronizer.ConditionNode.isReleasable()
        public boolean isReleasable() {
            // Checkstyle: allow Thread.isInterrupted"
            return status <= 1 || Thread.currentThread().isInterrupted();
            // Checkstyle: disallow Thread.isInterrupted"
        }

        // see AbstractQueuedSynchronizer.ConditionNode.block()
        public boolean block() {
            while (!isReleasable()) {
                LockSupport.park();
            }
            return true;
        }
    }

    private transient volatile Node head;
    private transient volatile Node tail;
    private volatile int state;
    // see AbstractOwnableSynchronizer.exclusiveOwnerThread
    private transient Thread exclusiveOwnerThread;

    // see AbstractOwnableSynchronizer.setExclusiveOwnerThread(Thread)
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected final void setExclusiveOwnerThread(Thread thread) {
        exclusiveOwnerThread = thread;
    }

    // see AbstractOwnableSynchronizer.getExclusiveOwnerThread()
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected final Thread getExclusiveOwnerThread() {
        return exclusiveOwnerThread;
    }

    // see AbstractQueuedSynchronizer.getState()
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final int getState() {
        return state;
    }

    // see AbstractQueuedSynchronizer.setState(int)
    protected final void setState(int newState) {
        this.state = newState;
    }

    // see AbstractQueuedSynchronizer.compareAndSetState(int, int)
    protected final boolean compareAndSetState(int expect, int update) {
        return U.compareAndSetInt(this, STATE, expect, update);
    }

    // see AbstractQueuedSynchronizer.casTail(Node, Node)
    private boolean casTail(Node c, Node v) {
        return compareAndSetReference(this, TAIL, c, v);
    }

    // see AbstractQueuedSynchronizer.tryInitializeHead()
    private void tryInitializeHead() {
        Node h = new ExclusiveNode();
        if (compareAndSetReference(this, HEAD, null, h)) {
            tail = h;
        }
    }

    // see AbstractQueuedSynchronizer.enqueue(Node)
    final void enqueue(Node node) {
        if (node != null) {
            for (;;) {
                Node t = tail;
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

    // see AbstractQueuedSynchronizer.isEnqueued(Node)
    final boolean isEnqueued(Node node) {
        for (Node t = tail; t != null; t = t.prev) {
            if (t == node) {
                return true;
            }
        }
        return false;
    }

    // see AbstractQueuedSynchronizer.signalNext(Node)
    private static void signalNext(Node h) {
        Node s;
        if (h != null && (s = h.next) != null && s.status != 0) {
            s.getAndUnsetStatus(WAITING);
            LockSupport.unpark(s.waiter);
        }
    }

    // see AbstractQueuedSynchronizer.acquire(Node, int, boolean, boolean, boolean, long)
    @SuppressWarnings("all")
    final int acquire(Node node, int arg) {
        Thread current = Thread.currentThread();
        byte spins = 0;
        byte postSpins = 0;
        boolean first = false;
        Node pred = null;

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
                    acquired = tryAcquire(arg);
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
            if (node == null) { // allocate; retry before enqueue
                node = new ExclusiveNode();
            } else if (pred == null) { // try to enqueue
                node.waiter = current;
                Node t = tail;
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
                spins = postSpins = (byte) ((postSpins << 1) | 1);
                LockSupport.park(this);
                node.clearStatus();
            }
        }
    }

    // see AbstractQueuedSynchronizer.cleanQueue()
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

    // see AbstractQueuedSynchronizer.cancelAcquire(Node, boolean, boolean)
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

    // see AbstractQueuedSynchronizer.tryAcquire(int)
    protected abstract boolean tryAcquire(int arg);

    // see AbstractQueuedSynchronizer.tryRelease(int)
    protected abstract boolean tryRelease(int releases);

    // see AbstractQueuedSynchronizer.isHeldExclusively()
    protected abstract boolean isHeldExclusively();

    // see AbstractQueuedSynchronizer.acquire(int)
    public final void acquire(int arg) {
        if (!tryAcquire(arg)) {
            acquire(null, arg);
        }
    }

    // see AbstractQueuedSynchronizer.release(int)
    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            signalNext(head);
            return true;
        }
        return false;
    }

    // see AbstractQueuedSynchronizer.ConditionObject
    public class JavaMonitorConditionObject {
        private transient ConditionNode firstWaiter;
        private transient ConditionNode lastWaiter;

        // see AbstractQueuedSynchronizer.ConditionObject.doSignal(ConditionNode, boolean)
        @SuppressWarnings("all")
        private void doSignal(ConditionNode first, boolean all) {
            while (first != null) {
                ConditionNode next = first.nextWaiter;
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

        // see AbstractQueuedSynchronizer.ConditionObject.signal()
        public final void signal() {
            ConditionNode first = firstWaiter;
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            if (first != null) {
                doSignal(first, false);
            }
        }

        // see AbstractQueuedSynchronizer.ConditionObject.signalAll()
        public final void signalAll() {
            ConditionNode first = firstWaiter;
            if (!isHeldExclusively()) {
                throw new IllegalMonitorStateException();
            }
            if (first != null) {
                doSignal(first, true);
            }
        }

        // see AbstractQueuedSynchronizer.ConditionObject.enableWait()
        private int enableWait(ConditionNode node) {
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
                int savedState = getState();
                if (release(savedState)) {
                    return savedState;
                }
            }
            node.status = CANCELLED; // lock not held or inconsistent
            throw new IllegalMonitorStateException();
        }

        // see AbstractQueuedSynchronizer.ConditionObject.canReacquire(ConditionNode)
        private boolean canReacquire(ConditionNode node) {
            // check links, not status to avoid enqueue race
            Node p; // traverse unless known to be bidirectionally linked
            return node != null && (p = node.prev) != null &&
                            (p.next == node || isEnqueued(node));
        }

        // see AbstractQueuedSynchronizer.ConditionObject.unlinkCancelledWaiters(ConditionNode)
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

        // see AbstractQueuedSynchronizer.ConditionObject.await()
        @SuppressWarnings("all")
        public final void await() throws InterruptedException {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            ConditionNode node = new ConditionNode();
            int savedState = enableWait(node);
            setCurrentBlocker(this);
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
            setCurrentBlocker(null);
            node.clearStatus();
            acquire(node, savedState);
            if (interrupted) {
                if (cancelled) {
                    unlinkCancelledWaiters(node);
                    throw new InterruptedException();
                }
                Thread.currentThread().interrupt();
            }
        }

        // see AbstractQueuedSynchronizer.ConditionObject.await(long, TimeUnit)
        @SuppressWarnings("all")
        public final boolean await(long time, TimeUnit unit) throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            ConditionNode node = new ConditionNode();
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
            acquire(node, savedState);
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

    static void setCurrentBlocker(JavaMonitorConditionObject condition) {
        if (org.graalvm.compiler.serviceprovider.JavaVersionUtil.JAVA_SPEC >= 17) {
            Target_java_util_concurrent_locks_LockSupport.setCurrentBlocker(condition);
        }
    }

    static boolean compareAndSetReference(Object object, long offset, Node expected, Node newValue) {
        Target_jdk_internal_misc_Unsafe u = SubstrateUtil.cast(U, Target_jdk_internal_misc_Unsafe.class);
        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            return u.compareAndSetReference(object, offset, expected, newValue);
        } else {
            return u.compareAndSetObject(object, offset, expected, newValue);
        }
    }

    static boolean weakCompareAndSetReference(Object object, long offset, Node expected, Node newValue) {
        Target_jdk_internal_misc_Unsafe u = SubstrateUtil.cast(U, Target_jdk_internal_misc_Unsafe.class);
        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            return u.weakCompareAndSetReference(object, offset, expected, newValue);
        } else {
            return u.weakCompareAndSetObject(object, offset, expected, newValue);
        }
    }

    static void putReference(Object object, long offset, Node p) {
        Target_jdk_internal_misc_Unsafe u = SubstrateUtil.cast(U, Target_jdk_internal_misc_Unsafe.class);
        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            u.putReference(object, offset, p);
        } else {
            u.putObject(object, offset, p);
        }
    }

    // Unsafe
    private static final Unsafe U = Unsafe.getUnsafe();
    static final long STATE = U.objectFieldOffset(JavaMonitorAbstractQueuedSynchronizer.class, "state");
    private static final long HEAD = U.objectFieldOffset(JavaMonitorAbstractQueuedSynchronizer.class, "head");
    private static final long TAIL = U.objectFieldOffset(JavaMonitorAbstractQueuedSynchronizer.class, "tail");
}

@TargetClass(value = LockSupport.class)
final class Target_java_util_concurrent_locks_LockSupport {
    @Alias
    @TargetElement(onlyWith = com.oracle.svm.core.jdk.JDK17OrLater.class)
    public static native void setCurrentBlocker(Object blocker);
}

@TargetClass(value = Unsafe.class)
final class Target_jdk_internal_misc_Unsafe {
    @Alias
    @TargetElement(onlyWith = JDK17OrLater.class)
    public native boolean compareAndSetReference(Object o, long offset, Object expected, Object x);

    @Alias
    @TargetElement(onlyWith = JDK11OrEarlier.class)
    public native boolean compareAndSetObject(Object o, long offset, Object expected, Object x);

    @Alias
    @TargetElement(onlyWith = JDK17OrLater.class)
    public native boolean weakCompareAndSetReference(Object o, long offset, Object expected, Object x);

    @Alias
    @TargetElement(onlyWith = JDK11OrEarlier.class)
    public native boolean weakCompareAndSetObject(Object o, long offset, Object expected, Object x);

    @Alias
    @TargetElement(onlyWith = JDK17OrLater.class)
    public native void putReference(Object o, long offset, Object x);

    @Alias
    @TargetElement(onlyWith = JDK11OrEarlier.class)
    public native void putObject(Object o, long offset, Object x);
}
