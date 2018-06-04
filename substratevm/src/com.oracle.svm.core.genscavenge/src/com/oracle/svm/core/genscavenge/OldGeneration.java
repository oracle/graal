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

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;

/**
 * An OldGeneration has three Spaces,
 * <ul>
 * <li>fromSpace for existing objects,</li>
 * <li>toSpace for newly-allocated or promoted objects, and</li>
 * <li>pinnedSpace for pinned objects.</li>
 * </ul>
 * An OldGeneration also keeps a list of PinnedAllocators.
 */
public class OldGeneration extends Generation {

    /*
     * State.
     */

    /* This Spaces are final, though their contents change during semi-space flips. */
    private final Space fromSpace;
    private final Space toSpace;
    private final Space pinnedFromSpace;
    private final Space pinnedToSpace;

    /** Walkers of Spaces where there might be grey objects. */
    private final GreyObjectsWalker toGreyObjectsWalker;
    private final GreyObjectsWalker pinnedToGreyObjectsWalker;

    /** Constructor. */
    @Platforms(Platform.HOSTED_ONLY.class)
    OldGeneration(String name) {
        super(name);
        this.fromSpace = new Space("fromSpace", false);
        this.toSpace = new Space("toSpace", false);
        this.pinnedFromSpace = new Space("pinnedFromSpace", false);
        this.pinnedToSpace = new Space("pinnedToSpace", false);
        this.toGreyObjectsWalker = GreyObjectsWalker.factory();
        this.pinnedToGreyObjectsWalker = GreyObjectsWalker.factory();
    }

    /*
     * Ordinary object methods.
     */

    @Override
    public boolean walkObjects(ObjectVisitor visitor) {
        /* FromSpace probably has lots of objects. */
        if (!getFromSpace().walkObjects(visitor)) {
            return false;
        }
        /* ToSpace probably is empty. */
        if (!getToSpace().walkObjects(visitor)) {
            return false;
        }
        /* There might be objects in the pinned space. */
        if (!getPinnedFromSpace().walkObjects(visitor)) {
            return false;
        }
        return true;
    }

    /** Promote an Object to ToSpace if it is not already in ToSpace or PinnedToSpace. */
    @Override
    public Object promoteObject(Object original) {
        final Log trace = Log.noopLog().string("[OldGeneration.promoteObject:").string("  original: ").object(original).newline();
        Object result;
        /* Choose between object copying and chunk motion. */
        if (ObjectHeaderImpl.getObjectHeaderImpl().isAlignedObject(original)) {
            trace.string("  aligned header: ").hex(ObjectHeader.readHeaderFromObject(original)).newline();
            /* Promote by Object copying to the old generation. */
            result = promoteAlignedObject(original);
        } else {
            trace.string("  unaligned header: ").hex(ObjectHeader.readHeaderFromObject(original)).newline();
            /* Promote by HeapChunk motion to the old generation. */
            result = promoteUnalignedObjectChunk(original);
        }
        trace.string("  OldGeneration.promoteObject returns: ").object(result).string("]").newline();
        return result;
    }

    void releaseSpaces() {
        /* Release any spaces associated with this generation after a collection. */
        getFromSpace().release();
        assert getPinnedFromSpace().isEmpty() : "pinnedFromSpace should be empty.";
        /* Clean the spaces that have been scanned for grey objects. */
        getToSpace().cleanRememberedSet();
        getPinnedToSpace().cleanRememberedSet();
    }

    private boolean shouldPromoteFrom(Space originalSpace) {
        final Log trace = Log.noopLog();
        trace.string("[OldGeneration.shouldPromoteFrom:").string("  originalSpace: ").string(originalSpace.getName());
        final boolean result;
        final HeapImpl heap = HeapImpl.getHeapImpl();
        if (heap.isYoungGeneration(originalSpace)) {
            /* The most likely case: the original Space is young space. */
            result = true;
        } else if (originalSpace == getFromSpace()) {
            /* The next most likely case: the original Space is from space. */
            result = true;
        } else {
            /* Otherwise, do not promote to old toSpace. */
            result = false;
        }
        trace.string("  returns: ").bool(result);
        trace.string("]").newline();
        return result;
    }

    private Object promoteAlignedObject(Object original) {
        final Log trace = Log.noopLog().string("[OldGeneration.promoteAlignedObject:").string("  original: ").object(original);
        assert ObjectHeaderImpl.getObjectHeaderImpl().isAlignedObject(original);
        final AlignedHeapChunk.AlignedHeader originalChunk = AlignedHeapChunk.getEnclosingAlignedHeapChunk(original);
        final Space originalSpace = originalChunk.getSpace();
        trace.string("  originalSpace: ").string(originalSpace.getName());
        Object result = original;
        if (shouldPromoteFrom(originalSpace)) {
            trace.string("  promoting");
            if (HeapOptions.TraceObjectPromotion.getValue()) {
                final Log promotionTrace = Log.log().string("[OldGeneration.promoteAlignedObject:").string("  original: ").object(original);
                final UnsignedWord size = LayoutEncoding.getSizeFromObject(original);
                promotionTrace.string("  size: ").unsigned(size).string("]").newline();
            }
            result = getToSpace().promoteAlignedObject(original);
        } else {
            trace.string("  not promoting");
        }
        trace.string("  returns: ").object(result);
        if (trace.isEnabled()) {
            final AlignedHeapChunk.AlignedHeader resultChunk = AlignedHeapChunk.getEnclosingAlignedHeapChunk(result);
            final Space resultSpace = resultChunk.getSpace();
            trace.string("  resultSpace: ").string(resultSpace.getName());
        }
        trace.string("]").newline();
        return result;
    }

    private Object promoteUnalignedObjectChunk(Object original) {
        final Log trace = Log.noopLog().string("[OldGeneration.promoteUnalignedObjectChunk:").string("  original: ").object(original);
        assert ObjectHeaderImpl.getObjectHeaderImpl().isUnalignedObject(original);
        final UnalignedHeapChunk.UnalignedHeader uChunk = UnalignedHeapChunk.getEnclosingUnalignedHeapChunk(original);
        final Space originalSpace = uChunk.getSpace();
        trace.string("  originalSpace: ").string(originalSpace.getName());
        if (shouldPromoteFrom(originalSpace)) {
            trace.string("  promoting");
            /*
             * Since the object does not move when an UnalignedChunk is promoted, there is no need
             * to return a possible copy.
             */
            if (HeapOptions.TraceObjectPromotion.getValue()) {
                final Log promotionTrace = Log.log().string("[OldGeneration.promoteUnalignedObjectChunk:").string("  original: ").object(original);
                final UnsignedWord size = LayoutEncoding.getSizeFromObject(original);
                promotionTrace.string("  size: ").unsigned(size).string("]").newline();
            }
            getToSpace().promoteUnalignedHeapChunk(uChunk);
        } else {
            trace.string("  not promoting");
        }
        trace.string("  returns: ").object(original);
        if (trace.isEnabled()) {
            final UnalignedHeapChunk.UnalignedHeader resultChunk = UnalignedHeapChunk.getEnclosingUnalignedHeapChunk(original);
            final Space resultSpace = resultChunk.getSpace();
            trace.string("  resultSpace: ").string(resultSpace.getName());
        }
        trace.string("]").newline();
        return original;
    }

    protected void walkDirtyObjects(ObjectVisitor visitor, boolean clean) {
        getToSpace().walkDirtyObjects(visitor, clean);
        getPinnedToSpace().walkDirtyObjects(visitor, clean);
    }

    protected void prepareForPromotion() {
        /* Prepare the Space walkers. */
        getToGreyObjectsWalker().setScanStart(getToSpace());
        getPinnedToGreyObjectsWalker().setScanStart(getPinnedToSpace());
    }

    protected void scanGreyObjects() {
        final Log trace = Log.noopLog().string("[OldGeneration.scanGreyObjects:");
        /*
         * The PinnedToSpace will have grey objects after the PinnedFromSpace is promoted, but no
         * other Objects will be promoted into it later, so I walk it before I walk ToSpace so it
         * isn't part of the transitive closure when I walk ToSpace.
         *
         * TODO: Does this argue for a "blackenPinnedObjects()", like "blackenBootImageObjects()"?
         */
        final GCImpl gc = HeapImpl.getHeapImpl().getGCImpl();
        getPinnedToGreyObjectsWalker().walkGreyObjects(gc.getGreyToBlackObjectVisitor());
        getToGreyObjectsWalker().walkGreyObjects(gc.getGreyToBlackObjectVisitor());
        trace.string("]").newline();
    }

    /*
     * Pinned allocator collection methods.
     */

    protected void promotePinnedAllocatorChunks(boolean completeCollection) {
        final Log trace = Log.noopLog();
        trace.string("[OldGeneration.promotePinnedAllocatorChunks:");
        trace.string("  completeCollection: ").bool(completeCollection);
        /*
         * First, walk the list of PinnedAllocators distributing their chunks marking all the chunks
         * that should be pinned.
         */
        PinnedAllocatorImpl.markPinnedChunks();
        /*
         * Then, walk pinned from space and distribute chunks to pinned toSpace or unpinned
         * fromSpace as appropriate.
         */
        distributePinnedChunks(completeCollection);
        trace.string("]").newline();
    }

    private void distributePinnedChunks(boolean completeCollection) {
        final Log trace = Log.noopLog().string("[OldGeneration.distributePinnedChunks:").string("  completeCollection: ").bool(completeCollection);
        final Space unpinnedSpace = (completeCollection ? getFromSpace() : getToSpace());
        trace.string("  unpinnedSpace: ").string(unpinnedSpace.getName());
        distributePinnedAlignedChunks(getPinnedToSpace(), unpinnedSpace);
        distributePinnedUnalignedChunks(getPinnedToSpace(), unpinnedSpace);
    }

    private void distributePinnedAlignedChunks(Space pinnedSpace, Space unpinnedSpace) {
        final Log trace = Log.noopLog().string("[OldGeneration.distributePinnedAlignedChunks:");
        AlignedHeapChunk.AlignedHeader aChunk = getPinnedFromSpace().getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            trace.newline();
            /* Set up for the next iteration. */
            final AlignedHeapChunk.AlignedHeader next = aChunk.getNext();
            /* Move the chunk to pinned toSpace or the unpinned space. */
            getPinnedFromSpace().extractAlignedHeapChunk(aChunk);
            if (aChunk.getPinned()) {
                /* Clean up for the next collection. */
                aChunk.setPinned(false);
                trace.string("  to pinned space");
                pinnedSpace.appendAlignedHeapChunk(aChunk);
            } else {
                trace.string("  to unpinned space");
                unpinnedSpace.appendAlignedHeapChunk(aChunk);
            }
            /* Advance to the next chunk. */
            aChunk = next;
        }
        trace.string("]").newline();
    }

    private void distributePinnedUnalignedChunks(Space pinnedSpace, Space unpinnedSpace) {
        final Log trace = Log.noopLog();
        trace.string("[OldGeneration.distributePinnedUnalignedChunks:");
        UnalignedHeapChunk.UnalignedHeader uChunk = getPinnedFromSpace().getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            trace.newline();
            /* Set up for the next iteration. */
            final UnalignedHeapChunk.UnalignedHeader next = uChunk.getNext();
            /* Move the chunk to pinned toSpace or the unpinned space. */
            getPinnedFromSpace().extractUnalignedHeapChunk(uChunk);
            if (uChunk.getPinned()) {
                /* Clean up for the next collection. */
                uChunk.setPinned(false);
                trace.string("  to pinned space");
                pinnedSpace.appendUnalignedHeapChunk(uChunk);
            } else {
                trace.string("  to unpinned space");
                unpinnedSpace.appendUnalignedHeapChunk(uChunk);
            }
            /* Advance to the next chunk. */
            uChunk = next;
        }
        trace.string("]").newline();
    }

    @Override
    public Log report(Log log, boolean traceHeapChunks) {
        log.string("[Old generation: ");
        log.newline();
        log.string("  FromSpace: ");
        getFromSpace().report(log, traceHeapChunks);
        log.newline();
        log.string("  ToSpace: ");
        getToSpace().report(log, traceHeapChunks);
        log.newline();
        log.string("  PinnedFromSpace: ");
        getPinnedFromSpace().report(log, traceHeapChunks);
        log.newline();
        log.string("  PinnedToSpace: ");
        getPinnedToSpace().report(log, traceHeapChunks);
        log.string("]");
        return log;
    }

    @Override
    protected boolean verify(HeapVerifier.Occasion occasion) {
        boolean result = true;
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final HeapVerifierImpl heapVerifier = heap.getHeapVerifierImpl();
        final SpaceVerifierImpl spaceVerifier = heapVerifier.getSpaceVerifierImpl();
        /*
         * - The old generation consists of a from space, which should be clean after a collection
         * ...
         */
        spaceVerifier.initialize(heap.getOldGeneration().getFromSpace());
        if (!spaceVerifier.verify()) {
            result = false;
            heapVerifier.getWitnessLog().string("[OldGeneration.verify:").string("  old from space fails to verify").string("]").newline();
        }
        if (occasion.equals(HeapVerifier.Occasion.AFTER_COLLECTION)) {
            if (!spaceVerifier.verifyOnlyCleanCards()) {
                result = false;
                heapVerifier.getWitnessLog().string("[OldGeneration.verify:").string("  old from space contains dirty cards").string("]").newline();
            }
        }
        /*
         * ... and a to space, which should be empty except during a collection ...
         */
        spaceVerifier.initialize(heap.getOldGeneration().getToSpace());
        if (!spaceVerifier.verify()) {
            result = false;
            heapVerifier.getWitnessLog().string("[OldGeneration.verify:").string("  old to space fails to verify").string("]").newline();
        }
        if (!occasion.equals(HeapVerifier.Occasion.DURING_COLLECTION)) {
            if (spaceVerifier.containsChunks()) {
                result = false;
                heapVerifier.getWitnessLog().string("[OldGeneration.verify:").string("  old to space contains chunks").string("]").newline();
            }
        }
        /*
         * ... and a pinned from space, which should be clean after a collection ...
         */
        spaceVerifier.initialize(heap.getOldGeneration().getPinnedFromSpace());
        if (!spaceVerifier.verify()) {
            result = false;
            heapVerifier.getWitnessLog().string("[OldGeneration.verify:").string("  old pinned from space fails to verify").string("]").newline();
        }
        if (occasion.equals(HeapVerifier.Occasion.AFTER_COLLECTION)) {
            if (!spaceVerifier.verifyOnlyCleanCards()) {
                result = false;
                heapVerifier.getWitnessLog().string("[OldGeneration.verify:").string("  old pinned from space contains dirty cards").string("]").newline();
            }
        }
        /*
         * ... and a pinned to space, which should be empty except during a collection ...
         */
        spaceVerifier.initialize(heap.getOldGeneration().getPinnedToSpace());
        if (!spaceVerifier.verify()) {
            result = false;
            heapVerifier.getWitnessLog().string("[OldGeneration.verify:").string("  old pinned to space fails to verify").string("]").newline();
        }
        if (!occasion.equals(HeapVerifier.Occasion.DURING_COLLECTION)) {
            if (spaceVerifier.containsChunks()) {
                result = false;
                heapVerifier.getWitnessLog().string("[OldGeneration.verify:").string("  old to space contains chunks").string("]").newline();
            }
        }
        return result;
    }

    boolean slowlyFindPointer(Pointer p) {
        /*
         * FromSpace and PinnedFromSpace are "in" the Heap, ToSpace and PinnedToSpace are not "in"
         * the Heap, because they should be empty.
         */
        if (slowlyFindPointerInFromSpace(p)) {
            return true;
        }
        if (slowlyFindPointerInToSpace(p)) {
            try (Log paranoia = Log.noopLog()) {
                if (paranoia.isEnabled()) {
                    paranoia.string("[OldGeneration.slowlyFindPointerInOldGeneration:");
                    paranoia.string("  p: ").hex(p);
                    paranoia.string("  found in: ").string(getToSpace().getName());
                    paranoia.string("]").newline();
                }
            }
            return false;
        }
        if (slowlyFindPointerInPinnedFromSpace(p)) {
            return true;
        }
        if (slowlyFindPointerInPinnedToSpace(p)) {
            try (Log paranoia = Log.noopLog()) {
                if (paranoia.isEnabled()) {
                    paranoia.string("[OldGeneration.slowlyFindPointerInOldGeneration:");
                    paranoia.string("  p: ").hex(p);
                    paranoia.string("  found in: ").string(getPinnedToSpace().getName());
                    paranoia.string("]").newline();
                }
            }
            return false;
        }
        return false;
    }

    boolean slowlyFindPointerInFromSpace(Pointer p) {
        return HeapVerifierImpl.slowlyFindPointerInSpace(getFromSpace(), p, HeapVerifierImpl.ChunkLimit.top);
    }

    boolean slowlyFindPointerInToSpace(Pointer p) {
        return HeapVerifierImpl.slowlyFindPointerInSpace(getToSpace(), p, HeapVerifierImpl.ChunkLimit.top);
    }

    boolean slowlyFindPointerInPinnedFromSpace(Pointer p) {
        return HeapVerifierImpl.slowlyFindPointerInSpace(getPinnedFromSpace(), p, HeapVerifierImpl.ChunkLimit.top);
    }

    boolean slowlyFindPointerInPinnedToSpace(Pointer p) {
        return HeapVerifierImpl.slowlyFindPointerInSpace(getPinnedToSpace(), p, HeapVerifierImpl.ChunkLimit.top);
    }

    /* This could return an enum, but I want to be able to examine it easily from a debugger. */
    int classifyPointer(Pointer p) {
        if (p.isNull()) {
            return 0;
        }
        if (slowlyFindPointerInFromSpace(p)) {
            return 1;
        }
        if (slowlyFindPointerInToSpace(p)) {
            return 2;
        }
        if (slowlyFindPointerInPinnedFromSpace(p)) {
            return 3;
        }
        if (slowlyFindPointerInPinnedToSpace(p)) {
            return 4;
        }
        return -1;
    }

    /*
     * Space access methods.
     *
     * TODO: Why are some of the access methods public?
     */

    public Space getFromSpace() {
        return fromSpace;
    }

    Space getToSpace() {
        return toSpace;
    }

    Space getPinnedFromSpace() {
        return pinnedFromSpace;
    }

    Space getPinnedToSpace() {
        return pinnedToSpace;
    }

    void swapSpaces() {
        assert getFromSpace().isEmpty() : "fromSpace should be empty.";
        getFromSpace().absorb(getToSpace());
        assert getPinnedFromSpace().isEmpty() : "pinnedFromSpace should be empty.";
        getPinnedFromSpace().absorb(getPinnedToSpace());
    }

    /* Extract all the HeapChunks from FromSpace and append them to ToSpace. */
    void emptyFromSpaceIntoToSpace() {
        getToSpace().absorb(getFromSpace());
    }

    private GreyObjectsWalker getToGreyObjectsWalker() {
        return toGreyObjectsWalker;
    }

    private GreyObjectsWalker getPinnedToGreyObjectsWalker() {
        return pinnedToGreyObjectsWalker;
    }

    boolean walkHeapChunks(MemoryWalker.Visitor visitor) {
        /* In no particular order visit all the spaces. */
        final boolean result = (getFromSpace().walkHeapChunks(visitor) &&
                        getToSpace().walkHeapChunks(visitor) &&
                        getPinnedFromSpace().walkHeapChunks(visitor) &&
                        getPinnedToSpace().walkHeapChunks(visitor));
        return result;
    }
}
