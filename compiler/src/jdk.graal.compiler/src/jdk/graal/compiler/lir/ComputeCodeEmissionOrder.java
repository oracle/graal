/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.GraalOptions.LoopHeaderAlignment;

import java.util.ArrayList;

import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.CodeEmissionOrder.ComputationTime;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.PostAllocationOptimizationPhase;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;

import jdk.vm.ci.code.TargetDescription;

/**
 * Computes the LIR code emitting order. This phase should be scheduled after any phases that modify
 * the control flow.
 */
public final class ComputeCodeEmissionOrder extends PostAllocationOptimizationPhase {

    public static class Options {
        // @formatter:off
        @Option(help = "Enable early code emission order computation instead of late code emission order computation")
        public static final OptionKey<Boolean> EarlyCodeEmissionOrder = new OptionKey<>(false);
        // @formatter:on
    }

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
        LIR lir = lirGenRes.getLIR();
        int[] layout = context.blockOrder.computeCodeEmittingOrder(lir.getOptions(), ComputationTime.AFTER_CONTROL_FLOW_OPTIMIZATIONS);
        assert LIR.verifyBlocks(lir, layout) : "Block layout is not correct " + layout;
        lir.setCodeEmittingOrder(layout);
        for (int blockId : layout) {
            BasicBlock<?> block = lir.getBlockById(blockId);
            if (block.isAligned()) {
                ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
                assert instructions.get(0) instanceof StandardOp.LabelOp : "first instruction must always be a label";
                StandardOp.LabelOp label = (StandardOp.LabelOp) instructions.get(0);
                label.setAlignment(LoopHeaderAlignment.getValue(lir.getOptions()));
            }
        }
    }
}
