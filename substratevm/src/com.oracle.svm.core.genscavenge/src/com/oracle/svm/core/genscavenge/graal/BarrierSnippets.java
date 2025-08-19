/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_FREQUENT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.NOT_LIKELY_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.nodes.memory.address.AddressNode.Address;

import java.util.Map;

import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.SerialGCOptions;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.core.common.GraalOptions;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.nodes.BreakpointNode;
import jdk.graal.compiler.nodes.NamedLocationIdentity;
import jdk.graal.compiler.nodes.extended.FixedValueAnchorNode;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;
import jdk.graal.compiler.nodes.gc.SerialArrayRangeWriteBarrierNode;
import jdk.graal.compiler.nodes.gc.SerialWriteBarrierNode;
import jdk.graal.compiler.nodes.gc.WriteBarrierNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.nodes.type.StampTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.graal.compiler.replacements.gc.WriteBarrierSnippets;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Instances and hybrid objects like pods (in contrast to arrays) are always in aligned chunks,
 * except for {@link StoredContinuation} objects, but these are immutable and do not need barriers.
 *
 * Imprecise card marking means that the card corresponding to the object start is dirtied. This is
 * used for all objects in aligned chunks.
 *
 * Precise card marking means that the card corresponding to the actual write address is dirtied.
 * This is only used for object arrays in unaligned chunks.
 */
public class BarrierSnippets extends SubstrateTemplates implements Snippets {
    /** A LocationIdentity to distinguish card locations from other locations. */
    public static final LocationIdentity CARD_REMEMBERED_SET_LOCATION = NamedLocationIdentity.mutable("CardRememberedSet");

    private static final SnippetRuntime.SubstrateForeignCallDescriptor POST_WRITE_BARRIER = SnippetRuntime.findForeignCall(BarrierSnippets.class, "postWriteBarrierStub",
                    NO_SIDE_EFFECT, CARD_REMEMBERED_SET_LOCATION);
    private static final SnippetRuntime.SubstrateForeignCallDescriptor ARRAY_RANGE_POST_WRITE_BARRIER = SnippetRuntime.findForeignCall(BarrierSnippets.class, "arrayRangePostWriteBarrierStub",
                    NO_SIDE_EFFECT, CARD_REMEMBERED_SET_LOCATION);

    private final SnippetInfo postWriteBarrierSnippet;
    private final SnippetInfo arrayRangePostWriteBarrierSnippet;

    @SuppressWarnings("this-escape")
    public BarrierSnippets(OptionValues options, Providers providers) {
        super(options, providers);

        this.postWriteBarrierSnippet = snippet(providers, BarrierSnippets.class, "postWriteBarrierSnippet", CARD_REMEMBERED_SET_LOCATION);
        this.arrayRangePostWriteBarrierSnippet = snippet(providers, BarrierSnippets.class, "arrayRangePostWriteBarrierSnippet", CARD_REMEMBERED_SET_LOCATION);
    }

    public void registerLowerings(MetaAccessProvider metaAccess, Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        PostWriteBarrierLowering postWriteBarrierLowering = new PostWriteBarrierLowering(metaAccess);
        lowerings.put(SerialWriteBarrierNode.class, postWriteBarrierLowering);

        ArrayRangePostWriteBarrierLowering arrayRangePostWriteBarrierLowering = new ArrayRangePostWriteBarrierLowering(metaAccess);
        lowerings.put(SerialArrayRangeWriteBarrierNode.class, arrayRangePostWriteBarrierLowering);
    }

    public static void registerForeignCalls(SubstrateForeignCallsProvider provider) {
        provider.register(POST_WRITE_BARRIER);
        provider.register(ARRAY_RANGE_POST_WRITE_BARRIER);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void postWriteBarrierStub(Object object, Pointer address) {
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        UnsignedWord objectHeader = oh.readHeaderFromObject(object);
        if (ObjectHeaderImpl.isUnalignedHeader(objectHeader)) {
            RememberedSet.get().dirtyCardForUnalignedObject(object, address, false);
        } else {
            RememberedSet.get().dirtyCardForAlignedObject(object, false);
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static void arrayRangePostWriteBarrierStub(Object object, Pointer startAddress, Pointer endAddress) {
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        UnsignedWord objectHeader = oh.readHeaderFromObject(object);
        if (ObjectHeaderImpl.isUnalignedHeader(objectHeader)) {
            RememberedSet.get().dirtyCardRangeForUnalignedObject(object, startAddress, endAddress);
        } else {
            // Arrays in aligned chunks are always marked imprecise.
            RememberedSet.get().dirtyCardForAlignedObject(object, false);
        }
    }

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void callPostWriteBarrierStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object, Pointer address);

    @NodeIntrinsic(ForeignCallNode.class)
    private static native void callArrayRangePostWriteBarrierStub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Object object, Pointer startAddress, Pointer endAddress);

    @Snippet
    public static void postWriteBarrierSnippet(Object object, Address address, @ConstantParameter boolean shouldOutline, @ConstantParameter boolean precise, @ConstantParameter boolean eliminated) {
        boolean shouldVerify = SerialGCOptions.VerifyWriteBarriers.getValue();
        if (!shouldVerify && eliminated) {
            return;
        }

        Object fixedObject = FixedValueAnchorNode.getObject(object);
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        UnsignedWord objectHeader = oh.readHeaderFromObject(fixedObject);

        if (shouldVerify && !precise) {
            /*
             * To increase verification coverage, we do the verification before checking if a
             * barrier is needed at all.
             */
            if (probability(SLOW_PATH_PROBABILITY, ObjectHeaderImpl.isUnalignedHeader(objectHeader))) {
                BreakpointNode.breakpoint();
            }
            if (probability(SLOW_PATH_PROBABILITY, fixedObject == null)) {
                BreakpointNode.breakpoint();
            }
        }

        boolean needsBarrier = RememberedSet.get().hasRememberedSet(objectHeader);
        if (probability(FREQUENT_PROBABILITY, !needsBarrier)) {
            return;
        }

        Word addr = Word.fromAddress(address);

        if (shouldOutline && !eliminated) {
            callPostWriteBarrierStub(POST_WRITE_BARRIER, fixedObject, addr);
            return;
        }

        if (precise) {
            boolean unaligned = ObjectHeaderImpl.isUnalignedHeader(objectHeader);
            if (probability(NOT_LIKELY_PROBABILITY, unaligned)) {
                RememberedSet.get().dirtyCardForUnalignedObject(fixedObject, addr, eliminated);
                return;
            }
        }

        RememberedSet.get().dirtyCardForAlignedObject(fixedObject, eliminated);

    }

    @Snippet
    public static void arrayRangePostWriteBarrierSnippet(Object object, Address address, long length, @ConstantParameter int elementStride, @ConstantParameter boolean shouldOutline) {
        if (probability(NOT_FREQUENT_PROBABILITY, length == 0)) {
            return;
        }

        Object fixedObject = FixedValueAnchorNode.getObject(object);
        ObjectHeader oh = Heap.getHeap().getObjectHeader();
        UnsignedWord objectHeader = oh.readHeaderFromObject(fixedObject);

        boolean needsBarrier = RememberedSet.get().hasRememberedSet(objectHeader);
        if (probability(FREQUENT_PROBABILITY, !needsBarrier)) {
            return;
        }

        Word addr = Word.fromAddress(address);
        Word startAddress = WriteBarrierSnippets.getPointerToFirstArrayElement(addr, length, elementStride);
        Word endAddress = WriteBarrierSnippets.getPointerToLastArrayElement(addr, length, elementStride);

        if (shouldOutline) {
            callArrayRangePostWriteBarrierStub(ARRAY_RANGE_POST_WRITE_BARRIER, fixedObject, startAddress, endAddress);
            return;
        }

        boolean unaligned = ObjectHeaderImpl.isUnalignedHeader(objectHeader);
        if (probability(NOT_LIKELY_PROBABILITY, unaligned)) {
            RememberedSet.get().dirtyCardRangeForUnalignedObject(fixedObject, startAddress, endAddress);
            return;
        }

        RememberedSet.get().dirtyCardForAlignedObject(fixedObject, false);
    }

    private class PostWriteBarrierLowering implements NodeLoweringProvider<SerialWriteBarrierNode> {
        private final ResolvedJavaType storedContinuationType;

        PostWriteBarrierLowering(MetaAccessProvider metaAccess) {
            storedContinuationType = metaAccess.lookupJavaType(StoredContinuation.class);
        }

        @Override
        public void lower(SerialWriteBarrierNode barrier, LoweringTool tool) {
            Arguments args = new Arguments(postWriteBarrierSnippet, barrier.graph(), tool.getLoweringStage());
            OffsetAddressNode address = (OffsetAddressNode) barrier.getAddress();

            ResolvedJavaType baseType = StampTool.typeOrNull(address.getBase());
            assert baseType == null || !storedContinuationType.isAssignableFrom(baseType) : "StoredContinuation should be effectively immutable and references only be written by GC";

            args.add("object", address.getBase());
            args.add("address", address);
            args.add("shouldOutline", shouldOutline(barrier));
            args.add("precise", barrier.usePrecise());
            args.add("eliminated", tryEliminate(barrier));

            template(tool, barrier, args).instantiate(tool.getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    private class ArrayRangePostWriteBarrierLowering implements NodeLoweringProvider<SerialArrayRangeWriteBarrierNode> {
        private final ResolvedJavaType storedContinuationType;

        ArrayRangePostWriteBarrierLowering(MetaAccessProvider metaAccessProvider) {
            storedContinuationType = metaAccessProvider.lookupJavaType(StoredContinuation.class);
        }

        @Override
        public void lower(SerialArrayRangeWriteBarrierNode barrier, LoweringTool tool) {
            Arguments args = new Arguments(arrayRangePostWriteBarrierSnippet, barrier.graph(), tool.getLoweringStage());
            OffsetAddressNode address = (OffsetAddressNode) barrier.getAddress();

            ResolvedJavaType baseType = StampTool.typeOrNull(address.getBase());
            assert baseType == null || !storedContinuationType.isAssignableFrom(baseType) : "StoredContinuation should be effectively immutable and references only be written by GC";

            args.add("object", address.getBase());
            args.add("address", address);
            args.add("length", barrier.getLengthAsLong());
            args.add("elementStride", barrier.getElementStride());
            args.add("shouldOutline", shouldOutline(barrier));

            template(tool, barrier, args).instantiate(tool.getMetaAccess(), barrier, SnippetTemplate.DEFAULT_REPLACER, args);
        }

    }

    private static boolean shouldOutline(WriteBarrierNode barrier) {
        if (SerialGCOptions.OutlineWriteBarriers.getValue() != null) {
            return SerialGCOptions.OutlineWriteBarriers.getValue();
        }
        if (GraalOptions.ReduceCodeSize.getValue(barrier.getOptions())) {
            return true;
        }
        // Newly allocated objects are likely young, so we can outline the execution after
        // checking hasRememberedSet
        return barrier instanceof SerialWriteBarrierNode serialBarrier && serialBarrier.getBaseStatus().likelyYoung();
    }

    private static boolean tryEliminate(WriteBarrierNode barrier) {
        if (barrier instanceof SerialWriteBarrierNode serialBarrier) {
            return serialBarrier.isEliminated() || serialBarrier.getBaseStatus() == SerialWriteBarrierNode.BaseStatus.NO_LOOP_OR_SAFEPOINT;
        }
        return false;
    }

}
