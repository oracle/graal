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
import com.sun.cri.ci.*;

/**
 * The {@code Phi} instruction represents the merging of dataflow
 * in the instruction graph. It refers to a join block and a variable.
 */
public final class Phi extends Value {

    private static final int DEFAULT_MAX_VALUES = 2;

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_BLOCK = 0;

    private static final int SUCCESSOR_COUNT = 0;

    private int usedInputCount;

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    /**
     * The join block for this phi.
     */
     @Override
    public Merge block() {
        return (Merge) inputs().get(super.inputCount() + INPUT_BLOCK);
    }

    public Value setBlock(Value n) {
        return (Merge) inputs().set(super.inputCount() + INPUT_BLOCK, n);
    }

    /**
     * Create a new Phi for the specified join block and local variable (or operand stack) slot.
     * @param kind the type of the variable
     * @param block the join point
     * @param index the index into the stack (if < 0) or local variables
     * @param graph
     */
    public Phi(CiKind kind, Merge block, Graph graph) {
        this(kind, block, DEFAULT_MAX_VALUES, graph);
    }

    public Phi(CiKind kind, Merge block, int maxValues, Graph graph) {
        super(kind, INPUT_COUNT + maxValues, SUCCESSOR_COUNT, graph);
        usedInputCount = 1;
        setBlock(block);
    }

    /**
     * Get the instruction that produces the value associated with the i'th predecessor
     * of the join block.
     * @param i the index of the predecessor
     * @return the instruction that produced the value in the i'th predecessor
     */
    public Value valueAt(int i) {
        return (Value) inputs().get(i + INPUT_COUNT);
    }

    /**
     * Get the number of inputs to this phi (i.e. the number of predecessors to the join block).
     * @return the number of inputs in this phi
     */
    public int valueCount() {
        return usedInputCount - 1;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitPhi(this);
    }

    /**
     * Make this phi illegal if types were not merged correctly.
     */
    public void makeDead() {
        setFlag(Flag.PhiCannotSimplify);
        setFlag(Flag.PhiDead);
    }

    @Override
    public void print(LogStream out) {
        out.print("phi function (");
        for (int i = 0; i < inputs().size(); ++i) {
            if (i != 0) {
                out.print(' ');
            }
            out.print((Value) inputs().get(i));
        }
        out.print(')');
    }

    @Override
    public String shortName() {
        StringBuilder str = new StringBuilder();
        for (int i = 1; i < inputs().size(); ++i) {
            if (i != 1) {
                str.append(' ');
            }
            if (inputs().get(i) != null) {
                str.append(inputs().get(i).id());
            } else {
                str.append("-");
            }
        }
        return "Phi: (" + str + ")";
    }

    public Phi addInput(Node y) {
        assert !this.isDeleted() && !y.isDeleted();
        Phi phi = this;
        if (usedInputCount == inputs().size()) {
            phi = new Phi(kind, block(), usedInputCount * 2, graph());
            for (int i = 0; i < valueCount(); ++i) {
                phi.addInput(valueAt(i));
            }
            phi.addInput(y);
            this.replace(phi);
        } else {
            phi.inputs().set(usedInputCount++, y);
        }
        return phi;
    }

    public void removeInput(int index) {
        inputs().set(index, null);
        for (int i = index + 1; i < usedInputCount; ++i) {
            inputs().set(i - 1, inputs().get(i));
        }
        usedInputCount--;
    }
}
