/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2021, 2021, Alibaba Group Holding Limited. All rights reserved.
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

package com.oracle.graal.pointsto.api;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisGraphDecoder;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.common.meta.MultiMethod;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.java.GraphBuilderPhase.Instance;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.OptimisticOptimizations;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This abstract class defines the functionality that the hosting VM must support.
 */
public abstract class HostVM {

    protected final OptionValues options;
    protected final ClassLoader classLoader;
    protected final List<BiConsumer<AnalysisMethod, StructuredGraph>> methodAfterBytecodeParsedListeners;
    protected final List<BiConsumer<AnalysisMethod, StructuredGraph>> methodAfterParsingListeners;
    private final List<BiConsumer<DuringAnalysisAccess, Class<?>>> classReachabilityListeners;
    protected HostedProviders providers;

    protected HostVM(OptionValues options, ClassLoader classLoader) {
        this.options = options;
        this.classLoader = classLoader;
        this.methodAfterBytecodeParsedListeners = new CopyOnWriteArrayList<>();
        this.methodAfterParsingListeners = new CopyOnWriteArrayList<>();
        this.classReachabilityListeners = new ArrayList<>();
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public OptionValues options() {
        return options;
    }

    /**
     * Check if the provided object is a relocated pointer.
     * 
     * @param constant the constant to check
     */
    public boolean isRelocatedPointer(JavaConstant constant) {
        return false;
    }

    /**
     * Hook for handling foreign calls.
     * 
     * @param foreignCallDescriptor the foreign call descriptor
     * @param foreignCallsProvider the foreign calls provider
     * @return the {@link AnalysisMethod} modeling the foreign call, if supported
     */
    public Optional<AnalysisMethod> handleForeignCall(ForeignCallDescriptor foreignCallDescriptor, ForeignCallsProvider foreignCallsProvider) {
        return Optional.empty();
    }

    public void registerClassReachabilityListener(BiConsumer<DuringAnalysisAccess, Class<?>> listener) {
        classReachabilityListeners.add(listener);
    }

    public void notifyClassReachabilityListener(AnalysisUniverse universe, DuringAnalysisAccess access) {
        for (AnalysisType type : universe.getTypes()) {
            if (type.isReachable() && !type.getReachabilityListenerNotified()) {
                type.setReachabilityListenerNotified(true);

                for (BiConsumer<DuringAnalysisAccess, Class<?>> listener : classReachabilityListeners) {
                    listener.accept(access, type.getJavaClass());
                }
            }
        }
    }

    /**
     * Register newly created type.
     * 
     * @param newValue the type to register
     */
    public void registerType(AnalysisType newValue) {
    }

    /**
     * Register newly created type with a given identityHashCode.
     *
     * @param newValue the type to register
     * @param identityHashCode the hash code of the hub
     */
    public void registerType(AnalysisType newValue, int identityHashCode) {
    }

    /**
     * Run additional checks on a type before the corresponding {@link AnalysisType} is created.
     * 
     * @param type the hosted type
     * @param universe the analysis universe
     */
    public void checkType(ResolvedJavaType type, AnalysisUniverse universe) {
    }

    /**
     * Run initialization tasks for a newly created {@link AnalysisType}.
     * 
     * @param newValue the type to initialize
     */
    public abstract void onTypeReachable(BigBang bb, AnalysisType newValue);

    /**
     * Run initialization tasks for a type when it is marked as instantiated.
     *
     * @param bb the static analysis
     * @param type the type that is marked as instantiated
     */
    public void onTypeInstantiated(BigBang bb, AnalysisType type) {
    }

    public boolean isCoreType(@SuppressWarnings("unused") AnalysisType type) {
        return false;
    }

    public boolean useBaseLayer() {
        return false;
    }

    public boolean analyzedInPriorLayer(@SuppressWarnings("unused") AnalysisMethod method) {
        return false;
    }

    /**
     * Check if an {@link AnalysisType} is initialized.
     */
    public abstract boolean isInitialized(AnalysisType type);

    /**
     * Hook to change the {@link GraphBuilderConfiguration} used for parsing a method during
     * analysis.
     * 
     * @param config The default configuration used by the static analysis.
     * @param method The method that is going to be parsed with the returned configuration.
     * @return The updated configuration for the method.
     */
    public GraphBuilderConfiguration updateGraphBuilderConfiguration(GraphBuilderConfiguration config, AnalysisMethod method) {
        return config;
    }

    public abstract Instance createGraphBuilderPhase(HostedProviders builderProviders, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                    IntrinsicContext initialIntrinsicContext);

    /**
     * Gets the name of the native image being built.
     *
     * @return {@code null} if this VM is not being used in the context of building a native image
     */
    public String getImageName() {
        return null;
    }

    /**
     * Notify VM about activity.
     */
    public void recordActivity() {
    }

    public void addMethodAfterBytecodeParsedListener(BiConsumer<AnalysisMethod, StructuredGraph> listener) {
        methodAfterBytecodeParsedListeners.add(listener);
    }

    public void addMethodAfterParsingListener(BiConsumer<AnalysisMethod, StructuredGraph> listener) {
        methodAfterParsingListeners.add(listener);
    }

    /**
     * Can be overwritten to run code after the bytecode of a method is parsed. This hook is
     * guaranteed to be invoked before
     * {@link #methodAfterParsingHook(BigBang, AnalysisMethod, StructuredGraph)} .
     *
     * @param bb the analysis engine
     * @param method the newly parsed method
     * @param graph the method graph
     */
    public void methodAfterBytecodeParsedHook(BigBang bb, AnalysisMethod method, StructuredGraph graph) {
        for (BiConsumer<AnalysisMethod, StructuredGraph> listener : methodAfterBytecodeParsedListeners) {
            listener.accept(method, graph);
        }
    }

    /**
     * Can be overwritten to run code after a method is parsed and all pre-analysis optimizations
     * are finished. This hook will be invoked before the graph is made available to the analysis.
     *
     * @param bb the analysis engine
     * @param method the newly parsed method
     * @param graph the method graph
     */
    public void methodAfterParsingHook(BigBang bb, AnalysisMethod method, StructuredGraph graph) {
        for (BiConsumer<AnalysisMethod, StructuredGraph> listener : methodAfterParsingListeners) {
            listener.accept(method, graph);
        }
    }

    /**
     * Can be overwritten to run code before a method is created.
     *
     * @param bb the analysis engine
     * @param method the newly created method
     * @param graph the method graph
     */
    public void methodBeforeTypeFlowCreationHook(BigBang bb, AnalysisMethod method, StructuredGraph graph) {
    }

    /**
     * Check if the method can be inlined.
     *
     * @param method the target method
     */
    public boolean hasNeverInlineDirective(ResolvedJavaMethod method) {
        /* No inlining by the static analysis unless explicitly overwritten by the VM. */
        return true;
    }

    public InlineBeforeAnalysisGraphDecoder createInlineBeforeAnalysisGraphDecoder(BigBang bb, AnalysisMethod method, StructuredGraph resultGraph) {
        /* No inlining by the static analysis unless explicitly overwritten by the VM. */
        return new InlineBeforeAnalysisGraphDecoder(bb, InlineBeforeAnalysisPolicy.NO_INLINING, resultGraph, bb.getProviders(method), null);
    }

    @SuppressWarnings("unused")
    public boolean skipInterface(AnalysisUniverse universe, ResolvedJavaType interfaceType, ResolvedJavaType implementingType) {
        return false;
    }

    /**
     * Check if the element is supported on current platform.
     * 
     * @param element the {@link AnnotatedElement} to check
     */
    public boolean platformSupported(AnnotatedElement element) {
        return true;
    }

    public void clearInThread() {
    }

    public abstract Comparator<? super ResolvedJavaType> getTypeComparator();

    /*
     * Marker objects for {@code parseGraph}.
     */
    public static final Object PARSING_UNHANDLED = new Object();
    public static final Object PARSING_FAILED = new Object();

    /**
     * Hooks to allow the VM to perform a custom parsing routine for the graph.
     *
     * @return {@link #PARSING_UNHANDLED} when no custom parsing was performed,
     *         {@link #PARSING_FAILED} if a custom parse attempt failed, or a
     *         {@link StructuredGraph} when a custom parsing routine has been performed.
     */
    @SuppressWarnings("unused")
    public Object parseGraph(BigBang bb, DebugContext debug, AnalysisMethod method) {
        return PARSING_UNHANDLED;
    }

    /**
     * Determines when the graph is valid and should be processed by the analysis.
     *
     * @return Whether this is a valid graph or not.
     */
    @SuppressWarnings("unused")
    public boolean validateGraph(PointsToAnalysis bb, StructuredGraph graph) {
        return true;
    }

    /**
     * @return Whether assumptions are allowed.
     */
    @SuppressWarnings("unused")
    public StructuredGraph.AllowAssumptions allowAssumptions(AnalysisMethod method) {
        return StructuredGraph.AllowAssumptions.NO;
    }

    /**
     * @return Whether which methods were inlined should be recorded.
     */
    @SuppressWarnings("unused")
    public boolean recordInlinedMethods(AnalysisMethod method) {
        return false;
    }

    public void initializeProviders(HostedProviders newProviders) {
        AnalysisError.guarantee(providers == null, "can only initialize providers once");
        providers = newProviders;
    }

    @SuppressWarnings("unused")
    public HostedProviders getProviders(MultiMethod.MultiMethodKey key) {
        return providers;
    }

    @SuppressWarnings("unused")
    public boolean isFieldIncluded(BigBang bb, Field field) {
        return true;
    }

    public boolean isClosedTypeWorld() {
        return true;
    }

    public boolean enableTrackAcrossLayers() {
        return false;
    }

    /**
     * Helpers to determine what analysis actions should be taken for a given Multi-Method version.
     */
    public interface MultiMethodAnalysisPolicy {

        /**
         * Determines what versions of a method are reachable from the call.
         *
         * @param implementation The resolved destination. {@link MultiMethod#ORIGINAL_METHOD}.
         * @param target The original target. This should be a {@link MultiMethod#ORIGINAL_METHOD}.
         * @param callerMultiMethodKey The context in which the call is being made.
         * @return Collection of possible targets for a given implementation, target, and caller
         *         multi-method key. This method is expected to return a consistent value when
         *         called multiple times with the same parameters; hence, values returned by this
         *         method are allowed to be cached
         */
        <T extends AnalysisMethod> Collection<T> determineCallees(BigBang bb, T implementation, T target, MultiMethod.MultiMethodKey callerMultiMethodKey, InvokeTypeFlow invokeFlow);

        /**
         * Decides whether the caller's flows should be linked to callee's parameters flows.
         */
        boolean performParameterLinking(MultiMethod.MultiMethodKey callerMultiMethodKey, MultiMethod.MultiMethodKey calleeMultiMethodKey);

        /**
         * Decides whether the callee's return flow should be linked to caller's flows.
         */
        boolean performReturnLinking(MultiMethod.MultiMethodKey callerMultiMethodKey, MultiMethod.MultiMethodKey calleeMultiMethodKey);

        /**
         * Decides whether analysis should compute the returned parameter index.
         */
        boolean canComputeReturnedParameterIndex(MultiMethod.MultiMethodKey multiMethodKey);

        /**
         * Decides whether placeholder flows should be inserted for missing object parameter and
         * return values.
         */
        boolean insertPlaceholderParamAndReturnFlows(MultiMethod.MultiMethodKey multiMethodKey);

        /**
         * Some methods can be transformed after analysis; in these cases we do not know what the
         * returned value will be.
         */
        boolean unknownReturnValue(BigBang bb, MultiMethod.MultiMethodKey callerMultiMethodKey, AnalysisMethod implementation);

    }

    /**
     * The default policy does not alter the typical analysis behavior.
     */
    protected static final MultiMethodAnalysisPolicy DEFAULT_MULTIMETHOD_ANALYSIS_POLICY = new MultiMethodAnalysisPolicy() {

        @Override
        public <T extends AnalysisMethod> Collection<T> determineCallees(BigBang bb, T implementation, T target, MultiMethod.MultiMethodKey callerMultiMethodKey, InvokeTypeFlow invokeFlow) {
            return List.of(implementation);
        }

        @Override
        public boolean performParameterLinking(MultiMethod.MultiMethodKey callerMultiMethodKey, MultiMethod.MultiMethodKey calleeMultiMethodKey) {
            return true;
        }

        @Override
        public boolean performReturnLinking(MultiMethod.MultiMethodKey callerMultiMethodKey, MultiMethod.MultiMethodKey calleeMultiMethodKey) {
            return true;
        }

        @Override
        public boolean canComputeReturnedParameterIndex(MultiMethod.MultiMethodKey multiMethodKey) {
            return true;
        }

        @Override
        public boolean insertPlaceholderParamAndReturnFlows(MultiMethod.MultiMethodKey multiMethodKey) {
            return false;
        }

        @Override
        public boolean unknownReturnValue(BigBang bb, MultiMethod.MultiMethodKey callerMultiMethodKey, AnalysisMethod implementation) {
            return false;
        }
    };

    public MultiMethodAnalysisPolicy getMultiMethodAnalysisPolicy() {
        return DEFAULT_MULTIMETHOD_ANALYSIS_POLICY;
    }

    public boolean ignoreInstanceOfTypeDisallowed() {
        return false;
    }

    /**
     * Returns the function Strengthen Graphs should use to improve types based on analysis results.
     */
    public Function<AnalysisType, ResolvedJavaType> getStrengthenGraphsToTargetFunction(@SuppressWarnings("unused") MultiMethod.MultiMethodKey key) {
        return (t) -> t;
    }

    public boolean allowConstantFolding(AnalysisMethod method) {
        /*
         * Currently constant folding is only enabled for original methods. More work is needed to
         * support it within deoptimization targets and runtime-compiled methods.
         */
        return method.isOriginalMethod();
    }
}
