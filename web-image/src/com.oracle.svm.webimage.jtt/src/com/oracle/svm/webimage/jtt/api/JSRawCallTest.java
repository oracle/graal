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

import org.graalvm.webimage.api.JS;
import org.junit.Assert;

import com.oracle.svm.webimage.annotation.JSRawCall;

/**
 * Tests functionality of the {@link JS} annotation together with the {@link JSRawCall} annotation.
 * <p>
 * Because we pass the raw values to the JavaScript side, behavior is dependent on the backend. For
 * example the JS backend represents {@code long} as a {@code Long64} instance, while the Wasm
 * backends use {@code BigInt}. In the JS backend {@code float} is represented as {@code number}
 * (which is a 64bit double precision number) and only before arithmetic operations is the value
 * force into single precision. Because of that, the test only prints values in JavaScript for types
 * where all backends have the same representation on the JS side. For the round-trips, arbitrary
 * values can be checked.
 */
public class JSRawCallTest {

    public static final String[] OUTPUT = {
                    // printBoolean
                    "1", "0",
                    // printByte
                    "0", "-1",
                    // printShort
                    "1", "32767", "-32768",
                    // printChar
                    "97", "122", "65451",
                    // printInt
                    "0", "42",
                    // printDouble
                    "-2.5", "-Infinity", "NaN"
    };

    public static void main(String[] args) {
        printBoolean(true);
        printBoolean(false);
        printByte((byte) 0);
        printByte((byte) -1);
        printShort((short) 1);
        printShort(Short.MAX_VALUE);
        printShort(Short.MIN_VALUE);
        printChar('a');
        printChar('z');
        printChar((char) 0xffab);
        printInt(0);
        printInt(42);
        printDouble(-2.5d);
        printDouble(Double.NEGATIVE_INFINITY);
        printDouble(Double.NaN);

        checkRoundTripBoolean(true);
        checkRoundTripBoolean(false);

        for (byte b : new byte[]{0, 1, -1, 42, Byte.MIN_VALUE, Byte.MAX_VALUE}) {
            checkRoundTripByte(b);
        }

        for (short s : new short[]{0, 1, -1, 42, Short.MIN_VALUE, Short.MAX_VALUE}) {
            checkRoundTripShort(s);
        }

        for (short s : new short[]{0, 1, -1, 42, Short.MIN_VALUE, Short.MAX_VALUE}) {
            checkRoundTripShort(s);
        }

        for (char c : new char[]{0, 'a', 0xffff, Character.MIN_VALUE, Character.MAX_VALUE}) {
            checkRoundTripChar(c);
        }

        for (int i : new int[]{0, 1, -1, -42, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            checkRoundTripInt(i);
        }

        for (long l : new long[]{0, 1, -1, -42, Long.MIN_VALUE, Long.MAX_VALUE}) {
            checkRoundTripLong(l);
        }

        for (float f : new float[]{0, 1.23f, -1.54f, Float.MIN_VALUE, Float.MIN_NORMAL, Float.MAX_VALUE, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.NaN}) {
            checkRoundTripFloat(f);
        }

        for (double d : new double[]{0, 1.23f, -1.54f, Double.MIN_VALUE, Double.MIN_NORMAL, Double.MAX_VALUE, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.NaN}) {
            checkRoundTripDouble(d);
        }

        checkRoundTripObject(null);
        checkRoundTripObject(new Object());
        checkRoundTripObject("foobar");
        checkRoundTripObject(12);
    }

    @JSRawCall
    @JS("console.log('' + x);")
    private static native void printBoolean(boolean x);

    @JSRawCall
    @JS("console.log('' + x);")
    private static native void printByte(byte x);

    @JSRawCall
    @JS("console.log('' + x);")
    private static native void printShort(short x);

    @JSRawCall
    @JS("console.log('' + x);")
    private static native void printChar(char x);

    @JSRawCall
    @JS("console.log('' + x);")
    private static native void printInt(int x);

    @JSRawCall
    @JS("console.log('' + x);")
    private static native void printDouble(double x);

    @JSRawCall
    @JS("return x;")
    private static native boolean roundTripBoolean(boolean x);

    @JSRawCall
    @JS("return x;")
    private static native byte roundTripByte(byte x);

    @JSRawCall
    @JS("return x;")
    private static native short roundTripShort(short x);

    @JSRawCall
    @JS("return x;")
    private static native char roundTripChar(char x);

    @JSRawCall
    @JS("return x;")
    private static native int roundTripInt(int x);

    @JSRawCall
    @JS("return x;")
    private static native long roundTripLong(long x);

    @JSRawCall
    @JS("return x;")
    private static native float roundTripFloat(float x);

    @JSRawCall
    @JS("return x;")
    private static native double roundTripDouble(double x);

    @JSRawCall
    @JS("return x;")
    private static native Object roundTripObject(Object x);

    private static void checkRoundTripBoolean(boolean x) {
        Assert.assertEquals(x, roundTripBoolean(x));
    }

    private static void checkRoundTripByte(byte x) {
        Assert.assertEquals(x, roundTripByte(x));
    }

    private static void checkRoundTripShort(short x) {
        Assert.assertEquals(x, roundTripShort(x));
    }

    private static void checkRoundTripChar(char x) {
        Assert.assertEquals(x, roundTripChar(x));
    }

    private static void checkRoundTripInt(int x) {
        Assert.assertEquals(x, roundTripInt(x));
    }

    private static void checkRoundTripLong(long x) {
        Assert.assertEquals(x, roundTripLong(x));
    }

    private static void checkRoundTripFloat(float x) {
        Assert.assertEquals(x, roundTripFloat(x), 0.0f);
    }

    private static void checkRoundTripDouble(double x) {
        Assert.assertEquals(x, roundTripDouble(x), 0.0d);
    }

    private static void checkRoundTripObject(Object x) {
        Assert.assertEquals(x, roundTripObject(x));
    }
}
