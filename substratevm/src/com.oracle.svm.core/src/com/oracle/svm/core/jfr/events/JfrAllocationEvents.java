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

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalLong;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.JavaThreads;

public class JfrAllocationEvents {
    private static final FastThreadLocalLong lastAllocationSize = FastThreadLocalFactory.createLong("ObjectAllocationSampleEvent.lastAllocationSize");

    public static void resetLastAllocationSize(IsolateThread thread) {
        lastAllocationSize.set(thread, 0);
    }

    public static void emit(long startTicks, DynamicHub hub, UnsignedWord allocationSize, UnsignedWord tlabSize) {
        if (HasJfrSupport.get()) {
            Class<?> clazz = DynamicHub.toClass(hub);
            emitObjectAllocationInNewTLAB(startTicks, clazz, allocationSize, tlabSize);
            emitObjectAllocationSample(startTicks, clazz);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitObjectAllocationInNewTLAB(long startTicks, Class<?> clazz, UnsignedWord allocationSize, UnsignedWord tlabSize) {
        if (JfrEvent.ObjectAllocationInNewTLAB.shouldEmit()) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);
            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.ObjectAllocationInNewTLAB);
            JfrNativeEventWriter.putLong(data, startTicks);
            JfrNativeEventWriter.putEventThread(data);
            JfrNativeEventWriter.putLong(data, SubstrateJVM.get().getStackTraceId(JfrEvent.ObjectAllocationInNewTLAB, 0));
            JfrNativeEventWriter.putClass(data, clazz);
            JfrNativeEventWriter.putLong(data, allocationSize.rawValue());
            JfrNativeEventWriter.putLong(data, tlabSize.rawValue());
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emitObjectAllocationSample(long startTicks, Class<?> clazz) {
        if (JfrEvent.ObjectAllocationSample.shouldEmit()) {
            long currentAllocationSize = PlatformThreads.getThreadAllocatedBytes(JavaThreads.getCurrentThreadId());
            long weight = currentAllocationSize - lastAllocationSize.get();
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);
            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.ObjectAllocationSample);
            JfrNativeEventWriter.putLong(data, startTicks);
            JfrNativeEventWriter.putEventThread(data);
            JfrNativeEventWriter.putLong(data, SubstrateJVM.get().getStackTraceId(JfrEvent.ObjectAllocationSample, 0));
            JfrNativeEventWriter.putClass(data, clazz);
            JfrNativeEventWriter.putLong(data, weight);
            JfrNativeEventWriter.endSmallEvent(data);
            lastAllocationSize.set(currentAllocationSize);
        }
    }
}
