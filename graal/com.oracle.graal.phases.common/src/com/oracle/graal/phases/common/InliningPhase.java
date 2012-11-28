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

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.*;
import com.oracle.graal.phases.PhasePlan.PhasePosition;
import com.oracle.graal.phases.common.InliningUtil.InlineInfo;
import com.oracle.graal.phases.common.InliningUtil.InliningCallback;
import com.oracle.graal.phases.common.InliningUtil.InliningPolicy;
import com.oracle.graal.phases.common.InliningUtil.WeightComputationPolicy;

public class InliningPhase extends Phase implements InliningCallback {
    /*
     * - Detect method which only call another method with some parameters set to constants: void foo(a) -> void foo(a, b) -> void foo(a, b, c) ...
     *   These should not be taken into account when determining inlining depth.
     * - honor the result of overrideInliningDecision(0, caller, invoke.bci, method, true);
     */

    private final TargetDescription target;
    private final PhasePlan plan;

    private final GraalCodeCacheProvider runtime;
    private final Assumptions assumptions;
    private final GraphCache cache;
    private final InliningPolicy inliningPolicy;

    // Metrics
    private static final DebugMetric metricInliningPerformed = Debug.metric("InliningPerformed");
    private static final DebugMetric metricInliningConsidered = Debug.metric("InliningConsidered");
    private static final DebugMetric metricInliningStoppedByMaxDesiredSize = Debug.metric("InliningStoppedByMaxDesiredSize");
    private static final DebugMetric metricInliningRuns = Debug.metric("Runs");

    public InliningPhase(TargetDescription target, GraalCodeCacheProvider runtime, Collection<Invoke> hints, Assumptions assumptions, GraphCache cache, PhasePlan plan, OptimisticOptimizations optimisticOpts) {
        this(target, runtime, assumptions, cache, plan, createInliningPolicy(runtime, assumptions, optimisticOpts, hints));
    }

    public InliningPhase(TargetDescription target, GraalCodeCacheProvider runtime, Assumptions assumptions, GraphCache cache, PhasePlan plan, InliningPolicy inliningPolicy) {
        this.target = target;
        this.runtime = runtime;
        this.assumptions = assumptions;
        this.cache = cache;
        this.plan = plan;
        this.inliningPolicy = inliningPolicy;
    }

    @Override
    protected void run(final StructuredGraph graph) {
        inliningPolicy.initialize(graph);

        while (inliningPolicy.continueInlining(graph)) {
            final InlineInfo candidate = inliningPolicy.next();
            if (candidate != null) {
                boolean isWorthInlining = inliningPolicy.isWorthInlining(candidate);

                metricInliningConsidered.increment();
                if (isWorthInlining) {
                    int mark = graph.getMark();
                    try {
                        candidate.inline(graph, runtime, this, assumptions);
                        Debug.dump(graph, "after %s", candidate);
                        Iterable<Node> newNodes = graph.getNewNodes(mark);
                        if (GraalOptions.OptCanonicalizer) {
                            new CanonicalizerPhase(target, runtime, assumptions, mark, null).apply(graph);
                        }
                        metricInliningPerformed.increment();

                        inliningPolicy.scanInvokes(newNodes);
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

    private interface InliningDecision {
        boolean isWorthInlining(InlineInfo info);
    }

    private abstract static class AbstractInliningDecision implements InliningDecision {
        public static boolean decideSizeBasedInlining(InlineInfo info, double maxSize) {
            boolean success = info.weight() <= maxSize;
            if (GraalOptions.Debug) {
                String formatterString = success ? "(size %f <= %f)" : "(too large %f > %f)";
                InliningUtil.logInliningDecision(info, success, formatterString, info.weight(), maxSize);
            }
            return success;
        }

        public static boolean checkCompiledCodeSize(InlineInfo info) {
            if (GraalOptions.SmallCompiledCodeSize >= 0 && info.compiledCodeSize() > GraalOptions.SmallCompiledCodeSize) {
                InliningUtil.logNotInlinedMethod(info, "(CompiledCodeSize %d > %d)", info.compiledCodeSize(), GraalOptions.SmallCompiledCodeSize);
                return false;
            }
            return true;
        }
    }

    private static class C1StaticSizeBasedInliningDecision extends AbstractInliningDecision {
        @Override
        public boolean isWorthInlining(InlineInfo info) {
            double maxSize = Math.max(GraalOptions.MaximumTrivialSize, Math.pow(GraalOptions.NestedInliningSizeRatio, info.level()) * GraalOptions.MaximumInlineSize);
            return decideSizeBasedInlining(info, maxSize);
        }
    }

    private static class MinimumCodeSizeBasedInliningDecision extends AbstractInliningDecision {
        @Override
        public boolean isWorthInlining(InlineInfo info) {
            assert GraalOptions.ProbabilityAnalysis;
            if (!checkCompiledCodeSize(info)) {
                return false;
            }

            double inlineWeight = Math.min(GraalOptions.ProbabilityCapForInlining, info.invoke().probability());
            double maxSize = Math.pow(GraalOptions.NestedInliningSizeRatio, info.level()) * GraalOptions.MaximumInlineSize * inlineWeight;
            maxSize = Math.max(GraalOptions.MaximumTrivialSize, maxSize);

            return decideSizeBasedInlining(info, maxSize);
        }
    }

    private static class DynamicSizeBasedInliningDecision extends AbstractInliningDecision {
        @Override
        public boolean isWorthInlining(InlineInfo info) {
            assert GraalOptions.ProbabilityAnalysis;
            if (!checkCompiledCodeSize(info)) {
                return false;
            }

            double inlineBoost = Math.min(GraalOptions.ProbabilityCapForInlining, info.invoke().probability()) + Math.log10(Math.max(1, info.invoke().probability() - GraalOptions.ProbabilityCapForInlining + 1));
            double maxSize = Math.pow(GraalOptions.NestedInliningSizeRatio, info.level()) * GraalOptions.MaximumInlineSize;
            maxSize = maxSize + maxSize * inlineBoost;
            maxSize = Math.min(GraalOptions.MaximumGreedyInlineSize, Math.max(GraalOptions.MaximumTrivialSize, maxSize));

            return decideSizeBasedInlining(info, maxSize);
        }
    }

    private static class GreedySizeBasedInliningDecision extends AbstractInliningDecision {
        @Override
        public boolean isWorthInlining(InlineInfo info) {
            assert GraalOptions.ProbabilityAnalysis;
            if (!checkCompiledCodeSize(info)) {
                return false;
            }

            double maxSize = GraalOptions.MaximumGreedyInlineSize;
            if (GraalOptions.InliningBonusPerTransferredValue != 0) {
                Signature signature = info.invoke().methodCallTarget().targetMethod().getSignature();
                int transferredValues = signature.getParameterCount(!Modifier.isStatic(info.invoke().methodCallTarget().targetMethod().getModifiers()));
                if (signature.getReturnKind() != Kind.Void) {
                    transferredValues++;
                }
                maxSize += transferredValues * GraalOptions.InliningBonusPerTransferredValue;
            }

            double inlineRatio = Math.min(GraalOptions.ProbabilityCapForInlining, info.invoke().probability());
            maxSize = Math.pow(GraalOptions.NestedInliningSizeRatio, info.level()) * maxSize * inlineRatio;
            maxSize = Math.max(maxSize, GraalOptions.MaximumTrivialSize);

            return decideSizeBasedInlining(info, maxSize);
        }
    }

    private static class GreedyMachineCodeInliningDecision extends AbstractInliningDecision {
        @Override
        public boolean isWorthInlining(InlineInfo info) {
            assert GraalOptions.ProbabilityAnalysis;

            double maxSize = GraalOptions.MaximumGreedyInlineSize;
            double inlineRatio = Math.min(GraalOptions.ProbabilityCapForInlining, info.invoke().probability());
            maxSize = Math.pow(GraalOptions.NestedInliningSizeRatio, info.level()) * maxSize * inlineRatio;
            maxSize = Math.max(maxSize, GraalOptions.MaximumTrivialSize);

            return decideSizeBasedInlining(info, maxSize);
        }
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

    private static class CompiledCodeSizeWeightComputationPolicy implements WeightComputationPolicy {
        @Override
        public double computeWeight(ResolvedJavaMethod caller, ResolvedJavaMethod method, Invoke invoke, boolean preferredInvoke) {
            int compiledCodeSize = method.getCompiledCodeSize();
            return compiledCodeSize > 0 ? compiledCodeSize : method.getCodeSize() * 10;
        }
    }

    private static class CFInliningPolicy implements InliningPolicy {
        private final InliningDecision inliningDecision;
        private final WeightComputationPolicy weightComputationPolicy;
        private final Collection<Invoke> hints;
        private final GraalCodeCacheProvider runtime;
        private final Assumptions assumptions;
        private final OptimisticOptimizations optimisticOpts;
        private final Deque<Invoke> sortedInvokes;
        private NodeBitMap visitedFixedNodes;
        private FixedNode invokePredecessor;

        public CFInliningPolicy(InliningDecision inliningPolicy, WeightComputationPolicy weightComputationPolicy, Collection<Invoke> hints,
                        GraalCodeCacheProvider runtime, Assumptions assumptions, OptimisticOptimizations optimisticOpts) {
            this.inliningDecision = inliningPolicy;
            this.weightComputationPolicy = weightComputationPolicy;
            this.hints = hints;
            this.runtime = runtime;
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
            InlineInfo info = InliningUtil.getInlineInfo(invoke, runtime, assumptions, this, optimisticOpts);
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
            if (hints != null) {
                sortedInvokes.retainAll(hints);
            }
        }

        public void scanInvokes(Iterable<? extends Node> newNodes) {
            scanGraphForInvokes(invokePredecessor);
        }

        private void scanGraphForInvokes(FixedNode start) {
            ArrayList<Invoke> invokes = new InliningIterator(start, visitedFixedNodes).apply();

            // insert the newly found invokes in their correct control-flow order
            for (int i = invokes.size() - 1; i >= 0; i--) {
                sortedInvokes.addFirst(invokes.get(i));
            }
        }

        public double inliningWeight(ResolvedJavaMethod caller, ResolvedJavaMethod method, Invoke invoke) {
            boolean preferredInvoke = hints != null && hints.contains(invoke);
            return weightComputationPolicy.computeWeight(caller, method, invoke, preferredInvoke);
        }
    }

    private static class PriorityInliningPolicy implements InliningPolicy {
        private final InliningDecision inliningDecision;
        private final WeightComputationPolicy weightComputationPolicy;
        private final Collection<Invoke> hints;
        private final GraalCodeCacheProvider runtime;
        private final Assumptions assumptions;
        private final OptimisticOptimizations optimisticOpts;
        private final PriorityQueue<InlineInfo> sortedCandidates;

        public PriorityInliningPolicy(InliningDecision inliningPolicy, WeightComputationPolicy weightComputationPolicy, Collection<Invoke> hints,
                        GraalCodeCacheProvider runtime, Assumptions assumptions, OptimisticOptimizations optimisticOpts) {
            this.inliningDecision = inliningPolicy;
            this.weightComputationPolicy = weightComputationPolicy;
            this.hints = hints;
            this.runtime = runtime;
            this.assumptions = assumptions;
            this.optimisticOpts = optimisticOpts;
            sortedCandidates = new PriorityQueue<>();
        }

        public boolean continueInlining(StructuredGraph graph) {
            if (graph.getNodeCount() >= GraalOptions.MaximumDesiredSize) {
                InliningUtil.logInliningDecision("inlining is cut off by MaximumDesiredSize");
                metricInliningStoppedByMaxDesiredSize.increment();
                return false;
            }

            return !sortedCandidates.isEmpty();
        }

        public InlineInfo next() {
            // refresh cached info before using it (it might have been in the queue for a long time)
            InlineInfo info = sortedCandidates.remove();
            return InliningUtil.getInlineInfo(info.invoke(), runtime, assumptions, this, optimisticOpts);
        }

        @Override
        public boolean isWorthInlining(InlineInfo info) {
            return inliningDecision.isWorthInlining(info);
        }

        @SuppressWarnings("unchecked")
        public void initialize(StructuredGraph graph) {
            if (hints == null) {
                scanInvokes(graph.getNodes(InvokeNode.class));
                scanInvokes(graph.getNodes(InvokeWithExceptionNode.class));
            } else {
                scanInvokes((Iterable<? extends Node>) (Iterable<?>) hints);
            }
        }

        public void scanInvokes(Iterable<? extends Node> nodes) {
            for (Node node: nodes) {
                if (node != null) {
                    if (node instanceof Invoke) {
                        Invoke invoke = (Invoke) node;
                        scanInvoke(invoke);
                    }
                    for (Node usage : node.usages().filterInterface(Invoke.class).snapshot()) {
                        scanInvoke((Invoke) usage);
                    }
                }
            }
        }

        private void scanInvoke(Invoke invoke) {
            InlineInfo info = InliningUtil.getInlineInfo(invoke, runtime, assumptions, this, optimisticOpts);
            if (info != null) {
                sortedCandidates.add(info);
            }
        }

        @Override
        public double inliningWeight(ResolvedJavaMethod caller, ResolvedJavaMethod method, Invoke invoke) {
            boolean preferredInvoke = hints != null && hints.contains(invoke);
            return weightComputationPolicy.computeWeight(caller, method, invoke, preferredInvoke);
        }
    }

    private static class InliningIterator {
        private final FixedNode start;
        private final NodeBitMap processedNodes;

        private final Deque<FixedNode> nodeQueue;
        private final NodeBitMap queuedNodes;

        public InliningIterator(FixedNode start, NodeBitMap visitedFixedNodes) {
            this.start = start;
            this.processedNodes = visitedFixedNodes;

            this.nodeQueue = new ArrayDeque<>();
            this.queuedNodes = visitedFixedNodes.copy();

            assert start.isAlive();
        }

        public ArrayList<Invoke> apply() {
            ArrayList<Invoke> invokes = new ArrayList<>();
            FixedNode current = start;
            do {
                assert current.isAlive();
                processedNodes.mark(current);

                if (current instanceof InvokeWithExceptionNode || current instanceof InvokeNode) {
                    invokes.add((Invoke) current);
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

            return invokes;
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
    }

    private static InliningPolicy createInliningPolicy(GraalCodeCacheProvider runtime, Assumptions assumptions, OptimisticOptimizations optimisticOpts, Collection<Invoke> hints) {
        switch(GraalOptions.InliningPolicy) {
            case 0: return new CFInliningPolicy(createInliningDecision(), createWeightComputationPolicy(), hints, runtime, assumptions, optimisticOpts);
            case 1: return new PriorityInliningPolicy(createInliningDecision(), createWeightComputationPolicy(), hints, runtime, assumptions, optimisticOpts);
            default:
                GraalInternalError.shouldNotReachHere();
                return null;
        }
    }

    private static InliningDecision createInliningDecision() {
        switch(GraalOptions.InliningDecision) {
            case 1: return new C1StaticSizeBasedInliningDecision();
            case 2: return new MinimumCodeSizeBasedInliningDecision();
            case 3: return new DynamicSizeBasedInliningDecision();
            case 4: return new GreedySizeBasedInliningDecision();
            case 5: return new GreedyMachineCodeInliningDecision();
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
            case 3: return new CompiledCodeSizeWeightComputationPolicy();
            default:
                GraalInternalError.shouldNotReachHere();
                return null;
        }
    }
}
