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

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk;
import com.oracle.svm.core.genscavenge.HeapChunk;
import com.oracle.svm.core.genscavenge.HeapParameters;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.Space;
import com.oracle.svm.core.genscavenge.remset.BrickTable;
import com.oracle.svm.core.hub.LayoutEncoding;

import jdk.graal.compiler.word.Word;

public class PlanningVisitor implements AlignedHeapChunk.Visitor {

    private static final SweepPreparingVisitor SWEEP_PREPARING_VISITOR = new SweepPreparingVisitor();

    private AlignedHeapChunk.AlignedHeader allocationChunk;

    private Pointer allocationPointer;

    @Platforms(Platform.HOSTED_ONLY.class)
    public PlanningVisitor() {
    }

    @Override
    public boolean visitChunk(AlignedHeapChunk.AlignedHeader chunk) {
        return visitChunkInline(chunk);
    }

    @Override
    @AlwaysInline("GC performance")
    public boolean visitChunkInline(AlignedHeapChunk.AlignedHeader chunk) {
        Pointer cursor = AlignedHeapChunk.getObjectsStart(chunk);
        Pointer top = HeapChunk.getTopPointer(chunk); // top can't move here, therefore it's fine to read once

        Pointer relocationInfoPointer = AlignedHeapChunk.getObjectsStart(chunk);
        UnsignedWord gapSize = WordFactory.zero();
        UnsignedWord plugSize = WordFactory.zero();

        UnsignedWord brick = WordFactory.zero();
        UnsignedWord fragmentation = WordFactory.zero();

        /*
         * Write the first relocation info just before objects start.
         */
        RelocationInfo.writeRelocationPointer(relocationInfoPointer, allocationPointer);
        RelocationInfo.writeGapSize(relocationInfoPointer, 0);
        RelocationInfo.writeNextPlugOffset(relocationInfoPointer, 0);

        BrickTable.setEntry(chunk, brick, relocationInfoPointer);

        while (cursor.belowThan(top)) {
            Word header = ObjectHeaderImpl.readHeaderFromPointer(cursor);
            Object obj = cursor.toObject();

            UnsignedWord objSize;
            if (ObjectHeaderImpl.isForwardedHeader(header)) {
                Object forwardedObj = ObjectHeaderImpl.getObjectHeaderImpl().getForwardedObject(cursor, header);
                objSize = LayoutEncoding.getSizeFromObjectWithoutOptionalIdHashFieldInGC(forwardedObj);
            } else {
                objSize = LayoutEncoding.getSizeFromObjectInlineInGC(obj);
            }

            if (ObjectHeaderImpl.hasMarkedBit(header)) {
                ObjectHeaderImpl.clearMarkedBit(obj);

                /*
                 * Adding the optional identity hash field will increase the object's size,
                 * but in here, when compacting the tenured space, we expect that there aren't any marked objects
                 * which have their "IdentityHashFromAddress" object header flag set.
                 */
                assert !ConfigurationValues.getObjectLayout().isIdentityHashFieldOptional()
                        || !ObjectHeaderImpl.hasIdentityHashFromAddressInline(header)
                        || chunk.getShouldSweepInsteadOfCompact();

                if (gapSize.notEqual(0)) {

                    /*
                     * Update previous relocation info or set the chunk's "FirstRelocationInfo" pointer.
                     */
                    int offset = (int) cursor.subtract(relocationInfoPointer).rawValue();
                    RelocationInfo.writeNextPlugOffset(relocationInfoPointer, offset);

                    /*
                     * Write the current relocation info at the gap end.
                     */
                    relocationInfoPointer = cursor;
                    RelocationInfo.writeGapSize(relocationInfoPointer, (int) gapSize.rawValue());
                    RelocationInfo.writeNextPlugOffset(relocationInfoPointer, 0);

                    fragmentation = fragmentation.add(gapSize);
                    gapSize = WordFactory.zero();
                }

                plugSize = plugSize.add(objSize);
            } else {
                if (plugSize.notEqual(0)) {
                    /*
                     * Update previous relocation info to set its relocation pointer.
                     */
                    Pointer relocationPointer = getRelocationPointer(plugSize);
                    RelocationInfo.writeRelocationPointer(relocationInfoPointer, relocationPointer);

                    plugSize = WordFactory.zero();

                    /*
                     * Update brick table entry.
                     */
                    UnsignedWord currentBrick = BrickTable.getIndex(chunk, cursor);
                    while (brick.belowThan(currentBrick)) {
                        brick = brick.add(1);
                        BrickTable.setEntry(chunk, brick, relocationInfoPointer);
                    }
                }

                gapSize = gapSize.add(objSize);
            }

            cursor = cursor.add(objSize);
        }

        /*
         * Sanity check
         */
        assert gapSize.equal(0) || plugSize.equal(0);

        /*
         * Check for a gap at chunk end that requires updating the chunk top offset to clear that memory.
         */
        if (gapSize.notEqual(0)) {
            chunk.setTopOffset(chunk.getTopOffset().subtract(gapSize));
        }

        if (plugSize.notEqual(0)) {
            Pointer relocationPointer = getRelocationPointer(plugSize);
            RelocationInfo.writeRelocationPointer(relocationInfoPointer, relocationPointer);
        }

        /*
         * Sweep chunk instead of compacting on low fragmentation as the freed memory isn't worth the effort.
         */
        fragmentation = fragmentation.add(HeapChunk.getEndOffset(chunk).subtract(HeapChunk.getTopOffset(chunk)));
        if (shouldSweepBasedOnFragmentation(fragmentation)) {
            chunk.setShouldSweepInsteadOfCompact(true);
        }

        /*
         * Prepare for sweeping. Actual sweep will be done in compacting phase.
         */
        if (chunk.getShouldSweepInsteadOfCompact()) {
            RelocationInfo.visit(chunk, SWEEP_PREPARING_VISITOR);

            // Reset allocation pointer as we want to resume after the swept memory.
            this.allocationChunk = chunk;
            this.allocationPointer = HeapChunk.getTopPointer(chunk);
        }

        /*
         * Update remaining brick table entries at chunk end.
         */
        brick = brick.add(1);
        while (brick.belowThan(BrickTable.getLength())) {
            BrickTable.setEntry(chunk, brick, relocationInfoPointer);
            brick = brick.add(1);
        }

        return true;
    }

    private Pointer getRelocationPointer(UnsignedWord size) {
        Pointer relocationPointer = allocationPointer;
        allocationPointer = allocationPointer.add(size);
        if (AlignedHeapChunk.getObjectsEnd(allocationChunk).belowThan(allocationPointer)) {
            allocationChunk = HeapChunk.getNext(allocationChunk);
            relocationPointer = AlignedHeapChunk.getObjectsStart(allocationChunk);
            allocationPointer = relocationPointer.add(size);
        }
        return relocationPointer;
    }

    /**
     * @return {@code true} if {@code 0 <= fragmentation ratio < 0.0625}
     */
    @AlwaysInline("GC Performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean shouldSweepBasedOnFragmentation(UnsignedWord fragmentation) {
        UnsignedWord limit = HeapParameters.getAlignedHeapChunkSize().unsignedShiftRight(4);
        return fragmentation.aboveOrEqual(0) && fragmentation.belowThan(limit);
    }

    public void init(Space space) {
        allocationChunk = space.getFirstAlignedHeapChunk();
        allocationPointer = AlignedHeapChunk.getObjectsStart(allocationChunk);
    }

    private static class SweepPreparingVisitor implements RelocationInfo.Visitor {

            @Override
            public boolean visit(Pointer p) {
                return visitInline(p);
            }

            @Override
            public boolean visitInline(Pointer p) {
                RelocationInfo.writeRelocationPointer(p, p);
                return true;
            }
    }
}
