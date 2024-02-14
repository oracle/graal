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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.ClassInclusionPolicy.LayeredBaseImageInclusionPolicy;
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
import com.oracle.svm.common.meta.MultiMethod;

import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.DebugHandlersFactory;
import jdk.graal.compiler.debug.Indent;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.DeoptBciSupplier;
import jdk.graal.compiler.nodes.StateSplit;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.printer.GraalDebugHandlersFactory;
import jdk.graal.compiler.word.WordTypes;
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

    protected final int maxConstantObjectsPerType;
    protected final boolean profileConstantObjects;
    protected final boolean optimizeReturnedParameter;

    protected final OptionValues options;
    protected final DebugContext debug;
    private final List<DebugHandlersFactory> debugHandlerFactories;

    protected final HostVM hostVM;
    protected final UnsupportedFeatures unsupportedFeatures;
    private final SnippetReflectionProvider snippetReflectionProvider;
    private final ConstantReflectionProvider constantReflectionProvider;
    private final WordTypes wordTypes;

    /**
     * Processing queue.
     */
    protected final CompletionExecutor executor;

    protected final Timer processFeaturesTimer;
    protected final Timer analysisTimer;
    protected final Timer verifyHeapTimer;
    protected final ClassInclusionPolicy classInclusionPolicy;

    @SuppressWarnings("this-escape")
    public AbstractAnalysisEngine(OptionValues options, AnalysisUniverse universe, HostVM hostVM, AnalysisMetaAccess metaAccess, SnippetReflectionProvider snippetReflectionProvider,
                    ConstantReflectionProvider constantReflectionProvider, WordTypes wordTypes, UnsupportedFeatures unsupportedFeatures, DebugContext debugContext,
                    TimerCollection timerCollection, ClassInclusionPolicy classInclusionPolicy) {
        this.options = options;
        this.universe = universe;
        this.debugHandlerFactories = Collections.singletonList(new GraalDebugHandlersFactory(snippetReflectionProvider));
        this.debug = new Builder(options, debugHandlerFactories).build();
        this.metaAccess = metaAccess;
        this.analysisPolicy = universe.analysisPolicy();
        this.hostVM = hostVM;
        this.executor = new CompletionExecutor(debugContext, this);
        this.unsupportedFeatures = unsupportedFeatures;

        this.processFeaturesTimer = timerCollection.get(TimerCollection.Registry.FEATURES);
        this.verifyHeapTimer = timerCollection.get(TimerCollection.Registry.VERIFY_HEAP);
        this.analysisTimer = timerCollection.get(TimerCollection.Registry.ANALYSIS);

        maxConstantObjectsPerType = PointstoOptions.MaxConstantObjectsPerType.getValue(options);
        profileConstantObjects = PointstoOptions.ProfileConstantObjects.getValue(options);
        optimizeReturnedParameter = PointstoOptions.OptimizeReturnedParameter.getValue(options);
        this.snippetReflectionProvider = snippetReflectionProvider;
        this.constantReflectionProvider = constantReflectionProvider;
        this.wordTypes = wordTypes;
        classInclusionPolicy.setBigBang(this);
        this.classInclusionPolicy = classInclusionPolicy;
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
    private boolean analysisModified() {
        boolean analysisModified;
        try (Timer.StopTimer ignored = verifyHeapTimer.start()) {
            /*
             * After the analysis reaches a stable state check if the shadow heap contains all
             * objects reachable from roots. If this leads to analysis state changes, an additional
             * analysis iteration will be run.
             * 
             * We reuse the analysis executor, which at this point should be in before-start state:
             * the analysis finished and it re-initialized the executor for the next iteration. The
             * verifier controls the life cycle of the executor: it starts it and then waits until
             * all operations are completed. The same executor is implicitly used by the shadow heap
             * scanner and the verifier also passes it to the root scanner, so when
             * checkHeapSnapshot returns all heap scanning and verification tasks are completed.
             */
            assert executor.isBeforeStart() : executor.getState();
            analysisModified = universe.getHeapVerifier().checkHeapSnapshot(metaAccess, executor, "during analysis", true, universe.getEmbeddedRoots());
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

    public int maxConstantObjectsPerType() {
        return maxConstantObjectsPerType;
    }

    public boolean optimizeReturnedParameter() {
        return optimizeReturnedParameter;
    }

    public void profileConstantObject(AnalysisType type) {
        if (profileConstantObjects) {
            PointsToAnalysis.ConstantObjectsProfiler.registerConstant(type);
            PointsToAnalysis.ConstantObjectsProfiler.maybeDumpConstantHistogram();
        }
    }

    public boolean isBaseLayerAnalysisEnabled() {
        return classInclusionPolicy instanceof LayeredBaseImageInclusionPolicy;
    }

    @Override
    public OptionValues getOptions() {
        return options;
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
    public final HostedProviders getProviders(MultiMethod.MultiMethodKey key) {
        return getHostVM().getProviders(key);
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
        return snippetReflectionProvider;
    }

    @Override
    public final ConstantReflectionProvider getConstantReflectionProvider() {
        return constantReflectionProvider;
    }

    @Override
    public WordTypes getWordTypes() {
        return wordTypes;
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

    public void postTask(final Runnable task) {
        executor.execute(new CompletionExecutor.DebugContextRunnable() {
            @Override
            public void run(DebugContext ignore) {
                task.run();
            }

            @Override
            public DebugContext getDebug(OptionValues opts, List<DebugHandlersFactory> factories) {
                assert opts == getOptions() : opts + " != " + getOptions();
                return DebugContext.disabled(opts);
            }
        });
    }

    @Override
    public final boolean executorIsStarted() {
        return executor.isStarted();
    }

    @Override
    public void registerTypeForBaseImage(Class<?> cls) {
        if (classInclusionPolicy.isClassIncluded(cls)) {
            classInclusionPolicy.includeClass(cls);
            Stream.concat(Arrays.stream(cls.getDeclaredConstructors()), Arrays.stream(cls.getDeclaredMethods()))
                            .filter(classInclusionPolicy::isMethodIncluded)
                            .forEach(classInclusionPolicy::includeMethod);
            Arrays.stream(cls.getDeclaredFields())
                            .filter(classInclusionPolicy::isFieldIncluded)
                            .forEach(classInclusionPolicy::includeField);
        }
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
    public static BytecodePosition syntheticSourcePosition(ResolvedJavaMethod method) {
        return syntheticSourcePosition(null, method);
    }

    public static BytecodePosition syntheticSourcePosition(Node node, ResolvedJavaMethod method) {
        int bci = BytecodeFrame.UNKNOWN_BCI;
        if (node instanceof DeoptBciSupplier) {
            bci = ((DeoptBciSupplier) node).bci();
        }

        /*
         * If the node is a state split, then we can read the framestate to get a better guess of
         * the node's position
         */
        if (node instanceof StateSplit stateSplit) {
            var frameState = stateSplit.stateAfter();
            if (frameState != null) {
                if (frameState.outerFrameState() != null) {
                    /*
                     * If the outer framestate is not null, then inlinebeforeanalysis has inlined
                     * this call. We store the position of the original call to prevent recursive
                     * flows.
                     */
                    var current = frameState;
                    while (current.outerFrameState() != null) {
                        current = current.outerFrameState();
                    }
                    assert method.equals(current.getMethod()) : method + " != " + current.getMethod();
                    bci = current.bci;
                } else if (bci == BytecodeFrame.UNKNOWN_BCI) {
                    /*
                     * If there is a single framestate, then use its bci if nothing better is
                     * available
                     */
                    bci = frameState.bci;
                }
            }
        }
        return new BytecodePosition(null, method, bci);
    }

}
