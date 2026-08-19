/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.g1;

import static com.oracle.svm.core.heap.RuntimeCodeCacheCleaner.CLASSES_ASSUMED_REACHABLE;
import static com.oracle.svm.guest.staging.log.Log.RIGHT_ALIGN;
import static com.oracle.svm.core.g1.G1Options.G1HeapRegionSize;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.core.config.ObjectLayout;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CEntryPointLiteral;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.shared.BuildPhaseProvider.ReadyForCompilation;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.SubstrateDiagnostics.DiagnosticThunk;
import com.oracle.svm.core.SubstrateDiagnostics.DiagnosticThunkRegistry;
import com.oracle.svm.core.SubstrateDiagnostics.ErrorContext;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.guest.staging.SubstrateGCOptions;
import com.oracle.svm.guest.staging.core.UnmanagedMemoryUtil;
import com.oracle.svm.guest.staging.c.CGlobalData;
import com.oracle.svm.guest.staging.c.CGlobalDataFactory;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;
import com.oracle.svm.core.gc.shared.NativeGCStackWalker;
import com.oracle.svm.core.gc.shared.NativeGCThreadTransitions;
import com.oracle.svm.core.gc.shared.NativeGCVMOperationSupport;
import com.oracle.svm.core.gc.shared.NativeGCVMOperationSupport.NativeGCVMOperationData;
import com.oracle.svm.core.gc.shared.NativeGCVMOperationSupport.NativeGCVMOperationWrapperData;
import com.oracle.svm.core.code.RuntimeCodeInstallation;
import com.oracle.svm.guest.staging.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.heap.FillerArray;
import com.oracle.svm.core.heap.FillerObject;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.InstanceReferenceMapEncoder;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceHandlerThread;
import com.oracle.svm.guest.staging.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RuntimeCodeInfoGCSupport;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubUtils;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.guest.staging.log.Log;
import com.oracle.svm.guest.staging.option.NotifyGCRuntimeOptionKey;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.guest.staging.core.graal.KnownIntrinsics;
import com.oracle.svm.guest.staging.core.heap.UnknownObjectField;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.guest.staging.core.thread.ThreadStatus;
import com.oracle.svm.core.thread.ThreadsLock;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocal;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.guest.staging.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.threadlocal.VMThreadLocalSupport;
import com.oracle.svm.core.g1.nativelib.G1Library;
import com.oracle.svm.core.g1.nativelib.G1Structs.G1InitState;
import com.oracle.svm.core.g1.nativelib.G1Structs.G1InternalState;
import com.oracle.svm.core.g1.nativelib.G1Structs.G1RegionInfo;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.MultiLayeredImageSingleton;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.SubstrateUtil;
import com.oracle.svm.shared.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.vm.ci.meta.JavaKind;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, other = DisallowLayered.class)
public final class G1Heap extends Heap {
    public static final String IMAGE_HEAP_BOT_BEGIN_SYMBOL_NAME = "__svm_g1_image_heap_bot_begin";
    public static final String IMAGE_HEAP_BOT_END_SYMBOL_NAME = "__svm_g1_image_heap_bot_end";

    private static final CGlobalData<Word> IMAGE_HEAP_BOT_BEGIN = CGlobalDataFactory.forSymbol(IMAGE_HEAP_BOT_BEGIN_SYMBOL_NAME);
    private static final CGlobalData<Word> IMAGE_HEAP_BOT_END = CGlobalDataFactory.forSymbol(IMAGE_HEAP_BOT_END_SYMBOL_NAME);

    public static final Field GC_TOTAL_COLLECTIONS_ADDRESS_FIELD = ReflectionUtil.lookupField(G1Heap.class, "gcTotalCollectionsAddress");
    public static final FastThreadLocalBytes<Word> javaThreadTL = FastThreadLocalFactory.createBytes(G1Constants::javaThreadSize, "G1Heap.javaThread");
    private static final FastThreadLocalWord<Word> cardTableAddressTL = FastThreadLocalFactory.createWord("G1Heap.cardTableAddress").setMaxOffset(FastThreadLocal.FIRST_CACHE_LINE);

    private static long gcTotalCollectionsAddress;

    private final G1GC gc = new G1GC();
    private final G1ImageHeapInfo imageHeapInfo = new G1ImageHeapInfo();
    private final G1RuntimeCodeInfoGCSupport runtimeCodeInfoGCSupport = new G1RuntimeCodeInfoGCSupport();
    private final NativeGCStackWalker stackWalker = new NativeGCStackWalker();
    private final NativeGCThreadTransitions threadTransitions = new NativeGCThreadTransitions();
    private final NativeGCVMOperationSupport vmOperationSupport = new NativeGCVMOperationSupport();
    private final G1VMOperations vmOperations = new G1VMOperations();
    private final G1ObjectHeader objectHeader;
    private final G1PerfData perfData;

    private boolean isInitialized = false;
    private List<Class<?>> classList;
    /* The card table address is relative to the heap base and not an absolute address */
    private Word cardTableAddress;

    @UnknownObjectField(availability = ReadyForCompilation.class) private byte[] accessedFieldOffsets;

    @Platforms(Platform.HOSTED_ONLY.class)
    public G1Heap(G1PerfData perfData) {
        this.objectHeader = new G1ObjectHeader();
        this.perfData = perfData;

        DiagnosticThunkRegistry.singleton().add(new DumpHeapSettingsAndGCInternalState());
        DiagnosticThunkRegistry.singleton().add(new DumpRegionInformation());
        DiagnosticThunkRegistry.singleton().add(new DumpCurrentGCThreadName());
    }

    @Fold
    public static G1Heap get() {
        return ImageSingletons.lookup(G1Heap.class);
    }

    @Fold
    public static G1ImageHeapInfo getImageHeapInfo() {
        return G1Heap.get().imageHeapInfo;
    }

    @Uninterruptible(reason = "Called during startup. Holds the ThreadsLock with non-exclusive write access.")
    private void initialize(IsolateThread isolateThread) {
        VMError.guarantee(ThreadsLock.hasWriteAccess(), "must hold the ThreadsLock");
        assert !isInitialized : "only the first thread may initialize the heap";
        isInitialized = true;

        G1Heap heap = ImageSingletons.lookup(G1Heap.class);
        VMThreadLocalSupport threadLocalSupport = ImageSingletons.lookup(VMThreadLocalSupport.class);

        Pointer heapBase = KnownIntrinsics.heapBase();
        assert heap.getImageHeapOffsetInAddressSpace() % G1HeapRegionSize.getValue() == 0 : "null regions must be full regions";
        int closedImageHeapRegions = imageHeapInfo.getNumClosedRegions();
        int openImageHeapRegions = imageHeapInfo.getNumOpenRegions();
        Word imageHeapRegionTypes = Word.objectToUntrackedWord(imageHeapInfo.getRegionTypes());
        Word imageHeapRegionFreeSpaces = Word.objectToUntrackedWord(imageHeapInfo.getRegionFreeSpaces());
        Word imageHeapBlockOffsetTable = IMAGE_HEAP_BOT_BEGIN.get();
        UnsignedWord imageHeapBlockOffsetTableSize = IMAGE_HEAP_BOT_END.get().subtract(imageHeapBlockOffsetTable);
        Word dynamicHubClass = Word.objectToUntrackedWord(DynamicHub.class);
        Word fillerObjectClass = Word.objectToUntrackedWord(FillerObject.class);
        Word fillerArrayClass = Word.objectToUntrackedWord(FillerArray.class);
        Word stringClass = Word.objectToUntrackedWord(String.class);
        Word systemClass = Word.objectToUntrackedWord(System.class);
        Word staticObjectFields = Word.objectToUntrackedWord(StaticFieldsSupport.getStaticObjectFieldsAtRuntime(MultiLayeredImageSingleton.UNKNOWN_LAYER_NUMBER));
        Word staticPrimitiveFields = Word.objectToUntrackedWord(StaticFieldsSupport.getStaticPrimitiveFieldsAtRuntime(MultiLayeredImageSingleton.UNKNOWN_LAYER_NUMBER));
        Word vmOperationThread = Word.objectToUntrackedWord(VMOperationControl.getDedicatedVMOperationThread());
        Word safepointMaster = Word.objectToUntrackedWord(Safepoint.singleton());
        Word runtimeCodeInfoMemory = Word.objectToUntrackedWord(RuntimeCodeInfoMemory.singleton());
        int referenceMapCompressedOffsetShift = InstanceReferenceMapEncoder.REFERENCE_MAP_COMPRESSED_OFFSET_SHIFT;
        Word threadLocalsReferenceMap = NonmovableArrays.addressOf(threadLocalSupport.getThreadLocalsReferenceMap(), threadLocalSupport.getThreadLocalsReferenceMapIndex());
        Word classesAssumedReachableForCodeUnloading = getClassesAssumedReachableForCodeUnloading();
        Word performanceData = Word.objectToUntrackedWord(perfData);
        boolean closedTypeWorldHubLayout = SubstrateOptions.useClosedTypeWorldHubLayout();
        boolean useInterfaceHashing = SubstrateOptions.useInterfaceHashing();
        int interfaceHashingMaxId = SubstrateOptions.interfaceHashingMaxId();
        int dynamicHubHashingInterfaceMask = DynamicHubUtils.HASHING_INTERFACE_MASK;
        int dynamicHubHashingShiftOffset = DynamicHubUtils.HASHING_SHIFT_OFFSET;
        Word offsets = Word.objectToUntrackedWord(accessedFieldOffsets).add(getByteArrayBaseOffset());
        int offsetsLength = accessedFieldOffsets.length;
        CFunctionPointer collectForAllocationOp = getFunctionPointer(vmOperations.funcCollectForAllocation);
        CFunctionPointer executePauseRemark = getFunctionPointer(vmOperations.funcExecutePauseRemark);
        CFunctionPointer executePauseCleanup = getFunctionPointer(vmOperations.funcExecutePauseCleanup);
        CFunctionPointer collectFullOp = getFunctionPointer(vmOperations.funcCollectFull);
        CFunctionPointer verifyHeapOp = getFunctionPointer(vmOperations.funcVerifyHeap);
        CFunctionPointer tryInitiateConcMarkOp = getFunctionPointer(vmOperations.funcTryInitiateConcMarkOp);
        CFunctionPointer waitForVMOperationExecutionStatus = getFunctionPointer(vmOperationSupport.funcWaitForVMOperationExecutionStatus);
        CFunctionPointer updateVMOperationExecutionStatus = getFunctionPointer(vmOperationSupport.funcUpdateVMOperationExecutionStatus);
        CFunctionPointer isVMOperationFinished = getFunctionPointer(vmOperationSupport.funcIsVMOperationFinished);
        CFunctionPointer fetchThreadStackFrames = getFunctionPointer(stackWalker.funcFetchThreadStackFrames);
        CFunctionPointer freeThreadStackFrames = getFunctionPointer(stackWalker.funcFreeThreadStackFrames);
        CFunctionPointer fetchContinuationStackFrames = getFunctionPointer(stackWalker.funcFetchContinuationStackFrames);
        CFunctionPointer freeContinuationStackFrames = getFunctionPointer(stackWalker.funcFreeContinuationStackFrames);
        CFunctionPointer fetchCodeInfos = getFunctionPointer(stackWalker.funcFetchCodeInfos);
        CFunctionPointer freeCodeInfos = getFunctionPointer(stackWalker.funcFreeCodeInfos);
        CFunctionPointer cleanRuntimeCodeCache = getFunctionPointer(runtimeCodeInfoGCSupport.funcCleanCodeCache);
        CFunctionPointer transitionVMToNative = getFunctionPointer(threadTransitions.funcVMToNative);
        CFunctionPointer fastTransitionNativeToVM = getFunctionPointer(threadTransitions.funcFastNativeToVM);
        CFunctionPointer slowTransitionNativeToVM = getFunctionPointer(threadTransitions.funcSlowNativeToVM);

        G1InitState initState = G1Library.create(isolateThread, heapBase,
                        closedImageHeapRegions, openImageHeapRegions, imageHeapRegionTypes, imageHeapRegionFreeSpaces,
                        imageHeapBlockOffsetTable, imageHeapBlockOffsetTableSize,
                        dynamicHubClass, fillerObjectClass, fillerArrayClass, stringClass, systemClass,
                        staticObjectFields, staticPrimitiveFields, vmOperationThread, safepointMaster, runtimeCodeInfoMemory,
                        referenceMapCompressedOffsetShift, threadLocalsReferenceMap,
                        classesAssumedReachableForCodeUnloading, performanceData, closedTypeWorldHubLayout,
                        useInterfaceHashing, interfaceHashingMaxId, dynamicHubHashingInterfaceMask, dynamicHubHashingShiftOffset,
                        offsets, offsetsLength,
                        collectForAllocationOp, executePauseRemark, executePauseCleanup,
                        collectFullOp, verifyHeapOp, tryInitiateConcMarkOp,
                        waitForVMOperationExecutionStatus, updateVMOperationExecutionStatus, isVMOperationFinished,
                        fetchThreadStackFrames, freeThreadStackFrames,
                        fetchContinuationStackFrames, freeContinuationStackFrames,
                        fetchCodeInfos, freeCodeInfos, cleanRuntimeCodeCache,
                        transitionVMToNative, fastTransitionNativeToVM, slowTransitionNativeToVM);

        VMError.guarantee(initState.isNonNull(), "Fatal error while initializing G1");
        validateInitState(initState);
        G1Heap.gcTotalCollectionsAddress = initState.gcTotalCollectionsAddress().rawValue();
        G1Heap.get().cardTableAddress = initState.cardTableAddress();
    }

    @Uninterruptible(reason = "Called during startup.")
    private static Word getClassesAssumedReachableForCodeUnloading() {
        if (RuntimeCodeInstallation.isEnabled()) {
            return Word.objectToUntrackedWord(CLASSES_ASSUMED_REACHABLE);
        }
        return Word.nullPointer();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static CFunctionPointer getFunctionPointer(CEntryPointLiteral<CFunctionPointer> f) {
        if (f == null) {
            return Word.nullPointer();
        }
        return f.getFunctionPointer();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void suspendAllocation() {
        // Retire the TLAB so that the next allocation is forced to take the slow path.
        G1Library.retireTlab();
    }

    @Override
    public void resumeAllocation() {
        // Nothing to do - the next allocation will refill the TLAB.
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean isAllocationDisallowed() {
        return NoAllocationVerifier.isActive() || SafepointBehavior.ignoresSafepoints();
    }

    @Fold
    @Override
    public GC getGC() {
        return gc;
    }

    @Fold
    @Override
    public RuntimeCodeInfoGCSupport getRuntimeCodeInfoGCSupport() {
        return runtimeCodeInfoGCSupport;
    }

    @Override
    public void walkObjects(ObjectVisitor visitor) {
        walkImageHeapObjects(visitor);
        walkCollectedHeapObjects(visitor);
    }

    @Override
    public void walkImageHeapObjects(ObjectVisitor visitor) {
        if (visitor == null) {
            return;
        }

        byte[] regionTypes = imageHeapInfo.getRegionTypes();
        int imageHeapRegions = imageHeapInfo.getNumRegions();
        for (int region = 0; region < imageHeapRegions; region++) {
            if (!G1RegionType.isContinuesHumongous(regionTypes[region])) {
                Pointer cur = imageHeapInfo.getRegionStart(region);
                Pointer top = imageHeapInfo.getRegionTop(region);
                while (cur.belowThan(top)) {
                    Object o = cur.toObject();
                    visitor.visitObject(o);
                    cur = LayoutEncoding.getImageHeapObjectEnd(o);
                }
            }
        }
    }

    @Override
    public void walkCollectedHeapObjects(ObjectVisitor visitor) {
        if (visitor == null) {
            return;
        }
        G1HeapWalker.walkCollectedHeap(visitor);
    }

    @Fold
    @Override
    public int getHeapBaseAlignment() {
        int buildTimePageSize = SubstrateOptions.getPageSize();
        return Math.max(buildTimePageSize * G1Constants.cardSize(), G1HeapRegionSize.getValue());
    }

    @Fold
    @Override
    public int getImageHeapAlignment() {
        return G1HeapRegionSize.getValue();
    }

    @Fold
    @Override
    public int getImageHeapOffsetInAddressSpace() {
        int buildTimePageSize = SubstrateOptions.getPageSize();
        int result = Math.max(buildTimePageSize * G1Constants.cardSize(), G1HeapRegionSize.getValue());
        assert result % getImageHeapAlignment() == 0 : "start of image heap must be aligned";
        return result;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean isInImageHeap(Object object) {
        return isInPrimaryImageHeap(object);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean isInImageHeap(Pointer pointer) {
        return isInPrimaryImageHeap(pointer);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean isInPrimaryImageHeap(Object object) {
        Word pointer = Word.objectToUntrackedWord(object);
        return isInPrimaryImageHeap(pointer);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean isInPrimaryImageHeap(Pointer pointer) {
        return pointer.aboveOrEqual(imageHeapInfo.getImageHeapStart()) && pointer.belowThan(imageHeapInfo.getImageHeapEnd());
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getClassCount() {
        return imageHeapInfo.getDynamicHubCount();
    }

    @Override
    protected List<Class<?>> getClassesInImageHeap() {
        if (classList == null) {
            ArrayList<Class<?>> classes = findAllDynamicHubs();
            /* Ensure that other threads see consistent values once the list is published. */
            MembarNode.memoryBarrier(MembarNode.FenceKind.STORE_STORE);
            classList = classes;
        }
        return classList;
    }

    private ArrayList<Class<?>> findAllDynamicHubs() {
        byte[] regionTypes = imageHeapInfo.getRegionTypes();
        int hubCount = getClassCount();

        /* DynamicHubs are somewhere in the closed image heap. */
        ArrayList<Class<?>> classes = new ArrayList<>(hubCount);
        for (int region = 0; region < imageHeapInfo.getNumClosedRegions(); region++) {
            if (!G1RegionType.isHumongous(regionTypes[region])) {
                Pointer cur = imageHeapInfo.getRegionStart(region);
                Pointer top = imageHeapInfo.getRegionTop(region);
                while (cur.belowThan(top)) {
                    Object o = cur.toObject();
                    if (o instanceof Class) {
                        classes.add((Class<?>) o);
                        if (classes.size() == hubCount) {
                            return classes;
                        }
                    }
                    cur = LayoutEncoding.getImageHeapObjectEnd(o);
                }
            }
        }

        throw VMError.shouldNotReachHere("Found fewer DynamicHubs in the image heap than expected.");
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public ObjectHeader getObjectHeader() {
        return objectHeader;
    }

    @Uninterruptible(reason = "Tear-down in progress.")
    @Override
    public boolean tearDown() {
        return G1Library.tearDown();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setAccessedFieldOffsets(byte[] fieldOffsets) {
        assert this.accessedFieldOffsets == null;
        this.accessedFieldOffsets = fieldOffsets;
    }

    @Fold
    static int getByteArrayBaseOffset() {
        return ObjectLayout.singleton().getArrayBaseOffset(JavaKind.Byte);
    }

    @Override
    public void prepareForSafepoint() {
        G1Library.prepareForSafepoint();
    }

    @Override
    public void endSafepoint() {
        G1Library.endSafepoint();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static Pointer addressOfCardTableAddress() {
        return (Pointer) cardTableAddressTL.getAddress();
    }

    public static long getGcTotalCollectionsAddress() {
        return gcTotalCollectionsAddress;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void validateInitState(G1InitState state) {
        VMError.guarantee(G1Constants.tlabTopOffset() == state.tlabTopOffset(), "Failed while validating the G1 state: tlabTopOffset");
        VMError.guarantee(G1Constants.tlabEndOffset() == state.tlabEndOffset(), "Failed while validating the G1 state: tlabEndOffset");
        VMError.guarantee(G1Constants.satbQueueMarkingActiveOffset() == state.satbQueueMarkingOffset(), "Failed while validating the G1 state: satbQueueMarkingOffset");
        VMError.guarantee(G1Constants.satbQueueBufferOffset() == state.satbQueueBufferOffset(), "Failed while validating the G1 state: satbQueueBufferOffset");
        VMError.guarantee(G1Constants.satbQueueIndexOffset() == state.satbQueueIndexOffset(), "Failed while validating the G1 state: satbQueueIndexOffset");
        VMError.guarantee(G1Constants.cardQueueBufferOffset() == state.cardQueueBufferOffset(), "Failed while validating the G1 state: cardQueueBufferOffset");
        VMError.guarantee(G1Constants.cardQueueIndexOffset() == state.cardQueueIndexOffset(), "Failed while validating the G1 state: cardQueueIndexOffset");
        VMError.guarantee(G1Constants.dirtyCardValue() == state.dirtyCardValue(), "Failed while validating the G1 state: dirtyCardValue");
        VMError.guarantee(G1Constants.youngCardValue() == state.youngCardValue(), "Failed while validating the G1 state: youngCardValue");
        VMError.guarantee(G1Constants.cardTableShift() == state.cardTableShift(), "Failed while validating the G1 state: cardTableShift");
        VMError.guarantee(G1Constants.logOfHeapRegionGrainBytes() == state.logOfHeapRegionGrainBytes(), "Failed while validating the G1 state: logOfHeapRegionGrainBytes");
        VMError.guarantee(G1Constants.javaThreadSize() == state.javaThreadSize(), "Failed while validating the G1 state: javaThreadSize");
        VMError.guarantee(SizeOf.get(NativeGCVMOperationData.class) <= state.vmOperationDataSize(), "Failed while validating the G1 state: vmOperationDataSize");
        VMError.guarantee(SizeOf.get(NativeGCVMOperationWrapperData.class) <= state.vmOperationWrapperDataSize(), "Failed while validating the G1 state: vmOperationWrapperDataSize");
    }

    @Uninterruptible(reason = "Called during startup.")
    @Override
    public void attachThread(IsolateThread isolateThread) {
        if (isInitialized) {
            G1Library.attachThread(isolateThread);
        } else {
            /* The thread gets attached as a side effect of the initialization. */
            initialize(isolateThread);
        }
        cardTableAddressTL.set(isolateThread, cardTableAddress);
    }

    @Override
    @Uninterruptible(reason = "Current thread holds the ThreadsLock with exclusive write access.")
    public void detachThread(IsolateThread isolateThread) {
        G1Library.detachThread(isolateThread);

        /* Use a value, so that it looks as if the card table starts at address 0. */
        long invalidCardTableAddress = KnownIntrinsics.heapBase().unsignedShiftRight(G1Constants.cardTableShift()).rawValue();
        cardTableAddressTL.set(isolateThread, Word.signed(-invalidCardTableAddress));
    }

    @Override
    public void doReferenceHandling() {
        /* Nothing to do, G1 only supports a dedicated reference handler thread. */
    }

    @Override
    public boolean hasReferencePendingList() {
        return G1Library.hasReferencePendingList();
    }

    @Override
    public void waitForReferencePendingList() throws InterruptedException {
        /*
         * The order is crucial here to prevent transient issues. First, we call into C++ to get the
         * current wakeup count, then we check if the thread was asked to stop, or interrupted. This
         * ensures that the C++ code is able to properly detect the case where the thread is asked
         * to stop or interrupted right before blocking in G1Library.waitForReferencePendingList().
         */
        long initialWakeupCount = G1Library.getReferencePendingListWakeupCount();
        if (ReferenceHandlerThread.isStopping()) {
            // Must recheck because initialWakeupCount can already include the corresponding wakeup.
            return;
        }

        /* Throw an InterruptedException if the thread is interrupted before or after waiting. */
        if (Thread.interrupted() || !waitForPendingReferenceList(initialWakeupCount) && Thread.interrupted()) {
            throw new InterruptedException();
        }
    }

    private static boolean waitForPendingReferenceList(long initialWakeupCount) {
        Thread currentThread = Thread.currentThread();
        int oldThreadStatus = PlatformThreads.getThreadStatus(currentThread);
        PlatformThreads.setThreadStatus(currentThread, ThreadStatus.PARKED);
        try {
            return G1Library.waitForReferencePendingList(initialWakeupCount);
        } finally {
            PlatformThreads.setThreadStatus(currentThread, oldThreadStatus);
        }
    }

    @Override
    public void wakeUpReferencePendingListWaiters() {
        G1Library.wakeUpReferencePendingListWaiters();
    }

    @Override
    @Uninterruptible(reason = "Prevent stack overflow exceptions and recurring callback execution.", calleeMustBe = false)
    public Reference<?> getAndClearReferencePendingList() {
        Word result = G1Library.getAndClearReferencePendingList();
        return (Reference<?>) result.toObject();
    }

    @Override
    public boolean printLocationInfo(Log log, UnsignedWord value, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        Pointer heapBase = KnownIntrinsics.heapBase();
        if (value.equal(heapBase)) {
            log.string("is the heap base");
            return true;
        } else if (value.aboveThan(heapBase) && value.belowThan(imageHeapInfo.getImageHeapStart())) {
            log.string("points into the protected memory between the heap base and the image heap");
            return true;
        }

        if (objectHeader.isEncodedObjectHeader((Word) value)) {
            log.string("is the encoded object header for an object of type ");
            DynamicHub hub = objectHeader.dynamicHubFromObjectHeader((Word) value);
            log.string(hub.getName());
            return true;
        }

        Pointer ptr = (Pointer) value;
        if (printHeapLocationInfo(log, ptr)) {
            if (allowJavaHeapAccess && objectHeader.pointsToObjectHeader(ptr)) {
                log.indent(true);
                SubstrateDiagnostics.printObjectInfo(log, ptr.toObject());
                log.redent(false);
            }
            return true;
        }

        return printGCInternalLocationInfo(log, ptr);
    }

    private static boolean printHeapLocationInfo(Log log, Pointer ptr) {
        G1CommittedMemoryProvider memoryProvider = ImageSingletons.lookup(G1CommittedMemoryProvider.class);
        G1RegionInfo r = UnsafeStackValue.get(G1RegionInfo.class);
        for (int i = 0; i < memoryProvider.getMaxRegions(); i++) {
            if (G1Library.getRegionInfo(i, r)) {
                if (ptr.aboveOrEqual(r.bottom())) {
                    if (ptr.belowThan(r.top())) {
                        log.string("points into region ").signed(i).string(" (").string(G1RegionType.toString(r.regionType())).string(")");
                        return true;
                    } else if (ptr.belowThan(r.end())) {
                        log.string("points into unused space of region ").signed(i).string(" (").string(G1RegionType.toString(r.regionType())).string(")");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean printGCInternalLocationInfo(Log log, Pointer ptr) {
        G1InternalState state = UnsafeStackValue.get(G1InternalState.class);
        fillGCInternalState(state);

        if (pointsIntoTable(ptr, state.cardTableStart(), state.cardTableSize())) {
            log.string("points into the card table");
            return true;
        } else if (pointsIntoTable(ptr, state.blockOffsetTableStart(), state.blockOffsetTableSize())) {
            log.string("points into the block offset table");
            return true;
        }
        return false;
    }

    private static boolean pointsIntoTable(Pointer ptr, Pointer tableStart, UnsignedWord tableSize) {
        return ptr.aboveOrEqual(tableStart) && ptr.belowThan(tableStart.add(tableSize));
    }

    @Override
    public void optionValueChanged(NotifyGCRuntimeOptionKey<?> key) {
        /*
         * There is no need to inform G1 about options that can only be set during isolate startup.
         */
        if (!SubstrateUtil.HOSTED && !key.isIsolateCreationOnly()) {
            assert isInImageHeap(key.getName());
            Word optionName = Word.objectToUntrackedWord(key.getName());
            long value = convertOptionValueToLong(key);
            G1Library.updateOptionValue(optionName, value);
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getThreadAllocatedMemory(IsolateThread thread) {
        return G1Library.getThreadAllocatedMemory(thread);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getUsedMemoryAfterLastGC() {
        return G1Library.getUsedMemoryAfterLastGC();
    }

    @Override
    @Uninterruptible(reason = "Ensure that no GC can occur between modification of the object and this call.", callerMustBe = true)
    public void dirtyAllReferencesOf(Object obj) {
        if (obj == null) {
            return;
        }

        VMError.guarantee(obj instanceof StoredContinuation);
        G1Library.dirtyAllReferencesOf(Word.objectToUntrackedWord(obj));
    }

    @Override
    public long getMillisSinceLastWholeHeapExamined() {
        return G1Library.getMillisSinceLastWholeHeapExamined();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getIdentityHashSalt(Object obj) {
        ReplacementsUtil.staticAssert(false, "identity hash codes are never computed from addresses");
        return 0;
    }

    private static long convertOptionValueToLong(RuntimeOptionKey<?> key) {
        Class<?> valueType = key.getDescriptor().getOptionValueType();
        if (valueType == Boolean.class) {
            return ((Boolean) key.getValue()) ? 1L : 0L;
        } else if (valueType == Integer.class || valueType == Long.class) {
            return ((Number) key.getValue()).longValue();
        } else {
            throw VMError.shouldNotReachHere("Option " + key.getName() + " has an unexpected type: " + valueType);
        }
    }

    private static void fillGCInternalState(G1InternalState state) {
        int size = SizeOf.get(G1InternalState.class);
        UnmanagedMemoryUtil.fill((Pointer) state, Word.unsigned(size), (byte) 0);
        G1Library.getGCInternalState(state);
    }

    @Override
    @Uninterruptible(reason = "Called during early startup.")
    public boolean verifyImageHeapMapping() {
        /* Read & write some data at the beginning and end of each open region. */
        for (int region = imageHeapInfo.getNumClosedRegions(); region < imageHeapInfo.getNumRegions(); region++) {
            Pointer begin = imageHeapInfo.getRegionStart(region);
            Pointer end = imageHeapInfo.getRegionTop(region).subtract(1);

            byte val = begin.readByte(0);
            begin.writeByte(0, val);

            val = end.readByte(0);
            end.writeByte(0, val);
        }
        return true;
    }

    private static final class DumpHeapSettingsAndGCInternalState extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            log.string("Heap settings and statistics:").indent(true);
            log.string("Reserved hub pointer bits: 0b").number(Heap.getHeap().getObjectHeader().getReservedHubBitsMask(), 2, false).newline();
            log.string("Region size: ").signed(G1HeapRegionSize.getValue()).newline();
            log.string("Card table granularity: ").signed(G1Constants.cardSize()).newline();

            G1InternalState state = UnsafeStackValue.get(G1InternalState.class);
            fillGCInternalState(state);

            log.string("Full collections: ").unsigned(state.fullCollections()).newline();
            log.string("Total collections: ").unsigned(state.totalCollections()).newline();
            log.string("Card table: ").zhex(state.cardTableStart()).string(" - ").zhex(state.cardTableStart().add(state.cardTableSize()).subtract(1)).newline();
            log.string("Block offset table: ").zhex(state.blockOffsetTableStart()).string(" - ").zhex(state.blockOffsetTableStart().add(state.blockOffsetTableSize()).subtract(1)).newline();
            log.indent(false);
        }
    }

    private static final class DumpRegionInformation extends DiagnosticThunk {
        private static final int MAX_REGIONS_TO_PRINT = 128 * 1024;

        @Override
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            int maxRegions = ImageSingletons.lookup(G1CommittedMemoryProvider.class).getMaxRegions();
            G1RegionInfo regionInfo = UnsafeStackValue.get(G1RegionInfo.class);

            log.string("Heap regions:").indent(true);
            int printed = 0;
            for (int i = 0; i < maxRegions; i++) {
                if (printed >= MAX_REGIONS_TO_PRINT) {
                    log.string("... (truncated)").newline();
                    break;
                }

                if (G1Library.getRegionInfo(i, regionInfo)) {
                    printRegion(log, i, regionInfo);
                    printed++;
                }
            }
            log.indent(false);
        }

        private static void printRegion(Log log, int regionIndex, G1RegionInfo r) {
            log.string("|").unsigned(regionIndex, 4, RIGHT_ALIGN);
            log.string("|").zhex(r.bottom()).string(", ").zhex(r.top()).string(", ").zhex(r.end());

            UnsignedWord used = r.top().subtract(r.bottom());
            UnsignedWord capacity = r.end().subtract(r.bottom());
            log.string("|").unsigned(used.multiply(100).unsignedDivide(capacity), 3, RIGHT_ALIGN).string("%");
            log.string("|").string(G1RegionType.toString(r.regionType()), 4, RIGHT_ALIGN);
            if (r.inCollectionSet()) {
                log.string("|CS");
            } else {
                log.string("|  ");
            }

            log.string("|TAMS ").zhex(r.topAtMarkStart()).string("| PB ").zhex(r.parsableBottom()).string("|").string(G1RememberedSetState.toString(r.rememberedSetState()), 9, RIGHT_ALIGN);
            log.string("|").unsigned(r.pinnedObjectCount());
            log.newline();
        }
    }

    private static final class DumpCurrentGCThreadName extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            if (SubstrateDiagnostics.isThreadOnlyAttachedForCrashHandler(CurrentIsolate.getCurrentThread())) {
                // The failing thread is an unattached thread, so it might be a GC thread.
                CCharPointer name = G1Library.getCurrentThreadName();
                if (name.isNonNull()) {
                    log.string("Internal name of crashing thread: ").string(name).newline();
                }
            }
        }
    }
}

@TargetClass(value = java.lang.Runtime.class, onlyWith = UseG1GC.class)
@SuppressWarnings({"static-method"})
final class Target_java_lang_Runtime {
    @Substitute
    private long freeMemory() {
        return G1Library.getFreeMemory();
    }

    @Substitute
    private long totalMemory() {
        return G1Library.getTotalMemory();
    }

    @Substitute
    private long maxMemory() {
        return G1Library.getMaxMemory();
    }

    @Substitute
    private void gc() {
        if (!SubstrateGCOptions.DisableExplicitGC.getValue()) {
            G1Library.collect(GCCause.JavaLangSystemGC.getId());
        }
    }
}
