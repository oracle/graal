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
package com.sun.max.asm.gen.risc.bitRange;

/**
 */
public abstract class SimpleBitRange extends ContiguousBitRange {

    protected final int firstBitIndex;
    protected final int lastBitIndex;

    protected SimpleBitRange(int firstBitIndex, int lastBitIndex) {
        if (!(firstBitIndex >= 0 && lastBitIndex >= 0 && firstBitIndex < 32 && lastBitIndex < 32)) {
            throw new IllegalArgumentException("bit indexes must be between 0 and 31");
        }
        this.firstBitIndex = firstBitIndex;
        this.lastBitIndex = lastBitIndex;
    }

    @Override
    public int instructionMask() {
        return valueMask() << numberOfLessSignificantBits();
    }

    @Override
    public int encodedWidth() {
        return width();
    }

    // comparing

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SimpleBitRange)) {
            return false;
        }
        final SimpleBitRange simpleBitRange = (SimpleBitRange) other;
        return firstBitIndex == simpleBitRange.firstBitIndex && lastBitIndex == simpleBitRange.lastBitIndex;
    }

    @Override
    public int hashCode() {
        return firstBitIndex ^ lastBitIndex;
    }

    // extracting

    @Override
    public int extractSignedInt(int syllable) {
        final int unsignedInt = extractUnsignedInt(syllable);
        final int max = 1 << (width() - 1);
        if (unsignedInt < max) {
            return unsignedInt;
        }
        return (unsignedInt - valueMask()) - 1;
    }

    @Override
    public int extractUnsignedInt(int syllable) {
        return (syllable >>> numberOfLessSignificantBits()) & valueMask();
    }

    // inserting

    @Override
    public int assembleUncheckedSignedInt(int signedInt) {
        return (signedInt & valueMask()) << numberOfLessSignificantBits();
    }

    @Override
    public int assembleUncheckedUnsignedInt(int unsignedInt) {
        return (unsignedInt & valueMask()) << numberOfLessSignificantBits();
    }

    @Override
    public String encodingString(String value, boolean signed, boolean omitMask) {
        final StringBuilder sb = new StringBuilder();
        if (valueMask() == 0 || omitMask) {
            sb.append(value);
        } else {
            sb.append('(').append(value).append(" & 0x").append(Integer.toHexString(valueMask())).append(')');
        }
        if (numberOfLessSignificantBits() != 0) {
            return "(" + sb + " << " + numberOfLessSignificantBits() + ")";
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return firstBitIndex + ":" + lastBitIndex;
    }
}
