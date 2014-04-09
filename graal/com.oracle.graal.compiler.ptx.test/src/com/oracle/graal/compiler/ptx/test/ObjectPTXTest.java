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

import java.util.*;

import org.junit.*;

public class ObjectPTXTest extends PTXTest {

    static class A {
        boolean z = true;
        byte b = 17;
        char c = 'D';
        short s = 12345;
        int i = 0x1234565;
        long l;
        Object o;
        float f;
        double d;
    }

    @Test
    public void test0() {
        for (long l : new long[]{Long.MIN_VALUE, -10, 0, 1, 2, 10, Long.MAX_VALUE}) {
            A a = new A();
            a.l = l;
            test("testLong", l * 2, a);
        }
    }

    public static long testLong(long l, A a) {
        return a.l + l;
    }

    @Test
    public void test1() {
        for (int i : new int[]{Integer.MIN_VALUE, -10, 0, 1, 2, 10, Integer.MAX_VALUE}) {
            A a = new A();
            a.i = i;
            test("testInt", i * 2, a);
        }
    }

    public static int testInt(int i, A a) {
        return a.i + i;
    }

    @Ignore("com.oracle.graal.graph.GraalInternalError: should not reach here: unhandled register type v3|z")
    @Test
    public void test2() {
        A a = new A();
        a.z = true;
        test("testBoolean", a);
        a.z = false;
        test("testBoolean", a);
    }

    public static boolean testBoolean(A a) {
        return a.z;
    }

    @Ignore("[CUDA] Check for malformed PTX kernel or incorrect PTX compilation options")
    @Test
    public void test3() {
        for (byte b : new byte[]{Byte.MIN_VALUE, -10, 0, 1, 2, 10, Byte.MAX_VALUE}) {
            A a = new A();
            a.b = b;
            test("testByte", b, a);
        }
    }

    public static int testByte(byte b, A a) {
        return a.b + b;
    }

    @Ignore("com.oracle.graal.graph.GraalInternalError: should not reach here: unhandled register type v5|s")
    @Test
    public void test4() {
        for (short s : new short[]{Short.MIN_VALUE, -10, 0, 1, 2, 10, Short.MAX_VALUE}) {
            A a = new A();
            a.s = s;
            test("testShort", s, a);
        }
    }

    public static int testShort(short s, A a) {
        return a.s + s;
    }

    @Ignore("java.lang.AssertionError: expected:<65531> but was:<809107451>")
    @Test
    public void test5() {
        for (char c : new char[]{Character.MIN_VALUE, 1, 2, 10, Character.MAX_VALUE}) {
            A a = new A();
            a.c = c;
            test("testChar", (char) (c - 5), a);
        }
    }

    public static int testChar(char c, A a) {
        return a.c + c;
    }

    @Test
    public void test6() {
        for (float f : new float[]{Float.MIN_VALUE, Float.MIN_NORMAL, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NaN, -11.45F, -0.0F, 0.0F, 2, 10, Float.MAX_VALUE}) {
            A a = new A();
            a.f = f;
            test("testFloat", f * 2, a);
        }
    }

    public static float testFloat(float f, A a) {
        return a.f + f;
    }

    @Test
    public void test7() {
        for (double d : new double[]{Double.MIN_VALUE, Double.MIN_NORMAL, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, -11.45D, -0.0D, 0.0D, 2, 10, Double.MAX_VALUE}) {
            A a = new A();
            a.d = d;
            test("testDouble", d * 2, a);
        }
    }

    public static double testDouble(double d, A a) {
        return a.d + d;
    }

    @Ignore("Object return values not yet supported")
    @Test
    public void test9() {
        for (Object o : new Object[]{null, "object", new Object(), new HashMap<>()}) {
            A a = new A();
            a.o = o;
            test("testObject", a);
        }
    }

    public static Object testObject(A a) {
        return a.o;
    }
}
