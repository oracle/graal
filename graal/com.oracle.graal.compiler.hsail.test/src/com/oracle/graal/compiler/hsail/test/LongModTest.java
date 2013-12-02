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

import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;
import org.junit.Test;

/**
 * Tests the remainder operation (%) on two longs. Generates a rem_s64 instruction.
 */
public class LongModTest extends GraalKernelTester {

    static final int num = 20;
    @Result protected long[] outArray = new long[num];

    /**
     * The static "kernel" method we will be testing. By convention the gid is the last parameter.
     * This routine performs the remainder operation (%) on elements from two input arrays and
     * writes the result to the corresponding index of an output array.
     * 
     * @param out the output array
     * @param ina the first input array
     * @param inb the second input array
     * @param gid the parameter used to index into the input and output arrays
     */
    public static void run(long[] out, long[] ina, long[] inb, int gid) {
        out[gid] = (ina[gid] % inb[gid]);
    }

    @Test
    public void test() {
        super.testGeneratedHsail();
    }

    /**
     * Initialize input and output arrays.
     * 
     * @param in first input array
     * @param in2 second input array
     */
    void setupArrays(long[] in, long[] in2) {
        for (int i = 0; i < num; i++) {
            // Fill input arrays with a mix of positive and negative values.
            in[i] = i < num / 2 ? i + 1 : -(i + 1);
            in2[i] = (i & 1) == 0 ? i + 10 : -(i + 10);
            outArray[i] = 0;
        }
    }

    /**
     * Dispatches the HSAIL kernel for this test case.
     */
    @Override
    public void runTest() {
        long[] inArray = new long[num];
        long[] inArray2 = new long[num];
        setupArrays(inArray, inArray2);
        dispatchMethodKernel(num, outArray, inArray, inArray2);
    }

}
