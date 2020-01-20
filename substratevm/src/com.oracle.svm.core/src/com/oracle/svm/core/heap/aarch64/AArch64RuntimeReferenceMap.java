/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, 2020, Arm Limited. All rights reserved.
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
package com.oracle.svm.core.heap.aarch64;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.ReferenceMapEncoder;
import com.oracle.svm.core.heap.SubstrateReferenceMap;

/**
 * Encoding for runtime code references in AArch64. A different encoding is required to denote when
 * a reference inlined into code and split across multiple mov instructions. To accomplish this, a
 * notion of the number of pieces (i.e. number of places the reference is split across) is
 * introduced. This encoding differs from SubstrateReferenceMap in that if offset + 3 is set, then
 * it means the the following 4 bytes also contains a "piece" of the reference.
 */
public class AArch64RuntimeReferenceMap extends SubstrateReferenceMap {

    public AArch64RuntimeReferenceMap() {
        assert ConfigurationValues.getObjectLayout().getReferenceSize() > 3 : "needs to be four bits or more for encoding and validation";
    }

    public void markReferenceAtOffset(int offset, boolean compressed, int numPieces) {
        updateShiftedOffsets(offset);

        shiftedOffsets.set(offset + shift);
        if (compressed) {
            shiftedOffsets.set(offset + 1 + shift);
        }
        for (int i = 0; i < numPieces - 1; i++) {
            shiftedOffsets.set(offset + (i * 4) + 2 + shift);
        }
    }

    @Override
    public void markReferenceAtOffset(int offset, boolean compressed) {
        markReferenceAtOffset(offset, compressed, 1);
    }

    public void markReferenceAtOffset(int offset, int baseOffset, boolean compressed, int numPieces) {
        if (offset == baseOffset) {
            /* We might have already seen the offset as a base to a derived offset */
            if (derived == null || !derived.containsKey(baseOffset)) {
                markReferenceAtOffset(baseOffset, compressed, numPieces);
            }
            return;
        }

        assert numPieces == 1;

        if (!isOffsetMarked(baseOffset)) {
            markReferenceAtOffset(baseOffset, compressed, numPieces);
        }

        if (derived == null) {
            derived = EconomicMap.create(Equivalence.DEFAULT);
        }
        Set<Integer> derivedOffsets = derived.get(baseOffset);
        if (derivedOffsets == null) {
            derivedOffsets = new HashSet<>();
            derived.put(baseOffset, derivedOffsets);
        }

        assert !derivedOffsets.contains(offset);
        derivedOffsets.add(offset);
    }

    @Override
    public ReferenceMapEncoder.OffsetIterator getOffsets() {
        return new ReferenceMapEncoder.OffsetIterator() {
            private int nextShiftedOffset = shiftedOffsets == null ? -1 : shiftedOffsets.nextSetBit(0);

            private int getNumPieces(int offset) {
                assert isOffsetMarked(offset);
                int count = 1;
                int index = offset + 2;
                while (shiftedOffsets.get(index)) {
                    count++;
                    index += 4;
                }
                return count;
            }

            @Override
            public boolean hasNext() {
                return (nextShiftedOffset != -1);
            }

            @Override
            public int nextInt() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                int result = nextShiftedOffset - shift;
                int numPieces = getNumPieces(nextShiftedOffset);
                // skip bits corresponding to this marking
                nextShiftedOffset = shiftedOffsets.nextSetBit(nextShiftedOffset + (numPieces - 1) * 4 + 2);
                return result;
            }

            @Override
            public boolean isNextCompressed() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return shiftedOffsets.get(nextShiftedOffset + 1);
            }

            @Override
            public boolean isNextDerived() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return derived != null && derived.containsKey(nextShiftedOffset - shift);
            }

            @Override
            public Set<Integer> getDerivedOffsets(int baseOffset) {
                if (derived == null || !derived.containsKey(baseOffset)) {
                    throw new NoSuchElementException();
                }
                return derived.get(baseOffset);
            }

            @Override
            public int getNumPieces() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return getNumPieces(nextShiftedOffset);
            }
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj instanceof AArch64RuntimeReferenceMap) {
            return super.equals(obj);
        } else {
            return false;
        }
    }

}
