/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.nodes.Node;

/**
 * <p>
 * BranchProfiles are profiles to speculate on branches that are unlikely to be visited. If the
 * {@link #enter()} method is invoked first the optimized code is invalidated and the branch where
 * {@link #enter()} is invoked is enabled for compilation. Otherwise if the {@link #enter()} method
 * was never invoked the branch will not get compiled.
 * </p>
 *
 * <p>
 * <b> Usage example: </b>
 * {@link com.oracle.truffle.api.profiles.BranchProfileSnippets.BranchingNode#errorProfile}
 *
 * {@inheritDoc}
 *
 * @see BranchProfile#enter()
 * @since 0.10
 */
public final class BranchProfile extends Profile {

    private static final BranchProfile DISABLED;
    static {
        BranchProfile profile = new BranchProfile();
        profile.disable();
        DISABLED = profile;
    }

    @CompilationFinal private boolean visited;

    BranchProfile() {
    }

    /**
     * Call when an unlikely branch is entered.
     *
     * @since 0.10
     */
    public void enter() {
        if (!visited) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            visited = true;
        }
    }

    /**
     * Call to create a new instance of a branch profile.
     *
     * @since 0.10
     */
    public static BranchProfile create() {
        if (Profile.isProfilingEnabled()) {
            return new BranchProfile();
        } else {
            return getUncached();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 22.1
     */
    @Override
    public void disable() {
        visited = true;
    }

    /**
     * {@inheritDoc}
     *
     * @since 22.1
     */
    @Override
    public void reset() {
        /*
         * Make sure disabled branch profile can never be reenabled.
         */
        if (this != DISABLED) {
            visited = false;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @since 22.1
     */
    @Override
    public String toString() {
        if (this == DISABLED) {
            return toStringDisabled(BranchProfile.class);
        } else {
            return toString(BranchProfile.class, !visited, false, "VISITED");
        }
    }

    /**
     * Returns the uncached version of the profile. The uncached version of a profile does nothing.
     *
     * @since 19.0
     */
    public static BranchProfile getUncached() {
        return DISABLED;
    }

}

class BranchProfileSnippets {
    // BEGIN: com.oracle.truffle.api.profiles.BranchProfileSnippets.BranchingNode#errorProfile
    class BranchingNode extends Node {
        final BranchProfile errorProfile = BranchProfile.create();

        int execute(int value) {
            if (value == Integer.MAX_VALUE) {
                errorProfile.enter();
                throw new Error("Invalid input value");
            }
            return value;
        }
    }
    // END: com.oracle.truffle.api.profiles.BranchProfileSnippets.BranchingNode#errorProfile
}
