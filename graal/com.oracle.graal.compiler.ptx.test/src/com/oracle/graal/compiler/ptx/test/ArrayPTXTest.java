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

@Ignore
public class ArrayPTXTest extends PTXTestBase {

    @Test
    public void testArray() {
        int[] arrayI = {
            1, 2, 3, 4, 5
        };
        Integer resI = (Integer) invoke(compile("testArray1I"), arrayI, 3);
        printReport("testArray1I: " + resI);
        // compile("testArray1J");
        // compile("testArray1B");
        // compile("testArray1S");
        // compile("testArray1C");
        // compile("testArray1F");
        // compile("testArray1D");
        // compile("testArray1L");
        // compile("testStoreArray1I");
        // compile("testStoreArray1J");
        // compile("testStoreArray1B");
        // compile("testStoreArray1S");
        // compile("testStoreArray1F");
        // compile("testStoreArray1D");
    }

    public static int testArray1I(int[] array, int i) {
        return array[i];
    }

    public static long testArray1J(long[] array, int i) {
        return array[i];
    }

    public static byte testArray1B(byte[] array, int i) {
        return array[i];
    }

    public static short testArray1S(short[] array, int i) {
        return array[i];
    }

    public static char testArray1C(char[] array, int i) {
        return array[i];
    }

    public static float testArray1F(float[] array, int i) {
        return array[i];
    }

    public static double testArray1D(double[] array, int i) {
        return array[i];
    }

    public static Object testArray1L(Object[] array, int i) {
        return array[i];
    }

    public static void testStoreArray1I(int[] array, int i, int val) {
        array[i] = val;
    }

    public static void testStoreArray1B(byte[] array, int i, byte val) {
        array[i] = val;
    }

    public static void testStoreArray1S(short[] array, int i, short val) {
        array[i] = val;
    }

    public static void testStoreArray1J(long[] array, int i, long val) {
        array[i] = val;
    }

    public static void testStoreArray1F(float[] array, int i, float val) {
        array[i] = val;
    }

    public static void testStoreArray1D(double[] array, int i, double val) {
        array[i] = val;
    }

    public static void printReport(String message) {
        // CheckStyle: stop system..print check
        System.out.println(message);
        // CheckStyle: resume system..print check

    }

    public static void main(String[] args) {
        ArrayPTXTest test = new ArrayPTXTest();
        for (Method m : ArrayPTXTest.class.getMethods()) {
            String name = m.getName();
            if (m.getAnnotation(Test.class) == null && name.startsWith("test")) {
                // CheckStyle: stop system..print check
                System.out.println(name + ": \n" + new String(test.compile(name).getTargetCode()));
                // CheckStyle: resume system..print check
            }
        }
    }
}
