/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.core.test;

import org.graalvm.compiler.nodes.calc.NegateNode;
import org.junit.Test;

public class MulNegateTest extends GraalCompilerTest {

    public static final int[] INT_TEST_CASES = {0, 1, -1, Integer.MIN_VALUE, Integer.MAX_VALUE};

    public static int mulInt(int x, int y) {
        return -x * -y;
    }

    @Test
    public void testInt() {
        assertTrue(getFinalGraph("mulInt").getNodes().filter(NegateNode.class).count() == 0);

        for (int i : INT_TEST_CASES) {
            for (int j : INT_TEST_CASES) {
                test("mulInt", i, j);
            }
        }
    }

    public static final float[] FLOAT_TEST_CASES = {0.0f, -0.0f, 1.0f, -1.0f, Float.MIN_VALUE, Float.MIN_NORMAL,
                    Float.MAX_VALUE, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NaN};

    public static float mulFlt(float x, float y) {
        return -x * -y;
    }

    @Test
    public void testFloat() {
        assertTrue(getFinalGraph("mulFlt").getNodes().filter(NegateNode.class).count() == 0);

        for (float i : FLOAT_TEST_CASES) {
            for (float j : FLOAT_TEST_CASES) {
                test("mulFlt", i, j);
            }
        }
    }
}
