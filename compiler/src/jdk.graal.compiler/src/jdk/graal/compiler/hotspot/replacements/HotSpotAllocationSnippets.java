/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replacements;

import static jdk.graal.compiler.core.common.GraalOptions.MinimalBulkZeroingSize;
import static jdk.graal.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_OPTIONVALUES;
import static jdk.graal.compiler.hotspot.GraalHotSpotVMConfig.INJECTED_VMCONFIG;
import static jdk.graal.compiler.hotspot.HotSpotBackend.DYNAMIC_NEW_INSTANCE_OR_NULL;
import static jdk.graal.compiler.hotspot.HotSpotBackend.NEW_ARRAY_OR_NULL;
import static jdk.graal.compiler.hotspot.HotSpotBackend.NEW_INSTANCE_OR_NULL;
import static jdk.graal.compiler.hotspot.HotSpotBackend.NEW_MULTI_ARRAY_OR_NULL;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.CLASS_ARRAY_KLASS_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.CLASS_INIT_STATE_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.CLASS_INIT_THREAD_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.HUB_WRITE_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.MARK_WORD_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.PROTOTYPE_MARK_WORD_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_END_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.TLAB_TOP_LOCATION;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.arrayKlassOffset;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.instanceKlassStateBeingInitialized;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.isInstanceKlassFullyInitialized;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperHeaderSizeMask;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperHeaderSizeShift;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperLog2ElementSizeMask;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.layoutHelperLog2ElementSizeShift;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.loadKlassFromObject;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.readInstanceKlassInitState;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.readInstanceKlassInitThread;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.readLayoutHelper;
import static jdk.graal.compiler.hotspot.replacements.HotSpotReplacementsUtil.registerAsWord;
import static jdk.graal.compiler.hotspot.replacements.HotspotSnippetsOptions.ProfileAllocations;
import static jdk.graal.compiler.hotspot.replacements.HotspotSnippetsOptions.ProfileAllocationsContext;
import static jdk.graal.compiler.hotspot.stubs.StubUtil.VM_MESSAGE_C;
import static jdk.graal.compiler.nodes.PiArrayNode.piArrayCastToSnippetReplaceeStamp;
import static jdk.graal.compiler.nodes.PiNode.piCastToSnippetReplaceeStamp;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.DEOPT_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.VERY_FAST_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.replacements.ReplacementsUtil.dynamicAssert;
import static jdk.graal.compiler.replacements.ReplacementsUtil.staticAssert;
import static jdk.graal.compiler.replacements.SnippetTemplate.DEFAULT_REPLACER;
import static jdk.graal.compiler.replacements.nodes.CStringConstant.cstring;
import static jdk.vm.ci.meta.DeoptimizationAction.InvalidateRecompile;
import static jdk.vm.ci.meta.DeoptimizationAction.None;
import static jdk.vm.ci.meta.DeoptimizationReason.RuntimeConstraint;

import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.Fold.InjectedParameter;
import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.api.replacements.Snippet.NonNullParameter;
import jdk.graal.compiler.api.replacements.Snippet.VarargsParameter;
import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.Node.ConstantNodeParameter;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.hotspot.GraalHotSpotVMConfig;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.hotspot.meta.HotSpotRegistersProvider;
import jdk.graal.compiler.hotspot.nodes.KlassBeingInitializedCheckNode;
import jdk.graal.compiler.hotspot.nodes.type.KlassPointerStamp;
import jdk.graal.compiler.hotspot.word.KlassPointer;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.SnippetAnchorNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.debug.DynamicCounterNode;
import jdk.graal.compiler.nodes.debug.VerifyHeapNode;
import jdk.graal.compiler.nodes.extended.BranchProbabilityNode;
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
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.AllocationSnippets;
import jdk.graal.compiler.replacements.SnippetCounter;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.AbstractTemplates;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.hotspot.HotSpotResolvedObjectType;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

public class HotSpotAllocationSnippets extends AllocationSnippets {

    private final GraalHotSpotVMConfig config;
    private final Register threadRegister;

    public HotSpotAllocationSnippets(GraalHotSpotVMConfig config, HotSpotRegistersProvider registers) {
        this.config = config;
        this.threadRegister = registers.getThreadRegister();
    }

    @Snippet
    protected Object allocateInstance(KlassPointer hub,
                    @ConstantParameter long size,
                    @ConstantParameter boolean forceSlowPath,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter HotSpotAllocationProfilingData profilingData,
                    @ConstantParameter boolean withException) {
        Object result = allocateInstanceImpl(hub.asWord(), WordFactory.unsigned(size), forceSlowPath, fillContents, emitMemoryBarrier, true, profilingData, withException);
        return piCastToSnippetReplaceeStamp(result);
    }

    @Snippet
    public Object allocateArray(KlassPointer hub,
                    int length,
                    @ConstantParameter int arrayBaseOffset,
                    @ConstantParameter int log2ElementSize,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter int fillStartOffset,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter boolean maybeUnroll,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter HotSpotAllocationProfilingData profilingData,
                    @ConstantParameter boolean withException) {
        Object result = allocateArrayImpl(hub.asWord(), length, arrayBaseOffset, log2ElementSize, fillContents, fillStartOffset, emitMemoryBarrier, maybeUnroll, supportsBulkZeroing,
                        supportsOptimizedFilling, profilingData, withException);
        return piArrayCastToSnippetReplaceeStamp(result, length);
    }

    @Snippet
    public Object allocateInstanceDynamic(@NonNullParameter Class<?> type,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter HotSpotAllocationProfilingData profilingData,
                    @ConstantParameter boolean withException) {

        KlassPointer hub = ClassGetHubNode.readClass(type);
        if (probability(FAST_PATH_PROBABILITY, !hub.isNull())) {
            KlassPointer nonNullHub = ClassGetHubNode.piCastNonNull(hub, SnippetAnchorNode.anchor());

            if (probability(VERY_FAST_PATH_PROBABILITY, isInstanceKlassFullyInitialized(nonNullHub))) {
                int layoutHelper = readLayoutHelper(nonNullHub);
                /*
                 * src/share/vm/oops/klass.hpp: For instances, layout helper is a positive number,
                 * the instance size. This size is already passed through align_object_size and
                 * scaled to bytes. The low order bit is set if instances of this class cannot be
                 * allocated using the fastpath.
                 */
                if (probability(FAST_PATH_PROBABILITY, (layoutHelper & 1) == 0)) {
                    /*
                     * FIXME(je,ds): we should actually pass typeContext instead of "" but late
                     * binding of parameters is not yet supported by the GraphBuilderPlugin system.
                     */
                    UnsignedWord size = WordFactory.unsigned(layoutHelper);
                    return allocateInstanceImpl(nonNullHub.asWord(), size, false, fillContents, emitMemoryBarrier, false, profilingData, withException);
                }
            } else {
                DeoptimizeNode.deopt(None, RuntimeConstraint);
            }
        }
        return PiNode.piCastToSnippetReplaceeStamp(dynamicNewInstanceStub(type, withException));
    }

    @Snippet
    private static Class<?> validateNewInstanceClass(Class<?> type, Class<?> classClass) {
        if (probability(DEOPT_PROBABILITY, type == null)) {
            DeoptimizeNode.deopt(None, RuntimeConstraint);
        }
        Class<?> nonNullType = PiNode.piCastNonNullClass(type, SnippetAnchorNode.anchor());
        if (probability(DEOPT_PROBABILITY,
                        DynamicNewInstanceNode.throwsInstantiationExceptionInjectedProbability(DEOPT_PROBABILITY, nonNullType, classClass))) {
            DeoptimizeNode.deopt(None, RuntimeConstraint);
        }
        return nonNullType;
    }

    @Snippet
    public Object allocateArrayDynamic(Class<?> elementType,
                    Class<?> voidClass,
                    int length,
                    @ConstantParameter FillContent fillContents,
                    @ConstantParameter boolean emitMemoryBarrier,
                    @ConstantParameter JavaKind knownElementKind,
                    @ConstantParameter int knownLayoutHelper,
                    @ConstantParameter boolean supportsBulkZeroing,
                    @ConstantParameter boolean supportsOptimizedFilling,
                    @ConstantParameter boolean withException,
                    @ConstantParameter HotSpotAllocationProfilingData profilingData) {
        /*
         * We only need the dynamic check for void when we have no static information from
         * knownElementKind.
         */
        staticAssert(knownElementKind != JavaKind.Void, "unsupported knownElementKind");

        if (knownElementKind == JavaKind.Illegal) {
            if (probability(SLOW_PATH_PROBABILITY, elementType == null)) {
                DeoptimizeNode.deopt(None, RuntimeConstraint);
            }
            if (probability(SLOW_PATH_PROBABILITY, DynamicNewArrayNode.throwsIllegalArgumentException(elementType, voidClass))) {
                DeoptimizeNode.deopt(None, RuntimeConstraint);
            }
        }

        KlassPointer klass = loadKlassFromObject(elementType, arrayKlassOffset(INJECTED_VMCONFIG), CLASS_ARRAY_KLASS_LOCATION);
        if (probability(DEOPT_PROBABILITY, klass.isNull())) {
            DeoptimizeNode.deopt(None, RuntimeConstraint);
        }
        KlassPointer nonNullKlass = ClassGetHubNode.piCastNonNull(klass, SnippetAnchorNode.anchor());

        if (probability(DEOPT_PROBABILITY, length < 0)) {
            DeoptimizeNode.deopt(None, RuntimeConstraint);
        }
        int layoutHelper;
        if (knownElementKind == JavaKind.Illegal) {
            layoutHelper = readLayoutHelper(nonNullKlass);
        } else {
            dynamicAssert(knownLayoutHelper == readLayoutHelper(nonNullKlass), "layout mismatch");
            layoutHelper = knownLayoutHelper;
        }
        //@formatter:off
        // from src/share/vm/oops/klass.hpp:
        //
        // For arrays, layout helper is a negative number, containing four
        // distinct bytes, as follows:
        //    MSB:[tag, hsz, ebt, log2(esz)]:LSB
        // where:
        //    tag is 0x80 if the elements are oops, 0xC0 if non-oops
        //    hsz is array header size in bytes (i.e., offset of first element)
        //    ebt is the BasicType of the elements
        //    esz is the element size in bytes
        //@formatter:on

        int arrayBaseOffset = (layoutHelper >> layoutHelperHeaderSizeShift(INJECTED_VMCONFIG)) & layoutHelperHeaderSizeMask(INJECTED_VMCONFIG);
        int log2ElementSize = (layoutHelper >> layoutHelperLog2ElementSizeShift(INJECTED_VMCONFIG)) & layoutHelperLog2ElementSizeMask(INJECTED_VMCONFIG);
        Object result;
        result = allocateArrayImpl(nonNullKlass.asWord(), length, arrayBaseOffset, log2ElementSize, fillContents, arrayBaseOffset, emitMemoryBarrier, false,
                        supportsBulkZeroing, supportsOptimizedFilling, profilingData, withException);
        return piArrayCastToSnippetReplaceeStamp(result, length);
    }

    @Snippet
    protected Object newmultiarray(KlassPointer hub, @ConstantParameter int rank, @ConstantParameter boolean withException, @VarargsParameter int[] dimensions) {
        return piCastToSnippetReplaceeStamp(newMultiArrayImpl(hub.asWord(), rank, withException, dimensions));
    }

    @Snippet
    private void verifyHeap() {
        Word tlabInfo = getTLABInfo();
        Word topValue = readTlabTop(tlabInfo);
        if (!topValue.equal(WordFactory.zero())) {
            Word topValueContents = topValue.readWord(0, MARK_WORD_LOCATION);
            if (topValueContents.equal(WordFactory.zero())) {
                AssertionSnippets.vmMessageC(VM_MESSAGE_C, true, cstring("overzeroing of TLAB detected"), 0L, 0L, 0L);
            }
        }
    }

    @Snippet
    private void threadBeingInitializedCheck(KlassPointer klass) {
        int state = readInstanceKlassInitState(klass);
        if (state != instanceKlassStateBeingInitialized(INJECTED_VMCONFIG)) {
            // The klass is no longer being initialized so force recompilation
            DeoptimizeNode.deopt(InvalidateRecompile, RuntimeConstraint);
        } else if (getThread() != readInstanceKlassInitThread(klass)) {
            // The klass is being initialized but this isn't the initializing thread so
            // so deopt and allow execution to resume in the interpreter where it should block.
            DeoptimizeNode.deopt(None, RuntimeConstraint);
        }
    }

    @Override
    protected final int getPrefetchStyle() {
        return HotSpotReplacementsUtil.allocatePrefetchStyle(INJECTED_VMCONFIG);
    }

    @Override
    protected final int getPrefetchLines(boolean isArray) {
        if (isArray) {
            return HotSpotReplacementsUtil.allocatePrefetchLines(INJECTED_VMCONFIG);
        } else {
            return HotSpotReplacementsUtil.allocateInstancePrefetchLines(INJECTED_VMCONFIG);
        }
    }

    @Override
    protected final int getPrefetchStepSize() {
        return HotSpotReplacementsUtil.allocatePrefetchStepSize(INJECTED_VMCONFIG);
    }

    @Override
    protected final int getPrefetchDistance() {
        return HotSpotReplacementsUtil.allocatePrefetchDistance(INJECTED_VMCONFIG);
    }

    @Override
    protected final Object callNewInstanceStub(Word hub, boolean withException) {
        KlassPointer klassPtr = KlassPointer.fromWord(hub);
        if (!withException) {
            return nonNullOrDeopt(newInstanceOrNull(NEW_INSTANCE_OR_NULL, klassPtr));
        } else {
            return nonNullOrDeopt(newInstanceWithException(NEW_INSTANCE_OR_NULL, klassPtr));
        }
    }

    @NodeIntrinsic(value = ForeignCallWithExceptionNode.class)
    private static native Object newInstanceWithException(@ConstantNodeParameter ForeignCallDescriptor descriptor, KlassPointer hub);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object newInstanceOrNull(@ConstantNodeParameter ForeignCallDescriptor descriptor, KlassPointer hub);

    @Override
    protected final Object callNewArrayStub(Word hub, int length, boolean withException) {
        KlassPointer klassPtr = KlassPointer.fromWord(hub);
        if (!withException) {
            return nonNullOrDeopt(newArrayOrNull(NEW_ARRAY_OR_NULL, klassPtr, length));
        } else {
            return nonNullOrDeopt(newArrayWithException(NEW_ARRAY_OR_NULL, klassPtr, length));
        }
    }

    @NodeIntrinsic(value = ForeignCallWithExceptionNode.class)
    private static native Object newArrayWithException(@ConstantNodeParameter ForeignCallDescriptor descriptor, KlassPointer hub, int length);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object newArrayOrNull(@ConstantNodeParameter ForeignCallDescriptor descriptor, KlassPointer hub, int length);

    /**
     * Deoptimizes if {@code obj == null} otherwise returns {@code obj}.
     */
    private static Object nonNullOrDeopt(Object obj) {
        if (BranchProbabilityNode.probability(BranchProbabilityNode.DEOPT_PROBABILITY, obj == null)) {
            DeoptimizeNode.deopt(None, RuntimeConstraint);
        }
        return obj;
    }

    public static Object dynamicNewInstanceStub(Class<?> elementType, boolean withException) {
        if (!withException) {
            return nonNullOrDeopt(dynamicNewInstanceOrNull(DYNAMIC_NEW_INSTANCE_OR_NULL, elementType));
        } else {
            return nonNullOrDeopt(dynamicNewInstanceWithException(DYNAMIC_NEW_INSTANCE_OR_NULL, elementType));
        }
    }

    @NodeIntrinsic(value = ForeignCallWithExceptionNode.class)
    public static native Object dynamicNewInstanceWithException(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> elementType);

    @NodeIntrinsic(value = ForeignCallNode.class)
    public static native Object dynamicNewInstanceOrNull(@ConstantNodeParameter ForeignCallDescriptor descriptor, Class<?> elementType);

    @Override
    protected final Object callNewMultiArrayStub(Word hub, int rank, Word dims, boolean withException) {
        KlassPointer klassPointer = KlassPointer.fromWord(hub);
        if (!withException) {
            return nonNullOrDeopt(newMultiArrayOrNull(NEW_MULTI_ARRAY_OR_NULL, klassPointer, rank, dims));
        } else {
            return nonNullOrDeopt(newMultiArrayWithException(NEW_MULTI_ARRAY_OR_NULL, klassPointer, rank, dims));
        }
    }

    @NodeIntrinsic(value = ForeignCallWithExceptionNode.class)
    private static native Object newMultiArrayWithException(@ConstantNodeParameter ForeignCallDescriptor descriptor, KlassPointer hub, int rank, Word dims);

    @NodeIntrinsic(value = ForeignCallNode.class)
    private static native Object newMultiArrayOrNull(@ConstantNodeParameter ForeignCallDescriptor descriptor, KlassPointer hub, int rank, Word dims);

    @Fold
    static int getMinimalBulkZeroingSize(@InjectedParameter OptionValues optionValues) {
        return MinimalBulkZeroingSize.getValue(optionValues);
    }

    @Override
    protected final int getMinimalBulkZeroingSize() {
        return getMinimalBulkZeroingSize(INJECTED_OPTIONVALUES);
    }

    @Override
    public final void initializeObjectHeader(Word memory, Word hub, boolean isArray) {
        KlassPointer klassPtr = KlassPointer.fromWord(hub);
        Word markWord = WordFactory.signed(HotSpotReplacementsUtil.defaultPrototypeMarkWord(INJECTED_VMCONFIG));
        HotSpotReplacementsUtil.initializeObjectHeader(memory, markWord, klassPtr);
    }

    @Override
    protected final int instanceHeaderSize() {
        return HotSpotReplacementsUtil.instanceHeaderSize(INJECTED_VMCONFIG);
    }

    @Override
    public final int arrayLengthOffset() {
        return HotSpotReplacementsUtil.arrayLengthOffset(INJECTED_VMCONFIG);
    }

    @Override
    protected final int objectAlignment() {
        return HotSpotReplacementsUtil.objectAlignment(INJECTED_VMCONFIG);
    }

    @Override
    public final boolean useTLAB() {
        return HotSpotReplacementsUtil.useTLAB(INJECTED_VMCONFIG);
    }

    @Override
    protected final boolean shouldAllocateInTLAB(UnsignedWord allocationSize, boolean isArray) {
        if (HotSpotReplacementsUtil.useG1GC(INJECTED_VMCONFIG)) {
            // The TLAB is sized in a way that humongous objects are never allocated in the TLAB.
            // So, whatever fits into the TLAB can be allocated there.
            return true;
        } else {
            return !isArray || allocationSize.belowThan(16 * 1024 * 1024);
        }
    }

    @Override
    public final Word getTLABInfo() {
        return getThread();
    }

    private Word getThread() {
        return registerAsWord(threadRegister);
    }

    @Override
    public final Word readTlabEnd(Word thread) {
        return HotSpotReplacementsUtil.readTlabEnd(thread);
    }

    @Override
    public final Word readTlabTop(Word thread) {
        return HotSpotReplacementsUtil.readTlabTop(thread);
    }

    @Override
    public final void writeTlabTop(Word thread, Word newTop) {
        HotSpotReplacementsUtil.writeTlabTop(thread, newTop);
    }

    @Override
    protected final Object verifyOop(Object obj) {
        return HotSpotReplacementsUtil.verifyOop(obj);
    }

    @Override
    protected final void profileAllocation(AllocationProfilingData profilingData, UnsignedWord size) {
        if (doProfile(INJECTED_OPTIONVALUES)) {
            String name = createName(INJECTED_OPTIONVALUES, profilingData);

            boolean context = withContext(INJECTED_OPTIONVALUES);
            DynamicCounterNode.counter("number of bytes allocated", name, size.rawValue(), context);
            DynamicCounterNode.counter("number of allocations", name, 1, context);
        }
    }

    @Fold
    static boolean doProfile(@Fold.InjectedParameter OptionValues options) {
        return ProfileAllocations.getValue(options);
    }

    enum ProfileContext {
        AllocatingMethod,
        InstanceOrArray,
        AllocatedType,
        AllocatedTypesInMethod,
        Total
    }

    @Fold
    static String createName(@Fold.InjectedParameter OptionValues options, AllocationProfilingData profilingData) {
        HotSpotAllocationProfilingData hotspotAllocationProfilingData = (HotSpotAllocationProfilingData) profilingData;
        switch (ProfileAllocationsContext.getValue(options)) {
            case AllocatingMethod:
                return "";
            case InstanceOrArray:
                return hotspotAllocationProfilingData.path;
            case AllocatedType:
            case AllocatedTypesInMethod:
                return hotspotAllocationProfilingData.typeContext;
            case Total:
                return "bytes";
            default:
                throw GraalError.shouldNotReachHereUnexpectedValue(ProfileAllocationsContext.getValue(options)); // ExcludeFromJacocoGeneratedReport
        }
    }

    @Fold
    static boolean withContext(@Fold.InjectedParameter OptionValues options) {
        ProfileContext context = ProfileAllocationsContext.getValue(options);
        return context == ProfileContext.AllocatingMethod || context == ProfileContext.AllocatedTypesInMethod;
    }

    static HotSpotResolvedObjectType lookupArrayClass(MetaAccessProvider metaAccessProvider, JavaKind kind) {
        return (HotSpotResolvedObjectType) metaAccessProvider.lookupJavaType(kind == JavaKind.Object ? Object.class : kind.toJavaClass()).getArrayClass();
    }

    public static class Templates extends AbstractTemplates {
        private final GraalHotSpotVMConfig config;
        private final AllocationSnippetCounters snippetCounters;
        private HotSpotAllocationProfilingData profilingData;

        private final SnippetInfo allocateInstance;
        private final SnippetInfo allocateArray;
        private final SnippetInfo allocateArrayDynamic;
        private final SnippetInfo allocateInstanceDynamic;
        private final SnippetInfo validateNewInstanceClass;
        private final SnippetInfo newmultiarray;
        private final SnippetInfo verifyHeap;
        private final SnippetInfo threadBeingInitializedCheck;

        @SuppressWarnings("this-escape")
        public Templates(HotSpotAllocationSnippets receiver, OptionValues options, SnippetCounter.Group.Factory groupFactory, HotSpotProviders providers,
                        GraalHotSpotVMConfig config) {
            super(options, providers);
            this.config = config;
            snippetCounters = new AllocationSnippetCounters(groupFactory);

            allocateInstance = snippet(providers,
                            HotSpotAllocationSnippets.class,
                            "allocateInstance",
                            null,
                            receiver,
                            MARK_WORD_LOCATION,
                            HUB_WRITE_LOCATION,
                            TLAB_TOP_LOCATION,
                            TLAB_END_LOCATION,
                            PROTOTYPE_MARK_WORD_LOCATION);
            allocateArray = snippet(providers,
                            HotSpotAllocationSnippets.class,
                            "allocateArray",
                            null,
                            receiver,
                            MARK_WORD_LOCATION,
                            HUB_WRITE_LOCATION,
                            TLAB_TOP_LOCATION,
                            TLAB_END_LOCATION);
            allocateArrayDynamic = snippet(providers,
                            HotSpotAllocationSnippets.class,
                            "allocateArrayDynamic",
                            null,
                            receiver,
                            MARK_WORD_LOCATION,
                            HUB_WRITE_LOCATION,
                            TLAB_TOP_LOCATION,
                            TLAB_END_LOCATION);
            allocateInstanceDynamic = snippet(providers,
                            HotSpotAllocationSnippets.class,
                            "allocateInstanceDynamic",
                            null,
                            receiver,
                            MARK_WORD_LOCATION,
                            HUB_WRITE_LOCATION,
                            TLAB_TOP_LOCATION,
                            TLAB_END_LOCATION,
                            PROTOTYPE_MARK_WORD_LOCATION,
                            CLASS_INIT_STATE_LOCATION);
            validateNewInstanceClass = snippet(providers,
                            HotSpotAllocationSnippets.class,
                            "validateNewInstanceClass",
                            MARK_WORD_LOCATION,
                            HUB_WRITE_LOCATION,
                            TLAB_TOP_LOCATION,
                            TLAB_END_LOCATION,
                            PROTOTYPE_MARK_WORD_LOCATION,
                            CLASS_INIT_STATE_LOCATION);
            newmultiarray = snippet(providers,
                            HotSpotAllocationSnippets.class,
                            "newmultiarray",
                            null,
                            receiver,
                            TLAB_TOP_LOCATION,
                            TLAB_END_LOCATION);
            verifyHeap = snippet(providers,
                            HotSpotAllocationSnippets.class,
                            "verifyHeap",
                            null,
                            receiver);
            threadBeingInitializedCheck = snippet(providers,
                            HotSpotAllocationSnippets.class,
                            "threadBeingInitializedCheck",
                            null,
                            receiver,
                            CLASS_INIT_STATE_LOCATION,
                            CLASS_INIT_THREAD_LOCATION);
        }

        private HotSpotAllocationProfilingData getProfilingData(OptionValues localOptions, String path, ResolvedJavaType type) {
            if (ProfileAllocations.getValue(localOptions)) {
                // Create one object per snippet instantiation - this kills the snippet caching as
                // we need to add the object as a constant to the snippet.
                String typeContext = type == null ? null : type.toJavaName(false);
                return new HotSpotAllocationProfilingData(snippetCounters, path, typeContext);
            } else if (profilingData == null) {
                profilingData = new HotSpotAllocationProfilingData(snippetCounters, null, null);
            }

            return profilingData;
        }

        /**
         * Lowers a {@link NewInstanceNode}.
         */
        public void lower(NewInstanceNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) node.instanceClass();
            assert !type.isArray();
            ConstantNode hub = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), type.klass(), tool.getMetaAccess(), graph);
            long size = type.instanceSize();

            OptionValues localOptions = graph.getOptions();
            Arguments args = new Arguments(allocateInstance, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("hub", hub);
            // instanceSize returns a negative number for types which should be slow path allocated
            args.addConst("size", Math.abs(size));
            args.addConst("forceSlowPath", size < 0);
            args.addConst("fillContents", FillContent.fromBoolean(node.fillContents()));
            args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
            args.addConst("profilingData", getProfilingData(localOptions, "instance", type));
            args.addConst("withException", false);

            SnippetTemplate template = template(tool, node, args);
            graph.getDebug().log("Lowering allocateInstance in %s: node=%s, template=%s, arguments=%s", graph, node, template, args);
            template.instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        public void lower(NewInstanceWithExceptionNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) node.instanceClass();
            assert !type.isArray();
            ConstantNode hub = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), type.klass(), tool.getMetaAccess(), graph);
            long size = type.instanceSize();

            OptionValues localOptions = graph.getOptions();
            Arguments args = new Arguments(allocateInstance, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("hub", hub);
            // instanceSize returns a negative number for types which should be slow path allocated
            args.addConst("size", Math.abs(size));
            args.addConst("forceSlowPath", size < 0);
            args.addConst("fillContents", FillContent.fromBoolean(true));
            args.addConst("emitMemoryBarrier", true /* barrier */);
            args.addConst("profilingData", getProfilingData(localOptions, "instance", type));
            args.addConst("withException", true);

            SnippetTemplate template = template(tool, node, args);
            graph.getDebug().log("Lowering allocateInstance in %s: node=%s, template=%s, arguments=%s", graph, node, template, args);
            template.instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        /**
         * Lowers a {@link NewArrayNode}.
         */
        public void lower(NewArrayNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            ResolvedJavaType elementType = node.elementType();
            HotSpotResolvedObjectType arrayType = (HotSpotResolvedObjectType) elementType.getArrayClass();
            JavaKind elementKind = elementType.getJavaKind();
            ConstantNode hub = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), arrayType.klass(), tool.getMetaAccess(), graph);
            final int arrayBaseOffset = tool.getMetaAccess().getArrayBaseOffset(elementKind);
            int log2ElementSize = CodeUtil.log2(tool.getMetaAccess().getArrayIndexScale(elementKind));

            OptionValues localOptions = graph.getOptions();
            Arguments args = new Arguments(allocateArray, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("hub", hub);
            ValueNode length = node.length();
            args.add("length", length.isAlive() ? length : graph.addOrUniqueWithInputs(length));
            args.addConst("arrayBaseOffset", arrayBaseOffset);
            args.addConst("log2ElementSize", log2ElementSize);
            args.addConst("fillContents", FillContent.fromBoolean(node.fillContents()));
            args.addConst("fillStartOffset", arrayBaseOffset);
            args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
            args.addConst("maybeUnroll", length.isConstant());
            args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
            args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(localOptions));
            args.addConst("profilingData", getProfilingData(localOptions, "array", arrayType));
            args.addConst("withException", false);

            SnippetTemplate template = template(tool, node, args);
            graph.getDebug().log("Lowering allocateArray in %s: node=%s, template=%s, arguments=%s", graph, node, template, args);
            template.instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        public void lower(NewArrayWithExceptionNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            ResolvedJavaType elementType = node.elementType();
            HotSpotResolvedObjectType arrayType = (HotSpotResolvedObjectType) elementType.getArrayClass();
            JavaKind elementKind = elementType.getJavaKind();
            ConstantNode hub = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), arrayType.klass(), tool.getMetaAccess(), graph);
            final int arrayBaseOffset = tool.getMetaAccess().getArrayBaseOffset(elementKind);
            int log2ElementSize = CodeUtil.log2(tool.getMetaAccess().getArrayIndexScale(elementKind));

            OptionValues localOptions = graph.getOptions();
            Arguments args = new Arguments(allocateArray, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("hub", hub);
            ValueNode length = node.length();
            args.add("length", length.isAlive() ? length : graph.addOrUniqueWithInputs(length));
            args.addConst("arrayBaseOffset", arrayBaseOffset);
            args.addConst("log2ElementSize", log2ElementSize);
            args.addConst("fillContents", FillContent.fromBoolean(node.fillContents()));
            args.addConst("fillStartOffset", arrayBaseOffset);
            args.addConst("emitMemoryBarrier", true); // node.emitMemoryBarrier());
            args.addConst("maybeUnroll", length.isConstant());
            args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
            args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(localOptions));
            args.addConst("profilingData", getProfilingData(localOptions, "array", arrayType));
            args.addConst("withException", true);

            SnippetTemplate template = template(tool, node, args);
            graph.getDebug().log("Lowering allocateArray in %s: node=%s, template=%s, arguments=%s", graph, node, template, args);
            template.instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        public void lower(NewMultiArrayNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            int rank = node.dimensionCount();
            ValueNode[] dims = new ValueNode[rank];
            for (int i = 0; i < node.dimensionCount(); i++) {
                dims[i] = node.dimension(i);
            }
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) node.type();
            ConstantNode hub = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), type.klass(), tool.getMetaAccess(), graph);

            Arguments args = new Arguments(newmultiarray, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("hub", hub);
            args.addConst("rank", rank);
            args.addConst("withException", false);
            args.addVarargs("dimensions", int.class, StampFactory.forKind(JavaKind.Int), dims);

            template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        public void lower(NewMultiArrayWithExceptionNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            int rank = node.dimensionCount();
            ValueNode[] dims = new ValueNode[rank];
            for (int i = 0; i < node.dimensionCount(); i++) {
                dims[i] = node.dimension(i);
            }
            HotSpotResolvedObjectType type = (HotSpotResolvedObjectType) node.type();
            ConstantNode hub = ConstantNode.forConstant(KlassPointerStamp.klassNonNull(), type.klass(), tool.getMetaAccess(), graph);

            Arguments args = new Arguments(newmultiarray, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("hub", hub);
            args.addConst("rank", rank);
            args.addConst("withException", true);
            args.addVarargs("dimensions", int.class, StampFactory.forKind(JavaKind.Int), dims);

            template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        public void lower(DynamicNewInstanceNode node, LoweringTool tool) {
            OptionValues localOptions = node.graph().getOptions();

            Arguments args = new Arguments(allocateInstanceDynamic, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("type", node.getInstanceType());
            args.addConst("fillContents", FillContent.fromBoolean(node.fillContents()));
            args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
            args.addConst("profilingData", getProfilingData(localOptions, "", null));
            args.addConst("withException", false);

            template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        public void lower(DynamicNewInstanceWithExceptionNode node, LoweringTool tool) {
            OptionValues localOptions = node.graph().getOptions();

            Arguments args = new Arguments(allocateInstanceDynamic, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("type", node.getInstanceType());
            args.addConst("fillContents", FillContent.fromBoolean(true));
            args.addConst("emitMemoryBarrier", true/* barriers */);
            args.addConst("profilingData", getProfilingData(localOptions, "", null));
            args.addConst("withException", true);

            template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        public void lower(ValidateNewInstanceClassNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();

            Arguments args = new Arguments(validateNewInstanceClass, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("type", node.getInstanceType());
            args.add("classClass", node.getClassClass());
            template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        public void lower(DynamicNewArrayNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            OptionValues localOptions = graph.getOptions();
            ValueNode length = node.length();
            ValueNode voidClass = node.getVoidClass();
            assert voidClass != null;

            Arguments args = new Arguments(allocateArrayDynamic, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("elementType", node.getElementType());
            args.add("voidClass", voidClass);
            args.add("length", length.isAlive() ? length : graph.addOrUniqueWithInputs(length));
            args.addConst("fillContents", FillContent.fromBoolean(node.fillContents()));
            args.addConst("emitMemoryBarrier", node.emitMemoryBarrier());
            /*
             * We use Kind.Illegal as a marker value instead of null because constant snippet
             * parameters cannot be null.
             */
            args.addConst("knownElementKind", node.getKnownElementKind() == null ? JavaKind.Illegal : node.getKnownElementKind());
            if (node.getKnownElementKind() != null) {
                args.addConst("knownLayoutHelper", lookupArrayClass(tool, node.getKnownElementKind()).layoutHelper());
            } else {
                args.addConst("knownLayoutHelper", 0);
            }
            args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
            args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(localOptions));
            args.addConst("withException", false);
            args.addConst("profilingData", getProfilingData(localOptions, "dynamic type", null));

            template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        public void lower(DynamicNewArrayWithExceptionNode node, LoweringTool tool) {
            StructuredGraph graph = node.graph();
            OptionValues localOptions = graph.getOptions();
            ValueNode length = node.length();
            ValueNode voidClass = node.getVoidClass();
            assert voidClass != null;

            Arguments args = new Arguments(allocateArrayDynamic, graph.getGuardsStage(), tool.getLoweringStage());
            args.add("elementType", node.getElementType());
            args.add("voidClass", voidClass);
            args.add("length", length.isAlive() ? length : graph.addOrUniqueWithInputs(length));
            args.addConst("fillContents", FillContent.fromBoolean(true));
            args.addConst("emitMemoryBarrier", true/* barriers */);
            /*
             * We use Kind.Illegal as a marker value instead of null because constant snippet
             * parameters cannot be null.
             */
            args.addConst("knownElementKind", JavaKind.Illegal);
            args.addConst("knownLayoutHelper", 0);

            args.addConst("supportsBulkZeroing", tool.getLowerer().supportsBulkZeroing());
            args.addConst("supportsOptimizedFilling", tool.getLowerer().supportsOptimizedFilling(localOptions));
            args.addConst("withException", true);
            args.addConst("profilingData", getProfilingData(localOptions, "dynamic type", null));

            template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        public void lower(VerifyHeapNode node, LoweringTool tool) {
            if (config.cAssertions) {
                Arguments args = new Arguments(verifyHeap, node.graph().getGuardsStage(), tool.getLoweringStage());

                template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
            } else {
                GraphUtil.removeFixedWithUnusedInputs(node);
            }
        }

        public void lower(KlassBeingInitializedCheckNode node, LoweringTool tool) {
            Arguments args = new Arguments(threadBeingInitializedCheck, node.graph().getGuardsStage(), tool.getLoweringStage());
            args.add("klass", node.getKlass());

            template(tool, node, args).instantiate(tool.getMetaAccess(), node, DEFAULT_REPLACER, args);
        }

        private static HotSpotResolvedObjectType lookupArrayClass(LoweringTool tool, JavaKind kind) {
            return HotSpotAllocationSnippets.lookupArrayClass(tool.getMetaAccess(), kind);
        }

    }

    private static class HotSpotAllocationProfilingData extends AllocationProfilingData {
        String path;
        String typeContext;

        HotSpotAllocationProfilingData(AllocationSnippetCounters snippetCounters, String path, String typeContext) {
            super(snippetCounters);
            this.path = path;
            this.typeContext = typeContext;
        }
    }
}
