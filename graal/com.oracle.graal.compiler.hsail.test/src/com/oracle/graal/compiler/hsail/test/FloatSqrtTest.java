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

import com.oracle.graal.compiler.hsail.*;
import com.oracle.graal.compiler.hsail.test.infra.*;
import com.oracle.graal.graph.*;

/**
 * Tests floating point square root.
 */
public class FloatSqrtTest extends GraalKernelTester {

    static final int size = 128;
    static final float[] input = new float[size];
    @Result static final float[] output = new float[size];
    static float[] seed = new float[size];
    {
        for (int i = 0; i < seed.length; i++) {
            seed[i] = (float) Math.random();
        }

    }

    public static void run(float[] input1, float[] output1, int gid) {
        output1[gid] = (float) Math.sqrt(input1[gid]);
    }

    @Override
    public void runTest() {
        System.arraycopy(seed, 0, input, 0, seed.length);
        Arrays.fill(output, 0f);
        dispatchMethodKernel(64, input, output);
    }

    /**
     * Requires {@link HSAILLIRGenerator#emitDirectCall} to be implemented.
     */
    @Test(expected = GraalInternalError.class)
    public void test() {
        testGeneratedHsail();
    }
}
