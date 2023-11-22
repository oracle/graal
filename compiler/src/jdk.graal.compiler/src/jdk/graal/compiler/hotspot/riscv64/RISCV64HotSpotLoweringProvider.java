/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.riscv64;

import jdk.graal.compiler.core.riscv64.RISCV64LoweringProviderMixin;
import jdk.graal.compiler.debug.DebugHandlersFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.hotspot.replacements.HotSpotAllocationSnippets;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.meta.DefaultHotSpotLoweringProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotMetaAccessExtensionProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotPlatformConfigurationProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.meta.HotSpotRegistersProvider;
import jdk.graal.compiler.hotspot.replacements.arraycopy.HotSpotArraycopySnippets;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

public class RISCV64HotSpotLoweringProvider extends DefaultHotSpotLoweringProvider implements RISCV64LoweringProviderMixin {

    public RISCV64HotSpotLoweringProvider(HotSpotGraalRuntimeProvider graalRuntime, MetaAccessProvider metaAccess, HotSpotHostForeignCallsProvider foreignCalls,
                    HotSpotRegistersProvider registers, HotSpotConstantReflectionProvider constantReflection, HotSpotPlatformConfigurationProvider platformConfig,
                    HotSpotMetaAccessExtensionProvider metaAccessExtensionProvider, TargetDescription target) {
        super(graalRuntime, metaAccess, foreignCalls, registers, constantReflection, platformConfig, metaAccessExtensionProvider, target);
    }

    @Override
    public void initialize(OptionValues options, Iterable<DebugHandlersFactory> factories, HotSpotProviders providers, GraalHotSpotVMConfig config,
                    HotSpotArraycopySnippets.Templates arraycopySnippetTemplates,
                    HotSpotAllocationSnippets.Templates allocationSnippetTemplates) {

        super.initialize(options, factories, providers, config, arraycopySnippetTemplates, allocationSnippetTemplates);
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        throw GraalError.unimplementedOverride();
    }

}
