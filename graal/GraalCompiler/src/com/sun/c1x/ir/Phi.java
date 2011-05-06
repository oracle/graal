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

import com.oracle.graal.graph.*;
import com.sun.c1x.debug.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code Phi} instruction represents the merging of dataflow
 * in the instruction graph. It refers to a join block and a variable.
 */
public final class Phi extends Value {

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_BLOCK = 0;

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
     * The join block for this phi.
     */
     @Override
    public BlockBegin block() {
        return (BlockBegin) inputs().get(super.inputCount() + INPUT_BLOCK);
    }

    public BlockBegin setBlock(Value n) {
        return (BlockBegin) inputs().set(super.inputCount() + INPUT_BLOCK, n);
    }

    private final int index;

    /**
     * Create a new Phi for the specified join block and local variable (or operand stack) slot.
     * @param kind the type of the variable
     * @param block the join point
     * @param index the index into the stack (if < 0) or local variables
     * @param graph
     */
    public Phi(CiKind kind, BlockBegin block, int index, Graph graph) {
        super(kind, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        this.index = index;
        setBlock(block);
    }

    /**
     * Check whether this phi corresponds to a local variable.
     * @return {@code true} if this phi refers to a local variable
     */
    public boolean isLocal() {
        return index >= 0;
    }

    /**
     * Check whether this phi corresponds to a stack location.
     * @return {@code true} if this phi refers to a stack location
     */
    public boolean isOnStack() {
        return index < 0;
    }

    /**
     * Get the local index of this phi.
     * @return the local index
     */
    public int localIndex() {
        assert isLocal();
        return index;
    }

    /**
     * Get the stack index of this phi.
     * @return the stack index of this phi
     */
    public int stackIndex() {
        assert isOnStack();
        return -(index + 1);
    }

    /**
     * Get the instruction that produces the value associated with the i'th predecessor
     * of the join block.
     * @param i the index of the predecessor
     * @return the instruction that produced the value in the i'th predecessor
     */
    public Value inputAt(int i) {
        FrameState state;
        if (block().isExceptionEntry()) {
            state = block().exceptionHandlerStates().get(i);
        } else {
            state = block().blockPredecessors().get(i).end().stateAfter();
        }
        return inputIn(state);
    }

    /**
     * Gets the instruction that produces the value for this phi in the specified state.
     * @param state the state to access
     * @return the instruction producing the value
     */
    public Value inputIn(FrameState state) {
        if (isLocal()) {
            return state.localAt(localIndex());
        } else {
            return state.stackAt(stackIndex());
        }
    }

    /**
     * Get the number of inputs to this phi (i.e. the number of predecessors to the join block).
     * @return the number of inputs in this phi
     */
    public int phiInputCount() {
        if (block().isExceptionEntry()) {
            return block().exceptionHandlerStates().size();
        } else {
            return block().blockPredecessors().size();
        }
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitPhi(this);
    }

    /**
     * Make this phi illegal if types were not merged correctly.
     */
    public void makeDead() {
        setFlag(Flag.PhiCannotSimplify);
        setFlag(Flag.PhiDead);
    }

    @Override
    public void print(LogStream out) {
        out.print("phi function");
    }
}
