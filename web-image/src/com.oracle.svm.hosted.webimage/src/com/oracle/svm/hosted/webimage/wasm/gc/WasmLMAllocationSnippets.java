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
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.AllocationSnippets;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.word.Word;

public final class WasmLMAllocationSnippets implements Snippets {
    @Snippet
    public static Object formatObjectSnippet(Word memory, DynamicHub hub, boolean rememberedSet, AllocationSnippets.FillContent fillContents, boolean emitMemoryBarrier,
                    @ConstantParameter AllocationSnippets.AllocationSnippetCounters snippetCounters) {
        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        int layoutEncoding = hubNonNull.getLayoutEncoding();
        UnsignedWord size = LayoutEncoding.getPureInstanceAllocationSize(layoutEncoding);
        Word objectHeader = encodeAsObjectHeader(hubNonNull, rememberedSet, false);
        return alloc().formatObject(objectHeader, size, memory, fillContents, emitMemoryBarrier, false, snippetCounters);
    }

    @Snippet
    public static Object formatArraySnippet(Word memory, DynamicHub hub, int length, boolean rememberedSet, boolean unaligned, AllocationSnippets.FillContent fillContents, boolean emitMemoryBarrier,
                    @ConstantParameter boolean supportsBulkZeroing, @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter AllocationSnippets.AllocationSnippetCounters snippetCounters) {
        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        int layoutEncoding = hubNonNull.getLayoutEncoding();
        UnsignedWord size = LayoutEncoding.getArrayAllocationSize(layoutEncoding, length);
        Word objectHeader = encodeAsObjectHeader(hubNonNull, rememberedSet, unaligned);
        return alloc().formatArray(objectHeader, size, length, memory, fillContents, emitMemoryBarrier, false, supportsBulkZeroing, supportsOptimizedFilling, snippetCounters);
    }

    private static Word encodeAsObjectHeader(DynamicHub hub, boolean rememberedSet, boolean unaligned) {
        return WasmObjectHeader.encodeAsObjectHeader(hub, rememberedSet, unaligned);
    }

    @Fold
    static SubstrateAllocationSnippets alloc() {
        return ImageSingletons.lookup(SubstrateAllocationSnippets.class);
    }

    public static class Templates extends SubstrateTemplates {
        private final SubstrateAllocationSnippets.Templates baseTemplates;
        private final SnippetInfo formatObject;
        private final SnippetInfo formatArray;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, Providers providers, SubstrateAllocationSnippets.Templates baseTemplates) {
            super(options, providers);
            this.baseTemplates = baseTemplates;
            formatObject = snippet(providers, WasmLMAllocationSnippets.class, "formatObjectSnippet");
            formatArray = snippet(providers, WasmLMAllocationSnippets.class, "formatArraySnippet");
        }

        public void registerLowering(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
            lowerings.put(FormatObjectNode.class, new FormatObjectLowering());
            lowerings.put(FormatArrayNode.class, new FormatArrayLowering());
        }

        private final class FormatObjectLowering implements NodeLoweringProvider<FormatObjectNode> {
            @Override
            public void lower(FormatObjectNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
                    return;
                }
                Arguments args = new Arguments(formatObject, graph, tool.getLoweringStage());
                args.add("memory", node.getMemory());
                args.add("hub", node.getHub());
                args.add("rememberedSet", node.getRememberedSet());
                args.add("fillContents", node.getFillContents());
                args.add("emitMemoryBarrier", node.getEmitMemoryBarrier());
                args.add("snippetCounters", baseTemplates.getSnippetCounters());
                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private final class FormatArrayLowering implements NodeLoweringProvider<FormatArrayNode> {
            @Override
            public void lower(FormatArrayNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
                    return;
                }
                Arguments args = new Arguments(formatArray, graph, tool.getLoweringStage());
                args.add("memory", node.getMemory());
                args.add("hub", node.getHub());
                args.add("length", node.getLength());
                args.add("rememberedSet", node.getRememberedSet());
                args.add("unaligned", node.getUnaligned());
                args.add("fillContents", node.getFillContents());
                args.add("emitMemoryBarrier", node.getEmitMemoryBarrier());
                args.add("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroingOfEden());
                args.add("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.add("snippetCounters", baseTemplates.getSnippetCounters());
                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }
    }
}
