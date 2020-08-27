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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.regex.charset.CharMatchers;
import com.oracle.truffle.regex.tregex.util.MathUtil;

/**
 * Character range matcher that compiles to a static binary search.
 *
 * Example:<br>
 * Given a list of ranges [[1, 2], [4, 5], [10, 11], [20, 22]], this matcher will compile to
 * something similar to this:
 *
 * <pre>
 * match(char c) {
 *     if (c < 4) {
 *         return 1 <= c && c <= 2;
 *     } else if (c > 5) {
 *         if (c < 10) {
 *             return false;
 *         } else if (c > 11) {
 *             return 20 <= c && c <= 22;
 *         } else {
 *             return true;
 *         }
 *     } else {
 *         return true;
 *     }
 * }
 * </pre>
 */
public abstract class RangeTreeMatcher extends InvertibleCharMatcher {

    /**
     * Constructs a new {@link RangeTreeMatcher}.
     *
     * @param invert see {@link InvertibleCharMatcher}.
     * @param ranges a sorted array of character ranges in the form [lower inclusive bound of range
     *            0, higher inclusive bound of range 0, lower inclusive bound of range 1, higher
     *            inclusive bound of range 1, ...]. The array contents are not modified by this
     *            method.
     * @return a new {@link RangeTreeMatcher}.
     */
    public static RangeTreeMatcher fromRanges(boolean invert, int[] ranges) {
        return RangeTreeMatcherNodeGen.create(invert, ranges);
    }

    @CompilationFinal(dimensions = 1) private final int[] sortedRanges;

    RangeTreeMatcher(boolean invert, int[] sortedRanges) {
        super(invert);
        this.sortedRanges = sortedRanges;
    }

    @Specialization
    public boolean match(int c) {
        CompilerAsserts.partialEvaluationConstant(this);
        return matchTree(0, (sortedRanges.length >>> 1) - 1, c);
    }

    private boolean matchTree(int fromIndex, int toIndex, int c) {
        CompilerAsserts.partialEvaluationConstant(fromIndex);
        CompilerAsserts.partialEvaluationConstant(toIndex);
        if (fromIndex > toIndex) {
            return result(false);
        }
        final int mid = (fromIndex + toIndex) >>> 1;
        CompilerAsserts.partialEvaluationConstant(mid);
        if (c < sortedRanges[mid << 1]) {
            return matchTree(fromIndex, mid - 1, c);
        } else if (c > sortedRanges[(mid << 1) + 1]) {
            return matchTree(mid + 1, toIndex, c);
        } else {
            return result(true);
        }
    }

    @Override
    public int estimatedCost() {
        // In every node of the tree, we perform two int comparisons (2).
        // The number of nodes in the tree is tree.length / 2, so the depth d of the tree will be
        // MathUtil.log2ceil(tree.length / 2).
        // The average depth of traversal is then d - 1.
        return 2 * (MathUtil.log2ceil(sortedRanges.length / 2) - 1);
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return "tree " + modifiersToString() + "[" + CharMatchers.rangesToString(sortedRanges) + "]";
    }
}
