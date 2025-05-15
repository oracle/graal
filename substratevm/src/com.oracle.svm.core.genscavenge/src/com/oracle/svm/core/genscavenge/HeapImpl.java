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

import java.io.Serial;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
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
import com.oracle.svm.core.genscavenge.metaspace.MetaspaceImpl;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.ReferenceHandler;
import com.oracle.svm.core.heap.ReferenceHandlerThread;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RuntimeCodeInfoGCSupport;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jfr.JfrTicks;
import com.oracle.svm.core.jfr.events.SystemGCEvent;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.metaspace.Metaspace;
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
import jdk.graal.compiler.core.common.SuppressFBWarnings;
import jdk.graal.compiler.nodes.extended.MembarNode;
import jdk.graal.compiler.word.Word;

public final class HeapImpl extends Heap {
    /** Synchronization means for notifying {@link #refPendingList} waiters without deadlocks. */
    private static final VMMutex REF_MUTEX = new VMMutex("referencePendingList");
    private static final VMCondition REF_CONDITION = new VMCondition(REF_MUTEX);

    // Singleton instances, created during image generation.
    private final YoungGeneration youngGeneration = new YoungGeneration("YoungGeneration");
    private final OldGeneration oldGeneration;
    private final HeapChunkProvider chunkProvider = new HeapChunkProvider();
    private final ObjectHeaderImpl objectHeaderImpl = new ObjectHeaderImpl();
    private final GCImpl gcImpl;
    private final RuntimeCodeInfoGCSupportImpl runtimeCodeInfoGcSupport;
    private final HeapAccounting accounting = new HeapAccounting();

    /** Head of the linked list of currently pending (ready to be enqueued) {@link Reference}s. */
    private Reference<?> refPendingList;
    /** Total number of times when a new pending reference list became available. */
    private volatile long refListOfferCounter;
    /** Total number of times when threads waiting for a pending reference list were interrupted. */
    private volatile long refListWaiterWakeUpCounter;

    /** A cached list of all the classes, if someone asks for it. */
    private List<Class<?>> classList;

    @Platforms(Platform.HOSTED_ONLY.class)
    public HeapImpl() {
        this.gcImpl = new GCImpl();
        this.runtimeCodeInfoGcSupport = new RuntimeCodeInfoGCSupportImpl();
        this.oldGeneration = SerialGCOptions.useCompactingOldGen() ? new CompactingOldGeneration("OldGeneration")
                        : new CopyingOldGeneration("OldGeneration");
        if (ImageLayerBuildingSupport.firstImageBuild()) {
            DiagnosticThunkRegistry.singleton().add(new DumpHeapSettingsAndStatistics());
            DiagnosticThunkRegistry.singleton().add(new DumpHeapUsage());
            DiagnosticThunkRegistry.singleton().add(new DumpGCPolicy());
            DiagnosticThunkRegistry.singleton().add(new DumpImageHeapInfo());
            DiagnosticThunkRegistry.singleton().add(new DumpChunkInfo());
        }
    }

    @Fold
    public static HeapImpl getHeapImpl() {
        Heap heap = Heap.getHeap();
        assert heap instanceof HeapImpl : "VMConfiguration heap is not a HeapImpl.";
        return (HeapImpl) heap;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static ImageHeapInfo[] getImageHeapInfos() {
        return MultiLayeredImageSingleton.getAllLayers(ImageHeapInfo.class);
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
    @AlwaysInline("GC performance")
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
    @AlwaysInline("GC performance")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInPrimaryImageHeap(Pointer objPointer) {
        for (ImageHeapInfo info : getImageHeapInfos()) {
            if (info.isInImageHeap(objPointer)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void suspendAllocation() {
        TlabSupport.suspendAllocationInCurrentThread();
    }

    @Override
    public void resumeAllocation() {
        // Nothing to do - the next allocation will refill the TLAB.
    }

    @Override
    public void walkObjects(ObjectVisitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("must only be executed at a safepoint");
        walkImageHeapObjects(visitor);
        walkCollectedHeapObjects(visitor);
    }

    /** Tear down the heap and release its memory. */
    @Override
    @Uninterruptible(reason = "Tear-down in progress.")
    public boolean tearDown() {
        youngGeneration.tearDown();
        oldGeneration.tearDown();
        getChunkProvider().tearDown();

        if (Metaspace.isSupported()) {
            MetaspaceImpl.singleton().tearDown();
        }
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
            throw NoAllocationVerifier.exit(callSite, typeName);
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

    void logUsage(Log log) {
        if (Metaspace.isSupported()) {
            MetaspaceImpl.singleton().logUsage(log);
        }
        youngGeneration.logUsage(log);
        oldGeneration.logUsage(log);
    }

    void logChunks(Log log, boolean allowUnsafe) {
        if (Metaspace.isSupported()) {
            MetaspaceImpl.singleton().logChunks(log);
        }
        getYoungGeneration().logChunks(log, allowUnsafe);
        getOldGeneration().logChunks(log);
        getChunkProvider().logFreeChunks(log);
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
        int count = 0;
        for (ImageHeapInfo info : HeapImpl.getImageHeapInfos()) {
            count += info.dynamicHubCount;
        }
        return count;
    }

    @Override
    protected List<Class<?>> getClassesInImageHeap() {
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
        for (ImageHeapInfo info : HeapImpl.getImageHeapInfos()) {
            collectDynamicHubs(info, list);
        }

        VMError.guarantee(dynamicHubCount == list.size(), "Found fewer DynamicHubs in the image heap than expected.");
        return list;
    }

    private static void collectDynamicHubs(ImageHeapInfo info, ArrayList<Class<?>> list) {
        try {
            ClassListBuilderVisitor visitor = new ClassListBuilderVisitor(list.size() + info.dynamicHubCount, list);
            ImageHeapWalker.walkRegions(info, visitor);
        } catch (FoundAllHubsException ignored) {
        }
    }

    /**
     * Iterates over the image heap parts that may contain dynamic hubs. After collecting all
     * dynamic hubs were collected, this visitor throws a {@link FoundAllHubsException}.
     */
    private static class ClassListBuilderVisitor implements MemoryWalker.ImageHeapRegionVisitor, ObjectVisitor {
        private static final FoundAllHubsException FOUND_ALL_HUBS_EXCEPTION = new FoundAllHubsException();

        private final int dynamicHubCount;
        private final List<Class<?>> list;

        ClassListBuilderVisitor(int dynamicHubCount, List<Class<?>> list) {
            this.dynamicHubCount = dynamicHubCount;
            this.list = list;
        }

        @Override
        public <T> void visitNativeImageHeapRegion(T region, MemoryWalker.NativeImageHeapRegionAccess<T> access) {
            if (!access.isWritable(region) && !access.consistsOfHugeObjects(region)) {
                access.visitObjects(region, this);
            }
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, reason = "Allocation is fine: this method traverses only the image heap.")
        public void visitObject(Object o) {
            if (o instanceof Class<?>) {
                list.add((Class<?>) o);
                if (list.size() == dynamicHubCount) {
                    throw FOUND_ALL_HUBS_EXCEPTION;
                }
            }
        }
    }

    private static final class FoundAllHubsException extends RuntimeException {
        @Serial private static final long serialVersionUID = 1L;

        @Override
        @SuppressWarnings("sync-override")
        public Throwable fillInStackTrace() {
            /* No stacktrace needed. */
            return this;
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
        TlabSupport.startupInitialization();
        TlabSupport.initialize(isolateThread);
    }

    @Override
    @Uninterruptible(reason = "Thread is detaching and holds the THREAD_MUTEX.")
    public void detachThread(IsolateThread isolateThread) {
        TlabSupport.disableAndFlushForThread(isolateThread);
    }

    @Fold
    public static boolean usesImageHeapCardMarking() {
        Boolean enabled = SerialGCOptions.ImageHeapCardMarking.getValue();
        if (enabled == Boolean.FALSE || enabled == null && !SerialGCOptions.useRememberedSet()) {
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
    public int getHeapBaseAlignment() {
        return getImageHeapAlignment();
    }

    @Fold
    @Override
    public int getImageHeapAlignment() {
        return UnsignedUtils.safeToInt(HeapParameters.getAlignedHeapChunkAlignment());
    }

    @Fold
    static int getMetaspaceOffsetInAddressSpace() {
        assert SubstrateOptions.SpawnIsolates.getValue();
        int result = SerialAndEpsilonGCOptions.getNullRegionSize();
        assert result % HeapParameters.getAlignedHeapChunkAlignment().rawValue() == 0 : "start of metaspace must be aligned";
        return result;
    }

    @Fold
    @Override
    public int getImageHeapOffsetInAddressSpace() {
        if (!SubstrateOptions.SpawnIsolates.getValue()) {
            return 0;
        }

        int result = SerialAndEpsilonGCOptions.getNullRegionSize() + SerialAndEpsilonGCOptions.getReservedMetaspaceSize();
        assert result % getImageHeapAlignment() == 0 : "start of image heap must be aligned";
        return result;
    }

    @Fold
    public static boolean isImageHeapAligned() {
        return SubstrateOptions.SpawnIsolates.getValue();
    }

    @Override
    public void walkImageHeapObjects(ObjectVisitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("Must only be called at a safepoint");
        if (visitor == null) {
            return;
        }

        for (ImageHeapInfo info : HeapImpl.getImageHeapInfos()) {
            ImageHeapWalker.walkImageHeapObjects(info, visitor);
        }
        if (AuxiliaryImageHeap.isPresent()) {
            AuxiliaryImageHeap.singleton().walkObjects(visitor);
        }
    }

    @Override
    public void walkCollectedHeapObjects(ObjectVisitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("Must only be called at a safepoint");
        makeParseable();
        getYoungGeneration().walkObjects(visitor);
        getOldGeneration().walkObjects(visitor);
    }

    @Override
    public void doReferenceHandling() {
        if (ReferenceHandler.isExecutedManually()) {
            GCImpl.doReferenceHandling();
        }
    }

    void makeParseable() {
        youngGeneration.makeParseable();
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
        Pointer heapBase = KnownIntrinsics.heapBase();
        if (value.equal(heapBase)) {
            log.string("is the heap base");
            return true;
        } else if (value.aboveThan(heapBase) && value.belowThan(getImageHeapStart())) {
            log.string("points into the protected memory between the heap base and the image heap");
            return true;
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
        UnsignedWord allocatedBytes = ThreadLocalAllocation.getAllocatedBytes(thread);

        /*
         * The current aligned chunk in the TLAB is only partially filled and therefore not yet
         * accounted for in ThreadLocalAllocation.allocatedBytes. The reads below are unsynchronized
         * and unordered with the thread updating its TLAB, so races may occur. We only use the read
         * values if they are plausible and not obviously racy. We also accept that certain races
         * can cause that the memory in the current aligned TLAB chunk is counted twice.
         */
        ThreadLocalAllocation.Descriptor tlab = ThreadLocalAllocation.getTlab(thread);
        Pointer start = tlab.getAlignedAllocationStart(SubstrateAllocationSnippets.TLAB_START_IDENTITY);
        Pointer top = tlab.getAllocationTop(SubstrateAllocationSnippets.TLAB_TOP_IDENTITY);

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
        if (obj == null) {
            return;
        }

        RememberedSet.get().dirtyAllReferencesIfNecessary(obj);
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
        for (ImageHeapInfo info : HeapImpl.getImageHeapInfos()) {
            if (info.isInReadOnlyRegularPartition(ptr)) {
                log.string("points into the image heap (read-only)");
                return true;
            } else if (info.isInReadOnlyRelocatablePartition(ptr)) {
                log.string("points into the image heap (read-only relocatables)");
                return true;
            } else if (info.isInWritablePatchedPartition(ptr)) {
                log.string("points into the image heap (writable patched)");
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
        } else if (TlabSupport.printTlabInfo(log, ptr, CurrentIsolate.getCurrentThread())) {
            return true;
        }

        if (allowJavaHeapAccess) {
            // Accessing spaces and chunks is safe if we prevent a GC.
            if (youngGeneration.printLocationInfo(log, ptr)) {
                return true;
            } else if (oldGeneration.printLocationInfo(log, ptr)) {
                return true;
            }
        }

        if (allowUnsafeOperations || VMOperation.isInProgressAtSafepoint()) {
            /* Accessing the metaspace is unsafe due to possible concurrent modifications. */
            if (Metaspace.isSupported() && MetaspaceImpl.singleton().printLocationInfo(log, ptr)) {
                return true;
            }

            /*
             * If we are not at a safepoint, then it is unsafe to access thread locals of another
             * thread as the IsolateThread could be freed at any time.
             */
            if (TlabSupport.printTlabInfo(log, ptr)) {
                return true;
            }
        }

        if (allowJavaHeapAccess) {
            // Accessing chunks is safe if we prevent a GC.
            if (YoungGeneration.getHeapAllocation().printLocationInfo(log, ptr)) {
                return true;
            }
        }

        return false;
    }

    boolean isInHeapSlow(Pointer ptr) {
        return isInImageHeap(ptr) || youngGeneration.isInSpace(ptr) || oldGeneration.isInSpace(ptr) || Metaspace.singleton().isInAllocatedMemory(ptr);
    }

    @Override
    @Uninterruptible(reason = "Called during early startup.")
    public boolean verifyImageHeapMapping() {
        for (ImageHeapInfo info : HeapImpl.getImageHeapInfos()) {
            /* Read & write some data at the beginning and end of each writable chunk. */
            writeToEachChunk(info.getFirstWritableAlignedChunk(), Word.nullPointer());
            writeToEachChunk(info.getFirstWritableUnalignedChunk(), info.getLastWritableUnalignedChunk());
        }
        return true;
    }

    @Uninterruptible(reason = "Called during early startup.")
    private static void writeToEachChunk(HeapChunk.Header<?> firstChunk, HeapChunk.Header<?> lastChunk) {
        HeapChunk.Header<?> curChunk = firstChunk;
        while (curChunk.isNonNull()) {
            Pointer begin = (Pointer) curChunk;
            Pointer end = HeapChunk.getTopPointer(curChunk).subtract(1);

            byte val = begin.readByte(0);
            begin.writeByte(0, val);

            val = end.readByte(0);
            end.writeByte(0, val);

            if (curChunk.equal(lastChunk)) {
                break;
            }
            curChunk = HeapChunk.getNext(curChunk);
        }
    }

    private static final class DumpHeapSettingsAndStatistics extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            log.string("Heap settings and statistics:").indent(true);
            log.string("Reserved hub pointer bits: 0b").number(Heap.getHeap().getObjectHeader().getReservedHubBitsMask(), 2, false).newline();

            log.string("Aligned chunk size: ").unsigned(HeapParameters.getAlignedHeapChunkSize()).newline();
            log.string("Large array threshold: ").unsigned(HeapParameters.getLargeArrayThreshold()).newline();

            GCAccounting accounting = GCImpl.getAccounting();
            log.string("Incremental collections: ").unsigned(accounting.getIncrementalCollectionCount()).newline();
            log.string("Complete collections: ").unsigned(accounting.getCompleteCollectionCount()).newline();

            logZapValues(log);

            log.indent(false);
        }
    }

    private static final class DumpHeapUsage extends DiagnosticThunk {
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

    private static final class DumpGCPolicy extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            CollectionPolicy policy = GCImpl.getPolicy();

            log.string("GC policy:").indent(true);
            log.string("Name: ").string(policy.getName()).newline();
            log.string("Max eden size: ").unsigned(policy.getMaximumEdenSize()).newline();
            log.string("Max survivor size: ").unsigned(policy.getMaximumSurvivorSize()).newline();
            log.string("Max young size: ").unsigned(policy.getMaximumYoungGenerationSize()).newline();
            log.string("Max old size: ").unsigned(policy.getMaximumOldSize()).newline();
            log.string("Max heap size: ").unsigned(policy.getMaximumHeapSize()).newline();
            log.indent(false);
        }
    }

    private static final class DumpImageHeapInfo extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 1;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            log.string("Image heap boundaries:").indent(true);
            for (ImageHeapInfo info : HeapImpl.getImageHeapInfos()) {
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
    }

    private static final class DumpChunkInfo extends DiagnosticThunk {
        @Override
        public int maxInvocationCount() {
            return 2;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate while printing diagnostics.")
        public void printDiagnostics(Log log, ErrorContext context, int maxDiagnosticLevel, int invocationCount) {
            log.string("Heap chunks: E=eden, S=survivor, O=old, F=free; A=aligned chunk, U=unaligned chunk; T=to space").indent(true);
            boolean allowUnsafe = invocationCount == 1 && SubstrateDiagnostics.DiagnosticLevel.unsafeOperationsAllowed(maxDiagnosticLevel);
            HeapImpl.getHeapImpl().logChunks(log, allowUnsafe);
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
