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
package com.oracle.graal.truffle.test;

import org.junit.*;

import com.oracle.graal.api.runtime.*;
import com.oracle.graal.compiler.test.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.runtime.*;
import com.oracle.graal.truffle.substitutions.*;
import com.oracle.truffle.api.*;

public class ExactMathTest extends GraalCompilerTest {

    private static boolean substitutionsInstalled;

    public ExactMathTest() {
        if (!substitutionsInstalled) {
            Replacements replacements = Graal.getRequiredCapability(RuntimeProvider.class).getHostBackend().getProviders().getReplacements();
            replacements.registerSubstitutions(ExactMathSubstitutions.class);
            substitutionsInstalled = true;
        }
    }

    @Test
    public void testAdd() {
        test("add", 1, 2);
        test("add", Integer.MAX_VALUE, 2);
    }

    @Test
    public void testMul() {
        test("mul", 1, 2);
        test("mul", Integer.MAX_VALUE, 2);
    }

    @Test
    public void testSub() {
        test("sub", 1, 2);
        test("sub", Integer.MIN_VALUE, 2);
    }

    @Test
    public void testMulHigh() {
        test("mulHigh", 7, 15);
        test("mulHigh", Integer.MAX_VALUE, 15);
        test("mulHigh", Integer.MIN_VALUE, 15);
    }

    @Test
    public void testMulHighUnsigned() {
        test("mulHighUnsigned", 7, 15);
        test("mulHighUnsigned", Integer.MAX_VALUE, 15);
        test("mulHighUnsigned", Integer.MIN_VALUE, 15);
    }

    @Test
    public void testLongAdd() {
        test("longAdd", (long) Integer.MAX_VALUE, 2L);
        test("longAdd", Long.MAX_VALUE, 2L);
    }

    @Test
    public void testLongMul() {
        test("longMul", (long) Integer.MAX_VALUE, 2L);
        test("longMul", (long) Integer.MIN_VALUE, 2L);
        test("longMul", Long.MAX_VALUE, 2L);
    }

    @Test
    public void testLongSub() {
        test("longSub", (long) Integer.MIN_VALUE, 2L);
        test("longSub", Long.MIN_VALUE, 2L);
    }

    @Test
    public void testLongMulHigh() {
        test("longMulHigh", 7L, 15L);
        test("longMulHigh", Long.MAX_VALUE, 15L);
        test("longMulHigh", Long.MIN_VALUE, 15L);
    }

    @Test
    public void testLongMulHighUnsigned() {
        test("longMulHighUnsigned", 7L, 15L);
        test("longMulHighUnsigned", Long.MAX_VALUE, 15L);
        test("longMulHighUnsigned", Long.MIN_VALUE, 15L);
    }

    public static int add(int a, int b) {
        return ExactMath.addExact(a, b);
    }

    public static int mul(int a, int b) {
        return ExactMath.multiplyExact(a, b);
    }

    public static int sub(int a, int b) {
        return ExactMath.subtractExact(a, b);
    }

    public static int mulHigh(int a, int b) {
        return ExactMath.multiplyHigh(a, b);
    }

    public static int mulHighUnsigned(int a, int b) {
        return ExactMath.multiplyHighUnsigned(a, b);
    }

    public static long longAdd(long a, long b) {
        return ExactMath.addExact(a, b);
    }

    public static long longMul(long a, long b) {
        return ExactMath.multiplyExact(a, b);
    }

    public static long longSub(long a, long b) {
        return ExactMath.subtractExact(a, b);
    }

    public static long longMulHigh(long a, long b) {
        return ExactMath.multiplyHigh(a, b);
    }

    public static long longMulHighUnsigned(long a, long b) {
        return ExactMath.multiplyHighUnsigned(a, b);
    }
}
