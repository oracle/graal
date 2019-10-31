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

import com.oracle.truffle.regex.charset.CharSet;
import com.oracle.truffle.regex.charset.ImmutableSortedListOfRanges;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;

import static org.junit.Assert.assertTrue;

import org.junit.Assert;
import org.junit.Test;

public class MatcherBuilderTest {

    private static CharSet single(char i) {
        return range(i, i);
    }

    private static CharSet range(char i, char j) {
        return CharSet.create(i, j);
    }

    private static CharSet range(char[] i) {
        Assert.assertEquals(i.length, 2);
        return range(i[0], i[1]);
    }

    private static CharSet multi(char... values) {
        assert (values.length & 1) == 0;
        return CharSet.create(values);
    }

    private static String matchError(String errorMsg, CharSet m, CharSet expected) {
        return String.format("%s: got %s, expected %s", errorMsg, m, expected);
    }

    private static String matchError(String errorMsg, CharSet m, char[] values) {
        StringBuilder sb = new StringBuilder(errorMsg).append(": got ").append(m.toString()).append(", expected [ ");
        for (int i = 0; i < values.length; i += 2) {
            sb.append("[").append(values[i]).append("-").append(values[i + 1]).append("] ");
        }
        return sb.append("]").toString();
    }

    private static void checkMatch(String errorMsg, CharSet m, CharSet expected) {
        Assert.assertArrayEquals(matchError(errorMsg, m, expected), expected.getRanges(), m.getRanges());
    }

    private static void checkMatch(String errorMsg, CharSet m, char... values) {
        Assert.assertArrayEquals(matchError(errorMsg, m, values), values, m.getRanges());
    }

    private static void checkContains(CharSet a, CharSet b, boolean expected) {
        boolean test = a.contains(b);
        Assert.assertEquals(a + ".contains(" + b + "): got " + test + ", expected " + expected, test, expected);
    }

    private static void checkInverse(CharSet a, char... values) {
        checkMatch("inverse(" + a + ")", a.createInverse(), values);
    }

    private static void checkIntersection(CharSet a, CharSet b, char... values) {
        CharSet intersection = a.createIntersection(b, new CompilationBuffer());
        checkMatch("intersection(" + a + "," + b + ")", intersection, values);
        assertTrue("intersection(" + a + "," + b + ")", a.intersects(b) == intersection.matchesSomething());
        ImmutableSortedListOfRanges.IntersectAndSubtractResult<CharSet> result = a.intersectAndSubtract(b, new CompilationBuffer());
        checkMatch("intersectAndSubtract(" + a + "," + b + ")[0]", result.subtractedA, a.subtract(intersection, new CompilationBuffer()));
        checkMatch("intersectAndSubtract(" + a + "," + b + ")[1]", result.subtractedB, b.subtract(intersection, new CompilationBuffer()));
        checkMatch("intersectAndSubtract(" + a + "," + b + ")[2]", result.intersection, intersection);
    }

    private static void checkSubtraction(CharSet a, CharSet b, char... values) {
        checkMatch("subtraction(" + a + "," + b + ")", a.subtract(b, new CompilationBuffer()), values);
    }

    private static void checkUnion(CharSet a, CharSet b, char... values) {
        checkMatch("union(" + a + "," + b + ")", a.union(b, new CompilationBuffer()), values);
    }

    @Test
    public void testInverseSingle() {
        char[] in = {0, 1, 255, Character.MAX_VALUE - 1, Character.MAX_VALUE};
        char[][] out = {
                        {1, Character.MAX_VALUE},
                        {0, 0, 2, Character.MAX_VALUE},
                        {0, 254, 256, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE - 1},
        };
        for (int i = 0; i < in.length; i++) {
            checkInverse(single(in[i]), out[i]);
        }
    }

    @Test
    public void testContainsSingle() {
        char[] inA = {
                        0,
                        0,
                        0,
                        0,
                        255,
                        255,
                        Character.MAX_VALUE - 1,
                        Character.MAX_VALUE
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 1},
                        {0, 0},
                        {0, 0, 2, Character.MAX_VALUE},
                        {0, 254, 256, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE}
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
        char[] inA = {
                        0,
                        0,
                        0,
                        255,
                        255,
                        Character.MAX_VALUE - 1,
                        Character.MAX_VALUE
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 1},
                        {0, 0, 2, Character.MAX_VALUE},
                        {0, 254, 256, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE}
        };
        char[][] out = {
                        {},
                        {0, 0},
                        {0, 0},
                        {255, 255},
                        {},
                        {},
                        {Character.MAX_VALUE, Character.MAX_VALUE},
        };
        for (int i = 0; i < inA.length; i++) {
            checkIntersection(single(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testSubtractSingle() {
        char[] inA = {
                        0,
                        0,
                        0,
                        255,
                        255,
                        Character.MAX_VALUE - 1,
                        Character.MAX_VALUE
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 1},
                        {0, 0, 2, Character.MAX_VALUE},
                        {0, 254, 256, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE}
        };
        char[][] out = {
                        {0, 0},
                        {},
                        {},
                        {},
                        {255, 255},
                        {Character.MAX_VALUE - 1, Character.MAX_VALUE - 1},
                        {},
        };
        for (int i = 0; i < inA.length; i++) {
            checkSubtraction(single(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testUnionSingle() {
        char[] inA = {
                        0,
                        0,
                        0,
                        255,
                        255,
                        Character.MAX_VALUE - 1,
                        Character.MAX_VALUE,
                        255,
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 1},
                        {0, 0, 2, Character.MAX_VALUE},
                        {0, 254, 256, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE},
                        {142, 150, 190, 200, 300, 340}
        };
        char[][] out = {
                        {0, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 1},
                        {0, 0, 2, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE},
                        {142, 150, 190, 200, 255, 255, 300, 340}
        };
        for (int i = 0; i < inA.length; i++) {
            checkUnion(single(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testInverseSingleRange() {
        char[][] in = {
                        {0, 10},
                        {1, 10},
                        {200, 400},
                        {1000, Character.MAX_VALUE - 1},
                        {1000, Character.MAX_VALUE}
        };
        char[][] out = {
                        {11, Character.MAX_VALUE},
                        {0, 0, 11, Character.MAX_VALUE},
                        {0, 199, 401, Character.MAX_VALUE},
                        {0, 999, Character.MAX_VALUE, Character.MAX_VALUE},
                        {0, 999},
        };
        for (int i = 0; i < in.length; i++) {
            checkInverse(range(in[i]), out[i]);
        }
    }

    @Test
    public void testContainsSingleRange() {
        char[][] inA = {
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
                        {1000, Character.MAX_VALUE - 1},
                        {1000, Character.MAX_VALUE}
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 199},
                        {401, Character.MAX_VALUE},
                        {0, 199, 401, Character.MAX_VALUE},
                        {0, 200},
                        {200, 200},
                        {400, Character.MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {0, 254, 256, Character.MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE - 1}
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
        char[][] inA = {
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
                        {1000, Character.MAX_VALUE - 1},
                        {1000, Character.MAX_VALUE}
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 199},
                        {401, Character.MAX_VALUE},
                        {0, 199, 401, Character.MAX_VALUE},
                        {0, 200},
                        {200, 200},
                        {400, Character.MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {0, 254, 256, Character.MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE - 1}
        };
        char[][] out = {
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
                        {1000, Character.MAX_VALUE - 2},
                        {1000, Character.MAX_VALUE - 1},
        };
        for (int i = 0; i < inA.length; i++) {
            checkIntersection(range(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testSubtractSingleRange() {
        char[][] inA = {
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
                        {1000, Character.MAX_VALUE - 1},
                        {1000, Character.MAX_VALUE}
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 199},
                        {401, Character.MAX_VALUE},
                        {0, 199, 401, Character.MAX_VALUE},
                        {0, 200},
                        {400, Character.MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {0, 254, 256, Character.MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE - 1}
        };
        char[][] out = {
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
                        {Character.MAX_VALUE - 1, Character.MAX_VALUE - 1},
                        {Character.MAX_VALUE, Character.MAX_VALUE},
        };
        for (int i = 0; i < inA.length; i++) {
            checkSubtraction(range(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testUnionSingleRange() {
        char[][] inA = {
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
                        {1000, Character.MAX_VALUE - 1},
                        {1000, Character.MAX_VALUE}
        };
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 199},
                        {401, Character.MAX_VALUE},
                        {0, 199, 401, Character.MAX_VALUE},
                        {0, 200},
                        {400, Character.MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {0, 254, 256, Character.MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {0, 98, 100, 250, 300, 310, 350, 500, 502, 2000},
                        {0, Character.MAX_VALUE - 2, Character.MAX_VALUE, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE - 1}
        };
        char[][] out = {
                        {0, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 400},
                        {200, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 400},
                        {200, Character.MAX_VALUE},
                        {0, 400},
                        {200, 400},
                        {200, 500},
                        {200, 400},
                        {0, Character.MAX_VALUE},
                        {100, 500},
                        {0, 98, 100, 500, 502, 2000},
                        {0, Character.MAX_VALUE},
                        {142, Character.MAX_VALUE},
        };
        for (int i = 0; i < inA.length; i++) {
            checkUnion(range(inA[i]), multi(inB[i]), out[i]);
        }
    }

    @Test
    public void testInverseMultiRange() {
        char[][] in = {
                        {0, 10, 1000, 2000},
                        {1, 10, 1000, 2000},
                        {200, 400, 500, 600},
                        {200, 400, 1000, Character.MAX_VALUE - 1},
                        {0, 10, 1000, Character.MAX_VALUE}
        };
        char[][] out = {
                        {11, 999, 2001, Character.MAX_VALUE},
                        {0, 0, 11, 999, 2001, Character.MAX_VALUE},
                        {0, 199, 401, 499, 601, Character.MAX_VALUE},
                        {0, 199, 401, 999, Character.MAX_VALUE, Character.MAX_VALUE},
                        {11, 999},
        };
        for (int i = 0; i < in.length; i++) {
            checkInverse(multi(in[i]), out[i]);
        }
    }

    @Test
    public void testContainsMultiRange() {
        char[][] inA = {
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
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 199},
                        {401, 599},
                        {801, Character.MAX_VALUE},
                        {0, 199, 401, 599, 801, Character.MAX_VALUE},
                        {0, 200},
                        {800, Character.MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {300, 700},
                        {300, 450, 500, 700},
                        {100, 250, 300, 450, 500, 700, 750, 900},
                        {0, 254, 256, Character.MAX_VALUE},
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
        char[][] inA = {
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
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 199},
                        {401, 599},
                        {801, Character.MAX_VALUE},
                        {0, 199, 401, 599, 801, Character.MAX_VALUE},
                        {0, 200},
                        {800, Character.MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {300, 700},
                        {300, 450, 500, 700},
                        {100, 250, 300, 450, 500, 700, 750, 900},
                        {0, 254, 256, Character.MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {300, 300, 500, 500, 700, 700}
        };
        char[][] out = {
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
        char[][] inA = {
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
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 199},
                        {401, 599},
                        {801, Character.MAX_VALUE},
                        {0, 199, 401, 599, 801, Character.MAX_VALUE},
                        {0, 200},
                        {800, Character.MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {300, 700},
                        {300, 450, 500, 700},
                        {100, 250, 300, 450, 500, 700, 750, 900},
                        {0, 254, 256, Character.MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {300, 300, 500, 500, 700, 700}
        };
        char[][] out = {
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
        char[][] inA = {
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
        char[][] inB = {
                        {1, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 199},
                        {401, 599},
                        {801, Character.MAX_VALUE},
                        {0, 199, 401, 599, 801, Character.MAX_VALUE},
                        {0, 200},
                        {800, Character.MAX_VALUE},
                        {0, 300},
                        {200, 300},
                        {300, 500},
                        {300, 400},
                        {300, 700},
                        {300, 450, 500, 700},
                        {100, 250, 300, 450, 500, 700, 750, 900},
                        {0, 254, 256, Character.MAX_VALUE},
                        {100, 250, 300, 310, 350, 500},
                        {300, 300, 500, 500, 700, 700}
        };
        char[][] out = {
                        {0, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 400, 600, 800},
                        {200, 800},
                        {200, 400, 600, Character.MAX_VALUE},
                        {0, Character.MAX_VALUE},
                        {0, 400, 600, 800},
                        {200, 400, 600, Character.MAX_VALUE},
                        {0, 400, 600, 800},
                        {200, 400, 600, 800},
                        {200, 500, 600, 800},
                        {200, 400, 600, 800},
                        {200, 800},
                        {200, 450, 500, 800},
                        {100, 450, 500, 900},
                        {0, Character.MAX_VALUE},
                        {100, 500, 600, 800},
                        {200, 400, 500, 500, 600, 800}
        };
        for (int i = 0; i < inA.length; i++) {
            checkUnion(multi(inA[i]), multi(inB[i]), out[i]);
        }
    }
}
