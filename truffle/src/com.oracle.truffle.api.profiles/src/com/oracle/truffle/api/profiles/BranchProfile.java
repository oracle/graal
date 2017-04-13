/*
 * Copyright (c) 2013, 2015, Oracle and/or its affiliates. All rights reserved.
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
public abstract class BranchProfile extends Profile {

    BranchProfile() {
    }

    /**
     * Call when an unlikely branch is entered.
     * 
     * @since 0.10
     */
    public abstract void enter();

    /**
     * @deprecated it is not reliable when profiling is turned off.
     * @since 0.10
     */
    @Deprecated
    public abstract boolean isVisited();

    /**
     * Call to create a new instance of a branch profile.
     * 
     * @since 0.10
     */
    public static BranchProfile create() {
        if (Profile.isProfilingEnabled()) {
            return Enabled.create0();
        } else {
            return Disabled.INSTANCE;
        }
    }

    static final class Enabled extends BranchProfile {

        @CompilationFinal private boolean visited;

        @Override
        public void enter() {
            if (!visited) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                visited = true;
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean isVisited() {
            return visited;
        }

        @Override
        public String toString() {
            return toString(BranchProfile.class, !visited, false, "VISITED");
        }

        /* Needed for lazy class loading. */
        static BranchProfile create0() {
            return new Enabled();
        }
    }

    static final class Disabled extends BranchProfile {

        static final BranchProfile INSTANCE = new Disabled();

        @Override
        protected Object clone() {
            return INSTANCE;
        }

        @Override
        public void enter() {
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean isVisited() {
            return true;
        }

        @Override
        public String toString() {
            return toStringDisabled(BranchProfile.class);
        }

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
