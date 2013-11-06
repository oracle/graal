/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Tests instanceof operator. Requires correct support for decompression of klass ptrs.
 */
public class InstanceOfTest extends GraalKernelTester {

    static final int NUM = 20;

    abstract static class Shape {

        public abstract float getArea();
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

        Square(float len) {
            this.len = len;
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
            if (i % 2 == 0) {
                inShapeArray[i] = new Circle(i + 1);
            } else {
                inShapeArray[i] = new Square(i + 1);
            }
            outArray[i] = -i;
        }
    }

    public void run(int gid) {
        outArray[gid] = (inShapeArray[gid] instanceof Circle ? 1.0f : 2.0f);
    }

    @Override
    public void runTest() {
        setupArrays();

        dispatchMethodKernel(NUM);
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }
}
