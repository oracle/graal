/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.HostCompilerDirectives;
import com.oracle.truffle.api.dsl.InlineSupport.InlineTarget;
import com.oracle.truffle.api.dsl.InlineSupport.IntField;
import com.oracle.truffle.api.dsl.InlineSupport.LongField;
import com.oracle.truffle.api.dsl.InlineSupport.RequiredField;
import com.oracle.truffle.api.nodes.Node;

/**
 * <p>
 * InlinedLoopConditionProfiles are designed to profile the outcome of loop conditions. Loop
 * profiles can be used to profile unpredictable loops as well as predictable loops. This profile is
 * intended to be used in combination with Truffle DSL.
 * </p>
 *
 * <p>
 * <b> Uncounted loop usage example: </b>
 *
 * <pre>
 * class LoopNode extends Node {
 *
 *     abstract void execute();
 *
 *     &#064;Specialization
 *     void doDefault(&#064;Cached InlinedLoopConditionProfile loopProfile) {
 *         // loop count cannot be predicted
 *         while (loopProfile.profile(this, Math.random() &gt;= 0.9)) {
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
 *     abstract void execute(int length);
 *
 *     &#064;Specialization
 *     void doDefault(int length, &#064;Cached InlinedLoopConditionProfile loopProfile) {
 *         // loop count can be predicted
 *         loopProfile.profileCounted(this, length);
 *         for (int i = 0; loopProfile.inject(this, i &lt; length); i++) {
 *             // work
 *         }
 *     }
 * }
 * </pre>
 * <p>
 * The advantage of using {@link #profileCounted(Node, long)} to using
 * {@link #profile(Node, boolean)} is that it incurs less overhead in the interpreter. Using
 * {@link InlinedLoopConditionProfile#inject(Node, boolean)} is a no-op in the interpreter while
 * {@link #profile(Node, boolean)} needs to use a counter for each iteration.
 *
 * @see InlinedConditionProfile
 * @see InlinedCountingConditionProfile
 * @see LoopConditionProfile
 * @since 23.0
 */
public final class InlinedLoopConditionProfile extends InlinedProfile {

    private static final InlinedLoopConditionProfile DISABLED;
    static {
        InlinedLoopConditionProfile profile = new InlinedLoopConditionProfile();
        DISABLED = profile;
    }

    private final LongField trueCount;
    private final IntField falseCount;

    /**
     * A constant holding the maximum value an {@code int} can have, 2<sup>30</sup>-1. The sum of
     * the true and false count must not overflow. This constant is used to check whether one of the
     * counts does not exceed the required maximum value.
     */
    static final int MAX_VALUE = 0x3fffffff;

    private InlinedLoopConditionProfile() {
        this.trueCount = null;
        this.falseCount = null;
    }

    private InlinedLoopConditionProfile(InlineTarget target) {
        this.trueCount = target.getPrimitive(0, LongField.class);
        this.falseCount = target.getPrimitive(1, IntField.class);
    }

    /** @since 23.0 */
    public boolean profile(Node node, boolean condition) {
        if (trueCount == null) {
            return condition;
        }
        // locals required to guarantee no overflow in multi-threaded environments
        long trueCountLocal = trueCount.get(node);
        int falseCountLocal = falseCount.get(node);

        if (trueCountLocal == 0) {
            // Deopt for never entering the loop.
            if (condition) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
        }

        if (HostCompilerDirectives.inInterpreterFastPath()) {
            if (condition) {
                if (trueCountLocal < Long.MAX_VALUE) {
                    trueCount.set(node, trueCountLocal + 1);
                }
            } else {
                if (falseCountLocal < Integer.MAX_VALUE) {
                    falseCount.set(node, falseCountLocal + 1);
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
     * see {@link InlinedLoopConditionProfile} for an usage example.
     *
     * @see #inject(Node, boolean)
     * @since 23.0
     */
    public void profileCounted(Node node, long length) {
        if (HostCompilerDirectives.inInterpreterFastPath() && this != DISABLED) {
            long trueCountLocal = trueCount.get(node) + length;
            if (trueCountLocal >= 0) { // don't write overflow values
                trueCount.set(node, trueCountLocal);
                int falseCountLocal = falseCount.get(node);
                if (falseCountLocal < Integer.MAX_VALUE) {
                    falseCount.set(node, falseCountLocal + 1);
                }
            }
        }
    }

    /**
     * Provides an alternative way to profile counted loops with less interpreter footprint. Please
     * see {@link InlinedLoopConditionProfile} for an usage example.
     *
     * @since 23.0
     */
    public boolean inject(Node node, boolean condition) {
        if (CompilerDirectives.inCompiledCode() && this != DISABLED) {
            return CompilerDirectives.injectBranchProbability(calculateProbability(trueCount.get(node), falseCount.get(node)), condition);
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
     * Returns <code>true</code> if the {@link #profile(Node, boolean)} method ever received a
     * <code>true</code> value, otherwise <code>false</code>. For profiles with profiling disabled
     * or {@link #getUncached() uncached} profiles this method always returns <code>true</code>.
     *
     * @since 23.0
     */
    public boolean wasTrue(Node node) {
        return getTrueCount(node) != 0;
    }

    /**
     * Returns <code>true</code> if the {@link #profile(Node, boolean)} method ever received a
     * <code>false</code> value, otherwise <code>false</code>. For profiles with profiling disabled
     * or {@link #getUncached() uncached} profiles this method always returns <code>true</code>.
     *
     * @since 23.0
     */
    public boolean wasFalse(Node node) {
        return getFalseCount(node) != 0;
    }

    /**
     * {@inheritDoc}
     *
     * @since 23.0
     */
    @Override
    public void disable(Node node) {
        if (trueCount == null) {
            return;
        }
        if (this.trueCount.get(node) == 0) {
            this.trueCount.set(node, 1);
        }
        if (this.falseCount.get(node) == 0) {
            this.falseCount.set(node, 1);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 23.0
     */
    @Override
    public void reset(Node node) {
        if (trueCount == null) {
            return;
        }
        this.trueCount.set(node, 0);
        this.falseCount.set(node, 0);
    }

    long getTrueCount(Node node) {
        if (trueCount == null) {
            return Integer.MAX_VALUE;
        }
        return trueCount.get(node);
    }

    int getFalseCount(Node node) {
        if (trueCount == null) {
            return Integer.MAX_VALUE;
        }
        return falseCount.get(node);
    }

    boolean isGeneric(Node node) {
        if (trueCount == null) {
            return true;
        }
        return getTrueCount(node) != 0 && getFalseCount(node) != 0;
    }

    boolean isUninitialized(Node node) {
        if (trueCount == null) {
            return false;
        }
        return getTrueCount(node) == 0 && getFalseCount(node) == 0;
    }

    /**
     * {@inheritDoc}
     *
     * @since 23.0
     */
    @Override
    public String toString(Node node) {
        if (trueCount == null) {
            return toStringDisabled();
        }
        long t = trueCount.get(node);
        int f = falseCount.get(node);
        long sum = t + f;
        String details = String.format("trueProbability=%s (trueCount=%s, falseCount=%s)", (double) t / (double) sum, t, f);
        return toString(ConditionProfile.class, sum == 0, false, details);
    }

    /**
     * Returns an inlined version of the profile. This version is automatically used by Truffle DSL
     * node inlining.
     *
     * @since 23.0
     */
    public static InlinedLoopConditionProfile inline(
                    @RequiredField(value = LongField.class)//
                    @RequiredField(value = IntField.class) InlineTarget target) {
        if (Profile.isProfilingEnabled()) {
            return new InlinedLoopConditionProfile(target);
        } else {
            return getUncached();
        }
    }

    /**
     * Returns the uncached version of the profile. The uncached version of a profile does not
     * perform any profiling.
     *
     * @since 23.0
     */
    public static InlinedLoopConditionProfile getUncached() {
        return DISABLED;
    }

}
