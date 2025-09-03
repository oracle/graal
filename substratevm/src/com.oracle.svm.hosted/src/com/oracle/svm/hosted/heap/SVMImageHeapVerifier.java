/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.heap;

import java.util.Map;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.ObjectScanner;
import com.oracle.graal.pointsto.heap.HeapSnapshotVerifier;
import com.oracle.graal.pointsto.heap.ImageHeap;
import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.graal.pointsto.infrastructure.UniverseMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.CompletionExecutor;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.hosted.SVMHost;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;

public class SVMImageHeapVerifier extends HeapSnapshotVerifier {
    public SVMImageHeapVerifier(BigBang bb, ImageHeap imageHeap, ImageHeapScanner scanner) {
        super(bb, imageHeap, scanner);
    }

    @Override
    public boolean checkHeapSnapshot(UniverseMetaAccess metaAccess, CompletionExecutor executor, String phase, boolean forAnalysis, Map<Constant, Object> embeddedConstants) {
        boolean skipReachableCheck = forAnalysis && ImageLayerBuildingSupport.buildingExtensionLayer();
        return super.checkHeapSnapshot(metaAccess, executor, phase, forAnalysis, embeddedConstants, skipReachableCheck) || imageStateModified();
    }

    /**
     * An additional analysis iteration is required if a verification run modifies state such as:
     * 
     * - an image heap map, e.g., via an object replacer like
     * com.oracle.svm.enterprise.core.stringformat.StringFormatFeature.collectZeroDigits(). Signal
     * this by returning true to make sure that
     * com.oracle.graal.pointsto.heap.ImageHeapCollectionFeature.duringAnalysis() is run to properly
     * patch all ImageHeapMaps.
     * 
     * - runtime reflection registration.
     * 
     */
    private static boolean imageStateModified() {
        return ImageSingletons.lookup(ImageHeapCollectionFeature.class).needsUpdate();
    }

    @Override
    protected void scanTypes(ObjectScanner objectScanner) {
        SVMHost svmHost = svmHost();
        /* Verify the snapshots of reachable types, i.e., compare them with hosted values. */
        bb.getUniverse().getTypes().stream().filter(AnalysisType::isReachable).forEach(t -> verifyHub(svmHost, objectScanner, t));
    }

    private void verifyHub(SVMHost svmHost, ObjectScanner objectScanner, AnalysisType type) {
        JavaConstant hubConstant = bb.getSnippetReflectionProvider().forObject(svmHost.dynamicHub(type));
        objectScanner.scanConstant(hubConstant, ObjectScanner.OtherReason.HUB);
    }

    private SVMHost svmHost() {
        return (SVMHost) bb.getHostVM();
    }
}
