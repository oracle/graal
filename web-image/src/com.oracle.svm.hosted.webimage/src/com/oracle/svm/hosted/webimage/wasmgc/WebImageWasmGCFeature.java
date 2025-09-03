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

package com.oracle.svm.hosted.webimage.wasmgc;

import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.GCRelatedMXBeans;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateBackendFactory;
import com.oracle.svm.core.graal.code.SubstrateLoweringProviderFactory;
import com.oracle.svm.core.graal.code.SubstrateRegisterConfigFactory;
import com.oracle.svm.core.graal.code.SubstrateSuitesCreatorProvider;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.ExceptionSnippets;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.webimage.WebImageFeature;
import com.oracle.svm.hosted.webimage.codegen.WebImageNoRegisterConfig;
import com.oracle.svm.hosted.webimage.codegen.WebImageProviders;
import com.oracle.svm.hosted.webimage.snippets.WebImageIdentityHashCodeSnippets;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmAssembler;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WasmGCCloneSupport;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WebImageWasmGCBackend;
import com.oracle.svm.hosted.webimage.wasmgc.codegen.WebImageWasmGCProviders;
import com.oracle.svm.hosted.webimage.wasmgc.phases.WebImageWasmGCSuitesCreatorProvider;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;
import com.oracle.svm.webimage.wasmgc.WasmExtern;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.java.LoadExceptionObjectNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.TargetGraphBuilderPlugins;

@AutomaticallyRegisteredFeature
@Platforms(WebImageWasmGCPlatform.class)
public class WebImageWasmGCFeature implements InternalFeature {

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        /*
         * Run after WebImageFeature because it registers the WebImageProviders, which is needed in
         * this feature.
         */
        return List.of(WebImageFeature.class);
    }

    @Override
    public void afterRegistration(Feature.AfterRegistrationAccess access) {
        ImageSingletons.add(SubstrateRegisterConfigFactory.class, (config, metaAccess, target, preserveFramePointer) -> new WebImageNoRegisterConfig());

        ImageSingletons.add(SubstrateBackendFactory.class, new SubstrateBackendFactory() {
            @Override
            public SubstrateBackend newBackend(Providers newProviders) {
                return new WebImageWasmGCBackend(newProviders);
            }
        });

        ImageSingletons.add(SubstrateLoweringProviderFactory.class, WebImageWasmGCLoweringProvider::new);
        ImageSingletons.add(TargetGraphBuilderPlugins.class, new WasmGCGraphBuilderPlugins());
        ImageSingletons.add(SubstrateSuitesCreatorProvider.class, new WebImageWasmGCSuitesCreatorProvider());

        ImageSingletons.add(GCRelatedMXBeans.class, new GCRelatedMXBeans());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        WasmAssembler.install();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;
        for (SnippetRuntime.SubstrateForeignCallDescriptor descriptor : WasmGCAllocationSupport.FOREIGN_CALLS) {
            access.registerAsRoot((AnalysisMethod) descriptor.findMethod(access.getMetaAccess()), true, "WasmGC allocation foreign calls, registered in " + WebImageWasmGCFeature.class);
        }

        access.registerAsRoot((AnalysisMethod) WasmGCConversion.PROXY_OBJECT.findMethod(access.getMetaAccess()), true,
                        "Calls to this method are created after compilation, registered in " + WebImageWasmGCFeature.class);

        // Instances of this class are created when doing interop with the host runtime
        access.registerAsUnsafeAllocated(WasmExtern.class);

        access.registerAsRoot((AnalysisMethod) WasmGCArrayCopySupport.ARRAYCOPY.findMethod(access.getMetaAccess()), true, "System.arraycopy support, registered in " + WebImageWasmGCFeature.class);
        access.registerAsRoot((AnalysisMethod) WasmGCCloneSupport.CLONE.findMethod(access.getMetaAccess()), true, "Cloning support, registered in " + WebImageWasmGCFeature.class);

        for (SnippetRuntime.SubstrateForeignCallDescriptor descriptor : WasmGCTypeCheckSupport.FOREIGN_CALLS) {
            access.registerAsRoot((AnalysisMethod) descriptor.findMethod(access.getMetaAccess()), true, "Type check support, registered in " + WebImageWasmGCFeature.class);
        }
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        WasmGCAllocationSupport.registerForeignCalls(foreignCalls);
        WasmGCConversion.registerForeignCalls(foreignCalls);
        foreignCalls.register(WebImageIdentityHashCodeSnippets.COMPUTE_IDENTITY_HASH_CODE);
        WasmGCArrayCopySupport.registerForeignCalls(foreignCalls);
        WasmGCTypeCheckSupport.registerForeignCalls(foreignCalls);
        WasmGCCloneSupport.registerForeignCalls(foreignCalls);
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        // For lowering ValidateNewInstanceClassNode
        SubstrateAllocationSnippets allocationSnippets = ImageSingletons.lookup(SubstrateAllocationSnippets.class);
        SubstrateAllocationSnippets.Templates templates = new SubstrateAllocationSnippets.Templates(options, providers, allocationSnippets);
        templates.registerLowering(lowerings);

        WasmGCArrayCopySupport.registerLowering(lowerings);

        lowerings.put(LoadExceptionObjectNode.class, new ExceptionSnippets.LoadExceptionObjectLowering());
        WasmGCCloneSupport.registerLowerings(lowerings);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        WasmGCAllocationSupport.preRegisterAllocationTemplates((WebImageWasmGCProviders) ImageSingletons.lookup(WebImageProviders.class));
    }
}
