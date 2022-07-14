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

import java.util.concurrent.locks.Condition;
import com.oracle.svm.core.annotate.Uninterruptible;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.misc.Unsafe;
import org.graalvm.nativeimage.CurrentIsolate;

import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.JavaMonitorEnterEvent;
import com.oracle.svm.core.monitor.JavaMonitorAbstractQueuedSynchronizer.JavaMonitorConditionObject;
import com.oracle.svm.core.util.VMError;
/**
 * JavaMonitor is derived from the ReentrantLock class in the jdk 19 sources.
 * Git commit hash: f640fc5a1eb876a657d0de011dcd9b9a42b88eec. JDK tag: jdk-19+30
 *
 * Only the methods required for substrateVM monitor support have been kept.
 * Additional functionality specific to substrateVM has been added.
 */
public class JavaMonitor {
    private final Sync sync;

    public static class Sync extends JavaMonitorAbstractQueuedSynchronizer {

        public JavaMonitorConditionObject javaMonitorConditionObject; // change to private?

        public JavaMonitorConditionObject getJavaMonitorConditionObject() {
            return javaMonitorConditionObject;
        }

        Sync() {
        }

        /**
         * Performs non-fair tryLock.
         */
        final boolean tryLock() {
            Thread current = Thread.currentThread();
            int c = getState();
            if (c == 0) {
                if (compareAndSetState(0, 1)) {
                    setExclusiveOwnerThread(current);
                    return true;
                }
            } else if (getExclusiveOwnerThread() == current) {
                if (++c < 0) { // overflow
                    throw new Error("Maximum lock count exceeded");
                }
                setState(c);
                return true;
            }
            return false;
        }

        final void lock() {
            boolean initialSuccess = initialTryLock();
            if (!initialSuccess) {
                acquire(1);
            }
        }

        @Override
        protected final boolean tryRelease(int releases) {
            int c = getState() - releases; // state must be 0 here
            if (getExclusiveOwnerThread() != Thread.currentThread()) {
                throw new IllegalMonitorStateException(); // owner is null and c =-1
            }
            boolean free = (c == 0);
            if (free) {
                setExclusiveOwnerThread(null);
            }
            setState(c);
            return free;
        }

        @Override
        protected final boolean isHeldExclusively() {
            // While we must in general read state before owner,
            // we don't need to do so to check if current thread is owner
            return getExclusiveOwnerThread() == Thread.currentThread();
        }

        final boolean isLocked() {
            return this.getState() != 0;
        }

        final JavaMonitorConditionObject newCondition() {
            return new JavaMonitorConditionObject();
        }

        boolean initialTryLock() {
            Thread current = Thread.currentThread();
            if (compareAndSetState(0, 1)) { // first attempt is unguarded
                setExclusiveOwnerThread(current);
                return true;
            } else if (getExclusiveOwnerThread() == current) {
                int c = getState() + 1;
                if (c < 0) { // overflow
                    throw new Error("Maximum lock count exceeded");
                }
                setState(c);
                return true;
            } else {
                return false;
            }
        }

        @Override
        protected boolean tryAcquire(int acquires) {
            if (getState() == 0 && compareAndSetState(0, acquires)) {
                setExclusiveOwnerThread(Thread.currentThread());
                return true;
            }
            return false;
        }
    }

    protected final Thread getExclusiveOwnerThread() {
        return sync.getExclusiveOwnerThread();
    }

    protected final void setExclusiveOwnerThread(Thread thread) {
        sync.setExclusiveOwnerThread(thread);
    }

    @Uninterruptible(reason = "called during deoptimization")
    public Sync getSync() {
        return sync;
    }

    public JavaMonitorConditionObject getCondition() {
        return sync.getJavaMonitorConditionObject();
    }

    public void setCondition(JavaMonitorConditionObject newCondition) {
        sync.javaMonitorConditionObject = newCondition;
    }

    public void lock() {
        sync.lock();
    }

    public boolean tryLock() {
        return sync.tryLock();
    }

    public void unlock() {
        sync.release(1);
    }

    public boolean isLocked() {
        return sync.isLocked();
    }

    public boolean isHeldByCurrentThread() {
        return sync.isHeldExclusively();
    }

    public Condition newCondition() {
        return sync.newCondition();
    }

    private long latestJfrTid;



    public JavaMonitor() {
        sync = new Sync();
        setCondition(MultiThreadedMonitorSupport.MONITOR_WITHOUT_CONDITION);
        latestJfrTid = 0;
    }

    /**
     * Creates a new {@link ReentrantLock} that is locked by the provided thread. This requires
     * patching of internal state, since there is no public API in {@link ReentrantLock} to do that
     * (for a good reason, because it is a highly unusual operation).
     */
    public static JavaMonitor newLockedMonitorForThread(Thread thread, int recursionDepth) {
        JavaMonitor result = new JavaMonitor();
        for (int i = 0; i < recursionDepth; i++) {
            result.lock();
        }

        result.latestJfrTid = SubstrateJVM.getThreadId(thread);
        assert result.getExclusiveOwnerThread() == Thread.currentThread() : "Must be locked by current thread";
        result.setExclusiveOwnerThread(thread);

        return result;
    }

    public void monitorEnter(Object obj) {
        if (!tryLock()) {
            long startTicks = JfrTicks.elapsedTicks();
            lock();
            JavaMonitorEnterEvent.emit(obj, latestJfrTid, startTicks);
        }

        latestJfrTid = SubstrateJVM.getThreadId(CurrentIsolate.getCurrentThread());
    }

    @Uninterruptible(reason = "called during deoptimization")
    public void doRelockObject(long offset) {

        /*
         * This code runs just before we are returning to the actual deoptimized frame. This means
         * that the thread either must already hold the lock (if recursive locking is eliminated),
         * or the object must be unlocked (if the object was rematerialized during deoptimization).
         * If the object is locked by another thread, lock elimination in the compiler has a serious
         * bug.
         */
        Thread currentThread = Thread.currentThread();
        Thread ownerThread = sync.getExclusiveOwnerThread();
        VMError.guarantee(ownerThread == null || ownerThread == currentThread, "Object that needs re-locking during deoptimization is already locked by another thread");
        /*
         * Since this code must be uninterruptible, we cannot just call lock.tryLock() but instead
         * replicate that logic here by using only direct field accesses.
         */
        int oldState = sync.getState();
        int newState = oldState + 1;
        VMError.guarantee(newState > 0, "Maximum lock count exceeded");

        boolean success = Unsafe.getUnsafe().compareAndSetInt(sync, offset, oldState, newState);
        VMError.guarantee(success, "Could not re-lock object during deoptimization");
        setExclusiveOwnerThread(currentThread);
    }

}
