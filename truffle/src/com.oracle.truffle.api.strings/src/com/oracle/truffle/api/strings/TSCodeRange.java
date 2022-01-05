/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.strings.TStringGuards.isAscii;
import static com.oracle.truffle.api.strings.TStringGuards.isBytes;
import static com.oracle.truffle.api.strings.TStringGuards.isLatin1;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

final class TSCodeRange {

    /**
     * All codepoints are ASCII (0x00 - 0x7f).
     */
    private static final int CR_7BIT = 0;
    /**
     * All codepoints are LATIN-1 (0x00 - 0xff).
     */
    private static final int CR_8BIT = 1;
    /**
     * All codepoints are BMP (0x0000 - 0xffff, no UTF-16 surrogates).
     */
    private static final int CR_16BIT = 2;
    /**
     * The string is encoded correctly in the given fixed-width encoding.
     */
    private static final int CR_VALID_FIXED_WIDTH = 3;
    /**
     * The string is not encoded correctly in the given fixed-width encoding.
     */
    private static final int CR_BROKEN_FIXED_WIDTH = 4;
    /**
     * The string is encoded correctly in the given multi-byte/variable-width encoding.
     */
    private static final int CR_VALID_MULTIBYTE = 5;
    /**
     * The string is not encoded correctly in the given multi-byte/variable-width encoding.
     */
    private static final int CR_BROKEN_MULTIBYTE = 6;
    /**
     * No information about the string is known.
     */
    private static final int CR_UNKNOWN = 7;

    @CompilationFinal(dimensions = 1) private static final int[] MAX_CODEPOINT_PER_CODE_RANGE = {0x7f, 0xff, 0xffff, 0x10ffff, 0x10ffff, 0x10ffff, 0x10ffff, 0x10ffff};

    private static int maxCodePoint(int codeRange) {
        return MAX_CODEPOINT_PER_CODE_RANGE[codeRange];
    }

    static boolean isCodeRange(int codeRange) {
        return CR_7BIT <= codeRange && codeRange <= CR_UNKNOWN;
    }

    static int get7Bit() {
        return CR_7BIT;
    }

    static int get8Bit() {
        return CR_8BIT;
    }

    static int get16Bit() {
        return CR_16BIT;
    }

    static int getValidFixedWidth() {
        return CR_VALID_FIXED_WIDTH;
    }

    static int getBrokenFixedWidth() {
        return CR_BROKEN_FIXED_WIDTH;
    }

    static int getValidMultiByte() {
        return CR_VALID_MULTIBYTE;
    }

    static int getBrokenMultiByte() {
        return CR_BROKEN_MULTIBYTE;
    }

    static int getUnknown() {
        return CR_UNKNOWN;
    }

    static boolean isUnknown(int codeRange) {
        return codeRange == CR_UNKNOWN;
    }

    static boolean is7Bit(int codeRange) {
        return codeRange == CR_7BIT;
    }

    static boolean is7Or8Bit(int codeRange) {
        return codeRange <= CR_8BIT;
    }

    static boolean isUpTo16Bit(int codeRange) {
        return codeRange <= CR_16BIT;
    }

    static boolean is8Bit(int codeRange) {
        return codeRange == CR_8BIT;
    }

    static boolean is16Bit(int codeRange) {
        return codeRange == CR_16BIT;
    }

    static boolean isValidFixedWidth(int codeRange) {
        return codeRange == CR_VALID_FIXED_WIDTH;
    }

    static boolean isBrokenFixedWidth(int codeRange) {
        return codeRange == CR_BROKEN_FIXED_WIDTH;
    }

    static boolean isValidMultiByte(int codeRange) {
        return codeRange == CR_VALID_MULTIBYTE;
    }

    static boolean isBrokenMultiByte(int codeRange) {
        return codeRange == CR_BROKEN_MULTIBYTE;
    }

    static boolean isBrokenMultiByteOrUnknown(int codeRange) {
        return isBrokenMultiByte(codeRange) || isUnknown(codeRange);
    }

    static boolean isValidBrokenOrUnknownMultiByte(int codeRange) {
        return codeRange >= CR_VALID_MULTIBYTE && codeRange <= CR_UNKNOWN;
    }

    static boolean isKnown(int codeRange) {
        return !isUnknown(codeRange);
    }

    static boolean isKnown(int aCodeRange, int bCodeRange) {
        return isKnown(aCodeRange) && isKnown(bCodeRange);
    }

    /**
     * Returns the more general code range of both parameters {@code a} and {@code b}.
     */
    static int commonCodeRange(int a, int b) {
        return Math.max(a, b);
    }

    static boolean isMoreRestrictiveThan(int a, int b) {
        return a < b;
    }

    static boolean isMoreRestrictiveOrEqual(int a, int b) {
        return a <= b;
    }

    static boolean isMoreGeneralThan(int a, int b) {
        return a > b;
    }

    static boolean isFixedWidth(int codeRange) {
        return codeRange <= CR_BROKEN_FIXED_WIDTH;
    }

    static boolean isInCodeRange(int codepoint, int codeRange) {
        return Integer.toUnsignedLong(codepoint) <= maxCodePoint(codeRange);
    }

    static int toStrideUTF16(int codeRange) {
        return codeRange <= TSCodeRange.get8Bit() ? 0 : 1;
    }

    static int toStrideUTF32(int codeRange) {
        assert isFixedWidth(codeRange);
        if (codeRange > CR_16BIT) {
            return 2;
        } else if (codeRange == CR_16BIT) {
            return 1;
        }
        return 0;
    }

    static int asciiLatinBytesNonAsciiCodeRange(TruffleString.Encoding encoding) {
        return asciiLatinBytesNonAsciiCodeRange(encoding.id);
    }

    static int asciiLatinBytesNonAsciiCodeRange(int encoding) {
        if (isAscii(encoding)) {
            return TSCodeRange.getBrokenFixedWidth();
        } else if (isLatin1(encoding)) {
            return TSCodeRange.get8Bit();
        } else if (isBytes(encoding)) {
            return TSCodeRange.getValidFixedWidth();
        } else {
            return TSCodeRange.getUnknown();
        }
    }

    static int getAsciiCodeRange(int encoding) {
        if (TStringGuards.is7BitCompatible(encoding)) {
            return get7Bit();
        } else if (JCodings.getInstance().isSingleByte(TruffleString.Encoding.getJCoding(encoding))) {
            return getValidFixedWidth();
        } else {
            return getValidMultiByte();
        }
    }

    static {
        staticAssertions();
    }

    @SuppressWarnings("all")
    private static void staticAssertions() {
        assert toStrideUTF32(CR_7BIT) == 0;
        assert toStrideUTF32(CR_8BIT) == 0;
        assert toStrideUTF32(CR_16BIT) == 1;
        assert toStrideUTF32(CR_VALID_FIXED_WIDTH) == 2;
        assert toStrideUTF32(CR_BROKEN_FIXED_WIDTH) == 2;
        assert maxCodePoint(CR_7BIT) == 0x7f;
        assert maxCodePoint(CR_8BIT) == 0xff;
        assert maxCodePoint(CR_16BIT) == 0xffff;
        assert maxCodePoint(CR_VALID_FIXED_WIDTH) == 0x10ffff;
        assert maxCodePoint(CR_BROKEN_FIXED_WIDTH) == 0x10ffff;
        assert maxCodePoint(CR_VALID_MULTIBYTE) == 0x10ffff;
        assert maxCodePoint(CR_BROKEN_MULTIBYTE) == 0x10ffff;
        assert maxCodePoint(CR_UNKNOWN) == 0x10ffff;
    }

    @TruffleBoundary
    static String toString(int codeRange) {
        switch (codeRange) {
            case CR_7BIT:
                return "7Bit";
            case CR_8BIT:
                return "8Bit";
            case CR_16BIT:
                return "16Bit";
            case CR_VALID_FIXED_WIDTH:
                return "ValidFixedWidth";
            case CR_BROKEN_FIXED_WIDTH:
                return "BrokenFixedWidth";
            case CR_VALID_MULTIBYTE:
                return "ValidMultiByte";
            case CR_BROKEN_MULTIBYTE:
                return "BrokenMultiByte";
            case CR_UNKNOWN:
                return "Unknown";
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }
}
