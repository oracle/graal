/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot;

import java.util.*;

import jdk.internal.jvmci.code.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.asm.*;
import com.oracle.graal.asm.Assembler.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.phases.*;

public class HotSpotInstructionProfiling extends PostAllocationOptimizationPhase {
    public static final String COUNTER_GROUP = "INSTRUCTION_COUNTER";
    private final String[] instructionsToProfile;

    public HotSpotInstructionProfiling(String instructionsToProfile) {
        this.instructionsToProfile = instructionsToProfile.split(",");
    }

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder,
                    BenchmarkCounterFactory counterFactory) {
        new Analyzer(lirGenRes.getCompilationUnitName(), lirGenRes.getLIR(), counterFactory).run();
    }

    private class Analyzer {
        private final LIR lir;
        private final BenchmarkCounterFactory counterFactory;
        private final LIRInsertionBuffer buffer;
        private final String compilationUnitName;

        public Analyzer(String compilationUnitName, LIR lir, BenchmarkCounterFactory counterFactory) {
            this.lir = lir;
            this.compilationUnitName = compilationUnitName;
            this.counterFactory = counterFactory;
            this.buffer = new LIRInsertionBuffer();
        }

        public void run() {
            for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
                doBlock(block);
            }
        }

        public void doBlock(AbstractBlockBase<?> block) {
            List<LIRInstruction> instructions = lir.getLIRforBlock(block);
            assert instructions.size() >= 2 : "Malformed block: " + block + ", " + instructions;
            assert instructions.get(instructions.size() - 1) instanceof BlockEndOp : "Not a BlockEndOp: " + instructions.get(instructions.size() - 1);
            assert !(instructions.get(instructions.size() - 2) instanceof BlockEndOp) : "Is a BlockEndOp: " + instructions.get(instructions.size() - 2);
            assert instructions.get(0) instanceof LabelOp : "Not a LabelOp: " + instructions.get(0);
            assert !(instructions.get(1) instanceof LabelOp) : "Is a LabelOp: " + instructions.get(1);
            String[] names = new String[instructionsToProfile.length];
            String[] groups = new String[instructionsToProfile.length];
            Value[] increments = new Value[instructionsToProfile.length];
            for (int i = 0; i < instructionsToProfile.length; i++) {
                names[i] = compilationUnitName;
                groups[i] = COUNTER_GROUP + " " + instructionsToProfile[i];
                increments[i] = JavaConstant.INT_0;
            }
            HotSpotCounterOp op = (HotSpotCounterOp) counterFactory.createMultiBenchmarkCounter(names, groups, increments);
            LIRInstruction inst = new InstructionCounterOp(op, instructionsToProfile);
            assert inst != null;
            buffer.init(instructions);
            buffer.append(1, inst);
            buffer.finish();
        }
    }

    /**
     * After assembly the {@link HotSpotBackend#profileInstructions(LIR, CompilationResultBuilder)}
     * calls this method for patching the instruction counts into the coutner increment code.
     */
    public static void countInstructions(LIR lir, Assembler asm) {
        InstructionCounterOp lastOp = null;
        InstructionCounter counter = asm.getInstructionCounter();
        for (AbstractBlockBase<?> block : lir.codeEmittingOrder()) {
            for (LIRInstruction inst : lir.getLIRforBlock(block)) {
                if (inst instanceof InstructionCounterOp) {
                    InstructionCounterOp currentOp = (InstructionCounterOp) inst;

                    if (lastOp != null) {
                        int beginPc = lastOp.countOffsetEnd;
                        int endPc = currentOp.countOffsetBegin;
                        int[] instructionCounts = counter.countInstructions(lastOp.instructionsToProfile, beginPc, endPc);
                        lastOp.delegate.patchCounterIncrement(asm, instructionCounts);
                    }
                    lastOp = ((InstructionCounterOp) inst);
                }
            }
        }
        if (lastOp != null) {
            assert lastOp.countOffsetBegin < asm.position();
            int beginPc = lastOp.countOffsetBegin;
            int endPc = asm.position();
            int[] instructionCounts = counter.countInstructions(lastOp.instructionsToProfile, beginPc, endPc);
            lastOp.delegate.patchCounterIncrement(asm, instructionCounts);
        }
    }

    public static class InstructionCounterOp extends LIRInstruction {
        public static final LIRInstructionClass<InstructionCounterOp> TYPE = LIRInstructionClass.create(InstructionCounterOp.class);
        private final HotSpotCounterOp delegate;
        private final String[] instructionsToProfile;
        private int countOffsetBegin;
        private int countOffsetEnd;

        public InstructionCounterOp(HotSpotCounterOp delegate, String[] instructionsToProfile) {
            super(TYPE);
            this.delegate = delegate;
            this.instructionsToProfile = instructionsToProfile;
        }

        @Override
        public void emitCode(CompilationResultBuilder crb) {
            countOffsetBegin = crb.asm.position();
            this.delegate.emitCode(crb);
            countOffsetEnd = crb.asm.position();
        }

        public String[] getInstructionsToProfile() {
            return instructionsToProfile;
        }
    }
}
