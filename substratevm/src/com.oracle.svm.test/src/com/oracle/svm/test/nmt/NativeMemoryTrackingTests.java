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
import org.graalvm.word.WordFactory;
import org.junit.Test;

import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.nmt.NativeMemoryTracking;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.genscavenge.HeapParameters;

public class NativeMemoryTrackingTests {
    private static final int ALLOCATION_SIZE = 1024 * 16;
    private static final int REALLOC_SIZE = ALLOCATION_SIZE / 2;
    private final int commitSize = (int) VirtualMemoryProvider.get().getGranularity().rawValue();

    private final int reserveSize = commitSize * 8;

    @Test
    public void testMalloc() {
        assertEquals(0, getUsedMemory());

        Pointer ptr = NativeMemory.malloc(WordFactory.unsigned(ALLOCATION_SIZE), NmtCategory.Code);
        assertEquals(ALLOCATION_SIZE, getUsedMemory());
        assertTrue(getUsedMemory() > 0);

        NativeMemory.free(ptr);

        assertEquals(0, getUsedMemory());
    }

    @Test
    public void testCalloc() {
        assertEquals(0, getUsedMemory());
        Pointer ptr = NativeMemory.calloc(WordFactory.unsigned(ALLOCATION_SIZE), NmtCategory.Code);

        assertEquals(ALLOCATION_SIZE, getUsedMemory());
        assertTrue(getUsedMemory() > 0);

        NativeMemory.free(ptr);

        assertEquals(0, getUsedMemory());
    }

    @Test
    public void testRealloc() {
        assertEquals(0, getUsedMemory());
        Pointer ptr = NativeMemory.malloc(WordFactory.unsigned(ALLOCATION_SIZE), NmtCategory.Code);

        assertEquals(getUsedMemory(), ALLOCATION_SIZE);
        assertTrue(getUsedMemory() > 0);

        Pointer reallocPtr = NativeMemory.realloc(ptr, WordFactory.unsigned(REALLOC_SIZE), NmtCategory.Code);

        assertEquals(REALLOC_SIZE, getUsedMemory());

        NativeMemory.free(reallocPtr);
        assertEquals(0, getUsedMemory());
    }

    private static long getUsedMemory() {
        return NativeMemoryTracking.singleton().getUsedMemory(NmtCategory.Code);
    }

    @Test
    public void testReserveAndCommitBasic() {
        assertTrue("Test should start with no memory already allocated in the test category.", NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) == 0);

        Pointer reservePtr = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(reserveSize), HeapParameters.getAlignedHeapChunkSize(), false,
                        NmtCategory.Code);
        assertTrue(NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) >= reserveSize);
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);

        Pointer commitPtr = VirtualMemoryProvider.get().commit(reservePtr, WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        assertTrue(NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) >= reserveSize);
        assertTrue("Expected committed size:" + commitSize + " Actual:" + NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code),
                        NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) >= commitSize);

        assertTrue("uncommit op failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr, WordFactory.unsigned(commitSize)));
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);

        assertTrue("free op failed", 0 == VirtualMemoryProvider.get().free(reservePtr, WordFactory.unsigned(reserveSize)));
        assertTrue("After freeing memory for test, test category should have size 0. Actual:" + NativeMemoryTracking.getReservedByCategory(NmtCategory.Code),
                        NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) == 0);
    }

    @Test
    public void testSmallAlignmentReserveAndCommit() {
        assertTrue("Test should start with no memory already allocated in the test category.", NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) == 0);

        Pointer reservePtr = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(reserveSize), VirtualMemoryProvider.get().getGranularity().unsignedDivide(2), false,
                        NmtCategory.Code);
        assertTrue(NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) >= reserveSize);
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);

        Pointer commitPtr = VirtualMemoryProvider.get().commit(reservePtr, WordFactory.unsigned(commitSize * 3), 0, NmtCategory.Code);
        assertTrue("Unable to commit.", commitPtr.isNonNull());
        assertTrue("Expected committed size:" + commitSize * 3 + " Actual:" + NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code),
                        NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) >= commitSize * 3);

        assertTrue("Uncommit failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr, WordFactory.unsigned(commitSize * 3)));
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);

        assertTrue("Release failed.", 0 == VirtualMemoryProvider.get().free(reservePtr, WordFactory.unsigned(reserveSize)));
        assertTrue("After freeing memory for test, test category should have size 0. Actual:" + NativeMemoryTracking.getReservedByCategory(NmtCategory.Code),
                        NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) == 0);
    }

    @Test
    public void testReserveFree() {
        Pointer reservePtr1 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(reserveSize), VirtualMemoryProvider.get().getGranularity(), false,
                        NmtCategory.Code);
        assertTrue(NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) >= reserveSize);

        Pointer reservePtr2 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(reserveSize), VirtualMemoryProvider.get().getGranularity(), false,
                        NmtCategory.Code);
        assertTrue(NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) >= reserveSize * 2);

        Pointer reservePtr3 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(reserveSize), VirtualMemoryProvider.get().getGranularity(), false,
                        NmtCategory.Code);
        assertTrue(NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) >= reserveSize * 3);

        assertTrue("Free failed", 0 == VirtualMemoryProvider.get().free(reservePtr1, WordFactory.unsigned(reserveSize)));
        assertTrue("Free failed", 0 == VirtualMemoryProvider.get().free(reservePtr2, WordFactory.unsigned(reserveSize)));
        assertTrue("Free failed", 0 == VirtualMemoryProvider.get().free(reservePtr3, WordFactory.unsigned(reserveSize)));
        assertTrue("After releasing memory for test, test category should have size 0. Actual:" + NativeMemoryTracking.getReservedByCategory(NmtCategory.Code),
                        NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) == 0);
    }

    @Test
    public void testAlternatingReserveFree() {
        assertTrue("Test should start with no memory already allocated in the test category.", NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) == 0);

        Pointer reservePtr1 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(reserveSize), HeapParameters.getAlignedHeapChunkSize(), false,
                        NmtCategory.Code);
        assertTrue("Free failed", 0 == VirtualMemoryProvider.get().free(reservePtr1, WordFactory.unsigned(reserveSize)));

        Pointer reservePtr2 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(reserveSize), HeapParameters.getAlignedHeapChunkSize(), false,
                        NmtCategory.Code);
        assertTrue("Free failed", 0 == VirtualMemoryProvider.get().free(reservePtr2, WordFactory.unsigned(reserveSize)));

        Pointer reservePtr3 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(reserveSize), HeapParameters.getAlignedHeapChunkSize(), false,
                        NmtCategory.Code);
        assertTrue("Free failed", 0 == VirtualMemoryProvider.get().free(reservePtr3, WordFactory.unsigned(reserveSize)));

        assertTrue("After freeing memory for test, test category should have size 0. Actual:" + NativeMemoryTracking.getReservedByCategory(NmtCategory.Code),
                        NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) == 0);
    }

    @Test
    public void testGappedCommits() {
        Pointer reservePtr = beginVirtualMemoryTest();

        Pointer commitPtr1 = VirtualMemoryProvider.get().commit(reservePtr, WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        long recordedCommittedSize1 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize + " Actual:" + recordedCommittedSize1, recordedCommittedSize1 == commitSize);

        /* Commit again leaving a big gap between the previous regions. */
        Pointer commitPtr2 = VirtualMemoryProvider.get().commit(commitPtr1.add(commitSize * 3), WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        long recordedCommittedSize4 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize * 2 + " Actual:" + recordedCommittedSize4, recordedCommittedSize4 >= commitSize * 2);

        assertTrue("uncommit op failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr2, WordFactory.unsigned(commitSize)));
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == commitSize);

        assertTrue("uncommit op failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr1, WordFactory.unsigned(commitSize)));
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);

        endVirtualMemoryTest(reservePtr);
    }

    @Test
    public void testCommitUncommitThroughFree() {
        Pointer reservePtr = beginVirtualMemoryTest();

        VirtualMemoryProvider.get().commit(reservePtr, WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        long recordedCommittedSize1 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize + " Actual:" + recordedCommittedSize1, recordedCommittedSize1 == commitSize);

        /* Commit again leaving a large gap between the previous regions. */
        VirtualMemoryProvider.get().commit(reservePtr.add(commitSize * 3), WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        long recordedCommittedSize2 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize * 2 + " Actual:" + recordedCommittedSize2, recordedCommittedSize2 >= commitSize * 2);

        endVirtualMemoryTest(reservePtr);
    }

    @Test
    public void testAdjacentCommits() {
        Pointer reservePtr = beginVirtualMemoryTest();

        Pointer commitPtr1 = VirtualMemoryProvider.get().commit(reservePtr, WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        long recordedCommittedSize1 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize + " Actual:" + recordedCommittedSize1, recordedCommittedSize1 == commitSize);

        /* Commit again leaving a no gap between the previous regions. */
        Pointer commitPtr2 = VirtualMemoryProvider.get().commit(commitPtr1.add(commitSize), WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        long recordedCommittedSize4 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize * 2 + " Actual:" + recordedCommittedSize4, recordedCommittedSize4 >= commitSize * 2);

        assertTrue("uncommit op failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr2, WordFactory.unsigned(commitSize)));
        assertTrue("Expected committed size:" + commitSize + " Actual:" + NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code),
                        NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == commitSize);

        assertTrue("uncommit op failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr1, WordFactory.unsigned(commitSize)));
        assertTrue("Expected committed size:" + 0 + " Actual:" + NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code), NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);

        endVirtualMemoryTest(reservePtr);
    }

    @Test
    public void testAdjacentAndGappedCommits() {
        Pointer reservePtr = beginVirtualMemoryTest();

        Pointer commitPtr1 = VirtualMemoryProvider.get().commit(reservePtr, WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        long recordedCommittedSize1 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize + " Actual:" + recordedCommittedSize1, recordedCommittedSize1 == commitSize);

        /* Commit again adjacent to the previous committed region. */
        Pointer commitPtr2 = VirtualMemoryProvider.get().commit(commitPtr1.add(commitSize), WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        long recordedCommittedSize2 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize * 2 + " Actual:" + recordedCommittedSize2, recordedCommittedSize2 >= commitSize * 2);

        /* Commit again leaving a big gap between the previous regions. */
        Pointer commitPtr3 = VirtualMemoryProvider.get().commit(commitPtr2.add(commitSize * 3), WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        long recordedCommittedSize3 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize * 3 + " Actual:" + recordedCommittedSize3, recordedCommittedSize3 >= commitSize * 3);

        assertTrue("uncommit op failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr3, WordFactory.unsigned(commitSize)));
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) >= commitSize * 2);
        assertTrue("uncommit op failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr2, WordFactory.unsigned(commitSize)));
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) >= commitSize);
        assertTrue("uncommit op failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr1, WordFactory.unsigned(commitSize)));
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);

        endVirtualMemoryTest(reservePtr);
    }

    @Test
    public void testFullyOverlappingCommits() {
        Pointer reservePtr = beginVirtualMemoryTest();

        Pointer commitPtr1 = VirtualMemoryProvider.get().commit(reservePtr, WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        long recordedCommittedSize1 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize + " Actual:" + recordedCommittedSize1, recordedCommittedSize1 == commitSize);

        /* Commit again completely overlapping the previous committed region. */
        Pointer commitPtr2 = VirtualMemoryProvider.get().commit(commitPtr1, WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        long recordedCommittedSize2 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected that committed size remained the same:" + commitSize + " Actual:" + recordedCommittedSize2, recordedCommittedSize2 == commitSize);

        assertTrue("Uncommit failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr2, WordFactory.unsigned(commitSize)));
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);

        endVirtualMemoryTest(reservePtr);
    }

    /**
     * Test committing regions with unordered start addresses. Then uncommit in an unordered way.
     * |commitPtr1|commitPtr3| | | |commitPtr2| | |
     */
    @Test
    public void testUnorderedCommits() {
        Pointer reservePtr = beginVirtualMemoryTest();

        Pointer commitPtr1 = VirtualMemoryProvider.get().commit(reservePtr, WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        long recordedCommittedSize1 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize + " Actual:" + recordedCommittedSize1, recordedCommittedSize1 == commitSize);

        /* Commit again leaving a big gap between the previous regions. */
        Pointer commitPtr2 = VirtualMemoryProvider.get().commit(reservePtr.add(commitSize * 5), WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        long recordedCommittedSize2 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize * 2 + " Actual:" + recordedCommittedSize2, recordedCommittedSize2 >= commitSize * 2);

        // Commit again adjacent to the first committed region
        Pointer commitPtr3 = VirtualMemoryProvider.get().commit(commitPtr1.add(commitSize), WordFactory.unsigned(commitSize), 0, NmtCategory.Code);
        long recordedCommittedSize3 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize * 3 + " Actual:" + recordedCommittedSize3, recordedCommittedSize3 >= commitSize * 3);

        // Uncommit the previously committed region
        assertTrue("Uncommit failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr3, WordFactory.unsigned(commitSize)));
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) >= commitSize * 2);
        assertTrue("Uncommit failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr1, WordFactory.unsigned(commitSize)));
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) >= commitSize);
        assertTrue("Uncommit failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr2, WordFactory.unsigned(commitSize)));
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);

        endVirtualMemoryTest(reservePtr);
    }

    @Test
    public void testPartiallyOverlappingCommits() {
        Pointer reservePtr = beginVirtualMemoryTest();

        // Commit from within the middle of the reserved region
        Pointer commitPtr1 = VirtualMemoryProvider.get().commit(reservePtr.add(commitSize), WordFactory.unsigned(commitSize * 2), 0, NmtCategory.Code);
        long recordedCommittedSize1 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize * 2 + " Actual:" + recordedCommittedSize1, recordedCommittedSize1 == commitSize * 2);

        // Commit again partially overlapping to the previous committed region
        VirtualMemoryProvider.get().commit(commitPtr1.add(commitSize), WordFactory.unsigned(commitSize * 2), 0, NmtCategory.Code);
        long recordedCommittedSize2 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected that committed size remained the same:" + commitSize * 3 + " Actual:" + recordedCommittedSize2, recordedCommittedSize2 == commitSize * 3);

        endVirtualMemoryTest(reservePtr);
    }

    @Test
    public void testPartialUncommit() {
        Pointer reservePtr = beginVirtualMemoryTest();

        /* Commit from within the middle of the reserved region. */
        Pointer commitPtr1 = VirtualMemoryProvider.get().commit(reservePtr.add(commitSize), WordFactory.unsigned(commitSize * 2), 0, NmtCategory.Code);
        long recordedCommittedSize1 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize * 2 + " Actual:" + recordedCommittedSize1, recordedCommittedSize1 == commitSize * 2);

        /*
         * Uncommit only half of the committed region. Half of the target region to uncommit is
         * actually not committed.
         */
        assertTrue("Uncommit failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr1.add(commitSize), WordFactory.unsigned(commitSize * 2)));
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == commitSize);

        endVirtualMemoryTest(reservePtr);
    }

    @Test
    public void testPartialUncommitOverlappingMultipleRegions() {
        Pointer reservePtr = beginVirtualMemoryTest();

        Pointer commitPtr1 = VirtualMemoryProvider.get().commit(reservePtr, WordFactory.unsigned(commitSize * 2), 0, NmtCategory.Code);
        long recordedCommittedSize1 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize * 2 + " Actual:" + recordedCommittedSize1, recordedCommittedSize1 == commitSize * 2);

        /* Commit again adjacent to the previous region */
        VirtualMemoryProvider.get().commit(commitPtr1.add(commitSize * 2), WordFactory.unsigned(commitSize * 2), 0, NmtCategory.Code);
        long recordedCommittedSize4 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue("Expected committed size:" + commitSize * 4 + " Actual:" + recordedCommittedSize4, recordedCommittedSize4 >= commitSize * 4);

        /* Uncommit a region overlapping both previously committed regions. */
        assertTrue("Uncommit failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr1.add(commitSize), WordFactory.unsigned(commitSize * 2)));
        assertTrue("Expected committed size:" + commitSize * 2 + " Actual:" + NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code),
                        NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == commitSize * 2);

        endVirtualMemoryTest(reservePtr);
    }

    @Test
    public void testReservedPeak() {
        assertTrue("Test should start with no memory already allocated in the test category.", NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) == 0);
        assertTrue("Test should start with no memory already allocated in the test category.", NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);

        long initialPeak = NativeMemoryTracking.getPeakReservedByCategory(NmtCategory.Code);

        Pointer reservePtr1 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(initialPeak), VirtualMemoryProvider.get().getGranularity(), false,
                        NmtCategory.Code);
        assertTrue(NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) >= initialPeak);

        Pointer reservePtr2 = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(initialPeak), VirtualMemoryProvider.get().getGranularity(), false,
                        NmtCategory.Code);
        long peakReserved = NativeMemoryTracking.getReservedByCategory(NmtCategory.Code);
        assertTrue(NativeMemoryTracking.getPeakReservedByCategory(NmtCategory.Code) == peakReserved);

        assertTrue("Free failed", 0 == VirtualMemoryProvider.get().free(reservePtr1, WordFactory.unsigned(initialPeak)));
        assertTrue("Free failed", 0 == VirtualMemoryProvider.get().free(reservePtr2, WordFactory.unsigned(initialPeak)));

        assertTrue(NativeMemoryTracking.getPeakReservedByCategory(NmtCategory.Code) == peakReserved);
        assertTrue("After releasing memory for test, test category should have size 0. Actual:" + NativeMemoryTracking.getReservedByCategory(NmtCategory.Code),
                        NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) == 0);
    }

    @Test
    public void testCommittedPeak() {
        long initialPeak = NativeMemoryTracking.getPeakCommittedByCategory(NmtCategory.Code);
        long largeReserveSize = initialPeak * 2;
        int largeCommitSize = (int) initialPeak;
        Pointer reservePtr = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(largeReserveSize), HeapParameters.getAlignedHeapChunkSize(), false,
                        NmtCategory.Code);

        Pointer commitPtr1 = VirtualMemoryProvider.get().commit(reservePtr, WordFactory.unsigned(largeCommitSize), 0, NmtCategory.Code);
        long recordedCommittedSize1 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue(NativeMemoryTracking.getPeakCommittedByCategory(NmtCategory.Code) == recordedCommittedSize1);

        Pointer commitPtr2 = VirtualMemoryProvider.get().commit(commitPtr1.add(largeCommitSize), WordFactory.unsigned(largeCommitSize), 0, NmtCategory.Code);
        long recordedCommittedSize2 = NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code);
        assertTrue(NativeMemoryTracking.getPeakCommittedByCategory(NmtCategory.Code) == recordedCommittedSize2);

        assertTrue("Uncommit failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr1, WordFactory.unsigned(largeCommitSize)));
        assertTrue("Uncommit failed", 0 == VirtualMemoryProvider.get().uncommit(commitPtr2, WordFactory.unsigned(largeCommitSize)));
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);
        assertTrue(NativeMemoryTracking.getPeakCommittedByCategory(NmtCategory.Code) == recordedCommittedSize2);

        assertTrue("Free failed.", 0 == VirtualMemoryProvider.get().free(reservePtr, WordFactory.unsigned(largeReserveSize)));
        assertTrue("After freeing memory for test, test category should have size 0.", NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) == 0);
        assertTrue("After freeing memory for test, test category committed size should be 0.", NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);
    }

    /**
     * A convenience method that should be used with
     * {@link com.oracle.svm.test.nmt.NativeMemoryTrackingTests#endVirtualMemoryTest(Pointer)}.
     */
    private Pointer beginVirtualMemoryTest() {
        assertTrue("Test should start with no memory already reserved in the test category.", NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) == 0);
        assertTrue("Test should start with no memory already committed in the test category.", NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);

        /* Reserve some memory. */
        Pointer reservePtr = VirtualMemoryProvider.get().reserve(WordFactory.unsigned(reserveSize), HeapParameters.getAlignedHeapChunkSize(), false,
                        NmtCategory.Code);
        assertTrue(NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) >= reserveSize);
        assertTrue(NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);
        return reservePtr;
    }

    /**
     * A convenience method that should be used with
     * {@link com.oracle.svm.test.nmt.NativeMemoryTrackingTests#beginVirtualMemoryTest()}.
     */
    private void endVirtualMemoryTest(Pointer reservePtr) {
        /* Free the reserved region, which should also uncommit contained committed regions. */
        assertTrue("Free failed.", 0 == VirtualMemoryProvider.get().free(reservePtr, WordFactory.unsigned(reserveSize)));
        assertTrue("After freeing memory for test, test category should have size 0.", NativeMemoryTracking.getReservedByCategory(NmtCategory.Code) == 0);
        assertTrue("After freeing memory for test, test category committed size should be 0.", NativeMemoryTracking.getCommittedByCategory(NmtCategory.Code) == 0);

    }
}
