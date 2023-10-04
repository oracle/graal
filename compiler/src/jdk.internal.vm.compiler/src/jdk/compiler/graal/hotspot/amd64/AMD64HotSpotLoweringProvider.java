/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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
package jdk.compiler.graal.hotspot.amd64;

import jdk.compiler.graal.core.amd64.AMD64LoweringProviderMixin;
import jdk.compiler.graal.core.common.spi.ForeignCallsProvider;
import jdk.compiler.graal.core.common.spi.MetaAccessExtensionProvider;
import jdk.compiler.graal.debug.DebugHandlersFactory;
import jdk.compiler.graal.graph.Node;
import jdk.compiler.graal.hotspot.GraalHotSpotVMConfig;
import jdk.compiler.graal.hotspot.HotSpotBackend;
import jdk.compiler.graal.hotspot.HotSpotGraalRuntimeProvider;
import jdk.compiler.graal.hotspot.meta.DefaultHotSpotLoweringProvider;
import jdk.compiler.graal.hotspot.meta.HotSpotProviders;
import jdk.compiler.graal.hotspot.meta.HotSpotRegistersProvider;
import jdk.compiler.graal.hotspot.replacements.HotSpotAllocationSnippets;
import jdk.compiler.graal.hotspot.replacements.arraycopy.HotSpotArraycopySnippets;
import jdk.compiler.graal.nodes.StructuredGraph;
import jdk.compiler.graal.nodes.calc.FloatConvertNode;
import jdk.compiler.graal.nodes.extended.ForeignCallNode;
import jdk.compiler.graal.nodes.spi.LoweringTool;
import jdk.compiler.graal.nodes.spi.PlatformConfigurationProvider;
import jdk.compiler.graal.options.OptionValues;
import jdk.compiler.graal.replacements.amd64.AMD64ConvertSnippets;
import jdk.compiler.graal.replacements.nodes.UnaryMathIntrinsicNode;
import jdk.compiler.graal.replacements.nodes.UnaryMathIntrinsicNode.UnaryOperation;

import jdk.vm.ci.amd64.AMD64;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotConstantReflectionProvider;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class AMD64HotSpotLoweringProvider extends DefaultHotSpotLoweringProvider implements AMD64LoweringProviderMixin {

    private AMD64ConvertSnippets.Templates convertSnippets;
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
        convertSnippets = new AMD64ConvertSnippets.Templates(options, providers);
        mathSnippets = new AMD64X87MathSnippets.Templates(options, providers);
        super.initialize(options, factories, providers, config, arraycopySnippetTemplates, allocationSnippetTemplates);
    }

    @Override
    public void lower(Node n, LoweringTool tool) {
        if (lowerAMD64(n)) {
            return;
        }
        if (n instanceof FloatConvertNode) {
            convertSnippets.lower((FloatConvertNode) n, tool);
        } else if (n instanceof UnaryMathIntrinsicNode) {
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

        ForeignCallNode call = graph.add(new ForeignCallNode(foreignCalls, math.getOperation().foreignCallSignature, math.getValue()));
        graph.addAfterFixed(tool.lastFixedNode(), call);
        math.replaceAtUsages(call);
    }

    @Override
    public boolean supportsRounding() {
        return ((AMD64) getTarget().arch).getFeatures().contains(AMD64.CPUFeature.SSE4_1);
    }

}
