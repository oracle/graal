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

import java.util.*;

import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Graph.DuplicationReplacement;
import com.oracle.graal.graph.iterators.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.VirtualState.VirtualClosure;
import com.oracle.graal.nodes.cfg.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.virtual.*;

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
        LoopEx l;
        if (isDuplicate()) {
            l = original().loop();
        } else {
            l = loop();
        }
        return l.loopBegin().graph();
    }

    protected abstract DuplicationReplacement getDuplicationReplacement();

    protected abstract void finishDuplication();

    protected void patchNodes(final DuplicationReplacement dataFix) {
        if (isDuplicate() && !nodesReady) {
            assert !original.isDuplicate();
            final DuplicationReplacement cfgFix = original().getDuplicationReplacement();
            DuplicationReplacement dr;
            if (cfgFix == null && dataFix != null) {
                dr = dataFix;
            } else if (cfgFix != null && dataFix == null) {
                dr = cfgFix;
            } else if (cfgFix != null && dataFix != null) {
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
            } else {
                dr = null;
            }
            NodeIterable<Node> nodesIterable = original().nodes();
            duplicationMap = graph().addDuplicates(nodesIterable, graph(), nodesIterable.count(), dr);
            finishDuplication();
            nodesReady = true;
        } else {
            // TODO (gd) apply fix ?
        }
    }

    protected static NodeBitMap computeNodes(Graph graph, Iterable<AbstractBeginNode> blocks) {
        return computeNodes(graph, blocks, Collections.<AbstractBeginNode> emptyList());
    }

    protected static NodeBitMap computeNodes(Graph graph, Iterable<AbstractBeginNode> blocks, Iterable<AbstractBeginNode> earlyExits) {
        final NodeBitMap nodes = graph.createNodeBitMap(true);
        for (AbstractBeginNode b : blocks) {
            if (b.isDeleted()) {
                continue;
            }

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
        for (AbstractBeginNode earlyExit : earlyExits) {
            if (earlyExit.isDeleted()) {
                continue;
            }

            FrameState stateAfter = earlyExit.stateAfter();
            if (stateAfter != null) {
                nodes.mark(stateAfter);
                stateAfter.applyToVirtual(new VirtualClosure() {

                    @Override
                    public void apply(VirtualState node) {
                        nodes.mark(node);
                    }
                });
            }
            nodes.mark(earlyExit);
            for (ProxyNode proxy : earlyExit.proxies()) {
                nodes.mark(proxy);
            }
        }

        final NodeBitMap notloopNodes = graph.createNodeBitMap(true);
        for (AbstractBeginNode b : blocks) {
            if (b.isDeleted()) {
                continue;
            }

            for (Node n : b.getBlockNodes()) {
                if (n instanceof CommitAllocationNode) {
                    for (VirtualObjectNode obj : ((CommitAllocationNode) n).getVirtualObjects()) {
                        markFloating(obj, nodes, notloopNodes);
                    }
                }
                if (n instanceof MonitorEnterNode) {
                    markFloating(((MonitorEnterNode) n).getMonitorId(), nodes, notloopNodes);
                }
                for (Node usage : n.usages()) {
                    markFloating(usage, nodes, notloopNodes);
                }
            }
        }

        return nodes;
    }

    private static boolean markFloating(Node n, NodeBitMap loopNodes, NodeBitMap notloopNodes) {
        if (loopNodes.isMarked(n)) {
            return true;
        }
        if (notloopNodes.isMarked(n)) {
            return false;
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
                notloopNodes.mark(n);
                return false;
            }
        }
        for (Node usage : n.usages()) {
            if (markFloating(usage, loopNodes, notloopNodes)) {
                mark = true;
            }
        }
        if (mark) {
            loopNodes.mark(n);
            return true;
        }
        notloopNodes.mark(n);
        return false;
    }

    public static NodeIterable<AbstractBeginNode> toHirBlocks(final Iterable<Block> blocks) {
        return new AbstractNodeIterable<AbstractBeginNode>() {

            public Iterator<AbstractBeginNode> iterator() {
                final Iterator<Block> it = blocks.iterator();
                return new Iterator<AbstractBeginNode>() {

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    public AbstractBeginNode next() {
                        return it.next().getBeginNode();
                    }

                    public boolean hasNext() {
                        return it.hasNext();
                    }
                };
            }

        };
    }

    /**
     * Merges the early exits (i.e. loop exits) that were duplicated as part of this fragment, with
     * the original fragment's exits.
     */
    protected void mergeEarlyExits() {
        assert isDuplicate();
        StructuredGraph graph = graph();
        for (AbstractBeginNode earlyExit : LoopFragment.toHirBlocks(original().loop().lirLoop().exits)) {
            FixedNode next = earlyExit.next();
            if (earlyExit.isDeleted() || !this.original().contains(earlyExit)) {
                continue;
            }
            AbstractBeginNode newEarlyExit = getDuplicatedNode(earlyExit);
            if (newEarlyExit == null) {
                continue;
            }
            MergeNode merge = graph.add(new MergeNode());
            AbstractEndNode originalEnd = graph.add(new EndNode());
            AbstractEndNode newEnd = graph.add(new EndNode());
            merge.addForwardEnd(originalEnd);
            merge.addForwardEnd(newEnd);
            earlyExit.setNext(originalEnd);
            newEarlyExit.setNext(newEnd);
            merge.setNext(next);

            FrameState exitState = earlyExit.stateAfter();
            FrameState state = null;
            if (exitState != null) {
                state = exitState;
                exitState = exitState.duplicateWithVirtualState();
                earlyExit.setStateAfter(exitState);
                merge.setStateAfter(state);
                /*
                 * Using the old exit's state as the merge's state is necessary because some of the
                 * VirtualState nodes contained in the old exit's state may be shared by other
                 * dominated VirtualStates. Those dominated virtual states need to see the
                 * proxy->phi update that are applied below.
                 * 
                 * We now update the original fragment's nodes accordingly:
                 */
                state.applyToVirtual(new VirtualClosure() {
                    public void apply(VirtualState node) {
                        original.nodes.clear(node);
                    }
                });
                exitState.applyToVirtual(new VirtualClosure() {
                    public void apply(VirtualState node) {
                        original.nodes.mark(node);
                    }
                });
            }

            for (Node anchored : earlyExit.anchored().snapshot()) {
                anchored.replaceFirstInput(earlyExit, merge);
            }

            for (final ProxyNode vpn : earlyExit.proxies().snapshot()) {
                final ValueNode replaceWith;
                ProxyNode newVpn = getDuplicatedNode(vpn);
                if (newVpn != null) {
                    PhiNode phi;
                    switch (vpn.type()) {
                        case Value:
                            phi = graph.addWithoutUnique(new PhiNode(vpn.stamp(), merge));
                            break;
                        case Guard:
                            phi = graph.addWithoutUnique(new PhiNode(vpn.type(), merge));
                            break;
                        case Memory:
                            phi = graph.addWithoutUnique(new MemoryPhiNode(merge, ((MemoryProxyNode) vpn).getLocationIdentity()));
                            break;
                        default:
                            throw GraalInternalError.shouldNotReachHere();
                    }
                    phi.addInput(vpn);
                    phi.addInput(newVpn);
                    replaceWith = phi;
                } else {
                    replaceWith = vpn.value();
                }
                for (Node usage : vpn.usages().snapshot()) {
                    if (!merge.isPhiAtMerge(usage)) {
                        if (usage instanceof VirtualState) {
                            VirtualState stateUsage = (VirtualState) usage;
                            if (exitState.isPartOfThisState(stateUsage)) {
                                continue;
                            }
                        }
                        usage.replaceFirstInput(vpn, replaceWith);
                    }
                }
            }
        }
    }
}
