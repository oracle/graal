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

final class SpaceVerifier {
    private Space space;

    SpaceVerifier() {
    }

    public void initialize(Space s) {
        this.space = s;
    }

    public boolean verify() {
        Log trace = HeapVerifier.getTraceLog();
        trace.string("[SpaceVerifier.verify:").string("  ").string(space.getName()).newline();
        boolean isTLASpace = ThreadLocalAllocation.isThreadLocalAllocationSpace(space);
        if (isTLASpace) {
            ThreadLocalAllocation.disableAndFlushForAllThreads();
        }
        boolean result = true;
        if (!verifyChunkLists()) {
            HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("verifyChunkLists() returns false").string("]").newline();
            result = false;
        }
        if (result && !verifyChunks()) {
            HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[SpaceVerifier.verify:").string("  verifyChunks fails").string("]").newline();
            result = false;
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    boolean containsChunks() {
        AlignedHeapChunk.AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        UnalignedHeapChunk.UnalignedHeader uChunk = space.getFirstUnalignedHeapChunk();
        return (aChunk.isNonNull() || uChunk.isNonNull());
    }

    /** Verify the chunk list structures, but not the chunks themselves. */
    private boolean verifyChunkLists() {
        Log trace = HeapVerifier.getTraceLog();
        trace.string("[SpaceVerifier.verifyChunkLists:");
        boolean result = verifyAlignedChunkList() && verifyUnalignedChunkList();
        trace.string("  returns: ").bool(result);
        trace.string("]").newline();
        return result;
    }

    /** Verify the AlignedChunk list, but not the chunks themselves. */
    private boolean verifyAlignedChunkList() {
        Log trace = HeapVerifier.getTraceLog().string("[SpaceVerifier.verifyAlignedChunkList:");
        trace.string("  Space: ").string(space.getName()).newline();
        boolean result = true;
        AlignedHeapChunk.AlignedHeader current = space.getFirstAlignedHeapChunk();
        AlignedHeapChunk.AlignedHeader previous = WordFactory.nullPointer();
        while (current.isNonNull()) {
            AlignedHeapChunk.AlignedHeader previousOfCurrent = HeapChunk.getPrevious(current);
            result &= previousOfCurrent.equal(previous);
            if (!result) {
                Log failure = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[SpaceVerifier.verifyAlignedChunkList:");
                failure.string("  space: ").string(space.getName()).string("  doubly-linked list failure").newline();
                failure.string("  current: ").hex(current);
                failure.string("  current.previous: ").hex(previousOfCurrent);
                failure.string("  previous: ").hex(previous);
                failure.string("]").newline();
                break;
            }
            previous = current;
            current = HeapChunk.getNext(current);
        }
        result &= previous.equal(space.getLastAlignedHeapChunk());
        if (!result) {
            Log failure = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[SpaceVerifier.verifyAlignedChunkList:");
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
    private boolean verifyUnalignedChunkList() {
        Log trace = HeapVerifier.getTraceLog().string("[SpaceVerifier.verifyUnalignedChunkList:");
        boolean result = true;
        UnalignedHeapChunk.UnalignedHeader current = space.getFirstUnalignedHeapChunk();
        UnalignedHeapChunk.UnalignedHeader previous = WordFactory.nullPointer();
        while (current.isNonNull()) {
            result &= HeapChunk.getPrevious(current).equal(previous);
            if (!result) {
                Log failure = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[SpaceVerifier.verifyUnalignedChunkList:");
                failure.string("  space: ").string(space.getName()).string("  doubly-linked list failure").string("]").newline();
                break;
            }
            previous = current;
            current = HeapChunk.getNext(current);
        }
        result &= previous.equal(space.getLastUnalignedHeapChunk());
        if (!result) {
            Log failure = HeapImpl.getHeapImpl().getHeapVerifier().getWitnessLog().string("[SpaceVerifier.verifyUnalignedChunkList:");
            failure.string("  space: ").string(space.getName()).string("  lastUnalignedHeapChunk failure").string("]").newline();
            result = false;
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    /** Verify the contents of the chunks. */
    private boolean verifyChunks() {
        Log trace = HeapVerifier.getTraceLog().string("[SpaceVerifier.verifyChunks:");
        boolean result = verifyAlignedChunks() && verifyUnalignedChunks();
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    private boolean verifyAlignedChunks() {
        Log trace = HeapVerifier.getTraceLog().string("[SpaceVerifier.verifyAlignedChunks:");
        boolean result = true;
        AlignedHeapChunk.AlignedHeader chunk = space.getFirstAlignedHeapChunk();
        while (chunk.isNonNull()) {
            result &= AlignedHeapChunk.verify(chunk);
            chunk = HeapChunk.getNext(chunk);
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    private boolean verifyUnalignedChunks() {
        Log trace = HeapVerifier.getTraceLog().string("[SpaceVerifier.verifyUnalignedChunks:");
        boolean result = true;
        UnalignedHeapChunk.UnalignedHeader chunk = space.getFirstUnalignedHeapChunk();
        while (chunk.isNonNull()) {
            result &= UnalignedHeapChunk.verify(chunk);
            chunk = HeapChunk.getNext(chunk);
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    boolean verifyOnlyCleanCards() {
        Log trace = HeapVerifier.getTraceLog().string("[SpaceVerifier.verifyOnlyCleanCards:");
        trace.string("  space: ").string(space.getName()).newline();
        boolean result = verifyOnlyCleanAlignedChunks() && verifyOnlyCleanUnalignedChunks();
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    private boolean verifyOnlyCleanAlignedChunks() {
        Log trace = HeapVerifier.getTraceLog().string("[SpaceVerifier.verifyOnlyCleanAlignedChunks:").newline();
        boolean result = true;
        AlignedHeapChunk.AlignedHeader chunk = space.getFirstAlignedHeapChunk();
        while (chunk.isNonNull()) {
            result &= AlignedHeapChunk.verifyOnlyCleanCards(chunk);
            chunk = HeapChunk.getNext(chunk);
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    private boolean verifyOnlyCleanUnalignedChunks() {
        Log trace = HeapVerifier.getTraceLog().string("[SpaceVerifier.verifyOnlyCleanUnalignedChunks:").newline();
        boolean result = true;
        UnalignedHeapChunk.UnalignedHeader chunk = space.getFirstUnalignedHeapChunk();
        while (chunk.isNonNull()) {
            result &= UnalignedHeapChunk.verifyOnlyCleanCards(chunk);
            chunk = HeapChunk.getNext(chunk);
        }
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }
}
