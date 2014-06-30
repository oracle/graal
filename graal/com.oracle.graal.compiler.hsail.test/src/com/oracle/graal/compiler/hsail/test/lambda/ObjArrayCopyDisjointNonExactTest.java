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
 * Tests {@link System#arraycopy} for object arrays where dest is a superclass of src.
 */
public class ObjArrayCopyDisjointNonExactTest extends GraalKernelTester {

    final static int MAXOUTSIZ = 100;
    final static int NUM = 20;

    @Result Object[][] outArray = new Object[NUM][MAXOUTSIZ];
    String[] inArray = new String[NUM + MAXOUTSIZ];

    @Override
    public void runTest() {
        for (int i = 0; i < inArray.length; i++) {
            inArray[i] = Integer.toString(i * 1111);
        }
        dispatchLambdaKernel(NUM, (gid) -> {
            System.arraycopy(inArray, gid, outArray[gid], 0, MAXOUTSIZ);
        });
    }

    // this fails because we do not have a pure java snippet for this case
    // see ArrayCopySnippets.arrayCopy(Object, int, Object, int, int)
    @Test(expected = com.oracle.graal.compiler.common.GraalInternalError.class)
    public void testUsingLambdaMethod() {
        testGeneratedHsailUsingLambdaMethod();
    }
}
