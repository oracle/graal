/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.profiling;

import java.util.ArrayList;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.lir.ConstantValue;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInsertionBuffer;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.StandardOp.BlockEndOp;
import jdk.graal.compiler.lir.StandardOp.LabelOp;
import jdk.graal.compiler.lir.gen.DiagnosticLIRGeneratorTool;
import jdk.graal.compiler.lir.gen.LIRGenerationResult;
import jdk.graal.compiler.lir.phases.PostAllocationOptimizationPhase;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

public class MethodProfilingPhase extends PostAllocationOptimizationPhase {
    public static final String INVOCATION_GROUP = "METHOD_INVOCATION_COUNTER";
    public static final String ITERATION_GROUP = "METHOD_ITERATION_COUNTER";

    @Override
    protected void run(TargetDescription target, LIRGenerationResult lirGenRes, PostAllocationOptimizationContext context) {
        new Analyzer(target, lirGenRes, context.diagnosticLirGenTool).run();
    }

    private static class Analyzer {
        private final LIR lir;
        private final DiagnosticLIRGeneratorTool diagnosticLirGenTool;
        private final LIRInsertionBuffer buffer;
        private final ConstantValue increment;
        private final LIRGenerationResult lirGenRes;

        Analyzer(TargetDescription target, LIRGenerationResult lirGenRes, DiagnosticLIRGeneratorTool diagnosticLirGenTool) {
            this.lir = lirGenRes.getLIR();
            this.lirGenRes = lirGenRes;
            this.diagnosticLirGenTool = diagnosticLirGenTool;
            this.buffer = new LIRInsertionBuffer();
            this.increment = new ConstantValue(LIRKind.fromJavaKind(target.arch, JavaKind.Int), JavaConstant.INT_1);
        }

        public void run() {
            // insert counter at method entry
            doBlock(lir.getControlFlowGraph().getStartBlock(), INVOCATION_GROUP);
            for (BasicBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
                if (block.isLoopHeader()) {
                    // insert counter at loop header
                    doBlock(block, ITERATION_GROUP);
                }
            }
        }

        public void doBlock(BasicBlock<?> block, String group) {
            ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
            assert instructions.size() >= 2 : "Malformed block: " + block + ", " + instructions;
            assert instructions.get(instructions.size() - 1) instanceof BlockEndOp : "Not a BlockEndOp: " + instructions.get(instructions.size() - 1);
            assert !(instructions.get(instructions.size() - 2) instanceof BlockEndOp) : "Is a BlockEndOp: " + instructions.get(instructions.size() - 2);
            assert instructions.get(0) instanceof LabelOp : "Not a LabelOp: " + instructions.get(0);
            assert !(instructions.get(1) instanceof LabelOp) : "Is a LabelOp: " + instructions.get(1);

            LIRInstruction op = diagnosticLirGenTool.createBenchmarkCounter(lirGenRes.getCompilationUnitName(), group, increment);
            buffer.init(instructions);
            int insertionIndex = lirGenRes.getFirstInsertPosition();
            buffer.append(insertionIndex, op);
            buffer.finish();
        }
    }

}
