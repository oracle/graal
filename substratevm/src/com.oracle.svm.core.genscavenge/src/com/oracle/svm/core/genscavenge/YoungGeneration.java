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

import com.oracle.svm.core.hub.LayoutEncoding;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.log.Log;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

/**
 * A Young Generation has one space, for ordinary objects.
 */
public class YoungGeneration extends Generation {

    // Final State.
    private final Space eden;
    private final Space[] survivorFromSpaces;
    private final Space[] survivorToSpaces;
    private final GreyObjectsWalker[] survivorGreyObjectsWalkers;
    private final int maxSurvivorSpaces;

    /* Constructors. */

    @Platforms(Platform.HOSTED_ONLY.class)
    YoungGeneration(String name) {
        this(name, new Space("edenSpace", true, 0));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private YoungGeneration(String name, Space space) {
        super(name);
        this.eden = space;
        this.maxSurvivorSpaces = HeapPolicy.getMaxSurvivorSpaces();
        this.survivorFromSpaces = new Space[maxSurvivorSpaces];
        this.survivorToSpaces = new Space[maxSurvivorSpaces];
        this.survivorGreyObjectsWalkers = new GreyObjectsWalker[maxSurvivorSpaces];
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            this.survivorFromSpaces[i] = new Space("Survivor-" + (i + 1) + " From", true, (i + 1));
            this.survivorToSpaces[i] = new Space("Survivor-" + (i + 1) + " To", false, (i + 1));
            this.survivorGreyObjectsWalkers[i] = GreyObjectsWalker.factory();
        }
    }

    /** Return all allocated virtual memory chunks to HeapChunkProvider. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final void tearDown() {
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
        ThreadLocalAllocation.disableThreadLocalAllocation();
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
        log.string("[Young generation: ").indent(true);

        log.string("[Eden: ").indent(true);
        getEden().report(log, traceHeapChunks);
        log.redent(false).string("]").newline();
        log.string("[Survivors: ").indent(true);
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            this.survivorFromSpaces[i].report(log, traceHeapChunks).newline();
            this.survivorToSpaces[i].report(log, traceHeapChunks);
            if (i < maxSurvivorSpaces - 1) {
                log.newline();
            }
        }
        log.redent(false).string("]").redent(false).string("]");
        return log;
    }

    /**
     * Space access method.
     *
     * This method is final because it is called (transitively) from the allocation snippets.
     */
    public final Space getEden() {
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

    /** Check if that space is the young space. */
    boolean isYoungSpace(Space thatSpace) {
        return thatSpace.isYoungSpace();
    }

    @Override
    protected Object promoteObject(Object original) {
        final Log trace = Log.noopLog().string("[YoungGeneration.promoteObject:").string("  original: ").object(original).newline();
        /* Choose between object copying and chunk motion. */
        Object result = null;
        if (ObjectHeaderImpl.getObjectHeaderImpl().isAlignedObject(original)) {
            trace.string("  aligned header: ").hex(ObjectHeaderImpl.readHeaderFromObject(original)).newline();
            /* Promote by Object copying to the next age space. */
            AlignedHeapChunk.AlignedHeader originalChunk = AlignedHeapChunk.getEnclosingAlignedHeapChunk(original);
            final Space originalSpace = originalChunk.getSpace();
            if (originalSpace.getAge() < maxSurvivorSpaces) {
                result = promoteAlignedObject(original, originalSpace);
            } else {
                result = HeapImpl.getHeapImpl().getOldGeneration().promoteAlignedObject(original);
            }
        } else {
            trace.string("  unaligned header: ").hex(ObjectHeaderImpl.readHeaderFromObject(original)).newline();
            UnalignedHeapChunk.UnalignedHeader originalUnalignedChunk = UnalignedHeapChunk.getEnclosingUnalignedHeapChunk(original);
            final Space originalSpace = originalUnalignedChunk.getSpace();
            if (originalSpace.getAge() < maxSurvivorSpaces) {
                result = promoteUnalignedObject(original, originalSpace);
            } else {
                result = HeapImpl.getHeapImpl().getOldGeneration().promoteUnalignedObjectChunk(original);
            }
        }
        trace.string("  OldGeneration.promoteObject returns: ").object(result).string("]").newline();
        return result;
    }

    private void releaseSurvivorSpaces(boolean isFromSpace) {
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            if (isFromSpace) {
                getSurvivorFromSpaceAt(i).release();
            } else {
                getSurvivorToSpaceAt(i).release();
            }
        }
    }

    void releaseSpaces() {
        getEden().release();

        releaseSurvivorSpaces(true);
        if (HeapImpl.getHeapImpl().getGCImpl().isCompleteCollection()) {
            releaseSurvivorSpaces(false);
        }
    }

    void swapSpaces() {
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            assert getSurvivorFromSpaceAt(i).isEmpty() : "Survivor fromSpace should be empty.";
            getSurvivorFromSpaceAt(i).absorb(getSurvivorToSpaceAt(i));
        }
    }

    @Override
    protected boolean isValidSpace(Space thatSpace) {
        return isYoungSpace(thatSpace);
    }

    @Override
    protected boolean verify(final HeapVerifierImpl.Occasion occasion) {
        boolean result = true;
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final HeapVerifierImpl heapVerifier = heap.getHeapVerifierImpl();
        final SpaceVerifierImpl spaceVerifier = heapVerifier.getSpaceVerifierImpl();
        // Verify eden space
        spaceVerifier.initialize(getEden());
        if (occasion.equals(HeapVerifier.Occasion.AFTER_COLLECTION)) {
            // After a collection the eden space should be empty.
            if (spaceVerifier.containsChunks()) {
                result = false;
                heapVerifier.getWitnessLog().string("[YoungGeneration.verify:").string("  eden space contains chunks after collection").string("]").newline();
            }
        } else {
            // Otherwise, verify the space.
            if (!spaceVerifier.verify()) {
                result = false;
                heapVerifier.getWitnessLog().string("[YoungGeneration.verify:").string("  eden space fails to verify").string("]").newline();
            }
        }
        // Verify survivor spaces
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            // Verify survivor from space,
            spaceVerifier.initialize(survivorFromSpaces[i]);
            if (!spaceVerifier.verify()) {
                result = false;
                heapVerifier.getWitnessLog().string("[YoungGeneration.verify:").string("  survivor to space fails to verify").string("]").newline();
            }
            /*
             * The survivor to space, which should be empty except during a collection
             */
            spaceVerifier.initialize(survivorToSpaces[i]);
            if (!spaceVerifier.verify()) {
                result = false;
                heapVerifier.getWitnessLog().string("[YoungGeneration.verify:").string("  survivor to space fails to verify").string("]").newline();
            }
            if (!occasion.equals(HeapVerifier.Occasion.DURING_COLLECTION)) {
                if (spaceVerifier.containsChunks()) {
                    result = false;
                    heapVerifier.getWitnessLog().string("[YoungGeneration.verify:").string("  survivor to space contains chunks").string("]").newline();
                }
            }
        }

        return result;
    }

    boolean slowlyFindPointer(Pointer p) {
        if (HeapVerifierImpl.slowlyFindPointerInSpace(getEden(), p, HeapVerifierImpl.ChunkLimit.top)) {
            return true;
        }

        for (int i = 0; i < maxSurvivorSpaces; i++) {
            if (HeapVerifierImpl.slowlyFindPointerInSpace(getSurvivorFromSpaceAt(i), p, HeapVerifierImpl.ChunkLimit.top)) {
                return true;
            }
            if (HeapVerifierImpl.slowlyFindPointerInSpace(getSurvivorToSpaceAt(i), p, HeapVerifierImpl.ChunkLimit.top)) {
                return true;
            }
        }

        return false;
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
        /* Prepare the Space walkers. */
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            assert getSurvivorToSpaceAt(i).isEmpty() : "SurvivorToSpace should be empty.";
            getSurvivorGreyObjectsWalker(i).setScanStart(getSurvivorToSpaceAt(i));
        }
    }

    boolean scanGreyObjects() {
        final Log trace = Log.noopLog().string("[YoungGeneration.scanGreyObjects:");
        final GCImpl gc = HeapImpl.getHeapImpl().getGCImpl();
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
            getSurvivorGreyObjectsWalker(i).walkGreyObjects(gc.getGreyToBlackObjectVisitor());
        }
        trace.string("]").newline();

        return true;
    }

    UnsignedWord getSurvivorChunkUsedBytes() {
        UnsignedWord usedBytes = WordFactory.zero();
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            usedBytes = usedBytes.add(this.survivorFromSpaces[i].getChunkBytes());
            usedBytes = usedBytes.add(this.survivorToSpaces[i].getChunkBytes());
        }
        return usedBytes;
    }

    UnsignedWord getEdenChunkUsedBytes() {
        return getEden().getChunkBytes();
    }

    UnsignedWord getChunkUsedBytes() {
        return getEdenChunkUsedBytes().add(getSurvivorChunkUsedBytes());
    }

    UnsignedWord getSurvivorObjectBytes() {
        UnsignedWord usedObjectBytes = WordFactory.zero();
        for (int i = 0; i < maxSurvivorSpaces; i++) {
            usedObjectBytes = usedObjectBytes.add(this.survivorFromSpaces[i].getObjectBytes());
            usedObjectBytes = usedObjectBytes.add(this.survivorToSpaces[i].getObjectBytes());
        }
        return usedObjectBytes;
    }

    UnsignedWord getEdenObjectBytes() {
        return getEden().getObjectBytes();
    }

    UnsignedWord getObjectBytes() {
        return getEdenObjectBytes().add(getSurvivorObjectBytes());
    }

    boolean contains(Object object) {
        final ObjectHeaderImpl ohi = ObjectHeaderImpl.getObjectHeaderImpl();
        if (ohi.isAlignedObject(object)) {
            return isYoungSpace(AlignedHeapChunk.getEnclosingAlignedHeapChunk(object).getSpace());
        } else if (ohi.isUnalignedObject(object)) {
            return isYoungSpace(UnalignedHeapChunk.getEnclosingUnalignedHeapChunk(object).getSpace());
        }
        return false;
    }

    private static boolean shouldPromoteFrom(Space originalSpace) {
        return originalSpace.isFrom();
    }

    private Object promoteAlignedObject(Object original, Space originalSpace) {
        assert originalSpace.isEdenSpace() || originalSpace.isSurvivorSpace() : "Should be Eden or survivor.";
        if (!shouldPromoteFrom(originalSpace)) {
            return original;
        }
        int age = originalSpace.getNextAgeForPromotion();
        Space toSpace = getSurvivorToSpaceAt(age - 1);
        return toSpace.promoteAlignedObject(original);
    }

    private Object promoteUnalignedObject(Object original, Space originalSpace) {
        final Log trace = Log.noopLog().string("[YoungGeneration.promoteUnalignedObjectChunk:").string("  original: ").object(original);
        assert ObjectHeaderImpl.getObjectHeaderImpl().isUnalignedObject(original);
        trace.string("  originalSpace: ").string(originalSpace.getName());
        if (shouldPromoteFrom(originalSpace)) {
            trace.string("  promoting");
            final UnalignedHeapChunk.UnalignedHeader uChunk = UnalignedHeapChunk.getEnclosingUnalignedHeapChunk(original);
            int age = originalSpace.getNextAgeForPromotion();
            Space toSpace = getSurvivorToSpaceAt(age - 1);
            if (HeapOptions.TraceObjectPromotion.getValue()) {
                final Log promotionTrace = Log.log().string("[YoungGeneration.promoteUnalignedObjectChunk:").string("  original: ").object(original);
                final UnsignedWord size = LayoutEncoding.getSizeFromObject(original);
                promotionTrace.string("  size: ").unsigned(size).string("]").newline();
            }
            toSpace.promoteUnalignedHeapChunk(uChunk);
        } else {
            trace.string("  not promoting");
        }
        trace.string("  returns: ").object(original);
        trace.string("]").newline();
        return original;
    }

}
