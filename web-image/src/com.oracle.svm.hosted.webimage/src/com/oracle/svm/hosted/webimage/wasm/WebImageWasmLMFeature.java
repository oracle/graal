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

package com.oracle.svm.hosted.webimage.wasm;

import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.ReservedRegisters;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.code.SubstrateBackend;
import com.oracle.svm.core.graal.code.SubstrateBackendFactory;
import com.oracle.svm.core.graal.code.SubstrateLoweringProviderFactory;
import com.oracle.svm.core.graal.code.SubstrateRegisterConfigFactory;
import com.oracle.svm.core.graal.code.SubstrateSuitesCreatorProvider;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.snippets.ExceptionSnippets;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.hosted.webimage.codegen.WebImageNoRegisterConfig;
import com.oracle.svm.hosted.webimage.wasm.codegen.WasmAssembler;
import com.oracle.svm.hosted.webimage.wasm.codegen.WebImageWasmLMBackend;
import com.oracle.svm.hosted.webimage.wasm.phases.WebImageWasmLMSuitesCreatorProvider;
import com.oracle.svm.webimage.platform.WebImageWasmLMPlatform;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.java.LoadExceptionObjectNode;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.TargetGraphBuilderPlugins;

@AutomaticallyRegisteredFeature
@Platforms(WebImageWasmLMPlatform.class)
public class WebImageWasmLMFeature implements InternalFeature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        WasmAssembler.install();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(SubstrateRegisterConfigFactory.class, (config, metaAccess, target, preserveFramePointer) -> new WebImageNoRegisterConfig());

        ImageSingletons.add(ReservedRegisters.class, new WebImageWasmReservedRegisters());

        ImageSingletons.add(SubstrateBackendFactory.class, new SubstrateBackendFactory() {
            @Override
            public SubstrateBackend newBackend(Providers newProviders) {
                return new WebImageWasmLMBackend(newProviders);
            }
        });

        ImageSingletons.add(SubstrateLoweringProviderFactory.class, WebImageWasmLMLoweringProvider::new);
        ImageSingletons.add(TargetGraphBuilderPlugins.class, new WasmLMGraphBuilderPlugins());
        ImageSingletons.add(SubstrateSuitesCreatorProvider.class, new WebImageWasmLMSuitesCreatorProvider());
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        lowerings.put(LoadExceptionObjectNode.class, new ExceptionSnippets.LoadExceptionObjectLowering());
    }
}
