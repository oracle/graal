/*
 * Copyright (c) 2018, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.lir;

import static jdk.vm.ci.code.ValueUtil.isRegister;

import java.util.EnumSet;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRInstruction.OperandFlag;
import jdk.graal.compiler.lir.LIRInstruction.OperandMode;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.FinalCodeAnalysisPhase;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.Value;

/**
 * Checks that no registers exceed the MaxVectorSize flag from the VM config.
 */
public final class VerifyMaxRegisterSizePhase extends FinalCodeAnalysisPhase {

    private final int maxVectorSize;

    public VerifyMaxRegisterSizePhase(int maxVectorSize) {
        this.maxVectorSize = maxVectorSize;
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, FinalCodeAnalysisContext context) {
        LIR lir = lirGenRes.getLIR();
        for (BasicBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
            verifyBlock(lir, block);
        }
    }

    protected void verifyBlock(LIR lir, BasicBlock<?> block) {
        for (LIRInstruction inst : lir.getLIRforBlock(block)) {
            verifyInstruction(inst);
        }
    }

    protected void verifyInstruction(LIRInstruction inst) {
        inst.visitEachInput(this::verifyOperands);
        inst.visitEachOutput(this::verifyOperands);
        inst.visitEachAlive(this::verifyOperands);
        inst.visitEachTemp(this::verifyOperands);
    }

    @SuppressWarnings("unused")
    protected void verifyOperands(LIRInstruction instruction, Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
        // Ensure that vector registers are never larger than the current vector size
        if (isRegister(value) && value.getPlatformKind().getVectorLength() > 1) {
            assert value.getPlatformKind().getSizeInBytes() <= maxVectorSize : "value " + value + " exceeds MaxVectorSize " + maxVectorSize + " at " + instruction;
        }
    }
}
