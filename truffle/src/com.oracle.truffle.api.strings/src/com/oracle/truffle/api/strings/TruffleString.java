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

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import static com.oracle.truffle.api.strings.TStringGuards.indexOfCannotMatch;
import static com.oracle.truffle.api.strings.TStringGuards.is16Bit;
import static com.oracle.truffle.api.strings.TStringGuards.is7Bit;
import static com.oracle.truffle.api.strings.TStringGuards.is7BitCompatible;
import static com.oracle.truffle.api.strings.TStringGuards.is8Bit;
import static com.oracle.truffle.api.strings.TStringGuards.is8BitCompatible;
import static com.oracle.truffle.api.strings.TStringGuards.isAscii;
import static com.oracle.truffle.api.strings.TStringGuards.isBrokenFixedWidth;
import static com.oracle.truffle.api.strings.TStringGuards.isBrokenMultiByte;
import static com.oracle.truffle.api.strings.TStringGuards.isBytes;
import static com.oracle.truffle.api.strings.TStringGuards.isFixedWidth;
import static com.oracle.truffle.api.strings.TStringGuards.isInlinedJavaString;
import static com.oracle.truffle.api.strings.TStringGuards.isLatin1;
import static com.oracle.truffle.api.strings.TStringGuards.isStride0;
import static com.oracle.truffle.api.strings.TStringGuards.isStride1;
import static com.oracle.truffle.api.strings.TStringGuards.isSupportedEncoding;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF16Or32;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF32;
import static com.oracle.truffle.api.strings.TStringGuards.isUTF8;
import static com.oracle.truffle.api.strings.TStringGuards.isUnsupportedEncoding;
import static com.oracle.truffle.api.strings.TStringGuards.isValidFixedWidth;
import static com.oracle.truffle.api.strings.TStringGuards.isValidMultiByte;
import static com.oracle.truffle.api.strings.TStringGuards.littleEndian;

import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GeneratePackagePrivate;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.IntValueProfile;

/**
 * Represents a primitive String type, which can be reused across languages. Language implementers
 * are encouraged to use Truffle Strings as their language's string type for easier interoperability
 * and better performance. Truffle strings can be encoded in a number of {@link Encoding encodings}.
 * A {@link TruffleString} object can cache multiple representations (in multiple encodings) of the
 * same string in the string object itself. A single {@link TruffleString} instance can also
 * represent the same string in multiple encodings, if the string's content would be equal in all
 * such encodings (e.g. a string containing only ASCII characters can be viewed as being encoded in
 * almost any encoding, since the encoded bytes would be equal). To facilitate this, all methods
 * have an {@code expectedEncoding} parameter to indicate which encoding a given string should be
 * viewed in.
 * <p>
 * {@link TruffleString} instances can be created via one of the following nodes, or via
 * {@link TruffleStringBuilder}.
 * <ul>
 * <li>{@link FromByteArrayNode}</li>
 * <li>{@link FromCharArrayUTF16Node}</li>
 * <li>{@link FromJavaStringNode}</li>
 * <li>{@link FromIntArrayUTF32Node}</li>
 * <li>{@link FromNativePointerNode}</li>
 * <li>{@link FromCodePointNode}</li>
 * <li>{@link FromLongNode}</li>
 * </ul>
 *
 * For iteration use {@link TruffleStringIterator}. There is a version of {@link TruffleString} that
 * is also mutable. See {@link MutableTruffleString} for details.
 * <p>
 * Please see the
 * <a href="https://github.com/oracle/graal/tree/master/truffle/docs/TruffleStrings.md">tutorial</a>
 * for further usage instructions.
 *
 * @since 22.1
 */
public final class TruffleString extends AbstractTruffleString {

    /*
     * TODO: replace with VarHandle equivalent (GR-35129).
     */
    private static final AtomicReferenceFieldUpdater<TruffleString, TruffleString> NEXT_UPDATER = initializeNextUpdater();

    @TruffleBoundary
    private static AtomicReferenceFieldUpdater<TruffleString, TruffleString> initializeNextUpdater() {
        return AtomicReferenceFieldUpdater.newUpdater(TruffleString.class, TruffleString.class, "next");
    }

    private static final byte FLAG_CACHE_HEAD = (byte) 0x80;

    private final int codePointLength;
    private final byte codeRange;
    private volatile TruffleString next;

    private TruffleString(Object data, int offset, int length, int stride, int encoding, int codePointLength, int codeRange) {
        this(data, offset, length, stride, encoding, codePointLength, codeRange, true);
    }

    private TruffleString(Object data, int offset, int length, int stride, int encoding, int codePointLength, int codeRange, boolean isCacheHead) {
        super(data, offset, length, stride, encoding, isCacheHead ? FLAG_CACHE_HEAD : 0);
        assert isByte(codeRange);
        assert codePointLength >= 0;
        assert TSCodeRange.isCodeRange(codeRange);
        assert !isAscii(encoding) || is7Bit(codeRange) || isBrokenFixedWidth(codeRange);
        assert !isLatin1(encoding) || is7Bit(codeRange) || is8Bit(codeRange);
        assert !isUTF8(encoding) || !is8Bit(codeRange) && !is16Bit(codeRange) && !isValidFixedWidth(codeRange) && !isBrokenFixedWidth(codeRange);
        assert !isUTF16(encoding) || !isValidFixedWidth(codeRange) && !isBrokenFixedWidth(codeRange);
        assert !isUTF32(encoding) || !isValidMultiByte(codeRange) && !isBrokenMultiByte(codeRange);
        assert !isBytes(encoding) || is7Bit(codeRange) || isValidFixedWidth(codeRange);
        this.codePointLength = codePointLength;
        this.codeRange = (byte) codeRange;
    }

    static TruffleString createFromByteArray(byte[] bytes, int length, int stride, int encoding, int codePointLength, int codeRange) {
        return createFromByteArray(bytes, length, stride, encoding, codePointLength, codeRange, true);
    }

    static TruffleString createFromByteArray(byte[] bytes, int length, int stride, int encoding, int codePointLength, int codeRange, boolean isCacheHead) {
        return createFromArray(bytes, 0, length, stride, encoding, codePointLength, codeRange, isCacheHead);
    }

    static TruffleString createFromArray(Object bytes, int offset, int length, int stride, int encoding, int codePointLength, int codeRange) {
        return createFromArray(bytes, offset, length, stride, encoding, codePointLength, codeRange, true);
    }

    static TruffleString createFromArray(Object bytes, int offset, int length, int stride, int encoding, int codePointLength, int codeRange, boolean isCacheHead) {
        assert bytes instanceof byte[] || isInlinedJavaString(bytes) || bytes instanceof NativePointer;
        assert offset >= 0;
        assert bytes instanceof NativePointer || offset + ((long) length << stride) <= TStringOps.byteLength(bytes);
        assert attrsAreCorrect(bytes, encoding, offset, length, codePointLength, codeRange, stride);
        return new TruffleString(bytes, offset, length, stride, encoding, codePointLength, codeRange, isCacheHead);
    }

    static TruffleString createConstant(byte[] bytes, int length, int stride, int encoding, int codePointLength, int codeRange) {
        return createConstant(bytes, length, stride, encoding, codePointLength, codeRange, true);
    }

    static TruffleString createConstant(byte[] bytes, int length, int stride, int encoding, int codePointLength, int codeRange, boolean isCacheHead) {
        TruffleString ret = createFromByteArray(bytes, length, stride, encoding, codePointLength, codeRange, isCacheHead);
        // eagerly compute cached hash
        ret.hashCode();
        return ret;
    }

    static TruffleString createLazyLong(long value, int encoding) {
        int length = NumberConversion.stringLengthLong(value);
        return new TruffleString(new LazyLong(value), 0, length, 0, encoding, length, TSCodeRange.get7Bit());
    }

    static TruffleString createLazyConcat(TruffleString a, TruffleString b, int encoding, int length, int stride) {
        assert !TSCodeRange.isBrokenMultiByte(a.codeRange());
        assert !TSCodeRange.isBrokenMultiByte(b.codeRange());
        assert a.isLooselyCompatibleTo(encoding);
        assert b.isLooselyCompatibleTo(encoding);
        assert length == a.length() + b.length();
        int codeRange = TSCodeRange.commonCodeRange(a.codeRange(), b.codeRange());
        return new TruffleString(new LazyConcat(a, b), 0, length, stride, encoding, a.codePointLength() + b.codePointLength(), codeRange);
    }

    static TruffleString createWrapJavaString(String str, int codePointLength, int codeRange) {
        int stride = TStringUnsafe.getJavaStringStride(str);
        return new TruffleString(str, 0, str.length(), stride, Encodings.getUTF16(), codePointLength, codeRange, false);
    }

    private static boolean attrsAreCorrect(Object bytes, int encoding, int offset, int length, int codePointLength, int codeRange, int stride) {
        CompilerAsserts.neverPartOfCompilation();
        if (length == 0) {
            int length0CodeRange = is7BitCompatible(encoding) ? TSCodeRange.get7Bit()
                            : JCodings.getInstance().isSingleByte(Encoding.getJCoding(encoding)) ? TSCodeRange.getValidFixedWidth() : TSCodeRange.getValidMultiByte();
            return TStringOps.byteLength(bytes) == 0 && offset == 0 && codePointLength == 0 && codeRange == length0CodeRange && stride == 0;
        }
        int knownCodeRange = TSCodeRange.getUnknown();
        if (isUTF16Or32(encoding) && stride == 0) {
            knownCodeRange = TSCodeRange.get8Bit();
        } else if (isUTF32(encoding) && stride == 1) {
            knownCodeRange = TSCodeRange.get16Bit();
        }
        if (bytes instanceof NativePointer) {
            ((NativePointer) bytes).materializeByteArray(length << stride, ConditionProfile.getUncached());
        }
        long attrs = TStringInternalNodes.CalcStringAttributesNode.getUncached().execute(null, bytes, offset, length, stride, encoding, knownCodeRange);
        int cpLengthCalc = StringAttributes.getCodePointLength(attrs);
        int codeRangeCalc = StringAttributes.getCodeRange(attrs);
        assert cpLengthCalc == codePointLength : "inconsistent codePointLength: " + cpLengthCalc + " != " + codePointLength;
        assert codeRangeCalc == codeRange : "inconsistent codeRange: " + TSCodeRange.toString(codeRangeCalc) + " != " + TSCodeRange.toString(codeRange);
        return attrs == StringAttributes.create(codePointLength, codeRange);
    }

    boolean isLooselyCompatibleTo(int expectedEncoding) {
        return isLooselyCompatibleTo(expectedEncoding, Encoding.getMaxCompatibleCodeRange(expectedEncoding), codeRange());
    }

    /**
     * Get this string's length in codepoints.
     */
    int codePointLength() {
        return codePointLength;
    }

    /**
     * Get this string's code range as defined in {@link TSCodeRange}.
     */
    int codeRange() {
        return codeRange;
    }

    boolean isCacheHead() {
        assert ((flags() & FLAG_CACHE_HEAD) != 0) == (flags() < 0);
        return flags() < 0;
    }

    TruffleString getCacheHead() {
        assert cacheRingIsValid();
        TruffleString cur = next;
        if (cur == null) {
            assert isCacheHead();
            return this;
        }
        while (!cur.isCacheHead()) {
            cur = cur.next;
        }
        return cur;
    }

    @TruffleBoundary
    void cacheInsert(TruffleString entry) {
        assert !entry.isCacheHead();
        // the cache head does never change
        TruffleString cacheHead = getCacheHead();
        assert !cacheEntryEquals(cacheHead, entry);
        TruffleString cacheHeadNext;
        do {
            cacheHeadNext = cacheHead.next;
            if (hasDuplicateEncoding(cacheHead, cacheHeadNext, entry)) {
                return;
            }
            entry.next = cacheHeadNext == null ? cacheHead : cacheHeadNext;
        } while (!setNextAtomic(cacheHead, cacheHeadNext, entry));
    }

    private static boolean hasDuplicateEncoding(TruffleString cacheHead, TruffleString start, TruffleString insertEntry) {
        if (start == null) {
            return false;
        }
        TruffleString current = start;
        while (current != cacheHead) {
            if (cacheEntryEquals(insertEntry, current)) {
                return true;
            }
            current = current.next;
        }
        return false;
    }

    private static boolean cacheEntryEquals(TruffleString a, TruffleString b) {
        return b.encoding() == a.encoding() && (!isUTF16(a) || b.isJavaString() == a.isJavaString());
    }

    @TruffleBoundary
    private static boolean setNextAtomic(TruffleString cacheHead, TruffleString currentNext, TruffleString newNext) {
        return NEXT_UPDATER.compareAndSet(cacheHead, currentNext, newNext);
    }

    private boolean cacheRingIsValid() {
        CompilerAsserts.neverPartOfCompilation();
        TruffleString head = null;
        TruffleString cur = this;
        boolean javaStringVisited = false;
        BitSet visitedEncodings = new BitSet(Encoding.values().length);
        EconomicSet<TruffleString> visited = EconomicSet.create(Equivalence.IDENTITY_WITH_SYSTEM_HASHCODE);
        do {
            if (cur.isCacheHead()) {
                assert head == null : "multiple cache heads";
                head = cur;
            }
            if (cur.isJavaString()) {
                assert !javaStringVisited : "duplicate cached java string";
                javaStringVisited = true;
            } else {
                assert !visitedEncodings.get(cur.encoding()) : "duplicate encoding";
                visitedEncodings.set(cur.encoding());
            }
            assert visited.add(cur) : "not a ring structure";
            cur = cur.next;
        } while (cur != this && cur != null);
        return true;
    }

    /**
     * The list of encodings supported by {@link TruffleString}. {@link TruffleString} is especially
     * optimized for the following encodings:
     * <ul>
     * <li>{@code UTF-32}: this means UTF-32 <i>in your system's endianness</i>.
     * {@link TruffleString} transparently compacts UTF-32 strings to 8-bit or 16-bit
     * representations, where possible.</li>
     * <li>{@code UTF-16}: this means UTF-16 <i>in your system's endianness</i>.
     * {@link TruffleString} transparently compacts UTF-16 strings to 8-bit representations, where
     * possible.</li>
     * <li>{@code UTF-8}</li>
     * <li>{@code ISO-8859-1}</li>
     * <li>{@code US-ASCII}</li>
     * <li>{@code BYTES}, which is essentially identical to US-ASCII, with the only difference being
     * that {@code BYTES} treats all byte values as valid codepoints.</li>
     * </ul>
     * <p>
     * </p>
     * All other encodings are supported using the JRuby JCodings library, which incurs more
     * {@link TruffleBoundary} calls. NOTE: to enable support for these encodings,
     * {@code TruffleLanguage.Registration#needsAllEncodings()} must be set to {@code true} in the
     * truffle language's registration.
     *
     * @since 22.1
     */
    public enum Encoding {

        /* directly supported encodings */
        /**
         * UTF-32LE. Directly supported if the current system is little-endian.
         *
         * @since 22.1
         */
        UTF_32LE(littleEndian() ? 0 : 97, "UTF_32LE", littleEndian() ? 2 : 0),
        /**
         * UTF-32BE. Directly supported if the current system is big-endian.
         *
         * @since 22.1
         */
        UTF_32BE(littleEndian() ? 97 : 0, "UTF_32BE", littleEndian() ? 0 : 2),
        /**
         * UTF-16LE. Directly supported if the current system is little-endian.
         *
         * @since 22.1
         */
        UTF_16LE(littleEndian() ? 1 : 98, "UTF_16LE", littleEndian() ? 1 : 0),
        /**
         * UTF-16BE. Directly supported if the current system is big-endian.
         *
         * @since 22.1
         */
        UTF_16BE(littleEndian() ? 98 : 1, "UTF_16BE", littleEndian() ? 0 : 1),
        /**
         * ISO-8859-1, also known as LATIN-1, which is equivalent to US-ASCII + the LATIN-1
         * Supplement Unicode block.
         *
         * @since 22.1
         */
        ISO_8859_1(2, "ISO_8859_1"),
        /**
         * UTF-8.
         *
         * @since 22.1
         */
        UTF_8(3, "UTF_8"),
        /**
         * US-ASCII, which maps only 7-bit characters.
         *
         * @since 22.1
         */
        US_ASCII(4, "US_ASCII"),
        /**
         * Special "encoding" BYTES: This encoding is identical to US-ASCII, but treats all values
         * outside the us-ascii range as valid codepoints as well. Caution: no codepoint mappings
         * are defined for non-us-ascii values in this encoding, so {@link SwitchEncodingNode} will
         * replace all of them with {@code '?'} when converting from or to BYTES! To preserve all
         * bytes and "reinterpret" a BYTES string in another encoding, use
         * {@link ForceEncodingNode}.
         *
         * @since 22.1
         */
        BYTES(5, "BYTES"),

        /* encodings supported by falling back to JCodings */

        /**
         * Big5.
         *
         * @since 22.1
         */
        Big5(6, "Big5"),
        /**
         * Big5-HKSCS.
         *
         * @since 22.1
         */
        Big5_HKSCS(7, "Big5_HKSCS"),
        /**
         * Big5-UAO.
         *
         * @since 22.1
         */
        Big5_UAO(8, "Big5_UAO"),
        /**
         * CP51932.
         *
         * @since 22.1
         */
        CP51932(9, "CP51932"),
        /**
         * CP850.
         *
         * @since 22.1
         */
        CP850(10, "CP850"),
        /**
         * CP852.
         *
         * @since 22.1
         */
        CP852(11, "CP852"),
        /**
         * CP855.
         *
         * @since 22.1
         */
        CP855(12, "CP855"),
        /**
         * CP949.
         *
         * @since 22.1
         */
        CP949(13, "CP949"),
        /**
         * CP950.
         *
         * @since 22.1
         */
        CP950(14, "CP950"),
        /**
         * CP951.
         *
         * @since 22.1
         */
        CP951(15, "CP951"),
        /**
         * EUC-JIS-2004.
         *
         * @since 22.1
         */
        EUC_JIS_2004(16, "EUC_JIS_2004"),
        /**
         * EUC-JP.
         *
         * @since 22.1
         */
        EUC_JP(17, "EUC_JP"),
        /**
         * EUC-KR.
         *
         * @since 22.1
         */
        EUC_KR(18, "EUC_KR"),
        /**
         * EUC-TW.
         *
         * @since 22.1
         */
        EUC_TW(19, "EUC_TW"),
        /**
         * Emacs-Mule.
         *
         * @since 22.1
         */
        Emacs_Mule(20, "Emacs_Mule"),
        /**
         * EucJP-ms.
         *
         * @since 22.1
         */
        EucJP_ms(21, "EucJP_ms"),
        /**
         * GB12345.
         *
         * @since 22.1
         */
        GB12345(22, "GB12345"),
        /**
         * GB18030.
         *
         * @since 22.1
         */
        GB18030(23, "GB18030"),
        /**
         * GB1988.
         *
         * @since 22.1
         */
        GB1988(24, "GB1988"),
        /**
         * GB2312.
         *
         * @since 22.1
         */
        GB2312(25, "GB2312"),
        /**
         * GBK.
         *
         * @since 22.1
         */
        GBK(26, "GBK"),
        /**
         * IBM437.
         *
         * @since 22.1
         */
        IBM437(27, "IBM437"),
        /**
         * IBM737.
         *
         * @since 22.1
         */
        IBM737(28, "IBM737"),
        /**
         * IBM775.
         *
         * @since 22.1
         */
        IBM775(29, "IBM775"),
        /**
         * IBM852.
         *
         * @since 22.1
         */
        IBM852(30, "IBM852"),
        /**
         * IBM855.
         *
         * @since 22.1
         */
        IBM855(31, "IBM855"),
        /**
         * IBM857.
         *
         * @since 22.1
         */
        IBM857(32, "IBM857"),
        /**
         * IBM860.
         *
         * @since 22.1
         */
        IBM860(33, "IBM860"),
        /**
         * IBM861.
         *
         * @since 22.1
         */
        IBM861(34, "IBM861"),
        /**
         * IBM862.
         *
         * @since 22.1
         */
        IBM862(35, "IBM862"),
        /**
         * IBM863.
         *
         * @since 22.1
         */
        IBM863(36, "IBM863"),
        /**
         * IBM864.
         *
         * @since 22.1
         */
        IBM864(37, "IBM864"),
        /**
         * IBM865.
         *
         * @since 22.1
         */
        IBM865(38, "IBM865"),
        /**
         * IBM866.
         *
         * @since 22.1
         */
        IBM866(39, "IBM866"),
        /**
         * IBM869.
         *
         * @since 22.1
         */
        IBM869(40, "IBM869"),
        /**
         * ISO-8859-10.
         *
         * @since 22.1
         */
        ISO_8859_10(41, "ISO_8859_10"),
        /**
         * ISO-8859-11.
         *
         * @since 22.1
         */
        ISO_8859_11(42, "ISO_8859_11"),
        /**
         * ISO-8859-13.
         *
         * @since 22.1
         */
        ISO_8859_13(43, "ISO_8859_13"),
        /**
         * ISO-8859-14.
         *
         * @since 22.1
         */
        ISO_8859_14(44, "ISO_8859_14"),
        /**
         * ISO-8859-15.
         *
         * @since 22.1
         */
        ISO_8859_15(45, "ISO_8859_15"),
        /**
         * ISO-8859-16.
         *
         * @since 22.1
         */
        ISO_8859_16(46, "ISO_8859_16"),
        /**
         * ISO-8859-2.
         *
         * @since 22.1
         */
        ISO_8859_2(47, "ISO_8859_2"),
        /**
         * ISO-8859-3.
         *
         * @since 22.1
         */
        ISO_8859_3(48, "ISO_8859_3"),
        /**
         * ISO-8859-4.
         *
         * @since 22.1
         */
        ISO_8859_4(49, "ISO_8859_4"),
        /**
         * ISO-8859-5.
         *
         * @since 22.1
         */
        ISO_8859_5(50, "ISO_8859_5"),
        /**
         * ISO-8859-6.
         *
         * @since 22.1
         */
        ISO_8859_6(51, "ISO_8859_6"),
        /**
         * ISO-8859-7.
         *
         * @since 22.1
         */
        ISO_8859_7(52, "ISO_8859_7"),
        /**
         * ISO-8859-8.
         *
         * @since 22.1
         */
        ISO_8859_8(53, "ISO_8859_8"),
        /**
         * ISO-8859-9.
         *
         * @since 22.1
         */
        ISO_8859_9(54, "ISO_8859_9"),
        /**
         * KOI8-R.
         *
         * @since 22.1
         */
        KOI8_R(55, "KOI8_R"),
        /**
         * KOI8-U.
         *
         * @since 22.1
         */
        KOI8_U(56, "KOI8_U"),
        /**
         * MacCentEuro.
         *
         * @since 22.1
         */
        MacCentEuro(57, "MacCentEuro"),
        /**
         * MacCroatian.
         *
         * @since 22.1
         */
        MacCroatian(58, "MacCroatian"),
        /**
         * MacCyrillic.
         *
         * @since 22.1
         */
        MacCyrillic(59, "MacCyrillic"),
        /**
         * MacGreek.
         *
         * @since 22.1
         */
        MacGreek(60, "MacGreek"),
        /**
         * MacIceland.
         *
         * @since 22.1
         */
        MacIceland(61, "MacIceland"),
        /**
         * MacJapanese.
         *
         * @since 22.1
         */
        MacJapanese(62, "MacJapanese"),
        /**
         * MacRoman.
         *
         * @since 22.1
         */
        MacRoman(63, "MacRoman"),
        /**
         * MacRomania.
         *
         * @since 22.1
         */
        MacRomania(64, "MacRomania"),
        /**
         * MacThai.
         *
         * @since 22.1
         */
        MacThai(65, "MacThai"),
        /**
         * MacTurkish.
         *
         * @since 22.1
         */
        MacTurkish(66, "MacTurkish"),
        /**
         * MacUkraine.
         *
         * @since 22.1
         */
        MacUkraine(67, "MacUkraine"),
        /**
         * SJIS-DoCoMo.
         *
         * @since 22.1
         */
        SJIS_DoCoMo(68, "SJIS_DoCoMo"),
        /**
         * SJIS-KDDI.
         *
         * @since 22.1
         */
        SJIS_KDDI(69, "SJIS_KDDI"),
        /**
         * SJIS-SoftBank.
         *
         * @since 22.1
         */
        SJIS_SoftBank(70, "SJIS_SoftBank"),
        /**
         * Shift-JIS.
         *
         * @since 22.1
         */
        Shift_JIS(71, "Shift_JIS"),
        /**
         * Stateless-ISO-2022-JP.
         *
         * @since 22.1
         */
        Stateless_ISO_2022_JP(72, "Stateless_ISO_2022_JP"),
        /**
         * Stateless-ISO-2022-JP-KDDI.
         *
         * @since 22.1
         */
        Stateless_ISO_2022_JP_KDDI(73, "Stateless_ISO_2022_JP_KDDI"),
        /**
         * TIS-620.
         *
         * @since 22.1
         */
        TIS_620(74, "TIS_620"),
        /**
         * UTF8-DoCoMo.
         *
         * @since 22.1
         */
        UTF8_DoCoMo(75, "UTF8_DoCoMo"),
        /**
         * UTF8-KDDI.
         *
         * @since 22.1
         */
        UTF8_KDDI(76, "UTF8_KDDI"),
        /**
         * UTF8-MAC.
         *
         * @since 22.1
         */
        UTF8_MAC(77, "UTF8_MAC"),
        /**
         * UTF8-SoftBank.
         *
         * @since 22.1
         */
        UTF8_SoftBank(78, "UTF8_SoftBank"),
        /**
         * Windows-1250.
         *
         * @since 22.1
         */
        Windows_1250(79, "Windows_1250"),
        /**
         * Windows-1251.
         *
         * @since 22.1
         */
        Windows_1251(80, "Windows_1251"),
        /**
         * Windows-1252.
         *
         * @since 22.1
         */
        Windows_1252(81, "Windows_1252"),
        /**
         * Windows-1253.
         *
         * @since 22.1
         */
        Windows_1253(82, "Windows_1253"),
        /**
         * Windows-1254.
         *
         * @since 22.1
         */
        Windows_1254(83, "Windows_1254"),
        /**
         * Windows-1255.
         *
         * @since 22.1
         */
        Windows_1255(84, "Windows_1255"),
        /**
         * Windows-1256.
         *
         * @since 22.1
         */
        Windows_1256(85, "Windows_1256"),
        /**
         * Windows-1257.
         *
         * @since 22.1
         */
        Windows_1257(86, "Windows_1257"),
        /**
         * Windows-1258.
         *
         * @since 22.1
         */
        Windows_1258(87, "Windows_1258"),
        /**
         * Windows-31J.
         *
         * @since 22.1
         */
        Windows_31J(88, "Windows_31J"),
        /**
         * Windows-874.
         *
         * @since 22.1
         */
        Windows_874(89, "Windows_874"),
        /* non-ascii-compatible encodings */
        /**
         * CP50220.
         *
         * @since 22.1
         */
        CP50220(90, "CP50220"),
        /**
         * CP50221.
         *
         * @since 22.1
         */
        CP50221(91, "CP50221"),
        /**
         * IBM037.
         *
         * @since 22.1
         */
        IBM037(92, "IBM037"),
        /**
         * ISO-2022-JP.
         *
         * @since 22.1
         */
        ISO_2022_JP(93, "ISO_2022_JP"),
        /**
         * ISO-2022-JP-2.
         *
         * @since 22.1
         */
        ISO_2022_JP_2(94, "ISO_2022_JP_2"),
        /**
         * ISO-2022-JP-KDDI.
         *
         * @since 22.1
         */
        ISO_2022_JP_KDDI(95, "ISO_2022_JP_KDDI"),
        /**
         * UTF-7.
         *
         * @since 22.1
         */
        UTF_7(96, "UTF_7");

        /**
         * UTF-32 in <i>the current system's endianness</i>, without byte-order mark, with
         * transparent string compaction.
         *
         * @since 22.1
         */
        public static final Encoding UTF_32 = littleEndian() ? UTF_32LE : UTF_32BE;
        /**
         * UTF-16 in <i>the current system's endianness</i>, without byte-order mark, with
         * transparent string compaction.
         *
         * @since 22.1
         */
        public static final Encoding UTF_16 = littleEndian() ? UTF_16LE : UTF_16BE;

        final byte id;
        final String name;
        final JCodings.Encoding jCoding;
        final byte maxCompatibleCodeRange;
        final byte naturalStride;

        Encoding(int id, String name) {
            this(id, name, 0);
        }

        Encoding(int id, String name, int naturalStride) {
            assert id <= 0x7f;
            assert Stride.isStride(naturalStride);
            this.id = (byte) id;
            this.name = name;
            this.jCoding = JCodings.ENABLED ? JCodings.getInstance().get(name) : null;
            if (is16BitCompatible()) {
                maxCompatibleCodeRange = (byte) (TSCodeRange.get16Bit() + 1);
            } else if (is8BitCompatible()) {
                maxCompatibleCodeRange = (byte) (TSCodeRange.get8Bit() + 1);
            } else if (is7BitCompatible()) {
                maxCompatibleCodeRange = (byte) (TSCodeRange.get7Bit() + 1);
            } else {
                maxCompatibleCodeRange = 0;
            }
            this.naturalStride = (byte) naturalStride;
        }

        @CompilationFinal(dimensions = 1) private static final Encoding[] ENCODINGS_TABLE = new Encoding[Encoding.values().length];
        @CompilationFinal(dimensions = 1) private static final JCodings.Encoding[] J_CODINGS_TABLE = new JCodings.Encoding[Encoding.values().length];
        @CompilationFinal(dimensions = 1) private static final byte[] MAX_COMPATIBLE_CODE_RANGE = new byte[Encoding.values().length];
        @CompilationFinal(dimensions = 1) private static final TruffleString[] EMPTY_STRINGS = new TruffleString[Encoding.values().length];
        private static final EconomicMap<String, Encoding> J_CODINGS_NAME_MAP = EconomicMap.create(Encoding.values().length);

        static {
            for (Encoding e : Encoding.values()) {
                assert ENCODINGS_TABLE[e.id] == null;
                ENCODINGS_TABLE[e.id] = e;
                assert J_CODINGS_TABLE[e.id] == null;
                J_CODINGS_TABLE[e.id] = e.jCoding;
                MAX_COMPATIBLE_CODE_RANGE[e.id] = e.maxCompatibleCodeRange;
                if (JCodings.ENABLED) {
                    J_CODINGS_NAME_MAP.put(JCodings.getInstance().name(e.jCoding), e);
                }
            }
            assert UTF_16.naturalStride == 1;
            assert UTF_32.naturalStride == 2;
            EMPTY_STRINGS[US_ASCII.id] = createConstant(new byte[0], 0, 0, US_ASCII.id, 0, TSCodeRange.get7Bit());
            for (Encoding e : Encoding.values()) {
                if (e != US_ASCII) {
                    assert EMPTY_STRINGS[e.id] == null;
                    if (isSupported(e.id) || JCodings.ENABLED) {
                        EMPTY_STRINGS[e.id] = createEmpty(e.id);
                    }
                }
            }
        }

        private static TruffleString createEmpty(int encoding) {
            if (is7BitCompatible(encoding) && !AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS || encoding == Encodings.getAscii()) {
                return EMPTY_STRINGS[US_ASCII.id];
            }
            TruffleString ret = createConstant(new byte[0], 0, 0, encoding, 0, TSCodeRange.getAsciiCodeRange(encoding), false);
            EMPTY_STRINGS[US_ASCII.id].cacheInsert(ret);
            return ret;
        }

        /**
         * Get an empty {@link TruffleString} with this encoding.
         *
         * @since 22.1
         */
        public TruffleString getEmpty() {
            return EMPTY_STRINGS[id];
        }

        /**
         * Get the {@link Encoding} corresponding to the given encoding name from the
         * {@code JCodings} library.
         *
         * @since 22.1
         */
        public static Encoding fromJCodingName(String name) {
            Encoding encoding = J_CODINGS_NAME_MAP.get(name, null);
            if (encoding == null) {
                throw InternalErrors.unknownEncoding(name);
            }
            return encoding;
        }

        JCodings.Encoding getJCoding() {
            return jCoding;
        }

        static Encoding get(int encoding) {
            return ENCODINGS_TABLE[encoding];
        }

        static JCodings.Encoding getJCoding(int encoding) {
            assert J_CODINGS_TABLE[encoding] == get(encoding).jCoding;
            return J_CODINGS_TABLE[encoding];
        }

        static int getMaxCompatibleCodeRange(int encoding) {
            return MAX_COMPATIBLE_CODE_RANGE[encoding];
        }

        boolean is7BitCompatible() {
            return is7BitCompatible(id);
        }

        boolean is8BitCompatible() {
            return is8BitCompatible(id);
        }

        boolean is16BitCompatible() {
            return is16BitCompatible(id);
        }

        static boolean is7BitCompatible(int encoding) {
            return encoding < 90;
        }

        static boolean is8BitCompatible(int encoding) {
            return encoding < 3;
        }

        static boolean is16BitCompatible(int encoding) {
            return encoding < 2;
        }

        static boolean isSupported(int encoding) {
            return encoding < 6;
        }

        static boolean isUnsupported(int encoding) {
            return encoding >= 6;
        }

        static boolean isFixedWidth(int encoding) {
            return JCodings.getInstance().isFixedWidth(getJCoding(encoding));
        }
    }

    /**
     * Provides information about a string's content. All values of this enum describe a set of
     * codepoints potentially contained by a string reporting said value.
     *
     * @since 22.1
     */
    public enum CodeRange {

        /**
         * All codepoints in this string are part of the Basic Latin Unicode block, also known as
         * ASCII (0x00 - 0x7f).
         *
         * @since 22.1
         */
        ASCII,

        /**
         * All codepoints in this string are part of the ISO-8859-1 character set (0x00 - 0xff),
         * which is equivalent to the union of the Basic Latin and the Latin-1 Supplement Unicode
         * block. At least one codepoint is outside the ASCII range (greater than 0x7f). Applicable
         * to {@link Encoding#ISO_8859_1}, {@link Encoding#UTF_16} and {@link Encoding#UTF_32} only.
         *
         * @since 22.1
         */
        LATIN_1,

        /**
         * All codepoints in this string are part of the Unicode Basic Multilingual Plane (BMP) (
         * 0x0000 - 0xffff). At least one codepoint is outside the LATIN_1 range (greater than
         * 0xff). Applicable to {@link Encoding#UTF_16} and {@link Encoding#UTF_32} only.
         *
         * @since 22.1
         */
        BMP,

        /**
         * This string is encoded correctly ({@link IsValidNode} returns {@code true}), and at least
         * one codepoint is outside the largest other applicable code range (e.g. greater than 0x7f
         * on {@link Encoding#UTF_8}, greater than 0xffff on {@link Encoding#UTF_16}).
         *
         * @since 22.1
         */
        VALID,

        /**
         * This string is not encoded correctly ({@link IsValidNode} returns {@code false}), and
         * contains at least one invalid codepoint.
         *
         * @since 22.1
         */
        BROKEN;

        /**
         * Returns {@code true} if this set of potential codepoints is equal to or contained by
         * {@code other}.
         *
         * @since 22.1
         */
        public boolean isSubsetOf(CodeRange other) {
            return ordinal() <= other.ordinal();
        }

        /**
         * Returns {@code true} if this set of potential codepoints is equal to or contains
         * {@code other}.
         *
         * @since 22.1
         */
        public boolean isSupersetOf(CodeRange other) {
            return ordinal() >= other.ordinal();
        }

        @CompilationFinal(dimensions = 1) private static final CodeRange[] CODE_RANGES = {
                        CodeRange.ASCII, CodeRange.LATIN_1, CodeRange.BMP, CodeRange.VALID, CodeRange.BROKEN, CodeRange.VALID, CodeRange.BROKEN};

        @CompilationFinal(dimensions = 1) private static final CodeRange[] BYTE_CODE_RANGES = {
                        CodeRange.ASCII, CodeRange.VALID, CodeRange.VALID, CodeRange.VALID, CodeRange.BROKEN, CodeRange.VALID, CodeRange.BROKEN};

        static CodeRange get(int codeRange) {
            return CODE_RANGES[codeRange];
        }

        static CodeRange getByteCodeRange(int codeRange, Encoding encoding) {
            return codeRange == TSCodeRange.get7Bit() && isUTF16Or32(encoding) ? CodeRange.VALID : BYTE_CODE_RANGES[codeRange];
        }

        static boolean equals(int codeRange, CodeRange codeRangeEnum) {
            return codeRange == codeRangeEnum.ordinal() || codeRangeEnum == VALID && isValidMultiByte(codeRange) || codeRangeEnum == BROKEN && isBrokenMultiByte(codeRange);
        }

        static {
            assert get(TSCodeRange.get7Bit()) == CodeRange.ASCII;
            assert get(TSCodeRange.get8Bit()) == CodeRange.LATIN_1;
            assert get(TSCodeRange.get16Bit()) == CodeRange.BMP;
            assert get(TSCodeRange.getValidFixedWidth()) == CodeRange.VALID;
            assert get(TSCodeRange.getBrokenFixedWidth()) == CodeRange.BROKEN;
            assert get(TSCodeRange.getValidMultiByte()) == CodeRange.VALID;
            assert get(TSCodeRange.getBrokenMultiByte()) == CodeRange.BROKEN;
            assert equals(TSCodeRange.get7Bit(), CodeRange.ASCII);
            assert equals(TSCodeRange.get8Bit(), CodeRange.LATIN_1);
            assert equals(TSCodeRange.get16Bit(), CodeRange.BMP);
            assert equals(TSCodeRange.getValidFixedWidth(), CodeRange.VALID);
            assert equals(TSCodeRange.getBrokenFixedWidth(), CodeRange.BROKEN);
            assert equals(TSCodeRange.getValidMultiByte(), CodeRange.VALID);
            assert equals(TSCodeRange.getBrokenMultiByte(), CodeRange.BROKEN);
            assert TSCodeRange.getUnknown() == CODE_RANGES.length;
        }
    }

    /**
     * Extended parameter type for the operations {@link ByteIndexOfStringNode},
     * {@link LastByteIndexOfStringNode} and {@link RegionEqualByteIndexNode}. These operations can
     * optionally perform a logical OR operation when matching their string parameters against each
     * other, in the following way:
     * <p>
     * Given a parameter {@link TruffleString} {@code a} and {@link WithMask} {@code b}, region
     * equality will be checked as shown in this exemplary method:
     *
     * <pre>
     * {@code
     * boolean regionEquals(TruffleString a, int fromIndexA, TruffleString.WithMask b, int fromIndexB) {
     *     for (int i = 0; i < length; i++) {
     *         if ((readRaw(a, fromIndexA + i) | readRaw(b.mask, i)) != readRaw(b.string, fromIndexB + i)) {
     *             return false;
     *         }
     *     }
     *     return true;
     * }
     * }
     * </pre>
     *
     * @see ByteIndexOfStringNode
     * @see LastByteIndexOfStringNode
     * @see RegionEqualByteIndexNode
     * @since 22.1
     */
    public static final class WithMask {

        final AbstractTruffleString string;
        @CompilationFinal(dimensions = 1) final byte[] mask;

        WithMask(AbstractTruffleString string, byte[] mask) {
            this.string = string;
            this.mask = mask;
        }

        /**
         * Node to create a new {@link WithMask} from a string and a byte array. See
         * {@code #execute(AbstractTruffleString, byte[], Encoding)} for details.
         *
         * @since 22.1
         */
        @ImportStatic(TStringGuards.class)
        @GeneratePackagePrivate
        @GenerateUncached
        public abstract static class CreateNode extends Node {

            CreateNode() {
            }

            /**
             * Creates a new {@link WithMask} from {@code a} and {@code mask}. {@code mask.length}
             * must be equal to the string's length in bytes. Cannot be used for UTF-16 or UTF-32
             * strings.
             *
             * @since 22.1
             */
            public abstract WithMask execute(AbstractTruffleString a, byte[] mask, Encoding expectedEncoding);

            @Specialization
            WithMask doCreate(AbstractTruffleString a, byte[] mask, Encoding expectedEncoding) {
                if (expectedEncoding == Encoding.UTF_16 || expectedEncoding == Encoding.UTF_32) {
                    throw InternalErrors.illegalArgument("use a CreateUTF16Node for UTF-16, and CreateUTF32Node for UTF-32");
                }
                a.checkEncoding(expectedEncoding);
                checkMaskLength(a, mask.length);
                assert isStride0(a);
                return new WithMask(a, Arrays.copyOf(mask, mask.length));
            }

            /**
             * Create a new {@link TruffleString.WithMask.CreateNode}.
             *
             * @since 22.1
             */
            public static TruffleString.WithMask.CreateNode create() {
                return TruffleStringFactory.WithMaskFactory.CreateNodeGen.create();
            }

            /**
             * Get the uncached version of {@link TruffleString.WithMask.CreateNode}.
             *
             * @since 22.1
             */
            public static TruffleString.WithMask.CreateNode getUncached() {
                return TruffleStringFactory.WithMaskFactory.CreateNodeGen.getUncached();
            }
        }

        /**
         * Shorthand for calling the uncached version of {@link CreateNode}.
         *
         * @since 22.1
         */
        public static WithMask createUncached(AbstractTruffleString a, byte[] mask, Encoding expectedEncoding) {
            return CreateNode.getUncached().execute(a, mask, expectedEncoding);
        }

        /**
         * Node to create a new {@link WithMask} from a UTF-16 string and a char array. See
         * {@code #execute(AbstractTruffleString, char[])} for details.
         *
         * @since 22.1
         */
        @ImportStatic(TStringGuards.class)
        @GeneratePackagePrivate
        @GenerateUncached
        public abstract static class CreateUTF16Node extends Node {

            CreateUTF16Node() {
            }

            /**
             * Creates a new {@link WithMask} from {@code a} and {@code mask}. {@code mask.length}
             * must be equal to the string's length in {@code char}s.
             *
             * @since 22.1
             */
            public abstract WithMask execute(AbstractTruffleString a, char[] mask);

            @Specialization
            WithMask doCreate(AbstractTruffleString a, char[] mask) {
                a.checkEncoding(Encoding.UTF_16);
                checkMaskLength(a, mask.length);
                byte[] maskBytes = new byte[a.length() << a.stride()];
                if (a.stride() == 0) {
                    TStringOps.arraycopyWithStrideCB(this, mask, 0, maskBytes, 0, 0, mask.length);
                } else {
                    TStringOps.arraycopyWithStrideCB(this, mask, 0, maskBytes, 0, 1, mask.length);
                }
                return new WithMask(a, maskBytes);
            }

            /**
             * Create a new {@link TruffleString.WithMask.CreateNode}.
             *
             * @since 22.1
             */
            public static TruffleString.WithMask.CreateUTF16Node create() {
                return TruffleStringFactory.WithMaskFactory.CreateUTF16NodeGen.create();
            }

            /**
             * Get the uncached version of {@link TruffleString.WithMask.CreateNode}.
             *
             * @since 22.1
             */
            public static TruffleString.WithMask.CreateUTF16Node getUncached() {
                return TruffleStringFactory.WithMaskFactory.CreateUTF16NodeGen.getUncached();
            }
        }

        /**
         * Shorthand for calling the uncached version of {@link CreateUTF16Node}.
         *
         * @since 22.1
         */
        public static WithMask createUTF16Uncached(AbstractTruffleString a, char[] mask) {
            return CreateUTF16Node.getUncached().execute(a, mask);
        }

        /**
         * Node to create a new {@link WithMask} from a UTF-32 string and an int array. See
         * {@code #execute(AbstractTruffleString, int[])} for details.
         *
         * @since 22.1
         */
        @ImportStatic(TStringGuards.class)
        @GeneratePackagePrivate
        @GenerateUncached
        public abstract static class CreateUTF32Node extends Node {

            CreateUTF32Node() {
            }

            /**
             * Creates a new {@link WithMask} from {@code a} and {@code mask}. {@code mask.length}
             * must be equal to the string's length in {@code int}s.
             *
             * @since 22.1
             */
            public abstract WithMask execute(AbstractTruffleString a, int[] mask);

            @Specialization
            WithMask doCreate(AbstractTruffleString a, int[] mask) {
                a.checkEncoding(Encoding.UTF_32);
                checkMaskLength(a, mask.length);
                byte[] maskBytes = new byte[a.length() << a.stride()];
                if (a.stride() == 0) {
                    TStringOps.arraycopyWithStrideIB(this, mask, 0, maskBytes, 0, 0, mask.length);
                } else if (a.stride() == 1) {
                    TStringOps.arraycopyWithStrideIB(this, mask, 0, maskBytes, 0, 1, mask.length);
                } else {
                    TStringOps.arraycopyWithStrideIB(this, mask, 0, maskBytes, 0, 2, mask.length);
                }
                return new WithMask(a, maskBytes);
            }

            /**
             * Create a new {@link TruffleString.WithMask.CreateNode}.
             *
             * @since 22.1
             */
            public static TruffleString.WithMask.CreateUTF32Node create() {
                return TruffleStringFactory.WithMaskFactory.CreateUTF32NodeGen.create();
            }

            /**
             * Get the uncached version of {@link TruffleString.WithMask.CreateNode}.
             *
             * @since 22.1
             */
            public static TruffleString.WithMask.CreateUTF32Node getUncached() {
                return TruffleStringFactory.WithMaskFactory.CreateUTF32NodeGen.getUncached();
            }
        }

        /**
         * Shorthand for calling the uncached version of {@link CreateUTF32Node}.
         *
         * @since 22.1
         */
        public static WithMask createUTF32Uncached(AbstractTruffleString a, int[] mask) {
            return CreateUTF32Node.getUncached().execute(a, mask);
        }

        private static void checkMaskLength(AbstractTruffleString string, int length) {
            if (length != string.length()) {
                throw InternalErrors.illegalArgument("mask length does not match string length!");
            }
        }
    }

    /**
     * Node to create a new {@link TruffleString} from a single codepoint.
     *
     * @since 22.1
     */
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class FromCodePointNode extends Node {

        FromCodePointNode() {
        }

        /**
         * Creates a new TruffleString from a given code point.
         *
         * @since 22.1
         */
        public abstract TruffleString execute(int codepoint, Encoding encoding);

        @Specialization
        static TruffleString fromCodePoint(int c, Encoding enc,
                        @Cached ConditionProfile bytesProfile,
                        @Cached ConditionProfile utf8Profile,
                        @Cached ConditionProfile utf16Profile,
                        @Cached ConditionProfile utf32Profile,
                        @Cached ConditionProfile exoticProfile,
                        @Cached ConditionProfile bmpProfile) {
            if (Integer.toUnsignedLong(c) > 0x10ffff) {
                throw InternalErrors.invalidCodePoint(c);
            }
            if (is7BitCompatible(enc) && c <= 0x7f) {
                return TStringConstants.getSingleByteAscii(enc.id, c);
            }
            if (is8BitCompatible(enc) && c <= 0xff) {
                assert isSupportedEncoding(enc);
                return TStringConstants.getSingleByte(enc.id, c);
            }
            if (bytesProfile.profile(isBytes(enc))) {
                if (c > 0xff) {
                    throw InternalErrors.invalidCodePoint(c);
                }
                return TStringConstants.getSingleByte(Encoding.BYTES.id, c);
            }
            final byte[] bytes;
            final int length;
            final int stride;
            final int codeRange;
            if (utf8Profile.profile(isUTF8(enc))) {
                assert c > 0x7f;
                if (Encodings.isUTF16Surrogate(c)) {
                    throw InternalErrors.invalidCodePoint(c);
                }
                bytes = Encodings.utf8Encode(c);
                length = bytes.length;
                stride = 0;
                codeRange = TSCodeRange.getValidMultiByte();
            } else if (utf16Profile.profile(isUTF16(enc))) {
                assert c > 0xff;
                bytes = new byte[c <= 0xffff ? 2 : 4];
                stride = 1;
                if (bmpProfile.profile(c <= 0xffff)) {
                    length = 1;
                    codeRange = Encodings.isUTF16Surrogate(c) ? TSCodeRange.getBrokenMultiByte() : TSCodeRange.get16Bit();
                    TStringOps.writeToByteArray(bytes, 1, 0, c);
                } else {
                    length = 2;
                    codeRange = TSCodeRange.getValidMultiByte();
                    Encodings.utf16EncodeSurrogatePair(c, bytes, 0);
                }
            } else if (utf32Profile.profile(isUTF32(enc))) {
                assert c > 0xff;
                if (Encodings.isUTF16Surrogate(c)) {
                    throw InternalErrors.invalidCodePoint(c);
                }
                boolean compact1 = c <= 0xffff;
                bytes = new byte[compact1 ? 2 : 4];
                length = 1;
                if (bmpProfile.profile(compact1)) {
                    stride = 1;
                    codeRange = TSCodeRange.get16Bit();
                    TStringOps.writeToByteArray(bytes, 1, 0, c);
                } else {
                    stride = 2;
                    codeRange = TSCodeRange.getValidFixedWidth();
                    TStringOps.writeToByteArray(bytes, 2, 0, c);
                }
            } else if (exoticProfile.profile(!isSupportedEncoding(enc))) {
                assert !isBytes(enc);
                JCodings.Encoding jCodingsEnc = JCodings.getInstance().get(enc.id);
                length = JCodings.getInstance().getCodePointLength(jCodingsEnc, c);
                stride = 0;
                codeRange = JCodings.getInstance().isSingleByte(jCodingsEnc) ? TSCodeRange.getValidFixedWidth() : TSCodeRange.getValidMultiByte();
                if (length < 1) {
                    throw InternalErrors.invalidCodePoint(c);
                }
                bytes = new byte[length];
                int ret = JCodings.getInstance().writeCodePoint(jCodingsEnc, c, bytes, 0);
                if (ret != length || JCodings.getInstance().getCodePointLength(jCodingsEnc, bytes, 0, length) != ret || JCodings.getInstance().readCodePoint(jCodingsEnc, bytes, 0, length) != c) {
                    throw InternalErrors.invalidCodePoint(c);
                }
            } else {
                assert isAscii(enc) && c > 0x7f || (isLatin1(enc) && c > 0xff);
                throw InternalErrors.invalidCodePoint(c);
            }
            return TruffleString.createFromByteArray(bytes, length, stride, enc.id, 1, codeRange);
        }

        /**
         * Create a new {@link FromCodePointNode}.
         *
         * @since 22.1
         */
        public static FromCodePointNode create() {
            return TruffleStringFactory.FromCodePointNodeGen.create();
        }

        /**
         * Get the uncached version of {@link FromCodePointNode}.
         *
         * @since 22.1
         */
        public static FromCodePointNode getUncached() {
            return TruffleStringFactory.FromCodePointNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link FromCodePointNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public static TruffleString fromCodePointUncached(int codepoint, Encoding encoding) {
        return FromCodePointNode.getUncached().execute(codepoint, encoding);
    }

    /**
     * Node to create a new {@link TruffleString} from a {@code long} value. See
     * {@link #execute(long, TruffleString.Encoding, boolean)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class FromLongNode extends Node {

        FromLongNode() {
        }

        /**
         * Creates a 10's complement string from the given long value, using ASCII digits (0x30 -
         * 0x39). This operation does not support encodings that are incompatible with the ASCII
         * character set. If {@code lazy} is true, the string representation of the number is
         * computed lazily the first time it is needed.
         *
         * @since 22.1
         */
        public abstract TruffleString execute(long value, Encoding encoding, boolean lazy);

        @Specialization(guards = {"is7BitCompatible(enc)", "lazy"})
        static TruffleString doLazy(long value, Encoding enc, @SuppressWarnings("unused") boolean lazy) {
            CompilerAsserts.partialEvaluationConstant(lazy);
            return TruffleString.createLazyLong(value, enc.id);
        }

        @Specialization(guards = {"is7BitCompatible(enc)", "!lazy"})
        static TruffleString doEager(long value, Encoding enc, @SuppressWarnings("unused") boolean lazy) {
            CompilerAsserts.partialEvaluationConstant(lazy);
            int length = NumberConversion.stringLengthLong(value);
            return TruffleString.createFromByteArray(NumberConversion.longToString(value, length), length, 0, enc.id, length, TSCodeRange.get7Bit());
        }

        @Specialization(guards = "!is7BitCompatible(enc)")
        static TruffleString unsupported(@SuppressWarnings("unused") long value, Encoding enc, @SuppressWarnings("unused") boolean lazy) {
            CompilerAsserts.partialEvaluationConstant(lazy);
            throw InternalErrors.unsupportedOperation(nonAsciiCompatibleMessage(enc));
        }

        @TruffleBoundary
        private static String nonAsciiCompatibleMessage(Encoding enc) {
            return "Encoding " + enc + " is not ASCII-compatible";
        }

        /**
         * Create a new {@link FromLongNode}.
         *
         * @since 22.1
         */
        public static FromLongNode create() {
            return TruffleStringFactory.FromLongNodeGen.create();
        }

        /**
         * Get the uncached version of {@link FromLongNode}.
         *
         * @since 22.1
         */
        public static FromLongNode getUncached() {
            return TruffleStringFactory.FromLongNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link FromLongNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public static TruffleString fromLongUncached(long value, Encoding encoding, boolean lazy) {
        return FromLongNode.getUncached().execute(value, encoding, lazy);
    }

    /**
     * Node to create a new {@link TruffleString} from a byte array. See
     * {@link #execute(byte[], int, int, TruffleString.Encoding, boolean)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class FromByteArrayNode extends Node {

        FromByteArrayNode() {
        }

        /**
         * Creates a new {@link TruffleString} from a byte array. See
         * {@link #execute(byte[], int, int, TruffleString.Encoding, boolean)} for details.
         *
         * @since 22.1
         */
        public final TruffleString execute(byte[] value, Encoding encoding) {
            return execute(value, encoding, true);
        }

        /**
         * Creates a new {@link TruffleString} from a byte array. See
         * {@link #execute(byte[], int, int, TruffleString.Encoding, boolean)} for details.
         *
         * @since 22.1
         */
        public final TruffleString execute(byte[] value, Encoding encoding, boolean copy) {
            return execute(value, 0, value.length, encoding, copy);
        }

        /**
         * Creates a new {@link TruffleString} from a byte array. The array content is assumed to be
         * encoded in the given encoding already. This operation allows non-copying string creation,
         * i.e. the array parameter can be used directly by passing {@code copy = false}. Caution:
         * {@link TruffleString} assumes the array to be immutable, do not modify the byte array
         * after passing it to the non-copying variant of this operation!
         *
         * @since 22.1
         */
        public abstract TruffleString execute(byte[] value, int byteOffset, int byteLength, Encoding encoding, boolean copy);

        @Specialization
        static TruffleString fromByteArray(byte[] value, int byteOffset, int byteLength, Encoding enc, boolean copy,
                        @Cached TStringInternalNodes.FromBufferWithStringCompactionNode fromBufferWithStringCompactionNode) {
            checkArrayRange(value, byteOffset, byteLength);
            return fromBufferWithStringCompactionNode.execute(value, byteOffset, byteLength, enc.id, copy, true);
        }

        /**
         * Create a new {@link FromByteArrayNode}.
         *
         * @since 22.1
         */
        public static FromByteArrayNode create() {
            return TruffleStringFactory.FromByteArrayNodeGen.create();
        }

        /**
         * Get the uncached version of {@link FromByteArrayNode}.
         *
         * @since 22.1
         */
        public static FromByteArrayNode getUncached() {
            return TruffleStringFactory.FromByteArrayNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link FromByteArrayNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public static TruffleString fromByteArrayUncached(byte[] value, Encoding encoding) {
        return FromByteArrayNode.getUncached().execute(value, encoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link FromByteArrayNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public static TruffleString fromByteArrayUncached(byte[] value, Encoding encoding, boolean copy) {
        return FromByteArrayNode.getUncached().execute(value, encoding, copy);
    }

    /**
     * Shorthand for calling the uncached version of {@link FromByteArrayNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public static TruffleString fromByteArrayUncached(byte[] value, int byteOffset, int byteLength, Encoding encoding, boolean copy) {
        return FromByteArrayNode.getUncached().execute(value, byteOffset, byteLength, encoding, copy);
    }

    /**
     * Node to create a new UTF-16 {@link TruffleString} from a char array.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class FromCharArrayUTF16Node extends Node {

        FromCharArrayUTF16Node() {
        }

        /**
         * Creates a UTF-16 {@link TruffleString} from a char array.
         *
         * @since 22.1
         */
        public final TruffleString execute(char[] value) {
            return execute(value, 0, value.length);
        }

        /**
         * Creates a UTF-16 {@link TruffleString} from a char-array.
         *
         * @since 22.1
         */
        public abstract TruffleString execute(char[] value, int charOffset, int charLength);

        @Specialization
        TruffleString doNonEmpty(char[] value, int charOffset, int charLength,
                        @Cached ConditionProfile utf16CompactProfile) {
            checkArrayRange(value.length, charOffset, charLength);
            if (charLength == 0) {
                return Encoding.UTF_16.getEmpty();
            }
            if (charLength == 1 && value[charOffset] <= 0xff) {
                return TStringConstants.getSingleByte(Encodings.getUTF16(), value[charOffset]);
            }
            int offsetV = charOffset << 1;
            if (value.length > TStringConstants.MAX_ARRAY_SIZE_S1 || offsetV < 0) {
                throw InternalErrors.outOfMemory();
            }
            long attrs = TStringOps.calcStringAttributesUTF16C(this, value, offsetV, charLength);
            final int codePointLength = StringAttributes.getCodePointLength(attrs);
            final int codeRange = StringAttributes.getCodeRange(attrs);
            final int stride = Stride.fromCodeRangeUTF16(codeRange);
            final byte[] array = new byte[charLength << stride];
            if (utf16CompactProfile.profile(stride == 0)) {
                TStringOps.arraycopyWithStrideCB(this, value, offsetV, array, 0, 0, charLength);
            } else {
                TStringOps.arraycopyWithStrideCB(this, value, offsetV, array, 0, 1, charLength);
            }
            return TruffleString.createFromArray(array, 0, charLength, stride, Encodings.getUTF16(), codePointLength, codeRange);
        }

        /**
         * Create a new {@link FromCharArrayUTF16Node}.
         *
         * @since 22.1
         */
        public static FromCharArrayUTF16Node create() {
            return TruffleStringFactory.FromCharArrayUTF16NodeGen.create();
        }

        /**
         * Get the uncached version of {@link FromCharArrayUTF16Node}.
         *
         * @since 22.1
         */
        public static FromCharArrayUTF16Node getUncached() {
            return TruffleStringFactory.FromCharArrayUTF16NodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link FromCharArrayUTF16Node}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public static TruffleString fromCharArrayUTF16Uncached(char[] value) {
        return FromCharArrayUTF16Node.getUncached().execute(value);
    }

    /**
     * Shorthand for calling the uncached version of {@link FromCharArrayUTF16Node}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public static TruffleString fromCharArrayUTF16Uncached(char[] value, int charOffset, int charLength) {
        return FromCharArrayUTF16Node.getUncached().execute(value, charOffset, charLength);
    }

    /**
     * Node to create a new {@link TruffleString} from a Java string. See
     * {@link #execute(String, int, int, TruffleString.Encoding, boolean)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class FromJavaStringNode extends Node {

        FromJavaStringNode() {
        }

        /**
         * Creates a {@link TruffleString} from a Java string, re-using its internal byte array if
         * possible.
         *
         * @since 22.1
         */
        public final TruffleString execute(String value, Encoding encoding) {
            return execute(value, 0, value.length(), encoding, true);
        }

        /**
         * Creates a {@link TruffleString} from a given region in a Java string, re-using its
         * internal byte array if possible and the region covers the entire string. If {@code copy}
         * is {@code false}, the Java string's internal byte array will be re-used even if the
         * region does not cover the entire string. Note that this will keep the Java string's byte
         * array alive as long as the resulting {@link TruffleString} is alive.
         *
         * @since 22.1
         */
        public abstract TruffleString execute(String value, int charOffset, int length, Encoding encoding, boolean copy);

        @Specialization
        static TruffleString doUTF16(String javaString, int charOffset, int length, Encoding encoding, final boolean copy,
                        @Cached TStringInternalNodes.FromJavaStringUTF16Node fromJavaStringUTF16Node,
                        @Cached SwitchEncodingNode switchEncodingNode,
                        @Cached ConditionProfile utf16Profile) {
            if (javaString.isEmpty()) {
                return Encoding.UTF_16.getEmpty();
            }
            TruffleString utf16String = fromJavaStringUTF16Node.execute(javaString, charOffset, length, copy);
            if (utf16Profile.profile(encoding == Encoding.UTF_16)) {
                return utf16String;
            }
            return switchEncodingNode.execute(utf16String, encoding);
        }

        /**
         * Create a new {@link FromJavaStringNode}.
         *
         * @since 22.1
         */
        public static FromJavaStringNode create() {
            return TruffleStringFactory.FromJavaStringNodeGen.create();
        }

        /**
         * Get the uncached version of {@link FromJavaStringNode}.
         *
         * @since 22.1
         */
        public static FromJavaStringNode getUncached() {
            return TruffleStringFactory.FromJavaStringNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link FromJavaStringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public static TruffleString fromJavaStringUncached(String s, Encoding encoding) {
        return FromJavaStringNode.getUncached().execute(s, encoding);
    }

    /**
     * Shorthand for calling the uncached version of {@link FromJavaStringNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public static TruffleString fromJavaStringUncached(String s, int charOffset, int length, Encoding encoding, boolean copy) {
        return FromJavaStringNode.getUncached().execute(s, charOffset, length, encoding, copy);
    }

    /**
     * Node to create a new UTF-32 {@link TruffleString} from an int-array.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class FromIntArrayUTF32Node extends Node {

        FromIntArrayUTF32Node() {
        }

        /**
         * Creates a UTF-32 {@link TruffleString} from an int-array.
         *
         * @since 22.1
         */
        public final TruffleString execute(int[] value) {
            return execute(value, 0, value.length);
        }

        /**
         * Creates a UTF-32 {@link TruffleString} from an int-array.
         *
         * @since 22.1
         */
        public abstract TruffleString execute(int[] value, int intOffset, int intLength);

        @Specialization
        TruffleString doNonEmpty(int[] value, int intOffset, int length,
                        @Cached ConditionProfile utf32Compact0Profile,
                        @Cached ConditionProfile utf32Compact1Profile) {
            checkArrayRange(value.length, intOffset, length);
            if (length == 0) {
                return Encoding.UTF_32.getEmpty();
            }
            if (length == 1 && value[intOffset] <= 0xff) {
                return TStringConstants.getSingleByte(Encodings.getUTF32(), value[intOffset]);
            }
            int offsetV = intOffset << 2;
            if (length > TStringConstants.MAX_ARRAY_SIZE_S2 || offsetV < 0) {
                throw InternalErrors.outOfMemory();
            }
            final int codeRange = TStringOps.calcStringAttributesUTF32I(this, value, offsetV, length);
            final int stride = Stride.fromCodeRangeUTF32(codeRange);
            final byte[] array = new byte[length << stride];
            if (utf32Compact0Profile.profile(stride == 0)) {
                TStringOps.arraycopyWithStrideIB(this, value, offsetV, array, 0, 0, length);
            } else if (utf32Compact1Profile.profile(stride == 1)) {
                TStringOps.arraycopyWithStrideIB(this, value, offsetV, array, 0, 1, length);
            } else {
                TStringOps.arraycopyWithStrideIB(this, value, offsetV, array, 0, 2, length);
            }
            return TruffleString.createFromArray(array, 0, length, stride, Encodings.getUTF32(), length, codeRange);
        }

        /**
         * Create a new {@link FromIntArrayUTF32Node}.
         *
         * @since 22.1
         */
        public static FromIntArrayUTF32Node create() {
            return TruffleStringFactory.FromIntArrayUTF32NodeGen.create();
        }

        /**
         * Get the uncached version of {@link FromIntArrayUTF32Node}.
         *
         * @since 22.1
         */
        public static FromIntArrayUTF32Node getUncached() {
            return TruffleStringFactory.FromIntArrayUTF32NodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link FromIntArrayUTF32Node}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public static TruffleString fromIntArrayUTF32Uncached(int[] value) {
        return FromIntArrayUTF32Node.getUncached().execute(value);
    }

    /**
     * Shorthand for calling the uncached version of {@link FromIntArrayUTF32Node}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public static TruffleString fromIntArrayUTF32Uncached(int[] value, int intOffset, int intLength) {
        return FromIntArrayUTF32Node.getUncached().execute(value, intOffset, intLength);
    }

    /**
     * Node to create a new {@link TruffleString} from an interop object representing a native
     * pointer. See {@link #execute(Object, int, int, TruffleString.Encoding, boolean)} for details.
     *
     * @since 22.1
     */
    @ImportStatic({TStringGuards.class, TStringAccessor.class})
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class FromNativePointerNode extends Node {

        FromNativePointerNode() {
        }

        /**
         * Create a new {@link TruffleString} from an interop object representing a native pointer (
         * {@code isPointer(pointerObject)} must return {@code true}). The pointer is immediately
         * unboxed with ({@code asPointer(pointerObject)}) and saved in the {@link TruffleString}
         * instance, i.e. {@link TruffleString} assumes that the pointer address does not change.
         * The pointer's content is assumed to be encoded in the given encoding already. If
         * {@code copy} is {@code false}, the native pointer is used directly as the new string's
         * backing storage. Caution: {@link TruffleString} assumes the pointer's content to be
         * immutable, do not modify the pointer's content after passing it to this operation!
         *
         * <p>
         * <b>WARNING:</b> {@link TruffleString} cannot reason about the lifetime of the native
         * pointer, so it is up to the user to <b>make sure that the native pointer is valid to
         * access and not freed as long the {@code pointerObject} is alive</b> (if {@code copy} is
         * {@code false}). To help with this the TruffleString keeps a reference to the given
         * {@code pointerObject}, so the {@code pointerObject} is kept alive at least as long as the
         * TruffleString is used. In order to be able to use the string past the native pointer's
         * life time, convert it to a managed string via {@link AsManagedNode} <b>before the native
         * pointer is freed</b>.
         * </p>
         * <p>
         * If {@code copy} is {@code true}, the pointer's contents are copied to a Java byte array,
         * and the pointer can be freed safely after the operation completes.
         * </p>
         * This operation requires native access permissions
         * ({@code TruffleLanguage.Env#isNativeAccessAllowed()}).
         *
         * @since 22.1
         */
        public abstract TruffleString execute(Object pointerObject, int byteOffset, int byteLength, Encoding encoding, boolean copy);

        @Specialization
        TruffleString fromNativePointer(Object pointerObject, int byteOffset, int byteLength, Encoding enc, boolean copy,
                        @Cached(value = "createInteropLibrary()", uncached = "getUncachedInteropLibrary()") Node interopLibrary,
                        @Cached TStringInternalNodes.FromNativePointerNode fromNativePointerNode,
                        @Cached TStringInternalNodes.FromBufferWithStringCompactionNode fromBufferWithStringCompactionNode) {
            NativePointer pointer = NativePointer.create(this, pointerObject, interopLibrary, byteOffset);
            if (copy) {
                return fromBufferWithStringCompactionNode.execute(pointer, byteOffset, byteLength, enc.id, true, true);
            }
            return fromNativePointerNode.execute(pointer, byteOffset, byteLength, enc.id, true);
        }

        /**
         * Create a new {@link FromNativePointerNode}.
         *
         * @since 22.1
         */
        public static FromNativePointerNode create() {
            return TruffleStringFactory.FromNativePointerNodeGen.create();
        }

        /**
         * Get the uncached version of {@link FromNativePointerNode}.
         *
         * @since 22.1
         */
        public static FromNativePointerNode getUncached() {
            return TruffleStringFactory.FromNativePointerNodeGen.getUncached();
        }
    }

    /**
     * Shorthand for calling the uncached version of {@link FromNativePointerNode}.
     *
     * @since 22.1
     */
    @TruffleBoundary
    public static TruffleString fromNativePointerUncached(Object pointerObject, int byteOffset, int byteLength, Encoding encoding, boolean copy) {
        return FromNativePointerNode.getUncached().execute(pointerObject, byteOffset, byteLength, encoding, copy);
    }

    /**
     * Node to get the given {@link AbstractTruffleString} as a {@link TruffleString}. See
     * {@link #execute(AbstractTruffleString, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class AsTruffleStringNode extends Node {

        AsTruffleStringNode() {
        }

        /**
         * If the given string is already a {@link TruffleString}, return it. If it is a
         * {@link MutableTruffleString}, create a new {@link TruffleString}, copying the mutable
         * string's contents.
         *
         * @since 22.1
         */
        public abstract TruffleString execute(AbstractTruffleString value, Encoding expectedEncoding);

        @Specialization
        static TruffleString immutable(TruffleString a, Encoding expectedEncoding) {
            a.checkEncoding(expectedEncoding);
            return a;
        }

        @Specialization
        static TruffleString fromMutableString(MutableTruffleString a, Encoding expectedEncoding,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringInternalNodes.FromBufferWithStringCompactionKnownAttributesNode fromBufferWithStringCompactionNode) {
            a.checkEncoding(expectedEncoding);
            return fromBufferWithStringCompactionNode.execute(a.data(), a.offset(), a.length() << a.stride(), expectedEncoding.id, getCodePointLengthNode.execute(a), getCodeRangeNode.execute(a));
        }

        /**
         * Create a new {@link AsTruffleStringNode}.
         *
         * @since 22.1
         */
        public static AsTruffleStringNode create() {
            return TruffleStringFactory.AsTruffleStringNodeGen.create();
        }

        /**
         * Get the uncached version of {@link AsTruffleStringNode}.
         *
         * @since 22.1
         */
        public static AsTruffleStringNode getUncached() {
            return TruffleStringFactory.AsTruffleStringNodeGen.getUncached();
        }
    }

    /**
     * Node to get the given {@link AbstractTruffleString} as a managed {@link TruffleString},
     * meaning that the resulting string's backing memory is not a native pointer. See
     * {@link #execute(AbstractTruffleString, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class AsManagedNode extends Node {

        AsManagedNode() {
        }

        /**
         * If the given string is already a managed (i.e. not backed by a native pointer) string,
         * return it. Otherwise, copy the string's native pointer content into a Java byte array and
         * return a new string backed by the byte array.
         *
         * @since 22.1
         */
        public abstract TruffleString execute(AbstractTruffleString a, Encoding expectedEncoding);

        @Specialization(guards = "!a.isNative()")
        static TruffleString managedImmutable(TruffleString a, Encoding expectedEncoding) {
            a.checkEncoding(expectedEncoding);
            assert !(a.data() instanceof NativePointer);
            return a;
        }

        @Specialization(guards = "a.isNative() || a.isMutable()")
        static TruffleString nativeOrMutable(AbstractTruffleString a, Encoding expectedEncoding,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringInternalNodes.FromBufferWithStringCompactionKnownAttributesNode fromBufferWithStringCompactionNode) {
            a.checkEncoding(expectedEncoding);
            Object data = a.data();
            assert data instanceof byte[] || data instanceof NativePointer;
            return fromBufferWithStringCompactionNode.execute(data, a.offset(), a.length() << a.stride(), expectedEncoding.id, getCodePointLengthNode.execute(a), getCodeRangeNode.execute(a));
        }

        /**
         * Create a new {@link AsManagedNode}.
         *
         * @since 22.1
         */
        public static AsManagedNode create() {
            return TruffleStringFactory.AsManagedNodeGen.create();
        }

        /**
         * Get the uncached version of {@link AsManagedNode}.
         *
         * @since 22.1
         */
        public static AsManagedNode getUncached() {
            return TruffleStringFactory.AsManagedNodeGen.getUncached();
        }
    }

    @ImportStatic(TStringGuards.class)
    @GenerateUncached
    abstract static class ToIndexableNode extends Node {

        abstract Object execute(AbstractTruffleString a, Object data);

        @Specialization
        static byte[] doByteArray(@SuppressWarnings("unused") AbstractTruffleString a, byte[] data) {
            return data;
        }

        @Specialization(guards = "isSupportedEncoding(a)")
        static NativePointer doNativeSupported(@SuppressWarnings("unused") AbstractTruffleString a, NativePointer data) {
            return data;
        }

        @Specialization(guards = "!isSupportedEncoding(a)")
        static NativePointer doNativeUnsupported(@SuppressWarnings("unused") AbstractTruffleString a, NativePointer data,
                        @Cached ConditionProfile materializeProfile) {
            data.materializeByteArray(a, materializeProfile);
            return data;
        }

        @Specialization
        byte[] doLazyConcat(AbstractTruffleString a, @SuppressWarnings("unused") LazyConcat data) {
            // note: the write to a.data is racy, and we deliberately read it from the TString
            // object again after the race to de-duplicate simultaneously generated arrays
            a.setData(LazyConcat.flatten(this, (TruffleString) a));
            return (byte[]) a.data();
        }

        @Specialization
        static byte[] doLazyLong(AbstractTruffleString a, LazyLong data,
                        @Cached ConditionProfile materializeProfile) {
            // same pattern as in #doLazyConcat: racy write to data.bytes and read the result again
            // to de-duplicate
            if (materializeProfile.profile(data.bytes == null)) {
                data.setBytes((TruffleString) a, NumberConversion.longToString(data.value, a.length()));
            }
            return data.bytes;
        }

        static ToIndexableNode getUncached() {
            return TruffleStringFactory.ToIndexableNodeGen.getUncached();
        }
    }

    /**
     * Node to force materialization of any lazy internal data. Use this node to avoid
     * materialization code inside loops, e.g. when iterating over a string's code points or bytes.
     *
     * @since 22.1
     */
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class MaterializeNode extends Node {

        MaterializeNode() {
        }

        /**
         * Forces materialization of any lazy internal data. Use this node to avoid materialization
         * code inside loops, e.g. when iterating over a string's code points or bytes.
         *
         * @since 22.1
         */
        public abstract void execute(AbstractTruffleString a, Encoding expectedEncoding);

        @Specialization
        static void doMaterialize(AbstractTruffleString a, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode) {
            a.checkEncoding(expectedEncoding);
            toIndexableNode.execute(a, a.data());
            assert a.isMaterialized();
        }

        /**
         * Create a new {@link MaterializeNode}.
         *
         * @since 22.1
         */
        public static MaterializeNode create() {
            return TruffleStringFactory.MaterializeNodeGen.create();
        }

        /**
         * Get the uncached version of {@link MaterializeNode}.
         *
         * @since 22.1
         */
        public static MaterializeNode getUncached() {
            return TruffleStringFactory.MaterializeNodeGen.getUncached();
        }
    }

    /**
     * Node to get a string's {@link CodeRange}.
     *
     * @since 22.1
     */
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class GetCodeRangeNode extends Node {

        GetCodeRangeNode() {
        }

        /**
         * Get the string's {@link CodeRange}.
         *
         * @since 22.1
         */
        public abstract CodeRange execute(AbstractTruffleString a, Encoding expectedEncoding);

        @Specialization
        static CodeRange getCodeRange(AbstractTruffleString a, Encoding expectedEncoding,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode) {
            a.checkEncoding(expectedEncoding);
            return CodeRange.get(getCodeRangeNode.execute(a));
        }

        /**
         * Create a new {@link GetCodeRangeNode}.
         *
         * @since 22.1
         */
        public static GetCodeRangeNode create() {
            return TruffleStringFactory.GetCodeRangeNodeGen.create();
        }

        /**
         * Get the uncached version of {@link GetCodeRangeNode}.
         *
         * @since 22.1
         */
        public static GetCodeRangeNode getUncached() {
            return TruffleStringFactory.GetCodeRangeNodeGen.getUncached();
        }
    }

    /**
     * Node to get a string's "byte-based" {@link CodeRange}. See
     * {@link #execute(AbstractTruffleString, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class GetByteCodeRangeNode extends Node {

        GetByteCodeRangeNode() {
        }

        /**
         * Get the string's "byte-based" {@link CodeRange}. This differs from
         * {@link GetCodeRangeNode} in the following way:
         * <ul>
         * <li>A string is only considered to be in the {@link CodeRange#ASCII} code range if its
         * encoding is byte-based, so {@link Encoding#UTF_16} and {@link Encoding#UTF_32} cannot be
         * {@link CodeRange#ASCII}.</li>
         * <li>{@link CodeRange#LATIN_1} and {@link CodeRange#BMP} are mapped to
         * {@link CodeRange#VALID}</li>.
         * </ul>
         * The return value is always one of {@link CodeRange#ASCII}, {@link CodeRange#VALID} or
         * {@link CodeRange#BROKEN}.
         *
         * @since 22.1
         */
        public abstract CodeRange execute(AbstractTruffleString a, Encoding expectedEncoding);

        @Specialization
        static CodeRange getCodeRange(AbstractTruffleString a, Encoding expectedEncoding,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode) {
            a.checkEncoding(expectedEncoding);
            return CodeRange.getByteCodeRange(getCodeRangeNode.execute(a), expectedEncoding);
        }

        /**
         * Create a new {@link GetByteCodeRangeNode}.
         *
         * @since 22.1
         */
        public static GetByteCodeRangeNode create() {
            return TruffleStringFactory.GetByteCodeRangeNodeGen.create();
        }

        /**
         * Get the uncached version of {@link GetByteCodeRangeNode}.
         *
         * @since 22.1
         */
        public static GetByteCodeRangeNode getUncached() {
            return TruffleStringFactory.GetByteCodeRangeNodeGen.getUncached();
        }
    }

    /**
     * Node to check if a string's code range is equal to the given {@link CodeRange}. See
     * {@link #execute(AbstractTruffleString, TruffleString.CodeRange)} for details.
     *
     * @since 22.1
     */
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class CodeRangeEqualsNode extends Node {

        CodeRangeEqualsNode() {
        }

        /**
         * Returns {@code true} if the string's code range is equal to the given {@link CodeRange}.
         * Use this operation when caching code range instances, e.g.:
         *
         * <pre>
         * {@code
         * &#64;Specialization(guards = "codeRangeEqualsNode.execute(a, cachedCodeRange)")
         * static void someOperation(TString a,
         *              &#64;Cached TruffleString.GetCodeRangeNode getCodeRangeNode,
         *              &#64;Cached TruffleString.CodeRangeEqualsNode codeRangeEqualsNode,
         *              &#64;Cached("getCodeRangeNode.execute(a)") CodeRange cachedCodeRange) {
         *      // ...
         * }
         * }
         * </pre>
         *
         * @since 22.1
         */
        public abstract boolean execute(AbstractTruffleString a, CodeRange codeRange);

        @Specialization
        static boolean codeRangeEquals(AbstractTruffleString a, CodeRange codeRange,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode) {
            return CodeRange.equals(getCodeRangeNode.execute(a), codeRange);
        }

        /**
         * Create a new {@link CodeRangeEqualsNode}.
         *
         * @since 22.1
         */
        public static CodeRangeEqualsNode create() {
            return TruffleStringFactory.CodeRangeEqualsNodeGen.create();
        }

        /**
         * Get the uncached version of {@link CodeRangeEqualsNode}.
         *
         * @since 22.1
         */
        public static CodeRangeEqualsNode getUncached() {
            return TruffleStringFactory.CodeRangeEqualsNodeGen.getUncached();
        }
    }

    /**
     * Node to check if a string is encoded correctly.
     *
     * @since 22.1
     */
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class IsValidNode extends Node {

        IsValidNode() {
        }

        /**
         * Returns {@code true} if the string encoded correctly.
         *
         * @since 22.1
         */
        public abstract boolean execute(AbstractTruffleString a, Encoding expectedEncoding);

        @Specialization
        static boolean isValid(AbstractTruffleString a, Encoding expectedEncoding,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode) {
            a.checkEncoding(expectedEncoding);
            int codeRange = getCodeRangeNode.execute(a);
            return !isBrokenMultiByte(codeRange) && !isBrokenFixedWidth(codeRange);
        }

        /**
         * Create a new {@link IsValidNode}.
         *
         * @since 22.1
         */
        public static IsValidNode create() {
            return TruffleStringFactory.IsValidNodeGen.create();
        }

        /**
         * Get the uncached version of {@link IsValidNode}.
         *
         * @since 22.1
         */
        public static IsValidNode getUncached() {
            return TruffleStringFactory.IsValidNodeGen.getUncached();
        }
    }

    /**
     * Node to get the number of codepoints in a string.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class CodePointLengthNode extends Node {

        CodePointLengthNode() {
        }

        /**
         * Return the number of codepoints in the string.
         * <p>
         * If the string is not encoded correctly (if its coderange is {@link CodeRange#BROKEN}),
         * every broken minimum-length sequence in the encoding (4 bytes for UTF-32, 2 bytes for
         * UTF-16, 1 byte for other encodings) adds 1 to the length.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, Encoding expectedEncoding);

        @Specialization
        static int get(AbstractTruffleString a, Encoding expectedEncoding,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode) {
            a.checkEncoding(expectedEncoding);
            return getCodePointLengthNode.execute(a);
        }

        /**
         * Create a new {@link CodePointLengthNode}.
         *
         * @since 22.1
         */
        public static CodePointLengthNode create() {
            return TruffleStringFactory.CodePointLengthNodeGen.create();
        }

        /**
         * Get the uncached version of {@link CodePointLengthNode}.
         *
         * @since 22.1
         */
        public static CodePointLengthNode getUncached() {
            return TruffleStringFactory.CodePointLengthNodeGen.getUncached();
        }
    }

    /**
     * Node to get a string's hash code. See
     * {@link #execute(AbstractTruffleString, TruffleString.Encoding)} for details.
     *
     * @see TruffleString#hashCode()
     * @since 22.1
     */
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class HashCodeNode extends Node {

        HashCodeNode() {
        }

        /**
         * Returns the string's hash code. The hash is dependent on the string's encoding, make sure
         * to convert strings to a common encoding before comparing their hash codes!
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, Encoding expectedEncoding);

        @Specialization
        static int calculateHash(AbstractTruffleString a, Encoding expectedEncoding,
                        @Cached ConditionProfile cacheMiss,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringOpsNodes.CalculateHashCodeNode calculateHashCodeNode) {
            a.checkEncoding(expectedEncoding);
            int h = a.hashCode;
            if (cacheMiss.profile(h == 0)) {
                h = calculateHashCodeNode.execute(a, toIndexableNode.execute(a, a.data()));
                if (h == 0) {
                    h--;
                }
                a.hashCode = h;
            }
            return h;
        }

        /**
         * Create a new {@link HashCodeNode}.
         *
         * @since 22.1
         */
        public static HashCodeNode create() {
            return TruffleStringFactory.HashCodeNodeGen.create();
        }

        /**
         * Get the uncached version of {@link HashCodeNode}.
         *
         * @since 22.1
         */
        public static HashCodeNode getUncached() {
            return TruffleStringFactory.HashCodeNodeGen.getUncached();
        }
    }

    /**
     * Node to read a single byte from a string.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ReadByteNode extends Node {

        ReadByteNode() {
        }

        /**
         * Read a single byte from a string.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, int byteIndex, Encoding expectedEncoding);

        @Specialization
        static int doRead(AbstractTruffleString a, int i, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.ReadByteNode readByteNode) {
            a.checkEncoding(expectedEncoding);
            Object arrayA = toIndexableNode.execute(a, a.data());
            return readByteNode.execute(a, arrayA, i, expectedEncoding.id);
        }

        /**
         * Create a new {@link ReadByteNode}.
         *
         * @since 22.1
         */
        public static ReadByteNode create() {
            return TruffleStringFactory.ReadByteNodeGen.create();
        }

        /**
         * Get the uncached version of {@link ReadByteNode}.
         *
         * @since 22.1
         */
        public static ReadByteNode getUncached() {
            return TruffleStringFactory.ReadByteNodeGen.getUncached();
        }
    }

    /**
     * Node to read a single char from a UTF-16 string.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ReadCharUTF16Node extends Node {

        ReadCharUTF16Node() {
        }

        /**
         * Read a single char from a UTF-16 string.
         *
         * @since 22.1
         */
        public abstract char execute(AbstractTruffleString a, int charIndex);

        @Specialization
        static char doRead(AbstractTruffleString a, int i,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached ConditionProfile utf16S0Profile) {
            a.checkEncoding(Encoding.UTF_16);
            a.boundsCheckRaw(i);
            Object arrayA = toIndexableNode.execute(a, a.data());
            if (utf16S0Profile.profile(isStride0(a))) {
                return (char) TStringOps.readS0(a, arrayA, i);
            } else {
                assert isStride1(a);
                return TStringOps.readS1(a, arrayA, i);
            }
        }

        /**
         * Create a new {@link ReadCharUTF16Node}.
         *
         * @since 22.1
         */
        public static ReadCharUTF16Node create() {
            return TruffleStringFactory.ReadCharUTF16NodeGen.create();
        }

        /**
         * Get the uncached version of {@link ReadCharUTF16Node}.
         *
         * @since 22.1
         */
        public static ReadCharUTF16Node getUncached() {
            return TruffleStringFactory.ReadCharUTF16NodeGen.getUncached();
        }
    }

    /**
     * Node to get the number of bytes occupied by the codepoint starting at a given byte index. See
     * {@link #execute(AbstractTruffleString, int, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ByteLengthOfCodePointNode extends Node {

        ByteLengthOfCodePointNode() {
        }

        /**
         * Get the number of bytes occupied by the codepoint starting at {@code byteIndex}.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, int byteIndex, Encoding expectedEncoding);

        @Specialization
        static int translate(AbstractTruffleString a, int byteIndex, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringInternalNodes.RawLengthOfCodePointNode rawLengthOfCodePointNode) {
            a.checkEncoding(expectedEncoding);
            int rawIndex = rawIndex(byteIndex, expectedEncoding);
            a.boundsCheckRaw(rawIndex);
            Object arrayA = toIndexableNode.execute(a, a.data());
            int codeRangeA = getCodeRangeNode.execute(a);
            return rawLengthOfCodePointNode.execute(a, arrayA, codeRangeA, rawIndex) << expectedEncoding.naturalStride;
        }

        /**
         * Create a new {@link ByteLengthOfCodePointNode}.
         *
         * @since 22.1
         */
        public static ByteLengthOfCodePointNode create() {
            return TruffleStringFactory.ByteLengthOfCodePointNodeGen.create();
        }

        /**
         * Get the uncached version of {@link ByteLengthOfCodePointNode}.
         *
         * @since 22.1
         */
        public static ByteLengthOfCodePointNode getUncached() {
            return TruffleStringFactory.ByteLengthOfCodePointNodeGen.getUncached();
        }
    }

    /**
     * Node to convert a given codepoint index to a byte index. See
     * {@link #execute(AbstractTruffleString, int, int, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class CodePointIndexToByteIndexNode extends Node {

        CodePointIndexToByteIndexNode() {
        }

        /**
         * Convert the given codepoint index to a byte index, relative to starting point
         * {@code byteOffset}.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, int byteOffset, int codepointIndex, Encoding expectedEncoding);

        @Specialization
        static int translate(AbstractTruffleString a, int byteOffset, int codepointIndex, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringInternalNodes.CodePointIndexToRawNode codePointIndexToRawNode) {
            a.checkEncoding(expectedEncoding);
            a.boundsCheck(codepointIndex, getCodePointLengthNode);
            int rawOffset = rawIndex(byteOffset, expectedEncoding);
            a.boundsCheckRaw(rawOffset);
            if (codepointIndex == 0) {
                return byteOffset;
            }
            Object arrayA = toIndexableNode.execute(a, a.data());
            int codeRangeA = getCodeRangeNode.execute(a);
            return codePointIndexToRawNode.execute(a, arrayA, codeRangeA, rawOffset, codepointIndex, false) << expectedEncoding.naturalStride;
        }

        /**
         * Create a new {@link CodePointIndexToByteIndexNode}.
         *
         * @since 22.1
         */
        public static CodePointIndexToByteIndexNode create() {
            return TruffleStringFactory.CodePointIndexToByteIndexNodeGen.create();
        }

        /**
         * Get the uncached version of {@link CodePointIndexToByteIndexNode}.
         *
         * @since 22.1
         */
        public static CodePointIndexToByteIndexNode getUncached() {
            return TruffleStringFactory.CodePointIndexToByteIndexNodeGen.getUncached();
        }
    }

    /**
     * Node to read a codepoint at a given codepoint index. See
     * {@link #execute(AbstractTruffleString, int, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class CodePointAtIndexNode extends Node {

        CodePointAtIndexNode() {
        }

        /**
         * Decode and return the codepoint at codepoint index {@code i}.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, int i, Encoding expectedEncoding);

        @Specialization
        static int readCodePoint(AbstractTruffleString a, int i, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringInternalNodes.CodePointAtNode readCodePointNode) {
            a.checkEncoding(expectedEncoding);
            a.boundsCheck(i, getCodePointLengthNode);
            Object arrayA = toIndexableNode.execute(a, a.data());
            return readCodePointNode.execute(a, arrayA, getCodeRangeNode.execute(a), i, expectedEncoding.id);
        }

        /**
         * Create a new {@link CodePointAtIndexNode}.
         *
         * @since 22.1
         */
        public static CodePointAtIndexNode create() {
            return TruffleStringFactory.CodePointAtIndexNodeGen.create();
        }

        /**
         * Get the uncached version of {@link CodePointAtIndexNode}.
         *
         * @since 22.1
         */
        public static CodePointAtIndexNode getUncached() {
            return TruffleStringFactory.CodePointAtIndexNodeGen.getUncached();
        }
    }

    /**
     * Node to read a codepoint at a given byte index. See
     * {@link #execute(AbstractTruffleString, int, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class CodePointAtByteIndexNode extends Node {

        CodePointAtByteIndexNode() {
        }

        /**
         * Decode and return the codepoint at byte index {@code i}.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, int i, Encoding expectedEncoding);

        @Specialization
        static int readCodePoint(AbstractTruffleString a, int byteIndex, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringInternalNodes.CodePointAtRawNode readCodePointNode) {
            final int i = rawIndex(byteIndex, expectedEncoding);
            a.checkEncoding(expectedEncoding);
            a.boundsCheckRaw(i);
            return readCodePointNode.execute(a, toIndexableNode.execute(a, a.data()), getCodeRangeNode.execute(a), i, expectedEncoding.id);
        }

        /**
         * Create a new {@link CodePointAtByteIndexNode}.
         *
         * @since 22.1
         */
        public static CodePointAtByteIndexNode create() {
            return TruffleStringFactory.CodePointAtByteIndexNodeGen.create();
        }

        /**
         * Get the uncached version of {@link CodePointAtByteIndexNode}.
         *
         * @since 22.1
         */
        public static CodePointAtByteIndexNode getUncached() {
            return TruffleStringFactory.CodePointAtByteIndexNodeGen.getUncached();
        }
    }

    /**
     * Node to find the index of the first occurrence of any byte from a given array. See
     * {@link #execute(AbstractTruffleString, int, int, byte[], TruffleString.Encoding)} for
     * details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ByteIndexOfAnyByteNode extends Node {

        ByteIndexOfAnyByteNode() {
        }

        /**
         * Return the byte index of the first occurrence of any byte contained in {@code values},
         * bounded by {@code fromByteIndex} (inclusive) and {@code maxByteIndex} (exclusive).
         * <p>
         * If none of the values is found, return a negative value.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, int fromByteIndex, int maxByteIndex, byte[] values, Encoding expectedEncoding);

        @Specialization
        int indexOfRaw(AbstractTruffleString a, int fromByteIndex, int maxByteIndex, byte[] values, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode) {
            if (isUTF16Or32(expectedEncoding)) {
                throw InternalErrors.illegalArgument("UTF-16 and UTF-32 not supported!");
            }
            a.checkEncoding(expectedEncoding);
            if (a.isEmpty()) {
                return -1;
            }
            a.boundsCheckRaw(fromByteIndex, maxByteIndex);
            if (TSCodeRange.is7Bit(getCodeRangeNode.execute(a)) && noneIsAscii(this, values)) {
                return -1;
            }
            assert isStride0(a);
            Object arrayA = toIndexableNode.execute(a, a.data());
            return TStringOps.indexOfAnyByte(this, a, arrayA, fromByteIndex, maxByteIndex, values);
        }

        private static boolean noneIsAscii(Node location, byte[] values) {
            for (int i = 0; i < values.length; i++) {
                if (Byte.toUnsignedInt(values[i]) <= 0x7f) {
                    return false;
                }
                TStringConstants.truffleSafePointPoll(location, i + 1);
            }
            return true;
        }

        /**
         * Create a new {@link ByteIndexOfAnyByteNode}.
         *
         * @since 22.1
         */
        public static ByteIndexOfAnyByteNode create() {
            return TruffleStringFactory.ByteIndexOfAnyByteNodeGen.create();
        }

        /**
         * Get the uncached version of {@link ByteIndexOfAnyByteNode}.
         *
         * @since 22.1
         */
        public static ByteIndexOfAnyByteNode getUncached() {
            return TruffleStringFactory.ByteIndexOfAnyByteNodeGen.getUncached();
        }
    }

    /**
     * Node to find the index of the first occurrence of any {@code char} from a given array. See
     * {@link #execute(AbstractTruffleString, int, int, char[])} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class CharIndexOfAnyCharUTF16Node extends Node {

        CharIndexOfAnyCharUTF16Node() {
        }

        /**
         * Return the char index of the first occurrence of any char contained in {@code values},
         * bounded by {@code fromCharIndex} (inclusive) and {@code maxCharIndex} (exclusive).
         * <p>
         * If none of the values is found, return a negative value.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, int fromCharIndex, int maxCharIndex, char[] values);

        @Specialization
        int indexOfRaw(AbstractTruffleString a, int fromCharIndex, int maxCharIndex, char[] values,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringOpsNodes.IndexOfAnyCharNode indexOfNode) {
            a.checkEncoding(Encoding.UTF_16);
            if (a.isEmpty()) {
                return -1;
            }
            a.boundsCheckRaw(fromCharIndex, maxCharIndex);
            int codeRangeA = getCodeRangeNode.execute(a);
            if (TSCodeRange.isFixedWidth(codeRangeA) && noneInCodeRange(this, codeRangeA, values)) {
                return -1;
            }
            return indexOfNode.execute(a, toIndexableNode.execute(a, a.data()), fromCharIndex, maxCharIndex, values);
        }

        private static boolean noneInCodeRange(Node location, int codeRange, char[] values) {
            for (int i = 0; i < values.length; i++) {
                if (TSCodeRange.isInCodeRange(values[i], codeRange)) {
                    return false;
                }
                TStringConstants.truffleSafePointPoll(location, i + 1);
            }
            return true;
        }

        /**
         * Create a new {@link CharIndexOfAnyCharUTF16Node}.
         *
         * @since 22.1
         */
        public static CharIndexOfAnyCharUTF16Node create() {
            return TruffleStringFactory.CharIndexOfAnyCharUTF16NodeGen.create();
        }

        /**
         * Get the uncached version of {@link CharIndexOfAnyCharUTF16Node}.
         *
         * @since 22.1
         */
        public static CharIndexOfAnyCharUTF16Node getUncached() {
            return TruffleStringFactory.CharIndexOfAnyCharUTF16NodeGen.getUncached();
        }
    }

    /**
     * Node to find the index of the first occurrence of any {@code int} from a given array. See
     * {@link #execute(AbstractTruffleString, int, int, int[])} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class IntIndexOfAnyIntUTF32Node extends Node {

        IntIndexOfAnyIntUTF32Node() {
        }

        /**
         * Return the int index of the first occurrence of any int contained in {@code values},
         * bounded by {@code fromIntIndex} (inclusive) and {@code maxIntIndex} (exclusive).
         * <p>
         * If none of the values is found, return a negative value.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, int fromIntIndex, int maxIntIndex, int[] values);

        @Specialization
        int indexOfRaw(AbstractTruffleString a, int fromIntIndex, int maxIntIndex, int[] values,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringOpsNodes.IndexOfAnyIntNode indexOfNode) {
            a.checkEncoding(Encoding.UTF_32);
            if (a.isEmpty()) {
                return -1;
            }
            a.boundsCheckRaw(fromIntIndex, maxIntIndex);
            if (noneInCodeRange(this, getCodeRangeNode.execute(a), values)) {
                return -1;
            }
            return indexOfNode.execute(a, toIndexableNode.execute(a, a.data()), fromIntIndex, maxIntIndex, values);
        }

        private static boolean noneInCodeRange(Node location, int codeRange, int[] values) {
            for (int i = 0; i < values.length; i++) {
                if (TSCodeRange.isInCodeRange(values[i], codeRange)) {
                    return false;
                }
                TStringConstants.truffleSafePointPoll(location, i + 1);
            }
            return true;
        }

        /**
         * Create a new {@link IntIndexOfAnyIntUTF32Node}.
         *
         * @since 22.1
         */
        public static IntIndexOfAnyIntUTF32Node create() {
            return TruffleStringFactory.IntIndexOfAnyIntUTF32NodeGen.create();
        }

        /**
         * Get the uncached version of {@link IntIndexOfAnyIntUTF32Node}.
         *
         * @since 22.1
         */
        public static IntIndexOfAnyIntUTF32Node getUncached() {
            return TruffleStringFactory.IntIndexOfAnyIntUTF32NodeGen.getUncached();
        }
    }

    /**
     * Node to find the index of the first occurrence of a given code point. See
     * {@link #execute(AbstractTruffleString, int, int, int, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class IndexOfCodePointNode extends Node {

        IndexOfCodePointNode() {
        }

        /**
         * Return the codepoint index of the first occurrence of {@code codepoint}, bounded by
         * {@code fromIndex} (inclusive) and {@code toIndex} (exclusive), if no occurrence is found
         * return a negative value.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, int codepoint, int fromIndex, int toIndex, Encoding expectedEncoding);

        @Specialization
        static int doIndexOf(AbstractTruffleString a, int codepoint, int fromIndex, int toIndex, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringInternalNodes.IndexOfCodePointNode indexOfNode) {
            a.checkEncoding(expectedEncoding);
            if (a.isEmpty()) {
                return -1;
            }
            a.boundsCheck(fromIndex, toIndex, getCodePointLengthNode);
            Object arrayA = toIndexableNode.execute(a, a.data());
            return indexOfNode.execute(a, arrayA, getCodeRangeNode.execute(a), codepoint, fromIndex, toIndex, expectedEncoding.id);
        }

        /**
         * Create a new {@link IndexOfCodePointNode}.
         *
         * @since 22.1
         */
        public static IndexOfCodePointNode create() {
            return TruffleStringFactory.IndexOfCodePointNodeGen.create();
        }

        /**
         * Get the uncached version of {@link IndexOfCodePointNode}.
         *
         * @since 22.1
         */
        public static IndexOfCodePointNode getUncached() {
            return TruffleStringFactory.IndexOfCodePointNodeGen.getUncached();
        }
    }

    /**
     * {@link IndexOfCodePointNode}, but with byte indices.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ByteIndexOfCodePointNode extends Node {

        ByteIndexOfCodePointNode() {
        }

        /**
         * {@link IndexOfCodePointNode}, but with byte indices.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, int codepoint, int fromByteIndex, int toByteIndex, Encoding expectedEncoding);

        @Specialization
        static int doIndexOf(AbstractTruffleString a, int codepoint, int fromIndexB, int toIndexB, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringInternalNodes.IndexOfCodePointRawNode indexOfNode) {
            a.checkEncoding(expectedEncoding);
            if (a.isEmpty()) {
                return -1;
            }
            final int fromIndex = rawIndex(fromIndexB, expectedEncoding);
            final int toIndex = rawIndex(toIndexB, expectedEncoding);
            a.boundsCheckRaw(fromIndex, toIndex);
            return byteIndex(indexOfNode.execute(a, toIndexableNode.execute(a, a.data()), getCodeRangeNode.execute(a), codepoint, fromIndex, toIndex), expectedEncoding);
        }

        /**
         * Create a new {@link ByteIndexOfCodePointNode}.
         *
         * @since 22.1
         */
        public static ByteIndexOfCodePointNode create() {
            return TruffleStringFactory.ByteIndexOfCodePointNodeGen.create();
        }

        /**
         * Get the uncached version of {@link ByteIndexOfCodePointNode}.
         *
         * @since 22.1
         */
        public static ByteIndexOfCodePointNode getUncached() {
            return TruffleStringFactory.ByteIndexOfCodePointNodeGen.getUncached();
        }
    }

    /**
     * Node to find the codepoint index of the last occurrence of a given code point. See
     * {@link #execute(AbstractTruffleString, int, int, int, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class LastIndexOfCodePointNode extends Node {

        LastIndexOfCodePointNode() {
        }

        /**
         * Return the codepoint index of the last occurrence of {@code codepoint}, bounded by
         * {@code fromIndex} (exclusive upper limit) and {@code toIndex} (inclusive lower limit), if
         * no occurrence is found return a negative value.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, int codepoint, int fromIndex, int toIndex, Encoding expectedEncoding);

        @Specialization
        static int doIndexOf(AbstractTruffleString a, int codepoint, int fromIndex, int toIndex, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringInternalNodes.LastIndexOfCodePointNode lastIndexOfNode) {
            a.checkEncoding(expectedEncoding);
            if (a.isEmpty()) {
                return -1;
            }
            a.boundsCheck(toIndex, fromIndex, getCodePointLengthNode);
            Object arrayA = toIndexableNode.execute(a, a.data());
            return lastIndexOfNode.execute(a, arrayA, getCodeRangeNode.execute(a), codepoint, fromIndex, toIndex);
        }

        /**
         * Create a new {@link LastIndexOfCodePointNode}.
         *
         * @since 22.1
         */
        public static LastIndexOfCodePointNode create() {
            return TruffleStringFactory.LastIndexOfCodePointNodeGen.create();
        }

        /**
         * Get the uncached version of {@link LastIndexOfCodePointNode}.
         *
         * @since 22.1
         */
        public static LastIndexOfCodePointNode getUncached() {
            return TruffleStringFactory.LastIndexOfCodePointNodeGen.getUncached();
        }
    }

    /**
     * {@link LastIndexOfCodePointNode}, but with byte indices.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class LastByteIndexOfCodePointNode extends Node {

        LastByteIndexOfCodePointNode() {
        }

        /**
         * {@link LastIndexOfCodePointNode}, but with byte indices.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, int codepoint, int fromByteIndex, int toByteIndex, Encoding expectedEncoding);

        @Specialization
        static int doIndexOf(AbstractTruffleString a, int codepoint, int fromIndexB, int toIndexB, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringInternalNodes.LastIndexOfCodePointRawNode lastIndexOfNode) {
            a.checkEncoding(expectedEncoding);
            if (a.isEmpty()) {
                return -1;
            }
            final int fromIndex = rawIndex(fromIndexB, expectedEncoding);
            final int toIndex = rawIndex(toIndexB, expectedEncoding);
            a.boundsCheckRaw(toIndex, fromIndex);
            return byteIndex(lastIndexOfNode.execute(a, toIndexableNode.execute(a, a.data()), getCodeRangeNode.execute(a), codepoint, fromIndex, toIndex), expectedEncoding);
        }

        /**
         * Create a new {@link LastByteIndexOfCodePointNode}.
         *
         * @since 22.1
         */
        public static LastByteIndexOfCodePointNode create() {
            return TruffleStringFactory.LastByteIndexOfCodePointNodeGen.create();
        }

        /**
         * Get the uncached version of {@link LastByteIndexOfCodePointNode}.
         *
         * @since 22.1
         */
        public static LastByteIndexOfCodePointNode getUncached() {
            return TruffleStringFactory.LastByteIndexOfCodePointNodeGen.getUncached();
        }
    }

    /**
     * Node to find the index of the first occurrence of a given string. See
     * {@link #execute(AbstractTruffleString, AbstractTruffleString, int, int, TruffleString.Encoding)}
     * for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class IndexOfStringNode extends Node {

        IndexOfStringNode() {
        }

        /**
         * Return the codepoint index of the first occurrence of {@code string}, bounded by
         * {@code fromIndex} (inclusive) and {@code toIndex} (exclusive), if no occurrence is found
         * return a negative value.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, AbstractTruffleString b, int fromIndex, int toIndex, Encoding expectedEncoding);

        @Specialization
        static int indexOfString(AbstractTruffleString a, AbstractTruffleString b, int fromIndex, int toIndex, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNodeA,
                        @Cached ToIndexableNode toIndexableNodeB,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeBNode,
                        @Cached TStringInternalNodes.IndexOfStringNode indexOfStringNode) {
            int codeRangeA = getCodeRangeANode.execute(a);
            int codeRangeB = getCodeRangeBNode.execute(b);
            a.looseCheckEncoding(expectedEncoding, codeRangeA);
            b.looseCheckEncoding(expectedEncoding, codeRangeB);
            if (b.isEmpty()) {
                return fromIndex;
            }
            if (a.isEmpty()) {
                return -1;
            }
            a.boundsCheck(fromIndex, toIndex, getCodePointLengthNode);
            Object arrayA = toIndexableNodeA.execute(a, a.data());
            Object arrayB = toIndexableNodeB.execute(b, b.data());
            if (indexOfCannotMatch(a, codeRangeA, b, codeRangeB, null)) {
                return -1;
            }
            return indexOfStringNode.execute(a, arrayA, codeRangeA, b, arrayB, codeRangeB, fromIndex, toIndex);
        }

        /**
         * Create a new {@link IndexOfStringNode}.
         *
         * @since 22.1
         */
        public static IndexOfStringNode create() {
            return TruffleStringFactory.IndexOfStringNodeGen.create();
        }

        /**
         * Get the uncached version of {@link IndexOfStringNode}.
         *
         * @since 22.1
         */
        public static IndexOfStringNode getUncached() {
            return TruffleStringFactory.IndexOfStringNodeGen.getUncached();
        }
    }

    /**
     * {@link IndexOfStringNode}, but with byte indices.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ByteIndexOfStringNode extends Node {

        ByteIndexOfStringNode() {
        }

        /**
         * {@link IndexOfStringNode}, but with byte indices.
         *
         * @since 22.1
         */
        public final int execute(AbstractTruffleString a, AbstractTruffleString b, int fromByteIndex, int toByteIndex, Encoding expectedEncoding) {
            return execute(a, b, fromByteIndex, toByteIndex, null, expectedEncoding);
        }

        /**
         * {@link IndexOfStringNode}, but with byte indices. This variant accepts a
         * {@link TruffleString.WithMask} as the search value {@code b}, which changes the searching
         * algorithm in the following manner: whenever the contents of {@code a} and {@code b} are
         * compared, the mask is OR'ed to {@code a}, as shown in this exemplary method:
         *
         * <pre>
         * {@code
         * boolean bytesEqualAt(TruffleString a, int byteIndexA, TruffleString.WithMask b, int byteIndexB) {
         *     return (readByte(a, byteIndexA) | readByte(b.mask, byteIndexB)) == readByte(b, byteIndexB);
         * }
         * }
         * </pre>
         *
         * @since 22.1
         */
        public final int execute(AbstractTruffleString a, WithMask b, int fromByteIndex, int toByteIndex, Encoding expectedEncoding) {
            return execute(a, b.string, fromByteIndex, toByteIndex, b.mask, expectedEncoding);
        }

        abstract int execute(AbstractTruffleString a, AbstractTruffleString b, int fromIndex, int toIndex, byte[] mask, Encoding expectedEncoding);

        @Specialization
        static int indexOfString(AbstractTruffleString a, AbstractTruffleString b, int fromIndexB, int toIndexB, byte[] mask, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNodeA,
                        @Cached ToIndexableNode toIndexableNodeB,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeBNode,
                        @Cached TStringInternalNodes.IndexOfStringRawNode indexOfStringNode) {
            final int codeRangeA = getCodeRangeANode.execute(a);
            final int codeRangeB = getCodeRangeBNode.execute(b);
            a.looseCheckEncoding(expectedEncoding, codeRangeA);
            b.looseCheckEncoding(expectedEncoding, codeRangeB);
            if (mask != null && isUnsupportedEncoding(expectedEncoding) && !isFixedWidth(codeRangeA)) {
                throw InternalErrors.unsupportedOperation();
            }
            if (b.isEmpty()) {
                return fromIndexB;
            }
            if (a.isEmpty()) {
                return -1;
            }
            final int fromIndex = rawIndex(fromIndexB, expectedEncoding);
            final int toIndex = rawIndex(toIndexB, expectedEncoding);
            a.boundsCheckRaw(fromIndex, toIndex);
            Object arrayA = toIndexableNodeA.execute(a, a.data());
            Object arrayB = toIndexableNodeB.execute(b, b.data());
            if (indexOfCannotMatch(a, codeRangeA, b, codeRangeB, mask)) {
                return -1;
            }
            return byteIndex(indexOfStringNode.execute(a, arrayA, codeRangeA, b, arrayB, codeRangeB, fromIndex, toIndex, mask), expectedEncoding);
        }

        /**
         * Create a new {@link ByteIndexOfStringNode}.
         *
         * @since 22.1
         */
        public static ByteIndexOfStringNode create() {
            return TruffleStringFactory.ByteIndexOfStringNodeGen.create();
        }

        /**
         * Get the uncached version of {@link ByteIndexOfStringNode}.
         *
         * @since 22.1
         */
        public static ByteIndexOfStringNode getUncached() {
            return TruffleStringFactory.ByteIndexOfStringNodeGen.getUncached();
        }
    }

    /**
     * Node to find the index of the last occurrence of a given string. See
     * {@link #execute(AbstractTruffleString, AbstractTruffleString, int, int, TruffleString.Encoding)}
     * for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class LastIndexOfStringNode extends Node {

        LastIndexOfStringNode() {
        }

        /**
         * Return the codepoint index of the last occurrence of {@code string}, bounded by
         * {@code fromIndex} (exclusive upper limit) and {@code toIndex} (inclusive lower limit), if
         * no occurrence is found return a negative value.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, AbstractTruffleString b, int fromIndex, int toIndex, Encoding expectedEncoding);

        @Specialization
        static int lastIndexOfString(AbstractTruffleString a, AbstractTruffleString b, int fromIndex, int toIndex, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNodeA,
                        @Cached ToIndexableNode toIndexableNodeB,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeBNode,
                        @Cached TStringInternalNodes.LastIndexOfStringNode indexOfStringNode) {
            final int codeRangeA = getCodeRangeANode.execute(a);
            final int codeRangeB = getCodeRangeBNode.execute(b);
            a.looseCheckEncoding(expectedEncoding, codeRangeA);
            b.looseCheckEncoding(expectedEncoding, codeRangeB);
            if (b.isEmpty()) {
                return fromIndex;
            }
            if (a.isEmpty()) {
                return -1;
            }
            a.boundsCheck(toIndex, fromIndex, getCodePointLengthNode);
            Object arrayA = toIndexableNodeA.execute(a, a.data());
            Object arrayB = toIndexableNodeB.execute(b, b.data());
            if (indexOfCannotMatch(a, codeRangeA, b, codeRangeB, null)) {
                return -1;
            }
            return indexOfStringNode.execute(a, arrayA, codeRangeA, b, arrayB, codeRangeB, fromIndex, toIndex);
        }

        /**
         * Create a new {@link LastIndexOfStringNode}.
         *
         * @since 22.1
         */
        public static LastIndexOfStringNode create() {
            return TruffleStringFactory.LastIndexOfStringNodeGen.create();
        }

        /**
         * Get the uncached version of {@link LastIndexOfStringNode}.
         *
         * @since 22.1
         */
        public static LastIndexOfStringNode getUncached() {
            return TruffleStringFactory.LastIndexOfStringNodeGen.getUncached();
        }
    }

    /**
     * {@link LastIndexOfStringNode}, but with byte indices.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class LastByteIndexOfStringNode extends Node {

        LastByteIndexOfStringNode() {
        }

        /**
         * {@link LastIndexOfStringNode}, but with byte indices.
         *
         * @since 22.1
         */
        public final int execute(AbstractTruffleString a, AbstractTruffleString b, int fromIndex, int toIndex, Encoding expectedEncoding) {
            return execute(a, b, fromIndex, toIndex, null, expectedEncoding);
        }

        /**
         * {@link LastIndexOfStringNode}, but with byte indices. This variant accepts a
         * {@link TruffleString.WithMask} as the search value {@code b}, which changes the searching
         * algorithm in the following manner: whenever the contents of {@code a} and {@code b} are
         * compared, the mask is OR'ed to {@code a}, as shown in this exemplary method:
         *
         * <pre>
         * {@code
         * boolean bytesEqualAt(TruffleString a, int byteIndexA, TruffleString.WithMask b, int byteIndexB) {
         *     return (readByte(a, byteIndexA) | readByte(b.mask, byteIndexB)) == readByte(b, byteIndexB);
         * }
         * }
         * </pre>
         *
         * @since 22.1
         */
        public final int execute(AbstractTruffleString a, WithMask b, int fromIndex, int toIndex, Encoding expectedEncoding) {
            return execute(a, b.string, fromIndex, toIndex, b.mask, expectedEncoding);
        }

        abstract int execute(AbstractTruffleString a, AbstractTruffleString b, int fromIndex, int toIndex, byte[] mask, Encoding expectedEncoding);

        @Specialization
        static int lastByteIndexOfString(AbstractTruffleString a, AbstractTruffleString b, int fromIndexB, int toIndexB, byte[] mask, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNodeA,
                        @Cached ToIndexableNode toIndexableNodeB,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeBNode,
                        @Cached TStringInternalNodes.LastIndexOfStringRawNode indexOfStringNode) {
            final int codeRangeA = getCodeRangeANode.execute(a);
            final int codeRangeB = getCodeRangeBNode.execute(b);
            a.looseCheckEncoding(expectedEncoding, codeRangeA);
            b.looseCheckEncoding(expectedEncoding, codeRangeB);
            if (mask != null && isUnsupportedEncoding(expectedEncoding) && !isFixedWidth(codeRangeA)) {
                throw InternalErrors.unsupportedOperation();
            }
            if (b.isEmpty()) {
                return fromIndexB;
            }
            if (a.isEmpty()) {
                return -1;
            }
            final int fromIndex = rawIndex(fromIndexB, expectedEncoding);
            final int toIndex = rawIndex(toIndexB, expectedEncoding);
            a.boundsCheckRaw(toIndex, fromIndex);
            Object arrayA = toIndexableNodeA.execute(a, a.data());
            Object arrayB = toIndexableNodeB.execute(b, b.data());
            if (indexOfCannotMatch(a, codeRangeA, b, codeRangeB, mask)) {
                return -1;
            }
            return byteIndex(indexOfStringNode.execute(a, arrayA, codeRangeA, b, arrayB, codeRangeB, fromIndex, toIndex, mask), expectedEncoding);
        }

        /**
         * Create a new {@link LastByteIndexOfStringNode}.
         *
         * @since 22.1
         */
        public static LastByteIndexOfStringNode create() {
            return TruffleStringFactory.LastByteIndexOfStringNodeGen.create();
        }

        /**
         * Get the uncached version of {@link LastByteIndexOfStringNode}.
         *
         * @since 22.1
         */
        public static LastByteIndexOfStringNode getUncached() {
            return TruffleStringFactory.LastByteIndexOfStringNodeGen.getUncached();
        }
    }

    /**
     * Node to compare two strings byte-by-byte. See
     * {@link #execute(AbstractTruffleString, AbstractTruffleString, TruffleString.Encoding)} for
     * details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class CompareBytesNode extends Node {

        CompareBytesNode() {
        }

        /**
         * Compare strings {@code a} and {@code b} byte-by-byte. Returns zero if {@code a} and
         * {@code b} are equal. If {@code a} is equal to {@code b} up to its length, but {@code b}
         * is longer than {@code a}, a negative value is returned. In the inverse case, a positive
         * value is returned. Otherwise, elements {@code a[i]} and {@code b[i]} at a byte index
         * {@code i} are different. If {@code a[i]} is greater than {@code b[i]}, a positive value
         * is returned, otherwise a negative value is returned.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, AbstractTruffleString b, Encoding expectedEncoding);

        @Specialization
        int compare(AbstractTruffleString a, AbstractTruffleString b, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNodeA,
                        @Cached ToIndexableNode toIndexableNodeB,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeBNode,
                        @Cached TStringOpsNodes.RawMemCmpBytesNode cmp) {
            nullCheck(expectedEncoding);
            if (a == b) {
                return 0;
            }
            final int codeRangeA = getCodeRangeANode.execute(a);
            final int codeRangeB = getCodeRangeBNode.execute(b);
            a.looseCheckEncoding(expectedEncoding, codeRangeA);
            b.looseCheckEncoding(expectedEncoding, codeRangeB);
            return cmp.execute(a, toIndexableNodeA.execute(a, a.data()), b, toIndexableNodeB.execute(b, b.data()));
        }

        /**
         * Create a new {@link CompareBytesNode}.
         *
         * @since 22.1
         */
        public static CompareBytesNode create() {
            return TruffleStringFactory.CompareBytesNodeGen.create();
        }

        /**
         * Get the uncached version of {@link CompareBytesNode}.
         *
         * @since 22.1
         */
        public static CompareBytesNode getUncached() {
            return TruffleStringFactory.CompareBytesNodeGen.getUncached();
        }
    }

    /**
     * Node to compare two UTF-16 strings. See
     * {@link #execute(AbstractTruffleString, AbstractTruffleString)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class CompareCharsUTF16Node extends Node {

        CompareCharsUTF16Node() {
        }

        /**
         * Compare UTF-16 strings {@code a} and {@code b} char-by-char. Returns zero if {@code a}
         * and {@code b} are equal. If {@code a} is equal to {@code b} up to its length, but
         * {@code b} is longer than {@code a}, a negative value is returned. In the inverse case, a
         * positive value is returned. Otherwise, elements {@code a[i]} and {@code b[i]} at an index
         * {@code i} are different. If {@code a[i]} is greater than {@code b[i]}, a positive value
         * is returned, otherwise a negative value is returned.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, AbstractTruffleString b);

        @Specialization
        static int compare(AbstractTruffleString a, AbstractTruffleString b,
                        @Cached ToIndexableNode toIndexableNodeA,
                        @Cached ToIndexableNode toIndexableNodeB,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeBNode,
                        @Cached TStringOpsNodes.RawMemCmpNode cmp) {
            if (a == b) {
                return 0;
            }
            final int codeRangeA = getCodeRangeANode.execute(a);
            final int codeRangeB = getCodeRangeBNode.execute(b);
            a.looseCheckEncoding(Encoding.UTF_16, codeRangeA);
            b.looseCheckEncoding(Encoding.UTF_16, codeRangeB);
            return cmp.execute(a, toIndexableNodeA.execute(a, a.data()), b, toIndexableNodeB.execute(b, b.data()));
        }

        /**
         * Create a new {@link CompareCharsUTF16Node}.
         *
         * @since 22.1
         */
        public static CompareCharsUTF16Node create() {
            return TruffleStringFactory.CompareCharsUTF16NodeGen.create();
        }

        /**
         * Get the uncached version of {@link CompareCharsUTF16Node}.
         *
         * @since 22.1
         */
        public static CompareCharsUTF16Node getUncached() {
            return TruffleStringFactory.CompareCharsUTF16NodeGen.getUncached();
        }
    }

    /**
     * Node to compare two UTF-32 strings. See
     * {@link #execute(AbstractTruffleString, AbstractTruffleString)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class CompareIntsUTF32Node extends Node {

        CompareIntsUTF32Node() {
        }

        /**
         * Compare UTF-32 strings {@code a} and {@code b} int-by-int. Returns zero if {@code a} and
         * {@code b} are equal. If {@code a} is equal to {@code b} up to its length, but {@code b}
         * is longer than {@code a}, a negative value is returned. In the inverse case, a positive
         * value is returned. Otherwise, elements {@code a[i]} and {@code b[i]} at an index
         * {@code i} are different. If {@code a[i]} is greater than {@code b[i]}, a positive value
         * is returned, otherwise a negative value is returned.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, AbstractTruffleString b);

        @Specialization
        static int compare(AbstractTruffleString a, AbstractTruffleString b,
                        @Cached ToIndexableNode toIndexableNodeA,
                        @Cached ToIndexableNode toIndexableNodeB,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeBNode,
                        @Cached TStringOpsNodes.RawMemCmpNode cmp) {
            if (a == b) {
                return 0;
            }
            final int codeRangeA = getCodeRangeANode.execute(a);
            final int codeRangeB = getCodeRangeBNode.execute(b);
            a.looseCheckEncoding(Encoding.UTF_32, codeRangeA);
            b.looseCheckEncoding(Encoding.UTF_32, codeRangeB);
            return cmp.execute(a, toIndexableNodeA.execute(a, a.data()), b, toIndexableNodeB.execute(b, b.data()));
        }

        /**
         * Create a new {@link CompareIntsUTF32Node}.
         *
         * @since 22.1
         */
        public static CompareIntsUTF32Node create() {
            return TruffleStringFactory.CompareIntsUTF32NodeGen.create();
        }

        /**
         * Get the uncached version of {@link CompareIntsUTF32Node}.
         *
         * @since 22.1
         */
        public static CompareIntsUTF32Node getUncached() {
            return TruffleStringFactory.CompareIntsUTF32NodeGen.getUncached();
        }
    }

    /**
     * Node to check codepoint equality of two string regions. See
     * {@link #execute(AbstractTruffleString, int, AbstractTruffleString, int, int, TruffleString.Encoding)}.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class RegionEqualNode extends Node {

        RegionEqualNode() {
        }

        /**
         * Checks for codepoint equality in a region with the given codepoint index and codepoint
         * length.
         * <p>
         * Equivalent to:
         *
         * <pre>
         * for (int i = 0; i < length; i++) {
         *     if (codePointAt(a, fromIndexA + i) != codePointAt(b, fromIndexB + i)) {
         *         return false;
         *     }
         * }
         * return true;
         * </pre>
         *
         * @since 22.1
         */
        public abstract boolean execute(AbstractTruffleString a, int fromIndexA, AbstractTruffleString b, int fromIndexB, int length, Encoding expectedEncoding);

        @Specialization
        static boolean regionEquals(AbstractTruffleString a, int fromIndexA, AbstractTruffleString b, int fromIndexB, int length, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNodeA,
                        @Cached ToIndexableNode toIndexableNodeB,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthANode,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthBNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeBNode,
                        @Cached TStringInternalNodes.RegionEqualsNode regionEqualsNode) {
            if (length == 0) {
                return true;
            }
            final int codeRangeA = getCodeRangeANode.execute(a);
            final int codeRangeB = getCodeRangeBNode.execute(b);
            a.looseCheckEncoding(expectedEncoding, codeRangeA);
            b.looseCheckEncoding(expectedEncoding, codeRangeB);
            a.boundsCheckRegion(fromIndexA, length, getCodePointLengthANode);
            b.boundsCheckRegion(fromIndexB, length, getCodePointLengthBNode);
            Object arrayA = toIndexableNodeA.execute(a, a.data());
            Object arrayB = toIndexableNodeB.execute(b, b.data());
            return regionEqualsNode.execute(a, arrayA, codeRangeA, b, arrayB, codeRangeB, fromIndexA, fromIndexB, length);
        }

        /**
         * Create a new {@link RegionEqualNode}.
         *
         * @since 22.1
         */
        public static RegionEqualNode create() {
            return TruffleStringFactory.RegionEqualNodeGen.create();
        }

        /**
         * Get the uncached version of {@link RegionEqualNode}.
         *
         * @since 22.1
         */
        public static RegionEqualNode getUncached() {
            return TruffleStringFactory.RegionEqualNodeGen.getUncached();
        }
    }

    /**
     * Node to check for a region match, byte-by-byte. See
     * {@link #execute(AbstractTruffleString, int, AbstractTruffleString, int, int, TruffleString.Encoding)}
     * and
     * {@link #execute(AbstractTruffleString, int, TruffleString.WithMask, int, int, TruffleString.Encoding)}
     * for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class RegionEqualByteIndexNode extends Node {

        RegionEqualByteIndexNode() {
        }

        /**
         * Checks for a region match, byte-by-byte.
         *
         * @since 22.1
         */
        public final boolean execute(AbstractTruffleString a, int fromByteIndexA, AbstractTruffleString b, int fromByteIndexB, int length, Encoding expectedEncoding) {
            return execute(a, fromByteIndexA, b, fromByteIndexB, length, null, expectedEncoding);
        }

        /**
         * Checks for a region match, byte-by-byte. This variant accepts a
         * {@link TruffleString.WithMask} as the search value {@code b}, which changes the matching
         * algorithm in the following manner: when the contents of {@code a} and {@code b} are
         * compared, the mask is OR'ed to {@code a}, as shown in this exemplary method:
         *
         * <pre>
         * {@code
         * boolean bytesEqualAt(TruffleString a, int byteIndexA, TruffleString.WithMask b, int byteIndexB) {
         *     return (readByte(a, byteIndexA) | readByte(b.mask, byteIndexB)) == readByte(b, byteIndexB);
         * }
         * }
         * </pre>
         *
         * @since 22.1
         */
        public final boolean execute(AbstractTruffleString a, int fromByteIndexA, WithMask b, int fromByteIndexB, int length, Encoding expectedEncoding) {
            return execute(a, fromByteIndexA, b.string, fromByteIndexB, length, b.mask, expectedEncoding);
        }

        abstract boolean execute(AbstractTruffleString a, int fromIndexA, AbstractTruffleString b, int fromIndexB, int length, byte[] mask, Encoding expectedEncoding);

        @Specialization
        static boolean regionEquals(AbstractTruffleString a, int byteFromIndexA, AbstractTruffleString b, int byteFromIndexB, int byteLength, byte[] mask, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNodeA,
                        @Cached ToIndexableNode toIndexableNodeB,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeBNode,
                        @Cached TStringOpsNodes.RawRegionEqualsNode regionEqualsNode) {
            if (byteLength == 0) {
                return true;
            }
            final int codeRangeA = getCodeRangeANode.execute(a);
            final int codeRangeB = getCodeRangeBNode.execute(b);
            a.looseCheckEncoding(expectedEncoding, codeRangeA);
            b.looseCheckEncoding(expectedEncoding, codeRangeB);
            final int fromIndexA = rawIndex(byteFromIndexA, expectedEncoding);
            final int fromIndexB = rawIndex(byteFromIndexB, expectedEncoding);
            final int length = rawIndex(byteLength, expectedEncoding);
            a.boundsCheckRegionRaw(fromIndexA, length);
            b.boundsCheckRegionRaw(fromIndexB, length);
            Object arrayA = toIndexableNodeA.execute(a, a.data());
            Object arrayB = toIndexableNodeB.execute(b, b.data());
            return regionEqualsNode.execute(a, arrayA, b, arrayB, fromIndexA, fromIndexB, length, mask);
        }

        /**
         * Create a new {@link RegionEqualByteIndexNode}.
         *
         * @since 22.1
         */
        public static RegionEqualByteIndexNode create() {
            return TruffleStringFactory.RegionEqualByteIndexNodeGen.create();
        }

        /**
         * Get the uncached version of {@link RegionEqualByteIndexNode}.
         *
         * @since 22.1
         */
        public static RegionEqualByteIndexNode getUncached() {
            return TruffleStringFactory.RegionEqualByteIndexNodeGen.getUncached();
        }
    }

    /**
     * Node to concatenate two strings. See
     * {@link #execute(AbstractTruffleString, AbstractTruffleString, TruffleString.Encoding, boolean)}
     * for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ConcatNode extends Node {

        ConcatNode() {
        }

        /**
         * Create a new string by concatenating {@code a} and {@code b}. If {@code lazy} is
         * {@code true}, the creation of the new string's internal array may be delayed until it is
         * required by another operation.
         *
         * @since 22.1
         */
        public abstract TruffleString execute(AbstractTruffleString a, AbstractTruffleString b, Encoding expectedEncoding, boolean lazy);

        @SuppressWarnings("unused")
        @Specialization(guards = "isEmpty(a)")
        static TruffleString aEmpty(AbstractTruffleString a, TruffleString b, Encoding expectedEncoding, boolean lazy) {
            CompilerAsserts.partialEvaluationConstant(lazy);
            if (AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS) {
                b.looseCheckEncoding(expectedEncoding, b.codeRange());
                return b.switchEncodingUncached(expectedEncoding);
            }
            b.checkEncoding(expectedEncoding);
            return b;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isEmpty(a)")
        static TruffleString aEmptyMutable(AbstractTruffleString a, MutableTruffleString b, Encoding expectedEncoding, boolean lazy,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringInternalNodes.FromBufferWithStringCompactionKnownAttributesNode fromBufferWithStringCompactionNode) {
            CompilerAsserts.partialEvaluationConstant(lazy);
            if (AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS) {
                b.looseCheckEncoding(expectedEncoding, TStringInternalNodes.GetCodeRangeNode.getUncached().execute(b));
                return b.switchEncodingUncached(expectedEncoding);
            }
            int codeRange = getCodeRangeNode.execute(b);
            b.looseCheckEncoding(expectedEncoding, codeRange);
            return fromBufferWithStringCompactionNode.execute(b.data(), b.offset(), b.length() << b.stride(), expectedEncoding.id, getCodePointLengthNode.execute(b), codeRange);
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isEmpty(b)")
        static TruffleString bEmpty(TruffleString a, AbstractTruffleString b, Encoding expectedEncoding, boolean lazy) {
            CompilerAsserts.partialEvaluationConstant(lazy);
            if (AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS) {
                a.looseCheckEncoding(expectedEncoding, a.codeRange());
                return a.switchEncodingUncached(expectedEncoding);
            }
            a.checkEncoding(expectedEncoding);
            return a;
        }

        @SuppressWarnings("unused")
        @Specialization(guards = "isEmpty(b)")
        static TruffleString bEmptyMutable(MutableTruffleString a, AbstractTruffleString b, Encoding expectedEncoding, boolean lazy,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringInternalNodes.FromBufferWithStringCompactionKnownAttributesNode fromBufferWithStringCompactionNode) {
            CompilerAsserts.partialEvaluationConstant(lazy);
            if (AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS) {
                a.looseCheckEncoding(expectedEncoding, TStringInternalNodes.GetCodeRangeNode.getUncached().execute(a));
                return a.switchEncodingUncached(expectedEncoding);
            }
            int codeRange = getCodeRangeNode.execute(a);
            a.looseCheckEncoding(expectedEncoding, codeRange);
            return fromBufferWithStringCompactionNode.execute(a.data(), a.offset(), a.length() << a.stride(), expectedEncoding.id, getCodePointLengthNode.execute(a), codeRange);
        }

        @Specialization(guards = {"!isEmpty(a)", "!isEmpty(b)"})
        static TruffleString doConcat(AbstractTruffleString a, AbstractTruffleString b, Encoding encoding, boolean lazy,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeBNode,
                        @Cached TStringInternalNodes.StrideFromCodeRangeNode getStrideNode,
                        @Cached TStringInternalNodes.ConcatEagerNode concatEagerNode,
                        @Cached AsTruffleStringNode asTruffleStringANode,
                        @Cached AsTruffleStringNode asTruffleStringBNode,
                        @Cached ConditionProfile lazyProfile) {
            CompilerAsserts.partialEvaluationConstant(lazy);
            final int codeRangeA = getCodeRangeANode.execute(a);
            final int codeRangeB = getCodeRangeBNode.execute(b);
            a.looseCheckEncoding(encoding, codeRangeA);
            b.looseCheckEncoding(encoding, codeRangeB);
            int commonCodeRange = TSCodeRange.commonCodeRange(codeRangeA, codeRangeB);
            assert !(isBrokenMultiByte(codeRangeA) || isBrokenMultiByte(codeRangeB)) || isBrokenMultiByte(commonCodeRange);
            int targetStride = getStrideNode.execute(commonCodeRange, encoding.id);
            int length = addByteLengths(a, b, targetStride);
            boolean valid = !isBrokenMultiByte(commonCodeRange);
            if (lazyProfile.profile(lazy && valid && (a.isImmutable() || b.isImmutable()) && (length << targetStride) >= TStringConstants.LAZY_CONCAT_MIN_LENGTH)) {
                if (AbstractTruffleString.DEBUG_STRICT_ENCODING_CHECKS) {
                    return TruffleString.createLazyConcat(asTruffleStringLoose(a, encoding), asTruffleStringLoose(b, encoding), encoding.id, length, targetStride);
                } else {
                    return TruffleString.createLazyConcat(asTruffleStringANode.execute(a, encoding), asTruffleStringBNode.execute(b, encoding), encoding.id, length, targetStride);
                }
            }
            return concatEagerNode.execute(a, b, encoding.id, length, targetStride, commonCodeRange);
        }

        static int addByteLengths(AbstractTruffleString a, AbstractTruffleString b, int targetStride) {
            long length = (long) a.length() + (long) b.length();
            if (length << targetStride > TStringConstants.MAX_ARRAY_SIZE) {
                throw InternalErrors.outOfMemory();
            }
            return (int) length;
        }

        private static TruffleString asTruffleStringLoose(AbstractTruffleString a, Encoding encoding) {
            if (a.isImmutable()) {
                return (TruffleString) a;
            }
            return TStringInternalNodes.FromBufferWithStringCompactionKnownAttributesNode.getUncached().execute(
                            a.data(), a.offset(), a.length() << a.stride(), encoding.id,
                            TStringInternalNodes.GetCodePointLengthNode.getUncached().execute(a),
                            TStringInternalNodes.GetCodeRangeNode.getUncached().execute(a));
        }

        /**
         * Create a new {@link ConcatNode}.
         *
         * @since 22.1
         */
        public static ConcatNode create() {
            return TruffleStringFactory.ConcatNodeGen.create();
        }

        /**
         * Get the uncached version of {@link ConcatNode}.
         *
         * @since 22.1
         */
        public static ConcatNode getUncached() {
            return TruffleStringFactory.ConcatNodeGen.getUncached();
        }
    }

    /**
     * Node to repeat a given string {@code N} times. See
     * {@link #execute(AbstractTruffleString, int, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class RepeatNode extends Node {

        RepeatNode() {
        }

        /**
         * Create a new string by repeating {@code n} times string {@code a}.
         *
         * @since 22.1
         */
        public abstract TruffleString execute(AbstractTruffleString a, int n, Encoding expectedEncoding);

        @Specialization
        TruffleString repeat(AbstractTruffleString a, int n, Encoding expectedEncoding,
                        @Cached AsTruffleStringNode asTruffleStringNode,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.CalcStringAttributesNode calcStringAttributesNode,
                        @Cached ConditionProfile brokenProfile) {
            a.checkEncoding(expectedEncoding);
            if (n < 0) {
                throw InternalErrors.illegalArgument("n must be positive");
            }
            if (a.isEmpty() || n == 0) {
                return expectedEncoding.getEmpty();
            }
            if (n == 1) {
                return asTruffleStringNode.execute(a, expectedEncoding);
            }
            Object arrayA = toIndexableNode.execute(a, a.data());
            int codeRangeA = getCodeRangeNode.execute(a);
            int codePointLengthA = getCodePointLengthNode.execute(a);
            int byteLengthA = (a.length()) << a.stride();
            long byteLength = ((long) byteLengthA) * n;
            if (byteLength < 0 || byteLength > TStringConstants.MAX_ARRAY_SIZE) {
                throw InternalErrors.outOfMemory();
            }
            byte[] array = new byte[(int) byteLength];
            int offsetB = 0;
            for (int i = 0; i < n; i++) {
                TStringOps.arraycopyWithStride(this, arrayA, a.offset(), 0, 0, array, offsetB, 0, 0, byteLengthA);
                offsetB += byteLengthA;
                TStringConstants.truffleSafePointPoll(this, i + 1);
            }
            int length = (int) (byteLength >> a.stride());
            if (brokenProfile.profile(isBrokenFixedWidth(codeRangeA) || isBrokenMultiByte(codeRangeA))) {
                long attrs = calcStringAttributesNode.execute(null, array, 0, length, a.stride(), expectedEncoding.id, TSCodeRange.getUnknown());
                codeRangeA = StringAttributes.getCodeRange(attrs);
                codePointLengthA = StringAttributes.getCodePointLength(attrs);
            } else {
                codePointLengthA *= n;
            }
            return createFromByteArray(array, length, a.stride(), expectedEncoding.id, codePointLengthA, codeRangeA);
        }

        /**
         * Create a new {@link RepeatNode}.
         *
         * @since 22.1
         */
        public static RepeatNode create() {
            return TruffleStringFactory.RepeatNodeGen.create();
        }

        /**
         * Get the uncached version of {@link RepeatNode}.
         *
         * @since 22.1
         */
        public static RepeatNode getUncached() {
            return TruffleStringFactory.RepeatNodeGen.getUncached();
        }
    }

    /**
     * Node to create a substring of a given string. See
     * {@link #execute(AbstractTruffleString, int, int, TruffleString.Encoding, boolean)} for
     * details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class SubstringNode extends Node {

        SubstringNode() {
        }

        /**
         * Create a substring of {@code a}, starting from codepoint index {@code fromIndex}, with
         * codepoint length {@code length}. If {@code lazy} is {@code true}, {@code a}'s internal
         * storage will be re-used instead of creating a copy of the requested range. Since the
         * resulting string will have a reference to {@code a}'s internal storage, and
         * {@link TruffleString} currently does <i>not</i> resize/trim the substring's internal
         * storage at any point, the {@code lazy} variant effectively creates a memory leak! The
         * caller is responsible for deciding whether this is acceptable or not.
         *
         * @since 22.1
         */
        public abstract TruffleString execute(AbstractTruffleString a, int fromIndex, int length, Encoding expectedEncoding, boolean lazy);

        @Specialization
        static TruffleString substring(AbstractTruffleString a, int fromIndex, int length, Encoding expectedEncoding, boolean lazy,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.CodePointIndexToRawNode translateIndexNode,
                        @Cached TStringInternalNodes.SubstringNode substringNode) {
            a.checkEncoding(expectedEncoding);
            a.boundsCheckRegion(fromIndex, length, getCodePointLengthNode);
            if (length == 0) {
                return expectedEncoding.getEmpty();
            }
            Object arrayA = toIndexableNode.execute(a, a.data());
            final int codeRangeA = getCodeRangeANode.execute(a);
            int fromIndexRaw = translateIndexNode.execute(a, arrayA, codeRangeA, 0, fromIndex, false);
            int lengthRaw = translateIndexNode.execute(a, arrayA, codeRangeA, fromIndexRaw, length, true);
            return substringNode.execute(a, arrayA, codeRangeA, fromIndexRaw, lengthRaw, lazy && a.isImmutable());
        }

        /**
         * Create a new {@link SubstringNode}.
         *
         * @since 22.1
         */
        public static SubstringNode create() {
            return TruffleStringFactory.SubstringNodeGen.create();
        }

        /**
         * Get the uncached version of {@link SubstringNode}.
         *
         * @since 22.1
         */
        public static SubstringNode getUncached() {
            return TruffleStringFactory.SubstringNodeGen.getUncached();
        }
    }

    /**
     * {@link SubstringNode}, but with byte indices.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class SubstringByteIndexNode extends Node {

        SubstringByteIndexNode() {
        }

        /**
         * {@link SubstringNode}, but with byte indices.
         *
         * @since 22.1
         */
        public abstract TruffleString execute(AbstractTruffleString a, int fromIndex, int length, Encoding expectedEncoding, boolean lazy);

        @Specialization
        static TruffleString substringRaw(AbstractTruffleString a, int byteFromIndex, int byteLength, Encoding expectedEncoding, boolean lazy,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.SubstringNode substringNode) {
            a.checkEncoding(expectedEncoding);
            final int codeRangeA = getCodeRangeANode.execute(a);
            final int fromIndex = rawIndex(byteFromIndex, expectedEncoding);
            final int length = rawIndex(byteLength, expectedEncoding);
            a.boundsCheckRegionRaw(fromIndex, length);
            if (length == 0) {
                return expectedEncoding.getEmpty();
            }
            return substringNode.execute(a, toIndexableNode.execute(a, a.data()), codeRangeA, fromIndex, length, lazy && a.isImmutable());
        }

        /**
         * Create a new {@link SubstringByteIndexNode}.
         *
         * @since 22.1
         */
        public static SubstringByteIndexNode create() {
            return TruffleStringFactory.SubstringByteIndexNodeGen.create();
        }

        /**
         * Get the uncached version of {@link SubstringByteIndexNode}.
         *
         * @since 22.1
         */
        public static SubstringByteIndexNode getUncached() {
            return TruffleStringFactory.SubstringByteIndexNodeGen.getUncached();
        }
    }

    /**
     * Node to check two strings for equality.
     * <p>
     * The {@link TruffleString#equals(Object)}-method delegates to this node.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class EqualNode extends Node {

        EqualNode() {
        }

        /**
         * Returns {@code true} if {@code a} and {@code b} are byte-by-byte equal when considered in
         * {@code expectedEncoding}. Note that this method requires both strings to be
         * {@link #isCompatibleTo(TruffleString.Encoding) compatible} to the
         * {@code expectedEncoding}, just like all other operations with an {@code expectedEncoding}
         * parameter!
         * <p>
         * The {@link TruffleString#equals(Object)}-method delegates to this method.
         *
         * @since 22.1
         */
        public abstract boolean execute(AbstractTruffleString a, AbstractTruffleString b, Encoding expectedEncoding);

        @SuppressWarnings("unused")
        @Specialization(guards = "identical(a, b)")
        static boolean sameObject(AbstractTruffleString a, AbstractTruffleString b, Encoding expectedEncoding) {
            return true;
        }

        @Specialization(guards = "!identical(a, b)")
        static boolean check(AbstractTruffleString a, AbstractTruffleString b, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNodeA,
                        @Cached ToIndexableNode toIndexableNodeB,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeBNode,
                        @Cached TStringOpsNodes.RawEqualsNode equalsNode) {
            final int codeRangeA = getCodeRangeANode.execute(a);
            final int codeRangeB = getCodeRangeBNode.execute(b);
            a.looseCheckEncoding(expectedEncoding, codeRangeA);
            b.looseCheckEncoding(expectedEncoding, codeRangeB);
            return checkContentEquals(a, codeRangeA, b, codeRangeB, toIndexableNodeA, toIndexableNodeB, equalsNode);
        }

        static boolean checkContentEquals(
                        AbstractTruffleString a, int codeRangeA,
                        AbstractTruffleString b, int codeRangeB,
                        ToIndexableNode toIndexableNodeA,
                        ToIndexableNode toIndexableNodeB,
                        TStringOpsNodes.RawEqualsNode equalsNode) {
            assert TSCodeRange.isKnown(codeRangeA, codeRangeB);
            if (a.length() != b.length() || codeRangeA != codeRangeB || a.isHashCodeCalculated() && b.isHashCodeCalculated() && a.getHashCodeUnsafe() != b.getHashCodeUnsafe()) {
                return false;
            }
            return equalsNode.execute(a, toIndexableNodeA.execute(a, a.data()), b, toIndexableNodeB.execute(b, b.data()));
        }

        /**
         * Create a new {@link EqualNode}.
         *
         * @since 22.1
         */
        public static EqualNode create() {
            return TruffleStringFactory.EqualNodeGen.create();
        }

        /**
         * Get the uncached version of {@link EqualNode}.
         *
         * @since 22.1
         */
        public static EqualNode getUncached() {
            return TruffleStringFactory.EqualNodeGen.getUncached();
        }
    }

    /**
     * This exception may be thrown by {@link ParseIntNode}, {@link ParseLongNode} or
     * {@link ParseDoubleNode} to indicate that the given string cannot be parsed as an integer,
     * long or double value. This exception does not record stack traces for performance reasons.
     *
     * @since 22.1
     */
    public static final class NumberFormatException extends Exception {

        private static final long serialVersionUID = 0x016db657faff57a2L;

        NumberFormatException(String message) {
            super(message);
        }

        NumberFormatException() {
            super();
        }

        /**
         * No stack trace for this exception.
         *
         * @since 22.1
         */
        @SuppressWarnings("sync-override")
        @Override
        public Throwable fillInStackTrace() {
            return this;
        }
    }

    /**
     * Node to parse a given string as an int value.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ParseIntNode extends Node {

        ParseIntNode() {
        }

        /**
         * Parse the given string as an int value, or throw {@link NumberFormatException}.
         *
         * @since 22.1
         */
        public abstract int execute(AbstractTruffleString a, int radix) throws NumberFormatException;

        @Specialization(guards = {"a.isLazyLong()", "radix == 10"})
        static int doLazyLong(AbstractTruffleString a, @SuppressWarnings("unused") int radix,
                        @Cached BranchProfile errorProfile) throws NumberFormatException {
            long value = ((LazyLong) a.data()).value;
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                errorProfile.enter();
                throw NumberConversion.numberFormatException();
            }
            return (int) value;
        }

        @Specialization(guards = {"!a.isLazyLong() || radix != 10"})
        static int doParse(AbstractTruffleString a, int radix,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.ParseIntNode parseIntNode,
                        @Cached("createIdentityProfile()") IntValueProfile radixProfile) throws NumberFormatException {
            final int codeRangeA = getCodeRangeANode.execute(a);
            return parseIntNode.execute(a, toIndexableNode.execute(a, a.data()), codeRangeA, radixProfile.profile(radix));
        }

        /**
         * Create a new {@link ParseIntNode}.
         *
         * @since 22.1
         */
        public static ParseIntNode create() {
            return TruffleStringFactory.ParseIntNodeGen.create();
        }

        /**
         * Get the uncached version of {@link ParseIntNode}.
         *
         * @since 22.1
         */
        public static ParseIntNode getUncached() {
            return TruffleStringFactory.ParseIntNodeGen.getUncached();
        }
    }

    /**
     * Node to parse a given string as a long value.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ParseLongNode extends Node {

        ParseLongNode() {
        }

        /**
         * Parse the given string as a long value, or throw {@link NumberFormatException}.
         *
         * @since 22.1
         */
        public abstract long execute(AbstractTruffleString a, int radix) throws TruffleString.NumberFormatException;

        @Specialization(guards = {"a.isLazyLong()", "radix == 10"})
        static long doLazyLong(AbstractTruffleString a, @SuppressWarnings("unused") int radix) {
            return ((LazyLong) a.data()).value;
        }

        @Specialization(guards = {"!a.isLazyLong() || radix != 10"})
        static long doParse(AbstractTruffleString a, int radix,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode,
                        @Cached TStringInternalNodes.ParseLongNode parseLongNode,
                        @Cached("createIdentityProfile()") IntValueProfile radixProfile) throws NumberFormatException {
            final int codeRangeA = getCodeRangeANode.execute(a);
            return parseLongNode.execute(a, toIndexableNode.execute(a, a.data()), codeRangeA, radixProfile.profile(radix));
        }

        /**
         * Create a new {@link ParseLongNode}.
         *
         * @since 22.1
         */
        public static ParseLongNode create() {
            return TruffleStringFactory.ParseLongNodeGen.create();
        }

        /**
         * Get the uncached version of {@link ParseLongNode}.
         *
         * @since 22.1
         */
        public static ParseLongNode getUncached() {
            return TruffleStringFactory.ParseLongNodeGen.getUncached();
        }
    }

    /**
     * Node to parse a given string as a double value.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ParseDoubleNode extends Node {

        ParseDoubleNode() {
        }

        /**
         * Parse the given string as a double value, or throw {@link NumberFormatException}.
         *
         * @since 22.1
         */
        public abstract double execute(AbstractTruffleString a) throws NumberFormatException;

        @Specialization(guards = "isLazyLongSafeInteger(a)")
        static double doLazyLong(AbstractTruffleString a) {
            return ((LazyLong) a.data()).value;
        }

        @Specialization(guards = "!isLazyLongSafeInteger(a)")
        static double parseDouble(AbstractTruffleString a,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.ParseDoubleNode parseDoubleNode) throws NumberFormatException {
            return parseDoubleNode.execute(a, toIndexableNode.execute(a, a.data()));
        }

        static boolean isLazyLongSafeInteger(AbstractTruffleString a) {
            return a.isLazyLong() && NumberConversion.isSafeInteger(((LazyLong) a.data()).value);
        }

        /**
         * Create a new {@link ParseDoubleNode}.
         *
         * @since 22.1
         */
        public static ParseDoubleNode create() {
            return TruffleStringFactory.ParseDoubleNodeGen.create();
        }

        /**
         * Get the uncached version of {@link ParseDoubleNode}.
         *
         * @since 22.1
         */
        public static ParseDoubleNode getUncached() {
            return TruffleStringFactory.ParseDoubleNodeGen.getUncached();
        }
    }

    /**
     * Node to get a string's internal byte array. See
     * {@link #execute(AbstractTruffleString, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class GetInternalByteArrayNode extends Node {

        GetInternalByteArrayNode() {
        }

        /**
         * Get the given string's internal byte array. The returned byte array must not be modified.
         * Note that this operation may also return a copy of the string's internal storage, if the
         * internal format does not match the regular encoded string format; compacted and native
         * strings will always yield a copy.
         *
         * CAUTION: TruffleString re-uses internal byte arrays whenever possible, DO NOT modify the
         * arrays returned by this operation. Use this operation only when absolutely necessary.
         * Reading a string's contents should always be done via nodes like {@link ReadByteNode},
         * {@link ReadCharUTF16Node}, {@link CodePointAtIndexNode}, {@link CodePointAtByteIndexNode}
         * etc., if at all possible. If mutability is required, use {@link MutableTruffleString}
         * instead.
         *
         * @since 22.1
         */
        public abstract InternalByteArray execute(AbstractTruffleString a, Encoding expectedEncoding);

        @Specialization
        InternalByteArray getInternalByteArray(AbstractTruffleString a, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached ConditionProfile utf16Profile,
                        @Cached ConditionProfile utf16S0Profile,
                        @Cached ConditionProfile utf32Profile,
                        @Cached ConditionProfile utf32S0Profile,
                        @Cached ConditionProfile utf32S1Profile,
                        @Cached ConditionProfile isByteArrayProfile) {
            if (a.isEmpty()) {
                return InternalByteArray.EMPTY;
            }
            a.checkEncoding(expectedEncoding);
            Object arrayA = toIndexableNode.execute(a, a.data());
            if (utf16Profile.profile(isUTF16(expectedEncoding))) {
                if (utf16S0Profile.profile(isStride0(a))) {
                    return inflate(a, arrayA, 0, 1);
                }
            } else if (utf32Profile.profile(isUTF32(expectedEncoding))) {
                if (utf32S0Profile.profile(isStride0(a))) {
                    return inflate(a, arrayA, 0, 2);
                }
                if (utf32S1Profile.profile(isStride1(a))) {
                    return inflate(a, arrayA, 1, 2);
                }
            }
            int byteLength = a.length() << a.stride();
            if (isByteArrayProfile.profile(arrayA instanceof byte[])) {
                return new InternalByteArray((byte[]) arrayA, a.offset(), byteLength);
            } else {
                return new InternalByteArray(TStringOps.arraycopyOfWithStride(this, arrayA, a.offset(), byteLength, 0, byteLength, 0), 0, byteLength);
            }
        }

        private InternalByteArray inflate(AbstractTruffleString a, Object arrayA, int strideA, int strideB) {
            assert a.stride() == strideA;
            CompilerAsserts.partialEvaluationConstant(strideA);
            CompilerAsserts.partialEvaluationConstant(strideB);
            return new InternalByteArray(TStringOps.arraycopyOfWithStride(this, arrayA, a.offset(), a.length(), strideA, a.length(), strideB), 0, a.length() << strideB);
        }

        /**
         * Create a new {@link GetInternalByteArrayNode}.
         *
         * @since 22.1
         */
        public static GetInternalByteArrayNode create() {
            return TruffleStringFactory.GetInternalByteArrayNodeGen.create();
        }

        /**
         * Get the uncached version of {@link GetInternalByteArrayNode}.
         *
         * @since 22.1
         */
        public static GetInternalByteArrayNode getUncached() {
            return TruffleStringFactory.GetInternalByteArrayNodeGen.getUncached();
        }
    }

    /**
     * Node to get a {@link AbstractTruffleString#isNative() native} string's pointer object. See
     * {@link #execute(AbstractTruffleString, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class GetInternalNativePointerNode extends Node {

        GetInternalNativePointerNode() {
        }

        /**
         * Get the given string's pointer object which was passed to {@link FromNativePointerNode}.
         * If the string is not backed by a native pointer, this node will throw an
         * {@link UnsupportedOperationException}. Use {@link AbstractTruffleString#isNative()} to
         * check whether the string is actually backed by a native pointer before calling this node.
         * Caution: If the given string is a {@link TruffleString}, the native pointer must not be
         * modified as long as the string is used.
         *
         * @since 22.1
         */
        public abstract Object execute(AbstractTruffleString a, Encoding expectedEncoding);

        @Specialization
        static Object getNativePointer(AbstractTruffleString a, Encoding expectedEncoding) {
            a.checkEncoding(expectedEncoding);
            if (!a.isNative()) {
                throw InternalErrors.unsupportedOperation("string is not backed by a native pointer!");
            }
            return ((NativePointer) a.data()).getPointerObject();
        }

        /**
         * Create a new {@link GetInternalNativePointerNode}.
         *
         * @since 22.1
         */
        public static GetInternalNativePointerNode create() {
            return TruffleStringFactory.GetInternalNativePointerNodeGen.create();
        }

        /**
         * Get the uncached version of {@link GetInternalNativePointerNode}.
         *
         * @since 22.1
         */
        public static GetInternalNativePointerNode getUncached() {
            return TruffleStringFactory.GetInternalNativePointerNodeGen.getUncached();
        }
    }

    /**
     * Node to copy a region of a string into a byte array. See
     * {@link #execute(AbstractTruffleString, int, byte[], int, int, TruffleString.Encoding)} for
     * details.
     *
     * @since 22.1
     */
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class CopyToByteArrayNode extends Node {

        CopyToByteArrayNode() {
        }

        /**
         * Copy a region of the given {@link TruffleString} {@code a}, bounded by
         * {@code byteFromIndexA} and {@code byteLength} into the given byte array, at starting at
         * {@code byteFromIndexDst}.
         *
         * @since 22.1
         */
        public abstract void execute(AbstractTruffleString a, int byteFromIndexA, byte[] dst, int byteFromIndexDst, int byteLength, Encoding expectedEncoding);

        @Specialization
        void doCopy(AbstractTruffleString a, int byteFromIndexA, byte[] arrayB, int byteFromIndexB, int byteLength, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached ConditionProfile utf16Profile,
                        @Cached ConditionProfile utf16S0Profile,
                        @Cached ConditionProfile utf32Profile,
                        @Cached ConditionProfile utf32S0Profile,
                        @Cached ConditionProfile utf32S1Profile) {
            doCopyInternal(this, a, byteFromIndexA, arrayB, byteFromIndexB, arrayB.length, byteLength, expectedEncoding,
                            toIndexableNode, utf16Profile, utf16S0Profile, utf32Profile, utf32S0Profile, utf32S1Profile);
        }

        private static void doCopyInternal(Node location, AbstractTruffleString a, int byteFromIndexA, Object arrayB, int byteFromIndexB, int byteLengthB, int byteLength, Encoding expectedEncoding,
                        ToIndexableNode toIndexableNode,
                        ConditionProfile utf16Profile,
                        ConditionProfile utf16S0Profile,
                        ConditionProfile utf32Profile,
                        ConditionProfile utf32S0Profile,
                        ConditionProfile utf32S1Profile) {
            if (byteLength == 0) {
                return;
            }
            a.checkEncoding(expectedEncoding);
            boundsCheckRegionI(byteFromIndexB, byteLength, byteLengthB);
            final int offsetA = a.offset();
            final int offsetB = 0;
            Object arrayA = toIndexableNode.execute(a, a.data());
            if (utf16Profile.profile(isUTF16(expectedEncoding))) {
                a.boundsCheckByteIndexUTF16(byteFromIndexA);
                checkByteLengthUTF16(byteLength);
                final int fromIndexA = rawIndex(byteFromIndexA, expectedEncoding);
                final int fromIndexB = rawIndex(byteFromIndexB, expectedEncoding);
                final int length = rawIndex(byteLength, expectedEncoding);
                a.boundsCheckRegionRaw(fromIndexA, length);
                if (utf16S0Profile.profile(isStride0(a))) {
                    TStringOps.arraycopyWithStride(location,
                                    arrayA, offsetA, 0, fromIndexA,
                                    arrayB, offsetB, 1, fromIndexB, length);
                    return;
                }
            } else if (utf32Profile.profile(isUTF32(expectedEncoding))) {
                a.boundsCheckByteIndexUTF32(byteFromIndexA);
                checkByteLengthUTF32(byteLength);
                final int fromIndexA = rawIndex(byteFromIndexA, expectedEncoding);
                final int fromIndexB = rawIndex(byteFromIndexB, expectedEncoding);
                final int length = rawIndex(byteLength, expectedEncoding);
                a.boundsCheckRegionRaw(fromIndexA, length);
                if (utf32S0Profile.profile(isStride0(a))) {
                    TStringOps.arraycopyWithStride(location,
                                    arrayA, offsetA, 0, fromIndexA,
                                    arrayB, offsetB, 2, fromIndexB, length);
                    return;
                }
                if (utf32S1Profile.profile(isStride1(a))) {
                    TStringOps.arraycopyWithStride(location,
                                    arrayA, offsetA, 1, fromIndexA,
                                    arrayB, offsetB, 2, fromIndexB, length);
                    return;
                }
            }
            final int byteLengthA = a.length() << a.stride();
            boundsCheckRegionI(byteFromIndexA, byteLength, byteLengthA);
            TStringOps.arraycopyWithStride(location,
                            arrayA, offsetA, 0, byteFromIndexA,
                            arrayB, offsetB, 0, byteFromIndexB, byteLength);
        }

        /**
         * Create a new {@link CopyToByteArrayNode}.
         *
         * @since 22.1
         */
        public static CopyToByteArrayNode create() {
            return TruffleStringFactory.CopyToByteArrayNodeGen.create();
        }

        /**
         * Get the uncached version of {@link CopyToByteArrayNode}.
         *
         * @since 22.1
         */
        public static CopyToByteArrayNode getUncached() {
            return TruffleStringFactory.CopyToByteArrayNodeGen.getUncached();
        }
    }

    /**
     * Node to copy a region of a string into native memory. See
     * {@link #execute(AbstractTruffleString, int, Object, int, int, TruffleString.Encoding)} for
     * details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringAccessor.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class CopyToNativeMemoryNode extends Node {

        CopyToNativeMemoryNode() {
        }

        /**
         * Copy a region of the given {@link TruffleString} {@code a}, bounded by
         * {@code byteFromIndexA} and {@code byteLength} into the given interop object representing
         * a native pointer ({@code isPointer(pointerObject)} must return {@code true}), starting at
         * {@code byteFromIndexDst}.
         * <p>
         * This operation requires native access permissions
         * ({@code TruffleLanguage.Env#isNativeAccessAllowed()}).
         *
         * @since 22.1
         */
        public abstract void execute(AbstractTruffleString a, int byteFromIndexA, Object pointerObject, int byteFromIndexDst, int byteLength, Encoding expectedEncoding);

        @Specialization
        void doCopy(AbstractTruffleString a, int byteFromIndexA, Object pointerObject, int byteFromIndexB, int byteLength, Encoding expectedEncoding,
                        @Cached(value = "createInteropLibrary()", uncached = "getUncachedInteropLibrary()") Node interopLibrary,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached ConditionProfile utf16Profile,
                        @Cached ConditionProfile utf16S0Profile,
                        @Cached ConditionProfile utf32Profile,
                        @Cached ConditionProfile utf32S0Profile,
                        @Cached ConditionProfile utf32S1Profile) {
            CopyToByteArrayNode.doCopyInternal(this, a, byteFromIndexA, NativePointer.create(this, pointerObject, interopLibrary, byteFromIndexB), byteFromIndexB, byteFromIndexB + byteLength,
                            byteLength,
                            expectedEncoding, toIndexableNode, utf16Profile, utf16S0Profile, utf32Profile, utf32S0Profile, utf32S1Profile);
        }

        /**
         * Create a new {@link CopyToNativeMemoryNode}.
         *
         * @since 22.1
         */
        public static CopyToNativeMemoryNode create() {
            return TruffleStringFactory.CopyToNativeMemoryNodeGen.create();
        }

        /**
         * Get the uncached version of {@link CopyToNativeMemoryNode}.
         *
         * @since 22.1
         */
        public static CopyToNativeMemoryNode getUncached() {
            return TruffleStringFactory.CopyToNativeMemoryNodeGen.getUncached();
        }
    }

    /**
     * Node to get a {@link java.lang.String} representation of a {@link TruffleString}.
     *
     * @since 22.1
     */
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ToJavaStringNode extends Node {

        ToJavaStringNode() {
        }

        /**
         * Return a {@link java.lang.String} representation of the given {@link TruffleString}. For
         * the {@link Encoding#BYTES} encoding, the returned String uses "\xNN" for every byte >=
         * 128 as the actual interpretation of those bytes is unknown.
         *
         * @since 22.1
         */
        public abstract String execute(AbstractTruffleString a);

        @Specialization
        static String doUTF16(TruffleString a,
                        @Cached ConditionProfile cacheHit,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.ToJavaStringNode toJavaStringNode) {
            if (a.isEmpty()) {
                return "";
            }
            TruffleString cur = a.next;
            if (cur != null) {
                while (cur != a && !cur.isJavaString()) {
                    cur = cur.next;
                }
                if (cacheHit.profile(cur.isJavaString())) {
                    return (String) cur.data();
                }
            }
            cur = a.next;
            if (cur != null) {
                while (cur != a && cur.encoding() != Encoding.UTF_16.id) {
                    cur = cur.next;
                }
            } else {
                cur = a;
            }
            TruffleString s = toJavaStringNode.execute(cur, toIndexableNode.execute(cur, cur.data()));
            a.cacheInsert(s);
            return (String) s.data();
        }

        @Specialization
        static String doMutable(MutableTruffleString a,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached TStringInternalNodes.TransCodeNode transCodeNode,
                        @Cached TStringInternalNodes.CreateJavaStringNode createJavaStringNode) {
            if (a.isEmpty()) {
                return "";
            }
            final AbstractTruffleString utf16String;
            final int codeRangeA;
            if (isUTF16(a) || (codeRangeA = getCodeRangeNode.execute(a)) < Encoding.UTF_16.maxCompatibleCodeRange) {
                utf16String = a;
            } else {
                utf16String = transCodeNode.execute(a, a.data(), getCodePointLengthNode.execute(a), codeRangeA, Encodings.getUTF16());
            }
            return createJavaStringNode.execute(utf16String, utf16String.data());
        }

        /**
         * Create a new {@link ToJavaStringNode}.
         *
         * @since 22.1
         */
        public static ToJavaStringNode create() {
            return TruffleStringFactory.ToJavaStringNodeGen.create();
        }

        /**
         * Get the uncached version of {@link ToJavaStringNode}.
         *
         * @since 22.1
         */
        public static ToJavaStringNode getUncached() {
            return TruffleStringFactory.ToJavaStringNodeGen.getUncached();
        }
    }

    /**
     * Node to get a given string in a specific encoding. See
     * {@link #execute(AbstractTruffleString, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class SwitchEncodingNode extends Node {

        SwitchEncodingNode() {
        }

        /**
         * Returns a version of string {@code a} that is encoded in the given encoding, which may be
         * the string itself or a converted version. Note that the string itself may be returned
         * even if it was originally created using a different encoding, if the string is
         * byte-equivalent in both encodings.
         * <p>
         * If no lossless conversion is possible, the string is converted on a best-effort basis; no
         * exception is thrown and characters which cannot be mapped in the target encoding are
         * replaced by {@code '\ufffd'} (for UTF-*) or {@code '?'}.
         *
         * @since 22.1
         */
        public abstract TruffleString execute(AbstractTruffleString a, Encoding encoding);

        @Specialization(guards = "a.isCompatibleTo(encoding)")
        static TruffleString compatibleImmutable(TruffleString a, @SuppressWarnings("unused") Encoding encoding) {
            assert !a.isJavaString();
            return a;
        }

        @Specialization(guards = "a.isCompatibleTo(encoding)")
        static TruffleString compatibleMutable(MutableTruffleString a, Encoding encoding,
                        @Cached AsTruffleStringNode asTruffleStringNode) {
            return asTruffleStringNode.execute(a, encoding);
        }

        @Specialization(guards = "!a.isCompatibleTo(encoding)")
        static TruffleString transCode(TruffleString a, Encoding encoding,
                        @Cached ConditionProfile cacheHit,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached @Shared("transCodeNode") TStringInternalNodes.TransCodeNode transCodeNode) {
            if (a.isEmpty()) {
                return encoding.getEmpty();
            }
            TruffleString cur = a.next;
            assert !a.isJavaString();
            if (cur != null) {
                while (cur != a && cur.encoding() != encoding.id || (isUTF16(encoding) && cur.isJavaString())) {
                    cur = cur.next;
                }
                if (cacheHit.profile(cur.encoding() == encoding.id)) {
                    assert !cur.isJavaString();
                    return cur;
                }
            }
            TruffleString transCoded = transCodeNode.execute(a, toIndexableNode.execute(a, a.data()), a.codePointLength(), a.codeRange(), encoding.id);
            if (!transCoded.isCacheHead()) {
                a.cacheInsert(transCoded);
            }
            return transCoded;
        }

        @Specialization(guards = "!a.isCompatibleTo(encoding)")
        static TruffleString transCodeMutable(MutableTruffleString a, Encoding encoding,
                        @Cached TStringInternalNodes.GetCodePointLengthNode getCodePointLengthNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeNode,
                        @Cached @Shared("transCodeNode") TStringInternalNodes.TransCodeNode transCodeNode,
                        @Cached TStringOpsNodes.RawArrayCopyBytesNode copyBytesNode,
                        @Cached ConditionProfile isCompatibleProfile) {
            if (a.isEmpty()) {
                return encoding.getEmpty();
            }
            final int codePointLengthA = getCodePointLengthNode.execute(a);
            final int codeRangeA = getCodeRangeNode.execute(a);
            if (isCompatibleProfile.profile(codeRangeA < encoding.maxCompatibleCodeRange)) {
                int strideDst = Stride.fromCodeRange(codeRangeA, encoding.id);
                byte[] arrayDst = new byte[a.length() << strideDst];
                copyBytesNode.execute(a.data(), a.offset(), a.stride(), arrayDst, 0, strideDst, a.length());
                return createFromByteArray(arrayDst, a.length(), strideDst, encoding.id, codePointLengthA, codeRangeA);
            } else {
                return transCodeNode.execute(a, a.data(), codePointLengthA, codeRangeA, encoding.id);
            }
        }

        /**
         * Create a new {@link SwitchEncodingNode}.
         *
         * @since 22.1
         */
        public static SwitchEncodingNode create() {
            return TruffleStringFactory.SwitchEncodingNodeGen.create();
        }

        /**
         * Get the uncached version of {@link SwitchEncodingNode}.
         *
         * @since 22.1
         */
        public static SwitchEncodingNode getUncached() {
            return TruffleStringFactory.SwitchEncodingNodeGen.getUncached();
        }
    }

    /**
     * Node to forcibly assign any encoding to a string. See
     * {@link #execute(AbstractTruffleString, TruffleString.Encoding, TruffleString.Encoding)} for
     * details.
     *
     * @since 22.1
     */
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class ForceEncodingNode extends Node {

        ForceEncodingNode() {
        }

        /**
         * Returns a version of string {@code a} assigned to the given encoding, which may be the
         * string itself or a new string. The string itself may be returned even if it was
         * originally created using a different encoding, if the string is byte-equivalent in both
         * encodings. If the string is not byte-equivalent in both encodings, a new string
         * containing the same bytes but assigned to the new encoding is returned. <b>This node does
         * not transcode the string's contents in any way, it is the "encoding-equivalent" to a
         * C-style reinterpret-cast.</b>
         *
         * @since 22.1
         */
        public abstract TruffleString execute(AbstractTruffleString a, Encoding expectedEncoding, Encoding targetEncoding);

        @Specialization(guards = "isCompatibleAndNotCompacted(a, expectedEncoding, targetEncoding)")
        static TruffleString compatibleImmutable(TruffleString a, @SuppressWarnings("unused") Encoding expectedEncoding, @SuppressWarnings("unused") Encoding targetEncoding) {
            assert !a.isJavaString();
            return a;
        }

        @Specialization(guards = "isCompatibleAndNotCompacted(a, expectedEncoding, targetEncoding)")
        static TruffleString compatibleMutable(MutableTruffleString a, @SuppressWarnings("unused") Encoding expectedEncoding, Encoding targetEncoding,
                        @Cached AsTruffleStringNode asTruffleStringNode) {
            return asTruffleStringNode.execute(a, targetEncoding);
        }

        @Specialization(guards = "!isCompatibleAndNotCompacted(a, expectedEncoding, targetEncoding)")
        static TruffleString reinterpret(AbstractTruffleString a, Encoding expectedEncoding, Encoding targetEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached ConditionProfile managedProfile,
                        @Cached ConditionProfile inflateProfile,
                        @Cached TruffleString.CopyToByteArrayNode copyToByteArrayNode,
                        @Cached TStringInternalNodes.FromBufferWithStringCompactionNode fromBufferWithStringCompactionNode,
                        @Cached TStringInternalNodes.FromNativePointerNode fromNativePointerNode) {
            Object arrayA = toIndexableNode.execute(a, a.data());
            int byteLength = a.length() << expectedEncoding.naturalStride;
            if (managedProfile.profile(arrayA instanceof byte[] || a.isMutable())) {
                final Object arrayNoCompaction;
                if (inflateProfile.profile(isUTF16Or32(a) && a.stride() != expectedEncoding.naturalStride)) {
                    byte[] inflated = new byte[byteLength];
                    copyToByteArrayNode.execute(a, 0, inflated, 0, byteLength, expectedEncoding);
                    arrayNoCompaction = inflated;
                } else {
                    arrayNoCompaction = arrayA;
                }
                return fromBufferWithStringCompactionNode.execute(arrayNoCompaction, a.offset(), byteLength, targetEncoding.id, a.isMutable(), true);
            } else {
                assert arrayA instanceof NativePointer;
                return fromNativePointerNode.execute((NativePointer) arrayA, a.offset(), byteLength, targetEncoding.id, true);
            }
        }

        static boolean isCompatibleAndNotCompacted(AbstractTruffleString a, Encoding expectedEncoding, Encoding targetEncoding) {
            return a.encoding() == targetEncoding.id ||
                            expectedEncoding.naturalStride == targetEncoding.naturalStride && a.stride() == targetEncoding.naturalStride && a.isCompatibleTo(targetEncoding);
        }

        /**
         * Create a new {@link ForceEncodingNode}.
         *
         * @since 22.1
         */
        public static ForceEncodingNode create() {
            return TruffleStringFactory.ForceEncodingNodeGen.create();
        }

        /**
         * Get the uncached version of {@link ForceEncodingNode}.
         *
         * @since 22.1
         */
        public static ForceEncodingNode getUncached() {
            return TruffleStringFactory.ForceEncodingNodeGen.getUncached();
        }
    }

    /**
     * Node to create a {@link TruffleStringIterator}. See
     * {@link #execute(AbstractTruffleString, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class CreateCodePointIteratorNode extends Node {

        CreateCodePointIteratorNode() {
        }

        /**
         * Returns a {@link TruffleStringIterator}, which allows iterating this string's code
         * points.
         *
         * @since 22.1
         */
        public abstract TruffleStringIterator execute(AbstractTruffleString a, Encoding expectedEncoding);

        @Specialization
        static TruffleStringIterator createIterator(AbstractTruffleString a, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode) {
            a.checkEncoding(expectedEncoding);
            return forwardIterator(a, toIndexableNode.execute(a, a.data()), getCodeRangeANode.execute(a));
        }

        /**
         * Create a new {@link CreateCodePointIteratorNode}.
         *
         * @since 22.1
         */
        public static CreateCodePointIteratorNode create() {
            return TruffleStringFactory.CreateCodePointIteratorNodeGen.create();
        }

        /**
         * Get the uncached version of {@link CreateCodePointIteratorNode}.
         *
         * @since 22.1
         */
        public static CreateCodePointIteratorNode getUncached() {
            return TruffleStringFactory.CreateCodePointIteratorNodeGen.getUncached();
        }
    }

    /**
     * Node to create a {@link TruffleStringIterator}. See
     * {@link #execute(AbstractTruffleString, TruffleString.Encoding)} for details.
     *
     * @since 22.1
     */
    @ImportStatic(TStringGuards.class)
    @GeneratePackagePrivate
    @GenerateUncached
    public abstract static class CreateBackwardCodePointIteratorNode extends Node {

        CreateBackwardCodePointIteratorNode() {
        }

        /**
         * Returns a {@link TruffleStringIterator}, which allows iterating this string's code
         * points. The iterator is initialized to begin iteration at the end of the string, use
         * {@link TruffleStringIterator.PreviousNode} to iterate in reverse order.
         *
         * @since 22.1
         */
        public abstract TruffleStringIterator execute(AbstractTruffleString a, Encoding expectedEncoding);

        @Specialization
        static TruffleStringIterator createIterator(AbstractTruffleString a, Encoding expectedEncoding,
                        @Cached ToIndexableNode toIndexableNode,
                        @Cached TStringInternalNodes.GetCodeRangeNode getCodeRangeANode) {
            a.checkEncoding(expectedEncoding);
            return backwardIterator(a, toIndexableNode.execute(a, a.data()), getCodeRangeANode.execute(a));
        }

        /**
         * Create a new {@link CreateBackwardCodePointIteratorNode}.
         *
         * @since 22.1
         */
        public static CreateBackwardCodePointIteratorNode create() {
            return TruffleStringFactory.CreateBackwardCodePointIteratorNodeGen.create();
        }

        /**
         * Get the uncached version of {@link CreateBackwardCodePointIteratorNode}.
         *
         * @since 22.1
         */
        public static CreateBackwardCodePointIteratorNode getUncached() {
            return TruffleStringFactory.CreateBackwardCodePointIteratorNodeGen.getUncached();
        }
    }
}
