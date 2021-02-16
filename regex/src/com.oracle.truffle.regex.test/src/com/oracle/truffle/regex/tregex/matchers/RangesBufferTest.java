/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.matchers;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.regex.charset.RangesBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntRangesBuffer;

public class RangesBufferTest {

    private static void appendAll(RangesBuffer buf, int[] content) {
        for (int i = 0; i < content.length; i += 2) {
            buf.appendRange(content[i], content[i + 1]);
        }
    }

    private static IntRangesBuffer createIntRangesBuffer(int[] content) {
        IntRangesBuffer buf = new IntRangesBuffer();
        appendAll(buf, content);
        return buf;
    }

    private static boolean equals(RangesBuffer buf, int[] content) {
        return equals((IntRangesBuffer) buf, content);
    }

    private static boolean equals(IntRangesBuffer buf, int[] content) {
        if (buf.size() != content.length / 2) {
            return false;
        }
        for (int i = 0; i < content.length / 2; i++) {
            if (buf.getLo(i) != content[i * 2] || buf.getHi(i) != content[i * 2 + 1]) {
                return false;
            }
        }
        return true;
    }

    private static String matchError(String errorMsg, RangesBuffer m, int[] expected) {
        StringBuilder sb = new StringBuilder(errorMsg).append(": got ").append(m.toString()).append(", expected [ ");
        for (int i = 0; i < expected.length; i += 2) {
            sb.append("[").append(expected[i]).append("-").append(expected[i + 1]).append("] ");
        }
        return sb.append("]").toString();
    }

    private static void checkAddRange(RangesBuffer buf, int lo, int hi, int[] expected) {
        Assert.assertTrue(buf.rangesAreSortedNonAdjacentAndDisjoint());
        buf.addRange(lo, hi);
        Assert.assertTrue(matchError("addRange(" + buf + ", " + lo + ", " + hi + ")", buf, expected), equals(buf, expected));
        Assert.assertTrue(buf.rangesAreSortedNonAdjacentAndDisjoint());
    }

    private static void checkAddRange(int[] buf, int lo, int hi, int[] expected) {
        checkAddRange(createIntRangesBuffer(buf), lo, hi, expected);
    }

    @Test
    public void testAddRange() {
        int max = Character.MAX_VALUE;
        checkAddRange(new int[]{}, 0, 1, new int[]{0, 1});
        checkAddRange(new int[]{}, 0, max, new int[]{0, max});
        checkAddRange(new int[]{0, 0}, 0, max, new int[]{0, max});
        checkAddRange(new int[]{1, 1}, 0, max, new int[]{0, max});
        checkAddRange(new int[]{0, 2}, 0, max, new int[]{0, max});
        checkAddRange(new int[]{max - 1, max}, 0, max, new int[]{0, max});
        checkAddRange(new int[]{max - 1, max - 1}, 0, max, new int[]{0, max});
        checkAddRange(new int[]{max, max}, 0, max, new int[]{0, max});
        checkAddRange(new int[]{2, 2, 4, 4, 6, 7}, 0, max, new int[]{0, max});
        checkAddRange(new int[]{2, 2, 4, 4, 6, 7}, 0, max, new int[]{0, max});
        checkAddRange(new int[]{2, 2, 4, 4, 6, 7}, max, max, new int[]{2, 2, 4, 4, 6, 7, max, max});
        checkAddRange(new int[]{2, 2, 4, 4, 6, 7, max, max}, max - 1, max - 1, new int[]{2, 2, 4, 4, 6, 7, max - 1, max});
        checkAddRange(new int[]{2, 2, 4, 4, 6, 7}, 2, 2, new int[]{2, 2, 4, 4, 6, 7});
        checkAddRange(new int[]{2, 2, 4, 4, 6, 7}, 9, 9, new int[]{2, 2, 4, 4, 6, 7, 9, 9});
        checkAddRange(new int[]{2, 2, 4, 4, 6, 7}, 8, 8, new int[]{2, 2, 4, 4, 6, 8});
        checkAddRange(new int[]{2, 2, 4, 4, 6, 7}, 3, 3, new int[]{2, 4, 6, 7});
        checkAddRange(new int[]{2, 2, 4, 4, 6, 7}, 3, 5, new int[]{2, 7});
        checkAddRange(new int[]{2, 2, 4, 4, 6, 7}, 3, 9, new int[]{2, 9});
        checkAddRange(new int[]{2, 2, 6, 7}, 4, 4, new int[]{2, 2, 4, 4, 6, 7});
        checkAddRange(new int[]{2, 2, 6, 7}, 3, 6, new int[]{2, 7});
        checkAddRange(new int[]{2, 2, 6, 7}, 2, 6, new int[]{2, 7});
        checkAddRange(new int[]{2, 2, 6, 7}, 1, 6, new int[]{1, 7});
        checkAddRange(new int[]{2, 2, 6, 7}, 1, 9, new int[]{1, 9});
        checkAddRange(new int[]{2, 2, 6, 7}, 3, 9, new int[]{2, 9});
        checkAddRange(new int[]{2, 2, 6, 7}, 0, 0, new int[]{0, 0, 2, 2, 6, 7});
        checkAddRange(new int[]{2, 2, 4, 4, 7, 7}, 6, 6, new int[]{2, 2, 4, 4, 6, 7});
    }
}
