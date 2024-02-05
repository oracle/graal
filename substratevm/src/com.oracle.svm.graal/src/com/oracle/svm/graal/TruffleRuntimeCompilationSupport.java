/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature.BeforeHeapLayoutAccess;
import org.graalvm.nativeimage.hosted.Feature.FeatureAccess;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateBackendFactory;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SharedRuntimeMethod;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;

import jdk.graal.compiler.core.CompilationWrapper.ExceptionAction;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.DebugContext.Description;
import jdk.graal.compiler.debug.DiagnosticsOutputDirectory;
import jdk.graal.compiler.debug.GlobalMetrics;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.GraphDecoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.FloatingGuardPhase;
import jdk.graal.compiler.phases.Speculative;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;

/**
 * Holds data that is pre-computed during native image generation and accessed at run time during a
 * Truffle compilation.
 */
public class TruffleRuntimeCompilationSupport {

    private RuntimeConfiguration runtimeConfig;

    private Suites fullOptimizationSuites;
    private Suites suitesWithoutSpeculation;
    private Suites suitesWithExplicitExceptions;

    private LIRSuites lirSuites;
    private Suites firstTierSuites;
    private LIRSuites firstTierLirSuites;
    private Providers firstTierProviders;

    /*
     * The following four fields are set late in the image build process. To ensure their values are
     * not prematurely constant folded we must mark them as unknown object fields.
     */

    @UnknownObjectField private SubstrateMethod[] methodsToCompile;
    @UnknownObjectField private byte[] graphEncoding;
    @UnknownObjectField private Object[] graphObjects;
    @UnknownObjectField private NodeClass<?>[] graphNodeTypes;

    protected Function<Providers, SubstrateBackend> runtimeBackendProvider;

    protected final GlobalMetrics metricValues = new GlobalMetrics();
    protected final DiagnosticsOutputDirectory outputDirectory = new DiagnosticsOutputDirectory(RuntimeOptionValues.singleton());
    protected final Map<ExceptionAction, Integer> compilationProblemsPerAction = new EnumMap<>(ExceptionAction.class);

    public DebugContext openDebugContext(OptionValues options, CompilationIdentifier compilationId, Object compilable, PrintStream logStream) {
        Description description = new Description(compilable, compilationId.toString(CompilationIdentifier.Verbosity.ID));
        return new Builder(options, runtimeConfig.getDebugHandlersFactories()).globalMetrics(metricValues).description(description).logStream(logStream).build();
    }

    public DiagnosticsOutputDirectory getDebugOutputDirectory() {
        return outputDirectory;
    }

    public Map<ExceptionAction, Integer> getCompilationProblemsPerAction() {
        return compilationProblemsPerAction;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public TruffleRuntimeCompilationSupport() {
        /* By default the backend configuration is the same as for the native image. */
        runtimeBackendProvider = SubstrateBackendFactory.get()::newBackend;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setRuntimeConfig(RuntimeConfiguration runtimeConfig, Suites suites, LIRSuites lirSuites, Suites firstTierSuites, LIRSuites firstTierLirSuites) {
        get().runtimeConfig = runtimeConfig;
        get().fullOptimizationSuites = suites;
        get().suitesWithoutSpeculation = getWithoutSpeculative(suites);
        get().suitesWithExplicitExceptions = getWithExplicitExceptions(suites);
        get().lirSuites = lirSuites;
        get().firstTierSuites = firstTierSuites;
        get().firstTierLirSuites = firstTierLirSuites;
        get().firstTierProviders = runtimeConfig.getBackendForNormalMethod().getProviders();
    }

    private static Suites getWithoutSpeculative(Suites s) {
        Suites effectiveSuites = s.copy();
        effectiveSuites.getHighTier().removeSubTypePhases(Speculative.class);
        effectiveSuites.getMidTier().removeSubTypePhases(Speculative.class);
        effectiveSuites.getLowTier().removeSubTypePhases(Speculative.class);
        return effectiveSuites;
    }

    private static Suites getWithExplicitExceptions(Suites s) {
        Suites effectiveSuites = s.copy();
        effectiveSuites.getHighTier().removeSubTypePhases(FloatingGuardPhase.class);
        effectiveSuites.getMidTier().removeSubTypePhases(FloatingGuardPhase.class);
        effectiveSuites.getLowTier().removeSubTypePhases(FloatingGuardPhase.class);
        effectiveSuites.getHighTier().removeSubTypePhases(Speculative.class);
        effectiveSuites.getMidTier().removeSubTypePhases(Speculative.class);
        effectiveSuites.getLowTier().removeSubTypePhases(Speculative.class);
        return effectiveSuites;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean setMethodsToCompile(DuringAnalysisAccessImpl config, SubstrateMethod[] methodsToCompile) {
        boolean result = false;
        TruffleRuntimeCompilationSupport support = get();
        if (!Arrays.equals(support.methodsToCompile, methodsToCompile)) {
            support.methodsToCompile = methodsToCompile;
            TruffleRuntimeCompilationSupport.rescan(config, methodsToCompile);
            result = true;
        }
        return result;
    }

    public static Suites getFullOptSuites() {
        return get().fullOptimizationSuites;
    }

    public static Suites getWithoutSpeculativeSuites() {
        return get().suitesWithoutSpeculation;
    }

    public static Suites getExplicitExceptionSuites() {
        return get().suitesWithExplicitExceptions;
    }

    public static Suites getMatchingSuitesForGraph(StructuredGraph graph) {
        Suites s = getFullOptSuites();
        if (graph.getSpeculationLog() == null) {
            s = getWithoutSpeculativeSuites();
        }
        if (graph.getGraphState().isExplicitExceptionsNoDeopt()) {
            s = getExplicitExceptionSuites();
        }
        return s;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean setGraphEncoding(FeatureAccess a, byte[] graphEncoding, Object[] graphObjects, NodeClass<?>[] graphNodeTypes) {
        TruffleRuntimeCompilationSupport support = get();
        if (support.graphObjects == null && graphObjects.length == 0) {
            assert graphEncoding.length == 0;
            assert graphNodeTypes.length == 0;
            return false;
        }
        boolean result = false;
        if (!Arrays.equals(support.graphEncoding, graphEncoding)) {
            support.graphEncoding = graphEncoding;
            result = true;
        }
        if (!Arrays.deepEquals(support.graphObjects, graphObjects)) {
            support.graphObjects = graphObjects;
            TruffleRuntimeCompilationSupport.rescan(a, graphObjects);
            result = true;
        }
        if (!Arrays.equals(support.graphNodeTypes, graphNodeTypes)) {
            support.graphNodeTypes = graphNodeTypes;
            TruffleRuntimeCompilationSupport.rescan(a, graphNodeTypes);
            result = true;
        }
        return result;
    }

    private static void rescan(FeatureAccess a, Object object) {
        if (a instanceof DuringAnalysisAccessImpl access) {
            rescan(access.getUniverse(), object);
        }
    }

    /**
     * Rescan Graal objects during analysis. The fields that point to these objects are annotated
     * with {@link UnknownObjectField} so their value is not processed during analysis, only their
     * declared type is injected in the type flow graphs. Their eventual value becomes available
     * after analysis. Later when the field is read the lazy value supplier scans the final value
     * and patches the shadow heap.
     */
    public static void rescan(AnalysisUniverse universe, Object object) {
        universe.getHeapScanner().rescanObject(object);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerImmutableObjects(BeforeHeapLayoutAccess access) {
        access.registerAsImmutable(get().graphEncoding);
        access.registerAsImmutable(get().graphObjects);
        access.registerAsImmutable(get().graphNodeTypes);
    }

    public static TruffleRuntimeCompilationSupport get() {
        return ImageSingletons.lookup(TruffleRuntimeCompilationSupport.class);
    }

    public static RuntimeConfiguration getRuntimeConfig() {
        return get().runtimeConfig;
    }

    public static LIRSuites getLIRSuites() {
        return get().lirSuites;
    }

    public static Suites getFirstTierSuites() {
        return get().firstTierSuites;
    }

    public static LIRSuites getFirstTierLirSuites() {
        return get().firstTierLirSuites;
    }

    public static Providers getFirstTierProviders() {
        return get().firstTierProviders;
    }

    public static SubstrateMethod[] getMethodsToCompile() {
        return get().methodsToCompile;
    }

    public static EncodedGraph encodedGraph(SharedRuntimeMethod method, boolean trackNodeSourcePosition) {
        int startOffset = method.getEncodedGraphStartOffset();
        if (startOffset == -1) {
            return null;
        }
        return new EncodedGraph(get().graphEncoding, startOffset, get().graphObjects, get().graphNodeTypes, null, null, false, trackNodeSourcePosition);
    }

    public static StructuredGraph decodeGraph(DebugContext debug, String name, CompilationIdentifier compilationId, SharedRuntimeMethod method, StructuredGraph caller) {
        EncodedGraph encodedGraph = encodedGraph(method, false);
        if (encodedGraph == null) {
            return null;
        }

        boolean isSubstitution = method.isSnippet();
        StructuredGraph graph = new StructuredGraph.Builder(debug.getOptions(), debug)
                        .name(name)
                        .method(method)
                        .recordInlinedMethods(false)
                        .compilationId(compilationId)
                        .setIsSubstitution(isSubstitution)
                        .speculationLog((caller != null) ? caller.getSpeculationLog() : null)
                        .build();
        GraphDecoder decoder = new GraphDecoder(ConfigurationValues.getTarget().arch, graph);
        decoder.decode(encodedGraph);
        return graph;
    }

    public static Function<Providers, SubstrateBackend> getRuntimeBackendProvider() {
        return get().runtimeBackendProvider;
    }
}
