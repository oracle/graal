/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.JfrGCWhen;
import com.oracle.svm.core.jfr.JfrGCWhens;
import com.oracle.svm.core.jfr.JfrNativeEventWriter;
import com.oracle.svm.core.jfr.JfrNativeEventWriterData;
import com.oracle.svm.core.jfr.JfrNativeEventWriterDataAccess;

class JfrGCHeapSummaryEventSupport {

    private final JfrGCWhen before;
    private final JfrGCWhen after;

    JfrGCHeapSummaryEventSupport(JfrGCWhen before, JfrGCWhen after) {
        this.before = before;
        this.after = after;
    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public void emitGCHeapSummaryEventBeforeGC(UnsignedWord gcEpoch, long start, long heapUsed) {

        if (JfrEvent.GCHeapSummary.shouldEmit()) {

            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.GCHeapSummary);

            JfrNativeEventWriter.putLong(data, start); //   @Label("Start Time") @Timestamp("TICKS") long startTime;

            JfrNativeEventWriter.putLong(data, gcEpoch.rawValue()); //  @Label("GC Identifier") int gcId;
            JfrNativeEventWriter.putLong(data, before.getId()); //  @Label("When") String when;

            // VirtualSpace
            JfrNativeEventWriter.putLong(data, 0L); // start
            JfrNativeEventWriter.putLong(data, 0L); // committedEnd : ulong
            JfrNativeEventWriter.putLong(data, 0L); // committedSize : ulong
            JfrNativeEventWriter.putLong(data, 0L); // reservedEnd : ulong
            JfrNativeEventWriter.putLong(data, 0L); // reservedSize : ulong

            JfrNativeEventWriter.putLong(data, heapUsed); //  @Unsigned @DataAmount("BYTES")  @Label("Heap Used") @Description("Bytes allocated by objects in the heap")  long heapUsed;

            JfrNativeEventWriter.endSmallEvent(data);
        }

    }

    @Uninterruptible(reason = "Accesses a JFR buffer.")
    public void emitGCHeapSummaryEventAfterGC(UnsignedWord gcEpoch, long start, long heapUsed) {

        if (JfrEvent.GCHeapSummary.shouldEmit()) {

            JfrNativeEventWriterData data = StackValue.get(JfrNativeEventWriterData.class);
            JfrNativeEventWriterDataAccess.initializeThreadLocalNativeBuffer(data);

            JfrNativeEventWriter.beginSmallEvent(data, JfrEvent.GCHeapSummary);

            JfrNativeEventWriter.putLong(data, start); //   @Label("Start Time") @Timestamp("TICKS") long startTime;

            JfrNativeEventWriter.putLong(data, gcEpoch.rawValue()); //  @Label("GC Identifier") int gcId;
            JfrNativeEventWriter.putLong(data, after.getId()); //  @Label("When") String when;

            // VirtualSpace           
            JfrNativeEventWriter.putLong(data, 0L); // start
            JfrNativeEventWriter.putLong(data, 0L); // committedEnd : ulong
            JfrNativeEventWriter.putLong(data, 0L); // committedSize : ulong
            JfrNativeEventWriter.putLong(data, 0L); // reservedEnd : ulong
            JfrNativeEventWriter.putLong(data, 0L); // reservedSize : ulong

            JfrNativeEventWriter.putLong(data, heapUsed); //  @Unsigned @DataAmount("BYTES")  @Label("Heap Used") @Description("Bytes allocated by objects in the heap")  long heapUsed;

            JfrNativeEventWriter.endSmallEvent(data);
        }

    }
}

@AutomaticallyRegisteredFeature
class JfrGCHeapSummaryEventFeature implements InternalFeature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (HasJfrSupport.get()) {

            JfrGCWhen before = JfrGCWhens.singleton().addGCWhen("Before GC");

            JfrGCWhen after = JfrGCWhens.singleton().addGCWhen("After GC");

            ImageSingletons.add(JfrGCHeapSummaryEventSupport.class, new JfrGCHeapSummaryEventSupport(before, after));

        }
    }

}
