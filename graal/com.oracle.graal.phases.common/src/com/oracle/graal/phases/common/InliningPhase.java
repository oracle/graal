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

import static com.oracle.graal.phases.GraalOptions.*;
import static com.oracle.graal.phases.common.InliningPhase.Options.*;

import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.graph.*;
import com.oracle.graal.graph.Graph.Mark;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.nodes.type.*;
import com.oracle.graal.nodes.util.*;
import com.oracle.graal.options.*;
import com.oracle.graal.phases.common.InliningUtil.InlineInfo;
import com.oracle.graal.phases.common.InliningUtil.Inlineable;
import com.oracle.graal.phases.common.InliningUtil.InlineableGraph;
import com.oracle.graal.phases.common.InliningUtil.InlineableMacroNode;
import com.oracle.graal.phases.common.InliningUtil.InliningPolicy;
import com.oracle.graal.phases.graph.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.phases.util.*;

public class InliningPhase extends AbstractInliningPhase {

    static class Options {

        // @formatter:off
        @Option(help = "Unconditionally inline intrinsics")
        public static final OptionValue<Boolean> AlwaysInlineIntrinsics = new OptionValue<>(false);
        // @formatter:on
    }

    private final InliningPolicy inliningPolicy;
    private final CanonicalizerPhase canonicalizer;

    private int inliningCount;
    private int maxMethodPerInlining = Integer.MAX_VALUE;

    // Metrics
    private static final DebugMetric metricInliningPerformed = Debug.metric("InliningPerformed");
    private static final DebugMetric metricInliningConsidered = Debug.metric("InliningConsidered");
    private static final DebugMetric metricInliningStoppedByMaxDesiredSize = Debug.metric("InliningStoppedByMaxDesiredSize");
    private static final DebugMetric metricInliningRuns = Debug.metric("InliningRuns");

    public InliningPhase(CanonicalizerPhase canonicalizer) {
        this(new GreedyInliningPolicy(null), canonicalizer);
    }

    public InliningPhase(Map<Invoke, Double> hints, CanonicalizerPhase canonicalizer) {
        this(new GreedyInliningPolicy(hints), canonicalizer);
    }

    public InliningPhase(InliningPolicy policy, CanonicalizerPhase canonicalizer) {
        this.inliningPolicy = policy;
        this.canonicalizer = canonicalizer;
    }

    public void setMaxMethodsPerInlining(int max) {
        maxMethodPerInlining = max;
    }

    public int getInliningCount() {
        return inliningCount;
    }

    public static void storeStatisticsAfterLowTier(StructuredGraph graph) {
        ResolvedJavaMethod method = graph.method();
        if (method != null) {
            CompiledMethodInfo info = compiledMethodInfo(graph.method());
            info.setLowLevelNodeCount(graph.getNodeCount());
        }
    }

    @Override
    protected void run(final StructuredGraph graph, final HighTierContext context) {
        final InliningData data = new InliningData(graph, context.getAssumptions());

        while (data.hasUnprocessedGraphs()) {
            final MethodInvocation currentInvocation = data.currentInvocation();
            GraphInfo graphInfo = data.currentGraph();
            if (!currentInvocation.isRoot() &&
                            !inliningPolicy.isWorthInlining(context.getReplacements(), currentInvocation.callee(), data.inliningDepth(), currentInvocation.probability(),
                                            currentInvocation.relevance(), false)) {
                int remainingGraphs = currentInvocation.totalGraphs() - currentInvocation.processedGraphs();
                assert remainingGraphs > 0;
                data.popGraphs(remainingGraphs);
                data.popInvocation();
            } else if (graphInfo.hasRemainingInvokes() && inliningPolicy.continueInlining(graphInfo.graph())) {
                processNextInvoke(data, graphInfo, context);
            } else {
                data.popGraph();
                if (!currentInvocation.isRoot()) {
                    assert currentInvocation.callee().invoke().asNode().isAlive();
                    currentInvocation.incrementProcessedGraphs();
                    if (currentInvocation.processedGraphs() == currentInvocation.totalGraphs()) {
                        data.popInvocation();
                        final MethodInvocation parentInvoke = data.currentInvocation();
                        try (Scope s = Debug.scope("Inlining", data.inliningContext())) {
                            tryToInline(data.currentGraph(), currentInvocation, parentInvoke, data.inliningDepth() + 1, context);
                        } catch (Throwable e) {
                            throw Debug.handle(e);
                        }
                    }
                }
            }
        }

        assert data.inliningDepth() == 0;
        assert data.graphCount() == 0;
    }

    /**
     * Process the next invoke and enqueue all its graphs for processing.
     */
    private void processNextInvoke(InliningData data, GraphInfo graphInfo, HighTierContext context) {
        Invoke invoke = graphInfo.popInvoke();
        MethodInvocation callerInvocation = data.currentInvocation();
        Assumptions parentAssumptions = callerInvocation.assumptions();
        InlineInfo info = InliningUtil.getInlineInfo(data, invoke, maxMethodPerInlining, context.getReplacements(), parentAssumptions, context.getOptimisticOptimizations());

        if (info != null) {
            double invokeProbability = graphInfo.invokeProbability(invoke);
            double invokeRelevance = graphInfo.invokeRelevance(invoke);
            MethodInvocation calleeInvocation = data.pushInvocation(info, parentAssumptions, invokeProbability, invokeRelevance);

            for (int i = 0; i < info.numberOfMethods(); i++) {
                Inlineable elem = getInlineableElement(info.methodAt(i), info.invoke(), context.replaceAssumptions(calleeInvocation.assumptions()));
                info.setInlinableElement(i, elem);
                if (elem instanceof InlineableGraph) {
                    data.pushGraph(((InlineableGraph) elem).getGraph(), invokeProbability * info.probabilityAt(i), invokeRelevance * info.relevanceAt(i));
                } else {
                    assert elem instanceof InlineableMacroNode;
                    data.pushDummyGraph();
                }
            }
        }
    }

    private void tryToInline(GraphInfo callerGraphInfo, MethodInvocation calleeInfo, MethodInvocation parentInvocation, int inliningDepth, HighTierContext context) {
        InlineInfo callee = calleeInfo.callee();
        Assumptions callerAssumptions = parentInvocation.assumptions();

        if (inliningPolicy.isWorthInlining(context.getReplacements(), callee, inliningDepth, calleeInfo.probability(), calleeInfo.relevance(), true)) {
            doInline(callerGraphInfo, calleeInfo, callerAssumptions, context);
        } else if (context.getOptimisticOptimizations().devirtualizeInvokes()) {
            callee.tryToDevirtualizeInvoke(context.getMetaAccess(), callerAssumptions);
        }
        metricInliningConsidered.increment();
    }

    private void doInline(GraphInfo callerGraphInfo, MethodInvocation calleeInfo, Assumptions callerAssumptions, HighTierContext context) {
        StructuredGraph callerGraph = callerGraphInfo.graph();
        Mark markBeforeInlining = callerGraph.getMark();
        InlineInfo callee = calleeInfo.callee();
        try {
            List<Node> invokeUsages = callee.invoke().asNode().usages().snapshot();
            callee.inline(new Providers(context), callerAssumptions);
            callerAssumptions.record(calleeInfo.assumptions());
            metricInliningRuns.increment();
            Debug.dump(callerGraph, "after %s", callee);

            if (OptCanonicalizer.getValue()) {
                Mark markBeforeCanonicalization = callerGraph.getMark();
                canonicalizer.applyIncremental(callerGraph, context, invokeUsages, markBeforeInlining);

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

    private Inlineable getInlineableElement(final ResolvedJavaMethod method, Invoke invoke, HighTierContext context) {
        Class<? extends FixedWithNextNode> macroNodeClass = InliningUtil.getMacroNodeClass(context.getReplacements(), method);
        if (macroNodeClass != null) {
            return new InlineableMacroNode(macroNodeClass);
        } else {
            return new InlineableGraph(buildGraph(method, invoke, context));
        }
    }

    private StructuredGraph buildGraph(final ResolvedJavaMethod method, final Invoke invoke, final HighTierContext context) {
        final StructuredGraph newGraph;
        final boolean parseBytecodes;

        // TODO (chaeubl): copying the graph is only necessary if it is modified or if it contains
        // any invokes
        StructuredGraph intrinsicGraph = InliningUtil.getIntrinsicGraph(context.getReplacements(), method);
        if (intrinsicGraph != null) {
            newGraph = intrinsicGraph.copy();
            parseBytecodes = false;
        } else {
            StructuredGraph cachedGraph = getCachedGraph(method, context);
            if (cachedGraph != null) {
                newGraph = cachedGraph.copy();
                parseBytecodes = false;
            } else {
                newGraph = new StructuredGraph(method);
                parseBytecodes = true;
            }
        }

        try (Scope s = Debug.scope("InlineGraph", newGraph)) {
            if (parseBytecodes) {
                parseBytecodes(newGraph, context);
            }

            boolean callerHasMoreInformationAboutArguments = false;
            NodeInputList<ValueNode> args = invoke.callTarget().arguments();
            for (ParameterNode param : newGraph.getNodes(ParameterNode.class).snapshot()) {
                ValueNode arg = args.get(param.index());
                if (arg.isConstant()) {
                    Constant constant = arg.asConstant();
                    newGraph.replaceFloating(param, ConstantNode.forConstant(constant, context.getMetaAccess(), newGraph));
                    callerHasMoreInformationAboutArguments = true;
                } else {
                    Stamp joinedStamp = param.stamp().join(arg.stamp());
                    if (joinedStamp != null && !joinedStamp.equals(param.stamp())) {
                        param.setStamp(joinedStamp);
                        callerHasMoreInformationAboutArguments = true;
                    }
                }
            }

            if (!callerHasMoreInformationAboutArguments) {
                // TODO (chaeubl): if args are not more concrete, inlining should be avoided
                // in most cases or we could at least use the previous graph size + invoke
                // probability to check the inlining
            }

            if (OptCanonicalizer.getValue()) {
                canonicalizer.apply(newGraph, context);
            }

            return newGraph;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    private static StructuredGraph getCachedGraph(ResolvedJavaMethod method, HighTierContext context) {
        if (CacheGraphs.getValue() && context.getGraphCache() != null) {
            StructuredGraph cachedGraph = context.getGraphCache().get(method);
            if (cachedGraph != null) {
                return cachedGraph;
            }
        }
        return null;
    }

    private StructuredGraph parseBytecodes(StructuredGraph newGraph, HighTierContext context) {
        boolean hasMatureProfilingInfo = newGraph.method().getProfilingInfo().isMature();

        if (context.getGraphBuilderSuite() != null) {
            context.getGraphBuilderSuite().apply(newGraph, context);
        }
        assert newGraph.start().next() != null : "graph needs to be populated during PhasePosition.AFTER_PARSING";

        new DeadCodeEliminationPhase().apply(newGraph);

        if (OptCanonicalizer.getValue()) {
            canonicalizer.apply(newGraph, context);
        }

        if (CacheGraphs.getValue() && context.getGraphCache() != null) {
            context.getGraphCache().put(newGraph.copy(), hasMatureProfilingInfo);
        }
        return newGraph;
    }

    private static synchronized CompiledMethodInfo compiledMethodInfo(ResolvedJavaMethod m) {
        CompiledMethodInfo info = (CompiledMethodInfo) m.getCompilerStorage().get(CompiledMethodInfo.class);
        if (info == null) {
            info = new CompiledMethodInfo();
            m.getCompilerStorage().put(CompiledMethodInfo.class, info);
        }
        return info;
    }

    private abstract static class AbstractInliningPolicy implements InliningPolicy {

        protected final Map<Invoke, Double> hints;

        public AbstractInliningPolicy(Map<Invoke, Double> hints) {
            this.hints = hints;
        }

        protected double computeMaximumSize(double relevance, int configuredMaximum) {
            double inlineRatio = Math.min(RelevanceCapForInlining.getValue(), relevance);
            return configuredMaximum * inlineRatio;
        }

        protected double getInliningBonus(InlineInfo info) {
            if (hints != null && hints.containsKey(info.invoke())) {
                return hints.get(info.invoke());
            }
            return 1;
        }

        protected boolean isIntrinsic(Replacements replacements, InlineInfo info) {
            if (AlwaysInlineIntrinsics.getValue()) {
                return onlyIntrinsics(replacements, info);
            } else {
                return onlyForcedIntrinsics(replacements, info);
            }
        }

        private static boolean onlyIntrinsics(Replacements replacements, InlineInfo info) {
            for (int i = 0; i < info.numberOfMethods(); i++) {
                if (!InliningUtil.canIntrinsify(replacements, info.methodAt(i))) {
                    return false;
                }
            }
            return true;
        }

        private static boolean onlyForcedIntrinsics(Replacements replacements, InlineInfo info) {
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

        protected static int previousLowLevelGraphSize(InlineInfo info) {
            int size = 0;
            for (int i = 0; i < info.numberOfMethods(); i++) {
                size += compiledMethodInfo(info.methodAt(i)).lowLevelNodeCount();
            }
            return size;
        }

        protected static int determineNodeCount(InlineInfo info) {
            int nodes = 0;
            for (int i = 0; i < info.numberOfMethods(); i++) {
                Inlineable elem = info.inlineableElementAt(i);
                if (elem != null) {
                    nodes += elem.getNodeCount();
                }
            }
            return nodes;
        }

        protected static double determineInvokeProbability(InlineInfo info) {
            double invokeProbability = 0;
            for (int i = 0; i < info.numberOfMethods(); i++) {
                Inlineable callee = info.inlineableElementAt(i);
                Iterable<Invoke> invokes = callee.getInvokes();
                if (invokes.iterator().hasNext()) {
                    NodesToDoubles nodeProbabilities = new ComputeProbabilityClosure(((InlineableGraph) callee).getGraph()).apply();
                    for (Invoke invoke : invokes) {
                        invokeProbability += nodeProbabilities.get(invoke.asNode());
                    }
                }
            }
            return invokeProbability;
        }
    }

    public static class GreedyInliningPolicy extends AbstractInliningPolicy {

        public GreedyInliningPolicy(Map<Invoke, Double> hints) {
            super(hints);
        }

        public boolean continueInlining(StructuredGraph currentGraph) {
            if (currentGraph.getNodeCount() >= MaximumDesiredSize.getValue()) {
                InliningUtil.logInliningDecision("inlining is cut off by MaximumDesiredSize");
                metricInliningStoppedByMaxDesiredSize.increment();
                return false;
            }
            return true;
        }

        @Override
        public boolean isWorthInlining(Replacements replacements, InlineInfo info, int inliningDepth, double probability, double relevance, boolean fullyProcessed) {
            if (InlineEverything.getValue()) {
                return InliningUtil.logInlinedMethod(info, inliningDepth, fullyProcessed, "inline everything");
            }

            if (isIntrinsic(replacements, info)) {
                return InliningUtil.logInlinedMethod(info, inliningDepth, fullyProcessed, "intrinsic");
            }

            double inliningBonus = getInliningBonus(info);
            int nodes = determineNodeCount(info);
            int lowLevelGraphSize = previousLowLevelGraphSize(info);

            if (SmallCompiledLowLevelGraphSize.getValue() > 0 && lowLevelGraphSize > SmallCompiledLowLevelGraphSize.getValue() * inliningBonus) {
                return InliningUtil.logNotInlinedMethod(info, inliningDepth, "too large previous low-level graph (low-level-nodes: %d, relevance=%f, probability=%f, bonus=%f, nodes=%d)",
                                lowLevelGraphSize, relevance, probability, inliningBonus, nodes);
            }

            if (nodes < TrivialInliningSize.getValue() * inliningBonus) {
                return InliningUtil.logInlinedMethod(info, inliningDepth, fullyProcessed, "trivial (relevance=%f, probability=%f, bonus=%f, nodes=%d)", relevance, probability, inliningBonus, nodes);
            }

            /*
             * TODO (chaeubl): invoked methods that are on important paths but not yet compiled ->
             * will be compiled anyways and it is likely that we are the only caller... might be
             * useful to inline those methods but increases bootstrap time (maybe those methods are
             * also getting queued in the compilation queue concurrently)
             */
            double invokes = determineInvokeProbability(info);
            if (LimitInlinedInvokes.getValue() > 0 && fullyProcessed && invokes > LimitInlinedInvokes.getValue() * inliningBonus) {
                return InliningUtil.logNotInlinedMethod(info, inliningDepth, "callee invoke probability is too high (invokeP=%f, relevance=%f, probability=%f, bonus=%f, nodes=%d)", invokes,
                                relevance, probability, inliningBonus, nodes);
            }

            double maximumNodes = computeMaximumSize(relevance, (int) (MaximumInliningSize.getValue() * inliningBonus));
            if (nodes <= maximumNodes) {
                return InliningUtil.logInlinedMethod(info, inliningDepth, fullyProcessed, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d <= %f)", relevance, probability,
                                inliningBonus, nodes, maximumNodes);
            }

            return InliningUtil.logNotInlinedMethod(info, inliningDepth, "relevance-based (relevance=%f, probability=%f, bonus=%f, nodes=%d > %f)", relevance, probability, inliningBonus, nodes,
                            maximumNodes);
        }
    }

    public static final class InlineEverythingPolicy implements InliningPolicy {

        public boolean continueInlining(StructuredGraph graph) {
            if (graph.getNodeCount() >= MaximumDesiredSize.getValue()) {
                throw new BailoutException("Inline all calls failed. The resulting graph is too large.");
            }
            return true;
        }

        public boolean isWorthInlining(Replacements replacements, InlineInfo info, int inliningDepth, double probability, double relevance, boolean fullyProcessed) {
            return true;
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

        public LinkedList<Invoke> apply() {
            LinkedList<Invoke> invokes = new LinkedList<>();
            FixedNode current;
            forcedQueue(start);

            while ((current = nextQueuedNode()) != null) {
                assert current.isAlive();

                if (current instanceof Invoke && ((Invoke) current).callTarget() instanceof MethodCallTargetNode) {
                    if (current != start) {
                        invokes.addLast((Invoke) current);
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

        private void queueMerge(AbstractEndNode end) {
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

        private static final GraphInfo DummyGraphInfo = new GraphInfo(null, new LinkedList<Invoke>(), 1.0, 1.0);

        /**
         * Call hierarchy from outer most call (i.e., compilation unit) to inner most callee.
         */
        private final ArrayDeque<GraphInfo> graphQueue;
        private final ArrayDeque<MethodInvocation> invocationQueue;

        private int maxGraphs;

        public InliningData(StructuredGraph rootGraph, Assumptions rootAssumptions) {
            this.graphQueue = new ArrayDeque<>();
            this.invocationQueue = new ArrayDeque<>();
            this.maxGraphs = 1;

            invocationQueue.push(new MethodInvocation(null, rootAssumptions, 1.0, 1.0));
            pushGraph(rootGraph, 1.0, 1.0);
        }

        public int graphCount() {
            return graphQueue.size();
        }

        public void pushGraph(StructuredGraph graph, double probability, double relevance) {
            assert !contains(graph);
            NodeBitMap visitedFixedNodes = graph.createNodeBitMap();
            LinkedList<Invoke> invokes = new InliningIterator(graph.start(), visitedFixedNodes).apply();
            assert invokes.size() == count(graph.getInvokes());
            graphQueue.push(new GraphInfo(graph, invokes, probability, relevance));
            assert graphQueue.size() <= maxGraphs;
        }

        public void pushDummyGraph() {
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

        public void popGraphs(int count) {
            assert count >= 0;
            for (int i = 0; i < count; i++) {
                graphQueue.pop();
            }
        }

        private static final Object[] NO_CONTEXT = {};

        /**
         * Gets the call hierarchy of this inlining from outer most call to inner most callee.
         */
        public Object[] inliningContext() {
            if (!Debug.isDumpEnabled()) {
                return NO_CONTEXT;
            }
            Object[] result = new Object[graphQueue.size()];
            int i = 0;
            for (GraphInfo g : graphQueue) {
                result[i++] = g.graph.method();
            }
            return result;
        }

        public MethodInvocation currentInvocation() {
            return invocationQueue.peekFirst();
        }

        public MethodInvocation pushInvocation(InlineInfo info, Assumptions assumptions, double probability, double relevance) {
            MethodInvocation methodInvocation = new MethodInvocation(info, new Assumptions(assumptions.useOptimisticAssumptions()), probability, relevance);
            invocationQueue.addFirst(methodInvocation);
            maxGraphs += info.numberOfMethods();
            assert graphQueue.size() <= maxGraphs;
            return methodInvocation;
        }

        public void popInvocation() {
            maxGraphs -= invocationQueue.peekFirst().callee.numberOfMethods();
            assert graphQueue.size() <= maxGraphs;
            invocationQueue.removeFirst();
        }

        public int countRecursiveInlining(ResolvedJavaMethod method) {
            int count = 0;
            for (GraphInfo graphInfo : graphQueue) {
                if (method.equals(graphInfo.method())) {
                    count++;
                }
            }
            return count;
        }

        public int inliningDepth() {
            assert invocationQueue.size() > 0;
            return invocationQueue.size() - 1;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder("Invocations: ");

            for (MethodInvocation invocation : invocationQueue) {
                if (invocation.callee() != null) {
                    result.append(invocation.callee().numberOfMethods());
                    result.append("x ");
                    result.append(invocation.callee().invoke());
                    result.append("; ");
                }
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
            assert processedGraphs <= callee.numberOfMethods();
        }

        public int processedGraphs() {
            assert processedGraphs <= callee.numberOfMethods();
            return processedGraphs;
        }

        public int totalGraphs() {
            return callee.numberOfMethods();
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

        public boolean isRoot() {
            return callee == null;
        }

        @Override
        public String toString() {
            if (isRoot()) {
                return "<root>";
            }
            CallTargetNode callTarget = callee.invoke().callTarget();
            if (callTarget instanceof MethodCallTargetNode) {
                ResolvedJavaMethod calleeMethod = ((MethodCallTargetNode) callTarget).targetMethod();
                return MetaUtil.format("Invoke#%H.%n(%p)", calleeMethod);
            } else {
                return "Invoke#" + callTarget.targetName();
            }
        }
    }

    /**
     * Information about a graph that will potentially be inlined. This includes tracking the
     * invocations in graph that will subject to inlining themselves.
     */
    private static class GraphInfo {

        private final StructuredGraph graph;
        private final LinkedList<Invoke> remainingInvokes;
        private final double probability;
        private final double relevance;

        private NodesToDoubles nodeProbabilities;
        private NodesToDoubles nodeRelevance;

        public GraphInfo(StructuredGraph graph, LinkedList<Invoke> invokes, double probability, double relevance) {
            this.graph = graph;
            this.remainingInvokes = invokes;
            this.probability = probability;
            this.relevance = relevance;

            if (graph != null) {
                computeProbabilities();
            }
        }

        /**
         * Gets the method associated with the {@linkplain #graph() graph} represented by this
         * object.
         */
        public ResolvedJavaMethod method() {
            return graph.method();
        }

        public boolean hasRemainingInvokes() {
            return !remainingInvokes.isEmpty();
        }

        /**
         * The graph about which this object contains inlining information.
         */
        public StructuredGraph graph() {
            return graph;
        }

        public Invoke popInvoke() {
            return remainingInvokes.removeFirst();
        }

        public void pushInvoke(Invoke invoke) {
            remainingInvokes.push(invoke);
        }

        public void computeProbabilities() {
            nodeProbabilities = new ComputeProbabilityClosure(graph).apply();
            nodeRelevance = new ComputeInliningRelevanceClosure(graph, nodeProbabilities).apply();
        }

        public double invokeProbability(Invoke invoke) {
            return probability * nodeProbabilities.get(invoke.asNode());
        }

        public double invokeRelevance(Invoke invoke) {
            return Math.min(CapInheritedRelevance.getValue(), relevance) * nodeRelevance.get(invoke.asNode());
        }

        @Override
        public String toString() {
            return (graph != null ? MetaUtil.format("%H.%n(%p)", method()) : "<null method>") + remainingInvokes;
        }
    }

    private static class CompiledMethodInfo {

        private int lowLevelNodes;

        public CompiledMethodInfo() {
        }

        public int lowLevelNodeCount() {
            return lowLevelNodes;
        }

        public void setLowLevelNodeCount(int lowLevelNodes) {
            this.lowLevelNodes = lowLevelNodes;
        }

    }
}
