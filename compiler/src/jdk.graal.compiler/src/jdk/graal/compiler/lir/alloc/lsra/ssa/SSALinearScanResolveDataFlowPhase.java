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

import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.graal.compiler.lir.LIRValueUtil.asConstant;
import static jdk.graal.compiler.lir.LIRValueUtil.isConstantValue;
import static jdk.graal.compiler.lir.LIRValueUtil.isStackSlotValue;

import java.util.ArrayList;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.alloc.lsra.Interval;
import jdk.graal.compiler.lir.alloc.lsra.LinearScan;
import jdk.graal.compiler.lir.alloc.lsra.LinearScanResolveDataFlowPhase;
import jdk.graal.compiler.lir.alloc.lsra.MoveResolver;
import jdk.graal.compiler.lir.ssa.SSAUtil;
import jdk.graal.compiler.lir.ssa.SSAUtil.PhiValueVisitor;

import jdk.vm.ci.meta.Value;

class SSALinearScanResolveDataFlowPhase extends LinearScanResolveDataFlowPhase {

    private static final CounterKey numPhiResolutionMoves = DebugContext.counter("SSA LSRA[numPhiResolutionMoves]");
    private static final CounterKey numStackToStackMoves = DebugContext.counter("SSA LSRA[numStackToStackMoves]");

    SSALinearScanResolveDataFlowPhase(LinearScan allocator) {
        super(allocator);
    }

    @Override
    protected void resolveCollectMappings(BasicBlock<?> fromBlock, BasicBlock<?> toBlock, BasicBlock<?> midBlock, MoveResolver moveResolver) {
        super.resolveCollectMappings(fromBlock, toBlock, midBlock, moveResolver);

        if (toBlock.getPredecessorCount() > 1) {
            int toBlockFirstInstructionId = allocator.getFirstLirInstructionId(toBlock);
            int fromBlockLastInstructionId = allocator.getLastLirInstructionId(fromBlock) + 1;

            BasicBlock<?> phiOutBlock = midBlock != null ? midBlock : fromBlock;
            ArrayList<LIRInstruction> instructions = allocator.getLIR().getLIRforBlock(phiOutBlock);
            int phiOutIdx = SSAUtil.phiOutIndex(allocator.getLIR(), phiOutBlock);
            int phiOutId = midBlock != null ? fromBlockLastInstructionId : instructions.get(phiOutIdx).id();
            assert phiOutId >= 0 : phiOutId;

            PhiValueVisitor visitor = new PhiValueVisitor() {

                @Override
                public void visit(Value phiIn, Value phiOut) {
                    assert !isRegister(phiOut) : "phiOut is a register: " + phiOut;
                    assert !isRegister(phiIn) : "phiIn is a register: " + phiIn;
                    Interval toInterval = allocator.splitChildAtOpId(allocator.intervalFor(phiIn), toBlockFirstInstructionId, LIRInstruction.OperandMode.DEF);
                    DebugContext debug = allocator.getDebug();
                    if (isConstantValue(phiOut)) {
                        numPhiResolutionMoves.increment(debug);
                        moveResolver.addMapping(asConstant(phiOut), toInterval);
                    } else {
                        Interval fromInterval = allocator.splitChildAtOpId(allocator.intervalFor(phiOut), phiOutId, LIRInstruction.OperandMode.DEF);
                        if (fromInterval != toInterval && !fromInterval.location().equals(toInterval.location())) {
                            numPhiResolutionMoves.increment(debug);
                            if (!(isStackSlotValue(toInterval.location()) && isStackSlotValue(fromInterval.location()))) {
                                moveResolver.addMapping(fromInterval, toInterval);
                            } else {
                                numStackToStackMoves.increment(debug);
                                moveResolver.addMapping(fromInterval, toInterval);
                            }
                        }
                    }
                }
            };

            SSAUtil.forEachPhiValuePair(allocator.getLIR(), toBlock, phiOutBlock, visitor);
            SSAUtil.removePhiOut(allocator.getLIR(), phiOutBlock);
        }
    }

}
