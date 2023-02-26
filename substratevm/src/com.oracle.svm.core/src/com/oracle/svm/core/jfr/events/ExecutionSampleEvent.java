/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.util.concurrent.TimeUnit;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.Threading;
import org.graalvm.nativeimage.impl.ThreadingSupport;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrThreadState;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.thread.PlatformThreads;

public final class ExecutionSampleEvent {

    private static long intervalMillis;
    private static final ExecutionSampleEventCallback CALLBACK = new ExecutionSampleEventCallback();

    @Uninterruptible(reason = "Called from uninterruptible code.", calleeMustBe = false)
    public static void tryToRegisterExecutionSampleEventCallback() {
        if (intervalMillis > 0) {
            ImageSingletons.lookup(ThreadingSupport.class).registerRecurringCallback(intervalMillis, TimeUnit.MILLISECONDS, CALLBACK);
        }
    }

    public static void setSamplingInterval(long intervalMillis) {
        ExecutionSampleEvent.intervalMillis = intervalMillis;
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void writeExecutionSample(long elapsedTicks, long threadId, long stackTraceId, long threadState) {
        SubstrateJVM svm = SubstrateJVM.get();
        if (SubstrateJVM.isRecording() && svm.isEnabled(JfrEvent.ExecutionSample)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.ExecutionSample);
            JfrNativeEventWriter.putLong(data, elapsedTicks);
            JfrNativeEventWriter.putLong(data, threadId);
            JfrNativeEventWriter.putLong(data, stackTraceId);
            JfrNativeEventWriter.putLong(data, threadState);
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }

    private static final class ExecutionSampleEventCallback implements Threading.RecurringCallback {
        @Override
        public void run(Threading.RecurringCallbackAccess access) {
            IsolateThread isolateThread = CurrentIsolate.getCurrentThread();
            Thread javaThread = PlatformThreads.fromVMThread(isolateThread);
            ExecutionSampleEvent.writeExecutionSample(
                            JfrTicks.elapsedTicks(),
                            SubstrateJVM.getThreadId(isolateThread),
                            SubstrateJVM.get().getStackTraceId(JfrEvent.ExecutionSample, 0),
                            JfrThreadState.getId(javaThread.getState()));
        }
    }
}
