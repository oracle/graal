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

import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.Constants;

public final class Encodings {

    public static final Encoding UTF_8 = new Encoding.UTF8();
    public static final Encoding UTF_16 = new Encoding.UTF16();
    public static final Encoding UTF_32 = new Encoding.UTF32();

    public static final Encoding UTF_16_RAW = new Encoding.UTF16Raw();

    public abstract static class Encoding {

        public abstract String getName();

        public abstract int getEncodedSize(int codepoint);

        public abstract boolean isFixedCodePointWidth(CodePointSet set);

        public abstract AbstractStringBuffer createStringBuffer(int capacity);

        public static final class UTF32 extends Encoding {

            private UTF32() {
            }

            @Override
            public String getName() {
                return "UTF-32";
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
        }

        public static final class UTF16 extends Encoding {

            private UTF16() {
            }

            @Override
            public String getName() {
                return "UTF-16";
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
        }

        public static final class UTF16Raw extends Encoding {

            private UTF16Raw() {
            }

            @Override
            public String getName() {
                return "UTF-16-RAW";
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
        }

        public static final class UTF8 extends Encoding {

            @Override
            public String getName() {
                return "UTF-8";
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
        }
    }
}
