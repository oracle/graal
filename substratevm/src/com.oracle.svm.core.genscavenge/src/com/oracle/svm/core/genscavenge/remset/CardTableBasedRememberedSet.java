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

import java.util.List;

import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.GCImpl;
import com.oracle.svm.core.genscavenge.GreyToBlackObjectVisitor;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.HeapParameters;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.genscavenge.graal.SubstrateCardTableBarrierSet;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.image.ImageHeapObject;
import com.oracle.svm.core.util.HostedByteBufferPointer;

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
    public UnsignedWord getHeaderSizeOfUnalignedChunk() {
        return UnalignedChunkRememberedSet.getHeaderSize();
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void enableRememberedSetForAlignedChunk(HostedByteBufferPointer chunk, int chunkPosition, List<ImageHeapObject> objects) {
        AlignedChunkRememberedSet.enableRememberedSet(chunk, chunkPosition, objects);
    }

    @Override
    @Platforms(Platform.HOSTED_ONLY.class)
    public void enableRememberedSetForUnalignedChunk(HostedByteBufferPointer chunk) {
        UnalignedChunkRememberedSet.enableRememberedSet(chunk);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void enableRememberedSetForChunk(AlignedHeader chunk) {
        AlignedChunkRememberedSet.enableRememberedSet(chunk);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void enableRememberedSetForChunk(UnalignedHeader chunk) {
        UnalignedChunkRememberedSet.enableRememberedSet(chunk);
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void enableRememberedSetForObject(AlignedHeader chunk, Object obj, UnsignedWord objSize) {
        AlignedChunkRememberedSet.enableRememberedSetForObject(chunk, obj, objSize);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void clearRememberedSet(AlignedHeader chunk) {
        AlignedChunkRememberedSet.clearRememberedSet(chunk);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void clearRememberedSet(UnalignedHeader chunk) {
        UnalignedChunkRememberedSet.clearRememberedSet(chunk);
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean hasRememberedSet(UnsignedWord header) {
        return ObjectHeaderImpl.hasRememberedSet(header);
    }

    @Override
    @AlwaysInline("GC performance")
    public void dirtyCardForAlignedObject(Object object, boolean verifyOnly) {
        AlignedChunkRememberedSet.dirtyCardForObject(object, verifyOnly);
    }

    @Override
    @AlwaysInline("GC performance")
    public void dirtyCardForUnalignedObject(Object object, boolean verifyOnly) {
        UnalignedChunkRememberedSet.dirtyCardForObject(object, verifyOnly);
    }

    @Override
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void dirtyCardIfNecessary(Object holderObject, Object object) {
        if (holderObject == null || object == null) {
            return;
        }
        // We dirty the cards of ...
        if (HeapParameters.getMaxSurvivorSpaces() != 0 && !GCImpl.getGCImpl().isCompleteCollection() && HeapImpl.getHeapImpl().getYoungGeneration().contains(object)) {
            /*
             * ...references from the old generation to the young generation, unless there cannot be
             * any such references if we do not use survivor spaces, or if we do but are doing a
             * complete collection: in both cases, all objects are promoted to the old generation.
             * (We avoid an extra old generation check and might remark a few image heap cards, too)
             */
        } else if (HeapImpl.usesImageHeapCardMarking() && GCImpl.getGCImpl().isCompleteCollection() && HeapImpl.getHeapImpl().isInImageHeap(holderObject)) {
            // ...references from the image heap to the runtime heap, but we clean and remark those
            // only during complete collections.
            assert !HeapImpl.getHeapImpl().isInImageHeap(object) : "should never be called for references to image heap objects";
        } else {
            return;
        }

        UnsignedWord objectHeader = ObjectHeader.readHeaderFromObject(holderObject);
        if (hasRememberedSet(objectHeader)) {
            if (ObjectHeaderImpl.isAlignedObject(holderObject)) {
                AlignedChunkRememberedSet.dirtyCardForObject(holderObject, false);
            } else {
                assert ObjectHeaderImpl.isUnalignedObject(holderObject) : "sanity";
                UnalignedChunkRememberedSet.dirtyCardForObject(holderObject, false);
            }
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void walkDirtyObjects(AlignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean) {
        AlignedChunkRememberedSet.walkDirtyObjects(chunk, visitor, clean);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void walkDirtyObjects(UnalignedHeader chunk, GreyToBlackObjectVisitor visitor, boolean clean) {
        UnalignedChunkRememberedSet.walkDirtyObjects(chunk, visitor, clean);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void walkDirtyObjects(Space space, GreyToBlackObjectVisitor visitor, boolean clean) {
        AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            walkDirtyObjects(aChunk, visitor, clean);
            aChunk = HeapChunk.getNext(aChunk);
        }

        UnalignedHeader uChunk = space.getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            walkDirtyObjects(uChunk, visitor, clean);
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
        boolean success = true;
        UnalignedHeader uChunk = firstUnalignedHeapChunk;
        while (uChunk.isNonNull()) {
            success &= UnalignedChunkRememberedSet.verify(uChunk);
            uChunk = HeapChunk.getNext(uChunk);
        }
        return success;
    }
}
