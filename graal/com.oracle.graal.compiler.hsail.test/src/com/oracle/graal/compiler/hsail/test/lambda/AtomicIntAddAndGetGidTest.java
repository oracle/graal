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

import java.util.concurrent.atomic.AtomicInteger;
import java.util.Arrays;

/**
 * Tests {@link AtomicInteger#addAndGet(int)} with a variable delta.
 */
public class AtomicIntAddAndGetGidTest extends GraalKernelTester {

    static final int NUM = 20;
    public int[] outArray = new int[NUM];
    @Result public int[] gaps = new int[NUM];
    AtomicInteger atomicInt;

    void setupArrays() {
        for (int i = 0; i < NUM; i++) {
            outArray[i] = -i;
        }
        atomicInt = new AtomicInteger(0);
    }

    @Override
    public void runTest() {
        setupArrays();

        dispatchLambdaKernel(NUM, (gid) -> {
            outArray[gid] = atomicInt.addAndGet(gid);
        });

        // note: the actual order of entries in outArray is not predictable
        // thus we sort before we compare results
        Arrays.sort(outArray);
        // System.out.print("outArray: ");
        // for (int val : outArray) {
        // System.out.print(val + ", ");
        // }
        // System.out.println();
        // create array of gaps
        gaps[0] = outArray[0] - 0;
        for (int i = 1; i < NUM; i++) {
            gaps[i] = outArray[i] - outArray[i - 1];
        }
        Arrays.sort(gaps);

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
