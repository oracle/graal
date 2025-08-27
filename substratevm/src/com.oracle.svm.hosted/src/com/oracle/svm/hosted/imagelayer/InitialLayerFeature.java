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

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.util.ReflectionUtil;

import jdk.internal.misc.Unsafe;

@AutomaticallyRegisteredFeature
public class InitialLayerFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(Feature.IsInConfigurationAccess access) {
        return ImageLayerBuildingSupport.buildingInitialLayer();
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        BeforeAnalysisAccessImpl access = (BeforeAnalysisAccessImpl) a;

        /*
         * Make sure that critical VM components are included in the base layer by registering
         * runtime APIs as entry points. Although the types below are part of java.base, so they
         * would anyway be included in every base layer created with module=java.base, this ensures
         * that the base layer is usable regardless of the class inclusion policy.
         */
        String pinReason = "base layer entry point included unconditionally";
        AnalysisMetaAccess metaAccess = access.getMetaAccess();
        metaAccess.lookupJavaMethod(ReflectionUtil.lookupMethod(Unsafe.class, "getUnsafe")).setPinnedToInitialLayer(pinReason);
        metaAccess.lookupJavaMethod(ReflectionUtil.lookupMethod(Unsafe.class, "allocateInstance", Class.class)).setPinnedToInitialLayer(pinReason);
        metaAccess.lookupJavaMethod(ReflectionUtil.lookupMethod(Runtime.class, "getRuntime")).setPinnedToInitialLayer(pinReason);
        metaAccess.lookupJavaMethod(ReflectionUtil.lookupMethod(Runtime.class, "gc")).setPinnedToInitialLayer(pinReason);
        metaAccess.lookupJavaMethod(ReflectionUtil.lookupMethod(Class.class, "getResource", String.class)).setPinnedToInitialLayer(pinReason);
    }

}
