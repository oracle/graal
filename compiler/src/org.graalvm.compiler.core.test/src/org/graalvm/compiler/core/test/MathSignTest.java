/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

public class MathSignTest extends GraalCompilerTest {

    private static final float[][] float_cases = {
                    {123.4f, 1.0f},
                    {-56.7f, -1.0f},
                    {7e30f, 1.0f},
                    {-0.3e30f, -1.0f},
                    {Float.MAX_VALUE, 1.0f},
                    {-Float.MAX_VALUE, -1.0f},
                    {Float.MIN_VALUE, 1.0f},
                    {-Float.MIN_VALUE, -1.0f},
                    {0.0f, 0.0f},
                    {-0.0f, -0.0f},
                    {Float.POSITIVE_INFINITY, 1.0f},
                    {Float.NEGATIVE_INFINITY, -1.0f},
                    {Float.NaN, Float.NaN},
                    {Float.MIN_NORMAL, 1.0f},
                    {-Float.MIN_NORMAL, -1.0f},
                    {0x0.0002P-126f, 1.0f},
                    {-0x0.0002P-126f, -1.0f}
    };

    private static final double[][] double_cases = {
                    {123.4d, 1.0d},
                    {-56.7d, -1.0d},
                    {7e30d, 1.0d},
                    {-0.3e30d, -1.0d},
                    {Double.MAX_VALUE, 1.0d},
                    {-Double.MAX_VALUE, -1.0d},
                    {Double.MIN_VALUE, 1.0d},
                    {-Double.MIN_VALUE, -1.0d},
                    {0.0d, 0.0d},
                    {-0.0d, -0.0d},
                    {Double.POSITIVE_INFINITY, 1.0d},
                    {Double.NEGATIVE_INFINITY, -1.0d},
                    {Double.NaN, Double.NaN},
                    {Double.MIN_NORMAL, 1.0d},
                    {-Double.MIN_NORMAL, -1.0d},
                    {0x0.00000001P-1022, 1.0d},
                    {-0x0.00000001P-1022, -1.0d}
    };

    public static float floatSignum(float f) {
        return Math.signum(f);
    }

    @Test
    public void testFloatSignum() throws InvalidInstalledCodeException {
        InstalledCode code = getCode(getResolvedJavaMethod("floatSignum"));
        for (float[] entry : float_cases) {
            float result = (float) code.executeVarargs(entry[0]);
            Assert.assertEquals(entry[1], result, 0);
        }
    }

    public static double doubleSignum(double d) {
        return Math.signum(d);
    }

    @Test
    public void testDoubleSignum() throws InvalidInstalledCodeException {
        InstalledCode code = getCode(getResolvedJavaMethod("doubleSignum"));
        for (double[] entry : double_cases) {
            double result = (double) code.executeVarargs(entry[0]);
            Assert.assertEquals(entry[1], result, 0);

        }
    }

}
