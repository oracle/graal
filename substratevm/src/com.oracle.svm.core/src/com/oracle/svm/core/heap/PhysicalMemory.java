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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Containers;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.stack.StackOverflowCheck;
import com.oracle.svm.core.thread.PlatformThreads;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;
import com.sun.management.OperatingSystemMXBean;

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

    private static final long K = 1024;

    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final UnsignedWord UNSET_SENTINEL = UnsignedUtils.MAX_VALUE;
    private static UnsignedWord cachedSize = UNSET_SENTINEL;

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isInitialized() {
        return cachedSize != UNSET_SENTINEL;
    }

    public static boolean isInitializationInProgress() {
        return LOCK.isHeldByCurrentThread();
    }

    @Uninterruptible(reason = "May only be called during early startup.")
    public static void setSize(UnsignedWord value) {
        VMError.guarantee(!isInitialized(), "PhysicalMemorySize must not be initialized yet.");
        cachedSize = value;
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

        if (!isInitialized()) {
            long memoryLimit = SubstrateOptions.MaxRAM.getValue();
            if (memoryLimit > 0) {
                cachedSize = WordFactory.unsigned(memoryLimit);
            } else {
                LOCK.lock();
                try {
                    if (!isInitialized()) {
                        memoryLimit = Containers.memoryLimitInBytes();
                        cachedSize = memoryLimit > 0
                                        ? WordFactory.unsigned(memoryLimit)
                                        : ImageSingletons.lookup(PhysicalMemorySupport.class).size();
                    }
                } finally {
                    LOCK.unlock();
                }
            }
        }

        return cachedSize;
    }

    /** Returns the amount of used physical memory in bytes, or -1 if not supported. */
    public static long usedSize() {
        // Windows, macOS, and containerized Linux use the OS bean.
        if (Platform.includedIn(Platform.WINDOWS.class) ||
                        Platform.includedIn(Platform.MACOS.class) ||
                        (Containers.isContainerized() && Containers.memoryLimitInBytes() > 0)) {
            OperatingSystemMXBean osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            return osBean.getTotalMemorySize() - osBean.getFreeMemorySize();
        }

        // Non-containerized Linux uses /proc/meminfo.
        if (Platform.includedIn(Platform.LINUX.class)) {
            return getUsedSizeFromProcMemInfo();
        }

        return -1L;
    }

    // Will be removed as part of GR-51479.
    private static long getUsedSizeFromProcMemInfo() {
        try {
            List<String> lines = readAllLines("/proc/meminfo");
            for (String line : lines) {
                if (line.contains("MemAvailable")) {
                    return size().rawValue() - parseFirstNumber(line) * K;
                }
            }
        } catch (Exception e) {
            /* Nothing to do. */
        }
        return -1L;
    }

    private static List<String> readAllLines(String fileName) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(new FileReader(fileName, StandardCharsets.UTF_8))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** Parses the first number in the String as a long value. */
    private static long parseFirstNumber(String str) {
        int firstDigit = -1;
        int lastDigit = -1;

        for (int i = 0; i < str.length(); i++) {
            if (Character.isDigit(str.charAt(i))) {
                if (firstDigit == -1) {
                    firstDigit = i;
                }
                lastDigit = i;
            } else if (firstDigit != -1) {
                break;
            }
        }

        if (firstDigit >= 0) {
            String number = str.substring(firstDigit, lastDigit + 1);
            return Long.parseLong(number);
        }
        return -1;
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
