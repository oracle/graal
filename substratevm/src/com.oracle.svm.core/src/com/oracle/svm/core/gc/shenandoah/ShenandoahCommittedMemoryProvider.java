/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.gc.shenandoah;

import static com.oracle.svm.core.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;
import static com.oracle.svm.core.gc.shenandoah.ShenandoahOptions.ShenandoahRegionSize;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;

import com.oracle.svm.core.IsolateArguments;
import com.oracle.svm.core.NeverInline;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.UnmanagedMemoryUtil;
import com.oracle.svm.core.c.function.CEntryPointErrors;
import com.oracle.svm.core.container.Container;
import com.oracle.svm.core.container.ContainerLibrary;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahLibrary;
import com.oracle.svm.core.gc.shenandoah.nativelib.ShenandoahStructs.ShenandoahHeapOptions;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.os.AbstractCommittedMemoryProvider;
import com.oracle.svm.core.os.AbstractImageHeapProvider;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.os.ImageHeapProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.snippets.KnownIntrinsics;
import com.oracle.svm.core.util.UnsignedUtils;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.CompressEncoding;
import jdk.graal.compiler.word.Word;

/**
 * Reserves one contiguous block of memory in which the image heap and the collected Java heap are
 * placed. The layout of this block of memory is as follows:
 *
 * <pre>
 * | null regions |  image heap   |     collected Java heap      |
 * | (protected)  | closed | open | size determined by -Xms/-Xmx |
 * ^
 * heapBase
 * </pre>
 *
 * <ul>
 * <li>The memory right after the heap base is protected and cannot be accessed. This ensures that
 * Java null values never point to valid objects.</li>
 * <li>The image heap consists of closed and open regions (see {@link ShenandoahRegionType}).</li>
 * <li>The size of the Java heap is determined by the min and max heap size values (-Xms, -Xmx) that
 * are specified by the user. If uncompressed references are used, it is guaranteed that the image
 * heap does not reduce the size of the Java heap, e.g., if the user specifies '-Xmx1g', then the
 * Java heap will have a maximum size of 1g, regardless of the image heap size. However, if
 * compressed references are used, the image heap and the Java heap need to coexist in the 32 GB
 * address space, which can reduce the maximum size of the Java heap.</li>
 * </ul>
 */
public class ShenandoahCommittedMemoryProvider extends AbstractCommittedMemoryProvider {
    private Pointer reservedBegin;
    private UnsignedWord reservedSize;
    private UnsignedWord maxHeapSize;
    private UnsignedWord physicalMemorySize;

    @Platforms(Platform.HOSTED_ONLY.class)
    public ShenandoahCommittedMemoryProvider() {
        assert SubstrateOptions.SpawnIsolates.getValue();
    }

    @Override
    @Uninterruptible(reason = "Still being initialized.")
    public int initialize(WordPointer heapBaseOut, IsolateArguments arguments) {
        int argc = arguments.getArgc();
        CCharPointerPointer argv = arguments.getArgv();

        UnsignedWord nullRegionSize = Word.unsigned(ShenandoahHeap.get().getImageHeapOffsetInAddressSpace());
        // The image heap size in the file may be smaller than the image heap at run-time because we
        // don't fill the last image heap region completely. This reduces the file size.
        UnsignedWord imageHeapSize = UnsignedUtils.roundUp(AbstractImageHeapProvider.getImageHeapSizeInFile(), Word.unsigned(getRegionSize()));
        UnsignedWord heapBaseAlignment = Word.unsigned(Heap.getHeap().getHeapBaseAlignment());

        int heapOptionStructSize = SizeOf.get(ShenandoahHeapOptions.class);
        ShenandoahHeapOptions heapOptions = StackValue.get(ShenandoahHeapOptions.class);
        UnmanagedMemoryUtil.fill((Pointer) heapOptions, Word.unsigned(heapOptionStructSize), (byte) 0);

        boolean isContainerized = Container.isSupported() && Container.singleton().isContainerized();
        long containerMemoryLimitInBytes = isContainerized ? ContainerLibrary.getMemoryLimitInBytes() : 0;
        int containerActiveProcessorCount = isContainerized ? ContainerLibrary.getActiveProcessorCount() : 0;

        ShenandoahLibrary.parseOptions(ShenandoahLibrary.VERSION, argc, argv, ShenandoahOptions.HOSTED_ARGUMENTS.get(), ShenandoahOptions.RUNTIME_ARGUMENTS.get(),
                        ReferenceAccess.singleton().getMaxAddressSpaceSize(), heapBaseAlignment, nullRegionSize, imageHeapSize,
                        getCompressedReferenceShift(), isContainerized, containerMemoryLimitInBytes, containerActiveProcessorCount, heapOptions);

        UnsignedWord heapAddressSpaceSize = heapOptions.heapAddressSpaceSize();
        UnsignedWord newMaxHeapSize = heapOptions.maxHeapSize();
        assert heapAddressSpaceSize.belowOrEqual(ReferenceAccess.singleton().getMaxAddressSpaceSize()) : "must be";

        if (heapAddressSpaceSize.belowThan(nullRegionSize.add(imageHeapSize))) {
            return CEntryPointErrors.INSUFFICIENT_ADDRESS_SPACE;
        }

        Pointer reservedMemory = reserveHeapMemory(heapAddressSpaceSize, heapBaseAlignment);
        if (reservedMemory.isNull()) {
            return CEntryPointErrors.RESERVE_ADDRESS_SPACE_FAILED;
        }

        WordPointer imageHeapEndOut = StackValue.get(WordPointer.class);
        int result = ImageHeapProvider.get().initialize(reservedMemory, heapAddressSpaceSize, heapBaseOut, imageHeapEndOut);
        if (result != CEntryPointErrors.NO_ERROR) {
            VirtualMemoryProvider.get().free(reservedMemory, heapAddressSpaceSize);
            return result;
        }

        CEntryPointSnippets.initBaseRegisters(heapBaseOut.read());
        return initialize0(reservedMemory, heapAddressSpaceSize, newMaxHeapSize, heapOptions);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    @NeverInline("Force loading of a new instance reference, now that the heap base is initialized.")
    @SuppressWarnings("hiding")
    private static int initialize0(Pointer reservedBegin, UnsignedWord reservedSize, UnsignedWord maxHeapSize, ShenandoahHeapOptions heapOptions) {
        ShenandoahCommittedMemoryProvider instance = getInstance();
        instance.reservedBegin = reservedBegin;
        instance.reservedSize = reservedSize;
        instance.maxHeapSize = maxHeapSize;
        instance.physicalMemorySize = heapOptions.physicalMemorySize();
        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = "Still being initialized.")
    private static Pointer reserveHeapMemory(UnsignedWord heapAddressSpaceSize, UnsignedWord heapBaseAlignment) {
        return VirtualMemoryProvider.get().reserve(heapAddressSpaceSize, heapBaseAlignment, false);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static ShenandoahCommittedMemoryProvider getInstance() {
        return (ShenandoahCommittedMemoryProvider) CommittedMemoryProvider.get();
    }

    @Override
    public UnsignedWord getCollectedHeapAddressSpaceSize() {
        Pointer collectedHeapStart = KnownIntrinsics.heapBase().add(getCollectedHeapOffsetInAddressSpace());
        assert collectedHeapStart.aboveOrEqual(reservedBegin);
        return reservedSize.subtract(collectedHeapStart.subtract(reservedBegin));
    }

    private static UnsignedWord getCollectedHeapOffsetInAddressSpace() {
        return UnsignedUtils.roundUp(ImageHeapProvider.get().getImageHeapEndOffsetInAddressSpace(), Word.unsigned(getRegionSize()));
    }

    @Override
    public UnsignedWord getReservedAddressSpaceSize() {
        return reservedSize;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public UnsignedWord getMaxHeapSize() {
        return maxHeapSize;
    }

    public UnsignedWord getPhysicalMemorySize() {
        return physicalMemorySize;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getMaxRegions() {
        long result = getMaxHeapSize().unsignedDivide(getRegionSize()).rawValue();
        assert (int) result == result;
        return (int) result;
    }

    @Fold
    static int getCompressedReferenceShift() {
        return ImageSingletons.lookup(CompressEncoding.class).getShift();
    }

    @Fold
    protected static int getRegionSize() {
        return ShenandoahRegionSize.getValue();
    }

    @Override
    @Uninterruptible(reason = "Tear-down in progress.")
    public int tearDown() {
        /*
         * ImageHeapProvider.freeImageHeap must not be called because the ImageHeapProvider did not
         * allocate any memory for the image heap.
         */
        return unmapAddressSpace(KnownIntrinsics.heapBase());
    }

    @Uninterruptible(reason = "Tear-down in progress.")
    private int unmapAddressSpace(Pointer heapBase) {
        assert heapBase.aboveOrEqual(reservedBegin) && heapBase.belowOrEqual(reservedBegin.add(getRegionSize()));
        if (VirtualMemoryProvider.get().free(reservedBegin, reservedSize) != 0) {
            return CEntryPointErrors.FREE_ADDRESS_SPACE_FAILED;
        }
        return CEntryPointErrors.NO_ERROR;
    }
}
