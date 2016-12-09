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
package com.oracle.nfi.test;

import static java.io.File.separatorChar;
import static java.lang.System.getProperty;
import static java.lang.System.mapLibraryName;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import com.oracle.nfi.NativeFunctionInterfaceRuntime;
import com.oracle.nfi.api.NativeFunctionHandle;
import com.oracle.nfi.api.NativeFunctionInterface;
import com.oracle.nfi.api.NativeFunctionPointer;
import com.oracle.nfi.api.NativeLibraryHandle;

import sun.misc.Unsafe;

public class NativeFunctionInterfaceTest {

    private static final Unsafe unsafe = initUnsafe();

    // https://bugs.openjdk.java.net/browse/JDK-8146156
    private static String formatString(String format, Object... args) {
        return String.format(Locale.getDefault(), format, args);
    }

    private static Unsafe initUnsafe() {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            return (Unsafe) theUnsafe.get(Unsafe.class);
        } catch (Exception e) {
            throw new RuntimeException("exception while trying to get Unsafe", e);
        }
    }

    public final NativeFunctionInterface nfi;

    public NativeFunctionInterfaceTest() {
        nfi = NativeFunctionInterfaceRuntime.getNativeFunctionInterface();
    }

    private List<Long> allocations = new ArrayList<>();

    protected long malloc(int length) {
        long buf = unsafe.allocateMemory(length);
        allocations.add(buf);
        return buf;
    }

    @Before
    public void setUp() {
        // Ignore on SPARC
        Assume.assumeFalse(System.getProperty("os.arch").toUpperCase().contains("SPARC"));
        // and AArch64
        Assume.assumeFalse(System.getProperty("os.arch").toUpperCase().contains("AARCH64"));
    }

    @After
    public void cleanup() {
        for (long buf : allocations) {
            unsafe.freeMemory(buf);
        }
    }

    private static String readCString(long cStringAddress) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0;; i++) {
            char c = (char) unsafe.getByte(cStringAddress + i);
            if (c == 0) {
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static void assertCStringEquals(long cStringAddress, String expected) {
        String cString = readCString(cStringAddress);
        assertEquals(expected, cString);
    }

    @Test
    public void test1() {
        assumeTrue(nfi.isDefaultLibrarySearchSupported());
        NativeFunctionHandle malloc = nfi.getFunctionHandle("malloc", long.class, int.class);
        NativeFunctionHandle snprintf = nfi.getFunctionHandle("snprintf", int.class, long.class, int.class, long.class);
        NativeFunctionHandle free = nfi.getFunctionHandle("free", void.class, long.class);

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
        assumeTrue(nfi.isDefaultLibrarySearchSupported());
        String formatString = "AB %f%f";
        long formatCString = writeCString("AB %f%f", malloc(formatString.length() + 1));
        String referenceString = formatString(formatString, 1.0D, 1.0D);
        int bufferLength = referenceString.length() + 1;
        long buffer = malloc(bufferLength);

        NativeFunctionHandle snprintf = nfi.getFunctionHandle("snprintf", int.class, long.class, int.class, long.class, double.class, double.class);
        int result = (int) snprintf.call(buffer, bufferLength, formatCString, 1.0D, 1.0D);

        assertCStringEquals(buffer, referenceString);
        Assert.assertEquals(referenceString.length(), result);
    }

    @Test
    public void test3() {
        assumeTrue(nfi.isDefaultLibrarySearchSupported());
        String format = "%i%i%i%i%i%i%i%i%i%i%i%i";
        long formatCString = writeCString(format, malloc(format.length() + 1));
        String referenceString = "01234567891011";

        int bufferLength = referenceString.length() + 1;
        long buffer = malloc(bufferLength);

        NativeFunctionHandle snprintf = nfi.getFunctionHandle("snprintf", int.class, long.class, int.class, long.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class, int.class);

        int result = (int) snprintf.call(buffer, bufferLength, formatCString, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
        assertCStringEquals(buffer, referenceString);
        Assert.assertEquals(referenceString.length(), result);
    }

    @Test
    public void test4() {
        assumeTrue(nfi.isDefaultLibrarySearchSupported());
        StringBuilder formatStringBuf = new StringBuilder();
        int[] val = new int[12];
        for (int i = 0; i < 12; i++) {
            formatStringBuf.append("%d");
            val[i] = i;
        }
        double[] dval = new double[12];
        for (int i = 12; i < 24; i++) {
            formatStringBuf.append("%f");
            dval[i - 12] = i + 0.5;
        }

        String formatString = formatStringBuf.toString();
        long formatCString = writeCString(formatString, malloc(formatString.length() + 1));

        String referenceString = formatString(formatString, val[0], val[1], val[2], val[3], val[4], val[5], val[6], val[7], val[8], val[9], val[10], val[11], dval[0], dval[1], dval[2], dval[3],
                        dval[4], dval[5], dval[6], dval[7], dval[8], dval[9], dval[10], dval[11]);
        int bufferLength = referenceString.length() + 1;

        long buffer = malloc(bufferLength);

        NativeFunctionHandle snprintf = nfi.getFunctionHandle("snprintf", int.class, long.class, int.class, long.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class, int.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class,
                        double.class, double.class, double.class, double.class);

        int result = (int) snprintf.call(buffer, bufferLength, formatCString, val[0], val[1], val[2], val[3], val[4], val[5], val[6], val[7], val[8], val[9], val[10], val[11], dval[0], dval[1],
                        dval[2], dval[3], dval[4], dval[5], dval[6], dval[7], dval[8], dval[9], dval[10], dval[11]);
        assertCStringEquals(buffer, referenceString);
        Assert.assertEquals(referenceString.length(), result);
    }

    @Test
    public void test5() {
        assumeTrue(nfi.isDefaultLibrarySearchSupported());

        StringBuilder formatStringBuf = new StringBuilder();

        int[] val = new int[12];
        for (int i = 0; i < 12; i++) {
            formatStringBuf.append("%d");
            val[i] = i;
        }
        double[] dval = new double[12];
        for (int i = 12; i < 24; i++) {
            formatStringBuf.append("%f");
            dval[i - 12] = i + 0.5;
        }
        char[] cval = new char[12];
        for (int i = 24; i < 36; i++) {
            formatStringBuf.append("%c");
            cval[i - 24] = (char) ('a' + (i - 24));
        }

        String formatString = formatStringBuf.toString();
        long formatCString = writeCString(formatString, malloc(formatString.length() + 1));

        String referenceString = formatString(formatString, val[0], val[1], val[2], val[3], val[4], val[5], val[6], val[7], val[8], val[9], val[10], val[11], dval[0], dval[1], dval[2], dval[3],
                        dval[4], dval[5], dval[6], dval[7], dval[8], dval[9], dval[10], dval[11], cval[0], cval[1], cval[2], cval[3], cval[4], cval[5], cval[6], cval[7], cval[8], cval[9], cval[10],
                        cval[11]);
        int bufferLength = referenceString.length() + 1;

        long buffer = malloc(bufferLength);

        NativeFunctionHandle snprintf = nfi.getFunctionHandle("snprintf", int.class, long.class, int.class, long.class, int.class, int.class, int.class, int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, int.class, int.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class, double.class,
                        double.class, double.class, double.class, double.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class, char.class,
                        char.class, char.class);

        int result = (int) snprintf.call(buffer, bufferLength, formatCString, val[0], val[1], val[2], val[3], val[4], val[5], val[6], val[7], val[8], val[9], val[10], val[11], dval[0], dval[1],
                        dval[2], dval[3], dval[4], dval[5], dval[6], dval[7], dval[8], dval[9], dval[10], dval[11], cval[0], cval[1], cval[2], cval[3], cval[4], cval[5], cval[6], cval[7], cval[8],
                        cval[9], cval[10], cval[11]);
        assertCStringEquals(buffer, referenceString);
        Assert.assertEquals(referenceString.length(), result);
    }

    @Test
    public void test6() {
        assumeTrue(nfi.isDefaultLibrarySearchSupported());
        NativeFunctionHandle handle = nfi.getFunctionHandle("pow", double.class, double.class, double.class);
        double result = (double) handle.call(3D, 5.5D);
        assertEquals(Math.pow(3D, 5.5D), result, 0);
    }

    @Test
    public void test7() {
        assumeTrue(nfi.isDefaultLibrarySearchSupported());
        double result = 0;
        NativeFunctionHandle handle = nfi.getFunctionHandle("pow", double.class, double.class, double.class);
        for (int i = 0; i < 10; i++) {
            result = (double) handle.call(3D, 5.5D);
        }
        assertEquals(Math.pow(3D, 5.5D), result, 0);
    }

    @Test
    public void test8() {
        assumeTrue(nfi.isDefaultLibrarySearchSupported());
        String formatString = "AB %f%f";
        long formatCString = writeCString("AB %f%f", malloc(formatString.length() + 1));

        String expected = formatString(formatString, 1.0D, 1.0D);
        int bufferLength = expected.length() + 1;
        byte[] buffer = new byte[bufferLength];

        NativeFunctionHandle snprintf = nfi.getFunctionHandle("snprintf", int.class, byte[].class, int.class, long.class, double.class, double.class);
        int result = (int) snprintf.call(buffer, bufferLength, formatCString, 1.0D, 1.0D);

        // trim trailing '\0'
        String actual = new String(buffer, 0, expected.length());

        assertEquals(expected, actual);
        Assert.assertEquals(expected.length(), result);
    }

    private static double[] someDoubles = {2454.346D, 98789.22D, Double.MAX_VALUE, Double.MIN_NORMAL, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY};

    @Test
    public void test9() {
        assumeTrue(nfi.isDefaultLibrarySearchSupported());
        double[] src = someDoubles.clone();
        double[] dst = new double[src.length];

        NativeFunctionHandle memcpy = nfi.getFunctionHandle("memcpy", void.class, double[].class, double[].class, int.class);
        memcpy.call(dst, src, src.length * (Double.SIZE / Byte.SIZE));

        assertArrayEquals(src, dst, 0.0D);
    }

    private static String getVMName() {
        String vmName = System.getProperty("java.vm.name").toLowerCase();
        String vm = null;
        if (vmName.contains("server")) {
            vm = "server";
        } else if (vmName.contains("graal")) {
            vm = "graal";
        } else if (vmName.contains("client")) {
            vm = "client";
        }

        Assume.assumeTrue(vm != null);
        return vm;
    }

    private static String getVMLibPath() {
        String vm = getVMName();

        String path = formatString("%s%c%s%c%s", getProperty("sun.boot.library.path"), separatorChar, vm, separatorChar, mapLibraryName("jvm"));
        // Only continue if the library file exists
        Assume.assumeTrue(new File(path).exists());
        return path;
    }

    @Test
    public void test10() {
        NativeLibraryHandle vmLib = nfi.getLibraryHandle(getVMLibPath());
        NativeFunctionHandle currentTimeMillis = nfi.getFunctionHandle(vmLib, "JVM_CurrentTimeMillis", long.class);
        long time1 = (long) currentTimeMillis.call();
        long time2 = System.currentTimeMillis();
        long delta = time2 - time1;

        // The 2 calls to get the current time should not differ by more than
        // 100 milliseconds at the very most
        assertTrue(String.valueOf(delta), delta >= 0);
        assertTrue(String.valueOf(delta), delta < 100);
    }

    private static String getJavaLibPath() {
        String path = formatString("%s%c%s", getProperty("sun.boot.library.path"), separatorChar, mapLibraryName("java"));
        Assume.assumeTrue(new File(path).exists());
        return path;
    }

    private static void testD2L(NativeFunctionHandle d2l) {
        for (double d : someDoubles) {
            long expected = Double.doubleToRawLongBits(d);
            long actual = (long) d2l.call(0L, 0L, d);
            assertEquals(Double.toString(d), expected, actual);
        }
    }

    @Test
    public void test11() {
        NativeLibraryHandle javaLib = nfi.getLibraryHandle(getJavaLibPath());
        NativeFunctionHandle d2l = nfi.getFunctionHandle(javaLib, "Java_java_lang_Double_doubleToRawLongBits", long.class, long.class, long.class, double.class);
        testD2L(d2l);
    }

    @Test
    public void test12() {
        NativeLibraryHandle[] libs = {nfi.getLibraryHandle(getVMLibPath()), nfi.getLibraryHandle(getJavaLibPath())};
        NativeFunctionHandle d2l = nfi.getFunctionHandle(libs, "Java_java_lang_Double_doubleToRawLongBits", long.class, long.class, long.class, double.class);
        testD2L(d2l);

        NativeLibraryHandle[] libsReveresed = {libs[1], libs[0]};
        d2l = nfi.getFunctionHandle(libsReveresed, "Java_java_lang_Double_doubleToRawLongBits", long.class, long.class, long.class, double.class);
        testD2L(d2l);
    }

    @Test
    public void test13() {
        NativeLibraryHandle[] libs = {nfi.getLibraryHandle(getVMLibPath()), nfi.getLibraryHandle(getJavaLibPath())};
        NativeFunctionPointer functionPointer = nfi.getFunctionPointer(libs, "Java_java_lang_Double_doubleToRawLongBits");
        NativeFunctionHandle d2l = nfi.getFunctionHandle(functionPointer, long.class, long.class, long.class, double.class);
        testD2L(d2l);

        NativeLibraryHandle[] libsReveresed = {libs[1], libs[0]};
        functionPointer = nfi.getFunctionPointer(libsReveresed, "Java_java_lang_Double_doubleToRawLongBits");
        d2l = nfi.getFunctionHandle(functionPointer, long.class, long.class, long.class, double.class);
        testD2L(d2l);
    }

    @Test
    public void test14() {
        if (!nfi.isDefaultLibrarySearchSupported()) {
            try {
                nfi.getFunctionHandle("snprintf", int.class);
                fail();
            } catch (UnsatisfiedLinkError e) {
            }
        }
    }

    @Test
    public void test15() {
        assumeTrue(nfi.isDefaultLibrarySearchSupported());
        NativeFunctionHandle functionHandle = nfi.getFunctionHandle("an invalid function name", int.class);
        if (functionHandle != null) {
            fail();
        }
    }

    @Test
    public void test16() {
        NativeLibraryHandle javaLib = nfi.getLibraryHandle(getJavaLibPath());
        NativeFunctionHandle functionHandle = nfi.getFunctionHandle(javaLib, "an invalid function name", int.class);
        if (functionHandle != null) {
            fail();
        }
    }

    @Test
    public void test17() {
        NativeLibraryHandle[] libs = {nfi.getLibraryHandle(getVMLibPath()), nfi.getLibraryHandle(getJavaLibPath())};
        NativeFunctionHandle functionHandle = nfi.getFunctionHandle(libs, "an invalid function name", int.class);
        if (functionHandle != null) {
            fail();
        }
    }

    @Test
    public void test18() {
        NativeLibraryHandle[] libs = {nfi.getLibraryHandle(getVMLibPath()), nfi.getLibraryHandle(getJavaLibPath())};
        NativeFunctionHandle functionHandle = nfi.getFunctionHandle(libs, "an invalid function name", int.class);
        if (functionHandle != null) {
            fail();
        }
    }

    @Test
    public void test19() {
        NativeLibraryHandle[] libs = {nfi.getLibraryHandle(getVMLibPath()), nfi.getLibraryHandle(getJavaLibPath())};
        NativeFunctionPointer functionPointer = nfi.getFunctionPointer(libs, "an invalid function name");
        if (functionPointer != null) {
            fail();
        }
    }

    @Test
    public void test20() {
        try {
            nfi.getLibraryHandle("an invalid library name");
            fail();
        } catch (UnsatisfiedLinkError e) {
        }
    }

    /**
     * Writes the contents of a {@link String} to a native memory buffer as a {@code '\0'}
     * terminated C string. The caller is responsible for ensuring the buffer is at least
     * {@code s.length() + 1} bytes long. The caller is also responsible for releasing the buffer
     * when it is no longer.
     *
     * @return the value of {@code buf}
     */
    private static long writeCString(String s, long buf) {
        int size = s.length();
        for (int i = 0; i < size; i++) {
            unsafe.putByte(buf + i, (byte) s.charAt(i));
        }
        unsafe.putByte(buf + size, (byte) '\0');
        return buf;
    }
}
