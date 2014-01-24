/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.ptx.test;

import org.junit.*;

/* PTX ISA 3.1 - 8.7.3 Floating-Point Instructions */
public class FloatPTXTest extends PTXTest {

    @Test
    public void testAdd() {
        test("testAdd2I", 42, 43);
        test("testAdd2F", 42.1F, 43.5F);
        test("testAddFConst", 42.1F);
        test("testAdd2D", 42.1, 43.5);
    }

    public static float testAdd2I(int a, int b) {
        return a + b;
    }

    public static float testAdd2F(float a, float b) {
        return (a + b);
    }

    public static double testAdd2D(double a, double b) {
        return a + b;
    }

    public static float testAddFConst(float a) {
        return a + 32.0F;
    }

    public static float testAddConstF(float a) {
        return 32.0F + a;
    }

    public static double testAddDConst(double a) {
        return a + 32.0;
    }

    public static double testAddConstD(double a) {
        return 32.0 + a;
    }

    @Test
    public void testSub() {
        compileKernel("testSub2F");
        compileKernel("testSub2D");
        compileKernel("testSubFConst");
        compileKernel("testSubConstF");
        compileKernel("testSubDConst");
        compileKernel("testSubConstD");
    }

    public static float testSub2F(float a, float b) {
        return a - b;
    }

    public static double testSub2D(double a, double b) {
        return a - b;
    }

    public static float testSubFConst(float a) {
        return a - 32.0F;
    }

    public static float testSubConstF(float a) {
        return 32.0F - a;
    }

    public static double testSubDConst(double a) {
        return a - 32.0;
    }

    public static double testSubConstD(double a) {
        return 32.0 - a;
    }

    @Ignore("[CUDA] *** Error (209) Failed to load module data with online compiler options for method testMul2F")
    @Test
    public void testMul() {
        compileKernel("testMul2F");
        compileKernel("testMul2D");
        compileKernel("testMulFConst");
        compileKernel("testMulConstF");
        compileKernel("testMulDConst");
        compileKernel("testMulConstD");
    }

    public static float testMul2F(float a, float b) {
        return a * b;
    }

    public static double testMul2D(double a, double b) {
        return a * b;
    }

    public static float testMulFConst(float a) {
        return a * 32.0F;
    }

    public static float testMulConstF(float a) {
        return 32.0F * a;
    }

    public static double testMulDConst(double a) {
        return a * 32.0;
    }

    public static double testMulConstD(double a) {
        return 32.0 * a;
    }

    @Ignore("[CUDA] *** Error (209) Failed to load module data with online compiler options for method testDiv2F")
    @Test
    public void testDiv() {
        compileKernel("testDiv2F");
        compileKernel("testDiv2D");
        compileKernel("testDivFConst");
        compileKernel("testDivConstF");
        compileKernel("testDivDConst");
        compileKernel("testDivConstD");
    }

    public static float testDiv2F(float a, float b) {
        return a / b;
    }

    public static double testDiv2D(double a, double b) {
        return a / b;
    }

    public static float testDivFConst(float a) {
        return a / 32.0F;
    }

    public static float testDivConstF(float a) {
        return 32.0F / a;
    }

    public static double testDivDConst(double a) {
        return a / 32.0;
    }

    public static double testDivConstD(double a) {
        return 32.0 / a;
    }

    @Test
    public void testNeg() {
        compileKernel("testNeg2F");
        compileKernel("testNeg2D");
    }

    public static float testNeg2F(float a) {
        return -a;
    }

    public static double testNeg2D(double a) {
        return -a;
    }

    @Ignore("need linkage to PTX remainder")
    @Test
    public void testRem() {
        compileKernel("testRem2F");
        compileKernel("testRem2D");
    }

    public static float testRem2F(float a, float b) {
        return a % b;
    }

    public static double testRem2D(double a, double b) {
        return a % b;
    }

    @Ignore("[CUDA] *** Error (209) Failed to load module data with online compiler options for method testF2I")
    @Test
    public void testFloatConversion() {
        compileKernel("testF2I");
        compileKernel("testF2L");
        compileKernel("testF2D");
        compileKernel("testD2I");
        compileKernel("testD2L");
        compileKernel("testD2F");
    }

    public static int testF2I(float a) {
        return (int) a;
    }

    public static long testF2L(float a) {
        return (long) a;
    }

    public static double testF2D(float a) {
        return a;
    }

    public static int testD2I(double a) {
        return (int) a;
    }

    public static long testD2L(double a) {
        return (long) a;
    }

    public static float testD2F(double a) {
        return (float) a;
    }

    public static void main(String[] args) {
        compileAndPrintCode(new FloatPTXTest());
    }
}
