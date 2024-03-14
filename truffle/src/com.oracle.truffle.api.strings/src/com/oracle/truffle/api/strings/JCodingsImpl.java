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

import static com.oracle.truffle.api.strings.AbstractTruffleString.checkArrayRange;
import static com.oracle.truffle.api.strings.TStringGuards.isBroken;
import static com.oracle.truffle.api.strings.TStringGuards.isReturnNegative;
import static com.oracle.truffle.api.strings.TStringGuards.isStride0;
import static com.oracle.truffle.api.strings.TStringGuards.isStride1;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16Or32;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF32;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF8;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString.ErrorHandling;
import com.oracle.truffle.api.strings.provider.JCodingsProvider;
import com.oracle.truffle.api.strings.provider.JCodingsProvider.Encoding;
import com.oracle.truffle.api.strings.provider.JCodingsProvider.TranscodeResult;

final class JCodingsImpl implements JCodings {

    private final JCodingsProvider provider;

    JCodingsImpl(JCodingsProvider provider) {
        this.provider = provider;
    }

    @Override
    public JCodingsProvider.Encoding get(String encodingName) {
        return provider.get(encodingName);
    }

    @Override
    public Encoding get(TruffleString.Encoding encoding) {
        return encoding.jCoding;
    }

    @Override
    public String name(Encoding jCoding) {
        return jCoding.getCharsetName();
    }

    @Override
    public int minLength(Encoding jCoding) {
        return jCoding.minLength();
    }

    @Override
    public int maxLength(Encoding jCoding) {
        return jCoding.maxLength();
    }

    @Override
    public boolean isFixedWidth(Encoding jCoding) {
        return jCoding.isFixedWidth() && isSingleByte(jCoding);
    }

    @Override
    public boolean isSingleByte(Encoding jCoding) {
        return jCoding.isSingleByte();
    }

    @Override
    @TruffleBoundary
    public int getCodePointLength(Encoding jCoding, int codepoint) {
        return jCoding.codeToMbcLength(codepoint);
    }

    @Override
    @TruffleBoundary
    public int getPreviousCodePointIndex(Encoding jCoding, byte[] array, int arrayBegin, int index, int arrayEnd) {
        return jCoding.prevCharHead(array, arrayBegin, index, arrayEnd);
    }

    @Override
    @TruffleBoundary
    public int getCodePointLength(Encoding jCoding, byte[] array, int index, int arrayLength) {
        return jCoding.length(array, index, arrayLength);
    }

    @Override
    @TruffleBoundary
    public int readCodePoint(Encoding jCoding, byte[] array, int index, int arrayEnd, DecodingErrorHandler errorHandler) {
        int codePoint = jCoding.mbcToCode(array, index, arrayEnd);
        if (jCoding.isUnicode() && Encodings.isUTF16Surrogate(codePoint)) {
            return isReturnNegative(errorHandler) ? -1 : Encodings.invalidCodepoint();
        }
        return codePoint;
    }

    @Override
    @TruffleBoundary
    public boolean isValidCodePoint(Encoding jCoding, int codepoint) {
        return !jCoding.isUnicode() || !Encodings.isUTF16Surrogate(codepoint);
    }

    @Override
    @TruffleBoundary
    public int writeCodePoint(Encoding jCoding, int codepoint, byte[] array, int index) {
        return jCoding.codeToMbc(codepoint, array, index);
    }

    @Override
    @TruffleBoundary
    public int codePointIndexToRaw(Node location, AbstractTruffleString a, byte[] arrayA, int extraOffsetRaw, int index, boolean isLength, Encoding jCoding) {
        if (jCoding.isFixedWidth()) {
            return index * minLength(jCoding);
        }
        int offset = a.byteArrayOffset() + extraOffsetRaw;
        int end = a.byteArrayOffset() + a.length();
        int cpi = 0;
        int i = 0;
        while (i < a.length() - extraOffsetRaw) {
            if (cpi == index) {
                return i;
            }
            int length = jCoding.length(arrayA, offset + i, end);
            if (length < 1) {
                if (length < -1) {
                    // broken multibyte codepoint at end of string
                    if (isLength) {
                        return a.length() - extraOffsetRaw;
                    } else {
                        throw InternalErrors.indexOutOfBounds();
                    }
                } else {
                    i += minLength(jCoding);
                }
            } else {
                i += length;
            }
            cpi++;
            TStringConstants.truffleSafePointPoll(location, cpi);
        }
        return TStringInternalNodes.CodePointIndexToRawNode.atEnd(a, extraOffsetRaw, index, isLength, cpi);
    }

    @Override
    public int decode(AbstractTruffleString a, byte[] arrayA, int rawIndex, Encoding jCoding, ErrorHandling errorHandling) {
        int p = a.byteArrayOffset() + rawIndex;
        int end = a.byteArrayOffset() + a.length();
        int length = getCodePointLength(jCoding, arrayA, p, end);
        if (length < 1) {
            return Encodings.invalidCodepointReturnValue(errorHandling);
        }
        return readCodePoint(jCoding, arrayA, p, end, errorHandling.errorHandler);
    }

    @Override
    public long calcStringAttributes(Node location, Object array, int offset, int length, TruffleString.Encoding encoding, int fromIndex, InlinedConditionProfile validCharacterProfile,
                    InlinedConditionProfile fixedWidthProfile) {
        if (TStringGuards.is7BitCompatible(encoding) && TStringOps.calcStringAttributesLatin1(location, array, offset + fromIndex, length) == TSCodeRange.get7Bit()) {
            return StringAttributes.create(length, TSCodeRange.get7Bit());
        }
        byte[] bytes = JCodings.asByteArray(array);
        int offsetBytes = array instanceof AbstractTruffleString.NativePointer ? fromIndex : offset + fromIndex;
        Encoding enc = get(encoding);
        int codeRange = TSCodeRange.getValid(isSingleByte(enc));
        int characters = 0;
        int p = offsetBytes;
        final int end = offsetBytes + length;
        int loopCount = 0;
        for (; p < end; characters++) {
            final int lengthOfCurrentCharacter = getCodePointLength(enc, bytes, p, end);
            if (validCharacterProfile.profile(location, lengthOfCurrentCharacter > 0 && p + lengthOfCurrentCharacter <= end)) {
                p += lengthOfCurrentCharacter;
            } else {
                codeRange = TSCodeRange.getBroken(isSingleByte(enc));
                // If a string is detected as broken, and we already know the character length
                // due to a fixed width encoding, we can break here.
                if (fixedWidthProfile.profile(location, enc.isFixedWidth())) {
                    characters = (length + minLength(enc) - 1) / minLength(enc);
                    return StringAttributes.create(characters, codeRange);
                } else {
                    p += minLength(enc);
                }
            }
            TStringConstants.truffleSafePointPoll(location, ++loopCount);
        }
        return StringAttributes.create(characters, codeRange);
    }

    private static final byte[] CONVERSION_REPLACEMENT = {'?'};
    private static final byte[] CONVERSION_REPLACEMENT_UTF_16 = TStringGuards.littleEndian() ? new byte[]{(byte) 0xFD, (byte) 0xFF} : new byte[]{(byte) 0xFF, (byte) 0xFD};
    private static final byte[] CONVERSION_REPLACEMENT_UTF_32 = TStringGuards.littleEndian() ? new byte[]{(byte) 0xFD, (byte) 0xFF, 0, 0} : new byte[]{0, 0, (byte) 0xFF, (byte) 0xFD};

    private static byte[] getConversionReplacement(TruffleString.Encoding targetEncoding) {
        if (isUTF8(targetEncoding)) {
            return Encodings.CONVERSION_REPLACEMENT_UTF_8;
        } else if (isUTF16(targetEncoding)) {
            return CONVERSION_REPLACEMENT_UTF_16;
        } else if (isUTF32(targetEncoding)) {
            return CONVERSION_REPLACEMENT_UTF_32;
        } else {
            return CONVERSION_REPLACEMENT;
        }
    }

    private static Encoding getBytesEncoding(AbstractTruffleString a) {
        if (isUTF16Or32(a.encoding()) && isStride0(a)) {
            return TruffleString.Encoding.ISO_8859_1.jCoding;
        } else if (isUTF32(a.encoding()) && isStride1(a)) {
            return TruffleString.Encoding.UTF_16.jCoding;
        } else {
            return JCodings.getInstance().get(TruffleString.Encoding.get(a.encoding()));
        }
    }

    @Override
    public TruffleString transcode(Node location, AbstractTruffleString a, Object arrayA, int codePointLengthA, TruffleString.Encoding targetEncoding,
                    TStringInternalNodes.FromBufferWithStringCompactionNode fromBufferWithStringCompactionNode,
                    TranscodingErrorHandler errorHandler) {
        final Encoding jCodingSrc = getBytesEncoding(a);
        final Encoding jCodingDst = JCodings.getInstance().get(targetEncoding);
        final byte[] replacement = getConversionReplacement(targetEncoding);
        TranscodeResult result = provider.transcode(a, codePointLengthA, a.byteArrayOffset(), a.length() << a.stride(),
                        targetEncoding, jCodingSrc, jCodingDst,
                        replacement,
                        errorHandler,
                        JCodingsImpl::asBytesMaterializeNative,
                        JCodingsImpl::getBytesEncoding);
        checkArrayRange(result.buffer(), 0, result.length());
        return fromBufferWithStringCompactionNode.execute(location,
                        result.buffer(), 0, result.length(), targetEncoding, result.length() != result.buffer().length || targetEncoding.isSupported(),
                        isBroken(a.codeRange()) || result.undefinedConversion() || a.isMutable());
    }

    private static byte[] asBytesMaterializeNative(AbstractTruffleString replacementString) {
        return asBytesMaterializeNative(replacementString, TruffleString.ToIndexableNode.getUncached().execute(null, replacementString, replacementString.data()));
    }

    private static byte[] asBytesMaterializeNative(AbstractTruffleString a, Object arrayA) {
        if (arrayA instanceof AbstractTruffleString.NativePointer) {
            ((AbstractTruffleString.NativePointer) arrayA).materializeByteArray(null, a, InlinedConditionProfile.getUncached());
        }
        return JCodings.asByteArray(arrayA);
    }
}
