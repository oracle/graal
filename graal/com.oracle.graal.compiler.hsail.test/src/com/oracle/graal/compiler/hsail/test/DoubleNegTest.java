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
 * Tests negation of double values. Generates a neg_f64 instruction.
 */
public class DoubleNegTest extends GraalKernelTester {

    static final int num = 20;
    // Output array storing the results of negation operations.
    @Result protected double[] outArray = new double[num];

    /**
     * The static "kernel" method we will be testing. This method performs a negation operation on
     * an element of an input array and writes the result to the corresponding index of an output
     * array. By convention the gid is the last parameter.
     * 
     * @param out the output array.
     * @param ina the input array.
     * @param gid the parameter used to index into the input and output arrays.
     */
    public static void run(double[] out, double[] ina, int gid) {
        out[gid] = -(ina[gid]);
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
     * @param in the input array.
     */
    void setupArrays(double[] in) {
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
        double[] inArray = new double[num];
        setupArrays(inArray);
        dispatchMethodKernel(num, outArray, inArray);
    }
}
