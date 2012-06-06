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

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Graph.DuplicationReplacement;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.lir.cfg.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.PhiNode.PhiType;
import com.oracle.graal.nodes.util.*;


public abstract class LoopFragment {
    private final LoopEx loop;
    private final LoopFragment original;
    protected NodeBitMap nodes;
    protected boolean nodesReady;
    private Map<Node, Node> duplicationMap;

    public LoopFragment(LoopEx loop) {
        this(loop, null);
        this.nodesReady = true;
    }

    public LoopFragment(LoopEx loop, LoopFragment original) {
        this.loop = loop;
        this.original = original;
        this.nodesReady = false;
    }

    public LoopEx loop() {
        return loop;
    }

    public abstract LoopFragment duplicate();

    public abstract void insertBefore(LoopEx l);

    public void disconnect() {
        // TODO (gd) possibly abstract
    }

    public boolean contains(Node n) {
        return nodes().contains(n);
    }

    @SuppressWarnings("unchecked")
    public <New extends Node, Old extends New> New getDuplicatedNode(Old n) {
        assert isDuplicate();
        return (New) duplicationMap.get(n);
    }

    protected <New extends Node, Old extends New> void putDuplicatedNode(Old oldNode, New newNode) {
        duplicationMap.put(oldNode, newNode);
    }

    public boolean isDuplicate() {
        return original != null;
    }

    public LoopFragment original() {
        return original;
    }

    public abstract NodeIterable<Node> nodes();

    public StructuredGraph graph() {
        return (StructuredGraph) loop.loopBegin().graph();
    }

    protected abstract DuplicationReplacement getDuplicationReplacement();

    protected abstract void finishDuplication();

    protected void patchNodes(final DuplicationReplacement dataFix) {
        if (isDuplicate() && !nodesReady) {
            assert !original.isDuplicate();
            final DuplicationReplacement cfgFix = getDuplicationReplacement();
            DuplicationReplacement dr;
            if (cfgFix == null) {
                dr = dataFix;
            } else {
                dr = new DuplicationReplacement() {
                    @Override
                    public Node replacement(Node o) {
                        Node r1 = dataFix.replacement(o);
                        if (r1 != o) {
                            assert cfgFix.replacement(o) == o;
                            return r1;
                        }
                        Node r2 = cfgFix.replacement(o);
                        if (r2 != o) {
                            return r2;
                        }
                        return o;
                    }
                };
            }
            duplicationMap = graph().addDuplicates(nodes(), dr);
            finishDuplication();
            nodesReady = true;
        } else {
            //TODO (gd) apply fix ?
        }
    }

    public static Collection<BeginNode> toHirBlocks(Collection<Block> blocks) {
        List<BeginNode> hir = new ArrayList<>(blocks.size());
        for (Block b : blocks) {
            hir.add(b.getBeginNode());
        }
        return hir;
    }

    protected static NodeBitMap computeNodes(Graph graph, Collection<BeginNode> blocks) {
        return computeNodes(graph, blocks, Collections.<BeginNode>emptyList());
    }

    protected static NodeBitMap computeNodes(Graph graph, Collection<BeginNode> blocks, Collection<BeginNode> earlyExits) {
        NodeBitMap nodes = graph.createNodeBitMap(true);
        for (BeginNode b : blocks) {
            for (Node n : b.getBlockNodes()) {
                if (n instanceof Invoke) {
                    nodes.mark(((Invoke) n).callTarget());
                }
                if (n instanceof StateSplit) {
                    FrameState stateAfter = ((StateSplit) n).stateAfter();
                    if (stateAfter != null) {
                        nodes.mark(stateAfter);
                    }
                }
                nodes.mark(n);
            }
        }
        for (BeginNode earlyExit : earlyExits) {
            FrameState stateAfter = earlyExit.stateAfter();
            assert stateAfter != null;
            nodes.mark(stateAfter);
            nodes.mark(earlyExit);
            for (ValueProxyNode proxy : earlyExit.proxies()) {
                nodes.mark(proxy);
            }
        }

        for (BeginNode b : blocks) {
            for (Node n : b.getBlockNodes()) {
                for (Node usage : n.usages()) {
                    markFloating(usage, nodes);
                }
            }
        }

        return nodes;
    }

    private static boolean markFloating(Node n, NodeBitMap loopNodes) {
        if (loopNodes.isMarked(n)) {
            return true;
        }
        if (n instanceof FixedNode) {
            return false;
        }
        boolean mark = false;
        if (n instanceof PhiNode) {
            PhiNode phi = (PhiNode) n;
            mark = loopNodes.isMarked(phi.merge());
            if (mark) {
                loopNodes.mark(n);
            } else {
                return false;
            }
        }
        for (Node usage : n.usages()) {
            if (markFloating(usage, loopNodes)) {
                mark = true;
            }
        }
        if (mark) {
            loopNodes.mark(n);
            return true;
        }
        return false;
    }

    /**
     * Merges the early exits (i.e. loop exits) that were duplicated as part of this fragment, with the original fragment's exits.
     */
    protected void mergeEarlyExits() {
        assert isDuplicate();
        StructuredGraph graph = graph();
        for (BeginNode earlyExit : toHirBlocks(original().loop().lirLoop().exits)) {
            if (!this.contains(earlyExit)) {
                continue;
            }
            BeginNode newEarlyExit = getDuplicatedNode(earlyExit);
            assert newEarlyExit != null;
            MergeNode merge = graph.add(new MergeNode());
            EndNode originalEnd = graph.add(new EndNode());
            EndNode newEnd = graph.add(new EndNode());
            merge.addForwardEnd(originalEnd);
            merge.addForwardEnd(newEnd);
            FixedNode next = earlyExit.next();
            earlyExit.setNext(originalEnd);
            newEarlyExit.setNext(newEnd);
            merge.setNext(next);
            FrameState exitState = earlyExit.stateAfter();
            FrameState state = exitState.duplicate();
            merge.setStateAfter(state);

            for (Node anchored : earlyExit.anchored().snapshot()) {
                anchored.replaceFirstInput(earlyExit, merge);
            }

            for (ValueProxyNode vpn : earlyExit.proxies().snapshot()) {
                ValueNode replaceWith;
                ValueProxyNode newVpn = getDuplicatedNode(vpn);
                if (newVpn != null) {
                    PhiNode phi = graph.add(vpn.type() == PhiType.Value ? new PhiNode(vpn.kind(), merge) : new PhiNode(vpn.type(), merge));
                    phi.addInput(vpn);
                    phi.addInput(newVpn);
                    if (vpn.type() == PhiType.Value) {
                        replaceWith = phi;
                    } else {
                        assert vpn.type() == PhiType.Virtual;
                        ValueNode vof = GraphUtil.unProxify(vpn);
                        ValueNode newVof = GraphUtil.unProxify(newVpn);
                        replaceWith = GraphUtil.mergeVirtualChain(graph, vof, newVof, phi, earlyExit, newEarlyExit, merge);
                    }
                } else {
                    replaceWith = vpn.value();
                }
                state.replaceFirstInput(vpn, replaceWith);
                for (Node usage : vpn.usages().snapshot()) {
                    if (usage != exitState && !merge.isPhiAtMerge(usage)) {
                        usage.replaceFirstInput(vpn, replaceWith);
                    }
                }
            }
        }
    }
}
