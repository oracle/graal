/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.espresso.blocking;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.impl.SuppressFBWarnings;

/**
 * Lock implementation for guest objects. Provides a similar interface to {@link Object} built-in
 * monitor locks, along with some bookkeeping.
 */
public interface EspressoLock {

    /**
     * Creates a new {@code EspressoLock} instance.
     */
    @TruffleBoundary // ReentrantLock.<init> blacklisted by SVM
    static EspressoLock create(BlockingSupport<?> blockingSupport) {
        return new EspressoLockImpl(blockingSupport);
    }

    /**
     * Acquires the lock.
     * <p>
     * Acquires the lock if it is not held by another thread and returns immediately, setting the
     * lock hold count to one.
     * <p>
     * If the current thread already holds the lock then the hold count is incremented by one and
     * the method returns immediately.
     * <p>
     * If the lock is held by another thread then the current thread becomes disabled for thread
     * scheduling purposes and lies dormant until the lock has been acquired, at which time the lock
     * hold count is set to one. During this, the thread will still handle {@link TruffleSafepoint
     * safepoints}.
     */
    void lock();

    /**
     * Acquires the lock only if it is not held by another thread at the time of invocation.
     * <p>
     * Acquires the lock if it is not held by another thread and returns immediately with the value
     * {@code true}, setting the lock hold count to one.
     * <p>
     * If the current thread already holds this lock then the hold count is incremented by one and
     * the method returns {@code true}.
     * <p>
     * If the lock is held by another thread then this method will return immediately with the value
     * {@code false}.
     *
     * @return {@code true} if the lock was free and was acquired by the current thread, or the lock
     *         was already held by the current thread; and {@code false} otherwise
     */
    boolean tryLock();

    /**
     * Acquires the lock unless the current thread is
     * {@linkplain BlockingSupport#guestInterrupt(Thread, Object)} guest-interrupted}.
     * <p>
     * Acquires the lock if it is not held by another thread and returns immediately, setting the
     * lock hold count to one.
     * <p>
     * If the current thread already holds this lock then the hold count is incremented by one and
     * the method returns immediately.
     * <p>
     * If the lock is held by another thread then the current thread becomes disabled for thread
     * scheduling purposes but still answers to {@link com.oracle.truffle.api.TruffleSafepoint
     * safepoints} and lies dormant until one of two things happens:
     * <ul>
     * <li>The lock is acquired by the current thread; or
     * <li>Some other thread {@linkplain BlockingSupport#guestInterrupt(Thread, Object)}
     * guest-interrupts} the current thread.
     * </ul>
     * <p>
     * If the lock is acquired by the current thread then the lock hold count is set to one.
     * <p>
     * If the current thread:
     * <ul>
     * <li>has its guest-interrupted status set on entry to this method; or
     * <li>is {@linkplain BlockingSupport#guestInterrupt(Thread, Object)} guest-interrupted}} while
     * acquiring the lock,
     * </ul>
     * then {@link GuestInterruptedException} is thrown. There is no particular handling of the
     * thread interrupted status. It is up to the implementor to determine whether to clear the
     * guest interrupt status if it observes the {@link GuestInterruptedException}.
     *
     * @throws GuestInterruptedException if the current thread is guest-interrupted
     */
    void lockInterruptible() throws GuestInterruptedException;

    /**
     * Attempts to release this lock.
     * <p>
     * If the current thread is the holder of this lock then the hold count is decremented. If the
     * hold count is now zero then the lock is released. If the current thread is not the holder of
     * this lock then {@link IllegalMonitorStateException} is thrown.
     *
     * @throws IllegalMonitorStateException if the current thread does not hold this lock
     */
    void unlock();

    /**
     * Causes the current thread to wait until either another thread invokes the
     * {@link EspressoLock#signal()} method or the {@link EspressoLock#signalAll()} method for this
     * object, or a specified amount of time has elapsed.
     * <p>
     * The current thread must own this object's monitor.
     * <p>
     * Analogous to the {@link Object#wait(long)} method for built-in monitor locks.
     *
     * @param timeout the maximum time to wait in milliseconds. The value {@code 0} means to wait
     *            indefinitely.
     * @return {@code false} if the waiting time detectably elapsed before return from the method,
     *         else {@code true}
     * @throws IllegalArgumentException if the value of timeout is negative.
     * @throws IllegalMonitorStateException if the current thread is not the owner of the lock.
     * @throws GuestInterruptedException if any thread guest-interrupted the current thread before
     *             or while the current thread was waiting for a notification.
     */
    default boolean await(long timeout) throws GuestInterruptedException {
        return await(timeout, TimeUnit.MILLISECONDS);
    }

    /**
     * Causes the current thread to wait until either another thread invokes the
     * {@link EspressoLock#signal()} method or the {@link EspressoLock#signalAll()} method for this
     * object, or a specified amount of time has elapsed.
     * <p>
     * The current thread must own this object's monitor.
     * <p>
     * Analogous to the {@link Object#wait(long)} method for built-in monitor locks.
     *
     * @param timeout the maximum time to wait. The value {@code 0} means to wait indefinitely.
     * @param unit the time unit the given timeout is in.
     * @return {@code false} if the waiting time detectably elapsed before return from the method,
     *         else {@code true}
     * @throws IllegalArgumentException if the value of timeout is negative.
     * @throws IllegalMonitorStateException if the current thread is not the owner of the lock.
     * @throws GuestInterruptedException if any thread guest-interrupted the current thread before
     *             or while the current thread was waiting for a notification.
     */
    boolean await(long timeout, TimeUnit unit) throws GuestInterruptedException;

    /**
     * Causes the current thread to wait until it is signalled or guest interrupted, or the
     * specified deadline elapses.
     *
     * <p>
     * The lock is released and the current thread becomes disabled for thread scheduling purposes
     * and lies dormant until <em>one</em> of five things happens:
     * <ul>
     * <li>Some other thread invokes the {@link #signal} method for this {@code EspressoLock} and
     * the current thread happens to be chosen as the thread to be awakened; or
     * <li>Some other thread invokes the {@link #signalAll} method for this {@code EspressoLock}; or
     * <li>Some other thread {@linkplain GuestInterrupter#guestInterrupt(Thread, Object) guest
     * interrupted} the current thread; or
     * <li>The specified deadline elapses; or
     * <li>A &quot;<em>spurious wakeup</em>&quot; occurs.
     * </ul>
     *
     * <p>
     * In all cases, before this method can return the current thread must re-acquire the lock
     * associated with this condition. When the thread returns it is <em>guaranteed</em> to hold
     * this lock.
     *
     * <p>
     * If the current thread:
     * <ul>
     * <li>is {@linkplain GuestInterrupter#guestInterrupt(Thread, Object) guest interrupted} on
     * entry to this method; or
     * <li>is {@linkplain GuestInterrupter#guestInterrupt(Thread, Object) guest interrupted} while
     * waiting,
     * </ul>
     * then {@link GuestInterruptedException} is thrown. It is not specified, in the first case,
     * whether or not the test for interruption occurs before the lock is released.
     *
     * @param deadline the absolute time to wait until
     * @return {@code false} if the deadline has elapsed upon return, else {@code true}
     * @throws IllegalMonitorStateException if the current thread is not the owner of the lock.
     * @throws GuestInterruptedException if the current thread is guest interrupted.
     */
    boolean awaitUntil(Date deadline) throws GuestInterruptedException;

    /**
     * Wakes up one waiting thread.
     * <p>
     * If any threads are waiting on this condition then one is selected for waking up. That thread
     * must then re-acquire the lock before returning from {@code await}.
     * <p>
     * Analogous to the {@link Object#notify()} method for built-in monitor locks.
     */
    void signal();

    /**
     * Wakes up all waiting threads.
     * <p>
     * If any threads are waiting on this condition then they are all woken up. Each thread must
     * re-acquire the lock before it can return from {@code await}.
     * <p>
     * Analogous to the {@link Object#notifyAll()} method for built-in monitor locks.
     */
    void signalAll();

    /**
     * Queries if this lock is held by the current thread.
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
     * Exposes the underlying lock heldCount.
     *
     * @return the entry count
     */
    int getEntryCount();
}

/**
 * {@link EspressoLock} is not a final class to hide the {@link ReentrantLock} implementation.
 */
final class EspressoLockImpl extends ReentrantLock implements EspressoLock {

    private static final Node dummy = new Node() {
        @Override
        public boolean isAdoptable() {
            return false;
        }
    };
    private static final long serialVersionUID = -2776792497346642438L;

    EspressoLockImpl(BlockingSupport<?> blockingSupport) {
        assert blockingSupport != null;
        this.blockingSupport = blockingSupport;
    }

    private final BlockingSupport<?> blockingSupport;
    private volatile Condition waitCondition;
    private int waiters = 0;
    private int signals = 0;

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

    @Override
    public void lock() {
        TruffleSafepoint.setBlockedThreadInterruptible(dummy, EspressoLockImpl::doLock, this);
    }

    @Override
    @TruffleBoundary // ReetrantLock.unlock() blacklisted by SVM
    public void unlock() {
        super.unlock();
    }

    @Override
    public void lockInterruptible() throws GuestInterruptedException {
        blockingSupport.enterBlockingRegion(EspressoLockImpl::doLock, dummy, this);
    }

    @Override
    @Deprecated
    public void lockInterruptibly() {
        throw new UnsupportedOperationException("lockInterruptibly unsupported for EspressoLock. Use lockInterruptible instead.");
    }

    @Override
    public boolean await(long timeout, TimeUnit unit) throws GuestInterruptedException {
        if (timeout < 0) {
            throw new IllegalArgumentException();
        }
        if (!isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException();
        }
        WaitInterruptible interruptible = new WaitInterruptible(timeout, unit);
        enterWaitInterruptible(interruptible);
        return interruptible.getResult();
    }

    @Override
    public boolean awaitUntil(Date deadline) throws GuestInterruptedException {
        if (!isHeldByCurrentThread()) {
            throw new IllegalMonitorStateException();
        }
        WaitUntilInterruptible interruptible = new WaitUntilInterruptible(deadline);
        enterWaitInterruptible(interruptible);
        return interruptible.getResult();
    }

    @Override
    @TruffleBoundary
    public void signal() {
        if (isHeldByCurrentThread()) {
            signals = Math.min(signals + 1, waiters);
        }
        // Will throw if not held;
        getWaitCondition().signal();
    }

    @Override
    @TruffleBoundary
    public void signalAll() {
        if (isHeldByCurrentThread()) {
            signals = waiters;
        }
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

    @TruffleBoundary
    private void doLock() throws InterruptedException {
        if (!tryLock()) { // Bypass immediate interruption check done before locking
            super.lockInterruptibly();
        }
    }

    private void enterWaitInterruptible(InterruptibleWithBooleanResult<EspressoLockImpl> interruptible) throws GuestInterruptedException {
        waiters++;
        try {
            blockingSupport.enterBlockingRegion(interruptible, dummy, this,
                            /*
                             * Upon being notified to safepoint, control is returned after lock has
                             * been acquired. We must unlock it, allowing other thread to process
                             * safepoints while we process ours.
                             */
                            this::unlock,
                            /*
                             * Since we unlocked to allow other threads to process safepoints, we
                             * must re-lock ourselves. If this lock was woken up by a signal, then a
                             * Signaled exception is thrown.
                             */
                            this::afterSafepointForWait);
        } catch (Signaled e) {
            e.maybeRethrow();
        } catch (Throwable e) {
            /*
             * Either GuestInterruptedException or an exception thrown by a safepoint. Since at that
             * point, we are still considered in waiting, we may have missed a signal.
             */
            consumeSignal();
            throw e;
        } finally {
            waiters--;
        }
    }

    private boolean consumeSignal() {
        assert isHeldByCurrentThread();
        if (signals > 0) {
            signals--;
            return true;
        }
        return false;
    }

    private void afterSafepointForWait(Throwable t) {
        lock();
        if (consumeSignal()) {
            /* Another thread might have signaled while processing safepoints. */
            throw new Signaled(t);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Throwable> RuntimeException sneakyThrow(Throwable ex) throws T {
        throw (T) ex;
    }

    private static final class Signaled extends RuntimeException {
        private static final long serialVersionUID = 8504030520147416891L;
        private final Throwable safepointThrown;

        Signaled(Throwable t) {
            this.safepointThrown = t;
        }

        void maybeRethrow() {
            if (safepointThrown != null) {
                throw sneakyThrow(safepointThrown);
            }
        }

        @Override
        @SuppressWarnings("sync-override")
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    private abstract static class InterruptibleWithBooleanResult<T> implements TruffleSafepoint.Interruptible<T> {
        private boolean result;

        public final boolean getResult() {
            return result;
        }

        public final void setResult(boolean result) {
            this.result = result;
        }
    }

    private static final class WaitInterruptible extends InterruptibleWithBooleanResult<EspressoLockImpl> {
        WaitInterruptible(long timeout, TimeUnit unit) {
            this.nanoTimeout = unit.toNanos(timeout);
            this.start = System.nanoTime();
            setResult(true);
        }

        private final long nanoTimeout;
        private final long start;

        @Override
        @SuppressFBWarnings(value = "WA_AWAIT_NOT_IN_LOOP", justification = "Espresso lock runtime method.")
        public void apply(EspressoLockImpl lock) throws InterruptedException {
            if (nanoTimeout == 0L) {
                lock.getWaitCondition().await();
            } else {
                long left = nanoTimeout - (System.nanoTime() - start);
                if (left <= 0) {
                    return; // fully waited.
                }
                setResult(lock.getWaitCondition().await(left, TimeUnit.NANOSECONDS));
            }
        }
    }

    private static final class WaitUntilInterruptible extends InterruptibleWithBooleanResult<EspressoLockImpl> {
        WaitUntilInterruptible(Date date) {
            this.date = date;
            setResult(true);
        }

        private final Date date;

        @Override
        @SuppressFBWarnings(value = "WA_AWAIT_NOT_IN_LOOP", justification = "Espresso lock runtime method.")
        public void apply(EspressoLockImpl lock) throws InterruptedException {
            setResult(lock.getWaitCondition().awaitUntil(date));
        }
    }
}
