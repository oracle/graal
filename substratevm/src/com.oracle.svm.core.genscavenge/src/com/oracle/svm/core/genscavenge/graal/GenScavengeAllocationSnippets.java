/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
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
import org.graalvm.compiler.word.Word;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.genscavenge.HeapPolicy;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.ThreadLocalAllocation;
import com.oracle.svm.core.genscavenge.ThreadLocalAllocation.Descriptor;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;

final class GenScavengeAllocationSnippets extends SubstrateAllocationSnippets {
    private static final SubstrateForeignCallDescriptor SLOW_NEW_INSTANCE = SnippetRuntime.findForeignCall(ThreadLocalAllocation.class, "slowPathNewInstance", true);
    private static final SubstrateForeignCallDescriptor SLOW_NEW_ARRAY = SnippetRuntime.findForeignCall(ThreadLocalAllocation.class, "slowPathNewArray", true);
    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{SLOW_NEW_INSTANCE, SLOW_NEW_ARRAY};

    public static void registerForeignCalls(Providers providers, SubstrateForeignCallsProvider foreignCalls) {
        SubstrateAllocationSnippets.registerForeignCalls(providers, foreignCalls);
        foreignCalls.register(providers, FOREIGN_CALLS);
    }

    public static void registerLowering(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        GenScavengeAllocationSnippets snippetReceiver = new GenScavengeAllocationSnippets();
        GenScavengeAllocationSnippets.Templates allocationSnippets = new GenScavengeAllocationSnippets.Templates(
                        snippetReceiver, options, factories, SnippetCounter.Group.NullFactory, providers, snippetReflection);
        allocationSnippets.registerLowerings(lowerings);
    }

    @Snippet
    public Object formatObjectSnippet(Word memory, DynamicHub hub, boolean rememberedSet, boolean fillContents, boolean emitMemoryBarrier,
                    @ConstantParameter AllocationSnippetCounters snippetCounters) {
        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        int layoutEncoding = hubNonNull.getLayoutEncoding();
        UnsignedWord size = LayoutEncoding.getInstanceSize(layoutEncoding);
        Word objectHeader = encodeAsObjectHeader(hubNonNull, rememberedSet, false);
        return formatObject(objectHeader, WordFactory.nullPointer(), size, memory, fillContents, emitMemoryBarrier, false, snippetCounters);
    }

    @Snippet
    public Object formatArraySnippet(Word memory, DynamicHub hub, int length, boolean rememberedSet, boolean unaligned, boolean fillContents, boolean emitMemoryBarrier,
                    @ConstantParameter boolean supportsBulkZeroing, @ConstantParameter AllocationSnippetCounters snippetCounters) {
        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        int layoutEncoding = hubNonNull.getLayoutEncoding();
        UnsignedWord size = LayoutEncoding.getArraySize(layoutEncoding, length);
        int fillStartOffset = (int) LayoutEncoding.getArrayBaseOffset(layoutEncoding).rawValue();
        Word objectHeader = encodeAsObjectHeader(hubNonNull, rememberedSet, unaligned);
        return formatArray(objectHeader, WordFactory.nullPointer(), size, length, memory, fillContents, fillStartOffset,
                        emitMemoryBarrier, false, supportsBulkZeroing, snippetCounters);
    }

    private static Word encodeAsObjectHeader(DynamicHub hub, boolean rememberedSet, boolean unaligned) {
        return ObjectHeaderImpl.encodeAsObjectHeader(hub, rememberedSet, unaligned);
    }

    @Override
    protected void initializeObjectHeader(Word memory, Word objectHeader, Word prototypeMarkWord, boolean isArray) {
        ObjectHeaderImpl.initializeHeaderOfNewObject(memory, objectHeader, isArray);
    }

    @Override
    protected boolean useTLAB() {
        return true;
    }

    @Override
    protected boolean shouldAllocateInTLAB(UnsignedWord size, boolean isArray) {
        return !isArray || size.belowThan(HeapPolicy.getLargeArrayThreshold());
    }

    @Override
    protected Word getTLABInfo() {
        return (Word) ThreadLocalAllocation.regularTLAB.getAddress();
    }

    @Override
    protected Word readTlabTop(Word tlabInfo) {
        return ((Descriptor) tlabInfo).getAllocationTop(TLAB_TOP_IDENTITY);
    }

    @Override
    protected Word readTlabEnd(Word tlabInfo) {
        return ((Descriptor) tlabInfo).getAllocationEnd(TLAB_END_IDENTITY);
    }

    @Override
    protected void writeTlabTop(Word tlabInfo, Word newTop) {
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

        Templates(GenScavengeAllocationSnippets receiver, OptionValues options, Iterable<DebugHandlersFactory> factories,
                        SnippetCounter.Group.Factory groupFactory, Providers providers, SnippetReflectionProvider snippetReflection) {
            super(receiver, options, factories, groupFactory, providers, snippetReflection);

            formatObject = snippet(GenScavengeAllocationSnippets.class, "formatObjectSnippet", null, receiver);
            formatArray = snippet(GenScavengeAllocationSnippets.class, "formatArraySnippet", null, receiver);
        }

        @Override
        public void registerLowerings(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
            super.registerLowerings(lowerings);

            FormatObjectLowering formatObjectLowering = new FormatObjectLowering();
            lowerings.put(FormatObjectNode.class, formatObjectLowering);

            FormatArrayLowering formatArrayLowering = new FormatArrayLowering();
            lowerings.put(FormatArrayNode.class, formatArrayLowering);
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
                args.add("emitMemoryBarrier", node.getEmitMemoryBarrier());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("snippetCounters", snippetCounters);
                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }
    }
}
