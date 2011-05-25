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
import com.sun.c1x.debug.*;
import com.sun.c1x.util.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * Denotes the beginning of a basic block, and holds information
 * about the basic block, including the successor and
 * predecessor blocks, exception handlers, liveness information, etc.
 */
public final class BlockBegin extends StateSplit {

    private static final int INPUT_COUNT = 0;

    private static final int SUCCESSOR_COUNT = 0;

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
        Instruction next = next();
        while (!(next instanceof BlockEnd)) {
            next = next.next();
        }
        return (BlockEnd) next;
    }

    @Override
    public boolean needsStateAfter() {
        return false;
    }

    /**
     * A unique id used in tracing.
     */
    public final int blockID;

    public final boolean isLoopHeader;
    private int linearScanNumber;

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
    public BlockBegin(int bci, int blockID, boolean isLoopHeader, Graph graph) {
        super(CiKind.Illegal, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        this.blockID = blockID;
        linearScanNumber = -1;
        this.bci = bci;
        this.isLoopHeader = isLoopHeader;
    }

    /**
     * Gets the linear scan number of this block.
     * @return the linear scan number
     */
    public int linearScanNumber() {
        return linearScanNumber;
    }

    public void setLinearScanNumber(int linearScanNumber) {
        this.linearScanNumber = linearScanNumber;
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
     * Gets the bytecode index of this instruction.
     * @return the bytecode index of this instruction
     */
    public int bci() {
        return bci;
    }

    private void iterate(IdentityHashMap<BlockBegin, BlockBegin> mark, BlockClosure closure) {
        if (!mark.containsKey(this)) {
            mark.put(this, this);
            closure.apply(this);
            BlockEnd e = end();

            Instruction inst = this;
            ArrayList<BlockBegin> excBlocks = new ArrayList<BlockBegin>();
            while (inst != null) {
                if (inst instanceof ExceptionEdgeInstruction) {
                    excBlocks.add((BlockBegin) ((ExceptionEdgeInstruction) inst).exceptionEdge());
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

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("block #");
        builder.append(blockID);
        builder.append(",");
        builder.append(blockID); // was: depthFirstNumber
        builder.append(" [");

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

    public void printWithoutPhis(LogStream out) {
        // print block id
        BlockEnd end = end();
        out.print("B").print(blockID).print(" ");

        // print flags
        StringBuilder sb = new StringBuilder(8);
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
     * block, its {@linkplain Phi#valueCount() inputs} are appended to the returned string.
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
                for (int j = 0; j < phi.valueCount(); j++) {
                    sb.append(' ');
                    Value operand = phi.valueAt(j);
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
}
