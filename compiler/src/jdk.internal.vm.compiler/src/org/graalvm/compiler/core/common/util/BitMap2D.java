/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.common.util;

import java.util.BitSet;

/**
 * This class implements a two-dimensional bitmap.
 */
public final class BitMap2D {

    private BitSet map;
    private final int bitsPerSlot;

    private int bitIndex(int slotIndex, int bitWithinSlotIndex) {
        return slotIndex * bitsPerSlot + bitWithinSlotIndex;
    }

    private boolean verifyBitWithinSlotIndex(int index) {
        assert index < bitsPerSlot : "index " + index + " is out of bounds " + bitsPerSlot;
        return true;
    }

    public BitMap2D(int sizeInSlots, int bitsPerSlot) {
        long nBits = (long) sizeInSlots * bitsPerSlot;
        if (nBits > Integer.MAX_VALUE) {
            // Avoids issues where (sizeInSlots * bitsPerSlot) wraps around to a positive integer
            throw new OutOfMemoryError("Cannot allocate a BitSet for " + nBits + " bits");
        }
        map = new BitSet(sizeInSlots * bitsPerSlot);
        this.bitsPerSlot = bitsPerSlot;
    }

    public boolean at(int slotIndex, int bitWithinSlotIndex) {
        assert verifyBitWithinSlotIndex(bitWithinSlotIndex);
        return map.get(bitIndex(slotIndex, bitWithinSlotIndex));
    }

    public void setBit(int slotIndex, int bitWithinSlotIndex) {
        assert verifyBitWithinSlotIndex(bitWithinSlotIndex);
        map.set(bitIndex(slotIndex, bitWithinSlotIndex));
    }

    public void clear() {
        map.clear();
    }
}
