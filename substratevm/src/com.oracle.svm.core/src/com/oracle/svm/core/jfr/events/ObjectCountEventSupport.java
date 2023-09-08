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
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.VMOperationInfos;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jfr.JfrEvent;
import com.oracle.svm.core.jfr.SubstrateJVM;
import com.oracle.svm.core.jfr.events.ObjectCountEvents;
import com.oracle.svm.core.thread.NativeVMOperation;
import com.oracle.svm.core.thread.NativeVMOperationData;
import com.oracle.svm.core.thread.VMOperation.SystemEffect;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform.HOSTED_ONLY;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;

import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;

public class ObjectCountEventSupport {
    private final static ObjectCountVisitor objectCountVisitor = new ObjectCountVisitor();
    private static final ObjectCountOperation objectCountOperation = new ObjectCountOperation();
    private static int debugCount1 = 0;
    private static double cutoffPercentage = 0.005;
    private static long totalSize;

    // *** should be volatile because periodic thread writes it and VM op thread reads it
    private static volatile boolean shouldSendRequestableEvent = false;

    @Platforms(HOSTED_ONLY.class)
    ObjectCountEventSupport() {
    }
    /** This is to be called by the JFR periodic task as part of the JFR periodic events */
//    @Uninterruptible(reason = "Set and unset should be atomic with invoked GC to avoid races.", callerMustBe = true) //TODO revisit
    public static void setShouldSendRequestableEvent(boolean value){
        shouldSendRequestableEvent = value;
    }

    public static void emitEvents(int gcId, long startTicks){
        if (com.oracle.svm.core.jfr.HasJfrSupport.get() && shouldEmitEvents()) {
           emitEvents0(gcId, startTicks);
           if (shouldSendRequestableEvent){
               shouldSendRequestableEvent = false;
           }
        }
    }

    /** ShouldEmit will be checked again later. This is merely an optimization.*/
    @Uninterruptible(reason = "Caller of JfrEvent#shouldEmit must be uninterruptible.")
    private static boolean shouldEmitEvents(){
        return (shouldSendRequestableEvent && JfrEvent.ObjectCount.shouldEmit()) || JfrEvent.ObjectCountAfterGC.shouldEmit();
    }
    private static void emitEvents0(int gcId, long startTicks) {
        PointerArray objectCounts = StackValue.get(PointerArray.class);
        countObjects(objectCounts);

        for (int i = 0; i < objectCounts.getSize(); i++) {
            emitForTypeId(i, objectCounts, gcId, startTicks);
        }
        PointerArrayAccess.freeData(objectCounts);
    }

    private static void emitForTypeId(int typeId, PointerArray objectCounts, int gcId, long startTicks){
        ObjectCountData objectCountData = (ObjectCountData) PointerArrayAccess.get(objectCounts, typeId);
        if (objectCountData.isNonNull() && objectCountData.getSize() / (double) totalSize > cutoffPercentage) {
            VMError.guarantee(objectCountData.getSize() > 0 && objectCountData.getTraceId() >0 && objectCountData.getSize()>0, "size should be > 0 if count is > 0");

            ObjectCountEvents.emit(JfrEvent.ObjectCount, startTicks, objectCountData.getTraceId(), objectCountData.getCount(), objectCountData.getSize(), gcId);
            ObjectCountEvents.emit(JfrEvent.ObjectCountAfterGC, startTicks, objectCountData.getTraceId(), objectCountData.getCount(), objectCountData.getSize(), gcId);
        }
    }

    private static PointerArray countObjects(PointerArray objectCounts) {
        assert VMOperation.isInProgressAtSafepoint();
        int size = SizeOf.get(ObjectCountVMOperationData.class);
        ObjectCountVMOperationData vmOpData = StackValue.get(size);
        UnmanagedMemoryUtil.fill((Pointer) vmOpData, WordFactory.unsigned(size), (byte) 0);

        int initialCapacity = ImageSingletons.lookup(DynamicHubSupport.class).getMaxTypeId();
        PointerArrayAccess.initialize(objectCounts, initialCapacity);

        // Throw an error, otherwise we'll probably get a segfault later
        VMError.guarantee(objectCounts.getSize() == initialCapacity, "init not done properly");

        vmOpData.setObjectCounts(objectCounts);
        objectCountOperation.enqueue(vmOpData);
        VMError.guarantee(debugCount1 > 500, "debug count1 should be > 500");

        int sizeSum = 0;
        int countSum = 0;
        for (int i = 0; i < objectCounts.getSize(); i++) {
            ObjectCountData objectCountData = (ObjectCountData) PointerArrayAccess.get(objectCounts, i);
            if (objectCountData.isNull()) {
                continue;
            }
            countSum += objectCountData.getCount();
            sizeSum += objectCountData.getSize();
            VMError.guarantee(objectCountData.getSize() > 0, "size should be > 0 if count is > 0");
        }

        VMError.guarantee(countSum > 0, "countSum should be >0");
        VMError.guarantee(sizeSum > 0, "sizeSum should be >0");

        int typeId = DynamicHub.fromClass(String.class).getTypeID();
        ObjectCountData stringOcd = objectCounts.getData().addressOf(typeId).read();
        VMError.guarantee(stringOcd.getCount() > 0, "should have more than 1 String in heap");
        VMError.guarantee(stringOcd.getSize() > 0, "string size should be positive");

        return objectCounts;
    }

    private static class ObjectCountOperation extends NativeVMOperation {
        @Platforms(HOSTED_ONLY.class)
        ObjectCountOperation() {
            super(VMOperationInfos.get(ObjectCountOperation.class, "JFR count objects", SystemEffect.SAFEPOINT));
        }

        @Override
        protected void operate(NativeVMOperationData data) {
            ObjectCountVMOperationData objectCountVMOperationData = (ObjectCountVMOperationData) data;
            objectCountVisitor.initialize(objectCountVMOperationData.getObjectCounts());
            totalSize = 0; // compute anew each time we operate
            Heap.getHeap().walkImageHeapObjects(objectCountVisitor);
        }
    }


    private static boolean initializeObjectCountData(PointerArray pointerArray, int idx, Object obj) {
        ObjectCountData objectCountData = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(SizeOf.unsigned(ObjectCountData.class));
        if (objectCountData.isNull()) {
            return false;
        }
        objectCountData.setCount(0);
        objectCountData.setSize(0);
        objectCountData.setTraceId(getTraceId(obj.getClass()));
        PointerArrayAccess.write(pointerArray, idx, objectCountData);
        return true;
    }

    /** JFR epoch will not change before associated ObjectCount event is committed because this code runs within a
     * GC safepoint.*/  // TODO revisit this logic
    @Uninterruptible(reason = "Caller of SubstrateJVM#getClassId must be uninterruptible.")
    private static long getTraceId(Class<?> c){
        assert VMOperation.isInProgressAtSafepoint();
        return SubstrateJVM.get().getClassId(c);
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
                if (!initializeObjectCountData(objectCounts, typeId, obj)) {
                    return false;
                }
                // read it again to refresh the value
                objectCountData = objectCounts.getData().addressOf(typeId).read();
            }

            // Increase count
            objectCountData.setCount(objectCountData.getCount() + 1);

            // Get size
            long additionalSize = uninterruptibleGetSize(obj);
            totalSize += additionalSize;
            objectCountData.setSize(objectCountData.getSize() + additionalSize);

            return true;
        }

        /** GC should not touch this object again before we are done with it.*/ // TODO revisit this logic
        @Uninterruptible(reason = "Caller of LayoutEncoding#getSizeFromObject must be uninterruptible.")
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

        @RawField
        long getTraceId();

        @RawField
        void setTraceId(long value);
    }
}
