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
package com.oracle.svm.core.heap;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;
import org.graalvm.compiler.core.common.NumUtil;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.config.ConfigurationValues;

import jdk.vm.ci.code.ReferenceMap;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.Value;

public class SubstrateReferenceMap extends ReferenceMap implements ReferenceMapEncoder.Input {

    /**
     * Stores the reference map data. 3 bits are currently required per entry: the first bit at
     * "offset" marks the offset in the reference map. The following bit at offset + 1 stores the
     * "compressed" information. For verification purposes, the following bit must always be 0,
     * otherwise {@link #isValidToMark} can have false positives.
     *
     * Offsets can also be negative, i.e., the reference map can contain stack slots of the callee
     * frame. Because {@link BitSet} only supports positive indices, the whole bit set is shifted by
     * {@link #shift} bits when negative offsets are required.
     */
    private BitSet shiftedOffsets;
    private int shift;

    /* Maps base references with references pointing to the interior of that object */
    private EconomicMap<Integer, Set<Integer>> derived;

    private Map<Integer, Object> debugAllUsedRegisters;
    private Map<Integer, Object> debugAllUsedStackSlots;

    public SubstrateReferenceMap() {
        assert ConfigurationValues.getObjectLayout().getReferenceSize() > 2 : "needs to be three bits or more for encoding and validation";
    }

    public boolean isOffsetMarked(int offset) {
        return shiftedOffsets != null && offset + shift >= 0 && shiftedOffsets.get(offset + shift);
    }

    public void markReferenceAtOffset(int offset, boolean compressed) {
        if (shiftedOffsets == null) {
            shiftedOffsets = new BitSet();
        }
        if (offset < -shift) {
            int newShift = NumUtil.roundUp(-offset, Long.SIZE);
            int shiftDelta = newShift - shift;
            assert shiftDelta > 0 && NumUtil.roundUp(shiftDelta, Long.SIZE) == shiftDelta;

            long[] oldData = shiftedOffsets.toLongArray();
            long[] newData = new long[oldData.length + shiftDelta / Long.SIZE];
            System.arraycopy(oldData, 0, newData, shiftDelta / Long.SIZE, oldData.length);
            shiftedOffsets = BitSet.valueOf(newData);
            shift = newShift;
        }

        assert isValidToMark(offset, compressed) : "already marked or would overlap with predecessor or successor";
        shiftedOffsets.set(offset + shift);
        if (compressed) {
            shiftedOffsets.set(offset + 1 + shift);
        }
    }

    public void markReferenceAtOffset(int offset, int baseOffset, boolean compressed) {
        if (offset == baseOffset) {
            /* We might have already seen the offset as a base to a derived offset */
            if (derived == null || !derived.containsKey(baseOffset)) {
                markReferenceAtOffset(baseOffset, compressed);
            }
            return;
        }

        if (!isOffsetMarked(baseOffset)) {
            markReferenceAtOffset(baseOffset, compressed);
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

    private boolean isValidToMark(int offset, boolean isCompressed) {
        int uncompressedSize = FrameAccess.uncompressedReferenceSize();
        int compressedSize = ConfigurationValues.getObjectLayout().getReferenceSize();

        int previousShiftedOffset = shiftedOffsets.previousSetBit(offset - 1 + shift);
        if (previousShiftedOffset != -1) {
            int minShiftedOffset = previousShiftedOffset + uncompressedSize;
            if (previousShiftedOffset != 0 && shiftedOffsets.get(previousShiftedOffset - 1)) {
                /* Found a compression bit, previous bit represents the reference. */
                previousShiftedOffset--;
                minShiftedOffset = previousShiftedOffset + compressedSize;
            }
            if (offset + shift < minShiftedOffset) {
                return false;
            }
        }
        int size = isCompressed ? compressedSize : uncompressedSize;
        int nextShiftedOffset = shiftedOffsets.nextSetBit(offset + shift);
        return (nextShiftedOffset == -1) || (offset + shift + size <= nextShiftedOffset);
    }

    public Map<Integer, Object> getDebugAllUsedRegisters() {
        return debugAllUsedRegisters;
    }

    boolean debugMarkRegister(int offset, Value value) {
        if (debugAllUsedRegisters == null) {
            debugAllUsedRegisters = new HashMap<>();
        }
        debugAllUsedRegisters.put(offset, value);
        return true;
    }

    public Map<Integer, Object> getDebugAllUsedStackSlots() {
        return debugAllUsedStackSlots;
    }

    boolean debugMarkStackSlot(int offset, StackSlot value) {
        if (debugAllUsedStackSlots == null) {
            debugAllUsedStackSlots = new HashMap<>();
        }
        debugAllUsedStackSlots.put(offset, value);
        return true;
    }

    @Override
    public boolean isEmpty() {
        return shiftedOffsets == null || shiftedOffsets.isEmpty();
    }

    @Override
    public ReferenceMapEncoder.OffsetIterator getOffsets() {
        return new ReferenceMapEncoder.OffsetIterator() {
            private int nextShiftedOffset = shiftedOffsets == null ? -1 : shiftedOffsets.nextSetBit(0);

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
                /* +1: skip compression bit. */
                nextShiftedOffset = shiftedOffsets.nextSetBit(nextShiftedOffset + 2);
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
        };
    }

    @Override
    public int hashCode() {
        return shift ^ (shiftedOffsets == null ? 0 : shiftedOffsets.hashCode()) ^ (derived == null ? 0 : derived.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj instanceof SubstrateReferenceMap) {
            SubstrateReferenceMap other = (SubstrateReferenceMap) obj;
            if (shift != other.shift || !Objects.equals(shiftedOffsets, other.shiftedOffsets)) {
                return false;
            }

            if (derived == null || other.derived == null) {
                return derived == null && other.derived == null;
            }

            if (derived.size() != other.derived.size()) {
                return false;
            }

            for (int base : derived.getKeys()) {
                if (!derived.get(base).equals(other.derived.get(base))) {
                    return false;
                }
            }

            return true;
        } else {
            return false;
        }
    }

    public boolean hasNoDerivedOffsets() {
        return derived == null || derived.isEmpty();
    }

    public void verify() {
        if (derived == null) {
            return;
        }

        for (int baseOffset : derived.getKeys()) {
            for (int derivedOffset : derived.get(baseOffset)) {
                assert !derived.containsKey(derivedOffset);
            }
        }
    }

    public StringBuilder dump(StringBuilder builder) {
        if (shiftedOffsets == null || shiftedOffsets.isEmpty()) {
            builder.append("[]");
            return builder;
        }

        builder.append('[');
        shiftedOffsets.stream().forEach(shiftedOffset -> {
            int offset = shiftedOffset - shift;
            builder.append(offset);
            if (derived != null && derived.containsKey(offset)) {
                builder.append(" -> {");
                for (int derivedOffset : derived.get(offset)) {
                    builder.append(derivedOffset);
                    builder.append(", ");
                }
                builder.replace(builder.length() - 2, builder.length(), "}");
            }
            builder.append(", ");
        });
        builder.replace(builder.length() - 2, builder.length(), "]");
        return builder;
    }

    @Override
    public String toString() {
        return dump(new StringBuilder()).toString();
    }
}
