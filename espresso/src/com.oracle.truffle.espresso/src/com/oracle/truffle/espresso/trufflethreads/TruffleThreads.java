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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.api.TruffleSafepoint.Interrupter;
import com.oracle.truffle.api.TruffleSafepoint.Interruptible;
import com.oracle.truffle.api.nodes.ControlFlowException;
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
 * {@link #create(Interrupter)}. The parameter is an interrupter that should implement the
 * language-specific behavior of an interruption.
 * </p>
 */
public interface TruffleThreads {
    /**
     * Creates an instance of this interface, using the given {@link Interrupter guestInterrupter}
     * parameter as the implementation of guest interruptions.
     */
    static TruffleThreads create(Interrupter guestInterrupter) {
        return new TruffleThreadsImpl(guestInterrupter);
    }

    /**
     * Enters a {@link Interruptible} operation on the current thread. This execution will have the
     * following properties:
     * <ul>
     * <li>{@link TruffleSafepoint safepoints} can still be handled.</li>
     * <li>Will throw a {@link GuestInterruptedException} if {@link #guestInterrupt(Thread)} was
     * called on this thread.</li>
     * </ul>
     *
     * @throws GuestInterruptedException if the current thread was guest-interrupted.
     */
    <T> void enterInterruptible(Interruptible<T> interruptible, Node location, T object) throws GuestInterruptedException;

    /**
     * Similar to {@link Thread#sleep(long)}, but with the semantics of
     * {@link #enterInterruptible(Interruptible, Node, Object)}.
     *
     * @param millis the length of time to sleep in milliseconds.
     * @param location the location with which the safepoint should be polled.
     * @throws GuestInterruptedException if the current thread was guest-interrupted.
     * @throws IllegalArgumentException if millis is negative.
     */
    void sleep(long millis, Node location) throws GuestInterruptedException;

    /**
     * Waits for the given thread to die. The waiting has the semantics of
     * {@link #enterInterruptible(Interruptible, Node, Object)}.
     * <p>
     * Note that this method is equivalent to {@code join(t, 0, location)}.
     *
     * @param location the location with which the safepoint should be polled.
     * @throws GuestInterruptedException if the given thread was guest-interrupted.
     */
    default void join(Thread t, Node location) throws GuestInterruptedException {
        join(t, 0, location);
    }

    /**
     * Waits at most {@code millis} milliseconds for this thread to die. The waiting has the
     * semantics of {@link #enterInterruptible(Interruptible, Node, Object)}.
     *
     * @param millis the length of time to sleep in milliseconds.
     * @param location the location with which the safepoint should be polled.
     * @throws GuestInterruptedException if the current thread was guest-interrupted.
     * @throws IllegalArgumentException if millis is negative.
     */
    void join(Thread t, long millis, Node location) throws GuestInterruptedException;

    /**
     * Interrupts the given thread with the privided {@linkplain #create(Interrupter) guest
     * interruption} semantics.
     *
     * @param t the thread to interrupt.
     */
    void guestInterrupt(Thread t);

    /**
     * Holds the same role as a {@link InterruptedException}, but for guest interruptions.
     */
    class GuestInterruptedException extends ControlFlowException {
        private static final long serialVersionUID = -3471443492081741698L;

        /*
         * TODO: maybe reference the context(s) that guest-interrupted this thread so languages may
         * ignore non-self interruptions ? Ideally, the semantics in guestInterrupt(t) should be
         * enough to detect this.
         */
    }
}

final class TruffleThreadsImpl implements TruffleThreads {
    public TruffleThreadsImpl(Interrupter guestInterrupter) {
        this.guestInterrupter = guestInterrupter;
    }

    // TODO: This is slow. One such interrupter is needed per (language, thread) pair.
    private final Map<Thread, TruffleThreadInterrupter> interrupters = new ConcurrentHashMap<>();

    private final Interrupter guestInterrupter;

    @Override
    @TruffleBoundary
    public <T> void enterInterruptible(Interruptible<T> interruptible, Node location, T object) {
        TruffleSafepoint safepoint = TruffleSafepoint.getCurrent();
        Thread current = Thread.currentThread();
        TruffleThreadInterrupter interrupter = getInterrupter(current);
        safepoint.setBlocked(location, interrupter, interruptible, object, null, interrupter::afterInterrupt);
    }

    private Interruptible<Long> sleepInterruptible() {
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
    public void sleep(long millis, Node location) {
        enterInterruptible(sleepInterruptible(), location, millis);
    }

    private Interruptible<Long> joinMillisInterruptible(Thread t) {
        return new Interruptible<Long>() {
            private final long start = System.currentTimeMillis();

            @Override
            public void apply(Long arg) throws InterruptedException {
                long millis = arg - (System.currentTimeMillis() - start);
                if (millis <= 0) {
                    return;
                }
                t.join(arg - (System.currentTimeMillis() - start));
            }
        };
    }

    @Override
    public void join(Thread t, long millis, Node location) {
        if (millis == 0) {
            enterInterruptible(Thread::join, location, t);
        } else if (millis > 0) {
            enterInterruptible(joinMillisInterruptible(t), location, millis);
        } else {
            throw new IllegalArgumentException();
        }
    }

    @Override
    public void guestInterrupt(Thread t) {
        getInterrupter(t).guestInterrupt();
    }

    @TruffleBoundary
    private TruffleThreadInterrupter getInterrupter(Thread t) {
        return interrupters.computeIfAbsent(t, TruffleThreadInterrupter::new);
    }

    public class TruffleThreadInterrupter implements Interrupter {
        private volatile boolean isGuest = false;
        private final Thread thread;

        public TruffleThreadInterrupter(Thread thread) {
            this.thread = thread;
        }

        @Override
        public void interrupt(Thread ignore) {
            assert ignore == thread;
            thread.interrupt();
        }

        @Override
        public void resetInterrupted() {
            Thread.interrupted();
        }

        public void afterInterrupt() {
            // TODO: compareAndSet.
            boolean guestInterrupted = isGuest;
            isGuest = false;
            if (guestInterrupted) {
                resetInterrupted();
                throw new GuestInterruptedException();
            }
        }

        public void guestInterrupt() {
            isGuest = true;
            guestInterrupter.interrupt(thread);
            thread.interrupt();
        }
    }
}
