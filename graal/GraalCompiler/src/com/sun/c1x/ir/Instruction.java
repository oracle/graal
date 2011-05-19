/*
 * Copyright (c) 2009, 2010, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.c1x.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * Denotes an instruction node in the IR, which is a {@link Value} that
 * can be added to a basic block (whereas other {@link Value} nodes such as {@link Phi} and
 * {@link Local} cannot be added to basic blocks).
 *
 * Subclasses of instruction represent arithmetic and object operations,
 * control flow operators, phi statements, method calls, the start of basic blocks, and
 * the end of basic blocks.
 *
 * Instruction nodes are chained together in a basic block through the embedded
 * {@link Instruction#next} field. An Instruction may also have a list of {@link ExceptionHandler}s.
 */
public abstract class Instruction extends Value {

    private static final int INPUT_COUNT = 0;

    private static final int SUCCESSOR_COUNT = 1;
    private static final int SUCCESSOR_NEXT = 0;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    /**
     * Links to next instruction in a basic block, to {@code null} if this instruction is the end of a basic block or to
     * itself if not in a block.
     */
    public Instruction next() {
        return (Instruction) successors().get(super.successorCount() + SUCCESSOR_NEXT);
    }

    private Node setNext(Instruction next) {
        return successors().set(super.successorCount() + SUCCESSOR_NEXT, next);
    }


    public static final int SYNCHRONIZATION_ENTRY_BCI = -1;

    /**
     * Index of bytecode that generated this node when appended in a basic block.
     * Negative values indicate special cases.
     */
    private int bci;

    private boolean isAppended = false;

    /**
     * Constructs a new instruction with the specified value type.
     * @param kind the value type for this instruction
     * @param inputCount
     * @param successorCount
     */
    public Instruction(CiKind kind, int inputCount, int successorCount, Graph graph) {
        super(kind, inputCount + INPUT_COUNT, successorCount + SUCCESSOR_COUNT, graph);
        C1XMetrics.HIRInstructions++;
    }

    /**
     * Gets the bytecode index of this instruction.
     * @return the bytecode index of this instruction
     */
    public final int bci() {
        return bci;
    }

    /**
     * Sets the bytecode index of this instruction.
     * @param bci the new bytecode index for this instruction
     */
    public final void setBCI(int bci) {
        assert bci >= 0 || bci == SYNCHRONIZATION_ENTRY_BCI;
        this.bci = bci;
    }

    /**
     * Checks whether this instruction has already been added to its basic block.
     * @return {@code true} if this instruction has been added to the basic block containing it
     */
    public final boolean isAppended() {
        return isAppended;
    }


    /**
     * Sets the next instruction for this instruction. Note that it is illegal to
     * set the next field of a phi, block end, or local instruction.
     * @param next the next instruction
     * @param bci the bytecode index of the next instruction
     * @return the new next instruction
     */
    public final Instruction appendNext(Instruction next, int bci) {
        setNext(next);
        if (next != null) {
            assert !(this instanceof BlockEnd);
            next.setBCI(bci);
            next.isAppended = true;
        }
        return next;
    }

    @Override
    public BlockBegin block() {
        // TODO(tw): Make this more efficient.
        Instruction cur = this;
        while (!(cur instanceof BlockEnd)) {
            cur = cur.next();
        }
        return ((BlockEnd) cur).begin();
    }

    /**
     * Compute the value number of this Instruction. Local and global value numbering
     * optimizations use a hash map, and the value number provides a hash code.
     * If the instruction cannot be value numbered, then this method should return
     * {@code 0}.
     * @return the hashcode of this instruction
     */
    public int valueNumber() {
        return 0;
    }

    /**
     * Checks that this instruction is equal to another instruction for the purposes
     * of value numbering.
     * @param i the other instruction
     * @return {@code true} if this instruction is equivalent to the specified
     * instruction w.r.t. value numbering
     */
    public boolean valueEqual(Instruction i) {
        return false;
    }

    /**
     * Gets the state after the instruction, if it is recorded. Typically only
     * instances of {@link BlockEnd} have a non-null state after.
     * @return the state after the instruction
     */
    public FrameState stateAfter() {
        return null;
    }
}
