/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.regex.tregex.matchers;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.regex.charset.RangesBuffer;
import com.oracle.truffle.regex.tregex.buffer.CharRangesBuffer;
import com.oracle.truffle.regex.tregex.buffer.IntRangesBuffer;

public class RangesBufferTest {

    private static void appendAll(RangesBuffer buf, int[] content) {
        for (int i = 0; i < content.length; i += 2) {
            buf.appendRange(content[i], content[i + 1]);
        }
    }

    private static CharRangesBuffer createCharRangesBuffer(int[] content) {
        CharRangesBuffer buf = new CharRangesBuffer();
        appendAll(buf, content);
        return buf;
    }

    private static IntRangesBuffer createIntRangesBuffer(int[] content) {
        IntRangesBuffer buf = new IntRangesBuffer();
        appendAll(buf, content);
        return buf;
    }

    private static boolean equals(RangesBuffer buf, int[] content) {
        if (buf instanceof IntRangesBuffer) {
            return equals((IntRangesBuffer) buf, content);
        }
        return equals((CharRangesBuffer) buf, content);
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

    private static boolean equals(CharRangesBuffer buf, int[] content) {
        if (buf.size() != content.length / 2) {
            return false;
        }
        for (int i = 0; i < content.length / 2; i++) {
            if (buf.getLo(i) != (char) content[i * 2] || buf.getHi(i) != (char) content[i * 2 + 1]) {
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
        Assert.assertTrue(buf.rangesAreSortedAndDisjoint());
        buf.addRange(lo, hi);
        Assert.assertTrue(matchError("addRange(" + buf + ", " + lo + ", " + hi + ")", buf, expected), equals(buf, expected));
        Assert.assertTrue(buf.rangesAreSortedAndDisjoint());
    }

    private static void checkAddRange(int[] buf, int lo, int hi, int[] expected) {
        checkAddRange(createIntRangesBuffer(buf), lo, hi, expected);
        checkAddRange(createCharRangesBuffer(buf), lo, hi, expected);
    }

    private static void checkAddRange(int[] buf, int lo, int hi, int[] expected, boolean intBuffer) {
        if (intBuffer) {
            checkAddRange(createIntRangesBuffer(buf), lo, hi, expected);
        } else {
            checkAddRange(createCharRangesBuffer(buf), lo, hi, expected);
        }
    }

    @Test
    public void testAddRange() {
        checkAddRange(new int[]{}, 0, 1, new int[]{0, 1});
        for (boolean intBuffer : new boolean[]{true, false}) {
            int max = intBuffer ? Character.MAX_CODE_POINT : Character.MAX_VALUE;
            checkAddRange(new int[]{}, 0, max, new int[]{0, max}, intBuffer);
            checkAddRange(new int[]{0, 0}, 0, max, new int[]{0, max}, intBuffer);
            checkAddRange(new int[]{1, 1}, 0, max, new int[]{0, max}, intBuffer);
            checkAddRange(new int[]{0, 2}, 0, max, new int[]{0, max}, intBuffer);
            checkAddRange(new int[]{max - 1, max}, 0, max, new int[]{0, max}, intBuffer);
            checkAddRange(new int[]{max - 1, max - 1}, 0, max, new int[]{0, max}, intBuffer);
            checkAddRange(new int[]{max, max}, 0, max, new int[]{0, max}, intBuffer);
            checkAddRange(new int[]{2, 2, 4, 4, 6, 7}, 0, max, new int[]{0, max}, intBuffer);
            checkAddRange(new int[]{2, 2, 4, 4, 6, 7}, 0, max, new int[]{0, max}, intBuffer);
            checkAddRange(new int[]{2, 2, 4, 4, 6, 7}, max, max, new int[]{2, 2, 4, 4, 6, 7, max, max}, intBuffer);
            checkAddRange(new int[]{2, 2, 4, 4, 6, 7, max, max}, max - 1, max - 1, new int[]{2, 2, 4, 4, 6, 7, max - 1, max}, intBuffer);
        }
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
