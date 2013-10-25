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

import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;

import org.junit.Test;

/**
 * 
 * Tests bitwise XOR of two bytes.
 */
public class ByteBitwiseXorTest extends GraalKernelTester {

    static final int num = 20;
    @Result protected int[] outArray1 = new int[num];

    /**
     * The static "kernel" method we will be testing. By convention the gid is the last parameter.
     * 
     */
    public static void run(int[] out1, byte[] ina, byte[] inb, int gid) {
        out1[gid] = (ina[gid] ^ inb[gid]);
    }

    @Test
    public void test() {
        super.testGeneratedHsail();
    }

    void setupArrays(byte[] in, byte[] in2) {
        for (int i = 0; i < num; i++) {
            in[i] = (byte) (i);
            in2[i] = (byte) (i * i);
            outArray1[i] = 0;
        }
    }

    @Override
    public void runTest() {
        byte[] inArray = new byte[num];
        byte[] inArray2 = new byte[num];
        setupArrays(inArray, inArray2);

        dispatchMethodKernel(num, outArray1, inArray, inArray2);
    }

}
