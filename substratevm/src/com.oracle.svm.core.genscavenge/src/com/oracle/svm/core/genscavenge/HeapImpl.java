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

import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.compiler.word.Word;
import org.graalvm.nativeimage.Feature.FeatureAccess;
import org.graalvm.nativeimage.IsolateThread;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.MemoryWalker.HeapChunkAccess;
import com.oracle.svm.core.MemoryWalker.ImageCodeAccess;
import com.oracle.svm.core.MemoryWalker.NativeImageHeapRegionAccess;
import com.oracle.svm.core.MemoryWalker.RuntimeCompiledMethodAccess;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.heap.GC;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.NativeImageInfo;
import com.oracle.svm.core.heap.NoAllocationVerifier;
import com.oracle.svm.core.heap.ObjectHeader;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.PinnedAllocator;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicReference;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.RuntimeOptionValues;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

/** An implementation of a card remembered set generational heap. */
public class HeapImpl extends Heap {

    /**
     * The unordered list of PinnedAllocators. Field is managed by {@link PinnedAllocatorImpl}, but
     * declared as an instance field here to avoid a static field in {@link PinnedAllocatorImpl}.
     */
    PinnedAllocatorImpl pinnedAllocatorListHead;

    /*
     * Final state.
     */

    /* The Generations, etc. */
    private final YoungGeneration youngGeneration;
    private final OldGeneration oldGeneration;
    final HeapChunkProvider chunkProvider;

    /** A singleton instance, created during image generation. */
    private final MemoryMXBean memoryMXBean;

    /** A list of all the classes, if someone asks for it. */
    private List<Class<?>> classList;

    /** Constructor for subclasses. */
    @Platforms(Platform.HOSTED_ONLY.class)
    public HeapImpl(FeatureAccess access) {
        this.youngGeneration = new YoungGeneration("YoungGeneration");
        this.oldGeneration = new OldGeneration("OldGeneration");
        this.gcImpl = new GCImpl(access);
        this.objectHeaderImpl = new ObjectHeaderImpl();
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
        this.pinnedAllocatorListHead = null;
        this.objectVisitorWalkerOperation = new ObjectVisitorWalkerOperation();
        this.memoryMXBean = new HeapImplMemoryMXBean();
        this.classList = null;
    }

    @Fold
    public static HeapImpl getHeapImpl() {
        final Heap heap = Heap.getHeap();
        assert heap instanceof HeapImpl : "VMConfiguration heap is not a HeapImpl.";
        return (HeapImpl) heap;
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
    public void disableAllocation(IsolateThread vmThread) {
        ThreadLocalAllocation.disableThreadLocalAllocation(vmThread);
    }

    /*
     * Allocation methods from Heap.
     */

    @Override
    public PinnedAllocator createPinnedAllocator() {
        return new PinnedAllocatorImpl();
    }

    /*
     * Other interface methods from Heap.
     */

    /* Object walking. */

    /* State. */
    private final ObjectVisitorWalkerOperation objectVisitorWalkerOperation;

    private ObjectVisitorWalkerOperation getObjectVisitorWalkerOperation() {
        return objectVisitorWalkerOperation;
    }

    /* Walk the objects of the heap. */
    @Override
    public void walkObjects(ObjectVisitor visitor) {
        try (ObjectVisitorWalkerOperation operation = getObjectVisitorWalkerOperation().open(visitor)) {
            operation.enqueue();
        }
    }

    static class ObjectVisitorWalkerOperation extends VMOperation implements AutoCloseable {

        /** A lazily-initialized visitor. */
        private ObjectVisitor visitor = null;

        ObjectVisitorWalkerOperation() {
            super("ObjectVisitorWalker", CallerEffect.BLOCKS_CALLER, SystemEffect.CAUSES_SAFEPOINT);
        }

        ObjectVisitorWalkerOperation open(ObjectVisitor value) {
            this.visitor = value;
            return this;
        }

        @Override
        public void operate() {
            assert visitor != null : "HeapImpl.ObjectVisitorWalkerOperation.operate: null visitor";
            HeapImpl.getHeapImpl().doWalkObjects(visitor);
        }

        @Override
        public void close() {
            visitor = null;
        }
    }

    private void doWalkObjects(ObjectVisitor visitor) {
        /* Walk the native image heap. */
        if (!NativeImageInfo.walkNativeImageHeap(visitor)) {
            return;
        }
        /* Walk all the Generations that might have objects in them. */
        if (!getYoungGeneration().walkObjects(visitor)) {
            return;
        }
        if (!getOldGeneration().walkObjects(visitor)) {
            return;
        }
    }

    /** Walk the regions of the heap with a MemoryWalker. */
    public boolean walkHeap(MemoryWalker.Visitor visitor) {
        boolean result = true;
        if (result) {
            /* Walk the native image heap regions. */
            result = NativeImageInfo.walkNativeImageHeap(visitor);
        }
        if (result) {
            /* Walk the heap regions. */
            result = (getYoungGeneration().walkHeapChunks(visitor) && getOldGeneration().walkHeapChunks(visitor));
        }
        if (result) {
            /* Walk the free chunk region. */
            result = HeapChunkProvider.get().walkHeapChunks(visitor);
        }
        return result;
    }

    /** State: Who handles object headers? */
    private final ObjectHeaderImpl objectHeaderImpl;

    @Override
    public ObjectHeader getObjectHeader() {
        return getObjectHeaderImpl();
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
    public boolean isAllocationDisallowed() {
        /*
         * This method exists because Heap is the place clients should ask this question, and to
         * aggregate all the reasons allocation might be disallowed. Currently there is only ...
         */
        return NoAllocationVerifier.isActive() || getGCImpl().collectionInProgress.getState();
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
            return ((space == oldGen.getToSpace()) || (space == oldGen.getPinnedToSpace()));
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
        final UnsignedWord headerBits = ObjectHeaderImpl.readHeaderBitsFromObject(instance);
        /* The instance is pinned if it is in the image heap. */
        if (ohi.isBootImageHeaderBits(headerBits)) {
            return true;
        }
        final Space pinnedFromSpace = getOldGeneration().getPinnedFromSpace();
        final Space pinnedToSpace = getOldGeneration().getPinnedToSpace();
        if (ohi.isAlignedHeader(headerBits)) {
            final AlignedHeapChunk.AlignedHeader aChunk = AlignedHeapChunk.getEnclosingAlignedHeapChunk(instance);
            final Space space = aChunk.getSpace();
            /* The instance is pinned if it is in the pinned from space. */
            if ((space == pinnedFromSpace) || (space == pinnedToSpace)) {
                return true;
            }
        }
        if (ohi.isUnalignedHeader(headerBits)) {
            final UnalignedHeapChunk.UnalignedHeader uChunk = UnalignedHeapChunk.getEnclosingUnalignedHeapChunk(instance);
            final Space space = uChunk.getSpace();
            /* The instance is pinned if it is in the pinned from space. */
            if ((space == pinnedFromSpace) || (space == pinnedToSpace)) {
                return true;
            }
        }
        /* Look down the list of individually pinned objects. */
        for (PinnedObjectImpl pinnedObject = getPinHead().get(); pinnedObject != null; pinnedObject = pinnedObject.getNext()) {
            if (instance == pinnedObject.getObject()) {
                return true;
            }
        }
        return false;
    }

    public boolean isImageHeapObject(Object obj) {
        return (NativeImageInfo.isObjectInReadOnlyPrimitivePartition(obj) ||
                        NativeImageInfo.isObjectInReadOnlyReferencePartition(obj) ||
                        NativeImageInfo.isObjectInWritablePrimitivePartition(obj) ||
                        NativeImageInfo.isObjectInWritableReferencePartition(obj));
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
        final Space.Accounting pinnedFrom = getOldGeneration().getPinnedFromSpace().getAccounting();
        final UnsignedWord pinnedFromBytes = pinnedFrom.getAlignedChunkBytes().add(pinnedFrom.getUnalignedChunkBytes());
        final Space.Accounting pinnedTo = getOldGeneration().getPinnedFromSpace().getAccounting();
        final UnsignedWord pinnedToBytes = pinnedTo.getAlignedChunkBytes().add(pinnedTo.getUnalignedChunkBytes());
        final UnsignedWord result = fromBytes.add(toBytes).add(pinnedFromBytes).add(pinnedToBytes);
        if (trace.isEnabled()) {
            trace
                            .string("  fromAligned: ").unsigned(from.getAlignedChunkBytes())
                            .string("  fromUnaligned: ").signed(from.getUnalignedChunkBytes())
                            .string("  toAligned: ").unsigned(to.getAlignedChunkBytes())
                            .string("  toUnaligned: ").signed(to.getUnalignedChunkBytes())
                            .string("  pinnedFromAligned: ").unsigned(pinnedFrom.getAlignedChunkBytes())
                            .string("  pinnedFromUnaligned: ").signed(pinnedFrom.getUnalignedChunkBytes())
                            .string("  pinnedToAligned: ").unsigned(pinnedTo.getAlignedChunkBytes())
                            .string("  pinnedToUnaligned: ").signed(pinnedTo.getUnalignedChunkBytes())
                            .string("  returns: ").unsigned(result).string(" ]").newline();
        }
        return result;
    }

    /** Return the size, in bytes, of the actual used memory, not the committed memory. */
    public UnsignedWord getUsedObjectBytes() {
        final Space youngSpace = getYoungGeneration().getSpace();
        final UnsignedWord youngBytes = youngSpace.getObjectBytes();
        final Space fromSpace = getOldGeneration().getFromSpace();
        final UnsignedWord fromBytes = fromSpace.getObjectBytes();
        final Space pinnedSpace = getOldGeneration().getPinnedFromSpace();
        final UnsignedWord pinnedBytes = pinnedSpace.getObjectBytes();
        final UnsignedWord result = youngBytes.add(fromBytes).add(pinnedBytes);
        return result;
    }

    protected void report(Log log) {
        report(log, HeapPolicyOptions.TraceHeapChunks.getValue());
    }

    public void report(Log log, boolean traceHeapChunks) {
        final HeapImpl heap = HeapImpl.getHeapImpl();
        log.newline().string("[Heap:").newline();
        heap.getYoungGeneration().report(log, traceHeapChunks).newline();
        heap.getOldGeneration().report(log, traceHeapChunks).newline();
        HeapChunkProvider.get().report(log, traceHeapChunks);
        log.string("]");
    }

    /** Print the boundaries of the native image heap partitions. */
    Log bootImageHeapBoundariesToLog(Log log) {
        log.string("[Native image heap boundaries: ").newline();
        log.string("  ReadOnly Primitives: ").hex(Word.objectToUntrackedPointer(NativeImageInfo.firstReadOnlyPrimitiveObject)).string(" .. ").hex(
                        Word.objectToUntrackedPointer(NativeImageInfo.lastReadOnlyPrimitiveObject)).newline();
        log.string("  ReadOnly References: ").hex(Word.objectToUntrackedPointer(NativeImageInfo.firstReadOnlyReferenceObject)).string(" .. ").hex(
                        Word.objectToUntrackedPointer(NativeImageInfo.lastReadOnlyReferenceObject)).newline();
        log.string("  Writable Primitives: ").hex(Word.objectToUntrackedPointer(NativeImageInfo.firstWritablePrimitiveObject)).string(" .. ").hex(
                        Word.objectToUntrackedPointer(NativeImageInfo.lastWritablePrimitiveObject)).newline();
        log.string("  Writable References: ").hex(Word.objectToUntrackedPointer(NativeImageInfo.firstWritableReferenceObject)).string(" .. ").hex(
                        Word.objectToUntrackedPointer(NativeImageInfo.lastWritableReferenceObject));
        log.string("]").newline();
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
            final Object firstObject = NativeImageInfo.firstReadOnlyReferenceObject;
            final Object lastObject = NativeImageInfo.lastReadOnlyReferenceObject;
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
        return (HeapOptions.VerifyHeap.getValue() || HeapOptions.VerifyHeapBeforeCollection.getValue());
    }

    @Fold
    static boolean getVerifyHeapAfterGC() {
        return (HeapOptions.VerifyHeap.getValue() || HeapOptions.VerifyHeapAfterCollection.getValue());
    }

    @Fold
    static boolean getVerifyStackBeforeGC() {
        return (HeapOptions.VerifyHeap.getValue() || HeapOptions.VerifyStackBeforeCollection.getValue());
    }

    @Fold
    static boolean getVerifyStackAfterGC() {
        return (HeapOptions.VerifyHeap.getValue() || HeapOptions.VerifyStackAfterCollection.getValue());
    }

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
            if (!stackVerifier.verifyInAllThreads(KnownIntrinsics.readCallerStackPointer(), KnownIntrinsics.readReturnAddress(), "before collection")) {
                Log.log().string("[HeapImpl.verifyBeforeGC:").string("  cause: ").string(cause).string("  stack fails to verify epoch: ").unsigned(epoch).string("]").newline();
                assert false;
            }
        }
        trace.string("]").newline();
    }

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
            if (!stackVerifier.verifyInAllThreads(KnownIntrinsics.readCallerStackPointer(), KnownIntrinsics.readReturnAddress(), "after collection")) {
                Log.log().string("[HeapImpl.verifyAfterGC:").string("  cause: ").string(cause).string("  stack fails to verify after epoch: ").unsigned(epoch).string("]").newline();
                assert false;
            }
        }
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
        /*
         * This only reports the memory that will be used for heap-allocated objects. For example,
         * it does not include memory in the chunk free list, or memory in the image heap.
         */
        return HeapPolicy.getMaximumHeapSize();
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
final class HeapImplMemoryMXBean implements MemoryMXBean {

    /** Constant for the {@link MemoryUsage} constructor. */
    static final long UNDEFINED_MEMORY_USAGE = -1L;

    /** Instance fields. */
    private final MemoryMXBeanMemoryVisitor visitor;
    private final ObjectName objectName;

    @Platforms(Platform.HOSTED_ONLY.class)
    HeapImplMemoryMXBean() {
        this.visitor = new MemoryMXBeanMemoryVisitor();
        ObjectName name;
        try {
            name = new ObjectName("java.lang:type=Memory,name=HeapImpl");
        } catch (MalformedObjectNameException mone) {
            name = null;
        }
        this.objectName = name;
    }

    @Override
    public ObjectName getObjectName() {
        return objectName;
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
    public <T> boolean visitNativeImageHeapRegion(T bootImageHeapRegion, NativeImageHeapRegionAccess<T> access) {
        final UnsignedWord size = access.getSize(bootImageHeapRegion);
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
    public <T> boolean visitImageCode(T imageCode, ImageCodeAccess<T> access) {
        final UnsignedWord size = access.getSize(imageCode);
        nonHeapUsed = nonHeapUsed.add(size);
        nonHeapCommitted = nonHeapCommitted.add(size);
        return true;
    }

    @Override
    public <T> boolean visitRuntimeCompiledMethod(T runtimeMethod, RuntimeCompiledMethodAccess<T> access) {
        /* Is a runtime method allocated in some larger block of committed memory? */
        final UnsignedWord size = access.getSize(runtimeMethod);
        nonHeapUsed = nonHeapUsed.add(size);
        nonHeapCommitted = nonHeapUsed.add(size);
        return true;
    }
}

@TargetClass(java.lang.Runtime.class)
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
        HeapImpl.getHeapImpl().getHeapPolicy().getUserRequestedGCPolicy().maybeCauseCollection("java.lang.Runtime.gc()");
    }
}
