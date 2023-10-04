/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Containers;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicInteger;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

/**
 * Contains static methods to get configuration of physical memory.
 */
public class PhysicalMemory {

    /** Implemented by operating-system specific code. */
    public interface PhysicalMemorySupport {

        /* Will be removed in GR-48001. */
        default boolean hasSize() {
            throw VMError.shouldNotReachHere("Unused, will be removed");
        }

        /** Get the size of physical memory from the OS. */
        UnsignedWord size();
    }

    private static final CountDownLatch CACHED_SIZE_AVAIL_LATCH = new CountDownLatch(1);
    private static final AtomicInteger INITIALIZING = new AtomicInteger(0);
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final UnsignedWord UNSET_SENTINEL = UnsignedUtils.MAX_VALUE;
    private static UnsignedWord cachedSize = UNSET_SENTINEL;

    public static boolean isInitialized() {
        return INITIALIZING.get() > 1;
    }

    /**
     *
     * @return {@code true} when PhycialMemory.size() is still initializing
     */
    private static boolean isInitializing() {
        return INITIALIZING.get() == 1;
    }

    /**
     * Returns the size of physical memory in bytes, querying it from the OS if it has not been
     * initialized yet.
     *
     * This method might allocate and use synchronization, so it is not safe to call it from inside
     * a VMOperation or during early stages of a thread or isolate.
     */
    public static UnsignedWord size() {
        if (isInitializationDisallowed()) {
            /*
             * Note that we want to have this safety check even when the cache is already
             * initialized, so that we always detect wrong usages that could lead to problems.
             */
            throw VMError.shouldNotReachHere("Accessing the physical memory size may require allocation and synchronization");
        }

        LOCK.lock();
        try {
            if (!isInitialized()) {
                if (isInitializing()) {
                    /*
                     * Recursive initializations need to wait for the one initializing thread to
                     * finish so as to get correct reads of the cachedSize value.
                     */
                    try {
                        boolean expired = !CACHED_SIZE_AVAIL_LATCH.await(1L, TimeUnit.SECONDS);
                        if (expired) {
                            throw new InternalError("Expired latch!");
                        }
                        VMError.guarantee(cachedSize != UNSET_SENTINEL, "Expected cached size to be set");
                        return cachedSize;
                    } catch (InterruptedException e) {
                        throw VMError.shouldNotReachHere("Interrupt on countdown latch!");
                    }
                }
                INITIALIZING.incrementAndGet();
                long memoryLimit = SubstrateOptions.MaxRAM.getValue();
                if (memoryLimit > 0) {
                    cachedSize = WordFactory.unsigned(memoryLimit);
                } else {
                    memoryLimit = Containers.memoryLimitInBytes();
                    cachedSize = memoryLimit > 0
                                    ? WordFactory.unsigned(memoryLimit)
                                    : ImageSingletons.lookup(PhysicalMemorySupport.class).size();
                }
                // Now that we have set the cachedSize let other threads know it's
                // available to use.
                INITIALIZING.incrementAndGet();
                CACHED_SIZE_AVAIL_LATCH.countDown();
            }
        } finally {
            LOCK.unlock();
        }

        return cachedSize;
    }

    /**
     * Returns the size of physical memory in bytes that has been previously cached. This method
     * must not be called if {@link #isInitialized()} is still false.
     */
    public static UnsignedWord getCachedSize() {
        VMError.guarantee(isInitialized(), "Cached physical memory size is not available");
        return cachedSize;
    }

    private static boolean isInitializationDisallowed() {
        return Heap.getHeap().isAllocationDisallowed() || VMOperation.isInProgress() || !PlatformThreads.isCurrentAssigned() || StackOverflowCheck.singleton().isYellowZoneAvailable();
    }
}
