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
package com.sun.max.asm.gen.cisc.x86;

/**
 */
public final class X86Field {

    public final int shift;
    public final int mask;

    private X86Field(int shift, int width) {
        this.shift = shift;
        this.mask = ~(0xffffffff << width);
    }

    public int shift() {
        return shift;
    }

    public int extract(byte b) {
        final int ub = b & 0xff;
        return (ub >> shift) & mask;
    }

    public int inPlace(byte value) {
        return value << shift;
    }

    public static final X86Field RM = new X86Field(0, 3);
    public static final X86Field REG = new X86Field(3, 3);
    public static final X86Field MOD = new X86Field(6, 2);

    public static final X86Field BASE = new X86Field(0, 3);
    public static final X86Field INDEX = new X86Field(3, 3);
    public static final X86Field SCALE = new X86Field(6, 2);

    public static final int REX_B_BIT_INDEX = 0;
    public static final int REX_X_BIT_INDEX = 1;
    public static final int REX_R_BIT_INDEX = 2;
    public static final int REX_W_BIT_INDEX = 3;

    public static int extractRexValue(int rexBitIndex, byte rexByte) {
        final int urexByte = rexByte & 0xff;
        return ((urexByte >> rexBitIndex) & 1) << 3;
    }

    public static int inRexPlace(int rexBitIndex, byte rexValue) {
        final int urexValue = rexValue & 0xff;
        return ((urexValue >> 3) & 1) << rexBitIndex;
    }
}
