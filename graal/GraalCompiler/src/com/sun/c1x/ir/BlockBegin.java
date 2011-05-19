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
import com.oracle.max.asm.*;
import com.sun.c1x.*;
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
public final class BlockBegin extends StateSplit {

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
     * The last node in the block (which contains the successors).
     */
    public BlockEnd end() {
        return (BlockEnd) successors().get(super.successorCount() + SUCCESSOR_END);
    }

    @Override
    public boolean needsStateAfter() {
        return false;
    }

    public void setEnd(BlockEnd end) {
        assert end != null;
        successors().set(super.successorCount() + SUCCESSOR_END, end);
    }

    private static final List<BlockBegin> NO_HANDLERS = Collections.emptyList();

    /**
     * An enumeration of flags for block entries indicating various things.
     */
    public enum BlockFlag {
        ParserLoopHeader,
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

    private int depthFirstNumber;
    private int linearScanNumber;

    private BlockBegin dominator;

    // LIR block
    private LIRBlock lirBlock;

    public void setLIRBlock(LIRBlock block) {
        this.lirBlock = block;
    }

    /**
     * Index of bytecode that generated this node when appended in a basic block.
     * Negative values indicate special cases.
     */
    private int bci;

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
        setBCI(bci);
    }

    /**
     * Gets the list of predecessors of this block.
     * @return the predecessor list
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public List<Instruction> blockPredecessors() {
        if (predecessors().size() == 1 && predecessors().get(0) == graph().root()) {
            return Collections.EMPTY_LIST;
        } else {
            return (List) Collections.unmodifiableList(predecessors());
        }
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

    public void setDepthFirstNumber(int depthFirstNumber) {
        assert depthFirstNumber >= 0;
        this.depthFirstNumber = depthFirstNumber;
    }

    public void setLinearScanNumber(int linearScanNumber) {
        this.linearScanNumber = linearScanNumber;
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

            Instruction inst = block;
            ArrayList<BlockBegin> excBlocks = new ArrayList<BlockBegin>();
            while (inst != null) {
                if (inst instanceof ExceptionEdgeInstruction) {
                    excBlocks.add(((ExceptionEdgeInstruction) inst).exceptionEdge());
                }
                inst = inst.next();
            }
            while (excBlocks.remove(null)) {
                // nothing
            }
            if (excBlocks.size() > 0) {
                queueBlocks(queue, excBlocks, mark);
            }

            queueBlocks(queue, block.end().blockSuccessors(), mark);
            if (predecessors) {
                queueBlockEnds(queue, block.blockPredecessors(), mark);
            }
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

    private void queueBlockEnds(LinkedList<BlockBegin> queue, List<Instruction> list, IdentityHashMap<BlockBegin, BlockBegin> mark) {
        if (list != null) {
            for (Instruction end : list) {
                BlockBegin b = end.block();
                if (!mark.containsKey(b)) {
                    queue.offer(b);
                    mark.put(b, b);
                }
            }
        }
    }

    /**
     * Gets the bytecode index of this instruction.
     * @return the bytecode index of this instruction
     */
    public int bci() {
        return bci;
    }

    /**
     * Sets the bytecode index of this instruction.
     * @param bci the new bytecode index for this instruction
     */
    public void setBCI(int bci) {
        this.bci = bci;
    }

    private void iterate(IdentityHashMap<BlockBegin, BlockBegin> mark, BlockClosure closure) {
        if (!mark.containsKey(this)) {
            mark.put(this, this);
            closure.apply(this);
            BlockEnd e = end();

            Instruction inst = this;
            ArrayList<BlockBegin> excBlocks = new ArrayList<BlockBegin>();
            while (inst != null) {
                if (inst instanceof Invoke) {
                    excBlocks.add(((Invoke) inst).exceptionEdge());
                } else if (inst instanceof Throw) {
                    excBlocks.add(((Throw) inst).exceptionEdge());
                }
                inst = inst.next();
            }
            while (excBlocks.remove(null)) {
                // nothing
            }
            if (excBlocks.size() > 0) {
                iterateReverse(mark, closure, excBlocks);
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

    @Override
    public void accept(ValueVisitor v) {
        v.visitBlockBegin(this);
    }

    public void mergeOrClone(FrameStateAccess newState, RiMethod method) {
        FrameState existingState = stateBefore();

        if (existingState == null) {
            // copy state because it is modified
            FrameState duplicate = newState.duplicate(bci());

            if (C1XOptions.UseStackMapTableLiveness && method != null) {
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

    public boolean isParserLoopHeader() {
        return checkBlockFlag(BlockFlag.ParserLoopHeader);
    }

    public void setParserLoopHeader(boolean value) {
        setBlockFlag(BlockFlag.ParserLoopHeader, value);
    }

    public boolean isLinearScanLoopHeader() {
        return checkBlockFlag(BlockFlag.LinearScanLoopHeader);
    }

    public boolean isLinearScanLoopEnd() {
        return checkBlockFlag(BlockFlag.LinearScanLoopEnd);
    }

    private void setBlockFlag(BlockFlag flag, boolean value) {
        if (value) {
            setBlockFlag(flag);
        } else {
            clearBlockFlag(flag);
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("block #");
        builder.append(blockID);
        builder.append(",");
        builder.append(depthFirstNumber);
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
        // ignore the graph root
        if (predecessors().size() == 1 && predecessors().get(0) == graph().root()) {
            return 0;
        } else {
            return predecessors().size();
        }
    }

    /**
     * @return the label associated with the block, used by the LIR
     */
    public Label label() {
        return lirBlock().label;
    }

    public LIRList lir() {
        return lirBlock().lir();
    }

    public LIRBlock lirBlock() {
        return lirBlock;
    }

    public Instruction predAt(int j) {
        return (Instruction) predecessors().get(j);
    }


    public boolean isPredecessor(Instruction block) {
        return predecessors().contains(block);
    }

    public void printWithoutPhis(LogStream out) {
        // print block id
        BlockEnd end = end();
        out.print("B").print(blockID).print(" ");

        // print flags
        StringBuilder sb = new StringBuilder(8);
        if (isParserLoopHeader()) {
            sb.append("LH");
        }
        if (sb.length() != 0) {
            out.print('(').print(sb.toString()).print(')');
        }

        // print block bci range
        out.print('[').print(-1).print(", ").print(-1).print(']');

        // print block successors
        if (end != null && end.blockSuccessors().size() > 0) {
            out.print(" .");
            for (BlockBegin successor : end.blockSuccessors()) {
                out.print(" B").print(successor.blockID);
            }
        }

        // print predecessors
        if (!blockPredecessors().isEmpty()) {
            out.print(" pred:");
            for (Instruction pred : blockPredecessors()) {
                out.print(" B").print(pred.block().blockID);
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

    @Override
    public String shortName() {
        return "BlockBegin #" + blockID;
    }

    /**
     * Iterates over all successors of this block: successors of the end node and exception handler.
     */
    public void allSuccessorsDo(boolean backwards, BlockClosure closure) {
        if (backwards) {
            for (int i = numberOfSux() - 1; i >= 0; i--) {
                closure.apply(suxAt(i));
            }
        } else {
            for (int i = 0; i < numberOfSux(); i++) {
                closure.apply(suxAt(i));
            }
        }
        for (Instruction x = next(); x != null; x = x.next()) {
            if (x instanceof ExceptionEdgeInstruction && ((ExceptionEdgeInstruction) x).exceptionEdge() != null) {
                closure.apply(((ExceptionEdgeInstruction) x).exceptionEdge());
            }
        }
    }


}
