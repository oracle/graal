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
import java.util.Arrays;

/**
 * Tests non-escaping object creation and calling a method on it.
 */
public class NonEscapingNewObjWithArrayTest extends GraalKernelTester {
    static final int NUM = 20;
    @Result public float[] outArray = new float[NUM];

    static class MyObj {
        float a[];

        public MyObj(float[] src, int ofst) {
            a = Arrays.copyOfRange(src, ofst, ofst + 3);
        }

        public float productOf() {
            return a[0] * a[1] * a[2];
        }
    }

    void setupArrays() {
        for (int i = 0; i < NUM; i++) {
            outArray[i] = -i;
        }
    }

    @Override
    public void runTest() {
        setupArrays();
        float[] fsrc = new float[2 * NUM];
        for (int i = 0; i < 2 * NUM; i++) {
            fsrc[i] = i;
        }

        dispatchLambdaKernel(NUM, (gid) -> {
            outArray[gid] = new MyObj(fsrc, gid).productOf();
        });
    }

    @Override
    protected boolean supportsRequiredCapabilities() {
        // although not escaping, seems to require object allocation support
        return (canHandleObjectAllocation());
    }

    // NYI emitForeignCall floatArraycopy
    @Test(expected = com.oracle.graal.graph.GraalInternalError.class)
    public void test() {
        testGeneratedHsail();
    }

    @Test(expected = com.oracle.graal.graph.GraalInternalError.class)
    public void testUsingLambdaMethod() {
        testGeneratedHsailUsingLambdaMethod();
    }

}
