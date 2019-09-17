/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.posix.linux;

import java.io.FileInputStream;
import java.io.IOException;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.InternalPlatform;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.RestrictHeapAccess;
import com.oracle.svm.core.annotate.RestrictHeapAccess.Access;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.jdk.UninterruptibleUtils.AtomicInteger;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.thread.JavaThreads;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.UnsignedUtils;

@Platforms(InternalPlatform.LINUX_AND_JNI.class)
class LinuxPhysicalMemory extends PhysicalMemory {

    static class PhysicalMemorySupportImpl implements PhysicalMemorySupport {

        /** A sentinel unset value. */
        static final long UNSET_SENTINEL = Long.MIN_VALUE;
        /** Prevent recursive invocation of size() from initializeSize(). */
        static AtomicInteger initializeSize = new AtomicInteger(0);

        /** The cached size of physical memory, or an unset value. */
        long cachedSize = UNSET_SENTINEL;

        /** Get the size of physical memory. */
        @Override
        public UnsignedWord size() {
            /* If I have a cached a size, use that. */
            if (hasSize()) {
                return getSize();
            }
            /*
             * The size initialization code below requires synchronized and therefore must not run
             * inside a VMOperation. Also if we can't allocate we have to prevent it from running.
             */
            if (Heap.getHeap().isAllocationDisallowed() || VMOperation.isInProgress() || !JavaThreads.currentJavaThreadInitialized() || !initializeSize.compareAndSet(0, 1)) {
                return UnsignedUtils.MAX_VALUE;
            }
            /* Compute and cache the physical memory size. Races are idempotent. */
            initializeSize();
            initializeSize.set(0);
            return getSize();
        }

        /** Has the size of physical memory been computed? */
        @Override
        public boolean hasSize() {
            return (cachedSize != UNSET_SENTINEL);
        }

        /** Update the cached size. */
        void setSize(long value) {
            cachedSize = value;
        }

        /** Get the cached size. */
        UnsignedWord getSize() {
            assert hasSize() : "LinuxPhysicalMemory.PhysicalMemorySupportImpl.geSize: cachedSize has no value.";
            return WordFactory.unsigned(cachedSize);
        }

        /**
         * Initialize the physical memory size to the minimum of
         * <ul>
         * <li>The size from a Docker container configuration, if any.</li>
         * <li>The size from the kernel sysconf configuration, which must exist.</li>
         * </ul>
         */
        void initializeSize() {
            final long cgroupSize = sizeFromCGroup();
            final long sysconfSize = sizeFromSysconf();
            /* Use the minimum size. */
            final long minSize = Math.min(cgroupSize, sysconfSize);
            /* Races are idempotent. */
            setSize(minSize);
        }

        /** Set the size of physical memory from the kernel sysconf parameters. */
        long sizeFromSysconf() {
            final long numberOfPhysicalMemoryPages = Unistd.sysconf(Unistd._SC_PHYS_PAGES());
            final long sizeOfAPhysicalMemoryPage = Unistd.sysconf(Unistd._SC_PAGESIZE());
            final long result = (numberOfPhysicalMemoryPages * sizeOfAPhysicalMemoryPage);
            return result;
        }

        /**
         * From https://docs.docker.com/edge/engine/reference/commandline/run/
         *
         * <blockquote> Specify hard limits on memory available to containers (-m, --memory)
         * <p>
         * These parameters always set an upper limit on the memory available to the container. On
         * Linux, this is set on the cgroup and applications in a container can query it at
         * /sys/fs/cgroup/memory/memory.limit_in_bytes. </blockquote>
         */
        private static final String cgroupMemoryFileName = "/sys/fs/cgroup/memory/memory.limit_in_bytes";

        /** Read the size of physical memory from a Docker cgroup file, if it exists. */
        @RestrictHeapAccess(access = Access.UNRESTRICTED, overridesCallers = true, reason = "Only called if allocation is allowed.")
        long sizeFromCGroup() {
            assert !Heap.getHeap().isAllocationDisallowed() : "LinuxPhysicalMemory.PhysicalMemorySupportImpl.sizeFromCGroup: Allocation disallowed.";
            long result = Long.MAX_VALUE;
            try {
                /* Read digits out of the file and convert them a long. */
                final FileInputStream stream = new FileInputStream(cgroupMemoryFileName);
                /* Enough characters to hold a long: 9,223,372,036,854,775,807. */
                final int maxIndex = 31;
                final char[] charBuffer = new char[maxIndex + 1];
                int index = 0;
                int read = stream.read();
                while ((index < maxIndex) && (read >= '0') && (read <= '9')) {
                    charBuffer[index] = (char) read;
                    index += 1;
                    read = stream.read();
                }
                final String stringBuffer = new String(charBuffer, 0, index);
                result = Long.parseLong(stringBuffer);
            } catch (IOException | NumberFormatException e) {
                /* Ignore exceptions. */
            }
            return result;
        }
    }

    @AutomaticFeature
    static class PhysicalMemoryFeature implements Feature {

        @Override
        public void afterRegistration(AfterRegistrationAccess access) {
            ImageSingletons.add(PhysicalMemorySupport.class, new PhysicalMemorySupportImpl());
        }
    }
}
