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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.management.MBeanNotificationInfo;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.nodes.gc.BarrierSet;
import org.graalvm.compiler.nodes.gc.CardTableBarrierSet;
import org.graalvm.compiler.nodes.spi.PlatformConfigurationProvider;
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
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicReference;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

//Checkstyle: stop
import sun.management.Util;
//Checkstyle: resume

/** An implementation of a card remembered set generational heap. */
public class HeapImpl extends Heap {

    private final YoungGeneration youngGeneration;
    private final OldGeneration oldGeneration;
    final HeapChunkProvider chunkProvider;

    // Singleton instances, created during image generation.
    private final GenScavengePlatformConfigurationProvider platformConfigurationProvider;
    private final MemoryMXBean memoryMXBean;
    private final ImageHeapInfo imageHeapInfo;

    // Memory walkers for the image heap
    private final ReadOnlyPrimitiveMemoryWalkerAccess readOnlyPrimitiveWalker;
    private final ReadOnlyReferenceMemoryWalkerAccess readOnlyReferenceWalker;
    private final WritablePrimitiveMemoryWalkerAccess writablePrimitiveWalker;
    private final WritableReferenceMemoryWalkerAccess writableReferenceWalker;

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
        if (getVerifyHeapBeforeGC() || getVerifyHeapAfterGC() || getVerifyStackBeforeGC() || getVerifyStackAfterGC()) {
            this.heapVerifier = HeapVerifierImpl.factory();
            this.stackVerifier = new StackVerifier();
        } else {
            this.heapVerifier = null;
            this.stackVerifier = null;
        }
        chunkProvider = new HeapChunkProvider();
        this.platformConfigurationProvider = new GenScavengePlatformConfigurationProvider();
        this.memoryMXBean = new HeapImplMemoryMXBean();
        this.imageHeapInfo = new ImageHeapInfo();
        this.readOnlyPrimitiveWalker = new ReadOnlyPrimitiveMemoryWalkerAccess();
        this.readOnlyReferenceWalker = new ReadOnlyReferenceMemoryWalkerAccess();
        this.writablePrimitiveWalker = new WritablePrimitiveMemoryWalkerAccess();
        this.writableReferenceWalker = new WritableReferenceMemoryWalkerAccess();
        this.classList = null;
        SubstrateUtil.DiagnosticThunkRegister.getSingleton().register(() -> {
            bootImageHeapBoundariesToLog(Log.log()).newline();
            zapValuesToLog(Log.log()).newline();
            report(Log.log(), true).newline();
            Log.log().newline();
        });
    }

    private static class GenScavengePlatformConfigurationProvider implements PlatformConfigurationProvider {
        private final BarrierSet barrierSet;

        GenScavengePlatformConfigurationProvider() {
            this.barrierSet = new CardTableBarrierSet();
        }

        @Override
        public BarrierSet getBarrierSet() {
            return barrierSet;
        }

        @Override
        public boolean canVirtualizeLargeByteArrayAccess() {
            return true;
        }
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
    public boolean isInImageHeap(Object object) {
        return objectHeaderImpl.isBootImage(object);
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean isInImageHeap(Pointer pointer) {
        return imageHeapInfo.isInReadOnlyPrimitivePartition(pointer) || imageHeapInfo.isInReadOnlyReferencePartition(pointer) ||
                        imageHeapInfo.isInWritablePrimitivePartition(pointer) || imageHeapInfo.isInWritableReferencePartition(pointer);
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
        return walkNativeImageHeap(visitor) && getYoungGeneration().walkHeapChunks(visitor) && getOldGeneration().walkHeapChunks(visitor) && HeapChunkProvider.get().walkHeapChunks(visitor);
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

    /** State: Who handles object headers? */
    private final ObjectHeaderImpl objectHeaderImpl;

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public ObjectHeader getObjectHeader() {
        return objectHeaderImpl;
    }

    public ObjectHeaderImpl getObjectHeaderImpl() {
        return objectHeaderImpl;
    }

    /** State: Who handles garbage collection. */
    private final GCImpl gcImpl;

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
        return NoAllocationVerifier.isActive() || gcImpl.collectionInProgress.getState();
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
        return getYoungGeneration().getSpace();
    }

    public HeapChunk.Header<?> getEnclosingHeapChunk(Object obj) {
        final ObjectHeaderImpl ohi = getObjectHeaderImpl();
        if (ohi.isAlignedObject(obj)) {
            return AlignedHeapChunk.getEnclosingAlignedHeapChunk(obj);
        } else if (ohi.isUnalignedObject(obj)) {
            return UnalignedHeapChunk.getEnclosingUnalignedHeapChunk(obj);
        } else {
            try (Log failure = Log.log().string("[HeapImpl.getEnclosingHeapChunk:")) {
                failure.string("  obj: ").hex(Word.objectToUntrackedPointer(obj));
                /* This might not work: */
                final UnsignedWord header = ObjectHeaderImpl.readHeaderFromObjectCarefully(obj);
                failure.string("  header: ").hex(header).string("  is neither aligned nor unaligned").newline();
                /* This really might not work: */
                failure.string("  obj: ").object(obj).string("]").newline();
            }
            throw VMError.shouldNotReachHere();
        }
    }

    public Object promoteObject(Object original) {
        final Log trace = Log.noopLog().string("[HeapImpl.promoteObject:").string("  original: ").object(original);

        final OldGeneration oldGen = getOldGeneration();
        final Object result = oldGen.promoteObject(original);

        trace.string("  result: ").object(result).string("]").newline();
        return result;
    }

    boolean hasSurvivedThisCollection(Object obj) {
        final ObjectHeaderImpl ohi = getObjectHeaderImpl();
        if (ohi.isBootImage(obj)) {
            /* If the object is in the native image heap, then it will survive. */
            return true;
        }
        if (ohi.isHeapAllocated(obj)) {
            /*
             * If the object is in the heap, then check if it is in the destination part of the old
             * generation.
             */
            final HeapChunk.Header<?> chunk = getEnclosingHeapChunk(obj);
            final Space space = chunk.getSpace();
            final OldGeneration oldGen = getOldGeneration();
            return space == oldGen.getToSpace();
        }
        return false;
    }

    /** State: Who decides the heap policy? */
    private final HeapPolicy heapPolicy;

    public HeapPolicy getHeapPolicy() {
        return HeapImpl.getHeapImpl().heapPolicy;
    }

    /*
     * There could be a field in a Space that says what generation it is in, and I could fetch that
     * field and ask if it is the space of the young generation. But this is not a good place to use
     * that field, because this.getYoungGeneration().isYoungSpace(space) should compile to a test
     * against a constant because the YoungGeneration and its Space are allocated during image
     * build, so the *address* of the young generation space is a runtime constant. Compare that to
     * asking the Space for its generation: a field fetch and then comparing that against the
     * constant getYoungGeneration().getSpace(), which is not as good.
     */

    public boolean isYoungGeneration(Space space) {
        return getYoungGeneration().isYoungSpace(space);
    }

    public YoungGeneration getYoungGeneration() {
        return youngGeneration;
    }

    public OldGeneration getOldGeneration() {
        return oldGeneration;
    }

    /** The head of the linked list of object pins. */
    private AtomicReference<PinnedObjectImpl> pinHead;

    public AtomicReference<PinnedObjectImpl> getPinHead() {
        return pinHead;
    }

    public boolean isPinned(Object instance) {
        final ObjectHeaderImpl ohi = getObjectHeaderImpl();
        /* The instance is pinned if it is in the image heap. */
        if (ohi.isBootImage(instance)) {
            return true;
        }
        /* Look down the list of individually pinned objects. */
        for (PinnedObjectImpl pinnedObject = getPinHead().get(); pinnedObject != null; pinnedObject = pinnedObject.getNext()) {
            if (instance == pinnedObject.getObject()) {
                return true;
            }
        }
        return false;
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
        final Space.Accounting young = getYoungGeneration().getSpace().getAccounting();
        return young.getAlignedChunkBytes().add(young.getUnalignedChunkBytes());
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

    /** Return the size, in bytes, of the actual used memory, not the committed memory. */
    public UnsignedWord getUsedObjectBytes() {
        final Space youngSpace = getYoungGeneration().getSpace();
        final UnsignedWord youngBytes = youngSpace.getObjectBytes();
        final Space fromSpace = getOldGeneration().getFromSpace();
        final UnsignedWord fromBytes = fromSpace.getObjectBytes();
        return youngBytes.add(fromBytes);
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

    /** Print the boundaries of the native image heap partitions. */
    Log bootImageHeapBoundariesToLog(Log log) {
        log.string("[Native image heap boundaries: ").indent(true);
        log.string("ReadOnly Primitives: ").hex(Word.objectToUntrackedPointer(imageHeapInfo.firstReadOnlyPrimitiveObject)).string(" .. ").hex(
                        Word.objectToUntrackedPointer(imageHeapInfo.lastReadOnlyPrimitiveObject)).newline();
        log.string("ReadOnly References: ").hex(Word.objectToUntrackedPointer(imageHeapInfo.firstReadOnlyReferenceObject)).string(" .. ").hex(
                        Word.objectToUntrackedPointer(imageHeapInfo.lastReadOnlyReferenceObject)).newline();
        log.string("Writable Primitives: ").hex(Word.objectToUntrackedPointer(imageHeapInfo.firstWritablePrimitiveObject)).string(" .. ").hex(
                        Word.objectToUntrackedPointer(imageHeapInfo.lastWritablePrimitiveObject)).newline();
        log.string("Writable References: ").hex(Word.objectToUntrackedPointer(imageHeapInfo.firstWritableReferenceObject)).string(" .. ").hex(
                        Word.objectToUntrackedPointer(imageHeapInfo.lastWritableReferenceObject));
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

    /** State: The heap verifier. */
    private HeapVerifierImpl heapVerifier;

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
    }

    /** For assertions: Verify that the hub is a reference to where DynamicHubs live in the heap. */
    public boolean assertHub(DynamicHub hub) {
        /* DynamicHubs live only in the read-only reference section of the image heap. */
        return imageHeapInfo.isObjectInReadOnlyReferencePartition(hub);
    }

    /** For assertions: Verify the hub of the object. */
    public boolean assertHubOfObject(Object obj) {
        final DynamicHub hub = ObjectHeader.readDynamicHubFromObject(obj);
        return assertHub(hub);
    }

    /** For assertions: Verify that a Space is a valid Space. */
    public boolean isValidSpace(Space space) {
        return (getYoungGeneration().isValidSpace(space) || getOldGeneration().isValidSpace(space));
    }

    /** State: The stack verifier. */
    private final StackVerifier stackVerifier;

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
        return maxMemory().subtract(HeapPolicy.getBytesAllocatedSinceLastCollection()).subtract(getOldUsedChunkBytes());
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
            return walkPartition(imageHeapInfo.firstReadOnlyPrimitiveObject, imageHeapInfo.lastReadOnlyPrimitiveObject, visitor) &&
                            walkPartition(imageHeapInfo.firstReadOnlyReferenceObject, imageHeapInfo.lastReadOnlyReferenceObject, visitor) &&
                            walkPartition(imageHeapInfo.firstWritablePrimitiveObject, imageHeapInfo.lastWritablePrimitiveObject, visitor) &&
                            walkPartition(imageHeapInfo.firstWritableReferenceObject, imageHeapInfo.lastWritableReferenceObject, visitor);
        }
        return true;
    }

    @Override
    public boolean walkCollectedHeapObjects(ObjectVisitor visitor) {
        VMOperation.guaranteeInProgressAtSafepoint("Must only be called at a safepoint");
        return getYoungGeneration().walkObjects(visitor) && getOldGeneration().walkObjects(visitor);
    }

    private boolean walkNativeImageHeap(MemoryWalker.Visitor visitor) {
        return visitor.visitNativeImageHeapRegion(readOnlyPrimitiveWalker) && visitor.visitNativeImageHeapRegion(readOnlyReferenceWalker) &&
                        visitor.visitNativeImageHeapRegion(writablePrimitiveWalker) && visitor.visitNativeImageHeapRegion(writableReferenceWalker);
    }

    private static boolean walkPartition(Object firstObject, Object lastObject, ObjectVisitor visitor) {
        if (firstObject == null || lastObject == null) {
            assert firstObject == null && lastObject == null;
            return true;
        }
        final Pointer firstPointer = Word.objectToUntrackedPointer(firstObject);
        final Pointer lastPointer = Word.objectToUntrackedPointer(lastObject);
        Pointer current = firstPointer;
        while (current.belowOrEqual(lastPointer)) {
            final Object currentObject = KnownIntrinsics.convertUnknownValue(current.toObject(), Object.class);
            if (!visitor.visitObject(currentObject)) {
                return false;
            }
            current = LayoutEncoding.getObjectEnd(currentObject);
        }
        return true;
    }

    @Override
    public PlatformConfigurationProvider getPlatformConfigurationProvider() {
        return platformConfigurationProvider;
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
    public boolean visitNativeImageHeapRegion(NativeImageHeapRegionAccess access) {
        final UnsignedWord size = access.getSize();
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

/** A base class with shared logic for all the MemoryWalkerAccessImpl implementations. */
class BaseMemoryWalkerAccessImpl {
    /*
     * This looks like the "firstObject" and "lastObject" parameters could be replaced with instance
     * fields, initialized in the constructors for the subclasses and used here. That would not work
     * because the MemoryWalkerAccessImpl instances are created during native image generation at
     * which point I do not know the location of the first and last objects of each region. So, I
     * have to indirect through the variables that are relocated during image loading.
     */

    @Platforms(Platform.HOSTED_ONLY.class)
    protected BaseMemoryWalkerAccessImpl() {
    }

    protected UnsignedWord getStart(Object firstObject) {
        return Word.objectToUntrackedPointer(firstObject);
    }

    /** Return the distance from the start of the first object to the end of the last object. */
    protected UnsignedWord getSize(Object firstObject, Object lastObject) {
        final Pointer firstStart = Word.objectToUntrackedPointer(firstObject);
        final Pointer lastEnd = LayoutEncoding.getObjectEnd(lastObject);
        return lastEnd.subtract(firstStart);
    }
}

class ReadOnlyPrimitiveMemoryWalkerAccess extends BaseMemoryWalkerAccessImpl implements MemoryWalker.NativeImageHeapRegionAccess {
    @Platforms(Platform.HOSTED_ONLY.class)
    protected ReadOnlyPrimitiveMemoryWalkerAccess() {
    }

    @Override
    public UnsignedWord getStart() {
        return getStart(HeapImpl.getImageHeapInfo().firstReadOnlyPrimitiveObject);
    }

    @Override
    public UnsignedWord getSize() {
        return getSize(HeapImpl.getImageHeapInfo().firstReadOnlyPrimitiveObject, HeapImpl.getImageHeapInfo().lastReadOnlyPrimitiveObject);
    }

    @Override
    public String getRegion() {
        return "read-only primitives";
    }
}

class ReadOnlyReferenceMemoryWalkerAccess extends BaseMemoryWalkerAccessImpl implements MemoryWalker.NativeImageHeapRegionAccess {
    @Platforms(Platform.HOSTED_ONLY.class)
    protected ReadOnlyReferenceMemoryWalkerAccess() {
    }

    @Override
    public UnsignedWord getStart() {
        return getStart(HeapImpl.getImageHeapInfo().firstReadOnlyReferenceObject);
    }

    @Override
    public UnsignedWord getSize() {
        return getSize(HeapImpl.getImageHeapInfo().firstReadOnlyReferenceObject, HeapImpl.getImageHeapInfo().lastReadOnlyReferenceObject);
    }

    @Override
    public String getRegion() {
        return "read-only references";
    }
}

class WritablePrimitiveMemoryWalkerAccess extends BaseMemoryWalkerAccessImpl implements MemoryWalker.NativeImageHeapRegionAccess {
    @Platforms(Platform.HOSTED_ONLY.class)
    protected WritablePrimitiveMemoryWalkerAccess() {
    }

    @Override
    public UnsignedWord getStart() {
        return getStart(HeapImpl.getImageHeapInfo().firstWritablePrimitiveObject);
    }

    @Override
    public UnsignedWord getSize() {
        return getSize(HeapImpl.getImageHeapInfo().firstWritablePrimitiveObject, HeapImpl.getImageHeapInfo().lastWritablePrimitiveObject);
    }

    @Override
    public String getRegion() {
        return "writable primitives";
    }
}

class WritableReferenceMemoryWalkerAccess extends BaseMemoryWalkerAccessImpl implements MemoryWalker.NativeImageHeapRegionAccess {
    @Platforms(Platform.HOSTED_ONLY.class)
    protected WritableReferenceMemoryWalkerAccess() {
    }

    @Override
    public UnsignedWord getStart() {
        return getStart(HeapImpl.getImageHeapInfo().firstWritableReferenceObject);
    }

    @Override
    public UnsignedWord getSize() {
        return getSize(HeapImpl.getImageHeapInfo().firstWritableReferenceObject, HeapImpl.getImageHeapInfo().lastWritableReferenceObject);
    }

    @Override
    public String getRegion() {
        return "writable references";
    }
}
