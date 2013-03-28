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

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.common.CanonicalizerPhase.CustomCanonicalizer;
import com.oracle.graal.phases.common.InliningUtil.InlineInfo;
import com.oracle.graal.phases.common.InliningUtil.InliningCallback;
import com.oracle.graal.phases.common.InliningUtil.InliningPolicy;

public class InliningPhase extends Phase implements InliningCallback {

    /*
     * - Detect method which only call another method with some parameters set to constants: void
     * foo(a) -> void foo(a, b) -> void foo(a, b, c) ... These should not be taken into account when
     * determining inlining depth. - honor the result of overrideInliningDecision(0, caller,
     * invoke.bci, method, true);
     */

    private final PhasePlan plan;

    private final GraalCodeCacheProvider runtime;
    private final Assumptions assumptions;
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

    public InliningPhase(GraalCodeCacheProvider runtime, Map<Invoke, Double> hints, Assumptions assumptions, GraphCache cache, PhasePlan plan, OptimisticOptimizations optimisticOpts) {
        this(runtime, assumptions, cache, plan, createInliningPolicy(runtime, assumptions, optimisticOpts, hints), optimisticOpts);
    }

    public void setCustomCanonicalizer(CustomCanonicalizer customCanonicalizer) {
        this.customCanonicalizer = customCanonicalizer;
    }

    public void setMaxMethodsPerInlining(int max) {
        maxMethodPerInlining = max;
    }

    public InliningPhase(GraalCodeCacheProvider runtime, Assumptions assumptions, GraphCache cache, PhasePlan plan, InliningPolicy inliningPolicy, OptimisticOptimizations optimisticOpts) {
        this.runtime = runtime;
        this.assumptions = assumptions;
        this.cache = cache;
        this.plan = plan;
        this.inliningPolicy = inliningPolicy;
        this.optimisticOpts = optimisticOpts;
    }

    public int getInliningCount() {
        return inliningCount;
    }

    @Override
    protected void run(final StructuredGraph graph) {
        inliningPolicy.initialize(graph);

        while (inliningPolicy.continueInlining(graph)) {
            final InlineInfo candidate = inliningPolicy.next();

            if (candidate != null) {
                boolean isWorthInlining = inliningPolicy.isWorthInlining(candidate);
                isWorthInlining &= candidate.numberOfMethods() <= maxMethodPerInlining;

                metricInliningConsidered.increment();
                if (isWorthInlining) {
                    int mark = graph.getMark();
                    try {
                        List<Node> invokeUsages = candidate.invoke().node().usages().snapshot();
                        candidate.inline(graph, runtime, this, assumptions);
                        Debug.dump(graph, "after %s", candidate);
                        Iterable<Node> newNodes = graph.getNewNodes(mark);
                        inliningPolicy.scanInvokes(newNodes);
                        if (GraalOptions.OptCanonicalizer) {
                            new CanonicalizerPhase(runtime, assumptions, invokeUsages, mark, customCanonicalizer).apply(graph);
                        }
                        inliningCount++;
                        metricInliningPerformed.increment();
                    } catch (BailoutException bailout) {
                        throw bailout;
                    } catch (AssertionError e) {
                        throw new GraalInternalError(e).addContext(candidate.toString());
                    } catch (RuntimeException e) {
                        throw new GraalInternalError(e).addContext(candidate.toString());
                    } catch (GraalInternalError e) {
                        throw e.addContext(candidate.toString());
                    }
                } else if (optimisticOpts.devirtualizeInvokes()) {
                    candidate.tryToDevirtualizeInvoke(graph, runtime, assumptions);
                }
            }
        }
    }

    @Override
    public StructuredGraph buildGraph(final ResolvedJavaMethod method) {
        metricInliningRuns.increment();
        if (GraalOptions.CacheGraphs && cache != null) {
            StructuredGraph cachedGraph = cache.get(method);
            if (cachedGraph != null) {
                return cachedGraph;
            }
        }
        final StructuredGraph newGraph = new StructuredGraph(method);
        return Debug.scope("InlineGraph", newGraph, new Callable<StructuredGraph>() {

            @Override
            public StructuredGraph call() throws Exception {
                if (plan != null) {
                    plan.runPhases(PhasePosition.AFTER_PARSING, newGraph);
                }
                assert newGraph.start().next() != null : "graph needs to be populated during PhasePosition.AFTER_PARSING";

                if (GraalOptions.ProbabilityAnalysis) {
                    new DeadCodeEliminationPhase().apply(newGraph);
                    new ComputeProbabilityPhase().apply(newGraph);
                }
                if (GraalOptions.OptCanonicalizer) {
                    new CanonicalizerPhase(runtime, assumptions).apply(newGraph);
                }
                if (GraalOptions.CullFrameStates) {
                    new CullFrameStatesPhase().apply(newGraph);
                }
                if (GraalOptions.CacheGraphs && cache != null) {
                    cache.put(newGraph);
                }
                return newGraph;
            }
        });
    }

    private interface InliningDecision {

        boolean isWorthInlining(InlineInfo info);
    }

    private static class GreedySizeBasedInliningDecision implements InliningDecision {

        private final GraalCodeCacheProvider runtime;
        private final Map<Invoke, Double> hints;

        public GreedySizeBasedInliningDecision(GraalCodeCacheProvider runtime, Map<Invoke, Double> hints) {
            this.runtime = runtime;
            this.hints = hints;
        }

        @Override
        public boolean isWorthInlining(InlineInfo info) {
            assert GraalOptions.ProbabilityAnalysis;
            /*
             * TODO (chaeubl): invoked methods that are on important paths but not yet compiled ->
             * will be compiled anyways and it is likely that we are the only caller... might be
             * useful to inline those methods but increases bootstrap time (maybe those methods are
             * also getting queued in the compilation queue concurrently)
             */

            if (GraalOptions.AlwaysInlineIntrinsics && onlyIntrinsics(info)) {
                return InliningUtil.logInlinedMethod(info, "intrinsic");
            }

            double bonus = 1;
            if (hints != null && hints.containsKey(info.invoke())) {
                bonus = hints.get(info.invoke());
            }

            int bytecodeSize = (int) (bytecodeCodeSize(info) / bonus);
            int complexity = (int) (compilationComplexity(info) / bonus);
            int compiledCodeSize = (int) (compiledCodeSize(info) / bonus);
            double relevance = info.invoke().inliningRelevance();

            /*
             * as long as the compiled code size is small enough (or the method was not yet
             * compiled), we can do a pretty general inlining that suits most situations
             */
            if (compiledCodeSize < GraalOptions.SmallCompiledCodeSize) {
                if (isTrivialInlining(bytecodeSize, complexity, compiledCodeSize)) {
                    return InliningUtil.logInlinedMethod(info, "trivial (bytecodes=%d, complexity=%d, codeSize=%d)", bytecodeSize, complexity, compiledCodeSize);
                }

                if (canInlineRelevanceBased(relevance, bytecodeSize, complexity, compiledCodeSize)) {
                    return InliningUtil.logInlinedMethod(info, "relevance-based (relevance=%f, bytecodes=%d, complexity=%d, codeSize=%d)", relevance, bytecodeSize, complexity, compiledCodeSize);
                }
            }

            /*
             * the normal inlining did not fit this invoke, so check if we have any reason why we
             * should still do the inlining
             */
            double probability = info.invoke().probability();
            int transferredValues = numberOfTransferredValues(info);
            int invokeUsages = countInvokeUsages(info);
            int moreSpecificArguments = countMoreSpecificArgumentInfo(info);
            int level = info.level();

            // TODO (chaeubl): compute metric that is used to check if this method should be inlined

            return InliningUtil.logNotInlinedMethod(info,
                            "(relevance=%f, bytecodes=%d, complexity=%d, codeSize=%d, probability=%f, transferredValues=%d, invokeUsages=%d, moreSpecificArguments=%d, level=%d, bonus=%f)", relevance,
                            bytecodeSize, complexity, compiledCodeSize, probability, transferredValues, invokeUsages, moreSpecificArguments, level, bonus);
        }

        private static boolean isTrivialInlining(int bytecodeSize, int complexity, int compiledCodeSize) {
            return bytecodeSize < GraalOptions.TrivialBytecodeSize || complexity < GraalOptions.TrivialComplexity || compiledCodeSize > 0 && compiledCodeSize < GraalOptions.TrivialCompiledCodeSize;
        }

        private static boolean canInlineRelevanceBased(double relevance, int bytecodeSize, int complexity, int compiledCodeSize) {
            return bytecodeSize < computeMaximumSize(relevance, GraalOptions.NormalBytecodeSize) || complexity < computeMaximumSize(relevance, GraalOptions.NormalComplexity) || compiledCodeSize > 0 &&
                            compiledCodeSize < computeMaximumSize(relevance, GraalOptions.NormalCompiledCodeSize);
        }

        private static double computeMaximumSize(double relevance, int configuredMaximum) {
            double inlineRatio = Math.min(GraalOptions.RelevanceCapForInlining, relevance);
            return configuredMaximum * inlineRatio;
        }

        private static int numberOfTransferredValues(InlineInfo info) {
            Signature signature = info.invoke().methodCallTarget().targetMethod().getSignature();
            int transferredValues = signature.getParameterCount(!Modifier.isStatic(info.invoke().methodCallTarget().targetMethod().getModifiers()));
            if (signature.getReturnKind() != Kind.Void) {
                transferredValues++;
            }
            return transferredValues;
        }

        private static int countInvokeUsages(InlineInfo info) {
            // inlining calls with lots of usages simplifies the caller
            int usages = 0;
            for (Node n : info.invoke().node().usages()) {
                if (!(n instanceof FrameState)) {
                    usages++;
                }
            }
            return usages;
        }

        private int countMoreSpecificArgumentInfo(InlineInfo info) {
            /*
             * inlining invokes where the caller has very specific information about the passed
             * argument simplifies the callee
             */
            int moreSpecificArgumentInfo = 0;
            boolean isStatic = info.invoke().methodCallTarget().isStatic();
            int signatureOffset = isStatic ? 0 : 1;
            NodeInputList arguments = info.invoke().methodCallTarget().arguments();
            ResolvedJavaMethod targetMethod = info.invoke().methodCallTarget().targetMethod();
            ResolvedJavaType methodHolderClass = targetMethod.getDeclaringClass();
            Signature signature = targetMethod.getSignature();

            for (int i = 0; i < arguments.size(); i++) {
                Node n = arguments.get(i);
                if (n instanceof ConstantNode) {
                    moreSpecificArgumentInfo++;
                } else if (n instanceof ValueNode && !((ValueNode) n).kind().isPrimitive()) {
                    ResolvedJavaType actualType = ((ValueNode) n).stamp().javaType(runtime);
                    JavaType declaredType;
                    if (i == 0 && !isStatic) {
                        declaredType = methodHolderClass;
                    } else {
                        declaredType = signature.getParameterType(i - signatureOffset, methodHolderClass);
                    }

                    if (declaredType instanceof ResolvedJavaType && !actualType.equals(declaredType) && ((ResolvedJavaType) declaredType).isAssignableFrom(actualType)) {
                        moreSpecificArgumentInfo++;
                    }
                }

            }

            return moreSpecificArgumentInfo;
        }

        private static int bytecodeCodeSize(InlineInfo info) {
            int result = 0;
            for (int i = 0; i < info.numberOfMethods(); i++) {
                result += info.methodAt(i).getCodeSize();
            }
            return result;
        }

        private static int compilationComplexity(InlineInfo info) {
            int result = 0;
            for (int i = 0; i < info.numberOfMethods(); i++) {
                result += info.methodAt(i).getCompilationComplexity();
            }
            return result;
        }

        private static int compiledCodeSize(InlineInfo info) {
            int result = 0;
            for (int i = 0; i < info.numberOfMethods(); i++) {
                result += info.methodAt(i).getCompiledCodeSize();
            }
            return result;
        }

        private static boolean onlyIntrinsics(InlineInfo info) {
            for (int i = 0; i < info.numberOfMethods(); i++) {
                if (!InliningUtil.canIntrinsify(info.methodAt(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static class CFInliningPolicy implements InliningPolicy {

        private final InliningDecision inliningDecision;
        private final Assumptions assumptions;
        private final OptimisticOptimizations optimisticOpts;
        private final Deque<Invoke> sortedInvokes;
        private NodeBitMap visitedFixedNodes;
        private FixedNode invokePredecessor;

        public CFInliningPolicy(InliningDecision inliningPolicy, Assumptions assumptions, OptimisticOptimizations optimisticOpts) {
            this.inliningDecision = inliningPolicy;
            this.assumptions = assumptions;
            this.optimisticOpts = optimisticOpts;
            this.sortedInvokes = new ArrayDeque<>();
        }

        public boolean continueInlining(StructuredGraph graph) {
            if (graph.getNodeCount() >= GraalOptions.MaximumDesiredSize) {
                InliningUtil.logInliningDecision("inlining is cut off by MaximumDesiredSize");
                metricInliningStoppedByMaxDesiredSize.increment();
                return false;
            }

            return !sortedInvokes.isEmpty();
        }

        public InlineInfo next() {
            Invoke invoke = sortedInvokes.pop();
            InlineInfo info = InliningUtil.getInlineInfo(invoke, assumptions, optimisticOpts);
            if (info != null) {
                invokePredecessor = (FixedNode) info.invoke().predecessor();
                assert invokePredecessor.isAlive();
            }
            return info;
        }

        public boolean isWorthInlining(InlineInfo info) {
            return inliningDecision.isWorthInlining(info);
        }

        public void initialize(StructuredGraph graph) {
            visitedFixedNodes = graph.createNodeBitMap(true);
            scanGraphForInvokes(graph.start());
        }

        public void scanInvokes(Iterable<? extends Node> newNodes) {
            assert invokePredecessor.isAlive();
            int invokes = scanGraphForInvokes(invokePredecessor);
            assert invokes == countInvokes(newNodes);
        }

        private int scanGraphForInvokes(FixedNode start) {
            ArrayList<Invoke> invokes = new InliningIterator(start, visitedFixedNodes).apply();

            // insert the newly found invokes in their correct control-flow order
            for (int i = invokes.size() - 1; i >= 0; i--) {
                Invoke invoke = invokes.get(i);
                assert !sortedInvokes.contains(invoke);
                sortedInvokes.addFirst(invoke);

            }

            return invokes.size();
        }

        private static int countInvokes(Iterable<? extends Node> nodes) {
            int count = 0;
            for (Node n : nodes) {
                if (n instanceof Invoke) {
                    count++;
                }
            }
            return count;
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

        public ArrayList<Invoke> apply() {
            ArrayList<Invoke> invokes = new ArrayList<>();
            FixedNode current;
            forcedQueue(start);

            while ((current = nextQueuedNode()) != null) {
                assert current.isAlive();

                if (current instanceof Invoke) {
                    if (current != start) {
                        invokes.add((Invoke) current);
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

    private static InliningPolicy createInliningPolicy(GraalCodeCacheProvider runtime, Assumptions assumptions, OptimisticOptimizations optimisticOpts, Map<Invoke, Double> hints) {
        InliningDecision inliningDecision = new GreedySizeBasedInliningDecision(runtime, hints);
        return new CFInliningPolicy(inliningDecision, assumptions, optimisticOpts);
    }
}
