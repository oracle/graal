/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.shadowed.org.jcodings.provider;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Function;

import org.graalvm.shadowed.org.jcodings.EncodingDB;
import org.graalvm.shadowed.org.jcodings.Ptr;
import org.graalvm.shadowed.org.jcodings.transcode.EConv;
import org.graalvm.shadowed.org.jcodings.transcode.EConvFlags;
import org.graalvm.shadowed.org.jcodings.transcode.EConvResult;
import org.graalvm.shadowed.org.jcodings.transcode.TranscoderDB;
import org.graalvm.shadowed.org.jcodings.util.CaseInsensitiveBytesHash;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TranscodingErrorHandler;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.provider.JCodingsProvider;

public final class JCodingsProviderImpl implements JCodingsProvider {

    private static final int MAX_BYTE_ARRAY_LENGTH = Integer.MAX_VALUE - 8;

    private static final class EncodingWrapper implements JCodingsProvider.Encoding {
        private final org.graalvm.shadowed.org.jcodings.Encoding encoding;

        private EncodingWrapper(String jcodingsName) {
            this.encoding = Lazy.load(jcodingsName);
        }

        @Override
        public String getCharsetName() {
            return encoding.getCharsetName();
        }

        @Override
        public byte[] getName() {
            return encoding.getName();
        }

        @Override
        public int minLength() {
            return encoding.minLength();
        }

        @Override
        public int maxLength() {
            return encoding.maxLength();
        }

        @Override
        public boolean isUnicode() {
            return encoding.isUnicode();
        }

        @Override
        public boolean isSingleByte() {
            return encoding.isSingleByte();
        }

        @Override
        public boolean isFixedWidth() {
            return encoding.isFixedWidth();
        }

        @Override
        public int length(byte[] array, int index, int arrayLength) {
            return encoding.length(array, index, arrayLength);
        }

        @Override
        public int codeToMbcLength(int codepoint) {
            return encoding.codeToMbcLength(codepoint);
        }

        @Override
        public int codeToMbc(int codepoint, byte[] array, int index) {
            return encoding.codeToMbc(codepoint, array, index);
        }

        @Override
        public int mbcToCode(byte[] array, int index, int arrayEnd) {
            return encoding.mbcToCode(array, index, arrayEnd);
        }

        @Override
        public int prevCharHead(byte[] array, int arrayBegin, int index, int arrayEnd) {
            return encoding.prevCharHead(array, arrayBegin, index, arrayEnd);
        }
    }

    private static final class Lazy {

        static {
            CaseInsensitiveBytesHash<EncodingDB.Entry> encodings = EncodingDB.getEncodings();
            assert encodings.size() <= MAX_JCODINGS_INDEX_VALUE : String.format(
                            "Assumption broken: jcodings has more than %d encodings (actual: %d)!", MAX_JCODINGS_INDEX_VALUE, encodings.size());
            // Load all encodings in registration order to ensure consistent Encoding.getIndex().
            for (EncodingDB.Entry entry : encodings) {
                var enc = entry.getEncoding();
                int i = enc.getIndex();
                assert i >= 0 && i < encodings.size() : String.format(
                                "Assumption broken: index of jcodings encoding \"%s\" is greater than number of encodings (index: %d, number of encodings: %d)!",
                                enc, i, encodings.size());
            }
        }

        static org.graalvm.shadowed.org.jcodings.Encoding load(String jcodingsName) {
            CompilerAsserts.neverPartOfCompilation();
            CaseInsensitiveBytesHash<EncodingDB.Entry> encodings = EncodingDB.getEncodings();
            EncodingDB.Entry entry = encodings.get(jcodingsName.getBytes(StandardCharsets.ISO_8859_1));
            if (entry == null) {
                throw new IllegalArgumentException("JCodings Encoding '%s' not found".formatted(jcodingsName));
            }
            var encoding = entry.getEncoding();
            assert jcodingsName.equals(encoding.toString()) : jcodingsName + " != " + encoding;
            return encoding;
        }
    }

    @Override
    public Encoding get(String encodingName) {
        return new EncodingWrapper(encodingName);
    }

    private static EConv getEconvTranscoder(Encoding jCodingSrc, Encoding jCodingDst, boolean customErrorHandler) {
        CompilerAsserts.neverPartOfCompilation();
        return TranscoderDB.open(jCodingSrc.getName(), jCodingDst.getName(), customErrorHandler ? 0 : EConvFlags.INVALID_REPLACE | EConvFlags.UNDEF_REPLACE);
    }

    private static void econvSetReplacement(Encoding jCodingDst, EConv econv, byte[] replacement) {
        CompilerAsserts.neverPartOfCompilation();
        econv.setReplacement(replacement, 0, replacement.length, jCodingDst.getName());
    }

    private static EConvResult econvConvert(byte[] arrayA, byte[] buffer, EConv econv, Ptr srcPtr, Ptr dstPtr, int inStop) {
        CompilerAsserts.neverPartOfCompilation();
        return econv.convert(arrayA, srcPtr, inStop, buffer, dstPtr, buffer.length, 0);
    }

    private static void econvInsertOutput(EConv econv, byte[] replacementBytes, Encoding jCodingDst) {
        CompilerAsserts.neverPartOfCompilation();
        econv.insertOutput(replacementBytes, 0, replacementBytes.length, jCodingDst.getName());
    }

    @TruffleBoundary
    @Override
    public TranscodeResult transcode(AbstractTruffleString a,
                    int codePointLengthA,
                    int byteArrayOffset,
                    int byteLength,
                    TruffleString.Encoding targetEncoding,
                    Encoding jCodingSrc,
                    Encoding jCodingDst,
                    byte[] builtinReplacement,
                    TranscodingErrorHandler errorHandler,
                    Function<AbstractTruffleString, byte[]> asBytesMaterializeNative,
                    Function<AbstractTruffleString, Encoding> getBytesEncoding) {
        final boolean isBuiltinErrorHandler = errorHandler == TranscodingErrorHandler.DEFAULT || errorHandler == TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8;
        byte[] buffer = new byte[(int) Math.min(MAX_BYTE_ARRAY_LENGTH, (long) codePointLengthA * jCodingDst.maxLength())];
        int length = 0;
        boolean undefinedConversion = false;
        EConv econv = getEconvTranscoder(jCodingSrc, jCodingDst, !isBuiltinErrorHandler);
        if (econv == null) {
            undefinedConversion = true;
            char replacementCodepoint = builtinReplacement.length == 1 ? '?' : 0xfffd;
            for (int i = 0; i < codePointLengthA; i++) {
                int ret = jCodingDst.codeToMbc(replacementCodepoint, buffer, length);
                assert ret > 0;
                length += ret;
            }
        } else {
            final Ptr srcPtr = new Ptr();
            final Ptr dstPtr = new Ptr();
            srcPtr.p = byteArrayOffset;
            dstPtr.p = 0;
            int inStop = byteArrayOffset + byteLength;
            byte[] bytes = asBytesMaterializeNative.apply(a);
            EConvResult result = econvConvert(bytes, buffer, econv, srcPtr, dstPtr, inStop);
            while (!result.isFinished()) {
                if (result.isDestinationBufferFull()) {
                    if (buffer.length == MAX_BYTE_ARRAY_LENGTH) {
                        throw new OutOfMemoryError();
                    }
                    buffer = Arrays.copyOf(buffer, (int) Math.min(MAX_BYTE_ARRAY_LENGTH, ((long) buffer.length) << 1));
                } else {
                    if (result.isUndefinedConversion() || result.isInvalidByteSequence() || result.isIncompleteInput()) {
                        undefinedConversion = true;
                        if (isBuiltinErrorHandler) {
                            econvSetReplacement(jCodingDst, econv, builtinReplacement);
                        } else {
                            byte[] errorBytes = econv.lastError.getErrorBytes();
                            int errorBytesP = econv.lastError.getErrorBytesP();
                            int errorBytesLength = econv.lastError.getErrorBytesLength();
                            TruffleString.Encoding errorEncoding = TruffleString.Encoding.fromJCodingName(stringFromLatin1Bytes(econv.lastError.getErrorTranscoding().transcoder.getSource()));
                            TruffleString errorString = TruffleString.fromByteArrayUncached(errorBytes, errorBytesP, errorBytesLength, errorEncoding, false);
                            TranscodingErrorHandler.ReplacementString customReplacement = errorHandler.apply(errorString, 0, errorBytesLength, errorEncoding, targetEncoding);
                            if (customReplacement.byteLength() >= 0) {
                                throw new UnsupportedOperationException("Custom replacement region sizes are not supported in JCodings-backed encodings");
                            }
                            TruffleString replacementString = customReplacement.replacement();
                            byte[] replacementBytes = asBytesMaterializeNative.apply(replacementString);
                            Encoding replacementEnc = getBytesEncoding.apply(replacementString);
                            econvInsertOutput(econv, replacementBytes, replacementEnc);
                        }
                    } else {
                        throw CompilerDirectives.shouldNotReachHere();
                    }
                }
                result = econvConvert(bytes, buffer, econv, srcPtr, dstPtr, inStop);
            }
            length = dstPtr.p;
        }
        return new TranscodeResult(buffer, length, undefinedConversion);
    }

    private static String stringFromLatin1Bytes(byte[] source) {
        return new String(source, StandardCharsets.ISO_8859_1);
    }
}
