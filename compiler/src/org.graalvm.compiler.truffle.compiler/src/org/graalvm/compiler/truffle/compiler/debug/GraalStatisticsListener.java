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
package org.graalvm.compiler.truffle.compiler.debug;

import static java.util.function.Function.identity;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleCompilationStatisticDetails;
import static org.graalvm.compiler.truffle.common.TruffleCompilerOptions.TruffleCompilationStatistics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.truffle.compiler.GraalCompilationListener;
import org.graalvm.compiler.truffle.compiler.TruffleCompilerImpl;
import org.graalvm.compiler.truffle.common.CompilableTruffleAST;
import org.graalvm.compiler.truffle.common.TruffleCompilerOptions;

import jdk.vm.ci.code.site.Infopoint;

public final class GraalStatisticsListener implements GraalCompilationListener {

    private int compilations;
    private int failures;
    private int success;

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

    private final TruffleCompilerImpl compiler;

    private GraalStatisticsListener(TruffleCompilerImpl compiler) {
        this.compiler = compiler;
    }

    /**
     * Records interesting points in time for the current compilation.
     */
    private final ThreadLocal<Times> compilationTimes = new ThreadLocal<>();

    public static void install(TruffleCompilerImpl compiler) {
        if (TruffleCompilerOptions.getValue(TruffleCompilationStatistics) || TruffleCompilerOptions.getValue(TruffleCompilationStatisticDetails)) {
            compiler.addListener(new GraalStatisticsListener(compiler));
        }
    }

    @Override
    public synchronized void onCompilationFailed(CompilableTruffleAST target, StructuredGraph graph, Throwable t) {
        failures++;
        compilationTime.accept(System.nanoTime() - compilationTimes.get().compilationStarted);
        compilationTimes.set(null);
    }

    @Override
    public synchronized void onCompilationStarted(CompilableTruffleAST target) {
        compilations++;
        compilationTimes.set(new Times());
    }

    @Override
    public synchronized void onCompilationTruffleTierFinished(CompilableTruffleAST target, StructuredGraph graph) {
        compilationTimes.get().truffleTierFinished = System.nanoTime();
        truffleTierNodeCount.accept(graph.getNodeCount());
        if (TruffleCompilerOptions.getValue(TruffleCompilationStatisticDetails)) {
            truffleTierNodeStatistics.accept(nodeClasses(graph));
        }
    }

    @Override
    public synchronized void onCompilationGraalTierFinished(CompilableTruffleAST target, StructuredGraph graph) {
        compilationTimes.get().graalTierFinished = System.nanoTime();
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

    private static Collection<Class<?>> nodeClasses(StructuredGraph graph) {
        Collection<Class<?>> classes = new ArrayList<>();
        for (org.graalvm.compiler.graph.Node node : graph.getNodes()) {
            classes.add(node.getClass());
        }
        return classes;
    }

    @Override
    public synchronized void onCompilationSuccess(CompilableTruffleAST target, StructuredGraph graph, CompilationResult result) {
        success++;
        long compilationDone = System.nanoTime();
        Times times = compilationTimes.get();
        compilationTime.accept(compilationDone - times.compilationStarted);

        compilationTimeTruffleTier.accept(times.truffleTierFinished - times.compilationStarted);
        compilationTimeGraalTier.accept(times.graalTierFinished - times.truffleTierFinished);
        compilationTimeCodeInstallation.accept(compilationDone - times.graalTierFinished);

        compilationResultCodeSize.accept(result.getTargetCodeSize());
        compilationResultTotalFrameSize.accept(result.getTotalFrameSize());
        compilationResultExceptionHandlers.accept(result.getExceptionHandlers().size());
        compilationResultInfopoints.accept(result.getInfopoints().size());
        compilationResultInfopointStatistics.accept(infopoints(result));
        compilationResultMarks.accept(result.getMarks().size());
        compilationResultDataPatches.accept(result.getDataPatches().size());
        compilationTimes.set(null);
    }

    @Override
    public void onShutdown() {
        printStatistics();
    }

    public void printStatistics() {
        TruffleCompilerImpl tc = compiler;
        tc.log("Truffle compilation statistics:");
        printStatistic(tc, "Compilations", compilations);
        printStatistic(tc, "  Success", success);
        printStatistic(tc, "  Failed", failures);
        printStatistic(tc, "  Interrupted", compilations - (success + failures));

        printStatisticTime(tc, "Compilation time", compilationTime);
        printStatisticTime(tc, "  Truffle Tier", compilationTimeTruffleTier);
        printStatisticTime(tc, "  Graal Tier", compilationTimeGraalTier);
        printStatisticTime(tc, "  Code Installation", compilationTimeCodeInstallation);

        printStatistic(tc, "Graal node count");
        printStatistic(tc, "  After Truffle Tier", truffleTierNodeCount);
        printStatistic(tc, "  After Graal Tier", graalTierNodeCount);

        printStatistic(tc, "Graal compilation result");
        printStatistic(tc, "  Code size", compilationResultCodeSize);
        printStatistic(tc, "  Total frame size", compilationResultTotalFrameSize);
        printStatistic(tc, "  Exception handlers", compilationResultExceptionHandlers);
        printStatistic(tc, "  Infopoints", compilationResultInfopoints);
        compilationResultInfopointStatistics.printStatistics(tc, identity());
        printStatistic(tc, "  Marks", compilationResultMarks);
        printStatistic(tc, "  Data references", compilationResultDataPatches);

        if (TruffleCompilerOptions.getValue(TruffleCompilationStatisticDetails)) {
            printStatistic(tc, "Graal nodes after Truffle tier");
            truffleTierNodeStatistics.printStatistics(tc, Class::getSimpleName);
            printStatistic(tc, "Graal nodes after Graal tier");
            graalTierNodeStatistics.printStatistics(tc, Class::getSimpleName);
        }
    }

    private static void printStatistic(TruffleCompilerImpl tc, String label) {
        tc.log(String.format("  %-50s: ", label));
    }

    private static void printStatistic(TruffleCompilerImpl tc, String label, int value) {
        tc.log(String.format("  %-50s: %d", label, value));
    }

    private static void printStatistic(TruffleCompilerImpl tc, String label, IntSummaryStatistics value) {
        tc.log(String.format("  %-50s: count=%4d, sum=%8d, min=%8d, average=%12.2f, max=%8d ", label, value.getCount(), value.getSum(), value.getMin(), value.getAverage(), value.getMax()));
    }

    private static void printStatisticTime(TruffleCompilerImpl tc, String label, LongSummaryStatistics value) {
        tc.log(String.format("  %-50s: count=%4d, sum=%8d, min=%8d, average=%12.2f, max=%8d (milliseconds)", label, value.getCount(), value.getSum() / 1000000, value.getMin() / 1000000,
                        value.getAverage() / 1e6, value.getMax() / 1000000));
    }

    private static final class IdentityStatistics<T> {

        final Map<T, IntSummaryStatistics> types = new HashMap<>();

        public void printStatistics(TruffleCompilerImpl rt, Function<T, String> toStringFunction) {

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

    private static class Times {

        private final long compilationStarted = System.nanoTime();
        private long truffleTierFinished;
        private long graalTierFinished;

    }

}
