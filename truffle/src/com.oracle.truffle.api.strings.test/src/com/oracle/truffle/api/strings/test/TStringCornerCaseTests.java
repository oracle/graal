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

import static com.oracle.truffle.api.strings.test.TStringTestUtil.byteArray;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import com.oracle.truffle.api.strings.MutableTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public class TStringCornerCaseTests extends TStringTestBase {

    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    @Test
    public void testTranscodeYieldsEmptyString() {
        byte[] arr = byteArray(27, 40, 66);
        TruffleString a = TruffleString.fromByteArrayUncached(arr, 0, arr.length, TruffleString.Encoding.ISO_2022_JP, false);
        Assert.assertEquals("", a.toString());
    }

    @Test
    public void testForceEncodingStringCompaction() {
        TruffleString a = TruffleString.fromJavaStringUncached("abc", TruffleString.Encoding.UTF_8);
        TruffleString forced = a.forceEncodingUncached(TruffleString.Encoding.UTF_8, TruffleString.Encoding.UTF_8);
        Assert.assertEquals(3, forced.byteLength(TruffleString.Encoding.UTF_8));
    }

    @Test
    public void testForceEncodingStringCompaction2() {
        TruffleString a = TruffleString.fromCodePointUncached('\'', TruffleString.Encoding.US_ASCII);
        Assert.assertEquals('\'', a.forceEncodingUncached(TruffleString.Encoding.UTF_16, TruffleString.Encoding.UTF_16).codePointAtByteIndexUncached(0, TruffleString.Encoding.UTF_16));
    }

    @Test
    public void testForceEncoding3() {
        TruffleString a = TruffleString.fromJavaStringUncached("abcdabcdabcdabcdabcdabcdabcdabcdabcdabcd", 9, 9, TruffleString.Encoding.UTF_16, false);
        Assert.assertEquals(18, a.forceEncodingUncached(TruffleString.Encoding.UTF_16, TruffleString.Encoding.BYTES).byteLength(TruffleString.Encoding.BYTES));
    }

    @Test
    public void testConcatMutable() {
        TruffleString a = TruffleString.Encoding.UTF_8.getEmpty();
        MutableTruffleString b = MutableTruffleString.fromByteArrayUncached(new byte[0], 0, 0, TruffleString.Encoding.BYTES, false);
        Assert.assertTrue(a.concatUncached(b, TruffleString.Encoding.UTF_8, true).isEmpty());
    }

    @Test
    public void testConcatMutable2() {
        TruffleString a = TruffleString.fromJavaStringUncached("abcdabcdabcdabcdabcdabcdabcdabcdabcdabcd", TruffleString.Encoding.BYTES);
        MutableTruffleString b = MutableTruffleString.fromByteArrayUncached("abc".getBytes(StandardCharsets.UTF_8), 0, "abc".length(), TruffleString.Encoding.UTF_8, false);
        Assert.assertEquals(43, a.concatUncached(b, TruffleString.Encoding.BYTES, true).byteLength(TruffleString.Encoding.BYTES));
    }

    @Test
    public void testSubstring() {
        TruffleString a = TruffleString.fromByteArrayUncached(byteArray(0xE0, 0xA0, 0xA1), TruffleString.Encoding.EUC_JP);
        Assert.assertEquals(1, a.substringUncached(2, 1, TruffleString.Encoding.EUC_JP, true).byteLength(TruffleString.Encoding.EUC_JP));
    }

    @Test
    public void testFromCodepoint() {
        TruffleString a = TruffleString.fromCodePointUncached((int) 0x81308130L, TruffleString.Encoding.GB18030, false);
        Assert.assertEquals(1, a.codePointLengthUncached(TruffleString.Encoding.GB18030));
    }

    @Test
    public void testNegativeCodepoint() {
        TruffleString a = TruffleString.fromByteArrayUncached(byteArray(0x8e, 0xa2, 0xa1, 0xa1), TruffleString.Encoding.EUC_TW);
        Assert.assertEquals(0x8ea2a1a1, a.codePointAtByteIndexUncached(0, TruffleString.Encoding.EUC_TW, TruffleString.ErrorHandling.RETURN_NEGATIVE));
    }

    @Test
    public void testMutableImpreciseCodeRange() {
        MutableTruffleString a = MutableTruffleString.fromByteArrayUncached(new byte[]{0}, 0, 1, TruffleString.Encoding.BYTES, false);
        Assert.assertTrue(a.isCompatibleToUncached(TruffleString.Encoding.BYTES));
        Assert.assertEquals(TruffleString.CodeRange.VALID, a.getCodeRangeImpreciseUncached(TruffleString.Encoding.BYTES));
    }

    @Test
    public void testSafePointPollInObjectEquals() {
        char[] chars = new char[2000000];
        Arrays.fill(chars, 'a');
        String s = new String(chars);
        TruffleString t1 = TruffleString.fromConstant(s, TruffleString.Encoding.UTF_16);
        TruffleString t2 = TruffleString.fromConstant(s, TruffleString.Encoding.UTF_16);
        Assert.assertEquals(t1, t2);
    }

    /**
     * Turned off by default because we don't have enough heap space on all CI jobs.
     */
    @Ignore
    @Test
    public void testInflateOverAllocation() {
        TruffleStringBuilder sb = TruffleStringBuilder.createUTF16(MAX_ARRAY_SIZE >> 1);
        sb.appendStringUncached(TruffleString.fromJavaStringUncached("asdf", TruffleString.Encoding.UTF_16));
        sb.appendStringUncached(TruffleString.fromJavaStringUncached("\u2020", TruffleString.Encoding.UTF_16));
        Assert.assertEquals("asdf\u2020", sb.toStringUncached().toJavaStringUncached());
    }

    @Test(expected = OutOfMemoryError.class)
    public void testInflateOverAllocation2() {
        TruffleStringBuilder sb = TruffleStringBuilder.createUTF16(MAX_ARRAY_SIZE >> 1);
        sb.appendCodePointUncached('a', MAX_ARRAY_SIZE >> 1);
        sb.appendStringUncached(TruffleString.fromJavaStringUncached("\u2020", TruffleString.Encoding.UTF_16));
        Assert.assertEquals("asdf\u2020", sb.toStringUncached().toJavaStringUncached());
    }
}
