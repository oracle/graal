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

import com.oracle.truffle.regex.tregex.buffer.CharArrayBuffer;
import com.oracle.truffle.regex.tregex.string.Encodings.Encoding;

public final class StringBufferUTF16 extends CharArrayBuffer implements AbstractStringBuffer {

    public StringBufferUTF16() {
        super();
    }

    public StringBufferUTF16(int initialCapacity) {
        super(initialCapacity);
    }

    @Override
    public Encoding getEncoding() {
        return Encodings.UTF_16;
    }

    @Override
    public void append(int codepoint) {
        int n = getEncoding().getEncodedSize(codepoint);
        int newLength = length() + n;
        ensureCapacity(newLength);
        if (n == 1) {
            set(length(), (char) codepoint);
        } else {
            set(length(), Character.highSurrogate(codepoint));
            set(length() + 1, Character.lowSurrogate(codepoint));
        }
        setLength(newLength);
    }

    @Override
    public void appendOR(int c1, int c2) {
        int n = getEncoding().getEncodedSize(c1);
        assert getEncoding().getEncodedSize(c2) == n;
        int newLength = length() + n;
        ensureCapacity(newLength);
        if (n == 1) {
            set(length(), (char) (c1 | c2));
        } else {
            set(length(), (char) (Character.highSurrogate(c1) | Character.highSurrogate(c2)));
            set(length() + 1, (char) (Character.lowSurrogate(c1) | Character.lowSurrogate(c2)));
        }
        setLength(newLength);
    }

    @Override
    public void appendXOR(int c1, int c2) {
        int n = getEncoding().getEncodedSize(c1);
        assert getEncoding().getEncodedSize(c2) == n;
        int newLength = length() + n;
        ensureCapacity(newLength);
        if (n == 1) {
            set(length(), (char) (c1 ^ c2));
        } else {
            set(length(), (char) (Character.highSurrogate(c1) ^ Character.highSurrogate(c2)));
            set(length() + 1, (char) (Character.lowSurrogate(c1) ^ Character.lowSurrogate(c2)));
            assert Integer.bitCount(get(length())) + Integer.bitCount(get(length() + 1)) == 1;
        }
        setLength(newLength);
    }

    @Override
    public StringUTF16 materialize() {
        return new StringUTF16(toArray());
    }
}
