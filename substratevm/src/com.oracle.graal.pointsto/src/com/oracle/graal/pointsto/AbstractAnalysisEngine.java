/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.graal.pointsto.util.Timer;
import jdk.vm.ci.meta.ConstantReflectionProvider;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.nodes.spi.Replacements;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.printer.GraalDebugHandlersFactory;

import java.io.PrintWriter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

public abstract class AbstractAnalysisEngine implements BigBang {

    private final Boolean extendedAsserts;
    private final Timer processFeaturesTimer;
    private final Timer analysisTimer;
    protected final AnalysisMetaAccess metaAccess;
    private final HostedProviders providers;
    protected final HostVM hostVM;
    protected final ForkJoinPool executorService;
    private final Runnable heartbeatCallback;
    private final UnsupportedFeatures unsupportedFeatures;
    protected final DebugContext debug;
    protected final OptionValues options;
    private final AnalysisUniverse universe;
    private final List<DebugHandlersFactory> debugHandlerFactories;
    private final HeapScanningPolicy heapScanningPolicy;
    private final Replacements replacements;
    protected final CompletionExecutor executor;

    public AbstractAnalysisEngine(OptionValues options, AnalysisUniverse universe, HostedProviders providers, HostVM hostVM, ForkJoinPool executorService, Runnable heartbeatCallback,
                    UnsupportedFeatures unsupportedFeatures) {
        this.options = options;
        this.universe = universe;
        this.debugHandlerFactories = Collections.singletonList(new GraalDebugHandlersFactory(providers.getSnippetReflection()));
        this.debug = new org.graalvm.compiler.debug.DebugContext.Builder(options, debugHandlerFactories).build();
        this.metaAccess = (AnalysisMetaAccess) providers.getMetaAccess();
        this.providers = providers;
        this.hostVM = hostVM;
        this.executorService = executorService;
        this.executor = new CompletionExecutor(this, executorService, heartbeatCallback);
        this.executor.init(null);
        this.heartbeatCallback = heartbeatCallback;
        this.unsupportedFeatures = unsupportedFeatures;
        this.replacements = providers.getReplacements();

        String imageName = hostVM.getImageName();
        this.processFeaturesTimer = new Timer(imageName, "(features)", false);
        this.analysisTimer = new Timer(imageName, "analysis", true);

        this.extendedAsserts = PointstoOptions.ExtendedAsserts.getValue(options);

        this.heapScanningPolicy = PointstoOptions.ExhaustiveHeapScan.getValue(options)
                        ? HeapScanningPolicy.scanAll()
                        : HeapScanningPolicy.skipTypes(skippedHeapTypes());
    }

    @Override
    public void cleanupAfterAnalysis() {
        universe.getTypes().forEach(AnalysisType::cleanupAfterAnalysis);
        universe.getFields().forEach(AnalysisField::cleanupAfterAnalysis);
        universe.getMethods().forEach(AnalysisMethod::cleanupAfterAnalysis);
    }

    @Override
    public Timer getAnalysisTimer() {
        return analysisTimer;
    }

    @Override
    public Timer getProcessFeaturesTimer() {
        return processFeaturesTimer;
    }

    @Override
    public void printTimers() {
        processFeaturesTimer.print();
    }

    @Override
    public void printTimerStatistics(PrintWriter out) {
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
        // executorService.submit(task);
    }

    @Override
    public Replacements getReplacements() {
        return replacements;
    }
}
