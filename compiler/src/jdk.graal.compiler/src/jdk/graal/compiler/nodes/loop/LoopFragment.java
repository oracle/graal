/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.loop;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.collections.UnmodifiableEconomicMap;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Graph.DuplicationReplacement;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.GuardProxyNode;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.nodes.java.MonitorEnterNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.spi.NodeWithState;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.nodes.virtual.CommitAllocationNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.vm.ci.meta.TriState;

/**
 * A data structure that represents parts {@code ="fragments"} of a loop. Used for the optimizer to
 * have well defined sets for an entire loop, the body, the header only etc.
 *
 * There are 2 main implementations: {@link LoopFragmentWhole} which defines the entirety of a loop
 * including all loop control nodes. The second one is {@link LoopFragmentInside} which includes all
 * loop nodes of the body and no control variables.
 */
public abstract class LoopFragment {

    /**
     * The original loop this fragment is defined over.
     */
    private final Loop loop;
    /**
     * In case this fragment was duplicated a link to the original one.
     */
    private final LoopFragment original;
    /**
     * All the nodes considered to be part of this fragment.
     */
    protected NodeBitMap nodes;
    /**
     * A flag indicating if the {@link #nodes} map is ready, i.e., if this fragment is a duplicate
     * we have to recompute the nodes.
     */
    protected boolean nodesReady;
    /**
     * A mapping of old to new nodes after duplication.
     */
    private EconomicMap<Node, Node> duplicationMap;
    /**
     * Phi nodes introduced when merging early loop ends for loop optimizations.
     */
    protected List<PhiNode> introducedPhis;
    /**
     * Merge nodes introduced when merging early loop ends for loop optimizations.
     */
    protected List<MergeNode> introducedMerges;

    public LoopFragment(Loop loop) {
        this(loop, null);
        this.nodesReady = true;
    }

    public LoopFragment(Loop loop, LoopFragment original) {
        this.loop = loop;
        this.original = original;
        this.nodesReady = false;
    }

    /**
     * Return the original LoopEx for this fragment. For duplicated fragments this returns null.
     */
    public Loop loop() {
        return loop;
    }

    public abstract LoopFragment duplicate();

    public abstract void insertBefore(Loop l);

    public boolean contains(Node n) {
        return nodes().isMarkedAndGrow(n);
    }

    public UnmodifiableEconomicMap<Node, Node> duplicationMap() {
        return this.duplicationMap;
    }

    public List<PhiNode> getIntroducedPhis() {
        return this.introducedPhis;
    }

    public List<MergeNode> getIntroducedMerges() {
        return this.introducedMerges;
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
        Loop l;
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
        Loop originalLoopEx = original().loop();
        ControlFlowGraph cfg = originalLoopEx.loopsData().getCFG();
        for (LoopExitNode exit : originalLoopEx.loopBegin().loopExits().snapshot()) {
            if (!originalLoopEx.getCFGLoop().isLoopExit(cfg.blockFor(exit))) {
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
                            assert cfgFix.replacement(o) == o : Assertions.errorMessage(cfgFix, o, r1);
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
            nodes.markAll(duplicationMap.getValues());
            nodesReady = true;
        } else {
            // TODO (gd) apply fix ?
        }
    }

    protected static void computeNodes(NodeBitMap nodes, Graph graph, Loop loop, Iterable<AbstractBeginNode> blocks, Iterable<AbstractBeginNode> earlyExits) {
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
        ArrayList<MonitorIdNode> ids = new ArrayList<>();
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
                    ids.add(((MonitorEnterNode) n).getMonitorId());
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
        /*
         * Mark the MonitorIdNodes as part of the loop if all uses are within the loop. This will
         * cause them to be duplicated when the body is cloned. This makes it possible for lock
         * optimizations to identify independent lock regions since it relies on visiting the users
         * of a MonitorIdNode to find the monitor nodes for a lock region.
         */
        boolean mark = true;
        outer: for (MonitorIdNode id : ids) {
            for (Node use : id.usages()) {
                if (!nodes.contains(use)) {
                    mark = false;
                    break outer;
                }
            }
        }
        if (mark) {
            for (MonitorIdNode id : ids) {
                markFloating(worklist, loop, id, nodes, nonLoopNodes);
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

    private static void markFloating(WorkQueue workList, Loop loop, Node start, NodeBitMap loopNodes, NodeBitMap nonLoopNodes) {
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
                // NOTE: Guard Handling: Referenced by loop phases.
                if (!isLoopNode && current instanceof GuardNode guard && !current.hasUsages()) {
                    if (isLoopNode(guard.getCondition(), loopNodes, nonLoopNodes) != TriState.FALSE) {
                        ValueNode anchor = guard.getAnchor().asNode();
                        TriState isAnchorInLoop = isLoopNode(anchor, loopNodes, nonLoopNodes);
                        if (isAnchorInLoop != TriState.FALSE) {
                            if (!(anchor instanceof LoopExitNode && ((LoopExitNode) anchor).loopBegin() == loopBeginNode)) {
                                /*
                                 * Floating guards are treated specially in Graal. They are the only
                                 * floating nodes that survive compilation without usages. If we
                                 * have such floating guards they are normally not considered to be
                                 * part of a loop if they have no usages. If they have usages inside
                                 * the loop they will be naturally pulled inside the loop. If
                                 * however only the condition of the guard is inside the loop we
                                 * could schedule it outside the loop. It is necessary to schedule
                                 * floating guards early however for correctness. Thus, we include
                                 * guards as aggressively inside loops as possible since we "assume"
                                 * they are scheduled later inside a loop because that would
                                 * resemble their EARLY location.
                                 */
                                isLoopNode = true;
                            }
                        } else if (cfg.blockFor(anchor).strictlyDominates(cfg.blockFor(loopBeginNode))) {
                            /*
                             * The anchor dominates the loop, it could be the guard would be
                             * scheduled inside the loop. Be pessimistic an also always include it
                             * in the loop.
                             */
                            isLoopNode = true;
                        }
                    }
                    if (!isLoopNode) {
                        /*
                         * If anchor or condition are inside a loop the guard should be in. We
                         * schedule guards as early as possible for correctness.
                         */
                        if (isLoopNode(guard.getAnchor().asNode(), loopNodes, nonLoopNodes) == TriState.TRUE ||
                                        isLoopNode(guard.getCondition(), loopNodes, nonLoopNodes) == TriState.TRUE) {
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

    public static NodeIterable<AbstractBeginNode> toHirBlocks(final Iterable<HIRBlock> blocks) {
        return new NodeIterable<>() {

            @Override
            public Iterator<AbstractBeginNode> iterator() {
                final Iterator<HIRBlock> it = blocks.iterator();
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
        this.introducedPhis = new ArrayList<>();
        this.introducedMerges = new ArrayList<>();
        for (AbstractBeginNode earlyExit : LoopFragment.toHirBlocks(original().loop().getCFGLoop().getLoopExits())) {
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
            introducedMerges.add(merge);

            FrameState exitState = null;
            if (earlyExit instanceof LoopExitNode) {
                LoopExitNode earlyLoopExit = (LoopExitNode) earlyExit;
                exitState = earlyLoopExit.stateAfter();
                if (exitState != null) {
                    /**
                     * We now have to build a proper state for the new merge node: the state needs
                     * to be the same as the exit state except that all parts of it (including outer
                     * and virtual object mappings etc) that reference proxies of the earlyExit must
                     * be replaced by phis of those proxies. The phi is always merging the old exit
                     * proxy with a new exit proxy.
                     *
                     * Given that the exit state can contain virtual object mappings and outer
                     * states that are shared with portions inside the loop this complicates things
                     * dramatically: some nodes can be referenced from within the loop and some
                     * reference the proxies etc.
                     *
                     *
                     * An example can be seen below
                     *
                     * <pre>
                     * loop: while (tue) {
                     *     lotsOfCode();
                     *     virtualObjectNode v1;
                     *     nodeWithStateUsingVirtualObject(v1);
                     *     if (something) {
                     *         break; // proxy p1
                     *         // stateAfterOfExit: reference p1 via a virtual state mapping and
                     *         // also reference the virtual object v1 via an outer state / virtual
                     *         // object mapping
                     *     }
                     *     wayMoreCodeWithMoreExits();
                     * }
                     * </pre>
                     *
                     * where the exit state references a proxy and thus needs a phi if used as the
                     * merge state. But we also reference a virtual object node that must be
                     * duplicated if the loop is duplicated again (v1) because its used inside the
                     * loop as well.
                     *
                     * While other loop optimizations ensure unique states always before starting a
                     * loop optimization we cannot do this because we would need to recompute the
                     * entire loop fragment after every duplication. This would be too costly. Thus,
                     * we want to re-use as much as possible and only create necessary nodes newly
                     * without re-computing the original fragment. Thus, we use the original exit
                     * state as the merge state.
                     *
                     * However, we have to take caution here: when using the original exit state as
                     * merge state the merge state might still reference virtual object nodes that
                     * are used by other states inside the loop. Thus, we have to compute the
                     * transitive closure from the merge state and only delete (clear in the
                     * original loop) those nodes only reachable by the merge.
                     *
                     * To simplify this we do the following: we duplicate the state for the merge to
                     * have a fresh state for the merge. We mark the new nodes from the new exit
                     * state and then we kill all nodes reachable only from the old state that are
                     * no longer reachable, by using a call back when killing floating nodes.
                     *
                     * So we have 3 states that we are dealing with:
                     *
                     * <ul>
                     * <li>exitState: The one used at the loop exit is a fresh duplicate of the
                     * original exit state and that fresh duplicate is not changed. Proxy inputs are
                     * not replaced as its the finalExitState that is excluded from proxy processing
                     * below. Its new virtual parts are added to the original.nodes map.</li>
                     * <li>merge.stateAfter:The merge one is a fresh copy of the exit state as well
                     * where all proxy inputs are replaced by phis.</li>
                     * <li>originalExitState:The remaining original exit state can either be
                     * retained if it still has usages (below the original exit) or its deleted. If
                     * its deleted we dont want to duplicate any necessary remaining partial unused
                     * state nodes as those are still part of original.nodes. Thus, we remove all of
                     * its unused floating inputs and clear them from original.nodes. The next time
                     * this fragment is then duplicated the exit state (parts) are no longer in the
                     * map and not duplicated.</li>
                     * </ul>
                     */
                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before creating states for %s", earlyLoopExit);
                    FrameState originalExitState = exitState;
                    exitState = exitState.duplicateWithVirtualState();
                    earlyLoopExit.setStateAfter(exitState);
                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Before creating states for %s for %s and merge gets %s", exitState, earlyLoopExit, originalExitState);
                    exitState.applyToVirtual(node -> original.nodes.markAndGrow(node));
                    merge.setStateAfter(originalExitState.duplicateWithVirtualState());
                    if (originalExitState.hasNoUsages()) {
                        GraphUtil.killWithUnusedFloatingInputs(originalExitState, false, unusedInput -> original.nodes.clearAndGrow(unusedInput));
                    }
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
                        assert vpn instanceof GuardProxyNode : Assertions.errorMessage(vpn);
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
                        phi.inferStamp();
                        introducedPhis.add(phi);
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
