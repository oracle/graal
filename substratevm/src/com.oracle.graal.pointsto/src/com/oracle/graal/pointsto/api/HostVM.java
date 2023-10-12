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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.oracle.svm.common.ClassInitializationNode;
import jdk.vm.ci.meta.ResolvedJavaField;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.java.GraphBuilderPhase.Instance;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.UnsafeAccessNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.nodes.java.AccessFieldNode;
import org.graalvm.compiler.nodes.java.AccessMonitorNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.flow.InvokeTypeFlow;
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisField;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.FieldValueComputer;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisGraphDecoder;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;
import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.common.meta.MultiMethod;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This abstract class defines the functionality that the hosting VM must support.
 */
public abstract class HostVM {

    protected final OptionValues options;
    protected final ClassLoader classLoader;
    protected final List<BiConsumer<AnalysisMethod, StructuredGraph>> methodAfterParsingListeners;
    private final List<BiConsumer<DuringAnalysisAccess, Class<?>>> classReachabilityListeners;
    private HostedProviders providers;
    protected final ConcurrentMap<AnalysisMethod, Boolean> classInitializerSideEffect = new ConcurrentHashMap<>();
    private final ConcurrentMap<AnalysisMethod, Set<AnalysisType>> initializedClasses = new ConcurrentHashMap<>();

    protected HostVM(OptionValues options, ClassLoader classLoader) {
        this.options = options;
        this.classLoader = classLoader;
        this.methodAfterParsingListeners = new CopyOnWriteArrayList<>();
        this.classReachabilityListeners = new ArrayList<>();
    }

    public OptionValues options() {
        return options;
    }

    /**
     * Check if the provided object is a relocated pointer.
     * 
     * @param metaAccess the meta-access provider
     * @param constant the constant to check
     */
    public boolean isRelocatedPointer(UniverseMetaAccess metaAccess, JavaConstant constant) {
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
     * Check if the type is allowed.
     * 
     * @param type the type to check
     * @param kind usage kind
     */
    public void checkForbidden(AnalysisType type, AnalysisType.UsageKind kind) {
    }

    /**
     * Register newly created type.
     * 
     * @param newValue the type to register
     */
    public void registerType(AnalysisType newValue) {
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
    public abstract void onTypeReachable(AnalysisType newValue);

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

    public void addMethodAfterParsingListener(BiConsumer<AnalysisMethod, StructuredGraph> methodAfterParsingHook) {
        methodAfterParsingListeners.add(methodAfterParsingHook);
    }

    /**
     * Can be overwritten to run code after a method is parsed.
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
    public abstract void methodBeforeTypeFlowCreationHook(BigBang bb, AnalysisMethod method, StructuredGraph graph);

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

    public void installInThread(@SuppressWarnings("unused") Object vmConfig) {
        Thread.currentThread().setContextClassLoader(classLoader);
    }

    public void clearInThread() {
    }

    public Object getConfiguration() {
        return null;
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

    public FieldValueComputer createFieldValueComputer(@SuppressWarnings("unused") AnalysisField field) {
        return null;
    }

    /**
     * Classes are only safe for automatic initialization if the class initializer has no side
     * effect on other classes and cannot be influenced by other classes. Otherwise there would be
     * observable side effects. For example, if a class initializer of class A writes a static field
     * B.f in class B, then someone could rely on reading the old value of B.f before triggering
     * initialization of A. Similarly, if a class initializer of class A reads a static field B.f,
     * then an early automatic initialization of class A could read a non-yet-set value of B.f.
     *
     * Note that it is not necessary to disallow instance field accesses: Objects allocated by the
     * class initializer itself can always be accessed because they are independent from other
     * initializers; all other objects must be loaded transitively from a static field.
     *
     * Currently, we are conservative and mark all methods that access static fields as unsafe for
     * automatic class initialization (unless the class initializer itself accesses a static field
     * of its own class - the common way of initializing static fields). The check could be relaxed
     * by tracking the call chain, i.e., allowing static field accesses when the root method of the
     * call chain is the class initializer. But this does not fit well into the current approach
     * where each method has a `Safety` flag.
     */
    protected void checkClassInitializerSideEffect(AnalysisMethod method, Node n) {
        if (n instanceof AccessFieldNode) {
            ResolvedJavaField field = ((AccessFieldNode) n).field();
            if (isUnsafeFieldAccessing(method, field)) {
                classInitializerSideEffect.put(method, true);
            }
        } else if (n instanceof UnsafeAccessNode) {
            /*
             * Unsafe memory access nodes are rare, so it does not pay off to check what kind of
             * field they are accessing.
             */
            classInitializerSideEffect.put(method, true);
        } else if (n instanceof ClassInitializationNode) {
            ResolvedJavaType type = ((ClassInitializationNode) n).constantTypeOrNull(getProviders(method.getMultiMethodKey()).getConstantReflection());
            if (type != null) {
                initializedClasses.computeIfAbsent(method, k -> new HashSet<>()).add((AnalysisType) type);
            } else {
                classInitializerSideEffect.put(method, true);
            }
        } else if (n instanceof AccessMonitorNode) {
            classInitializerSideEffect.put(method, true);
        }
    }

    protected boolean isUnsafeFieldAccessing(AnalysisMethod method, ResolvedJavaField field) {
        return field.isStatic() && (!method.isClassInitializer() || !field.getDeclaringClass().equals(method.getDeclaringClass()));
    }

    public boolean hasClassInitializerSideEffect(AnalysisMethod method) {
        return classInitializerSideEffect.containsKey(method);
    }

    public Set<AnalysisType> getInitializedClasses(AnalysisMethod method) {
        Set<AnalysisType> result = initializedClasses.get(method);
        if (result != null) {
            return result;
        } else {
            return Collections.emptySet();
        }
    }
}
