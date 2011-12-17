/*
 * Copyright (c) 2007, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm;

import com.sun.max.lang.*;

/**
 * An instruction that addresses some data as an offset from itself.
 */
public abstract class InstructionWithOffset extends InstructionWithLabel {

    /**
     * The mask of all the valid offset sizes supported by the union of all instructions that can address some data via an offset.
     */
    public static final int ALL_VALID_OFFSET_SIZES_MASK = WordWidth.BITS_8.numberOfBytes | WordWidth.BITS_16.numberOfBytes | WordWidth.BITS_32.numberOfBytes | WordWidth.BITS_64.numberOfBytes;

    private final int validOffsetSizesMask;
    private int offsetSize;

    /**
     * Creates an object representing an instruction that addresses some data as an offset from itself.
     *
     * @param startPosition the current position in the instruction stream of the instruction's first byte
     * @param endPosition the current position in the instruction stream one byte past the instruction's last byte
     * @param label a label representing the position referred to by this instruction
     * @param validOffsetSizesMask a mask of the offset sizes supported by a concrete instruction. This value must not
     *            be 0 and its set of non-zero bits must be a subset of the non-zero bits of
     *            {@link #ALL_VALID_OFFSET_SIZES_MASK}. The one-bit integer values (i.e. the powers of two)
     *            corresponding with each set bit in the mask are the offset sizes for which there is an available
     *            concrete instruction. The concrete instruction emitted once the label has been bound is the one with
     *            smallest offset size that can represent the distance between the label's position and this
     *            instruction.
     */
    protected InstructionWithOffset(Assembler assembler, int startPosition, int endPosition, Label label, int validOffsetSizesMask) {
        super(assembler, startPosition, endPosition, label);
        this.validOffsetSizesMask = validOffsetSizesMask;
        assert validOffsetSizesMask != 0;
        assert (validOffsetSizesMask & ~ALL_VALID_OFFSET_SIZES_MASK) == 0;
        if (Ints.isPowerOfTwoOrZero(validOffsetSizesMask)) {
            assembler.addFixedSizeAssembledObject(this);
            this.offsetSize = validOffsetSizesMask;
        } else {
            assembler.addSpanDependentInstruction(this);
            this.offsetSize = Integer.lowestOneBit(validOffsetSizesMask);
        }
    }

    protected InstructionWithOffset(Assembler assembler, int startPosition, int endPosition, Label label) {
        super(assembler, startPosition, endPosition, label);
        this.validOffsetSizesMask = 0;
        assembler.addFixedSizeAssembledObject(this);
    }

    void setSize(int nBytes) {
        variableSize = nBytes;
    }

    protected final int labelSize() {
        return offsetSize;
    }

    /**
     * Updates the size of this instruction's label based on the value bound to the label.
     *
     * @return true if the size of this instruction's label was changed
     */
    boolean updateLabelSize() throws AssemblyException {
        if (validOffsetSizesMask == 0) {
            return false;
        }
        int newOffsetSize = WordWidth.signedEffective(offset()).numberOfBytes;
        if (newOffsetSize > this.offsetSize) {
            final int maxLabelSize = Integer.highestOneBit(validOffsetSizesMask);
            if (newOffsetSize > maxLabelSize) {
                throw new AssemblyException("instruction cannot accomodate number of bits required for offset");
            }
            while ((newOffsetSize & validOffsetSizesMask) == 0) {
                newOffsetSize = newOffsetSize << 1;
            }
            this.offsetSize = newOffsetSize;
            return true;
        }
        return false;
    }

    private int offset() throws AssemblyException {
        return assembler().offsetInstructionRelative(label(), this);
    }

    protected byte offsetAsByte() throws AssemblyException {
        if (assembler().selectingLabelInstructions()) {
            return (byte) 0;
        }
        final int result = offset();
        if (Ints.numberOfEffectiveSignedBits(result) > 8) {
            throw new AssemblyException("label out of 8-bit range");
        }
        return (byte) result;
    }

    protected short offsetAsShort() throws AssemblyException {
        if (assembler().selectingLabelInstructions()) {
            return (short) 0;
        }
        final int result = offset();
        if (Ints.numberOfEffectiveSignedBits(result) > 16) {
            throw new AssemblyException("label out of 16-bit range");
        }
        return (short) result;
    }

    protected int offsetAsInt() throws AssemblyException {
        if (assembler().selectingLabelInstructions()) {
            return 0;
        }
        return offset();
    }

}
