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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.AllocationSnippets;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.graal.nodes.NewStoredContinuationNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationImpl;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.thread.Continuation;

final class GenScavengeAllocationSnippets implements Snippets {
    @Snippet
    public static Object formatObjectSnippet(Word memory, DynamicHub hub, boolean rememberedSet, AllocationSnippets.FillContent fillContents, boolean emitMemoryBarrier,
                    @ConstantParameter AllocationSnippets.AllocationSnippetCounters snippetCounters) {
        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        int layoutEncoding = hubNonNull.getLayoutEncoding();
        UnsignedWord size = LayoutEncoding.getInstanceSize(layoutEncoding);
        Word objectHeader = encodeAsObjectHeader(hubNonNull, rememberedSet, false);
        return alloc().formatObject(objectHeader, size, memory, fillContents, emitMemoryBarrier, false, snippetCounters);
    }

    @Snippet
    public static Object formatArraySnippet(Word memory, DynamicHub hub, int length, boolean rememberedSet, boolean unaligned, AllocationSnippets.FillContent fillContents, int fillStartOffset,
                    boolean emitMemoryBarrier, @ConstantParameter boolean supportsBulkZeroing, @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter AllocationSnippets.AllocationSnippetCounters snippetCounters) {
        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        int layoutEncoding = hubNonNull.getLayoutEncoding();
        UnsignedWord size = LayoutEncoding.getArraySize(layoutEncoding, length);
        Word objectHeader = encodeAsObjectHeader(hubNonNull, rememberedSet, unaligned);
        Object obj = alloc().formatArray(objectHeader, size, length, memory, fillContents, fillStartOffset,
                        false, false, supportsBulkZeroing, supportsOptimizedFilling, snippetCounters);
        alloc().emitMemoryBarrierIf(emitMemoryBarrier);
        return obj;
    }

    @Snippet
    public static Object allocateStoredContinuationInstance(@ConstantParameter DynamicHub hub, int payloadSize, @ConstantParameter DynamicHub byteArrayHub,
                    @ConstantParameter int byteArrayBaseOffset, @ConstantParameter AllocationSnippets.AllocationProfilingData profilingData) {
        /*
         * We allocate a byte[] first and then convert it to a StoredContinuation. We must pass
         * parameters below that match the layout of a regular byte[], or we will run into problems
         * because not all parameters are passed on to the slow path.
         *
         * Barrier code assumes that instance objects are always in aligned chunks, but a large
         * StoredContinuation can end up in an unaligned chunk. Still, no barriers are needed
         * because objects are immutable once filled and are then written only by GC.
         */
        int arrayLength = StoredContinuationImpl.PAYLOAD_OFFSET + payloadSize - byteArrayBaseOffset;
        Object result = alloc().allocateArrayImpl(SubstrateAllocationSnippets.encodeAsTLABObjectHeader(byteArrayHub), arrayLength, byteArrayBaseOffset, 0,
                        AllocationSnippets.FillContent.WITH_GARBAGE_IF_ASSERTIONS_ENABLED,
                        SubstrateAllocationSnippets.afterArrayLengthOffset(), false, false, false, false, profilingData);
        UnsignedWord arrayHeader = ObjectHeaderImpl.readHeaderFromObject(result);
        Word header = encodeAsObjectHeader(hub, ObjectHeaderImpl.hasRememberedSet(arrayHeader), ObjectHeaderImpl.isUnalignedHeader(arrayHeader));
        alloc().initializeObjectHeader(Word.objectToUntrackedPointer(result), header, false);
        ObjectAccess.writeObject(result, hub.getMonitorOffset(), null, LocationIdentity.init());
        StoredContinuationImpl.initializeNewlyAllocated(result, payloadSize);
        alloc().emitMemoryBarrierIf(true);
        return PiNode.piCastToSnippetReplaceeStamp(result);
    }

    private static Word encodeAsObjectHeader(DynamicHub hub, boolean rememberedSet, boolean unaligned) {
        return ObjectHeaderImpl.encodeAsObjectHeader(hub, rememberedSet, unaligned);
    }

    @Fold
    static SubstrateAllocationSnippets alloc() {
        return ImageSingletons.lookup(SubstrateAllocationSnippets.class);
    }

    public static class Templates extends SubstrateTemplates {
        private final SubstrateAllocationSnippets.Templates baseTemplates;
        private final SnippetInfo formatObject;
        private final SnippetInfo formatArray;
        private final SnippetInfo allocateStoredContinuationInstance;

        Templates(OptionValues options, Providers providers, SubstrateAllocationSnippets.Templates baseTemplates) {
            super(options, providers);
            this.baseTemplates = baseTemplates;
            formatObject = snippet(GenScavengeAllocationSnippets.class, "formatObjectSnippet");
            formatArray = snippet(GenScavengeAllocationSnippets.class, "formatArraySnippet");

            allocateStoredContinuationInstance = !Continuation.isSupported() ? null
                            : snippet(GenScavengeAllocationSnippets.class, "allocateStoredContinuationInstance", SubstrateAllocationSnippets.ALLOCATION_LOCATIONS);
        }

        public void registerLowering(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
            FormatObjectLowering formatObjectLowering = new FormatObjectLowering();
            lowerings.put(FormatObjectNode.class, formatObjectLowering);

            FormatArrayLowering formatArrayLowering = new FormatArrayLowering();
            lowerings.put(FormatArrayNode.class, formatArrayLowering);

            if (Continuation.isSupported()) {
                NewStoredContinuationLowering newStoredContinuationLowering = new NewStoredContinuationLowering();
                lowerings.put(NewStoredContinuationNode.class, newStoredContinuationLowering);
            }
        }

        private class FormatObjectLowering implements NodeLoweringProvider<FormatObjectNode> {
            @Override
            public void lower(FormatObjectNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }
                Arguments args = new Arguments(formatObject, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("memory", node.getMemory());
                args.add("hub", node.getHub());
                args.add("rememberedSet", node.getRememberedSet());
                args.add("fillContents", node.getFillContents());
                args.add("emitMemoryBarrier", node.getEmitMemoryBarrier());
                args.addConst("snippetCounters", baseTemplates.getSnippetCounters());
                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class FormatArrayLowering implements NodeLoweringProvider<FormatArrayNode> {
            @Override
            public void lower(FormatArrayNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }
                Arguments args = new Arguments(formatArray, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("memory", node.getMemory());
                args.add("hub", node.getHub());
                args.add("length", node.getLength());
                args.add("rememberedSet", node.getRememberedSet());
                args.add("unaligned", node.getUnaligned());
                args.add("fillContents", node.getFillContents());
                args.add("fillStartOffset", node.getFillStartOffset());
                args.add("emitMemoryBarrier", node.getEmitMemoryBarrier());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("snippetCounters", baseTemplates.getSnippetCounters());
                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class NewStoredContinuationLowering implements NodeLoweringProvider<NewStoredContinuationNode> {
            @Override
            public void lower(NewStoredContinuationNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();

                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }

                DynamicHub hub = ((SharedType) tool.getMetaAccess().lookupJavaType(StoredContinuation.class)).getHub();
                assert hub.isStoredContinuationClass();
                ConstantNode hubConstant = ConstantNode.forConstant(SubstrateObjectConstant.forObject(hub), providers.getMetaAccess(), graph);

                DynamicHub byteArrayHub = ((SharedType) tool.getMetaAccess().lookupJavaType(byte[].class)).getHub();
                ConstantNode byteArrayHubConstant = ConstantNode.forConstant(SubstrateObjectConstant.forObject(byteArrayHub), providers.getMetaAccess(), graph);
                int byteArrayBaseOffset = NumUtil.safeToInt(LayoutEncoding.getArrayBaseOffset(byteArrayHub.getLayoutEncoding()).rawValue());

                Arguments args = new Arguments(allocateStoredContinuationInstance, graph.getGuardsStage(), tool.getLoweringStage());
                args.addConst("hub", hubConstant);
                args.add("payloadSize", node.getPayloadSize());
                args.addConst("byteArrayHub", byteArrayHubConstant);
                args.addConst("byteArrayBaseOffset", byteArrayBaseOffset);
                args.addConst("profilingData", baseTemplates.getProfilingData(node, null));

                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }
    }
}
