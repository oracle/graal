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

import java.util.*;

import org.junit.*;

import com.oracle.graal.compiler.hsail.test.infra.*;

/**
 * Tests the spilling of integers into memory.
 */
public class StaticIntSpillTest extends GraalKernelTester {

    static final int size = 100;
    private int[] in = new int[size * 400];
    @Result private int[] out = new int[size * 400];

    public static void run(int[] out, int[] in, int gid) {
        int id = gid;
        int step = 20;
        int sum0;
        int sum1;
        int sum2;
        int sum3;
        int sum4;
        int sum5;
        int sum6;
        int sum7;
        int sum8;
        int sum9;
        sum0 = sum1 = sum2 = sum3 = sum4 = sum5 = sum6 = sum7 = sum8 = sum9 = 0;
        for (int i = 0; i < size; i += step) {
            sum0 += in[i + 0];
            sum1 += in[i + 1];
            sum2 += in[i + 2];
            sum3 += in[i + 3];
            sum4 += in[i + 4];
            sum5 += in[i + 5];
            sum6 += in[i + 6];
            sum7 += in[i + 7];
            sum8 += in[i + 8];
            sum9 += in[i + 9];
        }
        out[id * step + 0] = sum0;
        out[id * step + 1] = sum1;
        out[id * step + 2] = sum2;
        out[id * step + 3] = sum3;
        out[id * step + 4] = sum4;
        out[id * step + 5] = sum5;
        out[id * step + 6] = sum6;
        out[id * step + 7] = sum7;
        out[id * step + 8] = sum8;
        out[id * step + 9] = sum9;
    }

    @Override
    public void runTest() {
        /**
         * Call it for a range, specifying testmethod args (but not the fields it uses or the gid
         * argument).
         * 
         */
        Arrays.fill(out, 0);
        Arrays.fill(in, 0);
        dispatchMethodKernel(size, out, in);
    }

    // Marked to only run on hardware until simulator spill bug is fixed.
    @Ignore
    @Test
    public void test() {
        testGeneratedHsail();
    }

}
