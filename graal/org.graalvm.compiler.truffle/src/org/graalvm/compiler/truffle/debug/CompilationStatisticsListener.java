/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.debug;

import static java.util.function.Function.identity;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleCompilationStatisticDetails;
import static org.graalvm.compiler.truffle.TruffleCompilerOptions.TruffleCompilationStatistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.OptimizedCallTarget;
import org.graalvm.compiler.truffle.OptimizedCompilationProfile;
import org.graalvm.compiler.truffle.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.TruffleCompilerOptions;
import org.graalvm.compiler.truffle.TruffleInlining;
import org.graalvm.compiler.truffle.TruffleInlining.CallTreeNodeVisitor;
import org.graalvm.compiler.truffle.TruffleInliningDecision;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;

import jdk.vm.ci.code.site.Infopoint;

public final class CompilationStatisticsListener extends AbstractDebugCompilationListener {

    private long firstCompilation;

    private int compilations;
    private int invalidations;
    private int failures;
    private int success;
    private int queues;
    private int dequeues;
    private int splits;

    private final IntSummaryStatistics deferCompilations = new IntSummaryStatistics();
    private final LongSummaryStatistics timeToQueue = new LongSummaryStatistics();
    private final LongSummaryStatistics timeToCompilation = new LongSummaryStatistics();

    private final IntSummaryStatistics nodeCount = new IntSummaryStatistics();
    private final IntSummaryStatistics nodeCountTrivial = new IntSummaryStatistics();
    private final IntSummaryStatistics nodeCountNonTrivial = new IntSummaryStatistics();
    private final IntSummaryStatistics nodeCountMonomorphic = new IntSummaryStatistics();
    private final IntSummaryStatistics nodeCountPolymorphic = new IntSummaryStatistics();
    private final IntSummaryStatistics nodeCountMegamorphic = new IntSummaryStatistics();
    private final IdentityStatistics<Class<?>> nodeStatistics = new IdentityStatistics<>();

    private final IntSummaryStatistics callCount = new IntSummaryStatistics();
    private final IntSummaryStatistics callCountIndirect = new IntSummaryStatistics();
    private final IntSummaryStatistics callCountDirect = new IntSummaryStatistics();
    private final IntSummaryStatistics callCountDirectDispatched = new IntSummaryStatistics();
    private final IntSummaryStatistics callCountDirectInlined = new IntSummaryStatistics();
    private final IntSummaryStatistics callCountDirectCloned = new IntSummaryStatistics();
    private final IntSummaryStatistics callCountDirectNotCloned = new IntSummaryStatistics();
    private final IntSummaryStatistics loopCount = new IntSummaryStatistics();

    private final LongSummaryStatistics compilationTime = new LongSummaryStatistics();
    private final LongSummaryStatistics compilationTimeTruffleTier = new LongSummaryStatistics();
    private final LongSummaryStatistics compilationTimeGraalTier = new LongSummaryStatistics();
    private final LongSummaryStatistics compilationTimeCodeInstallation = new LongSummaryStatistics();

    private final IntSummaryStatistics truffleTierNodeCount = new IntSummaryStatistics();
    private final IdentityStatistics<Class<?>> truffleTierNodeStatistics = new IdentityStatistics<>();
    private final IntSummaryStatistics graalTierNodeCount = new IntSummaryStatistics();
    private final IdentityStatistics<Class<?>> graalTierNodeStatistics = new IdentityStatistics<>();

    private final IntSummaryStatistics compilationResultCodeSize = new IntSummaryStatistics();
    private final IntSummaryStatistics compilationResultExceptionHandlers = new IntSummaryStatistics();
    private final IntSummaryStatistics compilationResultInfopoints = new IntSummaryStatistics();
    private final IdentityStatistics<String> compilationResultInfopointStatistics = new IdentityStatistics<>();
    private final IntSummaryStatistics compilationResultMarks = new IntSummaryStatistics();
    private final IntSummaryStatistics compilationResultTotalFrameSize = new IntSummaryStatistics();
    private final IntSummaryStatistics compilationResultDataPatches = new IntSummaryStatistics();

    private CompilationStatisticsListener() {
    }

    public static void install(GraalTruffleRuntime runtime) {
        if (TruffleCompilerOptions.getValue(TruffleCompilationStatistics) || TruffleCompilerOptions.getValue(TruffleCompilationStatisticDetails)) {
            runtime.addCompilationListener(new CompilationStatisticsListener());
        }
    }

    @Override
    public void notifyCompilationSplit(OptimizedDirectCallNode callNode) {
        splits++;
    }

    @Override
    public void notifyCompilationQueued(OptimizedCallTarget target) {
        queues++;
        if (firstCompilation == 0) {
            firstCompilation = System.nanoTime();
        }
        OptimizedCompilationProfile profile = target.getCompilationProfile();
        if (profile != null) {
            timeToQueue.accept(System.nanoTime() - profile.getTimestamp());
        }
    }

    @Override
    public void notifyCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {
        dequeues++;
    }

    @Override
    public void notifyCompilationFailed(OptimizedCallTarget target, StructuredGraph graph, Throwable t) {
        failures++;
        compilationMap.remove(target);
    }

    @Override
    public void notifyCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
        invalidations++;
    }

    private final Map<OptimizedCallTarget, CompilationLocal> compilationMap = new ConcurrentHashMap<>();

    @Override
    public void notifyCompilationStarted(OptimizedCallTarget target) {
        compilations++;
        CompilationLocal local = new CompilationLocal();
        local.compilationStarted = System.nanoTime();
        compilationMap.put(target, local);

        OptimizedCompilationProfile profile = target.getCompilationProfile();
        if (profile != null) {
            deferCompilations.accept(profile.getDeferredCount());
            timeToCompilation.accept(local.compilationStarted - profile.getTimestamp());
        }
    }

    @Override
    public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph) {
        compilationMap.get(target).truffleTierFinished = System.nanoTime();

        nodeStatistics.accept(nodeClasses(target, inliningDecision));

        CallTargetNodeStatistics callTargetStat = new CallTargetNodeStatistics(target, inliningDecision);
        nodeCount.accept(callTargetStat.getNodeCount());
        nodeCountTrivial.accept(callTargetStat.getNodeCountTrivial());
        nodeCountNonTrivial.accept(callTargetStat.getNodeCountNonTrivial());
        nodeCountMonomorphic.accept(callTargetStat.getNodeCountMonomorphic());
        nodeCountPolymorphic.accept(callTargetStat.getNodeCountPolymorphic());
        nodeCountMegamorphic.accept(callTargetStat.getNodeCountMegamorphic());

        callCount.accept(callTargetStat.getCallCount());
        callCountIndirect.accept(callTargetStat.getCallCountIndirect());
        callCountDirect.accept(callTargetStat.getCallCountDirect());
        callCountDirectDispatched.accept(callTargetStat.getCallCountDirectDispatched());
        callCountDirectInlined.accept(callTargetStat.getCallCountDirectInlined());
        callCountDirectCloned.accept(callTargetStat.getCallCountDirectCloned());
        callCountDirectNotCloned.accept(callTargetStat.getCallCountDirectNotCloned());
        loopCount.accept(callTargetStat.getLoopCount());

        truffleTierNodeCount.accept(graph.getNodeCount());
        if (TruffleCompilerOptions.getValue(TruffleCompilationStatisticDetails)) {
            truffleTierNodeStatistics.accept(nodeClasses(graph));
        }
    }

    @Override
    public void notifyCompilationGraalTierFinished(OptimizedCallTarget target, StructuredGraph graph) {
        compilationMap.get(target).graalTierFinished = System.nanoTime();
        graalTierNodeCount.accept(graph.getNodeCount());

        if (TruffleCompilerOptions.getValue(TruffleCompilationStatisticDetails)) {
            graalTierNodeStatistics.accept(nodeClasses(graph));
        }
    }

    private static Collection<String> infopoints(CompilationResult result) {
        Collection<String> infopoints = new ArrayList<>();
        for (Infopoint infopoint : result.getInfopoints()) {
            infopoints.add(infopoint.reason.toString());
        }
        return infopoints;
    }

    private static Collection<Class<?>> nodeClasses(OptimizedCallTarget target, TruffleInlining inliningDecision) {
        Collection<Class<?>> nodeClasses = new ArrayList<>();
        for (Node node : target.nodeIterable(inliningDecision)) {
            if (node != null) {
                nodeClasses.add(node.getClass());
            }
        }
        return nodeClasses;
    }

    private static Collection<Class<?>> nodeClasses(StructuredGraph graph) {
        Collection<Class<?>> classes = new ArrayList<>();
        for (org.graalvm.compiler.graph.Node node : graph.getNodes()) {
            classes.add(node.getClass());
        }
        return classes;
    }

    @Override
    public void notifyCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, StructuredGraph graph, CompilationResult result) {
        success++;
        long compilationDone = System.nanoTime();

        CompilationLocal local = compilationMap.get(target);
        compilationMap.remove(target);

        compilationTime.accept(compilationDone - local.compilationStarted);
        compilationTimeTruffleTier.accept(local.truffleTierFinished - local.compilationStarted);
        compilationTimeGraalTier.accept(local.graalTierFinished - local.truffleTierFinished);
        compilationTimeCodeInstallation.accept(compilationDone - local.graalTierFinished);

        compilationResultCodeSize.accept(result.getTargetCodeSize());
        compilationResultTotalFrameSize.accept(result.getTotalFrameSize());
        compilationResultExceptionHandlers.accept(result.getExceptionHandlers().size());
        compilationResultInfopoints.accept(result.getInfopoints().size());
        compilationResultInfopointStatistics.accept(infopoints(result));
        compilationResultMarks.accept(result.getMarks().size());
        compilationResultDataPatches.accept(result.getDataPatches().size());
    }

    @Override
    public void notifyShutdown(GraalTruffleRuntime rt) {
        printStatistics(rt);
    }

    public void printStatistics(GraalTruffleRuntime rt) {
        long endTime = System.nanoTime();
        rt.log("Truffle compilation statistics:");
        printStatistic(rt, "Compilations", compilations);
        printStatistic(rt, "  Success", success);
        printStatistic(rt, "  Failed", failures);
        printStatistic(rt, "  Interrupted", compilations - (success + failures));
        printStatistic(rt, "Invalidated", invalidations);
        printStatistic(rt, "Queues", queues);
        printStatistic(rt, "Dequeues", dequeues);
        printStatistic(rt, "Splits", splits);
        printStatistic(rt, "Compilation Accuracy", 1.0 - invalidations / (double) compilations);
        printStatistic(rt, "Queue Accuracy", 1.0 - dequeues / (double) queues);
        printStatistic(rt, "Compilation Utilization", compilationTime.getSum() / (double) (endTime - firstCompilation));
        printStatistic(rt, "Remaining Compilation Queue", rt.getCompilationQueueSize());
        printStatistic(rt, "Times defered until compilation", deferCompilations);

        printStatisticTime(rt, "Time to queue", timeToQueue);
        printStatisticTime(rt, "Time to compilation", timeToCompilation);

        printStatisticTime(rt, "Compilation time", compilationTime);
        printStatisticTime(rt, "  Truffle Tier", compilationTimeTruffleTier);
        printStatisticTime(rt, "  Graal Tier", compilationTimeGraalTier);
        printStatisticTime(rt, "  Code Installation", compilationTimeCodeInstallation);

        printStatistic(rt, "Truffle node count", nodeCount);
        printStatistic(rt, "  Trivial", nodeCountTrivial);
        printStatistic(rt, "  Non Trivial", nodeCountNonTrivial);
        printStatistic(rt, "    Monomorphic", nodeCountMonomorphic);
        printStatistic(rt, "    Polymorphic", nodeCountPolymorphic);
        printStatistic(rt, "    Megamorphic", nodeCountMegamorphic);
        printStatistic(rt, "Truffle call count", callCount);
        printStatistic(rt, "  Indirect", callCountIndirect);
        printStatistic(rt, "  Direct", callCountDirect);
        printStatistic(rt, "    Dispatched", callCountDirectDispatched);
        printStatistic(rt, "    Inlined", callCountDirectInlined);
        printStatistic(rt, "    ----------");
        printStatistic(rt, "    Cloned", callCountDirectCloned);
        printStatistic(rt, "    Not Cloned", callCountDirectNotCloned);
        printStatistic(rt, "Truffle loops", loopCount);
        printStatistic(rt, "Graal node count");
        printStatistic(rt, "  After Truffle Tier", truffleTierNodeCount);
        printStatistic(rt, "  After Graal Tier", graalTierNodeCount);

        printStatistic(rt, "Graal comilation result");
        printStatistic(rt, "  Code size", compilationResultCodeSize);
        printStatistic(rt, "  Total frame size", compilationResultTotalFrameSize);
        printStatistic(rt, "  Exception handlers", compilationResultExceptionHandlers);
        printStatistic(rt, "  Infopoints", compilationResultInfopoints);
        compilationResultInfopointStatistics.printStatistics(rt, identity());
        printStatistic(rt, "  Marks", compilationResultMarks);
        printStatistic(rt, "  Data references", compilationResultDataPatches);

        if (TruffleCompilerOptions.getValue(TruffleCompilationStatisticDetails)) {
            printStatistic(rt, "Truffle nodes");
            nodeStatistics.printStatistics(rt, Class::getSimpleName);
            printStatistic(rt, "Graal nodes after Truffle tier");
            truffleTierNodeStatistics.printStatistics(rt, Class::getSimpleName);
            printStatistic(rt, "Graal nodes after Graal tier");
            graalTierNodeStatistics.printStatistics(rt, Class::getSimpleName);
        }
    }

    private static void printStatistic(GraalTruffleRuntime rt, String label) {
        rt.log(String.format("  %-50s: ", label));
    }

    private static void printStatistic(GraalTruffleRuntime rt, String label, int value) {
        rt.log(String.format("  %-50s: %d", label, value));
    }

    private static void printStatistic(GraalTruffleRuntime rt, String label, double value) {
        rt.log(String.format("  %-50s: %f", label, value));
    }

    private static void printStatistic(GraalTruffleRuntime rt, String label, IntSummaryStatistics value) {
        rt.log(String.format("  %-50s: count=%4d, sum=%8d, min=%8d, average=%12.2f, max=%8d ", label, value.getCount(), value.getSum(), value.getMin(), value.getAverage(), value.getMax()));
    }

    private static void printStatisticTime(GraalTruffleRuntime rt, String label, LongSummaryStatistics value) {
        rt.log(String.format("  %-50s: count=%4d, sum=%8d, min=%8d, average=%12.2f, max=%8d (milliseconds)", label, value.getCount(), value.getSum() / 1000000, value.getMin() / 1000000,
                        value.getAverage() / 1e6, value.getMax() / 1000000));
    }

    private static final class IdentityStatistics<T> {

        final Map<T, IntSummaryStatistics> types = new HashMap<>();

        public void printStatistics(GraalTruffleRuntime rt, Function<T, String> toStringFunction) {

            SortedSet<T> sortedSet = new TreeSet<>(Comparator.comparing((T c) -> -types.get(c).getSum()));
            sortedSet.addAll(types.keySet());
            sortedSet.forEach(c -> {
                printStatistic(rt, String.format("    %s", toStringFunction.apply(c)), types.get(c));
            });
        }

        public void accept(Collection<T> elements) {
            /* First compute the histogram. */
            HashMap<T, Integer> histogram = new HashMap<>();
            for (T e : elements) {
                histogram.compute(e, (key, count) -> (count == null) ? 1 : count + 1);
            }

            /* Then create the summary statistics. */
            for (Map.Entry<T, Integer> entry : histogram.entrySet()) {
                T element = entry.getKey();
                Integer count = entry.getValue();
                types.computeIfAbsent(element, key -> new IntSummaryStatistics()).accept(count.intValue());
            }
        }
    }

    private static final class CallTargetNodeStatistics {

        // nodeCount = truffleNodeCountTrivial + truffleNodeCountNonTrivial
        private int nodeCountTrivial;
        private int nodeCountNonTrivial;
        private int nodeCountMonomorphic;
        private int nodeCountPolymorphic;
        private int nodeCountMegamorphic;

        // callCount = truffleCallCountDirect + truffleCallCountIndirect
        private int callCountIndirect;
        // callCountDirect = truffleCallCountDirectDispatched + truffleCallCountDirectInlined
        private int callCountDirectDispatched;
        private int callCountDirectInlined;
        private int callCountDirectCloned;
        private int callCountDirectNotCloned;
        private int loopCount;

        CallTargetNodeStatistics(OptimizedCallTarget target, TruffleInlining inliningDecision) {
            target.accept((CallTreeNodeVisitor) this::visitNode, inliningDecision);

        }

        private boolean visitNode(List<TruffleInlining> stack, Node node) {
            if (node == null) {
                return true;
            }

            NodeCost cost = node.getCost();
            if (cost.isTrivial()) {
                nodeCountTrivial++;
            } else {
                nodeCountNonTrivial++;
                if (cost == NodeCost.MONOMORPHIC) {
                    nodeCountMonomorphic++;
                } else if (cost == NodeCost.POLYMORPHIC) {
                    nodeCountPolymorphic++;
                } else if (cost == NodeCost.MEGAMORPHIC) {
                    nodeCountMegamorphic++;
                }
            }

            if (node instanceof DirectCallNode) {
                TruffleInliningDecision decision = CallTreeNodeVisitor.getCurrentInliningDecision(stack);
                if (decision != null && decision.getProfile().getCallNode() == node && decision.isInline()) {
                    callCountDirectInlined++;
                } else {
                    callCountDirectDispatched++;
                }
                if (decision != null && decision.getProfile().getCallNode().isCallTargetCloned()) {
                    callCountDirectCloned++;
                } else {
                    callCountDirectNotCloned++;
                }
            } else if (node instanceof IndirectCallNode) {
                callCountIndirect++;
            } else if (node instanceof LoopNode) {
                loopCount++;
            }

            return true;
        }

        public int getCallCountDirectCloned() {
            return callCountDirectCloned;
        }

        public int getCallCountDirectNotCloned() {
            return callCountDirectNotCloned;
        }

        public int getNodeCount() {
            return nodeCountTrivial + nodeCountNonTrivial;
        }

        public int getCallCount() {
            return getCallCountDirect() + callCountIndirect;
        }

        public int getCallCountDirect() {
            return callCountDirectDispatched + callCountDirectInlined;
        }

        public int getNodeCountTrivial() {
            return nodeCountTrivial;
        }

        public int getNodeCountNonTrivial() {
            return nodeCountNonTrivial;
        }

        public int getNodeCountMonomorphic() {
            return nodeCountMonomorphic;
        }

        public int getNodeCountPolymorphic() {
            return nodeCountPolymorphic;
        }

        public int getNodeCountMegamorphic() {
            return nodeCountMegamorphic;
        }

        public int getCallCountIndirect() {
            return callCountIndirect;
        }

        public int getCallCountDirectDispatched() {
            return callCountDirectDispatched;
        }

        public int getCallCountDirectInlined() {
            return callCountDirectInlined;
        }

        public int getLoopCount() {
            return loopCount;
        }
    }

    private static class CompilationLocal {

        private long compilationStarted;
        private long truffleTierFinished;
        private long graalTierFinished;

    }

}
