/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import static com.oracle.truffle.api.strings.AbstractTruffleString.checkArrayRange;
import static com.oracle.truffle.api.strings.TStringGuards.isStride0;
import static com.oracle.truffle.api.strings.TStringGuards.isStride1;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16Or32;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF32;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF8;
import static com.oracle.truffle.api.strings.TruffleString.ErrorHandling;

import java.util.Arrays;

import org.graalvm.collections.EconomicMap;
import org.graalvm.shadowed.org.jcodings.EncodingDB;
import org.graalvm.shadowed.org.jcodings.Ptr;
import org.graalvm.shadowed.org.jcodings.transcode.EConv;
import org.graalvm.shadowed.org.jcodings.transcode.EConvFlags;
import org.graalvm.shadowed.org.jcodings.transcode.EConvResult;
import org.graalvm.shadowed.org.jcodings.transcode.TranscoderDB;
import org.graalvm.shadowed.org.jcodings.util.CaseInsensitiveBytesHash;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;

final class JCodingsImpl implements JCodings {

    private static final class EncodingWrapper implements Encoding {
        private final org.graalvm.shadowed.org.jcodings.Encoding encoding;

        private EncodingWrapper(org.graalvm.shadowed.org.jcodings.Encoding encoding) {
            this.encoding = encoding;
        }
    }

    private static final int MAX_J_CODINGS_INDEX_VALUE = 0x7f;

    @CompilationFinal private static final EconomicMap<String, EncodingWrapper> J_CODINGS_MAP = createJCodingsMap();

    @TruffleBoundary
    private static EconomicMap<String, EncodingWrapper> createJCodingsMap() {
        CaseInsensitiveBytesHash<EncodingDB.Entry> encodings = EncodingDB.getEncodings();
        if (encodings.size() > MAX_J_CODINGS_INDEX_VALUE) {
            throw new RuntimeException(String.format("Assumption broken: org.graalvm.shadowed.org.jcodings has more than %d encodings (actual: %d)!", MAX_J_CODINGS_INDEX_VALUE, encodings.size()));
        }
        EconomicMap<String, EncodingWrapper> allEncodings = EconomicMap.create(encodings.size());
        for (EncodingDB.Entry entry : encodings) {
            org.graalvm.shadowed.org.jcodings.Encoding enc = entry.getEncoding();
            int i = enc.getIndex();
            if (i < 0 || i >= encodings.size()) {
                throw new RuntimeException(String.format(
                                "Assumption broken: index of org.graalvm.shadowed.org.jcodings encoding \"%s\" is greater than number of encodings (index: %d, number of encodings: %d)!", enc, i,
                                encodings.size()));
            }
            allEncodings.put(toEnumName(enc.toString()), new EncodingWrapper(enc));
        }
        return allEncodings;
    }

    @Override
    public Encoding get(String encodingName) {
        return J_CODINGS_MAP.get(encodingName);
    }

    @Override
    public Encoding get(TruffleString.Encoding encoding) {
        return encoding.jCoding;
    }

    @Override
    public String name(Encoding jCoding) {
        return unwrap(jCoding).toString();
    }

    @Override
    public int minLength(Encoding jCoding) {
        return unwrap(jCoding).minLength();
    }

    @Override
    public int maxLength(Encoding jCoding) {
        return unwrap(jCoding).maxLength();
    }

    @Override
    public boolean isFixedWidth(Encoding jCoding) {
        return unwrap(jCoding).isFixedWidth();
    }

    @Override
    public boolean isSingleByte(Encoding jCoding) {
        return unwrap(jCoding).isSingleByte();
    }

    @Override
    @TruffleBoundary
    public int getCodePointLength(Encoding jCoding, int codepoint) {
        return unwrap(jCoding).codeToMbcLength(codepoint);
    }

    @Override
    @TruffleBoundary
    public int getPreviousCodePointIndex(Encoding jCoding, byte[] array, int arrayBegin, int index, int arrayEnd) {
        return unwrap(jCoding).prevCharHead(array, arrayBegin, index, arrayEnd);
    }

    @Override
    @TruffleBoundary
    public int getCodePointLength(Encoding jCoding, byte[] array, int index, int arrayLength) {
        return unwrap(jCoding).length(array, index, arrayLength);
    }

    @Override
    @TruffleBoundary
    public int readCodePoint(Encoding jCoding, byte[] array, int index, int arrayEnd) {
        return unwrap(jCoding).mbcToCode(array, index, arrayEnd);
    }

    @Override
    @TruffleBoundary
    public int writeCodePoint(Encoding jCoding, int codepoint, byte[] array, int index) {
        return unwrap(jCoding).codeToMbc(codepoint, array, index);
    }

    @Override
    @TruffleBoundary
    public int codePointIndexToRaw(Node location, AbstractTruffleString a, byte[] arrayA, int extraOffsetRaw, int index, boolean isLength, Encoding jCoding) {
        if (isFixedWidth(jCoding)) {
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
            int length = unwrap(jCoding).length(arrayA, offset + i, end);
            if (length < 1) {
                if (length < -1) {
                    // broken multibyte codepoint at end of string
                    if (isLength) {
                        return a.length() - extraOffsetRaw;
                    } else {
                        throw InternalErrors.indexOutOfBounds();
                    }
                } else {
                    i++;
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
        return readCodePoint(jCoding, arrayA, p, end);
    }

    @Override
    public long calcStringAttributes(Node location, Object array, int offset, int length, TruffleString.Encoding encoding, int fromIndex, ConditionProfile validCharacterProfile,
                    ConditionProfile fixedWidthProfile) {
        if (TStringGuards.is7BitCompatible(encoding) && TStringOps.calcStringAttributesLatin1(location, array, offset + fromIndex, length) == TSCodeRange.get7Bit()) {
            return StringAttributes.create(length, TSCodeRange.get7Bit());
        }
        byte[] bytes = JCodings.asByteArray(array);
        int offsetBytes = array instanceof AbstractTruffleString.NativePointer ? fromIndex : offset + fromIndex;
        Encoding enc = get(encoding);
        int codeRange = isSingleByte(enc) ? TSCodeRange.getValidFixedWidth() : TSCodeRange.getValidMultiByte();
        int characters = 0;
        int p = offsetBytes;
        final int end = offsetBytes + length;
        int loopCount = 0;
        for (; p < end; characters++) {
            final int lengthOfCurrentCharacter = getCodePointLength(enc, bytes, p, end);
            if (validCharacterProfile.profile(lengthOfCurrentCharacter > 0 && p + lengthOfCurrentCharacter <= end)) {
                p += lengthOfCurrentCharacter;
            } else {
                codeRange = isSingleByte(enc) ? TSCodeRange.getBrokenFixedWidth() : TSCodeRange.getBrokenMultiByte();
                // If a string is detected as broken, and we already know the character length
                // due to a fixed width encoding, there's no value in visiting any more ptr.
                if (fixedWidthProfile.profile(isFixedWidth(enc))) {
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

    @TruffleBoundary
    private static EConv getEconvTranscoder(Encoding jCodingSrc, Encoding jCodingDst) {
        return TranscoderDB.open(unwrap(jCodingSrc).getName(), unwrap(jCodingDst).getName(), EConvFlags.INVALID_REPLACE | EConvFlags.UNDEF_REPLACE);
    }

    @TruffleBoundary
    private static void econvSetReplacement(Encoding jCodingDst, EConv econv, byte[] replacement) {
        econv.setReplacement(replacement, 0, replacement.length, unwrap(jCodingDst).getName());
    }

    @TruffleBoundary
    private static EConvResult econvConvert(byte[] arrayA, byte[] buffer, EConv econv, Ptr srcPtr, Ptr dstPtr, int inStop) {
        return econv.convert(arrayA, srcPtr, inStop, buffer, dstPtr, buffer.length, 0);
    }

    private static final byte[] CONVERSION_REPLACEMENT = {'?'};
    private static final byte[] CONVERSION_REPLACEMENT_UTF_8 = {(byte) 0xEF, (byte) 0xBF, (byte) 0xBD};
    private static final byte[] CONVERSION_REPLACEMENT_UTF_16 = TStringGuards.littleEndian() ? new byte[]{(byte) 0xFD, (byte) 0xFF} : new byte[]{(byte) 0xFF, (byte) 0xFD};
    private static final byte[] CONVERSION_REPLACEMENT_UTF_32 = TStringGuards.littleEndian() ? new byte[]{(byte) 0xFD, (byte) 0xFF, 0, 0} : new byte[]{0, 0, (byte) 0xFF, (byte) 0xFD};

    @Override
    public TruffleString transcode(Node location, AbstractTruffleString a, Object arrayA, int codePointLengthA, TruffleString.Encoding targetEncoding,
                    BranchProfile outOfMemoryProfile,
                    ConditionProfile nativeProfile,
                    TStringInternalNodes.FromBufferWithStringCompactionNode fromBufferWithStringCompactionNode) {
        final TruffleString.Encoding encoding = TruffleString.Encoding.get(a.encoding());
        final JCodings.Encoding jCodingSrc;
        if (isUTF16Or32(encoding) && isStride0(a)) {
            jCodingSrc = TruffleString.Encoding.ISO_8859_1.jCoding;
        } else if (isUTF32(encoding) && isStride1(a)) {
            jCodingSrc = TruffleString.Encoding.UTF_16.jCoding;
        } else {
            jCodingSrc = JCodings.getInstance().get(encoding);
        }
        JCodings.Encoding jCodingDst = JCodings.getInstance().get(targetEncoding);
        byte[] buffer = new byte[(int) Math.min(TStringConstants.MAX_ARRAY_SIZE, ((long) codePointLengthA) * JCodings.getInstance().maxLength(jCodingDst))];
        int length = 0;
        EConv econv = getEconvTranscoder(jCodingSrc, jCodingDst);
        boolean undefinedConversion = false;
        if (econv == null) {
            undefinedConversion = true;
            int loopCount = 0;
            for (int i = 0; i < codePointLengthA; i++) {
                int ret = JCodings.getInstance().writeCodePoint(jCodingDst, isUTF8(targetEncoding) || isUTF16Or32(targetEncoding) ? 0xfffd : '?', buffer, length);
                assert ret > 0;
                length += ret;
                TStringConstants.truffleSafePointPoll(location, ++loopCount);
            }
        } else {
            final byte[] replacement;
            if (isUTF8(targetEncoding)) {
                replacement = CONVERSION_REPLACEMENT_UTF_8;
            } else if (isUTF16(targetEncoding)) {
                replacement = CONVERSION_REPLACEMENT_UTF_16;
            } else if (isUTF32(targetEncoding)) {
                replacement = CONVERSION_REPLACEMENT_UTF_32;
            } else {
                replacement = CONVERSION_REPLACEMENT;
            }
            final Ptr srcPtr = new Ptr();
            final Ptr dstPtr = new Ptr();
            srcPtr.p = a.byteArrayOffset();
            dstPtr.p = 0;
            int inStop = a.byteArrayOffset() + (a.length() << a.stride());
            if (arrayA instanceof AbstractTruffleString.NativePointer) {
                ((AbstractTruffleString.NativePointer) arrayA).materializeByteArray(a, nativeProfile);
            }
            byte[] bytes = JCodings.asByteArray(arrayA);
            EConvResult result = econvConvert(bytes, buffer, econv, srcPtr, dstPtr, inStop);
            while (!result.isFinished()) {
                if (result.isUndefinedConversion()) {
                    undefinedConversion = true;
                    econvSetReplacement(jCodingDst, econv, replacement);
                } else if (result.isDestinationBufferFull()) {
                    if (buffer.length == TStringConstants.MAX_ARRAY_SIZE) {
                        outOfMemoryProfile.enter();
                        throw InternalErrors.outOfMemory();
                    }
                    buffer = Arrays.copyOf(buffer, (int) Math.min(TStringConstants.MAX_ARRAY_SIZE, ((long) buffer.length) << 1));
                } else {
                    throw CompilerDirectives.shouldNotReachHere();
                }
                result = econvConvert(bytes, buffer, econv, srcPtr, dstPtr, inStop);
            }
            length = dstPtr.p;
        }
        checkArrayRange(buffer, 0, length);
        return fromBufferWithStringCompactionNode.execute(
                        buffer, 0, length, targetEncoding, length != buffer.length || targetEncoding.isSupported(), undefinedConversion || a.isMutable());
    }

    @TruffleBoundary
    private static String toEnumName(String encodingName) {
        if ("ASCII-8BIT".equals(encodingName)) {
            return "BYTES";
        }
        String capitalized = encodingName;
        if (Character.isLowerCase(encodingName.charAt(0))) {
            capitalized = Character.toUpperCase(encodingName.charAt(0)) + encodingName.substring(1);
        }
        return capitalized.replace('-', '_');
    }

    private static org.graalvm.shadowed.org.jcodings.Encoding unwrap(Encoding wrapped) {
        return ((EncodingWrapper) wrapped).encoding;
    }
}
