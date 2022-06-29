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

import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.CurrentIsolate;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.JavaMonitorEnterEvent;

public class JavaMonitor extends ReentrantLock {
    private static final long serialVersionUID = 3921577070627519721L;

    //Records the number of waiters that have not yet been notified
    private int waitingThreads = 0;
    /*
     * This queue is used to record the TIDs of notifiers so that waiters have access to them when they eventually
     * acquire the monitor lock and resume.
     * Adding and removing TIDs from this queue in a FIFO fashion is correct because Condition.signal(), notifies waiters in a FIFO fashion.
     * Once waiters are notified, they acquire the monitor lock in a FIFO fashion as well. This is because they are transferred
     * from the front of the condition queue to the back of the sync queue.
     * Ultimately, this means that waiters will wake up and resume in the same order that they are notified in.
     * */
    private LinkedList<Long> notifiers = new LinkedList<Long>();
    private long latestJfrTid;

    public long getNotifierTid() {
        assert isHeldByCurrentThread(); //make sure we hold the lock
        return notifiers.removeFirst();
    }

    public void addWaiter() {
        waitingThreads++;
    }

    public void setNotifier(boolean notifyAll) {
        //make sure we hold the lock
        assert isHeldByCurrentThread();

        //If there are extra notifications, there will be no waiters to respond to them, so don't record them.
        if (waitingThreads == 0) {
            return;
        }
        long curr = SubstrateJVM.get().getThreadId(CurrentIsolate.getCurrentThread());

        int notifications = 1;
        if (notifyAll) {
            notifications = waitingThreads;
        }


        assert notifications <= waitingThreads && waitingThreads > 0;

        for (int i = 0; i < notifications; i++) {
            notifiers.addLast(curr);
            waitingThreads--;
        }
    }

    public JavaMonitor() {
        Target_java_util_concurrent_locks_ReentrantLock lock = SubstrateUtil.cast(this, Target_java_util_concurrent_locks_ReentrantLock.class);
        Target_java_util_concurrent_locks_ReentrantLock_NonfairSync sync = SubstrateUtil.cast(lock.sync, Target_java_util_concurrent_locks_ReentrantLock_NonfairSync.class);
        sync.objectMonitorCondition = SubstrateUtil.cast(MultiThreadedMonitorSupport.MONITOR_WITHOUT_CONDITION, Target_java_util_concurrent_locks_AbstractQueuedSynchronizer_ConditionObject.class);
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
        Target_java_util_concurrent_locks_ReentrantLock lock = SubstrateUtil.cast(result, Target_java_util_concurrent_locks_ReentrantLock.class);
        Target_java_util_concurrent_locks_AbstractOwnableSynchronizer sync = SubstrateUtil.cast(lock.sync, Target_java_util_concurrent_locks_AbstractOwnableSynchronizer.class);

        assert sync.exclusiveOwnerThread == Thread.currentThread() : "Must be locked by current thread";
        sync.exclusiveOwnerThread = thread;

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
}
