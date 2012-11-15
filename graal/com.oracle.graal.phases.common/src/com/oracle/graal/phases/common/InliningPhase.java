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
import com.oracle.graal.debug.internal.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.common.InliningUtil.InlineInfo;
import com.oracle.graal.phases.common.InliningUtil.InliningCallback;

public class InliningPhase extends Phase implements InliningCallback {
    /*
     * - Detect method which only call another method with some parameters set to constants: void foo(a) -> void foo(a, b) -> void foo(a, b, c) ...
     *   These should not be taken into account when determining inlining depth.
     * - honor the result of overrideInliningDecision(0, caller, invoke.bci, method, true);
     */

    private final TargetDescription target;
    private final GraalCodeCacheProvider runtime;

    private final Collection<? extends Invoke> hints;

    private Assumptions assumptions;

    private final PhasePlan plan;
    private final GraphCache cache;
    private final WeightComputationPolicy weightComputationPolicy;
    private final InliningPolicy inliningPolicy;
    private final OptimisticOptimizations optimisticOpts;

    // Metrics
    private static final DebugMetric metricInliningPerformed = Debug.metric("InliningPerformed");
    private static final DebugMetric metricInliningConsidered = Debug.metric("InliningConsidered");
    private static final DebugMetric metricInliningStoppedByMaxDesiredSize = Debug.metric("InliningStoppedByMaxDesiredSize");
    private static final DebugMetric metricInliningRuns = Debug.metric("Runs");

    public InliningPhase(TargetDescription target, GraalCodeCacheProvider runtime, Collection<? extends Invoke> hints, Assumptions assumptions, GraphCache cache, PhasePlan plan, OptimisticOptimizations optimisticOpts) {
        this.target = target;
        this.runtime = runtime;
        this.hints = hints;
        this.assumptions = assumptions;
        this.cache = cache;
        this.plan = plan;
        this.optimisticOpts = optimisticOpts;
        this.weightComputationPolicy = createWeightComputationPolicy();
        this.inliningPolicy = createInliningPolicy();
    }

    @Override
    protected void run(final StructuredGraph graph) {
        NodeBitMap visitedFixedNodes = graph.createNodeBitMap(true);
        Deque<Invoke> sortedInvokes = new ArrayDeque<>();

        queueInvokes(graph.start(), sortedInvokes, visitedFixedNodes);

        while (!sortedInvokes.isEmpty() && graph.getNodeCount() < GraalOptions.MaximumDesiredSize) {
            final Invoke invoke = sortedInvokes.pop();
            if (hints != null && !hints.contains(invoke)) {
                continue;
            }

            InlineInfo candidate = Debug.scope("InliningDecisions", new Callable<InlineInfo>() {
                @Override
                public InlineInfo call() throws Exception {
                    InlineInfo info = InliningUtil.getInlineInfo(invoke, computeInliningLevel(invoke), runtime, assumptions, InliningPhase.this, optimisticOpts);
                    if (info == null || !info.invoke.node().isAlive() || info.level > GraalOptions.MaximumInlineLevel) {
                        return null;
                    }
                    metricInliningConsidered.increment();
                    if (inliningPolicy.isWorthInlining(graph, info)) {
                        return info;
                    } else {
                        return null;
                    }
                }
            });

            // TEMP:
//            if (GraalOptions.AlwaysInlineTrivialMethods) {
//                TODO: Continue;
//            }

            if (candidate != null) {
                int mark = graph.getMark();
                try {
                    FixedNode predecessor = (FixedNode) invoke.predecessor();
                    candidate.inline(graph, runtime, InliningPhase.this);
                    Debug.dump(graph, "after %s", candidate);
                    if (GraalOptions.OptCanonicalizer) {
                        new CanonicalizerPhase(target, runtime, assumptions, mark, null).apply(graph);
                    }
                    metricInliningPerformed.increment();

                    queueInvokes(predecessor, sortedInvokes, visitedFixedNodes);
                } catch (BailoutException bailout) {
                    // TODO determine if we should really bail out of the whole compilation.
                    throw bailout;
                } catch (AssertionError e) {
                    throw new GraalInternalError(e).addContext(candidate.toString());
                } catch (RuntimeException e) {
                    throw new GraalInternalError(e).addContext(candidate.toString());
                } catch (GraalInternalError e) {
                    throw e.addContext(candidate.toString());
                }
            }
        }

        if (graph.getNodeCount() >= GraalOptions.MaximumDesiredSize) {
            if (GraalOptions.Debug) {
                Debug.scope("InliningDecisions", new Runnable() {
                    public void run() {
                        Debug.log("inlining is cut off by MaximumDesiredSize");
                    }
                });

                metricInliningStoppedByMaxDesiredSize.increment();
            }
        }
    }

    private static void queueInvokes(FixedNode start, Deque<Invoke> invokes, NodeBitMap visitedFixedNodes) {
        ArrayList<Invoke> results = new ArrayList<>();
        new InliningIterator(start, results, visitedFixedNodes).apply();
        // insert the newly found invokes in their correct control-flow order
        for (int i = results.size() - 1; i >= 0; i--) {
            invokes.addFirst(results.get(i));
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
        StructuredGraph newGraph = new StructuredGraph(method);
        if (plan != null) {
            plan.runPhases(PhasePosition.AFTER_PARSING, newGraph);
        }
        assert newGraph.start().next() != null : "graph needs to be populated during PhasePosition.AFTER_PARSING";

        if (GraalOptions.ProbabilityAnalysis) {
            new DeadCodeEliminationPhase().apply(newGraph);
            new ComputeProbabilityPhase().apply(newGraph);
        }
        if (GraalOptions.OptCanonicalizer) {
            new CanonicalizerPhase(target, runtime, assumptions).apply(newGraph);
        }
        if (GraalOptions.CullFrameStates) {
            new CullFrameStatesPhase().apply(newGraph);
        }
        if (GraalOptions.CacheGraphs && cache != null) {
            cache.put(newGraph);
        }
        return newGraph;
    }

    @Override
    public double inliningWeight(ResolvedJavaMethod caller, ResolvedJavaMethod method, Invoke invoke) {
        boolean preferred = hints != null && hints.contains(invoke);
        return weightComputationPolicy.computeWeight(caller, method, invoke, preferred);
    }

    public static int graphComplexity(StructuredGraph graph) {
        int result = 0;
        for (Node node : graph.getNodes()) {
            if (node instanceof ConstantNode || node instanceof LocalNode || node instanceof BeginNode || node instanceof ReturnNode || node instanceof UnwindNode) {
                result += 0;
            } else if (node instanceof PhiNode) {
                result += 5;
            } else if (node instanceof MergeNode || node instanceof Invoke || node instanceof LoopEndNode || node instanceof EndNode) {
                result += 0;
            } else if (node instanceof ControlSplitNode) {
                result += ((ControlSplitNode) node).blockSuccessorCount();
            } else {
                result += 1;
            }
        }
        return Math.max(1, result);
    }


    @Override
    public void recordConcreteMethodAssumption(ResolvedJavaMethod method, ResolvedJavaType context, ResolvedJavaMethod impl) {
        assumptions.recordConcreteMethod(method, context, impl);
    }

    @Override
    public void recordMethodContentsAssumption(ResolvedJavaMethod method) {
        if (assumptions != null) {
            assumptions.recordMethodContents(method);
        }
    }

    private static int computeInliningLevel(Invoke invoke) {
        int count = -1;
        FrameState curState = invoke.stateAfter();
        while (curState != null) {
            count++;
            curState = curState.outerFrameState();
        }
        return count;
    }

    private static InliningPolicy createInliningPolicy() {
        switch(GraalOptions.InliningPolicy) {
            case 0: return new WeightBasedInliningPolicy();
            case 1: return new C1StaticSizeBasedInliningPolicy();
            case 2: return new MinimumCodeSizeBasedInliningPolicy();
            case 3: return new DynamicSizeBasedInliningPolicy();
            case 4: return new GreedySizeBasedInliningPolicy();
            default:
                GraalInternalError.shouldNotReachHere();
                return null;
        }
    }

    private static WeightComputationPolicy createWeightComputationPolicy() {
        switch(GraalOptions.WeightComputationPolicy) {
            case 0: throw new GraalInternalError("removed because of invokation counter changes");
            case 1: return new BytecodeSizeBasedWeightComputationPolicy();
            case 2: return new ComplexityBasedWeightComputationPolicy();
            default:
                GraalInternalError.shouldNotReachHere();
                return null;
        }
    }

    private interface InliningPolicy {
        boolean isWorthInlining(StructuredGraph callerGraph, InlineInfo info);
    }

    private static class WeightBasedInliningPolicy implements InliningPolicy {
        @Override
        public boolean isWorthInlining(StructuredGraph callerGraph, InlineInfo info) {
            if (!checkCompiledCodeSize(info)) {
                return false;
            }

            double penalty = Math.pow(GraalOptions.InliningSizePenaltyExp, callerGraph.getNodeCount() / (double) GraalOptions.MaximumDesiredSize) / GraalOptions.InliningSizePenaltyExp;
            if (info.weight > GraalOptions.MaximumInlineWeight / (1 + penalty * GraalOptions.InliningSizePenalty)) {
                Debug.log("not inlining %s (cut off by weight %e)", InliningUtil.methodName(info), info.weight);
                return false;
            }

            Debug.log("inlining %s (weight %f): %s", InliningUtil.methodName(info), info.weight);
            return true;
        }
    }

    private static class C1StaticSizeBasedInliningPolicy implements InliningPolicy {
        @Override
        public boolean isWorthInlining(StructuredGraph callerGraph, InlineInfo info) {
            double maxSize = Math.max(GraalOptions.MaximumTrivialSize, Math.pow(GraalOptions.NestedInliningSizeRatio, info.level) * GraalOptions.MaximumInlineSize);
            return decideSizeBasedInlining(info, maxSize);
        }
    }

    private static class MinimumCodeSizeBasedInliningPolicy implements InliningPolicy {
        @Override
        public boolean isWorthInlining(StructuredGraph callerGraph, InlineInfo info) {
            assert GraalOptions.ProbabilityAnalysis;
            if (!checkCompiledCodeSize(info)) {
                return false;
            }

            double inlineWeight = Math.min(GraalOptions.ProbabilityCapForInlining, info.invoke.probability());
            double maxSize = Math.pow(GraalOptions.NestedInliningSizeRatio, info.level) * GraalOptions.MaximumInlineSize * inlineWeight;
            maxSize = Math.max(GraalOptions.MaximumTrivialSize, maxSize);

            return decideSizeBasedInlining(info, maxSize);
        }
    }

    private static class DynamicSizeBasedInliningPolicy implements InliningPolicy {
        @Override
        public boolean isWorthInlining(StructuredGraph callerGraph, InlineInfo info) {
            assert GraalOptions.ProbabilityAnalysis;
            if (!checkCompiledCodeSize(info)) {
                return false;
            }

            double inlineBoost = Math.min(GraalOptions.ProbabilityCapForInlining, info.invoke.probability()) + Math.log10(Math.max(1, info.invoke.probability() - GraalOptions.ProbabilityCapForInlining + 1));
            double maxSize = Math.pow(GraalOptions.NestedInliningSizeRatio, info.level) * GraalOptions.MaximumInlineSize;
            maxSize = maxSize + maxSize * inlineBoost;
            maxSize = Math.min(GraalOptions.MaximumGreedyInlineSize, Math.max(GraalOptions.MaximumTrivialSize, maxSize));

            return decideSizeBasedInlining(info, maxSize);
        }
    }

    private static class GreedySizeBasedInliningPolicy implements InliningPolicy {
        @Override
        public boolean isWorthInlining(StructuredGraph callerGraph, InlineInfo info) {
            assert GraalOptions.ProbabilityAnalysis;
            if (!checkCompiledCodeSize(info)) {
                return false;
            }

            double maxSize = GraalOptions.MaximumGreedyInlineSize;
            if (GraalOptions.InliningBonusPerTransferredValue != 0) {
                Signature signature = info.invoke.methodCallTarget().targetMethod().getSignature();
                int transferredValues = signature.getParameterCount(!Modifier.isStatic(info.invoke.methodCallTarget().targetMethod().getModifiers()));
                if (signature.getReturnKind() != Kind.Void) {
                    transferredValues++;
                }
                maxSize += transferredValues * GraalOptions.InliningBonusPerTransferredValue;
            }

            double inlineRatio = Math.min(GraalOptions.ProbabilityCapForInlining, info.invoke.probability());
            maxSize = Math.pow(GraalOptions.NestedInliningSizeRatio, info.level) * maxSize * inlineRatio;
            maxSize = Math.max(maxSize, GraalOptions.MaximumTrivialSize);

            return decideSizeBasedInlining(info, maxSize);
        }
    }

    private static boolean decideSizeBasedInlining(InlineInfo info, double maxSize) {
        boolean success = info.weight <= maxSize;
        if (DebugScope.getInstance().isLogEnabled()) {
            String formatterString = success ? "inlining %s (size %f <= %f)" : "not inlining %s (too large %f > %f)";
            Debug.log(formatterString, InliningUtil.methodName(info), info.weight, maxSize);
        }
        return success;
    }

    private static boolean checkCompiledCodeSize(InlineInfo info) {
        if (GraalOptions.SmallCompiledCodeSize >= 0 && info.compiledCodeSize() > GraalOptions.SmallCompiledCodeSize) {
            Debug.log("not inlining %s (CompiledCodeSize %d > %d)", InliningUtil.methodName(info), info.compiledCodeSize(), GraalOptions.SmallCompiledCodeSize);
            return false;
        }
        return true;
    }


    private interface WeightComputationPolicy {
        double computeWeight(ResolvedJavaMethod caller, ResolvedJavaMethod method, Invoke invoke, boolean preferredInvoke);
    }

    private static class BytecodeSizeBasedWeightComputationPolicy implements WeightComputationPolicy {
        @Override
        public double computeWeight(ResolvedJavaMethod caller, ResolvedJavaMethod method, Invoke invoke, boolean preferredInvoke) {
            double codeSize = method.getCodeSize();
            if (preferredInvoke) {
                codeSize = codeSize / GraalOptions.BoostInliningForEscapeAnalysis;
            }
            return codeSize;
        }
    }

    private static class ComplexityBasedWeightComputationPolicy implements WeightComputationPolicy {
        @Override
        public double computeWeight(ResolvedJavaMethod caller, ResolvedJavaMethod method, Invoke invoke, boolean preferredInvoke) {
            double complexity = method.getCompilationComplexity();
            if (preferredInvoke) {
                complexity = complexity / GraalOptions.BoostInliningForEscapeAnalysis;
            }
            return complexity;
        }
    }

    private static class InliningIterator {
        private final FixedNode start;
        private final Collection<Invoke> invokes;
        private final NodeBitMap processedNodes;

        private final Deque<FixedNode> nodeQueue;
        private final NodeBitMap queuedNodes;

        public InliningIterator(FixedNode start, Collection<Invoke> invokes, NodeBitMap visitedFixedNodes) {
            this.start = start;
            this.invokes = invokes;
            this.processedNodes = visitedFixedNodes;

            this.nodeQueue = new ArrayDeque<>();
            this.queuedNodes = visitedFixedNodes.copy();

            assert start.isAlive();
        }

        public void apply() {
            FixedNode current = start;
            do {
                assert current.isAlive();
                processedNodes.mark(current);

                if (current instanceof InvokeWithExceptionNode || current instanceof InvokeNode) {
                    invoke((Invoke) current);
                    queueSuccessors(current);
                    current = nextQueuedNode();
                } else if (current instanceof LoopBeginNode) {
                    current = ((LoopBeginNode) current).next();
                    assert current != null;
                } else if (current instanceof LoopEndNode) {
                    current = nextQueuedNode();
                } else if (current instanceof MergeNode) {
                    current = ((MergeNode) current).next();
                    assert current != null;
                } else if (current instanceof FixedWithNextNode) {
                    queueSuccessors(current);
                    current = nextQueuedNode();
                } else if (current instanceof EndNode) {
                    queueMerge((EndNode) current);
                    current = nextQueuedNode();
                } else if (current instanceof DeoptimizeNode) {
                    current = nextQueuedNode();
                } else if (current instanceof ReturnNode) {
                    current = nextQueuedNode();
                } else if (current instanceof UnwindNode) {
                    current = nextQueuedNode();
                } else if (current instanceof ControlSplitNode) {
                    queueSuccessors(current);
                    current = nextQueuedNode();
                } else {
                    assert false : current;
                }
            } while(current != null);
        }

        private void queueSuccessors(FixedNode x) {
            for (Node node : x.successors()) {
                if (node != null && !queuedNodes.isMarked(node)) {
                    queuedNodes.mark(node);
                    nodeQueue.addFirst((FixedNode) node);
                }
            }
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
                if (!processedNodes.isMarked(merge.forwardEndAt(i))) {
                    return false;
                }
            }
            return true;
        }

        protected void invoke(Invoke invoke) {
            invokes.add(invoke);
        }
    }
}
