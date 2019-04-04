/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import static com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.regex.charset.CharSet;

/**
 * Character range matcher using a sorted list of ranges.
 */
public abstract class RangeListMatcher extends InvertibleCharMatcher {

    @CompilationFinal(dimensions = 1) private final char[] ranges;

    /**
     * Constructs a new {@link RangeListMatcher}.
     * 
     * @param invert see {@link InvertibleCharMatcher}.
     * @param ranges a sorted array of character ranges in the form [lower inclusive bound of range
     *            0, higher inclusive bound of range 0, lower inclusive bound of range 1, higher
     *            inclusive bound of range 1, ...]. The array contents are not modified by this
     *            method.
     */
    RangeListMatcher(boolean invert, char[] ranges) {
        super(invert);
        this.ranges = ranges;
    }

    public static RangeListMatcher create(boolean invert, char[] ranges) {
        return RangeListMatcherNodeGen.create(invert, ranges);
    }

    @Specialization
    @ExplodeLoop(kind = ExplodeLoop.LoopExplosionKind.FULL_UNROLL)
    public boolean match(char c, boolean compactString) {
        for (int i = 0; i < ranges.length; i += 2) {
            final char lo = ranges[i];
            final char hi = ranges[i + 1];
            if (compactString && lo > 255) {
                return result(false);
            }
            if (isSingleChar(lo, hi)) {
                // do simple equality checks on ranges that contain a single character
                if (lo == c) {
                    return result(true);
                }
            } else if (isTwoChars(lo, hi)) {
                // do simple equality checks on ranges that contain two characters
                if (c == lo || c == hi) {
                    return result(true);
                }
            } else {
                if (lo <= c) {
                    if (hi >= c) {
                        return result(true);
                    }
                } else {
                    return result(false);
                }
            }
        }
        return result(false);
    }

    private static boolean isSingleChar(char lo, char hi) {
        CompilerAsserts.partialEvaluationConstant(lo);
        CompilerAsserts.partialEvaluationConstant(hi);
        return lo == hi;
    }

    private static boolean isTwoChars(char lo, char hi) {
        CompilerAsserts.partialEvaluationConstant(lo);
        CompilerAsserts.partialEvaluationConstant(hi);
        return lo + 1 == hi;
    }

    @Override
    public int estimatedCost() {
        return ranges.length;
    }

    @Override
    @TruffleBoundary
    public String toString() {
        return "list " + modifiersToString() + "[" + CharSet.rangesToString(ranges) + "]";
    }
}
