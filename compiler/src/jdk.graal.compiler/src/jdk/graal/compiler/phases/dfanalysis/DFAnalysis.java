/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.graal.compiler.phases.dfanalysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.EconomicSet;
import org.graalvm.collections.Pair;

import jdk.graal.compiler.core.common.util.CompilationAlarm;
import jdk.graal.compiler.core.common.util.IntList;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Position;
import jdk.graal.compiler.graph.iterators.NodePredicate;
import jdk.graal.compiler.nodeinfo.InputType;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.AbstractMergeNode;
import jdk.graal.compiler.nodes.ControlSplitNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.ValuePhiNode;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraph;
import jdk.graal.compiler.nodes.cfg.ControlFlowGraphBuilder;
import jdk.graal.compiler.nodes.cfg.HIRBlock;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.dfanalysis.DFEdgeMap.CFGEdge;
import jdk.graal.compiler.phases.dfanalysis.DFEdgeMap.Reachability;

/**
 * <p>
 * This class is a framework to be used for control flow sensitive optimistic data flow analysis.
 * This framework applies a fixed point algorithm described in
 * <a href="https://dl.acm.org/doi/10.1145/3679007.3685059">Lazy Sparse Conditional Constant
 * Propagation in the Sea of Nodes</a> by Chistoph Aigner, Gerg&ouml; Barany, and Hanspeter
 * M&ouml;ssenb&ouml;ck, using a given analysis domain that takes the shape of a
 * <a href="https://en.wikipedia.org/wiki/Lattice_(order)">complete lattice</a>. It tracks abstract
 * domain values for nodes in the graph as well as information about reachability of control flow
 * edges. The algorithm is designed in a way that it does not require a full schedule of the graph.
 * Additionally only iterates over portions of the graph that may contribute to new values being
 * discovered, a property we call "lazy iteration".
 * </p>
 * <p>
 * Generally, this analysis iterates over the graph in an approximation of a top-down order
 * (resembling reverse-post-order traversal). This analysis is "lazy" in the sense that it updates
 * values that lie above the depth that the analysis has reached <i>implicitly</i> to an
 * unrestricted value.
 * </p>
 * <p>
 * This framework is "optimistic" in the sense that the initial assumption is that the entire graph
 * is unreachable and all values are impossible values. This obviously too strict assumption is then
 * gradually weakened until a fixed point is reached. The opposite direction, starting with the
 * overapproximation that any value is possible and all branches are reachable, and
 * "pessimistically" narrowing the assumption until reaching a fixed point would be a "pessimistic"
 * analysis. An example of a constant that can be found only with optimistic analysis is the
 * following loop:
 * </p>
 *
 * <pre>
 * int x = 1;
 * do {
 *     if (x != 1) {
 *         x = 2;
 *     }
 * } while (cnt-- > 0);
 * </pre>
 *
 * <p>
 * Here, detecting 'x' as constant 1 requires the knowledge that 'x = 2' is unreachable. But
 * detecting this, in turn, requires the knowledge that 'x' is constant 1. Initially optimistically
 * assuming 'x = 2' to be unreachable and afterward checking the assumption allows us to break this
 * cycle and detect 'x' to be constant.
 * </p>
 * <p>
 * The analysis domains for this framework must be a complete lattice with the unrestricted element
 * representing all possible values. The {@link AnalysisDomainDefinition} interface provides a
 * skeleton of methods used to represent such a complete lattice for use in this framework. The
 * partial order of a lattice defined using this interface is defined in
 * {@link AnalysisDomainDefinition#isWeakerThan}. If a value is "weaker than" another, it means that
 * the weaker value is less specific than the stronger value. The strongest value in this lattice is
 * an "unevaluated" value, meaning, the analysis has not reached this node yet. The weakest value in
 * this lattice is "unrestricted", representing the fact that the analysis can not infer any
 * information about the given node and its output could be any concrete value. The algorithm in
 * this framework starts all nodes on "unevaluated" and gradually computes weaker values for each
 * node until a fixed point is reached.
 * </p>
 * <p>
 * The transfer function ({@link AnalysisDomainDefinition#transfer} must satisfy monotonicity. This
 * means, interpreting unevaluated values as unrestricted, given increasingly more general (weaker)
 * inputs, the result of the transfer function must be weaker or equal to the result given more
 * precise (stronger) inputs (i.e. given inputs
 * {@code a <= x & b <= y: transferForAdd(a, b) <= transferForAdd(x, y)}). Furthermore, the transfer
 * function must never return UNEVALUATED.
 * </p>
 * <p>
 * This analysis is capable of inferring additional information based on control flow by
 * approximating a schedule for floating nodes to attribute them to a specific control flow branch
 * (see {@link AnalysisInferenceHelper}). This information is inserted into the graph in the form of
 * short-lived {@link InferredFactNode}s which work in a very similar fashion to
 * {@link jdk.graal.compiler.nodes.PiNode}s. These nodes must be removed from the graph after using
 * the analysis result by calling {@link DFAnalysis#cleanup}.
 * </p>
 * <p>
 * As an optimization, this framework prevents too eager propagation of temporary values occurring
 * during the evaluation of loops to nodes below the loop by delaying the evaluation of
 * {@link jdk.graal.compiler.nodes.ValueProxyNode}s until its loop has been fully evaluated (this is
 * done by scheduling value proxies with priority in {@link WorkList#schedule(ValueNode)}). Analysis
 * without value proxies may be slower and is untested. The framework checks if the graph has
 * undergone value proxy removal and will abort if it is run after value proxy removal.
 * </p>
 * <p>
 * The analysis domain is intended to be passed as a stateless instance of
 * {@link AnalysisDomainDefinition} using the same type parameter as the instance of this analysis.
 * </p>
 *
 * @param <T> Type of the analysis domain element used for this analysis.
 */
public final class DFAnalysis<T> {

    public static final class Options {
        // @formatter:off
        @Option(help = "Records a trace for each node in the element map", type = OptionType.Debug)
        public static final OptionKey<Boolean> DFA_RecordTrace = new OptionKey<>(false);

        /**
         * Shows warnings if the analysis detects inferred facts that should be unreachable but
         * according to the information retrieved from the analysis domain can not safely be
         * considered to be unreachable.
         * <br/>
         * A value of {@code 0} disables warnings, a value of {@code 1} only prints to TTY,
         * a value of {@code 2} also dumps a graph when printing a warning.
         * <br/>
         * Enabling this option is <b>highly</b> recommended during the development of an
         * analysis.
         */
        @Option(help = "Prints a warning if an inferred fact is encountered that should be unreachable", type = OptionType.Debug)
        public static final OptionKey<Integer> DFA_WarnUnreachable = new OptionKey<>(0);

        @Option(help = "Allows the analysis framework to infer information from conditions of control flow branches", type = OptionType.Debug)
        public static final OptionKey<Boolean> DFA_AllowInferences = new OptionKey<>(true);

        @Option(help = "Runs a full Canonicalizer before applying the Pentagonal Analysis Phase", type = OptionType.Debug)
        public static final OptionKey<Boolean> DFA_PreCanonicalize = new OptionKey<>(false);

        @Option(help = "Runs the Analysis on all compilation units, not only units with loops", type = OptionType.Debug)
        public static final OptionKey<Boolean> DFA_EvalAll = new OptionKey<>(false);
    }

    /**
     * Most control splits are binary, therefore we preallocate FALSE_FALSE to use in the case of an
     * unreachable binary control split node.
     */
    private static final boolean[] FALSE_FALSE = new boolean[]{false, false};
    /**
     * We also preallocate an array of true values for binary splits unknown to th given analysis.
     */
    private static final boolean[] TRUE_TRUE = new boolean[]{true, true};
    final Class<T> elementType;
    final StructuredGraph graph;
    final DebugContext debug;
    final ControlFlowGraph cfg;
    final WorkList workList;
    final DFAMap<T> elemMap;
    final DFEdgeMap<T> edgeMap;
    final AnalysisDomainDefinition<T> domain;
    /* used in AnalysisInferenceHelper#generateInferredFacts */
    final EconomicMap<LogicNode, AbstractBeginNode[][]> logicBranchCache;
    final EconomicMap<ValueNode, EconomicSet<Pair<ValueNode, AbstractBeginNode>>> attemptedInferences;
    final EconomicMap<Node, HIRBlock> dominanceCache;
    final ArrayList<InferredFactNode<T>> inferences;
    final EconomicMap<ValueNode, Integer> nodesWithUnevaluatedInputs;
    private final EconomicMap<LoopBeginNode, Integer> loopBeginEvalCnt;
    private boolean expended;

    private DFAnalysis(Class<T> elementType, StructuredGraph graph, ControlFlowGraph cfg, AnalysisDomainDefinition<T> domain) {
        this.elementType = elementType;
        this.graph = graph;
        debug = graph.getDebug();
        this.cfg = cfg;
        workList = new WorkList(cfg, debug);
        elemMap = new DFAMap<>(elementType, this, Options.DFA_RecordTrace.getValue(graph.getOptions()));
        edgeMap = new DFEdgeMap<>(this, domain);
        this.domain = domain;
        logicBranchCache = EconomicMap.create();
        attemptedInferences = EconomicMap.create();
        dominanceCache = EconomicMap.create();
        inferences = new ArrayList<>();
        nodesWithUnevaluatedInputs = EconomicMap.create();
        loopBeginEvalCnt = EconomicMap.create();
        expended = false;
    }

    public static <T> DFAnalysis<T> create(Class<T> elementType, StructuredGraph graph, AnalysisDomainDefinition<T> analysisDefinition,
                    NodePredicate isStartingPoint) {
        ControlFlowGraph cfg = new ControlFlowGraphBuilder(graph).connectBlocks(true).computeDominators(true).build();
        DFAnalysis<T> analysis = new DFAnalysis<>(elementType, graph, cfg, analysisDefinition);
        analysis.workList.initialize(graph, isStartingPoint);
        return analysis;
    }

    static boolean isLastNodeInBlock(ControlFlowGraph cfg, FixedNode node) {
        return cfg.blockFor(node).getEndNode().equals(node);
    }

    static boolean isMemoryUsage(ValueNode value, ValueNode usage) {
        for (Position pos : usage.inputPositions()) {
            if (pos.getInputType() == InputType.Memory && pos.get(usage) == value) {
                // this is indeed a memory edge
                return true;
            }
        }
        return false;
    }

    public static Optional<BasePhase.NotApplicable> notApplicableTo(BasePhase<?> phase, GraphState graphState) {
        return BasePhase.NotApplicable.unlessRunBefore(phase, GraphState.StageFlag.VALUE_PROXY_REMOVAL, graphState);
    }

    public static boolean shouldApply(StructuredGraph graph) {
        return graph.hasLoops() || DFAnalysis.Options.DFA_EvalAll.getValue(graph.getOptions());
    }

    @SuppressWarnings("try")
    public DFAMap<T> run() {
        if (expended) {
            throw GraalError.shouldNotReachHere("This analysis instance has already been expended. DFAnalysis objects can only be used once!");
        }
        expended = true;
        if (!graph.isBeforeStage(GraphState.StageFlag.VALUE_PROXY_REMOVAL)) {
            throw GraalError.shouldNotReachHere("Analysis without value proxies may be slower and is not tested. Please only run the framework before value proxy removal.");
        }
        try (DebugContext.Scope ignore = debug.scope("DFAnalysis")) {
            debug.dump(DebugContext.DETAILED_LEVEL, graph, "before DFAnalysis");

            // main analysis loop, iterates until a fixed point is reached
            while (workList.hasNext()) {
                CompilationAlarm.checkProgress(graph);
                ValueNode node = workList.next();
                debug.log(DebugContext.VERY_DETAILED_LEVEL, "processing node %s", node);
                if (node instanceof AbstractMergeNode merge) {
                    handleMergeNode(merge);
                } else {
                    handleGeneralValueNode(node);
                }
                /*
                 * Nodes can be both control flow associated and normal values. Such nodes need to
                 * be evaluated as both splits and general values (e.g.
                 * ExactIntegerArithmeticSplitNodes or other value producing FixedNodes at the end
                 * of CFG blocks).
                 */
                if (node instanceof ControlSplitNode split) {
                    handleControlSplitNode(split);
                } else if (node instanceof FixedNode fixed && isLastNodeInBlock(cfg, fixed)) {
                    propagateReachability(fixed);
                }
            }

            /*
             * Drop all unreachable inferences (these do not provide any actionable, sometimes even
             * conflicting, information) to avoid confusion at the user's end when dealing with the
             * analysis result.
             */
            inferences.forEach(inf -> {
                if (!inf.isDeleted() && !mayBeReachable(cfg.blockFor(inf.getGuard()))) {
                    GraalError.guarantee(!elemMap.isEvaluated(inf), "Unreachable inference was evaluated");
                    inf.replaceAtUsagesAndDelete(inf.getOriginalNode());
                }
            });

            // extensive logging
            if (debug.isLogEnabled(DebugContext.DETAILED_LEVEL)) {
                debug.log(DebugContext.DETAILED_LEVEL, elemMap.prettyPrint());
                debug.log(DebugContext.DETAILED_LEVEL, edgeMap.prettyPrint());
                if (Options.DFA_RecordTrace.getValue(graph.getOptions())) {
                    debug.log(DebugContext.DETAILED_LEVEL, elemMap.printTrace());
                }
                debug.log(DebugContext.DETAILED_LEVEL, workList.prettyPrint());
            }
            debug.dump(DebugContext.DETAILED_LEVEL, graph, "after DFAnalysis");
            return elemMap;
        } catch (GraalError e) {
            StringBuilder sb = new StringBuilder();
            sb.append(elemMap.prettyPrint());
            if (Options.DFA_RecordTrace.getValue(graph.getOptions())) {
                sb.append(elemMap.printTrace());
            }
            sb.append(edgeMap.prettyPrint());
            sb.append(workList.prettyPrint());
            throw GraalError.shouldNotReachHere(e, sb.toString());
        }
    }

    /**
     * Removes {@link InferredFactNode}s generated by running this analysis instance.
     */
    public void cleanup() {
        // remove inferred facts
        for (InferredFactNode<T> iFact : inferences) {
            if (!iFact.isDeleted()) {
                iFact.replaceAtUsagesAndDelete(iFact.getOriginalNode());
            }
        }
    }

    /**
     * This analysis is "optimistic" in the sense that all control flow is initially assumed to be
     * unreachable. This means, back-edges of loops are also initially assumed to be unreachable,
     * causing the analysis to start out with very strong and gradually weakens the assumption until
     * a fixed point is reached. This allows the analysis to possibly reach better fixed points than
     * if the initial assumption was that the back-edges were reachable (which would be a
     * "pessimistic" assumption).
     * <p>
     * By passing below a control flow edge in the analysis or by the second iteration of evaluating
     * the loop, any assumption of unreachbility is implicitly updated to reachable. We do this,
     * since we would have marked any unreachable back-edges as such during evaluation of the upper
     * part of the graph or the first iteration of evaluating the loop.
     * <p>
     * If we are evaluating a straight-line merge, we are at a point in the analysis where we
     * implicitly updated unevaluated (and thereby UNKNOWN) edges to reachable. If we are evaluating
     * a loop merge, we need to be more careful, since the back-edges originate from a lower point
     * in the graph than what the analysis has reached when we first encounter the loop. On all
     * subsequent evaluation, the analyis has already reached the bottom of the loop, thereby
     * implicitly updating unevaluated edges to reachable.
     * <p>
     * Since reaching the bottom of the loop implicitly updates all unevaluated back-edges, we
     * schedule loop merges to be reevaluated after the loop body. Furthermore, rescheduling the
     * merge blocks any evaluation of the loop merge until the full loop body has been evaluated.
     */
    private void handleMergeNode(AbstractMergeNode mergeNode) {
        if (!mayBeReachable(cfg.blockFor(mergeNode))) {
            /*
             * This merge is currently believed to be unreachable and will be rescheduled if it is
             * considered reachable.
             */
            return;
        }

        List<ValuePhiNode> interestingPhis = mergeNode.valuePhis().filter(vp -> domain.isOfInterest((ValueNode) vp)).snapshot();
        if (interestingPhis.isEmpty()) {
            return;
        }

        boolean isOptimistic = mergeNode instanceof LoopBeginNode loopBegin && !loopBeginEvalCnt.containsKey(loopBegin);
        IntList reachableInputIndices = new IntList(mergeNode.phiPredecessorCount());
        if (isOptimistic) {
            /*
             * Evaluating a loop phi the first time needs to be done optimistically by propagating
             * the first value. Here we optimistically assume all incoming back edges as
             * unreachable.
             */
            reachableInputIndices.add(0);
        } else {
            // non-loop phi or previously visited loop phi: propagate pessimistically
            HIRBlock curBlock = cfg.blockFor(mergeNode);
            for (int i = 0; i < mergeNode.phiPredecessorCount(); i++) {
                if (edgeMap.get(cfg.blockFor(mergeNode.phiPredecessorAt(i)), curBlock) != Reachability.UNREACHABLE) {
                    // this edge is reachable
                    reachableInputIndices.add(i);
                }
            }
        }

        T[] results = domain.controlFlowMerge(interestingPhis, reachableInputIndices, elemMap);
        if (domain.supportsWidening() && mergeNode instanceof LoopBeginNode loopBegin && loopBeginEvalCnt.containsKey(loopBegin)) {
            domain.widen(interestingPhis, reachableInputIndices, results, elemMap, loopBeginEvalCnt.get(loopBegin));
        }

        boolean updated = false;
        for (int i = 0; i < results.length; i++) {
            ValuePhiNode curPhi = interestingPhis.get(i);
            T nuElem = results[i];
            if (elemMap.update(curPhi, nuElem)) {
                updated = true;
                if (curPhi.isLoopPhi() && !domain.isUnrestricted(nuElem)) {
                    /*
                     * Rescheduling the loop begin on a change of values for any associated phi
                     * ensures that any optimistic assumption is rechecked even if the reachability
                     * of back-edges only changes implicitly. Furthermore, rescheduling blocks any
                     * evaluation of this loop begin until after the entire loop body has been
                     * evaluated with the new information. If the new information is unrestricted,
                     * we will not update this phi again, therefore creating no need to reschedule
                     * the loop begin.
                     */
                    workList.rescheduleLoopBegin((LoopBeginNode) mergeNode, isOptimistic);
                }
                workList.scheduleUsages(curPhi);
            }
        }

        if (updated && mergeNode instanceof LoopBeginNode loopBegin) {
            if (!loopBeginEvalCnt.containsKey(loopBegin)) {
                // first evaluation is optimistic
                loopBeginEvalCnt.put(loopBegin, 0);
            } else {
                // increment loop updated cnt
                loopBeginEvalCnt.put(loopBegin, loopBeginEvalCnt.get(loopBegin) + 1);
            }
        }
    }

    /**
     * Processing a given value node by evaluating it given its inputs and rescheduling its usages
     * if the new value is weaker than the previous one. This method handles all value flow
     * calculations except the calculation for phis (see {@link DFAnalysis#handleMergeNode}).
     */
    private void handleGeneralValueNode(ValueNode node) {
        GraalError.guarantee(!(node instanceof ValuePhiNode), "Phi nodes need special treatment");
        final T nuElement;
        if (node instanceof InferredFactNode<?> fact && fact.isOfGeneric(elementType)) {
            if (!mayBeReachable(cfg.blockFor(fact.getGuard()))) {
                /*
                 * If an inferred fact node is unreachable, we do not care to evaluate it at this
                 * point in the analysis, it will come up later if necessary but at this point the
                 * inference might even be misleading. We insert inferences when evaluating its
                 * generating branching condition, which might be a floating node. On evalulation of
                 * floating nodes we cannot accurately predict the reachability of branches lower
                 * down in the graph. Therefore, this guard by reachability is necessary.
                 */
                return;
            } else {
                /*
                 * The handling of inferred facts is independent of the analysis domain, therefore
                 * we provide a generic transfer function here. The evaluation of inferences is
                 * hidden behind a check for reachability.
                 */
                InferredFactNode<T> cFact = fact.castTo(elementType);
                nuElement = cFact.transfer(this);
                if (nuElement == null) {
                    // InferredFactNode#transfer encountered an error and already handled it
                    return;
                }
            }
        } else {
            if (!domain.isOfInterest(node)) {
                // this node is not of interest to the given analysis
                return;
            }
            nuElement = domain.transfer(node, elemMap);
            GraalError.guarantee(nuElement != null,
                            "Received 'null' value in transfer function for interesting node %s. To indicate that no particular value can be inferred for this node, please return UNRESTRICTED instead.",
                            node);

            /*
             * Since general value nodes are not fully ordered with respect to each other (because
             * we do not have a full schedule), we can not guarantee that when evaluating a node in
             * the transfer function, all relevant inputs were evaluated before the given node
             * itself is evaluated. In such cases we still might want to produce values though (see
             * the example in AnalysisDomainDefinition#hasUnevaluatedInputs).
             *
             * If new inputs are now evaluated (i.e. prevUneval < nowUneval) monotonicity might be
             * broken. In this exceptional case, a violation of monotonicity is tolerable, since no
             * control flow decisions have been made based on this information. This is because all
             * nodes that will ever be evaluated, are evaluated in the first loop iteration, before
             * the loop phi is reevaluated pessimistically.
             */
            int prevUneval = nodesWithUnevaluatedInputs.containsKey(node) ? nodesWithUnevaluatedInputs.get(node) : -1;
            int nowUneval = domain.countUnevaluatedInputs(node, elemMap);
            if (nowUneval < prevUneval && domain.isWeakerThan(elemMap.getOrUnevaluated(node), nuElement).isTrue()) {
                elemMap.resetNodeAndUsages(node);
            }
            if (nowUneval >= 0) {
                nodesWithUnevaluatedInputs.put(node, nowUneval);
            }

            if (Options.DFA_AllowInferences.getValue(graph.getOptions())) {
                /*
                 * We generate inferences even if the result of the given node has not changed,
                 * because the mere fact that this node has been scheduled means that an input of
                 * this node has changed and the inferred facts need to be recalculated if the
                 * inputs of the generating node have changed.
                 */
                AnalysisInferenceHelper.generateInferredFacts(this, node);
            }
        }

        // then update and possibly schedule usages
        if (elemMap.update(node, nuElement)) {
            // if the associated information has been updated we schedule all its usages
            workList.scheduleUsages(node);
        }
    }

    /**
     * Handles control split node by calculating the reachability of its successors and subsequently
     * propagating the calculated reachabilities downwards.
     */
    private void handleControlSplitNode(ControlSplitNode split) {
        HIRBlock splitBlock = cfg.blockFor(split);
        boolean isCsReachable = mayBeReachable(splitBlock);
        boolean[] succReachability;
        if (isCsReachable) {
            succReachability = domain.splitReachability(split, elemMap);
            if (succReachability == null) {
                if (splitBlock.getSuccessorCount() == 2) {
                    succReachability = TRUE_TRUE;
                } else {
                    succReachability = new boolean[splitBlock.getSuccessorCount()];
                    Arrays.fill(succReachability, true);
                }
            }
        } else {
            succReachability = splitBlock.getSuccessorCount() == 2 ? FALSE_FALSE : new boolean[splitBlock.getSuccessorCount()];
        }
        for (int i = 0; i < succReachability.length; i++) {
            HIRBlock successor = splitBlock.getSuccessorAt(i);
            CFGEdge edge = new CFGEdge(splitBlock, successor);
            if (edgeMap.update(edge, succReachability[i])) {
                elemMap.recordPropagateReachability(split, edge, edgeMap, !isCsReachable);
                workList.schedule(successor, succReachability[i]);
            }
        }
    }

    /**
     * This method propagates the reachability of the given CFG block denoted by its starting node
     * to the outgoing CFG edges. This method is only supposed to receive CFG blocks with 0 or 1
     * successors. CFG blocks with multiple successors should be handled by
     * {@link DFAnalysis#handleControlSplitNode}.
     *
     * @param endNode the node at the end of the CFG block to propagate reachability through.
     */
    private void propagateReachability(FixedNode endNode) {
        HIRBlock cur = cfg.blockFor(endNode);
        GraalError.guarantee(cur.getSuccessorCount() < 2, "If this block (%s) is a control split, use DFAnalysis#handleControlSplit instead", cur);
        if (cur.getSuccessorCount() != 1) {
            // nothing to propagate
            return;
        }
        // propagate reachability 1 step downwards and schedule the next block
        HIRBlock next = cur.getFirstSuccessor();
        boolean nowReachable = mayBeReachable(cur);
        CFGEdge edge = new CFGEdge(cur, next);
        if (edgeMap.update(edge, nowReachable)) {
            elemMap.recordPropagateReachability(endNode, edge, edgeMap, true);
            // edge was updated
            workList.schedule(next, nowReachable);
        }
    }

    /**
     * This method checks if a block is currently considered to possibly be reachable. A block is
     * considered reachable if any of its predecessor edges is not {@code UNREACHABLE} (see
     * {@link Reachability}). For loop headers we only check the predecessor outside the loop since
     * the loop header dominates all incoming back edges. The start block is always considered
     * reachable.
     */
    boolean mayBeReachable(HIRBlock block) {
        if (block.getPredecessorCount() < 1) {
            // The start block is always reachable.
            assert block.getBeginNode() instanceof StartNode : block + " has no predecessors but is also not the start block";
            return true;
        }
        if (block.isLoopHeader()) {
            // If the loop entry cannot be reached from outside the loop, it should be considered
            // unreachable since the loop header dominates all back edges
            return edgeMap.get(block.getPredecessorAt(0), block) != Reachability.UNREACHABLE;
        }
        for (int i = 0; i < block.getPredecessorCount(); i++) {
            if (edgeMap.get(block.getPredecessorAt(i), block) != Reachability.UNREACHABLE) {
                return true;
            }
        }
        return false;
    }
}
