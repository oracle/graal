/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.strings.TruffleString.fromByteArrayUncached;
import static com.oracle.truffle.api.strings.TruffleString.fromCharArrayUTF16Uncached;
import static com.oracle.truffle.api.strings.TruffleString.fromCodePointUncached;
import static com.oracle.truffle.api.strings.TruffleString.fromIntArrayUTF32Uncached;
import static com.oracle.truffle.api.strings.TruffleString.fromLongUncached;
import static com.oracle.truffle.api.strings.TruffleString.fromNativePointerUncached;

import java.nio.ByteOrder;

import org.graalvm.shadowed.org.jcodings.Encoding;
import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.InternalByteArray;
import com.oracle.truffle.api.strings.MutableTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;
import com.oracle.truffle.api.strings.TruffleStringIterator;

public class TStringConstructorTests extends TStringTestBase {

    @Test
    public void testFromCodePointInvalid() throws Exception {
        forAllEncodingsAndInvalidCodePoints((TruffleString.Encoding encoding, int codepoint) -> {
            Assert.assertNull(fromCodePointUncached(codepoint, encoding, false));
            if ((isUTF16(encoding) || isUTF32(encoding)) && codepoint <= 0xffff && Character.isSurrogate((char) codepoint)) {
                Assert.assertNotNull(fromCodePointUncached(codepoint, encoding, true));
            }
        });
    }

    @Test
    public void testFromCodePoint() throws Exception {
        forAllEncodingsAndCodePoints((TruffleString.Encoding encoding, int codepoint) -> {
            TruffleString s = fromCodePointUncached(codepoint, encoding);
            Assert.assertEquals(codepoint, s.codePointAtIndexUncached(0, encoding, TruffleString.ErrorHandling.BEST_EFFORT));
            Assert.assertEquals(codepoint, s.codePointAtByteIndexUncached(0, encoding, TruffleString.ErrorHandling.BEST_EFFORT));
            Assert.assertTrue(s.isValidUncached(encoding));
        });
    }

    @Test
    public void testFromLong() throws Exception {
        forAllEncodings((TruffleString.Encoding encoding) -> {
            var testValues = new long[]{
                            Long.MIN_VALUE,
                            Long.MIN_VALUE + 1L,
                            Integer.MIN_VALUE - 1L,
                            Integer.MIN_VALUE,
                            Integer.MIN_VALUE + 1L,
                            Short.MIN_VALUE,
                            -12345,
                            -100,
                            -10,
                            -1,
                            0,
                            1,
                            10,
                            100,
                            12345,
                            3101342585L, // hashCode() == 0
                            Short.MAX_VALUE,
                            Integer.MAX_VALUE - 1L,
                            Integer.MAX_VALUE,
                            Integer.MAX_VALUE + 1L,
                            Long.MAX_VALUE - 1L,
                            Long.MAX_VALUE};
            for (long l : testValues) {
                if (isAsciiCompatible(encoding)) {
                    TruffleString eager = fromLongUncached(l, encoding, false);
                    Assert.assertEquals(l, eager.parseLongUncached());
                    Assert.assertEquals(l, eager.parseDoubleUncached(), 0);
                    TruffleString lazy = fromLongUncached(l, encoding, true);
                    Assert.assertEquals(l, lazy.parseDoubleUncached(), 0);
                    if ((int) l == l) {
                        Assert.assertEquals(l, eager.parseIntUncached());
                        Assert.assertEquals(l, lazy.parseIntUncached());
                    }

                    String javaString = Long.toString(l);
                    Assert.assertEquals(maskZero(javaString.hashCode()), eager.hashCodeUncached(encoding));
                    Assert.assertEquals(maskZero(javaString.hashCode()), lazy.hashCodeUncached(encoding));
                    TruffleString expectedString = TruffleString.fromJavaStringUncached(javaString, encoding);
                    Assert.assertEquals(maskZero(javaString.hashCode()), expectedString.hashCodeUncached(encoding));
                    Assert.assertEquals(javaString.hashCode(), expectedString.toJavaStringUncached().hashCode());
                    Assert.assertEquals(eager, expectedString);
                    Assert.assertEquals(lazy, expectedString);
                    Assert.assertEquals(javaString.length(), eager.byteLength(TruffleString.Encoding.US_ASCII));
                    Assert.assertEquals(javaString.length(), lazy.byteLength(TruffleString.Encoding.US_ASCII));
                } else {
                    expectUnsupportedOperationException(() -> fromLongUncached(l, encoding, false));
                    expectUnsupportedOperationException(() -> fromLongUncached(l, encoding, true));
                }
            }
        });
    }

    static int maskZero(int hash) {
        if (hash == 0) {
            return -1;
        }
        return hash;
    }

    @Test
    public void testFromByteArray() throws Exception {
        byte[] empty = {};
        forAllEncodings((TruffleString.Encoding encoding) -> {
            for (boolean copy : new boolean[]{true, false}) {
                TruffleString emptyString = TruffleString.fromByteArrayUncached(empty, encoding, copy);
                Assert.assertEquals(0, emptyString.byteLength(encoding));
                Assert.assertEquals(0, emptyString.codePointLengthUncached(encoding));
                if (isAsciiCompatible(encoding)) {
                    Assert.assertEquals(TruffleString.CodeRange.ASCII, emptyString.getCodeRangeUncached(encoding));
                    byte[] ascii = new byte[128 << getNaturalStride(encoding)];
                    for (int i = 0; i < 128; i++) {
                        TStringTestUtil.writeValue(ascii, getNaturalStride(encoding), i, i);
                    }
                    TruffleString s = fromByteArrayUncached(ascii, 0, ascii.length, encoding, copy);
                    int readByteOffset = 0;
                    if (isUTF32(encoding) && ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                        readByteOffset = 3;
                    }
                    if (isUTF16(encoding) && ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN) {
                        readByteOffset = 1;
                    }
                    TruffleStringIterator it = s.createCodePointIteratorUncached(encoding);
                    for (int i = 0; i < 128; i++) {
                        Assert.assertEquals(i, s.readByteUncached(byteIndex(i, encoding) + readByteOffset, encoding));
                        Assert.assertEquals(i, s.codePointAtByteIndexUncached(byteIndex(i, encoding), encoding, TruffleString.ErrorHandling.BEST_EFFORT));
                        Assert.assertEquals(i, s.codePointAtIndexUncached(i, encoding, TruffleString.ErrorHandling.BEST_EFFORT));
                        Assert.assertTrue(it.hasNext());
                        Assert.assertEquals(i, it.nextUncached(encoding));
                        Assert.assertEquals(i, s.indexOfCodePointUncached(i, 0, 128, encoding));
                        Assert.assertEquals(i, s.indexOfStringUncached(fromCodePointUncached(i, encoding), 0, 128, encoding));
                    }
                }

            }
        });
        forAllEncodingsAndCodePointLists((TruffleString.Encoding encoding, int[] codepointArray) -> {
            final int[][] cps;
            if (isUTF16(encoding) || isUTF32(encoding)) {
                cps = new int[][]{
                                new int[]{0x00, 0x7f},
                                new int[]{0x00, 0x7f, 0xff},
                                new int[]{0x00, 0x7f, 0xff, 0x100, 0xffff},
                                codepointArray};
            } else {
                cps = new int[][]{codepointArray};
            }
            for (int[] codepoints : cps) {
                TruffleStringBuilder sbBytes = TruffleStringBuilder.create(encoding);
                TruffleStringBuilder sbCP = TruffleStringBuilder.create(encoding);
                TruffleStringBuilder sbCPStrings = TruffleStringBuilder.create(encoding);
                Encoding jCoding = Encodings.getJCoding(encoding);
                int byteLength = 0;
                int[] byteIndices = new int[codepoints.length];
                for (int i = 0; i < codepoints.length; i++) {
                    byteIndices[i] = byteLength;
                    byteLength += jCoding.codeToMbcLength(codepoints[i]);
                }
                byte[] array = new byte[byteLength];

                for (int i = 0; i < codepoints.length; i++) {
                    jCoding.codeToMbc(codepoints[i], array, byteIndices[i]);
                    sbCP.appendCodePointUncached(codepoints[i]);
                    sbCPStrings.appendStringUncached(fromCodePointUncached(codepoints[i], encoding));
                }
                if (isUTF32(encoding)) {
                    for (int cp : codepoints) {
                        sbBytes.appendCodePointUncached(cp);
                    }
                } else if (isUTF16(encoding)) {
                    for (int i = 0; i < byteLength / 2; i++) {
                        sbBytes.appendCharUTF16Uncached((char) TStringTestUtil.readValue(array, 1, i));
                    }
                } else {
                    for (byte b : array) {
                        sbBytes.appendByteUncached(b);
                    }
                }
                AbstractTruffleString[] strings = new AbstractTruffleString[isUTF32(encoding) || isUTF16(encoding) ? 9 : 8];
                strings[0] = fromByteArrayUncached(array, 0, array.length, encoding, true);
                strings[1] = fromByteArrayUncached(array, 0, array.length, encoding, false);
                strings[2] = fromNativePointerUncached(PointerObject.create(array), 0, array.length, encoding, false);
                strings[3] = fromNativePointerUncached(PointerObject.create(array), 0, array.length, encoding, true);
                strings[4] = MutableTruffleString.fromByteArrayUncached(array, 0, array.length, encoding, true);
                strings[5] = MutableTruffleString.fromNativePointerUncached(PointerObject.create(array), 0, array.length, encoding, false);
                strings[6] = strings[2].asMutableTruffleStringUncached(encoding);
                strings[7] = strings[4].asTruffleStringUncached(encoding);
                if (isUTF16(encoding)) {
                    char[] charArray = TStringTestUtil.toCharArrayPunned(array);
                    strings[8] = fromCharArrayUTF16Uncached(charArray, 0, charArray.length);
                } else if (isUTF32(encoding)) {
                    strings[8] = fromIntArrayUTF32Uncached(codepoints, 0, codepoints.length);
                }
                for (AbstractTruffleString s : strings) {
                    for (TruffleStringBuilder sb : new TruffleStringBuilder[]{sbBytes, sbCP, sbCPStrings}) {
                        TruffleString sbs = sb.toStringUncached();
                        Assert.assertTrue(sbs.equalsUncached(s, encoding));
                        Assert.assertEquals(sbs, s);
                        Assert.assertEquals(sbs.hashCode(), s.hashCode());
                        Assert.assertEquals(sbs.hashCodeUncached(encoding), s.hashCodeUncached(encoding));
                        Assert.assertEquals(sbs.getCodeRangeUncached(encoding), s.getCodeRangeUncached(encoding));
                    }
                    TruffleStringBuilder sbCMP = TruffleStringBuilder.create(encoding);
                    for (int i = 0; i < codepoints.length - 1; i++) {
                        sbCMP.appendCodePointUncached(codepoints[i]);
                    }
                    sbCMP.appendCodePointUncached(codepoints[codepoints.length - 1] - 1);
                    TruffleString cmp = sbCMP.toStringUncached();
                    Assert.assertEquals(0, s.compareBytesUncached(s, encoding));
                    Assert.assertTrue(s.compareBytesUncached(cmp, encoding) > 0);
                    Assert.assertTrue(cmp.compareBytesUncached(s, encoding) < 0);
                    s.toJavaStringUncached();
                    TruffleStringIterator it = s.createCodePointIteratorUncached(encoding);
                    Assert.assertEquals(codepoints.length, s.codePointLengthUncached(encoding));
                    Assert.assertTrue(s.isValidUncached(encoding));
                    for (int i = 0; i < array.length; i++) {
                        Assert.assertEquals(Byte.toUnsignedInt(array[i]), s.readByteUncached(i, encoding));
                    }
                    checkInternalByteArrayEquals(array, s.getInternalByteArrayUncached(encoding));
                    byte[] copy = new byte[array.length];
                    s.copyToByteArrayUncached(0, copy, 0, copy.length, encoding);
                    Assert.assertArrayEquals(array, copy);
                    PointerObject pointerObject = PointerObject.create(array.length);
                    s.copyToNativeMemoryUncached(0, pointerObject, 0, array.length, encoding);
                    Assert.assertTrue(pointerObject.contentEquals(array));
                    for (int i = 0; i < codepoints.length; i++) {
                        Assert.assertEquals(codepoints[i], s.codePointAtIndexUncached(i, encoding, TruffleString.ErrorHandling.BEST_EFFORT));
                        Assert.assertEquals(codepoints[i], s.codePointAtByteIndexUncached(byteIndices[i], encoding, TruffleString.ErrorHandling.BEST_EFFORT));
                        Assert.assertEquals(i, s.indexOfCodePointUncached(codepoints[i], 0, codepoints.length, encoding));
                        Assert.assertEquals(byteIndices[i], s.byteIndexOfCodePointUncached(codepoints[i], 0, byteLength, encoding));
                        Assert.assertEquals(i, s.lastIndexOfCodePointUncached(codepoints[i], codepoints.length, 0, encoding));
                        Assert.assertEquals(byteIndices[i], s.lastByteIndexOfCodePointUncached(codepoints[i], byteLength, 0, encoding));
                        TruffleString s1 = fromByteArrayUncached(array, byteIndices[i], (i + 1 < codepoints.length ? byteIndices[i + 1] : array.length) - byteIndices[i], encoding, true);
                        TruffleString s2 = fromByteArrayUncached(array, byteIndices[i], (i + 2 < codepoints.length ? byteIndices[i + 2] : array.length) - byteIndices[i], encoding, false);
                        TruffleString s3 = fromByteArrayUncached(array, byteIndices[i], (i + 3 < codepoints.length ? byteIndices[i + 3] : array.length) - byteIndices[i], encoding, false);
                        s1.toJavaStringUncached();
                        s2.toJavaStringUncached();
                        s3.toJavaStringUncached();
                        for (TruffleString substring : new TruffleString[]{s1, s2, s3}) {
                            Assert.assertEquals(i, s.indexOfStringUncached(substring, 0, codepoints.length, encoding));
                            Assert.assertEquals(byteIndices[i], s.byteIndexOfStringUncached(substring, 0, byteLength, encoding));
                            Assert.assertEquals(i, s.lastIndexOfStringUncached(substring, codepoints.length, 0, encoding));
                            Assert.assertEquals(byteIndices[i], s.lastByteIndexOfStringUncached(substring, byteLength, 0, encoding));
                            Assert.assertTrue(s.regionEqualsUncached(i, substring, 0, substring.codePointLengthUncached(encoding), encoding));
                            Assert.assertTrue(s.regionEqualByteIndexUncached(byteIndices[i], substring, 0, substring.byteLength(encoding), encoding));
                        }
                        Assert.assertTrue(it.hasNext());
                        Assert.assertEquals(codepoints[i], it.nextUncached(encoding));
                    }
                }
            }
        });
    }

    private static void checkInternalByteArrayEquals(byte[] array, InternalByteArray internalByteArray) {
        Assert.assertEquals(array.length, internalByteArray.getLength());
        for (int i = 0; i < array.length; i++) {
            Assert.assertEquals(array[i], internalByteArray.getArray()[internalByteArray.getOffset() + i]);
        }
    }
}
