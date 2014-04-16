/*
 * Copyright (c) 2009, 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.hsail.test;

import org.junit.*;

import com.oracle.graal.compiler.hsail.test.infra.*;

/**
 * Tests bitwise shift right of int values. Generates an shr_s32 instruction.
 */
public class IntBitwiseShiftRightTest extends GraalKernelTester {

    static final int num = 100;
    // Output array containing the results of shift operations.
    @Result protected int[] outArray = new int[num];

    /**
     * The static "kernel" method we will be testing. This method performs a bitwise shift left
     * operation on each element of an input array and writes each result to the corresponding index
     * of an output array. By convention the gid is the last parameter.
     * 
     * @param out the output array
     * @param ina the input array
     * @param shiftAmount an array of values used for the shift magnitude
     * @param gid the parameter used to index into the input and output arrays
     */
    public static void run(int[] out, int[] ina, int[] shiftAmount, int gid) {
        out[gid] = ina[gid] >> shiftAmount[gid];
    }

    /**
     * Tests the HSAIL code generated for this unit test by comparing the result of executing this
     * code with the result of executing a sequential Java version of this unit test.
     */
    @Test
    public void test() {
        super.testGeneratedHsail();
    }

    /**
     * Initializes the arrays passed to the run routine.
     * 
     * We do this in such a way that the input arrays contain a mix of negative and positive values.
     * As a result, the work items will exercise all the different combinations for the sign of the
     * value being shifted and the sign of the shift magnitude.
     * 
     * @param in the input array
     */
    void setupArrays(int[] in, int[] shiftAmount) {
        for (int i = 0; i < num; i++) {
            in[i] = i < num / 2 ? i : -i;
            shiftAmount[i] = (i & 1) == 0 ? i : -i;
            outArray[i] = 0;
        }
    }

    /**
     * Dispatches the HSAIL kernel for this test case.
     */
    @Override
    public void runTest() {
        int[] inArray = new int[num];
        int[] shiftAmount = new int[num];
        setupArrays(inArray, shiftAmount);
        dispatchMethodKernel(num, outArray, inArray, shiftAmount);
    }
}
