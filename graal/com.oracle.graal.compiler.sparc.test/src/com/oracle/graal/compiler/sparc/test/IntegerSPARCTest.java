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

import org.junit.Ignore;
import org.junit.Test;

import java.lang.reflect.Method;

public class IntegerSPARCTest extends SPARCTestBase {

    @Test
    public void testAdd() {
        compile("testAdd2I");
        compile("testAdd2L");
        compile("testAdd2B");
        compile("testAddIConst");
        compile("testAddConstI");
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
        compile("testSub2I");
        compile("testSub2L");
        compile("testSubIConst");
        compile("testSubConstI");
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
        compile("testMul2I");
        compile("testMul2L");
        compile("testMulIConst");
        compile("testMulConstI");
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
        compile("testDiv2I");
        compile("testDiv2L");
        compile("testDivIConst");
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

    @Ignore
    public void testRem() {
        compile("testRem2I");
        compile("testRem2L");
    }

    public static int testRem2I(int a, int b) {
        return a % b;
    }

    public static long testRem2L(long a, long b) {
        return a % b;
    }

    @Test
    public void testIntConversion() {
        compile("testI2L");
        compile("testI2C");
        compile("testI2B");
        compile("testI2F");
        compile("testI2D");
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

    public static void main(String[] args) {
        IntegerSPARCTest test = new IntegerSPARCTest();
        for (Method m : IntegerSPARCTest.class.getMethods()) {
            String name = m.getName();
            if (m.getAnnotation(Test.class) == null && name.startsWith("test")) {
                // CheckStyle: stop system..print check
                System.out.println(name + ": \n" + new String(test.compile(name).getTargetCode()));
                // CheckStyle: resume system..print check
            }
        }
    }
}
