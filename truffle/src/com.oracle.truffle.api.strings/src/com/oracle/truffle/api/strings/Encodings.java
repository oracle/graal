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

import static com.oracle.truffle.api.strings.TStringOps.readS0;
import static com.oracle.truffle.api.strings.TStringOps.readS1;
import static com.oracle.truffle.api.strings.TStringOps.writeToByteArray;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node;

final class Encodings {

    static final int SUPPORTED_ENCODINGS_MIN_NUM = 0;
    static final int SUPPORTED_ENCODINGS_MAX_NUM = 6;

    @CompilationFinal(dimensions = 1) private static final int[] UTF_8_MIN_CODEPOINT = {0, 0, 0x80, 0x800, 0x10000};

    static int getAscii() {
        return TruffleString.Encoding.US_ASCII.id;
    }

    static int getLatin1() {
        return TruffleString.Encoding.ISO_8859_1.id;
    }

    static int getUTF8() {
        return TruffleString.Encoding.UTF_8.id;
    }

    static int getUTF16() {
        return TruffleString.Encoding.UTF_16.id;
    }

    static int getUTF32() {
        return TruffleString.Encoding.UTF_32.id;
    }

    static boolean isUTF16Surrogate(int c) {
        return (c >> 11) == 0x1b;
    }

    static boolean isUTF16HighSurrogate(int c) {
        return (c >> 10) == 0x36;
    }

    static boolean isUTF16LowSurrogate(int c) {
        return (c >> 10) == 0x37;
    }

    static boolean isUTF8ContinuationByte(int b) {
        return (b & 0xc0) == 0x80;
    }

    static int invalidCodepoint() {
        // TODO:
        // we currently return UNICODE REPLACEMENT CHARACTER for invalid characters.
        // should we throw an Exception instead?
        return 0xfffd;
    }

    static int utf8CodePointLength(int firstByte) {
        return Integer.numberOfLeadingZeros(~(firstByte << 24));
    }

    static int utf8EncodedSize(int codepoint) {
        if (codepoint < 0x80) {
            return 1;
        } else if (codepoint < 0x800) {
            return 2;
        } else if (codepoint < 0x10000) {
            return 3;
        } else {
            return 4;
        }
    }

    private static boolean isUTF8ContinuationByte(AbstractTruffleString a, Object arrayA, int index) {
        return isUTF8ContinuationByte(readS0(a, arrayA, index));
    }

    @SuppressWarnings("fallthrough")
    static byte[] utf8Encode(int codepoint) {
        int n = utf8EncodedSize(codepoint);
        byte[] ret = new byte[n];
        if (n == 1) {
            ret[0] = (byte) codepoint;
            return ret;
        }
        utf8Encode(codepoint, n, ret, 0);
        return ret;
    }

    static void utf8Encode(int codepoint, byte[] buffer, int index, int length) {
        assert length == utf8EncodedSize(codepoint);
        if (length == 1) {
            buffer[index] = (byte) codepoint;
        } else {
            utf8Encode(codepoint, length, buffer, index);
        }
    }

    @SuppressWarnings("fallthrough")
    private static void utf8Encode(int codepoint, int encodedLength, byte[] buffer, int index) {
        assert index >= 0;
        assert 2 <= encodedLength && encodedLength <= 4;
        int i = index + encodedLength;
        int c = codepoint;
        // Checkstyle: stop
        switch (encodedLength) {
            case 4:
                buffer[--i] = (byte) (0x80 | (c & 0x3f));
                c >>>= 6;
            case 3:
                buffer[--i] = (byte) (0x80 | (c & 0x3f));
                c >>>= 6;
            default:
                buffer[--i] = (byte) (0x80 | (c & 0x3f));
                c >>>= 6;
                buffer[--i] = (byte) ((0xf00 >>> encodedLength) | c);
        }
        // Checkstyle: resume
    }

    static int utf8CodePointToByteIndex(Node location, AbstractTruffleString a, Object arrayA, int codePointIndex) {
        int iCP = 0;
        int iBytes = 0;
        while (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, iBytes < a.length())) {
            if ((readS0(a, arrayA, iBytes) & 0xc0) != 0x80) {
                if (CompilerDirectives.injectBranchProbability(0.01, !(iCP < codePointIndex))) {
                    break;
                }
                iCP++;
            }
            iBytes++;
            TStringConstants.truffleSafePointPoll(location, iBytes);
        }
        if (iBytes >= a.length()) {
            throw InternalErrors.indexOutOfBounds();
        }
        return iBytes;
    }

    @SuppressWarnings("fallthrough")
    static int utf8DecodeValid(AbstractTruffleString a, Object arrayA, int i) {
        int b = readS0(a, arrayA, i);
        if (b < 0x80) {
            return b;
        }
        int nBytes = utf8CodePointLength(b);
        int codepoint = b & (0xff >>> nBytes);
        assert 1 < nBytes && nBytes < 5 : nBytes;
        assert i + nBytes <= a.length();
        int j = i + 1;
        // Checkstyle: stop
        switch (nBytes) {
            case 4:
                assert isUTF8ContinuationByte(a, arrayA, j);
                codepoint = codepoint << 6 | (readS0(a, arrayA, j++) & 0x3f);
            case 3:
                assert isUTF8ContinuationByte(a, arrayA, j);
                codepoint = codepoint << 6 | (readS0(a, arrayA, j++) & 0x3f);
            default:
                assert isUTF8ContinuationByte(a, arrayA, j);
                codepoint = codepoint << 6 | (readS0(a, arrayA, j) & 0x3f);
        }
        // Checkstyle: resume
        return codepoint;
    }

    @SuppressWarnings("fallthrough")
    static int utf8DecodeBroken(AbstractTruffleString a, Object arrayA, int i) {
        int b = readS0(a, arrayA, i);
        if (b < 0x80) {
            return b;
        }
        int nBytes = utf8CodePointLength(b);
        int codepoint = b & (0xff >>> nBytes);
        int j = i + 1;
        // Checkstyle: stop
        switch (nBytes) {
            case 4:
                if (j >= a.length() || !isUTF8ContinuationByte(a, arrayA, j)) {
                    return invalidCodepoint();
                }
                codepoint = codepoint << 6 | (readS0(a, arrayA, j++) & 0x3f);
            case 3:
                if (j >= a.length() || !isUTF8ContinuationByte(a, arrayA, j)) {
                    return invalidCodepoint();
                }
                codepoint = codepoint << 6 | (readS0(a, arrayA, j++) & 0x3f);
            case 2:
                if (j >= a.length() || !isUTF8ContinuationByte(a, arrayA, j)) {
                    return invalidCodepoint();
                }
                codepoint = codepoint << 6 | (readS0(a, arrayA, j) & 0x3f);
                break;
            default:
                return invalidCodepoint();
        }
        // Checkstyle: resume
        if (utf8IsInvalidCodePoint(codepoint, nBytes)) {
            return invalidCodepoint();
        }
        return codepoint;
    }

    static int utf8GetCodePointLength(AbstractTruffleString a, Object arrayA, int i) {
        return utf8GetCodePointLength(arrayA, a.offset(), a.length(), i);
    }

    /**
     * Try to decode a codepoint at byte index {@code i}, and return the number of bytes consumed if
     * the codepoint is valid, otherwise return {@code 1}.
     */
    @SuppressWarnings("fallthrough")
    static int utf8GetCodePointLength(Object arrayA, int offset, int length, int i) {
        int b = readS0(arrayA, offset, length, i);
        if (b < 0x80) {
            return 1;
        }
        int nBytes = utf8CodePointLength(b);
        int codepoint = b & (0xff >>> nBytes);
        int continuationByte;
        int j = i + 1;
        if (i + nBytes > length) {
            return 1;
        }
        // Checkstyle: stop
        switch (nBytes) {
            case 4:
                continuationByte = readS0(arrayA, offset, length, j++);
                if (!isUTF8ContinuationByte(continuationByte)) {
                    return 1;
                }
                codepoint = codepoint << 6 | (continuationByte & 0x3f);
            case 3:
                continuationByte = readS0(arrayA, offset, length, j++);
                if (!isUTF8ContinuationByte(continuationByte)) {
                    return 1;
                }
                codepoint = codepoint << 6 | (continuationByte & 0x3f);
            case 2:
                continuationByte = readS0(arrayA, offset, length, j);
                if (!isUTF8ContinuationByte(continuationByte)) {
                    return 1;
                }
                codepoint = codepoint << 6 | (continuationByte & 0x3f);
                break;
            default:
                return 1;
        }
        // Checkstyle: resume
        if (utf8IsInvalidCodePoint(codepoint, nBytes)) {
            return 1;
        }
        return nBytes;
    }

    static boolean utf8IsInvalidCodePoint(int codepoint, int nBytes) {
        return isUTF16Surrogate(codepoint) || codepoint < UTF_8_MIN_CODEPOINT[nBytes] || codepoint > 0x10ffff;
    }

    static int utf16EncodedSize(int codepoint) {
        return codepoint < 0x10000 ? 1 : 2;
    }

    static int utf16Encode(int codepoint, byte[] bytes, int index) {
        if (codepoint < 0x10000) {
            writeToByteArray(bytes, 1, index, codepoint);
            return 1;
        } else {
            utf16EncodeSurrogatePair(codepoint, bytes, index);
            return 2;
        }
    }

    static void utf16EncodeSurrogatePair(int codepoint, byte[] bytes, int index) {
        assert codepoint > 0xffff;
        char c1 = Character.highSurrogate(codepoint);
        char c2 = Character.lowSurrogate(codepoint);
        writeToByteArray(bytes, 1, index, c1);
        writeToByteArray(bytes, 1, index + 1, c2);
    }

    static int utf16ValidCodePointToCharIndex(Node location, AbstractTruffleString a, Object arrayA, int codePointIndex) {
        int iCP = 0;
        int iChars = 0;
        while (CompilerDirectives.injectBranchProbability(CompilerDirectives.LIKELY_PROBABILITY, iChars < a.length())) {
            if ((readS1(a, arrayA, iChars) & 0xfc00) != 0xdc00) {
                if (CompilerDirectives.injectBranchProbability(0.01, !(iCP < codePointIndex))) {
                    break;
                }
                iCP++;
            }
            iChars++;
            TStringConstants.truffleSafePointPoll(location, iChars);
        }
        if (iChars >= a.length()) {
            throw InternalErrors.indexOutOfBounds();
        }
        return iChars;
    }

    static int utf16BrokenCodePointToCharIndex(Node location, AbstractTruffleString a, Object arrayA, int codePointIndex) {
        int iCP = 0;
        int iChars = 0;
        while (iCP < codePointIndex) {
            if (isUTF16HighSurrogate(readS1(a, arrayA, iChars)) && (iChars + 1) < a.length() && isUTF16LowSurrogate(readS1(a, arrayA, iChars + 1))) {
                iChars++;
            }
            iChars++;
            iCP++;
            TStringConstants.truffleSafePointPoll(location, iCP);
        }
        if (iChars >= a.length()) {
            throw InternalErrors.indexOutOfBounds();
        }
        return iChars;
    }

    static int utf16DecodeValid(AbstractTruffleString a, Object arrayA, int i) {
        char c = readS1(a, arrayA, i);
        if (isUTF16HighSurrogate(c)) {
            assert (i + 1) < a.length();
            assert isUTF16LowSurrogate(readS1(a, arrayA, i + 1));
            return Character.toCodePoint(c, readS1(a, arrayA, i + 1));
        }
        return c;
    }

    static int utf16DecodeBroken(AbstractTruffleString a, Object arrayA, int i) {
        char c = readS1(a, arrayA, i);
        if (isUTF16HighSurrogate(c) && (i + 1) < a.length()) {
            char c2 = readS1(a, arrayA, i + 1);
            if (isUTF16LowSurrogate(c2)) {
                return Character.toCodePoint(c, c2);
            }
        }
        return c;
    }
}
