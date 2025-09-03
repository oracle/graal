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

/** GraalVM allows unsafe access to byte-addressable memory.
 *  This file contains the wimalloc implementation, which handles off-heap memory in GraalVM.
 *  The off-heap memory is emulated with a large JS ArrayBuffer, and managed with the wimalloc algorithm.
 *
 *
 *  ## High-level description
 *
 *  The allocator maintains a single contiguous region of memory, and serves allocation requests from this region.
 *  This region is separated into contiguous, non-overlapping, fixed-size subregions called pages.
 *
 *  The allocator distinguishes between two types of requests: small and large.
 *  A large request of size N is served by identifying a contiguous interval of pages, larger than N,
 *  that were not previously used to serve an allocation request,
 *  and then associating a subinterval of M > N bytes to the request, where M is a multiple of the page size.
 *  This way, a large request tends to divide an interval of pages into smaller intervals.
 *
 *  A small request of size N is served by determining the smallest size-rank R for N,
 *  where the size associated with the rank R must be greater or equal to N.
 *  For each size-rank R, there is a set of free-lists of nodes that serve requests for that size-rank.
 *  A free-list for R is therefore identified (ensuring that one exists), and a node removed from the list.
 *
 *  Memory blocks that serve large requests are called *segments*.
 *  Memory blocks that serve small requests are called *fragments*.
 *  The separation between small and large requests is done mainly to speed up small requests.
 *  An additional reason is to decrease external fragmentation by keeping similar sizes closer together.
 *
 *  The allocator has sufficient information to determine whether a given address is
 *  the start of a memory block that had previously been allocated.
 *  To deallocate a memory block, the allocator first decides if that block is small or large.
 *  If it is large, then the interval of pages associated with the block is marked as free,
 *  and the block is merged with adjacent (immediately preceding or following) free regions, if any.
 *  If the block is small, then the block is put at the head of the corresponding free-list.
 *
 *  If an allocation request cannot be satisfied, then the allocator will resize the underlying memory region.
 *  There is an upper limit beyond which the memory cannot be resized -- if this limit is reached,
 *  and a free memory block cannot be found, then the allocator returns 0.
 *
 *  In addition to the memory, the heap has some auxiliary data structures that assist in searches.
 *
 *
 *  ## Visualization, terminology and data structures
 *
 *  The state of the memory heap is illustrated with the following example:
 *   __________________________________________________________________________   _ _ _ _   ________________________
 *  | Page 0         | Page 1         | Page 2         | Page 3|__|__|__|      \           /       | Page N-1       |
 *  |   |            |   |            |                |   |   |__|__|__|      /           \       |   |        |   |
 *  | Left sentinel  | h |            |                | h | b |__free__|      \           /       | Right sentinel |
 *  |   |            | e |            |                | e | u |__list__|      /  _ _ _ _  \       |   |        |   |
 *  | h |            | a |            |                | a | c |__|__|__|      \           /       | h | Alloc  |   |
 *  | e |            | d |            |                | d | k |__|__|__|      /           \       | e | bitset |   |
 *  | a |            | e |            |                | e | e |__|__|__|      \           /       | a |        |   |
 *  | d |            | r |            |                | r | t |__|__|__|      /  _ _ _ _  \       | d | x..x.. |   |
 *  | e |            |   |            |                |   |   |__|__|__|      \           /       | e | ...... |   |
 *  | r |            |   |            |                |   | h |__|__|__|      /           \       | r | ...... |   |
 *  |   |            |   |            |                |   | e |__|__|__|      \           /       |   | ...... |   |
 *  |   |            |   |            |                |   | a |__|__|__|      /  _ _ _ _  \       |   | ...... |   |
 *  |   |            |   |            |                |   | d |__|__|__|      \           /       |   | ...... |   |
 *  |   |            |   |            |                |   | e |__|__|__|      /           \       |   | ...... |   |
 *  |   |            |   |            |                |   | r |__|__|__|      \           /       |   | .....x |   |
 *  |___|____________|___|____________|________________|___|___|__|__|__|______/  _ _ _ _  \_______|___|________|___|
 *  <---allocated---><-------------free---------------><---allocated--->                           <---allocated--->
 *       segment                  segment                   segment                                     segment
 *     (sentinel)                                          (bucket)                                   (sentinel)
 *
 *  We establish the following nomenclature:
 *
 *  - memory -- a contiguous sequence of addressable bytes, from which memory blocks are allocated
 *  - heap -- memory and the set of data structures that assist in serving allocation and deallocation requests
 *  - page -- a fixed-size sequence of bytes -- the memory size is always a multiple of the page size
 *  - segment -- a contiguous sequence of pages -- a segment can be either free or allocated
 *  - segment header -- a short sequence of bytes at the beginning of every segment, which holds housekeeping data
 *  - left sentinel -- a special 1-page segment at the beginning of the memory, which is always allocated
 *  - right sentinel -- a special segment that occupies several pages at the end of memory, always allocated
 *  - allocation bitset -- a bitset inside the right sentinel -- a bit is set iff the corresponding memory page is
 *    the start of an allocated segment (note: not set for a page that is not the first page of an allocated segment)
 *  - segment list -- a doubly-linked list of all the segments (starting with the left sentinel,
 *    up to the right sentinel), ordered by the segment start addresses
 *  - free-segment list -- a doubly linked list of all the free segments (starting with the left sentinel,
 *    and ending with the right sentinel -- even though sentinels are allocated), ordered by segment start addresses
 *  - bucket segment -- a segment that is allocated by the allocator to store a bucket (see next)
 *  - bucket -- a memory region (stored inside a segment) from which small requests are served -- each bucket
 *    serves small requests of one specific size class
 *  - bucket header -- a short sequence of bytes at the beginning of every bucket, which holds housekeeping data
 *  - bucket rank -- the number that represents the size class of the bucket (ranks start from 0)
 *  - bucket free-list -- free-list of memory blocks within the bucket, comes after the bucket header
 *  - fragment -- a memory block in the bucket free list
 *  - bucket allocation bitset -- part of the bucket header that tracks which fragments are allocated
 *  - bucket fragment count -- the maximum number of fragments that fit into the bucket of a specific size class
 *  - bucket free count -- the number of non-allocated fragments in the bucket
 *  - free-bucket list -- doubly linked list of buckets with non-zero free count (one list for each size class)
 *  - free-bucket head -- pointer to the head of the free-bucket list
 *  - free-bucket tail -- pointer to the tail of the free-bucket list
 *
 *  The preceding example shows a heap with the left and the right sentinel.
 *  The segment following the left sentinel consists of 2 pages, and is free.
 *  The subsequent segment is not free, and is allocated to store a bucket, which holds a free list.
 *  The right sentinel holds an allocation bitset, in which the 0-th, the 3rd and the last bit are set.
 *
 *
 *  ## Implementation
 *
 *  The implementation revolves around two main operations -- malloc and free.
 *  The operations aim to satisfy allocation/deallocation requests, while maintaining the heap invariants.
 *  The preceding description should provide sufficient context to understand the rest from the code.
 *
 *  There is dumping/loading functionality to capture the state of the heap.
 *  The dumps can be loaded into the debug page, which can be used to visualize and debug the heap state.
 *
 *
 *  ## Future improvements
 *
 *  Here are some of the pending features:
 *
 *  - Closest-fit segment selection, to decrease external fragmentation.
 *    For this, we need a search tree that keeps lists of same-size free segments, keyed by their length.
 *    The segment-allocation then becomes O(log n), rather than O(n).
 *  - Avoid the free neighbouring segment search in one of the deallocation cases,
 *    For this, we need an interval tree, which makes deallocation O(log n), rather than (occasionally) O(n).
 *  - Use two-page buckets, to decrease internal fragmentation
 *    (because large size-classes and small segments currently waste space).
 *  - Deallocating completely free buckets from the free-bucket lists, when the free-bucket list is long.
 *  - More intelligently ordering the completely-free buckets to free-bucket lists,
 *    to increase the likelihood that nearly free buckets become completely free.
 */

const INITIAL_SIZE = 1 << 16;

const MAX_SIZE = (1 << 31) >>> 0;

/** The page size and the minimum fragment size
 *  must be such that a page stores less than 255 fragments.
 *  The page size must be 2^15 or less.
 */
const PAGE_SIZE_EXPONENT = 10;

const PAGE_SIZE = 1 << PAGE_SIZE_EXPONENT;

const PAGE_SIZE_MASK = PAGE_SIZE - 1;

const SEGMENT_HEADER_SIZE = 16;

const SEGMENT_NEXT_FREE_OFFSET = 0;

const SEGMENT_PREV_FREE_OFFSET = 4;

const SEGMENT_NEXT_OFFSET = 8;

const SEGMENT_PREV_OFFSET = 12;

const NON_FREE_SEGMENT = 0xffffffff;

const MIN_FRAGMENT_SIZE_EXPONENT = 4;

const MIN_FRAGMENT_SIZE = 1 << MIN_FRAGMENT_SIZE_EXPONENT;

const BUCKET_SEGMENT_MAGIC = 0xffffceca;

const BUCKET_SIZE = PAGE_SIZE - SEGMENT_HEADER_SIZE;

const BUCKET_NEXT_OFFSET = 0;

const BUCKET_PREV_OFFSET = 4;

const BUCKET_FREE_LIST_OFFSET = 8;

const BUCKET_FREE_COUNT_OFFSET = 10;

const BUCKET_FRAGMENT_SIZE_EXPONENT_OFFSET = 11;

const BUCKET_ALLOC_BITSET_OFFSET = 12;

const BUCKET_PRE_BITSET_SIZE = 12;

const BUCKET_ALLOC_BITSET_MIN_SIZE = 8;

/** A bit of an overestimate, because the actual capacity is decreased by the bitset's own size.
 */
const BUCKET_ALLOC_BITSET_SIZE = Math.max(
    BUCKET_ALLOC_BITSET_MIN_SIZE,
    Math.ceil(
        Math.ceil(
            Math.floor((BUCKET_SIZE - BUCKET_PRE_BITSET_SIZE - BUCKET_ALLOC_BITSET_MIN_SIZE) / MIN_FRAGMENT_SIZE) / 8
        ) / 4
    ) * 4
);

const BUCKET_HEADER_SIZE = BUCKET_PRE_BITSET_SIZE + BUCKET_ALLOC_BITSET_SIZE;

/** The minimum fragment size must be 2 bytes or more,
 *  because free fragments are used as free-lists.
 */
const BUCKET_CAPACITY = BUCKET_SIZE - BUCKET_HEADER_SIZE;

const MAX_FRAGMENT_SIZE_EXPONENT = 32 - Math.clz32((BUCKET_CAPACITY >> 1) - 1);

const MAX_FRAGMENT_SIZE = 1 << MAX_FRAGMENT_SIZE_EXPONENT;

const MAX_BUCKET_FRAGMENTS = Math.floor(BUCKET_CAPACITY / MIN_FRAGMENT_SIZE);

const FREE_LIST_NULL = 0xffff;

const assertEquals = (msg, exp, act) => {
    if (exp !== act) throw new Error(msg + " Expected: " + exp + ", actual " + act);
};

const assertTrue = (msg, cond) => {
    if (!cond) throw new Error(msg);
};

/** Functions for manipulating segments.
 */
const segment = {
    /** Address free list of start-length tuples for variable-size memory segments.
     *  Sorted by the start address. Segments are non-overlapping.
     *  At the moment, we do not use a secondary data structure with size-based ordering.
     *  This optimization (for faster segment allocation) can be done later.
     *
     *  The zero-th segment is always the left sentinel segment, and at address 0.
     */
    leftSentinel: () => {
        return 0;
    },

    /** The address of the right sentinel segment.
     */
    rightSentinel: () => {
        const heapSize = heap.byteSize();
        return heapSize - heap._computeRightSentinelSize(heapSize);
    },

    /** Computes the total size of the segment at the specified address (including the header).
     */
    size: (address) => {
        return segment.next(address) - address;
    },

    /** Checks whether the segment at the specified address is in the free list.
     *  The caller must ensure that the specified address is really a start of an actual segment.
     */
    isFree: (address) => {
        return segment.nextFree(address) !== NON_FREE_SEGMENT && segment.prevFree(address) !== NON_FREE_SEGMENT;
    },

    /** Returns the next-free pointer of the segment that starts at the specified address.
     */
    nextFree: (address) => {
        return heap._memory.getUint32(address + SEGMENT_NEXT_FREE_OFFSET);
    },

    /** Sets the next-free pointer of the segment that starts at the specified address.
     */
    setNextFree: (address, next) => {
        heap._memory.setUint32(address + SEGMENT_NEXT_FREE_OFFSET, next);
    },

    /** Returns the previous-free pointer of the segment that starts at the specified address.
     */
    prevFree: (address) => {
        return heap._memory.getUint32(address + SEGMENT_PREV_FREE_OFFSET);
    },

    /** Sets the previous-free pointer of the segment that starts at the specified address.
     */
    setPrevFree: (address, prev) => {
        heap._memory.setUint32(address + SEGMENT_PREV_FREE_OFFSET, prev);
    },

    /** Returns the next pointer of the segment that starts at the specified address.
     */
    next: (address) => {
        return heap._memory.getUint32(address + SEGMENT_NEXT_OFFSET);
    },

    /** Sets the next pointer of the segment that starts at the specified address.
     */
    setNext: (address, next) => {
        heap._memory.setUint32(address + SEGMENT_NEXT_OFFSET, next);
    },

    /** Returns the previous pointer of the segment that starts at the specified address.
     */
    prev: (address) => {
        return heap._memory.getUint32(address + SEGMENT_PREV_OFFSET);
    },

    /** Sets the previous pointer of the segment that starts at the specified address.
     */
    setPrev: (address, prev) => {
        heap._memory.setUint32(address + SEGMENT_PREV_OFFSET, prev);
    },

    /** Checks whether the segment at the specified address is a bucket.
     */
    isBucket: (address) => {
        return heap._memory.getUint32(address + SEGMENT_NEXT_FREE_OFFSET) === BUCKET_SEGMENT_MAGIC;
    },

    /** Sets up the header of the segment at the specified address.
     *  Caller is responsible for making sure that it does not overlap other segments.
     */
    init: (address, nextFreeAddress, prevFreeAddress, nextAddress, prevAddress) => {
        heap._memory.setUint32(address + SEGMENT_NEXT_FREE_OFFSET, nextFreeAddress);
        heap._memory.setUint32(address + SEGMENT_PREV_FREE_OFFSET, prevFreeAddress);
        heap._memory.setUint32(address + SEGMENT_NEXT_OFFSET, nextAddress);
        heap._memory.setUint32(address + SEGMENT_PREV_OFFSET, prevAddress);
    },

    /** Finds the address of the preceding (i.e. immediately previous) free segment.
     *  The address has to be an address of an actual segment, but the segment need not be free.
     *  If the segment at the address is free, then the same address will be returned.
     *  This function may return the left sentinel.
     *
     *  This function may be optimized in the future to use the bitset,
     *  or an auxiliary data structure for faster searches.
     */
    findPrecedingFree: (address) => {
        let cur = address;
        while (cur !== segment.leftSentinel()) {
            if (segment.isFree(cur)) {
                return cur;
            }
            cur = segment.prev(cur);
        }
        return cur;
    },

    /** Find the size of the smallest free segment.
     */
    minFreeSize: () => {
        let min = 0;
        let cur = 0;
        while (cur !== NON_FREE_SEGMENT) {
            if (segment.isFree(cur) && (min === 0 || segment.size(cur) < min)) {
                min = segment.size(cur);
            }
            cur = segment.nextFree(cur);
        }
        return min;
    },

    /** Find the size of the largest free segment.
     */
    maxFreeSize: () => {
        let max = 0;
        let cur = 0;
        while (cur !== NON_FREE_SEGMENT) {
            if (segment.isFree(cur) && segment.size(cur) > max) {
                max = segment.size(cur);
            }
            cur = segment.nextFree(cur);
        }
        return max;
    },
};

/** Functions for accessing and manipulating free-bucket lists.
 */
const freeBucket = {
    /** Returns the head of the free-bucket list of the specified rank.
     */
    head: (rank) => {
        return heap._freebuckets[rank * 2 + 0];
    },

    /** Sets the head of the free-bucket list of the specified rank.
     */
    setHead: (rank, address) => {
        heap._freebuckets[rank * 2 + 0] = address;
    },

    /** Returns the tail of the free-bucket list of the specified rank.
     */
    tail: (rank) => {
        return heap._freebuckets[rank * 2 + 1];
    },

    /** Sets the tail of the free-bucket list of the specified rank.
     */
    setTail: (rank, address) => {
        heap._freebuckets[rank * 2 + 1] = address;
    },

    /** Initializes the bucket lists.
     *  Should be called only once, during heap initialization.
     */
    initLists: () => {
        for (let rank = 0; bucket.fragmentSizeForRank(rank) <= MAX_FRAGMENT_SIZE; rank++) {
            heap._freebuckets.push(0);
            heap._freebuckets.push(0);
        }
    },
};

/** Functions for accessing and manipulating buckets.
 */
const bucket = {
    /** Given a rank, computes x of the maximum size=2^x of the fragments in that bucket.
     */
    fragmentSizeExponentForRank: (rank) => {
        return rank + MIN_FRAGMENT_SIZE_EXPONENT;
    },

    /** Given a rank, computes the maximum size of the fragments in that bucket.
     */
    fragmentSizeForRank: (rank) => {
        return 1 << bucket.fragmentSizeExponentForRank(rank);
    },

    /** Computes the bucket rank for the given size.
     *  The rank is the index of the list that contains buckets
     *  of the smallest fragment size in which the specified size fits.
     */
    rankForSize: (size) => {
        const ceilSizeExponent = 32 - Math.clz32(size - 1);
        const bucketRank = Math.max(MIN_FRAGMENT_SIZE_EXPONENT, ceilSizeExponent) - MIN_FRAGMENT_SIZE_EXPONENT;
        return bucketRank;
    },

    /** Initializes the bucket whose header is at the specified address.
     */
    init: (address, next, prev, freeListAddress, freeCount, fragmentSizeExponent) => {
        heap._memory.setUint32(address + BUCKET_NEXT_OFFSET, next);
        heap._memory.setUint32(address + BUCKET_PREV_OFFSET, prev);
        heap._memory.setUint16(address + BUCKET_FREE_LIST_OFFSET, freeListAddress);
        heap._memory.setUint8(address + BUCKET_FREE_COUNT_OFFSET, freeCount);
        heap._memory.setUint8(address + BUCKET_FRAGMENT_SIZE_EXPONENT_OFFSET, fragmentSizeExponent);

        // Specify that the corresponding segment is a bucket.
        heap._memory.setUint32(address - SEGMENT_HEADER_SIZE + SEGMENT_NEXT_FREE_OFFSET, BUCKET_SEGMENT_MAGIC);

        // Arrange the free-list entries.
        const fragmentSize = 1 << fragmentSizeExponent;
        for (let i = 0; i < freeCount; i++) {
            const cur = i * fragmentSize;
            const next = i === freeCount - 1 ? FREE_LIST_NULL : (i + 1) * fragmentSize;
            freeList.setTail(address + BUCKET_HEADER_SIZE, cur, next);
        }

        // Initialize the allocation bitset.
        const bitsetStart = address + BUCKET_ALLOC_BITSET_OFFSET;
        const bitsetEnd = bitsetStart + BUCKET_ALLOC_BITSET_SIZE;
        for (let bitsetAddress = bitsetStart; bitsetAddress < bitsetEnd; bitsetAddress += 4) {
            heap._memory.setUint32(bitsetAddress, 0);
        }
    },

    /** Ensures that a bucket of the given rank exists in the corresponding free-bucket list.
     */
    ensure: (bucketRank) => {
        const head = freeBucket.head(bucketRank);
        if (head !== 0) {
            return head;
        } else {
            // No free buckets of this rank, allocate a new one.
            const address = heap._allocateSegment(BUCKET_SIZE);
            if (address === 0) {
                return 0;
            }
            const sizeExponent = bucket.fragmentSizeExponentForRank(bucketRank);
            const fragmentCount = Math.floor(BUCKET_CAPACITY / (1 << sizeExponent));
            bucket.init(address, 0, 0, 0, fragmentCount, sizeExponent);
            freeBucket.setHead(bucketRank, address);
            freeBucket.setTail(bucketRank, address);
            return address;
        }
    },

    /** Returns the next free-bucket-list pointer for the bucket at the specified address.
     */
    next: (address) => {
        return heap._memory.getUint32(address + BUCKET_NEXT_OFFSET);
    },

    /** Sets the next free-bucket-list pointer for the bucket at the specified address.
     */
    setNext: (address, value) => {
        return heap._memory.setUint32(address + BUCKET_NEXT_OFFSET, value);
    },

    /** Returns the previous free-bucket-list pointer for the bucket at the specified address.
     */
    prev: (address) => {
        return heap._memory.getUint32(address + BUCKET_PREV_OFFSET);
    },

    /** Sets the previous free-bucket-list pointer for the bucket at the specified address.
     */
    setPrev: (address, value) => {
        return heap._memory.setUint32(address + BUCKET_PREV_OFFSET, value);
    },

    /** Returns the free list of the bucket at the specified address.
     */
    freeList: (address) => {
        return heap._memory.getUint16(address + BUCKET_FREE_LIST_OFFSET);
    },

    /** Sets the offset of the free list of the bucket at the specified address.
     *  The offset is a 16-bit value.
     */
    setFreeList: (address, offset) => {
        heap._memory.setUint16(address + BUCKET_FREE_LIST_OFFSET, offset);
    },

    /** Returns the free count of the bucket at the specified address.
     */
    freeCount: (address) => {
        return heap._memory.getUint8(address + BUCKET_FREE_COUNT_OFFSET);
    },

    /** Sets the free count of the bucket at the specified address.
     *  The count is an 8-bit value.
     */
    setFreeCount: (address, count) => {
        heap._memory.setUint8(address + BUCKET_FREE_COUNT_OFFSET, count);
    },

    /** Returns the fragment-size exponent of the bucket at the specified address.
     */
    fragmentSizeExponent: (address) => {
        return heap._memory.getUint8(address + BUCKET_FRAGMENT_SIZE_EXPONENT_OFFSET);
    },

    /** Returns the allocation bit with the specified index for the bucket at the specified address.
     */
    allocBit: (bucketAddress, index) => {
        const address = bucketAddress + BUCKET_ALLOC_BITSET_OFFSET + (index >> 3);
        const byte = heap._memory.getUint8(address);
        return (byte >> (index & 0x7)) & 1;
    },

    /** Sets the allocation bit with the specified index for the bucket at the specified address.
     */
    setAllocBit: (bucketAddress, index, value) => {
        const address = bucketAddress + BUCKET_ALLOC_BITSET_OFFSET + (index >> 3);
        const oldByte = heap._memory.getUint8(address);
        const bit = 1 << (index & 0x7);
        const byte = value !== 0 ? oldByte | bit : oldByte & ~bit;
        heap._memory.setUint8(address, byte);
    },

    /** Computes the total fragment-count capacity of a bucket, given the bucket header address.
     */
    fragmentCount: (address) => {
        const sizeExponent = bucket.fragmentSizeExponent(address);
        return Math.floor(BUCKET_CAPACITY / (1 << sizeExponent));
    },

    /** Allocates a fragment from the bucket's free list.
     *  The caller must ensure that the free list is non-empty.
     */
    allocateFragment: (bucketAddress, bucketRank) => {
        const freeCount = bucket.freeCount(bucketAddress);
        bucket.setFreeCount(bucketAddress, freeCount - 1);
        const freeListAddress = bucketAddress + BUCKET_HEADER_SIZE;
        const head = bucket.freeList(bucketAddress);
        const tail = freeList.tail(freeListAddress, head);
        bucket.setFreeList(bucketAddress, tail);
        bucket.setAllocBit(bucketAddress, head >> (bucketRank + MIN_FRAGMENT_SIZE_EXPONENT), 1);
        return freeListAddress + head;
    },

    /** Removes the bucket at the specified address (with the specified rank) from the free-bucket list.
     *  This is called when all the fragments in the bucket get allocated.
     */
    removeFromList: (address, rank) => {
        const next = bucket.next(address);
        const prev = bucket.prev(address);
        bucket.setNext(address, 0);
        bucket.setPrev(address, 0);
        if (next === 0) {
            freeBucket.setTail(rank, prev);
        } else {
            bucket.setPrev(next, prev);
        }
        if (prev === 0) {
            freeBucket.setHead(rank, next);
        } else {
            bucket.setNext(prev, next);
        }
    },

    /** Appends the bucket at the specified address (with the specified rank) to the end of the free-bucket list.
     *  This is called after, in a previously completely allocated bucket, some fragments get deallocated.
     */
    appendToList: (address, rank) => {
        const tail = freeBucket.tail(rank);
        freeBucket.setTail(rank, address);
        bucket.setPrev(address, tail);
        bucket.setNext(address, 0);
        if (tail === 0) {
            freeBucket.setHead(rank, address);
        } else {
            bucket.setNext(tail, address);
        }
    },
};

/** Functions for accessing and manipulating the free-list region of the bucket.
 */
const freeList = {
    /** Returns the free-list tail from the specified address within the bucket.
     *  The caller must ensure that the specified address the start of the free-list region,
     *  and that the offset within that region corresponds to an actual free-list node.
     */
    tail: (address, offset) => {
        return heap._memory.getUint16(address + offset);
    },

    /** Sets the free-list tail for the specified address within the bucket.
     *  The caller must ensure that the specified address the start of the free-list region,
     *  and that the offset within that region corresponds to an actual free-list node.
     */
    setTail: (address, offset, value) => {
        return heap._memory.setUint16(address + offset, value);
    },
};

const heap = {
    /**
     * Multi-byte view over the raw byte array.
     *
     * Due to endianness issues, outside access to data in this view should happen through the accessors on the heap object.
     * Only manipulation of internal data structures should directly access this.
     */
    _memory: new DataView(new ArrayBuffer(INITIAL_SIZE), 0, INITIAL_SIZE),

    /** List of address pairs of the heads and tails of all free-bucket lists.
     *
     *  A bucket is a segment from which small allocation requests can be served.
     *  Each bucket is a segment of PAGE_SIZE, that internally organized
     *  as a free list of memory chunks of the same size.
     *
     *  Each free-bucket list contains buckets whose fragments have one specific size.
     *  Each bucket-list must have at least
     */
    _freebuckets: [],

    /** Checks the invariants of the bucket at the specified address.
     */
    _checkBucketInvariants: (bucketAddress) => {
        const freeListAddress = bucketAddress + BUCKET_HEADER_SIZE;
        const sizeExponent = bucket.fragmentSizeExponent(bucketAddress);
        const size = 1 << sizeExponent;
        const head = bucket.freeList(bucketAddress);
        const freeCount = bucket.freeCount(bucketAddress);
        const fragmentCount = bucket.fragmentCount(bucketAddress);
        if (head !== FREE_LIST_NULL) {
            // Bucket is not full.
            assertEquals(
                "Free-list head (" + head + ") must be fragment-size-aligned (0x" + bucketAddress.toString(16) + ").",
                0,
                head % size
            );

            // Bucket free-count corresponds to what you get by traversing the free-list.
            let observedFreeCount = 0;
            let offset = head;
            const seen = {};
            while (offset !== FREE_LIST_NULL) {
                assertTrue(
                    "Offset not within bounds in bucket 0x" + bucketAddress.toString(16) + ".",
                    offset >= 0 && offset < fragmentCount * size
                );
                seen[offset] = true;
                observedFreeCount++;
                offset = freeList.tail(freeListAddress, offset);
                if (offset in seen) {
                    throw new Error("Free-list in bucket 0x" + bucketAddress.toString(16) + " is cyclic.");
                }
            }
            assertEquals(
                "Observed fragment count not equal to the bucket's free counter.",
                observedFreeCount,
                freeCount
            );
        } else {
            // Bucket is full.
            assertEquals("Bucket with NULL head must have 0 free fragments.", 0, freeCount);
        }
    },

    /** Checks some of the internal invariants of the heap.
     *  Throws an error if any of the invariants are violated.
     */
    _checkInvariants: () => {
        // Check segments.
        let cur = 0;
        while (cur < heap.byteSize()) {
            if (!segment.isFree(cur)) {
                if (cur === segment.leftSentinel()) {
                    assertEquals("Left sentinel prev-free is invalid.", NON_FREE_SEGMENT, segment.prevFree(cur));
                    assertEquals("Left sentinel prev is invalid.", 0, segment.prev(cur));
                    assertTrue("Left sentinel is not a bucket", !segment.isBucket(cur));
                }
                if (segment.isBucket(cur)) {
                    const bucketAddress = cur + SEGMENT_HEADER_SIZE;
                    heap._checkBucketInvariants(bucketAddress);
                }
            }
            const next = segment.next(cur);
            if (next <= cur) {
                throw new Error("Segment list is corrupted.");
            }
            cur = next;
        }

        // Check free-bucket lists.
        for (let rank = 0; bucket.fragmentSizeForRank(rank) < MAX_FRAGMENT_SIZE; rank++) {
            const head = freeBucket.head(rank);
            const tail = freeBucket.tail(rank);
            const buckets = [];
            let seen = {};
            let cur = head;
            while (cur !== 0) {
                seen[cur] = true;
                buckets.push(cur);
                const seg = cur - SEGMENT_HEADER_SIZE;
                assertEquals("Bucket must be within an allocated segment", false, segment.isFree(seg));
                const next = bucket.next(cur);
                if (next in seen) {
                    throw new Error("Forward free-bucket list " + rank + " cyclic at bucket " + cur.toString(16));
                }
                cur = next;
            }
            seen = {};
            cur = tail;
            while (cur !== 0) {
                seen[cur] = true;
                const lastInForwardList = buckets.pop();
                assertEquals("Free-bucket lists must be the same in both directions", lastInForwardList, cur);
                const prev = bucket.prev(cur);
                if (prev in seen) {
                    throw new Error("Backward free-bucket list " + rank + " cyclic at bucket " + cur.toString(16));
                }
                cur = prev;
            }
        }
    },

    /** Heap size in bytes.
     */
    byteSize: () => heap._memory.byteLength,

    /** Heap size in pages, each of which is PAGE_SIZE.
     */
    _pageCount: () => {
        return heap.byteSize() / PAGE_SIZE;
    },

    /** Returns the number of free pages in the heap (i.e. the total page-count of all free segments).
     */
    _freePageCount: () => {
        let cur = 0;
        let count = 0;
        while (cur < heap.byteSize()) {
            if (segment.isFree(cur)) {
                count += segment.size(cur) / PAGE_SIZE;
            }
            cur = segment.next(cur);
        }
        return count;
    },

    /** Sets/resets the alloc bit of the segment at the specified address.
     *  The caller must ensure that the address is PAGE_SIZE-aligned.
     */
    _setAllocBit: (address, value) => {
        const allocBitsetAddress = segment.rightSentinel() + SEGMENT_HEADER_SIZE;
        const pageIndex = address >> PAGE_SIZE_EXPONENT;
        const byteOffset = pageIndex >> 3;
        const bitOffset = pageIndex & 7;
        const oldAllocByte = heap._memory.getUint8(allocBitsetAddress + byteOffset);
        const allocByte = value != 0 ? oldAllocByte | (1 << bitOffset) : oldAllocByte & ~(1 << bitOffset);
        heap._memory.setUint8(allocBitsetAddress + byteOffset, allocByte);
    },

    /** Checks whether the specified address is the start of an allocated segment.
     *  The caller must ensure that the address is PAGE_SIZE-aligned.
     */
    _allocBit: (address) => {
        const allocBitsetAddress = segment.rightSentinel() + SEGMENT_HEADER_SIZE;
        const pageIndex = address >> PAGE_SIZE_EXPONENT;
        const byteOffset = pageIndex >> 3;
        const bitOffset = pageIndex & 7;
        const allocByte = heap._memory.getUint8(allocBitsetAddress + byteOffset);
        const allocBit = (allocByte >> bitOffset) & 1;
        return allocBit;
    },

    /** Computes the size of the left sentinel, for a given size of memory in bytes.
     */
    _computeLeftSentinelSize: (memorySize) => {
        return PAGE_SIZE;
    },

    /** Computes the size of the right sentinel, for a given size of memory in bytes.
     */
    _computeRightSentinelSize: (memorySize) => {
        const bitsetSize = (memorySize >> PAGE_SIZE_EXPONENT) >> 3;
        const roundedSize = Math.ceil((bitsetSize + SEGMENT_HEADER_SIZE) / PAGE_SIZE) * PAGE_SIZE;
        return roundedSize;
    },

    /** Tries to extend the heap to the target size, if that is possible.
     *  Returns true if successful, false otherwise.
     */
    _tryExtendMemory: (size) => {
        if (size > MAX_SIZE) {
            return false;
        }

        let memory;
        try {
            memory = new DataView(new ArrayBuffer(size), 0, size);
        } catch (e) {
            if (e instanceof RangeError) {
                return false;
            } else {
                throw e;
            }
        }

        // Copy the current memory content.
        const oldMemory = heap._memory;
        const oldSize = heap.byteSize();
        const oldPageCount = heap._pageCount();
        const oldRightSentinel = segment.rightSentinel();
        const oldRightSentinelSize = heap._computeRightSentinelSize(oldSize);
        for (let i = 0; i < oldSize; i += 4) {
            memory.setUint32(i, heap._memory.getUint32(i));
        }
        heap._memory = memory;

        // Reorganize and move the right sentinel.
        // The segment list and free-segment list tail must be adjusted.
        // The alloc-bitset must be copied (we rely on page-count being a multiple of 8).
        const rightSentinelSize = heap._computeRightSentinelSize(size);
        const rightSentinel = size - rightSentinelSize;

        // The old right sentinel segment will effectively get deallocated.
        // We therefore link the newly created segment against the old right sentinel,
        // and then deallocate the old right sentinel.
        // Later, free is called on the old right sentinel, and the deallocation logic does the rest.
        const freshSegment = oldSize;
        const prevFree = segment.prevFree(oldRightSentinel);
        // Old right sentinel.
        segment.setNextFree(oldRightSentinel, NON_FREE_SEGMENT);
        segment.setPrevFree(oldRightSentinel, NON_FREE_SEGMENT);
        segment.setNext(oldRightSentinel, freshSegment);
        // Previous free segment (this is either a real free segment or the left sentinel).
        segment.setNextFree(prevFree, freshSegment);
        // The freshly created segment.
        segment.setNextFree(freshSegment, rightSentinel);
        segment.setPrevFree(freshSegment, prevFree);
        segment.setNext(freshSegment, rightSentinel);
        segment.setPrev(freshSegment, oldRightSentinel);
        // The new right sentinel.
        segment.setNextFree(rightSentinel, NON_FREE_SEGMENT);
        segment.setPrevFree(rightSentinel, freshSegment);
        segment.setNext(rightSentinel, size);
        segment.setPrev(rightSentinel, freshSegment);

        // Copy the alloc bitset from the old right sentinel to the new right sentinel.
        const oldAllocBitset = oldRightSentinel + SEGMENT_HEADER_SIZE;
        const allocBitset = rightSentinel + SEGMENT_HEADER_SIZE;
        for (let i = 0, src = oldAllocBitset, dst = allocBitset; i < oldPageCount; i += 8, src += 1, dst += 1) {
            const byte = oldMemory.getUint8(src);
            memory.setUint8(dst, byte);
        }
        heap._setAllocBit(rightSentinel, 1);

        // Free-bucket lists do not need any changes.

        // At this point, the allocator's invariants hold.
        // Free the old right sentinel.
        heap._freeSegment(oldRightSentinel);

        return true;
    },

    /** Find a segment that satisfies the request, and either return it fully, or split it into two segments.
     *  The returned address is the user address, i.e. pointing to memory after the segment header.
     */
    _allocateSegment: (size) => {
        // If the heap size is itself smaller than the request, then the request is not satisfiable.
        // In this case, we try to resize the heap.
        // In the future, this check should compare the request size against the largest free segment size.
        let heapSize = heap.byteSize();
        let leftSentinelSize = heap._computeLeftSentinelSize(heapSize);
        let rightSentinelSize = heap._computeRightSentinelSize(heapSize);
        if (size > heapSize - leftSentinelSize - rightSentinelSize) {
            do {
                heapSize *= 2;
                leftSentinelSize = heap._computeLeftSentinelSize(heapSize);
                rightSentinelSize = heap._computeRightSentinelSize(heapSize);
            } while (heapSize < size - leftSentinelSize - rightSentinelSize);
            if (!heap._tryExtendMemory(heapSize)) {
                return 0;
            }
        }

        const roundedSize = Math.ceil((size + SEGMENT_HEADER_SIZE) / PAGE_SIZE) * PAGE_SIZE;
        const rightSentinel = segment.rightSentinel();
        let address = segment.nextFree(segment.leftSentinel());
        while (address !== rightSentinel) {
            let segmentSize = segment.size(address);
            // Both segmentSize and roundedSize are multiples of PAGE_SIZE,
            // so it is not necessary to deduct SEGMENT_HEADER_SIZE from segmentSize.
            if (segmentSize >= roundedSize) {
                // Peel off, from the end, a subsegment rounded-up to a PAGE_SIZE multiplier.
                const resultAddress = address + segmentSize - roundedSize;
                segmentSize = segmentSize - roundedSize;
                if (segmentSize > 0) {
                    // Split the current segment into two.
                    const next = segment.next(address);
                    segment.init(resultAddress, NON_FREE_SEGMENT, NON_FREE_SEGMENT, next, address);
                    segment.setNext(address, resultAddress);
                    segment.setPrev(next, resultAddress);
                } else {
                    // Current segment completely serves the request.
                    // Must be removed from the list of free segments.
                    const prev = segment.prevFree(address);
                    const next = segment.nextFree(address);
                    segment.setNextFree(prev, next);
                    segment.setPrevFree(next, prev);
                    segment.setPrevFree(resultAddress, NON_FREE_SEGMENT);
                    segment.setNextFree(resultAddress, NON_FREE_SEGMENT);
                }
                heap._setAllocBit(resultAddress, 1);
                const userAddress = resultAddress + SEGMENT_HEADER_SIZE;
                return userAddress;
            }
            address = segment.nextFree(address);
        }

        // Try to extend the entire off-heap memory.
        if (heap._tryExtendMemory(heapSize * 2)) {
            // Memory was successfully extended, try again.
            return heap._allocateSegment(size);
        }

        // Unable to serve the memory request.
        return 0;
    },

    /** Allocates a fragment of the specified size.
     */
    _allocateFragment: (size) => {
        const bucketRank = bucket.rankForSize(size);
        const bucketAddress = bucket.ensure(bucketRank);
        if (bucketAddress === 0) {
            return 0;
        }
        const userAddress = bucket.allocateFragment(bucketAddress, bucketRank);
        if (bucket.freeCount(bucketAddress) === 0) {
            bucket.removeFromList(bucketAddress, bucketRank);
        }
        return userAddress;
    },

    /** Frees the segment at the specified address.
     */
    _freeSegment: (address) => {
        // The address is aligned to a PAGE_SIZE granularity,
        // Double-check that this is not a sentinel segment.
        if (address === segment.leftSentinel() || address === segment.rightSentinel()) {
            // Undefined behavior, so just return.
            return;
        }
        // Check the segment-allocation bitset to see if the segment is allocated.
        if (heap._allocBit(address) !== 1) {
            // Undefined behavior, so just return.
            return;
        }
        // Finally, check if this segments is a bucket allocation.
        // Such allocations belong to the allocator, and cannot be freed by user programs.
        if (segment.isBucket(address)) {
            return;
        }

        const next = segment.next(address);
        const prev = segment.prev(address);
        const nextIsFree = segment.isFree(next);
        const prevIsFree = segment.isFree(prev);
        heap._setAllocBit(address, 0);
        if (nextIsFree && prevIsFree) {
            // Case 1: Both adjacent segments are free, should be merged.
            // Therefore, neither of them is a sentinel.
            // We merge all three into a single segment.
            //
            // (I) Segment list
            //                                     ________________
            //      ____      ____                /  ____________  |
            //     /    |    |    |               | /            | |
            //     |    V    V    |        =>     V |            V |
            // ...|----|xxxx|----|...         ...|----|----|----|....
            //     prev addr next nextnext        prev addr next nextnext
            //
            const nextnext = segment.next(next);
            segment.setNext(prev, nextnext);
            segment.setPrev(nextnext, prev);

            // (II) Free-segment list
            //                                            _____________________
            //      _________   _________                /  _________________  |
            //     /         | |         |               | /                 | |
            //     |         V V         |        =>     V |                 V |
            // ...|----|xxxx|----|......|----|       ...|----|----|----|....|----|
            //     prev addr next    nextnextfree        prev addr next   nextnextfree
            //
            const nextnextfree = segment.nextFree(next);
            segment.setNextFree(prev, nextnextfree);
            segment.setPrevFree(nextnextfree, prev);
        } else if (nextIsFree && !prevIsFree) {
            // Case 2: Only the next segment is free.
            // The next segment is not a sentinel.
            // We merge the current and the next segment.
            //
            // (I) Segment list
            //                                          ___________
            //           ____   ___                    /  _______  |
            //          /    | |   |                   | |       \ |
            //          |    V V   |       =>          | V       | V
            // ...|xxxx|xxxx|----|....        ...|xxxx|----|----|....
            //     prev addr next nextnext        prev addr next nextnext
            //
            const nextnext = segment.next(next);
            segment.setNext(address, nextnext);
            segment.setPrev(nextnext, address);

            // (II) Free-segment list
            //
            //                                                         _________________   _____________
            //      ____________________   _________                __/_____________   _|_|___________  |
            //     /                    | |         |              /  |             | | | |           | |
            //     |                    V V         |        =>    |  V             V V | |           | V
            //   |----|......|xxxx|xxxx|----|......|----|         |----|......|xxxx|-------|----|....|----|
            // nextprevfree   prev addr next    nextnextfree    nextprevfree   prev addr    next   nextnextfree
            //
            const nextnextfree = segment.nextFree(next);
            const nextprevfree = segment.prevFree(next);
            segment.setNextFree(address, nextnextfree);
            segment.setPrevFree(nextnextfree, address);
            segment.setPrevFree(address, nextprevfree);
            segment.setNextFree(nextprevfree, address);
        } else if (!nextIsFree && prevIsFree) {
            // Case 3: Only the previous segment is free.
            // The previous segment is not a sentinel.
            // We merge the previous and the current segment.
            //
            // (I) Segment list
            //                                   ___________
            //      ____   ___                  /  _______  |
            //     /    | |   |                 | |       | |
            //     |    V V   |         =>      | V       | V
            // ...|----|xxxx|xxxx|....      ...|----|----|xxxx|....
            //     prev addr next               prev addr next
            //
            segment.setNext(prev, next);
            segment.setPrev(next, prev);

            // (II) Free-segment list
            //      __________   ____________________                 __________   ____________________
            //     /          | |                    |               /          | |                    |
            //     |          V V                    |       =>      |          V V                    |
            //   |----|......|----|xxxx|xxxx|......|----|          |----|......|----|----|xxxx|......|----|
            // nextprevfree   prev addr next    nextnextfree     nextprevfree   prev addr next    nextnextfree
            //
            // Free-segment list stays as-is, because the previous segment is just extended.
        } else {
            // Case 4: Neither of the adjacent segments is free.
            // Both of the adjacent segments may be sentinels.
            // We do not need to merge the current segment.
            //
            // (I) Segment list
            //      ____   ___                   ____   ___
            //     /    | |   |                 /    | |   |
            //     |    V V   |         =>      |    V V   |
            // ...|xxxx|xxxx|xxxx|....      ...|xxxx|----|xxxx|....
            //     prev addr next               prev addr next
            //
            // The segment list does not change, because no segment was merged.

            // (II) Free-segment list
            //
            //     ____________________________________               _____________________   _____________
            //    /  ________________________________  |             /  _______________   _|_|_____________|_
            //    | /                                | |             | /               | | | |             | |
            //    V |                                V |      =>     V |               V V | |             V |
            //   |-----|......|xxxx|xxxx|xxxx|......|----|          |----|......|xxxx|--------|xxxx|......|----|
            // precedingfree   prev addr next   subsequentfree                   prev   addr   next
            //
            // Must find the preceding and the subsequent free segment.
            // These segments could be sentinels.
            const precedingfree = segment.findPrecedingFree(address);
            const subsequentfree = segment.nextFree(precedingfree);
            segment.setNextFree(precedingfree, address);
            segment.setPrevFree(address, precedingfree);
            segment.setNextFree(address, subsequentfree);
            segment.setPrevFree(subsequentfree, address);
        }
    },

    _freeFragment: (address) => {
        // Determine the bucket that this address belongs to.
        const segmentAddress = address & ~PAGE_SIZE_MASK;

        // Check if the enclosing segment is a bucket.
        if (!segment.isBucket(segmentAddress)) {
            return;
        }

        // Check alignment -- a valid address must be fragment-aligned.
        const bucketAddress = segmentAddress + SEGMENT_HEADER_SIZE;
        const freeListAddress = bucketAddress + BUCKET_HEADER_SIZE;
        const fragmentSizeExponent = bucket.fragmentSizeExponent(bucketAddress);
        const fragmentSize = 1 << fragmentSizeExponent;
        const fragmentOffset = address - freeListAddress;
        if ((fragmentOffset & (fragmentSize - 1)) !== 0) {
            return;
        }

        // Check if allocated.
        const fragmentIndex = fragmentOffset >> fragmentSizeExponent;
        if (bucket.allocBit(bucketAddress, fragmentIndex) !== 1) {
            return;
        }

        // Insert into the bucket's free-list.
        const head = bucket.freeList(bucketAddress);
        freeList.setTail(freeListAddress, fragmentOffset, head);
        bucket.setFreeList(bucketAddress, fragmentOffset);

        // Update the count and the allocation bitset.
        const oldFreeCount = bucket.freeCount(bucketAddress);
        bucket.setFreeCount(bucketAddress, oldFreeCount + 1);
        bucket.setAllocBit(bucketAddress, fragmentIndex, 0);

        // Check if the bucket had been full, but now has free slots.
        if (oldFreeCount === 0) {
            // Reinsert the bucket at the end of the respective free-bucket list.
            bucket.appendToList(bucketAddress, fragmentSizeExponent - MIN_FRAGMENT_SIZE_EXPONENT);
        }
    },

    malloc: (longSize) => {
        if (Long64.highBits(longSize) !== 0) {
            // Unsafe code should throw an OutOfMemoryError in this case.
            return Long64.fromInt(0);
        }
        const size = Long64.lowBits(longSize);
        const address = heap._malloc(size);
        return Long64.fromInt(address);
    },

    _malloc: (size) => {
        if (size <= MAX_FRAGMENT_SIZE) {
            // Small object, find a free bucket for it and allocate from it.
            return heap._allocateFragment(size);
        } else {
            // Large object, find a free larger segment, and peel a subsegment.
            return heap._allocateSegment(size);
        }
    },

    realloc: (longUserAddress, longNewSize) => {
        if (Long64.highBits(longNewSize) !== 0) {
            // Unsafe code should throw an OutOfMemoryError in this case.
            return Long64.fromInt(0);
        }
        const newSize = Long64.lowBits(longNewSize);
        const newAddress = heap._realloc(narrowAddress(longUserAddress), newSize);
        return Long64.fromInt(newAddress);
    },

    _realloc: (userAddress, newSize) => {
        // TODO: This is used less frequently, so the current implementation does not try to extend the block first.
        // This can be improved later (for segments).
        if (userAddress === 0) {
            return heap._malloc(newSize);
        }

        const newAddress = heap._malloc(newSize);
        if (newAddress === 0) {
            return 0;
        }

        // Figure out the size.
        let oldSize = 0;
        let segmentAddress = userAddress - SEGMENT_HEADER_SIZE;
        if ((segmentAddress & PAGE_SIZE_MASK) === 0) {
            // Segments must not be allocator-owned.
            if (segmentAddress === segment.leftSentinel() || segmentAddress === segment.rightSentinel()) {
                return 0;
            }

            // Segment must have been allocated.
            if (heap._allocBit(segmentAddress) !== 1) {
                return 0;
            }

            // Bucket cannot be a user address.
            if (segment.isBucket(segmentAddress)) {
                return 0;
            }

            oldSize = segment.size(segmentAddress) - SEGMENT_HEADER_SIZE;
        } else {
            segmentAddress = userAddress & ~PAGE_SIZE_MASK;

            // The address cannot be a fragment is the corresponding segment is not a bucket.
            if (!segment.isBucket(segmentAddress)) {
                return 0;
            }

            // Check alignment -- a valid address must be fragment-aligned.
            const bucketAddress = segmentAddress + SEGMENT_HEADER_SIZE;
            const freeListAddress = bucketAddress + BUCKET_HEADER_SIZE;
            const fragmentSizeExponent = bucket.fragmentSizeExponent(bucketAddress);
            const fragmentSize = 1 << fragmentSizeExponent;
            const fragmentOffset = userAddress - freeListAddress;
            if ((fragmentOffset & (fragmentSize - 1)) !== 0) {
                return 0;
            }

            // Check if allocated.
            const fragmentIndex = fragmentOffset >> fragmentSizeExponent;
            if (bucket.allocBit(bucketAddress, fragmentIndex) !== 1) {
                return 0;
            }

            oldSize = fragmentSize;
        }

        // Copy the memory over and free the old segment (truncating if necessary).
        for (let i = 0; i < Math.min(oldSize, newSize); i++) {
            heap._memory.setUint8(newAddress + i, heap._memory.getUint8(userAddress + i));
        }

        // Free the old address.
        heap._free(userAddress);

        return newAddress;
    },

    free: (longUserAddress) => {
        if (Long64.highBits(longUserAddress) !== 0) {
            // Invalid address -- we currently do not allocate addresses in this range.
            return;
        }
        const userAddress = Long64.lowBits(longUserAddress);
        heap._free(userAddress);
    },

    _free: (userAddress) => {
        const segmentAddress = userAddress - SEGMENT_HEADER_SIZE;
        // Check whether the specified address is a small or a large allocation.
        if ((segmentAddress & PAGE_SIZE_MASK) === 0) {
            heap._freeSegment(segmentAddress);
        } else {
            // The address is not aligned, so it must be a fragment allocation.
            heap._freeFragment(userAddress);
        }
    },

    /** Dumps the state of the memory area into a DataView, which can then be dumped by the caller.
     *  The 32-bit memory size is dumped first, followed by the contents of the memory.
     *  The auxiliary data structures are dumped at the end.
     */
    _dump: () => {
        const size = 4 + heap.byteSize() + 4 * heap._freebuckets.length;
        const view = new DataView(new ArrayBuffer(size), 0, size);
        view.setUint32(0, heap.byteSize());
        for (let i = 0; i < heap.byteSize(); i++) {
            view.setUint8(4 + i, heap._memory.getUint8(i));
        }
        for (let i = 0; i < heap._freebuckets.length; i++) {
            view.setUint32(4 + heap.byteSize() + 4 * i, heap._freebuckets[i]);
        }
        return view;
    },

    /** Sets the state of the memory from the specified ArrayBuffer object.
     *  The bytes in the buffer must have been previously dumped with the _dump function.
     */
    _load: (view) => {
        const memoryByteLength = view.getUint32(0);
        heap._memory = new DataView(new ArrayBuffer(memoryByteLength), 0, memoryByteLength);
        for (let i = 0; i < memoryByteLength; i++) {
            heap._memory.setUint8(i, view.getUint8(4 + i));
        }
        heap._freebuckets = [];
        for (let i = 4 + memoryByteLength; i < view.byteLength; i += 8) {
            heap._freebuckets.push(view.getUint32(i + 0));
            heap._freebuckets.push(view.getUint32(i + 4));
        }
    },

    // The 8-bit accesses are only here for completeness, they don't have an endianness.

    getUint8: (address) => heap._memory.getUint8(address),
    setUint8: (address, value) => heap._memory.setUint8(address, value),

    getInt8: (address) => heap._memory.getInt8(address),
    setInt8: (address, value) => heap._memory.setInt8(address, value),

    getUint16: (address) => heap._memory.getUint16(address, runtime.isLittleEndian),
    setUint16: (address, value) => heap._memory.setUint16(address, value, runtime.isLittleEndian),

    getInt16: (address) => heap._memory.getInt16(address, runtime.isLittleEndian),
    setInt16: (address, value) => heap._memory.setInt16(address, value, runtime.isLittleEndian),

    getInt32: (address) => heap._memory.getInt32(address, runtime.isLittleEndian),
    setInt32: (address, value) => heap._memory.setInt32(address, value, runtime.isLittleEndian),

    getUint32: (address) => heap._memory.getUint32(address, runtime.isLittleEndian),
    setUint32: (address, value) => heap._memory.setUint32(address, value, runtime.isLittleEndian),

    getFloat32: (address) => heap._memory.getFloat32(address, runtime.isLittleEndian),
    setFloat32: (address, value) => heap._memory.setFloat32(address, value, runtime.isLittleEndian),

    getFloat64: (address) => heap._memory.getFloat64(address, runtime.isLittleEndian),
    setFloat64: (address, value) => heap._memory.setFloat64(address, value, runtime.isLittleEndian),
};

/** Organizes the contents of the memory to become the empty heap that satisfies the heap invariants.
 */
const initializeHeap = () => {
    const leftSentinelSize = PAGE_SIZE;
    const rightSentinelSize = heap._computeRightSentinelSize(INITIAL_SIZE);

    // Initialize the heap.
    // First is the PAGE_SIZE left sentinel.
    // The left sentinel is the only allocated node that has the next-free address set.
    segment.init(0, leftSentinelSize, NON_FREE_SEGMENT, leftSentinelSize, 0);

    // The second segment covers the rest of the heap, up to the right sentinel.
    segment.init(leftSentinelSize, INITIAL_SIZE - rightSentinelSize, 0, INITIAL_SIZE - rightSentinelSize, 0);

    // The last segment is the PAGE_SIZE right sentinel.
    // The right sentinel is the only allocated node that has the previous-free address set.
    segment.init(INITIAL_SIZE - leftSentinelSize, NON_FREE_SEGMENT, leftSentinelSize, INITIAL_SIZE, leftSentinelSize);

    // Set the allocation bits.
    heap._setAllocBit(0, 1);
    heap._setAllocBit(INITIAL_SIZE - rightSentinelSize, 1);

    // Initialize the bucket lists.
    freeBucket.initLists();
};

initializeHeap();

const narrowAddress = (la) => {
    if (Long64.highBits(la) !== 0) {
        // Invalid address -- we currently do not allocate addresses in this range.
        throw new Error("address-out-of-bounds");
    }
    return Long64.lowBits(la);
};

const read_byte = (la) => heap.getInt8(narrowAddress(la));

const read_char = (la) => heap.getUint16(narrowAddress(la));

const read_short = (la) => heap.getInt16(narrowAddress(la));

const read_int = (la) => heap.getInt32(narrowAddress(la));

const read_float = (la) => heap.getFloat32(narrowAddress(la));

const read_long = (la) => {
    const a = narrowAddress(la);
    if (runtime.isLittleEndian) {
        const lo = heap.getInt32(a);
        const hi = heap.getInt32(a + 4);
        return Long64.fromTwoInt(lo, hi);
    } else {
        const lo = heap.getInt32(a + 4);
        const hi = heap.getInt32(a);
        return Long64.fromTwoInt(lo, hi);
    }
};

const read_double = (la) => heap.getFloat64(narrowAddress(la));

const write_byte = (la, value) => heap.setInt8(narrowAddress(la), value);

const write_char = (la, value) => heap.setUint16(narrowAddress(la), value);

const write_short = (la, value) => heap.setInt16(narrowAddress(la), value);

const write_int = (la, value) => heap.setInt32(narrowAddress(la), value);

const write_float = (la, value) => heap.setFloat32(narrowAddress(la), value);

const write_long = (la, value) => {
    const a = narrowAddress(la);
    const lo = Long64.lowBits(value);
    const hi = Long64.highBits(value);

    if (runtime.isLittleEndian) {
        heap.setUint32(a, lo);
        heap.setUint32(a + 4, hi);
    } else {
        heap.setUint32(a + 4, lo);
        heap.setUint32(a, hi);
    }
};

const write_double = (la, value) => heap.setFloat64(narrowAddress(la), value);
