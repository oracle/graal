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

import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_32;

import com.oracle.truffle.api.strings.TranscodingErrorHandler;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

import java.nio.ByteOrder;

public class TStringUTF32Tests extends TStringTestBase {

    @Test
    public void testBroken() {
        TruffleString ts = TruffleString.fromJavaStringUncached("\ud803\udfff\ud800", UTF_32);
        Assert.assertEquals(2, ts.codePointLengthUncached(UTF_32));
        Assert.assertEquals(TruffleString.CodeRange.BROKEN, ts.getCodeRangeUncached(UTF_32));
        Assert.assertFalse(ts.isValidUncached(UTF_32));
    }

    @Test
    public void testBroken2() {
        TruffleString ts = TruffleString.fromJavaStringUncached("\ud800", UTF_32);
        Assert.assertEquals(1, ts.codePointLengthUncached(UTF_32));
        Assert.assertEquals(TruffleString.CodeRange.BROKEN, ts.getCodeRangeUncached(UTF_32));
        Assert.assertFalse(ts.isValidUncached(UTF_32));
    }

    @Test
    public void testBroken3() {
        TruffleStringBuilder sb = TruffleStringBuilder.create(TruffleString.Encoding.UTF_32);
        sb.appendCodePointUncached(0xD801, 1, true);
        sb.appendCodePointUncached(0xDC00, 1, true);
        TruffleString ts1 = sb.toStringUncached();
        TruffleString ts2 = ts1.switchEncodingUncached(TruffleString.Encoding.UTF_8, TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8);
        TruffleString ts4 = ts2.switchEncodingUncached(TruffleString.Encoding.UTF_32, TranscodingErrorHandler.DEFAULT_KEEP_SURROGATES_IN_UTF8);
        Assert.assertEquals(2, ts4.codePointLengthUncached(TruffleString.Encoding.UTF_32));
        Assert.assertEquals(0xD801, ts4.codePointAtIndexUncached(0, TruffleString.Encoding.UTF_32));
        Assert.assertEquals(0xDC00, ts4.codePointAtIndexUncached(1, TruffleString.Encoding.UTF_32));
    }

    private static TruffleString.Encoding getForeignEndian() {
        return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? TruffleString.Encoding.UTF_32BE : TruffleString.Encoding.UTF_32LE;
    }

    private static byte[] getByteSwappedArray(String s) {
        byte[] array = new byte[s.length() << 2];
        int i = 0;
        for (int cp : s.codePoints().toArray()) {
            int c = cp; // checkstyle
            if (ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                c = Integer.reverseBytes(c);
            }
            array[i << 2] = (byte) (c >> 24);
            array[(i << 2) + 1] = (byte) (c >> 16);
            array[(i << 2) + 2] = (byte) (c >> 8);
            array[(i << 2) + 3] = (byte) c;
            i++;
        }
        return array;
    }

    @Test
    public void testForeignEndian() {
        TruffleString a = TruffleString.fromByteArrayUncached(getByteSwappedArray("a\udc00b"), getForeignEndian());
        Assert.assertEquals(3, a.codePointLengthUncached(getForeignEndian()));
        Assert.assertEquals("a\udc00b", a.toJavaStringUncached());
    }
}
