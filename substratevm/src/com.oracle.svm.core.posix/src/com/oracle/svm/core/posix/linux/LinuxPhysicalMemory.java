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
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.UnsignedUtils;
import com.oracle.svm.core.util.VMError;

class LinuxPhysicalMemory extends PhysicalMemory {

    static class PhysicalMemorySupportImpl implements PhysicalMemorySupport {

        /**
         * Initialize the physical memory size to the minimum of
         * <ul>
         * <li>The size from a Docker container configuration, if any.</li>
         * <li>The size from the kernel sysconf configuration, which must exist.</li>
         * </ul>
         */
        @Override
        public UnsignedWord size() {
            return UnsignedUtils.min(sizeFromCGroup(), sizeFromSysconf());
        }

        /** Set the size of physical memory from the kernel sysconf parameters. */
        private static UnsignedWord sizeFromSysconf() {
            long numberOfPhysicalMemoryPages = Unistd.sysconf(Unistd._SC_PHYS_PAGES());
            long sizeOfAPhysicalMemoryPage = Unistd.sysconf(Unistd._SC_PAGESIZE());
            if (numberOfPhysicalMemoryPages == -1 || sizeOfAPhysicalMemoryPage == -1) {
                throw VMError.shouldNotReachHere("Physical memory size (number of pages or page size) not available");
            }
            return WordFactory.unsigned(numberOfPhysicalMemoryPages).multiply(WordFactory.unsigned(sizeOfAPhysicalMemoryPage));
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
        private static UnsignedWord sizeFromCGroup() {
            assert !Heap.getHeap().isAllocationDisallowed() : "LinuxPhysicalMemory.PhysicalMemorySupportImpl.sizeFromCGroup: Allocation disallowed.";
            /* Read digits out of the file and convert them a long. */
            try (FileInputStream stream = new FileInputStream(cgroupMemoryFileName)) {
                StringBuilder sb = new StringBuilder(32);
                int read = stream.read();
                while (read >= '0' && read <= '9') {
                    sb.append((char) read);
                    read = stream.read();
                }
                return WordFactory.unsigned(Long.parseLong(sb.toString()));
            } catch (IOException | NumberFormatException e) {
                /* Ignore exceptions. */
                return UnsignedUtils.MAX_VALUE;
            }
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
