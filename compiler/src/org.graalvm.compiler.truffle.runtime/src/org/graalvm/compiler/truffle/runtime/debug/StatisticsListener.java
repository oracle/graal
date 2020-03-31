/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.debug;

import com.oracle.truffle.api.TruffleLogger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

import org.graalvm.compiler.truffle.common.TruffleCompilerListener.CompilationResultInfo;
import org.graalvm.compiler.truffle.common.TruffleCompilerListener.GraphInfo;
import org.graalvm.compiler.truffle.runtime.AbstractGraalTruffleRuntimeListener;
import org.graalvm.compiler.truffle.runtime.EngineData;
import org.graalvm.compiler.truffle.runtime.GraalTruffleRuntime;
import org.graalvm.compiler.truffle.runtime.OptimizedCallTarget;
import org.graalvm.compiler.truffle.runtime.OptimizedDirectCallNode;
import org.graalvm.compiler.truffle.runtime.TruffleInlining;
import org.graalvm.compiler.truffle.runtime.TruffleInlining.CallTreeNodeVisitor;
import org.graalvm.compiler.truffle.runtime.TruffleInliningDecision;

import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;

public final class StatisticsListener extends AbstractGraalTruffleRuntimeListener {

    private long firstCompilation;

    private int compilations;
    private int invalidations;
    private int failures;
    private int success;
    private int queues;
    private int dequeues;
    private int splits;

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
    private final IdentityStatistics<String> truffleTierNodeStatistics = new IdentityStatistics<>();
    private final IntSummaryStatistics graalTierNodeCount = new IntSummaryStatistics();
    private final IdentityStatistics<String> graalTierNodeStatistics = new IdentityStatistics<>();

    private final IntSummaryStatistics compilationResultCodeSize = new IntSummaryStatistics();
    private final IntSummaryStatistics compilationResultExceptionHandlers = new IntSummaryStatistics();
    private final IntSummaryStatistics compilationResultInfopoints = new IntSummaryStatistics();
    private final IdentityStatistics<String> compilationResultInfopointStatistics = new IdentityStatistics<>();
    private final IntSummaryStatistics compilationResultMarks = new IntSummaryStatistics();
    private final IntSummaryStatistics compilationResultTotalFrameSize = new IntSummaryStatistics();
    private final IntSummaryStatistics compilationResultDataPatches = new IntSummaryStatistics();

    private StatisticsListener(GraalTruffleRuntime runtime) {
        super(runtime);
    }

    /**
     * Records interesting points in time for the current compilation.
     */
    private final ThreadLocal<Times> compilationTimes = new ThreadLocal<>();

    public static void install(GraalTruffleRuntime runtime) {
        runtime.addListener(new StatisticsDispatcher(runtime));
    }

    public static StatisticsListener createEngineListener(GraalTruffleRuntime runtime) {
        return new StatisticsListener(runtime);
    }

    @Override
    public synchronized void onCompilationSplit(OptimizedDirectCallNode callNode) {
        splits++;
    }

    @Override
    public synchronized void onCompilationQueued(OptimizedCallTarget target) {
        queues++;
        if (firstCompilation == 0) {
            firstCompilation = System.nanoTime();
        }
        long timeStamp = target.getInitializedTimestamp();
        if (timeStamp != 0) {
            timeToQueue.accept(System.nanoTime() - timeStamp);
        }
    }

    @Override
    public synchronized void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {
        dequeues++;
    }

    @Override
    public synchronized void onCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
        invalidations++;
    }

    @Override
    public synchronized void onCompilationStarted(OptimizedCallTarget target) {
        compilations++;
        final Times times = new Times();
        compilationTimes.set(times);
        long timeStamp = target.getInitializedTimestamp();
        if (timeStamp != 0) {
            timeToCompilation.accept(times.compilationStarted - timeStamp);
        }
    }

    @Override
    public synchronized void onCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph) {
        final Times times = compilationTimes.get();
        times.truffleTierFinished = System.nanoTime();
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
        if (target.engine.callTargetStatisticDetails) {
            truffleTierNodeStatistics.accept(Arrays.asList(graph.getNodeTypes(true)));
        }
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

    @Override
    public synchronized void onCompilationGraalTierFinished(OptimizedCallTarget target, GraphInfo graph) {
        final Times times = compilationTimes.get();
        times.graalTierFinished = System.nanoTime();
        graalTierNodeCount.accept(graph.getNodeCount());
        if (target.engine.callTargetStatisticDetails) {
            graalTierNodeStatistics.accept(Arrays.asList(graph.getNodeTypes(true)));
        }
    }

    @Override
    public synchronized void onCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph, CompilationResultInfo result) {
        success++;
        long compilationDone = System.nanoTime();

        Times times = compilationTimes.get();

        compilationTime.accept(compilationDone - times.compilationStarted);
        compilationTimeTruffleTier.accept(times.truffleTierFinished - times.compilationStarted);
        compilationTimeGraalTier.accept(times.graalTierFinished - times.truffleTierFinished);
        compilationTimeCodeInstallation.accept(compilationDone - times.graalTierFinished);

        compilationResultCodeSize.accept(result.getTargetCodeSize());
        compilationResultTotalFrameSize.accept(result.getTotalFrameSize());
        compilationResultExceptionHandlers.accept(result.getExceptionHandlersCount());
        compilationResultInfopoints.accept(result.getInfopointsCount());
        compilationResultInfopointStatistics.accept(Arrays.asList(result.getInfopoints()));
        compilationResultMarks.accept(result.getMarksCount());
        compilationResultDataPatches.accept(result.getDataPatchesCount());
    }

    @Override
    public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout) {
        failures++;
        final Times times = compilationTimes.get();
        compilationTime.accept(System.nanoTime() - times.compilationStarted);
    }

    @Override
    public void onEngineClosed(EngineData runtimeData) {
        printStatistics(runtimeData);
    }

    private void printStatistics(EngineData runtimeData) {
        GraalTruffleRuntime rt = runtime;
        long endTime = System.nanoTime();
        StringWriter logMessage = new StringWriter();
        try (PrintWriter out = new PrintWriter(logMessage)) {
            out.println("Truffle runtime statistics for engine " + runtimeData.id);
            printStatistic(out, "Compilations", compilations);
            printStatistic(out, "  Success", success);
            printStatistic(out, "  Failed", failures);
            printStatistic(out, "  Interrupted", compilations - (success + failures));
            printStatistic(out, "Invalidated", invalidations);
            printStatistic(out, "Queues", queues);
            printStatistic(out, "Dequeues", dequeues);
            printStatistic(out, "Splits", splits);
            printStatistic(out, "Compilation Accuracy", 1.0 - invalidations / (double) compilations);
            printStatistic(out, "Queue Accuracy", 1.0 - dequeues / (double) queues);
            printStatistic(out, "Compilation Utilization", compilationTime.getSum() / (double) (endTime - firstCompilation));
            printStatistic(out, "Remaining Compilation Queue", rt.getCompilationQueueSize());
            printStatisticTime(out, "Time to queue", timeToQueue);
            printStatisticTime(out, "Time to compilation", timeToCompilation);

            printStatisticTime(out, "Compilation time", compilationTime);
            printStatisticTime(out, "  Truffle Tier", compilationTimeTruffleTier);
            printStatisticTime(out, "  Graal Tier", compilationTimeGraalTier);
            printStatisticTime(out, "  Code Installation", compilationTimeCodeInstallation);

            printStatistic(out, "Truffle node count", nodeCount);
            printStatistic(out, "  Trivial", nodeCountTrivial);
            printStatistic(out, "  Non Trivial", nodeCountNonTrivial);
            printStatistic(out, "    Monomorphic", nodeCountMonomorphic);
            printStatistic(out, "    Polymorphic", nodeCountPolymorphic);
            printStatistic(out, "    Megamorphic", nodeCountMegamorphic);
            printStatistic(out, "Truffle call count", callCount);
            printStatistic(out, "  Indirect", callCountIndirect);
            printStatistic(out, "  Direct", callCountDirect);
            printStatistic(out, "    Dispatched", callCountDirectDispatched);
            printStatistic(out, "    Inlined", callCountDirectInlined);
            printStatistic(out, "    ----------");
            printStatistic(out, "    Cloned", callCountDirectCloned);
            printStatistic(out, "    Not Cloned", callCountDirectNotCloned);
            printStatistic(out, "Truffle loops", loopCount);
            printStatistic(out, "Graal node count");
            printStatistic(out, "  After Truffle Tier", truffleTierNodeCount);
            printStatistic(out, "  After Graal Tier", graalTierNodeCount);

            printStatistic(out, "Graal compilation result");
            printStatistic(out, "  Code size", compilationResultCodeSize);
            printStatistic(out, "  Total frame size", compilationResultTotalFrameSize);
            printStatistic(out, "  Exception handlers", compilationResultExceptionHandlers);
            printStatistic(out, "  Infopoints", compilationResultInfopoints);
            compilationResultInfopointStatistics.printStatistics(out, Function.identity());
            printStatistic(out, "  Marks", compilationResultMarks);
            printStatistic(out, "  Data references", compilationResultDataPatches);

            if (runtimeData.callTargetStatisticDetails) {
                printStatistic(out, "Truffle nodes");
                nodeStatistics.printStatistics(out, Class::getSimpleName);
                printStatistic(out, "Graal nodes after Truffle tier");
                truffleTierNodeStatistics.printStatistics(out, Function.identity());
                printStatistic(out, "Graal nodes after Graal tier");
                graalTierNodeStatistics.printStatistics(out, Function.identity());
            }
        }
        TruffleLogger logger = runtimeData.getLogger();
        logger.log(Level.INFO, logMessage.toString());
    }

    private static void printStatistic(PrintWriter out, String label) {
        out.printf("  %-50s:%n", label);
    }

    private static void printStatistic(PrintWriter out, String label, int value) {
        out.printf("  %-50s: %d%n", label, value);
    }

    private static void printStatistic(PrintWriter out, String label, double value) {
        out.printf("  %-50s: %f%n", label, value);
    }

    private static void printStatistic(PrintWriter out, String label, IntSummaryStatistics value) {
        out.printf("  %-50s: count=%4d, sum=%8d, min=%8d, average=%12.2f, max=%8d%n", label, value.getCount(), value.getSum(), value.getMin(), value.getAverage(), value.getMax());
    }

    private static void printStatisticTime(PrintWriter out, String label, LongSummaryStatistics value) {
        out.printf("  %-50s: count=%4d, sum=%8d, min=%8d, average=%12.2f, max=%8d (milliseconds)%n", label, value.getCount(), value.getSum() / 1000000, value.getMin() / 1000000,
                        value.getAverage() / 1e6, value.getMax() / 1000000);
    }

    private static final class IdentityStatistics<T> {

        final Map<T, IntSummaryStatistics> types = new HashMap<>();

        public void printStatistics(PrintWriter out, Function<T, String> toStringFunction) {

            SortedSet<T> sortedSet = new TreeSet<>(Comparator.comparing((T c) -> -types.get(c).getSum()));
            sortedSet.addAll(types.keySet());
            sortedSet.forEach(c -> {
                printStatistic(out, String.format("    %s", toStringFunction.apply(c)), types.get(c));
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
                if (decision != null && decision.getProfile().getCallNode() == node && decision.shouldInline()) {
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

    static class Times {
        final long compilationStarted = System.nanoTime();
        long truffleTierFinished;
        long graalTierFinished;
    }

    private static final class StatisticsDispatcher extends AbstractGraalTruffleRuntimeListener {

        private StatisticsDispatcher(GraalTruffleRuntime runtime) {
            super(runtime);
        }

        @Override
        public void onCompilationQueued(OptimizedCallTarget target) {
            StatisticsListener listener = target.engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationQueued(target);
            }
        }

        @Override
        public void onCompilationStarted(OptimizedCallTarget target) {
            StatisticsListener listener = target.engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationStarted(target);
            }
        }

        @Override
        public void onCompilationSplit(OptimizedDirectCallNode callNode) {
            StatisticsListener listener = callNode.getCallTarget().engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationSplit(callNode);
            }
        }

        @Override
        public void onCompilationSplitFailed(OptimizedDirectCallNode callNode, CharSequence reason) {
            StatisticsListener listener = callNode.getCallTarget().engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationSplitFailed(callNode, reason);
            }
        }

        @Override
        public void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {
            StatisticsListener listener = target.engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationDequeued(target, source, reason);
            }
        }

        @Override
        public void onCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
            StatisticsListener listener = target.engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationInvalidated(target, source, reason);
            }
        }

        @Override
        public void onCompilationTruffleTierFinished(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph) {
            StatisticsListener listener = target.engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationTruffleTierFinished(target, inliningDecision, graph);
            }
        }

        @Override
        public void onCompilationGraalTierFinished(OptimizedCallTarget target, GraphInfo graph) {
            StatisticsListener listener = target.engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationGraalTierFinished(target, graph);
            }
        }

        @Override
        public void onCompilationSuccess(OptimizedCallTarget target, TruffleInlining inliningDecision, GraphInfo graph, CompilationResultInfo result) {
            StatisticsListener listener = target.engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationSuccess(target, inliningDecision, graph, result);
            }
        }

        @Override
        public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout) {
            StatisticsListener listener = target.engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationFailed(target, reason, bailout, permanentBailout);
            }
        }

        @Override
        public void onEngineClosed(EngineData runtimeData) {
            StatisticsListener listener = runtimeData.statisticsListener;
            if (listener != null) {
                listener.onEngineClosed(runtimeData);
            }
        }
    }
}
