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
package com.oracle.svm.webimage.longemulation;

/*
 *  The arithmetic algorithms are adapted from
 *  https://github.com/scala-js/scala-js/blob/bb39b49d0bc995fe9ec471fd79732b03e81fc59b/linker-private-library/src/main/scala/org/scalajs/linker/runtime/RuntimeLong.scala
 *  which is licensed under Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
 */

import org.graalvm.nativeimage.Platforms;
import org.graalvm.webimage.api.JS;

import com.oracle.svm.webimage.annotation.JSRawCall;
import com.oracle.svm.webimage.platform.WebImageJSPlatform;

/**
 * This class is an emulation of the primitive datatype long. This is needed because JavaScript does
 * not natively support 64-bit integers. When an operation that uses/produces a primitive long is
 * lowered, these operations are mapped to functions from this class.
 */
@Platforms(WebImageJSPlatform.class)
public class Long64 {
    private static final int MASK_32 = 0xFFFFFFFF;
    private static final int HWORD = 0xFFFF;
    private static final int HWORDW = 16;
    /**
     * Minimum integer value.
     */
    private static final int IntMinValue = 0x80000000;
    private static final int AskRemainder = 0;
    private static final int AskQuotient = 1;

    /**
     * Helper mask to determine whether a long can safely be represented with a double.
     */
    private static final int UnsignedSafeDoubleMask = 0xffe00000;
    private static final double TwoPow32 = 4294967296.0;

    /**
     * Public long value that may be used repeatedly. This is not final in order to guarantee that
     * this is added to the field list of the generated JavaScript class. Otherwise there would only
     * be a constant in the heap image.
     */
    public static Long64 LongZero = new Long64(0, 0);
    public static Long64 LongMinValue = new Long64(0, 0x80000000);
    public static Long64 LongMaxValue = new Long64(0xffffffff, 0x7fffffff);
    /**
     * The long value is represented by the high and low 32-bits.
     */
    private final int low;
    private final int high;

    public Long64(int low, int high) {
        this.low = low;
        this.high = high;
    }

    public static Long64 fromInt(int n) {
        return new Long64(n, n >> 31);
    }

    /**
     * Converts a double to a 32-bit integer using JavaScript semantics.
     *
     * @param a number to be converted to int.
     * @return integer
     */
    @JSRawCall
    @JS("return a | 0;")
    private static native int rawToInt(double a);

    /**
     * Create a long variable from a double.
     *
     * @param number double
     * @return new long variable
     */
    public static Long64 fromDouble(double number) {
        if (Double.isNaN(number)) {
            return LongZero;
        } else if (number >= Long.MAX_VALUE) {
            return LongMaxValue;
        } else if (number <= Long.MIN_VALUE) {
            return LongMinValue;
        } else {
            int low = rawToInt(number % TwoPow32);
            int high = rawToInt(number / TwoPow32);
            if (number < 0 && low != 0) {
                high = high - 1;
            }
            return new Long64(low, high);
        }
    }

    /**
     * Create a long from a double that is unsigned and has a value that is in the safe range.
     *
     * @param number unsigned double that hold a long in the safe range
     * @return new Long64 variable
     */
    private static Long64 fromUnsignedSafeDouble(double number) {
        return new Long64(unsignedSafeDoubleLo(number), unsignedSafeDoubleHi(number));
    }

    /**
     * Get the high bits for a long that is represented by a given unsigned double.
     *
     * @param number unsigned double which a value in the safe range.
     * @return high bits for the corresponding long
     */
    private static int unsignedSafeDoubleHi(double number) {
        return rawToInt(number / TwoPow32);
    }

    /**
     * Get the low bits for a long that is represented by a given unsigned double.
     *
     * @param number unsigned double which a value in the safe range.
     * @return low bits for the corresponding long
     */
    private static int unsignedSafeDoubleLo(double number) {
        return rawToInt(number);
    }

    private static Long64 addInternal(int low1, int high1, int low2, int high2) {
        int low = low1 + low2;
        int high;
        // less than unsigned
        if ((low ^ IntMinValue) < (low1 ^ IntMinValue)) {
            high = high1 + high2 + 1;
        } else {
            high = high1 + high2;
        }
        return new Long64(low, high);
    }

    private static Long64 subInternal(int l1, int h1, int l2, int h2) {
        int low = l1 - l2;
        int high;
        // greater than unsigned
        if ((low ^ IntMinValue) > (l1 ^ IntMinValue)) {
            high = h1 - h2 - 1;
        } else {
            high = h1 - h2;
        }
        return fromTwoInt(low, high);
    }

    private static Long64 mulInternal(int l1, int h1, int l2, int h2) {
        int a0 = l1 & HWORD;
        int a1 = l1 >>> HWORDW;
        int b0 = l2 & HWORD;
        int b1 = l2 >>> HWORDW;

        int a0b0 = a0 * b0;
        int a1b0 = a1 * b0;
        int a0b1 = a0 * b1;

        int low = a0b0 + ((a1b0 + a0b1) << HWORDW);
        int c1part = (a0b0 >>> HWORDW) + a0b1;
        int high = l1 * h2 + h1 * l2 + a1 * b1 + (c1part >>> HWORDW) +
                        (((c1part & HWORD) + a1b0) >>> HWORDW);
        return fromTwoInt(low, high);
    }

    /**
     * Determine if a long has a value that only needs 32 bits.
     *
     * @param l low bits
     * @param h high bits
     * @return true iff the long can be represented with an int
     */
    private static boolean isInt32(int l, int h) {
        return h == (l >> 31);
    }

    /**
     * Compute the absolute value of a long.
     *
     * @param low low bits
     * @param high high bits
     * @return absolute value of the give long
     */
    private static Long64 absInternal(int low, int high) {
        if (high < 0) {
            int newHigh;
            if (low != 0) {
                newHigh = ~high;
            } else {
                newHigh = -high;
            }
            return new Long64(-low, newHigh);
        } else {
            return new Long64(low, high);
        }
    }

    private static Long64 divInternal(int l1, int h1, int l2, int h2) {
        if (isInt32(l1, h1)) {
            if (isInt32(l2, h2)) {
                if (l1 == IntMinValue && l2 == -1) {
                    return new Long64(IntMinValue, 0);
                } else {
                    int low = l1 / l2;
                    return new Long64(low, low >> 31);
                }
            } else {
                if (l1 == IntMinValue && (l2 == IntMinValue && h2 == 0)) {
                    return new Long64(MASK_32, MASK_32); // minus one
                } else {
                    return LongZero;
                }
            }
        }
        boolean dividendIsNegative = h1 < 0;
        boolean divisorIsNegative = h2 < 0;

        // compute absolute values
        int newL1 = l1;
        int newH1 = h1;
        int newL2 = l2;
        int newH2 = h2;
        if (h2 < 0) {
            if (l2 != 0) {
                newH2 = ~h2;
            } else {
                newH2 = -h2;
            }
            newL2 = -l2;
        }
        if (h1 < 0) {
            if (l1 != 0) {
                newH1 = ~h1;
            } else {
                newH1 = -h1;
            }
            newL1 = -l1;
        }
        Long64 quotient = divUnsignedInternal(newL1, newH1, newL2, newH2);
        if ((dividendIsNegative && !divisorIsNegative) ||
                        (!dividendIsNegative && divisorIsNegative)) {
            int newLow = ~quotient.low;
            int newHigh = ~quotient.high;

            newLow = newLow + 1;
            if (newLow == 0) {
                newHigh = newHigh + 1;
            }
            return new Long64(newLow, newHigh);
        }
        return quotient;
    }

    private static Long64 modInternal(int l1, int h1, int l2, int h2) {
        int remLow;
        int remHigh;
        Long64 rem;
        if (isInt32(l1, h1)) {
            if (isInt32(l2, h2)) {
                return fromTwoInt(l1 % l2, l1 >> 31);
            } else {
                if (l1 == IntMinValue && (l2 == IntMinValue && h2 == 0)) {
                    return LongZero;
                } else {
                    return fromTwoInt(l1, h1);
                }
            }
        } else {
            int newL1 = l1;
            int newH1 = h1;
            int newL2 = l2;
            int newH2 = h2;
            // inline absolute value
            if (h2 < 0) {
                if (l2 != 0) {
                    newH2 = ~h2;
                } else {
                    newH2 = -h2;
                }
                newL2 = -l2;
            }
            // inline absolute value
            if (h1 < 0) {
                if (l1 != 0) {
                    newH1 = ~h1;
                } else {
                    newH1 = -h1;
                }
                newL1 = -l1;
            }
            rem = modUnsignedInternal(newL1, newH1, newL2, newH2);
            remLow = rem.low;
            remHigh = rem.high;
        }
        if (h1 < 0) {
            // negative dividend, need to flip sign
            // inline not
            remLow = ~rem.low;
            remHigh = ~rem.high;
            // inline addition of 1
            // inline "+1"
            remLow = remLow + 1;
            // overflow from low to high
            if (remLow == 0) {
                remHigh = remHigh + 1;
            }
        }
        return new Long64(remLow, remHigh);
    }

    /**
     * Divide two longs. Both parameters are assumed to be positive.
     *
     * @param l1 low bits of dividend
     * @param h1 high bits of dividend
     * @param l2 low bits of divisor
     * @param h2 high bits of divisor
     * @return quotient
     */
    private static Long64 divUnsignedInternal(int l1, int h1, int l2, int h2) {
        if (isUnsignedSafeDouble(h1)) {
            if (isUnsignedSafeDouble(h2)) {
                double dividendAsDouble = toNumber(new Long64(l1, h1));
                double divisorAsDouble = toNumber(new Long64(l2, h2));
                double resultDouble = dividendAsDouble / divisorAsDouble;
                return Long64.fromUnsignedSafeDouble(resultDouble);
            } else {
                return LongZero;
            }
        } else {
            if (h2 == 0 && isPowerOfTwoIKnowItsNotZero(l2)) {
                int pow = log2OfPowerOfTwo(l2);
                return fromTwoInt(l1 >>> pow | (h1 << 1 << (31 - pow)), (h1 >>> pow));
            } else if (l2 == 0 && isPowerOfTwoIKnowItsNotZero(h2)) {
                int pow = log2OfPowerOfTwo(h2);
                return fromTwoInt(h1 >>> pow, 0);
            } else {
                return unsignedDivModHelper(l1, h1, l2, h2, AskQuotient);
            }
        }
    }

    /**
     * Convert a given integer to an unsigned integer.
     *
     * @param a given integer
     * @return given integer converted to an unsigned integer using JavaScript semantics
     */
    @JSRawCall
    @JS("return a >>> 0;")
    private static native double asUInt(int a);

    /**
     * Convert a long variable to a double. This assumes that the long variable can safely be
     * represented with a double.
     *
     * @param low low bits of the long
     * @param high high bits of the long
     * @return double representation of the long
     */
    private static double asUnsignedSafeDouble(int low, int high) {
        return high * TwoPow32 + asUInt(low);
    }

    /**
     * Compute the remainder of two positive longs.
     *
     * @param l1 low bits of dividend
     * @param h1 high bits of dividend
     * @param l2 low bits of divisor
     * @param h2 high bits of divisor
     * @return remainder
     */
    private static Long64 modUnsignedInternal(int l1, int h1, int l2, int h2) {
        if (isUnsignedSafeDouble(h1)) {
            if (isUnsignedSafeDouble(h2)) {
                // inline toNumber()
                double dividendDouble = asUnsignedSafeDouble(l1, h1);
                double divisorDouble = asUnsignedSafeDouble(l2, h2);
                double remDouble = dividendDouble % divisorDouble;
                return fromUnsignedSafeDouble(remDouble);
            } else {
                // divisor > dividend
                return fromTwoInt(l1, h1);
            }
        } else {
            if (h2 == 0 && isPowerOfTwoIKnowItsNotZero(l2)) {
                return fromTwoInt(l1 & (l2 - 1), 0);
            } else if (l2 == 0 && isPowerOfTwoIKnowItsNotZero(h2)) {
                return fromTwoInt(l1, h1 & (h2 - 1));
            } else {
                return unsignedDivModHelper(l1, h1, l2, h2, AskRemainder);
            }
        }
    }

    /**
     * Helper function for both division and remainder computation. Assumes that the given longs are
     * positive and the divisor is not 0.
     *
     * @param l1 low bits of dividend
     * @param h1 high bits of dividend
     * @param l2 low bits of divisor
     * @param h2 high bits of divisor
     * @param mode switch to decide whether division or remainder should be returned
     * @return quotient or remainder depending on the mode
     */
    private static Long64 unsignedDivModHelper(int l1, int h1, int l2, int h2, int mode) {
        int shift = numberOfLeadingZeroes(l2, h2) - numberOfLeadingZeroes(l1, h1);
        Long64 initialBShift = slFromNum(fromTwoInt(l2, h2), shift);
        int bShiftLo = initialBShift.low;
        int bShiftHi = initialBShift.high;
        int remLo = l1;
        int remHi = h1;
        int quotLo = 0;
        int quotHi = 0;

        while (shift >= 0 && (remHi & UnsignedSafeDoubleMask) != 0) {
            if (unsignedGE(remLo, remHi, bShiftLo, bShiftHi)) {
                Long64 newRem = sub(fromTwoInt(remLo, remHi), fromTwoInt(bShiftLo, bShiftHi));
                remLo = newRem.low;
                remHi = newRem.high;
                if (shift < 32) {
                    quotLo = quotLo | (1 << shift);
                } else {
                    quotHi = quotHi | (1 << (shift - 32));
                }
            }
            shift -= 1;
            Long64 newBShift = usrFromNum(fromTwoInt(bShiftLo, bShiftHi), 1);
            bShiftLo = newBShift.low;
            bShiftHi = newBShift.high;
        }

        // rem < 2^53 -> finish with a double division
        if (unsignedGE(remLo, remHi, l2, h2)) {
            double remDouble = toNumber(fromTwoInt(remLo, remHi));
            double bDouble = toNumber(fromTwoInt(l2, h2));
            if (mode == AskQuotient) {
                Long64 remDivBDouble = fromUnsignedSafeDouble(remDouble / bDouble);
                Long64 newQuot = addInternal(quotLo, quotHi, remDivBDouble.low, remDivBDouble.high);
                quotLo = newQuot.low;
                quotHi = newQuot.high;
            }
            if (mode == AskRemainder) {
                double remDivDouble = remDouble % bDouble;
                Long64 rem = fromUnsignedSafeDouble(remDivDouble);
                remLo = rem.low;
                remHi = rem.high;
            }
        }
        if (mode == AskQuotient) {
            return fromTwoInt(quotLo, quotHi);
        } else {
            return fromTwoInt(remLo, remHi);
        }
    }

    private static Long64 slInternal(int l1, int h1, int l2) {
        int toShift = l2 & 63;
        if (toShift == 0) {
            return new Long64(l1, h1);
        } else {
            if (toShift >= 32) {
                int shiftout = l1 << (toShift - 32);
                return fromTwoInt(0, shiftout);
            } else {
                int shiftIn = l1 >>> (32 - toShift);
                return fromTwoInt(l1 << toShift, shiftIn | (h1 << toShift));
            }
        }
    }

    private static Long64 srInternal(int l1, int h1, int l2) {
        int toShift = l2 & 63;
        if (toShift == 0) {
            return fromTwoInt(l1, h1);
        }
        if (toShift >= 32) {
            return fromTwoInt(h1 >> (toShift - 32), h1 >= 0 ? 0 : -1/* shift in sign if necessary */);
        } else {
            int shiftout = h1 << (32 - toShift);
            return fromTwoInt((l1 >>> toShift) | (shiftout), h1 >> toShift);
        }
    }

    private static Long64 usrInternal(int l1, int h1, int l2) {
        int toShift = l2 & 63;
        if (toShift == 0) {
            return fromTwoInt(l1, h1);
        }
        if (toShift == 32) {
            return fromTwoInt(h1, 0);
        }
        if (toShift >= 32) {
            return fromTwoInt(h1 >>> (toShift - 32), 0/* usr does not shift sign */);
        } else {
            int shiftout = h1 << (32 - toShift);
            return fromTwoInt((l1 >>> toShift) | (shiftout), h1 >>> toShift);
        }
    }

    private static Long64 andInternal(int l1, int h1, int l2, int h2) {
        return fromTwoInt(l1 & l2, h1 & h2);
    }

    private static Long64 orInternal(int l1, int h1, int l2, int h2) {
        return fromTwoInt(l1 | l2, h1 | h2);
    }

    private static Long64 xorInternal(int l1, int h1, int l2, int h2) {
        return fromTwoInt(l1 ^ l2, h1 ^ h2);
    }

    private static Long64 notInternal(int l1, int h1) {
        return fromTwoInt(~l1, ~h1);
    }

    private static Long64 negateInternal(int low, int high) {
        return fromTwoInt(-low, low != 0 ? ~high : -high);
    }

    private static boolean equalInternal(int l1, int h1, int l2, int h2) {
        return l1 == l2 && h1 == h2;
    }

    private static boolean testInternal(int l1, int h1, int l2, int h2) {
        return (l1 & l2) == 0 && (h1 & h2) == 0;
    }

    public static Long64 add(Long64 left, Long64 right) {
        return addInternal(left.low, left.high, right.low, right.high);
    }

    public static Long64 sub(Long64 left, Long64 right) {
        return subInternal(left.low, left.high, right.low, right.high);
    }

    public static Long64 mul(Long64 left, Long64 right) {
        return mulInternal(left.low, left.high, right.low, right.high);
    }

    public static Long64 div(Long64 left, Long64 right) {
        return divInternal(left.low, left.high, right.low, right.high);
    }

    public static Long64 and(Long64 left, Long64 right) {
        return andInternal(left.low, left.high, right.low, right.high);
    }

    public static Long64 or(Long64 left, Long64 right) {
        return orInternal(left.low, left.high, right.low, right.high);
    }

    public static Long64 xor(Long64 left, Long64 right) {
        return xorInternal(left.low, left.high, right.low, right.high);
    }

    public static Long64 abs(Long64 x) {
        return absInternal(x.low, x.high);
    }

    public static Long64 not(Long64 left) {
        return notInternal(left.low, left.high);
    }

    public static Long64 mod(Long64 left, Long64 right) {
        return modInternal(left.low, left.high, right.low, right.high);
    }

    public static Long64 sl(Long64 left, Long64 right) {
        return slInternal(left.low, left.high, right.low);
    }

    public static Long64 sr(Long64 left, Long64 right) {
        return srInternal(left.low, left.high, right.low);
    }

    public static Long64 usr(Long64 left, Long64 right) {
        return usrInternal(left.low, left.high, right.low);
    }

    public static Long64 slFromNum(Long64 left, int num) {
        return slInternal(left.low, left.high, num);
    }

    public static Long64 srFromNum(Long64 left, int num) {
        return srInternal(left.low, left.high, num);
    }

    public static Long64 usrFromNum(Long64 left, int num) {
        return usrInternal(left.low, left.high, num);
    }

    public static Long64 negate(Long64 a) {
        return negateInternal(a.low, a.high);
    }

    public static boolean equal(Long64 l, Long64 r) {
        return equalInternal(l.low, l.high, r.low, r.high);
    }

    public static boolean lessThan(Long64 l, Long64 r) {
        if (l.high == r.high) {
            return (l.low ^ IntMinValue) < (r.low ^ IntMinValue);
        } else {
            return l.high < r.high;
        }
    }

    public static boolean belowThan(Long64 l, Long64 r) {
        if (l.high == r.high) {
            return (l.low ^ IntMinValue) < (r.low ^ IntMinValue);
        } else {
            return (l.high ^ IntMinValue) < (r.high ^ IntMinValue);
        }
    }

    public static boolean test(Long64 l, Long64 r) {
        return testInternal(l.low, l.high, r.low, r.high);
    }

    private static boolean unsignedGE(int l1, int h1, int l2, int h2) {
        if (h1 == h2) {
            return (l1 ^ IntMinValue) >= (l2 ^ IntMinValue);
        } else {
            return (h1 ^ IntMinValue) >= (h2 ^ IntMinValue);
        }
    }

    /**
     * Compute the number of leading zeroes in a long.
     *
     * @param low low bits
     * @param high high bits
     * @return number of leading zeroes
     */
    private static int numberOfLeadingZeroes(int low, int high) {
        if (high != 0) {
            return numberOfLeadingZeroesInt(high);
        } else {
            return numberOfLeadingZeroesInt(low) + 32;
        }
    }

    /**
     * Return logarithm to base of 2 of a given power of 2.
     *
     * @param h2 a power of 2.
     * @return logarithm of the given number
     */
    private static int log2OfPowerOfTwo(int h2) {
        return 31 - numberOfLeadingZeroesInt(h2);
    }

    /**
     * Compute the number of leading zeroes in an int.
     *
     * @param k give integer
     * @return number of leading zeroes
     */
    private static int numberOfLeadingZeroesInt(int k) {
        int i = k;
        if (i == 0) {
            return 32;
        }
        int n = 1;
        if (i >>> 16 == 0) {
            n += 16;
            i <<= 16;
        }
        if (i >>> 24 == 0) {
            n += 8;
            i <<= 8;
        }
        if (i >>> 28 == 0) {
            n += 4;
            i <<= 4;
        }
        if (i >>> 30 == 0) {
            n += 2;
            i <<= 2;
        }
        n -= i >>> 31;
        return n;
    }

    private static boolean isPowerOfTwoIKnowItsNotZero(int a) {
        return (a & (a - 1)) == 0;
    }

    /**
     * Check if a long can safely be represented as a double.
     *
     * @param h1 high bits of the long
     * @return true iff the long can safely be represented by a double
     */
    private static boolean isUnsignedSafeDouble(int h1) {
        return (h1 & UnsignedSafeDoubleMask) == 0;
    }

    /**
     * Create a new long variable from the high and low bits.
     *
     * @param low low bits
     * @param high high bits
     * @return new long variable
     */
    public static Long64 fromTwoInt(int low, int high) {
        return new Long64(low, high);
    }

    /**
     * Create a new long variable by zero extending a given integer.
     *
     * @param a integer
     * @return new long variable
     */
    public static Long64 fromZeroExtend(int a) {
        return new Long64(a, 0);
    }

    public static int lowBits(Long64 a) {
        return a.low;
    }

    public static int highBits(Long64 a) {
        return a.high;
    }

    public static int compare(Long64 a, Long64 b) {
        if (a.high == b.high) {
            if (a.low == b.low) {
                return 0;
            } else if ((a.low ^ IntMinValue) < (b.low ^ IntMinValue)) {
                return -1;
            } else {
                return 1;
            }
        } else {
            if (a.high < b.high) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    public static int compareUnsigned(Long64 a, Long64 b) {
        if (a.high == b.high) {
            if (a.low == b.low) {
                return 0;
            } else if ((a.low ^ IntMinValue) < (b.low ^ IntMinValue)) {
                return -1;
            } else {
                return 1;
            }
        } else {
            if ((a.high ^ IntMinValue) < (b.high ^ IntMinValue)) {
                return -1;
            } else {
                return 1;
            }
        }
    }

    /**
     * Converts long to a double.
     *
     * @return double representation of the long
     */
    public static double toNumber(Long64 a) {
        return asUnsignedSafeDouble(a.low, a.high);
    }

}
