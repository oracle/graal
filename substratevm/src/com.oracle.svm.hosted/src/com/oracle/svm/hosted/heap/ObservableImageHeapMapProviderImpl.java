/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.heap.ImageHeapScanner;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.ObservableImageHeapMapProvider;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.util.ObservableMap;

public class ObservableImageHeapMapProviderImpl implements ObservableImageHeapMapProvider {
    private List<ObservableMap<?, ?>> cachedInstances = new ArrayList<>();
    private ImageHeapScanner heapScanner;

    @Override
    public <K, V> ObservableMap<K, V> createMap() {
        ObservableMap<K, V> map = new ObservableMap<>();
        if (heapScanner == null) {
            /* Heap scanner not yet available, cache instance. */
            cacheInstance(map);
        } else {
            registerWithScanner(map);
        }
        return map;
    }

    private synchronized <K, V> void cacheInstance(ObservableMap<K, V> map) {
        if (heapScanner == null) {
            cachedInstances.add(map);
        } else {
            registerWithScanner(map);
        }
    }

    synchronized void setHeapScanner(ImageHeapScanner scanner) {
        heapScanner = scanner;
        /* Register observer with cached instances. */
        cachedInstances.forEach(this::registerWithScanner);
        cachedInstances = null;
    }

    private <K, V> void registerWithScanner(ObservableMap<K, V> map) {
        /* Scan map values when they are added. */
        map.addObserver((key, value) -> heapScanner.rescanObject(value));
    }
}

@AutomaticallyRegisteredFeature
final class ObservableHeapMapFeature implements InternalFeature {

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        /*
         * Set the heap scanner beforeAnalysis, i.e., only after all other features have finished
         * their set-up. We want to make sure that all features have already registered the object
         * replacers before any scanning can happen, e.g., such as HostedDynamicHubFeature.
         */
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;
        ObservableImageHeapMapProviderImpl provider = (ObservableImageHeapMapProviderImpl) ImageSingletons.lookup(ObservableImageHeapMapProvider.class);
        provider.setHeapScanner(access.getUniverse().getHeapScanner());
    }
}
