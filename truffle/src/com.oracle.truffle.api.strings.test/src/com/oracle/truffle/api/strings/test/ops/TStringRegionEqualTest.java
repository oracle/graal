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

import com.oracle.truffle.api.strings.AbstractTruffleString;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.test.TStringTestBase;

@RunWith(Parameterized.class)
public class TStringRegionEqualTest extends TStringTestBase {

    @Parameter public TruffleString.RegionEqualNode node;

    @Parameters(name = "{0}")
    public static Iterable<TruffleString.RegionEqualNode> data() {
        return Arrays.asList(TruffleString.RegionEqualNode.create(), TruffleString.RegionEqualNode.getUncached());
    }

    @Test
    public void testAll() throws Exception {
        forAllStrings(true, (a, arrayA, codeRangeA, isValidA, encodingA, codepointsA, byteIndicesA) -> {
            forAllStrings(new TruffleString.Encoding[]{encodingA}, true, (b, arrayB, codeRangeB, isValidB, encodingB, codepointsB, byteIndicesB) -> {
                int length = Math.min(codepointsA.length, codepointsB.length);
                checkRegionEquals(a, codepointsA, 0, b, codepointsB, 0, length, encodingA);
                checkRegionEquals(a, codepointsA, codepointsA.length - length, b, codepointsB, codepointsB.length - length, length, encodingA);
            });
        });
    }

    private void checkRegionEquals(
                    AbstractTruffleString a, int[] codepointsA, int fromIndexA,
                    AbstractTruffleString b, int[] codepointsB, int fromIndexB, int length, TruffleString.Encoding encodingA) {
        boolean expected = regionEquals(codepointsA, fromIndexA, codepointsB, fromIndexB, length);
        Assert.assertEquals(expected, node.execute(a, fromIndexA, b, fromIndexB, length, encodingA));
    }

    private static boolean regionEquals(int[] a, int fromIndexA, int[] b, int fromIndexB, int length) {
        for (int i = 0; i < length; i++) {
            if (a[fromIndexA + i] != b[fromIndexB + i]) {
                return false;
            }
        }
        return true;
    }

    @Test
    public void testNull() throws Exception {
        checkNullSSE((s1, s2, e) -> node.execute(s1, 0, s2, 0, 1, e));
    }

    @Test
    public void testOutOfBounds() throws Exception {
        checkOutOfBoundsRegion(false, (a, fromIndex, length, encoding) -> node.execute(a, fromIndex, a, 0, length, encoding));
        checkOutOfBoundsRegion(false, (a, fromIndex, length, encoding) -> node.execute(a, 0, a, fromIndex, length, encoding));
    }
}
