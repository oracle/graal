/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.profiles;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.InlineSupport.InlineTarget;
import com.oracle.truffle.api.dsl.NeverDefault;

/**
 * <p>
 * CountingConditionProfiles are useful to profile the outcome of conditions. A counting condition
 * profile holds a count for each branch whether a branch was hit or not and communicates this to
 * the compiler as frequency information. If binary information only is desired for each branch
 * should use {@link ConditionProfile} instead.
 *
 * </p>
 *
 * <p>
 * <b> Usage example: </b>
 *
 * <pre>
 * class AbsoluteNode extends Node {
 *
 *     final CountingConditionProfile greaterZeroProfile = CountingConditionProfile.create();
 *
 *     void execute(int value) {
 *         if (greaterZeroProfile.profile(value >= 0)) {
 *             return value;
 *         } else {
 *             return -value;
 *         }
 *     }
 * }
 * </pre>
 *
 * @see #create()
 * @see LoopConditionProfile
 * @see CountingConditionProfile
 * @since 23.0
 */
public final class CountingConditionProfile extends Profile {

    private static final CountingConditionProfile DISABLED;
    static {
        CountingConditionProfile profile = new CountingConditionProfile();
        profile.trueCount = Integer.MAX_VALUE;
        profile.falseCount = Integer.MAX_VALUE;
        DISABLED = profile;
    }

    @CompilationFinal private int trueCount;
    @CompilationFinal private int falseCount;

    /**
     * A constant holding the maximum value an {@code int} can have, 2<sup>30</sup>-1. The sum of
     * the true and false count must not overflow. This constant is used to check whether one of the
     * counts does not exceed the required maximum value.
     */
    static final int MAX_VALUE = 0x3fffffff;

    private CountingConditionProfile() {
    }

    /** @since 22.1 */
    public boolean profile(boolean value) {
        // locals required to guarantee no overflow in multi-threaded environments
        int t = trueCount;
        int f = falseCount;
        boolean val = value;
        if (val) {
            if (t == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (f == 0) {
                // Make this branch fold during PE
                val = true;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (t < MAX_VALUE) {
                    trueCount = t + 1;
                }
            }
        } else {
            if (f == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (t == 0) {
                // Make this branch fold during PE
                val = false;
            }
            if (CompilerDirectives.inInterpreter()) {
                if (f < MAX_VALUE) {
                    falseCount = f + 1;
                }
            }
        }
        if (CompilerDirectives.inInterpreter()) {
            // no branch probability calculation in the interpreter
            return val;
        } else {
            if (this == DISABLED) {
                return val;
            }
            int sum = t + f;
            return CompilerDirectives.injectBranchProbability((double) t / (double) sum, val);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 23.0
     */
    @Override
    public void disable() {
        if (this.trueCount == 0) {
            this.trueCount = 1;
        }
        if (this.falseCount == 0) {
            this.falseCount = 1;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 23.0
     */
    @Override
    public void reset() {
        if (this == DISABLED) {
            return;
        }
        this.trueCount = 0;
        this.falseCount = 0;
    }

    int getTrueCount() {
        return trueCount;
    }

    int getFalseCount() {
        return falseCount;
    }

    /**
     * {@inheritDoc}
     *
     * @since 23.0
     */
    @Override
    public String toString() {
        if (this == DISABLED) {
            return toStringDisabled();
        } else {
            int t = trueCount;
            int f = falseCount;
            int sum = t + f;
            String details = String.format("trueProbability=%s (trueCount=%s, falseCount=%s)", (double) t / (double) sum, t, f);
            return toString(CountingConditionProfile.class, sum == 0, false, details);
        }
    }

    /**
     * Returns the uncached version of the profile. The uncached version of a profile does nothing.
     *
     * @since 23.0
     */
    public static CountingConditionProfile getUncached() {
        return DISABLED;
    }

    /**
     * Returns a {@link ConditionProfile} that speculates on conditions to be never
     * <code>true</code> or to be never <code>false</code>. Additionally to a binary profile this
     * method returns a condition profile that also counts the number of times the condition was
     * true and false. This information is reported to the underlying optimization system using
     * {@link CompilerDirectives#injectBranchProbability(double, boolean)}. Condition profiles are
     * intended to be used as part of if conditions.
     *
     * @see ConditionProfile
     * @since 23.0
     */
    @NeverDefault
    public static CountingConditionProfile create() {
        if (isProfilingEnabled()) {
            return new CountingConditionProfile();
        } else {
            return DISABLED;
        }
    }

    /**
     * Returns an inlined version of the profile. This version is automatically used by Truffle DSL
     * node inlining.
     *
     * @since 23.0
     */
    public static InlinedCountingConditionProfile inline(InlineTarget target) {
        return InlinedCountingConditionProfile.inline(target);
    }

}
