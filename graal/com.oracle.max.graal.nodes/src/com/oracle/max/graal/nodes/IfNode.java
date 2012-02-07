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
package com.oracle.max.graal.nodes;

import java.util.*;

import com.oracle.max.cri.ci.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;

/**
 * The {@code IfNode} represents a branch that can go one of two directions depending on the outcome of a
 * comparison.
 */
public final class IfNode extends ControlSplitNode implements Simplifiable, LIRLowerable {
    public static final int TRUE_EDGE = 0;
    public static final int FALSE_EDGE = 1;

    private static final BeginNode[] EMPTY_IF_SUCCESSORS = new BeginNode[] {null, null};

    @Input private BooleanNode compare;

    public BooleanNode compare() {
        return compare;
    }

    public IfNode(BooleanNode condition, FixedNode trueSuccessor, FixedNode falseSuccessor, double takenProbability) {
        super(StampFactory.illegal(), new BeginNode[] {BeginNode.begin(trueSuccessor), BeginNode.begin(falseSuccessor)}, new double[] {takenProbability, 1 - takenProbability});
        this.compare = condition;
    }

    public IfNode(BooleanNode condition, double probability) {
        super(StampFactory.illegal(), EMPTY_IF_SUCCESSORS, new double[] {probability, 1 - probability});
        this.compare = condition;
    }

    /**
     * Gets the true successor.
     *
     * @return the true successor
     */
    public BeginNode trueSuccessor() {
        return blockSuccessor(0);
    }

    /**
     * Gets the false successor.
     *
     * @return the false successor
     */
    public BeginNode falseSuccessor() {
        return blockSuccessor(1);
    }

    public void setTrueSuccessor(BeginNode node) {
        setBlockSuccessor(0, node);
    }

    public void setFalseSuccessor(BeginNode node) {
        setBlockSuccessor(1, node);
    }

    /**
     * Gets the node corresponding to the specified outcome of the branch.
     *
     * @param istrue {@code true} if the true successor is requested, {@code false} otherwise
     * @return the corresponding successor
     */
    public BeginNode successor(boolean istrue) {
        return blockSuccessor(istrue ? 0 : 1);
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.emitIf(this);
    }

    @Override
    public boolean verify() {
        assertTrue(compare() != null, "missing compare");
        assertTrue(trueSuccessor() != null, "missing trueSuccessor");
        assertTrue(falseSuccessor() != null, "missing falseSuccessor");
        return super.verify();
    }

    @Override
    public void simplify(SimplifierTool tool) {
        if (compare() instanceof ConstantNode) {
            ConstantNode c = (ConstantNode) compare();
            if (c.asConstant().asBoolean()) {
                tool.deleteBranch(falseSuccessor());
                tool.addToWorkList(trueSuccessor());
                ((StructuredGraph) graph()).removeSplit(this, TRUE_EDGE);
            } else {
                tool.deleteBranch(trueSuccessor());
                tool.addToWorkList(falseSuccessor());
                ((StructuredGraph) graph()).removeSplit(this, FALSE_EDGE);
            }
        } else {
            if (trueSuccessor().next() instanceof EndNode && falseSuccessor().next() instanceof EndNode) {
                EndNode trueEnd = (EndNode) trueSuccessor().next();
                EndNode falseEnd = (EndNode) falseSuccessor().next();
                MergeNode merge = trueEnd.merge();
                if (merge == falseEnd.merge() && merge.endCount() == 2) {
                    Iterator<PhiNode> phis = merge.phis().iterator();
                    if (!phis.hasNext()) {
                        // empty if construct with no phis: remove it
                        removeEmptyIf(tool);
                    } else {
                        PhiNode singlePhi = phis.next();
                        if (!phis.hasNext()) {
                            // one phi at the merge of an otherwise empty if construct: try to convert into a MaterializeNode
                            boolean inverted = trueEnd == merge.endAt(FALSE_EDGE);
                            ValueNode trueValue = singlePhi.valueAt(inverted ? 1 : 0);
                            ValueNode falseValue = singlePhi.valueAt(inverted ? 0 : 1);
                            if (trueValue.kind() != falseValue.kind()) {
                                return;
                            }
                            if (trueValue.kind() != CiKind.Int && trueValue.kind() != CiKind.Long) {
                                return;
                            }
                            if (trueValue.isConstant() && falseValue.isConstant()) {
                                MaterializeNode materialize = MaterializeNode.create(compare(), graph(), (ConstantNode) trueValue, (ConstantNode) falseValue);
                                ((StructuredGraph) graph()).replaceFloating(singlePhi, materialize);
                                removeEmptyIf(tool);
                            }
                        }
                    }
                }
            }
        }
    }

    private void removeEmptyIf(SimplifierTool tool) {
        BeginNode trueSuccessor = trueSuccessor();
        BeginNode falseSuccessor = falseSuccessor();
        assert trueSuccessor.next() instanceof EndNode && falseSuccessor.next() instanceof EndNode;

        EndNode trueEnd = (EndNode) trueSuccessor.next();
        EndNode falseEnd = (EndNode) falseSuccessor.next();
        assert trueEnd.merge() == falseEnd.merge();

        MergeNode merge = trueEnd.merge();
        assert merge.usages().isEmpty();

        FixedNode next = merge.next();
        merge.setNext(null);
        setTrueSuccessor(null);
        setFalseSuccessor(null);
        ((FixedWithNextNode) predecessor()).setNext(next);
        safeDelete();
        trueSuccessor.safeDelete();
        falseSuccessor.safeDelete();
        merge.safeDelete();
        trueEnd.safeDelete();
        falseEnd.safeDelete();
        tool.addToWorkList(next);
    }
}
