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

import java.nio.ByteOrder;

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

    static boolean isValidFixedWidth(int codeRange) {
        return TSCodeRange.isValidFixedWidth(codeRange);
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

    static boolean isBrokenMultiByte(TruffleStringBuilder sb) {
        return TSCodeRange.isBrokenMultiByte(sb.getCodeRange());
    }

    static boolean isUnknown(int codeRange) {
        return TSCodeRange.isUnknown(codeRange);
    }

    static boolean isBrokenMultiByteOrUnknown(int codeRange) {
        return TSCodeRange.isBrokenMultiByteOrUnknown(codeRange);
    }

    public static boolean isValidBrokenOrUnknownMultiByte(int codeRange) {
        return TSCodeRange.isValidBrokenOrUnknownMultiByte(codeRange);
    }

    static boolean isFixedWidth(int codeRange) {
        return TSCodeRange.isFixedWidth(codeRange);
    }

    static boolean isFixedWidth(int codeRangeA, int codeRangeB) {
        return isFixedWidth(codeRangeA) && isFixedWidth(codeRangeB);
    }

    static boolean indexOfCannotMatch(AbstractTruffleString a, int codeRangeA, AbstractTruffleString b, int codeRangeB, byte[] mask) {
        return a.length() < b.length() || mask == null &&
                        !TSCodeRange.isBrokenMultiByteOrUnknown(codeRangeA) &&
                        !TSCodeRange.isBrokenMultiByteOrUnknown(codeRangeB) &&
                        TSCodeRange.isMoreRestrictiveThan(codeRangeA, codeRangeB);
    }

    static boolean isAscii(AbstractTruffleString a) {
        return isAscii(a.encoding());
    }

    static boolean isAscii(TruffleString.Encoding enc) {
        return isAscii(enc.id);
    }

    static boolean isAscii(int enc) {
        return enc == TruffleString.Encoding.US_ASCII.id;
    }

    static boolean isBytes(AbstractTruffleString a) {
        return isBytes(a.encoding());
    }

    static boolean isBytes(TruffleString.Encoding enc) {
        return isBytes(enc.id);
    }

    static boolean isBytes(int enc) {
        return enc == TruffleString.Encoding.BYTES.id;
    }

    static boolean isLatin1(TruffleString.Encoding enc) {
        return isLatin1(enc.id);
    }

    static boolean isLatin1(int enc) {
        return enc == TruffleString.Encoding.ISO_8859_1.id;
    }

    static boolean isAsciiBytesOrLatin1(int enc) {
        return isAscii(enc) || isLatin1(enc) || isBytes(enc);
    }

    static boolean isUTF8(TruffleString.Encoding enc) {
        return isUTF8(enc.id);
    }

    static boolean isUTF8(int enc) {
        return enc == TruffleString.Encoding.UTF_8.id;
    }

    static boolean isUTF8(AbstractTruffleString a) {
        return isUTF8(a.encoding());
    }

    static boolean isUTF16(TruffleString.Encoding enc) {
        return isUTF16(enc.id);
    }

    static boolean isUTF16(int enc) {
        return enc == TruffleString.Encoding.UTF_16.id;
    }

    static boolean isUTF16(AbstractTruffleString a) {
        return isUTF16(a.encoding());
    }

    static boolean isUTF16(TruffleStringBuilder sb) {
        return isUTF16(sb.getEncoding());
    }

    static boolean isUTF32(TruffleString.Encoding enc) {
        return isUTF32(enc.id);
    }

    static boolean isUTF32(int enc) {
        return enc == TruffleString.Encoding.UTF_32.id;
    }

    static boolean isUTF32(AbstractTruffleString a) {
        return isUTF32(a.encoding());
    }

    static boolean isUTF16Or32(TruffleString.Encoding enc) {
        return isUTF16Or32(enc.id);
    }

    static boolean isUTF16Or32(int enc) {
        assert enc >= 0;
        assert TruffleString.Encoding.UTF_32.id == 0;
        assert TruffleString.Encoding.UTF_16.id == 1;
        return enc <= 1;
    }

    static boolean isUTF16Or32(AbstractTruffleString a) {
        return isUTF16Or32(a.encoding());
    }

    static boolean identical(Object a, Object b) {
        return a == b;
    }

    static boolean isSupportedEncoding(TruffleString.Encoding encoding) {
        return isSupportedEncoding(encoding.id);
    }

    static boolean isSupportedEncoding(int encoding) {
        return TruffleString.Encoding.isSupported(encoding);
    }

    static boolean isSupportedEncoding(AbstractTruffleString a) {
        return isSupportedEncoding(a.encoding());
    }

    static boolean isUnsupportedEncoding(TruffleString.Encoding encoding) {
        return isUnsupportedEncoding(encoding.id);
    }

    static boolean isUnsupportedEncoding(int encoding) {
        return TruffleString.Encoding.isUnsupported(encoding);
    }

    static boolean isUnsupportedEncoding(AbstractTruffleString a) {
        return isUnsupportedEncoding(a.encoding());
    }

    static int stride(AbstractTruffleString a) {
        return a.stride();
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

    static boolean isStride0(TruffleStringBuilder sb) {
        return sb.getStride() == 0;
    }

    static boolean isStride1(TruffleStringBuilder sb) {
        return sb.getStride() == 1;
    }

    static boolean isStride2(TruffleStringBuilder sb) {
        return sb.getStride() == 2;
    }

    static boolean is7BitCompatible(TruffleString.Encoding encoding) {
        return is7BitCompatible(encoding.id);
    }

    static boolean is8BitCompatible(TruffleString.Encoding encoding) {
        return is8BitCompatible(encoding.id);
    }

    static boolean is7BitCompatible(int encoding) {
        return TruffleString.Encoding.is7BitCompatible(encoding);
    }

    static boolean is8BitCompatible(int encoding) {
        return TruffleString.Encoding.is8BitCompatible(encoding);
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
}
