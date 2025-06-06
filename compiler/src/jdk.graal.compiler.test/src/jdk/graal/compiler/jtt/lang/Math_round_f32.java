/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import jdk.graal.compiler.jtt.JTTTest;

/**
 * Tests {@link Math#floor}, {@link Math#ceil}, {@link Math#rint} with {@code float} arguments.
 *
 * @see Math_round
 */
@RunWith(Parameterized.class)
public class Math_round_f32 extends JTTTest {

    @Parameter(value = 0) public float input;

    public static float rintFF(float arg) {
        return (float) Math.rint(arg);
    }

    public static double rintFD(float arg) {
        return Math.rint(arg);
    }

    public static float floorFF(float arg) {
        return (float) Math.floor(arg);
    }

    public static double floorFD(float arg) {
        return Math.floor(arg);
    }

    public static float ceilFF(float arg) {
        return (float) Math.ceil(arg);
    }

    public static double ceilFD(float arg) {
        return Math.ceil(arg);
    }

    @Test
    public void runRint() throws Throwable {
        runTest("rintFF", input);
        runTest("rintFD", input);
    }

    @Test
    public void runFloor() throws Throwable {
        runTest("floorFF", input);
        runTest("floorFD", input);
    }

    @Test
    public void runCeil() throws Throwable {
        runTest("ceilFF", input);
        runTest("ceilFD", input);
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        ArrayList<Object[]> tests = new ArrayList<>();
        for (int i = -3; i < 3; i++) {
            addTest(tests, i);
            addTest(tests, i + 0.2f);
            addTest(tests, i + 0.5f);
            addTest(tests, i + 0.7f);
        }
        addTest(tests, -0.0f);
        for (float sign : new float[]{-1f, 1f}) {
            // largest non-integral value representable in float
            float largestNonIntegralValue = Float.intBitsToFloat(0b0_10010101_1111111_11111111_11111111);
            assert largestNonIntegralValue == 8388607.5f : largestNonIntegralValue;
            addTest(tests, Math.copySign(largestNonIntegralValue, sign));
            addTest(tests, Math.copySign(Math.nextDown(largestNonIntegralValue), sign));
            addTest(tests, Math.copySign(Math.nextUp(largestNonIntegralValue), sign));

            addTest(tests, Math.copySign(Float.MIN_VALUE, sign));
            addTest(tests, Math.copySign(Float.MAX_VALUE, sign));
        }
        addTest(tests, Float.NEGATIVE_INFINITY);
        addTest(tests, Float.POSITIVE_INFINITY);
        addTest(tests, Float.NaN);
        addTest(tests, Float.intBitsToFloat(0xffc00000)); // negative quiet NaN
        addTest(tests, Float.intBitsToFloat(0x7f800001)); // positive signaling NaN
        addTest(tests, Float.intBitsToFloat(0xffbfffff)); // negative signaling NaN
        return tests;
    }

    private static void addTest(ArrayList<Object[]> tests, float input) {
        tests.add(new Object[]{input});
    }
}
