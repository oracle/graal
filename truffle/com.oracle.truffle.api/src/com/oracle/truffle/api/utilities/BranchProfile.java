/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.utilities;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.NodeCloneable;

/**
 * @deprecated use {@link com.oracle.truffle.api.profiles.BranchProfile} instead
 * @since 0.8 or earlier
 */
@Deprecated
public final class BranchProfile extends NodeCloneable {

    @CompilationFinal private boolean visited;

    private BranchProfile() {
    }

    /** @since 0.8 or earlier */
    public void enter() {
        if (!visited) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            visited = true;
        }
    }

    /** @since 0.8 or earlier */
    public boolean isVisited() {
        return visited;
    }

    /**
     * @deprecated use {@link com.oracle.truffle.api.profiles.BranchProfile#create()} instead
     * @since 0.8 or earlier
     */
    @Deprecated
    public static BranchProfile create() {
        return new BranchProfile();
    }

    /** @since 0.8 or earlier */
    @Override
    public String toString() {
        return String.format("%s(%s)@%x", getClass().getSimpleName(), visited ? "visited" : "not-visited", hashCode());
    }
}
