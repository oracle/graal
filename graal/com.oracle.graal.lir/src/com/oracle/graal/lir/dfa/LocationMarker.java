/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.lir.dfa;

import static jdk.internal.jvmci.code.ValueUtil.*;

import java.util.*;

import jdk.internal.jvmci.code.*;
import com.oracle.graal.debug.*;
import jdk.internal.jvmci.meta.*;

import com.oracle.graal.compiler.common.cfg.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.framemap.*;

public abstract class LocationMarker<T extends AbstractBlockBase<T>, S extends ValueSet<S>> {

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

    @SuppressWarnings("unchecked")
    void build() {
        UniqueWorkList<T> worklist = new UniqueWorkList<>(lir.getControlFlowGraph().getBlocks().size());
        for (int i = lir.getControlFlowGraph().getBlocks().size() - 1; i >= 0; i--) {
            worklist.add((T) lir.getControlFlowGraph().getBlocks().get(i));
        }
        for (AbstractBlockBase<?> block : lir.getControlFlowGraph().getBlocks()) {
            liveInMap.put(block, newLiveValueSet());
        }
        while (!worklist.isEmpty()) {
            AbstractBlockBase<T> block = worklist.poll();
            processBlock(block, worklist);
        }
    }

    /**
     * Merge outSet with in-set of successors.
     */
    private boolean updateOutBlock(AbstractBlockBase<T> block) {
        S union = newLiveValueSet();
        for (T succ : block.getSuccessors()) {
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

    private void processBlock(AbstractBlockBase<T> block, UniqueWorkList<T> worklist) {
        if (updateOutBlock(block)) {
            try (Indent indent = Debug.logAndIndent("handle block %s", block)) {
                currentSet = liveOutMap.get(block).copy();
                List<LIRInstruction> instructions = lir.getLIRforBlock(block);
                for (int i = instructions.size() - 1; i >= 0; i--) {
                    LIRInstruction inst = instructions.get(i);
                    processInstructionBottomUp(inst);
                }
                liveInMap.put(block, currentSet);
                currentSet = null;
                worklist.addAll(block.getPredecessors());
            }
        }
    }

    private static final EnumSet<OperandFlag> REGISTER_FLAG_SET = EnumSet.of(OperandFlag.REG);
    private static final LIRKind REFERENCE_KIND = LIRKind.reference(Kind.Object);

    private S currentSet;

    /**
     * Process all values of an instruction bottom-up, i.e. definitions before usages. Values that
     * start or end at the current operation are not included.
     */
    private void processInstructionBottomUp(LIRInstruction op) {
        try (Indent indent = Debug.logAndIndent("handle op %d, %s", op.id(), op)) {
            // kills

            op.visitEachTemp(defConsumer);
            op.visitEachOutput(defConsumer);
            if (frameMap != null && op.destroysCallerSavedRegisters()) {
                for (Register reg : frameMap.getRegisterConfig().getCallerSaveRegisters()) {
                    defConsumer.visitValue(reg.asValue(REFERENCE_KIND), OperandMode.TEMP, REGISTER_FLAG_SET);
                }
            }

            // gen - values that are considered alive for this state
            op.visitEachAlive(useConsumer);
            op.visitEachState(useConsumer);
            // mark locations
            op.forEachState(stateConsumer);
            // gen
            op.visitEachInput(useConsumer);
        }
    }

    InstructionStateProcedure stateConsumer = new InstructionStateProcedure() {
        public void doState(LIRInstruction inst, LIRFrameState info) {
            processState(inst, info, currentSet);
        }
    };

    ValueConsumer useConsumer = new ValueConsumer() {
        public void visitValue(Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
            if (shouldProcessValue(operand)) {
                // no need to insert values and derived reference
                if (Debug.isLogEnabled()) {
                    Debug.log("set operand: %s", operand);
                }
                currentSet.put(operand);
            }
        }
    };

    ValueConsumer defConsumer = new ValueConsumer() {
        public void visitValue(Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
            if (shouldProcessValue(operand)) {
                if (Debug.isLogEnabled()) {
                    Debug.log("clear operand: %s", operand);
                }
                currentSet.remove(operand);
            } else {
                assert isIllegal(operand) || operand.getPlatformKind() != Kind.Illegal || mode == OperandMode.TEMP : String.format("Illegal PlatformKind is only allowed for TEMP mode: %s, %s",
                                operand, mode);
            }
        }
    };
}
