/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_32;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;

import java.nio.ByteOrder;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringBuilderUTF16;
import com.oracle.truffle.api.strings.TruffleStringIterator;

public class TStringUTF16Tests extends TStringTestBase {

    private static final int UNICODE_REPLACEMENT_CHARACTER = 0xfffd;

    /**
     * GR-40064.
     */
    @Test
    public void testBrokenUTF16() {
        String input = new StringBuilder().append('[').append(Character.toChars(0x10ffff)).append(Character.toChars(0xdc80)).append(']').toString();
        TruffleString utf16 = TruffleString.fromJavaStringUncached(input, UTF_16);
        Assert.assertFalse(utf16.isValidUncached(UTF_16));
        Assert.assertTrue(utf16.codeRangeEqualsUncached(TruffleString.CodeRange.BROKEN));

        Assert.assertEquals(4, utf16.codePointLengthUncached(UTF_16));
        if (UTF_16 == TruffleString.Encoding.UTF_16LE) {
            Assert.assertArrayEquals(new byte[]{91, 0, -1, -37, -1, -33, -128, -36, 93, 0}, utf16.copyToByteArrayUncached(TruffleString.Encoding.UTF_16LE));
        }
        Assert.assertEquals('[', utf16.codePointAtIndexUncached(0, UTF_16));
        Assert.assertEquals(0x10ffff, utf16.codePointAtIndexUncached(1, UTF_16));
        Assert.assertEquals(0xdc80, utf16.codePointAtIndexUncached(2, UTF_16));
        Assert.assertEquals(']', utf16.codePointAtIndexUncached(3, UTF_16));

        TruffleStringIterator it = utf16.createCodePointIteratorUncached(UTF_16);
        Assert.assertEquals('[', it.nextUncached(UTF_16));
        Assert.assertEquals(0x10ffff, it.nextUncached(UTF_16));
        Assert.assertEquals(0xdc80, it.nextUncached(UTF_16));
        Assert.assertEquals(']', it.nextUncached(UTF_16));
        Assert.assertFalse(it.hasNext());

        TruffleString utf32 = TruffleString.fromJavaStringUncached(input, UTF_32);
        Assert.assertEquals(4, utf32.codePointLengthUncached(UTF_32));
        Assert.assertEquals('[', utf32.codePointAtIndexUncached(0, UTF_32));
        Assert.assertEquals(0x10ffff, utf32.codePointAtIndexUncached(1, UTF_32));
        Assert.assertEquals(0xdc80, utf32.codePointAtIndexUncached(2, UTF_32));
        Assert.assertEquals(']', utf32.codePointAtIndexUncached(3, UTF_32));

        it = utf32.createCodePointIteratorUncached(UTF_32);
        Assert.assertEquals('[', it.nextUncached(UTF_32));
        Assert.assertEquals(0x10ffff, it.nextUncached(UTF_32));
        Assert.assertEquals(0xdc80, it.nextUncached(UTF_32));
        Assert.assertEquals(']', it.nextUncached(UTF_32));
        Assert.assertFalse(it.hasNext());

        TruffleString utf8 = TruffleString.fromJavaStringUncached(input, UTF_8);
        Assert.assertEquals(4, utf8.codePointLengthUncached(UTF_8));
        Assert.assertEquals('[', utf8.codePointAtIndexUncached(0, UTF_8));
        Assert.assertEquals(0x10ffff, utf8.codePointAtIndexUncached(1, UTF_8));
        Assert.assertEquals(UNICODE_REPLACEMENT_CHARACTER, utf8.codePointAtIndexUncached(2, UTF_8));
        Assert.assertEquals(']', utf8.codePointAtIndexUncached(3, UTF_8));

        it = utf8.createCodePointIteratorUncached(UTF_8);
        Assert.assertEquals('[', it.nextUncached(UTF_8));
        Assert.assertEquals(0x10ffff, it.nextUncached(UTF_8));
        Assert.assertEquals(UNICODE_REPLACEMENT_CHARACTER, it.nextUncached(UTF_8));
        Assert.assertEquals(']', it.nextUncached(UTF_8));
        Assert.assertFalse(it.hasNext());
    }

    /**
     * GR-40108.
     */
    @Test
    public void testBrokenUTF16ToUTF8() {
        String input = "http://example.com/\uD800\uD801\uDFFE\uDFFF\uFDD0\uFDCF\uFDEF\uFDF0\uFFFE\uFFFF?\uD800\uD801\uDFFE\uDFFF\uFDD0\uFDCF\uFDEF\uFDF0\uFFFE\uFFFF";
        TruffleString utf16 = TruffleString.fromJavaStringUncached(input, UTF_16);
        Assert.assertFalse(utf16.isValidUncached(UTF_16));
        Assert.assertTrue(utf16.codeRangeEqualsUncached(TruffleString.CodeRange.BROKEN));

        TruffleString utf8 = TruffleString.fromJavaStringUncached(input, UTF_8);
        Assert.assertEquals(38, utf16.codePointLengthUncached(UTF_16));
        Assert.assertEquals(80, utf16.byteLength(UTF_16));

        Assert.assertEquals(38, utf8.codePointLengthUncached(UTF_8));
        Assert.assertEquals(76, utf8.byteLength(UTF_8));
        byte[] expectedUTF8Bytes = new byte[]{
                        104, 116, 116, 112, 58, 47, 47, 101, 120, 97, 109, 112, 108, 101, 46, 99, 111, 109, 47, -17,
                        -65, -67, -16, -112, -97, -66, -17, -65, -67, -17, -73, -112, -17, -73, -113, -17, -73, -81, -17, -73,
                        -80, -17, -65, -66, -17, -65, -65, 63, -17, -65, -67, -16, -112, -97, -66, -17, -65, -67, -17, -73,
                        -112, -17, -73, -113, -17, -73, -81, -17, -73, -80, -17, -65, -66, -17, -65, -65};
        byte[] actualUTF8Bytes = utf8.copyToByteArrayUncached(UTF_8);
        Assert.assertArrayEquals(Arrays.toString(actualUTF8Bytes), expectedUTF8Bytes, actualUTF8Bytes);
    }

    @Test(expected = OutOfMemoryError.class)
    public void testStringBuilderAppendCodePoint() {
        TruffleStringBuilderUTF16 sb = TruffleStringBuilder.createUTF16();
        sb.appendCodePointUncached(Character.MAX_CODE_POINT, Integer.MAX_VALUE - 10, false);
    }

    @Test
    public void testToJavaString() {
        TruffleString a = TruffleString.fromCharArrayUTF16Uncached(new char[]{'a', 'b', 'c'});
        Assert.assertEquals("abc", a.toJavaStringUncached());
    }

    private static TruffleString.Encoding getForeignEndian() {
        return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? TruffleString.Encoding.UTF_16BE : TruffleString.Encoding.UTF_16LE;
    }

    private static byte[] getByteSwappedArray(String s) {
        byte[] array = new byte[s.length() << 1];
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                c = Character.reverseBytes(c);
            }
            array[i << 1] = (byte) (c >> 8);
            array[(i << 1) + 1] = (byte) c;
        }
        return array;
    }

    @Test
    public void testForeignEndian() {
        TruffleString a = TruffleString.fromByteArrayUncached(getByteSwappedArray("a\udc00"), getForeignEndian());
        Assert.assertEquals(2, a.codePointLengthUncached(getForeignEndian()));
        Assert.assertEquals("a\udc00", a.toJavaStringUncached());
    }
}
