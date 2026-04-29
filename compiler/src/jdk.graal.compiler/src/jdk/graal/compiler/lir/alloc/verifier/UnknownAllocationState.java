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
 * Default allocation state for all locations, nothing was yet inserted.
 */
public class UnknownAllocationState extends AllocationState {
    /**
     * Single instance used for all occurrences of {@link UnknownAllocationState unknown state}.
     */
    public static UnknownAllocationState INSTANCE = new UnknownAllocationState();

    @Override
    public boolean isUnknown() {
        return true;
    }

    /**
     * Meet state from a predecessor, if both are unknown then unknown is returned, otherwise
     * {@link ConflictedAllocationState conflict} occurs.
     *
     * @param other The other state coming from a predecessor edge
     * @return null, if both states are Unknown, otherwise a new {@link ConflictedAllocationState}
     */
    @Override
    public AllocationState meet(AllocationState other, BasicBlock<?> otherBlock, BasicBlock<?> block) {
        if (other.isUnknown()) {
            return null;
        }

        if (other instanceof ConflictedAllocationState conflictedState) {
            var newConfState = new ConflictedAllocationState(conflictedState.conflictedStates);
            newConfState.addConflictedValue(ValueAllocationState.createUndefined(block));
            return newConfState;
        }

        return new ConflictedAllocationState((ValueAllocationState) other, ValueAllocationState.createUndefined(block));
    }

    @Override
    public AllocationState clone() {
        return INSTANCE;
    }

    @Override
    public boolean equals(Object other) {
        return other == INSTANCE;
    }

    @Override
    public int hashCode() {
        return 0;
    }

    @Override
    public String toString() {
        return "Unknown";
    }
}
