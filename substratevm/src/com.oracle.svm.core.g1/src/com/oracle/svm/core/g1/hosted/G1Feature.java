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
package com.oracle.svm.core.g1.hosted;

import static com.oracle.svm.hosted.FeatureImpl.AfterAbstractImageCreationAccessImpl;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platform.DARWIN_AARCH64;
import org.graalvm.nativeimage.Platform.LINUX_AARCH64;
import org.graalvm.nativeimage.Platform.LINUX_AMD64;
import org.graalvm.nativeimage.Platform.WINDOWS_AMD64;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.PinnedObjectSupport;

import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.GCRelatedMXBeans;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTarget;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.config.ObjectLayout.IdentityHashMode;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.gc.shared.graal.NativeGCAllocationSupport;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.GCAllocationSupport;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.heap.AllocationFeature;
import com.oracle.svm.core.heap.BarrierSetProvider;
import com.oracle.svm.core.heap.FillerArray;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.heap.PlatformPhysicalMemorySupport;
import com.oracle.svm.core.hub.RuntimeClassLoading;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.jdk.SystemPropertiesSupport;
import com.oracle.svm.core.jvmstat.PerfDataFeature;
import com.oracle.svm.core.jvmstat.PerfManager;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.posix.darwin.DarwinPhysicalMemorySupportImpl;
import com.oracle.svm.core.posix.linux.LinuxPhysicalMemorySupportImpl;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.windows.WindowsPhysicalMemorySupportImpl;
import com.oracle.svm.core.g1.G1CommittedMemoryProvider;
import com.oracle.svm.core.g1.G1Heap;
import com.oracle.svm.core.g1.G1ImageHeapInfo;
import com.oracle.svm.core.g1.G1ObjectHeader;
import com.oracle.svm.core.g1.G1Options;
import com.oracle.svm.core.g1.G1PerfData;
import com.oracle.svm.core.g1.G1PhysicalMemorySupport;
import com.oracle.svm.core.g1.G1PinnedObjectSupport;
import com.oracle.svm.core.g1.G1RelatedMXBeans;
import com.oracle.svm.core.g1.graal.G1AllocationSupport;
import com.oracle.svm.core.g1.graal.G1BarrierSetProvider;
import com.oracle.svm.core.g1.graal.SubstrateG1WriteBarrierSnippets;
import com.oracle.svm.guest.staging.option.RuntimeOptionKey;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;
import com.oracle.svm.hosted.gc.shared.NativeGCAccessedFields;
import com.oracle.svm.hosted.image.AbstractImage;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.thread.VMThreadFeature;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.option.SubstrateOptionKey;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.JVMCIReflectionUtil;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetCounter;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaType;

@AutomaticallyRegisteredFeature
public class G1Feature implements InternalFeature {
    private static final String IMAGE_HEAP_BOT_SECTION_NAME = "svm_g1_bot";

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.useG1GC();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(VMThreadFeature.class, PerfDataFeature.class, AllocationFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        verifyOptionsAndPlatform();

        ImageSingletons.add(BarrierSetProvider.class, new G1BarrierSetProvider());
        ImageSingletons.add(ObjectLayout.class, createObjectLayout());

        G1CommittedMemoryProvider memoryProvider = new G1CommittedMemoryProvider();
        ImageSingletons.add(CommittedMemoryProvider.class, memoryProvider);
        ImageSingletons.add(G1CommittedMemoryProvider.class, memoryProvider);

        ImageSingletons.add(PlatformPhysicalMemorySupport.class, new G1PhysicalMemorySupport(createPlatformPhysicalMemorySupport()));
        ImageSingletons.add(GCRelatedMXBeans.class, new G1RelatedMXBeans());
    }

    private static PlatformPhysicalMemorySupport createPlatformPhysicalMemorySupport() {
        if (Platform.includedIn(Platform.LINUX.class)) {
            return new LinuxPhysicalMemorySupportImpl();
        } else if (Platform.includedIn(Platform.DARWIN.class)) {
            return new DarwinPhysicalMemorySupportImpl();
        } else if (Platform.includedIn(Platform.WINDOWS.class)) {
            return new WindowsPhysicalMemorySupportImpl();
        } else {
            throw VMError.shouldNotReachHere("Unexpected platform");
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        G1PerfData perfData = null;
        if (ImageSingletons.contains(PerfManager.class)) {
            perfData = new G1PerfData();
        }

        G1Heap heap = new G1Heap(perfData);
        ImageSingletons.add(Heap.class, heap);
        ImageSingletons.add(G1Heap.class, heap);

        G1AllocationSupport allocationSupport = new G1AllocationSupport();
        ImageSingletons.add(GCAllocationSupport.class, allocationSupport);
        ImageSingletons.add(NativeGCAllocationSupport.class, allocationSupport);

        G1PinnedObjectSupport pinnedObjectSupport = new G1PinnedObjectSupport();
        ImageSingletons.add(PinnedObjectSupport.class, pinnedObjectSupport);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        BeforeAnalysisAccessImpl accessImpl = (BeforeAnalysisAccessImpl) access;
        NativeGCAccessedFields.markAsAccessed(accessImpl, G1AccessedFields.ACCESSED_CLASSES);

        /* G1 needs a custom filler array class that does not match int[].class. */
        accessImpl.registerAsUsed(FillerArray.class);

        /* Needed for the barrier set. */
        accessImpl.registerAsUsed(Object[].class);

        /* Ensure that SVM knows about all runtime options that G1 parses on the C++ side. */
        registerRuntimeOptionsAsRead(accessImpl);

        // If building libgraal, set system property showing gc algorithm
        SystemPropertiesSupport.singleton().setLibGraalRuntimeProperty("gc", Heap.getHeap().getGC().getName());
    }

    private static void registerRuntimeOptionsAsRead(BeforeAnalysisAccessImpl accessImpl) {
        ResolvedJavaType runtimeOptionKeyType = accessImpl.getMetaAccess().lookupJavaType(RuntimeOptionKey.class);
        for (Field field : G1Options.getOptionFields()) {
            ResolvedJavaType fieldType = accessImpl.getMetaAccess().lookupJavaType(field.getType());
            if (runtimeOptionKeyType.isAssignableFrom(fieldType)) {
                accessImpl.registerAsRead(field, "it is a GC option field");
            }
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        ImageSingletons.add(ImageHeapLayouter.class, new G1ImageHeapLayouter());
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        BeforeCompilationAccessImpl access = (BeforeCompilationAccessImpl) a;
        G1Heap heap = G1Heap.get();

        /* Mark the image heap info as immutable. */
        G1ImageHeapInfo imageHeapInfo = G1Heap.getImageHeapInfo();
        access.registerAsImmutable(imageHeapInfo);
        access.registerAsImmutable(imageHeapInfo.getRegionTypes());
        access.registerAsImmutable(imageHeapInfo.getRegionFreeSpaces());

        /* Collect data and offsets that are needed when initializing G1. */
        byte[] fieldOffsets = NativeGCAccessedFields.writeOffsets(access, G1ObjectHeader.getMarkWordOffset(), G1Heap.javaThreadTL, G1AllocationSupport.podReferenceMapTL,
                        G1AccessedFields.ACCESSED_CLASSES);
        heap.setAccessedFieldOffsets(fieldOffsets);
        access.registerAsImmutable(fieldOffsets);
    }

    @Override
    public void afterAbstractImageCreation(AfterAbstractImageCreationAccess a) {
        AfterAbstractImageCreationAccessImpl access = (AfterAbstractImageCreationAccessImpl) a;
        AbstractImage image = access.getImage();

        finalizeImageHeapInfo(image.getHeap());
        createBlockOffsetTable(image.getObjectFile());
    }

    /** Updates the arrays in the image heap info, now that the layouting is done. */
    private static void finalizeImageHeapInfo(NativeImageHeap nativeImageHeap) {
        G1ImageHeapInfo imageHeapInfo = G1Heap.getImageHeapInfo();
        ImageHeapScanner heapScanner = nativeImageHeap.aUniverse.getHeapScanner();
        ResolvedJavaType imageHeapInfoType = GuestAccess.get().lookupType(G1ImageHeapInfo.class);
        ObjectScanner.ScanReason reason = new ObjectScanner.OtherReason("Manual rescan triggered from " + G1ImageHeapLayouter.class);
        heapScanner.rescanField(imageHeapInfo, JVMCIReflectionUtil.getUniqueDeclaredField(imageHeapInfoType, "regionTypes"), reason);
        heapScanner.rescanField(imageHeapInfo, JVMCIReflectionUtil.getUniqueDeclaredField(imageHeapInfoType, "regionFreeSpaces"), reason);
    }

    /**
     * Creates the block offset table for the relevant writable parts of the image heap and stores
     * it in a separate section.
     */
    private static void createBlockOffsetTable(ObjectFile objectFile) {
        byte[] blockOffsetTable = G1ImageHeapBlockOffsetTable.build();
        SectionName sectionName = new SectionName.ProgbitsSectionName(IMAGE_HEAP_BOT_SECTION_NAME);
        ObjectFile.Section section = objectFile.newProgbitsSection(sectionName.getFormatDependentName(objectFile.getFormat()), objectFile.getPageSize(), false, false,
                        new BasicProgbitsSectionImpl(blockOffsetTable));
        objectFile.createDefinedSymbol(section.getName(), section, 0, 0, false, false, false);

        int wordSize = SubstrateTarget.getWordSize();
        boolean internalSymbolsAreGlobal = SubstrateOptions.InternalSymbolsAreGlobal.getValue();
        objectFile.createDefinedSymbol(G1Heap.IMAGE_HEAP_BOT_BEGIN_SYMBOL_NAME, section, 0, wordSize, false, internalSymbolsAreGlobal, internalSymbolsAreGlobal);
        objectFile.createDefinedSymbol(G1Heap.IMAGE_HEAP_BOT_END_SYMBOL_NAME, section, blockOffsetTable.length, wordSize, false, internalSymbolsAreGlobal, internalSymbolsAreGlobal);
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        SubstrateG1WriteBarrierSnippets.registerForeignCalls(foreignCalls);
        G1AllocationSupport.registerForeignCalls(foreignCalls);
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        SubstrateAllocationSnippets.Templates templates = new SubstrateAllocationSnippets.Templates(options, providers);
        templates.registerLowering(lowerings);

        SubstrateG1WriteBarrierSnippets.Templates writeBarrierSnippets = new SubstrateG1WriteBarrierSnippets.Templates(options, SnippetCounter.Group.NullFactory, providers);
        writeBarrierSnippets.registerLowerings(lowerings);
    }

    private static void verifyOptionsAndPlatform() {
        UserError.guarantee(Platform.includedIn(LINUX_AMD64.class) || Platform.includedIn(LINUX_AARCH64.class) || Platform.includedIn(DARWIN_AARCH64.class) || Platform.includedIn(WINDOWS_AMD64.class),
                        "The G1 garbage collector ('--gc=G1') is currently only supported on Linux/amd64, Linux/aarch64, macOS/aarch64, and Windows/amd64.");

        verifyOptionEnabled(SubstrateOptions.AllowVMInternalThreads);
        verifyOptionEnabled(SubstrateOptions.ConcealedOptions.UseDedicatedVMOperationThread);
        verifyOptionEnabled(SubstrateOptions.ConcealedOptions.AutomaticReferenceHandling);
        verifyOptionEnabled(SubstrateOptions.UseNullRegion);

        UserError.guarantee(!SubstrateOptions.SupportCompileInIsolates.getValue(), "The G1 garbage collector ('--gc=G1') does not support isolated compilation.");
        UserError.guarantee(!RuntimeClassLoading.isSupported(), "The G1 garbage collector ('--gc=G1') does not support option '%s' because it requires metaspace support.",
                        RuntimeClassLoading.Options.RuntimeClassLoading.getName());
    }

    private static void verifyOptionEnabled(SubstrateOptionKey<Boolean> option) {
        String optionMustBeEnabledFmt = "When using the G1 garbage collector ('--gc=G1'), please note that option '%s' must be enabled.";
        UserError.guarantee(option.getValue(), optionMustBeEnabledFmt, option.getName());
    }

    /**
     * Defines the layout of objects.
     *
     * The layout of instance objects is:
     * <ul>
     * <li>32/64 bit mark word/identity hashcode</li>
     * <li>32 bit hub reference</li>
     * <li>instance fields (references, primitives)</li>
     * <li>32/64 bit object monitor reference (if needed)</li>
     * </ul>
     *
     * The layout of array objects is:
     * <ul>
     * <li>32/64 bit mark word/identity hashcode</li>
     * <li>32 bit hub reference</li>
     * <li>32 bit array length</li>
     * <li>array elements (length * elementSize)</li>
     * </ul>
     */
    private static ObjectLayout createObjectLayout() {
        SubstrateTarget target = SubstrateTarget.singleton();
        int referenceSize = computeReferenceSize(target);
        int intSize = target.arch.getPlatformKind(JavaKind.Int).getSizeInBytes();
        int objectAlignment = 8;

        int markWordSize = referenceSize;
        int hubSize = Integer.BYTES;

        int markWordOffset = G1ObjectHeader.getMarkWordOffset();
        int headerIdentityHashOffset = markWordOffset;
        int headerSize = headerIdentityHashOffset + markWordSize + hubSize + SubstrateOptions.AdditionalHeaderBytes.getValue();

        int hubOffset = markWordOffset + markWordSize;
        int firstFieldOffset = headerSize;
        int arrayLengthOffset = headerSize;
        int arrayBaseOffset = arrayLengthOffset + intSize;

        int identityHashNumBits = 31;
        int identityHashShift = 6;

        return new ObjectLayout(target, referenceSize, objectAlignment, hubSize, hubOffset, firstFieldOffset, arrayLengthOffset, arrayBaseOffset,
                        headerIdentityHashOffset, IdentityHashMode.OBJECT_HEADER, identityHashNumBits, identityHashShift);
    }

    private static int computeReferenceSize(SubstrateTarget target) {
        JavaKind referenceKind = JavaKind.Object;
        if (SubstrateOptions.useCompressedReferences()) {
            referenceKind = JavaKind.Int;
        }
        return target.arch.getPlatformKind(referenceKind).getSizeInBytes();
    }
}
