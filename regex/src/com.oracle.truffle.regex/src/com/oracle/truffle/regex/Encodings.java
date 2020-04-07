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
package com.oracle.truffle.regex;

public final class Encodings {

    public static final Encoding UTF_8 = new Encoding.UTF8();
    public static final Encoding UTF_16 = new Encoding.UTF16();
    public static final Encoding UTF_32 = new Encoding.UTF32();

    public abstract static class Encoding {

        public abstract String getName();

        public abstract int getEncodedSize(int codepoint);

        private static final class UTF32 extends Encoding {

            @Override
            public String getName() {
                return "UTF-32";
            }

            @Override
            public int getEncodedSize(int codepoint) {
                return 1;
            }
        }

        private static final class UTF16 extends Encoding {

            @Override
            public String getName() {
                return "UTF-16";
            }

            @Override
            public int getEncodedSize(int codepoint) {
                return codepoint < 0x10000 ? 1 : 2;
            }
        }

        private static final class UTF8 extends Encoding {

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
        }
    }
}
