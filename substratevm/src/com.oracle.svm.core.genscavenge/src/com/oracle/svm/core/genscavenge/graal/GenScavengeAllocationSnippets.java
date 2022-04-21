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

import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.genscavenge.HeapParameters;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.ThreadLocalAllocation;
import com.oracle.svm.core.genscavenge.ThreadLocalAllocation.Descriptor;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.nodes.NewStoredContinuationNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.StoredContinuationImpl;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.thread.Continuation;

final class GenScavengeAllocationSnippets extends SubstrateAllocationSnippets {
    private static final SubstrateForeignCallDescriptor SLOW_NEW_INSTANCE = SnippetRuntime.findForeignCall(ThreadLocalAllocation.class, "slowPathNewInstance", true);
    private static final SubstrateForeignCallDescriptor SLOW_NEW_ARRAY = SnippetRuntime.findForeignCall(ThreadLocalAllocation.class, "slowPathNewArray", true);
    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{SLOW_NEW_INSTANCE, SLOW_NEW_ARRAY};

    public static void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        SubstrateAllocationSnippets.registerForeignCalls(foreignCalls);
        foreignCalls.register(FOREIGN_CALLS);
    }

    public static void registerLowering(OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        SubstrateAllocationSnippets snippetReceiver = ImageSingletons.lookup(SubstrateAllocationSnippets.class);
        GenScavengeAllocationSnippets.Templates allocationSnippets = new GenScavengeAllocationSnippets.Templates(
                        snippetReceiver, options, SnippetCounter.Group.NullFactory, providers);
        allocationSnippets.registerLowerings(lowerings);
    }

    @Snippet
    public Object formatObjectSnippet(Word memory, DynamicHub hub, boolean rememberedSet, FillContent fillContents, boolean emitMemoryBarrier,
                    @ConstantParameter AllocationSnippetCounters snippetCounters) {
        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        int layoutEncoding = hubNonNull.getLayoutEncoding();
        UnsignedWord size = LayoutEncoding.getInstanceSize(layoutEncoding);
        Word objectHeader = encodeAsObjectHeader(hubNonNull, rememberedSet, false);
        return formatObject(objectHeader, size, memory, fillContents, emitMemoryBarrier, false, snippetCounters);
    }

    @Snippet
    public Object formatArraySnippet(Word memory, DynamicHub hub, int length, boolean rememberedSet, boolean unaligned, FillContent fillContents, int fillStartOffset, boolean emitMemoryBarrier,
                    @ConstantParameter boolean supportsBulkZeroing, @ConstantParameter boolean supportsOptimizedFilling, @ConstantParameter AllocationSnippetCounters snippetCounters) {
        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        int layoutEncoding = hubNonNull.getLayoutEncoding();
        UnsignedWord size = LayoutEncoding.getArraySize(layoutEncoding, length);
        Word objectHeader = encodeAsObjectHeader(hubNonNull, rememberedSet, unaligned);
        Object obj = formatArray(objectHeader, size, length, memory, fillContents, fillStartOffset,
                        false, false, supportsBulkZeroing, supportsOptimizedFilling, snippetCounters);
        emitMemoryBarrierIf(emitMemoryBarrier);
        return obj;
    }

    private static Word encodeAsObjectHeader(DynamicHub hub, boolean rememberedSet, boolean unaligned) {
        return ObjectHeaderImpl.encodeAsObjectHeader(hub, rememberedSet, unaligned);
    }

    @Snippet
    public Object allocateStoredContinuationInstance(@ConstantParameter DynamicHub hub, int payloadSize, @ConstantParameter DynamicHub byteArrayHub,
                    @ConstantParameter int byteArrayBaseOffset, @ConstantParameter AllocationProfilingData profilingData) {
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
        Object result = allocateArrayImpl(encodeAsTLABObjectHeader(byteArrayHub), arrayLength, byteArrayBaseOffset, 0, FillContent.WITH_GARBAGE_IF_ASSERTIONS_ENABLED,
                        afterArrayLengthOffset(), false, false, false, false, profilingData);
        UnsignedWord arrayHeader = ObjectHeaderImpl.readHeaderFromObject(result);
        Word header = encodeAsObjectHeader(hub, ObjectHeaderImpl.hasRememberedSet(arrayHeader), ObjectHeaderImpl.isUnalignedHeader(arrayHeader));
        initializeObjectHeader(Word.objectToUntrackedPointer(result), header, false);
        ObjectAccess.writeObject(result, hub.getMonitorOffset(), null, LocationIdentity.init());
        StoredContinuationImpl.initializeNewlyAllocated(result, payloadSize);
        emitMemoryBarrierIf(true);
        return PiNode.piCastToSnippetReplaceeStamp(result);
    }

    @Override
    public void initializeObjectHeader(Word memory, Word objectHeader, boolean isArray) {
        Heap.getHeap().getObjectHeader().initializeHeaderOfNewObject(memory, objectHeader);
    }

    @Override
    public boolean useTLAB() {
        return true;
    }

    @Override
    protected boolean shouldAllocateInTLAB(UnsignedWord size, boolean isArray) {
        return !isArray || size.belowThan(HeapParameters.getLargeArrayThreshold());
    }

    @Override
    public Word getTLABInfo() {
        return ThreadLocalAllocation.getTlabAddress();
    }

    @Override
    public Word readTlabTop(Word tlabInfo) {
        return ((Descriptor) tlabInfo).getAllocationTop(TLAB_TOP_IDENTITY);
    }

    @Override
    public Word readTlabEnd(Word tlabInfo) {
        return ((Descriptor) tlabInfo).getAllocationEnd(TLAB_END_IDENTITY);
    }

    @Override
    public void writeTlabTop(Word tlabInfo, Word newTop) {
        ((Descriptor) tlabInfo).setAllocationTop(newTop, TLAB_TOP_IDENTITY);
    }

    @Override
    protected SubstrateForeignCallDescriptor getSlowNewInstanceStub() {
        return SLOW_NEW_INSTANCE;
    }

    @Override
    protected SubstrateForeignCallDescriptor getSlowNewArrayStub() {
        return SLOW_NEW_ARRAY;
    }

    public static class Templates extends SubstrateAllocationSnippets.Templates {
        private final SnippetInfo formatObject;
        private final SnippetInfo formatArray;
        private final SnippetInfo allocateStoredContinuationInstance;

        Templates(SubstrateAllocationSnippets receiver, OptionValues options, SnippetCounter.Group.Factory groupFactory, Providers providers) {
            super(receiver, options, groupFactory, providers);

            formatObject = snippet(GenScavengeAllocationSnippets.class, "formatObjectSnippet", null, receiver);
            formatArray = snippet(GenScavengeAllocationSnippets.class, "formatArraySnippet", null, receiver);

            allocateStoredContinuationInstance = !Continuation.isSupported() ? null
                            : snippet(GenScavengeAllocationSnippets.class, "allocateStoredContinuationInstance", null, receiver, ALLOCATION_LOCATIONS);
        }

        @Override
        public void registerLowerings(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
            super.registerLowerings(lowerings);

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
                args.addConst("snippetCounters", snippetCounters);
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
                args.addConst("snippetCounters", snippetCounters);
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
                int byteArrayBaseOffset = getArrayBaseOffset(byteArrayHub.getLayoutEncoding());

                Arguments args = new Arguments(allocateStoredContinuationInstance, graph.getGuardsStage(), tool.getLoweringStage());
                args.addConst("hub", hubConstant);
                args.add("payloadSize", node.getPayloadSize());
                args.addConst("byteArrayHub", byteArrayHubConstant);
                args.addConst("byteArrayBaseOffset", byteArrayBaseOffset);
                args.addConst("profilingData", getProfilingData(node, null));

                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }
    }
}
