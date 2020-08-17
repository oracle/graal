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
package com.oracle.svm.core.graal.snippets;

import static org.graalvm.compiler.nodes.PiArrayNode.piArrayCastToSnippetReplaceeStamp;
import static org.graalvm.compiler.nodes.PiNode.piCastToSnippetReplaceeStamp;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.LUDICROUSLY_FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;
import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.Snippet.VarargsParameter;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.java.DynamicNewArrayNode;
import org.graalvm.compiler.nodes.java.DynamicNewInstanceNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.AllocationSnippets;
import org.graalvm.compiler.replacements.SnippetCounter;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.allocationprofile.AllocationCounter;
import com.oracle.svm.core.allocationprofile.AllocationSite;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.nodes.UnreachableNode;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class SubstrateAllocationSnippets extends AllocationSnippets {
    public static final LocationIdentity TLAB_TOP_IDENTITY = NamedLocationIdentity.mutable("TLAB.top");
    public static final LocationIdentity TLAB_END_IDENTITY = NamedLocationIdentity.mutable("TLAB.end");
    public static final Object[] ALLOCATION_LOCATION_IDENTITIES = new Object[]{TLAB_TOP_IDENTITY, TLAB_END_IDENTITY, AllocationCounter.COUNT_FIELD, AllocationCounter.SIZE_FIELD};

    private static final SubstrateForeignCallDescriptor NEW_MULTI_ARRAY = SnippetRuntime.findForeignCall(SubstrateAllocationSnippets.class, "newMultiArrayStub", true);
    private static final SubstrateForeignCallDescriptor HUB_ERROR = SnippetRuntime.findForeignCall(SubstrateAllocationSnippets.class, "hubErrorStub", true);
    private static final SubstrateForeignCallDescriptor ARRAY_HUB_ERROR = SnippetRuntime.findForeignCall(SubstrateAllocationSnippets.class, "arrayHubErrorStub", true);
    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{NEW_MULTI_ARRAY, HUB_ERROR, ARRAY_HUB_ERROR};

    private static final String RUNTIME_REFLECTION_TYPE_NAME = RuntimeReflection.class.getTypeName();

    public static void registerForeignCalls(Providers providers, SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(providers, FOREIGN_CALLS);
    }

    @Snippet
    protected Object allocateInstance(DynamicHub hub,
                    @ConstantParameter long size,
                    @ConstantParameter boolean fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter AllocationProfilingData profilingData) {
        DynamicHub checkedHub = checkHub(hub);
        Object result = allocateInstanceImpl(encodeAsTLABObjectHeader(checkedHub), WordFactory.nullPointer(), WordFactory.unsigned(size), fillContents, emitMemoryBarrier, true, profilingData);
        return piCastToSnippetReplaceeStamp(result);
    }

    @Snippet
    public Object allocateArray(DynamicHub hub,
                    int length,
                    @ConstantParameter int arrayBaseOffset,
                    @ConstantParameter int log2ElementSize,
                    @ConstantParameter boolean fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean maybeUnroll,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter AllocationProfilingData profilingData) {
        DynamicHub checkedHub = checkHub(hub);
        Object result = allocateArrayImpl(encodeAsTLABObjectHeader(checkedHub), WordFactory.nullPointer(), length, arrayBaseOffset, log2ElementSize, fillContents,
                        emitMemoryBarrier, maybeUnroll, supportsBulkZeroing, profilingData);
        return piArrayCastToSnippetReplaceeStamp(result, length);
    }

    @Snippet
    public Object allocateInstanceDynamic(DynamicHub hub, @ConstantParameter boolean fillContents, @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter AllocationProfilingData profilingData) {
        DynamicHub checkedHub = checkInstanceDynamicHub(hub);
        UnsignedWord size = LayoutEncoding.getInstanceSize(checkedHub.getLayoutEncoding());
        Object result = allocateInstanceImpl(encodeAsTLABObjectHeader(checkedHub), WordFactory.nullPointer(), size, fillContents, emitMemoryBarrier, false, profilingData);
        return piCastToSnippetReplaceeStamp(result);
    }

    @Snippet
    public Object allocateArrayDynamic(DynamicHub elementType, int length, @ConstantParameter boolean fillContents, @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean supportsBulkZeroing, @ConstantParameter AllocationProfilingData profilingData) {
        DynamicHub checkedArrayHub = getCheckedArrayHub(elementType);

        int layoutEncoding = checkedArrayHub.getLayoutEncoding();
        int arrayBaseOffset = getArrayBaseOffset(layoutEncoding);
        int log2ElementSize = LayoutEncoding.getArrayIndexShift(layoutEncoding);

        Object result = allocateArrayImpl(encodeAsTLABObjectHeader(checkedArrayHub), WordFactory.nullPointer(), length, arrayBaseOffset, log2ElementSize, fillContents,
                        emitMemoryBarrier, false, supportsBulkZeroing, profilingData);
        return piArrayCastToSnippetReplaceeStamp(result, length);
    }

    @Snippet
    protected Object newmultiarray(DynamicHub hub, @ConstantParameter int rank, @VarargsParameter int[] dimensions) {
        return newMultiArrayImpl(Word.objectToUntrackedPointer(hub), rank, dimensions);
    }

    /** Foreign call: {@link #NEW_MULTI_ARRAY}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static Object newMultiArrayStub(Word dynamicHub, int rank, Word dimensionsStackValue) {
        /*
         * All dimensions must be checked here up front, since a previous dimension of length 0
         * stops allocation of inner dimensions.
         */
        for (int i = 0; i < rank; i++) {
            if (dimensionsStackValue.readInt(i * 4) < 0) {
                throw new NegativeArraySizeException();
            }
        }
        // newMultiArray does not have a fast path, so there is no need to encode the hub as an
        // object header.
        DynamicHub hub = KnownIntrinsics.convertUnknownValue(dynamicHub.toObject(), DynamicHub.class);
        return newMultiArrayRecursion(hub, rank, dimensionsStackValue);
    }

    private static Object newMultiArrayRecursion(DynamicHub hub, int rank, Word dimensionsStackValue) {
        int length = dimensionsStackValue.readInt(0);
        Object result = java.lang.reflect.Array.newInstance(DynamicHub.toClass(hub.getComponentHub()), length);

        if (rank > 1) {
            UnsignedWord offset = LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding());
            UnsignedWord endOffset = LayoutEncoding.getArrayElementOffset(hub.getLayoutEncoding(), length);

            while (offset.belowThan(endOffset)) {
                // Each newMultiArrayRecursion could create a cross-generational reference.
                BarrieredAccess.writeObject(result, offset,
                                newMultiArrayRecursion(hub.getComponentHub(), rank - 1, dimensionsStackValue.add(4)));
                offset = offset.add(ConfigurationValues.getObjectLayout().getReferenceSize());
            }
        }
        return result;
    }

    private static DynamicHub checkHub(DynamicHub hub) {
        if (probability(LUDICROUSLY_FAST_PATH_PROBABILITY, hub != null)) {
            DynamicHub nonNullHub = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
            if (probability(LUDICROUSLY_FAST_PATH_PROBABILITY, nonNullHub.isInstantiated())) {
                return nonNullHub;
            }
        }

        callHubErrorStub(HUB_ERROR, DynamicHub.toClass(hub));
        throw UnreachableNode.unreachable();
    }

    private static DynamicHub checkInstanceDynamicHub(DynamicHub hub) {
        if (probability(LUDICROUSLY_FAST_PATH_PROBABILITY, hub != null)) {
            DynamicHub nonNullHub = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
            if (probability(LUDICROUSLY_FAST_PATH_PROBABILITY, LayoutEncoding.isInstance(nonNullHub.getLayoutEncoding())) &&
                            probability(LUDICROUSLY_FAST_PATH_PROBABILITY, nonNullHub.isInstantiated())) {
                return nonNullHub;
            }
        }

        callHubErrorStub(HUB_ERROR, DynamicHub.toClass(hub));
        throw UnreachableNode.unreachable();
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callHubErrorStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> hub);

    /** Foreign call: {@link #HUB_ERROR}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void hubErrorStub(DynamicHub hub) throws InstantiationException {
        if (hub == null) {
            throw new NullPointerException("Allocation type is null.");
        } else if (!LayoutEncoding.isInstance(hub.getLayoutEncoding())) {
            throw new InstantiationException("Cannot allocate instance.");
        } else if (!hub.isInstantiated()) {
            throw new IllegalArgumentException("Class " + DynamicHub.toClass(hub).getTypeName() +
                            " is instantiated reflectively but was never registered. Register the class by using " + RUNTIME_REFLECTION_TYPE_NAME);
        } else {
            throw VMError.shouldNotReachHere();
        }
    }

    private static DynamicHub getCheckedArrayHub(DynamicHub elementType) {
        if (probability(LUDICROUSLY_FAST_PATH_PROBABILITY, elementType != null) && probability(LUDICROUSLY_FAST_PATH_PROBABILITY, elementType != DynamicHub.fromClass(void.class))) {
            DynamicHub nonNullElementType = (DynamicHub) PiNode.piCastNonNull(elementType, SnippetAnchorNode.anchor());
            DynamicHub arrayHub = nonNullElementType.getArrayHub();
            if (probability(LUDICROUSLY_FAST_PATH_PROBABILITY, arrayHub != null)) {
                DynamicHub nonNullArrayHub = (DynamicHub) PiNode.piCastNonNull(arrayHub, SnippetAnchorNode.anchor());
                if (probability(LUDICROUSLY_FAST_PATH_PROBABILITY, nonNullArrayHub.isInstantiated())) {
                    return nonNullArrayHub;
                }
            }
        }

        callArrayHubErrorStub(ARRAY_HUB_ERROR, DynamicHub.toClass(elementType));
        throw UnreachableNode.unreachable();
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callArrayHubErrorStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> elementType);

    /** Foreign call: {@link #ARRAY_HUB_ERROR}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void arrayHubErrorStub(DynamicHub elementType) {
        if (elementType == null) {
            throw new NullPointerException("Allocation type is null.");
        } else if (elementType == DynamicHub.fromClass(void.class)) {
            throw new IllegalArgumentException("Cannot allocate void array.");
        } else if (elementType.getArrayHub() == null || !elementType.getArrayHub().isInstantiated()) {
            throw new IllegalArgumentException("Class " + DynamicHub.toClass(elementType).getTypeName() + "[]" +
                            " is instantiated reflectively but was never registered. Register the class by using " + RUNTIME_REFLECTION_TYPE_NAME);
        } else {
            VMError.shouldNotReachHere();
        }
    }

    @Override
    protected final int getPrefetchStyle() {
        return SubstrateOptions.AllocatePrefetchStyle.getValue();
    }

    @Fold
    @Override
    protected int getPrefetchLines(boolean isArray) {
        if (isArray) {
            return SubstrateOptions.AllocatePrefetchLines.getValue();
        } else {
            return SubstrateOptions.AllocateInstancePrefetchLines.getValue();
        }
    }

    @Override
    protected final int getPrefetchStepSize() {
        return SubstrateOptions.AllocatePrefetchStepSize.getValue();
    }

    @Override
    protected final int getPrefetchDistance() {
        return SubstrateOptions.AllocatePrefetchDistance.getValue();
    }

    @Override
    protected final int instanceHeaderSize() {
        return ConfigurationValues.getObjectLayout().getFirstFieldOffset();
    }

    @Override
    protected final void profileAllocation(AllocationProfilingData profilingData, UnsignedWord size) {
        if (AllocationSite.Options.AllocationProfiling.getValue()) {
            SubstrateAllocationProfilingData svmProfilingData = (SubstrateAllocationProfilingData) profilingData;
            AllocationCounter allocationSiteCounter = svmProfilingData.allocationSiteCounter;
            allocationSiteCounter.incrementCount();
            allocationSiteCounter.incrementSize(size.rawValue());
        }
    }

    @Fold
    @Override
    protected int getMinimalBulkZeroingSize() {
        return GraalOptions.MinimalBulkZeroingSize.getValue(HostedOptionValues.singleton());
    }

    @Override
    protected final Object verifyOop(Object obj) {
        return obj;
    }

    @Override
    protected final int arrayLengthOffset() {
        return ConfigurationValues.getObjectLayout().getArrayLengthOffset();
    }

    @Override
    protected final int objectAlignment() {
        return ConfigurationValues.getObjectLayout().getAlignment();
    }

    protected static int getArrayBaseOffset(int layoutEncoding) {
        return (int) LayoutEncoding.getArrayBaseOffset(layoutEncoding).rawValue();
    }

    private static Word encodeAsTLABObjectHeader(DynamicHub hub) {
        return Heap.getHeap().getObjectHeader().encodeAsTLABObjectHeader(hub);
    }

    @Override
    protected final Object callNewInstanceStub(Word objectHeader) {
        return callSlowNewInstance(getSlowNewInstanceStub(), objectHeader);
    }

    @Override
    protected final Object callNewArrayStub(Word objectHeader, int length) {
        return callSlowNewArray(getSlowNewArrayStub(), objectHeader, length);
    }

    @Override
    protected final Object callNewMultiArrayStub(Word objectHeader, int rank, Word dims) {
        return callNewMultiArray(NEW_MULTI_ARRAY, objectHeader, rank, dims);
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callSlowNewInstance(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callSlowNewArray(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub, int length);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callNewMultiArray(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub, int rank, Word dimensions);

    protected abstract SubstrateForeignCallDescriptor getSlowNewInstanceStub();

    protected abstract SubstrateForeignCallDescriptor getSlowNewArrayStub();

    public static class SubstrateAllocationProfilingData extends AllocationProfilingData {
        final AllocationCounter allocationSiteCounter;

        public SubstrateAllocationProfilingData(AllocationSnippetCounters snippetCounters, AllocationCounter allocationSiteCounter) {
            super(snippetCounters);
            this.allocationSiteCounter = allocationSiteCounter;
        }
    }

    public abstract static class Templates extends SubstrateTemplates {
        protected final AllocationSnippetCounters snippetCounters;
        private final AllocationProfilingData profilingData;

        private final SnippetInfo allocateInstance;
        private final SnippetInfo allocateArray;
        private final SnippetInfo newmultiarray;

        private final SnippetInfo allocateArrayDynamic;
        private final SnippetInfo allocateInstanceDynamic;

        public Templates(SubstrateAllocationSnippets receiver, OptionValues options, Iterable<DebugHandlersFactory> factories, SnippetCounter.Group.Factory groupFactory, Providers providers,
                        SnippetReflectionProvider snippetReflection) {
            super(options, factories, providers, snippetReflection);
            snippetCounters = new AllocationSnippetCounters(groupFactory);
            profilingData = new SubstrateAllocationProfilingData(snippetCounters, null);

            allocateInstance = snippet(SubstrateAllocationSnippets.class, "allocateInstance", null, receiver, ALLOCATION_LOCATION_IDENTITIES);
            allocateArray = snippet(SubstrateAllocationSnippets.class, "allocateArray", null, receiver, ALLOCATION_LOCATION_IDENTITIES);
            allocateInstanceDynamic = snippet(SubstrateAllocationSnippets.class, "allocateInstanceDynamic", null, receiver, ALLOCATION_LOCATION_IDENTITIES);
            allocateArrayDynamic = snippet(SubstrateAllocationSnippets.class, "allocateArrayDynamic", null, receiver, ALLOCATION_LOCATION_IDENTITIES);
            newmultiarray = snippet(SubstrateAllocationSnippets.class, "newmultiarray", null, receiver, ALLOCATION_LOCATION_IDENTITIES);
        }

        public void registerLowerings(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
            lowerings.put(NewInstanceNode.class, new NewInstanceLowering());
            lowerings.put(NewArrayNode.class, new NewArrayLowering());
            lowerings.put(DynamicNewInstanceNode.class, new DynamicNewInstanceLowering());
            lowerings.put(DynamicNewArrayNode.class, new DynamicNewArrayLowering());
            lowerings.put(NewMultiArrayNode.class, new NewMultiArrayLowering());
        }

        private AllocationProfilingData getProfilingData(ValueNode node, ResolvedJavaType type) {
            if (AllocationSite.Options.AllocationProfiling.getValue()) {
                // Create one object per snippet instantiation - this kills the snippet caching as
                // we need to add the object as a constant to the snippet.
                return new SubstrateAllocationProfilingData(snippetCounters, createAllocationSiteCounter(node, type));
            }
            return profilingData;
        }

        private static AllocationCounter createAllocationSiteCounter(ValueNode node, ResolvedJavaType type) {
            String siteName = "[others]";
            if (node.getNodeSourcePosition() != null) {
                siteName = node.getNodeSourcePosition().getMethod().asStackTraceElement(node.getNodeSourcePosition().getBCI()).toString();
            }
            String className = "[dynamic]";
            if (type != null) {
                className = type.toJavaName(true);
            }

            AllocationSite allocationSite = AllocationSite.lookup(siteName, className);

            String counterName = node.graph().name;
            if (counterName == null) {
                counterName = node.graph().method().format("%H.%n(%p)");
            }
            return allocationSite.createCounter(counterName);
        }

        private class NewInstanceLowering implements NodeLoweringProvider<NewInstanceNode> {
            @Override
            public void lower(NewInstanceNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }

                SharedType type = (SharedType) node.instanceClass();
                DynamicHub hub = type.getHub();

                ConstantNode hubConstant = ConstantNode.forConstant(SubstrateObjectConstant.forObject(hub), providers.getMetaAccess(), graph);
                long size = LayoutEncoding.getInstanceSize(hub.getLayoutEncoding()).rawValue();

                Arguments args = new Arguments(allocateInstance, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.addConst("size", size);
                args.addConst("fillContents", node.fillContents());
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("profilingData", getProfilingData(node, type));

                template(node, args).instantiate(providers.getMetaAccess(), node, DEFAULT_REPLACER, args);
            }
        }

        private class NewArrayLowering implements NodeLoweringProvider<NewArrayNode> {
            @Override
            public void lower(NewArrayNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }

                ValueNode length = node.length();
                SharedType type = (SharedType) node.elementType().getArrayClass();
                DynamicHub hub = type.getHub();
                int layoutEncoding = hub.getLayoutEncoding();
                int arrayBaseOffset = getArrayBaseOffset(layoutEncoding);
                int log2ElementSize = LayoutEncoding.getArrayIndexShift(layoutEncoding);
                ConstantNode hubConstant = ConstantNode.forConstant(SubstrateObjectConstant.forObject(hub), providers.getMetaAccess(), graph);

                Arguments args = new Arguments(allocateArray, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.add("length", length.isAlive() ? length : graph.addOrUniqueWithInputs(length));
                args.addConst("arrayBaseOffset", arrayBaseOffset);
                args.addConst("log2ElementSize", log2ElementSize);
                args.addConst("fillContents", node.fillContents());
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("maybeUnroll", length.isConstant());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("profilingData", getProfilingData(node, type));

                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class NewMultiArrayLowering implements NodeLoweringProvider<NewMultiArrayNode> {
            @Override
            public void lower(NewMultiArrayNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }

                int rank = node.dimensionCount();
                ValueNode[] dims = new ValueNode[rank];
                for (int i = 0; i < node.dimensionCount(); i++) {
                    dims[i] = node.dimension(i);
                }

                SharedType type = (SharedType) node.type();
                ConstantNode hubConstant = ConstantNode.forConstant(SubstrateObjectConstant.forObject(type.getHub()), providers.getMetaAccess(), graph);

                Arguments args = new Arguments(newmultiarray, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.addConst("rank", rank);
                args.addVarargs("dimensions", int.class, StampFactory.forKind(JavaKind.Int), dims);

                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class DynamicNewInstanceLowering implements NodeLoweringProvider<DynamicNewInstanceNode> {
            @Override
            public void lower(DynamicNewInstanceNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }

                Arguments args = new Arguments(allocateInstanceDynamic, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", node.getInstanceType());
                args.addConst("fillContents", node.fillContents());
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("profilingData", getProfilingData(node, null));

                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class DynamicNewArrayLowering implements NodeLoweringProvider<DynamicNewArrayNode> {
            @Override
            public void lower(DynamicNewArrayNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }

                Arguments args = new Arguments(allocateArrayDynamic, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("elementType", node.getElementType());
                args.add("length", node.length());
                args.addConst("fillContents", node.fillContents());
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("profilingData", getProfilingData(node, null));

                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }
    }
}
