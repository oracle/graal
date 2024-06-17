/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.Uninterruptible;

import jdk.internal.misc.Unsafe;

/**
 * This class holds thread-specific data that must be freed when the thread detaches. However, it
 * might not be possible to free the data right away because other threads could use the data
 * concurrently.
 * 
 * If a thread wants to access the {@link ThreadData} of another thread, it must explicitly call
 * {@link #acquire()} and {@link #release()} to ensure that the {@link ThreadData} can't be freed
 * unexpectedly.
 */
public final class ThreadData extends UnacquiredThreadData {
    private static final Unsafe U = Unsafe.getUnsafe();
    private static final long LOCK_OFFSET = U.objectFieldOffset(ThreadData.class, "lock");
    private static final long UNSAFE_PARK_EVENT_OFFSET = U.objectFieldOffset(ThreadData.class, "unsafeParker");
    private static final long SLEEP_PARK_EVENT_OFFSET = U.objectFieldOffset(ThreadData.class, "sleepParker");

    @SuppressWarnings("unused") private volatile int lock;
    private boolean detached;
    private long refCount;

    private volatile Parker unsafeParker;
    private volatile Parker sleepParker;

    /**
     * Returns the {@link Parker} for {@link Thread#sleep}. May return null, if the {@link Parker}
     * wasn't initialized yet. If this method is called to access the {@link Parker} of another
     * thread, then the returned value must not be used after {@link #release() releasing} the
     * {@link ThreadData}.
     */
    public Parker getSleepParker() {
        assert isForCurrentThread() || refCount > 0;
        return sleepParker;
    }

    /**
     * Returns the {@link Parker} for {@link Unsafe#park} and {@link Unsafe#unpark}. If this method
     * is called to access the {@link Parker} of another thread, then the returned value must not be
     * used after {@link #release() releasing} the {@link ThreadData}.
     */
    public Parker ensureUnsafeParker() {
        assert isForCurrentThread() || refCount > 0;

        Parker existingEvent = unsafeParker;
        if (existingEvent != null) {
            return existingEvent;
        }

        initializeParker(UNSAFE_PARK_EVENT_OFFSET, false);
        return unsafeParker;
    }

    /**
     * Returns the {@link Parker} for {@link Thread#sleep}. If this method is called to access the
     * {@link Parker} of another thread, then the returned value must not be used after
     * {@link #release() releasing} the {@link ThreadData}.
     */
    public Parker ensureSleepParker() {
        assert isForCurrentThread() || refCount > 0;

        Parker existingEvent = sleepParker;
        if (existingEvent != null) {
            return existingEvent;
        }

        initializeParker(SLEEP_PARK_EVENT_OFFSET, true);
        return sleepParker;
    }

    /**
     * Must be called if a thread wants to access the {@link ThreadData} of another thread. This
     * method increases the reference count and returns the {@link ThreadData} object if the data
     * can be accessed. It returns {@code null} if the data is no longer accessible because the
     * thread detached in the meanwhile.
     */
    @Override
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public ThreadData acquire() {
        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);
        try {
            if (detached) {
                return null;
            }
            assert refCount >= 0;
            refCount++;
            return this;
        } finally {
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    /** Decreases the reference count. The data may be freed if the reference count reaches zero. */
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public void release() {
        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);
        try {
            assert refCount > 0;
            refCount--;
            if (detached && refCount == 0) {
                free();
            }
        } finally {
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    /**
     * If the {@link ThreadData} is not used by any other threads, then this method frees the thread
     * data right away. Otherwise, it marks the data as ready to be freed so that it can be freed
     * once the reference count reaches zero.
     */
    @Override
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public void detach() {
        assert isForCurrentThread() || VMOperation.isInProgressAtSafepoint() : "may only be called by the detaching thread or at a safepoint";
        assert !detached : "may only be called once";

        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);
        try {
            detached = true;
            if (refCount == 0) {
                free();
            }
        } finally {
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void free() {
        assert JavaSpinLockUtils.isLocked(this, LOCK_OFFSET);
        if (unsafeParker != null) {
            unsafeParker.release();
            unsafeParker = null;
        }
        if (sleepParker != null) {
            sleepParker.release();
            sleepParker = null;
        }
    }

    private void initializeParker(long offset, boolean isSleepEvent) {
        Parker newEvent = Parker.acquire(isSleepEvent);
        if (!tryToStoreParker(offset, newEvent)) {
            newEvent.release();
        }
    }

    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private boolean tryToStoreParker(long offset, Parker newEvent) {
        JavaSpinLockUtils.lockNoTransition(this, LOCK_OFFSET);
        try {
            if (U.getReference(this, offset) != null) {
                return false;
            }
            U.putReferenceVolatile(this, offset, newEvent);
            return true;
        } finally {
            JavaSpinLockUtils.unlock(this, LOCK_OFFSET);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean isForCurrentThread() {
        return this == PlatformThreads.getCurrentThreadData();
    }
}

abstract class UnacquiredThreadData {
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract ThreadData acquire();

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public abstract void detach();
}
