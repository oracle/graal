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

import org.graalvm.word.WordFactory;

import com.oracle.svm.core.log.Log;

final class SpaceVerifierImpl implements Space.Verifier {

    /**
     * A factory for a VerifierImpl that will be initialized later.
     *
     * @return A Verifier that can be initialized later.
     */
    public static SpaceVerifierImpl factory() {
        return new SpaceVerifierImpl();
    }

    /**
     * Initialize the state of this VerifierImpl.
     *
     * @param s The Space to be verified.
     */
    @Override
    public SpaceVerifierImpl initialize(final Space s) {
        this.space = s;
        return this;
    }

    @Override
    public boolean verify() {
        final Log trace = HeapImpl.getHeapImpl().getHeapVerifierImpl().getTraceLog();
        trace.string("[SpaceVerifierImpl.verify:").string("  ").string(space.getName()).newline();
        boolean result = true;
        final boolean isTLASpace = ThreadLocalAllocation.isThreadLocalAllocationSpace(space);
        if (isTLASpace) {
            ThreadLocalAllocation.disableThreadLocalAllocation();
        }
        /* Verify the list structure. */
        if (!verifyChunkLists()) {
            HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("verifyChunkLists() returns false").string("]").newline();
            result = false;
        }
        /*
         * Verify the contents of the chunks. If the chunk lists don't verify, there's no point in
         * trying to verify the chunks themselves.
         */
        if (result && !verifyChunks()) {
            HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("[SpaceVerifierImpl.VerifierImpl.verify:").string("  verifyChunks fails").string("]").newline();
            result = false;
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    boolean containsChunks() {
        final AlignedHeapChunk.AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        final UnalignedHeapChunk.UnalignedHeader uChunk = space.getFirstUnalignedHeapChunk();
        return (aChunk.isNonNull() || uChunk.isNonNull());
    }

    /** Verify the chunk list structures, but not the chunks themselves. */
    private boolean verifyChunkLists() {
        final Log trace = HeapImpl.getHeapImpl().getHeapVerifierImpl().getTraceLog();
        trace.string("[SpaceVerifierImpl.VerifierImpl.verifyChunkLists:");
        boolean result = true;
        /* Verify the list structure. */
        result &= verifyAlignedChunkList();
        result &= verifyUnalignedList();
        trace.string("  returns: ").bool(result);
        trace.string("]").newline();
        return result;
    }

    /** Verify the AlignedChunk list, but not the chunks themselves. */
    private boolean verifyAlignedChunkList() {
        final Log trace = HeapImpl.getHeapImpl().getHeapVerifierImpl().getTraceLog().string("[SpaceVerifierImpl.VerifierImpl.verifyAlignedChunkList:");
        trace.string("  Space: ").string(space.getName()).newline();
        boolean result = true;
        /* - Verify the doubly-linked large chunk list. */
        AlignedHeapChunk.AlignedHeader current = space.getFirstAlignedHeapChunk();
        AlignedHeapChunk.AlignedHeader previous = WordFactory.nullPointer();
        /* - - Run down the list with a trailing pointer to the previous chunk. */
        while (current.isNonNull()) {
            /* Check the backwards link. */
            final AlignedHeapChunk.AlignedHeader previousOfCurrent = current.getPrevious();
            result &= previousOfCurrent.equal(previous);
            if (!result) {
                final Log failure = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("[SpaceVerifierImpl.VerifierImpl.verifyAlignedChunkList:");
                failure.string("  space: ").string(space.getName()).string("  doubly-linked list failure").newline();
                failure.string("  current: ").hex(current);
                failure.string("  current.previous: ").hex(previousOfCurrent);
                failure.string("  previous: ").hex(previous);
                failure.string("]").newline();
                break;
            }
            previous = current;
            current = current.getNext();
        }
        /* - - Check that the last fixed pointer is correct. */
        result &= previous.equal(space.getLastAlignedHeapChunk());
        if (!result) {
            final Log failure = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("[SpaceVerifierImpl.VerifierImpl.verifyAlignedChunkList:");
            failure.string("  space: ").string(space.getName()).string("  lastAlignedHeapChunk failure").string("]").newline();
            failure.string("  previous: ").hex(previous);
            failure.string("  lastAlignedHeapChunk: ").hex(space.getLastAlignedHeapChunk());
            failure.string("]").newline();
            result = false;
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    /** Verify the UnalignedChunks list, but not the chunks themselves. */
    private boolean verifyUnalignedList() {
        final Log trace = HeapImpl.getHeapImpl().getHeapVerifierImpl().getTraceLog().string("[SpaceVerifierImpl.verifyUnalignedChunkList:");
        boolean result = true;
        /* - Verify the doubly-linked large chunk list. */
        UnalignedHeapChunk.UnalignedHeader current = space.getFirstUnalignedHeapChunk();
        UnalignedHeapChunk.UnalignedHeader previous = WordFactory.nullPointer();
        /* - - Run down the list with a trailing pointer to the previous chunk. */
        while (current.isNonNull()) {
            /* Check the backwards link. */
            result &= current.getPrevious().equal(previous);
            if (!result) {
                final Log failure = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("[SpaceVerifierImpl.VerifierImpl.verifyUnalignedChunkList:");
                failure.string("  space: ").string(space.getName()).string("  doubly-linked list failure").string("]").newline();
                break;
            }
            previous = current;
            current = current.getNext();
        }
        /* - - Check that the last array pointer is correct. */
        result &= previous.equal(space.getLastUnalignedHeapChunk());
        if (!result) {
            final Log failure = HeapImpl.getHeapImpl().getHeapVerifierImpl().getWitnessLog().string("[SpaceVerifierImpl.VerifierImpl.verifyUnalignedChunkList:");
            failure.string("  space: ").string(space.getName()).string("  lastUnalignedHeapChunk failure").string("]").newline();
            result = false;
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    /** Verify the contents of the chunks. */
    private boolean verifyChunks() {
        final Log trace = HeapImpl.getHeapImpl().getHeapVerifierImpl().getTraceLog().string("[SpaceVerifierImpl.VerifierImpl.verifyChunks:");
        boolean result = true;
        result &= verifyAlignedChunks();
        result &= verifyUnalignedChunks();
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    private boolean verifyAlignedChunks() {
        final Log trace = HeapImpl.getHeapImpl().getHeapVerifierImpl().getTraceLog().string("[SpaceVerifierImpl.VerifierImpl.verifyAlignedChunks:");
        boolean result = true;
        AlignedHeapChunk.AlignedHeader chunk = space.getFirstAlignedHeapChunk();
        while (chunk.isNonNull()) {
            result &= AlignedHeapChunk.verifyAlignedHeapChunk(chunk);
            chunk = chunk.getNext();
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    private boolean verifyUnalignedChunks() {
        final Log trace = HeapImpl.getHeapImpl().getHeapVerifierImpl().getTraceLog().string("[SpaceVerifierImpl.verifyUnalignedChunks:");
        boolean result = true;
        UnalignedHeapChunk.UnalignedHeader chunk = space.getFirstUnalignedHeapChunk();
        while (chunk.isNonNull()) {
            result &= UnalignedHeapChunk.verifyUnalignedHeapChunk(chunk);
            chunk = chunk.getNext();
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    boolean verifyOnlyCleanCards() {
        final Log trace = HeapImpl.getHeapImpl().getHeapVerifierImpl().getTraceLog().string("[SpaceVerifierImpl.VerifierImpl.verifyOnlyCleanCards:");
        trace.string("  space: ").string(space.getName()).newline();
        boolean result = verifyOnlyCleanAlignedChunks() && verifyOnlyCleanUnalignedChunks();
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    private boolean verifyOnlyCleanAlignedChunks() {
        final Log trace = HeapImpl.getHeapImpl().getHeapVerifierImpl().getTraceLog().string("[SpaceVerifierImpl.VerifierImpl.verifyOnlyAlignedChunks:").newline();
        boolean result = true;
        AlignedHeapChunk.AlignedHeader chunk = space.getFirstAlignedHeapChunk();
        while (chunk.isNonNull()) {
            result &= AlignedHeapChunk.verifyOnlyCleanCards(chunk);
            chunk = chunk.getNext();
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    private boolean verifyOnlyCleanUnalignedChunks() {
        final Log trace = HeapImpl.getHeapImpl().getHeapVerifierImpl().getTraceLog().string("[SpaceVerifierImpl.verifyOnlyCleanUnalignedChunks:").newline();
        boolean result = true;
        UnalignedHeapChunk.UnalignedHeader chunk = space.getFirstUnalignedHeapChunk();
        while (chunk.isNonNull()) {
            result &= UnalignedHeapChunk.verifyOnlyCleanCardsOfUnalignedHeapChunk(chunk);
            chunk = chunk.getNext();
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    private SpaceVerifierImpl() {
    }

    /*
     * State.
     */
    private Space space;
}
