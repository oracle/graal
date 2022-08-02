/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.strings.test.TStringTestUtil.toIntArray;
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
public class TStringLastByteIndexOfStringTest extends TStringTestBase {

    @Parameter public TruffleString.LastByteIndexOfStringNode node;

    @Parameters(name = "{0}")
    public static Iterable<TruffleString.LastByteIndexOfStringNode> data() {
        return Arrays.asList(TruffleString.LastByteIndexOfStringNode.create(), TruffleString.LastByteIndexOfStringNode.getUncached());
    }

    @Test
    public void testAll() throws Exception {
        forAllStrings(true, (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
            testIndexOfString(a, array, isValid, encoding, codepoints, byteIndices, true, true, (b, fromIndex, toIndex) -> node.execute(a, b, fromIndex, toIndex, encoding));
        });
    }

    @Test
    public void testWithMask() throws Exception {
        String javaStrA = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int[] codepointsA = toIntArray(javaStrA);
        TruffleString strA = TruffleString.fromJavaStringUncached(javaStrA, TruffleString.Encoding.UTF_16);
        TruffleString strB = TruffleString.fromJavaStringUncached("abc", TruffleString.Encoding.UTF_16);
        TruffleString.WithMask[] withMask = {
                        TruffleString.WithMask.createUncached(strB.switchEncodingUncached(TruffleString.Encoding.UTF_8), new byte[]{0x20, 0x20, 0x20}, TruffleString.Encoding.UTF_8),
                        TruffleString.WithMask.createUTF16Uncached(strB.switchEncodingUncached(TruffleString.Encoding.UTF_16), new char[]{0x20, 0x20, 0x20}),
                        TruffleString.WithMask.createUTF32Uncached(strB.switchEncodingUncached(TruffleString.Encoding.UTF_32), new int[]{0x20, 0x20, 0x20})
        };
        TruffleString.Encoding[] encodings = {TruffleString.Encoding.UTF_8, TruffleString.Encoding.UTF_16, TruffleString.Encoding.UTF_32};
        for (int i = 0; i < encodings.length; i++) {
            TruffleString.Encoding encoding = encodings[i];
            byte[] arr = new byte[strA.byteLength(encoding)];
            strA.switchEncodingUncached(encoding).copyToByteArrayUncached(0, arr, 0, arr.length, encoding);
            int iFinal = i;
            checkStringVariants(arr, TruffleString.CodeRange.ASCII, true, encoding, codepointsA, null, (a, array, codeRange, isValid, enc, codepoints, byteIndices) -> {
                Assert.assertEquals(0, node.execute(a, withMask[iFinal], array.length, 0, encoding));
            });
        }
    }

    @Test
    public void testNull() throws Exception {
        checkNullSSE((s1, s2, e) -> node.execute(s1, s2, 0, 1, e));
    }

    @Test
    public void testOutOfBounds() throws Exception {
        checkOutOfBoundsFromTo(true, (a, toIndex, fromIndex, encoding) -> node.execute(a, a, fromIndex, toIndex, encoding));
    }
}
