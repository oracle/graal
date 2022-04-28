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

import static org.graalvm.compiler.nodes.PiArrayNode.piArrayCastToSnippetReplaceeStamp;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.util.Arrays;
import java.util.Map;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.Snippet.NonNullParameter;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.ReplacementsUtil;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.HeapParameters;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.ThreadLocalAllocation;
import com.oracle.svm.core.genscavenge.ThreadLocalAllocation.Descriptor;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatPodNode;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.nodes.NewPodInstanceNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;

import jdk.vm.ci.meta.JavaKind;

final class GenScavengeAllocationSnippets extends SubstrateAllocationSnippets {
    private static final SubstrateForeignCallDescriptor SLOW_NEW_INSTANCE = SnippetRuntime.findForeignCall(ThreadLocalAllocation.class, "slowPathNewInstance", true);
    private static final SubstrateForeignCallDescriptor SLOW_NEW_ARRAY = SnippetRuntime.findForeignCall(ThreadLocalAllocation.class, "slowPathNewArray", true);
    private static final SubstrateForeignCallDescriptor SLOW_NEW_POD_INSTANCE = SnippetRuntime.findForeignCall(ThreadLocalAllocation.class, "slowPathNewPodInstance", true);
    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{SLOW_NEW_INSTANCE, SLOW_NEW_ARRAY, SLOW_NEW_POD_INSTANCE};

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
        return formatArray(objectHeader, size, length, memory, fillContents, fillStartOffset,
                        emitMemoryBarrier, false, supportsBulkZeroing, supportsOptimizedFilling, snippetCounters);
    }

    @Snippet
    public Object allocatePod(@NonNullParameter DynamicHub hub, int sizeWithoutRefMap, byte[] referenceMap, @ConstantParameter boolean emitMemoryBarrier, @ConstantParameter boolean maybeUnroll,
                    @ConstantParameter boolean supportsBulkZeroing, @ConstantParameter boolean supportsOptimizedFilling, @ConstantParameter AllocationProfilingData profilingData) {

        Word thread = getTLABInfo();
        Word top = readTlabTop(thread);
        Word end = readTlabEnd(thread);
        ReplacementsUtil.dynamicAssert(end.subtract(top).belowOrEqual(Integer.MAX_VALUE), "TLAB is too large");

        int arrayBaseOffset = LayoutEncoding.getArrayBaseOffsetAsInt(hub.getLayoutEncoding());
        UnsignedWord allocationSize = arrayAllocationSize(sizeWithoutRefMap, arrayBaseOffset, 0);
        Word newTop = top.add(allocationSize);

        Object instance;
        if (useTLAB() && probability(FAST_PATH_PROBABILITY, shouldAllocateInTLAB(allocationSize, true)) && probability(FAST_PATH_PROBABILITY, newTop.belowOrEqual(end))) {
            writeTlabTop(thread, newTop);
            emitPrefetchAllocate(newTop, true);
            instance = formatPod(top, hub, sizeWithoutRefMap, referenceMap, false, false, afterArrayLengthOffset(),
                            emitMemoryBarrier, maybeUnroll, supportsBulkZeroing, supportsOptimizedFilling, profilingData.snippetCounters);
        } else {
            profilingData.snippetCounters.stub.inc();
            instance = callSlowNewPodInstance(SLOW_NEW_POD_INSTANCE, encodeAsTLABObjectHeader(hub), sizeWithoutRefMap, afterArrayLengthOffset(), referenceMap);
        }
        profileAllocation(profilingData, allocationSize);
        return piArrayCastToSnippetReplaceeStamp(verifyOop(instance), sizeWithoutRefMap);
    }

    @Snippet
    public Object formatPodSnippet(Word memory, DynamicHub hub, int sizeWithoutRefMap, byte[] referenceMap, boolean rememberedSet, boolean unaligned, int fillStartOffset, boolean emitMemoryBarrier,
                    @ConstantParameter boolean supportsBulkZeroing, @ConstantParameter boolean supportsOptimizedFilling, @ConstantParameter AllocationSnippetCounters snippetCounters) {

        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        byte[] refMapNonNull = (byte[]) PiNode.piCastNonNull(referenceMap, SnippetAnchorNode.anchor());
        return formatPod(memory, hubNonNull, sizeWithoutRefMap, refMapNonNull, rememberedSet, unaligned, fillStartOffset,
                        emitMemoryBarrier, false, supportsBulkZeroing, supportsOptimizedFilling, snippetCounters);
    }

    private Object formatPod(Word memory, DynamicHub hub, int sizeWithoutRefMap, byte[] referenceMap, boolean rememberedSet, boolean unaligned, int fillStartOffset,
                    boolean emitMemoryBarrier, boolean maybeUnroll, boolean supportsBulkZeroing, boolean supportsOptimizedFilling, AllocationSnippetCounters snippetCounters) {

        int layoutEncoding = hub.getLayoutEncoding();
        UnsignedWord allocationSize = LayoutEncoding.getArraySize(layoutEncoding, sizeWithoutRefMap);

        Word objectHeader = encodeAsObjectHeader(hub, rememberedSet, unaligned);
        Object instance = formatArray(objectHeader, allocationSize, sizeWithoutRefMap, memory, FillContent.WITH_ZEROES, fillStartOffset,
                        false, maybeUnroll, supportsBulkZeroing, supportsOptimizedFilling, snippetCounters);

        int fromOffset = ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Byte);
        int toOffset = LayoutEncoding.getArrayBaseOffsetAsInt(layoutEncoding) + sizeWithoutRefMap - referenceMap.length;
        for (int i = 0; i < referenceMap.length; i++) {
            byte b = ObjectAccess.readByte(referenceMap, fromOffset + i, byteArrayIdentity());
            ObjectAccess.writeByte(instance, toOffset + i, b, LocationIdentity.INIT_LOCATION);
        }
        emitMemoryBarrierIf(emitMemoryBarrier);
        return instance;
    }

    @Fold
    static LocationIdentity byteArrayIdentity() {
        return NamedLocationIdentity.getArrayLocation(JavaKind.Byte);
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callSlowNewPodInstance(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub, int length, int fillStartOffset, byte[] referenceMap);

    private static Word encodeAsObjectHeader(DynamicHub hub, boolean rememberedSet, boolean unaligned) {
        return ObjectHeaderImpl.encodeAsObjectHeader(hub, rememberedSet, unaligned);
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
        private final SnippetInfo formatPod;
        private final SnippetInfo allocatePod;

        Templates(SubstrateAllocationSnippets receiver, OptionValues options, SnippetCounter.Group.Factory groupFactory, Providers providers) {
            super(receiver, options, groupFactory, providers);

            formatObject = snippet(GenScavengeAllocationSnippets.class, "formatObjectSnippet", null, receiver);
            formatArray = snippet(GenScavengeAllocationSnippets.class, "formatArraySnippet", null, receiver);

            Object[] podAllocLocations = Arrays.copyOf(ALLOCATION_LOCATIONS, ALLOCATION_LOCATIONS.length + 1);
            podAllocLocations[podAllocLocations.length - 1] = byteArrayIdentity();
            allocatePod = snippet(GenScavengeAllocationSnippets.class, "allocatePod", null, receiver, podAllocLocations);
            formatPod = snippet(GenScavengeAllocationSnippets.class, "formatPodSnippet", null, receiver, byteArrayIdentity());
        }

        @Override
        public void registerLowerings(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
            super.registerLowerings(lowerings);

            lowerings.put(FormatObjectNode.class, new FormatObjectLowering());
            lowerings.put(FormatArrayNode.class, new FormatArrayLowering());
            lowerings.put(FormatPodNode.class, new FormatPodLowering());
            lowerings.put(NewPodInstanceNode.class, new NewPodInstanceLowering());
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

        private class NewPodInstanceLowering implements NodeLoweringProvider<NewPodInstanceNode> {
            @Override
            public void lower(NewPodInstanceNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }

                assert node.getKnownInstanceType() == null || (node.getHub().isConstant() &&
                                providers.getConstantReflection().asJavaType(node.getHub().asConstant()).equals(node.getKnownInstanceType()));
                assert node.fillContents() : "fillContents must be true for hybrid allocations";

                Arguments args = new Arguments(allocatePod, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", node.getHub());
                args.add("sizeWithoutRefMap", node.getSizeWithoutRefMap());
                args.add("referenceMap", node.getReferenceMap());
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("maybeUnroll", node.getSizeWithoutRefMap().isConstant());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("profilingData", getProfilingData(node, node.getKnownInstanceType()));

                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class FormatPodLowering implements NodeLoweringProvider<FormatPodNode> {
            @Override
            public void lower(FormatPodNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }
                Arguments args = new Arguments(formatPod, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("memory", node.getMemory());
                args.add("hub", node.getHub());
                args.add("sizeWithoutRefMap", node.getSizeWithoutRefMap());
                args.add("referenceMap", node.getReferenceMap());
                args.add("rememberedSet", node.getRememberedSet());
                args.add("unaligned", node.getUnaligned());
                args.add("fillStartOffset", node.getFillStartOffset());
                args.add("emitMemoryBarrier", node.getEmitMemoryBarrier());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("snippetCounters", snippetCounters);
                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }
    }
}
