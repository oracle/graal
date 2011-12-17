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
package com.sun.max.asm.arm;

/**
* Handles validations for all immediate operands in ARM instructions.
*
*/

public final class ARMImmediates {

    private ARMImmediates() {
    }

    /**
     * Finds out legitimate 8 bit immediate value and 4 bit rotate value for the given 32 bit value. Throws an
     * exception if there doesn't exist any such combination, as such a value is an invalid operand.
     * 
     * @param value
     *            32 bit immediate operand
     * @return 12 bit shifter operand
     */
    public static int calculateShifter(int value) {
        int immed;
        for (int i = 0; i < 32; i += 2) {
            immed = Integer.rotateLeft(value, i);
            if (immed >= 0 && immed <= 255) {
                return immed | i << 7;
            }
        }
        throw new IllegalArgumentException("Invalid immediate operand value");
    }

    /**
     * Tests if rotate amount is even, hence valid.
     * 
     * @param value
     *            rotate amount specified as operand
     * @return
     */
    public static boolean isValidRotate(int value) {
        return value % 2 == 0;
    }

    /**
     * Tests if 32 bit immediate value is valid for 4 bit rotate and 8 bit immediate value representation.
     * 
     * @param value
     *            32 bit immediate operand value
     * @return
     */
    public static boolean isValidImmediate(int value) {
        int a;
        for (int i = 0; i < 32; i += 2) {
            a = Integer.rotateLeft(value, i);
            if (a >= 0 && a <= 255) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests if immediate shift value is between 0 and 32.
     * 
     * @param value
     *            6 bit number with only 0 to 32 allowed
     * @return
     */
    public static boolean isValidShiftImm(int value) {
        return value >= 0 && value <= 32;
    }

}
