/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, 2024, Red Hat Inc. All rights reserved.
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

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.os.ImageHeapProvider;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.word.Word;

/**
 * This class implements native memory tracking (NMT). There are two components to NMT: tracking
 * memory allocations (malloc/realloc/calloc), and tracking virtual memory usage (not supported
 * yet).
 * 
 * For tracking memory allocations, we have an internal API (see {@link NativeMemory}) that adds a
 * custom {@link NmtMallocHeader header} to each allocation if NMT is enabled. This header stores
 * data that is needed to properly untrack the memory when it is freed.
 *
 * Virtual memory tracking makes the assumption that commits within a reserved region happen neatly.
 * There will never be overlapping commits and the size requested to be committed/uncommitted is
 * exactly the size committed/uncommitted. In Hotspot, this assumption is not made, and an internal
 * model of virtual memory is maintained.
 */
public class NativeMemoryTracking {
    private static final UnsignedWord ALIGNMENT = Word.unsigned(16);
    private static final int MAGIC = 0xF0F1F2F3;
    private static final long KB = 1024;

    private final NmtMallocMemoryInfo[] mallocCategories;
    private final NmtVirtualMemoryInfo[] virtualMemCategories;
    private final NmtMallocMemoryInfo mallocTotal;
    private final NmtVirtualMemoryInfo virtualMemTotal;

    @Platforms(Platform.HOSTED_ONLY.class)
    public NativeMemoryTracking() {
        mallocTotal = new NmtMallocMemoryInfo();
        virtualMemTotal = new NmtVirtualMemoryInfo();

        mallocCategories = new NmtMallocMemoryInfo[NmtCategory.values().length];
        for (int i = 0; i < mallocCategories.length; i++) {
            mallocCategories[i] = new NmtMallocMemoryInfo();
        }

        virtualMemCategories = new NmtVirtualMemoryInfo[NmtCategory.values().length];
        for (int i = 0; i < virtualMemCategories.length; i++) {
            virtualMemCategories[i] = new NmtVirtualMemoryInfo();
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

        getMallocInfo(header.getCategory()).track(allocationSize);
        getMallocInfo(NmtCategory.NMT).track(nmtHeaderSize);
        mallocTotal.track(totalSize);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public PointerBase untrack(PointerBase innerPtr) {
        if (innerPtr.isNull()) {
            return Word.nullPointer();
        }

        NmtMallocHeader header = getHeader(innerPtr);
        untrack(header.getAllocationSize(), header.getCategory());
        return header;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void untrack(UnsignedWord size, int category) {
        getMallocInfo(category).untrack(size);
        getMallocInfo(NmtCategory.NMT).untrack(sizeOfNmtHeader());
        mallocTotal.untrack(size.add(sizeOfNmtHeader()));
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
    public void trackReserve(UnsignedWord size, NmtCategory category) {
        trackReserve(size.rawValue(), category);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void trackReserve(long size, NmtCategory category) {
        getVirtualInfo(category).trackReserved(size);
        virtualMemTotal.trackReserved(size);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void trackCommit(UnsignedWord size, NmtCategory category) {
        trackCommit(size.rawValue(), category);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void trackCommit(long size, NmtCategory category) {
        getVirtualInfo(category).trackCommitted(size);
        virtualMemTotal.trackCommitted(size);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void trackUncommit(UnsignedWord size, NmtCategory category) {
        trackUncommit(size.rawValue(), category);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void trackUncommit(long size, NmtCategory category) {
        getVirtualInfo(category).trackUncommit(size);
        virtualMemTotal.trackUncommit(size);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void trackFree(UnsignedWord size, NmtCategory category) {
        trackFree(size.rawValue(), category);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public void trackFree(long size, NmtCategory category) {
        getVirtualInfo(category).trackFree(size);
        virtualMemTotal.trackFree(size);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getMallocMemory(NmtCategory category) {
        return getMallocInfo(category).getUsed();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getMallocCount(NmtCategory category) {
        return getMallocInfo(category).getCount();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getPeakMallocMemory(NmtCategory category) {
        return getMallocInfo(category).getPeakUsed();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getCountAtPeakMallocMemory(NmtCategory category) {
        return getMallocInfo(category).getCountAtPeakUsage();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getTotalMallocCount() {
        return mallocTotal.getCount();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getTotalMallocMemory() {
        return mallocTotal.getUsed();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getPeakTotalMallocMemory() {
        return mallocTotal.getPeakUsed();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getCountAtPeakTotalMallocMemory() {
        return mallocTotal.getCountAtPeakUsage();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getReservedVirtualMemory(NmtCategory category) {
        return getVirtualInfo(category).getReservedSize();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getCommittedVirtualMemory(NmtCategory category) {
        return getVirtualInfo(category).getCommittedSize();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getPeakReservedVirtualMemory(NmtCategory category) {
        return getVirtualInfo(category).getPeakReservedSize();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getPeakCommittedVirtualMemory(NmtCategory category) {
        return getVirtualInfo(category).getPeakCommittedSize();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getTotalReservedVirtualMemory() {
        return virtualMemTotal.getReservedSize();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getTotalCommittedVirtualMemory() {
        return virtualMemTotal.getCommittedSize();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getPeakTotalReservedVirtualMemory() {
        return virtualMemTotal.getPeakReservedSize();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public long getPeakTotalCommittedVirtualMemory() {
        return virtualMemTotal.getPeakCommittedSize();
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private NmtMallocMemoryInfo getMallocInfo(int category) {
        return mallocCategories[category];
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private NmtMallocMemoryInfo getMallocInfo(NmtCategory category) {
        return getMallocInfo(category.ordinal());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private NmtVirtualMemoryInfo getVirtualInfo(NmtCategory category) {
        return getVirtualInfo(category.ordinal());
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    private NmtVirtualMemoryInfo getVirtualInfo(int category) {
        return virtualMemCategories[category];
    }

    public static RuntimeSupport.Hook initializationHook() {
        return isFirstIsolate -> {
            NativeMemoryTracking.singleton().trackReserve(ImageHeapProvider.get().getImageHeapReservedBytes(), NmtCategory.ImageHeap);
            NativeMemoryTracking.singleton().trackCommit(ImageHeapProvider.get().getImageHeapMappedBytes(), NmtCategory.ImageHeap);
        };
    }

    public static RuntimeSupport.Hook shutdownHook() {
        return isFirstIsolate -> {
            NativeMemoryTracking.singleton().printStatistics();
        };
    }

    public void printStatistics() {
        if (VMInspectionOptions.PrintNMTStatistics.getValue()) {
            System.out.println();
            System.out.println(generateReportString());
        }
    }

    public String generateReportString() {
        String lineBreak = System.lineSeparator();

        StringBuilder result = new StringBuilder(3000);
        result.append("Native memory tracking").append(lineBreak).append(lineBreak);

        result.append("Total").append(lineBreak);
        long reservedTotal = (getTotalReservedVirtualMemory() + getTotalMallocMemory()) / KB;
        long committedTotal = (getTotalCommittedVirtualMemory() + getTotalMallocMemory()) / KB;
        result.append("    ").append("(reserved=").append(reservedTotal).append("KB, committed=").append(committedTotal).append("KB)").append(lineBreak);
        result.append("    ").append("(malloc=").append(getTotalMallocMemory() / KB).append("KB, count=").append(getTotalMallocCount()).append(")").append(lineBreak);
        result.append("    ").append("(peak malloc=").append(getPeakTotalMallocMemory() / KB).append("KB, count at peak=").append(getCountAtPeakTotalMallocMemory()).append(")").append(lineBreak);
        result.append("    ").append("(mmap: reserved=").append(getTotalReservedVirtualMemory() / KB).append("KB, committed=").append(getTotalCommittedVirtualMemory() / KB).append("KB)")
                        .append(lineBreak);
        result.append("    ").append("(mmap: peak reserved=").append(getPeakTotalReservedVirtualMemory() / KB).append("KB, peak committed=").append(getPeakTotalCommittedVirtualMemory() / KB)
                        .append("KB)").append(lineBreak);

        for (int i = 0; i < NmtCategory.values().length; i++) {
            NmtCategory category = NmtCategory.values()[i];
            result.append(category.getName()).append(lineBreak);
            long reserved = (getReservedVirtualMemory(category) + getMallocMemory(category)) / KB;
            long committed = (getCommittedVirtualMemory(category) + getMallocMemory(category)) / KB;
            result.append("    ").append("(reserved=").append(reserved).append("KB, committed=").append(committed).append("KB)").append(lineBreak);
            result.append("    ").append("(malloc=").append(getMallocMemory(category) / KB).append("KB, count=").append(getMallocCount(category)).append(")").append(lineBreak);
            result.append("    ").append("(peak malloc=").append(getPeakMallocMemory(category) / KB).append("KB, count at peak=").append(getCountAtPeakMallocMemory(category)).append(")")
                            .append(lineBreak);
            result.append("    ").append("(mmap: reserved=").append(getReservedVirtualMemory(category) / KB).append("KB, committed=").append(getCommittedVirtualMemory(category) / KB)
                            .append("KB)").append(lineBreak);
            result.append("    ").append("(mmap: peak reserved=").append(getPeakReservedVirtualMemory(category) / KB).append("KB, peak committed=")
                            .append(getPeakCommittedVirtualMemory(category) / KB).append("KB)").append(lineBreak);
        }
        return result.toString();
    }
}
