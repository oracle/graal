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
 * A range of bits that contributes to a field's value but does not occupy any bit
 * positions in an instruction. The implicit bits are 0. This type of bit range
 * is typically used to represent the low-order bits for a field value's that is
 * always modulo {@code n} where {@code n > 1}. That is, an aligned value.
 */
public class OmittedBitRange extends ContiguousBitRange {

    private int width;

    OmittedBitRange(int width) {
        this.width = width;
        assert width > 0;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int encodedWidth() {
        return 0;
    }

    @Override
    public BitRange move(boolean left, int bits) {
        return this;
    }

    /* Accessing */

    @Override
    public int instructionMask() {
        return 0;
    }

    @Override
    public int numberOfLessSignificantBits() {
        return 32;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof OmittedBitRange)) {
            throw new IllegalArgumentException("Invalid argument type");
        }
        final OmittedBitRange omittedBitRange = (OmittedBitRange) other;
        return width == omittedBitRange.width;
    }

    @Override
    public int hashCode() {
        return width;
    }

    /* Extracting */
    @Override
    public int extractSignedInt(int syllable) {
        return 0;
    }

    @Override
    public int extractUnsignedInt(int syllable) {
        return 0;
    }

    /* Inserting */
    @Override
    public int assembleUncheckedSignedInt(int signedInt) {
        return 0;
    }

    @Override
    public int assembleUncheckedUnsignedInt(int unsignedInt) {
        return 0;
    }

    @Override
    public String encodingString(String value, boolean signed, boolean checked) {
        return "";
    }

    @Override
    public String toString() {
        return "omit" + width;
    }
}
