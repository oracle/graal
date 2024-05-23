/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.runtime.debug;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;

import com.oracle.truffle.api.TruffleLogger;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeVisitor;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilerListener.CompilationResultInfo;
import com.oracle.truffle.compiler.TruffleCompilerListener.GraphInfo;
import com.oracle.truffle.runtime.AbstractCompilationTask;
import com.oracle.truffle.runtime.AbstractGraalTruffleRuntimeListener;
import com.oracle.truffle.runtime.EngineData;
import com.oracle.truffle.runtime.OptimizedTruffleRuntime;
import com.oracle.truffle.runtime.OptimizedCallTarget;
import com.oracle.truffle.runtime.OptimizedDirectCallNode;

public final class StatisticsListener extends AbstractGraalTruffleRuntimeListener {

    static final int EXPECTED_TIERS = 2;

    private long firstCompilation;

    private int compilations;
    private int invalidations;
    private int failures;
    private int temporaryBailouts;
    private int permanentBailouts;
    private int success;
    private int queues;
    private int dequeues;
    private int splits;

    private final IdentityStatistics<String> temporaryBailoutReasons = new IdentityStatistics<>();
    private final IdentityStatistics<String> permanentBailoutReasons = new IdentityStatistics<>();
    private final IdentityStatistics<String> failureReasons = new IdentityStatistics<>();
    private final IdentityStatistics<String> invalidatedReasons = new IdentityStatistics<>();
    private final IdentityStatistics<String> dequeuedReasons = new IdentityStatistics<>();

    private final TargetLongStatistics timeToQueue = new TargetLongStatistics();
    private final TargetLongStatistics timeInQueue = new TargetLongStatistics();

    private final TargetIntStatistics nodeCount = new TargetIntStatistics();
    private final TargetIntStatistics nodeCountTrivial = new TargetIntStatistics();
    private final TargetIntStatistics nodeCountNonTrivial = new TargetIntStatistics();
    private final TargetIntStatistics nodeCountMonomorphic = new TargetIntStatistics();
    private final TargetIntStatistics nodeCountPolymorphic = new TargetIntStatistics();
    private final TargetIntStatistics nodeCountMegamorphic = new TargetIntStatistics();
    private final IdentityStatistics<Class<?>> nodeStatistics = new IdentityStatistics<>();

    private final TargetIntStatistics callCount = new TargetIntStatistics();
    private final TargetIntStatistics callCountIndirect = new TargetIntStatistics();
    private final TargetIntStatistics callCountDirect = new TargetIntStatistics();
    private final TargetIntStatistics callCountDirectDispatched = new TargetIntStatistics();
    private final TargetIntStatistics callCountDirectInlined = new TargetIntStatistics();
    private final TargetIntStatistics callCountDirectCloned = new TargetIntStatistics();
    private final TargetIntStatistics callCountDirectNotCloned = new TargetIntStatistics();
    private final TargetIntStatistics loopCount = new TargetIntStatistics();

    private final CompilationStatistics[] tieredStatistics = new CompilationStatistics[EXPECTED_TIERS];
    {
        for (int i = 0; i < tieredStatistics.length; i++) {
            tieredStatistics[i] = new CompilationStatistics();
        }
    }

    static final class CompilationStatistics {

        final TargetLongStatistics compilationTime = new TargetLongStatistics();
        final TargetLongStatistics compilationTimeTruffleTier = new TargetLongStatistics();
        final TargetLongStatistics compilationTimeGraalTier = new TargetLongStatistics();
        final TargetLongStatistics compilationTimeCodeInstallation = new TargetLongStatistics();

        final TargetIntStatistics truffleTierNodeCount = new TargetIntStatistics();
        final IdentityStatistics<String> truffleTierNodeStatistics = new IdentityStatistics<>();
        final TargetIntStatistics graalTierNodeCount = new TargetIntStatistics();
        final IdentityStatistics<String> graalTierNodeStatistics = new IdentityStatistics<>();

        final TargetIntStatistics compilationResultCodeSize = new TargetIntStatistics();
        final TargetIntStatistics compilationResultExceptionHandlers = new TargetIntStatistics();
        final TargetIntStatistics compilationResultInfopoints = new TargetIntStatistics();
        final IdentityStatistics<String> compilationResultInfopointStatistics = new IdentityStatistics<>();
        final TargetIntStatistics compilationResultMarks = new TargetIntStatistics();
        final TargetIntStatistics compilationResultTotalFrameSize = new TargetIntStatistics();
        final TargetIntStatistics compilationResultDataPatches = new TargetIntStatistics();

    }

    private final Map<OptimizedCallTarget, Long> timeQueued = new HashMap<>();

    private StatisticsListener(OptimizedTruffleRuntime runtime) {
        super(runtime);
    }

    /**
     * Records interesting points in time for the current compilation.
     */
    private final ThreadLocal<CurrentCompilationStatistics> currentCompilationStatistics = new ThreadLocal<>();

    public static void install(OptimizedTruffleRuntime runtime) {
        runtime.addListener(new StatisticsDispatcher(runtime));
    }

    public static StatisticsListener createEngineListener(OptimizedTruffleRuntime runtime) {
        return new StatisticsListener(runtime);
    }

    @Override
    public synchronized void onCompilationSplit(OptimizedDirectCallNode callNode) {
        splits++;
    }

    @Override
    public synchronized void onCompilationQueued(OptimizedCallTarget target, int tier) {
        queues++;
        long currentTime = System.nanoTime();
        if (firstCompilation == 0) {
            firstCompilation = currentTime;
        }
        timeQueued.put(target, currentTime);
        long timeStamp = target.getInitializedTimestamp();
        if (timeStamp != 0) {
            timeToQueue.accept(currentTime - timeStamp, target);
        }
    }

    @Override
    public synchronized void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason, int tier) {
        dequeues++;
        dequeuedReasons.accept(Arrays.asList(Objects.toString(reason)), target);
        timeQueued.remove(target);
    }

    @Override
    public synchronized void onCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
        invalidations++;
        String useReason = reason == null ? "Unknown Reason" : reason.toString();
        invalidatedReasons.accept(Arrays.asList(useReason), target);
    }

    @Override
    public void onCompilationStarted(OptimizedCallTarget target, AbstractCompilationTask task) {
        compilations++;
        final CurrentCompilationStatistics times = new CurrentCompilationStatistics(task.tier());
        currentCompilationStatistics.set(times);
        Long timeStamp = timeQueued.get(target);
        if (timeStamp != null) {
            timeInQueue.accept(times.compilationStarted - timeStamp, target);
        }
        timeQueued.remove(target);
    }

    @Override
    public synchronized void onCompilationTruffleTierFinished(OptimizedCallTarget target, AbstractCompilationTask task, GraphInfo graph) {
        final CurrentCompilationStatistics current = currentCompilationStatistics.get();
        current.truffleTierFinished = System.nanoTime();
        nodeStatistics.accept(nodeClasses(task), target);

        CallTargetNodeStatistics callTargetStat = new CallTargetNodeStatistics(task);
        nodeCount.accept(callTargetStat.getNodeCount(), target);
        nodeCountTrivial.accept(callTargetStat.getNodeCountTrivial(), target);
        nodeCountNonTrivial.accept(callTargetStat.getNodeCountNonTrivial(), target);
        nodeCountMonomorphic.accept(callTargetStat.getNodeCountMonomorphic(), target);
        nodeCountPolymorphic.accept(callTargetStat.getNodeCountPolymorphic(), target);
        nodeCountMegamorphic.accept(callTargetStat.getNodeCountMegamorphic(), target);

        callCount.accept(callTargetStat.getCallCount(), target);
        callCountIndirect.accept(callTargetStat.getCallCountIndirect(), target);
        callCountDirect.accept(callTargetStat.getCallCountDirect(), target);
        callCountDirectDispatched.accept(callTargetStat.getCallCountDirectDispatched(), target);
        callCountDirectInlined.accept(callTargetStat.getCallCountDirectInlined(), target);
        callCountDirectCloned.accept(callTargetStat.getCallCountDirectCloned(), target);
        callCountDirectNotCloned.accept(callTargetStat.getCallCountDirectNotCloned(), target);
        loopCount.accept(callTargetStat.getLoopCount(), target);

        CompilationStatistics s = getStatisticsForTier(current.tier);
        s.truffleTierNodeCount.accept(graph.getNodeCount(), target);

        if (target.engine.callTargetStatisticDetails) {
            s.truffleTierNodeStatistics.accept(Arrays.asList(graph.getNodeTypes(true)), target);
        }
    }

    private CompilationStatistics getStatisticsForTier(int tier) {
        if (tier <= 0 || tier > 2) {
            throw new AssertionError("Unexpected tier");
        }
        return tieredStatistics[tier - 1];
    }

    private static Collection<Class<?>> nodeClasses(AbstractCompilationTask task) {
        Collection<Class<?>> nodeClasses = new ArrayList<>();
        for (TruffleCompilable ast : task.inlinedTargets()) {
            ((OptimizedCallTarget) ast).accept(new NodeVisitor() {
                @Override
                public boolean visit(Node node) {
                    if (node != null) {
                        nodeClasses.add(node.getClass());
                    }
                    return true;
                }
            });
        }
        return nodeClasses;
    }

    @Override
    public synchronized void onCompilationGraalTierFinished(OptimizedCallTarget target, GraphInfo graph) {
        final CurrentCompilationStatistics current = currentCompilationStatistics.get();
        current.graalTierFinished = System.nanoTime();

        CompilationStatistics s = getStatisticsForTier(current.tier);
        s.graalTierNodeCount.accept(graph.getNodeCount(), target);

        if (target.engine.callTargetStatisticDetails) {
            s.graalTierNodeStatistics.accept(Arrays.asList(graph.getNodeTypes(true)), target);
        }
    }

    @Override
    public synchronized void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, GraphInfo graph, CompilationResultInfo result) {
        success++;
        long compilationDone = System.nanoTime();

        CurrentCompilationStatistics current = currentCompilationStatistics.get();
        assert current.tier == task.tier();

        long compilationTime = compilationDone - current.compilationStarted;
        int codeSize = result.getTargetCodeSize();

        CompilationStatistics s = getStatisticsForTier(current.tier);

        s.compilationTime.accept(compilationTime, target);
        s.compilationTimeTruffleTier.accept(current.truffleTierFinished - current.compilationStarted, target);
        s.compilationTimeGraalTier.accept(current.graalTierFinished - current.truffleTierFinished, target);
        s.compilationTimeCodeInstallation.accept(compilationDone - current.graalTierFinished, target);

        s.compilationResultCodeSize.accept(codeSize, target);
        s.compilationResultTotalFrameSize.accept(result.getTotalFrameSize(), target);
        s.compilationResultExceptionHandlers.accept(result.getExceptionHandlersCount(), target);
        s.compilationResultInfopoints.accept(result.getInfopointsCount(), target);
        s.compilationResultInfopointStatistics.accept(Arrays.asList(result.getInfopoints()), target);
        s.compilationResultMarks.accept(result.getMarksCount(), target);
        s.compilationResultDataPatches.accept(result.getDataPatchesCount(), target);
    }

    @Override
    public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> lazyStackTrace) {
        if (bailout) {
            if (permanentBailout) {
                permanentBailouts++;
                permanentBailoutReasons.accept(Arrays.asList(reason), target);
            } else {
                temporaryBailouts++;
                temporaryBailoutReasons.accept(Arrays.asList(reason), target);
            }
        } else {
            failures++;
            failureReasons.accept(Arrays.asList(reason), target);
        }
        final CurrentCompilationStatistics times = currentCompilationStatistics.get();
        getStatisticsForTier(times.tier).compilationTime.accept(System.nanoTime() - times.compilationStarted, target);
    }

    @Override
    public void onEngineClosed(EngineData runtimeData) {
        printStatistics(runtimeData);
    }

    static String formatLabel(String s) {
        return s;
    }

    private void printStatistics(EngineData runtimeData) {
        OptimizedTruffleRuntime rt = runtime;
        long endTime = System.nanoTime();
        StringWriter logMessage = new StringWriter();
        try (PrintWriter out = new PrintWriter(logMessage)) {
            out.print("Truffle runtime statistics for engine " + runtimeData.id);

            printStatistic(out, "  Compilations", compilations);
            printStatistic(out, "    Success", success);
            printStatistic(out, "    Temporary Bailouts", temporaryBailouts);
            temporaryBailoutReasons.printStatistics(out, StatisticsListener::formatLabel, true, false);
            printStatistic(out, "    Permanent Bailouts", permanentBailouts);
            permanentBailoutReasons.printStatistics(out, StatisticsListener::formatLabel, true, false);
            printStatistic(out, "    Failed", failures);
            failureReasons.printStatistics(out, StatisticsListener::formatLabel, true, false);
            printStatistic(out, "    Interrupted", compilations - (success + failures + temporaryBailouts + permanentBailouts));
            printStatistic(out, "  Invalidated", invalidations);
            invalidatedReasons.printStatistics(out, StatisticsListener::formatLabel, true, false);
            printStatistic(out, "  Queues", queues);
            printStatistic(out, "  Dequeues", dequeues);
            dequeuedReasons.printStatistics(out, StatisticsListener::formatLabel, true, false);
            printStatistic(out, "  Splits", splits);
            printStatistic(out, "  Compilation Accuracy", 1.0 - invalidations / (double) compilations);
            printStatistic(out, "  Queue Accuracy", 1.0 - dequeues / (double) queues);
            long compilationTimeSum = 0;
            for (int i = 0; i < tieredStatistics.length; i++) {
                compilationTimeSum += tieredStatistics[i].compilationTime.getSum();
            }
            printStatistic(out, "  Compilation Utilization", compilationTimeSum / (double) (endTime - firstCompilation));
            printStatistic(out, "  Remaining Compilation Queue", rt.getCompilationQueueSize());
            printStatisticTime(out, "  Time to queue", timeToQueue);
            printStatisticTime(out, "  Time waiting in queue", timeInQueue);

            // GR-25014 Truffle node count statistics are broken with language agnostic inlining
            printStatistic(out, "---------------------------");
            printStatistic(out, "AST node statistics ");
            printStatistic(out, "  Truffle node count", nodeCount);
            printStatistic(out, "    Trivial", nodeCountTrivial);
            printStatistic(out, "    Non Trivial", nodeCountNonTrivial);
            printStatistic(out, "      Monomorphic", nodeCountMonomorphic);
            printStatistic(out, "      Polymorphic", nodeCountPolymorphic);
            printStatistic(out, "      Megamorphic", nodeCountMegamorphic);
            printStatistic(out, "  Truffle call count", callCount);
            printStatistic(out, "    Indirect", callCountIndirect);
            printStatistic(out, "    Direct", callCountDirect);
            printStatistic(out, "      Dispatched", callCountDirectDispatched);
            printStatistic(out, "      Inlined", callCountDirectInlined);
            printStatistic(out, "      ----------");
            printStatistic(out, "      Cloned", callCountDirectCloned);
            printStatistic(out, "      Not Cloned", callCountDirectNotCloned);
            printStatistic(out, "  Truffle loops", loopCount);

            if (runtimeData.callTargetStatisticDetails) {
                printStatistic(out, "Truffle nodes");
                nodeStatistics.printStatistics(out, Class::getSimpleName, false, true);
            }

            for (int tierIndex = 0; tierIndex < EXPECTED_TIERS; tierIndex++) {

                printStatistic(out, "---------------------------");
                printStatistic(out, "Compilation Tier " + (tierIndex + 1));

                CompilationStatistics s = tieredStatistics[tierIndex];

                printStatisticRate(out, "  Compilation Rate", s.compilationResultCodeSize.getSum() / (s.compilationTime.getSum() / 1_000_000_000d), "bytes/second");
                printStatisticRate(out, "    Truffle Tier Rate", s.compilationResultCodeSize.getSum() / (s.compilationTimeTruffleTier.getSum() / 1_000_000_000d), "bytes/second");
                printStatisticRate(out, "    Graal Tier Rate", s.compilationResultCodeSize.getSum() / (s.compilationTimeGraalTier.getSum() / 1_000_000_000d), "bytes/second");
                printStatisticRate(out, "    Installation Rate", s.compilationResultCodeSize.getSum() / (s.compilationTimeCodeInstallation.getSum() / 1_000_000_000d), "bytes/second");

                printStatisticTime(out, "  Time for compilation (us)", s.compilationTime);
                printStatisticTime(out, "    Truffle Tier (us)", s.compilationTimeTruffleTier);
                printStatisticTime(out, "    Graal Tier (us)", s.compilationTimeGraalTier);
                printStatisticTime(out, "    Code Installation (us)", s.compilationTimeCodeInstallation);

                printStatistic(out, "  Graal node count");
                printStatistic(out, "    After Truffle Tier", s.truffleTierNodeCount);
                printStatistic(out, "    After Graal Tier", s.graalTierNodeCount);

                printStatistic(out, "  Graal compilation result");
                printStatistic(out, "    Code size", s.compilationResultCodeSize);
                printStatistic(out, "    Total frame size", s.compilationResultTotalFrameSize);
                printStatistic(out, "    Exception handlers", s.compilationResultExceptionHandlers);
                printStatistic(out, "    Infopoints", s.compilationResultInfopoints);
                s.compilationResultInfopointStatistics.printStatistics(out, Function.identity(), false, true);
                printStatistic(out, "  Marks", s.compilationResultMarks);
                printStatistic(out, "  Data references", s.compilationResultDataPatches);

                if (runtimeData.callTargetStatisticDetails) {
                    printStatistic(out, "  Graal nodes after Truffle tier");
                    s.truffleTierNodeStatistics.printStatistics(out, Function.identity(), false, true);
                    printStatistic(out, "  Graal nodes after Graal tier");
                    s.graalTierNodeStatistics.printStatistics(out, Function.identity(), false, true);
                }
            }

        }
        TruffleLogger logger = runtimeData.getEngineLogger();
        logger.log(Level.INFO, logMessage.toString());
    }

    private static void printStatistic(PrintWriter out, String label) {
        out.printf("%n  %-30s:", label);
    }

    private static void printStatistic(PrintWriter out, String label, long value) {
        out.printf("%n  %-30s: %d", label, value);
    }

    private static void printStatistic(PrintWriter out, String label, double value) {
        out.printf("%n  %-30s: %f", label, value);
    }

    private static void printStatistic(PrintWriter out, String label, TargetIntStatistics value) {
        out.printf("%n  %-30s: count=%4d, sum=%10d, min=%8d, average=%12.2f, max=%8d, maxTarget=%s", label, value.getCount(), value.getSum(), value.getMin(), value.getAverage(), value.getMax(),
                        value.getMaxName());
    }

    private static void printStatisticTime(PrintWriter out, String label, TargetLongStatistics value) {
        out.printf("%n  %-30s: count=%4d, sum=%10d, min=%8d, average=%12.2f, max=%8d, maxTarget=%s", label, value.getCount(), value.getSum() / 1_000, value.getMin() / 1_000,
                        value.getAverage() / 1_000d, value.getMax() / 1_000, value.getMaxName());
    }

    private static void printStatisticRate(PrintWriter out, String label, double value, String unit) {
        out.printf("%n  %-30s: %12.2f %s", label, Double.isNaN(value) ? 0.0D : value, unit);
    }

    private static final class TargetIntStatistics extends IntSummaryStatistics {

        private String maxName;

        public void accept(int value, OptimizedCallTarget target) {
            if (value > getMax() && target != null) {
                this.maxName = target.getName();
            }
            super.accept(value);
        }

        public String getMaxName() {
            return maxName;
        }

        @Deprecated(since = "20.3")
        @Override
        public void accept(int value) {
            throw new UnsupportedOperationException();
        }

        @Deprecated(since = "20.3")
        @Override
        public void combine(IntSummaryStatistics other) {
            throw new UnsupportedOperationException();
        }

    }

    private static final class TargetLongStatistics extends LongSummaryStatistics {

        private String maxName;

        public void accept(long value, OptimizedCallTarget target) {
            if (value > getMax()) {
                maxName = target.getName();
            }
            super.accept(value);
        }

        public String getMaxName() {
            return maxName;
        }

        @Deprecated(since = "20.3")
        @Override
        public void accept(long value) {
            throw new UnsupportedOperationException();
        }

        @Deprecated(since = "20.3")
        @Override
        public void combine(LongSummaryStatistics other) {
            throw new UnsupportedOperationException();
        }

    }

    private static final class IdentityStatistics<T> {

        final Map<T, TargetIntStatistics> types = new HashMap<>();

        private int elementCount;

        public void printStatistics(PrintWriter out, Function<T, String> toStringFunction, boolean onlyCount, boolean normalize) {
            if (normalize) {
                normalize();
            }
            types.keySet().stream().sorted(Comparator.comparing((T c) -> -types.get(c).getSum())).forEach(c -> {
                String label = String.format("      %s", toStringFunction.apply(c));
                TargetIntStatistics statistic = types.get(c);
                if (onlyCount) {
                    printStatistic(out, label, statistic.getCount());
                } else {
                    printStatistic(out, label, statistic);
                }
            });
        }

        private void normalize() {
            /*
             * We also want to include the number of times an element had zero elements.
             */
            for (TargetIntStatistics stat : types.values()) {
                while (stat.getCount() < elementCount) {
                    stat.accept(0, null);
                }
            }
        }

        public void accept(Collection<T> elements, OptimizedCallTarget target) {
            this.elementCount++;
            /* First compute the histogram. */
            HashMap<T, Integer> histogram = new HashMap<>();
            for (T e : elements) {
                histogram.compute(e, (key, count) -> (count == null) ? 1 : count + 1);
            }

            /* Then create the summary statistics. */
            for (Map.Entry<T, Integer> entry : histogram.entrySet()) {
                T element = entry.getKey();
                Integer count = entry.getValue();
                types.computeIfAbsent(element, key -> new TargetIntStatistics()).accept(count.intValue(), target);
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

        CallTargetNodeStatistics(AbstractCompilationTask task) {
            for (TruffleCompilable ast : task.inlinedTargets()) {
                ((OptimizedCallTarget) ast).accept(this::visitNode);
            }
            callCountDirectInlined = task.countInlinedCalls();
            callCountDirectDispatched = task.countCalls() - callCountDirectInlined;
        }

        private boolean visitNode(Node node) {
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
                OptimizedDirectCallNode optimizedDirectCallNode = node instanceof OptimizedDirectCallNode ? ((OptimizedDirectCallNode) node) : null;
                if (optimizedDirectCallNode != null && optimizedDirectCallNode.getCallTarget().isSplit()) {
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

    static class CurrentCompilationStatistics {
        final long compilationStarted = System.nanoTime();
        long truffleTierFinished;
        long graalTierFinished;
        final int tier;

        CurrentCompilationStatistics(int tier) {
            this.tier = tier;
        }
    }

    private static final class StatisticsDispatcher extends AbstractGraalTruffleRuntimeListener {

        private StatisticsDispatcher(OptimizedTruffleRuntime runtime) {
            super(runtime);
        }

        @Override
        public void onCompilationQueued(OptimizedCallTarget target, int tier) {
            StatisticsListener listener = target.engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationQueued(target, tier);
            }
        }

        @Override
        public void onCompilationStarted(OptimizedCallTarget target, AbstractCompilationTask task) {
            StatisticsListener listener = target.engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationStarted(target, task);
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
        public void onCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason, int tier) {
            StatisticsListener listener = target.engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationDequeued(target, source, reason, tier);
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
        public void onCompilationTruffleTierFinished(OptimizedCallTarget target, AbstractCompilationTask task, GraphInfo graph) {
            StatisticsListener listener = target.engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationTruffleTierFinished(target, task, graph);
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
        public void onCompilationSuccess(OptimizedCallTarget target, AbstractCompilationTask task, GraphInfo graph, CompilationResultInfo result) {
            StatisticsListener listener = target.engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationSuccess(target, task, graph, result);
            }
        }

        @Override
        public void onCompilationFailed(OptimizedCallTarget target, String reason, boolean bailout, boolean permanentBailout, int tier, Supplier<String> lazyStackTrace) {
            StatisticsListener listener = target.engine.statisticsListener;
            if (listener != null) {
                listener.onCompilationFailed(target, reason, bailout, permanentBailout, tier, lazyStackTrace);
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
