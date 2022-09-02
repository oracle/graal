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

import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import static com.oracle.truffle.api.dsl.Cached.Shared;
import static com.oracle.truffle.api.strings.AbstractTruffleString.checkArrayRange;
import static com.oracle.truffle.api.strings.AbstractTruffleString.checkByteLengthUTF16;
import static com.oracle.truffle.api.strings.AbstractTruffleString.checkByteLengthUTF32;
import static com.oracle.truffle.api.strings.TStringGuards.indexOfCannotMatch;
import static com.oracle.truffle.api.strings.TStringGuards.is16Bit;
import static com.oracle.truffle.api.strings.TStringGuards.is7Bit;
import static com.oracle.truffle.api.strings.TStringGuards.is7Or8Bit;
import static com.oracle.truffle.api.strings.TStringGuards.is8Bit;
import static com.oracle.truffle.api.strings.TStringGuards.isAscii;
import static com.oracle.truffle.api.strings.TStringGuards.isAsciiBytesOrLatin1;
import static com.oracle.truffle.api.strings.TStringGuards.isBrokenFixedWidth;
import static com.oracle.truffle.api.strings.TStringGuards.isBrokenMultiByte;
import static com.oracle.truffle.api.strings.TStringGuards.isBytes;
import static com.oracle.truffle.api.strings.TStringGuards.isFixedWidth;
import static com.oracle.truffle.api.strings.TStringGuards.isStride0;
import static com.oracle.truffle.api.strings.TStringGuards.isStride1;
import static com.oracle.truffle.api.strings.TStringGuards.isStride2;
import static com.oracle.truffle.api.strings.TStringGuards.isSupportedEncoding;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16Or32;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF32;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF8;
import static com.oracle.truffle.api.strings.TStringGuards.isUnsupportedEncoding;
import static com.oracle.truffle.api.strings.TStringGuards.isValidFixedWidth;
import static com.oracle.truffle.api.strings.TStringGuards.isValidMultiByte;
import static com.oracle.truffle.api.strings.TStringOps.readS0;
import static com.oracle.truffle.api.strings.TStringOps.writeToByteArray;
import static com.oracle.truffle.api.strings.TStringOpsNodes.RawIndexOfStringNode;
import static com.oracle.truffle.api.strings.TStringOpsNodes.RawLastIndexOfStringNode;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GeneratePackagePrivate;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.AbstractTruffleString.NativePointer;
import com.oracle.truffle.api.strings.TruffleString.Encoding;
import com.oracle.truffle.api.strings.TruffleString.ErrorHandling;

final class TStringInternalNodes {

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class GetCodeRangeNode extends Node {

        abstract int execute(AbstractTruffleString a);

        @Specialization
        int immutable(TruffleString a) {
            return a.codeRange();
        }

        @Specialization(guards = "!isUnknown(a.codeRange())")
        int mutableCacheHit(MutableTruffleString a) {
            return a.codeRange();
        }

        @Specialization(guards = "isUnknown(a.codeRange())")
        int mutableCacheMiss(MutableTruffleString a,
                        @Cached MutableTruffleString.CalcLazyAttributesNode calcLazyAttributesNode) {
            calcLazyAttributesNode.execute(a);
            return a.codeRange();
        }

        static GetCodeRangeNode getUncached() {
            return TStringInternalNodesFactory.GetCodeRangeNodeGen.getUncached();
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class GetCodePointLengthNode extends Node {

        abstract int execute(AbstractTruffleString a);

        @Specialization
        int immutable(TruffleString a) {
            return a.codePointLength();
        }

        @Specialization(guards = "a.codePointLength() >= 0")
        int mutableCacheHit(MutableTruffleString a) {
            return a.codePointLength();
        }

        @Specialization(guards = "a.codePointLength() < 0")
        int mutableCacheMiss(MutableTruffleString a,
                        @Cached MutableTruffleString.CalcLazyAttributesNode calcLazyAttributesNode) {
            calcLazyAttributesNode.execute(a);
            return a.codePointLength();
        }

        static GetCodePointLengthNode getUncached() {
            return TStringInternalNodesFactory.GetCodePointLengthNodeGen.getUncached();
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class FromBufferWithStringCompactionNode extends Node {

        abstract TruffleString execute(Object arrayA, int offsetA, int byteLength, Encoding encoding, boolean copy, boolean isCacheHead);

        @Specialization
        TruffleString fromBufferWithStringCompaction(Object arrayA, int offsetA, int byteLength, Encoding encoding, boolean copy, boolean isCacheHead,
                        @Cached ConditionProfile asciiLatinBytesProfile,
                        @Cached ConditionProfile utf8Profile,
                        @Cached ConditionProfile utf8BrokenProfile,
                        @Cached ConditionProfile utf16Profile,
                        @Cached ConditionProfile utf16CompactProfile,
                        @Cached ConditionProfile utf32Profile,
                        @Cached ConditionProfile utf32Compact0Profile,
                        @Cached ConditionProfile utf32Compact1Profile,
                        @Cached ConditionProfile exoticValidProfile,
                        @Cached ConditionProfile exoticFixedWidthProfile) {
            final int offset;
            final int length;
            final int stride;
            final int codePointLength;
            final int codeRange;
            final Object array;
            if (utf16Profile.profile(isUTF16(encoding))) {
                checkByteLengthUTF16(byteLength);
                length = byteLength >> 1;
                long attrs = TStringOps.calcStringAttributesUTF16(this, arrayA, offsetA, length, false);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
                stride = Stride.fromCodeRangeUTF16(codeRange);
                if (copy || stride == 0) {
                    offset = 0;
                    array = new byte[length << stride];
                    if (utf16CompactProfile.profile(stride == 0)) {
                        TStringOps.arraycopyWithStride(this, arrayA, offsetA, 1, 0, array, offset, 0, 0, length);
                    } else {
                        TStringOps.arraycopyWithStride(this, arrayA, offsetA, 1, 0, array, offset, 1, 0, length);
                    }
                } else {
                    offset = offsetA;
                    array = arrayA;
                }
            } else if (utf32Profile.profile(isUTF32(encoding))) {
                checkByteLengthUTF32(byteLength);
                length = byteLength >> 2;
                codeRange = TStringOps.calcStringAttributesUTF32(this, arrayA, offsetA, length);
                codePointLength = length;
                stride = Stride.fromCodeRangeUTF32(codeRange);
                if (copy || stride < 2) {
                    offset = 0;
                    array = new byte[length << stride];
                    if (utf32Compact0Profile.profile(stride == 0)) {
                        TStringOps.arraycopyWithStride(this, arrayA, offsetA, 2, 0, array, offset, 0, 0, length);
                    } else if (utf32Compact1Profile.profile(stride == 1)) {
                        TStringOps.arraycopyWithStride(this, arrayA, offsetA, 2, 0, array, offset, 1, 0, length);
                    } else {
                        TStringOps.arraycopyWithStride(this, arrayA, offsetA, 2, 0, array, offset, 2, 0, length);
                    }
                } else {
                    offset = offsetA;
                    array = arrayA;
                }
            } else {
                length = byteLength;
                stride = 0;
                if (utf8Profile.profile(isUTF8(encoding))) {
                    long attrs = TStringOps.calcStringAttributesUTF8(this, arrayA, offsetA, length, false, false, utf8BrokenProfile);
                    codeRange = StringAttributes.getCodeRange(attrs);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                } else if (asciiLatinBytesProfile.profile(isAsciiBytesOrLatin1(encoding))) {
                    int cr = TStringOps.calcStringAttributesLatin1(this, arrayA, offsetA, length);
                    codeRange = is8Bit(cr) ? TSCodeRange.asciiLatinBytesNonAsciiCodeRange(encoding) : cr;
                    codePointLength = length;
                } else {
                    if (arrayA instanceof NativePointer) {
                        ((NativePointer) arrayA).materializeByteArray(offsetA, length << stride, ConditionProfile.getUncached());
                    }
                    long attrs = JCodings.getInstance().calcStringAttributes(this, arrayA, offsetA, length, encoding, 0, exoticValidProfile, exoticFixedWidthProfile);
                    codeRange = StringAttributes.getCodeRange(attrs);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                }
                if (copy) {
                    offset = 0;
                    array = TStringOps.arraycopyOfWithStride(this, arrayA, offsetA, length, 0, length, 0);
                } else {
                    offset = offsetA;
                    array = arrayA;
                }
            }
            return TruffleString.createFromArray(array, offset, length, stride, encoding, codePointLength, codeRange, isCacheHead);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class FromBufferWithStringCompactionKnownAttributesNode extends Node {

        final TruffleString execute(AbstractTruffleString a, Encoding encoding) {
            return execute(a, true, encoding);
        }

        abstract TruffleString execute(AbstractTruffleString a, boolean isCacheHead, Encoding encoding);

        @Specialization
        TruffleString fromBufferWithStringCompaction(AbstractTruffleString a, boolean isCacheHead, Encoding encoding,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached ConditionProfile utf16Profile,
                        @Cached ConditionProfile utf16CompactProfile,
                        @Cached ConditionProfile utf32Profile,
                        @Cached ConditionProfile utf32Compact0Profile,
                        @Cached ConditionProfile utf32Compact1Profile) {
            final int codeRange = getCodeRangeNode.execute(a);
            a.looseCheckEncoding(encoding, codeRange);
            final int length = a.length();
            if (length == 0) {
                return encoding.getEmpty();
            }
            final Object arrayA = a.data();
            assert arrayA instanceof byte[] || arrayA instanceof NativePointer;
            final int offsetA = a.offset();
            final int strideA = a.stride();
            final int offset = 0;
            final int stride;
            final Object array;
            if (utf16Profile.profile(isUTF16(encoding))) {
                stride = Stride.fromCodeRangeUTF16(codeRange);
                array = new byte[length << stride];
                if (utf16CompactProfile.profile(strideA == 1 && stride == 0)) {
                    TStringOps.arraycopyWithStride(this, arrayA, offsetA, 1, 0, array, offset, 0, 0, length);
                } else {
                    assert stride == strideA;
                    TStringOps.arraycopyWithStride(this, arrayA, offsetA, 0, 0, array, offset, 0, 0, length << stride);
                }
            } else if (utf32Profile.profile(isUTF32(encoding))) {
                stride = Stride.fromCodeRangeUTF32(codeRange);
                array = new byte[length << stride];
                if (utf32Compact0Profile.profile(strideA == 2 && stride == 0)) {
                    TStringOps.arraycopyWithStride(this, arrayA, offsetA, 2, 0, array, offset, 0, 0, length);
                } else if (utf32Compact1Profile.profile(strideA == 2 && stride == 1)) {
                    TStringOps.arraycopyWithStride(this, arrayA, offsetA, 2, 0, array, offset, 1, 0, length);
                } else {
                    assert stride == strideA;
                    TStringOps.arraycopyWithStride(this, arrayA, offsetA, 0, 0, array, offset, 0, 0, length << stride);
                }
            } else {
                stride = 0;
                array = TStringOps.arraycopyOfWithStride(this, arrayA, offsetA, length, 0, length, 0);
            }
            int codePointLength = getCodePointLengthNode.execute(a);
            return TruffleString.createFromArray(array, offset, length, stride, encoding, codePointLength, codeRange, isCacheHead);
        }

        static FromBufferWithStringCompactionKnownAttributesNode getUncached() {
            return TStringInternalNodesFactory.FromBufferWithStringCompactionKnownAttributesNodeGen.getUncached();
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class FromNativePointerNode extends Node {

        abstract TruffleString execute(NativePointer pointer, int byteOffset, int byteLength, Encoding encoding, boolean isCacheHead);

        @Specialization
        TruffleString fromNativePointerInternal(NativePointer pointer, int byteOffset, int byteLength, Encoding encoding, boolean isCacheHead,
                        @Cached ConditionProfile asciiLatinBytesProfile,
                        @Cached ConditionProfile utf8Profile,
                        @Cached ConditionProfile utf8BrokenProfile,
                        @Cached ConditionProfile utf16Profile,
                        @Cached ConditionProfile utf32Profile,
                        @Cached ConditionProfile exoticValidProfile,
                        @Cached ConditionProfile exoticFixedWidthProfile) {
            final int length;
            final int stride;
            final int codePointLength;
            final int codeRange;
            if (utf16Profile.profile(isUTF16(encoding))) {
                checkByteLengthUTF16(byteLength);
                length = byteLength >> 1;
                long attrs = TStringOps.calcStringAttributesUTF16(this, pointer, byteOffset, length, false);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
                stride = 1;
            } else if (utf32Profile.profile(isUTF32(encoding))) {
                checkByteLengthUTF32(byteLength);
                length = byteLength >> 2;
                codeRange = TStringOps.calcStringAttributesUTF32(this, pointer, byteOffset, length);
                codePointLength = length;
                stride = 2;
            } else {
                length = byteLength;
                stride = 0;
                if (utf8Profile.profile(isUTF8(encoding))) {
                    long attrs = TStringOps.calcStringAttributesUTF8(this, pointer, byteOffset, length, false, false, utf8BrokenProfile);
                    codeRange = StringAttributes.getCodeRange(attrs);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                } else if (asciiLatinBytesProfile.profile(isAsciiBytesOrLatin1(encoding))) {
                    int cr = TStringOps.calcStringAttributesLatin1(this, pointer, byteOffset, length);
                    codeRange = is8Bit(cr) ? TSCodeRange.asciiLatinBytesNonAsciiCodeRange(encoding) : cr;
                    codePointLength = length;
                } else {
                    pointer.materializeByteArray(byteOffset, byteLength, ConditionProfile.getUncached());
                    long attrs = JCodings.getInstance().calcStringAttributes(this, pointer, byteOffset, length, encoding, 0, exoticValidProfile, exoticFixedWidthProfile);
                    codeRange = StringAttributes.getCodeRange(attrs);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                }
            }
            return TruffleString.createFromArray(pointer, byteOffset, length, stride, encoding, codePointLength, codeRange, isCacheHead);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class ByteLengthOfCodePointNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling);

        @SuppressWarnings("unused")
        @Specialization(guards = {"isFixedWidth(codeRangeA)", "isBestEffort(errorHandling)"})
        int doFixed(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling) {
            return 1 << encoding.naturalStride;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isUpToValidFixedWidth(codeRangeA)", "isReturnNegative(errorHandling)"})
        int doFixedValidReturnNegative(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling) {
            return 1 << encoding.naturalStride;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isAscii(encoding)", "isBrokenFixedWidth(codeRangeA)", "isReturnNegative(errorHandling)"})
        int doASCIIBrokenReturnNegative(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling) {
            assert isStride0(a);
            return readS0(a, arrayA, index) < 0x80 ? 1 : -1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isUTF32(encoding)", "isBrokenFixedWidth(codeRangeA)", "isReturnNegative(errorHandling)"})
        int doUTF32BrokenReturnNegative(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling,
                        @Cached CodePointAtRawNode codePointAtRawNode) {
            return codePointAtRawNode.execute(a, arrayA, codeRangeA, encoding, index, ErrorHandling.RETURN_NEGATIVE) < 0 ? -1 : 4;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isUTF8(encoding)", "isValidMultiByte(codeRangeA)"})
        int utf8Valid(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling) {
            assert isStride0(a);
            int firstByte = readS0(a, arrayA, index);
            return firstByte <= 0x7f ? 1 : Encodings.utf8CodePointLength(firstByte);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isUTF8(encoding)", "isBrokenMultiByte(codeRangeA)"})
        int utf8Broken(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling) {
            assert isStride0(a);
            return Encodings.utf8GetCodePointLength(a, arrayA, index, errorHandling);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isUTF16(encoding)", "isValidMultiByte(codeRangeA)"})
        int utf16Valid(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling) {
            assert isStride1(a);
            return Encodings.isUTF16HighSurrogate(TStringOps.readS1(a, arrayA, index)) ? 4 : 2;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isUTF16(encoding)", "isBrokenMultiByte(codeRangeA)"})
        int utf16Broken(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling) {
            assert isStride1(a);
            return Encodings.utf16BrokenGetCodePointByteLength(a, arrayA, index, errorHandling);
        }

        @SuppressWarnings("unused")
        @TruffleBoundary
        @Specialization(guards = "isUnsupportedEncoding(encoding)")
        int unsupported(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling) {
            assert isStride0(a);
            JCodings.Encoding jCoding = JCodings.getInstance().get(encoding);
            int cpLength = JCodings.getInstance().getCodePointLength(jCoding, JCodings.asByteArray(arrayA), a.byteArrayOffset() + index, a.byteArrayOffset() + a.length());
            int regionLength = a.length() - index;
            if (errorHandling == ErrorHandling.BEST_EFFORT) {
                if (cpLength > 0 && cpLength <= regionLength) {
                    return cpLength;
                } else {
                    return Math.min(JCodings.getInstance().minLength(jCoding), regionLength);
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

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class RawIndexToCodePointIndexNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int byteOffset, int index);

        @SuppressWarnings("unused")
        @Specialization(guards = "isFixedWidth(codeRangeA)")
        int doFixed(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int byteOffset, int index) {
            return index;
        }

        @Specialization(guards = {"isUTF8(encoding)", "isValidMultiByte(codeRangeA)"})
        int utf8Valid(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int byteOffset, int index,
                        @Cached ConditionProfile brokenProfile) {
            return StringAttributes.getCodePointLength(TStringOps.calcStringAttributesUTF8(this, arrayA, a.offset() + byteOffset, index, true, byteOffset + index == a.length(), brokenProfile));
        }

        @Specialization(guards = {"isUTF8(encoding)", "isBrokenMultiByte(codeRangeA)"})
        int utf8Broken(@SuppressWarnings("unused") AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int byteOffset,
                        int index,
                        @Cached ConditionProfile brokenProfile) {
            return StringAttributes.getCodePointLength(TStringOps.calcStringAttributesUTF8(this, arrayA, a.offset() + byteOffset, index, false, false, brokenProfile));
        }

        @Specialization(guards = {"isUTF16(encoding)", "isValidMultiByte(codeRangeA)"})
        int utf16Valid(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int byteOffset, int index) {
            assert isStride1(a);
            return StringAttributes.getCodePointLength(TStringOps.calcStringAttributesUTF16(this, arrayA, a.offset() + byteOffset, index, true));
        }

        @Specialization(guards = {"isUTF16(encoding)", "isBrokenMultiByte(codeRangeA)"})
        int utf16Broken(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int byteOffset, int index) {
            assert isStride1(a);
            return StringAttributes.getCodePointLength(TStringOps.calcStringAttributesUTF16(this, arrayA, a.offset() + byteOffset, index, false));
        }

        @TruffleBoundary
        @Specialization(guards = "isUnsupportedEncoding(encoding)")
        int unsupported(@SuppressWarnings("unused") AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, Encoding encoding, int byteOffset, int index,
                        @Cached ConditionProfile validProfile,
                        @Cached ConditionProfile fixedWidthProfile) {
            return StringAttributes.getCodePointLength(JCodings.getInstance().calcStringAttributes(this, arrayA, a.offset(), index, encoding, byteOffset, validProfile, fixedWidthProfile));
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class CodePointIndexToRawNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int extraOffsetRaw, int index, boolean isLength);

        @SuppressWarnings("unused")
        @Specialization(guards = "isFixedWidth(codeRangeA)")
        int doFixed(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int extraOffsetRaw, int index, boolean isLength) {
            return index;
        }

        @Specialization(guards = {"isUTF8(encoding)", "isValidMultiByte(codeRangeA)"})
        int utf8Valid(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int extraOffsetRaw, int index,
                        boolean isLength) {
            assert isStride0(a);
            int regionOffset = a.offset() + extraOffsetRaw;
            int regionLength = a.length() - extraOffsetRaw;
            int byteIndex = TStringOps.codePointIndexToByteIndexUTF8Valid(this, arrayA, regionOffset, regionLength, index);
            if (byteIndex < 0 || !isLength && byteIndex == regionLength) {
                throw InternalErrors.indexOutOfBounds();
            }
            return byteIndex;
        }

        @Specialization(guards = {"isUTF8(encoding)", "isBrokenMultiByte(codeRangeA)"})
        int utf8Broken(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int extraOffsetRaw, int index,
                        boolean isLength) {
            assert isStride0(a);
            int cpi = 0;
            for (int i = extraOffsetRaw; i < a.length(); i += Encodings.utf8GetCodePointLength(a, arrayA, i, ErrorHandling.BEST_EFFORT)) {
                if (cpi == index) {
                    return i - extraOffsetRaw;
                }
                cpi++;
                TStringConstants.truffleSafePointPoll(this, cpi);
            }
            return atEnd(a, extraOffsetRaw, index, isLength, cpi);
        }

        @Specialization(guards = {"isUTF16(encoding)", "isValidMultiByte(codeRangeA)"})
        int utf16Valid(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int extraOffsetRaw, int index,
                        boolean isLength) {
            assert isStride1(a);
            int regionOffset = a.offset() + (extraOffsetRaw << 1);
            int regionLength = a.length() - extraOffsetRaw;
            int result = TStringOps.codePointIndexToByteIndexUTF16Valid(this, arrayA, regionOffset, regionLength, index);
            if (result < 0 || !isLength && result == regionLength) {
                throw InternalErrors.indexOutOfBounds();
            }
            return result;
        }

        @Specialization(guards = {"isUTF16(encoding)", "isBrokenMultiByte(codeRangeA)"})
        int utf16Broken(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int extraOffsetRaw, int index,
                        boolean isLength) {
            assert isStride1(a);
            int cpi = 0;
            for (int i = extraOffsetRaw; i < a.length(); i++) {
                if (!(i > extraOffsetRaw && Encodings.isUTF16LowSurrogate(TStringOps.readS1(a, arrayA, i)) && Encodings.isUTF16HighSurrogate(TStringOps.readS1(a, arrayA, i - 1)))) {
                    if (cpi == index) {
                        return i - extraOffsetRaw;
                    }
                    cpi++;
                }
                TStringConstants.truffleSafePointPoll(this, i + 1);
            }
            return atEnd(a, extraOffsetRaw, index, isLength, cpi);
        }

        @TruffleBoundary
        @Specialization(guards = "isUnsupportedEncoding(encoding)")
        int unsupported(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, Encoding encoding, int extraOffsetRaw, int index, boolean isLength) {
            JCodings.Encoding jCoding = JCodings.getInstance().get(encoding);
            return JCodings.getInstance().codePointIndexToRaw(this, a, JCodings.asByteArray(arrayA), extraOffsetRaw, index, isLength, jCoding);
        }

        static int atEnd(AbstractTruffleString a, int extraOffsetRaw, int index, boolean isLength, int cpi) {
            if (isLength && cpi == index) {
                return a.length() - extraOffsetRaw;
            }
            throw InternalErrors.indexOutOfBounds();
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class ReadByteNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int i, Encoding encoding);

        @Specialization(guards = "isUTF16(encoding)")
        static int doUTF16(AbstractTruffleString a, Object arrayA, int i, @SuppressWarnings("unused") Encoding encoding,
                        @Cached ConditionProfile stride0Profile) {
            a.boundsCheckByteIndexUTF16(i);
            final int index;
            if (stride0Profile.profile(isStride0(a))) {
                // simplified from:
                // (TStringGuards.bigEndian() ? (i & 1) == 0 : (i & 1) != 0)
                if ((TStringGuards.bigEndian()) == ((i & 1) == 0)) {
                    return 0;
                } else {
                    index = i >> 1;
                }
            } else {
                assert isStride1(a);
                index = i;
            }
            return TStringOps.readS0(arrayA, a.offset(), a.length() << a.stride(), index);
        }

        @Specialization(guards = "isUTF32(encoding)")
        static int doUTF32(AbstractTruffleString a, Object arrayA, int i, @SuppressWarnings("unused") Encoding encoding,
                        @Cached ConditionProfile stride0Profile,
                        @Cached ConditionProfile stride1Profile) {
            a.boundsCheckByteIndexUTF32(i);
            final int index;
            if (stride0Profile.profile(isStride0(a))) {
                if ((i & 3) != (TStringGuards.bigEndian() ? 3 : 0)) {
                    return 0;
                } else {
                    index = i >> 2;
                }
            } else if (stride1Profile.profile(isStride1(a))) {
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
                assert isStride2(a);
                index = i;
            }
            return TStringOps.readS0(arrayA, a.offset(), a.length() << a.stride(), index);
        }

        @Specialization(guards = "!isUTF16Or32(encoding)")
        static int doRest(AbstractTruffleString a, Object arrayA, int i, @SuppressWarnings("unused") Encoding encoding) {
            a.boundsCheckByteIndexS0(i);
            return TStringOps.readS0(a, arrayA, i);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class CodePointAtNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int i, ErrorHandling errorHandling);

        @Specialization(guards = "isUTF16(encoding)")
        int utf16(AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i, ErrorHandling errorHandling,
                        @Cached ConditionProfile fixedWidthProfile,
                        @Cached ConditionProfile stride0Profile,
                        @Cached ConditionProfile validProfile) {
            if (fixedWidthProfile.profile(isFixedWidth(codeRangeA))) {
                if (stride0Profile.profile(isStride0(a))) {
                    return TStringOps.readS0(a, arrayA, i);
                } else {
                    assert isStride1(a);
                    return TStringOps.readS1(a, arrayA, i);
                }
            } else if (validProfile.profile(isValidMultiByte(codeRangeA))) {
                assert isStride1(a);
                return Encodings.utf16DecodeValid(a, arrayA, Encodings.utf16ValidCodePointToCharIndex(this, a, arrayA, i));
            } else {
                assert isStride1(a);
                assert TStringGuards.isBrokenMultiByte(codeRangeA);
                return Encodings.utf16DecodeBroken(a, arrayA, Encodings.utf16BrokenCodePointToCharIndex(this, a, arrayA, i), errorHandling);
            }
        }

        @Specialization(guards = "isUTF32(encoding)")
        static int utf32(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int i, ErrorHandling errorHandling,
                        @Cached ConditionProfile stride0Profile,
                        @Cached ConditionProfile stride1Profile) {
            return CodePointAtRawNode.utf32(a, arrayA, codeRangeA, encoding, i, errorHandling, stride0Profile, stride1Profile);
        }

        @Specialization(guards = "isUTF8(encoding)")
        int utf8(AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i, ErrorHandling errorHandling,
                        @Cached ConditionProfile fixedWidthProfile,
                        @Cached ConditionProfile validProfile) {
            if (fixedWidthProfile.profile(is7Bit(codeRangeA))) {
                return TStringOps.readS0(a, arrayA, i);
            } else {
                int byteIndex = Encodings.utf8CodePointToByteIndex(this, a, arrayA, i);
                if (validProfile.profile(isValidMultiByte(codeRangeA))) {
                    return Encodings.utf8DecodeValid(a, arrayA, byteIndex);
                } else {
                    assert TStringGuards.isBrokenMultiByte(codeRangeA);
                    return Encodings.utf8DecodeBroken(a, arrayA, byteIndex, errorHandling);
                }
            }
        }

        @Specialization(guards = {"!isUTF16Or32(encoding)", "!isUTF8(encoding)", "isBytes(encoding) || is7Or8Bit(codeRangeA)"})
        static int doFixed(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i,
                        @SuppressWarnings("unused") ErrorHandling errorHandling) {
            return CodePointAtRawNode.doFixed(a, arrayA, codeRangeA, encoding, i, errorHandling);
        }

        @Specialization(guards = {"isAscii(encoding)", "!is7Or8Bit(codeRangeA)"})
        static int doAsciiBroken(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i,
                        ErrorHandling errorHandling) {
            return CodePointAtRawNode.doAsciiBroken(a, arrayA, codeRangeA, encoding, i, errorHandling);
        }

        @Specialization(guards = {"isUnsupportedEncoding(encoding)", "!is7Or8Bit(codeRangeA)"})
        int unsupported(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, Encoding encoding, int i, ErrorHandling errorHandling) {
            assert isStride0(a);
            JCodings.Encoding jCoding = JCodings.getInstance().get(encoding);
            byte[] bytes = JCodings.asByteArray(arrayA);
            return JCodings.getInstance().decode(a, bytes, JCodings.getInstance().codePointIndexToRaw(this, a, bytes, 0, i, false, jCoding), jCoding, errorHandling);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class CodePointAtRawNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int i, ErrorHandling errorHandling);

        @Specialization(guards = "isUTF16(encoding)")
        static int utf16(AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i, ErrorHandling errorHandling,
                        @Cached ConditionProfile fixedWidthProfile,
                        @Cached ConditionProfile validProfile,
                        @Cached ConditionProfile stride0Profile) {
            if (fixedWidthProfile.profile(isFixedWidth(codeRangeA))) {
                if (stride0Profile.profile(isStride0(a))) {
                    return TStringOps.readS0(a, arrayA, i);
                } else {
                    assert isStride1(a);
                    return TStringOps.readS1(a, arrayA, i);
                }
            } else if (validProfile.profile(isValidMultiByte(codeRangeA))) {
                return Encodings.utf16DecodeValid(a, arrayA, i);
            } else {
                assert TStringGuards.isBrokenMultiByte(codeRangeA);
                return Encodings.utf16DecodeBroken(a, arrayA, i, errorHandling);
            }
        }

        @Specialization(guards = "isUTF32(encoding)")
        static int utf32(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i, ErrorHandling errorHandling,
                        @Cached ConditionProfile stride0Profile,
                        @Cached ConditionProfile stride1Profile) {
            if (stride0Profile.profile(isStride0(a))) {
                return TStringOps.readS0(a, arrayA, i);
            } else if (stride1Profile.profile(isStride1(a))) {
                char c = TStringOps.readS1(a, arrayA, i);
                if (errorHandling == ErrorHandling.RETURN_NEGATIVE && Encodings.isUTF16Surrogate(c)) {
                    return -1;
                }
                return c;
            } else {
                assert isStride2(a);
                int c = TStringOps.readS2(a, arrayA, i);
                if (errorHandling == ErrorHandling.RETURN_NEGATIVE && !Encodings.isValidUnicodeCodepoint(c)) {
                    return -1;
                }
                return c;
            }
        }

        @Specialization(guards = "isUTF8(encoding)")
        static int utf8(AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i, ErrorHandling errorHandling,
                        @Cached ConditionProfile fixedWidthProfile,
                        @Cached ConditionProfile validProfile) {
            if (fixedWidthProfile.profile(is7Bit(codeRangeA))) {
                return TStringOps.readS0(a, arrayA, i);
            } else if (validProfile.profile(isValidMultiByte(codeRangeA))) {
                return Encodings.utf8DecodeValid(a, arrayA, i);
            } else {
                assert TStringGuards.isBrokenMultiByte(codeRangeA);
                return Encodings.utf8DecodeBroken(a, arrayA, i, errorHandling);
            }
        }

        @Specialization(guards = {"!isUTF16Or32(encoding)", "!isUTF8(encoding)", "isBytes(encoding) || is7Or8Bit(codeRangeA)"})
        static int doFixed(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i,
                        @SuppressWarnings("unused") ErrorHandling errorHandling) {
            assert isStride0(a);
            return TStringOps.readS0(a, arrayA, i);
        }

        @Specialization(guards = {"isAscii(encoding)", "!is7Or8Bit(codeRangeA)"})
        static int doAsciiBroken(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i,
                        ErrorHandling errorHandling) {
            assert isStride0(a);
            int c = readS0(a, arrayA, i);
            if (errorHandling == ErrorHandling.RETURN_NEGATIVE && c > 0x7f) {
                return -1;
            }
            assert errorHandling == ErrorHandling.BEST_EFFORT;
            return c;
        }

        @Specialization(guards = {"isUnsupportedEncoding(encoding)", "!is7Or8Bit(codeRangeA)"})
        static int unsupported(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i, ErrorHandling errorHandling) {
            return JCodings.getInstance().decode(a, JCodings.asByteArray(arrayA), i, JCodings.getInstance().get(encoding), errorHandling);
        }
    }

    static int indexOfFixedWidth(AbstractTruffleString a, Object arrayA, int codeRangeA, int codepoint, int fromIndex, int toIndex,
                    TStringOpsNodes.RawIndexOfCodePointNode indexOfNode) {
        if (fromIndex == toIndex || !TSCodeRange.isInCodeRange(codepoint, codeRangeA)) {
            return -1;
        }
        return indexOfNode.execute(a, arrayA, codepoint, fromIndex, toIndex);
    }

    static int lastIndexOfFixedWidth(AbstractTruffleString a, Object arrayA, int codeRangeA, int codepoint, int fromIndex, int toIndex,
                    TStringOpsNodes.RawLastIndexOfCodePointNode indexOfNode) {
        if (fromIndex == toIndex || !TSCodeRange.isInCodeRange(codepoint, codeRangeA)) {
            return -1;
        }
        return indexOfNode.execute(a, arrayA, codepoint, fromIndex, toIndex);
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class IndexOfCodePointNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int toIndex);

        @Specialization(guards = "isFixedWidth(codeRangeA)")
        static int doFixedWidth(AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TStringOpsNodes.RawIndexOfCodePointNode indexOfNode) {
            return indexOfFixedWidth(a, arrayA, codeRangeA, codepoint, fromIndex, toIndex, indexOfNode);
        }

        @Specialization(guards = "!isFixedWidth(codeRangeA)")
        int decode(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TruffleStringIterator.NextNode nextNode) {
            return TruffleStringIterator.indexOf(this, AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding), codepoint, fromIndex, toIndex, nextNode);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class IndexOfCodePointRawNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int toIndex);

        @Specialization(guards = {"isFixedWidth(codeRangeA)"})
        static int utf8Fixed(AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TStringOpsNodes.RawIndexOfCodePointNode indexOfNode) {
            return indexOfFixedWidth(a, arrayA, codeRangeA, codepoint, fromIndex, toIndex, indexOfNode);
        }

        @Specialization(guards = {"isUTF8(encoding)", "!isFixedWidth(codeRangeA)"})
        int utf8Variable(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex, int toIndex) {
            assert isStride0(a);
            int encodedSize = Encodings.utf8EncodedSize(codepoint);
            if (encodedSize > toIndex - fromIndex) {
                return -1;
            }
            if (encodedSize == 1) {
                return TStringOps.indexOfCodePointWithStride(this, a, arrayA, 0, fromIndex, toIndex, codepoint);
            }
            byte[] encoded = Encodings.utf8EncodeNonAscii(codepoint, encodedSize);
            TruffleString b = TruffleString.createFromByteArray(encoded, encoded.length, 0, Encoding.UTF_8, 1, TSCodeRange.getValidMultiByte());
            return TStringOps.indexOfStringWithOrMaskWithStride(this, a, arrayA, 0, b, encoded, 0, fromIndex, toIndex, null);
        }

        @Specialization(guards = {"isUTF16(encoding)", "!isFixedWidth(codeRangeA)"})
        int utf16Variable(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex,
                        int toIndex) {
            assert isStride1(a);
            int encodedSize = Encodings.utf16EncodedSize(codepoint);
            if (encodedSize > toIndex - fromIndex) {
                return -1;
            }
            if (encodedSize == 1) {
                return TStringOps.indexOfCodePointWithStride(this, a, arrayA, 1, fromIndex, toIndex, codepoint);
            }
            return TStringOps.indexOf2ConsecutiveWithStride(
                            this, a, arrayA, 1, fromIndex, toIndex, Character.highSurrogate(codepoint), Character.lowSurrogate(codepoint));
        }

        @Specialization(guards = {"isUnsupportedEncoding(encoding)", "!isFixedWidth(codeRangeA)"})
        int unsupported(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TruffleStringIterator.NextNode nextNode) {
            final TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding);
            it.setRawIndex(fromIndex);
            int loopCount = 0;
            while (it.hasNext() && it.getRawIndex() < toIndex) {
                int ret = it.getRawIndex();
                if (nextNode.execute(it) == codepoint) {
                    return ret;
                }
                TStringConstants.truffleSafePointPoll(this, ++loopCount);
            }
            return -1;
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class LastIndexOfCodePointNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int toIndex);

        @Specialization(guards = "isFixedWidth(codeRangeA)")
        static int doFixedWidth(AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TStringOpsNodes.RawLastIndexOfCodePointNode lastIndexOfNode) {
            return lastIndexOfFixedWidth(a, arrayA, codeRangeA, codepoint, fromIndex, toIndex, lastIndexOfNode);
        }

        @Specialization(guards = "!isFixedWidth(codeRangeA)")
        int decode(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TruffleStringIterator.NextNode nextNode) {
            return TruffleStringIterator.lastIndexOf(this, AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding), codepoint, fromIndex, toIndex, nextNode);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class LastIndexOfCodePointRawNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int toIndex);

        @Specialization(guards = {"isFixedWidth(codeRangeA)"})
        static int utf8Fixed(AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached @Shared("lastIndexOfNode") TStringOpsNodes.RawLastIndexOfCodePointNode lastIndexOfNode) {
            return lastIndexOfFixedWidth(a, arrayA, codeRangeA, codepoint, fromIndex, toIndex, lastIndexOfNode);
        }

        @Specialization(guards = {"isUTF8(encoding)", "!isFixedWidth(codeRangeA)"})
        int utf8Variable(AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached @Shared("lastIndexOfNode") TStringOpsNodes.RawLastIndexOfCodePointNode lastIndexOfNode) {
            int encodedSize = Encodings.utf8EncodedSize(codepoint);
            if (encodedSize > fromIndex - toIndex) {
                return -1;
            }
            if (encodedSize == 1) {
                return lastIndexOfFixedWidth(a, arrayA, codeRangeA, codepoint, fromIndex, toIndex, lastIndexOfNode);
            }
            byte[] encoded = Encodings.utf8EncodeNonAscii(codepoint, encodedSize);
            TruffleString b = TruffleString.createFromByteArray(encoded, encoded.length, 0, Encoding.UTF_8, 1, TSCodeRange.getValidMultiByte());
            return TStringOps.lastIndexOfStringWithOrMaskWithStride(this, a, arrayA, 0, b, encoded, 0, fromIndex, toIndex, null);
        }

        @Specialization(guards = {"isUTF16(encoding)", "!isFixedWidth(codeRangeA)"})
        int utf16Variable(AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached @Shared("lastIndexOfNode") TStringOpsNodes.RawLastIndexOfCodePointNode lastIndexOfNode) {
            assert isStride1(a);
            int encodedSize = Encodings.utf16EncodedSize(codepoint);
            if (encodedSize > fromIndex - toIndex) {
                return -1;
            }
            if (encodedSize == 1) {
                return lastIndexOfFixedWidth(a, arrayA, codeRangeA, codepoint, fromIndex, toIndex, lastIndexOfNode);
            }
            return TStringOps.lastIndexOf2ConsecutiveWithOrMaskWithStride(
                            this, a, arrayA, 1, fromIndex, toIndex, Character.highSurrogate(codepoint), Character.lowSurrogate(codepoint), 0, 0);
        }

        @Specialization(guards = {"isUnsupportedEncoding(encoding)", "!isFixedWidth(codeRangeA)"})
        int unsupported(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TruffleStringIterator.PreviousNode prevNode) {
            final TruffleStringIterator it = TruffleString.backwardIterator(a, arrayA, codeRangeA, encoding);
            it.setRawIndex(fromIndex);
            int loopCount = 0;
            while (it.hasPrevious() && it.getRawIndex() >= toIndex) {
                if (prevNode.execute(it) == codepoint) {
                    return it.getRawIndex();
                }
                TStringConstants.truffleSafePointPoll(this, ++loopCount);
            }
            return -1;
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class SubstringNode extends Node {

        abstract TruffleString execute(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int fromIndex, int length, boolean lazy);

        @SuppressWarnings("unused")
        @Specialization(guards = "length == 0")
        static TruffleString lengthZero(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int fromIndex, int length, boolean lazy) {
            return encoding.getEmpty();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"fromIndex == 0", "length == length(a)"})
        static TruffleString sameStr(TruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int fromIndex, int length, boolean lazy) {
            return a;
        }

        @Specialization(guards = {"length > 0", "length != length(a) || a.isMutable()", "!lazy"})
        TruffleString materializeSubstring(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int fromIndex, int length, @SuppressWarnings("unused") boolean lazy,
                        @Cached CalcStringAttributesNode calcAttributesNode,
                        @Cached ConditionProfile utf16Profile,
                        @Cached ConditionProfile utf32Profile) {
            final long attrs;
            final int codeRange;
            final int stride;
            final int newStride;
            if (utf16Profile.profile(isUTF16(encoding))) {
                stride = a.stride();
                attrs = calcAttributesNode.execute(a, arrayA, a.offset(), length, stride, Encoding.UTF_16, fromIndex, codeRangeA);
                codeRange = StringAttributes.getCodeRange(attrs);
                newStride = Stride.fromCodeRangeUTF16(codeRange);
            } else if (utf32Profile.profile(isUTF32(encoding))) {
                stride = a.stride();
                attrs = calcAttributesNode.execute(a, arrayA, a.offset(), length, stride, Encoding.UTF_32, fromIndex, codeRangeA);
                codeRange = StringAttributes.getCodeRange(attrs);
                newStride = Stride.fromCodeRangeUTF32(codeRange);
            } else {
                stride = 0;
                attrs = calcAttributesNode.execute(a, arrayA, a.offset(), length, stride, encoding, fromIndex, codeRangeA);
                codeRange = StringAttributes.getCodeRange(attrs);
                newStride = 0;
            }
            byte[] newBytes = TStringOps.arraycopyOfWithStride(this, arrayA, a.offset() + (fromIndex << stride), length, stride, length, newStride);
            return TruffleString.createFromByteArray(newBytes, length, newStride, encoding, StringAttributes.getCodePointLength(attrs), codeRange);
        }

        @Specialization(guards = {"length > 0", "length != length(a)", "lazy"})
        TruffleString createLazySubstring(TruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int fromIndex, int length, @SuppressWarnings("unused") boolean lazy,
                        @Cached CalcStringAttributesNode calcAttributesNode,
                        @Cached ConditionProfile stride1MustMaterializeProfile,
                        @Cached ConditionProfile stride2MustMaterializeProfile) {
            int lazyOffset = a.offset() + (fromIndex << a.stride());
            long attrs = calcAttributesNode.execute(a, arrayA, a.offset(), length, a.stride(), encoding, fromIndex, codeRangeA);
            int codeRange = StringAttributes.getCodeRange(attrs);
            int codePointLength = StringAttributes.getCodePointLength(attrs);
            final Object array;
            final int offset;
            final int stride;
            if (stride1MustMaterializeProfile.profile(a.stride() == 1 && TSCodeRange.isMoreRestrictiveOrEqual(codeRange, TSCodeRange.get8Bit()))) {
                assert isUTF16Or32(encoding);
                stride = 0;
                offset = 0;
                final byte[] newBytes = new byte[length];
                TStringOps.arraycopyWithStride(this,
                                arrayA, lazyOffset, 1, 0,
                                newBytes, offset, 0, 0, length);
                array = newBytes;
            } else if (stride2MustMaterializeProfile.profile(a.stride() == 2 && TSCodeRange.isMoreRestrictiveOrEqual(codeRange, TSCodeRange.get16Bit()))) {
                // Always materialize 4-byte UTF-32 strings when they can be compacted. Otherwise,
                // they could get re-interpreted as UTF-16 and break the assumption that all UTF-16
                // strings are stride 0 or 1.
                assert isUTF32(encoding);
                stride = Stride.fromCodeRangeUTF32(StringAttributes.getCodeRange(attrs));
                offset = 0;
                final byte[] newBytes = new byte[length << stride];
                if (stride == 0) {
                    TStringOps.arraycopyWithStride(this,
                                    arrayA, lazyOffset, 2, 0,
                                    newBytes, offset, 0, 0, length);
                } else {
                    assert stride == 1;
                    TStringOps.arraycopyWithStride(this,
                                    arrayA, lazyOffset, 2, 0,
                                    newBytes, offset, 1, 0, length);
                }
                array = newBytes;
            } else {
                if (isUnsupportedEncoding(encoding) && arrayA instanceof NativePointer) {
                    // avoid conflicts in NativePointer#getBytes
                    array = ((NativePointer) arrayA).copy();
                } else {
                    array = arrayA;
                }
                offset = lazyOffset;
                stride = a.stride();
            }
            return TruffleString.createFromArray(array, offset, length, stride, encoding, codePointLength, codeRange);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class ConcatEagerNode extends Node {

        abstract TruffleString execute(AbstractTruffleString a, AbstractTruffleString b, Encoding encoding, int concatLength, int concatStride, int concatCodeRange);

        @Specialization
        static TruffleString concat(AbstractTruffleString a, AbstractTruffleString b, Encoding encoding, int concatLength, int concatStride, int concatCodeRange,
                        @Cached TruffleString.ToIndexableNode toIndexableNodeA,
                        @Cached TruffleString.ToIndexableNode toIndexableNodeB,
                        @Cached GetCodePointLengthNode getCodePointLengthANode,
                        @Cached GetCodePointLengthNode getCodePointLengthBNode,
                        @Cached ConcatMaterializeBytesNode materializeBytesNode,
                        @Cached CalcStringAttributesNode calculateAttributesNode,
                        @Cached ConditionProfile brokenProfile) {
            final byte[] bytes = materializeBytesNode.execute(a, toIndexableNodeA.execute(a, a.data()), b, toIndexableNodeB.execute(b, b.data()), encoding, concatLength, concatStride);
            final int codeRange;
            final int codePointLength;
            if (brokenProfile.profile(isBrokenMultiByte(concatCodeRange))) {
                final long attrs = calculateAttributesNode.execute(null, bytes, 0, concatLength, concatStride, encoding, 0, TSCodeRange.getUnknown());
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            } else {
                codePointLength = getCodePointLengthANode.execute(a) + getCodePointLengthBNode.execute(b);
                codeRange = concatCodeRange;
            }
            return TruffleString.createFromByteArray(bytes, concatLength, concatStride, encoding, codePointLength, codeRange);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class ConcatMaterializeBytesNode extends Node {

        abstract byte[] execute(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB, Encoding encoding, int concatLength, int concatStride);

        @Specialization(guards = "isUTF16(encoding) || isUTF32(encoding)")
        byte[] doWithCompression(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB, @SuppressWarnings("unused") Encoding encoding, int concatLength, int concatStride) {
            final byte[] bytes = new byte[concatLength << concatStride];
            TStringOps.arraycopyWithStride(this,
                            arrayA, a.offset(), a.stride(), 0,
                            bytes, 0, concatStride, 0, a.length());
            TStringOps.arraycopyWithStride(this,
                            arrayB, b.offset(), b.stride(), 0,
                            bytes, 0, concatStride, a.length(), b.length());
            return bytes;
        }

        @Specialization(guards = {"!isUTF16(encoding)", "!isUTF32(encoding)"})
        byte[] doNoCompression(AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB, @SuppressWarnings("unused") Encoding encoding, int concatLength, int concatStride) {
            assert isStride0(a);
            assert isStride0(b);
            assert concatStride == 0;
            final byte[] bytes = new byte[concatLength];
            TStringOps.arraycopyWithStride(this,
                            arrayA, a.offset(), 0, 0,
                            bytes, 0, 0, 0, a.length());
            TStringOps.arraycopyWithStride(this,
                            arrayB, b.offset(), 0, 0,
                            bytes, 0, 0, a.length(), b.length());
            return bytes;
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class RegionEqualsNode extends Node {

        abstract boolean execute(
                        AbstractTruffleString a, Object arrayA, int codeRangeA, int fromIndexA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndexB, int length, Encoding encoding);

        @Specialization(guards = {"isFixedWidth(codeRangeA, codeRangeB)"})
        boolean direct(
                        AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, int fromIndexA,
                        AbstractTruffleString b, Object arrayB, @SuppressWarnings("unused") int codeRangeB, int fromIndexB, int length, @SuppressWarnings("unused") Encoding encoding) {
            return TStringOps.regionEqualsWithOrMaskWithStride(this, a, arrayA, a.stride(), fromIndexA, b, arrayB, b.stride(), fromIndexB, null, length);
        }

        @Specialization(guards = {"!isFixedWidth(codeRangeA, codeRangeB)"})
        boolean decode(
                        AbstractTruffleString a, Object arrayA, int codeRangeA, int fromIndexA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndexB, int length, Encoding encoding,
                        @Cached TruffleStringIterator.NextNode nextNodeA,
                        @Cached TruffleStringIterator.NextNode nextNodeB) {
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.forwardIterator(b, arrayB, codeRangeB, encoding);
            for (int i = 0; i < fromIndexA; i++) {
                if (!aIt.hasNext()) {
                    return false;
                }
                nextNodeA.execute(aIt);
                TStringConstants.truffleSafePointPoll(this, i + 1);
            }
            for (int i = 0; i < fromIndexB; i++) {
                if (!bIt.hasNext()) {
                    return false;
                }
                nextNodeB.execute(bIt);
                TStringConstants.truffleSafePointPoll(this, i + 1);
            }
            for (int i = 0; i < length; i++) {
                if (!(aIt.hasNext() && bIt.hasNext()) || nextNodeA.execute(aIt) != nextNodeB.execute(bIt)) {
                    return false;
                }
                TStringConstants.truffleSafePointPoll(this, i + 1);
            }
            return true;
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class IndexOfStringNode extends Node {

        abstract int execute(
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, Encoding encoding);

        @Specialization(guards = {"isFixedWidth(codeRangeA, codeRangeB)"})
        static int direct(
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, @SuppressWarnings("unused") Encoding encoding,
                        @Cached RawIndexOfStringNode indexOfStringNode) {
            assert !b.isEmpty() && !indexOfCannotMatch(codeRangeA, b, codeRangeB, toIndex - fromIndex, GetCodePointLengthNode.getUncached());
            return indexOfStringNode.execute(a, arrayA, b, arrayB, fromIndex, toIndex, null);
        }

        @Specialization(guards = {"!isFixedWidth(codeRangeA, codeRangeB)"})
        int decode(
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, Encoding encoding,
                        @Cached TruffleStringIterator.NextNode nextNodeA,
                        @Cached TruffleStringIterator.NextNode nextNodeB) {
            assert !b.isEmpty() && !indexOfCannotMatch(codeRangeA, b, codeRangeB, toIndex - fromIndex, GetCodePointLengthNode.getUncached());
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.forwardIterator(b, arrayB, codeRangeB, encoding);
            return TruffleStringIterator.indexOfString(this, aIt, bIt, fromIndex, toIndex, nextNodeA, nextNodeB);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class IndexOfStringRawNode extends Node {

        abstract int execute(
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, byte[] mask, Encoding encoding);

        @Specialization(guards = {"isSupportedEncoding(encoding) || isFixedWidth(codeRangeA)"})
        static int supported(
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, byte[] mask, @SuppressWarnings("unused") Encoding encoding,
                        @Cached TStringOpsNodes.RawIndexOfStringNode indexOfStringNode) {
            assert !b.isEmpty() && !indexOfCannotMatch(codeRangeA, b, codeRangeB, mask, toIndex - fromIndex);
            return indexOfStringNode.execute(a, arrayA, b, arrayB, fromIndex, toIndex, mask);
        }

        @Specialization(guards = {"isUnsupportedEncoding(encoding)", "!isFixedWidth(codeRangeA)"})
        int unsupported(
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, @SuppressWarnings("unused") byte[] mask, Encoding encoding,
                        @Cached TruffleStringIterator.NextNode nextNodeA,
                        @Cached TruffleStringIterator.NextNode nextNodeB) {
            assert mask == null;
            assert !b.isEmpty() && !indexOfCannotMatch(codeRangeA, b, codeRangeB, mask, toIndex - fromIndex);
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.forwardIterator(b, arrayB, codeRangeB, encoding);
            return TruffleStringIterator.byteIndexOfString(this, aIt, bIt, fromIndex, toIndex, nextNodeA, nextNodeB);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class LastIndexOfStringNode extends Node {

        abstract int execute(
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, Encoding encoding);

        @Specialization(guards = {"isFixedWidth(codeRangeA, codeRangeB)"})
        static int direct(
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, @SuppressWarnings("unused") Encoding encoding,
                        @Cached RawLastIndexOfStringNode indexOfStringNode) {
            assert !b.isEmpty() && !indexOfCannotMatch(codeRangeA, b, codeRangeB, fromIndex - toIndex, GetCodePointLengthNode.getUncached());
            return indexOfStringNode.execute(a, arrayA, b, arrayB, fromIndex, toIndex, null);
        }

        @Specialization(guards = {"!isFixedWidth(codeRangeA, codeRangeB)"})
        int decode(
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, Encoding encoding,
                        @Cached TruffleStringIterator.NextNode nextNodeA,
                        @Cached TruffleStringIterator.PreviousNode prevNodeA,
                        @Cached TruffleStringIterator.PreviousNode prevNodeB) {
            assert !b.isEmpty() && !indexOfCannotMatch(codeRangeA, b, codeRangeB, fromIndex - toIndex, GetCodePointLengthNode.getUncached());
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.backwardIterator(b, arrayB, codeRangeB, encoding);
            return TruffleStringIterator.lastIndexOfString(this, aIt, bIt, fromIndex, toIndex, nextNodeA, prevNodeA, prevNodeB);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class LastIndexOfStringRawNode extends Node {

        abstract int execute(
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, byte[] mask, Encoding encoding);

        @Specialization(guards = {"isSupportedEncoding(encoding) || isFixedWidth(codeRangeA)"})
        static int lios8SameEncoding(
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, byte[] mask, @SuppressWarnings("unused") Encoding encoding,
                        @Cached TStringOpsNodes.RawLastIndexOfStringNode indexOfStringNode) {
            assert !b.isEmpty() && !indexOfCannotMatch(codeRangeA, b, codeRangeB, mask, fromIndex - toIndex);
            return indexOfStringNode.execute(a, arrayA, b, arrayB, fromIndex, toIndex, mask);
        }

        @Specialization(guards = {"isUnsupportedEncoding(encoding)", "!isFixedWidth(codeRangeA)"})
        int unsupported(
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, @SuppressWarnings("unused") byte[] mask, Encoding encoding,
                        @Cached TruffleStringIterator.NextNode nextNodeA,
                        @Cached TruffleStringIterator.PreviousNode prevNodeA,
                        @Cached TruffleStringIterator.PreviousNode prevNodeB) {
            assert mask == null;
            assert !b.isEmpty() && !indexOfCannotMatch(codeRangeA, b, codeRangeB, mask, fromIndex - toIndex);
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.backwardIterator(b, arrayB, codeRangeB, encoding);
            return TruffleStringIterator.lastByteIndexOfString(this, aIt, bIt, fromIndex, toIndex, nextNodeA, prevNodeA, prevNodeB);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class StrideFromCodeRangeNode extends Node {

        abstract int execute(int codeRange, Encoding encoding);

        @Specialization(guards = "isUTF16(encoding)")
        int doUTF16(int codeRange, @SuppressWarnings("unused") Encoding encoding) {
            return Stride.fromCodeRangeUTF16(codeRange);
        }

        @Specialization(guards = "isUTF32(encoding)")
        int doUTF32(int codeRange, @SuppressWarnings("unused") Encoding encoding) {
            return Stride.fromCodeRangeUTF32(codeRange);
        }

        @Specialization(guards = {"!isUTF16(encoding)", "!isUTF32(encoding)"})
        int doOther(@SuppressWarnings("unused") int codeRange, @SuppressWarnings("unused") Encoding encoding) {
            return 0;
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class CalcStringAttributesNode extends Node {

        abstract long execute(AbstractTruffleString a, Object array, int offset, int length, int stride, Encoding encoding, int fromIndex, int knownCodeRange);

        @SuppressWarnings("unused")
        @Specialization(guards = "length == 0")
        long empty(AbstractTruffleString a, Object array, int offset, int length, int stride, Encoding encoding, int fromIndex, int knownCodeRange) {
            return StringAttributes.create(length, TSCodeRange.getAsciiCodeRange(encoding));
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"is7Bit(knownCodeRange)", "length > 0"})
        long ascii(AbstractTruffleString a, Object array, int offset, int length, int stride, Encoding encoding, int fromIndex, int knownCodeRange) {
            return StringAttributes.create(length, TSCodeRange.get7Bit());
        }

        @Specialization(guards = {"!is7Bit(knownCodeRange)", "length > 0"})
        long notAscii(AbstractTruffleString a, Object array, int offset, int length, int stride, Encoding encoding, int fromIndex, int knownCodeRange,
                        @Cached CalcStringAttributesInnerNode calcNode) {
            return calcNode.execute(a, array, offset, length, stride, encoding, fromIndex, knownCodeRange);
        }

        static CalcStringAttributesNode getUncached() {
            return TStringInternalNodesFactory.CalcStringAttributesNodeGen.getUncached();
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class CalcStringAttributesInnerNode extends Node {

        abstract long execute(AbstractTruffleString a, Object array, int offset, int length, int stride, Encoding encoding, int fromIndex, int knownCodeRange);

        @Specialization(guards = {"is8Bit(knownCodeRange) || isAsciiBytesOrLatin1(encoding)", "stride == 0"})
        long doLatin1(@SuppressWarnings("unused") AbstractTruffleString a, Object array, int offset, int length, @SuppressWarnings("unused") int stride, Encoding encoding, int fromIndex,
                        @SuppressWarnings("unused") int knownCodeRange) {
            int codeRange = TStringOps.calcStringAttributesLatin1(this, array, offset + fromIndex, length);
            return StringAttributes.create(length, is8Bit(codeRange) && isAsciiBytesOrLatin1(encoding) ? TSCodeRange.asciiLatinBytesNonAsciiCodeRange(encoding) : codeRange);
        }

        @Specialization(guards = {"isUpTo16Bit(knownCodeRange)", "stride == 1"})
        long doBMP(@SuppressWarnings("unused") AbstractTruffleString a, Object array, int offset, int length, @SuppressWarnings("unused") int stride, @SuppressWarnings("unused") Encoding encoding,
                        int fromIndex, @SuppressWarnings("unused") int knownCodeRange) {
            return StringAttributes.create(length, TStringOps.calcStringAttributesBMP(this, array, offset + (fromIndex << 1), length));
        }

        @Specialization(guards = {"isUTF8(encoding)", "!isFixedWidth(knownCodeRange)"})
        long doUTF8(AbstractTruffleString a, Object array, int offset, int length, int stride, @SuppressWarnings("unused") Encoding encoding, int fromIndex, int knownCodeRange,
                        @Cached ConditionProfile brokenProfile) {
            assert stride == 0;
            int off = offset + fromIndex;
            if (isValidMultiByte(knownCodeRange) && a != null) {
                return TStringOps.calcStringAttributesUTF8(this, array, off, length, true, off + length == a.offset() + a.length(), brokenProfile);
            } else {
                return TStringOps.calcStringAttributesUTF8(this, array, off, length, false, false, brokenProfile);
            }
        }

        @Specialization(guards = {"isUTF16(encoding)", "isValidMultiByte(knownCodeRange)"})
        long doUTF16Valid(@SuppressWarnings("unused") AbstractTruffleString a, Object array, int offset, int length, int stride, @SuppressWarnings("unused") Encoding encoding, int fromIndex,
                        @SuppressWarnings("unused") int knownCodeRange) {
            assert stride == 1;
            return TStringOps.calcStringAttributesUTF16(this, array, offset + (fromIndex << 1), length, true);
        }

        @Specialization(guards = {"isUTF16(encoding)", "isBrokenMultiByteOrUnknown(knownCodeRange)"})
        long doUTF16Unknown(@SuppressWarnings("unused") AbstractTruffleString a, Object array, int offset, int length, int stride, @SuppressWarnings("unused") Encoding encoding, int fromIndex,
                        @SuppressWarnings("unused") int knownCodeRange) {
            assert stride == 1;
            return TStringOps.calcStringAttributesUTF16(this, array, offset + (fromIndex << 1), length, false);
        }

        @Specialization(guards = {"stride == 2"})
        long doUTF32(@SuppressWarnings("unused") AbstractTruffleString a, Object array, int offset, int length, @SuppressWarnings("unused") int stride, Encoding encoding, int fromIndex,
                        @SuppressWarnings("unused") int knownCodeRange) {
            assert isUTF32(encoding);
            return StringAttributes.create(length, TStringOps.calcStringAttributesUTF32(this, array, offset + (fromIndex << 2), length));
        }

        @Specialization(guards = "isUnsupportedEncoding(encoding)")
        long doGeneric(@SuppressWarnings("unused") AbstractTruffleString a, Object array, int offset, int length, int stride, Encoding encoding, int fromIndex,
                        @SuppressWarnings("unused") int knownCodeRange,
                        @Cached ConditionProfile validCharacterProfile,
                        @Cached ConditionProfile fixedWidthProfile) {
            assert stride == 0;
            return JCodings.getInstance().calcStringAttributes(this, array, offset, length, encoding, fromIndex, validCharacterProfile, fixedWidthProfile);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class ParseIntNode extends Node {

        abstract int execute(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int radix) throws TruffleString.NumberFormatException;

        @Specialization(guards = {"is7Bit(codeRangeA)", "cachedStride == a.stride()"})
        static int do7Bit(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int radix,
                        @Cached(value = "a.stride()", allowUncached = true) int cachedStride,
                        @Cached BranchProfile errorProfile) throws TruffleString.NumberFormatException {
            return NumberConversion.parseInt7Bit(a, arrayA, cachedStride, radix, errorProfile);
        }

        @Specialization(guards = "!is7Bit(codeRangeA)")
        static int doGeneric(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int radix,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached BranchProfile errorProfile) throws TruffleString.NumberFormatException {
            return NumberConversion.parseInt(AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding), radix, errorProfile, nextNode);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class ParseLongNode extends Node {

        abstract long execute(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int radix) throws TruffleString.NumberFormatException;

        @Specialization(guards = {"is7Bit(codeRangeA)", "cachedStride == a.stride()"})
        static long do7Bit(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int radix,
                        @Cached(value = "a.stride()", allowUncached = true) int cachedStride,
                        @Cached BranchProfile errorProfile) throws TruffleString.NumberFormatException {
            return NumberConversion.parseLong7Bit(a, arrayA, cachedStride, radix, errorProfile);
        }

        @Specialization(guards = "!is7Bit(codeRangeA)")
        static long parseLong(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int radix,
                        @Cached TruffleStringIterator.NextNode nextNode,
                        @Cached BranchProfile errorProfile) throws TruffleString.NumberFormatException {
            return NumberConversion.parseLong(AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding), radix, errorProfile, nextNode);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class ParseDoubleNode extends Node {

        abstract double execute(AbstractTruffleString a, Object arrayA) throws TruffleString.NumberFormatException;

        @Specialization(guards = "cachedStride == a.stride()")
        double doParse(AbstractTruffleString a, Object arrayA,
                        @Cached(value = "a.stride()", allowUncached = true) int cachedStride,
                        @Cached BranchProfile errorProfile) throws TruffleString.NumberFormatException {
            return FastDoubleParser.parseDouble(this, a, arrayA, cachedStride, 0, a.length(), errorProfile);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    abstract static class FromJavaStringUTF16Node extends Node {

        FromJavaStringUTF16Node() {
        }

        abstract TruffleString execute(String value, int charOffset, int length, boolean copy);

        @Specialization
        TruffleString doNonEmpty(String javaString, int charOffset, int length, final boolean copy,
                        @Cached ConditionProfile utf16CompactProfile) {
            checkArrayRange(javaString.length(), charOffset, length);
            CompilerAsserts.partialEvaluationConstant(copy);
            if (length == 0) {
                return Encoding.UTF_16.getEmpty();
            }
            final byte[] array;
            final int offset;
            final int stride;
            final int codeRange;
            final int codePointLength;
            int strideJS = TStringUnsafe.getJavaStringStride(javaString);
            int offsetJS = charOffset << 1;
            byte[] arrayJS = TStringUnsafe.getJavaStringArray(javaString);
            if (utf16CompactProfile.profile(strideJS == 0)) {
                if (length == 1) {
                    return TStringConstants.getSingleByte(Encoding.UTF_16, Byte.toUnsignedInt(arrayJS[charOffset]));
                }
                codeRange = TStringOps.calcStringAttributesLatin1(this, arrayJS, offsetJS, length);
                codePointLength = length;
            } else {
                assert strideJS == 1;
                if (length == 1 && TStringOps.readFromByteArray(arrayJS, 1, charOffset) <= 0xff) {
                    return TStringConstants.getSingleByte(Encoding.UTF_16, TStringOps.readFromByteArray(arrayJS, 1, charOffset));
                }
                final long attrs = TStringOps.calcStringAttributesUTF16(this, arrayJS, offsetJS, length, false);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            }
            if (!copy || length == javaString.length()) {
                stride = strideJS;
                offset = offsetJS;
                array = arrayJS;
            } else {
                stride = Stride.fromCodeRangeUTF16(codeRange);
                array = new byte[length << stride];
                offset = 0;
                if (strideJS == 1 && stride == 0) {
                    TStringOps.arraycopyWithStride(this, arrayJS, offsetJS, 1, 0, array, offset, 0, 0, length);
                } else {
                    assert strideJS == stride;
                    TStringOps.arraycopyWithStride(this, arrayJS, offsetJS, 0, 0, array, offset, 0, 0, length << stride);
                }
            }
            TruffleString ret = TruffleString.createFromArray(array, offset, length, stride, Encoding.UTF_16, codePointLength, codeRange);
            if (length == javaString.length()) {
                assert charOffset == 0;
                TruffleString wrapped = TruffleString.createWrapJavaString(javaString, codePointLength, codeRange);
                ret.cacheInsertFirstBeforePublished(wrapped);
            }
            return ret;
        }
    }

    @ImportStatic({TStringGuards.class, Encoding.class})
    @GenerateUncached
    abstract static class ToJavaStringNode extends Node {

        abstract TruffleString execute(TruffleString a, Object arrayA);

        @Specialization(guards = "a.isCompatibleTo(UTF_16)")
        static TruffleString doUTF16(TruffleString a, Object arrayA,
                        @Cached @Shared("createStringNode") CreateJavaStringNode createStringNode) {
            return TruffleString.createWrapJavaString(createStringNode.execute(a, arrayA), a.codePointLength(), a.codeRange());
        }

        @Specialization(guards = "!a.isCompatibleTo(UTF_16)")
        static TruffleString doGeneric(TruffleString a, Object arrayA,
                        @Cached GetCodePointLengthNode getCodePointLengthNode,
                        @Cached GetCodeRangeNode getCodeRangeNode,
                        @Cached TransCodeNode transCodeNode,
                        @Cached @Shared("createStringNode") CreateJavaStringNode createStringNode) {
            TruffleString utf16 = transCodeNode.execute(a, arrayA, getCodePointLengthNode.execute(a), getCodeRangeNode.execute(a), Encoding.UTF_16);
            if (!utf16.isCacheHead()) {
                a.cacheInsert(utf16);
            }
            return TruffleString.createWrapJavaString(createStringNode.execute(utf16, utf16.data()), utf16.codePointLength(), utf16.codeRange());
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class CreateJavaStringNode extends Node {

        abstract String execute(AbstractTruffleString a, Object arrayA);

        @Specialization
        String createJavaString(AbstractTruffleString a, Object arrayA,
                        @Cached ConditionProfile reuseProfile,
                        @Cached GetCodeRangeNode getCodeRangeNode) {
            assert isUTF16Compatible(a);
            final int codeRange = getCodeRangeNode.execute(a);
            final int stride = Stride.fromCodeRangeUTF16(codeRange);
            final byte[] bytes;
            if (reuseProfile.profile(a instanceof TruffleString && arrayA instanceof byte[] && a.length() << a.stride() == ((byte[]) arrayA).length && a.stride() == stride)) {
                assert a.offset() == 0;
                bytes = (byte[]) arrayA;
            } else {
                bytes = new byte[a.length() << stride];
                TStringOps.arraycopyWithStride(this,
                                arrayA, a.offset(), a.stride(), 0,
                                bytes, 0, stride, 0, a.length());
            }
            return TStringUnsafe.createJavaString(bytes, stride);
        }

        private static boolean isUTF16Compatible(AbstractTruffleString a) {
            return a.isCompatibleTo(Encoding.UTF_16) || a instanceof MutableTruffleString && ((MutableTruffleString) a).codeRange() < Encoding.UTF_16.maxCompatibleCodeRange;
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class TransCodeNode extends Node {

        abstract TruffleString execute(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding targetEncoding);

        @Specialization
        TruffleString transcode(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding targetEncoding,
                        @Cached ConditionProfile asciiBytesInvalidProfile,
                        @Cached TransCodeIntlNode transCodeIntlNode) {
            if (AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS && a.isImmutable() && codeRangeA < targetEncoding.maxCompatibleCodeRange) {
                if (a.stride() == 0) {
                    return TruffleString.createFromArray(arrayA, a.offset(), a.length(), 0, targetEncoding, codePointLengthA, codeRangeA, false);
                }
                int targetStride = Stride.fromCodeRange(codeRangeA, targetEncoding);
                Object array = TStringOps.arraycopyOfWithStride(this, arrayA, a.offset(), a.length(), a.stride(), a.length(), targetStride);
                return TruffleString.createFromArray(array, 0, a.length(), targetStride, targetEncoding, codePointLengthA, codeRangeA, false);
            }
            assert a.length() > 0;
            if (asciiBytesInvalidProfile.profile((isAscii(a.encoding()) || isBytes(a.encoding())) && isSupportedEncoding(targetEncoding))) {
                assert (isBrokenFixedWidth(codeRangeA) || isValidFixedWidth(codeRangeA)) && isStride0(a) && codePointLengthA == a.length();
                byte[] buffer = new byte[codePointLengthA];
                for (int i = 0; i < buffer.length; i++) {
                    int c = readS0(a, arrayA, i);
                    buffer[i] = (byte) (c > 0x7f ? '?' : c);
                    TStringConstants.truffleSafePointPoll(this, i + 1);
                }
                return TransCodeIntlNode.create(a, buffer, buffer.length, 0, targetEncoding, codePointLengthA, TSCodeRange.get7Bit(), true);
            } else {
                return transCodeIntlNode.execute(a, arrayA, codePointLengthA, codeRangeA, Encoding.get(a.encoding()), targetEncoding);
            }
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class TransCodeIntlNode extends Node {

        abstract TruffleString execute(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, Encoding targetEncoding);

        @SuppressWarnings("unused")
        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "isAscii(targetEncoding) || isBytes(targetEncoding)"})
        TruffleString targetAscii(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, Encoding targetEncoding,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.NextNode iteratorNextNode) {
            assert !is7Bit(codeRangeA);
            byte[] buffer = new byte[codePointLengthA];
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, sourceEncoding);
            int i = 0;
            while (it.hasNext()) {
                int codepoint = iteratorNextNode.execute(it);
                buffer[i++] = codepoint > 0x7f ? (byte) '?' : (byte) codepoint;
                TStringConstants.truffleSafePointPoll(this, i);
            }
            return create(a, buffer, buffer.length, 0, targetEncoding, codePointLengthA, TSCodeRange.get7Bit(), true);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "isLatin1(targetEncoding)"})
        TruffleString latin1Transcode(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, Encoding targetEncoding,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.NextNode iteratorNextNode) {
            assert !is7Or8Bit(codeRangeA);
            byte[] buffer = new byte[codePointLengthA];
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, sourceEncoding);
            int codeRange = TSCodeRange.get7Bit();
            int i = 0;
            while (it.hasNext()) {
                int codepoint = iteratorNextNode.execute(it);
                byte latin1 = codepoint > 0xff ? (byte) '?' : (byte) codepoint;
                buffer[i++] = latin1;
                if (latin1 < 0) {
                    codeRange = TSCodeRange.get8Bit();
                }
                TStringConstants.truffleSafePointPoll(this, i);
            }
            return create(a, buffer, codePointLengthA, 0, Encoding.ISO_8859_1, codePointLengthA, codeRange, true);
        }

        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "!isLarge(codePointLengthA)", "isUTF8(targetEncoding)"})
        TruffleString utf8TranscodeRegular(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, @SuppressWarnings("unused") Encoding targetEncoding,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.NextNode iteratorNextNode,
                        @Cached @Shared("brokenProfile") ConditionProfile brokenProfile,
                        @Cached @Shared("outOfMemoryProfile") BranchProfile outOfMemoryProfile) {
            return utf8Transcode(a, arrayA, codePointLengthA, codeRangeA, sourceEncoding, iteratorNextNode, false, brokenProfile, outOfMemoryProfile);
        }

        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "isLarge(codePointLengthA)", "isUTF8(targetEncoding)"})
        TruffleString utf8TranscodeLarge(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, @SuppressWarnings("unused") Encoding targetEncoding,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.NextNode iteratorNextNode,
                        @Cached @Shared("brokenProfile") ConditionProfile brokenProfile,
                        @Cached @Shared("outOfMemoryProfile") BranchProfile outOfMemoryProfile) {
            return utf8Transcode(a, arrayA, codePointLengthA, codeRangeA, sourceEncoding, iteratorNextNode, true, brokenProfile, outOfMemoryProfile);
        }

        static boolean isLarge(int codePointLengthA) {
            return codePointLengthA > TStringConstants.MAX_ARRAY_SIZE / 4;
        }

        private TruffleString utf8Transcode(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        TruffleStringIterator.NextNode iteratorNextNode, boolean isLarge, ConditionProfile brokenProfile, BranchProfile outOfMemoryProfile) {
            assert !is7Bit(codeRangeA);
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, sourceEncoding);
            byte[] buffer = new byte[isLarge ? TStringConstants.MAX_ARRAY_SIZE : codePointLengthA * 4];
            int codeRange = TSCodeRange.getValidMultiByte();
            int length = 0;
            int loopCount = 0;
            while (it.hasNext()) {
                int codepoint = iteratorNextNode.execute(it);
                if (Encodings.isUTF16Surrogate(codepoint) || Integer.toUnsignedLong(codepoint) > Character.MAX_CODE_POINT) {
                    codeRange = TSCodeRange.getBrokenMultiByte();
                    codepoint = Encodings.invalidCodepoint();
                }
                int n = Encodings.utf8EncodedSize(codepoint);
                assert isLarge || length + n <= buffer.length;
                if (isLarge && length > TStringConstants.MAX_ARRAY_SIZE - n) {
                    outOfMemoryProfile.enter();
                    throw InternalErrors.outOfMemory();
                }
                Encodings.utf8Encode(codepoint, buffer, length, n);
                length += n;
                TStringConstants.truffleSafePointPoll(this, ++loopCount);
            }
            final int codePointLength;
            if (isBrokenMultiByte(codeRange)) {
                long attrs = TStringOps.calcStringAttributesUTF8(this, buffer, 0, length, false, false, brokenProfile);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            } else {
                codePointLength = codePointLengthA;
            }
            return create(a, Arrays.copyOf(buffer, length), length, 0, Encoding.UTF_8, codePointLength, codeRange, isBrokenMultiByte(codeRange));
        }

        @Specialization(guards = {"isUTF32(sourceEncoding)", "isUTF16(targetEncoding)"})
        TruffleString utf16Fixed32Bit(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, @SuppressWarnings("unused") Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding) {
            assert TStringGuards.isValidFixedWidth(codeRangeA) || TStringGuards.isBrokenFixedWidth(codeRangeA);
            assert isStride2(a);
            byte[] buffer = new byte[codePointLengthA * 4];
            int length = 0;
            int codeRange = TStringGuards.isValidFixedWidth(codeRangeA) ? TSCodeRange.getValidMultiByte() : TSCodeRange.getBrokenMultiByte();
            for (int i = 0; i < a.length(); i++) {
                int codepoint = TStringOps.readS2(a, arrayA, i);
                length += Encodings.utf16Encode(codepoint, buffer, length);
                TStringConstants.truffleSafePointPoll(this, i + 1);
            }
            final int codePointLength;
            if (isBrokenMultiByte(codeRange)) {
                long attrs = TStringOps.calcStringAttributesUTF16(this, buffer, 0, length, false);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            } else {
                codePointLength = codePointLengthA;
            }
            return create(a, Arrays.copyOf(buffer, length * 2), length, 1, Encoding.UTF_16, codePointLength, codeRange, isBrokenMultiByte(codeRange));
        }

        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "!isFixedWidth(codeRangeA)", "!isLarge(codePointLengthA)", "isUTF16(targetEncoding)"})
        TruffleString utf16TranscodeRegular(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, @SuppressWarnings("unused") Encoding targetEncoding,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.NextNode iteratorNextNode,
                        @Cached @Shared("outOfMemoryProfile") BranchProfile outOfMemoryProfile) {
            return utf16Transcode(a, arrayA, codePointLengthA, codeRangeA, sourceEncoding, iteratorNextNode, false, outOfMemoryProfile);
        }

        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "!isFixedWidth(codeRangeA)", "isLarge(codePointLengthA)", "isUTF16(targetEncoding)"})
        TruffleString utf16TranscodeLarge(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, @SuppressWarnings("unused") Encoding targetEncoding,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.NextNode iteratorNextNode,
                        @Cached @Shared("outOfMemoryProfile") BranchProfile outOfMemoryProfile) {
            return utf16Transcode(a, arrayA, codePointLengthA, codeRangeA, sourceEncoding, iteratorNextNode, true, outOfMemoryProfile);
        }

        private TruffleString utf16Transcode(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        TruffleStringIterator.NextNode iteratorNextNode, boolean isLarge, BranchProfile outOfMemoryProfile) {
            assert TStringGuards.isValidBrokenOrUnknownMultiByte(codeRangeA);
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, sourceEncoding);
            byte[] buffer = new byte[codePointLengthA];
            int codePointLength = codePointLengthA;
            int length = 0;
            int codeRange = TSCodeRange.get7Bit();
            while (it.hasNext()) {
                int curIndex = it.getRawIndex();
                int codepoint = iteratorNextNode.execute(it);
                if (codepoint > 0xff) {
                    buffer = TStringOps.arraycopyOfWithStride(this, buffer, 0, length, 0, codePointLengthA, 1);
                    codeRange = TSCodeRange.get16Bit();
                    it.setRawIndex(curIndex);
                    break;
                }
                if (codepoint > 0x7f) {
                    codeRange = TSCodeRange.get8Bit();
                }
                buffer[length++] = (byte) codepoint;
                TStringConstants.truffleSafePointPoll(this, length);
            }
            if (!it.hasNext()) {
                assert length == codePointLengthA;
                return create(a, buffer, length, 0, Encoding.UTF_16, codePointLengthA, codeRange, false);
            }
            while (it.hasNext()) {
                int curIndex = it.getRawIndex();
                int codepoint = iteratorNextNode.execute(it);
                if (codepoint > 0xffff) {
                    buffer = Arrays.copyOf(buffer, isLarge ? TStringConstants.MAX_ARRAY_SIZE : buffer.length * 2);
                    codeRange = TSCodeRange.commonCodeRange(codeRange, TSCodeRange.getValidMultiByte());
                    it.setRawIndex(curIndex);
                    break;
                }
                if (Encodings.isUTF16Surrogate(codepoint)) {
                    codeRange = TSCodeRange.getBrokenMultiByte();
                }
                writeToByteArray(buffer, 1, length++, codepoint);
                TStringConstants.truffleSafePointPoll(this, length);
            }
            if (!it.hasNext()) {
                assert length == codePointLengthA;
                if (isBrokenMultiByte(codeRange)) {
                    long attrs = TStringOps.calcStringAttributesUTF16(this, buffer, 0, length, false);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                    codeRange = StringAttributes.getCodeRange(attrs);
                }
                return create(a, buffer, length, 1, Encoding.UTF_16, codePointLength, codeRange, isBrokenMultiByte(codeRange));
            }
            int loopCount = 0;
            while (it.hasNext()) {
                int codepoint = iteratorNextNode.execute(it);
                if (Encodings.isUTF16Surrogate(codepoint) || Integer.toUnsignedLong(codepoint) > Character.MAX_CODE_POINT) {
                    codeRange = TSCodeRange.getBrokenMultiByte();
                }
                if (isLarge && length + Encodings.utf16EncodedSize(codepoint) > TStringConstants.MAX_ARRAY_SIZE_S1) {
                    outOfMemoryProfile.enter();
                    throw InternalErrors.outOfMemory();
                }
                length += Encodings.utf16Encode(codepoint, buffer, length);
                TStringConstants.truffleSafePointPoll(this, ++loopCount);
            }
            if (isBrokenMultiByte(codeRange)) {
                long attrs = TStringOps.calcStringAttributesUTF16(this, buffer, 0, length, false);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            }
            return create(a, Arrays.copyOf(buffer, length * 2), length, 1, Encoding.UTF_16, codePointLength, codeRange, isBrokenMultiByte(codeRange));
        }

        @Specialization(guards = {"!isUTF16(sourceEncoding)", "isSupportedEncoding(sourceEncoding)", "!isFixedWidth(codeRangeA)", "!isLarge(codePointLengthA)", "isUTF32(targetEncoding)"})
        TruffleString utf32TranscodeRegular(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, @SuppressWarnings("unused") Encoding targetEncoding,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.NextNode iteratorNextNode) {
            return utf32Transcode(a, arrayA, codePointLengthA, codeRangeA, sourceEncoding, iteratorNextNode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "!isFixedWidth(codeRangeA)", "isLarge(codePointLengthA)", "isUTF32(targetEncoding)"})
        static TruffleString utf32TranscodeLarge(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, Encoding targetEncoding) {
            throw InternalErrors.outOfMemory();
        }

        @Specialization(guards = {"isUTF16(sourceEncoding)", "!isFixedWidth(codeRangeA)", "!isLarge(codePointLengthA)", "isUTF32(targetEncoding)"})
        TruffleString utf32TranscodeUTF16(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, @SuppressWarnings("unused") Encoding targetEncoding,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.NextNode iteratorNextNode) {
            assert containsSurrogates(a);
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, sourceEncoding);
            byte[] buffer = new byte[codePointLengthA << 2];
            int length = 0;
            while (it.hasNext()) {
                writeToByteArray(buffer, 2, length++, iteratorNextNode.execute(it));
                TStringConstants.truffleSafePointPoll(this, length);
            }
            assert length == codePointLengthA;
            boolean isBroken = isBrokenMultiByte(codeRangeA);
            return create(a, buffer, length, 2, Encoding.UTF_32, codePointLengthA, isBroken ? TSCodeRange.getBrokenFixedWidth() : TSCodeRange.getValidFixedWidth(), isBroken);
        }

        private TruffleString utf32Transcode(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, TruffleStringIterator.NextNode iteratorNextNode) {
            assert TStringGuards.isValidBrokenOrUnknownMultiByte(codeRangeA);
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, sourceEncoding);
            byte[] buffer = new byte[codePointLengthA];
            int length = 0;
            int codeRange = TSCodeRange.get7Bit();
            int codepoint = 0;
            while (it.hasNext()) {
                int curIndex = it.getRawIndex();
                codepoint = iteratorNextNode.execute(it);
                if (codepoint > 0xff) {
                    if (Encodings.isUTF16Surrogate(codepoint)) {
                        buffer = TStringOps.arraycopyOfWithStride(this, buffer, 0, length, 0, codePointLengthA, 2);
                        codeRange = TSCodeRange.getBrokenFixedWidth();
                    } else {
                        buffer = TStringOps.arraycopyOfWithStride(this, buffer, 0, length, 0, codePointLengthA, 1);
                        codeRange = TSCodeRange.get16Bit();
                    }
                    it.setRawIndex(curIndex);
                    break;
                }
                if (codepoint > 0x7f) {
                    codeRange = TSCodeRange.get8Bit();
                }
                buffer[length++] = (byte) codepoint;
                TStringConstants.truffleSafePointPoll(this, length);
            }
            if (!it.hasNext()) {
                assert length == codePointLengthA;
                return create(a, buffer, length, 0, Encoding.UTF_32, codePointLengthA, codeRange, isBrokenFixedWidth(codeRange));
            }
            if (is16Bit(codeRange)) {
                while (it.hasNext()) {
                    int curIndex = it.getRawIndex();
                    codepoint = iteratorNextNode.execute(it);
                    if (codepoint > 0xffff || Encodings.isUTF16Surrogate(codepoint)) {
                        buffer = TStringOps.arraycopyOfWithStride(this, buffer, 0, length, 1, codePointLengthA, 2);
                        codeRange = Encodings.isValidUnicodeCodepoint(codepoint) ? TSCodeRange.getValidFixedWidth() : TSCodeRange.getBrokenFixedWidth();
                        it.setRawIndex(curIndex);
                        break;
                    }
                    writeToByteArray(buffer, 1, length++, codepoint);
                    TStringConstants.truffleSafePointPoll(this, length);
                }
            }
            if (!it.hasNext()) {
                assert length == codePointLengthA;
                return create(a, buffer, length, 1, Encoding.UTF_32, codePointLengthA, codeRange, isBrokenFixedWidth(codeRange));
            }
            while (it.hasNext()) {
                codepoint = iteratorNextNode.execute(it);
                if (!Encodings.isValidUnicodeCodepoint(codepoint)) {
                    codeRange = TSCodeRange.getBrokenFixedWidth();
                }
                writeToByteArray(buffer, 2, length++, codepoint);
                TStringConstants.truffleSafePointPoll(this, length);
            }
            return create(a, buffer, length, 2, Encoding.UTF_32, codePointLengthA, codeRange, isBrokenFixedWidth(codeRange));
        }

        @Specialization(guards = {"isUnsupportedEncoding(sourceEncoding) || isUnsupportedEncoding(targetEncoding)"})
        TruffleString unsupported(AbstractTruffleString a, Object arrayA, int codePointLengthA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding sourceEncoding,
                        Encoding targetEncoding,
                        @Cached BranchProfile outOfMemoryProfile,
                        @Cached ConditionProfile nativeProfile,
                        @Cached FromBufferWithStringCompactionNode fromBufferWithStringCompactionNode) {
            return JCodings.getInstance().transcode(this, a, arrayA, codePointLengthA, targetEncoding, outOfMemoryProfile, nativeProfile, fromBufferWithStringCompactionNode);
        }

        private static TruffleString create(AbstractTruffleString a, byte[] buffer, int length, int stride, Encoding encoding, int codePointLength, int codeRange, boolean isCacheHead) {
            return TruffleString.createFromByteArray(buffer, length, stride, encoding, codePointLength, codeRange, isCacheHead || a.isMutable());
        }

        @TruffleBoundary
        private static boolean containsSurrogates(AbstractTruffleString a) {
            CompilerAsserts.neverPartOfCompilation();
            for (int i = 0; i < a.length(); i++) {
                if (Encodings.isUTF16Surrogate(a.readCharUTF16Uncached(i))) {
                    return true;
                }
            }
            return false;
        }
    }
}
