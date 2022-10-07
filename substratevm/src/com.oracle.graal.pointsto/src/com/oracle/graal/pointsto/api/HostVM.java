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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.java.GraphBuilderPhase.Instance;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;
import org.graalvm.nativeimage.hosted.Feature.DuringAnalysisAccess;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;

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
    public abstract void initializeType(AnalysisType newValue);

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

    public abstract Instance createGraphBuilderPhase(HostedProviders providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
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

    public InlineBeforeAnalysisPolicy<?> inlineBeforeAnalysisPolicy() {
        /* No inlining by the static analysis unless explicitly overwritten by the VM. */
        return InlineBeforeAnalysisPolicy.NO_INLINING;
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
}
