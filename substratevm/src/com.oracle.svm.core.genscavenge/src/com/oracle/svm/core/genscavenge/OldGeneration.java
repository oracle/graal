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
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.hub.LayoutEncoding;
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
        this.fromSpace = new Space("fromSpace", false);
        this.toSpace = new Space("toSpace", false);
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

    /** Promote an Object to ToSpace if it is not already in ToSpace. */
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
        /* Clean the spaces that have been scanned for grey objects. */
        getToSpace().cleanRememberedSet();
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
    }

    protected void prepareForPromotion() {
        /* Prepare the Space walkers. */
        getToGreyObjectsWalker().setScanStart(getToSpace());
    }

    protected void scanGreyObjects() {
        final Log trace = Log.noopLog().string("[OldGeneration.scanGreyObjects:");
        final GCImpl gc = HeapImpl.getHeapImpl().getGCImpl();
        getToGreyObjectsWalker().walkGreyObjects(gc.getGreyToBlackObjectVisitor());
        trace.string("]").newline();
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
    protected boolean isValidSpace(Space space) {
        return space == getFromSpace() || space == getToSpace();
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
