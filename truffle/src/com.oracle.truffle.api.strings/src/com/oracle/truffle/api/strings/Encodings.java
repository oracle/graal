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

import static com.oracle.truffle.api.strings.TStringOps.readS0;
import static com.oracle.truffle.api.strings.TStringOps.readS1;
import static com.oracle.truffle.api.strings.TStringOps.writeToByteArray;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString.ErrorHandling;

final class Encodings {

    static final int SUPPORTED_ENCODINGS_MIN_NUM = 0;
    static final int SUPPORTED_ENCODINGS_MAX_NUM = 6;
    /**
     * Copyright (c) 2008-2010 Bjoern Hoehrmann <bjoern@hoehrmann.de> See
     * http://bjoern.hoehrmann.de/utf-8/decoder/dfa/ for details.
     *
     * LICENCE: MIT
     */
    @CompilationFinal(dimensions = 1) static final byte[] UTF_8_STATE_MACHINE = {
                    // The first part of the table maps bytes to character classes
                    // to reduce the size of the transition table and create bitmasks.
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
                    7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
                    8, 8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
                    10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,

                    // The second part is a transition table that maps a combination
                    // of a state of the automaton and a character class to a state.
                    0, 12, 24, 36, 60, 96, 84, 12, 12, 12, 48, 72, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
                    12, 0, 12, 12, 12, 12, 12, 0, 12, 0, 12, 12, 12, 24, 12, 12, 12, 12, 12, 24, 12, 24, 12, 12,
                    12, 12, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12,
                    12, 12, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12,
                    12, 36, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
    };

    /**
     * Variant of UTF_8_STATE_MACHINE that allows UTF-16 surrogate values, by changing the character
     * class of 0xED to 3.
     */
    @CompilationFinal(dimensions = 1) static final byte[] UTF_8_STATE_MACHINE_ALLOW_UTF16_SURROGATES = {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
                    7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
                    8, 8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
                    10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,

                    0, 12, 24, 36, 60, 96, 84, 12, 12, 12, 48, 72, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
                    12, 0, 12, 12, 12, 12, 12, 0, 12, 0, 12, 12, 12, 24, 12, 12, 12, 12, 12, 24, 12, 24, 12, 12,
                    12, 12, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 24, 12, 12, 12, 12, 12, 12, 12, 24, 12, 12,
                    12, 12, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12, 12, 36, 12, 12, 12, 12, 12, 36, 12, 36, 12, 12,
                    12, 36, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
    };

    /**
     * Variant of UTF_8_STATE_MACHINE for backward string iteration. To achieve the exact same
     * behavior on incomplete sequences in forward and backward iteration, this state machine has
     * two error states: REJECT and INCOMPLETE_SEQUENCE, where REJECT means that only one byte
     * should be consumed, and INCOMPLETE_SEQUENCE means that all bytes that were consumed until the
     * INCOMPLETE_SEQUENCE state was reached should be consumed.
     */
    @CompilationFinal(dimensions = 1) static final byte[] UTF_8_STATE_MACHINE_REVERSE = {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
                    7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
                    8, 8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
                    10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 4, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,

                    0, 36, 12, 12, 12, 12, 12, 48, 12, 60, 12, 12,
                    // REJECT state
                    12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
                    // INCOMPLETE_SEQUENCE state
                    24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
                    12, 72, 0, 24, 24, 24, 24, 96, 12, 84, 12, 12,
                    12, 72, 0, 24, 12, 12, 24, 96, 12, 84, 24, 24,
                    12, 72, 0, 24, 24, 12, 24, 96, 12, 84, 12, 24,
                    12, 108, 12, 0, 0, 24, 24, 120, 12, 120, 12, 12,
                    12, 108, 12, 0, 0, 12, 24, 120, 12, 120, 12, 24,
                    12, 108, 12, 0, 12, 12, 24, 120, 12, 120, 0, 24,
                    12, 12, 12, 12, 12, 0, 0, 12, 12, 12, 12, 12,
                    12, 12, 12, 12, 12, 12, 0, 12, 12, 12, 12, 0,

    };
    /**
     * Variant of UTF_8_STATE_MACHINE_REVERSE that allows UTF-16 surrogate values, by changing the
     * character class of 0xED to 3.
     */
    @CompilationFinal(dimensions = 1) static final byte[] UTF_8_STATE_MACHINE_REVERSE_ALLOW_UTF16_SURROGATES = {
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9,
                    7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
                    8, 8, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
                    10, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 11, 6, 6, 6, 5, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,

                    0, 36, 12, 12, 12, 12, 12, 48, 12, 60, 12, 12,
                    12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12, 12,
                    24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24,
                    12, 72, 0, 24, 24, 24, 24, 96, 12, 84, 12, 12,
                    12, 72, 0, 24, 12, 12, 24, 96, 12, 84, 24, 24,
                    12, 72, 0, 24, 24, 12, 24, 96, 12, 84, 12, 24,
                    12, 108, 12, 0, 0, 24, 24, 120, 12, 120, 12, 12,
                    12, 108, 12, 0, 0, 12, 24, 120, 12, 120, 12, 24,
                    12, 108, 12, 0, 12, 12, 24, 120, 12, 120, 0, 24,
                    12, 12, 12, 12, 12, 0, 0, 12, 12, 12, 12, 12,
                    12, 12, 12, 12, 12, 12, 0, 12, 12, 12, 12, 0,

    };
    static final byte UTF8_ACCEPT = 0;
    static final byte UTF8_REJECT = 12;
    static final byte UTF8_REVERSE_INCOMPLETE_SEQ = 24;
    /**
     * UTF-8 encoded 0xfffd.
     */
    static final byte[] CONVERSION_REPLACEMENT_UTF_8 = {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD};

    static byte[] getUTF8DecodingStateMachine(DecodingErrorHandler errorHandler) {
        return errorHandler == DecodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8 ? Encodings.UTF_8_STATE_MACHINE_ALLOW_UTF16_SURROGATES : Encodings.UTF_8_STATE_MACHINE;
    }

    static byte[] getUTF8DecodingStateMachineReverse(DecodingErrorHandler errorHandler) {
        return errorHandler == DecodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8 ? Encodings.UTF_8_STATE_MACHINE_REVERSE_ALLOW_UTF16_SURROGATES : Encodings.UTF_8_STATE_MACHINE_REVERSE;
    }

    @CompilationFinal(dimensions = 1) static final int[] UTF_8_MIN_CODEPOINT = {0, 0, 0x80, 0x800, 0x10000};

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

    private static boolean isUTF8ContinuationByte(Object arrayA, int offsetA, int lengthA, int index) {
        return isUTF8ContinuationByte(readS0(arrayA, offsetA, lengthA, index));
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

    static byte[] utf8EncodeNonAscii(int codepoint, int encodedSize) {
        assert encodedSize == utf8EncodedSize(codepoint);
        assert encodedSize > 1;
        byte[] ret = new byte[encodedSize];
        utf8Encode(codepoint, encodedSize, ret, 0);
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

    static int utf8DecodeValid(AbstractTruffleString a, Object arrayA, int i) {
        return utf8DecodeValid(arrayA, a.offset(), a.length(), i);
    }

    @SuppressWarnings("fallthrough")
    static int utf8DecodeValid(Object arrayA, int offsetA, int lengthA, int i) {
        int b = readS0(arrayA, offsetA, lengthA, i);
        if (b < 0x80) {
            return b;
        }
        int nBytes = utf8CodePointLength(b);
        int codepoint = b & (0xff >>> nBytes);
        assert 1 < nBytes && nBytes < 5 : nBytes;
        assert i + nBytes <= lengthA;
        int j = i + 1;
        // Checkstyle: stop
        switch (nBytes) {
            case 4:
                assert isUTF8ContinuationByte(arrayA, offsetA, lengthA, j);
                codepoint = codepoint << 6 | (readS0(arrayA, offsetA, lengthA, j++) & 0x3f);
            case 3:
                assert isUTF8ContinuationByte(arrayA, offsetA, lengthA, j);
                codepoint = codepoint << 6 | (readS0(arrayA, offsetA, lengthA, j++) & 0x3f);
            default:
                assert isUTF8ContinuationByte(arrayA, offsetA, lengthA, j);
                codepoint = codepoint << 6 | (readS0(arrayA, offsetA, lengthA, j) & 0x3f);
        }
        // Checkstyle: resume
        return codepoint;
    }

    static int utf8DecodeBroken(AbstractTruffleString a, Object arrayA, int i, ErrorHandling errorHandling) {
        return utf8DecodeBroken(arrayA, a.offset(), a.length(), i, errorHandling);
    }

    @SuppressWarnings("fallthrough")
    static int utf8DecodeBroken(Object arrayA, int offsetA, int lengthA, int i, ErrorHandling errorHandling) {
        int b = readS0(arrayA, offsetA, lengthA, i);
        if (b < 0x80) {
            return b;
        }
        int nBytes = utf8CodePointLength(b);
        int codepoint = b & (0xff >>> nBytes);
        int j = i + 1;
        // Checkstyle: stop
        switch (nBytes) {
            case 4:
                if (j >= lengthA || !isUTF8ContinuationByte(arrayA, offsetA, lengthA, j)) {
                    return invalidCodepointReturnValue(errorHandling);
                }
                codepoint = codepoint << 6 | (readS0(arrayA, offsetA, lengthA, j++) & 0x3f);
            case 3:
                if (j >= lengthA || !isUTF8ContinuationByte(arrayA, offsetA, lengthA, j)) {
                    return invalidCodepointReturnValue(errorHandling);
                }
                codepoint = codepoint << 6 | (readS0(arrayA, offsetA, lengthA, j++) & 0x3f);
            case 2:
                if (j >= lengthA || !isUTF8ContinuationByte(arrayA, offsetA, lengthA, j)) {
                    return invalidCodepointReturnValue(errorHandling);
                }
                codepoint = codepoint << 6 | (readS0(arrayA, offsetA, lengthA, j) & 0x3f);
                break;
            default:
                return invalidCodepointReturnValue(errorHandling);
        }
        // Checkstyle: resume
        if (utf8IsInvalidCodePoint(codepoint, nBytes)) {
            return invalidCodepointReturnValue(errorHandling);
        }
        return codepoint;
    }

    static int utf8GetCodePointLength(AbstractTruffleString a, Object arrayA, int i, DecodingErrorHandler errorHandler) {
        return utf8GetCodePointLength(arrayA, a.offset(), a.length(), i, errorHandler);
    }

    /**
     * Try to decode a codepoint at byte index {@code i}, and return the number of bytes consumed if
     * the codepoint is valid, otherwise return {@code 1}.
     */
    static int utf8GetCodePointLength(Object arrayA, int offset, int length, int i, DecodingErrorHandler errorHandler) {
        assert TStringGuards.isBuiltin(errorHandler);
        int b = readS0(arrayA, offset, length, i);
        if (b < 0x80) {
            return 1;
        }
        int nBytes = utf8CodePointLength(b);
        /*
         * Copyright (c) 2008-2010 Bjoern Hoehrmann <bjoern@hoehrmann.de> See
         * http://bjoern.hoehrmann.de/utf-8/decoder/dfa/ for details.
         */
        byte[] stateMachine = getUTF8DecodingStateMachine(errorHandler);
        int type = stateMachine[b];
        int state = stateMachine[256 + type];
        int j = i + 1;
        if (state != UTF8_REJECT) {
            for (; j < Math.min(length, i + nBytes); j++) {
                b = readS0(arrayA, offset, length, j);
                type = stateMachine[b];
                state = stateMachine[256 + state + type];
                if (state == UTF8_REJECT) {
                    break;
                }
            }
        }
        if (state == UTF8_ACCEPT) {
            return nBytes;
        } else if (TStringGuards.isDefaultVariant(errorHandler)) {
            if (errorHandler == DecodingErrorHandler.DEFAULT) {
                return 1;
            }
            return j - i;
        } else {
            assert TStringGuards.isReturnNegative(errorHandler);
            if (j == length && state != UTF8_REJECT) {
                return length - (i + nBytes) - 1;
            } else {
                return -1;
            }
        }
    }

    static boolean utf8IsInvalidCodePoint(int codepoint, int nBytes) {
        return isUTF16Surrogate(codepoint) || codepoint < UTF_8_MIN_CODEPOINT[nBytes] || codepoint > 0x10ffff;
    }

    static int invalidCodepointReturnValue(ErrorHandling errorHandling) {
        return invalidCodepointReturnValue(invalidCodepoint(), errorHandling);
    }

    static int invalidCodepointReturnValue(int bestEffortValue, ErrorHandling errorHandling) {
        if (errorHandling == ErrorHandling.BEST_EFFORT) {
            return bestEffortValue;
        }
        assert errorHandling == ErrorHandling.RETURN_NEGATIVE;
        return -1;
    }

    static int utf16EncodedSize(int codepoint) {
        return codepoint < 0x10000 ? 1 : 2;
    }

    static int utf16BrokenGetCodePointByteLength(AbstractTruffleString a, Object arrayA, int i, ErrorHandling errorHandling) {
        return utf16BrokenGetCodePointByteLength(arrayA, a.offset(), a.length(), i, errorHandling);
    }

    static int utf16BrokenGetCodePointByteLength(Object arrayA, int offset, int length, int i, ErrorHandling errorHandling) {
        char c = readS1(arrayA, offset, length, i);
        if (errorHandling == ErrorHandling.BEST_EFFORT) {
            return isUTF16HighSurrogate(c) && i + 1 < length && isUTF16LowSurrogate(TStringOps.readS1(arrayA, offset, length, i + 1)) ? 4 : 2;
        }
        assert errorHandling == ErrorHandling.RETURN_NEGATIVE;
        if (isUTF16Surrogate(c)) {
            if (isUTF16HighSurrogate(c)) {
                if (i + 1 == length) {
                    return -3;
                }
                if (isUTF16LowSurrogate(TStringOps.readS1(arrayA, offset, length, i + 1))) {
                    return 4;
                }
            }
            return -1;
        }
        return 2;
    }

    static byte[] utf16Encode(int codepoint) {
        byte[] bytes = new byte[codepoint < 0x10000 ? 2 : 4];
        utf16Encode(codepoint, bytes, 0);
        return bytes;
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
        return utf16DecodeValid(arrayA, a.offset(), a.length(), i);
    }

    static int utf16DecodeValid(Object arrayA, int offsetA, int lengthA, int i) {
        char c = readS1(arrayA, offsetA, lengthA, i);
        if (isUTF16HighSurrogate(c)) {
            assert (i + 1) < lengthA;
            assert isUTF16LowSurrogate(readS1(arrayA, offsetA, lengthA, i + 1));
            return Character.toCodePoint(c, readS1(arrayA, offsetA, lengthA, i + 1));
        }
        return c;
    }

    static int utf16DecodeBroken(AbstractTruffleString a, Object arrayA, int i, ErrorHandling errorHandling) {
        return utf16DecodeBroken(arrayA, a.offset(), a.length(), i, errorHandling);
    }

    static int utf16DecodeBroken(Object arrayA, int offsetA, int lengthA, int i, ErrorHandling errorHandling) {
        char c = readS1(arrayA, offsetA, lengthA, i);
        if (errorHandling == ErrorHandling.BEST_EFFORT) {
            if (isUTF16HighSurrogate(c) && (i + 1) < lengthA) {
                char c2 = readS1(arrayA, offsetA, lengthA, i + 1);
                if (isUTF16LowSurrogate(c2)) {
                    return Character.toCodePoint(c, c2);
                }
            }
        } else {
            assert errorHandling == ErrorHandling.RETURN_NEGATIVE;
            if (isUTF16Surrogate(c)) {
                if (isUTF16LowSurrogate(c) || i + 1 >= lengthA) {
                    return -1;
                }
                char c2 = readS1(arrayA, offsetA, lengthA, i + 1);
                if (!isUTF16LowSurrogate(c2)) {
                    return -1;
                }
                return Character.toCodePoint(c, c2);
            }
        }
        return c;
    }

    static boolean isValidUnicodeCodepoint(int codepoint) {
        return !isUTF16Surrogate(codepoint) && Integer.toUnsignedLong(codepoint) <= Character.MAX_CODE_POINT;
    }

    static boolean isValidUnicodeCodepoint(int codepoint, boolean allowUTF16Surrogates) {
        return (allowUTF16Surrogates || !isUTF16Surrogate(codepoint)) && Integer.toUnsignedLong(codepoint) <= Character.MAX_CODE_POINT;
    }

    static int maxCodePoint(TruffleString.Encoding encoding) {
        switch (encoding) {
            case US_ASCII:
                return 0x7f;
            case ISO_8859_1:
            case BYTES:
                return 0xff;
            case UTF_8:
            case UTF_16BE:
            case UTF_16LE:
            case UTF_32BE:
            case UTF_32LE:
                return Character.MAX_CODE_POINT;
            default:
                return Integer.MAX_VALUE;
        }
    }

    static final class BuiltinDecodingErrorHandler implements DecodingErrorHandler {

        @Override
        public Result apply(AbstractTruffleString string, int bytePosition, int estimatedByteLength) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    static final class BuiltinTranscodingErrorHandler implements TranscodingErrorHandler {

        @Override
        public TranscodingErrorHandler.ReplacementString apply(AbstractTruffleString sourceString, int byteIndex, int estimatedByteLength, TruffleString.Encoding sourceEncoding,
                        TruffleString.Encoding targetEncoding) {
            throw CompilerDirectives.shouldNotReachHere();
        }
    }
}
