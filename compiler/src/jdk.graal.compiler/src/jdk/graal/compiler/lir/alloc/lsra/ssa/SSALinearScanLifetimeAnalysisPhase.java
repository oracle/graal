/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.EnumSet;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstruction.OperandFlag;
import jdk.graal.compiler.lir.LIRInstruction.OperandMode;
import jdk.graal.compiler.lir.StandardOp;
import jdk.graal.compiler.lir.StandardOp.LabelOp;
import jdk.graal.compiler.lir.alloc.lsra.Interval;
import jdk.graal.compiler.lir.alloc.lsra.Interval.RegisterPriority;
import jdk.graal.compiler.lir.alloc.lsra.LinearScan;
import jdk.graal.compiler.lir.alloc.lsra.LinearScanLifetimeAnalysisPhase;
import jdk.graal.compiler.lir.alloc.lsra.SparseBitSet;
import jdk.graal.compiler.lir.ssa.SSAUtil;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.Value;

public class SSALinearScanLifetimeAnalysisPhase extends LinearScanLifetimeAnalysisPhase {

    SSALinearScanLifetimeAnalysisPhase(LinearScan linearScan) {
        super(linearScan);
    }

    @Override
    protected void addRegisterHint(final LIRInstruction op, final Value targetValue, OperandMode mode, EnumSet<OperandFlag> flags, final boolean hintAtDef) {
        super.addRegisterHint(op, targetValue, mode, flags, hintAtDef);

        if (hintAtDef && op instanceof LabelOp) {
            LabelOp label = (LabelOp) op;
            if (!label.isPhiIn()) {
                return;
            }

            Interval to = allocator.getOrCreateInterval((AllocatableValue) targetValue);

            LIR lir = allocator.getLIR();
            BasicBlock<?> block = allocator.blockForId(label.id());
            assert mode == OperandMode.DEF : "Wrong operand mode: " + mode;
            assert lir.getLIRforBlock(block).get(0).equals(label) : String.format("Block %s and Label %s do not match!", block, label);

            int idx = SSAUtil.indexOfValue(label, targetValue);
            assert idx >= 0 : String.format("Value %s not in label %s", targetValue, label);

            SparseBitSet blockLiveIn = allocator.getBlockData(block).liveIn;

            BasicBlock<?> selectedPredecessor = null;
            AllocatableValue selectedSource = null;
            for (int i = 0; i < block.getPredecessorCount(); i++) {
                BasicBlock<?> pred = block.getPredecessorAt(i);
                if (selectedPredecessor == null || pred.getRelativeFrequency() > selectedPredecessor.getRelativeFrequency()) {
                    StandardOp.JumpOp jump = SSAUtil.phiOut(lir, pred);
                    Value sourceValue = jump.getOutgoingValue(idx);
                    if (LinearScan.isVariableOrRegister(sourceValue) && !blockLiveIn.get(getOperandNumber(sourceValue))) {
                        selectedSource = (AllocatableValue) sourceValue;
                        selectedPredecessor = pred;
                    }
                }
            }
            if (selectedSource != null) {
                Interval from = allocator.getOrCreateInterval(selectedSource);
                setHint(debug, op, to, from);
                setHint(debug, op, from, to);
            }
        }
    }

    public static void setHint(DebugContext debug, final LIRInstruction op, Interval target, Interval source) {
        Interval currentHint = target.locationHint(false);
        if (currentHint == null || currentHint.from() > target.from()) {
            /*
             * Update hint if there was none or if the hint interval starts after the hinted
             * interval.
             */
            target.setLocationHint(source);
            if (debug.isLogEnabled()) {
                debug.log("operation at opId %d: added hint from interval %d to %d", op.id(), source.operandNumber, target.operandNumber);
            }
        }
    }

    @Override
    protected RegisterPriority registerPriorityOfOutputOperand(LIRInstruction op) {
        if (op instanceof LabelOp) {
            LabelOp label = (LabelOp) op;
            if (label.isPhiIn()) {
                return RegisterPriority.None;
            }
        }
        return super.registerPriorityOfOutputOperand(op);
    }
}
