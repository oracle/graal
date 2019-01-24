/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.test;

import org.graalvm.compiler.core.test.GraalCompilerTest;
import org.graalvm.compiler.nodes.graphbuilderconf.InvocationPlugins;
import org.graalvm.compiler.truffle.compiler.substitutions.TruffleGraphBuilderPlugins;
import org.junit.Test;

import com.oracle.truffle.api.ExactMath;

public class ExactMathTest extends GraalCompilerTest {

    @Override
    protected void registerInvocationPlugins(InvocationPlugins invocationPlugins) {
        TruffleGraphBuilderPlugins.registerExactMathPlugins(invocationPlugins, getMetaAccess());
        super.registerInvocationPlugins(invocationPlugins);
    }

    @Test
    public void testAdd() {
        test("add", 1, 2);
        test("add", Integer.MAX_VALUE, 2);
        test("add", Integer.MIN_VALUE, -1);
        test("add", -1, 2);
    }

    @Test
    public void testMul() {
        test("mul", 1, 2);
        test("mul", -1, 2);
        test("mul", Integer.MIN_VALUE, 1);
        test("mul", Integer.MIN_VALUE, 2);
        test("mul", Integer.MIN_VALUE, Integer.MIN_VALUE);
        test("mul", Integer.MAX_VALUE, 1);
        test("mul", Integer.MAX_VALUE, 2);
        test("mul", Integer.MAX_VALUE, Integer.MAX_VALUE);
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
        test("mulHighUnsigned", 15, Integer.MAX_VALUE);
        test("mulHighUnsigned", Integer.MAX_VALUE, Integer.MAX_VALUE);
        test("mulHighUnsigned", 15, Integer.MIN_VALUE);
        test("mulHighUnsigned", Integer.MIN_VALUE, 15);
        test("mulHighUnsigned", Integer.MIN_VALUE, Integer.MIN_VALUE);
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
        test("longMul", Long.MAX_VALUE, 1L);
        test("longMul", Long.MAX_VALUE, Long.MAX_VALUE);
        test("longMul", Long.MIN_VALUE, Long.MIN_VALUE);
        test("longMul", Long.MIN_VALUE, Long.MAX_VALUE);
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
        test("longMulHigh", 15L, Long.MAX_VALUE);
        test("longMulHigh", Long.MAX_VALUE, Long.MAX_VALUE);
        test("longMulHigh", Long.MIN_VALUE, 15L);
        test("longMulHigh", 15L, Long.MIN_VALUE);
        test("longMulHigh", Long.MIN_VALUE, Long.MIN_VALUE);
    }

    @Test
    public void testLongMulHighUnsigned() {
        test("longMulHighUnsigned", 7L, 15L);
        test("longMulHighUnsigned", Long.MAX_VALUE, 15L);
        test("longMulHighUnsigned", Long.MIN_VALUE, 15L);
    }

    public static int add(int a, int b) {
        return Math.addExact(a, b);
    }

    public static int mul(int a, int b) {
        return Math.multiplyExact(a, b);
    }

    public static int sub(int a, int b) {
        return Math.subtractExact(a, b);
    }

    public static int mulHigh(int a, int b) {
        return ExactMath.multiplyHigh(a, b);
    }

    public static int mulHighUnsigned(int a, int b) {
        return ExactMath.multiplyHighUnsigned(a, b);
    }

    public static long longAdd(long a, long b) {
        return Math.addExact(a, b);
    }

    public static long longMul(long a, long b) {
        return Math.multiplyExact(a, b);
    }

    public static long longSub(long a, long b) {
        return Math.subtractExact(a, b);
    }

    public static long longMulHigh(long a, long b) {
        return ExactMath.multiplyHigh(a, b);
    }

    public static long longMulHighUnsigned(long a, long b) {
        return ExactMath.multiplyHighUnsigned(a, b);
    }
}
