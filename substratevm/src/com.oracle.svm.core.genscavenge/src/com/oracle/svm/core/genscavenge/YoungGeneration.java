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

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.GCImpl.ChunkReleaser;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;

public final class YoungGeneration extends Generation {
    private final Space eden;
    private final Space[] survivorFromSpaces;
    private final Space[] survivorToSpaces;
    private final GreyObjectsWalker[] survivorGreyObjectsWalkers;
    private final ChunksAccounting survivorsToSpacesAccounting;
    private final int maxSurvivorSpaces;
    private final HeapAllocation heapAllocation = new HeapAllocation();

    @Platforms(Platform.HOSTED_ONLY.class)
    YoungGeneration(String name) {
        super(name);
        this.eden = new Space("Eden", "E", false, 0);
        this.maxSurvivorSpaces = HeapParameters.getMaxSurvivorSpaces();
        this.survivorFromSpaces = new Space[maxSurvivorSpaces];
        this.survivorToSpaces = new Space[maxSurvivorSpaces];
        this.survivorGreyObjectsWalkers = new GreyObjectsWalker[maxSurvivorSpaces];
        this.survivorsToSpacesAccounting = new ChunksAccounting();
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            int age = i + 1;
            this.survivorFromSpaces[i] = new Space("Survivor-" + age, "S" + age, false, age);
            this.survivorToSpaces[i] = new Space("Survivor-" + age + " To", "S" + age, true, age, survivorsToSpacesAccounting);
            this.survivorGreyObjectsWalkers[i] = new GreyObjectsWalker();
        }
    }

    @Fold
    public static HeapAllocation getHeapAllocation() {
        return HeapImpl.getHeapImpl().getYoungGeneration().heapAllocation;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getMaxSurvivorSpaces() {
        return maxSurvivorSpaces;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void tearDown() {
        heapAllocation.tearDown();
        TlabSupport.tearDown();
        eden.tearDown();
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            survivorFromSpaces[i].tearDown();
            survivorToSpaces[i].tearDown();
        }
    }

    @Override
    public void walkObjects(ObjectVisitor visitor) {
        getEden().walkObjects(visitor);
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            survivorFromSpaces[i].walkObjects(visitor);
            survivorToSpaces[i].walkObjects(visitor);
        }
    }

    @Override
    public void logUsage(Log log) {
        getEden().logUsage(log, true);
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            this.survivorFromSpaces[i].logUsage(log, false);
            this.survivorToSpaces[i].logUsage(log, false);
        }
    }

    public void logChunks(Log log, boolean allowUnsafe) {
        if (allowUnsafe) {
            heapAllocation.logChunks(log, eden.getShortName());

            for (IsolateThread thread = VMThreads.firstThreadUnsafe(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
                TlabSupport.logTlabChunks(log, thread, eden.getShortName());
            }
        }

        getEden().logChunks(log);
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            this.survivorFromSpaces[i].logChunks(log);
            this.survivorToSpaces[i].logChunks(log);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Space getEden() {
        return eden;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Space getSurvivorToSpaceAt(int index) {
        assert index >= 0 && index < maxSurvivorSpaces : "Survivor index should be between 0 and NumberOfSurvivorSpaces";
        return survivorToSpaces[index];
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Space getSurvivorFromSpaceAt(int index) {
        assert index >= 0 && index < maxSurvivorSpaces : "Survivor index should be between 0 and NumberOfSurvivorSpaces";
        return survivorFromSpaces[index];
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private GreyObjectsWalker getSurvivorGreyObjectsWalker(int index) {
        return survivorGreyObjectsWalkers[index];
    }

    void releaseSpaces(ChunkReleaser chunkReleaser) {
        getEden().releaseChunks(chunkReleaser);
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            getSurvivorFromSpaceAt(i).releaseChunks(chunkReleaser);
        }
    }

    void swapSpaces() {
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            assert getSurvivorFromSpaceAt(i).isEmpty() : "Survivor fromSpace should be empty.";
            assert getSurvivorFromSpaceAt(i).getChunkBytes().equal(0) : "Chunk bytes must be 0";
            getSurvivorFromSpaceAt(i).absorb(getSurvivorToSpaceAt(i));
        }
        assert survivorsToSpacesAccounting.getChunkBytes().equal(0);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void beginPromotion() {
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            assert getSurvivorToSpaceAt(i).isEmpty() : "SurvivorToSpace should be empty.";
            getSurvivorGreyObjectsWalker(i).setScanStart(getSurvivorToSpaceAt(i));
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean scanGreyObjects() {
        boolean needScan = false;
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            if (getSurvivorGreyObjectsWalker(i).haveGreyObjects()) {
                needScan = true;
                break;
            }
        }
        if (!needScan) {
            return false;
        }
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            getSurvivorGreyObjectsWalker(i).walkGreyObjects();
        }
        return true;
    }

    /**
     * This value is only updated during a GC, so it may be outdated if called from outside the GC
     * VM operation. Also be careful when calling this method during a GC as it might wrongly
     * include chunks that will be freed at the end of the GC.
     */
    UnsignedWord getChunkBytes() {
        return getEden().getChunkBytes().add(getSurvivorChunkBytes());
    }

    /**
     * This value is only updated during a GC, be careful: see {@link #getChunkBytes}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord getSurvivorChunkBytes() {
        UnsignedWord chunkBytes = Word.zero();
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            chunkBytes = chunkBytes.add(getSurvivorChunkBytes(i));
        }
        return chunkBytes;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord getSurvivorChunkBytes(int survivorIndex) {
        return survivorFromSpaces[survivorIndex].getChunkBytes().add(survivorToSpaces[survivorIndex].getChunkBytes());
    }

    /**
     * This value is only updated during a GC, be careful: see {@link #getChunkBytes}.
     */
    UnsignedWord getAlignedChunkBytes() {
        return getEden().getAlignedChunkBytes().add(getSurvivorAlignedChunkBytes());
    }

    /**
     * This value is only updated during a GC, be careful: see {@link #getChunkBytes}.
     */
    UnsignedWord getSurvivorAlignedChunkBytes() {
        UnsignedWord chunkBytes = Word.zero();
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            chunkBytes = chunkBytes.add(this.survivorFromSpaces[i].getAlignedChunkBytes());
            chunkBytes = chunkBytes.add(this.survivorToSpaces[i].getAlignedChunkBytes());
        }
        return chunkBytes;
    }

    UnsignedWord computeObjectBytes() {
        return getEden().computeObjectBytes().add(computeSurvivorObjectBytes());
    }

    UnsignedWord computeSurvivorObjectBytes() {
        UnsignedWord usedObjectBytes = Word.zero();
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            usedObjectBytes = usedObjectBytes.add(survivorFromSpaces[i].computeObjectBytes());
            usedObjectBytes = usedObjectBytes.add(survivorToSpaces[i].computeObjectBytes());
        }
        return usedObjectBytes;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @SuppressWarnings("static-method")
    public boolean contains(Object object) {
        if (!HeapImpl.usesImageHeapCardMarking()) {
            return HeapChunk.getSpace(HeapChunk.getEnclosingHeapChunk(object)).isYoungSpace();
        }
        // Only objects in the young generation have no remembered set
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        UnsignedWord header = oh.readHeaderFromObject(object);
        boolean young = !ObjectHeaderImpl.hasRememberedSet(header);
        assert young == HeapChunk.getSpace(HeapChunk.getEnclosingHeapChunk(object)).isYoungSpace();
        return young;
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected Object promoteAlignedObject(Object original, AlignedHeapChunk.AlignedHeader originalChunk, Space originalSpace) {
        assert originalSpace.isFromSpace();
        assert ObjectHeaderImpl.isAlignedObject(original);
        assert originalSpace.getAge() < maxSurvivorSpaces;

        // The object might fit in an existing chunk in the survivor space. If it doesn't, we get
        // called back in requestAlignedSurvivorChunk() and decide if another chunk fits in the
        // survivor space. If it does not, we return null here to tell the caller.
        int age = originalSpace.getNextAgeForPromotion();
        Space toSpace = getSurvivorToSpaceAt(age - 1);
        return ObjectPromoter.copyAlignedObject(original, originalSpace, toSpace);
    }

    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    protected Object promoteUnalignedObject(Object original, UnalignedHeapChunk.UnalignedHeader originalChunk, Space originalSpace) {
        assert originalSpace.isFromSpace();
        assert originalSpace.getAge() < maxSurvivorSpaces;
        if (!unalignedChunkFitsInSurvivors(originalChunk)) {
            return null;
        }

        int age = originalSpace.getNextAgeForPromotion();
        Space toSpace = getSurvivorToSpaceAt(age - 1);
        ObjectPromoter.promoteUnalignedHeapChunk(originalChunk, originalSpace, toSpace);
        return original;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    protected boolean promotePinnedObject(Object obj, HeapChunk.Header<?> originalChunk, boolean isAligned, Space originalSpace) {
        assert originalSpace.isFromSpace();
        assert originalSpace.getAge() < maxSurvivorSpaces;
        if (!fitsInSurvivors(originalChunk, isAligned)) {
            return false;
        }

        int age = originalSpace.getNextAgeForPromotion();
        Space toSpace = getSurvivorToSpaceAt(age - 1);
        if (isAligned) {
            ObjectPromoter.promoteAlignedHeapChunk((AlignedHeapChunk.AlignedHeader) originalChunk, originalSpace, toSpace);
        } else {
            ObjectPromoter.promoteUnalignedHeapChunk((UnalignedHeapChunk.UnalignedHeader) originalChunk, originalSpace, toSpace);
        }
        return true;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean fitsInSurvivors(HeapChunk.Header<?> chunk, boolean isAligned) {
        if (isAligned) {
            return alignedChunkFitsInSurvivors();
        }
        return unalignedChunkFitsInSurvivors((UnalignedHeapChunk.UnalignedHeader) chunk);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean alignedChunkFitsInSurvivors() {
        UnsignedWord sum = survivorsToSpacesAccounting.getChunkBytes().add(HeapParameters.getAlignedHeapChunkSize());
        return sum.belowOrEqual(GCImpl.getPolicy().getSurvivorSpacesCapacity());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private boolean unalignedChunkFitsInSurvivors(UnalignedHeapChunk.UnalignedHeader chunk) {
        UnsignedWord size = UnalignedHeapChunk.getCommittedObjectMemory(chunk);
        UnsignedWord sum = survivorsToSpacesAccounting.getChunkBytes().add(size);
        return sum.belowOrEqual(GCImpl.getPolicy().getSurvivorSpacesCapacity());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    AlignedHeapChunk.AlignedHeader requestAlignedSurvivorChunk() {
        assert VMOperation.isGCInProgress() : "Should only be called from the collector.";
        if (!alignedChunkFitsInSurvivors()) {
            return Word.nullPointer();
        }
        return HeapImpl.getChunkProvider().produceAlignedChunk();
    }

    @Override
    public void checkSanityAfterCollection() {
        assert eden.isEmpty() : "eden should be empty after a collection.";
    }

    boolean isInSpace(Pointer ptr) {
        if (getEden().contains(ptr)) {
            return true;
        }
        for (int i = 0; i < getMaxSurvivorSpaces(); i++) {
            if (getSurvivorFromSpaceAt(i).contains(ptr)) {
                return true;
            }
            if (getSurvivorToSpaceAt(i).contains(ptr)) {
                return true;
            }
        }
        return false;
    }

    public boolean printLocationInfo(Log log, Pointer ptr) {
        if (getEden().printLocationInfo(log, ptr)) {
            return true;
        }
        for (int i = 0; i < getMaxSurvivorSpaces(); i++) {
            if (getSurvivorFromSpaceAt(i).printLocationInfo(log, ptr)) {
                return true;
            }
            if (getSurvivorToSpaceAt(i).printLocationInfo(log, ptr)) {
                return true;
            }
        }
        return false;
    }

    void makeParseable() {
        TlabSupport.disableAndFlushForAllThreads();
        heapAllocation.retireChunksToEden();
    }

}
