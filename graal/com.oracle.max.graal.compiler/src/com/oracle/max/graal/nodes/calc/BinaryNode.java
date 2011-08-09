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
package com.oracle.max.graal.nodes.calc;

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.base.*;
import com.sun.cri.bytecode.*;
import com.sun.cri.ci.*;

/**
 * The {@code Op2} class is the base of arithmetic and logic operations with two inputs.
 */
public abstract class BinaryNode extends FloatingNode {

    @Input private ValueNode x;
    @Input private ValueNode y;
    @Data public final int opcode;

    public ValueNode x() {
        return x;
    }

    public void setX(ValueNode x) {
        updateUsages(this.x, x);
        this.x = x;
    }

    public ValueNode y() {
        return y;
    }

    public void setY(ValueNode x) {
        updateUsages(y, x);
        this.y = x;
    }

    /**
     * Creates a new Op2 instance.
     * @param kind the result type of this instruction
     * @param opcode the bytecode opcode
     * @param x the first input instruction
     * @param y the second input instruction
     */
    public BinaryNode(CiKind kind, int opcode, ValueNode x, ValueNode y, Graph graph) {
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
        ValueNode t = x();
        setX(y());
        setY(t);
    }
}
