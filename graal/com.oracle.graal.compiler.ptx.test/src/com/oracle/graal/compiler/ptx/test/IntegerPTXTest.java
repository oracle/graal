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

import java.lang.reflect.Method;

public class IntegerPTXTest extends PTXTestBase {

    @Test
    public void testAdd() {

        /* Integer r4 = (Integer) invoke(compile("testAdd2B"), (byte) 6, (byte) 4);
        if (r4 == null) {
            printReport("testAdd2B FAILED");
        } else if (r4.intValue() == testAdd2B((byte) 6, (byte) 4)) {
            printReport("testAdd2B PASSED");
        } else {
            printReport("testAdd2B FAILED");
        } */

        Integer r4 = (Integer) invoke(compile("testAdd2I"), 18, 24);
        if (r4 == null) {
            printReport("testAdd2I FAILED");
        } else if (r4.intValue() == testAdd2I(18, 24)) {
            printReport("testAdd2I PASSED");
        } else {
            printReport("testAdd2I FAILED");
        }

        Long r2 = (Long) invoke(compile("testAdd2L"), (long) 12, (long) 6);
        if (r2 == null) {
            printReport("testAdd2L FAILED");
        } else if (r2.longValue() == testAdd2L(12, 6)) {
            printReport("testAdd2L PASSED");
        } else {
            printReport("testAdd2L FAILED");
        }

        r4 = (Integer) invoke(compile("testAddIConst"), 5);
        if (r4 == null) {
            printReport("testAddIConst FAILED");
        } else if (r4.intValue() == testAddIConst(5)) {
            printReport("testAddIConst PASSED");
        } else {
            printReport("testAddIConst FAILED");
        }

        r4 = (Integer) invoke(compile("testAddConstI"), 7);
        if (r4 == null) {
            printReport("testAddConstI FAILED");
        } else if (r4.intValue() == testAddConstI(7)) {
            printReport("testAddConstI PASSED");
        } else {
            printReport("testAddConstI FAILED");
        }
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

        Integer r1 = (Integer) invoke(compile("testSub2I"), 18, 4);

        if (r1 == null) {
            printReport("testSub2I FAILED");
        } else if (r1.intValue() == testSub2I(18, 4)) {
            printReport("testSub2I PASSED");
        } else {
            printReport("testSub2I FAILED");
        }

        Long r2 = (Long) invoke(compile("testSub2L"), (long) 12, (long) 6);
        if (r2 == null) {
            printReport("testSub2I FAILED (null return value)");
        } else if (r2.longValue() == testSub2L(12, 6)) {
            printReport("testSub2I PASSED");
        } else {
            printReport("testSub2I FAILED");
        }

        r1 = (Integer) invoke(compile("testSubIConst"), 35);
        if (r1 == null) {
            printReport("testSubIConst FAILED");
        } else if (r1.intValue() == testSubIConst(35)) {
            printReport("testSubIConst PASSED");
        } else {
            printReport("testSubIConst FAILED");
        }

        r1 = (Integer) invoke(compile("testSubConstI"), 12);
        if (r1 == null) {
            printReport("testSubConstI FAILED");
        } else if (r1.intValue() == testSubConstI(12)) {
            printReport("testSubConstI PASSED");
        } else {
            printReport("testSubConstI FAILED");
        }
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

        Integer r1 = (Integer) invoke(compile("testMul2I"), 8, 4);
        if (r1 == null) {
            printReport("testMul2I FAILED");
        } else if (r1.intValue() == testMul2I(8, 4)) {
            printReport("testMul2I PASSED");
        } else {
            printReport("testMul2I FAILED");
        }

        Long r2 = (Long) invoke(compile("testMul2L"), (long) 12, (long) 6);
        if (r2 == null) {
            printReport("testMul2L FAILED");
        } else if (r2.longValue() == testMul2L(12, 6)) {
            printReport("testMul2L PASSED");
        } else {
            printReport("testMul2L FAILED");
        }

        r1 = (Integer) invoke(compile("testMulIConst"), 4);
        if (r1 == null) {
            printReport("testMulIConst FAILED");
        } else if (r1.intValue() == testMulIConst(4)) {
            printReport("testMulIConst PASSED");
        } else {
            printReport("testMulIConst FAILED");
        }

        r1 = (Integer) invoke(compile("testMulConstI"), 5);
        if (r1 == null) {
            printReport("testMulConstI FAILED");
        } else if (r1.intValue() == testMulConstI(5)) {
            printReport("testMulConstI PASSED");
        } else {
            printReport("testMulConstI FAILED");
        }
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
        Integer r1 = (Integer) invoke(compile("testDiv2I"), 8, 4);
        if (r1 == null) {
            printReport("testDiv2I FAILED (null value returned)");
        } else if (r1.intValue() == testDiv2I(8, 4)) {
            printReport("testDiv2I PASSED");
        } else {
            printReport("testDiv2I FAILED");
        }

        Long r2 = (Long) invoke(compile("testDiv2L"), (long) 12, (long) 6);
        if (r2 == null) {
            printReport("testDiv2L FAILED (null value returned)");
        } else if (r2.longValue() == testDiv2L(12, 6)) {
            printReport("testDiv2L PASSED");
        } else {
            printReport("testDiv2L FAILED");
        }

        r1 = (Integer) invoke(compile("testDivIConst"), 64);
        if (r1 == null) {
            printReport("testDivIConst FAILED (null value returned)");
        } else if (r1.intValue() == testDivIConst(64)) {
            printReport("testDivIConst PASSED");
        } else {
            printReport("testDivIConst FAILED");
        }

        r1 = (Integer) invoke(compile("testDivConstI"), 8);
        if (r1 == null) {
            printReport("testDivConstI FAILED (null value returned)");
        } else if (r1.intValue() == testDivConstI(8)) {
            printReport("testDivConstI PASSED");
        } else {
            printReport("testDivConstI FAILED");
        }
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

    @Ignore
    @Test
    public void testRem() {
        Integer r1 = (Integer) invoke(compile("testRem2I"), 8, 4);
        if (r1 == null) {
            printReport("testRem2I FAILED (null value returned)");
        } else if (r1.intValue() == testRem2I(8, 4)) {
            printReport("testRem2I PASSED");
        } else {
            printReport("testRem2I FAILED");
        }

        Long r2 = (Long) invoke(compile("testRem2L"), (long) 12, (long) 6);
        if (r2 == null) {
            printReport("testRem2L FAILED (null value returned)");
        } else if (r1.longValue() == testRem2L(12, 6)) {
            printReport("testRem2L PASSED");
        } else {
            printReport("testRem2L FAILED");
        }
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
        Long r1 = (Long) invoke(compile("testI2L"), 8);
        if (r1 == null) {
            printReport("testI2L FAILED (null value returned)");
        } else if (r1.longValue() == testI2L(8)) {
            printReport("testI2L PASSED");
        } else {
            printReport("testI2L FAILED");
        }

        Integer r2 = (Integer) invoke(compile("testL2I"), (long) 12);
        if (r2 == null) {
            printReport("testL2I FAILED (null value returned)");
        } else if (r1.longValue() == testL2I(12)) {
            printReport("testL2I PASSED");
        } else {
            printReport("testL2I FAILED");
        }

        // invoke(compile("testI2C"), 65);
        // invoke(compile("testI2B"), 9);
        // invoke(compile("testI2F"), 17);
        // invoke(compile("testI2D"), 22);
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

    public static void printReport(String message) {
        // CheckStyle: stop system..print check
        System.out.println(message);
        // CheckStyle: resume system..print check

    }

    public static void main(String[] args) {
        IntegerPTXTest test = new IntegerPTXTest();
        for (Method m : IntegerPTXTest.class.getMethods()) {
            String name = m.getName();
            if (m.getAnnotation(Test.class) == null && name.startsWith("test")) {
                // CheckStyle: stop system..print check
                System.out.println(name + ": \n" + new String(test.compile(name).getTargetCode()));
                // CheckStyle: resume system..print check
            }
        }
    }
}
