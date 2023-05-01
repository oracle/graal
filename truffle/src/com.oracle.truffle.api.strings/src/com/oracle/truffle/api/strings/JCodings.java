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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleOptions;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;

interface JCodings {

    interface Encoding {
    }

    boolean ENABLED = !TruffleOptions.AOT || TStringAccessor.getNeedsAllEncodings();
    JCodings INSTANCE = ENABLED ? new JCodingsImpl() : new JCodingsDisabled();

    static JCodings getInstance() {
        return INSTANCE;
    }

    static byte[] asByteArray(Object array) {
        if (array instanceof AbstractTruffleString.NativePointer) {
            return ((AbstractTruffleString.NativePointer) array).getBytes();
        }
        return (byte[]) array;
    }

    Encoding get(String encodingName);

    Encoding get(TruffleString.Encoding encoding);

    String name(Encoding jCoding);

    /**
     * No TruffleBoundary because this method is a final getter in the Encoding base class.
     */
    int minLength(Encoding enc);

    /**
     * No TruffleBoundary because this method is a final getter in the Encoding base class.
     */
    int maxLength(Encoding e);

    /**
     * No TruffleBoundary because this method is a final getter in the Encoding base class.
     */
    boolean isFixedWidth(Encoding enc);

    /**
     * No TruffleBoundary because this method is a final getter in the Encoding base class.
     */
    boolean isSingleByte(Encoding enc);

    @TruffleBoundary
    int getCodePointLength(Encoding jCoding, int codepoint);

    @TruffleBoundary
    int getPreviousCodePointIndex(Encoding jCoding, byte[] array, int arrayBegin, int index, int arrayEnd);

    @TruffleBoundary
    int getCodePointLength(Encoding jCoding, byte[] array, int index, int arrayLength);

    @TruffleBoundary
    int readCodePoint(Encoding jCoding, byte[] array, int index, int arrayEnd, TruffleString.ErrorHandling errorHandling);

    @TruffleBoundary
    int writeCodePoint(Encoding jCoding, int codepoint, byte[] array, int index);

    @TruffleBoundary
    int codePointIndexToRaw(Node location, AbstractTruffleString a, byte[] arrayA, int extraOffsetRaw, int index, boolean isLength, Encoding jCoding);

    int decode(AbstractTruffleString a, byte[] arrayA, int rawIndex, Encoding jCoding, TruffleString.ErrorHandling errorHandling);

    long calcStringAttributes(Node location, Object array, int offset, int length, TruffleString.Encoding encoding, int fromIndex, InlinedConditionProfile validCharacterProfile,
                    InlinedConditionProfile fixedWidthProfile);

    TruffleString transcode(Node location, AbstractTruffleString a, Object arrayA, int codePointLengthA, TruffleString.Encoding targetEncoding,
                    InlinedBranchProfile outOfMemoryProfile,
                    InlinedConditionProfile nativeProfile,
                    TStringInternalNodes.FromBufferWithStringCompactionNode fromBufferWithStringCompactionNode);
}
