/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.java.UnsafeCompareAndSwapNode;
import jdk.graal.compiler.test.AddExports;
import jdk.internal.misc.Unsafe;

/**
 * Test to ensure that the virtualization of
 * {@link Unsafe#compareAndSetBoolean(Object, long, boolean, boolean)} operations on arrays work
 * properly.
 */
@AddExports("java.base/jdk.internal.misc")
public class UnsafeCompareAndSwapTest extends GraalCompilerTest {

    @Override
    protected Object[] getArgumentToBind() {
        return constantArgs;
    }

    Object[] constantArgs;

    @Override
    protected void checkHighTierGraph(StructuredGraph graph) {
        super.checkHighTierGraph(graph);
        Assert.assertEquals(0, graph.getNodes().filter(UnsafeCompareAndSwapNode.class).count());
    }

    static boolean swapBooleanSnippet(boolean b1, boolean b2, boolean b3, boolean trueVal, boolean falseVal) {
        boolean[] f = new boolean[]{b1, b2, b3};
        final int index = 2;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(boolean[].class);
        long indexScale = unsafe.arrayIndexScale(boolean[].class);
        long offset = baseOffset + index * indexScale;
        return unsafe.compareAndSetBoolean(f, offset, trueVal, falseVal);
    }

    @Test
    public void swapBoolean() {
        Random r = getRandomInstance();
        test("swapBooleanSnippet", r.nextBoolean(), r.nextBoolean(), r.nextBoolean(), r.nextBoolean(), r.nextBoolean());
        constantArgs = new Object[]{r.nextBoolean(), r.nextBoolean(), r.nextBoolean(), r.nextBoolean(), r.nextBoolean()};
        test("swapBooleanSnippet", constantArgs);
        constantArgs = null;
    }

    static boolean swapByteSnippet(byte b1, byte b2, byte b3, byte trueVal, byte falseVal) {
        byte[] f = new byte[]{b1, b2, b3};
        final int index = 2;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(byte[].class);
        long indexScale = unsafe.arrayIndexScale(byte[].class);
        long offset = baseOffset + index * indexScale;
        return unsafe.compareAndSetByte(f, offset, trueVal, falseVal);
    }

    @Test
    public void swapByte() {
        Random r = getRandomInstance();
        test("swapByteSnippet", (byte) r.nextInt(), (byte) r.nextInt(), (byte) r.nextInt(), (byte) r.nextInt(), (byte) r.nextInt());
        constantArgs = new Object[]{(byte) r.nextInt(), (byte) r.nextInt(), (byte) r.nextInt(), (byte) r.nextInt(), (byte) r.nextInt()};
        test("swapByteSnippet", constantArgs);
        constantArgs = null;
    }

    static boolean swapCharSnippet(char c1, char c2, char c3, char trueVal, char falseVal) {
        char[] f = new char[]{c1, c2, c3};
        final int index = 2;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(char[].class);
        long indexScale = unsafe.arrayIndexScale(char[].class);
        long offset = baseOffset + index * indexScale;
        return unsafe.compareAndSetChar(f, offset, trueVal, falseVal);
    }

    @Test
    public void swapChar() {
        Random r = getRandomInstance();
        test("swapCharSnippet", (char) r.nextInt(), (char) r.nextInt(), (char) r.nextInt(), (char) r.nextInt(), (char) r.nextInt());
        constantArgs = new Object[]{(char) r.nextInt(), (char) r.nextInt(), (char) r.nextInt(), (char) r.nextInt(), (char) r.nextInt()};
        test("swapCharSnippet", constantArgs);
        constantArgs = null;
    }

    static boolean swapShortSnippet(short s1, short s2, short s3, short trueVal, short falseVal) {
        short[] f = new short[]{s1, s2, s3};
        final int index = 2;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(short[].class);
        long indexScale = unsafe.arrayIndexScale(short[].class);
        long offset = baseOffset + index * indexScale;
        return unsafe.compareAndSetShort(f, offset, trueVal, falseVal);
    }

    @Test
    public void swapShort() {
        Random r = getRandomInstance();
        test("swapShortSnippet", (short) r.nextInt(), (short) r.nextInt(), (short) r.nextInt(), (short) r.nextInt(), (short) r.nextInt());
        constantArgs = new Object[]{(short) r.nextInt(), (short) r.nextInt(), (short) r.nextInt(), (short) r.nextInt(), (short) r.nextInt()};
        test("swapShortSnippet", constantArgs);
        constantArgs = null;
    }

    static boolean swapFloatSnippet(float f1, float f2, float f3, float trueVal, float falseVal) {
        float[] f = new float[]{f1, f2, f3};
        final int index = 2;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(float[].class);
        long indexScale = unsafe.arrayIndexScale(float[].class);
        long offset = baseOffset + index * indexScale;
        return unsafe.compareAndSetFloat(f, offset, trueVal, falseVal);
    }

    @Test
    public void swapFloat() {
        Random r = getRandomInstance();
        test("swapFloatSnippet", r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat());
        constantArgs = new Object[]{r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat(), r.nextFloat()};
        test("swapFloatSnippet", constantArgs);
        constantArgs = null;
    }

    static boolean swapDoubleSnippet(double d1, double d2, double d3, double trueVal, double falseVal) {
        double[] f = new double[]{d1, d2, d3};
        final int index = 2;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(double[].class);
        long indexScale = unsafe.arrayIndexScale(double[].class);
        long offset = baseOffset + index * indexScale;
        return unsafe.compareAndSetDouble(f, offset, trueVal, falseVal);
    }

    @Test
    public void swapDouble() {
        Random r = getRandomInstance();
        test("swapDoubleSnippet", r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble());
        constantArgs = new Object[]{r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble(), r.nextDouble()};
        test("swapDoubleSnippet", constantArgs);
        constantArgs = null;
    }

    static boolean swapIntSnippet(int i1, int i2, int i3, int trueVal, int falseVal) {
        int[] f = new int[]{i1, i2, i3};
        final int index = 2;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(int[].class);
        long indexScale = unsafe.arrayIndexScale(int[].class);
        long offset = baseOffset + index * indexScale;
        return unsafe.compareAndSetInt(f, offset, trueVal, falseVal);
    }

    @Test
    public void swapInt() {
        Random r = getRandomInstance();
        test("swapIntSnippet", r.nextInt(), r.nextInt(), r.nextInt(), r.nextInt(), r.nextInt());
        constantArgs = new Object[]{r.nextInt(), r.nextInt(), r.nextInt(), r.nextInt(), r.nextInt()};
        test("swapIntSnippet", constantArgs);
        constantArgs = null;
    }

    static boolean swapLongSnippet(long l1, long l2, long l3, long trueVal, long falseVal) {
        long[] f = new long[]{l1, l2, l3};
        final int index = 2;
        var unsafe = jdk.internal.misc.Unsafe.getUnsafe();
        long baseOffset = unsafe.arrayBaseOffset(long[].class);
        long indexScale = unsafe.arrayIndexScale(long[].class);
        long offset = baseOffset + index * indexScale;
        return unsafe.compareAndSetLong(f, offset, trueVal, falseVal);
    }

    @Test
    public void swapLong() {
        Random r = getRandomInstance();
        test("swapLongSnippet", r.nextLong(), r.nextLong(), r.nextLong(), r.nextLong(), r.nextLong());
        constantArgs = new Object[]{r.nextLong(), r.nextLong(), r.nextLong(), r.nextLong(), r.nextLong()};
        test("swapLongSnippet", constantArgs);
        constantArgs = null;
    }
}
