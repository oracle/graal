/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.configure.ConfigurationFile;
import com.oracle.svm.core.MissingRegistrationUtils;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.allocationprofile.AllocationCounter;
import com.oracle.svm.core.allocationprofile.AllocationSite;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.graal.meta.KnownOffsets;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.nodes.NewPodInstanceNode;
import com.oracle.svm.core.graal.nodes.NewStoredContinuationNode;
import com.oracle.svm.core.graal.nodes.SubstrateNewDynamicHubNode;
import com.oracle.svm.core.graal.nodes.SubstrateNewHybridInstanceNode;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.Pod;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.hub.RuntimeClassLoading;
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
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.spi.MetaAccessExtensionProvider;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.FixedNode;
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
    public static final LocationIdentity TLAB_START_IDENTITY = NamedLocationIdentity.mutable("TLAB.start");
    public static final LocationIdentity TLAB_TOP_IDENTITY = NamedLocationIdentity.mutable("TLAB.top");
    public static final LocationIdentity TLAB_END_IDENTITY = NamedLocationIdentity.mutable("TLAB.end");
    public static final Object[] ALLOCATION_LOCATIONS = new Object[]{TLAB_START_IDENTITY, TLAB_TOP_IDENTITY, TLAB_END_IDENTITY, IdentityHashCodeSupport.IDENTITY_HASHCODE_SALT_LOCATION,
                    AllocationCounter.COUNT_FIELD, AllocationCounter.SIZE_FIELD};
    public static final LocationIdentity[] GC_LOCATIONS = new LocationIdentity[]{TLAB_START_IDENTITY, TLAB_TOP_IDENTITY, TLAB_END_IDENTITY, IdentityHashCodeSupport.IDENTITY_HASHCODE_SALT_LOCATION};

    private static final SubstrateForeignCallDescriptor NEW_MULTI_ARRAY = SnippetRuntime.findForeignCall(SubstrateAllocationSnippets.class, "newMultiArrayStub", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor SLOW_PATH_HUB_OR_UNSAFE_INSTANTIATE_ERROR = SnippetRuntime.findForeignCall(SubstrateAllocationSnippets.class,
                    "slowPathHubOrUnsafeInstantiationError", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor ARRAY_HUB_ERROR = SnippetRuntime.findForeignCall(SubstrateAllocationSnippets.class, "arrayHubErrorStub", NO_SIDE_EFFECT);
    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{NEW_MULTI_ARRAY, SLOW_PATH_HUB_OR_UNSAFE_INSTANTIATE_ERROR, ARRAY_HUB_ERROR};

    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(FOREIGN_CALLS);
    }

    @Snippet
    protected Object allocateInstance(@NonNullParameter DynamicHub hub,
                    @ConstantParameter long size,
                    @ConstantParameter boolean forceSlowPath,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter AllocationProfilingData profilingData,
                    @ConstantParameter boolean withException) {
        Object result = allocateInstanceImpl(encodeAsTLABObjectHeader(hub), Word.unsigned(size), forceSlowPath, fillContents, emitMemoryBarrier, true, profilingData, withException);
        return piCastToSnippetReplaceeStamp(result);
    }

    @Snippet
    protected Object allocateInstanceConstantHeader(long objectHeader,
                    @ConstantParameter long size,
                    @ConstantParameter boolean forceSlowPath,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter AllocationProfilingData profilingData,
                    @ConstantParameter boolean withException) {
        Object result = allocateInstanceImpl(Word.unsigned(objectHeader), Word.unsigned(size), forceSlowPath, fillContents, emitMemoryBarrier, true, profilingData, withException);
        return piCastToSnippetReplaceeStamp(result);
    }

    @Snippet
    public Object allocateArray(@NonNullParameter DynamicHub hub,
                    int length,
                    @ConstantParameter boolean forceSlowPath,
                    @ConstantParameter int arrayBaseOffset,
                    @ConstantParameter int log2ElementSize,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean maybeUnroll,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter AllocationProfilingData profilingData,
                    @ConstantParameter boolean withException) {
        Object result = allocateArrayImpl(encodeAsTLABObjectHeader(hub), length, forceSlowPath, arrayBaseOffset, log2ElementSize, fillContents,
                        afterArrayLengthOffset(), emitMemoryBarrier, maybeUnroll, supportsBulkZeroing, supportsOptimizedFilling, profilingData, withException);
        return piArrayCastToSnippetReplaceeStamp(result, length);
    }

    @Snippet
    public Object allocateArrayConstantHeader(long objectHeader,
                    int length,
                    @ConstantParameter boolean forceSlowPath,
                    @ConstantParameter int arrayBaseOffset,
                    @ConstantParameter int log2ElementSize,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean maybeUnroll,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter AllocationProfilingData profilingData,
                    @ConstantParameter boolean withException) {
        Object result = allocateArrayImpl(Word.unsigned(objectHeader), length, forceSlowPath, arrayBaseOffset, log2ElementSize, fillContents,
                        afterArrayLengthOffset(), emitMemoryBarrier, maybeUnroll, supportsBulkZeroing, supportsOptimizedFilling, profilingData, withException);
        return piArrayCastToSnippetReplaceeStamp(result, length);
    }

    @Snippet
    public Object allocateStoredContinuation(@NonNullParameter DynamicHub hub,
                    int length,
                    @ConstantParameter boolean forceSlowPath,
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
        if (!forceSlowPath && useTLAB() && probability(FAST_PATH_PROBABILITY, shouldAllocateInTLAB(allocationSize, true)) && probability(FAST_PATH_PROBABILITY, newTop.belowOrEqual(end))) {
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
                    @ConstantParameter boolean forceSlowPath,
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
        if (!forceSlowPath && useTLAB() && probability(FAST_PATH_PROBABILITY, shouldAllocateInTLAB(allocationSize, true)) && probability(FAST_PATH_PROBABILITY, newTop.belowOrEqual(end))) {
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
    public Object allocateDynamicHub(int vTableEntries,
                    @ConstantParameter int vTableBaseOffset,
                    @ConstantParameter int log2VTableEntrySize,
                    @ConstantParameter AllocationProfilingData profilingData) {
        profilingData.snippetCounters.stub.inc();

        // always slow path, because DynamicHubs are allocated into dedicated chunks
        Object result = callNewDynamicHub(gcAllocationSupport().getNewDynamicHub(), vTableEntries);

        UnsignedWord allocationSize = arrayAllocationSize(vTableEntries, vTableBaseOffset, log2VTableEntrySize);
        profileAllocation(profilingData, allocationSize);

        return piArrayCastToSnippetReplaceeStamp(verifyOop(result), vTableEntries);
    }

    @Snippet
    public Object allocateInstanceDynamic(@NonNullParameter DynamicHub hub,
                    @ConstantParameter boolean forceSlowPath,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter AllocationProfilingData profilingData,
                    @ConstantParameter boolean withException) {
        return allocateInstanceDynamicImpl(hub, forceSlowPath, fillContents, emitMemoryBarrier, supportsBulkZeroing, supportsOptimizedFilling, profilingData, withException);
    }

    protected Object allocateInstanceDynamicImpl(DynamicHub hub, boolean forceSlowPath, FillContent fillContents, boolean emitMemoryBarrier, @SuppressWarnings("unused") boolean supportsBulkZeroing,
                    @SuppressWarnings("unused") boolean supportsOptimizedFilling, AllocationProfilingData profilingData, boolean withException) {
        // The hub was already verified by a ValidateNewInstanceClassNode.
        UnsignedWord size = LayoutEncoding.getPureInstanceAllocationSize(hub.getLayoutEncoding());
        Object result = allocateInstanceImpl(encodeAsTLABObjectHeader(hub), size, forceSlowPath, fillContents, emitMemoryBarrier, false, profilingData, withException);
        return piCastToSnippetReplaceeStamp(result);
    }

    @Snippet
    public Object allocateArrayDynamic(DynamicHub elementType,
                    int length,
                    @ConstantParameter boolean forceSlowPath,
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

        Object result = allocateArrayImpl(encodeAsTLABObjectHeader(checkedArrayHub), length, forceSlowPath, arrayBaseOffset, log2ElementSize, fillContents,
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
            if (probability(EXTREMELY_FAST_PATH_PROBABILITY, nonNullHub.canUnsafeInstantiateAsInstanceFastPath())) {
                return nonNullHub;
            }
        }
        DynamicHub slowPathStub = slowPathHubOrUnsafeInstantiationErrorStub(SLOW_PATH_HUB_OR_UNSAFE_INSTANTIATE_ERROR, DynamicHub.toClass(hub));
        return (DynamicHub) PiNode.piCastNonNull(slowPathStub, SnippetAnchorNode.anchor());
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
    private static native DynamicHub slowPathHubOrUnsafeInstantiationErrorStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> hub);

    /** Foreign call: {@link #SLOW_PATH_HUB_OR_UNSAFE_INSTANTIATE_ERROR}. */
    @SubstrateForeignCallTarget(stubCallingConvention = true)
    private static DynamicHub slowPathHubOrUnsafeInstantiationError(DynamicHub hub) throws InstantiationException {
        if (hub == null) {
            throw new NullPointerException("Allocation type is null.");
        } else if (!hub.isInstanceClass() || LayoutEncoding.isSpecial(hub.getLayoutEncoding())) {
            throw new InstantiationException("Can only allocate instance objects for concrete classes: " + DynamicHub.toClass(hub).getTypeName());
        } else if (LayoutEncoding.isHybrid(hub.getLayoutEncoding())) {
            throw new InstantiationException("Cannot allocate objects of special hybrid types: " + DynamicHub.toClass(hub).getTypeName());
        } else {
            if (hub.canUnsafeInstantiateAsInstanceSlowPath()) {
                hub.setCanUnsafeAllocate();
                return hub;
            } else {
                if (MissingRegistrationUtils.throwMissingRegistrationErrors()) {
                    MissingReflectionRegistrationUtils.reportUnsafeAllocation(DynamicHub.toClass(hub));
                }
                throw new IllegalArgumentException("Type " + DynamicHub.toClass(hub).getTypeName() + " is instantiated reflectively but was never registered." +
                                " Register the type by adding \"unsafeAllocated\" for the type in " + ConfigurationFile.REFLECTION.getFileName() + ".");
            }
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
            throw MissingReflectionRegistrationUtils.reportArrayInstantiation(DynamicHub.toClass(elementType), 1);
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
        memory.writeWord(Word.unsigned(ipOffset), Word.nullPointer(), LocationIdentity.init());
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
        return SubstrateOptions.getAllocatePrefetchStyle();
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
    private static native Object callNewDynamicHub(@ConstantNodeParameter ForeignCallDescriptor descriptor, int vTableEntries);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callNewMultiArray(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub, int rank, Word dimensions);

    @NodeIntrinsic(value = ForeignCallWithExceptionNode.class)
    private static native Object callNewMultiArrayWithException(@ConstantNodeParameter ForeignCallDescriptor descriptor, Word hub, int rank, Word dimensions);

    @Override
    public void initializeObjectHeader(Word memory, Word objectHeader, boolean isArrayLike) {
        Heap.getHeap().getObjectHeader().initializeHeaderOfNewObjectInit(memory, objectHeader, isArrayLike);
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
        private final SnippetInfo allocateInstanceConstantHeader;
        private final SnippetInfo allocateArray;
        private final SnippetInfo allocateArrayConstantHeader;
        private final SnippetInfo newmultiarray;

        private final SnippetInfo allocateArrayDynamic;
        private final SnippetInfo allocateInstanceDynamic;

        private final SnippetInfo validateNewInstanceClass;

        private final SnippetInfo allocateStoredContinuation;
        private final SnippetInfo allocatePod;
        private final SnippetInfo allocateDynamicHub;

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
            allocateInstanceConstantHeader = snippet(providers,
                            SubstrateAllocationSnippets.class,
                            "allocateInstanceConstantHeader",
                            null,
                            receiver,
                            ALLOCATION_LOCATIONS);
            allocateArray = snippet(providers,
                            SubstrateAllocationSnippets.class,
                            "allocateArray",
                            null,
                            receiver,
                            ALLOCATION_LOCATIONS);
            allocateArrayConstantHeader = snippet(providers,
                            SubstrateAllocationSnippets.class,
                            "allocateArrayConstantHeader",
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

            SnippetInfo allocateDynamicHubSnippet = null;
            if (RuntimeClassLoading.isSupported()) {
                allocateDynamicHubSnippet = snippet(providers,
                                SubstrateAllocationSnippets.class,
                                "allocateDynamicHub",
                                null,
                                receiver,
                                ALLOCATION_LOCATIONS);
            }
            allocateDynamicHub = allocateDynamicHubSnippet;
        }

        public void registerLowering(Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
            lowerings.put(NewInstanceNode.class, new NewInstanceLowering());
            lowerings.put(NewInstanceWithExceptionNode.class, new NewInstanceLowering());

            lowerings.put(SubstrateNewHybridInstanceNode.class, new NewHybridInstanceLowering());

            lowerings.put(NewArrayNode.class, new NewArrayLowering());
            lowerings.put(NewArrayWithExceptionNode.class, new NewArrayLowering());

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
            if (RuntimeClassLoading.isSupported()) {
                lowerings.put(SubstrateNewDynamicHubNode.class, new NewDynamicHubLowering());
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

        private static boolean shouldForceSlowPath(StructuredGraph graph) {
            return GraalOptions.ReduceCodeSize.getValue(graph.getOptions());
        }

        private final class NewInstanceLowering implements NodeLoweringProvider<FixedNode> {
            @Override
            public void lower(FixedNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
                    return;
                }

                SharedType type;
                boolean fillContents;
                boolean emitMemoryBarrier;
                boolean withException;
                if (node instanceof NewInstanceNode n) {
                    type = (SharedType) n.instanceClass();
                    fillContents = n.fillContents();
                    emitMemoryBarrier = n.emitMemoryBarrier();
                    withException = false;
                } else if (node instanceof NewInstanceWithExceptionNode n) {
                    type = (SharedType) n.instanceClass();
                    fillContents = n.fillContents();
                    emitMemoryBarrier = n.emitMemoryBarrier();
                    withException = true;
                } else {
                    throw VMError.shouldNotReachHereUnexpectedInput(node);
                }

                DynamicHub hub = ensureMarkedAsInstantiated(type.getHub());
                long size = LayoutEncoding.getPureInstanceAllocationSize(hub.getLayoutEncoding()).rawValue();
                Arguments args;

                ValueNode objectHeaderConstant = snippetReflection.forTLABObjectHeader(hub, graph);
                if (objectHeaderConstant != null) {
                    args = new Arguments(allocateInstanceConstantHeader, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("objectHeader", objectHeaderConstant);
                } else {
                    ConstantNode hubConstant = ConstantNode.forConstant(snippetReflection.forObject(hub), tool.getMetaAccess(), graph);
                    args = new Arguments(allocateInstance, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("hub", hubConstant);
                }

                args.add("size", size);
                args.add("forceSlowPath", shouldForceSlowPath(graph));
                args.add("fillContents", FillContent.fromBoolean(fillContents));
                args.add("emitMemoryBarrier", emitMemoryBarrier);
                args.add("profilingData", getProfilingData(node, type));
                args.add("withException", withException);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
            }
        }

        private final class NewHybridInstanceLowering implements NodeLoweringProvider<SubstrateNewHybridInstanceNode> {
            @Override
            public void lower(SubstrateNewHybridInstanceNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
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
                args.add("forceSlowPath", shouldForceSlowPath(graph));
                args.add("arrayBaseOffset", arrayBaseOffset);
                args.add("log2ElementSize", log2ElementSize);
                args.add("fillContents", FillContent.fromBoolean(fillContents));
                args.add("emitMemoryBarrier", node.emitMemoryBarrier());
                args.add("maybeUnroll", length.isConstant());
                args.add("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroingOfEden());
                args.add("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.add("profilingData", getProfilingData(node, instanceClass));
                args.add("withException", false);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private final class NewStoredContinuationLowering implements NodeLoweringProvider<NewStoredContinuationNode> {
            @Override
            public void lower(NewStoredContinuationNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
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
                args.add("forceSlowPath", shouldForceSlowPath(graph));
                args.add("arrayBaseOffset", arrayBaseOffset);
                args.add("log2ElementSize", log2ElementSize);
                args.add("ipOffset", ContinuationSupport.singleton().getIPOffset());
                args.add("emitMemoryBarrier", node.emitMemoryBarrier());
                args.add("profilingData", getProfilingData(node, instanceClass));

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private final class NewArrayLowering implements NodeLoweringProvider<FixedNode> {
            @Override
            public void lower(FixedNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
                    return;
                }

                ValueNode length;
                SharedType type;
                boolean fillContents;
                boolean emitMemoryBarrier;
                boolean withException;
                if (node instanceof NewArrayNode n) {
                    length = n.length();
                    type = (SharedType) n.elementType().getArrayClass();
                    fillContents = n.fillContents();
                    emitMemoryBarrier = n.emitMemoryBarrier();
                    withException = false;
                } else if (node instanceof NewArrayWithExceptionNode n) {
                    length = n.length();
                    type = (SharedType) n.elementType().getArrayClass();
                    fillContents = n.fillContents();
                    emitMemoryBarrier = n.emitMemoryBarrier();
                    withException = true;
                } else {
                    throw VMError.shouldNotReachHereUnexpectedInput(node);
                }

                DynamicHub hub = ensureMarkedAsInstantiated(type.getHub());
                int layoutEncoding = hub.getLayoutEncoding();
                int arrayBaseOffset = getArrayBaseOffset(layoutEncoding);
                int log2ElementSize = LayoutEncoding.getArrayIndexShift(layoutEncoding);
                Arguments args;

                ValueNode objectHeaderConstant = snippetReflection.forTLABObjectHeader(hub, graph);
                if (objectHeaderConstant != null) {
                    args = new Arguments(allocateArrayConstantHeader, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("objectHeader", objectHeaderConstant);
                } else {
                    ConstantNode hubConstant = ConstantNode.forConstant(snippetReflection.forObject(hub), tool.getMetaAccess(), graph);
                    args = new Arguments(allocateArray, graph.getGuardsStage(), tool.getLoweringStage());
                    args.add("hub", hubConstant);
                }

                args.add("length", length.isAlive() ? length : graph.addOrUniqueWithInputs(length));
                args.add("forceSlowPath", shouldForceSlowPath(graph));
                args.add("arrayBaseOffset", arrayBaseOffset);
                args.add("log2ElementSize", log2ElementSize);
                args.add("fillContents", FillContent.fromBoolean(fillContents));
                args.add("emitMemoryBarrier", emitMemoryBarrier);
                args.add("maybeUnroll", length.isConstant());
                args.add("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroingOfEden());
                args.add("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.add("profilingData", getProfilingData(node, type));
                args.add("withException", withException);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private final class NewMultiArrayLowering implements NodeLoweringProvider<NewMultiArrayNode> {
            @Override
            public void lower(NewMultiArrayNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
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
                args.add("rank", rank);
                args.add("withException", false);
                args.addVarargs("dimensions", int.class, StampFactory.forKind(JavaKind.Int), dims);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private final class NewMultiArrayWithExceptionLowering implements NodeLoweringProvider<NewMultiArrayWithExceptionNode> {
            @Override
            public void lower(NewMultiArrayWithExceptionNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
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
                args.add("rank", rank);
                args.add("withException", true);
                args.addVarargs("dimensions", int.class, StampFactory.forKind(JavaKind.Int), dims);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private final class DynamicNewInstanceLowering implements NodeLoweringProvider<DynamicNewInstanceNode> {
            @Override
            public void lower(DynamicNewInstanceNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
                    return;
                }

                Arguments args = new Arguments(allocateInstanceDynamic, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", node.getInstanceType());
                args.add("forceSlowPath", shouldForceSlowPath(graph));
                args.add("fillContents", FillContent.fromBoolean(node.fillContents()));
                args.add("emitMemoryBarrier", node.emitMemoryBarrier());
                args.add("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroingOfEden());
                args.add("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.add("profilingData", getProfilingData(node, null));
                args.add("withException", false);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private final class DynamicNewInstanceWithExceptionLowering implements NodeLoweringProvider<DynamicNewInstanceWithExceptionNode> {
            @Override
            public void lower(DynamicNewInstanceWithExceptionNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
                    return;
                }

                Arguments args = new Arguments(allocateInstanceDynamic, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", node.getInstanceType());
                args.add("forceSlowPath", shouldForceSlowPath(graph));
                args.add("fillContents", FillContent.fromBoolean(true));
                args.add("emitMemoryBarrier", true/* barriers */);
                args.add("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroingOfEden());
                args.add("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.add("profilingData", getProfilingData(node, null));
                args.add("withException", true);

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private final class DynamicNewArrayLowering implements NodeLoweringProvider<DynamicNewArrayNode> {
            @Override
            public void lower(DynamicNewArrayNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
                    return;
                }

                Arguments args = new Arguments(allocateArrayDynamic, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("elementType", node.getElementType());
                args.add("length", node.length());
                args.add("forceSlowPath", shouldForceSlowPath(graph));
                args.add("fillContents", FillContent.fromBoolean(node.fillContents()));
                args.add("emitMemoryBarrier", node.emitMemoryBarrier());
                args.add("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroingOfEden());
                args.add("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.add("withException", false);
                args.add("profilingData", getProfilingData(node, null));

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private final class DynamicNewArrayWithExceptionLowering implements NodeLoweringProvider<DynamicNewArrayWithExceptionNode> {
            @Override
            public void lower(DynamicNewArrayWithExceptionNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
                    return;
                }

                Arguments args = new Arguments(allocateArrayDynamic, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("elementType", node.getElementType());
                args.add("length", node.length());
                args.add("forceSlowPath", shouldForceSlowPath(graph));
                args.add("fillContents", FillContent.fromBoolean(true));
                args.add("emitMemoryBarrier", true/* barriers */);
                args.add("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroingOfEden());
                args.add("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.add("withException", true);
                args.add("profilingData", getProfilingData(node, null));

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private final class ValidateNewInstanceClassLowering implements NodeLoweringProvider<ValidateNewInstanceClassNode> {
            @Override
            public void lower(ValidateNewInstanceClassNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();

                Arguments args = new Arguments(validateNewInstanceClass, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", node.getInstanceType());

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private final class NewPodInstanceLowering implements NodeLoweringProvider<NewPodInstanceNode> {
            @Override
            public void lower(NewPodInstanceNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
                    return;
                }

                assert node.getKnownInstanceType() == null || (node.getHub().isConstant() &&
                                tool.getConstantReflection().asJavaType(node.getHub().asConstant()).equals(node.getKnownInstanceType()));
                assert node.fillContents() : "fillContents must be true for hybrid allocations";

                Arguments args = new Arguments(allocatePod, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("hub", node.getHub());
                args.add("arrayLength", node.getArrayLength());
                args.add("forceSlowPath", shouldForceSlowPath(graph));
                args.add("referenceMap", node.getReferenceMap());
                args.add("emitMemoryBarrier", node.emitMemoryBarrier());
                args.add("maybeUnroll", node.getArrayLength().isConstant());
                args.add("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroingOfEden());
                args.add("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(graph.getOptions()));
                args.add("profilingData", getProfilingData(node, node.getKnownInstanceType()));

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
            }
        }

        private final class NewDynamicHubLowering implements NodeLoweringProvider<SubstrateNewDynamicHubNode> {
            @Override
            public void lower(SubstrateNewDynamicHubNode node, LoweringTool tool) {
                StructuredGraph graph = node.graph();
                if (graph.getGuardsStage().areFrameStatesAtSideEffects()) {
                    return;
                }

                assert node.getVTableEntries() != null;
                assert node.fillContents() : "fillContents must be true for DynamicHub allocations";

                ValueNode vTableEntries = node.getVTableEntries();
                SharedType type = (SharedType) tool.getMetaAccess().lookupJavaType(Class.class);
                DynamicHub hubOfDynamicHub = type.getHub();

                int layoutEncoding = hubOfDynamicHub.getLayoutEncoding();

                int vTableBaseOffset = getArrayBaseOffset(layoutEncoding);
                assert vTableBaseOffset == KnownOffsets.singleton().getVTableBaseOffset();

                int log2VTableEntrySize = LayoutEncoding.getArrayIndexShift(layoutEncoding);
                assert log2VTableEntrySize == NumUtil.unsignedLog2(KnownOffsets.singleton().getVTableEntrySize());

                Arguments args = new Arguments(allocateDynamicHub, graph.getGuardsStage(), tool.getLoweringStage());
                args.add("vTableEntries", vTableEntries);
                args.add("vTableBaseOffset", vTableBaseOffset);
                args.add("log2VTableEntrySize", log2VTableEntrySize);
                args.add("profilingData", getProfilingData(node, type));

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
