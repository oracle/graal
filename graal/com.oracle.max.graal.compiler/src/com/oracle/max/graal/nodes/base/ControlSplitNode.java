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
package com.oracle.max.graal.nodes.base;

import java.util.*;

import com.oracle.max.graal.graph.*;
import com.sun.cri.ci.*;

/**
 * The {@code BlockEnd} instruction is a base class for all instructions that end a basic
 * block, including branches, switches, throws, and goto's.
 */
public abstract class ControlSplitNode extends FixedNode {

    @Successor    private final NodeSuccessorList<FixedNode> blockSuccessors;

    public FixedNode blockSuccessor(int index) {
        return blockSuccessors.get(index);
    }

    public void setBlockSuccessor(int index, FixedNode x) {
        blockSuccessors.set(index, x);
    }

    public int blockSuccessorCount() {
        return blockSuccessors.size();
    }

    protected final double[] branchProbability;

    /**
     * Constructs a new block end with the specified value type.
     * @param kind the type of the value produced by this instruction
     * @param successors the list of successor blocks. If {@code null}, a new one will be created.
     */
    public ControlSplitNode(CiKind kind, List<? extends FixedNode> blockSuccessors, double[] branchProbability, Graph graph) {
        this(kind, blockSuccessors.size(), branchProbability, graph);
        for (int i = 0; i < blockSuccessors.size(); i++) {
            setBlockSuccessor(i, blockSuccessors.get(i));
        }
    }

    public ControlSplitNode(CiKind kind, int blockSuccessorCount, double[] branchProbability, Graph graph) {
        super(kind, graph);
        this.blockSuccessors = new NodeSuccessorList<FixedNode>(this, blockSuccessorCount);
        assert branchProbability.length == blockSuccessorCount;
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

    public Iterable<FixedNode> blockSuccessors() {
        return new Iterable<FixedNode>() {
            @Override
            public Iterator<FixedNode> iterator() {
                return new Iterator<FixedNode>() {
                    int i = 0;
                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                    @Override
                    public FixedNode next() {
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
            str.append(i == 0 ? "" : ", ").append(String.format("%7.5f", branchProbability[i]));
        }
        properties.put("branchProbability", str.toString());
        return properties;
    }
}
