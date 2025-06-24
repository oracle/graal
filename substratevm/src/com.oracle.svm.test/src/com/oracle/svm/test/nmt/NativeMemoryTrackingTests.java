/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
import org.junit.Test;

import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.nmt.NativeMemoryTracking;
import com.oracle.svm.core.nmt.NmtCategory;

import jdk.graal.compiler.word.Word;

public class NativeMemoryTrackingTests {
    private static final int K = 1024;
    private static final int M = 1024 * 1024;

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

        assertEquals(16 * K, getUsedMemory());
        assertTrue(getUsedMemory() > 0);

        Pointer reallocPtr = NativeMemory.realloc(ptr, Word.unsigned(8 * K), NmtCategory.Code);
        assertEquals(8 * K, getUsedMemory());

        NativeMemory.free(reallocPtr);
        assertEquals(0, getUsedMemory());
    }

    @Test
    public void testPeakTracking() {
        assertEquals(0, getUsedMemory());

        Pointer ptr1 = NativeMemory.malloc(M, NmtCategory.Code);
        long peakUsed = NativeMemoryTracking.singleton().getPeakMallocMemory(NmtCategory.Code);
        assertEquals(M, peakUsed);

        Pointer ptr2 = NativeMemory.malloc(M, NmtCategory.Code);
        peakUsed = NativeMemoryTracking.singleton().getPeakMallocMemory(NmtCategory.Code);
        assertEquals(2 * M, peakUsed);

        NativeMemory.free(ptr1);
        ptr1 = Word.nullPointer();

        NativeMemory.free(ptr2);
        ptr2 = Word.nullPointer();

        assertEquals(0, getUsedMemory());
        assertEquals(2 * M, NativeMemoryTracking.singleton().getPeakMallocMemory(NmtCategory.Code));

        Pointer ptr3 = NativeMemory.malloc(3 * M, NmtCategory.Code);
        peakUsed = NativeMemoryTracking.singleton().getPeakMallocMemory(NmtCategory.Code);
        assertEquals(3 * M, peakUsed);

        NativeMemory.free(ptr3);
        ptr3 = Word.nullPointer();

        assertEquals(0, getUsedMemory());
        assertEquals(3 * M, NativeMemoryTracking.singleton().getPeakMallocMemory(NmtCategory.Code));
    }

    private static long getUsedMemory() {
        return NativeMemoryTracking.singleton().getMallocMemory(NmtCategory.Code);
    }

    @Test
    public void testVirtualMemoryTracking() {
        // The application should already be using some virtual memory for the heap.
        assertTrue(NativeMemoryTracking.singleton().getReservedVirtualMemory(NmtCategory.ImageHeap) > 0);
        assertTrue(NativeMemoryTracking.singleton().getReservedVirtualMemory(NmtCategory.JavaHeap) > 0);

        assertTrue(NativeMemoryTracking.singleton().getCommittedVirtualMemory(NmtCategory.JavaHeap) > 0);
        assertTrue(NativeMemoryTracking.singleton().getCommittedVirtualMemory(NmtCategory.ImageHeap) > 0);

        assertTrue(NativeMemoryTracking.singleton().getPeakCommittedVirtualMemory(NmtCategory.JavaHeap) > 0);
        assertTrue(NativeMemoryTracking.singleton().getPeakCommittedVirtualMemory(NmtCategory.ImageHeap) > 0);

        assertTrue(NativeMemoryTracking.singleton().getPeakReservedVirtualMemory(NmtCategory.JavaHeap) > 0);
        assertTrue(NativeMemoryTracking.singleton().getPeakReservedVirtualMemory(NmtCategory.ImageHeap) > 0);

        // determine baseline
        long codeReservedVirtualMemory = NativeMemoryTracking.singleton().getReservedVirtualMemory(NmtCategory.Code);
        long codeCommittedVirtualMemory = NativeMemoryTracking.singleton().getCommittedVirtualMemory(NmtCategory.Code);
        long codePeakReservedVirtualMemory = NativeMemoryTracking.singleton().getPeakReservedVirtualMemory(NmtCategory.Code);
        long codePeakCommittedVirtualMemory = NativeMemoryTracking.singleton().getPeakCommittedVirtualMemory(NmtCategory.Code);

        // Use some memory
        NativeMemoryTracking.singleton().trackReserve(1024, NmtCategory.Code);
        NativeMemoryTracking.singleton().trackCommit(512, NmtCategory.Code);
        assertEquals(codeReservedVirtualMemory + 1024, NativeMemoryTracking.singleton().getReservedVirtualMemory(NmtCategory.Code));
        assertEquals(codeCommittedVirtualMemory + 512, NativeMemoryTracking.singleton().getCommittedVirtualMemory(NmtCategory.Code));

        // Uncommit and check peaks
        NativeMemoryTracking.singleton().trackUncommit(512, NmtCategory.Code);
        NativeMemoryTracking.singleton().trackFree(1024, NmtCategory.Code);
        assertEquals(codePeakReservedVirtualMemory + 1024, NativeMemoryTracking.singleton().getPeakReservedVirtualMemory(NmtCategory.Code));
        assertEquals(codePeakCommittedVirtualMemory + 512, NativeMemoryTracking.singleton().getPeakCommittedVirtualMemory(NmtCategory.Code));
    }
}
