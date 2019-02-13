/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

public class ThreadStatus {

    /*
     * Translations from src/share/javavm/export/jvmti.h
     */

    static final int JVMTI_THREAD_STATE_ALIVE = 0x0001;
    static final int JVMTI_THREAD_STATE_TERMINATED = 0x0002;
    static final int JVMTI_THREAD_STATE_RUNNABLE = 0x0004;
    static final int JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER = 0x0400;
    static final int JVMTI_THREAD_STATE_WAITING = 0x0080;
    static final int JVMTI_THREAD_STATE_WAITING_INDEFINITELY = 0x0010;
    static final int JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT = 0x0020;
    static final int JVMTI_THREAD_STATE_SLEEPING = 0x0040;
    static final int JVMTI_THREAD_STATE_IN_OBJECT_WAIT = 0x0100;
    static final int JVMTI_THREAD_STATE_PARKED = 0x0200;
    static final int JVMTI_THREAD_STATE_SUSPENDED = 0x100000;
    static final int JVMTI_THREAD_STATE_INTERRUPTED = 0x200000;
    static final int JVMTI_THREAD_STATE_IN_NATIVE = 0x400000;
    static final int JVMTI_THREAD_STATE_VENDOR_1 = 0x10000000;
    static final int JVMTI_THREAD_STATE_VENDOR_2 = 0x20000000;
    static final int JVMTI_THREAD_STATE_VENDOR_3 = 0x40000000;

    /*
     * Translations from src/share/vm/classfile/javaClasses.hpp
     */

    /** New. */
    public static final int NEW = 0;

    /** Runnable / Running. */
    public static final int RUNNABLE = JVMTI_THREAD_STATE_ALIVE +
                    JVMTI_THREAD_STATE_RUNNABLE;

    /** {@link Thread#sleep}. */
    public static final int SLEEPING = JVMTI_THREAD_STATE_ALIVE +
                    JVMTI_THREAD_STATE_WAITING +
                    JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT +
                    JVMTI_THREAD_STATE_SLEEPING;

    /** {@link Object#wait()}. */
    public static final int IN_OBJECT_WAIT = JVMTI_THREAD_STATE_ALIVE +
                    JVMTI_THREAD_STATE_WAITING +
                    JVMTI_THREAD_STATE_WAITING_INDEFINITELY +
                    JVMTI_THREAD_STATE_IN_OBJECT_WAIT;

    /** {@link Object#wait(long)}. */
    public static final int IN_OBJECT_WAIT_TIMED = JVMTI_THREAD_STATE_ALIVE +
                    JVMTI_THREAD_STATE_WAITING +
                    JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT +
                    JVMTI_THREAD_STATE_IN_OBJECT_WAIT;

    /** {@link sun.misc.Unsafe#park}. */
    public static final int PARKED = JVMTI_THREAD_STATE_ALIVE +
                    JVMTI_THREAD_STATE_WAITING +
                    JVMTI_THREAD_STATE_WAITING_INDEFINITELY +
                    JVMTI_THREAD_STATE_PARKED;

    /** {@link sun.misc.Unsafe#park}. */
    public static final int PARKED_TIMED = JVMTI_THREAD_STATE_ALIVE +
                    JVMTI_THREAD_STATE_WAITING +
                    JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT +
                    JVMTI_THREAD_STATE_PARKED;

    /** (re-)entering a synchronization block. */
    public static final int BLOCKED_ON_MONITOR_ENTER = JVMTI_THREAD_STATE_ALIVE +
                    JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER;

    public static final int TERMINATED = JVMTI_THREAD_STATE_TERMINATED;

    /** Debugging. */
    public static final String toString(int threadStatus) {
        switch (threadStatus) {
            case NEW:
                return "ThreadStatus.NEW";
            case RUNNABLE:
                return "ThreadStatus.RUNNABLE";
            case SLEEPING:
                return "ThreadStatus.SLEEPING";
            case IN_OBJECT_WAIT:
                return "ThreadStatus.IN_OBJECT_WAIT";
            case IN_OBJECT_WAIT_TIMED:
                return "ThreadStatus.IN_OBJECT_WAIT_TIMED";
            case PARKED:
                return "ThreadStatus.PARKED";
            case PARKED_TIMED:
                return "ThreadStatus.PARKED_TIMED";
            case BLOCKED_ON_MONITOR_ENTER:
                return "ThreadStatus.BLOCKED_ON_MONITOR_ENTER";
            default:
                return "ThreadStatus unknown";
        }
    }
}
