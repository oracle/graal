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

import com.oracle.max.graal.compiler.debug.*;
import com.oracle.max.graal.compiler.util.*;
import com.oracle.max.graal.graph.*;
import com.oracle.max.graal.nodes.spi.*;
import com.sun.cri.ci.*;

/**
 * Denotes the beginning of a basic block, and holds information
 * about the basic block, including the successor and
 * predecessor blocks, exception handlers, liveness information, etc.
 */
public class MergeNode extends StateSplit {

    @Input    private final NodeInputList<EndNode> ends = new NodeInputList<EndNode>(this);

    public MergeNode(Graph graph) {
        super(CiKind.Illegal, graph);
    }

    @Override
    public boolean needsStateAfter() {
        return false;
    }

    @Override
    public void accept(ValueVisitor v) {
        v.visitMerge(this);
    }

    public int endIndex(EndNode end) {
        return ends.indexOf(end);
    }

    public void addEnd(EndNode end) {
        ends.add(end);
    }

    public int endCount() {
        return ends.size();
    }

    public EndNode endAt(int index) {
        return ends.get(index);
    }

    public Iterable<? extends Node> phiPredecessors() {
        return ends;
    }

    @Override
    public Iterable<EndNode> cfgPredecessors() {
        return ends;
    }

    @Override
    public Iterable< ? extends Node> dataInputs() {
        return Collections.emptyList();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("merge #");
        builder.append(id());
        builder.append(" [");

        builder.append("]");

        builder.append(" -> ");
        boolean hasSucc = false;
        for (Node s : this.successors()) {
            if (hasSucc) {
                builder.append(", ");
            }
            builder.append("#");
            if (s != null) {
                builder.append(s.id());
            } else {
                builder.append("null");
            }
            hasSucc = true;
        }
        return builder.toString();
    }

    public void printWithoutPhis(LogStream out) {
        // print block id
        out.print("B").print(id()).print(" ");

        // print flags
        StringBuilder sb = new StringBuilder(8);
        if (sb.length() != 0) {
            out.print('(').print(sb.toString()).print(')');
        }

        // print block bci range
        out.print('[').print(-1).print(", ").print(-1).print(']');

        // print block successors
        //if (end != null && end.blockSuccessors().size() > 0) {
            out.print(" .");
            for (Node successor : this.successors()) {
                if (successor instanceof ValueNode) {
                    out.print((ValueNode) successor);
                } else {
                    out.print(successor.toString());
                }
            }
        //}

        // print predecessors
//        if (!blockPredecessors().isEmpty()) {
//            out.print(" pred:");
//            for (Instruction pred : blockPredecessors()) {
//                out.print(pred.block());
//            }
//        }
    }

    /**
     * Determines if a given instruction is a phi whose {@linkplain PhiNode#merge() join block} is a given block.
     *
     * @param value the instruction to test
     * @param block the block that may be the join block of {@code value} if {@code value} is a phi
     * @return {@code true} if {@code value} is a phi and its join block is {@code block}
     */
    private boolean isPhiAtBlock(ValueNode value) {
        return value instanceof PhiNode && ((PhiNode) value).merge() == this;
    }


    /**
     * Formats a given instruction as a value in a {@linkplain FrameState frame state}. If the instruction is a phi defined at a given
     * block, its {@linkplain PhiNode#valueCount() inputs} are appended to the returned string.
     *
     * @param index the index of the value in the frame state
     * @param value the frame state value
     * @param block if {@code value} is a phi, then its inputs are formatted if {@code block} is its
     *            {@linkplain PhiNode#merge() join point}
     * @return the instruction representation as a string
     */
    public String stateString(int index, ValueNode value) {
        StringBuilder sb = new StringBuilder(30);
        sb.append(String.format("%2d  %s", index, Util.valueString(value)));
        if (value instanceof PhiNode) {
            PhiNode phi = (PhiNode) value;
            // print phi operands
            if (phi.merge() == this) {
                sb.append(" [");
                for (int j = 0; j < phi.valueCount(); j++) {
                    sb.append(' ');
                    ValueNode operand = phi.valueAt(j);
                    if (operand != null) {
                        sb.append(Util.valueString(operand));
                    } else {
                        sb.append("NULL");
                    }
                }
                sb.append("] ");
            }
        }
        return sb.toString();
    }

    public void removeEnd(EndNode pred) {
        int predIndex = ends.indexOf(pred);
        assert predIndex != -1;
        ends.remove(predIndex);

        for (Node usage : usages()) {
            if (usage instanceof PhiNode) {
                ((PhiNode) usage).removeInput(predIndex);
            }
        }
    }

    public int phiPredecessorCount() {
        return endCount();
    }

    public int phiPredecessorIndex(Node pred) {
        EndNode end = (EndNode) pred;
        return endIndex(end);
    }

    public Node phiPredecessorAt(int index) {
        return endAt(index);
    }

    public Collection<PhiNode> phis() {
        return Util.filter(this.usages(), PhiNode.class);
    }
}
