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
package com.oracle.svm.core.posix.cosmo;

import com.oracle.svm.core.container.Container;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.heap.PhysicalMemory.PhysicalMemorySupport;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.posix.cosmo.headers.Unistd;
import com.oracle.svm.core.util.VMError;
import jdk.graal.compiler.word.Word;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.word.UnsignedWord;

import java.util.function.BooleanSupplier;

public class CosmoPhysicalMemorySupportImpl implements PhysicalMemorySupport {

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
        return -1L;
    }
}

@AutomaticallyRegisteredFeature
class CosmoPhysicalMemorySupportFeature implements InternalFeature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        BooleanSupplier x = new CosmoLibCSupplier();
        if (!x.getAsBoolean()) return;
        if (ImageLayerBuildingSupport.firstImageBuild() && !ImageSingletons.contains(PhysicalMemorySupport.class)) {
            ImageSingletons.add(PhysicalMemorySupport.class, new CosmoPhysicalMemorySupportImpl());
        }
    }
}
