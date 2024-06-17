/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge.graal;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateGCOptions;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.genscavenge.ChunkedImageHeapLayouter;
import com.oracle.svm.core.genscavenge.CompleteGarbageCollectorMXBean;
import com.oracle.svm.core.genscavenge.EpsilonGarbageCollectorMXBean;
import com.oracle.svm.core.genscavenge.GenScavengeMemoryPoolMXBeans;
import com.oracle.svm.core.genscavenge.HeapImpl;
import com.oracle.svm.core.genscavenge.HeapImplMemoryMXBean;
import com.oracle.svm.core.genscavenge.HeapVerifier;
import com.oracle.svm.core.genscavenge.ImageHeapInfo;
import com.oracle.svm.core.genscavenge.IncrementalGarbageCollectorMXBean;
import com.oracle.svm.core.genscavenge.SerialGCOptions;
import com.oracle.svm.core.genscavenge.jvmstat.EpsilonGCPerfData;
import com.oracle.svm.core.genscavenge.jvmstat.SerialGCPerfData;
import com.oracle.svm.core.genscavenge.remset.CardTableBasedRememberedSet;
import com.oracle.svm.core.genscavenge.remset.NoRememberedSet;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.graal.snippets.GCAllocationSupport;
import com.oracle.svm.core.graal.snippets.NodeLoweringProvider;
import com.oracle.svm.core.graal.snippets.SubstrateAllocationSnippets;
import com.oracle.svm.core.heap.AllocationFeature;
import com.oracle.svm.core.heap.BarrierSetProvider;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.core.jdk.RuntimeSupportFeature;
import com.oracle.svm.core.jdk.management.ManagementFeature;
import com.oracle.svm.core.jdk.management.ManagementSupport;
import com.oracle.svm.core.jvmstat.PerfDataFeature;
import com.oracle.svm.core.jvmstat.PerfDataHolder;
import com.oracle.svm.core.jvmstat.PerfManager;
import com.sun.management.GarbageCollectorMXBean;

import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;

@AutomaticallyRegisteredFeature
class GenScavengeGCFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return new com.oracle.svm.core.genscavenge.UseSerialOrEpsilonGC().getAsBoolean();
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return Arrays.asList(RuntimeSupportFeature.class, ManagementFeature.class, PerfDataFeature.class, AllocationFeature.class);
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        RememberedSet rememberedSet = createRememberedSet();
        ImageSingletons.add(RememberedSet.class, rememberedSet);
        ImageSingletons.add(BarrierSetProvider.class, rememberedSet);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        HeapImpl heap = new HeapImpl();
        ImageSingletons.add(Heap.class, heap);
        ImageSingletons.add(GCAllocationSupport.class, new GenScavengeAllocationSupport());

        GenScavengeMemoryPoolMXBeans memoryPoolMXBeans = new GenScavengeMemoryPoolMXBeans();
        ImageSingletons.add(GenScavengeMemoryPoolMXBeans.class, memoryPoolMXBeans);

        List<GarbageCollectorMXBean> garbageCollectors;
        if (SubstrateOptions.useEpsilonGC()) {
            garbageCollectors = Arrays.asList(new EpsilonGarbageCollectorMXBean());
        } else {
            garbageCollectors = Arrays.asList(new IncrementalGarbageCollectorMXBean(), new CompleteGarbageCollectorMXBean());
        }

        ManagementSupport managementSupport = ManagementSupport.getSingleton();
        managementSupport.addPlatformManagedObjectSingleton(java.lang.management.MemoryMXBean.class, new HeapImplMemoryMXBean());
        managementSupport.addPlatformManagedObjectList(java.lang.management.MemoryPoolMXBean.class, Arrays.asList(memoryPoolMXBeans.getMXBeans()));
        managementSupport.addPlatformManagedObjectList(com.sun.management.GarbageCollectorMXBean.class, garbageCollectors);
        /* Not supported yet. */
        managementSupport.addPlatformManagedObjectList(java.lang.management.BufferPoolMXBean.class, Collections.emptyList());

        if (ImageSingletons.contains(PerfManager.class)) {
            ImageSingletons.lookup(PerfManager.class).register(createPerfData());
        }

        if (SubstrateGCOptions.VerifyHeap.getValue()) {
            ImageSingletons.add(HeapVerifier.class, new HeapVerifier());
        }
    }

    @Override
    public void registerLowerings(RuntimeConfiguration runtimeConfig, OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean hosted) {
        if (SerialGCOptions.useRememberedSet()) {
            BarrierSnippets barrierSnippets = new BarrierSnippets(options, providers);
            barrierSnippets.registerLowerings(providers.getMetaAccess(), lowerings);
        }

        SubstrateAllocationSnippets allocationSnippets = ImageSingletons.lookup(SubstrateAllocationSnippets.class);
        SubstrateAllocationSnippets.Templates templates = new SubstrateAllocationSnippets.Templates(options, providers, allocationSnippets);
        templates.registerLowering(lowerings);

        GenScavengeAllocationSnippets.Templates genScavengeTemplates = new GenScavengeAllocationSnippets.Templates(options, providers, templates);
        genScavengeTemplates.registerLowering(lowerings);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // Needed for the barrier set.
        access.registerAsUsed(Object[].class);
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        ImageHeapLayouter heapLayouter = new ChunkedImageHeapLayouter(HeapImpl.getFirstImageHeapInfo(), Heap.getHeap().getImageHeapOffsetInAddressSpace());
        ImageSingletons.add(ImageHeapLayouter.class, heapLayouter);
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        ImageHeapInfo imageHeapInfo = HeapImpl.getFirstImageHeapInfo();
        access.registerAsImmutable(imageHeapInfo);
    }

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        GenScavengeAllocationSupport.registerForeignCalls(foreignCalls);
    }

    private static RememberedSet createRememberedSet() {
        if (SerialGCOptions.useRememberedSet()) {
            return new CardTableBasedRememberedSet();
        } else {
            return new NoRememberedSet();
        }
    }

    private static PerfDataHolder createPerfData() {
        if (SubstrateOptions.useSerialGC()) {
            return new SerialGCPerfData();
        } else {
            assert SubstrateOptions.useEpsilonGC();
            return new EpsilonGCPerfData();
        }
    }
}
