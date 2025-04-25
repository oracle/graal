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

package com.oracle.svm.hosted.webimage.wasmgc;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.genscavenge.remset.NoRememberedSet;
import com.oracle.svm.core.genscavenge.remset.RememberedSet;
import com.oracle.svm.core.graal.snippets.GCAllocationSupport;
import com.oracle.svm.core.heap.BarrierSetProvider;
import com.oracle.svm.core.heap.Heap;
import com.oracle.svm.core.image.ImageHeapLayouter;
import com.oracle.svm.hosted.webimage.wasmgc.image.WasmGCHeapLayouter;
import com.oracle.svm.webimage.heap.WebImageJSHeap;
import com.oracle.svm.webimage.heap.WebImageNopAllocationSupport;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;

import jdk.graal.compiler.nodes.gc.NoBarrierSet;

@AutomaticallyRegisteredFeature
@Platforms(WebImageWasmGCPlatform.class)
public class WasmGCHeapFeature implements InternalFeature {
    @Override
    public void duringSetup(DuringSetupAccess access) {
        ImageSingletons.add(GCAllocationSupport.class, new WebImageNopAllocationSupport());
    }

    @Override
    public void afterRegistration(Feature.AfterRegistrationAccess access) {
        ImageSingletons.add(BarrierSetProvider.class, metaAccess -> new NoBarrierSet());
        ImageSingletons.add(Heap.class, new WebImageJSHeap());
        ImageSingletons.add(RememberedSet.class, new NoRememberedSet());
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        ImageSingletons.add(ImageHeapLayouter.class, new WasmGCHeapLayouter());
    }
}
