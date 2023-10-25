/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.truffle.test;

import jdk.graal.compiler.nodes.calc.RoundNode;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.graal.compiler.truffle.substitutions.TruffleGraphBuilderPlugins;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.ExactMath;

import jdk.vm.ci.amd64.AMD64;

public class ExactMathTest extends TruffleCompilerImplTest {

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        TruffleGraphBuilderPlugins.registerExactMathPlugins(invocationPlugins, getTypes(), getReplacements(), getLowerer());
        super.registerInvocationPlugins(invocationPlugins);
    }

    @Test
    public void testAdd() {
        test("add", 1, 2);
        test("add", Integer.MAX_VALUE, 2);
        test("add", Integer.MIN_VALUE, -1);
        test("add", -1, 2);
    }

    @Test
    public void testMul() {
        test("mul", 1, 2);
        test("mul", -1, 2);
        test("mul", Integer.MIN_VALUE, 1);
        test("mul", Integer.MIN_VALUE, 2);
        test("mul", Integer.MIN_VALUE, Integer.MIN_VALUE);
        test("mul", Integer.MAX_VALUE, 1);
        test("mul", Integer.MAX_VALUE, 2);
        test("mul", Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    @Test
    public void testSub() {
        test("sub", 1, 2);
        test("sub", Integer.MIN_VALUE, 2);
    }

    @Test
    public void testNeg() {
        test("neg", 1);
        test("neg", Integer.MIN_VALUE);
    }

    @Test
    public void testMulHigh() {
        test("mulHigh", 7, 15);
        test("mulHigh", Integer.MAX_VALUE, 15);
        test("mulHigh", Integer.MIN_VALUE, 15);
    }

    @Test
    public void testMulHighUnsigned() {
        test("mulHighUnsigned", 7, 15);
        test("mulHighUnsigned", Integer.MAX_VALUE, 15);
        test("mulHighUnsigned", 15, Integer.MAX_VALUE);
        test("mulHighUnsigned", Integer.MAX_VALUE, Integer.MAX_VALUE);
        test("mulHighUnsigned", 15, Integer.MIN_VALUE);
        test("mulHighUnsigned", Integer.MIN_VALUE, 15);
        test("mulHighUnsigned", Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    @Test
    public void testLongAdd() {
        test("longAdd", (long) Integer.MAX_VALUE, 2L);
        test("longAdd", Long.MAX_VALUE, 2L);
    }

    @Test
    public void testLongMul() {
        test("longMul", (long) Integer.MAX_VALUE, 2L);
        test("longMul", (long) Integer.MIN_VALUE, 2L);
        test("longMul", Long.MAX_VALUE, 2L);
        test("longMul", Long.MAX_VALUE, 1L);
        test("longMul", Long.MAX_VALUE, Long.MAX_VALUE);
        test("longMul", Long.MIN_VALUE, Long.MIN_VALUE);
        test("longMul", Long.MIN_VALUE, Long.MAX_VALUE);
    }

    @Test
    public void testLongSub() {
        test("longSub", (long) Integer.MIN_VALUE, 2L);
        test("longSub", Long.MIN_VALUE, 2L);
    }

    @Test
    public void testLongNeg() {
        test("longNeg", (long) Integer.MIN_VALUE);
        test("longNeg", Long.MIN_VALUE);
    }

    @Test
    public void testLongMulHigh() {
        test("longMulHigh", 7L, 15L);
        test("longMulHigh", Long.MAX_VALUE, 15L);
        test("longMulHigh", 15L, Long.MAX_VALUE);
        test("longMulHigh", Long.MAX_VALUE, Long.MAX_VALUE);
        test("longMulHigh", Long.MIN_VALUE, 15L);
        test("longMulHigh", 15L, Long.MIN_VALUE);
        test("longMulHigh", Long.MIN_VALUE, Long.MIN_VALUE);
        test("longMulHigh1", Long.MIN_VALUE);
        test("longMulHighLeftAssociative", -1L, 1L, 1L);
        test("longMulHighRightAssociative", -1L, 1L, 1L);
    }

    @Test
    public void testLongMulHighUnsigned() {
        test("longMulHighUnsigned", 7L, 15L);
        test("longMulHighUnsigned", Long.MAX_VALUE, 15L);
        test("longMulHighUnsigned", Long.MIN_VALUE, 15L);
        test("longMulHighUnsigned1", Long.MIN_VALUE);
        test("longMulHighUnsignedLeftAssociative", -1L, Long.MAX_VALUE, 4L);
        test("longMulHighUnsignedRightAssociative", -1L, Long.MAX_VALUE, 4L);
    }

    @Test
    public void testTruncateFloat() {
        test("truncateFloat", Float.NEGATIVE_INFINITY);
        test("truncateFloat", -1.5f);
        test("truncateFloat", -1.0f);
        test("truncateFloat", -0.5f);
        test("truncateFloat", -0.0f);
        test("truncateFloat", Float.MIN_VALUE);
        test("truncateFloat", Float.MIN_NORMAL);
        test("truncateFloat", 0.0f);
        test("truncateFloat", 0.5f);
        test("truncateFloat", 1.0f);
        test("truncateFloat", 1.5f);
        test("truncateFloat", Float.MAX_VALUE);
        test("truncateFloat", Float.POSITIVE_INFINITY);
        test("truncateFloat", Float.NaN);
    }

    @Test
    public void testTruncateFloatIntrinsified() {
        Assume.assumeTrue(isArchitecture("aarch64") || (isArchitecture("AMD64") && ((AMD64) getArchitecture()).getFeatures().contains(AMD64.CPUFeature.SSE4_1)));
        Assert.assertEquals(1, getFinalGraph("truncateFloat").getNodes().filter(RoundNode.class).count());
    }

    @Test
    public void testTruncateDouble() {
        test("truncateDouble", Double.NEGATIVE_INFINITY);
        test("truncateDouble", -1.5);
        test("truncateDouble", -1.0);
        test("truncateDouble", -0.5);
        test("truncateDouble", -0.0);
        test("truncateDouble", Double.MIN_VALUE);
        test("truncateDouble", Double.MIN_NORMAL);
        test("truncateDouble", 0.0);
        test("truncateDouble", 0.5);
        test("truncateDouble", 1.0);
        test("truncateDouble", 1.5);
        test("truncateDouble", Double.MAX_VALUE);
        test("truncateDouble", Double.POSITIVE_INFINITY);
        test("truncateDouble", Double.NaN);
    }

    @Test
    public void testTruncateDoubleIntrinsified() {
        Assume.assumeTrue(isArchitecture("aarch64") || (isArchitecture("AMD64") && ((AMD64) getArchitecture()).getFeatures().contains(AMD64.CPUFeature.SSE4_1)));
        Assert.assertEquals(1, getFinalGraph("truncateFloat").getNodes().filter(RoundNode.class).count());
    }

    public static int add(int a, int b) {
        return Math.addExact(a, b);
    }

    public static int mul(int a, int b) {
        return Math.multiplyExact(a, b);
    }

    public static int sub(int a, int b) {
        return Math.subtractExact(a, b);
    }

    public static int neg(int a) {
        return Math.negateExact(a);
    }

    public static int mulHigh(int a, int b) {
        return ExactMath.multiplyHigh(a, b);
    }

    public static int mulHighUnsigned(int a, int b) {
        return ExactMath.multiplyHighUnsigned(a, b);
    }

    public static long longAdd(long a, long b) {
        return Math.addExact(a, b);
    }

    public static long longMul(long a, long b) {
        return Math.multiplyExact(a, b);
    }

    public static long longSub(long a, long b) {
        return Math.subtractExact(a, b);
    }

    public static long longNeg(long a) {
        return Math.negateExact(a);
    }

    public static long longMulHigh(long a, long b) {
        return ExactMath.multiplyHigh(a, b);
    }

    public static long longMulHigh1(long a) {
        return ExactMath.multiplyHigh(a, 1L);
    }

    public static long longMulHighLeftAssociative(long a, long b, long c) {
        return ExactMath.multiplyHigh(ExactMath.multiplyHigh(a, b), c);
    }

    public static long longMulHighRightAssociative(long a, long b, long c) {
        return ExactMath.multiplyHigh(a, ExactMath.multiplyHigh(b, c));
    }

    public static long longMulHighUnsigned(long a, long b) {
        return ExactMath.multiplyHighUnsigned(a, b);
    }

    public static long longMulHighUnsigned1(long a) {
        return ExactMath.multiplyHighUnsigned(a, 1L);
    }

    public static long longMulHighUnsignedLeftAssociative(long a, long b, long c) {
        return ExactMath.multiplyHighUnsigned(ExactMath.multiplyHighUnsigned(a, b), c);
    }

    public static long longMulHighUnsignedRightAssociative(long a, long b, long c) {
        return ExactMath.multiplyHighUnsigned(a, ExactMath.multiplyHighUnsigned(b, c));
    }

    public static float truncateFloat(float a) {
        return ExactMath.truncate(a);
    }

    public static double truncateDouble(double a) {
        return ExactMath.truncate(a);
    }
}
