/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.c1x.ir;

import java.util.*;

import com.oracle.graal.graph.*;
import com.sun.c1x.*;
import com.sun.c1x.asm.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.lir.*;
import com.sun.c1x.util.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;
import com.sun.cri.ri.*;

/**
 * Denotes the beginning of a basic block, and holds information
 * about the basic block, including the successor and
 * predecessor blocks, exception handlers, liveness information, etc.
 */
public final class BlockBegin extends Instruction {

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_STATE_BEFORE = 0;

    private static final int SUCCESSOR_COUNT = 1;
    private static final int SUCCESSOR_END = 0;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    /**
     * The frame state before execution of the first instruction in this block.
     */
    public FrameState stateBefore() {
        return (FrameState) inputs().get(super.inputCount() + INPUT_STATE_BEFORE);
    }

    public FrameState setStateBefore(FrameState n) {
        assert stateBefore() == null;
        return (FrameState) inputs().set(super.inputCount() + INPUT_STATE_BEFORE, n);
    }

    /**
     * The last node in the block (which contains the successors).
     */
    public BlockEnd end() {
        return (BlockEnd) successors().get(super.successorCount() + SUCCESSOR_END);
    }

    public void setEnd(BlockEnd end) {
        assert end != null;
        BlockEnd old = this.end();
        if (old != end) {
            if (old != null) {
                // disconnect this block from the old end
                old.setBegin(null);
                // disconnect this block from its current successors
                for (BlockBegin s : old.blockSuccessors()) {
                    s.blockPredecessors().remove(this);
                }
            }
            successors().set(super.successorCount() + SUCCESSOR_END, end);
            end.setBegin(this);
            for (BlockBegin s : end.blockSuccessors()) {
                s.addPredecessor(this);
            }
        }
    }

    private static final List<BlockBegin> NO_HANDLERS = Collections.emptyList();

    /**
     * An enumeration of flags for block entries indicating various things.
     */
    public enum BlockFlag {
        StandardEntry,
        ExceptionEntry,
        SubroutineEntry,
        BackwardBranchTarget,
        IsOnWorkList,
        WasVisited,
        DefaultExceptionHandler,
        ParserLoopHeader,
        CriticalEdgeSplit,
        LinearScanLoopHeader,
        LinearScanLoopEnd;

        public final int mask = 1 << ordinal();
    }

    /**
     * A unique id used in tracing.
     */
    public final int blockID;

    /**
     * Denotes the current set of {@link BlockBegin.BlockFlag} settings.
     */
    private int blockFlags;

    private FrameState stateAfter;

    /**
     * The {@link BlockBegin} nodes for which this node is a successor.
     */
    private final List<BlockBegin> predecessors;

    private int depthFirstNumber;
    private int linearScanNumber;
    private int loopDepth;
    private int loopIndex;

    private BlockBegin dominator;
    private List<BlockBegin> exceptionHandlerBlocks;
    private List<FrameState> exceptionHandlerStates;

    // LIR block
    public LIRBlock lirBlock;

    /**
     * Constructs a new BlockBegin at the specified bytecode index.
     * @param bci the bytecode index of the start
     * @param blockID the ID of the block
     * @param graph
     */
    public BlockBegin(int bci, int blockID, Graph graph) {
        super(CiKind.Illegal, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        this.blockID = blockID;
        depthFirstNumber = -1;
        linearScanNumber = -1;
        predecessors = new ArrayList<BlockBegin>(2);
        loopIndex = -1;
        setBCI(bci);
    }

    /**
     * Gets the list of predecessors of this block.
     * @return the predecessor list
     */
    public List<BlockBegin> blockPredecessors() {
        return predecessors;
    }

    /**
     * Gets the dominator of this block.
     * @return the dominator block
     */
    public BlockBegin dominator() {
        return dominator;
    }

    /**
     * Sets the dominator block for this block.
     * @param dominator the dominator for this block
     */
    public void setDominator(BlockBegin dominator) {
        this.dominator = dominator;
    }

    /**
     * Gets the depth first traversal number of this block.
     * @return the depth first number
     */
    public int depthFirstNumber() {
        return depthFirstNumber;
    }

    /**
     * Gets the linear scan number of this block.
     * @return the linear scan number
     */
    public int linearScanNumber() {
        return linearScanNumber;
    }

    /**
     * Gets the loop depth of this block.
     * @return the loop depth
     */
    public int loopDepth() {
        return loopDepth;
    }

    /**
     * Gets the loop index of this block.
     * @return the loop index
     */
    public int loopIndex() {
        return loopIndex;
    }

    public void setStateAfter(FrameState stateAfter) {
        this.stateAfter = stateAfter;
    }

    @Override
    public FrameState stateAfter() {
        return stateAfter;
    }

    /**
     * Gets the exception handlers that cover one or more instructions of this basic block.
     *
     * @return the exception handlers
     */
    public List<BlockBegin> exceptionHandlerBlocks() {
        return exceptionHandlerBlocks == null ? NO_HANDLERS : exceptionHandlerBlocks;
    }

    public List<FrameState> exceptionHandlerStates() {
        return exceptionHandlerStates;
    }

    public void setDepthFirstNumber(int depthFirstNumber) {
        assert depthFirstNumber >= 0;
        this.depthFirstNumber = depthFirstNumber;
    }

    public void setLinearScanNumber(int linearScanNumber) {
        this.linearScanNumber = linearScanNumber;
    }

    public void setLoopDepth(int loopDepth) {
        this.loopDepth = loopDepth;
    }

    public void setLoopIndex(int loopIndex) {
        this.loopIndex = loopIndex;
    }

    /**
     * Set a flag on this block.
     * @param flag the flag to set
     */
    public void setBlockFlag(BlockFlag flag) {
        blockFlags |= flag.mask;
    }

    /**
     * Clear a flag on this block.
     * @param flag the flag to clear
     */
    public void clearBlockFlag(BlockFlag flag) {
        blockFlags &= ~flag.mask;
    }

    public void copyBlockFlag(BlockBegin other, BlockFlag flag) {
        setBlockFlag(flag, other.checkBlockFlag(flag));
    }

    /**
     * Check whether this block has the specified flag set.
     * @param flag the flag to test
     * @return {@code true} if this block has the flag
     */
    public boolean checkBlockFlag(BlockFlag flag) {
        return (blockFlags & flag.mask) != 0;
    }

    /**
     * Iterate over this block, its exception handlers, and its successors, in that order.
     * @param closure the closure to apply to each block
     */
    public void iteratePreOrder(BlockClosure closure) {
        // XXX: identity hash map might be too slow, consider a boolean array or a mark field
        iterate(new IdentityHashMap<BlockBegin, BlockBegin>(), closure);
    }

    /**
     * Iterate over all blocks transitively reachable from this block.
     * @param closure the closure to apply to each block
     * @param predecessors {@code true} if also to include this blocks predecessors
     */
    public void iterateAnyOrder(BlockClosure closure, boolean predecessors) {
        IdentityHashMap<BlockBegin, BlockBegin> mark = new IdentityHashMap<BlockBegin, BlockBegin>();
        LinkedList<BlockBegin> queue = new LinkedList<BlockBegin>();
        queue.offer(this);
        mark.put(this, this);
        BlockBegin block;
        while ((block = queue.poll()) != null) {
            closure.apply(block);
            queueBlocks(queue, block.exceptionHandlerBlocks(), mark);
            queueBlocks(queue, block.end().blockSuccessors(), mark);
            queueBlocks(queue, predecessors ? block.predecessors : null, mark);
        }
    }

    private void queueBlocks(LinkedList<BlockBegin> queue, List<BlockBegin> list, IdentityHashMap<BlockBegin, BlockBegin> mark) {
        if (list != null) {
            for (BlockBegin b : list) {
                if (!mark.containsKey(b)) {
                    queue.offer(b);
                    mark.put(b, b);
                }
            }
        }
    }

    private void iterate(IdentityHashMap<BlockBegin, BlockBegin> mark, BlockClosure closure) {
        if (!mark.containsKey(this)) {
            mark.put(this, this);
            closure.apply(this);
            BlockEnd e = end();
            if (exceptionHandlerBlocks != null) {
                iterateReverse(mark, closure, exceptionHandlerBlocks);
            }
            assert e != null : "block must have block end";
            iterateReverse(mark, closure, e.blockSuccessors());
        }
    }

    private void iterateReverse(IdentityHashMap<BlockBegin, BlockBegin> mark, BlockClosure closure, List<BlockBegin> list) {
        for (int i = list.size() - 1; i >= 0; i--) {
            list.get(i).iterate(mark, closure);
        }
    }

    /**
     * Adds an exception handler that covers one or more instructions in this block.
     *
     * @param handler the entry block for the exception handler to add
     */
    public void addExceptionHandler(BlockBegin handler) {
        assert handler != null && handler.checkBlockFlag(BlockBegin.BlockFlag.ExceptionEntry);
        if (exceptionHandlerBlocks == null) {
            exceptionHandlerBlocks = new ArrayList<BlockBegin>(3);
            exceptionHandlerBlocks.add(handler);
        } else if (!exceptionHandlerBlocks.contains(handler)) {
            exceptionHandlerBlocks.add(handler);
        }
    }

    /**
     * Adds a frame state that merges into the exception handler whose entry is this block.
     *
     * @param state the frame state at an instruction that raises an exception that can be caught by the exception
     *            handler represented by this block
     * @return the index of {@code state} in the list of frame states merging at this block (i.e. the frames states for
     *         all instruction throwing an exception caught by this exception handler)
     */
    public int addExceptionState(FrameState state) {
        assert checkBlockFlag(BlockBegin.BlockFlag.ExceptionEntry);
        if (exceptionHandlerStates == null) {
            exceptionHandlerStates = new ArrayList<FrameState>(4);
        }
        exceptionHandlerStates.add(state);
        return exceptionHandlerStates.size() - 1;
    }

    /**
     * Add a predecessor to this block.
     * @param pred the predecessor to add
     */
    public void addPredecessor(BlockBegin pred) {
        predecessors.add(pred);
    }

    /**
     * Removes all occurrences of the specified block from the predecessor list of this block.
     * @param pred the predecessor to remove
     */
    public void removePredecessor(BlockBegin pred) {
        while (predecessors.remove(pred)) {
            // the block may appear multiple times in the list
            // XXX: this is not very efficient, consider Util.removeAllFromList
        }
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitBlockBegin(this);
    }

    public void mergeOrClone(FrameStateAccess newState, RiMethod method) {
        FrameState existingState = stateBefore();

        if (existingState == null) {
            // this is the first state for the block
            if (wasVisited()) {
                // this can happen for complex jsr/ret patterns; just bail out
                throw new CiBailout("jsr/ret too complex");
            }

            // copy state because it is modified
            FrameState duplicate = newState.duplicate();

            if (C1XOptions.UseStackMapTableLiveness) {
                // if a liveness map is available, use it to invalidate dead locals
                CiBitMap[] livenessMap = method.livenessMap();
                if (livenessMap != null && bci() >= 0) {
                    assert bci() < livenessMap.length;
                    CiBitMap liveness = livenessMap[bci()];
                    if (liveness != null) {
                        invalidateDeadLocals(duplicate, liveness);
                    }
                }
            }

            // if the block is a loop header, insert all necessary phis
            if (isParserLoopHeader()) {
                insertLoopPhis(duplicate);
            }

            setStateBefore(duplicate);
        } else {
            if (!C1XOptions.AssumeVerifiedBytecode && !existingState.isCompatibleWith(newState)) {
                // stacks or locks do not match--bytecodes would not verify
                throw new CiBailout("stack or locks do not match");
            }

            assert existingState.localsSize() == newState.localsSize();
            assert existingState.stackSize() == newState.stackSize();

            if (wasVisited() && !isParserLoopHeader()) {
                throw new CiBailout("jsr/ret too complicated");
            }

            existingState.merge(this, newState);
        }
    }

    private void invalidateDeadLocals(FrameState newState, CiBitMap liveness) {
        int max = newState.localsSize();
        assert max <= liveness.size();
        for (int i = 0; i < max; i++) {
            Value x = newState.localAt(i);
            if (x != null) {
                if (!liveness.get(i)) {
                    // invalidate the local if it is not live
                    newState.invalidateLocal(i);
                }
            }
        }
    }

    private void insertLoopPhis(FrameState newState) {
        int stackSize = newState.stackSize();
        for (int i = 0; i < stackSize; i++) {
            // always insert phis for the stack
            newState.setupPhiForStack(this, i);
        }
        int localsSize = newState.localsSize();
        for (int i = 0; i < localsSize; i++) {
            Value x = newState.localAt(i);
            if (x != null) {
                newState.setupPhiForLocal(this, i);
            }
        }
    }

    public boolean isStandardEntry() {
        return checkBlockFlag(BlockFlag.StandardEntry);
    }

    public void setStandardEntry() {
        setBlockFlag(BlockFlag.StandardEntry);
    }

    public boolean isBackwardBranchTarget() {
        return checkBlockFlag(BlockFlag.BackwardBranchTarget);
    }

    public void setBackwardBranchTarget(boolean value) {
        setBlockFlag(BlockFlag.BackwardBranchTarget, value);
    }

    public boolean isCriticalEdgeSplit() {
        return checkBlockFlag(BlockFlag.CriticalEdgeSplit);
    }

    public void setCriticalEdgeSplit(boolean value) {
        setBlockFlag(BlockFlag.CriticalEdgeSplit, value);
    }

    public boolean isExceptionEntry() {
        return checkBlockFlag(BlockFlag.ExceptionEntry);
    }

    public void setExceptionEntry() {
        setBlockFlag(BlockFlag.ExceptionEntry);
    }

    public boolean isSubroutineEntry() {
        return checkBlockFlag(BlockFlag.SubroutineEntry);
    }

    public void setSubroutineEntry() {
        setBlockFlag(BlockFlag.SubroutineEntry);
    }

    public boolean isOnWorkList() {
        return checkBlockFlag(BlockFlag.IsOnWorkList);
    }

    public void setOnWorkList(boolean value) {
        setBlockFlag(BlockFlag.IsOnWorkList, value);
    }

    public boolean wasVisited() {
        return checkBlockFlag(BlockFlag.WasVisited);
    }

    public void setWasVisited(boolean value) {
        setBlockFlag(BlockFlag.WasVisited, value);
    }

    public boolean isParserLoopHeader() {
        return checkBlockFlag(BlockFlag.ParserLoopHeader);
    }

    public void setParserLoopHeader(boolean value) {
        setBlockFlag(BlockFlag.ParserLoopHeader, value);
    }

    public boolean isLinearScanLoopHeader() {
        return checkBlockFlag(BlockFlag.LinearScanLoopHeader);
    }

    public void setLinearScanLoopHeader(boolean value) {
        setBlockFlag(BlockFlag.LinearScanLoopHeader, value);
    }

    public boolean isLinearScanLoopEnd() {
        return checkBlockFlag(BlockFlag.LinearScanLoopEnd);
    }

    public void setLinearScanLoopEnd(boolean value) {
        setBlockFlag(BlockFlag.LinearScanLoopEnd, value);
    }

    private void setBlockFlag(BlockFlag flag, boolean value) {
        if (value) {
            setBlockFlag(flag);
        } else {
            clearBlockFlag(flag);
        }
    }

    public void copyBlockFlags(BlockBegin other) {
        copyBlockFlag(other, BlockBegin.BlockFlag.ParserLoopHeader);
        copyBlockFlag(other, BlockBegin.BlockFlag.SubroutineEntry);
        copyBlockFlag(other, BlockBegin.BlockFlag.ExceptionEntry);
        copyBlockFlag(other, BlockBegin.BlockFlag.WasVisited);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("block #");
        builder.append(blockID);
        builder.append(",");
        builder.append(depthFirstNumber);
        builder.append(" @ ");
        builder.append(bci());
        builder.append(" [");
        boolean hasFlag = false;
        for (BlockFlag f : BlockFlag.values()) {
            if (checkBlockFlag(f)) {
                if (hasFlag) {
                    builder.append(' ');
                }
                builder.append(f.name());
                hasFlag = true;
            }
        }

        builder.append("]");
        if (end() != null) {
            builder.append(" -> ");
            boolean hasSucc = false;
            for (BlockBegin s : end().blockSuccessors()) {
                if (hasSucc) {
                    builder.append(", ");
                }
                builder.append("#");
                builder.append(s.blockID);
                hasSucc = true;
            }
        }
        return builder.toString();
    }

    /**
     * Get the number of successors.
     * @return the number of successors
     */
    public int numberOfSux() {
        return end().blockSuccessorCount();
    }

    /**
     * Get the successor at a certain position.
     * @param i the position
     * @return the successor
     */
    public BlockBegin suxAt(int i) {
        return end().blockSuccessor(i);
    }

    /**
     * Get the number of predecessors.
     * @return the number of predecessors
     */
    public int numberOfPreds() {
        return predecessors.size();
    }

    /**
     * @return the label associated with the block, used by the LIR
     */
    public Label label() {
        return lirBlock().label;
    }

    public void setLir(LIRList lir) {
        lirBlock().setLir(lir);
    }

    public LIRList lir() {
        return lirBlock().lir();
    }

    public LIRBlock lirBlock() {
        if (lirBlock == null) {
            lirBlock = new LIRBlock();
        }
        return lirBlock;
    }

    public int exceptionHandlerPco() {
        return lirBlock == null ? 0 : lirBlock.exceptionHandlerPCO;
    }

    public void setExceptionHandlerPco(int codeOffset) {
        lirBlock().exceptionHandlerPCO = codeOffset;
    }

    public int numberOfExceptionHandlers() {
        return exceptionHandlerBlocks == null ? 0 : exceptionHandlerBlocks.size();
    }

    public BlockBegin exceptionHandlerAt(int i) {
        return exceptionHandlerBlocks.get(i);
    }

    public BlockBegin predAt(int j) {
        return predecessors.get(j);
    }

    public int firstLirInstructionId() {
        return lirBlock.firstLirInstructionID;
    }

    public void setFirstLirInstructionId(int firstLirInstructionId) {
        lirBlock.firstLirInstructionID = firstLirInstructionId;
    }

    public int lastLirInstructionId() {
        return lirBlock.lastLirInstructionID;
    }

    public void setLastLirInstructionId(int lastLirInstructionId) {
        lirBlock.lastLirInstructionID = lastLirInstructionId;
    }

    public boolean isPredecessor(BlockBegin block) {
        return this.predecessors.contains(block);
    }

    public void printWithoutPhis(LogStream out) {
        // print block id
        BlockEnd end = end();
        out.print("B").print(blockID).print(" ");

        // print flags
        StringBuilder sb = new StringBuilder(8);
        if (isStandardEntry()) {
            sb.append('S');
        }
        if (isExceptionEntry()) {
            sb.append('E');
        }
        if (isSubroutineEntry()) {
            sb.append('s');
        }
        if (isParserLoopHeader()) {
            sb.append("LH");
        }
        if (isBackwardBranchTarget()) {
            sb.append('b');
        }
        if (wasVisited()) {
            sb.append('V');
        }
        if (sb.length() != 0) {
            out.print('(').print(sb.toString()).print(')');
        }

        // print block bci range
        out.print('[').print(bci()).print(", ").print(end == null ? -1 : end.bci()).print(']');

        // print block successors
        if (end != null && end.blockSuccessors().size() > 0) {
            out.print(" .");
            for (BlockBegin successor : end.blockSuccessors()) {
                out.print(" B").print(successor.blockID);
            }
        }
        // print exception handlers
        if (!exceptionHandlers().isEmpty()) {
            out.print(" (xhandlers");
            for (BlockBegin handler : exceptionHandlerBlocks()) {
                out.print(" B").print(handler.blockID);
            }
            out.print(')');
        }

        // print dominator block
        if (dominator() != null) {
            out.print(" dom B").print(dominator().blockID);
        }

        // print predecessors
        if (!blockPredecessors().isEmpty()) {
            out.print(" pred:");
            for (BlockBegin pred : blockPredecessors()) {
                out.print(" B").print(pred.blockID);
            }
        }
    }

    @Override
    public void print(LogStream out) {

        printWithoutPhis(out);

        // print phi functions
        boolean hasPhisInLocals = false;
        boolean hasPhisOnStack = false;

        if (end() != null && end().stateAfter() != null) {
            FrameState state = stateBefore();

            int i = 0;
            while (!hasPhisOnStack && i < state.stackSize()) {
                Value value = state.stackAt(i);
                hasPhisOnStack = isPhiAtBlock(value);
                if (value != null && !value.isIllegal()) {
                    i += value.kind.sizeInSlots();
                } else {
                    i++;
                }
            }

            for (i = 0; !hasPhisInLocals && i < state.localsSize();) {
                Value value = state.localAt(i);
                hasPhisInLocals = isPhiAtBlock(value);
                // also ignore illegal HiWords
                if (value != null && !value.isIllegal()) {
                    i += value.kind.sizeInSlots();
                } else {
                    i++;
                }
            }
        }

        // print values in locals
        if (hasPhisInLocals) {
            out.println();
            out.println("Locals:");

            FrameState state = stateBefore();
            int i = 0;
            while (i < state.localsSize()) {
                Value value = state.localAt(i);
                if (value != null) {
                    out.println(stateString(i, value));
                    // also ignore illegal HiWords
                    i += value.isIllegal() ? 1 : value.kind.sizeInSlots();
                } else {
                    i++;
                }
            }
            out.println();
        }

        // print values on stack
        if (hasPhisOnStack) {
            out.println();
            out.println("Stack:");
            int i = 0;
            while (i < stateBefore().stackSize()) {
                Value value = stateBefore().stackAt(i);
                if (value != null) {
                    out.println(stateString(i, value));
                    i += value.kind.sizeInSlots();
                } else {
                    i++;
                }
            }
        }

    }



    /**
     * Determines if a given instruction is a phi whose {@linkplain Phi#block() join block} is a given block.
     *
     * @param value the instruction to test
     * @param block the block that may be the join block of {@code value} if {@code value} is a phi
     * @return {@code true} if {@code value} is a phi and its join block is {@code block}
     */
    private boolean isPhiAtBlock(Value value) {
        return value instanceof Phi && ((Phi) value).block() == this;
    }


    /**
     * Formats a given instruction as a value in a {@linkplain FrameState frame state}. If the instruction is a phi defined at a given
     * block, its {@linkplain Phi#phiInputCount() inputs} are appended to the returned string.
     *
     * @param index the index of the value in the frame state
     * @param value the frame state value
     * @param block if {@code value} is a phi, then its inputs are formatted if {@code block} is its
     *            {@linkplain Phi#block() join point}
     * @return the instruction representation as a string
     */
    public String stateString(int index, Value value) {
        StringBuilder sb = new StringBuilder(30);
        sb.append(String.format("%2d  %s", index, Util.valueString(value)));
        if (value instanceof Phi) {
            Phi phi = (Phi) value;
            // print phi operands
            if (phi.block() == this) {
                sb.append(" [");
                for (int j = 0; j < phi.phiInputCount(); j++) {
                    sb.append(' ');
                    Value operand = phi.inputAt(j);
                    if (operand != null) {
                        sb.append(Util.valueString(operand));
                    } else {
                        sb.append("NULL");
                    }
                }
                sb.append("] ");
            }
        }
        if (value != null && value.hasSubst()) {
            sb.append("alias ").append(Util.valueString(value.subst()));
        }
        return sb.toString();
    }
}
