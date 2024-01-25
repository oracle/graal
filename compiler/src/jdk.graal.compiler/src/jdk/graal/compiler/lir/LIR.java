/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.List;

import jdk.graal.compiler.asm.Label;
import jdk.graal.compiler.core.common.cfg.AbstractControlFlowGraph;
import jdk.graal.compiler.core.common.cfg.BasicBlock;
import jdk.graal.compiler.core.common.cfg.BlockMap;
import jdk.graal.compiler.core.common.util.EventCounter;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.lir.StandardOp.BlockEndOp;
import jdk.graal.compiler.lir.StandardOp.LabelHoldingOp;
import jdk.graal.compiler.lir.StandardOp.LabelOp;
import jdk.graal.compiler.lir.gen.LIRGenerator;
import jdk.graal.compiler.options.OptionValues;

/**
 * This class implements the overall container for the LIR graph and directs its construction,
 * optimization, and finalization.
 *
 * In order to reduce memory usage LIR keeps arrays of block ids instead of direct pointer arrays to
 * {@link BasicBlock} from a {@link AbstractControlFlowGraph} reverse post order list.
 */
public final class LIR extends LIRGenerator.VariableProvider implements EventCounter {

    private final AbstractControlFlowGraph<?> cfg;

    /**
     * The linear-scan ordered list of block ids into {@link AbstractControlFlowGraph#getBlocks()}.
     */
    private final int[] linearScanOrder;

    /**
     * The code emission ordered list of block ids into
     * {@link AbstractControlFlowGraph#getBlocks()}.
     */
    private int[] codeEmittingOrder;

    /**
     * Map from {@linkplain BasicBlock block} to {@linkplain LIRInstruction}s. Note that we are
     * using {@link ArrayList} instead of {@link List} to avoid interface dispatch.
     */
    private final BlockMap<ArrayList<LIRInstruction>> lirInstructions;

    /**
     * Extra chunks of out of line assembly that must be emitted after all the LIR instructions.
     */
    private ArrayList<LIRInstruction.LIRInstructionSlowPath> slowPaths;

    private boolean hasArgInCallerFrame;

    private final OptionValues options;

    private final DebugContext debug;
    /**
     * Counter to associate "events" with this LIR, i.e., have a counter per graph that can be used
     * to trigger certain operations.
     */
    private int eventCounter;

    /**
     * Creates a new LIR instance for the specified compilation.
     */
    public LIR(AbstractControlFlowGraph<?> cfg,
                    int[] linearScanOrder,
                    OptionValues options,
                    DebugContext debug) {
        this.cfg = cfg;
        this.codeEmittingOrder = null;
        this.linearScanOrder = linearScanOrder;
        this.lirInstructions = new BlockMap<>(cfg);
        this.options = options;
        this.debug = debug;
    }

    @Override
    public boolean eventCounterOverflows(int max) {
        if (eventCounter++ > max) {
            eventCounter = 0;
            return true;
        }
        return false;
    }

    public AbstractControlFlowGraph<?> getControlFlowGraph() {
        return cfg;
    }

    public BasicBlock<?> getBlockById(int blockId) {
        assert blockId <= AbstractControlFlowGraph.LAST_VALID_BLOCK_INDEX;
        return cfg.getBlocks()[blockId];
    }

    public static boolean isBlockDeleted(int blockId) {
        return AbstractControlFlowGraph.blockIsDeletedOrNew(blockId);
    }

    public OptionValues getOptions() {
        return options;
    }

    public DebugContext getDebug() {
        return debug;
    }

    /**
     * Determines if any instruction in the LIR has debug info associated with it.
     */
    public boolean hasDebugInfo() {
        for (int c : linearScanOrder()) {
            BasicBlock<?> b = cfg.getBlocks()[c];
            for (LIRInstruction op : getLIRforBlock(b)) {
                if (op.hasState()) {
                    return true;
                }
            }
        }
        return false;
    }

    public ArrayList<LIRInstruction> getLIRforBlock(BasicBlock<?> block) {
        return lirInstructions.get(block);
    }

    public void setLIRforBlock(BasicBlock<?> block, ArrayList<LIRInstruction> list) {
        assert getLIRforBlock(block) == null : "lir instruction list should only be initialized once";
        lirInstructions.put(block, list);
    }

    /**
     * Gets the linear scan ordering of blocks as an array. After control flow optimizations this
     * can contain {@code null} entries for blocks that have been optimized away.
     *
     * @return the blocks in linear scan order
     */
    public int[] linearScanOrder() {
        return linearScanOrder;
    }

    /**
     * Gets the code emitting ordering of blocks as an array. Code that does not care about visiting
     * blocks in a particular order should use {@link #getBlocks()} instead. The code emitting order
     * is computed late in the LIR pipeline; {@link #codeEmittingOrderAvailable()} can be used to
     * check whether it has been computed. This method will throw an exception if the code emitting
     * order is not available.
     *
     * @return the blocks in code emitting order
     * @throws IllegalStateException if the code emitting order is not
     *             {@linkplain #codeEmittingOrderAvailable() available}
     */
    public int[] codeEmittingOrder() {
        if (!codeEmittingOrderAvailable()) {
            throw new IllegalStateException("codeEmittingOrder not computed, consider using getBlocks() or linearScanOrder()");
        }
        return codeEmittingOrder;
    }

    public void setCodeEmittingOrder(int[] codeEmittingOrder) {
        this.codeEmittingOrder = codeEmittingOrder;
    }

    /**
     * Checks whether the code emitting order has been computed.
     */
    public boolean codeEmittingOrderAvailable() {
        return codeEmittingOrder != null;
    }

    /**
     * The current set of out of line assembly chunks to be emitted.
     */
    public ArrayList<LIRInstruction.LIRInstructionSlowPath> getSlowPaths() {
        return slowPaths;
    }

    /**
     * Add a chunk of assembly that will be emitted out of line after all LIR has been emitted.
     */
    public void addSlowPath(LIRInstruction op, Runnable slowPath) {
        if (slowPaths == null) {
            slowPaths = new ArrayList<>();
        }
        slowPaths.add(new LIRInstruction.LIRInstructionSlowPath(op, slowPath));
    }

    /**
     * Gets an array of all the blocks in this LIR. This should be used by all code that wants to
     * iterate over the blocks but does not care about a particular order.
     *
     * The array returned here is in the {@link #codeEmittingOrder()} if available, otherwise it is
     * in {@link #linearScanOrder()}. In either case it can contain {@code null} entries for blocks
     * that have been optimized away. The start block will always be at index 0.
     */
    public int[] getBlocks() {
        if (codeEmittingOrderAvailable()) {
            return codeEmittingOrder;
        } else {
            return linearScanOrder;
        }
    }

    public void setHasArgInCallerFrame() {
        hasArgInCallerFrame = true;
    }

    /**
     * Determines if any of the parameters to the method are passed via the stack where the
     * parameters are located in the caller's frame.
     */
    public boolean hasArgInCallerFrame() {
        return hasArgInCallerFrame;
    }

    /**
     * Gets the next non-{@code null} block in a list.
     *
     * @param blocks list of blocks
     * @param blockIndex index of the current block
     * @return the next block in the list that is none {@code null} or {@code null} if there is no
     *         such block
     */
    public static BasicBlock<?> getNextBlock(AbstractControlFlowGraph<?> cfg, int[] blocks, int blockIndex) {
        for (int nextIndex = blockIndex + 1; nextIndex > 0 && nextIndex < blocks.length; nextIndex++) {
            int nextBlock = blocks[nextIndex];
            if (nextBlock != AbstractControlFlowGraph.INVALID_BLOCK_ID) {
                return cfg.getBlocks()[nextBlock];
            }
        }
        return null;
    }

    /**
     * Gets the exception edge (if any) originating at a given operation.
     */
    public static LabelRef getExceptionEdge(LIRInstruction op) {
        final LabelRef[] exceptionEdge = {null};
        op.forEachState(state -> {
            if (state.exceptionEdge != null) {
                assert exceptionEdge[0] == null;
                exceptionEdge[0] = state.exceptionEdge;
            }
        });
        return exceptionEdge[0];
    }

    /**
     * The maximum distance an operation with an {@linkplain #getExceptionEdge(LIRInstruction)
     * exception edge} can be from the last instruction of a LIR block. The value of 3 is based on a
     * non-void call operation that has an exception edge. Such a call may move the result to
     * another register and then spill it.
     * <p>
     * The rationale for such a constant is to limit the search for an insertion point when adding
     * move operations at the end of a block. Such moves must be inserted before all control flow
     * instructions.
     */
    public static final int MAX_EXCEPTION_EDGE_OP_DISTANCE_FROM_END = 3;

    public static boolean verifyBlock(LIR lir, BasicBlock<?> block) {
        ArrayList<LIRInstruction> ops = lir.getLIRforBlock(block);
        if (ops.size() == 0) {
            return false;
        }
        assert ops.get(0) instanceof LabelOp : String.format("Not a Label %s (Block %s)", ops.get(0).getClass(), block);
        LIRInstruction opWithExceptionEdge = null;
        int index = 0;
        int lastIndex = ops.size() - 1;
        for (LIRInstruction op : ops.subList(0, lastIndex)) {
            assert !(op instanceof BlockEndOp) : String.format("BlockEndOp %s (Block %s)", op.getClass(), block);
            LabelRef exceptionEdge = getExceptionEdge(op);
            if (exceptionEdge != null) {
                assert opWithExceptionEdge == null : "multiple ops with an exception edge not allowed";
                opWithExceptionEdge = op;
                int distanceFromEnd = lastIndex - index;
                assert distanceFromEnd <= MAX_EXCEPTION_EDGE_OP_DISTANCE_FROM_END : distanceFromEnd;
            }
            index++;
        }
        LIRInstruction end = ops.get(lastIndex);
        assert end instanceof BlockEndOp : String.format("Not a BlockEndOp %s (Block %s)", end.getClass(), block);
        return true;
    }

    @SuppressWarnings("unlikely-arg-type")
    public static boolean verifyBlocks(LIR lir, int[] blocks) {
        for (int blockId : blocks) {
            if (blockId == AbstractControlFlowGraph.INVALID_BLOCK_ID) {
                continue;
            }
            BasicBlock<?> block = lir.getBlockById(blockId);
            for (int i = 0; i < block.getSuccessorCount(); i++) {
                BasicBlock<?> sux = block.getSuccessorAt(i);
                assert contains(blocks, sux.getId()) : "missing successor from: " + block + "to: " + sux;
            }
            for (int i = 0; i < block.getPredecessorCount(); i++) {
                BasicBlock<?> pred = block.getPredecessorAt(i);
                assert contains(blocks, pred.getId()) : "missing predecessor from: " + block + "to: " + pred;
            }
            if (!verifyBlock(lir, block)) {
                return false;
            }
        }
        return true;
    }

    private static boolean contains(int[] blockIndices, int key) {
        for (int index : blockIndices) {
            if (index == key) {
                return true;
            }
        }
        return false;
    }

    public void resetLabels() {
        for (int b : getBlocks()) {
            BasicBlock<?> block = cfg.getBlocks()[b];
            if (block == null) {
                continue;
            }
            for (LIRInstruction inst : lirInstructions.get(block)) {
                if (inst instanceof LabelHoldingOp) {
                    Label label = ((LabelHoldingOp) inst).getLabel();
                    if (label != null) {
                        label.reset();
                    }
                }
            }
        }
        if (slowPaths != null) {
            slowPaths.clear();
        }
    }
}
