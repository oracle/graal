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

import com.sun.c1x.debug.*;
import com.sun.c1x.util.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code If} instruction represents a branch that can go one of two directions
 * depending on the outcome of a comparison.
 *
 * @author Ben L. Titzer
 */
public final class If extends BlockEnd {

    Value x;
    Value y;
    Condition condition;

    /**
     * Constructs a new If instruction.
     * @param x the instruction producing the first input to the instruction
     * @param cond the condition (comparison operation)
     * @param unorderedIsTrue {@code true} if unordered is treated as true (floating point operations)
     * @param y the instruction that produces the second input to this instruction
     * @param trueSucc the block representing the true successor
     * @param falseSucc the block representing the false successor
     * @param stateAfter the state before the branch but after the input values have been popped
     * @param isSafepoint {@code true} if this branch should be considered a safepoint
     */
    public If(Value x, Condition cond, boolean unorderedIsTrue, Value y,
              BlockBegin trueSucc, BlockBegin falseSucc, FrameState stateAfter, boolean isSafepoint) {
        super(CiKind.Illegal, stateAfter, isSafepoint);
        this.x = x;
        this.y = y;
        condition = cond;
        assert Util.archKindsEqual(x, y);
        initFlag(Flag.UnorderedIsTrue, unorderedIsTrue);
        successors.add(trueSucc);
        successors.add(falseSucc);
    }

    /**
     * Gets the instruction that produces the first input to this comparison.
     * @return the instruction producing the first input
     */
    public Value x() {
        return x;
    }

    /**
     * Gets the instruction that produces the second input to this comparison.
     * @return the instruction producing the second input
     */
    public Value y() {
        return y;
    }

    /**
     * Gets the condition (comparison operation) for this instruction.
     * @return the condition
     */
    public Condition condition() {
        return condition;
    }

    /**
     * Checks whether unordered inputs mean true or false.
     * @return {@code true} if unordered inputs produce true
     */
    public boolean unorderedIsTrue() {
        return checkFlag(Flag.UnorderedIsTrue);
    }

    /**
     * Gets the block corresponding to the true successor.
     * @return the true successor
     */
    public BlockBegin trueSuccessor() {
        return successors.get(0);
    }

    /**
     * Gets the block corresponding to the false successor.
     * @return the false successor
     */
    public BlockBegin falseSuccessor() {
        return successors.get(1);
    }

    /**
     * Gets the block corresponding to the specified outcome of the branch.
     * @param istrue {@code true} if the true successor is requested, {@code false} otherwise
     * @return the corresponding successor
     */
    public BlockBegin successor(boolean istrue) {
        return successors.get(istrue ? 0 : 1);
    }

    /**
     * Gets the successor of this instruction for the unordered case.
     * @return the successor for unordered inputs
     */
    public BlockBegin unorderedSuccessor() {
        return successor(unorderedIsTrue());
    }

    /**
     * Swaps the operands to this if and reverses the condition (e.g. > goes to <=).
     * @see Condition#mirror()
     */
    public void swapOperands() {
        condition = condition.mirror();
        Value t = x;
        x = y;
        y = t;
    }

    /**
     * Swaps the successor blocks to this if and negates the condition (e.g. == goes to !=)
     * @see Condition#negate()
     */
    public void swapSuccessors() {
        setFlag(Flag.UnorderedIsTrue, !unorderedIsTrue());
        condition = condition.negate();
        BlockBegin t = successors.get(0);
        BlockBegin f = successors.get(1);
        successors.set(0, f);
        successors.set(1, t);
    }

    @Override
    public void inputValuesDo(ValueClosure closure) {
        x = closure.apply(x);
        y = closure.apply(y);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitIf(this);
    }

    @Override
    public void print(LogStream out) {
        out.print("if ").
        print(x()).
        print(' ').
        print(condition().operator).
        print(' ').
        print(y()).
        print(" then B").
        print(successors().get(0).blockID).
        print(" else B").
        print(successors().get(1).blockID);
        if (isSafepoint()) {
            out.print(" (safepoint)");
        }
    }
}
