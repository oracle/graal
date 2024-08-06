/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.graal.compiler.replacements.nodes.arithmetic;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.ProfileData.BranchProbabilityData;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
import jdk.graal.compiler.nodes.spi.LIRLowerable;
import jdk.graal.compiler.nodes.spi.NodeLIRBuilderTool;
import jdk.graal.compiler.nodes.spi.Simplifiable;

import jdk.vm.ci.meta.Value;

@NodeInfo
public abstract class IntegerExactArithmeticSplitNode extends ControlSplitNode implements Simplifiable, LIRLowerable {
    public static final NodeClass<IntegerExactArithmeticSplitNode> TYPE = NodeClass.create(IntegerExactArithmeticSplitNode.class);

    @Successor AbstractBeginNode next;
    @Successor AbstractBeginNode overflowSuccessor;

    protected IntegerExactArithmeticSplitNode(NodeClass<? extends IntegerExactArithmeticSplitNode> c, Stamp stamp,
                    AbstractBeginNode next, AbstractBeginNode overflowSuccessor) {
        super(c, stamp);
        this.overflowSuccessor = overflowSuccessor;
        this.next = next;
    }

    @Override
    public AbstractBeginNode getPrimarySuccessor() {
        return next;
    }

    @Override
    public double probability(AbstractBeginNode successor) {
        return successor == next ? getProfileData().getDesignatedSuccessorProbability() : getProfileData().getNegatedProbability();
    }

    @Override
    public boolean setProbability(AbstractBeginNode successor, BranchProbabilityData profileData) {
        // Successor probabilities for arithmetic split nodes are fixed.
        return false;
    }

    @Override
    public BranchProbabilityData getProfileData() {
        return BranchProbabilityNode.ALWAYS_TAKEN_PROFILE;
    }

    public AbstractBeginNode getNext() {
        return next;
    }

    public AbstractBeginNode getOverflowSuccessor() {
        return overflowSuccessor;
    }

    public void setNext(AbstractBeginNode next) {
        updatePredecessor(this.next, next);
        this.next = next;
    }

    @Override
    public void generate(NodeLIRBuilderTool generator) {
        generator.setResult(this, generateArithmetic(generator));
        generator.emitOverflowCheckBranch(getOverflowSuccessor(), getNext(), stamp, probability(getOverflowSuccessor()));
    }

    protected abstract Value generateArithmetic(NodeLIRBuilderTool generator);

    @Override
    public int getSuccessorCount() {
        return 2;
    }
}
