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
 * Tests the spilling of double variables into memory.
 */
public class StaticDoubleSpillTest extends GraalKernelTester {

    static final int size = 100;
    private double[] in = new double[size * 400];
    @Result private double[] out = new double[size * 400];

    public static void run(double[] out, double[] in, int gid) {
        int id = gid;
        int step = 20;
        double sum0;
        double sum1;
        double sum2;
        double sum3;
        double sum4;
        double sum5;
        double sum6;
        double sum7;
        double sum8;
        double sum9;
        double sum10;
        double sum11;
        double sum12;
        double sum13;
        double sum14;
        double sum15;
        double sum16;
        double sum17;
        double sum18;
        double sum19;
        sum0 = sum1 = sum2 = sum3 = sum4 = sum5 = sum6 = sum7 = sum8 = sum9 = 0;
        sum10 = sum11 = sum12 = sum13 = sum14 = sum15 = sum16 = sum17 = sum18 = sum19 = 0;
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
            sum10 += in[i + 0];
            sum11 += in[i + 1];
            sum12 += in[i + 2];
            sum13 += in[i + 3];
            sum14 += in[i + 4];
            sum15 += in[i + 5];
            sum16 += in[i + 6];
            sum17 += in[i + 7];
            sum18 += in[i + 8];
            sum19 += in[i + 9];
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
        out[id * step + 10] = sum10;
        out[id * step + 11] = sum11;
        out[id * step + 12] = sum12;
        out[id * step + 13] = sum13;
        out[id * step + 14] = sum14;
        out[id * step + 15] = sum15;
        out[id * step + 16] = sum16;
        out[id * step + 17] = sum17;
        out[id * step + 18] = sum18;
        out[id * step + 19] = sum19;
    }

    @Override
    public void runTest() {
        /**
         * Call it for a range, specifying testmethod args (but not the fields it uses or the gid
         * argument).
         * 
         */
        Arrays.fill(out, 0f);
        Arrays.fill(in, 0f);
        dispatchMethodKernel(size, out, in);
    }

    @Test
    @Ignore("until stack slots are supported in deopt")
    public void test() {
        testGeneratedHsail();
    }

}
