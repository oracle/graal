/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jvmti.headers;

import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.constant.CEnum;
import org.graalvm.nativeimage.c.constant.CEnumLookup;
import org.graalvm.nativeimage.c.constant.CEnumValue;

@CContext(JvmtiDirectives.class)
@CEnum
public enum JvmtiThreadState {
    JVMTI_THREAD_STATE_ALIVE,
    JVMTI_THREAD_STATE_TERMINATED,
    JVMTI_THREAD_STATE_RUNNABLE,
    JVMTI_THREAD_STATE_BLOCKED_ON_MONITOR_ENTER,
    JVMTI_THREAD_STATE_WAITING,
    JVMTI_THREAD_STATE_WAITING_INDEFINITELY,
    JVMTI_THREAD_STATE_WAITING_WITH_TIMEOUT,
    JVMTI_THREAD_STATE_SLEEPING,
    JVMTI_THREAD_STATE_IN_OBJECT_WAIT,
    JVMTI_THREAD_STATE_PARKED,
    JVMTI_THREAD_STATE_SUSPENDED,
    JVMTI_THREAD_STATE_INTERRUPTED,
    JVMTI_THREAD_STATE_IN_NATIVE;

    @CEnumValue
    public native int getCValue();

    @CEnumLookup
    public static native JvmtiThreadState fromValue(int value);
}
