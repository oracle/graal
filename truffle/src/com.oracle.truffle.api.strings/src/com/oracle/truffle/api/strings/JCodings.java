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

import java.util.HashMap;
import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.strings.provider.JCodingsProvider;

sealed interface JCodings permits JCodingsImpl {

    JCodingsProvider PROVIDER = loadProvider();
    boolean JCODINGS_ENABLED = PROVIDER != null;
    JCodings INSTANCE = JCODINGS_ENABLED ? new JCodingsImpl(PROVIDER) : null;

    private static JCodingsProvider loadProvider() {
        if (TruffleOptions.AOT && !TStringAccessor.getNeedsAllEncodings()) {
            /*
             * In the AOT case, we already know all included languages up front; if none of them
             * need all encodings, we do not need to enable jcodings support even if it's available.
             * In the non-AOT case on the other hand, languages are loaded lazily, so we cannot make
             * such assumptions.
             */
            return null;
        }
        for (JCodingsProvider provider : loadService(JCodingsProvider.class)) {
            return provider;
        }
        return null;
    }

    private static <S> Iterable<S> loadService(Class<S> service) {
        return TStringAccessor.ACCESSOR.engineSupport().loadServices(service);
    }

    static JCodings getInstance() {
        if (INSTANCE == null) {
            throw CompilerDirectives.shouldNotReachHere("TruffleStrings: JCodings is disabled!");
        }
        return INSTANCE;
    }

    static byte[] asByteArray(AbstractTruffleString a, byte[] arrayA) {
        if (arrayA == null) {
            return ((AbstractTruffleString.NativePointer) a.data()).materializeByteArray(a);
        }
        return arrayA;
    }

    /**
     * No TruffleBoundary because this method is a final getter in the Encoding base class.
     */
    int minLength(TruffleString.Encoding encoding);

    /**
     * No TruffleBoundary because this method is a final getter in the Encoding base class.
     */
    int maxLength(TruffleString.Encoding encoding);

    /**
     * No TruffleBoundary because this method is a final getter in the Encoding base class.
     */
    boolean isFixedWidth(TruffleString.Encoding encoding);

    /**
     * No TruffleBoundary because this method is a final getter in the Encoding base class.
     */
    boolean isSingleByte(TruffleString.Encoding encoding);

    @TruffleBoundary
    int getCodePointLength(TruffleString.Encoding encoding, int codepoint);

    @TruffleBoundary
    int getPreviousCodePointIndex(TruffleString.Encoding encoding, byte[] array, int arrayBegin, int index, int arrayEnd);

    @TruffleBoundary
    int getCodePointLength(TruffleString.Encoding encoding, byte[] array, int index, int arrayLength);

    @TruffleBoundary
    int readCodePoint(TruffleString.Encoding encoding, byte[] array, int index, int arrayEnd, DecodingErrorHandler errorHandler);

    @TruffleBoundary
    boolean isValidCodePoint(TruffleString.Encoding encoding, int codepoint);

    @TruffleBoundary
    int writeCodePoint(TruffleString.Encoding encoding, int codepoint, byte[] array, int index);

    @TruffleBoundary
    int codePointIndexToRaw(Node location, AbstractTruffleString a, byte[] arrayA, int extraOffsetRaw, int index, boolean isLength, TruffleString.Encoding encoding);

    @TruffleBoundary
    int decode(AbstractTruffleString a, byte[] arrayA, int rawIndex, TruffleString.Encoding encoding, TruffleString.ErrorHandling errorHandling);

    @TruffleBoundary
    long calcStringAttributes(Node location, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, TruffleString.Encoding encodingA, int fromIndexA);

    @TruffleBoundary
    TruffleString transcode(Node location, AbstractTruffleString a, byte[] arrayA, int codePointLengthA, TruffleString.Encoding targetEncoding, TranscodingErrorHandler errorHandler);

    static TruffleString.Encoding fromJCodingsName(String jCodingsName) {
        // This mapping does not actually require JCodings to be present to work.
        final class Lazy {
            static final Map<String, TruffleString.Encoding> JCODINGS_NAME_MAP;

            static {
                final var encodingValues = TruffleString.Encoding.values();
                // Java 17 compatible version of (Java 19) newHashMap(encodingValues.length)
                Map<String, TruffleString.Encoding> jcodingsNameMap = new HashMap<>(encodingValues.length + encodingValues.length / 3);
                for (var encoding : encodingValues) {
                    jcodingsNameMap.put(encoding.jCodingName, encoding);
                }
                JCODINGS_NAME_MAP = Map.copyOf(jcodingsNameMap);
            }

            private Lazy() {
            }
        }
        return Lazy.JCODINGS_NAME_MAP.get(jCodingsName);
    }
}
