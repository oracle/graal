/*
 * Copyright (c) 2018, 2025, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.util.PointerUtils.roundDown;
import static com.oracle.svm.core.util.PointerUtils.roundUp;
import static com.oracle.svm.core.util.VMError.guarantee;
import static jdk.graal.compiler.word.Word.nullPointer;
import static jdk.graal.compiler.word.Word.unsigned;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.IsolateArgumentAccess;
import com.oracle.svm.core.IsolateArgumentParser;
import com.oracle.svm.core.IsolateArguments;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.graal.stackvalue.UnsafeStackValue;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.OutOfMemoryUtil;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.locks.VMMutex;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.memory.NullableNativeMemory;
import com.oracle.svm.core.metaspace.Metaspace;
import com.oracle.svm.core.nmt.NativeMemoryTracking;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.ChunkBasedCommittedMemoryProvider;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.os.ImageHeapProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;

/**
 * Reserves a fixed-size address range and provides memory from it by committing and uncommitting
 * virtual memory within that range. The address space is shared by the null regions, the
 * {@link Metaspace}, the image heap, and the collected Java heap.
 * <p>
 * The main objective of this code is to keep external fragmentation low so that an
 * {@linkplain Isolate} is unlikely to run out of memory because its address space is exhausted. To
 * accomplish that, allocation requests are satisfied with a best-fit strategy that traverses the
 * {@linkplain #allocListHead entire list of allocatable blocks} and chooses the smallest block that
 * satisfies the request.
 * <p>
 * Allocating memory usually involves splitting a block. When a new block is smaller than the
 * minimum size that can be allocated, it is kept only in a {@linkplain #unusedListHead separate
 * list that contains all unused blocks}. This list is needed when adjacent memory areas are freed
 * so that they can be coalesced into a larger memory area. Observations have shown that the list of
 * allocatable blocks is around half of the size of the list of all unused blocks. Overall, list
 * traversals should be negligible in contrast to the cost of the performed commit and uncommit
 * operations that require system calls. Avoiding these operations is not a design goal of this
 * class and should be implemented by code using it.
 * <p>
 * However, traversing the list of allocatable blocks might become expensive in long-running
 * programs due to increasing fragmentation. In that case, the list could be replaced by a
 * self-balancing search tree with block sizes as keys, which guarantees logarithmic time complexity
 * for allocation. Several blocks of the same size could be grouped in one tree node to reduce tree
 * operations (particularly balancing).
 */
public class AddressRangeCommittedMemoryProvider extends ChunkBasedCommittedMemoryProvider {
    private static final long MIN_RESERVED_ADDRESS_SPACE_SIZE = 32L * 1024 * 1024 * 1024;

    protected static final int NO_ERROR = 0;
    protected static final int OUT_OF_ADDRESS_SPACE = 1;
    protected static final int COMMIT_FAILED = 2;

    private static final OutOfMemoryError NODE_ALLOCATION_FAILED = new OutOfMemoryError("Could not allocate node for free list, OS may be out of memory.");
    private static final OutOfMemoryError OUT_OF_METASPACE = new OutOfMemoryError("Could not allocate a metaspace chunk because the metaspace is exhausted.");
    private static final OutOfMemoryError ALIGNED_OUT_OF_ADDRESS_SPACE = new OutOfMemoryError("Could not allocate an aligned heap chunk because the heap address space is exhausted. " +
                    "Consider increasing the address space size (see option -XX:ReservedAddressSpaceSize).");
    private static final OutOfMemoryError UNALIGNED_OUT_OF_ADDRESS_SPACE = new OutOfMemoryError("Could not allocate an unaligned heap chunk because the heap address space is exhausted. " +
                    "Consider increasing the address space size (see option -XX:ReservedAddressSpaceSize).");

    /**
     * This mutex is used by the GC and the application. The application may hold this mutex only in
     * uninterruptible code to prevent the case that a safepoint can be initiated while the
     * application holds the mutex. Otherwise, we would risk deadlocks between the application and
     * the GC.
     */
    private final VMMutex lock = new VMMutex("freeList");

    protected UnsignedWord reservedAddressSpaceSize;
    private Pointer metaspaceBegin;
    private Pointer metaspaceTop;
    private Pointer metaspaceEnd;
    protected Pointer collectedHeapBegin;
    protected UnsignedWord collectedHeapSize;

    /**
     * Contains free blocks for the collected Java heap that are large enough to fit allocations.
     */
    protected FreeListNode allocListHead;
    protected long allocListCount;

    /**
     * Contains all free blocks for the collected Java heap, including small blocks that are needed
     * for coalescing.
     */
    protected FreeListNode unusedListHead;
    protected long unusedListCount;

    @Platforms(Platform.HOSTED_ONLY.class)
    public AddressRangeCommittedMemoryProvider() {
        assert SubstrateOptions.SpawnIsolates.getValue();
    }

    @Fold
    public static AddressRangeCommittedMemoryProvider singleton() {
        return (AddressRangeCommittedMemoryProvider) ImageSingletons.lookup(CommittedMemoryProvider.class);
    }

    @Override
    @Uninterruptible(reason = "Still being initialized.")
    public int initialize(WordPointer heapBaseOut, IsolateArguments arguments) {
        UnsignedWord reservedSize = Word.unsigned(IsolateArgumentAccess.readLong(arguments, IsolateArgumentParser.getOptionIndex(SubstrateGCOptions.ReservedAddressSpaceSize)));
        if (reservedSize.equal(0)) {
            /*
             * Reserve a 32 GB address space, except if a larger heap size was specified, or if the
             * maximum address space size is less than that.
             */
            UnsignedWord maxHeapSize = Word.unsigned(IsolateArgumentAccess.readLong(arguments, IsolateArgumentParser.getOptionIndex(SubstrateGCOptions.MaxHeapSize)));
            reservedSize = UnsignedUtils.max(maxHeapSize, Word.unsigned(MIN_RESERVED_ADDRESS_SPACE_SIZE));
        }
        reservedSize = UnsignedUtils.min(reservedSize, ReferenceAccess.singleton().getMaxAddressSpaceSize());

        UnsignedWord alignment = unsigned(Heap.getHeap().getHeapBaseAlignment());
        WordPointer reservedBeginPtr = StackValue.get(WordPointer.class);
        int errorCode = reserveHeapMemory(reservedSize, alignment, arguments, reservedBeginPtr);
        if (errorCode != CEntryPointErrors.NO_ERROR) {
            return errorCode;
        }

        Pointer reservedBegin = reservedBeginPtr.read();
        WordPointer imageHeapEndOut = StackValue.get(WordPointer.class);
        errorCode = ImageHeapProvider.get().initialize(reservedBegin, reservedSize, heapBaseOut, imageHeapEndOut);
        if (errorCode != CEntryPointErrors.NO_ERROR) {
            freeOnInitializeError(reservedBegin, reservedSize);
            return errorCode;
        }

        CEntryPointSnippets.initBaseRegisters(heapBaseOut.read());
        WordPointer runtimeHeapBeginOut = StackValue.get(WordPointer.class);
        errorCode = initializeCollectedHeapBegin(arguments, reservedBegin, reservedSize, imageHeapEndOut.read(), runtimeHeapBeginOut);
        if (errorCode != CEntryPointErrors.NO_ERROR) {
            freeOnInitializeError(reservedBegin, reservedSize);
            return errorCode;
        }

        /*
         * We can access the image heap from here on, but our `this` argument is not safe to use
         * because the image heap was not initialized when we were called, so we invoke a static
         * method that loads a new reference to our instance.
         */
        errorCode = initialize(reservedBegin, reservedSize, runtimeHeapBeginOut.read());
        if (errorCode != CEntryPointErrors.NO_ERROR) {
            freeOnInitializeError(reservedBegin, reservedSize);
        }
        return errorCode;
    }

    @Uninterruptible(reason = "Still being initialized.")
    protected int initializeCollectedHeapBegin(@SuppressWarnings("unused") IsolateArguments arguments, @SuppressWarnings("unused") Pointer reservedBegin,
                    @SuppressWarnings("unused") UnsignedWord reservedSize, Pointer imageHeapEnd, WordPointer collectedHeapBeginOut) {
        assert PointerUtils.isAMultiple(imageHeapEnd, Word.unsigned(SubstrateOptions.getPageSize()));
        collectedHeapBeginOut.write(imageHeapEnd);
        return CEntryPointErrors.NO_ERROR;
    }

    @NeverInline("Ensure a newly looked up value is used as 'this', now that the image heap is initialized")
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    private static int initialize(Pointer reservedBegin, UnsignedWord reservedSize, Pointer collectedHeapBegin) {
        AddressRangeCommittedMemoryProvider provider = (AddressRangeCommittedMemoryProvider) ChunkBasedCommittedMemoryProvider.get();
        return provider.initializeFields(reservedBegin, reservedSize, collectedHeapBegin);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @SuppressWarnings("hiding")
    protected int initializeFields(Pointer reservedBegin, UnsignedWord reservedSize, Pointer collectedHeapBegin) {
        this.reservedAddressSpaceSize = reservedSize;

        initializeMetaspaceFields();
        return initializeCollectedHeapFields(reservedBegin, reservedSize, collectedHeapBegin);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected void initializeMetaspaceFields() {
        int metaspaceSize = SerialAndEpsilonGCOptions.getReservedMetaspaceSize();
        this.metaspaceBegin = KnownIntrinsics.heapBase().add(HeapImpl.getMetaspaceOffsetInAddressSpace());
        this.metaspaceTop = metaspaceBegin;
        this.metaspaceEnd = metaspaceTop.add(metaspaceSize);

        if (VMInspectionOptions.hasNativeMemoryTrackingSupport() && metaspaceSize > 0) {
            NativeMemoryTracking.singleton().trackReserve(metaspaceSize, NmtCategory.Metaspace);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @SuppressWarnings("hiding")
    private int initializeCollectedHeapFields(Pointer reservedBegin, UnsignedWord reservedSize, Pointer collectedHeapBegin) {
        this.collectedHeapBegin = collectedHeapBegin;
        this.collectedHeapSize = reservedSize.subtract(collectedHeapBegin.subtract(reservedBegin));

        if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            NativeMemoryTracking.singleton().trackReserve(collectedHeapSize, NmtCategory.JavaHeap);
        }

        FreeListNode node = allocNodeOrNull(collectedHeapBegin, collectedHeapSize);
        if (node.isNull()) {
            return CEntryPointErrors.ALLOCATION_FAILED;
        }

        this.unusedListHead = node;
        this.unusedListCount = 1;
        this.allocListHead = node;
        this.allocListCount = 1;
        return CEntryPointErrors.NO_ERROR;
    }

    @Override
    public UnsignedWord getCollectedHeapAddressSpaceSize() {
        return collectedHeapSize;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isInMetaspace(Pointer ptr) {
        /* Checking against begin and end does not need any locking. */
        return ptr.aboveOrEqual(metaspaceBegin) && ptr.belowThan(metaspaceEnd);
    }

    @Uninterruptible(reason = "Still being initialized.")
    protected int reserveHeapMemory(UnsignedWord reserved, UnsignedWord alignment, IsolateArguments arguments, WordPointer beginOut) {
        Pointer begin = reserveHeapMemory0(reserved, alignment, arguments);
        if (begin.isNull()) {
            return CEntryPointErrors.RESERVE_ADDRESS_SPACE_FAILED;
        }
        beginOut.write(begin);
        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Still being initialized.")
    protected Pointer reserveHeapMemory0(UnsignedWord reserved, UnsignedWord alignment, @SuppressWarnings("unused") IsolateArguments arguments) {
        return VirtualMemoryProvider.get().reserve(reserved, alignment, false);
    }

    @Uninterruptible(reason = "Still being initialized.")
    protected void freeOnInitializeError(Pointer begin, UnsignedWord reserved) {
        VirtualMemoryProvider.get().free(begin, reserved);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private FreeListNode allocNode(Pointer start, UnsignedWord size) {
        FreeListNode node = allocNodeOrNull(start, size);
        if (node.isNull()) {
            throw NODE_ALLOCATION_FAILED;
        }
        return node;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private FreeListNode allocNodeOrNull(Pointer start, UnsignedWord size) {
        FreeListNode node = NullableNativeMemory.calloc(sizeOfFreeListNode(), NmtCategory.GC);
        if (node.isNonNull()) {
            setBounds(node, start, size);
        }
        return node;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected UnsignedWord sizeOfFreeListNode() {
        return SizeOf.unsigned(FreeListNode.class);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static void freeNode(FreeListNode node) {
        NullableNativeMemory.free(node);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean isAllocatable(UnsignedWord size) {
        return size.aboveOrEqual(minAllocationSize());
    }

    @Fold
    static UnsignedWord minAllocationSize() {
        return UnsignedUtils.min(HeapParameters.getAlignedHeapChunkSize(), HeapParameters.getMinUnalignedChunkSize());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void setBounds(FreeListNode node, Pointer start, UnsignedWord size) {
        assert UnsignedUtils.isAMultiple(start, getGranularity());
        assert UnsignedUtils.isAMultiple(size, getGranularity());
        assert start.aboveOrEqual(collectedHeapBegin);
        assert size.belowOrEqual(collectedHeapSize);
        assert start.add(size).belowOrEqual(collectedHeapBegin.add(collectedHeapSize));

        node.setStart(start);
        node.setSize(size);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected static Pointer getNodeEnd(FreeListNode node) {
        return node.getStart().add(node.getSize());
    }

    @Override
    @Uninterruptible(reason = "Tear-down in progress.")
    public int tearDown() {
        FreeListNode node = unusedListHead;
        while (node.isNonNull()) {
            FreeListNode next = node.getUnusedNext();
            freeNode(node);
            node = next;
        }
        // ImageHeapProvider.freeImageHeap must not be called because the ImageHeapProvider did not
        // allocate any memory for the image heap.
        return unmapAddressSpace(KnownIntrinsics.heapBase());
    }

    @Uninterruptible(reason = "Tear-down in progress.")
    protected int unmapAddressSpace(PointerBase heapBase) {
        if (VirtualMemoryProvider.get().free(heapBase, reservedAddressSpaceSize) != 0) {
            return CEntryPointErrors.FREE_ADDRESS_SPACE_FAILED;
        }
        return CEntryPointErrors.NO_ERROR;
    }

    @Override
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    public Pointer allocateMetaspaceChunk(UnsignedWord nbytes, UnsignedWord alignment) {
        lock.lockNoTransition();
        try {
            return allocateMetaspaceChunk0(nbytes, alignment);
        } finally {
            lock.unlock();
        }
    }

    /**
     * This method intentionally does not use {@link OutOfMemoryUtil} when reporting
     * {@link OutOfMemoryError}s as the metaspace is not part of the Java heap.
     */
    @Uninterruptible(reason = "Locking without transition requires that the whole critical section is uninterruptible.")
    private Pointer allocateMetaspaceChunk0(UnsignedWord nbytes, UnsignedWord alignment) {
        assert lock.isOwner();

        Pointer result = metaspaceTop;
        Pointer newTop = metaspaceTop.add(nbytes);
        assert result.isNonNull();
        assert PointerUtils.isAMultiple(result, alignment);
        assert UnsignedUtils.isAMultiple(newTop, alignment);

        /* Check if the allocation fits into the reserved address space. */
        if (newTop.aboveThan(metaspaceEnd)) {
            throw OUT_OF_METASPACE;
        }

        /* Try to commit the memory. */
        int access = VirtualMemoryProvider.Access.READ | VirtualMemoryProvider.Access.WRITE;
        Pointer actualBegin = VirtualMemoryProvider.get().commit(result, nbytes, access);
        if (actualBegin.isNull()) {
            throw METASPACE_CHUNK_COMMIT_FAILED;
        }

        /* Update top and NMT statistics. */
        metaspaceTop = newTop;
        if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            NativeMemoryTracking.singleton().trackCommit(nbytes, NmtCategory.Metaspace);
        }
        return actualBegin;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Pointer allocateAlignedChunk(UnsignedWord nbytes, UnsignedWord alignment) {
        WordPointer allocOut = UnsafeStackValue.get(WordPointer.class);
        int error = allocateInHeapAddressSpace(nbytes, alignment, allocOut);
        if (error == NO_ERROR) {
            if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
                NativeMemoryTracking.singleton().trackCommit(nbytes, NmtCategory.JavaHeap);
            }
            return allocOut.read();
        }
        throw reportAlignedChunkAllocationFailed(error);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected OutOfMemoryError reportAlignedChunkAllocationFailed(int error) {
        if (error == OUT_OF_ADDRESS_SPACE) {
            throw OutOfMemoryUtil.reportOutOfMemoryError(ALIGNED_OUT_OF_ADDRESS_SPACE);
        } else if (error == COMMIT_FAILED) {
            throw OutOfMemoryUtil.reportOutOfMemoryError(ALIGNED_CHUNK_COMMIT_FAILED);
        } else {
            throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public Pointer allocateUnalignedChunk(UnsignedWord nbytes) {
        WordPointer allocOut = UnsafeStackValue.get(WordPointer.class);
        int error = allocateInHeapAddressSpace(nbytes, getAlignmentForUnalignedChunks(), allocOut);
        if (error == NO_ERROR) {
            if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
                NativeMemoryTracking.singleton().trackCommit(nbytes, NmtCategory.JavaHeap);
            }
            return allocOut.read();
        }
        throw reportUnalignedChunkAllocationFailed(error);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected OutOfMemoryError reportUnalignedChunkAllocationFailed(int error) {
        if (error == OUT_OF_ADDRESS_SPACE) {
            throw OutOfMemoryUtil.reportOutOfMemoryError(UNALIGNED_OUT_OF_ADDRESS_SPACE);
        } else if (error == COMMIT_FAILED) {
            throw OutOfMemoryUtil.reportOutOfMemoryError(UNALIGNED_CHUNK_COMMIT_FAILED);
        } else {
            throw VMError.shouldNotReachHereAtRuntime();
        }
    }

    /**
     * Allocates memory from the address space. If the allocation succeeded, {@link #NO_ERROR} is
     * returned. The allocated memory is always committed.
     */
    @Uninterruptible(reason = "Entering a safepoint in this code can deadlock garbage collection.")
    protected int allocateInHeapAddressSpace(UnsignedWord size, UnsignedWord alignment, WordPointer allocOut) {
        assert size.aboveThan(0);
        assert alignment.aboveThan(0);

        // this code is also executed in JNI_CreateJavaVM, so we don't always know the owning thread
        lock.lockNoTransitionUnspecifiedOwner();
        try {
            // Find best fit for requested size and alignment
            FreeListNode fit = nullPointer();
            FreeListNode fitAllocPrevious = nullPointer();
            UnsignedWord fitSize = UnsignedUtils.MAX_VALUE;
            for (FreeListNode previous = nullPointer(), node = allocListHead; node.isNonNull(); previous = node, node = node.getAllocNext()) {
                UnsignedWord nodeSize = node.getSize();
                if (nodeSize.aboveOrEqual(size) && nodeSize.belowThan(fitSize)) {
                    Pointer offset = roundUp(node.getStart(), alignment).subtract(node.getStart());
                    if (nodeSize.subtract(size).aboveOrEqual(offset)) {
                        fitAllocPrevious = previous;
                        fit = node;
                        fitSize = nodeSize;
                        if (nodeSize.equal(size)) {
                            break; // perfect fit
                        }
                    }
                }
            }

            if (fit.isNull()) {
                allocOut.write(nullPointer());
                return OUT_OF_ADDRESS_SPACE;
            }

            UnsignedWord pageSize = getGranularity();

            // Determine an area that satisfies both requested alignment and page size alignment
            Pointer fitStart = fit.getStart();
            assert PointerUtils.isAMultiple(fitStart, pageSize);
            assert UnsignedUtils.isAMultiple(fitSize, pageSize);

            Pointer allocated = roundUp(fitStart, alignment);
            Pointer mapBegin = roundDown(allocated, pageSize);
            Pointer mapEnd = roundUp(allocated.add(size), pageSize);
            UnsignedWord alignedSize = mapEnd.subtract(mapBegin);
            assert mapBegin.aboveOrEqual(fitStart) && mapEnd.belowOrEqual(fitStart.add(fitSize));

            final int access = VirtualMemoryProvider.Access.READ | VirtualMemoryProvider.Access.WRITE;
            Pointer actualMapBegin = VirtualMemoryProvider.get().commit(mapBegin, alignedSize, access);
            if (actualMapBegin.isNull()) {
                allocOut.write(nullPointer());
                return COMMIT_FAILED;
            }
            VMError.guarantee(actualMapBegin.equal(mapBegin), "Must not be mapped anywhere else.");

            /*
             * Update lists with leftover memory, reusing the existing node whenever possible. If
             * possible, we reuse the existing node in a way that we don't need to touch allocList.
             */
            UnsignedWord leadingSize = mapBegin.subtract(fitStart);
            UnsignedWord trailingSize = fitStart.add(fitSize).subtract(mapEnd);

            boolean perfectFit = false;
            if (leadingSize.aboveThan(0) && trailingSize.aboveThan(0)) {
                if (isAllocatable(leadingSize)) {
                    /* Reuse the existing node for the leading memory. */
                    createNodeForTrailingMemory(fit, mapEnd, trailingSize);
                    trimBounds(fit, fit.getStart(), leadingSize);
                } else {
                    /* Reuse the existing node for the trailing memory. */
                    createNodeForLeadingMemory(fit, leadingSize);
                    trimBounds(fit, mapEnd, trailingSize);
                }
            } else if (trailingSize.aboveThan(0)) {
                /* Reuse the existing node for the trailing memory. */
                trimBounds(fit, mapEnd, trailingSize);
            } else if (leadingSize.aboveThan(0)) {
                /* Reuse the existing node for the leading memory. */
                trimBounds(fit, fit.getStart(), leadingSize);
            } else {
                perfectFit = true;
            }

            if (perfectFit || !isAllocatable(fit.getSize())) {
                removeFromAllocList(fit, fitAllocPrevious);
            }

            if (perfectFit) {
                /* Perfect fit, remove node entirely. */
                removeFromUnusedList(fit);
                freeNode(fit);
                fit = nullPointer();
            }

            allocOut.write(allocated);
            return NO_ERROR;
        } finally {
            lock.unlockNoTransitionUnspecifiedOwner();
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void createNodeForLeadingMemory(FreeListNode fit, UnsignedWord leadingSize) {
        FreeListNode leadingNode = createNodeWhenSplitting(fit, fit.getStart(), leadingSize);
        addToUnusedList(leadingNode, fit.getUnusedPrevious());

        /* No need to add the new node to allocList if leadingSize is too small. */
        assert !isAllocatable(leadingSize);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void createNodeForTrailingMemory(FreeListNode fit, Pointer trailingStart, UnsignedWord trailingSize) {
        FreeListNode trailingNode = createNodeWhenSplitting(fit, trailingStart, trailingSize);
        addToUnusedList(trailingNode, fit);

        if (isAllocatable(trailingSize)) {
            addToAllocList(trailingNode, fit);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected FreeListNode createNodeWhenSplitting(@SuppressWarnings("unused") FreeListNode fit, Pointer start, UnsignedWord size) {
        return allocNode(start, size);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void addToUnusedList(FreeListNode newNode, FreeListNode prev) {
        assert newNode.isNonNull();
        assert newNode.getUnusedNext().isNull();
        assert newNode.getUnusedPrevious().isNull();

        FreeListNode next;
        if (prev.isNull()) {
            next = unusedListHead;
            unusedListHead = newNode;
        } else {
            next = prev.getUnusedNext();
            prev.setUnusedNext(newNode);
            newNode.setUnusedPrevious(prev);
        }

        if (next.isNonNull()) {
            next.setUnusedPrevious(newNode);
            newNode.setUnusedNext(next);
        }
        unusedListCount++;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void removeFromUnusedList(FreeListNode node) {
        assert node.isNonNull();

        FreeListNode next = node.getUnusedNext();
        FreeListNode prev = node.getUnusedPrevious();
        if (next.isNonNull()) {
            next.setUnusedPrevious(prev);
        }
        if (node == unusedListHead) {
            unusedListHead = next;
        } else {
            prev.setUnusedNext(next);
        }
        unusedListCount--;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void addToAllocList(FreeListNode newNode, FreeListNode prev) {
        assert newNode.isNonNull();
        assert newNode.getAllocNext().isNull();
        assert isAllocatable(newNode.getSize());

        if (prev.isNull()) {
            newNode.setAllocNext(allocListHead);
            allocListHead = newNode;
        } else {
            newNode.setAllocNext(prev.getAllocNext());
            prev.setAllocNext(newNode);
        }
        allocListCount++;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void removeFromAllocList(FreeListNode node, FreeListNode prevNode) {
        assert node.isNonNull();

        if (node == allocListHead) {
            assert prevNode.isNull();
            allocListHead = node.getAllocNext();
        } else {
            prevNode.setAllocNext(node.getAllocNext());
        }
        node.setAllocNext(nullPointer());
        allocListCount--;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void freeAlignedChunk(PointerBase start, UnsignedWord nbytes, UnsignedWord alignment) {
        if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            NativeMemoryTracking.singleton().trackUncommit(nbytes, NmtCategory.JavaHeap);
        }
        freeInHeapAddressSpace((Pointer) start, nbytes);
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void freeUnalignedChunk(PointerBase start, UnsignedWord nbytes) {
        if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            NativeMemoryTracking.singleton().trackUncommit(nbytes, NmtCategory.JavaHeap);
        }
        freeInHeapAddressSpace((Pointer) start, nbytes);
    }

    @Uninterruptible(reason = "Entering a safepoint in this code can deadlock garbage collection.")
    protected void freeInHeapAddressSpace(Pointer start, UnsignedWord nbytes) {
        assert start.isNonNull();
        assert nbytes.aboveThan(0);

        lock.lockNoTransition();
        try {
            UnsignedWord pageSize = getGranularity();
            Pointer mapBegin = roundDown(start, pageSize);

            /* Find adjacent allocatable free blocks. */
            FreeListNode allocPrevious = nullPointer();
            FreeListNode allocNext = allocListHead;
            while (allocNext.isNonNull() && allocNext.getStart().belowThan(mapBegin)) {
                allocPrevious = allocNext;
                allocNext = allocNext.getAllocNext();
            }

            /* Find adjacent unused blocks. */
            FreeListNode unusedPrevious = allocPrevious;
            FreeListNode unusedNext = unusedPrevious.isNull() ? unusedListHead : unusedPrevious.getUnusedNext();
            while (unusedNext.isNonNull() && unusedNext.getStart().belowThan(mapBegin)) {
                unusedPrevious = unusedNext;
                unusedNext = unusedNext.getUnusedNext();
            }

            Pointer mapEnd = roundUp(start.add(nbytes), pageSize);
            UnsignedWord alignedSize = mapEnd.subtract(mapBegin);
            assert alignedSize.aboveOrEqual(nbytes);
            assert unusedPrevious.isNull() || mapBegin.aboveOrEqual(getNodeEnd(unusedPrevious));
            assert unusedNext.isNull() || mapEnd.belowOrEqual(unusedNext.getStart());

            /*
             * Always try to add the freed memory to adjacent unused memory (i.e., by increasing the
             * bounds of an existing node). If there is no adjacent unused memory, we need to create
             * a new node.
             */
            FreeListNode container;
            boolean adjacentPredecessor = unusedPrevious.isNonNull() && getNodeEnd(unusedPrevious).equal(mapBegin);
            boolean adjacentSuccessor = unusedNext.isNonNull() && mapEnd.equal(unusedNext.getStart());

            if (adjacentPredecessor) {
                /* Add the freed memory to the predecessor. */
                increaseBounds(unusedPrevious, mapBegin, alignedSize);
                container = unusedPrevious;

                if (adjacentSuccessor) {
                    /* Merge the successor with the predecessor and remove the successor. */
                    mergeNodes(container, unusedNext);

                    if (allocNext == unusedNext) {
                        allocNext = allocNext.getAllocNext();
                        removeFromAllocList(unusedNext, allocPrevious);
                    }

                    removeFromUnusedList(unusedNext);
                    freeNode(unusedNext);
                    unusedNext = Word.nullPointer();
                }
            } else if (adjacentSuccessor) {
                /* Add the freed memory to the successor node. */
                increaseBounds(unusedNext, mapBegin, alignedSize);
                container = unusedNext;
            } else {
                /* Create a new node for the freed memory because the adjacent memory is used. */
                FreeListNode node = allocNode(mapBegin, alignedSize);
                addToUnusedList(node, unusedPrevious);
                container = node;
            }

            uncommit(container, mapBegin, alignedSize);

            /* Insert merged or created node into allocatables list if necessary. */
            if (isAllocatable(container.getSize()) && container != allocPrevious && container != allocNext) {
                addToAllocList(container, allocPrevious);
            }
        } finally {
            lock.unlock();
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected void uncommit(@SuppressWarnings("unused") FreeListNode node, Pointer mapBegin, UnsignedWord mappingSize) {
        if (VirtualMemoryProvider.get().uncommit(mapBegin, mappingSize) != 0) {
            throw reportUncommitFailed(mapBegin, mappingSize);
        }
    }

    @Uninterruptible(reason = "Switch to interruptible code for error reporting.", calleeMustBe = false)
    private static RuntimeException reportUncommitFailed(Pointer mapBegin, UnsignedWord mappingSize) {
        throw reportUncommitFailedInterruptibly(mapBegin, mappingSize);
    }

    private static RuntimeException reportUncommitFailedInterruptibly(Pointer mapBegin, UnsignedWord mappingSize) {
        Log.log().string("Uncommitting ").unsigned(mappingSize).string(" bytes of unused memory at ").hex(mapBegin).string(" failed.").newline();
        throw VMError.shouldNotReachHere("Uncommitting memory failed.");
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected void mergeNodes(FreeListNode target, FreeListNode obsolete) {
        increaseBounds(target, obsolete.getStart(), obsolete.getSize());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private void increaseBounds(FreeListNode node, Pointer otherStart, UnsignedWord otherSize) {
        assert getNodeEnd(node).equal(otherStart) || otherStart.add(otherSize).equal(node.getStart()) : "must be adjacent";
        assert UnsignedUtils.isAMultiple(otherSize, getGranularity());
        assert otherSize.belowOrEqual(reservedAddressSpaceSize);

        Pointer newStart = PointerUtils.min(node.getStart(), otherStart);
        UnsignedWord newSize = node.getSize().add(otherSize);
        setBounds(node, newStart, newSize);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected void trimBounds(FreeListNode fit, Pointer newStart, UnsignedWord newSize) {
        assert newSize.belowOrEqual(reservedAddressSpaceSize);
        assert fit.getStart().equal(newStart) && newSize.belowThan(fit.getSize()) ||
                        fit.getStart().belowThan(newStart) && getNodeEnd(fit).equal(newStart.add(newSize));

        setBounds(fit, newStart, newSize);
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called by the GC.")
    public void beforeGarbageCollection() {
        assert VMOperation.isGCInProgress() : "may only be called by the GC";
        assert !lock.hasOwner() : "Must not be locked -- is mutator code holding the lock?";
        if (SubstrateGCOptions.VerifyHeap.getValue()) {
            verifyFreeList();
        }
    }

    @Override
    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Called by the GC.")
    public void uncommitUnusedMemory() {
        assert VMOperation.isGCInProgress() : "may only be called by the GC";
        assert !lock.hasOwner() : "Must not be locked";
        uncommitUnusedMemory0();
    }

    protected void uncommitUnusedMemory0() {
        if (SubstrateGCOptions.VerifyHeap.getValue()) {
            verifyFreeList();
        }
    }

    protected void verifyFreeList() {
        int unusedCount = 0;
        FreeListNode unusedPrevious = nullPointer();
        for (FreeListNode unused = unusedListHead; unused.isNonNull(); unused = unused.getUnusedNext()) {
            guarantee(unused.getUnusedPrevious().equal(unusedPrevious), "Unused list previous-next node linkage must be consistent");
            guarantee(unusedPrevious.isNull() || unused.getStart().aboveThan(getNodeEnd(unusedPrevious)), "Unused blocks must not be adjacent or overlapping");
            guarantee(isInAllocList(unused) == isAllocatable(unused.getSize()), "Allocatable blocks must be in the alloc list");
            unusedCount++;
            unusedPrevious = unused;
        }
        guarantee(unusedCount == unusedListCount, "Number of unused list nodes must match recorded count");

        int unusedReverseCounted = 0;
        if (unusedPrevious.isNonNull()) { // iterate in reverse
            FreeListNode unusedNext = unusedPrevious;
            unusedReverseCounted++;
            for (FreeListNode unused = unusedNext.getUnusedPrevious(); unused.isNonNull(); unused = unused.getUnusedPrevious()) {
                guarantee(unused.getUnusedNext().equal(unusedNext), "Unused list previous-next node linkage must be consistent");
                guarantee(unusedNext.getStart().aboveThan(getNodeEnd(unused)), "Unused blocks must not be adjacent or overlapping");
                unusedReverseCounted++;
                unusedNext = unused;
            }
            guarantee(unusedNext == unusedListHead, "Unused list reverse iteration must terminate at list head");
        }
        guarantee(unusedCount == unusedReverseCounted, "Number of unused list nodes must be the same for forward and reverse iteration");

        int allocCount = 0;
        for (FreeListNode previous = nullPointer(), alloc = allocListHead; alloc.isNonNull(); previous = alloc, alloc = alloc.getAllocNext()) {
            guarantee(previous.isNull() || alloc.getStart().aboveThan(getNodeEnd(previous)), "Allocatable blocks must not be adjacent or overlapping");
            guarantee(isAllocatable(alloc.getSize()), "Allocatable blocks must satisfy minimum allocatable size");
            allocCount++;
        }

        guarantee(allocListHead.isNull() || unusedListHead.equal(allocListHead) || getNodeEnd(unusedListHead).belowOrEqual(allocListHead.getStart()),
                        "First unused block must start before first allocatable block, or be allocatable itself");
        guarantee(allocCount <= unusedCount, "Allocation list must not be longer than unused list");
        guarantee(allocCount == allocListCount, "Number of allocation list nodes must match recorded count");
    }

    private boolean isInAllocList(FreeListNode node) {
        FreeListNode alloc = allocListHead;
        while (alloc.isNonNull() && alloc.getStart().belowOrEqual(node.getStart())) {
            if (alloc.equal(node)) {
                return true;
            }
            alloc = alloc.getAllocNext();
        }
        return false;
    }

    @Override
    public UnsignedWord getReservedAddressSpaceSize() {
        return reservedAddressSpaceSize;
    }

    /** Keeps track of unused memory. */
    @RawStructure
    protected interface FreeListNode extends PointerBase {
        @RawField
        Pointer getStart();

        @RawField
        void setStart(Pointer start);

        @RawField
        UnsignedWord getSize();

        @RawField
        void setSize(UnsignedWord size);

        @RawField
        FreeListNode getAllocNext();

        @RawField
        void setAllocNext(FreeListNode next);

        @RawField
        FreeListNode getUnusedPrevious();

        @RawField
        void setUnusedPrevious(FreeListNode unusedPrevious);

        @RawField
        FreeListNode getUnusedNext();

        @RawField
        void setUnusedNext(FreeListNode unusedNext);
    }
}
