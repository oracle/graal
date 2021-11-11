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
package com.oracle.svm.jfr.events;

import java.util.concurrent.TimeUnit;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.Threading;
import org.graalvm.nativeimage.impl.ThreadingSupport;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.jfr.JfrEvents;
import com.oracle.svm.jfr.JfrNativeEventWriter;
import com.oracle.svm.jfr.JfrNativeEventWriterData;
import com.oracle.svm.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.jfr.JfrThreadState;
import com.oracle.svm.jfr.JfrTicks;
import com.oracle.svm.jfr.SubstrateJVM;

public final class ExecutionSampleEvent {

    private static long intervalMillis;
    private static final ExecutionSampleEventCallback CALLBACK = new ExecutionSampleEventCallback();

    @Uninterruptible(reason = "Called from uninterruptible code.", calleeMustBe = false)
    public static void tryToRegisterExecutionSampleEventCallback() {
        if (SubstrateJVM.get().isEnabled(JfrEvents.ExecutionSample) && intervalMillis > 0) {
            ImageSingletons.lookup(ThreadingSupport.class).registerRecurringCallback(intervalMillis, TimeUnit.MILLISECONDS, CALLBACK);
        }
    }

    public static void setSamplingInterval(long intervalMillis) {
        ExecutionSampleEvent.intervalMillis = intervalMillis;
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void writeExecutionSample(IsolateThread isolateThread, Thread.State threadState) {
        SubstrateJVM svm = SubstrateJVM.get();
        if (SubstrateJVM.isRecording() && svm.isEnabled(JfrEvents.ExecutionSample)) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginEventWrite(data, false);
            JfrNativeEventWriter.putLong(data, JfrEvents.ExecutionSample.getId());
            JfrNativeEventWriter.putLong(data, JfrTicks.elapsedTicks());
            JfrNativeEventWriter.putThread(data, isolateThread);
            JfrNativeEventWriter.putLong(data, svm.getStackTraceId(JfrEvents.ExecutionSample.getId(), 0));
            JfrNativeEventWriter.putLong(data, JfrThreadState.getId(threadState));
            JfrNativeEventWriter.endEventWrite(data, false);
        }
    }

    private static final class ExecutionSampleEventCallback implements Threading.RecurringCallback {

        @Override
        public void run(Threading.RecurringCallbackAccess access) {
            IsolateThread isolateThread = CurrentIsolate.getCurrentThread();
            Thread javaThread = JavaThreads.fromVMThread(isolateThread);
            ExecutionSampleEvent.writeExecutionSample(isolateThread, javaThread.getState());
        }
    }
}
