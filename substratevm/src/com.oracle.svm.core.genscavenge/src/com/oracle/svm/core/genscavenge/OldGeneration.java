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

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.genscavenge.GCImpl.ChunkReleaser;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.log.Log;

/**
 * An OldGeneration has two Spaces, {@link #fromSpace} for existing objects, and {@link #toSpace}
 * for newly-allocated or promoted objects.
 */
public final class OldGeneration extends Generation {
    /* This Spaces are final and are flipped by transferring chunks from one to the other. */
    private final Space fromSpace;
    private final Space toSpace;

    private final GreyObjectsWalker toGreyObjectsWalker = new GreyObjectsWalker();

    @Platforms(Platform.HOSTED_ONLY.class)
    OldGeneration(String name) {
        super(name);
        int age = HeapParameters.getMaxSurvivorSpaces() + 1;
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
    public Object promoteAlignedObject(Object original, AlignedHeapChunk.AlignedHeader originalChunk, Space originalSpace) {
        if (originalSpace.isFromSpace()) {
            return getToSpace().promoteAlignedObject(original, originalSpace);
        }
        return original;
    }

    @AlwaysInline("GC performance")
    @Override
    protected Object promoteUnalignedObject(Object original, UnalignedHeapChunk.UnalignedHeader originalChunk, Space originalSpace) {
        if (originalSpace.isFromSpace()) {
            getToSpace().promoteUnalignedHeapChunk(originalChunk, originalSpace);
        }
        return original;
    }

    public void promoteObjectChunk(Object obj) {
        getToSpace().promoteObjectChunk(obj);
    }

    void releaseSpaces(ChunkReleaser chunkReleaser) {
        getFromSpace().releaseChunks(chunkReleaser);
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
        log.string("Old generation: ").indent(true);
        getFromSpace().report(log, traceHeapChunks).newline();
        getToSpace().report(log, traceHeapChunks).newline();
        log.redent(false);
        return log;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    Space getFromSpace() {
        return fromSpace;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
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

    /**
     * This value is only updated during a GC. Be careful when calling this method during a GC as it
     * might wrongly include chunks that will be freed at the end of the GC.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    UnsignedWord getChunkBytes() {
        UnsignedWord fromBytes = getFromSpace().getChunkBytes();
        UnsignedWord toBytes = getToSpace().getChunkBytes();
        return fromBytes.add(toBytes);
    }
}
