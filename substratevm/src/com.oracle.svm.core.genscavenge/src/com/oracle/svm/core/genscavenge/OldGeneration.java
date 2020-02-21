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
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.log.Log;

/**
 * An OldGeneration has two Spaces, {@link #fromSpace} for existing objects, and {@link #toSpace}
 * for newly-allocated or promoted objects.
 */
public class OldGeneration extends Generation {

    /*
     * State.
     */

    /* This Spaces are final, though their contents change during semi-space flips. */
    private final Space fromSpace;
    private final Space toSpace;

    /** Walkers of Spaces where there might be grey objects. */
    private final GreyObjectsWalker toGreyObjectsWalker;

    /** Constructor. */
    @Platforms(Platform.HOSTED_ONLY.class)
    OldGeneration(String name) {
        super(name);
        int age = HeapPolicy.getMaxSurvivorSpaces() + 1;
        this.fromSpace = new Space("oldFromSpace", true, age);
        this.toSpace = new Space("oldToSpace", false, age);
        this.toGreyObjectsWalker = GreyObjectsWalker.factory();
    }

    /** Return all allocated virtual memory chunks to HeapChunkProvider. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final void tearDown() {
        fromSpace.tearDown();
        toSpace.tearDown();
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
        return true;
    }

    /**
     * Promote an Object to ToSpace if it is not already in ToSpace.
     */
    @AlwaysInline("GC performance")
    @Override
    public Object promoteObject(Object original, UnsignedWord header) {
        if (ObjectHeaderImpl.isAlignedHeader(original, header)) {
            AlignedHeapChunk.AlignedHeader chunk = AlignedHeapChunk.getEnclosingAlignedHeapChunk(original);
            Space originalSpace = chunk.getSpace();
            if (originalSpace.isFrom()) {
                return promoteAlignedObject(original, originalSpace);
            }
        } else {
            assert ObjectHeaderImpl.isUnalignedHeader(original, header);
            UnalignedHeapChunk.UnalignedHeader chunk = UnalignedHeapChunk.getEnclosingUnalignedHeapChunk(original);
            Space originalSpace = chunk.getSpace();
            if (originalSpace.isFrom()) {
                promoteUnalignedChunk(chunk, originalSpace);
            }
        }
        return original;
    }

    @AlwaysInline("GC performance")
    public Object promoteAlignedObject(Object original, Space originalSpace) {
        return getToSpace().promoteAlignedObject(original, originalSpace);
    }

    @AlwaysInline("GC performance")
    public void promoteUnalignedChunk(UnalignedHeapChunk.UnalignedHeader chunk, Space originalSpace) {
        getToSpace().promoteUnalignedHeapChunk(chunk, originalSpace);
    }

    public void promoteObjectChunk(Object obj) {
        getToSpace().promoteObjectChunk(obj);
    }

    void releaseSpaces() {
        /* Release any spaces associated with this generation after a collection. */
        getFromSpace().release();
        /* Just clean remember set in complete collection */
        if (HeapImpl.getHeapImpl().getGCImpl().isCompleteCollection()) {
            /* Clean the spaces that have been scanned for grey objects. */
            getToSpace().cleanRememberedSet();
        }
    }

    protected void walkDirtyObjects(ObjectVisitor visitor, boolean clean) {
        getToSpace().walkDirtyObjects(visitor, clean);
    }

    protected void prepareForPromotion() {
        /* Prepare the Space walkers. */
        getToGreyObjectsWalker().setScanStart(getToSpace());
    }

    protected boolean scanGreyObjects() {
        if (!getToGreyObjectsWalker().haveGreyObjects()) {
            return false;
        }

        getToGreyObjectsWalker().walkGreyObjects();
        return true;
    }

    @Override
    public Log report(Log log, boolean traceHeapChunks) {
        log.string("[Old generation: ").indent(true);
        getFromSpace().report(log, traceHeapChunks).newline();
        getToSpace().report(log, traceHeapChunks).newline();
        log.redent(false).string("]");
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
        return result;
    }

    protected void verifyDirtyCards(boolean isTo) {
        if (isTo) {
            getToSpace().verifyDirtyCards();
        } else {
            getFromSpace().verifyDirtyCards();
        }
    }

    boolean slowlyFindPointer(Pointer p) {
        /*
         * FromSpace is "in" the Heap, ToSpace is not "in" the Heap, because it should be empty.
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
        return false;
    }

    boolean slowlyFindPointerInFromSpace(Pointer p) {
        return HeapVerifierImpl.slowlyFindPointerInSpace(getFromSpace(), p, HeapVerifierImpl.ChunkLimit.top);
    }

    boolean slowlyFindPointerInToSpace(Pointer p) {
        return HeapVerifierImpl.slowlyFindPointerInSpace(getToSpace(), p, HeapVerifierImpl.ChunkLimit.top);
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

    void swapSpaces() {
        assert getFromSpace().isEmpty() : "fromSpace should be empty.";
        getFromSpace().absorb(getToSpace());
    }

    /* Extract all the HeapChunks from FromSpace and append them to ToSpace. */
    void emptyFromSpaceIntoToSpace() {
        getToSpace().absorb(getFromSpace());
    }

    private GreyObjectsWalker getToGreyObjectsWalker() {
        return toGreyObjectsWalker;
    }

    boolean walkHeapChunks(MemoryWalker.Visitor visitor) {
        /* In no particular order visit all the spaces. */
        return getFromSpace().walkHeapChunks(visitor) && getToSpace().walkHeapChunks(visitor);
    }
}
