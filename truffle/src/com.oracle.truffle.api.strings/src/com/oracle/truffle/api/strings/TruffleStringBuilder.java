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

import static com.oracle.truffle.api.strings.AbstractTruffleString.boundsCheckRegionI;
import static com.oracle.truffle.api.strings.TStringGuards.is7Bit;
import static com.oracle.truffle.api.strings.TStringGuards.is7BitCompatible;
import static com.oracle.truffle.api.strings.TStringGuards.isAsciiBytesOrLatin1;
import static com.oracle.truffle.api.strings.TStringGuards.isBroken;
import static com.oracle.truffle.api.strings.TStringGuards.isFixedWidth;
import static com.oracle.truffle.api.strings.TStringGuards.isUnsupportedEncoding;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Exclusive;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.NeverDefault;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;
import com.oracle.truffle.api.profiles.InlinedConditionProfile;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

// @formatter:off
// mx eclipseformat currently doesn't format sealed classes correctly
/**
 * The {@link TruffleString} equivalent to {@link java.lang.StringBuilder}. This builder eagerly
 * fills up a byte array with all strings passed to its {@code Append}-nodes. For lazy string
 * concatenation, use {@link TruffleString.ConcatNode} instead.
 *
 * @since 22.1
 */
public abstract sealed class TruffleStringBuilder permits TruffleStringBuilderGeneric, TruffleStringBuilderUTF8, TruffleStringBuilderUTF16, TruffleStringBuilderUTF32 {
// @formatter:on

    private static final int DEFAULT_INITIAL_CAPACITY = 16;

    byte[] buf;
    int length;
    int codeRange;
    final Encoding encoding;
    int stride;
    int codePointLength;

    TruffleStringBuilder(Encoding encoding, int initialSize, int codeRange) {
        this.buf = new byte[initialSize];
        this.codeRange = codeRange;
        this.encoding = encoding;
    }

    /**
     * Returns true if this string builder is empty.
     *
     * @since 22.1
     */
    public final boolean isEmpty() {
        return length == 0;
    }

    /**
     * Returns this string builder's byte length.
     *
     * @since 22.1
     */
    public final int byteLength() {
        return length << encoding.naturalStride;
    }

    final void updateCodeRange(int newCodeRange) {
        codeRange = TSCodeRange.commonCodeRange(codeRange, newCodeRange);
    }

    final void appendLength(int addLength) {
        appendLength(addLength, addLength);
    }

    final void appendLength(int addLength, int addCodePointLength) {
        length += addLength;
        codePointLength += addCodePointLength;
    }

    /**
     * Create a new string builder with the given encoding.
     * <p>
     * If the encoding is known ahead of time, use {@link #createUTF8()}, {@link #createUTF16()} or
     * {@link #createUTF32()} instead.
     *
     * @since 22.1
     */
    public static TruffleStringBuilder create(Encoding encoding) {
        return create(encoding, 16);
    }

    /**
     * Create a new string builder with the given encoding, and pre-allocate the given number of
     * bytes.
     * <p>
     * If the encoding is known ahead of time, use {@link #createUTF8(int)},
     * {@link #createUTF16(int)} or {@link #createUTF32(int)} instead.
     *
     * @since 22.1
     */
    public static TruffleStringBuilder create(Encoding encoding, int initialCapacity) {
        if (encoding == Encoding.UTF_8) {
            return createUTF8(initialCapacity);
        } else if (encoding == Encoding.UTF_16) {
            return createUTF16(initialCapacity);
        } else if (encoding == Encoding.UTF_32) {
            return createUTF32(initialCapacity);
        }
        return createGeneric(encoding, initialCapacity);
    }

    /**
     * Create a new UTF-8 string builder.
     *
     * @since 23.1
     */
    public static TruffleStringBuilderUTF8 createUTF8() {
        return createUTF8(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Create a new UTF-8 string builder and pre-allocate the given number of bytes.
     *
     * @since 23.1
     */
    public static TruffleStringBuilderUTF8 createUTF8(int initialCapacity) {
        return new TruffleStringBuilderUTF8(initialCapacity);
    }

    /**
     * Create a new UTF-16 string builder.
     *
     * @since 23.1
     */
    public static TruffleStringBuilderUTF16 createUTF16() {
        return createUTF16(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Create a new UTF-16 string builder and pre-allocate the given number of chars.
     *
     * @since 23.1
     */
    public static TruffleStringBuilderUTF16 createUTF16(int initialCapacity) {
        return new TruffleStringBuilderUTF16(initialCapacity);
    }

    /**
     * Create a new UTF-32 string builder.
     *
     * @since 23.1
     */
    public static TruffleStringBuilderUTF32 createUTF32() {
        return createUTF32(DEFAULT_INITIAL_CAPACITY);
    }

    /**
     * Create a new UTF-32 string builder and pre-allocate the given number of codepoints.
     *
     * @since 23.1
     */
    public static TruffleStringBuilderUTF32 createUTF32(int initialCapacity) {
        return new TruffleStringBuilderUTF32(initialCapacity);
    }

    /**
     * Create a new generic string builder, which can be used for non-UTF encodings.
     *
     * @param encoding must not be {@link Encoding#UTF_8}, {@link Encoding#UTF_16} or
     *            {@link Encoding#UTF_32}.
     */
    static TruffleStringBuilderGeneric createGeneric(Encoding encoding, int initialCapacity) {
        if (encoding == Encoding.UTF_8 || encoding == Encoding.UTF_16 || encoding == Encoding.UTF_32) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw InternalErrors.illegalArgument("use createUTF* methods for UTF encodings!");
        }
        return new TruffleStringBuilderGeneric(encoding, initialCapacity);
    }

    /**
     * Node to append a single byte to a string builder.
     *
     * @since 22.1
     */
    public abstract static class AppendByteNode extends AbstractPublicNode {

        AppendByteNode() {
        }

        /**
         * Append a single byte to the string builder. Does not support UTF-16 and UTF-32; use
         * {@link AppendCharUTF16Node} and {@link AppendCodePointNode} instead.
         *
         * @since 22.1
         */
        public abstract void execute(TruffleStringBuilder sb, byte value);

        @Specialization
        final void append(TruffleStringBuilderUTF8 sb, byte value,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            appendByte(sb, value, bufferGrowProfile, errorProfile, TSCodeRange.markImprecise(TSCodeRange.getBrokenMultiByte()));
        }

        @Specialization
        final void append(TruffleStringBuilderGeneric sb, byte value,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            appendByte(sb, value, bufferGrowProfile, errorProfile, TSCodeRange.asciiLatinBytesNonAsciiCodeRange(sb.encoding));
        }

        private void appendByte(TruffleStringBuilder sb, byte value, InlinedBranchProfile bufferGrowProfile, InlinedBranchProfile errorProfile, int nonAsciiCodeRange) {
            sb.ensureCapacityS0(this, 1, bufferGrowProfile, errorProfile);
            sb.buf[sb.length++] = value;
            if (value < 0) {
                sb.codeRange = nonAsciiCodeRange;
            }
            sb.codePointLength++;
        }

        /**
         * Create a new {@link AppendByteNode}.
         *
         * @since 22.1
         */
        @NeverDefault
        public static AppendByteNode create() {
            return TruffleStringBuilderFactory.AppendByteNodeGen.create();
        }

        /**
         * Get the uncached version of {@link AppendByteNode}.
         *
         * @since 22.1
         */
        public static AppendByteNode getUncached() {
            return TruffleStringBuilderFactory.AppendByteNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link AppendByteNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final void appendByteUncached(byte value) {
        AppendByteNode.getUncached().execute(this, value);
    }

    /**
     * Node to append a single char to a string builder. For UTF-16 only.
     *
     * @since 22.1
     */
    public abstract static class AppendCharUTF16Node extends AbstractPublicNode {

        AppendCharUTF16Node() {
        }

        /**
         * Append a single char to the string builder. For UTF-16 only.
         *
         * @since 22.1
         */
        public abstract void execute(TruffleStringBuilder sb, char value);

        @Specialization
        void append(TruffleStringBuilderUTF16 sb, char value,
                        @Cached InlinedBranchProfile nonAsciiProfile,
                        @Cached InlinedBranchProfile inflateProfile,
                        @Cached InlinedBranchProfile bufferGrowProfile,
                        @Cached InlinedBranchProfile errorProfile) {
            if ((sb.stride | (value >>> 7)) == 0) {
                // fast path: string builder is in byte stride and codepoint is ASCII
                sb.ensureCapacityS0(this, 1, bufferGrowProfile, errorProfile);
                sb.buf[sb.length++] = (byte) value;
            } else {
                nonAsciiProfile.enter(this);
                final int codeRangeA;
                final int strideA;
                if (value <= 0x7f) {
                    codeRangeA = TSCodeRange.get7Bit();
                    strideA = 0;
                } else if (value <= 0xff) {
                    codeRangeA = TSCodeRange.get8Bit();
                    strideA = 0;
                } else if (Encodings.isUTF16Surrogate(value)) {
                    codeRangeA = TSCodeRange.markImprecise(TSCodeRange.getBrokenMultiByte());
                    strideA = 1;
                } else {
                    codeRangeA = TSCodeRange.get16Bit();
                    strideA = 1;
                }
                sb.updateCodeRange(codeRangeA);
                sb.ensureCapacityAndInflate(this, 1, strideA, inflateProfile, bufferGrowProfile, errorProfile);
                TStringOps.writeToByteArray(sb.buf, sb.stride, sb.length++, value);
            }
            sb.codePointLength++;
        }

        /**
         * Create a new {@link AppendCharUTF16Node}.
         *
         * @since 22.1
         */
        @NeverDefault
        public static AppendCharUTF16Node create() {
            return TruffleStringBuilderFactory.AppendCharUTF16NodeGen.create();
        }

        /**
         * Get the uncached version of {@link AppendCharUTF16Node}.
         *
         * @since 22.1
         */
        public static AppendCharUTF16Node getUncached() {
            return TruffleStringBuilderFactory.AppendCharUTF16NodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link AppendCharUTF16Node}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final void appendCharUTF16Uncached(char value) {
        AppendCharUTF16Node.getUncached().execute(this, value);
    }

    /**
     * Node to append a codepoint to a string builder.
     *
     * @since 22.1
     */
    public abstract static class AppendCodePointNode extends AbstractPublicNode {

        AppendCodePointNode() {
        }

        /**
         * Append a codepoint to the string builder.
         *
         * @since 22.1
         */
        public final void execute(TruffleStringBuilder sb, int codepoint) {
            execute(sb, codepoint, 1);
        }

        /**
         * Append a codepoint to the string builder, {@code repeat} times.
         *
         * @since 22.2
         */
        public final void execute(TruffleStringBuilder sb, int codepoint, int repeat) {
            execute(sb, codepoint, repeat, false);
        }

        /**
         * Append a codepoint to the string builder, {@code repeat} times.
         *
         * If {@code allowUTF16Surrogates} is {@code true}, {@link Character#isSurrogate(char)
         * UTF-16 surrogate values} passed as {@code codepoint} will not cause an
         * {@link IllegalArgumentException}, but instead be encoded on a best-effort basis. This
         * option is only supported on {@link Encoding#UTF_16} and {@link Encoding#UTF_32}.
         *
         * @since 22.2
         */
        public abstract void execute(TruffleStringBuilder sb, int codepoint, int repeat, boolean allowUTF16Surrogates);

        @Specialization
        void append(TruffleStringBuilder sb, int codepoint, int repeat, boolean allowUTF16Surrogates,
                        @Cached AppendCodePointIntlNode appendCodePointIntlNode) {
            if (repeat < 1) {
                throw InternalErrors.illegalArgument("number of repetitions must be at least 1");
            }
            appendCodePointIntlNode.execute(this, sb, codepoint, repeat, allowUTF16Surrogates);
        }

        /**
         * Create a new {@link AppendCodePointNode}.
         *
         * @since 22.1
         */
        @NeverDefault
        public static AppendCodePointNode create() {
            return TruffleStringBuilderFactory.AppendCodePointNodeGen.create();
        }

        /**
         * Get the uncached version of {@link AppendCodePointNode}.
         *
         * @since 22.1
         */
        public static AppendCodePointNode getUncached() {
            return TruffleStringBuilderFactory.AppendCodePointNodeGen.getUncached();
        }
    }

    abstract static class AppendCodePointIntlNode extends AbstractInternalNode {

        abstract void execute(Node node, TruffleStringBuilder sb, int codepoint, int repeat, boolean allowUTF16Surrogates);

        @Specialization
        static void append(Node node, TruffleStringBuilderUTF8 sb, int codepoint, int repeat, boolean allowUTF16Surrogates,
                        @Cached @Shared InlinedBranchProfile multiByteProfile,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            if (Integer.compareUnsigned(codepoint, 0x7f) <= 0) {
                appendAscii(node, sb, (byte) codepoint, repeat, bufferGrowProfile, errorProfile);
            } else {
                multiByteProfile.enter(node);
                if (Integer.compareUnsigned(codepoint, 0x10ffff) > 0 || !allowUTF16Surrogates && Encodings.isUTF16Surrogate(codepoint)) {
                    throw InternalErrors.invalidCodePoint(codepoint);
                } else {
                    sb.updateCodeRange(TSCodeRange.getValidMultiByte());
                    int length = Encodings.utf8EncodedSize(codepoint);
                    try {
                        sb.ensureCapacityS0(node, Math.multiplyExact(repeat, length), bufferGrowProfile, errorProfile);
                    } catch (ArithmeticException e) {
                        errorProfile.enter(node);
                        throw InternalErrors.outOfMemory();
                    }
                    for (int i = 0; i < repeat; i++) {
                        Encodings.utf8Encode(codepoint, sb.buf, sb.length, length);
                        sb.length += length;
                    }
                }
            }
            sb.codePointLength += repeat;
        }

        @Specialization
        static void append(Node node, TruffleStringBuilderUTF16 sb, int codepoint, int repeat, boolean allowUTF16Surrogates,
                        @Cached @Shared InlinedBranchProfile nonAsciiProfile,
                        @Cached @Shared InlinedBranchProfile multiByteProfile,
                        @Cached @Shared InlinedBranchProfile inflateProfile,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            if ((sb.stride | (codepoint >>> 7)) == 0) {
                // fast path: string builder is in byte stride and codepoint is ASCII
                appendAscii(node, sb, (byte) codepoint, repeat, bufferGrowProfile, errorProfile);
            } else {
                if (Integer.compareUnsigned(codepoint, 0xff) <= 0) {
                    if (codepoint > 0x7f) {
                        sb.updateCodeRange(TSCodeRange.get8Bit());
                    }
                    sb.ensureCapacityWithStride(node, repeat, bufferGrowProfile, errorProfile);
                    for (int i = 0; i < repeat; i++) {
                        TStringOps.writeToByteArray(sb.buf, sb.stride, sb.length++, codepoint);
                    }
                } else if (Integer.compareUnsigned(codepoint, 0xffff) <= 0) {
                    nonAsciiProfile.enter(node);
                    final int codeRangeA;
                    if (Encodings.isUTF16Surrogate(codepoint)) {
                        if (!allowUTF16Surrogates) {
                            throw InternalErrors.invalidCodePoint(codepoint);
                        }
                        codeRangeA = TSCodeRange.getBrokenMultiByte();
                    } else {
                        codeRangeA = TSCodeRange.get16Bit();
                    }
                    sb.updateCodeRange(codeRangeA);
                    sb.ensureCapacityAndInflate(node, repeat, 1, inflateProfile, bufferGrowProfile, errorProfile);
                    assert sb.stride == 1;
                    for (int i = 0; i < repeat; i++) {
                        TStringOps.writeToByteArray(sb.buf, 1, sb.length++, codepoint);
                    }
                } else if (Integer.compareUnsigned(codepoint, 0x10ffff) > 0) {
                    throw InternalErrors.invalidCodePoint(codepoint);
                } else {
                    multiByteProfile.enter(node);
                    sb.updateCodeRange(TSCodeRange.getValidMultiByte());
                    try {
                        sb.ensureCapacityAndInflate(node, Math.multiplyExact(repeat, 2), 1, inflateProfile, bufferGrowProfile, errorProfile);
                    } catch (ArithmeticException e) {
                        errorProfile.enter(node);
                        throw InternalErrors.outOfMemory();
                    }
                    for (int i = 0; i < repeat; i++) {
                        Encodings.utf16EncodeSurrogatePair(codepoint, sb.buf, sb.length);
                        sb.length += 2;
                    }
                }
            }
            sb.codePointLength += repeat;
        }

        @Specialization
        static void append(Node node, TruffleStringBuilderUTF32 sb, int codepoint, int repeat, boolean allowUTF16Surrogates,
                        @Cached @Shared InlinedBranchProfile nonAsciiProfile,
                        @Cached @Shared InlinedBranchProfile inflateProfile,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            if ((sb.stride | (codepoint >>> 7)) == 0) {
                // fast path: string builder is in byte stride and codepoint is ASCII
                appendAscii(node, sb, (byte) codepoint, repeat, bufferGrowProfile, errorProfile);
            } else {
                nonAsciiProfile.enter(node);
                final int codeRangeA;
                final int strideA;
                if (Integer.compareUnsigned(codepoint, 0x7f) <= 0) {
                    codeRangeA = TSCodeRange.get7Bit();
                    strideA = 0;
                } else if (Integer.compareUnsigned(codepoint, 0xff) <= 0) {
                    codeRangeA = TSCodeRange.get8Bit();
                    strideA = 0;
                } else if (Encodings.isUTF16Surrogate(codepoint)) {
                    if (!allowUTF16Surrogates) {
                        throw InternalErrors.invalidCodePoint(codepoint);
                    }
                    codeRangeA = TSCodeRange.getBrokenFixedWidth();
                    strideA = 2;
                } else if (Integer.compareUnsigned(codepoint, 0xffff) <= 0) {
                    codeRangeA = TSCodeRange.get16Bit();
                    strideA = 1;
                } else if (Integer.compareUnsigned(codepoint, 0x10ffff) > 0) {
                    throw InternalErrors.invalidCodePoint(codepoint);
                } else {
                    codeRangeA = TSCodeRange.getValidFixedWidth();
                    strideA = 2;
                }
                sb.updateCodeRange(codeRangeA);
                sb.ensureCapacityAndInflate(node, repeat, strideA, inflateProfile, bufferGrowProfile, errorProfile);
                for (int i = 0; i < repeat; i++) {
                    TStringOps.writeToByteArray(sb.buf, sb.stride, sb.length++, codepoint);
                }
            }
        }

        @Specialization
        static void generic(Node node, TruffleStringBuilderGeneric sb, int codepoint, int repeat, @SuppressWarnings("unused") boolean allowUTF16Surrogates,
                        @Cached @Exclusive InlinedConditionProfile supportedProfile,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            if (supportedProfile.profile(node, isAsciiBytesOrLatin1(sb.encoding))) {
                if (codepoint > 0xff) {
                    throw InternalErrors.invalidCodePoint(codepoint);
                }
                sb.ensureCapacityWithStride(node, repeat, bufferGrowProfile, errorProfile);
                if (codepoint > 0x7f) {
                    sb.updateCodeRange(TSCodeRange.asciiLatinBytesNonAsciiCodeRange(sb.encoding));
                }
                Arrays.fill(sb.buf, sb.length, sb.length + repeat, (byte) codepoint);
                sb.length += repeat;
            } else {
                assert isUnsupportedEncoding(sb.encoding);
                if (Integer.compareUnsigned(codepoint, 0x10ffff) > 0) {
                    throw InternalErrors.invalidCodePoint(codepoint);
                }
                JCodings.Encoding jCodingsEnc = JCodings.getInstance().get(sb.encoding);
                int length = JCodings.getInstance().getCodePointLength(jCodingsEnc, codepoint);
                if (!(sb.encoding.is7BitCompatible() && codepoint <= 0x7f)) {
                    sb.updateCodeRange(TSCodeRange.getValid(JCodings.getInstance().isSingleByte(jCodingsEnc)));
                }
                if (length < 1) {
                    throw InternalErrors.invalidCodePoint(codepoint);
                }
                sb.ensureCapacityWithStride(node, length * repeat, bufferGrowProfile, errorProfile);
                for (int i = 0; i < repeat; i++) {
                    int ret = JCodings.getInstance().writeCodePoint(jCodingsEnc, codepoint, sb.buf, sb.length);
                    if (ret != length || JCodings.getInstance().getCodePointLength(jCodingsEnc, sb.buf, sb.length, sb.length + length) != ret ||
                                    JCodings.getInstance().readCodePoint(jCodingsEnc, sb.buf, sb.length, sb.length + length, DecodingErrorHandler.RETURN_NEGATIVE) != codepoint) {
                        throw InternalErrors.invalidCodePoint(codepoint);
                    }
                    sb.length += length;
                }
            }
            sb.codePointLength += repeat;
        }

        private static void appendAscii(Node node, TruffleStringBuilder sb, byte codepoint, int repeat, InlinedBranchProfile bufferGrowProfile, InlinedBranchProfile errorProfile) {
            sb.ensureCapacityS0(node, repeat, bufferGrowProfile, errorProfile);
            for (int i = 0; i < repeat; i++) {
                sb.buf[sb.length++] = codepoint;
            }
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link AppendCodePointNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final void appendCodePointUncached(int codepoint) {
        AppendCodePointNode.getUncached().execute(this, codepoint);
    }

    /**
     * Shorthand for calling the uncached version of {@link AppendCodePointNode}.
     *
     * @since 22.2
     */
    @TruffleBoundary
    public final void appendCodePointUncached(int codepoint, int repeat) {
        AppendCodePointNode.getUncached().execute(this, codepoint, repeat);
    }

    /**
     * Shorthand for calling the uncached version of {@link AppendCodePointNode}.
     *
     * @since 22.2
     */
    @TruffleBoundary
    public final void appendCodePointUncached(int codepoint, int repeat, boolean allowUTF16Surrogates) {
        AppendCodePointNode.getUncached().execute(this, codepoint, repeat, allowUTF16Surrogates);
    }

    /**
     * Node to append an integer to a string builder. See
     * {@link #execute(TruffleStringBuilder, int)} for details.
     *
     * @since 22.1
     */
    public abstract static class AppendIntNumberNode extends AbstractPublicNode {

        AppendIntNumberNode() {
        }

        /**
         * Append the base-10 string equivalent of a given integer to the string builder. For
         * ASCII-compatible encodings only.
         *
         * @since 22.1
         */
        public abstract void execute(TruffleStringBuilder sb, int value);

        @Specialization
        void doAppend(TruffleStringBuilderUTF8 sb, int value,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            int len = NumberConversion.stringLengthInt(value);
            sb.ensureCapacityS0(this, len, bufferGrowProfile, errorProfile);
            if (len == 1) {
                sb.buf[sb.length] = (byte) ('0' + value);
            } else {
                NumberConversion.writeIntToBytes(this, value, sb.buf, 0, sb.length, len);
            }
            sb.length += len;
            sb.codePointLength += len;
        }

        @Specialization
        void doAppend(TruffleStringBuilderUTF16 sb, int value,
                        @Cached @Shared InlinedConditionProfile stride0Profile,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            int len = NumberConversion.stringLengthInt(value);
            sb.ensureCapacityWithStride(this, len, bufferGrowProfile, errorProfile);
            if (sb.stride == 0 && len == 1) {
                sb.buf[sb.length] = (byte) ('0' + value);
            } else {
                if (stride0Profile.profile(this, sb.stride == 0)) {
                    NumberConversion.writeIntToBytes(this, value, sb.buf, 0, sb.length, len);
                } else {
                    NumberConversion.writeIntToBytes(this, value, sb.buf, 1, sb.length, len);
                }
            }
            sb.length += len;
            sb.codePointLength += len;
        }

        @Specialization
        void doAppend(TruffleStringBuilderUTF32 sb, int value,
                        @Cached @Shared InlinedConditionProfile stride0Profile,
                        @Cached @Exclusive InlinedConditionProfile stride1Profile,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            int len = NumberConversion.stringLengthInt(value);
            sb.ensureCapacityWithStride(this, len, bufferGrowProfile, errorProfile);
            if (sb.stride == 0 && len == 1) {
                sb.buf[sb.length] = (byte) ('0' + value);
            } else {
                if (stride0Profile.profile(this, sb.stride == 0)) {
                    NumberConversion.writeIntToBytes(this, value, sb.buf, 0, sb.length, len);
                } else if (stride1Profile.profile(this, sb.stride == 1)) {
                    NumberConversion.writeIntToBytes(this, value, sb.buf, 1, sb.length, len);
                } else {
                    NumberConversion.writeIntToBytes(this, value, sb.buf, 2, sb.length, len);
                }
            }
            sb.length += len;
        }

        @Specialization
        void doAppend(TruffleStringBuilderGeneric sb, int value,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            if (!is7BitCompatible(sb.encoding)) {
                throw InternalErrors.unsupportedOperation("appendIntNumber is supported on ASCII-compatible encodings only");
            }
            int len = NumberConversion.stringLengthInt(value);
            sb.ensureCapacityS0(this, len, bufferGrowProfile, errorProfile);
            NumberConversion.writeIntToBytes(this, value, sb.buf, 0, sb.length, len);
            sb.appendLength(len);
        }

        /**
         * Create a new {@link AppendIntNumberNode}.
         *
         * @since 22.1
         */
        @NeverDefault
        public static AppendIntNumberNode create() {
            return TruffleStringBuilderFactory.AppendIntNumberNodeGen.create();
        }

        /**
         * Get the uncached version of {@link AppendIntNumberNode}.
         *
         * @since 22.1
         */
        public static AppendIntNumberNode getUncached() {
            return TruffleStringBuilderFactory.AppendIntNumberNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link AppendIntNumberNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final void appendIntNumberUncached(int value) {
        AppendIntNumberNode.getUncached().execute(this, value);
    }

    /**
     * Node to append a {@code long} value to a string builder. See
     * {@link #execute(TruffleStringBuilder, long)} for details.
     *
     * @since 22.1
     */
    public abstract static class AppendLongNumberNode extends AbstractPublicNode {

        AppendLongNumberNode() {
        }

        /**
         * Append the base-10 string equivalent of a given long value to the string builder. For
         * ASCII-compatible encodings only.
         *
         * @since 22.1
         */
        public abstract void execute(TruffleStringBuilder sb, long value);

        @Specialization
        void doAppend(TruffleStringBuilderUTF8 sb, long value,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            int len = NumberConversion.stringLengthLong(value);
            sb.ensureCapacityS0(this, len, bufferGrowProfile, errorProfile);
            if (len == 1) {
                sb.buf[sb.length] = (byte) ('0' + value);
            } else {
                NumberConversion.writeLongToBytes(this, value, sb.buf, 0, sb.length, len);
            }
            sb.length += len;
            sb.codePointLength += len;
        }

        @Specialization
        void doAppend(TruffleStringBuilderUTF16 sb, long value,
                        @Cached @Shared InlinedConditionProfile stride0Profile,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            int len = NumberConversion.stringLengthLong(value);
            sb.ensureCapacityWithStride(this, len, bufferGrowProfile, errorProfile);
            if (sb.stride == 0 && len == 1) {
                sb.buf[sb.length] = (byte) ('0' + value);
            } else {
                if (stride0Profile.profile(this, sb.stride == 0)) {
                    NumberConversion.writeLongToBytes(this, value, sb.buf, 0, sb.length, len);
                } else {
                    NumberConversion.writeLongToBytes(this, value, sb.buf, 1, sb.length, len);
                }
            }
            sb.length += len;
            sb.codePointLength += len;
        }

        @Specialization
        void doAppend(TruffleStringBuilderUTF32 sb, long value,
                        @Cached @Shared InlinedConditionProfile stride0Profile,
                        @Cached @Exclusive InlinedConditionProfile stride1Profile,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            int len = NumberConversion.stringLengthLong(value);
            sb.ensureCapacityWithStride(this, len, bufferGrowProfile, errorProfile);
            if (sb.stride == 0 && len == 1) {
                sb.buf[sb.length] = (byte) ('0' + value);
            } else {
                if (stride0Profile.profile(this, sb.stride == 0)) {
                    NumberConversion.writeLongToBytes(this, value, sb.buf, 0, sb.length, len);
                } else if (stride1Profile.profile(this, sb.stride == 1)) {
                    NumberConversion.writeLongToBytes(this, value, sb.buf, 1, sb.length, len);
                } else {
                    NumberConversion.writeLongToBytes(this, value, sb.buf, 2, sb.length, len);
                }
            }
            sb.length += len;
        }

        @Specialization
        void doAppend(TruffleStringBuilderGeneric sb, long value,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            if (!is7BitCompatible(sb.encoding)) {
                throw InternalErrors.unsupportedOperation("appendIntNumber is supported on ASCII-compatible encodings only");
            }
            int len = NumberConversion.stringLengthLong(value);
            sb.ensureCapacityS0(this, len, bufferGrowProfile, errorProfile);
            NumberConversion.writeLongToBytes(this, value, sb.buf, 0, sb.length, len);
            sb.appendLength(len);
        }

        /**
         * Create a new {@link AppendLongNumberNode}.
         *
         * @since 22.1
         */
        @NeverDefault
        public static AppendLongNumberNode create() {
            return TruffleStringBuilderFactory.AppendLongNumberNodeGen.create();
        }

        /**
         * Get the uncached version of {@link AppendLongNumberNode}.
         *
         * @since 22.1
         */
        public static AppendLongNumberNode getUncached() {
            return TruffleStringBuilderFactory.AppendLongNumberNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link AppendLongNumberNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final void appendLongNumberUncached(long value) {
        AppendLongNumberNode.getUncached().execute(this, value);
    }

    /**
     * Node to append a given {@link TruffleString} to a string builder.
     *
     * @since 22.1
     */
    public abstract static class AppendStringNode extends AbstractPublicNode {

        AppendStringNode() {
        }

        /**
         * Append a given {@link TruffleString} to the string builder.
         *
         * @since 22.1
         */
        public abstract void execute(TruffleStringBuilder sb, AbstractTruffleString a);

        @Specialization
        void append(TruffleStringBuilder sb, AbstractTruffleString a,
                        @Cached AppendStringIntlNode intlNode) {
            intlNode.execute(this, sb, a);
        }

        /**
         * Create a new {@link AppendStringNode}.
         *
         * @since 22.1
         */
        @NeverDefault
        public static AppendStringNode create() {
            return TruffleStringBuilderFactory.AppendStringNodeGen.create();
        }

        /**
         * Get the uncached version of {@link AppendStringNode}.
         *
         * @since 22.1
         */
        public static AppendStringNode getUncached() {
            return TruffleStringBuilderFactory.AppendStringNodeGen.getUncached();
        }
    }

    abstract static class AppendStringIntlNode extends AbstractInternalNode {

        public abstract void execute(Node node, TruffleStringBuilder sb, AbstractTruffleString a);

        @Specialization
        static void append(Node node, TruffleStringBuilderUTF8 sb, AbstractTruffleString a,
                        @Cached @Shared TruffleString.ToIndexableNode toIndexableNode,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            if (a.length() == 0) {
                return;
            }
            a.checkEncoding(Encoding.UTF_8);
            Object arrayA = toIndexableNode.execute(node, a, a.data());
            sb.updateCodeRange(a.codeRange());
            sb.ensureCapacityS0(node, a.length(), bufferGrowProfile, errorProfile);
            TStringOps.arraycopyWithStride(node,
                            arrayA, a.offset(), 0, 0,
                            sb.buf, 0, 0, sb.length, a.length());
            sb.codePointLength += a.codePointLength();
            sb.length += a.length();
        }

        @Specialization
        static void append(Node node, TruffleStringBuilderUTF16 sb, AbstractTruffleString a,
                        @Cached @Shared TruffleString.ToIndexableNode toIndexableNode,
                        @Cached @Exclusive TStringInternalNodes.GetPreciseCodeRangeNode getPreciseCodeRangeNode,
                        @Cached @Exclusive TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached @Shared InlinedBranchProfile slowPathProfile,
                        @Cached @Shared InlinedBranchProfile inflateProfile,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            if (a.length() == 0) {
                return;
            }
            a.checkEncoding(Encoding.UTF_16);
            Object arrayA = toIndexableNode.execute(node, a, a.data());
            if ((a.stride() | sb.stride) == 0) {
                sb.updateCodeRange(a.codeRange());
                sb.ensureCapacityS0(node, a.length(), bufferGrowProfile, errorProfile);
                TStringOps.arraycopyWithStride(node,
                                arrayA, a.offset(), 0, 0,
                                sb.buf, 0, 0, sb.length, a.length());
                sb.codePointLength += a.length();
            } else {
                slowPathProfile.enter(node);
                int codeRangeA = getPreciseCodeRangeNode.execute(node, a, Encoding.UTF_16);
                sb.codePointLength += getCodePointLengthNode.execute(node, a, Encoding.UTF_16);
                sb.updateCodeRange(codeRangeA);
                sb.ensureCapacityAndInflate(node, a.length(), Stride.fromCodeRangeUTF16(codeRangeA), inflateProfile, bufferGrowProfile, errorProfile);
                TStringOps.arraycopyWithStride(node,
                                arrayA, a.offset(), a.stride(), 0,
                                sb.buf, 0, sb.stride, sb.length, a.length());
            }
            sb.length += a.length();
        }

        @Specialization
        static void append(Node node, TruffleStringBuilderUTF32 sb, AbstractTruffleString a,
                        @Cached @Shared TruffleString.ToIndexableNode toIndexableNode,
                        @Cached @Exclusive TStringInternalNodes.GetPreciseCodeRangeNode getPreciseCodeRangeNode,
                        @Cached @Shared InlinedBranchProfile slowPathProfile,
                        @Cached @Shared InlinedBranchProfile inflateProfile,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            if (a.length() == 0) {
                return;
            }
            a.checkEncoding(Encoding.UTF_32);
            Object arrayA = toIndexableNode.execute(node, a, a.data());
            if ((a.stride() | sb.stride) == 0) {
                sb.updateCodeRange(a.codeRange());
                sb.ensureCapacityS0(node, a.length(), bufferGrowProfile, errorProfile);
                TStringOps.arraycopyWithStride(node,
                                arrayA, a.offset(), 0, 0,
                                sb.buf, 0, 0, sb.length, a.length());
            } else {
                slowPathProfile.enter(node);
                int codeRangeA = getPreciseCodeRangeNode.execute(node, a, Encoding.UTF_32);
                sb.updateCodeRange(codeRangeA);
                sb.ensureCapacityAndInflate(node, a.length(), Stride.fromCodeRangeUTF32(codeRangeA), inflateProfile, bufferGrowProfile, errorProfile);
                TStringOps.arraycopyWithStride(node,
                                arrayA, a.offset(), a.stride(), 0,
                                sb.buf, 0, sb.stride, sb.length, a.length());
            }
            sb.length += a.length();
        }

        @Specialization
        static void append(Node node, TruffleStringBuilderGeneric sb, AbstractTruffleString a,
                        @Cached @Shared TruffleString.ToIndexableNode toIndexableNode,
                        @Cached @Exclusive TStringInternalNodes.GetPreciseCodeRangeNode getPreciseCodeRangeNode,
                        @Cached @Exclusive TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            if (a.length() == 0) {
                return;
            }
            a.checkEncoding(sb.encoding);
            Object arrayA = toIndexableNode.execute(node, a, a.data());
            int codeRangeA = getPreciseCodeRangeNode.execute(node, a, sb.encoding);
            sb.updateCodeRange(codeRangeA);
            sb.ensureCapacityS0(node, a.length(), bufferGrowProfile, errorProfile);
            TStringOps.arraycopyWithStride(node,
                            arrayA, a.offset(), 0, 0,
                            sb.buf, 0, 0, sb.length, a.length());
            sb.appendLength(a.length(), getCodePointLengthNode.execute(node, a, sb.encoding));
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link AppendStringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final void appendStringUncached(TruffleString a) {
        AppendStringNode.getUncached().execute(this, a);
    }

    /**
     * Node to append a substring of a given {@link TruffleString} to a string builder. See
     * {@link #execute(TruffleStringBuilder, AbstractTruffleString, int, int)} for details.
     *
     * @since 22.1
     */
    public abstract static class AppendSubstringByteIndexNode extends AbstractPublicNode {

        AppendSubstringByteIndexNode() {
        }

        /**
         * Append a substring of a given {@link TruffleString}, starting at byte index
         * {@code fromByteIndex} and ending at byte index {@code fromByteIndex + byteLength}, to the
         * string builder.
         *
         * @since 22.1
         */
        public abstract void execute(TruffleStringBuilder sb, AbstractTruffleString a, int fromByteIndex, int byteLength);

        @Specialization
        final void append(TruffleStringBuilderUTF8 sb, AbstractTruffleString a, int fromIndex, int length,
                        @Cached @Shared TruffleString.ToIndexableNode toIndexableNode,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            if (length == 0) {
                return;
            }
            a.checkEncoding(Encoding.UTF_8);
            a.boundsCheckRegionRaw(fromIndex, length);
            Object arrayA = toIndexableNode.execute(this, a, a.data());
            if (!is7Bit(a.codeRange())) {
                sb.updateCodeRange(TSCodeRange.markImprecise(a.codeRange()));
            }
            sb.ensureCapacityS0(this, length, bufferGrowProfile, errorProfile);
            TStringOps.arraycopyWithStride(this,
                            arrayA, a.offset(), 0, fromIndex,
                            sb.buf, 0, 0, sb.length, length);
            sb.codePointLength += length;
            sb.length += length;
        }

        @Specialization
        final void append(TruffleStringBuilderUTF16 sb, AbstractTruffleString a, int fromByteIndex, int byteLength,
                        @Cached @Shared TruffleString.ToIndexableNode toIndexableNode,
                        @Cached @Shared InlinedBranchProfile slowPathProfile,
                        @Cached @Shared InlinedBranchProfile inflateProfile,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            if (byteLength == 0) {
                return;
            }
            a.checkEncoding(Encoding.UTF_16);
            final int fromIndex = TruffleString.rawIndexUTF16(fromByteIndex);
            final int length = TruffleString.rawIndexUTF16(byteLength);
            a.boundsCheckRegionRaw(fromIndex, length);
            Object arrayA = toIndexableNode.execute(this, a, a.data());

            if ((a.stride() | sb.stride) == 0) {
                sb.updateCodeRange(TSCodeRange.markImprecise(a.codeRange()));
                sb.ensureCapacityS0(this, length, bufferGrowProfile, errorProfile);
                TStringOps.arraycopyWithStride(this,
                                arrayA, a.offset(), 0, fromIndex,
                                sb.buf, 0, 0, sb.length, length);
                sb.codePointLength += length;
            } else {
                slowPathProfile.enter(this);
                final int codeRangeA = a.codeRange();
                final int codePointLength;
                final int codeRange;
                if (a.stride() == 0) {
                    codeRange = TSCodeRange.markImprecise(codeRangeA);
                    codePointLength = length;
                } else if (fromIndex == 0 && length == a.length()) {
                    codeRange = codeRangeA;
                    codePointLength = a.codePointLength();
                } else if (TSCodeRange.is16Bit(codeRangeA)) {
                    assert a.stride() == 1;
                    codeRange = TStringOps.calcStringAttributesBMP(this, arrayA, a.offset() + fromByteIndex, length);
                    codePointLength = length;
                } else if (TSCodeRange.isValidMultiByte(codeRangeA)) {
                    long attrs = TStringOps.calcStringAttributesUTF16(this, arrayA, a.offset() + fromByteIndex, length, true);
                    codeRange = StringAttributes.getCodeRange(attrs);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                } else {
                    long attrs = TStringOps.calcStringAttributesUTF16(this, arrayA, a.offset() + fromByteIndex, length, false);
                    codeRange = StringAttributes.getCodeRange(attrs);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                }
                sb.updateCodeRange(codeRange);
                sb.ensureCapacityAndInflate(this, a.length(), Stride.fromCodeRangeUTF16AllowImprecise(codeRangeA), inflateProfile, bufferGrowProfile, errorProfile);
                TStringOps.arraycopyWithStride(this,
                                arrayA, a.offset(), a.stride(), fromIndex,
                                sb.buf, 0, sb.stride, sb.length, length);
                sb.codePointLength += codePointLength;
            }
            sb.length += length;
        }

        @Specialization
        final void append(TruffleStringBuilderUTF32 sb, AbstractTruffleString a, int fromByteIndex, int byteLength,
                        @Cached @Shared TruffleString.ToIndexableNode toIndexableNode,
                        @Cached @Shared InlinedBranchProfile slowPathProfile,
                        @Cached @Shared InlinedBranchProfile inflateProfile,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            if (byteLength == 0) {
                return;
            }
            a.checkEncoding(Encoding.UTF_32);
            final int fromIndex = TruffleString.rawIndexUTF32(fromByteIndex);
            final int length = TruffleString.rawIndexUTF32(byteLength);
            a.boundsCheckRegionRaw(fromIndex, length);
            Object arrayA = toIndexableNode.execute(this, a, a.data());
            if ((a.stride() | sb.stride) == 0) {
                sb.updateCodeRange(TSCodeRange.markImprecise(a.codeRange()));
                sb.ensureCapacityS0(this, length, bufferGrowProfile, errorProfile);
                TStringOps.arraycopyWithStride(this,
                                arrayA, a.offset(), 0, fromIndex,
                                sb.buf, 0, 0, sb.length, length);
            } else {
                slowPathProfile.enter(this);
                final int codeRangeA = a.codeRange();
                final int codeRange;
                if (a.stride() == 0 || fromIndex == 0 && length == a.length() || !TSCodeRange.isMoreGeneralThan(codeRangeA, sb.codeRange)) {
                    codeRange = TSCodeRange.markImprecise(codeRangeA);
                } else if (a.stride() == 1) {
                    codeRange = TStringOps.calcStringAttributesBMP(this, arrayA, a.offset() + (fromIndex << 1), length);
                } else {
                    assert a.stride() == 2;
                    codeRange = TStringOps.calcStringAttributesUTF32(this, arrayA, a.offset() + fromByteIndex, length);
                }
                sb.updateCodeRange(codeRange);
                sb.ensureCapacityAndInflate(this, a.length(), Stride.fromCodeRangeUTF32AllowImprecise(codeRangeA), inflateProfile, bufferGrowProfile, errorProfile);
                TStringOps.arraycopyWithStride(this,
                                arrayA, a.offset(), a.stride(), fromIndex,
                                sb.buf, 0, sb.stride, sb.length, length);
            }
            sb.length += length;
        }

        @Specialization
        static void append(TruffleStringBuilderGeneric sb, AbstractTruffleString a, int fromIndex, int length,
                        @Bind("this") Node node,
                        @Cached @Shared TruffleString.ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetPreciseCodeRangeNode getPreciseCodeRangeNode,
                        @Cached TStringInternalNodes.CalcStringAttributesNode calcAttributesNode,
                        @Cached InlinedConditionProfile calcAttrsProfile,
                        @Cached @Shared InlinedBranchProfile bufferGrowProfile,
                        @Cached @Shared InlinedBranchProfile errorProfile) {
            if (length == 0) {
                return;
            }
            a.checkEncoding(sb.encoding);
            a.boundsCheckRegionRaw(fromIndex, length);
            Object arrayA = toIndexableNode.execute(node, a, a.data());
            final int codeRangeA = getPreciseCodeRangeNode.execute(node, a, sb.encoding);
            final int codeRange;
            final int codePointLength;
            if (fromIndex == 0 && length == a.length()) {
                codeRange = codeRangeA;
                codePointLength = getCodePointLengthNode.execute(node, a, sb.encoding);
            } else if (isFixedWidth(codeRangeA) && !TSCodeRange.isMoreGeneralThan(codeRangeA, sb.codeRange)) {
                codeRange = codeRangeA;
                codePointLength = length;
            } else if (calcAttrsProfile.profile(node, !isBroken(sb.codeRange))) {
                long attrs = calcAttributesNode.execute(node, a, arrayA, a.offset(), length, a.stride(), sb.encoding, fromIndex, codeRangeA);
                codeRange = StringAttributes.getCodeRange(attrs);
                codePointLength = StringAttributes.getCodePointLength(attrs);
            } else {
                codeRange = TSCodeRange.getUnknownCodeRangeForEncoding(sb.encoding.id);
                codePointLength = 0;
            }
            sb.updateCodeRange(codeRange);
            sb.ensureCapacityS0(node, length, bufferGrowProfile, errorProfile);
            TStringOps.arraycopyWithStride(node,
                            arrayA, a.offset(), 0, fromIndex,
                            sb.buf, 0, 0, sb.length, length);

            sb.appendLength(length, codePointLength);
        }

        /**
         * Create a new {@link AppendSubstringByteIndexNode}.
         *
         * @since 22.1
         */
        @NeverDefault
        public static AppendSubstringByteIndexNode create() {
            return TruffleStringBuilderFactory.AppendSubstringByteIndexNodeGen.create();
        }

        /**
         * Get the uncached version of {@link AppendSubstringByteIndexNode}.
         *
         * @since 22.1
         */
        public static AppendSubstringByteIndexNode getUncached() {
            return TruffleStringBuilderFactory.AppendSubstringByteIndexNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link AppendSubstringByteIndexNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final void appendSubstringByteIndexUncached(TruffleString a, int fromByteIndex, int byteLength) {
        AppendSubstringByteIndexNode.getUncached().execute(this, a, fromByteIndex, byteLength);
    }

    /**
     * Node to append a substring of a given {@link java.lang.String} to a string builder. See
     * {@link #execute(TruffleStringBuilder, String, int, int)} for details.
     *
     * @since 22.1
     */
    public abstract static class AppendJavaStringUTF16Node extends AbstractPublicNode {

        AppendJavaStringUTF16Node() {
        }

        /**
         * Append a substring of a given {@link java.lang.String} to the string builder. For UTF-16
         * only.
         *
         * @since 22.1
         */
        public final void execute(TruffleStringBuilder sb, String a) {
            execute(sb, a, 0, a.length());
        }

        /**
         * Append a substring of a given {@link java.lang.String}, starting at char index
         * {@code fromIndex} and ending at {@code fromIndex + length}, to the string builder. For
         * UTF-16 only.
         *
         * @since 22.1
         */
        public abstract void execute(TruffleStringBuilder sb, String a, int fromCharIndex, int charLength);

        @Specialization
        final void append(TruffleStringBuilderUTF16 sb, String javaString, int fromIndex, int lengthStr,
                        @Cached InlinedBranchProfile slowPathProfile,
                        @Cached InlinedBranchProfile inflateProfile,
                        @Cached InlinedBranchProfile bufferGrowProfile,
                        @Cached InlinedBranchProfile errorProfile) {
            if (lengthStr == 0) {
                return;
            }
            boundsCheckRegionI(fromIndex, lengthStr, javaString.length());
            final byte[] arrayStr = TStringUnsafe.getJavaStringArray(javaString);
            final int strideStr = TStringUnsafe.getJavaStringStride(javaString);
            final int offsetStr = fromIndex << strideStr;

            if ((strideStr | sb.stride) == 0) {
                sb.updateCodeRange(TSCodeRange.markImprecise(TSCodeRange.get8Bit()));
                sb.ensureCapacityS0(this, lengthStr, bufferGrowProfile, errorProfile);
                TStringOps.arraycopyWithStride(this,
                                arrayStr, offsetStr, 0, 0,
                                sb.buf, 0, 0, sb.length, lengthStr);
                sb.codePointLength += lengthStr;
            } else {
                slowPathProfile.enter(this);
                final int codePointLength;
                final int codeRange;
                if (strideStr == 0) {
                    codeRange = TSCodeRange.markImprecise(TSCodeRange.get8Bit());
                    codePointLength = lengthStr;
                } else {
                    long attrs = TStringOps.calcStringAttributesUTF16(this, arrayStr, offsetStr, lengthStr, false);
                    codeRange = StringAttributes.getCodeRange(attrs);
                    codePointLength = StringAttributes.getCodePointLength(attrs);
                }
                sb.updateCodeRange(codeRange);
                sb.ensureCapacityAndInflate(this, lengthStr, Stride.fromCodeRangeUTF16AllowImprecise(codeRange), inflateProfile, bufferGrowProfile, errorProfile);
                TStringOps.arraycopyWithStride(this,
                                arrayStr, offsetStr, strideStr, 0,
                                sb.buf, 0, sb.stride, sb.length, lengthStr);
                sb.codePointLength += codePointLength;
            }
            sb.length += lengthStr;
        }

        /**
         * Create a new {@link AppendJavaStringUTF16Node}.
         *
         * @since 22.1
         */
        @NeverDefault
        public static AppendJavaStringUTF16Node create() {
            return TruffleStringBuilderFactory.AppendJavaStringUTF16NodeGen.create();
        }

        /**
         * Get the uncached version of {@link AppendJavaStringUTF16Node}.
         *
         * @since 22.1
         */
        public static AppendJavaStringUTF16Node getUncached() {
            return TruffleStringBuilderFactory.AppendJavaStringUTF16NodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link AppendJavaStringUTF16Node}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final void appendJavaStringUTF16Uncached(String a) {
        AppendJavaStringUTF16Node.getUncached().execute(this, a);
    }

    /**
     * Shorthand for calling the uncached version of {@link AppendJavaStringUTF16Node}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final void appendJavaStringUTF16Uncached(String a, int fromCharIndex, int charLength) {
        AppendJavaStringUTF16Node.getUncached().execute(this, a, fromCharIndex, charLength);
    }

    /**
     * Node to materialize a string builder as a {@link TruffleString}.
     *
     * @since 22.1
     */
    public abstract static class ToStringNode extends AbstractPublicNode {

        ToStringNode() {
        }

        /**
         * Materialize this string builder to a {@link TruffleString}.
         *
         * @since 22.1
         */
        public final TruffleString execute(TruffleStringBuilder sb) {
            return execute(sb, false);
        }

        /**
         * Materialize this string builder to a {@link TruffleString}.
         *
         * If {@code lazy} is {@code true}, {@code sb}'s internal storage will be re-used even if
         * there are unused bytes. Since the resulting string will have a reference to {@code sb}'s
         * internal storage, and {@link TruffleString} currently does <i>not</i> resize/trim the
         * substring's internal storage at any point, the {@code lazy} variant effectively creates a
         * memory leak! The caller is responsible for deciding whether this is acceptable or not.
         *
         * @since 22.1
         */
        public abstract TruffleString execute(TruffleStringBuilder sb, boolean lazy);

        @Specialization
        final TruffleString createString(TruffleStringBuilder sb, boolean lazy,
                        @Cached ToStringIntlNode intlNode) {
            return intlNode.execute(this, sb, lazy);
        }

        /**
         * Create a new {@link ToStringNode}.
         *
         * @since 22.1
         */
        @NeverDefault
        public static ToStringNode create() {
            return TruffleStringBuilderFactory.ToStringNodeGen.create();
        }

        /**
         * Get the uncached version of {@link ToStringNode}.
         *
         * @since 22.1
         */
        public static ToStringNode getUncached() {
            return TruffleStringBuilderFactory.ToStringNodeGen.getUncached();
        }
    }

    abstract static class ToStringIntlNode extends AbstractInternalNode {

        public abstract TruffleString execute(Node node, TruffleStringBuilder sb, boolean lazy);

        @Specialization
        static TruffleString createString(Node node, TruffleStringBuilderUTF8 sb, boolean lazy,
                        @Cached @Shared InlinedConditionProfile calcAttributesProfile,
                        @Cached @Exclusive InlinedConditionProfile brokenProfile) {
            if (sb.length == 0) {
                return TruffleString.Encoding.UTF_8.getEmpty();
            }
            final int codeRange;
            final int codePointLength;
            if (calcAttributesProfile.profile(node, !TSCodeRange.isPrecise(sb.codeRange) || TSCodeRange.isBrokenMultiByte(sb.codeRange))) {
                long attrs = TStringOps.calcStringAttributesUTF8(node, sb.buf, 0, sb.length, false, true, brokenProfile);
                codeRange = StringAttributes.getCodeRange(attrs);
                codePointLength = StringAttributes.getCodePointLength(attrs);
            } else {
                codeRange = sb.codeRange;
                codePointLength = sb.codePointLength;
            }
            byte[] bytes = lazy || sb.buf.length == sb.length ? sb.buf : Arrays.copyOf(sb.buf, sb.length);
            return TruffleString.createFromByteArray(bytes, sb.length, 0, TruffleString.Encoding.UTF_8, codePointLength, codeRange);
        }

        @Specialization
        static TruffleString createString(Node node, TruffleStringBuilderUTF16 sb, boolean lazy,
                        @Cached @Shared InlinedConditionProfile calcAttributesProfile) {
            if (sb.length == 0) {
                return TruffleString.Encoding.UTF_16.getEmpty();
            }
            final int codeRange;
            final int codePointLength;
            if (calcAttributesProfile.profile(node, TSCodeRange.isBrokenMultiByte(sb.codeRange))) {
                assert sb.stride == 1;
                long attrs = TStringOps.calcStringAttributesUTF16(node, sb.buf, 0, sb.length, false);
                codeRange = StringAttributes.getCodeRange(attrs);
                codePointLength = StringAttributes.getCodePointLength(attrs);
            } else {
                codeRange = sb.codeRange;
                codePointLength = sb.codePointLength;
            }
            int byteLength = sb.length << sb.stride;
            byte[] bytes = lazy || sb.buf.length == byteLength ? sb.buf : Arrays.copyOf(sb.buf, byteLength);
            return TruffleString.createFromByteArray(bytes, sb.length, sb.stride, TruffleString.Encoding.UTF_16, codePointLength, codeRange);
        }

        @Specialization
        static TruffleString createString(TruffleStringBuilderUTF32 sb, boolean lazy) {
            if (sb.length == 0) {
                return TruffleString.Encoding.UTF_32.getEmpty();
            }
            int byteLength = sb.length << sb.stride;
            byte[] bytes = lazy || sb.buf.length == byteLength ? sb.buf : Arrays.copyOf(sb.buf, byteLength);
            return TruffleString.createFromByteArray(bytes, sb.length, sb.stride, TruffleString.Encoding.UTF_32, sb.length, sb.codeRange);
        }

        @Specialization
        static TruffleString createString(Node node, TruffleStringBuilderGeneric sb, boolean lazy,
                        @Cached @Shared InlinedConditionProfile calcAttributesProfile,
                        @Cached TStringInternalNodes.CalcStringAttributesNode calcAttributesNode) {
            if (sb.length == 0) {
                return sb.encoding.getEmpty();
            }
            final int codeRange;
            final int codePointLength;
            if (calcAttributesProfile.profile(node, !TSCodeRange.isPrecise(sb.codeRange) || TSCodeRange.isBrokenMultiByte(sb.codeRange))) {
                long attrs = calcAttributesNode.execute(node, null, sb.buf, 0, sb.length, 0, sb.encoding, 0, sb.codeRange);
                codeRange = StringAttributes.getCodeRange(attrs);
                codePointLength = StringAttributes.getCodePointLength(attrs);
            } else {
                codeRange = sb.codeRange;
                codePointLength = sb.codePointLength;
            }
            byte[] bytes = lazy || sb.buf.length == sb.length ? sb.buf : Arrays.copyOf(sb.buf, sb.length);
            return TruffleString.createFromByteArray(bytes, sb.length, 0, sb.encoding, codePointLength, codeRange);
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link ToStringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final TruffleString toStringUncached() {
        return ToStringNode.getUncached().execute(this);
    }

    final void ensureCapacityAndInflate(Node node, int appendLength, int appendStride, InlinedBranchProfile inflateProfile, InlinedBranchProfile bufferGrowProfile,
                    InlinedBranchProfile errorProfile) {
        if (appendStride > stride) {
            inflateProfile.enter(node);
            buf = TStringOps.arraycopyOfWithStride(node, buf, 0, length, stride, buf.length >> stride, appendStride);
            stride = appendStride;
        }
        ensureCapacityWithStride(node, appendLength, bufferGrowProfile, errorProfile);
    }

    final void ensureCapacityWithStride(Node node, int appendLength, InlinedBranchProfile bufferGrowProfile, InlinedBranchProfile errorProfile) {
        int minimumCapacity = length + appendLength;
        int oldCapacity = buf.length >> stride;
        if (minimumCapacity - oldCapacity > 0) {
            bufferGrowProfile.enter(node);
            buf = Arrays.copyOf(buf, newCapacityWithStride(node, minimumCapacity, errorProfile));
        }
    }

    final void ensureCapacityS0(Node node, int appendLength, InlinedBranchProfile bufferGrowProfile, InlinedBranchProfile errorProfile) {
        int minimumCapacity = length + appendLength;
        int oldCapacity = buf.length;
        if (minimumCapacity - oldCapacity > 0) {
            bufferGrowProfile.enter(node);
            buf = Arrays.copyOf(buf, newCapacityS0(node, minimumCapacity, errorProfile));
        }
    }

    final int newCapacityS0(Node node, int minCapacity, InlinedBranchProfile errorProfile) {
        int oldLength = buf.length;
        int growth = minCapacity - oldLength;
        int newLength = newLength(node, oldLength, growth, oldLength + 2, errorProfile);
        assert 0 < newLength && newLength <= TStringConstants.MAX_ARRAY_SIZE;
        return newLength;
    }

    final int newCapacityWithStride(Node node, int minCapacity, InlinedBranchProfile errorProfile) {
        int oldBytes = buf.length;
        int minCapacityBytes = minCapacity << stride;
        int growth = minCapacityBytes - oldBytes;
        int newLengthBytes = newLength(node, oldBytes, growth, oldBytes + (2 << stride), errorProfile);
        assert 0 < newLengthBytes && newLengthBytes <= TStringConstants.MAX_ARRAY_SIZE;
        return newLengthBytes;
    }

    private static int newLength(Node node, int oldLength, int minGrowth, int prefGrowth, InlinedBranchProfile errorProfile) {
        int prefLength = oldLength + Math.max(minGrowth, prefGrowth); // might overflow
        if (Integer.compareUnsigned(prefLength, TStringConstants.MAX_ARRAY_SIZE) <= 0) {
            return prefLength;
        } else {
            return hugeLength(node, oldLength, minGrowth, errorProfile);
        }
    }

    private static int hugeLength(Node node, int oldLength, int minGrowth, InlinedBranchProfile errorProfile) {
        int minLength = oldLength + minGrowth;
        if (Integer.compareUnsigned(minLength, TStringConstants.MAX_ARRAY_SIZE) > 0) { // overflow
            errorProfile.enter(node);
            throw InternalErrors.outOfMemory();
        } else {
            return TStringConstants.MAX_ARRAY_SIZE;
        }
    }

    /**
     * Convert the string builder's content to a java string. Do not use this on a fast path.
     *
     * @since 22.1
     */
    @TruffleBoundary
    @Override
    public final String toString() {
        return ToStringNode.getUncached().execute(this).toJavaStringUncached();
    }
}
