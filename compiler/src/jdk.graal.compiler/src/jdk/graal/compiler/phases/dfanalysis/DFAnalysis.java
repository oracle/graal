/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package jdk.graal.compiler.phases.dfanalysis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import jdk.graal.compiler.phases.dfanalysis.DFEdgeMap.CFGEdge;
import jdk.graal.compiler.phases.dfanalysis.DFEdgeMap.Reachability;

/**
 * <p>
 * This class is a framework to be used for control flow sensitive optimistic data flow analysis.
 * This framework applies a common fixed point algorithm using a given analysis domain that takes
 * the shape of a complete lattice. It tracks abstract domain values for nodes in the graph as well
 * as information about reachability of control flow edges. The algorithm is designed in a way that
 * it does not require a full schedule of the graph and additionally only iterates over portions of
 * the graph that may contribute to new values being discovered.
 * </p>
 * <p>
 * This framework implements the fixed point algorithm described in
 * <a href="https://dl.acm.org/doi/10.1145/3679007.3685059">this paper</a>. It is an optimistic
 * algorithm designed to find fixed points for loops with control flow which can not be found using
 * the pessimistic approach used in canonicalization and conditional elimination. This algorithm is
 * "optimistic" in the sense that all back edges of loops are initially assumed to be unreachable.
 * For all other evaluations of Phis, unevaluated reachable inputs are interpreted as data flow that
 * will never yield a result.
 * </p>
 * <p>
 * For this fixed point algorithm to yield the correct result, the analysis domains for this
 * framework need to be a <a href="https://en.wikipedia.org/wiki/Lattice_(order)">complete
 * lattice</a> with the unrestricted element representing all possible values. The
 * {@link AnalysisDomainDefinition} interface provides a skeleton of methods used to represent such
 * a complete lattice for use in this framework. The semi-order of a lattice defined using this
 * interface is defined in {@link AnalysisDomainDefinition#isWeakerThan}. If a value is "weaker
 * than" another, it means, the weaker value is less specific than the stronger value. The strongest
 * value in this lattice is an "unevaluated" value, meaning, the analysis has not reached this node
 * yet. The weakest value in this lattice is "unrestricted", representing the fact that the analysis
 * can not infer any information about the given node and its output could be any concrete value.
 * The algorithm in this framework starts all nodes on "unevaluated" and gradually computes weaker
 * values for each node until a fixed point is reached.
 * </p>
 * <p>
 * Evaluating a PHI "pessimistically" means, all unevaluated reachable values are taken as
 * unrestricted. This effectively means, that branches that are unevaluated when evaluating this PHI
 * will never produce new information. This is the default evaluation method of PHI nodes. The order
 * in which nodes are processed in this framework and the conditions for
 * {@link AnalysisDomainDefinition#transfer} ensure that at the point where the given PHI is
 * evaluated, all inputs are sure to already be evaluated to their strongest non-unevaluated value.
 * Therefore, this pessimistic assumption can be made without compromising on precision.
 * </p>
 * <p>
 * On the first encounter, loop PHIs are evaluated "optimistically". This "optimistic" property of
 * the fixed point algorithm is necessary to find constants like the following example:
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
 * assuming either 'x' to be constant 1, or 'x = 2' to be unreachable (these assumptions are
 * mechanically equivalent) and afterward checking the assumption allows us to break this cycle and
 * detect 'x' to be constant.
 * </p>
 * <p>
 * The transfer function ({@link AnalysisDomainDefinition#transfer} must satisfy monotonicity. This
 * means, interpreting unevaluated values as unrestricted, given increasingly more general (weaker)
 * inputs, the result of the transfer function must be weaker or equal to the result given more
 * precise (stronger) inputs (i.e. given inputs
 * {@code a <= x & b <= y: transferForAdd(a, b) <= transferForAdd(x, y)}). For the case that
 * monotonicity may be violated when an additional input gets evaluated for a node, {@link DFAMap}
 * (the map passed to the transfer function to retrieve information about the given node's inputs)
 * offers a way to circumvent monotonicity in form of the method {@link DFAMap#resetNodeAndUsages}.
 * The user may call this method inside the transfer function and then provide a stronger result
 * than was associated with the given node before.
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

    // =================================================================================================================
    // Creation and fields of DFAnalysis class
    // =================================================================================================================
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

    // =================================================================================================================
    // Public access to behavior of DFAnalysis class
    // =================================================================================================================

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

    // =================================================================================================================
    // Private methods describing inner behavior
    // =================================================================================================================

    static boolean isMemoryUsage(ValueNode value, ValueNode usage) {
        for (Position pos : usage.inputPositions()) {
            if (pos.getInputType() == InputType.Memory && pos.get(usage) == value) {
                // this is indeed a memory edge
                return true;
            }
        }
        return false;
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
     * All non-loop merges can be handled pessimistically since all possibly available reachability
     * info as well as all the strongest non-unevaluated input values should already be calculated
     * when the merge is evaluated. Loop phis, upon first evaluation, need to be handled
     * optimistically because information about reachability of back-edges is not yet available.
     * Because propagating values through loop phis optimistically can result in incorrect values,
     * loop phis need to be reevaluated in a pessimistic fashion after processing their respective
     * loop to ensure correctness of the result.
     */
    private void handleMergeNode(AbstractMergeNode mergeNode) {
        if (!mayBeReachable(cfg.blockFor(mergeNode))) {
            /*
             * This phi is currently believed to be unreachable and will be rescheduled if it is
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
            if (elemMap.update(curPhi, results[i])) {
                updated = true;
                if (curPhi.isLoopPhi() && !domain.isUnrestricted(nuElem)) {
                    /*
                     * To prevent any reevaluation of the loop until the entire loop has been
                     * evaluated once more, we reschedule the loop begin. Additionally, rescheduling
                     * the loop begin after evaluating it optimistically implicitly schedules a
                     * check if the optimistic assumption was correct. If not, we can rectify overly
                     * optimistic assumptions then. If all phis at the loop begin were updated to
                     * unrestricted, no further information will emerge here, therefore we do not
                     * have to reschedule it.
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
                 * inference might even be misleading.
                 */
                return;
            } else {
                /*
                 * The handling of inferred facts is independent of the analysis domain, therefore
                 * we provide a generic transfer function here.
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
            // if the associated stamp has been updated we schedule all usages of the given node
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
        boolean[] succReachability = isCsReachable
                        ? domain.splitReachability(split, elemMap)
                        : splitBlock.getSuccessorCount() == 2 ? FALSE_FALSE : new boolean[splitBlock.getSuccessorCount()];
        if (succReachability == null) {
            if (splitBlock.getSuccessorCount() == 2) {
                succReachability = TRUE_TRUE;
            } else {
                succReachability = new boolean[splitBlock.getSuccessorCount()];
                Arrays.fill(succReachability, true);
            }
        }
        for (int i = 0; i < succReachability.length; i++) {
            HIRBlock successor = splitBlock.getSuccessorAt(i);
            CFGEdge edge = new CFGEdge(splitBlock, successor);
            if (edgeMap.update(edge, succReachability[i])) {
                elemMap.recordPropagateReachability(split, edge, edgeMap, !isCsReachable);
                workList.schedule(successor);
                if (successor.getBeginNode() instanceof AbstractMergeNode merge) {
                    // if an edge to a phi has changed, we need to reschedule the merge
                    workList.schedule(merge);
                }
                // reschedule all inferred facts that might now have become reachable
                if (succReachability[i]) {
                    successor.getBeginNode().usages().filter(InferredFactNode.class).forEach(workList::schedule);
                }
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
            workList.schedule(next);
            if (next.getBeginNode() instanceof AbstractMergeNode merge) {
                // if an edge to a phi has changed, we need to reschedule the merge
                workList.schedule(merge);
            }
            // reschedule all inferred facts that might now have become reachable
            if (nowReachable) {
                next.getBeginNode().usages().filter(InferredFactNode.class).forEach(workList::schedule);
            }
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
}
