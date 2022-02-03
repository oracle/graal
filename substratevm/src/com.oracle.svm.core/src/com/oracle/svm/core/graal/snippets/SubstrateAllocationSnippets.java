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
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_FAST_PATH_PROBABILITY;
import static org.graalvm.compiler.nodes.extended.BranchProbabilityNode.probability;
import static org.graalvm.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import java.util.Map;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.api.replacements.Snippet;
import org.graalvm.compiler.api.replacements.Snippet.ConstantParameter;
import org.graalvm.compiler.api.replacements.Snippet.NonNullParameter;
import org.graalvm.compiler.api.replacements.Snippet.VarargsParameter;
import org.graalvm.compiler.core.common.GraalOptions;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.UnreachableNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.extended.ForeignCallWithExceptionNode;
import org.graalvm.compiler.nodes.java.DynamicNewArrayNode;
import org.graalvm.compiler.nodes.java.DynamicNewInstanceNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.nodes.java.ValidateNewInstanceClassNode;
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
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.nodes.NewStoredContinuationNode;
import com.oracle.svm.core.graal.nodes.SubstrateNewHybridInstanceNode;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public abstract class SubstrateAllocationSnippets extends AllocationSnippets {
    public static final LocationIdentity TLAB_TOP_IDENTITY = NamedLocationIdentity.mutable("TLAB.top");
    public static final LocationIdentity TLAB_END_IDENTITY = NamedLocationIdentity.mutable("TLAB.end");
    public static final Object[] ALLOCATION_LOCATIONS = new Object[]{TLAB_TOP_IDENTITY, TLAB_END_IDENTITY, AllocationCounter.COUNT_FIELD, AllocationCounter.SIZE_FIELD};
    public static final LocationIdentity[] TLAB_LOCATIONS = new LocationIdentity[]{TLAB_TOP_IDENTITY, TLAB_END_IDENTITY};

    private static final SubstrateForeignCallDescriptor NEW_MULTI_ARRAY = SnippetRuntime.findForeignCall(SubstrateAllocationSnippets.class, "newMultiArrayStub", true);
    private static final SubstrateForeignCallDescriptor HUB_ERROR = SnippetRuntime.findForeignCall(SubstrateAllocationSnippets.class, "hubErrorStub", true);
    private static final SubstrateForeignCallDescriptor ARRAY_HUB_ERROR = SnippetRuntime.findForeignCall(SubstrateAllocationSnippets.class, "arrayHubErrorStub", true);
    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{NEW_MULTI_ARRAY, HUB_ERROR, ARRAY_HUB_ERROR};

    private static final String RUNTIME_REFLECTION_TYPE_NAME = RuntimeReflection.class.getTypeName();

    public static void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(FOREIGN_CALLS);
    }

    @Snippet
    protected Object allocateInstance(@NonNullParameter DynamicHub hub,
                    @ConstantParameter long size,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter AllocationProfilingData profilingData) {
        Object result = allocateInstanceImpl(encodeAsTLABObjectHeader(hub), WordFactory.nullPointer(), WordFactory.unsigned(size), fillContents, emitMemoryBarrier, true, profilingData);
        return piCastToSnippetReplaceeStamp(result);
    }

    @Snippet
    protected Object allocateStoredContinuationInstance(@NonNullParameter DynamicHub hub,
                    long size,
                    @ConstantParameter AllocationProfilingData profilingData) {
        Object result = allocateInstanceImpl(encodeAsTLABObjectHeader(hub), WordFactory.nullPointer(), WordFactory.unsigned(size),
                        FillContent.WITH_GARBAGE_IF_ASSERTIONS_ENABLED, true, false, profilingData);
        return piCastToSnippetReplaceeStamp(result);
    }

    @Snippet
    public Object allocateArray(@NonNullParameter DynamicHub hub,
                    int length,
                    @ConstantParameter int arrayBaseOffset,
                    @ConstantParameter int log2ElementSize,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter int fillStartOffset,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean maybeUnroll,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter AllocationProfilingData profilingData) {
        Object result = allocateArrayImpl(encodeAsTLABObjectHeader(hub), WordFactory.nullPointer(), length, arrayBaseOffset, log2ElementSize, fillContents,
                        fillStartOffset, emitMemoryBarrier, maybeUnroll, supportsBulkZeroing, supportsOptimizedFilling, profilingData);
        return piArrayCastToSnippetReplaceeStamp(result, length);
    }

    @Snippet
    public Object allocateInstanceDynamic(@NonNullParameter DynamicHub hub, @ConstantParameter FillContent fillContents, @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter AllocationProfilingData profilingData) {
        UnsignedWord size = LayoutEncoding.getInstanceSize(hub.getLayoutEncoding());
        Object result = allocateInstanceImpl(encodeAsTLABObjectHeader(hub), WordFactory.nullPointer(), size, fillContents, emitMemoryBarrier, false, profilingData);
        return piCastToSnippetReplaceeStamp(result);
    }

    @Snippet
    public Object allocateArrayDynamic(DynamicHub elementType, int length, @ConstantParameter FillContent fillContents, @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean supportsBulkZeroing, @ConstantParameter boolean supportsOptimizedFilling, @ConstantParameter AllocationProfilingData profilingData) {
        DynamicHub checkedArrayHub = getCheckedArrayHub(elementType);

        int layoutEncoding = checkedArrayHub.getLayoutEncoding();
        int arrayBaseOffset = getArrayBaseOffset(layoutEncoding);
        int log2ElementSize = LayoutEncoding.getArrayIndexShift(layoutEncoding);

        Object result = allocateArrayImpl(encodeAsTLABObjectHeader(checkedArrayHub), WordFactory.nullPointer(), length, arrayBaseOffset, log2ElementSize, fillContents,
                        arrayBaseOffset, emitMemoryBarrier, false, supportsBulkZeroing, supportsOptimizedFilling, profilingData);
        return piArrayCastToSnippetReplaceeStamp(result, length);
    }

    @Snippet
    protected Object newmultiarray(DynamicHub hub, @ConstantParameter int rank, @VarargsParameter int[] dimensions) {
        return newMultiArrayImpl(Word.objectToUntrackedPointer(hub), rank, dimensions);
    }

    @Snippet
    private static DynamicHub validateNewInstanceClass(DynamicHub hub) {
        if (probability(EXTREMELY_FAST_PATH_PROBABILITY, hub != null)) {
            DynamicHub nonNullHub = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
            if (probability(EXTREMELY_FAST_PATH_PROBABILITY, LayoutEncoding.isInstance(nonNullHub.getLayoutEncoding())) &&
                            probability(EXTREMELY_FAST_PATH_PROBABILITY, nonNullHub.isInstantiated())) {
                return nonNullHub;
            }
        }
        callHubErrorWithExceptionStub(HUB_ERROR, DynamicHub.toClass(hub));
        throw UnreachableNode.unreachable();
    }

    /** Foreign call: {@link #NEW_MULTI_ARRAY}. */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static Object newMultiArrayStub(Word dynamicHub, int rank, Word dimensionsStackValue) {
        // newMultiArray does not have a fast path, so there is no need to encode the hub as an
        // object header.
        DynamicHub hub = (DynamicHub) dynamicHub.toObject();
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

    @NodeIntrinsic(value = ForeignCallWithExceptionNode.class)
    private static native void callHubErrorWithExceptionStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> hub);

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
        if (probability(EXTREMELY_FAST_PATH_PROBABILITY, elementType != null) && probability(EXTREMELY_FAST_PATH_PROBABILITY, elementType != DynamicHub.fromClass(void.class))) {
            DynamicHub nonNullElementType = (DynamicHub) PiNode.piCastNonNull(elementType, SnippetAnchorNode.anchor());
            DynamicHub arrayHub = nonNullElementType.getArrayHub();
            if (probability(EXTREMELY_FAST_PATH_PROBABILITY, arrayHub != null)) {
                DynamicHub nonNullArrayHub = (DynamicHub) PiNode.piCastNonNull(arrayHub, SnippetAnchorNode.anchor());
                if (probability(EXTREMELY_FAST_PATH_PROBABILITY, nonNullArrayHub.isInstantiated())) {
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

    protected static int afterArrayLengthOffset() {
        return ConfigurationValues.getObjectLayout().getArrayLengthOffset() + ConfigurationValues.getObjectLayout().sizeInBytes(JavaKind.Int);
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
    public final int arrayLengthOffset() {
        return ConfigurationValues.getObjectLayout().getArrayLengthOffset();
    }

    @Override
    protected final int objectAlignment() {
        return ConfigurationValues.getObjectLayout().getAlignment();
    }

    protected static int getArrayBaseOffset(int layoutEncoding) {
        return (int) LayoutEncoding.getArrayBaseOffset(layoutEncoding).rawValue();
    }

    public static Word encodeAsTLABObjectHeader(DynamicHub hub) {
        return Heap.getHeap().getObjectHeader().encodeAsTLABObjectHeader(hub);
    }

    @Override
    protected final Object callNewInstanceStub(Word objectHeader, UnsignedWord size) {
        return callSlowNewInstance(getSlowNewInstanceStub(), objectHeader, size);
    }

    @Override
    protected final Object callNewInstanceStub(Word objectHeader) {
        throw VMError.shouldNotReachHere("callNewInstanceStub with size should be used to support dynamic allocation");
    }

    @Override
    protected final Object callNewArrayStub(Word objectHeader, int length, int fillStartOffset) {
        return callSlowNewArray(getSlowNewArrayStub(), objectHeader, length, fillStartOffset);
    }

    @Override
    protected final Object callNewMultiArrayStub(Word objectHeader, int rank, Word dims) {
        return callNewMultiArray(NEW_MULTI_ARRAY, objectHeader, rank, dims);
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callSlowNewInstance(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub, UnsignedWord size);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callSlowNewArray(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub, int length, int fillStartOffset);

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

        private final SnippetInfo allocateStoredContinuationInstance;

        private final SnippetInfo validateNewInstanceClass;

        public Templates(SubstrateAllocationSnippets receiver, OptionValues options, SnippetCounter.Group.Factory groupFactory, Providers providers) {
            super(options, providers);
            snippetCounters = new AllocationSnippetCounters(groupFactory);
            profilingData = new SubstrateAllocationProfilingData(snippetCounters, null);

            allocateInstance = snippet(SubstrateAllocationSnippets.class, "allocateInstance", null, receiver, ALLOCATION_LOCATIONS);
            allocateArray = snippet(SubstrateAllocationSnippets.class, "allocateArray", null, receiver, ALLOCATION_LOCATIONS);
            allocateInstanceDynamic = snippet(SubstrateAllocationSnippets.class, "allocateInstanceDynamic", null, receiver, ALLOCATION_LOCATIONS);
            allocateStoredContinuationInstance = snippet(SubstrateAllocationSnippets.class, "allocateStoredContinuationInstance", null, receiver, ALLOCATION_LOCATIONS);
            allocateArrayDynamic = snippet(SubstrateAllocationSnippets.class, "allocateArrayDynamic", null, receiver, ALLOCATION_LOCATIONS);
            newmultiarray = snippet(SubstrateAllocationSnippets.class, "newmultiarray", null, receiver, ALLOCATION_LOCATIONS);

            validateNewInstanceClass = snippet(SubstrateAllocationSnippets.class, "validateNewInstanceClass", ALLOCATION_LOCATIONS);
        }

        public void registerLowerings(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
            lowerings.put(NewInstanceNode.class, new NewInstanceLowering());
            lowerings.put(SubstrateNewHybridInstanceNode.class, new NewHybridInstanceLowering());
            lowerings.put(NewArrayNode.class, new NewArrayLowering());
            lowerings.put(DynamicNewInstanceNode.class, new DynamicNewInstanceLowering());
            lowerings.put(DynamicNewArrayNode.class, new DynamicNewArrayLowering());
            lowerings.put(NewMultiArrayNode.class, new NewMultiArrayLowering());
            lowerings.put(NewStoredContinuationNode.class, new NewStoredContinuationLowering());
            lowerings.put(ValidateNewInstanceClassNode.class, new ValidateNewInstanceClassLowering());
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

                Arguments args = new Arguments(allocateStoredContinuationInstance, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.add("size", node.getSize());
                args.addConst("profilingData", getProfilingData(node, null));

                template(node, args).instantiate(providers.getMetaAccess(), node, DEFAULT_REPLACER, args);
            }
        }

        private class NewInstanceLowering implements NodeLoweringProvider<NewInstanceNode> {
            @Override
            public void lower(NewInstanceNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }

                SharedType type = (SharedType) node.instanceClass();
                DynamicHub hub = ensureMarkedAsInstantiated(type.getHub());

                ConstantNode hubConstant = ConstantNode.forConstant(SubstrateObjectConstant.forObject(hub), providers.getMetaAccess(), graph);
                long size = LayoutEncoding.getInstanceSize(hub.getLayoutEncoding()).rawValue();

                Arguments args = new Arguments(allocateInstance, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.addConst("size", size);
                args.addConst("fillContents", FillContent.fromBoolean(node.fillContents()));
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("profilingData", getProfilingData(node, type));

                template(node, args).instantiate(providers.getMetaAccess(), node, DEFAULT_REPLACER, args);
            }
        }

        private class NewHybridInstanceLowering implements NodeLoweringProvider<SubstrateNewHybridInstanceNode> {
            @Override
            public void lower(SubstrateNewHybridInstanceNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                    return;
                }

                SharedType instanceClass = (SharedType) node.instanceClass();
                ValueNode length = node.length();
                DynamicHub hub = instanceClass.getHub();
                int layoutEncoding = hub.getLayoutEncoding();
                int arrayBaseOffset = (int) LayoutEncoding.getArrayBaseOffset(layoutEncoding).rawValue();
                int log2ElementSize = LayoutEncoding.getArrayIndexShift(layoutEncoding);
                boolean fillContents = node.fillContents();
                assert fillContents : "fillContents must be true for hybrid allocations";

                ConstantNode hubConstant = ConstantNode.forConstant(SubstrateObjectConstant.forObject(hub), providers.getMetaAccess(), graph);

                Arguments args = new Arguments(allocateArray, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.add("length", length.isAlive() ? length : graph.addOrUniqueWithInputs(length));
                args.addConst("arrayBaseOffset", arrayBaseOffset);
                args.addConst("log2ElementSize", log2ElementSize);
                args.addConst("fillContents", FillContent.fromBoolean(fillContents));
                args.addConst("fillStartOffset", afterArrayLengthOffset());
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("maybeUnroll", length.isConstant());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("profilingData", getProfilingData(node, instanceClass));

                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
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
                DynamicHub hub = ensureMarkedAsInstantiated(type.getHub());
                int layoutEncoding = hub.getLayoutEncoding();
                int arrayBaseOffset = getArrayBaseOffset(layoutEncoding);
                int log2ElementSize = LayoutEncoding.getArrayIndexShift(layoutEncoding);
                ConstantNode hubConstant = ConstantNode.forConstant(SubstrateObjectConstant.forObject(hub), providers.getMetaAccess(), graph);

                Arguments args = new Arguments(allocateArray, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.add("length", length.isAlive() ? length : graph.addOrUniqueWithInputs(length));
                args.addConst("arrayBaseOffset", arrayBaseOffset);
                args.addConst("log2ElementSize", log2ElementSize);
                args.addConst("fillContents", FillContent.fromBoolean(node.fillContents()));
                args.addConst("fillStartOffset", afterArrayLengthOffset());
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("maybeUnroll", length.isConstant());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
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
                args.addConst("fillContents", FillContent.fromBoolean(node.fillContents()));
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
                args.addConst("fillContents", FillContent.fromBoolean(node.fillContents()));
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("profilingData", getProfilingData(node, null));

                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class ValidateNewInstanceClassLowering implements NodeLoweringProvider<ValidateNewInstanceClassNode> {
            @Override
            public void lower(ValidateNewInstanceClassNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();

                Arguments args = new Arguments(validateNewInstanceClass, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", node.getInstanceType());

                template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }
    }

    /**
     * Verify that we do not need to check at run time if a type coming from a
     * {@link NewInstanceNode} or {@link NewArrayNode} is instantiated. All allocations with a
     * constant type before static analysis result in the type being marked as instantiated.
     * {@link DynamicNewInstanceNode} and {@link DynamicNewArrayNode} whose type gets folded to a
     * constant late during compilation remain a dynamic allocation because
     * {@link MetaAccessExtensionProvider#canConstantFoldDynamicAllocation} returns false.
     */
    public static DynamicHub ensureMarkedAsInstantiated(DynamicHub hub) {
        if (!hub.isInstantiated()) {
            throw VMError.shouldNotReachHere("Cannot allocate type that is not marked as instantiated: " + hub.getName());
        }
        return hub;
    }
}
