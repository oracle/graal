/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.strings.TStringOps.writeToByteArray;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.strings.TruffleString.NumberFormatException.Reason;

final class NumberConversion {

    private static final double MAX_SAFE_INTEGER = Math.pow(2, 53) - 1;
    private static final double MIN_SAFE_INTEGER = -MAX_SAFE_INTEGER;
    private static final long MAX_SAFE_INTEGER_LONG = (long) Math.pow(2, 53) - 1;
    private static final long MIN_SAFE_INTEGER_LONG = (long) MIN_SAFE_INTEGER;

    /**
     * See jdk.internal.util.DecimalDigits. Using a String so that the array is treated as stable.
     * Each element of the char[] array represents the packaging of two ascii characters.
     *
     * @see #digitPair(int)
     * @see #digitPairHi(char)
     * @see #digitPairLo(char)
     */
    private static final String DIGIT_PAIRS;

    static {
        char[] digits = new char[10 * 10];
        for (int i = 0; i < 10; i++) {
            char hi = (char) (i + '0');
            for (int j = 0; j < 10; j++) {
                char lo = (char) ((j + '0') << 8);
                digits[i * 10 + j] = (char) (hi | lo);
            }
        }
        DIGIT_PAIRS = new String(digits);
    }

    private NumberConversion() {
    }

    static int parseInt(Node node, TruffleStringIterator it, TruffleString.Encoding encoding, int radix, InlinedBranchProfile errorProfile,
                    TruffleStringIterator.InternalNextNode nextNode) throws TruffleString.NumberFormatException {
        return (int) parseNum(node, it, encoding, radix, errorProfile, Integer.MIN_VALUE, Integer.MAX_VALUE, nextNode);
    }

    static long parseLong(Node node, TruffleStringIterator it, TruffleString.Encoding encoding, int radix, InlinedBranchProfile errorProfile,
                    TruffleStringIterator.InternalNextNode nextNode) throws TruffleString.NumberFormatException {
        return parseNum(node, it, encoding, radix, errorProfile, Long.MIN_VALUE, Long.MAX_VALUE, nextNode);
    }

    static int parseInt7Bit(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int stride, int radix, InlinedBranchProfile errorProfile) throws TruffleString.NumberFormatException {
        return (int) parseNum7Bit(node, a, arrayA, offsetA, stride, radix, errorProfile, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    static long parseLong7Bit(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int stride, int radix, InlinedBranchProfile errorProfile) throws TruffleString.NumberFormatException {
        return parseNum7Bit(node, a, arrayA, offsetA, stride, radix, errorProfile, Long.MIN_VALUE, Long.MAX_VALUE);
    }

    static boolean isSafeInteger(long value) {
        return MIN_SAFE_INTEGER_LONG <= value && value <= MAX_SAFE_INTEGER_LONG;
    }

    private static long parseNum(Node node, TruffleStringIterator it, TruffleString.Encoding encoding, int radix, InlinedBranchProfile errorProfile, long min, long max,
                    TruffleStringIterator.InternalNextNode nextNode) throws TruffleString.NumberFormatException {
        checkArgs(node, it, radix, errorProfile);
        long result = 0;
        boolean negative = false;
        long limit = -max;
        if (it.hasNext()) {
            int firstChar = nextNode.execute(node, it, encoding);
            if (firstChar < '0') { // Possible leading "+" or "-"
                if (firstChar == '-') {
                    negative = true;
                    limit = min;
                } else if (firstChar != '+') {
                    errorProfile.enter(node);
                    throw numberFormatException(it, Reason.INVALID_CODEPOINT);
                }
                if (!it.hasNext()) { // Cannot have lone "+" or "-"
                    errorProfile.enter(node);
                    throw numberFormatException(it, Reason.LONE_SIGN);
                }
            } else {
                int digit = Character.digit(firstChar, radix);
                if (digit < 0) {
                    errorProfile.enter(node);
                    throw numberFormatException(it, Reason.INVALID_CODEPOINT);
                }
                assert result >= limit + digit;
                result -= digit;
            }
            long multmin = limit / radix;
            while (it.hasNext()) {
                // Accumulating negatively avoids surprises near MAX_VALUE
                int digit = Character.digit(nextNode.execute(node, it, encoding), radix);
                if (digit < 0) {
                    errorProfile.enter(node);
                    throw numberFormatException(it, Reason.INVALID_CODEPOINT);
                }
                if (result < multmin) {
                    errorProfile.enter(node);
                    throw numberFormatException(it, Reason.OVERFLOW);
                }
                result *= radix;
                if (result < limit + digit) {
                    errorProfile.enter(node);
                    throw numberFormatException(it, Reason.OVERFLOW);
                }
                result -= digit;
            }
        } else {
            errorProfile.enter(node);
            throw numberFormatException(it, Reason.EMPTY);
        }
        return negative ? result : -result;
    }

    private static long parseNum7Bit(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int stride, int radix, InlinedBranchProfile errorProfile, long min, long max)
                    throws TruffleString.NumberFormatException {
        CompilerAsserts.partialEvaluationConstant(stride);
        assert TStringGuards.is7Bit(a.codeRange());
        checkRadix(node, a, radix, errorProfile);
        checkEmptyStr(node, a, errorProfile);
        long result = 0;
        boolean negative = false;
        long limit = -max;
        int i = 0;
        int firstChar = TStringOps.readValue(a, arrayA, offsetA, stride, i);
        if (firstChar < '0') { // Possible leading "+" or "-"
            if (firstChar == '-') {
                negative = true;
                limit = min;
            } else if (firstChar != '+') {
                errorProfile.enter(node);
                throw numberFormatException(a, i, Reason.INVALID_CODEPOINT);
            }
            if (a.length() == 1) { // Cannot have lone "+" or "-"
                errorProfile.enter(node);
                throw numberFormatException(a, i, Reason.LONE_SIGN);
            }
            i++;
        }
        long multmin = limit / radix;
        while (i < a.length()) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            int c = TStringOps.readValue(a, arrayA, offsetA, stride, i++);
            final int digit = parseDigit7Bit(node, a, i, radix, errorProfile, c);
            if (result < multmin) {
                errorProfile.enter(node);
                throw numberFormatException(a, i, Reason.OVERFLOW);
            }
            result *= radix;
            if (result < limit + digit) {
                errorProfile.enter(node);
                throw numberFormatException(a, i, Reason.OVERFLOW);
            }
            result -= digit;
        }
        return negative ? result : -result;
    }

    private static int parseDigit7Bit(Node node, AbstractTruffleString a, int i, int radix, InlinedBranchProfile errorProfile, int c) throws TruffleString.NumberFormatException {
        if ('0' <= c && c <= Math.min((radix - 1) + '0', '9')) {
            return c & 0xf;
        } else if (radix > 10) {
            int lc = c | 0x20; // lowercase
            if ('a' <= lc && lc <= (radix - 11) + 'a') {
                return lc - ('a' - 10);
            }
        }
        errorProfile.enter(node);
        throw numberFormatException(a, i, Reason.INVALID_CODEPOINT);
    }

    private static void checkArgs(Node node, TruffleStringIterator it, int radix, InlinedBranchProfile errorProfile) throws TruffleString.NumberFormatException {
        assert it != null;
        checkRadix(node, it.a, radix, errorProfile);
    }

    private static void checkRadix(Node node, AbstractTruffleString a, int radix, InlinedBranchProfile errorProfile) throws TruffleString.NumberFormatException {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
            errorProfile.enter(node);
            throw numberFormatException(a, Reason.UNSUPPORTED_RADIX);
        }
    }

    private static void checkEmptyStr(Node node, AbstractTruffleString a, InlinedBranchProfile errorProfile) throws TruffleString.NumberFormatException {
        if (a.isEmpty()) {
            errorProfile.enter(node);
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

    @InliningCutoff
    static byte[] longToString(long i, int length) {
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

    /**
     * Calculates <code>floor(log<sub>2</sub>(x))</code> for all positive x. Returns 0 for
     * <code>x == 0</code>, and 63 for all <code>x < 0</code>, which happens to be the expected
     * result for <code>floor(log<sub>2</sub>(abs(Long.MIN_VALUE)))</code> (only).
     */
    private static int floorLog2(long n) {
        assert n >= 0 || n == Long.MIN_VALUE : n;
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
        long n = longValue;
        final int sign;
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, n < 0)) {
            sign = 1;
            n = -n;
        } else {
            sign = 0;
        }
        // Works for Long.MIN_VALUE, too (floorLog2(MIN_VALUE) == 63)
        final int bits = floorLog2(n);
        final int digits = (int) ((LONG_LENGTH_TABLE[bits] + (n >> (bits >> 2))) >> 52);
        return sign + digits;
    }

    static void writeLongToBytes(long i, byte[] buf, int stride, int fromIndex, int length) {
        writeLongToBytesIntl(i, fromIndex + length, buf, stride);
    }

    /**
     * Write decimal string representation of the long value into the target buffer backwards
     * starting with the least significant digit at the specified index (exclusive).
     *
     * @implNote See jdk.internal.util.DecimalDigits#getCharsLatin1(long, int, byte[]). This method
     *           converts positive inputs into negative values, to cover the Long.MIN_VALUE case.
     *
     * @param value value to convert to string
     * @param index next index, after the least significant digit
     * @param buf target buffer
     * @param stride target buffer stride (0, 1, or 2)
     */
    private static void writeLongToBytesIntl(long value, int index, byte[] buf, int stride) {
        long i = value;
        boolean negative = i < 0;
        if (!negative) {
            i = -i;
        }

        // Get 2 digits/iteration using longs until quotient fits into an int
        int bytePos = index;
        while (i < Integer.MIN_VALUE) {
            long q = i / 100;
            int r = (int) ((q * 100) - i);
            i = q;
            bytePos -= 2;
            writeDigitPairToByteArray(buf, stride, bytePos, r);
        }

        // Get 2 digits/iteration using ints
        int i2 = (int) i;
        while (i2 <= -100) {
            int q = i2 / 100;
            int r = (q * 100) - i2;
            i2 = q;
            bytePos -= 2;
            writeDigitPairToByteArray(buf, stride, bytePos, r);
        }

        // We know there are at most two digits and at least one digit left at this point.
        if (i2 < -9) {
            int r = -i2;
            bytePos -= 2;
            writeDigitPairToByteArray(buf, stride, bytePos, r);
        } else {
            int r = '0' - i2;
            writeToByteArray(buf, stride, --bytePos, r);
        }
        if (negative) {
            writeToByteArray(buf, stride, --bytePos, '-');
        }
    }

    static void writeIntToBytes(int i, byte[] buf, int stride, int fromIndex, int length) {
        writeIntToBytesIntl(i, fromIndex + length, buf, stride);
    }

    /**
     * Write decimal string representation of the int value into the target buffer backwards
     * starting with the least significant digit at the specified index (exclusive).
     *
     * @implNote See jdk.internal.util.DecimalDigits#getCharsLatin1(int, int, byte[]). This method
     *           converts positive inputs into negative values, to cover the Integer.MIN_VALUE case.
     *
     * @param value value to convert to string
     * @param index next index, after the least significant digit
     * @param buf target buffer
     * @param stride target buffer stride (0, 1, or 2)
     */
    private static void writeIntToBytesIntl(int value, int index, byte[] buf, int stride) {
        int i = value;
        boolean negative = i < 0;
        if (!negative) {
            i = -i;
        }

        int bytePos = index;
        // Get 2 digits/iteration using ints
        while (i <= -100) {
            int q = i / 100;
            int r = (q * 100) - i;
            i = q;
            bytePos -= 2;
            writeDigitPairToByteArray(buf, stride, bytePos, r);
        }

        // We know there are at most two digits and at least one digit left at this point.
        if (i < -9) {
            int r = -i;
            bytePos -= 2;
            writeDigitPairToByteArray(buf, stride, bytePos, r);
        } else {
            writeToByteArray(buf, stride, --bytePos, ('0' - i));
        }
        if (negative) {
            writeToByteArray(buf, stride, --bytePos, '-');
        }
    }

    /**
     * Converts an integer value to a two digit character pair.
     */
    private static char digitPair(int i) {
        assert 0 <= i && i <= 99 : i;
        return DIGIT_PAIRS.charAt(i);
    }

    /**
     * Extracts the leading (more significant) digit from a digit pair.
     */
    private static int digitPairHi(char digitPair) {
        return (digitPair & 0xff);
    }

    /**
     * Extracts the trailing (less significant) digit from a digit pair.
     */
    private static int digitPairLo(char digitPair) {
        return (digitPair >>> 8);
    }

    /**
     * Writes the integer as a two-decimal-digit sequence into the buffer at the specified position.
     */
    private static void writeDigitPairToByteArray(byte[] buf, int stride, int bufPos, int r) {
        var digitPair = digitPair(r);
        writeToByteArray(buf, stride, bufPos, digitPairHi(digitPair));
        writeToByteArray(buf, stride, bufPos + 1, digitPairLo(digitPair));
    }

    /**
     * Computes Long.toString(value).hashCode() from the long value.
     *
     * @implNote This method converts positive inputs into negative values, to cover the
     *           Long.MIN_VALUE case.
     * @see #writeLongToBytesIntl
     */
    static int computeLongStringHashCode(long value) {
        long i = value;
        boolean negative = (i < 0);
        if (!negative) {
            i = -i;
        }

        int hash = 0;
        int coef = 1;
        // Get 2 digits/iteration using longs until quotient fits into an int
        while (i < Integer.MIN_VALUE) {
            long q = i / 100;
            int r = (int) ((q * 100) - i);
            i = q;
            var digitPair = digitPair(r);
            hash += coef * digitPairLo(digitPair);
            coef *= 31;
            hash += coef * digitPairHi(digitPair);
            coef *= 31;
        }

        // Get 2 digits/iteration using ints
        int i2 = (int) i;
        while (i2 <= -100) {
            int q = i2 / 100;
            int r = (q * 100) - i2;
            i2 = q;
            var digitPair = digitPair(r);
            hash += coef * digitPairLo(digitPair);
            coef *= 31;
            hash += coef * digitPairHi(digitPair);
            coef *= 31;
        }

        // We know there are at most two digits and at least one digit left at this point.
        if (i2 < -9) {
            int r = -i2;
            var digitPair = digitPair(r);
            hash += coef * digitPairLo(digitPair);
            coef *= 31;
            hash += coef * digitPairHi(digitPair);
            coef *= 31;
        } else {
            hash += coef * ('0' - i2);
            coef *= 31;
        }

        if (negative) {
            hash += coef * '-';
        }
        assert hash == Long.toString(value).hashCode() : value;
        return hash;
    }
}
