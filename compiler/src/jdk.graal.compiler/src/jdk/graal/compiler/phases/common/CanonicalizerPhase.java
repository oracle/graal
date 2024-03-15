/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.phases.common;

import static jdk.graal.compiler.phases.common.CanonicalizerPhase.CanonicalizerFeature.CFG_SIMPLIFICATION;
import static jdk.graal.compiler.phases.common.CanonicalizerPhase.CanonicalizerFeature.DEAD_PHI_CYCLE_DETECTION;
import static jdk.graal.compiler.phases.common.CanonicalizerPhase.CanonicalizerFeature.GVN;
import static jdk.graal.compiler.phases.common.CanonicalizerPhase.CanonicalizerFeature.READ_CANONICALIZATION;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

import org.graalvm.collections.EconomicSet;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.CounterKey;
import jdk.graal.compiler.debug.DebugCloseable;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.GraalGraphError;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Graph.Mark;
import jdk.graal.compiler.graph.Graph.NodeEventListener;
import jdk.graal.compiler.graph.Graph.NodeEventScope;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.IndirectCanonicalization;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.graph.NodeFlood;
import jdk.graal.compiler.graph.NodeWorkList;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.ControlSinkNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.FixedWithNextNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.GraphState.StageFlag;
import jdk.graal.compiler.nodes.GuardNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PhiNode;
import jdk.graal.compiler.nodes.ProxyNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.VirtualState;
import jdk.graal.compiler.nodes.WithExceptionNode;
import jdk.graal.compiler.nodes.calc.FloatingNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.loop.InductionVariable;
import jdk.graal.compiler.nodes.spi.Canonicalizable;
import jdk.graal.compiler.nodes.spi.Canonicalizable.BinaryCommutative;
import jdk.graal.compiler.nodes.spi.CanonicalizerTool;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.spi.CoreProvidersDelegate;
import jdk.graal.compiler.nodes.spi.Simplifiable;
import jdk.graal.compiler.nodes.spi.SimplifierTool;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Constant;

public class CanonicalizerPhase extends BasePhase<CoreProviders> {

    public static class Options {
        // @formatter:off
        @Option(help = "Verify if the current graph state allows GVN to be performed.", type = OptionType.Debug)
        public static final OptionKey<Boolean> CanonicalizerVerifyGVNAllowed = new OptionKey<>(true);
        // @formatter:on
    }

    /**
     * Constants for types of canonicalization that can be performed.
     *
     * {@link Canonicalizable} and {@link CanonicalizerPhase} combine different optimizations into a
     * single API. This includes global value numbering, strength reductions, stamp based
     * optimizations and many more. This feature enum groups them into different categories.
     */
    public enum CanonicalizerFeature {
        /**
         * Determines if {@link CanonicalizerPhase} is allowed to canonicalize memory read
         * operations. See {@link CanonicalizerTool#canonicalizeReads()}.
         */
        READ_CANONICALIZATION,
        /**
         * Determines if the canonicalizer is allowed to change {@link ControlFlowGraph} of the
         * currently compiled method. This includes removal/deletion/insertion/etc of
         * {@link FixedNode}.
         */
        CFG_SIMPLIFICATION,
        /**
         * Determines if the canonicalizer is allowed to perform global value numbering. See
         * {@link StructuredGraph#findDuplicate(Node)} for details.
         */
        GVN,
        /**
         * Determines if the canonicalizer is allowed to perform a global graph analysis on dead
         * loop phi cycles and delete them.
         * {@link CanonicalizerPhase#isDeadLoopPhiCycle(PhiNode, NodeFlood)} for details.
         */
        DEAD_PHI_CYCLE_DETECTION;
    }

    protected static final int MAX_ITERATION_PER_NODE = 10;
    private static final CounterKey COUNTER_CANONICALIZED_NODES = DebugContext.counter("CanonicalizedNodes");
    private static final CounterKey COUNTER_PROCESSED_NODES = DebugContext.counter("ProcessedNodes");
    private static final CounterKey COUNTER_CANONICALIZATION_CONSIDERED_NODES = DebugContext.counter("CanonicalizationConsideredNodes");
    private static final CounterKey COUNTER_INFER_STAMP_CALLED = DebugContext.counter("InferStampCalled");
    private static final CounterKey COUNTER_STAMP_CHANGED = DebugContext.counter("StampChanged");
    private static final CounterKey COUNTER_SIMPLIFICATION_CONSIDERED_NODES = DebugContext.counter("SimplificationConsideredNodes");
    private static final CounterKey COUNTER_CUSTOM_SIMPLIFICATION_CONSIDERED_NODES = DebugContext.counter("CustomSimplificationConsideredNodes");

    protected final EnumSet<CanonicalizerFeature> features;
    protected final CustomSimplification customSimplification;

    public interface CustomSimplification {
        /**
         * @param node the node to be simplified
         * @param tool utility available during the simplification process
         */
        void simplify(Node node, SimplifierTool tool);
    }

    protected CanonicalizerPhase(EnumSet<CanonicalizerFeature> features) {
        this(null, features);
    }

    protected CanonicalizerPhase() {
        this(null, defaultFeatures());
    }

    protected CanonicalizerPhase(CustomSimplification customSimplification) {
        this(customSimplification, defaultFeatures());
    }

    protected CanonicalizerPhase(CustomSimplification customSimplification, EnumSet<CanonicalizerFeature> features) {
        this.customSimplification = customSimplification;
        this.features = features;
    }

    public EnumSet<CanonicalizerFeature> getFeatures() {
        return features;
    }

    public CanonicalizerPhase copyWithCustomSimplification(CustomSimplification newSimplification) {
        return new CanonicalizerPhase(newSimplification, features);
    }

    public CanonicalizerPhase copyWithoutGVN() {
        EnumSet<CanonicalizerFeature> newFeatures = EnumSet.copyOf(features);
        newFeatures.remove(GVN);
        return new CanonicalizerPhase(customSimplification, newFeatures);
    }

    public CanonicalizerPhase copyWithoutDeadPhiCycleDetection() {
        EnumSet<CanonicalizerFeature> newFeatures = EnumSet.copyOf(features);
        newFeatures.remove(DEAD_PHI_CYCLE_DETECTION);
        return new CanonicalizerPhase(customSimplification, newFeatures);
    }

    public CanonicalizerPhase copyWithoutSimplification() {
        EnumSet<CanonicalizerFeature> newFeatures = EnumSet.copyOf(features);
        newFeatures.remove(CFG_SIMPLIFICATION);
        return new CanonicalizerPhase(customSimplification, newFeatures);
    }

    public static CanonicalizerPhase create() {
        return new CanonicalizerPhase(null, defaultFeatures());
    }

    public static CanonicalizerPhase createWithoutReadCanonicalization() {
        return new CanonicalizerPhase(defaultFeaturesWithout(READ_CANONICALIZATION));
    }

    public static CanonicalizerPhase createWithoutCFGSimplification() {
        return new CanonicalizerPhase(defaultFeaturesWithout(CFG_SIMPLIFICATION));
    }

    private static EnumSet<CanonicalizerFeature> defaultFeatures() {
        return EnumSet.allOf(CanonicalizerFeature.class);
    }

    private static EnumSet<CanonicalizerFeature> defaultFeaturesWithout(CanonicalizerFeature feature) {
        EnumSet<CanonicalizerFeature> features = defaultFeatures();
        features.remove(feature);
        return features;
    }

    @Override
    public boolean checkContract() {
        /*
         * There are certain canonicalizations we make that heavily increase code size by e.g.
         * replacing a merge followed by a return of the merge's phi with returns in each
         * predecessor.
         */
        return false;
    }

    protected boolean isFinalCanonicalizationPhase() {
        return false;
    }

    @Override
    public boolean mustApply(GraphState graphState) {
        return graphState.requiresFutureStage(StageFlag.CANONICALIZATION) || super.mustApply(graphState);
    }

    @Override
    public Optional<NotApplicable> notApplicableTo(GraphState graphState) {
        return NotApplicable.ifAny(
                        NotApplicable.unlessRunBefore(this, StageFlag.FINAL_CANONICALIZATION, graphState),
                        NotApplicable.unlessRunBefore(this, StageFlag.REMOVE_OPAQUE_VALUES, graphState));
    }

    @Override
    protected void run(StructuredGraph graph, CoreProviders context) {
        processWorkSet(graph, new Tool(graph, context, graph.createIterativeNodeWorkList(true, MAX_ITERATION_PER_NODE)));
    }

    @Override
    public void updateGraphState(GraphState graphState) {
        super.updateGraphState(graphState);
        graphState.removeRequirementToStage(StageFlag.CANONICALIZATION);
    }

    /**
     * @param newNodesMark only the {@linkplain Graph#getNewNodes(Mark) new nodes} specified by this
     *            mark are processed
     */
    public void applyIncremental(StructuredGraph graph, CoreProviders context, Mark newNodesMark) {
        new IncrementalCanonicalizerPhase(features, customSimplification, graph, context, newNodesMark).apply(graph, context);
    }

    /**
     * @param workingSet the initial working set of nodes on which the canonicalizer works, should
     *            be an auto-grow node bitmap
     */
    public void applyIncremental(StructuredGraph graph, CoreProviders context, Iterable<? extends Node> workingSet) {
        new IncrementalCanonicalizerPhase(features, customSimplification, graph, context, workingSet).apply(graph, context);
    }

    private static NodeView getNodeView() {
        return NodeView.DEFAULT;
    }

    @Override
    public int hashCode() {
        if (customSimplification == null) {
            return Objects.hash(this.getClass().getName(), features.toString());
        }

        return Objects.hash(this.getClass().getName(), features.toString(),
                        customSimplification.getClass().getName());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof CanonicalizerPhase)) {
            return false;
        }

        CanonicalizerPhase that = (CanonicalizerPhase) obj;

        if (!this.getClass().equals(that.getClass()) || !this.features.equals(that.features)) {
            return false;
        }

        if (this.customSimplification == null) {
            return that.customSimplification == null;
        } else if (that.customSimplification == null) {
            return false;
        }

        return this.customSimplification.getClass().equals(that.customSimplification.getClass());
    }

    @SuppressWarnings("try")
    protected int processWorkSet(StructuredGraph graph, Tool tool) {
        int sum = 0;
        NodeEventListener listener = new NodeEventListener() {

            @Override
            public void nodeAdded(Node node) {
                tool.workList.add(node);
            }

            @Override
            public void inputChanged(Node node) {
                tool.workList.add(node);
                if (node instanceof IndirectCanonicalization) {
                    for (Node usage : node.usages()) {
                        tool.workList.add(usage);
                    }
                }

                if (node instanceof AbstractBeginNode) {
                    AbstractBeginNode abstractBeginNode = (AbstractBeginNode) node;
                    if (abstractBeginNode.predecessor() != null) {
                        tool.workList.add(abstractBeginNode.predecessor());
                    }
                }
            }

            @Override
            public void usagesDroppedToZero(Node node) {
                tool.workList.add(node);
            }
        };

        try (NodeEventScope nes = graph.trackNodeEvents(listener)) {
            for (Node n : tool.workList) {
                processNode(n, tool);
                ++sum;
            }
        }

        EconomicSet<PhiNode> phiPostProcessingWorkList = null;
        if (features.contains(DEAD_PHI_CYCLE_DETECTION) && graph.hasLoops()) {
            for (LoopBeginNode lb : graph.getNodes(LoopBeginNode.TYPE)) {
                for (PhiNode n : lb.phis()) {
                    if (tool.allUsagesAvailable() && n.isAlive()) {
                        if (phiPostProcessingWorkList == null) {
                            phiPostProcessingWorkList = EconomicSet.create();
                        }
                        phiPostProcessingWorkList.add(n);
                    }
                }
            }
        }

        if (tool.allUsagesAvailable() && phiPostProcessingWorkList != null) {
            for (PhiNode phi : phiPostProcessingWorkList) {
                if (!phi.isAlive()) {
                    continue;
                }
                NodeFlood nf = graph.createNodeFlood();
                if (isDeadLoopPhiCycle(phi, nf)) {
                    graph.getDebug().dump(DebugContext.VERY_DETAILED_LEVEL, graph, "Found dead phi cycle %s keeps alive -> %s", phi, nf.getVisited());
                    for (Node visitedNode : nf.getVisited()) {
                        /**
                         * Inside the visited usages there can be other phis which themselves are
                         * dead (and so on). In order to clean them up in a proper way we need to
                         * visit all of them individually.
                         */
                        if (visitedNode instanceof PhiNode) {
                            PhiNode visitedPhi = (PhiNode) visitedNode;
                            /*
                             * Ideally we would just replace the phi with null at all usages and
                             * kill with floating inputs, however incremental canonicalization does
                             * not guarantee that all floating nodes that are dead themselves
                             * hanging off the phi are deleted already. Thus, we have to first kill
                             * them and process the rest as usual.
                             */
                            for (Node visited : nf.getVisited()) {
                                if (visited.isAlive() && visited.hasNoUsages()) {
                                    GraphUtil.tryKillUnused(visited);
                                }
                            }
                            if (visitedPhi.isAlive()) {
                                visitedPhi.replaceAtUsages(null);
                                GraphUtil.killWithUnusedFloatingInputs(visitedPhi, true);
                            }
                        }
                    }
                }
            }
        }
        return sum;

    }

    /**
     * Determine if the given {@link PhiNode} is the root of a dead (no real usages outside the
     * cycle) phi cycle. Loop phis ({@link PhiNode#isLoopPhi()}) often form cyclic chains of
     * floating nodes due to heavy usage of {@link InductionVariable} in loop code.
     *
     * Dead phi cycles are often the result of inlining, conditional elimination and partial escape
     * analysis.
     *
     * Cycle detection is a best-effort operation: we do not want to spend a lot of compile time
     * cleaning up extremely complex or long dead cycles (they will be handled by the scheduling
     * phase).
     *
     * Consider the following intended complex example
     *
     * <pre>
     * int phi = 0;
     * while (true) {
     *     if (sth) {
     *         code();
     *         phi = 12; // constant
     *     } else if (sthElse) {
     *         moreCode();
     *         phi = phi - 125; // sub node usage
     *     } else {
     *         evenMoreCode();
     *         phi = phi + 127; // add node usage
     *     }
     * }
     * </pre>
     *
     * The cycle detection will start by processing the node associated with {@code phi} and process
     * its usages. Note that the phi itself has multiple usages even though it is still dead. The
     * detection will follow all usages of marked nodes which means at the end the nodes visited
     * will be {@code phi, constant(12), sub, constant(125), add, constant(127)}. Since no node
     * visited in between has other usages except one (so no outside usages) and no fixed node
     * usages we can conclude we found a dead phi cycle that can be removed.
     */
    public static boolean isDeadLoopPhiCycle(PhiNode thisPhi, NodeFlood nf) {
        GraalError.guarantee(thisPhi.isLoopPhi(), "Must only process loop phis");
        nf.add(thisPhi);
        int steps = 0;
        for (Node flooded : nf) {
            // constants are always marked as visited but we never visit their usages, they are leaf
            // nodes and can appear everywhere
            if (flooded instanceof ConstantNode) {
                continue;
            }
            if (steps++ >= DEAD_PHI_CYCLE_STEPS) {
                // too much effort, abort
                return false;
            }
            for (Node usage : flooded.usages()) {
                if (usage instanceof GuardNode) {
                    // guards are the only floating nodes that are not removed if their usages drop
                    // to 0
                    return false;
                }
                if (!GraphUtil.isFloatingNode(usage)) {
                    // Fixed node usage: can never have a dead cycle
                    return false;
                }
                if (usage instanceof VirtualState || usage instanceof ProxyNode) {
                    // usages that still require the (potential) dead cycle to be alive
                    return false;
                }
                if (!nf.isMarked(usage)) {
                    nf.add(usage);
                }
            }
        }
        // we managed to find a dead cycle
        return true;
    }

    /**
     * Number of loop iterations to perform in dead phi cycle detection before giving up for compile
     * time reasons (the scheduler will consider the rest). This is a very high upper bound that
     * should never be reached.
     */
    private static final int DEAD_PHI_CYCLE_STEPS = 128;

    /**
     * @return true if the graph was changed.
     */
    private boolean processNode(Node node, Tool tool) {
        if (!node.isAlive()) {
            return false;
        }
        COUNTER_PROCESSED_NODES.increment(tool.debug);
        StructuredGraph graph = (StructuredGraph) node.graph();
        if (GraphUtil.tryKillUnused(node)) {
            graph.getOptimizationLog().report(DebugContext.VERY_DETAILED_LEVEL, CanonicalizerPhase.class, "UnusedNodeRemoval", node);
            return true;
        }
        NodeClass<?> nodeClass = node.getNodeClass();
        if (tryCanonicalize(node, nodeClass, tool)) {
            return true;
        }
        if (node instanceof ValueNode) {
            ValueNode valueNode = (ValueNode) node;
            boolean improvedStamp = tryInferStamp(valueNode, tool);
            Constant constant = valueNode.stamp(NodeView.DEFAULT).asConstant();
            if (constant != null && !(node instanceof ConstantNode)) {
                ConstantNode stampConstant = ConstantNode.forConstant(valueNode.stamp(NodeView.DEFAULT), constant, tool.context.getMetaAccess(), graph);
                valueNode.replaceAtUsages(stampConstant, InputType.Value);
                GraphUtil.tryKillUnused(valueNode);
                graph.getOptimizationLog().report(CanonicalizerPhase.class, "ConstantStampReplacement", valueNode);
                return true;
            } else if (improvedStamp) {
                // the improved stamp may enable additional canonicalization
                if (tryCanonicalize(valueNode, nodeClass, tool)) {
                    return true;
                }
                tool.workList.addAll(valueNode.usages());
            }
        }
        // Perform GVN after possibly inferring the stamp since a stale stamp will inhibit GVN.
        if (features.contains(GVN) && tryGlobalValueNumbering(node, nodeClass)) {
            return true;
        }
        return false;
    }

    public static boolean gvn(Node node, NodeClass<?> nodeClass) {
        if (nodeClass.valueNumberable()) {
            Node newNode = node.graph().findDuplicate(node);
            if (newNode != null) {
                assert !(node instanceof FixedNode || newNode instanceof FixedNode) : Assertions.errorMessageContext("node", node, "newNode", newNode);
                node.replaceAtUsagesAndDelete(newNode);
                StructuredGraph graph = (StructuredGraph) node.graph();
                graph.getOptimizationLog().report(CanonicalizerPhase.class, "GlobalValueNumbering", node);
                return true;
            }
        }
        return false;
    }

    public boolean tryGlobalValueNumbering(Node node, NodeClass<?> nodeClass) {
        if (Options.CanonicalizerVerifyGVNAllowed.getValue(node.getOptions())) {
            assert ((StructuredGraph) node.graph()).getGraphState().isBeforeStage(
                            StageFlag.PARTIAL_REDUNDANCY_SCHEDULE) : "GVN must not occur after expanding partially redundant nodes, trying to gvn " + node + " for graph " +
                                            node.graph();
        }
        return gvn(node, nodeClass);
    }

    private static AutoCloseable getCanonicalizeableContractAssertion(Node node) {
        boolean needsAssertion = false;
        assert (needsAssertion = true) == true;
        if (needsAssertion) {
            Mark mark = node.graph().getMark();
            return () -> {
                assert mark.equals(node.graph().getMark()) : "new node created while canonicalizing " + node.getClass().getSimpleName() + " " + node + ": " +
                                node.graph().getNewNodes(mark).snapshot();
            };
        } else {
            return null;
        }
    }

    @SuppressWarnings("try")
    public boolean tryCanonicalize(final Node node, NodeClass<?> nodeClass, Tool tool) {
        try (DebugCloseable position = node.withNodeSourcePosition(); DebugContext.Scope scope = tool.debug.withContext(node)) {
            if (nodeClass.isCanonicalizable()) {
                COUNTER_CANONICALIZATION_CONSIDERED_NODES.increment(tool.debug);
                Node canonical = node;
                try (AutoCloseable verify = getCanonicalizeableContractAssertion(node)) {
                    canonical = ((Canonicalizable) node).canonical(tool);
                    if (canonical == node && nodeClass.isCommutative()) {
                        canonical = ((BinaryCommutative<?>) node).maybeCommuteInputs();
                    }
                } catch (Throwable e) {
                    throw new GraalGraphError(e).addContext(node);
                }
                if (performReplacement(node, canonical, tool)) {
                    Node finalCanonical = canonical;
                    StructuredGraph graph = (StructuredGraph) node.graph();
                    graph.getOptimizationLog().withLazyProperty("replacedNodeClass", nodeClass::shortName).withLazyProperty("canonicalNodeClass",
                                    () -> (finalCanonical == null) ? null : finalCanonical.getNodeClass().shortName()).report(DebugContext.VERY_DETAILED_LEVEL, CanonicalizerPhase.class,
                                                    "CanonicalReplacement", node);
                    return true;
                }
            }

            if (features.contains(CFG_SIMPLIFICATION)) {
                if (customSimplification != null) {
                    tool.debug.log(DebugContext.VERBOSE_LEVEL, "Canonicalizer: customSimplification simplifying %s", node);
                    COUNTER_CUSTOM_SIMPLIFICATION_CONSIDERED_NODES.increment(tool.debug);

                    int modCount = node.graph().getEdgeModificationCount();
                    customSimplification.simplify(node, tool);
                    if (node.isDeleted() || modCount != node.graph().getEdgeModificationCount()) {
                        StructuredGraph graph = (StructuredGraph) node.graph();
                        graph.getOptimizationLog().report(DebugContext.VERY_DETAILED_LEVEL, CanonicalizerPhase.class, "CfgSimplificationCustom", node);
                        return true;
                    }
                }
                if (nodeClass.isSimplifiable()) {
                    tool.debug.log(DebugContext.VERBOSE_LEVEL, "Canonicalizer: simplifying %s", node);
                    COUNTER_SIMPLIFICATION_CONSIDERED_NODES.increment(tool.debug);

                    int modCount = node.graph().getEdgeModificationCount();
                    ((Simplifiable) node).simplify(tool);
                    if (node.isDeleted() || modCount != node.graph().getEdgeModificationCount()) {
                        StructuredGraph graph = (StructuredGraph) node.graph();
                        graph.getOptimizationLog().report(DebugContext.VERY_DETAILED_LEVEL, CanonicalizerPhase.class, "CfgSimplification", node);
                        return true;
                    }
                }
            }
            return false;
        } catch (Throwable throwable) {
            throw tool.debug.handle(throwable);
        }
    }

    // @formatter:off
        //     cases:                                           original node:
        //                                         |Floating|Fixed-unconnected|Fixed-connected|
        //                                         --------------------------------------------
        //                                     null|   1    |        X        |       3       |
        //                                         --------------------------------------------
        //                                 Floating|   2    |        X        |       4       |
        //       canonical node:                   --------------------------------------------
        //                        Fixed-unconnected|   X    |        X        |       5       |
        //                                         --------------------------------------------
        //                          Fixed-connected|   2    |        X        |       6       |
        //                                         --------------------------------------------
        //                              ControlSink|   X    |        X        |       7       |
        //                                         --------------------------------------------
        //       X: must not happen (checked with assertions)
        // @formatter:on
    private static boolean performReplacement(final Node node, Node newCanonical, Tool tool) {
        if (newCanonical == node) {
            tool.debug.log(DebugContext.VERBOSE_LEVEL, "Canonicalizer: work on %1s", node);
            return false;
        } else {
            Node canonical = newCanonical;
            tool.debug.log(DebugContext.VERBOSE_LEVEL, "Canonicalizer: replacing %1s with %1s", node, canonical);
            COUNTER_CANONICALIZED_NODES.increment(tool.debug);
            StructuredGraph graph = (StructuredGraph) node.graph();
            if (canonical != null && !canonical.isAlive()) {
                assert !canonical.isDeleted();
                canonical = graph.addOrUniqueWithInputs(canonical);
            }
            if (node instanceof FloatingNode) {
                assert canonical == null || !(canonical instanceof FixedNode) ||
                                (canonical.predecessor() != null || canonical instanceof StartNode || canonical instanceof AbstractMergeNode) : node + " -> " + canonical +
                                                " : replacement should be floating or fixed and connected";
                node.replaceAtUsages(canonical);
                GraphUtil.killWithUnusedFloatingInputs(node, true);
            } else {
                assert node instanceof FixedNode && node.predecessor() != null : node + " -> " + canonical + " : node should be fixed & connected (" + node.predecessor() + ")";
                FixedNode fixed = (FixedNode) node;
                if (canonical instanceof ControlSinkNode) {
                    // case 7
                    fixed.replaceAtPredecessor(canonical);
                    GraphUtil.killCFG(fixed);
                    return true;

                } else if (fixed instanceof WithExceptionNode) {
                    /*
                     * A fixed node with an exception edge is handled similarly to a
                     * FixedWithNextNode. The only difference is that the exception edge needs to be
                     * killed as part of canonicalization (unless the canonical node is a new
                     * WithExceptionNode too).
                     */
                    WithExceptionNode withException = (WithExceptionNode) fixed;

                    // When removing a fixed node, new canonicalization
                    // opportunities for its successor may arise
                    assert withException.next() != null;
                    tool.addToWorkList(withException.next());
                    if (canonical == null) {
                        // case 3
                        node.replaceAtUsages(null);
                        GraphUtil.unlinkAndKillExceptionEdge(withException);
                        GraphUtil.killWithUnusedFloatingInputs(withException);
                    } else if (canonical instanceof FloatingNode floating) {
                        // case 4
                        /*
                         * In corner cases it is possible for the killing of the exception edge to
                         * trigger the killing of the replacement node. We therefore wait to kill
                         * the exception edge until after replacing the WithException node.
                         */
                        var exceptionEdge = withException.exceptionEdge();
                        withException.setExceptionEdge(null);
                        graph.replaceSplitWithFloating(withException, floating, withException.next());
                        if (exceptionEdge != null) {
                            GraphUtil.killCFG(exceptionEdge);
                        }
                    } else {
                        assert canonical instanceof FixedNode : Assertions.errorMessage(canonical);
                        if (canonical.predecessor() == null) {
                            assert !canonical.cfgSuccessors().iterator().hasNext() : "replacement " + canonical + " shouldn't have successors";
                            // case 5
                            if (canonical instanceof WithExceptionNode) {
                                graph.replaceWithExceptionSplit(withException, (WithExceptionNode) canonical);
                            } else {
                                withException.killExceptionEdge();
                                graph.replaceSplitWithFixed(withException, (FixedWithNextNode) canonical, withException.next());
                            }
                        } else {
                            assert canonical.cfgSuccessors().iterator().hasNext() : "replacement " + canonical + " should have successors";
                            // case 6
                            node.replaceAtUsages(canonical);
                            GraphUtil.unlinkAndKillExceptionEdge(withException);
                            GraphUtil.killWithUnusedFloatingInputs(withException);
                        }
                    }

                } else {
                    assert fixed instanceof FixedWithNextNode : Assertions.errorMessage(fixed);
                    FixedWithNextNode fixedWithNext = (FixedWithNextNode) fixed;
                    // When removing a fixed node, new canonicalization
                    // opportunities for its successor may arise
                    assert fixedWithNext.next() != null;
                    tool.addToWorkList(fixedWithNext.next());
                    if (canonical == null) {
                        // case 3
                        node.replaceAtUsages(null);
                        GraphUtil.removeFixedWithUnusedInputs(fixedWithNext);
                    } else if (canonical instanceof FloatingNode) {
                        // case 4
                        graph.replaceFixedWithFloating(fixedWithNext, (FloatingNode) canonical);
                    } else {
                        assert canonical instanceof FixedNode : Assertions.errorMessage(canonical);
                        if (canonical.predecessor() == null) {
                            assert !canonical.cfgSuccessors().iterator().hasNext() : "replacement " + canonical + " shouldn't have successors";
                            // case 5
                            graph.replaceFixedWithFixed(fixedWithNext, (FixedWithNextNode) canonical);
                        } else {
                            assert canonical.cfgSuccessors().iterator().hasNext() : "replacement " + canonical + " should have successors";
                            // case 6
                            node.replaceAtUsages(canonical);
                            GraphUtil.removeFixedWithUnusedInputs(fixedWithNext);
                        }
                    }
                }
            }
            return true;
        }
    }

    /**
     * Calls {@link ValueNode#inferStamp()} on the node and, if it returns true (which means that
     * the stamp has changed), re-queues the node's usages. If the stamp has changed then this
     * method also checks if the stamp now describes a constant integer value, in which case the
     * node is replaced with a constant.
     */
    private static boolean tryInferStamp(ValueNode node, Tool tool) {
        if (node.isAlive()) {
            COUNTER_INFER_STAMP_CALLED.increment(tool.debug);
            if (node.inferStamp()) {
                COUNTER_STAMP_CHANGED.increment(tool.debug);
                for (Node usage : node.usages()) {
                    tool.workList.add(usage);
                }
                return true;
            }
        }
        return false;
    }

    protected final class Tool extends CoreProvidersDelegate implements SimplifierTool, NodeView {

        private final Assumptions assumptions;
        private final OptionValues options;
        private final CoreProviders context;
        private final NodeWorkList workList;
        private final NodeView nodeView;
        private final DebugContext debug;

        Tool(StructuredGraph graph, CoreProviders context, NodeWorkList workList) {
            super(context);
            this.debug = graph.getDebug();
            this.assumptions = graph.getAssumptions();
            this.options = graph.getOptions();
            this.nodeView = getNodeView();
            this.context = context;
            this.workList = workList;
        }

        @Override
        public void deleteBranch(Node branch) {
            FixedNode fixedBranch = (FixedNode) branch;
            fixedBranch.predecessor().replaceFirstSuccessor(fixedBranch, null);
            GraphUtil.killCFG(fixedBranch);
        }

        @Override
        public void addToWorkList(Node node) {
            workList.add(node);
        }

        @Override
        public void addToWorkList(Iterable<? extends Node> nodes) {
            workList.addAll(nodes);
        }

        @Override
        public void removeIfUnused(Node node) {
            GraphUtil.tryKillUnused(node);
        }

        @Override
        public boolean canonicalizeReads() {
            return features.contains(READ_CANONICALIZATION);
        }

        @Override
        public boolean finalCanonicalization() {
            return isFinalCanonicalizationPhase();
        }

        @Override
        public boolean allUsagesAvailable() {
            return true;
        }

        @Override
        public boolean trySinkWriteFences() {
            return context.getLowerer().writesStronglyOrdered();
        }

        @Override
        public Assumptions getAssumptions() {
            return assumptions;
        }

        @Override
        public Integer smallestCompareWidth() {
            return context.getLowerer().smallestCompareWidth();
        }

        @Override
        public boolean supportsRounding() {
            return context.getLowerer().supportsRounding();
        }

        @Override
        public OptionValues getOptions() {
            return options;
        }

        @Override
        public Stamp stamp(ValueNode node) {
            return nodeView.stamp(node);
        }

        @Override
        public boolean divisionOverflowIsJVMSCompliant() {
            return context.getLowerer().divisionOverflowIsJVMSCompliant();
        }

    }

    public boolean getCanonicalizeReads() {
        return features.contains(READ_CANONICALIZATION);
    }

}
