/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
public class TStringRepeatTest extends TStringTestBase {

    @Parameter public TruffleString.RepeatNode node;

    @Parameters(name = "{0}")
    public static Iterable<TruffleString.RepeatNode> data() {
        return Arrays.asList(TruffleString.RepeatNode.create(), TruffleString.RepeatNode.getUncached());
    }

    @Test
    public void testAll() throws Exception {
        forAllStrings(true, (a, array, codeRange, isValid, encoding, codepoints, byteIndices) -> {
            Assert.assertTrue(node.execute(a, 0, encoding).isEmpty());
            if (a instanceof TruffleString) {
                Assert.assertSame(a, node.execute(a, 1, encoding));
            }
            int n = 3;
            TruffleString b = node.execute(a, n, encoding);
            if (isValid) {
                Assert.assertEquals(array.length * n, b.byteLength(encoding));
                Assert.assertEquals(codepoints.length * n, b.codePointLengthUncached(encoding));
                Assert.assertEquals(codeRange, b.getCodeRangeUncached(encoding));
            }
            byte[] cmp = new byte[array.length * 3];
            b.copyToByteArrayUncached(0, cmp, 0, cmp.length, encoding);
            for (int i = 0; i < n; i++) {
                int offset = array.length * i;
                for (int j = 0; j < array.length; j++) {
                    Assert.assertEquals(array[j], cmp[offset + j]);
                }
            }
        });
    }

    @Test
    public void testNull() throws Exception {
        checkNullSE((s, e) -> node.execute(s, 1, e));
    }

    @Test
    public void testNegativeN() throws Exception {
        for (int n : new int[]{Integer.MIN_VALUE, -1}) {
            expectIllegalArgumentException(() -> node.execute(S_UTF8, n, TruffleString.Encoding.UTF_8));
        }
    }

    @Test(expected = OutOfMemoryError.class)
    public void testLargeN() {
        node.execute(TruffleString.fromCodePointUncached(0xffff, TruffleString.Encoding.UTF_16), Integer.MAX_VALUE, TruffleString.Encoding.UTF_16);
    }
}
