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
package com.oracle.max.graal.compiler.ir;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * The {@code IfOp} class represents a comparison that yields one of two values.
 * Note that these nodes are not built directly from the bytecode but are introduced
 * by conditional expression elimination.
 */
public final class Conditional extends Binary {

    private static final int INPUT_COUNT = 2;
    private static final int INPUT_TRUE_VALUE = 0;
    private static final int INPUT_FALSE_VALUE = 1;

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
     * The instruction that produces the value if the comparison is true.
     */
    public Value trueValue() {
        return (Value) inputs().get(super.inputCount() + INPUT_TRUE_VALUE);
    }

    public Value setTrueValue(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_TRUE_VALUE, n);
    }

    /**
     * The instruction that produces the value if the comparison is false.
     */
    public Value falseValue() {
        return (Value) inputs().get(super.inputCount() + INPUT_FALSE_VALUE);
    }

    public Value setFalseValue(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_FALSE_VALUE, n);
    }


    Condition condition;

    /**
     * Constructs a new IfOp.
     * @param x the instruction producing the first value to be compared
     * @param condition the condition of the comparison
     * @param y the instruction producing the second value to be compared
     * @param trueValue the value produced if the condition is true
     * @param falseValue the value produced if the condition is false
     */
    public Conditional(Value x, Condition condition, Value y, Value trueValue, Value falseValue, Graph graph) {
        // TODO: return the appropriate bytecode IF_ICMPEQ, etc
        super(trueValue.kind.meet(falseValue.kind), Bytecodes.ILLEGAL, x, y, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        this.condition = condition;
        setTrueValue(trueValue);
        setFalseValue(falseValue);
    }

    // for copying
    private Conditional(CiKind kind, Condition cond, Graph graph) {
        super(kind, Bytecodes.ILLEGAL, null, null, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        this.condition = cond;
    }

    /**
     * Gets the condition of this if operation.
     * @return the condition
     */
    public Condition condition() {
        return condition;
    }

    /**
     * Checks whether this comparison operator is commutative (i.e. it is either == or !=).
     * @return {@code true} if this comparison is commutative
     */
    public boolean isCommutative() {
        return condition == Condition.EQ || condition == Condition.NE;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitIfOp(this);
    }

    @Override
    public int valueNumber() {
        return Util.hash4(condition.hashCode(), x(), y(), trueValue(), falseValue());
    }

    @Override
    public boolean valueEqual(Node i) {
        if (i instanceof Conditional) {
            Conditional o = (Conditional) i;
            return opcode == o.opcode && x() == o.x() && y() == o.y() && trueValue() == o.trueValue() && falseValue() == o.falseValue();
        }
        return false;
    }

    @Override
    public void print(LogStream out) {
        out.print(x()).
        print(' ').
        print(condition().operator).
        print(' ').
        print(y()).
        print(" ? ").
        print(trueValue()).
        print(" : ").
        print(falseValue());
    }

    @Override
    public Node copy(Graph into) {
        Conditional x = new Conditional(kind, condition, into);
        return x;
    }
}
