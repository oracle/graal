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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;

final class TStringConstants {

    static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;
    static final int MAX_ARRAY_SIZE_S1 = MAX_ARRAY_SIZE >> 1;
    static final int MAX_ARRAY_SIZE_S2 = MAX_ARRAY_SIZE >> 2;

    @CompilationFinal(dimensions = 1) static final byte[] EMPTY_BYTES = new byte[0];
    @CompilationFinal(dimensions = 2) static final byte[][] SINGLE_BYTE_ARRAYS = new byte[256][1];
    @CompilationFinal(dimensions = 1) private static final byte[] INFINITY_BYTES = {'I', 'n', 'f', 'i', 'n', 'i', 't', 'y'};
    @CompilationFinal(dimensions = 1) private static final byte[] NaN_BYTES = {'N', 'a', 'N'};

    private static final TruffleString EMPTY = TruffleString.createConstant(EMPTY_BYTES, 0, 0, Encodings.getAscii(), 0, TSCodeRange.get7Bit());
    private static final TruffleString EMPTY_NO_CACHE_HEAD = TruffleString.createConstant(EMPTY_BYTES, 0, 0, Encodings.getAscii(), 0, TSCodeRange.get7Bit(), false);
    private static final TruffleString INFINITY = TruffleString.createConstant(INFINITY_BYTES, INFINITY_BYTES.length, 0, Encodings.getAscii(), INFINITY_BYTES.length, TSCodeRange.get7Bit());
    private static final TruffleString NaN = TruffleString.createConstant(NaN_BYTES, NaN_BYTES.length, 0, Encodings.getAscii(), NaN_BYTES.length, TSCodeRange.get7Bit());
    @CompilationFinal(dimensions = 2) private static final TruffleString[][] SINGLE_BYTE = new TruffleString[Encodings.SUPPORTED_ENCODINGS_MAX_NUM][];

    /**
     * Minimum combined length of two strings for lazy concatenation.
     */
    static final int LAZY_CONCAT_MIN_LENGTH = 40;

    static {
        for (int i = Encodings.SUPPORTED_ENCODINGS_MIN_NUM; i < Encodings.SUPPORTED_ENCODINGS_MAX_NUM; i++) {
            SINGLE_BYTE[i] = new TruffleString[256];
        }
        for (int i = 0; i < 128; i++) {
            SINGLE_BYTE_ARRAYS[i][0] = (byte) i;
            SINGLE_BYTE[Encodings.SUPPORTED_ENCODINGS_MIN_NUM][i] = TruffleString.createConstant(SINGLE_BYTE_ARRAYS[i], 1, 0, Encodings.getAscii(), 1, TSCodeRange.get7Bit());
            for (int j = Encodings.SUPPORTED_ENCODINGS_MIN_NUM + 1; j < Encodings.SUPPORTED_ENCODINGS_MAX_NUM; j++) {
                SINGLE_BYTE[j][i] = SINGLE_BYTE[Encodings.SUPPORTED_ENCODINGS_MIN_NUM][i];
            }
        }

        for (int i = 128; i < 256; i++) {
            SINGLE_BYTE_ARRAYS[i][0] = (byte) i;
            for (int j = Encodings.SUPPORTED_ENCODINGS_MIN_NUM; j < Encodings.SUPPORTED_ENCODINGS_MAX_NUM; j++) {
                SINGLE_BYTE[j][i] = TruffleString.createConstant(SINGLE_BYTE_ARRAYS[i], 1, 0, j, 1, nonAsciiCodeRange(j));
            }
        }
    }

    private static int nonAsciiCodeRange(int encoding) {
        if (TStringGuards.isAsciiBytesOrLatin1(encoding)) {
            return TSCodeRange.asciiLatinBytesNonAsciiCodeRange(encoding);
        }
        if (TStringGuards.isUTF8(encoding)) {
            return TSCodeRange.getBrokenMultiByte();
        }
        return TSCodeRange.get8Bit();
    }

    static TruffleString getEmpty(int encoding) {
        if (AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS) {
            return createAscii(EMPTY_BYTES, encoding);
        }
        return EMPTY;
    }

    static TruffleString getEmptyNoCacheHead(int encoding) {
        if (AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS) {
            return createAscii(EMPTY_BYTES, encoding, false);
        }
        return EMPTY_NO_CACHE_HEAD;
    }

    static TruffleString getInfinity(int encoding) {
        if (AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS) {
            return createAscii(INFINITY_BYTES, encoding);
        }
        return INFINITY;
    }

    static TruffleString getNaN(int encoding) {
        if (AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS) {
            return createAscii(NaN_BYTES, encoding);
        }
        return NaN;
    }

    static TruffleString getSingleByteAscii(int encoding, int value) {
        if (AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS) {
            return createAscii(SINGLE_BYTE_ARRAYS[value], encoding);
        }
        if (TStringGuards.isUnsupportedEncoding(encoding)) {
            return SINGLE_BYTE[Encodings.getAscii()][value];
        }
        return SINGLE_BYTE[encoding][value];
    }

    static TruffleString getSingleByte(int encoding, int value) {
        if (AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS && value <= 0x7f) {
            return createAscii(SINGLE_BYTE_ARRAYS[value], encoding);
        }
        return SINGLE_BYTE[encoding][value];
    }

    private static TruffleString createAscii(byte[] array, int encoding) {
        return createAscii(array, encoding, true);
    }

    private static TruffleString createAscii(byte[] array, int encoding, boolean isCacheHead) {
        return TruffleString.createFromByteArray(array, array.length, 0, encoding, array.length, getAsciiCodeRange(encoding), isCacheHead);
    }

    private static int getAsciiCodeRange(int encoding) {
        if (TStringGuards.is7BitCompatible(encoding)) {
            return TSCodeRange.get7Bit();
        } else if (JCodings.getInstance().isSingleByte(TruffleString.Encoding.getJCoding(encoding))) {
            return TSCodeRange.getValidFixedWidth();
        } else {
            return TSCodeRange.getValidMultiByte();
        }
    }
}
