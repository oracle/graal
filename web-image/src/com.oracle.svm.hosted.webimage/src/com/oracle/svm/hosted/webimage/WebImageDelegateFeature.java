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

package com.oracle.svm.hosted.webimage;

import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.InternalPlatform.NATIVE_ONLY;

import com.oracle.svm.core.ParsingReason;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderConfiguration;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.tiers.Suites;
import jdk.graal.compiler.phases.util.Providers;

/**
 * Feature that delegates all calls to {@link #delegate}.
 * <p>
 * TODO GR-42832: This is a mechanism enable a feature only in the JS or WASM backend but not both
 * (in which case {@link NATIVE_ONLY} cannot be used).
 *
 */
public class WebImageDelegateFeature implements InternalFeature {

    private final InternalFeature delegate;

    public WebImageDelegateFeature(InternalFeature delegate) {
        this.delegate = delegate;
    }

    @Override
    public String getURL() {
        return delegate.getURL();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return delegate.isInConfiguration(access);
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return delegate.getRequiredFeatures();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        delegate.afterRegistration(access);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        delegate.duringSetup(access);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        delegate.beforeAnalysis(access);
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        delegate.duringAnalysis(access);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        delegate.afterAnalysis(access);
    }

    @Override
    public void onAnalysisExit(OnAnalysisExitAccess access) {
        delegate.onAnalysisExit(access);
    }

    @Override
    public void beforeUniverseBuilding(BeforeUniverseBuildingAccess access) {
        delegate.beforeUniverseBuilding(access);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        delegate.beforeCompilation(access);
    }

    @Override
    public void afterCompilation(AfterCompilationAccess access) {
        delegate.afterCompilation(access);
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess access) {
        delegate.afterHeapLayout(access);
    }

    @Override
    public void beforeImageWrite(BeforeImageWriteAccess access) {
        delegate.beforeImageWrite(access);
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        delegate.afterImageWrite(access);
    }

    @Override
    public void cleanup() {
        delegate.cleanup();
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        delegate.registerForeignCalls(foreignCalls);
    }

    @Override
    public void registerInvocationPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        delegate.registerInvocationPlugins(providers, plugins, reason);
    }

    @Override
    public void registerGraphBuilderPlugins(Providers providers, GraphBuilderConfiguration.Plugins plugins, ParsingReason reason) {
        delegate.registerGraphBuilderPlugins(providers, plugins, reason);
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        delegate.registerLowerings(runtimeConfig, options, providers, lowerings, hosted);
    }

    @Override
    public void registerGraalPhases(Providers providers, Suites suites, boolean hosted) {
        delegate.registerGraalPhases(providers, suites, hosted);
    }

    @Override
    public void registerCodeObserver(RuntimeConfiguration runtimeConfig) {
        delegate.registerCodeObserver(runtimeConfig);
    }

    @Override
    public boolean isHidden() {
        return delegate.isHidden();
    }
}
