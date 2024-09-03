/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.dfa;

import static jdk.vm.ci.code.ValueUtil.isIllegal;

import java.util.ArrayList;
import java.util.EnumSet;

import jdk.graal.compiler.core.common.LIRKind;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.lir.InstructionStateProcedure;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRFrameState;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.LIRValueUtil;
import jdk.graal.compiler.lir.ValueConsumer;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.lir.util.ValueSet;

import jdk.vm.ci.code.Register;
import jdk.vm.ci.meta.PlatformKind;
import jdk.vm.ci.meta.Value;

public abstract class LocationMarker<S extends ValueSet<S>> {

    private final LIR lir;
    private final BlockMap<S> liveInMap;
    private final BlockMap<S> liveOutMap;

    protected final FrameMap frameMap;

    protected LocationMarker(LIR lir, FrameMap frameMap) {
        this.lir = lir;
        this.frameMap = frameMap;
        liveInMap = new BlockMap<>(lir.getControlFlowGraph());
        liveOutMap = new BlockMap<>(lir.getControlFlowGraph());
    }

    protected abstract S newLiveValueSet();

    protected abstract boolean shouldProcessValue(Value operand);

    protected abstract void processState(LIRInstruction op, LIRFrameState info, S values);

    void build() {
        BasicBlock<?>[] blocks = lir.getControlFlowGraph().getBlocks();
        UniqueWorkList worklist = new UniqueWorkList(blocks.length);
        for (int i = blocks.length - 1; i >= 0; i--) {
            worklist.add(blocks[i]);
        }
        for (BasicBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
            liveInMap.put(block, newLiveValueSet());
        }
        while (!worklist.isEmpty()) {
            CompilationAlarm.checkProgress(lir.getOptions(), lir);
            BasicBlock<?> block = worklist.poll();
            processBlock(block, worklist);
        }
    }

    /**
     * Merge outSet with in-set of successors.
     */
    private boolean updateOutBlock(BasicBlock<?> block) {
        S union = newLiveValueSet();
        for (int i = 0; i < block.getSuccessorCount(); i++) {
            BasicBlock<?> succ = block.getSuccessorAt(i);
            union.putAll(liveInMap.get(succ));
        }
        S outSet = liveOutMap.get(block);
        // check if changed
        if (outSet == null || !union.equals(outSet)) {
            liveOutMap.put(block, union);
            return true;
        }
        return false;
    }

    @SuppressWarnings("try")
    private void processBlock(BasicBlock<?> block, UniqueWorkList worklist) {
        if (updateOutBlock(block)) {
            DebugContext debug = lir.getDebug();
            try (Indent indent = debug.logAndIndent("handle block %s", block)) {
                currentSet = liveOutMap.get(block).copy();
                ArrayList<LIRInstruction> instructions = lir.getLIRforBlock(block);
                for (int i = instructions.size() - 1; i >= 0; i--) {
                    LIRInstruction inst = instructions.get(i);
                    processInstructionBottomUp(inst);
                }
                liveInMap.put(block, currentSet);
                currentSet = null;
                for (int i = 0; i < block.getPredecessorCount(); i++) {
                    BasicBlock<?> b = block.getPredecessorAt(i);
                    worklist.add(b);
                }
            }
        }
    }

    private static final EnumSet<LIRInstruction.OperandFlag> REGISTER_FLAG_SET = EnumSet.of(LIRInstruction.OperandFlag.REG);

    private S currentSet;

    /**
     * Process all values of an instruction bottom-up, i.e. definitions before usages. Values that
     * start or end at the current operation are not included.
     */
    @SuppressWarnings("try")
    private void processInstructionBottomUp(LIRInstruction op) {
        DebugContext debug = lir.getDebug();
        try (Indent indent = debug.logAndIndent("handle op %d, %s", op.id(), op)) {
            // kills

            op.visitEachOutput(defConsumer);
            op.visitEachTemp(defConsumer);
            if (frameMap != null && op.destroysCallerSavedRegisters()) {
                for (Register reg : frameMap.getRegisterConfig().getCallerSaveRegisters()) {
                    PlatformKind kind = frameMap.getTarget().arch.getLargestStorableKind(reg.getRegisterCategory());
                    defConsumer.visitValue(reg.asValue(LIRKind.value(kind)), LIRInstruction.OperandMode.TEMP, REGISTER_FLAG_SET);
                }
            }

            // gen - values that are considered alive for this state
            op.visitEachAlive(useConsumer);
            op.visitEachState(useConsumer);
            // mark locations
            op.forEachState(stateConsumer);
            // gen
            op.visitEachInput(useConsumer);
        } catch (GraalError e) {
            throw e.addContext("lir instruction", "@" + op.id() + " " + op.getClass().getName() + " " + op);
        }
    }

    InstructionStateProcedure stateConsumer = new InstructionStateProcedure() {
        @Override
        public void doState(LIRInstruction inst, LIRFrameState info) {
            processState(inst, info, currentSet);
        }
    };

    ValueConsumer useConsumer = new ValueConsumer() {
        @Override
        public void visitValue(Value operand, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
            if (shouldProcessValue(operand)) {
                // no need to insert values and derived reference
                // Remove any cast so the uses and defs are in agreement about the type
                Value value = LIRValueUtil.uncast(operand);
                DebugContext debug = lir.getDebug();
                if (debug.isLogEnabled()) {
                    if (!value.equals(operand)) {
                        debug.log("changing operand from %s to %s", operand, value);
                    }
                    debug.log("set operand: %s", value);
                }
                currentSet.put(value);
            }
        }
    };

    ValueConsumer defConsumer = new ValueConsumer() {
        @Override
        public void visitValue(Value operand, LIRInstruction.OperandMode mode, EnumSet<LIRInstruction.OperandFlag> flags) {
            if (shouldProcessValue(operand)) {
                DebugContext debug = lir.getDebug();
                if (debug.isLogEnabled()) {
                    debug.log("clear operand: %s", operand);
                }
                currentSet.remove(operand);
            } else {
                assert isIllegal(operand) || !operand.getValueKind().equals(LIRKind.Illegal) || mode == LIRInstruction.OperandMode.TEMP : String.format(
                                "Illegal PlatformKind is only allowed for TEMP mode: %s, %s",
                                operand, mode);
            }
        }
    };
}
