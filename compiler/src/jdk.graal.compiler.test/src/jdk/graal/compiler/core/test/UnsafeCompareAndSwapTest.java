/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import org.junit.Test;

import jdk.graal.compiler.test.AddExports;

@AddExports("java.base/jdk.internal.misc")
public class UnsafeCompareAndSwapTest extends GraalCompilerTest {

    static void swapBooleanSnippet() {
        boolean[] f = new boolean[100];
        final int index = 12;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(boolean[].class);
        long indexScale = unsafe.arrayIndexScale(boolean[].class);
        long offset = baseOffset + index * indexScale;
        unsafe.compareAndSetBoolean(f, offset, true, false);
    }

    @Test
    public void swapBoolean() {
        test("swapBooleanSnippet");
    }

    static void swapByteSnippet() {
        byte[] f = new byte[100];
        final int index = 12;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(byte[].class);
        long indexScale = unsafe.arrayIndexScale(byte[].class);
        long offset = baseOffset + index * indexScale;
        unsafe.compareAndSetByte(f, offset, (byte) 2, (byte) 3);
    }

    @Test
    public void swapByte() {
        test("swapByteSnippet");
    }

    static void swapCharSnippet() {
        char[] f = new char[100];
        final int index = 12;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(char[].class);
        long indexScale = unsafe.arrayIndexScale(char[].class);
        long offset = baseOffset + index * indexScale;
        unsafe.compareAndSetChar(f, offset, 'b', 'a');
    }

    @Test
    public void swapChar() {
        test("swapCharSnippet");
    }

    static void swapShortSnippet() {
        short[] f = new short[100];
        final int index = 12;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(short[].class);
        long indexScale = unsafe.arrayIndexScale(short[].class);
        long offset = baseOffset + index * indexScale;
        unsafe.compareAndSetShort(f, offset, (short) 2, (short) 3);
    }

    @Test
    public void swapShort() {
        test("swapShortSnippet");
    }

    static void swapFloatSnippet() {
        float[] f = new float[100];
        final int index = 12;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(float[].class);
        long indexScale = unsafe.arrayIndexScale(float[].class);
        long offset = baseOffset + index * indexScale;
        unsafe.compareAndSetFloat(f, offset, 2f, 3f);
    }

    @Test
    public void swapFloat() {
        test("swapFloatSnippet");
    }

    static void swapDoubleSnippet() {
        double[] f = new double[100];
        final int index = 12;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(double[].class);
        long indexScale = unsafe.arrayIndexScale(double[].class);
        long offset = baseOffset + index * indexScale;
        unsafe.compareAndSetDouble(f, offset, 2D, 3D);
    }

    @Test
    public void swapDouble() {
        test("swapDoubleSnippet");
    }

    static void swapIntSnippet() {
        int[] f = new int[100];
        final int index = 12;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(int[].class);
        long indexScale = unsafe.arrayIndexScale(int[].class);
        long offset = baseOffset + index * indexScale;
        unsafe.compareAndSetInt(f, offset, 2, 3);
    }

    @Test
    public void swapInt() {
        test("swapIntSnippet");
    }

    static void swapLongSnippet() {
        long[] f = new long[100];
        final int index = 12;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(long[].class);
        long indexScale = unsafe.arrayIndexScale(long[].class);
        long offset = baseOffset + index * indexScale;
        unsafe.compareAndSetLong(f, offset, 2, 3);
    }

    @Test
    public void swapLong() {
        test("swapLongSnippet");
    }
}
