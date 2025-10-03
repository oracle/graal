/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.webimage.jtt.api;

import org.graalvm.webimage.api.JSNumber;
import org.graalvm.webimage.api.JSObject;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JSNumberTest {

    static final double DOUBLE_SMALL = 3.14159;
    static final double DOUBLE_BIG = 123.456789;
    static final double SMALL = 0.00001234;
    static final double SMALL_EXTENDED = 0.0000123456789;
    static final double LARGE = 987654321.123;
    static final double SAFE_INT = 9007199254740991L;
    static final double UNSAFE_INT = 9007199254740992L;
    static final double POS_INF = Double.POSITIVE_INFINITY;
    static final double NEG_INF = Double.NEGATIVE_INFINITY;
    static final double NAN = Double.NaN;
    static final int INT = 42;
    static final int NEG_INT = -42;
    static final int HEX = 255;
    static final double DELTA = 0.0;
    static final double SMALL_DELTA = 0.001;

    public static void main(String[] args) {
        testIsFinite();
        testIsInteger();
        testIsNaN();
        testIsSafeInteger();
        testConstants();
        testParseFloat();
        testParseInt();
        testToExponential();
        testToFixed();
        testToLocaleString();
        testToPrecision();
        testToStringRadix();
        testValueOf();
    }

    public static void testIsFinite() {
        assertTrue(JSNumber.isFinite(JSNumber.of(INT)));
        assertFalse(JSNumber.isFinite(JSNumber.of(POS_INF)));
        assertFalse(JSNumber.isFinite(JSNumber.of(NAN)));
        assertTrue(JSNumber.isFinite(JSNumber.of(DOUBLE_BIG)));
        assertFalse(JSNumber.isFinite(JSNumber.of(NEG_INF)));
    }

    public static void testIsInteger() {
        assertTrue(JSNumber.isInteger(JSNumber.of(INT)));
        assertFalse(JSNumber.isInteger(JSNumber.of(DOUBLE_BIG)));
        assertFalse(JSNumber.isInteger(JSNumber.of(NAN)));
        assertFalse(JSNumber.isInteger(JSNumber.of(POS_INF)));
        assertFalse(JSNumber.isInteger(JSNumber.of(NEG_INF)));
    }

    public static void testIsNaN() {
        assertFalse(JSNumber.isNaN(JSNumber.of(INT)));
        assertTrue(JSNumber.isNaN(JSNumber.of(NAN)));
        assertFalse(JSNumber.isNaN(JSNumber.of(POS_INF)));
        assertFalse(JSNumber.isNaN(JSNumber.of(DOUBLE_BIG)));
        assertFalse(JSNumber.isNaN(JSNumber.of(NEG_INF)));
    }

    public static void testIsSafeInteger() {
        assertTrue(JSNumber.isSafeInteger(JSNumber.of(SAFE_INT)));
        assertFalse(JSNumber.isSafeInteger(JSNumber.of(UNSAFE_INT)));
        assertFalse(JSNumber.isSafeInteger(JSNumber.of(DOUBLE_BIG)));
        assertFalse(JSNumber.isSafeInteger(JSNumber.of(NAN)));
        assertFalse(JSNumber.isSafeInteger(JSNumber.of(1e100)));
    }

    public static void testConstants() {
        assertEquals(2.220446e-16, JSNumber.EPSILON(), 1e-16);
        assertEquals(9.007199e+15, JSNumber.MAX_SAFE_INTEGER(), 0.000001e+15);
        assertEquals(1.797693e+308, JSNumber.MAX_VALUE(), 0.000001e+308);
        assertEquals(-9.007199e+15, JSNumber.MIN_SAFE_INTEGER(), 0.000001e+15);
        assertEquals(4.900000e-324, JSNumber.MIN_VALUE(), 1e-16);
        assertEquals(NAN, JSNumber.NaN(), DELTA);
        assertEquals(NEG_INF, JSNumber.NEGATIVE_INFINITY(), DELTA);
        assertEquals(POS_INF, JSNumber.POSITIVE_INFINITY(), DELTA);
    }

    public static void testParseFloat() {
        assertEquals(42.0, JSNumber.parseFloat(INT), DELTA);
        assertEquals(DOUBLE_BIG, JSNumber.parseFloat(DOUBLE_BIG), SMALL_DELTA);
        assertEquals(3.14, JSNumber.parseFloat("3.14abc"), SMALL_DELTA);
        assertEquals(NAN, JSNumber.parseFloat("abc"), DELTA);
    }

    public static void testParseInt() {
        assertEquals(42, JSNumber.parseInt(42.9));
        assertEquals(-3, JSNumber.parseInt(-3.99));
        assertEquals(123, JSNumber.parseInt("123"));
        assertEquals(123, JSNumber.parseInt("123.456"));
        assertEquals(0, JSNumber.parseInt("abc"));
        assertEquals(10, JSNumber.parseInt("1010", 2));
        assertEquals(255, JSNumber.parseInt("FF", 16));
        assertEquals(63, JSNumber.parseInt("77", 8));
    }

    public static void testToExponential() {
        JSNumber small = JSNumber.of(SMALL);
        JSNumber big = JSNumber.of(987654321);
        JSNumber pi = JSNumber.of(DOUBLE_SMALL);

        assertEquals("1.234e-5", small.toExponential());
        assertEquals("9.87654321e+8", big.toExponential());
        assertEquals("3.14159e+0", pi.toExponential());

        assertEquals("1.23e-5", small.toExponential(2));
        assertEquals("9.8765e+8", big.toExponential(4));
        assertEquals("3.141590e+0", pi.toExponential(6));
    }

    public static void testToFixed() {
        JSNumber pi = JSNumber.of(DOUBLE_SMALL);
        JSNumber big = JSNumber.of(DOUBLE_BIG);
        JSNumber small = JSNumber.of(SMALL);

        assertEquals("3", pi.toFixed());
        assertEquals("123", big.toFixed());
        assertEquals("0", small.toFixed());

        assertEquals("3.14", pi.toFixed(2));
        assertEquals("123.4568", big.toFixed(4));
        assertEquals("0.00001234", small.toFixed(8));
    }

    public static void testToLocaleString() {
        JSNumber number = JSNumber.of(1234567.89);

        JSObject currencyOpts = JSObject.create();
        currencyOpts.set("style", "currency");
        currencyOpts.set("currency", "EUR");

        JSObject fractionOpts = JSObject.create();
        fractionOpts.set("minimumFractionDigits", JSNumber.of(4));
        fractionOpts.set("maximumFractionDigits", JSNumber.of(4));

        assertTrue(number.toLocaleString().matches("\\d[,.]\\d{3}[,.]\\d{3}[,.]\\d{2}"));
        assertEquals("1\u00A0234\u00A0567,89", number.toLocaleString("de-AT"));
        assertEquals("1,234,567.89", number.toLocaleString("en-US"));
        assertEquals("\u20AC\u00A01.234.567,89", number.toLocaleString("de-AT", currencyOpts));
        assertEquals("1,234,567.8900", number.toLocaleString("en-US", fractionOpts));
    }

    public static void testToPrecision() {
        JSNumber big = JSNumber.of(DOUBLE_BIG);
        JSNumber extended = JSNumber.of(SMALL_EXTENDED);
        JSNumber large = JSNumber.of(LARGE);

        assertEquals("123.456789", big.toPrecision());
        assertEquals("0.0000123456789", extended.toPrecision());
        assertEquals("987654321.123", large.toPrecision());

        assertEquals("123.5", big.toPrecision(4));
        assertEquals("0.0000123", extended.toPrecision(3));
        assertEquals("9.87654e+8", large.toPrecision(6));
    }

    public static void testToStringRadix() {
        JSNumber hex = JSNumber.of(HEX);
        JSNumber pi = JSNumber.of(DOUBLE_SMALL);
        JSNumber neg = JSNumber.of(NEG_INT);

        assertEquals("JavaScript<number; 255.0>", hex.toString());
        assertEquals("JavaScript<number; 3.14159>", pi.toString());
        assertEquals("JavaScript<number; -42.0>", neg.toString());

        assertEquals("JavaScript<number; 11111111>", hex.toString(2));
        assertEquals("JavaScript<number; ff>", hex.toString(16));
        assertEquals("JavaScript<number; 377>", hex.toString(8));
        assertEquals("JavaScript<number; -132>", neg.toString(5));
    }

    public static void testValueOf() {
        assertEquals(42.0, JSNumber.of(INT).valueOf(), DELTA);
        assertEquals(DOUBLE_SMALL, JSNumber.of(DOUBLE_SMALL).valueOf(), 0.000001);
        assertEquals(NAN, JSNumber.of(NAN).valueOf(), DELTA);
    }
}
