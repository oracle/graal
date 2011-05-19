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
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code BlockEnd} instruction is a base class for all instructions that end a basic
 * block, including branches, switches, throws, and goto's.
 */
public abstract class BlockEnd extends Instruction {

    private static final int INPUT_COUNT = 0;

    private static final int SUCCESSOR_COUNT = 1;
    private static final int SUCCESSOR_STATE_AFTER = 0;
    private final int blockSuccessorCount;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + blockSuccessorCount + SUCCESSOR_COUNT;
    }

    /**
     * The state for this instruction.
     */
     @Override
    public FrameState stateAfter() {
        return (FrameState) successors().get(super.successorCount() + SUCCESSOR_STATE_AFTER);
    }

    public FrameState setStateAfter(FrameState n) {
        return (FrameState) successors().set(super.successorCount() + SUCCESSOR_STATE_AFTER, n);
    }

    /**
     * The list of instructions that produce input for this instruction.
     */
    public BlockBegin blockSuccessor(int index) {
        assert index >= 0 && index < blockSuccessorCount;
        return (BlockBegin) successors().get(super.successorCount() + SUCCESSOR_COUNT + index);
    }

    public BlockBegin setBlockSuccessor(int index, BlockBegin n) {
        assert index >= 0 && index < blockSuccessorCount;
        return (BlockBegin) successors().set(super.successorCount() + SUCCESSOR_COUNT + index, n);
    }

    public int blockSuccessorCount() {
        return blockSuccessorCount;
    }

    private boolean isSafepoint;

    /**
     * Constructs a new block end with the specified value type.
     * @param kind the type of the value produced by this instruction
     * @param stateAfter the frame state at the end of this block
     * @param isSafepoint {@code true} if this instruction is a safepoint instruction
     * @param successors the list of successor blocks. If {@code null}, a new one will be created.
     */
    public BlockEnd(CiKind kind, FrameState stateAfter, boolean isSafepoint, List<BlockBegin> blockSuccessors, int inputCount, int successorCount, Graph graph) {
        this(kind, stateAfter, isSafepoint, blockSuccessors.size(), inputCount, successorCount, graph);
        for (int i = 0; i < blockSuccessors.size(); i++) {
            setBlockSuccessor(i, blockSuccessors.get(i));
        }
    }

    public BlockEnd(CiKind kind, FrameState stateAfter, boolean isSafepoint, int blockSuccessorCount, int inputCount, int successorCount, Graph graph) {
        super(kind, inputCount + INPUT_COUNT, successorCount + blockSuccessorCount + SUCCESSOR_COUNT, graph);
        this.blockSuccessorCount = blockSuccessorCount;
        setStateAfter(stateAfter);
        this.isSafepoint = isSafepoint;
    }

    public BlockEnd(CiKind kind, FrameState stateAfter, boolean isSafepoint, Graph graph) {
        this(kind, stateAfter, isSafepoint, 2, 0, 0, graph);
    }

    /**
     * Checks whether this instruction is a safepoint.
     * @return {@code true} if this instruction is a safepoint
     */
    public boolean isSafepoint() {
        return isSafepoint;
    }

    /**
     * Gets the block begin associated with this block end.
     * @return the beginning of this basic block
     */
    public BlockBegin begin() {
        for (Node n : predecessors()) {
            if (n instanceof BlockBegin) {
                return (BlockBegin) n;
            }
        }
        return null;
    }

    /**
     * Substitutes a successor block in this block end's successor list. Note that
     * this method updates all occurrences in the list.
     * @param oldSucc the old successor to replace
     * @param newSucc the new successor
     */
    public void substituteSuccessor(BlockBegin oldSucc, BlockBegin newSucc) {
        assert newSucc != null;
        for (int i = 0; i < blockSuccessorCount; i++) {
            if (blockSuccessor(i) == oldSucc) {
                setBlockSuccessor(i, newSucc);
            }
        }
    }

    /**
     * Gets the successor corresponding to the default (fall through) case.
     * @return the default successor
     */
    public BlockBegin defaultSuccessor() {
        return blockSuccessor(blockSuccessorCount - 1);
    }

    /**
     * Searches for the specified successor and returns its index into the
     * successor list if found.
     * @param b the block to search for in the successor list
     * @return the index of the block in the list if found; <code>-1</code> otherwise
     */
    public int successorIndex(BlockBegin b) {
        for (int i = 0; i < blockSuccessorCount; i++) {
            if (blockSuccessor(i) == b) {
                return i;
            }
        }
        return -1;
    }

    /**
     * This method reorders the predecessors of the i-th successor in such a way that this BlockEnd is at position backEdgeIndex.
     */
    public void reorderSuccessor(int i, int backEdgeIndex) {
        assert i >= 0 && i < blockSuccessorCount;
        BlockBegin successor = blockSuccessor(i);
        if (successor != null) {
            successors().set(super.successorCount() + SUCCESSOR_COUNT + i, Node.Null);
            successors().set(super.successorCount() + SUCCESSOR_COUNT + i, successor, backEdgeIndex);
        }
    }

    /**
     * Gets this block end's list of successors.
     * @return the successor list
     */
    @SuppressWarnings({ "unchecked", "rawtypes"})
    public List<BlockBegin> blockSuccessors() {
        List<BlockBegin> list = (List) successors().subList(super.successorCount() + SUCCESSOR_COUNT, super.successorCount() + blockSuccessorCount + SUCCESSOR_COUNT);
        return Collections.unmodifiableList(list);
    }

    public void clearSuccessors() {
        for (int i = 0; i < blockSuccessorCount(); i++) {
            setBlockSuccessor(i, null);
        }
    }

}
