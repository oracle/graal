/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;
import static jdk.graal.compiler.nodes.PiArrayNode.piArrayCastToSnippetReplaceeStamp;
import static jdk.graal.compiler.nodes.PiNode.piCastToSnippetReplaceeStamp;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;

import java.util.Arrays;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MissingRegistrationUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.allocationprofile.AllocationCounter;
import com.oracle.svm.core.allocationprofile.AllocationSite;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.configure.ConfigurationFile;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.nodes.NewPodInstanceNode;
import com.oracle.svm.core.graal.nodes.NewStoredContinuationNode;
import com.oracle.svm.core.graal.nodes.SubstrateNewHybridInstanceNode;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.identityhashcode.IdentityHashCodeSupport;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.option.HostedOptionValues;
import com.oracle.svm.core.reflect.MissingReflectionRegistrationUtils;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.thread.ContinuationSupport;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.api.replacements.Snippet.NonNullParameter;
import jdk.graal.compiler.api.replacements.Snippet.VarargsParameter;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.GraphState;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnreachableNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.extended.ForeignCallWithExceptionNode;
import jdk.graal.compiler.nodes.java.DynamicNewArrayNode;
import jdk.graal.compiler.nodes.java.DynamicNewArrayWithExceptionNode;
import jdk.graal.compiler.nodes.java.DynamicNewInstanceNode;
import jdk.graal.compiler.nodes.java.DynamicNewInstanceWithExceptionNode;
import jdk.graal.compiler.nodes.java.NewArrayNode;
import jdk.graal.compiler.nodes.java.NewArrayWithExceptionNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.graal.compiler.nodes.java.NewInstanceWithExceptionNode;
import jdk.graal.compiler.nodes.java.NewMultiArrayNode;
import jdk.graal.compiler.nodes.java.NewMultiArrayWithExceptionNode;
import jdk.graal.compiler.nodes.java.ValidateNewInstanceClassNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.AllocationSnippets;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.replacements.SnippetCounter;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.word.BarrieredAccess;
import jdk.graal.compiler.word.ObjectAccess;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstrateAllocationSnippets extends AllocationSnippets {
    public static final LocationIdentity TLAB_TOP_IDENTITY = NamedLocationIdentity.mutable("TLAB.top");
    public static final LocationIdentity TLAB_END_IDENTITY = NamedLocationIdentity.mutable("TLAB.end");
    public static final Object[] ALLOCATION_LOCATIONS = new Object[]{TLAB_TOP_IDENTITY, TLAB_END_IDENTITY, IdentityHashCodeSupport.IDENTITY_HASHCODE_SALT_LOCATION,
                    AllocationCounter.COUNT_FIELD, AllocationCounter.SIZE_FIELD};
    public static final LocationIdentity[] GC_LOCATIONS = new LocationIdentity[]{TLAB_TOP_IDENTITY, TLAB_END_IDENTITY, IdentityHashCodeSupport.IDENTITY_HASHCODE_SALT_LOCATION};

    private static final SubstrateForeignCallDescriptor NEW_MULTI_ARRAY = SnippetRuntime.findForeignCall(SubstrateAllocationSnippets.class, "newMultiArrayStub", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor INSTANCE_HUB_ERROR = SnippetRuntime.findForeignCall(SubstrateAllocationSnippets.class, "instanceHubErrorStub", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor ARRAY_HUB_ERROR = SnippetRuntime.findForeignCall(SubstrateAllocationSnippets.class, "arrayHubErrorStub", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{NEW_MULTI_ARRAY, INSTANCE_HUB_ERROR, ARRAY_HUB_ERROR};

    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(FOREIGN_CALLS);
    }

    @Snippet
    protected Object allocateInstance(@NonNullParameter DynamicHub hub,
                    @ConstantParameter long size,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter AllocationProfilingData profilingData,
                    @ConstantParameter boolean withException) {
        Object result = allocateInstanceImpl(encodeAsTLABObjectHeader(hub), WordFactory.unsigned(size), false, fillContents, emitMemoryBarrier, true, profilingData, withException);
        return piCastToSnippetReplaceeStamp(result);
    }

    @Snippet
    public Object allocateArray(@NonNullParameter DynamicHub hub,
                    int length,
                    @ConstantParameter int arrayBaseOffset,
                    @ConstantParameter int log2ElementSize,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean maybeUnroll,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter AllocationProfilingData profilingData,
                    @ConstantParameter boolean withException) {
        Object result = allocateArrayImpl(encodeAsTLABObjectHeader(hub), length, arrayBaseOffset, log2ElementSize, fillContents,
                        afterArrayLengthOffset(), emitMemoryBarrier, maybeUnroll, supportsBulkZeroing, supportsOptimizedFilling, profilingData, withException);
        return piArrayCastToSnippetReplaceeStamp(result, length);
    }

    @Snippet
    public Object allocateStoredContinuation(@NonNullParameter DynamicHub hub,
                    int length,
                    @ConstantParameter int arrayBaseOffset,
                    @ConstantParameter int log2ElementSize,
                    @ConstantParameter long ipOffset,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter AllocationProfilingData profilingData) {
        Word thread = getTLABInfo();
        Word top = readTlabTop(thread);
        Word end = readTlabEnd(thread);
        ReplacementsUtil.dynamicAssert(end.subtract(top).belowOrEqual(Integer.MAX_VALUE), "TLAB is too large");

        // A negative array length will result in an array size larger than the largest possible
        // TLAB. Therefore, this case will always end up in the stub call.
        UnsignedWord allocationSize = arrayAllocationSize(length, arrayBaseOffset, log2ElementSize);
        Word newTop = top.add(allocationSize);

        Object result;
        if (useTLAB() && probability(FAST_PATH_PROBABILITY, shouldAllocateInTLAB(allocationSize, true)) && probability(FAST_PATH_PROBABILITY, newTop.belowOrEqual(end))) {
            writeTlabTop(thread, newTop);
            emitPrefetchAllocate(newTop, true);
            result = formatStoredContinuation(encodeAsTLABObjectHeader(hub), allocationSize, length, top, emitMemoryBarrier, ipOffset, profilingData.snippetCounters);
        } else {
            profilingData.snippetCounters.stub.inc();
            result = callSlowNewStoredContinuation(gcAllocationSupport().getNewStoredContinuationStub(), encodeAsTLABObjectHeader(hub), length);
        }
        profileAllocation(profilingData, allocationSize);
        return piArrayCastToSnippetReplaceeStamp(verifyOop(result), length);
    }

    @Snippet
    public Object allocatePod(@NonNullParameter DynamicHub hub,
                    int arrayLength,
                    byte[] referenceMap,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean maybeUnroll,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter AllocationSnippets.AllocationProfilingData profilingData) {
        Word thread = getTLABInfo();
        Word top = readTlabTop(thread);
        Word end = readTlabEnd(thread);
        ReplacementsUtil.dynamicAssert(end.subtract(top).belowOrEqual(Integer.MAX_VALUE), "TLAB is too large");

        // A negative array length will result in an array size larger than the largest possible
        // TLAB. Therefore, this case will always end up in the stub call.
        int arrayBaseOffset = LayoutEncoding.getArrayBaseOffsetAsInt(hub.getLayoutEncoding());
        UnsignedWord allocationSize = arrayAllocationSize(arrayLength, arrayBaseOffset, 0);
        Word newTop = top.add(allocationSize);

        Object result;
        if (useTLAB() && probability(FAST_PATH_PROBABILITY, shouldAllocateInTLAB(allocationSize, true)) && probability(FAST_PATH_PROBABILITY, newTop.belowOrEqual(end))) {
            writeTlabTop(thread, newTop);
            emitPrefetchAllocate(newTop, true);
            result = formatPod(encodeAsTLABObjectHeader(hub), hub, allocationSize, arrayLength, referenceMap, top, AllocationSnippets.FillContent.WITH_ZEROES,
                            emitMemoryBarrier, maybeUnroll, supportsBulkZeroing, supportsOptimizedFilling, profilingData.snippetCounters);
        } else {
            profilingData.snippetCounters.stub.inc();
            result = callSlowNewPodInstance(gcAllocationSupport().getNewPodInstanceStub(), encodeAsTLABObjectHeader(hub), arrayLength, referenceMap);
        }
        profileAllocation(profilingData, allocationSize);
        return piArrayCastToSnippetReplaceeStamp(verifyOop(result), arrayLength);
    }

    @Snippet
    public Object allocateInstanceDynamic(@NonNullParameter DynamicHub hub,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter AllocationProfilingData profilingData,
                    @ConstantParameter boolean withException) {
        return allocateInstanceDynamicImpl(hub, fillContents, emitMemoryBarrier, supportsBulkZeroing, supportsOptimizedFilling, profilingData, withException);
    }

    protected Object allocateInstanceDynamicImpl(DynamicHub hub, FillContent fillContents, boolean emitMemoryBarrier, @SuppressWarnings("unused") boolean supportsBulkZeroing,
                    @SuppressWarnings("unused") boolean supportsOptimizedFilling, AllocationProfilingData profilingData, boolean withException) {
        // The hub was already verified by a ValidateNewInstanceClassNode.
        UnsignedWord size = LayoutEncoding.getPureInstanceAllocationSize(hub.getLayoutEncoding());
        Object result = allocateInstanceImpl(encodeAsTLABObjectHeader(hub), size, false, fillContents, emitMemoryBarrier, false, profilingData, withException);
        return piCastToSnippetReplaceeStamp(result);
    }

    @Snippet
    public Object allocateArrayDynamic(DynamicHub elementType,
                    int length,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter boolean withException,
                    @ConstantParameter AllocationProfilingData profilingData) {
        DynamicHub checkedArrayHub = getCheckedArrayHub(elementType);

        int layoutEncoding = checkedArrayHub.getLayoutEncoding();
        int arrayBaseOffset = getArrayBaseOffset(layoutEncoding);
        int log2ElementSize = LayoutEncoding.getArrayIndexShift(layoutEncoding);

        Object result = allocateArrayImpl(encodeAsTLABObjectHeader(checkedArrayHub), length, arrayBaseOffset, log2ElementSize, fillContents,
                        arrayBaseOffset, emitMemoryBarrier, false, supportsBulkZeroing, supportsOptimizedFilling, profilingData, withException);

        return piArrayCastToSnippetReplaceeStamp(result, length);
    }

    @Snippet
    protected Object newmultiarray(DynamicHub hub, @ConstantParameter int rank, @ConstantParameter boolean withException, @VarargsParameter int[] dimensions) {
        return newMultiArrayImpl(Word.objectToUntrackedPointer(hub), rank, withException, dimensions);
    }

    @Snippet
    public DynamicHub validateNewInstanceClass(DynamicHub hub) {
        if (probability(EXTREMELY_FAST_PATH_PROBABILITY, hub != null)) {
            DynamicHub nonNullHub = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
            if (probability(EXTREMELY_FAST_PATH_PROBABILITY, nonNullHub.canUnsafeInstantiateAsInstance())) {
                return nonNullHub;
            }
        }
        callInstanceHubErrorWithExceptionStub(INSTANCE_HUB_ERROR, DynamicHub.toClass(hub));
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

        if (probability(LIKELY_PROBABILITY, rank > 1)) {
            UnsignedWord offset = LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding());
            UnsignedWord endOffset = LayoutEncoding.getArrayElementOffset(hub.getLayoutEncoding(), length);

            while (GraalDirectives.injectIterationCount(10, offset.belowThan(endOffset))) {
                // Each newMultiArrayRecursion could create a cross-generational reference.
                BarrieredAccess.writeObject(result, offset,
                                newMultiArrayRecursion(hub.getComponentHub(), rank - 1, dimensionsStackValue.add(4)));
                offset = offset.add(ConfigurationValues.getObjectLayout().getReferenceSize());
            }
        }
        return result;
    }

    @NodeIntrinsic(value = ForeignCallWithExceptionNode.class)
    private static native void callInstanceHubErrorWithExceptionStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> hub);

    /** Foreign call: {@link #INSTANCE_HUB_ERROR}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static void instanceHubErrorStub(DynamicHub hub) throws InstantiationException {
        if (hub == null) {
            throw new NullPointerException("Allocation type is null.");
        } else if (!hub.isInstanceClass() || LayoutEncoding.isSpecial(hub.getLayoutEncoding())) {
            throw new InstantiationException("Can only allocate instance objects for concrete classes.");
        } else if (!hub.canUnsafeInstantiateAsInstance()) {
            if (MissingRegistrationUtils.throwMissingRegistrationErrors()) {
                MissingReflectionRegistrationUtils.forClass(hub.getTypeName());
            }
            throw new IllegalArgumentException("Type " + DynamicHub.toClass(hub).getTypeName() + " is instantiated reflectively but was never registered." +
                            " Register the type by adding \"unsafeAllocated\" for the type in " + ConfigurationFile.REFLECTION.getFileName() + ".");
        } else if (LayoutEncoding.isHybrid(hub.getLayoutEncoding())) {
            throw new InstantiationException("Cannot allocate objects of special hybrid types.");
        } else {
            throw VMError.shouldNotReachHereUnexpectedInput(hub); // ExcludeFromJacocoGeneratedReport
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
            throw MissingReflectionRegistrationUtils.errorForArray(DynamicHub.toClass(elementType), 1);
        } else {
            VMError.shouldNotReachHereUnexpectedInput(elementType); // ExcludeFromJacocoGeneratedReport
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callSlowNewPodInstance(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub, int length, byte[] referenceMap);

    public Object formatArray(Word hub, UnsignedWord allocationSize, int length, Word memory, FillContent fillContents, boolean emitMemoryBarrier, boolean maybeUnroll, boolean supportsBulkZeroing,
                    boolean supportsOptimizedFilling, AllocationSnippetCounters snippetCounters) {
        return formatArray(hub, allocationSize, length, memory, fillContents, emitMemoryBarrier, SubstrateAllocationSnippets.afterArrayLengthOffset(), maybeUnroll, supportsBulkZeroing,
                        supportsOptimizedFilling, snippetCounters);
    }

    public Object formatStoredContinuation(Word objectHeader, UnsignedWord allocationSize, int arrayLength, Word memory, boolean emitMemoryBarrier, long ipOffset,
                    AllocationSnippetCounters snippetCounters) {
        Object result = formatArray(objectHeader, allocationSize, arrayLength, memory, FillContent.DO_NOT_FILL, false, false, false, false, snippetCounters);
        memory.writeWord(WordFactory.unsigned(ipOffset), WordFactory.nullPointer(), LocationIdentity.init());
        emitMemoryBarrierIf(emitMemoryBarrier);
        return result;
    }

    public Object formatPod(Word objectHeader, DynamicHub hub, UnsignedWord allocationSize, int arrayLength, byte[] referenceMap, Word memory, FillContent fillContents, boolean emitMemoryBarrier,
                    boolean maybeUnroll, boolean supportsBulkZeroing, boolean supportsOptimizedFilling, AllocationSnippetCounters snippetCounters) {
        Object result = formatArray(objectHeader, allocationSize, arrayLength, memory, fillContents, false, maybeUnroll, supportsBulkZeroing, supportsOptimizedFilling,
                        snippetCounters);

        int fromOffset = ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Byte);
        int toOffset = LayoutEncoding.getArrayBaseOffsetAsInt(hub.getLayoutEncoding()) + arrayLength - referenceMap.length;
        for (int i = 0; probability(LIKELY_PROBABILITY, i < referenceMap.length); i++) {
            byte b = ObjectAccess.readByte(referenceMap, fromOffset + i, byteArrayIdentity());
            ObjectAccess.writeByte(result, toOffset + i, b, LocationIdentity.INIT_LOCATION);
        }
        emitMemoryBarrierIf(emitMemoryBarrier);
        return result;
    }

    @Fold
    static LocationIdentity byteArrayIdentity() {
        return NamedLocationIdentity.getArrayLocation(JavaKind.Byte);
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

    @Fold
    public static int afterArrayLengthOffset() {
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

    public static int getArrayBaseOffset(int layoutEncoding) {
        return (int) LayoutEncoding.getArrayBaseOffset(layoutEncoding).rawValue();
    }

    public static Word encodeAsTLABObjectHeader(DynamicHub hub) {
        return Heap.getHeap().getObjectHeader().encodeAsTLABObjectHeader(hub);
    }

    @Override
    protected final Object callNewInstanceStub(Word objectHeader, boolean withException) {
        if (withException) {
            return callSlowNewInstanceWithException(gcAllocationSupport().getNewInstanceStub(), objectHeader);
        }
        return callSlowNewInstance(gcAllocationSupport().getNewInstanceStub(), objectHeader);
    }

    @Override
    protected final Object callNewArrayStub(Word objectHeader, int length, boolean withException) {
        if (withException) {
            return callSlowNewArrayWithException(gcAllocationSupport().getNewArrayStub(), objectHeader, length);
        }
        return callSlowNewArray(gcAllocationSupport().getNewArrayStub(), objectHeader, length);
    }

    @Override
    protected final Object callNewMultiArrayStub(Word objectHeader, int rank, Word dims, boolean withException) {
        if (withException) {
            return callNewMultiArrayWithException(NEW_MULTI_ARRAY, objectHeader, rank, dims);
        }
        return callNewMultiArray(NEW_MULTI_ARRAY, objectHeader, rank, dims);
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callSlowNewInstance(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub);

    @NodeIntrinsic(value = ForeignCallWithExceptionNode.class)
    private static native Object callSlowNewInstanceWithException(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callSlowNewArray(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub, int length);

    @NodeIntrinsic(value = ForeignCallWithExceptionNode.class)
    private static native Object callSlowNewArrayWithException(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub, int length);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callSlowNewStoredContinuation(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub, int length);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callNewMultiArray(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub, int rank, Word dimensions);

    @NodeIntrinsic(value = ForeignCallWithExceptionNode.class)
    private static native Object callNewMultiArrayWithException(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub, int rank, Word dimensions);

    @Override
    public void initializeObjectHeader(Word memory, Word objectHeader, boolean isArrayLike) {
        Heap.getHeap().getObjectHeader().initializeHeaderOfNewObject(memory, objectHeader, isArrayLike);
    }

    @Override
    public boolean useTLAB() {
        return gcAllocationSupport().useTLAB();
    }

    @Override
    protected boolean shouldAllocateInTLAB(UnsignedWord size, boolean isArray) {
        return gcAllocationSupport().shouldAllocateInTLAB(size, isArray);
    }

    @Override
    public Word getTLABInfo() {
        return gcAllocationSupport().getTLABInfo();
    }

    @Override
    public Word readTlabTop(Word tlabInfo) {
        return tlabInfo.readWord(gcAllocationSupport().tlabTopOffset(), TLAB_TOP_IDENTITY);
    }

    @Override
    public Word readTlabEnd(Word tlabInfo) {
        return tlabInfo.readWord(gcAllocationSupport().tlabEndOffset(), TLAB_END_IDENTITY);
    }

    @Override
    public void writeTlabTop(Word tlabInfo, Word newTop) {
        tlabInfo.writeWord(gcAllocationSupport().tlabTopOffset(), newTop, TLAB_TOP_IDENTITY);
    }

    @Fold
    static GCAllocationSupport gcAllocationSupport() {
        return ImageSingletons.lookup(GCAllocationSupport.class);
    }

    public static class SubstrateAllocationProfilingData extends AllocationProfilingData {
        final AllocationCounter allocationSiteCounter;

        public SubstrateAllocationProfilingData(AllocationSnippetCounters snippetCounters, AllocationCounter allocationSiteCounter) {
            super(snippetCounters);
            this.allocationSiteCounter = allocationSiteCounter;
        }
    }

    public static class Templates extends SubstrateTemplates {
        private final AllocationSnippetCounters snippetCounters;
        private final AllocationProfilingData profilingData;

        private final SnippetInfo allocateInstance;
        private final SnippetInfo allocateArray;
        private final SnippetInfo newmultiarray;

        private final SnippetInfo allocateArrayDynamic;
        private final SnippetInfo allocateInstanceDynamic;

        private final SnippetInfo validateNewInstanceClass;

        private final SnippetInfo allocateStoredContinuation;
        private final SnippetInfo allocatePod;

        @SuppressWarnings("this-escape")
        public Templates(OptionValues options, Providers providers, SubstrateAllocationSnippets receiver) {
            super(options, providers);
            snippetCounters = new AllocationSnippetCounters(SnippetCounter.Group.NullFactory);
            profilingData = new SubstrateAllocationProfilingData(snippetCounters, null);

            allocateInstance = snippet(providers,
                            SubstrateAllocationSnippets.class,
                            "allocateInstance",
                            null,
                            receiver,
                            ALLOCATION_LOCATIONS);
            allocateArray = snippet(providers,
                            SubstrateAllocationSnippets.class,
                            "allocateArray",
                            null,
                            receiver,
                            ALLOCATION_LOCATIONS);
            allocateInstanceDynamic = snippet(providers,
                            SubstrateAllocationSnippets.class,
                            "allocateInstanceDynamic",
                            null,
                            receiver,
                            ALLOCATION_LOCATIONS);
            allocateArrayDynamic = snippet(providers,
                            SubstrateAllocationSnippets.class,
                            "allocateArrayDynamic",
                            null,
                            receiver,
                            ALLOCATION_LOCATIONS);
            newmultiarray = snippet(providers,
                            SubstrateAllocationSnippets.class,
                            "newmultiarray",
                            null,
                            receiver,
                            ALLOCATION_LOCATIONS);
            validateNewInstanceClass = snippet(providers,
                            SubstrateAllocationSnippets.class,
                            "validateNewInstanceClass",
                            null,
                            receiver,
                            ALLOCATION_LOCATIONS);

            SnippetInfo allocateStoredContinuationSnippet = null;
            if (ContinuationSupport.isSupported()) {
                allocateStoredContinuationSnippet = snippet(providers,
                                SubstrateAllocationSnippets.class,
                                "allocateStoredContinuation",
                                null,
                                receiver,
                                ALLOCATION_LOCATIONS);
            }
            allocateStoredContinuation = allocateStoredContinuationSnippet;

            SnippetInfo allocatePodSnippet = null;
            if (Pod.RuntimeSupport.isPresent()) {
                Object[] podLocations = SubstrateAllocationSnippets.ALLOCATION_LOCATIONS;
                podLocations = Arrays.copyOf(podLocations, podLocations.length + 1);
                podLocations[podLocations.length - 1] = byteArrayIdentity();
                allocatePodSnippet = snippet(providers,
                                SubstrateAllocationSnippets.class,
                                "allocatePod",
                                null,
                                receiver,
                                podLocations);
            }
            allocatePod = allocatePodSnippet;
        }

        public void registerLowering(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
            lowerings.put(NewInstanceNode.class, new NewInstanceLowering());
            lowerings.put(NewInstanceWithExceptionNode.class, new NewInstanceWithExceptionLowering());

            lowerings.put(SubstrateNewHybridInstanceNode.class, new NewHybridInstanceLowering());

            lowerings.put(NewArrayNode.class, new NewArrayLowering());
            lowerings.put(NewArrayWithExceptionNode.class, new NewArrayWithExceptionLowering());

            lowerings.put(DynamicNewInstanceNode.class, new DynamicNewInstanceLowering());
            lowerings.put(DynamicNewInstanceWithExceptionNode.class, new DynamicNewInstanceWithExceptionLowering());

            lowerings.put(DynamicNewArrayNode.class, new DynamicNewArrayLowering());
            lowerings.put(DynamicNewArrayWithExceptionNode.class, new DynamicNewArrayWithExceptionLowering());

            lowerings.put(NewMultiArrayNode.class, new NewMultiArrayLowering());
            lowerings.put(NewMultiArrayWithExceptionNode.class, new NewMultiArrayWithExceptionLowering());

            lowerings.put(ValidateNewInstanceClassNode.class, new ValidateNewInstanceClassLowering());

            if (ContinuationSupport.isSupported()) {
                lowerings.put(NewStoredContinuationNode.class, new NewStoredContinuationLowering());
            }
            if (Pod.RuntimeSupport.isPresent()) {
                lowerings.put(NewPodInstanceNode.class, new NewPodInstanceLowering());
            }
        }

        public AllocationSnippetCounters getSnippetCounters() {
            return snippetCounters;
        }

        public AllocationProfilingData getProfilingData(ValueNode node, ResolvedJavaType type) {
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
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }

                SharedType type = (SharedType) node.instanceClass();
                DynamicHub hub = ensureMarkedAsInstantiated(type.getHub());

                ConstantNode hubConstant = ConstantNode.forConstant(snippetReflection.forObject(hub), tool.getMetaAccess(), graph);
                long size = LayoutEncoding.getPureInstanceAllocationSize(hub.getLayoutEncoding()).rawValue();

                Arguments args = new Arguments(allocateInstance, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.addConst("size", size);
                args.addConst("fillContents", FillContent.fromBoolean(node.fillContents()));
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("profilingData", getProfilingData(node, type));
                args.addConst("withException", false);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
            }
        }

        private class NewInstanceWithExceptionLowering implements NodeLoweringProvider<NewInstanceWithExceptionNode> {
            @Override
            public void lower(NewInstanceWithExceptionNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }
                SharedType type = (SharedType) node.instanceClass();
                DynamicHub hub = ensureMarkedAsInstantiated(type.getHub());

                ConstantNode hubConstant = ConstantNode.forConstant(snippetReflection.forObject(hub), tool.getMetaAccess(), graph);
                long size = LayoutEncoding.getPureInstanceAllocationSize(hub.getLayoutEncoding()).rawValue();

                Arguments args = new Arguments(allocateInstance, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.addConst("size", size);
                args.addConst("fillContents", FillContent.fromBoolean(true));
                args.addConst("emitMemoryBarrier", true);
                args.addConst("profilingData", getProfilingData(node, type));
                args.addConst("withException", true);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
            }
        }

        private class NewHybridInstanceLowering implements NodeLoweringProvider<SubstrateNewHybridInstanceNode> {
            @Override
            public void lower(SubstrateNewHybridInstanceNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }

                SharedType instanceClass = (SharedType) node.instanceClass();
                ValueNode length = node.length();
                DynamicHub hub = instanceClass.getHub();
                int layoutEncoding = hub.getLayoutEncoding();
                int arrayBaseOffset = LayoutEncoding.getArrayBaseOffsetAsInt(layoutEncoding);
                int log2ElementSize = LayoutEncoding.getArrayIndexShift(layoutEncoding);
                boolean fillContents = node.fillContents();
                assert fillContents : "fillContents must be true for hybrid allocations";

                ConstantNode hubConstant = ConstantNode.forConstant(snippetReflection.forObject(hub), tool.getMetaAccess(), graph);

                Arguments args = new Arguments(allocateArray, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.add("length", length.isAlive() ? length : graph.addOrUniqueWithInputs(length));
                args.addConst("arrayBaseOffset", arrayBaseOffset);
                args.addConst("log2ElementSize", log2ElementSize);
                args.addConst("fillContents", FillContent.fromBoolean(fillContents));
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("maybeUnroll", length.isConstant());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("profilingData", getProfilingData(node, instanceClass));
                args.addConst("withException", false);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class NewStoredContinuationLowering implements NodeLoweringProvider<NewStoredContinuationNode> {
            @Override
            public void lower(NewStoredContinuationNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }

                SharedType instanceClass = (SharedType) node.instanceClass();
                ValueNode length = node.length();
                DynamicHub hub = instanceClass.getHub();
                int layoutEncoding = hub.getLayoutEncoding();
                int arrayBaseOffset = LayoutEncoding.getArrayBaseOffsetAsInt(layoutEncoding);
                int log2ElementSize = LayoutEncoding.getArrayIndexShift(layoutEncoding);

                ConstantNode hubConstant = ConstantNode.forConstant(snippetReflection.forObject(hub), tool.getMetaAccess(), graph);

                Arguments args = new Arguments(allocateStoredContinuation, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.add("length", length.isAlive() ? length : graph.addOrUniqueWithInputs(length));
                args.addConst("arrayBaseOffset", arrayBaseOffset);
                args.addConst("log2ElementSize", log2ElementSize);
                args.addConst("ipOffset", ContinuationSupport.singleton().getIPOffset());
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("profilingData", getProfilingData(node, instanceClass));

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class NewArrayLowering implements NodeLoweringProvider<NewArrayNode> {
            @Override
            public void lower(NewArrayNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }

                ValueNode length = node.length();
                SharedType type = (SharedType) node.elementType().getArrayClass();
                DynamicHub hub = ensureMarkedAsInstantiated(type.getHub());
                int layoutEncoding = hub.getLayoutEncoding();
                int arrayBaseOffset = getArrayBaseOffset(layoutEncoding);
                int log2ElementSize = LayoutEncoding.getArrayIndexShift(layoutEncoding);
                ConstantNode hubConstant = ConstantNode.forConstant(snippetReflection.forObject(hub), tool.getMetaAccess(), graph);

                Arguments args = new Arguments(allocateArray, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.add("length", length.isAlive() ? length : graph.addOrUniqueWithInputs(length));
                args.addConst("arrayBaseOffset", arrayBaseOffset);
                args.addConst("log2ElementSize", log2ElementSize);
                args.addConst("fillContents", FillContent.fromBoolean(node.fillContents()));
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("maybeUnroll", length.isConstant());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("profilingData", getProfilingData(node, type));
                args.addConst("withException", false);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class NewArrayWithExceptionLowering implements NodeLoweringProvider<NewArrayWithExceptionNode> {
            @Override
            public void lower(NewArrayWithExceptionNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }
                // lowered with exception path early on

                ValueNode length = node.length();
                SharedType type = (SharedType) node.elementType().getArrayClass();
                DynamicHub hub = ensureMarkedAsInstantiated(type.getHub());
                int layoutEncoding = hub.getLayoutEncoding();
                int arrayBaseOffset = getArrayBaseOffset(layoutEncoding);
                int log2ElementSize = LayoutEncoding.getArrayIndexShift(layoutEncoding);
                ConstantNode hubConstant = ConstantNode.forConstant(snippetReflection.forObject(hub), tool.getMetaAccess(), graph);

                Arguments args = new Arguments(allocateArray, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.add("length", length.isAlive() ? length : graph.addOrUniqueWithInputs(length));
                args.addConst("arrayBaseOffset", arrayBaseOffset);
                args.addConst("log2ElementSize", log2ElementSize);
                args.addConst("fillContents", FillContent.fromBoolean(node.fillContents()));
                args.addConst("emitMemoryBarrier", true);
                args.addConst("maybeUnroll", length.isConstant());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("profilingData", getProfilingData(node, type));
                args.addConst("withException", true);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class NewMultiArrayLowering implements NodeLoweringProvider<NewMultiArrayNode> {
            @Override
            public void lower(NewMultiArrayNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }

                int rank = node.dimensionCount();
                ValueNode[] dims = new ValueNode[rank];
                for (int i = 0; i < node.dimensionCount(); i++) {
                    dims[i] = node.dimension(i);
                }

                SharedType type = (SharedType) node.type();
                ConstantNode hubConstant = ConstantNode.forConstant(snippetReflection.forObject(type.getHub()), tool.getMetaAccess(), graph);

                Arguments args = new Arguments(newmultiarray, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.addConst("rank", rank);
                args.addConst("withException", false);
                args.addVarargs("dimensions", int.class, StampFactory.forKind(JavaKind.Int), dims);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class NewMultiArrayWithExceptionLowering implements NodeLoweringProvider<NewMultiArrayWithExceptionNode> {
            @Override
            public void lower(NewMultiArrayWithExceptionNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }

                int rank = node.dimensionCount();
                ValueNode[] dims = new ValueNode[rank];
                for (int i = 0; i < node.dimensionCount(); i++) {
                    dims[i] = node.dimension(i);
                }

                SharedType type = (SharedType) node.type();
                ConstantNode hubConstant = ConstantNode.forConstant(snippetReflection.forObject(type.getHub()), tool.getMetaAccess(), graph);

                Arguments args = new Arguments(newmultiarray, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", hubConstant);
                args.addConst("rank", rank);
                args.addConst("withException", true);
                args.addVarargs("dimensions", int.class, StampFactory.forKind(JavaKind.Int), dims);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class DynamicNewInstanceLowering implements NodeLoweringProvider<DynamicNewInstanceNode> {
            @Override
            public void lower(DynamicNewInstanceNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }

                Arguments args = new Arguments(allocateInstanceDynamic, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", node.getInstanceType());
                args.addConst("fillContents", FillContent.fromBoolean(node.fillContents()));
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("profilingData", getProfilingData(node, null));
                args.addConst("withException", false);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class DynamicNewInstanceWithExceptionLowering implements NodeLoweringProvider<DynamicNewInstanceWithExceptionNode> {
            @Override
            public void lower(DynamicNewInstanceWithExceptionNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }

                Arguments args = new Arguments(allocateInstanceDynamic, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", node.getInstanceType());
                args.addConst("fillContents", FillContent.fromBoolean(true));
                args.addConst("emitMemoryBarrier", true/* barriers */);
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("profilingData", getProfilingData(node, null));
                args.addConst("withException", true);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class DynamicNewArrayLowering implements NodeLoweringProvider<DynamicNewArrayNode> {
            @Override
            public void lower(DynamicNewArrayNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }

                Arguments args = new Arguments(allocateArrayDynamic, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("elementType", node.getElementType());
                args.add("length", node.length());
                args.addConst("fillContents", FillContent.fromBoolean(node.fillContents()));
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("withException", false);
                args.addConst("profilingData", getProfilingData(node, null));

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class DynamicNewArrayWithExceptionLowering implements NodeLoweringProvider<DynamicNewArrayWithExceptionNode> {
            @Override
            public void lower(DynamicNewArrayWithExceptionNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }

                Arguments args = new Arguments(allocateArrayDynamic, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("elementType", node.getElementType());
                args.add("length", node.length());
                args.addConst("fillContents", FillContent.fromBoolean(true));
                args.addConst("emitMemoryBarrier", true/* barriers */);
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("withException", true);
                args.addConst("profilingData", getProfilingData(node, null));

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class ValidateNewInstanceClassLowering implements NodeLoweringProvider<ValidateNewInstanceClassNode> {
            @Override
            public void lower(ValidateNewInstanceClassNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();

                Arguments args = new Arguments(validateNewInstanceClass, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", node.getInstanceType());

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private class NewPodInstanceLowering implements NodeLoweringProvider<NewPodInstanceNode> {
            @Override
            public void lower(NewPodInstanceNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage() != GraphState.GuardsStage.AFTER_FSA) {
                    return;
                }

                assert node.getKnownInstanceType() == null || (node.getHub().isConstant() &&
                                tool.getConstantReflection().asJavaType(node.getHub().asConstant()).equals(node.getKnownInstanceType()));
                assert node.fillContents() : "fillContents must be true for hybrid allocations";

                Arguments args = new Arguments(allocatePod, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", node.getHub());
                args.add("arrayLength", node.getArrayLength());
                args.add("referenceMap", node.getReferenceMap());
                args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
                args.addConst("maybeUnroll", node.getArrayLength().isConstant());
                args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
                args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.addConst("profilingData", getProfilingData(node, node.getKnownInstanceType()));

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
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
