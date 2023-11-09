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

import com.oracle.svm.core.nmt.NmtFlag;

import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.svm.core.nmt.NativeMemoryTracking;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.UnmanagedMemorySupport;
import org.graalvm.word.WordFactory;
import org.graalvm.word.Pointer;
import static org.junit.Assert.assertTrue;

public class TestBasic {
    private static final int ALLOCATION_SIZE = 1024 * 16;
    private static final int RESERVE_SIZE = ALLOCATION_SIZE;
    private static final int REALLOC_SIZE = ALLOCATION_SIZE / 2;
    private static final int COMMIT_SIZE = RESERVE_SIZE / 2;

    /**
     * This both initializes NMT and does some basic checks to verify pre-init allocations are
     * handled.
     */
    @BeforeClass
    public static void setup() {
        // Allocate some memory and check it's being tracked.
        Pointer ptr = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(ALLOCATION_SIZE), NmtFlag.mtTest.ordinal());
        assertTrue(NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == ALLOCATION_SIZE);

        // Realloc previously allocated memory and check NMT has tracked it correctly
        Pointer reallocPtr = ImageSingletons.lookup(UnmanagedMemorySupport.class).realloc(ptr, WordFactory.unsigned(REALLOC_SIZE), NmtFlag.mtTest.ordinal());
        assertTrue(NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == REALLOC_SIZE);

        // Free the memory and ensure the tracking is now zeroed.
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(reallocPtr);
        assertTrue(NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == 0);

        // Allocate a new block that will live across the initialization boundary
        ptr = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(ALLOCATION_SIZE), NmtFlag.mtTest.ordinal());

        // We must initialize NMT here so that other tests can use it.
        NativeMemoryTracking.initialize(true);

        // Reallocate a block that has lived across the initialization boundary. Verify its been
        // recorded.
        reallocPtr = ImageSingletons.lookup(UnmanagedMemorySupport.class).realloc(ptr, WordFactory.unsigned(REALLOC_SIZE), NmtFlag.mtTest.ordinal());
        assertTrue(NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == REALLOC_SIZE);

        // Free the memory and verify we're back at zero.
        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(reallocPtr);
        assertTrue(NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == 0);
    }

    @Test
    public void testMalloc() throws Throwable {
        assertTrue("Test should start with no memory already allocated in the mtTest category.", NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == 0);

        Pointer ptr = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(ALLOCATION_SIZE), NmtFlag.mtTest.ordinal());
        assertTrue(NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == ALLOCATION_SIZE);
        assertTrue(NativeMemoryTracking.getMallocByCategory(NmtFlag.mtNMT) > 0);

        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(ptr);

        assertTrue("After freeing memory for test, mtTest category should have size 0.", NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == 0);
    }

    @Test
    public void testCalloc() throws Throwable {
        assertTrue("Test should start with no memory already allocated in the mtTest category.", NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == 0);
        Pointer ptr = ImageSingletons.lookup(UnmanagedMemorySupport.class).calloc(WordFactory.unsigned(ALLOCATION_SIZE), NmtFlag.mtTest.ordinal());

        assertTrue(NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == ALLOCATION_SIZE);
        assertTrue(NativeMemoryTracking.getMallocByCategory(NmtFlag.mtNMT) > 0);

        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(ptr);

        assertTrue("After freeing memory for test, mtTest category should have size 0.", NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == 0);
    }

    @Test
    public void testRealloc() throws Throwable {
        assertTrue("Test should start with no memory already allocated in the mtTest category.", NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == 0);
        Pointer ptr = ImageSingletons.lookup(UnmanagedMemorySupport.class).malloc(WordFactory.unsigned(ALLOCATION_SIZE), NmtFlag.mtTest.ordinal());

        assertTrue(NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == ALLOCATION_SIZE);
        assertTrue(NativeMemoryTracking.getMallocByCategory(NmtFlag.mtNMT) > 0);

        Pointer reallocPtr = ImageSingletons.lookup(UnmanagedMemorySupport.class).realloc(ptr, WordFactory.unsigned(REALLOC_SIZE), NmtFlag.mtTest.ordinal());

        assertTrue(NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == REALLOC_SIZE);

        ImageSingletons.lookup(UnmanagedMemorySupport.class).free(reallocPtr);
        assertTrue("After freeing memory for test, mtTest category should have size 0.", NativeMemoryTracking.getMallocByCategory(NmtFlag.mtTest) == 0);
    }
}
