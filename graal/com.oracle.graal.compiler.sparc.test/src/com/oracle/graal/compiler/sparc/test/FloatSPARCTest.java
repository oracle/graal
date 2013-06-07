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
package com.oracle.graal.compiler.sparc.test;

import java.lang.reflect.Method;

import org.junit.Test;

public class FloatSPARCTest extends SPARCTestBase {

    @Test
    public void testAdd() {
        compile("testAdd2F");
        compile("testAdd2D");
        // compile("testAddFConst");
        // compile("testAddConstF");
        // compile("testAddDConst");
        // compile("testAddConstD");
    }

    public static float testAdd2F(float a, float b) {
        return a + b;
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
        compile("testSub2F");
        compile("testSub2D");
        // compile("testSubFConst");
        // compile("testSubConstF");
        // compile("testSubDConst");
        // compile("testSubConstD");
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

    @Test
    public void testMul() {
        compile("testMul2F");
        compile("testMul2D");
        // compile("testMulFConst");
        // compile("testMulConstF");
        // compile("testMulDConst");
        // compile("testMulConstD");
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

    @Test
    public void testDiv() {
        compile("testDiv2F");
        compile("testDiv2D");
        // compile("testDivFConst");
        // compile("testDivConstF");
        // compile("testDivDConst");
        // compile("testDivConstD");
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
        compile("testNeg2F");
        compile("testNeg2D");
    }

    public static float testNeg2F(float a) {
        return -a;
    }

    public static double testNeg2D(double a) {
        return -a;
    }

    @Test
    public void testRem() {
        // need linkage to PTX remainder()
        // compile("testRem2F");
        // compile("testRem2D");
    }

    public static float testRem2F(float a, float b) {
        return a % b;
    }

    public static double testRem2D(double a, double b) {
        return a % b;
    }

    @Test
    public void testFloatConversion() {
        // compile("testF2I");
        // compile("testF2L");
        // compile("testF2D");
        // compile("testD2I");
        // compile("testD2L");
        // compile("testD2F");
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
        FloatSPARCTest test = new FloatSPARCTest();
        for (Method m : FloatSPARCTest.class.getMethods()) {
            String name = m.getName();
            if (m.getAnnotation(Test.class) == null && name.startsWith("test") && name.startsWith("testRem") == false) {
                // CheckStyle: stop system..print check
                System.out.println(name + ": \n" + new String(test.compile(name).getTargetCode()));
                // CheckStyle: resume system..print check
            }
        }
    }
}
