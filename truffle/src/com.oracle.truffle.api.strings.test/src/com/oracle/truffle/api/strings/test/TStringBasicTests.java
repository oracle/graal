/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.api.strings.test;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringIterator;

public class TStringBasicTests extends TStringTestBase {

    @Test
    public void testIndexOutOfBounds() throws Exception {
        forAllEncodingsAndCodePoints((TruffleString.Encoding encoding, int codepoint) -> {
            TruffleString s = TruffleString.fromCodePointUncached(codepoint, encoding);
            for (int i : new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -1, 1, Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
                expectOutOfBoundsException(() -> s.codePointAtIndexUncached(i, encoding));
            }
        });
    }

    @Test
    public void testEncodeDecode() throws Exception {
        forAllEncodingsAndCodePoints((TruffleString.Encoding encoding, int codepoint) -> {
            testEncodeDecode(codepoint, encoding);
        });
    }

    @Test
    public void testSwitchEncoding() throws Exception {
        forAllEncodingsAndCodePoints((TruffleString.Encoding encoding, int codepoint) -> {
            testTransCode(codepoint, encoding);
        });
    }

    @Test
    public void testEncodingFromJCodingName() throws Exception {
        forAllEncodings(e -> {
            Assert.assertSame(e, TruffleString.Encoding.fromJCodingName(Encodings.getJCoding(e).toString()));
        });
    }

    private static void testTransCode(int codepoint, TruffleString.Encoding encodingA) throws Exception {
        TruffleString stringA = TruffleString.fromCodePointUncached(codepoint, encodingA);
        Assert.assertEquals(codepoint, stringA.codePointAtIndexUncached(0, encodingA));
        if (isAsciiCompatible(encodingA)) {
            for (TruffleString.Encoding encodingB : TruffleString.Encoding.values()) {
                if (isAsciiCompatible(encodingB) && codepoint <= 0x7f) {
                    switchEncodingEquivalentCodePoint(codepoint, encodingA, stringA, encodingB);
                }
            }
        }
        if (isUTF(encodingA)) {
            if (codepoint <= 0xff) {
                switchEncodingEquivalentCodePoint(codepoint, encodingA, stringA, TruffleString.Encoding.ISO_8859_1);
            }
            for (TruffleString.Encoding encodingB : new TruffleString.Encoding[]{TruffleString.Encoding.UTF_8, TruffleString.Encoding.UTF_16, TruffleString.Encoding.UTF_32}) {
                switchEncodingEquivalentCodePoint(codepoint, encodingA, stringA, encodingB);
            }
        }
    }

    private static void switchEncodingEquivalentCodePoint(int codepoint, TruffleString.Encoding encodingA, TruffleString stringA, TruffleString.Encoding encodingB) throws Exception {
        if (encodingA != TruffleString.Encoding.BYTES && encodingB != TruffleString.Encoding.BYTES) {
            TruffleString stringB = stringA.switchEncodingUncached(encodingB);
            Assert.assertEquals(codepoint, stringB.codePointAtIndexUncached(0, encodingB));
            Assert.assertEquals(codepoint, stringB.switchEncodingUncached(encodingA).codePointAtIndexUncached(0, encodingA));
        }
    }

    private static void testEncodeDecode(int codepoint, TruffleString.Encoding encoding) {
        Assert.assertEquals(codepoint, TruffleString.fromCodePointUncached(codepoint, encoding).codePointAtIndexUncached(0, encoding));
        Assert.assertEquals(codepoint, TruffleString.fromCodePointUncached(codepoint, encoding).createCodePointIteratorUncached(encoding).nextUncached());
        Assert.assertEquals(codepoint, TruffleString.fromCodePointUncached(codepoint, encoding).createBackwardCodePointIteratorUncached(encoding).previousUncached());
        if (isAsciiCompatible(encoding) && codepoint <= 0x7f || isUTF(encoding)) {
            String javaString = TruffleString.fromCodePointUncached(codepoint, encoding).toJavaStringUncached();
            Assert.assertEquals(codepoint, javaString.codePointAt(0));
            Assert.assertEquals(codepoint, TruffleString.fromJavaStringUncached(javaString, TruffleString.Encoding.UTF_16).codePointAtIndexUncached(0, TruffleString.Encoding.UTF_16));
            for (int first : new int[]{'x', codepoint}) {
                Assert.assertEquals(codepoint, TruffleString.fromCodePointUncached(first, encoding).concatUncached(
                                TruffleString.fromCodePointUncached(codepoint, encoding), encoding, true).codePointAtIndexUncached(1, encoding));
                Assert.assertEquals(codepoint, TruffleString.fromCodePointUncached(first, encoding).concatUncached(
                                TruffleString.fromCodePointUncached(codepoint, encoding), encoding, true).substringUncached(1, 1, encoding, true).codePointAtIndexUncached(0, encoding));
                TruffleStringIterator it = TruffleString.fromCodePointUncached(first, encoding).concatUncached(
                                TruffleString.fromCodePointUncached(codepoint, encoding), encoding, true).createCodePointIteratorUncached(encoding);
                Assert.assertEquals(first, it.nextUncached());
                Assert.assertEquals(codepoint, it.nextUncached());
                Assert.assertEquals(codepoint, it.previousUncached());
                Assert.assertEquals(first, it.previousUncached());
            }
        }
    }
}
