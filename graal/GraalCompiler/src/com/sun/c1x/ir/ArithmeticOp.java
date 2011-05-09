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
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * The {@code ArithmeticOp} class represents arithmetic operations such as addition, subtraction, etc.
 */
public final class ArithmeticOp extends Op2 {

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_STATE_BEFORE = 0;

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
     * The state for this instruction.
     */
     @Override
    public FrameState stateBefore() {
        return (FrameState) inputs().get(super.inputCount() + INPUT_STATE_BEFORE);
    }

    private FrameState setStateBefore(FrameState n) {
        return (FrameState) inputs().set(super.inputCount() + INPUT_STATE_BEFORE, n);
    }

    private final boolean isStrictFP;

    /**
     * Creates a new arithmetic operation.
     * @param opcode the bytecode opcode
     * @param kind the result kind of the operation
     * @param x the first input instruction
     * @param y the second input instruction
     * @param isStrictFP indicates this operation has strict rounding semantics
     * @param stateBefore the state for instructions that may trap
     */
    public ArithmeticOp(int opcode, CiKind kind, Value x, Value y, boolean isStrictFP, FrameState stateBefore, Graph graph) {
        super(kind, opcode, x, y, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        this.isStrictFP = isStrictFP;
        setStateBefore(stateBefore);
    }

    /**
     * Checks whether this instruction has strict fp semantics.
     * @return {@code true} if this instruction has strict fp semantics
     */
    public boolean isStrictFP() {
        return isStrictFP;
    }

    /**
     * Checks whether this instruction can cause a trap. For arithmetic operations,
     * only division and remainder operations can cause traps.
     * @return {@code true} if this instruction can cause a trap
     */
    @Override
    public boolean canTrap() {
        return stateBefore() != null;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitArithmeticOp(this);
    }

    public boolean isCommutative() {
        return Bytecodes.isCommutative(opcode);
    }

    @Override
    public void print(LogStream out) {
        out.print(x()).print(' ').print(Bytecodes.operator(opcode)).print(' ').print(y());
    }

    @Override
    public String shortName() {
        return Bytecodes.operator(opcode);
    }
}
