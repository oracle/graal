/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.compiler.api.replacements.Snippet.VarargsParameter;
import org.graalvm.compiler.api.replacements.SnippetReflectionProvider;
import org.graalvm.compiler.core.common.spi.ForeignCallDescriptor;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.debug.DebugHandlersFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.Node.ConstantNodeParameter;
import org.graalvm.compiler.graph.Node.NodeIntrinsic;
import org.graalvm.compiler.nodes.PiArrayNode;
import org.graalvm.compiler.nodes.PiNode;
import org.graalvm.compiler.nodes.PrefetchAllocateNode;
import org.graalvm.compiler.nodes.SnippetAnchorNode;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.extended.BranchProbabilityNode;
import org.graalvm.compiler.nodes.extended.FixedValueAnchorNode;
import org.graalvm.compiler.nodes.extended.ForeignCallNode;
import org.graalvm.compiler.nodes.java.DynamicNewArrayNode;
import org.graalvm.compiler.nodes.java.DynamicNewInstanceNode;
import org.graalvm.compiler.nodes.java.NewArrayNode;
import org.graalvm.compiler.nodes.java.NewInstanceNode;
import org.graalvm.compiler.nodes.java.NewMultiArrayNode;
import org.graalvm.compiler.nodes.memory.address.OffsetAddressNode;
import org.graalvm.compiler.nodes.spi.LoweringTool;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.util.Providers;
import org.graalvm.compiler.replacements.SnippetTemplate;
import org.graalvm.compiler.replacements.SnippetTemplate.Arguments;
import org.graalvm.compiler.replacements.SnippetTemplate.SnippetInfo;
import org.graalvm.compiler.replacements.Snippets;
import org.graalvm.compiler.replacements.nodes.ExplodeLoopNode;
import org.graalvm.compiler.word.BarrieredAccess;
import org.graalvm.compiler.word.ObjectAccess;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.word.LocationIdentity;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordBase;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.allocationprofile.AllocationCounter;
import com.oracle.svm.core.allocationprofile.AllocationSite;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.HeapPolicy;
import com.oracle.svm.core.genscavenge.ObjectHeaderImpl;
import com.oracle.svm.core.genscavenge.ThreadLocalAllocation;
import com.oracle.svm.core.graal.jdk.SubstrateArraysCopyOfNode;
import com.oracle.svm.core.graal.jdk.SubstrateObjectCloneNode;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallLinkage;
import com.oracle.svm.core.graal.nodes.DimensionsNode;
import com.oracle.svm.core.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.graal.nodes.SubstrateDynamicNewArrayNode;
import com.oracle.svm.core.graal.nodes.SubstrateDynamicNewInstanceNode;
import com.oracle.svm.core.graal.nodes.SubstrateNewArrayNode;
import com.oracle.svm.core.graal.nodes.SubstrateNewInstanceNode;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateTemplates;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.meta.SharedType;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

public final class AllocationSnippets extends SubstrateTemplates implements Snippets {

    public static final Object[] ALLOCATION_LOCATION_IDENTITIES = new Object[]{ThreadLocalAllocation.TOP_IDENTITY,
                    ThreadLocalAllocation.END_IDENTITY, AllocationCounter.COUNT_FIELD, AllocationCounter.SIZE_FIELD};

    private static final SubstrateForeignCallDescriptor CHECK_DYNAMIC_HUB = SnippetRuntime.findForeignCall(AllocationSnippets.class, "checkDynamicHub", true);
    private static final SubstrateForeignCallDescriptor CHECK_ARRAY_HUB = SnippetRuntime.findForeignCall(AllocationSnippets.class, "checkArrayHub", true);
    private static final SubstrateForeignCallDescriptor SLOW_NEW_INSTANCE = SnippetRuntime.findForeignCall(ThreadLocalAllocation.class, "slowPathNewInstance", true);
    private static final SubstrateForeignCallDescriptor SLOW_NEW_ARRAY = SnippetRuntime.findForeignCall(ThreadLocalAllocation.class, "slowPathNewArray", true);
    private static final SubstrateForeignCallDescriptor NEW_MULTI_ARRAY = SnippetRuntime.findForeignCall(AllocationSnippets.class, "newMultiArray", true);

    private static final SubstrateForeignCallDescriptor[] FOREIGN_CALLS = new SubstrateForeignCallDescriptor[]{CHECK_DYNAMIC_HUB, CHECK_ARRAY_HUB, SLOW_NEW_INSTANCE, SLOW_NEW_ARRAY, NEW_MULTI_ARRAY};

    public static Object newInstance(DynamicHub hub, int encoding, @ConstantParameter boolean constantSize, @ConstantParameter boolean fillContents, AllocationCounter counter) {
        checkHub(hub);

        UnsignedWord size = LayoutEncoding.getInstanceSize(encoding);
        profileAllocation(size, counter);

        Pointer memory = ThreadLocalAllocation.allocateMemory(ThreadLocalAllocation.regularTLAB.getAddress(), size);

        Object result;
        if (BranchProbabilityNode.probability(BranchProbabilityNode.FAST_PATH_PROBABILITY, memory.isNonNull())) {
            result = formatObjectImpl(memory, hub, size, constantSize, fillContents, false);
        } else {
            result = callSlowNewInstance(SLOW_NEW_INSTANCE, DynamicHub.toClass(hub));
        }

        return PiNode.piCastToSnippetReplaceeStamp(result);
    }

    private static void checkHub(DynamicHub hub) {
        if (BranchProbabilityNode.probability(BranchProbabilityNode.SLOW_PATH_PROBABILITY, hub == null || !hub.isInstantiated())) {
            callCheckDynamicHub(CHECK_DYNAMIC_HUB, DynamicHub.toClass(hub));
        }
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callCheckDynamicHub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> c);

    /* Must be computed during native image generation. */
    private static final String runtimeReflectionTypeName = RuntimeReflection.class.getTypeName();

    /** Foreign call: {@link #CHECK_DYNAMIC_HUB}. */
    @SubstrateForeignCallTarget
    private static void checkDynamicHub(DynamicHub hub) {
        if (hub == null) {
            throw new NullPointerException("Allocation type is null.");
        } else if (!hub.isInstantiated()) {
            throw new IllegalArgumentException("Class " + DynamicHub.toClass(hub).getTypeName() +
                            " is instantiated reflectively but was never registered. Register the class by using " + runtimeReflectionTypeName);
        }
    }

    private static DynamicHub getCheckedArrayHub(DynamicHub elementType) {
        DynamicHub arrayHub = elementType.getArrayHub();
        if (BranchProbabilityNode.probability(BranchProbabilityNode.SLOW_PATH_PROBABILITY, arrayHub == null || !arrayHub.isInstantiated())) {
            callCheckArrayHub(CHECK_ARRAY_HUB, DynamicHub.toClass(elementType));
        }
        return arrayHub;
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native void callCheckArrayHub(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> elementType);

    /** Foreign call: {@link #CHECK_DYNAMIC_HUB}. */
    @SubstrateForeignCallTarget
    private static void checkArrayHub(DynamicHub elementType) {
        throw new IllegalArgumentException("Class " + DynamicHub.toClass(elementType).getTypeName() + "[]" +
                        " is instantiated reflectively but was never registered. Register the class by using " + runtimeReflectionTypeName);
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callSlowNewInstance(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> c);

    @Snippet
    public static Object staticNewInstanceSnippet(DynamicHub hub, @ConstantParameter int encoding, @ConstantParameter boolean fillContents, AllocationCounter counter) {
        return newInstance(hub, encoding, true, fillContents, counter);
    }

    private static final InstantiationException INSTANTIATION_EXCEPTION = new InstantiationException("Cannot allocate instance.");

    @Snippet
    public static Object dynamicNewInstanceSnippet(DynamicHub hub, @ConstantParameter boolean fillContents, AllocationCounter counter) throws InstantiationException {
        if (!LayoutEncoding.isInstance(hub.getLayoutEncoding())) {
            throw INSTANTIATION_EXCEPTION;
        }
        return newInstance(hub, hub.getLayoutEncoding(), false, fillContents, counter);
    }

    private static void profileAllocation(UnsignedWord size, AllocationCounter counter) {
        if (AllocationSite.Options.AllocationProfiling.getValue()) {
            counter.incrementCount();
            counter.incrementSize(size.rawValue());
        }
    }

    @Snippet
    public static Object newArraySnippet(DynamicHub hub, int length, @ConstantParameter int layoutEncoding, @ConstantParameter boolean fillContents, AllocationCounter counter) {
        checkHub(hub);
        return fastNewArrayWithPiCast(hub, length, layoutEncoding, fillContents, counter);
    }

    private static Object fastNewArrayWithPiCast(DynamicHub hub, int length, @ConstantParameter int layoutEncoding, @ConstantParameter boolean fillContents, AllocationCounter counter) {
        Object result = fastNewArray(hub, length, layoutEncoding, fillContents, counter);
        return PiArrayNode.piArrayCastToSnippetReplaceeStamp(result, length);
    }

    public static Object fastNewArray(DynamicHub hub, int length, int layoutEncoding, boolean fillContents, AllocationCounter counter) {
        /*
         * Note: layoutEncoding is passed in as a @ConstantParameter so that much of the array size
         * computation can be folded away early when preparing the snippet.
         */
        UnsignedWord size = LayoutEncoding.getArraySize(layoutEncoding, length);
        profileAllocation(size, counter);

        Object result = fastNewArrayUninterruptibly(hub, length, layoutEncoding, fillContents, size);
        if (result == null) {
            result = callSlowNewArray(SLOW_NEW_ARRAY, DynamicHub.toClass(hub), length);
        }
        return result;
    }

    @Uninterruptible(reason = "Holds uninitialized memory from allocateMemory through formatArryImpl")
    private static Object fastNewArrayUninterruptibly(DynamicHub hub, int length, int layoutEncoding, boolean fillContents, UnsignedWord size) {
        Pointer memory = WordFactory.nullPointer();
        if (size.belowOrEqual(HeapPolicy.getLargeArrayThreshold()) && length >= 0) {
            memory = ThreadLocalAllocation.allocateMemory(ThreadLocalAllocation.regularTLAB.getAddress(), size);
        }

        Object result = null;
        if (memory.isNonNull()) {
            result = formatArrayImpl(memory, hub, length, layoutEncoding, size, fillContents, false, false);
        }
        return result;
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callSlowNewArray(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> c, int length);

    private static final IllegalArgumentException VOID_ARRAY = new IllegalArgumentException();

    @Snippet
    public static Object dynamicNewArraySnippet(DynamicHub elementType, int length, @ConstantParameter boolean fillContents, AllocationCounter counter) {
        if (elementType == DynamicHub.fromClass(void.class)) {
            throw VOID_ARRAY;
        }
        DynamicHub hub = getCheckedArrayHub(elementType);
        return fastNewArrayWithPiCast(hub, length, hub.getLayoutEncoding(), fillContents, counter);
    }

    @Snippet
    public static Object formatObjectSnippet(Word memory, DynamicHub hub, boolean rememberedSet) {
        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        UnsignedWord size = LayoutEncoding.getInstanceSize(hubNonNull.getLayoutEncoding());
        return formatObjectImpl(memory, hubNonNull, size, false, true, rememberedSet);
    }

    @Snippet
    public static Object formatArraySnippet(Word memory, DynamicHub hub, int length, boolean rememberedSet, boolean unaligned) {
        DynamicHub hubNonNull = (DynamicHub) PiNode.piCastNonNull(hub, SnippetAnchorNode.anchor());
        int layoutEncoding = hubNonNull.getLayoutEncoding();
        UnsignedWord size = LayoutEncoding.getArraySize(layoutEncoding, length);
        emitPrefetchAllocate(memory, true);
        return formatArrayImpl(memory, hubNonNull, length, layoutEncoding, size, true, rememberedSet, unaligned);
    }

    /** The vector of dimensions is a sequence of ints, indexed by byte offset. */
    private static final int sizeOfDimensionElement = ConfigurationValues.getObjectLayout().sizeInBytes(JavaKind.Int);

    @Snippet
    public static Object newMultiArraySnippet(DynamicHub hub, @ConstantParameter int rank, @VarargsParameter int[] dimensions, AllocationCounter counter) {

        Pointer dims = DimensionsNode.allocaDimsArray(rank);
        ExplodeLoopNode.explodeLoop();
        for (int i = 0; i < rank; i++) {
            dims.writeInt(i * sizeOfDimensionElement, dimensions[i], LocationIdentity.INIT_LOCATION);
        }

        return callNewMultiArray(NEW_MULTI_ARRAY, DynamicHub.toClass(hub), rank, dims, counter);
    }

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object callNewMultiArray(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> c, int rank, Pointer dimensions, AllocationCounter counter);

    /** Foreign call: {@link #NEW_MULTI_ARRAY}. */
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    @SubstrateForeignCallTarget
    private static Object newMultiArray(DynamicHub hub, int rank, Pointer dimensionsStackValue, AllocationCounter counter) {
        /*
         * All dimensions must be checked here up front, since a previous dimension of length 0
         * stops allocation of inner dimensions.
         */
        for (int i = 0; i < rank; i++) {
            if (dimensionsStackValue.readInt(i * sizeOfDimensionElement) < 0) {
                throw new NegativeArraySizeException();
            }
        }
        return newMultiArrayRecursion(hub, rank, dimensionsStackValue, counter);
    }

    private static Object newMultiArrayRecursion(DynamicHub hub, int rank, Pointer dimensionsStackValue, AllocationCounter counter) {

        checkHub(hub);
        int length = dimensionsStackValue.readInt(0);
        Object result = fastNewArray(hub, length, hub.getLayoutEncoding(), true, counter);

        if (rank > 1) {
            UnsignedWord offset = LayoutEncoding.getArrayBaseOffset(hub.getLayoutEncoding());
            UnsignedWord endOffset = LayoutEncoding.getArrayElementOffset(hub.getLayoutEncoding(), length);

            while (offset.belowThan(endOffset)) {
                // Each newMultiArrayRecursion could create a cross-generational reference.
                BarrieredAccess.writeObject(result, offset,
                                newMultiArrayRecursion(hub.getComponentHub(), rank - 1, dimensionsStackValue.add(sizeOfDimensionElement), counter));
                offset = offset.add(ConfigurationValues.getObjectLayout().getReferenceSize());
            }
        }
        return result;
    }

    @Snippet
    public static Object arraysCopyOfSnippet(DynamicHub hub, Object original, int originalLength, int newLength, AllocationCounter counter) {
        checkHub(hub);
        int layoutEncoding = hub.getLayoutEncoding();
        // allocate new array without initializing the new array
        Object newArray = fastNewArrayWithPiCast(hub, newLength, layoutEncoding, false, counter);

        int copiedLength = originalLength < newLength ? originalLength : newLength;
        UnsignedWord copiedEndOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, copiedLength);
        UnsignedWord newArrayEndOffset = LayoutEncoding.getArrayElementOffset(layoutEncoding, newLength);

        UnsignedWord offset = LayoutEncoding.getArrayBaseOffset(layoutEncoding);
        while (offset.belowThan(copiedEndOffset)) {
            Object val = ObjectAccess.readObject(original, offset);
            ObjectAccess.writeObject(newArray, offset, val, LocationIdentity.INIT_LOCATION);

            offset = offset.add(ConfigurationValues.getObjectLayout().getReferenceSize());
        }

        while (offset.belowThan(newArrayEndOffset)) {
            ObjectAccess.writeObject(newArray, offset, null, LocationIdentity.INIT_LOCATION);
            offset = offset.add(ConfigurationValues.getObjectLayout().getReferenceSize());
        }

        return FixedValueAnchorNode.getObject(newArray);
    }

    private static final CloneNotSupportedException CLONE_NOT_SUPPORTED_EXCEPTION = new CloneNotSupportedException("Object is not instance of Cloneable.");

    /** The actual implementation of {@link Object#clone}. */
    @Snippet
    private static Object doCloneSnippet(Object thisObj, AllocationCounter counter) throws CloneNotSupportedException {

        if (!(thisObj instanceof Cloneable)) {
            throw CLONE_NOT_SUPPORTED_EXCEPTION;
        }

        DynamicHub hub = KnownIntrinsics.readHub(thisObj);
        int layoutEncoding = hub.getLayoutEncoding();
        UnsignedWord size = LayoutEncoding.getSizeFromObject(thisObj);

        profileAllocation(size, counter);

        /*
         * The size of the clone is the same as the size of the original object. On the fast path we
         * try to allocate aligned memory, i.e., a block inside an aligned chunks, for the clone and
         * don't need to distinguish instance objects from arrays. If we fail, i.e., the returned
         * memory is null, then either the instance object or small array didn't fit in the
         * available space or it is a large array. In either case we go on the slow path.
         */
        Pointer memory = ThreadLocalAllocation.allocateMemory(ThreadLocalAllocation.regularTLAB.getAddress(), size);

        Object thatObject = null;
        if (BranchProbabilityNode.probability(BranchProbabilityNode.FAST_PATH_PROBABILITY, memory.isNonNull())) {
            WordBase header = ObjectHeaderImpl.getObjectHeaderImpl().formatHub(hub, false, false);
            ObjectHeader.initializeHeaderOfNewObject(memory, header);
            /*
             * For arrays the length initialization is handled by doCloneUninterruptibly since the
             * array length offset is the same as the first field offset.
             */
            thatObject = memory.toObjectNonNull();
        } else {
            if (LayoutEncoding.isArray(layoutEncoding)) {
                int length = KnownIntrinsics.readArrayLength(thisObj);
                thatObject = callSlowNewArray(SLOW_NEW_ARRAY, DynamicHub.toClass(hub), length);
            } else {
                thatObject = callSlowNewInstance(SLOW_NEW_INSTANCE, DynamicHub.toClass(hub));
            }
        }

        if (LayoutEncoding.isArray(layoutEncoding)) {
            int length = KnownIntrinsics.readArrayLength(thisObj);
            thatObject = PiArrayNode.piArrayCastToSnippetReplaceeStamp(thatObject, length);
        } else {
            thatObject = PiNode.piCastToSnippetReplaceeStamp(thatObject);
        }

        UnsignedWord firstFieldOffset = WordFactory.signed(ConfigurationValues.getObjectLayout().getFirstFieldOffset());

        return doCloneUninterruptibly(thisObj, thatObject, firstFieldOffset, size);
    }

    @Uninterruptible(reason = "Copies via Pointers")
    // TODO: Could this call objectCopyForwards?
    // TODO: What if the bytes being written need remembered set operations?
    private static Object doCloneUninterruptibly(Object thisObject, Object thatObject, UnsignedWord firstFieldOffset, UnsignedWord size) {
        Pointer thatMemory = Word.objectToUntrackedPointer(thatObject);
        /*
         * Copy the thisObj over thatMemory. Excluding the hub to make sure that no GC-relevant
         * header bits are transfered from thisObj to the clone.
         */
        Pointer thisMemory = Word.objectToUntrackedPointer(thisObject);
        UnsignedWord offset = firstFieldOffset;
        if (!isWordAligned(offset) && offset.belowThan(size)) { // narrow references
            thatMemory.writeInt(offset, thisMemory.readInt(offset));
            offset = offset.add(Integer.BYTES);
        }
        while (offset.belowThan(size)) {
            thatMemory.writeWord(offset, thisMemory.readWord(offset));
            offset = offset.add(ConfigurationValues.getTarget().wordSize);
        }
        return thatMemory.toObjectNonNull();
    }

    private static Object formatObjectImpl(Pointer memory, DynamicHub hub, UnsignedWord size, @ConstantParameter boolean constantSize, @ConstantParameter boolean fillContents, boolean rememberedSet) {
        if (fillContents) {
            emitPrefetchAllocate(memory, false);
        }

        WordBase header = ObjectHeaderImpl.getObjectHeaderImpl().formatHub(hub, rememberedSet, false);
        ObjectHeaderImpl.initializeHeaderOfNewObject(memory, header);
        if (fillContents) {
            int wordSize = ConfigurationValues.getTarget().wordSize;
            UnsignedWord offset = WordFactory.unsigned(ConfigurationValues.getObjectLayout().getFirstFieldOffset());
            Word zeroingStores = WordFactory.zero();
            if (!isWordAligned(offset) && offset.belowThan(size)) { // narrow references
                memory.writeInt(offset, 0, LocationIdentity.INIT_LOCATION);
                offset = offset.add(Integer.BYTES);
                zeroingStores = zeroingStores.add(1);
            }
            if (constantSize) {
                zeroingStores = zeroingStores.add(size.subtract(offset).unsignedDivide(wordSize));
                if (zeroingStores.belowOrEqual(SubstrateOptions.MaxUnrolledObjectZeroingStores.getValue())) {
                    ExplodeLoopNode.explodeLoop();
                }
            }
            while (offset.belowThan(size)) {
                memory.writeWord(offset, WordFactory.zero(), LocationIdentity.INIT_LOCATION);
                offset = offset.add(wordSize);
            }
        }
        return memory.toObjectNonNull();
    }

    private static void emitPrefetchAllocate(Pointer address, boolean isArray) {
        if (SubstrateOptions.AllocatePrefetchStyle.getValue() > 0) {
            int lines = isArray ? SubstrateOptions.AllocatePrefetchLines.getValue() : SubstrateOptions.AllocateInstancePrefetchLines.getValue();
            int stepSize = SubstrateOptions.AllocatePrefetchStepSize.getValue();
            int distance = SubstrateOptions.AllocatePrefetchDistance.getValue();
            ExplodeLoopNode.explodeLoop();
            for (int i = 0; i < lines; i++) {
                PrefetchAllocateNode.prefetch(OffsetAddressNode.address(address, distance));
                distance += stepSize;
            }
        }
    }

    @Uninterruptible(reason = "Manipulates Objects via Pointers", callerMustBe = true)
    private static Object formatArrayImpl(Pointer memory, DynamicHub hub, int length, int layoutEncoding, UnsignedWord size, boolean fillContents, boolean rememberedSet, boolean unaligned) {
        WordBase header = ObjectHeaderImpl.getObjectHeaderImpl().formatHub(hub, rememberedSet, unaligned);
        ObjectHeader.initializeHeaderOfNewObject(memory, header);
        memory.writeInt(ConfigurationValues.getObjectLayout().getArrayLengthOffset(), length, LocationIdentity.INIT_LOCATION);
        if (fillContents) {
            UnsignedWord offset = LayoutEncoding.getArrayBaseOffset(layoutEncoding);
            if ((!isWordAligned(offset)) && offset.belowThan(size)) {
                /*
                 * The first array element offset can be 4-byte aligned. Write an int in this case
                 * to zero any bytes before the Word writes below.
                 */
                memory.writeInt(offset, 0, LocationIdentity.INIT_LOCATION);
                offset = offset.add(4);
            }
            while (offset.belowThan(size)) {
                memory.writeWord(offset, WordFactory.zero(), LocationIdentity.INIT_LOCATION);
                offset = offset.add(ConfigurationValues.getTarget().wordSize);
            }
        }
        return memory.toObjectNonNull();
    }

    /** Is the given value Word aligned? */
    @Uninterruptible(reason = "Called from uninterruptible code.")
    private static boolean isWordAligned(UnsignedWord offset) {
        return offset.unsignedRemainder(ConfigurationValues.getTarget().wordSize).equal(0);
    }

    @SuppressWarnings("unused")
    public static void registerForeignCalls(RuntimeConfiguration runtimeConfig, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<SubstrateForeignCallDescriptor, SubstrateForeignCallLinkage> foreignCalls, boolean hosted) {
        for (SubstrateForeignCallDescriptor descriptor : FOREIGN_CALLS) {
            foreignCalls.put(descriptor, new SubstrateForeignCallLinkage(providers, descriptor));
        }
    }

    @SuppressWarnings("unused")
    public static void registerLowerings(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        new AllocationSnippets(options, factories, providers, snippetReflection, lowerings);
    }

    private AllocationSnippets(OptionValues options, Iterable<DebugHandlersFactory> factories, Providers providers, SnippetReflectionProvider snippetReflection,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings) {
        super(options, factories, providers, snippetReflection);

        NewInstanceLowering newInstanceLowering = new NewInstanceLowering();
        lowerings.put(NewInstanceNode.class, newInstanceLowering);
        lowerings.put(SubstrateNewInstanceNode.class, newInstanceLowering);

        DynamicNewInstanceLowering dynamicNewInstanceLowering = new DynamicNewInstanceLowering();
        lowerings.put(DynamicNewInstanceNode.class, dynamicNewInstanceLowering);
        lowerings.put(SubstrateDynamicNewInstanceNode.class, dynamicNewInstanceLowering);

        NewArrayLowering newArrayLowering = new NewArrayLowering();
        lowerings.put(NewArrayNode.class, newArrayLowering);
        lowerings.put(SubstrateNewArrayNode.class, newArrayLowering);

        DynamicNewArrayLowering dynamicNewArrayLowering = new DynamicNewArrayLowering();
        lowerings.put(DynamicNewArrayNode.class, dynamicNewArrayLowering);
        lowerings.put(SubstrateDynamicNewArrayNode.class, dynamicNewArrayLowering);

        NewMultiArrayLowering newMultiArrayLowering = new NewMultiArrayLowering();
        lowerings.put(NewMultiArrayNode.class, newMultiArrayLowering);

        lowerings.put(FormatObjectNode.class, new FormatObjectLowering());
        lowerings.put(FormatArrayNode.class, new FormatArrayLowering());

        ArraysCopyOfLowering arraysCopyOfLowering = new ArraysCopyOfLowering();
        lowerings.put(SubstrateArraysCopyOfNode.class, arraysCopyOfLowering);

        ObjectCloneLowering objectCloneLowering = new ObjectCloneLowering();
        lowerings.put(SubstrateObjectCloneNode.class, objectCloneLowering);
    }

    protected class NewInstanceLowering implements NodeLoweringProvider<NewInstanceNode> {

        private final SnippetInfo newInstance = snippet(AllocationSnippets.class, "staticNewInstanceSnippet", ALLOCATION_LOCATION_IDENTITIES);

        @Override
        public void lower(NewInstanceNode node, LoweringTool tool) {
            if (node.graph().getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                return;
            }
            SharedType type = (SharedType) node.instanceClass();
            Arguments args = new Arguments(newInstance, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("hub", type.getHub());
            args.addConst("encoding", type.getHub().getLayoutEncoding());
            args.addConst("fillContents", node.fillContents());
            addCounterArgs(args, node, type);
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }

    }

    protected class DynamicNewInstanceLowering implements NodeLoweringProvider<DynamicNewInstanceNode> {

        private final SnippetInfo dynamicNewInstance = snippet(AllocationSnippets.class, "dynamicNewInstanceSnippet", ALLOCATION_LOCATION_IDENTITIES);

        @Override
        public void lower(DynamicNewInstanceNode node, LoweringTool tool) {
            if (node.graph().getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                return;
            }
            Arguments args = new Arguments(dynamicNewInstance, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("hub", node.getInstanceType());
            args.addConst("fillContents", node.fillContents());
            addCounterArgs(args, node, null);
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    public static void addCounterArgs(Arguments args, ValueNode node, ResolvedJavaType type) {
        AllocationCounter counter = null;

        if (AllocationSite.Options.AllocationProfiling.getValue()) {
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
            counter = allocationSite.createCounter(counterName);
        }
        args.add("counter", counter);
    }

    protected class NewArrayLowering implements NodeLoweringProvider<NewArrayNode> {

        private final SnippetInfo newArray = snippet(AllocationSnippets.class, "newArraySnippet", ALLOCATION_LOCATION_IDENTITIES);

        @Override
        public void lower(NewArrayNode node, LoweringTool tool) {
            if (node.graph().getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                return;
            }
            SharedType type = (SharedType) node.elementType().getArrayClass();
            Arguments args = new Arguments(newArray, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("hub", type.getHub());
            args.add("length", node.length());
            args.addConst("layoutEncoding", type.getHub().getLayoutEncoding());
            args.addConst("fillContents", node.fillContents());
            addCounterArgs(args, node, type);
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    protected class DynamicNewArrayLowering implements NodeLoweringProvider<DynamicNewArrayNode> {

        private final SnippetInfo dynamicNewArray = snippet(AllocationSnippets.class, "dynamicNewArraySnippet", ALLOCATION_LOCATION_IDENTITIES);

        @Override
        public void lower(DynamicNewArrayNode node, LoweringTool tool) {
            if (node.graph().getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                return;
            }
            Arguments args = new Arguments(dynamicNewArray, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("elementType", node.getElementType());
            args.add("length", node.length());
            args.addConst("fillContents", node.fillContents());
            addCounterArgs(args, node, null);
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    protected class FormatObjectLowering implements NodeLoweringProvider<FormatObjectNode> {

        private final SnippetInfo formatObject = snippet(AllocationSnippets.class, "formatObjectSnippet", ALLOCATION_LOCATION_IDENTITIES);

        @Override
        public void lower(FormatObjectNode node, LoweringTool tool) {
            if (node.graph().getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                return;
            }
            Arguments args = new Arguments(formatObject, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("memory", node.getMemory());
            args.add("hub", node.getHub());
            args.add("rememberedSet", node.getRememberedSet());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    protected class FormatArrayLowering implements NodeLoweringProvider<FormatArrayNode> {

        private final SnippetInfo formatArray = snippet(AllocationSnippets.class, "formatArraySnippet", ALLOCATION_LOCATION_IDENTITIES);

        @Override
        public void lower(FormatArrayNode node, LoweringTool tool) {
            if (node.graph().getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                return;
            }
            Arguments args = new Arguments(formatArray, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("memory", node.getMemory());
            args.add("hub", node.getHub());
            args.add("length", node.getLength());
            args.add("rememberedSet", node.getRememberedSet());
            args.add("unaligned", node.getUnaligned());
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    protected class NewMultiArrayLowering implements NodeLoweringProvider<NewMultiArrayNode> {
        private final SnippetInfo newMultiArray = snippet(AllocationSnippets.class, "newMultiArraySnippet", ALLOCATION_LOCATION_IDENTITIES);

        @Override
        public void lower(NewMultiArrayNode node, LoweringTool tool) {
            if (node.graph().getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                return;
            }
            StructuredGraph graph = node.graph();
            int rank = node.dimensionCount();
            ValueNode[] dims = new ValueNode[rank];
            for (int i = 0; i < node.dimensionCount(); i++) {
                dims[i] = node.dimension(i);
            }
            SharedType type = (SharedType) node.type();

            Arguments args = new Arguments(newMultiArray, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("hub", type.getHub());
            args.addConst("rank", rank);
            args.addVarargs("dimensions", int.class, StampFactory.forKind(JavaKind.Int), dims);
            addCounterArgs(args, node, type);
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    protected class ArraysCopyOfLowering implements NodeLoweringProvider<SubstrateArraysCopyOfNode> {

        private final SnippetInfo arraysCopyOf = snippet(AllocationSnippets.class, "arraysCopyOfSnippet", ALLOCATION_LOCATION_IDENTITIES);

        @Override
        public void lower(SubstrateArraysCopyOfNode node, LoweringTool tool) {
            if (node.graph().getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                return;
            }

            Arguments args = new Arguments(arraysCopyOf, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("hub", node.getNewArrayType());
            args.add("original", node.getOriginal());
            args.add("originalLength", node.getOriginaLength());
            args.add("newLength", node.getNewLength());

            addCounterArgs(args, node, null);
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    final class ObjectCloneLowering implements NodeLoweringProvider<SubstrateObjectCloneNode> {

        private final SnippetInfo doClone = snippet(AllocationSnippets.class, "doCloneSnippet", AllocationSnippets.ALLOCATION_LOCATION_IDENTITIES);

        @Override
        public void lower(SubstrateObjectCloneNode node, LoweringTool tool) {
            if (node.graph().getGuardsStage() != StructuredGraph.GuardsStage.AFTER_FSA) {
                return;
            }

            Arguments args = new Arguments(doClone, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("thisObj", node.getObject());
            AllocationSnippets.addCounterArgs(args, node, null);
            template(node, args).instantiate(providers.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

}
