/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.test;

import static org.junit.Assert.*;

import java.lang.reflect.*;
import java.util.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;

/**
 * Tests intrinsification of {@link System#arraycopy(Object, int, Object, int, int)}.
 */
public class ArrayCopyIntrinsificationTest extends GraalCompilerTest {

    @Override
    protected InstalledCode getCode(ResolvedJavaMethod method, StructuredGraph graph) {
        int nodeCount = graph.getNodeCount();
        InstalledCode result = super.getCode(method, graph);
        boolean graphWasProcessed = nodeCount != graph.getNodeCount();
        if (graphWasProcessed) {
            if (mustIntrinsify) {
                for (Node node : graph.getNodes()) {
                    if (node instanceof Invoke) {
                        Invoke invoke = (Invoke) node;
                        Assert.assertTrue(invoke.callTarget() instanceof DirectCallTargetNode);
                        LoweredCallTargetNode directCall = (LoweredCallTargetNode) invoke.callTarget();
                        JavaMethod callee = directCall.targetMethod();
                        Assert.assertTrue(callee.getName().equals("<init>"));
                        Assert.assertTrue(getMetaAccess().lookupJavaType(ArrayIndexOutOfBoundsException.class).equals(callee.getDeclaringClass()) ||
                                        getMetaAccess().lookupJavaType(NullPointerException.class).equals(callee.getDeclaringClass()));
                    }
                }
            } else {
                boolean found = false;
                for (Node node : graph.getNodes()) {
                    if (node instanceof Invoke) {
                        Invoke invoke = (Invoke) node;
                        LoweredCallTargetNode directCall = (LoweredCallTargetNode) invoke.callTarget();
                        JavaMethod callee = directCall.targetMethod();
                        if (callee.getDeclaringClass().equals(getMetaAccess().lookupJavaType(System.class)) && callee.getName().equals("arraycopy")) {
                            found = true;
                        } else {
                            fail("found invoke to some method other than arraycopy: " + callee);
                        }
                    }
                }
                Assert.assertTrue("did not find invoke to arraycopy", found);
            }
        }
        return result;
    }

    boolean mustIntrinsify = true;

    @Test
    public void test0() {
        mustIntrinsify = false; // a generic call to arraycopy will not be intrinsified
        // Array store checks
        test("genericArraycopy", new Object(), 0, new Object[0], 0, 0);
        test("genericArraycopy", new Object[0], 0, new Object(), 0, 0);

        mustIntrinsify = true;
    }

    @Test
    public void test1() {
        String name = "intArraycopy";
        int[] src = {234, 5345, 756, 23, 8, 345, 873, 440};
        // Null checks
        test(name, null, 0, src, 0, 0);
        test(name, src, 0, null, 0, 0);
        // Bounds checks
        test(name, src, 0, src, 0, -1);
        test(name, src, 0, src, 0, src.length + 1);
    }

    @Test
    public void testByte() {
        byte[] src = {-1, 0, 1, 2, 3, 4};
        testHelper("byteArraycopy", src);
    }

    @Test
    public void testChar() {
        char[] src = "some string of chars".toCharArray();
        testHelper("charArraycopy", src);
    }

    @Test
    public void testShort() {
        short[] src = {234, 5345, 756, 23, 8, 345, 873, 440};
        testHelper("shortArraycopy", src);
    }

    @Test
    public void testInt() {
        int[] src = {234, 5345, 756, 23, 8, 345, 873, 440};
        testHelper("intArraycopy", src);
    }

    @Test
    public void testFloat() {
        float[] src = {234, 5345, 756, 23, 8, 345, 873, 440};
        testHelper("floatArraycopy", src);
    }

    @Test
    public void testLong() {
        long[] src = {234, 5345, 756, 23, 8, 345, 873, 440};
        testHelper("longArraycopy", src);
    }

    @Test
    public void testDouble() {
        double[] src = {234, 5345, 756, 23, 8, 345, 873, 440};
        testHelper("doubleArraycopy", src);
    }

    @Test
    public void testObject() {
        mustIntrinsify = false; // a generic call to arraycopy will not be intrinsified

        Object[] src = {"one", "two", "three", new ArrayList<>(), new HashMap<>()};
        testHelper("objectArraycopy", src);

        mustIntrinsify = true;
    }

    @Test
    public void testObjectExact() {
        Integer[] src = {1, 2, 3, 4};
        testHelper("objectArraycopyExact", src);
    }

    private static Object newArray(Object proto, int length) {
        assert proto != null;
        assert proto.getClass().isArray();
        return Array.newInstance(proto.getClass().getComponentType(), length);
    }

    private void testHelper(String name, Object src) {
        int srcLength = Array.getLength(src);

        // Complete array copy
        test(name, src, 0, newArray(src, srcLength), 0, srcLength);

        for (int length : new int[]{0, 1, srcLength - 1, srcLength}) {
            // Partial array copying
            test(name, src, 0, newArray(src, length), 0, length);
            test(name, src, srcLength - length, newArray(src, length), 0, length);
            test(name, src, 0, newArray(src, srcLength), 0, length);
        }
    }

    public static Object genericArraycopy(Object src, int srcPos, Object dst, int dstPos, int length) {
        System.arraycopy(src, srcPos, dst, dstPos, length);
        return dst;
    }

    public static Object[] objectArraycopy(Object[] src, int srcPos, Object[] dst, int dstPos, int length) {
        System.arraycopy(src, srcPos, dst, dstPos, length);
        return dst;
    }

    public static Object[] objectArraycopyExact(Integer[] src, int srcPos, Integer[] dst, int dstPos, int length) {
        System.arraycopy(src, srcPos, dst, dstPos, length);
        return dst;
    }

    public static boolean[] booleanArraycopy(boolean[] src, int srcPos, boolean[] dst, int dstPos, int length) {
        System.arraycopy(src, srcPos, dst, dstPos, length);
        return dst;
    }

    public static byte[] byteArraycopy(byte[] src, int srcPos, byte[] dst, int dstPos, int length) {
        System.arraycopy(src, srcPos, dst, dstPos, length);
        return dst;
    }

    public static char[] charArraycopy(char[] src, int srcPos, char[] dst, int dstPos, int length) {
        System.arraycopy(src, srcPos, dst, dstPos, length);
        return dst;
    }

    public static short[] shortArraycopy(short[] src, int srcPos, short[] dst, int dstPos, int length) {
        System.arraycopy(src, srcPos, dst, dstPos, length);
        return dst;
    }

    public static int[] intArraycopy(int[] src, int srcPos, int[] dst, int dstPos, int length) {
        System.arraycopy(src, srcPos, dst, dstPos, length);
        return dst;
    }

    public static float[] floatArraycopy(float[] src, int srcPos, float[] dst, int dstPos, int length) {
        System.arraycopy(src, srcPos, dst, dstPos, length);
        return dst;
    }

    public static long[] longArraycopy(long[] src, int srcPos, long[] dst, int dstPos, int length) {
        System.arraycopy(src, srcPos, dst, dstPos, length);
        return dst;
    }

    public static double[] doubleArraycopy(double[] src, int srcPos, double[] dst, int dstPos, int length) {
        System.arraycopy(src, srcPos, dst, dstPos, length);
        return dst;
    }

}
