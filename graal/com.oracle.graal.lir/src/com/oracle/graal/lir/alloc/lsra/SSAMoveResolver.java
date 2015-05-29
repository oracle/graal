/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.lsra;

import com.oracle.jvmci.meta.Value;
import com.oracle.jvmci.meta.AllocatableValue;
import static com.oracle.jvmci.code.ValueUtil.*;

import java.util.*;

import com.oracle.graal.lir.*;
import com.oracle.graal.lir.framemap.*;

final class SSAMoveResolver extends MoveResolver {

    private int[] stackBlocked;

    SSAMoveResolver(LinearScan allocator) {
        super(allocator);
        this.stackBlocked = new int[((FrameMapBuilderTool) allocator.frameMapBuilder).getNumberOfStackSlots()];
    }

    @Override
    boolean checkEmpty() {
        for (int i = 0; i < stackBlocked.length; i++) {
            assert stackBlocked[i] == 0 : "stack map must be empty before and after processing";
        }
        return super.checkEmpty();
    }

    @Override
    protected void checkMultipleReads() {
        // multiple reads are allowed in SSA LSRA
    }

    @Override
    protected void verifyStackSlotMapping() {
        // relax disjoint stack maps invariant
    }

    @Override
    protected boolean areMultipleReadsAllowed() {
        return true;
    }

    @Override
    protected boolean mightBeBlocked(Value location) {
        if (super.mightBeBlocked(location)) {
            return true;
        }
        if (isStackSlotValue(location)) {
            return true;
        }
        return false;
    }

    @Override
    protected void setValueBlocked(Value location, int direction) {
        assert direction == 1 || direction == -1 : "out of bounds";
        if (isVirtualStackSlot(location)) {
            assert LinearScanPhase.SSA_LSRA.getValue() : "should only happen if SSA LSRA is used!";
            int stack = asVirtualStackSlot(location).getId();
            if (stack >= stackBlocked.length) {
                stackBlocked = Arrays.copyOf(stackBlocked, stack + 1);
            }
            stackBlocked[stack] += direction;
        } else if (isStackSlot(location)) {
            assert LinearScanPhase.SSA_LSRA.getValue() : "should only happen if SSA LSRA is used!";
            assert asStackSlot(location).isInCallerFrame() : "Unexpected stack slot: " + location;
            // incoming stack arguments can be ignored
        } else {
            super.setValueBlocked(location, direction);
        }
    }

    @Override
    protected int valueBlocked(Value location) {
        if (isVirtualStackSlot(location)) {
            assert LinearScanPhase.SSA_LSRA.getValue() : "should only happen if SSA LSRA is used!";
            int stack = asVirtualStackSlot(location).getId();
            if (stack >= stackBlocked.length) {
                return 0;
            }
            return stackBlocked[stack];
        }
        if (isStackSlot(location)) {
            assert LinearScanPhase.SSA_LSRA.getValue() : "should only happen if SSA LSRA is used!";
            assert asStackSlot(location).isInCallerFrame() : "Unexpected stack slot: " + location;
            // incoming stack arguments are always blocked (aka they can not be written)
            return 1;
        }
        return super.valueBlocked(location);
    }

    @Override
    protected LIRInstruction createMove(AllocatableValue fromOpr, AllocatableValue toOpr, AllocatableValue fromLocation, AllocatableValue toLocation) {
        if (isStackSlotValue(toLocation) && isStackSlotValue(fromLocation)) {
            return getAllocator().getSpillMoveFactory().createStackMove(toOpr, fromOpr);
        }
        return super.createMove(fromOpr, toOpr, fromLocation, toLocation);
    }
}
