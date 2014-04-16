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

import static com.oracle.graal.debug.Debug.*;

import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;
import com.oracle.graal.debug.*;

import org.junit.Test;

/**
 * Tests a true virtual method call.
 */
public class VirtualCallTest extends GraalKernelTester {

    static final int NUM = 20;

    static abstract class Shape {

        abstract public float getArea();
    }

    static class Circle extends Shape {

        private float radius;

        Circle(float r) {
            radius = r;
        }

        @Override
        public float getArea() {
            return (float) (Math.PI * radius * radius);
        }
    }

    static class Square extends Shape {

        private float len;

        Square(float _len) {
            len = _len;
        }

        @Override
        public float getArea() {
            return len * len;
        }
    }

    @Result public float[] outArray = new float[NUM];
    public Shape[] inShapeArray = new Shape[NUM];

    void setupArrays() {
        for (int i = 0; i < NUM; i++) {
            if (i % 2 == 0)
                inShapeArray[i] = new Circle(i + 1);
            else
                inShapeArray[i] = new Square(i + 1);
            outArray[i] = -i;
        }
    }

    @Override
    public void runTest() {
        setupArrays();

        dispatchLambdaKernel(NUM, (gid) -> {
            Shape shape = inShapeArray[gid];
            outArray[gid] = shape.getArea();
        });
    }

    // graal says not inlining getArea():float (0 bytes): no type profile exists
    @Test(expected = com.oracle.graal.graph.GraalInternalError.class)
    public void test() {
        try (DebugConfigScope s = disableIntercept()) {
            testGeneratedHsail();
        }
    }

    @Test(expected = com.oracle.graal.graph.GraalInternalError.class)
    public void testUsingLambdaMethod() {
        try (DebugConfigScope s = disableIntercept()) {
            testGeneratedHsailUsingLambdaMethod();
        }
    }

}
