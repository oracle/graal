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

import com.oracle.truffle.api.TruffleSafepoint;
import com.oracle.truffle.espresso.vm.UnsafeAccess;

/**
 * Provides the {@link TruffleThreads} with the representation of guest languages interruptions.
 * Only two behaviors are needed for this purpose:
 * <ul>
 * <li>{@link #guestInterrupt(Thread)} should provide the language specific way of making known a
 * given thread was interrupted by guest means.</li>
 * <li>{@link #isGuestInterrupted(Thread)} should provide a way of checking whether a thread was
 * guest-interrupted.</li>
 * </ul>
 * <p>
 * The contract is simply that, if {@link #isGuestInterrupted(Thread)} is called immediately after
 * (and the thread did not have time to process safepoints) {@link #guestInterrupt(Thread)}, then it
 * should return true. Also, the {@link #isGuestInterrupted(Thread)} method must not rely on the
 * host interrupted status (ie: {@link Thread#isInterrupted()}).
 * <p>
 * Note that the guest interrupted status is not cleared by the implementation. It is up to the
 * language implementor to clear it or not when observing an interruption.
 * <p>
 * The {@link #EMPTY} guest interrupter is provided for languages that do not have well-specified
 * interruption semantics. This interrupter can be used to still benefit from the safepoint-able
 * blocking methods of this package, but be aware that no early escape from the blocking operations
 * will be possible.
 */
public abstract class GuestInterrupter implements TruffleSafepoint.Interrupter {
    public static final GuestInterrupter EMPTY = new GuestInterrupter() {
        @Override
        public void guestInterrupt(Thread t) {
        }

        @Override
        public boolean isGuestInterrupted(Thread t) {
            return false;
        }
    };

    /**
     * Provides the semantics for making it known that a thread was interrupted by guest.
     * 
     * @param t the thread to interrupt
     */
    public abstract void guestInterrupt(Thread t);

    /**
     * Provides a check whether the given thread was {@linkplain #guestInterrupt(Thread)
     * guest-interrupted}.
     * 
     * @param t The thread whose interrupt status is to be checked.
     * @return true if the thread was guest interrupted, false otherwise.
     */
    public abstract boolean isGuestInterrupted(Thread t);

    final void afterInterrupt(Thread t) {
        if (isGuestInterrupted(t)) {
            // Needs to be unchecked throw (Runnable does not declare as a checked exception.)
            UnsafeAccess.get().throwException(new GuestInterruptedException());
        }
    }

    @Override
    public final void interrupt(Thread thread) {
        thread.interrupt();
    }

    @Override
    public final void resetInterrupted() {
        Thread.interrupted();
    }
}
