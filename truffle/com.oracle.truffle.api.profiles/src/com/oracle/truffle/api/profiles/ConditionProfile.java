/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.profiles;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;

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
 */
public abstract class ConditionProfile extends Profile {

    ConditionProfile() {
    }

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
     */
    public static ConditionProfile createCountingProfile() {
        if (Truffle.getRuntime().isProfilingEnabled()) {
            return Counting.create();
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
     */
    public static ConditionProfile createBinaryProfile() {
        if (Truffle.getRuntime().isProfilingEnabled()) {
            return Binary.create();
        } else {
            return Disabled.INSTANCE;
        }
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

        Counting() {
            /* package protected constructor */
        }

        @Override
        public boolean profile(boolean value) {
            if (CompilerDirectives.inInterpreter()) {
                if (value) {
                    // local required to guarantee no overflow in multi-threaded environments
                    int t = trueCount;
                    if (t < Integer.MAX_VALUE) {
                        trueCount = t + 1;
                    }
                } else {
                    // local required to guarantee no overflow in multi-threaded environments
                    int f = falseCount;
                    if (f < Integer.MAX_VALUE) {
                        falseCount = f + 1;
                    }
                }
                // no branch probability calculation in the interpreter
                return value;
            } else {
                // use trueCount and falseCount as locals for compilation speed
                int t = trueCount;
                int f = falseCount;
                if (value) {
                    if (t == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        t = 1;
                    }
                } else {
                    if (f == 0) {
                        CompilerDirectives.transferToInterpreterAndInvalidate();
                        f = 1;
                    }
                }
                return CompilerDirectives.injectBranchProbability((double) t / (double) (t + f), value);
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
            return toString(ConditionProfile.class, trueCount == 0 && falseCount == 0, false, //
                            String.format("trueProbability=%s (trueCount=%s, falseCount=%s)", (double) t / (double) (t + f), t, f));
        }

        /* Needed for lazy class loading. */
        static ConditionProfile create() {
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
            /* package protected constructor */
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
        static ConditionProfile create() {
            return new Binary();
        }
    }

}
