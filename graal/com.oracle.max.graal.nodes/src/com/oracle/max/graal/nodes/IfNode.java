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

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.spi.*;
import com.oracle.max.graal.nodes.type.*;

/**
 * The {@code IfNode} represents a branch that can go one of two directions depending on the outcome of a
 * comparison.
 */
public final class IfNode extends ControlSplitNode implements Canonicalizable, LIRLowerable {

    private static final BeginNode[] EMPTY_IF_SUCCESSORS = new BeginNode[] {null, null};

    @Input private BooleanNode compare;

    public BooleanNode compare() {
        return compare;
    }

    public IfNode(BooleanNode condition, FixedNode trueSuccessor, FixedNode falseSuccessor, double probability) {
        super(StampFactory.illegal(), new BeginNode[] {BeginNode.begin(trueSuccessor), BeginNode.begin(falseSuccessor)}, new double[] {probability, 1 - probability});
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
    public FixedNode successor(boolean istrue) {
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
    public Node canonical(CanonicalizerTool tool) {
        if (compare() instanceof ConstantNode) {
            ConstantNode c = (ConstantNode) compare();
            if (c.asConstant().asBoolean()) {
                tool.deleteBranch(falseSuccessor());
                return trueSuccessor();
            } else {
                tool.deleteBranch(trueSuccessor());
                return falseSuccessor();
            }
        }
        if (trueSuccessor().next() instanceof EndNode && falseSuccessor().next() instanceof EndNode) {
            EndNode trueEnd = (EndNode) trueSuccessor().next();
            EndNode falseEnd = (EndNode) falseSuccessor().next();
            MergeNode merge = trueEnd.merge();
            if (merge == falseEnd.merge() && !merge.phis().iterator().hasNext() && merge.endCount() == 2) {
                FixedNode next = merge.next();
                merge.safeDelete();
                BeginNode falseSuccessor = this.falseSuccessor();
                BeginNode trueSuccessor = this.trueSuccessor();
                this.setTrueSuccessor(null);
                this.setFalseSuccessor(null);
                trueSuccessor.safeDelete();
                falseSuccessor.safeDelete();
                trueEnd.safeDelete();
                falseEnd.safeDelete();
                return next;
            }
        }
        return this;
    }
}
