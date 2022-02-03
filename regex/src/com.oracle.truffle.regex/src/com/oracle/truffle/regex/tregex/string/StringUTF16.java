/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.strings.TruffleString;

public final class StringUTF16 implements AbstractString {

    private final String str;

    public StringUTF16(char[] str) {
        this(new String(str));
    }

    public StringUTF16(String str) {
        this.str = str;
    }

    @Override
    public int encodedLength() {
        return str.length();
    }

    public char charAt(int i) {
        return str.charAt(i);
    }

    @Override
    public String toString() {
        return str;
    }

    @Override
    public Object content() {
        return str;
    }

    @Override
    public StringUTF16 substring(int start, int end) {
        return new StringUTF16(str.substring(start, end));
    }

    @Override
    public boolean regionMatches(int offset, AbstractString other, int ooffset, int encodedLength) {
        return str.regionMatches(offset, ((StringUTF16) other).str, ooffset, encodedLength);
    }

    @Override
    public TruffleString asTString() {
        return TruffleString.fromJavaStringUncached(str, TruffleString.Encoding.UTF_16);
    }

    @Override
    public TruffleString.WithMask asTStringMask(TruffleString pattern) {
        return TruffleString.WithMask.createUTF16Uncached(pattern, str.toCharArray());
    }

    @Override
    public AbstractStringIterator iterator() {
        return new StringUTF16Iterator(str);
    }

    private static final class StringUTF16Iterator extends AbstractStringIterator {

        private final String str;

        private StringUTF16Iterator(String str) {
            this.str = str;
        }

        @Override
        public boolean hasNext() {
            return i < str.length();
        }

        @Override
        public int nextInt() {
            char c = str.charAt(i++);
            if (Encodings.Encoding.UTF16.isHighSurrogate(c) && hasNext() && Encodings.Encoding.UTF16.isLowSurrogate(str.charAt(i))) {
                return Character.toCodePoint(c, str.charAt(i++));
            }
            return c;
        }
    }
}
