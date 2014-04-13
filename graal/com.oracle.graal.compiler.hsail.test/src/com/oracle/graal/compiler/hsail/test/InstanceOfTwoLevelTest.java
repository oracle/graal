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

import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;
import org.junit.Test;

public class InstanceOfTwoLevelTest extends GraalKernelTester {

    static final int NUM = 20;

    static abstract class Shape {
        abstract public float getArea();
    }

    static class Ellipse extends Shape {
        private float major;
        private float minor;

        Ellipse(float major, float minor) {
            this.major = major;
            this.minor = minor;
        }

        public float getEccentricity() {
            float a = major / 2;
            float b = minor / 2;
            return (float) Math.sqrt(1 - (b / a) * (b / a));
        }

        @Override
        public float getArea() {
            float a = major / 2;
            float b = minor / 2;
            return (float) (Math.PI * a * b);
        }
    }

    static class Circle extends Ellipse {
        Circle(float r) {
            super(2 * r, 2 * r);
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
    public Object[] inShapeArray = new Object[NUM];

    void setupArrays() {
        for (int i = 0; i < NUM; i++) {
            switch (i % 4) {
                case 0:
                    inShapeArray[i] = new Circle(i + 1);
                    break;
                case 1:
                    inShapeArray[i] = new Square(i + 1);
                    break;
                case 2:
                    inShapeArray[i] = new Ellipse(i + 1, i + 2);
                    break;
                case 3:
                    inShapeArray[i] = new Object();
                    break;
            }
            outArray[i] = -i;
        }
    }

    public void run(int gid) {
        outArray[gid] = (inShapeArray[gid] instanceof Shape ? 1.0f : 2.0f);
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
