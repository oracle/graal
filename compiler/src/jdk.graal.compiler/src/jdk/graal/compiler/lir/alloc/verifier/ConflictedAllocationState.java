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
import jdk.graal.compiler.util.EconomicHashSet;

import java.util.Set;

/**
 * Conflicted allocation state - two or more instances have collided, and either of them can be
 * stored at said location, needs to be resolved by either overwriting the location with a new
 * {@link ValueAllocationState instance}.
 */
public final class ConflictedAllocationState extends AllocationState {
    protected final Set<ValueAllocationState> conflictedStates;

    public ConflictedAllocationState() {
        this.conflictedStates = new EconomicHashSet<>();
    }

    @SuppressWarnings("this-escape")
    public ConflictedAllocationState(ValueAllocationState state1, ValueAllocationState state2) {
        this();
        addConflictedValue(state1);
        addConflictedValue(state2);
    }

    protected ConflictedAllocationState(Set<ValueAllocationState> conflictedStates) {
        this.conflictedStates = new EconomicHashSet<>(conflictedStates);
    }

    public void addConflictedValue(ValueAllocationState state) {
        if (hasConflictedValue(state)) {
            return;
        }

        this.conflictedStates.add(state);
    }

    public boolean hasConflictedValue(ValueAllocationState valueAllocationState) {
        return this.conflictedStates.contains(valueAllocationState);
    }

    /**
     * Get the set of all {@link ValueAllocationState} instances conflicting in this state.
     *
     * @return Set of {@link ValueAllocationState} instances
     */
    public Set<ValueAllocationState> getConflictedStates() {
        return this.conflictedStates;
    }

    @Override
    public boolean isConflicted() {
        return true;
    }

    /**
     * Adds incoming state to conflicted states, does not allocate a new state.
     *
     * @param other The other state coming from a predecessor edge
     * @return always null (see {@link AllocationStateMap#mergeWith}), to not trigger propagation
     *         through the CFG
     */
    @Override
    public AllocationState meet(AllocationState other, BasicBlock<?> otherBlock, BasicBlock<?> block) {
        if (other instanceof ValueAllocationState valueState) {
            this.addConflictedValue(valueState);
        } else if (other instanceof ConflictedAllocationState conflictedState) {
            for (var otherConfState : conflictedState.getConflictedStates()) {
                this.addConflictedValue(otherConfState);
            }
        } else if (other instanceof UnknownAllocationState) {
            /*
             * Unknown state creates an Illegal ValueAllocationState inside it, because the unknown
             * state is coming from a different predecessor to the same block, and it means that
             * this location was not defined there, but it was defined in a different predecessor
             * block, meaning it's now in a conflicted state, where it either is defined or it -
             * should not be used in further blocks.
             */
            this.addConflictedValue(ValueAllocationState.createUndefined(otherBlock));
        }

        // When conflicted, we do not want to process everything again
        return null;
    }

    @Override
    public AllocationState clone() {
        return new ConflictedAllocationState(this.conflictedStates);
    }

    /**
     * We do not compare a conflicted state on its contents, whenever a new one would be created as
     * a result, the set of contents would remain the same if values are equal based on RAValue
     * rules.
     *
     * @param other Another state we are comparing it to
     * @return Are both states conflicted?
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof ConflictedAllocationState c && c.isConflicted();
    }

    @Override
    public int hashCode() {
        return conflictedStates.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Conflicted {");
        for (var state : this.conflictedStates) {
            sb.append(state.toString()).append(", ");
        }

        if (!conflictedStates.isEmpty()) {
            sb.setLength(sb.length() - 2);
        }

        return sb.append("}").toString();
    }
}
