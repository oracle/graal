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
package com.oracle.svm.hosted;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;
import com.oracle.svm.hosted.heap.ImageHeapObjectAdder;
import com.oracle.svm.hosted.image.NativeImageHeap;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.util.ReflectionUtil;

@AutomaticallyRegisteredFeature
public class DynamicHubSupportFeature implements InternalFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(DynamicHubSupport.class, new DynamicHubSupport());
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageHeapObjectAdder.singleton().registerObjectAdder(DynamicHubSupportFeature::addReferenceMapEncodingToImageHeap);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        BeforeAnalysisAccessImpl a = (BeforeAnalysisAccessImpl) access;
        a.registerAsInHeap(DynamicHubSupport.class);
        a.registerAsRead(ReflectionUtil.lookupField(DynamicHubSupport.class, "referenceMapEncoding"), "needed by the GC");
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        BeforeCompilationAccessImpl a = (BeforeCompilationAccessImpl) access;
        a.getHeapScanner().rescanField(DynamicHubSupport.currentLayer(), ReflectionUtil.lookupField(DynamicHubSupport.class, "referenceMapEncoding"));
    }

    /**
     * At runtime, no code directly accesses {@code DynamicHubSupport.referenceMapEncoding}.
     * However, the object referenced by this field must still be included in the image heap because
     * each {@link DynamicHub} contains a heap-base relative offset into that object (see
     * {@link DynamicHub#getReferenceMapCompressedOffset()}). Therefore, we must add this object to
     * the image heap manually.
     */
    private static void addReferenceMapEncodingToImageHeap(NativeImageHeap heap, HostedUniverse hUniverse) {
        byte[] referenceMapEncoding = DynamicHubSupport.currentLayer().getReferenceMapEncoding();
        ImageHeapConstant singletonConstant = (ImageHeapConstant) hUniverse.getSnippetReflection().forObject(referenceMapEncoding);
        heap.addConstant(singletonConstant, false, "Registered as a required heap constant within DynamicHubSupportFeature");
    }
}
