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

import com.oracle.max.graal.compiler.*;
import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.CanonicalizerOp;
import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * The {@code If} instruction represents a branch that can go one of two directions
 * depending on the outcome of a comparison.
 */
public final class If extends ControlSplit {

    private static final int INPUT_COUNT = 1;
    private static final int INPUT_COMPARE = 0;

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
     public BooleanNode compare() {
        return (BooleanNode) inputs().get(super.inputCount() + INPUT_COMPARE);
    }

    public void setCompare(BooleanNode n) {
        inputs().set(super.inputCount() + INPUT_COMPARE, n);
    }

    public If(BooleanNode condition, double probability, Graph graph) {
        super(CiKind.Illegal, 2, new double[] {probability, 1 - probability}, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        setCompare(condition);
    }

    /**
     * Gets the block corresponding to the true successor.
     * @return the true successor
     */
    public FixedNode trueSuccessor() {
        return blockSuccessor(0);
    }

    /**
     * Gets the block corresponding to the false successor.
     * @return the false successor
     */
    public FixedNode falseSuccessor() {
        return blockSuccessor(1);
    }


    public void setTrueSuccessor(FixedNode node) {
        setBlockSuccessor(0, node);
    }


    public void setFalseSuccessor(FixedNode node) {
        setBlockSuccessor(1, node);
    }

    /**
     * Gets the block corresponding to the specified outcome of the branch.
     * @param istrue {@code true} if the true successor is requested, {@code false} otherwise
     * @return the corresponding successor
     */
    public FixedNode successor(boolean istrue) {
        return blockSuccessor(istrue ? 0 : 1);
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitIf(this);
    }

    @Override
    public boolean verify() {
        assertTrue(compare() != null);
        assertTrue(trueSuccessor() != null);
        assertTrue(falseSuccessor() != null);
        return true;
    }

    @Override
    public void print(LogStream out) {
        out.print("if ").
        print(compare()).
        print(" then ").
        print(trueSuccessor()).
        print(" else ").
        print(falseSuccessor());
    }

    @Override
    public String shortName() {
        return "If";
    }

    @Override
    public Node copy(Graph into) {
        If x = new If(null, probability(0), into);
        super.copyInto(x);
        return x;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Op> T lookup(Class<T> clazz) {
        if (clazz == CanonicalizerOp.class) {
            return (T) CANONICALIZER;
        }
        return super.lookup(clazz);
    }

    private static CanonicalizerOp CANONICALIZER = new CanonicalizerOp() {
        @Override
        public Node canonical(Node node) {
            If ifNode = (If) node;
            if (ifNode.compare() instanceof Constant) {
                Constant c = (Constant) ifNode.compare();
                if (c.asConstant().asBoolean()) {
                    if (GraalOptions.TraceCanonicalizer) {
                        TTY.println("Replacing if " + ifNode + " with true branch");
                    }
                    return ifNode.trueSuccessor();
                } else {
                    if (GraalOptions.TraceCanonicalizer) {
                        TTY.println("Replacing if " + ifNode + " with false branch");
                    }
                    return ifNode.falseSuccessor();
                }
            }
            return ifNode;
        }
    };
}
