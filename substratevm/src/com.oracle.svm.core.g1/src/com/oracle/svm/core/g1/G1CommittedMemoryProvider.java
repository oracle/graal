/*
 * Copyright (c) 2019, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.g1;

import static com.oracle.svm.core.g1.G1Options.G1HeapRegionSize;
import static com.oracle.svm.shared.Uninterruptible.CALLED_FROM_UNINTERRUPTIBLE_CODE;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.StackValue;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.WordPointer;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.impl.Word;

import com.oracle.svm.core.VMInspectionOptions;
import com.oracle.svm.core.container.Container;
import com.oracle.svm.core.container.ContainerLibrary;
import com.oracle.svm.core.g1.nativelib.G1Library;
import com.oracle.svm.core.g1.nativelib.G1Structs.G1HeapOptions;
import com.oracle.svm.core.graal.snippets.CEntryPointSnippets;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.ReferenceAccess;
import com.oracle.svm.core.nmt.NativeMemoryTracking;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.AbstractCommittedMemoryProvider;
import com.oracle.svm.core.os.AbstractImageHeapProvider;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.os.ImageHeapProvider;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.util.PointerUtils;
import com.oracle.svm.guest.staging.IsolateArguments;
import com.oracle.svm.guest.staging.c.function.CEntryPointErrors;
import com.oracle.svm.guest.staging.core.UnmanagedMemoryUtil;
import com.oracle.svm.guest.staging.core.graal.KnownIntrinsics;
import com.oracle.svm.shared.NeverInline;
import com.oracle.svm.shared.Uninterruptible;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.DisallowLayered;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.SingleLayer;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.InitialLayerOnly;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.UnsignedUtils;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.core.common.CompressEncoding;

/**
 * Reserves one contiguous block of memory in which the image heap and the collected Java heap are
 * placed. The layout of this block of memory is as follows:
 *
 * <pre>
 * | null regions | metaspace |  image heap   |     collected Java heap      |
 * |              |           | closed | open | size determined by -Xms/-Xmx |
 * |                             G1 managed heap                             |
 * ^
 * heapBase
 * </pre>
 *
 * <ul>
 * <li>The first regions in the G1 managed heap are null regions and are used to ensure that 'null'
 * can never point to a valid object.</li>
 * <li>The image heap consists of closed and open regions (see {@link G1RegionType}).</li>
 * <li>The size of the Java heap is determined by the min and max heap size values (-Xms, -Xmx) that
 * are specified by the user. If uncompressed references are used, it is guaranteed that the image
 * heap does not reduce the size of the Java heap, e.g., if the user specifies '-Xmx1g', then the
 * Java heap will have a maximum size of 1g, regardless of the image heap size. However, if
 * compressed references are used, the image heap and the Java heap need to coexist in the 32 GB
 * address space, which can reduce the maximum size of the Java heap.</li>
 * </ul>
 */
@SingletonTraits(access = AllAccess.class, layeredCallbacks = SingleLayer.class, layeredInstallationKind = InitialLayerOnly.class, other = DisallowLayered.class)
public class G1CommittedMemoryProvider extends AbstractCommittedMemoryProvider {
    private Pointer reservedBegin;
    private UnsignedWord reservedSize;
    private UnsignedWord maxHeapSize;
    private UnsignedWord physicalMemorySize;

    private Pointer metaspaceBegin;
    private Pointer metaspaceEnd;
    private UnsignedWord collectedHeapSize;

    @Platforms(Platform.HOSTED_ONLY.class)
    public G1CommittedMemoryProvider() {
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public static G1CommittedMemoryProvider singleton() {
        return (G1CommittedMemoryProvider) CommittedMemoryProvider.get();
    }

    @Override
    @Uninterruptible(reason = "Still being initialized.")
    public int initialize(WordPointer heapBaseOut, IsolateArguments arguments) {
        int argc = arguments.getArgc();
        CCharPointerPointer argv = arguments.getArgv();

        UnsignedWord nullRegionSize = Word.unsigned(G1Heap.getNullRegionSize());
        UnsignedWord metaspaceSize = Word.unsigned(G1Heap.getReservedMetaspaceSize());
        // The image heap size in the file may be smaller than the image heap at run-time because we
        // don't fill the last image heap region completely. This reduces the file size.
        UnsignedWord imageHeapSize = UnsignedUtils.roundUp(AbstractImageHeapProvider.getImageHeapSizeInFile(), Word.unsigned(getRegionSize()));
        UnsignedWord heapBaseAlignment = Word.unsigned(Heap.getHeap().getHeapBaseAlignment());

        int heapOptionStructSize = SizeOf.get(G1HeapOptions.class);
        G1HeapOptions heapOptions = StackValue.get(G1HeapOptions.class);
        UnmanagedMemoryUtil.fill((Pointer) heapOptions, Word.unsigned(heapOptionStructSize), (byte) 0);

        boolean isContainerized = Container.isSupported() && Container.singleton().isContainerized();
        long containerMemoryLimitInBytes = isContainerized ? ContainerLibrary.getMemoryLimitInBytes() : 0;
        int containerActiveProcessorCount = isContainerized ? ContainerLibrary.getActiveProcessorCount() : 0;

        G1Library.parseOptions(G1Library.VERSION, argc, argv, G1Options.HOSTED_ARGUMENTS.get(), G1Options.RUNTIME_ARGUMENTS.get(),
                        ReferenceAccess.singleton().getMaxAddressSpaceSize(), heapBaseAlignment, nullRegionSize, metaspaceSize, imageHeapSize,
                        getCompressedReferenceShift(), isContainerized, containerMemoryLimitInBytes, containerActiveProcessorCount, heapOptions);

        UnsignedWord heapAddressSpaceSize = heapOptions.heapAddressSpaceSize();
        UnsignedWord newMaxHeapSize = heapOptions.maxHeapSize();
        assert heapAddressSpaceSize.belowOrEqual(ReferenceAccess.singleton().getMaxAddressSpaceSize()) : "must be";

        if (heapAddressSpaceSize.belowThan(imageHeapSize.add(Heap.getHeap().getImageHeapOffsetInAddressSpace()))) {
            return CEntryPointErrors.INSUFFICIENT_ADDRESS_SPACE;
        }

        Pointer reservedMemory = G1Support.singleton().reserveHeapSpace(heapAddressSpaceSize, heapBaseAlignment);
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

        Pointer collectedHeapBegin = PointerUtils.roundUp(imageHeapEndOut.read(), Word.unsigned(getRegionSize()));
        return initialize0(reservedMemory, heapAddressSpaceSize, collectedHeapBegin, newMaxHeapSize, heapOptions);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE)
    @NeverInline("Force loading of a new instance reference, now that the heap base is initialized.")
    @SuppressWarnings("hiding")
    private static int initialize0(Pointer reservedBegin, UnsignedWord reservedSize, Pointer collectedHeapBegin, UnsignedWord maxHeapSize, G1HeapOptions heapOptions) {
        G1CommittedMemoryProvider provider = singleton();
        return provider.initializeFields(reservedBegin, reservedSize, collectedHeapBegin, maxHeapSize, heapOptions);
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @SuppressWarnings("hiding")
    private int initializeFields(Pointer reservedBegin, UnsignedWord reservedSize, Pointer collectedHeapBegin, UnsignedWord maxHeapSize, G1HeapOptions heapOptions) {
        this.reservedBegin = reservedBegin;
        this.reservedSize = reservedSize;
        this.maxHeapSize = maxHeapSize;
        this.physicalMemorySize = heapOptions.physicalMemorySize();

        initializeMetaspaceFields();
        initializeCollectedHeapFields(reservedBegin, reservedSize, collectedHeapBegin);
        return CEntryPointErrors.NO_ERROR;
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    protected void initializeMetaspaceFields() {
        int metaspaceSize = G1Heap.getReservedMetaspaceSize();
        this.metaspaceBegin = KnownIntrinsics.heapBase().add(G1Heap.getMetaspaceOffsetInAddressSpace());
        this.metaspaceEnd = metaspaceBegin.add(metaspaceSize);

        if (VMInspectionOptions.hasNativeMemoryTrackingSupport() && metaspaceSize > 0) {
            NativeMemoryTracking.singleton().trackReserve(metaspaceSize, NmtCategory.Metaspace);
        }
    }

    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    @SuppressWarnings("hiding")
    private void initializeCollectedHeapFields(Pointer reservedBegin, UnsignedWord reservedSize, Pointer collectedHeapBegin) {
        this.collectedHeapSize = reservedSize.subtract(collectedHeapBegin.subtract(reservedBegin));

        if (VMInspectionOptions.hasNativeMemoryTrackingSupport()) {
            UnsignedWord collectedHeapSize = reservedSize.subtract(collectedHeapBegin.subtract(reservedBegin));
            NativeMemoryTracking.singleton().trackReserve(collectedHeapSize, NmtCategory.JavaHeap);
        }
    }

    @Override
    public UnsignedWord getCollectedHeapAddressSpaceSize() {
        return collectedHeapSize;
    }

    @Override
    @Uninterruptible(reason = CALLED_FROM_UNINTERRUPTIBLE_CODE, mayBeInlined = true)
    public boolean isInMetaspace(Pointer ptr) {
        return ptr.aboveOrEqual(metaspaceBegin) && ptr.belowThan(metaspaceEnd);
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
        return G1HeapRegionSize.getValue();
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
