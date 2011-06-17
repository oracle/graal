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
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * The {@code Phi} instruction represents the merging of dataflow
 * in the instruction graph. It refers to a join block and a variable.
 */
public final class Phi extends FloatingNode {

    private static final int DEFAULT_MAX_VALUES = 2;

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_MERGE = 0;

    private static final int SUCCESSOR_COUNT = 0;

    private boolean isDead;

    @Override
    protected int inputCount() {
        return super.inputCount() + INPUT_COUNT;
    }

    @Override
    protected int successorCount() {
        return super.successorCount() + SUCCESSOR_COUNT;
    }

    /**
     * The merge node for this phi.
     */
    public PhiPoint merge() {
        return (PhiPoint) inputs().get(super.inputCount() + INPUT_MERGE);
    }

    public void setMerge(Node n) {
        assert n instanceof PhiPoint;
        inputs().set(super.inputCount() + INPUT_MERGE, n);
    }

    public Phi(CiKind kind, PhiPoint merge, Graph graph) {
        super(kind, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        setMerge(merge.asNode());
    }

    Phi(CiKind kind, Graph graph) {
        super(kind, INPUT_COUNT, SUCCESSOR_COUNT, graph);
    }

    /**
     * Get the instruction that produces the value associated with the i'th predecessor
     * of the join block.
     * @param i the index of the predecessor
     * @return the instruction that produced the value in the i'th predecessor
     */
    public Value valueAt(int i) {
        return (Value) inputs().variablePart().get(i);
    }

    public void setValueAt(int i, Value x) {
        inputs().set(INPUT_COUNT + i, x);
    }

    /**
     * Get the number of inputs to this phi (i.e. the number of predecessors to the join block).
     * @return the number of inputs in this phi
     */
    public int valueCount() {
        return inputs().variablePart().size();
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitPhi(this);
    }

    /**
     * Make this phi illegal if types were not merged correctly.
     */
    public void makeDead() {
        isDead = true;
    }

    public boolean isDead() {
        return isDead;
    }

    @Override
    public void print(LogStream out) {
        out.print("phi function (");
        for (int i = 0; i < valueCount(); ++i) {
            if (i != 0) {
                out.print(' ');
            }
            out.print(valueAt(i));
        }
        out.print(')');
    }

    @Override
    public String shortName() {
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < valueCount(); ++i) {
            if (i != 0) {
                str.append(' ');
            }
            str.append(valueAt(i) == null ? "-" : valueAt(i).id());
        }
        return "Phi: (" + str + ")";
    }

    public void addInput(Node y) {
        inputs().variablePart().add(y);
    }

    public void removeInput(int index) {
        inputs().variablePart().remove(index);
    }

    @Override
    public Node copy(Graph into) {
        Phi x = new Phi(kind, into);
        x.isDead = isDead;
        return x;
    }
}
