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

/*
GraalReentrantLock is derived from the ReentrantLock class in the jdk 19 sources. Only the methods required for
substrateVM monitor support have been kept. Additional functionality specific to substrateVM has been added.
 */
public class GraalReentrantLock {
    private static final long serialVersionUID = 7373984872572414699L;
    private final Sync sync;

    public abstract static class Sync extends GraalAbstractQueuedSynchronizer {
        private static final long serialVersionUID = -5179523762034025860L;
        public GraalConditionObject graalConditionObject; // change to private?

        public GraalConditionObject getGraalConditionObject() {
            return graalConditionObject;
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

        /**
         * Checks for reentrancy and acquires if lock immediately available under fair
         * vs nonfair rules. Locking methods perform initialTryLock check before
         * relaying to corresponding AQS acquire methods.
         */
        abstract boolean initialTryLock();


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

        final GraalConditionObject newCondition() {
            return new GraalConditionObject();
        }

    }

    protected final Thread getExclusiveOwnerThread() {
        return sync.getExclusiveOwnerThread1();
    }

    protected final void setExclusiveOwnerThread(Thread thread) {
        sync.setExclusiveOwnerThread1(thread);
    }

    @Uninterruptible(reason = "called during deoptimization")
    public Sync getSync() {
        return sync;
    }

    public GraalAbstractQueuedSynchronizer.GraalConditionObject getCondition() {
        return sync.getGraalConditionObject();
    }

    public void setCondition(GraalAbstractQueuedSynchronizer.GraalConditionObject newCondition) {
        sync.graalConditionObject = newCondition;
    }

    public static final class GraalNonfairSync extends GraalReentrantLock.Sync {
        private static final long serialVersionUID = 7316153563782823691L;
        GraalNonfairSync() {
        }

        @Override
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

    public GraalReentrantLock() {
        sync = new GraalReentrantLock.GraalNonfairSync();
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

}
