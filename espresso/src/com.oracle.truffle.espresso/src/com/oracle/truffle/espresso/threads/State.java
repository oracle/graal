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

package com.oracle.truffle.espresso.threads;

import static com.oracle.truffle.espresso.jvmti.JvmtiConstants.JvmtiThreadStateFlags.JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER;
import static com.oracle.truffle.espresso.jvmti.JvmtiConstants.JvmtiThreadStateFlags.JVMTI_THREAD_STATE_IN_NATIVE;
import static com.oracle.truffle.espresso.jvmti.JvmtiConstants.JvmtiThreadStateFlags.JVMTI_THREAD_STATE_RUNNABLE;
import static com.oracle.truffle.espresso.jvmti.JvmtiConstants.JvmtiThreadStateFlags.JVMTI_THREAD_STATE_TERMINATED;
import static com.oracle.truffle.espresso.jvmti.JvmtiConstants.JvmtiThreadStateFlags.JVMTI_THREAD_STATE_WAITING_INDEFINITELY;
import static com.oracle.truffle.espresso.jvmti.JvmtiConstants.JvmtiThreadStateFlags.JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT;

public enum State {
    NEW(0),
    RUNNABLE(JVMTI_THREAD_STATE_RUNNABLE),
    BLOCKED(JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER),
    WAITING(JVMTI_THREAD_STATE_WAITING_INDEFINITELY),
    TIMED_WAITING(JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT),
    TERMINATED(JVMTI_THREAD_STATE_TERMINATED),
    IN_NATIVE(JVMTI_THREAD_STATE_IN_NATIVE);

    public final int value;

    State(int value) {
        this.value = value;
    }
}
