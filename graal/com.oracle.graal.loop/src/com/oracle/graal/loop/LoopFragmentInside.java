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
package com.oracle.graal.loop;

import static com.oracle.graal.graph.util.CollectionsAccess.*;

import java.util.*;

import com.oracle.graal.compiler.common.*;
import com.oracle.graal.graph.Graph.DuplicationReplacement;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.VirtualState.NodeClosure;
import com.oracle.graal.nodes.util.*;

public class LoopFragmentInside extends LoopFragment {

    /**
     * mergedInitializers. When an inside fragment's (loop)ends are merged to create a unique exit
     * point, some phis must be created : they phis together all the back-values of the loop-phis
     * These can then be used to update the loop-phis' forward edge value ('initializer') in the
     * peeling case. In the unrolling case they will be used as the value that replace the loop-phis
     * of the duplicated inside fragment
     */
    private Map<ValuePhiNode, ValueNode> mergedInitializers;
    private final DuplicationReplacement dataFixBefore = new DuplicationReplacement() {

        @Override
        public Node replacement(Node oriInput) {
            if (!(oriInput instanceof ValueNode)) {
                return oriInput;
            }
            return prim((ValueNode) oriInput);
        }
    };

    public LoopFragmentInside(LoopEx loop) {
        super(loop);
    }

    public LoopFragmentInside(LoopFragmentInside original) {
        super(null, original);
    }

    @Override
    public LoopFragmentInside duplicate() {
        assert !isDuplicate();
        return new LoopFragmentInside(this);
    }

    @Override
    public LoopFragmentInside original() {
        return (LoopFragmentInside) super.original();
    }

    @SuppressWarnings("unused")
    public void appendInside(LoopEx loop) {
        // TODO (gd)
    }

    @Override
    public LoopEx loop() {
        assert !this.isDuplicate();
        return super.loop();
    }

    @Override
    public void insertBefore(LoopEx loop) {
        assert this.isDuplicate() && this.original().loop() == loop;

        patchNodes(dataFixBefore);

        BeginNode end = mergeEnds();

        mergeEarlyExits();

        original().patchPeeling(this);

        BeginNode entry = getDuplicatedNode(loop.loopBegin());
        loop.entryPoint().replaceAtPredecessor(entry);
        end.setNext(loop.entryPoint());
    }

    @Override
    public NodeBitMap nodes() {
        if (nodes == null) {
            LoopFragmentWhole whole = loop().whole();
            whole.nodes(); // init nodes bitmap in whole
            nodes = whole.nodes.copy();
            // remove the phis
            LoopBeginNode loopBegin = loop().loopBegin();
            for (PhiNode phi : loopBegin.phis()) {
                nodes.clear(phi);
            }
            clearStateNodes(loopBegin);
            for (LoopExitNode exit : exits()) {
                clearStateNodes(exit);
                for (ProxyNode proxy : exit.proxies()) {
                    nodes.clear(proxy);
                }
            }
        }
        return nodes;
    }

    private void clearStateNodes(StateSplit stateSplit) {
        FrameState loopState = stateSplit.stateAfter();
        if (loopState != null) {
            loopState.applyToVirtual(v -> {
                if (v.usages().filter(n -> nodes.isMarked(n) && n != stateSplit).isEmpty()) {
                    nodes.clear(v);
                }
            });
        }
    }

    public NodeIterable<LoopExitNode> exits() {
        return loop().loopBegin().loopExits();
    }

    @Override
    protected DuplicationReplacement getDuplicationReplacement() {
        final LoopBeginNode loopBegin = loop().loopBegin();
        final StructuredGraph graph = graph();
        return new DuplicationReplacement() {

            private HashMap<Node, Node> seenNode = new HashMap<>();

            @Override
            public Node replacement(Node original) {
                if (original == loopBegin) {
                    Node value = seenNode.get(original);
                    if (value != null) {
                        return value;
                    }
                    BeginNode newValue = graph.add(BeginNode.create());
                    seenNode.put(original, newValue);
                    return newValue;
                }
                if (original instanceof LoopExitNode && ((LoopExitNode) original).loopBegin() == loopBegin) {
                    Node value = seenNode.get(original);
                    if (value != null) {
                        return value;
                    }
                    BeginNode newValue = graph.add(BeginNode.create());
                    seenNode.put(original, newValue);
                    return newValue;
                }
                if (original instanceof LoopEndNode && ((LoopEndNode) original).loopBegin() == loopBegin) {
                    Node value = seenNode.get(original);
                    if (value != null) {
                        return value;
                    }
                    EndNode newValue = graph.add(EndNode.create());
                    seenNode.put(original, newValue);
                    return newValue;
                }
                return original;
            }
        };
    }

    @Override
    protected void finishDuplication() {
        // TODO (gd) ?
    }

    private static PhiNode patchPhi(StructuredGraph graph, PhiNode phi, MergeNode merge) {
        PhiNode ret;
        if (phi instanceof ValuePhiNode) {
            ret = ValuePhiNode.create(phi.stamp(), merge);
        } else if (phi instanceof GuardPhiNode) {
            ret = GuardPhiNode.create(merge);
        } else if (phi instanceof MemoryPhiNode) {
            ret = MemoryPhiNode.create(merge, ((MemoryPhiNode) phi).getLocationIdentity());
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }
        return graph.addWithoutUnique(ret);
    }

    private void patchPeeling(LoopFragmentInside peel) {
        LoopBeginNode loopBegin = loop().loopBegin();
        StructuredGraph graph = loopBegin.graph();
        List<PhiNode> newPhis = new LinkedList<>();

        NodeBitMap usagesToPatch = nodes.copy();
        for (LoopExitNode exit : exits()) {
            markStateNodes(exit, usagesToPatch);
            for (ProxyNode proxy : exit.proxies()) {
                usagesToPatch.markAndGrow(proxy);
            }
        }
        markStateNodes(loopBegin, usagesToPatch);

        for (PhiNode phi : loopBegin.phis().snapshot()) {
            if (phi.usages().isEmpty()) {
                continue;
            }
            ValueNode first;
            if (loopBegin.loopEnds().count() == 1) {
                ValueNode b = phi.valueAt(loopBegin.loopEnds().first()); // back edge value
                first = peel.prim(b); // corresponding value in the peel
            } else {
                first = peel.mergedInitializers.get(phi);
            }
            // create a new phi (we don't patch the old one since some usages of the old one may
            // still be valid)
            PhiNode newPhi = patchPhi(graph, phi, loopBegin);
            newPhi.addInput(first);
            for (LoopEndNode end : loopBegin.orderedLoopEnds()) {
                newPhi.addInput(phi.valueAt(end));
            }
            peel.putDuplicatedNode(phi, newPhi);
            newPhis.add(newPhi);
            for (Node usage : phi.usages().snapshot()) {
                // patch only usages that should use the new phi ie usages that were peeled
                if (usagesToPatch.isMarkedAndGrow(usage)) {
                    usage.replaceFirstInput(phi, newPhi);
                }
            }
        }
        // check new phis to see if they have as input some old phis, replace those inputs with the
        // new corresponding phis
        for (PhiNode phi : newPhis) {
            for (int i = 0; i < phi.valueCount(); i++) {
                ValueNode v = phi.valueAt(i);
                if (loopBegin.isPhiAtMerge(v)) {
                    PhiNode newV = peel.getDuplicatedNode((ValuePhiNode) v);
                    if (newV != null) {
                        phi.setValueAt(i, newV);
                    }
                }
            }
        }

        for (PhiNode deadPhi : loopBegin.phis().filter(n -> n.usages().isEmpty()).snapshot()) {
            if (deadPhi.isAlive()) {
                GraphUtil.killWithUnusedFloatingInputs(deadPhi);
            }
        }
    }

    private static void markStateNodes(StateSplit stateSplit, NodeBitMap marks) {
        FrameState exitState = stateSplit.stateAfter();
        if (exitState != null) {
            exitState.applyToVirtual(v -> marks.markAndGrow(v));
        }
    }

    /**
     * Gets the corresponding value in this fragment.
     *
     * @param b original value
     * @return corresponding value in the peel
     */
    @Override
    protected ValueNode prim(ValueNode b) {
        assert isDuplicate();
        LoopBeginNode loopBegin = original().loop().loopBegin();
        if (loopBegin.isPhiAtMerge(b)) {
            PhiNode phi = (PhiNode) b;
            return phi.valueAt(loopBegin.forwardEnd());
        } else if (nodesReady) {
            ValueNode v = getDuplicatedNode(b);
            if (v == null) {
                return b;
            }
            return v;
        } else {
            return b;
        }
    }

    private BeginNode mergeEnds() {
        assert isDuplicate();
        List<AbstractEndNode> endsToMerge = new LinkedList<>();
        Map<AbstractEndNode, LoopEndNode> reverseEnds = new HashMap<>(); // map peel's exit to the
        // corresponding loop exits
        LoopBeginNode loopBegin = original().loop().loopBegin();
        for (LoopEndNode le : loopBegin.loopEnds()) {
            AbstractEndNode duplicate = getDuplicatedNode(le);
            if (duplicate != null) {
                endsToMerge.add(duplicate);
                reverseEnds.put(duplicate, le);
            }
        }
        mergedInitializers = newNodeIdentityMap();
        BeginNode newExit;
        StructuredGraph graph = graph();
        if (endsToMerge.size() == 1) {
            AbstractEndNode end = endsToMerge.get(0);
            assert end.usages().isEmpty();
            newExit = graph.add(BeginNode.create());
            end.replaceAtPredecessor(newExit);
            end.safeDelete();
        } else {
            assert endsToMerge.size() > 1;
            MergeNode newExitMerge = graph.add(MergeNode.create());
            newExit = newExitMerge;
            FrameState state = loopBegin.stateAfter();
            FrameState duplicateState = null;
            if (state != null) {
                duplicateState = state.duplicateWithVirtualState();
                newExitMerge.setStateAfter(duplicateState);
            }
            for (AbstractEndNode end : endsToMerge) {
                newExitMerge.addForwardEnd(end);
            }

            for (final PhiNode phi : loopBegin.phis().snapshot()) {
                if (phi.usages().isEmpty()) {
                    continue;
                }
                final PhiNode firstPhi = patchPhi(graph, phi, newExitMerge);
                for (AbstractEndNode end : newExitMerge.forwardEnds()) {
                    LoopEndNode loopEnd = reverseEnds.get(end);
                    ValueNode prim = prim(phi.valueAt(loopEnd));
                    assert prim != null;
                    firstPhi.addInput(prim);
                }
                ValueNode initializer = firstPhi;
                if (duplicateState != null) {
                    // fix the merge's state after
                    duplicateState.applyToNonVirtual(new NodeClosure<ValueNode>() {

                        @Override
                        public void apply(Node from, ValueNode node) {
                            if (node == phi) {
                                from.replaceFirstInput(phi, firstPhi);
                            }
                        }
                    });
                }
                mergedInitializers.put((ValuePhiNode) phi, initializer);
            }
        }
        return newExit;
    }
}
