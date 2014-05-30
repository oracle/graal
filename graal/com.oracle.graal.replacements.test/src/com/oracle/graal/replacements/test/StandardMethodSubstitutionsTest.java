/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.replacements.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.calc.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.nodes.*;

/**
 * Tests the VM independent {@link MethodSubstitution}s.
 */
public class StandardMethodSubstitutionsTest extends MethodSubstitutionTest {

    @Test
    public void testMathSubstitutions() {
        assertInGraph(assertNotInGraph(test("mathAbs"), IfNode.class), MathIntrinsicNode.class);     // Java
        test("math");

        double value = 34567.891D;
        assertDeepEquals(Math.sqrt(value), MathSubstitutionsX86.sqrt(value));
        assertDeepEquals(Math.log(value), MathSubstitutionsX86.log(value));
        assertDeepEquals(Math.log10(value), MathSubstitutionsX86.log10(value));
        assertDeepEquals(Math.sin(value), MathSubstitutionsX86.sin(value));
        assertDeepEquals(Math.cos(value), MathSubstitutionsX86.cos(value));
        assertDeepEquals(Math.tan(value), MathSubstitutionsX86.tan(value));
    }

    @SuppressWarnings("all")
    public static double mathAbs(double value) {
        return Math.abs(value);
    }

    @SuppressWarnings("all")
    public static double math(double value) {
        return Math.sqrt(value) + Math.log(value) + Math.log10(value) + Math.sin(value) + Math.cos(value) + Math.tan(value);
        // Math.exp(value) +
        // Math.pow(value, 13);
    }

    private static Object executeVarargsSafe(InstalledCode code, Object... args) {
        try {
            return code.executeVarargs(args);
        } catch (InvalidInstalledCodeException e) {
            throw new RuntimeException(e);
        }
    }

    private static Object invokeSafe(Method method, Object... args) {
        method.setAccessible(true);
        try {
            Object result = method.invoke(null, args);
            return result;
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public void testSubstitution(String testMethodName, Class<?> intrinsicClass, Class<?> holder, String methodName, boolean optional, Object... args) {
        Method realMethod = getMethod(holder, methodName);
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
        for (Object l : args) {
            // Verify that the original method and the substitution produce the same value
            assertDeepEquals(invokeSafe(testMethod, l), invokeSafe(realMethod, l));
            // Verify that the generated code and the original produce the same value
            assertDeepEquals(executeVarargsSafe(code, l), invokeSafe(realMethod, l));
        }
    }

    @Test
    public void testIntegerSubstitutions() {
        Object[] args = new Object[]{Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};

        testSubstitution("integerReverseBytes", ReverseBytesNode.class, Integer.class, "reverseBytes", false, args);
        testSubstitution("integerNumberOfLeadingZeros", BitScanReverseNode.class, Integer.class, "numberOfLeadingZeros", true, args);
        testSubstitution("integerNumberOfTrailingZeros", BitScanForwardNode.class, Integer.class, "numberOfTrailingZeros", false, args);
        testSubstitution("integerBitCount", BitCountNode.class, Integer.class, "bitCount", true, args);
    }

    @SuppressWarnings("all")
    public static int integerReverseBytes(int value) {
        return Integer.reverseBytes(value);
    }

    @SuppressWarnings("all")
    public static int integerNumberOfLeadingZeros(int value) {
        return Integer.numberOfLeadingZeros(value);
    }

    @SuppressWarnings("all")
    public static int integerNumberOfTrailingZeros(int value) {
        return Integer.numberOfTrailingZeros(value);
    }

    @SuppressWarnings("all")
    public static int integerBitCount(int value) {
        return Integer.bitCount(value);
    }

    @Test
    public void testLongSubstitutions() {
        Object[] args = new Object[]{Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE};

        testSubstitution("longReverseBytes", ReverseBytesNode.class, Long.class, "reverseBytes", false, args);
        testSubstitution("longNumberOfLeadingZeros", BitScanReverseNode.class, Long.class, "numberOfLeadingZeros", true, args);
        testSubstitution("longNumberOfTrailingZeros", BitScanForwardNode.class, Long.class, "numberOfTrailingZeros", false, args);
        testSubstitution("longBitCount", BitCountNode.class, Long.class, "bitCount", true, args);
    }

    @SuppressWarnings("all")
    public static long longReverseBytes(long value) {
        return Long.reverseBytes(value);
    }

    @SuppressWarnings("all")
    public static int longNumberOfLeadingZeros(long value) {
        return Long.numberOfLeadingZeros(value);
    }

    @SuppressWarnings("all")
    public static int longNumberOfTrailingZeros(long value) {
        return Long.numberOfTrailingZeros(value);
    }

    @SuppressWarnings("all")
    public static int longBitCount(long value) {
        return Long.bitCount(value);
    }

    @Test
    public void testFloatSubstitutions() {
        assertInGraph(test("floatToIntBits"), ReinterpretNode.class); // Java
        test("intBitsToFloat");
    }

    @SuppressWarnings("all")
    public static int floatToIntBits(float value) {
        return Float.floatToIntBits(value);
    }

    @SuppressWarnings("all")
    public static float intBitsToFloat(int value) {
        return Float.intBitsToFloat(value);
    }

    @Test
    public void testDoubleSubstitutions() {
        assertInGraph(test("doubleToLongBits"), ReinterpretNode.class); // Java
        test("longBitsToDouble");
    }

    @SuppressWarnings("all")
    public static long doubleToLongBits(double value) {
        return Double.doubleToLongBits(value);
    }

    @SuppressWarnings("all")
    public static double longBitsToDouble(long value) {
        return Double.longBitsToDouble(value);
    }
}
