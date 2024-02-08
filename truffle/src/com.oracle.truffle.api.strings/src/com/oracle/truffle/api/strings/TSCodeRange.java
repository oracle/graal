/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

final class TSCodeRange {

    private static final int FLAG_MULTIBYTE = 1 << 3;
    private static final int FLAG_IMPRECISE = 1 << 4;
    private static final int MASK_ORDINAL = 0x7;
    private static final int MASK_ORDINAL_MULTIBYTE = MASK_ORDINAL | FLAG_MULTIBYTE;

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
     * The string is encoded correctly.
     */
    private static final int CR_VALID = 3;
    /**
     * The string is not encoded correctly.
     */
    private static final int CR_BROKEN = 4;
    private static final int CR_VALID_MULTIBYTE = CR_VALID | FLAG_MULTIBYTE;
    private static final int CR_BROKEN_MULTIBYTE = CR_BROKEN | FLAG_MULTIBYTE;

    static int ordinal(int codeRange) {
        return codeRange & MASK_ORDINAL;
    }

    static int ordinalAndMultibyteFlag(int codeRange) {
        return codeRange & MASK_ORDINAL_MULTIBYTE;
    }

    private static int maxCodePoint(int codeRange) {
        int ordinal = ordinal(codeRange);
        return ordinal == CR_7BIT ? 0x7f : ordinal == CR_8BIT ? 0xff : ordinal == CR_16BIT ? 0xffff : 0x10ffff;
    }

    static boolean isCodeRange(int codeRange) {
        return CR_7BIT <= ordinal(codeRange) && ordinal(codeRange) <= CR_BROKEN && (codeRange >>> 6) == 0;
    }

    static int markImprecise(int codeRange) {
        return codeRange | FLAG_IMPRECISE;
    }

    static boolean isFixedWidth(int codeRange) {
        return (codeRange & FLAG_MULTIBYTE) == 0;
    }

    static boolean isFixedWidth(int codeRangeA, int codeRangeB) {
        return ((codeRangeA | codeRangeB) & FLAG_MULTIBYTE) == 0;
    }

    static boolean isPrecise(int codeRange) {
        return (codeRange & FLAG_IMPRECISE) == 0;
    }

    static boolean isPrecise(int codeRangeA, int codeRangeB) {
        return ((codeRangeA | codeRangeB) & FLAG_IMPRECISE) == 0;
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

    static int getValid(boolean fixedWidth) {
        return fixedWidth ? getValidFixedWidth() : getValidMultiByte();
    }

    static int getBroken(boolean fixedWidth) {
        return fixedWidth ? getBrokenFixedWidth() : getBrokenMultiByte();
    }

    static int getValidFixedWidth() {
        return CR_VALID;
    }

    static int getBrokenFixedWidth() {
        return CR_BROKEN;
    }

    static int getValidMultiByte() {
        return CR_VALID_MULTIBYTE;
    }

    static int getBrokenMultiByte() {
        return CR_BROKEN_MULTIBYTE;
    }

    static boolean is7Bit(int codeRange) {
        return ordinal(codeRange) == CR_7BIT;
    }

    static boolean is7Or8Bit(int codeRange) {
        return ordinal(codeRange) <= CR_8BIT;
    }

    static boolean isUpTo16Bit(int codeRange) {
        return ordinal(codeRange) <= CR_16BIT;
    }

    static boolean is8Bit(int codeRange) {
        return ordinal(codeRange) == CR_8BIT;
    }

    static boolean is16Bit(int codeRange) {
        return ordinal(codeRange) == CR_16BIT;
    }

    static boolean isValid(int codeRange) {
        return ordinal(codeRange) == CR_VALID;
    }

    static boolean isBroken(int codeRange) {
        return ordinal(codeRange) == CR_BROKEN;
    }

    static boolean isValidFixedWidth(int codeRange) {
        return ordinalAndMultibyteFlag(codeRange) == getValidFixedWidth();
    }

    static boolean isBrokenFixedWidth(int codeRange) {
        return ordinalAndMultibyteFlag(codeRange) == getBrokenFixedWidth();
    }

    static boolean isValidMultiByte(int codeRange) {
        return ordinalAndMultibyteFlag(codeRange) == getValidMultiByte();
    }

    static boolean isBrokenMultiByte(int codeRange) {
        return ordinalAndMultibyteFlag(codeRange) == getBrokenMultiByte();
    }

    static boolean isValidOrBrokenMultiByte(int codeRange) {
        return isValidMultiByte(codeRange) || isBrokenMultiByte(codeRange);
    }

    /**
     * Returns the more general code range of both parameters {@code a} and {@code b}.
     */
    static int commonCodeRange(int a, int b) {
        return isMoreGeneralThan(a, b) ? a : b;
    }

    static boolean isMoreRestrictiveThan(int a, int b) {
        return ordinal(a) < ordinal(b);
    }

    static boolean isMoreRestrictiveOrEqual(int a, int b) {
        return ordinal(a) <= ordinal(b);
    }

    static boolean isMoreGeneralThan(int a, int b) {
        return ordinal(a) > ordinal(b);
    }

    static boolean isUpToValidFixedWidth(int codeRange) {
        return ordinal(codeRange) <= CR_VALID && isFixedWidth(codeRange);
    }

    static boolean isInCodeRange(int codepoint, int codeRange) {
        return Integer.toUnsignedLong(codepoint) <= maxCodePoint(codeRange);
    }

    static int toStrideUTF16(int codeRange) {
        assert is7Or8Bit(codeRange) || isPrecise(codeRange);
        return toStrideUTF16AllowImprecise(codeRange);
    }

    static int toStrideUTF16AllowImprecise(int codeRange) {
        return is7Or8Bit(codeRange) ? 0 : 1;
    }

    static int toStrideUTF32(int codeRange) {
        assert is7Or8Bit(codeRange) || isPrecise(codeRange);
        return toStrideUTF32AllowImprecise(codeRange);
    }

    static int toStrideUTF32AllowImprecise(int codeRange) {
        assert isFixedWidth(codeRange);
        int ordinal = ordinal(codeRange);
        if (ordinal > CR_16BIT) {
            return 2;
        } else if (ordinal == CR_16BIT) {
            return 1;
        }
        return 0;
    }

    static int asciiLatinBytesNonAsciiCodeRange(int encoding) {
        if (isAscii(encoding)) {
            return TSCodeRange.getBrokenFixedWidth();
        } else if (isLatin1(encoding)) {
            return TSCodeRange.get8Bit();
        } else {
            assert isBytes(encoding);
            return TSCodeRange.getValidFixedWidth();
        }
    }

    static int asciiLatinBytesNonAsciiCodeRange(Encoding encoding) {
        if (isAscii(encoding)) {
            return TSCodeRange.getBrokenFixedWidth();
        } else if (isLatin1(encoding)) {
            return TSCodeRange.get8Bit();
        } else if (isBytes(encoding)) {
            return TSCodeRange.getValidFixedWidth();
        } else {
            return getUnknownCodeRangeForEncoding(encoding.id);
        }
    }

    static int getAsciiCodeRange(Encoding encoding) {
        if (TStringGuards.is7BitCompatible(encoding)) {
            return get7Bit();
        } else if (JCodings.getInstance().isSingleByte(encoding.jCoding)) {
            return getValidFixedWidth();
        } else {
            return getValidMultiByte();
        }
    }

    static byte getUnknownCodeRangeForEncoding(int encoding) {
        if (isLatin1(encoding)) {
            return (byte) markImprecise(get8Bit());
        }
        if (isBytes(encoding)) {
            return (byte) markImprecise(getValidFixedWidth());
        }
        return (byte) markImprecise(getBroken(Encoding.isFixedWidth(encoding)));
    }

    static {
        staticAssertions();
    }

    @SuppressWarnings("all")
    private static void staticAssertions() {
        assert toStrideUTF32(CR_7BIT) == 0;
        assert toStrideUTF32(get7Bit()) == 0;
        assert toStrideUTF32(CR_8BIT) == 0;
        assert toStrideUTF32(get8Bit()) == 0;
        assert toStrideUTF32(CR_16BIT) == 1;
        assert toStrideUTF32(get16Bit()) == 1;
        assert toStrideUTF32(CR_VALID) == 2;
        assert toStrideUTF32(CR_BROKEN) == 2;
        assert maxCodePoint(CR_7BIT) == 0x7f;
        assert maxCodePoint(get7Bit()) == 0x7f;
        assert maxCodePoint(CR_8BIT) == 0xff;
        assert maxCodePoint(get8Bit()) == 0xff;
        assert maxCodePoint(CR_16BIT) == 0xffff;
        assert maxCodePoint(get16Bit()) == 0xffff;
        assert maxCodePoint(CR_VALID) == 0x10ffff;
        assert maxCodePoint(getValidFixedWidth()) == 0x10ffff;
        assert maxCodePoint(getValidMultiByte()) == 0x10ffff;
        assert maxCodePoint(CR_BROKEN) == 0x10ffff;
        assert maxCodePoint(getBrokenFixedWidth()) == 0x10ffff;
        assert maxCodePoint(getBrokenMultiByte()) == 0x10ffff;
    }

    @TruffleBoundary
    static String toString(int codeRange) {
        switch (ordinal(codeRange)) {
            case CR_7BIT:
                return "7Bit" + preciseFlagToString(codeRange);
            case CR_8BIT:
                return "8Bit" + preciseFlagToString(codeRange);
            case CR_16BIT:
                return "16Bit" + preciseFlagToString(codeRange);
            case CR_VALID:
                return "Valid" + flagsToString(codeRange);
            case CR_BROKEN:
                return "Broken" + flagsToString(codeRange);
            default:
                throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static String preciseFlagToString(int codeRange) {
        return isPrecise(codeRange) ? "" : "(imprecise)";
    }

    private static String flagsToString(int codeRange) {
        if (isFixedWidth(codeRange)) {
            if (isPrecise(codeRange)) {
                return "(fixedWidth)";
            } else {
                return "(fixedWidth, imprecise)";
            }
        } else {
            if (isPrecise(codeRange)) {
                return "(multibyte)";
            } else {
                return "(multibyte, imprecise)";
            }
        }
    }
}
