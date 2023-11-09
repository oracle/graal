/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.truffle.api.strings.test.ops;

import static com.oracle.truffle.api.strings.TruffleString.Encoding.BYTES;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.ISO_8859_1;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.US_ASCII;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_32;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;
import static com.oracle.truffle.api.strings.test.TStringTestUtil.byteArray;
import static org.junit.runners.Parameterized.Parameter;

import java.util.Arrays;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.test.TStringTestBase;

@RunWith(Parameterized.class)
public class TStringToValidStringTest extends TStringTestBase {

    @Parameter public TruffleString.ToValidStringNode node;

    @Parameters(name = "{0}")
    public static Iterable<TruffleString.ToValidStringNode> data() {
        return Arrays.asList(TruffleString.ToValidStringNode.create(), TruffleString.ToValidStringNode.getUncached());
    }

    @Test
    public void testAll() throws Exception {
        forAllStrings(new TruffleString.Encoding[]{US_ASCII, ISO_8859_1, BYTES, UTF_8, UTF_16, UTF_32}, true, (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
            TruffleString wellFormed = node.execute(a, encoding);
            if (isValid && a instanceof TruffleString) {
                Assert.assertSame(a, wellFormed);
            }
            Assert.assertTrue(wellFormed.isValidUncached(encoding));
        });
    }

    @Test
    public void testAscii() {
        testAscii(byteArray('a', '?'), byteArray('a', 0xff));
        testAscii(byteArray('a', '?'), byteArray('a', 0x80));
        testAscii(byteArray('a', '?', 'b'), byteArray('a', 0xff, 'b'));
        testAscii(byteArray('a', '?', 'b'), byteArray('a', 0x80, 'b'));
        testAscii(byteArray('a', 0x7f, 'b'), byteArray('a', 0x7f, 'b'));
    }

    @Test
    public void testUTF8() {
        testUTF8(byteArray('a', 0xEF, 0xBF, 0xBD), byteArray('a', 0xff));
        testUTF8(byteArray('a', 0xEF, 0xBF, 0xBD), byteArray('a', 0xf0, 0x90));
        testUTF8(byteArray('a', 0xEF, 0xBF, 0xBD), byteArray('a', 0xf0, 0x90, 0x80));
        testUTF8(byteArray('a', 0xEF, 0xBF, 0xBD, 0xf0, 0x90, 0x80, 0x80), byteArray('a', 0xf0, 0x90, 0x80, 0xf0, 0x90, 0x80, 0x80));
        testUTF8(byteArray('a', 0xf0, 0x90, 0x80, 0x80, 0xEF, 0xBF, 0xBD), byteArray('a', 0xf0, 0x90, 0x80, 0x80, 0xf0, 0x90, 0x80));
        testUTF8(byteArray('a', 0xf0, 0x90, 0x80, 0x80), byteArray('a', 0xf0, 0x90, 0x80, 0x80));
        testUTF8(byteArray('a', 0xEF, 0xBF, 0xBD, 0xEF, 0xBF, 0xBD), byteArray('a', 0xf8, 0x90));
        testUTF8(byteArray('a', 0xEF, 0xBF, 0xBD, 'b'), byteArray('a', 0xff, 'b'));
        testUTF8(byteArray('a', 0xEF, 0xBF, 0xBD, 'b'), byteArray('a', 0xf0, 0x90, 'b'));
        testUTF8(byteArray('a', 0xEF, 0xBF, 0xBD, 'b'), byteArray('a', 0xf0, 0x90, 0x80, 'b'));
        testUTF8(byteArray('a', 0xf0, 0x90, 0x80, 0x80, 'b'), byteArray('a', 0xf0, 0x90, 0x80, 0x80, 'b'));
        testUTF8(byteArray('a', 0xEF, 0xBF, 0xBD, 0xf0, 0x90, 0x80, 0x80, 'b'), byteArray('a', 0xf0, 0x90, 0x80, 0xf0, 0x90, 0x80, 0x80, 'b'));
        testUTF8(byteArray('a', 0xEF, 0xBF, 0xBD, 0xEF, 0xBF, 0xBD, 'b'), byteArray('a', 0xf8, 0x90, 'b'));
    }

    private void testAscii(byte[] expected, byte[] input) {
        testByteArray(expected, input, US_ASCII);
    }

    private void testUTF8(byte[] expected, byte[] input) {
        testByteArray(expected, input, UTF_8);
    }

    private void testByteArray(byte[] expected, byte[] input, TruffleString.Encoding encoding) {
        TruffleString wellFormed = node.execute(TruffleString.fromByteArrayUncached(input, encoding), encoding);
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(Byte.toUnsignedInt(expected[i]), wellFormed.readByteUncached(i, encoding));
        }
        Assert.assertTrue(wellFormed.isValidUncached(encoding));
    }

    @Test
    public void testUTF16() {
        testUTF16("a\ufffd", "a\udfff");
        testUTF16("a\ufffd", "a\udbff");
        testUTF16("a\ufffd\ufffd", "a\udfff\udfff");
        testUTF16("a\ufffd\ufffd", "a\udbff\udbff");
        testUTF16("a\udbff\udfff\ufffd", "a\udbff\udfff\udbff");
        testUTF16("a\udbff\udfff\ufffdb", "a\udbff\udfff\udbffb");
    }

    private void testUTF16(String expected, String input) {
        TruffleString wellFormed = node.execute(TruffleString.fromJavaStringUncached(input, UTF_16), UTF_16);
        Assert.assertEquals(expected, wellFormed.toJavaStringUncached());
        Assert.assertTrue(wellFormed.isValidUncached(UTF_16));
    }

    @Test
    public void testUTF32() {
        testUTF32(new int[]{'a', 0xfffd}, new int[]{'a', Character.MIN_SURROGATE});
        testUTF32(new int[]{'a', 0xfffd}, new int[]{'a', Character.MAX_SURROGATE});
        testUTF32(new int[]{'a', 0xfffd}, new int[]{'a', Integer.MAX_VALUE});
        testUTF32(new int[]{'a', 0xfffd}, new int[]{'a', Integer.MIN_VALUE});
        testUTF32(new int[]{'a', 0xfffd}, new int[]{'a', 0x110000});
        testUTF32(new int[]{'a', 0xfffd}, new int[]{'a', 0xffff_ffff});
        testUTF32(new int[]{'a', Character.MAX_CODE_POINT}, new int[]{'a', Character.MAX_CODE_POINT});
        testUTF32(new int[]{'a', Character.MAX_CODE_POINT, 0xfffd}, new int[]{'a', Character.MAX_CODE_POINT, Character.MIN_SURROGATE});
    }

    private void testUTF32(int[] expected, int[] input) {
        TruffleString wellFormed = node.execute(TruffleString.fromIntArrayUTF32Uncached(input), UTF_32);
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(expected[i], wellFormed.codePointAtIndexUncached(i, UTF_32));
        }
        Assert.assertTrue(wellFormed.isValidUncached(UTF_32));
    }

    @Test
    public void testNull() throws Exception {
        expectNullPointerException(() -> node.execute(null, UTF_16));
        expectNullPointerException(() -> node.execute(S_UTF16, null));
    }
}
