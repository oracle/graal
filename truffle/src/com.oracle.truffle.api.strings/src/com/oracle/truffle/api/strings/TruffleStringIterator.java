/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.strings.TStringGuards.isBuiltin;
import static com.oracle.truffle.api.strings.TStringGuards.isDefaultVariant;
import static com.oracle.truffle.api.strings.TStringGuards.isReturnNegative;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
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
    final Object arrayA;
    final byte codeRangeA;
    final Encoding encoding;
    final TruffleString.ErrorHandling errorHandling;
    private int rawIndex;

    TruffleStringIterator(AbstractTruffleString a, Object arrayA, int codeRangeA, Encoding encoding, TruffleString.ErrorHandling errorHandling, int rawIndex) {
        assert TSCodeRange.isCodeRange(codeRangeA);
        this.a = a;
        this.arrayA = arrayA;
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

    private int applyErrorHandler(DecodingErrorHandler errorHandler, int startIndex, boolean forward) {
        CompilerAsserts.partialEvaluationConstant(errorHandler);
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

        final int execute(Node node, TruffleStringIterator it) {
            return execute(node, it, DecodingErrorHandler.DEFAULT);
        }

        final int execute(Node node, TruffleStringIterator it, DecodingErrorHandler errorHandler) {
            if (!it.hasNext()) {
                throw InternalErrors.illegalState("end of string has been reached already");
            }
            CompilerAsserts.partialEvaluationConstant(errorHandler);
            return executeInternal(node, it, errorHandler);
        }

        abstract int executeInternal(Node node, TruffleStringIterator it, DecodingErrorHandler errorHandler);

        @Specialization(guards = {"isUTF32(it.encoding) || isFixedWidth(it.codeRangeA)", "isDefaultVariant(errorHandler)"})
        static int fixed(Node node, TruffleStringIterator it, @SuppressWarnings("unused") DecodingErrorHandler errorHandler,
                        @Shared("readRaw") @Cached TStringOpsNodes.RawReadValueNode readNode) {
            return readAndInc(node, it, readNode);
        }

        @Specialization(guards = {"isUpToValidFixedWidth(it.codeRangeA)"})
        static int fixedValid(Node node, TruffleStringIterator it, @SuppressWarnings("unused") DecodingErrorHandler errorHandler,
                        @Shared("readRaw") @Cached TStringOpsNodes.RawReadValueNode readNode) {
            return readAndInc(node, it, readNode);
        }

        @Specialization(guards = {"isAscii(it.encoding)", "isBroken(it.codeRangeA)", "!isDefaultVariant(errorHandler)"})
        static int brokenAscii(Node node, TruffleStringIterator it, DecodingErrorHandler errorHandler,
                        @Shared("readRaw") @Cached TStringOpsNodes.RawReadValueNode readNode) {
            int codepoint = readAndInc(node, it, readNode);
            if (codepoint < 0x80) {
                return codepoint;
            } else {
                return it.applyErrorHandler(errorHandler, it.rawIndex - 1);
            }
        }

        @Specialization(guards = {"isUTF32(it.encoding)", "isBroken(it.codeRangeA)", "!isDefaultVariant(errorHandler)"})
        static int brokenUTF32(Node node, TruffleStringIterator it, @SuppressWarnings("unused") DecodingErrorHandler errorHandler,
                        @Shared("readRaw") @Cached TStringOpsNodes.RawReadValueNode readNode) {
            int codepoint = readAndInc(node, it, readNode);
            if (Encodings.isValidUnicodeCodepoint(codepoint)) {
                return codepoint;
            } else {
                return it.applyErrorHandler(errorHandler, it.rawIndex - 1);
            }
        }

        @SuppressWarnings("fallthrough")
        @Specialization(guards = {"isUTF8(it.encoding)", "isValid(it.codeRangeA)"})
        static int utf8Valid(TruffleStringIterator it, @SuppressWarnings("unused") DecodingErrorHandler errorHandler) {
            int b = it.readAndIncS0();
            if (b < 0x80) {
                return b;
            }
            int nBytes = Integer.numberOfLeadingZeros(~(b << 24));
            int codepoint = b & (0xff >>> nBytes);
            assert 1 < nBytes && nBytes < 5 : nBytes;
            assert it.rawIndex + nBytes - 1 <= it.a.length();
            // Checkstyle: stop
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
            // Checkstyle: resume
            return codepoint;
        }

        @Specialization(guards = {"isUTF8(it.encoding)", "isBroken(it.codeRangeA)"})
        static int utf8Broken(TruffleStringIterator it, DecodingErrorHandler errorHandler) {
            int startIndex = it.rawIndex;
            int b = it.readAndIncS0();
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

        @Specialization(guards = {"isUTF16(it.encoding)", "isValid(it.codeRangeA)"})
        static int utf16Valid(TruffleStringIterator it, @SuppressWarnings("unused") DecodingErrorHandler errorHandler) {
            char c = (char) it.readAndIncS1();
            if (Encodings.isUTF16HighSurrogate(c)) {
                assert it.hasNext();
                assert Encodings.isUTF16LowSurrogate(it.readFwdS1());
                return Character.toCodePoint(c, (char) it.readAndIncS1());
            }
            return c;
        }

        @Specialization(guards = {"isUTF16(it.encoding)", "isBroken(it.codeRangeA)"})
        static int utf16Broken(TruffleStringIterator it, DecodingErrorHandler errorHandler) {
            char c = (char) it.readAndIncS1();
            if (isReturnNegative(errorHandler) || !isBuiltin(errorHandler)) {
                if (Encodings.isUTF16Surrogate(c)) {
                    if (Encodings.isUTF16HighSurrogate(c) && it.hasNext()) {
                        char c2 = (char) it.readFwdS1();
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
                    char c2 = (char) it.readFwdS1();
                    if (Encodings.isUTF16LowSurrogate(c2)) {
                        it.rawIndex++;
                        return Character.toCodePoint(c, c2);
                    }
                }
            }
            return c;
        }

        @Specialization(guards = {"isUnsupportedEncoding(it.encoding)"})
        static int unsupported(TruffleStringIterator it, DecodingErrorHandler errorHandler) {
            assert it.hasNext();
            byte[] bytes = JCodings.asByteArray(it.arrayA);
            int startIndex = it.rawIndex;
            int p = it.a.byteArrayOffset() + it.rawIndex;
            int end = it.a.byteArrayOffset() + it.a.length();
            JCodings.Encoding jCoding = JCodings.getInstance().get(it.encoding);
            int length = JCodings.getInstance().getCodePointLength(jCoding, bytes, p, end);
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
                codepoint = JCodings.getInstance().readCodePoint(jCoding, bytes, p, end, errorHandler);
            }
            if (length < 1 || !JCodings.getInstance().isValidCodePoint(jCoding, codepoint)) {
                return it.applyErrorHandler(errorHandler, startIndex);
            }
            return codepoint;
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
         */
        public abstract int execute(TruffleStringIterator it);

        @Specialization
        final int doDefault(TruffleStringIterator it,
                        @Cached InternalNextNode nextNode,
                        @Cached InlinedConditionProfile errorHandlerProfile) {
            // make sure the error handler is PE constant
            if (errorHandlerProfile.profile(this, it.errorHandling == TruffleString.ErrorHandling.BEST_EFFORT)) {
                return nextNode.execute(this, it, DecodingErrorHandler.DEFAULT);
            } else {
                return nextNode.execute(this, it, DecodingErrorHandler.RETURN_NEGATIVE);
            }
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
     */
    @TruffleBoundary
    public int nextUncached() {
        return NextNode.getUncached().execute(this);
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
         */
        public abstract int execute(TruffleStringIterator it);

        @Specialization
        final int doDefault(TruffleStringIterator it,
                        @Cached InternalPreviousNode previousNode,
                        @Cached InlinedConditionProfile errorHandlerProfile) {
            // make sure the error handler is PE constant
            if (errorHandlerProfile.profile(this, it.errorHandling == TruffleString.ErrorHandling.BEST_EFFORT)) {
                return previousNode.execute(this, it, DecodingErrorHandler.DEFAULT);
            } else {
                return previousNode.execute(this, it, DecodingErrorHandler.RETURN_NEGATIVE);
            }
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

        public final int execute(Node node, TruffleStringIterator it, DecodingErrorHandler errorHandler) {
            if (!it.hasPrevious()) {
                throw InternalErrors.illegalState("beginning of string has been reached already");
            }
            return executeInternal(node, it, errorHandler);
        }

        abstract int executeInternal(Node node, TruffleStringIterator it, DecodingErrorHandler errorHandler);

        @Specialization(guards = {"isFixedWidth(it.codeRangeA)", "isDefaultVariant(errorHandler)"})
        static int fixed(Node node, TruffleStringIterator it, @SuppressWarnings("unused") DecodingErrorHandler errorHandler,
                        @Shared("readRaw") @Cached TStringOpsNodes.RawReadValueNode readNode) {
            return readAndDec(node, it, readNode);
        }

        @Specialization(guards = {"isUpToValidFixedWidth(it.codeRangeA)", "!isDefaultVariant(errorHandler)"})
        static int fixedValid(Node node, TruffleStringIterator it, @SuppressWarnings("unused") DecodingErrorHandler errorHandler,
                        @Shared("readRaw") @Cached TStringOpsNodes.RawReadValueNode readNode) {
            return readAndDec(node, it, readNode);
        }

        @Specialization(guards = {"isAscii(it.encoding)", "isBroken(it.codeRangeA)", "!isDefaultVariant(errorHandler)"})
        static int brokenAscii(Node node, TruffleStringIterator it, DecodingErrorHandler errorHandler,
                        @Shared("readRaw") @Cached TStringOpsNodes.RawReadValueNode readNode) {
            int codepoint = readAndDec(node, it, readNode);
            if (codepoint < 0x80) {
                return codepoint;
            } else {
                return it.applyErrorHandlerReverse(errorHandler, it.rawIndex + 1);
            }
        }

        @Specialization(guards = {"isUTF32(it.encoding)", "isBroken(it.codeRangeA)", "!isDefaultVariant(errorHandler)"})
        static int brokenUTF32(Node node, TruffleStringIterator it, DecodingErrorHandler errorHandler,
                        @Shared("readRaw") @Cached TStringOpsNodes.RawReadValueNode readNode) {
            int codepoint = readAndDec(node, it, readNode);
            if (Encodings.isValidUnicodeCodepoint(codepoint)) {
                return codepoint;
            } else {
                return it.applyErrorHandlerReverse(errorHandler, it.rawIndex + 1);
            }
        }

        @Specialization(guards = {"isUTF8(it.encoding)", "isValid(it.codeRangeA)"})
        static int utf8Valid(TruffleStringIterator it, @SuppressWarnings("unused") DecodingErrorHandler errorHandler) {
            int b = it.readAndDecS0();
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
        }

        @Specialization(guards = {"isUTF8(it.encoding)", "isBroken(it.codeRangeA)"})
        static int utf8Broken(TruffleStringIterator it, DecodingErrorHandler errorHandler) {
            int startIndex = it.rawIndex;
            int b = it.readAndDecS0();
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

        @Specialization(guards = {"isUTF16(it.encoding)", "isValid(it.codeRangeA)"})
        static int utf16Valid(TruffleStringIterator it, @SuppressWarnings("unused") DecodingErrorHandler errorHandler) {
            char c = (char) it.readAndDecS1();
            if (Encodings.isUTF16LowSurrogate(c)) {
                assert Encodings.isUTF16HighSurrogate((char) it.readBckS1());
                return Character.toCodePoint((char) it.readAndDecS1(), c);
            }
            return c;
        }

        @Specialization(guards = {"isUTF16(it.encoding)", "isBroken(it.codeRangeA)"})
        static int utf16Broken(TruffleStringIterator it, DecodingErrorHandler errorHandler) {
            char c = (char) it.readAndDecS1();
            if (isReturnNegative(errorHandler) || !isBuiltin(errorHandler)) {
                if (Encodings.isUTF16Surrogate(c)) {
                    if (Encodings.isUTF16LowSurrogate(c) && it.hasPrevious()) {
                        char c2 = (char) it.readBckS1();
                        if (Encodings.isUTF16HighSurrogate(c2)) {
                            it.rawIndex--;
                            return Character.toCodePoint(c2, c);
                        }
                    }
                    return it.applyErrorHandlerReverse(errorHandler, it.rawIndex + 1);
                }
            } else {
                if (Encodings.isUTF16LowSurrogate(c) && it.hasPrevious()) {
                    char c2 = (char) it.readBckS1();
                    if (Encodings.isUTF16HighSurrogate(c2)) {
                        it.rawIndex--;
                        return Character.toCodePoint(c2, c);
                    }
                }
            }
            return c;
        }

        @Specialization(guards = {"isUnsupportedEncoding(it.encoding)"})
        static int unsupported(TruffleStringIterator it, DecodingErrorHandler errorHandler) {
            assert it.hasPrevious();
            byte[] bytes = JCodings.asByteArray(it.arrayA);
            int start = it.a.byteArrayOffset();
            int index = it.a.byteArrayOffset() + it.rawIndex;
            int end = it.a.byteArrayOffset() + it.a.length();
            JCodings.Encoding jCoding = JCodings.getInstance().get(it.encoding);
            int prevIndex = JCodings.getInstance().getPreviousCodePointIndex(jCoding, bytes, start, index, end);
            int codepoint = 0;
            if (prevIndex < 0) {
                it.rawIndex--;
            } else {
                assert prevIndex >= it.a.byteArrayOffset();
                assert prevIndex < index;
                it.rawIndex = prevIndex - it.a.byteArrayOffset();
                codepoint = JCodings.getInstance().readCodePoint(jCoding, bytes, prevIndex, end, errorHandler);
            }
            if (prevIndex < 0 || !JCodings.getInstance().isValidCodePoint(jCoding, codepoint)) {
                return it.applyErrorHandlerReverse(errorHandler, index);
            }
            return codepoint;
        }

    }

    /**
     * Shorthand for calling the uncached version of {@link PreviousNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public int previousUncached() {
        return PreviousNode.getUncached().execute(this);
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
        return TStringOps.readS0(a, arrayA, rawIndex);
    }

    private int readFwdS1() {
        assert a.stride() == 1;
        assert hasNext();
        return TStringOps.readS1(a, arrayA, rawIndex);
    }

    private int readBckS1() {
        assert a.stride() == 1;
        assert hasPrevious();
        return TStringOps.readS1(a, arrayA, rawIndex - 1);
    }

    private static int readAndInc(Node node, TruffleStringIterator it, TStringOpsNodes.RawReadValueNode readNode) {
        assert it.hasNext();
        return readNode.execute(node, it.a, it.arrayA, it.rawIndex++);
    }

    private int readAndIncS0() {
        assert a.stride() == 0;
        assert hasNext();
        return TStringOps.readS0(a, arrayA, rawIndex++);
    }

    private int readAndIncS1() {
        assert a.stride() == 1;
        assert hasNext();
        return TStringOps.readS1(a, arrayA, rawIndex++);
    }

    private static int readAndDec(Node node, TruffleStringIterator it, TStringOpsNodes.RawReadValueNode readNode) {
        assert it.hasPrevious();
        return readNode.execute(node, it.a, it.arrayA, --it.rawIndex);
    }

    private int readAndDecS0() {
        assert a.stride() == 0;
        assert hasPrevious();
        return TStringOps.readS0(a, arrayA, --rawIndex);
    }

    private int readAndDecS1() {
        assert a.stride() == 1;
        assert hasPrevious();
        return TStringOps.readS1(a, arrayA, --rawIndex);
    }

    private boolean curIsUtf8ContinuationByte() {
        return Encodings.isUTF8ContinuationByte(readFwdS0());
    }

    static int indexOf(Node location, TruffleStringIterator it, int codepoint, int fromIndex, int toIndex, InternalNextNode nextNode) {
        int aCodepointIndex = 0;
        while (aCodepointIndex < fromIndex && it.hasNext()) {
            nextNode.execute(location, it);
            aCodepointIndex++;
            TStringConstants.truffleSafePointPoll(location, aCodepointIndex);
        }
        if (aCodepointIndex < fromIndex) {
            return -1;
        }
        while (it.hasNext() && aCodepointIndex < toIndex) {
            if (nextNode.execute(location, it) == codepoint) {
                return aCodepointIndex;
            }
            aCodepointIndex++;
            TStringConstants.truffleSafePointPoll(location, aCodepointIndex);
        }
        return -1;
    }

    static int lastIndexOf(Node location, TruffleStringIterator it, int codepoint, int fromIndex, int toIndex, InternalNextNode nextNode) {
        int aCodepointIndex = 0;
        int result = -1;
        // the code point index is based on the beginning of the string, so we have to count
        // from there
        while (aCodepointIndex < fromIndex && it.hasNext()) {
            if (nextNode.execute(location, it) == codepoint) {
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

    static int indexOfString(Node node, TruffleStringIterator aIt, TruffleStringIterator bIt, int fromIndex, int toIndex, InternalNextNode nextNodeA, InternalNextNode nextNodeB) {
        if (!bIt.hasNext()) {
            return fromIndex;
        }
        int aCodepointIndex = 0;
        while (aCodepointIndex < fromIndex && aIt.hasNext()) {
            nextNodeA.execute(node, aIt);
            aCodepointIndex++;
            TStringConstants.truffleSafePointPoll(node, aCodepointIndex);
        }
        if (aCodepointIndex < fromIndex) {
            return -1;
        }
        int bFirst = nextNodeB.execute(node, bIt);
        int bSecondIndex = bIt.getRawIndex();
        while (aIt.hasNext() && aCodepointIndex < toIndex) {
            if (nextNodeA.execute(node, aIt) == bFirst) {
                if (!bIt.hasNext()) {
                    return aCodepointIndex;
                }
                int aCurIndex = aIt.getRawIndex();
                int innerLoopCount = 0;
                while (bIt.hasNext()) {
                    if (!aIt.hasNext()) {
                        return -1;
                    }
                    if (nextNodeA.execute(node, aIt) != nextNodeB.execute(node, bIt)) {
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

    static int byteIndexOfString(Node node, TruffleStringIterator aIt, TruffleStringIterator bIt, int fromByteIndex, int toByteIndex,
                    InternalNextNode nextNodeA,
                    InternalNextNode nextNodeB) {
        if (!bIt.hasNext()) {
            return fromByteIndex;
        }
        aIt.setRawIndex(fromByteIndex);
        int bFirst = nextNodeB.execute(node, bIt);
        int bSecondIndex = bIt.getRawIndex();
        int loopCount = 0;
        while (aIt.hasNext() && aIt.getRawIndex() < toByteIndex) {
            int ret = aIt.getRawIndex();
            if (nextNodeA.execute(node, aIt) == bFirst) {
                if (!bIt.hasNext()) {
                    return ret;
                }
                int aCurIndex = aIt.getRawIndex();
                while (bIt.hasNext()) {
                    if (!aIt.hasNext()) {
                        return -1;
                    }
                    if (nextNodeA.execute(node, aIt) != nextNodeB.execute(node, bIt)) {
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

    static int lastIndexOfString(Node node, TruffleStringIterator aIt, TruffleStringIterator bIt, int fromIndex, int toIndex,
                    InternalNextNode nextNodeA,
                    InternalPreviousNode prevNodeA,
                    InternalPreviousNode prevNodeB) {
        if (!bIt.hasPrevious()) {
            return fromIndex;
        }
        int bFirstCodePoint = prevNodeB.execute(node, bIt, DecodingErrorHandler.DEFAULT);
        int lastMatchIndex = -1;
        int lastMatchByteIndex = -1;
        int aCodepointIndex = 0;
        while (aCodepointIndex < fromIndex && aIt.hasNext()) {
            if (nextNodeA.execute(node, aIt) == bFirstCodePoint) {
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
            if (prevNodeA.execute(node, aIt, DecodingErrorHandler.DEFAULT) == bFirstCodePoint) {
                if (!bIt.hasPrevious()) {
                    return aCodepointIndex;
                }
                int aCurIndex = aIt.getRawIndex();
                int aCurCodePointIndex = aCodepointIndex;
                while (bIt.hasPrevious()) {
                    if (!aIt.hasPrevious()) {
                        return -1;
                    }
                    if (prevNodeA.execute(node, aIt, DecodingErrorHandler.DEFAULT) != prevNodeB.execute(node, bIt, DecodingErrorHandler.DEFAULT)) {
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

    static int lastByteIndexOfString(Node node, TruffleStringIterator aIt, TruffleStringIterator bIt, int fromByteIndex, int toByteIndex,
                    InternalNextNode nextNodeA,
                    InternalPreviousNode prevNodeA,
                    InternalPreviousNode prevNodeB) {
        if (!bIt.hasPrevious()) {
            return fromByteIndex;
        }
        int bFirstCodePoint = prevNodeB.execute(node, bIt, DecodingErrorHandler.DEFAULT);
        int lastMatchByteIndex = -1;
        int loopCount = 0;
        while (aIt.getRawIndex() < fromByteIndex && aIt.hasNext()) {
            if (nextNodeA.execute(node, aIt) == bFirstCodePoint) {
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
            if (prevNodeA.execute(node, aIt, DecodingErrorHandler.DEFAULT) == bFirstCodePoint) {
                if (!bIt.hasPrevious()) {
                    return aIt.getRawIndex();
                }
                int aCurIndex = aIt.getRawIndex();
                while (bIt.hasPrevious()) {
                    if (!aIt.hasPrevious()) {
                        return -1;
                    }
                    if (prevNodeA.execute(node, aIt, DecodingErrorHandler.DEFAULT) != prevNodeB.execute(node, bIt, DecodingErrorHandler.DEFAULT)) {
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
