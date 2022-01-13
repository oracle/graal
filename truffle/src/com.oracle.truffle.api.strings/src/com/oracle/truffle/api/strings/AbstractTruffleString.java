/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import static com.oracle.truffle.api.strings.TStringGuards.isBytes;
import static com.oracle.truffle.api.strings.TStringGuards.isSupportedEncoding;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF32;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString.Encoding;

/**
 * Abstract base class for Truffle strings. Useful when a value can be both a {@link TruffleString}
 * or a {@link MutableTruffleString}. Note that values of this type are not valid interop values.
 * They must be converted to {@link TruffleString} before passing them to other languages.
 *
 * @see TruffleString
 * @since 22.1
 */
public abstract class AbstractTruffleString {

    static final boolean DEBUG_STRICT_ENCODING_CHECKS = Boolean.getBoolean("truffle.strings.debug-strict-encoding-checks");

    /**
     * String content. This can be one of the following:
     * <ul>
     * <li>{@code byte[]}</li>
     * <li>{@link LazyLong}</li>
     * <li>{@link LazyConcat}</li>
     * <li>{@link NativePointer}</li>
     * <li>{@link String} (only for caching results of {@link #toJavaStringUncached()})</li>
     * </ul>
     */
    private Object data;
    /**
     * String content offset in bytes. Used for string views / lazy substrings.
     */
    private final int offset;
    /**
     * String length, scaled to the string's {@link TruffleString.Encoding#naturalStride natural
     * stride}, i.e.: if the string encoding is UTF-32, the length is int-based, UTF-16 implies
     * char-based length, and all other encodings use byte-based length. This is useful for string
     * compaction.
     */
    private final int length;
    /**
     * String encoding id, stored as a byte to save space. Resolve with
     * {@link TruffleString.Encoding#get(int)}.
     */
    private final byte encoding;
    /**
     * String content's {@link Stride stride} in log2 format (0 for byte-stride, 1 for char-stride,
     * 2 for int-stride). Used for string compaction in UTF-32 and UTF-16 encodings.
     */
    private final byte stride;
    /**
     * Flags. The only flag defined at the moment is {@link TruffleString#isCacheHead()}.
     */
    private final byte flags;
    /**
     * Cached {@link TruffleString.HashCodeNode hash code}. The hash method never returns zero, so a
     * hashCode value of zero always means that the hash is not calculated yet.
     */
    int hashCode = 0;

    AbstractTruffleString(Object data, int offset, int length, int stride, int encoding, int flags) {
        validateData(data, offset, length, stride);
        assert 0 <= encoding && encoding <= Byte.MAX_VALUE;
        assert isByte(stride);
        assert isByte(encoding);
        assert isByte(flags);
        assert isSupportedEncoding(encoding) || TStringAccessor.ENGINE.requireLanguageWithAllEncodings(Encoding.get(encoding));
        this.data = data;
        this.encoding = (byte) encoding;
        this.offset = offset;
        this.length = length;
        this.stride = (byte) stride;
        this.flags = (byte) flags;
    }

    static boolean isByte(int i) {
        return Byte.MIN_VALUE <= i && i <= Byte.MAX_VALUE;
    }

    private static void validateData(Object data, int offset, int length, int stride) {
        if (data instanceof byte[]) {
            validateDataArray(offset, length, stride, ((byte[]) data).length);
        } else if (data instanceof String) {
            validateDataArray(offset, length, stride, ((String) data).length() << TStringUnsafe.getJavaStringStride((String) data));
        } else if (data instanceof LazyLong || data instanceof LazyConcat) {
            validateDataLazy(offset, length, stride);
        } else if (data instanceof NativePointer) {
            validateDataNative(offset, length, stride);
        } else {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static void validateDataArray(int offset, int length, int stride, int arrayLength) {
        if (!Stride.isStride(stride) || offset < 0 || length < 0 || offset + (((long) length) << stride) > arrayLength) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static void validateDataLazy(int offset, int length, int stride) {
        if (!Stride.isStride(stride) || offset != 0 || length < 0 || (((long) length) << stride) > Integer.MAX_VALUE) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    private static void validateDataNative(int offset, int length, int stride) {
        if (!Stride.isStride(stride) || offset < 0 || length < 0 || (((long) length) << stride) > Integer.MAX_VALUE) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere();
        }
    }

    /**
     * Returns {@code true} if this string is empty. This method is allowed to be used on fast
     * paths.
     *
     * @since 22.1
     */
    public final boolean isEmpty() {
        return length() == 0;
    }

    /**
     * Get this string's length in bytes. This method is allowed to be used on fast paths.
     *
     * @since 22.1
     */
    public final int byteLength(TruffleString.Encoding expectedEncoding) {
        checkEncoding(expectedEncoding);
        return length() << expectedEncoding.naturalStride;
    }

    /**
     * Returns {@code true} if this string is compatible to the given encoding. Compatible for
     * {@link TruffleString} means it is byte-equivalent in both the encoding used to create this
     * string and the given encoding. For {@link MutableTruffleString} this method only returned
     * true if the passed encoding is the same encoding used to create this string.
     *
     * @since 22.1
     */
    public final boolean isCompatibleTo(TruffleString.Encoding expectedEncoding) {
        return isCompatibleTo(expectedEncoding.id, expectedEncoding.maxCompatibleCodeRange);
    }

    /**
     * Returns {@code true} if this string is <i>not</i> backed by a native pointer.
     *
     * @since 22.1
     */
    public final boolean isManaged() {
        return !isNative();
    }

    /**
     * Returns {@code true} if this string is backed by a native pointer.
     *
     * @since 22.1
     */
    public final boolean isNative() {
        return data instanceof NativePointer;
    }

    /**
     * Returns {@code true} if this string is immutable, i.e. an instance of {@link TruffleString}.
     *
     * @since 22.1
     */
    public final boolean isImmutable() {
        return this instanceof TruffleString;
    }

    /**
     * Returns {@code true} if this string is mutable, i.e. an instance of
     * {@link MutableTruffleString}.
     *
     * @since 22.1
     */
    public final boolean isMutable() {
        assert this instanceof TruffleString || this instanceof MutableTruffleString;
        return !isImmutable();
    }

    final boolean isCompatibleTo(int enc, int maxCompatibleCodeRange) {
        return this.encoding() == enc || !DEBUG_STRICT_ENCODING_CHECKS && this instanceof TruffleString && ((TruffleString) this).codeRange() < maxCompatibleCodeRange;
    }

    /**
     * Get this string's backing data. This may be a byte array, a {@link String}, a
     * {@link NativePointer}, a {@link LazyLong}, or a {@link LazyConcat}.
     */
    final Object data() {
        return data;
    }

    final void setData(byte[] array) {
        if (offset() != 0 || length() << stride() != array.length) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw CompilerDirectives.shouldNotReachHere();
        }
        this.data = array;
    }

    /**
     * Get this string's base offset in bytes. This property is used to accommodate string views /
     * lazy strings, and also allow "storage agnostic" access to the string's content. All methods
     * accessing {@link #data()} will shift their indices by this offset.
     */
    final int offset() {
        return offset;
    }

    final int byteArrayOffset() {
        assert data instanceof byte[] || data instanceof NativePointer || data instanceof LazyLong;
        assert !(data instanceof NativePointer) || offset() == ((NativePointer) data).offset();
        return data instanceof NativePointer ? 0 : offset();
    }

    /**
     * Get this string's length in raw values (bytes/characters/integers...), where the actual
     * bit-width of one value is defined by {@link #stride()}. This means that a UTF-8 string will
     * report its length in 8-bit values, but a UTF-16 string may be reporting its length in 8-bit
     * or 16-bit values, depending on whether the string was compressed to 8-bit values or not.
     * Consequently, this behavior makes string compression transparent to the caller: a UTF-32
     * string of codepoint-length of four will always report a "raw" length of four as well,
     * regardless of whether those four codepoints are actually stored in 32-bit, 16-bit, or 8-bit
     * array slots.
     * <p>
     * The length reported by this method may not reflect the underlying array's length, if this
     * string is a string view / lazy substring.
     */
    final int length() {
        return length;
    }

    /**
     * Get the index representing a string encoding as defined in {@link Encodings}.
     */
    final int encoding() {
        return encoding;
    }

    /**
     * Get this string's stride as defined in {@link Stride}.
     */
    final int stride() {
        return stride;
    }

    /**
     * Get this string's flags byte.
     */
    final int flags() {
        return flags;
    }

    final int getHashCodeUnsafe() {
        assert isHashCodeCalculated();
        return hashCode;
    }

    final boolean isHashCodeCalculated() {
        return hashCode != 0;
    }

    // don't use this on fast path
    final boolean isMaterialized() {
        return data instanceof byte[] || isLazyLong() && ((AbstractTruffleString.LazyLong) data).bytes != null;
    }

    final boolean isLazyConcat() {
        return data instanceof AbstractTruffleString.LazyConcat;
    }

    final boolean isLazyLong() {
        return data instanceof AbstractTruffleString.LazyLong;
    }

    final boolean isJavaString() {
        return data instanceof String;
    }

    static TruffleStringIterator forwardIterator(AbstractTruffleString a, Object arrayA, int codeRangeA) {
        return new TruffleStringIterator(a, arrayA, codeRangeA, 0);
    }

    static TruffleStringIterator backwardIterator(AbstractTruffleString a, Object arrayA, int codeRangeA) {
        return new TruffleStringIterator(a, arrayA, codeRangeA, a.length());
    }

    final void checkEncoding(TruffleString.Encoding expectedEncoding) {
        if (!isCompatibleTo(expectedEncoding)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw InternalErrors.wrongEncoding(expectedEncoding);
        }
    }

    final void looseCheckEncoding(TruffleString.Encoding expectedEncoding, int codeRange) {
        if (!isLooselyCompatibleTo(expectedEncoding.id, expectedEncoding.maxCompatibleCodeRange, codeRange)) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw InternalErrors.wrongEncoding(expectedEncoding);
        }
    }

    boolean isLooselyCompatibleTo(int expectedEncoding, int maxCompatibleCodeRange, int codeRange) {
        return encoding() == expectedEncoding || codeRange < maxCompatibleCodeRange;
    }

    static int rawIndex(int byteIndex, TruffleString.Encoding expectedEncoding) {
        if (isUTF16(expectedEncoding) && (byteIndex & 1) != 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw InternalErrors.illegalArgument("misaligned byte index on UTF-16 string");
        } else if (isUTF32(expectedEncoding) && (byteIndex & 3) != 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw InternalErrors.illegalArgument("misaligned byte index on UTF-32 string");
        }
        return byteIndex >> expectedEncoding.naturalStride;
    }

    static int byteIndex(int rawIndex, TruffleString.Encoding expectedEncoding) {
        assert rawIndex < 0 || (rawIndex << expectedEncoding.naturalStride) >= 0;
        assert rawIndex >= 0 || (rawIndex << expectedEncoding.naturalStride) < 0;
        return rawIndex << expectedEncoding.naturalStride;
    }

    final void boundsCheck(int index, TStringInternalNodes.GetCodePointLengthNode codePointLengthNode) {
        boundsCheckI(index, codePointLengthNode.execute(this));
    }

    final void boundsCheck(int fromIndex, int toIndex, TStringInternalNodes.GetCodePointLengthNode codePointLengthNode) {
        boundsCheckI(fromIndex, toIndex, codePointLengthNode.execute(this));
    }

    final void boundsCheckRegion(int fromIndex, int regionLength, TStringInternalNodes.GetCodePointLengthNode codePointLengthNode) {
        boundsCheckRegionI(fromIndex, regionLength, codePointLengthNode.execute(this));
    }

    final void boundsCheckByteIndexS0(int byteIndex) {
        assert stride() == 0;
        boundsCheckI(byteIndex, length());
    }

    final void boundsCheckByteIndexUTF16(int byteIndex) {
        boundsCheckI(byteIndex, length() << TruffleString.Encoding.UTF_16.naturalStride);
    }

    final void boundsCheckByteIndexUTF32(int byteIndex) {
        boundsCheckI(byteIndex, length() << TruffleString.Encoding.UTF_32.naturalStride);
    }

    final void boundsCheckRaw(int index) {
        boundsCheckI(index, length());
    }

    final void boundsCheckRaw(int fromIndex, int toIndex) {
        boundsCheckI(fromIndex, toIndex, length());
    }

    final void boundsCheckRegionRaw(int fromIndex, int regionLength) {
        boundsCheckRegionI(fromIndex, regionLength, length());
    }

    static void boundsCheckI(int index, int arrayLength) {
        if (index < 0 || index >= arrayLength) {
            throw InternalErrors.indexOutOfBounds();
        }
    }

    static void boundsCheckI(int fromIndex, int toIndex, int arrayLength) {
        if (fromIndex < 0 || fromIndex >= arrayLength || toIndex < 0 || toIndex > arrayLength) {
            throw InternalErrors.indexOutOfBounds();
        }
    }

    static void boundsCheckRegionI(int fromIndex, int regionLength, int arrayLength) {
        if (fromIndex < 0 || regionLength < 0 || Integer.toUnsignedLong(fromIndex + regionLength) > arrayLength) {
            throw InternalErrors.indexOutOfBounds();
        }
    }

    static void nullCheck(Object o) {
        if (o == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new NullPointerException("unexpected null pointer");
        }
    }

    static void checkByteLength(int byteLength, TruffleString.Encoding encoding) {
        checkByteLength(byteLength, encoding.id);
    }

    static void checkByteLength(int byteLength, int encoding) {
        if (isUTF16(encoding)) {
            TruffleString.checkByteLengthUTF16(byteLength);
        } else if (isUTF32(encoding)) {
            TruffleString.checkByteLengthUTF32(byteLength);
        }
    }

    static void checkByteLengthUTF16(int byteLength) {
        if ((byteLength & 1) != 0) {
            throw InternalErrors.illegalArgument("UTF-16 string byte length is not a multiple of 2");
        }
    }

    static void checkByteLengthUTF32(int byteLength) {
        if ((byteLength & 3) != 0) {
            throw InternalErrors.illegalArgument("UTF-32 string byte length is not a multiple of 4");
        }
    }

    static void checkArrayRange(byte[] value, int byteOffset, int byteLength) {
        checkArrayRange(value.length, byteOffset, byteLength);
    }

    static void checkArrayRange(int arrayLength, int byteOffset, int byteLength) {
        if (Integer.toUnsignedLong(byteOffset) + Integer.toUnsignedLong(byteLength) > arrayLength) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw InternalErrors.substringOutOfBounds();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.FromByteArrayNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final TruffleString asTruffleStringUncached(TruffleString.Encoding expectedEncoding) {
        return TruffleString.AsTruffleStringNode.getUncached().execute(this, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.AsManagedNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final TruffleString asManagedTruffleStringUncached(TruffleString.Encoding expectedEncoding) {
        return TruffleString.AsManagedNode.getUncached().execute(this, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of
     * {@link MutableTruffleString.AsMutableTruffleStringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final MutableTruffleString asMutableTruffleStringUncached(TruffleString.Encoding expectedEncoding) {
        return MutableTruffleString.AsMutableTruffleStringNode.getUncached().execute(this, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.AsManagedNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final MutableTruffleString asManagedMutableTruffleStringUncached(TruffleString.Encoding expectedEncoding) {
        return MutableTruffleString.AsManagedNode.getUncached().execute(this, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.GetCodeRangeNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final TruffleString.CodeRange getCodeRangeUncached(TruffleString.Encoding expectedEncoding) {
        return TruffleString.GetCodeRangeNode.getUncached().execute(this, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.GetByteCodeRangeNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final TruffleString.CodeRange getByteCodeRangeUncached(TruffleString.Encoding expectedEncoding) {
        return TruffleString.GetByteCodeRangeNode.getUncached().execute(this, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.CodeRangeEqualsNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final boolean codeRangeEqualsUncached(TruffleString.CodeRange otherCodeRange) {
        return TruffleString.CodeRangeEqualsNode.getUncached().execute(this, otherCodeRange);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.IsValidNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final boolean isValidUncached(TruffleString.Encoding expectedEncoding) {
        return TruffleString.IsValidNode.getUncached().execute(this, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.CodePointLengthNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int codePointLengthUncached(TruffleString.Encoding expectedEncoding) {
        return TruffleString.CodePointLengthNode.getUncached().execute(this, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.HashCodeNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int hashCodeUncached(TruffleString.Encoding expectedEncoding) {
        return TruffleString.HashCodeNode.getUncached().execute(this, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.ReadByteNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int readByteUncached(int i, TruffleString.Encoding expectedEncoding) {
        return TruffleString.ReadByteNode.getUncached().execute(this, i, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.ReadCharUTF16Node}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int readCharUTF16Uncached(int i) {
        return TruffleString.ReadCharUTF16Node.getUncached().execute(this, i);
    }

    /**
     * Shorthand for calling the uncached version of
     * {@link TruffleString.CodePointIndexToByteIndexNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int byteLengthOfCodePointUncached(int byteIndex, TruffleString.Encoding expectedEncoding) {
        return TruffleString.ByteLengthOfCodePointNode.getUncached().execute(this, byteIndex, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of
     * {@link TruffleString.CodePointIndexToByteIndexNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int codePointIndexToByteIndexUncached(int byteOffset, int codepointIndex, TruffleString.Encoding expectedEncoding) {
        return TruffleString.CodePointIndexToByteIndexNode.getUncached().execute(this, byteOffset, codepointIndex, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.CodePointAtIndexNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int codePointAtIndexUncached(int i, TruffleString.Encoding expectedEncoding) {
        return TruffleString.CodePointAtIndexNode.getUncached().execute(this, i, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.CodePointAtByteIndexNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int codePointAtByteIndexUncached(int i, TruffleString.Encoding expectedEncoding) {
        return TruffleString.CodePointAtByteIndexNode.getUncached().execute(this, i, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.ByteIndexOfAnyByteNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int byteIndexOfAnyByteUncached(int fromByteIndex, int maxByteIndex, byte[] values, TruffleString.Encoding expectedEncoding) {
        return TruffleString.ByteIndexOfAnyByteNode.getUncached().execute(this, fromByteIndex, maxByteIndex, values, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of
     * {@link TruffleString.CharIndexOfAnyCharUTF16Node}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int charIndexOfAnyCharUTF16Uncached(int fromCharIndex, int maxCharIndex, char[] values) {
        return TruffleString.CharIndexOfAnyCharUTF16Node.getUncached().execute(this, fromCharIndex, maxCharIndex, values);
    }

    /**
     * Shorthand for calling the uncached version of
     * {@link TruffleString.IntIndexOfAnyIntUTF32Node}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int intIndexOfAnyIntUTF32Uncached(int fromIntIndex, int maxIntIndex, int[] values) {
        return TruffleString.IntIndexOfAnyIntUTF32Node.getUncached().execute(this, fromIntIndex, maxIntIndex, values);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.IndexOfCodePointNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int indexOfCodePointUncached(int cp, int fromIndex, int toIndex, TruffleString.Encoding expectedEncoding) {
        return TruffleString.IndexOfCodePointNode.getUncached().execute(this, cp, fromIndex, toIndex, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.ByteIndexOfCodePointNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int byteIndexOfCodePointUncached(int cp, int fromIndex, int toIndex, TruffleString.Encoding expectedEncoding) {
        return TruffleString.ByteIndexOfCodePointNode.getUncached().execute(this, cp, fromIndex, toIndex, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.LastIndexOfCodePointNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int lastIndexOfCodePointUncached(int cp, int fromIndex, int toIndex, TruffleString.Encoding expectedEncoding) {
        return TruffleString.LastIndexOfCodePointNode.getUncached().execute(this, cp, fromIndex, toIndex, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of
     * {@link TruffleString.LastByteIndexOfCodePointNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int lastByteIndexOfCodePointUncached(int cp, int fromIndex, int toIndex, TruffleString.Encoding expectedEncoding) {
        return TruffleString.LastByteIndexOfCodePointNode.getUncached().execute(this, cp, fromIndex, toIndex, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.IndexOfStringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int indexOfStringUncached(AbstractTruffleString b, int fromIndex, int toIndex, TruffleString.Encoding expectedEncoding) {
        return TruffleString.IndexOfStringNode.getUncached().execute(this, b, fromIndex, toIndex, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.ByteIndexOfStringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int byteIndexOfStringUncached(AbstractTruffleString b, int fromIndex, int toIndex, TruffleString.Encoding expectedEncoding) {
        return TruffleString.ByteIndexOfStringNode.getUncached().execute(this, b, fromIndex, toIndex, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.ByteIndexOfStringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int byteIndexOfStringUncached(TruffleString.WithMask b, int fromIndex, int toIndex, TruffleString.Encoding expectedEncoding) {
        return TruffleString.ByteIndexOfStringNode.getUncached().execute(this, b.string, fromIndex, toIndex, b.mask, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.LastIndexOfStringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int lastIndexOfStringUncached(AbstractTruffleString b, int fromIndex, int toIndex, TruffleString.Encoding expectedEncoding) {
        return TruffleString.LastIndexOfStringNode.getUncached().execute(this, b, fromIndex, toIndex, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of
     * {@link TruffleString.LastByteIndexOfStringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int lastByteIndexOfStringUncached(AbstractTruffleString b, int fromIndex, int toIndex, TruffleString.Encoding expectedEncoding) {
        return TruffleString.LastByteIndexOfStringNode.getUncached().execute(this, b, fromIndex, toIndex, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of
     * {@link TruffleString.LastByteIndexOfStringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int lastByteIndexOfStringUncached(TruffleString.WithMask b, int fromIndex, int toIndex, TruffleString.Encoding expectedEncoding) {
        return TruffleString.LastByteIndexOfStringNode.getUncached().execute(this, b, fromIndex, toIndex, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.CompareBytesNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int compareBytesUncached(AbstractTruffleString b, TruffleString.Encoding expectedEncoding) {
        return TruffleString.CompareBytesNode.getUncached().execute(this, b, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.CompareCharsUTF16Node}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int compareCharsUTF16Uncached(AbstractTruffleString b) {
        return TruffleString.CompareCharsUTF16Node.getUncached().execute(this, b);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.CompareIntsUTF32Node}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int compareIntsUTF32Uncached(AbstractTruffleString b) {
        return TruffleString.CompareIntsUTF32Node.getUncached().execute(this, b);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.RegionEqualNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final boolean regionEqualsUncached(int fromIndexA, AbstractTruffleString b, int fromIndexB, int regionLength, TruffleString.Encoding expectedEncoding) {
        return TruffleString.RegionEqualNode.getUncached().execute(this, fromIndexA, b, fromIndexB, regionLength, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.RegionEqualByteIndexNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final boolean regionEqualByteIndexUncached(int fromByteIndexA, AbstractTruffleString b, int fromByteIndexB, int byteLength, TruffleString.Encoding expectedEncoding) {
        return TruffleString.RegionEqualByteIndexNode.getUncached().execute(this, fromByteIndexA, b, fromByteIndexB, byteLength, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.RegionEqualByteIndexNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final boolean regionEqualByteIndexUncached(int fromByteIndexA, TruffleString.WithMask b, int fromByteIndexB, int byteLength, TruffleString.Encoding expectedEncoding) {
        return TruffleString.RegionEqualByteIndexNode.getUncached().execute(this, fromByteIndexA, b, fromByteIndexB, byteLength, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.ConcatNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final TruffleString concatUncached(AbstractTruffleString b, TruffleString.Encoding expectedEncoding, boolean lazy) {
        return TruffleString.ConcatNode.getUncached().execute(this, b, expectedEncoding, lazy);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.RepeatNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final TruffleString repeatUncached(int n, TruffleString.Encoding expectedEncoding) {
        return TruffleString.RepeatNode.getUncached().execute(this, n, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.SubstringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final TruffleString substringUncached(int fromIndex, int substringLength, TruffleString.Encoding expectedEncoding, boolean lazy) {
        return TruffleString.SubstringNode.getUncached().execute(this, fromIndex, substringLength, expectedEncoding, lazy);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.SubstringByteIndexNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final TruffleString substringByteIndexUncached(int fromByteIndex, int byteLength, TruffleString.Encoding expectedEncoding, boolean lazy) {
        return TruffleString.SubstringByteIndexNode.getUncached().execute(this, fromByteIndex, byteLength, expectedEncoding, lazy);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.EqualNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final boolean equalsUncached(AbstractTruffleString b, TruffleString.Encoding expectedEncoding) {
        return TruffleString.EqualNode.getUncached().execute(this, b, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.ParseIntNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int parseIntUncached() throws TruffleString.NumberFormatException {
        return parseIntUncached(10);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.ParseIntNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final int parseIntUncached(int radix) throws TruffleString.NumberFormatException {
        return TruffleString.ParseIntNode.getUncached().execute(this, radix);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.ParseLongNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final long parseLongUncached() throws TruffleString.NumberFormatException {
        return parseLongUncached(10);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.ParseLongNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final long parseLongUncached(int radix) throws TruffleString.NumberFormatException {
        return TruffleString.ParseLongNode.getUncached().execute(this, radix);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.ParseDoubleNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final double parseDoubleUncached() throws TruffleString.NumberFormatException {
        return TruffleString.ParseDoubleNode.getUncached().execute(this);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.GetInternalByteArrayNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final InternalByteArray getInternalByteArrayUncached(TruffleString.Encoding expectedEncoding) {
        return TruffleString.GetInternalByteArrayNode.getUncached().execute(this, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of
     * {@link TruffleString.GetInternalNativePointerNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final Object getInternalNativePointerUncached(TruffleString.Encoding expectedEncoding) {
        return TruffleString.GetInternalNativePointerNode.getUncached().execute(this, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.CopyToByteArrayNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final void copyToByteArrayNodeUncached(int byteFromIndexA, byte[] dst, int byteFromIndexDst, int byteLength, TruffleString.Encoding expectedEncoding) {
        TruffleString.CopyToByteArrayNode.getUncached().execute(this, byteFromIndexA, dst, byteFromIndexDst, byteLength, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.CopyToNativeMemoryNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final void copyToNativeMemoryNodeUncached(int byteFromIndexA, Object pointerObject, int byteFromIndexDst, int byteLength, TruffleString.Encoding expectedEncoding) {
        TruffleString.CopyToNativeMemoryNode.getUncached().execute(this, byteFromIndexA, pointerObject, byteFromIndexDst, byteLength, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.ToJavaStringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final String toJavaStringUncached() {
        return TruffleString.ToJavaStringNode.getUncached().execute(this);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.SwitchEncodingNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final TruffleString switchEncodingUncached(TruffleString.Encoding targetEncoding) {
        return TruffleString.SwitchEncodingNode.getUncached().execute(this, targetEncoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link TruffleString.ForceEncodingNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final TruffleString forceEncodingUncached(TruffleString.Encoding expectedEncoding, TruffleString.Encoding targetEncoding) {
        return TruffleString.ForceEncodingNode.getUncached().execute(this, expectedEncoding, targetEncoding);
    }

    /**
     * Shorthand for calling the uncached version of
     * {@link TruffleString.CreateCodePointIteratorNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final TruffleStringIterator createCodePointIteratorUncached(TruffleString.Encoding expectedEncoding) {
        return TruffleString.CreateCodePointIteratorNode.getUncached().execute(this, expectedEncoding);
    }

    /**
     * Shorthand for calling the uncached version of
     * {@link TruffleString.CreateBackwardCodePointIteratorNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public final TruffleStringIterator createBackwardCodePointIteratorUncached(TruffleString.Encoding expectedEncoding) {
        TruffleStringIterator it = TruffleString.CreateCodePointIteratorNode.getUncached().execute(this, expectedEncoding);
        it.setRawIndex(length());
        return it;
    }

    /**
     * Returns {@code true} if this string is equal to {@code obj}. {@link TruffleString} does not
     * attempt to automatically convert differently encoded strings during comparison, make sure to
     * convert both strings to a common encoding before comparing them!
     *
     * @see TruffleString.EqualNode
     * @since 22.1
     */
    @TruffleBoundary
    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AbstractTruffleString)) {
            return false;
        }
        AbstractTruffleString b = (AbstractTruffleString) obj;
        int codeRangeA = TStringInternalNodes.GetCodeRangeNode.getUncached().execute(this);
        int codeRangeB = TStringInternalNodes.GetCodeRangeNode.getUncached().execute(b);
        int enc = encoding();
        if (enc != b.encoding()) {
            if (!b.isLooselyCompatibleTo(enc, TruffleString.Encoding.getMaxCompatibleCodeRange(enc), codeRangeB)) {
                enc = b.encoding();
            }
            if (!isLooselyCompatibleTo(enc, TruffleString.Encoding.getMaxCompatibleCodeRange(enc), codeRangeA)) {
                return false;
            }
        }
        return TruffleString.EqualNode.checkContentEquals(this, codeRangeA, b, codeRangeB,
                        TruffleString.ToIndexableNode.getUncached(),
                        TruffleString.ToIndexableNode.getUncached(),
                        TStringOpsNodes.RawEqualsNode.getUncached());
    }

    /**
     * Returns this string's hashcode. Note that the hashcode is dependent on the string's encoding,
     * make sure to convert strings to a common encoding before comparing their hash codes!
     *
     * @see TruffleString.HashCodeNode
     * @since 22.1
     */
    @TruffleBoundary
    @Override
    public final int hashCode() {
        if (!isHashCodeCalculated()) {
            return hashCodeUncached(TruffleString.Encoding.get(encoding()));
        }
        return hashCode;
    }

    /**
     * Returns a {@link String} representation of this string. Do not use this on fast paths.
     *
     * @see TruffleString.ToJavaStringNode
     * @since 22.1
     */
    @TruffleBoundary
    @Override
    public final String toString() {
        if (isBytes(encoding) && !is7Bit(TStringInternalNodes.GetCodeRangeNode.getUncached().execute(this))) {
            StringBuilder sb = new StringBuilder(length);
            TruffleStringIterator it = createCodePointIteratorUncached(TruffleString.Encoding.BYTES);
            while (it.hasNext()) {
                int c = it.nextUncached();
                if (c <= 0x7f) {
                    sb.append((char) c);
                } else {
                    sb.append(String.format("\\x%02x", c));
                }
            }
            return sb.toString();
        }
        return toJavaStringUncached();
    }

    /**
     * Returns debug information about this string. For debugging purposes only. The format of the
     * information returned by this method is unspecified and may change at any time.
     *
     * @since 22.1
     */
    @SuppressWarnings("unused")
    @TruffleBoundary
    public final String toStringDebug() {
        return String.format("TString(%s, %s, off: %d, len: %d, str: %d, cpLen: %d, \"%s\")",
                        TruffleString.Encoding.get(encoding()),
                        TSCodeRange.toString(this instanceof TruffleString ? ((TruffleString) this).codeRange() : ((MutableTruffleString) this).codeRange()),
                        offset(), length(), stride(),
                        this instanceof TruffleString ? ((TruffleString) this).codePointLength() : ((MutableTruffleString) this).codePointLength(),
                        toJavaStringUncached());
    }

    static final class LazyConcat {

        private final TruffleString left;
        private final TruffleString right;

        LazyConcat(TruffleString left, TruffleString right) {
            this.left = left;
            this.right = right;
        }

        @TruffleBoundary
        static byte[] flatten(Node location, TruffleString a) {
            byte[] dst = new byte[a.length() << a.stride()];
            flatten(location, a, 0, a.length(), dst, 0, a.stride());
            return dst;
        }

        @TruffleBoundary
        private static void flatten(Node location, TruffleString src, int srcBegin, int srcEnd, byte[] dst, int dstBegin, int dstStride) {
            TruffleString str = src;
            int from = srcBegin;
            int to = srcEnd;
            int dstFrom = dstBegin;
            for (;;) {
                assert 0 <= from && from <= to && to <= str.length();
                Object data = str.data();
                if (data instanceof LazyConcat) {
                    TruffleString left = ((LazyConcat) data).left;
                    TruffleString right = ((LazyConcat) data).right;
                    int mid = left.length();
                    if (to - mid >= mid - from) {
                        // right is longer, recurse left
                        if (from < mid) {
                            if (left.isLazyConcat()) {
                                flatten(location, left, from, mid, dst, dstFrom, dstStride);
                            } else {
                                copy(location, left, dst, dstFrom, dstStride);
                            }
                            dstFrom += mid - from;
                            from = 0;
                        } else {
                            from -= mid;
                        }
                        to -= mid;
                        str = right;
                    } else {
                        // left is longer, recurse right
                        if (to > mid) {
                            if (right.isLazyConcat()) {
                                flatten(location, right, 0, to - mid, dst, dstFrom + mid - from, dstStride);
                            } else {
                                copy(location, right, dst, dstFrom + mid - from, dstStride);
                            }
                            to = mid;
                        }
                        str = left;
                    }
                } else {
                    copy(location, str, dst, dstFrom, dstStride);
                    return;
                }
            }
        }

        @TruffleBoundary
        private static void copy(Node location, TruffleString src, byte[] dst, int dstFrom, int dstStride) {
            Object arrayA = TruffleString.ToIndexableNode.getUncached().execute(src, src.data());
            TStringOps.arraycopyWithStride(location,
                            arrayA, src.offset(), src.stride(), 0,
                            dst, 0, dstStride, dstFrom, src.length());
        }
    }

    static final class LazyLong {

        final long value;
        byte[] bytes;

        LazyLong(long value) {
            this.value = value;
        }

        void setBytes(TruffleString a, byte[] bytes) {
            if (a.offset() != 0 || a.length() != bytes.length) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw CompilerDirectives.shouldNotReachHere();
            }
            this.bytes = bytes;
        }
    }

    static final class NativePointer {

        /**
         * The Interop object the long pointer was extracted from. We keep this reference in case
         * the native pointer's lifetime depends on the pointer object's lifetime.
         */
        private final Object pointerObject;
        final long pointer;
        private byte[] bytes;
        private final int offset;
        private volatile boolean byteArrayIsValid = false;

        private NativePointer(Object pointerObject, long pointer, int offset) {
            this.pointerObject = pointerObject;
            this.pointer = pointer;
            this.offset = offset;
        }

        static NativePointer create(Node nodeThis, Object pointerObject, Node interopLibrary, int offset) {
            if (!TStringAccessor.isNativeAccessAllowed(nodeThis)) {
                throw InternalErrors.nativeAccessRequired();
            }
            return new NativePointer(pointerObject, TStringAccessor.INTEROP.unboxPointer(interopLibrary, pointerObject), offset);
        }

        NativePointer copy(int newOffset) {
            return new NativePointer(pointerObject, pointer, newOffset);
        }

        Object getPointerObject() {
            return pointerObject;
        }

        byte[] getBytes() {
            assert bytes != null;
            return bytes;
        }

        int offset() {
            return offset;
        }

        void materializeByteArray(AbstractTruffleString a, ConditionProfile profile) {
            materializeByteArray(a.length() << a.stride(), profile);
        }

        void materializeByteArray(int byteLength, ConditionProfile profile) {
            if (profile.profile(!byteArrayIsValid)) {
                if (bytes == null) {
                    bytes = new byte[byteLength];
                }
                TStringUnsafe.copyFromNative(pointer, offset, bytes, 0, byteLength);
                byteArrayIsValid = true;
            }
        }

        void invalidateCachedByteArray() {
            byteArrayIsValid = false;
        }
    }
}
