/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.graal.compiler.hsail.test.infra.*;

import org.junit.*;

/**
 * Tests {@link System#arraycopy} for object arrays where dest and src type are same, and overlap.
 */
public class ObjArrayCopyConjointTest extends GraalKernelTester {

    final static int MAXOUTSIZ = 100;
    final static int NUM = 20;

    @Result String[][] outArray = new String[NUM][MAXOUTSIZ];

    @Override
    public void runTest() {
        for (int i = 0; i < NUM; i++) {
            for (int j = 0; j < outArray[i].length; j++) {
                outArray[i][j] = Integer.toString(i * 100 + j);
            }
        }
        dispatchLambdaKernel(NUM, (gid) -> {
            System.arraycopy(outArray[gid], 0, outArray[gid], gid % NUM, NUM);
        });
    }

    @Test
    public void testUsingLambdaMethod() {
        testGeneratedHsailUsingLambdaMethod();
    }
}
