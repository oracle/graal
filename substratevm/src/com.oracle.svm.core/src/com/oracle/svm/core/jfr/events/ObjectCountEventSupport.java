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
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.thread.NativeVMOperation;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import com.oracle.svm.core.thread.VMOperation.SystemEffect;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.Heap;
import org.graalvm.nativeimage.StackValue;
import com.oracle.svm.core.hub.DynamicHub;
import org.graalvm.word.WordFactory;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.word.Pointer;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.util.VMError;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.struct.SizeOf;
import com.oracle.svm.core.hub.DynamicHubSupport;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.PointerBase;
import com.oracle.svm.core.jfr.events.PointerArrayAccess;
import com.oracle.svm.core.jfr.events.PointerArray;

public class ObjectCountEventSupport {
    private final static ObjectCountVisitor objectCountVisitor = new ObjectCountVisitor();
    private static final ObjectCountOperation objectCountOperation = new ObjectCountOperation();
    private static int debugCount1 = 0;

    @Platforms(HOSTED_ONLY.class)
    ObjectCountEventSupport() {
    }

    public static void countObjects() {
        assert VMOperation.isInProgressAtSafepoint();
        int size = SizeOf.get(ObjectCountVMOperationData.class);
        ObjectCountVMOperationData data = StackValue.get(size);
        UnmanagedMemoryUtil.fill((Pointer) data, WordFactory.unsigned(size), (byte) 0);
        PointerArray objectCounts = StackValue.get(PointerArray.class);
        // int initialCapacity = Heap.getHeap().getClassCount();
        // max type id:9218 classes count:7909
        int initialCapacity = ImageSingletons.lookup(DynamicHubSupport.class).getMaxTypeId();
        PointerArrayAccess.initialize(objectCounts, initialCapacity);

        // Throw an error, otherwise we'll probably get a segfault later
        VMError.guarantee(objectCounts.getSize() == initialCapacity, "init not done properly");

        data.setObjectCounts(objectCounts);
        objectCountOperation.enqueue(data);
        VMError.guarantee(debugCount1 > 2000, "debug count1 should be > 2000");

        int sizeSum = 0;
        int countSum = 0;
        for (int i = 0; i < objectCounts.getSize(); i++) {
            ObjectCountData objectCountData = (ObjectCountData) PointerArrayAccess.get(objectCounts, i);
            if (objectCountData.isNull()) {
                continue;
            }
            countSum += objectCountData.getCount();
            sizeSum += objectCountData.getSize();
            if (objectCountData.getCount() > 0) {
                VMError.guarantee(objectCountData.getSize() > 0, "size should be > 0 if count is > 0");
            }
        }
        // *** Do NOT add prints. Segfault at index 2927
        VMError.guarantee(countSum > 0, "countSum should be >0");
        VMError.guarantee(sizeSum > 0, "sizeSum should be >0");

        int typeId = DynamicHub.fromClass(String.class).getTypeID();
        ObjectCountData stringOcd = objectCounts.getData().addressOf(typeId).read();
        VMError.guarantee(stringOcd.getCount() > 0, "should have more than 1 String in heap");
        VMError.guarantee(stringOcd.getSize() > 0, "string size should be positive");
        PointerArrayAccess.freeData(objectCounts);
    }

    private static class ObjectCountOperation extends NativeVMOperation {
        @Platforms(HOSTED_ONLY.class)
        ObjectCountOperation() {
            super(VMOperationInfos.get(ObjectCountOperation.class, "JFR count objects", SystemEffect.SAFEPOINT));
        }

        @Override
// @RestrictHeapAccess(access = NO_ALLOCATION, reason = "Must not allocate during GC.")
        protected void operate(NativeVMOperationData data) {
            ObjectCountVMOperationData objectCountVMOperationData = (ObjectCountVMOperationData) data;
            objectCountVisitor.initialize(objectCountVMOperationData.getObjectCounts());
            Heap.getHeap().walkImageHeapObjects(objectCountVisitor);
        }
    }

    private static boolean initializeObjectCountData(PointerArray pointerArray, int idx) {
        ObjectCountData objectCountData = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(SizeOf.unsigned(ObjectCountData.class));
        if (objectCountData.isNull()) {
            return false;
        }
        objectCountData.setCount(0);
        objectCountData.setSize(0);
        PointerArrayAccess.write(pointerArray, idx, objectCountData);
        return true;
    }

    private static class ObjectCountVisitor implements ObjectVisitor {
        PointerArray objectCounts;

        @Platforms(HOSTED_ONLY.class)
        ObjectCountVisitor() {
        }

        public void initialize(PointerArray objectCounts) {
            this.objectCounts = objectCounts;
        }

        @Override
        public boolean visitObject(Object obj) { // *** Can't allocate in here no matter what.
            assert VMOperation.isInProgressAtSafepoint();
            DynamicHub hub = DynamicHub.fromClass(obj.getClass());
            int typeId = hub.getTypeID();
            VMError.guarantee(typeId < objectCounts.getSize(), "Should not encounter a typeId out of scope of the array");

            // create an ObjectCountData for this typeID if one doesn't already exist
            ObjectCountData objectCountData = objectCounts.getData().addressOf(typeId).read();
            if (objectCountData.isNull()) {  // *** this is working as expected
                debugCount1++;
                if (!initializeObjectCountData(objectCounts, typeId)) {
                    return false;
                }
                // read it again to refresh the value
                objectCountData = objectCounts.getData().addressOf(typeId).read();
            }

            // Increase count
            objectCountData.setCount(objectCountData.getCount() + 1);

            // Get size
            long size = objectCountData.getSize();
            // int layoutEncoding = KnownIntrinsics.readHub(obj).getLayoutEncoding();
            // if (LayoutEncoding.isArray(layoutEncoding)) {
            // int elementSize;
            // if (LayoutEncoding.isPrimitiveArray(layoutEncoding)) {
            // elementSize = LayoutEncoding.getArrayIndexScale(layoutEncoding);
            // } else {
            // elementSize = ConfigurationValues.getTarget().wordSize;
            // }
            // int length = ArrayLengthNode.arrayLength(obj);
            // size += WordFactory.unsigned(length).multiply(elementSize).rawValue();
            // } else if (LayoutEncoding.isPureInstance(layoutEncoding)) {
            // size += LayoutEncoding.getPureInstanceAllocationSize(layoutEncoding).rawValue();
            // } else if (LayoutEncoding.isHybrid(layoutEncoding)){
            // size += LayoutEncoding.getArrayBaseOffsetAsInt(layoutEncoding);
            // }
            size += uninterruptibleGetSize(obj); // there's also getSizeFromObjectInGC and
                                                 // getMomentarySizeFromObject
            objectCountData.setSize(size);
            return true;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        private long uninterruptibleGetSize(Object obj) {
            return LayoutEncoding.getSizeFromObject(obj).rawValue();
        }
    }

    @RawStructure
    private interface ObjectCountVMOperationData extends NativeVMOperationData {
        @RawField
        PointerArray getObjectCounts();

        @RawField
        void setObjectCounts(PointerArray value);
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
    }
}
