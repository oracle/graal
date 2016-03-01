/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.profiling;

import java.util.List;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInsertionBuffer;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.gen.BenchmarkCounterFactory;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase;

public class MethodProfilingPhase extends PostAllocationOptimizationPhase {
    public static final String INVOCATION_GROUP = "METHOD_INVOCATION_COUNTER";
    public static final String ITERATION_GROUP = "METHOD_ITERATION_COUNTER";

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder,
                    PostAllocationOptimizationContext context) {
        BenchmarkCounterFactory counterFactory = context.counterFactory;
        new Analyzer(target, lirGenRes.getCompilationUnitName(), lirGenRes.getLIR(), counterFactory).run();
    }

    private class Analyzer {
        private final LIR lir;
        private final BenchmarkCounterFactory counterFactory;
        private final LIRInsertionBuffer buffer;
        private final String compilationUnitName;
        private final ConstantValue increment;

        Analyzer(TargetDescription target, String compilationUnitName, LIR lir, BenchmarkCounterFactory counterFactory) {
            this.lir = lir;
            this.compilationUnitName = compilationUnitName;
            this.counterFactory = counterFactory;
            this.buffer = new LIRInsertionBuffer();
            this.increment = new ConstantValue(target.getLIRKind(JavaKind.Int), JavaConstant.INT_1);
        }

        public void run() {
            // insert counter at method entry
            doBlock(lir.getControlFlowGraph().getStartBlock(), INVOCATION_GROUP);
            for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
                if (block.isLoopHeader()) {
                    // insert counter at loop header
                    doBlock(block, ITERATION_GROUP);
                }
            }
        }

        public void doBlock(AbstractBlockBase<?> block, String group) {
            List<LIRInstruction> instructions = lir.getLIRforBlock(block);
            assert instructions.size() >= 2 : "Malformed block: " + block + ", " + instructions;
            assert instructions.get(instructions.size() - 1) instanceof BlockEndOp : "Not a BlockEndOp: " + instructions.get(instructions.size() - 1);
            assert !(instructions.get(instructions.size() - 2) instanceof BlockEndOp) : "Is a BlockEndOp: " + instructions.get(instructions.size() - 2);
            assert instructions.get(0) instanceof LabelOp : "Not a LabelOp: " + instructions.get(0);
            assert !(instructions.get(1) instanceof LabelOp) : "Is a LabelOp: " + instructions.get(1);

            LIRInstruction op = counterFactory.createBenchmarkCounter(compilationUnitName, group, increment);
            buffer.init(instructions);
            buffer.append(1, op);
            buffer.finish();
        }
    }

}
