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

import static jdk.internal.jvmci.code.ValueUtil.*;

import java.util.*;

import jdk.internal.jvmci.debug.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.ssa.*;
import com.oracle.graal.lir.ssa.SSAUtils.PhiValueVisitor;

class SSALinarScanResolveDataFlowPhase extends LinearScanResolveDataFlowPhase {

    private static final DebugMetric numPhiResolutionMoves = Debug.metric("SSA LSRA[numPhiResolutionMoves]");
    private static final DebugMetric numStackToStackMoves = Debug.metric("SSA LSRA[numStackToStackMoves]");

    SSALinarScanResolveDataFlowPhase(LinearScan allocator) {
        super(allocator);
    }

    @Override
    void resolveCollectMappings(AbstractBlockBase<?> fromBlock, AbstractBlockBase<?> toBlock, AbstractBlockBase<?> midBlock, MoveResolver moveResolver) {
        super.resolveCollectMappings(fromBlock, toBlock, midBlock, moveResolver);

        if (toBlock.getPredecessorCount() > 1) {
            int toBlockFirstInstructionId = allocator.getFirstLirInstructionId(toBlock);
            int fromBlockLastInstructionId = allocator.getLastLirInstructionId(fromBlock) + 1;

            AbstractBlockBase<?> phiOutBlock = midBlock != null ? midBlock : fromBlock;
            List<LIRInstruction> instructions = allocator.ir.getLIRforBlock(phiOutBlock);
            int phiOutIdx = SSAUtils.phiOutIndex(allocator.ir, phiOutBlock);
            int phiOutId = midBlock != null ? fromBlockLastInstructionId : instructions.get(phiOutIdx).id();
            assert phiOutId >= 0;

            PhiValueVisitor visitor = new PhiValueVisitor() {

                public void visit(Value phiIn, Value phiOut) {
                    assert !isRegister(phiOut) : "phiOut is a register: " + phiOut;
                    assert !isRegister(phiIn) : "phiIn is a register: " + phiIn;
                    Interval toInterval = allocator.splitChildAtOpId(allocator.intervalFor(phiIn), toBlockFirstInstructionId, LIRInstruction.OperandMode.DEF);
                    if (isConstant(phiOut)) {
                        numPhiResolutionMoves.increment();
                        moveResolver.addMapping(phiOut, toInterval);
                    } else {
                        Interval fromInterval = allocator.splitChildAtOpId(allocator.intervalFor(phiOut), phiOutId, LIRInstruction.OperandMode.DEF);
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

            SSAUtils.forEachPhiValuePair(allocator.ir, toBlock, phiOutBlock, visitor);
            SSAUtils.removePhiOut(allocator.ir, phiOutBlock);
        }
    }

}
