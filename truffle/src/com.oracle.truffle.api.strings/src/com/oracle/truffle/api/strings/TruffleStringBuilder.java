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
import static com.oracle.truffle.api.strings.TStringGuards.isBrokenFixedWidth;
import static com.oracle.truffle.api.strings.TStringGuards.isBrokenMultiByteOrUnknown;
import static com.oracle.truffle.api.strings.TStringGuards.isFixedWidth;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16Or32;

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GeneratePackagePrivate;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;

/**
 * The {@link TruffleString} equivalent to {@link java.lang.StringBuilder}. This builder eagerly
 * fills up a byte array with all strings passed to its {@code Append}-nodes. For lazy string
 * concatenation, use {@link TruffleString.ConcatNode} instead.
 *
 * @since 22.1
 */
public final class TruffleStringBuilder {

    private final TruffleString.Encoding encoding;
    private byte[] buf;
    int stride;
    private int length;
    private int codePointLength;
    private int codeRange;

    private TruffleStringBuilder(TruffleString.Encoding encoding) {
        this(encoding, 16);
    }

    private TruffleStringBuilder(TruffleString.Encoding encoding, int initialSize) {
        this.encoding = encoding;
        buf = new byte[initialSize];
        codeRange = is7BitCompatible(encoding) ? TSCodeRange.get7Bit() : TSCodeRange.getUnknown();
    }

    private int bufferLength() {
        return buf.length >> stride;
    }

    /**
     * Returns true if the this string builder is empty.
     *
     * @since 22.1
     */
    public boolean isEmpty() {
        return length == 0;
    }

    /**
     * Returns this string builder's byte length.
     *
     * @since 22.1
     */
    public int byteLength() {
        return length << encoding.naturalStride;
    }

    TruffleString.Encoding getEncoding() {
        return encoding;
    }

    int getStride() {
        return stride;
    }

    int getCodeRange() {
        return codeRange;
    }

    private void updateCodeRange(int newCodeRange) {
        codeRange = TSCodeRange.commonCodeRange(codeRange, newCodeRange);
    }

    private void appendLength(int addLength) {
        appendLength(addLength, addLength);
    }

    private void appendLength(int addLength, int addCodePointLength) {
        length += addLength;
        codePointLength += addCodePointLength;
    }

    /**
     * Create a new string builder with the given encoding.
     *
     * @since 22.1
     */
    public static TruffleStringBuilder create(TruffleString.Encoding encoding) {
        return new TruffleStringBuilder(encoding);
    }

    /**
     * Create a new string builder with the given encoding, and pre-allocate the given number of
     * bytes.
     *
     * @since 22.1
     */
    public static TruffleStringBuilder create(TruffleString.Encoding encoding, int initialCapacity) {
        return new TruffleStringBuilder(encoding, initialCapacity);
    }

    /**
     * Node to append a single byte to a string builder.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class AppendByteNode extends Node {

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
        static void append(TruffleStringBuilder sb, byte value,
                        @Cached ConditionProfile bufferGrowProfile,
                        @Cached ConditionProfile errorProfile) {
            if (errorProfile.profile(isUTF16Or32(sb.encoding))) {
                throw InternalErrors.unsupportedOperation("appendByte is not supported for UTF-16 and UTF-32, use appendChar and appendInt instead");
            }
            sb.ensureCapacityS0(1, bufferGrowProfile, errorProfile);
            sb.buf[sb.length++] = value;
            if (value < 0) {
                sb.codeRange = TSCodeRange.asciiLatinBytesNonAsciiCodeRange(sb.encoding);
            }
            sb.codePointLength++;
        }

        /**
         * Create a new {@link AppendByteNode}.
         *
         * @since 22.1
         */
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
    public void appendByteUncached(byte value) {
        AppendByteNode.getUncached().execute(this, value);
    }

    /**
     * Node to append a single char to a string builder. For UTF-16 only.
     *
     * @since 22.1
     */
    @ImportStatic({TStringGuards.class, TruffleStringBuilder.class})
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class AppendCharUTF16Node extends Node {

        AppendCharUTF16Node() {
        }

        /**
         * Append a single char to the string builder. For UTF-16 only.
         *
         * @since 22.1
         */
        public abstract void execute(TruffleStringBuilder sb, char value);

        @Specialization(guards = {"cachedCurStride == sb.stride", "cachedNewStride == utf16Stride(sb, value)"}, limit = TStringOpsNodes.LIMIT_STRIDE)
        void doCached(TruffleStringBuilder sb, char value,
                        @Cached(value = "sb.stride") int cachedCurStride,
                        @Cached(value = "utf16Stride(sb, value)") int cachedNewStride,
                        @Cached ConditionProfile bufferGrowProfile,
                        @Cached ConditionProfile errorProfile) {
            doAppend(sb, value, cachedCurStride, cachedNewStride, bufferGrowProfile, errorProfile);
        }

        @Specialization(replaces = "doCached")
        void doUncached(TruffleStringBuilder sb, char value,
                        @Cached ConditionProfile bufferGrowProfile,
                        @Cached ConditionProfile errorProfile) {
            doAppend(sb, value, sb.stride, utf16Stride(sb, value), bufferGrowProfile, errorProfile);
        }

        private void doAppend(TruffleStringBuilder sb, char value, int cachedCurStride, int cachedNewStride, ConditionProfile bufferGrowProfile, ConditionProfile errorProfile) {
            if (errorProfile.profile(!isUTF16(sb.encoding))) {
                throw InternalErrors.unsupportedOperation("appendChar is meant for UTF-16 strings only");
            }
            sb.ensureCapacity(this, 1, cachedCurStride, cachedNewStride, bufferGrowProfile, errorProfile);
            sb.updateCodeRange(utf16CodeRange(value));
            TStringOps.writeToByteArray(sb.buf, cachedNewStride, sb.length, value);
            sb.appendLength(1);
        }

        /**
         * Create a new {@link AppendCharUTF16Node}.
         *
         * @since 22.1
         */
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

    static int utf16Stride(TruffleStringBuilder sb, int value) {
        return value <= 0xff ? sb.stride : 1;
    }

    static int utf16CodeRange(int value) {
        if (value <= 0x7f) {
            return TSCodeRange.get7Bit();
        }
        if (value <= 0xff) {
            return TSCodeRange.get8Bit();
        }
        if (Encodings.isUTF16Surrogate(value)) {
            return TSCodeRange.getBrokenMultiByte();
        }
        if (value <= 0xffff) {
            return TSCodeRange.get16Bit();
        }
        return TSCodeRange.getValidMultiByte();
    }

    /**
     * Shorthand for calling the uncached version of {@link AppendCharUTF16Node}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public void appendCharUTF16Uncached(char value) {
        AppendCharUTF16Node.getUncached().execute(this, value);
    }

    static int utf32Stride(TruffleStringBuilder sb, int value) {
        return Math.max(sb.stride, value <= 0xff ? 0 : value <= 0xffff && !Encodings.isUTF16Surrogate(value) ? 1 : 2);
    }

    static int utf32CodeRange(int value) {
        if (value <= 0x7f) {
            return TSCodeRange.get7Bit();
        }
        if (value <= 0xff) {
            return TSCodeRange.get8Bit();
        }
        if (Encodings.isUTF16Surrogate(value)) {
            return TSCodeRange.getBrokenFixedWidth();
        }
        if (value <= 0xffff) {
            return TSCodeRange.get16Bit();
        }
        return TSCodeRange.getValidFixedWidth();
    }

    /**
     * Node to append a codepoint to a string builder.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class AppendCodePointNode extends Node {

        AppendCodePointNode() {
        }

        /**
         * Append a codepoint to the string builder.
         *
         * @since 22.1
         */
        public abstract void execute(TruffleStringBuilder sb, int codepoint);

        @Specialization
        static void append(TruffleStringBuilder sb, int c,
                        @Cached AppendCodePointIntlNode appendCodePointIntlNode,
                        @Cached ConditionProfile bufferGrowProfile,
                        @Cached ConditionProfile errorProfile) {
            if (errorProfile.profile(c < 0 || c > 0x10ffff)) {
                throw InternalErrors.invalidCodePoint(c);
            }
            appendCodePointIntlNode.execute(sb, c, sb.encoding.id, bufferGrowProfile, errorProfile);
            sb.codePointLength++;
        }

        /**
         * Create a new {@link AppendCodePointNode}.
         *
         * @since 22.1
         */
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

    @ImportStatic({TStringGuards.class, TruffleStringBuilder.class})
    @GenerateUncached
    abstract static class AppendCodePointIntlNode extends Node {

        abstract void execute(TruffleStringBuilder sb, int c, int encoding,
                        ConditionProfile bufferGrowProfile,
                        ConditionProfile errorProfile);

        @Specialization(guards = "isAsciiBytesOrLatin1(enc)")
        static void bytes(TruffleStringBuilder sb, int c, @SuppressWarnings("unused") int enc,
                        ConditionProfile bufferGrowProfile,
                        ConditionProfile errorProfile) {
            if (errorProfile.profile(c > 0xff)) {
                throw InternalErrors.invalidCodePoint(c);
            }
            sb.ensureCapacityS0(1, bufferGrowProfile, errorProfile);
            if (c > 0x7f) {
                sb.updateCodeRange(TSCodeRange.asciiLatinBytesNonAsciiCodeRange(sb.encoding));
            }
            sb.buf[sb.length++] = (byte) c;
        }

        @Specialization(guards = "isUTF8(enc)")
        static void utf8(TruffleStringBuilder sb, int c, @SuppressWarnings("unused") int enc,
                        ConditionProfile bufferGrowProfile,
                        ConditionProfile errorProfile) {
            if (errorProfile.profile(Encodings.isUTF16Surrogate(c))) {
                throw InternalErrors.invalidCodePoint(c);
            }
            int length = Encodings.utf8EncodedSize(c);
            sb.ensureCapacityS0(length, bufferGrowProfile, errorProfile);
            Encodings.utf8Encode(c, sb.buf, sb.length, length);
            if (Encodings.isUTF16Surrogate(c)) {
                sb.updateCodeRange(TSCodeRange.getBrokenMultiByte());
            } else if (c > 0x7f) {
                sb.updateCodeRange(TSCodeRange.getValidMultiByte());
            }
            sb.length += length;
        }

        @Specialization(guards = {"isUTF16(enc)", "cachedCurStride == sb.stride", "cachedNewStride == utf16Stride(sb, c)"}, limit = TStringOpsNodes.LIMIT_STRIDE)
        void utf16Cached(TruffleStringBuilder sb, int c, @SuppressWarnings("unused") int enc,
                        ConditionProfile bufferGrowProfile,
                        ConditionProfile errorProfile,
                        @Cached(value = "sb.stride") int cachedCurStride,
                        @Cached(value = "utf16Stride(sb, c)") int cachedNewStride,
                        @Cached ConditionProfile bmpProfile) {
            doUTF16(sb, c, bufferGrowProfile, errorProfile, cachedCurStride, cachedNewStride, bmpProfile);
        }

        @Specialization(guards = "isUTF16(enc)", replaces = "utf16Cached")
        void utf16Uncached(TruffleStringBuilder sb, int c, @SuppressWarnings("unused") int enc,
                        ConditionProfile bufferGrowProfile,
                        ConditionProfile errorProfile,
                        @Cached ConditionProfile bmpProfile) {
            doUTF16(sb, c, bufferGrowProfile, errorProfile, sb.stride, utf16Stride(sb, c), bmpProfile);
        }

        private void doUTF16(TruffleStringBuilder sb, int c, ConditionProfile bufferGrowProfile, ConditionProfile errorProfile, int cachedCurStride, int cachedNewStride,
                        ConditionProfile bmpProfile) {
            if (errorProfile.profile(Encodings.isUTF16Surrogate(c))) {
                throw InternalErrors.invalidCodePoint(c);
            }
            int length = c <= 0xffff ? 1 : 2;
            sb.ensureCapacity(this, length, cachedCurStride, cachedNewStride, bufferGrowProfile, errorProfile);
            sb.updateCodeRange(utf16CodeRange(c));
            if (bmpProfile.profile(c <= 0xffff)) {
                TStringOps.writeToByteArray(sb.buf, cachedNewStride, sb.length, c);
            } else {
                Encodings.utf16EncodeSurrogatePair(c, sb.buf, sb.length);
            }
            sb.length += length;
        }

        @Specialization(guards = {"isUTF32(enc)", "cachedCurStride == sb.stride", "cachedNewStride == utf32Stride(sb, c)"}, limit = TStringOpsNodes.LIMIT_STRIDE)
        void utf32Cached(TruffleStringBuilder sb, int c, @SuppressWarnings("unused") int enc,
                        ConditionProfile bufferGrowProfile,
                        ConditionProfile errorProfile,
                        @Cached(value = "sb.stride") int cachedCurStride,
                        @Cached(value = "utf32Stride(sb, c)") int cachedNewStride) {
            doUTF32(sb, c, bufferGrowProfile, errorProfile, cachedCurStride, cachedNewStride);
        }

        @Specialization(guards = "isUTF32(enc)", replaces = "utf32Cached")
        void utf32Uncached(TruffleStringBuilder sb, int c, @SuppressWarnings("unused") int enc,
                        ConditionProfile bufferGrowProfile,
                        ConditionProfile errorProfile) {
            doUTF32(sb, c, bufferGrowProfile, errorProfile, sb.stride, utf32Stride(sb, c));
        }

        void doUTF32(TruffleStringBuilder sb, int c, ConditionProfile bufferGrowProfile, ConditionProfile errorProfile, int cachedCurStride, int cachedNewStride) {
            if (errorProfile.profile(Encodings.isUTF16Surrogate(c))) {
                throw InternalErrors.invalidCodePoint(c);
            }
            sb.ensureCapacity(this, 1, cachedCurStride, cachedNewStride, bufferGrowProfile, errorProfile);
            sb.updateCodeRange(utf32CodeRange(c));
            TStringOps.writeToByteArray(sb.buf, cachedNewStride, sb.length, c);
            sb.length++;
        }

        @Specialization(guards = "isUnsupportedEncoding(enc)")
        static void unsupported(TruffleStringBuilder sb, int c, int enc,
                        ConditionProfile bufferGrowProfile,
                        ConditionProfile errorProfile) {
            JCodings.Encoding jCodingsEnc = JCodings.getInstance().get(enc);
            int length = JCodings.getInstance().getCodePointLength(jCodingsEnc, c);
            if (!(TruffleString.Encoding.is7BitCompatible(enc) && c <= 0x7f)) {
                sb.updateCodeRange(JCodings.getInstance().isSingleByte(jCodingsEnc) ? TSCodeRange.getValidFixedWidth() : TSCodeRange.getValidMultiByte());
            }
            if (errorProfile.profile(length < 1)) {
                throw InternalErrors.invalidCodePoint(c);
            }
            sb.ensureCapacityS0(length, bufferGrowProfile, errorProfile);
            int ret = JCodings.getInstance().writeCodePoint(jCodingsEnc, c, sb.buf, sb.length);
            if (errorProfile.profile(
                            ret != length || JCodings.getInstance().getCodePointLength(jCodingsEnc, sb.buf, sb.length, sb.length + length) != ret ||
                                            JCodings.getInstance().readCodePoint(jCodingsEnc, sb.buf, sb.length, sb.length + length) != c)) {
                throw InternalErrors.invalidCodePoint(c);
            }
            sb.length += length;
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link AppendCodePointNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public void appendCodePointUncached(int codepoint) {
        AppendCodePointNode.getUncached().execute(this, codepoint);
    }

    /**
     * Node to append an integer to a string builder. See
     * {@link #execute(TruffleStringBuilder, int)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class AppendIntNumberNode extends Node {

        AppendIntNumberNode() {
        }

        /**
         * Append the base-10 string equivalent of a given integer to the string builder. For
         * ASCII-compatible encodings only.
         *
         * @since 22.1
         */
        public abstract void execute(TruffleStringBuilder sb, int value);

        @Specialization(guards = "cachedStride == sb.stride")
        void doAppend(TruffleStringBuilder sb, int value,
                        @Cached(value = "sb.stride", allowUncached = true) int cachedStride,
                        @Cached ConditionProfile bufferGrowProfile,
                        @Cached ConditionProfile errorProfile) {
            if (errorProfile.profile(!is7BitCompatible(sb.encoding))) {
                throw InternalErrors.unsupportedOperation("appendIntNumber is supported on ASCII-compatible encodings only");
            }
            int len = NumberConversion.stringLengthInt(value);
            sb.ensureCapacity(this, len, cachedStride, cachedStride, bufferGrowProfile, errorProfile);
            NumberConversion.writeIntToBytes(this, value, sb.buf, cachedStride, sb.length, len);
            sb.appendLength(len);
        }

        /**
         * Create a new {@link AppendIntNumberNode}.
         *
         * @since 22.1
         */
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
    public void appendIntNumberUncached(int value) {
        AppendIntNumberNode.getUncached().execute(this, value);
    }

    /**
     * Node to append a {@code long} value to a string builder. See
     * {@link #execute(TruffleStringBuilder, long)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class AppendLongNumberNode extends Node {

        AppendLongNumberNode() {
        }

        /**
         * Append the base-10 string equivalent of a given long value to the string builder. For
         * ASCII-compatible encodings only.
         *
         * @since 22.1
         */
        public abstract void execute(TruffleStringBuilder sb, long value);

        @Specialization(guards = "cachedStride == sb.stride")
        void doAppend(TruffleStringBuilder sb, long value,
                        @Cached(value = "sb.stride", allowUncached = true) int cachedStride,
                        @Cached ConditionProfile bufferGrowProfile,
                        @Cached ConditionProfile errorProfile) {
            if (errorProfile.profile(!is7BitCompatible(sb.encoding))) {
                throw InternalErrors.unsupportedOperation("appendIntNumber is supported on ASCII-compatible encodings only");
            }
            int len = NumberConversion.stringLengthLong(value);
            sb.ensureCapacity(this, len, cachedStride, cachedStride, bufferGrowProfile, errorProfile);
            NumberConversion.writeLongToBytes(this, value, sb.buf, cachedStride, sb.length, len);
            sb.appendLength(len);
        }

        /**
         * Create a new {@link AppendLongNumberNode}.
         *
         * @since 22.1
         */
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
    public void appendLongNumberUncached(long value) {
        AppendLongNumberNode.getUncached().execute(this, value);
    }

    /**
     * Node to append a given {@link TruffleString} to a string builder.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class AppendStringNode extends Node {

        AppendStringNode() {
        }

        /**
         * Append a given {@link TruffleString} to the string builder.
         *
         * @since 22.1
         */
        public abstract void execute(TruffleStringBuilder sb, AbstractTruffleString a);

        @Specialization
        static void append(TruffleStringBuilder sb, AbstractTruffleString a,
                        @Cached TruffleString.ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached AppendArrayIntlNode appendArrayIntlNode) {
            if (a.length() == 0) {
                return;
            }
            a.checkEncoding(sb.encoding);
            Object arrayA = toIndexableNode.execute(a, a.data());
            int codeRangeA = getCodeRangeNode.execute(a);
            sb.updateCodeRange(codeRangeA);
            int newStride = Math.max(sb.stride, Stride.fromCodeRange(codeRangeA, sb.encoding.id));
            appendArrayIntlNode.execute(sb, arrayA, a.offset(), a.length(), a.stride(), newStride);
            sb.appendLength(a.length(), getCodePointLengthNode.execute(a));
        }

        /**
         * Create a new {@link AppendStringNode}.
         *
         * @since 22.1
         */
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

    /**
     * Shorthand for calling the uncached version of {@link AppendStringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public void appendStringUncached(TruffleString a) {
        AppendStringNode.getUncached().execute(this, a);
    }

    /**
     * Node to append a substring of a given {@link TruffleString} to a string builder. See
     * {@link #execute(TruffleStringBuilder, AbstractTruffleString, int, int)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class AppendSubstringByteIndexNode extends Node {

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
        static void append(TruffleStringBuilder sb, AbstractTruffleString a, int fromByteIndex, int byteLength,
                        @Cached TruffleString.ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached AppendArrayIntlNode appendArrayIntlNode,
                        @Cached TStringInternalNodes.CalcStringAttributesNode calcAttributesNode,
                        @Cached ConditionProfile calcAttrsProfile) {
            if (byteLength == 0) {
                return;
            }
            a.checkEncoding(sb.encoding);
            final int fromIndex = TruffleString.rawIndex(fromByteIndex, sb.encoding);
            final int length = TruffleString.rawIndex(byteLength, sb.encoding);
            a.boundsCheckRegionRaw(fromIndex, length);
            Object arrayA = toIndexableNode.execute(a, a.data());
            final int codeRangeA = getCodeRangeNode.execute(a);
            final int codeRange;
            final int codePointLength;
            if (fromIndex == 0 && length == a.length()) {
                codeRange = codeRangeA;
                codePointLength = getCodePointLengthNode.execute(a);
            } else if (isFixedWidth(codeRangeA) && !TSCodeRange.isMoreGeneralThan(codeRangeA, sb.codeRange)) {
                codeRange = codeRangeA;
                codePointLength = length;
            } else if (calcAttrsProfile.profile(!(isBrokenMultiByteOrUnknown(sb.codeRange) || isBrokenFixedWidth(sb.codeRange)))) {
                long attrs = calcAttributesNode.execute(a, arrayA, a.offset() + (fromIndex << a.stride()), length, a.stride(), sb.encoding.id, codeRangeA);
                codeRange = StringAttributes.getCodeRange(attrs);
                codePointLength = StringAttributes.getCodePointLength(attrs);
            } else {
                codeRange = TSCodeRange.getUnknown();
                codePointLength = 0;
            }
            sb.updateCodeRange(codeRange);
            appendArrayIntlNode.execute(sb, arrayA, a.offset() + (fromIndex << a.stride()), length, a.stride(), Stride.fromCodeRange(sb.codeRange, sb.encoding.id));
            sb.appendLength(length, codePointLength);
        }

        /**
         * Create a new {@link AppendSubstringByteIndexNode}.
         *
         * @since 22.1
         */
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
    public void appendSubstringByteIndexUncached(TruffleString a, int fromByteIndex, int byteLength) {
        AppendSubstringByteIndexNode.getUncached().execute(this, a, fromByteIndex, byteLength);
    }

    /**
     * Node to append a substring of a given {@link java.lang.String} to a string builder. See
     * {@link #execute(TruffleStringBuilder, String, int, int)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class AppendJavaStringUTF16Node extends Node {

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
        void append(TruffleStringBuilder sb, String javaString, int fromIndex, int lengthStr,
                        @Cached AppendArrayIntlNode appendArrayIntlNode,
                        @Cached AppendCharArrayIntlNode appendCharArrayIntlNode,
                        @Cached ConditionProfile stride0Profile,
                        @Cached ConditionProfile errorProfile) {
            if (errorProfile.profile(!isUTF16(sb))) {
                throw InternalErrors.unsupportedOperation("appendJavaString is supported on UTF-16 only, use appendString for other encodings");
            }
            if (lengthStr == 0) {
                return;
            }
            boundsCheckRegionI(fromIndex, lengthStr, javaString.length());
            final int appendCodePointLength;
            if (TStringUnsafe.JAVA_SPEC <= 8) {
                if (errorProfile.profile(javaString.length() > TStringConstants.MAX_ARRAY_SIZE_S1)) {
                    throw InternalErrors.outOfMemory();
                }
                final char[] arrayStr = TStringUnsafe.getJavaStringArrayJDK8(javaString);
                final int offsetStr = fromIndex << 1;
                if (!isBrokenMultiByteOrUnknown(sb.codeRange)) {
                    final long attrs = TStringOps.calcStringAttributesUTF16C(this, arrayStr, offsetStr, lengthStr);
                    sb.updateCodeRange(StringAttributes.getCodeRange(attrs));
                    appendCodePointLength = StringAttributes.getCodePointLength(attrs);
                } else {
                    appendCodePointLength = 0;
                }
                appendCharArrayIntlNode.execute(sb, arrayStr, offsetStr, lengthStr, Stride.fromCodeRangeUTF16(sb.codeRange));
            } else {
                final byte[] arrayStr = TStringUnsafe.getJavaStringArrayJDK9(javaString);
                final int strideStr = TStringUnsafe.getJavaStringStride(javaString);
                final int offsetStr = fromIndex << strideStr;
                if (stride0Profile.profile(strideStr == 0)) {
                    if (is7Bit(sb.codeRange)) {
                        sb.updateCodeRange(TStringOps.calcStringAttributesLatin1(this, arrayStr, offsetStr, lengthStr));
                    }
                    appendCodePointLength = lengthStr;
                } else {
                    if (!isBrokenMultiByteOrUnknown(sb.codeRange)) {
                        long attrs = TStringOps.calcStringAttributesUTF16(this, arrayStr, offsetStr, lengthStr, false);
                        sb.updateCodeRange(StringAttributes.getCodeRange(attrs));
                        appendCodePointLength = StringAttributes.getCodePointLength(attrs);
                    } else {
                        appendCodePointLength = 0;
                    }
                }
                appendArrayIntlNode.execute(sb, arrayStr, offsetStr, lengthStr, strideStr, Stride.fromCodeRangeUTF16(sb.codeRange));
            }
            sb.appendLength(lengthStr, appendCodePointLength);
        }

        /**
         * Create a new {@link AppendJavaStringUTF16Node}.
         *
         * @since 22.1
         */
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
    public void appendJavaStringUTF16Uncached(String a) {
        AppendJavaStringUTF16Node.getUncached().execute(this, a);
    }

    /**
     * Shorthand for calling the uncached version of {@link AppendJavaStringUTF16Node}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public void appendJavaStringUTF16Uncached(String a, int fromCharIndex, int charLength) {
        AppendJavaStringUTF16Node.getUncached().execute(this, a, fromCharIndex, charLength);
    }

    /**
     * Node to materialize a string builder as a {@link TruffleString}.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ToStringNode extends Node {

        ToStringNode() {
        }

        /**
         * Materialize this string builder to a {@link TruffleString}.
         *
         * @since 22.1
         */
        public abstract TruffleString execute(TruffleStringBuilder sb);

        @Specialization
        static TruffleString createString(TruffleStringBuilder sb,
                        @Cached TStringInternalNodes.CalcStringAttributesNode calcAttributesNode) {
            if (sb.length == 0) {
                return sb.encoding.getEmpty();
            }
            final int codeRange;
            final int codePointLength;
            if (isBrokenMultiByteOrUnknown(sb.codeRange)) {
                long attrs = calcAttributesNode.execute(null, sb.buf, 0, sb.length, sb.stride, sb.encoding.id, TSCodeRange.getUnknown());
                codeRange = StringAttributes.getCodeRange(attrs);
                codePointLength = StringAttributes.getCodePointLength(attrs);
            } else {
                codeRange = sb.codeRange;
                codePointLength = sb.codePointLength;
            }
            return TruffleString.createFromByteArray(Arrays.copyOf(sb.buf, sb.length << sb.stride), sb.length, sb.stride, sb.encoding.id, codePointLength, codeRange);
        }

        /**
         * Create a new {@link ToStringNode}.
         *
         * @since 22.1
         */
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

    /**
     * Shorthand for calling the uncached version of {@link ToStringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public TruffleString toStringUncached() {
        return ToStringNode.getUncached().execute(this);
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class AppendArrayIntlNode extends Node {

        abstract void execute(TruffleStringBuilder sb, Object array, int offsetA, int lengthA, int strideA, int strideNew);

        @Specialization(guards = {"sb.stride == cachedStrideSB", "strideA == cachedStrideA", "strideNew == cachedStrideNew"}, limit = TStringOpsNodes.LIMIT_STRIDE)
        void doCached(TruffleStringBuilder sb, Object array, int offsetA, int lengthA, @SuppressWarnings("unused") int strideA, @SuppressWarnings("unused") int strideNew,
                        @Cached(value = "sb.stride") int cachedStrideSB,
                        @Cached(value = "strideA") int cachedStrideA,
                        @Cached(value = "strideNew") int cachedStrideNew,
                        @Cached ConditionProfile bufferGrowProfile,
                        @Cached ConditionProfile errorProfile) {
            doAppend(this, sb, array, offsetA, lengthA, cachedStrideSB, cachedStrideA, cachedStrideNew, bufferGrowProfile, errorProfile);
        }

        @Specialization(replaces = "doCached")
        void doUncached(TruffleStringBuilder sb, Object array, int offsetA, int lengthA, int strideA, int strideNew,
                        @Cached ConditionProfile bufferGrowProfile,
                        @Cached ConditionProfile errorProfile) {
            doAppend(this, sb, array, offsetA, lengthA, sb.stride, strideA, strideNew, bufferGrowProfile, errorProfile);
        }

        private static void doAppend(Node location, TruffleStringBuilder sb, Object array, int offsetA, int lengthA, int cachedStrideSB, int cachedStrideA, int cachedStrideNew,
                        ConditionProfile bufferGrowProfile,
                        ConditionProfile errorProfile) {
            sb.ensureCapacity(location, lengthA, cachedStrideSB, cachedStrideNew, bufferGrowProfile, errorProfile);
            assert sb.stride == cachedStrideNew;
            TStringOps.arraycopyWithStride(location,
                            array, offsetA, cachedStrideA, 0,
                            sb.buf, 0, cachedStrideNew, sb.length, lengthA);
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class AppendCharArrayIntlNode extends Node {

        abstract void execute(TruffleStringBuilder sb, char[] array, int offsetA, int lengthA, int strideNew);

        @Specialization(guards = {"sb.stride == cachedStrideSB", "strideNew == cachedStrideNew"}, limit = TStringOpsNodes.LIMIT_STRIDE)
        void doCached(TruffleStringBuilder sb, char[] array, int offsetA, int lengthA, @SuppressWarnings("unused") int strideNew,
                        @Cached(value = "sb.stride") int cachedStrideSB,
                        @Cached(value = "strideNew") int cachedStrideNew,
                        @Cached ConditionProfile bufferGrowProfile,
                        @Cached ConditionProfile errorProfile) {
            doAppend(this, sb, array, offsetA, lengthA, cachedStrideSB, cachedStrideNew, bufferGrowProfile, errorProfile);
        }

        @Specialization(replaces = "doCached")
        void doUnached(TruffleStringBuilder sb, char[] array, int offsetA, int lengthA, int strideNew,
                        @Cached ConditionProfile bufferGrowProfile,
                        @Cached ConditionProfile errorProfile) {
            doAppend(this, sb, array, offsetA, lengthA, sb.stride, strideNew, bufferGrowProfile, errorProfile);
        }

        private static void doAppend(Node location, TruffleStringBuilder sb, char[] array, int offsetA, int lengthA, int cachedStrideSB, int cachedStrideNew, ConditionProfile bufferGrowProfile,
                        ConditionProfile errorProfile) {
            sb.ensureCapacity(location, lengthA, cachedStrideSB, cachedStrideNew, bufferGrowProfile, errorProfile);
            assert sb.stride == cachedStrideNew;
            TStringOps.arraycopyWithStrideCB(location, array, offsetA, sb.buf, sb.length << cachedStrideNew, cachedStrideNew, lengthA);
        }
    }

    void ensureCapacityS0(int appendLength, ConditionProfile bufferGrowProfile, ConditionProfile errorProfile) {
        assert stride == 0;
        final long newLength = (long) length + appendLength;
        if (bufferGrowProfile.profile(newLength > bufferLength())) {
            long newBufferLength = ((long) bufferLength() << 1) + 2;
            assert newLength >= 0;
            final int maxLength = TStringConstants.MAX_ARRAY_SIZE;
            if (errorProfile.profile(newLength > maxLength)) {
                throw InternalErrors.outOfMemory();
            }
            newBufferLength = Math.min(newBufferLength, maxLength);
            newBufferLength = Math.max(newBufferLength, newLength);
            buf = Arrays.copyOf(buf, (int) newBufferLength);
        }
    }

    void ensureCapacity(Node location, int appendLength, int curStride, int newStride, ConditionProfile bufferGrowProfile, ConditionProfile errorProfile) {
        assert curStride == stride;
        assert newStride >= stride;
        final long newLength = (long) length + appendLength;
        if (bufferGrowProfile.profile(curStride != newStride || newLength > bufferLength())) {
            long newBufferLength = newLength > bufferLength() ? ((long) bufferLength() << 1) + 2 : bufferLength();
            assert newLength >= 0;
            final int maxLength = TStringConstants.MAX_ARRAY_SIZE >> newStride;
            if (errorProfile.profile(newLength > maxLength)) {
                throw InternalErrors.outOfMemory();
            }
            newBufferLength = Math.min(newBufferLength, maxLength);
            newBufferLength = Math.max(newBufferLength, newLength);
            buf = TStringOps.arraycopyOfWithStride(location, buf, 0, length, curStride, (int) newBufferLength, newStride);
            stride = newStride;
        }
    }

    /**
     * Convert the string builder's content to a java string. Do not use this on a fast path.
     *
     * @since 22.1
     */
    @TruffleBoundary
    @Override
    public String toString() {
        return ToStringNode.getUncached().execute(this).toJavaStringUncached();
    }
}
