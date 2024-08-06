/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited. All rights reserved.
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

package jdk.graal.compiler.nodes.test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.calc.RoundNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TruncateCanonicalizationTest extends GraalCompilerTest {

    // See ExactMathTest#testTruncateFloat and ExactMathTest#testTruncateDouble for values tests.

    @Before
    public void before() {
        // Resolve these methods, otherwise we can run into Deopt Unresolved nodes in the graphs
        // when running tests in isolation.
        getResolvedJavaMethod(Math.class, "ceil");
        getResolvedJavaMethod(Math.class, "floor");
    }

    public void testRoundNodes(String snippetName, int expected) {
        Assert.assertEquals(expected, getFinalGraph(snippetName).getNodes().filter(RoundNode.class).count());
    }

    @Test
    public void testTruncateFloatCanonicalized1() {
        testRoundNodes("truncateFloat1", 1);
    }

    public static float truncateFloat1(float a) {
        return (float) truncateDouble1(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized1() {
        testRoundNodes("truncateDouble1", 1);
    }

    public static double truncateDouble1(double a) {
        // First canonical form
        return a < 0.0 ? Math.ceil(a) : Math.floor(a);
    }

    @Test
    public void testTruncateFloatCanonicalized2() {
        testRoundNodes("truncateFloat2", 1);
    }

    public static float truncateFloat2(float a) {
        return (float) truncateDouble2(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized2() {
        testRoundNodes("truncateDouble2", 1);
    }

    public static double truncateDouble2(double a) {
        // First canonical form, with condition operands swapped
        return 0.0 > a ? Math.ceil(a) : Math.floor(a);
    }

    @Test
    public void testTruncateFloatCanonicalized3() {
        testRoundNodes("truncateFloat3", 1);
    }

    public static float truncateFloat3(float a) {
        return (float) truncateDouble3(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized3() {
        testRoundNodes("truncateDouble3", 1);
    }

    public static double truncateDouble3(double a) {
        // Second canonical form
        return 0.0 < a ? Math.floor(a) : Math.ceil(a);
    }

    @Test
    public void testTruncateFloatCanonicalized4() {
        testRoundNodes("truncateFloat4", 1);
    }

    public static float truncateFloat4(float a) {
        return (float) truncateDouble4(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized4() {
        testRoundNodes("truncateDouble4", 1);
    }

    public static double truncateDouble4(double a) {
        // Second canonical form, with condition operands swapped
        return a > 0.0 ? Math.floor(a) : Math.ceil(a);
    }

    @Test
    public void testTruncateFloatCanonicalized5() {
        testRoundNodes("truncateFloat5", 1);
    }

    public static float truncateFloat5(float a) {
        return (float) truncateDouble5(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized5() {
        testRoundNodes("truncateDouble5", 1);
    }

    public static double truncateDouble5(double a) {
        // First canonical form, with inverse condition
        return a >= 0.0 ? Math.floor(a) : Math.ceil(a);
    }

    @Test
    public void testTruncateFloatCanonicalized6() {
        testRoundNodes("truncateFloat6", 1);
    }

    public static float truncateFloat6(float a) {
        return (float) truncateDouble6(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized6() {
        testRoundNodes("truncateDouble6", 1);
    }

    public static double truncateDouble6(double a) {
        // Second canonical form, with inverse condition
        return a <= 0.0 ? Math.ceil(a) : Math.floor(a);
    }

    @Test
    public void testTruncateFloatCanonicalized7() {
        testRoundNodes("truncateFloat7", 1);
    }

    public static float truncateFloat7(float a) {
        return (float) truncateDouble7(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized7() {
        testRoundNodes("truncateDouble7", 1);
    }

    public static double truncateDouble7(double a) {
        // First canonical form, with inverse condition and condition operands swapped
        return 0.0 <= a ? Math.floor(a) : Math.ceil(a);
    }

    @Test
    public void testTruncateFloatCanonicalized8() {
        testRoundNodes("truncateFloat8", 1);
    }

    public static float truncateFloat8(float a) {
        return (float) truncateDouble8(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized8() {
        testRoundNodes("truncateDouble8", 1);
    }

    public static double truncateDouble8(double a) {
        // Second canonical form, with inverse condition and condition operands swapped
        return 0.0 >= a ? Math.ceil(a) : Math.floor(a);
    }

    @Test
    public void testTruncateFloatCanonicalized9() {
        testRoundNodes("truncateFloat9", 1);
    }

    public static float truncateFloat9(float a) {
        return (float) truncateDouble9(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized9() {
        testRoundNodes("truncateDouble9", 1);
    }

    public static double truncateDouble9(double a) {
        // First canonical form, diamond
        double result;
        if (a < 0.0) {
            result = Math.ceil(a);
        } else {
            result = Math.floor(a);
        }
        return result;
    }

    @Test
    public void testTruncateFloatCanonicalized10() {
        testRoundNodes("truncateFloat10", 1);
    }

    public static float truncateFloat10(float a) {
        return (float) truncateDouble10(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized10() {
        testRoundNodes("truncateDouble10", 1);
    }

    public static double truncateDouble10(double a) {
        // Second canonical form, diamond
        double result;
        if (0.0 < a) {
            result = Math.floor(a);
        } else {
            result = Math.ceil(a);
        }
        return result;
    }

    @Test
    public void testTruncateFloatCanonicalized11() {
        testRoundNodes("truncateFloat11", 1);
    }

    public static double truncateFloat11(float a) {
        // Unlike most other float tests, this does the comparison as float, not as double. The
        // float is still converted to double for rounding.
        return 0.0f > a ? Math.ceil(a) : Math.floor(a);
    }

    @Test
    public void testTruncateFloatCanonicalized12() {
        testRoundNodes("truncateFloat12", 1);
    }

    public static double truncateFloat12(float a) {
        // Another test with the compare as float, even though the constant is formally a double.
        return 0.0 > a ? Math.ceil(a) : Math.floor(a);
    }

    @Test
    public void testDoNotCanonicalizeFloat1() {
        testRoundNodes("doNotCanonicalizeFloat1", 2);
    }

    public static float doNotCanonicalizeFloat1(float a, float b) {
        return (float) doNotCanonicalizeDouble1(a, b);
    }

    @Test
    public void testDoNotCanonicalizeDouble1() {
        testRoundNodes("doNotCanonicalizeDouble1", 2);
    }

    public static double doNotCanonicalizeDouble1(double a, double b) {
        return a < 0.0 ? Math.ceil(b) : Math.floor(b);
    }

    @Test
    public void testDoNotCanonicalizeFloat2() {
        testRoundNodes("doNotCanonicalizeFloat2", 2);
    }

    public static float doNotCanonicalizeFloat2(float a, float b) {
        return (float) doNotCanonicalizeDouble2(a, b);
    }

    @Test
    public void testDoNotCanonicalizeDouble2() {
        testRoundNodes("doNotCanonicalizeDouble2", 2);
    }

    public static double doNotCanonicalizeDouble2(double a, double b) {
        return b < 0.0 ? Math.ceil(a) : Math.floor(b);
    }

    @Test
    public void testDoNotCanonicalizeFloat3() {
        testRoundNodes("doNotCanonicalizeFloat3", 2);
    }

    public static float doNotCanonicalizeFloat3(float a, float b) {
        return (float) doNotCanonicalizeDouble3(a, b);
    }

    @Test
    public void testDoNotCanonicalizeDouble3() {
        testRoundNodes("doNotCanonicalizeDouble3", 2);
    }

    public static double doNotCanonicalizeDouble3(double a, double b) {
        return b < 0.0 ? Math.ceil(b) : Math.floor(a);
    }
}
