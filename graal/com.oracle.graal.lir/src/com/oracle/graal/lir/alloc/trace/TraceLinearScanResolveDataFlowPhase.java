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
package com.oracle.graal.lir.alloc.trace;

import static jdk.internal.jvmci.code.ValueUtil.*;

import java.util.*;

import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.alloc.lsra.*;
import com.oracle.graal.lir.ssa.SSAUtil.PhiValueVisitor;
import com.oracle.graal.lir.ssi.*;

public class TraceLinearScanResolveDataFlowPhase extends LinearScanResolveDataFlowPhase {

    private static final DebugMetric numSSIResolutionMoves = Debug.metric("SSI LSRA[numSSIResolutionMoves]");
    private static final DebugMetric numStackToStackMoves = Debug.metric("SSI LSRA[numStackToStackMoves]");

    public TraceLinearScanResolveDataFlowPhase(LinearScan allocator) {
        super(allocator);
    }

    @Override
    protected void optimizeEmptyBlocks(MoveResolver moveResolver, BitSet blockCompleted) {
        // do not optimize
    }

    @Override
    protected void resolveCollectMappings(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> midBlock, MoveResolver moveResolver) {
        assert midBlock == null;
        if (containedInTrace(fromBlock) && containedInTrace(toBlock)) {
            super.resolveCollectMappings(fromBlock, toBlock, midBlock, moveResolver);
            SSIUtils.forEachValuePair(allocator.getLIR(), toBlock, fromBlock, new MyPhiValueVisitor(moveResolver, toBlock, fromBlock));
        }

    }

    private boolean containedInTrace(AbstractBlockBase<?> block) {
        return allocator.sortedBlocks().contains(block);
    }

    private class MyPhiValueVisitor implements PhiValueVisitor {
        final MoveResolver moveResolver;
        final int toId;
        final int fromId;

        public MyPhiValueVisitor(MoveResolver moveResolver, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> fromBlock) {
            this.moveResolver = moveResolver;
            toId = allocator.getFirstLirInstructionId(toBlock);
            fromId = allocator.getLastLirInstructionId(fromBlock);
            assert fromId >= 0;
        }

        public void visit(Value phiIn, Value phiOut) {
            assert !isRegister(phiOut) : "Out is a register: " + phiOut;
            assert !isRegister(phiIn) : "In is a register: " + phiIn;
            if (Value.ILLEGAL.equals(phiIn)) {
                // The value not needed in this branch.
                return;
            }
            if (isVirtualStackSlot(phiIn) && isVirtualStackSlot(phiOut) && phiIn.equals(phiOut)) {
                // no need to handle virtual stack slots
                return;
            }
            Interval toInterval = allocator.splitChildAtOpId(allocator.intervalFor(phiIn), toId, LIRInstruction.OperandMode.DEF);
            if (isConstant(phiOut)) {
                numSSIResolutionMoves.increment();
                moveResolver.addMapping(phiOut, toInterval);
            } else {
                Interval fromInterval = allocator.splitChildAtOpId(allocator.intervalFor(phiOut), fromId, LIRInstruction.OperandMode.DEF);
                if (fromInterval != toInterval) {
                    numSSIResolutionMoves.increment();
                    if (!(isStackSlotValue(toInterval.location()) && isStackSlotValue(fromInterval.location()))) {
                        moveResolver.addMapping(fromInterval, toInterval);
                    } else {
                        numStackToStackMoves.increment();
                        moveResolver.addMapping(fromInterval, toInterval);
                    }
                }
            }
        }
    }

}
