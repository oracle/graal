/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle.debug;

import com.oracle.jvmci.code.CompilationResult;
import static java.util.function.Function.*;
import static java.util.stream.Collectors.*;

import java.io.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.truffle.*;
import com.oracle.graal.truffle.TruffleInlining.CallTreeNodeVisitor;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.nodes.Node;

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
        if (TruffleCompilerOptions.TruffleCompilationStatistics.getValue() || TruffleCompilerOptions.TruffleCompilationStatisticDetails.getValue()) {
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
        timeToQueue.accept(System.nanoTime() - target.getCompilationProfile().getTimestamp());
    }

    @Override
    public void notifyCompilationDequeued(OptimizedCallTarget target, Object source, CharSequence reason) {
        dequeues++;
    }

    @Override
    public void notifyCompilationFailed(OptimizedCallTarget target, StructuredGraph graph, Throwable t) {
        failures++;
    }

    @Override
    public void notifyCompilationInvalidated(OptimizedCallTarget target, Object source, CharSequence reason) {
        invalidations++;
    }

    private final ThreadLocal<CompilationLocal> compilationLocal = new ThreadLocal<>();

    @Override
    public void notifyCompilationStarted(OptimizedCallTarget target) {
        compilations++;
        CompilationLocal local = new CompilationLocal();
        local.compilationStarted = System.nanoTime();
        compilationLocal.set(local);

        deferCompilations.accept(target.getCompilationProfile().getDeferedCount());
        timeToCompilation.accept(local.compilationStarted - target.getCompilationProfile().getTimestamp());
    }

    @Override
    public void notifyCompilationTruffleTierFinished(OptimizedCallTarget target, StructuredGraph graph) {
        compilationLocal.get().truffleTierFinished = System.nanoTime();

        nodeStatistics.accept(target.nodeStream(true).filter(n -> n != null).map(node -> node.getClass()));

        CallTargetNodeStatistics callTargetStat = new CallTargetNodeStatistics(target);
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
        if (TruffleCompilerOptions.TruffleCompilationStatisticDetails.getValue()) {
            truffleTierNodeStatistics.accept(nodeClassStream(graph));
        }
    }

    @Override
    public void notifyCompilationGraalTierFinished(OptimizedCallTarget target, StructuredGraph graph) {
        compilationLocal.get().graalTierFinished = System.nanoTime();
        graalTierNodeCount.accept(graph.getNodeCount());

        if (TruffleCompilerOptions.TruffleCompilationStatisticDetails.getValue()) {
            graalTierNodeStatistics.accept(nodeClassStream(graph));
        }
    }

    private static Stream<Class<?>> nodeClassStream(StructuredGraph graph) {
        return StreamSupport.stream(graph.getNodes().spliterator(), false).map(node -> node.getClass());
    }

    @Override
    public void notifyCompilationSuccess(OptimizedCallTarget target, StructuredGraph graph, CompilationResult result) {
        success++;
        long compilationDone = System.nanoTime();

        CompilationLocal local = compilationLocal.get();

        compilationTime.accept(compilationDone - local.compilationStarted);
        compilationTimeTruffleTier.accept(local.truffleTierFinished - local.compilationStarted);
        compilationTimeGraalTier.accept(local.graalTierFinished - local.truffleTierFinished);
        compilationTimeCodeInstallation.accept(compilationDone - local.graalTierFinished);

        compilationResultCodeSize.accept(result.getTargetCodeSize());
        compilationResultTotalFrameSize.accept(result.getTotalFrameSize());
        compilationResultExceptionHandlers.accept(result.getExceptionHandlers().size());
        compilationResultInfopoints.accept(result.getInfopoints().size());
        compilationResultInfopointStatistics.accept(result.getInfopoints().stream().map(e -> e.reason.toString()));
        compilationResultMarks.accept(result.getMarks().size());
        compilationResultDataPatches.accept(result.getDataPatches().size());
    }

    @Override
    public void notifyShutdown(GraalTruffleRuntime runtime) {
        printStatistics(runtime, OUT);
    }

    public void printStatistics(GraalTruffleRuntime runtime, PrintStream out) {
        long endTime = System.nanoTime();
        out.println("Truffle compilation statistics:");
        printStatistic("Compilations", compilations);
        printStatistic("  Success", success);
        printStatistic("  Failed", failures);
        printStatistic("  Interrupted", compilations - (success + failures));
        printStatistic("Invalidated", invalidations);
        printStatistic("Queues", queues);
        printStatistic("Dequeues", dequeues);
        printStatistic("Splits", splits);
        printStatistic("Compilation Accuracy", 1.0 - invalidations / (double) compilations);
        printStatistic("Queue Accuracy", 1.0 - dequeues / (double) queues);
        printStatistic("Compilation Utilization", compilationTime.getSum() / (double) (endTime - firstCompilation));
        printStatistic("Remaining Compilation Queue", runtime.getQueuedCallTargets().size());
        printStatistic("Times defered until compilation", deferCompilations);

        printStatisticTime("Time to queue", timeToQueue);
        printStatisticTime("Time to compilation", timeToCompilation);

        printStatisticTime("Compilation time", compilationTime);
        printStatisticTime("  Truffle Tier", compilationTimeTruffleTier);
        printStatisticTime("  Graal Tier", compilationTimeGraalTier);
        printStatisticTime("  Code Installation", compilationTimeCodeInstallation);

        printStatistic("Truffle node count", nodeCount);
        printStatistic("  Trivial", nodeCountTrivial);
        printStatistic("  Non Trivial", nodeCountNonTrivial);
        printStatistic("    Monomorphic", nodeCountMonomorphic);
        printStatistic("    Polymorphic", nodeCountPolymorphic);
        printStatistic("    Megamorphic", nodeCountMegamorphic);
        printStatistic("Truffle call count", callCount);
        printStatistic("  Indirect", callCountIndirect);
        printStatistic("  Direct", callCountDirect);
        printStatistic("    Dispatched", callCountDirectDispatched);
        printStatistic("    Inlined", callCountDirectInlined);
        printStatistic("    ----------");
        printStatistic("    Cloned", callCountDirectCloned);
        printStatistic("    Not Cloned", callCountDirectNotCloned);
        printStatistic("Truffle loops", loopCount);
        printStatistic("Graal node count");
        printStatistic("  After Truffle Tier", truffleTierNodeCount);
        printStatistic("  After Graal Tier", graalTierNodeCount);

        printStatistic("Graal comilation result");
        printStatistic("  Code size", compilationResultCodeSize);
        printStatistic("  Total frame size", compilationResultTotalFrameSize);
        printStatistic("  Exception handlers", compilationResultExceptionHandlers);
        printStatistic("  Infopoints", compilationResultInfopoints);
        compilationResultInfopointStatistics.printStatistics(identity());
        printStatistic("  Marks", compilationResultMarks);
        printStatistic("  Data references", compilationResultDataPatches);

        if (TruffleCompilerOptions.TruffleCompilationStatisticDetails.getValue()) {
            printStatistic("Truffle nodes");
            nodeStatistics.printStatistics(Class::getSimpleName);
            printStatistic("Graal nodes after Truffle tier");
            truffleTierNodeStatistics.printStatistics(Class::getSimpleName);
            printStatistic("Graal nodes after Graal tier");
            graalTierNodeStatistics.printStatistics(Class::getSimpleName);
        }
    }

    private static void printStatistic(String label) {
        OUT.printf("  %-50s: %n", label);
    }

    private static void printStatistic(String label, int value) {
        OUT.printf("  %-50s: %d%n", label, value);
    }

    private static void printStatistic(String label, double value) {
        OUT.printf("  %-50s: %f%n", label, value);
    }

    private static void printStatistic(String label, IntSummaryStatistics value) {
        OUT.printf("  %-50s: count=%4d, sum=%8d, min=%8d, average=%12.2f, max=%8d %n", label, value.getCount(), value.getSum(), value.getMin(), value.getAverage(), value.getMax());
    }

    private static void printStatisticTime(String label, LongSummaryStatistics value) {
        OUT.printf("  %-50s: count=%4d, sum=%8d, min=%8d, average=%12.2f, max=%8d (milliseconds)%n", label, value.getCount(), value.getSum() / 1000000, value.getMin() / 1000000,
                        value.getAverage() / 1e6, value.getMax() / 1000000);
    }

    private static final class IdentityStatistics<T> {

        final Map<T, IntSummaryStatistics> types = new HashMap<>();

        public void printStatistics(Function<T, String> toStringFunction) {
            types.keySet().stream().sorted(Comparator.comparing(c -> -types.get(c).getSum())).//
            forEach(c -> {
                printStatistic(String.format("    %s", toStringFunction.apply(c)), types.get(c));
            });
        }

        public void accept(Stream<T> classes) {
            classes.collect(groupingBy(identity(), counting())).//
            forEach((clazz, count) -> {
                types.computeIfAbsent(clazz, c -> new IntSummaryStatistics()).accept(count.intValue());
            });
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

        public CallTargetNodeStatistics(OptimizedCallTarget target) {
            target.accept((CallTreeNodeVisitor) this::visitNode, true);

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
