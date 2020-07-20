/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.charset;

import static com.oracle.truffle.regex.util.BitSets.highByte;
import static com.oracle.truffle.regex.util.BitSets.lowByte;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.buffer.CompilationBuffer;
import com.oracle.truffle.regex.tregex.matchers.AnyMatcher;
import com.oracle.truffle.regex.tregex.matchers.BitSetMatcher;
import com.oracle.truffle.regex.tregex.matchers.CharMatcher;
import com.oracle.truffle.regex.tregex.matchers.EmptyMatcher;
import com.oracle.truffle.regex.tregex.matchers.HybridBitSetMatcher;
import com.oracle.truffle.regex.tregex.matchers.InvertibleCharMatcher;
import com.oracle.truffle.regex.tregex.matchers.MultiBitSetMatcher;
import com.oracle.truffle.regex.tregex.matchers.RangeListMatcher;
import com.oracle.truffle.regex.tregex.matchers.RangeTreeMatcher;
import com.oracle.truffle.regex.tregex.matchers.SingleCharMatcher;
import com.oracle.truffle.regex.tregex.matchers.SingleRangeMatcher;
import com.oracle.truffle.regex.tregex.matchers.TwoCharMatcher;
import com.oracle.truffle.regex.util.CompilationFinalBitSet;

/**
 * Helper class for converting {@link CodePointSet}s to {@link CharMatcher}s.
 */
public class CharMatchers {

    public static CharMatcher createMatcher(CodePointSet cps, CompilationBuffer compilationBuffer) {
        if (cps.matchesMinAndMax(compilationBuffer.getEncoding()) || cps.inverseIsSameHighByte(compilationBuffer.getEncoding())) {
            // the inverse of the given set is easier to match, generate inverted matcher
            return createMatcher(cps.createInverse(compilationBuffer.getEncoding()), compilationBuffer, true);
        }
        return createMatcher(cps, compilationBuffer, false);
    }

    private static CharMatcher createMatcher(CodePointSet cps, CompilationBuffer compilationBuffer, boolean inverse) {
        if (cps.isEmpty()) {
            return EmptyMatcher.create(inverse);
        }
        if (cps.matchesEverything(compilationBuffer.getEncoding())) {
            return AnyMatcher.create(inverse);
        }
        if (cps.matchesSingleChar()) {
            return SingleCharMatcher.create(inverse, cps.getMin());
        }
        if (cps.valueCountEquals(2)) {
            return TwoCharMatcher.create(inverse, cps.getMin(), cps.getMax());
        }
        int size = cps.size();
        if (size == 1) {
            return SingleRangeMatcher.create(inverse, cps.getMin(), cps.getMax());
        }
        if (preferRangeListMatcherOverBitSetMatcher(cps, size)) {
            return RangeListMatcher.create(inverse, cps.toArray());
        }
        if (highByte(cps.getMin()) == highByte(cps.getMax())) {
            return convertToBitSetMatcher(cps, compilationBuffer, inverse);
        }
        if (size > 100 && cps.getMax() <= 0xffff) {
            return MultiBitSetMatcher.fromRanges(inverse, cps);
        } else {
            CompressedCodePointSet ccps = CompressedCodePointSet.create(cps, compilationBuffer);
            if (ccps.hasBitSets()) {
                return HybridBitSetMatcher.create(inverse, ccps);
            } else if (ccps.size() <= 10) {
                return RangeListMatcher.create(inverse, ccps.getRanges());
            } else {
                return RangeTreeMatcher.fromRanges(inverse, ccps.getRanges());
            }
        }
    }

    private static boolean preferRangeListMatcherOverBitSetMatcher(CodePointSet cps, int size) {
        // for up to two ranges, RangeListMatcher is faster than any BitSet matcher
        // also, up to four single character checks are still faster than a bit set
        return size <= 2 || cps.valueCountMax(4);
    }

    private static InvertibleCharMatcher convertToBitSetMatcher(CodePointSet cps, CompilationBuffer compilationBuffer, boolean inverse) {
        int highByte = highByte(cps.getMin());
        CompilationFinalBitSet bs = compilationBuffer.getByteSizeBitSet();
        for (int i = 0; i < cps.size(); i++) {
            assert highByte(cps.getLo(i)) == highByte && highByte(cps.getHi(i)) == highByte;
            bs.setRange(lowByte(cps.getLo(i)), lowByte(cps.getHi(i)));
        }
        return BitSetMatcher.create(inverse, highByte, bs.toLongArray());
    }

    @TruffleBoundary
    public static String rangesToString(int[] ranges) {
        return rangesToString(ranges, false);
    }

    @TruffleBoundary
    public static String rangesToString(int[] ranges, boolean numeric) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ranges.length; i += 2) {
            if (numeric) {
                sb.append("[").append(ranges[i]).append("-").append(ranges[i + 1]).append("]");
            } else {
                sb.append(Range.toString(ranges[i], ranges[i + 1]));
            }
        }
        return sb.toString();
    }
}
