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
import com.sun.c1x.util.*;
import com.sun.c1x.value.*;
import com.sun.cri.ci.*;

/**
 * The {@code If} instruction represents a branch that can go one of two directions
 * depending on the outcome of a comparison.
 */
public final class If extends BlockEnd {

    private static final int INPUT_COUNT = 2;
    private static final int INPUT_X = 0;
    private static final int INPUT_Y = 1;

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
     * The instruction that produces the first input to this comparison.
     */
     public Value x() {
        return (Value) inputs().get(super.inputCount() + INPUT_X);
    }

    public Value setX(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_X, n);
    }

    /**
     * The instruction that produces the second input to this comparison.
     */
    public Value y() {
        return (Value) inputs().get(super.inputCount() + INPUT_Y);
    }

    public Value setY(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_Y, n);
    }

    Condition condition;
    boolean unorderedIsTrue;

    /**
     * Constructs a new If instruction.
     * @param x the instruction producing the first input to the instruction
     * @param cond the condition (comparison operation)
     * @param y the instruction that produces the second input to this instruction
     * @param stateAfter the state before the branch but after the input values have been popped
     * @param graph
     */
    public If(Value x, Condition cond, Value y, Graph graph) {
        super(CiKind.Illegal, 2, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        assert Util.archKindsEqual(x, y);
        condition = cond;
        setX(x);
        setY(y);
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
        return unorderedIsTrue;
    }

    /**
     * Gets the block corresponding to the true successor.
     * @return the true successor
     */
    public Instruction trueSuccessor() {
        return blockSuccessor(0);
    }

    /**
     * Gets the block corresponding to the false successor.
     * @return the false successor
     */
    public Instruction falseSuccessor() {
        return blockSuccessor(1);
    }

    /**
     * Gets the block corresponding to the specified outcome of the branch.
     * @param istrue {@code true} if the true successor is requested, {@code false} otherwise
     * @return the corresponding successor
     */
    public Instruction successor(boolean istrue) {
        return blockSuccessor(istrue ? 0 : 1);
    }

    /**
     * Gets the successor of this instruction for the unordered case.
     * @return the successor for unordered inputs
     */
    public Instruction unorderedSuccessor() {
        return successor(unorderedIsTrue());
    }

    /**
     * Swaps the operands to this if and reverses the condition (e.g. > goes to <=).
     * @see Condition#mirror()
     */
    public void swapOperands() {
        condition = condition.mirror();
        Value t = x();
        setX(y());
        setY(t);
    }

    /**
     * Swaps the successor blocks to this if and negates the condition (e.g. == goes to !=)
     * @see Condition#negate()
     */
    public void swapSuccessors() {
        unorderedIsTrue = !unorderedIsTrue;
        condition = condition.negate();
        Instruction t = blockSuccessor(0);
        Instruction f = blockSuccessor(1);
        setBlockSuccessor(0, f);
        setBlockSuccessor(1, t);
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
        print(blockSuccessors().get(0).blockID).
        print(" else B").
        print(blockSuccessors().get(1).blockID);
    }

    @Override
    public String shortName() {
        return "If " + condition.operator;
    }


}
