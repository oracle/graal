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

import java.util.*;

import org.junit.*;

import com.oracle.graal.compiler.hsail.test.infra.*;

/**
 * Tests double to long conversion.
 */
public class DoubleLongConvertTest extends GraalKernelTester {

    static final int size = 128;
    static final double[] inputDouble = new double[size];
    static final long[] inputLong = new long[size];
    @Result static final double[] outputDouble = new double[size];
    @Result static final long[] outputLong = new long[size];
    static double[] seedDouble = new double[size];
    {
        for (int i = 0; i < seedDouble.length; i++) {
            seedDouble[i] = (int) Math.random();
        }
    }
    static long[] seedLong = new long[size];
    {
        for (int i = 0; i < seedLong.length; i++) {
            seedLong[i] = (long) Math.random();
        }
    }

    public static void run(double[] inDouble, long[] inLong, double[] outDouble, long[] outLong, int gid) {
        outDouble[gid] = inLong[gid];
        outLong[gid] = (long) inDouble[gid];
    }

    @Override
    public void runTest() {
        System.arraycopy(seedLong, 0, inputLong, 0, seedLong.length);
        Arrays.fill(outputLong, 0);
        System.arraycopy(seedDouble, 0, inputDouble, 0, seedDouble.length);
        Arrays.fill(outputDouble, 0);
        dispatchMethodKernel(64, inputDouble, inputLong, outputDouble, outputLong);
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }
}
