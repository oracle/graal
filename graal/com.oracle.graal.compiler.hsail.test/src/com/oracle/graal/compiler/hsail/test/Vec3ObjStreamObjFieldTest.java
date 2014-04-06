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

import com.oracle.graal.compiler.hsail.test.infra.*;

/**
 * Tests codegen for a java 7 style object array stream kernel, no captures. Instance method which
 * accesses an object field
 */
public class Vec3ObjStreamObjFieldTest extends GraalKernelTester {

    static final int NUM = 20;

    @Result public Vec3[] inArray = new Vec3[NUM];
    Vec3 basevec = new Vec3(1, 2, 3);

    void setupArrays() {
        for (int i = 0; i < NUM; i++) {
            inArray[i] = new Vec3(i, i + 1, -1);
        }
    }

    /**
     * The "kernel" method we will be testing. For Array Stream, an object from the array will be
     * the last parameter
     */
    public void run(Vec3 vec3) {
        vec3.z = vec3.x + vec3.y - basevec.z;
    }

    @Override
    public void runTest() {
        setupArrays();
        dispatchMethodKernel(inArray);
    }

    @Test
    public void test() {
        testGeneratedHsail();
    }

}
