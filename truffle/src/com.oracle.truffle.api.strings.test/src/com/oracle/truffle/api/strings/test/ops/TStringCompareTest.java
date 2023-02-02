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
public class TStringCompareTest extends TStringTestBase {

    @Parameter(0) public TruffleString.CompareBytesNode node;
    @Parameter(1) public TruffleString.CompareCharsUTF16Node nodeUTF16;
    @Parameter(2) public TruffleString.CompareIntsUTF32Node nodeUTF32;

    @Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(
                        new Object[]{TruffleString.CompareBytesNode.create(), TruffleString.CompareCharsUTF16Node.create(), TruffleString.CompareIntsUTF32Node.create()},
                        new Object[]{TruffleString.CompareBytesNode.getUncached(), TruffleString.CompareCharsUTF16Node.getUncached(), TruffleString.CompareIntsUTF32Node.getUncached()});
    }

    @Test
    public void testAll() throws Exception {
        forAllStrings(true, (a, arrayA, codeRangeA, isValidA, encodingA, codepointsA, byteIndicesA) -> {
            forAllStrings(new TruffleString.Encoding[]{encodingA}, true, (b, arrayB, codeRangeB, isValidB, encodingB, codepointsB, byteIndicesB) -> {
                checkResult(compare(arrayA, arrayB), node.execute(a, b, encodingA));
                if (encodingA == TruffleString.Encoding.UTF_16) {
                    checkResult(compare(codepointsA, codepointsB), nodeUTF16.execute(a, b));
                } else if (encodingA == TruffleString.Encoding.UTF_32) {
                    checkResult(compare(codepointsA, codepointsB), nodeUTF32.execute(a, b));
                }
            });
        });
    }

    private static void checkResult(int expected, int actual) {
        if (expected < 0) {
            Assert.assertTrue(actual < 0);
        } else if (expected == 0) {
            Assert.assertEquals(0, actual);
        } else {
            Assert.assertTrue(actual > 0);
        }
    }

    private static int compare(byte[] a, byte[] b) {
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int cmp = Byte.toUnsignedInt(a[i]) - Byte.toUnsignedInt(b[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return a.length - b.length;
    }

    private static int compare(int[] a, int[] b) {
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            int cmp = a[i] - b[i];
            if (cmp != 0) {
                return cmp;
            }
        }
        return a.length - b.length;
    }

    @Test
    public void testNull() throws Exception {
        checkNullSSE((s1, s2, e) -> node.execute(s1, s2, e));
        checkNullSS((s1, s2) -> nodeUTF16.execute(s1, s2));
        checkNullSS((s1, s2) -> nodeUTF32.execute(s1, s2));
    }
}
