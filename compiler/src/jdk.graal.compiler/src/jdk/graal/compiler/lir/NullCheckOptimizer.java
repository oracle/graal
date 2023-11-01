/*
 * Copyright (c) 2013, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir;

import java.util.ArrayList;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.StandardOp.ImplicitNullCheck;
import jdk.graal.compiler.lir.StandardOp.NullCheck;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.PostAllocationOptimizationPhase;

import jdk.vm.ci.code.TargetDescription;

public final class NullCheckOptimizer extends PostAllocationOptimizationPhase {

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
        LIR ir = lirGenRes.getLIR();
        int[] blockIndices = ir.getBlocks();
        NullCheckOptimizer.foldNullChecks(ir, blockIndices, target.implicitNullCheckLimit);
    }

    private static void foldNullChecks(LIR ir, int[] blockIds, int implicitNullCheckLimit) {
        for (int blockId : blockIds) {
            if (LIR.isBlockDeleted(blockId)) {
                continue;
            }
            BasicBlock<?> block = ir.getBlockById(blockId);
            ArrayList<LIRInstruction> list = ir.getLIRforBlock(block);
            if (!list.isEmpty()) {
                LIRInstruction lastInstruction = list.get(0);
                for (int i = 0; i < list.size(); i++) {
                    LIRInstruction instruction = list.get(i);

                    if (instruction instanceof ImplicitNullCheck && lastInstruction instanceof NullCheck) {
                        NullCheck nullCheck = (NullCheck) lastInstruction;
                        ImplicitNullCheck implicitNullCheck = (ImplicitNullCheck) instruction;
                        if (implicitNullCheck.makeNullCheckFor(nullCheck.getCheckedValue(), nullCheck.getState(), implicitNullCheckLimit)) {
                            list.remove(i - 1);
                            if (i < list.size()) {
                                instruction = list.get(i);
                            }
                        }
                    }
                    lastInstruction = instruction;
                }
            }
        }
    }

}
