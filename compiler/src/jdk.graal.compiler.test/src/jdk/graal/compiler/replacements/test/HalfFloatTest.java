/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.replacements.test;

import org.junit.Test;

import jdk.graal.compiler.jtt.JTTTest;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;

public class HalfFloatTest extends JTTTest {

    public static float float16ToFloat(short s) {
        return Float.float16ToFloat(s);
    }

    public static short floatToFloat16(float f) {
        return Float.floatToFloat16(f);
    }

    /*
     * Put all 16-bit values through a conversion loop and make sure the values are preserved (NaN
     * bit patterns notwithstanding).
     */
    @Test
    public void binary16RoundTrip() {
        for (int i = Short.MIN_VALUE; i < Short.MAX_VALUE; i++) {
            short s = (short) i;
            Result result = test("float16ToFloat", s);
            float f = (Float) result.returnValue;
            result = test("floatToFloat16", f);

            assertTrue(Binary16.equivalent(s, (Short) result.returnValue));
        }
    }

    @Test
    public void binary16CardinalValues() {
        // Encode short value for different binary16 cardinal values as an
        // integer-valued float.
        float[][] testCases = {
                        {Binary16.POSITIVE_ZERO, +0.0f},
                        {Binary16.MIN_VALUE, 0x1.0p-24f},
                        {Binary16.MAX_SUBNORMAL, 0x1.ff8p-15f},
                        {Binary16.MIN_NORMAL, 0x1.0p-14f},
                        {Binary16.ONE, 1.0f},
                        {Binary16.MAX_VALUE, 65504.0f},
                        {Binary16.POSITIVE_INFINITY, Float.POSITIVE_INFINITY},
        };

        // Check conversions in both directions

        // short -> float
        for (var testCase : testCases) {
            runTest("float16ToFloat", (short) testCase[0]);
        }

        // float -> short
        for (var testCase : testCases) {
            runTest("floatToFloat16", testCase[1]);
        }
    }

    @Test
    public void roundFloatToBinary16() {
        float[][] testCases = {
                        // Test all combinations of LSB, round, and sticky bit

                        // LSB = 0, test combination of round and sticky
                        {0x1.ff8000p-1f, (short) 0x3bfe},             // round = 0, sticky = 0
                        {0x1.ff8010p-1f, (short) 0x3bfe},             // round = 0, sticky = 1
                        {0x1.ffa000p-1f, (short) 0x3bfe},             // round = 1, sticky = 0
                        {0x1.ffa010p-1f, (short) 0x3bff},             // round = 1, sticky = 1 => ++

                        // LSB = 1, test combination of round and sticky
                        {0x1.ffc000p-1f, Binary16.ONE - 1},           // round = 0, sticky = 0
                        {0x1.ffc010p-1f, Binary16.ONE - 1},           // round = 0, sticky = 1
                        {0x1.ffe000p-1f, Binary16.ONE},               // round = 1, sticky = 0 => ++
                        {0x1.ffe010p-1f, Binary16.ONE},               // round = 1, sticky = 1 => ++

                        // Test subnormal rounding
                        // Largest subnormal binary16 0x03ff => 0x1.ff8p-15f; LSB = 1
                        {0x1.ff8000p-15f, Binary16.MAX_SUBNORMAL},    // round = 0, sticky = 0
                        {0x1.ff8010p-15f, Binary16.MAX_SUBNORMAL},    // round = 0, sticky = 1
                        {0x1.ffc000p-15f, Binary16.MIN_NORMAL},       // round = 1, sticky = 0 => ++
                        {0x1.ffc010p-15f, Binary16.MIN_NORMAL},       // round = 1, sticky = 1 => ++

                        // Test rounding near binary16 MIN_VALUE
                        // Smallest in magnitude subnormal binary16 value 0x0001 => 0x1.0p-24f
                        // Half-way case,0x1.0p-25f, and smaller should round down to zero
                        {0x1.fffffep-26f, Binary16.POSITIVE_ZERO},     // nextDown in float
                        {0x1.000000p-25f, Binary16.POSITIVE_ZERO},
                        {0x1.000002p-25f, Binary16.MIN_VALUE},         // nextUp in float
                        {0x1.100000p-25f, Binary16.MIN_VALUE},

                        // Test rounding near overflow threshold
                        // Largest normal binary16 number 0x7bff => 0x1.ffcp15f; LSB = 1
                        {0x1.ffc000p15f, Binary16.MAX_VALUE},         // round = 0, sticky = 0
                        {0x1.ffc010p15f, Binary16.MAX_VALUE},         // round = 0, sticky = 1
                        {0x1.ffe000p15f, Binary16.POSITIVE_INFINITY}, // round = 1, sticky = 0 => ++
                        {0x1.ffe010p15f, Binary16.POSITIVE_INFINITY}, // round = 1, sticky = 1 => ++
        };

        for (var testCase : testCases) {
            runTest("floatToFloat16", testCase[0]);
        }
    }

    @Test
    public void roundFloatToBinary16HalfWayCases() throws InvalidInstalledCodeException {
        // Test rounding of exact half-way cases between each pair of
        // finite exactly-representable binary16 numbers. Also test
        // rounding of half-way +/- ulp of the *float* value.
        // Additionally, test +/- float ulp of the endpoints. (Other
        // tests in this file make sure all short values round-trip so
        // that doesn't need to be tested here.)

        InstalledCode f162f = getCode(getResolvedJavaMethod("float16ToFloat"));

        for (int i = Binary16.POSITIVE_ZERO;      // 0x0000
                        i <= Binary16.MAX_VALUE;  // 0x7bff
                        i += 2) {                 // Check every even/odd pair once
            short lower = (short) i;
            short upper = (short) (i + 1);

            float lowerFloat = (Float) f162f.executeVarargs(lower);
            float upperFloat = (Float) f162f.executeVarargs(upper);

            assertTrue(lowerFloat < upperFloat);

            float midway = (lowerFloat + upperFloat) * 0.5f; // Exact midpoint

            test("floatToFloat16", Math.nextUp(lowerFloat));
            test("floatToFloat16", Math.nextDown(midway));

            // Under round to nearest even, the midway point will
            // round *down* to the (even) lower endpoint.
            test("floatToFloat16", midway);

            test("floatToFloat16", Math.nextUp(midway));
            test("floatToFloat16", Math.nextDown(upperFloat));
        }

        // More testing around the overflow threshold
        // Binary16.ulp(Binary16.MAX_VALUE) == 32.0f; test around Binary16.MAX_VALUE + 1/2 ulp
        float binary16MaxValue = (Float) f162f.executeVarargs(Binary16.MAX_VALUE);
        float binary16MaxValueHalfUlp = binary16MaxValue + 16.0f;

        runTest("floatToFloat16", Math.nextDown(binary16MaxValue));
        runTest("floatToFloat16", binary16MaxValue);
        runTest("floatToFloat16", Math.nextUp(binary16MaxValue));

        // Binary16.MAX_VALUE is an "odd" value since its LSB = 1 so
        // the half-way value greater than Binary16.MAX_VALUE should
        // round up to the next even value, in this case Binary16.POSITIVE_INFINITY.
        runTest("floatToFloat16", Math.nextDown(binary16MaxValueHalfUlp));
        runTest("floatToFloat16", binary16MaxValueHalfUlp);
        runTest("floatToFloat16", Math.nextUp(binary16MaxValueHalfUlp));
    }

    @Test
    public void roundFloatToBinary16FullBinade() throws InvalidInstalledCodeException {
        // For each float value between 1.0 and less than 2.0
        // (i.e. set of float values with an exponent of 0), convert
        // each value to binary16 and then convert that binary16 value
        // back to float.
        //
        // Any exponent could be used; the maximum exponent for normal
        // values would not exercise the full set of code paths since
        // there is an up-front check on values that would overflow,
        // which correspond to a ripple-carry of the significand that
        // bumps the exponent.
        short previous = (short) 0;

        InstalledCode f2f16 = getCode(getResolvedJavaMethod("floatToFloat16"));
        InstalledCode f162f = getCode(getResolvedJavaMethod("float16ToFloat"));

        for (int i = Float.floatToIntBits(1.0f); i <= Float.floatToIntBits(Math.nextDown(2.0f)); i++) {
            // (Could also express the loop control directly in terms
            // of floating-point operations, incrementing by ulp(1.0),
            // etc.)

            float f = Float.intBitsToFloat(i);
            short fAsBin16 = (Short) f2f16.executeVarargs(f);
            short fAsBin16Down = (short) (fAsBin16 - 1);
            short fAsBin16Up = (short) (fAsBin16 + 1);

            // Across successive float values to convert to binary16,
            // the binary16 results should be semi-monotonic,
            // non-decreasing in this case.

            // Only positive binary16 values so can compare using integer operations
            assertTrue(fAsBin16 >= previous, "Semi-monotonicity violation observed on %s", Integer.toHexString(0xfff & fAsBin16));
            previous = fAsBin16;

            // If round-to-nearest was correctly done, when exactly
            // mapped back to float, fAsBin16 should be at least as
            // close as either of its neighbors to the original value
            // of f.

            float fPrimeDown = (Float) f162f.executeVarargs(fAsBin16Down);
            float fPrime = (Float) f162f.executeVarargs(fAsBin16);
            float fPrimeUp = (Float) f162f.executeVarargs(fAsBin16Up);

            float fPrimeDiff = Math.abs(f - fPrime);
            if (fPrimeDiff == 0.0) {
                continue;
            }
            float fPrimeDownDiff = Math.abs(f - fPrimeDown);
            float fPrimeUpDiff = Math.abs(f - fPrimeUp);

            assertTrue(fPrimeDiff <= fPrimeDownDiff && fPrimeDiff <= fPrimeUpDiff, "Round-to-nearest violation on converting %s to binary16 and back.", Float.toHexString(f));
        }
    }

    private static final int NAN_EXPONENT = 0x7c00;
    private static final int SIGN_BIT = 0x8000;

    /*
     * Put all 16-bit NaN values through a conversion loop and make sure the significand, sign, and
     * exponent are all preserved.
     */
    @Test
    public void binary16NaNRoundTrip() {
        // A NaN has a nonzero significand
        for (int i = 1; i <= 0x3ff; i++) {
            short binary16NaN = (short) (NAN_EXPONENT | i);
            assertTrue(isNaN(binary16NaN));

            runTest("float16ToFloat", binary16NaN);
            runTest("floatToFloat16", Float.float16ToFloat(binary16NaN));

            short negBinary16NaN = (short) (SIGN_BIT | binary16NaN);
            runTest("float16ToFloat", negBinary16NaN);
            runTest("floatToFloat16", Float.float16ToFloat(negBinary16NaN));
        }
    }

    private static boolean isNaN(short binary16) {
        return ((binary16 & 0x7c00) == 0x7c00)         // Max exponent and...
                        && ((binary16 & 0x03ff) != 0); // significand nonzero.
    }

    public static class Binary16 {
        public static final short POSITIVE_INFINITY = (short) 0x7c00;
        public static final short MAX_VALUE = 0x7bff;
        public static final short ONE = 0x3c00;
        public static final short MIN_NORMAL = 0x0400;
        public static final short MAX_SUBNORMAL = 0x03ff;
        public static final short MIN_VALUE = 0x0001;
        public static final short POSITIVE_ZERO = 0x0000;

        public static boolean isNaN(short binary16) {
            return ((binary16 & 0x7c00) == 0x7c00) // Max exponent and...
                            && ((binary16 & 0x03ff) != 0);    // significand nonzero.
        }

        public static short negate(short binary16) {
            return (short) (binary16 ^ 0x8000); // Flip only sign bit.
        }

        public static boolean equivalent(short bin16X, short bin16Y) {
            return (bin16X == bin16Y) || isNaN(bin16X) && isNaN(bin16Y);
        }
    }
}
