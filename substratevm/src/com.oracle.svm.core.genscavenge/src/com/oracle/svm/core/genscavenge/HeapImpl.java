/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateDiagnostics;
import com.oracle.svm.core.SubstrateDiagnostics.DiagnosticThunk;
import com.oracle.svm.core.SubstrateDiagnostics.DiagnosticThunkRegistry;
import com.oracle.svm.core.SubstrateDiagnostics.ErrorContext;
import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.genscavenge.AlignedHeapChunk.AlignedHeader;
import com.oracle.svm.core.genscavenge.ThreadLocalAllocation.Descriptor;
import com.oracle.svm.core.genscavenge.UnalignedHeapChunk.UnalignedHeader;
import com.oracle.svm.core.genscavenge.graal.ForcedSerialPostWriteBarrier;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.heap.ReferenceHandler;
import com.oracle.svm.core.heap.ReferenceHandlerThread;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RuntimeCodeInfoGCSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicReference;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.events.SystemGCEvent;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.ThreadStatus;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.thread.VMThreads.SafepointBehavior;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.directives.GraalDirectives;
import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.nodes.memory.address.OffsetAddressNode;
import jdk.graal.compiler.word.Word;

public final class HeapImpl extends Heap {
    /** Synchronization means for notifying {@link #refPendingList} waiters without deadlocks. */
    private static final VMMutex REF_MUTEX = new VMMutex("referencePendingList");
    private static final VMCondition REF_CONDITION = new VMCondition(REF_MUTEX);

    // Singleton instances, created during image generation.
    private final YoungGeneration youngGeneration = new YoungGeneration("YoungGeneration");
    private final OldGeneration oldGeneration = new OldGeneration("OldGeneration");
    private final HeapChunkProvider chunkProvider = new HeapChunkProvider();
    private final ObjectHeaderImpl objectHeaderImpl = new ObjectHeaderImpl();
    private final GCImpl gcImpl;
    private final RuntimeCodeInfoGCSupportImpl runtimeCodeInfoGcSupport;
    private final ImageHeapInfo firstImageHeapInfo = new ImageHeapInfo();
    private final HeapAccounting accounting = new HeapAccounting();

    /** Head of the linked list of currently pending (ready to be enqueued) {@link Reference}s. */
    private Reference<?> refPendingList;
    /** Total number of times when a new pending reference list became available. */
    private volatile long refListOfferCounter;
    /** Total number of times when threads waiting for a pending reference list were interrupted. */
    private volatile long refListWaiterWakeUpCounter;

    /** Head of the linked list of object pins. */
    private final AtomicReference<PinnedObjectImpl> pinHead = new AtomicReference<>();

    /** A cached list of all the classes, if someone asks for it. */
    private List<Class<?>> classList;

    @Platforms(Platform.HOSTED_ONLY.class)
    public HeapImpl() {
        this.gcImpl = new GCImpl();
        this.runtimeCodeInfoGcSupport = new RuntimeCodeInfoGCSupportImpl();
        HeapParameters.initialize();
        DiagnosticThunkRegistry.singleton().add(new DumpHeapSettingsAndStatistics());
        DiagnosticThunkRegistry.singleton().add(new DumpHeapUsage());
        DiagnosticThunkRegistry.singleton().add(new DumpChunkInformation());
    }

    @Fold
    public static HeapImpl getHeapImpl() {
        Heap heap = Heap.getHeap();
        assert heap instanceof HeapImpl : "VMConfiguration heap is not a HeapImpl.";
        return (HeapImpl) heap;
    }

    @Fold
    public static ImageHeapInfo getFirstImageHeapInfo() {
        return getHeapImpl().firstImageHeapInfo;
    }

    @Fold
    static HeapChunkProvider getChunkProvider() {
        return getHeapImpl().chunkProvider;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @Override
    public boolean isInImageHeap(Object obj) {
        // This method is not really uninterruptible (mayBeInlined) but converts arbitrary objects
        // to pointers. An object that is outside the image heap may be moved by a GC but it will
        // never be moved into the image heap. So, this is fine.
        return isInImageHeap(Word.objectToUntrackedPointer(obj));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInImageHeap(Pointer objPointer) {
        return isInPrimaryImageHeap(objPointer) || (AuxiliaryImageHeap.isPresent() && AuxiliaryImageHeap.singleton().containsObject(objPointer));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInPrimaryImageHeap(Object obj) {
        // This method is not really uninterruptible (mayBeInlined) but converts arbitrary objects
        // to pointers. An object that is outside the image heap may be moved by a GC but it will
        // never be moved into the image heap. So, this is fine.
        return isInPrimaryImageHeap(Word.objectToUntrackedPointer(obj));
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInPrimaryImageHeap(Pointer objPointer) {
        for (ImageHeapInfo info = firstImageHeapInfo; info != null; info = info.next) {
            if (info.isInImageHeap(objPointer)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void suspendAllocation() {
        ThreadLocalAllocation.suspendInCurrentThread();
    }

    @Override
    public void resumeAllocation() {
        // Nothing to do - the next allocation will refill the TLAB.
    }

    @Override
    public boolean walkObjects(ObjectVisitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("must only be executed at a safepoint");
        return walkImageHeapObjects(visitor) && walkCollectedHeapObjects(visitor);
    }

    /** Tear down the heap and release its memory. */
    @Override
    @Uninterruptible(reason = "Tear-down in progress.")
    public boolean tearDown() {
        youngGeneration.tearDown();
        oldGeneration.tearDown();
        getChunkProvider().tearDown();
        return true;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public ObjectHeader getObjectHeader() {
        return objectHeaderImpl;
    }

    @Fold
    static ObjectHeaderImpl getObjectHeaderImpl() {
        return getHeapImpl().objectHeaderImpl;
    }

    @Fold
    @Override
    public GC getGC() {
        return getHeapImpl().gcImpl;
    }

    @Fold
    static GCImpl getGCImpl() {
        return getHeapImpl().gcImpl;
    }

    @Fold
    @Override
    public RuntimeCodeInfoGCSupport getRuntimeCodeInfoGCSupport() {
        return runtimeCodeInfoGcSupport;
    }

    @Fold
    public static HeapAccounting getAccounting() {
        return getHeapImpl().accounting;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isAllocationDisallowed() {
        return NoAllocationVerifier.isActive() || SafepointBehavior.ignoresSafepoints();
    }

    /** A guard to place before an allocation, giving the call site and the allocation type. */
    static void exitIfAllocationDisallowed(String callSite, String typeName) {
        if (HeapImpl.getHeapImpl().isAllocationDisallowed()) {
            NoAllocationVerifier.exit(callSite, typeName);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public YoungGeneration getYoungGeneration() {
        return youngGeneration;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public OldGeneration getOldGeneration() {
        return oldGeneration;
    }

    @Fold
    AtomicReference<PinnedObjectImpl> getPinHead() {
        return pinHead;
    }

    void logUsage(Log log) {
        youngGeneration.logUsage(log);
        oldGeneration.logUsage(log);
    }

    void logChunks(Log log) {
        getYoungGeneration().logChunks(log);
        getOldGeneration().logChunks(log);
        getChunkProvider().logFreeChunks(log);
    }

    void logImageHeapPartitionBoundaries(Log log) {
        log.string("Native image heap boundaries:").indent(true);
        for (ImageHeapInfo info = firstImageHeapInfo; info != null; info = info.next) {
            info.print(log);
        }
        log.indent(false);

        if (AuxiliaryImageHeap.isPresent()) {
            ImageHeapInfo auxHeapInfo = AuxiliaryImageHeap.singleton().getImageHeapInfo();
            if (auxHeapInfo != null) {
                log.string("Auxiliary image heap boundaries:").indent(true);
                auxHeapInfo.print(log);
                log.indent(false);
            }
        }
    }

    /** Log the zap values to make it easier to search for them. */
    static void logZapValues(Log log) {
        if (HeapParameters.getZapProducedHeapChunks() || HeapParameters.getZapConsumedHeapChunks()) {
            /* Padded with spaces so the columns line up between the int and word variants. */
            if (HeapParameters.getZapProducedHeapChunks()) {
                log.string("producedHeapChunkZapInt: ") //
                                .string(" hex: ").spaces(8).hex(HeapParameters.getProducedHeapChunkZapInt()) //
                                .string(" signed: ").spaces(9).signed(HeapParameters.getProducedHeapChunkZapInt()) //
                                .string(" unsigned: ").spaces(10).unsigned(HeapParameters.getProducedHeapChunkZapInt()).newline();
                log.string("producedHeapChunkZapWord:") //
                                .string(" hex: ").hex(HeapParameters.getProducedHeapChunkZapWord()) //
                                .string(" signed: ").signed(HeapParameters.getProducedHeapChunkZapWord()) //
                                .string(" unsigned: ").unsigned(HeapParameters.getProducedHeapChunkZapWord()).newline();
            }
            if (HeapParameters.getZapConsumedHeapChunks()) {
                log.string("consumedHeapChunkZapInt: ") //
                                .string(" hex: ").spaces(8).hex(HeapParameters.getConsumedHeapChunkZapInt()) //
                                .string(" signed: ").spaces(10).signed(HeapParameters.getConsumedHeapChunkZapInt()) //
                                .string(" unsigned: ").spaces(10).unsigned(HeapParameters.getConsumedHeapChunkZapInt()).newline();
                log.string("consumedHeapChunkZapWord:") //
                                .string(" hex: ").hex(HeapParameters.getConsumedHeapChunkZapWord()) //
                                .string(" signed: ").signed(HeapParameters.getConsumedHeapChunkZapWord()) //
                                .string(" unsigned: ").unsigned(HeapParameters.getConsumedHeapChunkZapWord()).newline();
            }
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getClassCount() {
        int count = firstImageHeapInfo.dynamicHubCount;
        for (ImageHeapInfo info = firstImageHeapInfo.next; info != null; info = info.next) {
            count += info.dynamicHubCount;
        }
        return count;
    }

    @Override
    protected List<Class<?>> getAllClasses() {
        /* Two threads might race to set classList, but they compute the same result. */
        if (classList == null) {
            ArrayList<Class<?>> list = findAllDynamicHubs();
            /* Ensure that other threads see consistent values once the list is published. */
            MembarNode.memoryBarrier(MembarNode.FenceKind.STORE_STORE);
            classList = list;
        }
        return classList;
    }

    private ArrayList<Class<?>> findAllDynamicHubs() {
        int dynamicHubCount = getClassCount();

        ArrayList<Class<?>> list = new ArrayList<>(dynamicHubCount);
        for (ImageHeapInfo info = firstImageHeapInfo; info != null; info = info.next) {
            ImageHeapWalker.walkRegions(info, new ClassListBuilderVisitor(list.size() + info.dynamicHubCount, list));
        }

        VMError.guarantee(dynamicHubCount == list.size(), "Found fewer DynamicHubs in the image heap than expected.");
        return list;
    }

    private static class ClassListBuilderVisitor implements MemoryWalker.ImageHeapRegionVisitor, ObjectVisitor {
        private final int dynamicHubCount;
        private final List<Class<?>> list;

        ClassListBuilderVisitor(int dynamicHubCount, List<Class<?>> list) {
            this.dynamicHubCount = dynamicHubCount;
            this.list = list;
        }

        @Override
        public <T> boolean visitNativeImageHeapRegion(T region, MemoryWalker.NativeImageHeapRegionAccess<T> access) {
            if (!access.isWritable(region) && !access.consistsOfHugeObjects(region)) {
                return access.visitObjects(region, this);
            }
            return true;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "Allocation is fine: this method traverses only the image heap.")
        public boolean visitObject(Object o) {
            if (o instanceof Class<?>) {
                list.add((Class<?>) o);
                return list.size() != dynamicHubCount;
            }
            return true;
        }
    }

    @Override
    public void prepareForSafepoint() {
        // nothing to do
    }

    @Override
    public void endSafepoint() {
        // nothing to do
    }

    @Uninterruptible(reason = "Called during startup.")
    @Override
    public void attachThread(IsolateThread isolateThread) {
        // nothing to do
    }

    @Override
    @Uninterruptible(reason = "Thread is detaching and holds the THREAD_MUTEX.")
    public void detachThread(IsolateThread isolateThread) {
        ThreadLocalAllocation.disableAndFlushForThread(isolateThread);
    }

    @Fold
    public static boolean usesImageHeapCardMarking() {
        Boolean enabled = SerialGCOptions.ImageHeapCardMarking.getValue();
        if (enabled == Boolean.FALSE || enabled == null && !SubstrateOptions.useRememberedSet()) {
            return false;
        } else if (enabled == null) {
            return isImageHeapAligned();
        }
        UserError.guarantee(isImageHeapAligned(),
                        "Enabling option %s requires a custom image heap alignment at runtime, which cannot be ensured with the current configuration (option %s might be disabled)",
                        SerialGCOptions.ImageHeapCardMarking, SubstrateOptions.SpawnIsolates);
        return true;
    }

    @Fold
    @Override
    public int getPreferredAddressSpaceAlignment() {
        return UnsignedUtils.safeToInt(HeapParameters.getAlignedHeapChunkAlignment());
    }

    @Fold
    @Override
    public int getImageHeapOffsetInAddressSpace() {
        int imageHeapOffset = 0;
        if (SubstrateOptions.SpawnIsolates.getValue() && SubstrateOptions.UseNullRegion.getValue()) {
            /*
             * The image heap will be mapped in a way that there is a memory protected gap between
             * the heap base and the start of the image heap. The gap won't need any memory in the
             * native image file.
             */
            imageHeapOffset = NumUtil.safeToInt(SerialAndEpsilonGCOptions.AlignedHeapChunkSize.getValue());
        }
        /*
         * GR-53993: With ImageLayerSupport, it would be possible to fetch the startOffset from the
         * loader instead of storing it in the Heap.
         */
        if (SubstrateOptions.LoadImageLayer.hasBeenSet()) {
            /*
             * GR-53964: The page size used to round up the start offset should be the same as the
             * one used in run time.
             */
            int runtimePageSize = 4096;
            imageHeapOffset = NumUtil.roundUp(imageHeapOffset + NumUtil.safeToInt(startOffset), runtimePageSize);
        }
        return imageHeapOffset;
    }

    @Fold
    public static boolean isImageHeapAligned() {
        return SubstrateOptions.SpawnIsolates.getValue();
    }

    @Override
    public boolean walkImageHeapObjects(ObjectVisitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("Must only be called at a safepoint");
        if (visitor == null) {
            return true;
        }
        for (ImageHeapInfo info = firstImageHeapInfo; info != null; info = info.next) {
            if (!ImageHeapWalker.walkImageHeapObjects(info, visitor)) {
                return false;
            }
        }
        return !AuxiliaryImageHeap.isPresent() || AuxiliaryImageHeap.singleton().walkObjects(visitor);
    }

    @Override
    public boolean walkCollectedHeapObjects(ObjectVisitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("Must only be called at a safepoint");
        ThreadLocalAllocation.disableAndFlushForAllThreads();
        return getYoungGeneration().walkObjects(visitor) && getOldGeneration().walkObjects(visitor);
    }

    @Override
    public void doReferenceHandling() {
        if (ReferenceHandler.isExecutedManually()) {
            GCImpl.doReferenceHandling();
        }
    }

    @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "Only the GC increments the volatile field 'refListOfferCounter'.")
    void addToReferencePendingList(Reference<?> list) {
        assert VMOperation.isGCInProgress();
        if (list == null) {
            return;
        }

        REF_MUTEX.lock();
        try {
            if (refPendingList != null) { // append
                Reference<?> current = refPendingList;
                Reference<?> next = ReferenceInternals.getNextDiscovered(current);
                while (next != null) {
                    current = next;
                    next = ReferenceInternals.getNextDiscovered(current);
                }
                ReferenceInternals.setNextDiscovered(current, list);
                // No need to notify: waiters would have been notified about the existing list
            } else {
                refPendingList = list;
                refListOfferCounter++;
                REF_CONDITION.broadcast();
            }
        } finally {
            REF_MUTEX.unlock();
        }
    }

    @Override
    @Uninterruptible(reason = "Safepoint while holding the lock could lead to a deadlock in GC.")
    public boolean hasReferencePendingList() {
        REF_MUTEX.lockNoTransition();
        try {
            return hasReferencePendingListUnsafe();
        } finally {
            REF_MUTEX.unlock();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean hasReferencePendingListUnsafe() {
        return refPendingList != null;
    }

    @Override
    public void waitForReferencePendingList() throws InterruptedException {
        /*
         * This method is only execute by the reference handler thread. So, it does not cause any
         * harm if it wakes up too frequently. However, we must guarantee that it does not miss any
         * updates. As awaitPendingRefsInNative() can't directly use object references, we use the
         * offer count and the wakeup count to detect if this thread was interrupted or if a new
         * pending reference list was set in the meanwhile. To prevent transient issues, the
         * execution order is crucial:
         *
         * - Another thread could set a new reference pending list at any safepoint. As
         * awaitPendingRefsInNative() can only access the offer count, it is necessary to ensure
         * that the offer count is reasonably accurate. So, we read the offer count *before*
         * checking if there is already a pending reference list. If there is no pending reference
         * list, then the read offer count is accurate (there is no other thread that could clear
         * the pending reference list in the meanwhile).
         *
         * - Another thread could interrupt this thread at any time. Interrupting will first set the
         * interrupted flag and then increment the wakeup count. So, it is crucial that we do
         * everything in the reverse order here: we read the wakeup count *before* the call to
         * Thread.interrupted().
         */
        assert ReferenceHandlerThread.isReferenceHandlerThread();
        long initialOffers = refListOfferCounter;
        long initialWakeUps = refListWaiterWakeUpCounter;
        if (hasReferencePendingList()) {
            return;
        }

        // Throw an InterruptedException if the thread is interrupted before or after waiting.
        if (Thread.interrupted() || (!waitForPendingReferenceList(initialOffers, initialWakeUps) && Thread.interrupted())) {
            throw new InterruptedException();
        }
    }

    private static boolean waitForPendingReferenceList(long initialOffers, long initialWakeUps) {
        Thread currentThread = Thread.currentThread();
        int oldThreadStatus = PlatformThreads.getThreadStatus(currentThread);
        PlatformThreads.setThreadStatus(currentThread, ThreadStatus.PARKED);
        try {
            return transitionToNativeThenAwaitPendingRefs(initialOffers, initialWakeUps);
        } finally {
            PlatformThreads.setThreadStatus(currentThread, oldThreadStatus);
        }
    }

    @NeverInline("Must not be inlined in a caller that has an exception handler: We only support InvokeNode and not InvokeWithExceptionNode between a CFunctionPrologueNode and CFunctionEpilogueNode")
    private static boolean transitionToNativeThenAwaitPendingRefs(long initialOffers, long initialWakeUps) {
        // Note that we cannot hold the lock going into or out of native because we could enter a
        // safepoint during the transition and would risk a deadlock with the VMOperation.
        CFunctionPrologueNode.cFunctionPrologue(VMThreads.StatusSupport.STATUS_IN_NATIVE);
        boolean offered = awaitPendingRefsInNative(initialOffers, initialWakeUps);
        CFunctionEpilogueNode.cFunctionEpilogue(VMThreads.StatusSupport.STATUS_IN_NATIVE);
        return offered;
    }

    @Uninterruptible(reason = "In native.")
    @NeverInline("Provide a return address for the Java frame anchor.")
    private static boolean awaitPendingRefsInNative(long initialOffers, long initialWakeUps) {
        /*
         * This method is executing in native state and must not deal with object references.
         * Therefore it has to be static and cannot access the `refPendingList` field either. We
         * work around this by indicating updates and interrupts via counter updates. We can safely
         * access those counters as fields of HeapImpl as long as we can get the HeapImpl instance
         * folded to its memory address so that the field accesses become direct memory reads.
         */
        REF_MUTEX.lockNoTransition();
        try {
            while (getHeapImpl().refListOfferCounter == initialOffers && getHeapImpl().refListWaiterWakeUpCounter == initialWakeUps) {
                REF_CONDITION.blockNoTransition();
            }
            return getHeapImpl().refListWaiterWakeUpCounter == initialWakeUps;
        } finally {
            REF_MUTEX.unlock();
        }
    }

    @Override
    @Uninterruptible(reason = "Safepoint while holding the lock could lead to a deadlock in GC.")
    @SuppressFBWarnings(value = "VO_VOLATILE_INCREMENT", justification = "We use a lock when incrementing the volatile field 'refListWaiterWakeUpCounter'.")
    public void wakeUpReferencePendingListWaiters() {
        REF_MUTEX.lockNoTransition();
        try {
            refListWaiterWakeUpCounter++;
            REF_CONDITION.broadcast();
        } finally {
            REF_MUTEX.unlock();
        }
    }

    @Override
    @Uninterruptible(reason = "Safepoint while holding the lock could lead to a deadlock in GC.")
    public Reference<?> getAndClearReferencePendingList() {
        REF_MUTEX.lockNoTransition();
        try {
            Reference<?> list = refPendingList;
            if (list != null) {
                refPendingList = null;
            }
            return list;
        } finally {
            REF_MUTEX.unlock();
        }
    }

    @Override
    public boolean printLocationInfo(Log log, UnsignedWord value, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        if (SubstrateOptions.SpawnIsolates.getValue()) {
            Pointer heapBase = KnownIntrinsics.heapBase();
            if (value.equal(heapBase)) {
                log.string("is the heap base");
                return true;
            } else if (value.aboveThan(heapBase) && value.belowThan(getImageHeapStart())) {
                log.string("points into the protected memory between the heap base and the image heap");
                return true;
            }
        }

        if (objectHeaderImpl.isEncodedObjectHeader((Word) value)) {
            log.string("is the encoded object header for an object of type ");
            DynamicHub hub = objectHeaderImpl.dynamicHubFromObjectHeader((Word) value);
            log.string(hub.getName());
            return true;
        }

        Pointer ptr = (Pointer) value;
        if (printLocationInfo(log, ptr, allowJavaHeapAccess, allowUnsafeOperations)) {
            if (allowJavaHeapAccess && objectHeaderImpl.pointsToObjectHeader(ptr)) {
                log.indent(true);
                SubstrateDiagnostics.printObjectInfo(log, ptr.toObject());
                log.redent(false);
            }
            return true;
        }
        return false;
    }

    @Override
    public void optionValueChanged(RuntimeOptionKey<?> key) {
        if (!SubstrateUtil.HOSTED) {
            GCImpl.getPolicy().updateSizeParameters();
        }
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public long getThreadAllocatedMemory(IsolateThread thread) {
        UnsignedWord allocatedBytes = ThreadLocalAllocation.allocatedBytes.getVolatile(thread);

        /*
         * The current aligned chunk in the TLAB is only partially filled and therefore not yet
         * accounted for in ThreadLocalAllocation.allocatedBytes. The reads below are unsynchronized
         * and unordered with the thread updating its TLAB, so races may occur. We only use the read
         * values if they are plausible and not obviously racy. We also accept that certain races
         * can cause that the memory in the current aligned TLAB chunk is counted twice.
         */
        ThreadLocalAllocation.Descriptor tlab = ThreadLocalAllocation.getTlab(thread);
        AlignedHeader alignedTlab = tlab.getAlignedChunk();
        Pointer top = tlab.getAllocationTop(SubstrateAllocationSnippets.TLAB_TOP_IDENTITY);
        Pointer start = AlignedHeapChunk.getObjectsStart(alignedTlab);

        if (top.aboveThan(start)) {
            UnsignedWord usedTlabSize = top.subtract(start);
            if (usedTlabSize.belowOrEqual(HeapParameters.getAlignedHeapChunkSize())) {
                return allocatedBytes.add(usedTlabSize).rawValue();
            }
        }
        return allocatedBytes.rawValue();
    }

    @Override
    @Uninterruptible(reason = "Ensure that no GC can occur between modification of the object and this call.", callerMustBe = true)
    public void dirtyAllReferencesOf(Object obj) {
        if (SubstrateOptions.useRememberedSet() && obj != null) {
            ForcedSerialPostWriteBarrier.force(OffsetAddressNode.address(obj, 0), false);
        }
    }

    @Override
    public long getMillisSinceLastWholeHeapExamined() {
        return HeapImpl.getGCImpl().getMillisSinceLastWholeHeapExamined();
    }

    @Override
    @Uninterruptible(reason = "Ensure that no GC can move the object to another chunk.", callerMustBe = true)
    public long getIdentityHashSalt(Object obj) {
        if (!GraalDirectives.inIntrinsic()) {
            assert !isInImageHeap(obj) : "Image heap objects have identity hash code fields";
        }
        HeapChunk.Header<?> chunk = HeapChunk.getEnclosingHeapChunk(obj);
        return HeapChunk.getIdentityHashSalt(chunk).rawValue();
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getUsedMemoryAfterLastGC() {
        return accounting.getUsedBytes();
    }

    private boolean printLocationInfo(Log log, Pointer ptr, boolean allowJavaHeapAccess, boolean allowUnsafeOperations) {
        for (ImageHeapInfo info = firstImageHeapInfo; info != null; info = info.next) {
            if (info.isInReadOnlyRegularPartition(ptr)) {
                log.string("points into the image heap (read-only)");
                return true;
            } else if (info.isInReadOnlyRelocatablePartition(ptr)) {
                log.string("points into the image heap (read-only relocatables)");
                return true;
            } else if (info.isInWritableRegularPartition(ptr)) {
                log.string("points into the image heap (writable)");
                return true;
            } else if (info.isInWritableHugePartition(ptr)) {
                log.string("points into the image heap (writable huge)");
                return true;
            } else if (info.isInReadOnlyHugePartition(ptr)) {
                log.string("points into the image heap (read-only huge)");
                return true;
            }
        }

        if (AuxiliaryImageHeap.isPresent() && AuxiliaryImageHeap.singleton().containsObject(ptr)) {
            log.string("points into the auxiliary image heap");
            return true;
        } else if (printTlabInfo(log, ptr, CurrentIsolate.getCurrentThread())) {
            return true;
        }

        if (allowJavaHeapAccess) {
            // Accessing spaces and chunks is safe if we prevent a GC.
            if (isInYoungGen(ptr)) {
                log.string("points into the young generation");
                return true;
            } else if (isInOldGen(ptr)) {
                log.string("points into the old generation");
                return true;
            }
        }

        if (allowUnsafeOperations || VMOperation.isInProgressAtSafepoint()) {
            // If we are not at a safepoint, then it is unsafe to access thread locals of another
            // thread as the IsolateThread could be freed at any time.
            return printTlabInfo(log, ptr);
        }
        return false;
    }

    boolean isInHeap(Pointer ptr) {
        return isInImageHeap(ptr) || isInYoungGen(ptr) || isInOldGen(ptr);
    }

    @Uninterruptible(reason = "Prevent that chunks are freed.")
    private boolean isInYoungGen(Pointer ptr) {
        if (findPointerInSpace(youngGeneration.getEden(), ptr)) {
            return true;
        }

        for (int i = 0; i < youngGeneration.getMaxSurvivorSpaces(); i++) {
            if (findPointerInSpace(youngGeneration.getSurvivorFromSpaceAt(i), ptr)) {
                return true;
            }
            if (findPointerInSpace(youngGeneration.getSurvivorToSpaceAt(i), ptr)) {
                return true;
            }
        }
        return false;
    }

    @Uninterruptible(reason = "Prevent that chunks are freed.")
    private boolean isInOldGen(Pointer ptr) {
        return findPointerInSpace(oldGeneration.getFromSpace(), ptr) || findPointerInSpace(oldGeneration.getToSpace(), ptr);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean findPointerInSpace(Space space, Pointer p) {
        AlignedHeapChunk.AlignedHeader aChunk = space.getFirstAlignedHeapChunk();
        while (aChunk.isNonNull()) {
            Pointer start = AlignedHeapChunk.getObjectsStart(aChunk);
            if (start.belowOrEqual(p) && p.belowThan(HeapChunk.getTopPointer(aChunk))) {
                return true;
            }
            aChunk = HeapChunk.getNext(aChunk);
        }

        UnalignedHeapChunk.UnalignedHeader uChunk = space.getFirstUnalignedHeapChunk();
        while (uChunk.isNonNull()) {
            Pointer start = UnalignedHeapChunk.getObjectStart(uChunk);
            if (start.belowOrEqual(p) && p.belowThan(HeapChunk.getTopPointer(uChunk))) {
                return true;
            }
            uChunk = HeapChunk.getNext(uChunk);
        }
        return false;
    }

    private static boolean printTlabInfo(Log log, Pointer ptr) {
        for (IsolateThread thread = VMThreads.firstThreadUnsafe(); thread.isNonNull(); thread = VMThreads.nextThread(thread)) {
            if (printTlabInfo(log, ptr, thread)) {
                return true;
            }
        }
        return false;
    }

    private static boolean printTlabInfo(Log log, Pointer ptr, IsolateThread thread) {
        ThreadLocalAllocation.Descriptor tlab = getTlabUnsafe(thread);
        AlignedHeader aChunk = tlab.getAlignedChunk();
        while (aChunk.isNonNull()) {
            Pointer dataStart = AlignedHeapChunk.getObjectsStart(aChunk);
            Pointer dataEnd = AlignedHeapChunk.getObjectsEnd(aChunk);
            if (ptr.aboveOrEqual(dataStart) && ptr.belowThan(dataEnd)) {
                log.string("points into an aligned TLAB chunk of thread ").zhex(thread);
                return true;
            }
            aChunk = HeapChunk.getNext(aChunk);
        }

        UnalignedHeader uChunk = tlab.getUnalignedChunk();
        while (uChunk.isNonNull()) {
            Pointer dataStart = UnalignedHeapChunk.getObjectStart(uChunk);
            Pointer dataEnd = UnalignedHeapChunk.getObjectEnd(uChunk);
            if (ptr.aboveOrEqual(dataStart) && ptr.belowThan(dataEnd)) {
                log.string("points into an unaligned TLAB chunk of thread ").zhex(thread);
                return true;
            }
            uChunk = HeapChunk.getNext(uChunk);
        }

        return false;
    }

    @Uninterruptible(reason = "This whole method is unsafe, so it is only uninterruptible to satisfy the checks.")
    private static Descriptor getTlabUnsafe(IsolateThread thread) {
        assert SubstrateDiagnostics.isFatalErrorHandlingThread() : "can cause crashes, so it may only be used while printing diagnostics";
        return ThreadLocalAllocation.getTlab(thread);
    }

    private static class DumpHeapSettingsAndStatistics extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            log.string("Heap settings and statistics:").indent(true);
            log.string("Supports isolates: ").bool(SubstrateOptions.SpawnIsolates.getValue()).newline();
            if (SubstrateOptions.SpawnIsolates.getValue()) {
                log.string("Heap base: ").zhex(KnownIntrinsics.heapBase()).newline();
            }
            log.string("Object reference size: ").signed(ConfigurationValues.getObjectLayout().getReferenceSize()).newline();
            log.string("Reserved object header bits: 0b").number(Heap.getHeap().getObjectHeader().getReservedBitsMask(), 2, false).newline();

            log.string("Aligned chunk size: ").unsigned(HeapParameters.getAlignedHeapChunkSize()).newline();
            log.string("Large array threshold: ").unsigned(HeapParameters.getLargeArrayThreshold()).newline();

            GCAccounting accounting = GCImpl.getAccounting();
            log.string("Incremental collections: ").unsigned(accounting.getIncrementalCollectionCount()).newline();
            log.string("Complete collections: ").unsigned(accounting.getCompleteCollectionCount()).newline();

            logZapValues(log);

            log.indent(false);
        }
    }

    private static class DumpHeapUsage extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            log.string("Heap usage:").indent(true);
            HeapImpl.getHeapImpl().logUsage(log);
            log.indent(false);
        }
    }

    private static class DumpChunkInformation extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            HeapImpl heap = HeapImpl.getHeapImpl();
            heap.logImageHeapPartitionBoundaries(log);

            log.string("Heap chunks: E=eden, S=survivor, O=old, F=free; A=aligned chunk, U=unaligned chunk; T=to space").indent(true);
            heap.logChunks(log);
            log.indent(false);
        }
    }
}

@TargetClass(value = java.lang.Runtime.class, onlyWith = UseSerialOrEpsilonGC.class)
@SuppressWarnings("static-method")
final class Target_java_lang_Runtime {
    @Substitute
    private long freeMemory() {
        return maxMemory() - HeapImpl.getAccounting().getUsedBytes().rawValue();
    }

    @Substitute
    private long totalMemory() {
        return maxMemory();
    }

    @Substitute
    private long maxMemory() {
        PhysicalMemory.size(); // ensure physical memory size is set correctly and not estimated
        GCImpl.getPolicy().updateSizeParameters();
        return GCImpl.getPolicy().getMaximumHeapSize().rawValue();
    }

    @Substitute
    private void gc() {
        if (!SubstrateGCOptions.DisableExplicitGC.getValue()) {
            long startTicks = JfrTicks.elapsedTicks();
            GCImpl.getGCImpl().collectCompletely(GCCause.JavaLangSystemGC);
            SystemGCEvent.emit(startTicks, false);
        }
    }
}
