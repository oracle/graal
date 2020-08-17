/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * <p>
 * ConditionProfiles are useful to profile the outcome of conditions.
 * </p>
 *
 * <p>
 * <b> Usage example: </b>
 *
 * <pre>
 * class AbsoluteNode extends Node {
 *
 *     final ConditionProfile greaterZeroProfile = ConditionProfile.create{Binary,Counting}Profile();
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
 * {@inheritDoc}
 *
 * @see #createBinaryProfile()
 * @see #createCountingProfile()
 * @see LoopConditionProfile
 * @since 0.10
 */
public abstract class ConditionProfile extends Profile {

    ConditionProfile() {
    }

    /** @since 0.10 */
    public abstract boolean profile(boolean value);

    /**
     * Returns a {@link ConditionProfile} that speculates on conditions to be never
     * <code>true</code> or to be never <code>false</code>. Additionally to a binary profile this
     * method returns a condition profile that also counts the number of times the condition was
     * true and false. This information is reported to the underlying optimization system using
     * {@link CompilerDirectives#injectBranchProbability(double, boolean)}. Condition profiles are
     * intended to be used as part of if conditions.
     *
     * @see ConditionProfile
     * @see #createBinaryProfile()
     * @since 0.10
     */
    public static ConditionProfile createCountingProfile() {
        if (Profile.isProfilingEnabled()) {
            return Counting.createLazyLoadClass();
        } else {
            return Disabled.INSTANCE;
        }
    }

    /**
     * Returns a {@link ConditionProfile} that speculates on conditions to be never
     * <code>true</code> or to be never <code>false</code>. Condition profiles are intended to be
     * used as part of if conditions.
     *
     * @see ConditionProfile
     * @see ConditionProfile#createCountingProfile()
     * @since 0.10
     */
    public static ConditionProfile createBinaryProfile() {
        if (Profile.isProfilingEnabled()) {
            return Binary.createLazyLoadClass();
        } else {
            return Disabled.INSTANCE;
        }
    }

    /**
     * Creates a binary ConditionProfile using {@link #createBinaryProfile()}. This is a convenience
     * method so it can be used as {@code @Cached ConditionProfile myProfile} instead of the much
     * longer {@code @Cached("createBinaryProfile()") ConditionProfile myProfile}.
     *
     * @since 20.2
     */
    public static ConditionProfile create() {
        return createBinaryProfile();
    }

    /**
     * Returns the uncached version of the profile. The uncached version of a profile does nothing.
     *
     * @since 19.0
     */
    public static ConditionProfile getUncached() {
        return Disabled.INSTANCE;
    }

    static final class Disabled extends ConditionProfile {

        static final ConditionProfile INSTANCE = new Disabled();

        @Override
        protected Object clone() {
            return INSTANCE;
        }

        @Override
        public boolean profile(boolean value) {
            return value;
        }

        @Override
        public String toString() {
            return toStringDisabled(ConditionProfile.class);
        }

    }

    static final class Counting extends ConditionProfile {

        @CompilationFinal private int trueCount;
        @CompilationFinal private int falseCount;

        /**
         * A constant holding the maximum value an {@code int} can have, 2<sup>30</sup>-1. The sum
         * of the true and false count must not overflow. This constant is used to check whether one
         * of the counts does not exceed the required maximum value.
         */
        public static final int MAX_VALUE = 0x3fffffff;

        Counting() {
        }

        @Override
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
                int sum = t + f;
                return CompilerDirectives.injectBranchProbability((double) t / (double) sum, val);
            }
        }

        int getTrueCount() {
            return trueCount;
        }

        int getFalseCount() {
            return falseCount;
        }

        @Override
        public String toString() {
            int t = trueCount;
            int f = falseCount;
            int sum = t + f;
            String details = String.format("trueProbability=%s (trueCount=%s, falseCount=%s)", (double) t / (double) sum, t, f);
            return toString(ConditionProfile.class, sum == 0, false, details);
        }

        /* Needed for lazy class loading. */
        static ConditionProfile createLazyLoadClass() {
            return new Counting();
        }
    }

    /**
     * Utility class to speculate on conditions to be never true or to be never false. Condition
     * profiles are intended to be used as part of if conditions.
     *
     * @see ConditionProfile#createBinaryProfile()
     */
    static final class Binary extends ConditionProfile {

        @CompilationFinal private boolean wasTrue;
        @CompilationFinal private boolean wasFalse;

        Binary() {
        }

        @Override
        public boolean profile(boolean value) {
            if (value) {
                if (!wasTrue) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    wasTrue = true;
                }
                return true;
            } else {
                if (!wasFalse) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    wasFalse = true;
                }
                return false;
            }
        }

        boolean wasTrue() {
            return wasTrue;
        }

        boolean wasFalse() {
            return wasFalse;
        }

        @Override
        public String toString() {
            return String.format("%s(wasTrue=%s, wasFalse=%s)@%x", getClass().getSimpleName(), wasTrue, wasFalse, hashCode());
        }

        /* Needed for lazy class loading. */
        static ConditionProfile createLazyLoadClass() {
            return new Binary();
        }
    }

}
