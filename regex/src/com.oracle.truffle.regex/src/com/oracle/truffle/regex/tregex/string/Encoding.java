/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.string;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.regex.charset.CharMatchers;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.nodes.dfa.SequentialMatchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.SequentialMatchers.Builder;

public enum Encoding {

    UTF_8(TruffleString.Encoding.UTF_8),
    UTF_16(TruffleString.Encoding.UTF_16),
    UTF_16BE(TruffleString.Encoding.UTF_16BE),
    UTF_16_RAW(TruffleString.Encoding.UTF_16),
    UTF_32(TruffleString.Encoding.UTF_32),
    UTF_32BE(TruffleString.Encoding.UTF_32BE),
    LATIN_1(TruffleString.Encoding.ISO_8859_1),
    BYTES(TruffleString.Encoding.BYTES),
    ASCII(TruffleString.Encoding.US_ASCII);

    private final TruffleString.Encoding tsEncoding;

    Encoding(TruffleString.Encoding tsEncoding) {
        this.tsEncoding = tsEncoding;
    }

    private static final CodePointSet FULL_UNICODE_SET = CodePointSet.createNoDedup(0, 0x10ffff);
    private static final CodePointSet UTF16_RAW_FULL_SET = CodePointSet.createNoDedup(0, 0xffff);
    public static final String[] ALL_NAMES = {UTF_8.getName(), UTF_16.getName(), UTF_16BE.getName(), UTF_16_RAW.getName(), UTF_32.getName(), UTF_32BE.getName(), ASCII.getName(), LATIN_1.getName(),
                    BYTES.getName()};

    public static Encoding getEncoding(String name) {
        return switch (name) {
            case "UTF-8" -> UTF_8;
            case "UTF-16" -> UTF_16;
            case "UTF-16BE" -> UTF_16BE;
            case "UTF-16-RAW" -> UTF_16_RAW;
            case "UTF-32" -> UTF_32;
            case "UTF-32BE" -> UTF_32BE;
            case "BYTES" -> BYTES;
            case "LATIN-1" -> LATIN_1;
            case "ASCII" -> ASCII;
            default -> null;
        };
    }

    public String getName() {
        return switch (this) {
            case UTF_8 -> "UTF-8";
            case UTF_16 -> "UTF-16";
            case UTF_16BE -> "UTF-16BE";
            case UTF_16_RAW -> "UTF-16-RAW";
            case UTF_32 -> "UTF-32";
            case UTF_32BE -> "UTF-32BE";
            case LATIN_1 -> "LATIN-1";
            case BYTES -> "BYTES";
            case ASCII -> "ASCII";
        };
    }

    public boolean isUTF16() {
        return this == UTF_16 || this == UTF_16BE;
    }

    public boolean isUTF32() {
        return this == UTF_32 || this == UTF_32BE;
    }

    public TruffleString.Encoding getTStringEncoding() {
        return tsEncoding;
    }

    public int getStride() {
        return switch (this) {
            case UTF_32, UTF_32BE -> 2;
            case UTF_16, UTF_16BE, UTF_16_RAW -> 1;
            default -> 0;
        };
    }

    public static int getMinValue() {
        return 0;
    }

    public int getMaxValue() {
        return switch (this) {
            case UTF_8, UTF_16, UTF_16BE, UTF_32, UTF_32BE -> Character.MAX_CODE_POINT;
            case UTF_16_RAW -> Character.MAX_VALUE;
            case LATIN_1, BYTES -> 0xff;
            case ASCII -> 0x7f;
        };
    }

    public CodePointSet getFullSet() {
        return switch (this) {
            case UTF_8, UTF_16, UTF_16BE, UTF_32, UTF_32BE -> FULL_UNICODE_SET;
            case UTF_16_RAW -> UTF16_RAW_FULL_SET;
            case LATIN_1, BYTES -> Constants.BYTE_RANGE;
            case ASCII -> Constants.ASCII_RANGE;
        };
    }

    public int getEncodedSize(int codepoint) {
        switch (this) {
            case UTF_8 -> {
                if (codepoint < 0x80) {
                    return 1;
                } else if (codepoint < 0x800) {
                    return 2;
                } else if (codepoint < 0x10000) {
                    return 3;
                } else {
                    return 4;
                }
            }
            case UTF_16, UTF_16BE -> {
                return codepoint < 0x10000 ? 1 : 2;
            }
            default -> {
                assert this != UTF_16_RAW || codepoint <= 0xffff : codepoint;
                return 1;
            }
        }
    }

    /**
     * Returns {@code true} iff the given code point set may not match a variable amount of
     * {@code byte}/{@code char}s in an encoded string, i.e. the set is bounded by exactly one of
     * the following ranges:
     *
     * <pre>
     * UTF-8:
     * [0x0     - 0x7f    ] (one byte)
     * [0x80    - 0x7ff   ] (two bytes)
     * [0x800   - 0xffff  ] (three bytes)
     * [0x10000 - 0x10ffff] (four bytes)
     * 
     * UTF-16:
     * [0x0     - 0xffff  ] (one char)
     * [0x10000 - 0x10ffff] (two chars)
     * </pre>
     */
    public boolean isFixedCodePointWidth(CodePointSet set) {
        if (set.isEmpty()) {
            return true;
        }
        int min = set.getMin();
        int max = set.getMax();
        switch (this) {
            case UTF_8 -> {
                return !(min < 0x80 && max >= 0x80 || min < 0x800 && max >= 0x800 || min < 0x10000 && max >= 0x10000);
            }
            case UTF_16, UTF_16BE -> {
                return !(min < 0x10000 && max > 0x10000);
            }
            default -> {
                assert this != UTF_16_RAW || max <= 0xffff : set;
                return true;
            }
        }
    }

    public boolean isUnicode() {
        return switch (this) {
            case LATIN_1, ASCII, BYTES -> false;
            default -> true;
        };
    }

    public AbstractStringBuffer createStringBuffer(int capacity) {
        return switch (this) {
            case UTF_8 -> new StringBufferUTF8(capacity);
            case UTF_16, UTF_16BE, UTF_16_RAW -> new StringBufferUTF16(capacity, this);
            case UTF_32, UTF_32BE -> new StringBufferUTF32(capacity, this);
            case LATIN_1, BYTES, ASCII -> new StringBufferBytes(capacity, this);
        };
    }

    private int getNumberOfCodeRanges() {
        return switch (this) {
            case UTF_8, UTF_16, UTF_16BE, UTF_32, UTF_32BE -> 4;
            case UTF_16_RAW -> 3;
            case LATIN_1, BYTES, ASCII -> 1;
        };
    }

    public SequentialMatchers.Builder createMatchersBuilder() {
        return new SequentialMatchers.Builder(getNumberOfCodeRanges());
    }

    private static final CodePointSet[] SPLIT_RANGES_UTF_8 = {Constants.ASCII_RANGE, Constants.UTF8_TWO_BYTE_RANGE, Constants.UTF8_THREE_BYTE_RANGE, Constants.ASTRAL_SYMBOLS};
    private static final CodePointSet[] SPLIT_RANGES_UTF_16 = {Constants.ASCII_RANGE, Constants.BYTE_RANGE, Constants.BMP_RANGE_WITHOUT_LATIN1, Constants.ASTRAL_SYMBOLS_AND_LONE_SURROGATES};
    private static final CodePointSet[] SPLIT_RANGES_UTF_16_RAW = {Constants.ASCII_RANGE, Constants.BYTE_RANGE, Constants.BMP_RANGE_WITHOUT_LATIN1};
    private static final CodePointSet[] SPLIT_RANGES_UTF_32 = {Constants.ASCII_RANGE, Constants.BYTE_RANGE, Constants.BMP_WITHOUT_LATIN1_WITHOUT_SURROGATES,
                    Constants.ASTRAL_SYMBOLS_AND_LONE_SURROGATES};

    public void createMatcher(Builder matchersBuilder, int i, CodePointSet cps, CompilationBuffer compilationBuffer) {
        assert cps.getMax() <= getMaxValue();
        switch (this) {
            case LATIN_1, ASCII, BYTES -> matchersBuilder.getBuffer(0).set(i, CharMatchers.createMatcher(cps, compilationBuffer));
            default -> matchersBuilder.createSplitMatcher(i, cps, compilationBuffer, switch (this) {
                case UTF_8 -> SPLIT_RANGES_UTF_8;
                case UTF_16, UTF_16BE -> SPLIT_RANGES_UTF_16;
                case UTF_32, UTF_32BE -> SPLIT_RANGES_UTF_32;
                case UTF_16_RAW -> SPLIT_RANGES_UTF_16_RAW;
                default -> throw CompilerDirectives.shouldNotReachHere();
            });
        }
    }

    public SequentialMatchers toMatchers(Builder mb) {
        return switch (this) {
            case UTF_8 -> new SequentialMatchers.UTF8SequentialMatchers(mb.materialize(0), mb.materialize(1), mb.materialize(2), mb.materialize(3), mb.getNoMatchSuccessor());
            case UTF_16, UTF_16BE, UTF_32, UTF_32BE ->
                new SequentialMatchers.UTF16Or32SequentialMatchers(mb.materialize(0), mb.materialize(1), mb.materialize(2), mb.materialize(3), mb.getNoMatchSuccessor());
            case UTF_16_RAW -> new SequentialMatchers.UTF16RawSequentialMatchers(mb.materialize(0), mb.materialize(1), mb.materialize(2), mb.getNoMatchSuccessor());
            case LATIN_1, BYTES, ASCII -> new SequentialMatchers.SimpleSequentialMatchers(mb.materialize(0), mb.getNoMatchSuccessor());
        };
    }

    @Override
    public String toString() {
        return getName();
    }

    public static boolean isHighSurrogate(int c, boolean forward) {
        return forward ? isHighSurrogate(c) : isLowSurrogate(c);
    }

    public static boolean isLowSurrogate(int c, boolean forward) {
        return forward ? isLowSurrogate(c) : isHighSurrogate(c);
    }

    public static boolean isHighSurrogate(int c) {
        assert c <= 0xffff;
        return (c >> 10) == 0x36;
    }

    public static boolean isLowSurrogate(int c) {
        assert c <= 0xffff;
        return (c >> 10) == 0x37;
    }
}
