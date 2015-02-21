/*
 * Copyright (c) 2014, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.alloc.lsra;

import static com.oracle.graal.api.code.ValueUtil.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.framemap.*;
import com.oracle.graal.lir.gen.*;
import com.oracle.graal.lir.phases.*;
import com.oracle.graal.options.*;

/**
 * Mark all live references for a frame state. The frame state use this information to build the OOP
 * maps.
 */
public final class LocationMarker extends AllocationPhase {

    public static class Options {
        // @formatter:off
        @Option(help = "Use decoupled pass for location marking (instead of using LSRA marking)", type = OptionType.Debug)
        public static final OptionValue<Boolean> UseLocationMarker = new OptionValue<>(true);
        // @formatter:on
    }

    @Override
    protected <B extends AbstractBlock<B>> void run(TargetDescription target, LIRGenerationResult lirGenRes, List<B> codeEmittingOrder, List<B> linearScanOrder) {
        new Marker(lirGenRes.getLIR(), lirGenRes.getFrameMap()).build();
    }

    private static final class Marker {
        private final LIR lir;
        private final FrameMap frameMap;
        private final RegisterAttributes[] registerAttributes;
        private final BlockMap<ReferenceMap> liveInMap;
        private final BlockMap<ReferenceMap> liveOutMap;

        private Marker(LIR lir, FrameMap frameMap) {
            this.lir = lir;
            this.frameMap = frameMap;
            this.registerAttributes = frameMap.getRegisterConfig().getAttributesMap();
            liveInMap = new BlockMap<>(lir.getControlFlowGraph());
            liveOutMap = new BlockMap<>(lir.getControlFlowGraph());
        }

        private void build() {
            Deque<AbstractBlock<?>> worklist = new ArrayDeque<>();
            for (int i = lir.getControlFlowGraph().getBlocks().size() - 1; i >= 0; i--) {
                worklist.add(lir.getControlFlowGraph().getBlocks().get(i));
            }
            for (AbstractBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
                liveInMap.put(block, frameMap.initReferenceMap(true));
            }
            while (!worklist.isEmpty()) {
                AbstractBlock<?> block = worklist.poll();
                processBlock(block, worklist);
            }
            // finish states
            for (AbstractBlock<?> block : lir.getControlFlowGraph().getBlocks()) {
                List<LIRInstruction> instructions = lir.getLIRforBlock(block);
                for (int i = instructions.size() - 1; i >= 0; i--) {
                    LIRInstruction inst = instructions.get(i);
                    inst.forEachState((op, info) -> info.finish(op, frameMap));
                }

            }
        }

        /**
         * Merge outSet with in-set of successors.
         */
        private boolean updateOutBlock(AbstractBlock<?> block) {
            ReferenceMap union = frameMap.initReferenceMap(true);
            block.getSuccessors().forEach(succ -> union.updateUnion(liveInMap.get(succ)));
            ReferenceMap outSet = liveOutMap.get(block);
            // check if changed
            if (outSet == null || !union.equals(outSet)) {
                liveOutMap.put(block, union);
                return true;
            }
            return false;
        }

        private void processBlock(AbstractBlock<?> block, Deque<AbstractBlock<?>> worklist) {
            if (updateOutBlock(block)) {
                try (Indent indent = Debug.logAndIndent("handle block %s", block)) {
                    BlockClosure closure = new BlockClosure(liveOutMap.get(block).clone());
                    List<LIRInstruction> instructions = lir.getLIRforBlock(block);
                    for (int i = instructions.size() - 1; i >= 0; i--) {
                        LIRInstruction inst = instructions.get(i);
                        closure.processInstructionBottomUp(inst);
                    }
                    liveInMap.put(block, closure.getCurrentSet());
                    worklist.addAll(block.getPredecessors());
                }
            }
        }

        private static final EnumSet<OperandFlag> REGISTER_FLAG_SET = EnumSet.of(OperandFlag.REG);
        private static final LIRKind REFERENCE_KIND = LIRKind.reference(Kind.Object);

        private void forEachDestroyedCallerSavedRegister(LIRInstruction op, ValueConsumer consumer) {
            if (op.destroysCallerSavedRegisters()) {
                for (Register reg : frameMap.getRegisterConfig().getCallerSaveRegisters()) {
                    consumer.visitValue(reg.asValue(REFERENCE_KIND), OperandMode.TEMP, REGISTER_FLAG_SET);
                }
            }
        }

        private final class BlockClosure {
            private final ReferenceMap currentSet;

            private BlockClosure(ReferenceMap set) {
                currentSet = set;
            }

            private ReferenceMap getCurrentSet() {
                return currentSet;
            }

            /**
             * Process all values of an instruction bottom-up, i.e. definitions before usages.
             * Values that start or end at the current operation are not included.
             */
            private void processInstructionBottomUp(LIRInstruction op) {
                try (Indent indent = Debug.logAndIndent("handle op %d, %s", op.id(), op)) {
                    // kills
                    op.visitEachTemp(this::defConsumer);
                    op.visitEachOutput(this::defConsumer);
                    forEachDestroyedCallerSavedRegister(op, this::defConsumer);

                    // gen - values that are considered alive for this state
                    op.visitEachAlive(this::useConsumer);
                    op.visitEachState(this::useConsumer);
                    // mark locations
                    op.forEachState((inst, info) -> markLocation(inst, info, this.getCurrentSet()));
                    // gen
                    op.visitEachInput(this::useConsumer);
                }
            }

            /**
             * @see InstructionValueConsumer
             * @param operand
             * @param mode
             * @param flags
             */
            private void useConsumer(Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                LIRKind kind = operand.getLIRKind();
                if (shouldProcessValue(operand) && !kind.isValue() && !kind.isDerivedReference()) {
                    // no need to insert values and derived reference
                    Debug.log("set operand: %s", operand);
                    frameMap.setReference(operand, currentSet);
                }
            }

            /**
             * @see InstructionValueConsumer
             * @param operand
             * @param mode
             * @param flags
             */
            private void defConsumer(Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (shouldProcessValue(operand)) {
                    Debug.log("clear operand: %s", operand);
                    frameMap.clearReference(operand, currentSet);
                } else {
                    assert isIllegal(operand) || operand.getPlatformKind() != Kind.Illegal || mode == OperandMode.TEMP : String.format("Illegal PlatformKind is only allowed for TEMP mode: %s, %s",
                                    operand, mode);
                }
            }

            protected boolean shouldProcessValue(Value operand) {
                return (isRegister(operand) && attributes(asRegister(operand)).isAllocatable() || isStackSlot(operand)) && operand.getPlatformKind() != Kind.Illegal;
            }
        }

        /**
         * This method does the actual marking.
         */
        private void markLocation(LIRInstruction op, LIRFrameState info, ReferenceMap refMap) {
            if (!info.hasDebugInfo()) {
                info.initDebugInfo(frameMap, !op.destroysCallerSavedRegisters() || !frameMap.getRegisterConfig().areAllAllocatableRegistersCallerSaved());
            }
            info.updateUnion(refMap);
        }

        /**
         * Gets an object describing the attributes of a given register according to this register
         * configuration.
         *
         * @see LinearScan#attributes
         */
        private RegisterAttributes attributes(Register reg) {
            return registerAttributes[reg.number];
        }

    }
}
