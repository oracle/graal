/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.matchers;

import com.oracle.truffle.api.CompilerDirectives;

/**
 * Matcher for a single character range.
 */
public final class SingleRangeMatcher extends ProfiledCharMatcher {

    private final char lo;
    private final char hi;

    /**
     * Constructs a new {@link SingleRangeMatcher}.
     * 
     * @param invert see {@link ProfiledCharMatcher}.
     * @param lo inclusive lower bound of range to match.
     * @param hi inclusive upper bound of range to match.
     */
    public SingleRangeMatcher(boolean invert, char lo, char hi) {
        super(invert);
        this.lo = lo;
        this.hi = hi;
    }

    /**
     * @return inclusive lower bound of range to match.
     */
    public char getLo() {
        return lo;
    }

    /**
     * @return inclusive upper bound of range to match.
     */
    public char getHi() {
        return hi;
    }

    @Override
    public boolean matchChar(char c) {
        return lo <= c && hi >= c;
    }

    @Override
    public int estimatedCost() {
        return 2;
    }

    @Override
    @CompilerDirectives.TruffleBoundary
    public String toString() {
        return modifiersToString() + MatcherBuilder.rangeToString(lo, hi);
    }
}
