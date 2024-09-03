/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.compacting;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.HeapChunk;

/** Moves sequences of live objects to their planned location during compaction. */
public final class CompactingVisitor implements ObjectMoveInfo.Visitor {
    private AlignedHeapChunk.AlignedHeader chunk;

    public void init(AlignedHeapChunk.AlignedHeader c) {
        this.chunk = c;
        HeapChunk.setTopPointer(c, AlignedHeapChunk.getObjectsStart(c));
    }

    @Override
    public boolean visit(Pointer objSeq, UnsignedWord size, Pointer destAddress, Pointer nextObjSeq) {
        if (size.equal(0)) { // gap right at the chunk's start
            assert objSeq.equal(AlignedHeapChunk.getObjectsStart(chunk));
            return true;
        }

        AlignedHeapChunk.AlignedHeader destChunk;
        if (destAddress.equal(objSeq)) {
            destChunk = chunk;
        } else {
            UnmanagedMemoryUtil.copy(objSeq, destAddress, size);
            destChunk = AlignedHeapChunk.getEnclosingChunkFromObjectPointer(destAddress);
        }
        HeapChunk.setTopPointerCarefully(destChunk, destAddress.add(size));
        return true;
    }
}
