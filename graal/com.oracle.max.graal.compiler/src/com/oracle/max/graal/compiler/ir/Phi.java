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

import java.util.*;

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.ir.StateSplit.*;
import com.oracle.max.graal.compiler.phases.CanonicalizerPhase.*;
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

    private final PhiType type;

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
    public Merge merge() {
        return (Merge) inputs().get(super.inputCount() + INPUT_MERGE);
    }

    public void setMerge(Merge n) {
        assert n != null;
        inputs().set(super.inputCount() + INPUT_MERGE, n);
    }

    public static enum PhiType {
        Value,          // normal value phis
        Memory,         // memory phis
        Virtual         // phis used for VirtualObjectField merges
    }

    public Phi(CiKind kind, Merge merge, PhiType type, Graph graph) {
        super(kind, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        this.type = type;
        setMerge(merge);
    }

    private Phi(CiKind kind, PhiType type, Graph graph) {
        super(kind, INPUT_COUNT, SUCCESSOR_COUNT, graph);
        this.type = type;
    }

    public PhiType type() {
        return type;
    }

    @Override
    public boolean verify() {
        assertTrue(merge() != null);
        assertTrue(merge().phiPredecessorCount() == valueCount(), merge().phiPredecessorCount() + "==" + valueCount());
        return true;
    }

    /**
     * Get the instruction that produces the value associated with the i'th predecessor
     * of the join block.
     * @param i the index of the predecessor
     * @return the instruction that produced the value in the i'th predecessor
     */
    public Value valueAt(int i) {
        return (Value) variableInputs().get(i);
    }

    public void setValueAt(int i, Value x) {
        inputs().set(INPUT_COUNT + i, x);
    }

    /**
     * Get the number of inputs to this phi (i.e. the number of predecessors to the join block).
     * @return the number of inputs in this phi
     */
    public int valueCount() {
        return variableInputs().size();
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitPhi(this);
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
        if (type == PhiType.Value) {
            return "Phi: (" + str + ")";
        } else {
            return type + "Phi: (" + str + ")";
        }
    }

    public void addInput(Node y) {
        variableInputs().add(y);
    }

    public void removeInput(int index) {
        variableInputs().remove(index);
    }

    @Override
    public Node copy(Graph into) {
        Phi x = new Phi(kind, type, into);
        return x;
    }

    @Override
    public Iterable<? extends Node> dataInputs() {
        final Iterator< ? extends Node> input = super.dataInputs().iterator();
        return new Iterable<Node>() {
            @Override
            public Iterator<Node> iterator() {
                return new FilteringIterator(input, Merge.class);
            }
        };
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
            Phi phiNode = (Phi) node;
//            if (phiNode.valueCount() != 2 || phiNode.merge().endCount() != 2 || phiNode.merge().phis().size() != 1) {
//                return phiNode;
//            }
//            if (!(phiNode.valueAt(0) instanceof Constant && phiNode.valueAt(1) instanceof Constant)) {
//                return phiNode;
//            }
//            Merge merge = phiNode.merge();
//            Node end0 = merge.endAt(0);
//            Node end1 = merge.endAt(1);
//            if (end0.predecessors().size() != 1 || end1.predecessors().size() != 1) {
//                return phiNode;
//            }
//            Node endPred0 = end0.predecessors().get(0);
//            Node endPred1 = end1.predecessors().get(0);
//            if (endPred0 != endPred1 || !(endPred0 instanceof If)) {
//                return phiNode;
//            }
//            If ifNode = (If) endPred0;
//            if (ifNode.predecessors().size() != 1) {
//                return phiNode;
//            }
//            boolean inverted = ((If) endPred0).trueSuccessor() == end1;
//            CiConstant trueValue = phiNode.valueAt(inverted ? 1 : 0).asConstant();
//            CiConstant falseValue = phiNode.valueAt(inverted ? 0 : 1).asConstant();
//            if (trueValue.kind != CiKind.Int || falseValue.kind != CiKind.Int) {
//                return phiNode;
//            }
//            if ((trueValue.asInt() == 0 || trueValue.asInt() == 1) && (falseValue.asInt() == 0 || falseValue.asInt() == 1) && (trueValue.asInt() != falseValue.asInt())) {
//                MaterializeNode result;
//                if (trueValue.asInt() == 1) {
//                    result = new MaterializeNode(ifNode.compare(), phiNode.graph());
//                } else {
//                    result = new MaterializeNode(new NegateBooleanNode(ifNode.compare(), phiNode.graph()), phiNode.graph());
//                }
//                Node next = merge.next();
//                merge.setNext(null);
//                ifNode.predecessors().get(0).successors().replace(ifNode, next);
//                return result;
//            }
            return phiNode;
        }
    };
}
