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
package com.oracle.svm.hosted.gc.shenandoah;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.PinnedObjectSupport;

import com.oracle.svm.core.GCRelatedMXBeans;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateTargetDescription;
import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;
import com.oracle.svm.core.config.ObjectLayout.IdentityHashMode;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.gc.shared.graal.NativeGCAllocationSupport;
import com.oracle.svm.core.gc.shenandoah.ShenandoahCommittedMemoryProvider;
import com.oracle.svm.core.gc.shenandoah.ShenandoahHeap;
import com.oracle.svm.core.gc.shenandoah.ShenandoahImageHeapInfo;
import com.oracle.svm.core.gc.shenandoah.ShenandoahObjectHeader;
import com.oracle.svm.core.gc.shenandoah.ShenandoahOptions;
import com.oracle.svm.core.gc.shenandoah.ShenandoahPinnedObjectSupport;
import com.oracle.svm.core.gc.shenandoah.ShenandoahRelatedMXBeans;
import com.oracle.svm.core.gc.shenandoah.graal.ShenandoahAllocationSupport;
import com.oracle.svm.core.gc.shenandoah.graal.ShenandoahBarrierSetProvider;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.GCAllocationSupport;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.heap.AllocationFeature;
import com.oracle.svm.core.heap.BarrierSetProvider;
import com.oracle.svm.core.heap.FillerArray;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.jvmstat.PerfDataFeature;
import com.oracle.svm.core.option.RuntimeOptionKey;
import com.oracle.svm.core.option.SubstrateOptionKey;
import com.oracle.svm.core.os.CommittedMemoryProvider;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;
import com.oracle.svm.hosted.gc.shared.NativeGCAccessedFields;
import com.oracle.svm.hosted.thread.VMThreadFeature;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.vm.ci.meta.JavaKind;

/** Shenandoah GC support. */
@AutomaticallyRegisteredFeature
public class ShenandoahFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.useShenandoahGC();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(VMThreadFeature.class, PerfDataFeature.class, AllocationFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        verifyOptionsAndPlatform();

        boolean useCompressedReferences = false;
        ImageSingletons.add(BarrierSetProvider.class, new ShenandoahBarrierSetProvider());
        ImageSingletons.add(ObjectLayout.class, createObjectLayout(useCompressedReferences));

        ShenandoahCommittedMemoryProvider memoryProvider = new ShenandoahCommittedMemoryProvider();
        ImageSingletons.add(CommittedMemoryProvider.class, memoryProvider);
        ImageSingletons.add(ShenandoahCommittedMemoryProvider.class, memoryProvider);

        ImageSingletons.add(GCRelatedMXBeans.class, new ShenandoahRelatedMXBeans());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ShenandoahHeap heap = new ShenandoahHeap();
        ImageSingletons.add(Heap.class, heap);
        ImageSingletons.add(ShenandoahHeap.class, heap);
        ImageSingletons.add(PinnedObjectSupport.class, new ShenandoahPinnedObjectSupport());

        ShenandoahAllocationSupport allocationSupport = new ShenandoahAllocationSupport();
        ImageSingletons.add(GCAllocationSupport.class, allocationSupport);
        ImageSingletons.add(NativeGCAllocationSupport.class, allocationSupport);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        BeforeAnalysisAccessImpl accessImpl = (BeforeAnalysisAccessImpl) access;
        NativeGCAccessedFields.markAsAccessed(accessImpl, ShenandoahAccessedFields.ACCESSED_CLASSES);

        /* Shenandoah needs a custom filler array class that does not match int[].class. */
        accessImpl.registerAsUsed(FillerArray.class);

        /* Needed for the barrier set. */
        accessImpl.registerAsUsed(Object[].class);

        /*
         * Ensure that SVM knows about all runtime options that Shenandoah parses on the C++ side.
         */
        registerRuntimeOptionsAsRead(accessImpl);
    }

    private static void registerRuntimeOptionsAsRead(BeforeAnalysisAccessImpl accessImpl) {
        for (Field field : ShenandoahOptions.getOptionFields()) {
            if (RuntimeOptionKey.class.isAssignableFrom(field.getType())) {
                accessImpl.registerAsRead(field, "it is a GC option field");
            }
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        ImageSingletons.add(ImageHeapLayouter.class, new ShenandoahImageHeapLayouter());
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        BeforeCompilationAccessImpl access = (BeforeCompilationAccessImpl) a;
        ShenandoahHeap heap = ShenandoahHeap.get();

        /* Mark the image heap info as immutable. */
        ShenandoahImageHeapInfo imageHeapInfo = ShenandoahHeap.getImageHeapInfo();
        access.registerAsImmutable(imageHeapInfo);
        access.registerAsImmutable(imageHeapInfo.getRegionTypes());
        access.registerAsImmutable(imageHeapInfo.getRegionFreeSpaces());

        /* Collect data and offsets that are needed when initializing Shenandoah. */
        byte[] fieldOffsets = NativeGCAccessedFields.writeOffsets(access, ShenandoahObjectHeader.getMarkWordOffset(), ShenandoahHeap.javaThreadTL, ShenandoahAllocationSupport.podReferenceMapTL,
                        ShenandoahAccessedFields.ACCESSED_CLASSES);
        heap.setAccessedFieldOffsets(fieldOffsets);
        access.registerAsImmutable(fieldOffsets);
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        ShenandoahAllocationSupport.registerForeignCalls(foreignCalls);
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        SubstrateAllocationSnippets allocationSnippets = ImageSingletons.lookup(SubstrateAllocationSnippets.class);
        SubstrateAllocationSnippets.Templates templates = new SubstrateAllocationSnippets.Templates(options, providers, allocationSnippets);
        templates.registerLowering(lowerings);
    }

    private static void verifyOptionsAndPlatform() {
        UserError.guarantee(Platform.includedIn(Platform.LINUX_AMD64.class) || Platform.includedIn(Platform.LINUX_AARCH64.class),
                        "The Shenandoah garbage collector ('--gc=shenandoah') is currently only supported on linux/amd64 and linux/aarch64.");

        verifyOptionEnabled(SubstrateOptions.SpawnIsolates);
        verifyOptionEnabled(SubstrateOptions.AllowVMInternalThreads);
        verifyOptionEnabled(SubstrateOptions.ConcealedOptions.UseDedicatedVMOperationThread);
        verifyOptionEnabled(SubstrateOptions.ConcealedOptions.AutomaticReferenceHandling);
        verifyOptionEnabled(SubstrateOptions.UseNullRegion);

        UserError.guarantee(!SubstrateOptions.supportCompileInIsolates(), "The Shenandoah garbage collector ('--gc=shenandoah') does not support isolated compilation.");
    }

    private static void verifyOptionEnabled(SubstrateOptionKey<Boolean> option) {
        String optionMustBeEnabledFmt = "When using the Shenandoah garbage collector ('--gc=shenandoah'), please note that option '%s' must be enabled.";
        UserError.guarantee(option.getValue(), optionMustBeEnabledFmt, option.getName());
    }

    /**
     * Layout of instance objects:
     * <ul>
     * <li>32/64 bit mark word/identity hashcode</li>
     * <li>32 bit hub reference</li>
     * <li>instance fields (references, primitives)</li>
     * <li>32/64 bit object monitor reference (if needed)</li>
     * </ul>
     *
     * Layout of array objects:
     * <ul>
     * <li>32/64 bit mark word/identity hashcode</li>
     * <li>32 bit hub reference</li>
     * <li>32 bit array length</li>
     * <li>array elements (length * elementSize)</li>
     * </ul>
     */
    private static ObjectLayout createObjectLayout(boolean useCompressedReferences) {
        SubstrateTargetDescription target = ConfigurationValues.getTarget();
        int referenceSize = computeReferenceSize(target, useCompressedReferences);
        int intSize = target.arch.getPlatformKind(JavaKind.Int).getSizeInBytes();
        int objectAlignment = 8;

        int markWordSize = referenceSize;
        int hubSize = Integer.BYTES;

        int markWordOffset = ShenandoahObjectHeader.getMarkWordOffset();
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

    private static int computeReferenceSize(SubstrateTargetDescription target, boolean useCompressedReferences) {
        JavaKind referenceKind = JavaKind.Object;
        if (useCompressedReferences) {
            referenceKind = JavaKind.Int;
        }
        return target.arch.getPlatformKind(referenceKind).getSizeInBytes();
    }
}
