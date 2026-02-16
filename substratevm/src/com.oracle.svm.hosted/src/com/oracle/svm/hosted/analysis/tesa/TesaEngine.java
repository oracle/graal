/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.analysis.tesa;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.AbstractAnalysisEngine;
import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.results.StrengthenGraphs;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.analysis.tesa.effect.TesaEffect;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.util.ImageBuildStatistics;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.util.json.JsonBuilder;
import jdk.graal.compiler.util.json.JsonWriter;

/**
 * The core class of the Transitive Effect Summary Analysis (TESA) framework. Manages a set of
 * {@link AbstractTesa} instances, each implementing a separate TESA analysis based on a given
 * {@link TesaEffect}.
 * <p>
 * The main entry points are the following methods, which should be called in this order:
 * <ol>
 * <li>{@link #initializeStateForMethod} - for each method separately. Should be called after
 * {@link StrengthenGraphs} so that we start with graphs already optimized by the results of
 * {@link AbstractAnalysisEngine}.</li>
 * <li>{@link #runFixedPointLoops} - once per image build.</li>
 * <li>{@link #applyResults} - for each method separately.</li>
 * </ol>
 *
 * @see AbstractTesa
 * @see TesaEffect
 * 
 * @implNote For a concrete example on how to create and register a custom TESA instance, search for
 *           {@code ExampleUnsafeAnalysisTesaTest}.
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
public class TesaEngine implements ImageBuildStatistics.TesaPrinter {

    public static class Options {
        @Option(help = "Enable Transitive Effect Summary Analysis (TESA).")//
        public static final HostedOptionKey<Boolean> TransitiveEffectSummaryAnalysis = new HostedOptionKey<>(true);

        @Option(help = "Print TESA results to the console. Enabled automatically with assertions.")//
        public static final HostedOptionKey<Boolean> TesaPrintToConsole = new HostedOptionKey<>(SubstrateUtil.assertionsEnabled());

        @Option(help = "Throw an exception if any TESA instance fails to reach a fixed point within the expected number of iterations.")//
        public static final HostedOptionKey<Boolean> TesaThrowOnNonTermination = new HostedOptionKey<>(true);
    }

    /**
     * Contains TESA instances to be performed.
     */
    private final Map<Class<?>, AbstractTesa<?>> analyses = new LinkedHashMap<>();

    /**
     * Used to make sure that TESA instances are not registered too late, after the initial state
     * computation has already been done.
     */
    private volatile boolean isSealed;

    /**
     * Call-graph-related data.
     */
    private TesaReverseCallGraph callGraph;

    /**
     * The total number of methods visited by TESA during the optimization stage. Used for
     * statistics.
     */
    private final AtomicInteger totalMethodsCounter = new AtomicInteger();

    /**
     * The total number of invokes visited by TESA during the optimization stage. Used for
     * statistics.
     */
    private final AtomicInteger totalInvokesCounter = new AtomicInteger();

    public TesaEngine() {
        registerDefaultAnalyses();
    }

    /**
     * Register a set of TESA instances that are enabled out of the box. More may be added by
     * explicitly calling {@link #registerTesa}.
     */
    private void registerDefaultAnalyses() {
        registerTesa(new KilledLocationTesa());
    }

    /**
     * Registers a given TESA instance. Should be called during the configuration stage of the
     * build, at the latest during {@link Feature#beforeAnalysis} callbacks.
     * <p>
     * The analyses may then be retrieved by calling {@link #getAnalysis}.
     */
    public final void registerTesa(AbstractTesa<?> tesa) {
        VMError.guarantee(!isSealed, "The TESA engine has already been sealed, no more TESA instances may be added.");
        analyses.put(tesa.getClass(), tesa);
    }

    public static boolean enabled() {
        return ImageSingletons.contains(TesaEngine.class);
    }

    /**
     * Gets the singleton instance of the engine.
     */
    public static TesaEngine get() {
        return ImageSingletons.lookup(TesaEngine.class);
    }

    /**
     * This method marks the end of the configuration phase, after which no more analyses may be
     * registered via {@link #registerTesa}.
     */
    public void seal() {
        isSealed = true;
    }

    /**
     * Runs the {@link AbstractTesa#initializeStateForMethod} for each registered TESA.
     * 
     * @see AbstractTesa#initializeStateForMethod
     */
    public void initializeStateForMethod(AnalysisMethod method, StructuredGraph graph) {
        analyses.values().parallelStream()
                        .forEach(analysis -> analysis.initializeStateForMethod(method, graph));
    }

    /**
     * @see AbstractTesa#runFixedPointLoop
     */
    public void runFixedPointLoops(BigBang bb) {
        analyses.values().parallelStream()
                        .forEach(analysis -> analysis.runFixedPointLoop(this, bb));
    }

    /**
     * @see AbstractTesa#applyResults
     */
    public void applyResults(HostedUniverse universe, HostedMethod method, StructuredGraph graph) {
        totalMethodsCounter.incrementAndGet();
        for (Node node : graph.getNodes()) {
            if (node instanceof Invoke) {
                totalInvokesCounter.incrementAndGet();
            }
        }
        for (AbstractTesa<?> analysis : analyses.values()) {
            analysis.applyResults(this, universe, method, graph);
        }
    }

    /**
     * Print the results as a JSON object reported as a part of {@link ImageBuildStatistics}.
     */
    @Override
    public void printTesaResults(PrintWriter out) {
        try {
            var writer = new JsonWriter(out);
            // we are extending an already existing JSON object
            writer.appendSeparator();
            // create a nested tesa object for clarity
            writer.quote("tesa").appendFieldSeparator();
            try (var objectBuilder = writer.objectBuilder()) {
                printTesaResults0(objectBuilder);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // Do not close the writer. The outer JSON object has not been fully printed yet.
    }

    private void printTesaResults0(JsonBuilder.ObjectBuilder objectBuilder) throws IOException {
        objectBuilder.append(callGraph.getCallGraphInitializationTimer().getName() + "Ms", callGraph.getCallGraphInitializationTimer().getTotalTimeMs());
        objectBuilder.append("totalMethods", totalMethodsCounter.get());
        objectBuilder.append("totalInvokes", totalInvokesCounter.get());
        for (AbstractTesa<?> analysis : analyses.values()) {
            printSingleAnalysisResults(objectBuilder, analysis);
        }
    }

    private static void printSingleAnalysisResults(JsonBuilder.ObjectBuilder parentBuilder, AbstractTesa<?> analysis) throws IOException {
        var classname = ClassUtil.getUnqualifiedName(analysis.getClass());
        try (var objectBuilder = parentBuilder.append(classname).object()) {
            objectBuilder.append("methods", analysis.getOptimizableMethods());
            objectBuilder.append("invokes", analysis.getOptimizableInvokes());
            objectBuilder.append("timeMs", analysis.getFixedPointLoopTimeMs());
        }
    }

    /**
     * Save the relevant call-graph-related data computed by {@link AbstractAnalysisEngine} and
     * store them in {@link TesaReverseCallGraph} for later use.
     */
    public void saveCallGraph(BigBang bb) {
        callGraph = TesaReverseCallGraph.create(bb);
    }

    public TesaReverseCallGraph getCallGraph() {
        return callGraph;
    }

    public <T> T getAnalysis(Class<T> clazz) {
        return clazz.cast(analyses.get(clazz));
    }

    public Collection<AbstractTesa<?>> getAllAnalyses() {
        return analyses.values();
    }

    public int getTotalMethods() {
        return totalMethodsCounter.get();
    }

    public int getTotalInvokes() {
        return totalInvokesCounter.get();
    }
}
