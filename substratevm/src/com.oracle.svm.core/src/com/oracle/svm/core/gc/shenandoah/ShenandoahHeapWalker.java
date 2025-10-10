/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shenandoah;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahLibrary;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahStructs.ShenandoahRegionBoundaries;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RestrictHeapAccess.Access;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.thread.RecurringCallbackSupport;
import com.oracle.svm.core.thread.VMOperation;

import jdk.graal.compiler.word.Word;

public class ShenandoahHeapWalker {
    private static final OutOfMemoryError OUT_OF_MEMORY_ERROR = new OutOfMemoryError("Ran out of native memory while preparing the heap walk.");

    public static void walkCollectedHeap(ObjectVisitor visitor) {
        RecurringCallbackSupport.suspendCallbackTimer("Recurring callbacks could allocate.");
        try {
            walkCollectedHeap0(visitor);
        } finally {
            RecurringCallbackSupport.resumeCallbackTimer();
        }
    }

    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Allocations could change the heap regions")
    private static void walkCollectedHeap0(ObjectVisitor visitor) {
        assert RecurringCallbackSupport.isCallbackUnsupportedOrTimerSuspended();
        VMOperation.guaranteeInProgressAtSafepoint("must only be executed at a safepoint");

        ShenandoahCommittedMemoryProvider memoryProvider = ImageSingletons.lookup(ShenandoahCommittedMemoryProvider.class);
        int maxRegions = memoryProvider.getMaxRegions();

        ShenandoahImageHeapInfo imageHeapInfo = ShenandoahHeap.getImageHeapInfo();
        int fromRegion = imageHeapInfo.getNumRegions();

        ShenandoahRegionBoundaries regionBoundaries = NullableNativeMemory.calloc(Word.unsigned(maxRegions).multiply(SizeOf.get(ShenandoahRegionBoundaries.class)), NmtCategory.GC);
        if (regionBoundaries.isNull()) {
            throw OUT_OF_MEMORY_ERROR;
        }

        try {
            ShenandoahLibrary.getRegionBoundaries(regionBoundaries);
            for (int i = fromRegion; i < maxRegions; i++) {
                ShenandoahRegionBoundaries bounds = regionBoundaries.addressOf(i);
                if (bounds.bottom().equal(0)) {
                    continue;
                }

                Word bottom = bounds.bottom();
                Word top = bounds.top();
                Pointer cur = bottom;
                while (cur.belowThan(top)) {
                    Object o = cur.toObject();
                    visitor.visitObject(o);
                    cur = LayoutEncoding.getObjectEndInGC(o);
                }
            }
        } finally {
            NullableNativeMemory.free(regionBoundaries);
        }
    }
}
