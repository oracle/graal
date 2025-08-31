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
package com.oracle.svm.hosted.imagelayer;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.meta.HostedMethod;

@AutomaticallyRegisteredFeature
public class LayerCompilationBehaviorFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingImageLayer();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        if (ImageLayerBuildingSupport.buildingApplicationLayer()) {
            /*
             * Methods fully delayed to the application layer that are referenced in a shared layer
             * need to be marked as root and compiled in the application layer to avoid any
             * undefined reference.
             */
            SVMImageLayerLoader loader = HostedImageLayerBuildingSupport.singleton().getLoader();
            FeatureImpl.BeforeAnalysisAccessImpl access = (FeatureImpl.BeforeAnalysisAccessImpl) a;
            BigBang bigbang = access.getUniverse().getBigbang();
            for (int methodId : HostedDynamicLayerInfo.singleton().getPreviousLayerDelayedMethodIds()) {
                AnalysisMethod method = loader.getAnalysisMethodForBaseLayerId(methodId);
                bigbang.forcedAddRootMethod(method, method.isConstructor(), "Fully delayed to application layer");
            }
        }
    }

    @Override
    public void afterHeapLayout(AfterHeapLayoutAccess a) {
        boolean buildingInitialLayer = ImageLayerBuildingSupport.buildingInitialLayer();
        /* Methods pinned to a shared layer need to be compiled in the corresponding layer. */
        FeatureImpl.AfterHeapLayoutAccessImpl access = (FeatureImpl.AfterHeapLayoutAccessImpl) a;
        for (HostedMethod method : access.getHeap().hUniverse.getMethods()) {
            if (method.wrapped.isPinnedToInitialLayer()) {
                String msg = "The method %s was pinned to the initial layer but was not compiled in the initial layer";
                if (buildingInitialLayer) {
                    VMError.guarantee(method.isCompiled(), msg, method);
                } else {
                    VMError.guarantee(method.isCompiledInPriorLayer(), msg, method);
                }
            }
        }
    }
}
