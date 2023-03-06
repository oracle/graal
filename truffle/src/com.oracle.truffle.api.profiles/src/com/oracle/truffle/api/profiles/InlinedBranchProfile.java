/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.InlineSupport.InlineTarget;
import com.oracle.truffle.api.dsl.InlineSupport.RequiredField;
import com.oracle.truffle.api.dsl.InlineSupport.StateField;
import com.oracle.truffle.api.nodes.Node;

/**
 * BranchProfiles are profiles to speculate on branches that are unlikely to be visited. If the
 * {@link #enter(Node)} method is invoked first the optimized code is invalidated and the branch
 * where {@link #enter(Node)} is invoked is enabled for compilation. Otherwise if the
 * {@link #enter(Node)} method was never invoked the branch will not get compiled.
 * <p>
 * <b> Usage example: </b>
 *
 * <pre>
 * class ExampleNode extends Node {
 *
 *     abstract int execute(int value);
 *
 *     &#064;Specialization
 *     int doDefault(int value, &#064;Cached InlinedBranchProfile errorProfile) {
 *         if (value == Integer.MAX_VALUE) {
 *             errorProfile.enter(this);
 *             throw new Error("Invalid input value");
 *         }
 *         return value;
 *     }
 * }
 * </pre>
 *
 * @see InlinedProfile
 * @since 23.0
 */
public final class InlinedBranchProfile extends InlinedProfile {

    private static final InlinedBranchProfile DISABLED;
    static {
        InlinedBranchProfile profile = new InlinedBranchProfile();
        DISABLED = profile;
    }
    private static final int REQUIRED_STATE_BITS = 1;

    private final StateField state;

    private InlinedBranchProfile() {
        this.state = null;
    }

    private InlinedBranchProfile(InlineTarget target) {
        this.state = target.getState(0, REQUIRED_STATE_BITS);
    }

    /** @since 23.0 */
    public void enter(Node node) {
        if (state == null) {
            return;
        }
        int s = state.get(node);
        if (s == 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            state.set(node, 1);
        }
    }

    /**
     * Returns <code>true</code> if the {@link #enter(Node)} method was ever called, otherwise
     * <code>false</code>. For profiles with profiling disabled or {@link #getUncached() uncached}
     * profiles this method always returns <code>true</code>.
     *
     * @since 23.0
     */
    public boolean wasEntered(Node node) {
        if (state == null) {
            return true;
        }
        return state.get(node) != 0;
    }

    /**
     * {@inheritDoc}
     *
     * @since 23.0
     */
    @Override
    public void reset(Node node) {
        if (state == null) {
            return;
        }
        state.set(node, 0);
    }

    /**
     * {@inheritDoc}
     *
     * @since 23.0
     */
    @Override
    public void disable(Node node) {
        if (state == null) {
            return;
        }
        state.set(node, 1);
    }

    /**
     * {@inheritDoc}
     *
     * @since 23.0
     */
    @Override
    public String toString(Node node) {
        if (state == null) {
            return toStringDisabled();
        }
        return toString(InlinedBranchProfile.class, state.get(node) == 0, false, "VISITED");
    }

    boolean isGeneric(Node node) {
        if (state == null) {
            return true;
        }
        return this.state.get(node) == 1;
    }

    boolean isUninitialized(Node node) {
        if (state == null) {
            return false;
        }
        return this.state.get(node) == 0;
    }

    /**
     * Returns an inlined version of the profile. This version is automatically used by Truffle DSL
     * node inlining.
     *
     * @since 23.0
     */
    public static InlinedBranchProfile inline(
                    @RequiredField(value = StateField.class, bits = REQUIRED_STATE_BITS) InlineTarget target) {
        if (Profile.isProfilingEnabled()) {
            return new InlinedBranchProfile(target);
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
    public static InlinedBranchProfile getUncached() {
        return DISABLED;
    }

}
