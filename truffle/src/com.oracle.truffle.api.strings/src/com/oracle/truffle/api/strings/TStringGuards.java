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

import java.nio.ByteOrder;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

final class TStringGuards {

    static boolean isEmpty(AbstractTruffleString a) {
        return a.isEmpty();
    }

    static boolean is7Bit(int codeRange) {
        return TSCodeRange.is7Bit(codeRange);
    }

    static boolean is8Bit(int codeRange) {
        return TSCodeRange.is8Bit(codeRange);
    }

    static boolean is7Or8Bit(int codeRange) {
        return TSCodeRange.is7Or8Bit(codeRange);
    }

    static boolean isUpTo16Bit(int codeRange) {
        return TSCodeRange.isUpTo16Bit(codeRange);
    }

    static boolean is16Bit(int codeRange) {
        return TSCodeRange.is16Bit(codeRange);
    }

    static boolean isValid(int codeRange) {
        return TSCodeRange.isValid(codeRange);
    }

    static boolean isBroken(int codeRange) {
        return TSCodeRange.isBroken(codeRange);
    }

    static boolean isValidFixedWidth(int codeRange) {
        return TSCodeRange.isValidFixedWidth(codeRange);
    }

    static boolean isUpToValidFixedWidth(int codeRange) {
        return TSCodeRange.isUpToValidFixedWidth(codeRange);
    }

    static boolean isBrokenFixedWidth(int codeRange) {
        return TSCodeRange.isBrokenFixedWidth(codeRange);
    }

    static boolean isValidMultiByte(int codeRange) {
        return TSCodeRange.isValidMultiByte(codeRange);
    }

    static boolean isBrokenMultiByte(int codeRange) {
        return TSCodeRange.isBrokenMultiByte(codeRange);
    }

    public static boolean isValidOrBrokenMultiByte(int codeRange) {
        return TSCodeRange.isValidOrBrokenMultiByte(codeRange);
    }

    static boolean isFixedWidth(int codeRange) {
        return TSCodeRange.isFixedWidth(codeRange);
    }

    static boolean isFixedWidth(int codeRangeA, int codeRangeB) {
        return TSCodeRange.isFixedWidth(codeRangeA, codeRangeB);
    }

    static boolean indexOfCannotMatch(Node node, int codeRangeA, AbstractTruffleString b, int codeRangeB, int regionLength, Encoding encoding,
                    TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNodeB) {
        return regionLength < getCodePointLengthNodeB.execute(node, b, encoding) || codeRangesCannotMatch(codeRangeA, codeRangeB, null);
    }

    static boolean indexOfCannotMatch(int codeRangeA, AbstractTruffleString b, int codeRangeB, byte[] mask, int regionLength) {
        return regionLength < b.length() || codeRangesCannotMatch(codeRangeA, codeRangeB, mask);
    }

    private static boolean codeRangesCannotMatch(int codeRangeA, int codeRangeB, byte[] mask) {
        return mask == null &&
                        TSCodeRange.isPrecise(codeRangeA, codeRangeB) &&
                        !TSCodeRange.isBroken(codeRangeA) &&
                        !TSCodeRange.isBroken(codeRangeB) &&
                        TSCodeRange.isMoreRestrictiveThan(codeRangeA, codeRangeB);
    }

    static boolean isAscii(int enc) {
        return enc == Encoding.US_ASCII.id;
    }

    static boolean isAscii(Encoding enc) {
        return enc == Encoding.US_ASCII;
    }

    static boolean isBytes(int enc) {
        return enc == Encoding.BYTES.id;
    }

    static boolean isBytes(Encoding enc) {
        return enc == Encoding.BYTES;
    }

    static boolean isLatin1(int enc) {
        return enc == Encoding.ISO_8859_1.id;
    }

    static boolean isLatin1(Encoding enc) {
        return enc == Encoding.ISO_8859_1;
    }

    static boolean isAsciiBytesOrLatin1(int enc) {
        return isAscii(enc) || isLatin1(enc) || isBytes(enc);
    }

    static boolean isAsciiBytesOrLatin1(Encoding enc) {
        return isAscii(enc) || isLatin1(enc) || isBytes(enc);
    }

    static boolean isUTF8(int enc) {
        return enc == Encoding.UTF_8.id;
    }

    static boolean isUTF8(Encoding enc) {
        return enc == Encoding.UTF_8;
    }

    static boolean isUTF16(int enc) {
        return enc == Encoding.UTF_16.id;
    }

    static boolean isUTF16(Encoding enc) {
        return enc == Encoding.UTF_16;
    }

    static boolean isUTF32(int enc) {
        return enc == Encoding.UTF_32.id;
    }

    static boolean isUTF32(Encoding enc) {
        return enc == Encoding.UTF_32;
    }

    static boolean isUTF16Or32(Encoding enc) {
        return isUTF16Or32(enc.id);
    }

    static boolean isUTF16Or32(int enc) {
        assert Encoding.UTF_32.id == 0;
        assert Encoding.UTF_16.id == 1;
        return enc <= 1;
    }

    static boolean isUTF(Encoding enc) {
        return isUTF16Or32(enc) || isUTF8(enc);
    }

    static boolean identical(Object a, Object b) {
        return a == b;
    }

    static boolean isSupportedEncoding(int encoding) {
        return Encoding.isSupported(encoding);
    }

    static boolean isSupportedEncoding(Encoding encoding) {
        return encoding.isSupported();
    }

    static boolean isUnsupportedEncoding(int encoding) {
        return Encoding.isUnsupported(encoding);
    }

    static boolean isUnsupportedEncoding(Encoding encoding) {
        return encoding.isUnsupported();
    }

    static int length(AbstractTruffleString a) {
        return a.length();
    }

    static boolean isStride0(AbstractTruffleString a) {
        return a.stride() == 0;
    }

    static boolean isStride1(AbstractTruffleString a) {
        return a.stride() == 1;
    }

    static boolean isStride2(AbstractTruffleString a) {
        return a.stride() == 2;
    }

    static boolean is7BitCompatible(Encoding encoding) {
        return encoding.is7BitCompatible();
    }

    static boolean is8BitCompatible(Encoding encoding) {
        return encoding.is8BitCompatible();
    }

    static boolean isDefaultVariant(DecodingErrorHandler errorHandler) {
        return errorHandler == DecodingErrorHandler.DEFAULT ||
                        errorHandler == DecodingErrorHandler.DEFAULT_UTF8_INCOMPLETE_SEQUENCES ||
                        errorHandler == DecodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8;
    }

    static boolean isReturnNegative(DecodingErrorHandler errorHandler) {
        return errorHandler == DecodingErrorHandler.RETURN_NEGATIVE || errorHandler == DecodingErrorHandler.RETURN_NEGATIVE_UTF8_INCOMPLETE_SEQUENCES;
    }

    static boolean isBestEffort(TruffleString.ErrorHandling errorHandling) {
        return errorHandling == TruffleString.ErrorHandling.BEST_EFFORT;
    }

    static boolean isReturnNegative(TruffleString.ErrorHandling errorHandling) {
        return errorHandling == TruffleString.ErrorHandling.RETURN_NEGATIVE;
    }

    static boolean bigEndian() {
        return ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
    }

    static boolean littleEndian() {
        return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;
    }

    static boolean isInlinedJavaString(@SuppressWarnings("unused") Object string) {
        // TODO: Inlined Java Strings may be allowed as backing storage for TruffleString in the
        // future, this is a placeholder for now. (GR-34838)
        return false;
    }

    static boolean isBuiltin(DecodingErrorHandler errorHandler) {
        boolean ret = errorHandler instanceof Encodings.BuiltinDecodingErrorHandler;
        CompilerAsserts.partialEvaluationConstant(ret);
        return ret;
    }

    static boolean isBuiltin(TranscodingErrorHandler errorHandler) {
        boolean ret = errorHandler instanceof Encodings.BuiltinTranscodingErrorHandler;
        CompilerAsserts.partialEvaluationConstant(ret);
        return ret;
    }
}
