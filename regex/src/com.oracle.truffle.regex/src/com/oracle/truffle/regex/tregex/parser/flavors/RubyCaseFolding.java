/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.parser.flavors;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.truffle.regex.UnsupportedRegexException;
import com.oracle.truffle.regex.charset.CodePointSet;
import com.oracle.truffle.regex.charset.CodePointSetAccumulator;
import com.oracle.truffle.regex.tregex.parser.RegexASTBuilder;
import com.oracle.truffle.regex.tregex.parser.flavors.RubyCaseUnfoldingTrie.Unfolding;

public class RubyCaseFolding {

    public static void caseFoldUnfoldString(int[] codepoints, CodePointSet encodingRange, RegexASTBuilder astBuilder) {
        caseFoldUnfoldString(codepoints, encodingRange, false, astBuilder);
    }

    /**
     * Appends to the {@code astBuilder} a matcher that matches all case variants of the input
     * string.
     * 
     * @param codepoints the input string as an array of Unicode codepoints
     * @param encodingRange the range of characters that we should limit ourselves to
     * @param dropAsciiOnStart whether we should forbid ASCII characters on the first positions of
     *            the variants
     * @param astBuilder where to append the matcher
     */
    public static void caseFoldUnfoldString(int[] codepoints, CodePointSet encodingRange, boolean dropAsciiOnStart, RegexASTBuilder astBuilder) {
        List<Integer> caseFolded = caseFold(codepoints);

        List<Unfolding> unfoldings = RubyCaseUnfoldingTrie.findUnfoldings(caseFolded);
        // We assume that if `codepoints` was in the encodingRange, than so will be `caseFolded`.
        // The only way that we could introduce out-of-range characters is through the unfoldings,
        // so just filter those should be enough to prevent generating out-of-range matchers.
        unfoldings = unfoldings.stream().filter(u -> encodingRange.contains(u.getCodepoint())).collect(Collectors.toList());

        astBuilder.pushGroup();

        // We identify segments of the string which are independent, i.e. there is no unfolding
        // that crosses the boundary of a segment. We the unfold each segment separately, which
        // helps to avoid unnecessary combinatorial explosions.
        int start = 0;
        int end = 0;
        int unfoldingsStartIndex = 0;
        int unfoldingsEndIndex = 0;
        for (int i = 0; i < unfoldings.size(); i++) {
            Unfolding unfolding = unfoldings.get(i);
            if (unfolding.getStart() >= end) {
                unfoldSegment(astBuilder, caseFolded, unfoldings.subList(unfoldingsStartIndex, unfoldingsEndIndex), start, end, 0, dropAsciiOnStart);
                if (unfolding.getStart() > end) {
                    // If the following mandatory string that we would add would be at the
                    // beginning of the matcher and it would match an ASCII character, then we
                    // return a dead matcher instead (if dropAsciiOnStart is set).
                    if (dropAsciiOnStart && end == 0 && RubyRegexParser.isAscii(caseFolded.get(end))) {
                        astBuilder.popGroup();
                        astBuilder.replaceCurTermWithDeadNode();
                        return;
                    }
                    addString(astBuilder, caseFolded.subList(end, unfolding.getStart()));
                }
                start = unfolding.getStart();
                unfoldingsStartIndex = i;
            }
            end = Math.max(end, unfolding.getEnd());
            unfoldingsEndIndex = i + 1;
        }

        unfoldSegment(astBuilder, caseFolded, unfoldings.subList(unfoldingsStartIndex, unfoldingsEndIndex), start, end, 0, dropAsciiOnStart);
        if (end < caseFolded.size()) {
            if (dropAsciiOnStart && end == 0 && RubyRegexParser.isAscii(caseFolded.get(end))) {
                astBuilder.popGroup();
                astBuilder.replaceCurTermWithDeadNode();
                return;
            }
            addString(astBuilder, caseFolded.subList(end, caseFolded.size()));
        }

        astBuilder.popGroup();
    }

    private static List<Integer> caseFold(int[] codepoints) {
        List<Integer> caseFolded = new ArrayList<>();
        for (int codepoint : codepoints) {
            if (RubyCaseFoldingData.CASE_FOLD.containsKey(codepoint)) {
                for (int caseFoldedCodepoint : RubyCaseFoldingData.CASE_FOLD.get(codepoint)) {
                    caseFolded.add(caseFoldedCodepoint);
                }
            } else {
                caseFolded.add(codepoint);
            }
        }
        return caseFolded;
    }

    private static void addChar(RegexASTBuilder astBuilder, int codepoint) {
        astBuilder.addCharClass(CodePointSet.create(codepoint), true);
    }

    private static void addString(RegexASTBuilder astBuilder, List<Integer> codepoints) {
        for (int codepoint : codepoints) {
            addChar(astBuilder, codepoint);
        }
    }

    private static void unfoldSegment(RegexASTBuilder astBuilder, List<Integer> caseFolded, List<Unfolding> unfoldings, int start, int end, int backtrackingDepth, boolean dropAsciiOnStart) {
        if (backtrackingDepth > 8) {
            throw new UnsupportedRegexException("case-unfolding of case-insensitive string is too complex");
        }
        // The terminating condition of this recursion. This is reached when we have generated
        // an alternative that covers the entire case-folded segment given by `start` and `end`.
        if (start == end) {
            return;
        }

        // This shouldn't happen in our current use case, but it's included for completeness.
        if (unfoldings.isEmpty()) {
            addString(astBuilder, caseFolded.subList(start, end));
            return;
        }

        Unfolding unfolding = unfoldings.get(0);

        // Fast-forward to the next possible unfolding.
        if (unfolding.getStart() > start) {
            addString(astBuilder, caseFolded.subList(start, unfolding.getStart()));
            unfoldSegment(astBuilder, caseFolded, unfoldings, unfolding.getStart(), end, backtrackingDepth, dropAsciiOnStart);
            return;
        }

        // The unfolding has length > 1. We will generate two alternatives, one with the sequence
        // unfolded and one with the sequence folded.
        if (unfolding.getLength() > 1) {
            // If we do the unfolding, we will advance the `start` position. We will therefore
            // also clean up the `unfoldings` list so that we drop unfoldings that will be ruled
            // out by the current unfolding.
            int unfoldingsNextIndex = 1;
            while (unfoldingsNextIndex < unfoldings.size() && unfoldings.get(unfoldingsNextIndex).getStart() < unfolding.getEnd()) {
                unfoldingsNextIndex++;
            }
            // We push a new group so that we limit the scope of the alternation operator (|).
            astBuilder.pushGroup();
            addChar(astBuilder, unfolding.getCodepoint());
            unfoldSegment(astBuilder, caseFolded, unfoldings.subList(unfoldingsNextIndex, unfoldings.size()), unfolding.getEnd(), end, backtrackingDepth + 1, dropAsciiOnStart);
            astBuilder.nextSequence(); // |
            // In the second alternative, where we decide not to pursue the unfolding, we just drop
            // it from the `unfoldings` list.
            unfoldSegment(astBuilder, caseFolded, unfoldings.subList(1, unfoldings.size()), start, end, backtrackingDepth + 1, dropAsciiOnStart);
            astBuilder.popGroup();
            return;
        }

        // The only possible unfoldings at this position have length == 1. We can express all the
        // choices by using a character class.
        CodePointSetAccumulator acc = new CodePointSetAccumulator();
        if (!dropAsciiOnStart || start != 0 || !RubyRegexParser.isAscii(caseFolded.get(start))) {
            acc.addCodePoint(caseFolded.get(start));
        }
        int unfoldingsNextIndex = 0;
        while (unfoldingsNextIndex < unfoldings.size() && unfoldings.get(unfoldingsNextIndex).getStart() == start) {
            // The `unfoldings` are sorted by length in descending order and all unfoldings have
            // length > 0.
            assert unfoldings.get(unfoldingsNextIndex).getLength() == 1;
            int codepoint = unfoldings.get(unfoldingsNextIndex).getCodepoint();
            if (!dropAsciiOnStart || start != 0 || !RubyRegexParser.isAscii(codepoint)) {
                acc.addCodePoint(codepoint);
            }
            unfoldingsNextIndex++;
        }
        astBuilder.addCharClass(acc.toCodePointSet());
        unfoldSegment(astBuilder, caseFolded, unfoldings.subList(unfoldingsNextIndex, unfoldings.size()), start + 1, end, backtrackingDepth, dropAsciiOnStart);
    }
}
