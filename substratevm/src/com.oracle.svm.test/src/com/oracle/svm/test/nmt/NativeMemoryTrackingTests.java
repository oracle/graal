/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2023, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.test.nmt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.WordFactory;
import org.graalvm.word.UnsignedWord;
import org.junit.Test;

import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.nmt.NativeMemoryTracking;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.genscavenge.HeapParameters;

public class NativeMemoryTrackingTests {
    private static final int K = 1024;
    private static final int M = 1024 * 1024;
    private static final UnsignedWord GRANULARITY = VirtualMemoryProvider.get().getGranularity();
    private static final int COMMIT_SIZE = (int) GRANULARITY.rawValue();
    private static final int RESERVE_SIZE = COMMIT_SIZE * 8;

    @Test
    public void testMalloc() {
        assertEquals(0, getUsedMemory());

        Pointer ptr = NativeMemory.malloc(16 * K, NmtCategory.Code);
        assertEquals(16 * K, getUsedMemory());
        assertTrue(getUsedMemory() > 0);

        NativeMemory.free(ptr);

        assertEquals(0, getUsedMemory());
    }

    @Test
    public void testCalloc() {
        assertEquals(0, getUsedMemory());
        Pointer ptr = NativeMemory.calloc(16 * K, NmtCategory.Code);

        assertEquals(16 * K, getUsedMemory());
        assertTrue(getUsedMemory() > 0);

        NativeMemory.free(ptr);

        assertEquals(0, getUsedMemory());
    }

    @Test
    public void testRealloc() {
        assertEquals(0, getUsedMemory());
        Pointer ptr = NativeMemory.malloc(16 * K, NmtCategory.Code);

        assertEquals(getUsedMemory(), 16 * K);
        assertTrue(getUsedMemory() > 0);

        Pointer reallocPtr = NativeMemory.realloc(ptr, WordFactory.unsigned(8 * K), NmtCategory.Code);
        assertEquals(8 * K, getUsedMemory());

        NativeMemory.free(reallocPtr);
        assertEquals(0, getUsedMemory());
    }

    @Test
    public void testPeakTracking() {
        assertEquals(0, getUsedMemory());

        Pointer ptr1 = NativeMemory.malloc(M, NmtCategory.Code);
        long peakUsed = NativeMemoryTracking.singleton().getPeakUsedMemory(NmtCategory.Code);
        assertEquals(M, peakUsed);

        Pointer ptr2 = NativeMemory.malloc(M, NmtCategory.Code);
        peakUsed = NativeMemoryTracking.singleton().getPeakUsedMemory(NmtCategory.Code);
        assertEquals(2 * M, peakUsed);

        NativeMemory.free(ptr1);
        ptr1 = WordFactory.nullPointer();

        NativeMemory.free(ptr2);
        ptr2 = WordFactory.nullPointer();

        assertEquals(0, getUsedMemory());
        assertEquals(2 * M, NativeMemoryTracking.singleton().getPeakUsedMemory(NmtCategory.Code));

        Pointer ptr3 = NativeMemory.malloc(3 * M, NmtCategory.Code);
        peakUsed = NativeMemoryTracking.singleton().getPeakUsedMemory(NmtCategory.Code);
        assertEquals(3 * M, peakUsed);

        NativeMemory.free(ptr3);
        ptr3 = WordFactory.nullPointer();

        assertEquals(0, getUsedMemory());
        assertEquals(3 * M, NativeMemoryTracking.singleton().getPeakUsedMemory(NmtCategory.Code));
    }

    private static long getUsedMemory() {
        return NativeMemoryTracking.singleton().getUsedMemory(NmtCategory.Code);
    }

    @Test
    public void testReserveAndCommitBasic() {
        Pointer reservePtr = beginVirtualMemoryTestAndReserve();

        Pointer commitPtr = commit(reservePtr, COMMIT_SIZE);
        assertEquals(RESERVE_SIZE, getReserved());
        assertEquals(COMMIT_SIZE, getCommitted());

        uncommit(commitPtr, COMMIT_SIZE);
        assertEquals(0, getCommitted());

        endVirtualMemoryTestAndFree(reservePtr);
    }

    @Test
    public void testSmallAlignmentReserveAndCommit() {
        assertEquals(0, getReserved());

        Pointer reservePtr = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(RESERVE_SIZE), GRANULARITY.unsignedDivide(2), false,
                        NmtCategory.Code);
        assertEquals(RESERVE_SIZE, getReserved());
        assertEquals(0, getCommitted());

        Pointer commitPtr = commit(reservePtr, COMMIT_SIZE * 3);
        assertEquals(COMMIT_SIZE * 3, getCommitted());

        uncommit(commitPtr, COMMIT_SIZE * 3);
        assertEquals(0, getCommitted());

        free(reservePtr, RESERVE_SIZE);
        assertEquals(0, getReserved());
    }

    @Test
    public void testReserveFree() {
        Pointer reservePtr1 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(RESERVE_SIZE), GRANULARITY, false,
                        NmtCategory.Code);
        assertEquals(RESERVE_SIZE, getReserved());

        Pointer reservePtr2 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(RESERVE_SIZE), GRANULARITY, false,
                        NmtCategory.Code);
        assertEquals(RESERVE_SIZE * 2, getReserved());

        Pointer reservePtr3 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(RESERVE_SIZE), GRANULARITY, false,
                        NmtCategory.Code);
        assertEquals(RESERVE_SIZE * 3, getReserved());

        free(reservePtr1, RESERVE_SIZE);
        free(reservePtr2, RESERVE_SIZE);
        free(reservePtr3, RESERVE_SIZE);
        assertEquals(0, getReserved());
    }

    @Test
    public void testAlternatingReserveFree() {
        assertEquals(0, getReserved());

        Pointer reservePtr1 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(RESERVE_SIZE), HeapParameters.getAlignedHeapChunkSize(), false,
                        NmtCategory.Code);
        free(reservePtr1, RESERVE_SIZE);

        Pointer reservePtr2 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(RESERVE_SIZE), HeapParameters.getAlignedHeapChunkSize(), false,
                        NmtCategory.Code);
        free(reservePtr2, RESERVE_SIZE);

        Pointer reservePtr3 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(RESERVE_SIZE), HeapParameters.getAlignedHeapChunkSize(), false,
                        NmtCategory.Code);
        free(reservePtr3, RESERVE_SIZE);

        assertEquals(0, getReserved());
    }

    @Test
    public void testGappedCommits() {
        Pointer reservePtr = beginVirtualMemoryTestAndReserve();

        Pointer commitPtr1 = commit(reservePtr, COMMIT_SIZE);
        assertEquals(COMMIT_SIZE, getCommitted());

        /* Commit again leaving a big gap between the previous regions. */
        Pointer commitPtr2 = commit(commitPtr1.add(COMMIT_SIZE * 3), COMMIT_SIZE);
        assertEquals(COMMIT_SIZE * 2, getCommitted());

        uncommit(commitPtr2, COMMIT_SIZE);
        assertEquals(COMMIT_SIZE, getCommitted());

        uncommit(commitPtr1, COMMIT_SIZE);
        assertEquals(0, getCommitted());

        endVirtualMemoryTestAndFree(reservePtr);
    }

    @Test
    public void testCommitUncommitThroughFree() {
        Pointer reservePtr = beginVirtualMemoryTestAndReserve();

        commit(reservePtr, COMMIT_SIZE);
        assertEquals(COMMIT_SIZE, getCommitted());

        /* Commit again leaving a large gap between the previous regions. */
        commit(reservePtr.add(COMMIT_SIZE * 3), COMMIT_SIZE);
        assertEquals(COMMIT_SIZE * 2, getCommitted());

        endVirtualMemoryTestAndFree(reservePtr);
    }

    @Test
    public void testAdjacentCommits() {
        Pointer reservePtr = beginVirtualMemoryTestAndReserve();

        Pointer commitPtr1 = commit(reservePtr, COMMIT_SIZE);
        assertEquals(COMMIT_SIZE, getCommitted());

        /* Commit again leaving a no gap between the previous regions. */
        Pointer commitPtr2 = commit(commitPtr1.add(COMMIT_SIZE), COMMIT_SIZE);
        assertEquals(COMMIT_SIZE * 2, getCommitted());

        uncommit(commitPtr2, COMMIT_SIZE);
        assertEquals(COMMIT_SIZE, getCommitted());

        uncommit(commitPtr1, COMMIT_SIZE);
        assertEquals(0, getCommitted());

        endVirtualMemoryTestAndFree(reservePtr);
    }

    @Test
    public void testAdjacentAndGappedCommits() {
        Pointer reservePtr = beginVirtualMemoryTestAndReserve();

        Pointer commitPtr1 = commit(reservePtr, COMMIT_SIZE);
        assertEquals(COMMIT_SIZE, getCommitted());

        /* Commit again adjacent to the previous committed region. */
        Pointer commitPtr2 = commit(commitPtr1.add(COMMIT_SIZE), COMMIT_SIZE);
        assertEquals(COMMIT_SIZE * 2, getCommitted());

        /* Commit again leaving a big gap between the previous regions. */
        Pointer commitPtr3 = commit(commitPtr2.add(COMMIT_SIZE * 3), COMMIT_SIZE);
        assertEquals(COMMIT_SIZE * 3, getCommitted());

        uncommit(commitPtr3, COMMIT_SIZE);
        assertEquals(COMMIT_SIZE * 2, getCommitted());
        uncommit(commitPtr2, COMMIT_SIZE);
        assertEquals(COMMIT_SIZE, getCommitted());
        uncommit(commitPtr1, COMMIT_SIZE);
        assertEquals(0, getCommitted());

        endVirtualMemoryTestAndFree(reservePtr);
    }

    @Test
    public void testFullyOverlappingCommits() {
        Pointer reservePtr = beginVirtualMemoryTestAndReserve();

        Pointer commitPtr1 = commit(reservePtr, COMMIT_SIZE);
        long recordedCommittedSize1 = getCommitted();
        assertEquals(COMMIT_SIZE, recordedCommittedSize1);

        /* Commit again completely overlapping the previous committed region. */
        Pointer commitPtr2 = commit(commitPtr1, COMMIT_SIZE);
        long recordedCommittedSize2 = getCommitted();
        assertEquals(COMMIT_SIZE, recordedCommittedSize2);

        uncommit(commitPtr2, COMMIT_SIZE);
        assertEquals(0, getCommitted());

        endVirtualMemoryTestAndFree(reservePtr);
    }

    /**
     * Test committing regions with unordered start addresses. Then uncommit in an unordered way.
     * |commitPtr1|commitPtr3| | | |commitPtr2| | |
     */
    @Test
    public void testUnorderedCommits() {
        Pointer reservePtr = beginVirtualMemoryTestAndReserve();

        Pointer commitPtr1 = commit(reservePtr, COMMIT_SIZE);
        long recordedCommittedSize1 = getCommitted();
        assertEquals(COMMIT_SIZE, recordedCommittedSize1);

        /* Commit again leaving a big gap between the previous regions. */
        Pointer commitPtr2 = commit(reservePtr.add(COMMIT_SIZE * 5), COMMIT_SIZE);
        long recordedCommittedSize2 = getCommitted();
        assertEquals(COMMIT_SIZE * 2, recordedCommittedSize2);

        // Commit again adjacent to the first committed region
        Pointer commitPtr3 = commit(commitPtr1.add(COMMIT_SIZE), COMMIT_SIZE);
        long recordedCommittedSize3 = getCommitted();
        assertEquals(COMMIT_SIZE * 3, recordedCommittedSize3);

        // Uncommit the previously committed region
        uncommit(commitPtr3, COMMIT_SIZE);
        assertEquals(COMMIT_SIZE * 2, getCommitted());
        uncommit(commitPtr1, COMMIT_SIZE);
        assertEquals(COMMIT_SIZE, getCommitted());
        uncommit(commitPtr2, COMMIT_SIZE);
        assertEquals(0, getCommitted());

        endVirtualMemoryTestAndFree(reservePtr);
    }

    @Test
    public void testPartiallyOverlappingCommits() {
        Pointer reservePtr = beginVirtualMemoryTestAndReserve();

        // Commit from within the middle of the reserved region
        Pointer commitPtr1 = commit(reservePtr.add(COMMIT_SIZE), COMMIT_SIZE * 2);
        long recordedCommittedSize1 = getCommitted();
        assertEquals(COMMIT_SIZE * 2, recordedCommittedSize1);

        // Commit again partially overlapping to the previous committed region
        commit(commitPtr1.add(COMMIT_SIZE), COMMIT_SIZE * 2);
        long recordedCommittedSize2 = getCommitted();
        assertEquals(COMMIT_SIZE * 3, recordedCommittedSize2);

        endVirtualMemoryTestAndFree(reservePtr);
    }

    @Test
    public void testPartialUncommit() {
        Pointer reservePtr = beginVirtualMemoryTestAndReserve();

        /* Commit from within the middle of the reserved region. */
        Pointer commitPtr1 = commit(reservePtr.add(COMMIT_SIZE), COMMIT_SIZE * 2);
        long recordedCommittedSize1 = getCommitted();
        assertEquals(COMMIT_SIZE * 2, recordedCommittedSize1);

        /*
         * Uncommit only half of the committed region. Half of the target region to uncommit is
         * actually not committed.
         */
        uncommit(commitPtr1.add(COMMIT_SIZE), COMMIT_SIZE * 2);
        assertEquals(COMMIT_SIZE, getCommitted());

        endVirtualMemoryTestAndFree(reservePtr);
    }

    @Test
    public void testPartialUncommitOverlappingMultipleRegions() {
        Pointer reservePtr = beginVirtualMemoryTestAndReserve();

        Pointer commitPtr1 = commit(reservePtr, COMMIT_SIZE * 2);
        assertEquals(COMMIT_SIZE * 2, getCommitted());

        /* Commit again adjacent to the previous region */
        commit(commitPtr1.add(COMMIT_SIZE * 2), COMMIT_SIZE * 2);
        assertEquals(COMMIT_SIZE * 4, getCommitted());

        /* Uncommit a region overlapping both previously committed regions. */
        uncommit(commitPtr1.add(COMMIT_SIZE), COMMIT_SIZE * 2);
        assertEquals(COMMIT_SIZE * 2, getCommitted());

        endVirtualMemoryTestAndFree(reservePtr);
    }

    @Test
    public void testReservedPeak() {
        assertEquals("Test should start with no memory already allocated in the test category.", 0, getReserved());
        assertEquals("Test should start with no memory already allocated in the test category.", 0, getCommitted());

        long initialPeak = NativeMemoryTracking.singleton().getPeakReservedByCategory(NmtCategory.Code);

        Pointer reservePtr1 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(initialPeak), GRANULARITY, false,
                        NmtCategory.Code);
        assertEquals(initialPeak, getReserved());

        Pointer reservePtr2 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(initialPeak), GRANULARITY, false,
                        NmtCategory.Code);
        long peakReserved = getReserved();
        assertEquals(peakReserved, NativeMemoryTracking.singleton().getPeakReservedByCategory(NmtCategory.Code));

        free(reservePtr1, initialPeak);
        free(reservePtr2, initialPeak);

        assertEquals(peakReserved, NativeMemoryTracking.singleton().getPeakReservedByCategory(NmtCategory.Code));
        assertEquals(0, getReserved());
    }

    @Test
    public void testCommittedPeak() {
        long initialPeak = NativeMemoryTracking.singleton().getPeakCommittedByCategory(NmtCategory.Code);
        long largeReserveSize = initialPeak * 2;
        int largeCommitSize = (int) initialPeak;
        Pointer reservePtr = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(largeReserveSize), HeapParameters.getAlignedHeapChunkSize(), false,
                        NmtCategory.Code);

        Pointer commitPtr1 = commit(reservePtr, largeCommitSize);
        assertEquals(getCommitted(), NativeMemoryTracking.singleton().getPeakCommittedByCategory(NmtCategory.Code));

        Pointer commitPtr2 = commit(commitPtr1.add(largeCommitSize), largeCommitSize);
        long recordedCommittedSize2 = getCommitted();
        assertEquals(recordedCommittedSize2, NativeMemoryTracking.singleton().getPeakCommittedByCategory(NmtCategory.Code));

        uncommit(commitPtr1, largeCommitSize);
        uncommit(commitPtr2, largeCommitSize);
        assertEquals(0, getCommitted());
        assertEquals(recordedCommittedSize2, NativeMemoryTracking.singleton().getPeakCommittedByCategory(NmtCategory.Code));

        free(reservePtr, largeReserveSize);
        assertEquals(0, getReserved());
        assertEquals(0, getCommitted());
    }

    private static Pointer commit(PointerBase base, long size) {
        Pointer result = VirtualMemoryProvider.get().commit(base, WordFactory.unsigned(size), 0, NmtCategory.Code);
        assertTrue("Commit operation failed.", result.isNonNull());
        return result;
    }

    private static void uncommit(PointerBase start, long nbytes) {
        int result = VirtualMemoryProvider.get().uncommit(start, WordFactory.unsigned(nbytes));
        assertEquals("Uncommit operation failed.", 0, result);
    }

    private static void free(PointerBase start, long nbytes) {
        int result = VirtualMemoryProvider.get().free(start, WordFactory.unsigned(nbytes));
        assertEquals("Free operation failed.", 0, result);
    }

    private static long getReserved() {
        return NativeMemoryTracking.singleton().getReservedByCategory(NmtCategory.Code);
    }

    private static long getCommitted() {
        return NativeMemoryTracking.singleton().getCommittedByCategory(NmtCategory.Code);
    }

    private static Pointer beginVirtualMemoryTestAndReserve() {
        assertEquals("Test should start with no memory already allocated in the test category.", 0, getReserved());
        assertEquals("Test should start with no memory already allocated in the test category.", 0, getCommitted());

        /* Reserve some memory. */
        Pointer reservePtr = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(RESERVE_SIZE), HeapParameters.getAlignedHeapChunkSize(), false,
                        NmtCategory.Code);
        assertEquals(RESERVE_SIZE, getReserved());
        assertEquals(0, getCommitted());
        return reservePtr;
    }

    private static void endVirtualMemoryTestAndFree(Pointer reservePtr) {
        /* Free the reserved region, which should also uncommit contained committed regions. */
        free(reservePtr, RESERVE_SIZE);
        assertEquals(0, getReserved());
        assertEquals(0, getCommitted());

    }
}
