/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.remset;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.util.List;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.HeapParameters;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.SerialGCOptions;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.genscavenge.graal.SubstrateCardTableBarrierSet;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.PodReferenceMapDecoder;
import com.oracle.svm.core.heap.UninterruptibleObjectReferenceVisitor;
import com.oracle.svm.core.heap.UninterruptibleObjectVisitor;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.hub.HubType;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.metaspace.Metaspace;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.HostedByteBufferPointer;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.nodes.gc.BarrierSet;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A card table based remembered set where the {@link CardTable} and the {@link FirstObjectTable}
 * are placed in the individual {@link HeapChunk}s.
 */
public class CardTableBasedRememberedSet implements RememberedSet {
    @Platforms(Platform.HOSTED_ONLY.class)
    public CardTableBasedRememberedSet() {
    }

    @Override
    public BarrierSet createBarrierSet(MetaAccessProvider metaAccess) {
        ResolvedJavaType objectArrayType = metaAccess.lookupJavaType(Object[].class);
        return new SubstrateCardTableBarrierSet(objectArrayType);
    }

    @Override
    public UnsignedWord getHeaderSizeOfAlignedChunk() {
        return AlignedChunkRememberedSet.getHeaderSize();
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord getHeaderSizeOfUnalignedChunk(UnsignedWord objectSize) {
        return UnalignedChunkRememberedSet.getHeaderSize(objectSize);
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void setObjectStartOffsetOfUnalignedChunk(HostedByteBufferPointer chunk, UnsignedWord objectStartOffset) {
        UnalignedChunkRememberedSet.setObjectStartOffset(chunk, objectStartOffset);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void setObjectStartOffsetOfUnalignedChunk(UnalignedHeader chunk, UnsignedWord objectStartOffset) {
        UnalignedChunkRememberedSet.setObjectStartOffset(chunk, objectStartOffset);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord getObjectStartOffsetOfUnalignedChunk(UnalignedHeader chunk) {
        return UnalignedChunkRememberedSet.getObjectStartOffset(chunk);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public UnsignedWord getOffsetForObjectInUnalignedChunk(Pointer objPtr) {
        return UnalignedChunkRememberedSet.getOffsetForObject(objPtr);
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void enableRememberedSetForAlignedChunk(HostedByteBufferPointer chunk, int chunkPosition, List<ImageHeapObject> objects) {
        AlignedChunkRememberedSet.enableRememberedSet(chunk, chunkPosition, objects);
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void enableRememberedSetForUnalignedChunk(HostedByteBufferPointer chunk, UnsignedWord objectSize) {
        UnalignedChunkRememberedSet.enableRememberedSet(chunk, objectSize);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void enableRememberedSetForChunk(AlignedHeader chunk) {
        AlignedChunkRememberedSet.enableRememberedSet(chunk);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void enableRememberedSetForChunk(UnalignedHeader chunk) {
        UnalignedChunkRememberedSet.enableRememberedSet(chunk);
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void enableRememberedSetForObject(AlignedHeader chunk, Object obj, UnsignedWord objSize) {
        AlignedChunkRememberedSet.enableRememberedSetForObject(chunk, obj, objSize);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void clearRememberedSet(AlignedHeader chunk) {
        AlignedChunkRememberedSet.clearRememberedSet(chunk);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void clearRememberedSet(UnalignedHeader chunk) {
        UnalignedChunkRememberedSet.clearRememberedSet(chunk);
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean hasRememberedSet(UnsignedWord header) {
        return ObjectHeaderImpl.hasRememberedSet(header);
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void dirtyCardForAlignedObject(Object object, boolean verifyOnly) {
        AlignedChunkRememberedSet.dirtyCardForObject(object, verifyOnly);
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void dirtyCardForUnalignedObject(Object object, Pointer address, boolean verifyOnly) {
        UnalignedChunkRememberedSet.dirtyCardForObject(object, address, verifyOnly);
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void dirtyCardRangeForUnalignedObject(Object object, Pointer startAddress, Pointer endAddress) {
        UnalignedChunkRememberedSet.dirtyCardRangeForObject(object, startAddress, endAddress);
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void dirtyCardIfNecessary(Object holderObject, Object object, Pointer objRef) {
        if (holderObject == null || object == null) {
            return;
        }

        assert !HeapImpl.getHeapImpl().isInImageHeap(object) : "should never be called for references to image heap objects";

        if (cardNeedsDirtying(holderObject, object)) {
            if (ObjectHeaderImpl.isAlignedObject(holderObject)) {
                AlignedChunkRememberedSet.dirtyCardForObject(holderObject, false);
            } else {
                assert ObjectHeaderImpl.isUnalignedObject(holderObject);
                UnalignedChunkRememberedSet.dirtyCardForObject(holderObject, objRef, false);
            }
        }
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private boolean cardNeedsDirtying(Object holderObject, Object object) {
        assert holderObject != null && object != null;

        if (GCImpl.getGCImpl().isCompleteCollection()) {
            /*
             * After a full GC, the young generation is empty. So, there would be nothing to do.
             * However, we want to keep track of all the references that the image heap or metaspace
             * have to the runtime heap (regardless of the runtime heap generation). We clean and
             * remark those only during complete collections.
             */
            return HeapImpl.usesImageHeapCardMarking() && HeapImpl.getHeapImpl().isInImageHeap(holderObject) ||
                            SerialGCOptions.useRememberedSet() && Metaspace.singleton().isInAddressSpace(holderObject);
        }

        /*
         * If we don't have any survivor spaces, then there is nothing to do because the young
         * generation will be empty after a young GC.
         */
        if (HeapParameters.getMaxSurvivorSpaces() == 0) {
            return false;
        }

        /*
         * Dirty the card table for references from the image heap, metaspace, or old generation to
         * the young generation.
         */
        if (HeapImpl.getHeapImpl().getYoungGeneration().contains(object)) {
            ObjectHeader oh = Heap.getHeap().getObjectHeader();
            UnsignedWord objectHeader = oh.readHeaderFromObject(holderObject);
            return hasRememberedSet(objectHeader);
        }
        return false;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void dirtyAllReferencesIfNecessary(Object obj) {
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        Word header = oh.readHeaderFromObject(obj);
        if (RememberedSet.get().hasRememberedSet(header) && mayContainReferences(obj)) {
            dirtyAllReferencesOf(obj);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean mayContainReferences(Object obj) {
        DynamicHub hub = KnownIntrinsics.readHub(obj);
        int hubType = hub.getHubType();
        return switch (hubType) {
            case HubType.INSTANCE -> !DynamicHubSupport.hasEmptyReferenceMap(hub);
            case HubType.POD_INSTANCE -> !DynamicHubSupport.hasEmptyReferenceMap(hub) || !PodReferenceMapDecoder.hasEmptyReferenceMap(obj);
            case HubType.REFERENCE_INSTANCE, HubType.STORED_CONTINUATION_INSTANCE -> true;
            case HubType.PRIMITIVE_ARRAY -> false;
            case HubType.OBJECT_ARRAY -> ((Object[]) obj).length > 0;
            default -> throw VMError.shouldNotReachHere("Unexpected hub type.");
        };
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void dirtyAllReferencesOf(Object obj) {
        if (ObjectHeaderImpl.isAlignedObject(obj)) {
            AlignedChunkRememberedSet.dirtyAllReferencesOf(obj);
        } else {
            assert ObjectHeaderImpl.isUnalignedObject(obj);
            UnalignedChunkRememberedSet.dirtyAllReferencesOf(obj);
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void walkDirtyObjects(AlignedHeader firstAlignedChunk, UnalignedHeader firstUnalignedChunk, UnalignedHeader lastUnalignedChunk, UninterruptibleObjectVisitor visitor,
                    UninterruptibleObjectReferenceVisitor refVisitor, boolean clean) {
        AlignedHeader aChunk = firstAlignedChunk;
        while (aChunk.isNonNull()) {
            AlignedChunkRememberedSet.walkDirtyObjects(aChunk, visitor, clean);
            aChunk = HeapChunk.getNext(aChunk);
        }

        UnalignedHeader uChunk = firstUnalignedChunk;
        while (uChunk.isNonNull()) {
            UnalignedChunkRememberedSet.walkDirtyObjects(uChunk, refVisitor, clean);
            if (uChunk.equal(lastUnalignedChunk)) {
                break;
            }
            uChunk = HeapChunk.getNext(uChunk);
        }
    }

    @Override
    public boolean verify(AlignedHeader firstAlignedHeapChunk) {
        boolean success = true;
        AlignedHeader aChunk = firstAlignedHeapChunk;
        while (aChunk.isNonNull()) {
            success &= AlignedChunkRememberedSet.verify(aChunk);
            aChunk = HeapChunk.getNext(aChunk);
        }
        return success;
    }

    @Override
    public boolean verify(UnalignedHeader firstUnalignedHeapChunk) {
        return verify(firstUnalignedHeapChunk, Word.nullPointer());
    }

    @Override
    public boolean verify(UnalignedHeader firstUnalignedHeapChunk, UnalignedHeader lastUnalignedHeapChunk) {
        boolean success = true;
        UnalignedHeader uChunk = firstUnalignedHeapChunk;
        while (uChunk.isNonNull()) {
            success &= UnalignedChunkRememberedSet.verify(uChunk);
            if (uChunk.equal(lastUnalignedHeapChunk)) {
                break;
            }
            uChunk = HeapChunk.getNext(uChunk);
        }
        return success;
    }

    @Override
    public boolean usePreciseCardMarking(Object obj) {
        if (ObjectHeaderImpl.isAlignedObject(obj)) {
            return AlignedChunkRememberedSet.usePreciseCardMarking();
        } else {
            assert ObjectHeaderImpl.isUnalignedObject(obj);
            return UnalignedChunkRememberedSet.usePreciseCardMarking(obj);
        }
    }
}
