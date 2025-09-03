/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.GCImpl.ChunkReleaser;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.log.Log;

import jdk.graal.compiler.word.Word;

/**
 * An OldGeneration has two Spaces, {@link #fromSpace} for existing objects, and {@link #toSpace}
 * for newly-allocated or promoted objects.
 */
final class CopyingOldGeneration extends OldGeneration {
    /* These Spaces are final and are flipped by transferring chunks from one to the other. */
    private final Space fromSpace;
    private final Space toSpace;

    private final GreyObjectsWalker toGreyObjectsWalker = new GreyObjectsWalker();

    @Platforms(Platform.HOSTED_ONLY.class)
    CopyingOldGeneration(String name) {
        super(name);
        this.fromSpace = new Space("Old", "O", false, getAge());
        this.toSpace = new Space("Old To", "O", true, getAge());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void tearDown() {
        fromSpace.tearDown();
        toSpace.tearDown();
    }

    @Override
    public void walkObjects(ObjectVisitor visitor) {
        getFromSpace().walkObjects(visitor);
        getToSpace().walkObjects(visitor);
    }

    /** Promote an Object to ToSpace if it is not already in ToSpace. */
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public Object promoteAlignedObject(Object original, AlignedHeapChunk.AlignedHeader originalChunk, Space originalSpace) {
        assert originalSpace.isFromSpace();
        return ObjectPromoter.copyAlignedObject(original, originalSpace, getToSpace());
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected Object promoteUnalignedObject(Object original, UnalignedHeapChunk.UnalignedHeader originalChunk, Space originalSpace) {
        assert originalSpace.isFromSpace();
        ObjectPromoter.promoteUnalignedHeapChunk(originalChunk, originalSpace, getToSpace());
        return original;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected boolean promotePinnedObject(Object obj, HeapChunk.Header<?> originalChunk, boolean isAligned, Space originalSpace) {
        assert originalSpace.isFromSpace();
        if (isAligned) {
            ObjectPromoter.promoteAlignedHeapChunk((AlignedHeapChunk.AlignedHeader) originalChunk, originalSpace, getToSpace());
        } else {
            ObjectPromoter.promoteUnalignedHeapChunk((UnalignedHeapChunk.UnalignedHeader) originalChunk, originalSpace, getToSpace());
        }
        return true;
    }

    @Override
    void releaseSpaces(ChunkReleaser chunkReleaser) {
        getFromSpace().releaseChunks(chunkReleaser);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void beginPromotion(boolean incrementalGc) {
        if (incrementalGc) {
            emptyFromSpaceIntoToSpace();
        }
        toGreyObjectsWalker.setScanStart(getToSpace());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean scanGreyObjects(boolean incrementalGc) {
        if (!toGreyObjectsWalker.haveGreyObjects()) {
            return false;
        }
        toGreyObjectsWalker.walkGreyObjects();
        return true;
    }

    @Override
    public void logUsage(Log log) {
        getFromSpace().logUsage(log, true);
        getToSpace().logUsage(log, false);
    }

    @Override
    public void logChunks(Log log) {
        getFromSpace().logChunks(log);
        getToSpace().logChunks(log);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Space getFromSpace() {
        return fromSpace;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Space getToSpace() {
        return toSpace;
    }

    @Override
    void swapSpaces() {
        assert getFromSpace().isEmpty() : "fromSpace should be empty.";
        getFromSpace().absorb(getToSpace());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void blackenDirtyCardRoots(GreyToBlackObjectVisitor visitor, GreyToBlackObjRefVisitor refVisitor) {
        RememberedSet.get().walkDirtyObjects(toSpace.getFirstAlignedHeapChunk(), toSpace.getFirstUnalignedHeapChunk(), Word.nullPointer(), visitor, refVisitor, true);
    }

    @Override
    boolean isInSpace(Pointer ptr) {
        return fromSpace.contains(ptr) || toSpace.contains(ptr);
    }

    @Override
    boolean printLocationInfo(Log log, Pointer ptr) {
        return fromSpace.printLocationInfo(log, ptr) || toSpace.printLocationInfo(log, ptr);
    }

    @Override
    boolean verifyRememberedSets() {
        boolean success = true;
        success &= HeapVerifier.verifyRememberedSet(toSpace);
        success &= HeapVerifier.verifyRememberedSet(fromSpace);
        return success;
    }

    @Override
    boolean verifySpaces() {
        boolean success = true;
        if (!toSpace.isEmpty()) {
            Log.log().string("Old generation to-space contains chunks: firstAlignedChunk: ").zhex(toSpace.getFirstAlignedHeapChunk())
                            .string(", firstUnalignedChunk: ").zhex(toSpace.getFirstUnalignedHeapChunk()).newline();
            success = false;
        }
        success &= HeapVerifier.verifySpace(fromSpace);
        success &= HeapVerifier.verifySpace(toSpace);
        return success;
    }

    @Override
    void sweepAndCompact(Timers timers, ChunkReleaser chunkReleaser) {
        /*
         * Compaction occurred implicitly by copying live objects one after another. Sweeping could
         * be done on dead objects in pinned chunks, but is currently not implemented.
         */
    }

    /* Extract all the HeapChunks from FromSpace and append them to ToSpace. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void emptyFromSpaceIntoToSpace() {
        getToSpace().absorb(getFromSpace());
    }

    /**
     * This value is only updated during a GC. Be careful when calling this method during a GC as it
     * might wrongly include chunks that will be freed at the end of the GC.
     */
    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord getChunkBytes() {
        return fromSpace.getChunkBytes().add(toSpace.getChunkBytes());
    }

    @Override
    UnsignedWord computeObjectBytes() {
        return fromSpace.computeObjectBytes().add(toSpace.computeObjectBytes());
    }

    @Override
    void checkSanityBeforeCollection() {
        assert toSpace.isEmpty() : "toSpace should be empty before a collection.";
    }

    @Override
    void checkSanityAfterCollection() {
        assert toSpace.isEmpty() : "toSpace should be empty after a collection.";
    }
}
