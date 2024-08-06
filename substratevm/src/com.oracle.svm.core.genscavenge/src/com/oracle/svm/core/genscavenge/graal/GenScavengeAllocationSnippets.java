/*
 * Copyright (c) 2019, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.graal;

import java.util.Map;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
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
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatPodNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatStoredContinuationNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.thread.ContinuationSupport;

import jdk.vm.ci.meta.JavaKind;

public final class GenScavengeAllocationSnippets implements Snippets {
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

    @Snippet
    public static Object formatStoredContinuation(Word memory, DynamicHub hub, int length, boolean rememberedSet, boolean unaligned, @ConstantParameter long ipOffset,
                    @ConstantParameter boolean emitMemoryBarrier, @ConstantParameter AllocationSnippets.AllocationSnippetCounters snippetCounters) {
        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        int layoutEncoding = hubNonNull.getLayoutEncoding();
        UnsignedWord size = LayoutEncoding.getArrayAllocationSize(layoutEncoding, length);
        Word objectHeader = encodeAsObjectHeader(hubNonNull, rememberedSet, unaligned);
        return alloc().formatStoredContinuation(objectHeader, size, length, memory, emitMemoryBarrier, ipOffset, snippetCounters);
    }

    @Snippet
    public static Object formatPodSnippet(Word memory, DynamicHub hub, int arrayLength, byte[] referenceMap, boolean rememberedSet, boolean unaligned, AllocationSnippets.FillContent fillContents,
                    @ConstantParameter boolean emitMemoryBarrier, @ConstantParameter boolean supportsBulkZeroing, @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter AllocationSnippets.AllocationSnippetCounters snippetCounters) {
        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        byte[] refMapNonNull = (byte[]) PiNode.piCastNonNull(referenceMap, SnippetAnchorNode.anchor());
        Word objectHeader = encodeAsObjectHeader(hubNonNull, rememberedSet, unaligned);
        int layoutEncoding = hubNonNull.getLayoutEncoding();
        UnsignedWord allocationSize = LayoutEncoding.getArrayAllocationSize(layoutEncoding, arrayLength);
        return alloc().formatPod(objectHeader, hubNonNull, allocationSize, arrayLength, refMapNonNull, memory, fillContents, emitMemoryBarrier, false, supportsBulkZeroing, supportsOptimizedFilling,
                        snippetCounters);
    }

    private static Word encodeAsObjectHeader(DynamicHub hub, boolean rememberedSet, boolean unaligned) {
        return ObjectHeaderImpl.getObjectHeaderImpl().encodeAsObjectHeader(hub, rememberedSet, unaligned);
    }

    @Fold
    static SubstrateAllocationSnippets alloc() {
        return ImageSingletons.lookup(SubstrateAllocationSnippets.class);
    }

    public static class Templates extends SubstrateTemplates {
        private final SubstrateAllocationSnippets.Templates baseTemplates;
        private final SnippetInfo formatObject;
        private final SnippetInfo formatArray;
        private final SnippetInfo formatStoredContinuation;
        private final SnippetInfo formatPod;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, Providers providers, SubstrateAllocationSnippets.Templates baseTemplates) {
            super(options, providers);
            this.baseTemplates = baseTemplates;
            formatObject = snippet(providers, GenScavengeAllocationSnippets.class, "formatObjectSnippet");
            formatArray = snippet(providers, GenScavengeAllocationSnippets.class, "formatArraySnippet");
            formatStoredContinuation = ContinuationSupport.isSupported() ? snippet(providers, GenScavengeAllocationSnippets.class, "formatStoredContinuation") : null;
            formatPod = Pod.RuntimeSupport.isPresent() ? snippet(providers,
                            GenScavengeAllocationSnippets.class,
                            "formatPodSnippet",
                            NamedLocationIdentity.getArrayLocation(JavaKind.Byte))
                            : null;
        }

        public void registerLowering(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
            lowerings.put(FormatObjectNode.class, new FormatObjectLowering());
            lowerings.put(FormatArrayNode.class, new FormatArrayLowering());
            if (ContinuationSupport.isSupported()) {
                lowerings.put(FormatStoredContinuationNode.class, new FormatStoredContinuationLowering());
            }
            if (Pod.RuntimeSupport.isPresent()) {
                lowerings.put(FormatPodNode.class, new FormatPodLowering());
            }
        }

        private class FormatObjectLowering implements NodeLoweringProvider<FormatObjectNode> {
            @Override
            public void lower(FormatObjectNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }
                Arguments args = new Arguments(formatObject, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("memory", node.getMemory());
                args.add("hub", node.getHub());
                args.add("rememberedSet", node.getRememberedSet());
                args.add("fillContents", node.getFillContents());
                args.add("emitMemoryBarrier", node.getEmitMemoryBarrier());
                args.addConst("snippetCounters", baseTemplates.getSnippetCounters());
                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class FormatArrayLowering implements NodeLoweringProvider<FormatArrayNode> {
            @Override
            public void lower(FormatArrayNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }
                Arguments args = new Arguments(formatArray, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("memory", node.getMemory());
                args.add("hub", node.getHub());
                args.add("length", node.getLength());
                args.add("rememberedSet", node.getRememberedSet());
                args.add("unaligned", node.getUnaligned());
                args.add("fillContents", node.getFillContents());
                args.add("emitMemoryBarrier", node.getEmitMemoryBarrier());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("snippetCounters", baseTemplates.getSnippetCounters());
                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class FormatStoredContinuationLowering implements NodeLoweringProvider<FormatStoredContinuationNode> {
            @Override
            public void lower(FormatStoredContinuationNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }
                Arguments args = new Arguments(formatStoredContinuation, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("memory", node.getMemory());
                args.add("hub", node.getHub());
                args.add("length", node.getLength());
                args.add("rememberedSet", node.getRememberedSet());
                args.add("unaligned", node.getUnaligned());
                args.addConst("ipOffset", ContinuationSupport.singleton().getIPOffset());
                args.addConst("emitMemoryBarrier", node.getEmitMemoryBarrier());
                args.addConst("snippetCounters", baseTemplates.getSnippetCounters());
                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class FormatPodLowering implements NodeLoweringProvider<FormatPodNode> {
            @Override
            public void lower(FormatPodNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }
                Arguments args = new Arguments(formatPod, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("memory", node.getMemory());
                args.add("hub", node.getHub());
                args.add("arrayLength", node.getArrayLength());
                args.add("referenceMap", node.getReferenceMap());
                args.add("rememberedSet", node.getRememberedSet());
                args.add("unaligned", node.getUnaligned());
                args.add("fillContents", node.getFillContents());
                args.addConst("emitMemoryBarrier", node.getEmitMemoryBarrier());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("snippetCounters", baseTemplates.getSnippetCounters());
                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }
    }
}
