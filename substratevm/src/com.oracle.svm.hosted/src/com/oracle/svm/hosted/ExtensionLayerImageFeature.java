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

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

import org.graalvm.collections.EconomicMap;

import com.oracle.graal.pointsto.api.HostVM;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.common.layeredimage.LayeredCompilationBehavior;
import com.oracle.svm.core.c.BoxedRelocatedPointer;
import com.oracle.svm.core.code.ImageCodeInfo;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.BeforeAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.BeforeCompilationAccessImpl;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.imagelayer.InitialLayerFeature;
import com.oracle.svm.hosted.imagelayer.SVMImageLayerLoader;
import com.oracle.svm.util.ReflectionUtil;

/**
 * This feature contains some configs currently necessary to build an extension layer. We'll need
 * better mechanisms to avoid these workarounds.
 */
@AutomaticallyRegisteredFeature
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Independent.class)
final class ExtensionLayerImageFeature implements InternalFeature {
    private static final Object NULL_SUPER_CORE_TYPE = new Object();

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

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        BeforeCompilationAccessImpl access = (BeforeCompilationAccessImpl) a;
        AnalysisUniverse universe = access.aUniverse;
        HostVM hostVM = universe.hostVM();
        SVMImageLayerLoader imageLayerLoader = HostedImageLayerBuildingSupport.singleton().getLoader();
        EconomicMap<AnalysisType, Object> superCoreTypes = EconomicMap.create();

        for (var type : universe.getTypes()) {
            /*
             * These checks allow to ensure that core types can be treated as closed (see
             * PointsToAnalysis.isClosed(AnalysisType)).
             */
            boolean coreType = hostVM.isCoreType(type);
            if (coreType) {
                superCoreTypes.put(type, type);
            }
            AnalysisType superCoreType = getSuperCoreType(type, hostVM, superCoreTypes);
            boolean extendsCoreType = superCoreType != null && !coreType;
            if (coreType || extendsCoreType) {
                /*
                 * This checks that all core types and types that extend or implement a core type
                 * were not marked as reachable or instantiated only in an extension layer, showing
                 * that the analysis of the core was complete.
                 */
                checkCondition(type.isReachable(), imageLayerLoader::isReachableInPreviousLayer, type, extendsCoreType, superCoreType, "reachable");
                checkCondition(type.isInstantiated(), imageLayerLoader::isInstantiatedInPreviousLayer, type, extendsCoreType, superCoreType, "instantiated");
            }
        }
    }

    public AnalysisType getSuperCoreType(AnalysisType type, HostVM hostVM, EconomicMap<AnalysisType, Object> superCoreTypes) {
        /* Check the cache to see if the super core type was already computed. */
        if (superCoreTypes.containsKey(type)) {
            Object result = superCoreTypes.get(type);
            return result == NULL_SUPER_CORE_TYPE ? null : (AnalysisType) result;
        }

        AnalysisType result = null;

        /* Go through the super types to check if one of them is a core type. */
        AnalysisType superType = type.getSuperclass();
        if (superType != null) {
            result = hostVM.isCoreType(superType) ? superType : getSuperCoreType(superType, hostVM, superCoreTypes);
        }

        if (result == null) {
            /*
             * If no result was found, iterate through all interfaces to see if one of them is a
             * core type.
             */
            AnalysisType[] interfaces = type.getInterfaces();
            var coreInterface = Arrays.stream(interfaces).map(i -> getSuperCoreType(i, hostVM, superCoreTypes)).filter(Objects::nonNull).findFirst();
            if (coreInterface.isPresent()) {
                result = coreInterface.get();
            }
        }

        /* Cache the result. */
        superCoreTypes.put(type, result == null ? NULL_SUPER_CORE_TYPE : result);

        return result;
    }

    private static void checkCondition(boolean condition, Predicate<AnalysisType> test, AnalysisType type, boolean extendsCoreType, AnalysisType superCoreType, String kind) {
        if (condition) {
            String hint = "Please make sure that all core types are seen by the initial layer analysis by either registering more entry points using " +
                            LayeredCompilationBehavior.class + " with the " + LayeredCompilationBehavior.Behavior.PINNED_TO_INITIAL_LAYER +
                            " value or by explicitly registering the entry point or type reachability in " + InitialLayerFeature.class;
            if (extendsCoreType) {
                VMError.guarantee(test.test(type), "The type %s is extending the core type %s which was not seen as %s the initial layer. " +
                                "It is illegal to extend core types in subsequent layers. %s", type, superCoreType, kind, hint);
            } else {
                VMError.guarantee(test.test(type), "The core type %s was not seen as %s the initial layer. " +
                                "It is illegal for core types to become reachable in subsequent layers. %s", type, kind, hint);
            }
        }
    }
}
