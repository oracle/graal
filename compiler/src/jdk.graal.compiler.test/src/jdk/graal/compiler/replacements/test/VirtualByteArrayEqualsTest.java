/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import org.junit.Test;

import jdk.graal.compiler.core.test.GraalCompilerTest;
import jdk.graal.compiler.nodes.StructuredGraph;

/**
 * {@link java.util.Arrays#equals(byte[], byte[])} on non-escaping arrays compares bytes. A wide
 * store into a virtual byte array is one entry of the wide kind followed by illegal entries, so
 * entries that differ do not prove that the bytes differ.
 */
public class VirtualByteArrayEqualsTest extends GraalCompilerTest {

    private static final long BASE = UNSAFE.arrayBaseOffset(byte[].class);

    public static boolean longAgainstBytes() {
        byte[] a = new byte[8];
        byte[] b = new byte[8];
        UNSAFE.putLong(a, BASE, 0x0101010101010101L);
        b[0] = 1;
        b[1] = 1;
        b[2] = 1;
        b[3] = 1;
        b[4] = 1;
        b[5] = 1;
        b[6] = 1;
        b[7] = 1;
        return java.util.Arrays.equals(a, b);
    }

    public static boolean longAgainstInts() {
        byte[] a = new byte[8];
        byte[] b = new byte[8];
        UNSAFE.putLong(a, BASE, 0x0000000700000007L);
        UNSAFE.putInt(b, BASE, 7);
        UNSAFE.putInt(b, BASE + 4, 7);
        return java.util.Arrays.equals(a, b);
    }

    public static boolean intAgainstBytes() {
        byte[] a = new byte[4];
        byte[] b = new byte[4];
        UNSAFE.putInt(a, BASE, 0x01010101);
        b[0] = 1;
        b[1] = 1;
        b[2] = 1;
        b[3] = 1;
        return java.util.Arrays.equals(a, b);
    }

    public static boolean bytesAgainstShort() {
        byte[] a = {1, 1};
        byte[] b = new byte[2];
        UNSAFE.putShort(b, BASE, (short) 0x0101);
        return java.util.Arrays.equals(a, b);
    }

    public static boolean sameLong(long x) {
        byte[] a = new byte[8];
        byte[] b = new byte[8];
        UNSAFE.putLong(a, BASE, x);
        UNSAFE.putLong(b, BASE, x);
        return java.util.Arrays.equals(a, b);
    }

    public static boolean differentLongs() {
        byte[] a = new byte[8];
        byte[] b = new byte[8];
        UNSAFE.putLong(a, BASE, 5L);
        UNSAFE.putLong(b, BASE, 261L);
        return java.util.Arrays.equals(a, b);
    }

    public static boolean differentBytes() {
        byte[] a = new byte[2];
        byte[] b = new byte[2];
        a[0] = 1;
        b[0] = 2;
        return java.util.Arrays.equals(a, b);
    }

    public static boolean differentFloatNaNs() {
        byte[] a = new byte[4];
        byte[] b = new byte[4];
        UNSAFE.putFloat(a, BASE, Float.intBitsToFloat(0x7fc00001));
        UNSAFE.putFloat(b, BASE, Float.intBitsToFloat(0x7fc00002));
        return java.util.Arrays.equals(a, b);
    }

    public static boolean differentDoubleNaNs() {
        byte[] a = new byte[8];
        byte[] b = new byte[8];
        UNSAFE.putDouble(a, BASE, Double.longBitsToDouble(0x7ff8000000000001L));
        UNSAFE.putDouble(b, BASE, Double.longBitsToDouble(0x7ff8000000000002L));
        return java.util.Arrays.equals(a, b);
    }

    @Test
    public void testFloatNaNs() {
        test("differentFloatNaNs");
        test("differentDoubleNaNs");
    }

    @Test
    public void testEqualBytes() {
        test("longAgainstBytes");
        test("longAgainstInts");
        test("intAgainstBytes");
        test("bytesAgainstShort");
    }

    @Test
    public void testSameLayout() {
        test("sameLong", 0x1122334455667788L);
        test("differentLongs");
        test("differentBytes");
    }

    public static boolean differentBytesAfterMismatchedSpans(long x, int y) {
        byte[] a = new byte[17];
        byte[] b = new byte[17];
        UNSAFE.putLong(a, BASE, x);
        UNSAFE.putInt(b, BASE, y);
        UNSAFE.putInt(b, BASE + 4, y);
        UNSAFE.putInt(a, BASE + 8, y);
        UNSAFE.putInt(a, BASE + 12, y);
        UNSAFE.putLong(b, BASE + 8, x);
        a[16] = 1;
        b[16] = 2;
        return java.util.Arrays.equals(a, b);
    }

    public static boolean mismatchedSpansWithEqualTail(long x, int y) {
        byte[] a = new byte[9];
        byte[] b = new byte[9];
        UNSAFE.putLong(a, BASE, x);
        UNSAFE.putInt(b, BASE, y);
        UNSAFE.putInt(b, BASE + 4, y);
        a[8] = 1;
        b[8] = 1;
        return java.util.Arrays.equals(a, b);
    }

    public static boolean sameBytesDifferentKinds() {
        byte[] a = new byte[4];
        byte[] b = new byte[4];
        UNSAFE.putInt(a, BASE, 0x3f800000);
        UNSAFE.putFloat(b, BASE, 1.0f);
        return java.util.Arrays.equals(a, b);
    }

    public static boolean differentLongStamps(long x) {
        byte[] a = new byte[8];
        byte[] b = new byte[8];
        UNSAFE.putLong(a, BASE, x & 0xff);
        UNSAFE.putLong(b, BASE, 256L);
        return java.util.Arrays.equals(a, b);
    }

    @Test
    public void testResynchronize() {
        test("differentBytesAfterMismatchedSpans", 0L, 0);
        test("differentBytesAfterMismatchedSpans", 1L, 2);
        test("mismatchedSpansWithEqualTail", 0L, 0);
        test("mismatchedSpansWithEqualTail", 1L, 0);
        test("sameBytesDifferentKinds");
    }

    @Test
    public void testWideStamps() {
        test("differentLongStamps", 0L);
        test("differentLongStamps", 255L);
    }

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        String name = graph.method().getName();
        if (name.equals("differentBytesAfterMismatchedSpans") || name.equals("differentLongStamps")) {
            assertConstantReturn(graph, 0);
        }
    }
}
