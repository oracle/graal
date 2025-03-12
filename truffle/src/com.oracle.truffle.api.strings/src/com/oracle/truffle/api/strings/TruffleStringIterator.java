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

import static com.oracle.truffle.api.strings.TStringGuards.is7Bit;
import static com.oracle.truffle.api.strings.TStringGuards.is7Or8Bit;
import static com.oracle.truffle.api.strings.TStringGuards.isAscii;
import static com.oracle.truffle.api.strings.TStringGuards.isBroken;
import static com.oracle.truffle.api.strings.TStringGuards.isBuiltin;
import static com.oracle.truffle.api.strings.TStringGuards.isDefaultVariant;
import static com.oracle.truffle.api.strings.TStringGuards.isFixedWidth;
import static com.oracle.truffle.api.strings.TStringGuards.isReturnNegative;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16FE;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF32FE;
import static com.oracle.truffle.api.strings.TStringGuards.isUnsupportedEncoding;
import static com.oracle.truffle.api.strings.TStringGuards.isUpTo16Bit;
import static com.oracle.truffle.api.strings.TStringGuards.isUpToValid;
import static com.oracle.truffle.api.strings.TStringGuards.isUpToValidFixedWidth;
import static com.oracle.truffle.api.strings.TStringGuards.isValid;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.HostCompilerDirectives.InliningCutoff;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.profiles.InlinedIntValueProfile;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

// Checkstyle: stop
/**
 * An iterator object that allows iterating over a {@link TruffleString}'s codepoints, without
 * having to re-calculate codepoint indices on every access.
 * <p>
 * Usage Example:
 *
 * <pre>
 * <code>
 * {@code
 * abstract static class SomeNode extends Node {
 *
 *     &#64;Specialization
 *     static void someSpecialization(
 *                     TruffleString string,
 *                     &#64;Cached TruffleString.CreateCodePointIteratorNode createCodePointIteratorNode,
 *                     &#64;Cached TruffleStringIterator.NextNode nextNode,
 *                     &#64;Cached TruffleString.CodePointLengthNode codePointLengthNode,
 *                     &#64;Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode) {
 *
 *         // iterating over a string's code points using TruffleStringIterator
 *         TruffleStringIterator iterator = createCodePointIteratorNode.execute(string, Encoding.UTF_8);
 *         while (iterator.hasNext()) {
 *             System.out.printf("%x%n", nextNode.execute(iterator));
 *         }
 *
 *         // uncached variant:
 *         TruffleStringIterator iterator2 = string.createCodePointIteratorUncached(Encoding.UTF_8);
 *         while (iterator2.hasNext()) {
 *             System.out.printf("%x%n", iterator2.nextUncached());
 *         }
 *
 *         // suboptimal variant: using CodePointAtIndexNode in a loop
 *         int codePointLength = codePointLengthNode.execute(string, Encoding.UTF_8);
 *         for (int i = 0; i < codePointLength; i++) {
 *             // performance problem: codePointAtIndexNode may have to calculate the byte index
 *             // corresponding
 *             // to codepoint index i for every loop iteration
 *             System.out.printf("%x%n", codePointAtIndexNode.execute(string, i, Encoding.UTF_8));
 *         }
 *     }
 * }
 * }
 * </code>
 * </pre>
 *
 * @since 22.1
 */
// Checkstyle: resume
public final class TruffleStringIterator {

    final AbstractTruffleString a;
    final byte[] arrayA;
    final long offsetA;
    final byte strideA;
    final byte codeRangeA;
    final Encoding encoding;
    final TruffleString.ErrorHandling errorHandling;
    private int rawIndex;

    TruffleStringIterator(AbstractTruffleString a, byte[] arrayA, long offsetA, int codeRangeA, Encoding encoding, TruffleString.ErrorHandling errorHandling, int rawIndex) {
        assert TSCodeRange.isCodeRange(codeRangeA);
        this.a = a;
        this.arrayA = arrayA;
        this.offsetA = offsetA;
        this.strideA = (byte) a.stride();
        this.codeRangeA = (byte) codeRangeA;
        this.encoding = encoding;
        this.errorHandling = errorHandling;
        this.rawIndex = rawIndex;
    }

    /**
     * Returns {@code true} if there are more codepoints remaining.
     *
     * @since 22.1
     */
    public boolean hasNext() {
        return rawIndex < a.length();
    }

    /**
     * Returns {@code true} if there are more codepoints remaining in reverse direction.
     *
     * @since 22.1
     */
    public boolean hasPrevious() {
        return rawIndex > 0;
    }

    /**
     * Returns the next codepoint's byte index, where "byte index" refers the codepoint's first byte
     * in forward mode, while in backward mode it refers to the first byte <i>after</i> the
     * codepoint.
     *
     * @since 22.3
     */
    public int getByteIndex() {
        return rawIndex << encoding.naturalStride;
    }

    private int applyErrorHandler(DecodingErrorHandler errorHandler, int startIndex) {
        return applyErrorHandler(errorHandler, startIndex, true);
    }

    private int applyErrorHandlerReverse(DecodingErrorHandler errorHandler, int startIndex) {
        return applyErrorHandler(errorHandler, startIndex, false);
    }

    @InliningCutoff
    private int applyErrorHandler(DecodingErrorHandler errorHandler, int startIndex, boolean forward) {
        CompilerAsserts.partialEvaluationConstant(forward);
        if (isReturnNegative(errorHandler)) {
            return -1;
        } else if (isBuiltin(errorHandler)) {
            return Encodings.invalidCodepoint();
        }
        int byteEnd = getByteIndex();
        rawIndex = startIndex;
        int byteStart = getByteIndex();
        int estimatedByteLength = forward ? byteEnd - byteStart : byteStart - byteEnd;
        DecodingErrorHandler.Result result = errorHandler.apply(a, byteStart, estimatedByteLength);
        errorHandlerSkipBytes(result.byteLength(), forward);
        return result.codepoint();
    }

    void errorHandlerSkipBytes(int byteLength, boolean forward) {
        int rawLength = byteLength >> encoding.naturalStride;
        if (rawLength == 0) {
            throw InternalErrors.illegalState("custom error handler consumed less than one char / int value");
        }
        if (forward) {
            rawIndex += rawLength;
            if (Integer.compareUnsigned(rawIndex, a.length()) > 0) {
                throw InternalErrors.illegalState("custom error handler consumed more bytes than string length");
            }
        } else {
            rawIndex -= rawLength;
            if (rawIndex < 0) {
                throw InternalErrors.illegalState("custom error handler consumed more bytes than string length");
            }
        }
    }

    abstract static class InternalNextNode extends AbstractInternalNode {

        final int execute(Node node, TruffleStringIterator it, Encoding encoding) {
            return execute(node, it, encoding, DecodingErrorHandler.DEFAULT);
        }

        final int execute(Node node, TruffleStringIterator it, Encoding encoding, DecodingErrorHandler errorHandler) {
            if (!it.hasNext()) {
                throw InternalErrors.illegalState("end of string has been reached already");
            }
            return executeInternal(node, it, encoding, errorHandler);
        }

        abstract int executeInternal(Node node, TruffleStringIterator it, Encoding encoding, DecodingErrorHandler errorHandler);

        @SuppressWarnings("fallthrough")
        @Specialization(guards = "isUTF8(encoding)")
        static int utf8(Node node, TruffleStringIterator it, @SuppressWarnings("unused") Encoding encoding, @SuppressWarnings("unused") DecodingErrorHandler errorHandler,
                        @Cached @Exclusive InlinedConditionProfile asciiProfile,
                        @Cached @Exclusive InlinedConditionProfile validProfile) {
            byte codeRange = it.codeRangeA;
            int b = it.readAndIncS0();
            if (asciiProfile.profile(node, is7Bit(codeRange))) {
                return b;
            } else if (validProfile.profile(node, isValid(codeRange))) {
                if (b < 0x80) {
                    return b;
                }
                int nBytes = Integer.numberOfLeadingZeros(~(b << 24));
                int codepoint = b & (0xff >>> nBytes);
                assert 1 < nBytes && nBytes < 5 : nBytes;
                assert it.rawIndex + nBytes - 1 <= it.a.length();
                // Checkstyle: stop FallThrough
                switch (nBytes) {
                    case 4:
                        assert it.curIsUtf8ContinuationByte();
                        codepoint = codepoint << 6 | (it.readAndIncS0() & 0x3f);
                    case 3:
                        assert it.curIsUtf8ContinuationByte();
                        codepoint = codepoint << 6 | (it.readAndIncS0() & 0x3f);
                    default:
                        assert it.curIsUtf8ContinuationByte();
                        codepoint = codepoint << 6 | (it.readAndIncS0() & 0x3f);
                }
                // Checkstyle: resume FallThrough
                return codepoint;
            } else {
                assert isBroken(codeRange);
                return utf8Broken(it, b, errorHandler);
            }
        }

        @Specialization(guards = "isUTF16(encoding)")
        static int utf16(Node node, TruffleStringIterator it, @SuppressWarnings("unused") Encoding encoding, @SuppressWarnings("unused") DecodingErrorHandler errorHandler,
                        @Cached @Exclusive InlinedConditionProfile fixedProfile,
                        @Cached @Exclusive InlinedConditionProfile compressedProfile,
                        @Cached @Exclusive InlinedConditionProfile validProfile) {
            byte codeRange = it.codeRangeA;
            if (fixedProfile.profile(node, isFixedWidth(codeRange))) {
                if (compressedProfile.profile(node, it.strideA == 0)) {
                    return it.readAndIncS0();
                } else {
                    return it.readAndIncS1(false);
                }
            } else if (validProfile.profile(node, isValid(codeRange))) {
                return utf16ValidIntl(it, false);
            } else {
                assert isBroken(codeRange);
                return utf16BrokenIntl(it, false, errorHandler);
            }
        }

        @Specialization(guards = "isUTF32(encoding)")
        static int utf32(Node node, TruffleStringIterator it, @SuppressWarnings("unused") Encoding encoding, @SuppressWarnings("unused") DecodingErrorHandler errorHandler,
                        @Cached @Exclusive InlinedConditionProfile oneByteProfile,
                        @Cached @Exclusive InlinedConditionProfile twoByteProfile,
                        @Cached @Exclusive InlinedConditionProfile validProfile) {
            byte codeRange = it.codeRangeA;
            if (oneByteProfile.profile(node, it.strideA == 0)) {
                assert is7Or8Bit(codeRange);
                return it.readAndIncS0();
            } else if (twoByteProfile.profile(node, it.strideA == 1)) {
                assert isUpTo16Bit(codeRange);
                return it.readAndIncS1(false);
            } else {
                assert it.strideA == 2;
                int codepoint = it.readAndIncS2();
                if (validProfile.profile(node, isDefaultVariant(errorHandler) || isUpToValid(codeRange))) {
                    return codepoint;
                } else {
                    assert isBroken(codeRange);
                    return utf32BrokenIntl(it, errorHandler, codepoint, 1);
                }
            }
        }

        @Fallback
        static int unlikelyCases(Node node, TruffleStringIterator it, Encoding encoding, DecodingErrorHandler errorHandler,
                        @Cached @Exclusive InlinedIntValueProfile encodingProfile) {
            int enc = encodingProfile.profile(node, encoding.id);
            byte codeRange = it.codeRangeA;
            if (isUpToValidFixedWidth(codeRange)) {
                return it.readAndIncS0();
            } else if (isAscii(enc)) {
                assert isBroken(codeRange);
                int codepoint = it.readAndIncS0();
                if (isDefaultVariant(errorHandler) || codepoint < 0x80) {
                    return codepoint;
                } else {
                    return it.applyErrorHandler(errorHandler, it.rawIndex - 1);
                }
            } else if (isUTF32FE(enc)) {
                if (isDefaultVariant(errorHandler) || isValid(codeRange)) {
                    return it.readAndIncS2UTF32FE();
                } else {
                    assert isBroken(codeRange);
                    return utf32BrokenIntl(it, errorHandler, it.readAndIncS2UTF32FE(), 4);
                }
            } else if (isUTF16FE(enc)) {
                if (isValid(codeRange)) {
                    return utf16ValidIntl(it, true);
                } else {
                    assert isBroken(codeRange);
                    return utf16BrokenIntl(it, true, errorHandler);
                }
            } else {
                assert isUnsupportedEncoding(enc);
                return unsupported(it, encoding, errorHandler);
            }
        }

        @InliningCutoff
        private static int utf8Broken(TruffleStringIterator it, int firstByte, DecodingErrorHandler errorHandler) {
            int startIndex = it.rawIndex - 1;
            int b = firstByte;
            if (b < 0x80) {
                return b;
            }
            int nBytes = Encodings.utf8CodePointLength(b);
            int codepoint = b & (0xff >>> nBytes);
            /*
             * Copyright (c) 2008-2010 Bjoern Hoehrmann <bjoern@hoehrmann.de> See
             * http://bjoern.hoehrmann.de/utf-8/decoder/dfa/ for details.
             */
            byte[] stateMachine = Encodings.getUTF8DecodingStateMachine(errorHandler);
            int type = stateMachine[b];
            int state = stateMachine[256 + type];
            if (state != Encodings.UTF8_REJECT) {
                int maxIndex = Math.min(it.a.length(), it.rawIndex - 1 + nBytes);
                while (it.rawIndex < maxIndex) {
                    b = it.readFwdS0();
                    type = stateMachine[b];
                    state = stateMachine[256 + state + type];
                    if (state == Encodings.UTF8_REJECT) {
                        break;
                    }
                    codepoint = (b & 0x3f) | (codepoint << 6);
                    it.rawIndex++;
                }
            }
            if (state == Encodings.UTF8_ACCEPT) {
                return codepoint;
            } else if (isDefaultVariant(errorHandler)) {
                if (errorHandler == DecodingErrorHandler.DEFAULT) {
                    it.rawIndex = startIndex + 1;
                }
                return Encodings.invalidCodepoint();
            } else {
                if (errorHandler == DecodingErrorHandler.RETURN_NEGATIVE) {
                    it.rawIndex = startIndex + 1;
                }
                return it.applyErrorHandler(errorHandler, startIndex);
            }
        }

        private static int utf16ValidIntl(TruffleStringIterator it, boolean foreignEndian) {
            char c = (char) it.readAndIncS1(foreignEndian);
            if (Encodings.isUTF16HighSurrogate(c)) {
                assert it.hasNext();
                assert Encodings.isUTF16LowSurrogate(it.readFwdS1(foreignEndian));
                return Character.toCodePoint(c, (char) it.readAndIncS1(foreignEndian));
            }
            return c;
        }

        @InliningCutoff
        private static int utf16BrokenIntl(TruffleStringIterator it, boolean foreignEndian, DecodingErrorHandler errorHandler) {
            char c = (char) it.readAndIncS1(foreignEndian);
            if (isReturnNegative(errorHandler) || !isBuiltin(errorHandler)) {
                if (Encodings.isUTF16Surrogate(c)) {
                    if (Encodings.isUTF16HighSurrogate(c) && it.hasNext()) {
                        char c2 = (char) it.readFwdS1(foreignEndian);
                        if (Encodings.isUTF16LowSurrogate(c2)) {
                            it.rawIndex++;
                            return Character.toCodePoint(c, c2);
                        }
                    }
                    return it.applyErrorHandler(errorHandler, it.rawIndex - 1);
                }
            } else {
                assert isDefaultVariant(errorHandler);
                if (Encodings.isUTF16HighSurrogate(c) && it.hasNext()) {
                    char c2 = (char) it.readFwdS1(foreignEndian);
                    if (Encodings.isUTF16LowSurrogate(c2)) {
                        it.rawIndex++;
                        return Character.toCodePoint(c, c2);
                    }
                }
            }
            return c;
        }

        @InliningCutoff
        private static int utf32BrokenIntl(TruffleStringIterator it, DecodingErrorHandler errorHandler, int codepoint, int errOffset) {
            if (Encodings.isValidUnicodeCodepoint(codepoint)) {
                return codepoint;
            } else {
                return it.applyErrorHandler(errorHandler, it.rawIndex - errOffset);
            }
        }

        @TruffleBoundary
        private static int unsupported(TruffleStringIterator it, Encoding encoding, DecodingErrorHandler errorHandler) {
            assert it.hasNext();
            JCodings jcodings = JCodings.getInstance();
            byte[] bytes = JCodings.asByteArray(it.a, it.arrayA);
            int startIndex = it.rawIndex;
            int p = it.a.byteArrayOffset() + it.rawIndex;
            int end = it.a.byteArrayOffset() + it.a.length();
            int length = jcodings.getCodePointLength(encoding, bytes, p, end);
            int codepoint = 0;
            if (length < 1) {
                if (length < -1) {
                    // broken multibyte codepoint at end of string
                    it.rawIndex = it.a.length();
                } else {
                    it.rawIndex++;
                }
            } else {
                it.rawIndex += length;
                codepoint = jcodings.readCodePoint(encoding, bytes, p, end, errorHandler);
            }
            if (length < 1 || !jcodings.isValidCodePoint(encoding, codepoint)) {
                return it.applyErrorHandler(errorHandler, startIndex);
            }
            return codepoint;
        }

        static InternalNextNode getUncached() {
            return TruffleStringIteratorFactory.InternalNextNodeGen.getUncached();
        }
    }

    /**
     * Returns the next codepoint in the string.
     *
     * @since 22.1
     */
    public abstract static class NextNode extends AbstractPublicNode {

        NextNode() {
        }

        /**
         * Returns the next codepoint in the string.
         *
         * @since 22.1
         * @deprecated use {@link #execute(TruffleStringIterator, Encoding)} instead.
         */
        @Deprecated(since = "25.0")
        public final int execute(TruffleStringIterator it) {
            return execute(it, it.encoding);
        }

        /**
         * Returns the next codepoint in the string.
         *
         * @since 25.0
         */
        public abstract int execute(TruffleStringIterator it, Encoding encoding);

        @Specialization
        final int doDefault(TruffleStringIterator it, Encoding encoding,
                        @Cached InternalNextNode nextNode) {
            return nextNode.execute(this, it, encoding, it.errorHandling.errorHandler);
        }

        /**
         * Create a new {@link NextNode}.
         *
         * @since 22.1
         */
        @NeverDefault
        public static NextNode create() {
            return TruffleStringIteratorFactory.NextNodeGen.create();
        }

        /**
         * Get the uncached version of {@link NextNode}.
         *
         * @since 22.1
         */
        public static NextNode getUncached() {
            return TruffleStringIteratorFactory.NextNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link NextNode}.
     *
     * @since 22.1
     * @deprecated use {@link #nextUncached(Encoding)} instead.
     */
    @Deprecated(since = "25.0")
    @TruffleBoundary
    public int nextUncached() {
        return NextNode.getUncached().execute(this);
    }

    /**
     * Shorthand for calling the uncached version of {@link NextNode}.
     *
     * @since 25.0
     */
    @TruffleBoundary
    public int nextUncached(Encoding expectedEncoding) {
        return NextNode.getUncached().execute(this, expectedEncoding);
    }

    /**
     * Returns the previous codepoint in the string.
     *
     * @since 22.1
     */
    public abstract static class PreviousNode extends AbstractPublicNode {

        PreviousNode() {
        }

        /**
         * Returns the previous codepoint in the string.
         *
         * @since 22.1
         * @deprecated use {@link #execute(TruffleStringIterator, Encoding)} instead.
         */
        @Deprecated(since = "25.0")
        public final int execute(TruffleStringIterator it) {
            return execute(it, it.encoding);
        }

        /**
         * Returns the previous codepoint in the string.
         *
         * @since 25.0
         */
        public abstract int execute(TruffleStringIterator it, Encoding encoding);

        @Specialization
        final int doDefault(TruffleStringIterator it, Encoding encoding,
                        @Cached InternalPreviousNode previousNode) {
            return previousNode.execute(this, it, encoding, it.errorHandling.errorHandler);
        }

        /**
         * Create a new {@link PreviousNode}.
         *
         * @since 22.1
         */
        @NeverDefault
        public static PreviousNode create() {
            return TruffleStringIteratorFactory.PreviousNodeGen.create();
        }

        /**
         * Get the uncached version of {@link PreviousNode}.
         *
         * @since 22.1
         */
        public static PreviousNode getUncached() {
            return TruffleStringIteratorFactory.PreviousNodeGen.getUncached();
        }
    }

    abstract static class InternalPreviousNode extends AbstractInternalNode {

        InternalPreviousNode() {
        }

        public final int execute(Node node, TruffleStringIterator it, Encoding encoding, DecodingErrorHandler errorHandler) {
            if (!it.hasPrevious()) {
                throw InternalErrors.illegalState("beginning of string has been reached already");
            }
            return executeInternal(node, it, encoding, errorHandler);
        }

        abstract int executeInternal(Node node, TruffleStringIterator it, Encoding encoding, DecodingErrorHandler errorHandler);

        @Specialization(guards = "isUTF8(encoding)")
        static int utf8(Node node, TruffleStringIterator it, @SuppressWarnings("unused") Encoding encoding, @SuppressWarnings("unused") DecodingErrorHandler errorHandler,
                        @Cached @Exclusive InlinedConditionProfile asciiProfile,
                        @Cached @Exclusive InlinedConditionProfile validProfile) {
            byte codeRange = it.codeRangeA;
            int b = it.readAndDecS0();
            if (asciiProfile.profile(node, is7Bit(codeRange))) {
                return b;
            } else if (validProfile.profile(node, isValid(codeRange))) {
                if (b < 0x80) {
                    return b;
                }
                assert Encodings.isUTF8ContinuationByte(b);
                int codepoint = b & 0x3f;
                for (int j = 1; j < 4; j++) {
                    b = it.readAndDecS0();
                    if (j < 3 && Encodings.isUTF8ContinuationByte(b)) {
                        codepoint |= (b & 0x3f) << (6 * j);
                    } else {
                        break;
                    }
                }
                int nBytes = Integer.numberOfLeadingZeros(~(b << 24));
                assert 1 < nBytes && nBytes < 5 : nBytes;
                return codepoint | (b & (0xff >>> nBytes)) << (6 * (nBytes - 1));
            } else {
                assert isBroken(codeRange);
                return utf8Broken(it, b, errorHandler);
            }
        }

        @Specialization(guards = "isUTF16(encoding)")
        static int utf16(Node node, TruffleStringIterator it, @SuppressWarnings("unused") Encoding encoding, @SuppressWarnings("unused") DecodingErrorHandler errorHandler,
                        @Cached @Exclusive InlinedConditionProfile fixedProfile,
                        @Cached @Exclusive InlinedConditionProfile compressedProfile,
                        @Cached @Exclusive InlinedConditionProfile validProfile) {
            byte codeRange = it.codeRangeA;
            if (fixedProfile.profile(node, isFixedWidth(codeRange))) {
                if (compressedProfile.profile(node, it.strideA == 0)) {
                    return it.readAndDecS0();
                } else {
                    return it.readAndDecS1(false);
                }
            } else if (validProfile.profile(node, isValid(codeRange))) {
                return utf16ValidIntl(it, false);
            } else {
                assert isBroken(codeRange);
                return utf16BrokenIntl(it, false, errorHandler);
            }
        }

        @Specialization(guards = "isUTF32(encoding)")
        static int utf32(Node node, TruffleStringIterator it, @SuppressWarnings("unused") Encoding encoding, @SuppressWarnings("unused") DecodingErrorHandler errorHandler,
                        @Cached @Exclusive InlinedConditionProfile oneByteProfile,
                        @Cached @Exclusive InlinedConditionProfile twoByteProfile,
                        @Cached @Exclusive InlinedConditionProfile validProfile) {
            byte codeRange = it.codeRangeA;
            if (oneByteProfile.profile(node, it.strideA == 0)) {
                assert is7Or8Bit(codeRange);
                return it.readAndDecS0();
            } else if (twoByteProfile.profile(node, it.strideA == 1)) {
                assert isUpTo16Bit(codeRange);
                return it.readAndDecS1(false);
            } else {
                assert it.strideA == 2;
                int codepoint = it.readAndDecS2();
                if (validProfile.profile(node, isDefaultVariant(errorHandler) || isUpToValid(codeRange))) {
                    return codepoint;
                } else {
                    assert isBroken(codeRange);
                    return utf32BrokenIntl(it, errorHandler, codepoint, 1);
                }
            }
        }

        @Fallback
        static int unlikelyCases(Node node, TruffleStringIterator it, Encoding encoding, DecodingErrorHandler errorHandler,
                        @Cached @Exclusive InlinedIntValueProfile encodingProfile) {
            int enc = encodingProfile.profile(node, encoding.id);
            byte codeRange = it.codeRangeA;
            if (isUpToValidFixedWidth(codeRange)) {
                return it.readAndDecS0();
            } else if (isAscii(enc)) {
                assert isBroken(codeRange);
                int codepoint = it.readAndDecS0();
                if (isDefaultVariant(errorHandler) || codepoint < 0x80) {
                    return codepoint;
                } else {
                    return it.applyErrorHandlerReverse(errorHandler, it.rawIndex + 1);
                }
            } else if (isUTF32FE(enc)) {
                if (isDefaultVariant(errorHandler) || isValid(codeRange)) {
                    return it.readAndDecS2UTF32FE();
                } else {
                    assert isBroken(codeRange);
                    return utf32BrokenIntl(it, errorHandler, it.readAndDecS2UTF32FE(), 4);
                }
            } else if (isUTF16FE(enc)) {
                if (isValid(codeRange)) {
                    return utf16ValidIntl(it, true);
                } else {
                    assert isBroken(codeRange);
                    return utf16BrokenIntl(it, true, errorHandler);
                }
            } else {
                assert isUnsupportedEncoding(enc);
                return unsupported(it, encoding, errorHandler);
            }
        }

        @InliningCutoff
        private static int utf8Broken(TruffleStringIterator it, int firstByte, DecodingErrorHandler errorHandler) {
            int startIndex = it.rawIndex + 1;
            int b = firstByte;
            if (b < 0x80) {
                return b;
            }
            int codepoint = b & 0x3f;
            byte[] stateMachine = Encodings.getUTF8DecodingStateMachineReverse(errorHandler);
            int type = stateMachine[b];
            int state = stateMachine[256 + type];
            int shift = 6;
            assert state != Encodings.UTF8_ACCEPT;
            if (state > Encodings.UTF8_REVERSE_INCOMPLETE_SEQ) {
                while (it.rawIndex > 0) {
                    b = it.readAndDecS0();
                    type = stateMachine[b];
                    state = stateMachine[256 + state + type];
                    if (state <= Encodings.UTF8_REVERSE_INCOMPLETE_SEQ) {
                        // breaks on ACCEPT, REJECT and INCOMPLETE_SEQ
                        break;
                    }
                    codepoint |= (b & 0x3f) << shift;
                    shift += 6;
                }
            }
            if (state == Encodings.UTF8_ACCEPT) {
                return (((0xff >> type) & b) << shift) | codepoint;
            } else if (isDefaultVariant(errorHandler)) {
                if (errorHandler == DecodingErrorHandler.DEFAULT || state != Encodings.UTF8_REVERSE_INCOMPLETE_SEQ) {
                    it.rawIndex = startIndex - 1;
                }
                return Encodings.invalidCodepoint();
            } else {
                if (errorHandler == DecodingErrorHandler.RETURN_NEGATIVE) {
                    it.rawIndex = startIndex - 1;
                }
                return it.applyErrorHandler(errorHandler, startIndex);
            }
        }

        private static int utf16ValidIntl(TruffleStringIterator it, boolean foreignEndian) {
            char c = (char) it.readAndDecS1(foreignEndian);
            if (Encodings.isUTF16LowSurrogate(c)) {
                assert Encodings.isUTF16HighSurrogate((char) it.readBckS1(foreignEndian));
                return Character.toCodePoint((char) it.readAndDecS1(foreignEndian), c);
            }
            return c;
        }

        @InliningCutoff
        private static int utf16BrokenIntl(TruffleStringIterator it, boolean foreignEndian, DecodingErrorHandler errorHandler) {
            char c = (char) it.readAndDecS1(foreignEndian);
            if (isReturnNegative(errorHandler) || !isBuiltin(errorHandler)) {
                if (Encodings.isUTF16Surrogate(c)) {
                    if (Encodings.isUTF16LowSurrogate(c) && it.hasPrevious()) {
                        char c2 = (char) it.readBckS1(foreignEndian);
                        if (Encodings.isUTF16HighSurrogate(c2)) {
                            it.rawIndex--;
                            return Character.toCodePoint(c2, c);
                        }
                    }
                    return it.applyErrorHandlerReverse(errorHandler, it.rawIndex + 1);
                }
            } else {
                if (Encodings.isUTF16LowSurrogate(c) && it.hasPrevious()) {
                    char c2 = (char) it.readBckS1(foreignEndian);
                    if (Encodings.isUTF16HighSurrogate(c2)) {
                        it.rawIndex--;
                        return Character.toCodePoint(c2, c);
                    }
                }
            }
            return c;
        }

        @InliningCutoff
        private static int utf32BrokenIntl(TruffleStringIterator it, DecodingErrorHandler errorHandler, int codepoint, int errOffset) {
            if (Encodings.isValidUnicodeCodepoint(codepoint)) {
                return codepoint;
            } else {
                return it.applyErrorHandlerReverse(errorHandler, it.rawIndex + errOffset);
            }
        }

        @TruffleBoundary
        private static int unsupported(TruffleStringIterator it, Encoding encoding, DecodingErrorHandler errorHandler) {
            assert it.hasPrevious();
            JCodings jcodings = JCodings.getInstance();
            byte[] bytes = JCodings.asByteArray(it.a, it.arrayA);
            int start = it.a.byteArrayOffset();
            int index = it.a.byteArrayOffset() + it.rawIndex;
            int end = it.a.byteArrayOffset() + it.a.length();
            int prevIndex = jcodings.getPreviousCodePointIndex(encoding, bytes, start, index, end);
            int codepoint = 0;
            if (prevIndex < 0) {
                it.rawIndex--;
            } else {
                assert prevIndex >= it.a.byteArrayOffset();
                assert prevIndex < index;
                it.rawIndex = prevIndex - it.a.byteArrayOffset();
                codepoint = jcodings.readCodePoint(encoding, bytes, prevIndex, end, errorHandler);
            }
            if (prevIndex < 0 || !jcodings.isValidCodePoint(encoding, codepoint)) {
                return it.applyErrorHandlerReverse(errorHandler, index);
            }
            return codepoint;
        }

        static InternalPreviousNode getUncached() {
            return TruffleStringIteratorFactory.InternalPreviousNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link PreviousNode}.
     *
     * @since 22.1
     * @deprecated use {@link #previousUncached(Encoding)} instead.
     */
    @Deprecated(since = "25.0")
    @TruffleBoundary
    public int previousUncached() {
        return PreviousNode.getUncached().execute(this);
    }

    /**
     * Shorthand for calling the uncached version of {@link PreviousNode}.
     *
     * @since 25.0
     */
    @TruffleBoundary
    public int previousUncached(Encoding expectedEncoding) {
        return PreviousNode.getUncached().execute(this, expectedEncoding);
    }

    int getRawIndex() {
        return rawIndex;
    }

    void setRawIndex(int i) {
        rawIndex = i;
    }

    private int readFwdS0() {
        assert a.stride() == 0;
        assert hasNext();
        return TStringOps.readS0(a, arrayA, offsetA, rawIndex);
    }

    private int readFwdS1(boolean foreignEndian) {
        CompilerAsserts.partialEvaluationConstant(foreignEndian);
        assert hasNext();
        if (foreignEndian) {
            assert a.stride() == 0;
            char c = TStringOps.readS1(arrayA, offsetA, a.length() >> 1, rawIndex >> 1);
            return Character.reverseBytes(c);
        } else {
            assert a.stride() == 1;
            return TStringOps.readS1(a, arrayA, offsetA, rawIndex);
        }
    }

    private int readBckS1(boolean foreignEndian) {
        CompilerAsserts.partialEvaluationConstant(foreignEndian);
        assert hasPrevious();
        if (foreignEndian) {
            assert a.stride() == 0;
            return Character.reverseBytes(TStringOps.readS1(arrayA, offsetA, a.length() >> 1, (rawIndex - 2) >> 1));
        } else {
            assert a.stride() == 1;
            return TStringOps.readS1(a, arrayA, offsetA, rawIndex - 1);
        }
    }

    private int readAndIncS0() {
        assert a.stride() == 0;
        assert hasNext();
        return TStringOps.readS0(a, arrayA, offsetA, rawIndex++);
    }

    private int readAndIncS1(boolean foreignEndian) {
        CompilerAsserts.partialEvaluationConstant(foreignEndian);
        assert hasNext();
        if (foreignEndian) {
            assert a.stride() == 0;
            char c = TStringOps.readS1(arrayA, offsetA, a.length() >> 1, rawIndex >> 1);
            rawIndex += 2;
            return Character.reverseBytes(c);
        } else {
            assert a.stride() == 1;
            return TStringOps.readS1(a, arrayA, offsetA, rawIndex++);
        }
    }

    private int readAndIncS2() {
        assert a.stride() == 2;
        assert hasNext();
        return TStringOps.readS2(a, arrayA, offsetA, rawIndex++);
    }

    private int readAndDecS1(boolean foreignEndian) {
        CompilerAsserts.partialEvaluationConstant(foreignEndian);
        assert hasPrevious();
        if (foreignEndian) {
            assert a.stride() == 0;
            rawIndex -= 2;
            return Character.reverseBytes(TStringOps.readS1(arrayA, offsetA, a.length() >> 1, rawIndex >> 1));

        } else {
            assert a.stride() == 1;
            return TStringOps.readS1(a, arrayA, offsetA, --rawIndex);
        }
    }

    private int readAndIncS2UTF32FE() {
        assert a.stride() == 0;
        assert hasNext();
        int value = Integer.reverseBytes(TStringOps.readS2(arrayA, offsetA, a.length() >> 2, rawIndex >> 2));
        rawIndex += 4;
        return value;
    }

    private int readAndDecS2UTF32FE() {
        assert a.stride() == 0;
        assert hasPrevious();
        rawIndex -= 4;
        return Integer.reverseBytes(TStringOps.readS2(arrayA, offsetA, a.length() >> 2, rawIndex >> 2));
    }

    private int readAndDecS0() {
        assert a.stride() == 0;
        assert hasPrevious();
        return TStringOps.readS0(a, arrayA, offsetA, --rawIndex);
    }

    private int readAndDecS2() {
        assert a.stride() == 2;
        assert hasPrevious();
        return TStringOps.readS2(a, arrayA, offsetA, --rawIndex);
    }

    private boolean curIsUtf8ContinuationByte() {
        return Encodings.isUTF8ContinuationByte(readFwdS0());
    }

    static int indexOf(Node location, TruffleStringIterator it, Encoding encoding, int codepoint, int fromIndex, int toIndex, InternalNextNode nextNode) {
        int aCodepointIndex = 0;
        while (aCodepointIndex < fromIndex && it.hasNext()) {
            nextNode.execute(location, it, encoding);
            aCodepointIndex++;
            TStringConstants.truffleSafePointPoll(location, aCodepointIndex);
        }
        if (aCodepointIndex < fromIndex) {
            return -1;
        }
        while (it.hasNext() && aCodepointIndex < toIndex) {
            if (nextNode.execute(location, it, encoding) == codepoint) {
                return aCodepointIndex;
            }
            aCodepointIndex++;
            TStringConstants.truffleSafePointPoll(location, aCodepointIndex);
        }
        return -1;
    }

    static int lastIndexOf(Node location, TruffleStringIterator it, Encoding encoding, int codepoint, int fromIndex, int toIndex, InternalNextNode nextNode) {
        int aCodepointIndex = 0;
        int result = -1;
        // the code point index is based on the beginning of the string, so we have to count
        // from there
        while (aCodepointIndex < fromIndex && it.hasNext()) {
            if (nextNode.execute(location, it, encoding) == codepoint) {
                result = aCodepointIndex;
            }
            aCodepointIndex++;
            TStringConstants.truffleSafePointPoll(location, aCodepointIndex);
        }
        if (aCodepointIndex < toIndex) {
            // fromIndex was out of bounds
            return -1;
        }
        return result;
    }

    static int indexOfString(Node node, TruffleStringIterator aIt, TruffleStringIterator bIt, Encoding encoding, int fromIndex, int toIndex,
                    InternalNextNode nextNodeA,
                    InternalNextNode nextNodeB) {
        if (!bIt.hasNext()) {
            return fromIndex;
        }
        int aCodepointIndex = 0;
        while (aCodepointIndex < fromIndex && aIt.hasNext()) {
            nextNodeA.execute(node, aIt, encoding);
            aCodepointIndex++;
            TStringConstants.truffleSafePointPoll(node, aCodepointIndex);
        }
        if (aCodepointIndex < fromIndex) {
            return -1;
        }
        int bFirst = nextNodeB.execute(node, bIt, encoding);
        int bSecondIndex = bIt.getRawIndex();
        while (aIt.hasNext() && aCodepointIndex < toIndex) {
            if (nextNodeA.execute(node, aIt, encoding) == bFirst) {
                if (!bIt.hasNext()) {
                    return aCodepointIndex;
                }
                int aCurIndex = aIt.getRawIndex();
                int innerLoopCount = 0;
                while (bIt.hasNext()) {
                    if (!aIt.hasNext()) {
                        return -1;
                    }
                    if (nextNodeA.execute(node, aIt, encoding) != nextNodeB.execute(node, bIt, encoding)) {
                        break;
                    }
                    if (!bIt.hasNext()) {
                        return aCodepointIndex;
                    }
                    TStringConstants.truffleSafePointPoll(node, ++innerLoopCount);
                }
                aIt.setRawIndex(aCurIndex);
                bIt.setRawIndex(bSecondIndex);
            }
            aCodepointIndex++;
            TStringConstants.truffleSafePointPoll(node, aCodepointIndex);
        }
        return -1;
    }

    static int byteIndexOfString(Node node, TruffleStringIterator aIt, TruffleStringIterator bIt, Encoding encoding, int fromByteIndex, int toByteIndex,
                    InternalNextNode nextNodeA,
                    InternalNextNode nextNodeB) {
        if (!bIt.hasNext()) {
            return fromByteIndex;
        }
        aIt.setRawIndex(fromByteIndex);
        int bFirst = nextNodeB.execute(node, bIt, encoding);
        int bSecondIndex = bIt.getRawIndex();
        int loopCount = 0;
        while (aIt.hasNext() && aIt.getRawIndex() < toByteIndex) {
            int ret = aIt.getRawIndex();
            if (nextNodeA.execute(node, aIt, encoding) == bFirst) {
                if (!bIt.hasNext()) {
                    return ret;
                }
                int aCurIndex = aIt.getRawIndex();
                while (bIt.hasNext()) {
                    if (!aIt.hasNext()) {
                        return -1;
                    }
                    if (nextNodeA.execute(node, aIt, encoding) != nextNodeB.execute(node, bIt, encoding)) {
                        break;
                    }
                    if (!bIt.hasNext()) {
                        return ret;
                    }
                    TStringConstants.truffleSafePointPoll(node, ++loopCount);
                }
                aIt.setRawIndex(aCurIndex);
                bIt.setRawIndex(bSecondIndex);
            }
            TStringConstants.truffleSafePointPoll(node, ++loopCount);
        }
        return -1;
    }

    static int lastIndexOfString(Node node, TruffleStringIterator aIt, TruffleStringIterator bIt, Encoding encoding, int fromIndex, int toIndex,
                    InternalNextNode nextNodeA,
                    InternalPreviousNode prevNodeA,
                    InternalPreviousNode prevNodeB) {
        if (!bIt.hasPrevious()) {
            return fromIndex;
        }
        int bFirstCodePoint = prevNodeB.execute(node, bIt, encoding, DecodingErrorHandler.DEFAULT);
        int lastMatchIndex = -1;
        int lastMatchByteIndex = -1;
        int aCodepointIndex = 0;
        while (aCodepointIndex < fromIndex && aIt.hasNext()) {
            if (nextNodeA.execute(node, aIt, encoding) == bFirstCodePoint) {
                lastMatchIndex = aCodepointIndex;
                lastMatchByteIndex = aIt.getRawIndex();
            }
            aCodepointIndex++;
            TStringConstants.truffleSafePointPoll(node, aCodepointIndex);
        }
        if (aCodepointIndex < fromIndex || lastMatchIndex < 0) {
            return -1;
        }
        aCodepointIndex = lastMatchIndex;
        aIt.setRawIndex(lastMatchByteIndex);
        int bSecondIndex = bIt.getRawIndex();
        while (aIt.hasPrevious() && aCodepointIndex >= toIndex) {
            if (prevNodeA.execute(node, aIt, encoding, DecodingErrorHandler.DEFAULT) == bFirstCodePoint) {
                if (!bIt.hasPrevious()) {
                    return aCodepointIndex;
                }
                int aCurIndex = aIt.getRawIndex();
                int aCurCodePointIndex = aCodepointIndex;
                while (bIt.hasPrevious()) {
                    if (!aIt.hasPrevious()) {
                        return -1;
                    }
                    if (prevNodeA.execute(node, aIt, encoding, DecodingErrorHandler.DEFAULT) != prevNodeB.execute(node, bIt, encoding, DecodingErrorHandler.DEFAULT)) {
                        break;
                    }
                    aCurCodePointIndex--;
                    if (!bIt.hasPrevious() && aCurCodePointIndex >= toIndex) {
                        return aCurCodePointIndex;
                    }
                    TStringConstants.truffleSafePointPoll(node, aCurCodePointIndex);
                }
                aIt.setRawIndex(aCurIndex);
                bIt.setRawIndex(bSecondIndex);
            }
            aCodepointIndex--;
            TStringConstants.truffleSafePointPoll(node, aCodepointIndex);
        }
        return -1;
    }

    static int lastByteIndexOfString(Node node, TruffleStringIterator aIt, TruffleStringIterator bIt, Encoding encoding, int fromByteIndex, int toByteIndex,
                    InternalNextNode nextNodeA,
                    InternalPreviousNode prevNodeA,
                    InternalPreviousNode prevNodeB) {
        if (!bIt.hasPrevious()) {
            return fromByteIndex;
        }
        int bFirstCodePoint = prevNodeB.execute(node, bIt, encoding, DecodingErrorHandler.DEFAULT);
        int lastMatchByteIndex = -1;
        int loopCount = 0;
        while (aIt.getRawIndex() < fromByteIndex && aIt.hasNext()) {
            if (nextNodeA.execute(node, aIt, encoding) == bFirstCodePoint) {
                lastMatchByteIndex = aIt.getRawIndex();
            }
            TStringConstants.truffleSafePointPoll(node, ++loopCount);
        }
        if (aIt.getRawIndex() < fromByteIndex || lastMatchByteIndex < 0) {
            return -1;
        }
        aIt.setRawIndex(lastMatchByteIndex);
        int bSecondIndex = bIt.getRawIndex();
        while (aIt.hasPrevious() && aIt.getRawIndex() > toByteIndex) {
            if (prevNodeA.execute(node, aIt, encoding, DecodingErrorHandler.DEFAULT) == bFirstCodePoint) {
                if (!bIt.hasPrevious()) {
                    return aIt.getRawIndex();
                }
                int aCurIndex = aIt.getRawIndex();
                while (bIt.hasPrevious()) {
                    if (!aIt.hasPrevious()) {
                        return -1;
                    }
                    if (prevNodeA.execute(node, aIt, encoding, DecodingErrorHandler.DEFAULT) != prevNodeB.execute(node, bIt, encoding, DecodingErrorHandler.DEFAULT)) {
                        break;
                    }
                    if (!bIt.hasPrevious() && aIt.getRawIndex() >= toByteIndex) {
                        return aIt.getRawIndex();
                    }
                    TStringConstants.truffleSafePointPoll(node, ++loopCount);
                }
                aIt.setRawIndex(aCurIndex);
                bIt.setRawIndex(bSecondIndex);
            }
            TStringConstants.truffleSafePointPoll(node, ++loopCount);
        }
        return -1;
    }
}
