/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.oracle.svm.core.code.CodeInfoQueryResult;
import com.oracle.svm.core.config.ConfigurationValues;

/**
 * Encodes the reference map of Java instances. Features such as derived pointers and mixing
 * compressed/uncompressed pointers are neither needed nor supported. The implementation favors
 * decoding speed over compactness. This is fine as there is a limited number of instance reference
 * maps. Each reference map has the following format:
 * <ul>
 * <li>int entryCount - the number of entries in the reference map</li>
 * <li>entryCount entries with the following format:
 * <ul>
 * <li>int offset - the offset where a reference is located</li>
 * <li>uint referenceCount - the number of adjacent references that are located at the offset</li>
 * </ul>
 * </li>
 * </ul>
 */
public class InstanceReferenceMapEncoder extends ReferenceMapEncoder {
    public static final int MAP_HEADER_SIZE = 4;
    public static final int MAP_ENTRY_SIZE = 8;

    @Override
    protected void encodeAll(List<Entry<Input, Long>> sortedEntries) {
        /*
         * The table always starts with the empty reference map. This allows clients to actually
         * iterate the empty reference map, making a check for the empty map optional.
         */
        assert CodeInfoQueryResult.EMPTY_REFERENCE_MAP == writeBuffer.getBytesWritten();
        writeBuffer.putS4(0);

        for (Map.Entry<ReferenceMapEncoder.Input, Long> entry : sortedEntries) {
            ReferenceMapEncoder.Input map = entry.getKey();
            encodings.put(map, encode(map.getOffsets()));
        }
    }

    private long encode(ReferenceMapEncoder.OffsetIterator offsets) {
        long startIndex = writeBuffer.getBytesWritten();
        // make room for the number of entries that we need to patch at the end
        writeBuffer.putS4(-1);

        int entries = 0;
        int adjacentReferences = 0;
        int offset = 0;

        if (offsets.hasNext()) {
            int referenceSize = ConfigurationValues.getObjectLayout().getReferenceSize();
            boolean compressed = offsets.isNextCompressed();
            int nextAdjacentOffset = 0;
            do {
                assert !offsets.isNextDerived() : "derived references are not located within objects";
                assert compressed == offsets.isNextCompressed() : "compressed and uncompressed references must not be mixed";

                int nextOffset = offsets.nextInt();
                if (nextOffset == nextAdjacentOffset) {
                    adjacentReferences++;
                } else {
                    assert nextOffset >= nextAdjacentOffset : "values must be strictly increasing";
                    if (adjacentReferences > 0) {
                        // The end of a run. Encode the *previous* run.
                        encodeRun(offset, adjacentReferences);
                        entries++;
                    }
                    // Beginning of the next gap+run pair.
                    offset = nextOffset;
                    adjacentReferences = 1;
                }

                nextAdjacentOffset = nextOffset + referenceSize;
            } while (offsets.hasNext());

            if (adjacentReferences > 0) {
                encodeRun(offset, adjacentReferences);
                entries++;
            }
        }

        writeBuffer.patchS4(entries, startIndex);
        return startIndex;
    }

    private void encodeRun(int offset, int refsCount) {
        assert offset >= 0 && refsCount >= 0;
        writeBuffer.putS4(offset);
        writeBuffer.putU4(refsCount);
    }
}
