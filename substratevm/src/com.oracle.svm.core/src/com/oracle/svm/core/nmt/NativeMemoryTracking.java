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

package com.oracle.svm.core.nmt;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.word.Pointer;
import org.graalvm.word.PointerBase;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.graal.compiler.api.replacements.Fold;

/**
 * This class implements native memory tracking (NMT). There are two components to NMT: tracking
 * memory allocations (malloc/realloc/calloc), and tracking virtual memory usage (not supported
 * yet).
 * 
 * For tracking memory allocations, we have an internal API (see {@link NativeMemory}) that adds a
 * custom {@link NmtMallocHeader header} to each allocation if NMT is enabled. This header stores
 * data that is needed to properly untrack the memory when it is freed.
 */
public class NativeMemoryTracking {
    private static final UnsignedWord ALIGNMENT = WordFactory.unsigned(16);
    private static final int MAGIC = 0xF0F1F2F3;

    private final NmtMallocMemoryInfo[] categories;
    private final NmtMallocMemoryInfo total;

    @Platforms(Platform.HOSTED_ONLY.class)
    public NativeMemoryTracking() {
        total = new NmtMallocMemoryInfo();
        categories = new NmtMallocMemoryInfo[NmtCategory.values().length];
        for (int i = 0; i < categories.length; i++) {
            categories[i] = new NmtMallocMemoryInfo();
        }
    }

    @Fold
    public static NativeMemoryTracking singleton() {
        return ImageSingletons.lookup(NativeMemoryTracking.class);
    }

    @Fold
    public static UnsignedWord sizeOfNmtHeader() {
        /*
         * Align the header to 16 bytes to preserve platform-specific malloc alignment up to 16
         * bytes (i.e., the allocation payload is aligned to 16 bytes if the platform-specific
         * malloc implementation returns a pointer that is aligned to at least 16 bytes).
         */
        return UnsignedUtils.roundUp(SizeOf.unsigned(NmtMallocHeader.class), ALIGNMENT);
    }

    /**
     * Initializes the NMT header and returns a pointer to the allocation payload (i.e., the inner
     * pointer).
     */
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @SuppressWarnings("static-method")
    public Pointer initializeHeader(PointerBase outerPtr, UnsignedWord size, NmtCategory category) {
        NmtMallocHeader mallocHeader = (NmtMallocHeader) outerPtr;
        mallocHeader.setAllocationSize(size);
        mallocHeader.setCategory(category.ordinal());
        assert setMagic(mallocHeader);
        return getInnerPointer(mallocHeader);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static boolean setMagic(NmtMallocHeader mallocHeader) {
        mallocHeader.setMagic(MAGIC);
        return true;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void track(PointerBase innerPtr) {
        if (innerPtr.isNull()) {
            return;
        }

        NmtMallocHeader header = getHeader(innerPtr);
        UnsignedWord nmtHeaderSize = sizeOfNmtHeader();
        UnsignedWord allocationSize = header.getAllocationSize();
        UnsignedWord totalSize = allocationSize.add(nmtHeaderSize);

        getInfo(header.getCategory()).track(allocationSize);
        getInfo(NmtCategory.NMT).track(nmtHeaderSize);
        total.track(totalSize);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public PointerBase untrack(PointerBase innerPtr) {
        if (innerPtr.isNull()) {
            return WordFactory.nullPointer();
        }

        NmtMallocHeader header = getHeader(innerPtr);
        untrack(header.getAllocationSize(), header.getCategory());
        return header;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void untrack(UnsignedWord size, int category) {
        getInfo(category).untrack(size);
        getInfo(NmtCategory.NMT).untrack(sizeOfNmtHeader());
        total.untrack(size.add(sizeOfNmtHeader()));
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static NmtMallocHeader getHeader(PointerBase innerPtr) {
        NmtMallocHeader result = (NmtMallocHeader) ((Pointer) innerPtr).subtract(sizeOfNmtHeader());
        assert result.getMagic() == MAGIC : "bad NMT malloc header";
        return result;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private static Pointer getInnerPointer(NmtMallocHeader mallocHeader) {
        return ((Pointer) mallocHeader).add(sizeOfNmtHeader());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getUsedMemory(NmtCategory category) {
        return getInfo(category).getUsed();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getPeakUsedMemory(NmtCategory category) {
        return getInfo(category).getPeakUsed();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getCountAtPeakUsage(NmtCategory category) {
        return getInfo(category).getCountAtPeakUsage();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getTotalCount() {
        return total.getCount();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getTotalUsedMemory() {
        return total.getUsed();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getPeakTotalUsedMemory() {
        return total.getPeakUsed();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getCountAtTotalPeakUsage() {
        return total.getCountAtPeakUsage();
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private NmtMallocMemoryInfo getInfo(NmtCategory category) {
        return getInfo(category.ordinal());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private NmtMallocMemoryInfo getInfo(int category) {
        assert category < categories.length;
        return categories[category];
    }

    public static RuntimeSupport.Hook shutdownHook() {
        return isFirstIsolate -> NativeMemoryTracking.singleton().printStatistics();
    }

    private void printStatistics() {
        if (VMInspectionOptions.PrintNMTStatistics.getValue()) {
            System.out.println();
            System.out.println("Native memory tracking");
            System.out.println("  Peak total used memory: " + getPeakTotalUsedMemory() + " bytes");
            System.out.println("  Total alive allocations at peak usage: " + getCountAtTotalPeakUsage());
            System.out.println("  Total used memory: " + getTotalUsedMemory() + " bytes");
            System.out.println("  Total alive allocations: " + getTotalCount());

            for (int i = 0; i < NmtCategory.values().length; i++) {
                String name = NmtCategory.values()[i].getName();
                NmtMallocMemoryInfo info = getInfo(i);

                System.out.println("  " + name + " peak used memory: " + info.getPeakUsed() + " bytes");
                System.out.println("  " + name + " alive allocations at peak: " + info.getCountAtPeakUsage());
                System.out.println("  " + name + " currently used memory: " + info.getUsed() + " bytes");
                System.out.println("  " + name + " currently alive allocations: " + info.getCount());
            }
        }
    }
}
