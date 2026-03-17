/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.alloc.verifier;

import jdk.graal.compiler.core.common.cfg.BasicBlock;

/**
 * Interface for state concrete location is in,
 * stored in {@link AllocationStateMap}.
 */
public abstract class AllocationState {
    /**
     * Get the default allocation state for every location,
     * instead of null, we have {@link UnknownAllocationState unknown} state.
     *
     * @return Default state for every location
     */
    public static AllocationState getDefault() {
        return UnknownAllocationState.INSTANCE;
    }

    /**
     * Shortcut to check if state is {@link UnknownAllocationState unknown}.
     *
     * @return Is {@link UnknownAllocationState unknown state}
     */
    public boolean isUnknown() {
        return false;
    }

    /**
     * Shortcut to check if state is {@link ConflictedAllocationState conflicted}.
     *
     * @return Is {@link ConflictedAllocationState conflicted}
     */
    public boolean isConflicted() {
        return false;
    }

    /**
     * Create a copy of this state, necessary
     * for state copies made over program graph edges.
     *
     * @return Newly copied state
     */
    public abstract AllocationState clone();

    /**
     * Meet a state from different block coming from edge in
     * the program graph, decide what result of said two states
     * should be.
     *
     * @param other      Other state coming from a predecessor edge
     * @param otherBlock Which block is other state from
     * @param block      Which state is this state from
     * @return What is the new state the location is in.
     */
    public abstract AllocationState meet(AllocationState other, BasicBlock<?> otherBlock, BasicBlock<?> block);

    public abstract boolean equals(AllocationState other);
}
