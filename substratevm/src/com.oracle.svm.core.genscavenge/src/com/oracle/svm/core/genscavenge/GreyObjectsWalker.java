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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.log.Log;

/**
 * Apply an ObjectVisitor to all the new Object in a Space since a snapshot.
 *
 * This knows that allocations take place from the last HeapChunks of the Space. And it knows (too
 * much about) that AlignedChunks have a top pointer.
 */
/* TODO: Does this know too much about the internals of AlignedChunks? */
/* TODO: Should there be a corresponding class in AlignedChunk? */
public final class GreyObjectsWalker {

    /** A factory for an instance that will be initialized lazily. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static GreyObjectsWalker factory() {
        return new GreyObjectsWalker();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private GreyObjectsWalker() {
        /* Nothing to do. */
    }

    /**
     * Take a snapshot of a Space, such that all Object in the Space are now black, and any new
     * Objects in the Space will be grey, and can have an ObjectVisitor applied to them.
     *
     * @param s The Space to snapshot.
     */
    void setScanStart(final Space s) {
        final Log trace = Log.noopLog().string("[Space.GreyObjectsWalker.setScanStart:").string("  s: ").string(s.getName());
        /* Remember the snapshot "constants". */
        space = s;
        final AlignedHeapChunk.AlignedHeader aChunk = s.getLastAlignedHeapChunk();
        setAlignedHeapChunk(aChunk);
        trace.string("  alignedHeapChunk: ").hex(getAlignedHeapChunk()).string("  isNull: ").bool(aChunk.isNull());
        alignedTop = (aChunk.isNonNull() ? aChunk.getTop() : WordFactory.nullPointer());
        trace.string("  alignedTop: ").hex(alignedTop);
        final UnalignedHeapChunk.UnalignedHeader uChunk = s.getLastUnalignedHeapChunk();
        setUnalignedHeapChunk(uChunk);
        trace.string("  unalignedChunkPointer: ").hex(getUnalignedHeapChunk()).string("]").newline();
    }

    /**
     * Compare the snapshot to the current state of the Space to see if there are grey Objects.
     *
     * @return True if the snapshot updated, false otherwise.
     */
    private boolean haveGreyObjects() {
        final Log trace = Log.noopLog().string("[Space.GreyObjectsWalker.haveGreyObjects:");
        /* Any difference is a difference. */
        boolean result = false;
        result |= (getAlignedHeapChunk().notEqual(space.getLastAlignedHeapChunk()));
        result |= (getAlignedHeapChunk().isNonNull() && alignedTop.notEqual(getAlignedHeapChunk().getTop()));
        result |= (getUnalignedHeapChunk().notEqual(space.getLastUnalignedHeapChunk()));
        trace.string("  returns: ").bool(result).string("]").newline();
        return result;
    }

    boolean walkGreyObjects(final ObjectVisitor visitor) {
        final Log trace = Log.noopLog().string("[Space.GreyObjectsWalker.walkGreyObjects:");
        while (haveGreyObjects()) {
            trace.newline();
            /* Walk the grey objects. */
            if (!walkAlignedGreyObjects(visitor)) {
                /* Log the failure. */
                Log.log().string("[Space.GreyObjectsWalker.walkGreyObjects:  walkAlignedGreyObjects fails.]").newline();
                return false;
            }
            if (!walkUnalignedGreyObjects(visitor)) {
                /* Log the failure. */
                Log.log().string("[Space.GreyObjectsWalker.walkGreyObjects:  walkUnalignedGreyObjects fails.]").newline();
                return false;
            }
        }
        trace.string("  returns true").string("]").newline();
        return true;
    }

    private boolean walkAlignedGreyObjects(final ObjectVisitor visitor) {
        final Log trace = Log.noopLog().string("[Space.GreyObjectsWalker.walkAlignedGreyObjects:");
        /* Locals that start from the snapshot. */
        AlignedHeapChunk.AlignedHeader aChunk = WordFactory.nullPointer();
        Pointer aOffset = WordFactory.nullPointer();
        if (getAlignedHeapChunk().isNull() && getAlignedTop().isNull()) {
            /* If the snapshot is empty, then I have to walk from the beginning of the Space. */
            aChunk = space.getFirstAlignedHeapChunk();
            aOffset = (aChunk.isNonNull() ? AlignedHeapChunk.getAlignedHeapChunkStart(aChunk) : WordFactory.nullPointer());
        } else {
            /* Otherwise walk Objects that arrived after the snapshot. */
            aChunk = getAlignedHeapChunk();
            aOffset = getAlignedTop();
        }
        /* Visit Objects in the AlignedChunks. */
        while (aChunk.isNonNull()) {
            trace.newline().string("  aChunk: ").hex(aChunk).string("  aOffset: ").hex(aOffset);
            if (!AlignedHeapChunk.walkObjectsFrom(aChunk, aOffset, visitor)) {
                /* Log the failure. */
                Log.log().string("[Space.GreyObjectsWalker.walkAlignedGreyObjects:  aChunk.walkObject fails.]").newline();
                return false;
            }
            /* Move the scan point. */
            setAlignedHeapChunk(aChunk);
            setAlignedTop(aChunk.getTop());
            trace.string("  moved aligned scan point to: ").string("  alignedChunk: ").hex(getAlignedHeapChunk()).string("  alignedTop: ").hex(getAlignedTop());
            /* Step to the next AlignedChunk. */
            aChunk = aChunk.getNext();
            aOffset = (aChunk.isNonNull() ? AlignedHeapChunk.getAlignedHeapChunkStart(aChunk) : WordFactory.nullPointer());
        }
        trace.string("  returns true").string("]").newline();
        return true;
    }

    private boolean walkUnalignedGreyObjects(final ObjectVisitor visitor) {
        final Log trace = Log.noopLog().string("[Space.GreyObjectsWalker.walkUnalignedGreyObjects:");
        /* Visit the Objects in the UnalignedChunk after the snapshot UnalignedChunk. */
        UnalignedHeapChunk.UnalignedHeader uChunk;
        if (getUnalignedHeapChunk().isNull()) {
            uChunk = space.getFirstUnalignedHeapChunk();
        } else {
            uChunk = getUnalignedHeapChunk().getNext();
        }
        while (uChunk.isNonNull()) {
            trace.newline();
            trace.string("  uChunk: ").hex(uChunk);
            if (!UnalignedHeapChunk.walkObjectsFrom(uChunk, UnalignedHeapChunk.getUnalignedHeapChunkStart(uChunk), visitor)) {
                /* Log the failure. */
                Log.log().string("[Space.GreyObjectsWalker.walkUnalignedGreyObjects:  uChunk.walkObject fails.]").newline();
                return false;
            }
            /* Move the scan point. */
            setUnalignedHeapChunk(uChunk);
            trace.string("  moved unaligned scan point to: ").string("  unalignedChunk: ").hex(getUnalignedHeapChunk());
            /* Step to the next AlignedChunk. */
            uChunk = uChunk.getNext();
        }
        trace.string("  returns true").string("]").newline();
        return true;
    }

    /*
     * Methods to maintain HeapChunks as Pointers.
     */

    private AlignedHeapChunk.AlignedHeader getAlignedHeapChunk() {
        return alignedHeapChunk;
    }

    private void setAlignedHeapChunk(final AlignedHeapChunk.AlignedHeader aChunk) {
        alignedHeapChunk = aChunk;
    }

    private UnalignedHeapChunk.UnalignedHeader getUnalignedHeapChunk() {
        return unalignedHeapChunk;
    }

    private void setUnalignedHeapChunk(UnalignedHeapChunk.UnalignedHeader uChunk) {
        unalignedHeapChunk = uChunk;
    }

    private Pointer getAlignedTop() {
        return alignedTop;
    }

    private void setAlignedTop(final Pointer value) {
        alignedTop = value;
    }

    /*
     * Snapshot state.
     */

    /* The Space that is snapshot. */
    private Space space;
    /* The top of the Space, as Pointers rather than HeapChunks. */
    private AlignedHeapChunk.AlignedHeader alignedHeapChunk;
    private Pointer alignedTop;
    private UnalignedHeapChunk.UnalignedHeader unalignedHeapChunk;
}
