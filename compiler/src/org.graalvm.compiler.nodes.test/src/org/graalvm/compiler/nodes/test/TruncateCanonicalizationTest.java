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

package org.graalvm.compiler.nodes.test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.calc.RoundNode;
import org.junit.Assert;
import org.junit.Test;

public class TruncateCanonicalizationTest extends GraalCompilerTest {

    // See ExactMathTest#testTruncateFloat and ExactMathTest#testTruncateDouble for values tests.

    @Test
    public void testTruncateFloatCanonicalized1() {
        Assert.assertEquals(1, getFinalGraph("truncateFloat1").getNodes().filter(RoundNode.class).count());
    }

    public static float truncateFloat1(float a) {
        return (float) truncateDouble1(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized1() {
        Assert.assertEquals(1, getFinalGraph("truncateDouble1").getNodes().filter(RoundNode.class).count());
    }

    public static double truncateDouble1(double a) {
        // First canonical form
        return a < 0.0 ? Math.ceil(a) : Math.floor(a);
    }

    @Test
    public void testTruncateFloatCanonicalized2() {
        Assert.assertEquals(1, getFinalGraph("truncateFloat2").getNodes().filter(RoundNode.class).count());
    }

    public static float truncateFloat2(float a) {
        return (float) truncateDouble2(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized2() {
        Assert.assertEquals(1, getFinalGraph("truncateDouble2").getNodes().filter(RoundNode.class).count());
    }

    public static double truncateDouble2(double a) {
        // First canonical form, with condition operands swapped
        return 0.0 > a ? Math.ceil(a) : Math.floor(a);
    }

    @Test
    public void testTruncateFloatCanonicalized3() {
        Assert.assertEquals(1, getFinalGraph("truncateFloat3").getNodes().filter(RoundNode.class).count());
    }

    public static float truncateFloat3(float a) {
        return (float) truncateDouble3(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized3() {
        Assert.assertEquals(1, getFinalGraph("truncateDouble3").getNodes().filter(RoundNode.class).count());
    }

    public static double truncateDouble3(double a) {
        // Second canonical form
        return 0.0 < a ? Math.floor(a) : Math.ceil(a);
    }

    @Test
    public void testTruncateFloatCanonicalized4() {
        Assert.assertEquals(1, getFinalGraph("truncateFloat4").getNodes().filter(RoundNode.class).count());
    }

    public static float truncateFloat4(float a) {
        return (float) truncateDouble4(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized4() {
        Assert.assertEquals(1, getFinalGraph("truncateDouble4").getNodes().filter(RoundNode.class).count());
    }

    public static double truncateDouble4(double a) {
        // Second canonical form, with condition operands swapped
        return a > 0.0 ? Math.floor(a) : Math.ceil(a);
    }

    @Test
    public void testTruncateFloatCanonicalized5() {
        Assert.assertEquals(1, getFinalGraph("truncateFloat5").getNodes().filter(RoundNode.class).count());
    }

    public static float truncateFloat5(float a) {
        return (float) truncateDouble5(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized5() {
        Assert.assertEquals(1, getFinalGraph("truncateDouble5").getNodes().filter(RoundNode.class).count());
    }

    public static double truncateDouble5(double a) {
        // First canonical form, with inverse condition
        return a >= 0.0 ? Math.floor(a) : Math.ceil(a);
    }

    @Test
    public void testTruncateFloatCanonicalized6() {
        Assert.assertEquals(1, getFinalGraph("truncateFloat6").getNodes().filter(RoundNode.class).count());
    }

    public static float truncateFloat6(float a) {
        return (float) truncateDouble6(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized6() {
        Assert.assertEquals(1, getFinalGraph("truncateDouble6").getNodes().filter(RoundNode.class).count());
    }

    public static double truncateDouble6(double a) {
        // Second canonical form, with inverse condition
        return a <= 0.0 ? Math.ceil(a) : Math.floor(a);
    }

    @Test
    public void testTruncateFloatCanonicalized7() {
        Assert.assertEquals(1, getFinalGraph("truncateFloat7").getNodes().filter(RoundNode.class).count());
    }

    public static float truncateFloat7(float a) {
        return (float) truncateDouble7(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized7() {
        Assert.assertEquals(1, getFinalGraph("truncateDouble7").getNodes().filter(RoundNode.class).count());
    }

    public static double truncateDouble7(double a) {
        // First canonical form, with inverse condition and condition operands swapped
        return 0.0 <= a ? Math.floor(a) : Math.ceil(a);
    }

    @Test
    public void testTruncateFloatCanonicalized8() {
        Assert.assertEquals(1, getFinalGraph("truncateFloat8").getNodes().filter(RoundNode.class).count());
    }

    public static float truncateFloat8(float a) {
        return (float) truncateDouble8(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized8() {
        Assert.assertEquals(1, getFinalGraph("truncateDouble8").getNodes().filter(RoundNode.class).count());
    }

    public static double truncateDouble8(double a) {
        // Second canonical form, with inverse condition and condition operands swapped
        return 0.0 >= a ? Math.ceil(a) : Math.floor(a);
    }

    @Test
    public void testTruncateFloatCanonicalized9() {
        Assert.assertEquals(1, getFinalGraph("truncateFloat9").getNodes().filter(RoundNode.class).count());
    }

    public static float truncateFloat9(float a) {
        return (float) truncateDouble9(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized9() {
        Assert.assertEquals(1, getFinalGraph("truncateDouble9").getNodes().filter(RoundNode.class).count());
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
        Assert.assertEquals(1, getFinalGraph("truncateFloat10").getNodes().filter(RoundNode.class).count());
    }

    public static float truncateFloat10(float a) {
        return (float) truncateDouble10(a);
    }

    @Test
    public void testTruncateDoubleCanonicalized10() {
        Assert.assertEquals(1, getFinalGraph("truncateDouble10").getNodes().filter(RoundNode.class).count());
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

}
