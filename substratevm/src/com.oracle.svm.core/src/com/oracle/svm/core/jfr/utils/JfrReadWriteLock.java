/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.utils;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jdk.UninterruptibleUtils;
import org.graalvm.compiler.nodes.PauseNode;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.jfr.SubstrateJVM;

/** An uninterruptible read-write lock implementation using atomics with writer preference. */
public class JfrReadWriteLock {
    private static final long CURRENTLY_WRITING = Long.MAX_VALUE;
    private final UninterruptibleUtils.AtomicLong ownerCount;
    private final UninterruptibleUtils.AtomicLong waitingWriters;
    private volatile long writeOwnerTid; // If this is set, then a writer owns the lock. Otherwise
                                         // -1.

    public JfrReadWriteLock() {
        ownerCount = new UninterruptibleUtils.AtomicLong(0);
        waitingWriters = new UninterruptibleUtils.AtomicLong(0);
        writeOwnerTid = -1;
    }

    @Uninterruptible(reason = "This method does not do a transition, so the whole critical section must be uninterruptible.", callerMustBe = true)
    public void readLockNoTransition() {
        readTryLock(Integer.MAX_VALUE);
    }

    @Uninterruptible(reason = "This method does not do a transition, so the whole critical section must be uninterruptible.", callerMustBe = true)
    public void writeLockNoTransition() {
        writeTryLock(Integer.MAX_VALUE);
    }

    /**
     * The bias towards writers does NOT ensure that there are no waiting writers at the time a
     * reader enters the critical section. Readers only make a best-effort check there are no
     * waiting writers before they attempt to acquire the lock to prevent writer starvation.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void readTryLock(int retries) {
        int yields = 0;
        for (int i = 0; i < retries; i++) {
            long readers = ownerCount.get();
            // Only begin the attempt to enter the critical section if no writers are waiting or
            // writes are in-progress.
            if (waitingWriters.get() > 0 || readers == CURRENTLY_WRITING) {
                yields = maybeYield(i, yields);
            } else {
                // Attempt to take the lock.
                if (ownerCount.compareAndSet(readers, readers + 1)) {
                    return;
                }
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void writeTryLock(int retries) {
        // Increment the writer count to signal our intent to acquire the lock.
        waitingWriters.incrementAndGet();
        try {
            int yields = 0;
            for (int i = 0; i < retries; i++) {
                long readers = ownerCount.get();
                // Only enter the critical section if all in-progress readers have finished.
                if (readers != 0) {
                    yields = maybeYield(i, yields);
                } else {
                    // Attempt to acquire the lock.
                    if (ownerCount.compareAndSet(0, CURRENTLY_WRITING)) {
                        writeOwnerTid = SubstrateJVM.getCurrentThreadId();
                        return;
                    }
                }
            }
        } finally {
            // Regardless of whether we eventually acquired the lock, signal we are done waiting.
            long waiters = waitingWriters.decrementAndGet();
            assert waiters >= 0;
        }
    }

    /**
     * This is essentially the same logic as in
     * {@link com.oracle.svm.core.thread.JavaSpinLockUtils#tryLock(Object, long, int)}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static int maybeYield(int retryCount, int yields) {
        if ((retryCount & 0xff) == 0 && VMThreads.singleton().supportsNativeYieldAndSleep()) {
            if (yields > 5) {
                VMThreads.singleton().nativeSleep(1);
            } else {
                VMThreads.singleton().yield();
                return yields + 1;
            }
        } else {
            PauseNode.pause();
        }
        return yields;
    }

    @Uninterruptible(reason = "Used in locking without transition, so the whole critical section must be uninterruptible.", callerMustBe = true)
    public void unlock() {
        if (writeOwnerTid < 0) {
            // Readers own the lock.
            long readerCount = ownerCount.decrementAndGet();
            assert readerCount >= 0;
            return;
        }
        // A writer owns the lock.
        assert isCurrentThreadWriteOwner();
        writeOwnerTid = -1;
        ownerCount.set(0);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isCurrentThreadWriteOwner() {
        return writeOwnerTid == SubstrateJVM.getCurrentThreadId();
    }
}
