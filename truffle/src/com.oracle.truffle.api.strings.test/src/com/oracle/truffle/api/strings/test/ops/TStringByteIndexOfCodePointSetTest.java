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

import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_16;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_32;
import static com.oracle.truffle.api.strings.TruffleString.Encoding.UTF_8;
import static org.junit.runners.Parameterized.Parameter;

import java.util.Arrays;

import org.graalvm.shadowed.org.jcodings.specific.UTF16LEEncoding;
import org.graalvm.shadowed.org.jcodings.specific.UTF8Encoding;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.test.Encodings;
import com.oracle.truffle.api.strings.test.TStringTestBase;
import com.oracle.truffle.api.strings.test.TStringTestUtil;

@RunWith(Parameterized.class)
public class TStringByteIndexOfCodePointSetTest extends TStringTestBase {

    @Parameter public TruffleString.ByteIndexOfCodePointSetNode node;

    @Parameters(name = "{0}")
    public static Iterable<TruffleString.ByteIndexOfCodePointSetNode> data() {
        return Arrays.asList(TruffleString.ByteIndexOfCodePointSetNode.create(), TruffleString.ByteIndexOfCodePointSetNode.getUncached());
    }

    @Test
    public void testAll() throws Exception {
        int[] byteIndices1Byte = TStringTestUtil.intRange(0, 512);
        int[] byteIndicesUTF16 = TStringTestUtil.intRange(0, 512, 2);
        int[] byteIndicesUTF32 = TStringTestUtil.intRange(0, 512, 4);
        int[] codepointsBMP = new int[512];
        for (int i = 0; i < codepointsBMP.length; i++) {
            codepointsBMP[i] = i;
        }
        int[][] codepoints = new int[TruffleString.CodeRange.values().length][];
        codepoints[TruffleString.CodeRange.ASCII.ordinal()] = Arrays.copyOf(codepointsBMP, 128);
        codepoints[TruffleString.CodeRange.LATIN_1.ordinal()] = Arrays.copyOf(codepointsBMP, 256);
        codepoints[TruffleString.CodeRange.BMP.ordinal()] = codepointsBMP;
        codepoints[TruffleString.CodeRange.VALID.ordinal()] = Arrays.copyOf(codepointsBMP, codepointsBMP.length);
        codepoints[TruffleString.CodeRange.BROKEN.ordinal()] = Arrays.copyOf(codepointsBMP, codepointsBMP.length);

        codepoints[TruffleString.CodeRange.VALID.ordinal()][256] = 0x10ffff;
        codepoints[TruffleString.CodeRange.BROKEN.ordinal()][256] = 0xdc00;

        TruffleString[] src = new TruffleString[TruffleString.CodeRange.values().length];
        for (TruffleString.CodeRange codeRange : TruffleString.CodeRange.values()) {
            src[codeRange.ordinal()] = TruffleString.fromIntArrayUTF32Uncached(codepoints[codeRange.ordinal()]);
            Assert.assertEquals(codeRange, src[codeRange.ordinal()].getCodeRangeUncached(UTF_32));
        }
        for (TruffleString.Encoding encoding : Encodings.PRIMARY_ENCODINGS) {
            TruffleString[] strings = new TruffleString[src.length];
            for (TruffleString.CodeRange codeRange : TruffleString.CodeRange.values()) {
                strings[codeRange.ordinal()] = src[codeRange.ordinal()].switchEncodingUncached(encoding);
            }
            int[][] byteIndices = new int[src.length][];
            if (encoding == UTF_8) {
                for (int i = 0; i < byteIndices.length; i++) {
                    byteIndices[i] = Encodings.codePointByteIndices(codepoints[i], UTF8Encoding.INSTANCE);
                }
            } else if (encoding == UTF_16) {
                Arrays.fill(byteIndices, byteIndicesUTF16);
                byteIndices[TruffleString.CodeRange.VALID.ordinal()] = Encodings.codePointByteIndices(codepoints[TruffleString.CodeRange.VALID.ordinal()], UTF16LEEncoding.INSTANCE);
                byteIndices[TruffleString.CodeRange.BROKEN.ordinal()] = Encodings.codePointByteIndices(codepoints[TruffleString.CodeRange.BROKEN.ordinal()], UTF16LEEncoding.INSTANCE);
            } else if (encoding == UTF_32) {
                Arrays.fill(byteIndices, byteIndicesUTF32);
            } else {
                Arrays.fill(byteIndices, byteIndices1Byte);
            }
            for (int[] ranges : new int[][]{
                            // 1 value
                            {0, 0},
                            {1, 1},
                            {0x7f, 0x7f},
                            {0xff, 0xff},
                            {500, 500},
                            // 2 values
                            {3, 3, 5, 5},
                            // 1 range
                            {0, 1},
                            {10, 20},
                            {0xfe, 0xff},
                            // 2 ranges
                            {10, 20, 30, 40},
                            {0x00, 0x10, 0x21, 0x32},
                            // table
                            {0, 1, 3, 4, 6, 7, 0x10, 0x11, 0x20, 0x21},
                            {
                                            0x00, 0x00,
                                            0x02, 0x02,
                                            0x04, 0x04,
                                            0x10, 0x10,
                                            0x14, 0x14,
                                            0x23, 0x23,
                                            0x25, 0x25,
                                            0x30, 0x3f,
                                            0x50, 0x5e,
                            },
                            {
                                            0x00, 0x10,
                                            0x21, 0x32,
                                            0x34, 0x4e,
                                            0x52, 0x73,
                                            0x75, 0x88,
                                            0x90, 0x97,
                                            0x99, 0x99,
                                            0xa1, 0xa1,
                                            0xa3, 0xa3,
                                            0xa5, 0xa5,
                                            0xa7, 0xa7,
                            },
                            {
                                            0x00, 0x01,
                                            0x11, 0x11,
                                            0x20, 0x20,
                                            0x34, 0x34,
                                            0x44, 0x45,
                                            0x55, 0x55,
                                            0x66, 0x67,
                                            0x77, 0x77,
                                            0x86, 0x86,
                                            0x9a, 0x9a,
                                            0xaa, 0xab,
                                            0xbb, 0xbb,
                                            0xc0, 0xc1,
                                            0xc4, 0xc4,
                                            0xca, 0xcb,

                            },
                            {
                                            0x30, 0x39,
                                            0x41, 0x5a,
                                            0x5f, 0x5f,
                                            0x61, 0x7a
                            },
                            {
                                            0x00, 0x2f,
                                            0x3a, 0x40,
                                            0x5b, 0x5e,
                                            0x60, 0x60,
                                            0x7b, 0x10ffff},
                            {
                                            0x30, 0x39,
                                            0x41, 0x46,
                                            0x61, 0x66
                            }
            }) {
                if (!isUTF(encoding) && ranges[ranges.length - 1] > 0x7f) {
                    continue;
                }
                TruffleString.CodePointSet codePointSet = TruffleString.CodePointSet.fromRanges(ranges, encoding);
                for (int i = 0; i < strings.length; i++) {
                    int expected = indexOfRanges(codepoints[i], ranges, byteIndices[i]);
                    int actual = node.execute(strings[i], 0, strings[i].byteLength(encoding), codePointSet);
                    checkEqual(expected, actual);
                }
            }
        }
    }

    private static void checkEqual(int expected, int actual) {
        if (expected < 0) {
            Assert.assertTrue(actual < 0);
        } else {
            Assert.assertEquals(expected, actual);
        }
    }

    private static int indexOfRanges(int[] codepoints, int[] ranges, int[] byteIndices) {
        for (int i = 0; i < codepoints.length; i++) {
            for (int j = 0; j < ranges.length; j += 2) {
                if (ranges[j] <= codepoints[i] && codepoints[i] <= ranges[j + 1]) {
                    return byteIndices[i];
                }
            }
        }
        return -1;
    }

    @Test
    public void testNull() throws Exception {
        checkNullSE((s, e) -> node.execute(s, 0, 1, TruffleString.CodePointSet.fromRanges(new int[]{0, 0}, e)));
        expectNullPointerException(() -> node.execute(S_UTF8, 0, 1, null));
    }

    @Test
    public void testOutOfBounds() throws Exception {
        checkOutOfBoundsFromTo(true, 0, Encodings.PRIMARY_ENCODINGS,
                        (a, fromIndex, toIndex, encoding) -> node.execute(a, fromIndex, toIndex, TruffleString.CodePointSet.fromRanges(new int[]{0, 0}, encoding)));
    }
}
