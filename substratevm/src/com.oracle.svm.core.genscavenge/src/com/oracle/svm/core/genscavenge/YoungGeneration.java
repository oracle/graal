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
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.genscavenge.GCImpl.ChunkReleaser;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.log.Log;

public final class YoungGeneration extends Generation {
    private final Space eden;
    private final Space[] survivorFromSpaces;
    private final Space[] survivorToSpaces;
    private final GreyObjectsWalker[] survivorGreyObjectsWalkers;
    private final int maxSurvivorSpaces;

    @Platforms(Platform.HOSTED_ONLY.class)
    YoungGeneration(String name) {
        super(name);
        this.eden = new Space("edenSpace", true, 0);
        this.maxSurvivorSpaces = HeapPolicy.getMaxSurvivorSpaces();
        this.survivorFromSpaces = new Space[maxSurvivorSpaces];
        this.survivorToSpaces = new Space[maxSurvivorSpaces];
        this.survivorGreyObjectsWalkers = new GreyObjectsWalker[maxSurvivorSpaces];
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            this.survivorFromSpaces[i] = new Space("Survivor-" + (i + 1) + " From", true, (i + 1));
            this.survivorToSpaces[i] = new Space("Survivor-" + (i + 1) + " To", false, (i + 1));
            this.survivorGreyObjectsWalkers[i] = new GreyObjectsWalker();
        }
    }

    public int getMaxSurvivorSpaces() {
        return maxSurvivorSpaces;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void tearDown() {
        ThreadLocalAllocation.tearDown();
        eden.tearDown();
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            survivorFromSpaces[i].tearDown();
            survivorToSpaces[i].tearDown();
        }
    }

    @Override
    public boolean walkObjects(ObjectVisitor visitor) {
        /* Flush the thread-local allocation data. */
        ThreadLocalAllocation.disableAndFlushForAllThreads();

        if (!getEden().walkObjects(visitor)) {
            return false;
        }
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            if (!survivorFromSpaces[i].walkObjects(visitor)) {
                return false;
            }
            if (!survivorToSpaces[i].walkObjects(visitor)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Log report(Log log, boolean traceHeapChunks) {
        log.string("Young generation: ").indent(true);
        log.string("Eden: ").indent(true);
        getEden().report(log, traceHeapChunks);
        log.redent(false).newline();
        log.string("Survivors: ").indent(true);
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            this.survivorFromSpaces[i].report(log, traceHeapChunks).newline();
            this.survivorToSpaces[i].report(log, traceHeapChunks);
            if (i < maxSurvivorSpaces - 1) {
                log.newline();
            }
        }
        log.redent(false).redent(false);
        return log;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Space getEden() {
        return eden;
    }

    Space getSurvivorToSpaceAt(int index) {
        assert index >= 0 && index < maxSurvivorSpaces : "Survivor index should be between 0 and NumberOfSurvivorSpaces";
        return survivorToSpaces[index];
    }

    Space getSurvivorFromSpaceAt(int index) {
        assert index >= 0 && index < maxSurvivorSpaces : "Survivor index should be between 0 and NumberOfSurvivorSpaces";
        return survivorFromSpaces[index];
    }

    private GreyObjectsWalker getSurvivorGreyObjectsWalker(int index) {
        return survivorGreyObjectsWalkers[index];
    }

    @AlwaysInline("GC performance")
    @Override
    protected Object promoteObject(Object original, UnsignedWord header) {
        if (ObjectHeaderImpl.isAlignedHeader(header)) {
            AlignedHeapChunk.AlignedHeader originalChunk = AlignedHeapChunk.getEnclosingChunk(original);
            Space originalSpace = HeapChunk.getSpace(originalChunk);
            if (originalSpace.isFromSpace()) {
                return promoteAlignedObject(original, originalSpace);
            }
        } else {
            assert ObjectHeaderImpl.isUnalignedHeader(header);
            UnalignedHeapChunk.UnalignedHeader chunk = UnalignedHeapChunk.getEnclosingChunk(original);
            Space originalSpace = HeapChunk.getSpace(chunk);
            if (originalSpace.isFromSpace()) {
                promoteUnalignedObject(chunk, originalSpace);
            }
        }
        return original;
    }

    private void releaseSurvivorSpaces(ChunkReleaser chunkReleaser, boolean isFromSpace) {
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            if (isFromSpace) {
                getSurvivorFromSpaceAt(i).releaseChunks(chunkReleaser);
            } else {
                getSurvivorToSpaceAt(i).releaseChunks(chunkReleaser);
            }
        }
    }

    void releaseSpaces(ChunkReleaser chunkReleaser) {
        getEden().releaseChunks(chunkReleaser);

        releaseSurvivorSpaces(chunkReleaser, true);
        if (HeapImpl.getHeapImpl().getGCImpl().isCompleteCollection()) {
            releaseSurvivorSpaces(chunkReleaser, false);
        }
    }

    void swapSpaces() {
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            assert getSurvivorFromSpaceAt(i).isEmpty() : "Survivor fromSpace should be empty.";
            assert getSurvivorFromSpaceAt(i).getChunkBytes().equal(0) : "Chunk bytes must be 0";
            getSurvivorFromSpaceAt(i).absorb(getSurvivorToSpaceAt(i));
        }
    }

    boolean walkHeapChunks(MemoryWalker.Visitor visitor) {
        if (getEden().walkHeapChunks(visitor)) {
            for (int i = 0; i < maxSurvivorSpaces; i++) {
                if (!(getSurvivorFromSpaceAt(i).walkHeapChunks(visitor) && getSurvivorToSpaceAt(i).walkHeapChunks(visitor))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    void prepareForPromotion() {
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            assert getSurvivorToSpaceAt(i).isEmpty() : "SurvivorToSpace should be empty.";
            getSurvivorGreyObjectsWalker(i).setScanStart(getSurvivorToSpaceAt(i));
        }
    }

    boolean scanGreyObjects() {
        Log trace = Log.noopLog().string("[YoungGeneration.scanGreyObjects:");
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
            trace.string("[Scanning survivor-").signed(i).string("]").newline();
            getSurvivorGreyObjectsWalker(i).walkGreyObjects();
        }
        trace.string("]").newline();
        return true;
    }

    /**
     * This value is only updated during a GC. Be careful when calling this method during a GC as it
     * might wrongly include chunks that will be freed at the end of the GC.
     */
    UnsignedWord getChunkBytes() {
        return getEden().getChunkBytes().add(getSurvivorChunkBytes());
    }

    private UnsignedWord getSurvivorChunkBytes() {
        UnsignedWord chunkBytes = WordFactory.zero();
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            chunkBytes = chunkBytes.add(this.survivorFromSpaces[i].getChunkBytes());
            chunkBytes = chunkBytes.add(this.survivorToSpaces[i].getChunkBytes());
        }
        return chunkBytes;
    }

    UnsignedWord computeObjectBytes() {
        return getEden().computeObjectBytes().add(computeSurvivorObjectBytes());
    }

    private UnsignedWord computeSurvivorObjectBytes() {
        UnsignedWord usedObjectBytes = WordFactory.zero();
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            usedObjectBytes = usedObjectBytes.add(survivorFromSpaces[i].computeObjectBytes());
            usedObjectBytes = usedObjectBytes.add(survivorToSpaces[i].computeObjectBytes());
        }
        return usedObjectBytes;
    }

    @AlwaysInline("GC performance")
    @SuppressWarnings("static-method")
    public boolean contains(Object object) {
        if (!HeapImpl.usesImageHeapCardMarking()) {
            return HeapChunk.getSpace(HeapChunk.getEnclosingHeapChunk(object)).isYoungSpace();
        }
        // Only objects in the young generation have no remembered set
        UnsignedWord header = ObjectHeaderImpl.readHeaderFromObject(object);
        boolean young = !ObjectHeaderImpl.hasRememberedSet(header);
        assert young == HeapChunk.getSpace(HeapChunk.getEnclosingHeapChunk(object)).isYoungSpace();
        return young;
    }

    @AlwaysInline("GC performance")
    private Object promoteAlignedObject(Object original, Space originalSpace) {
        assert ObjectHeaderImpl.isAlignedObject(original);
        assert originalSpace.isEdenSpace() || originalSpace.isSurvivorSpace() : "Should be Eden or survivor.";
        assert originalSpace.isFromSpace() : "must not be called for other objects";

        if (originalSpace.getAge() < maxSurvivorSpaces) {
            int age = originalSpace.getNextAgeForPromotion();
            Space toSpace = getSurvivorToSpaceAt(age - 1);
            return toSpace.promoteAlignedObject(original, originalSpace);
        } else {
            return HeapImpl.getHeapImpl().getOldGeneration().promoteAlignedObject(original, originalSpace);
        }
    }

    @AlwaysInline("GC performance")
    private void promoteUnalignedObject(UnalignedHeapChunk.UnalignedHeader originalChunk, Space originalSpace) {
        assert originalSpace.isFromSpace() : "must not be called for other objects";

        if (originalSpace.getAge() < maxSurvivorSpaces) {
            int age = originalSpace.getNextAgeForPromotion();
            Space toSpace = getSurvivorToSpaceAt(age - 1);
            toSpace.promoteUnalignedHeapChunk(originalChunk, originalSpace);
        } else {
            HeapImpl.getHeapImpl().getOldGeneration().promoteUnalignedChunk(originalChunk, originalSpace);
        }
    }
}
