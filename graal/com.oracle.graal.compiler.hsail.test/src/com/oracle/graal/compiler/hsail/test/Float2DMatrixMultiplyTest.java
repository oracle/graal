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

import org.junit.*;

/**
 * Tests 2D array access for Matrix Multiplication.
 */
public class Float2DMatrixMultiplyTest extends Float2DMatrixBase {

    int range = 20;

    public void run(int gid) {
        for (int j = 0; j < range; j++) {
            float sum = 0;
            for (int k = 0; k < range; k++) {
                sum += (matrixA[gid][k] * matrixB[k][j]);
            }
            outMatrix[gid][j] = sum;
        }
    }

    @Override
    public void runTest() {
        setupArrays(range);
        dispatchMethodKernel(range);
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }
}
