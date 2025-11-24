/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import static com.oracle.truffle.api.strings.AbstractTruffleString.checkByteLengthUTF16;
import static com.oracle.truffle.api.strings.AbstractTruffleString.checkByteLengthUTF32;
import static com.oracle.truffle.api.strings.Encodings.UTF8_ACCEPT;
import static com.oracle.truffle.api.strings.Encodings.UTF8_REJECT;
import static com.oracle.truffle.api.strings.Encodings.isUTF16Surrogate;
import static com.oracle.truffle.api.strings.TSCodeRange.isBroken;
import static com.oracle.truffle.api.strings.TSCodeRange.isPrecise;
import static com.oracle.truffle.api.strings.TSCodeRange.isUpToValid;
import static com.oracle.truffle.api.strings.TStringGuards.indexOfCannotMatch;
import static com.oracle.truffle.api.strings.TStringGuards.is16Bit;
import static com.oracle.truffle.api.strings.TStringGuards.is7Bit;
import static com.oracle.truffle.api.strings.TStringGuards.is7Or8Bit;
import static com.oracle.truffle.api.strings.TStringGuards.is8Bit;
import static com.oracle.truffle.api.strings.TStringGuards.isAscii;
import static com.oracle.truffle.api.strings.TStringGuards.isAsciiBytesOrLatin1;
import static com.oracle.truffle.api.strings.TStringGuards.isBestEffort;
import static com.oracle.truffle.api.strings.TStringGuards.isBrokenFixedWidth;
import static com.oracle.truffle.api.strings.TStringGuards.isBrokenMultiByte;
import static com.oracle.truffle.api.strings.TStringGuards.isBuiltin;
import static com.oracle.truffle.api.strings.TStringGuards.isBytes;
import static com.oracle.truffle.api.strings.TStringGuards.isFixedWidth;
import static com.oracle.truffle.api.strings.TStringGuards.isReturnNegative;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16FE;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16Or32;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF32;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF32FE;
import static com.oracle.truffle.api.strings.TStringGuards.isUnsupportedEncoding;
import static com.oracle.truffle.api.strings.TStringGuards.isUpTo16Bit;
import static com.oracle.truffle.api.strings.TStringGuards.isValid;
import static com.oracle.truffle.api.strings.TStringGuards.isValidFixedWidth;
import static com.oracle.truffle.api.strings.TStringOps.readS0;
import static com.oracle.truffle.api.strings.TStringOps.readS1;
import static com.oracle.truffle.api.strings.TStringOps.writeToByteArrayS1;
import static com.oracle.truffle.api.strings.TStringOps.writeToByteArrayS2;
import static com.oracle.truffle.api.strings.TStringUnsafe.byteArrayBaseOffset;

import java.lang.ref.Reference;
import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateCached;
import com.oracle.truffle.api.dsl.GenerateInline;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.AbstractTruffleString.NativePointer;
import com.oracle.truffle.api.strings.TStringOpsNodes.RawIndexOfStringNode;
import com.oracle.truffle.api.strings.TStringOpsNodes.RawLastIndexOfStringNode;
import com.oracle.truffle.api.strings.TruffleString.CompactionLevel;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleString.ErrorHandling;

final class TStringInternalNodes {

    /**
     * Gets a string's code range with enough precision to decide whether the code range makes the
     * string fixed-width.
     */
    static int getCodeRangeForIndexCalculation(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, Encoding encoding, InlinedConditionProfile impreciseProfile) {
        int codeRange = a.codeRange();
        if (impreciseProfile.profile(node, !TSCodeRange.isPrecise(codeRange) && !isFixedWidth(codeRange))) {
            return StringAttributes.getCodeRange(updateAttributes(node, a, arrayA, offsetA, encoding, codeRange));
        }
        return codeRange;
    }

    static int getPreciseCodeRange(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, Encoding encoding, InlinedConditionProfile impreciseProfile) {
        int codeRange = a.codeRange();
        if (impreciseProfile.profile(node, !TSCodeRange.isPrecise(codeRange))) {
            return StringAttributes.getCodeRange(updateAttributes(node, a, arrayA, offsetA, encoding, codeRange));
        }
        return codeRange;
    }

    /**
     * Gets a string's code range with enough precision to decide whether the string is valid.
     */
    abstract static class GetValidOrBrokenCodeRangeNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Encoding encoding);

        @Specialization
        static int get(Node node, AbstractTruffleString a, Encoding encoding,
                        @Cached InlinedConditionProfile impreciseProfile,
                        @Cached InlinedConditionProfile managedProfileA,
                        @Cached InlinedConditionProfile nativeProfileA) {
            int codeRange = a.codeRange();
            if (impreciseProfile.profile(node, !TSCodeRange.isPrecise(codeRange) && TSCodeRange.isBroken(codeRange))) {
                Object dataA = a.data();
                try {
                    final byte[] arrayA;
                    final long addOffsetA;
                    if (managedProfileA.profile(node, dataA instanceof byte[])) {
                        arrayA = (byte[]) dataA;
                        addOffsetA = byteArrayBaseOffset();
                    } else if (nativeProfileA.profile(node, dataA instanceof NativePointer)) {
                        arrayA = null;
                        addOffsetA = NativePointer.unwrap(dataA);
                    } else {
                        arrayA = a.materializeLazy(node, dataA);
                        addOffsetA = byteArrayBaseOffset();
                    }
                    final long offsetA = a.offset() + addOffsetA;
                    return StringAttributes.getCodeRange(updateAttributes(node, a, arrayA, offsetA, encoding, codeRange));
                } finally {
                    Reference.reachabilityFence(dataA);
                }
            }
            return codeRange;
        }
    }

    abstract static class GetPreciseCodeRangeWithMaterializationNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Encoding encoding);

        @Specialization
        static int get(Node node, AbstractTruffleString a, Encoding encoding,
                        @Cached InlinedConditionProfile impreciseProfile,
                        @Cached InlinedConditionProfile managedProfileA,
                        @Cached InlinedConditionProfile nativeProfileA) {
            int codeRange = a.codeRange();
            if (impreciseProfile.profile(node, !TSCodeRange.isPrecise(codeRange))) {
                Object dataA = a.data();
                try {
                    final byte[] arrayA;
                    final long addOffsetA;
                    if (managedProfileA.profile(node, dataA instanceof byte[])) {
                        arrayA = (byte[]) dataA;
                        addOffsetA = byteArrayBaseOffset();
                    } else if (nativeProfileA.profile(node, dataA instanceof NativePointer)) {
                        arrayA = null;
                        addOffsetA = NativePointer.unwrap(dataA);
                    } else {
                        arrayA = a.materializeLazy(node, dataA);
                        addOffsetA = byteArrayBaseOffset();
                    }
                    final long offsetA = a.offset() + addOffsetA;
                    return StringAttributes.getCodeRange(updateAttributes(node, a, arrayA, offsetA, encoding, codeRange));
                } finally {
                    Reference.reachabilityFence(dataA);
                }
            }
            return codeRange;
        }
    }

    static int getCodePointLength(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, Encoding encoding, InlinedConditionProfile calcCodePointLengthProfile) {
        int codePointLength = a.codePointLength();
        if (calcCodePointLengthProfile.profile(node, codePointLength < 0)) {
            return StringAttributes.getCodePointLength(updateAttributes(node, a, arrayA, offsetA, encoding, a.codeRange()));
        }
        return codePointLength;
    }

    abstract static class GetCodePointLengthWithMaterializationNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Encoding encoding);

        @Specialization
        static int get(Node node, AbstractTruffleString a, Encoding encoding,
                        @Cached InlinedConditionProfile cacheMissProfile,
                        @Cached InlinedConditionProfile managedProfileA,
                        @Cached InlinedConditionProfile nativeProfileA) {
            int codePointLength = a.codePointLength();
            if (cacheMissProfile.profile(node, codePointLength < 0)) {
                Object dataA = a.data();
                try {
                    final byte[] arrayA;
                    final long addOffsetA;
                    if (managedProfileA.profile(node, dataA instanceof byte[])) {
                        arrayA = (byte[]) dataA;
                        addOffsetA = byteArrayBaseOffset();
                    } else if (nativeProfileA.profile(node, dataA instanceof NativePointer)) {
                        arrayA = null;
                        addOffsetA = NativePointer.unwrap(dataA);
                    } else {
                        arrayA = a.materializeLazy(node, dataA);
                        addOffsetA = byteArrayBaseOffset();
                    }
                    final long offsetA = a.offset() + addOffsetA;
                    return StringAttributes.getCodePointLength(updateAttributes(node, a, arrayA, offsetA, encoding, a.codeRange()));
                } finally {
                    Reference.reachabilityFence(dataA);
                }
            }
            return codePointLength;
        }
    }

    @TruffleBoundary
    static long updateAttributes(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, Encoding encoding, int impreciseCodeRange) {
        assert !TSCodeRange.isPrecise(impreciseCodeRange);
        long attrs = CalcStringAttributesNode.getUncached().execute(node, a, arrayA, offsetA, a.length(), a.stride(), encoding, 0, impreciseCodeRange);
        a.updateAttributes(StringAttributes.getCodePointLength(attrs), StringAttributes.getCodeRange(attrs));
        return attrs;
    }

    static final class FromBufferWithStringCompaction {

        static TruffleString fromBufferWithStringCompaction(Node node, Object dataA, int strOffsetA, int byteLength, Encoding encoding, boolean copy,
                        InlinedConditionProfile utf8BrokenProfile,
                        InlinedConditionProfile utf16CompactProfile,
                        InlinedConditionProfile utf32Compact0Profile,
                        InlinedConditionProfile utf32Compact1Profile,
                        InlinedConditionProfile singleByteProfile) {
            assert dataA instanceof byte[] || dataA instanceof NativePointer;
            try {
                final byte[] arrayA;
                final long offsetA;
                if (dataA instanceof byte[] byteArray) {
                    arrayA = byteArray;
                    offsetA = byteArrayBaseOffset() + strOffsetA;
                } else {
                    arrayA = null;
                    offsetA = NativePointer.unwrap(dataA) + strOffsetA;
                }
                if (encoding == Encoding.UTF_8) {
                    return utf8(node, dataA, strOffsetA, byteLength, copy, arrayA, offsetA, utf8BrokenProfile, singleByteProfile);
                } else if (encoding == Encoding.UTF_16) {
                    return utf16(node, dataA, strOffsetA, byteLength, copy, arrayA, offsetA, utf16CompactProfile, singleByteProfile);
                } else if (encoding == Encoding.UTF_32) {
                    return utf32(node, dataA, strOffsetA, byteLength, copy, arrayA, offsetA, utf32Compact0Profile, utf32Compact1Profile, singleByteProfile);
                } else {
                    return unlikelyCases(node, dataA, strOffsetA, byteLength, encoding, copy, arrayA, offsetA);
                }
            } finally {
                Reference.reachabilityFence(dataA);
            }
        }

        private static TruffleString utf8(Node node, Object dataA, int strOffsetA, int byteLength, boolean copy, byte[] arrayA, long offsetA,
                        InlinedConditionProfile utf8BrokenProfile,
                        InlinedConditionProfile singleByteProfile) {
            if (singleByteProfile.profile(node, byteLength == 1)) {
                return TStringConstants.getSingleByte(Encoding.UTF_8, TStringOps.readS0(arrayA, offsetA, 1, 0));
            }
            final long attrs = TStringOps.calcStringAttributesUTF8(node, arrayA, offsetA, byteLength, false, false, utf8BrokenProfile);
            final int codeRange = StringAttributes.getCodeRange(attrs);
            final int codePointLength = StringAttributes.getCodePointLength(attrs);
            final int offset;
            final byte[] array;
            if (copy) {
                offset = 0;
                array = TStringOps.arraycopyOfWithStride(node, arrayA, offsetA, byteLength, 0, byteLength, 0);
            } else {
                offset = strOffsetA;
                array = arrayA;
            }
            Object data = array == null ? dataA : array;
            return TruffleString.createFromArray(data, offset, byteLength, 0, Encoding.UTF_8, codePointLength, codeRange, true);
        }

        private static TruffleString utf16(Node node, Object dataA, int strOffsetA, int byteLength, boolean copy, byte[] arrayA, long offsetA,
                        InlinedConditionProfile utf16CompactProfile,
                        InlinedConditionProfile singleByteProfile) {
            checkByteLengthUTF16(byteLength);
            final int length = byteLength >> 1;
            long attrs = TStringOps.calcStringAttributesUTF16(node, arrayA, offsetA, length, false);
            final int codePointLength = StringAttributes.getCodePointLength(attrs);
            final int codeRange = StringAttributes.getCodeRange(attrs);
            final int stride = Stride.fromCodeRangeUTF16(codeRange);
            final byte[] array;
            final int offset;
            if (copy || stride == 0) {
                if (singleByteProfile.profile(node, length == 1 && stride == 0)) {
                    return TStringConstants.getSingleByte(Encoding.UTF_16, readS1(arrayA, offsetA, 1, 0));
                }
                offset = 0;
                array = new byte[length << stride];
                if (utf16CompactProfile.profile(node, stride == 0)) {
                    TStringOps.arraycopyWithStride(node, arrayA, offsetA, 1, 0, array, byteArrayBaseOffset(), 0, 0, length);
                } else {
                    TStringOps.arraycopyWithStride(node, arrayA, offsetA, 1, 0, array, byteArrayBaseOffset(), 1, 0, length);
                }
            } else {
                offset = strOffsetA;
                array = arrayA;
            }
            Object data = array == null ? dataA : array;
            return TruffleString.createFromArray(data, offset, length, stride, Encoding.UTF_16, codePointLength, codeRange, true);
        }

        private static TruffleString utf32(Node node, Object dataA, int strOffsetA, int byteLength, boolean copy, byte[] arrayA, long offsetA,
                        InlinedConditionProfile utf32Compact0Profile,
                        InlinedConditionProfile utf32Compact1Profile,
                        InlinedConditionProfile singleByteProfile) {
            checkByteLengthUTF32(byteLength);
            final int length = byteLength >> 2;
            final int codeRange = TStringOps.calcStringAttributesUTF32(node, arrayA, offsetA, length);
            final int stride = Stride.fromCodeRangeUTF32(codeRange);
            final int offset;
            final byte[] array;
            if (copy || stride < 2) {
                if (singleByteProfile.profile(node, length == 1 && stride == 0)) {
                    return TStringConstants.getSingleByte(Encoding.UTF_32, TStringOps.readS2(arrayA, offsetA, 1, 0));
                }
                offset = 0;
                array = new byte[length << stride];
                if (utf32Compact0Profile.profile(node, stride == 0)) {
                    TStringOps.arraycopyWithStride(node, arrayA, offsetA, 2, 0, array, byteArrayBaseOffset(), 0, 0, length);
                } else if (utf32Compact1Profile.profile(node, stride == 1)) {
                    TStringOps.arraycopyWithStride(node, arrayA, offsetA, 2, 0, array, byteArrayBaseOffset(), 1, 0, length);
                } else {
                    TStringOps.arraycopyWithStride(node, arrayA, offsetA, 2, 0, array, byteArrayBaseOffset(), 2, 0, length);
                }
            } else {
                offset = strOffsetA;
                array = arrayA;
            }
            Object data = array == null ? dataA : array;
            return TruffleString.createFromArray(data, offset, length, stride, Encoding.UTF_32, length, codeRange, true);
        }

        @InliningCutoff
        private static TruffleString unlikelyCases(Node node, Object dataA, int strOffsetA, int byteLength, Encoding encoding, boolean copy, byte[] arrayA,
                        long offsetA) {
            final int codePointLength;
            final byte[] array;
            final int stride;
            final int codeRange;
            final int offset;
            final int length;
            stride = encoding.naturalStride;
            length = byteLength >> stride;
            final long attrs;
            attrs = calcAttrsUnlikelyCases(node, arrayA, offsetA, byteLength, encoding);
            codeRange = StringAttributes.getCodeRange(attrs);
            codePointLength = StringAttributes.getCodePointLength(attrs);
            if (copy) {
                offset = 0;
                array = TStringOps.arraycopyOfWithStride(node, arrayA, offsetA, byteLength, 0, byteLength, 0);
            } else {
                offset = strOffsetA;
                array = arrayA;
            }
            Object data = array == null ? dataA : array;
            return TruffleString.createFromArray(data, offset, length, stride, encoding, codePointLength, codeRange, true);
        }

        @InliningCutoff
        private static long calcAttrsUnlikelyCases(Node node, byte[] arrayA, long offsetA, int byteLength, Encoding encoding) {
            if (isAsciiBytesOrLatin1(encoding)) {
                int cr = TStringOps.calcStringAttributesLatin1(node, arrayA, offsetA, byteLength);
                return StringAttributes.create(byteLength, is8Bit(cr) ? TSCodeRange.asciiLatinBytesNonAsciiCodeRange(encoding) : cr);
            } else if (isUTF16FE(encoding)) {
                checkByteLengthUTF16(byteLength);
                return TStringOps.calcStringAttributesUTF16FE(node, arrayA, offsetA, byteLength >> 1);
            } else if (isUTF32FE(encoding)) {
                checkByteLengthUTF32(byteLength);
                return StringAttributes.create(byteLength >> 2, TStringOps.calcStringAttributesUTF32FE(node, arrayA, offsetA, byteLength >> 2));
            } else {
                return JCodings.getInstance().calcStringAttributes(node, null, arrayA, offsetA, byteLength, encoding, 0);
            }
        }
    }

    abstract static class FromBufferWithStringCompactionKnownAttributesNode extends AbstractInternalNode {

        final TruffleString execute(Node node, AbstractTruffleString a, Encoding encoding) {
            return execute(node, a, true, encoding);
        }

        abstract TruffleString execute(Node node, AbstractTruffleString a, boolean isCacheHead, Encoding encoding);

        @Specialization
        static TruffleString fromBufferWithStringCompaction(Node node, AbstractTruffleString a, boolean isCacheHead, Encoding encoding,
                        @Cached InlinedConditionProfile managedProfileA,
                        @Cached InlinedConditionProfile nativeProfileA,
                        @Cached InlinedConditionProfile calcCodePointLengthProfile,
                        @Cached InlinedConditionProfile impreciseProfile,
                        @Cached InlinedConditionProfile utf16CompactProfile,
                        @Cached InlinedConditionProfile utf32Compact0Profile,
                        @Cached InlinedConditionProfile utf32Compact1Profile) {
            Object dataA = a.data();
            assert dataA instanceof byte[] || dataA instanceof NativePointer;
            try {
                final byte[] arrayA;
                final long addOffsetA;
                if (managedProfileA.profile(node, dataA instanceof byte[])) {
                    arrayA = (byte[]) dataA;
                    addOffsetA = byteArrayBaseOffset();
                } else if (nativeProfileA.profile(node, dataA instanceof NativePointer)) {
                    arrayA = null;
                    addOffsetA = NativePointer.unwrap(dataA);
                } else {
                    arrayA = a.materializeLazy(node, dataA);
                    addOffsetA = byteArrayBaseOffset();
                }
                final long offsetA = a.offset() + addOffsetA;

                final int codeRange = getPreciseCodeRange(node, a, arrayA, offsetA, encoding, impreciseProfile);
                a.looseCheckEncoding(encoding, codeRange);
                final int length = a.length();
                if (length == 0) {
                    return encoding.getEmpty();
                }
                final int strideA = a.stride();
                final int offset = byteArrayBaseOffset();
                final int stride;
                final byte[] array;
                if (encoding == Encoding.UTF_16) {
                    stride = Stride.fromCodeRangeUTF16(codeRange);
                    array = new byte[length << stride];
                    if (utf16CompactProfile.profile(node, strideA == 1 && stride == 0)) {
                        TStringOps.arraycopyWithStride(node, arrayA, offsetA, 1, 0, array, offset, 0, 0, length);
                    } else {
                        assert stride == strideA;
                        TStringOps.arraycopyWithStride(node, arrayA, offsetA, 0, 0, array, offset, 0, 0, length << stride);
                    }
                } else if (encoding == Encoding.UTF_32) {
                    stride = Stride.fromCodeRangeUTF32(codeRange);
                    array = new byte[length << stride];
                    if (utf32Compact0Profile.profile(node, strideA == 2 && stride == 0)) {
                        TStringOps.arraycopyWithStride(node, arrayA, offsetA, 2, 0, array, offset, 0, 0, length);
                    } else if (utf32Compact1Profile.profile(node, strideA == 2 && stride == 1)) {
                        TStringOps.arraycopyWithStride(node, arrayA, offsetA, 2, 0, array, offset, 1, 0, length);
                    } else {
                        assert stride == strideA;
                        TStringOps.arraycopyWithStride(node, arrayA, offsetA, 0, 0, array, offset, 0, 0, length << stride);
                    }
                } else {
                    stride = encoding.naturalStride;
                    assert a.stride() == stride;
                    array = TStringOps.arraycopyOfWithStride(node, arrayA, offsetA, length << stride, 0, length << stride, 0);
                }
                int codePointLength = getCodePointLength(node, a, arrayA, offsetA, encoding, calcCodePointLengthProfile);
                return TruffleString.createFromByteArray(array, 0, length, stride, encoding, codePointLength, codeRange, isCacheHead);
            } finally {
                Reference.reachabilityFence(dataA);
            }
        }

        static FromBufferWithStringCompactionKnownAttributesNode getUncached() {
            return TStringInternalNodesFactory.FromBufferWithStringCompactionKnownAttributesNodeGen.getUncached();
        }
    }

    @GenerateInline(false)
    @GenerateCached(false)
    abstract static class FromNativePointerEmbedderNode extends AbstractInternalNode {

        abstract TruffleString execute(long rawPointer, int byteOffset, int byteLength, Encoding encoding, boolean copy);

        @Specialization
        final TruffleString fromNativePointerEmbedder(long rawPointer, int byteOffset, int byteLength, Encoding enc, boolean copy,
                        @Cached InlinedConditionProfile utf8BrokenProfile,
                        @Cached InlinedConditionProfile utf16CompactProfile,
                        @Cached InlinedConditionProfile utf32Compact0Profile,
                        @Cached InlinedConditionProfile utf32Compact1Profile,
                        @Cached InlinedConditionProfile singleByteProfile) {
            NativePointer pointer = new NativePointer(TruffleString.ETERNAL_POINTER, rawPointer);
            if (copy) {
                return FromBufferWithStringCompaction.fromBufferWithStringCompaction(this, pointer, byteOffset, byteLength, enc, true,
                                utf8BrokenProfile,
                                utf16CompactProfile,
                                utf32Compact0Profile,
                                utf32Compact1Profile,
                                singleByteProfile);
            }
            return fromNativePointer(this, pointer, byteOffset, byteLength, enc, utf8BrokenProfile);
        }

    }

    static TruffleString fromNativePointer(Node node, NativePointer pointer, int byteOffset, int byteLength, Encoding encoding, InlinedConditionProfile utf8BrokenProfile) {
        final long offsetA = pointer.pointer + byteOffset;
        final int length;
        final int stride;
        final int codePointLength;
        final int codeRange;
        if (encoding == Encoding.UTF_16) {
            checkByteLengthUTF16(byteLength);
            length = byteLength >> 1;
            long attrs = TStringOps.calcStringAttributesUTF16(node, null, offsetA, length, false);
            codePointLength = StringAttributes.getCodePointLength(attrs);
            codeRange = StringAttributes.getCodeRange(attrs);
            stride = 1;
        } else if (encoding == Encoding.UTF_32) {
            checkByteLengthUTF32(byteLength);
            length = byteLength >> 2;
            codeRange = TStringOps.calcStringAttributesUTF32(node, null, offsetA, length);
            codePointLength = length;
            stride = 2;
        } else {
            stride = encoding.naturalStride;
            length = byteLength >> stride;
            final long attrs;
            if (encoding == Encoding.UTF_8) {
                attrs = TStringOps.calcStringAttributesUTF8(node, null, offsetA, length, false, false, utf8BrokenProfile);
            } else {
                attrs = FromBufferWithStringCompaction.calcAttrsUnlikelyCases(node, null, offsetA, byteLength, encoding);
            }
            codeRange = StringAttributes.getCodeRange(attrs);
            codePointLength = StringAttributes.getCodePointLength(attrs);
        }
        return TruffleString.createFromArray(pointer, byteOffset, length, stride, encoding, codePointLength, codeRange, true);
    }

    static final class ByteLengthOfCodePoint {

        static int utf8(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int index, ErrorHandling errorHandling,
                        InlinedConditionProfile validProfile) {
            assert strideA == 0;
            int firstByte = readS0(arrayA, offsetA, lengthA, index);
            if (firstByte <= 0x7f) {
                return 1;
            } else if (validProfile.profile(node, isValid(codeRangeA))) {
                return Encodings.utf8CodePointLength(firstByte);
            } else {
                return Encodings.utf8GetCodePointLength(arrayA, offsetA, lengthA, index, errorHandling.errorHandler);
            }
        }

        static int utf16(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int index, ErrorHandling errorHandling,
                        InlinedConditionProfile fixedProfile,
                        InlinedConditionProfile validProfile) {
            if (fixedProfile.profile(node, isFixedWidth(codeRangeA))) {
                return 2;
            } else {
                assert strideA == 1;
                if (validProfile.profile(node, isValid(codeRangeA))) {
                    return Encodings.isUTF16HighSurrogate(readS1(arrayA, offsetA, lengthA, index)) ? 4 : 2;
                } else {
                    assert isBroken(codeRangeA);
                    return Encodings.utf16BrokenGetCodePointByteLength(arrayA, offsetA, lengthA, index, errorHandling);
                }
            }
        }

        static int utf32(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int index, ErrorHandling errorHandling,
                        InlinedConditionProfile validProfile) {
            if (validProfile.profile(node, !(isBestEffort(errorHandling) || isUpToValid(codeRangeA)))) {
                assert strideA == 2;
                int c = TStringOps.readS2(arrayA, offsetA, lengthA, index);
                if (errorHandling == ErrorHandling.RETURN_NEGATIVE && !Encodings.isValidUnicodeCodepoint(c)) {
                    return -1;
                }
            }
            return 4;
        }

        static int unlikelyCases(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int index,
                        ErrorHandling errorHandling,
                        InlinedConditionProfile fixedProfile,
                        InlinedConditionProfile validProfile) {
            boolean isBroken = isBroken(codeRangeA);
            if (fixedProfile.profile(node, isFixedWidth(codeRangeA) && (isUpToValid(codeRangeA) || isBestEffort(errorHandling)))) {
                return 1 << encoding.naturalStride;
            } else if (encoding == Encoding.US_ASCII) {
                assert strideA == 0;
                assert isBroken && isReturnNegative(errorHandling);
                return readS0(arrayA, offsetA, lengthA, index) < 0x80 ? 1 : -1;
            } else if (encoding == Encoding.UTF_16_FOREIGN_ENDIAN) {
                assert strideA == 1;
                if (validProfile.profile(node, isBroken)) {
                    return Encodings.utf16FEBrokenGetCodePointByteLength(arrayA, offsetA, lengthA, index, errorHandling);
                } else {
                    return Encodings.isUTF16HighSurrogate(Character.reverseBytes(readS1(arrayA, offsetA, lengthA, index))) ? 4 : 2;
                }
            } else if (encoding == Encoding.UTF_32_FOREIGN_ENDIAN) {
                assert strideA == 2;
                if (validProfile.profile(node, isBroken && isReturnNegative(errorHandling))) {
                    return CodePointAtRaw.utf32FE(arrayA, offsetA, lengthA, strideA, index, ErrorHandling.RETURN_NEGATIVE) < 0 ? -1 : 4;
                } else {
                    return 4;
                }
            } else {
                assert isUnsupportedEncoding(encoding);
                return byteLengthOfCodePointUnsupported(a, arrayA, lengthA, encoding, index, errorHandling);
            }
        }

        @TruffleBoundary
        private static int byteLengthOfCodePointUnsupported(AbstractTruffleString a, byte[] arrayA, int lengthA, Encoding encoding, int index, ErrorHandling errorHandling) {
            JCodings jcodings = JCodings.getInstance();
            int cpLength = jcodings.getCodePointLength(encoding, JCodings.asByteArray(a, arrayA), a.byteArrayOffset() + index, a.byteArrayOffset() + lengthA);
            int regionLength = lengthA - index;
            if (errorHandling == ErrorHandling.BEST_EFFORT) {
                if (cpLength > 0 && cpLength <= regionLength) {
                    return cpLength;
                } else {
                    return Math.min(jcodings.minLength(encoding), regionLength);
                }
            } else {
                assert errorHandling == ErrorHandling.RETURN_NEGATIVE;
                if (cpLength <= regionLength) {
                    return cpLength;
                } else {
                    return -1 - (cpLength - regionLength);
                }
            }
        }
    }

    static final class CodePointIndexToRaw {

        static int codePointIndexToRaw(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int extraOffsetRaw, int index,
                        boolean isLength,
                        InlinedConditionProfile fixedProfile,
                        InlinedConditionProfile validProfile) {
            if (encoding == Encoding.UTF_32 || encoding == Encoding.UTF_32_FOREIGN_ENDIAN || index == 0 || fixedProfile.profile(node, TSCodeRange.isFixedWidth(codeRangeA))) {
                return index;
            } else if (encoding == Encoding.UTF_8) {
                return utf8(node, arrayA, offsetA, lengthA, strideA, codeRangeA, extraOffsetRaw, index, isLength, validProfile);
            } else if (encoding == Encoding.UTF_16) {
                return utf16(node, arrayA, offsetA, lengthA, strideA, codeRangeA, extraOffsetRaw, index, isLength, validProfile);
            } else {
                return unlikelyCases(node, a, arrayA, offsetA, lengthA, strideA, codeRangeA, encoding, extraOffsetRaw, index, isLength, validProfile);
            }
        }

        static int utf8(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int extraOffsetRaw, int index, boolean isLength,
                        InlinedConditionProfile validProfile) {
            if (validProfile.profile(node, isValid(codeRangeA))) {
                assert strideA == 0;
                long regionOffset = offsetA + extraOffsetRaw;
                int regionLength = lengthA - extraOffsetRaw;
                int byteIndex = TStringOps.codePointIndexToByteIndexUTF8Valid(node, arrayA, regionOffset, regionLength, index);
                if (byteIndex < 0 || !isLength && byteIndex == regionLength) {
                    throw InternalErrors.indexOutOfBounds(regionLength, byteIndex);
                }
                return byteIndex;
            } else {
                return utf8Broken(node, arrayA, offsetA, lengthA, strideA, extraOffsetRaw, index, isLength);
            }
        }

        static int utf16(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int extraOffsetRaw, int index, boolean isLength,
                        InlinedConditionProfile validProfile) {
            if (validProfile.profile(node, isValid(codeRangeA))) {
                assert strideA == 1;
                long regionOffset = offsetA + ((long) extraOffsetRaw << 1);
                int regionLength = lengthA - extraOffsetRaw;
                int result = TStringOps.codePointIndexToByteIndexUTF16Valid(node, arrayA, regionOffset, regionLength, index);
                if (result < 0 || !isLength && result == regionLength) {
                    throw InternalErrors.indexOutOfBounds(regionLength, result);
                }
                return result;
            } else {
                return utf16Broken(node, arrayA, offsetA, lengthA, strideA, extraOffsetRaw, index, isLength);
            }
        }

        static int unlikelyCases(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int extraOffsetRaw, int index,
                        boolean isLength,
                        InlinedConditionProfile validProfile) {
            if (encoding == Encoding.UTF_16_FOREIGN_ENDIAN) {
                if (validProfile.profile(node, isValid(codeRangeA))) {
                    assert strideA == 1;
                    long regionOffset = offsetA + ((long) extraOffsetRaw << 1);
                    int regionLength = lengthA - extraOffsetRaw;
                    int result = TStringOps.codePointIndexToByteIndexUTF16FEValid(node, arrayA, regionOffset, regionLength, index);
                    if (result < 0 || !isLength && result == regionLength) {
                        throw InternalErrors.indexOutOfBounds(regionLength, result);
                    }
                    return result;
                } else {
                    return utf16FEBroken(node, arrayA, offsetA, lengthA, strideA, extraOffsetRaw, index, isLength);
                }
            } else {
                assert isUnsupportedEncoding(encoding);
                return unsupported(node, a, arrayA, encoding, extraOffsetRaw, index, isLength);
            }
        }

        @InliningCutoff
        private static int utf8Broken(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int extraOffsetRaw, int index, boolean isLength) {
            assert strideA == 0;
            int cpi = 0;
            for (int i = extraOffsetRaw; i < lengthA; i += Encodings.utf8GetCodePointLength(arrayA, offsetA, lengthA, i, DecodingErrorHandler.DEFAULT)) {
                if (cpi == index) {
                    return i - extraOffsetRaw;
                }
                cpi++;
                TStringConstants.truffleSafePointPoll(node, cpi);
            }
            return atEnd(lengthA, extraOffsetRaw, index, isLength, cpi);
        }

        @InliningCutoff
        private static int utf16Broken(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int extraOffsetRaw, int index, boolean isLength) {
            assert strideA == 1;
            int cpi = 0;
            for (int i = extraOffsetRaw; i < lengthA; i++) {
                if (!(i > extraOffsetRaw && Encodings.isUTF16LowSurrogate(readS1(arrayA, offsetA, lengthA, i)) &&
                                Encodings.isUTF16HighSurrogate(readS1(arrayA, offsetA, lengthA, i - 1)))) {
                    if (cpi == index) {
                        return i - extraOffsetRaw;
                    }
                    cpi++;
                }
                TStringConstants.truffleSafePointPoll(node, i + 1);
            }
            return atEnd(lengthA, extraOffsetRaw, index, isLength, cpi);
        }

        @InliningCutoff
        private static int utf16FEBroken(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int extraOffsetRaw, int index, boolean isLength) {
            assert strideA == 1;
            int cpi = 0;
            for (int i = extraOffsetRaw; i < lengthA; i++) {
                if (!(i > extraOffsetRaw && Encodings.isUTF16FELowSurrogate(readS1(arrayA, offsetA, lengthA, i)) &&
                                Encodings.isUTF16FEHighSurrogate(readS1(arrayA, offsetA, lengthA, i - 1)))) {
                    if (cpi == index) {
                        return (i - extraOffsetRaw);
                    }
                    cpi++;
                }
                TStringConstants.truffleSafePointPoll(node, i + 1);
            }
            return atEnd(lengthA, extraOffsetRaw, index, isLength, cpi);
        }

        @TruffleBoundary
        private static int unsupported(Node node, AbstractTruffleString a, byte[] arrayA, Encoding encoding, int extraOffsetRaw, int index, boolean isLength) {
            return JCodings.getInstance().codePointIndexToRaw(node, a, JCodings.asByteArray(a, arrayA), extraOffsetRaw, index, isLength, encoding);
        }

        static int atEnd(int lengthA, int extraOffsetRaw, int index, boolean isLength, int cpi) {
            int regionLength = lengthA - extraOffsetRaw;
            if (isLength && cpi == index) {
                return regionLength;
            }
            throw InternalErrors.indexOutOfBounds(regionLength, index);
        }
    }

    static final class ReadByte {

        static int utf16(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int i,
                        InlinedConditionProfile stride0Profile) {
            TruffleString.boundsCheckByteIndexUTF16(lengthA, i);
            final int index;
            if (stride0Profile.profile(node, strideA == 0)) {
                // simplified from:
                // (TStringGuards.bigEndian() ? (i & 1) == 0 : (i & 1) != 0)
                if ((TStringGuards.bigEndian()) == ((i & 1) == 0)) {
                    return 0;
                } else {
                    index = i >> 1;
                }
            } else {
                assert strideA == 1;
                index = i;
            }
            return readS0(arrayA, offsetA, lengthA << strideA, index);
        }

        static int utf32(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int i,
                        InlinedConditionProfile stride0Profile,
                        InlinedConditionProfile stride1Profile) {
            TruffleString.boundsCheckByteIndexUTF32(lengthA, i);
            final int index;
            if (stride0Profile.profile(node, strideA == 0)) {
                if ((i & 3) != (TStringGuards.bigEndian() ? 3 : 0)) {
                    return 0;
                } else {
                    index = i >> 2;
                }
            } else if (stride1Profile.profile(node, strideA == 1)) {
                // simplified from:
                // (TStringGuards.bigEndian() ? (i & 2) == 0 : (i & 2) != 0)
                if ((TStringGuards.bigEndian()) == ((i & 2) == 0)) {
                    return 0;
                } else {
                    if (TStringGuards.bigEndian()) {
                        index = (i >> 1) | (i & 1);
                    } else {
                        index = ((i >> 1) & ~1) | (i & 1);
                    }
                }
            } else {
                assert strideA == 2;
                index = i;
            }
            return readS0(arrayA, offsetA, lengthA << strideA, index);
        }
    }

    static final class CodePointAt {

        static int utf8(Node node, byte[] arrayA, long offsetA, int lengthA, int codeRangeA, int i,
                        ErrorHandling errorHandling,
                        InlinedConditionProfile fixedWidthProfile,
                        InlinedConditionProfile validProfile) {
            if (fixedWidthProfile.profile(node, is7Bit(codeRangeA))) {
                return readS0(arrayA, offsetA, lengthA, i);
            } else {
                if (validProfile.profile(node, isValid(codeRangeA))) {
                    int byteIndex = TStringOps.codePointIndexToByteIndexUTF8Valid(node, arrayA, offsetA, lengthA, i);
                    assert byteIndex >= 0 && byteIndex < lengthA;
                    return Encodings.utf8DecodeValid(arrayA, offsetA, lengthA, byteIndex);
                } else {
                    assert TStringGuards.isBroken(codeRangeA);
                    int byteIndex = Encodings.utf8CodePointToByteIndex(node, arrayA, offsetA, lengthA, i);
                    return Encodings.utf8DecodeBroken(arrayA, offsetA, lengthA, byteIndex, errorHandling);
                }
            }
        }

        static int utf16(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int i,
                        ErrorHandling errorHandling,
                        InlinedConditionProfile fixedWidthProfile,
                        InlinedConditionProfile stride0Profile,
                        InlinedConditionProfile validProfile) {
            if (fixedWidthProfile.profile(node, isFixedWidth(codeRangeA))) {
                if (stride0Profile.profile(node, strideA == 0)) {
                    return readS0(arrayA, offsetA, lengthA, i);
                } else {
                    assert strideA == 1;
                    return readS1(arrayA, offsetA, lengthA, i);
                }
            } else if (validProfile.profile(node, isValid(codeRangeA))) {
                assert strideA == 1;
                int charIndex = TStringOps.codePointIndexToByteIndexUTF16Valid(node, arrayA, offsetA, lengthA, i);
                assert charIndex >= 0 && charIndex < lengthA;
                return Encodings.utf16DecodeValid(arrayA, offsetA, lengthA, charIndex);
            } else {
                assert strideA == 1;
                assert TStringGuards.isBroken(codeRangeA);
                int charIndex = Encodings.utf16BrokenCodePointToCharIndex(node, arrayA, offsetA, lengthA, i);
                return Encodings.utf16DecodeBroken(arrayA, offsetA, lengthA, charIndex, errorHandling);
            }
        }

        static int unlikelyCases(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int i, ErrorHandling errorHandling,
                        InlinedConditionProfile fixedProfile,
                        InlinedConditionProfile validProfile) {
            if (encoding == Encoding.UTF_16_FOREIGN_ENDIAN) {
                assert strideA == 1;
                return utf16FE(node, arrayA, offsetA, lengthA, strideA, codeRangeA, i, errorHandling, fixedProfile, validProfile);
            } else if (encoding == Encoding.UTF_32_FOREIGN_ENDIAN) {
                assert strideA == 2;
                return CodePointAtRaw.utf32FE(arrayA, offsetA, lengthA, strideA, i, errorHandling);
            } else if (encoding == Encoding.BYTES || fixedProfile.profile(node, is7Or8Bit(codeRangeA))) {
                assert strideA == 0;
                return readS0(arrayA, offsetA, lengthA, i);
            } else if (encoding == Encoding.US_ASCII) {
                return CodePointAtRaw.asciiBroken(arrayA, offsetA, lengthA, strideA, codeRangeA, i, errorHandling);
            } else {
                assert isUnsupportedEncoding(encoding);
                assert strideA == 0;
                return unsupported(node, a, arrayA, encoding, i, errorHandling);
            }
        }

        private static int utf16FE(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int i, ErrorHandling errorHandling,
                        InlinedConditionProfile utf16FEFixedProfile,
                        InlinedConditionProfile utf16FEValidProfile) {
            assert strideA == 1;
            if (utf16FEFixedProfile.profile(node, isFixedWidth(codeRangeA))) {
                return Character.reverseBytes(readS1(arrayA, offsetA, lengthA, i));
            } else if (utf16FEValidProfile.profile(node, isValid(codeRangeA))) {
                int charIndex = TStringOps.codePointIndexToByteIndexUTF16FEValid(node, arrayA, offsetA, lengthA, i);
                return Encodings.utf16FEDecodeValid(arrayA, offsetA, lengthA, charIndex);
            } else {
                assert TStringGuards.isBroken(codeRangeA);
                return Encodings.utf16FEDecodeBroken(arrayA, offsetA, lengthA, Encodings.utf16FEBrokenCodePointToCharIndex(node, arrayA, offsetA, lengthA, i), errorHandling);
            }
        }

        @TruffleBoundary
        private static int unsupported(Node node, AbstractTruffleString a, byte[] arrayA, Encoding encoding, int i, ErrorHandling errorHandling) {
            JCodings jcodings = JCodings.getInstance();
            byte[] bytes = JCodings.asByteArray(a, arrayA);
            return jcodings.decode(a, bytes, jcodings.codePointIndexToRaw(node, a, bytes, 0, i, false, encoding), encoding, errorHandling);
        }
    }

    static final class CodePointAtRaw {

        static int utf8(Node node, byte[] arrayA, long offsetA, int lengthA, int codeRangeA, int i,
                        ErrorHandling errorHandling,
                        InlinedConditionProfile fixedProfile,
                        InlinedConditionProfile validProfile) {
            if (fixedProfile.profile(node, is7Bit(codeRangeA))) {
                return readS0(arrayA, offsetA, lengthA, i);
            } else if (validProfile.profile(node, isValid(codeRangeA))) {
                return Encodings.utf8DecodeValid(arrayA, offsetA, lengthA, i);
            } else {
                assert TStringGuards.isBroken(codeRangeA);
                return Encodings.utf8DecodeBroken(arrayA, offsetA, lengthA, i, errorHandling);
            }
        }

        static int utf16(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int i,
                        ErrorHandling errorHandling,
                        InlinedConditionProfile fixedProfile,
                        InlinedConditionProfile stride0Profile,
                        InlinedConditionProfile validProfile) {
            if (stride0Profile.profile(node, strideA == 0)) {
                return readS0(arrayA, offsetA, lengthA, i);
            } else if (fixedProfile.profile(node, isFixedWidth(codeRangeA))) {
                assert strideA == 1;
                return readS1(arrayA, offsetA, lengthA, i);
            } else if (validProfile.profile(node, isValid(codeRangeA))) {
                return Encodings.utf16DecodeValid(arrayA, offsetA, lengthA, i);
            } else {
                assert TStringGuards.isBroken(codeRangeA);
                return Encodings.utf16DecodeBroken(arrayA, offsetA, lengthA, i, errorHandling);
            }
        }

        static int utf32(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int i,
                        ErrorHandling errorHandling,
                        InlinedConditionProfile stride0Profile,
                        InlinedConditionProfile stride1Profile) {
            if (stride0Profile.profile(node, strideA == 0)) {
                return readS0(arrayA, offsetA, lengthA, i);
            } else if (stride1Profile.profile(node, strideA == 1)) {
                char c = readS1(arrayA, offsetA, lengthA, i);
                if (errorHandling == ErrorHandling.RETURN_NEGATIVE && isUTF16Surrogate(c)) {
                    return -1;
                }
                return c;
            } else {
                assert strideA == 2;
                int c = TStringOps.readS2(arrayA, offsetA, lengthA, i);
                if (errorHandling == ErrorHandling.RETURN_NEGATIVE && !Encodings.isValidUnicodeCodepoint(c)) {
                    return -1;
                }
                return c;
            }
        }

        static int unlikelyCases(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int i,
                        ErrorHandling errorHandling,
                        InlinedConditionProfile fixedProfile,
                        InlinedConditionProfile validProfile) {
            if (encoding == Encoding.UTF_16_FOREIGN_ENDIAN) {
                return utf16FE(node, arrayA, offsetA, lengthA, strideA, codeRangeA, i, errorHandling, fixedProfile, validProfile);
            } else if (encoding == Encoding.UTF_32_FOREIGN_ENDIAN) {
                assert strideA == 2;
                return utf32FE(arrayA, offsetA, lengthA, strideA, i, errorHandling);
            } else if (encoding == Encoding.BYTES || fixedProfile.profile(node, is7Or8Bit(codeRangeA))) {
                assert strideA == 0;
                return readS0(arrayA, offsetA, lengthA, i);
            } else if (encoding == Encoding.US_ASCII) {
                assert strideA == 0;
                return asciiBroken(arrayA, offsetA, lengthA, strideA, codeRangeA, i, errorHandling);
            } else {
                assert strideA == 0;
                assert isUnsupportedEncoding(encoding);
                return unsupported(a, arrayA, encoding, i, errorHandling);
            }
        }

        static int asciiBroken(byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int i, ErrorHandling errorHandling) {
            assert strideA == 0;
            int c = readS0(arrayA, offsetA, lengthA, i);
            if (errorHandling == ErrorHandling.RETURN_NEGATIVE && c > 0x7f) {
                return -1;
            }
            assert errorHandling == ErrorHandling.BEST_EFFORT || !TSCodeRange.isPrecise(codeRangeA);
            return c;
        }

        private static int utf16FE(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int i, ErrorHandling errorHandling,
                        InlinedConditionProfile fixedProfile,
                        InlinedConditionProfile validProfile) {
            assert strideA == 1;
            if (fixedProfile.profile(node, isFixedWidth(codeRangeA))) {
                return Character.reverseBytes(readS1(arrayA, offsetA, lengthA, i));
            } else if (validProfile.profile(node, isValid(codeRangeA))) {
                return Encodings.utf16FEDecodeValid(arrayA, offsetA, lengthA, i);
            } else {
                assert TStringGuards.isBroken(codeRangeA);
                return Encodings.utf16FEDecodeBroken(arrayA, offsetA, lengthA, i, errorHandling);
            }
        }

        static int utf32FE(byte[] arrayA, long offsetA, int lengthA, int strideA, int i, ErrorHandling errorHandling) {
            assert strideA == 2;
            int c = Integer.reverseBytes(TStringOps.readS2(arrayA, offsetA, lengthA, i));
            if (errorHandling == ErrorHandling.RETURN_NEGATIVE && !Encodings.isValidUnicodeCodepoint(c)) {
                return -1;
            }
            return c;
        }

        @TruffleBoundary
        private static int unsupported(AbstractTruffleString a, byte[] arrayA, Encoding encoding, int i, ErrorHandling errorHandling) {
            return JCodings.getInstance().decode(a, JCodings.asByteArray(a, arrayA), i, encoding, errorHandling);
        }
    }

    /**
     * @param codepoint the codepoint to search for.
     * @param codepointSearchValue possibly byte-swapped version of the codepoint for foreign-endian
     *            encodings.
     */
    static int indexOfFixedWidth(Node node, byte[] arrayA, long offsetA, int strideA, int codeRangeA, int codepoint, int codepointSearchValue, int fromIndex, int toIndex,
                    TStringOpsNodes.RawIndexOfCodePointNode indexOfNode) {
        if (fromIndex == toIndex || !TSCodeRange.isInCodeRange(codepoint, codeRangeA)) {
            return -1;
        }
        return indexOfNode.execute(node, arrayA, offsetA, strideA, codepointSearchValue, fromIndex, toIndex);
    }

    static int lastIndexOfFixedWidth(Node node, byte[] arrayA, long offsetA, int strideA, int codeRangeA, int codepoint, int fromIndex, int toIndex,
                    TStringOpsNodes.RawLastIndexOfCodePointNode indexOfNode) {
        return lastIndexOfFixedWidth(node, arrayA, offsetA, strideA, codeRangeA, codepoint, codepoint, fromIndex, toIndex, indexOfNode);
    }

    static int lastIndexOfFixedWidth(Node node, byte[] arrayA, long offsetA, int strideA, int codeRangeA, int codepoint, int codepointSearchValue, int fromIndex, int toIndex,
                    TStringOpsNodes.RawLastIndexOfCodePointNode indexOfNode) {
        if (fromIndex == toIndex || !TSCodeRange.isInCodeRange(codepoint, codeRangeA)) {
            return -1;
        }
        return indexOfNode.execute(node, arrayA, offsetA, strideA, codepointSearchValue, fromIndex, toIndex);
    }

    static int getCodepointFE(Encoding encoding, int codepoint) {
        assert isUTF16FE(encoding) || isUTF32FE(encoding);
        return isUTF16FE(encoding) ? Character.reverseBytes((char) codepoint) : Integer.reverseBytes(codepoint);
    }

    abstract static class IndexOfCodePointNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex,
                        int fromIndexRaw, int toIndex);

        @SuppressWarnings("unused")
        @Specialization(guards = "isFixedWidth(codeRangeA)")
        static int doFixedWidth(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex,
                        int fromIndexRaw, int toIndex,
                        @Cached TStringOpsNodes.RawIndexOfCodePointNode indexOfNode) {
            return indexOfFixedWidth(node, arrayA, offsetA, strideA, codeRangeA, codepoint, encoding.isForeignEndian() ? getCodepointFE(encoding, codepoint) : codepoint, fromIndexRaw, toIndex,
                            indexOfNode);
        }

        @Specialization(guards = "!isFixedWidth(codeRangeA)")
        static int decode(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int fromIndexRaw,
                        int toIndex,
                        @Cached TruffleStringIterator.InternalNextNode nextNode) {
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, encoding);
            it.setRawIndex(fromIndexRaw);
            return TruffleStringIterator.indexOf(node, it, encoding, codepoint, fromIndex, toIndex, nextNode);
        }
    }

    static final class IndexOfCodePointRaw {

        static int utf8(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int codepoint, int fromIndex, int toIndex) {
            assert strideA == 0;
            int encodedSize = Encodings.utf8EncodedSize(codepoint);
            if (encodedSize > toIndex - fromIndex) {
                return -1;
            }
            if (encodedSize == 1) {
                return TStringOps.indexOfCodePointWithStride(node, arrayA, offsetA, 0, fromIndex, toIndex, codepoint);
            }
            byte[] encoded = Encodings.utf8EncodeNonAscii(codepoint, encodedSize);
            return TStringOps.indexOfStringWithOrMaskWithStride(node, arrayA, offsetA, lengthA, 0, encoded, byteArrayBaseOffset(), encoded.length, 0, fromIndex, toIndex, null);
        }

        static int utf16(Node node, byte[] arrayA, long offsetA, int strideA, int codepoint, int fromIndex, int toIndex) {
            assert strideA == 1;
            int encodedSize = Encodings.utf16EncodedSize(codepoint);
            if (encodedSize > toIndex - fromIndex) {
                return -1;
            }
            if (encodedSize == 1) {
                return TStringOps.indexOfCodePointWithStride(node, arrayA, offsetA, 1, fromIndex, toIndex, codepoint);
            }
            return TStringOps.indexOf2ConsecutiveWithStride(
                            node, arrayA, offsetA, 1, fromIndex, toIndex, Character.highSurrogate(codepoint), Character.lowSurrogate(codepoint));
        }

        static int unlikelyCases(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex,
                        int toIndex) {
            if (encoding == Encoding.UTF_16_FOREIGN_ENDIAN) {
                assert strideA == 1;
                int encodedSize = Encodings.utf16EncodedSize(codepoint);
                if (encodedSize > toIndex - fromIndex) {
                    return -1;
                }
                if (encodedSize == 1) {
                    return TStringOps.indexOfCodePointWithStride(node, arrayA, offsetA, 1, fromIndex, toIndex, Character.reverseBytes((char) codepoint));
                }
                char hiSurrogate = Character.reverseBytes(Character.highSurrogate(codepoint));
                char loSurrogate = Character.reverseBytes(Character.lowSurrogate(codepoint));
                return TStringOps.indexOf2ConsecutiveWithStride(node, arrayA, offsetA, 1, fromIndex, toIndex, hiSurrogate, loSurrogate);
            } else {
                assert strideA == 0;
                return unsupported(node, a, arrayA, offsetA, lengthA, strideA, codeRangeA, encoding, codepoint, fromIndex, toIndex);
            }
        }

        @TruffleBoundary
        private static int unsupported(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex,
                        int toIndex) {
            final TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, encoding);
            it.setRawIndex(fromIndex);
            int loopCount = 0;
            while (it.hasNext() && it.getRawIndex() < toIndex) {
                int ret = it.getRawIndex();
                if (TruffleStringIterator.InternalNextNode.getUncached().execute(node, it, encoding) == codepoint) {
                    return ret;
                }
                TStringConstants.truffleSafePointPoll(node, ++loopCount);
            }
            return -1;
        }
    }

    abstract static class LastIndexOfCodePointNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA,
                        Encoding encoding, int codepoint, int fromIndex, int toIndex);

        @SuppressWarnings("unused")
        @Specialization(guards = "isFixedWidth(codeRangeA)")
        static int doFixedWidth(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA,
                        Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TStringOpsNodes.RawLastIndexOfCodePointNode lastIndexOfNode) {
            return lastIndexOfFixedWidth(node, arrayA, offsetA, strideA, codeRangeA, codepoint, encoding.isForeignEndian() ? getCodepointFE(encoding, codepoint) : codepoint, fromIndex, toIndex,
                            lastIndexOfNode);
        }

        @Specialization(guards = "!isFixedWidth(codeRangeA)")
        static int decode(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA,
                        Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TruffleStringIterator.InternalNextNode nextNode) {
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, encoding);
            return TruffleStringIterator.lastIndexOf(node, it, encoding, codepoint, fromIndex, toIndex, nextNode);
        }
    }

    static final class LastIndexOfCodePointRaw {

        static int utf8(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int codepoint, int fromIndex, int toIndex,
                        TStringOpsNodes.RawLastIndexOfCodePointNode lastIndexOfNode) {
            int encodedSize = Encodings.utf8EncodedSize(codepoint);
            if (encodedSize > fromIndex - toIndex) {
                return -1;
            }
            if (encodedSize == 1) {
                return lastIndexOfFixedWidth(node, arrayA, offsetA, strideA, codeRangeA, codepoint, fromIndex, toIndex, lastIndexOfNode);
            }
            byte[] encoded = Encodings.utf8EncodeNonAscii(codepoint, encodedSize);
            return TStringOps.lastIndexOfStringWithOrMaskWithStride(node,
                            arrayA, offsetA, lengthA, 0,
                            encoded, byteArrayBaseOffset(), encoded.length, 0, fromIndex, toIndex, null);
        }

        static int utf16(Node node, byte[] arrayA, long offsetA, int strideA, int codeRangeA, int codepoint, int fromIndex, int toIndex,
                        TStringOpsNodes.RawLastIndexOfCodePointNode lastIndexOfNode) {
            assert strideA == 1;
            int encodedSize = Encodings.utf16EncodedSize(codepoint);
            if (encodedSize > fromIndex - toIndex) {
                return -1;
            }
            if (encodedSize == 1) {
                return lastIndexOfFixedWidth(node, arrayA, offsetA, strideA, codeRangeA, codepoint, fromIndex, toIndex, lastIndexOfNode);
            }
            return TStringOps.lastIndexOf2ConsecutiveWithOrMaskWithStride(
                            node, arrayA, offsetA, 1, fromIndex, toIndex, Character.highSurrogate(codepoint), Character.lowSurrogate(codepoint), 0, 0);
        }

        static int unlikelyCases(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex,
                        int toIndex) {
            if (encoding == Encoding.UTF_16_FOREIGN_ENDIAN) {
                assert strideA == 1;
                int encodedSize = Encodings.utf16EncodedSize(codepoint);
                if (encodedSize > fromIndex - toIndex) {
                    return -1;
                }
                if (encodedSize == 1) {
                    if (!TSCodeRange.isInCodeRange(codepoint, codeRangeA)) {
                        return -1;
                    }
                    return TStringOps.lastIndexOfCodePointWithOrMaskWithStride(node, arrayA, offsetA, 1, fromIndex, toIndex, Character.reverseBytes((char) codepoint), 0);
                }
                char hiSurrogate = Character.reverseBytes(Character.highSurrogate(codepoint));
                char loSurrogate = Character.reverseBytes(Character.lowSurrogate(codepoint));
                return TStringOps.lastIndexOf2ConsecutiveWithOrMaskWithStride(node, arrayA, offsetA, 1, fromIndex, toIndex, hiSurrogate, loSurrogate, 0, 0);
            } else {
                assert strideA == 0;
                return unsupported(node, a, arrayA, offsetA, lengthA, strideA, codeRangeA, encoding, codepoint, fromIndex, toIndex);
            }
        }

        @TruffleBoundary
        private static int unsupported(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex,
                        int toIndex) {
            final TruffleStringIterator it = TruffleString.backwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, encoding);
            it.setRawIndex(fromIndex);
            int loopCount = 0;
            while (it.hasPrevious() && it.getRawIndex() >= toIndex) {
                if (TruffleStringIterator.InternalPreviousNode.getUncached().execute(node, it, encoding, DecodingErrorHandler.DEFAULT) == codepoint) {
                    return it.getRawIndex();
                }
                TStringConstants.truffleSafePointPoll(node, ++loopCount);
            }
            return -1;
        }
    }

    abstract static class IndexOfCodePointSetNode extends AbstractInternalNode {

        abstract int execute(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, TruffleString.CodePointSet codePointSet,
                        BranchProfile[] branchProfiles);

        @SuppressWarnings("unused")
        @Specialization(guards = "compaction == cachedCompaction", limit = Stride.STRIDE_CACHE_LIMIT)
        static int dynamicStride(Node node, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex, TruffleString.CodePointSet codePointSet,
                        BranchProfile[] branchProfiles,
                        @Bind("fromStride(strideA)") CompactionLevel compaction,
                        @Cached("compaction") CompactionLevel cachedCompaction) {
            return codePointSet.doIndexOf(node, arrayA, offsetA, lengthA, cachedCompaction.getStride(), codeRangeA, fromIndex, toIndex, branchProfiles);
        }
    }

    static TruffleString substring(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int fromIndex, int length,
                    boolean lazy,
                    CalcStringAttributesNode calcAttributesNode,
                    InlinedConditionProfile stride1MustMaterializeProfile,
                    InlinedConditionProfile stride2MustMaterializeProfile,
                    InlinedConditionProfile singleByteProfile) {
        assert length > 0;
        if (CompilerDirectives.injectBranchProbability(CompilerDirectives.UNLIKELY_PROBABILITY, fromIndex == 0 && length == lengthA && a instanceof TruffleString)) {
            return (TruffleString) a;
        } else {
            long attrs = calcAttributesNode.execute(node, a, arrayA, offsetA, length, strideA, encoding, fromIndex, codeRangeA);
            int codeRange = StringAttributes.getCodeRange(attrs);
            int codePointLength = StringAttributes.getCodePointLength(attrs);
            final int newStride = Stride.fromCodeRange(codeRange, encoding);
            if (singleByteProfile.profile(node, length == 1 && newStride == 0 && encoding.isSupported())) {
                return TStringConstants.getSingleByte(encoding, TStringOps.readValue(arrayA, offsetA, lengthA, strideA, fromIndex));
            }
            CompilerAsserts.partialEvaluationConstant(lazy);
            final Object data;
            final int offset;
            final long regionOffset = offsetA + (fromIndex << strideA);
            if (lazy) {
                if (isUTF16Or32(encoding) && stride1MustMaterializeProfile.profile(node, strideA == 1 && newStride == 0)) {
                    offset = 0;
                    final byte[] newBytes = new byte[length];
                    TStringOps.arraycopyWithStride(node,
                                    arrayA, regionOffset, 1, 0,
                                    newBytes, byteArrayBaseOffset(), 0, 0, length);
                    data = newBytes;
                } else if (encoding == Encoding.UTF_32 && stride2MustMaterializeProfile.profile(node, strideA == 2 && newStride < 2)) {
                    // Always materialize 4-byte UTF-32 strings when they can be compacted.
                    // Otherwise, they could get re-interpreted as UTF-16 and break the
                    // assumption that all UTF-16 strings are stride 0 or 1.
                    offset = 0;
                    final byte[] newBytes = new byte[length << newStride];
                    if (newStride == 0) {
                        TStringOps.arraycopyWithStride(node,
                                        arrayA, regionOffset, 2, 0,
                                        newBytes, byteArrayBaseOffset(), 0, 0, length);
                    } else {
                        assert newStride == 1;
                        TStringOps.arraycopyWithStride(node,
                                        arrayA, regionOffset, 2, 0,
                                        newBytes, byteArrayBaseOffset(), 1, 0, length);
                    }
                    data = newBytes;
                } else {
                    if (arrayA == null) {
                        if (isUnsupportedEncoding(encoding)) {
                            if (!JCodings.JCODINGS_ENABLED) {
                                throw CompilerDirectives.shouldNotReachHere();
                            }
                            // avoid conflicts in NativePointer#getBytes
                            data = ((NativePointer) a.data()).copy();
                        } else {
                            data = a.data();
                        }
                    } else {
                        data = arrayA;
                    }
                    offset = a.offset() + (fromIndex << strideA);
                }
            } else {
                data = TStringOps.arraycopyOfWithStride(node, arrayA, regionOffset, length, strideA, length, newStride);
                offset = 0;
            }
            return TruffleString.createFromArray(data, offset, length, newStride, encoding, codePointLength, codeRange);
        }
    }

    static TruffleString concat(Node node, AbstractTruffleString a, AbstractTruffleString b, Encoding encoding, int concatLength, int concatStride, int concatCodeRange,
                    InlinedConditionProfile managedProfileA,
                    InlinedConditionProfile nativeProfileA,
                    InlinedConditionProfile managedProfileB,
                    InlinedConditionProfile nativeProfileB,
                    InlinedConditionProfile calcCodePointLengthAProfile,
                    InlinedConditionProfile calcCodePointLengthBProfile,
                    CalcStringAttributesNode calculateAttributesNode,
                    InlinedConditionProfile brokenProfile) {
        Object dataA = a.data();
        Object dataB = b.data();
        try {
            final byte[] arrayA;
            final long addOffsetA;
            if (managedProfileA.profile(node, dataA instanceof byte[])) {
                arrayA = (byte[]) dataA;
                addOffsetA = byteArrayBaseOffset();
            } else if (nativeProfileA.profile(node, dataA instanceof NativePointer)) {
                arrayA = null;
                addOffsetA = NativePointer.unwrap(dataA);
            } else {
                arrayA = a.materializeLazy(node, dataA);
                addOffsetA = byteArrayBaseOffset();
            }
            final long offsetA = a.offset() + addOffsetA;
            final int lengthA = a.length();
            final int strideA = a.stride();

            final byte[] arrayB;
            final long addOffsetB;
            if (managedProfileB.profile(node, dataB instanceof byte[])) {
                arrayB = (byte[]) dataB;
                addOffsetB = byteArrayBaseOffset();
            } else if (nativeProfileB.profile(node, dataB instanceof NativePointer)) {
                arrayB = null;
                addOffsetB = NativePointer.unwrap(dataB);
            } else {
                arrayB = b.materializeLazy(node, dataB);
                addOffsetB = byteArrayBaseOffset();
            }
            final long offsetB = b.offset() + addOffsetB;
            final int lengthB = b.length();
            final int strideB = b.stride();

            final byte[] bytes = concatMaterializeBytes(node, arrayA, offsetA, lengthA, strideA, arrayB, offsetB, lengthB, strideB, encoding, concatLength, concatStride);
            final int codeRange;
            final int codePointLength;
            if (brokenProfile.profile(node, isBrokenMultiByte(concatCodeRange))) {
                final long attrs = calculateAttributesNode.execute(node, null, bytes, byteArrayBaseOffset(), concatLength, concatStride, encoding, 0, TSCodeRange.getBrokenMultiByte());
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            } else {
                codePointLength = getCodePointLength(node, a, arrayA, offsetA, encoding, calcCodePointLengthAProfile) +
                                getCodePointLength(node, b, arrayB, offsetB, encoding, calcCodePointLengthBProfile);
                codeRange = concatCodeRange;
            }
            return TruffleString.createFromByteArray(bytes, concatLength, concatStride, encoding, codePointLength, codeRange);
        } finally {
            Reference.reachabilityFence(dataA);
            Reference.reachabilityFence(dataB);
        }
    }

    static byte[] concatMaterializeBytes(Node node,
                    byte[] arrayA, long offsetA, int lengthA, int strideA,
                    byte[] arrayB, long offsetB, int lengthB, int strideB, Encoding encoding, int concatLength, int concatStride) {
        if (encoding == Encoding.UTF_16 || encoding == Encoding.UTF_32) {
            final byte[] bytes = new byte[concatLength << concatStride];
            TStringOps.arraycopyWithStride(node,
                            arrayA, offsetA, strideA, 0,
                            bytes, byteArrayBaseOffset(), concatStride, 0, lengthA);
            TStringOps.arraycopyWithStride(node,
                            arrayB, offsetB, strideB, 0,
                            bytes, byteArrayBaseOffset(), concatStride, lengthA, lengthB);
            return bytes;
        } else {
            final byte[] bytes = new byte[concatLength << concatStride];
            TStringOps.arraycopyWithStride(node,
                            arrayA, offsetA, 0, 0,
                            bytes, byteArrayBaseOffset(), 0, 0, lengthA << strideA);
            TStringOps.arraycopyWithStride(node,
                            arrayB, offsetB, 0, 0,
                            bytes, byteArrayBaseOffset(), 0, lengthA << strideA, lengthB << strideB);
            return bytes;
        }
    }

    abstract static class RegionEqualsNode extends AbstractInternalNode {

        abstract boolean execute(Node node,
                        AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndexA,
                        AbstractTruffleString b, byte[] arrayB, long offsetB, int lengthB, int strideB, int codeRangeB, int fromIndexB, int length, Encoding encoding);

        @SuppressWarnings("unused")
        @Specialization(guards = {"isFixedWidth(codeRangeA, codeRangeB)"})
        boolean direct(
                        AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndexA,
                        AbstractTruffleString b, byte[] arrayB, long offsetB, int lengthB, int strideB, int codeRangeB, int fromIndexB, int length, Encoding encoding) {
            return TStringOps.regionEqualsWithOrMaskWithStride(this,
                            arrayA, offsetA, lengthA, strideA, fromIndexA,
                            arrayB, offsetB, lengthB, strideB, fromIndexB, null, length);
        }

        @Specialization(guards = {"!isFixedWidth(codeRangeA, codeRangeB)"})
        static boolean decode(Node node,
                        AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, int fromIndexA,
                        AbstractTruffleString b, byte[] arrayB, long offsetB, int lengthB, int strideB, int codeRangeB, int fromIndexB, int length, Encoding encoding,
                        @Cached TruffleStringIterator.InternalNextNode nextNodeA,
                        @Cached TruffleStringIterator.InternalNextNode nextNodeB) {
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.forwardIterator(b, arrayB, offsetB, lengthB, strideB, codeRangeB, encoding);
            for (int i = 0; i < fromIndexA; i++) {
                if (!aIt.hasNext()) {
                    return false;
                }
                nextNodeA.execute(node, aIt, encoding);
                TStringConstants.truffleSafePointPoll(node, i + 1);
            }
            for (int i = 0; i < fromIndexB; i++) {
                if (!bIt.hasNext()) {
                    return false;
                }
                nextNodeB.execute(node, bIt, encoding);
                TStringConstants.truffleSafePointPoll(node, i + 1);
            }
            for (int i = 0; i < length; i++) {
                if (!(aIt.hasNext() && bIt.hasNext()) || nextNodeA.execute(node, aIt, encoding) != nextNodeB.execute(node, bIt, encoding)) {
                    return false;
                }
                TStringConstants.truffleSafePointPoll(node, i + 1);
            }
            return true;
        }
    }

    abstract static class InternalIndexOfStringNode extends AbstractInternalNode {

        abstract int execute(Node node,
                        AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA,
                        AbstractTruffleString b, byte[] arrayB, long offsetB, int lengthB, int strideB, int codeRangeB, int fromIndex, int toIndex, Encoding encoding);

        @SuppressWarnings("unused")
        @Specialization(guards = {"isFixedWidth(codeRangeA, codeRangeB)"})
        static int direct(Node node,
                        AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA,
                        AbstractTruffleString b, byte[] arrayB, long offsetB, int lengthB, int strideB, int codeRangeB, int fromIndex, int toIndex, Encoding encoding,
                        @Cached RawIndexOfStringNode indexOfStringNode) {
            return indexOfStringNode.execute(node, arrayA, offsetA, lengthA, strideA, arrayB, offsetB, lengthB, strideB, fromIndex, toIndex, null);
        }

        @Specialization(guards = {"!isFixedWidth(codeRangeA, codeRangeB)"})
        static int decode(Node node,
                        AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA,
                        AbstractTruffleString b, byte[] arrayB, long offsetB, int lengthB, int strideB, int codeRangeB, int fromIndex, int toIndex, Encoding encoding,
                        @Cached TruffleStringIterator.InternalNextNode nextNodeA,
                        @Cached TruffleStringIterator.InternalNextNode nextNodeB) {
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.forwardIterator(b, arrayB, offsetB, lengthB, strideB, codeRangeB, encoding);
            return TruffleStringIterator.indexOfString(node, aIt, bIt, encoding, fromIndex, toIndex, nextNodeA, nextNodeB);
        }
    }

    static final class IndexOfStringRaw {

        @TruffleBoundary
        static int unsupported(Node node,
                        AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA,
                        AbstractTruffleString b, byte[] arrayB, long offsetB, int lengthB, int strideB, int codeRangeB, int fromIndex, int toIndex, byte[] mask, Encoding encoding) {
            assert mask == null;
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.forwardIterator(b, arrayB, offsetB, lengthB, strideB, codeRangeB, encoding);
            return TruffleStringIterator.byteIndexOfString(node, aIt, bIt, encoding, fromIndex, toIndex,
                            TruffleStringIterator.InternalNextNode.getUncached(),
                            TruffleStringIterator.InternalNextNode.getUncached());
        }
    }

    abstract static class LastIndexOfStringNode extends AbstractInternalNode {

        abstract int execute(Node node,
                        AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA,
                        AbstractTruffleString b, byte[] arrayB, long offsetB, int lengthB, int strideB, int codeRangeB, int fromIndex, int toIndex, Encoding encoding);

        @SuppressWarnings("unused")
        @Specialization(guards = {"isFixedWidth(codeRangeA, codeRangeB)"})
        static int direct(Node node,
                        AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA,
                        AbstractTruffleString b, byte[] arrayB, long offsetB, int lengthB, int strideB, int codeRangeB, int fromIndex, int toIndex, Encoding encoding,
                        @Cached RawLastIndexOfStringNode indexOfStringNode) {
            return indexOfStringNode.execute(node,
                            arrayA, offsetA, lengthA, strideA,
                            arrayB, offsetB, lengthB, strideB, fromIndex, toIndex, null);
        }

        @Specialization(guards = {"!isFixedWidth(codeRangeA, codeRangeB)"})
        static int decode(Node node,
                        AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA,
                        AbstractTruffleString b, byte[] arrayB, long offsetB, int lengthB, int strideB, int codeRangeB, int fromIndex, int toIndex, Encoding encoding,
                        @Cached TruffleStringIterator.InternalNextNode nextNodeA,
                        @Cached TruffleStringIterator.InternalPreviousNode prevNodeA,
                        @Cached TruffleStringIterator.InternalPreviousNode prevNodeB) {
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.backwardIterator(b, arrayB, offsetB, lengthB, strideB, codeRangeB, encoding);
            return TruffleStringIterator.lastIndexOfString(node, aIt, bIt, encoding, fromIndex, toIndex, nextNodeA, prevNodeA, prevNodeB);
        }
    }

    static final class LastIndexOfStringRaw {

        @TruffleBoundary
        static int unsupported(Node node,
                        AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA,
                        AbstractTruffleString b, byte[] arrayB, long offsetB, int lengthB, int strideB, int codeRangeB, int fromIndex, int toIndex, byte[] mask, Encoding encoding) {
            assert mask == null;
            assert lengthB > 0 && !indexOfCannotMatch(codeRangeA, lengthB, codeRangeB, null, fromIndex - toIndex);
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.backwardIterator(b, arrayB, offsetB, lengthB, strideB, codeRangeB, encoding);
            return TruffleStringIterator.lastByteIndexOfString(node, aIt, bIt, encoding, fromIndex, toIndex,
                            TruffleStringIterator.InternalNextNode.getUncached(),
                            TruffleStringIterator.InternalPreviousNode.getUncached(),
                            TruffleStringIterator.InternalPreviousNode.getUncached());
        }
    }

    abstract static class CalcStringAttributesNode extends AbstractInternalNode {

        abstract long execute(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int length, int stride, Encoding encoding, int fromIndex, int knownCodeRange);

        @Specialization
        static long calc(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int length, int stride, Encoding encoding, int fromIndex, int knownCodeRange,
                        @Cached InlinedConditionProfile codeRangeProfile1,
                        @Cached InlinedConditionProfile codeRangeProfile2,
                        @Cached InlinedConditionProfile codeRangeProfile3) {
            if (length == 0) {
                return StringAttributes.create(length, TSCodeRange.getAsciiCodeRange(encoding));
            } else if (is7Bit(knownCodeRange)) {
                assert TSCodeRange.isFixedWidth(knownCodeRange);
                return StringAttributes.create(length, TSCodeRange.markPrecise(knownCodeRange));
            }
            if (encoding == Encoding.UTF_8) {
                return utf8(node, a, arrayA, offsetA, length, stride, fromIndex, knownCodeRange, codeRangeProfile1, codeRangeProfile2);
            } else if (encoding == Encoding.UTF_16 || encoding == Encoding.UTF_32) {
                return utf16Or32(node, arrayA, offsetA, length, stride, encoding, fromIndex, knownCodeRange, codeRangeProfile1, codeRangeProfile2, codeRangeProfile3);
            } else {
                return unlikelyCases(node, a, arrayA, offsetA, length, stride, encoding, fromIndex, knownCodeRange, codeRangeProfile1);
            }
        }

        static long utf8(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int length, int stride, int fromIndex, int knownCodeRange,
                        InlinedConditionProfile validProfile,
                        InlinedConditionProfile brokenProfile) {
            assert stride == 0;
            long off = offsetA + fromIndex;
            if (validProfile.profile(node, isValid(knownCodeRange) && a != null)) {
                return TStringOps.calcStringAttributesUTF8(node, arrayA, off, length, true, fromIndex + length == a.length(), brokenProfile);
            } else {
                return TStringOps.calcStringAttributesUTF8(node, arrayA, off, length, false, false, brokenProfile);
            }
        }

        static long utf16Or32(Node node, byte[] arrayA, long offsetA, int length, int stride, Encoding encoding, int fromIndex,
                        int knownCodeRange,
                        InlinedConditionProfile oneByteProfile,
                        InlinedConditionProfile twoByteProfile,
                        InlinedConditionProfile utf16ValidProfile) {
            if (oneByteProfile.profile(node, is8Bit(knownCodeRange) && stride == 0)) {
                return StringAttributes.create(length, TStringOps.calcStringAttributesLatin1(node, arrayA, offsetA + fromIndex, length));
            } else if (twoByteProfile.profile(node, isUpTo16Bit(knownCodeRange) && stride == 1)) {
                return StringAttributes.create(length, TStringOps.calcStringAttributesBMP(node, arrayA, offsetA + (fromIndex << 1), length));
            } else if (encoding == Encoding.UTF_16) {
                assert stride == 1;
                if (utf16ValidProfile.profile(node, isValid(knownCodeRange))) {
                    return TStringOps.calcStringAttributesUTF16(node, arrayA, offsetA + (fromIndex << 1), length, true);
                } else {
                    assert isBroken(knownCodeRange);
                    return TStringOps.calcStringAttributesUTF16(node, arrayA, offsetA + (fromIndex << 1), length, false);
                }
            } else {
                assert isUTF32(encoding);
                assert stride == 2;
                return StringAttributes.create(length, TStringOps.calcStringAttributesUTF32(node, arrayA, offsetA + (fromIndex << 2), length));
            }
        }

        @InliningCutoff
        static long unlikelyCases(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int length, int stride, Encoding encoding, int fromIndex, int knownCodeRange,
                        InlinedConditionProfile validProfile) {
            if (isAsciiBytesOrLatin1(encoding)) {
                assert stride == 0;
                int codeRange = TStringOps.calcStringAttributesLatin1(node, arrayA, offsetA + fromIndex, length);
                return StringAttributes.create(length, is8Bit(codeRange) ? TSCodeRange.asciiLatinBytesNonAsciiCodeRange(encoding) : codeRange);
            } else if (encoding == Encoding.UTF_16_FOREIGN_ENDIAN) {
                if (validProfile.profile(node, isValid(knownCodeRange))) {
                    return TStringOps.calcStringAttributesUTF16FEAssumeValid(node, arrayA, offsetA + ((long) fromIndex << 1), length);
                } else {
                    return TStringOps.calcStringAttributesUTF16FE(node, arrayA, offsetA + ((long) fromIndex << 1), length);
                }
            } else if (encoding == Encoding.UTF_32_FOREIGN_ENDIAN) {
                return StringAttributes.create(length, TStringOps.calcStringAttributesUTF32FE(node, arrayA, offsetA + ((long) fromIndex << 2), length));
            } else {
                assert stride == 0;
                assert isUnsupportedEncoding(encoding);
                return JCodings.getInstance().calcStringAttributes(node, a, arrayA, offsetA, length, encoding, fromIndex);
            }
        }

        static CalcStringAttributesNode getUncached() {
            return TStringInternalNodesFactory.CalcStringAttributesNodeGen.getUncached();
        }
    }

    abstract static class ParseIntNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int radix)
                        throws TruffleString.NumberFormatException;

        @SuppressWarnings("unused")
        @Specialization(guards = {"is7Bit(codeRangeA)", "compaction == cachedCompaction"}, limit = Stride.STRIDE_CACHE_LIMIT, unroll = Stride.STRIDE_UNROLL)
        static int do7Bit(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int radix,
                        @Bind("fromStride(strideA)") CompactionLevel compaction,
                        @Cached("compaction") CompactionLevel cachedCompaction,
                        @Cached InlinedBranchProfile errorProfile) throws TruffleString.NumberFormatException {
            return NumberConversion.parseInt7Bit(node, a, arrayA, offsetA, lengthA, cachedCompaction.getStride(), radix, errorProfile);
        }

        @Specialization(guards = "!is7Bit(codeRangeA)")
        static int doGeneric(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int radix,
                        @Cached TruffleStringIterator.InternalNextNode nextNode,
                        @Cached InlinedBranchProfile errorProfile) throws TruffleString.NumberFormatException {
            return NumberConversion.parseInt(node, AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, encoding), encoding, radix, errorProfile, nextNode);
        }
    }

    abstract static class ParseLongNode extends AbstractInternalNode {

        abstract long execute(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int radix)
                        throws TruffleString.NumberFormatException;

        @SuppressWarnings("unused")
        @Specialization(guards = {"is7Bit(codeRangeA)", "compaction == cachedCompaction"}, limit = Stride.STRIDE_CACHE_LIMIT, unroll = Stride.STRIDE_UNROLL)
        static long do7Bit(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int radix,
                        @Bind("fromStride(strideA)") CompactionLevel compaction,
                        @Cached("compaction") CompactionLevel cachedCompaction,
                        @Cached InlinedBranchProfile errorProfile) throws TruffleString.NumberFormatException {
            return NumberConversion.parseLong7Bit(node, a, arrayA, offsetA, lengthA, cachedCompaction.getStride(), radix, errorProfile);
        }

        @Specialization(guards = "!is7Bit(codeRangeA)")
        static long parseLong(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding encoding, int radix,
                        @Cached TruffleStringIterator.InternalNextNode nextNode,
                        @Cached InlinedBranchProfile errorProfile) throws TruffleString.NumberFormatException {
            return NumberConversion.parseLong(node, AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, encoding), encoding, radix, errorProfile, nextNode);
        }
    }

    abstract static class ParseDoubleNode extends AbstractInternalNode {

        abstract double execute(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA) throws TruffleString.NumberFormatException;

        @SuppressWarnings("unused")
        @Specialization(guards = "compaction == cachedCompaction", limit = Stride.STRIDE_CACHE_LIMIT, unroll = Stride.STRIDE_UNROLL)
        static double doParse(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA,
                        @Bind("fromStride(strideA)") CompactionLevel compaction,
                        @Cached("compaction") CompactionLevel cachedCompaction) throws TruffleString.NumberFormatException {
            return FastDoubleParser.parseDouble(node, a, arrayA, offsetA, lengthA, cachedCompaction.getStride(), 0, lengthA);
        }
    }

    static String createJavaString(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA,
                    InlinedConditionProfile reuseProfile) {
        assert TSCodeRange.is7Or8Bit(a.codeRange()) || TSCodeRange.isPrecise(a.codeRange());
        assert a.isLooselyCompatibleTo(Encoding.UTF_16);
        final int stride = TStringUnsafe.COMPACT_STRINGS_ENABLED ? Stride.fromCodeRangeUTF16(a.codeRange()) : 1;
        final byte[] bytes;
        if (reuseProfile.profile(node, a instanceof TruffleString && arrayA != null && lengthA << strideA == arrayA.length && strideA == stride)) {
            assert offsetA == byteArrayBaseOffset();
            bytes = arrayA;
        } else {
            bytes = new byte[lengthA << stride];
            TStringOps.arraycopyWithStride(node,
                            arrayA, offsetA, strideA, 0,
                            bytes, byteArrayBaseOffset(), stride, 0, lengthA);
        }
        int javaStringHash = a.hashCode;
        if (javaStringHash == AbstractTruffleString.MASKED_ZERO_HASH_CODE) {
            // hash code could be 0 or -1, treat the hash code as uninitialized
            javaStringHash = 0;
        }
        return TStringUnsafe.createJavaString(bytes, stride, javaStringHash);
    }

    abstract static class ToValidStringNode extends AbstractInternalNode {

        private static final int[] UTF_32_ASTRAL_RANGE = {0x10000, 0x10ffff};
        private static final int[] UTF_32_INVALID_RANGES = {Character.MIN_SURROGATE, Character.MAX_SURROGATE, 0x11_0000, 0xffff_ffff};

        abstract TruffleString execute(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, Encoding encoding);

        @SuppressWarnings("unused")
        @Specialization(guards = "isAscii(encoding)")
        static TruffleString ascii(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, Encoding encoding) {
            assert strideA == 0;
            byte[] array = TStringOps.arraycopyOfWithStride(node, arrayA, offsetA, lengthA, 0, lengthA, 0);
            int pos = 0;
            int loopCount = 0;
            while (pos < lengthA) {
                pos = TStringOps.indexOfCodePointWithMaskWithStrideIntl(node, array, byteArrayBaseOffset(), lengthA, 0, pos, 0xff, 0x7f);
                if (pos >= 0) {
                    array[pos++] = '?';
                } else {
                    break;
                }
                TStringConstants.truffleSafePointPoll(node, ++loopCount);
            }
            return TruffleString.createFromByteArray(array, lengthA, 0, Encoding.US_ASCII, lengthA, TSCodeRange.get7Bit());
        }

        @Specialization(guards = "isUTF8(encoding)")
        static TruffleString utf8(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, @SuppressWarnings("unused") Encoding encoding,
                        @Cached InlinedBranchProfile outOfMemoryProfile) {
            assert strideA == 0;
            assert isPrecise(a.codeRange());
            assert a.isCodePointLengthKnown();

            boolean isLarge = TransCodeIntlNode.isLarge(a.codePointLength());
            byte[] buffer = new byte[isLarge ? TStringConstants.MAX_ARRAY_SIZE : a.codePointLength() * 4];
            int length = 0;
            int state = UTF8_ACCEPT;
            int lastCodePointPos = 0;
            int lastErrorPos = 0;
            int codePointLength = a.codePointLength();
            byte[] stateMachine = Encodings.UTF_8_STATE_MACHINE;
            int i = 0;
            while (i < lengthA) {
                int b = readS0(arrayA, offsetA, lengthA, i++);
                int type = stateMachine[b];
                state = stateMachine[256 + state + type];
                if (state == UTF8_ACCEPT) {
                    lastCodePointPos = i;
                } else if (state == UTF8_REJECT) {
                    int curCPLength = i - (lastCodePointPos + 1);
                    length = utf8CopyValidRegion(node, arrayA, offsetA, outOfMemoryProfile, isLarge, buffer, length, lastCodePointPos, lastErrorPos);
                    System.arraycopy(Encodings.CONVERSION_REPLACEMENT_UTF_8, 0, buffer, length, Encodings.CONVERSION_REPLACEMENT_UTF_8.length);
                    length += Encodings.CONVERSION_REPLACEMENT_UTF_8.length;
                    state = UTF8_ACCEPT;
                    if (curCPLength > 1) {
                        codePointLength -= curCPLength - 1;
                        i--;
                    }
                    lastErrorPos = i;
                    lastCodePointPos = i;
                }
                TStringConstants.truffleSafePointPoll(node, i);
            }
            length = utf8CopyValidRegion(node, arrayA, offsetA, outOfMemoryProfile, isLarge, buffer, length, lastCodePointPos, lastErrorPos);
            if (lastCodePointPos != lengthA && lastErrorPos != lastCodePointPos) {
                System.arraycopy(Encodings.CONVERSION_REPLACEMENT_UTF_8, 0, buffer, length, Encodings.CONVERSION_REPLACEMENT_UTF_8.length);
                length += Encodings.CONVERSION_REPLACEMENT_UTF_8.length;
                int curCPLength = lengthA - lastCodePointPos;
                if (curCPLength > 1) {
                    codePointLength -= curCPLength - 1;
                }
            }
            return TruffleString.createFromByteArray(Arrays.copyOf(buffer, length), length, 0, Encoding.UTF_8, codePointLength, TSCodeRange.getValidMultiByte());
        }

        private static int utf8CopyValidRegion(Node node, byte[] arrayA, long offsetA,
                        InlinedBranchProfile outOfMemoryProfile, boolean isLarge, byte[] buffer, int length, int lastCodePointPos, int lastErrorPos) {
            int lengthCPY = lastCodePointPos - lastErrorPos;
            if (isLarge && Integer.compareUnsigned(length + lengthCPY + Encodings.CONVERSION_REPLACEMENT_UTF_8.length, buffer.length) > 0) {
                outOfMemoryProfile.enter(node);
                throw InternalErrors.outOfMemory();
            }
            TStringOps.arraycopyWithStride(node, arrayA, offsetA, 0, lastErrorPos, buffer, byteArrayBaseOffset(), 0, length, lengthCPY);
            return length + lengthCPY;
        }

        @Specialization(guards = "isUTF16(encoding)")
        static TruffleString utf16(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, @SuppressWarnings("unused") Encoding encoding) {
            assert strideA == 1;
            byte[] array = TStringOps.arraycopyOfWithStride(node, arrayA, offsetA, lengthA, 1, lengthA, 1);
            int pos = 0;
            int codeRange = TSCodeRange.get16Bit();
            int loopCount = 0;
            while (true) {
                pos = TStringOps.indexOfCodePointWithMaskWithStrideIntl(node, array, byteArrayBaseOffset(), lengthA, 1, pos, 0xdfff, 0x7ff);
                if (pos >= 0) {
                    boolean invalid = true;
                    if (pos != lengthA - 1) {
                        char c = (char) TStringOps.readFromByteArrayS1(array, pos);
                        assert Encodings.isUTF16Surrogate(c);
                        if (!Encodings.isUTF16LowSurrogate(c)) {
                            assert Encodings.isUTF16HighSurrogate(c);
                            if (Encodings.isUTF16LowSurrogate((char) TStringOps.readFromByteArrayS1(array, pos + 1))) {
                                invalid = false;
                                codeRange = TSCodeRange.getValidMultiByte();
                                pos++;
                            }
                        }
                    }
                    if (invalid) {
                        writeToByteArrayS1(array, pos, 0xfffd);
                    }
                    if (++pos == lengthA) {
                        break;
                    }
                } else {
                    break;
                }
                TStringConstants.truffleSafePointPoll(node, ++loopCount);
            }
            return TruffleString.createFromByteArray(array, lengthA, 1, Encoding.UTF_16, a.codePointLength(), codeRange);
        }

        @Specialization(guards = "isUTF32(encoding)")
        static TruffleString utf32(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, @SuppressWarnings("unused") Encoding encoding,
                        @Cached InlinedConditionProfile strideProfile) {
            assert strideA == 2;
            final byte[] array;
            final int stride;
            final int codeRange;
            if (strideProfile.profile(node, TStringOps.indexOfAnyIntRange(node, arrayA, offsetA, 2, 0, lengthA, UTF_32_ASTRAL_RANGE) < 0)) {
                array = TStringOps.arraycopyOfWithStride(node, arrayA, offsetA, lengthA, 2, lengthA, 1);
                stride = 1;
                codeRange = TSCodeRange.get16Bit();
                utf32ReplaceInvalid(node, arrayA, offsetA, lengthA, array, 1);
            } else {
                array = TStringOps.arraycopyOfWithStride(node, arrayA, offsetA, lengthA, 2, lengthA, 2);
                stride = 2;
                codeRange = TSCodeRange.getValidFixedWidth();
                utf32ReplaceInvalid(node, arrayA, offsetA, lengthA, array, 2);
            }
            return TruffleString.createFromByteArray(array, lengthA, stride, Encoding.UTF_32, a.codePointLength(), codeRange);
        }

        private static void utf32ReplaceInvalid(Node node, byte[] arrayA, long offsetA, int length, byte[] array, int stride) {
            int pos = 0;
            int loopCount = 0;
            while (pos < length) {
                pos = TStringOps.indexOfAnyIntRange(node, arrayA, offsetA, 2, pos, length, UTF_32_INVALID_RANGES);
                if (pos >= 0) {
                    TStringOps.writeToByteArray(array, stride, pos++, 0xfffd);
                } else {
                    break;
                }
                TStringConstants.truffleSafePointPoll(node, ++loopCount);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"JCODINGS_ENABLED", "isUnsupportedEncoding(encoding)"})
        static TruffleString unsupported(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, Encoding encoding) {
            throw InternalErrors.unsupportedOperation();
        }
    }

    abstract static class TransCodeNode extends AbstractInternalNode {

        @InliningCutoff
        abstract TruffleString execute(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA, Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler);

        @Specialization
        static TruffleString transcode(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA, Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached InlinedConditionProfile asciiBytesInvalidProfile,
                        @Cached TransCodeIntlWithErrorHandlerNode transCodeIntlNode) {
            CompilerAsserts.partialEvaluationConstant(errorHandler);
            if (AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS && a.isImmutable() && TSCodeRange.isMoreRestrictiveAndNativeEndian(codeRangeA, targetEncoding.maxCompatibleCodeRange)) {
                if (strideA == 0) {
                    return TruffleString.createFromByteArray(arrayA, a.offset(), lengthA, 0, targetEncoding, codePointLengthA, codeRangeA, false);
                }
                int targetStride = Stride.fromCodeRange(codeRangeA, targetEncoding);
                byte[] array = TStringOps.arraycopyOfWithStride(node, arrayA, offsetA, lengthA, strideA, lengthA, targetStride);
                return TruffleString.createFromByteArray(array, 0, lengthA, targetStride, targetEncoding, codePointLengthA, codeRangeA, false);
            }
            assert lengthA > 0;
            if (asciiBytesInvalidProfile.profile(node,
                            (isAscii(a.encoding()) || isBytes(a.encoding())) && TStringGuards.isSupportedEncodingWithCompaction(targetEncoding) && isBuiltin(errorHandler))) {
                assert (isBrokenFixedWidth(codeRangeA) || isValidFixedWidth(codeRangeA)) && strideA == 0 && codePointLengthA == lengthA;
                boolean replacedChars = false;
                byte[] buffer = new byte[codePointLengthA];
                for (int i = 0; i < buffer.length; i++) {
                    int c = readS0(arrayA, offsetA, lengthA, i);
                    if (c > 0x7f) {
                        buffer[i] = (byte) '?';
                        replacedChars = true;
                    } else {
                        buffer[i] = (byte) c;
                    }
                    TStringConstants.truffleSafePointPoll(node, i + 1);
                }
                return TransCodeIntlNode.create(a, buffer, buffer.length, 0, targetEncoding, codePointLengthA, TSCodeRange.get7Bit(), replacedChars);
            } else {
                return transCodeIntlNode.execute(node, a, arrayA, offsetA, lengthA, strideA, codePointLengthA, codeRangeA, Encoding.get(a.encoding()), targetEncoding, errorHandler);
            }
        }
    }

    abstract static class TransCodeIntlWithErrorHandlerNode extends AbstractInternalNode {

        abstract TruffleString execute(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler);

        @Specialization(guards = {
                        "isBroken(codeRangeA) || isAsciiBytesOrLatin1(targetEncoding)",
                        "isSupportedEncodingWithCompaction(sourceEncoding)",
                        "isSupportedEncodingWithCompaction(targetEncoding)",
                        "!isBuiltin(errorHandler)"})
        static TruffleString supportedWithCustomErrorHandler(Node node,
                        AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, @SuppressWarnings("unused") int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached TruffleStringIterator.InternalNextNode iteratorNextNode,
                        @Cached TruffleStringBuilder.AppendCodePointIntlNode appendCodePointNode,
                        @Cached TruffleStringBuilder.AppendStringIntlNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringIntlNode toStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(targetEncoding);
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, sourceEncoding);
            int maxCodePoint = Encodings.maxCodePoint(targetEncoding);
            int i = 1;
            while (it.hasNext()) {
                int pos = it.getRawIndex();
                int bytePos = it.getByteIndex();
                int codepoint = iteratorNextNode.execute(node, it, sourceEncoding, DecodingErrorHandler.RETURN_NEGATIVE_UTF8_INCOMPLETE_SEQUENCES);
                if (Integer.compareUnsigned(codepoint, maxCodePoint) <= 0) {
                    appendCodePointNode.execute(node, sb, codepoint, 1, false);
                } else {
                    TranscodingErrorHandler.ReplacementString replacementString = errorHandler.apply(a, bytePos, it.getByteIndex() - bytePos, sourceEncoding, targetEncoding);
                    if (replacementString.byteLength() >= 0) {
                        it.setRawIndex(pos);
                        it.errorHandlerSkipBytes(replacementString.byteLength(), true);
                    }
                    appendStringNode.execute(node, sb, replacementString.replacement());
                }
                TStringConstants.truffleSafePointPoll(node, i++);
            }
            return toStringNode.execute(node, sb, false);
        }

        @Fallback
        static TruffleString transcode(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached TransCodeIntlNode transCodeIntlNode) {
            return transCodeIntlNode.execute(node, a, arrayA, offsetA, lengthA, strideA, codePointLengthA, codeRangeA, sourceEncoding, targetEncoding, errorHandler);
        }
    }

    @ImportStatic(TSCodeRange.class)
    abstract static class TransCodeIntlNode extends AbstractInternalNode {

        abstract TruffleString execute(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler);

        @SuppressWarnings("unused")
        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "isAscii(targetEncoding) || isBytes(targetEncoding)"})
        static TruffleString targetAscii(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode) {
            assert !is7Bit(codeRangeA) || isUTF16FE(sourceEncoding) || isUTF32FE(sourceEncoding);
            byte[] buffer = new byte[codePointLengthA];
            int length = 0;
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, sourceEncoding);
            boolean replacedChars = false;
            while (it.hasNext()) {
                int codepoint = iteratorNextNode.execute(node, it, sourceEncoding, DecodingErrorHandler.DEFAULT_UTF8_INCOMPLETE_SEQUENCES);
                if (codepoint > 0x7f) {
                    buffer[length++] = (byte) '?';
                    replacedChars = true;
                } else {
                    buffer[length++] = (byte) codepoint;
                }
                TStringConstants.truffleSafePointPoll(node, length);
            }
            return create(a, trim(buffer, length), length, 0, targetEncoding, length, TSCodeRange.get7Bit(), replacedChars);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "isLatin1(targetEncoding)"})
        static TruffleString latin1Transcode(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding, Encoding targetEncoding, TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode) {
            assert !is7Or8Bit(codeRangeA);
            byte[] buffer = new byte[codePointLengthA];
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, sourceEncoding);
            int codeRange = TSCodeRange.get7Bit();
            boolean replacedChars = false;
            int length = 0;
            while (it.hasNext()) {
                int codepoint = iteratorNextNode.execute(node, it, sourceEncoding, DecodingErrorHandler.DEFAULT_UTF8_INCOMPLETE_SEQUENCES);
                byte latin1;
                if (codepoint > 0xff) {
                    latin1 = (byte) '?';
                    replacedChars = true;
                } else {
                    latin1 = (byte) codepoint;
                }
                buffer[length++] = latin1;
                if (latin1 < 0) {
                    codeRange = TSCodeRange.get8Bit();
                }
                TStringConstants.truffleSafePointPoll(node, length);
            }
            return create(a, trim(buffer, length), length, 0, Encoding.ISO_8859_1, length, codeRange, replacedChars);
        }

        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "!isLarge(codePointLengthA)", "isUTF8(targetEncoding)"})
        static TruffleString utf8Transcode(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared TruffleStringIterator.InternalNextNode iteratorNextNode,
                        @Cached @Shared InlinedConditionProfile brokenProfile,
                        @Cached @Shared InlinedBranchProfile outOfMemoryProfile,
                        @Cached @Shared InlinedConditionProfile largeProfile) {
            return utf8Transcode(node, a, arrayA, offsetA, lengthA, strideA, codePointLengthA, codeRangeA, sourceEncoding, errorHandler, iteratorNextNode,
                            largeProfile.profile(node, isLarge(codePointLengthA)),
                            brokenProfile,
                            outOfMemoryProfile);
        }

        static boolean isLarge(int codePointLengthA) {
            return codePointLengthA > TStringConstants.MAX_ARRAY_SIZE / 4;
        }

        private static TruffleString utf8Transcode(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        TranscodingErrorHandler errorHandler,
                        TruffleStringIterator.InternalNextNode iteratorNextNode,
                        boolean isLarge, InlinedConditionProfile brokenProfile,
                        InlinedBranchProfile outOfMemoryProfile) {
            assert !is7Bit(codeRangeA) || isUTF16FE(sourceEncoding) || isUTF32FE(sourceEncoding);
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, sourceEncoding);
            boolean allowUTF16Surrogates = errorHandler == TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8;
            DecodingErrorHandler decodingErrorHandler = getDecodingErrorHandler(errorHandler);
            byte[] buffer = new byte[isLarge ? TStringConstants.MAX_ARRAY_SIZE : codePointLengthA * 4];
            int codeRange = TSCodeRange.get7Bit();
            int length = 0;
            int loopCount = 0;
            while (it.hasNext()) {
                int codepoint = iteratorNextNode.execute(node, it, sourceEncoding, decodingErrorHandler);
                boolean isSurrogate = isUTF16Surrogate(codepoint);
                boolean isGreaterMax = isGreaterThanMaxUTFCodepoint(codepoint);
                if (isSurrogate || isGreaterMax) {
                    codeRange = TSCodeRange.getBrokenMultiByte();
                    codepoint = utfReplaceInvalid(codepoint, isSurrogate, isGreaterMax, allowUTF16Surrogates);
                } else if (codepoint > 0x7f && TSCodeRange.is7Bit(codeRange)) {
                    codeRange = TSCodeRange.getValidMultiByte();
                }
                int n = Encodings.utf8EncodedSize(codepoint);
                assert isLarge || length + n <= buffer.length;
                if (isLarge && length > TStringConstants.MAX_ARRAY_SIZE - n) {
                    outOfMemoryProfile.enter(node);
                    throw InternalErrors.outOfMemory();
                }
                Encodings.utf8Encode(codepoint, buffer, length, n);
                length += n;
                TStringConstants.truffleSafePointPoll(node, ++loopCount);
            }
            final int codePointLength;
            if (isBrokenMultiByte(codeRange)) {
                long attrs = TStringOps.calcStringAttributesUTF8(node, buffer, byteArrayBaseOffset(), length, false, false, brokenProfile);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            } else {
                codePointLength = codePointLengthA;
            }
            return create(a, Arrays.copyOf(buffer, length), length, 0, Encoding.UTF_8, codePointLength, codeRange);
        }

        @Specialization(guards = {"isUTF32(sourceEncoding)", "isUTF16(targetEncoding)"})
        TruffleString utf16Fixed32Bit(AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        @SuppressWarnings("unused") Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler) {
            assert TStringGuards.isValidFixedWidth(codeRangeA) || TStringGuards.isBrokenFixedWidth(codeRangeA);
            assert strideA == 2;
            boolean allowUTF16Surrogates = isAllowUTF16SurrogatesUTF16Or32(errorHandler);
            byte[] buffer = new byte[codePointLengthA * 4];
            int length = 0;
            int codeRange = TStringGuards.isValidFixedWidth(codeRangeA) ? TSCodeRange.getValidMultiByte() : TSCodeRange.getBrokenMultiByte();
            for (int i = 0; i < lengthA; i++) {
                length += Encodings.utf16Encode(utfReplaceInvalid(TStringOps.readS2(arrayA, offsetA, lengthA, i), allowUTF16Surrogates), buffer, length);
                TStringConstants.truffleSafePointPoll(this, i + 1);
            }
            final int codePointLength;
            if (isBrokenMultiByte(codeRange)) {
                long attrs = TStringOps.calcStringAttributesUTF16(this, buffer, byteArrayBaseOffset(), length, false);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            } else {
                codePointLength = codePointLengthA;
            }
            return create(a, Arrays.copyOf(buffer, length * 2), length, 1, Encoding.UTF_16, codePointLength, codeRange);
        }

        @Specialization(guards = {"isUTF16(sourceEncoding)", "isUTF16FE(targetEncoding)"})
        static TruffleString utf16ByteSwapToFE(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        @SuppressWarnings("unused") Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        @SuppressWarnings("unused") TranscodingErrorHandler errorHandler) {
            final byte[] inflatedBuffer;
            final long inflatedOffset;
            if (strideA == 0) {
                inflatedBuffer = TStringOps.arraycopyOfWithStride(node, arrayA, offsetA, lengthA, 0, lengthA, 1);
                inflatedOffset = byteArrayBaseOffset();
            } else {
                inflatedBuffer = arrayA;
                inflatedOffset = offsetA;
            }
            byte[] buffer = new byte[lengthA << Encoding.UTF_16_FOREIGN_ENDIAN.naturalStride];
            TStringOps.byteSwapS1(node, inflatedBuffer, inflatedOffset, buffer, byteArrayBaseOffset(), lengthA);
            return create(a, buffer, lengthA, Encoding.UTF_16_FOREIGN_ENDIAN.naturalStride, Encoding.UTF_16_FOREIGN_ENDIAN, codePointLengthA, TSCodeRange.markForeignEndian(codeRangeA));
        }

        @Specialization(guards = {"isUTF16FE(sourceEncoding)", "isUTF16(targetEncoding) || isMoreRestrictiveThan(codeRangeA, targetEncoding.maxCompatibleCodeRange)"})
        static TruffleString utf16ByteSwapFromFE(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        @SuppressWarnings("unused") Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        @SuppressWarnings("unused") TranscodingErrorHandler errorHandler) {
            assert strideA == 1;
            byte[] buffer = new byte[lengthA << 1];
            TStringOps.byteSwapS1(node, arrayA, offsetA, buffer, byteArrayBaseOffset(), lengthA);
            int stride = Stride.fromCodeRangeUTF16(codeRangeA);
            if (stride == 0) {
                buffer = TStringOps.arraycopyOfWithStride(node, buffer, byteArrayBaseOffset(), lengthA, 1, lengthA, 0);
            }
            return create(a, buffer, lengthA, stride, targetEncoding, codePointLengthA, TSCodeRange.clearMarkForeignEndian(codeRangeA));
        }

        @Specialization(guards = {"isUTF32FE(sourceEncoding)", "isUTF32(targetEncoding) || isMoreRestrictiveThan(codeRangeA, targetEncoding.maxCompatibleCodeRange)"})
        static TruffleString utf32ByteSwap(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, @SuppressWarnings("unused") int codePointLengthA,
                        int codeRangeA,
                        @SuppressWarnings("unused") Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        @SuppressWarnings("unused") TranscodingErrorHandler errorHandler) {
            return utf32BESwapAndCompress(node, a, arrayA, offsetA, lengthA, strideA, codeRangeA, Encoding.UTF_32);
        }

        private static TruffleString utf32BESwapAndCompress(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codeRangeA, Encoding targetEncoding) {
            assert strideA == 2;
            byte[] buffer = new byte[lengthA << 2];
            TStringOps.byteSwapS2(node, arrayA, offsetA, buffer, byteArrayBaseOffset(), lengthA);
            int stride = Stride.fromCodeRangeUTF32(codeRangeA);
            if (stride < 2) {
                buffer = TStringOps.arraycopyOfWithStride(node, buffer, byteArrayBaseOffset(), lengthA, 2, lengthA, stride);
            }
            return create(a, buffer, lengthA, stride, targetEncoding, lengthA, TSCodeRange.clearMarkForeignEndian(codeRangeA));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isUTF32(sourceEncoding)", "isUTF32FE(targetEncoding)"})
        static TruffleString utf32ByteSwapToFE(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler) {
            final byte[] inflatedBuffer;
            final long inflatedOffset;
            if (strideA < 2) {
                inflatedBuffer = TStringOps.arraycopyOfWithStride(node, arrayA, offsetA, lengthA, strideA, lengthA, 2);
                inflatedOffset = byteArrayBaseOffset();
            } else {
                inflatedBuffer = arrayA;
                inflatedOffset = offsetA;
            }
            byte[] buffer = new byte[lengthA << Encoding.UTF_32.naturalStride];
            TStringOps.byteSwapS2(node, inflatedBuffer, inflatedOffset, buffer, byteArrayBaseOffset(), lengthA);
            return create(a, buffer, lengthA, Encoding.UTF_32_FOREIGN_ENDIAN.naturalStride, Encoding.UTF_32_FOREIGN_ENDIAN, codePointLengthA, TSCodeRange.markForeignEndian(codeRangeA));
        }

        @Specialization(guards = {"isSupportedEncodingWithCompaction(sourceEncoding) || isUTF32FE(sourceEncoding)", "!isUpTo16Bit(codeRangeA)", "isUTF16(targetEncoding)"})
        static TruffleString utf16Transcode(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode,
                        @Cached @Shared("outOfMemoryProfile") InlinedBranchProfile outOfMemoryProfile,
                        @Cached @Shared InlinedConditionProfile largeProfile) {
            return utf16Transcode(node, a, arrayA, offsetA, lengthA, strideA, codePointLengthA, codeRangeA, sourceEncoding, errorHandler, iteratorNextNode,
                            largeProfile.profile(node, isLarge(codePointLengthA)),
                            outOfMemoryProfile);
        }

        private static TruffleString utf16Transcode(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        TranscodingErrorHandler errorHandler,
                        TruffleStringIterator.InternalNextNode iteratorNextNode,
                        boolean isLarge,
                        InlinedBranchProfile outOfMemoryProfile) {
            assert TStringGuards.isValidOrBrokenMultiByte(codeRangeA) || isUTF32FE(sourceEncoding) && (TStringGuards.isValidFixedWidth(codeRangeA) || TStringGuards.isBrokenFixedWidth(codeRangeA));
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, sourceEncoding);
            boolean allowUTF16Surrogates = isAllowUTF16SurrogatesUTF16Or32(errorHandler);
            byte[] buffer = new byte[codePointLengthA];
            int codePointLength = codePointLengthA;
            int length = 0;
            int codeRange = TSCodeRange.get7Bit();
            DecodingErrorHandler decodingErrorHandler = getDecodingErrorHandler(errorHandler);
            while (it.hasNext()) {
                int curIndex = it.getRawIndex();
                int codepoint = iteratorNextNode.execute(node, it, sourceEncoding, decodingErrorHandler);
                if (codepoint > 0xff) {
                    buffer = TStringOps.arraycopyOfWithStride(node, buffer, byteArrayBaseOffset(), length, 0, codePointLengthA, 1);
                    codeRange = TSCodeRange.get16Bit();
                    it.setRawIndex(curIndex);
                    break;
                }
                if (codepoint > 0x7f) {
                    codeRange = TSCodeRange.get8Bit();
                }
                buffer[length++] = (byte) codepoint;
                TStringConstants.truffleSafePointPoll(node, length);
            }
            if (!it.hasNext()) {
                assert length == codePointLengthA;
                return create(a, buffer, length, 0, Encoding.UTF_16, codePointLengthA, codeRange);
            }
            while (it.hasNext()) {
                int curIndex = it.getRawIndex();
                int codepoint = iteratorNextNode.execute(node, it, sourceEncoding, decodingErrorHandler);
                if (codepoint > 0xffff) {
                    buffer = Arrays.copyOf(buffer, isLarge ? TStringConstants.MAX_ARRAY_SIZE : buffer.length * 2);
                    codeRange = TSCodeRange.commonCodeRange(codeRange, TSCodeRange.getValidMultiByte());
                    it.setRawIndex(curIndex);
                    break;
                }
                boolean isSurrogate = isUTF16Surrogate(codepoint);
                if (isSurrogate) {
                    codepoint = utfReplaceInvalid(codepoint, true, false, allowUTF16Surrogates);
                    codeRange = TSCodeRange.getBrokenMultiByte();
                }
                writeToByteArrayS1(buffer, length++, codepoint);
                TStringConstants.truffleSafePointPoll(node, length);
            }
            codePointLength = length;
            if (!it.hasNext()) {
                if (isBrokenMultiByte(codeRange)) {
                    long attrs = TStringOps.calcStringAttributesUTF16(node, buffer, byteArrayBaseOffset(), length, false);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                    codeRange = StringAttributes.getCodeRange(attrs);
                }
                return create(a, trim(buffer, length * 2), length, 1, Encoding.UTF_16, codePointLength, codeRange);
            }
            int loopCount = 0;
            while (it.hasNext()) {
                int codepoint = iteratorNextNode.execute(node, it, sourceEncoding, decodingErrorHandler);
                boolean isSurrogate = isUTF16Surrogate(codepoint);
                boolean isGreaterMax = isGreaterThanMaxUTFCodepoint(codepoint);
                if (isSurrogate || isGreaterMax) {
                    codeRange = TSCodeRange.getBrokenMultiByte();
                    codepoint = utfReplaceInvalid(codepoint, isSurrogate, isGreaterMax, allowUTF16Surrogates);
                }
                if (isLarge && length + Encodings.utf16EncodedSize(codepoint) > TStringConstants.MAX_ARRAY_SIZE_S1) {
                    outOfMemoryProfile.enter(node);
                    throw InternalErrors.outOfMemory();
                }
                length += Encodings.utf16Encode(codepoint, buffer, length);
                codePointLength++;
                TStringConstants.truffleSafePointPoll(node, ++loopCount);
            }
            if (isBrokenMultiByte(codeRange)) {
                long attrs = TStringOps.calcStringAttributesUTF16(node, buffer, byteArrayBaseOffset(), length, false);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            }
            return create(a, Arrays.copyOf(buffer, length * 2), length, 1, Encoding.UTF_16, codePointLength, codeRange);
        }

        @Specialization(guards = {"isSupportedEncodingWithCompaction(sourceEncoding) || isUTF32FE(sourceEncoding)", "!isUTF16(sourceEncoding)", "isUTF16FE(targetEncoding)"})
        static TruffleString utf16FETranscode(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode,
                        @Cached @Shared("outOfMemoryProfile") InlinedBranchProfile outOfMemoryProfile,
                        @Cached @Shared InlinedConditionProfile largeProfile) {
            return utf16FETranscode(node, a, arrayA, offsetA, lengthA, strideA, codePointLengthA, codeRangeA, sourceEncoding, errorHandler, iteratorNextNode,
                            largeProfile.profile(node, isLarge(codePointLengthA)),
                            outOfMemoryProfile);
        }

        private static TruffleString utf16FETranscode(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        TranscodingErrorHandler errorHandler,
                        TruffleStringIterator.InternalNextNode iteratorNextNode,
                        boolean isLarge,
                        InlinedBranchProfile outOfMemoryProfile) {
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, sourceEncoding);
            boolean allowUTF16Surrogates = isAllowUTF16SurrogatesUTF16Or32(errorHandler);
            byte[] buffer = new byte[isLarge ? TStringConstants.MAX_ARRAY_SIZE : codePointLengthA << 2];
            int codePointLength = 0;
            int length = 0;
            int codeRange = TSCodeRange.get7Bit();
            DecodingErrorHandler decodingErrorHandler = getDecodingErrorHandler(errorHandler);
            int loopCount = 0;
            while (it.hasNext()) {
                int codepoint = iteratorNextNode.execute(node, it, sourceEncoding, decodingErrorHandler);
                boolean isSurrogate = isUTF16Surrogate(codepoint);
                boolean isGreaterMax = isGreaterThanMaxUTFCodepoint(codepoint);
                if (isSurrogate || isGreaterMax) {
                    codeRange = TSCodeRange.getBrokenMultiByte();
                    codepoint = utfReplaceInvalid(codepoint, isSurrogate, isGreaterMax, allowUTF16Surrogates);
                } else {
                    codeRange = TSCodeRange.commonCodeRange(codeRange, TSCodeRange.fromValidCodePoint(codepoint, false));
                }
                if (isLarge && length + Encodings.utf16EncodedSize(codepoint) > TStringConstants.MAX_ARRAY_SIZE_S1) {
                    outOfMemoryProfile.enter(node);
                    throw InternalErrors.outOfMemory();
                }
                length += Encodings.utf16FEEncode(codepoint, buffer, length);
                codePointLength++;
                TStringConstants.truffleSafePointPoll(node, ++loopCount);
            }
            if (isBroken(codeRange)) {
                long attrs = TStringOps.calcStringAttributesUTF16FE(node, buffer, byteArrayBaseOffset(), length);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            }
            return create(a, Arrays.copyOf(buffer, length * 2), length, Encoding.UTF_16_FOREIGN_ENDIAN.naturalStride, Encoding.UTF_16_FOREIGN_ENDIAN, codePointLength,
                            TSCodeRange.markForeignEndian(codeRange));
        }

        @Specialization(guards = {"!isUTF16(sourceEncoding)",
                        "isSupportedEncodingWithCompaction(sourceEncoding) || isUTF16FE(sourceEncoding)", "!isFixedWidth(codeRangeA)", "!isLarge(codePointLengthA)", "isUTF32(targetEncoding)"})
        static TruffleString utf32TranscodeRegular(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode) {
            return utf32Transcode(node, a, arrayA, offsetA, lengthA, strideA, codePointLengthA, codeRangeA, sourceEncoding, errorHandler, iteratorNextNode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isSupportedEncodingWithCompaction(sourceEncoding) || isUTF16FE(sourceEncoding)", "!isFixedWidth(codeRangeA)", "isLarge(codePointLengthA)",
                        "isUTF32(targetEncoding)"})
        static TruffleString utf32TranscodeLarge(AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler) {
            throw InternalErrors.outOfMemory();
        }

        @Specialization(guards = {"isUTF16(sourceEncoding)", "!isFixedWidth(codeRangeA)", "!isLarge(codePointLengthA)", "isUTF32(targetEncoding)"})
        static TruffleString utf32TranscodeUTF16(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode) {
            assert containsSurrogates(a);
            boolean allowUTF16Surrogates = isAllowUTF16SurrogatesUTF16Or32(errorHandler);
            DecodingErrorHandler decodingErrorHandler = getDecodingErrorHandler(errorHandler);
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, sourceEncoding);
            byte[] buffer = new byte[codePointLengthA << 2];
            int length = 0;
            while (it.hasNext()) {
                writeToByteArrayS2(buffer, length++, utfReplaceInvalid(iteratorNextNode.execute(node, it, sourceEncoding, decodingErrorHandler), allowUTF16Surrogates));
                TStringConstants.truffleSafePointPoll(node, length);
            }
            assert length == codePointLengthA;
            boolean isBroken = isBrokenMultiByte(codeRangeA);
            int codePointLength = codePointLengthA;
            int codeRange = isBroken ? TSCodeRange.getBrokenFixedWidth() : TSCodeRange.getValidFixedWidth();
            if (isBroken && !allowUTF16Surrogates) {
                long attrs = TStringOps.calcStringAttributesUTF16(node, buffer, byteArrayBaseOffset(), length, false);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            }
            return create(a, buffer, length, 2, Encoding.UTF_32, codePointLength, codeRange);
        }

        private static TruffleString utf32Transcode(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        TranscodingErrorHandler errorHandler,
                        TruffleStringIterator.InternalNextNode iteratorNextNode) {
            assert TStringGuards.isValidOrBrokenMultiByte(codeRangeA);
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, sourceEncoding);
            DecodingErrorHandler decodingErrorHandler = getDecodingErrorHandler(errorHandler);
            byte[] buffer = new byte[codePointLengthA];
            int offsetBuf = byteArrayBaseOffset();
            int length = 0;
            int codeRange = TSCodeRange.get7Bit();
            int codepoint = 0;
            while (it.hasNext()) {
                int curIndex = it.getRawIndex();
                codepoint = iteratorNextNode.execute(node, it, sourceEncoding, decodingErrorHandler);
                if (codepoint > 0xff) {
                    if (isUTF16Surrogate(codepoint)) {
                        buffer = TStringOps.arraycopyOfWithStride(node, buffer, offsetBuf, length, 0, codePointLengthA, 2);
                        codeRange = TSCodeRange.getBrokenFixedWidth();
                    } else {
                        buffer = TStringOps.arraycopyOfWithStride(node, buffer, offsetBuf, length, 0, codePointLengthA, 1);
                        codeRange = TSCodeRange.get16Bit();
                    }
                    it.setRawIndex(curIndex);
                    break;
                }
                if (codepoint > 0x7f) {
                    codeRange = TSCodeRange.get8Bit();
                }
                buffer[length++] = (byte) codepoint;
                TStringConstants.truffleSafePointPoll(node, length);
            }
            if (!it.hasNext()) {
                assert length == codePointLengthA;
                return create(a, buffer, length, 0, Encoding.UTF_32, codePointLengthA, codeRange);
            }
            if (is16Bit(codeRange)) {
                while (it.hasNext()) {
                    int curIndex = it.getRawIndex();
                    codepoint = iteratorNextNode.execute(node, it, sourceEncoding, decodingErrorHandler);
                    if (codepoint > 0xffff || isUTF16Surrogate(codepoint)) {
                        buffer = TStringOps.arraycopyOfWithStride(node, buffer, offsetBuf, length, 1, codePointLengthA, 2);
                        codeRange = Encodings.isValidUnicodeCodepoint(codepoint) ? TSCodeRange.getValidFixedWidth() : TSCodeRange.getBrokenFixedWidth();
                        it.setRawIndex(curIndex);
                        break;
                    }
                    writeToByteArrayS1(buffer, length++, codepoint);
                    TStringConstants.truffleSafePointPoll(node, length);
                }
            }
            if (!it.hasNext()) {
                assert length == codePointLengthA || sourceEncoding == Encoding.UTF_8 && isBrokenMultiByte(codeRangeA);
                return create(a, trim(buffer, length * 2), length, 1, Encoding.UTF_32, length, codeRange);
            }
            while (it.hasNext()) {
                codepoint = iteratorNextNode.execute(node, it, sourceEncoding, decodingErrorHandler);
                boolean isSurrogate = isUTF16Surrogate(codepoint);
                boolean isGreaterMax = isGreaterThanMaxUTFCodepoint(codepoint);
                if (isSurrogate || isGreaterMax) {
                    codeRange = TSCodeRange.getBrokenFixedWidth();
                    codepoint = utfReplaceInvalid(codepoint, isSurrogate, isGreaterMax, isAllowUTF16SurrogatesUTF16Or32(errorHandler));
                }
                writeToByteArrayS2(buffer, length++, codepoint);
                TStringConstants.truffleSafePointPoll(node, length);
            }
            return create(a, trim(buffer, length * 4), length, 2, Encoding.UTF_32, length, codeRange);
        }

        @Specialization(guards = {"isSupportedEncodingWithCompaction(sourceEncoding) || isUTF16FE(sourceEncoding)", "!isUTF32(sourceEncoding)", "!isLarge(codePointLengthA)",
                        "isUTF32FE(targetEncoding)"})
        static TruffleString utf32FETranscodeRegular(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode) {
            return utf32FETranscode(node, a, arrayA, offsetA, lengthA, strideA, codePointLengthA, codeRangeA, sourceEncoding, errorHandler, iteratorNextNode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isSupportedEncodingWithCompaction(sourceEncoding) || isUTF16FE(sourceEncoding)", "!isUTF32(sourceEncoding)", "isLarge(codePointLengthA)",
                        "isUTF32FE(targetEncoding)"})
        static TruffleString utf32FETranscodeLarge(AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler) {
            throw InternalErrors.outOfMemory();
        }

        private static TruffleString utf32FETranscode(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        TranscodingErrorHandler errorHandler,
                        TruffleStringIterator.InternalNextNode iteratorNextNode) {
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, offsetA, lengthA, strideA, codeRangeA, sourceEncoding);
            DecodingErrorHandler decodingErrorHandler = getDecodingErrorHandler(errorHandler);
            byte[] buffer = new byte[codePointLengthA << 2];
            int length = 0;
            int codeRange = TSCodeRange.get7Bit();
            while (it.hasNext()) {
                int codepoint = iteratorNextNode.execute(node, it, sourceEncoding, decodingErrorHandler);
                boolean isSurrogate = isUTF16Surrogate(codepoint);
                boolean isGreaterMax = isGreaterThanMaxUTFCodepoint(codepoint);
                if (isSurrogate || isGreaterMax) {
                    codeRange = TSCodeRange.getBrokenFixedWidth();
                    codepoint = utfReplaceInvalid(codepoint, isSurrogate, isGreaterMax, isAllowUTF16SurrogatesUTF16Or32(errorHandler));
                } else {
                    codeRange = TSCodeRange.commonCodeRange(codeRange, TSCodeRange.fromValidCodePoint(codepoint, true));
                }
                writeToByteArrayS2(buffer, length++, Integer.reverseBytes(codepoint));
                TStringConstants.truffleSafePointPoll(node, length);
            }
            return create(a, trim(buffer, length << 2), length, Encoding.UTF_32_FOREIGN_ENDIAN.naturalStride, Encoding.UTF_32_FOREIGN_ENDIAN, length, TSCodeRange.markForeignEndian(codeRange));
        }

        @SuppressWarnings("unused")
        @TruffleBoundary
        @Specialization(guards = {"isUnsupportedEncoding(sourceEncoding) || isUnsupportedEncoding(targetEncoding)"})
        static TruffleString unsupported(Node node, AbstractTruffleString a, byte[] arrayA, long offsetA, int lengthA, int strideA, int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler) {
            return JCodings.getInstance().transcode(node, a, arrayA, codePointLengthA, targetEncoding, errorHandler);
        }

        private static byte[] trim(byte[] array, int byteLength) {
            if (byteLength != array.length) {
                return Arrays.copyOf(array, byteLength);
            }
            return array;
        }

        private static DecodingErrorHandler getDecodingErrorHandler(TranscodingErrorHandler errorHandler) {
            assert errorHandler == TranscodingErrorHandler.DEFAULT || errorHandler == TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8;
            CompilerAsserts.partialEvaluationConstant(errorHandler);
            DecodingErrorHandler decodingErrorHandler = errorHandler == TranscodingErrorHandler.DEFAULT ? DecodingErrorHandler.DEFAULT_UTF8_INCOMPLETE_SEQUENCES
                            : DecodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8;
            CompilerAsserts.partialEvaluationConstant(decodingErrorHandler);
            return decodingErrorHandler;
        }

        static boolean isAllowUTF16SurrogatesUTF16Or32(TranscodingErrorHandler errorHandler) {
            return isBuiltin(errorHandler);
        }

        private static boolean isGreaterThanMaxUTFCodepoint(int codepoint) {
            return Integer.toUnsignedLong(codepoint) > Character.MAX_CODE_POINT;
        }

        private static int utfReplaceInvalid(int codepoint, boolean allowUTF16Surrogates) {
            return utfReplaceInvalid(codepoint, isUTF16Surrogate(codepoint), isGreaterThanMaxUTFCodepoint(codepoint), allowUTF16Surrogates);
        }

        private static int utfReplaceInvalid(int codepoint, boolean isSurrogate, boolean isGreaterThanMaxCodepoint, boolean allowUTF16Surrogates) {
            if (isGreaterThanMaxCodepoint || isSurrogate && !allowUTF16Surrogates) {
                return Encodings.invalidCodepoint();
            }
            return codepoint;
        }

        private static TruffleString create(AbstractTruffleString a, byte[] buffer, int length, int stride, Encoding encoding, int codePointLength, int codeRange) {
            return create(a, buffer, length, stride, encoding, codePointLength, codeRange, false);
        }

        private static TruffleString create(AbstractTruffleString a, byte[] buffer, int length, int stride, Encoding encoding, int codePointLength, int codeRange, boolean replacedChars) {
            return TruffleString.createFromByteArray(buffer, length, stride, encoding, codePointLength, codeRange,
                            isBroken(a.codeRange()) || isBroken(codeRange) || a.isMutable() || replacedChars);
        }

        @TruffleBoundary
        private static boolean containsSurrogates(AbstractTruffleString a) {
            CompilerAsserts.neverPartOfCompilation();
            for (int i = 0; i < a.length(); i++) {
                if (isUTF16Surrogate(a.readCharUTF16Uncached(i))) {
                    return true;
                }
            }
            return false;
        }
    }
}
