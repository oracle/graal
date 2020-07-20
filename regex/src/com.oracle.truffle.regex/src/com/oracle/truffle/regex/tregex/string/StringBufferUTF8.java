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

import com.oracle.truffle.regex.tregex.buffer.ByteArrayBuffer;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;

public final class StringBufferUTF8 extends ByteArrayBuffer implements AbstractStringBuffer {

    public StringBufferUTF8() {
        this(16);
    }

    public StringBufferUTF8(int capacity) {
        super(capacity);
    }

    @Override
    public Encoding getEncoding() {
        return Encodings.UTF_8;
    }

    @Override
    public void append(int codepoint) {
        int n = getEncoding().getEncodedSize(codepoint);
        int newLength = length() + n;
        ensureCapacity(newLength);
        setLength(newLength);
        int i = newLength;
        if (n == 1) {
            set(--i, (byte) codepoint);
            return;
        }
        int c = codepoint;
        // Checkstyle: stop
        switch (n) {
            case 4:
                set(--i, (byte) (0x80 | (c & 0x3f)));
                c >>>= 6;
            case 3:
                set(--i, (byte) (0x80 | (c & 0x3f)));
                c >>>= 6;
            default:
                set(--i, (byte) (0x80 | (c & 0x3f)));
                c >>>= 6;
                set(--i, (byte) ((0xf00 >>> n) | c));
        }
        // Checkstyle: resume
    }

    @Override
    public void appendOR(int cp1, int cp2) {
        int n = getEncoding().getEncodedSize(cp1);
        assert getEncoding().getEncodedSize(cp2) == n;
        int newLength = length() + n;
        ensureCapacity(newLength);
        setLength(newLength);
        int i = newLength;
        if (n == 1) {
            set(--i, (byte) (cp1 | cp2));
            return;
        }
        int c1 = cp1;
        int c2 = cp2;
        // Checkstyle: stop
        switch (n) {
            case 4:
                set(--i, (byte) (0x80 | ((c1 | c2) & 0x3f)));
                c1 >>>= 6;
                c2 >>>= 6;
            case 3:
                set(--i, (byte) (0x80 | ((c1 | c2) & 0x3f)));
                c1 >>>= 6;
                c2 >>>= 6;
            default:
                set(--i, (byte) (0x80 | ((c1 | c2) & 0x3f)));
                c1 >>>= 6;
                c2 >>>= 6;
                set(--i, (byte) ((0xf00 >>> n) | (c1 | c2)));
        }
        // Checkstyle: resume
    }

    @Override
    public void appendXOR(int cp1, int cp2) {
        int n = getEncoding().getEncodedSize(cp1);
        assert getEncoding().getEncodedSize(cp2) == n;
        int newLength = length() + n;
        ensureCapacity(newLength);
        setLength(newLength);
        int i = newLength;
        if (n == 1) {
            set(--i, (byte) (cp1 ^ cp2));
            return;
        }
        int c1 = cp1;
        int c2 = cp2;
        // Checkstyle: stop
        switch (n) {
            case 4:
                set(--i, (byte) ((c1 ^ c2) & 0x3f));
                c1 >>>= 6;
                c2 >>>= 6;
            case 3:
                set(--i, (byte) ((c1 ^ c2) & 0x3f));
                c1 >>>= 6;
                c2 >>>= 6;
            default:
                set(--i, (byte) ((c1 ^ c2) & 0x3f));
                c1 >>>= 6;
                c2 >>>= 6;
                set(--i, (byte) (c1 ^ c2));
        }
        // Checkstyle: resume
    }

    @Override
    public StringUTF8 materialize() {
        return new StringUTF8(toArray());
    }
}
