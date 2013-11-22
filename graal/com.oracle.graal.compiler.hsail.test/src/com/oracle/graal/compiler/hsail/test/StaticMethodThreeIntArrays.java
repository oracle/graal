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

/**
 * Superclass that initializes two input arrays containing ints and one output array. Derived by
 * some of the other test cases that take two arrays of ints as input parameters.
 */
public abstract class StaticMethodThreeIntArrays extends StaticMethodTwoIntArrays {

    /**
     * Initialize the input and output arrays.
     * 
     * @param in1 the first input array
     * @param in2 the second input array
     */
    void setupArrays(int[] in1, int[] in2) {
        for (int i = 0; i < num; i++) {
            // Fill input arrays with a mix of positive and negative values.
            in1[i] = i < num / 2 ? i + 1 : -(i + 1);
            in2[i] = (i & 1) == 0 ? in1[i] + 10 : -(in1[i] + 10);
            outArray[i] = -i;
        }
    }

    @Override
    public void runTest() {
        int[] inArray1 = new int[num];
        int[] inArray2 = new int[num];
        setupArrays(inArray1, inArray2);

        /**
         * DumpArrayParameters(inArray); Call it for a range, specifying testmethod args (but not
         * the fields it uses or the gid argument). Will put output in outArray.
         */
        dispatchMethodKernel(num, outArray, inArray1, inArray2);
    }
}
