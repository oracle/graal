/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.amd64;

import jdk.graal.compiler.core.amd64.AMD64LoweringProviderMixin;
import jdk.graal.compiler.core.common.spi.ForeignCallsProvider;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.debug.DebugHandlersFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.HotSpotBackend;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.meta.DefaultHotSpotLoweringProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.meta.HotSpotRegistersProvider;
import jdk.graal.compiler.hotspot.replacements.HotSpotAllocationSnippets;
import jdk.graal.compiler.hotspot.replacements.arraycopy.HotSpotArraycopySnippets;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.spi.PlatformConfigurationProvider;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.graal.compiler.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;
import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AMD64HotSpotLoweringProvider extends DefaultHotSpotLoweringProvider implements AMD64LoweringProviderMixin {

    private AMD64X87MathSnippets.Templates mathSnippets;

    public AMD64HotSpotLoweringProvider(HotSpotGraalRuntimeProvider runtime, MetaAccessProvider metaAccess, ForeignCallsProvider foreignCalls, HotSpotRegistersProvider registers,
                    HotSpotConstantReflectionProvider constantReflection, PlatformConfigurationProvider platformConfig, MetaAccessExtensionProvider metaAccessExtensionProvider,
                    TargetDescription target) {
        super(runtime, metaAccess, foreignCalls, registers, constantReflection, platformConfig, metaAccessExtensionProvider, target);
    }

    @Override
    public void initialize(OptionValues options, Iterable<DebugHandlersFactory> factories, HotSpotProviders providers, GraalHotSpotVMConfig config,
                    HotSpotArraycopySnippets.Templates arraycopySnippetTemplates,
                    HotSpotAllocationSnippets.Templates allocationSnippetTemplates) {
        mathSnippets = new AMD64X87MathSnippets.Templates(options, providers);
        super.initialize(options, factories, providers, config, arraycopySnippetTemplates, allocationSnippetTemplates);
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        if (lowerAMD64(n)) {
            return;
        }
        if (n instanceof UnaryMathIntrinsicNode) {
            lowerUnaryMath((UnaryMathIntrinsicNode) n, tool);
        } else {
            super.lower(n, tool);
        }
    }

    private void lowerUnaryMath(UnaryMathIntrinsicNode math, LoweringTool tool) {
        if (tool.getLoweringStage() == LoweringTool.StandardLoweringStage.HIGH_TIER) {
            return;
        }
        StructuredGraph graph = math.graph();
        ResolvedJavaMethod method = graph.method();
        if (method != null && getReplacements().isSnippet(method)) {
            // In the context of SnippetStub, i.e., Graal-generated stubs, use the LIR
            // lowering to emit the stub assembly code instead of the Node lowering.
            return;
        }
        if (!HotSpotBackend.Options.GraalArithmeticStubs.getValue(graph.getOptions())) {
            switch (math.getOperation()) {
                case SIN:
                case COS:
                case TAN:
                    // Math.sin(), .cos() and .tan() guarantee a value within 1 ULP of the exact
                    // result, but x87 trigonometric FPU instructions are only that accurate within
                    // [-pi/4, pi/4]. The snippets fall back to a foreign call to HotSpot stubs
                    // should the inputs outside of that interval.
                    mathSnippets.lower(math, tool);
                    return;
                case LOG:
                    math.replaceAtUsages(graph.addOrUnique(new AMD64X87MathIntrinsicNode(math.getValue(), UnaryOperation.LOG)));
                    return;
                case LOG10:
                    math.replaceAtUsages(graph.addOrUnique(new AMD64X87MathIntrinsicNode(math.getValue(), UnaryOperation.LOG10)));
                    return;
            }
        }
        lowerUnaryMathToForeignCall(math, tool);
    }

    @Override
    public boolean supportsRounding() {
        return ((AMD64) getTarget().arch).getFeatures().contains(AMD64.CPUFeature.SSE4_1);
    }

}
