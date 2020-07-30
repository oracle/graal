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
final class OldGeneration extends Generation {
    /* This Spaces are final and are flipped by transferring chunks from one to the other. */
    private final Space fromSpace;
    private final Space toSpace;

    private final GreyObjectsWalker toGreyObjectsWalker = new GreyObjectsWalker();

    @Platforms(Platform.HOSTED_ONLY.class)
    OldGeneration(String name) {
        super(name);
        int age = HeapPolicy.getMaxSurvivorSpaces() + 1;
        this.fromSpace = new Space("oldFromSpace", true, age);
        this.toSpace = new Space("oldToSpace", false, age);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    void tearDown() {
        fromSpace.tearDown();
        toSpace.tearDown();
    }

    @Override
    public boolean walkObjects(ObjectVisitor visitor) {
        return getFromSpace().walkObjects(visitor) && getToSpace().walkObjects(visitor);
    }

    /** Promote an Object to ToSpace if it is not already in ToSpace. */
    @AlwaysInline("GC performance")
    @Override
    public Object promoteObject(Object original, UnsignedWord header) {
        if (ObjectHeaderImpl.isAlignedHeader(original, header)) {
            AlignedHeapChunk.AlignedHeader chunk = AlignedHeapChunk.getEnclosingChunk(original);
            Space originalSpace = HeapChunk.getSpace(chunk);
            if (originalSpace.isFromSpace()) {
                return promoteAlignedObject(original, originalSpace);
            }
        } else {
            assert ObjectHeaderImpl.isUnalignedHeader(original, header);
            UnalignedHeapChunk.UnalignedHeader chunk = UnalignedHeapChunk.getEnclosingChunk(original);
            Space originalSpace = HeapChunk.getSpace(chunk);
            if (originalSpace.isFromSpace()) {
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
        getFromSpace().releaseChunks();
        if (HeapImpl.getHeapImpl().getGCImpl().isCompleteCollection()) {
            getToSpace().cleanRememberedSet();
        }
    }

    void walkDirtyObjects(ObjectVisitor visitor, boolean clean) {
        getToSpace().walkDirtyObjects(visitor, clean);
    }

    void prepareForPromotion() {
        toGreyObjectsWalker.setScanStart(getToSpace());
    }

    boolean scanGreyObjects() {
        if (!toGreyObjectsWalker.haveGreyObjects()) {
            return false;
        }
        toGreyObjectsWalker.walkGreyObjects();
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
        HeapImpl heap = HeapImpl.getHeapImpl();
        HeapVerifier heapVerifier = heap.getHeapVerifier();
        SpaceVerifier spaceVerifier = heapVerifier.getSpaceVerifier();
        // The from space should be clean after a collection...
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
        // ... and the to space should be empty, except during a collection.
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

    void verifyDirtyCards(boolean inToSpace) {
        if (inToSpace) {
            getToSpace().verifyDirtyCards();
        } else {
            getFromSpace().verifyDirtyCards();
        }
    }

    boolean slowlyFindPointer(Pointer p) {
        // FromSpace is "in" the Heap, ToSpace is not "in" the Heap, because it should be empty.
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
        return HeapVerifier.slowlyFindPointerInSpace(getFromSpace(), p);
    }

    boolean slowlyFindPointerInToSpace(Pointer p) {
        return HeapVerifier.slowlyFindPointerInSpace(getToSpace(), p);
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

    Space getFromSpace() {
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

    boolean walkHeapChunks(MemoryWalker.Visitor visitor) {
        return getFromSpace().walkHeapChunks(visitor) && getToSpace().walkHeapChunks(visitor);
    }
}
