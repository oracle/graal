/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

public final class StringUTF32 implements AbstractString {

    private final int[] str;

    public StringUTF32(int[] str) {
        this.str = str;
    }

    @Override
    public int encodedLength() {
        return str.length;
    }

    @Override
    public Object content() {
        return str;
    }

    @Override
    public String toString() {
        return defaultToString();
    }

    @Override
    public StringUTF32 substring(int start, int end) {
        return new StringUTF32(Arrays.copyOfRange(str, start, end));
    }

    @Override
    public boolean regionMatches(int offset, AbstractString other, int ooffset, int encodedLength) {
        int[] o = ((StringUTF32) other).str;
        if (offset + encodedLength > str.length || ooffset + encodedLength > o.length) {
            return false;
        }
        for (int i = 0; i < encodedLength; i++) {
            if (str[offset + i] != o[ooffset + i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public AbstractStringIterator iterator() {
        return new StringUTF32Iterator(str);
    }

    private static final class StringUTF32Iterator extends AbstractStringIterator {

        private final int[] str;

        private StringUTF32Iterator(int[] str) {
            this.str = str;
        }

        @Override
        public boolean hasNext() {
            return i < str.length;
        }

        @Override
        public int nextInt() {
            return str[i++];
        }
    }
}
