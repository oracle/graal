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
package com.sun.max.asm.ppc;

import com.sun.max.asm.*;
import com.sun.max.util.*;

/**
 * The constants denoting the eight 4-bit fields into which the 32-bit Condition Register
 * is logically partitioned.
 */
public enum CRF implements EnumerableArgument<CRF> {

    CR0, CR1, CR2, CR3, CR4, CR5, CR6, CR7;

    /**
     * Determines if a mask of bits only selects one of the 8 condition register fields.
     */
    public static boolean isExactlyOneCRFSelected(int mask) {
        return (mask & (mask - 1)) == 0 && mask > 0 && mask <= 128;
    }

    public int value() {
        return ordinal();
    }

    public long asLong() {
        return value();
    }

    public String externalValue() {
        return Integer.toString(ordinal());
    }

    public String disassembledValue() {
        return externalValue();
    }

    public Enumerator<CRF> enumerator() {
        return ENUMERATOR;
    }

    public CRF exampleValue() {
        return CR0;
    }

    /**
     * Given the index of a bit within this 4-bit field, returns the index of the same bit
     * in the 32-bit Condition Register.
     * 
     * @throws IllegalArgumentException if n is not between 0 and 3 inclusive
     */
    public int bitFor(int n) {
        if (n < 0 || n > 3) {
            throw new IllegalArgumentException("bit specifier must be between 0 and 3");
        }
        return ordinal() * 4 + n;
    }

    /**
     * @return the index of the bit within the 32-bit Condition Register corresponding to the <i>less than</i> bit in this Condition Register field
     */
    public int lt() {
        return bitFor(LT);
    }

    /**
     * @return the index of the bit within the 32-bit Condition Register corresponding to the <i>greater than</i> bit in this Condition Register field
     */
    public int gt() {
        return bitFor(GT);
    }

    /**
     * @return the index of the bit within the 32-bit Condition Register corresponding to the <i>equal</i> bit in this Condition Register field
     */
    public int eq() {
        return bitFor(EQ);
    }

    /**
     * @return the index of the bit within the 32-bit Condition Register corresponding to the <i>summary overflow</i> bit in this Condition Register field
     */
    public int so() {
        return bitFor(SO);
    }

    /**
     * @return the index of the bit within the 32-bit Condition Register corresponding to the <i>unordered (after floating-point comparison)</i> bit in this Condition Register field
     */
    public int un() {
        return bitFor(UN);
    }

    /**
     * Index of the bit in a 4-bit Condition Register field indicating a <i>less than</i> condition.
     */
    public static final int LT = 0;

    /**
     * Index of the bit in a 4-bit Condition Register field indicating a <i>greater than</i> condition.
     */
    public static final int GT = 1;

    /**
     * Index of the bit in a 4-bit Condition Register field indicating an <i>equal</i> condition.
     */
    public static final int EQ = 2;

    /**
     * Index of the bit in a 4-bit Condition Register field indicating a <i>summary overflow</i> condition.
     */
    public static final int SO = 3;

    /**
     * Index of the bit in a 4-bit Condition Register field indicating an <i>unordered (after floating-point comparison)</i> condition.
     */
    public static final int UN = 3;

    public static final Enumerator<CRF> ENUMERATOR = new Enumerator<CRF>(CRF.class);

}
