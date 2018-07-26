/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.regex.chardata.CodePointRange;
import com.oracle.truffle.regex.chardata.CodePointSet;
import com.oracle.truffle.regex.chardata.Constants;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class CodePointSetTest {

    public CodePointSet single(int i) {
        return range(i, i);
    }

    public CodePointSet range(int i, int j) {
        return CodePointSet.create(new CodePointRange(i, j));
    }

    public CodePointSet range(int[] i) {
        Assert.assertEquals(i.length, 2);
        return range(i[0], i[1]);
    }

    public CodePointSet multi(int... values) {
        assert (values.length & 1) == 0;
        List<CodePointRange> ts = new ArrayList<>();
        for (int i = 0; i < values.length; i += 2) {
            ts.add(new CodePointRange(values[i], values[i + 1]));
        }
        return CodePointSet.create(ts.toArray(new CodePointRange[ts.size()]));
    }

    public void checkMatch(String errorMsg, CodePointSet m, int... values) {
        List<CodePointRange> ranges = m.getRanges();
        int[] chk = new int[ranges.size() * 2];
        int i = 0;
        for (CodePointRange r : ranges) {
            chk[i++] = r.lo;
            chk[i++] = r.hi;
        }
        Assert.assertArrayEquals(matchError(errorMsg, m, values), values, chk);
    }

    private static String matchError(String errorMsg, CodePointSet m, int[] values) {
        StringBuilder sb = new StringBuilder(errorMsg).append(": got ").append(m.toString()).append(", expected [ ");
        for (int i = 0; i < values.length; i += 2) {
            sb.append("[").append(values[i]).append("-").append(values[i + 1]).append("] ");
        }
        return sb.append("]").toString();
    }

    public void checkAddRange(CodePointSet a, CodePointRange r, int... values) {
        CodePointSet orig = a.copy();
        a.addRange(r);
        checkMatch("addRange(" + orig + ", " + r + ")", a, values);
    }

    public void checkInverse(CodePointSet a, int... values) {
        checkMatch("inverse(" + a + ")", a.createInverse(), values);
    }

    public void checkIntersection(CodePointSet a, CodePointSet b, int... values) {
        checkMatch("intersection(" + a + "," + b + ")", a.createIntersection(b), values);
    }

    @Test
    public void testAddRangeSingle() {
        int[][] inA = new int[][]{
                        new int[]{1, Constants.MAX_CODEPOINT},
                        new int[]{0, Constants.MAX_CODEPOINT},
                        new int[]{0, 1},
                        new int[]{0, 0, 2, Constants.MAX_CODEPOINT},
                        new int[]{0, 254, 256, Constants.MAX_CODEPOINT},
                        new int[]{0, Constants.MAX_CODEPOINT - 2, Constants.MAX_CODEPOINT, Constants.MAX_CODEPOINT},
                        new int[]{142, Constants.MAX_CODEPOINT},
                        new int[]{142, 150, 190, 200, 300, 340}
        };
        int[] inB = new int[]{
                        0,
                        0,
                        0,
                        255,
                        255,
                        Constants.MAX_CODEPOINT - 1,
                        Constants.MAX_CODEPOINT,
                        255,
        };
        int[][] out = new int[][]{
                        new int[]{0, Constants.MAX_CODEPOINT},
                        new int[]{0, Constants.MAX_CODEPOINT},
                        new int[]{0, 1},
                        new int[]{0, 0, 2, Constants.MAX_CODEPOINT},
                        new int[]{0, Constants.MAX_CODEPOINT},
                        new int[]{0, Constants.MAX_CODEPOINT},
                        new int[]{142, Constants.MAX_CODEPOINT},
                        new int[]{142, 150, 190, 200, 255, 255, 300, 340}
        };
        for (int i = 0; i < inA.length; i++) {
            checkAddRange(multi(inA[i]), new CodePointRange(inB[i]), out[i]);
        }
    }

    @Test
    public void testAddRangeSingleRange() {
        int[][] inA = new int[][]{
                        new int[]{1, Constants.MAX_CODEPOINT},
                        new int[]{0, Constants.MAX_CODEPOINT},
                        new int[]{0, 199},
                        new int[]{401, Constants.MAX_CODEPOINT},
                        new int[]{0, 199, 401, Constants.MAX_CODEPOINT},
                        new int[]{0, 200},
                        new int[]{400, Constants.MAX_CODEPOINT},
                        new int[]{0, 300},
                        new int[]{200, 300},
                        new int[]{300, 500},
                        new int[]{300, 400},
                        new int[]{0, 254, 256, Constants.MAX_CODEPOINT},
                        new int[]{100, 250, 300, 310, 350, 500},
                        new int[]{0, 98, 100, 250, 300, 310, 350, 500, 502, 2000},
                        new int[]{0, Constants.MAX_CODEPOINT - 2, Constants.MAX_CODEPOINT, Constants.MAX_CODEPOINT},
                        new int[]{142, Constants.MAX_CODEPOINT - 1}
        };
        int[][] inB = new int[][]{
                        new int[]{0, 10},
                        new int[]{1, 10},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{1000, Constants.MAX_CODEPOINT - 1},
                        new int[]{1000, Constants.MAX_CODEPOINT}
        };
        int[][] out = new int[][]{
                        new int[]{0, Constants.MAX_CODEPOINT},
                        new int[]{0, Constants.MAX_CODEPOINT},
                        new int[]{0, 400},
                        new int[]{200, Constants.MAX_CODEPOINT},
                        new int[]{0, Constants.MAX_CODEPOINT},
                        new int[]{0, 400},
                        new int[]{200, Constants.MAX_CODEPOINT},
                        new int[]{0, 400},
                        new int[]{200, 400},
                        new int[]{200, 500},
                        new int[]{200, 400},
                        new int[]{0, Constants.MAX_CODEPOINT},
                        new int[]{100, 500},
                        new int[]{0, 98, 100, 500, 502, 2000},
                        new int[]{0, Constants.MAX_CODEPOINT},
                        new int[]{142, Constants.MAX_CODEPOINT},
        };
        for (int i = 0; i < inA.length; i++) {
            checkAddRange(multi(inA[i]), new CodePointRange(inB[i][0], inB[i][1]), out[i]);
        }
    }

    @Test
    public void testInverseSingle() {
        int[] in = new int[]{0, 1, 255, Constants.MAX_CODEPOINT - 1, Constants.MAX_CODEPOINT};
        int[][] out = new int[][]{
                        new int[]{1, Constants.MAX_CODEPOINT},
                        new int[]{0, 0, 2, Constants.MAX_CODEPOINT},
                        new int[]{0, 254, 256, Constants.MAX_CODEPOINT},
                        new int[]{0, Constants.MAX_CODEPOINT - 2, Constants.MAX_CODEPOINT, Constants.MAX_CODEPOINT},
                        new int[]{0, Constants.MAX_CODEPOINT - 1},
        };
        for (int i = 0; i < in.length; i++) {
            checkInverse(single(in[i]), out[i]);
        }
    }

    @Test
    public void testIntersectionSingle() {
        int[] inA = new int[]{
                        0,
                        0,
                        0,
                        255,
                        255,
                        Constants.MAX_CODEPOINT - 1,
                        Constants.MAX_CODEPOINT
        };
        int[][] inB = new int[][]{
                        new int[]{1, Constants.MAX_CODEPOINT},
                        new int[]{0, Constants.MAX_CODEPOINT},
                        new int[]{0, 1},
                        new int[]{0, 0, 2, Constants.MAX_CODEPOINT},
                        new int[]{0, 254, 256, Constants.MAX_CODEPOINT},
                        new int[]{0, Constants.MAX_CODEPOINT - 2, Constants.MAX_CODEPOINT, Constants.MAX_CODEPOINT},
                        new int[]{142, Constants.MAX_CODEPOINT}
        };
        int[][] out = new int[][]{
                        new int[]{},
                        new int[]{0, 0},
                        new int[]{0, 0},
                        new int[]{255, 255},
                        new int[]{},
                        new int[]{},
                        new int[]{Constants.MAX_CODEPOINT, Constants.MAX_CODEPOINT},
        };
        for (int i = 0; i < inA.length; i++) {
            checkIntersection(single(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testInverseSingleRange() {
        int[][] in = new int[][]{
                        new int[]{0, 10},
                        new int[]{1, 10},
                        new int[]{200, 400},
                        new int[]{1000, Constants.MAX_CODEPOINT - 1},
                        new int[]{1000, Constants.MAX_CODEPOINT}
        };
        int[][] out = new int[][]{
                        new int[]{11, Constants.MAX_CODEPOINT},
                        new int[]{0, 0, 11, Constants.MAX_CODEPOINT},
                        new int[]{0, 199, 401, Constants.MAX_CODEPOINT},
                        new int[]{0, 999, Constants.MAX_CODEPOINT, Constants.MAX_CODEPOINT},
                        new int[]{0, 999},
        };
        for (int i = 0; i < in.length; i++) {
            checkInverse(range(in[i]), out[i]);
        }
    }

    @Test
    public void testIntersectionSingleRange() {
        int[][] inA = new int[][]{
                        new int[]{0, 10},
                        new int[]{1, 10},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{200, 400},
                        new int[]{1000, Constants.MAX_CODEPOINT - 1},
                        new int[]{1000, Constants.MAX_CODEPOINT}
        };
        int[][] inB = new int[][]{
                        new int[]{1, Constants.MAX_CODEPOINT},
                        new int[]{0, Constants.MAX_CODEPOINT},
                        new int[]{0, 199},
                        new int[]{401, Constants.MAX_CODEPOINT},
                        new int[]{0, 199, 401, Constants.MAX_CODEPOINT},
                        new int[]{0, 200},
                        new int[]{200, 200},
                        new int[]{400, Constants.MAX_CODEPOINT},
                        new int[]{0, 300},
                        new int[]{200, 300},
                        new int[]{300, 500},
                        new int[]{300, 400},
                        new int[]{0, 254, 256, Constants.MAX_CODEPOINT},
                        new int[]{100, 250, 300, 310, 350, 500},
                        new int[]{0, Constants.MAX_CODEPOINT - 2, Constants.MAX_CODEPOINT, Constants.MAX_CODEPOINT},
                        new int[]{142, Constants.MAX_CODEPOINT - 1}
        };
        int[][] out = new int[][]{
                        new int[]{1, 10},
                        new int[]{1, 10},
                        new int[]{},
                        new int[]{},
                        new int[]{},
                        new int[]{200, 200},
                        new int[]{200, 200},
                        new int[]{400, 400},
                        new int[]{200, 300},
                        new int[]{200, 300},
                        new int[]{300, 400},
                        new int[]{300, 400},
                        new int[]{200, 254, 256, 400},
                        new int[]{200, 250, 300, 310, 350, 400},
                        new int[]{1000, Constants.MAX_CODEPOINT - 2},
                        new int[]{1000, Constants.MAX_CODEPOINT - 1},
        };
        for (int i = 0; i < inA.length; i++) {
            checkIntersection(range(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testInverseMultiRange() {
        int[][] in = new int[][]{
                        new int[]{0, 10, 1000, 2000},
                        new int[]{1, 10, 1000, 2000},
                        new int[]{200, 400, 500, 600},
                        new int[]{200, 400, 1000, Constants.MAX_CODEPOINT - 1},
                        new int[]{0, 10, 1000, Constants.MAX_CODEPOINT}
        };
        int[][] out = new int[][]{
                        new int[]{11, 999, 2001, Constants.MAX_CODEPOINT},
                        new int[]{0, 0, 11, 999, 2001, Constants.MAX_CODEPOINT},
                        new int[]{0, 199, 401, 499, 601, Constants.MAX_CODEPOINT},
                        new int[]{0, 199, 401, 999, Constants.MAX_CODEPOINT, Constants.MAX_CODEPOINT},
                        new int[]{11, 999},
        };
        for (int i = 0; i < in.length; i++) {
            checkInverse(multi(in[i]), out[i]);
        }
    }

    @Test
    public void testIntersectionMultiRange() {
        int[][] inA = new int[][]{
                        new int[]{0, 10, 200, 400},
                        new int[]{1, 10, 200, 400},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800},
                        new int[]{200, 400, 600, 800}
        };
        int[][] inB = new int[][]{
                        new int[]{1, Constants.MAX_CODEPOINT},
                        new int[]{0, Constants.MAX_CODEPOINT},
                        new int[]{0, 199},
                        new int[]{401, 599},
                        new int[]{801, Constants.MAX_CODEPOINT},
                        new int[]{0, 199, 401, 599, 801, Constants.MAX_CODEPOINT},
                        new int[]{0, 200},
                        new int[]{800, Constants.MAX_CODEPOINT},
                        new int[]{0, 300},
                        new int[]{200, 300},
                        new int[]{300, 500},
                        new int[]{300, 400},
                        new int[]{300, 700},
                        new int[]{300, 450, 500, 700},
                        new int[]{100, 250, 300, 450, 500, 700, 750, 900},
                        new int[]{0, 254, 256, Constants.MAX_CODEPOINT},
                        new int[]{100, 250, 300, 310, 350, 500},
                        new int[]{300, 300, 500, 500, 700, 700}
        };
        int[][] out = new int[][]{
                        new int[]{1, 10, 200, 400},
                        new int[]{1, 10, 200, 400},
                        new int[]{},
                        new int[]{},
                        new int[]{},
                        new int[]{},
                        new int[]{200, 200},
                        new int[]{800, 800},
                        new int[]{200, 300},
                        new int[]{200, 300},
                        new int[]{300, 400},
                        new int[]{300, 400},
                        new int[]{300, 400, 600, 700},
                        new int[]{300, 400, 600, 700},
                        new int[]{200, 250, 300, 400, 600, 700, 750, 800},
                        new int[]{200, 254, 256, 400, 600, 800},
                        new int[]{200, 250, 300, 310, 350, 400},
                        new int[]{300, 300, 700, 700}
        };
        for (int i = 0; i < inA.length; i++) {
            checkIntersection(multi(inA[i]), multi(inB[i]), out[i]);
        }
    }
}
