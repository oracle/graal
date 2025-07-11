/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.GCImpl.ChunkReleaser;
import com.oracle.svm.core.genscavenge.metaspace.MetaspaceImpl;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;

import jdk.graal.compiler.word.Word;

/**
 * A Space is a collection of HeapChunks.
 *
 * Each Space keeps two collections: one of {@link AlignedHeapChunk} and one of
 * {@link UnalignedHeapChunk}.
 */
public final class Space {
    private final String name;
    private final String shortName;
    private final boolean isToSpace;
    private final int age;
    private final ChunksAccounting accounting;

    /* Heads and tails of the HeapChunk lists. */
    private AlignedHeapChunk.AlignedHeader firstAlignedHeapChunk;
    private AlignedHeapChunk.AlignedHeader lastAlignedHeapChunk;
    private UnalignedHeapChunk.UnalignedHeader firstUnalignedHeapChunk;
    private UnalignedHeapChunk.UnalignedHeader lastUnalignedHeapChunk;

    /**
     * Space creation is HOSTED_ONLY because all Spaces must be constructed during native image
     * generation so they end up in the native image heap because they need to be accessed during
     * collections so they should not move.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public Space(String name, String shortName, boolean isToSpace, int age) {
        this(name, shortName, isToSpace, age, null);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    Space(String name, String shortName, boolean isToSpace, int age, ChunksAccounting parentAccounting) {
        assert name != null : "Space name should not be null.";
        this.name = name;
        this.shortName = shortName;
        this.isToSpace = isToSpace;
        this.age = age;
        this.accounting = new ChunksAccounting(parentAccounting);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getName() {
        return name;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public String getShortName() {
        return shortName;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isEmpty() {
        return (getFirstAlignedHeapChunk().isNull() && getFirstUnalignedHeapChunk().isNull());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void tearDown() {
        HeapChunkProvider.freeAlignedChunkList(getFirstAlignedHeapChunk());
        HeapChunkProvider.freeUnalignedChunkList(getFirstUnalignedHeapChunk());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isEdenSpace() {
        return age == 0;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isYoungSpace() {
        return age <= HeapParameters.getMaxSurvivorSpaces();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isSurvivorSpace() {
        return age > 0 && age <= HeapParameters.getMaxSurvivorSpaces();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isOldSpace() {
        return age == OldGeneration.getAge();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isMetaspace() {
        return age == MetaspaceImpl.getAge();
    }

    @AlwaysInline("GC performance.")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isCompactingOldSpace() {
        return SerialGCOptions.useCompactingOldGen() && isOldSpace();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    int getAge() {
        return age;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    int getNextAgeForPromotion() {
        assert age < OldGeneration.getAge();
        return age + 1;
    }

    @AlwaysInline("GC performance.")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isFromSpace() {
        return !isToSpace && !isCompactingOldSpace();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isToSpace() {
        return isToSpace;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void walkObjects(ObjectVisitor visitor) {
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            AlignedHeapChunk.walkObjects(aChunk, visitor);
            aChunk = HeapChunk.getNext(aChunk);
        }
        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            UnalignedHeapChunk.walkObjects(uChunk, visitor);
            uChunk = HeapChunk.getNext(uChunk);
        }
    }

    void walkAlignedHeapChunks(AlignedHeapChunk.Visitor visitor) {
        AlignedHeapChunk.AlignedHeader chunk = getFirstAlignedHeapChunk();
        while (chunk.isNonNull()) {
            visitor.visitChunk(chunk);
            chunk = HeapChunk.getNext(chunk);
        }
    }

    public void logUsage(Log log, boolean logIfEmpty) {
        UnsignedWord chunkBytes;
        if (isEdenSpace() && !VMOperation.isGCInProgress()) {
            chunkBytes = HeapImpl.getAccounting().getEdenUsedBytes();
        } else {
            chunkBytes = getChunkBytes();
        }

        if (logIfEmpty || chunkBytes.aboveThan(0)) {
            log.string(getName()).string(": ").rational(chunkBytes, GCImpl.M, 2).string("M (")
                            .rational(accounting.getAlignedChunkBytes(), GCImpl.M, 2).string("M in ").signed(accounting.getAlignedChunkCount()).string(" aligned chunks, ")
                            .rational(accounting.getUnalignedChunkBytes(), GCImpl.M, 2).string("M in ").signed(accounting.getUnalignedChunkCount()).string(" unaligned chunks)").newline();
        }
    }

    public void logChunks(Log log) {
        HeapChunkLogging.logChunks(log, getFirstAlignedHeapChunk(), shortName, isToSpace());
        HeapChunkLogging.logChunks(log, getFirstUnalignedHeapChunk(), shortName, isToSpace());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void releaseChunks(ChunkReleaser chunkReleaser) {
        chunkReleaser.add(firstAlignedHeapChunk);
        chunkReleaser.add(firstUnalignedHeapChunk);

        firstAlignedHeapChunk = Word.nullPointer();
        lastAlignedHeapChunk = Word.nullPointer();
        firstUnalignedHeapChunk = Word.nullPointer();
        lastUnalignedHeapChunk = Word.nullPointer();
        accounting.reset();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    void appendAlignedHeapChunk(AlignedHeapChunk.AlignedHeader aChunk) {
        /*
         * This method is called either during a GC or when detaching threads. So, it cannot
         * guarantee that it is inside a VMOperation, only that there is some mutual exclusion.
         */
        VMThreads.guaranteeOwnsThreadMutex("Trying to append an aligned heap chunk but no mutual exclusion.", true);
        appendAlignedHeapChunkUnsafe(aChunk);
    }

    /**
     * Callers need to ensure that there is some mutual exclusion in place that prevents races
     * between threads. If possible, please use {@link #appendAlignedHeapChunk} instead.
     */
    @Uninterruptible(reason = "GC must see a consistent state.")
    public void appendAlignedHeapChunkUnsafe(AlignedHeapChunk.AlignedHeader aChunk) {
        AlignedHeapChunk.AlignedHeader oldLast = getLastAlignedHeapChunk();
        HeapChunk.setSpace(aChunk, this);
        HeapChunk.setPrevious(aChunk, oldLast);
        HeapChunk.setNext(aChunk, Word.nullPointer());
        if (oldLast.isNonNull()) {
            HeapChunk.setNext(oldLast, aChunk);
        }
        setLastAlignedHeapChunk(aChunk);
        if (getFirstAlignedHeapChunk().isNull()) {
            setFirstAlignedHeapChunk(aChunk);
        }

        accounting.noteAlignedHeapChunk();
    }

    @Uninterruptible(reason = "GC must see a consistent state.")
    void extractAlignedHeapChunk(AlignedHeapChunk.AlignedHeader aChunk) {
        assert VMOperation.isGCInProgress() : "Should only be called by the collector.";

        AlignedHeapChunk.AlignedHeader chunkNext = HeapChunk.getNext(aChunk);
        AlignedHeapChunk.AlignedHeader chunkPrev = HeapChunk.getPrevious(aChunk);
        if (chunkPrev.isNonNull()) {
            HeapChunk.setNext(chunkPrev, chunkNext);
        } else {
            setFirstAlignedHeapChunk(chunkNext);
        }
        if (chunkNext.isNonNull()) {
            HeapChunk.setPrevious(chunkNext, chunkPrev);
        } else {
            setLastAlignedHeapChunk(chunkPrev);
        }
        HeapChunk.setNext(aChunk, Word.nullPointer());
        HeapChunk.setPrevious(aChunk, Word.nullPointer());
        HeapChunk.setSpace(aChunk, null);

        accounting.unnoteAlignedHeapChunk();
    }

    @Uninterruptible(reason = "GC must see a consistent state.")
    void appendUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader uChunk) {
        /*
         * This method is used while detaching a thread, so it cannot guarantee that it is inside a
         * VMOperation, only that there is some mutual exclusion.
         */
        VMThreads.guaranteeOwnsThreadMutex("Trying to append an unaligned chunk but no mutual exclusion.", true);

        UnalignedHeapChunk.UnalignedHeader oldLast = getLastUnalignedHeapChunk();
        HeapChunk.setSpace(uChunk, this);
        HeapChunk.setPrevious(uChunk, oldLast);
        HeapChunk.setNext(uChunk, Word.nullPointer());
        if (oldLast.isNonNull()) {
            HeapChunk.setNext(oldLast, uChunk);
        }
        setLastUnalignedHeapChunk(uChunk);
        if (getFirstUnalignedHeapChunk().isNull()) {
            setFirstUnalignedHeapChunk(uChunk);
        }

        accounting.noteUnalignedHeapChunk(uChunk);
    }

    @Uninterruptible(reason = "GC must see a consistent state.")
    void extractUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader uChunk) {
        assert VMOperation.isGCInProgress() : "Trying to extract an unaligned chunk but not in a VMOperation.";

        UnalignedHeapChunk.UnalignedHeader chunkNext = HeapChunk.getNext(uChunk);
        UnalignedHeapChunk.UnalignedHeader chunkPrev = HeapChunk.getPrevious(uChunk);
        if (chunkPrev.isNonNull()) {
            HeapChunk.setNext(chunkPrev, chunkNext);
        } else {
            setFirstUnalignedHeapChunk(chunkNext);
        }
        if (chunkNext.isNonNull()) {
            HeapChunk.setPrevious(chunkNext, chunkPrev);
        } else {
            setLastUnalignedHeapChunk(chunkPrev);
        }
        /* Reset the fields that the result chunk keeps for Space. */
        HeapChunk.setNext(uChunk, Word.nullPointer());
        HeapChunk.setPrevious(uChunk, Word.nullPointer());
        HeapChunk.setSpace(uChunk, null);

        accounting.unnoteUnalignedHeapChunk(uChunk);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public AlignedHeapChunk.AlignedHeader getFirstAlignedHeapChunk() {
        return firstAlignedHeapChunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void setFirstAlignedHeapChunk(AlignedHeapChunk.AlignedHeader chunk) {
        firstAlignedHeapChunk = chunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    AlignedHeapChunk.AlignedHeader getLastAlignedHeapChunk() {
        return lastAlignedHeapChunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void setLastAlignedHeapChunk(AlignedHeapChunk.AlignedHeader chunk) {
        lastAlignedHeapChunk = chunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnalignedHeapChunk.UnalignedHeader getFirstUnalignedHeapChunk() {
        return firstUnalignedHeapChunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void setFirstUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader chunk) {
        this.firstUnalignedHeapChunk = chunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnalignedHeapChunk.UnalignedHeader getLastUnalignedHeapChunk() {
        return lastUnalignedHeapChunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private void setLastUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader chunk) {
        lastUnalignedHeapChunk = chunk;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void absorb(Space src) {
        /*
         * Absorb the chunks of a source into this Space. I cannot just copy the lists, because each
         * HeapChunk has a reference to the Space it is in, so I have to touch them all.
         */
        AlignedHeapChunk.AlignedHeader aChunk = src.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            AlignedHeapChunk.AlignedHeader next = HeapChunk.getNext(aChunk);
            src.extractAlignedHeapChunk(aChunk);
            appendAlignedHeapChunk(aChunk);
            aChunk = next;
        }
        UnalignedHeapChunk.UnalignedHeader uChunk = src.getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            UnalignedHeapChunk.UnalignedHeader next = HeapChunk.getNext(uChunk);
            src.extractUnalignedHeapChunk(uChunk);
            appendUnalignedHeapChunk(uChunk);
            uChunk = next;
        }
        assert src.isEmpty();
    }

    /**
     * This value is only updated during a GC. Be careful when calling this method during a GC as it
     * might wrongly include chunks that will be freed at the end of the GC.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord getChunkBytes() {
        assert !isEdenSpace() || areEdenBytesCorrect() : "eden bytes are only accurate during a GC, or at a safepoint after a TLAB flush";
        return getAlignedChunkBytes().add(accounting.getUnalignedChunkBytes());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean areEdenBytesCorrect() {
        if (VMOperation.isGCInProgress()) {
            return true;
        } else if (VMOperation.isInProgressAtSafepoint()) {
            /* Verify that there are no threads that have a TLAB. */
            for (IsolateThread thread = VMThreads.firstThread(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
                ThreadLocalAllocation.Descriptor tlab = ThreadLocalAllocation.getTlab(thread);
                if (tlab.getAlignedAllocationStart(SubstrateAllocationSnippets.TLAB_START_IDENTITY).isNonNull() || tlab.getUnalignedChunk().isNonNull()) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord getAlignedChunkBytes() {
        return accounting.getAlignedChunkBytes();
    }

    UnsignedWord computeObjectBytes() {
        assert !isEdenSpace() || areEdenBytesCorrect() : "eden bytes are only accurate during a GC, or at a safepoint after a TLAB flush";
        return computeAlignedObjectBytes().add(computeUnalignedObjectBytes());
    }

    private UnsignedWord computeAlignedObjectBytes() {
        UnsignedWord result = Word.zero();
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            UnsignedWord allocatedBytes = HeapChunk.getTopOffset(aChunk).subtract(AlignedHeapChunk.getObjectsStartOffset());
            result = result.add(allocatedBytes);
            aChunk = HeapChunk.getNext(aChunk);
        }
        return result;
    }

    private UnsignedWord computeUnalignedObjectBytes() {
        UnsignedWord result = Word.zero();
        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            UnsignedWord allocatedBytes = HeapChunk.getTopOffset(uChunk).subtract(UnalignedHeapChunk.getObjectStartOffset(uChunk));
            result = result.add(allocatedBytes);
            uChunk = HeapChunk.getNext(uChunk);
        }
        return result;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean contains(Pointer p) {
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            Pointer start = AlignedHeapChunk.getObjectsStart(aChunk);
            if (start.belowOrEqual(p) && p.belowThan(HeapChunk.getTopPointer(aChunk))) {
                return true;
            }
            aChunk = HeapChunk.getNext(aChunk);
        }

        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            Pointer start = UnalignedHeapChunk.getObjectStart(uChunk);
            if (start.belowOrEqual(p) && p.belowThan(HeapChunk.getTopPointer(uChunk))) {
                return true;
            }
            uChunk = HeapChunk.getNext(uChunk);
        }
        return false;
    }

    public boolean printLocationInfo(Log log, Pointer p) {
        AlignedHeapChunk.AlignedHeader aChunk = getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            if (HeapChunk.asPointer(aChunk).belowOrEqual(p) && p.belowThan(HeapChunk.getEndPointer(aChunk))) {
                boolean unusablePart = p.aboveOrEqual(HeapChunk.getTopPointer(aChunk));
                printChunkInfo(log, aChunk, "aligned", unusablePart);
                return true;
            }
            aChunk = HeapChunk.getNext(aChunk);
        }

        UnalignedHeapChunk.UnalignedHeader uChunk = getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            if (HeapChunk.asPointer(uChunk).belowOrEqual(p) && p.belowThan(HeapChunk.getEndPointer(uChunk))) {
                boolean unusablePart = p.aboveOrEqual(HeapChunk.getTopPointer(uChunk));
                printChunkInfo(log, uChunk, "unaligned", unusablePart);
                return true;
            }
            uChunk = HeapChunk.getNext(uChunk);
        }
        return false;
    }

    private void printChunkInfo(Log log, HeapChunk.Header<?> chunk, String chunkType, boolean unusablePart) {
        String toSpace = isToSpace ? "-T" : "";
        String unusable = unusablePart ? "unusable part of " : "";
        log.string("points into ").string(unusable).string(chunkType).string(" chunk ").zhex(chunk).spaces(1);
        log.string("(").string(getShortName()).string(toSpace).string(")");
    }
}
