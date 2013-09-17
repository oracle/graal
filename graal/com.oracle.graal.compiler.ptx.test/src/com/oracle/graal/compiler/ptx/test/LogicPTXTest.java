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

import org.junit.Test;

/* PTX ISA 3.1 - 8.7.5 Logic and Shift Instructions */
public class LogicPTXTest extends PTXTestBase {

    @Test
    public void testAnd() {
        compile("testAnd2I");
        compile("testAnd2L");
    }

    public static int testAnd2I(int a, int b) {
        return a & b;
    }

    public static long testAnd2L(long a, long b) {
        return a & b;
    }

    @Test
    public void testOr() {
        compile("testOr2I");
        compile("testOr2L");
    }

    public static int testOr2I(int a, int b) {
        return a | b;
    }

    public static long testOr2L(long a, long b) {
        return a | b;
    }

    @Test
    public void testXor() {
        compile("testXor2I");
        compile("testXor2L");
    }

    public static int testXor2I(int a, int b) {
        return a ^ b;
    }

    public static long testXor2L(long a, long b) {
        return a ^ b;
    }

    @Test
    public void testNot() {
        compile("testNot1I");
        compile("testNot1L");
    }

    public static int testNot1I(int a) {
        return ~a;
    }

    public static long testNot1L(long a) {
        return ~a;
    }

    @Test
    public void testShiftLeft() {
        compile("testShiftLeft2I");
        compile("testShiftLeft2L");
    }

    public static int testShiftLeft2I(int a, int b) {
        return a << b;
    }

    public static long testShiftLeft2L(long a, int b) {
        return a << b;
    }

    @Test
    public void testShiftRight() {
        compile("testShiftRight2I");
        compile("testShiftRight2L");
        compile("testUnsignedShiftRight2I");
        // compile("testUnsignedShiftRight2L");
    }

    public static int testShiftRight2I(int a, int b) {
        return a >> b;
    }

    public static long testShiftRight2L(long a, int b) {
        return a >> b;
    }

    public static int testUnsignedShiftRight2I(int a, int b) {
        return a >>> b;
    }

    public static long testUnsignedShiftRight2L(long a, long b) {
        return a >>> b;
    }

    public static void main(String[] args) {
        LogicPTXTest test = new LogicPTXTest();
        for (Method m : LogicPTXTest.class.getMethods()) {
            String name = m.getName();
            if (m.getAnnotation(Test.class) == null && name.startsWith("test")) {
                // CheckStyle: stop system..print check
                System.out.println(name + ": \n" + new String(test.compile(name).getTargetCode()));
                // CheckStyle: resume system..print check
            }
        }
    }
}
