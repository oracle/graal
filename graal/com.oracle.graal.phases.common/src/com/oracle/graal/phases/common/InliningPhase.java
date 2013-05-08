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
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import com.oracle.graal.phases.common.InliningUtil.InlineInfo;
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
    private final InliningData data;

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

    public InliningPhase(MetaAccessProvider runtime, Replacements replacements, Assumptions assumptions, GraphCache cache, PhasePlan plan, OptimisticOptimizations optimisticOpts) {
        this(runtime, replacements, assumptions, cache, plan, optimisticOpts, null);
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

        this.data = new InliningData();
    }

    public static synchronized CompiledMethodInfo compiledMethodInfo(ResolvedJavaMethod m) {
        CompiledMethodInfo info = compiledMethodInfo.get(m);
        if (info == null) {
            info = new CompiledMethodInfo();
            compiledMethodInfo.put(m, info);
        }
        return info;
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

    static class InliningData {

        private static final GraphInfo DummyGraphInfo = new GraphInfo(null, new Stack<Invoke>(), 1.0, 1.0);

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

        public void pushGraphDummy() {
            graphQueue.push(DummyGraphInfo);
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

        public int inliningDepth() {
            return invocationQueue.size();
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

    @Override
    protected void run(final StructuredGraph graph) {
        data.pushGraph(graph, 1.0, 1.0);

        while (data.hasUnprocessedGraphs()) {
            GraphInfo graphInfo = data.currentGraph();
            if (graphInfo.hasRemainingInvokes() && inliningPolicy.continueInlining(data)) {
                processNextInvoke(graphInfo);
            } else {
                data.popGraph();
                MethodInvocation currentInvoke = data.currentInvocation();
                if (currentInvoke != null) {
                    assert currentInvoke.callee().invoke().asNode().isAlive();
                    currentInvoke.incrementProcessedGraphs();
                    if (currentInvoke.processedAllGraphs()) {
                        data.popInvocation();
                        MethodInvocation parentInvoke = data.currentInvocation();
                        tryToInline(data.currentGraph(), currentInvoke, parentInvoke);
                    }
                }
            }
        }
    }

    private void processNextInvoke(GraphInfo graphInfo) {
        // process the next invoke and enqueue all its graphs for processing
        Invoke invoke = graphInfo.popInvoke();
        MethodInvocation callerInvocation = data.currentInvocation();
        Assumptions parentAssumptions = callerInvocation == null ? compilationAssumptions : callerInvocation.assumptions();
        InlineInfo info = InliningUtil.getInlineInfo(data, invoke, maxMethodPerInlining, replacements, parentAssumptions, optimisticOpts);

        if (info != null) {
            double callerProbability = graphInfo.getInvokeProbability(invoke);
            double callerRelevance = graphInfo.getInvokeRelevance(invoke);
            MethodInvocation calleeInvocation = data.pushInvocation(info, parentAssumptions, callerProbability, callerRelevance);

            for (int i = 0; i < info.numberOfMethods(); i++) {
                InlineableElement elem = getInlineableElement(info.methodAt(i), info.invoke(), calleeInvocation.assumptions());
                info.setInlinableElement(i, elem);
                if (elem instanceof StructuredGraph) {
                    data.pushGraph((StructuredGraph) elem, callerProbability * info.probabilityAt(i), callerRelevance * info.relevanceAt(i));
                } else {
                    data.pushGraphDummy();
                }
            }
        }
    }

    private void tryToInline(GraphInfo callerGraphInfo, MethodInvocation calleeInfo, MethodInvocation parentInvoke) {
        InlineInfo callee = calleeInfo.callee();
        Assumptions callerAssumptions = parentInvoke == null ? compilationAssumptions : parentInvoke.assumptions();

        if (inliningPolicy.isWorthInlining(callee, calleeInfo.probability(), calleeInfo.relevance())) {
            doInline(callerGraphInfo, calleeInfo, callerAssumptions);
        } else if (optimisticOpts.devirtualizeInvokes()) {
            callee.tryToDevirtualizeInvoke(runtime, callerAssumptions);
        }
        metricInliningConsidered.increment();
    }

    private void doInline(GraphInfo callerGraphInfo, MethodInvocation calleeInfo, Assumptions callerAssumptions) {
        StructuredGraph callerGraph = callerGraphInfo.graph();
        int mark = callerGraph.getMark();
        InlineInfo callee = calleeInfo.callee();
        try {
            List<Node> invokeUsages = callee.invoke().asNode().usages().snapshot();
            callee.inline(runtime, callerAssumptions);
            callerAssumptions.record(calleeInfo.assumptions());
            metricInliningRuns.increment();
            Debug.dump(callerGraph, "after %s", callee);

            if (GraalOptions.OptCanonicalizer) {
                new CanonicalizerPhase.Instance(runtime, callerAssumptions, invokeUsages, mark, customCanonicalizer).apply(callerGraph);

// if (callerGraph.getNewNodes(mark).contains(Invoke.class)) {
// // TODO (chaeubl): invoke nodes might be created during canonicalization, which
// // is bad for the CF ordering of the invokes but we still have to handle it properly...
// }
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
            return new InliningUtil.InlineableMacroNode(macroNodeClass);
        } else {
            return buildGraph(method, invoke, assumptions);
        }
    }

    private StructuredGraph buildGraph(final ResolvedJavaMethod method, final Invoke invoke, final Assumptions assumptions) {
        final StructuredGraph newGraph;
        final boolean parseBytecodes;

        // TODO (chaeubl): copying the graph is only necessary if it is modified
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
                    // TODO (chaeubl): if args are not more concrete, inlining will not change the
                    // size of the graph
                    NodeInputList<ValueNode> args = invoke.callTarget().arguments();
                    for (LocalNode localNode : newGraph.getNodes(LocalNode.class).snapshot()) {
                        ValueNode arg = args.get(localNode.index());
                        if (arg.isConstant()) {
                            Constant constant = arg.asConstant();
                            newGraph.replaceFloating(localNode, ConstantNode.forConstant(constant, runtime, newGraph));
                        } else {
                            localNode.setStamp(localNode.stamp().join(arg.stamp()));
                        }
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

            if (graph != null) {
                computeProbabilities();
            }
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

            InlineInfo info = currentInvocation.callee();
            double inliningBonus = getInliningBonus(info);

            int hirSize = previousHIRSize(info);
            if (hirSize > GraalOptions.SmallCompiledGraphSize * inliningBonus) {
                return InliningUtil.logNotInlinedMethod(info, "too large HIR graph: %s", hirSize);
            }

            int nodes = determineNodeCount(info);
            if (nodes < GraalOptions.TrivialHighLevelGraphSize * inliningBonus) {
                return InliningUtil.logInlinedMethod(info, "trivial (nodes=%d)", nodes);
            }

            double maximumNodes = computeMaximumSize(currentInvocation.relevance(), (int) (GraalOptions.NormalHighLevelGraphSize * inliningBonus));
            if (nodes < maximumNodes) {
                return InliningUtil.logInlinedMethod(info, "relevance-based (relevance=%f, nodes=%d)", currentInvocation.relevance(), nodes);
            }

            // TODO (chaeubl): compute metric that is used to check if this method should be
            // inlined

            return InliningUtil.logNotInlinedMethod(info, "(relevance=%f, probability=%f, bonus=%f)", currentInvocation.relevance(), currentInvocation.probability(), inliningBonus);
        }

        @Override
        public boolean isWorthInlining(InlineInfo info, double probability, double relevance) {
            if (isIntrinsic(info)) {
                return InliningUtil.logInlinedMethod(info, "intrinsic");
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

            // TODO (chaeubl): we could do a shortcut here, i.e. if it resulted in something simple
            // before avoid building the graphs

            // @formatter:off
            // trivial
            //   few nodes
            //   linear and no invokes
            // leaf
            //   no invokes
            // normal
            //   many nodes
            //   no frequently executed invokes
            // complex
            //   many nodes
            //   frequently executed invokes
            // @formatter:on

            int nodes = determineNodeCount(info);
            if (nodes < GraalOptions.TrivialHighLevelGraphSize * inliningBonus) {
                return InliningUtil.logInlinedMethod(info, "trivial (nodes=%d)", nodes);
            }

            double invokes = determineInvokeProbability(info);
            if (GraalOptions.LimitInlinedInvokes > 0 && invokes > GraalOptions.LimitInlinedInvokes * inliningBonus) {
                return InliningUtil.logNotInlinedMethod(info, "invoke probability is too high (%f)", invokes);
            }

            double maximumNodes = computeMaximumSize(relevance, (int) (GraalOptions.NormalHighLevelGraphSize * inliningBonus));
            if (nodes < maximumNodes) {
                return InliningUtil.logInlinedMethod(info, "relevance-based (relevance=%f, nodes=%d)", relevance, nodes);
            }

            // TODO (chaeubl): compute metric that is used to check if this method should be inlined

            return InliningUtil.logNotInlinedMethod(info, "(relevance=%f, probability=%f, bonus=%f)", relevance, probability, inliningBonus);
        }

        private static int previousHIRSize(InlineInfo info) {
            int size = 0;
            for (int i = 0; i < info.numberOfMethods(); i++) {
                size += compiledMethodInfo(info.methodAt(i)).getHighLevelNodes();
            }
            return size;
        }

        private static int determineNodeCount(InlineInfo info) {
            int nodes = 0;
            for (int i = 0; i < info.numberOfMethods(); i++) {
                InlineableElement elem = info.inlineableElementAt(i);
                if (elem != null) {
                    nodes += elem.getNodeCount();
                }
            }
            return nodes;
        }

        private static double determineInvokeProbability(InlineInfo info) {
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

    public static class CompiledMethodInfo {

        private int highLevelNodes;
        private int midLevelNodes;
        private int lowLevelNodes;
        private int compiledCodeSize;
        private double summedUpProbabilityOfRemainingInvokes;
        private double maxProbabilityOfRemainingInvokes;
        private int numberOfRemainingInvokes;

        public CompiledMethodInfo() {
        }

        public int getHighLevelNodes() {
            return highLevelNodes;
        }

        public int getMidLevelNodes() {
            return midLevelNodes;
        }

        public int getLowLevelNodes() {
            return lowLevelNodes;
        }

        public int getCompiledCodeSize() {
            return compiledCodeSize;
        }

        public double getMaxProbabilityOfRemainingInvokes() {
            return maxProbabilityOfRemainingInvokes;
        }

        public double getSummedUpProbabilityOfRemainingInvokes() {
            return summedUpProbabilityOfRemainingInvokes;
        }

        public int getNumberOfRemainingInvokes() {
            return numberOfRemainingInvokes;
        }

        public void setHighLevelNodes(int highLevelNodes) {
            this.highLevelNodes = highLevelNodes;
        }

        public void setMidLevelNodes(int midLevelNodes) {
            this.midLevelNodes = midLevelNodes;
        }

        public void setLowLevelNodes(int lowLevelNodes) {
            this.lowLevelNodes = lowLevelNodes;
        }

        public void setMaxProbabilityOfRemainingInvokes(double maxProbabilityOfRemainingInvokes) {
            this.maxProbabilityOfRemainingInvokes = maxProbabilityOfRemainingInvokes;
        }

        public void setSummedUpProbabilityOfRemainingInvokes(double summedUpProbabilityOfRemainingInvokes) {
            this.summedUpProbabilityOfRemainingInvokes = summedUpProbabilityOfRemainingInvokes;
        }

        public void setCompiledCodeSize(int compiledCodeSize) {
            this.compiledCodeSize = compiledCodeSize;
        }

        public void setNumberOfRemainingInvokes(int numberOfRemainingInvokes) {
            this.numberOfRemainingInvokes = numberOfRemainingInvokes;
        }

        @Override
        public String toString() {
            return String.format("High: %d, Mid: %d, Low: %d, Compiled: %d, #Invokes: %d, SumOfInvokes: %f, MaxOfInvokes: %f", highLevelNodes, midLevelNodes, lowLevelNodes, compiledCodeSize,
                            numberOfRemainingInvokes, summedUpProbabilityOfRemainingInvokes, maxProbabilityOfRemainingInvokes);
        }
    }
}
