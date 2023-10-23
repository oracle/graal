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
package jdk.graal.compiler.jtt.lang;

import jdk.graal.compiler.jtt.JTTTest;
import org.junit.Test;

public class Math_signum extends JTTTest {

    private static final float[] floatValues = {
                    123.4f,
                    -56.7f,
                    7e30f,
                    -0.3e30f,
                    Float.MAX_VALUE,
                    -Float.MAX_VALUE,
                    Float.MIN_VALUE,
                    -Float.MIN_VALUE,
                    0.0f,
                    -0.0f,
                    Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY,
                    Float.NaN,
                    Float.MIN_NORMAL,
                    -Float.MIN_NORMAL,
                    0x0.0002P-126f,
                    -0x0.0002P-126f
    };

    private static final double[] doubleValues = {
                    123.4d,
                    -56.7d,
                    7e30d,
                    -0.3e30d,
                    Double.MAX_VALUE,
                    -Double.MAX_VALUE,
                    Double.MIN_VALUE,
                    -Double.MIN_VALUE,
                    0.0d,
                    -0.0d,
                    Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY,
                    Double.NaN,
                    Double.MIN_NORMAL,
                    -Double.MIN_NORMAL,
                    0x0.00000001P-1022,
                    -0x0.00000001P-1022,
    };

    public static float floatSignum(float f) {
        return Math.signum(f);
    }

    @Test
    public void testFloatSignum() {
        for (float f : floatValues) {
            runTest("floatSignum", f);
        }
    }

    public static double doubleSignum(double d) {
        return Math.signum(d);
    }

    @Test
    public void testDoubleSignum() {
        for (double d : doubleValues) {
            runTest("doubleSignum", d);
        }
    }
}
