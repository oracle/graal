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

package com.oracle.svm.hosted.webimage.wasm.gc;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.AuxiliaryImageHeap;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.ImageHeapInfo;
import com.oracle.svm.core.genscavenge.ImageHeapWalker;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RuntimeCodeInfoGCSupport;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.word.Word;

/**
 * Equivalent of {@link HeapImpl} for the Wasm backend.
 * <p>
 * Reuses the logic for image heaps (see {@link ImageHeapInfo}, {@link ImageHeapWalker}), but is
 * otherwise independent from {@link HeapImpl}.
 */
public class WasmHeap extends Heap {

    private final WasmObjectHeader objectHeader = new WasmObjectHeader();

    public final ImageHeapInfo imageHeapInfo = new ImageHeapInfo();

    private final WasmLMGC gc = new WasmLMGC();

    /**
     * A cached list of all the classes, if someone asks for it.
     */
    private List<Class<?>> classList;

    @Platforms(Platform.HOSTED_ONLY.class)
    public WasmHeap() {
    }

    @Fold
    public static WasmHeap getHeapImpl() {
        Heap heap = Heap.getHeap();
        assert heap instanceof WasmHeap : "VMConfiguration heap is not a WasmHeap. " + heap;
        return (WasmHeap) heap;
    }

    public static ImageHeapInfo getImageHeapInfo() {
        return getHeapImpl().imageHeapInfo;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void attachThread(IsolateThread isolateThread) {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void detachThread(IsolateThread isolateThread) {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public void suspendAllocation() {
        // Nothing to do...
    }

    @Override
    public void resumeAllocation() {
        // Nothing to do...
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isAllocationDisallowed() {
        return NoAllocationVerifier.isActive() || SafepointBehavior.ignoresSafepoints() || gc.isCollectionInProgress();
    }

    /** A guard to place before an allocation, giving the call site and the allocation type. */
    static void exitIfAllocationDisallowed(String callSite, String typeName) {
        if (getHeapImpl().isAllocationDisallowed()) {
            throw NoAllocationVerifier.exit(callSite, typeName);
        }
    }

    @Override
    public WasmLMGC getGC() {
        return gc;
    }

    @Override
    public RuntimeCodeInfoGCSupport getRuntimeCodeInfoGCSupport() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public void walkObjects(ObjectVisitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("must only be executed at a safepoint");
        walkImageHeapObjects(visitor);
        walkCollectedHeapObjects(visitor);
    }

    @Override
    public void walkImageHeapObjects(ObjectVisitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("Must only be called at a safepoint");
        if (visitor != null) {
            ImageHeapWalker.walkImageHeapObjects(imageHeapInfo, visitor);
            if (AuxiliaryImageHeap.isPresent()) {
                AuxiliaryImageHeap.singleton().walkObjects(visitor);
            }
        }
    }

    @Override
    public void walkCollectedHeapObjects(ObjectVisitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("Must only be called at a safepoint");
        WasmAllocation.walkObjects(visitor);
    }

    public void walkNativeImageHeapRegions(MemoryWalker.ImageHeapRegionVisitor visitor) {
        ImageHeapWalker.walkRegions(imageHeapInfo, visitor);
        if (AuxiliaryImageHeap.isPresent()) {
            AuxiliaryImageHeap.singleton().walkRegions(visitor);
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getClassCount() {
        return imageHeapInfo.dynamicHubCount;
    }

    @Override
    protected List<Class<?>> getClassesInImageHeap() {
        /* Two threads might race to set classList, but they compute the same result. */
        if (classList == null) {
            ArrayList<Class<?>> list = new ArrayList<>(imageHeapInfo.dynamicHubCount);
            ImageHeapWalker.walkRegions(imageHeapInfo, new ClassListBuilderVisitor(list));
            list.trimToSize();
            classList = list;
        }
        assert classList.size() == imageHeapInfo.dynamicHubCount;
        return classList;
    }

    @Uninterruptible(reason = "Necessary to return a reasonably consistent value (a GC can change the queried values).")
    public UnsignedWord getUsedBytes() {
        return Word.unsigned(WasmAllocation.getObjectSize());
    }

    private static final class ClassListBuilderVisitor implements MemoryWalker.ImageHeapRegionVisitor, ObjectVisitor {
        private final List<Class<?>> list;

        private ClassListBuilderVisitor(List<Class<?>> list) {
            this.list = list;
        }

        @Override
        public <T> void visitNativeImageHeapRegion(T region, MemoryWalker.NativeImageHeapRegionAccess<T> access) {
            if (!access.isWritable(region) && !access.consistsOfHugeObjects(region)) {
                access.visitObjects(region, this);
            }
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "Allocation is fine: this method traverses only the image heap.")
        public void visitObject(Object o) {
            if (o instanceof Class<?>) {
                list.add((Class<?>) o);
            }
        }

    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public ObjectHeader getObjectHeader() {
        return objectHeader;
    }

    WasmObjectHeader getObjectHeaderImpl() {
        return objectHeader;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean tearDown() {
        throw VMError.shouldNotReachHere("WasmHeap.tearDown");
    }

    @Override
    public void prepareForSafepoint() {
        // Nothing to do
    }

    @Override
    public void endSafepoint() {
        // Nothing to do
    }

    @Override
    public int getHeapBaseAlignment() {
        return 1;
    }

    @Override
    public int getImageHeapAlignment() {
        return 1;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public Pointer getImageHeapStart() {
        return Isolates.IMAGE_HEAP_BEGIN.get().add(getImageHeapOffsetInAddressSpace());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getImageHeapOffsetInAddressSpace() {
        return 0;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInImageHeap(Object obj) {
        // This method is not really uninterruptible (mayBeInlined) but converts arbitrary objects
        // to pointers. An object that is outside the image heap may be moved by a GC but it will
        // never be moved into the image heap. So, this is fine.
        return isInImageHeap(Word.objectToUntrackedPointer(obj));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInImageHeap(Pointer objPointer) {
        return isInPrimaryImageHeap(objPointer);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInPrimaryImageHeap(Object obj) {
        // This method is not really uninterruptible (mayBeInlined) but converts arbitrary objects
        // to pointers. An object that is outside the image heap may be moved by a GC but it will
        // never be moved into the image heap. So, this is fine.
        return isInPrimaryImageHeap(Word.objectToUntrackedPointer(obj));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInPrimaryImageHeap(Pointer objPointer) {
        return imageHeapInfo.isInImageHeap(objPointer);
    }

    boolean isInHeap(Pointer ptr) {
        return isInImageHeap(ptr) || WasmAllocation.isObjectPointer(ptr);
    }

    @Override
    public void doReferenceHandling() {
        throw VMError.shouldNotReachHere("WasmHeap.doReferenceHandling");
    }

    @Override
    public boolean hasReferencePendingList() {
        throw VMError.shouldNotReachHere("WasmHeap.hasReferencePendingList");
    }

    @Override
    public void waitForReferencePendingList() {
        throw VMError.shouldNotReachHere("WasmHeap.waitForReferencePendingList");
    }

    @Override
    public void wakeUpReferencePendingListWaiters() {
        throw VMError.shouldNotReachHere("WasmHeap.wakeUpReferencePendingListWaiters");
    }

    @Override
    public Reference<?> getAndClearReferencePendingList() {
        throw VMError.shouldNotReachHere("WasmHeap.getAndClearReferencePendingList");
    }

    @Override
    public boolean printLocationInfo(Log log, UnsignedWord value, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        throw VMError.shouldNotReachHere("WasmHeap.printLocationInfo");
    }

    @Override
    public void optionValueChanged(RuntimeOptionKey<?> key) {
        // Nothing to do
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getThreadAllocatedMemory(IsolateThread thread) {
        throw VMError.shouldNotReachHere("WasmHeap.getThreadAllocatedMemory");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getUsedMemoryAfterLastGC() {
        throw VMError.shouldNotReachHere("WasmHeap.getUsedMemoryAfterLastGC");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void dirtyAllReferencesOf(Object obj) {
        throw VMError.shouldNotReachHere("WasmHeap.dirtyAllReferencesOf");
    }

    @Override
    public long getMillisSinceLastWholeHeapExamined() {
        throw VMError.shouldNotReachHere("WasmHeap.getMillisSinceLastWholeHeapExamined");
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getIdentityHashSalt(Object obj) {
        ReplacementsUtil.staticAssert(false, "identity hash codes are never computed from addresses");
        return 0;
    }

    @Override
    public boolean verifyImageHeapMapping() {
        return true;
    }
}
