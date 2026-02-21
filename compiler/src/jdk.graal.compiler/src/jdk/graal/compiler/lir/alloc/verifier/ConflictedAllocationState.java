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

import jdk.graal.compiler.util.EconomicHashSet;

import java.util.Arrays;
import java.util.Set;

/**
 * Conflicted allocation state - two or more ValueAllocationState instances
 * have collided and either of them can be stored at said location, needs
 * to be resolved by either overwriting the location with a new ValueAllocationState instance
 * or by a ConflictResolver implementation.
 */
public class ConflictedAllocationState extends AllocationState {
    protected Set<ValueAllocationState> conflictedStates;

    public ConflictedAllocationState() {
        this.conflictedStates = new EconomicHashSet<>();
    }

    public ConflictedAllocationState(ValueAllocationState state1, ValueAllocationState state2) {
        this();
        this.conflictedStates.add(state1); // Not using addConflictedValue because a warning is thrown
        this.conflictedStates.add(state2);
    }

    protected ConflictedAllocationState(Set<ValueAllocationState> conflictedStates) {
        this.conflictedStates = new EconomicHashSet<>(conflictedStates);
    }

    public void addConflictedValue(ValueAllocationState state) {
        this.conflictedStates.add(state);
    }

    /**
     * Get the set of all ValueAllocationState instances conflicting in this state.
     *
     * @return Set of ValueAllocationState instances
     */
    public Set<ValueAllocationState> getConflictedStates() {
        return this.conflictedStates;
    }

    @Override
    public boolean isConflicted() {
        return true;
    }

    /**
     * Any state coming here will be added to the conflict set
     * and create a new ConflictedAllocationState instance.
     *
     * @param other Other state coming from a predecessor edge
     * @return ConflictedAllocationState with predecessor state added up
     */
    @Override
    public AllocationState meet(AllocationState other) {
        var newlyConflictedState = new ConflictedAllocationState(this.getConflictedStates());
        if (other instanceof ValueAllocationState valueState) {
            newlyConflictedState.addConflictedValue(valueState);
        }

        if (other instanceof ConflictedAllocationState conflictedState) {
            newlyConflictedState.conflictedStates.addAll(conflictedState.conflictedStates);
        }

        if (other instanceof UnknownAllocationState) {
            // Unknown state creates an Illegal ValueAllocationState inside it, because
            // the unknown state is coming from a different predecessor to the same block,
            // and it means that this location was not defined there, but it was defined in a
            // different predecessor block, meaning it's now in a conflicted state, where
            // it either is defined or it is not - should not be used in further blocks.
            newlyConflictedState.conflictedStates.add(ValueAllocationState.createIllegal());
        }

        return newlyConflictedState;
    }

    @Override
    public AllocationState clone() {
        return new ConflictedAllocationState(this.conflictedStates);
    }

    /**
     * We do not compare conflicted state on its contents,
     * whenever new one would be created as a result, the
     * set of contents would remain the same, if values
     * are equal based on RAValue rules.
     *
     * @param other Other state we are comparing it to
     * @return Are both states conflicted?
     */
    @Override
    public boolean equals(AllocationState other) {
        return other.isConflicted();
    }

    @Override
    public String toString() {
        return "Conflicted {" + Arrays.toString(this.conflictedStates.toArray()) + "}";
    }
}
