/*
 * Copyright (c) 2010, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.api.code;

import java.util.*;

import com.oracle.graal.api.meta.*;

/**
 * The callee save area (CSA) is a contiguous space in a stack frame used to save (and restore) the
 * values of the caller's registers. This class describes the layout of a CSA in terms of its
 * {@linkplain #size size}, {@linkplain #slotSize slot size} and the {@linkplain #registers callee
 * save registers} covered by the CSA.
 */
public class CalleeSaveLayout {

    /**
     * The size (in bytes) of the CSA.
     */
    public final int size;

    /**
     * The size (in bytes) of an {@linkplain #registerAt(int) indexable} slot in the CSA.
     */
    public final int slotSize;

    /**
     * Map from {@linkplain Register#number register numbers} to slot indexes in the CSA.
     */
    private final int[] regNumToIndex;

    private final Register[] indexToReg;

    /**
     * The list of registers {@linkplain #contains(int) contained} by this CSA.
     */
    public final Register[] registers;

    /**
     * The offset from the frame pointer to the CSA. If this is not known, then this field will have
     * the value {@link Integer#MAX_VALUE}.
     */
    public final int frameOffsetToCSA;

    /**
     * Creates a CSA layout.
     * 
     * @param size size (in bytes) of the CSA. If this is {@code -1}, then the CSA size will be
     *            computed from {@code registers}.
     * @param slotSize the size (in bytes) of an {@linkplain #registerAt(int) indexable} slot in the
     *            CSA
     * @param registers the registers that can be saved in the CSA
     */
    public CalleeSaveLayout(TargetDescription target, int frameOffsetToCSA, int size, int slotSize, Register... registers) {
        this.frameOffsetToCSA = frameOffsetToCSA;
        assert slotSize == 0 || CodeUtil.isPowerOf2(slotSize);
        this.slotSize = slotSize;
        int maxRegNum = -1;
        int maxOffset = 0;
        this.registers = registers;
        int offset = 0;
        for (Register reg : registers) {
            assert offset % slotSize == 0;
            assert reg.number >= 0;
            if (reg.number > maxRegNum) {
                maxRegNum = reg.number;
            }
            if (offset > maxOffset) {
                maxOffset = offset;
            }
            PlatformKind kind = target.arch.getLargestStorableKind(reg.getRegisterCategory());
            offset += target.getSizeInBytes(kind);
        }
        if (size == -1) {
            this.size = offset;
        } else {
            assert offset <= size;
            this.size = size;
        }

        this.regNumToIndex = new int[maxRegNum + 1];
        this.indexToReg = offset == 0 ? new Register[0] : new Register[offset / slotSize];
        Arrays.fill(regNumToIndex, -1);
        offset = 0;
        for (Register reg : registers) {
            int index = offset / slotSize;
            regNumToIndex[reg.number] = index;
            indexToReg[index] = reg;
            PlatformKind kind = target.arch.getLargestStorableKind(reg.getRegisterCategory());
            offset += target.getSizeInBytes(kind);
        }
    }

    /**
     * Gets the offset of a given register in the CSA.
     * 
     * @return the offset (in bytes) of {@code reg} in the CSA
     * @throws IllegalArgumentException if {@code reg} does not have a slot in the CSA
     */
    public int offsetOf(int reg) {
        return indexOf(reg) * slotSize;
    }

    /**
     * Gets the index of a given register in the CSA.
     * 
     * @return the index of {@code reg} in the CSA
     * @throws IllegalArgumentException if {@code reg} does not have a slot in the CSA
     */
    public int indexOf(int reg) {
        if (!contains(reg)) {
            throw new IllegalArgumentException(String.valueOf(reg));
        }
        return regNumToIndex[reg];
    }

    /**
     * Gets the offset of a given register in the CSA.
     * 
     * @return the offset (in bytes) of {@code reg} in the CSA
     * @throws IllegalArgumentException if {@code reg} does not have a slot in the CSA
     */
    public int offsetOf(Register reg) {
        return offsetOf(reg.number);
    }

    /**
     * Determines if the CSA includes a slot for a given register.
     * 
     * @param reg the register to test
     * @return true if the CSA contains a slot for {@code reg}
     */
    public boolean contains(int reg) {
        return reg >= 0 && reg < regNumToIndex.length && regNumToIndex[reg] != -1;
    }

    /**
     * Gets the register whose slot in the CSA is at a given index.
     * 
     * @param index an index of a slot in the CSA
     * @return the register whose slot in the CSA is at {@code index} or {@code null} if
     *         {@code index} does not denote a slot in the CSA aligned with a register
     */
    public Register registerAt(int index) {
        if (index < 0 || index >= indexToReg.length) {
            return null;
        }
        return indexToReg[index];
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        for (Register reg : registers) {
            if (sb.length() != 1) {
                sb.append(", ");
            }
            sb.append(reg).append("{+").append(offsetOf(reg)).append('}');
        }
        return sb.append("] size=").append(size).toString();
    }
}
