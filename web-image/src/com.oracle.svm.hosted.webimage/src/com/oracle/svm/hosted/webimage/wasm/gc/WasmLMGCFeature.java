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

package com.oracle.svm.hosted.webimage.wasm.gc;

import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.svm.core.GCRelatedMXBeans;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.GCAllocationSupport;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.webimage.WebImageFeature;
import com.oracle.svm.webimage.platform.WebImageWasmLMPlatform;
import com.oracle.svm.webimage.wasm.code.WasmCodeInfoQueryResult;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;

/**
 * Feature for the garbage collector in the WasmLM backend.
 * <p>
 * Not to be confused with a feature in the WasmGC backend.
 */
@AutomaticallyRegisteredFeature
@Platforms(WebImageWasmLMPlatform.class)
public class WasmLMGCFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        FeatureImpl.BeforeAnalysisAccessImpl accessImpl = (FeatureImpl.BeforeAnalysisAccessImpl) access;
        AnalysisMetaAccess aMetaAccess = accessImpl.getMetaAccess();

        // TODO GR-43486 Can be removed once metadata is stored in binary format
        aMetaAccess.lookupJavaType(WasmCodeInfoQueryResult.class).registerAsInstantiated("Registered from " + WebImageFeature.class.getName());
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        SubstrateAllocationSnippets allocationSnippets = ImageSingletons.lookup(SubstrateAllocationSnippets.class);
        SubstrateAllocationSnippets.Templates templates = new SubstrateAllocationSnippets.Templates(options, providers, allocationSnippets);
        templates.registerLowering(lowerings);

        WasmLMAllocationSnippets.Templates allocationTemplates = new WasmLMAllocationSnippets.Templates(options, providers, templates);
        allocationTemplates.registerLowering(lowerings);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(GCRelatedMXBeans.class, new GCRelatedMXBeans());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(GCAllocationSupport.class, new WasmLMAllocationSupport());
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        WasmLMAllocationSupport.registerForeignCalls(foreignCalls);
    }
}
