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
package com.oracle.truffle.regex.tregex.nodes.dfa;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.charset.CompressedCodePointSet;
import com.oracle.truffle.regex.charset.Constants;
import com.oracle.truffle.regex.util.BitSets;

/**
 * This class provides an alternative way of calculating the next transition - instead of checking
 * all transitions in sequential manner, all ranges of all transitions are merged into one sorted
 * array, which is then searched in tree-recursive fashion.
 *
 * @see CompressedCodePointSet
 */
public final class AllTransitionsInOneTreeMatcher {

    /**
     * Data structure for optimized matching of multiple ranges in one lower byte range.
     *
     * @see CompressedCodePointSet
     */
    public static final class AllTransitionsInOneTreeLeafMatcher {

        @CompilationFinal(dimensions = 2) private final long[][] bitSets;
        @CompilationFinal(dimensions = 1) private final short[] successors;
        @CompilationFinal(dimensions = 1) private final int[] ranges;

        public AllTransitionsInOneTreeLeafMatcher(long[][] bitSets, short[] successors, int[] ranges) {
            assert successors.length == bitSets.length + ranges.length + 1;
            this.bitSets = bitSets;
            this.successors = successors;
            this.ranges = ranges;
        }

        @Override
        @TruffleBoundary
        public String toString() {
            StringBuilder sb = new StringBuilder("ranges: ").append(rangesToString(ranges)).append("\nbitsets:\n");
            for (int i = 0; i < bitSets.length; i++) {
                sb.append(BitSets.toString(bitSets[i])).append("\n");
            }
            return sb.toString();
        }
    }

    @CompilationFinal(dimensions = 1) private final int[] ranges;
    @CompilationFinal(dimensions = 1) private final short[] successors;
    @CompilationFinal(dimensions = 1) private final AllTransitionsInOneTreeLeafMatcher[] leafMatchers;

    /**
     * Constructs a new {@link AllTransitionsInOneTreeMatcher}.
     *
     * @param ranges a sorted list of adjacent character ranges, in the following format: Every
     *            character in the array simultaneously represents the inclusive lower bound of a
     *            range and the exclusive upper bound of a range. The algorithm adds an implicit
     *            zero at the begin and an implicit {@link Constants#MAX_CODE_POINT} + 1 at the end
     *            of the array. An array representing the ranges
     *            {@code [0x00-0x10][0x10-0xff][0xff-0x2000][0x2000-0x10ffff]} (represented with
     *            exclusive upper bound) would be: {@code [0x10, 0xff, 0x2000]}.
     * @param successors the list of successors corresponding to every range in the sorted list of
     *            ranges. Every entry in this array is an index of
     *            {@link DFAStateNode#getSuccessors()}, or a negative index. A negative index can
     *            mean one of two things: {@code -1} denotes "no successor", indices below
     *            {@code -1} denote {@link AllTransitionsInOneTreeLeafMatcher leaf matchers}. These
     *            specialized matchers are used when many ranges lie in the same lower byte range,
     *            i.e. all bytes of their numerical values except the lowest one are equal (e.g.
     *            {@code [0x2020-0x2021][0x2030-0x2031]...}).
     */
    public AllTransitionsInOneTreeMatcher(int[] ranges, short[] successors, AllTransitionsInOneTreeLeafMatcher[] leafMatchers) {
        assert successors.length == ranges.length + 1;
        this.ranges = ranges;
        this.successors = successors;
        this.leafMatchers = leafMatchers;
    }

    public int checkMatchTree(int c) {
        CompilerAsserts.partialEvaluationConstant(this);
        return checkMatchTree(0, ranges.length - 1, c);
    }

    /**
     * Recursive binary-search through {@code ranges}.
     */
    private int checkMatchTree(int fromIndex, int toIndex, int c) {
        CompilerAsserts.partialEvaluationConstant(fromIndex);
        CompilerAsserts.partialEvaluationConstant(toIndex);
        if (fromIndex > toIndex) {
            final short successor = successors[fromIndex];
            if (successor < -1) {
                return checkMatchLeaf((successor * -1) - 2, c);
            }
            return successor;
        }
        final int mid = (fromIndex + toIndex) >>> 1;
        CompilerAsserts.partialEvaluationConstant(mid);
        if (c < ranges[mid]) {
            return checkMatchTree(fromIndex, mid - 1, c);
        } else {
            return checkMatchTree(mid + 1, toIndex, c);
        }
    }

    /**
     * The search has been narrowed down to a byte range, continue in a leaf matcher. Here, we first
     * check all bit sets, and if none match, we check the remaining ranges that did not get
     * converted to bit sets.
     */
    @ExplodeLoop
    private int checkMatchLeaf(int iLeaf, int c) {
        CompilerAsserts.partialEvaluationConstant(iLeaf);
        AllTransitionsInOneTreeLeafMatcher leafMatcher = leafMatchers[iLeaf];
        int lowByte = BitSets.lowByte(c);
        for (int i = 0; i < leafMatcher.bitSets.length; i++) {
            CompilerAsserts.partialEvaluationConstant(i);
            if (BitSets.get(leafMatcher.bitSets[i], lowByte)) {
                final short successor = leafMatcher.successors[i];
                CompilerAsserts.partialEvaluationConstant(successor);
                return successor;
            }
        }
        return checkMatchLeafSubTree(leafMatcher, 0, leafMatcher.ranges.length - 1, c);
    }

    /**
     * Recursive binary-search through {@code ranges} of a {@link AllTransitionsInOneTreeLeafMatcher
     * leaf matcher}.
     */
    private static int checkMatchLeafSubTree(AllTransitionsInOneTreeLeafMatcher leafMatcher, int fromIndex, int toIndex, int c) {
        CompilerAsserts.partialEvaluationConstant(leafMatcher);
        CompilerAsserts.partialEvaluationConstant(fromIndex);
        CompilerAsserts.partialEvaluationConstant(toIndex);
        if (fromIndex > toIndex) {
            final short successor = leafMatcher.successors[leafMatcher.bitSets.length + fromIndex];
            CompilerAsserts.partialEvaluationConstant(successor);
            if (successor == -1) {
                int lo = fromIndex == 0 ? 0 : leafMatcher.ranges[fromIndex - 1];
                int hi = fromIndex == leafMatcher.ranges.length ? Character.MAX_CODE_POINT + 1 : leafMatcher.ranges[fromIndex];
                CompilerAsserts.partialEvaluationConstant(lo);
                CompilerAsserts.partialEvaluationConstant(hi);
                // TODO: move bitset matches here. requires PE intrinsic.
                return successor;
            } else {
                return successor;
            }
        }
        final int mid = (fromIndex + toIndex) >>> 1;
        CompilerAsserts.partialEvaluationConstant(mid);
        if (c < leafMatcher.ranges[mid]) {
            return checkMatchLeafSubTree(leafMatcher, fromIndex, mid - 1, c);
        } else {
            return checkMatchLeafSubTree(leafMatcher, mid + 1, toIndex, c);
        }
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return "AllTransitionsInOneTreeMatcher: " + rangesToString(ranges);
    }

    @TruffleBoundary
    private static String rangesToString(int[] ranges) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (int c : ranges) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            if (c > 0xff) {
                sb.append(String.format("%04x", c));
            } else {
                sb.append(String.format("%02x", c));
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
