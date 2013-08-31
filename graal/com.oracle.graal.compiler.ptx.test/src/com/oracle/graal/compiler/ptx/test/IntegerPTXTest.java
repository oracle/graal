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

import org.junit.Test;

import java.lang.reflect.Method;


public class IntegerPTXTest extends PTXTestBase {

    @Test
    public void testAdd() {

        Long r2 = (Long) invoke(compile("testAdd2L"), (long) 12, (long) 6);
        if (r2 == null) {
            printReport("testAdd2L FAILED");
        } else if (r2.longValue() == 18) {
            printReport("testAdd2L PASSED");
        } else {
            printReport("testAdd2L FAILED");
        }

        //invoke(compile("testAdd2B"), (byte) 6, (byte) 4);

        Integer r4 = (Integer) invoke(compile("testAddIConst"), 5);
        if (r4 == null) {
            printReport("testAddIConst FAILED");
        } else if (r4.intValue() == 37) {
            printReport("testAddIConst PASSED");
        } else {
            printReport("testAddIConst FAILED");
        }

        r4 = (Integer) invoke(compile("testAddConstI"), 7);
        if (r4 == null) {
            printReport("testAddConstI FAILED");
        } else if (r4.intValue() == 39) {
            printReport("testAddConstI PASSED");
        } else {
            printReport("testAddConstI FAILED");
        }

        r4 = (Integer) invoke(compile("testAdd2I"), 18, 24);
        if (r4 == null) {
            printReport("testAdd2I FAILED");
        } else if (r4.intValue() == 42) {
            printReport("testAdd2I PASSED");
        } else {
            printReport("testAdd2I FAILED");
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
        Long r2 = (Long) invoke(compile("testSub2L"), (long) 12, (long) 6);
        if (r2 == null) {
            printReport("testSub2I FAILED (null return value)");
        } else if (r2.longValue() == 6) {
            printReport("testSub2I PASSED");
        } else {
            printReport("testSub2I FAILED");
        }

        Integer r1 = (Integer) invoke(compile("testSub2I"), 18, 4);

        if (r1 == null) {
            printReport("testSub2I FAILED");
        } else if (r1.intValue() == 14) {
            printReport("testSub2I PASSED");
        } else {
            printReport("testSub2I FAILED");
        }

        r1 = (Integer) invoke(compile("testSubIConst"), 35);
        if (r1 == null) {
            printReport("testSubIConst FAILED");
        } else if (r1.intValue() == 3) {
            printReport("testSubIConst PASSED");
        } else {
            printReport("testSubIConst FAILED");
        }

        r1 = (Integer) invoke(compile("testSubConstI"), 12);
        if (r1 == null) {
            printReport("testSubConstI FAILED");
        } else if (r1.intValue() == 20) {
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
        invoke(compile("testMul2I"), 8, 4);
        invoke(compile("testMul2L"), (long) 12, (long) 6);
        invoke(compile("testMulIConst"), 4);
        invoke(compile("testMulConstI"), 5);
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
        invoke(compile("testDiv2I"), 8, 4);
        invoke(compile("testDiv2L"), (long) 12, (long) 6);
        invoke(compile("testDivIConst"), 64);
        invoke(compile("testDivConstI"), 8);
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
        invoke(compile("testRem2I"), 8, 4);
        invoke(compile("testRem2L"), (long) 12, (long) 6);
    }

    public static int testRem2I(int a, int b) {
        return a % b;
    }

    public static long testRem2L(long a, long b) {
        return a % b;
    }

    @Test
    public void testIntConversion() {
        invoke(compile("testI2L"), 8);
        invoke(compile("testL2I"), (long) 12);
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
