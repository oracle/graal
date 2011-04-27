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

import com.sun.c1x.util.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * The {@code Op2} class is the base of arithmetic and logic operations with two inputs.
 *
 * @author Ben L. Titzer
 */
public abstract class Op2 extends Instruction {

    /**
     * The opcode of this instruction.
     */
    public final int opcode;

    Value x;
    Value y;

    /**
     * Creates a new Op2 instance.
     * @param kind the result type of this instruction
     * @param opcode the bytecode opcode
     * @param x the first input instruction
     * @param y the second input instruction
     */
    public Op2(CiKind kind, int opcode, Value x, Value y) {
        super(kind);
        this.opcode = opcode;
        this.x = x;
        this.y = y;
    }

    /**
     * Gets the first input to this instruction.
     * @return the first input to this instruction
     */
    public final Value x() {
        return x;
    }

    /**
     * Gets the second input to this instruction.
     * @return the second input to this instruction
     */
    public final Value y() {
        return y;
    }

    /**
     * Swaps the operands of this instruction. This is only legal for commutative operations.
     */
    public void swapOperands() {
        assert Bytecodes.isCommutative(opcode);
        Value t = x;
        x = y;
        y = t;
    }

    /**
     * Iterates over the inputs to this instruction.
     * @param closure the closure to apply to each input value
     */
    @Override
    public void inputValuesDo(ValueClosure closure) {
        x = closure.apply(x);
        y = closure.apply(y);
    }

    @Override
    public int valueNumber() {
        return Util.hash2(opcode, x, y);
    }

    @Override
    public boolean valueEqual(Instruction i) {
        if (i instanceof Op2) {
            Op2 o = (Op2) i;
            return opcode == o.opcode && x == o.x && y == o.y;
        }
        return false;
    }
}
