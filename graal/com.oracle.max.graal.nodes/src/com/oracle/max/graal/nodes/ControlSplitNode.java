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

import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.type.*;

/**
 * The {@code ControlSplitNode} is a base class for all instructions that split the control flow (ie. have more than one successor).
 */
public abstract class ControlSplitNode extends FixedNode {

    @Successor private final NodeSuccessorList<BeginNode> blockSuccessors;

    public BeginNode blockSuccessor(int index) {
        return blockSuccessors.get(index);
    }

    public void setBlockSuccessor(int index, BeginNode x) {
        blockSuccessors.set(index, x);
    }

    public int blockSuccessorCount() {
        return blockSuccessors.size();
    }

    protected final double[] branchProbability;

    public ControlSplitNode(Stamp stamp, BeginNode[] blockSuccessors, double[] branchProbability) {
        super(stamp);
        assert branchProbability.length == blockSuccessors.length;
        this.blockSuccessors = new NodeSuccessorList<BeginNode>(this, blockSuccessors);
        this.branchProbability = branchProbability;
    }

    public double probability(int successorIndex) {
        return branchProbability[successorIndex];
    }

    public void setProbability(int successorIndex, double x) {
        branchProbability[successorIndex] = x;
    }

    /**
     * Gets the successor corresponding to the default (fall through) case.
     * @return the default successor
     */
    public FixedNode defaultSuccessor() {
        return blockSuccessor(blockSuccessorCount() - 1);
    }

    public Iterable<BeginNode> blockSuccessors() {
        return new Iterable<BeginNode>() {
            @Override
            public Iterator<BeginNode> iterator() {
                return new Iterator<BeginNode>() {
                    int i = 0;
                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                    @Override
                    public BeginNode next() {
                        return ControlSplitNode.this.blockSuccessor(i++);
                    }

                    @Override
                    public boolean hasNext() {
                        return i < ControlSplitNode.this.blockSuccessorCount();
                    }
                };
            }
        };
    }

    @Override
    public Map<Object, Object> getDebugProperties() {
        Map<Object, Object> properties = super.getDebugProperties();
        StringBuilder str = new StringBuilder();
        for (int i = 0; i < branchProbability.length; i++) {
            str.append(i == 0 ? "" : ", ").append(String.format(Locale.ENGLISH, "%7.5f", branchProbability[i]));
        }
        properties.put("branchProbability", str.toString());
        return properties;
    }
}
