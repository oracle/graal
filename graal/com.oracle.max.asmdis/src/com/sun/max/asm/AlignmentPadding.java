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

/**
 * Byte stream padding for memory alignment in the assembler. This pseudo-instruction pads the byte stream to ensure that the next
 * memory location is aligned on the specified boundary.
 * Padding may also be performed conditionally to the amount of space left in the byte stream before the next aligned boundary.
 */
public abstract class AlignmentPadding extends MutableAssembledObject {

    public AlignmentPadding(Assembler assembler, int startPosition, int endPosition, int alignment, int alignmentStart, int requiredSpace, byte padByte) {
        super(assembler, startPosition, endPosition);
        this.alignment = alignment;
        this.padByte = padByte;
        this.alignmentStart = alignmentStart;
        this.maxMisalignment = alignment - requiredSpace;
        assembler.addAlignmentPadding(this);
    }

    private final int alignment;

    public final int alignment() {
        return alignment;
    }

    /**
     * Max misalignment supported. If greater than that, pad to the next alignment.
     * By default, this is zero, meaning that padding is always performed to the next alignment.
     * When we want to pad only if there isn't enough space to fit the next object of size s, maxMisalignment is
     * set to alignment - s.
     */
    private final int maxMisalignment;

    private final int alignmentStart;

    private final byte padByte;

    public void updatePadding() {
        // We avoid sign problems with '%' below by masking off the sign bit:
        final long unsignedAddend = (assembler().baseAddress() + startPosition()) & 0x7fffffffffffffffL;
        final int misalignmentSize = (int) (unsignedAddend % alignment);
        variableSize =  (misalignmentSize <= maxMisalignment)  ? 0 :  alignment - misalignmentSize;
    }

    @Override
    protected void assemble() throws AssemblyException {
        for (int i = alignmentStart; i < size(); i++) {
            assembler().emitByte(padByte);
        }
    }
}
