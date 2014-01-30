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
package com.oracle.graal.ffi.amd64.test;

import org.junit.*;

import com.oracle.graal.api.code.*;

public class StdLibCallTest extends LibCallTest {

    @Test
    public void mallocTest() {
        Object[] args = new Object[]{6};
        NativeFunctionHandle mallocHandle = ffi.getFunctionHandle("malloc", long.class, new Class[]{int.class});

        long p = (long) mallocHandle.call(args);
        unsafe.putByte(p, (byte) 'G');
        unsafe.putByte(p + 1, (byte) 'R');
        unsafe.putByte(p + 2, (byte) 'A');
        unsafe.putByte(p + 3, (byte) 'A');
        unsafe.putByte(p + 4, (byte) 'L');
        unsafe.putByte(p + 5, (byte) '\0');

        NativeFunctionHandle putsHandle = ffi.getFunctionHandle("puts", int.class, new Class[]{long.class});
        Object[] args2 = new Object[]{p};
        int result = (int) putsHandle.call(args2);
        Assert.assertTrue(0 < result);
        Assert.assertEquals(unsafe.getByte(p) & 0xFF, 'G');
        Assert.assertEquals(unsafe.getByte(p + 1) & 0xFF, 'R');
        Assert.assertEquals(unsafe.getByte(p + 2) & 0xFF, 'A');
        Assert.assertEquals(unsafe.getByte(p + 3) & 0xFF, 'A');
        Assert.assertEquals(unsafe.getByte(p + 4) & 0xFF, 'L');
        Assert.assertEquals(unsafe.getByte(p + 5) & 0xFF, '\0');

        NativeFunctionHandle freeHandle = ffi.getFunctionHandle("free", void.class, new Class[]{long.class});
        freeHandle.call(args2);
    }

    @Test
    public void printfSimpleTest() {
        long str = unsafe.allocateMemory(8);
        unsafe.putByte(str, (byte) 'A');
        unsafe.putByte(str + 1, (byte) 'B');
        unsafe.putByte(str + 2, (byte) ' ');
        unsafe.putByte(str + 3, (byte) '%');
        unsafe.putByte(str + 4, (byte) 'f');
        unsafe.putByte(str + 5, (byte) '%');
        unsafe.putByte(str + 6, (byte) 'f');
        unsafe.putByte(str + 7, (byte) '\0');

        Object[] args = new Object[]{str, Double.doubleToRawLongBits(1.0), Double.doubleToRawLongBits(1.0)};
        NativeFunctionHandle printfHandle = ffi.getFunctionHandle("printf", int.class, new Class[]{long.class, double.class, double.class});

        int result = (int) printfHandle.call(args);
        Assert.assertTrue(0 < result);
    }

    @Test
    public void putsSimpleTest() {
        long str = unsafe.allocateMemory(6);
        unsafe.putByte(str, (byte) 'A');
        unsafe.putByte(str + 1, (byte) 'B');
        unsafe.putByte(str + 2, (byte) 'B');
        unsafe.putByte(str + 3, (byte) 'B');
        unsafe.putByte(str + 4, (byte) '\0');

        Object[] args = new Object[]{str};
        NativeFunctionHandle printfHandle = ffi.getFunctionHandle("puts", int.class, new Class[]{long.class});

        int result = (int) printfHandle.call(args);
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

        Object[] args = new Object[]{str, val[0], val[1], val[2], val[3], val[4], val[5], val[6], val[7], val[8], val[9], val[10], val[11]};
        NativeFunctionHandle printfHandle = ffi.getFunctionHandle("printf", int.class, new Class[]{long.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class});

        int result = (int) printfHandle.call(args);
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

        Object[] args = new Object[]{str, val[0], val[1], val[2], val[3], val[4], val[5], val[6], val[7], val[8], val[9], val[10], val[11], dval[0], dval[1], dval[2], dval[3], dval[4], dval[5],
                        dval[6], dval[7], dval[8], dval[9], dval[10], dval[11]};

        NativeFunctionHandle printfHandle = ffi.getFunctionHandle("printf", int.class, new Class[]{long.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class,
                        double.class, double.class, double.class});

        int result = (int) printfHandle.call(args);
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

        Object[] args = new Object[]{str, val[0], val[1], val[2], val[3], val[4], val[5], val[6], val[7], val[8], val[9], val[10], val[11], dval[0], dval[1], dval[2], dval[3], dval[4], dval[5],
                        dval[6], dval[7], dval[8], dval[9], dval[10], dval[11], cval[0], cval[1], cval[2], cval[3], cval[4], cval[5], cval[6], cval[7], cval[8], cval[9], cval[10], cval[11]};

        NativeFunctionHandle printfHandle = ffi.getFunctionHandle("printf", int.class, new Class[]{long.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class,
                        double.class, double.class, double.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class,
                        char.class});

        int result = (int) printfHandle.call(args);
        Assert.assertTrue(0 < result);
    }
}
