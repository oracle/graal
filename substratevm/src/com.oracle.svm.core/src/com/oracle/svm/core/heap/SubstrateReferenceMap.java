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
import java.util.Map;
import java.util.NoSuchElementException;

import com.oracle.svm.core.amd64.FrameAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.ReferenceMapEncoder.OffsetIterator;

import jdk.vm.ci.code.ReferenceMap;
import jdk.vm.ci.code.StackSlot;
import jdk.vm.ci.meta.Value;

public class SubstrateReferenceMap extends ReferenceMap implements ReferenceMapEncoder.Input {

    private final BitSet input = new BitSet();

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
    public OffsetIterator getOffsets() {
        return new OffsetIterator() {
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
        };
    }

    @Override
    public int hashCode() {
        return input.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj instanceof SubstrateReferenceMap) {
            SubstrateReferenceMap other = (SubstrateReferenceMap) obj;
            return input.equals(other.input);
        } else {
            return false;
        }
    }
}
