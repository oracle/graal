/*
 * Copyright (c) 2014, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.compiler.graal.hotspot.aarch64;

import jdk.compiler.graal.core.aarch64.AArch64LoweringProviderMixin;
import jdk.compiler.graal.core.common.spi.ForeignCallsProvider;
import jdk.compiler.graal.core.common.spi.MetaAccessExtensionProvider;
import jdk.compiler.graal.debug.DebugHandlersFactory;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.hotspot.GraalHotSpotVMConfig;
import jdk.compiler.graal.hotspot.HotSpotGraalRuntimeProvider;
import jdk.compiler.graal.hotspot.meta.DefaultHotSpotLoweringProvider;
import jdk.compiler.graal.hotspot.meta.HotSpotProviders;
import jdk.compiler.graal.hotspot.meta.HotSpotRegistersProvider;
import jdk.compiler.graal.hotspot.replacements.HotSpotAllocationSnippets;
import jdk.compiler.graal.hotspot.replacements.arraycopy.HotSpotArraycopySnippets;
import jdk.compiler.graal.nodes.calc.FloatConvertNode;
import jdk.compiler.graal.nodes.calc.IntegerDivRemNode;
import jdk.compiler.graal.nodes.spi.LoweringTool;
import jdk.compiler.graal.nodes.spi.PlatformConfigurationProvider;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.replacements.aarch64.AArch64IntegerArithmeticSnippets;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;

public class AArch64HotSpotLoweringProvider extends DefaultHotSpotLoweringProvider implements AArch64LoweringProviderMixin {

    private AArch64IntegerArithmeticSnippets integerArithmeticSnippets;

    public AArch64HotSpotLoweringProvider(HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, HotSpotRegistersProvider registers,
                    HotSpotConstantReflectionProvider constantReflection, PlatformConfigurationProvider platformConfig, MetaAccessExtensionProvider metaAccessExtensionProvider,
                    TargetDescription target) {
        super(runtime, metaAccess, foreignCalls, registers, constantReflection, platformConfig, metaAccessExtensionProvider, target);
    }

    @Override
    public void initialize(OptionValues options, Iterable<DebugHandlersFactory> factories, HotSpotProviders providers, GraalHotSpotVMConfig config,
                    HotSpotArraycopySnippets.Templates arraycopySnippetTemplates,
                    HotSpotAllocationSnippets.Templates allocationSnippetTemplates) {
        integerArithmeticSnippets = new AArch64IntegerArithmeticSnippets(options, providers);
        super.initialize(options, factories, providers, config, arraycopySnippetTemplates, allocationSnippetTemplates);
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        if (n instanceof IntegerDivRemNode && tool.getLoweringStage() == LoweringTool.StandardLoweringStage.LOW_TIER) {
            // try to float in high tier first
            integerArithmeticSnippets.lower((IntegerDivRemNode) n, tool);
        } else if (n instanceof FloatConvertNode) {
            // AMD64 has custom lowerings for ConvertNodes, HotSpotLoweringProvider does not expect
            // to see a ConvertNode and throws an error, just do nothing here.
        } else {
            super.lower(n, tool);
        }
    }

}
