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

import java.util.*;

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
 *
 *
 * @author Ben L. Titzer
 */
public abstract class Instruction extends Value {

    public static final int INVOCATION_ENTRY_BCI = -1;  // XXX: not currently used
    public static final int SYNCHRONIZATION_ENTRY_BCI = -1;

    /**
     * Index of bytecode that generated this node when appended in a basic block.
     * Negative values indicate special cases.
     */
    private int bci;

    /**
     * Links to next instruction in a basic block or to {@code} itself if not in a block.
     */
    private Instruction next = this;

    /**
     * List of associated exception handlers.
     */
    private List<ExceptionHandler> exceptionHandlers = ExceptionHandler.ZERO_HANDLERS;

    /**
     * Constructs a new instruction with the specified value type.
     * @param kind the value type for this instruction
     */
    public Instruction(CiKind kind) {
        super(kind);
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
        return next != this;
    }

    /**
     * Gets the next instruction after this one in the basic block, or {@code null}
     * if this instruction is the end of a basic block.
     * @return the next instruction after this one in the basic block
     */
    public final Instruction next() {
        if (next == this) {
            return null;
        }
        return next;
    }

    /**
     * Sets the next instruction for this instruction. Note that it is illegal to
     * set the next field of a phi, block end, or local instruction.
     * @param next the next instruction
     * @param bci the bytecode index of the next instruction
     * @return the new next instruction
     */
    public final Instruction setNext(Instruction next, int bci) {
        this.next = next;
        if (next != null) {
            assert !(this instanceof BlockEnd);
            next.setBCI(bci);
            if (next.next == next) {
                next.next = null;
            }
        }
        return next;
    }

    /**
     * Re-sets the next instruction for this instruction. Note that it is illegal to
     * set the next field of a phi, block end, or local instruction.
     * @param next the next instruction
     * @return the new next instruction
     */
    public final Instruction resetNext(Instruction next) {
        if (next != null) {
            assert !(this instanceof BlockEnd);
            this.next = next;
        }
        return next;
    }

    /**
     * Gets the instruction preceding this instruction in the specified basic block.
     * Note that instructions do not directly refer to their previous instructions,
     * and therefore this operation much search from the beginning of the basic
     * block, thereby requiring time linear in the size of the basic block in the worst
     * case. Use with caution!
     * @param block the basic block that contains this instruction
     * @return the instruction before this instruction in the basic block
     */
    public final Instruction prev(BlockBegin block) {
        Instruction p = null;
        Instruction q = block;
        while (q != this) {
            assert q != null : "this instruction is not in the specified basic block";
            p = q;
            q = q.next();
        }
        return p;
    }

    @Override
    public BlockBegin block() {
        // TODO(tw): Make this more efficient.
        Instruction cur = this;
        while (!(cur instanceof BlockEnd)) {
            cur = cur.next;
        }
        return ((BlockEnd) cur).begin;
    }

    /**
     * Gets the list of exception handlers associated with this instruction.
     * @return the list of exception handlers for this instruction
     */
    public final List<ExceptionHandler> exceptionHandlers() {
        return exceptionHandlers;
    }

    /**
     * Sets the list of exception handlers for this instruction.
     * @param exceptionHandlers the exception handlers
     */
    public final void setExceptionHandlers(List<ExceptionHandler> exceptionHandlers) {
        this.exceptionHandlers = exceptionHandlers;
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
     * Gets the name of this instruction as a string.
     * @return the name of this instruction
     */
    public final String name() {
        return getClass().getSimpleName();
    }

    /**
     * Tests whether this instruction can trap. This is conservative; it does not take
     * into account analysis results that may eliminate the possibility of this
     * instruction from trapping.
     *
     * @return {@code true} if this instruction can cause a trap.
     */
    public boolean canTrap() {
        return false;
    }

    /**
     * Apply the specified closure to all the values of this instruction, including
     * input values, state values, and other values.
     * @param closure the closure to apply
     */
    public final void allValuesDo(ValueClosure closure) {
        inputValuesDo(closure);
        FrameState stateBefore = stateBefore();
        if (stateBefore != null) {
            stateBefore.valuesDo(closure);
        }
        FrameState stateAfter = stateAfter();
        if (stateAfter != null) {
            stateAfter.valuesDo(closure);
        }
    }

    /**
     * Gets the state before the instruction, if it is recorded.
     * @return the state before the instruction
     */
    public FrameState stateBefore() {
        return null;
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
