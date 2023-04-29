/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package org.graalvm.compiler.hotspot.test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

import org.graalvm.compiler.api.test.Graal;
import org.graalvm.compiler.replacements.nodes.GHASHProcessBlocksNode;
import org.graalvm.compiler.runtime.RuntimeProvider;
import org.graalvm.compiler.test.AddExports;
import org.junit.Assume;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;

import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.code.InstalledCode;

@AddExports("java.base/com.sun.crypto.provider")
public class HotSpotGHASHTest extends HotSpotGraalCompilerTest {

    private Class<?> classGHASH;
    private Constructor<?> ghashConstructor;
    private Method methodUpdate;
    private Method methodDigest;
    private Field fieldState;

    @Before
    public void init() {
        Architecture arch = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getTarget().arch;
        Assume.assumeTrue(GHASHProcessBlocksNode.isSupported(arch));
        try {
            classGHASH = Class.forName("com.sun.crypto.provider.GHASH");
            ghashConstructor = classGHASH.getDeclaredConstructor(byte[].class);
            ghashConstructor.setAccessible(true);
            methodUpdate = classGHASH.getDeclaredMethod("update", byte[].class, int.class, int.class);
            methodUpdate.setAccessible(true);
            methodDigest = classGHASH.getDeclaredMethod("digest");
            methodDigest.setAccessible(true);
            fieldState = classGHASH.getDeclaredField("state");
            fieldState.setAccessible(true);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException e) {
            throw new AssumptionViolatedException(e.getMessage());
        }
    }

    private static final String HEX_DIGITS = "0123456789abcdef";

    private static byte[] bytes(String hex) {
        StringBuilder sb = new StringBuilder(hex);
        while ((sb.length() % 32) != 0) {
            sb.append('0');
        }
        String newHex = sb.toString();

        byte[] result = new byte[newHex.length() / 2];
        for (int i = 0; i < result.length; ++i) {
            int a = HEX_DIGITS.indexOf(newHex.charAt(2 * i));
            int b = HEX_DIGITS.indexOf(newHex.charAt(2 * i + 1));
            if ((a | b) < 0) {
                if (a < 0) {
                    throw new AssertionError("bad character " + (int) newHex.charAt(2 * i));
                }
                throw new AssertionError("bad character " + (int) newHex.charAt(2 * i + 1));
            }
            result[i] = (byte) ((a << 4) | b);
        }
        return result;
    }

    private static byte[] bytes(long l0, long l1) {
        return ByteBuffer.allocate(16).putLong(l0).putLong(l1).array();
    }

    private Result ghash(Object ghash, long[] initState, byte[]... inputs) {
        try {
            long[] state = (long[]) fieldState.get(ghash);
            System.arraycopy(initState, 0, state, 0, 2);
            for (byte[] input : inputs) {
                methodUpdate.invoke(ghash, input, 0, input.length);
            }
            return new Result(methodDigest.invoke(ghash), null);
        } catch (Exception e) {
            return new Result(null, e);
        }
    }

    private void testMultipleUpdateHelper(Object ghash, String strA, String strC, String result) {
        long[] state = new long[]{0, 0};
        byte[][] inputs = new byte[][]{bytes(strA), bytes(strC), bytes(strA.length() * 4, strC.length() * 4)};
        assertTrue(result.length() == 32);
        Result expected = new Result(bytes(result), null);
        InstalledCode intrinsic = compileAndInstallSubstitution(classGHASH, "processBlocks");
        Result actual = ghash(ghash, state, inputs);
        assertEquals(expected, actual);
        intrinsic.invalidate();
    }

    @Test
    public void testMultipleUpdate() throws InvocationTargetException, InstantiationException, IllegalAccessException {
        Object ghash = ghashConstructor.newInstance(bytes("66e94bd4ef8a2c3b884cfa59ca342b2e"));
        testMultipleUpdateHelper(ghash, "", "", "00000000000000000000000000000000");
        testMultipleUpdateHelper(ghash, "", "0388dace60b6a392f328c2b971b2fe78", "f38cbb1ad69223dcc3457ae5b6b0f885");

        ghash = ghashConstructor.newInstance(bytes("b83b533708bf535d0aa6e52980d53b78"));
        testMultipleUpdateHelper(ghash,
                        "",
                        "42831ec2217774244b7221b784d0d49c" + "e3aa212f2c02a4e035c17e2329aca12e" + "21d514b25466931c7d8f6a5aac84aa05" + "1ba30b396a0aac973d58e091473f5985",
                        "7f1b32b81b820d02614f8895ac1d4eac");
        testMultipleUpdateHelper(ghash, "feedfacedeadbeeffeedfacedeadbeef" + "abaddad2",
                        "42831ec2217774244b7221b784d0d49c" + "e3aa212f2c02a4e035c17e2329aca12e" + "21d514b25466931c7d8f6a5aac84aa05" + "1ba30b396a0aac973d58e091",
                        "698e57f70e6ecc7fd9463b7260a9ae5f");
        testMultipleUpdateHelper(ghash,
                        "feedfacedeadbeeffeedfacedeadbeef" + "abaddad2",
                        "61353b4c2806934a777ff51fa22a4755" + "699b2a714fcdc6f83766e5f97b6c7423" + "73806900e49f24b22b097544d4896b42" + "4989b5e1ebac0f07c23f4598",
                        "df586bb4c249b92cb6922877e444d37b");
    }

    private Result ghash(Object ghash, long[] initState, byte[] input, int inOff, int inLen) {
        try {
            long[] state = (long[]) fieldState.get(ghash);
            System.arraycopy(initState, 0, state, 0, 2);
            methodUpdate.invoke(ghash, input, inOff, inLen);
            return new Result(methodDigest.invoke(ghash), null);
        } catch (Exception e) {
            return new Result(null, e);
        }
    }

    private void testGHASH(Object ghash, long[] initState, byte[] input, int inOff, int inLen) {
        Result expected = ghash(ghash, initState, input, inOff, inLen);
        InstalledCode intrinsic = compileAndInstallSubstitution(classGHASH, "processBlocks");
        Result actual = ghash(ghash, initState, input, inOff, inLen);
        assertEquals(expected, actual);
        intrinsic.invalidate();
    }

    @Test
    public void testOffset() throws InvocationTargetException, InstantiationException, IllegalAccessException {
        Object ghash = ghashConstructor.newInstance(bytes(-2549203865593441186L, -7934336365809252297L));

        byte[] input = new byte[]{23, 3, 3, 0, 65, 112, -106, -54, 49, -74, -104, -65, -27, 85, 53, 64, 68, 112, -1, -91, 65, -93, -102, 126, 106, 24, -38, 10, 11, 110, -85, -123, -99, 121, 1, -100,
                        6, -52, 17, -46, 50, -75, 69, 11, -119, -109, 60, -69, -125, -83, 79, 93, -88, 24, -28, 111, 39, -105, -13, -14, -5, -5, 65, 57, 6, -112, -96, 75, 28, 42, 64, 95, -5, -40, -64,
                        -83, -6, -30, -42, 108, 64, 3, -48, 62, 100, 89, 108, -39, 96, 86, -15, -11, 115, -96, -96, 122, 9, -102, 63, 9, 4, 88, -106, -77, 91, -54, 98, 22, -91, 70, 75, 23, -93, -87,
                        107, -96, 32, -59, 5, -70, 61, -80, 76, -113, -115, -118, 36, -119, 32, -4, 14, 83, 18, -19, 17, 19, 57, -29, -40, 94, 13, -112, 103, 102, -96, 9, -81, -10, 91, 19, 2, 41, 108,
                        -95, 44, -98, 47, -60, 97, 27, 39, -61, 117, 42, -96, -45, 75, 115, -87, -85, -39, 14, -75, -111, -102, 76, -58, -35, -126, -122, -8, -55, 81, 56, -40, -16, 84, -93, 58, -44,
                        -60, 56, -17, -96, -83, -71, 86, -59, 111, -43, -7, 84, -58, -18, -109, -22, 6, -99, -92, -33, 9, 98, 8, -2, 47, -102, 53, 124, -85, 33, 60, -108, -102, -88, -33, 50, 96, -115,
                        14, 46, 36, 88, -61, -118, 72, 57, 13, 27, 40, 93, 44, 110, 114, -83, 126, -21, 113, -15, -16, -103, -51, 118, 12, -9, -121, -108, 19, 5, 20, -122, -29, 35, 31, -50, -81, 85,
                        57, -82, 25, 78, -24, -102, 74, -97, 107, -22, -92, 104, -76, 77, 37, -49, -114, -100, 122, -80, 79, -48, -119, 67, 72, 88, -12, 103, 107, 5, -14, -1, 56, -66, -102, 15, -72,
                        41, 41, -74, -9, -56, 12, -68, -120, 43, -44, -85, -45, 79, -84, -58, -81, 97, 10, 2, 60, 1, -103, -10, -98, 123, 6, -65, 17, -46, -58, -41, 103, -24, -119, -89, -93, -115, -3,
                        -55, 38, -119, -88, 83, -36, 29, 28, -66, -121, 9, -32, -7, 112, 19, -58, -2, -119, -20, -9, 25, 36, -120, -10, -75, 80, 34, -29, 126, -105, -37, -28, 57, 66, 127, 118, 12, 53,
                        -9, -31, -33, 7, -82, 80, -60, -10, -17, -17, 94, 63, 46, 77, 71, 8, 85, -113, -33, -16, -68, 37, 64, -21, -91, 116, -125, -41, -43, 1, -89, 6, -53, -105, 47, -5, 59, 71, -115,
                        108, 30, 125, 16, 52, 7, 87, -29, 111, 126, -42, 48, 114, 80, 54, 85, -45, 52, 37, -63, -59, 81, 55, 83, 67, -11, 68, -57, 91, -38, -40, 113, -25, 89, 86, -44, 53, -84, -48,
                        -120, -38, 21, -29, 103, -53, 32, -122, -32, -11, 20, 55, -32, -91, 99, -98, -45, -5, -94, 107, 120, 66, 90, -64, -7, 103, 122, -33, 44, -91, -80, -1, -98, 99, -71, 120, 10,
                        -114, 43, 58, -11, -69, -55, 65, -17, -113, -37, -51, 39, -117, 60, 3, -76, 87, 90, -27, 85, -82, -6, 89, -40, 77, -14, -124, 29, -9, 122, -97, 119, -126, 84, 116, 28, -45,
                        -50, 74, 107, 8, 8, 101, -124, 5, 56, 4, -125, 100, -4, -100, -11, -65, -8, -110, -27, 0, -106, -37, 29, 91, 35, 80, 88, 64, 117, -128, -91, -117, 5, -36, -27, -108, 29, 3,
                        115, 95, -69, -53, -20, -122, 39, -21, -29, -128, -58, -94, -78, -100, -4, -58, -12, 104, -96, -98, -9, 0, 64, -7, 72, -127, -86, 76, 57, -36, -86, 39, -100, -126, -71, 13,
                        116, -106, -71, -6, 66, -67, -85, -90, 92, 99, -47, -101, 16, -52, -90, -1, 84, -112, -36, -112, 114, -3, -126, 29, -121, 68, -37, -118, 7, -91, -50, -33, 23, -113, 68, -66,
                        -27, -30, -20, -78, 8, 43, -27, -62, -74, 22, 1, -53, 28, 114, -8, 54, -14, 120, 118, -70, -112, -23, 19, -2, 21, 126, -44, 20, -43, 75, 27, -92, 2, -84, 48, 108, 101, 39, 35,
                        -93, 16, 62, -58, -20, -24, 44, -109, 110, 95, -68, 73, -82, -125, -99, 26, -88, 16, -48, -125, 44, -68, -122, 57, 111, 8, 0, 43, 107, 122, 78, 57, -22, -77, 83, 115, 107, -87,
                        112, 91, 27, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

        long[] state = new long[]{-2224758530180934284L, 2670573948063642579L};
        testGHASH(ghash, state, input, 5, input.length - 5);
    }
}
