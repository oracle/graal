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

import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;

/**
 * Tests direct method calls.
 */
public class NestedVirtualCallTest extends GraalKernelTester {

    static final int width = 768;
    static final int height = width;
    private int iterations = 100;
    static final int range = width * height;
    @Result public float[] outArray = new float[range];

    public int a(int i) {
        if (i < 2) {
            return b(i);
        } else {
            return i;
        }
    }

    public int b(int i) {
        if (i < 90) {
            return c(i) + i;
        } else {
            return d(i) - i;
        }
    }

    public int c(int i) {

        return d(i) + 5;
    }

    public int d(int i) {

        return e(i) + 10;
    }

    public int e(int i) {
        return 52 + i;
    }

    public void run(int gid) {
        for (int i = 0; i < iterations; i++) {
            outArray[gid] = a(gid) + i;
        }
    }

    @Override
    public void runTest() {
        dispatchMethodKernel(range);
    }

    @Test
    public void test() {
        super.testGeneratedHsail();
    }
}
