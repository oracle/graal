/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.phases.common;

import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import com.oracle.graal.phases.common.InliningUtil.InlineInfo;
import com.oracle.graal.phases.common.InliningUtil.InlineableMacroNode;
import com.oracle.graal.phases.common.InliningUtil.InliningPolicy;
import com.oracle.graal.phases.graph.*;

public class InliningPhase extends Phase {

    private static final HashMap<ResolvedJavaMethod, CompiledMethodInfo> compiledMethodInfo = new HashMap<>();

    private final PhasePlan plan;
    private final MetaAccessProvider runtime;
    private final Assumptions compilationAssumptions;
    private final Replacements replacements;
    private final GraphCache cache;
    private final InliningPolicy inliningPolicy;
    private final OptimisticOptimizations optimisticOpts;

    private CustomCanonicalizer customCanonicalizer;
    private int inliningCount;
    private int maxMethodPerInlining = Integer.MAX_VALUE;

    // Metrics
    private static final DebugMetric metricInliningPerformed = Debug.metric("InliningPerformed");
    private static final DebugMetric metricInliningConsidered = Debug.metric("InliningConsidered");
    private static final DebugMetric metricInliningStoppedByMaxDesiredSize = Debug.metric("InliningStoppedByMaxDesiredSize");
    private static final DebugMetric metricInliningRuns = Debug.metric("Runs");

    public InliningPhase(MetaAccessProvider runtime, Map<Invoke, Double> hints, Replacements replacements, Assumptions assumptions, GraphCache cache, PhasePlan plan,
                    OptimisticOptimizations optimisticOpts) {
        this(runtime, replacements, assumptions, cache, plan, optimisticOpts, hints);
    }

    private InliningPhase(MetaAccessProvider runtime, Replacements replacements, Assumptions assumptions, GraphCache cache, PhasePlan plan, OptimisticOptimizations optimisticOpts,
                    Map<Invoke, Double> hints) {
        this.runtime = runtime;
        this.replacements = replacements;
        this.compilationAssumptions = assumptions;
        this.cache = cache;
        this.plan = plan;
        this.inliningPolicy = new GreedyInliningPolicy(replacements, hints);
        this.optimisticOpts = optimisticOpts;
    }

    public void setCustomCanonicalizer(CustomCanonicalizer customCanonicalizer) {
        this.customCanonicalizer = customCanonicalizer;
    }

    public void setMaxMethodsPerInlining(int max) {
        maxMethodPerInlining = max;
    }

    public int getInliningCount() {
        return inliningCount;
    }

    public static void saveGraphStatistics(StructuredGraph graph) {
        CompiledMethodInfo info = compiledMethodInfo(graph.method());
        double summedUpProbabilityOfRemainingInvokes = sumUpInvokeProbabilities(graph);
        info.setSummedUpProbabilityOfRemainingInvokes(summedUpProbabilityOfRemainingInvokes);
        info.setHighLevelNodeCount(graph.getNodeCount());
    }

    @Override
    protected void run(final StructuredGraph graph) {
        InliningData data = new InliningData();
        data.pushGraph(graph, 1.0, 1.0);

        while (data.hasUnprocessedGraphs()) {
            GraphInfo graphInfo = data.currentGraph();
            if (graphInfo.hasRemainingInvokes() && inliningPolicy.continueInlining(data)) {
                processNextInvoke(data, graphInfo);
            } else {
                data.popGraph();
                tryToInlineCurrentInvocation(data);
            }
        }
    }

    /**
     * Process the next invoke and enqueue all its graphs for processing.
     */
    private void processNextInvoke(InliningData data, GraphInfo graphInfo) {
        Invoke invoke = graphInfo.popInvoke();
        MethodInvocation callerInvocation = data.currentInvocation();
        Assumptions parentAssumptions = callerInvocation == null ? compilationAssumptions : callerInvocation.assumptions();
        InlineInfo info = InliningUtil.getInlineInfo(data, invoke, maxMethodPerInlining, replacements, parentAssumptions, optimisticOpts);

        double invokeProbability = graphInfo.getInvokeProbability(invoke);
        double invokeRelevance = graphInfo.getInvokeRelevance(invoke);
        if (info != null && inliningPolicy.isWorthInlining(info, invokeProbability, invokeRelevance, false)) {
            MethodInvocation calleeInvocation = data.pushInvocation(info, parentAssumptions, invokeProbability, invokeRelevance);

            for (int i = 0; i < info.numberOfMethods(); i++) {
                InlineableElement elem = getInlineableElement(info.methodAt(i), info.invoke(), calleeInvocation.assumptions());
                info.setInlinableElement(i, elem);
                if (elem instanceof StructuredGraph) {
                    data.pushGraph((StructuredGraph) elem, invokeProbability * info.probabilityAt(i), invokeRelevance * info.relevanceAt(i));
                } else {
                    assert elem instanceof InlineableMacroNode;
                    // directly mark one callee as done because we do not have a graph to process
                    calleeInvocation.incrementProcessedGraphs();
                }
            }
        }
    }

    private void tryToInlineCurrentInvocation(InliningData data) {
        MethodInvocation currentInvocation = data.currentInvocation();
        if (currentInvocation != null) {
            assert currentInvocation.callee().invoke().asNode().isAlive();
            currentInvocation.incrementProcessedGraphs();
            if (currentInvocation.processedAllGraphs()) {
                data.popInvocation();
                MethodInvocation parentInvoke = data.currentInvocation();
                tryToInline(data.currentGraph(), currentInvocation, parentInvoke);
            }
        }
    }

    private void tryToInline(GraphInfo callerGraphInfo, MethodInvocation calleeInfo, MethodInvocation parentInvocation) {
        InlineInfo callee = calleeInfo.callee();
        Assumptions callerAssumptions = parentInvocation == null ? compilationAssumptions : parentInvocation.assumptions();

        if (inliningPolicy.isWorthInlining(callee, calleeInfo.probability(), calleeInfo.relevance(), true)) {
            doInline(callerGraphInfo, calleeInfo, callerAssumptions);
        } else if (optimisticOpts.devirtualizeInvokes()) {
            callee.tryToDevirtualizeInvoke(runtime, callerAssumptions);
        }
        metricInliningConsidered.increment();
    }

    private void doInline(GraphInfo callerGraphInfo, MethodInvocation calleeInfo, Assumptions callerAssumptions) {
        StructuredGraph callerGraph = callerGraphInfo.graph();
        int markBeforeInlining = callerGraph.getMark();
        InlineInfo callee = calleeInfo.callee();
        try {
            List<Node> invokeUsages = callee.invoke().asNode().usages().snapshot();
            callee.inline(runtime, callerAssumptions);
            callerAssumptions.record(calleeInfo.assumptions());
            metricInliningRuns.increment();
            Debug.dump(callerGraph, "after %s", callee);

            if (GraalOptions.OptCanonicalizer) {
                int markBeforeCanonicalization = callerGraph.getMark();
                new CanonicalizerPhase.Instance(runtime, callerAssumptions, invokeUsages, markBeforeInlining, customCanonicalizer).apply(callerGraph);

                // process invokes that are possibly created during canonicalization
                for (Node newNode : callerGraph.getNewNodes(markBeforeCanonicalization)) {
                    if (newNode instanceof Invoke) {
                        callerGraphInfo.pushInvoke((Invoke) newNode);
                    }
                }
            }

            callerGraphInfo.computeProbabilities();

            inliningCount++;
            metricInliningPerformed.increment();
        } catch (BailoutException bailout) {
            throw bailout;
        } catch (AssertionError | RuntimeException e) {
            throw new GraalInternalError(e).addContext(callee.toString());
        } catch (GraalInternalError e) {
            throw e.addContext(callee.toString());
        }
    }

    private InlineableElement getInlineableElement(final ResolvedJavaMethod method, Invoke invoke, Assumptions assumptions) {
        Class<? extends FixedWithNextNode> macroNodeClass = InliningUtil.getMacroNodeClass(replacements, method);
        if (macroNodeClass != null) {
            return new InlineableMacroNode(macroNodeClass);
        } else {
            return buildGraph(method, invoke, assumptions);
        }
    }

    private StructuredGraph buildGraph(final ResolvedJavaMethod method, final Invoke invoke, final Assumptions assumptions) {
        final StructuredGraph newGraph;
        final boolean parseBytecodes;

        // TODO (chaeubl): copying the graph is only necessary if it is modified or if it contains
        // any invokes
        StructuredGraph intrinsicGraph = InliningUtil.getIntrinsicGraph(replacements, method);
        if (intrinsicGraph != null) {
            newGraph = intrinsicGraph.copy();
            parseBytecodes = false;
        } else {
            StructuredGraph cachedGraph = getCachedGraph(method);
            if (cachedGraph != null) {
                newGraph = cachedGraph.copy();
                parseBytecodes = false;
            } else {
                newGraph = new StructuredGraph(method);
                parseBytecodes = true;
            }
        }

        return Debug.scope("InlineGraph", newGraph, new Callable<StructuredGraph>() {

            @Override
            public StructuredGraph call() throws Exception {
                if (parseBytecodes) {
                    parseBytecodes(newGraph, assumptions);
                }

                if (GraalOptions.PropagateArgumentsDuringInlining) {
                    boolean callerHasMoreInformationAboutArguments = false;
                    NodeInputList<ValueNode> args = invoke.callTarget().arguments();
                    for (LocalNode localNode : newGraph.getNodes(LocalNode.class).snapshot()) {
                        ValueNode arg = args.get(localNode.index());
                        if (arg.isConstant()) {
                            Constant constant = arg.asConstant();
                            newGraph.replaceFloating(localNode, ConstantNode.forConstant(constant, runtime, newGraph));
                            callerHasMoreInformationAboutArguments = true;
                        } else {
                            Stamp joinedStamp = localNode.stamp().join(arg.stamp());
                            if (!joinedStamp.equals(localNode.stamp())) {
                                localNode.setStamp(joinedStamp);
                                callerHasMoreInformationAboutArguments = true;
                            }
                        }
                    }

                    if (!callerHasMoreInformationAboutArguments) {
                        // TODO (chaeubl): if args are not more concrete, inlining should be avoided
                        // in most cases or we could at least use the previous graph size + invoke
                        // probability to check the inlining
                    }

                    if (GraalOptions.OptCanonicalizer) {
                        new CanonicalizerPhase.Instance(runtime, assumptions).apply(newGraph);
                    }
                }

                return newGraph;
            }
        });
    }

    private StructuredGraph getCachedGraph(ResolvedJavaMethod method) {
        if (GraalOptions.CacheGraphs && cache != null) {
            StructuredGraph cachedGraph = cache.get(method);
            if (cachedGraph != null) {
                return cachedGraph;
            }
        }
        return null;
    }

    private StructuredGraph parseBytecodes(StructuredGraph newGraph, Assumptions assumptions) {
        if (plan != null) {
            plan.runPhases(PhasePosition.AFTER_PARSING, newGraph);
        }
        assert newGraph.start().next() != null : "graph needs to be populated during PhasePosition.AFTER_PARSING";

        new DeadCodeEliminationPhase().apply(newGraph);

        if (GraalOptions.OptCanonicalizer) {
            new CanonicalizerPhase.Instance(runtime, assumptions).apply(newGraph);
        }

        if (GraalOptions.CullFrameStates) {
            new CullFrameStatesPhase().apply(newGraph);
        }
        if (GraalOptions.CacheGraphs && cache != null) {
            cache.put(newGraph.copy());
        }
        return newGraph;
    }

    private static synchronized CompiledMethodInfo compiledMethodInfo(ResolvedJavaMethod m) {
        CompiledMethodInfo info = compiledMethodInfo.get(m);
        if (info == null) {
            info = new CompiledMethodInfo();
            compiledMethodInfo.put(m, info);
        }
        return info;
    }

    private static double sumUpInvokeProbabilities(StructuredGraph graph) {
        NodesToDoubles nodeProbabilities = new ComputeProbabilityClosure(graph).apply();
        double summedUpProbabilityOfRemainingInvokes = 0;
        for (Invoke invoke : graph.getInvokes()) {
            summedUpProbabilityOfRemainingInvokes += nodeProbabilities.get(invoke.asNode());
        }
        return summedUpProbabilityOfRemainingInvokes;
    }

    private static class GraphInfo {

        private final StructuredGraph graph;
        private final Stack<Invoke> remainingInvokes;
        private final double probability;
        private final double relevance;
        private NodesToDoubles nodeProbabilities;
        private NodesToDoubles nodeRelevance;

        public GraphInfo(StructuredGraph graph, Stack<Invoke> invokes, double probability, double relevance) {
            this.graph = graph;
            this.remainingInvokes = invokes;
            this.probability = probability;
            this.relevance = relevance;

            computeProbabilities();
        }

        public boolean hasRemainingInvokes() {
            return !remainingInvokes.isEmpty();
        }

        public StructuredGraph graph() {
            return graph;
        }

        public Invoke popInvoke() {
            return remainingInvokes.pop();
        }

        public void pushInvoke(Invoke invoke) {
            remainingInvokes.push(invoke);
        }

        public void computeProbabilities() {
            nodeProbabilities = new ComputeProbabilityClosure(graph).apply();
            nodeRelevance = new ComputeInliningRelevanceClosure(graph, nodeProbabilities).apply();
        }

        public double getInvokeProbability(Invoke invoke) {
            return probability * nodeProbabilities.get(invoke.asNode());
        }

        public double getInvokeRelevance(Invoke invoke) {
            return Math.min(relevance, 1.0) * nodeRelevance.get(invoke.asNode());
        }
    }

    private abstract static class AbstractInliningPolicy implements InliningPolicy {

        protected final Replacements replacements;
        protected final Map<Invoke, Double> hints;

        public AbstractInliningPolicy(Replacements replacements, Map<Invoke, Double> hints) {
            this.replacements = replacements;
            this.hints = hints;
        }

        protected double computeMaximumSize(double relevance, int configuredMaximum) {
            double inlineRatio = Math.min(GraalOptions.RelevanceCapForInlining, relevance);
            return configuredMaximum * inlineRatio;
        }

        protected double getInliningBonus(InlineInfo info) {
            if (hints != null && hints.containsKey(info.invoke())) {
                return hints.get(info.invoke());
            }
            return 1;
        }

        protected boolean isIntrinsic(InlineInfo info) {
            if (GraalOptions.AlwaysInlineIntrinsics) {
                return onlyIntrinsics(info);
            } else {
                return onlyForcedIntrinsics(info);
            }
        }

        private boolean onlyIntrinsics(InlineInfo info) {
            for (int i = 0; i < info.numberOfMethods(); i++) {
                if (!InliningUtil.canIntrinsify(replacements, info.methodAt(i))) {
                    return false;
                }
            }
            return true;
        }

        private boolean onlyForcedIntrinsics(InlineInfo info) {
            for (int i = 0; i < info.numberOfMethods(); i++) {
                if (!InliningUtil.canIntrinsify(replacements, info.methodAt(i))) {
                    return false;
                }
                if (!replacements.isForcedSubstitution(info.methodAt(i))) {
                    return false;
                }
            }
            return true;
        }

        protected static int previousHIRSize(InlineInfo info) {
            int size = 0;
            for (int i = 0; i < info.numberOfMethods(); i++) {
                size += compiledMethodInfo(info.methodAt(i)).getHighLevelNodes();
            }
            return size;
        }

        protected static int determineNodeCount(InlineInfo info) {
            int nodes = 0;
            for (int i = 0; i < info.numberOfMethods(); i++) {
                InlineableElement elem = info.inlineableElementAt(i);
                if (elem != null) {
                    nodes += elem.getNodeCount();
                }
            }
            return nodes;
        }

        protected static double determineInvokeProbability(InlineInfo info) {
            double invokeProbability = 0;
            for (int i = 0; i < info.numberOfMethods(); i++) {
                InlineableElement callee = info.inlineableElementAt(i);
                Iterable<Invoke> invokes = callee.getInvokes();
                if (invokes.iterator().hasNext()) {
                    NodesToDoubles nodeProbabilities = new ComputeProbabilityClosure((StructuredGraph) callee).apply();
                    for (Invoke invoke : invokes) {
                        invokeProbability += nodeProbabilities.get(invoke.asNode());
                    }
                }
            }
            return invokeProbability;
        }
    }

    private static class GreedyInliningPolicy extends AbstractInliningPolicy {

        public GreedyInliningPolicy(Replacements replacements, Map<Invoke, Double> hints) {
            super(replacements, hints);
        }

        public boolean continueInlining(InliningData data) {
            if (data.currentGraph().graph().getNodeCount() >= GraalOptions.MaximumDesiredSize) {
                InliningUtil.logInliningDecision("inlining is cut off by MaximumDesiredSize");
                metricInliningStoppedByMaxDesiredSize.increment();
                return false;
            }

            MethodInvocation currentInvocation = data.currentInvocation();
            if (currentInvocation == null) {
                return true;
            }

            return isWorthInlining(currentInvocation.callee(), currentInvocation.probability(), currentInvocation.relevance(), false);
        }

        @Override
        public boolean isWorthInlining(InlineInfo info, double probability, double relevance, boolean fullyProcessed) {
            if (isIntrinsic(info)) {
                return InliningUtil.logInlinedMethod(info, fullyProcessed, "intrinsic");
            }

            double inliningBonus = getInliningBonus(info);

            int hirSize = previousHIRSize(info);
            if (hirSize > GraalOptions.SmallCompiledGraphSize * inliningBonus) {
                return InliningUtil.logNotInlinedMethod(info, "too large HIR graph: %s", hirSize);
            }

            /*
             * TODO (chaeubl): invoked methods that are on important paths but not yet compiled ->
             * will be compiled anyways and it is likely that we are the only caller... might be
             * useful to inline those methods but increases bootstrap time (maybe those methods are
             * also getting queued in the compilation queue concurrently)
             */

            int nodes = determineNodeCount(info);
            if (nodes < GraalOptions.TrivialHighLevelGraphSize * inliningBonus) {
                return InliningUtil.logInlinedMethod(info, fullyProcessed, "trivial (nodes=%d)", nodes);
            }

            double invokes = determineInvokeProbability(info);
            if (GraalOptions.LimitInlinedInvokes > 0 && fullyProcessed && invokes > GraalOptions.LimitInlinedInvokes * inliningBonus) {
                return InliningUtil.logNotInlinedMethod(info, "invoke probability is too high (%f)", invokes);
            }

            double maximumNodes = computeMaximumSize(relevance, (int) (GraalOptions.NormalHighLevelGraphSize * inliningBonus));
            if (nodes < maximumNodes) {
                return InliningUtil.logInlinedMethod(info, fullyProcessed, "relevance-based (relevance=%f, nodes=%d)", relevance, nodes);
            }

            return InliningUtil.logNotInlinedMethod(info, "(relevance=%f, probability=%f, bonus=%f)", relevance, probability, inliningBonus);
        }
    }

    private static class InliningIterator {

        private final FixedNode start;
        private final Deque<FixedNode> nodeQueue;
        private final NodeBitMap queuedNodes;

        public InliningIterator(FixedNode start, NodeBitMap visitedFixedNodes) {
            this.start = start;
            this.nodeQueue = new ArrayDeque<>();
            this.queuedNodes = visitedFixedNodes;
            assert start.isAlive();
        }

        public Stack<Invoke> apply() {
            Stack<Invoke> invokes = new Stack<>();
            FixedNode current;
            forcedQueue(start);

            while ((current = nextQueuedNode()) != null) {
                assert current.isAlive();

                if (current instanceof Invoke) {
                    if (current != start) {
                        invokes.push((Invoke) current);
                    }
                    queueSuccessors(current);
                } else if (current instanceof LoopBeginNode) {
                    queueSuccessors(current);
                } else if (current instanceof LoopEndNode) {
                    // nothing todo
                } else if (current instanceof MergeNode) {
                    queueSuccessors(current);
                } else if (current instanceof FixedWithNextNode) {
                    queueSuccessors(current);
                } else if (current instanceof EndNode) {
                    queueMerge((EndNode) current);
                } else if (current instanceof ControlSinkNode) {
                    // nothing todo
                } else if (current instanceof ControlSplitNode) {
                    queueSuccessors(current);
                } else {
                    assert false : current;
                }
            }

            return invokes;
        }

        private void queueSuccessors(FixedNode x) {
            for (Node node : x.successors()) {
                queue(node);
            }
        }

        private void queue(Node node) {
            if (node != null && !queuedNodes.isMarked(node)) {
                forcedQueue(node);
            }
        }

        private void forcedQueue(Node node) {
            queuedNodes.mark(node);
            nodeQueue.addFirst((FixedNode) node);
        }

        private FixedNode nextQueuedNode() {
            if (nodeQueue.isEmpty()) {
                return null;
            }

            FixedNode result = nodeQueue.removeFirst();
            assert queuedNodes.isMarked(result);
            return result;
        }

        private void queueMerge(EndNode end) {
            MergeNode merge = end.merge();
            if (!queuedNodes.isMarked(merge) && visitedAllEnds(merge)) {
                queuedNodes.mark(merge);
                nodeQueue.add(merge);
            }
        }

        private boolean visitedAllEnds(MergeNode merge) {
            for (int i = 0; i < merge.forwardEndCount(); i++) {
                if (!queuedNodes.isMarked(merge.forwardEndAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Holds the data for building the callee graphs recursively: graphs and invocations (each
     * invocation can have multiple graphs).
     */
    static class InliningData {

        private final ArrayDeque<GraphInfo> graphQueue;
        private final ArrayDeque<MethodInvocation> invocationQueue;

        private int maxGraphs = 1;

        public InliningData() {
            this.graphQueue = new ArrayDeque<>();
            this.invocationQueue = new ArrayDeque<>();
        }

        public void pushGraph(StructuredGraph graph, double probability, double relevance) {
            assert !contains(graph);
            NodeBitMap visitedFixedNodes = graph.createNodeBitMap();
            Stack<Invoke> invokes = new InliningIterator(graph.start(), visitedFixedNodes).apply();
            assert invokes.size() == count(graph.getInvokes());
            graphQueue.push(new GraphInfo(graph, invokes, probability, relevance));
            assert graphQueue.size() <= maxGraphs;
        }

        public boolean hasUnprocessedGraphs() {
            return !graphQueue.isEmpty();
        }

        public GraphInfo currentGraph() {
            return graphQueue.peek();
        }

        public void popGraph() {
            graphQueue.pop();
            assert graphQueue.size() <= maxGraphs;
        }

        public MethodInvocation currentInvocation() {
            return invocationQueue.peek();
        }

        public MethodInvocation pushInvocation(InlineInfo info, Assumptions assumptions, double probability, double relevance) {
            MethodInvocation methodInvocation = new MethodInvocation(info, new Assumptions(assumptions.useOptimisticAssumptions()), probability, relevance);
            invocationQueue.push(methodInvocation);
            maxGraphs += info.numberOfMethods();
            assert graphQueue.size() <= maxGraphs;
            return methodInvocation;
        }

        public void popInvocation() {
            maxGraphs -= invocationQueue.peek().callee.numberOfMethods();
            assert graphQueue.size() <= maxGraphs;
            invocationQueue.pop();
        }

        public int countRecursiveInlining(ResolvedJavaMethod method) {
            int count = 0;
            for (GraphInfo graphInfo : graphQueue) {
                if (method.equals(graphInfo.graph().method())) {
                    count++;
                }
            }
            return count;
        }

        public int inliningDepth() {
            return invocationQueue.size();
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder("Invocations: ");

            for (MethodInvocation invocation : invocationQueue) {
                result.append(invocation.callee().numberOfMethods());
                result.append("x ");
                result.append(invocation.callee().invoke());
                result.append("; ");
            }

            result.append("\nGraphs: ");
            for (GraphInfo graph : graphQueue) {
                result.append(graph.graph());
                result.append("; ");
            }

            return result.toString();
        }

        private boolean contains(StructuredGraph graph) {
            for (GraphInfo info : graphQueue) {
                if (info.graph() == graph) {
                    return true;
                }
            }
            return false;
        }

        private static int count(Iterable<Invoke> invokes) {
            int count = 0;
            Iterator<Invoke> iterator = invokes.iterator();
            while (iterator.hasNext()) {
                iterator.next();
                count++;
            }
            return count;
        }
    }

    private static class MethodInvocation {

        private final InlineInfo callee;
        private final Assumptions assumptions;
        private final double probability;
        private final double relevance;

        private int processedGraphs;

        public MethodInvocation(InlineInfo info, Assumptions assumptions, double probability, double relevance) {
            this.callee = info;
            this.assumptions = assumptions;
            this.probability = probability;
            this.relevance = relevance;
        }

        public void incrementProcessedGraphs() {
            processedGraphs++;
        }

        public boolean processedAllGraphs() {
            assert processedGraphs <= callee.numberOfMethods();
            return processedGraphs == callee.numberOfMethods();
        }

        public InlineInfo callee() {
            return callee;
        }

        public Assumptions assumptions() {
            return assumptions;
        }

        public double probability() {
            return probability;
        }

        public double relevance() {
            return relevance;
        }
    }

    private static class CompiledMethodInfo {

        private int highLevelNodes;
        private double summedUpProbabilityOfRemainingInvokes;

        public CompiledMethodInfo() {
        }

        public int getHighLevelNodes() {
            return highLevelNodes;
        }

        public void setHighLevelNodeCount(int highLevelNodes) {
            this.highLevelNodes = highLevelNodes;
        }

        public double getSummedUpProbabilityOfRemainingInvokes() {
            return summedUpProbabilityOfRemainingInvokes;
        }

        public void setSummedUpProbabilityOfRemainingInvokes(double summedUpProbabilityOfRemainingInvokes) {
            this.summedUpProbabilityOfRemainingInvokes = summedUpProbabilityOfRemainingInvokes;
        }
    }
}
