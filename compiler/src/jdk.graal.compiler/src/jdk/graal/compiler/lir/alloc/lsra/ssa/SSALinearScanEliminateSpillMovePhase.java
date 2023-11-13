/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.alloc.lsra.ssa;

import static jdk.graal.compiler.lir.LIRValueUtil.isStackSlotValue;
import static jdk.vm.ci.code.ValueUtil.isRegister;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.StandardOp.LabelOp;
import jdk.graal.compiler.lir.StandardOp.MoveOp;
import jdk.graal.compiler.lir.alloc.lsra.Interval;
import jdk.graal.compiler.lir.alloc.lsra.LinearScan;
import jdk.graal.compiler.lir.alloc.lsra.LinearScanEliminateSpillMovePhase;

public class SSALinearScanEliminateSpillMovePhase extends LinearScanEliminateSpillMovePhase {

    SSALinearScanEliminateSpillMovePhase(LinearScan allocator) {
        super(allocator);
    }

    @Override
    protected int firstInstructionOfInterest() {
        // also look at Labels as they define PHI values
        return 0;
    }

    @Override
    protected boolean canEliminateSpillMove(BasicBlock<?> block, MoveOp move) {
        if (super.canEliminateSpillMove(block, move)) {
            // SSA Linear Scan might introduce moves to stack slots
            Interval curInterval = allocator.intervalFor(move.getResult());
            assert !isRegister(curInterval.location()) && curInterval.alwaysInMemory() : Assertions.errorMessage(curInterval);
            if (!isPhiResolutionMove(block, move, curInterval)) {
                assert isStackSlotValue(curInterval.location()) : "Not a stack slot: " + curInterval.location();
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("try")
    private boolean isPhiResolutionMove(BasicBlock<?> block, MoveOp move, Interval toInterval) {
        if (!toInterval.isSplitParent()) {
            return false;
        }
        if ((toInterval.from() & 1) == 1) {
            // phi intervals start at even positions.
            return false;
        }
        if (block.getSuccessorCount() != 1) {
            return false;
        }
        LIRInstruction op = allocator.instructionForId(toInterval.from());
        if (!(op instanceof LabelOp)) {
            return false;
        }
        BasicBlock<?> intStartBlock = allocator.blockForId(toInterval.from());
        assert allocator.getLIR().getLIRforBlock(intStartBlock).get(0).equals(op);
        if (!block.getSuccessorAt(0).equals(intStartBlock)) {
            return false;
        }
        DebugContext debug = allocator.getDebug();
        try (Indent indent = debug.indent()) {
            debug.log("Is a move (%s) to phi interval %s", move, toInterval);
        }
        return true;
    }
}
