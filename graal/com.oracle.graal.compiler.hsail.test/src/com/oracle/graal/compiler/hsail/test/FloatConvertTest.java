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
 * Tests floating point comparison.
 */
public class FloatConvertTest extends GraalKernelTester {

    static final int size = 128;
    static final float[] inputFloat = new float[size];
    static final double[] inputDouble = new double[size];
    @Result static final float[] outputFloat = new float[size];
    @Result static final double[] outputDouble = new double[size];
    static float[] seedFloat = new float[size];
    {
        for (int i = 0; i < seedFloat.length; i++) {
            seedFloat[i] = (float) Math.random();
        }
    }
    static double[] seedDouble = new double[size];
    {
        for (int i = 0; i < seedDouble.length; i++) {
            seedDouble[i] = Math.random();
        }
    }

    public static void run(float[] inFloat, double[] inDouble, float[] outFloat, double[] outDouble, int gid) {
        outFloat[gid] = (float) inDouble[gid];
        outDouble[gid] = inFloat[gid];
    }

    @Override
    public void runTest() {
        System.arraycopy(seedDouble, 0, inputDouble, 0, seedDouble.length);
        Arrays.fill(outputDouble, 0f);
        System.arraycopy(seedFloat, 0, inputFloat, 0, seedFloat.length);
        Arrays.fill(outputFloat, 0f);
        dispatchMethodKernel(64, inputFloat, inputDouble, outputFloat, outputDouble);
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }
}
