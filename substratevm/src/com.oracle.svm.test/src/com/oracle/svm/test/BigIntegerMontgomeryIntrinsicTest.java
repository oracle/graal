/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.test;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Test;

public class BigIntegerMontgomeryIntrinsicTest {

    private static final BigInteger EXPONENT = new BigInteger("112233445566778899aabbccddeeff00112233445566778899aabbccddeeff", 16);
    private static final int[] MONTGOMERY_LENGTHS = {2, 4, 8, 16, 32, 64, 80};

    @Test
    public void testMontgomeryModPow() {
        checkModPow(
                        "123456789abcdef00123456789abcdef",
                        "f123456789abcdef0123456789abcdef",
                        "4cc25f6e9959d3284f660edd2687f53a");
        checkModPow(
                        "123456789abcdef00123456789abcdef123456789abcdef00123456789abcdef123456789abcdef00123456789abcdef123456789abcdef00123456789abcdef",
                        "f123456789abcdef0123456789abcdef123456789abcdef00123456789abcdef123456789abcdef00123456789abcdef123456789abcdef00123456789abcdef",
                        "2137c95eabcd45a7b4bc2c9a07e0578b8aee85cf19dfe163883adcbefb507ed75576f7b32ce278e7dbe2b0550112cefb5d2bb5ccf864d464bdea1567a81afb9e");
        // 40 64-bit chunks produce 80 int words, crossing the Montgomery square helper threshold.
        checkModPow(
                        repeatHex("123456789abcdef0", 40),
                        repeatHex("f123456789abcdef", 40),
                        repeatHex("ac26fcf8d31ee29e", 40));
    }

    @Test
    public void testMontgomeryModPowMatrix() {
        for (int len : MONTGOMERY_LENGTHS) {
            BigInteger modulus = modulusForLength(len);
            checkModPowAgainstSimpleImplementation(BigInteger.ZERO, modulus);
            checkModPowAgainstSimpleImplementation(BigInteger.ONE, modulus);
            checkModPowAgainstSimpleImplementation(modulus.subtract(BigInteger.ONE), modulus);
            checkModPowAgainstSimpleImplementation(repeatedWordValue(len, 0xAAAA_AAAA, 0x5555_5555).mod(modulus), modulus);
            checkModPowAgainstSimpleImplementation(repeatedWordValue(len, 0x3333_3333, 0xCCCC_CCCC).mod(modulus), modulus);
        }
    }

    private static void checkModPow(String baseHex, String modulusHex, String expectedHex) {
        BigInteger base = new BigInteger(baseHex, 16);
        BigInteger modulus = new BigInteger(modulusHex, 16).setBit(0);
        BigInteger expected = new BigInteger(expectedHex, 16);

        assertEquals(expected, base.modPow(EXPONENT, modulus));
    }

    private static void checkModPowAgainstSimpleImplementation(BigInteger base, BigInteger modulus) {
        assertEquals(simpleModPow(base, EXPONENT, modulus), base.modPow(EXPONENT, modulus));
    }

    private static BigInteger simpleModPow(BigInteger base, BigInteger exponent, BigInteger modulus) {
        BigInteger result = BigInteger.ONE;
        BigInteger current = base.mod(modulus);
        for (int bit = 0; bit < exponent.bitLength(); bit++) {
            if (exponent.testBit(bit)) {
                result = result.multiply(current).mod(modulus);
            }
            current = current.multiply(current).mod(modulus);
        }
        return result;
    }

    private static BigInteger modulusForLength(int len) {
        int bits = len * Integer.SIZE;
        return BigInteger.ONE.shiftLeft(bits - 1).add(BigInteger.ONE.shiftLeft(bits / 2)).add(BigInteger.valueOf(0x1_0000_0001L));
    }

    private static BigInteger repeatedWordValue(int len, int evenWord, int oddWord) {
        BigInteger result = BigInteger.ZERO;
        for (int i = 0; i < len; i++) {
            int word = (i & 1) == 0 ? evenWord : oddWord;
            result = result.shiftLeft(Integer.SIZE).add(BigInteger.valueOf(word & 0xFFFF_FFFFL));
        }
        return result;
    }

    private static String repeatHex(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
