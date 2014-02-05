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
package com.oracle.graal.nfi.test;

import org.junit.*;

import com.oracle.graal.api.code.*;

public class StdLibCallTest extends LibCallTest {

    private static void copyString(long pointer, String s) {
        for (int i = 0; i < s.length(); i++) {
            unsafe.putByte(pointer + i, (byte) s.charAt(i));
        }
        unsafe.putByte(pointer + s.length(), (byte) '\0');
    }

    private static void checkString(long pointer, String s) {
        for (int i = 0; i < s.length(); i++) {
            Assert.assertEquals(unsafe.getByte(pointer + i) & 0xFF, (byte) s.charAt(i));
        }
        Assert.assertEquals(unsafe.getByte(pointer + s.length()) & 0xFF, (byte) '\0');
    }

    private static long getBuffer(int length) {
        return unsafe.allocateMemory(length);
    }

    @Test
    public void mallocTest() {
        String string = "GRAAL";
        int stringLength = string.length() + 1;

        Object[] args = new Object[]{1};
        args[0] = stringLength;
        NativeFunctionHandle mallocHandle = ffi.getFunctionHandle("malloc", long.class, new Class[]{int.class});

        long p = (long) mallocHandle.call(args);
        copyString(p, string);

        long buffer = getBuffer(stringLength);
        NativeFunctionHandle putsHandle = ffi.getFunctionHandle("snprintf", int.class, new Class[]{long.class, int.class, long.class});
        Object[] args2 = new Object[]{buffer, stringLength, p};
        int result = (int) putsHandle.call(args2);
        Assert.assertTrue(0 < result);
        checkString(p, string);
        checkString(buffer, string);

        NativeFunctionHandle freeHandle = ffi.getFunctionHandle("free", void.class, new Class[]{long.class});
        freeHandle.call(args2);
    }

    @Test
    public void printfSimpleTest() {
        long str = unsafe.allocateMemory(8);
        copyString(str, "AB %f%f");

        String referenceString = "AB 1.0000001.000000";
        int referenceStringLenght = referenceString.length() + 1;

        long buffer = getBuffer(referenceStringLenght);

        Object[] args = new Object[]{buffer, referenceStringLenght, str, Double.doubleToRawLongBits(1.0), Double.doubleToRawLongBits(1.0)};
        NativeFunctionHandle printfHandle = ffi.getFunctionHandle("snprintf", int.class, new Class[]{long.class, int.class, long.class, double.class, double.class});

        int result = (int) printfHandle.call(args);

        checkString(buffer, referenceString);
        Assert.assertTrue(0 < result);
    }

    @Test
    public void printfTest() {
        long str = unsafe.allocateMemory(25);
        int[] val = new int[12];
        for (int i = 0; i < 12; i++) {
            unsafe.putByte(str + 2 * i, (byte) '%');
            unsafe.putByte(str + 2 * i + 1, (byte) 'i');
            val[i] = i;
        }
        unsafe.putByte(str + 24, (byte) '\0');

        String referenceString = "01234567891011";
        int referenceStringLenght = referenceString.length() + 1;

        long buffer = getBuffer(referenceStringLenght);

        Object[] args = new Object[]{buffer, referenceStringLenght, str, val[0], val[1], val[2], val[3], val[4], val[5], val[6], val[7], val[8], val[9], val[10], val[11]};
        NativeFunctionHandle printfHandle = ffi.getFunctionHandle("snprintf", int.class, new Class[]{long.class, int.class, long.class, int.class, int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class, int.class, int.class, int.class});

        int result = (int) printfHandle.call(args);
        checkString(buffer, referenceString);
        Assert.assertTrue(0 < result);
    }

    @Test
    public void printfTest2() {
        long str = unsafe.allocateMemory(49);
        int[] val = new int[12];
        for (int i = 0; i < 12; i++) {
            unsafe.putByte(str + 2 * i, (byte) '%');
            unsafe.putByte(str + 2 * i + 1, (byte) 'i');
            val[i] = i;
        }
        double[] dval = new double[12];
        for (int i = 12; i < 24; i++) {
            unsafe.putByte(str + 2 * i, (byte) '%');
            unsafe.putByte(str + 2 * i + 1, (byte) 'f');
            dval[i - 12] = i + 0.5;
        }
        unsafe.putByte(str + 48, (byte) '\0');

        String referenceString = "0123456789101112.50000013.50000014.50000015.50000016.50000017.50000018.50000019.50000020.500000" + "21.50000022.50000023.500000";
        int referenceStringLenght = referenceString.length() + 1;

        long buffer = getBuffer(referenceStringLenght);

        Object[] args = new Object[]{buffer, referenceStringLenght, str, val[0], val[1], val[2], val[3], val[4], val[5], val[6], val[7], val[8], val[9], val[10], val[11], dval[0], dval[1], dval[2],
                        dval[3], dval[4], dval[5], dval[6], dval[7], dval[8], dval[9], dval[10], dval[11]};

        NativeFunctionHandle printfHandle = ffi.getFunctionHandle("snprintf", int.class, new Class[]{long.class, int.class, long.class, int.class, int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class, int.class, int.class, int.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class,
                        double.class, double.class, double.class, double.class, double.class});

        int result = (int) printfHandle.call(args);
        checkString(buffer, referenceString);
        Assert.assertTrue(0 < result);
    }

    @Test
    public void printfTest3() {
        long str = unsafe.allocateMemory(73);
        int[] val = new int[12];
        for (int i = 0; i < 12; i++) {
            unsafe.putByte(str + 2 * i, (byte) '%');
            unsafe.putByte(str + 2 * i + 1, (byte) 'i');
            val[i] = i;
        }
        double[] dval = new double[12];
        for (int i = 12; i < 24; i++) {
            unsafe.putByte(str + 2 * i, (byte) '%');
            unsafe.putByte(str + 2 * i + 1, (byte) 'f');
            dval[i - 12] = i + 0.5;
        }
        char[] cval = new char[12];
        for (int i = 24; i < 36; i++) {
            unsafe.putByte(str + 2 * i, (byte) '%');
            unsafe.putByte(str + 2 * i + 1, (byte) 'c');
            cval[i - 24] = (char) ('a' + (i - 24));
        }
        unsafe.putByte(str + 72, (byte) '\0');

        String referenceString = "0123456789101112.50000013.50000014.50000015.50000016.50000017.50000018.50000019.50000020.50000021.50000022.50000023.500000abcdefghijkl";
        int referenceStringLenght = referenceString.length() + 1;

        long buffer = getBuffer(referenceStringLenght);

        Object[] args = new Object[]{buffer, referenceStringLenght, str, val[0], val[1], val[2], val[3], val[4], val[5], val[6], val[7], val[8], val[9], val[10], val[11], dval[0], dval[1], dval[2],
                        dval[3], dval[4], dval[5], dval[6], dval[7], dval[8], dval[9], dval[10], dval[11], cval[0], cval[1], cval[2], cval[3], cval[4], cval[5], cval[6], cval[7], cval[8], cval[9],
                        cval[10], cval[11]};

        NativeFunctionHandle printfHandle = ffi.getFunctionHandle("snprintf", int.class, new Class[]{long.class, int.class, long.class, int.class, int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class, int.class, int.class, int.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class,
                        double.class, double.class, double.class, double.class, double.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class,
                        char.class, char.class, char.class, char.class});

        int result = (int) printfHandle.call(args);
        checkString(buffer, referenceString);
        Assert.assertTrue(0 < result);
    }
}
