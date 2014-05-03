/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.test;

import java.lang.reflect.*;
import java.util.*;

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.common.inlining.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.nodes.*;
import com.oracle.graal.virtual.phases.ea.*;

/**
 * Tests {@link ArraysSubstitutions}.
 */
public class ArraysSubstitutionsTest extends MethodSubstitutionTest {

    private static Object executeVarargsSafe(InstalledCode code, Object... args) {
        try {
            return code.executeVarargs(args);
        } catch (InvalidInstalledCodeException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object invokeSafe(Method method, Object receiver, Object... args) {
        method.setAccessible(true);
        try {
            Object result = method.invoke(receiver, args);
            return result;
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public void testSubstitution(String testMethodName, Class<?> intrinsicClass, Class<?> holder, String methodName, Class<?>[] parameterTypes, boolean optional, Object[] args1, Object[] args2) {
        Method realMethod = getMethod(holder, methodName, parameterTypes);
        Method testMethod = getMethod(testMethodName);
        StructuredGraph graph = test(testMethodName);

        // Check to see if the resulting graph contains the expected node
        StructuredGraph replacement = getReplacements().getMethodSubstitution(getMetaAccess().lookupJavaMethod(realMethod));
        if (replacement == null && !optional) {
            assertInGraph(graph, intrinsicClass);
        }

        // Force compilation
        InstalledCode code = getCode(getMetaAccess().lookupJavaMethod(testMethod), parse(testMethod));
        assert optional || code != null;

        for (int i = 0; i < args1.length; i++) {
            Object arg1 = args1[i];
            Object arg2 = args2[i];
            // Verify that the original method and the substitution produce the same value
            assertEquals(invokeSafe(testMethod, null, arg1, arg2), invokeSafe(realMethod, null, arg1, arg2));
            // Verify that the generated code and the original produce the same value
            assertEquals(executeVarargsSafe(code, arg1, arg2), invokeSafe(realMethod, null, arg1, arg2));
        }
    }

    private static final int N = 10;

    @Test
    public void testEqualsBoolean() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new boolean[i];
            args2[n] = new boolean[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            boolean[] a2 = new boolean[i];
            if (i > 0) {
                a2[i - 1] = true;
            }
            args1[n] = new boolean[i];
            args2[n] = a2;
        }
        Class<?>[] parameterTypes = new Class<?>[]{boolean[].class, boolean[].class};
        testSubstitution("arraysEqualsBoolean", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsBoolean(boolean[] a, boolean[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsByte() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new byte[i];
            args2[n] = new byte[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            byte[] a2 = new byte[i];
            if (i > 0) {
                a2[i - 1] = 1;
            }
            args1[n] = new byte[i];
            args2[n] = a2;
        }

        Class<?>[] parameterTypes = new Class<?>[]{byte[].class, byte[].class};
        testSubstitution("arraysEqualsByte", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsByte(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsChar() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new char[i];
            args2[n] = new char[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            char[] a2 = new char[i];
            if (i > 0) {
                a2[i - 1] = 1;
            }
            args1[n] = new char[i];
            args2[n] = a2;
        }

        Class<?>[] parameterTypes = new Class<?>[]{char[].class, char[].class};
        testSubstitution("arraysEqualsChar", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsChar(char[] a, char[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsShort() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new short[i];
            args2[n] = new short[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            short[] a2 = new short[i];
            if (i > 0) {
                a2[i - 1] = 1;
            }
            args1[n] = new short[i];
            args2[n] = a2;
        }

        Class<?>[] parameterTypes = new Class<?>[]{short[].class, short[].class};
        testSubstitution("arraysEqualsShort", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsShort(short[] a, short[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsInt() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new int[i];
            args2[n] = new int[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            int[] a2 = new int[i];
            if (i > 0) {
                a2[i - 1] = 1;
            }
            args1[n] = new int[i];
            args2[n] = a2;
        }

        Class<?>[] parameterTypes = new Class<?>[]{int[].class, int[].class};
        testSubstitution("arraysEqualsInt", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsInt(int[] a, int[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsLong() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new long[i];
            args2[n] = new long[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            long[] a2 = new long[i];
            if (i > 0) {
                a2[i - 1] = 1;
            }
            args1[n] = new long[i];
            args2[n] = a2;
        }

        Class<?>[] parameterTypes = new Class<?>[]{long[].class, long[].class};
        testSubstitution("arraysEqualsLong", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsLong(long[] a, long[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsFloat() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new float[i];
            args2[n] = new float[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            float[] a2 = new float[i];
            if (i > 0) {
                a2[i - 1] = 1;
            }
            args1[n] = new float[i];
            args2[n] = a2;
        }

        Class<?>[] parameterTypes = new Class<?>[]{float[].class, float[].class};
        testSubstitution("arraysEqualsFloat", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsFloat(float[] a, float[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsDouble() {
        Object[] args1 = new Object[N];
        Object[] args2 = new Object[N];
        int n = 0;

        // equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            args1[n] = new double[i];
            args2[n] = new double[i];
        }

        // non-equal arrays
        for (int i = 0; i < N / 2; i++, n++) {
            double[] a2 = new double[i];
            if (i > 0) {
                a2[i - 1] = 1;
            }
            args1[n] = new double[i];
            args2[n] = a2;
        }

        Class<?>[] parameterTypes = new Class<?>[]{double[].class, double[].class};
        testSubstitution("arraysEqualsDouble", ArrayEqualsNode.class, Arrays.class, "equals", parameterTypes, false, args1, args2);
    }

    @SuppressWarnings("all")
    public static boolean arraysEqualsDouble(double[] a, double[] b) {
        return Arrays.equals(a, b);
    }

    @Test
    public void testEqualsNodeGVN() {
        test("testEqualsNodeGVNSnippet", true);
    }

    public static int[] intArrayCompare = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
    public static int[] intArray;

    public static boolean testEqualsNodeGVNSnippet(boolean b) {
        int[] newIntArray = new int[]{0, 2, 3, 4, 5, 6, 7, 8, 9};
        intArray = newIntArray;

        if (b) {
            newIntArray[0] = 1;
            return Arrays.equals(newIntArray, intArrayCompare);
        } else {
            newIntArray[0] = 1;
            return Arrays.equals(newIntArray, intArrayCompare);
        }
    }

    @Test
    public void testConstants() {
        test("testConstantsSnippet");
    }

    public static final int[] constantArray1 = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};
    public static final int[] constantArray2 = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9};

    public static boolean testConstantsSnippet() {
        constantArray2[0] = 10;
        try {
            return Arrays.equals(constantArray1, constantArray2);
        } finally {
            constantArray2[0] = 1;
        }
    }

    @Test
    public void testCanonicalLength() {
        StructuredGraph graph = parse("testCanonicalLengthSnippet");
        Assumptions assumptions = new Assumptions(false);
        HighTierContext context = new HighTierContext(getProviders(), assumptions, null, getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        new InliningPhase(new CanonicalizerPhase(true)).apply(graph, context);
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), assumptions));

        Assert.assertTrue(graph.getNodes(ReturnNode.class).first().result().asConstant().asLong() == 0);
    }

    public static final int[] constantArray3 = new int[]{1, 2, 3};

    public static boolean testCanonicalLengthSnippet() {
        return Arrays.equals(constantArray1, constantArray3);
    }

    @Test
    public void testCanonicalEqual() {
        StructuredGraph graph = parse("testCanonicalEqualSnippet");
        Assumptions assumptions = new Assumptions(false);
        HighTierContext context = new HighTierContext(getProviders(), assumptions, null, getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        new InliningPhase(new CanonicalizerPhase(true)).apply(graph, context);
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), assumptions));

        Assert.assertTrue(graph.getNodes(ReturnNode.class).first().result().asConstant().asLong() == 1);
    }

    public static boolean testCanonicalEqualSnippet() {
        return Arrays.equals(constantArray1, constantArray1);
    }

    @Test
    public void testVirtualEqual() {
        StructuredGraph graph = parse("testVirtualEqualSnippet");
        Assumptions assumptions = new Assumptions(false);
        HighTierContext context = new HighTierContext(getProviders(), assumptions, null, getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        new InliningPhase(new CanonicalizerPhase(true)).apply(graph, context);
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), assumptions));
        new PartialEscapePhase(false, new CanonicalizerPhase(false)).apply(graph, context);
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), assumptions));

        Assert.assertTrue(graph.getNodes(ReturnNode.class).first().result().asConstant().asLong() == 1);
    }

    public static boolean testVirtualEqualSnippet() {
        int[] array1 = new int[]{1, 2, 3, 4};
        int[] array2 = new int[]{1, 2, 3, 4};
        return Arrays.equals(array1, array2);
    }

    @Test
    public void testVirtualNotEqual() {
        StructuredGraph graph = parse("testVirtualNotEqualSnippet");
        Assumptions assumptions = new Assumptions(false);
        HighTierContext context = new HighTierContext(getProviders(), assumptions, null, getDefaultGraphBuilderSuite(), OptimisticOptimizations.ALL);
        new InliningPhase(new CanonicalizerPhase(true)).apply(graph, context);
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), assumptions));
        new PartialEscapePhase(false, new CanonicalizerPhase(false)).apply(graph, context);
        new CanonicalizerPhase(true).apply(graph, new PhaseContext(getProviders(), assumptions));

        Assert.assertTrue(graph.getNodes(ReturnNode.class).first().result().asConstant().asLong() == 0);
    }

    public static boolean testVirtualNotEqualSnippet(int x) {
        int[] array1 = new int[]{1, 2, 100, x};
        int[] array2 = new int[]{1, 2, 3, 4};
        return Arrays.equals(array1, array2);
    }
}
