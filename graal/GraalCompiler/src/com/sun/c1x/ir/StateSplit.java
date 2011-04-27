/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.ir;

import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code StateSplit} class is the abstract base class of all instructions
 * that store an immutable copy of the frame state.
 *
 * @author Ben L. Titzer
 */
public abstract class StateSplit extends Instruction {

    /**
     * Sentinel denoting an explicitly cleared state.
     */
    private static final FrameState CLEARED_STATE = new MutableFrameState(null, -5, 0, 0);

    private FrameState stateBefore;

    /**
     * Creates a new state split with the specified value type.
     * @param kind the type of the value that this instruction produces
     */
    public StateSplit(CiKind kind, FrameState stateBefore) {
        super(kind);
        this.stateBefore = stateBefore;
    }

    /**
     * Determines if the state for this instruction has explicitly
     * been cleared (as opposed to never initialized). Once explicitly
     * cleared, an instruction must not have it state (re)set.
     */
    public boolean isStateCleared() {
        return stateBefore == CLEARED_STATE;
    }

    /**
     * Clears the state for this instruction. Once explicitly
     * cleared, an instruction must not have it state (re)set.
     */
    protected void clearState() {
        stateBefore = CLEARED_STATE;
    }

    /**
     * Records the state of this instruction before it is executed.
     *
     * @param stateBefore the state
     */
    public final void setStateBefore(FrameState stateBefore) {
        assert this.stateBefore == null;
        this.stateBefore = stateBefore;
    }

    /**
     * Gets the state for this instruction.
     * @return the state
     */
    @Override
    public final FrameState stateBefore() {
        return stateBefore == CLEARED_STATE ? null : stateBefore;
    }
}
