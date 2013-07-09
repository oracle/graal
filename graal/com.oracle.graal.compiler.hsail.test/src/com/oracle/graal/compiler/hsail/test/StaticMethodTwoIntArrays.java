/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Superclass that initializes one input array and one output array. Derived by some of the other
 * test cases that take one array as input and write to an output array.
 */
public abstract class StaticMethodTwoIntArrays extends GraalKernelTester {

    static final int num = 20;
    @Result protected int[] outArray = new int[num];

    void setupArrays(int[] in) {
        for (int i = 0; i < num; i++) {
            in[i] = i;
            outArray[i] = -i;
        }
    }

    @Override
    public void runTest() {
        int[] inArray = new int[num];
        setupArrays(inArray);
        /**
         * DumpArrayParameters(inArray); Call it for a range, specifying testmethod args (but not
         * the fields it uses or the gid argument). Will put output in outArray.
         */
        dispatchMethodKernel(num, outArray, inArray);
    }

}
