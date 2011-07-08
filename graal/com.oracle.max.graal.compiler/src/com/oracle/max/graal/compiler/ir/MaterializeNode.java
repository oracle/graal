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

import com.oracle.max.asm.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.gen.*;
import com.oracle.max.graal.compiler.lir.*;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * The {@code Convert} class represents a conversion between primitive types.
 */
public final class MaterializeNode extends FloatingNode {

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_VALUE = 0;

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
     * The instruction which produces the input value to this instruction.
     */
     public Value value() {
        return (Value) inputs().get(super.inputCount() + INPUT_VALUE);
    }

    public void setValue(Value n) {
        inputs().set(super.inputCount() + INPUT_VALUE, n);
    }

    /**
     * Constructs a new Convert instance.
     * @param opcode the bytecode representing the operation
     * @param value the instruction producing the input value
     * @param kind the result type of this instruction
     * @param graph
     */
    public MaterializeNode(Value value, Graph graph) {
        super(CiKind.Int, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        setValue(value);
    }

    @Override
    public void accept(ValueVisitor v) {
    }

    @Override
    public boolean valueEqual(Node i) {
        return (i instanceof MaterializeNode);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == LIRGenerator.LIRGeneratorOp.class) {
            return (T) LIR_GENERATOR_OP;
        }
        return super.lookup(clazz);
    }

    public static final LIRGenerator.LIRGeneratorOp LIR_GENERATOR_OP = new LIRGenerator.LIRGeneratorOp() {

        @Override
        public void generate(Node n, LIRGenerator generator) {
            LIRBlock trueSuccessor = new LIRBlock(new Label(), null);
            generator.emitBooleanBranch(((MaterializeNode) n).value(), trueSuccessor, null, null);
            CiValue result = generator.createResultVariable((Value) n);
            LIRList lir = generator.lir();
            lir.move(CiConstant.FALSE, result);
            Label label = new Label();
            lir.branch(Condition.TRUE, label);
            lir.branchDestination(trueSuccessor.label);
            lir.move(CiConstant.TRUE, result);
            lir.branchDestination(label);
        }
    };

    @Override
    public void print(LogStream out) {
        out.print("materialize(").print(value().toString()).print(')');
    }

    @Override
    public Node copy(Graph into) {
        return new MaterializeNode(null, into);
    }
}
