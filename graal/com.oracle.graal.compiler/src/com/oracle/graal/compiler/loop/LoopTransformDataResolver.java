/*
 * Copyright (c) 2012, 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.loop;

import java.util.*;
import java.util.Map.Entry;

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.NodeClass.NodeClassIterator;
import com.oracle.graal.graph.NodeClass.Position;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;



public class LoopTransformDataResolver {
    private List<ResolvableSuperBlock> resolvables = new LinkedList<>();

    private abstract static class ResolvableSuperBlock {
        final SuperBlock block;
        public ResolvableSuperBlock(SuperBlock block) {
            this.block = block;
        }
        public abstract void resolve();
    }

    private static class PeeledResolvableSuperBlock extends ResolvableSuperBlock{
        final SuperBlock peel;
        final boolean nextIteration;
        public PeeledResolvableSuperBlock(SuperBlock peeled, SuperBlock peel, boolean nextIteration) {
            super(peeled);
            this.peel = peel;
            this.nextIteration = nextIteration;
        }
        @Override
        public void resolve() {
            if (nextIteration) {
                SuperBlock peeled = block;
                LoopBeginNode loopBegin = (LoopBeginNode) peeled.getEntry();
                Map<Node, Node> dup = peel.getDuplicationMapping();
                List<PhiNode> newPhis = new LinkedList<>();
                for (PhiNode phi : loopBegin.phis().snapshot()) {
                    ValueNode first = null;
                    StructuredGraph graph = (StructuredGraph) loopBegin.graph();
                    if (loopBegin.loopEnds().count() == 1) {
                        ValueNode b = phi.valueAt(loopBegin.loopEnds().first()); // back edge value
                        first = prim(b); // corresponding value in the peel
                    } else {
                        Map<EndNode, LoopEndNode> reverseEnds = new HashMap<>(); // map peel's exit to the corresponding loop exits
                        MergeNode merge = null; // look for the merge if the peel's exits
                        for (LoopEndNode end : loopBegin.loopEnds()) {
                            EndNode newEnd = (EndNode) dup.get(end);
                            if (newEnd != null) {
                                reverseEnds.put(newEnd, end);
                                if (prim(phi.valueAt(end)) != null) {
                                    merge = newEnd.merge();
                                }
                            }
                        }
                        if (merge != null) { // found values of interest (backedge values that exist in the peel)
                            PhiNode firstPhi = graph.add(new PhiNode(phi.kind(), merge, phi.type()));
                            for (EndNode end : merge.forwardEnds()) {
                                LoopEndNode loopEnd = reverseEnds.get(end);
                                ValueNode prim = prim(phi.valueAt(loopEnd));
                                assert prim != null;
                                firstPhi.addInput(prim);
                            }
                            first = firstPhi;
                            merge.stateAfter().replaceFirstInput(phi, firstPhi); // fix the merge's state after (see SuperBlock.mergeExits)
                            if (phi.type() == PhiType.Virtual) {
                                first = SuperBlock.mergeVirtualChain(graph, firstPhi, merge);
                            }
                        }
                    }
                    if (first != null) { // create a new phi (we don't patch the old one since some usages of the old one may still be valid)
                        PhiNode newPhi = graph.add(new PhiNode(phi.kind(), loopBegin, phi.type()));
                        newPhi.addInput(first);
                        for (LoopEndNode end : loopBegin.orderedLoopEnds()) {
                            newPhi.addInput(phi.valueAt(end));
                        }
                        dup.put(phi, newPhi);
                        newPhis.add(newPhi);
                        for (Node usage : phi.usages().snapshot()) {
                            if (dup.get(usage) != null) { // patch only usages that should use the new phi ie usages that were peeled
                                usage.replaceFirstInput(phi, newPhi);
                            }
                        }
                    }
                }
                // check new phis to see if they have as input some old phis, replace those inputs with the new corresponding phis
                for (PhiNode phi : newPhis) {
                    for (int i = 0; i < phi.valueCount(); i++) {
                        ValueNode v = phi.valueAt(i);
                        if (loopBegin.isPhiAtMerge(v)) {
                            PhiNode newV = (PhiNode) dup.get(v);
                            if (newV != null) {
                                phi.setValueAt(i, newV);
                            }
                        }
                    }
                }
            }
        }

        /**
         * Gets the corresponding value in the peel.
         * @param b original value
         * @return corresponding value in the peel
         */
        public ValueNode prim(ValueNode b) {
            SuperBlock peeled = block;
            LoopBeginNode loopBegin = (LoopBeginNode) peeled.getEntry();
            Map<Node, Node> dup = peel.getDuplicationMapping();
            if (loopBegin.isPhiAtMerge(b)) {
                PhiNode phi = (PhiNode) b;
                return phi.valueAt(loopBegin.forwardEnd());
            } else {
                ValueNode v = (ValueNode) dup.get(b);
                if (v == null && nextIteration) {
                    // may not be right in inversion case
                    return b;
                }
                return v;
            }
        }
    }

    private static class PeelResolvableSuperBlock extends ResolvableSuperBlock{
        final SuperBlock peeled;
        public PeelResolvableSuperBlock(SuperBlock peel, SuperBlock peeled) {
            super(peel);
            this.peeled = peeled;
        }
        @Override
        public void resolve() {
            SuperBlock peel = block;
            LoopBeginNode loopBegin = (LoopBeginNode) peeled.getEntry();
            for (Entry<Node, Node> entry : peel.getDuplicationMapping().entrySet()) {
                Node oriNode = entry.getKey();
                Node newNode = entry.getValue();
                for (NodeClassIterator iter = oriNode.inputs().iterator(); iter.hasNext();) {
                    Position pos = iter.nextPosition();
                    if (pos.isValidFor(newNode, oriNode) && pos.get(newNode) == null) {
                        Node oriInput = pos.get(oriNode);
                        // oriInput is not checked against null because oriNode.inputs().iterator() only iterates over non-null pos/input
                        Node v;
                        if (loopBegin.isPhiAtMerge(oriInput)) {
                            PhiNode phi = (PhiNode) oriInput;
                            v = phi.valueAt(loopBegin.forwardEnd());
                        } else {
                            v = oriInput;
                        }
                        pos.set(newNode, v);
                    }
                }
            }
        }
    }

    public class WholeLoop {
        private final SuperBlock from;
        public WholeLoop(SuperBlock from) {
            this.from = from;
        }
        public void peeled(SuperBlock peel) {
            resolvables.add(new PeelResolvableSuperBlock(peel, from));
            resolvables.add(new PeeledResolvableSuperBlock(from, peel, true));
        }

    }

    public void resolve() {
        for (ResolvableSuperBlock resolvable : this.resolvables) {
            resolvable.resolve();
        }
    }

    public WholeLoop wholeLoop(SuperBlock block) {
        return new WholeLoop(block);
    }
}
