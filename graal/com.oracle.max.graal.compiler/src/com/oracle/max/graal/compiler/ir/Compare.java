/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

public final class Compare extends FloatingNode {

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
     * @param condition the condition (comparison operation)
     * @param y the instruction that produces the second input to this instruction
     * @param graph
     */
    public Compare(Value x, Condition condition, Value y, Graph graph) {
        super(CiKind.Illegal, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        assert (x == null && y == null) || Util.archKindsEqual(x, y);
        this.condition = condition;
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
     * Swaps the operands to this if and reverses the condition (e.g. > goes to <=).
     * @see Condition#mirror()
     */
    public void swapOperands() {
        condition = condition.mirror();
        Value t = x();
        setX(y());
        setY(t);
    }

    @Override
    public void accept(ValueVisitor v) {
    }

    @Override
    public void print(LogStream out) {
        out.print("comp ").
        print(x()).
        print(' ').
        print(condition().operator).
        print(' ').
        print(y());
    }

    @Override
    public String shortName() {
        return "Comp " + condition.operator;
    }

    @Override
    public Node copy(Graph into) {
        Compare x = new Compare(null, condition, null, into);
        x.unorderedIsTrue = unorderedIsTrue;
        return x;
    }
}
