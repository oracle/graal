/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.loop;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.core.common.cfg.AbstractControlFlowGraph;
import org.graalvm.compiler.debug.DebugCloseable;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.Graph.DuplicationReplacement;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.GuardProxyNode;
import org.graalvm.compiler.nodes.Invoke;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.VirtualState;
import org.graalvm.compiler.nodes.cfg.Block;
import org.graalvm.compiler.nodes.cfg.ControlFlowGraph;
import org.graalvm.compiler.nodes.java.MonitorEnterNode;
import org.graalvm.compiler.nodes.spi.NodeWithState;
import org.graalvm.compiler.nodes.virtual.CommitAllocationNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;

import jdk.vm.ci.meta.TriState;

public abstract class LoopFragment {

    private final LoopEx loop;
    private final LoopFragment original;
    protected NodeBitMap nodes;
    protected boolean nodesReady;
    private EconomicMap<Node, Node> duplicationMap;

    public LoopFragment(LoopEx loop) {
        this(loop, null);
        this.nodesReady = true;
    }

    public LoopFragment(LoopEx loop, LoopFragment original) {
        this.loop = loop;
        this.original = original;
        this.nodesReady = false;
    }

    /**
     * Return the original LoopEx for this fragment. For duplicated fragments this returns null.
     */
    public LoopEx loop() {
        return loop;
    }

    public abstract LoopFragment duplicate();

    public abstract void insertBefore(LoopEx l);

    public void disconnect() {
        GraalError.unimplemented();
    }

    public boolean contains(Node n) {
        return nodes().isMarkedAndGrow(n);
    }

    @SuppressWarnings("unchecked")
    public <New extends Node, Old extends New> New getDuplicatedNode(Old n) {
        assert isDuplicate();
        return (New) duplicationMap.get(n);
    }

    protected <New extends Node, Old extends New> void putDuplicatedNode(Old oldNode, New newNode) {
        duplicationMap.put(oldNode, newNode);
    }

    public EconomicMap<Node, Node> reverseDuplicationMap() {
        EconomicMap<Node, Node> reverseDuplicationMap = EconomicMap.create();
        MapCursor<Node, Node> cursor = duplicationMap.getEntries();
        while (cursor.advance()) {
            reverseDuplicationMap.put(cursor.getValue(), cursor.getKey());
        }
        return reverseDuplicationMap;
    }

    /**
     * Gets the corresponding value in this fragment. Should be called on duplicate fragments with a
     * node from the original fragment as argument.
     *
     * @param b original value
     * @return corresponding value in the peel
     */
    protected abstract ValueNode prim(ValueNode b);

    public boolean isDuplicate() {
        return original != null;
    }

    public LoopFragment original() {
        return original;
    }

    public abstract NodeBitMap nodes();

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

    protected abstract void beforeDuplication();

    protected void finishDuplication() {
        LoopEx originalLoopEx = original().loop();
        ControlFlowGraph cfg = originalLoopEx.loopsData().getCFG();
        for (LoopExitNode exit : originalLoopEx.loopBegin().loopExits().snapshot()) {
            if (!originalLoopEx.loop().isLoopExit(cfg.blockFor(exit))) {
                // this LoopExitNode is too low, we need to remove it otherwise it will be below
                // merged exits
                exit.removeExit();
            }
        }

    }

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
            beforeDuplication();
            NodeIterable<Node> nodesIterable = original().nodes();
            duplicationMap = graph().addDuplicates(nodesIterable, graph(), nodesIterable.count(), dr);
            finishDuplication();
            nodes = new NodeBitMap(graph());

            try {
                nodes.markAll(duplicationMap.getValues());
            } catch (Throwable t) {
                checkNoNulls(duplicationMap);
                graph().getDebug().forceDump(graph(), "map of type %s has a null key", duplicationMap.getClass());
                throw GraalError.shouldNotReachHere(t);
            }
            nodesReady = true;
        } else {
            // TODO (gd) apply fix ?
        }
    }

    private void checkNoNulls(EconomicMap<Node, Node> dupMap) {
        List<Node> keyListByIterationOrder = new ArrayList<>();
        List<Node> valueListByIterationOrder = new ArrayList<>();
        List<Node> valueListByIterationOrderVALUEAPI = new ArrayList<>();

        MapCursor<Node, Node> c = dupMap.getEntries();
        while (c.advance()) {
            keyListByIterationOrder.add(c.getKey());
            valueListByIterationOrder.add(c.getValue());
            GraalError.guarantee(c.getKey() != null, "key == null in %s", this);
            GraalError.guarantee(c.getValue() != null, "Value == null for %s in %s", c.getKey(), this);
        }

        for (Node value : dupMap.getValues()) {
            valueListByIterationOrderVALUEAPI.add(value);
        }

        final int keyListSize = keyListByIterationOrder.size();
        final int valueListSize = valueListByIterationOrder.size();
        final int valueListSizeVALUEAPI = valueListByIterationOrderVALUEAPI.size();
        GraalError.guarantee(keyListSize == valueListSize, "%d != %d", keyListSize, valueListSize);
        GraalError.guarantee(keyListSize == valueListSizeVALUEAPI, " %d != %d", keyListSize, valueListSizeVALUEAPI);

        for (int i = 0; i < valueListSize; i++) {
            GraalError.guarantee(valueListByIterationOrder.get(i) == valueListByIterationOrderVALUEAPI.get(i), "%s != %s for %d", valueListByIterationOrder.get(i),
                            valueListByIterationOrderVALUEAPI.get(i), i);
        }
    }

    protected static void computeNodes(NodeBitMap nodes, Graph graph, LoopEx loop, Iterable<AbstractBeginNode> blocks, Iterable<AbstractBeginNode> earlyExits) {
        for (AbstractBeginNode b : blocks) {
            if (b.isDeleted()) {
                continue;
            }

            for (Node n : b.getBlockNodes()) {
                if (n instanceof Invoke) {
                    nodes.mark(((Invoke) n).callTarget());
                }
                if (n instanceof NodeWithState) {
                    NodeWithState withState = (NodeWithState) n;
                    withState.states().forEach(state -> state.applyToVirtual(node -> nodes.mark(node)));
                }
                if (n instanceof AbstractMergeNode) {
                    // if a merge is in the loop, all of its phis are also in the loop
                    for (PhiNode phi : ((AbstractMergeNode) n).phis()) {
                        nodes.mark(phi);
                    }
                }
                nodes.mark(n);
            }
        }
        for (AbstractBeginNode earlyExit : earlyExits) {
            if (earlyExit.isDeleted()) {
                continue;
            }

            nodes.mark(earlyExit);

            if (earlyExit instanceof LoopExitNode) {
                LoopExitNode loopExit = (LoopExitNode) earlyExit;
                FrameState stateAfter = loopExit.stateAfter();
                if (stateAfter != null) {
                    stateAfter.applyToVirtual(node -> nodes.mark(node));
                }
                for (ProxyNode proxy : loopExit.proxies()) {
                    nodes.mark(proxy);
                }
            }
        }

        final NodeBitMap nonLoopNodes = graph.createNodeBitMap();
        WorkQueue worklist = new WorkQueue(graph);
        for (AbstractBeginNode b : blocks) {
            if (b.isDeleted()) {
                continue;
            }

            for (Node n : b.getBlockNodes()) {
                if (n instanceof CommitAllocationNode) {
                    for (VirtualObjectNode obj : ((CommitAllocationNode) n).getVirtualObjects()) {
                        markFloating(worklist, loop, obj, nodes, nonLoopNodes);
                    }
                }
                if (n instanceof MonitorEnterNode) {
                    markFloating(worklist, loop, ((MonitorEnterNode) n).getMonitorId(), nodes, nonLoopNodes);
                }
                if (n instanceof AbstractMergeNode) {
                    /*
                     * Since we already marked all phi nodes as being in the loop to break cycles,
                     * we also have to iterate over their usages here.
                     */
                    for (PhiNode phi : ((AbstractMergeNode) n).phis()) {
                        for (Node usage : phi.usages()) {
                            markFloating(worklist, loop, usage, nodes, nonLoopNodes);
                        }
                    }
                }
                for (Node usage : n.usages()) {
                    markFloating(worklist, loop, usage, nodes, nonLoopNodes);
                }
            }
        }
    }

    static class WorkListEntry {
        final Iterator<Node> usages;
        final Node n;
        boolean isLoopNode;

        WorkListEntry(Node n, NodeBitMap loopNodes) {
            this.n = n;
            this.usages = n.usages().iterator();
            this.isLoopNode = loopNodes.isMarked(n);
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof WorkListEntry)) {
                return false;
            }
            WorkListEntry other = (WorkListEntry) obj;
            return this.n == other.n;
        }

        @Override
        public int hashCode() {
            return n.hashCode();
        }
    }

    static class WorkQueue implements Iterable<WorkListEntry> {
        Deque<WorkListEntry> worklist;
        NodeBitMap contents;

        WorkQueue(Graph graph) {
            worklist = new ArrayDeque<>();
            contents = new NodeBitMap(graph);
        }

        public void addFirst(WorkListEntry e) {
            worklist.addFirst(e);
            GraalError.guarantee(!contents.isMarked(e.n), "Trying to addFirst an entry that is already in the work queue!");
            contents.mark(e.n);
        }

        public void push(WorkListEntry e) {
            worklist.push(e);
            GraalError.guarantee(!contents.isMarked(e.n), "Trying to push an entry that is already in the work queue!");
            contents.mark(e.n);
        }

        public WorkListEntry peek() {
            return worklist.peek();
        }

        public WorkListEntry pop() {
            WorkListEntry e = worklist.pop();
            contents.clear(e.n);
            return e;
        }

        public boolean isEmpty() {
            return worklist.isEmpty();
        }

        public boolean contains(WorkListEntry e) {
            return contents.contains(e.n);
        }

        public void clear() {
            worklist.clear();
            contents.clearAll();
        }

        @Override
        public Iterator<WorkListEntry> iterator() {
            return worklist.iterator();
        }
    }

    static TriState isLoopNode(Node n, NodeBitMap loopNodes, NodeBitMap nonLoopNodes) {
        if (loopNodes.isMarked(n)) {
            return TriState.TRUE;
        }
        if (nonLoopNodes.isMarked(n)) {
            return TriState.FALSE;
        }
        if (n instanceof FixedNode || n instanceof PhiNode) {
            // phi nodes are treated the same as fixed nodes in this algorithm to break cycles
            return TriState.FALSE;
        }
        return TriState.UNKNOWN;
    }

    private static void pushWorkList(WorkQueue workList, Node node, NodeBitMap loopNodes) {
        WorkListEntry entry = new WorkListEntry(node, loopNodes);
        GraalError.guarantee(!workList.contains(entry), "node %s added to worklist twice", node);
        workList.push(entry);
    }

    private static void markFloating(WorkQueue workList, LoopEx loop, Node start, NodeBitMap loopNodes, NodeBitMap nonLoopNodes) {
        if (isLoopNode(start, loopNodes, nonLoopNodes).isKnown()) {
            return;
        }

        LoopBeginNode loopBeginNode = loop.loopBegin();
        ControlFlowGraph cfg = loop.loopsData().getCFG();

        pushWorkList(workList, start, loopNodes);
        while (!workList.isEmpty()) {
            WorkListEntry currentEntry = workList.peek();
            if (currentEntry.usages.hasNext()) {
                Node current = currentEntry.usages.next();
                TriState result = isLoopNode(current, loopNodes, nonLoopNodes);
                if (result.isKnown()) {
                    if (result.toBoolean()) {
                        currentEntry.isLoopNode = true;
                    }
                } else {
                    pushWorkList(workList, current, loopNodes);
                }
            } else {
                workList.pop();
                boolean isLoopNode = currentEntry.isLoopNode;
                Node current = currentEntry.n;
                if (!isLoopNode && current instanceof GuardNode && !current.hasUsages()) {
                    GuardNode guard = (GuardNode) current;
                    if (isLoopNode(guard.getCondition(), loopNodes, nonLoopNodes) != TriState.FALSE) {
                        ValueNode anchor = guard.getAnchor().asNode();
                        TriState isAnchorInLoop = isLoopNode(anchor, loopNodes, nonLoopNodes);
                        if (isAnchorInLoop != TriState.FALSE) {
                            if (!(anchor instanceof LoopExitNode && ((LoopExitNode) anchor).loopBegin() == loopBeginNode)) {
                                // It is undecidable whether the node is in the loop or not. This is
                                // not an issue for getting counted loop information,
                                // but causes issues when using the information for actual loop
                                // transformations. This is why a loop transformation must
                                // not happen while guards are floating.
                                isLoopNode = true;
                            }
                        } else if (AbstractControlFlowGraph.strictlyDominates(cfg.blockFor(anchor), cfg.blockFor(loopBeginNode))) {
                            // The anchor is above the loop. The no-usage guard can potentially be
                            // scheduled inside the loop.
                            isLoopNode = true;
                        }
                    }
                }
                if (isLoopNode) {
                    loopNodes.mark(current);
                    for (WorkListEntry e : workList) {
                        e.isLoopNode = true;
                    }
                } else {
                    nonLoopNodes.mark(current);
                }
            }
        }
    }

    public static NodeIterable<AbstractBeginNode> toHirBlocks(final Iterable<Block> blocks) {
        return new NodeIterable<>() {

            @Override
            public Iterator<AbstractBeginNode> iterator() {
                final Iterator<Block> it = blocks.iterator();
                return new Iterator<>() {

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public AbstractBeginNode next() {
                        return it.next().getBeginNode();
                    }

                    @Override
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
    @SuppressWarnings("try")
    protected void mergeEarlyExits() {
        assert isDuplicate();
        StructuredGraph graph = graph();
        for (AbstractBeginNode earlyExit : LoopFragment.toHirBlocks(original().loop().loop().getLoopExits())) {
            FixedNode next = earlyExit.next();
            if (earlyExit.isDeleted() || !this.original().contains(earlyExit)) {
                continue;
            }
            AbstractBeginNode newEarlyExit = getDuplicatedNode(earlyExit);
            if (newEarlyExit == null) {
                continue;
            }

            MergeNode merge;
            EndNode originalEnd;
            EndNode newEnd;
            try (DebugCloseable position = earlyExit.withNodeSourcePosition()) {
                merge = graph.add(new MergeNode());
                originalEnd = graph.add(new EndNode());
                newEnd = graph.add(new EndNode());
            }
            merge.addForwardEnd(originalEnd);
            merge.addForwardEnd(newEnd);
            earlyExit.setNext(originalEnd);
            newEarlyExit.setNext(newEnd);
            merge.setNext(next);

            FrameState exitState = null;
            if (earlyExit instanceof LoopExitNode) {
                LoopExitNode earlyLoopExit = (LoopExitNode) earlyExit;
                exitState = earlyLoopExit.stateAfter();
                if (exitState != null) {
                    FrameState originalExitState = exitState;
                    exitState = exitState.duplicateWithVirtualState();
                    earlyLoopExit.setStateAfter(exitState);
                    merge.setStateAfter(originalExitState);
                    /*
                     * Using the old exit's state as the merge's state is necessary because some of
                     * the VirtualState nodes contained in the old exit's state may be shared by
                     * other dominated VirtualStates. Those dominated virtual states need to see the
                     * proxy->phi update that are applied below.
                     *
                     * We now update the original fragment's nodes accordingly:
                     */
                    originalExitState.applyToVirtual(node -> original.nodes.clearAndGrow(node));
                    exitState.applyToVirtual(node -> original.nodes.markAndGrow(node));
                }
            }

            for (Node anchored : earlyExit.anchored().snapshot()) {
                anchored.replaceFirstInput(earlyExit, merge);
            }

            if (earlyExit instanceof LoopExitNode) {
                LoopExitNode earlyLoopExit = (LoopExitNode) earlyExit;
                FrameState finalExitState = exitState;
                boolean newEarlyExitIsLoopExit = newEarlyExit instanceof LoopExitNode;
                for (ProxyNode vpn : earlyLoopExit.proxies().snapshot()) {
                    if (vpn.hasNoUsages()) {
                        continue;
                    }
                    if (vpn.value() == null) {
                        assert vpn instanceof GuardProxyNode;
                        vpn.replaceAtUsages(null);
                        continue;
                    }
                    final ValueNode replaceWith;
                    ValueNode newVpn = prim(newEarlyExitIsLoopExit ? vpn : vpn.value());
                    if (newVpn != null) {
                        PhiNode phi = vpn.createPhi(merge);
                        phi.setNodeSourcePosition(merge.getNodeSourcePosition());
                        phi.addInput(vpn);
                        phi.addInput(newVpn);
                        replaceWith = phi;
                    } else {
                        replaceWith = vpn.value();
                    }
                    vpn.replaceAtMatchingUsages(replaceWith, usage -> {
                        if (merge.isPhiAtMerge(usage)) {
                            return false;
                        }
                        if (usage instanceof VirtualState) {
                            VirtualState stateUsage = (VirtualState) usage;
                            if (finalExitState != null && finalExitState.isPartOfThisState(stateUsage)) {
                                return false;
                            }
                        }
                        return true;
                    });
                }
            }
        }
    }
}
