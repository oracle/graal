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

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.regex.charset.CompressedCodePointSet;
import com.oracle.truffle.regex.charset.Range;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.util.MathUtil;
import com.oracle.truffle.regex.util.BitSets;

/**
 * Character matcher that compiles to a binary search in a sorted list of ranges, like
 * {@link RangeTreeMatcher}, but replaces some subtrees with bit-set matches. Specifically, if the
 * character set contains many short ranges that differ only in the lowest byte, those ranges are
 * converted to a bit-set, which is checked when the binary search finds that the given character is
 * in the byte range denoted by the bit set. The threshold for bit-set conversion is defined by
 * {@link TRegexOptions#TRegexRangeToBitSetConversionThreshold}.
 *
 * Example:
 *
 * <pre>
 * Character set:
 * [0x02, 0x04, 0x06, 0x1000, 0x1020-0x1030]
 *
 * Resulting Hybrid matcher with threshold = 3:
 * - ranges:   [0x02-0x06,          0x1000, 0x1020-0x1030]
 * - bit-sets: [[0x02, 0x04, 0x06], null,   null         ]
 * </pre>
 *
 * @see CompressedCodePointSet
 */
public abstract class HybridBitSetMatcher extends InvertibleCharMatcher {

    @CompilationFinal(dimensions = 1) private final int[] sortedRanges;
    @CompilationFinal(dimensions = 2) private final long[][] bitSets;

    /**
     * Constructs a new {@link HybridBitSetMatcher}.
     *
     * @param invert see {@link InvertibleCharMatcher}.
     * @param bitSets the bit sets that match the low bytes if the character under inspection has
     *            the corresponding high byte.
     */
    HybridBitSetMatcher(boolean invert, int[] sortedRanges, long[][] bitSets) {
        super(invert);
        this.sortedRanges = sortedRanges;
        this.bitSets = bitSets;
        assert bitSets.length == sortedRanges.length / 2;
    }

    public static HybridBitSetMatcher create(boolean invert, CompressedCodePointSet ccps) {
        return HybridBitSetMatcherNodeGen.create(invert, ccps.getRanges(), ccps.getBitSets());
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
            return result(bitSets[mid] == null || BitSets.get(bitSets[mid], lowByte(c)));
        }
    }

    @Override
    public int estimatedCost() {
        return 2 * (MathUtil.log2ceil(sortedRanges.length / 2) - 1);
    }

    @Override
    @TruffleBoundary
    public String toString() {
        StringBuilder sb = new StringBuilder("hybrid ").append(modifiersToString()).append("[");
        for (int i = 0; i < sortedRanges.length; i += 2) {
            if (bitSets[i / 2] == null) {
                sb.append(Range.toString(sortedRanges[i], sortedRanges[i + 1]));
            } else {
                sb.append("[range: ").append(Range.toString(sortedRanges[i], sortedRanges[i + 1])).append(", bs: ").append(BitSets.toString(bitSets[i / 2])).append("]");
            }
        }
        return sb.append("]").toString();
    }
}
