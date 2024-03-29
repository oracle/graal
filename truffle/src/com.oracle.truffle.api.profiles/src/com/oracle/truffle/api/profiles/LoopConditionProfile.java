/*
 * Copyright (c) 2015, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.NeverDefault;

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
 *
 * @see #create()
 * @see LoopConditionProfile
 * @since 0.10
 */
public final class LoopConditionProfile extends ConditionProfile {

    private static final LoopConditionProfile DISABLED;
    static {
        LoopConditionProfile profile = new LoopConditionProfile();
        profile.trueCount = Long.MAX_VALUE;
        profile.falseCount = Integer.MAX_VALUE;
        DISABLED = profile;
    }

    @CompilationFinal private long trueCount; // long for long running loops.
    @CompilationFinal private int falseCount;

    LoopConditionProfile() {
    }

    /** @since 0.10 */
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
            if (this != DISABLED) {
                return CompilerDirectives.injectBranchProbability(calculateProbability(trueCountLocal, falseCountLocal), condition);
            } else {
                return condition;
            }
        }
    }

    /**
     * Provides an alternative way to profile counted loops with less interpreter footprint. Please
     * see {@link LoopConditionProfile} for an usage example.
     *
     * @see #inject(boolean)
     * @since 0.10
     */
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

    /**
     * Provides an alternative way to profile counted loops with less interpreter footprint. Please
     * see {@link LoopConditionProfile} for an usage example.
     *
     * @see #inject(boolean)
     * @since 0.10
     */
    public boolean inject(boolean condition) {
        if (CompilerDirectives.inCompiledCode() && this != DISABLED) {
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

    /**
     * {@inheritDoc}
     *
     * @since 22.1
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
     * @since 22.1
     */
    @Override
    public void reset() {
        if (this != DISABLED) {
            this.trueCount = 0L;
            this.falseCount = 0;
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

    /**
     * {@inheritDoc}
     *
     * @since 22.1
     */
    @Override
    public String toString() {
        if (this == DISABLED) {
            return toStringDisabled();
        } else {
            return toString(LoopConditionProfile.class, falseCount == 0, false, //
                            String.format("trueProbability=%s (trueCount=%s, falseCount=%s)", calculateProbability(trueCount, falseCount), trueCount, falseCount));
        }
    }

    /**
     * @since 0.10
     * @deprecated use {@link #create()} instead.
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    @NeverDefault
    public static LoopConditionProfile createCountingProfile() {
        if (isProfilingEnabled()) {
            return new LoopConditionProfile();
        } else {
            return DISABLED;
        }
    }

    /**
     * Returns a {@link LoopConditionProfile} that speculates on loop conditions to be never
     * <code>true</code>. It also captures loop probabilities for the compiler. Loop condition
     * profiles are intended to be used for loop conditions.
     *
     * @since 21.2
     */
    @NeverDefault
    public static LoopConditionProfile create() {
        return createCountingProfile();
    }

    /**
     * Returns the uncached version of the profile. The uncached version of a profile does nothing.
     *
     * @since 19.0
     */
    public static LoopConditionProfile getUncached() {
        return DISABLED;
    }

}
