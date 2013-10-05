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

import java.lang.reflect.Method;

import org.junit.*;

import com.oracle.graal.api.code.CompilationResult;

/* PTX ISA 3.1 - 8.7.3 Floating-Point Instructions */
public class FloatPTXTest extends PTXTestBase {

    @Ignore
    @Test
    public void testAdd() {
        CompilationResult r = compile("testAdd2F");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testAdd2F FAILED");
        }

        /*
        r = compile("testAdd2D");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testAdd2D FAILED");
        }

        r = compile("testAddFConst");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testAddFConst FAILED");
        }
        r = compile("testAddConstF");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testConstF FAILED");
        }
        r = compile("testAddDConst");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testAddDConst FAILED");
        }
        r = compile("testAddConstD");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testConstD FAILED");
        }
        */
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

    @Ignore
    @Test
    public void testSub() {
        CompilationResult r = compile("testSub2F");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testSub2F FAILED");
        }

        r = compile("testSub2D");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testSub2D FAILED");
        }

        r = compile("testSubFConst");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testSubFConst FAILED");
        }

        r = compile("testSubConstF");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testSubConstF FAILED");
        }

        r = compile("testSubDConst");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testSubDconst FAILED");
        }

        r = compile("testSubConstD");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testConstD FAILED");
        }
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

    @Ignore
    @Test
    public void testMul() {
        CompilationResult r = compile("testMul2F");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testAdd2F FAILED");
        }

        r = compile("testMul2D");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testAdd2F FAILED");
        }

        r = compile("testMulFConst");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testAdd2F FAILED");
        }

        r = compile("testMulConstF");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testAdd2F FAILED");
        }

        r = compile("testMulDConst");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testAdd2F FAILED");
        }

        r = compile("testMulConstD");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testAdd2F FAILED");
        }
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

    @Ignore
    @Test
    public void testDiv() {
        CompilationResult r = compile("testDiv2F");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testDiv2F FAILED");
        }

        r = compile("testDiv2D");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testDiv2D FAILED");
        }

        r = compile("testDivFConst");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testDivFConst FAILED");
        }

        r = compile("testDivConstF");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testDivConstF FAILED");
        }

        r = compile("testDivDConst");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testDivDConst FAILED");
        }

        r = compile("testDivConstD");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testDivConstD FAILED");
        }
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

    @Ignore
    @Test
    public void testNeg() {
        CompilationResult r = compile("testNeg2F");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testNeg2F FAILED");
        }

        r = compile("testNeg2D");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testNeg2D FAILED");
        }
    }

    public static float testNeg2F(float a) {
        return -a;
    }

    public static double testNeg2D(double a) {
        return -a;
    }

    @Ignore
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

    @Ignore
    @Test
    public void testFloatConversion() {
        CompilationResult r = compile("testF2I");
        if (r.getTargetCode() == null) {
            printReport("Compilation of tesF2I FAILED");
        }

        r = compile("testF2L");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testF2L FAILED");
        }

        r = compile("testF2D");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testF2D FAILED");
        }

        r = compile("testD2I");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testD2I FAILED");
        }

        r = compile("testD2L");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testD2L FAILED");
        }

        r = compile("testD2F");
        if (r.getTargetCode() == null) {
            printReport("Compilation of testD2F FAILED");
        }
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
        FloatPTXTest test = new FloatPTXTest();
        for (Method m : FloatPTXTest.class.getMethods()) {
            String name = m.getName();
            if (m.getAnnotation(Test.class) == null && name.startsWith("test") && name.startsWith("testRem") == false) {
                // CheckStyle: stop system..print check
                System.out.println(name + ": \n" + new String(test.compile(name).getTargetCode()));
                // CheckStyle: resume system..print check
            }
        }
    }
}
