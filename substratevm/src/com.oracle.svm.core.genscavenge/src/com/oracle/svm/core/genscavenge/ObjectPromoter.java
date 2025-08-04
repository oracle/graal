/*
 * Copyright (c) 2013, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.identityhashcode.IdentityHashCodeSupport;
import com.oracle.svm.core.thread.VMOperation;

import jdk.graal.compiler.word.ObjectAccess;
import jdk.graal.compiler.word.Word;

/** Promotes individual objects or whole heap chunks to a target {@link Space}. */
public class ObjectPromoter {
    /** Promote an aligned Object to the target Space. */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static Object copyAlignedObject(Object original, Space originalSpace, Space targetSpace) {
        assert ObjectHeaderImpl.isAlignedObject(original);
        assert originalSpace.isFromSpace() || (originalSpace == targetSpace && targetSpace.isCompactingOldSpace());

        Object copy = copyAlignedObject(original, targetSpace);
        if (copy != null) {
            ObjectHeaderImpl.getObjectHeaderImpl().installForwardingPointer(original, copy);
        }
        return copy;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Object copyAlignedObject(Object originalObj, Space targetSpace) {
        assert VMOperation.isGCInProgress();
        assert ObjectHeaderImpl.isAlignedObject(originalObj);

        UnsignedWord originalSize = LayoutEncoding.getSizeFromObjectInlineInGC(originalObj, false);
        UnsignedWord copySize = originalSize;
        boolean addIdentityHashField = false;
        if (ConfigurationValues.getObjectLayout().isIdentityHashFieldOptional()) {
            ObjectHeader oh = Heap.getHeap().getObjectHeader();
            Word header = oh.readHeaderFromObject(originalObj);
            if (probability(SLOW_PATH_PROBABILITY, ObjectHeaderImpl.hasIdentityHashFromAddressInline(header))) {
                addIdentityHashField = true;
                copySize = LayoutEncoding.getSizeFromObjectInlineInGC(originalObj, true);
                assert copySize.aboveOrEqual(originalSize);
            }
        }

        Pointer copyMemory = allocateMemory(copySize, targetSpace);
        if (probability(VERY_SLOW_PATH_PROBABILITY, copyMemory.isNull())) {
            return null;
        }

        /*
         * This does a direct memory copy, without regard to whether the copied data contains object
         * references. That's okay, because all references in the copy are visited and overwritten
         * later on anyways (the card table is also updated at that point if necessary).
         */
        Pointer originalMemory = Word.objectToUntrackedPointer(originalObj);
        UnmanagedMemoryUtil.copyLongsForward(originalMemory, copyMemory, originalSize);

        Object copy = copyMemory.toObjectNonNull();
        if (probability(SLOW_PATH_PROBABILITY, addIdentityHashField)) {
            // Must do first: ensures correct object size below and in other places
            int value = IdentityHashCodeSupport.computeHashCodeFromAddress(originalObj);
            int offset = LayoutEncoding.getIdentityHashOffset(copy);
            ObjectAccess.writeInt(copy, offset, value, IdentityHashCodeSupport.IDENTITY_HASHCODE_LOCATION);
            ObjectHeaderImpl.getObjectHeaderImpl().setIdentityHashInField(copy);
        }
        if (targetSpace.isOldSpace()) {
            if (SerialGCOptions.useCompactingOldGen() && GCImpl.getGCImpl().isCompleteCollection()) {
                /*
                 * In a compacting complete collection, the remembered set bit is set already during
                 * marking and the first object table is built later during fix-up.
                 */
            } else {
                /*
                 * If an object was copied to the old generation, its remembered set bit must be set
                 * and the first object table must be updated (even when copying from old to old).
                 */
                AlignedHeapChunk.AlignedHeader copyChunk = AlignedHeapChunk.getEnclosingChunk(copy);
                RememberedSet.get().enableRememberedSetForObject(copyChunk, copy, copySize);
            }
        }
        return copy;
    }

    /** Promote an AlignedHeapChunk by moving it to the target space. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void promoteAlignedHeapChunk(AlignedHeapChunk.AlignedHeader chunk, Space originalSpace, Space targetSpace) {
        assert targetSpace != originalSpace && originalSpace.isFromSpace();

        originalSpace.extractAlignedHeapChunk(chunk);
        targetSpace.appendAlignedHeapChunk(chunk);

        if (targetSpace.isOldSpace()) {
            if (originalSpace.isYoungSpace()) {
                RememberedSet.get().enableRememberedSetForChunk(chunk);
            } else {
                assert originalSpace.isOldSpace();
                RememberedSet.get().clearRememberedSet(chunk);
            }
        }
    }

    /** Promote an UnalignedHeapChunk by moving it to the target Space. */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    static void promoteUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader chunk, Space originalSpace, Space targetSpace) {
        assert targetSpace != originalSpace && originalSpace.isFromSpace();

        originalSpace.extractUnalignedHeapChunk(chunk);
        targetSpace.appendUnalignedHeapChunk(chunk);

        if (targetSpace.isOldSpace()) {
            if (originalSpace.isYoungSpace()) {
                RememberedSet.get().enableRememberedSetForChunk(chunk);
            } else {
                assert originalSpace.isOldSpace();
                RememberedSet.get().clearRememberedSet(chunk);
            }
        }
    }

    /**
     * Allocate memory from an AlignedHeapChunk in the target Space.
     */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Pointer allocateMemory(UnsignedWord objectSize, Space targetSpace) {
        Pointer result = Word.nullPointer();
        /* Fast-path: try allocating in the last chunk. */
        AlignedHeapChunk.AlignedHeader oldChunk = targetSpace.getLastAlignedHeapChunk();
        if (oldChunk.isNonNull()) {
            result = AlignedHeapChunk.tryAllocateMemory(oldChunk, objectSize);
        }
        if (result.isNonNull()) {
            return result;
        }
        /* Slow-path: try allocating a new chunk for the requested memory. */
        return allocateInNewChunk(objectSize, targetSpace);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Pointer allocateInNewChunk(UnsignedWord objectSize, Space targetSpace) {
        AlignedHeapChunk.AlignedHeader newChunk = requestAlignedHeapChunk(targetSpace);
        if (newChunk.isNonNull()) {
            return AlignedHeapChunk.tryAllocateMemory(newChunk, objectSize);
        }
        return Word.nullPointer();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static AlignedHeapChunk.AlignedHeader requestAlignedHeapChunk(Space targetSpace) {
        AlignedHeapChunk.AlignedHeader chunk;
        if (targetSpace.isYoungSpace()) {
            assert targetSpace.isSurvivorSpace();
            chunk = HeapImpl.getHeapImpl().getYoungGeneration().requestAlignedSurvivorChunk();
        } else {
            chunk = HeapImpl.getHeapImpl().getOldGeneration().requestAlignedChunk();
        }
        if (chunk.isNonNull()) {
            targetSpace.appendAlignedHeapChunk(chunk);
        }
        return chunk;
    }
}
