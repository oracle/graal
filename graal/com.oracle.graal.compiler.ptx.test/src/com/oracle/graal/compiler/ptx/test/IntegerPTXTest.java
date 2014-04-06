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

import java.io.*;
import java.lang.reflect.*;

import org.junit.*;

public class IntegerPTXTest extends PTXTest {

    @Test
    public void testAdd() {
        test("testAdd2B", (byte) 6, (byte) 4);
        test("testAdd2I", 18, 24);
        test("testAdd2L", (long) 12, (long) 6);
        test("testAddIConst", 5);
        test("testAddConstI", 7);
    }

    public static int testAdd2I(int a, int b) {
        return a + b;
    }

    public static long testAdd2L(long a, long b) {
        return a + b;
    }

    public static int testAdd2B(byte a, byte b) {
        return a + b;
    }

    public static int testAddIConst(int a) {
        return a + 32;
    }

    public static int testAddConstI(int a) {
        return 32 + a;
    }

    @Test
    public void testSub() {
        test("testSub2I", 18, 4);
        test("testSub2L", (long) 12, (long) 6);
        test("testSubIConst", 35);
        test("testSubConstI", 12);
    }

    public static int testSub2I(int a, int b) {
        return a - b;
    }

    public static long testSub2L(long a, long b) {
        return a - b;
    }

    public static int testSubIConst(int a) {
        return a - 32;
    }

    public static int testSubConstI(int a) {
        return 32 - a;
    }

    @Test
    public void testMul() {
        test("testMul2I", 8, 4);
        test("testMul2L", (long) 12, (long) 6);
        test("testMulIConst", 4);
        test("testMulConstI", 5);
    }

    public static int testMul2I(int a, int b) {
        return a * b;
    }

    public static long testMul2L(long a, long b) {
        return a * b;
    }

    public static int testMulIConst(int a) {
        return a * 32;
    }

    public static int testMulConstI(int a) {
        return 32 * a;
    }

    @Test
    public void testDiv() {
        test("testDiv2I", 8, 4);
        test("testDiv2L", (long) 12, (long) 6);
        test("testDivIConst", 64);
        test("testDivConstI", 8);
    }

    public static int testDiv2I(int a, int b) {
        return a / b;
    }

    public static long testDiv2L(long a, long b) {
        return a / b;
    }

    public static int testDivIConst(int a) {
        return a / 32;
    }

    public static int testDivConstI(int a) {
        return 32 / a;
    }

    @Test
    public void testRem() {
        test("testRem2I", 8, 4);
        test("testRem2L", (long) 12, (long) 6);
    }

    public static int testRem2I(int a, int b) {
        return a % b;
    }

    public static long testRem2L(long a, long b) {
        return a % b;
    }

    @Ignore
    @Test
    public void testIntConversion() {
        test("testI2L", 8);
        test("testL2I", (long) 12);
        // test("testI2C", 65);
        // test("testI2B", 9);
        // test("testI2F", 17);
        // test("testI2D", 22);
    }

    public static long testI2L(int a) {
        return a;
    }

    public static char testI2C(int a) {
        return (char) a;
    }

    public static byte testI2B(int a) {
        return (byte) a;
    }

    public static float testI2F(int a) {
        return a;
    }

    public static double testI2D(int a) {
        return a;
    }

    public static int testL2I(long a) {
        return (int) a;
    }

    public static void main(String[] args) {
        IntegerPTXTest test = new IntegerPTXTest();
        for (Method m : IntegerPTXTest.class.getMethods()) {
            String name = m.getName();
            if (m.getAnnotation(Test.class) == null && name.startsWith("test")) {
                PrintStream out = System.out;
                out.println(name + ": \n" + new String(test.compileKernel(name).getTargetCode()));
            }
        }
    }
}
