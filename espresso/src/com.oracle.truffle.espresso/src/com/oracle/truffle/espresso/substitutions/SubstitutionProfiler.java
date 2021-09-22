/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package com.oracle.truffle.espresso.substitutions;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.espresso.meta.EspressoError;

public class SubstitutionProfiler extends Node {

    @CompilationFinal //
    private long profiles = 0;

    /**
     * Profiles whether a branch was hit or not. Current implementation only allows 16 branches per
     * substitution.
     */
    public final void profile(int branch) {
        assert branch < 64;
        if ((profiles << branch) == 0) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            profiles |= (1 << branch);
        }
    }

    /**
     * Should return true if the substitution uses profiles. This will allow to spawn a profile for
     * every call site.
     */
    public boolean shouldSplit() {
        return false;
    }

    /**
     * Spawns a new Substitution with uninitialized profiles.
     */
    public SubstitutionProfiler split() {
        throw EspressoError.shouldNotReachHere();
    }

    public boolean uninitialized() {
        return profiles == 0;
    }

    public boolean isTrivial() {
        return false;
    }
}
