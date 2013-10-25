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
import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;

/**
 * Tests integer to short conversion.
 */
public class IntShortConvertTest extends GraalKernelTester {

    static final int size = 128;
    static final int[] inputInt = new int[size];
    static final short[] inputShort = new short[size];
    @Result static final int[] outputInt = new int[size];
    @Result static final short[] outputShort = new short[size];
    static int[] seedInt = new int[size];
    {
        for (int i = 0; i < seedInt.length; i++) {
            seedInt[i] = (int) Math.random();
        }
    }
    static short[] seedShort = new short[size];
    {
        for (int i = 0; i < seedShort.length; i++) {
            seedShort[i] = (short) Math.random();
        }
    }

    public static void run(int[] inInt, short[] inShort, int[] outInt, short[] outShort, int gid) {
        outInt[gid] = inShort[gid];
        outShort[gid] = (short) inInt[gid];
    }

    @Override
    public void runTest() {
        System.arraycopy(seedShort, 0, inputShort, 0, seedShort.length);
        Arrays.fill(outputShort, (short) 0);
        System.arraycopy(seedInt, 0, inputInt, 0, seedInt.length);
        Arrays.fill(outputInt, 0);
        dispatchMethodKernel(64, inputInt, inputShort, outputInt, outputShort);
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }
}
