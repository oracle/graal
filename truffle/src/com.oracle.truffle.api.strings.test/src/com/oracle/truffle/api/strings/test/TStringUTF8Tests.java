/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.strings.TruffleString.Encoding.ISO_8859_1;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.ISO_8859_2;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.US_ASCII;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16BE;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_32;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_32BE;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;
import static com.oracle.truffle.api.strings.test.TStringTestUtil.byteArray;

import java.nio.charset.StandardCharsets;

import org.graalvm.shadowed.org.jcodings.specific.UTF8Encoding;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilderUTF8;
import com.oracle.truffle.api.strings.TruffleStringIterator;

public class TStringUTF8Tests extends TStringTestBase {

    private static final byte[][] VALID = {
                    utf8Encode(0x00),
                    utf8Encode(0x7f),
                    utf8Encode(0x80),
                    utf8Encode(0x7ff),
                    utf8Encode(0x800),
                    utf8Encode(Character.MIN_SURROGATE - 1),
                    utf8Encode(Character.MAX_SURROGATE + 1),
                    utf8Encode(0xffff),
                    utf8Encode(0x10000),
                    utf8Encode(Character.MAX_CODE_POINT),
    };

    private static final byte[][] INVALID = {
                    byteArray(0x80),
                    byteArray(0xc0, 0x80),
                    byteArray(0b11000000),
                    byteArray(0b11000000, 0x80, 0x80),
                    byteArray(0b11100000),
                    byteArray(0b11100000, 0x80),
                    byteArray(0b11100000, 0x80, 0x80, 0x80),
                    byteArray(0b11110000),
                    byteArray(0b11110000, 0x80, 0x80),
                    byteArray(0b11110000, 0x80, 0x80, 0x80, 0x80),
                    byteArray(0b11111000),
                    byteArray(0b11111000, 0x80, 0x80, 0x80, 0x80),
                    byteArray(0b11111100),
                    byteArray(0b11111100, 0x80, 0x80, 0x80, 0x80, 0x80),
                    byteArray(0b11111110),
                    byteArray(0b11111111),
                    byteArray(0xf4, 0x90, 0x80, 0x80),
                    byteArray(0xed, 0xb0, 0x80),
                    byteArray(0xed, 0xbf, 0xbf),
                    byteArray(0xed, 0xa0, 0x80),
                    byteArray(0xed, 0xaf, 0xbf),
                    byteArray(0xc0, 0xbf),
                    byteArray(0xf3, 0x90, 0x80),
                    byteArray(0xf3, 0x90, 0x80, 0x7f),
    };

    private static byte[] utf8Encode(int codepoint) {
        return new StringBuilder().appendCodePoint(codepoint).toString().getBytes(StandardCharsets.UTF_8);
    }

    private static TruffleString asTString(byte[] arr) {
        return asTString(arr, arr.length);
    }

    private static TruffleString asTString(byte[] arr, int length) {
        return TruffleString.fromByteArrayUncached(arr, 0, length, UTF_8, false);
    }

    @Test
    public void testValid() {
        for (byte[] arr : VALID) {
            Assert.assertTrue(TStringTestUtil.hex(arr), asTString(arr).isValidUncached(UTF_8));
        }
    }

    @Test
    public void testInvalid() {
        for (byte[] arr : INVALID) {
            Assert.assertFalse(TStringTestUtil.hex(arr), asTString(arr).isValidUncached(UTF_8));
        }
    }

    @Test
    public void testByteLengthOfInvalidCodePoint() {
        for (byte[] arr : INVALID) {
            checkByteLengthOfCodePoint(arr, arr.length);
        }
    }

    @Test
    public void testByteLengthOfCodePointExhaustive() {
        // Disabled by default because this test takes almost 10 minutes.
        Assume.assumeTrue(false);
        byte[] arr = new byte[4];
        for (long i = 0; i <= 0xff_ff_ff_ffL; i++) {
            arr[0] = (byte) (i & 0xff);
            arr[1] = (byte) ((i >> 8) & 0xff);
            arr[2] = (byte) ((i >> 16) & 0xff);
            arr[3] = (byte) ((i >> 24) & 0xff);
            int length = i <= 0xff ? 1 : i <= 0xff_ff ? 2 : i <= 0xff_ff_ff ? 3 : 4;
            checkByteLengthOfCodePoint(arr, arr.length);
            checkByteLengthOfCodePoint(arr, length);
            // if ((i & 0xffffff) == 0) {
            // TTY.println("progress: " + Long.toHexString(i));
            // }
        }
    }

    private static void checkByteLengthOfCodePoint(byte[] arr, int length) {
        int expected = jcodingsCodePointLength(arr, length);
        int actual = asTString(arr, length).byteLengthOfCodePointUncached(0, UTF_8, TruffleString.ErrorHandling.RETURN_NEGATIVE);
        Assert.assertEquals(expected, actual);
    }

    private static int jcodingsCodePointLength(byte[] arr, int length) {
        final int width = UTF8Encoding.INSTANCE.length(arr, 0, length);
        if (width <= length) {
            return width;
        } else {
            return -1 - (width - length);
        }
    }

    @Test
    public void testCodePointLength1() {
        byte[] arr = byteArray(0xf4, 0x90, 0x80, 0x80, 0x7f, 0x7f);
        TruffleString a = asTString(arr);
        a.toString();
        Assert.assertEquals(6, a.codePointLengthUncached(UTF_8));
    }

    @Test
    public void testCodePointLength2() {
        byte[] arr = byteArray(0, 0, 0xc0, 0xbf);
        TruffleString a = asTString(arr);
        Assert.assertEquals(4, a.codePointLengthUncached(UTF_8));
    }

    @Test
    public void testCodePointLength3() {
        byte[] arr = byteArray(0xf3, 0x90, 0x80, 0x80, 0x7f, 0x7f);
        TruffleString a = asTString(arr);
        a.toString();
        Assert.assertEquals(3, a.codePointLengthUncached(UTF_8));
    }

    @Test
    public void testIncompleteSequence() {
        byte[] arr = byteArray(0xf0, 0x90, 0x80);
        TruffleString a = asTString(arr);
        Assert.assertEquals(3, a.codePointLengthUncached(UTF_8));
        switchIncompleteSequence(a, UTF_16, 0xfffd);
        switchIncompleteSequence(a, UTF_32, 0xfffd);
        switchIncompleteSequence(a, US_ASCII, '?');
        switchIncompleteSequence(a, ISO_8859_1, '?');
        switchIncompleteSequence(a, UTF_16BE, 0xfffd);
        switchIncompleteSequence(a, UTF_32BE, 0xfffd);
        switchIncompleteSequence(a, ISO_8859_2, '?');
        for (TruffleString.ErrorHandling errorHandling : TruffleString.ErrorHandling.values()) {
            int replacement = errorHandling == TruffleString.ErrorHandling.BEST_EFFORT ? 0xfffd : -1;
            TruffleStringIterator it = TruffleString.CreateCodePointIteratorNode.getUncached().execute(a, UTF_8, errorHandling);
            Assert.assertTrue(it.hasNext());
            Assert.assertEquals(replacement, it.nextUncached());
            Assert.assertEquals(replacement, it.nextUncached());
            Assert.assertEquals(replacement, it.nextUncached());
            Assert.assertFalse(it.hasNext());
            TruffleStringIterator itBackwards = TruffleString.CreateBackwardCodePointIteratorNode.getUncached().execute(a, UTF_8, errorHandling);
            Assert.assertTrue(itBackwards.hasPrevious());
            Assert.assertEquals(replacement, itBackwards.previousUncached());
            Assert.assertEquals(replacement, itBackwards.previousUncached());
            Assert.assertEquals(replacement, itBackwards.previousUncached());
            Assert.assertFalse(itBackwards.hasPrevious());
        }
    }

    private static void switchIncompleteSequence(TruffleString a, TruffleString.Encoding enc, int replacement) {
        TruffleString b = a.switchEncodingUncached(enc);
        Assert.assertEquals(1, b.codePointLengthUncached(enc));
        Assert.assertEquals(replacement, b.codePointAtByteIndexUncached(0, enc));
    }

    @Test
    public void testBackwardIteratorExhaustive() {
        StringBuilder sb = new StringBuilder(Character.MAX_CODE_POINT);
        for (int i = 0; i < Character.MIN_SURROGATE; i++) {
            sb.appendCodePoint(i);
        }
        for (int i = Character.MAX_SURROGATE + 1; i <= Character.MAX_CODE_POINT; i++) {
            sb.appendCodePoint(i);
        }
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        TruffleString a = TruffleString.fromByteArrayUncached(bytes, UTF_8, false);
        Assert.assertTrue(a.isValidUncached(UTF_8));
        for (TruffleString.ErrorHandling errorHandling : TruffleString.ErrorHandling.values()) {
            TruffleStringIterator it = TruffleString.CreateBackwardCodePointIteratorNode.getUncached().execute(a, UTF_8, errorHandling);
            for (int i = Character.MAX_CODE_POINT; i >= Character.MAX_SURROGATE + 1; i--) {
                Assert.assertEquals(i, it.previousUncached());
            }
            for (int i = Character.MIN_SURROGATE - 1; i >= 0; i--) {
                Assert.assertEquals(i, it.previousUncached());
            }
            Assert.assertFalse(it.hasPrevious());

            TruffleString b = a.concatUncached(TruffleString.fromByteArrayUncached(byteArray(0xf0, 0x90, 0x80), UTF_8, false), UTF_8, false);
            Assert.assertFalse(b.isValidUncached(UTF_8));
            it = TruffleString.CreateBackwardCodePointIteratorNode.getUncached().execute(b, UTF_8, errorHandling);
            int replacement = errorHandling == TruffleString.ErrorHandling.BEST_EFFORT ? 0xfffd : -1;
            Assert.assertEquals(replacement, it.previousUncached());
            Assert.assertEquals(replacement, it.previousUncached());
            Assert.assertEquals(replacement, it.previousUncached());
            for (int i = Character.MAX_CODE_POINT; i >= Character.MAX_SURROGATE + 1; i--) {
                Assert.assertEquals(i, it.previousUncached());
            }
            for (int i = Character.MIN_SURROGATE - 1; i >= 0; i--) {
                Assert.assertEquals(i, it.previousUncached());
            }
            Assert.assertFalse(it.hasPrevious());
        }
    }

    @Test
    public void testBackwardIteratorConsistencyExhaustive() {
        // Disabled by default because this test takes almost 10 minutes.
        Assume.assumeTrue(false);
        byte[] arr = new byte[4];
        int[] bytePositions = new int[4];
        int[] codepoints = new int[4];
        for (long i = 0; i <= 0xff_ff_ff_ffL; i++) {
            arr[0] = (byte) (i & 0xff);
            arr[1] = (byte) ((i >> 8) & 0xff);
            arr[2] = (byte) ((i >> 16) & 0xff);
            arr[3] = (byte) ((i >> 24) & 0xff);
            int length = i <= 0xff ? 1 : i <= 0xff_ff ? 2 : i <= 0xff_ff_ff ? 3 : 4;
            TruffleString a = TruffleString.fromByteArrayUncached(arr, 0, length, UTF_8, false);
            int codepointLength = a.codePointLengthUncached(UTF_8);
            TruffleStringIterator it = a.createCodePointIteratorUncached(UTF_8);
            for (int j = 0; j < codepointLength; j++) {
                Assert.assertTrue(it.hasNext());
                bytePositions[j] = it.getByteIndex();
                codepoints[j] = it.nextUncached();
            }
            Assert.assertFalse(it.hasNext());
            Assert.assertEquals(length, it.getByteIndex());
            for (int j = codepointLength - 1; j >= 0; j--) {
                Assert.assertTrue(it.hasPrevious());
                Assert.assertEquals(codepoints[j], it.previousUncached());
                Assert.assertEquals(bytePositions[j], it.getByteIndex());
            }
            Assert.assertFalse(it.hasPrevious());
            Assert.assertEquals(0, it.getByteIndex());
            // if ((i & 0xffffff) == 0) {
            // TTY.println("progress: " + Long.toHexString(i));
            // }
        }
    }

    @Test
    public void testIndexOf() {
        TruffleString s1 = TruffleString.fromJavaStringUncached("aaa", UTF_8);
        TruffleString s2 = TruffleString.fromJavaStringUncached("a", UTF_8);
        Assert.assertEquals(-1, s1.byteIndexOfStringUncached(s2, 1, 1, UTF_8));
    }

    @Test
    public void testIndexOf2() {
        TruffleString a = TruffleString.fromCodePointUncached(0x102, UTF_8);
        TruffleString b = TruffleString.fromCodePointUncached(0x10_0304, UTF_8);
        TruffleString s1 = a.repeatUncached(10, UTF_8);
        TruffleString s2 = a.concatUncached(b, UTF_8, false);
        Assert.assertEquals(-1, s1.byteIndexOfStringUncached(s2, 0, s1.byteLength(UTF_8), UTF_8));
        Assert.assertEquals(-1, s1.indexOfStringUncached(s2, 0, s1.codePointLengthUncached(UTF_8), UTF_8));
    }

    @Test
    public void testIndexOf3() {
        TruffleString a = TruffleString.fromJavaStringUncached("aaa", UTF_8);
        TruffleString b = TruffleString.fromJavaStringUncached("baa", UTF_8);
        Assert.assertEquals(-1, a.lastIndexOfStringUncached(b, 3, 0, UTF_8));
    }

    @Test
    public void testIndexOf4() {
        TruffleString a = TruffleString.fromJavaStringUncached("defghiabc", UTF_8);
        TruffleString b = TruffleString.fromJavaStringUncached("def", UTF_8);
        Assert.assertEquals(-1, a.lastIndexOfStringUncached(b, 9, 1, UTF_8));
    }

    @Test
    public void testIndexOf5() {
        TruffleString ts1 = TruffleString.fromJavaStringUncached("a\u00A3b\u00A3", UTF_8);
        TruffleString ts2 = TruffleString.fromJavaStringUncached("a\u00A3", UTF_8);
        Assert.assertEquals(-1, ts1.lastIndexOfStringUncached(ts2, 4, 1, UTF_8));
        Assert.assertEquals(-1, ts1.lastByteIndexOfStringUncached(ts2, 6, 1, UTF_8));
    }

    @Test
    public void testIndexOf6() {
        TruffleString ts1 = TruffleString.fromJavaStringUncached("<......\u043c...", UTF_8);
        TruffleString ts2 = TruffleString.fromJavaStringUncached("<", UTF_8);
        Assert.assertEquals(0, ts1.lastIndexOfStringUncached(ts2, ts1.codePointLengthUncached(UTF_8), 0, UTF_8));
    }

    @Test
    public void testIteratorPrev() {
        TruffleStringIterator it = asTString(byteArray(' ', 'a', 'b', 'c', ' ', 0x80)).createBackwardCodePointIteratorUncached(UTF_8);
        it.previousUncached();
        Assert.assertEquals(5, it.getByteIndex());
    }

    @Test(expected = OutOfMemoryError.class)
    public void testStringBuilderAppendCodePoint() {
        TruffleStringBuilderUTF8 sb = TruffleStringBuilder.createUTF8();
        sb.appendCodePointUncached(Character.MAX_CODE_POINT, Integer.MAX_VALUE - 10, false);
    }
}
