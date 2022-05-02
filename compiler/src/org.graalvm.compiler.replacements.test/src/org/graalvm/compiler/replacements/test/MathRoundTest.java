/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.junit.Test;

public class MathRoundTest extends GraalCompilerTest {

    private static final float[] FLOAT_INPUT = {1, -0.0f, -1.0f, 1.0f, -0.5f, 0.5f,
                    Integer.MAX_VALUE, Integer.MAX_VALUE - 0.5f, Integer.MAX_VALUE + 0.5f,
                    Integer.MIN_VALUE, Integer.MIN_VALUE - 0.5f, Integer.MIN_VALUE + 0.5f,
                    Float.MIN_VALUE, -Float.MIN_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE,
                    Float.MIN_NORMAL, -Float.MIN_NORMAL, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY,
                    Float.NaN, Float.intBitsToFloat(0x7fffffff)};

    public static int roundFloat(float f) {
        return Math.round(f);
    }

    @Test
    public void testRoundFloat() {
        for (float input : FLOAT_INPUT) {
            test("roundFloat", input);
        }
    }

    private static final double[] DOUBLE_INPUT = {0, -0.0d, -1.0d, 1.0d, -0.5d, 0.5d,
                    Long.MAX_VALUE, Long.MAX_VALUE - 0.5d, Long.MAX_VALUE + 0.5d,
                    Long.MIN_VALUE, Long.MIN_VALUE - 0.5d, Long.MIN_VALUE + 0.5d,
                    Double.MIN_VALUE, -Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE,
                    Double.MIN_NORMAL, -Double.MIN_NORMAL, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY,
                    Double.NaN, Double.longBitsToDouble(0x7fffffff_ffffffffL)};

    public static long roundDouble(double d) {
        return Math.round(d);
    }

    @Test
    public void testRoundDouble() {
        for (double input : DOUBLE_INPUT) {
            test("roundDouble", input);
        }
    }
}
