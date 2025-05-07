/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.container.Container;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.PhysicalMemory;
import com.oracle.svm.core.heap.PhysicalMemory.PhysicalMemorySupport;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.posix.headers.Unistd;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.word.Word;

public class LinuxPhysicalMemorySupportImpl implements PhysicalMemorySupport {

    private static final long K = 1024;

    @Override
    public UnsignedWord size() {
        long numberOfPhysicalMemoryPages = Unistd.sysconf(Unistd._SC_PHYS_PAGES());
        long sizeOfAPhysicalMemoryPage = Unistd.sysconf(Unistd._SC_PAGESIZE());
        if (numberOfPhysicalMemoryPages == -1 || sizeOfAPhysicalMemoryPage == -1) {
            throw VMError.shouldNotReachHere("Physical memory size (number of pages or page size) not available");
        }
        return Word.unsigned(numberOfPhysicalMemoryPages).multiply(Word.unsigned(sizeOfAPhysicalMemoryPage));
    }

    @Override
    public long usedSize() {
        /*
         * Note: we use getCachedMemoryLimitInBytes() because we don't want to mutate the state, and
         * we assume that the memory limits have be queried before calling this method.
         */
        assert !(Container.singleton().isContainerized() && Container.singleton().getCachedMemoryLimitInBytes() > 0) : "Should be using OperatingSystemMXBean";
        /* Non-containerized Linux uses /proc/meminfo. */
        return getUsedSizeFromProcMemInfo();
    }

    private static long getUsedSizeFromProcMemInfo() {
        try {
            List<String> lines = readAllLines("/proc/meminfo");
            for (String line : lines) {
                if (line.contains("MemAvailable")) {
                    return PhysicalMemory.size().rawValue() - parseFirstNumber(line) * K;
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
}

@AutomaticallyRegisteredFeature
class LinuxPhysicalMemorySupportFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (ImageLayerBuildingSupport.firstImageBuild() && !ImageSingletons.contains(PhysicalMemorySupport.class)) {
            ImageSingletons.add(PhysicalMemorySupport.class, new LinuxPhysicalMemorySupportImpl());
        }
    }
}
