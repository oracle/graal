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
import static com.oracle.truffle.api.strings.TStringGuards.isUnsupportedEncoding;
import static com.oracle.truffle.api.strings.TStringUnsafe.byteArrayBaseOffset;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.TruffleString.ErrorHandling;
import com.oracle.truffle.api.strings.provider.JCodingsProvider;
import com.oracle.truffle.api.strings.provider.JCodingsProvider.Encoding;
import com.oracle.truffle.api.strings.provider.JCodingsProvider.TranscodeResult;

final class JCodingsImpl implements JCodings {

    private final JCodingsProvider provider;

    @CompilationFinal(dimensions = 1) private final JCodingsProvider.Encoding[] jcodingsEncodings;

    JCodingsImpl(JCodingsProvider provider) {
        this.provider = provider;

        final var encodingValues = TruffleString.Encoding.values();
        this.jcodingsEncodings = new JCodingsProvider.Encoding[encodingValues.length];
        for (var e : encodingValues) {
            var jcodingsEncoding = provider.get(e.jCodingName);
            jcodingsEncodings[e.id] = jcodingsEncoding;
            assert jcodingsEncoding.isSingleByte() == e.isSingleByte() : e;
        }
    }

    private Encoding get(TruffleString.Encoding encoding) {
        return jcodingsEncodings[encoding.id];
    }

    @Override
    public int minLength(TruffleString.Encoding encoding) {
        assert isUnsupportedEncoding(encoding);
        return get(encoding).minLength();
    }

    @Override
    public int maxLength(TruffleString.Encoding encoding) {
        assert isUnsupportedEncoding(encoding);
        return get(encoding).maxLength();
    }

    @Override
    public boolean isFixedWidth(TruffleString.Encoding encoding) {
        assert isUnsupportedEncoding(encoding);
        var jCoding = get(encoding);
        return jCoding.isFixedWidth() && jCoding.isSingleByte();
    }

    @Override
    public boolean isSingleByte(TruffleString.Encoding encoding) {
        assert isUnsupportedEncoding(encoding);
        return get(encoding).isSingleByte();
    }

    @Override
    @TruffleBoundary
    public int getCodePointLength(TruffleString.Encoding encoding, int codepoint) {
        assert isUnsupportedEncoding(encoding);
        return get(encoding).codeToMbcLength(codepoint);
    }

    @Override
    @TruffleBoundary
    public int getPreviousCodePointIndex(TruffleString.Encoding encoding, byte[] array, int arrayBegin, int index, int arrayEnd) {
        assert isUnsupportedEncoding(encoding);
        return get(encoding).prevCharHead(array, arrayBegin, index, arrayEnd);
    }

    @Override
    @TruffleBoundary
    public int getCodePointLength(TruffleString.Encoding encoding, byte[] array, int index, int arrayLength) {
        assert isUnsupportedEncoding(encoding);
        return get(encoding).length(array, index, arrayLength);
    }

    @Override
    @TruffleBoundary
    public int readCodePoint(TruffleString.Encoding encoding, byte[] array, int index, int arrayEnd, DecodingErrorHandler errorHandler) {
        assert isUnsupportedEncoding(encoding);
        var jCoding = get(encoding);
        int codePoint = jCoding.mbcToCode(array, index, arrayEnd);
        if (jCoding.isUnicode() && Encodings.isUTF16Surrogate(codePoint)) {
            return isReturnNegative(errorHandler) ? -1 : Encodings.invalidCodepoint();
        }
        return codePoint;
    }

    @Override
    @TruffleBoundary
    public boolean isValidCodePoint(TruffleString.Encoding encoding, int codepoint) {
        assert isUnsupportedEncoding(encoding);
        return !get(encoding).isUnicode() || !Encodings.isUTF16Surrogate(codepoint);
    }

    @Override
    @TruffleBoundary
    public int writeCodePoint(TruffleString.Encoding encoding, int codepoint, byte[] array, int index) {
        assert isUnsupportedEncoding(encoding);
        return get(encoding).codeToMbc(codepoint, array, index);
    }

    @Override
    @TruffleBoundary
    public int codePointIndexToRaw(Node location, AbstractTruffleString a, byte[] arrayA, int extraOffsetRaw, int index, boolean isLength, TruffleString.Encoding encoding) {
        assert isUnsupportedEncoding(encoding);
        var jCoding = get(encoding);
        int minLength = jCoding.minLength();
        if (jCoding.isFixedWidth()) {
            return index * minLength;
        }
        int offset = a.byteArrayOffset() + extraOffsetRaw;
        int end = a.byteArrayOffset() + a.length();
        int cpi = 0;
        int i = 0;
        int regionLength = a.length() - extraOffsetRaw;
        while (i < regionLength) {
            if (cpi == index) {
                return i;
            }
            int length = jCoding.length(arrayA, offset + i, end);
            if (length < 1) {
                if (length < -1) {
                    // broken multibyte codepoint at end of string
                    if (isLength) {
                        return regionLength;
                    } else {
                        throw InternalErrors.indexOutOfBounds(regionLength, index);
                    }
                } else {
                    i += minLength;
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
    @TruffleBoundary
    public int decode(AbstractTruffleString a, byte[] arrayA, int rawIndex, TruffleString.Encoding encoding, ErrorHandling errorHandling) {
        assert isUnsupportedEncoding(encoding);
        int p = a.byteArrayOffset() + rawIndex;
        int end = a.byteArrayOffset() + a.length();
        int length = getCodePointLength(encoding, arrayA, p, end);
        if (length < 1) {
            return Encodings.invalidCodepointReturnValue(errorHandling);
        }
        return readCodePoint(encoding, arrayA, p, end, errorHandling.errorHandler);
    }

    @Override
    @TruffleBoundary
    public long calcStringAttributes(Node location, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, TruffleString.Encoding encodingA, int fromIndexA) {
        assert isUnsupportedEncoding(encodingA);
        if (TStringGuards.is7BitCompatible(encodingA) && TStringOps.calcStringAttributesLatin1(location, arrayA, offsetA + fromIndexA, lengthA) == TSCodeRange.get7Bit()) {
            return StringAttributes.create(lengthA, TSCodeRange.get7Bit());
        }
        final byte[] bytes;
        final int offsetBytes;
        if (arrayA == null) {
            if (a == null) {
                bytes = new byte[lengthA];
                TStringUnsafe.copyFromNative(offsetA, 0, bytes, 0, lengthA);
            } else {
                bytes = ((AbstractTruffleString.NativePointer) a.data()).materializeByteArray(a);
            }
            offsetBytes = fromIndexA;
        } else {
            bytes = arrayA;
            offsetBytes = (int) ((offsetA - byteArrayBaseOffset()) + fromIndexA);
        }
        Encoding enc = get(encodingA);
        int codeRange = TSCodeRange.getValid(enc.isSingleByte());
        int characters = 0;
        int p = offsetBytes;
        final int end = offsetBytes + lengthA;
        int loopCount = 0;
        for (; p < end; characters++) {
            final int lengthOfCurrentCharacter = enc.length(bytes, p, end);
            if (lengthOfCurrentCharacter > 0 && p + lengthOfCurrentCharacter <= end) {
                p += lengthOfCurrentCharacter;
            } else {
                codeRange = TSCodeRange.getBroken(enc.isSingleByte());
                // If a string is detected as broken, and we already know the character length
                // due to a fixed width encoding, we can break here.
                if (enc.isFixedWidth()) {
                    characters = (lengthA + enc.minLength() - 1) / enc.minLength();
                    return StringAttributes.create(characters, codeRange);
                } else {
                    p += enc.minLength();
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
        JCodingsImpl impl = (JCodingsImpl) JCodings.getInstance();
        if (isUTF16Or32(a.encoding()) && isStride0(a)) {
            return impl.get(TruffleString.Encoding.ISO_8859_1);
        } else if (isUTF32(a.encoding()) && isStride1(a)) {
            return impl.get(TruffleString.Encoding.UTF_16);
        } else {
            return impl.get(TruffleString.Encoding.get(a.encoding()));
        }
    }

    @Override
    @TruffleBoundary
    public TruffleString transcode(Node location, AbstractTruffleString a, byte[] arrayA, int codePointLengthA, TruffleString.Encoding targetEncoding,
                    TranscodingErrorHandler errorHandler) {
        final Encoding jCodingSrc = getBytesEncoding(a);
        final Encoding jCodingDst = get(targetEncoding);
        final byte[] replacement = getConversionReplacement(targetEncoding);
        TranscodeResult result = provider.transcode(a, codePointLengthA, a.byteArrayOffset(), a.length() << a.stride(),
                        targetEncoding, jCodingSrc, jCodingDst,
                        replacement,
                        errorHandler,
                        JCodingsImpl::asBytesMaterializeNative,
                        JCodingsImpl::getBytesEncoding);
        checkArrayRange(result.buffer(), 0, result.length());
        return TStringInternalNodesFactory.FromBufferWithStringCompactionNodeGen.getUncached().execute(location,
                        result.buffer(), 0, result.length(), targetEncoding, result.length() != result.buffer().length || targetEncoding.isSupported(),
                        isBroken(a.codeRange()) || result.undefinedConversion() || a.isMutable());
    }

    private static byte[] asBytesMaterializeNative(AbstractTruffleString replacementString) {
        Object dataA = TStringInternalNodes.ToIndexableNode.getUncached().execute(null, replacementString, replacementString.data());
        if (dataA instanceof AbstractTruffleString.NativePointer nativePointer) {
            return nativePointer.materializeByteArray(replacementString);
        }
        return (byte[]) dataA;
    }
}
