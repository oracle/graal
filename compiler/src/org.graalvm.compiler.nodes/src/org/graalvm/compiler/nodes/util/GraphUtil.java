/*
 * Copyright (c) 2011, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.nodes.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Equivalence;
import org.graalvm.collections.MapCursor;
import org.graalvm.compiler.bytecode.Bytecode;
import org.graalvm.compiler.code.SourceStackTraceBailoutException;
import org.graalvm.compiler.core.common.type.ObjectStamp;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.graph.Graph;
import org.graalvm.compiler.graph.LinkedNodeStack;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeBitMap;
import org.graalvm.compiler.graph.NodeSourcePosition;
import org.graalvm.compiler.graph.NodeStack;
import org.graalvm.compiler.graph.Position;
import org.graalvm.compiler.graph.iterators.NodeIterable;
import org.graalvm.compiler.nodes.AbstractBeginNode;
import org.graalvm.compiler.nodes.AbstractEndNode;
import org.graalvm.compiler.nodes.AbstractMergeNode;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ControlSinkNode;
import org.graalvm.compiler.nodes.ControlSplitNode;
import org.graalvm.compiler.nodes.EndNode;
import org.graalvm.compiler.nodes.FixedNode;
import org.graalvm.compiler.nodes.FixedWithNextNode;
import org.graalvm.compiler.nodes.FrameState;
import org.graalvm.compiler.nodes.GuardNode;
import org.graalvm.compiler.nodes.IfNode;
import org.graalvm.compiler.nodes.LoopBeginNode;
import org.graalvm.compiler.nodes.LoopEndNode;
import org.graalvm.compiler.nodes.LoopExitNode;
import org.graalvm.compiler.nodes.MergeNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.PhiNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.ProxyNode;
import org.graalvm.compiler.nodes.StateSplit;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.ValuePhiNode;
import org.graalvm.compiler.nodes.ValueProxyNode;
import org.graalvm.compiler.nodes.WithExceptionNode;
import org.graalvm.compiler.nodes.extended.MultiGuardNode;
import org.graalvm.compiler.nodes.java.LoadIndexedNode;
import org.graalvm.compiler.nodes.java.MethodCallTargetNode;
import org.graalvm.compiler.nodes.java.MonitorIdNode;
import org.graalvm.compiler.nodes.memory.MemoryPhiNode;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider;
import org.graalvm.compiler.nodes.spi.ArrayLengthProvider.FindLengthMode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.spi.CoreProvidersDelegate;
import org.graalvm.compiler.nodes.spi.LimitedValueProxy;
import org.graalvm.compiler.nodes.spi.SimplifierTool;
import org.graalvm.compiler.nodes.spi.ValueProxy;
import org.graalvm.compiler.nodes.spi.VirtualizerTool;
import org.graalvm.compiler.nodes.type.StampTool;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionKey;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.options.OptionValues;

import jdk.vm.ci.code.BailoutException;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Constant;
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
                    assert merge.forwardEndCount() == 1;
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
            OptionValues options = node.getOptions();
            boolean verifyGraalGraphEdges = node.graph().verifyGraphEdges;
            boolean verifyKillCFGUnusedNodes = GraphUtil.Options.VerifyKillCFGUnusedNodes.getValue(options);
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
                        if (e == Graph.NodeEvent.INPUT_CHANGED && n instanceof FixedNode && !(n instanceof AbstractMergeNode) && n.predecessor() == null) {
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
                        GraalError.shouldNotReachHere();
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
        LinkedNodeStack stack = null;
        Node cur = node;
        do {
            assert checkKill(cur, mayKillGuard);
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
                            stack = new LinkedNodeStack();
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
        } while (true);
    }

    /**
     * Removes all nodes created after the {@code mark}, assuming no "old" nodes point to "new"
     * nodes.
     */
    public static void removeNewNodes(Graph graph, Graph.Mark mark) {
        assert checkNoOldToNewEdges(graph, mark);
        for (Node n : graph.getNewNodes(mark)) {
            n.markDeleted();
            for (Node in : n.inputs()) {
                in.removeUsage(n);
            }
        }
    }

    private static boolean checkNoOldToNewEdges(Graph graph, Graph.Mark mark) {
        for (Node old : graph.getNodes()) {
            if (graph.isNew(mark, old)) {
                break;
            }
            for (Node n : old.successors()) {
                assert !graph.isNew(mark, n) : old + " -> " + n;
            }
            for (Node n : old.inputs()) {
                assert !graph.isNew(mark, n) : old + " -> " + n;
            }
        }
        return true;
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
                assert begin.forwardEndCount() == 1;
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
     * available. Details of the different modes are documented in {@link FindLengthMode}.
     *
     * @param value The start value.
     * @param mode The mode as documented in {@link FindLengthMode}.
     * @return The array length if one was found, or null otherwise.
     */
    public static ValueNode arrayLength(ValueNode value, FindLengthMode mode, ConstantReflectionProvider constantReflection) {
        return arrayLength(value, mode, constantReflection, null);
    }

    private static ValueNode arrayLength(ValueNode value, FindLengthMode mode, ConstantReflectionProvider constantReflection, EconomicMap<ValueNode, ValueNode> visitedPhiInputs) {
        Objects.requireNonNull(mode);

        EconomicMap<ValueNode, ValueNode> visitedPhiInputMap = visitedPhiInputs;
        ValueNode current = value;
        do {
            /*
             * PiArrayNode implements ArrayLengthProvider and ValueProxy. We want to treat it as an
             * ArrayLengthProvider, therefore we check this case first.
             */
            if (current instanceof ArrayLengthProvider) {
                return ((ArrayLengthProvider) current).findLength(mode, constantReflection);

            } else if (current instanceof ValuePhiNode) {
                if (visitedPhiInputMap == null) {
                    visitedPhiInputMap = EconomicMap.create();
                }
                return phiArrayLength((ValuePhiNode) current, mode, constantReflection, visitedPhiInputMap);

            } else if (current instanceof ValueProxyNode) {
                ValueProxyNode proxy = (ValueProxyNode) current;
                ValueNode length = arrayLength(proxy.getOriginalNode(), mode, constantReflection);
                if (mode == ArrayLengthProvider.FindLengthMode.CANONICALIZE_READ && length != null && !length.isConstant()) {
                    length = new ValueProxyNode(length, proxy.proxyPoint());
                }
                return length;

            } else if (current instanceof ValueProxy) {
                /* Written as a loop instead of a recursive call to reduce recursion depth. */
                current = ((ValueProxy) current).getOriginalNode();

            } else {
                return null;
            }
        } while (true);
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
            assert length.stamp(NodeView.DEFAULT).getStackKind() == JavaKind.Int;

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
        assert !(cur instanceof LimitedValueProxy) && !(cur instanceof PhiNode);
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
        if (node.isAlive() && isFloatingNode(node) && node.hasNoUsages() && !(node instanceof GuardNode)) {
            killWithUnusedFloatingInputs(node);
            return true;
        }
        return false;
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
        public boolean supportsRounding() {
            if (getLowerer() != null) {
                return getLowerer().supportsRounding();
            } else {
                return false;
            }
        }

        @Override
        public boolean divisionOverflowIsJVMSCompliant() {
            if (getLowerer() != null) {
                return getLowerer().divisionOverflowIsJVMSCompliant();
            } else {
                // prevent accidental floating of divs if we dont know the target arch
                return false;
            }
        }
    }

    public static SimplifierTool getDefaultSimplifier(CoreProviders providers, boolean canonicalizeReads, Assumptions assumptions, OptionValues options) {
        return new DefaultSimplifierTool(providers, canonicalizeReads, assumptions, options);
    }

    public static Constant foldIfConstantAndRemove(ValueNode node, ValueNode constant) {
        assert node.inputs().contains(constant);
        if (constant.isConstant()) {
            node.replaceFirstInput(constant, null);
            Constant result = constant.asConstant();
            tryKillUnused(constant);
            return result;
        }
        return null;
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
            assert sourceLengthInt == sourceVirtual.entryCount();
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
        while (true) {
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
                        GraalError.shouldNotReachHere();
                    }
                }
                assert loopBegin.phis().count() == replacementPhis.size();

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
                    assert (phi instanceof ValuePhiNode && replacementPhi instanceof ValuePhiNode) || (phi instanceof MemoryPhiNode && replacementPhi instanceof MemoryPhiNode);
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
        while (true) {
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

}
