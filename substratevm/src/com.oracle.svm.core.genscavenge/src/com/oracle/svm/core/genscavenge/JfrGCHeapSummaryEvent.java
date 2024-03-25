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
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrGCWhen;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;
import com.oracle.svm.core.jfr.JfrTicks;

class JfrGCHeapSummaryEvent {
    public static void emit(JfrGCWhen gcWhen) {
        if (HasJfrSupport.get()) {
            emit0(GCImpl.getGCImpl().getCollectionEpoch(), JfrTicks.elapsedTicks(), HeapImpl.getAccounting().getCommittedBytes(), HeapImpl.getAccounting().getUsedBytes(), gcWhen);
        }
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    private static void emit0(UnsignedWord gcEpoch, long start, UnsignedWord committedSize, UnsignedWord heapUsed, JfrGCWhen gcWhen) {
        if (JfrEvent.GCHeapSummary.shouldEmit()) {
            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.GCHeapSummary);
            JfrNativeEventWriter.putLong(data, start);
            JfrNativeEventWriter.putLong(data, gcEpoch.rawValue());
            JfrNativeEventWriter.putLong(data, gcWhen.getId());

            // VirtualSpace
            JfrNativeEventWriter.putLong(data, -1); // start
            JfrNativeEventWriter.putLong(data, -1); // committedEnd
            JfrNativeEventWriter.putLong(data, committedSize.rawValue());
            JfrNativeEventWriter.putLong(data, -1); // reservedEnd
            // Reserved heap size matches committed size
            JfrNativeEventWriter.putLong(data, committedSize.rawValue()); // reservedSize

            JfrNativeEventWriter.putLong(data, heapUsed.rawValue());
            JfrNativeEventWriter.endSmallEvent(data);
        }
    }
}
