/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shenandoah;

import static com.oracle.svm.core.gc.shenandoah.ShenandoahOptions.ShenandoahRegionSize;
import static com.oracle.svm.core.heap.RuntimeCodeCacheCleaner.CLASSES_ASSUMED_REACHABLE;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

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

import com.oracle.svm.core.BuildPhaseProvider.ReadyForCompilation;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.SubstrateDiagnostics.DiagnosticThunk;
import com.oracle.svm.core.SubstrateDiagnostics.DiagnosticThunkRegistry;
import com.oracle.svm.core.SubstrateDiagnostics.ErrorContext;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.RuntimeCodeInfoMemory;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.gc.shared.NativeGCStackWalker;
import com.oracle.svm.core.gc.shared.NativeGCThreadTransitions;
import com.oracle.svm.core.gc.shared.NativeGCVMOperationSupport;
import com.oracle.svm.core.gc.shared.NativeGCVMOperationSupport.NativeGCVMOperationData;
import com.oracle.svm.core.gc.shared.NativeGCVMOperationSupport.NativeGCVMOperationWrapperData;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahLibrary;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahStructs.ShenandoahInitState;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahStructs.ShenandoahInternalState;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahStructs.ShenandoahRegionInfo;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.heap.FillerArray;
import com.oracle.svm.core.heap.FillerObject;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.InstanceReferenceMapEncoder;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RuntimeCodeInfoGCSupport;
import com.oracle.svm.core.heap.StoredContinuation;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubTypeCheckUtil;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.Safepoint;
import com.oracle.svm.core.thread.ThreadStatus;
import com.oracle.svm.core.thread.VMOperationControl;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.threadlocal.FastThreadLocal;
import com.oracle.svm.core.threadlocal.FastThreadLocalBytes;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalWord;
import com.oracle.svm.core.threadlocal.VMThreadLocalSupport;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.replacements.ReplacementsUtil;
import jdk.graal.compiler.word.Word;
import jdk.vm.ci.meta.JavaKind;

public final class ShenandoahHeap extends Heap {
    public static final FastThreadLocalBytes<Word> javaThreadTL = FastThreadLocalFactory.createBytes(ShenandoahConstants::javaThreadSize, "ShenandoahHeap.javaThread");
    private static final FastThreadLocalWord<Word> cardTableAddressTL = FastThreadLocalFactory.createWord("ShenandoahHeap.cardTableAddress").setMaxOffset(FastThreadLocal.FIRST_CACHE_LINE);

    private final ShenandoahGC gc = new ShenandoahGC();
    private final ShenandoahImageHeapInfo imageHeapInfo = new ShenandoahImageHeapInfo();
    private final ShenandoahRuntimeCodeInfoGCSupport runtimeCodeInfoGCSupport = new ShenandoahRuntimeCodeInfoGCSupport();
    private final NativeGCStackWalker stackWalker = new NativeGCStackWalker();
    private final NativeGCThreadTransitions threadTransitions = new NativeGCThreadTransitions();
    private final NativeGCVMOperationSupport vmOperationSupport = new NativeGCVMOperationSupport();
    private final ShenandoahVMOperations vmOperations = new ShenandoahVMOperations();
    private final ShenandoahObjectHeader objectHeader = new ShenandoahObjectHeader();

    private boolean isInitialized = false;
    private List<Class<?>> classList;
    /* The card table address is relative to the heap base and not an absolute address */
    private Word cardTableAddress;

    @UnknownObjectField(availability = ReadyForCompilation.class) private byte[] accessedFieldOffsets;

    @Platforms(Platform.HOSTED_ONLY.class)
    public ShenandoahHeap() {
        DiagnosticThunkRegistry.singleton().add(new DumpHeapSettingsAndGCInternalState());
        DiagnosticThunkRegistry.singleton().add(new DumpRegionInformation());
        DiagnosticThunkRegistry.singleton().add(new DumpCurrentGCThreadName());
    }

    @Fold
    public static ShenandoahHeap get() {
        return ImageSingletons.lookup(ShenandoahHeap.class);
    }

    @Fold
    public static ShenandoahImageHeapInfo getImageHeapInfo() {
        return ShenandoahHeap.get().imageHeapInfo;
    }

    @Uninterruptible(reason = "Called during startup.")
    private void initialize(IsolateThread isolateThread) {
        VMThreads.guaranteeOwnsThreadMutex("Only the first thread may initialize the heap");
        assert !isInitialized;
        isInitialized = true;

        ShenandoahHeap heap = ImageSingletons.lookup(ShenandoahHeap.class);
        VMThreadLocalSupport threadLocalSupport = ImageSingletons.lookup(VMThreadLocalSupport.class);

        Pointer heapBase = KnownIntrinsics.heapBase();
        assert heap.getImageHeapOffsetInAddressSpace() % ShenandoahRegionSize.getValue() == 0 : "null regions must be full regions";
        int closedImageHeapRegions = imageHeapInfo.getNumClosedRegions();
        int openImageHeapRegions = imageHeapInfo.getNumOpenRegions();
        Word imageHeapRegionTypes = Word.objectToUntrackedPointer(imageHeapInfo.getRegionTypes());
        Word imageHeapRegionFreeSpaces = Word.objectToUntrackedPointer(imageHeapInfo.getRegionFreeSpaces());
        Word dynamicHubClass = Word.objectToUntrackedPointer(DynamicHub.class);
        Word fillerObjectClass = Word.objectToUntrackedPointer(FillerObject.class);
        Word fillerArrayClass = Word.objectToUntrackedPointer(FillerArray.class);
        Word stringClass = Word.objectToUntrackedPointer(String.class);
        Word systemClass = Word.objectToUntrackedPointer(System.class);
        Word staticObjectFields = Word.objectToUntrackedPointer(StaticFieldsSupport.getStaticObjectFieldsAtRuntime(MultiLayeredImageSingleton.UNKNOWN_LAYER_NUMBER));
        Word staticPrimitiveFields = Word.objectToUntrackedPointer(StaticFieldsSupport.getStaticPrimitiveFieldsAtRuntime(MultiLayeredImageSingleton.UNKNOWN_LAYER_NUMBER));
        Word vmOperationThread = Word.objectToUntrackedPointer(VMOperationControl.getDedicatedVMOperationThread());
        Word safepointMaster = Word.objectToUntrackedPointer(Safepoint.singleton());
        Word runtimeCodeInfoMemory = Word.objectToUntrackedPointer(RuntimeCodeInfoMemory.singleton());
        int referenceMapCompressedOffsetShift = InstanceReferenceMapEncoder.REFERENCE_MAP_COMPRESSED_OFFSET_SHIFT;
        Word threadLocalsReferenceMap = NonmovableArrays.addressOf(threadLocalSupport.getThreadLocalsReferenceMap(), threadLocalSupport.getThreadLocalsReferenceMapIndex());
        Word classesAssumedReachableForCodeUnloading = getClassesAssumedReachableForCodeUnloading();
        boolean perfDataSupport = VMInspectionOptions.hasJvmstatSupport();
        boolean useStringInlining = false;
        boolean closedTypeWorldHubLayout = SubstrateOptions.useClosedTypeWorldHubLayout();
        boolean useInterfaceHashing = SubstrateOptions.useInterfaceHashing();
        int interfaceHashingMaxId = SubstrateOptions.interfaceHashingMaxId();
        int dynamicHubHashingInterfaceMask = DynamicHubTypeCheckUtil.HASHING_INTERFACE_MASK;
        int dynamicHubHashingShiftOffset = DynamicHubTypeCheckUtil.HASHING_SHIFT_OFFSET;
        Word offsets = Word.objectToUntrackedPointer(accessedFieldOffsets).add(getByteArrayBaseOffset());
        int offsetsLength = accessedFieldOffsets.length;
        CFunctionPointer collectForAllocationOp = getFunctionPointer(vmOperations.funcCollectForAllocation);
        CFunctionPointer collectFullOp = getFunctionPointer(vmOperations.funcCollectFull);
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

        ShenandoahInitState initState = ShenandoahLibrary.create(isolateThread, heapBase,
                        closedImageHeapRegions, openImageHeapRegions, imageHeapRegionTypes, imageHeapRegionFreeSpaces,
                        dynamicHubClass, fillerObjectClass, fillerArrayClass, stringClass, systemClass,
                        staticObjectFields, staticPrimitiveFields, vmOperationThread, safepointMaster, runtimeCodeInfoMemory,
                        referenceMapCompressedOffsetShift, threadLocalsReferenceMap,
                        classesAssumedReachableForCodeUnloading, perfDataSupport, useStringInlining, closedTypeWorldHubLayout,
                        useInterfaceHashing, interfaceHashingMaxId, dynamicHubHashingInterfaceMask, dynamicHubHashingShiftOffset,
                        offsets, offsetsLength,
                        collectForAllocationOp, collectFullOp,
                        waitForVMOperationExecutionStatus, updateVMOperationExecutionStatus, isVMOperationFinished,
                        fetchThreadStackFrames, freeThreadStackFrames,
                        fetchContinuationStackFrames, freeContinuationStackFrames,
                        fetchCodeInfos, freeCodeInfos, cleanRuntimeCodeCache,
                        transitionVMToNative, fastTransitionNativeToVM, slowTransitionNativeToVM);

        VMError.guarantee(initState.isNonNull(), "Fatal error while initializing Shenandoah");
        validateInitState(initState);
        ShenandoahHeap.get().cardTableAddress = initState.cardTableAddress();
    }

    @Uninterruptible(reason = "Called during startup.")
    private static Word getClassesAssumedReachableForCodeUnloading() {
        if (RuntimeCompilation.isEnabled()) {
            return Word.objectToUntrackedPointer(CLASSES_ASSUMED_REACHABLE);
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
        ShenandoahLibrary.retireTlab();
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
            if (!ShenandoahRegionType.isContinuesHumongous(regionTypes[region])) {
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
        ShenandoahHeapWalker.walkCollectedHeap(visitor);
    }

    @Fold
    @Override
    public int getHeapBaseAlignment() {
        int buildTimePageSize = SubstrateOptions.getPageSize();
        return Math.max(buildTimePageSize * ShenandoahConstants.cardSize(), ShenandoahRegionSize.getValue());
    }

    @Fold
    @Override
    public int getImageHeapAlignment() {
        return ShenandoahRegionSize.getValue();
    }

    @Fold
    @Override
    public int getImageHeapOffsetInAddressSpace() {
        int buildTimePageSize = SubstrateOptions.getPageSize();
        int result = Math.max(buildTimePageSize * ShenandoahConstants.cardSize(), ShenandoahRegionSize.getValue());
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
        Word pointer = Word.objectToUntrackedPointer(object);
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
            if (!ShenandoahRegionType.isHumongous(regionTypes[region])) {
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
        return ShenandoahLibrary.tearDown();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void setAccessedFieldOffsets(byte[] fieldOffsets) {
        assert this.accessedFieldOffsets == null;
        this.accessedFieldOffsets = fieldOffsets;
    }

    @Fold
    static int getByteArrayBaseOffset() {
        return ConfigurationValues.getObjectLayout().getArrayBaseOffset(JavaKind.Byte);
    }

    @Override
    public void prepareForSafepoint() {
        ShenandoahLibrary.prepareForSafepoint();
    }

    @Override
    public void endSafepoint() {
        ShenandoahLibrary.endSafepoint();
    }

    public static Pointer addressOfCardTableAddress() {
        return (Pointer) cardTableAddressTL.getAddress();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void validateInitState(ShenandoahInitState state) {
        VMError.guarantee(ShenandoahConstants.tlabTopOffset() == state.tlabTopOffset(), "Failed while validating the Shenandoah state: tlabTopOffset");
        VMError.guarantee(ShenandoahConstants.tlabEndOffset() == state.tlabEndOffset(), "Failed while validating the Shenandoah state: tlabEndOffset");
        VMError.guarantee(ShenandoahConstants.dirtyCardValue() == state.dirtyCardValue(), "Failed while validating the Shenandoah state: dirtyCardValue");
        VMError.guarantee(ShenandoahConstants.cardTableShift() == state.cardTableShift(), "Failed while validating the Shenandoah state: cardTableShift");
        VMError.guarantee(ShenandoahConstants.logOfHeapRegionGrainBytes() == state.logOfHeapRegionGrainBytes(), "Failed while validating the Shenandoah state: logOfHeapRegionGrainBytes");
        VMError.guarantee(ShenandoahConstants.javaThreadSize() == state.javaThreadSize(), "Failed while validating the Shenandoah state: javaThreadSize");
        VMError.guarantee(SizeOf.get(NativeGCVMOperationData.class) <= state.vmOperationDataSize(), "Failed while validating the Shenandoah state: vmOperationDataSize");
        VMError.guarantee(SizeOf.get(NativeGCVMOperationWrapperData.class) <= state.vmOperationWrapperDataSize(), "Failed while validating the Shenandoah state: vmOperationWrapperDataSize");
    }

    @Uninterruptible(reason = "Called during startup.")
    @Override
    public void attachThread(IsolateThread isolateThread) {
        if (isInitialized) {
            ShenandoahLibrary.attachThread(isolateThread);
        } else {
            /* The thread gets attached as a side effect of the initialization. */
            initialize(isolateThread);
        }
        cardTableAddressTL.set(isolateThread, cardTableAddress);
    }

    @Override
    @Uninterruptible(reason = "Thread is detaching and holds the THREAD_MUTEX.")
    public void detachThread(IsolateThread isolateThread) {
        ShenandoahLibrary.detachThread(isolateThread);

        /* Use a value, so that it looks as if the card table starts at address 0. */
        long invalidCardTableAddress = KnownIntrinsics.heapBase().unsignedShiftRight(ShenandoahConstants.cardTableShift()).rawValue();
        cardTableAddressTL.set(isolateThread, Word.signed(-invalidCardTableAddress));
    }

    @Override
    public void doReferenceHandling() {
        /* Nothing to do, Shenandoah only supports a dedicated reference handler thread. */
    }

    @Override
    public boolean hasReferencePendingList() {
        return ShenandoahLibrary.hasReferencePendingList();
    }

    @Override
    public void waitForReferencePendingList() throws InterruptedException {
        /*
         * The order is crucial here to prevent transient issues. First, we call into C++ to get the
         * current wakeup count, then we check if the thread was interrupted. This ensures that the
         * C++ code is able to properly detect the case where the thread is interrupted right before
         * blocking in ShenandoahLibrary.waitForReferencePendingList().
         */
        long initialWakeupCount = ShenandoahLibrary.getReferencePendingListWakeupCount();

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
            return ShenandoahLibrary.waitForReferencePendingList(initialWakeupCount);
        } finally {
            PlatformThreads.setThreadStatus(currentThread, oldThreadStatus);
        }
    }

    @Override
    public void wakeUpReferencePendingListWaiters() {
        ShenandoahLibrary.wakeUpReferencePendingListWaiters();
    }

    @Override
    @Uninterruptible(reason = "Prevent stack overflow exceptions and recurring callback execution.", calleeMustBe = false)
    public Reference<?> getAndClearReferencePendingList() {
        Word result = ShenandoahLibrary.getAndClearReferencePendingList();
        return (Reference<?>) result.toObject();
    }

    @Override
    public boolean printLocationInfo(Log log, UnsignedWord value, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        if (value.equal(KnownIntrinsics.heapBase())) {
            log.string("is the heap base");
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
        ShenandoahCommittedMemoryProvider memoryProvider = ImageSingletons.lookup(ShenandoahCommittedMemoryProvider.class);
        ShenandoahRegionInfo r = UnsafeStackValue.get(ShenandoahRegionInfo.class);
        for (int i = 0; i < memoryProvider.getMaxRegions(); i++) {
            if (ShenandoahLibrary.getRegionInfo(i, r)) {
                if (ptr.aboveOrEqual(r.bottom())) {
                    if (ptr.belowThan(r.top())) {
                        log.string("points into region ").signed(i).string(" (").string(ShenandoahRegionType.toString(r.regionType())).string(")");
                        return true;
                    } else if (ptr.belowThan(r.end())) {
                        log.string("points into unused space of region ").signed(i).string(" (").string(ShenandoahRegionType.toString(r.regionType())).string(")");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unused")
    private static boolean printGCInternalLocationInfo(Log log, Pointer ptr) {
        ShenandoahInternalState state = UnsafeStackValue.get(ShenandoahInternalState.class);
        fillGCInternalState(state);

        throw VMError.shouldNotReachHere("Unimplemented: check if this is a GC-internal location and print some debugging output in that case");
    }

    @Override
    public void optionValueChanged(RuntimeOptionKey<?> key) {
        /*
         * There is no need to inform Shenandoah about options that can only be set during isolate
         * startup.
         */
        if (!SubstrateUtil.HOSTED && !key.isIsolateCreationOnly()) {
            assert isInImageHeap(key.getName());
            Word optionName = Word.objectToUntrackedPointer(key.getName());
            long value = convertOptionValueToLong(key);
            ShenandoahLibrary.updateOptionValue(optionName, value);
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getThreadAllocatedMemory(IsolateThread thread) {
        return ShenandoahLibrary.getThreadAllocatedMemory(thread);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getUsedMemoryAfterLastGC() {
        return ShenandoahLibrary.getUsedMemoryAfterLastGC();
    }

    @Override
    @Uninterruptible(reason = "Ensure that no GC can occur between modification of the object and this call.", callerMustBe = true)
    public void dirtyAllReferencesOf(Object obj) {
        if (obj == null) {
            return;
        }

        VMError.guarantee(obj instanceof StoredContinuation);
        ShenandoahLibrary.dirtyAllReferencesOf(Word.objectToUntrackedPointer(obj));
    }

    @Override
    public long getMillisSinceLastWholeHeapExamined() {
        return ShenandoahLibrary.getMillisSinceLastWholeHeapExamined();
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

    private static void fillGCInternalState(ShenandoahInternalState state) {
        int size = SizeOf.get(ShenandoahInternalState.class);
        UnmanagedMemoryUtil.fill((Pointer) state, Word.unsigned(size), (byte) 0);
        ShenandoahLibrary.getGCInternalState(state);
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
            log.string("Region size: ").signed(ShenandoahRegionSize.getValue()).newline();
            log.string("Card table granularity: ").signed(ShenandoahConstants.cardSize()).newline();

            ShenandoahInternalState state = UnsafeStackValue.get(ShenandoahInternalState.class);
            fillGCInternalState(state);

            log.string("Full collections: ").unsigned(state.fullCollections()).newline();
            log.string("Total collections: ").unsigned(state.totalCollections()).newline();
            log.string("Card table: ").zhex(state.cardTableStart()).string(" - ").zhex(state.cardTableStart().add(state.cardTableSize()).subtract(1)).newline();
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
            int maxRegions = ImageSingletons.lookup(ShenandoahCommittedMemoryProvider.class).getMaxRegions();
            ShenandoahRegionInfo regionInfo = UnsafeStackValue.get(ShenandoahRegionInfo.class);

            log.string("Heap regions:").indent(true);
            int printed = 0;
            for (int i = 0; i < maxRegions; i++) {
                if (printed >= MAX_REGIONS_TO_PRINT) {
                    log.string("... (truncated)").newline();
                    break;
                }

                if (ShenandoahLibrary.getRegionInfo(i, regionInfo)) {
                    printRegion(log, i, regionInfo);
                    printed++;
                }
            }
            log.indent(false);
        }

        @SuppressWarnings("unused")
        private static void printRegion(Log log, int regionIndex, ShenandoahRegionInfo r) {
            throw VMError.shouldNotReachHere("Unimplemented: region printing");
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
                CCharPointer name = ShenandoahLibrary.getCurrentThreadName();
                if (name.isNonNull()) {
                    log.string("Internal name of crashing thread: ").string(name).newline();
                }
            }
        }
    }
}

@TargetClass(value = Runtime.class, onlyWith = UseShenandoahGC.class)
@SuppressWarnings({"static-method"})
final class Target_java_lang_Runtime {
    @Substitute
    private long freeMemory() {
        return ShenandoahLibrary.getFreeMemory();
    }

    @Substitute
    private long totalMemory() {
        return ShenandoahLibrary.getTotalMemory();
    }

    @Substitute
    private long maxMemory() {
        return ShenandoahLibrary.getMaxMemory();
    }

    @Substitute
    private void gc() {
        ShenandoahLibrary.collect(GCCause.JavaLangSystemGC.getId());
    }
}
