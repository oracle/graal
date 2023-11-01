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
import jdk.graal.compiler.core.CompilationWrapper.ExceptionAction;
import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.gen.NodeMatchRules;
import jdk.graal.compiler.core.match.MatchStatement;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.DebugContext.Builder;
import jdk.graal.compiler.debug.DebugContext.Description;
import jdk.graal.compiler.debug.DebugHandlersFactory;
import jdk.graal.compiler.debug.DiagnosticsOutputDirectory;
import jdk.graal.compiler.debug.GlobalMetrics;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.lir.CompositeValueClass;
import jdk.graal.compiler.lir.LIRInstructionClass;
import jdk.graal.compiler.lir.phases.LIRPhase;
import jdk.graal.compiler.lir.phases.LIRSuites;
import jdk.graal.compiler.loop.phases.SpeculativeGuardMovementPhase;
import jdk.graal.compiler.nodes.EncodedGraph;
import jdk.graal.compiler.nodes.GraphDecoder;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.BasePhase;
import jdk.graal.compiler.phases.FloatingGuardPhase;
import jdk.graal.compiler.phases.Speculative;
import jdk.graal.compiler.phases.common.CanonicalizerPhase;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.serviceprovider.GraalServices;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature.BeforeHeapLayoutAccess;
import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;
import org.graalvm.nativeimage.hosted.Feature.FeatureAccess;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateBackendFactory;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SharedRuntimeMethod;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.graal.meta.SubstrateMethod;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;

/**
 * Holds data that is pre-computed during native image generation and accessed at run time during a
 * Graal compilation.
 */
public class GraalSupport {

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

    public final EconomicMap<Class<?>, NodeClass<?>> nodeClasses = ImageHeapMap.create();
    public final EconomicMap<Class<?>, LIRInstructionClass<?>> instructionClasses = ImageHeapMap.create();
    public final EconomicMap<Class<?>, CompositeValueClass<?>> compositeValueClasses = ImageHeapMap.create();
    public HashMap<Class<? extends NodeMatchRules>, EconomicMap<Class<? extends Node>, List<MatchStatement>>> matchRuleRegistry;

    protected EconomicMap<Class<?>, BasePhase.BasePhaseStatistics> basePhaseStatistics;
    protected EconomicMap<Class<?>, LIRPhase.LIRPhaseStatistics> lirPhaseStatistics;
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
        // remove after GR-49600 is resolved:
        effectiveSuites.getMidTier().replaceAllPhases(SpeculativeGuardMovementPhase.class, () -> new SpeculativeGuardMovementPhase(CanonicalizerPhase.create(), false, false));
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
        // remove after GR-49600 is resolved:
        effectiveSuites.getMidTier().replaceAllPhases(SpeculativeGuardMovementPhase.class, () -> new SpeculativeGuardMovementPhase(CanonicalizerPhase.create(), false, false));
        return effectiveSuites;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static boolean setMethodsToCompile(DuringAnalysisAccessImpl config, SubstrateMethod[] methodsToCompile) {
        boolean result = false;
        GraalSupport support = get();
        if (!Arrays.equals(support.methodsToCompile, methodsToCompile)) {
            support.methodsToCompile = methodsToCompile;
            GraalSupport.rescan(config, methodsToCompile);
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
        GraalSupport support = get();
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
            GraalSupport.rescan(a, graphObjects);
            result = true;
        }
        if (!Arrays.equals(support.graphNodeTypes, graphNodeTypes)) {
            support.graphNodeTypes = graphNodeTypes;
            GraalSupport.rescan(a, graphNodeTypes);
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

    @Platforms(Platform.HOSTED_ONLY.class)
    public static void allocatePhaseStatisticsCache() {
        GraalSupport.get().basePhaseStatistics = ImageHeapMap.create();
        GraalSupport.get().lirPhaseStatistics = ImageHeapMap.create();
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

    private static <S> void registerStatistics(Class<?> phaseSubClass, EconomicMap<Class<?>, S> cache, S newStatistics, DuringAnalysisAccessImpl access) {
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

    public static class GraalShutdownHook implements RuntimeSupport.Hook {
        @Override
        public void execute(boolean isFirstIsolate) {
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

    public EconomicMap<Class<?>, BasePhase.BasePhaseStatistics> getBasePhaseStatistics() {
        return basePhaseStatistics;
    }

    public EconomicMap<Class<?>, LIRPhase.LIRPhaseStatistics> getLirPhaseStatistics() {
        return lirPhaseStatistics;
    }

    public List<DebugHandlersFactory> getDebugHandlersFactories() {
        return debugHandlersFactories;
    }
}
