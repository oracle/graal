/*
 * Copyright (c) 2012, 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.replacements.aot;

import static com.oracle.graal.nodes.extended.BranchProbabilityNode.VERY_SLOW_PATH_PROBABILITY;
import static com.oracle.graal.nodes.extended.BranchProbabilityNode.probability;
import static com.oracle.graal.replacements.SnippetTemplate.DEFAULT_REPLACER;

import com.oracle.graal.api.replacements.Snippet;
import com.oracle.graal.debug.GraalError;
import com.oracle.graal.hotspot.meta.HotSpotConstantLoadAction;
import com.oracle.graal.hotspot.meta.HotSpotProviders;
import com.oracle.graal.hotspot.nodes.EncodedSymbolNode;
import com.oracle.graal.hotspot.nodes.aot.InitializeKlassNode;
import com.oracle.graal.hotspot.nodes.aot.InitializeKlassStubCall;
import com.oracle.graal.hotspot.nodes.aot.LoadConstantIndirectlyNode;
import com.oracle.graal.hotspot.nodes.aot.LoadMethodCountersIndirectlyNode;
import com.oracle.graal.hotspot.nodes.aot.ResolveConstantNode;
import com.oracle.graal.hotspot.nodes.aot.ResolveConstantStubCall;
import com.oracle.graal.hotspot.nodes.aot.ResolveMethodAndLoadCountersNode;
import com.oracle.graal.hotspot.nodes.aot.ResolveMethodAndLoadCountersStubCall;
import com.oracle.graal.hotspot.nodes.type.MethodPointerStamp;
import com.oracle.graal.hotspot.word.KlassPointer;
import com.oracle.graal.hotspot.word.MethodCountersPointer;
import com.oracle.graal.hotspot.word.MethodPointer;
import com.oracle.graal.nodes.ConstantNode;
import com.oracle.graal.nodes.StructuredGraph;
import com.oracle.graal.nodes.ValueNode;
import com.oracle.graal.nodes.spi.LoweringTool;
import com.oracle.graal.nodes.util.GraphUtil;
import com.oracle.graal.replacements.SnippetTemplate;
import com.oracle.graal.replacements.SnippetTemplate.AbstractTemplates;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.replacements.Snippets;

import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.hotspot.HotSpotMetaspaceConstant;
import jdk.vm.ci.hotspot.HotSpotObjectConstant;
import jdk.vm.ci.meta.Constant;

public class ResolveConstantSnippets implements Snippets {

    @Snippet
    public static Object resolveObjectConstant(Object constant) {
        Object result = LoadConstantIndirectlyNode.loadObject(constant);
        if (probability(VERY_SLOW_PATH_PROBABILITY, result == null)) {
            result = ResolveConstantStubCall.resolveObject(constant, EncodedSymbolNode.encode(constant));
        }
        return result;
    }

    @Snippet
    public static KlassPointer resolveKlassConstant(KlassPointer constant) {
        KlassPointer result = LoadConstantIndirectlyNode.loadKlass(constant);
        if (probability(VERY_SLOW_PATH_PROBABILITY, result.isNull())) {
            result = ResolveConstantStubCall.resolveKlass(constant, EncodedSymbolNode.encode(constant));
        }
        return result;
    }

    @Snippet
    public static MethodCountersPointer resolveMethodAndLoadCounters(MethodPointer method, KlassPointer klassHint) {
        MethodCountersPointer result = LoadMethodCountersIndirectlyNode.loadMethodCounters(method);
        if (probability(VERY_SLOW_PATH_PROBABILITY, result.isNull())) {
            result = ResolveMethodAndLoadCountersStubCall.resolveMethodAndLoadCounters(method, klassHint, EncodedSymbolNode.encode(method));
        }
        return result;
    }

    @Snippet
    public static KlassPointer initializeKlass(KlassPointer constant) {
        KlassPointer result = LoadConstantIndirectlyNode.loadKlass(constant, HotSpotConstantLoadAction.INITIALIZE);
        if (probability(VERY_SLOW_PATH_PROBABILITY, result.isNull())) {
            result = InitializeKlassStubCall.initializeKlass(constant, EncodedSymbolNode.encode(constant));
        }
        return result;
    }

    @Snippet
    public static KlassPointer pureInitializeKlass(KlassPointer constant) {
        KlassPointer result = LoadConstantIndirectlyNode.loadKlass(constant, HotSpotConstantLoadAction.INITIALIZE);
        if (probability(VERY_SLOW_PATH_PROBABILITY, result.isNull())) {
            result = ResolveConstantStubCall.resolveKlass(constant, EncodedSymbolNode.encode(constant), HotSpotConstantLoadAction.INITIALIZE);
        }
        return result;
    }

    public static class Templates extends AbstractTemplates {
        private final SnippetInfo resolveObjectConstant = snippet(ResolveConstantSnippets.class, "resolveObjectConstant");
        private final SnippetInfo resolveKlassConstant = snippet(ResolveConstantSnippets.class, "resolveKlassConstant");
        private final SnippetInfo resolveMethodAndLoadCounters = snippet(ResolveConstantSnippets.class, "resolveMethodAndLoadCounters");
        private final SnippetInfo initializeKlass = snippet(ResolveConstantSnippets.class, "initializeKlass");
        private final SnippetInfo pureInitializeKlass = snippet(ResolveConstantSnippets.class, "pureInitializeKlass");

        public Templates(HotSpotProviders providers, TargetDescription target) {
            super(providers, providers.getSnippetReflection(), target);
        }

        public void lower(ResolveConstantNode resolveConstantNode, LoweringTool tool) {
            StructuredGraph graph = resolveConstantNode.graph();

            ValueNode value = resolveConstantNode.value();
            assert value.isConstant() : "Expected a constant: " + value;
            Constant constant = value.asConstant();
            SnippetInfo snippet = null;

            if (constant instanceof HotSpotMetaspaceConstant) {
                HotSpotMetaspaceConstant hotspotMetaspaceConstant = (HotSpotMetaspaceConstant) constant;
                if (hotspotMetaspaceConstant.asResolvedJavaType() != null) {
                    if (resolveConstantNode.action() == HotSpotConstantLoadAction.RESOLVE) {
                        snippet = resolveKlassConstant;
                    } else {
                        assert resolveConstantNode.action() == HotSpotConstantLoadAction.INITIALIZE;
                        snippet = pureInitializeKlass;
                    }
                }
            } else if (constant instanceof HotSpotObjectConstant) {
                snippet = resolveObjectConstant;
                HotSpotObjectConstant hotspotObjectConstant = (HotSpotObjectConstant) constant;
                assert hotspotObjectConstant.isInternedString();
            }
            if (snippet == null) {
                throw new GraalError("Unsupported constant type: " + constant);
            }

            Arguments args = new Arguments(snippet, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("constant", value);

            SnippetTemplate template = template(args);
            template.instantiate(providers.getMetaAccess(), resolveConstantNode, DEFAULT_REPLACER, tool, args);

            assert resolveConstantNode.hasNoUsages();
            if (!resolveConstantNode.isDeleted()) {
                GraphUtil.killWithUnusedFloatingInputs(resolveConstantNode);
            }
        }

        public void lower(InitializeKlassNode initializeKlassNode, LoweringTool tool) {
            StructuredGraph graph = initializeKlassNode.graph();

            ValueNode value = initializeKlassNode.value();
            assert value.isConstant() : "Expected a constant: " + value;
            Constant constant = value.asConstant();

            if (constant instanceof HotSpotMetaspaceConstant) {
                Arguments args = new Arguments(initializeKlass, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("constant", value);

                SnippetTemplate template = template(args);
                template.instantiate(providers.getMetaAccess(), initializeKlassNode, DEFAULT_REPLACER, args);
                assert initializeKlassNode.hasNoUsages();
                if (!initializeKlassNode.isDeleted()) {
                    GraphUtil.killWithUnusedFloatingInputs(initializeKlassNode);
                }

            } else {
                throw new GraalError("Unsupported constant type: " + constant);
            }
        }

        public void lower(ResolveMethodAndLoadCountersNode resolveMethodAndLoadCountersNode, LoweringTool tool) {
            StructuredGraph graph = resolveMethodAndLoadCountersNode.graph();
            ConstantNode method = ConstantNode.forConstant(MethodPointerStamp.methodNonNull(), resolveMethodAndLoadCountersNode.getMethod().getEncoding(), tool.getMetaAccess(), graph);
            Arguments args = new Arguments(resolveMethodAndLoadCounters, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("method", method);
            args.add("klassHint", resolveMethodAndLoadCountersNode.getHub());
            SnippetTemplate template = template(args);
            template.instantiate(providers.getMetaAccess(), resolveMethodAndLoadCountersNode, DEFAULT_REPLACER, tool, args);

            assert resolveMethodAndLoadCountersNode.hasNoUsages();
            if (!resolveMethodAndLoadCountersNode.isDeleted()) {
                GraphUtil.killWithUnusedFloatingInputs(resolveMethodAndLoadCountersNode);
            }
        }
    }
}
