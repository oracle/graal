/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2020, Arm Limited. All rights reserved.
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
package org.graalvm.compiler.jtt.lang;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.graalvm.compiler.jtt.JTTTest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class Math_minMax extends JTTTest {

    @Parameters(name = "{0}, {1}")
    public static Collection<Object[]> testData() {
        double[] inputs = {0.0d, -0.0d, 4.1d, -4.1d, Double.MIN_VALUE, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN};

        List<Object[]> testParameters = new ArrayList<>();
        for (double a : inputs) {
            for (double b : inputs) {
                testParameters.add(new Object[]{a, b});
            }
        }
        return testParameters;
    }

    @Parameter(value = 0) public double input0;
    @Parameter(value = 1) public double input1;

    public static double maxDouble(double x, double y) {
        return Math.max(x, y);
    }

    public static double minDouble(double x, double y) {
        return Math.min(x, y);
    }

    public static float maxFloat(float x, float y) {
        return Math.max(x, y);
    }

    public static float minFloat(float x, float y) {
        return Math.min(x, y);
    }

    @Test
    public void testMaxDouble() throws Throwable {
        runTest("maxDouble", input0, input1);
    }

    @Test
    public void testMinDouble() throws Throwable {
        runTest("minDouble", input0, input1);
    }

    @Test
    public void testMaxFloat() throws Throwable {
        runTest("maxFloat", (float) input0, (float) input1);
    }

    @Test
    public void testMinFloat() throws Throwable {
        runTest("minFloat", (float) input0, (float) input1);
    }

    public static double maxDoubleConst0(double x) {
        return Math.max(x, 0.0);
    }

    public static double minDoubleConst0(double x) {
        return Math.min(x, 0.0);
    }

    public static double maxDoubleConstM0(double x) {
        return Math.max(x, -0.0);
    }

    public static double minDoubleConstM0(double x) {
        return Math.min(x, -0.0);
    }

    public static double maxDoubleConstNaN(double x) {
        return Math.max(x, Double.NaN);
    }

    public static double minDoubleConstNaN(double x) {
        return Math.min(x, Double.NaN);
    }

    public static double maxDoubleConstNegInf(double x) {
        return Math.max(x, Double.NEGATIVE_INFINITY);
    }

    public static double minDoubleConstPosInf(double x) {
        return Math.min(x, Double.POSITIVE_INFINITY);
    }

    public static double maxDoubleConstMinValue(double x) {
        return Math.max(x, Double.MIN_VALUE);
    }

    public static double minDoubleConstMaxValue(double x) {
        return Math.min(x, Double.MAX_VALUE);
    }

    @Test
    public void testMaxDoubleConst() {
        if (input0 != input1) {
            // skip
            return;
        }
        runTest("maxDoubleConst0", input0);
        runTest("maxDoubleConstM0", input0);
        runTest("maxDoubleConstNaN", input0);
        runTest("maxDoubleConstNegInf", input0);
        runTest("maxDoubleConstMinValue", input0);
    }

    @Test
    public void testMinDoubleConst() {
        if (input0 != input1) {
            // skip
            return;
        }
        runTest("minDoubleConst0", input0);
        runTest("minDoubleConstM0", input0);
        runTest("minDoubleConstNaN", input0);
        runTest("minDoubleConstPosInf", input0);
        runTest("minDoubleConstMaxValue", input0);
    }
}
