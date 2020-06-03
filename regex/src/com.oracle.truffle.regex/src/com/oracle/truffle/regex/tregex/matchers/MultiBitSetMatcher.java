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

import java.util.Arrays;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.regex.charset.ImmutableSortedListOfIntRanges;
import com.oracle.truffle.regex.charset.Range;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * Character matcher that uses an array of 256 bit sets to fully cover the 16 bit character space.
 * This matcher can take up to 8 kilobytes of space, so it should be used only when the set of
 * characters to match is very large and sparse.
 */
public abstract class MultiBitSetMatcher extends InvertibleCharMatcher {

    private static final int BYTE_RANGE = 256;
    private static final int BYTE_MAX_VALUE = 255;
    private static final int BYTE_MIN_VALUE = 0;

    private static final CompilationFinalBitSet MATCH_NONE = new CompilationFinalBitSet(BYTE_RANGE);
    private static final CompilationFinalBitSet MATCH_ALL = new CompilationFinalBitSet(BYTE_RANGE);

    static {
        MATCH_ALL.invert();
    }

    public static MultiBitSetMatcher fromRanges(boolean inverse, ImmutableSortedListOfIntRanges cps) {
        CompilationFinalBitSet[] bitSets = new CompilationFinalBitSet[BYTE_RANGE];
        Arrays.fill(bitSets, MATCH_NONE);
        CompilationFinalBitSet cur = new CompilationFinalBitSet(BYTE_RANGE);
        int curByte = -1;
        for (Range r : cps) {
            if (curByte == -1) {
                curByte = highByte(r.lo);
            }
            if (highByte(r.lo) > curByte) {
                bitSets[curByte] = cur;
                cur = new CompilationFinalBitSet(BYTE_RANGE);
                curByte = highByte(r.lo);
            }
            if (highByte(r.lo) == highByte(r.hi)) {
                cur.setRange(lowByte(r.lo), lowByte(r.hi));
            } else {
                cur.setRange(lowByte(r.lo), BYTE_MAX_VALUE);
                bitSets[curByte] = cur;
                for (int j = highByte(r.lo) + 1; j < highByte(r.hi); j++) {
                    bitSets[j] = MATCH_ALL;
                }
                cur = new CompilationFinalBitSet(BYTE_RANGE);
                curByte = highByte(r.hi);
                cur.setRange(BYTE_MIN_VALUE, lowByte(r.hi));
            }
        }
        bitSets[curByte] = cur;
        return MultiBitSetMatcherNodeGen.create(inverse, bitSets);
    }

    @CompilationFinal(dimensions = 1) private final CompilationFinalBitSet[] bitSets;

    MultiBitSetMatcher(boolean invert, CompilationFinalBitSet[] bitSets) {
        super(invert);
        this.bitSets = bitSets;
    }

    @Specialization
    protected boolean match(int c, boolean compactString) {
        return result(bitSets[compactString ? 0 : highByte(c)].get(lowByte(c)));
    }

    @Override
    public int estimatedCost() {
        // 4 for the bit set check, 2 for the array load + 2 penalty for potentially huge data
        // structure
        return 8;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        StringBuilder sb = new StringBuilder(modifiersToString()).append("[\n");
        for (int i = 0; i < bitSets.length; i++) {
            sb.append(String.format("    %02x: ", i)).append(bitSets[i]).append("\n");
        }
        return sb.append("  ]").toString();
    }
}
