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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.management.MBeanNotificationInfo;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.nodes.gc.CardTableBarrierSet;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature.FeatureAccess;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.MemoryWalker.CodeAccess;
import com.oracle.svm.core.MemoryWalker.HeapChunkAccess;
import com.oracle.svm.core.MemoryWalker.NativeImageHeapRegionAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicReference;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.ThreadStatus;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
//Checkstyle: stop
import sun.management.Util;
//Checkstyle: resume

/** An implementation of a card remembered set generational heap. */
public class HeapImpl extends Heap {
    /** Synchronization means for notifying {@link #refPendingList} waiters without deadlocks. */
    private static final VMMutex REF_MUTEX = new VMMutex();
    private static final VMCondition REF_CONDITION = new VMCondition(REF_MUTEX);

    // Singleton instances, created during image generation.
    private final YoungGeneration youngGeneration;
    private final OldGeneration oldGeneration;
    final HeapChunkProvider chunkProvider;
    private final ObjectHeaderImpl objectHeaderImpl;
    private final GCImpl gcImpl;
    private final HeapPolicy heapPolicy;

    private final MemoryMXBean memoryMXBean;
    private final ImageHeapInfo imageHeapInfo;
    private HeapVerifierImpl heapVerifier;
    private final StackVerifier stackVerifier;

    /** The head of the list of currently pending (ready to be enqueued) {@link Reference}s. */
    private Reference<?> refPendingList;
    /** Total number of times when a new pending reference list became available. */
    private long refListOfferCounter;
    /** Total number of times when threads waiting for a pending reference list were interrupted. */
    private long refListWaiterWakeUpCounter;

    /** A list of all the classes, if someone asks for it. */
    private List<Class<?>> classList;

    @Platforms(Platform.HOSTED_ONLY.class)
    public HeapImpl(FeatureAccess access) {
        this.youngGeneration = new YoungGeneration("YoungGeneration");
        this.oldGeneration = new OldGeneration("OldGeneration");
        this.objectHeaderImpl = new ObjectHeaderImpl();
        this.gcImpl = new GCImpl(access);
        this.heapPolicy = new HeapPolicy(access);
        this.pinHead = new AtomicReference<>();
        /* Pre-allocate verifiers for use during collection. */
        if (getVerifyHeapBeforeGC() || getVerifyHeapAfterGC() || getVerifyStackBeforeGC() || getVerifyStackAfterGC() || getVerifyDirtyCardBeforeGC() || getVerifyDirtyCardAfterGC()) {
            this.heapVerifier = HeapVerifierImpl.factory();
            this.stackVerifier = new StackVerifier();
        } else {
            this.heapVerifier = null;
            this.stackVerifier = null;
        }
        chunkProvider = new HeapChunkProvider();
        this.memoryMXBean = new HeapImplMemoryMXBean();
        this.imageHeapInfo = new ImageHeapInfo();
        this.classList = null;
        SubstrateUtil.DiagnosticThunkRegister.getSingleton().register(log -> {
            logImageHeapPartitionBoundaries(log).newline();
            zapValuesToLog(log).newline();
            report(log, true).newline();
            log.newline();
        });
    }

    @Fold
    public static HeapImpl getHeapImpl() {
        final Heap heap = Heap.getHeap();
        assert heap instanceof HeapImpl : "VMConfiguration heap is not a HeapImpl.";
        return (HeapImpl) heap;
    }

    @Fold
    public static ImageHeapInfo getImageHeapInfo() {
        return getHeapImpl().imageHeapInfo;
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
    public boolean isInImageHeap(Pointer pointer) {
        return imageHeapInfo.isInImageHeap(pointer) || (AuxiliaryImageHeap.isPresent() && AuxiliaryImageHeap.singleton().containsObject(pointer));
    }

    public boolean isInImageHeapSlow(Object obj) {
        return isInImageHeapSlow(Word.objectToUntrackedPointer(obj));
    }

    /** Slow version that is used for verification only. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInImageHeapSlow(Pointer p) {
        return imageHeapInfo.isInImageHeapSlow(p) || (AuxiliaryImageHeap.isPresent() && AuxiliaryImageHeap.singleton().containsObjectSlow(p));
    }

    @Override
    public void suspendAllocation() {
        ThreadLocalAllocation.suspendThreadLocalAllocation();
    }

    @Override
    public void resumeAllocation() {
        ThreadLocalAllocation.resumeThreadLocalAllocation();
    }

    @Override
    public boolean walkObjects(ObjectVisitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("must only be executed at a safepoint");
        return walkImageHeapObjects(visitor) && walkCollectedHeapObjects(visitor);
    }

    /** Walk the regions of the heap with a MemoryWalker. */
    public boolean walkMemory(MemoryWalker.Visitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("must only be executed at a safepoint");
        return walkNativeImageHeapRegions(visitor) && getYoungGeneration().walkHeapChunks(visitor) && getOldGeneration().walkHeapChunks(visitor) && HeapChunkProvider.get().walkHeapChunks(visitor);
    }

    /** Tear down the heap, return all allocated virtual memory chunks to VirtualMemoryProvider. */
    @Override
    @Uninterruptible(reason = "Tear-down in progress.")
    public final boolean tearDown() {
        youngGeneration.tearDown();
        oldGeneration.tearDown();
        HeapChunkProvider.get().tearDown();
        return true;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public ObjectHeader getObjectHeader() {
        return objectHeaderImpl;
    }

    public ObjectHeaderImpl getObjectHeaderImpl() {
        return objectHeaderImpl;
    }

    @Override
    public GC getGC() {
        return getGCImpl();
    }

    public GCImpl getGCImpl() {
        return gcImpl;
    }

    /** Allocation is disallowed if ... */
    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isAllocationDisallowed() {
        /*
         * This method exists because Heap is the place clients should ask this question, and to
         * aggregate all the reasons allocation might be disallowed.
         */
        return NoAllocationVerifier.isActive() || gcImpl.isCollectionInProgress();
    }

    /** A guard to place before an allocation, giving the call site and the allocation type. */
    static void exitIfAllocationDisallowed(final String callSite, final String typeName) {
        if (HeapImpl.getHeapImpl().isAllocationDisallowed()) {
            NoAllocationVerifier.exit(callSite, typeName);
        }
    }

    /*
     * This method has to be final so it can be called (transitively) from the allocation snippets.
     */
    final Space getAllocationSpace() {
        return getYoungGeneration().getEden();
    }

    @AlwaysInline("GC performance")
    public Object promoteObject(Object original, UnsignedWord header) {
        final Log trace = Log.noopLog().string("[HeapImpl.promoteObject:").string("  original: ").object(original);

        Object result;
        if (HeapPolicy.getMaxSurvivorSpaces() > 0 && !getGCImpl().isCompleteCollection()) {
            result = getYoungGeneration().promoteObject(original, header);
        } else {
            result = getOldGeneration().promoteObject(original, header);
        }

        trace.string("  result: ").object(result).string("]").newline();
        return result;
    }

    @AlwaysInline("GC performance")
    void dirtyCardIfNecessary(Object holderObject, Object object) {
        if (HeapPolicy.getMaxSurvivorSpaces() == 0 || holderObject == null || GCImpl.getGCImpl().isCompleteCollection() || !youngGeneration.contains(object)) {
            return;
        }

        final UnsignedWord objectHeader = ObjectHeaderImpl.readHeaderFromObject(holderObject);
        if (ObjectHeaderImpl.hasRememberedSet(objectHeader)) {
            if (ObjectHeaderImpl.isAlignedObject(holderObject)) {
                AlignedHeapChunk.dirtyCardForObjectOfAlignedHeapChunk(holderObject, false);
            } else {
                assert ObjectHeaderImpl.isUnalignedObject(holderObject) : "sanity";
                UnalignedHeapChunk.dirtyCardForObjectOfUnalignedHeapChunk(holderObject, false);
            }
        }
    }

    public HeapPolicy getHeapPolicy() {
        return HeapImpl.getHeapImpl().heapPolicy;
    }

    public YoungGeneration getYoungGeneration() {
        return youngGeneration;
    }

    public OldGeneration getOldGeneration() {
        return oldGeneration;
    }

    /** The head of the linked list of object pins. */
    private final AtomicReference<PinnedObjectImpl> pinHead;

    public AtomicReference<PinnedObjectImpl> getPinHead() {
        return pinHead;
    }

    /**
     * Returns the size (in bytes) of the heap currently used for aligned and unaligned chunks. It
     * excludes chunks that are unused.
     */
    UnsignedWord getUsedChunkBytes() {
        final UnsignedWord youngBytes = getYoungUsedChunkBytes();
        final UnsignedWord oldBytes = getOldUsedChunkBytes();
        return youngBytes.add(oldBytes);
    }

    UnsignedWord getYoungUsedChunkBytes() {
        return getYoungGeneration().getChunkUsedBytes();
    }

    UnsignedWord getOldUsedChunkBytes() {
        final Log trace = Log.noopLog().string("[HeapImpl.getOldUsedChunkBytes:");
        final Space.Accounting from = getOldGeneration().getFromSpace().getAccounting();
        final UnsignedWord fromBytes = from.getAlignedChunkBytes().add(from.getUnalignedChunkBytes());
        final Space.Accounting to = getOldGeneration().getToSpace().getAccounting();
        final UnsignedWord toBytes = to.getAlignedChunkBytes().add(to.getUnalignedChunkBytes());
        final UnsignedWord result = fromBytes.add(toBytes);
        // @formatter:off
        if (trace.isEnabled()) {
            trace
                            .string("  fromAligned: ").unsigned(from.getAlignedChunkBytes())
                            .string("  fromUnaligned: ").signed(from.getUnalignedChunkBytes())
                            .string("  toAligned: ").unsigned(to.getAlignedChunkBytes())
                            .string("  toUnaligned: ").signed(to.getUnalignedChunkBytes())
                            .string("  returns: ").unsigned(result).string(" ]").newline();
        }
        // @formatter:on
        return result;
    }

    protected void report(Log log) {
        report(log, HeapPolicyOptions.TraceHeapChunks.getValue());
    }

    public Log report(Log log, boolean traceHeapChunks) {
        final HeapImpl heap = HeapImpl.getHeapImpl();
        log.newline().string("[Heap:").indent(true);
        heap.getYoungGeneration().report(log, traceHeapChunks).newline();
        heap.getOldGeneration().report(log, traceHeapChunks).newline();
        HeapChunkProvider.get().report(log, traceHeapChunks);
        log.redent(false).string("]");
        return log;
    }

    Log logImageHeapPartitionBoundaries(Log log) {
        log.string("[Native image heap boundaries: ").indent(true);
        ImageHeapWalker.logPartitionBoundaries(log, imageHeapInfo);
        log.redent(false).string("]");
        return log;
    }

    /** Log the zap values to make it easier to search for them. */
    Log zapValuesToLog(Log log) {
        if (HeapPolicy.getZapProducedHeapChunks() || HeapPolicy.getZapConsumedHeapChunks()) {
            log.string("[Heap Chunk zap values: ").indent(true);
            /* Padded with spaces so the columns line up between the int and word variants. */
            // @formatter:off
            if (HeapPolicy.getZapProducedHeapChunks()) {
                log.string("  producedHeapChunkZapInt: ")
                                .string("  hex: ").spaces(8).hex(HeapPolicy.getProducedHeapChunkZapInt())
                                .string("  signed: ").spaces(9).signed(HeapPolicy.getProducedHeapChunkZapInt())
                                .string("  unsigned: ").spaces(10).unsigned(HeapPolicy.getProducedHeapChunkZapInt()).newline();
                log.string("  producedHeapChunkZapWord:")
                                .string("  hex: ").hex(HeapPolicy.getProducedHeapChunkZapWord())
                                .string("  signed: ").signed(HeapPolicy.getProducedHeapChunkZapWord())
                                .string("  unsigned: ").unsigned(HeapPolicy.getProducedHeapChunkZapWord());
                if (HeapPolicy.getZapConsumedHeapChunks()) {
                    log.newline();
                }
            }
            if (HeapPolicy.getZapConsumedHeapChunks()) {
                log.string("  consumedHeapChunkZapInt: ")
                                .string("  hex: ").spaces(8).hex(HeapPolicy.getConsumedHeapChunkZapInt())
                                .string("  signed: ").spaces(10).signed(HeapPolicy.getConsumedHeapChunkZapInt())
                                .string("  unsigned: ").spaces(10).unsigned(HeapPolicy.getConsumedHeapChunkZapInt()).newline();
                log.string("  consumedHeapChunkZapWord:")
                                .string("  hex: ").hex(HeapPolicy.getConsumedHeapChunkZapWord())
                                .string("  signed: ").signed(HeapPolicy.getConsumedHeapChunkZapWord())
                                .string("  unsigned: ").unsigned(HeapPolicy.getConsumedHeapChunkZapWord());
            }
            log.redent(false).string("]");
            // @formatter:on
        }
        return log;
    }

    /** An accessor for the MemoryMXBean. */
    @Override
    public MemoryMXBean getMemoryMXBean() {
        return memoryMXBean;
    }

    /** Return a list of all the classes in the heap. */
    @Override
    public List<Class<?>> getClassList() {
        if (classList == null) {
            /* Two threads might race to set classList, but they compute the same result. */
            final List<Class<?>> list = new ArrayList<>(1024);
            final Object firstObject = imageHeapInfo.firstReadOnlyReferenceObject;
            final Object lastObject = imageHeapInfo.lastReadOnlyReferenceObject;
            final Pointer firstPointer = Word.objectToUntrackedPointer(firstObject);
            final Pointer lastPointer = Word.objectToUntrackedPointer(lastObject);
            Pointer currentPointer = firstPointer;
            while (currentPointer.belowOrEqual(lastPointer)) {
                final Object currentObject = KnownIntrinsics.convertUnknownValue(currentPointer.toObject(), Object.class);
                if (currentObject instanceof Class<?>) {
                    final Class<?> asClass = (Class<?>) currentObject;
                    list.add(asClass);
                }
                currentPointer = LayoutEncoding.getObjectEnd(currentObject);
            }
            classList = Collections.unmodifiableList(list);
        }
        return classList;
    }

    /*
     * Verification.
     */

    HeapVerifier getHeapVerifier() {
        return getHeapVerifierImpl();
    }

    public HeapVerifierImpl getHeapVerifierImpl() {
        return heapVerifier;
    }

    void setHeapVerifierImpl(HeapVerifierImpl value) {
        this.heapVerifier = value;
    }

    @Fold
    static boolean getVerifyHeapBeforeGC() {
        return (SubstrateOptions.VerifyHeap.getValue() || HeapOptions.VerifyHeapBeforeCollection.getValue());
    }

    @Fold
    static boolean getVerifyHeapAfterGC() {
        return (SubstrateOptions.VerifyHeap.getValue() || HeapOptions.VerifyHeapAfterCollection.getValue());
    }

    @Fold
    static boolean getVerifyStackBeforeGC() {
        return (SubstrateOptions.VerifyHeap.getValue() || HeapOptions.VerifyStackBeforeCollection.getValue());
    }

    @Fold
    static boolean getVerifyStackAfterGC() {
        return (SubstrateOptions.VerifyHeap.getValue() || HeapOptions.VerifyStackAfterCollection.getValue());
    }

    @Fold
    static boolean getVerifyDirtyCardBeforeGC() {
        return (SubstrateOptions.VerifyHeap.getValue() || HeapOptions.VerifyDirtyCardsBeforeCollection.getValue());
    }

    @Fold
    static boolean getVerifyDirtyCardAfterGC() {
        return (SubstrateOptions.VerifyHeap.getValue() || HeapOptions.VerifyDirtyCardsAfterCollection.getValue());
    }

    @NeverInline("Starting a stack walk in the caller frame")
    void verifyBeforeGC(String cause, UnsignedWord epoch) {
        final Log trace = Log.noopLog().string("[HeapImpl.verifyBeforeGC:");
        trace.string("  getVerifyHeapBeforeGC(): ").bool(getVerifyHeapBeforeGC()).string("  heapVerifier: ").object(heapVerifier);
        trace.string("  getVerifyStackBeforeGC(): ").bool(getVerifyStackBeforeGC()).string("  stackVerifier: ").object(stackVerifier);
        if (getVerifyHeapBeforeGC()) {
            assert heapVerifier != null : "No heap verifier!";
            if (!heapVerifier.verifyOperation("before collection", HeapVerifier.Occasion.BEFORE_COLLECTION)) {
                Log.log().string("[HeapImpl.verifyBeforeGC:").string("  cause: ").string(cause).string("  heap fails to verify before epoch: ").unsigned(epoch).string("]").newline();
                assert false;
            }
        }
        if (getVerifyStackBeforeGC()) {
            assert stackVerifier != null : "No stack verifier!";
            if (!stackVerifier.verifyInAllThreads(KnownIntrinsics.readCallerStackPointer(), "before collection")) {
                Log.log().string("[HeapImpl.verifyBeforeGC:").string("  cause: ").string(cause).string("  stack fails to verify epoch: ").unsigned(epoch).string("]").newline();
                assert false;
            }
        }
        if (getVerifyDirtyCardBeforeGC()) {
            assert heapVerifier != null : "No heap verifier!";
            Log.log().string("[Verify dirtyCard before GC: ");
            heapVerifier.verifyDirtyCard(false);
        }
        trace.string("]").newline();
    }

    @NeverInline("Starting a stack walk in the caller frame")
    void verifyAfterGC(String cause, UnsignedWord epoch) {
        if (getVerifyHeapAfterGC()) {
            assert heapVerifier != null : "No heap verifier!";
            if (!heapVerifier.verifyOperation("after collection", HeapVerifier.Occasion.AFTER_COLLECTION)) {
                Log.log().string("[HeapImpl.verifyAfterGC:").string("  cause: ").string(cause).string("  heap fails to verify after epoch: ").unsigned(epoch).string("]").newline();
                assert false;
            }
        }
        if (getVerifyStackAfterGC()) {
            assert stackVerifier != null : "No stack verifier!";
            if (!stackVerifier.verifyInAllThreads(KnownIntrinsics.readCallerStackPointer(), "after collection")) {
                Log.log().string("[HeapImpl.verifyAfterGC:").string("  cause: ").string(cause).string("  stack fails to verify after epoch: ").unsigned(epoch).string("]").newline();
                assert false;
            }
        }
        if (getVerifyDirtyCardAfterGC()) {
            assert heapVerifier != null : "No heap verifier!";
            Log.log().string("[Verify dirtyCard after GC: ");
            heapVerifier.verifyDirtyCard(true);
        }
    }

    /*
     * Methods for java.lang.Runtime.*Memory(), quoting from that JavaDoc.
     */

    /**
     * @return an approximation to the total amount of memory currently available for future
     *         allocated objects, measured in bytes.
     */
    public UnsignedWord freeMemory() {
        /*
         * Report "chunk bytes" rather than the slower but more accurate "object bytes".
         */
        return maxMemory().subtract(HeapPolicy.getYoungUsedBytes()).subtract(getOldUsedChunkBytes());
    }

    /**
     * @return the total amount of memory currently available for current and future objects,
     *         measured in bytes.
     */
    public UnsignedWord totalMemory() {
        return maxMemory();
    }

    /**
     * @return the maximum amount of memory that the virtual machine will attempt to use, measured
     *         in bytes
     */
    public UnsignedWord maxMemory() {
        /* Get physical memory size, so it gets set correctly instead of being estimated. */
        PhysicalMemory.size();
        /*
         * This only reports the memory that will be used for heap-allocated objects. For example,
         * it does not include memory in the chunk free list, or memory in the image heap.
         */
        return HeapPolicy.getMaximumHeapSize();
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
    public void detachThread(IsolateThread isolateThread) {
        ThreadLocalAllocation.disableThreadLocalAllocation(isolateThread);
    }

    @Fold
    @Override
    public int getImageHeapOffsetInAddressSpace() {
        return 0;
    }

    @Override
    public boolean walkImageHeapObjects(ObjectVisitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("Must only be called at a safepoint");
        if (visitor != null) {
            return ImageHeapWalker.walkImageHeapObjects(imageHeapInfo, visitor) &&
                            (!AuxiliaryImageHeap.isPresent() || AuxiliaryImageHeap.singleton().walkObjects(visitor));
        }
        return true;
    }

    @Override
    public boolean walkCollectedHeapObjects(ObjectVisitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("Must only be called at a safepoint");
        return getYoungGeneration().walkObjects(visitor) && getOldGeneration().walkObjects(visitor);
    }

    boolean walkNativeImageHeapRegions(MemoryWalker.Visitor visitor) {
        return ImageHeapWalker.walkRegions(imageHeapInfo, visitor) &&
                        (!AuxiliaryImageHeap.isPresent() || AuxiliaryImageHeap.singleton().walkRegions(visitor));
    }

    @Override
    public CardTableBarrierSet createBarrierSet(MetaAccessProvider metaAccess) {
        ResolvedJavaType objectArrayType = metaAccess.lookupJavaType(Object[].class);
        return new CardTableBarrierSet(objectArrayType);
    }

    void addToReferencePendingList(Reference<?> list) {
        VMOperation.guaranteeGCInProgress("Must only be called during a GC.");
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
            return (refPendingList != null);
        } finally {
            REF_MUTEX.unlock();
        }
    }

    @Override
    @Uninterruptible(reason = "Safepoint while holding the lock could lead to a deadlock in GC.")
    public void waitForReferencePendingList() throws InterruptedException {
        long initialOffers;
        long initialWakeUps;
        REF_MUTEX.lockNoTransition();
        try {
            if (refPendingList != null) {
                return;
            }
            /*
             * Remember current counter values to detect changes when waiting in native. We need to
             * do this right after the above check while holding the lock to prevent lost updates.
             */
            initialOffers = refListOfferCounter;
            initialWakeUps = refListWaiterWakeUpCounter;
        } finally {
            REF_MUTEX.unlock();
        }
        transitionToParkedInNativeThenAwaitPendingRefs(initialOffers, initialWakeUps);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", calleeMustBe = false)
    private static void transitionToParkedInNativeThenAwaitPendingRefs(long initialOffers, long initialWakeUps) throws InterruptedException {
        doTransitionToParkedInNativeThenAwaitPendingRefs(initialOffers, initialWakeUps);
    }

    private static void doTransitionToParkedInNativeThenAwaitPendingRefs(long initialOffers, long initialWakeUps) throws InterruptedException {
        Thread currentThread = Thread.currentThread();
        int oldThreadStatus = JavaThreads.getThreadStatus(currentThread);
        JavaThreads.setThreadStatus(currentThread, ThreadStatus.PARKED);
        try {
            boolean offered;
            do {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                offered = transitionToNativeThenAwaitPendingRefs(initialOffers, initialWakeUps);
            } while (!offered);
        } finally {
            JavaThreads.setThreadStatus(currentThread, oldThreadStatus);
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
            while (getHeapImpl().refListOfferCounter == initialOffers) {
                REF_CONDITION.blockNoTransition();
                if (getHeapImpl().refListWaiterWakeUpCounter != initialWakeUps) {
                    return false;
                }
            }
            return true;
        } finally {
            REF_MUTEX.unlock();
        }
    }

    @Override
    @Uninterruptible(reason = "Safepoint while holding the lock could lead to a deadlock in GC.")
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
            refPendingList = null;
            return list;
        } finally {
            REF_MUTEX.unlock();
        }
    }
}

/**
 * A MemoryMXBean for this heap.
 *
 * Note: This implementation is somewhat inefficient, in that each time it is asked for the
 * <em>current</em> heap memory usage or non-heap memory usage, it uses the MemoryWalker.Visitor to
 * walk all of memory. If someone asks for only the heap memory usage <em>or</em> the non-heap
 * memory usage, the other kind of memory will still be walked. If someone asks for both the heap
 * memory usage <em>and</em> the non-heap memory usage, all the memory will be walked twice.
 */
final class HeapImplMemoryMXBean implements MemoryMXBean, NotificationEmitter {

    /** Constant for the {@link MemoryUsage} constructor. */
    static final long UNDEFINED_MEMORY_USAGE = -1L;

    /** Instance fields. */
    private final MemoryMXBeanMemoryVisitor visitor;

    @Platforms(Platform.HOSTED_ONLY.class)
    HeapImplMemoryMXBean() {
        this.visitor = new MemoryMXBeanMemoryVisitor();
    }

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(ManagementFactory.MEMORY_MXBEAN_NAME);
    }

    @Override
    public int getObjectPendingFinalizationCount() {
        /* No finalization! */
        return 0;
    }

    @Override
    public MemoryUsage getHeapMemoryUsage() {
        visitor.reset();
        MemoryWalker.getMemoryWalker().visitMemory(visitor);
        final long used = visitor.getHeapUsed().rawValue();
        final long committed = visitor.getHeapCommitted().rawValue();
        return new MemoryUsage(UNDEFINED_MEMORY_USAGE, used, committed, UNDEFINED_MEMORY_USAGE);
    }

    @Override
    public MemoryUsage getNonHeapMemoryUsage() {
        visitor.reset();
        MemoryWalker.getMemoryWalker().visitMemory(visitor);
        final long used = visitor.getNonHeapUsed().rawValue();
        final long committed = visitor.getNonHeapCommitted().rawValue();
        return new MemoryUsage(UNDEFINED_MEMORY_USAGE, used, committed, UNDEFINED_MEMORY_USAGE);
    }

    @Override
    public boolean isVerbose() {
        return SubstrateOptions.PrintGC.getValue();
    }

    @Override
    public void setVerbose(boolean value) {
        RuntimeOptionValues.singleton().update(SubstrateOptions.PrintGC, value);
    }

    @Override
    public void gc() {
        System.gc();
    }

    @Override
    public void removeNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
    }

    @Override
    public void addNotificationListener(NotificationListener listener, NotificationFilter filter, Object handback) {
    }

    @Override
    public void removeNotificationListener(NotificationListener listener) {
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
        return new MBeanNotificationInfo[0];
    }
}

/** A MemoryWalker.Visitor that records used and committed memory sizes. */
final class MemoryMXBeanMemoryVisitor implements MemoryWalker.Visitor {

    /*
     * The gathered sizes.
     */
    private UnsignedWord heapUsed;
    private UnsignedWord heapCommitted;
    private UnsignedWord nonHeapUsed;
    private UnsignedWord nonHeapCommitted;

    /** Constructor. */
    MemoryMXBeanMemoryVisitor() {
        reset();
    }

    /*
     * Access method for the sizes.
     */

    public UnsignedWord getHeapUsed() {
        return heapUsed;
    }

    public UnsignedWord getHeapCommitted() {
        return heapCommitted;
    }

    public UnsignedWord getNonHeapUsed() {
        return nonHeapUsed;
    }

    public UnsignedWord getNonHeapCommitted() {
        return nonHeapCommitted;
    }

    public void reset() {
        heapUsed = WordFactory.zero();
        heapCommitted = WordFactory.zero();
        nonHeapUsed = WordFactory.zero();
        nonHeapCommitted = WordFactory.zero();
    }

    /*
     * Implementations of methods declared by MemoryWalker.Visitor.
     */

    @Override
    public <T> boolean visitNativeImageHeapRegion(T region, NativeImageHeapRegionAccess<T> access) {
        final UnsignedWord size = access.getSize(region);
        heapUsed = heapUsed.add(size);
        heapCommitted = heapCommitted.add(size);
        return true;
    }

    @Override
    public <T extends PointerBase> boolean visitHeapChunk(T heapChunk, HeapChunkAccess<T> access) {
        final UnsignedWord used = access.getAllocationEnd(heapChunk).subtract(access.getAllocationStart(heapChunk));
        final UnsignedWord committed = access.getSize(heapChunk);
        heapUsed = heapUsed.add(used);
        heapCommitted = heapCommitted.add(committed);
        return true;
    }

    @Override
    public <T extends CodeInfo> boolean visitCode(T codeInfo, CodeAccess<T> access) {
        final UnsignedWord size = access.getSize(codeInfo).add(access.getMetadataSize(codeInfo));
        nonHeapUsed = nonHeapUsed.add(size);
        nonHeapCommitted = nonHeapCommitted.add(size);
        return true;
    }
}

@TargetClass(value = java.lang.Runtime.class, onlyWith = UseCardRememberedSetHeap.class)
@SuppressWarnings({"static-method"})
final class Target_java_lang_Runtime {

    /** What would calling this mean on a virtual machine without a fixed-sized heap? */
    @Substitute
    private long freeMemory() {
        return HeapImpl.getHeapImpl().freeMemory().rawValue();
    }

    /** What would calling this mean on a virtual machine without a fixed-sized heap? */
    @Substitute
    private long totalMemory() {
        return HeapImpl.getHeapImpl().totalMemory().rawValue();
    }

    /**
     * The JavaDoc for {@link Runtime#maxMemory()} says 'If there is no inherent limit then the
     * value {@link Long#MAX_VALUE} will be returned.'.
     */
    @Substitute
    private long maxMemory() {
        return HeapImpl.getHeapImpl().maxMemory().rawValue();
    }

    /**
     * The JavaDoc for {@link Runtime#gc()} says 'When control returns from the method call, the
     * virtual machine has made its best effort to recycle all discarded objects.'.
     */
    @Substitute
    private void gc() {
        HeapImpl.getHeapImpl().getHeapPolicy().getUserRequestedGCPolicy().maybeCauseCollection(GCCause.JavaLangSystemGC);
    }
}
