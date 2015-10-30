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
package com.oracle.graal.lir.profiling;

import static jdk.vm.ci.code.ValueUtil.isRegister;
import static jdk.vm.ci.code.ValueUtil.isStackSlot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.AllocatableValue;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;

import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.lir.ConstantValue;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInsertionBuffer;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.StandardOp.LoadConstantOp;
import com.oracle.graal.lir.StandardOp.MoveOp;
import com.oracle.graal.lir.StandardOp.ValueMoveOp;
import com.oracle.graal.lir.gen.BenchmarkCounterFactory;
import com.oracle.graal.lir.gen.LIRGenerationResult;
import com.oracle.graal.lir.phases.PostAllocationOptimizationPhase;

public class MoveProfiling extends PostAllocationOptimizationPhase {

    @Override
    protected <B extends AbstractBlockBase<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder,
                    BenchmarkCounterFactory counterFactory) {
        new Analyzer(target, lirGenRes.getLIR(), counterFactory).run();
    }

    private static enum MoveType {
        REG2REG("Reg", "Reg"),
        STACK2REG("Reg", "Stack"),
        CONST2REG("Reg", "Const"),
        REG2STACK("Stack", "Reg"),
        CONST2STACK("Stack", "Const"),
        STACK2STACK("Stack", "Stack");

        private final String name;

        MoveType(String dst, String src) {
            this.name = String.format("%5s <- %s", dst, src);
        }

        @Override
        public String toString() {
            return name;
        }

        public static MoveType get(MoveOp move) {
            AllocatableValue dst = move.getResult();
            Value src = null;
            if (move instanceof LoadConstantOp) {
                if (isRegister(dst)) {
                    return CONST2REG;
                } else if (isStackSlot(dst)) {
                    return CONST2STACK;
                }
            } else if (move instanceof ValueMoveOp) {
                src = ((ValueMoveOp) move).getInput();
                if (isRegister(dst)) {
                    if (isRegister(src)) {
                        return REG2REG;
                    } else if (isStackSlot(src)) {
                        return STACK2REG;
                    }
                } else if (isStackSlot(dst)) {
                    if (isRegister(src)) {
                        return REG2STACK;
                    } else if (isStackSlot(src)) {
                        return STACK2STACK;
                    }
                }
            }
            throw JVMCIError.shouldNotReachHere(String.format("Unrecognized Move: %s dst=%s, src=%s", move, dst, src));
        }
    }

    private static class Analyzer {
        private final TargetDescription target;
        private final LIR lir;
        private final BenchmarkCounterFactory counterFactory;
        private final LIRInsertionBuffer buffer;
        private final int[] cnt;

        public Analyzer(TargetDescription target, LIR lir, BenchmarkCounterFactory counterFactory) {
            this.target = target;
            this.lir = lir;
            this.counterFactory = counterFactory;
            this.buffer = new LIRInsertionBuffer();
            cnt = new int[MoveType.values().length];
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

            // reset counters
            Arrays.fill(cnt, 0);
            // analysis phase
            for (LIRInstruction inst : instructions) {
                if (inst instanceof MoveOp) {
                    cnt[MoveType.get((MoveOp) inst).ordinal()]++;
                }
            }

            // counter insertion phase
            List<String> names = new ArrayList<>();
            List<Value> increments = new ArrayList<>();
            for (MoveType type : MoveType.values()) {
                int i = cnt[type.ordinal()];
                if (i > 0) {
                    names.add(type.toString());
                    increments.add(new ConstantValue(target.getLIRKind(JavaKind.Int), JavaConstant.forInt(i)));
                }
            }
            String[] groups = new String[names.size()];
            Arrays.fill(groups, "Move Operations");
            if (names.size() > 0) { // Don't pollute LIR when nothing has to be done
                LIRInstruction inst = counterFactory.createMultiBenchmarkCounter(names.toArray(new String[0]), groups, increments.toArray(new Value[0]));
                assert inst != null;
                buffer.init(instructions);
                buffer.append(1, inst);
                buffer.finish();
            }
        }
    }

}
