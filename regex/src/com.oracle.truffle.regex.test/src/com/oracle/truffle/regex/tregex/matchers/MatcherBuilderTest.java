/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.charset.Range;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.string.Encodings;

public class MatcherBuilderTest {

    private static final int MAX_VALUE = Character.MAX_CODE_POINT;

    private final CodePointSetAccumulator acc = new CodePointSetAccumulator();

    private CodePointSet single(int i) {
        return range(i, i);
    }

    private CodePointSet range(int i, int j) {
        return multi(i, j);
    }

    private CodePointSet range(int[] i) {
        Assert.assertEquals(i.length, 2);
        return multi(i);
    }

    private CodePointSet multi(int... values) {
        assert (values.length & 1) == 0;
        acc.clear();
        for (int i = 0; i < values.length; i += 2) {
            acc.addRange(values[i], values[i + 1]);
        }
        return acc.toCodePointSet();
    }

    private static String matchError(String errorMsg, CodePointSet m, CodePointSet expected) {
        return String.format("%s: got %s, expected %s", errorMsg, m, expected);
    }

    private static String matchError(String errorMsg, CodePointSet m, int[] values) {
        StringBuilder sb = new StringBuilder(errorMsg).append(": got ").append(m.toString()).append(", expected [ ");
        for (int i = 0; i < values.length; i += 2) {
            sb.append("[").append(values[i]).append("-").append(values[i + 1]).append("] ");
        }
        return sb.append("]").toString();
    }

    private static void checkMatch(String errorMsg, CodePointSet m, CodePointSet expected) {
        Assert.assertEquals(matchError(errorMsg, m, expected), expected, m);
    }

    private static void checkMatch(String errorMsg, CodePointSet m, int... values) {
        int i = 0;
        for (Range r : m) {
            if (r.lo != values[i] || r.hi != values[i + 1]) {
                Assert.fail(matchError(errorMsg, m, values));
            }
            i += 2;
        }
    }

    private static void checkContains(CodePointSet a, CodePointSet b, boolean expected) {
        boolean test = a.contains(b);
        Assert.assertEquals(a + ".contains(" + b + "): got " + test + ", expected " + expected, test, expected);
    }

    private static void checkInverse(CodePointSet a, int... values) {
        checkMatch("inverse(" + a + ")", a.createInverse(Encodings.UTF_16), values);
    }

    private static void checkIntersection(CodePointSet a, CodePointSet b, int... values) {
        CompilationBuffer compilationBuffer = new CompilationBuffer(Encodings.UTF_16);
        CodePointSet intersection = a.createIntersection(b, compilationBuffer);
        checkMatch("intersection(" + a + "," + b + ")", intersection, values);
        assertTrue("intersection(" + a + "," + b + ")", a.intersects(b) == intersection.matchesSomething());
        CodePointSet.IntersectAndSubtractResult<CodePointSet> result = a.intersectAndSubtract(b, compilationBuffer);
        checkMatch("intersectAndSubtract(" + a + "," + b + ")[0]", result.subtractedA, a.subtract(intersection, compilationBuffer));
        checkMatch("intersectAndSubtract(" + a + "," + b + ")[1]", result.subtractedB, b.subtract(intersection, compilationBuffer));
        checkMatch("intersectAndSubtract(" + a + "," + b + ")[2]", result.intersection, intersection);
    }

    private static void checkSubtraction(CodePointSet a, CodePointSet b, int... values) {
        checkMatch("subtraction(" + a + "," + b + ")", a.subtract(b, new CompilationBuffer(Encodings.UTF_16)), values);
    }

    private static void checkUnion(CodePointSet a, CodePointSet b, int... values) {
        checkMatch("union(" + a + "," + b + ")", a.union(b, new CompilationBuffer(Encodings.UTF_16)), values);
    }

    @Test
    public void testInverseSingle() {
        int[] in = {0, 1, 255, MAX_VALUE - 1, MAX_VALUE};
        int[][] out = {
                        {1, MAX_VALUE},
                        {0, 0, 2, MAX_VALUE},
                        {0, 254, 256, MAX_VALUE},
                        {0, MAX_VALUE - 2, MAX_VALUE, MAX_VALUE},
                        {0, MAX_VALUE - 1},
        };
        for (int i = 0; i < in.length; i++) {
            checkInverse(single(in[i]), out[i]);
        }
    }

    @Test
    public void testContainsSingle() {
        int[] inA = {
                        0,
                        0,
                        0,
                        0,
                        255,
                        255,
                        MAX_VALUE - 1,
                        MAX_VALUE
        };
        int[][] inB = {
                        {1, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 1},
                        {0, 0},
                        {0, 0, 2, MAX_VALUE},
                        {0, 254, 256, MAX_VALUE},
                        {0, MAX_VALUE - 2, MAX_VALUE, MAX_VALUE},
                        {142, MAX_VALUE}
        };
        boolean[] out = new boolean[]{
                        false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        false,
                        false,
        };
        for (int i = 0; i < inA.length; i++) {
            checkContains(single(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testIntersectionSingle() {
        int[] inA = {
                        0,
                        0,
                        0,
                        255,
                        255,
                        MAX_VALUE - 1,
                        MAX_VALUE
        };
        int[][] inB = {
                        {1, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 1},
                        {0, 0, 2, MAX_VALUE},
                        {0, 254, 256, MAX_VALUE},
                        {0, MAX_VALUE - 2, MAX_VALUE, MAX_VALUE},
                        {142, MAX_VALUE}
        };
        int[][] out = {
                        {},
                        {0, 0},
                        {0, 0},
                        {255, 255},
                        {},
                        {},
                        {MAX_VALUE, MAX_VALUE},
        };
        for (int i = 0; i < inA.length; i++) {
            checkIntersection(single(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testSubtractSingle() {
        int[] inA = {
                        0,
                        0,
                        0,
                        255,
                        255,
                        MAX_VALUE - 1,
                        MAX_VALUE
        };
        int[][] inB = {
                        {1, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 1},
                        {0, 0, 2, MAX_VALUE},
                        {0, 254, 256, MAX_VALUE},
                        {0, MAX_VALUE - 2, MAX_VALUE, MAX_VALUE},
                        {142, MAX_VALUE}
        };
        int[][] out = {
                        {0, 0},
                        {},
                        {},
                        {},
                        {255, 255},
                        {MAX_VALUE - 1, MAX_VALUE - 1},
                        {},
        };
        for (int i = 0; i < inA.length; i++) {
            checkSubtraction(single(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testUnionSingle() {
        int[] inA = {
                        0,
                        0,
                        0,
                        255,
                        255,
                        MAX_VALUE - 1,
                        MAX_VALUE,
                        255,
        };
        int[][] inB = {
                        {1, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 1},
                        {0, 0, 2, MAX_VALUE},
                        {0, 254, 256, MAX_VALUE},
                        {0, MAX_VALUE - 2, MAX_VALUE, MAX_VALUE},
                        {142, MAX_VALUE},
                        {142, 150, 190, 200, 300, 340}
        };
        int[][] out = {
                        {0, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 1},
                        {0, 0, 2, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, MAX_VALUE},
                        {142, MAX_VALUE},
                        {142, 150, 190, 200, 255, 255, 300, 340}
        };
        for (int i = 0; i < inA.length; i++) {
            checkUnion(single(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testInverseSingleRange() {
        int[][] in = {
                        {0, 10},
                        {1, 10},
                        {200, 400},
                        {1000, MAX_VALUE - 1},
                        {1000, MAX_VALUE}
        };
        int[][] out = {
                        {11, MAX_VALUE},
                        {0, 0, 11, MAX_VALUE},
                        {0, 199, 401, MAX_VALUE},
                        {0, 999, MAX_VALUE, MAX_VALUE},
                        {0, 999},
        };
        for (int i = 0; i < in.length; i++) {
            checkInverse(range(in[i]), out[i]);
        }
    }

    @Test
    public void testContainsSingleRange() {
        int[][] inA = {
                        {0, 10},
                        {1, 10},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {1000, MAX_VALUE - 1},
                        {1000, MAX_VALUE}
        };
        int[][] inB = {
                        {1, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 199},
                        {401, MAX_VALUE},
                        {0, 199, 401, MAX_VALUE},
                        {0, 200},
                        {200, 200},
                        {400, MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {0, 254, 256, MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {0, MAX_VALUE - 2, MAX_VALUE, MAX_VALUE},
                        {142, MAX_VALUE - 1}
        };
        boolean[] out = new boolean[]{
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        false,
                        true,
                        false,
                        true,
                        false,
                        false,
                        false,
                        false
        };
        for (int i = 0; i < inA.length; i++) {
            checkContains(range(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testIntersectionSingleRange() {
        int[][] inA = {
                        {0, 10},
                        {1, 10},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {1000, MAX_VALUE - 1},
                        {1000, MAX_VALUE}
        };
        int[][] inB = {
                        {1, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 199},
                        {401, MAX_VALUE},
                        {0, 199, 401, MAX_VALUE},
                        {0, 200},
                        {200, 200},
                        {400, MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {0, 254, 256, MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {0, MAX_VALUE - 2, MAX_VALUE, MAX_VALUE},
                        {142, MAX_VALUE - 1}
        };
        int[][] out = {
                        {1, 10},
                        {1, 10},
                        {},
                        {},
                        {},
                        {200, 200},
                        {200, 200},
                        {400, 400},
                        {200, 300},
                        {200, 300},
                        {300, 400},
                        {300, 400},
                        {200, 254, 256, 400},
                        {200, 250, 300, 310, 350, 400},
                        {1000, MAX_VALUE - 2},
                        {1000, MAX_VALUE - 1},
        };
        for (int i = 0; i < inA.length; i++) {
            checkIntersection(range(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testSubtractSingleRange() {
        int[][] inA = {
                        {0, 10},
                        {1, 10},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {1000, MAX_VALUE - 1},
                        {1000, MAX_VALUE}
        };
        int[][] inB = {
                        {1, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 199},
                        {401, MAX_VALUE},
                        {0, 199, 401, MAX_VALUE},
                        {0, 200},
                        {400, MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {0, 254, 256, MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {0, MAX_VALUE - 2, MAX_VALUE, MAX_VALUE},
                        {142, MAX_VALUE - 1}
        };
        int[][] out = {
                        {0, 0},
                        {},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {201, 400},
                        {200, 399},
                        {301, 400},
                        {301, 400},
                        {200, 299},
                        {200, 299},
                        {255, 255},
                        {251, 299, 311, 349},
                        {MAX_VALUE - 1, MAX_VALUE - 1},
                        {MAX_VALUE, MAX_VALUE},
        };
        for (int i = 0; i < inA.length; i++) {
            checkSubtraction(range(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testUnionSingleRange() {
        int[][] inA = {
                        {0, 10},
                        {1, 10},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {200, 400},
                        {1000, MAX_VALUE - 1},
                        {1000, MAX_VALUE}
        };
        int[][] inB = {
                        {1, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 199},
                        {401, MAX_VALUE},
                        {0, 199, 401, MAX_VALUE},
                        {0, 200},
                        {400, MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {0, 254, 256, MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {0, 98, 100, 250, 300, 310, 350, 500, 502, 2000},
                        {0, MAX_VALUE - 2, MAX_VALUE, MAX_VALUE},
                        {142, MAX_VALUE - 1}
        };
        int[][] out = {
                        {0, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 400},
                        {200, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 400},
                        {200, MAX_VALUE},
                        {0, 400},
                        {200, 400},
                        {200, 500},
                        {200, 400},
                        {0, MAX_VALUE},
                        {100, 500},
                        {0, 98, 100, 500, 502, 2000},
                        {0, MAX_VALUE},
                        {142, MAX_VALUE},
        };
        for (int i = 0; i < inA.length; i++) {
            checkUnion(range(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testInverseMultiRange() {
        int[][] in = {
                        {0, 10, 1000, 2000},
                        {1, 10, 1000, 2000},
                        {200, 400, 500, 600},
                        {200, 400, 1000, MAX_VALUE - 1},
                        {0, 10, 1000, MAX_VALUE}
        };
        int[][] out = {
                        {11, 999, 2001, MAX_VALUE},
                        {0, 0, 11, 999, 2001, MAX_VALUE},
                        {0, 199, 401, 499, 601, MAX_VALUE},
                        {0, 199, 401, 999, MAX_VALUE, MAX_VALUE},
                        {11, 999},
        };
        for (int i = 0; i < in.length; i++) {
            checkInverse(multi(in[i]), out[i]);
        }
    }

    @Test
    public void testContainsMultiRange() {
        int[][] inA = {
                        {0, 10, 200, 400},
                        {1, 10, 200, 400},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800}
        };
        int[][] inB = {
                        {1, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 199},
                        {401, 599},
                        {801, MAX_VALUE},
                        {0, 199, 401, 599, 801, MAX_VALUE},
                        {0, 200},
                        {800, MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {300, 700},
                        {300, 450, 500, 700},
                        {100, 250, 300, 450, 500, 700, 750, 900},
                        {0, 254, 256, MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {300, 300, 500, 500, 700, 700},
                        {300, 300, 350, 350, 700, 700, 750, 750}
        };
        boolean[] out = new boolean[]{
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true,
                        false,
                        true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        false,
                        true
        };
        for (int i = 0; i < inA.length; i++) {
            checkContains(multi(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testIntersectionMultiRange() {
        int[][] inA = {
                        {0, 10, 200, 400},
                        {1, 10, 200, 400},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800}
        };
        int[][] inB = {
                        {1, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 199},
                        {401, 599},
                        {801, MAX_VALUE},
                        {0, 199, 401, 599, 801, MAX_VALUE},
                        {0, 200},
                        {800, MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {300, 700},
                        {300, 450, 500, 700},
                        {100, 250, 300, 450, 500, 700, 750, 900},
                        {0, 254, 256, MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {300, 300, 500, 500, 700, 700}
        };
        int[][] out = {
                        {1, 10, 200, 400},
                        {1, 10, 200, 400},
                        {},
                        {},
                        {},
                        {},
                        {200, 200},
                        {800, 800},
                        {200, 300},
                        {200, 300},
                        {300, 400},
                        {300, 400},
                        {300, 400, 600, 700},
                        {300, 400, 600, 700},
                        {200, 250, 300, 400, 600, 700, 750, 800},
                        {200, 254, 256, 400, 600, 800},
                        {200, 250, 300, 310, 350, 400},
                        {300, 300, 700, 700}
        };
        for (int i = 0; i < inA.length; i++) {
            checkIntersection(multi(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testSubtractMultiRange() {
        int[][] inA = {
                        {0, 10, 200, 400},
                        {1, 10, 200, 400},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800}
        };
        int[][] inB = {
                        {1, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 199},
                        {401, 599},
                        {801, MAX_VALUE},
                        {0, 199, 401, 599, 801, MAX_VALUE},
                        {0, 200},
                        {800, MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {300, 700},
                        {300, 450, 500, 700},
                        {100, 250, 300, 450, 500, 700, 750, 900},
                        {0, 254, 256, MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {300, 300, 500, 500, 700, 700}
        };
        int[][] out = {
                        {0, 0},
                        {},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {201, 400, 600, 800},
                        {200, 400, 600, 799},
                        {301, 400, 600, 800},
                        {301, 400, 600, 800},
                        {200, 299, 600, 800},
                        {200, 299, 600, 800},
                        {200, 299, 701, 800},
                        {200, 299, 701, 800},
                        {251, 299, 701, 749},
                        {255, 255},
                        {251, 299, 311, 349, 600, 800},
                        {200, 299, 301, 400, 600, 699, 701, 800}
        };
        for (int i = 0; i < inA.length; i++) {
            checkSubtraction(multi(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testUnionMultiRange() {
        int[][] inA = {
                        {0, 10, 200, 400},
                        {1, 10, 200, 400},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 400, 600, 800}
        };
        int[][] inB = {
                        {1, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 199},
                        {401, 599},
                        {801, MAX_VALUE},
                        {0, 199, 401, 599, 801, MAX_VALUE},
                        {0, 200},
                        {800, MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {300, 700},
                        {300, 450, 500, 700},
                        {100, 250, 300, 450, 500, 700, 750, 900},
                        {0, 254, 256, MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {300, 300, 500, 500, 700, 700}
        };
        int[][] out = {
                        {0, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 400, 600, 800},
                        {200, 800},
                        {200, 400, 600, MAX_VALUE},
                        {0, MAX_VALUE},
                        {0, 400, 600, 800},
                        {200, 400, 600, MAX_VALUE},
                        {0, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 500, 600, 800},
                        {200, 400, 600, 800},
                        {200, 800},
                        {200, 450, 500, 800},
                        {100, 450, 500, 900},
                        {0, MAX_VALUE},
                        {100, 500, 600, 800},
                        {200, 400, 500, 500, 600, 800}
        };
        for (int i = 0; i < inA.length; i++) {
            checkUnion(multi(inA[i]), multi(inB[i]), out[i]);
        }
    }
}
