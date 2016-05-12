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
package com.oracle.graal.lir.ssi;

import static com.oracle.graal.lir.LIRValueUtil.asVariable;
import static com.oracle.graal.lir.LIRValueUtil.isVariable;

import java.util.BitSet;
import java.util.EnumSet;
import java.util.List;
import java.util.ListIterator;

import com.oracle.graal.compiler.common.cfg.AbstractBlockBase;
import com.oracle.graal.compiler.common.cfg.BlockMap;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Indent;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.lir.InstructionValueConsumer;
import com.oracle.graal.lir.LIR;
import com.oracle.graal.lir.LIRInstruction;
import com.oracle.graal.lir.LIRInstruction.OperandFlag;
import com.oracle.graal.lir.LIRInstruction.OperandMode;
import com.oracle.graal.lir.StandardOp.BlockEndOp;
import com.oracle.graal.lir.StandardOp.LabelOp;
import com.oracle.graal.lir.ValueConsumer;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.meta.Value;

public class SSIBuilder {

    private static class BlockData {

        /**
         * Bit map specifying which operands are live upon entry to this block. These are values
         * used in this block or any of its successors where such value are not defined in this
         * block. The bit index of an operand is its {@linkplain #operandNumber operand number}.
         */
        public BitSet liveIn;

        /**
         * Bit map specifying which operands are live upon exit from this block. These are values
         * used in a successor block that are either defined in this block or were live upon entry
         * to this block. The bit index of an operand is its {@linkplain #operandNumber operand
         * number}.
         */
        public BitSet liveOut;

        /**
         * Bit map specifying which operands are used (before being defined) in this block. That is,
         * these are the values that are live upon entry to the block. The bit index of an operand
         * is its {@linkplain #operandNumber operand number}.
         */
        public BitSet liveGen;

        /**
         * Bit map specifying which operands are defined/overwritten in this block. The bit index of
         * an operand is its {@linkplain #operandNumber operand number}.
         */
        public BitSet liveKill;
    }

    private final LIR lir;
    private final Value[] operands;
    private final BlockMap<SSIBuilder.BlockData> blockData;

    protected SSIBuilder(LIR lir) {
        this.lir = lir;
        this.blockData = new BlockMap<>(lir.getControlFlowGraph());
        this.operands = new Value[lir.numVariables()];
    }

    protected void build() {
        init();
        computeLocalLiveSets();
        computeGlobalLiveSets();
        finish();
    }

    /**
     * Gets the size of the {@link BlockData#liveIn} and {@link BlockData#liveOut} sets for a basic
     * block.
     */
    private int liveSetSize() {
        return lir.numVariables();
    }

    private AbstractBlockBase<?>[] getBlocks() {
        return lir.getControlFlowGraph().getBlocks();
    }

    protected LIR getLIR() {
        return lir;
    }

    static int operandNumber(Value operand) {
        if (isVariable(operand)) {
            return asVariable(operand).index;
        }
        throw GraalError.shouldNotReachHere("Can only handle Variables: " + operand);
    }

    private SSIBuilder.BlockData getBlockData(AbstractBlockBase<?> block) {
        return blockData.get(block);
    }

    private void initBlockData(AbstractBlockBase<?> block) {
        blockData.put(block, new SSIBuilder.BlockData());
    }

    private void init() {
        ValueConsumer setVariableConsumer = new ValueConsumer() {
            @Override
            public void visitValue(Value value, OperandMode mode, EnumSet<OperandFlag> flags) {
                if (isVariable(value)) {
                    if (operands[asVariable(value).index] == null) {
                        operands[asVariable(value).index] = value;
                    }
                }
            }
        };

        for (AbstractBlockBase<?> block : getBlocks()) {
            initBlockData(block);

            for (LIRInstruction op : getLIR().getLIRforBlock(block)) {
                op.visitEachTemp(setVariableConsumer);
                op.visitEachOutput(setVariableConsumer);
            }
        }
    }

    /**
     * Computes local live sets (i.e. {@link BlockData#liveGen} and {@link BlockData#liveKill})
     * separately for each block.
     */
    @SuppressWarnings("try")
    private void computeLocalLiveSets() {
        int liveSize = liveSetSize();

        // iterate all blocks
        AbstractBlockBase<?>[] blocks = getBlocks();
        for (int i = blocks.length - 1; i >= 0; i--) {
            final AbstractBlockBase<?> block = blocks[i];
            try (Indent indent = Debug.logAndIndent("compute local live sets for block %s", block)) {

                final BitSet liveGen = new BitSet(liveSize);
                final BitSet liveKill = new BitSet(liveSize);

                InstructionValueConsumer useConsumer = new InstructionValueConsumer() {
                    @Override
                    public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                        processLocalUse(liveGen, operand);
                        visitUse(block, op, operand, mode, flags);
                    }
                };
                InstructionValueConsumer aliveConsumer = new InstructionValueConsumer() {
                    @Override
                    public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                        processLocalUse(liveGen, operand);
                        visitAlive(block, op, operand, mode, flags);
                    }
                };
                InstructionValueConsumer stateConsumer = new InstructionValueConsumer() {
                    @Override
                    public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                        if (isVariable(operand)) {
                            int operandNum = operandNumber(operand);
                            if (!liveKill.get(operandNum)) {
                                liveGen.set(operandNum);
                                if (Debug.isLogEnabled()) {
                                    Debug.log("liveGen in state for operand %d(%s)", operandNum, operand);
                                }
                            }
                        }
                        visitState(block, op, operand, mode, flags);
                    }
                };
                InstructionValueConsumer defConsumer = new InstructionValueConsumer() {
                    @Override
                    public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                        processLocalDef(liveGen, liveKill, operand);
                        visitDef(block, op, operand, mode, flags);
                    }
                };
                InstructionValueConsumer tempConsumer = new InstructionValueConsumer() {
                    @Override
                    public void visitValue(LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
                        processLocalDef(liveGen, liveKill, operand);
                        visitTemp(block, op, operand, mode, flags);
                    }
                };

                // iterate all instructions of the block
                List<LIRInstruction> instructions = getLIR().getLIRforBlock(block);
                ListIterator<LIRInstruction> instIt = instructions.listIterator(instructions.size());
                while (instIt.hasPrevious()) {
                    final LIRInstruction op = instIt.previous();

                    try (Indent indent2 = Debug.logAndIndent("handle op %d: %s", op.id(), op)) {
                        visitInstruction(block, op);
                        op.visitEachOutput(defConsumer);
                        op.visitEachTemp(tempConsumer);
                        op.visitEachState(stateConsumer);
                        op.visitEachAlive(aliveConsumer);
                        op.visitEachInput(useConsumer);
                    }
                } // end of instruction iteration

                SSIBuilder.BlockData blockSets = getBlockData(block);
                blockSets.liveGen = liveGen;
                blockSets.liveKill = liveKill;
                blockSets.liveIn = new BitSet(liveSize);
                blockSets.liveOut = new BitSet(liveSize);

                if (Debug.isLogEnabled()) {
                    Debug.log("liveGen  B%d %s", block.getId(), blockSets.liveGen);
                    Debug.log("liveKill B%d %s", block.getId(), blockSets.liveKill);
                }

            }
        } // end of block iteration
    }

    protected void processLocalUse(final BitSet liveGen, Value operand) {
        if (isVariable(operand)) {
            int operandNum = operandNumber(operand);
            liveGen.set(operandNum);
            if (Debug.isLogEnabled()) {
                Debug.log("liveGen for operand %d(%s)", operandNum, operand);
            }
        }
    }

    protected void processLocalDef(final BitSet liveGen, final BitSet liveKill, Value operand) {
        if (isVariable(operand)) {
            int operandNum = operandNumber(operand);
            liveKill.set(operandNum);
            liveGen.clear(operandNum);
            if (Debug.isLogEnabled()) {
                Debug.log("liveKill for operand %d(%s)", operandNum, operand);
            }
        }
    }

    /**
     * @param block
     * @param op
     * @param operand
     * @param mode
     * @param flags
     */
    protected void visitUse(AbstractBlockBase<?> block, LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
    }

    /**
     * @param block
     * @param op
     * @param operand
     * @param mode
     * @param flags
     */
    protected void visitAlive(AbstractBlockBase<?> block, LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
    }

    /**
     * @param block
     * @param op
     * @param operand
     * @param mode
     * @param flags
     */
    protected void visitDef(AbstractBlockBase<?> block, LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
    }

    /**
     * @param block
     * @param op
     * @param operand
     * @param mode
     * @param flags
     */
    protected void visitTemp(AbstractBlockBase<?> block, LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
    }

    /**
     * @param block
     * @param op
     * @param operand
     * @param mode
     * @param flags
     */
    protected void visitState(AbstractBlockBase<?> block, LIRInstruction op, Value operand, OperandMode mode, EnumSet<OperandFlag> flags) {
    }

    /**
     * @param op
     * @param block
     * @param incoming
     */
    protected void visitIncoming(AbstractBlockBase<?> block, LIRInstruction op, Value[] incoming) {
    }

    /**
     * @param op
     * @param block
     * @param outgoing
     */
    protected void visitOutgoing(AbstractBlockBase<?> block, LIRInstruction op, Value[] outgoing) {
    }

    /**
     * @param block
     * @param op
     */
    protected void visitInstruction(AbstractBlockBase<?> block, LIRInstruction op) {
    }

    /**
     * Performs a backward dataflow analysis to compute global live sets (i.e.
     * {@link BlockData#liveIn} and {@link BlockData#liveOut}) for each block.
     */
    @SuppressWarnings("try")
    private void computeGlobalLiveSets() {
        try (Indent indent = Debug.logAndIndent("compute global live sets")) {
            boolean changeOccurred;
            boolean changeOccurredInBlock;
            int iterationCount = 0;
            BitSet liveOut = new BitSet(liveSetSize()); // scratch set for
                                                        // calculations

            /*
             * Perform a backward dataflow analysis to compute liveOut and liveIn for each block.
             * The loop is executed until a fixpoint is reached (no changes in an iteration).
             */
            do {
                changeOccurred = false;

                try (Indent indent2 = Debug.logAndIndent("new iteration %d", iterationCount)) {

                    // iterate all blocks in reverse order
                    AbstractBlockBase<?>[] blocks = getBlocks();
                    for (int i = blocks.length - 1; i >= 0; i--) {
                        final AbstractBlockBase<?> block = blocks[i];
                        SSIBuilder.BlockData blockSets = getBlockData(block);

                        changeOccurredInBlock = false;

                        /*
                         * liveOut(block) is the union of liveIn(sux), for successors sux of block.
                         */
                        int n = block.getSuccessorCount();
                        if (n > 0) {
                            // block has successors
                            liveOut.clear();
                            for (AbstractBlockBase<?> successor : block.getSuccessors()) {
                                liveOut.or(getBlockData(successor).liveIn);
                            }

                            if (!blockSets.liveOut.equals(liveOut)) {
                                /*
                                 * A change occurred. Swap the old and new live out sets to avoid
                                 * copying.
                                 */
                                BitSet temp = blockSets.liveOut;
                                blockSets.liveOut = liveOut;
                                liveOut = temp;

                                changeOccurred = true;
                                changeOccurredInBlock = true;
                            }
                        }

                        if (iterationCount == 0 || changeOccurredInBlock) {
                            /*
                             * liveIn(block) is the union of liveGen(block) with (liveOut(block) &
                             * !liveKill(block)).
                             *
                             * Note: liveIn has to be computed only in first iteration or if liveOut
                             * has changed!
                             */
                            BitSet liveIn = blockSets.liveIn;
                            liveIn.clear();
                            liveIn.or(blockSets.liveOut);
                            liveIn.andNot(blockSets.liveKill);
                            liveIn.or(blockSets.liveGen);

                            if (Debug.isLogEnabled()) {
                                Debug.log("block %d: livein = %s,  liveout = %s", block.getId(), liveIn, blockSets.liveOut);
                            }
                        }
                    }
                    iterationCount++;

                    if (changeOccurred && iterationCount > 50) {
                        throw new BailoutException("too many iterations in computeGlobalLiveSets");
                    }
                }
            } while (changeOccurred);

            // check that the liveIn set of the first block is empty
            AbstractBlockBase<?> startBlock = getLIR().getControlFlowGraph().getStartBlock();
            if (getBlockData(startBlock).liveIn.cardinality() != 0) {
                // bailout if this occurs in product mode.
                throw new GraalError("liveIn set of first block must be empty: " + getBlockData(startBlock).liveIn);
            }
        }
    }

    @SuppressWarnings("try")
    private void finish() {
        Debug.dump(Debug.INFO_LOG_LEVEL, getLIR(), "Before SSI operands");
        // iterate all blocks in reverse order
        AbstractBlockBase<?>[] blocks = getBlocks();
        for (int i = blocks.length - 1; i >= 0; i--) {
            final AbstractBlockBase<?> block = blocks[i];
            try (Indent indent = Debug.logAndIndent("Finish Block %s", block)) {
                // set label
                SSIBuilder.BlockData data = blockData.get(block);
                assert data != null;
                buildOutgoing(block, data.liveOut);
                buildIncoming(block, data.liveIn);
            }
        }
    }

    private void buildIncoming(AbstractBlockBase<?> block, BitSet liveIn) {
        /*
         * Collect live out of predecessors since there might be values not used in this block which
         * might cause out/in mismatch.
         */
        BitSet predLiveOut = new BitSet(liveIn.length());
        for (AbstractBlockBase<?> pred : block.getPredecessors()) {
            predLiveOut.or(getBlockData(pred).liveOut);
        }
        if (predLiveOut.isEmpty()) {
            return;
        }

        Value[] values = new Value[predLiveOut.cardinality()];
        assert values.length > 0;
        int cnt = 0;
        for (int i = predLiveOut.nextSetBit(0); i >= 0; i = predLiveOut.nextSetBit(i + 1)) {
            values[cnt++] = liveIn.get(i) ? operands[i] : Value.ILLEGAL;
        }
        LabelOp label = SSIUtil.incoming(getLIR(), block);
        visitIncoming(block, label, values);
        label.addIncomingValues(values);
    }

    private void buildOutgoing(AbstractBlockBase<?> block, BitSet liveOut) {
        if (liveOut.isEmpty()) {
            return;
        }
        Value[] values = new Value[liveOut.cardinality()];
        assert values.length > 0;
        int cnt = 0;
        for (int i = liveOut.nextSetBit(0); i >= 0; i = liveOut.nextSetBit(i + 1)) {
            values[cnt++] = operands[i];
        }
        BlockEndOp blockEndOp = SSIUtil.outgoing(getLIR(), block);
        visitOutgoing(block, (LIRInstruction) blockEndOp, values);
        blockEndOp.addOutgoingValues(values);
    }
}
