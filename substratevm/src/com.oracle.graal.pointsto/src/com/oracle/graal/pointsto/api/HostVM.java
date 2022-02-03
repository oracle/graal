/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Optional;

import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.java.GraphBuilderPhase;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import org.graalvm.compiler.nodes.graphbuilderconf.IntrinsicContext;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.OptimisticOptimizations;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.PointsToAnalysis;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.graal.pointsto.phases.InlineBeforeAnalysisPolicy;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This is an interface for the functionality that the hosting VM must support.
 */
public interface HostVM {

    OptionValues options();

    boolean isRelocatedPointer(Object originalObject);

    void clearInThread();

    void installInThread(Object vmConfig);

    Object getConfiguration();

    void checkForbidden(AnalysisType type, AnalysisType.UsageKind kind);

    void registerType(AnalysisType newValue);

    void initializeType(AnalysisType newValue);

    boolean isInitialized(AnalysisType type);

    /**
     * Hook to change the {@link GraphBuilderConfiguration} used for parsing a method during
     * analysis.
     * 
     * @param config The default configuration used by the static analysis.
     * @param method The method that is going to be parsed with the returned configuration.
     * @return The updated configuration for the method.
     */
    default GraphBuilderConfiguration updateGraphBuilderConfiguration(GraphBuilderConfiguration config, AnalysisMethod method) {
        return config;
    }

    Optional<AnalysisMethod> handleForeignCall(ForeignCallDescriptor foreignCallDescriptor, ForeignCallsProvider foreignCallsProvider);

    GraphBuilderPhase.Instance createGraphBuilderPhase(HostedProviders providers, GraphBuilderConfiguration graphBuilderConfig, OptimisticOptimizations optimisticOpts,
                    IntrinsicContext initialIntrinsicContext);

    String inspectServerContentPath();

    void warn(String message);

    /**
     * Gets the name of the native image being built.
     *
     * @return {@code null} if this VM is not being used in the context of building a native image
     */
    default String getImageName() {
        return null;
    }

    void checkType(ResolvedJavaType type, AnalysisUniverse universe);

    void methodAfterParsingHook(BigBang bb, AnalysisMethod method, StructuredGraph graph);

    void methodBeforeTypeFlowCreationHook(PointsToAnalysis bb, AnalysisMethod method, StructuredGraph graph);

    default boolean hasNeverInlineDirective(@SuppressWarnings("unused") ResolvedJavaMethod method) {
        /* No inlining by the static analysis unless explicitly overwritten by the VM. */
        return true;
    }

    default InlineBeforeAnalysisPolicy<?> inlineBeforeAnalysisPolicy() {
        /* No inlining by the static analysis unless explicitly overwritten by the VM. */
        return InlineBeforeAnalysisPolicy.NO_INLINING;
    }

    @SuppressWarnings("unused")
    default boolean skipInterface(AnalysisUniverse universe, ResolvedJavaType interfaceType, ResolvedJavaType implementingType) {
        return false;
    }

    @SuppressWarnings("unused")
    default boolean platformSupported(AnnotatedElement element) {
        return true;
    }
}
