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

import static com.oracle.graal.api.code.ValueUtil.*;
import static com.oracle.graal.lir.LIRValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.alloc.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.gen.LIRGeneratorTool.SpillMoveFactory;
import com.oracle.graal.lir.ssa.*;
import com.oracle.graal.lir.ssa.SSAUtils.PhiValueVisitor;

final class SSALinearScan extends LinearScan {

    private static final DebugMetric numPhiResolutionMoves = Debug.metric("SSA LSRA[numPhiResolutionMoves]");
    private static final DebugMetric numStackToStackMoves = Debug.metric("SSA LSRA[numStackToStackMoves]");

    SSALinearScan(TargetDescription target, LIRGenerationResult res, SpillMoveFactory spillMoveFactory, RegisterAllocationConfig regAllocConfig) {
        super(target, res, spillMoveFactory, regAllocConfig);
    }

    @Override
    protected MoveResolver createMoveResolver() {
        SSAMoveResolver moveResolver = new SSAMoveResolver(this);
        assert moveResolver.checkEmpty();
        return moveResolver;
    }

    @Override
    protected int firstInstructionOfInterest() {
        // also look at Labels as they define PHI values
        return 0;
    }

    @Override
    void resolveCollectMappings(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> midBlock, MoveResolver moveResolver) {
        super.resolveCollectMappings(fromBlock, toBlock, midBlock, moveResolver);

        if (toBlock.getPredecessorCount() > 1) {
            int toBlockFirstInstructionId = getFirstLirInstructionId(toBlock);
            int fromBlockLastInstructionId = getLastLirInstructionId(fromBlock) + 1;

            AbstractBlockBase<?> phiOutBlock = midBlock != null ? midBlock : fromBlock;
            List<LIRInstruction> instructions = ir.getLIRforBlock(phiOutBlock);
            int phiOutIdx = SSAUtils.phiOutIndex(ir, phiOutBlock);
            int phiOutId = midBlock != null ? fromBlockLastInstructionId : instructions.get(phiOutIdx).id();
            assert phiOutId >= 0;

            PhiValueVisitor visitor = new PhiValueVisitor() {

                public void visit(Value phiIn, Value phiOut) {
                    Interval toInterval = splitChildAtOpId(intervalFor(phiIn), toBlockFirstInstructionId, LIRInstruction.OperandMode.DEF);
                    if (isConstant(phiOut)) {
                        numPhiResolutionMoves.increment();
                        moveResolver.addMapping(phiOut, toInterval);
                    } else {
                        Interval fromInterval = splitChildAtOpId(intervalFor(phiOut), phiOutId, LIRInstruction.OperandMode.DEF);
                        if (fromInterval != toInterval && !fromInterval.location().equals(toInterval.location())) {
                            numPhiResolutionMoves.increment();
                            if (!(isStackSlotValue(toInterval.location()) && isStackSlotValue(fromInterval.location()))) {
                                moveResolver.addMapping(fromInterval, toInterval);
                            } else {
                                numStackToStackMoves.increment();
                                moveResolver.addMapping(fromInterval, toInterval);
                            }
                        }
                    }
                }
            };

            SSAUtils.forEachPhiValuePair(ir, toBlock, phiOutBlock, visitor);
            SSAUtils.removePhiOut(ir, phiOutBlock);
        }
    }

    @Override
    protected void beforeSpillMoveElimination() {
        /*
         * PHI Ins are needed for the RegisterVerifier, otherwise PHIs where the Out and In value
         * matches (ie. there is no resolution move) are falsely detected as errors.
         */
        try (Scope s1 = Debug.scope("Remove Phi In")) {
            for (AbstractBlockBase<?> toBlock : sortedBlocks) {
                if (toBlock.getPredecessorCount() > 1) {
                    SSAUtils.removePhiIn(ir, toBlock);
                }
            }
        }
    }

    @Override
    protected boolean canEliminateSpillMove(AbstractBlockBase<?> block, MoveOp move) {
        // SSA Linear Scan might introduce moves to stack slots
        assert isVariable(move.getResult()) || LinearScanPhase.SSA_LSRA.getValue() : "Move should not be produced in a non-SSA compilation: " + move;

        Interval curInterval = intervalFor(move.getResult());

        if (!isRegister(curInterval.location()) && curInterval.alwaysInMemory() && !isPhiResolutionMove(block, move, curInterval)) {
            assert isStackSlotValue(curInterval.location()) : "Not a stack slot: " + curInterval.location();
            return true;
        }
        return false;
    }

    @Override
    protected LifetimeAnalysis createLifetimeAnalysisPhase() {
        return new SSALifetimeAnalysis(this);
    }

    private boolean isPhiResolutionMove(AbstractBlockBase<?> block, MoveOp move, Interval toInterval) {
        if (!LinearScanPhase.SSA_LSRA.getValue()) {
            return false;
        }
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
        LIRInstruction op = instructionForId(toInterval.from());
        if (!(op instanceof LabelOp)) {
            return false;
        }
        AbstractBlockBase<?> intStartBlock = blockForId(toInterval.from());
        assert ir.getLIRforBlock(intStartBlock).get(0).equals(op);
        if (!block.getSuccessors().get(0).equals(intStartBlock)) {
            return false;
        }
        try (Indent indet = Debug.indent()) {
            Debug.log("Is a move (%s) to phi interval %s", move, toInterval);
        }
        return true;
    }
}
