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
package com.oracle.truffle.regex.tregex;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.oracle.truffle.api.strings.TruffleStringIterator;
import com.oracle.truffle.regex.tregex.string.AbstractStringBuffer;
import com.oracle.truffle.regex.tregex.string.Encoding;
import com.oracle.truffle.regex.tregex.string.StringBufferUTF16;
import com.oracle.truffle.regex.tregex.string.StringBufferUTF32;
import com.oracle.truffle.regex.tregex.string.StringBufferUTF8;

public class StringTest {

    @Test
    public void testEncodings() {
        testEncodingsRange(Character.MIN_CODE_POINT, Character.MIN_HIGH_SURROGATE - 1);
        testEncodingsRange(Character.MAX_LOW_SURROGATE + 1, Character.MAX_CODE_POINT);
    }

    static void testEncodingsRange(int lo, int hi) {
        int capacity = hi - lo + 1;
        AbstractStringBuffer[] sbs = {
                        new StringBufferUTF8(capacity),
                        new StringBufferUTF16(capacity, Encoding.UTF_16),
                        new StringBufferUTF16(capacity, Encoding.UTF_16BE),
                        new StringBufferUTF32(capacity, Encoding.UTF_32),
                        new StringBufferUTF32(capacity, Encoding.UTF_32BE)
        };

        for (AbstractStringBuffer sb : sbs) {
            for (int i = lo; i <= hi; i++) {
                sb.append(i);
            }
        }

        for (AbstractStringBuffer sb : sbs) {
            TruffleStringIterator it = sb.asTString().createCodePointIteratorUncached(sb.getEncoding().getTStringEncoding());
            for (int i = lo; i <= hi; i++) {
                assertEquals(i, it.nextUncached(sb.getEncoding().getTStringEncoding()));
            }
        }
    }
}
