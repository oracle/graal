/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.windows;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.os.OSCommittedMemoryProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.core.windows.headers.MemoryAPI;

@AutomaticFeature
class WindowsOSCommittedMemoryProviderFeature implements Feature {
    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        if (!ImageSingletons.contains(CommittedMemoryProvider.class)) {
            ImageSingletons.add(CommittedMemoryProvider.class, new WindowsOSCommittedMemoryProvider());
        }
    }
}

/**
 * As it is not possible to free a subrange of the allocated address range on Windows, the main
 * purpose of this class is to adjust how the <b>aligned</b> blocks of committed memory are
 * allocated and freed to circumvent this restriction.
 */
public class WindowsOSCommittedMemoryProvider extends OSCommittedMemoryProvider {
    @Override
    public Pointer allocate(UnsignedWord size, UnsignedWord alignment, boolean executable) {
        if (alignment.belowOrEqual(defaultAlignment())) {
            return allocate(size, executable);
        }

        /* Reserve a container that is large enough for the requested size *and* the alignment. */
        UnsignedWord containerSize = getContainerSize(size, alignment);
        Pointer containerStart = VirtualMemoryProvider.get().reserve(containerSize);
        if (containerStart.isNull()) {
            return WordFactory.nullPointer();
        }

        /* Commit only the requested amount at the requested alignment within the container. */
        Pointer start = PointerUtils.roundUp(containerStart, alignment);
        if (VirtualMemoryProvider.get().commit(start, size, defaultProtection(executable)).isNull()) {
            VirtualMemoryProvider.get().free(containerStart, containerSize);
            return WordFactory.nullPointer();
        }
        return start;
    }

    @Override
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public boolean free(PointerBase start, UnsignedWord size, UnsignedWord alignment, boolean executable) {
        PointerBase containerStart;
        if (alignment.belowOrEqual(defaultAlignment())) {
            containerStart = start;
        } else {
            /* Retrieve the start of the enclosing container that was originally reserved. */
            MemoryAPI.MEMORY_BASIC_INFORMATION memoryInfo = StackValue.get(MemoryAPI.MEMORY_BASIC_INFORMATION.class);
            MemoryAPI.VirtualQuery(start, memoryInfo, SizeOf.unsigned(MemoryAPI.MEMORY_BASIC_INFORMATION.class));
            assert start.equal(memoryInfo.BaseAddress()) : "Invalid memory block start";
            containerStart = memoryInfo.AllocationBase();
        }
        return free(containerStart, getContainerSize(size, alignment));
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static UnsignedWord getContainerSize(UnsignedWord size, UnsignedWord alignment) {
        return alignment.belowOrEqual(defaultAlignment()) ? size : size.add(alignment);
    }
}
