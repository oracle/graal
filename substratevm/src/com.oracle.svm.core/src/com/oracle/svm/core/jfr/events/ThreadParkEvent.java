/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2025, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.jfr.events;

import org.graalvm.nativeimage.StackValue;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.monitor.JavaMonitorQueuedSynchronizer;

import jdk.graal.compiler.word.Word;

public class ThreadParkEvent {
    public static void emit(long startTicks, Object obj, boolean isAbsolute, long time) {
        if (HasJfrSupport.get() && !isInternalPark(obj)) {
            emit0(startTicks, obj, isAbsolute, time);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emit0(long startTicks, Object obj, boolean isAbsolute, long time) {
        long duration = JfrTicks.duration(startTicks);
        if (JfrEvent.ThreadPark.shouldEmit(duration)) {
            Class<?> parkedClass = null;
            if (obj != null) {
                parkedClass = obj.getClass();
            }

            long timeout = Long.MIN_VALUE;
            long until = Long.MIN_VALUE;
            if (isAbsolute) {
                until = time;
            } else if (time != 0L) {
                timeout = time;
            }

            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);
            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.ThreadPark);
            JfrNativeEventWriter.putLong(data, startTicks);
            JfrNativeEventWriter.putLong(data, duration);
            JfrNativeEventWriter.putEventThread(data);
            JfrNativeEventWriter.putLong(data, SubstrateJVM.get().getStackTraceId(JfrEvent.ThreadPark));
            JfrNativeEventWriter.putClass(data, parkedClass);
            JfrNativeEventWriter.putLong(data, timeout);
            JfrNativeEventWriter.putLong(data, until);
            JfrNativeEventWriter.putLong(data, Word.objectToUntrackedPointer(obj).rawValue());
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }

    /**
     * Skip emission if this is an internal park ({@link JavaMonitorWaitEvent} or
     * {@link JavaMonitorEnterEvent} will be emitted instead).
     */
    private static boolean isInternalPark(Object obj) {
        if (obj == null) {
            return false;
        }

        Class<?> parkedClass = obj.getClass();
        if (JavaMonitorQueuedSynchronizer.class.isAssignableFrom(parkedClass)) {
            return true;
        }

        Class<?> enclosingClass = parkedClass.getEnclosingClass();
        return enclosingClass != null && JavaMonitorQueuedSynchronizer.class.isAssignableFrom(enclosingClass);
    }
}
