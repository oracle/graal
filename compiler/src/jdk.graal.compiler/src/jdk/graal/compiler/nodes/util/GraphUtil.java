/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.bytecode.Bytecode;
import jdk.graal.compiler.code.SourceStackTraceBailoutException;
import jdk.graal.compiler.core.common.type.ObjectStamp;
import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.LinkedStack;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.graph.NodeStack;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.graph.iterators.NodeIterable;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractEndNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.BeginNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.LoopEndNode;
import jdk.graal.compiler.nodes.LoopExitNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.ValueProxyNode;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.debug.ControlFlowAnchored;
import jdk.graal.compiler.nodes.extended.MultiGuardNode;
import jdk.graal.compiler.nodes.extended.SwitchCaseProbabilityNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.java.MonitorIdNode;
import jdk.graal.compiler.nodes.memory.MemoryAnchorNode;
import jdk.graal.compiler.nodes.memory.MemoryPhiNode;
import jdk.graal.compiler.nodes.spi.ArrayLengthProvider;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.CoreProvidersDelegate;
import jdk.graal.compiler.nodes.spi.LimitedValueProxy;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.spi.ValueProxy;
import jdk.graal.compiler.nodes.spi.VirtualizerTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.nodes.virtual.VirtualArrayNode;
import jdk.graal.compiler.nodes.virtual.VirtualObjectNode;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class GraphUtil {

    public static class Options {
        @Option(help = "Verify that there are no new unused nodes when performing killCFG", type = OptionType.Debug)//
        public static final OptionKey<Boolean> VerifyKillCFGUnusedNodes = new OptionKey<>(false);
    }

    public static final int MAX_FRAMESTATE_SEARCH_DEPTH = 4;

    private static void killCFGInner(FixedNode node) {
        EconomicSet<Node> markedNodes = EconomicSet.create();
        EconomicMap<AbstractMergeNode, List<AbstractEndNode>> unmarkedMerges = EconomicMap.create();

        // Detach this node from CFG
        node.replaceAtPredecessor(null);

        markFixedNodes(node, markedNodes, unmarkedMerges);

        fixSurvivingAffectedMerges(markedNodes, unmarkedMerges);

        DebugContext debug = node.getDebug();
        debug.dump(DebugContext.DETAILED_LEVEL, node.graph(), "After fixing merges (killCFG %s)", node);

        // Mark non-fixed nodes
        markUsagesForKill(markedNodes);

        // Detach marked nodes from non-marked nodes
        for (Node marked : markedNodes) {
            for (Node input : marked.inputs()) {
                if (!markedNodes.contains(input)) {
                    marked.replaceFirstInput(input, null);
                    tryKillUnused(input);
                }
            }
        }
        debug.dump(DebugContext.VERY_DETAILED_LEVEL, node.graph(), "After disconnecting non-marked inputs (killCFG %s)", node);
        // Kill marked nodes
        for (Node marked : markedNodes) {
            if (marked.isAlive()) {
                marked.markDeleted();
            }
        }
        for (Node m : markedNodes) {
            if (m instanceof FixedWithNextNode fixed) {
                GraalError.guarantee(fixed.next() == null || fixed.next().isDeleted(), "dead node %s has live next %s", m, fixed.next());
            }
        }
    }

    private static void markFixedNodes(FixedNode node, EconomicSet<Node> markedNodes, EconomicMap<AbstractMergeNode, List<AbstractEndNode>> unmarkedMerges) {
        NodeStack workStack = new NodeStack();
        workStack.push(node);
        while (!workStack.isEmpty()) {
            Node fixedNode = workStack.pop();
            markedNodes.add(fixedNode);
            if (fixedNode instanceof AbstractMergeNode) {
                unmarkedMerges.removeKey((AbstractMergeNode) fixedNode);
            }
            while (fixedNode instanceof FixedWithNextNode) {
                fixedNode = ((FixedWithNextNode) fixedNode).next();
                if (fixedNode != null) {
                    markedNodes.add(fixedNode);
                }
            }
            if (fixedNode instanceof ControlSplitNode) {
                for (Node successor : fixedNode.successors()) {
                    workStack.push(successor);
                }
            } else if (fixedNode instanceof AbstractEndNode) {
                AbstractEndNode end = (AbstractEndNode) fixedNode;
                AbstractMergeNode merge = end.merge();
                if (merge != null) {
                    assert !markedNodes.contains(merge) || (merge instanceof LoopBeginNode && end instanceof LoopEndNode) : merge;
                    if (merge instanceof LoopBeginNode) {
                        if (end == ((LoopBeginNode) merge).forwardEnd()) {
                            workStack.push(merge);
                            continue;
                        }
                        if (markedNodes.contains(merge)) {
                            continue;
                        }
                    }
                    List<AbstractEndNode> endsSeen = unmarkedMerges.get(merge);
                    if (endsSeen == null) {
                        endsSeen = new ArrayList<>(merge.forwardEndCount());
                        unmarkedMerges.put(merge, endsSeen);
                    }
                    endsSeen.add(end);
                    if (!(end instanceof LoopEndNode) && endsSeen.size() == merge.forwardEndCount()) {
                        assert merge.forwardEnds().filter(n -> !markedNodes.contains(n)).isEmpty();
                        // all this merge's forward ends are marked: it needs to be killed
                        workStack.push(merge);
                    }
                }
            }
        }
    }

    private static void fixSurvivingAffectedMerges(EconomicSet<Node> markedNodes, EconomicMap<AbstractMergeNode, List<AbstractEndNode>> unmarkedMerges) {
        MapCursor<AbstractMergeNode, List<AbstractEndNode>> cursor = unmarkedMerges.getEntries();
        while (cursor.advance()) {
            AbstractMergeNode merge = cursor.getKey();
            for (AbstractEndNode end : cursor.getValue()) {
                merge.removeEnd(end);
            }
            if (merge.phiPredecessorCount() == 1) {
                if (merge instanceof LoopBeginNode) {
                    LoopBeginNode loopBegin = (LoopBeginNode) merge;
                    assert merge.forwardEndCount() == 1 : Assertions.errorMessageContext("merge", merge);
                    for (LoopExitNode loopExit : loopBegin.loopExits().snapshot()) {
                        if (markedNodes.contains(loopExit)) {
                            /*
                             * disconnect from loop begin so that reduceDegenerateLoopBegin doesn't
                             * transform it into a new beginNode
                             */
                            loopExit.replaceFirstInput(loopBegin, null);
                        }
                    }
                    merge.graph().reduceDegenerateLoopBegin(loopBegin, true);
                } else {
                    merge.graph().reduceTrivialMerge(merge, true);
                }
            } else {
                assert merge.phiPredecessorCount() > 1 : merge;
            }
        }
    }

    private static void markUsagesForKill(EconomicSet<Node> markedNodes) {
        NodeStack workStack = new NodeStack(markedNodes.size() + 4);
        for (Node marked : markedNodes) {
            workStack.push(marked);
        }
        ArrayList<MultiGuardNode> unmarkedMultiGuards = new ArrayList<>();
        while (!workStack.isEmpty()) {
            Node marked = workStack.pop();
            for (Node usage : marked.usages()) {
                boolean doMark = true;
                if (usage instanceof MultiGuardNode) {
                    // Only mark a MultiGuardNode for deletion if all of its guards are marked for
                    // deletion. Otherwise, we would kill nodes outside the path to be killed.
                    MultiGuardNode multiGuard = (MultiGuardNode) usage;
                    for (Node guard : multiGuard.inputs()) {
                        if (!markedNodes.contains(guard)) {
                            doMark = false;
                            unmarkedMultiGuards.add(multiGuard);
                        }
                    }
                }
                if (doMark && !markedNodes.contains(usage)) {
                    workStack.push(usage);
                    markedNodes.add(usage);
                }
            }
            // Detach unmarked multi guards from the marked node
            for (MultiGuardNode multiGuard : unmarkedMultiGuards) {
                multiGuard.replaceFirstInput(marked, null);
            }
            unmarkedMultiGuards.clear();
        }
    }

    @SuppressWarnings("try")
    public static void killCFG(FixedNode node) {
        DebugContext debug = node.getDebug();
        try (DebugContext.Scope scope = debug.scope("KillCFG", node)) {
            EconomicSet<Node> unusedNodes = null;
            EconomicSet<Node> unsafeNodes = null;
            Graph.NodeEventScope nodeEventScope = null;
            boolean verifyGraalGraphEdges = node.graph().verifyGraphEdges;
            boolean verifyKillCFGUnusedNodes = node.graph().verifyKillCFGUnusedNodes;
            if (verifyGraalGraphEdges) {
                unsafeNodes = collectUnsafeNodes(node.graph());
            }
            if (verifyKillCFGUnusedNodes) {
                EconomicSet<Node> deadControlFLow = EconomicSet.create(Equivalence.IDENTITY);
                for (Node n : node.graph().getNodes()) {
                    if (n instanceof FixedNode && !(n instanceof AbstractMergeNode) && n.predecessor() == null) {
                        deadControlFLow.add(n);
                    }
                }
                EconomicSet<Node> collectedUnusedNodes = unusedNodes = EconomicSet.create(Equivalence.IDENTITY);
                nodeEventScope = node.graph().trackNodeEvents(new Graph.NodeEventListener() {
                    @Override
                    public void changed(Graph.NodeEvent e, Node n) {
                        if (e == Graph.NodeEvent.ZERO_USAGES && isFloatingNode(n) && !(n instanceof GuardNode)) {
                            collectedUnusedNodes.add(n);
                        }
                        if ((e == Graph.NodeEvent.INPUT_CHANGED || e == Graph.NodeEvent.CONTROL_FLOW_CHANGED) && n instanceof FixedNode && !(n instanceof AbstractMergeNode) &&
                                        n.predecessor() == null) {
                            if (!deadControlFLow.contains(n)) {
                                collectedUnusedNodes.add(n);
                            }
                        }
                        if (e == Graph.NodeEvent.NODE_REMOVED) {
                            collectedUnusedNodes.remove(n);
                        }
                    }
                });
            }
            debug.dump(DebugContext.VERY_DETAILED_LEVEL, node.graph(), "Before killCFG %s", node);
            killCFGInner(node);
            debug.dump(DebugContext.VERY_DETAILED_LEVEL, node.graph(), "After killCFG %s", node);
            if (verifyGraalGraphEdges) {
                EconomicSet<Node> newUnsafeNodes = collectUnsafeNodes(node.graph());
                newUnsafeNodes.removeAll(unsafeNodes);
                assert newUnsafeNodes.isEmpty() : "New unsafe nodes: " + newUnsafeNodes;
            }
            if (verifyKillCFGUnusedNodes) {
                nodeEventScope.close();
                Iterator<Node> iterator = unusedNodes.iterator();
                while (iterator.hasNext()) {
                    Node curNode = iterator.next();
                    if (curNode.isDeleted()) {
                        GraalError.shouldNotReachHereUnexpectedValue(curNode); // ExcludeFromJacocoGeneratedReport
                    } else {
                        if (curNode instanceof FixedNode && !(curNode instanceof AbstractMergeNode) && curNode.predecessor() != null) {
                            iterator.remove();
                        }
                        if ((curNode instanceof PhiNode)) {
                            // We seem to skip PhiNodes at the moment but that's mostly ok.
                            iterator.remove();
                        }
                    }
                }
                GraalError.guarantee(unusedNodes.isEmpty(), "New unused nodes: %s", unusedNodes);
            }
        } catch (Throwable t) {
            throw debug.handle(t);
        }
    }

    /**
     * Collects all node in the graph which have non-optional inputs that are null.
     */
    private static EconomicSet<Node> collectUnsafeNodes(Graph graph) {
        EconomicSet<Node> unsafeNodes = EconomicSet.create(Equivalence.IDENTITY);
        for (Node n : graph.getNodes()) {
            for (Position pos : n.inputPositions()) {
                Node input = pos.get(n);
                if (input == null) {
                    if (!pos.isInputOptional()) {
                        unsafeNodes.add(n);
                    }
                }
            }
        }
        return unsafeNodes;
    }

    public static boolean isFloatingNode(Node n) {
        return !(n instanceof FixedNode);
    }

    private static boolean checkKill(Node node, boolean mayKillGuard) {
        node.assertTrue(mayKillGuard || !(node instanceof GuardNode), "must not be a guard node %s", node);
        node.assertTrue(node.isAlive(), "must be alive");
        node.assertTrue(node.hasNoUsages(), "cannot kill node %s because of usages: %s", node, node.usages());
        node.assertTrue(node.predecessor() == null, "cannot kill node %s because of predecessor: %s", node, node.predecessor());
        return true;
    }

    public static void killWithUnusedFloatingInputs(Node node) {
        killWithUnusedFloatingInputs(node, false);
    }

    public static void killWithUnusedFloatingInputs(Node node, boolean mayKillGuard) {
        killWithUnusedFloatingInputs(node, mayKillGuard, null);
    }

    public static void killWithUnusedFloatingInputs(Node node, boolean mayKillGuard, Consumer<Node> beforeDelete) {
        LinkedStack<Node> stack = null;
        Node cur = node;
        do {
            CompilationAlarm.checkProgress(node.graph());
            assert checkKill(cur, mayKillGuard);
            if (beforeDelete != null) {
                beforeDelete.accept(cur);
            }
            cur.markDeleted();
            outer: for (Node in : cur.inputs()) {
                if (in.isAlive()) {
                    in.removeUsage(cur);
                    if (in.hasNoUsages()) {
                        cur.maybeNotifyZeroUsages(in);
                    }
                    if (isFloatingNode(in)) {
                        if (in.hasNoUsages()) {
                            if (in instanceof GuardNode) {
                                // Guard nodes are only killed if their anchor dies.
                                continue outer;
                            }
                        } else if (in instanceof PhiNode) {
                            if (!((PhiNode) in).isDegenerated()) {
                                continue outer;
                            }
                            in.replaceAtUsages(null);
                        } else {
                            continue outer;
                        }
                        if (stack == null) {
                            stack = new LinkedStack<>();
                        }
                        stack.push(in);
                    }
                }
            }
            if (stack == null || stack.isEmpty()) {
                break;
            } else {
                cur = stack.pop();
            }
        } while (true); // TERMINATION ARGUMENT: processing floating nodes without inputs until
                        // input is found
    }

    /**
     * Deletes the specified nodes in the graph and all nodes transitively, which are left without
     * usages (except {@link GuardNode}s). This implementation is more efficient than repeated
     * invocations of {@link GraphUtil#killWithUnusedFloatingInputs(Node, boolean)}.
     */
    public static void killAllWithUnusedFloatingInputs(NodeIterable<? extends Node> toKill, boolean mayKillGuard) {
        /*
         * Removing single nodes from a node's usage list is expensive (especially for complex
         * graphs). This implementation works in the following:
         *
         * 1) If node x uses node y and y is deleted, the deletion of y from x's usages is deferred
         * until it is known that x is not deleted as well. Thus, if both x any y are deleted, the
         * usage is not deleted.
         *
         * 2) Nodes which were not deleted during the transitive deletion have their dead usages
         * removed all at once, which yields linear complexity for removing multiple usages for one
         * node.
         */

        // tracks the usage counts for each node instead of actually deleting usages for now
        EconomicMap<Node, Integer> usageMap = EconomicMap.create();

        EconomicSet<Node> maybeKill = EconomicSet.create();

        // delete the initial set of nodes to be killed
        for (Node n : toKill) {
            assert checkKill(n, mayKillGuard);
            n.markDeleted();
            for (Node in : n.inputs()) {
                Integer usages = usageMap.get(in);
                if (usages == null) {
                    usages = in.getUsageCount();
                }
                usageMap.put(in, usages - 1);
                maybeKill.add(in);
            }
        }

        // fixed point algorithm for transitive deletion of nodes which have no more usages
        EconomicSet<Node> newMaybeKill;
        do {
            newMaybeKill = EconomicSet.create();

            for (Node n : maybeKill) {
                if (n.isAlive()) {
                    if (usageMap.get(n) == 0) {
                        n.maybeNotifyZeroUsages(n);
                        if (!isFloatingNode(n)) {
                            continue;
                        }
                        if (n instanceof GuardNode) {
                            // Guard nodes are only killed if their anchor dies.
                            continue;
                        }
                    } else if (n instanceof PhiNode phi && phi.isDegenerated()) {
                        n.replaceAtUsages(null);
                        n.maybeNotifyZeroUsages(n);
                    } else {
                        continue;
                    }
                    // no "checkKill" because usages were not actually removed
                    n.markDeleted();
                    for (Node in : n.inputs()) {
                        Integer usages = usageMap.get(in);
                        if (usages == null) {
                            usages = in.usages().count();
                        }
                        usageMap.put(in, usages - 1);
                        newMaybeKill.add(in);
                    }
                }
            }
            maybeKill = newMaybeKill;
        } while (!newMaybeKill.isEmpty());

        // actual removal of deleted usages for each node which is still alive
        for (Node n : usageMap.getKeys()) {
            if (n.isAlive() && (n.getUsageCount() != usageMap.get(n))) {
                n.removeDeadUsages();
            }
        }
    }

    public static void removeFixedWithUnusedInputs(FixedWithNextNode fixed) {
        if (fixed instanceof StateSplit) {
            FrameState stateAfter = ((StateSplit) fixed).stateAfter();
            if (stateAfter != null) {
                ((StateSplit) fixed).setStateAfter(null);
                if (stateAfter.hasNoUsages()) {
                    killWithUnusedFloatingInputs(stateAfter);
                }
            }
        }
        unlinkFixedNode(fixed);
        killWithUnusedFloatingInputs(fixed);
    }

    public static void unlinkFixedNode(FixedWithNextNode fixed) {
        assert fixed.next() != null && fixed.predecessor() != null && fixed.isAlive() : fixed;
        FixedNode next = fixed.next();
        fixed.setNext(null);
        fixed.replaceAtPredecessor(next);
    }

    public static void unlinkAndKillExceptionEdge(WithExceptionNode withException) {
        assert withException.next() != null && withException.predecessor() != null && withException.isAlive() : withException;
        FixedNode next = withException.next();
        withException.setNext(null);
        withException.replaceAtPredecessor(next);
        withException.killExceptionEdge();
    }

    public static void checkRedundantPhi(PhiNode phiNode) {
        if (phiNode.isDeleted() || phiNode.valueCount() == 1) {
            return;
        }

        ValueNode singleValue = phiNode.singleValueOrThis();
        if (singleValue != phiNode) {
            Collection<PhiNode> phiUsages = phiNode.usages().filter(PhiNode.class).snapshot();
            Collection<ProxyNode> proxyUsages = phiNode.usages().filter(ProxyNode.class).snapshot();
            phiNode.replaceAtUsagesAndDelete(singleValue);
            for (PhiNode phi : phiUsages) {
                checkRedundantPhi(phi);
            }
            for (ProxyNode proxy : proxyUsages) {
                checkRedundantProxy(proxy);
            }
        }
    }

    public static void checkRedundantProxy(ProxyNode vpn) {
        if (vpn.isDeleted()) {
            return;
        }
        AbstractBeginNode proxyPoint = vpn.proxyPoint();
        if (proxyPoint instanceof LoopExitNode) {
            LoopExitNode exit = (LoopExitNode) proxyPoint;
            LoopBeginNode loopBegin = exit.loopBegin();
            Node vpnValue = vpn.value();
            for (ValueNode v : loopBegin.stateAfter().values()) {
                ValueNode v2 = v;
                if (loopBegin.isPhiAtMerge(v2)) {
                    v2 = ((PhiNode) v2).valueAt(loopBegin.forwardEnd());
                }
                if (vpnValue == v2) {
                    Collection<PhiNode> phiUsages = vpn.usages().filter(PhiNode.class).snapshot();
                    Collection<ProxyNode> proxyUsages = vpn.usages().filter(ProxyNode.class).snapshot();
                    vpn.replaceAtUsagesAndDelete(vpnValue);
                    for (PhiNode phi : phiUsages) {
                        checkRedundantPhi(phi);
                    }
                    for (ProxyNode proxy : proxyUsages) {
                        checkRedundantProxy(proxy);
                    }
                    return;
                }
            }
        }
    }

    /**
     * Remove loop header without loop ends. This can happen with degenerated loops like this one:
     *
     * <pre>
     * for (;;) {
     *     try {
     *         break;
     *     } catch (UnresolvedException iioe) {
     *     }
     * }
     * </pre>
     */
    public static void normalizeLoops(StructuredGraph graph) {
        boolean loopRemoved = false;
        for (LoopBeginNode begin : graph.getNodes(LoopBeginNode.TYPE)) {
            if (begin.loopEnds().isEmpty()) {
                assert begin.forwardEndCount() == 1 : Assertions.errorMessage(begin);
                graph.reduceDegenerateLoopBegin(begin);
                loopRemoved = true;
            } else {
                normalizeLoopBegin(begin);
            }
        }

        if (loopRemoved) {
            /*
             * Removing a degenerated loop can make non-loop phi functions unnecessary. Therefore,
             * we re-check all phi functions and remove redundant ones.
             */
            for (Node node : graph.getNodes()) {
                if (node instanceof PhiNode) {
                    checkRedundantPhi((PhiNode) node);
                }
            }
        }
    }

    private static void normalizeLoopBegin(LoopBeginNode begin) {
        // Delete unnecessary loop phi functions, i.e., phi functions where all inputs are either
        // the same or the phi itself.
        for (PhiNode phi : begin.phis().snapshot()) {
            GraphUtil.checkRedundantPhi(phi);
        }
        for (LoopExitNode exit : begin.loopExits().snapshot()) {
            for (ProxyNode vpn : exit.proxies().snapshot()) {
                GraphUtil.checkRedundantProxy(vpn);
            }
        }
    }

    /**
     * Gets an approximate source code location for a node if possible.
     *
     * @return the StackTraceElements if an approximate source location is found, null otherwise
     */
    public static StackTraceElement[] approxSourceStackTraceElement(Node node) {
        NodeSourcePosition position = node.getNodeSourcePosition();
        if (position != null) {
            // use GraphBuilderConfiguration and enable trackNodeSourcePosition to get better source
            // positions.
            return approxSourceStackTraceElement(position);
        }
        ArrayList<StackTraceElement> elements = new ArrayList<>();
        Node n = node;
        while (n != null) {
            if (n instanceof MethodCallTargetNode) {
                elements.add(((MethodCallTargetNode) n).targetMethod().asStackTraceElement(-1));
                n = ((MethodCallTargetNode) n).invoke().asNode();
            }

            if (n instanceof StateSplit) {
                FrameState state = ((StateSplit) n).stateAfter();
                elements.addAll(Arrays.asList(approxSourceStackTraceElement(state)));
                break;
            }
            n = n.predecessor();
        }
        return elements.toArray(new StackTraceElement[elements.size()]);
    }

    /**
     * Gets an approximate source code location for frame state.
     *
     * @return the StackTraceElements if an approximate source location is found, null otherwise
     */
    public static StackTraceElement[] approxSourceStackTraceElement(FrameState frameState) {
        ArrayList<StackTraceElement> elements = new ArrayList<>();
        FrameState state = frameState;
        while (state != null) {
            Bytecode code = state.getCode();
            if (code != null) {
                elements.add(code.asStackTraceElement(state.bci - 1));
            }
            state = state.outerFrameState();
        }
        return elements.toArray(new StackTraceElement[0]);
    }

    /**
     * Gets approximate stack trace elements for a bytecode position.
     */
    public static StackTraceElement[] approxSourceStackTraceElement(BytecodePosition bytecodePosition) {
        ArrayList<StackTraceElement> elements = new ArrayList<>();
        BytecodePosition position = bytecodePosition;
        while (position != null) {
            ResolvedJavaMethod method = position.getMethod();
            if (method != null) {
                elements.add(method.asStackTraceElement(position.getBCI()));
            }
            position = position.getCaller();
        }
        return elements.toArray(new StackTraceElement[0]);
    }

    /**
     * Gets an approximate source code location for a node, encoded as an exception, if possible.
     *
     * @return the exception with the location
     */
    public static RuntimeException approxSourceException(Node node, Throwable cause) {
        final StackTraceElement[] elements = approxSourceStackTraceElement(node);
        return createBailoutException(cause == null ? "" : cause.getMessage(), cause, elements);
    }

    /**
     * Creates a bailout exception with the given stack trace elements and message.
     *
     * @param message the message of the exception
     * @param elements the stack trace elements
     * @return the exception
     */
    public static BailoutException createBailoutException(String message, Throwable cause, StackTraceElement[] elements) {
        return SourceStackTraceBailoutException.create(cause, message, elements);
    }

    /**
     * Gets an approximate source code location for a node if possible.
     *
     * @return a file name and source line number in stack trace format (e.g. "String.java:32") if
     *         an approximate source location is found, null otherwise
     */
    public static String approxSourceLocation(Node node) {
        StackTraceElement[] stackTraceElements = approxSourceStackTraceElement(node);
        if (stackTraceElements != null && stackTraceElements.length > 0) {
            StackTraceElement top = stackTraceElements[0];
            if (top.getFileName() != null && top.getLineNumber() >= 0) {
                return top.getFileName() + ":" + top.getLineNumber();
            }
        }
        return null;
    }

    /**
     * Returns a string representation of the given collection of objects.
     *
     * @param objects The {@link Iterable} that will be used to iterate over the objects.
     * @return A string of the format "[a, b, ...]".
     */
    public static String toString(Iterable<?> objects) {
        StringBuilder str = new StringBuilder();
        str.append("[");
        for (Object o : objects) {
            str.append(o).append(", ");
        }
        if (str.length() > 1) {
            str.setLength(str.length() - 2);
        }
        str.append("]");
        return str.toString();
    }

    /**
     * Gets the original value by iterating through all {@link ValueProxy ValueProxies}.
     *
     * @param value the start value.
     * @return the first non-proxy value encountered
     */
    public static ValueNode unproxify(ValueNode value) {
        if (value instanceof ValueProxy) {
            return unproxify((ValueProxy) value);
        } else {
            return value;
        }
    }

    /**
     * Gets the original value by iterating through all {@link ValueProxy ValueProxies}.
     *
     * @param value the start value proxy.
     * @return the first non-proxy value encountered
     */
    public static ValueNode unproxify(ValueProxy value) {
        if (value != null) {
            ValueNode result = value.getOriginalNode();
            while (result instanceof ValueProxy) {
                result = ((ValueProxy) result).getOriginalNode();
            }
            return result;
        } else {
            return null;
        }
    }

    /**
     * Gets the original value by iterating through all {@link ValueProxy value proxies}, except
     * {@link ProxyNode ProxyNodes}. That is, this method looks through pi nodes, value anchors,
     * etc., but it does not enter loops.
     *
     * @param original the start value
     * @return the first non-proxy or loop proxy value encountered
     */
    public static ValueNode unproxifyExceptLoopProxies(ValueNode original) {
        ValueNode node = original;
        while (node instanceof ValueProxy proxy && !(node instanceof ProxyNode)) {
            node = proxy.getOriginalNode();
        }
        return node;
    }

    public static ValueNode skipPi(ValueNode node) {
        ValueNode n = node;
        while (n instanceof PiNode) {
            PiNode piNode = (PiNode) n;
            n = piNode.getOriginalNode();
        }
        return n;
    }

    public static ValueNode skipPiWhileNonNullArray(ValueNode node) {
        ValueNode n = node;
        while (n instanceof PiNode) {
            PiNode piNode = (PiNode) n;
            ObjectStamp originalStamp = (ObjectStamp) piNode.getOriginalNode().stamp(NodeView.DEFAULT);
            if (originalStamp.nonNull() && originalStamp.isAlwaysArray()) {
                n = piNode.getOriginalNode();
            } else {
                break;
            }
        }
        return n;
    }

    /**
     * Returns the length of the array described by the value parameter, or null if it is not
     * available. Details of the different modes are documented in
     * {@link jdk.graal.compiler.nodes.spi.ArrayLengthProvider.FindLengthMode}.
     *
     * @param value The start value.
     * @param mode The mode as documented in
     *            {@link jdk.graal.compiler.nodes.spi.ArrayLengthProvider.FindLengthMode}.
     * @return The array length if one was found, or null otherwise.
     */
    public static ValueNode arrayLength(ValueNode value, ArrayLengthProvider.FindLengthMode mode, ConstantReflectionProvider constantReflection) {
        return arrayLength(value, mode, constantReflection, null);
    }

    /**
     * Filters out non-constant results when requested.
     */
    private static ValueNode filterArrayLengthResult(ValueNode result, boolean allowOnlyConstantResult) {
        return result == null || !allowOnlyConstantResult || result.isConstant() ? result : null;
    }

    private static ValueNode arrayLength(ValueNode value, ArrayLengthProvider.FindLengthMode mode, ConstantReflectionProvider constantReflection, EconomicMap<ValueNode, ValueNode> visitedPhiInputs) {
        Objects.requireNonNull(mode);

        EconomicMap<ValueNode, ValueNode> visitedPhiInputMap = visitedPhiInputs;
        ValueNode current = value;
        StructuredGraph graph = value.graph();
        boolean allowOnlyConstantResult = false;
        do {
            CompilationAlarm.checkProgress(graph);
            /*
             * PiArrayNode implements ArrayLengthProvider and ValueProxy. We want to treat it as an
             * ArrayLengthProvider, therefore we check this case first.
             */
            if (current instanceof ArrayLengthProvider provider) {
                return filterArrayLengthResult(provider.findLength(mode, constantReflection), allowOnlyConstantResult);

            } else if (current instanceof ValuePhiNode phi) {
                if (visitedPhiInputMap == null) {
                    visitedPhiInputMap = EconomicMap.create();
                }
                return filterArrayLengthResult(phiArrayLength(phi, mode, constantReflection, visitedPhiInputMap), allowOnlyConstantResult);

            } else if (current instanceof ValueProxyNode proxy) {
                ValueNode length = arrayLength(proxy.getOriginalNode(), mode, constantReflection);
                if (mode == ArrayLengthProvider.FindLengthMode.CANONICALIZE_READ && length != null && !length.isConstant()) {
                    length = new ValueProxyNode(length, proxy.proxyPoint());
                }
                return filterArrayLengthResult(length, allowOnlyConstantResult);

            } else if (current instanceof LimitedValueProxy valueProxy) {
                /*
                 * Note is it usually recommended to check for ValueProxy, not LimitedValueProxy.
                 * However, in this case we are intentionally unproxifying all LimitedValueProxies,
                 * as we want constant lengths to be found across DeoptProxyNodes. When the result
                 * is not a ValueProxy we limit the returned result to constant values.
                 */
                if (!(valueProxy instanceof ValueProxy)) {
                    allowOnlyConstantResult = true;
                }
                current = valueProxy.getOriginalNode();
            } else {
                return null;
            }
        } while (true);  // TERMINATION ARGUMENT: processing specific inputs until an exit criteria
                         // is met
    }

    private static ValueNode phiArrayLength(ValuePhiNode phi, ArrayLengthProvider.FindLengthMode mode, ConstantReflectionProvider constantReflection,
                    EconomicMap<ValueNode, ValueNode> visitedPhiInputs) {
        if (phi.merge() instanceof LoopBeginNode) {
            /*
             * Avoid fixed point computation by not processing phi functions that could introduce
             * cycles.
             */
            return null;
        }

        ValueNode singleLength = null;
        for (int i = 0; i < phi.values().count(); i++) {
            ValueNode input = phi.values().get(i);
            if (input == null) {
                return null;
            }
            /*
             * Multi-way phis can have the same input along many paths. Avoid the exponential blowup
             * from visiting them many times.
             */
            ValueNode length = null;
            if (visitedPhiInputs.containsKey(input)) {
                length = visitedPhiInputs.get(input);
            } else {
                length = arrayLength(input, mode, constantReflection, visitedPhiInputs);
                if (length == null) {
                    return null;
                }
                visitedPhiInputs.put(input, length);
            }
            assert length.stamp(NodeView.DEFAULT).getStackKind() == JavaKind.Int : Assertions.errorMessage(length, phi);

            if (i == 0) {
                assert singleLength == null;
                singleLength = length;
            } else if (singleLength == length) {
                /* Nothing to do, still having a single length. */
            } else {
                return null;
            }
        }
        return singleLength;
    }

    /**
     * Tries to find an original value of the given node by traversing through proxies and
     * unambiguous phis. Note that this method will perform an exhaustive search through phis.
     *
     * @param value the node whose original value should be determined
     * @param abortOnLoopPhi specifies if the traversal through phis should stop and return
     *            {@code value} if it hits a {@linkplain PhiNode#isLoopPhi loop phi}. This argument
     *            must be {@code true} if used during graph building as loop phi nodes may not yet
     *            have all their inputs computed.
     * @return the original value (which might be {@code value} itself)
     */
    public static ValueNode originalValue(ValueNode value, boolean abortOnLoopPhi) {
        ValueNode result = originalValueSimple(value, abortOnLoopPhi);
        assert result != null;
        return result;
    }

    private static ValueNode originalValueSimple(ValueNode value, boolean abortOnLoopPhi) {
        /* The very simple case: look through proxies. */
        ValueNode cur = originalValueForProxy(value);

        while (cur instanceof PhiNode) {
            /*
             * We found a phi function. Check if we can analyze it without allocating temporary data
             * structures.
             */
            PhiNode phi = (PhiNode) cur;

            if (abortOnLoopPhi && phi.isLoopPhi()) {
                return value;
            }

            ValueNode phiSingleValue = null;
            int count = phi.valueCount();
            for (int i = 0; i < count; ++i) {
                ValueNode phiCurValue = originalValueForProxy(phi.valueAt(i));
                if (phiCurValue == phi) {
                    /* Simple cycle, we can ignore the input value. */
                } else if (phiSingleValue == null) {
                    /* The first input. */
                    phiSingleValue = phiCurValue;
                } else if (phiSingleValue != phiCurValue) {
                    /* Another input that is different from the first input. */

                    if (phiSingleValue instanceof PhiNode || phiCurValue instanceof PhiNode) {
                        /*
                         * We have two different input values for the phi function, and at least one
                         * of the inputs is another phi function. We need to do a complicated
                         * exhaustive check.
                         */
                        return originalValueForComplicatedPhi(value, phi, new NodeBitMap(value.graph()), abortOnLoopPhi);
                    } else {
                        /*
                         * We have two different input values for the phi function, but none of them
                         * is another phi function. This phi function cannot be reduce any further,
                         * so the phi function is the original value.
                         */
                        return phi;
                    }
                }
            }

            /*
             * Successfully reduced the phi function to a single input value. The single input value
             * can itself be a phi function again, so we might take another loop iteration.
             */
            assert phiSingleValue != null;
            cur = phiSingleValue;
        }

        /* We reached a "normal" node, which is the original value. */
        assert !(cur instanceof LimitedValueProxy) && !(cur instanceof PhiNode) : Assertions.errorMessageContext("cur", cur);
        return cur;
    }

    private static ValueNode originalValueForProxy(ValueNode value) {
        ValueNode cur = value;
        while (cur instanceof LimitedValueProxy) {
            cur = ((LimitedValueProxy) cur).getOriginalNode();
        }
        return cur;
    }

    /**
     * Handling for complicated nestings of phi functions. We need to reduce phi functions
     * recursively, and need a temporary map of visited nodes to avoid endless recursion of cycles.
     *
     * @param value the node whose original value is being determined
     * @param abortOnLoopPhi specifies if the traversal through phis should stop and return
     *            {@code value} if it hits a {@linkplain PhiNode#isLoopPhi loop phi}
     */
    private static ValueNode originalValueForComplicatedPhi(ValueNode value, PhiNode phi, NodeBitMap visited, boolean abortOnLoopPhi) {
        if (visited.isMarked(phi)) {
            /*
             * Found a phi function that was already seen. Either a cycle, or just a second phi
             * input to a path we have already processed.
             */
            return null;
        }
        visited.mark(phi);

        ValueNode phiSingleValue = null;
        int count = phi.valueCount();
        for (int i = 0; i < count; ++i) {
            ValueNode phiCurValue = originalValueForProxy(phi.valueAt(i));
            if (phiCurValue instanceof PhiNode) {
                /* Recursively process a phi function input. */
                PhiNode curPhi = (PhiNode) phiCurValue;
                if (abortOnLoopPhi && curPhi.isLoopPhi()) {
                    return value;
                }
                phiCurValue = originalValueForComplicatedPhi(value, curPhi, visited, abortOnLoopPhi);
                if (phiCurValue == value) {
                    // Hit a loop phi
                    assert abortOnLoopPhi;
                    return value;
                }
            }

            if (phiCurValue == null) {
                /* Cycle to a phi function that was already seen. We can ignore this input. */
            } else if (phiSingleValue == null) {
                /* The first input. */
                phiSingleValue = phiCurValue;
            } else if (phiCurValue != phiSingleValue) {
                /*
                 * Another input that is different from the first input. Since we already
                 * recursively looked through other phi functions, we now know that this phi
                 * function cannot be reduce any further, so the phi function is the original value.
                 */
                return phi;
            }
        }
        return phiSingleValue;
    }

    public static boolean tryKillUnused(Node node) {
        if (shouldKillUnused(node)) {
            killWithUnusedFloatingInputs(node);
            return true;
        }
        return false;
    }

    public static boolean shouldKillUnused(Node node) {
        return node.isAlive() && isFloatingNode(node) && node.hasNoUsages() && !(node instanceof GuardNode);
    }

    /**
     * Returns an iterator that will return the given node followed by all its predecessors, up
     * until the point where {@link Node#predecessor()} returns null.
     *
     * @param start the node at which to start iterating
     */
    public static NodeIterable<FixedNode> predecessorIterable(final FixedNode start) {
        return new NodeIterable<>() {
            @Override
            public Iterator<FixedNode> iterator() {
                return new Iterator<>() {
                    public FixedNode current = start;

                    @Override
                    public boolean hasNext() {
                        return current != null;
                    }

                    @Override
                    public FixedNode next() {
                        try {
                            return current;
                        } finally {
                            current = (FixedNode) current.predecessor();
                        }
                    }
                };
            }
        };
    }

    private static final class DefaultSimplifierTool extends CoreProvidersDelegate implements SimplifierTool {
        private final boolean canonicalizeReads;
        private final Assumptions assumptions;
        private final OptionValues options;

        DefaultSimplifierTool(CoreProviders providers, boolean canonicalizeReads, Assumptions assumptions, OptionValues options) {
            super(providers);
            this.canonicalizeReads = canonicalizeReads;
            this.assumptions = assumptions;
            this.options = options;
        }

        @Override
        public boolean canonicalizeReads() {
            return canonicalizeReads;
        }

        @Override
        public boolean allUsagesAvailable() {
            return true;
        }

        @Override
        public void deleteBranch(Node branch) {
            FixedNode fixedBranch = (FixedNode) branch;
            fixedBranch.predecessor().replaceFirstSuccessor(fixedBranch, null);
            GraphUtil.killCFG(fixedBranch);
        }

        @Override
        public void removeIfUnused(Node node) {
            GraphUtil.tryKillUnused(node);
        }

        @Override
        public void addToWorkList(Node node) {
        }

        @Override
        public void addToWorkList(Iterable<? extends Node> nodes) {
        }

        @Override
        public boolean trySinkWriteFences() {
            return false;
        }

        @Override
        public Assumptions getAssumptions() {
            return assumptions;
        }

        @Override
        public OptionValues getOptions() {
            return options;
        }

        @Override
        public Integer smallestCompareWidth() {
            if (getLowerer() != null) {
                return getLowerer().smallestCompareWidth();
            } else {
                return null;
            }
        }

        @Override
        public boolean divisionOverflowIsJVMSCompliant() {
            if (getLowerer() != null) {
                return getLowerer().divisionOverflowIsJVMSCompliant();
            } else {
                // prevent accidental floating of divs if we don't know the target arch
                return false;
            }
        }
    }

    public static SimplifierTool getDefaultSimplifier(CoreProviders providers, boolean canonicalizeReads, Assumptions assumptions, OptionValues options) {
        return new DefaultSimplifierTool(providers, canonicalizeReads, assumptions, options);
    }

    /**
     * Virtualize an array copy.
     *
     * @param tool the virtualization tool
     * @param source the source array
     * @param sourceLength the length of the source array
     * @param newLength the length of the new array
     * @param from the start index in the source array
     * @param newComponentType the component type of the new array
     * @param elementKind the kind of the new array elements
     * @param graph the node graph
     * @param virtualArrayProvider a functional provider that returns a new virtual array given the
     *            component type and length
     */
    public static void virtualizeArrayCopy(VirtualizerTool tool, ValueNode source, ValueNode sourceLength, ValueNode newLength, ValueNode from, ResolvedJavaType newComponentType, JavaKind elementKind,
                    StructuredGraph graph, BiFunction<ResolvedJavaType, Integer, VirtualArrayNode> virtualArrayProvider) {

        ValueNode sourceAlias = tool.getAlias(source);
        ValueNode replacedSourceLength = tool.getAlias(sourceLength);
        ValueNode replacedNewLength = tool.getAlias(newLength);
        ValueNode replacedFrom = tool.getAlias(from);
        if (!replacedNewLength.isConstant() || !replacedFrom.isConstant() || !replacedSourceLength.isConstant()) {
            return;
        }

        assert newComponentType != null : "An array copy can be virtualized only if the real type of the resulting array is known statically.";

        int fromInt = replacedFrom.asJavaConstant().asInt();
        int newLengthInt = replacedNewLength.asJavaConstant().asInt();
        int sourceLengthInt = replacedSourceLength.asJavaConstant().asInt();
        if (sourceAlias instanceof VirtualObjectNode) {
            VirtualObjectNode sourceVirtual = (VirtualObjectNode) sourceAlias;
            assert sourceLengthInt == sourceVirtual.entryCount() : Assertions.errorMessageContext("sourceLengthInt", sourceLengthInt, "virtual", sourceVirtual, "sourceVirtual.EntryCount",
                            sourceVirtual.entryCount());
        }

        if (fromInt < 0 || newLengthInt < 0 || fromInt > sourceLengthInt) {
            /* Illegal values for either from index, the new length or the source length. */
            return;
        }

        if (newLengthInt > tool.getMaximumEntryCount()) {
            /* The new array size is higher than maximum allowed size of virtualized objects. */
            return;
        }

        ValueNode[] newEntryState = new ValueNode[newLengthInt];
        int readLength = Math.min(newLengthInt, sourceLengthInt - fromInt);

        if (sourceAlias instanceof VirtualObjectNode) {
            /* The source array is virtualized, just copy over the values. */
            VirtualObjectNode sourceVirtual = (VirtualObjectNode) sourceAlias;
            boolean alwaysAssignable = newComponentType.getJavaKind() == JavaKind.Object && newComponentType.isJavaLangObject();
            for (int i = 0; i < readLength; i++) {
                ValueNode entry = tool.getEntry(sourceVirtual, fromInt + i);
                if (!alwaysAssignable) {
                    ResolvedJavaType entryType = StampTool.typeOrNull(entry, tool.getMetaAccess());
                    if (entryType == null) {
                        return;
                    }
                    if (!newComponentType.isAssignableFrom(entryType)) {
                        return;
                    }
                }
                newEntryState[i] = entry;
            }
        } else {
            /* The source array is not virtualized, emit index loads. */
            ResolvedJavaType sourceType = StampTool.typeOrNull(sourceAlias, tool.getMetaAccess());
            if (sourceType == null || !sourceType.isArray() || !newComponentType.isAssignableFrom(sourceType.getElementalType())) {
                return;
            }
            for (int i = 0; i < readLength; i++) {
                LoadIndexedNode load = new LoadIndexedNode(null, sourceAlias, ConstantNode.forInt(i + fromInt, graph), null, elementKind);
                tool.addNode(load);
                newEntryState[i] = load;
            }
        }
        if (readLength < newLengthInt) {
            /* Pad the copy with the default value of its elment kind. */
            ValueNode defaultValue = ConstantNode.defaultForKind(elementKind, graph);
            for (int i = readLength; i < newLengthInt; i++) {
                newEntryState[i] = defaultValue;
            }
        }
        /* Perform the replacement. */
        VirtualArrayNode newVirtualArray = virtualArrayProvider.apply(newComponentType, newLengthInt);
        tool.createVirtualObject(newVirtualArray, newEntryState, Collections.<MonitorIdNode> emptyList(), source.getNodeSourcePosition(), false);
        tool.replaceWithVirtual(newVirtualArray);
    }

    /**
     * Snippet lowerings may produce patterns without a frame state on the merge. We need to take
     * extra care when optimizing these patterns.
     */
    public static boolean checkFrameState(FixedNode start, int maxDepth) {
        if (maxDepth == 0) {
            return false;
        }
        FixedNode node = start;
        while (true) { // TERMINATION ARGUMENT: following next nodes or returning
            CompilationAlarm.checkProgress(start.graph());
            if (node instanceof AbstractMergeNode) {
                AbstractMergeNode mergeNode = (AbstractMergeNode) node;
                if (mergeNode.stateAfter() == null) {
                    return false;
                } else {
                    return true;
                }
            } else if (node instanceof StateSplit) {
                StateSplit stateSplitNode = (StateSplit) node;
                if (stateSplitNode.stateAfter() != null) {
                    return true;
                }
            }

            if (node instanceof ControlSplitNode) {
                ControlSplitNode controlSplitNode = (ControlSplitNode) node;
                for (Node succ : controlSplitNode.cfgSuccessors()) {
                    if (checkFrameState((FixedNode) succ, maxDepth - 1)) {
                        return true;
                    }
                }
                return false;
            } else if (node instanceof FixedWithNextNode) {
                FixedWithNextNode fixedWithNextNode = (FixedWithNextNode) node;
                node = fixedWithNextNode.next();
            } else if (node instanceof AbstractEndNode) {
                AbstractEndNode endNode = (AbstractEndNode) node;
                node = endNode.merge();
            } else if (node instanceof ControlSinkNode) {
                return true;
            } else {
                assert false : "unexpected node";
                return false;
            }
        }
    }

    public static boolean mayRemoveSplit(IfNode ifNode) {
        return GraphUtil.checkFrameState(ifNode.trueSuccessor(), MAX_FRAMESTATE_SEARCH_DEPTH) && GraphUtil.checkFrameState(ifNode.falseSuccessor(), MAX_FRAMESTATE_SEARCH_DEPTH);
    }

    /**
     * An if node with an empty body at the end of a loop is represented with a {@link LoopEndNode}
     * at the end of each path. For some optimizations it is more useful to have a representation of
     * the if statement as a proper diamond with a merge after the two bodies, followed by a
     * {@link LoopEndNode}. This method tries to transform the given {@code ifNode} into such a
     * form, introducing new phi nodes for the diamond and patching up the loop's phis accordingly.
     * On success, the newly introduced loop end node is returned. If the given {@code ifNode} is
     * not an if statement with empty bodies at the end of the loop, the graph is not modified, and
     * {@code null} is returned.
     *
     * Note that the diamond representation is not canonical and will be undone by the next
     * application of {@link AbstractMergeNode#simplify(SimplifierTool)} to the merge.
     */
    public static LoopEndNode tryToTransformToEmptyLoopDiamond(IfNode ifNode, LoopBeginNode loopBegin) {
        if (ifNode.trueSuccessor().next() instanceof AbstractEndNode && ifNode.falseSuccessor().next() instanceof AbstractEndNode) {
            AbstractEndNode trueEnd = (AbstractEndNode) ifNode.trueSuccessor().next();
            AbstractEndNode falseEnd = (AbstractEndNode) ifNode.falseSuccessor().next();
            if (trueEnd.merge() == loopBegin && falseEnd.merge() == loopBegin) {
                StructuredGraph graph = loopBegin.graph();
                for (PhiNode phi : loopBegin.phis()) {
                    if (!(phi instanceof ValuePhiNode || phi instanceof MemoryPhiNode)) {
                        return null;
                    }
                }

                EndNode newTrueEnd = graph.add(new EndNode());
                EndNode newFalseEnd = graph.add(new EndNode());
                MergeNode merge = graph.add(new MergeNode());
                merge.addForwardEnd(newTrueEnd);
                merge.addForwardEnd(newFalseEnd);

                EconomicMap<PhiNode, PhiNode> replacementPhis = EconomicMap.create(Equivalence.IDENTITY);
                for (PhiNode phi : loopBegin.phis()) {
                    if (phi instanceof ValuePhiNode) {
                        ValuePhiNode valuePhi = (ValuePhiNode) phi;
                        ValuePhiNode newPhi = phi.graph().unique(new ValuePhiNode(valuePhi.stamp(NodeView.DEFAULT), merge, new ValueNode[]{valuePhi.valueAt(trueEnd), valuePhi.valueAt(falseEnd)}));
                        replacementPhis.put(phi, newPhi);
                    } else if (phi instanceof MemoryPhiNode) {
                        MemoryPhiNode memoryPhi = (MemoryPhiNode) phi;
                        MemoryPhiNode newPhi = phi.graph().unique(new MemoryPhiNode(merge, memoryPhi.getLocationIdentity(), new ValueNode[]{memoryPhi.valueAt(trueEnd), memoryPhi.valueAt(falseEnd)}));
                        replacementPhis.put(phi, newPhi);
                    } else {
                        GraalError.shouldNotReachHereUnexpectedValue(phi); // ExcludeFromJacocoGeneratedReport
                    }
                }
                assert loopBegin.phis().count() == replacementPhis.size() : Assertions.errorMessage(loopBegin, loopBegin.phis(), replacementPhis);

                loopBegin.removeEnd(trueEnd);
                loopBegin.removeEnd(falseEnd);
                ifNode.trueSuccessor().setNext(newTrueEnd);
                ifNode.falseSuccessor().setNext(newFalseEnd);
                trueEnd.safeDelete();
                falseEnd.safeDelete();

                LoopEndNode newEnd = graph.add(new LoopEndNode(loopBegin));
                merge.setNext(newEnd);
                int i = 0;
                for (PhiNode phi : loopBegin.phis()) {
                    PhiNode replacementPhi = replacementPhis.get(phi);
                    assert (phi instanceof ValuePhiNode && replacementPhi instanceof ValuePhiNode) ||
                                    (phi instanceof MemoryPhiNode && replacementPhi instanceof MemoryPhiNode) : Assertions.errorMessageContext("phi", phi, "replacementPhi", replacementPhi);
                    ValueNode replacementValue = replacementPhi.singleValueOrThis();
                    phi.addInput(replacementValue);
                    i++;
                }
                assert i == replacementPhis.size() : "did not consume all values";
                for (PhiNode maybeUnused : replacementPhis.getValues()) {
                    if (maybeUnused.hasNoUsages() && !maybeUnused.isDeleted()) {
                        maybeUnused.safeDelete();
                    }
                }

                return newEnd;
            }
        }
        return null;
    }

    /**
     * Find the last, i.e. dominating, {@link StateSplit} node that returns {@code true} for
     * {@link StateSplit#hasSideEffect()} and return its {@link StateSplit#stateAfter()}. That is
     * the {@link FrameState} node describing the current frame since no {@linkplain StateSplit side
     * effect} happened in between.
     *
     * This method will check Graal's invariant relations regarding side-effects and framestates.
     */
    public static FrameState findLastFrameState(FixedNode start) {
        GraalError.guarantee(start.graph().getGuardsStage().areFrameStatesAtSideEffects(), "Framestates must be at side effects when looking for state split nodes");
        assert start != null;
        FixedNode lastFixedNode = null;
        FixedNode currentStart = start;
        while (true) { // TERMINATION ARGUMENT: following prev nodes
            CompilationAlarm.checkProgress(start.graph());
            for (FixedNode fixed : GraphUtil.predecessorIterable(currentStart)) {
                if (fixed instanceof StateSplit) {
                    StateSplit stateSplit = (StateSplit) fixed;
                    GraalError.guarantee(!stateSplit.hasSideEffect() || stateSplit.stateAfter() != null, "Found state split with side-effect without framestate=%s", stateSplit);
                    if (stateSplit.stateAfter() != null) {
                        return stateSplit.stateAfter();
                    }
                }
                lastFixedNode = fixed;
            }
            if (lastFixedNode instanceof LoopBeginNode) {
                currentStart = ((LoopBeginNode) lastFixedNode).forwardEnd();
                continue;
            }
            break;
        }
        return null;
    }

    public static boolean assertIsConstant(ValueNode n) {
        assert n.isConstant() : "Node " + n + " must be constant";
        return true;
    }

    /**
     * Optimizes control split nodes by deduplicating the successor nodes of its control flow graph
     * successor statements.
     *
     * An example would be case statements of a Switch. The illustrative example below is for a
     * switch control split node. However, we perform the same transformation also for if nodes.
     *
     * <p>
     * This transformation is only applied to patterns where the same code prefix is executed after
     * each case, such as the following example:
     *
     * <pre>
     * public static int switchReducePattern(int a) {
     *     int result = 0;
     *     switch (a) {
     *         case 1:
     *             result = sideEffect;
     *             // some other code 1
     *             break;
     *         case 2:
     *             result = sideEffect;
     *             // some other code 2
     *             break;
     *         case 3:
     *             result = sideEffect;
     *             // some other code 3
     *             break;
     *         default:
     *             result = sideEffect;
     *             // some other code 4
     *             break;
     *     }
     *     return result;
     * }
     * </pre>
     *
     * <p>
     * The optimized code will have the common successor code extracted before the switch statement,
     * resulting in:
     *
     * <pre>
     * public static int switchReducePattern(int a) {
     *     int result = 0;
     *     result = sideEffect; // deduplicated before switch
     *     switch (a) {
     *         case 1:
     *             // some other code 1
     *             break;
     *         case 2:
     *             // some other code 2
     *             break;
     *         case 3:
     *             // some other code 3
     *             break;
     *         default:
     *             // some other code 4
     *             break;
     *     }
     *     return result;
     * }
     * </pre>
     */
    public static void tryDeDuplicateSplitSuccessors(ControlSplitNode split, SimplifierTool tool) {
        do {
            Node referenceSuccessor = null;
            for (Node successor : split.successors()) {
                if (successor instanceof BeginNode begin && begin.next() instanceof FixedWithNextNode fwn) {
                    if (successor.hasUsages()) {
                        return;
                    }
                    if (fwn instanceof AbstractBeginNode || fwn instanceof ControlFlowAnchored || fwn instanceof MemoryAnchorNode || fwn instanceof SwitchCaseProbabilityNode) {
                        /*
                         * Cannot do this optimization for begin nodes, because it could move guards
                         * above the control split that need to stay below a branch.
                         *
                         * Cannot do this optimization for ControlFlowAnchored nodes, because these
                         * are anchored in their control-flow position, and should not be moved
                         * upwards.
                         */
                        return;
                    }
                    if (referenceSuccessor == null) {
                        referenceSuccessor = fwn;
                    } else {
                        // ensure we are alike the reference successor - check if all case
                        // successors are structurally and data wise the same node
                        if (referenceSuccessor.getClass() != fwn.getClass()) {
                            return;
                        }
                        if (!fwn.dataFlowEquals(referenceSuccessor)) {
                            return;
                        }
                    }
                } else {
                    return;
                }
            }

            List<Node> successorList = split.successors().snapshot();
            FixedWithNextNode firstSuccessorNext = (FixedWithNextNode) ((FixedWithNextNode) successorList.getFirst()).next();

            GraphUtil.unlinkFixedNode(firstSuccessorNext);
            split.graph().addBeforeFixed(split, firstSuccessorNext);

            for (int i = 1; i < successorList.size(); i++) {
                FixedNode otherSuccessorNext = ((FixedWithNextNode) successorList.get(i)).next();
                otherSuccessorNext.replaceAtUsages(firstSuccessorNext);
                split.graph().removeFixed((FixedWithNextNode) otherSuccessorNext);
            }

            /*
             * Immediately cleanup usages - this is required as certain simplify implementations in
             * the compiler expect "known" graph shapes. De-duplication is an intrusive operation
             * that can destroy these invariants (temporarily). Thus, we immediately cleanup
             * floating usages to be deduplicated as well to have a correct graph shape again.
             *
             * Mostly relevant for commit allocation nodes and other partial escaped graph shapes.
             */
            for (Node usage : firstSuccessorNext.usages().snapshot()) {
                if (usage.isAlive()) {
                    NodeClass<?> usageNodeClass = usage.getNodeClass();
                    if (usageNodeClass.valueNumberable() && !usageNodeClass.isLeafNode()) {
                        Node newNode = split.graph().findDuplicate(usage);
                        if (newNode != null) {
                            usage.replaceAtUsagesAndDelete(newNode);
                        }
                    }
                    if (usage.isAlive()) {
                        tool.addToWorkList(usage);
                    }
                }
            }
        } while (true); // TERMINATION ARGUMENT: processing fixed nodes until duplication is no
        // longer possible.
    }
}
