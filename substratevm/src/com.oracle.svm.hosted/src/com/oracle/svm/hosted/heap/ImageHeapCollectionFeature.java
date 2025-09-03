/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.util.ImageHeapList.HostedImageHeapList;
import com.oracle.svm.core.util.ImageHeapMap.HostedImageHeapMap;
import com.oracle.svm.core.util.LayeredHostedImageHeapMapCollector;
import com.oracle.svm.core.util.LayeredImageHeapMap;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;

@AutomaticallyRegisteredFeature
final class ImageHeapCollectionFeature implements InternalFeature {

    private final Set<HostedImageHeapMap<?, ?>> allMaps = ConcurrentHashMap.newKeySet();
    private final Set<HostedImageHeapList<?>> allLists = ConcurrentHashMap.newKeySet();

    @Override
    public void duringSetup(DuringSetupAccess config) {
        config.registerObjectReplacer(this::replaceHostedWithRuntime);
    }

    private Object replaceHostedWithRuntime(Object obj) {
        if (obj instanceof HostedImageHeapMap) {
            HostedImageHeapMap<?, ?> hostedImageHeapMap = (HostedImageHeapMap<?, ?>) obj;
            if (BuildPhaseProvider.isAnalysisFinished()) {
                VMError.guarantee(allMaps.contains(hostedImageHeapMap), "ImageHeapMap reachable after analysis that was not seen during analysis");
            } else {
                allMaps.add(hostedImageHeapMap);
                if (hostedImageHeapMap.getRuntimeMap() instanceof LayeredImageHeapMap<Object, Object> layeredImageHeapMap) {
                    LayeredHostedImageHeapMapCollector.singleton().registerReachableHostedImageHeapMap(layeredImageHeapMap);
                }
            }
            return hostedImageHeapMap.getRuntimeMap();
        } else if (obj instanceof HostedImageHeapList<?> hostedImageHeapList) {
            if (BuildPhaseProvider.isAnalysisFinished()) {
                VMError.guarantee(allLists.contains(hostedImageHeapList), "ImageHeapList reachable after analysis that was not seen during analysis");
            } else {
                allLists.add(hostedImageHeapList);
            }
            return hostedImageHeapList.getRuntimeList();
        }
        return obj;
    }

    /**
     * This method makes sure that the content of all modified {@link HostedImageHeapMap}s and
     * {@link HostedImageHeapList}s is properly propagated to their runtime counterparts. As both
     * the number of these collections and their individual sizes are theoretically unbounded, we
     * use <i>parallel streams</i> to divide the load across all cores.
     * <p>
     * We split the process into two stages. First, the content of each modified collection is
     * propagated from the hosted to the runtime version. Then, the modified runtime collections are
     * rescanned. The split is done to prevent concurrent modifications of the hosted collections
     * during the execution of this method, as they may be updated indirectly during the heap
     * scanning.
     */
    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
            allMaps.addAll(LayeredHostedImageHeapMapCollector.singleton().getPreviousLayerReachableMaps());
        }
        Set<Object> objectsToRescan = ConcurrentHashMap.newKeySet();
        allMaps.parallelStream().forEach(hostedImageHeapMap -> {
            if (hostedImageHeapMap.needsUpdate()) {
                hostedImageHeapMap.update();
                objectsToRescan.add(hostedImageHeapMap.getCurrentLayerMap());
            }
        });
        allLists.parallelStream().forEach(hostedImageHeapList -> {
            if (hostedImageHeapList.needsUpdate()) {
                hostedImageHeapList.update();
                objectsToRescan.add(hostedImageHeapList.getRuntimeList());
            }
        });
        if (!objectsToRescan.isEmpty()) {
            objectsToRescan.parallelStream().forEach(access::rescanObject);
            access.requireAnalysisIteration();
        }
    }

    public boolean needsUpdate() {
        for (var hostedImageHeapMap : allMaps) {
            if (hostedImageHeapMap.needsUpdate()) {
                return true;
            }
        }
        for (var hostedImageHeapList : allLists) {
            if (hostedImageHeapList.needsUpdate()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        for (var hostedImageHeapMap : allMaps) {
            if (hostedImageHeapMap.needsUpdate()) {
                throw VMError.shouldNotReachHere("ImageHeapMap modified after static analysis:%n%s%n%s",
                                hostedImageHeapMap, hostedImageHeapMap.getCurrentLayerMap());
            }
        }
        for (var hostedImageHeapList : allLists) {
            if (hostedImageHeapList.needsUpdate()) {
                throw VMError.shouldNotReachHere("ImageHeapList modified after static analysis:%n%s%n%s",
                                hostedImageHeapList, hostedImageHeapList.getRuntimeList());

            }
        }
    }
}
