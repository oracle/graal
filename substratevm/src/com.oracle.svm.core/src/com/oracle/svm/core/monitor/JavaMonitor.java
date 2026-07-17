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

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.IsolateThread;

import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrThreadRepository;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.events.JavaMonitorEnterEvent;
import com.oracle.svm.core.jfr.traceid.JfrEpoch;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.util.BasedOnJDKClass;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.internal.misc.Unsafe;

/**
 * {@link JavaMonitor} is based on the code of {@link java.util.concurrent.locks.ReentrantLock} as
 * of JDK 24+11.
 *
 * Only the relevant methods from the JDK sources have been kept. Some additional Native
 * Image-specific functionality has been added.
 *
 * Main differences to the JDK implementation:
 * <ul>
 * <li>Uses the {@linkplain #setState synchronization state} to store a
 * {@linkplain #getThreadIdentity numeric identifier of the owner thread} and {@link #acquisitions}
 * to store the number of lock acquisitions, enabling various optimizations.</li>
 * </ul>
 */
@BasedOnJDKClass(ReentrantLock.class)
@BasedOnJDKClass(value = ReentrantLock.class, innerClass = "Sync")
public final class JavaMonitor extends JavaMonitorQueuedSynchronizer {
    private long latestJfrTid;
    private String latestJfrVThreadName;
    private long latestJfrVThreadEpochId;

    public JavaMonitor() {
    }

    public void monitorEnter(Object obj) {
        if (!tryLock()) {
            long startTicks = JfrTicks.elapsedTicks();
            acquire(1);
            JavaMonitorEnterEvent.emit(obj, latestJfrTid, latestJfrVThreadName, latestJfrVThreadEpochId, startTicks);
        }

        setJfrOwner();
    }

    private void setJfrOwner() {
        if (!HasJfrSupport.get()) {
            return;
        }

        setJfrOwner(Thread.currentThread());
    }

    /** Captures the JFR metadata of the monitor owner for a later contention event. */
    private void setJfrOwner(Thread thread) {
        if (!HasJfrSupport.get()) {
            return;
        }

        latestJfrTid = JavaThreads.getThreadId(thread);
        if (JavaThreads.isVirtual(thread)) {
            latestJfrVThreadName = thread.getName();
            latestJfrVThreadEpochId = JfrThreadRepository.getVThreadEpochId(thread);
        } else {
            latestJfrVThreadName = null;
            latestJfrVThreadEpochId = JfrEpoch.NO_EPOCH_ID;
        }
    }

    void monitorExit() {
        release(1);
    }

    boolean isHeldByCurrentThread() {
        return isHeldExclusively();
    }

    JavaMonitorConditionObject getOrCreateCondition(boolean createIfNotExisting) {
        JavaMonitorConditionObject existingCondition = condition;
        if (existingCondition != null || !createIfNotExisting) {
            return existingCondition;
        }
        JavaMonitorConditionObject newCondition = new JavaMonitorConditionObject();
        if (!U.compareAndSetReference(this, CONDITION_FIELD_OFFSET, null, newCondition)) {
            newCondition = condition;
        }
        return newCondition;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void relockObject() {
        /*
         * This code runs just before we are returning to the actual deoptimized frame. This means
         * that the thread either must already hold the lock (if recursive locking is eliminated),
         * or the object must be unlocked (if the object was rematerialized during deoptimization).
         * If the object is locked by another thread, lock elimination in the compiler has a serious
         * bug.
         *
         * Since this code must be uninterruptible, we cannot just call lock.tryLock() but instead
         * replicate that logic here by using only direct field accesses.
         */
        long currentThread = getCurrentThreadIdentity();
        long ownerThread = getState();
        if (ownerThread == 0) {
            boolean success = compareAndSetState(0, currentThread);
            VMError.guarantee(success && acquisitions == 1, "Could not re-lock object during deoptimization");
        } else {
            VMError.guarantee(ownerThread == currentThread, "Object that needs re-locking during deoptimization is already locked by another thread");

            acquisitions++;
            VMError.guarantee(acquisitions > 0, "Maximum lock count exceeded");
        }
    }

    /*
     * Everything below is related to ReentrantLock.Sync and would normally be encapsulated in a
     * static inner class of ReentrantLock. We removed this encapsulation and merged the code into
     * one class for performance and footprint reasons.
     */

    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long CONDITION_FIELD_OFFSET = U.objectFieldOffset(JavaMonitor.class, "condition");

    private JavaMonitorConditionObject condition;

    /**
     * We store the numeric identifier of the owner thread in the {@linkplain #setState
     * synchronization state} rather than using it for the number of acquisitions (recursive
     * entries) like {@code ReentrantLock} does. We use this field for the acquisition count
     * instead, which is accessed only by the thread holding the lock so that it does not need
     * {@code volatile} semantics. This also enables us to leave this field's value at 1 on release
     * so that the next thread acquiring the lock does not need to immediately write it.
     */
    private int acquisitions = 1;

    /** {@inheritDoc} */
    @Override
    protected long getAcquisitions() {
        return acquisitions;
    }

    // see ReentrantLock.NonFairSync.tryAcquire(int)
    @Override
    protected boolean tryAcquire(long acquires) {
        assert acquires > 0 && acquires == (int) acquires;
        // Do not expect to acquire the lock because this method is typically called after having
        // already failed to do so, except after conditional waiting.
        if (probability(NOT_FREQUENT_PROBABILITY, getState() == 0)) {
            if (probability(FREQUENT_PROBABILITY, compareAndSetState(0, getCurrentThreadIdentity()))) {
                assert acquisitions == 1;
                acquisitions = (int) acquires;
                return true;
            }
        }
        return false;
    }

    // see ReentrantLock.Sync.tryLock()
    private boolean tryLock() {
        long current = getCurrentThreadIdentity();
        long c = getState();
        if (c == 0) {
            if (compareAndSetState(0, current)) {
                assert acquisitions == 1;
                return true;
            }
        } else if (c == current) {
            int r = acquisitions + 1;
            if (r < 0) { // overflow
                throw new Error("Maximum lock count exceeded");
            }
            // Note: protected by monitor and not required to be observable, no ordering needed
            acquisitions = r;
            return true;
        }
        // Try one round of spinning before thread state changes, JFR events, etc.
        return trySpinAcquire(1, 1) == SPIN_SUCCESS;
    }

    // see ReentrantLock.Sync.tryRelease()
    @Override
    protected boolean tryRelease(long releases) {
        assert releases > 0;
        long current = getCurrentThreadIdentity();
        long c = getState();
        if (c != current) {
            throw new IllegalMonitorStateException();
        }
        boolean free = (acquisitions == releases);
        if (free) {
            acquisitions = 1;
            setState(0);
        } else {
            // Note: protected by monitor and not required to be observable, no ordering needed
            assert releases < acquisitions;
            acquisitions -= (int) releases;
        }
        return free;
    }

    // see ReentrantLock.Sync.isHeldExclusively()
    @Override
    protected boolean isHeldExclusively() {
        return getState() == getCurrentThreadIdentity();
    }

    // see ReentrantLock.Sync.isLocked()
    boolean isLocked() {
        return getState() != 0;
    }

    /**
     * Storing and comparing a {@link Thread} object to determine lock ownership needs heap address
     * computations and GC barriers, while we don't actually need to access the object, so we use a
     * unique numeric identifier instead. {@link IsolateThread} is readily available in a register,
     * which makes for compact fast path code, but if virtual threads are enabled, they migrate
     * between {@link IsolateThread}s, so we must use the unique ids assigned to {@link Thread}s.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static long getCurrentThreadIdentity() {
        return JavaThreads.getCurrentThreadId();
    }

    private static long getThreadIdentity(Thread thread) {
        return JavaThreads.getThreadId(thread);
    }

    boolean tryFastMonitorEnter() {
        long current = getCurrentThreadIdentity();
        long c = getState();
        if (probability(FREQUENT_PROBABILITY, c == 0)) {
            if (probability(FREQUENT_PROBABILITY, compareAndSetState(0, current))) {
                if (GraalDirectives.inIntrinsic()) {
                    ReplacementsUtil.dynamicAssert(acquisitions == 1, "acquisitions must have been reset to 1 on last exit");
                } else {
                    assert acquisitions == 1;
                }
                return true;
            }
        } else if (probability(NOT_LIKELY_PROBABILITY, c == current)) {
            // Note: protected by monitor and not required to be observable, no ordering needed
            int r = acquisitions + 1;
            if (probability(EXTREMELY_SLOW_PATH_PROBABILITY, r < 0)) {
                return false; // overflow: throw in slow-path
            }
            acquisitions = r;
            return true;
        }
        return false; // retry in slow-path where we might have to enqueue
    }

    /** @return one of the constants of {@link FastMonitorExitStatus}. */
    int tryFastBalancedMonitorExit() {
        // Note that JNI forbids exiting a monitor acquired with monitorenter and breaking balancing
        ReplacementsUtil.dynamicAssert(getState() == getCurrentThreadIdentity(), "compiler must enforce balanced monitor use");

        boolean free = (acquisitions == 1);
        if (probability(FREQUENT_PROBABILITY, free)) {
            setState(0);
        } else {
            // Note: protected by monitor and not required to be observable, no ordering needed
            acquisitions--;
        }

        if (free && hasReleaseSuccessor()) {
            return FastMonitorExitStatus.MUST_SIGNAL_SUCCESSOR;
        }
        return FastMonitorExitStatus.SUCCESS;
    }

    /**
     * Result of a {@link #tryFastBalancedMonitorExit} call. This is not a Java enum so that inlined
     * fast paths do not need heap address computations.
     */
    public interface FastMonitorExitStatus {
        /** Fast exit failed, invoke {@link #monitorExit} instead. */
        int USE_SLOW_PATH = -1;

        /** Fast exit succeeded. */
        int SUCCESS = 0;

        /** Fast exit succeeded, but must invoke {@link #signalReleaseSuccessor()} separately. */
        int MUST_SIGNAL_SUCCESSOR = 1;
    }

    /**
     * Creates a new {@link JavaMonitor} that is locked by the provided thread. This requires
     * patching of internal state.
     */
    public static JavaMonitor createLocked(Thread ownerThread, int acquisitions) {
        assert ownerThread != null;
        assert acquisitions > 0;

        long thread = getThreadIdentity(ownerThread);
        JavaMonitor monitor = new JavaMonitor();
        monitor.setState(thread);
        monitor.acquisitions = acquisitions;
        monitor.setJfrOwner(ownerThread);
        return monitor;
    }
}
