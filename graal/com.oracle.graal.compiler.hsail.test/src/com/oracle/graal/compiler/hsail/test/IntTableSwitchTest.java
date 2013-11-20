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

import org.junit.Test;
import com.oracle.graal.compiler.hsail.test.infra.*;

/**
 * Tests a switch statement with integer keys. This test exercises the TABLESWITCH Java bytecode
 * instruction.
 * 
 * The HSAIL code generated for this example is a series of cascading compare and branch
 * instructions for each case of the switch.
 * 
 * These instruction have the following form:
 * 
 * 
 * //Check whether the key matches the key constant of the case. Store the result of the compare (0
 * or 1) in the control register c0.
 * 
 * cmp_eq $c0 <source register>, <key constant for case statement>
 * 
 * //Branch to the corresponding label of that case if there's a match.
 * 
 * cbr $c0 <branch target for that case>
 */
public class IntTableSwitchTest extends GraalKernelTester {

    static final int num = 20;
    // Output array storing the results of the operations.
    @Result protected int[] outArray = new int[num];

    /**
     * The static "kernel" method we will be testing. This method writes to an output array based on
     * switching on an element of an input array.
     * 
     * Note: Because the key constants used in the cases of the switch are in consecutive order, the
     * Java source compiler compiles this example into the TABLESWITCH bytecode instruction. So this
     * is really a test to see whether the HSAIL backend is appropriately handling the TABLESWITCH
     * bytecode.
     * 
     * @param out the output array
     * @param ina the input array
     * @param gid the parameter used to index into the input and output arrays
     */
    public static void run(int[] out, int[] ina, int gid) {
        switch (ina[gid]) {
            case 0:
                out[gid] = ina[gid];
                break;
            case 1:
                out[gid] = ina[gid] * ina[gid];
                break;
            case 2:
                out[gid] = -ina[gid];
                break;
            case 3:
                out[gid] = ina[gid] - ina[gid];
                break;
            case 4:
                out[gid] = ina[gid] + ina[gid];
                break;
            case 5:
            case 6:
                out[gid] = ina[gid] * ina[gid];
                break;
            case 7:
                out[gid] = -ina[gid] * 7;
                break;
            case 8:
                break;
            case 9:
                int i = ina[gid] * 5;
                out[gid] = ina[gid] - ina[gid] + i;
                break;
            case 10:
                out[gid] = ina[gid] + ina[gid];
                break;
            default:
                out[gid] = 9;
                break;
        }
        out[gid] += ina[gid];
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
     * Initializes the input and output arrays passed to the run routine.
     * 
     * @param in the input array
     */
    void setupArrays(int[] in) {
        for (int i = 0; i < num; i++) {
            in[i] = i < num / 2 ? i : -i;
            outArray[i] = 0;
        }
    }

    /**
     * Dispatches the HSAIL kernel for this test case.
     */
    @Override
    public void runTest() {
        int[] inArray = new int[num];
        setupArrays(inArray);
        dispatchMethodKernel(num, outArray, inArray);
    }
}
