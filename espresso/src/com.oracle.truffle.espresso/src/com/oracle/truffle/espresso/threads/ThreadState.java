/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.threads;

import static com.oracle.truffle.espresso.threads.ThreadState.Constants.THREAD_STATE_ALIVE;
import static com.oracle.truffle.espresso.threads.ThreadState.Constants.THREAD_STATE_ALIVE_REASON_MASK;
import static com.oracle.truffle.espresso.threads.ThreadState.Constants.THREAD_STATE_ALIVE_STATUS_MASK;
import static com.oracle.truffle.espresso.threads.ThreadState.Constants.THREAD_STATE_BLOCKED_ON_MONITOR_ENTER;
import static com.oracle.truffle.espresso.threads.ThreadState.Constants.THREAD_STATE_IN_NATIVE;
import static com.oracle.truffle.espresso.threads.ThreadState.Constants.THREAD_STATE_RESPONSIVE;
import static com.oracle.truffle.espresso.threads.ThreadState.Constants.THREAD_STATE_RUNNABLE;
import static com.oracle.truffle.espresso.threads.ThreadState.Constants.THREAD_STATE_TERMINATED;
import static com.oracle.truffle.espresso.threads.ThreadState.Constants.THREAD_STATE_WAITING;
import static com.oracle.truffle.espresso.threads.ThreadState.Constants.THREAD_STATE_WAITING_REASON_MASK;
import static com.oracle.truffle.espresso.threads.ThreadState.Constants.THREAD_STATE_WAITING_TIME_MASK;

import com.oracle.truffle.espresso.runtime.EspressoContext;

public enum ThreadState {
    BLOCKED(
                    Constants.THREAD_STATE_BLOCKED_ON_MONITOR_ENTER | Constants.THREAD_STATE_RESPONSIVE,
                    Constants.THREAD_STATE_ALIVE_REASON_MASK),
    TIMED_SLEEPING(
                    waitingBits(Constants.THREAD_STATE_SLEEPING, true),
                    Constants.THREAD_STATE_ALIVE_REASON_MASK),
    TIMED_OBJECT_WAIT(
                    waitingBits(Constants.THREAD_STATE_IN_OBJECT_WAIT, true),
                    Constants.THREAD_STATE_ALIVE_REASON_MASK),
    OBJECT_WAIT(
                    waitingBits(Constants.THREAD_STATE_IN_OBJECT_WAIT, false),
                    Constants.THREAD_STATE_ALIVE_REASON_MASK),
    TIMED_PARKED(
                    waitingBits(Constants.THREAD_STATE_PARKED, true),
                    Constants.THREAD_STATE_ALIVE_REASON_MASK),
    PARKED(
                    waitingBits(Constants.THREAD_STATE_PARKED, false),
                    Constants.THREAD_STATE_ALIVE_REASON_MASK),
    IN_ESPRESSO(
                    Constants.THREAD_STATE_RESPONSIVE,
                    Constants.THREAD_STATE_IN_NATIVE),
    IN_NATIVE(
                    Constants.THREAD_STATE_IN_NATIVE,
                    Constants.THREAD_STATE_RESPONSIVE);

    // Enum impl

    private final int addBits;
    private final int clearBitsMask;

    ThreadState(int addBits, int clearBits) {
        this.addBits = addBits;
        this.clearBitsMask = ~clearBits;
    }

    public int from(int old) {
        // Order of operations is important: Ensure we clear before adding.
        return (clearBitsMask & old) | addBits;
    }

    // Utilities

    public static boolean currentThreadInEspresso(EspressoContext context) {
        if (!context.isMainThreadCreated()) {
            // vm init
            return true;
        }
        if (context.getGuestThreadFromHost(Thread.currentThread()) == null) {
            return false;
        }
        return !context.getThreadAccess().isInNative(context.getCurrentPlatformThread());
    }

    public static boolean isAlive(int status) {
        return isSet(status, THREAD_STATE_ALIVE);
    }

    public static boolean isTerminated(int status) {
        return isSet(status, THREAD_STATE_TERMINATED);
    }

    public static boolean isRunnable(int status) {
        return isSet(status, THREAD_STATE_RUNNABLE);
    }

    public static boolean isWaiting(int status) {
        return isSet(status, THREAD_STATE_WAITING);
    }

    public static boolean isBlocked(int status) {
        return isSet(status, THREAD_STATE_BLOCKED_ON_MONITOR_ENTER);
    }

    public static boolean hasBlockingObject(int status) {
        return isSet(status, THREAD_STATE_BLOCKED_ON_MONITOR_ENTER | THREAD_STATE_WAITING);
    }

    public static boolean isResponsive(int status) {
        return isSet(status, THREAD_STATE_RESPONSIVE);
    }

    public static boolean isInNative(int status) {
        return isSet(status, THREAD_STATE_IN_NATIVE);
    }

    public static boolean isValidStatus(int status) {
        if (!oneOrZeroBitSet(status & THREAD_STATE_ALIVE_STATUS_MASK) ||
                        !oneOrZeroBitSet(status & THREAD_STATE_ALIVE_REASON_MASK) ||
                        !oneOrZeroBitSet(status & THREAD_STATE_WAITING_TIME_MASK) ||
                        !oneOrZeroBitSet(status & THREAD_STATE_WAITING_REASON_MASK)) {
            return false;
        }
        if (!isAlive(status)) {
            // If thread is dead, no other bit that terminated should be set
            return !isSet(status, ~THREAD_STATE_TERMINATED);
        }
        if (!isSet(status, ~THREAD_STATE_ALIVE_REASON_MASK)) {
            // Alive status, but no reason
            return false;
        }
        if (isWaiting(status)) {
            return isSet(status, THREAD_STATE_WAITING_TIME_MASK) && isSet(status, THREAD_STATE_WAITING_REASON_MASK);
        } else {
            return !isSet(status, THREAD_STATE_WAITING_TIME_MASK | THREAD_STATE_WAITING_REASON_MASK);
        }
    }

    // Internals

    private static boolean isSet(int status, int mask) {
        return (status & mask) != 0;
    }

    private static int waitingBits(int reason, boolean timed) {
        int timedBit = timed ? Constants.THREAD_STATE_WAITING_WITH_TIMEOUT : Constants.THREAD_STATE_WAITING_INDEFINITELY;
        return timedBit | reason | Constants.THREAD_STATE_WAITING | THREAD_STATE_RESPONSIVE;
    }

    private static boolean oneOrZeroBitSet(int status) {
        return (status & (status - 1)) == 0;
    }

    // Constants

    static final class DefaultStates {
        // Default thread state
        public static final int STATE_NEW = 0;
        // Valid initialization states
        public static final int DEFAULT_ATTACH_THREAD_STATE = THREAD_STATE_ALIVE | THREAD_STATE_RUNNABLE | THREAD_STATE_IN_NATIVE;
        public static final int DEFAULT_RUNNABLE_STATE = THREAD_STATE_ALIVE | THREAD_STATE_RUNNABLE;
        // Terminated state
        public static final int TERMINATED = THREAD_STATE_TERMINATED;
    }

    /**
     * These are the {@code JVMTI_*} thread state definitions.
     * <p>
     * See {@code jdk.internal.misc.VM#JVMTI_*} definitions.
     */
    static final class Constants {
        public static final int THREAD_STATE_ALIVE = 0x0001;
        public static final int THREAD_STATE_TERMINATED = 0x0002;

        public static final int THREAD_STATE_RUNNABLE = 0x0004;
        public static final int THREAD_STATE_WAITING = 0x0080;
        public static final int THREAD_STATE_BLOCKED_ON_MONITOR_ENTER = 0x0400;

        public static final int THREAD_STATE_WAITING_INDEFINITELY = 0x0010;
        public static final int THREAD_STATE_WAITING_WITH_TIMEOUT = 0x0020;

        public static final int THREAD_STATE_SLEEPING = 0x0040;
        public static final int THREAD_STATE_IN_OBJECT_WAIT = 0x0100;
        public static final int THREAD_STATE_PARKED = 0x0200;

        public static final int THREAD_STATE_SUSPENDED = 0x100000;
        public static final int THREAD_STATE_INTERRUPTED = 0x200000;
        public static final int THREAD_STATE_IN_NATIVE = 0x400000;

        public static final int THREAD_STATE_RESPONSIVE = 0x10000000;
        public static final int THREAD_STATE_VENDOR_2 = 0x20000000;
        public static final int THREAD_STATE_VENDOR_3 = 0x40000000;

        public static final int THREAD_STATE_ALIVE_STATUS_MASK = THREAD_STATE_ALIVE |
                        THREAD_STATE_TERMINATED;

        public static final int THREAD_STATE_ALIVE_REASON_MASK = THREAD_STATE_RUNNABLE |
                        THREAD_STATE_WAITING |
                        THREAD_STATE_BLOCKED_ON_MONITOR_ENTER;

        public static final int THREAD_STATE_WAITING_TIME_MASK = THREAD_STATE_WAITING_INDEFINITELY |
                        THREAD_STATE_WAITING_WITH_TIMEOUT;

        public static final int THREAD_STATE_WAITING_REASON_MASK = THREAD_STATE_SLEEPING |
                        THREAD_STATE_IN_OBJECT_WAIT |
                        THREAD_STATE_PARKED;
    }
}
