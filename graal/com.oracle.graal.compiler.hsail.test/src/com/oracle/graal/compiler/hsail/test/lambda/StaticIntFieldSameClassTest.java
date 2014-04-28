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

import org.junit.Test;

/**
 * Tests reading from a two static int fields in the same class.
 */
public class StaticIntFieldSameClassTest extends StaticIntFieldReadTest {

    static int myField1 = 5;
    static int myField2 = -99;
    @Result int fieldResult;

    @Override
    public void runTest() {
        setupArrays();
        myField2 = -99;
        dispatchLambdaKernel(NUM, (gid) -> {
            int val = inArray[gid] * myField1;
            outArray[gid] = val;
            if (gid == 3)
                myField2 = val + gid;
        });
        fieldResult = myField2;
    }

    @Override
    @Test
    public void test() {
        testGeneratedHsail();
    }

    @Override
    @Test
    public void testUsingLambdaMethod() {
        testGeneratedHsailUsingLambdaMethod();
    }

}
