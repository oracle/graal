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

import com.oracle.truffle.espresso.impl.Stable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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
     */
    void await(long timeout) throws InterruptedException;

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
     * Creates a new {@code EspressoLock} instance.
     */
    static EspressoLock create() {
        return new EspressoLockImpl();
    }
}

final class EspressoLockImpl extends ReentrantLock implements EspressoLock {

    private static final long serialVersionUID = -2776792497346642438L;

    @Stable private volatile Condition waitCondition;

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

    @Override
    public void await(long timeout) throws InterruptedException {
        if (timeout == 0) {
            // Wait without timeout, NOT equivalent to await(0L, TimeUnit.MILLISECONDS);
            getWaitCondition().await();
        } else if (timeout > 0) {
            getWaitCondition().await(timeout, TimeUnit.MILLISECONDS);
        } else {
            throw new IllegalArgumentException();
        }
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
    public Condition newCondition() {
        // Disable arbitrary conditions.
        throw new UnsupportedOperationException();
    }
}
