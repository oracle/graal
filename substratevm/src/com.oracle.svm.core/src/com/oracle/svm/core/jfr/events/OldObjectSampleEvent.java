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

import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;

public class OldObjectSampleEvent {
    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public static void emit(long startTicks, long objectId, UnsignedWord objectSize, long allocationTicks, long threadId, long stackTraceId, UnsignedWord heapUsedAfterLastGC, int arrayLength) {
        if (JfrEvent.OldObjectSample.shouldEmit()) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.OldObjectSample);
            JfrNativeEventWriter.putLong(data, startTicks); // start time
            JfrNativeEventWriter.putLong(data, 0); // duration
            JfrNativeEventWriter.putLong(data, threadId);
            JfrNativeEventWriter.putLong(data, stackTraceId);
            JfrNativeEventWriter.putLong(data, allocationTicks); // allocation time
            JfrNativeEventWriter.putLong(data, objectSize.rawValue());
            JfrNativeEventWriter.putLong(data, startTicks - allocationTicks); // object age
            JfrNativeEventWriter.putLong(data, heapUsedAfterLastGC.rawValue());
            JfrNativeEventWriter.putLong(data, objectId);
            JfrNativeEventWriter.putInt(data, arrayLength);
            JfrNativeEventWriter.putLong(data, 0); // GC root
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }
}
