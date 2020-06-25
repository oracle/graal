/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.charset.CharMatchers;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAStateNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAStateNode.LoopOptIndexOfAnyByteNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAStateNode.LoopOptIndexOfAnyCharNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAStateNode.LoopOptIndexOfStringNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.DFAStateNode.LoopOptimizationNode;
import com.oracle.truffle.regex.tregex.nodes.dfa.Matchers;
import com.oracle.truffle.regex.tregex.nodes.dfa.Matchers.Builder;
import com.oracle.truffle.regex.tregex.util.Exceptions;

public final class Encodings {

    private static final CodePointSet FULL_UNICODE_SET = CodePointSet.createNoDedup(0, 0x10ffff);

    public static final Encoding UTF_8 = new Encoding.UTF8();
    public static final Encoding UTF_16 = new Encoding.UTF16();
    public static final Encoding UTF_32 = new Encoding.UTF32();
    public static final Encoding UTF_16_RAW = new Encoding.UTF16Raw();
    public static final Encoding LATIN_1 = new Encoding.Latin1();

    public static Encoding getEncoding(String name) {
        switch (name) {
            case "UTF-8":
                return UTF_8;
            case "UTF-16":
                return UTF_16;
            case "UTF-32":
                return UTF_32;
            case "UTF-16-RAW":
                return UTF_16_RAW;
            case "BYTES":
                return LATIN_1;
            case "LATIN-1":
                return LATIN_1;
            default:
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw Exceptions.shouldNotReachHere("Unknown Encoding \"" + name + "\"");
        }
    }

    public abstract static class Encoding {

        public abstract String getName();

        public int getMinValue() {
            return 0;
        }

        public abstract int getMaxValue();

        public abstract CodePointSet getFullSet();

        public abstract int getEncodedSize(int codepoint);

        public abstract boolean isFixedCodePointWidth(CodePointSet set);

        public abstract AbstractStringBuffer createStringBuffer(int capacity);

        public abstract DFAStateNode.LoopOptimizationNode extractLoopOptNode(CodePointSet loopCPS);

        public abstract int getNumberOfDecodingSteps();

        public Matchers.Builder createMatchersBuilder() {
            return new Matchers.Builder(getNumberOfDecodingSteps());
        }

        public abstract void createMatcher(Builder matchersBuilder, int i, CodePointSet cps, CompilationBuffer compilationBuffer);

        public abstract Matchers toMatchers(Builder matchersBuilder);

        public static final class UTF32 extends Encoding {

            private UTF32() {
            }

            @Override
            public String getName() {
                return "UTF-32";
            }

            @Override
            public int getMaxValue() {
                return Character.MAX_CODE_POINT;
            }

            @Override
            public CodePointSet getFullSet() {
                return FULL_UNICODE_SET;
            }

            @Override
            public int getEncodedSize(int codepoint) {
                return 1;
            }

            @Override
            public boolean isFixedCodePointWidth(CodePointSet set) {
                return true;
            }

            @Override
            public StringBufferUTF32 createStringBuffer(int capacity) {
                return new StringBufferUTF32(capacity);
            }

            @Override
            public LoopOptimizationNode extractLoopOptNode(CodePointSet cps) {
                // TODO: not implemented yet
                return null;
            }

            @Override
            public int getNumberOfDecodingSteps() {
                return 1;
            }

            @Override
            public void createMatcher(Builder matchersBuilder, int i, CodePointSet cps, CompilationBuffer compilationBuffer) {
                matchersBuilder.getBuffer(0).set(i, CharMatchers.createMatcher(cps, compilationBuffer));
            }

            @Override
            public Matchers toMatchers(Builder matchersBuilder) {
                return new Matchers.SimpleMatchers(matchersBuilder.materialize(0), matchersBuilder.getNoMatchSuccessor());
            }
        }

        public static final class UTF16 extends Encoding {

            private UTF16() {
            }

            @Override
            public String getName() {
                return "UTF-16";
            }

            @Override
            public int getMaxValue() {
                return Character.MAX_CODE_POINT;
            }

            @Override
            public CodePointSet getFullSet() {
                return FULL_UNICODE_SET;
            }

            @Override
            public int getEncodedSize(int codepoint) {
                return codepoint < 0x10000 ? 1 : 2;
            }

            /**
             * Returns {@code true} iff the given code point set may not match a variable amount of
             * {@code char}s in an UTF-16 encoded string, i.e. the set is bounded by exactly one of
             * the following ranges:
             *
             * <pre>
             * [0x0     - 0xffff  ] (one char)
             * [0x10000 - 0x10ffff] (two chars)
             * </pre>
             *
             * @see Constants#BMP_RANGE
             * @see Constants#ASTRAL_SYMBOLS
             */
            @Override
            public boolean isFixedCodePointWidth(CodePointSet set) {
                if (set.isEmpty()) {
                    return true;
                }
                int min = set.getMin();
                int max = set.getMax();
                return !(min < 0x10000 && max > 0x10000);
            }

            @Override
            public LoopOptimizationNode extractLoopOptNode(CodePointSet cps) {
                if (cps.inverseGetMax(this) <= 0xffff) {
                    char[] indexOfChars = cps.inverseToCharArray(this);
                    for (char c : indexOfChars) {
                        if (Constants.SURROGATES.contains(c)) {
                            return null;
                        }
                    }
                    return new LoopOptIndexOfAnyCharNode(indexOfChars);
                } else if (cps.inverseValueCount(this) == 1) {
                    StringBufferUTF16 sb = createStringBuffer(2);
                    sb.append(cps.inverseGetMin(this));
                    return new LoopOptIndexOfStringNode(sb.materialize(), null);
                } else {
                    return null;
                }
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

            @Override
            public StringBufferUTF16 createStringBuffer(int capacity) {
                return new StringBufferUTF16(capacity);
            }

            @Override
            public int getNumberOfDecodingSteps() {
                return 2;
            }

            @Override
            public Matchers.Builder createMatchersBuilder() {
                return new Matchers.Builder(3);
            }

            @Override
            public void createMatcher(Builder matchersBuilder, int i, CodePointSet cps, CompilationBuffer compilationBuffer) {
                matchersBuilder.createSplitMatcher(i, cps, compilationBuffer, Constants.BYTE_RANGE, Constants.BMP_RANGE_WITHOUT_LATIN1, Constants.ASTRAL_SYMBOLS);
            }

            @Override
            public Matchers toMatchers(Builder matchersBuilder) {
                return new Matchers.UTF16Matchers(matchersBuilder.materialize(0), matchersBuilder.materialize(1), matchersBuilder.materialize(2), matchersBuilder.getNoMatchSuccessor());
            }
        }

        public static final class UTF16Raw extends Encoding {

            private static final CodePointSet UTF16_RAW_FULL_SET = CodePointSet.createNoDedup(0, 0xffff);

            private UTF16Raw() {
            }

            @Override
            public String getName() {
                return "UTF-16-RAW";
            }

            @Override
            public int getMaxValue() {
                return Character.MAX_VALUE;
            }

            @Override
            public CodePointSet getFullSet() {
                return UTF16_RAW_FULL_SET;
            }

            @Override
            public int getEncodedSize(int codepoint) {
                return codepoint < 0x10000 ? 1 : 2;
            }

            @Override
            public boolean isFixedCodePointWidth(CodePointSet set) {
                return true;
            }

            @Override
            public StringBufferUTF16 createStringBuffer(int capacity) {
                return new StringBufferUTF16(capacity);
            }

            @Override
            public LoopOptimizationNode extractLoopOptNode(CodePointSet cps) {
                return new LoopOptIndexOfAnyCharNode(cps.inverseToCharArray(this));
            }

            @Override
            public int getNumberOfDecodingSteps() {
                return 1;
            }

            @Override
            public Matchers.Builder createMatchersBuilder() {
                return new Matchers.Builder(2);
            }

            @Override
            public void createMatcher(Builder matchersBuilder, int i, CodePointSet cps, CompilationBuffer compilationBuffer) {
                assert cps.getMax() <= getMaxValue();
                matchersBuilder.createSplitMatcher(i, cps, compilationBuffer, Constants.BYTE_RANGE, Constants.BMP_RANGE_WITHOUT_LATIN1);
            }

            @Override
            public Matchers toMatchers(Builder matchersBuilder) {
                return new Matchers.UTF16RawMatchers(matchersBuilder.materialize(0), matchersBuilder.materialize(1), matchersBuilder.getNoMatchSuccessor());
            }
        }

        public static final class UTF8 extends Encoding {

            @Override
            public String getName() {
                return "UTF-8";
            }

            @Override
            public int getMaxValue() {
                return Character.MAX_CODE_POINT;
            }

            @Override
            public CodePointSet getFullSet() {
                return FULL_UNICODE_SET;
            }

            @Override
            public int getEncodedSize(int codepoint) {
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

            /**
             * Returns {@code true} iff the given code point set may not match a variable amount of
             * {@code byte}s in an UTF-8 encoded string, i.e. the set is bounded by exactly one of
             * the following ranges:
             *
             * <pre>
             * [0x0     - 0x7f    ] (one byte)
             * [0x80    - 0x7ff   ] (two bytes)
             * [0x800   - 0xffff  ] (three bytes)
             * [0x10000 - 0x10ffff] (four bytes)
             * </pre>
             */
            @Override
            public boolean isFixedCodePointWidth(CodePointSet set) {
                if (set.isEmpty()) {
                    return true;
                }
                int min = set.getMin();
                int max = set.getMax();
                return !(min < 0x80 && max >= 0x80 || min < 0x800 && max >= 0x800 || min < 0x10000 && max > 0x10000);
            }

            @Override
            public StringBufferUTF8 createStringBuffer(int capacity) {
                return new StringBufferUTF8(capacity);
            }

            @Override
            public LoopOptimizationNode extractLoopOptNode(CodePointSet cps) {
                if (cps.inverseGetMax(this) <= 0x7f) {
                    byte[] indexOfChars = cps.inverseToByteArray(this);
                    return new LoopOptIndexOfAnyByteNode(indexOfChars);
                } else if (cps.inverseValueCount(this) == 1) {
                    StringBufferUTF8 sb = createStringBuffer(4);
                    sb.append(cps.inverseGetMin(this));
                    return new LoopOptIndexOfStringNode(sb.materialize(), new StringUTF8(new byte[sb.length()]));
                } else {
                    return null;
                }
            }

            @Override
            public int getNumberOfDecodingSteps() {
                return 4;
            }

            @Override
            public void createMatcher(Builder matchersBuilder, int i, CodePointSet cps, CompilationBuffer compilationBuffer) {
                matchersBuilder.createSplitMatcher(i, cps, compilationBuffer, Constants.ASCII_RANGE, Constants.UTF8_TWO_BYTE_RANGE, Constants.UTF8_THREE_BYTE_RANGE, Constants.ASTRAL_SYMBOLS);
            }

            @Override
            public Matchers toMatchers(Builder matchersBuilder) {
                return new Matchers.UTF8Matchers(matchersBuilder.materialize(0), matchersBuilder.materialize(1), matchersBuilder.materialize(2), matchersBuilder.materialize(3),
                                matchersBuilder.getNoMatchSuccessor());
            }
        }

        public static final class Latin1 extends Encoding {

            private Latin1() {
            }

            @Override
            public String getName() {
                return "LATIN-1";
            }

            @Override
            public int getMaxValue() {
                return 0xff;
            }

            @Override
            public CodePointSet getFullSet() {
                return Constants.BYTE_RANGE;
            }

            @Override
            public int getEncodedSize(int codepoint) {
                return 1;
            }

            @Override
            public boolean isFixedCodePointWidth(CodePointSet set) {
                return true;
            }

            @Override
            public StringBufferUTF16 createStringBuffer(int capacity) {
                return new StringBufferUTF16(capacity);
            }

            @Override
            public LoopOptimizationNode extractLoopOptNode(CodePointSet cps) {
                return new LoopOptIndexOfAnyCharNode(cps.inverseToCharArray(this));
            }

            @Override
            public int getNumberOfDecodingSteps() {
                return 1;
            }

            @Override
            public void createMatcher(Builder matchersBuilder, int i, CodePointSet cps, CompilationBuffer compilationBuffer) {
                matchersBuilder.getBuffer(0).set(i, CharMatchers.createMatcher(cps, compilationBuffer));
            }

            @Override
            public Matchers toMatchers(Builder matchersBuilder) {
                return new Matchers.SimpleMatchers(matchersBuilder.materialize(0), matchersBuilder.getNoMatchSuccessor());
            }
        }
    }
}
