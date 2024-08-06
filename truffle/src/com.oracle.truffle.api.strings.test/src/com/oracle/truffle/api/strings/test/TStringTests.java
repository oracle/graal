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

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.strings.TruffleString;

public class TStringTests {

    private static final String longStr = mkLongString();

    private static String mkLongString() {
        char[] chars = new char[40]; // see TStringConstants.LAZY_CONCAT_MIN_LENGTH
        Arrays.fill(chars, 'x');
        return new String(chars);
    }

    private static TruffleString fjs(String s) {
        return TruffleString.FromJavaStringNode.getUncached().execute(s, TruffleString.Encoding.UTF_16);
    }

    private static TruffleString fjsLong(String s) {
        return fjs(longStr + s);
    }

    @Test
    public void testASCII() {
        String s = "asdf";
        TruffleString ts = fjs(s);
        Assert.assertEquals(s, ts.toString());
        Assert.assertEquals(s.length(), ts.byteLength(TruffleString.Encoding.UTF_16) >> 1);
        Assert.assertEquals(s.length(), ts.codePointLengthUncached(TruffleString.Encoding.UTF_16));
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int iBytes = i << 1;
            int lengthBytes = s.length() << 1;
            TruffleString cs = TruffleString.FromCodePointNode.getUncached().execute(c, TruffleString.Encoding.UTF_16, true);
            Assert.assertEquals(c, ts.readCharUTF16Uncached(i));
            Assert.assertEquals(c, ts.codePointAtIndexUncached(i, TruffleString.Encoding.UTF_16, TruffleString.ErrorHandling.BEST_EFFORT));
            Assert.assertEquals(c, ts.codePointAtByteIndexUncached(iBytes, TruffleString.Encoding.UTF_16, TruffleString.ErrorHandling.BEST_EFFORT));
            Assert.assertEquals(i, ts.indexOfCodePointUncached(c, 0, s.length(), TruffleString.Encoding.UTF_16));
            Assert.assertEquals(iBytes, ts.byteIndexOfCodePointUncached(c, 0, lengthBytes, TruffleString.Encoding.UTF_16));
            Assert.assertEquals(i, ts.indexOfStringUncached(cs, 0, s.length(), TruffleString.Encoding.UTF_16));
            Assert.assertEquals(iBytes, ts.byteIndexOfStringUncached(cs, 0, lengthBytes, TruffleString.Encoding.UTF_16));
            Assert.assertEquals(i, ts.lastIndexOfCodePointUncached(c, s.length(), 0, TruffleString.Encoding.UTF_16));
            Assert.assertEquals(iBytes, ts.lastByteIndexOfCodePointUncached(c, lengthBytes, 0, TruffleString.Encoding.UTF_16));
            Assert.assertEquals(i, ts.lastIndexOfStringUncached(cs, s.length(), 0, TruffleString.Encoding.UTF_16));
            Assert.assertEquals(iBytes, ts.lastByteIndexOfStringUncached(cs, lengthBytes, 0, TruffleString.Encoding.UTF_16));
        }
    }

    @Test
    public void testEqualsConcat() {
        checkEquals(() -> new TruffleString[]{fjs("ab"), fjs("a").concatUncached(fjs("b"), TruffleString.Encoding.UTF_16, true)});
        checkEquals(() -> new TruffleString[]{fjsLong("ab"), fjsLong("a").concatUncached(fjs("b"), TruffleString.Encoding.UTF_16, true)});
    }

    @Test
    public void testEqualsSubstring() {
        checkEquals(() -> new TruffleString[]{fjs("ab"), fjs("\u2020ab").substringUncached(1, 2, TruffleString.Encoding.UTF_16, true),
                        fjsLong("\u2020ab").substringUncached(longStr.length() + 1, 2, TruffleString.Encoding.UTF_16, true)});
        checkEquals(() -> new TruffleString[]{fjsLong("ab"), fjsLong("ab\u2020").substringUncached(0, longStr.length() + 2, TruffleString.Encoding.UTF_16, true)});
    }

    @Test
    public void testParseInt() throws TruffleString.NumberFormatException {
        for (int i : new int[]{0, 1, -1, 100, -100, Integer.MIN_VALUE + 1, Integer.MIN_VALUE, Integer.MAX_VALUE - 1, Integer.MAX_VALUE}) {
            for (int radix : new int[]{2, 8, 10, 16}) {
                checkParseIntParseLong(i, fjs(Integer.toString(i, radix)), radix);
                checkParseIntParseLong(i, fjs(Integer.toString(i, radix).toUpperCase(Locale.ENGLISH)), radix);
                if (radix == 10) {
                    checkParseIntParseLong(i, TruffleString.FromLongNode.getUncached().execute(i, TruffleString.Encoding.UTF_16, true));
                }
            }
        }
        for (long i : new long[]{Long.MIN_VALUE + 1, Long.MIN_VALUE, Long.MAX_VALUE - 1, Long.MAX_VALUE}) {
            for (int radix : new int[]{2, 8, 10, 16}) {
                Assert.assertEquals(i, fjs(Long.toString(i, radix)).parseLongUncached(radix));
                Assert.assertEquals(i, fjs(Long.toString(i, radix).toUpperCase(Locale.ENGLISH)).parseLongUncached(radix));
                if (radix == 10) {
                    Assert.assertEquals(i, TruffleString.FromLongNode.getUncached().execute(i, TruffleString.Encoding.UTF_16, true).parseLongUncached());
                }
            }
        }
    }

    private static void checkParseIntParseLong(int i, TruffleString s) throws TruffleString.NumberFormatException {
        checkParseIntParseLong(i, s, 10);
    }

    private static void checkParseIntParseLong(int i, TruffleString s, int radix) throws TruffleString.NumberFormatException {
        Assert.assertEquals(i, s.parseIntUncached(radix));
        Assert.assertEquals(i, s.parseLongUncached(radix));
        if (radix == 10) {
            Assert.assertEquals(i, s.parseDoubleUncached(), 0);
        }
    }

    private static void checkEquals(Supplier<TruffleString[]> stringSupplier) {
        checkHashLib(stringSupplier.get());
        checkHashTString(stringSupplier.get());
        checkEqualsLib(stringSupplier.get());
    }

    private static void checkHashLib(TruffleString[] strings) {
        assert strings.length > 1;
        for (int i = 1; i < strings.length; i++) {
            Assert.assertEquals(strings[0].hashCodeUncached(TruffleString.Encoding.UTF_16), strings[i].hashCodeUncached(TruffleString.Encoding.UTF_16));
        }
    }

    private static void checkHashTString(TruffleString[] strings) {
        assert strings.length > 1;
        for (int i = 1; i < strings.length; i++) {
            Assert.assertEquals(strings[0].hashCode(), strings[i].hashCode());
        }
    }

    private static void checkEqualsLib(TruffleString[] strings) {
        assert strings.length > 1;
        for (int i = 1; i < strings.length; i++) {
            assertTrue(strings[0].equalsUncached(strings[i], TruffleString.Encoding.UTF_16));
        }
    }
}
