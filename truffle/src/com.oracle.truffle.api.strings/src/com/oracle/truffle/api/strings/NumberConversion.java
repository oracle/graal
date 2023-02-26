/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.api.strings;

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.strings.TruffleString.NumberFormatException.Reason;

final class NumberConversion {

    private static final byte[] INT_MIN_VALUE_BYTES = {'-', '2', '1', '4', '7', '4', '8', '3', '6', '4', '8'};
    private static final byte[] LONG_MIN_VALUE_BYTES = {'-', '9', '2', '2', '3', '3', '7', '2', '0', '3', '6', '8', '5', '4', '7', '7', '5', '8', '0', '8'};
    private static final byte[] DIGITS = {
                    '0', '1', '2', '3', '4', '5',
                    '6', '7', '8', '9', 'a', 'b',
                    'c', 'd', 'e', 'f', 'g', 'h',
                    'i', 'j', 'k', 'l', 'm', 'n',
                    'o', 'p', 'q', 'r', 's', 't',
                    'u', 'v', 'w', 'x', 'y', 'z'
    };
    private static final byte[] DIGIT_TENS = {
                    '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
                    '1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
                    '2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
                    '3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
                    '4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
                    '5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
                    '6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
                    '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
                    '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
                    '9', '9', '9', '9', '9', '9', '9', '9', '9', '9',
    };
    private static final byte[] DIGIT_ONES = {
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
    };

    private static final double MAX_SAFE_INTEGER = Math.pow(2, 53) - 1;
    private static final double MIN_SAFE_INTEGER = -MAX_SAFE_INTEGER;
    private static final long MAX_SAFE_INTEGER_LONG = (long) Math.pow(2, 53) - 1;
    private static final long MIN_SAFE_INTEGER_LONG = (long) MIN_SAFE_INTEGER;

    static int parseInt(TruffleStringIterator it, int radix, BranchProfile errorProfile,
                    TruffleStringIterator.NextNode nextNode) throws TruffleString.NumberFormatException {
        return (int) parseNum(it, radix, errorProfile, Integer.MIN_VALUE, Integer.MAX_VALUE, nextNode);
    }

    static long parseLong(TruffleStringIterator it, int radix, BranchProfile errorProfile,
                    TruffleStringIterator.NextNode nextNode) throws TruffleString.NumberFormatException {
        return parseNum(it, radix, errorProfile, Long.MIN_VALUE, Long.MAX_VALUE, nextNode);
    }

    static int parseInt7Bit(AbstractTruffleString a, Object ptrA, int stride, int radix, BranchProfile errorProfile) throws TruffleString.NumberFormatException {
        return (int) parseNum7Bit(a, ptrA, stride, radix, errorProfile, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    static long parseLong7Bit(AbstractTruffleString a, Object ptrA, int stride, int radix, BranchProfile errorProfile) throws TruffleString.NumberFormatException {
        return parseNum7Bit(a, ptrA, stride, radix, errorProfile, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    static boolean isSafeInteger(long value) {
        return MIN_SAFE_INTEGER_LONG <= value && value <= MAX_SAFE_INTEGER_LONG;
    }

    private static long parseNum(TruffleStringIterator it, int radix, BranchProfile errorProfile, long min, long max,
                    TruffleStringIterator.NextNode nextNode) throws TruffleString.NumberFormatException {
        checkArgs(it, radix, errorProfile);
        long result = 0;
        boolean negative = false;
        long limit = -max;
        if (it.hasNext()) {
            int firstChar = nextNode.execute(it);
            if (firstChar < '0') { // Possible leading "+" or "-"
                if (firstChar == '-') {
                    negative = true;
                    limit = min;
                } else if (firstChar != '+') {
                    errorProfile.enter();
                    throw numberFormatException(it, Reason.INVALID_CODEPOINT);
                }
                if (!it.hasNext()) { // Cannot have lone "+" or "-"
                    errorProfile.enter();
                    throw numberFormatException(it, Reason.LONE_SIGN);
                }
            } else {
                int digit = Character.digit(firstChar, radix);
                if (digit < 0) {
                    errorProfile.enter();
                    throw numberFormatException(it, Reason.INVALID_CODEPOINT);
                }
                assert result >= limit + digit;
                result -= digit;
            }
            long multmin = limit / radix;
            while (it.hasNext()) {
                // Accumulating negatively avoids surprises near MAX_VALUE
                int digit = Character.digit(nextNode.execute(it), radix);
                if (digit < 0) {
                    errorProfile.enter();
                    throw numberFormatException(it, Reason.INVALID_CODEPOINT);
                }
                if (result < multmin) {
                    errorProfile.enter();
                    throw numberFormatException(it, Reason.OVERFLOW);
                }
                result *= radix;
                if (result < limit + digit) {
                    errorProfile.enter();
                    throw numberFormatException(it, Reason.OVERFLOW);
                }
                result -= digit;
            }
        } else {
            errorProfile.enter();
            throw numberFormatException(it, Reason.EMPTY);
        }
        return negative ? result : -result;
    }

    private static long parseNum7Bit(AbstractTruffleString a, Object arrayA, int stride, int radix, BranchProfile errorProfile, long min, long max) throws TruffleString.NumberFormatException {
        CompilerAsserts.partialEvaluationConstant(stride);
        assert TStringGuards.is7Bit(TStringInternalNodes.GetCodeRangeNode.getUncached().execute(a));
        checkRadix(a, radix, errorProfile);
        checkEmptyStr(a, errorProfile);
        long result = 0;
        boolean negative = false;
        long limit = -max;
        int i = 0;
        int firstChar = TStringOps.readValue(a, arrayA, stride, i);
        if (firstChar < '0') { // Possible leading "+" or "-"
            if (firstChar == '-') {
                negative = true;
                limit = min;
            } else if (firstChar != '+') {
                errorProfile.enter();
                throw numberFormatException(a, i, Reason.INVALID_CODEPOINT);
            }
            if (a.length() == 1) { // Cannot have lone "+" or "-"
                errorProfile.enter();
                throw numberFormatException(a, i, Reason.LONE_SIGN);
            }
            i++;
        }
        long multmin = limit / radix;
        while (i < a.length()) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            int c = TStringOps.readValue(a, arrayA, stride, i++);
            final int digit = parseDigit7Bit(a, i, radix, errorProfile, c);
            if (result < multmin) {
                errorProfile.enter();
                throw numberFormatException(a, i, Reason.OVERFLOW);
            }
            result *= radix;
            if (result < limit + digit) {
                errorProfile.enter();
                throw numberFormatException(a, i, Reason.OVERFLOW);
            }
            result -= digit;
        }
        return negative ? result : -result;
    }

    private static int parseDigit7Bit(AbstractTruffleString a, int i, int radix, BranchProfile errorProfile, int c) throws TruffleString.NumberFormatException {
        if ('0' <= c && c <= Math.min((radix - 1) + '0', '9')) {
            return c & 0xf;
        } else if (radix > 10) {
            int lc = c | 0x20; // lowercase
            if ('a' <= lc && lc <= (radix - 11) + 'a') {
                return lc - ('a' - 10);
            }
        }
        errorProfile.enter();
        throw numberFormatException(a, i, Reason.INVALID_CODEPOINT);
    }

    private static void checkArgs(TruffleStringIterator it, int radix, BranchProfile errorProfile) throws TruffleString.NumberFormatException {
        assert it != null;
        checkRadix(it.a, radix, errorProfile);
    }

    private static void checkRadix(AbstractTruffleString a, int radix, BranchProfile errorProfile) throws TruffleString.NumberFormatException {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            errorProfile.enter();
            throw numberFormatException(a, Reason.UNSUPPORTED_RADIX);
        }
    }

    private static void checkEmptyStr(AbstractTruffleString a, BranchProfile errorProfile) throws TruffleString.NumberFormatException {
        if (a.isEmpty()) {
            errorProfile.enter();
            throw numberFormatException(a, Reason.EMPTY);
        }
    }

    @TruffleBoundary
    static TruffleString.NumberFormatException numberFormatException(AbstractTruffleString a, Reason msg) {
        return new TruffleString.NumberFormatException(a, msg);
    }

    @TruffleBoundary
    static TruffleString.NumberFormatException numberFormatException(TruffleStringIterator it, Reason msg) {
        return new TruffleString.NumberFormatException(it.a, it.getRawIndex(), 1, msg);
    }

    @TruffleBoundary
    static TruffleString.NumberFormatException numberFormatException(AbstractTruffleString a, int regionOffset, Reason msg) {
        return new TruffleString.NumberFormatException(a, regionOffset, 1, msg);
    }

    @TruffleBoundary
    static TruffleString.NumberFormatException numberFormatException(AbstractTruffleString a, int regionOffset, int regionLength, Reason msg) {
        return new TruffleString.NumberFormatException(a, regionOffset, regionLength, msg);
    }

    static byte[] longToString(long i, int length) {
        if (i == Long.MIN_VALUE) {
            return LONG_MIN_VALUE_BYTES;
        }
        byte[] buf = new byte[length];
        writeLongToBytesIntl(i, length, buf, 0);
        return buf;
    }

    /**
     * Pre-calculated table of the following expression.
     *
     * <pre>
     * {@code
     * digits = ceil(log10(2 ** i))
     * table[i] = ((digits + 1) << 52) - ((10 ** digits) >> (i >> 2))
     * }
     * </pre>
     */
    @CompilationFinal(dimensions = 1) private static final long[] LONG_LENGTH_TABLE = {
                    0x001ffffffffffff6L, 0x001ffffffffffff6L, 0x001ffffffffffff6L, 0x001ffffffffffff6L, 0x002fffffffffffceL, 0x002fffffffffffceL, 0x002fffffffffffceL, 0x003ffffffffffe0cL,
                    0x003fffffffffff06L, 0x003fffffffffff06L, 0x004ffffffffff63cL, 0x004ffffffffff63cL, 0x004ffffffffffb1eL, 0x004ffffffffffb1eL, 0x005fffffffffcf2cL, 0x005fffffffffcf2cL,
                    0x005fffffffffe796L, 0x006fffffffff0bdcL, 0x006fffffffff0bdcL, 0x006fffffffff0bdcL, 0x007ffffffffb3b4cL, 0x007ffffffffb3b4cL, 0x007ffffffffb3b4cL, 0x007ffffffffb3b4cL,
                    0x008fffffffe8287cL, 0x008fffffffe8287cL, 0x008fffffffe8287cL, 0x009fffffff1194d8L, 0x009fffffff88ca6cL, 0x009fffffff88ca6cL, 0x00affffffb57e838L, 0x00affffffb57e838L,
                    0x00affffffdabf41cL, 0x00affffffdabf41cL, 0x00bfffffe8b78918L, 0x00bfffffe8b78918L, 0x00bffffff45bc48cL, 0x00cfffff8b95ad78L, 0x00cfffff8b95ad78L, 0x00cfffff8b95ad78L,
                    0x00dffffdb9ec6358L, 0x00dffffdb9ec6358L, 0x00dffffdb9ec6358L, 0x00dffffdb9ec6358L, 0x00effff4a19df0b8L, 0x00effff4a19df0b8L, 0x00effff4a19df0b8L, 0x00ffff8e502b6730L,
                    0x00ffffc72815b398L, 0x00ffffc72815b398L, 0x010ffdc790d903f0L, 0x010ffdc790d903f0L, 0x010ffee3c86c81f8L, 0x010ffee3c86c81f8L, 0x011ff4e5d43d13b0L, 0x011ff4e5d43d13b0L,
                    0x011ffa72ea1e89d8L, 0x012fc87d25316270L, 0x012fc87d25316270L, 0x012fc87d25316270L, 0x013eea71b9f6ec30L, 0x013eea71b9f6ec30L, 0x013eea71b9f6ec30L, 0x013eea71b9f6ec30L};

    /**
     * Pre-calculated table of the following expression.
     *
     * <pre>
     * {@code
     * digits = ceil(log10(2 ** i))
     * table[i] = (1 << 32) - (10 ** digits) + (digits << 32))
     * }
     * </pre>
     */
    @CompilationFinal(dimensions = 1) private static final long[] INT_LENGTH_TABLE = {
                    0x100000000L, 0x1FFFFFFF6L, 0x1FFFFFFF6L, 0x1FFFFFFF6L, 0x2FFFFFF9CL, 0x2FFFFFF9CL, 0x2FFFFFF9CL, 0x3FFFFFC18L, 0x3FFFFFC18L, 0x3FFFFFC18L, 0x4FFFFD8F0L, 0x4FFFFD8F0L,
                    0x4FFFFD8F0L, 0x4FFFFD8F0L, 0x5FFFE7960L, 0x5FFFE7960L, 0x5FFFE7960L, 0x6FFF0BDC0L, 0x6FFF0BDC0L, 0x6FFF0BDC0L, 0x7FF676980L, 0x7FF676980L, 0x7FF676980L, 0x7FF676980L,
                    0x8FA0A1F00L, 0x8FA0A1F00L, 0x8FA0A1F00L, 0x9C4653600L, 0x9C4653600L, 0x9C4653600L, 0xA00000000L, 0xA00000000L
    };

    private static int floorLog2(long n) {
        return 63 ^ Long.numberOfLeadingZeros(n | 1);
    }

    // From
    // https://lemire.me/blog/2021/06/03/computing-the-number-of-digits-of-an-integer-even-faster/
    // and
    // https://github.com/lemire/Code-used-on-Daniel-Lemire-s-blog/blob/4e6e171a7d/2021/06/03/digitcount.c
    // (license: public domain)
    static int stringLengthInt(long intValue) {
        assert Integer.MIN_VALUE <= intValue && intValue <= Integer.MAX_VALUE;
        long n = intValue;
        final int sign;
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, n < 0)) {
            sign = 1;
            n = -n;
        } else {
            sign = 0;
        }
        final int digits = (int) ((n + INT_LENGTH_TABLE[floorLog2(n)]) >>> 32);
        return sign + digits;
    }

    static int stringLengthLong(long longValue) {
        if (longValue == Long.MIN_VALUE) {
            return LONG_MIN_VALUE_BYTES.length;
        }
        long n = longValue;
        final int sign;
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, n < 0)) {
            sign = 1;
            n = -n;
        } else {
            sign = 0;
        }
        final int bits = floorLog2(n);
        final int digits = (int) ((LONG_LENGTH_TABLE[bits] + (n >> (bits >> 2))) >> 52);
        return sign + digits;
    }

    static void writeLongToBytes(Node location, long i, byte[] buf, int stride, int fromIndex, int length) {
        if (i == Long.MIN_VALUE) {
            TStringOps.arraycopyWithStride(location,
                            LONG_MIN_VALUE_BYTES, 0, 0, 0,
                            buf, 0, stride, fromIndex, LONG_MIN_VALUE_BYTES.length);
        } else {
            writeLongToBytesIntl(i, fromIndex + length, buf, stride);
        }
    }

    /**
     * Copied from {@link Long}, adapted for {@code byte[]}.
     */
    private static void writeLongToBytesIntl(long value, int index, byte[] buf, int stride) {
        long i = value;
        long q;
        int r;
        int bytePos = index;
        byte sign = 0;

        if (i < 0) {
            sign = '-';
            i = -i;
        }

        // Get 2 digits/iteration using longs until quotient fits into an int
        while (i > Integer.MAX_VALUE) {
            q = i / 100;
            // really: r = i - (q * 100);
            r = (int) (i - ((q << 6) + (q << 5) + (q << 2)));
            i = q;
            writeInt(buf, stride, --bytePos, DIGIT_ONES[r]);
            writeInt(buf, stride, --bytePos, DIGIT_TENS[r]);
        }

        // Get 2 digits/iteration using ints
        int q2;
        int i2 = (int) i;
        while (i2 >= 65536) {
            q2 = i2 / 100;
            // really: r = i2 - (q * 100);
            r = i2 - ((q2 << 6) + (q2 << 5) + (q2 << 2));
            i2 = q2;
            writeInt(buf, stride, --bytePos, DIGIT_ONES[r]);
            writeInt(buf, stride, --bytePos, DIGIT_TENS[r]);
        }

        // Fall thru to fast mode for smaller numbers
        // assert(i2 <= 65536, i2);
        do {
            q2 = (i2 * 52429) >>> (16 + 3);
            r = i2 - ((q2 << 3) + (q2 << 1));  // r = i2-(q2*10) ...
            writeInt(buf, stride, --bytePos, DIGITS[r]);
            i2 = q2;
        } while (i2 != 0);
        if (sign != 0) {
            writeInt(buf, stride, --bytePos, sign);
        }
    }

    static void writeIntToBytes(Node location, int i, byte[] buf, int stride, int fromIndex, int length) {
        if (i == Integer.MIN_VALUE) {
            TStringOps.arraycopyWithStride(location,
                            INT_MIN_VALUE_BYTES, 0, 0, 0,
                            buf, 0, stride, fromIndex, INT_MIN_VALUE_BYTES.length);
        } else {
            writeIntToBytesIntl(i, fromIndex + length, buf, stride);
        }
    }

    /**
     * Copied from {@link Integer}, adapted for {@code byte[]}.
     * <p>
     * Places characters representing the integer i into the character array buf. The characters are
     * placed into the buffer backwards starting with the least significant digit at the specified
     * index (exclusive), and working backwards from there.
     * <p>
     * Will fail if i == Integer.MIN_VALUE
     */
    private static void writeIntToBytesIntl(int value, int index, byte[] buf, int stride) {
        int i = value;
        int q;
        int r;
        int bytePos = index;
        byte sign = 0;
        if (i < 0) {
            sign = '-';
            i = -i;
        }
        // Generate two digits per iteration
        while (i >= 65536) {
            q = i / 100;
            // really: r = i - (q * 100);
            r = i - ((q << 6) + (q << 5) + (q << 2));
            i = q;
            writeInt(buf, stride, --bytePos, DIGIT_ONES[r]);
            writeInt(buf, stride, --bytePos, DIGIT_TENS[r]);
        }
        // Fall thru to fast mode for smaller numbers
        // assert(i <= 65536, i);
        do {
            q = (i * 52429) >>> (16 + 3);
            r = i - ((q << 3) + (q << 1));  // r = i-(q*10) ...
            writeInt(buf, stride, --bytePos, DIGITS[r]);
            i = q;
        } while (i != 0);
        if (sign != 0) {
            writeInt(buf, stride, --bytePos, sign);
        }
    }

    private static void writeInt(byte[] buf, int stride, int bytePos, byte value) {
        TStringOps.writeToByteArray(buf, stride, bytePos, value);
    }
}
