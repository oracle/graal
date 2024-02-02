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
import static com.oracle.truffle.api.strings.AbstractTruffleString.checkByteLengthUTF16;
import static com.oracle.truffle.api.strings.AbstractTruffleString.checkByteLengthUTF32;
import static com.oracle.truffle.api.strings.Encodings.UTF8_ACCEPT;
import static com.oracle.truffle.api.strings.Encodings.UTF8_REJECT;
import static com.oracle.truffle.api.strings.Encodings.isUTF16Surrogate;
import static com.oracle.truffle.api.strings.TSCodeRange.isBroken;
import static com.oracle.truffle.api.strings.TSCodeRange.isPrecise;
import static com.oracle.truffle.api.strings.TStringGuards.indexOfCannotMatch;
import static com.oracle.truffle.api.strings.TStringGuards.is16Bit;
import static com.oracle.truffle.api.strings.TStringGuards.is7Bit;
import static com.oracle.truffle.api.strings.TStringGuards.is7Or8Bit;
import static com.oracle.truffle.api.strings.TStringGuards.is8Bit;
import static com.oracle.truffle.api.strings.TStringGuards.isAscii;
import static com.oracle.truffle.api.strings.TStringGuards.isAsciiBytesOrLatin1;
import static com.oracle.truffle.api.strings.TStringGuards.isBrokenFixedWidth;
import static com.oracle.truffle.api.strings.TStringGuards.isBrokenMultiByte;
import static com.oracle.truffle.api.strings.TStringGuards.isBuiltin;
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
import static com.oracle.truffle.api.strings.TStringGuards.isValid;
import static com.oracle.truffle.api.strings.TStringGuards.isValidFixedWidth;
import static com.oracle.truffle.api.strings.TStringOps.readS0;
import static com.oracle.truffle.api.strings.TStringOps.writeToByteArray;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.Node;
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
    abstract static class GetCodeRangeForIndexCalculationNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Encoding encoding);

        @Specialization
        static int get(Node node, AbstractTruffleString a, Encoding encoding,
                        @Cached InlinedConditionProfile impreciseProfile,
                        @Cached TruffleString.ToIndexableNode toIndexableNode,
                        @Cached CalcStringAttributesNode calcStringAttributesNode) {
            int codeRange = a.codeRange();
            if (impreciseProfile.profile(node, !TSCodeRange.isPrecise(codeRange) && !isFixedWidth(codeRange))) {
                return StringAttributes.getCodeRange(updateAttributes(node, a, encoding, codeRange, toIndexableNode, calcStringAttributesNode));
            }
            return codeRange;
        }
    }

    /**
     * Gets a string's code range with enough precision to decide whether the string is valid.
     */
    abstract static class GetValidOrBrokenCodeRangeNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Encoding encoding);

        @Specialization
        static int get(Node node, AbstractTruffleString a, Encoding encoding,
                        @Cached InlinedConditionProfile impreciseProfile,
                        @Cached TruffleString.ToIndexableNode toIndexableNode,
                        @Cached CalcStringAttributesNode calcStringAttributesNode) {
            int codeRange = a.codeRange();
            if (impreciseProfile.profile(node, !TSCodeRange.isPrecise(codeRange) && TSCodeRange.isBroken(codeRange))) {
                return StringAttributes.getCodeRange(updateAttributes(node, a, encoding, codeRange, toIndexableNode, calcStringAttributesNode));
            }
            return codeRange;
        }
    }

    abstract static class GetPreciseCodeRangeNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Encoding encoding);

        @Specialization
        static int get(Node node, AbstractTruffleString a, Encoding encoding,
                        @Cached InlinedConditionProfile impreciseProfile,
                        @Cached TruffleString.ToIndexableNode toIndexableNode,
                        @Cached CalcStringAttributesNode calcStringAttributesNode) {
            int codeRange = a.codeRange();
            if (impreciseProfile.profile(node, !TSCodeRange.isPrecise(codeRange))) {
                return StringAttributes.getCodeRange(updateAttributes(node, a, encoding, codeRange, toIndexableNode, calcStringAttributesNode));
            }
            return codeRange;
        }
    }

    abstract static class GetCodePointLengthNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Encoding encoding);

        @Specialization
        static int get(Node node, AbstractTruffleString a, Encoding encoding,
                        @Cached InlinedConditionProfile cacheMissProfile,
                        @Cached TruffleString.ToIndexableNode toIndexableNode,
                        @Cached CalcStringAttributesNode calcStringAttributesNode) {
            int codePointLength = a.codePointLength();
            if (cacheMissProfile.profile(node, codePointLength < 0)) {
                return StringAttributes.getCodePointLength(updateAttributes(node, a, encoding, a.codeRange(), toIndexableNode, calcStringAttributesNode));
            }
            return codePointLength;
        }

        static GetCodePointLengthNode getUncached() {
            return TStringInternalNodesFactory.GetCodePointLengthNodeGen.getUncached();
        }
    }

    private static long updateAttributes(Node node, AbstractTruffleString a, Encoding encoding, int impreciseCodeRange,
                    TruffleString.ToIndexableNode toIndexableNode,
                    CalcStringAttributesNode calcStringAttributesNode) {
        assert !TSCodeRange.isPrecise(impreciseCodeRange);
        long attrs = calcStringAttributesNode.execute(node, a, toIndexableNode.execute(node, a, a.data()), a.offset(), a.length(), a.stride(), encoding, 0, impreciseCodeRange);
        a.updateAttributes(StringAttributes.getCodePointLength(attrs), StringAttributes.getCodeRange(attrs));
        return attrs;
    }

    abstract static class FromBufferWithStringCompactionNode extends AbstractInternalNode {

        abstract TruffleString execute(Node node, Object arrayA, int offsetA, int byteLength, Encoding encoding, boolean copy, boolean isCacheHead);

        @Specialization
        static TruffleString fromBufferWithStringCompaction(Node node, Object arrayA, int offsetA, int byteLength, Encoding encoding, boolean copy, boolean isCacheHead,
                        @Cached InlinedConditionProfile asciiLatinBytesProfile,
                        @Cached InlinedConditionProfile utf8Profile,
                        @Cached InlinedConditionProfile utf8BrokenProfile,
                        @Cached InlinedConditionProfile utf16Profile,
                        @Cached InlinedConditionProfile utf16CompactProfile,
                        @Cached InlinedConditionProfile utf32Profile,
                        @Cached InlinedConditionProfile utf32Compact0Profile,
                        @Cached InlinedConditionProfile utf32Compact1Profile,
                        @Cached InlinedConditionProfile exoticValidProfile,
                        @Cached InlinedConditionProfile exoticFixedWidthProfile) {
            final int offset;
            final int length;
            final int stride;
            final int codePointLength;
            final int codeRange;
            final Object array;
            if (utf16Profile.profile(node, isUTF16(encoding))) {
                checkByteLengthUTF16(byteLength);
                length = byteLength >> 1;
                long attrs = TStringOps.calcStringAttributesUTF16(node, arrayA, offsetA, length, false);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
                stride = Stride.fromCodeRangeUTF16(codeRange);
                if (copy || stride == 0) {
                    offset = 0;
                    array = new byte[length << stride];
                    if (utf16CompactProfile.profile(node, stride == 0)) {
                        TStringOps.arraycopyWithStride(node, arrayA, offsetA, 1, 0, array, offset, 0, 0, length);
                    } else {
                        TStringOps.arraycopyWithStride(node, arrayA, offsetA, 1, 0, array, offset, 1, 0, length);
                    }
                } else {
                    offset = offsetA;
                    array = arrayA;
                }
            } else if (utf32Profile.profile(node, isUTF32(encoding))) {
                checkByteLengthUTF32(byteLength);
                length = byteLength >> 2;
                codeRange = TStringOps.calcStringAttributesUTF32(node, arrayA, offsetA, length);
                codePointLength = length;
                stride = Stride.fromCodeRangeUTF32(codeRange);
                if (copy || stride < 2) {
                    offset = 0;
                    array = new byte[length << stride];
                    if (utf32Compact0Profile.profile(node, stride == 0)) {
                        TStringOps.arraycopyWithStride(node, arrayA, offsetA, 2, 0, array, offset, 0, 0, length);
                    } else if (utf32Compact1Profile.profile(node, stride == 1)) {
                        TStringOps.arraycopyWithStride(node, arrayA, offsetA, 2, 0, array, offset, 1, 0, length);
                    } else {
                        TStringOps.arraycopyWithStride(node, arrayA, offsetA, 2, 0, array, offset, 2, 0, length);
                    }
                } else {
                    offset = offsetA;
                    array = arrayA;
                }
            } else {
                length = byteLength;
                stride = 0;
                if (utf8Profile.profile(node, isUTF8(encoding))) {
                    long attrs = TStringOps.calcStringAttributesUTF8(node, arrayA, offsetA, length, false, false, utf8BrokenProfile);
                    codeRange = StringAttributes.getCodeRange(attrs);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                } else if (asciiLatinBytesProfile.profile(node, isAsciiBytesOrLatin1(encoding))) {
                    int cr = TStringOps.calcStringAttributesLatin1(node, arrayA, offsetA, length);
                    codeRange = is8Bit(cr) ? TSCodeRange.asciiLatinBytesNonAsciiCodeRange(encoding) : cr;
                    codePointLength = length;
                } else {
                    if (arrayA instanceof NativePointer) {
                        ((NativePointer) arrayA).materializeByteArray(node, offsetA, length << stride, InlinedConditionProfile.getUncached());
                    }
                    long attrs = JCodings.getInstance().calcStringAttributes(node, arrayA, offsetA, length, encoding, 0, exoticValidProfile, exoticFixedWidthProfile);
                    codeRange = StringAttributes.getCodeRange(attrs);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                }
                if (copy) {
                    offset = 0;
                    array = TStringOps.arraycopyOfWithStride(node, arrayA, offsetA, length, 0, length, 0);
                } else {
                    offset = offsetA;
                    array = arrayA;
                }
            }
            return TruffleString.createFromArray(array, offset, length, stride, encoding, codePointLength, codeRange, isCacheHead);
        }
    }

    abstract static class FromBufferWithStringCompactionKnownAttributesNode extends AbstractInternalNode {

        final TruffleString execute(Node node, AbstractTruffleString a, Encoding encoding) {
            return execute(node, a, true, encoding);
        }

        abstract TruffleString execute(Node node, AbstractTruffleString a, boolean isCacheHead, Encoding encoding);

        @Specialization
        static TruffleString fromBufferWithStringCompaction(Node node, AbstractTruffleString a, boolean isCacheHead, Encoding encoding,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached GetPreciseCodeRangeNode getPreciseCodeRangeNode,
                        @Cached InlinedConditionProfile utf16Profile,
                        @Cached InlinedConditionProfile utf16CompactProfile,
                        @Cached InlinedConditionProfile utf32Profile,
                        @Cached InlinedConditionProfile utf32Compact0Profile,
                        @Cached InlinedConditionProfile utf32Compact1Profile) {
            final int codeRange = getPreciseCodeRangeNode.execute(node, a, encoding);
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
            if (utf16Profile.profile(node, isUTF16(encoding))) {
                stride = Stride.fromCodeRangeUTF16(codeRange);
                array = new byte[length << stride];
                if (utf16CompactProfile.profile(node, strideA == 1 && stride == 0)) {
                    TStringOps.arraycopyWithStride(node, arrayA, offsetA, 1, 0, array, offset, 0, 0, length);
                } else {
                    assert stride == strideA;
                    TStringOps.arraycopyWithStride(node, arrayA, offsetA, 0, 0, array, offset, 0, 0, length << stride);
                }
            } else if (utf32Profile.profile(node, isUTF32(encoding))) {
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
                stride = 0;
                array = TStringOps.arraycopyOfWithStride(node, arrayA, offsetA, length, 0, length, 0);
            }
            int codePointLength = getCodePointLengthNode.execute(node, a, encoding);
            return TruffleString.createFromArray(array, offset, length, stride, encoding, codePointLength, codeRange, isCacheHead);
        }

        static FromBufferWithStringCompactionKnownAttributesNode getUncached() {
            return TStringInternalNodesFactory.FromBufferWithStringCompactionKnownAttributesNodeGen.getUncached();
        }
    }

    abstract static class FromNativePointerNode extends AbstractInternalNode {

        abstract TruffleString execute(Node node, NativePointer pointer, int byteOffset, int byteLength, Encoding encoding, boolean isCacheHead);

        @Specialization
        static TruffleString fromNativePointerInternal(Node node, NativePointer pointer, int byteOffset, int byteLength, Encoding encoding, boolean isCacheHead,
                        @Cached InlinedConditionProfile asciiLatinBytesProfile,
                        @Cached InlinedConditionProfile utf8Profile,
                        @Cached InlinedConditionProfile utf8BrokenProfile,
                        @Cached InlinedConditionProfile utf16Profile,
                        @Cached InlinedConditionProfile utf32Profile,
                        @Cached InlinedConditionProfile exoticValidProfile,
                        @Cached InlinedConditionProfile exoticFixedWidthProfile) {
            final int length;
            final int stride;
            final int codePointLength;
            final int codeRange;
            if (utf16Profile.profile(node, isUTF16(encoding))) {
                checkByteLengthUTF16(byteLength);
                length = byteLength >> 1;
                long attrs = TStringOps.calcStringAttributesUTF16(node, pointer, byteOffset, length, false);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
                stride = 1;
            } else if (utf32Profile.profile(node, isUTF32(encoding))) {
                checkByteLengthUTF32(byteLength);
                length = byteLength >> 2;
                codeRange = TStringOps.calcStringAttributesUTF32(node, pointer, byteOffset, length);
                codePointLength = length;
                stride = 2;
            } else {
                length = byteLength;
                stride = 0;
                if (utf8Profile.profile(node, isUTF8(encoding))) {
                    long attrs = TStringOps.calcStringAttributesUTF8(node, pointer, byteOffset, length, false, false, utf8BrokenProfile);
                    codeRange = StringAttributes.getCodeRange(attrs);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                } else if (asciiLatinBytesProfile.profile(node, isAsciiBytesOrLatin1(encoding))) {
                    int cr = TStringOps.calcStringAttributesLatin1(node, pointer, byteOffset, length);
                    codeRange = is8Bit(cr) ? TSCodeRange.asciiLatinBytesNonAsciiCodeRange(encoding) : cr;
                    codePointLength = length;
                } else {
                    pointer.materializeByteArray(node, byteOffset, byteLength, InlinedConditionProfile.getUncached());
                    long attrs = JCodings.getInstance().calcStringAttributes(node, pointer, byteOffset, length, encoding, 0, exoticValidProfile, exoticFixedWidthProfile);
                    codeRange = StringAttributes.getCodeRange(attrs);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                }
            }
            return TruffleString.createFromArray(pointer, byteOffset, length, stride, encoding, codePointLength, codeRange, isCacheHead);
        }
    }

    abstract static class ByteLengthOfCodePointNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling);

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
        @Specialization(guards = {"isAscii(encoding)", "isBroken(codeRangeA)", "isReturnNegative(errorHandling)"})
        int doASCIIBrokenReturnNegative(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling) {
            assert isStride0(a);
            return readS0(a, arrayA, index) < 0x80 ? 1 : -1;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isUTF32(encoding)", "isBroken(codeRangeA)", "isReturnNegative(errorHandling)"})
        static int doUTF32BrokenReturnNegative(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling,
                        @Cached CodePointAtRawNode codePointAtRawNode) {
            return codePointAtRawNode.execute(node, a, arrayA, codeRangeA, encoding, index, ErrorHandling.RETURN_NEGATIVE) < 0 ? -1 : 4;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isUTF8(encoding)", "isValid(codeRangeA)"})
        int utf8Valid(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling) {
            assert isStride0(a);
            int firstByte = readS0(a, arrayA, index);
            return firstByte <= 0x7f ? 1 : Encodings.utf8CodePointLength(firstByte);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isUTF8(encoding)", "isBroken(codeRangeA)"})
        int utf8Broken(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling) {
            assert isStride0(a);
            return Encodings.utf8GetCodePointLength(a, arrayA, index, errorHandling.errorHandler);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isUTF16(encoding)", "isValid(codeRangeA)"})
        int utf16Valid(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int index, ErrorHandling errorHandling) {
            assert isStride1(a);
            return Encodings.isUTF16HighSurrogate(TStringOps.readS1(a, arrayA, index)) ? 4 : 2;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isUTF16(encoding)", "isBroken(codeRangeA)"})
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

    abstract static class RawIndexToCodePointIndexNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int byteOffset, int index);

        @SuppressWarnings("unused")
        @Specialization(guards = "isFixedWidth(codeRangeA)")
        int doFixed(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int byteOffset, int index) {
            return index;
        }

        @Specialization(guards = {"isUTF8(encoding)", "isValid(codeRangeA)"})
        static int utf8Valid(Node node, AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int byteOffset, int index,
                        @Shared("broken") @Cached InlinedConditionProfile brokenProfile) {
            return StringAttributes.getCodePointLength(TStringOps.calcStringAttributesUTF8(node, arrayA, a.offset() + byteOffset, index, true, byteOffset + index == a.length(), brokenProfile));
        }

        @Specialization(guards = {"isUTF8(encoding)", "isBroken(codeRangeA)"})
        static int utf8Broken(Node node, @SuppressWarnings("unused") AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding,
                        int byteOffset,
                        int index,
                        @Shared("broken") @Cached InlinedConditionProfile brokenProfile) {
            return StringAttributes.getCodePointLength(TStringOps.calcStringAttributesUTF8(node, arrayA, a.offset() + byteOffset, index, false, false, brokenProfile));
        }

        @Specialization(guards = {"isUTF16(encoding)", "isValid(codeRangeA)"})
        int utf16Valid(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int byteOffset, int index) {
            assert isStride1(a);
            return StringAttributes.getCodePointLength(TStringOps.calcStringAttributesUTF16(this, arrayA, a.offset() + byteOffset, index, true));
        }

        @Specialization(guards = {"isUTF16(encoding)", "isBroken(codeRangeA)"})
        int utf16Broken(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int byteOffset, int index) {
            assert isStride1(a);
            return StringAttributes.getCodePointLength(TStringOps.calcStringAttributesUTF16(this, arrayA, a.offset() + byteOffset, index, false));
        }

        @TruffleBoundary
        @Specialization(guards = "isUnsupportedEncoding(encoding)")
        static int unsupported(Node node, @SuppressWarnings("unused") AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, Encoding encoding, int byteOffset, int index,
                        @Exclusive @Cached InlinedConditionProfile validProfile,
                        @Shared("broken") @Cached InlinedConditionProfile fixedWidthProfile) {
            return StringAttributes.getCodePointLength(JCodings.getInstance().calcStringAttributes(node, arrayA, a.offset(), index, encoding, byteOffset, validProfile, fixedWidthProfile));
        }
    }

    abstract static class CodePointIndexToRawNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int extraOffsetRaw, int index, boolean isLength);

        @SuppressWarnings("unused")
        @Specialization(guards = "isFixedWidth(codeRangeA)")
        int doFixed(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int extraOffsetRaw, int index, boolean isLength) {
            return index;
        }

        @Specialization(guards = {"isUTF8(encoding)", "isValid(codeRangeA)"})
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

        @Specialization(guards = {"isUTF8(encoding)", "isBroken(codeRangeA)"})
        int utf8Broken(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int extraOffsetRaw, int index,
                        boolean isLength) {
            assert isStride0(a);
            int cpi = 0;
            for (int i = extraOffsetRaw; i < a.length(); i += Encodings.utf8GetCodePointLength(a, arrayA, i, DecodingErrorHandler.DEFAULT)) {
                if (cpi == index) {
                    return i - extraOffsetRaw;
                }
                cpi++;
                TStringConstants.truffleSafePointPoll(this, cpi);
            }
            return atEnd(a, extraOffsetRaw, index, isLength, cpi);
        }

        @Specialization(guards = {"isUTF16(encoding)", "isValid(codeRangeA)"})
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

        @Specialization(guards = {"isUTF16(encoding)", "isBroken(codeRangeA)"})
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

    abstract static class ReadByteNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Object arrayA, int i, Encoding encoding);

        @Specialization(guards = "isUTF16(encoding)")
        static int doUTF16(Node node, AbstractTruffleString a, Object arrayA, int i, @SuppressWarnings("unused") Encoding encoding,
                        @Shared("stride0") @Cached InlinedConditionProfile stride0Profile) {
            a.boundsCheckByteIndexUTF16(i);
            final int index;
            if (stride0Profile.profile(node, isStride0(a))) {
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
        static int doUTF32(Node node, AbstractTruffleString a, Object arrayA, int i, @SuppressWarnings("unused") Encoding encoding,
                        @Shared("stride0") @Cached InlinedConditionProfile stride0Profile,
                        @Exclusive @Cached InlinedConditionProfile stride1Profile) {
            a.boundsCheckByteIndexUTF32(i);
            final int index;
            if (stride0Profile.profile(node, isStride0(a))) {
                if ((i & 3) != (TStringGuards.bigEndian() ? 3 : 0)) {
                    return 0;
                } else {
                    index = i >> 2;
                }
            } else if (stride1Profile.profile(node, isStride1(a))) {
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

    abstract static class CodePointAtNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int i, ErrorHandling errorHandling);

        @Specialization(guards = "isUTF16(encoding)")
        static int utf16(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i, ErrorHandling errorHandling,
                        @Cached InlinedConditionProfile fixedWidthProfile,
                        @Shared("stride0") @Cached InlinedConditionProfile stride0Profile,
                        @Shared("valid") @Cached InlinedConditionProfile validProfile) {
            if (fixedWidthProfile.profile(node, isFixedWidth(codeRangeA))) {
                if (stride0Profile.profile(node, isStride0(a))) {
                    return TStringOps.readS0(a, arrayA, i);
                } else {
                    assert isStride1(a);
                    return TStringOps.readS1(a, arrayA, i);
                }
            } else if (validProfile.profile(node, isValid(codeRangeA))) {
                assert isStride1(a);
                return Encodings.utf16DecodeValid(a, arrayA, Encodings.utf16ValidCodePointToCharIndex(node, a, arrayA, i));
            } else {
                assert isStride1(a);
                assert TStringGuards.isBroken(codeRangeA);
                return Encodings.utf16DecodeBroken(a, arrayA, Encodings.utf16BrokenCodePointToCharIndex(node, a, arrayA, i), errorHandling);
            }
        }

        @Specialization(guards = "isUTF32(encoding)")
        static int utf32(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int i, ErrorHandling errorHandling,
                        @Shared("stride0") @Cached InlinedConditionProfile stride0Profile,
                        @Exclusive @Cached InlinedConditionProfile stride1Profile) {
            return CodePointAtRawNode.utf32(node, a, arrayA, codeRangeA, encoding, i, errorHandling, stride0Profile, stride1Profile);
        }

        @Specialization(guards = "isUTF8(encoding)")
        static int utf8(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i, ErrorHandling errorHandling,
                        @Exclusive @Cached InlinedConditionProfile fixedWidthProfile,
                        @Shared("valid") @Cached InlinedConditionProfile validProfile) {
            if (fixedWidthProfile.profile(node, is7Bit(codeRangeA))) {
                return TStringOps.readS0(a, arrayA, i);
            } else {
                int byteIndex = Encodings.utf8CodePointToByteIndex(node, a, arrayA, i);
                if (validProfile.profile(node, isValid(codeRangeA))) {
                    return Encodings.utf8DecodeValid(a, arrayA, byteIndex);
                } else {
                    assert TStringGuards.isBroken(codeRangeA);
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

    abstract static class CodePointAtRawNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int i, ErrorHandling errorHandling);

        @Specialization(guards = "isUTF16(encoding)")
        static int utf16(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i, ErrorHandling errorHandling,
                        @Cached InlinedConditionProfile fixedWidthProfile,
                        @Cached InlinedConditionProfile validProfile,
                        @Shared("stride0") @Cached InlinedConditionProfile stride0Profile) {
            if (fixedWidthProfile.profile(node, isFixedWidth(codeRangeA))) {
                if (stride0Profile.profile(node, isStride0(a))) {
                    return TStringOps.readS0(a, arrayA, i);
                } else {
                    assert isStride1(a);
                    return TStringOps.readS1(a, arrayA, i);
                }
            } else if (validProfile.profile(node, isValid(codeRangeA))) {
                return Encodings.utf16DecodeValid(a, arrayA, i);
            } else {
                assert TStringGuards.isBroken(codeRangeA);
                return Encodings.utf16DecodeBroken(a, arrayA, i, errorHandling);
            }
        }

        @Specialization(guards = "isUTF32(encoding)")
        static int utf32(Node node, AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i,
                        ErrorHandling errorHandling,
                        @Shared("stride0") @Cached InlinedConditionProfile stride0Profile,
                        @Exclusive @Cached InlinedConditionProfile stride1Profile) {
            if (stride0Profile.profile(node, isStride0(a))) {
                return TStringOps.readS0(a, arrayA, i);
            } else if (stride1Profile.profile(node, isStride1(a))) {
                char c = TStringOps.readS1(a, arrayA, i);
                if (errorHandling == ErrorHandling.RETURN_NEGATIVE && isUTF16Surrogate(c)) {
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
        static int utf8(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i, ErrorHandling errorHandling,
                        @Exclusive @Cached InlinedConditionProfile fixedWidthProfile,
                        @Exclusive @Cached InlinedConditionProfile validProfile) {
            if (fixedWidthProfile.profile(node, is7Bit(codeRangeA))) {
                return TStringOps.readS0(a, arrayA, i);
            } else if (validProfile.profile(node, isValid(codeRangeA))) {
                return Encodings.utf8DecodeValid(a, arrayA, i);
            } else {
                assert TStringGuards.isBroken(codeRangeA);
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
        static int doAsciiBroken(AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i,
                        ErrorHandling errorHandling) {
            assert isStride0(a);
            int c = readS0(a, arrayA, i);
            if (errorHandling == ErrorHandling.RETURN_NEGATIVE && c > 0x7f) {
                return -1;
            }
            assert errorHandling == ErrorHandling.BEST_EFFORT || !TSCodeRange.isPrecise(codeRangeA);
            return c;
        }

        @Specialization(guards = {"isUnsupportedEncoding(encoding)", "!is7Or8Bit(codeRangeA)"})
        static int unsupported(AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int i, ErrorHandling errorHandling) {
            return JCodings.getInstance().decode(a, JCodings.asByteArray(arrayA), i, JCodings.getInstance().get(encoding), errorHandling);
        }
    }

    static int indexOfFixedWidth(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, int codepoint, int fromIndex, int toIndex,
                    TStringOpsNodes.RawIndexOfCodePointNode indexOfNode) {
        if (fromIndex == toIndex || !TSCodeRange.isInCodeRange(codepoint, codeRangeA)) {
            return -1;
        }
        return indexOfNode.execute(node, a, arrayA, codepoint, fromIndex, toIndex);
    }

    static int lastIndexOfFixedWidth(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, int codepoint, int fromIndex, int toIndex,
                    TStringOpsNodes.RawLastIndexOfCodePointNode indexOfNode) {
        if (fromIndex == toIndex || !TSCodeRange.isInCodeRange(codepoint, codeRangeA)) {
            return -1;
        }
        return indexOfNode.execute(node, a, arrayA, codepoint, fromIndex, toIndex);
    }

    abstract static class IndexOfCodePointNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int toIndex);

        @Specialization(guards = "isFixedWidth(codeRangeA)")
        static int doFixedWidth(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TStringOpsNodes.RawIndexOfCodePointNode indexOfNode) {
            return indexOfFixedWidth(node, a, arrayA, codeRangeA, codepoint, fromIndex, toIndex, indexOfNode);
        }

        @Specialization(guards = "!isFixedWidth(codeRangeA)")
        static int decode(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TruffleStringIterator.InternalNextNode nextNode) {
            return TruffleStringIterator.indexOf(node, AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding), codepoint, fromIndex, toIndex, nextNode);
        }
    }

    abstract static class IndexOfCodePointRawNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int toIndex);

        @Specialization(guards = {"isFixedWidth(codeRangeA)"})
        static int utf8Fixed(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TStringOpsNodes.RawIndexOfCodePointNode indexOfNode) {
            return indexOfFixedWidth(node, a, arrayA, codeRangeA, codepoint, fromIndex, toIndex, indexOfNode);
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
        static int unsupported(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TruffleStringIterator.InternalNextNode nextNode) {
            final TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding);
            it.setRawIndex(fromIndex);
            int loopCount = 0;
            while (it.hasNext() && it.getRawIndex() < toIndex) {
                int ret = it.getRawIndex();
                if (nextNode.execute(node, it) == codepoint) {
                    return ret;
                }
                TStringConstants.truffleSafePointPoll(node, ++loopCount);
            }
            return -1;
        }
    }

    abstract static class LastIndexOfCodePointNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int toIndex);

        @Specialization(guards = "isFixedWidth(codeRangeA)")
        static int doFixedWidth(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TStringOpsNodes.RawLastIndexOfCodePointNode lastIndexOfNode) {
            return lastIndexOfFixedWidth(node, a, arrayA, codeRangeA, codepoint, fromIndex, toIndex, lastIndexOfNode);
        }

        @Specialization(guards = "!isFixedWidth(codeRangeA)")
        static int decode(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached TruffleStringIterator.InternalNextNode nextNode) {
            return TruffleStringIterator.lastIndexOf(node, AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding), codepoint, fromIndex, toIndex, nextNode);
        }
    }

    abstract static class LastIndexOfCodePointRawNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int codepoint, int fromIndex, int toIndex);

        @Specialization(guards = {"isFixedWidth(codeRangeA)"})
        static int utf8Fixed(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached @Shared("lastIndexOfNode") TStringOpsNodes.RawLastIndexOfCodePointNode lastIndexOfNode) {
            return lastIndexOfFixedWidth(node, a, arrayA, codeRangeA, codepoint, fromIndex, toIndex, lastIndexOfNode);
        }

        @Specialization(guards = {"isUTF8(encoding)", "!isFixedWidth(codeRangeA)"})
        static int utf8Variable(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached @Shared("lastIndexOfNode") TStringOpsNodes.RawLastIndexOfCodePointNode lastIndexOfNode) {
            int encodedSize = Encodings.utf8EncodedSize(codepoint);
            if (encodedSize > fromIndex - toIndex) {
                return -1;
            }
            if (encodedSize == 1) {
                return lastIndexOfFixedWidth(node, a, arrayA, codeRangeA, codepoint, fromIndex, toIndex, lastIndexOfNode);
            }
            byte[] encoded = Encodings.utf8EncodeNonAscii(codepoint, encodedSize);
            TruffleString b = TruffleString.createFromByteArray(encoded, encoded.length, 0, Encoding.UTF_8, 1, TSCodeRange.getValidMultiByte());
            return TStringOps.lastIndexOfStringWithOrMaskWithStride(node, a, arrayA, 0, b, encoded, 0, fromIndex, toIndex, null);
        }

        @Specialization(guards = {"isUTF16(encoding)", "!isFixedWidth(codeRangeA)"})
        static int utf16Variable(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint, int fromIndex, int toIndex,
                        @Cached @Shared("lastIndexOfNode") TStringOpsNodes.RawLastIndexOfCodePointNode lastIndexOfNode) {
            assert isStride1(a);
            int encodedSize = Encodings.utf16EncodedSize(codepoint);
            if (encodedSize > fromIndex - toIndex) {
                return -1;
            }
            if (encodedSize == 1) {
                return lastIndexOfFixedWidth(node, a, arrayA, codeRangeA, codepoint, fromIndex, toIndex, lastIndexOfNode);
            }
            return TStringOps.lastIndexOf2ConsecutiveWithOrMaskWithStride(
                            node, a, arrayA, 1, fromIndex, toIndex, Character.highSurrogate(codepoint), Character.lowSurrogate(codepoint), 0, 0);
        }

        @Specialization(guards = {"isUnsupportedEncoding(encoding)", "!isFixedWidth(codeRangeA)"})
        static int unsupported(Node node, AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int codepoint,
                        int fromIndex, int toIndex,
                        @Cached TruffleStringIterator.InternalPreviousNode prevNode) {
            final TruffleStringIterator it = TruffleString.backwardIterator(a, arrayA, codeRangeA, encoding);
            it.setRawIndex(fromIndex);
            int loopCount = 0;
            while (it.hasPrevious() && it.getRawIndex() >= toIndex) {
                if (prevNode.execute(node, it, DecodingErrorHandler.DEFAULT) == codepoint) {
                    return it.getRawIndex();
                }
                TStringConstants.truffleSafePointPoll(node, ++loopCount);
            }
            return -1;
        }
    }

    @ImportStatic({TStringGuards.class, Encoding.class})
    abstract static class IndexOfCodePointSetNode extends Node {

        static final int POSSIBLE_STRIDE_VALUES = 3;

        @Children IndexOfCodePointSet.IndexOfNode[] indexOfNodes;
        final Encoding encoding;
        final boolean isUTF16Or32;

        IndexOfCodePointSetNode(IndexOfCodePointSet.IndexOfNode[] indexOfNodes, Encoding encoding) {
            this.indexOfNodes = insert(indexOfNodes);
            this.encoding = encoding;
            this.isUTF16Or32 = isUTF16Or32(encoding);
        }

        abstract int execute(Object arrayA, int offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex);

        @Specialization(guards = "!isUTF16Or32")
        int stride0(Object arrayA, int offsetA, int lengthA, @SuppressWarnings("unused") int strideA, int codeRangeA, int fromIndex, int toIndex) {
            return doIndexOf(arrayA, offsetA, lengthA, 0, codeRangeA, fromIndex, toIndex);
        }

        @Specialization(guards = {"isUTF16Or32", "strideA == cachedStride"}, limit = "POSSIBLE_STRIDE_VALUES")
        int dynamicStride(Object arrayA, int offsetA, int lengthA, @SuppressWarnings("unused") int strideA, int codeRangeA, int fromIndex, int toIndex,
                        @Cached(value = "strideA", allowUncached = true) int cachedStride) {
            return doIndexOf(arrayA, offsetA, lengthA, cachedStride, codeRangeA, fromIndex, toIndex);
        }

        @ExplodeLoop
        private int doIndexOf(Object arrayA, int offsetA, int lengthA, int strideA, int codeRangeA, int fromIndex, int toIndex) {
            CompilerAsserts.partialEvaluationConstant(indexOfNodes);
            // generate an if-else cascade checking increasing code range values
            for (int i = 0; i < indexOfNodes.length - 1; i++) {
                CompilerAsserts.partialEvaluationConstant(i);
                IndexOfCodePointSet.IndexOfNode node = indexOfNodes[i];
                CompilerAsserts.partialEvaluationConstant(node);
                if (TSCodeRange.isMoreRestrictiveOrEqual(codeRangeA, Byte.toUnsignedInt(node.maxCodeRange))) {
                    return node.execute(this, arrayA, offsetA, lengthA, strideA, codeRangeA, fromIndex, toIndex, encoding);
                }
            }
            // last node is always the else-branch
            return indexOfNodes[indexOfNodes.length - 1].execute(this, arrayA, offsetA, lengthA, strideA, codeRangeA, fromIndex, toIndex, encoding);
        }
    }

    abstract static class SubstringNode extends AbstractInternalNode {

        abstract TruffleString execute(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int fromIndex, int length, boolean lazy);

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
        static TruffleString materializeSubstring(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int fromIndex, int length,
                        @SuppressWarnings("unused") boolean lazy,
                        @Shared("attributes") @Cached CalcStringAttributesNode calcAttributesNode,
                        @Exclusive @Cached InlinedConditionProfile utf16Profile,
                        @Exclusive @Cached InlinedConditionProfile utf32Profile) {
            final long attrs;
            final int codeRange;
            final int stride;
            final int newStride;
            if (utf16Profile.profile(node, isUTF16(encoding))) {
                stride = a.stride();
                attrs = calcAttributesNode.execute(node, a, arrayA, a.offset(), length, stride, Encoding.UTF_16, fromIndex, codeRangeA);
                codeRange = StringAttributes.getCodeRange(attrs);
                newStride = Stride.fromCodeRangeUTF16(codeRange);
            } else if (utf32Profile.profile(node, isUTF32(encoding))) {
                stride = a.stride();
                attrs = calcAttributesNode.execute(node, a, arrayA, a.offset(), length, stride, Encoding.UTF_32, fromIndex, codeRangeA);
                codeRange = StringAttributes.getCodeRange(attrs);
                newStride = Stride.fromCodeRangeUTF32(codeRange);
            } else {
                stride = 0;
                attrs = calcAttributesNode.execute(node, a, arrayA, a.offset(), length, stride, encoding, fromIndex, codeRangeA);
                codeRange = StringAttributes.getCodeRange(attrs);
                newStride = 0;
            }
            byte[] newBytes = TStringOps.arraycopyOfWithStride(node, arrayA, a.offset() + (fromIndex << stride), length, stride, length, newStride);
            return TruffleString.createFromByteArray(newBytes, length, newStride, encoding, StringAttributes.getCodePointLength(attrs), codeRange);
        }

        @Specialization(guards = {"length > 0", "length != length(a)", "lazy"})
        static TruffleString createLazySubstring(Node node, TruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int fromIndex, int length, @SuppressWarnings("unused") boolean lazy,
                        @Shared("attributes") @Cached CalcStringAttributesNode calcAttributesNode,
                        @Exclusive @Cached InlinedConditionProfile stride1MustMaterializeProfile,
                        @Exclusive @Cached InlinedConditionProfile stride2MustMaterializeProfile) {
            int lazyOffset = a.offset() + (fromIndex << a.stride());
            long attrs = calcAttributesNode.execute(node, a, arrayA, a.offset(), length, a.stride(), encoding, fromIndex, codeRangeA);
            int codeRange = StringAttributes.getCodeRange(attrs);
            int codePointLength = StringAttributes.getCodePointLength(attrs);
            final Object array;
            final int offset;
            final int stride;
            if (stride1MustMaterializeProfile.profile(node, a.stride() == 1 && TSCodeRange.isMoreRestrictiveOrEqual(codeRange, TSCodeRange.get8Bit()))) {
                assert isUTF16Or32(encoding);
                stride = 0;
                offset = 0;
                final byte[] newBytes = new byte[length];
                TStringOps.arraycopyWithStride(node,
                                arrayA, lazyOffset, 1, 0,
                                newBytes, offset, 0, 0, length);
                array = newBytes;
            } else if (stride2MustMaterializeProfile.profile(node, a.stride() == 2 && TSCodeRange.isMoreRestrictiveOrEqual(codeRange, TSCodeRange.get16Bit()))) {
                // Always materialize 4-byte UTF-32 strings when they can be compacted. Otherwise,
                // they could get re-interpreted as UTF-16 and break the assumption that all UTF-16
                // strings are stride 0 or 1.
                assert isUTF32(encoding);
                stride = Stride.fromCodeRangeUTF32(StringAttributes.getCodeRange(attrs));
                offset = 0;
                final byte[] newBytes = new byte[length << stride];
                if (stride == 0) {
                    TStringOps.arraycopyWithStride(node,
                                    arrayA, lazyOffset, 2, 0,
                                    newBytes, offset, 0, 0, length);
                } else {
                    assert stride == 1;
                    TStringOps.arraycopyWithStride(node,
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

    abstract static class ConcatEagerNode extends AbstractInternalNode {

        abstract TruffleString execute(Node node, AbstractTruffleString a, AbstractTruffleString b, Encoding encoding, int concatLength, int concatStride, int concatCodeRange);

        @Specialization
        static TruffleString concat(Node node, AbstractTruffleString a, AbstractTruffleString b, Encoding encoding, int concatLength, int concatStride, int concatCodeRange,
                        @Cached TruffleString.ToIndexableNode toIndexableNodeA,
                        @Cached TruffleString.ToIndexableNode toIndexableNodeB,
                        @Cached GetCodePointLengthNode getCodePointLengthANode,
                        @Cached GetCodePointLengthNode getCodePointLengthBNode,
                        @Cached ConcatMaterializeBytesNode materializeBytesNode,
                        @Cached CalcStringAttributesNode calculateAttributesNode,
                        @Cached InlinedConditionProfile brokenProfile) {
            final byte[] bytes = materializeBytesNode.execute(node, a, toIndexableNodeA.execute(node, a, a.data()), b, toIndexableNodeB.execute(node, b, b.data()), encoding, concatLength,
                            concatStride);
            final int codeRange;
            final int codePointLength;
            if (brokenProfile.profile(node, isBrokenMultiByte(concatCodeRange))) {
                final long attrs = calculateAttributesNode.execute(node, null, bytes, 0, concatLength, concatStride, encoding, 0, TSCodeRange.getBrokenMultiByte());
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            } else {
                codePointLength = getCodePointLengthANode.execute(node, a, encoding) + getCodePointLengthBNode.execute(node, b, encoding);
                codeRange = concatCodeRange;
            }
            return TruffleString.createFromByteArray(bytes, concatLength, concatStride, encoding, codePointLength, codeRange);
        }
    }

    abstract static class ConcatMaterializeBytesNode extends AbstractInternalNode {

        abstract byte[] execute(Node node, AbstractTruffleString a, Object arrayA, AbstractTruffleString b, Object arrayB, Encoding encoding, int concatLength, int concatStride);

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

    abstract static class RegionEqualsNode extends AbstractInternalNode {

        abstract boolean execute(Node node,
                        AbstractTruffleString a, Object arrayA, int codeRangeA, int fromIndexA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndexB, int length, Encoding encoding);

        @Specialization(guards = {"isFixedWidth(codeRangeA, codeRangeB)"})
        boolean direct(
                        AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, int fromIndexA,
                        AbstractTruffleString b, Object arrayB, @SuppressWarnings("unused") int codeRangeB, int fromIndexB, int length, @SuppressWarnings("unused") Encoding encoding) {
            return TStringOps.regionEqualsWithOrMaskWithStride(this, a, arrayA, a.stride(), fromIndexA, b, arrayB, b.stride(), fromIndexB, null, length);
        }

        @Specialization(guards = {"!isFixedWidth(codeRangeA, codeRangeB)"})
        static boolean decode(Node node,
                        AbstractTruffleString a, Object arrayA, int codeRangeA, int fromIndexA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndexB, int length, Encoding encoding,
                        @Cached TruffleStringIterator.InternalNextNode nextNodeA,
                        @Cached TruffleStringIterator.InternalNextNode nextNodeB) {
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.forwardIterator(b, arrayB, codeRangeB, encoding);
            for (int i = 0; i < fromIndexA; i++) {
                if (!aIt.hasNext()) {
                    return false;
                }
                nextNodeA.execute(node, aIt);
                TStringConstants.truffleSafePointPoll(node, i + 1);
            }
            for (int i = 0; i < fromIndexB; i++) {
                if (!bIt.hasNext()) {
                    return false;
                }
                nextNodeB.execute(node, bIt);
                TStringConstants.truffleSafePointPoll(node, i + 1);
            }
            for (int i = 0; i < length; i++) {
                if (!(aIt.hasNext() && bIt.hasNext()) || nextNodeA.execute(node, aIt) != nextNodeB.execute(node, bIt)) {
                    return false;
                }
                TStringConstants.truffleSafePointPoll(node, i + 1);
            }
            return true;
        }
    }

    abstract static class InternalIndexOfStringNode extends AbstractInternalNode {

        abstract int execute(Node node,
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, Encoding encoding);

        @Specialization(guards = {"isFixedWidth(codeRangeA, codeRangeB)"})
        static int direct(Node node,
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, @SuppressWarnings("unused") Encoding encoding,
                        @Cached RawIndexOfStringNode indexOfStringNode) {
            assert !b.isEmpty() && !indexOfCannotMatch(node, codeRangeA, b, codeRangeB, toIndex - fromIndex, encoding, GetCodePointLengthNode.getUncached());
            return indexOfStringNode.execute(node, a, arrayA, b, arrayB, fromIndex, toIndex, null);
        }

        @Specialization(guards = {"!isFixedWidth(codeRangeA, codeRangeB)"})
        static int decode(Node node,
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, Encoding encoding,
                        @Cached TruffleStringIterator.InternalNextNode nextNodeA,
                        @Cached TruffleStringIterator.InternalNextNode nextNodeB) {
            assert !b.isEmpty() && !indexOfCannotMatch(node, codeRangeA, b, codeRangeB, toIndex - fromIndex, encoding, GetCodePointLengthNode.getUncached());
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.forwardIterator(b, arrayB, codeRangeB, encoding);
            return TruffleStringIterator.indexOfString(node, aIt, bIt, fromIndex, toIndex, nextNodeA, nextNodeB);
        }
    }

    abstract static class IndexOfStringRawNode extends AbstractInternalNode {

        abstract int execute(Node node,
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, byte[] mask, Encoding encoding);

        @Specialization(guards = {"isSupportedEncoding(encoding) || isFixedWidth(codeRangeA)"})
        static int supported(Node node,
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, byte[] mask, @SuppressWarnings("unused") Encoding encoding,
                        @Cached TStringOpsNodes.RawIndexOfStringNode indexOfStringNode) {
            assert !b.isEmpty() && !indexOfCannotMatch(codeRangeA, b, codeRangeB, mask, toIndex - fromIndex);
            return indexOfStringNode.execute(node, a, arrayA, b, arrayB, fromIndex, toIndex, mask);
        }

        @Specialization(guards = {"isUnsupportedEncoding(encoding)", "!isFixedWidth(codeRangeA)"})
        static int unsupported(Node node,
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, @SuppressWarnings("unused") byte[] mask, Encoding encoding,
                        @Cached TruffleStringIterator.InternalNextNode nextNodeA,
                        @Cached TruffleStringIterator.InternalNextNode nextNodeB) {
            assert mask == null;
            assert !b.isEmpty() && !indexOfCannotMatch(codeRangeA, b, codeRangeB, mask, toIndex - fromIndex);
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.forwardIterator(b, arrayB, codeRangeB, encoding);
            return TruffleStringIterator.byteIndexOfString(node, aIt, bIt, fromIndex, toIndex, nextNodeA, nextNodeB);
        }
    }

    abstract static class LastIndexOfStringNode extends AbstractInternalNode {

        abstract int execute(Node node,
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, Encoding encoding);

        @Specialization(guards = {"isFixedWidth(codeRangeA, codeRangeB)"})
        static int direct(Node node,
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, @SuppressWarnings("unused") Encoding encoding,
                        @Cached RawLastIndexOfStringNode indexOfStringNode) {
            assert !b.isEmpty() && !indexOfCannotMatch(node, codeRangeA, b, codeRangeB, fromIndex - toIndex, encoding, GetCodePointLengthNode.getUncached());
            return indexOfStringNode.execute(node, a, arrayA, b, arrayB, fromIndex, toIndex, null);
        }

        @Specialization(guards = {"!isFixedWidth(codeRangeA, codeRangeB)"})
        static int decode(Node node,
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, Encoding encoding,
                        @Cached TruffleStringIterator.InternalNextNode nextNodeA,
                        @Cached TruffleStringIterator.InternalPreviousNode prevNodeA,
                        @Cached TruffleStringIterator.InternalPreviousNode prevNodeB) {
            assert !b.isEmpty() && !indexOfCannotMatch(node, codeRangeA, b, codeRangeB, fromIndex - toIndex, encoding, GetCodePointLengthNode.getUncached());
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.backwardIterator(b, arrayB, codeRangeB, encoding);
            return TruffleStringIterator.lastIndexOfString(node, aIt, bIt, fromIndex, toIndex, nextNodeA, prevNodeA, prevNodeB);
        }
    }

    abstract static class LastIndexOfStringRawNode extends AbstractInternalNode {

        abstract int execute(Node node,
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, byte[] mask, Encoding encoding);

        @Specialization(guards = {"isSupportedEncoding(encoding) || isFixedWidth(codeRangeA)"})
        static int lios8SameEncoding(Node node,
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, byte[] mask, @SuppressWarnings("unused") Encoding encoding,
                        @Cached TStringOpsNodes.RawLastIndexOfStringNode indexOfStringNode) {
            assert !b.isEmpty() && !indexOfCannotMatch(codeRangeA, b, codeRangeB, mask, fromIndex - toIndex);
            return indexOfStringNode.execute(node, a, arrayA, b, arrayB, fromIndex, toIndex, mask);
        }

        @Specialization(guards = {"isUnsupportedEncoding(encoding)", "!isFixedWidth(codeRangeA)"})
        static int unsupported(Node node,
                        AbstractTruffleString a, Object arrayA, int codeRangeA,
                        AbstractTruffleString b, Object arrayB, int codeRangeB, int fromIndex, int toIndex, @SuppressWarnings("unused") byte[] mask, Encoding encoding,
                        @Cached TruffleStringIterator.InternalNextNode nextNodeA,
                        @Cached TruffleStringIterator.InternalPreviousNode prevNodeA,
                        @Cached TruffleStringIterator.InternalPreviousNode prevNodeB) {
            assert mask == null;
            assert !b.isEmpty() && !indexOfCannotMatch(codeRangeA, b, codeRangeB, mask, fromIndex - toIndex);
            TruffleStringIterator aIt = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding);
            TruffleStringIterator bIt = AbstractTruffleString.backwardIterator(b, arrayB, codeRangeB, encoding);
            return TruffleStringIterator.lastByteIndexOfString(node, aIt, bIt, fromIndex, toIndex, nextNodeA, prevNodeA, prevNodeB);
        }
    }

    abstract static class StrideFromCodeRangeNode extends AbstractInternalNode {

        abstract int execute(Node node, int codeRange, Encoding encoding);

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

    abstract static class CalcStringAttributesNode extends AbstractInternalNode {

        abstract long execute(Node node, AbstractTruffleString a, Object array, int offset, int length, int stride, Encoding encoding, int fromIndex, int knownCodeRange);

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
        static long notAscii(Node node, AbstractTruffleString a, Object array, int offset, int length, int stride, Encoding encoding, int fromIndex, int knownCodeRange,
                        @Cached CalcStringAttributesInnerNode calcNode) {
            return calcNode.execute(node, a, array, offset, length, stride, encoding, fromIndex, knownCodeRange);
        }

    }

    abstract static class CalcStringAttributesInnerNode extends AbstractInternalNode {

        abstract long execute(Node node, AbstractTruffleString a, Object array, int offset, int length, int stride, Encoding encoding, int fromIndex, int knownCodeRange);

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
        static long doUTF8(Node node, AbstractTruffleString a, Object array, int offset, int length, int stride, @SuppressWarnings("unused") Encoding encoding, int fromIndex, int knownCodeRange,
                        @Exclusive @Cached InlinedConditionProfile brokenProfile) {
            assert stride == 0;
            int off = offset + fromIndex;
            if (isValid(knownCodeRange) && a != null) {
                return TStringOps.calcStringAttributesUTF8(node, array, off, length, true, off + length == a.offset() + a.length(), brokenProfile);
            } else {
                return TStringOps.calcStringAttributesUTF8(node, array, off, length, false, false, brokenProfile);
            }
        }

        @Specialization(guards = {"isUTF16(encoding)", "isValid(knownCodeRange)"})
        long doUTF16Valid(@SuppressWarnings("unused") AbstractTruffleString a, Object array, int offset, int length, int stride, @SuppressWarnings("unused") Encoding encoding, int fromIndex,
                        @SuppressWarnings("unused") int knownCodeRange) {
            assert stride == 1;
            return TStringOps.calcStringAttributesUTF16(this, array, offset + (fromIndex << 1), length, true);
        }

        @Specialization(guards = {"isUTF16(encoding)", "isBroken(knownCodeRange)"})
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
        static long doGeneric(Node node, @SuppressWarnings("unused") AbstractTruffleString a, Object array, int offset, int length, int stride, Encoding encoding, int fromIndex,
                        @SuppressWarnings("unused") int knownCodeRange,
                        @Exclusive @Cached InlinedConditionProfile validCharacterProfile,
                        @Exclusive @Cached InlinedConditionProfile fixedWidthProfile) {
            assert stride == 0;
            return JCodings.getInstance().calcStringAttributes(node, array, offset, length, encoding, fromIndex, validCharacterProfile, fixedWidthProfile);
        }
    }

    abstract static class ParseIntNode extends AbstractInternalNode {

        abstract int execute(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int radix) throws TruffleString.NumberFormatException;

        @SuppressWarnings("unused")
        @Specialization(guards = {"is7Bit(codeRangeA)", "compaction == cachedCompaction"}, limit = Stride.STRIDE_CACHE_LIMIT, unroll = Stride.STRIDE_UNROLL)
        static int do7Bit(Node node, AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int radix,
                        @Bind("fromStride(a.stride())") CompactionLevel compaction,
                        @Cached("compaction") CompactionLevel cachedCompaction,
                        @Cached InlinedBranchProfile errorProfile) throws TruffleString.NumberFormatException {
            return NumberConversion.parseInt7Bit(node, a, arrayA, cachedCompaction.getStride(), radix, errorProfile);
        }

        @Specialization(guards = "!is7Bit(codeRangeA)")
        static int doGeneric(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int radix,
                        @Cached TruffleStringIterator.InternalNextNode nextNode,
                        @Cached InlinedBranchProfile errorProfile) throws TruffleString.NumberFormatException {
            return NumberConversion.parseInt(node, AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding), radix, errorProfile, nextNode);
        }
    }

    abstract static class ParseLongNode extends AbstractInternalNode {

        abstract long execute(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int radix) throws TruffleString.NumberFormatException;

        @SuppressWarnings("unused")
        @Specialization(guards = {"is7Bit(codeRangeA)", "compaction == cachedCompaction"}, limit = Stride.STRIDE_CACHE_LIMIT, unroll = Stride.STRIDE_UNROLL)
        static long do7Bit(Node node, AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codeRangeA, @SuppressWarnings("unused") Encoding encoding, int radix,
                        @Bind("fromStride(a.stride())") CompactionLevel compaction,
                        @Cached("compaction") CompactionLevel cachedCompaction,
                        @Cached InlinedBranchProfile errorProfile) throws TruffleString.NumberFormatException {
            return NumberConversion.parseLong7Bit(node, a, arrayA, cachedCompaction.getStride(), radix, errorProfile);
        }

        @Specialization(guards = "!is7Bit(codeRangeA)")
        static long parseLong(Node node, AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, int radix,
                        @Cached TruffleStringIterator.InternalNextNode nextNode,
                        @Cached InlinedBranchProfile errorProfile) throws TruffleString.NumberFormatException {
            return NumberConversion.parseLong(node, AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, encoding), radix, errorProfile, nextNode);
        }
    }

    abstract static class ParseDoubleNode extends AbstractInternalNode {

        abstract double execute(Node node, AbstractTruffleString a, Object arrayA) throws TruffleString.NumberFormatException;

        @SuppressWarnings("unused")
        @Specialization(guards = "compaction == cachedCompaction", limit = Stride.STRIDE_CACHE_LIMIT, unroll = Stride.STRIDE_UNROLL)
        static double doParse(Node node, AbstractTruffleString a, Object arrayA,
                        @Bind("fromStride(a.stride())") CompactionLevel compaction,
                        @Cached("compaction") CompactionLevel cachedCompaction,
                        @Cached InlinedBranchProfile errorProfile) throws TruffleString.NumberFormatException {
            return FastDoubleParser.parseDouble(node, a, arrayA, cachedCompaction.getStride(), 0, a.length(), errorProfile);
        }
    }

    abstract static class FromJavaStringUTF16Node extends AbstractInternalNode {

        FromJavaStringUTF16Node() {
        }

        abstract TruffleString execute(Node node, String value, int charOffset, int length, boolean copy);

        @Specialization
        static TruffleString doNonEmpty(Node node, String javaString, int charOffset, int length, final boolean copy,
                        @Cached InlinedConditionProfile utf16CompactProfile) {
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
            if (utf16CompactProfile.profile(node, strideJS == 0)) {
                if (length == 1) {
                    return TStringConstants.getSingleByte(Encoding.UTF_16, Byte.toUnsignedInt(arrayJS[charOffset]));
                }
                codeRange = TSCodeRange.markImprecise(TSCodeRange.get8Bit());
                codePointLength = length;
            } else {
                assert strideJS == 1;
                if (length == 1 && TStringOps.readFromByteArray(arrayJS, 1, charOffset) <= 0xff) {
                    return TStringConstants.getSingleByte(Encoding.UTF_16, TStringOps.readFromByteArray(arrayJS, 1, charOffset));
                }
                if (TStringUnsafe.COMPACT_STRINGS_ENABLED && length == javaString.length()) {
                    codePointLength = -1;
                    codeRange = TSCodeRange.markImprecise(TSCodeRange.getBrokenMultiByte());
                } else {
                    final long attrs = TStringOps.calcStringAttributesUTF16(node, arrayJS, offsetJS, length, false);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                    codeRange = StringAttributes.getCodeRange(attrs);
                }
            }
            if (TStringUnsafe.COMPACT_STRINGS_ENABLED && (!copy || length == javaString.length())) {
                stride = strideJS;
                offset = offsetJS;
                array = arrayJS;
            } else {
                stride = Stride.fromCodeRangeUTF16AllowImprecise(codeRange);
                array = new byte[length << stride];
                offset = 0;
                if (strideJS == 1 && stride == 0) {
                    TStringOps.arraycopyWithStride(node, arrayJS, offsetJS, 1, 0, array, offset, 0, 0, length);
                } else {
                    assert strideJS == stride;
                    TStringOps.arraycopyWithStride(node, arrayJS, offsetJS, 0, 0, array, offset, 0, 0, length << stride);
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

    abstract static class CreateJavaStringNode extends AbstractInternalNode {

        abstract String execute(Node node, AbstractTruffleString a, Object arrayA);

        @Specialization
        static String createJavaString(Node node, AbstractTruffleString a, Object arrayA,
                        @Cached InlinedConditionProfile reuseProfile) {
            assert TSCodeRange.is7Or8Bit(a.codeRange()) || TSCodeRange.isPrecise(a.codeRange());
            assert a.isLooselyCompatibleTo(Encoding.UTF_16);
            final int stride = TStringUnsafe.COMPACT_STRINGS_ENABLED ? Stride.fromCodeRangeUTF16(a.codeRange()) : 1;
            final byte[] bytes;
            if (reuseProfile.profile(node, a instanceof TruffleString && arrayA instanceof byte[] && a.length() << a.stride() == ((byte[]) arrayA).length && a.stride() == stride)) {
                assert a.offset() == 0;
                bytes = (byte[]) arrayA;
            } else {
                bytes = new byte[a.length() << stride];
                TStringOps.arraycopyWithStride(node,
                                arrayA, a.offset(), a.stride(), 0,
                                bytes, 0, stride, 0, a.length());
            }
            return TStringUnsafe.createJavaString(bytes, stride);
        }
    }

    abstract static class ToValidStringNode extends AbstractInternalNode {

        private static final int[] UTF_32_ASTRAL_RANGE = {0x10000, 0x10ffff};
        private static final int[] UTF_32_INVALID_RANGES = {Character.MIN_SURROGATE, Character.MAX_SURROGATE, 0x11_0000, 0xffff_ffff};

        abstract TruffleString execute(Node node, AbstractTruffleString a, Object arrayA, Encoding encoding);

        @Specialization(guards = "isAscii(encoding)")
        static TruffleString ascii(Node node, AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") Encoding encoding) {
            assert isStride0(a);
            int length = a.length();
            byte[] array = TStringOps.arraycopyOfWithStride(node, arrayA, a.offset(), length, 0, length, 0);
            int pos = 0;
            int loopCount = 0;
            while (pos < length) {
                pos = TStringOps.indexOfCodePointWithMaskWithStrideIntl(node, array, 0, length, 0, pos, 0xff, 0x7f);
                if (pos >= 0) {
                    TStringOps.writeToByteArray(array, 0, pos++, '?');
                } else {
                    break;
                }
                TStringConstants.truffleSafePointPoll(node, ++loopCount);
            }
            return TruffleString.createFromByteArray(array, length, 0, Encoding.US_ASCII, length, TSCodeRange.get7Bit());
        }

        @Specialization(guards = "isUTF8(encoding)")
        static TruffleString utf8(Node node, AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") Encoding encoding,
                        @Cached InlinedBranchProfile outOfMemoryProfile) {
            assert isStride0(a);
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
            while (i < a.length()) {
                int b = readS0(a, arrayA, i++);
                int type = stateMachine[b];
                state = stateMachine[256 + state + type];
                if (state == UTF8_ACCEPT) {
                    lastCodePointPos = i;
                } else if (state == UTF8_REJECT) {
                    int curCPLength = i - (lastCodePointPos + 1);
                    length = utf8CopyValidRegion(node, a, arrayA, outOfMemoryProfile, isLarge, buffer, length, lastCodePointPos, lastErrorPos);
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
            length = utf8CopyValidRegion(node, a, arrayA, outOfMemoryProfile, isLarge, buffer, length, lastCodePointPos, lastErrorPos);
            if (lastCodePointPos != a.length() && lastErrorPos != lastCodePointPos) {
                System.arraycopy(Encodings.CONVERSION_REPLACEMENT_UTF_8, 0, buffer, length, Encodings.CONVERSION_REPLACEMENT_UTF_8.length);
                length += Encodings.CONVERSION_REPLACEMENT_UTF_8.length;
                int curCPLength = a.length() - lastCodePointPos;
                if (curCPLength > 1) {
                    codePointLength -= curCPLength - 1;
                }
            }
            return TruffleString.createFromByteArray(Arrays.copyOf(buffer, length), length, 0, Encoding.UTF_8, codePointLength, TSCodeRange.getValidMultiByte());
        }

        private static int utf8CopyValidRegion(Node node, AbstractTruffleString a, Object arrayA,
                        InlinedBranchProfile outOfMemoryProfile, boolean isLarge, byte[] buffer, int length, int lastCodePointPos, int lastErrorPos) {
            int lengthCPY = lastCodePointPos - lastErrorPos;
            if (isLarge && Integer.compareUnsigned(length + lengthCPY + Encodings.CONVERSION_REPLACEMENT_UTF_8.length, buffer.length) > 0) {
                outOfMemoryProfile.enter(node);
                throw InternalErrors.outOfMemory();
            }
            TStringOps.arraycopyWithStride(node, arrayA, a.offset(), 0, lastErrorPos, buffer, 0, 0, length, lengthCPY);
            return length + lengthCPY;
        }

        @Specialization(guards = "isUTF16(encoding)")
        static TruffleString utf16(Node node, AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") Encoding encoding) {
            assert isStride1(a);
            int length = a.length();
            byte[] array = TStringOps.arraycopyOfWithStride(node, arrayA, a.offset(), length, 1, length, 1);
            int pos = 0;
            int codeRange = TSCodeRange.get16Bit();
            int loopCount = 0;
            while (true) {
                pos = TStringOps.indexOfCodePointWithMaskWithStrideIntl(node, array, 0, length, 1, pos, 0xdfff, 0x7ff);
                if (pos >= 0) {
                    boolean invalid = true;
                    if (pos != length - 1) {
                        char c = (char) TStringOps.readFromByteArray(array, 1, pos);
                        assert Encodings.isUTF16Surrogate(c);
                        if (!Encodings.isUTF16LowSurrogate(c)) {
                            assert Encodings.isUTF16HighSurrogate(c);
                            if (Encodings.isUTF16LowSurrogate((char) TStringOps.readFromByteArray(array, 1, pos + 1))) {
                                invalid = false;
                                codeRange = TSCodeRange.getValidMultiByte();
                                pos++;
                            }
                        }
                    }
                    if (invalid) {
                        TStringOps.writeToByteArray(array, 1, pos, 0xfffd);
                    }
                    if (++pos == length) {
                        break;
                    }
                } else {
                    break;
                }
                TStringConstants.truffleSafePointPoll(node, ++loopCount);
            }
            return TruffleString.createFromByteArray(array, length, 1, Encoding.UTF_16, a.codePointLength(), codeRange);
        }

        @Specialization(guards = "isUTF32(encoding)")
        static TruffleString utf32(Node node, AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") Encoding encoding,
                        @Cached InlinedConditionProfile strideProfile) {
            assert isStride2(a);
            int length = a.length();
            final byte[] array;
            final int stride;
            final int codeRange;
            if (strideProfile.profile(node, TStringOps.indexOfAnyIntRange(node, arrayA, 0, 2, 0, a.length(), UTF_32_ASTRAL_RANGE) < 0)) {
                array = TStringOps.arraycopyOfWithStride(node, arrayA, a.offset(), length, 2, length, 1);
                stride = 1;
                codeRange = TSCodeRange.get16Bit();
                utf32ReplaceInvalid(node, arrayA, length, array, 1);
            } else {
                array = TStringOps.arraycopyOfWithStride(node, arrayA, a.offset(), length, 2, length, 2);
                stride = 2;
                codeRange = TSCodeRange.getValidFixedWidth();
                utf32ReplaceInvalid(node, arrayA, length, array, 2);
            }
            return TruffleString.createFromByteArray(array, length, stride, Encoding.UTF_32, a.codePointLength(), codeRange);
        }

        private static void utf32ReplaceInvalid(Node node, Object arrayA, int length, byte[] array, int stride) {
            int pos = 0;
            int loopCount = 0;
            while (pos < length) {
                pos = TStringOps.indexOfAnyIntRange(node, arrayA, 0, 2, pos, length, UTF_32_INVALID_RANGES);
                if (pos >= 0) {
                    TStringOps.writeToByteArray(array, stride, pos++, 0xfffd);
                } else {
                    break;
                }
                TStringConstants.truffleSafePointPoll(node, ++loopCount);
            }
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isUnsupportedEncoding(encoding)")
        static TruffleString unsupported(Node node, AbstractTruffleString a, Object arrayA, Encoding encoding) {
            throw InternalErrors.unsupportedOperation();
        }
    }

    abstract static class TransCodeNode extends AbstractInternalNode {

        abstract TruffleString execute(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler);

        @Specialization
        static TruffleString transcode(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached InlinedConditionProfile asciiBytesInvalidProfile,
                        @Cached TransCodeIntlWithErrorHandlerNode transCodeIntlNode) {
            CompilerAsserts.partialEvaluationConstant(errorHandler);
            if (AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS && a.isImmutable() && TSCodeRange.isMoreRestrictiveThan(codeRangeA, targetEncoding.maxCompatibleCodeRange)) {
                if (a.stride() == 0) {
                    return TruffleString.createFromArray(arrayA, a.offset(), a.length(), 0, targetEncoding, codePointLengthA, codeRangeA, false);
                }
                int targetStride = Stride.fromCodeRange(codeRangeA, targetEncoding);
                Object array = TStringOps.arraycopyOfWithStride(node, arrayA, a.offset(), a.length(), a.stride(), a.length(), targetStride);
                return TruffleString.createFromArray(array, 0, a.length(), targetStride, targetEncoding, codePointLengthA, codeRangeA, false);
            }
            assert a.length() > 0;
            if (asciiBytesInvalidProfile.profile(node,
                            (isAscii(a.encoding()) || isBytes(a.encoding())) && isSupportedEncoding(targetEncoding) && isBuiltin(errorHandler))) {
                assert (isBrokenFixedWidth(codeRangeA) || isValidFixedWidth(codeRangeA)) && isStride0(a) && codePointLengthA == a.length();
                boolean replacedChars = false;
                byte[] buffer = new byte[codePointLengthA];
                for (int i = 0; i < buffer.length; i++) {
                    int c = readS0(a, arrayA, i);
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
                return transCodeIntlNode.execute(node, a, arrayA, codePointLengthA, codeRangeA, Encoding.get(a.encoding()), targetEncoding, errorHandler);
            }
        }
    }

    abstract static class TransCodeIntlWithErrorHandlerNode extends AbstractInternalNode {

        abstract TruffleString execute(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler);

        @Specialization(guards = {"isBroken(codeRangeA) || isAsciiBytesOrLatin1(targetEncoding)",
                        "isSupportedEncoding(sourceEncoding)",
                        "isSupportedEncoding(targetEncoding)",
                        "!isBuiltin(errorHandler)"})
        static TruffleString supportedWithCustomErrorHandler(Node node, AbstractTruffleString a, Object arrayA, @SuppressWarnings("unused") int codePointLengthA, int codeRangeA,
                        Encoding sourceEncoding,
                        Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached TruffleStringIterator.InternalNextNode iteratorNextNode,
                        @Cached TruffleStringBuilder.AppendCodePointIntlNode appendCodePointNode,
                        @Cached TruffleStringBuilder.AppendStringIntlNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringIntlNode toStringNode) {
            TruffleStringBuilder sb = TruffleStringBuilder.create(targetEncoding);
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, sourceEncoding);
            int maxCodePoint = Encodings.maxCodePoint(targetEncoding);
            int i = 1;
            while (it.hasNext()) {
                int pos = it.getRawIndex();
                int bytePos = it.getByteIndex();
                int codepoint = iteratorNextNode.execute(node, it, DecodingErrorHandler.RETURN_NEGATIVE_UTF8_INCOMPLETE_SEQUENCES);
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
        static TruffleString transcode(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached TransCodeIntlNode transCodeIntlNode) {
            return transCodeIntlNode.execute(node, a, arrayA, codePointLengthA, codeRangeA, sourceEncoding, targetEncoding, errorHandler);
        }
    }

    abstract static class TransCodeIntlNode extends AbstractInternalNode {

        abstract TruffleString execute(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler);

        @SuppressWarnings("unused")
        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "isAscii(targetEncoding) || isBytes(targetEncoding)"})
        static TruffleString targetAscii(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode) {
            assert !is7Bit(codeRangeA);
            byte[] buffer = new byte[codePointLengthA];
            int length = 0;
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, sourceEncoding);
            boolean replacedChars = false;
            while (it.hasNext()) {
                int codepoint = iteratorNextNode.execute(node, it, DecodingErrorHandler.DEFAULT_UTF8_INCOMPLETE_SEQUENCES);
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
        static TruffleString latin1Transcode(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode) {
            assert !is7Or8Bit(codeRangeA);
            byte[] buffer = new byte[codePointLengthA];
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, sourceEncoding);
            int codeRange = TSCodeRange.get7Bit();
            boolean replacedChars = false;
            int length = 0;
            while (it.hasNext()) {
                int codepoint = iteratorNextNode.execute(node, it, DecodingErrorHandler.DEFAULT_UTF8_INCOMPLETE_SEQUENCES);
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
        static TruffleString utf8TranscodeRegular(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode,
                        @Cached @Shared("brokenProfile") InlinedConditionProfile brokenProfile,
                        @Cached @Shared("outOfMemoryProfile") InlinedBranchProfile outOfMemoryProfile) {
            return utf8Transcode(node, a, arrayA, codePointLengthA, codeRangeA, sourceEncoding, errorHandler, iteratorNextNode, false, brokenProfile, outOfMemoryProfile);
        }

        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "isLarge(codePointLengthA)", "isUTF8(targetEncoding)"})
        static TruffleString utf8TranscodeLarge(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode,
                        @Cached @Shared("brokenProfile") InlinedConditionProfile brokenProfile,
                        @Cached @Shared("outOfMemoryProfile") InlinedBranchProfile outOfMemoryProfile) {
            return utf8Transcode(node, a, arrayA, codePointLengthA, codeRangeA, sourceEncoding, errorHandler, iteratorNextNode, true, brokenProfile, outOfMemoryProfile);
        }

        static boolean isLarge(int codePointLengthA) {
            return codePointLengthA > TStringConstants.MAX_ARRAY_SIZE / 4;
        }

        private static TruffleString utf8Transcode(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        TranscodingErrorHandler errorHandler,
                        TruffleStringIterator.InternalNextNode iteratorNextNode,
                        boolean isLarge, InlinedConditionProfile brokenProfile,
                        InlinedBranchProfile outOfMemoryProfile) {
            assert !is7Bit(codeRangeA);
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, sourceEncoding);
            boolean allowUTF16Surrogates = errorHandler == TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8;
            DecodingErrorHandler decodingErrorHandler = getDecodingErrorHandler(errorHandler);
            byte[] buffer = new byte[isLarge ? TStringConstants.MAX_ARRAY_SIZE : codePointLengthA * 4];
            int codeRange = TSCodeRange.getValidMultiByte();
            int length = 0;
            int loopCount = 0;
            while (it.hasNext()) {
                int codepoint = iteratorNextNode.execute(node, it, decodingErrorHandler);
                boolean isSurrogate = isUTF16Surrogate(codepoint);
                boolean isGreaterMax = isGreaterThanMaxUTFCodepoint(codepoint);
                if (isSurrogate || isGreaterMax) {
                    codeRange = TSCodeRange.getBrokenMultiByte();
                    codepoint = utfReplaceInvalid(codepoint, isSurrogate, isGreaterMax, allowUTF16Surrogates);
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
                long attrs = TStringOps.calcStringAttributesUTF8(node, buffer, 0, length, false, false, brokenProfile);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            } else {
                codePointLength = codePointLengthA;
            }
            return create(a, Arrays.copyOf(buffer, length), length, 0, Encoding.UTF_8, codePointLength, codeRange);
        }

        @Specialization(guards = {"isUTF32(sourceEncoding)", "isUTF16(targetEncoding)"})
        TruffleString utf16Fixed32Bit(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, @SuppressWarnings("unused") Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler) {
            assert TStringGuards.isValidFixedWidth(codeRangeA) || TStringGuards.isBrokenFixedWidth(codeRangeA);
            assert isStride2(a);
            boolean allowUTF16Surrogates = isAllowUTF16SurrogatesUTF16Or32(errorHandler);
            byte[] buffer = new byte[codePointLengthA * 4];
            int length = 0;
            int codeRange = TStringGuards.isValidFixedWidth(codeRangeA) ? TSCodeRange.getValidMultiByte() : TSCodeRange.getBrokenMultiByte();
            for (int i = 0; i < a.length(); i++) {
                length += Encodings.utf16Encode(utfReplaceInvalid(TStringOps.readS2(a, arrayA, i), allowUTF16Surrogates), buffer, length);
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
            return create(a, Arrays.copyOf(buffer, length * 2), length, 1, Encoding.UTF_16, codePointLength, codeRange);
        }

        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "!isFixedWidth(codeRangeA)", "!isLarge(codePointLengthA)", "isUTF16(targetEncoding)"})
        static TruffleString utf16TranscodeRegular(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode,
                        @Cached @Shared("outOfMemoryProfile") InlinedBranchProfile outOfMemoryProfile) {
            return utf16Transcode(node, a, arrayA, codePointLengthA, codeRangeA, sourceEncoding, errorHandler, iteratorNextNode, false, outOfMemoryProfile);
        }

        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "!isFixedWidth(codeRangeA)", "isLarge(codePointLengthA)", "isUTF16(targetEncoding)"})
        static TruffleString utf16TranscodeLarge(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode,
                        @Cached @Shared("outOfMemoryProfile") InlinedBranchProfile outOfMemoryProfile) {
            return utf16Transcode(node, a, arrayA, codePointLengthA, codeRangeA, sourceEncoding, errorHandler, iteratorNextNode, true, outOfMemoryProfile);
        }

        private static TruffleString utf16Transcode(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        TranscodingErrorHandler errorHandler,
                        TruffleStringIterator.InternalNextNode iteratorNextNode,
                        boolean isLarge,
                        InlinedBranchProfile outOfMemoryProfile) {
            assert TStringGuards.isValidOrBrokenMultiByte(codeRangeA);
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, sourceEncoding);
            boolean allowUTF16Surrogates = isAllowUTF16SurrogatesUTF16Or32(errorHandler);
            byte[] buffer = new byte[codePointLengthA];
            int codePointLength = codePointLengthA;
            int length = 0;
            int codeRange = TSCodeRange.get7Bit();
            DecodingErrorHandler decodingErrorHandler = getDecodingErrorHandler(errorHandler);
            while (it.hasNext()) {
                int curIndex = it.getRawIndex();
                int codepoint = iteratorNextNode.execute(node, it, decodingErrorHandler);
                if (codepoint > 0xff) {
                    buffer = TStringOps.arraycopyOfWithStride(node, buffer, 0, length, 0, codePointLengthA, 1);
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
                int codepoint = iteratorNextNode.execute(node, it, decodingErrorHandler);
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
                writeToByteArray(buffer, 1, length++, codepoint);
                TStringConstants.truffleSafePointPoll(node, length);
            }
            codePointLength = length;
            if (!it.hasNext()) {
                if (isBrokenMultiByte(codeRange)) {
                    long attrs = TStringOps.calcStringAttributesUTF16(node, buffer, 0, length, false);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                    codeRange = StringAttributes.getCodeRange(attrs);
                }
                return create(a, trim(buffer, length * 2), length, 1, Encoding.UTF_16, codePointLength, codeRange);
            }
            int loopCount = 0;
            while (it.hasNext()) {
                int codepoint = iteratorNextNode.execute(node, it, decodingErrorHandler);
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
                long attrs = TStringOps.calcStringAttributesUTF16(node, buffer, 0, length, false);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            }
            return create(a, Arrays.copyOf(buffer, length * 2), length, 1, Encoding.UTF_16, codePointLength, codeRange);
        }

        @Specialization(guards = {"!isUTF16(sourceEncoding)", "isSupportedEncoding(sourceEncoding)", "!isFixedWidth(codeRangeA)", "!isLarge(codePointLengthA)", "isUTF32(targetEncoding)"})
        static TruffleString utf32TranscodeRegular(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode) {
            return utf32Transcode(node, a, arrayA, codePointLengthA, codeRangeA, sourceEncoding, errorHandler, iteratorNextNode);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"isSupportedEncoding(sourceEncoding)", "!isFixedWidth(codeRangeA)", "isLarge(codePointLengthA)", "isUTF32(targetEncoding)"})
        static TruffleString utf32TranscodeLarge(AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding, Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler) {
            throw InternalErrors.outOfMemory();
        }

        @Specialization(guards = {"isUTF16(sourceEncoding)", "!isFixedWidth(codeRangeA)", "!isLarge(codePointLengthA)", "isUTF32(targetEncoding)"})
        static TruffleString utf32TranscodeUTF16(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        @SuppressWarnings("unused") Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Cached @Shared("iteratorNextNode") TruffleStringIterator.InternalNextNode iteratorNextNode) {
            assert containsSurrogates(a);
            boolean allowUTF16Surrogates = isAllowUTF16SurrogatesUTF16Or32(errorHandler);
            DecodingErrorHandler decodingErrorHandler = getDecodingErrorHandler(errorHandler);
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, sourceEncoding);
            byte[] buffer = new byte[codePointLengthA << 2];
            int length = 0;
            while (it.hasNext()) {
                writeToByteArray(buffer, 2, length++, utfReplaceInvalid(iteratorNextNode.execute(node, it, decodingErrorHandler), allowUTF16Surrogates));
                TStringConstants.truffleSafePointPoll(node, length);
            }
            assert length == codePointLengthA;
            boolean isBroken = isBrokenMultiByte(codeRangeA);
            int codePointLength = codePointLengthA;
            int codeRange = isBroken ? TSCodeRange.getBrokenFixedWidth() : TSCodeRange.getValidFixedWidth();
            if (isBroken && !allowUTF16Surrogates) {
                long attrs = TStringOps.calcStringAttributesUTF16(node, buffer, 0, length, false);
                codePointLength = StringAttributes.getCodePointLength(attrs);
                codeRange = StringAttributes.getCodeRange(attrs);
            }
            return create(a, buffer, length, 2, Encoding.UTF_32, codePointLength, codeRange);
        }

        private static TruffleString utf32Transcode(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, int codeRangeA, Encoding sourceEncoding,
                        TranscodingErrorHandler errorHandler,
                        TruffleStringIterator.InternalNextNode iteratorNextNode) {
            assert TStringGuards.isValidOrBrokenMultiByte(codeRangeA);
            TruffleStringIterator it = AbstractTruffleString.forwardIterator(a, arrayA, codeRangeA, sourceEncoding);
            DecodingErrorHandler decodingErrorHandler = getDecodingErrorHandler(errorHandler);
            byte[] buffer = new byte[codePointLengthA];
            int length = 0;
            int codeRange = TSCodeRange.get7Bit();
            int codepoint = 0;
            while (it.hasNext()) {
                int curIndex = it.getRawIndex();
                codepoint = iteratorNextNode.execute(node, it, decodingErrorHandler);
                if (codepoint > 0xff) {
                    if (isUTF16Surrogate(codepoint)) {
                        buffer = TStringOps.arraycopyOfWithStride(node, buffer, 0, length, 0, codePointLengthA, 2);
                        codeRange = TSCodeRange.getBrokenFixedWidth();
                    } else {
                        buffer = TStringOps.arraycopyOfWithStride(node, buffer, 0, length, 0, codePointLengthA, 1);
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
                    codepoint = iteratorNextNode.execute(node, it, decodingErrorHandler);
                    if (codepoint > 0xffff || isUTF16Surrogate(codepoint)) {
                        buffer = TStringOps.arraycopyOfWithStride(node, buffer, 0, length, 1, codePointLengthA, 2);
                        codeRange = Encodings.isValidUnicodeCodepoint(codepoint) ? TSCodeRange.getValidFixedWidth() : TSCodeRange.getBrokenFixedWidth();
                        it.setRawIndex(curIndex);
                        break;
                    }
                    writeToByteArray(buffer, 1, length++, codepoint);
                    TStringConstants.truffleSafePointPoll(node, length);
                }
            }
            if (!it.hasNext()) {
                assert length == codePointLengthA || sourceEncoding == Encoding.UTF_8 && isBrokenMultiByte(codeRangeA);
                return create(a, trim(buffer, length * 2), length, 1, Encoding.UTF_32, length, codeRange);
            }
            while (it.hasNext()) {
                codepoint = iteratorNextNode.execute(node, it, decodingErrorHandler);
                boolean isSurrogate = isUTF16Surrogate(codepoint);
                boolean isGreaterMax = isGreaterThanMaxUTFCodepoint(codepoint);
                if (isSurrogate || isGreaterMax) {
                    codeRange = TSCodeRange.getBrokenFixedWidth();
                    codepoint = utfReplaceInvalid(codepoint, isSurrogate, isGreaterMax, isAllowUTF16SurrogatesUTF16Or32(errorHandler));
                }
                writeToByteArray(buffer, 2, length++, codepoint);
                TStringConstants.truffleSafePointPoll(node, length);
            }
            return create(a, trim(buffer, length * 4), length, 2, Encoding.UTF_32, length, codeRange);
        }

        @Specialization(guards = {"isUnsupportedEncoding(sourceEncoding) || isUnsupportedEncoding(targetEncoding)"})
        static TruffleString unsupported(Node node, AbstractTruffleString a, Object arrayA, int codePointLengthA, @SuppressWarnings("unused") int codeRangeA,
                        @SuppressWarnings("unused") Encoding sourceEncoding,
                        Encoding targetEncoding,
                        TranscodingErrorHandler errorHandler,
                        @Shared("outOfMemoryProfile") @Cached InlinedBranchProfile outOfMemoryProfile,
                        @Exclusive @Cached InlinedConditionProfile nativeProfile,
                        @Cached FromBufferWithStringCompactionNode fromBufferWithStringCompactionNode) {
            return JCodings.getInstance().transcode(node, a, arrayA, codePointLengthA, targetEncoding, outOfMemoryProfile, nativeProfile, fromBufferWithStringCompactionNode, errorHandler);
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

        private static boolean isAllowUTF16SurrogatesUTF16Or32(TranscodingErrorHandler errorHandler) {
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
            return TruffleString.createFromByteArray(buffer, length, stride, encoding, codePointLength, codeRange, isBroken(a.codeRange()) || isBroken(codeRange) || a.isMutable() || replacedChars);
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
