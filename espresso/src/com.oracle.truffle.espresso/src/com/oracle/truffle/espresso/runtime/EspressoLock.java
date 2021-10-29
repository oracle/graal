/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.runtime;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.espresso.impl.SuppressFBWarnings;

/**
 * Lock implementation for guest objects. Provides a similar interface to {@link Object} built-in
 * monitor locks.
 */
public interface EspressoLock extends Lock {

    /**
     * Causes the current thread to wait until either another thread invokes the
     * {@link EspressoLock#signal()} method or the {@link EspressoLock#signalAll()} method for this
     * object, or a specified amount of time has elapsed.
     *
     * <p>
     * The current thread must own this object's monitor.
     * <p>
     * Analogous to the {@link Object#wait(long)} method for built-in monitor locks.
     *
     * @param timeout the maximum time to wait in milliseconds. {@code false} if the waiting time
     *            detectably elapsed before return from the method, else {@code true}
     * @throws IllegalArgumentException if the value of timeout is negative.
     * @throws IllegalMonitorStateException if the current thread is not the owner of the object's
     *             monitor.
     * @throws InterruptedException if any thread interrupted the current thread before or while the
     *             current thread was waiting for a notification. The <i>interrupted status</i> of
     *             the current thread is cleared when this exception is thrown.
     */
    boolean await(long timeout) throws InterruptedException;

    /**
     * Wakes up one waiting thread.
     *
     * <p>
     * If any threads are waiting on this condition then one is selected for waking up. That thread
     * must then re-acquire the lock before returning from {@code await}.
     *
     * <p>
     * Analogous to the {@link Object#notify()} method for built-in monitor locks.
     */
    void signal();

    /**
     * Wakes up all waiting threads.
     *
     * <p>
     * If any threads are waiting on this condition then they are all woken up. Each thread must
     * re-acquire the lock before it can return from {@code await}.
     *
     * <p>
     * Analogous to the {@link Object#notifyAll()} method for built-in monitor locks.
     */
    void signalAll();

    /**
     * Queries if this lock is held by the current thread.
     * 
     * <p>
     * Analogous to the {@link Thread#holdsLock(Object)} method for built-in monitor locks.
     */
    boolean isHeldByCurrentThread();

    /**
     * Returns the thread that currently owns this lock, or {@code null} if not owned. When this
     * method is called by a thread that is not the owner, the return value reflects a best-effort
     * approximation of current lock status. For example, the owner may be momentarily {@code null}
     * even if there are threads trying to acquire the lock but have not yet done so.
     *
     * @return the owner, or {@code null} if not owned
     */
    Thread getOwnerThread();

    /**
     * Creates a new {@code EspressoLock} instance.
     */
    static EspressoLock create() {
        return new EspressoLockImpl();
    }

    /**
     * Exposes the underlying lock heldCount.
     *
     * @return the entry count
     */
    int getEntryCount();
}

final class EspressoLockImpl extends ReentrantLock implements EspressoLock {

    private static final long serialVersionUID = -2776792497346642438L;

    private volatile Condition waitCondition;

    @SuppressFBWarnings(value = "JLM_JSR166_LOCK_MONITORENTER", justification = "Espresso runtime method.")
    private Condition getWaitCondition() {
        Condition cond = waitCondition;
        if (cond == null) {
            synchronized (this) {
                cond = waitCondition;
                if (cond == null) {
                    waitCondition = cond = super.newCondition();
                }
            }
        }
        return cond;
    }

    @SuppressFBWarnings(value = "WA_AWAIT_NOT_IN_LOOP", justification = "Espresso runtime method.")
    @Override
    public boolean await(long timeout) throws InterruptedException {
        if (timeout == 0) {
            // Wait without timeout, NOT equivalent to await(0L, TimeUnit.MILLISECONDS);
            getWaitCondition().await();
        } else if (timeout > 0) {
            return getWaitCondition().await(timeout, TimeUnit.MILLISECONDS);
        } else {
            throw new IllegalArgumentException();
        }
        return false;
    }

    @Override
    public void signal() {
        getWaitCondition().signal();
    }

    @Override
    public void signalAll() {
        getWaitCondition().signalAll();
    }

    @Override
    public Thread getOwnerThread() {
        return getOwner();
    }

    @Override
    public Condition newCondition() {
        // Disable arbitrary conditions.
        throw new UnsupportedOperationException();
    }

    @Override
    public int getEntryCount() {
        return getHoldCount();
    }
}
