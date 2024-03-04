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
package com.oracle.svm.core.monitor;

import com.oracle.svm.core.jvmti.headers.JvmtiError;

public class JvmtiRawMonitorHelper {

    public static JvmtiError getOrCreateRawMonitor(Object obj) {
        MonitorSupport monitorSupport = MonitorSupport.singleton();
        if (!(monitorSupport instanceof MultiThreadedMonitorSupport multiThreadedMonitorSupport)) {
            return JvmtiError.JVMTI_ERROR_INTERNAL;
        }
        multiThreadedMonitorSupport.getOrCreateMonitor(obj, MonitorInflationCause.JVMTI_CREATE);
        return JvmtiError.JVMTI_ERROR_NONE;
    }

    public static JvmtiError exitCompletely(Object obj) {
        MonitorSupport monitorSupport = MonitorSupport.singleton();
        if (!(monitorSupport instanceof MultiThreadedMonitorSupport multiThreadedMonitorSupport)) {
            return JvmtiError.JVMTI_ERROR_INTERNAL;
        }
        JavaMonitor monitor = multiThreadedMonitorSupport.getMonitor(obj);
        boolean done;
        try {
            done = monitor.tryRelease(monitor.getAcquisitions());
        } catch (IllegalMonitorStateException e) {
            return JvmtiError.JVMTI_ERROR_INTERNAL;
        }
        return done ? JvmtiError.JVMTI_ERROR_NONE : JvmtiError.JVMTI_ERROR_INTERRUPT;
    }
}
