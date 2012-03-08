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
package com.oracle.graal.nodes;

import static com.oracle.graal.graph.iterators.NodePredicates.*;

import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.spi.*;

/**
 * Denotes the merging of multiple control-flow paths.
 */
public class MergeNode extends BeginNode implements Node.IterableNodeType, LIRLowerable {

    @Input(notDataflow = true) private final NodeInputList<EndNode> ends = new NodeInputList<>(this);

    @Override
    public boolean needsStateAfter() {
        return false;
    }

    @Override
    public void generate(LIRGeneratorTool gen) {
        gen.visitMerge(this);
    }

    public int forwardEndIndex(EndNode end) {
        return ends.indexOf(end);
    }

    public void addForwardEnd(EndNode end) {
        ends.add(end);
    }

    public int forwardEndCount() {
        return ends.size();
    }

    public EndNode forwardEndAt(int index) {
        return ends.get(index);
    }

    @Override
    public NodeIterable<EndNode> cfgPredecessors() {
        return ends;
    }

    /**
     * Determines if a given node is a phi whose {@linkplain PhiNode#merge() merge} is this node.
     *
     * @param value the instruction to test
     * @return {@code true} if {@code value} is a phi and its merge is {@code this}
     */
    public boolean isPhiAtMerge(Node value) {
        return value instanceof PhiNode && ((PhiNode) value).merge() == this;
    }

    /**
     * Removes the given end from the merge, along with the entries corresponding to this end in the phis connected to the merge.
     * @param pred the end to remove
     */
    public void removeEnd(EndNode pred) {
        int predIndex = phiPredecessorIndex(pred);
        assert predIndex != -1;
        deleteEnd(pred);
        for (PhiNode phi : phis()) {
            phi.removeInput(predIndex);
        }
    }

    protected void deleteEnd(EndNode end) {
        ends.remove(end);
    }

    public void clearEnds() {
        ends.clear();
    }

    public NodeIterable<EndNode> forwardEnds() {
        return ends;
    }

    public int phiPredecessorCount() {
        return forwardEndCount();
    }

    public int phiPredecessorIndex(EndNode pred) {
        return forwardEndIndex(pred);
    }

    public EndNode phiPredecessorAt(int index) {
        return forwardEndAt(index);
    }

    public NodeIterable<PhiNode> phis() {
        return this.usages().filter(PhiNode.class);
    }

    @Override
    public void simplify(SimplifierTool tool) {
        FixedNode next = next();
        if (next instanceof LoopEndNode) {
            LoopEndNode origLoopEnd = (LoopEndNode) next;
            LoopBeginNode begin = origLoopEnd.loopBegin();
            for (PhiNode phi : phis()) {
                for (Node usage : phi.usages().filter(isNotA(FrameState.class))) {
                    if (!begin.isPhiAtMerge(usage)) {
                        return;
                    }
                }
            }
            Debug.log("Split %s into loop ends for %s", this, begin);
            int numEnds = this.forwardEndCount();
            StructuredGraph graph = (StructuredGraph) graph();
            for (int i = 0; i < numEnds - 1; i++) {
                EndNode end = forwardEndAt(numEnds - 1 - i);
                LoopEndNode loopEnd = graph.add(new LoopEndNode(begin));
                for (PhiNode phi : begin.phis()) {
                    ValueNode v = phi.valueAt(origLoopEnd);
                    ValueNode newInput;
                    if (isPhiAtMerge(v)) {
                        PhiNode endPhi = (PhiNode) v;
                        newInput = endPhi.valueAt(end);
                    } else {
                        newInput = v;
                    }
                    phi.addInput(newInput);
                }
                this.removeEnd(end);
                end.replaceAtPredecessors(loopEnd);
                end.safeDelete();
                tool.addToWorkList(loopEnd.predecessor());
            }
            graph.reduceTrivialMerge(this);
        }
    }
}
