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

import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * The {@code Op2} class is the base of arithmetic and logic operations with two inputs.
 */
public abstract class Binary extends FloatingNode {

    @NodeInput
    private Value x;

    @NodeInput
    private Value y;

    public Value x() {
        return x;
    }

    public void setX(Value x) {
        updateUsages(this.x, x);
        this.x = x;
    }

    public Value y() {
        return y;
    }

    public void setY(Value x) {
        updateUsages(y, x);
        this.y = x;
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
    public Binary(CiKind kind, int opcode, Value x, Value y, Graph graph) {
        super(kind, graph);
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
        if (i instanceof Binary) {
            Binary o = (Binary) i;
            return opcode == o.opcode && x() == o.x() && y() == o.y();
        }
        return false;
    }
}
