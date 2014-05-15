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
package com.oracle.graal.phases.common.inlining.walker;

import com.oracle.graal.api.code.Assumptions;
import com.oracle.graal.api.code.BailoutException;
import com.oracle.graal.api.meta.ResolvedJavaMethod;
import com.oracle.graal.compiler.common.GraalInternalError;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.DebugMetric;
import com.oracle.graal.graph.Graph;
import com.oracle.graal.graph.Node;
import com.oracle.graal.nodes.FixedNode;
import com.oracle.graal.nodes.Invoke;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.phases.common.CanonicalizerPhase;
import com.oracle.graal.phases.common.inlining.InliningUtil;
import com.oracle.graal.phases.common.inlining.info.InlineInfo;
import com.oracle.graal.phases.common.inlining.policy.InliningPolicy;
import com.oracle.graal.phases.tiers.HighTierContext;
import com.oracle.graal.phases.util.Providers;

import java.util.ArrayDeque;
import java.util.List;
import java.util.function.ToDoubleFunction;

import static com.oracle.graal.compiler.common.GraalOptions.OptCanonicalizer;

/**
 * Holds the data for building the callee graphs recursively: graphs and invocations (each
 * invocation can have multiple graphs).
 */
public class InliningData {

    private static final CallsiteHolder DUMMY_CALLSITE_HOLDER = new CallsiteHolder(null, 1.0, 1.0);
    // Metrics
    private static final DebugMetric metricInliningPerformed = Debug.metric("InliningPerformed");
    private static final DebugMetric metricInliningRuns = Debug.metric("InliningRuns");
    private static final DebugMetric metricInliningConsidered = Debug.metric("InliningConsidered");

    /**
     * Call hierarchy from outer most call (i.e., compilation unit) to inner most callee.
     */
    private final ArrayDeque<CallsiteHolder> graphQueue;
    private final ArrayDeque<MethodInvocation> invocationQueue;
    private final int maxMethodPerInlining;
    private final CanonicalizerPhase canonicalizer;
    private final InliningPolicy inliningPolicy;

    private int maxGraphs;

    public InliningData(StructuredGraph rootGraph, Assumptions rootAssumptions, int maxMethodPerInlining, CanonicalizerPhase canonicalizer, InliningPolicy inliningPolicy) {
        assert rootGraph != null;
        this.graphQueue = new ArrayDeque<>();
        this.invocationQueue = new ArrayDeque<>();
        this.maxMethodPerInlining = maxMethodPerInlining;
        this.canonicalizer = canonicalizer;
        this.inliningPolicy = inliningPolicy;
        this.maxGraphs = 1;

        invocationQueue.push(new MethodInvocation(null, rootAssumptions, 1.0, 1.0));
        pushGraph(rootGraph, 1.0, 1.0);
    }

    private void doInline(CallsiteHolder callerCallsiteHolder, MethodInvocation calleeInfo, Assumptions callerAssumptions, HighTierContext context) {
        StructuredGraph callerGraph = callerCallsiteHolder.graph();
        Graph.Mark markBeforeInlining = callerGraph.getMark();
        InlineInfo callee = calleeInfo.callee();
        try {
            try (Debug.Scope scope = Debug.scope("doInline", callerGraph)) {
                List<Node> invokeUsages = callee.invoke().asNode().usages().snapshot();
                callee.inline(new Providers(context), callerAssumptions);
                callerAssumptions.record(calleeInfo.assumptions());
                metricInliningRuns.increment();
                Debug.dump(callerGraph, "after %s", callee);

                if (OptCanonicalizer.getValue()) {
                    Graph.Mark markBeforeCanonicalization = callerGraph.getMark();
                    canonicalizer.applyIncremental(callerGraph, context, invokeUsages, markBeforeInlining);

                    // process invokes that are possibly created during canonicalization
                    for (Node newNode : callerGraph.getNewNodes(markBeforeCanonicalization)) {
                        if (newNode instanceof Invoke) {
                            callerCallsiteHolder.pushInvoke((Invoke) newNode);
                        }
                    }
                }

                callerCallsiteHolder.computeProbabilities();

                metricInliningPerformed.increment();
            }
        } catch (BailoutException bailout) {
            throw bailout;
        } catch (AssertionError | RuntimeException e) {
            throw new GraalInternalError(e).addContext(callee.toString());
        } catch (GraalInternalError e) {
            throw e.addContext(callee.toString());
        }
    }

    /**
     * @return true iff inlining was actually performed
     */
    private boolean tryToInline(ToDoubleFunction<FixedNode> probabilities, CallsiteHolder callerCallsiteHolder, MethodInvocation calleeInfo, MethodInvocation parentInvocation, int inliningDepth,
                    HighTierContext context) {
        InlineInfo callee = calleeInfo.callee();
        Assumptions callerAssumptions = parentInvocation.assumptions();
        metricInliningConsidered.increment();

        if (inliningPolicy.isWorthInlining(probabilities, context.getReplacements(), callee, inliningDepth, calleeInfo.probability(), calleeInfo.relevance(), true)) {
            doInline(callerCallsiteHolder, calleeInfo, callerAssumptions, context);
            return true;
        }

        if (context.getOptimisticOptimizations().devirtualizeInvokes()) {
            callee.tryToDevirtualizeInvoke(context.getMetaAccess(), callerAssumptions);
        }

        return false;
    }

    /**
     * Process the next invoke and enqueue all its graphs for processing.
     */
    void processNextInvoke(HighTierContext context) {
        CallsiteHolder callsiteHolder = currentGraph();
        Invoke invoke = callsiteHolder.popInvoke();
        MethodInvocation callerInvocation = currentInvocation();
        Assumptions parentAssumptions = callerInvocation.assumptions();
        InlineInfo info = InliningUtil.getInlineInfo(this, invoke, maxMethodPerInlining, context.getReplacements(), parentAssumptions, context.getOptimisticOptimizations());

        if (info != null) {
            double invokeProbability = callsiteHolder.invokeProbability(invoke);
            double invokeRelevance = callsiteHolder.invokeRelevance(invoke);
            MethodInvocation calleeInvocation = pushInvocation(info, parentAssumptions, invokeProbability, invokeRelevance);

            for (int i = 0; i < info.numberOfMethods(); i++) {
                InliningUtil.Inlineable elem = DepthSearchUtil.getInlineableElement(info.methodAt(i), info.invoke(), context.replaceAssumptions(calleeInvocation.assumptions()), canonicalizer);
                info.setInlinableElement(i, elem);
                if (elem instanceof InliningUtil.InlineableGraph) {
                    pushGraph(((InliningUtil.InlineableGraph) elem).getGraph(), invokeProbability * info.probabilityAt(i), invokeRelevance * info.relevanceAt(i));
                } else {
                    assert elem instanceof InliningUtil.InlineableMacroNode;
                    pushDummyGraph();
                }
            }
        }
    }

    public int graphCount() {
        return graphQueue.size();
    }

    private void pushGraph(StructuredGraph graph, double probability, double relevance) {
        assert graph != null;
        assert !contains(graph);
        graphQueue.push(new CallsiteHolder(graph, probability, relevance));
        assert graphQueue.size() <= maxGraphs;
    }

    private void pushDummyGraph() {
        graphQueue.push(DUMMY_CALLSITE_HOLDER);
    }

    public boolean hasUnprocessedGraphs() {
        return !graphQueue.isEmpty();
    }

    public CallsiteHolder currentGraph() {
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
        for (CallsiteHolder g : graphQueue) {
            result[i++] = g.method();
        }
        return result;
    }

    public MethodInvocation currentInvocation() {
        return invocationQueue.peekFirst();
    }

    private MethodInvocation pushInvocation(InlineInfo info, Assumptions assumptions, double probability, double relevance) {
        MethodInvocation methodInvocation = new MethodInvocation(info, new Assumptions(assumptions.useOptimisticAssumptions()), probability, relevance);
        invocationQueue.addFirst(methodInvocation);
        maxGraphs += info.numberOfMethods();
        assert graphQueue.size() <= maxGraphs;
        return methodInvocation;
    }

    public void popInvocation() {
        maxGraphs -= invocationQueue.peekFirst().callee().numberOfMethods();
        assert graphQueue.size() <= maxGraphs;
        invocationQueue.removeFirst();
    }

    public int countRecursiveInlining(ResolvedJavaMethod method) {
        int count = 0;
        for (CallsiteHolder callsiteHolder : graphQueue) {
            if (method.equals(callsiteHolder.method())) {
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
        for (CallsiteHolder graph : graphQueue) {
            result.append(graph.graph());
            result.append("; ");
        }

        return result.toString();
    }

    private boolean contains(StructuredGraph graph) {
        for (CallsiteHolder info : graphQueue) {
            if (info.graph() == graph) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return true iff inlining was actually performed
     */
    public boolean moveForward(HighTierContext context, ToDoubleFunction<FixedNode> probabilities) {

        final MethodInvocation currentInvocation = currentInvocation();

        final boolean backtrack = (!currentInvocation.isRoot() && !inliningPolicy.isWorthInlining(probabilities, context.getReplacements(), currentInvocation.callee(), inliningDepth(),
                        currentInvocation.probability(), currentInvocation.relevance(), false));
        if (backtrack) {
            int remainingGraphs = currentInvocation.totalGraphs() - currentInvocation.processedGraphs();
            assert remainingGraphs > 0;
            popGraphs(remainingGraphs);
            popInvocation();
            return false;
        }

        final boolean delve = currentGraph().hasRemainingInvokes() && inliningPolicy.continueInlining(currentGraph().graph());
        if (delve) {
            processNextInvoke(context);
            return false;
        }

        popGraph();
        if (currentInvocation.isRoot()) {
            return false;
        }

        // try to inline
        assert currentInvocation.callee().invoke().asNode().isAlive();
        currentInvocation.incrementProcessedGraphs();
        if (currentInvocation.processedGraphs() == currentInvocation.totalGraphs()) {
            popInvocation();
            final MethodInvocation parentInvoke = currentInvocation();
            try (Debug.Scope s = Debug.scope("Inlining", inliningContext())) {
                return tryToInline(probabilities, currentGraph(), currentInvocation, parentInvoke, inliningDepth() + 1, context);
            } catch (Throwable e) {
                throw Debug.handle(e);
            }
        }

        return false;
    }
}
