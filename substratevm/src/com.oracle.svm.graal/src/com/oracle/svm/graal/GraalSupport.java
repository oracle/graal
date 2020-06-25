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

import static org.graalvm.word.LocationIdentity.ANY_LOCATION;

import java.io.PrintStream;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.MethodSubstitution;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.core.CompilationWrapper.ExceptionAction;
import org.graalvm.compiler.core.common.CompilationIdentifier;
import org.graalvm.compiler.core.gen.NodeMatchRules;
import org.graalvm.compiler.core.match.MatchStatement;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.DebugContext.Builder;
import org.graalvm.compiler.debug.DebugContext.Description;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.debug.DiagnosticsOutputDirectory;
import org.graalvm.compiler.debug.GlobalMetrics;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.lir.CompositeValueClass;
import org.graalvm.compiler.lir.LIRInstructionClass;
import org.graalvm.compiler.lir.phases.LIRPhase;
import org.graalvm.compiler.lir.phases.LIRSuites;
import org.graalvm.compiler.nodes.EncodedGraph;
import org.graalvm.compiler.nodes.GraphDecoder;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.BasePhase;
import org.graalvm.compiler.phases.tiers.Suites;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature.CompilationAccess;
import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateBackendFactory;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SharedRuntimeMethod;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;

/**
 * Holds data that is pre-computed during native image generation and accessed at run time during a
 * Graal compilation.
 */
public class GraalSupport {

    private RuntimeConfiguration runtimeConfig;
    private Suites suites;
    private LIRSuites lirSuites;
    private Suites firstTierSuites;
    private LIRSuites firstTierLirSuites;
    private Providers firstTierProviders;

    private SubstrateMethod[] methodsToCompile;
    private byte[] graphEncoding;
    private Object[] graphObjects;
    private NodeClass<?>[] graphNodeTypes;

    public final Map<Class<?>, NodeClass<?>> nodeClasses = new HashMap<>();
    public final Map<Class<?>, LIRInstructionClass<?>> instructionClasses = new HashMap<>();
    public final Map<Class<?>, CompositeValueClass<?>> compositeValueClasses = new HashMap<>();
    public HashMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> matchRuleRegistry;

    protected Map<Class<?>, BasePhase.BasePhaseStatistics> basePhaseStatistics;
    protected Map<Class<?>, LIRPhase.LIRPhaseStatistics> lirPhaseStatistics;
    protected Function<Providers, SubstrateBackend> runtimeBackendProvider;

    protected final GlobalMetrics metricValues = new GlobalMetrics();
    protected final List<DebugHandlersFactory> debugHandlersFactories = new ArrayList<>();
    protected final DiagnosticsOutputDirectory outputDirectory = new DiagnosticsOutputDirectory(RuntimeOptionValues.singleton());
    protected final Map<ExceptionAction, Integer> compilationProblemsPerAction = new EnumMap<>(ExceptionAction.class);

    private static final CGlobalData<Pointer> nextIsolateId = CGlobalDataFactory.createWord((Pointer) WordFactory.unsigned(1L));

    private volatile long isolateId = 0;

    /**
     * Gets an identifier for the current isolate that is guaranteed to be unique for the first
     * {@code 2^64 - 1} isolates in the process.
     *
     * @return a non-zero value
     */
    public long getIsolateId() {
        if (isolateId == 0) {
            synchronized (this) {
                if (isolateId == 0) {
                    Pointer p = nextIsolateId.get();
                    long value;
                    long nextValue;
                    do {
                        value = p.readLong(0);
                        nextValue = value + 1;
                        if (nextValue == 0) {
                            // Avoid setting id to reserved 0 value after long integer overflow
                            nextValue = 1;
                        }
                    } while (p.compareAndSwapLong(0, value, nextValue, ANY_LOCATION) != value);
                    isolateId = value;
                }
            }
        }
        return isolateId;
    }

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
    public GraalSupport() {
        /* By default the backend configuration is the same as for the native image. */
        runtimeBackendProvider = SubstrateBackendFactory.get()::newBackend;

        for (DebugHandlersFactory c : GraalServices.load(DebugHandlersFactory.class)) {
            debugHandlersFactories.add(c);
        }
    }

    public HashMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> getMatchRuleRegistry() {
        return matchRuleRegistry;
    }

    public void setMatchRuleRegistry(HashMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> matchRuleRegistry) {
        this.matchRuleRegistry = matchRuleRegistry;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void setRuntimeConfig(RuntimeConfiguration runtimeConfig, Suites suites, LIRSuites lirSuites, Suites firstTierSuites, LIRSuites firstTierLirSuites) {
        get().runtimeConfig = runtimeConfig;
        get().suites = suites;
        get().lirSuites = lirSuites;
        get().firstTierSuites = firstTierSuites;
        get().firstTierLirSuites = firstTierLirSuites;
        get().firstTierProviders = runtimeConfig.getBackendForNormalMethod().getProviders();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean setMethodsToCompile(SubstrateMethod[] methodsToCompile) {
        boolean result = false;
        if (!Arrays.equals(get().methodsToCompile, methodsToCompile)) {
            get().methodsToCompile = methodsToCompile;
            result = true;
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean setGraphEncoding(byte[] graphEncoding, Object[] graphObjects, NodeClass<?>[] graphNodeTypes) {
        if (get().graphObjects == null && graphObjects.length == 0) {
            assert graphEncoding.length == 0;
            assert graphNodeTypes.length == 0;
            return false;
        }
        boolean result = false;
        if (!Arrays.equals(get().graphEncoding, graphEncoding)) {
            get().graphEncoding = graphEncoding;
            result = true;
        }
        if (!Arrays.deepEquals(get().graphObjects, graphObjects)) {
            get().graphObjects = graphObjects;
            result = true;
        }
        if (!Arrays.equals(get().graphNodeTypes, graphNodeTypes)) {
            get().graphNodeTypes = graphNodeTypes;
            result = true;
        }
        return result;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerImmutableObjects(CompilationAccess access) {
        access.registerAsImmutable(get().graphEncoding);
        access.registerAsImmutable(get().graphObjects);
        access.registerAsImmutable(get().graphNodeTypes);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void allocatePhaseStatisticsCache() {
        GraalSupport.get().basePhaseStatistics = new HashMap<>();
        GraalSupport.get().lirPhaseStatistics = new HashMap<>();
    }

    /* Invoked once for every class that is reachable in the native image. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public static void registerPhaseStatistics(DuringAnalysisAccess a, Class<?> newlyReachableClass) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;

        if (!Modifier.isAbstract(newlyReachableClass.getModifiers())) {
            if (BasePhase.class.isAssignableFrom(newlyReachableClass)) {
                registerStatistics(newlyReachableClass, GraalSupport.get().basePhaseStatistics, new BasePhase.BasePhaseStatistics(newlyReachableClass), access);

            } else if (LIRPhase.class.isAssignableFrom(newlyReachableClass)) {
                registerStatistics(newlyReachableClass, GraalSupport.get().lirPhaseStatistics, new LIRPhase.LIRPhaseStatistics(newlyReachableClass), access);

            }
        }
    }

    private static <S> void registerStatistics(Class<?> phaseSubClass, Map<Class<?>, S> cache, S newStatistics, DuringAnalysisAccessImpl access) {
        assert !cache.containsKey(phaseSubClass);

        cache.put(phaseSubClass, newStatistics);
        access.requireAnalysisIteration();
    }

    public static GraalSupport get() {
        return ImageSingletons.lookup(GraalSupport.class);
    }

    public static RuntimeConfiguration getRuntimeConfig() {
        return get().runtimeConfig;
    }

    public static Suites getSuites() {
        return get().suites;
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
        return new EncodedGraph(get().graphEncoding, startOffset, get().graphObjects, get().graphNodeTypes, null, null, null, false, trackNodeSourcePosition);
    }

    public static StructuredGraph decodeGraph(DebugContext debug, String name, CompilationIdentifier compilationId, SharedRuntimeMethod method) {
        EncodedGraph encodedGraph = encodedGraph(method, false);
        if (encodedGraph == null) {
            return null;
        }

        boolean isSubstitution = method.getAnnotation(Snippet.class) != null || method.getAnnotation(MethodSubstitution.class) != null;
        StructuredGraph graph = new StructuredGraph.Builder(debug.getOptions(), debug).name(name).method(method).compilationId(compilationId).setIsSubstitution(isSubstitution).build();
        GraphDecoder decoder = new GraphDecoder(ConfigurationValues.getTarget().arch, graph);
        decoder.decode(encodedGraph);
        return graph;
    }

    public static class GraalShutdownHook implements Runnable {
        @Override
        public void run() {
            GraalSupport graalSupport = GraalSupport.get();
            graalSupport.metricValues.print(RuntimeOptionValues.singleton());
            graalSupport.outputDirectory.close();
        }
    }

    public static Function<Providers, SubstrateBackend> getRuntimeBackendProvider() {
        return get().runtimeBackendProvider;
    }

    public static void setRuntimeBackendProvider(Function<Providers, SubstrateBackend> backendProvider) {
        get().runtimeBackendProvider = backendProvider;
    }
}
