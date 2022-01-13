/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.util.ImageHeapMap.HostedImageHeapMap;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;

@AutomaticFeature
final class ImageHeapMapFeature implements Feature {

    private final Set<HostedImageHeapMap<?, ?>> allInstances = ConcurrentHashMap.newKeySet();

    @Override
    public void duringSetup(DuringSetupAccess config) {
        config.registerObjectReplacer(this::imageHeapMapTransformer);
    }

    private Object imageHeapMapTransformer(Object obj) {
        if (obj instanceof HostedImageHeapMap) {
            HostedImageHeapMap<?, ?> hostedImageHeapMap = (HostedImageHeapMap<?, ?>) obj;
            if (BuildPhaseProvider.isAnalysisFinished()) {
                VMError.guarantee(allInstances.contains(hostedImageHeapMap), "ImageHeapMap reachable after analysis that was not seen during analysis");
            } else {
                allInstances.add(hostedImageHeapMap);
            }
            return hostedImageHeapMap.runtimeMap;
        }
        return obj;
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        for (HostedImageHeapMap<?, ?> hostedImageHeapMap : allInstances) {
            if (needsUpdate(hostedImageHeapMap)) {
                update(hostedImageHeapMap);
                access.requireAnalysisIteration();
            }
        }
    }

    public boolean imageHeapMapNeedsUpdate() {
        for (HostedImageHeapMap<?, ?> hostedImageHeapMap : allInstances) {
            if (needsUpdate(hostedImageHeapMap)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterImageWrite(AfterImageWriteAccess access) {
        for (HostedImageHeapMap<?, ?> hostedImageHeapMap : allInstances) {
            if (needsUpdate(hostedImageHeapMap)) {
                throw VMError.shouldNotReachHere("ImageHeapMap modified after static analysis: " + hostedImageHeapMap + System.lineSeparator() + hostedImageHeapMap.runtimeMap);
            }
        }
    }

    private static boolean needsUpdate(HostedImageHeapMap<?, ?> hostedMap) {
        EconomicMap<Object, Object> runtimeMap = hostedMap.runtimeMap;
        if (hostedMap.size() != runtimeMap.size()) {
            return true;
        }
        MapCursor<?, ?> hostedEntry = hostedMap.getEntries();
        while (hostedEntry.advance()) {
            Object hostedValue = hostedEntry.getValue();
            Object runtimeValue = runtimeMap.get(hostedEntry.getKey());
            if (hostedValue != runtimeValue) {
                return true;
            }
        }
        return false;
    }

    private static void update(HostedImageHeapMap<?, ?> hostedMap) {
        hostedMap.runtimeMap.clear();
        hostedMap.runtimeMap.putAll(hostedMap);
    }
}
