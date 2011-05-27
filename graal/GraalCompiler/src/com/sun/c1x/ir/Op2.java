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
import com.sun.c1x.util.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * The {@code Op2} class is the base of arithmetic and logic operations with two inputs.
 */
public abstract class Op2 extends Value {

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
     * The first input to this instruction.
     */
     public Value x() {
        return (Value) inputs().get(super.inputCount() + INPUT_X);
    }

    public Value setX(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_X, n);
    }

    /**
     * The second input to this instruction.
     */
    public Value y() {
        return (Value) inputs().get(super.inputCount() + INPUT_Y);
    }

    public Value setY(Value n) {
        return (Value) inputs().set(super.inputCount() + INPUT_Y, n);
    }

    /**
     * The opcode of this instruction.
     */
    public final int opcode;

    /**
     * Creates a new Op2 instance.
     * @param kind the result type of this instruction
     * @param opcode the bytecode opcode
     * @param x the first input instruction
     * @param y the second input instruction
     */
    public Op2(CiKind kind, int opcode, Value x, Value y, int inputCount, int successorCount, Graph graph) {
        super(kind, inputCount + INPUT_COUNT, successorCount + SUCCESSOR_COUNT, graph);
        this.opcode = opcode;
        setX(x);
        setY(y);
    }

    /**
     * Swaps the operands of this instruction. This is only legal for commutative operations.
     */
    public void swapOperands() {
        assert Bytecodes.isCommutative(opcode);
        Value t = x();
        setX(y());
        setY(t);
    }

    @Override
    public int valueNumber() {
        return Util.hash2(opcode, x(), y());
    }

    @Override
    public boolean valueEqual(Node i) {
        if (i instanceof Op2) {
            Op2 o = (Op2) i;
            return opcode == o.opcode && x() == o.x() && y() == o.y();
        }
        return false;
    }
}
