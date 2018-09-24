/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

/**
 * <p>
 * LoopConditionProfiles are designed to profile the outcome of loop conditions. Loop profiles can
 * be used to profile unpredictable loops as well as predictable loops.
 * </p>
 *
 * <p>
 * <b> Arbitrary loop usage example: </b>
 *
 * <pre>
 * class LoopNode extends Node {
 *
 *     final LoopConditionProfile loopProfile = LoopConditionProfile.createCountingProfile();
 *
 *     void execute() {
 *         // loop count cannot be predicted
 *         while (loopProfile.profile(Math.random() &gt;= 0.9)) {
 *             // work
 *         }
 *     }
 * }
 * </pre>
 *
 * </p>
 *
 * <p>
 * <b> Counted loop usage example: </b>
 *
 * <pre>
 * class CountedLoopNode extends Node {
 *
 *     final LoopConditionProfile loopProfile = LoopConditionProfile.createCountingProfile();
 *
 *     void execute(int length) {
 *         // loop count can be predicted
 *         loopProfile.profileCounted(length);
 *         for (int i = 0; loopProfile.inject(i &lt; length); i++) {
 *             // work
 *         }
 *     }
 * }
 * </pre>
 *
 * </p>
 * <p>
 * The advantage of using {@link #profileCounted(long)} to using {@link #profile(boolean)} is that
 * it incurs less overhead in the interpreter. Using {@link LoopConditionProfile#inject(boolean)} is
 * a no-op in the interpreter while {@link #profile(boolean)} needs to use a counter for each
 * iteration.
 * </p>
 *
 *
 * {@inheritDoc}
 *
 * @see #createBinaryProfile()
 * @see #createCountingProfile()
 * @see LoopConditionProfile
 * @since 0.10
 */
public abstract class LoopConditionProfile extends ConditionProfile {

    LoopConditionProfile() {
    }

    /** @since 0.10 */
    @Override
    public abstract boolean profile(boolean value);

    /**
     * Provides an alternative way to profile counted loops with less interpreter footprint. Please
     * see {@link LoopConditionProfile} for an usage example.
     *
     * @see #inject(boolean)
     * @since 0.10
     */
    public abstract void profileCounted(long length);

    /**
     * Provides an alternative way to profile counted loops with less interpreter footprint. Please
     * see {@link LoopConditionProfile} for an usage example.
     *
     * @see #inject(boolean)
     * @since 0.10
     */
    public abstract boolean inject(boolean condition);

    /**
     * Returns a {@link LoopConditionProfile} that speculates on loop conditions to be never
     * <code>true</code>. It also captures loop probabilities for the compiler. Loop condition
     * profiles are intended to be used for loop conditions.
     *
     * @see LoopConditionProfile
     * @since 0.10
     */
    public static LoopConditionProfile createCountingProfile() {
        if (Profile.isProfilingEnabled()) {
            return Enabled.create();
        } else {
            return Disabled.INSTANCE;
        }
    }

    static final class Enabled extends LoopConditionProfile {

        @CompilationFinal private long trueCount; // long for long running loops.
        @CompilationFinal private int falseCount;

        @Override
        public boolean profile(boolean condition) {
            // locals required to guarantee no overflow in multi-threaded environments
            long trueCountLocal = trueCount;
            int falseCountLocal = falseCount;
            if (trueCountLocal == 0) {
                // Deopt for never entering the loop.
                if (condition) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                }
            }
            // No deopt for not entering the loop.

            if (CompilerDirectives.inInterpreter()) {
                if (condition) {
                    if (trueCountLocal < Long.MAX_VALUE) {
                        trueCount = trueCountLocal + 1;
                    }
                } else {
                    if (falseCountLocal < Integer.MAX_VALUE) {
                        falseCount = falseCountLocal + 1;
                    }
                }
                // no branch probability calculation in the interpreter
                return condition;
            } else {
                return CompilerDirectives.injectBranchProbability(calculateProbability(trueCountLocal, falseCountLocal), condition);
            }
        }

        @Override
        public void profileCounted(long length) {
            if (CompilerDirectives.inInterpreter()) {
                long trueCountLocal = trueCount + length;
                if (trueCountLocal >= 0) { // don't write overflow values
                    trueCount = trueCountLocal;
                    int falseCountLocal = falseCount;
                    if (falseCountLocal < Integer.MAX_VALUE) {
                        falseCount = falseCountLocal + 1;
                    }
                }
            }
        }

        @Override
        public boolean inject(boolean condition) {
            if (CompilerDirectives.inCompiledCode()) {
                return CompilerDirectives.injectBranchProbability(calculateProbability(trueCount, falseCount), condition);
            } else {
                return condition;
            }
        }

        private static double calculateProbability(long trueCountLocal, int falseCountLocal) {
            if (falseCountLocal == 0 && trueCountLocal == 0) {
                /* Avoid division by zero if profile was never used. */
                return 0.0;
            } else {
                return (double) trueCountLocal / (double) (trueCountLocal + falseCountLocal);
            }
        }

        /* for testing */
        long getTrueCount() {
            return trueCount;
        }

        /* for testing */
        int getFalseCount() {
            return falseCount;
        }

        @Override
        public String toString() {
            return toString(LoopConditionProfile.class, falseCount == 0, false, //
                            String.format("trueProbability=%s (trueCount=%s, falseCount=%s)", calculateProbability(trueCount, falseCount), trueCount, falseCount));
        }

        /* Needed for lazy class loading. */
        static LoopConditionProfile create() {
            return new Enabled();
        }

    }

    static final class Disabled extends LoopConditionProfile {

        static final LoopConditionProfile INSTANCE = new Disabled();

        @Override
        protected Object clone() {
            return INSTANCE;
        }

        @Override
        public boolean profile(boolean condition) {
            return condition;
        }

        @Override
        public void profileCounted(long length) {
        }

        @Override
        public boolean inject(boolean condition) {
            return condition;
        }

        @Override
        public String toString() {
            return toStringDisabled(LoopConditionProfile.class);
        }

    }

}
