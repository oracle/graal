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

package com.oracle.truffle.espresso.trufflethreads;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.ThreadLocalAction;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.TruffleSafepoint.Interrupter;
import com.oracle.truffle.api.TruffleSafepoint.Interruptible;
import com.oracle.truffle.api.nodes.Node;

/**
 * TruffleThreads provides custom implementations of the common blocking methods from the
 * {@link java.lang.Thread} class which behaves as their original counterpart, save for two details:
 * <ul>
 * <li>These blocking operation can still handle {@link TruffleSafepoint safepoints}.</li>
 * <li>These operation will only be interrupted through the provided {@link #guestInterrupt(Thread)}
 * method, and throw a {@link GuestInterruptedException} instead of a
 * {@link java.lang.InterruptedException}.</li>
 * </ul>
 * <p>
 * Additionally, this class provides the {@link #enterInterruptible(Interruptible, Node, Object)}
 * which allows implementing custom blocking operations with the behavior described above.
 * </p>
 * <p>
 * A language meaning to use these capabilities must create an instance of this interface through
 * {@link #create(GuestInterrupter)}. The parameter is an interrupter that should implement the
 * language-specific behavior of an interruption.
 * </p>
 */
public interface TruffleThreads {
    /**
     * Creates an instance of this interface, using the given {@link Interrupter guestInterrupter}
     * parameter as how guest interruptions are made observable.
     * <p>
     * This implementation can be as simple as setting a boolean in the guest's representation of
     * the thread.
     * <p>
     * To benefit from the classes of this package, the guest implementations of thread
     * interruptions should call {@link #guestInterrupt(Thread)}. Coordination with truffle
     * safepoints and wake-ups of the thread are handled by the internals of this class. As such,
     * the guest interrupter need not call the host {@link Thread#interrupt()}.
     */
    static TruffleThreads create(GuestInterrupter guestInterrupter) {
        return new TruffleThreadsImpl(guestInterrupter);
    }

    /**
     * Enters a {@link Interruptible} operation on the current thread. This execution will have the
     * following properties:
     * <ul>
     * <li>{@link TruffleSafepoint safepoints} will still be handled.</li>
     * <li>Will throw a {@link GuestInterruptedException} if {@link #guestInterrupt(Thread)} was
     * called on this thread.</li>
     * </ul>
     * Furthermore, no host interruptions of the current thread will result in an
     * {@link InterruptedException}.
     * 
     * As such, there are only three ways to retrieve control from a call to this method:
     * <ul>
     * <li>The given {@linkplain Interruptible interruptible} naturally completes</li>
     * <li>{@link #guestInterrupt(Thread)} is called for this thread.</li>
     * <li>An {@link ThreadLocalAction action} was submitted to this thread, that throws an
     * exception that is not an {@link InterruptedException}.</li>
     * </ul>
     *
     * @throws GuestInterruptedException if the current thread was guest-interrupted.
     */
    <T> void enterInterruptible(Interruptible<T> interruptible, Node location, T object) throws GuestInterruptedException;

    /**
     * Same as {@link #enterInterruptible(Interruptible, Node, Object)}, but allows providing
     * something to execute before and/or after the thread is interrupted and safepoints are
     * processed.
     *
     * @throws GuestInterruptedException if the current thread was guest-interrupted.
     */
    <T> void enterInterruptible(Interruptible<T> interruptible, Node location, T object, Runnable beforeSafepoint, Runnable afterSafepoint) throws GuestInterruptedException;

    /**
     * Similar to {@link Thread#sleep(long)}, but with the semantics of
     * {@link #enterInterruptible(Interruptible, Node, Object)}, meaning that the current thread
     * will still handle {@linkplain TruffleSafepoint safepoints}.
     *
     * @param millis the length of time to sleep in milliseconds.
     * @param location the location with which the safepoint should be polled.
     * @throws GuestInterruptedException if the current thread was guest-interrupted.
     * @throws IllegalArgumentException if millis is negative.
     */
    void sleep(long millis, Node location) throws GuestInterruptedException;

    /**
     * Interrupts the given thread with the provided {@linkplain #create(GuestInterrupter) guest
     * interruption} semantics.
     *
     * @param t the thread to interrupt.
     */
    void guestInterrupt(Thread t);

}

final class TruffleThreadsImpl implements TruffleThreads {
    TruffleThreadsImpl(GuestInterrupter guestInterrupter) {
        this.guestInterrupter = guestInterrupter;
    }

    private final GuestInterrupter guestInterrupter;

    @Override
    @TruffleBoundary
    public <T> void enterInterruptible(Interruptible<T> interruptible, Node location, T object) throws GuestInterruptedException {
        TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();
        Thread current = Thread.currentThread();
        safepoint.setBlocked(location, guestInterrupter, interruptible, object, null, () -> guestInterrupter.afterInterrupt(current));
    }

    @Override
    @TruffleBoundary
    public <T> void enterInterruptible(Interruptible<T> interruptible, Node location, T object, Runnable beforeSafepoint, Runnable afterSafepoint) throws GuestInterruptedException {
        TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();
        Thread current = Thread.currentThread();
        safepoint.setBlocked(location, guestInterrupter, interruptible, object, beforeSafepoint, () -> {
            if (afterSafepoint != null) {
                afterSafepoint.run();
            }
            guestInterrupter.afterInterrupt(current);
        });
    }

    private static Interruptible<Long> sleepInterruptible() {
        return new Interruptible<Long>() {
            private final long start = System.currentTimeMillis();

            @Override
            public void apply(Long arg) throws InterruptedException {
                long millis = arg - (System.currentTimeMillis() - start);
                if (millis <= 0) {
                    return;
                }
                Thread.sleep(millis);
            }
        };
    }

    @Override
    public void sleep(long millis, Node location) throws GuestInterruptedException {
        enterInterruptible(sleepInterruptible(), location, millis);
    }

    @Override
    public void guestInterrupt(Thread t) {
        guestInterrupter.guestInterrupt(t);
        t.interrupt();
    }
}
