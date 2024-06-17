/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.InlineSupport.RequiredField;
import com.oracle.truffle.api.nodes.Node;

/**
 * CountingConditionProfiles are useful to profile the outcome of conditions. A counting condition
 * profile holds a count for each branch whether a branch was hit or not and communicates this to
 * the compiler as frequency information. If binary information only is desired for each branch
 * should use {@link InlinedConditionProfile} instead.
 *
 *
 * <p>
 * <b> Usage example: </b>
 *
 * <pre>
 * abstract class AbsoluteNode extends Node {
 *
 *     abstract void execute(int value);
 *
 *     &#64;Specialization
 *     int doDefault(int value,
 *                     &#64;Cached InlinedCountingConditionProfile p) {
 *         if (p.profile(this, value >= 0)) {
 *             return value;
 *         } else {
 *             return -value;
 *         }
 *     }
 * }
 * </pre>
 *
 *
 * @since 23.0
 */
public final class InlinedCountingConditionProfile extends InlinedProfile {

    private static final InlinedCountingConditionProfile DISABLED;
    static {
        InlinedCountingConditionProfile profile = new InlinedCountingConditionProfile();
        DISABLED = profile;
    }

    private final IntField trueCount;
    private final IntField falseCount;

    /**
     * A constant holding the maximum value an {@code int} can have, 2<sup>30</sup>-1. The sum of
     * the true and false count must not overflow. This constant is used to check whether one of the
     * counts does not exceed the required maximum value.
     */
    static final int MAX_VALUE = 0x3fffffff;

    private InlinedCountingConditionProfile() {
        this.trueCount = null;
        this.falseCount = null;
    }

    private InlinedCountingConditionProfile(InlineTarget target) {
        this.trueCount = target.getPrimitive(0, IntField.class);
        this.falseCount = target.getPrimitive(1, IntField.class);
    }

    /** @since 23.0 */
    public boolean profile(Node node, boolean value) {
        if (trueCount == null) {
            return value;
        }
        // locals required to guarantee no overflow in multi-threaded environments
        int t = trueCount.get(node);
        int f = falseCount.get(node);
        boolean val = value;
        if (val) {
            if (t == 0) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
            }
            if (f == 0) {
                // Make this branch fold during PE
                val = true;
            }
            if (HostCompilerDirectives.inInterpreterFastPath()) {
                if (t < MAX_VALUE) {
                    trueCount.set(node, t + 1);
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
            if (HostCompilerDirectives.inInterpreterFastPath()) {
                if (f < MAX_VALUE) {
                    falseCount.set(node, f + 1);
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

    int getTrueCount(Node node) {
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
        int t = trueCount.get(node);
        int f = falseCount.get(node);
        int sum = t + f;
        String details = String.format("trueProbability=%s (trueCount=%s, falseCount=%s)", (double) t / (double) sum, t, f);
        return toString(ConditionProfile.class, sum == 0, false, details);
    }

    /**
     * Returns an inlined version of the profile. This version is automatically used by Truffle DSL
     * node inlining.
     *
     * @since 23.0
     */
    public static InlinedCountingConditionProfile inline(
                    @RequiredField(value = IntField.class)//
                    @RequiredField(value = IntField.class) InlineTarget target) {
        if (Profile.isProfilingEnabled()) {
            return new InlinedCountingConditionProfile(target);
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
    public static InlinedCountingConditionProfile getUncached() {
        return DISABLED;
    }

}
