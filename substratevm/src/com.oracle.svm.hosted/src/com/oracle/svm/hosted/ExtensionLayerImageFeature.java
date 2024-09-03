/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.c.BoxedRelocatedPointer;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.util.ReflectionUtil;

/**
 * This feature contains some configs currently necessary to build an extension layer. We'll need
 * better mechanisms to avoid these workarounds.
 */
@AutomaticallyRegisteredFeature
final class ExtensionLayerImageFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingExtensionLayer();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;
        /*
         * BoxedRelocatedPointer is used for implementing the indirect calls between layers. Since
         * the box object itself is only reachable late, after compilation, we need to mark it as
         * allocated and the pointer field as accessed.
         */
        access.registerAsInHeap(BoxedRelocatedPointer.class);
        access.registerAsAccessed(ReflectionUtil.lookupField(BoxedRelocatedPointer.class, "pointer"));

        /*
         * ImageCodeInfo.codeStart, used by KnownOffsetsFeature, is not normally reachable for a
         * minimal extension layer.
         */
        access.registerAsAccessed(ReflectionUtil.lookupField(ImageCodeInfo.class, "codeStart"));

        /*
         * In an extension layer build ConcurrentHashMap$CounterCell is not marked as allocated by
         * the analysis since ConcurrentHashMap.fullAddCount() is not analyzed. However, an instance
         * of this type may still be reachable when scanning ClassLoader.packages, but its
         * allocation is non-deterministic, and it depends on the contention on the map. This can
         * lead to
         * "image heap writing found an object whose type was not marked as instantiated by the static analysis"
         * transient errors when writing the heap of the extension image.
         */
        access.registerAsInHeap(ReflectionUtil.lookupClass(false, "java.util.concurrent.ConcurrentHashMap$CounterCell"));
    }
}
