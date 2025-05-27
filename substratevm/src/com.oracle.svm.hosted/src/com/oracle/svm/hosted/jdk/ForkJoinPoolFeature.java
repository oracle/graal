/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jdk;

import java.util.concurrent.ForkJoinPool;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.jdk.DeferredCommonPool;
import com.oracle.svm.core.layeredimagesingleton.FeatureSingleton;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.imagelayer.CrossLayerConstantRegistry;

import jdk.vm.ci.meta.JavaConstant;

@AutomaticallyRegisteredFeature
class ForkJoinPoolFeature implements InternalFeature, FeatureSingleton {

    private static final String KEY_NAME = "ForkJoinPool#commonPool";

    @Override
    public void duringSetup(DuringSetupAccess access) {
        CrossLayerConstantRegistry registry = CrossLayerConstantRegistry.singletonOrNull();
        if (ImageLayerBuildingSupport.buildingExtensionLayer() && registry.constantExists(KEY_NAME)) {
            ((FeatureImpl.DuringSetupAccessImpl) access).registerObjectToConstantReplacer(obj -> (ImageHeapConstant) replaceCommonPoolWithLayerConstant(registry, obj));
        } else {
            var commonPool = new DeferredCommonPool();
            access.registerObjectReplacer(obj -> replaceCommonPoolWithRuntimeObject(obj, commonPool));

            if (ImageLayerBuildingSupport.buildingSharedLayer()) {
                registry.registerConstantCandidate(KEY_NAME, commonPool);
            }
        }
    }

    private static Object replaceCommonPoolWithRuntimeObject(Object original, DeferredCommonPool commonPool) {
        if (original == ForkJoinPool.commonPool()) {
            return commonPool;
        }
        return original;
    }

    private static JavaConstant replaceCommonPoolWithLayerConstant(CrossLayerConstantRegistry registry, Object original) {
        if (original == ForkJoinPool.commonPool()) {
            return registry.getConstant(KEY_NAME);
        }
        return null;
    }
}
