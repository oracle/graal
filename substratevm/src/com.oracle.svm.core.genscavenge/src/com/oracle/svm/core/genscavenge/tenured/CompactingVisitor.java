/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.tenured;

import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.HeapChunk;

public class CompactingVisitor implements RelocationInfo.Visitor {

    private AlignedHeapChunk.AlignedHeader chunk;

    private UnsignedWord top;

    @Override
    public boolean visit(Pointer p) {
        return visitInline(p);
    }

    @AlwaysInline("GC performance")
    @Override
    public boolean visitInline(Pointer p) {
        Pointer relocationPointer = RelocationInfo.readRelocationPointer(p);
        if (relocationPointer.equal(p)) {
            /*
             * No copy. Data stays where it's currently located.
             */
            return true;
        }

        UnsignedWord size = calculatePlugSize(p);
        if (size.equal(0)) {
            /*
             * Gap at chunk start causes a 0 bytes 1st plug.
             */
            return true;
        }

        UnmanagedMemoryUtil.copy(p, relocationPointer, size);

        // TODO: Find a more elegant way to set the top pointer during/after compaction.
        AlignedHeapChunk.AlignedHeader newChunk = AlignedHeapChunk.getEnclosingChunkFromObjectPointer(relocationPointer);
        HeapChunk.setTopPointer(
                newChunk,
                relocationPointer.add(size)
        );
        if (chunk.notEqual(newChunk)) {
            HeapChunk.setTopPointer(
                    chunk,
                    AlignedHeapChunk.getObjectsStart(chunk)
            );
        }

        return true;
    }

    /**
     * May return {@code 0} values if there is a gap at chunk start!
     */
    @AlwaysInline("GC performance")
    private UnsignedWord calculatePlugSize(Pointer relocInfo) {
        Pointer nextRelocInfo = RelocationInfo.getNextRelocationInfo(relocInfo);
        if (nextRelocInfo.isNull()) {
            // Last plug of chunk! Plug size is based on chunk top.
            return top.subtract(relocInfo);
        }
        int gap = RelocationInfo.readGapSize(nextRelocInfo);
        return nextRelocInfo.subtract(gap).subtract(relocInfo);
    }

    public void init(AlignedHeapChunk.AlignedHeader c) {
        this.chunk = c;
        this.top = HeapChunk.getTopPointer(c);
    }
}
