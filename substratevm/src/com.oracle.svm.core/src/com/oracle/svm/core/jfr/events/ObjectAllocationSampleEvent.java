/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.HasJfrSupport;
import org.graalvm.nativeimage.StackValue;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.core.thread.PlatformThreads;

public class ObjectAllocationSampleEvent {
    private static final FastThreadLocalLong lastAllocationSize = FastThreadLocalFactory.createLong("ObjectAllocationSampleEvent.lastAllocationSize");
    public static void emit(long startTicks, Class<?> clazz) {
        if (HasJfrSupport.get()) {
            // TODO: consider moving this to after the isRecording check in emit0 to avoid duplicate checks. Might be a pain to deal with uninterruptibility though. Also we want to minimize uninterruptible code usage.
            // Doesn't hurt to check twice, might save us some time doing the sampling
            if (SubstrateJVM.get().shouldCommit(JfrEvent.ObjectAllocationSample)) {
                emit0(startTicks, clazz);
            }
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emit0(long startTicks, Class<?> clazz) {
        if (JfrEvent.ObjectAllocationSample.shouldEmit()) {
            long currentAllocationSize = PlatformThreads.getThreadAllocatedBytes(com.oracle.svm.core.thread.JavaThreads.getCurrentThreadId());

            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);
            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.ObjectAllocationSample);
            JfrNativeEventWriter.putLong(data, startTicks);
            JfrNativeEventWriter.putEventThread(data);
            JfrNativeEventWriter.putLong(data, SubstrateJVM.get().getStackTraceId(JfrEvent.ObjectAllocationSample, 0)); //This causes problems during JFR shutdown
            JfrNativeEventWriter.putClass(data, clazz);
            JfrNativeEventWriter.putLong(data, currentAllocationSize - lastAllocationSize.get());
            JfrNativeEventWriter.endSmallEvent(data);
            lastAllocationSize.set(currentAllocationSize);
        }
    }
}
