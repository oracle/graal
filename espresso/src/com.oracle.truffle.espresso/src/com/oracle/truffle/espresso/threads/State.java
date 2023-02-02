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

public enum State {
    NEW(0),
    RUNNABLE(0x0004 /* JVMTI_THREAD_STATE_RUNNABLE */),
    BLOCKED(0x0400 /* JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER */),
    WAITING(0x0010 /* JVMTI_THREAD_STATE_WAITING_INDEFINITELY */),
    TIMED_WAITING(0x0020 /* JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT */),
    TERMINATED(0x0002 /* JVMTI_THREAD_STATE_TERMINATED */),
    IN_NATIVE(0x400000 /* JVMTI_THREAD_STATE_IN_NATIVE */);

    public final int value;

    State(int value) {
        this.value = value;
    }
}
