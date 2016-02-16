/*
 * Copyright (c) 2007, 2016, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.jtt.bytecode;

import org.junit.Test;

import com.oracle.graal.jtt.JTTTest;

public class BC_fdiv extends JTTTest {

    public static float test(float a, float b) {
        return a / b;
    }

    // 0.0, -0.0, 1.0, -1.0

    @Test
    public void run0() {
        runTest("test", 0.0f, 0.0f);
    }

    @Test
    public void run1() {
        runTest("test", -0.0f, 0.0f);
    }

    @Test
    public void run2() {
        runTest("test", 0.0f, -0.0f);
    }

    @Test
    public void run3() {
        runTest("test", -0.0f, -0.0f);
    }

    @Test
    public void run4() {
        runTest("test", -1.0f, 0.0f);
    }

    @Test
    public void run5() {
        runTest("test", 0.0f, -1.0f);
    }

    @Test
    public void run6() {
        runTest("test", -1.0f, -1.0f);
    }

    @Test
    public void run7() {
        runTest("test", -1.0f, -0.0f);
    }

    @Test
    public void run8() {
        runTest("test", -0.0f, -1.0f);
    }

    @Test
    public void run9() {
        runTest("test", 311.0f, 0.0f);
    }

    @Test
    public void run10() {
        runTest("test", 0.0f, 311.0f);
    }

    @Test
    public void run11() {
        runTest("test", 311.0f, -0.0f);
    }

    @Test
    public void run12() {
        runTest("test", -0.0f, 311.0f);
    }

    // POSITIVE_INFINITY

    @Test
    public void runPositiveInfinity0() {
        runTest("test", Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    @Test
    public void runPositiveInfinity1() {
        runTest("test", Float.POSITIVE_INFINITY, 0.0f);
    }

    @Test
    public void runPositiveInfinity2() {
        runTest("test", Float.POSITIVE_INFINITY, -0.0f);
    }

    @Test
    public void runPositiveInfinity3() {
        runTest("test", Float.POSITIVE_INFINITY, 1.0f);
    }

    @Test
    public void runPositiveInfinity4() {
        runTest("test", Float.POSITIVE_INFINITY, -1.0f);
    }

    @Test
    public void runPositiveInfinity5() {
        runTest("test", Float.POSITIVE_INFINITY, 123.0f);
    }

    @Test
    public void runPositiveInfinity6() {
        runTest("test", Float.POSITIVE_INFINITY, -123.0f);
    }

    @Test
    public void runPositiveInfinity7() {
        runTest("test", 0.0f, Float.POSITIVE_INFINITY);
    }

    @Test
    public void runPositiveInfinity8() {
        runTest("test", -0.0f, Float.POSITIVE_INFINITY);
    }

    @Test
    public void runPositiveInfinity9() {
        runTest("test", 1.0f, Float.POSITIVE_INFINITY);
    }

    @Test
    public void runPositiveInfinity10() {
        runTest("test", -1.0f, Float.POSITIVE_INFINITY);
    }

    @Test
    public void runPositiveInfinity11() {
        runTest("test", 123.0f, Float.POSITIVE_INFINITY);
    }

    @Test
    public void runPositiveInfinity12() {
        runTest("test", 1123.0f, Float.POSITIVE_INFINITY);
    }

    // NEGATIVE_INFINITY

    @Test
    public void runNegativeInfinity0() {
        runTest("test", Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
    }

    @Test
    public void runNegativeInfinity1() {
        runTest("test", Float.NEGATIVE_INFINITY, 0.0f);
    }

    @Test
    public void runNegativeInfinity2() {
        runTest("test", Float.NEGATIVE_INFINITY, -0.0f);
    }

    @Test
    public void runNegativeInfinity3() {
        runTest("test", Float.NEGATIVE_INFINITY, 1.0f);
    }

    @Test
    public void runNegativeInfinity4() {
        runTest("test", Float.NEGATIVE_INFINITY, -1.0f);
    }

    @Test
    public void runNegativeInfinity5() {
        runTest("test", Float.NEGATIVE_INFINITY, 234.0f);
    }

    @Test
    public void runNegativeInfinity6() {
        runTest("test", Float.NEGATIVE_INFINITY, -234.0f);
    }

    @Test
    public void runNegativeInfinity7() {
        runTest("test", 0.0f, Float.NEGATIVE_INFINITY);
    }

    @Test
    public void runNegativeInfinity8() {
        runTest("test", -0.0f, Float.NEGATIVE_INFINITY);
    }

    @Test
    public void runNegativeInfinity9() {
        runTest("test", 1.0f, Float.NEGATIVE_INFINITY);
    }

    @Test
    public void runNegativeInfinity10() {
        runTest("test", -1.0f, Float.NEGATIVE_INFINITY);
    }

    @Test
    public void runNegativeInfinity11() {
        runTest("test", 234.0f, Float.NEGATIVE_INFINITY);
    }

    @Test
    public void runNegativeInfinity12() {
        runTest("test", -234.0f, Float.NEGATIVE_INFINITY);
    }

    // Nan

    @Test
    public void runNaN0() {
        runTest("test", Float.NaN, Float.NaN);
    }

    @Test
    public void runNaN1() {
        runTest("test", Float.NaN, 0.0f);
    }

    @Test
    public void runNaN2() {
        runTest("test", Float.NaN, -0.0f);
    }

    @Test
    public void runNaN3() {
        runTest("test", Float.NaN, 1.0f);
    }

    @Test
    public void runNaN4() {
        runTest("test", Float.NaN, -1.0f);
    }

    @Test
    public void runNaN5() {
        runTest("test", Float.NaN, 345.0f);
    }

    @Test
    public void runNaN6() {
        runTest("test", Float.NaN, -345.0f);
    }

    @Test
    public void runNaN7() {
        runTest("test", 0.0f, Float.NaN);
    }

    @Test
    public void runNaN8() {
        runTest("test", -0.0f, Float.NaN);
    }

    @Test
    public void runNaN9() {
        runTest("test", 1.0f, Float.NaN);
    }

    @Test
    public void runNaN10() {
        runTest("test", -1.0f, Float.NaN);
    }

    @Test
    public void runNaN11() {
        runTest("test", 345.0f, Float.NaN);
    }

    @Test
    public void runNaN12() {
        runTest("test", -345.0f, Float.NaN);
    }

    // Various

    @Test
    public void runVarious() {
        runTest("test", 311.0f, 10f);
    }

}
