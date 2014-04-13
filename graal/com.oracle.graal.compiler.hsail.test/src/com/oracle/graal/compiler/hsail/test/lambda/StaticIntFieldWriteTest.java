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

package com.oracle.graal.compiler.hsail.test.lambda;

import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;
import org.junit.Test;

/**
 * Tests writing a static int field.
 */
public class StaticIntFieldWriteTest extends GraalKernelTester {

    static final int NUM = 20;
    @Result public int[] outArray = new int[NUM];
    public int[] inArray = new int[NUM];
    @Result int fieldResult;

    static int intStaticField = -99;

    void setupArrays() {
        for (int i = 0; i < NUM; i++) {
            inArray[i] = i;
            outArray[i] = -i;
        }
    }

    @Override
    public void runTest() {
        setupArrays();
        intStaticField = -99;
        dispatchLambdaKernel(NUM, (gid) -> {
            outArray[gid] = inArray[gid] * 2;
            if (gid == 3)
                intStaticField = outArray[gid];
        });
        fieldResult = intStaticField;   // save for kerneltester comparison
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }

    @Test
    public void testUsingLambdaMethod() {
        testGeneratedHsailUsingLambdaMethod();
    }

}
