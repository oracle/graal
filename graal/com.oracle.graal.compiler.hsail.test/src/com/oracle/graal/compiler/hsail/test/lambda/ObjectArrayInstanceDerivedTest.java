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

import org.junit.*;

import com.oracle.graal.compiler.hsail.test.infra.GraalKernelTester;

/**
 * Tests calling a method on an object when there are derived types of that object. Note: if you
 * enable these tests, not only will these tests fail but other tests like ObjectArrayInstanceTest
 * will also fail because they depend on there being no derived classes from Body.
 */
public class ObjectArrayInstanceDerivedTest extends GraalKernelTester {

    static final int NUM = 20;

    class DerivedBody extends Body {

        DerivedBody(float x, float y, float z, float m) {
            super(x, y, z, m);
        }

        @Override
        public float getX() {
            return 42.0f;
        }
    }

    @Result public float[] outArray = new float[NUM];
    public Body[] inBodyArray = new Body[NUM];
    public Body[] unusedBodyArray = new Body[NUM];
    public DerivedBody[] unusedDerivedBodyArray = new DerivedBody[NUM];

    void setupArrays() {
        for (int i = 0; i < NUM; i++) {
            inBodyArray[i] = new Body(i, i + 1, i + 2, i + 3);
            // unusedBodyArray[i] = new DerivedBody(i, i+1, i+2, i+3);
            outArray[i] = -i;
        }
    }

    @Override
    public void runTest() {
        setupArrays();

        dispatchLambdaKernel(NUM, (gid) -> {
            Body b = inBodyArray[gid];
            outArray[gid] = b.getX() * b.getY();
        });
    }

    @Ignore
    @Test
    public void test() {
        testGeneratedHsail();
    }

    @Ignore
    @Test
    public void testUsingLambdaMethod() {
        testGeneratedHsailUsingLambdaMethod();
    }

}
