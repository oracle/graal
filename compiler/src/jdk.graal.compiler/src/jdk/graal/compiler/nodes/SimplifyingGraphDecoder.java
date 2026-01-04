/*
 * Copyright (c) 2015, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.nodes;

import static jdk.graal.compiler.nodeinfo.InputType.Guard;
import static jdk.graal.compiler.nodeinfo.NodeCycles.CYCLES_IGNORED;
import static jdk.graal.compiler.nodeinfo.NodeSize.SIZE_IGNORED;

import java.util.List;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TimerKey;
import jdk.graal.compiler.graph.Edges;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeBitMap;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.extended.AnchoringNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.extended.IntegerSwitchNode;
import jdk.graal.compiler.nodes.extended.SwitchNode;
import jdk.graal.compiler.nodes.extended.UnsafeAccessNode;
import jdk.graal.compiler.nodes.java.ArrayLengthNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.LoadIndexedNode;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.CoreProvidersDelegate;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.vm.ci.code.Architecture;
import jdk.vm.ci.meta.Assumptions;

/**
 * Graph decoder that simplifies nodes during decoding. The standard
 * {@link Canonicalizable#canonical node canonicalization} interface is used to canonicalize nodes
 * during decoding. Additionally, {@link IfNode branches} and {@link IntegerSwitchNode switches}
 * with constant conditions are simplified.
 */
public class SimplifyingGraphDecoder extends GraphDecoder {

    private static final TimerKey CanonicalizeFixedNode = DebugContext.timer("PartialEvaluation-CanonicalizeFixedNode").doc("Time spent in simplifying fixed nodes.");

    protected final CoreProviders providers;
    protected final boolean canonicalizeReads;
    protected final CanonicalizerTool canonicalizerTool;

    protected class PECanonicalizerTool extends CoreProvidersDelegate implements CanonicalizerTool {

        private final Assumptions assumptions;
        private final OptionValues options;

        public PECanonicalizerTool(Assumptions assumptions, OptionValues options) {
            super(providers);
            this.assumptions = assumptions;
            this.options = options;
        }

        @Override
        public OptionValues getOptions() {
            return options;
        }

        @Override
        public boolean canonicalizeReads() {
            return canonicalizeReads;
        }

        @Override
        public boolean allUsagesAvailable() {
            return false;
        }

        @Override
        public Assumptions getAssumptions() {
            return assumptions;
        }

        @Override
        public Integer smallestCompareWidth() {
            // to be safe, just report null here
            // there will be more opportunities for this optimization later
            return null;
        }

        @Override
        public boolean divisionOverflowIsJVMSCompliant() {
            return getLowerer().divisionOverflowIsJVMSCompliant();
        }

    }

    @NodeInfo(cycles = CYCLES_IGNORED, size = SIZE_IGNORED, allowedUsageTypes = {Guard, InputType.Anchor})
    static class CanonicalizeToNullNode extends FloatingNode implements Canonicalizable, GuardingNode, AnchoringNode {
        public static final NodeClass<CanonicalizeToNullNode> TYPE = NodeClass.create(CanonicalizeToNullNode.class);

        protected CanonicalizeToNullNode(Stamp stamp) {
            super(TYPE, stamp);
        }

        @Override
        public Node canonical(CanonicalizerTool tool) {
            return null;
        }
    }

    public SimplifyingGraphDecoder(Architecture architecture, StructuredGraph graph, CoreProviders providers, boolean canonicalizeReads) {
        super(architecture, graph);
        this.providers = providers;
        this.canonicalizeReads = canonicalizeReads;
        this.canonicalizerTool = new PECanonicalizerTool(graph.getAssumptions(), graph.getOptions());
    }

    @Override
    protected void cleanupGraph(MethodScope rootMethodScope) {
        GraphUtil.normalizeLoops(graph);
        super.cleanupGraph(rootMethodScope);

        /*
         * To avoid calling tryKillUnused for each individual floating node we remove during
         * cleanup, we maintain unused nodes in a bitmap and kill them after we finish iterating the
         * new nodes in the graph.
         */
        final NodeBitMap unusedNodes = new NodeBitMap(graph);

        for (Node node : graph.getNewNodes(rootMethodScope.methodStartMark)) {
            if (node instanceof MergeNode mergeNode) {
                if (mergeNode.forwardEndCount() == 1) {
                    graph.reduceTrivialMerge(mergeNode, false, unusedNodes);
                }
            } else if (node instanceof BeginNode) {
                if (!(node.predecessor() instanceof ControlSplitNode) && node.hasNoUsages()) {
                    GraphUtil.unlinkFixedNode((AbstractBeginNode) node);
                    node.safeDelete();
                }
            } else if (GraphUtil.shouldKillUnused(node)) {
                unusedNodes.mark(node);
            }
        }

        if (unusedNodes.isNotEmpty()) {
            GraphUtil.killAllWithUnusedFloatingInputs(unusedNodes, false);
        }
    }

    @Override
    protected boolean allowLazyPhis() {
        /*
         * We do not need to exactly reproduce the encoded graph, so we want to avoid unnecessary
         * phi functions.
         */
        return true;
    }

    @Override
    protected void handleMergeNode(MergeNode merge) {
        /*
         * All inputs of non-loop phi nodes are known by now. We can infer the stamp for the phi, so
         * that parsing continues with more precise type information.
         */
        for (ValuePhiNode phi : merge.valuePhis()) {
            phi.inferStamp();
        }
    }

    @SuppressWarnings("try")
    @Override
    protected void handleFixedNode(MethodScope methodScope, LoopScope loopScope, int nodeOrderId, FixedNode node) {
        try (DebugCloseable a = CanonicalizeFixedNode.start(debug)) {
            Node canonical = canonicalizeFixedNode(methodScope, loopScope, node);
            if (canonical != node) {
                handleCanonicalization(loopScope, nodeOrderId, node, canonical);
            }
        }
    }

    /**
     * Canonicalizes the provided node, which was originally a {@link FixedNode} but can already be
     * canonicalized (and therefore be a non-fixed node).
     *
     * @param methodScope The current method.
     * @param loopScope The current loop.
     * @param originalNode The node to be canonicalized.
     */
    protected Node canonicalizeFixedNode(MethodScope methodScope, LoopScope loopScope, Node originalNode) {
        Node node = originalNode;
        if (originalNode instanceof UnsafeAccessNode) {
            /*
             * Ensure that raw stores and loads are eventually transformed to fields to make node
             * plugins trigger for them reliably during PE.
             */
            node = ((UnsafeAccessNode) node).canonical(canonicalizerTool);
        }

        /*
         * Duplicate cases for frequent classes (LoadFieldNode, LoadIndexedNode and ArrayLengthNode)
         * to improve performance (Haeubl, 2017).
         */
        if (node instanceof LoadFieldNode) {
            LoadFieldNode loadFieldNode = (LoadFieldNode) node;
            return loadFieldNode.canonical(canonicalizerTool);
        } else if (node instanceof FixedGuardNode) {
            FixedGuardNode guard = (FixedGuardNode) node;
            if (guard.getCondition() instanceof LogicConstantNode) {
                LogicConstantNode condition = (LogicConstantNode) guard.getCondition();
                if (condition.getValue() == guard.isNegated()) {
                    DeoptimizeNode deopt = new DeoptimizeNode(guard.getAction(), guard.getReason(), guard.getSpeculation());
                    if (guard.stateBefore() != null) {
                        deopt.setStateBefore(guard.stateBefore());
                    }
                    return deopt;
                } else {
                    return null;
                }
            }
            return node;
        } else if (node instanceof IfNode) {
            IfNode ifNode = (IfNode) node;
            if (ifNode.condition() instanceof LogicNegationNode) {
                ifNode.eliminateNegation();
            }
            return ifNode;
        } else if (node instanceof LoadIndexedNode) {
            LoadIndexedNode loadIndexedNode = (LoadIndexedNode) node;
            return loadIndexedNode.canonical(canonicalizerTool);
        } else if (node instanceof ArrayLengthNode) {
            ArrayLengthNode arrayLengthNode = (ArrayLengthNode) node;
            return arrayLengthNode.canonical(canonicalizerTool);
        } else if (node instanceof Canonicalizable) {
            return ((Canonicalizable) node).canonical(canonicalizerTool);
        } else {
            return node;
        }
    }

    static {
        /* Check assumption made by earlyCanonicalization about IfNode. */
        Edges edges = IfNode.TYPE.getSuccessorEdges();
        GraalError.guarantee(edges.getCount() == IfNode.SUCCESSOR_EDGES_COUNT, "%s expected to have 0 indirect successors", IfNode.class);

        /* Check assumptions made by earlyCanonicalization about IntegerSwitchNode. */
        edges = IntegerSwitchNode.TYPE.getSuccessorEdges();
        GraalError.guarantee(edges.getDirectCount() == 0, "%s expected to have 0 direct successor", IntegerSwitchNode.class);
        GraalError.guarantee(edges.getCount() == 1, "%s expected to have 0 indirect successors", IntegerSwitchNode.class);
    }

    @Override
    protected boolean earlyCanonicalization(MethodScope methodScope, LoopScope loopScope, int nodeOrderId, FixedNode node) {
        if (node instanceof IfNode ifNode && ifNode.condition() instanceof LogicConstantNode condition) {

            long survivingIndex = condition.getValue() ? IfNode.TRUE_SUCCESSOR_EDGE_INDEX : IfNode.FALSE_SUCCESSOR_EDGE_INDEX;

            /* Read the surviving successor order id. */
            long successorsByteIndex = methodScope.reader.getByteIndex();
            methodScope.reader.setByteIndex(successorsByteIndex + survivingIndex * methodScope.orderIdWidth);
            int survivingOrderId = readOrderId(methodScope);
            // Reset decode position to first byte after successors
            methodScope.reader.setByteIndex(successorsByteIndex + (IfNode.SUCCESSOR_EDGES_COUNT * methodScope.orderIdWidth));

            removeSplit(methodScope, loopScope, ifNode, survivingOrderId);
            return true;
        } else if (node instanceof IntegerSwitchNode switchNode && switchNode.value().isConstant()) {
            /*
             * Avoid spawning all successors for trivially canonicalizable switches, this ensures
             * that bytecode interpreters with huge switches do not allocate nodes that are removed
             * straight away during PE.
             */
            int value = switchNode.value().asJavaConstant().asInt();
            long survivingIndex = switchNode.successorIndexAtKey(value);

            /* Read the surviving successor order id. */
            long size = methodScope.reader.getSVInt();
            long successorsIndex = SwitchNode.SUCCESSORS_EDGE_INDEX;
            long successorsByteIndex = methodScope.reader.getByteIndex() + successorsIndex * methodScope.orderIdWidth;
            methodScope.reader.setByteIndex(successorsByteIndex + survivingIndex * methodScope.orderIdWidth);
            int survivingOrderId = readOrderId(methodScope);
            methodScope.reader.setByteIndex(successorsByteIndex + size * methodScope.orderIdWidth);

            removeSplit(methodScope, loopScope, switchNode, survivingOrderId);
            return true;
        } else {
            return false;
        }
    }

    private void removeSplit(MethodScope methodScope, LoopScope loopScope, ControlSplitNode controlSplit, int survivingOrderId) {
        if (controlSplit.predecessor() instanceof BeginNode && getNodeClass(methodScope, loopScope, survivingOrderId) == controlSplit.predecessor().getNodeClass()) {
            // Reuse the previous BeginNode but mark it in nodesToProcess so that the decoding loop
            // continues decoding.
            loopScope.nodesToProcess.set(survivingOrderId);
            registerNode(loopScope, survivingOrderId, controlSplit.predecessor(), false, false);
            assert controlSplit.hasNoUsages();
            controlSplit.clearSuccessors();
            controlSplit.replaceAtPredecessor(null);
            controlSplit.safeDelete();
        } else {
            AbstractBeginNode survivingSuccessor = (AbstractBeginNode) makeStubNode(methodScope, loopScope, survivingOrderId);
            graph.removeSplit(controlSplit, survivingSuccessor);
        }
    }

    private static Node canonicalizeFixedNodeToNull(FixedNode node) {
        /*
         * When a node is unnecessary, we must not remove it right away because there might be nodes
         * that use it as a guard input. Therefore, we replace it with a more lightweight node
         * (which is floating and has no inputs).
         */
        return new CanonicalizeToNullNode(node.stamp);
    }

    @SuppressWarnings("try")
    private void handleCanonicalization(LoopScope loopScope, int nodeOrderId, FixedNode node, Node c) {
        assert c != node : "unnecessary call";
        try (DebugCloseable position = graph.withNodeSourcePosition(node)) {
            Node canonical = c == null ? canonicalizeFixedNodeToNull(node) : c;
            if (!canonical.isAlive()) {
                assert !canonical.isDeleted();
                canonical = graph.addOrUniqueWithInputs(canonical);
                if (canonical instanceof FixedWithNextNode) {
                    graph.addBeforeFixed(node, (FixedWithNextNode) canonical);
                } else if (canonical instanceof ControlSinkNode) {
                    FixedWithNextNode predecessor = (FixedWithNextNode) node.predecessor();
                    predecessor.setNext((ControlSinkNode) canonical);
                    List<Node> successorSnapshot = node.successors().snapshot();
                    node.safeDelete();
                    for (Node successor : successorSnapshot) {
                        successor.safeDelete();
                    }
                } else if (canonical instanceof WithExceptionNode) {
                    // will be handled below
                    assert node instanceof WithExceptionNode : Assertions.errorMessage("Only WithExceptionNodes can canonicalize to WithException nodes", node, canonical);
                } else {
                    assert !(canonical instanceof FixedNode) : Assertions.errorMessageContext("canonical", canonical);
                }
            }
            if (!node.isDeleted()) {
                if (node instanceof WithExceptionNode we && canonical instanceof WithExceptionNode weCanon) {
                    graph.replaceWithExceptionSplit(we, weCanon);
                } else {
                    if (node instanceof WithExceptionNode) {
                        GraphUtil.unlinkAndKillExceptionEdge((WithExceptionNode) node);
                    } else {
                        GraphUtil.unlinkFixedNode((FixedWithNextNode) node);
                    }
                    node.replaceAtUsagesAndDelete(canonical);
                }
            }
            assert lookupNode(loopScope, nodeOrderId) == node : Assertions.errorMessage(node, loopScope, nodeOrderId);
            registerNode(loopScope, nodeOrderId, canonical, true, false);
        }
    }

    @Override
    @SuppressWarnings("try")
    protected Node handleFloatingNodeBeforeAdd(MethodScope methodScope, LoopScope loopScope, Node node) {
        if (node instanceof ValueNode) {
            ((ValueNode) node).inferStamp();
        }
        if (node instanceof Canonicalizable) {
            try (DebugCloseable context = graph.withNodeSourcePosition(node)) {
                Node canonical = ((Canonicalizable) node).canonical(canonicalizerTool);
                if (canonical == null) {
                    /*
                     * This is a possible return value of canonicalization. However, we might need
                     * to add additional usages later on for which we need a node. Therefore, we
                     * just do nothing and leave the node in place.
                     */
                } else if (canonical != node) {
                    if (!canonical.isAlive()) {
                        assert !canonical.isDeleted();
                        canonical = graph.addOrUniqueWithInputs(canonical);
                    }
                    assert node.hasNoUsages();
                    return canonical;
                }
            }
        }
        return node;
    }

    @Override
    protected Node addFloatingNode(MethodScope methodScope, LoopScope loopScope, Node node) {
        /*
         * In contrast to the base class implementation, we do not need to exactly reproduce the
         * encoded graph. Since we do canonicalization, we also want nodes to be unique.
         */
        return graph.addOrUnique(node);
    }
}
