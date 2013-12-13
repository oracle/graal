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
 * Tests intrinsic for call to Math.sqrt(double). Generates a sqrt_f64 instruction.
 */
public class DoubleSqrtTest extends GraalKernelTester {

    static final int size = 64;
    @Result double[] out = new double[size];

    /**
     * The static "kernel" method we will be testing. This method calls Math.sqrt() on an element of
     * an input array and writes the result to the corresponding index of an output array. By
     * convention the gid is the last parameter.
     * 
     * @param out the output array.
     * @param in the input array.
     * @param gid the parameter used to index into the input and output arrays.
     */
    public static void run(double[] in, double[] out, int gid) {
        out[gid] = Math.sqrt(in[gid]);
    }

    /**
     * Initializes the input and output arrays passed to the run routine.
     * 
     * @param in the input array.
     */
    void setupArrays(double[] in) {
        for (int i = 0; i < size; i++) {
            // Include positive and negative values as well as corner cases.
            if (i == 1) {
                in[i] = Double.NaN;
            } else if (i == 2) {
                in[i] = Double.NEGATIVE_INFINITY;
            } else if (i == 3) {
                in[i] = Double.POSITIVE_INFINITY;
            } else if (i == 4) {
                in[i] = -0.0;
            } else if (i > 5 && i < 10) {
                in[i] = i + 0.5;
            } else {
                in[i] = i < size / 2 ? i : -i;
            }
            out[i] = 0;
        }
    }

    /**
     * Dispatches the HSAIL kernel for this test case.
     */
    @Override
    public void runTest() {
        double[] inArray = new double[size];
        setupArrays(inArray);
        dispatchMethodKernel(size, inArray, out);
    }

    /**
     * Tests the HSAIL code generated for this unit test by comparing the result of executing this
     * code with the result of executing a sequential Java version of this unit test.
     */
    @Test
    public void test() {
        testGeneratedHsail();
    }
}
