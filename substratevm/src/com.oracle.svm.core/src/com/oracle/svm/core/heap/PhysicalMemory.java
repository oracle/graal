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

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicInteger;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

/**
 * Contains static methods to get configuration of physical memory.
 */
public class PhysicalMemory {

    /** Implemented by operating-system specific code. */
    protected interface PhysicalMemorySupport {

        default boolean hasSize() {
            throw VMError.shouldNotReachHere("Unused, will be removed");
        }

        /** Get the size of physical memory from the OS. */
        UnsignedWord size();
    }

    /** A sentinel unset value. */
    private static final UnsignedWord UNSET_SENTINEL = UnsignedUtils.MAX_VALUE;

    /** Prevent recursive initialization in {@link #tryInitialize}. */
    static AtomicInteger initializing = new AtomicInteger(0);

    /** The cached size of physical memory, or an unset value. */
    private static UnsignedWord cachedSize = UNSET_SENTINEL;

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
            throw VMError.shouldNotReachHere("Accessing the physical memory size requires allocation and synchronization");
        }

        if (!isInitialized()) {
            initializing.incrementAndGet();
            try {
                /*
                 * Multiple threads can race to initialize the cache. This is OK because all of them
                 * will compute the same value.
                 */
                doInitialize();
            } finally {
                initializing.decrementAndGet();
            }
        }

        return cachedSize;
    }

    /**
     * Tries to initialize the cached memory size. If the initialization is not possible, e.g.,
     * because the call is from within a VMOperation, the method does nothing.
     */
    public static void tryInitialize() {
        if (isInitialized() || isInitializationDisallowed()) {
            return;
        }

        /*
         * We need to prevent recursive calls of the initialization. We also want only one thread to
         * try the initialization. Since this is an optional initialization, we also do not need to
         * wait until the other thread has finished the initialization. Initialization can be quite
         * heavyweight and involve reading configuration files.
         */
        if (initializing.compareAndSet(0, 1)) {
            try {
                doInitialize();
            } finally {
                initializing.decrementAndGet();
            }
        }
    }

    /**
     * Returns true if the memory size has been queried from the OS, i.e., if
     * {@link #getCachedSize()} can be called.
     */
    public static boolean isInitialized() {
        return cachedSize != UNSET_SENTINEL;
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
        return Heap.getHeap().isAllocationDisallowed() || VMOperation.isInProgress() || !JavaThreads.currentJavaThreadInitialized();
    }

    @RestrictHeapAccess(access = RestrictHeapAccess.Access.UNRESTRICTED, overridesCallers = true, reason = "Only called if allocation is allowed.")
    private static void doInitialize() {
        cachedSize = ImageSingletons.lookup(PhysicalMemorySupport.class).size();
    }
}
