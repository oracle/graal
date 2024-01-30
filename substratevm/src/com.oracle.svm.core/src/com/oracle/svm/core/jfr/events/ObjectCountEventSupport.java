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
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jfr.HasJfrSupport;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.thread.VMOperation;

import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;

import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;
import org.graalvm.word.UnsignedWord;

public class ObjectCountEventSupport {
    private static final double CUTOFF_PERCENTAGE = 0.005;
    private static final ObjectCountVisitor objectCountVisitor = new ObjectCountVisitor();

    @Platforms(HOSTED_ONLY.class)
    ObjectCountEventSupport() {
    }

    public static void emitEvents(UnsignedWord gcId, long startTicks, GCCause cause) {
        if (HasJfrSupport.get() && (GCCause.JfrObjectCount.equals(cause) || shouldEmitObjectCountAfterGC())) {
            emitEvents0(gcId, startTicks, cause);
        }
    }

    /** ShouldEmit will be checked again later. This is merely an optimization. */
    @Uninterruptible(reason = "Caller of JfrEvent#shouldEmit must be uninterruptible.")
    private static boolean shouldEmitObjectCountAfterGC() {
        return JfrEvent.ObjectCountAfterGC.shouldEmit();
    }

    private static void emitEvents0(UnsignedWord gcId, long startTicks, GCCause cause) {
        int initialCapacity = ImageSingletons.lookup(DynamicHubSupport.class).getMaxTypeId();
        NonmovableArray<ObjectCountData> objectCounts = NonmovableArrays.createWordArray(initialCapacity);
        try {
            long totalSize = visitObjects(objectCounts);

            for (int i = 0; i < initialCapacity; i++) {
                emitForTypeId(i, objectCounts, gcId, startTicks, totalSize, cause);
            }
        } finally {
            NonmovableArrays.releaseUnmanagedArray(objectCounts);
        }
    }

    private static void emitForTypeId(int typeId, NonmovableArray<ObjectCountData> objectCounts, UnsignedWord gcId, long startTicks, long totalSize, GCCause cause) {
        ObjectCountData objectCountData = NonmovableArrays.getWord(objectCounts, typeId);
        if (objectCountData.isNonNull() && objectCountData.getSize() / (double) totalSize > CUTOFF_PERCENTAGE) {
            assert objectCountData.getSize() > 0 && objectCountData.getTraceId() > 0 && objectCountData.getSize() > 0;
            if (GCCause.JfrObjectCount.equals(cause)) {
                ObjectCountEvents.emit(JfrEvent.ObjectCount, startTicks, objectCountData.getTraceId(), objectCountData.getCount(), objectCountData.getSize(), (int) gcId.rawValue());
            }
            ObjectCountEvents.emit(JfrEvent.ObjectCountAfterGC, startTicks, objectCountData.getTraceId(), objectCountData.getCount(), objectCountData.getSize(), (int) gcId.rawValue());
        }
    }

    private static long visitObjects(NonmovableArray<ObjectCountData> objectCounts) {
        assert VMOperation.isGCInProgress();
        objectCountVisitor.initialize(objectCounts);
        Heap.getHeap().walkObjects(objectCountVisitor);
        return objectCountVisitor.getTotalSize();
    }

    private static ObjectCountData initializeObjectCountData(NonmovableArray<ObjectCountData> objectCounts, int idx, Object obj) {
        ObjectCountData objectCountData = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(SizeOf.unsigned(ObjectCountData.class));
        if (objectCountData.isNull()) {
            return WordFactory.nullPointer();
        }
        objectCountData.setCount(0);
        objectCountData.setSize(0);
        objectCountData.setTraceId(getTraceId(obj.getClass()));
        NonmovableArrays.setWord(objectCounts, idx, objectCountData);
        return objectCountData;
    }

    /**
     * It's ok to get the trace ID here because the JFR epoch will not change before jdk.ObjectCount
     * events are committed.
     */
    @Uninterruptible(reason = "Caller of SubstrateJVM#getClassId must be uninterruptible.")
    private static long getTraceId(Class<?> c) {
        assert VMOperation.isGCInProgress();
        return SubstrateJVM.get().getClassId(c);
    }

    private static class ObjectCountVisitor implements ObjectVisitor {
        private NonmovableArray<ObjectCountData> objectCounts;
        private long totalSize;

        @Platforms(HOSTED_ONLY.class)
        ObjectCountVisitor() {
        }

        public void initialize(NonmovableArray<ObjectCountData> objectCounts) {
            this.objectCounts = objectCounts;
            this.totalSize = 0;
        }

        @Override
        public boolean visitObject(Object obj) {
            assert VMOperation.isGCInProgress();
            DynamicHub hub = DynamicHub.fromClass(obj.getClass());
            int typeId = hub.getTypeID();
            assert typeId < NonmovableArrays.lengthOf(objectCounts);

            // Create an ObjectCountData for this typeID if one doesn't already exist
            ObjectCountData objectCountData = NonmovableArrays.getWord(objectCounts, typeId);
            if (objectCountData.isNull()) {
                objectCountData = initializeObjectCountData(objectCounts, typeId, obj);
                if (objectCountData.isNull()) {
                    return false;
                }
            }

            // Increase count
            objectCountData.setCount(objectCountData.getCount() + 1);

            // Get size
            long additionalSize = LayoutEncoding.getSizeFromObjectInGC(obj).rawValue();
            totalSize += additionalSize;
            objectCountData.setSize(objectCountData.getSize() + additionalSize);

            return true;
        }

        public long getTotalSize() {
            return totalSize;
        }
    }

    @RawStructure
    public interface ObjectCountData extends PointerBase {
        @RawField
        long getCount();

        @RawField
        void setCount(long value);

        @RawField
        long getSize();

        @RawField
        void setSize(long value);

        @RawField
        long getTraceId();

        @RawField
        void setTraceId(long value);
    }
}
