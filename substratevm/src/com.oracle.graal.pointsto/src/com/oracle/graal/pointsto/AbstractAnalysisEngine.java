/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.Indent;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.DeoptBciSupplier;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.graal.pointsto.constraints.UnsupportedFeatures;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.reports.StatisticsPrinter;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.graal.pointsto.util.Timer;
import com.oracle.graal.pointsto.util.TimerCollection;

import jdk.vm.ci.code.BytecodeFrame;
import jdk.vm.ci.code.BytecodePosition;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This abstract class is shared between Reachability and Points-to. It contains generic methods
 * needed by both types of analysis and getters.
 */
public abstract class AbstractAnalysisEngine implements BigBang {

    protected final AnalysisUniverse universe;
    protected final AnalysisMetaAccess metaAccess;
    protected final AnalysisPolicy analysisPolicy;
    private final HeapScanningPolicy heapScanningPolicy;

    protected final Boolean extendedAsserts;
    protected final int maxConstantObjectsPerType;
    protected final boolean profileConstantObjects;

    protected final OptionValues options;
    protected final DebugContext debug;
    private final List<DebugHandlersFactory> debugHandlerFactories;

    protected final HostVM hostVM;
    protected final HostedProviders providers;
    protected final UnsupportedFeatures unsupportedFeatures;
    private final Replacements replacements;

    /**
     * Processing queue.
     */
    protected final CompletionExecutor executor;
    private final Runnable heartbeatCallback;

    protected final Timer processFeaturesTimer;
    protected final Timer analysisTimer;
    protected final Timer verifyHeapTimer;

    public AbstractAnalysisEngine(OptionValues options, AnalysisUniverse universe, HostedProviders providers, HostVM hostVM, ForkJoinPool executorService, Runnable heartbeatCallback,
                    UnsupportedFeatures unsupportedFeatures, TimerCollection timerCollection) {
        this.options = options;
        this.universe = universe;
        this.debugHandlerFactories = Collections.singletonList(new GraalDebugHandlersFactory(providers.getSnippetReflection()));
        this.debug = new Builder(options, debugHandlerFactories).build();
        this.metaAccess = (AnalysisMetaAccess) providers.getMetaAccess();
        this.analysisPolicy = universe.analysisPolicy();
        this.providers = providers;
        this.hostVM = hostVM;
        this.executor = new CompletionExecutor(this, executorService, heartbeatCallback);
        this.heartbeatCallback = heartbeatCallback;
        this.unsupportedFeatures = unsupportedFeatures;
        this.replacements = providers.getReplacements();

        this.processFeaturesTimer = timerCollection.get(TimerCollection.Registry.FEATURES);
        this.verifyHeapTimer = timerCollection.get(TimerCollection.Registry.VERIFY_HEAP);
        this.analysisTimer = timerCollection.get(TimerCollection.Registry.ANALYSIS);

        this.extendedAsserts = PointstoOptions.ExtendedAsserts.getValue(options);
        maxConstantObjectsPerType = PointstoOptions.MaxConstantObjectsPerType.getValue(options);
        profileConstantObjects = PointstoOptions.ProfileConstantObjects.getValue(options);

        this.heapScanningPolicy = PointstoOptions.ExhaustiveHeapScan.getValue(options)
                        ? HeapScanningPolicy.scanAll()
                        : HeapScanningPolicy.skipTypes(skippedHeapTypes());
    }

    /**
     * Iterate until analysis reaches a fixpoint.
     *
     * @param debugContext debug context
     * @param analysisEndCondition hook for actions to be taken during analysis. It also dictates
     *            when the analysis should end, i.e., it returns true if no more iterations are
     *            required.
     *
     *            When the analysis is used for Native Image generation the actions could for
     *            example be specified via
     *            {@link org.graalvm.nativeimage.hosted.Feature#duringAnalysis(Feature.DuringAnalysisAccess)}.
     *            The ending condition could be provided by
     *            {@link org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess#requireAnalysisIteration()}.
     *
     * @throws AnalysisError if the analysis fails
     */
    @SuppressWarnings("try")
    @Override
    public void runAnalysis(DebugContext debugContext, Function<AnalysisUniverse, Boolean> analysisEndCondition) throws InterruptedException {
        int numIterations = 0;
        while (true) {
            try (Indent indent2 = debugContext.logAndIndent("new analysis iteration")) {
                /*
                 * Do the analysis (which itself is done in a similar iterative process)
                 */
                boolean analysisChanged = finish();

                numIterations++;
                if (numIterations > 1000) {
                    /*
                     * Usually there are < 10 iterations. If we have so many iterations, we probably
                     * have an endless loop (but at least we have a performance problem because we
                     * re-start the analysis so often).
                     */
                    throw AnalysisError.shouldNotReachHere(String.format("Static analysis did not reach a fix point after %d iterations because a Feature keeps requesting new analysis iterations. " +
                                    "The analysis itself %s find a change in type states in the last iteration.",
                                    numIterations, analysisChanged ? "DID" : "DID NOT"));
                }
                /*
                 * Allow features to change the universe.
                 */
                int numTypes = universe.getTypes().size();
                int numMethods = universe.getMethods().size();
                int numFields = universe.getFields().size();
                if (analysisEndCondition.apply(universe)) {
                    if (numTypes != universe.getTypes().size() || numMethods != universe.getMethods().size() || numFields != universe.getFields().size()) {
                        throw AnalysisError.shouldNotReachHere(
                                        "When a feature makes more types, methods, or fields reachable, it must require another analysis iteration via DuringAnalysisAccess.requireAnalysisIteration()");
                    }
                    /*
                     * Manual rescanning doesn't explicitly require analysis iterations, but it can
                     * insert some pending operations.
                     */
                    boolean pendingOperations = executor.getPostedOperations() > 0;
                    if (pendingOperations) {
                        System.out.println("Found pending operations, continuing analysis.");
                        continue;
                    }
                    /* Outer analysis loop is done. Check if heap verification modifies analysis. */
                    if (!analysisModified()) {
                        return;
                    }
                }
            }
        }
    }

    protected abstract CompletionExecutor.Timing getTiming();

    @SuppressWarnings("try")
    private boolean analysisModified() throws InterruptedException {
        boolean analysisModified;
        try (Timer.StopTimer ignored = verifyHeapTimer.start()) {
            analysisModified = universe.getHeapVerifier().requireAnalysisIteration(executor);
        }
        /* Initialize for the next iteration. */
        executor.init(getTiming());
        return analysisModified;
    }

    @Override
    public void cleanupAfterAnalysis() {
        universe.getTypes().forEach(AnalysisType::cleanupAfterAnalysis);
        universe.getFields().forEach(AnalysisField::cleanupAfterAnalysis);
        universe.getMethods().forEach(AnalysisMethod::cleanupAfterAnalysis);

        universe.getHeapScanner().cleanupAfterAnalysis();
        universe.getHeapVerifier().cleanupAfterAnalysis();
    }

    @Override
    public void printTimerStatistics(PrintWriter out) {
        // todo print reachability here
        StatisticsPrinter.print(out, "features_time_ms", processFeaturesTimer.getTotalTime());
        StatisticsPrinter.print(out, "total_analysis_time_ms", analysisTimer.getTotalTime());

        StatisticsPrinter.printLast(out, "total_memory_bytes", analysisTimer.getTotalMemory());
    }

    @Override
    public AnalysisType[] skippedHeapTypes() {
        return new AnalysisType[]{metaAccess.lookupJavaType(String.class)};
    }

    @Override
    public boolean extendedAsserts() {
        return extendedAsserts;
    }

    public int maxConstantObjectsPerType() {
        return maxConstantObjectsPerType;
    }

    public void profileConstantObject(AnalysisType type) {
        if (profileConstantObjects) {
            PointsToAnalysis.ConstantObjectsProfiler.registerConstant(type);
            PointsToAnalysis.ConstantObjectsProfiler.maybeDumpConstantHistogram();
        }
    }

    @Override
    public OptionValues getOptions() {
        return options;
    }

    @Override
    public Runnable getHeartbeatCallback() {
        return heartbeatCallback;
    }

    @Override
    public DebugContext getDebug() {
        return debug;
    }

    @Override
    public List<DebugHandlersFactory> getDebugHandlerFactories() {
        return debugHandlerFactories;
    }

    @Override
    public AnalysisPolicy analysisPolicy() {
        return universe.analysisPolicy();
    }

    @Override
    public AnalysisUniverse getUniverse() {
        return universe;
    }

    @Override
    public HostedProviders getProviders() {
        return providers;
    }

    @Override
    public AnalysisMetaAccess getMetaAccess() {
        return metaAccess;
    }

    @Override
    public UnsupportedFeatures getUnsupportedFeatures() {
        return unsupportedFeatures;
    }

    @Override
    public final SnippetReflectionProvider getSnippetReflectionProvider() {
        return providers.getSnippetReflection();
    }

    @Override
    public final ConstantReflectionProvider getConstantReflectionProvider() {
        return providers.getConstantReflection();
    }

    @Override
    public HeapScanningPolicy scanningPolicy() {
        return heapScanningPolicy;
    }

    @Override
    public HostVM getHostVM() {
        return hostVM;
    }

    protected void schedule(Runnable task) {
        executor.execute((d) -> task.run());
    }

    @Override
    public final void postTask(CompletionExecutor.DebugContextRunnable task) {
        executor.execute(task);
    }

    @Override
    public final boolean executorIsStarted() {
        return executor.isStarted();
    }

    @Override
    public Replacements getReplacements() {
        return replacements;
    }

    /**
     * Provide a non-null position. Some flows like newInstance and invoke require a non-null
     * position, for others is just better. The constructed position is best-effort, i.e., it
     * contains at least the method, and a BCI only if the node provides it.
     * <p>
     * This is necessary because {@link Node#getNodeSourcePosition()} doesn't always provide a
     * position, like for example for generated factory methods in FactoryThrowMethodHolder.
     */
    public static BytecodePosition sourcePosition(ValueNode node) {
        BytecodePosition position = node.getNodeSourcePosition();
        if (position == null) {
            position = syntheticSourcePosition(node, node.graph().method());
        }
        return position;
    }

    /** Creates a synthetic position for the node in the given method. */
    public static BytecodePosition syntheticSourcePosition(Node node, ResolvedJavaMethod method) {
        int bci = BytecodeFrame.UNKNOWN_BCI;
        if (node instanceof DeoptBciSupplier) {
            bci = ((DeoptBciSupplier) node).bci();
        }
        return new BytecodePosition(null, method, bci);
    }

}
