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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.TRegexOptions;
import com.oracle.truffle.regex.tregex.parser.Counter;

/**
 * This profile is used for tracking statistics about a compiled regular expression, such as the
 * amount of times the expression was executed and the amount of matches that were found. The
 * profiling information is used by TRegex for deciding whether a regular expression should match
 * capture groups in a lazy or eager way.
 *
 * @see com.oracle.truffle.regex.tregex.nodes.TRegexExecRootNode
 * @see com.oracle.truffle.regex.tregex.nodes.dfa.TRegexLazyCaptureGroupsRootNode
 */
public final class RegexProfile {

    private static final int EVALUATION_TRIP_POINT = 800;

    private final Counter.ThreadSafeCounter calls = new Counter.ThreadSafeCounter();
    private final Counter.ThreadSafeCounter matches = new Counter.ThreadSafeCounter();
    private final Counter.ThreadSafeCounter captureGroupAccesses = new Counter.ThreadSafeCounter();
    private double avgMatchLength = 0;
    private double avgMatchedPortionOfSearchSpace = 0;

    /**
     * Increase the number of times the regular expression was executed by one.
     */
    public void incCalls() {
        calls.inc();
    }

    public void resetCalls() {
        calls.reset();
    }

    /**
     * Increase the number of times a match for the regular expression was found by one.
     */
    public void incMatches() {
        matches.inc();
    }

    /**
     * Update profile after the execution of a lazy capture groups search DFA.
     *
     * @param matchLength the length of capture group 0 of the match.
     * @param numberOfCharsTraversed the number of characters that were traversed between the
     *            initial index (fromIndex) and the end of the match.
     * @see com.oracle.truffle.regex.tregex.nodes.dfa.TRegexLazyCaptureGroupsRootNode
     */
    public void profileCaptureGroupAccess(int matchLength, int numberOfCharsTraversed) {
        captureGroupAccesses.inc();
        avgMatchLength += (matchLength - avgMatchLength) / captureGroupAccesses.getCount();
        double matchedPortion = ((double) matchLength) / numberOfCharsTraversed;
        avgMatchedPortionOfSearchSpace += (matchedPortion - avgMatchedPortionOfSearchSpace) / captureGroupAccesses.getCount();
    }

    /**
     * Check if the profiling information gathered so far is sufficient for making a decision.
     *
     * @return {@code true} if the number of times the regular expression was called is divisible by
     *         {@value #EVALUATION_TRIP_POINT}.
     */
    public boolean atEvaluationTripPoint() {
        return calls.getCount() > 0 && (calls.getCount() % EVALUATION_TRIP_POINT) == 0;
    }

    private double matchRatio() {
        assert calls.getCount() > 0;
        return (double) matches.getCount() / calls.getCount();
    }

    private double cgAccessRatio() {
        assert matches.getCount() > 0;
        return (double) captureGroupAccesses.getCount() / matches.getCount();
    }

    /**
     * Decides whether the regular expression was executed often enough to warrant the costly
     * generation of a fully expanded DFA.
     */
    public boolean shouldGenerateDFA() {
        return calls.getCount() >= TRegexOptions.TRegexGenerateDFAThreshold;
    }

    /**
     * Decides whether the capture groups of the regular expression should be matched in an eager
     * manner.
     *
     * @return {@code true} if:
     *         <ul>
     *         <li>most searches led to a match</li>
     *         <li>the capture groups of most search results were queried</li>
     *         <li>the match often covered a big part of the part of the input string that had to be
     *         traversed in order to find it.</li>
     *         </ul>
     */
    public boolean shouldUseEagerMatching() {
        return matchRatio() > 0.5 && cgAccessRatio() > 0.5 && avgMatchedPortionOfSearchSpace > 0.4;
    }

    @TruffleBoundary
    @Override
    public String toString() {
        return String.format("calls: %d, matches: %d (%.2f%%), cg accesses: %d (%.2f%%), avg matched portion of search space: %.2f%%",
                        calls.getCount(), matches.getCount(), matchRatio() * 100, captureGroupAccesses.getCount(), cgAccessRatio() * 100, avgMatchedPortionOfSearchSpace * 100);
    }

    public interface TracksRegexProfile {
        RegexProfile getRegexProfile();
    }
}
