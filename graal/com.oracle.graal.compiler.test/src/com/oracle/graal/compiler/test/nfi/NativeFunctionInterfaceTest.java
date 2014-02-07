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
package com.oracle.graal.compiler.test.nfi;

import static com.oracle.graal.graph.UnsafeAccess.*;
import static org.junit.Assert.*;

import java.util.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.target.*;
import com.oracle.graal.runtime.*;

public class NativeFunctionInterfaceTest {

    public final RuntimeProvider runtimeProvider;
    public final NativeFunctionInterface ffi;

    public NativeFunctionInterfaceTest() {
        this.runtimeProvider = Graal.getRequiredCapability(RuntimeProvider.class);
        Assume.assumeTrue(runtimeProvider.getHostBackend() instanceof HostBackend);
        ffi = ((HostBackend) runtimeProvider.getHostBackend()).getNativeFunctionInterface();
    }

    private List<Long> allocations = new ArrayList<>();

    protected long malloc(int length) {
        long buf = unsafe.allocateMemory(length);
        allocations.add(buf);
        return buf;
    }

    @After
    public void cleanup() {
        for (long buf : allocations) {
            unsafe.freeMemory(buf);
        }
    }

    private static void assertCStringEquals(long cString, String s) {
        for (int i = 0; i < s.length(); i++) {
            assertEquals(unsafe.getByte(cString + i) & 0xFF, (byte) s.charAt(i));
        }
        assertEquals(unsafe.getByte(cString + s.length()) & 0xFF, (byte) '\0');
    }

    @Test
    public void test1() {

        NativeFunctionHandle malloc = ffi.getFunctionHandle("malloc", long.class, int.class);
        NativeFunctionHandle snprintf = ffi.getFunctionHandle("snprintf", int.class, long.class, int.class, long.class);
        NativeFunctionHandle free = ffi.getFunctionHandle("free", void.class, long.class);

        String string = "GRAAL";
        int bufferLength = string.length() + 1;
        long cString = (long) malloc.call(bufferLength);
        writeCString(string, cString);

        long cStringCopy = malloc(bufferLength);
        int result = (int) snprintf.call(cStringCopy, bufferLength, cString);
        Assert.assertEquals(string.length(), result);
        assertCStringEquals(cString, string);
        assertCStringEquals(cStringCopy, string);

        free.call(cString);
    }

    @Test
    public void test2() {
        String formatString = "AB %f%f";
        long formatCString = writeCString("AB %f%f", malloc(formatString.length() + 1));

        String referenceString = "AB 1.0000001.000000";
        int bufferLength = referenceString.length() + 1;
        long buffer = malloc(bufferLength);

        NativeFunctionHandle snprintf = ffi.getFunctionHandle("snprintf", int.class, long.class, int.class, long.class, double.class, double.class);
        int result = (int) snprintf.call(buffer, bufferLength, formatCString, 1.0D, 1.0D);

        assertCStringEquals(buffer, referenceString);
        Assert.assertEquals(referenceString.length(), result);
    }

    @Test
    public void test3() {
        String format = "%i%i%i%i%i%i%i%i%i%i%i%i";
        long formatCString = writeCString(format, malloc(format.length() + 1));
        String referenceString = "01234567891011";

        int bufferLength = referenceString.length() + 1;
        long buffer = malloc(bufferLength);

        NativeFunctionHandle snprintf = ffi.getFunctionHandle("snprintf", int.class, long.class, int.class, long.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class, int.class);

        int result = (int) snprintf.call(buffer, bufferLength, formatCString, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        assertCStringEquals(buffer, referenceString);
        Assert.assertEquals(referenceString.length(), result);
    }

    @Test
    public void test4() {
        long str = malloc(49);
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
        int bufferLength = referenceString.length() + 1;

        long buffer = malloc(bufferLength);

        NativeFunctionHandle snprintf = ffi.getFunctionHandle("snprintf", int.class, long.class, int.class, long.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class, int.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class,
                        double.class, double.class, double.class, double.class);

        int result = (int) snprintf.call(buffer, bufferLength, str, val[0], val[1], val[2], val[3], val[4], val[5], val[6], val[7], val[8], val[9], val[10], val[11], dval[0], dval[1], dval[2],
                        dval[3], dval[4], dval[5], dval[6], dval[7], dval[8], dval[9], dval[10], dval[11]);
        assertCStringEquals(buffer, referenceString);
        Assert.assertEquals(referenceString.length(), result);
    }

    @Test
    public void test5() {
        long str = malloc(73);
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
        int bufferLength = referenceString.length() + 1;

        long buffer = malloc(bufferLength);

        NativeFunctionHandle snprintf = ffi.getFunctionHandle("snprintf", int.class, long.class, int.class, long.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class, int.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class,
                        double.class, double.class, double.class, double.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class,
                        char.class, char.class);

        int result = (int) snprintf.call(buffer, bufferLength, str, val[0], val[1], val[2], val[3], val[4], val[5], val[6], val[7], val[8], val[9], val[10], val[11], dval[0], dval[1], dval[2],
                        dval[3], dval[4], dval[5], dval[6], dval[7], dval[8], dval[9], dval[10], dval[11], cval[0], cval[1], cval[2], cval[3], cval[4], cval[5], cval[6], cval[7], cval[8], cval[9],
                        cval[10], cval[11]);
        assertCStringEquals(buffer, referenceString);
        Assert.assertEquals(referenceString.length(), result);
    }

    @Test
    public void test6() {
        NativeFunctionHandle handle = ffi.getFunctionHandle("pow", double.class, double.class, double.class);
        double result = (double) handle.call(3D, 5.5D);
        assertEquals(Math.pow(3D, 5.5D), result, 0);
    }

    @Test
    public void test7() {
        double result = 0;
        NativeFunctionHandle handle = ffi.getFunctionHandle("pow", double.class, double.class, double.class);
        for (int i = 0; i < 100000; i++) {
            result = (double) handle.call(3D, 5.5D);
        }
        assertEquals(Math.pow(3D, 5.5D), result, 0);
    }

    @Test
    public void test8() {
        String formatString = "AB %f%f";
        long formatCString = writeCString("AB %f%f", malloc(formatString.length() + 1));

        String expected = "AB 1.0000001.000000";
        int bufferLength = expected.length() + 1;
        byte[] buffer = new byte[bufferLength];

        NativeFunctionHandle snprintf = ffi.getFunctionHandle("snprintf", int.class, byte[].class, int.class, long.class, double.class, double.class);
        int result = (int) snprintf.call(buffer, bufferLength, formatCString, 1.0D, 1.0D);

        // trim trailing '\0'
        String actual = new String(buffer, 0, expected.length());

        assertEquals(expected, actual);
        Assert.assertEquals(expected.length(), result);
    }

    @Test
    public void test9() {
        double[] src = {2454.346D, 98789.22D, Double.MAX_VALUE, Double.MIN_NORMAL, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};
        double[] dst = new double[src.length];

        NativeFunctionHandle memcpy = ffi.getFunctionHandle("memcpy", void.class, double[].class, double[].class, int.class);
        memcpy.call(dst, src, src.length * (Double.SIZE / Byte.SIZE));

        assertArrayEquals(src, dst, 0.0D);
    }
}
