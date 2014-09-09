/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.hsail.test.lambda;

import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;

import org.junit.*;

public class MoreThanEightArgsOOBTest extends GraalKernelTester {

    int[] makeIntArray(int size) {
        int[] out = new int[size];

        for (int i = 0; i < size; i++) {
            out[i] = 1;
        }
        return out;
    }

    final int rows = 4096;
    final int cols = 4096;
    final int loops = 1;

    @Result int[] result;

    void innerTest(int[] res, int[] a, int[] b, int[] c, int[] d, int base, int stride) {
        final int resCols = a.length;
        final int resRows = res.length;
        final int limit = resCols - stride;

        dispatchLambdaKernel(resRows, (row) -> {
            res[row] = 0;
            if (a != null) {
                for (int col = base; col < limit; col += 4) {
                    int p0 = 0;
                    int p1 = 0;
                    int p2 = 0;
                    int p3 = 0;
                    p0 = a[col] + b[col] + c[col] + d[col] + stride;
                    p1 = a[col + 1] + b[col + 1] + c[col + 1] + d[col + 1];
                    p2 = a[col + 2] + b[col + 2] + c[col + 2] + d[col + 2];
                    p3 = a[col + 3] + b[col + 3] + c[col + 3] + d[col + 5000];
                    res[row] += p0 + p1 + p2 + p3;
                }
            }
        });
    }

    @Override
    public void runTest() {
        int[] a;
        int[] b;
        int[] c;
        int[] d;

        result = makeIntArray(rows);
        a = makeIntArray(cols);
        b = makeIntArray(cols);
        c = makeIntArray(cols);
        d = makeIntArray(cols);
        for (int i = 0; i < loops; i++) {
            innerTest(result, a, b, c, d, 0, 4);
        }
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void test() {
        testGeneratedHsail();
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void testUsingLambdaMethod() {
        testGeneratedHsailUsingLambdaMethod();
    }
}