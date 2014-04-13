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
import com.oracle.graal.compiler.hsail.test.Vec3;

/**
 * Tests codegen for a java 8 style object array stream kernel, one int and one float capture.
 */
public class Vec3ObjStreamIntFloatCaptureTest extends GraalKernelTester {

    static final int NUM = 20;

    @Result public Vec3[] inArray = new Vec3[NUM];
    int baseAdjustment = 7;
    float baseMultiplier = 0.5f;

    void setupArrays() {
        for (int i = 0; i < NUM; i++) {
            inArray[i] = new Vec3(i, i + 1, -1);
        }
    }

    @Override
    public void runTest() {
        setupArrays();
        int adjustment = baseAdjustment;
        float multiplier = baseMultiplier;

        dispatchLambdaKernel(inArray, obj -> {
            Vec3 vec3 = (Vec3) obj;
            vec3.z = (vec3.x + vec3.y - adjustment) * multiplier;
        });
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
