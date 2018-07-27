/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.regex.tregex.parser.Counter;

/**
 * This profile is used for tracking statistics about a compiled regular expression, such as the
 * amount of times the expression was executed and the amount of matches that were found. The
 * profiling information is used by TRegex for deciding whether a regular expression should match
 * capture groups in a lazy or eager way.
 *
 * @see com.oracle.truffle.regex.tregex.nodes.TRegexExecRootNode
 * @see com.oracle.truffle.regex.tregex.nodes.TRegexLazyCaptureGroupsRootNode
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
     * @see com.oracle.truffle.regex.tregex.nodes.TRegexLazyCaptureGroupsRootNode
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
     * Decides whether the capture groups of the regular expression should be matched in an eager
     * manner.
     * 
     * @return {@code true} if:
     *         <ul>
     *         <li>most searches led to a match</li>
     *         <li>the capture groups of most search results were queried</li>
     *         <li>the match often covered a big part of the part of the input string that had to be
     *         traversed in order to find it, or the match was usually very short</li>
     *         </ul>
     */
    public boolean shouldUseEagerMatching() {
        return matchRatio() > 0.5 && cgAccessRatio() > 0.5 && (avgMatchLength < 5 || avgMatchedPortionOfSearchSpace > 0.4);
    }

    @CompilerDirectives.TruffleBoundary
    @Override
    public String toString() {
        return String.format("calls: %d, matches: %d (%.2f%%), cg accesses: %d (%.2f%%), avg matched portion of search space: %.2f%%",
                        calls.getCount(), matches.getCount(), matchRatio() * 100, captureGroupAccesses.getCount(), cgAccessRatio() * 100, avgMatchedPortionOfSearchSpace * 100);
    }

    public interface TracksRegexProfile {
        RegexProfile getRegexProfile();
    }

}
