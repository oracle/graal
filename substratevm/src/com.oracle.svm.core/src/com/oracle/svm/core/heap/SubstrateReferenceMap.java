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
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.config.ConfigurationValues;

import jdk.vm.ci.code.ReferenceMap;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.Value;

public class SubstrateReferenceMap extends ReferenceMap implements ReferenceMapEncoder.Input {

    private final BitSet input = new BitSet();

    /* Maps base references with references pointing to the interior of that object */
    private EconomicMap<Integer, Set<Integer>> derived;

    private Map<Integer, Object> debugAllUsedRegisters;
    private Map<Integer, Object> debugAllUsedStackSlots;

    public SubstrateReferenceMap() {
        assert ConfigurationValues.getObjectLayout().getReferenceSize() > 2 : "needs to be three bits or more for encoding and validation";
    }

    public boolean isOffsetMarked(int offset) {
        return input.get(offset);
    }

    public boolean isOffsetCompressed(int offset) {
        assert isOffsetMarked(offset);
        return input.get(offset + 1);
    }

    public void markReferenceAtOffset(int offset, boolean compressed) {
        assert isValidToMark(offset, compressed) : "already marked or would overlap with predecessor or successor";
        input.set(offset);
        if (compressed) {
            input.set(offset + 1);
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

        int previousOffset = input.previousSetBit(offset - 1);
        if (previousOffset != -1) {
            int minOffset = previousOffset + uncompressedSize;
            if (previousOffset != 0 && input.get(previousOffset - 1)) {
                previousOffset--; // found a compression bit, previous bit represents the reference
                minOffset = previousOffset + compressedSize;
            }
            if (offset < minOffset) {
                return false;
            }
        }
        int size = isCompressed ? compressedSize : uncompressedSize;
        int nextIndex = input.nextSetBit(offset);
        return (nextIndex == -1) || (offset + size <= nextIndex);
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
        return input.isEmpty();
    }

    @Override
    public ReferenceMapEncoder.OffsetIterator getOffsets() {
        return new ReferenceMapEncoder.OffsetIterator() {
            private int nextIndex = input.nextSetBit(0);

            @Override
            public boolean hasNext() {
                return (nextIndex != -1);
            }

            @Override
            public int nextInt() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                int index = nextIndex;
                nextIndex = input.nextSetBit(index + 2); // +1: skip compression bit
                return index;
            }

            @Override
            public boolean isNextCompressed() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return isOffsetCompressed(nextIndex);
            }

            @Override
            public boolean isNextDerived() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return derived != null && derived.containsKey(nextIndex);
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
        return input.hashCode() + ((derived == null) ? 0 : derived.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj instanceof SubstrateReferenceMap) {
            SubstrateReferenceMap other = (SubstrateReferenceMap) obj;
            if (!input.equals(other.input)) {
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

    public void dump(StringBuilder builder) {
        if (input.isEmpty()) {
            builder.append("[]");
            return;
        }

        builder.append('[');
        input.stream().forEach(offset -> {
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
    }
}
