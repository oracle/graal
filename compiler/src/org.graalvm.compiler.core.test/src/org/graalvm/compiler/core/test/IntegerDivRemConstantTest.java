/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, Arm Limited. All rights reserved.
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

package org.graalvm.compiler.core.test;

import org.junit.Test;

public class IntegerDivRemConstantTest extends GraalCompilerTest {
    public static int intDivPositiveConstant(int val) {
        return val / 5;
    }

    @Test
    public void testIntDivPositiveConstant() {
        test("intDivPositiveConstant", -10);
        test("intDivPositiveConstant", 0);
        test("intDivPositiveConstant", 4256);
        test("intDivPositiveConstant", Integer.MAX_VALUE);
        test("intDivPositiveConstant", Integer.MIN_VALUE);
    }

    public static int intDivIntegerMax(int val) {
        return val / Integer.MAX_VALUE;
    }

    @Test
    public void testIntDivIntegerMax() {
        test("intDivIntegerMax", -10);
        test("intDivIntegerMax", 0);
        test("intDivIntegerMax", 4256);
        test("intDivIntegerMax", Integer.MAX_VALUE);
        test("intDivIntegerMax", Integer.MIN_VALUE);
    }

    public static int intDivNegativeConstant(int val) {
        return val / -1234;
    }

    @Test
    public void testIntDivNegativeConstant() {
        test("intDivNegativeConstant", -123);
        test("intDivNegativeConstant", 0);
        test("intDivNegativeConstant", 123);
        test("intDivNegativeConstant", Integer.MAX_VALUE);
        test("intDivNegativeConstant", Integer.MIN_VALUE);
    }

    public static int intDivIntegerMinOdd(int val) {
        return val / (Integer.MIN_VALUE + 1);
    }

    @Test
    public void testIntDivIntegerMinOdd() {
        test("intDivIntegerMinOdd", -123);
        test("intDivIntegerMinOdd", 0);
        test("intDivIntegerMinOdd", 123);
        test("intDivIntegerMinOdd", Integer.MAX_VALUE);
        test("intDivIntegerMinOdd", Integer.MIN_VALUE);
    }

    public static long longDivPositiveConstant(long val) {
        return val / 35170432;
    }

    @Test
    public void testLongDivPositiveConstant() {
        test("longDivPositiveConstant", -1234L);
        test("longDivPositiveConstant", 0L);
        test("longDivPositiveConstant", 214423L);
        test("longDivPositiveConstant", Long.MAX_VALUE);
        test("longDivPositiveConstant", Long.MIN_VALUE);
    }

    public static long longDivLongMax(long val) {
        return val / Long.MAX_VALUE;
    }

    @Test
    public void testLongDivLongMax() {
        test("longDivLongMax", -1234L);
        test("longDivLongMax", 0L);
        test("longDivLongMax", 214423L);
        test("longDivLongMax", Long.MAX_VALUE);
        test("longDivLongMax", Long.MIN_VALUE);
    }

    public static long longDivNegativeConstant(long val) {
        return val / -413;
    }

    @Test
    public void testLongDivNegativeConstant() {
        test("longDivNegativeConstant", -43L);
        test("longDivNegativeConstant", 0L);
        test("longDivNegativeConstant", 147065L);
        test("longDivNegativeConstant", Long.MAX_VALUE);
        test("longDivNegativeConstant", Long.MIN_VALUE);
    }

    public static long longDivLongMinOdd(long val) {
        return val / (Long.MIN_VALUE + 1);
    }

    @Test
    public void testLongDivLongMinOdd() {
        test("longDivLongMinOdd", -1234L);
        test("longDivLongMinOdd", 0L);
        test("longDivLongMinOdd", 214423L);
        test("longDivLongMinOdd", Long.MAX_VALUE);
        test("longDivLongMinOdd", Long.MIN_VALUE);
    }

    public static int intRemPositiveConstant(int val) {
        return val % 139968;
    }

    @Test
    public void testIntRemPositiveConstant() {
        test("intRemPositiveConstant", -10);
        test("intRemPositiveConstant", 0);
        test("intRemPositiveConstant", 4256);
        test("intRemPositiveConstant", Integer.MAX_VALUE);
        test("intRemPositiveConstant", Integer.MIN_VALUE);
    }

    public static int intRemNegativeConstant(int val) {
        return val % -139968;
    }

    @Test
    public void testIntRemNegativeConstant() {
        test("intRemNegativeConstant", -10);
        test("intRemNegativeConstant", 0);
        test("intRemNegativeConstant", 4256);
        test("intRemNegativeConstant", Integer.MAX_VALUE);
        test("intRemNegativeConstant", Integer.MIN_VALUE);
    }

    public static long longRemPositiveConstant(long val) {
        return val % 35170432;
    }

    @Test
    public void testLongRemPositiveConstant() {
        test("longRemPositiveConstant", -1234L);
        test("longRemPositiveConstant", 0L);
        test("longRemPositiveConstant", 214423L);
        test("longRemPositiveConstant", Long.MAX_VALUE);
        test("longRemPositiveConstant", Long.MIN_VALUE);
    }

    public static long longRemNegativeConstant(long val) {
        return val % -413;
    }

    @Test
    public void testLongRemNegativeConstant() {
        test("longRemNegativeConstant", -43L);
        test("longRemNegativeConstant", 0L);
        test("longRemNegativeConstant", 147065L);
        test("longRemNegativeConstant", Long.MAX_VALUE);
        test("longRemNegativeConstant", Long.MIN_VALUE);
    }
}
