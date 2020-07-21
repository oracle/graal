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

//Checkstyle: stop

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.nodes.gc.CardTableBarrierSet;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature.FeatureAccess;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.AlwaysInline;
import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.CodeInfo;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.GCCause;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.heap.ReferenceInternals;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicReference;
import com.oracle.svm.core.locks.VMCondition;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.nodes.CFunctionEpilogueNode;
import com.oracle.svm.core.nodes.CFunctionPrologueNode;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.ThreadStatus;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.thread.VMThreads;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.UserError;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;
//Checkstyle: resume

/** An implementation of a card remembered set generational heap. */
public final class HeapImpl extends Heap {
    /** Synchronization means for notifying {@link #refPendingList} waiters without deadlocks. */
    private static final VMMutex REF_MUTEX = new VMMutex();
    private static final VMCondition REF_CONDITION = new VMCondition(REF_MUTEX);

    // Singleton instances, created during image generation.
    private final YoungGeneration youngGeneration = new YoungGeneration("YoungGeneration");
    private final OldGeneration oldGeneration = new OldGeneration("OldGeneration");
    private final HeapChunkProvider chunkProvider = new HeapChunkProvider();
    private final ObjectHeaderImpl objectHeaderImpl = new ObjectHeaderImpl();
    private final GCImpl gcImpl;
    private final HeapPolicy heapPolicy;
    private final ImageHeapInfo imageHeapInfo = new ImageHeapInfo();
    private HeapVerifier heapVerifier;
    private final StackVerifier stackVerifier;

    /** Head of the linked list of currently pending (ready to be enqueued) {@link Reference}s. */
    private Reference<?> refPendingList;
    /** Total number of times when a new pending reference list became available. */
    private long refListOfferCounter;
    /** Total number of times when threads waiting for a pending reference list were interrupted. */
    private long refListWaiterWakeUpCounter;

    /** Head of the linked list of object pins. */
    private final AtomicReference<PinnedObjectImpl> pinHead = new AtomicReference<>();

    /** A cached list of all the classes, if someone asks for it. */
    private List<Class<?>> classList;

    @Platforms(Platform.HOSTED_ONLY.class)
    public HeapImpl(FeatureAccess access) {
        this.gcImpl = new GCImpl(access);
        this.heapPolicy = new HeapPolicy(access);
        if (getVerifyHeapBeforeGC() || getVerifyHeapAfterGC() || getVerifyStackBeforeGC() || getVerifyStackAfterGC() || getVerifyDirtyCardBeforeGC() || getVerifyDirtyCardAfterGC()) {
            this.heapVerifier = new HeapVerifier();
            this.stackVerifier = new StackVerifier();
        } else {
            this.heapVerifier = null;
            this.stackVerifier = null;
        }
        SubstrateUtil.DiagnosticThunkRegister.getSingleton().register(log -> {
            logImageHeapPartitionBoundaries(log).newline();
            zapValuesToLog(log).newline();
            report(log, true).newline();
            log.newline();
        });
    }

    @Fold
    public static HeapImpl getHeapImpl() {
        Heap heap = Heap.getHeap();
        assert heap instanceof HeapImpl : "VMConfiguration heap is not a HeapImpl.";
        return (HeapImpl) heap;
    }

    @Fold
    public static ImageHeapInfo getImageHeapInfo() {
        return getHeapImpl().imageHeapInfo;
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
    public boolean isInImageHeap(Pointer pointer) {
        return imageHeapInfo.isInImageHeap(pointer) || (AuxiliaryImageHeap.isPresent() && AuxiliaryImageHeap.singleton().containsObject(pointer));
    }

    boolean isInImageHeapSlow(Object obj) {
        return isInImageHeapSlow(Word.objectToUntrackedPointer(obj));
    }

    /** Slow, verification-only version of {@link #isInImageHeap}. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    boolean isInImageHeapSlow(Pointer p) {
        return imageHeapInfo.isInImageHeapSlow(p) || (AuxiliaryImageHeap.isPresent() && AuxiliaryImageHeap.singleton().containsObjectSlow(p));
    }

    @Override
    public void suspendAllocation() {
        ThreadLocalAllocation.suspendInCurrentThread();
    }

    @Override
    public void resumeAllocation() {
        ThreadLocalAllocation.resumeInCurrentThread();
    }

    @Override
    public boolean walkObjects(ObjectVisitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("must only be executed at a safepoint");
        return walkImageHeapObjects(visitor) && walkCollectedHeapObjects(visitor);
    }

    /** Walk the regions of the heap. */
    boolean walkMemory(MemoryWalker.Visitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("must only be executed at a safepoint");
        return walkNativeImageHeapRegions(visitor) && getYoungGeneration().walkHeapChunks(visitor) && getOldGeneration().walkHeapChunks(visitor) && getChunkProvider().walkHeapChunks(visitor);
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

    ObjectHeaderImpl getObjectHeaderImpl() {
        return objectHeaderImpl;
    }

    @Override
    public GC getGC() {
        return getGCImpl();
    }

    GCImpl getGCImpl() {
        return gcImpl;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isAllocationDisallowed() {
        return NoAllocationVerifier.isActive() || gcImpl.isCollectionInProgress();
    }

    /** A guard to place before an allocation, giving the call site and the allocation type. */
    static void exitIfAllocationDisallowed(String callSite, String typeName) {
        if (HeapImpl.getHeapImpl().isAllocationDisallowed()) {
            NoAllocationVerifier.exit(callSite, typeName);
        }
    }

    Space getAllocationSpace() {
        return getYoungGeneration().getEden();
    }

    @AlwaysInline("GC performance")
    Object promoteObject(Object original, UnsignedWord header) {
        Log trace = Log.noopLog().string("[HeapImpl.promoteObject:").string("  original: ").object(original);

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

        UnsignedWord objectHeader = ObjectHeaderImpl.readHeaderFromObject(holderObject);
        if (ObjectHeaderImpl.hasRememberedSet(objectHeader)) {
            if (ObjectHeaderImpl.isAlignedObject(holderObject)) {
                AlignedHeapChunk.dirtyCardForObject(holderObject, false);
            } else {
                assert ObjectHeaderImpl.isUnalignedObject(holderObject) : "sanity";
                UnalignedHeapChunk.dirtyCardForObject(holderObject, false);
            }
        }
    }

    HeapPolicy getHeapPolicy() {
        return heapPolicy;
    }

    YoungGeneration getYoungGeneration() {
        return youngGeneration;
    }

    OldGeneration getOldGeneration() {
        return oldGeneration;
    }

    AtomicReference<PinnedObjectImpl> getPinHead() {
        return pinHead;
    }

    /**
     * Returns the size (in bytes) of the heap currently used for aligned and unaligned chunks. It
     * excludes chunks that are unused.
     */
    UnsignedWord getUsedChunkBytes() {
        UnsignedWord youngBytes = getYoungUsedChunkBytes();
        UnsignedWord oldBytes = getOldUsedChunkBytes();
        return youngBytes.add(oldBytes);
    }

    UnsignedWord getYoungUsedChunkBytes() {
        return getYoungGeneration().getChunkUsedBytes();
    }

    UnsignedWord getOldUsedChunkBytes() {
        Log trace = Log.noopLog().string("[HeapImpl.getOldUsedChunkBytes:");
        SpaceAccounting from = getOldGeneration().getFromSpace().getAccounting();
        UnsignedWord fromBytes = from.getAlignedChunkBytes().add(from.getUnalignedChunkBytes());
        SpaceAccounting to = getOldGeneration().getToSpace().getAccounting();
        UnsignedWord toBytes = to.getAlignedChunkBytes().add(to.getUnalignedChunkBytes());
        UnsignedWord result = fromBytes.add(toBytes);
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

    void report(Log log) {
        report(log, HeapPolicyOptions.TraceHeapChunks.getValue());
    }

    Log report(Log log, boolean traceHeapChunks) {
        log.newline().string("[Heap:").indent(true);
        getYoungGeneration().report(log, traceHeapChunks).newline();
        getOldGeneration().report(log, traceHeapChunks).newline();
        getChunkProvider().report(log, traceHeapChunks);
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
    static Log zapValuesToLog(Log log) {
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

    /** Return a list of all the classes in the heap. */
    @Override
    public List<Class<?>> getClassList() {
        /* Two threads might race to set classList, but they compute the same result. */
        if (classList == null) {
            List<Class<?>> list = new ArrayList<>(1024);
            ImageHeapWalker.walkRegions(imageHeapInfo, new ClassListBuilderVisitor(list));
            classList = Collections.unmodifiableList(list);
        }
        return classList;
    }

    private static class ClassListBuilderVisitor implements MemoryWalker.ImageHeapRegionVisitor, ObjectVisitor {
        private final List<Class<?>> list;

        ClassListBuilderVisitor(List<Class<?>> list) {
            this.list = list;
        }

        @Override
        public <T> boolean visitNativeImageHeapRegion(T region, MemoryWalker.NativeImageHeapRegionAccess<T> access) {
            if (!access.isWritable(region) && access.containsReferences(region)) {
                access.visitObjects(region, this);
            }
            return true;
        }

        @Override
        @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, overridesCallers = true, //
                        reason = "Allocation is fine: this method traverses only the image heap.")
        public boolean visitObject(Object o) {
            if (o instanceof Class<?>) {
                list.add(KnownIntrinsics.convertUnknownValue(o, Class.class));
            }
            return true;
        }
    }

    /*
     * Verification.
     */

    HeapVerifier getHeapVerifier() {
        return heapVerifier;
    }

    void setHeapVerifier(HeapVerifier value) {
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
        Log trace = Log.noopLog().string("[HeapImpl.verifyBeforeGC:");
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
            HeapVerifier.verifyDirtyCard(false);
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
            HeapVerifier.verifyDirtyCard(true);
        }
    }

    /*
     * Methods for java.lang.Runtime.*Memory(), quoting from that JavaDoc.
     */

    /**
     * @return an approximation to the total amount of memory currently available for future
     *         allocated objects, measured in bytes.
     */
    UnsignedWord freeMemory() {
        // Report "chunk bytes" rather than the slower but more accurate "object bytes".
        return maxMemory().subtract(HeapPolicy.getYoungUsedBytes()).subtract(getOldUsedChunkBytes());
    }

    /**
     * @return the total amount of memory currently available for current and future objects,
     *         measured in bytes.
     */
    UnsignedWord totalMemory() {
        return maxMemory();
    }

    /**
     * @return the maximum amount of memory that the virtual machine will attempt to use, measured
     *         in bytes
     */
    @SuppressWarnings("static-method")
    UnsignedWord maxMemory() {
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
        ThreadLocalAllocation.disableAndFlushForThread(isolateThread);
    }

    @Fold
    public static boolean usesImageHeapChunks() {
        // Chunks are needed for card marking and not very useful without it
        return usesImageHeapCardMarking();
    }

    @Fold
    public static boolean usesImageHeapCardMarking() {
        Boolean enabled = HeapOptions.ImageHeapCardMarking.getValue();
        if (enabled == Boolean.FALSE) {
            return false;
        } else if (enabled == null) {
            return CommittedMemoryProvider.get().guaranteesHeapPreferredAddressSpaceAlignment() &&
                            HeapPolicyOptions.MaxSurvivorSpaces.getValue() == 0;
        }
        UserError.guarantee(CommittedMemoryProvider.get().guaranteesHeapPreferredAddressSpaceAlignment(),
                        "Enabling option %s requires a custom image heap alignment at runtime, which cannot be ensured with the current configuration (option %s might be disabled)",
                        HeapOptions.ImageHeapCardMarking, SubstrateOptions.SpawnIsolates);
        UserError.guarantee(HeapPolicyOptions.MaxSurvivorSpaces.getValue() == 0,
                        "Enabling option %s is currently not supported together with non-zero %s", HeapOptions.ImageHeapCardMarking, HeapPolicyOptions.MaxSurvivorSpaces);
        return true;
    }

    @Fold
    @Override
    public int getPreferredAddressSpaceAlignment() {
        if (usesImageHeapChunks()) {
            return UnsignedUtils.safeToInt(HeapPolicy.getAlignedHeapChunkAlignment());
        }
        return ConfigurationValues.getObjectLayout().getAlignment();
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

    boolean walkNativeImageHeapRegions(MemoryWalker.ImageHeapRegionVisitor visitor) {
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

    @Override
    @Uninterruptible(reason = "Called when installing code.", callerMustBe = true)
    public void registerRuntimeCodeInfo(CodeInfo codeInfo) {
        // nothing to do (all runtime compiled code gets processed at every GC)
    }

    @Override
    @Uninterruptible(reason = "Called when installing code.", callerMustBe = true)
    public void registerCodeConstants(CodeInfo codeInfo) {
        // nothing to do, see above
    }

    @Override
    @Uninterruptible(reason = "Called when freeing code.", callerMustBe = true)
    public void unregisterCodeConstants(CodeInfo info) {
        // nothing to do, see above
    }

    @Override
    @Uninterruptible(reason = "Called when freeing code.", callerMustBe = true)
    public void unregisterRuntimeCodeInfo(CodeInfo codeInfo) {
        // nothing to do, see above
    }
}

@TargetClass(value = java.lang.Runtime.class, onlyWith = UseCardRememberedSetHeap.class)
@SuppressWarnings("static-method")
final class Target_java_lang_Runtime {
    @Substitute
    private long freeMemory() {
        return HeapImpl.getHeapImpl().freeMemory().rawValue();
    }

    @Substitute
    private long totalMemory() {
        return HeapImpl.getHeapImpl().totalMemory().rawValue();
    }

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
