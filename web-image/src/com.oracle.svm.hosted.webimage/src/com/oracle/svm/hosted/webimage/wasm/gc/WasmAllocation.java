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

package com.oracle.svm.hosted.webimage.wasm.gc;

import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.EXTREMELY_SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;
import static jdk.graal.compiler.word.Word.nullPointer;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.AlwaysInline;
import com.oracle.svm.core.FrameAccess;
import com.oracle.svm.core.JavaMemoryUtil;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatArrayNode;
import com.oracle.svm.core.genscavenge.graal.nodes.FormatObjectNode;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.heap.RestrictHeapAccess;
import com.oracle.svm.core.heap.RestrictHeapAccess.Access;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.LayoutEncoding;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmTrapNode;
import com.oracle.svm.webimage.platform.WebImageWasmLMPlatform;
import com.oracle.svm.webimage.wasmgc.annotation.WasmExport;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.replacements.AllocationSnippets.FillContent;
import jdk.graal.compiler.word.Word;

/**
 * Simple allocator for the WASM backend using an implicit list of free and allocated blocks as well
 * as an explicit free list.
 * <p>
 * The entire allocator region is treated as a set of aligned 8-byte words and all allocations are
 * at least 8-byte aligned. The region is made up of a set of contiguous blocks of memory, each
 * either free or allocated. Each block starts with an 8-byte header containing the size of the
 * block (including header), whether it is allocated and whether the block contains a Java object.
 * <p>
 * The size of the block acts as an implicit pointer to the next block (just add the size to the
 * address of the block header) and allows for visiting all blocks.
 *
 * <pre>
 *     |<-        'Size' bytes (Outer size)         ->|
 *     +------+---------+----------------------------+
 *     |  Block Header  |            Data            |
 *     | Size |  Flags  |             ..             |
 *     +------+---------+----------------------------+
 *     |      | 3 bits  |<-      (Inner size)      ->|
 *     |<-  8 bytes   ->|
 * </pre>
 *
 * The three least significant bits of the header contain all information besides the size itself
 * (since the size is divisible by 8, these three bits are always 0 and can be overloaded with
 * flags):
 *
 * <ul>
 * <li>Bit 0: Allocated bit (1 = allocated, 0 = free)</li>
 * <li>Bit 1: Object bit (1 = Java Object, 0 = some other kind of allocation)</li>
 * <li>Bit 2: Unused</li>
 * </ul>
 *
 * The explicit free list is encoded as a linked list where each free block stores a pointer to
 * another free block as its first data word. Free blocks are added to the front of this list by
 * pointing {@link FreeList#firstFree} to the new free block. See {@link FreeList}
 */
public final class WasmAllocation {

    public static class Options {
        @Option(help = "Enable (time-consuming) runtime verifications of the allocator state.")//
        public static final HostedOptionKey<Boolean> VerifyAllocations = new HostedOptionKey<>(false);

        /**
         * The option is turned on by default if we verify allocations.
         */
        @Option(help = "Make sure free memory is cleared.")//
        public static final HostedOptionKey<Boolean> ClearFreeMemory = new HostedOptionKey<>(false) {
            @Override
            public Boolean getValueOrDefault(UnmodifiableEconomicMap<OptionKey<?>, Object> values) {
                if (!values.containsKey(this)) {
                    return VerifyAllocations.getValueOrDefault(values);
                }
                return super.getValueOrDefault(values);
            }

            @Override
            public Boolean getValue(OptionValues values) {
                return getValueOrDefault(values.getMap());
            }
        };
    }

    /**
     * Tracks some usage statistics of the allocator region.
     */
    static class Statistics {

        /**
         * Number of bytes taken up by allocated objects (including the block header).
         */
        static long objectSize = 0;

        /**
         * Number of bytes in free blocks (including block header).
         */
        static long freeSize = 0;

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        static void removeBlock(BlockHeader header) {
            assert header.getAllocated();

            long size = header.getSize().rawValue();

            if (header.getIsObject()) {
                objectSize -= size;
            }

            freeSize += size;
        }
    }

    private static final OutOfMemoryError OUT_OF_MEMORY_ERROR = new OutOfMemoryError("Could not allocate memory");

    private static final UnsignedWord MASK_HEADER_BITS = Word.unsigned(0b111);
    private static final UnsignedWord MASK_IS_ALLOCATED_BIT = Word.unsigned(0b001);
    private static final UnsignedWord MASK_IS_OBJECT_BIT = Word.unsigned(0b010);
    private static final UnsignedWord CLEAR_HEADER_BITS = MASK_HEADER_BITS.not();

    /**
     * Byte-alignment of headers and block.
     */
    private static final UnsignedWord ALIGNMENT = Word.unsigned(8);

    private static final UnsignedWord HEADER_SIZE = align(ALIGNMENT);

    /**
     * Minimal inner size a block needs to have (allocated or not).
     * <p>
     * Free blocks that are any smaller do not have space to store the free list metadata and
     * allocated blocks will run into that issue as soon as they're freed.
     *
     * @see FreeList#MIN_FREE_BLOCK_SIZE
     */
    private static final UnsignedWord MIN_INNER_SIZE = getInnerSize(FreeList.MIN_FREE_BLOCK_SIZE);

    /**
     * Stack allocatable data structure containing the information encoded in the block header in an
     * easily accessible form.
     * <p>
     * This saves us from having to either constantly decode information from the block header or
     * passing around all block information as multiple values.
     */
    @RawStructure
    private interface BlockHeader extends PointerBase {
        @RawField
        UnsignedWord getSize();

        @RawField
        void setSize(UnsignedWord size);

        @RawField
        boolean getAllocated();

        @RawField
        void setAllocated(boolean isAllocated);

        @RawField
        boolean getIsObject();

        @RawField
        void setIsObject(boolean isObject);
    }

    @Fold
    public static boolean shouldVerify() {
        return Options.VerifyAllocations.getValue();
    }

    /**
     * Returns the byte used to fill empty memory.
     * <p>
     * If allocations are verified, we use a special fill byte for verification, otherwise we use
     * zero.
     */
    @Fold
    public static byte getFillByte() {
        assert Options.ClearFreeMemory.getValue();
        return (byte) (shouldVerify() ? 0xCD : 0x00);
    }

    @WasmExport(value = "malloc", comment = "Allocate unmanaged memory")
    @Platforms(WebImageWasmLMPlatform.class)
    public static Pointer malloc(UnsignedWord numBytes) {
        return doMalloc(numBytes);
    }

    @WasmExport(value = "free", comment = "Free memory allocated with malloc")
    @Platforms(WebImageWasmLMPlatform.class)
    public static void free(Pointer ptr) {
        doFree(ptr);
    }

    @Uninterruptible(reason = "Runs while module is constructed", callerMustBe = true)
    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Allocator not yet initialized")
    static void initialize() {
        Pointer base = MemoryLayout.getAllocatorBase();
        Pointer end = MemoryLayout.getAllocatorTop();
        VMError.guarantee(base == end, "Allocator must start out completely empty");

        // Initialize the allocator with at least a page
        if (growAllocatorRegion(MemoryLayout.pageSize()).isNull()) {
            WasmTrapNode.trap();
        }
    }

    /**
     * Grows the allocator region by at least the given number of bytes and adds it to the list of
     * blocks.
     *
     * @return The base of the created block, or a null pointer if it failed
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Allocator may be in inconsistent state")
    static Pointer growAllocatorRegion(UnsignedWord numBytes) {
        Pointer top = MemoryLayout.getAllocatorTop();

        // Also align the new top to the page size
        Pointer newTop = MemoryLayout.ensureAllocatorRegion((Pointer) MemoryLayout.alignToPage(top.add(numBytes)));
        if (newTop.isNull()) {
            return nullPointer();
        }

        UnsignedWord size = newTop.subtract(top);

        Statistics.freeSize += size.rawValue();
        // Create empty block in newly grown allocator region
        writeFreeBlockHeader(top, size);
        FreeList.add(top);

        return top;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void verifyBlockHeader(UnsignedWord header) {
        if (shouldVerify()) {
            doVerifyBlockHeader(header);
        }
    }

    @Uninterruptible(reason = "Executes interruptible code if the validation fails.", callerMustBe = true, calleeMustBe = false)
    private static void doVerifyBlockHeader(UnsignedWord header) {
        UnsignedWord size = header.and(CLEAR_HEADER_BITS);

        boolean isAllocated = header.and(MASK_IS_ALLOCATED_BIT).notEqual(0);

        VMError.guarantee(isAligned(size), "Size is not aligned");
        VMError.guarantee(isAligned(getInnerSize(size)), "Inner size is not aligned");

        if (size.aboveThan(MemoryLayout.getAllocatorTop().subtract(MemoryLayout.getAllocatorBase()))) {
            Log.log().string("Block size is larger than allocator region: ").hex(size).newline();
            throw VMError.shouldNotReachHereAtRuntime();
        }

        /*
         * Free and allocated blocks must be at least the size of the minimal free block. If an
         * allocated block does not satisfy this, it will become a too-small free block once freed.
         */
        if (size.belowThan(FreeList.MIN_FREE_BLOCK_SIZE)) {
            Log.log().string("Block size is too small: ").hex(size).string(isAllocated ? " (allocated)" : " (free)")
                            .string(". Must be at least ").unsigned(FreeList.MIN_FREE_BLOCK_SIZE).string("B").newline();
            throw VMError.shouldNotReachHereAtRuntime();
        }

        if (!isAllocated) {
            VMError.guarantee(header.and(MASK_IS_OBJECT_BIT).equal(0), "Free block is marked as object");
        }
    }

    /**
     * Walks the entire list of blocks.
     * <p>
     * Reading each block header verify that block header. Finally, it checks that the last block
     * points to {@link MemoryLayout#getAllocatorTop()}.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static void verifyAllBlocks() {
        if (!shouldVerify()) {
            return;
        }

        FreeList.verifyFreeList();
        Pointer currentBlock = MemoryLayout.getAllocatorBase();

        long objectSize = 0;
        long freeSize = 0;

        while (currentBlock.belowThan(MemoryLayout.getAllocatorTop())) {
            VMError.guarantee(isAligned(currentBlock), "Block is not aligned");
            BlockHeader header = StackValue.get(BlockHeader.class);
            readBlockHeader(currentBlock, header);

            if (header.getAllocated()) {
                if (header.getIsObject()) {
                    objectSize += header.getSize().rawValue();
                }
            } else {
                freeSize += header.getSize().rawValue();
            }

            if (!header.getAllocated() && Options.ClearFreeMemory.getValue()) {
                for (UnsignedWord offset = FreeList.POINTERS_SIZE; offset.belowThan(getInnerSize(header)); offset = offset.add(1)) {
                    VMError.guarantee(getInnerPointer(currentBlock).readByte(offset) == getFillByte(), "Free block does not only contain fill bytes");
                }
            }

            currentBlock = getNextBlock(currentBlock, header);
        }

        VMError.guarantee(currentBlock.equal(MemoryLayout.getAllocatorTop()), "Last block does not point to allocator top");

        VMError.guarantee(objectSize == Statistics.objectSize, "objectSize has wrong value in Statistics");
        VMError.guarantee(freeSize == Statistics.freeSize, "freeSize has wrong value in Statistics");
    }

    @Uninterruptible(reason = "Executes interruptible code.", calleeMustBe = false)
    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Allocator state must not change")
    static void printState() {
        Log.log().string("Allocator state").newline();
        Log.log().string("Free size: ").unsigned(getFreeSize()).newline();
        Log.log().string("Object size: ").unsigned(getObjectSize()).newline();
        Pointer currentBlock = MemoryLayout.getAllocatorBase();
        while (currentBlock.belowThan(MemoryLayout.getAllocatorTop())) {
            BlockHeader header = StackValue.get(BlockHeader.class);
            readBlockHeader(currentBlock, header);

            Log.log().hex(currentBlock).string(" [").string(header.getAllocated() ? "alloc" : " free").string("]");

            if (header.getIsObject()) {
                Object obj = getInnerPointer(currentBlock).toObjectNonNull();
                char c;
                if (WasmObjectHeader.isWhiteObject(obj)) {
                    c = 'W';
                } else if (WasmObjectHeader.isGrayObject(obj)) {
                    c = 'G';
                } else if (WasmObjectHeader.isBlackObject(obj)) {
                    c = 'B';
                } else {
                    throw VMError.shouldNotReachHereUnexpectedInput(obj);
                }

                Log.log().string(" [").character(c).string("] ").object(obj);
            }

            Log.log().string(", size: ").hex(header.getSize()).newline();

            currentBlock = getNextBlock(currentBlock, header);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static long getObjectSize() {
        return Statistics.objectSize;
    }

    public static long getFreeSize() {
        return Statistics.freeSize;
    }

    /**
     * Returns the current size of the heap that can be/is used for object allocation.
     * <p>
     * Ignores non-object allocations.
     */
    public static long getHeapSize() {
        return getObjectSize() + getFreeSize();
    }

    /**
     * Returns the percentage of heap size taken up by objects.
     *
     * @return A value between 0 and 100
     */
    public static int getObjectPercentage() {
        return (int) (getObjectSize() * 100 / getHeapSize());
    }

    /**
     * Checks if the given pointer is the inner pointer to an allocated object block.
     *
     * @param ptr If the pointer points into the allocator region, it must point to a valid interior
     *            block.
     */
    @Uninterruptible(reason = "Reads allocator state")
    public static boolean isObjectPointer(Pointer ptr) {
        if (ptr.belowThan(MemoryLayout.getAllocatorBase()) || ptr.aboveOrEqual(MemoryLayout.getAllocatorTop())) {
            return false;
        }

        Pointer outer = getOuterPointer(ptr);
        BlockHeader header = StackValue.get(BlockHeader.class);
        readBlockHeader(outer, header);

        return header.getAllocated() && header.getIsObject();
    }

    @SuppressWarnings("unchecked")
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static <T extends UnsignedWord> T align(T address) {
        @SuppressWarnings("unchecked")
        T ptr = (T) UnsignedUtils.roundUp(address, ALIGNMENT);
        return ptr;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static boolean isAligned(UnsignedWord address) {
        return UnsignedUtils.isAMultiple(address, ALIGNMENT);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer getNextBlock(Pointer blockBase, BlockHeader header) {
        return getNextBlock(blockBase, header.getSize());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer getNextBlock(Pointer blockBase, UnsignedWord size) {
        return blockBase.add(size);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static Pointer getInnerPointer(Pointer blockBase) {
        return blockBase.add(HEADER_SIZE);
    }

    @Uninterruptible(reason = "Exposes allocator state", callerMustBe = true)
    private static Pointer getOuterPointer(Pointer innerPointer) {
        return innerPointer.subtract(HEADER_SIZE);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord getInnerSize(BlockHeader header) {
        return header.getSize().subtract(HEADER_SIZE);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord getInnerSize(UnsignedWord size) {
        return size.subtract(HEADER_SIZE);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    @AlwaysInline("Allocator Performance")
    private static void readBlockHeader(Pointer blockBase, BlockHeader returnHeader) {
        UnsignedWord header = blockBase.readWord(0);
        verifyBlockHeader(header);

        returnHeader.setSize(header.and(CLEAR_HEADER_BITS));
        returnHeader.setAllocated(header.and(MASK_IS_ALLOCATED_BIT).notEqual(0));
        returnHeader.setIsObject(header.and(MASK_IS_OBJECT_BIT).notEqual(0));
    }

    @Uninterruptible(reason = "Holds uninitialized memory.", callerMustBe = true)
    @AlwaysInline("Allocator performance")
    private static void writeFreeBlockHeader(Pointer blockBase, UnsignedWord size) {
        writeBlockHeader(blockBase, size, false, false);
    }

    @Uninterruptible(reason = "Holds uninitialized memory", callerMustBe = true)
    @AlwaysInline("Allocator performance")
    private static void writeBlockHeader(Pointer blockBase, BlockHeader header) {
        writeBlockHeader(blockBase, header.getSize(), header.getAllocated(), header.getIsObject());
    }

    @Uninterruptible(reason = "Holds uninitialized memory", callerMustBe = true)
    @AlwaysInline("Allocator performance")
    private static void writeBlockHeader(Pointer blockBase, UnsignedWord size, boolean isAllocated, boolean isObject) {
        VMError.guarantee(isAligned(blockBase));
        UnsignedWord header = size;

        if (isAllocated) {
            header = header.or(MASK_IS_ALLOCATED_BIT);
        }
        if (isObject) {
            assert isAllocated;
            header = header.or(MASK_IS_OBJECT_BIT);
        }

        verifyBlockHeader(header);
        blockBase.writeWord(0, header);

        if (!isAllocated && Options.ClearFreeMemory.getValue()) {
            JavaMemoryUtil.fill(
                            getInnerPointer(blockBase).add(FreeList.POINTERS_SIZE),
                            getInnerSize(size).subtract(FreeList.POINTERS_SIZE),
                            getFillByte());
        }
    }

    @Uninterruptible(reason = "Modifies allocator state.", callerMustBe = true)
    private static void markFree(Pointer blockBase) {
        verifyAllBlocks();
        BlockHeader header = StackValue.get(BlockHeader.class);
        readBlockHeader(blockBase, header);
        VMError.guarantee(header.getAllocated(), "Double free");

        Statistics.removeBlock(header);

        writeFreeBlockHeader(blockBase, header.getSize());
        FreeList.add(blockBase);
        verifyAllBlocks();
    }

    @Uninterruptible(reason = "Holds uninitialized memory.", callerMustBe = true)
    private static void markAsObject(Pointer blockBase) {
        verifyAllBlocks();
        BlockHeader header = StackValue.get(BlockHeader.class);
        readBlockHeader(blockBase, header);
        assert header.getAllocated();

        if (!header.getIsObject()) {
            header.setIsObject(true);
            writeBlockHeader(blockBase, header);
            Statistics.objectSize += header.getSize().rawValue();
            verifyAllBlocks();
        }
    }

    @Uninterruptible(reason = "Modifies allocator state.", callerMustBe = true)
    private static void allocateInBlock(Pointer blockBase, UnsignedWord newSize) {
        BlockHeader header = StackValue.get(BlockHeader.class);
        readBlockHeader(blockBase, header);
        allocateInBlock(blockBase, header, newSize);
    }

    @Uninterruptible(reason = "Modifies allocator state.", callerMustBe = true)
    private static void allocateInBlock(Pointer blockBase, BlockHeader header, UnsignedWord newSize) {
        UnsignedWord alignedSize = align(newSize);
        verifyAllBlocks();
        if (shouldVerify()) {
            VMError.guarantee(!header.getAllocated(), "Trying to allocate in non-free block");
            VMError.guarantee(getInnerSize(header).aboveOrEqual(alignedSize), "Trying to allocate in too small block");
            VMError.guarantee(newSize.aboveOrEqual(MIN_INNER_SIZE), "Trying to allocate too small block");
        }

        UnsignedWord spaceFreeAfter = getInnerSize(header).subtract(alignedSize);

        if (shouldVerify()) {
            VMError.guarantee(isAligned(spaceFreeAfter), "Remaining space after block is not aligned");
        }

        /*
         * We split up the free block only if we are left with enough space for a free block.
         */
        boolean shouldSplit = spaceFreeAfter.aboveOrEqual(FreeList.MIN_FREE_BLOCK_SIZE);
        // Shrink previous block if it is split
        UnsignedWord newBlockSize = header.getSize().subtract(shouldSplit ? spaceFreeAfter : Word.zero());
        header.setSize(newBlockSize);
        header.setAllocated(true);

        FreeList.remove(blockBase);
        writeBlockHeader(blockBase, header);
        Statistics.freeSize -= newBlockSize.rawValue();

        if (shouldSplit) {
            // Add a new block directly after
            Pointer nextBlockBase = getNextBlock(blockBase, header);
            writeFreeBlockHeader(nextBlockBase, spaceFreeAfter);
            FreeList.add(nextBlockBase);
        }

        verifyAllBlocks();
    }

    /**
     * Grows the allocator region and allocates memory from there.
     */
    @Uninterruptible(reason = "Modifies allocator state", calleeMustBe = false)
    private static Pointer growMalloc(UnsignedWord numBytes) {
        Pointer newBlockBase = growAllocatorRegion(numBytes.add(HEADER_SIZE));
        if (newBlockBase.isNull()) {
            return nullPointer();
        }

        // Use fresh block to allocate
        allocateInBlock(newBlockBase, numBytes);

        return getInnerPointer(newBlockBase);
    }

    /**
     * Searches for a suitable free block to allocate the given number of bytes.
     *
     * @return The inner pointer of the allocated block or a null pointer if no block was found.
     */
    @Uninterruptible(reason = "Modifies allocator state", mayBeInlined = true)
    @AlwaysInline("Allocator Performance")
    private static Pointer allocateInExistingBlocks(UnsignedWord numBytes) {
        assert numBytes.notEqual(0);

        Pointer current = FreeList.firstFree;

        // Walk all blocks and find a free block with enough space
        while (current.isNonNull()) {
            BlockHeader header = StackValue.get(BlockHeader.class);
            readBlockHeader(current, header);

            assert !header.getAllocated();

            if (getInnerSize(header).aboveOrEqual(numBytes)) {
                allocateInBlock(current, header, numBytes);
                return getInnerPointer(current);
            }

            current = FreeList.getNextFreeBlock(current);
        }

        return nullPointer();
    }

    /**
     * Main allocation logic.
     * <p>
     * Searches for a suitable free block. If none is found, triggers a collection, if after that no
     * suitable block is found, grows the heap until enough space is available.
     *
     * @return The inner pointer of the allocated block or a null pointer if the allocator ran out
     *         of memory.
     */
    @Uninterruptible(reason = "Modifies allocator state")
    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    public static Pointer doMalloc(UnsignedWord numBytes) {
        if (probability(EXTREMELY_SLOW_PATH_PROBABILITY, numBytes.equal(0))) {
            return nullPointer();
        }

        // Increase allocation to minimal size
        UnsignedWord paddedSize = UnsignedUtils.max(MIN_INNER_SIZE, numBytes);

        verifyAllBlocks();

        Pointer ptr = allocateInExistingBlocks(paddedSize);
        if (ptr.isNonNull()) {
            return ptr;
        }

        // If nothing was found trigger a collection (which may grow the heap).
        WasmLMGC.getGC().collect(WasmGCCause.OnAllocation);

        // Search for a suitable block again.
        ptr = allocateInExistingBlocks(paddedSize);
        if (ptr.isNonNull()) {
            return ptr;
        }

        // If we reach here, it means we couldn't find a suitable free block, even after collection
        return growMalloc(paddedSize);
    }

    /**
     * Basic implementation of {@code realloc}.
     * <p>
     * Will always free old pointer and allocate a new memory segment.
     */
    @Uninterruptible(reason = "Modifies allocator state")
    public static Pointer doRealloc(Pointer innerPtr, UnsignedWord numBytes) {
        try {
            if (innerPtr.isNull()) {
                return doMalloc(numBytes);
            }

            if (numBytes.equal(0)) {
                return Word.nullPointer();
            }

            Pointer outerPtr = getOuterPointer(innerPtr);
            BlockHeader header = StackValue.get(BlockHeader.class);
            readBlockHeader(outerPtr, header);
            UnsignedWord currentSize = getInnerSize(header);

            Pointer newPtr = doMalloc(numBytes);

            UnsignedWord copiedSize = UnsignedUtils.min(currentSize, numBytes);
            UnmanagedMemoryUtil.copy(innerPtr, newPtr, copiedSize);

            return newPtr;
        } finally {
            doFree(innerPtr);
        }
    }

    @Uninterruptible(reason = "Modifies allocator state")
    public static void doFree(Pointer innerPtr) {
        if (innerPtr.isNull()) {
            return;
        }

        logicalFree(innerPtr);

        Pointer blockBase = getOuterPointer(innerPtr);
        BlockHeader header = StackValue.get(BlockHeader.class);
        readBlockHeader(blockBase, header);

        coalesceAt(blockBase, header);
    }

    /**
     * Marks the given pointer as freed, but does not modify any of the lists. Any active visitors
     * remain valid.
     */
    @Uninterruptible(reason = "Modifies allocator state")
    public static void logicalFree(Pointer innerPtr) {
        if (innerPtr.isNull()) {
            return;
        }

        markFree(getOuterPointer(innerPtr));
    }

    /**
     * Coalesces adjacent free blocks.
     */
    @Uninterruptible(reason = "Modifies allocator state")
    public static void coalesce() {
        Pointer currentBlock = MemoryLayout.getAllocatorBase();

        Pointer minFreeBlock = MemoryLayout.getAllocatorTop();

        while (currentBlock.belowThan(MemoryLayout.getAllocatorTop())) {
            BlockHeader header = StackValue.get(BlockHeader.class);
            readBlockHeader(currentBlock, header);

            if (!header.getAllocated()) {
                coalesceAt(currentBlock, header);
                minFreeBlock = (Pointer) UnsignedUtils.min(minFreeBlock, currentBlock);
            }

            currentBlock = getNextBlock(currentBlock, header);
        }

        verifyAllBlocks();
    }

    /**
     * Coalesces the given free block with subsequent free blocks.
     *
     * @param header The parsed block header. The method updates it with the new size.
     */
    @Uninterruptible(reason = "Modifies allocator state")
    public static void coalesceAt(Pointer blockBase, BlockHeader header) {
        assert !header.getAllocated();

        UnsignedWord newSize = header.getSize();
        Pointer currentBlock = getNextBlock(blockBase, header);

        while (currentBlock.belowThan(MemoryLayout.getAllocatorTop())) {
            BlockHeader currentHeader = StackValue.get(BlockHeader.class);
            readBlockHeader(currentBlock, currentHeader);

            if (currentHeader.getAllocated()) {
                break;
            }

            FreeList.remove(currentBlock);

            newSize = newSize.add(currentHeader.getSize());

            currentBlock = getNextBlock(currentBlock, currentHeader);
        }

        if (newSize.aboveThan(header.getSize())) {
            header.setSize(newSize);
            writeBlockHeader(blockBase, header);
        }
    }

    public static void walkObjects(ObjectVisitor visitor) {
        Pointer currentBlock = MemoryLayout.getAllocatorBase();
        while (currentBlock.belowThan(MemoryLayout.getAllocatorTop())) {
            BlockHeader header = StackValue.get(BlockHeader.class);
            readBlockHeader(currentBlock, header);

            if (header.getIsObject()) {
                visitor.visitObject(getInnerPointer(currentBlock).toObjectNonNull());
            }

            currentBlock = getNextBlock(currentBlock, header);
        }
    }

    /**
     * Utility methods to maintain the explicit free list.
     * <p>
     * The free list is a doubly linked list where the first data word of each free block points to
     * the next free block and the second data word points to the previous free block.
     */
    private static final class FreeList {

        /**
         * Pointer to the first free block in the free list.
         */
        private static Pointer firstFree = nullPointer();

        /**
         * The size taken up by the two pointers for the free list.
         */
        private static final UnsignedWord POINTERS_SIZE = Word.unsigned(2 * FrameAccess.wordSize());

        /**
         * The minimum size of a free block (header and space for the two pointers).
         */
        private static final UnsignedWord MIN_FREE_BLOCK_SIZE = HEADER_SIZE.add(POINTERS_SIZE);

        /**
         * @return The next free block that the given free block points to. A null pointer if the
         *         given block is the last free block.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        static Pointer getNextFreeBlock(Pointer freeBlock) {
            return getInnerPointer(freeBlock).readWord(0);
        }

        /**
         * @return The previous free block that the given free block points to. A null pointer if
         *         the given block is the first free block.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        static Pointer getPrevFreeBlock(Pointer freeBlock) {
            return getInnerPointer(freeBlock).readWord(FrameAccess.wordSize());
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        static void setNext(Pointer freeBlock, Pointer next) {
            getInnerPointer(freeBlock).writeWord(0, next);
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        static void setPrev(Pointer freeBlock, Pointer prev) {
            getInnerPointer(freeBlock).writeWord(FrameAccess.wordSize(), prev);
        }

        /**
         * Removes the given free block from the list. The block must already be in the list and
         * have valid next and prev pointers.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @AlwaysInline("Allocator Performance")
        static void remove(Pointer freeBlock) {
            Pointer prev = getPrevFreeBlock(freeBlock);
            Pointer next = getNextFreeBlock(freeBlock);

            if (prev.isNull()) {
                // This is the first block
                firstFree = next;
            } else {
                setNext(prev, next);
            }

            /*
             * If this is not the last block, update the prev pointer in the next block.
             */
            if (next.isNonNull()) {
                setPrev(next, prev);
            }
        }

        /**
         * Adds the given free block to the list. The block must not already be in the list.
         */
        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        @AlwaysInline("Allocator Performance")
        static void add(Pointer freeBlock) {
            Pointer next = firstFree;

            setNext(freeBlock, next);
            setPrev(freeBlock, nullPointer());

            if (next.isNonNull()) {
                setPrev(next, freeBlock);
            }

            firstFree = freeBlock;
        }

        @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
        static void verifyFreeList() {
            if (!shouldVerify()) {
                return;
            }

            Pointer current = firstFree;

            while (current.isNonNull()) {
                Pointer prev = getPrevFreeBlock(current);
                Pointer next = getNextFreeBlock(current);

                BlockHeader blockHeader = StackValue.get(BlockHeader.class);
                readBlockHeader(current, blockHeader);

                VMError.guarantee(!blockHeader.getAllocated(), "Found allocated block in free list");

                if (prev.isNull()) {
                    VMError.guarantee(firstFree.equal(current), "Block with null prev pointer is not the first block");
                } else {
                    VMError.guarantee(getNextFreeBlock(prev).equal(current), "Broken double link to previous block");
                }

                if (next.isNonNull()) {
                    VMError.guarantee(getPrevFreeBlock(next).equal(current), "Broken double link to next block");
                }

                current = getNextFreeBlock(current);
            }
        }
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static Object newInstance(Word objectHeader) {
        DynamicHub hub = WasmObjectHeader.getObjectHeaderImpl().dynamicHubFromObjectHeader(objectHeader);
        return newInstanceWithoutAllocating(hub);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    private static Object newArray(Word objectHeader, int length) {
        return newArrayLikeObject(objectHeader, length);
    }

    private static Object newArrayLikeObject(Word objectHeader, int length) {
        if (length < 0) { // must be done before allocation-restricted code
            throw new NegativeArraySizeException();
        }

        return newArrayLikeObject0(objectHeader, length);
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    private static Object newArrayLikeObject0(Word objectHeader, int length) {
        DynamicHub hub = WasmObjectHeader.getObjectHeaderImpl().dynamicHubFromObjectHeader(objectHeader);

        WasmHeap.exitIfAllocationDisallowed("WasmAllocation.newArrayLikeObject0", DynamicHub.toClass(hub).getName());
        WasmLMGC.getGC().maybeCollectOnAllocation();

        return allocateArrayLikeObject(hub, length);
    }

    @Uninterruptible(reason = "Holds uninitialized memory")
    private static Object allocateArrayLikeObject(DynamicHub hub, int length) {
        UnsignedWord size = LayoutEncoding.getArrayAllocationSize(hub.getLayoutEncoding(), length);
        Pointer memory = allocateObject(size);
        return formatArrayLikeObject(memory, hub, length, false, FillContent.WITH_ZEROES);
    }

    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    private static Object newInstanceWithoutAllocating(DynamicHub hub) {
        WasmHeap.exitIfAllocationDisallowed("WasmAllocation.newInstanceWithoutAllocating", DynamicHub.toClass(hub).getName());
        WasmLMGC.getGC().maybeCollectOnAllocation();

        return allocateInstance(hub);
    }

    @Uninterruptible(reason = "Holds uninitialized memory")
    private static Object allocateInstance(DynamicHub hub) {
        UnsignedWord size = LayoutEncoding.getPureInstanceAllocationSize(hub.getLayoutEncoding());
        Pointer memory = allocateObject(size);
        return FormatObjectNode.formatObject(memory, DynamicHub.toClass(hub), false, FillContent.WITH_ZEROES, true);
    }

    @RestrictHeapAccess(access = Access.NO_ALLOCATION, reason = "Must not allocate in the implementation of allocation.")
    @Uninterruptible(reason = "Holds uninitialized memory")
    private static Pointer allocateObject(UnsignedWord size) {
        Pointer ptr = doMalloc(size);

        if (ptr.isNull()) {
            throw OUT_OF_MEMORY_ERROR;
        }

        markAsObject(getOuterPointer(ptr));
        return ptr;
    }

    @Uninterruptible(reason = "Holds uninitialized memory")
    private static Object formatArrayLikeObject(Pointer memory, DynamicHub hub, int length, boolean unaligned, FillContent fillContent) {
        Class<?> clazz = DynamicHub.toClass(hub);
        return FormatArrayNode.formatArray(memory, clazz, length, false, unaligned, fillContent, true);
    }

}
