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
package com.oracle.svm.hosted;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.imagelayer.SVMImageLayerLoader;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.graal.compiler.debug.Assertions;

@AutomaticallyRegisteredFeature
public class OpenTypeWorldFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(Feature.IsInConfigurationAccess access) {
        return !SubstrateOptions.useClosedTypeWorldHubLayout();
    }

    @Override
    public void beforeUniverseBuilding(BeforeUniverseBuildingAccess access) {
        if (ImageLayerBuildingSupport.buildingInitialLayer()) {
            ImageSingletons.add(LayerTypeCheckInfo.class, new LayerTypeCheckInfo(0));
        }
    }

    private final Set<AnalysisType> triggeredTypes = new HashSet<>();
    private final Set<AnalysisMethod> triggeredMethods = new HashSet<>();

    @Override
    public void duringAnalysis(DuringAnalysisAccess access) {
        var config = (FeatureImpl.DuringAnalysisAccessImpl) access;
        for (AnalysisType aType : config.getUniverse().getTypes()) {
            if (triggeredTypes.add(aType)) {
                aType.getOrCalculateOpenTypeWorldDispatchTableMethods();
                config.requireAnalysisIteration();
            }
        }
        for (AnalysisMethod aMethod : config.getUniverse().getMethods()) {
            if (triggeredMethods.add(aMethod)) {
                if (!aMethod.isStatic()) {
                    aMethod.getIndirectCallTarget();
                    config.requireAnalysisIteration();
                }
            }
        }
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        var impl = (FeatureImpl.BeforeCompilationAccessImpl) access;
        for (HostedType type : impl.getUniverse().getTypes()) {
            DynamicHub hub = type.getHub();
            impl.registerAsImmutable(hub.getOpenTypeWorldTypeCheckSlots());
        }
    }

    public static int loadTypeInfo(Collection<HostedType> types) {
        if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
            /*
             * Load analysis must be enabled or otherwise the same Analysis Type id will not be
             * reassigned across layers.
             */
            return ImageSingletons.lookup(LayerTypeCheckInfo.class).loadTypeID(types);
        } else {
            return 0;
        }
    }

    @SuppressWarnings("unused")
    public static boolean validateTypeInfo(Collection<HostedType> types) {
        if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
            var loader = HostedImageLayerBuildingSupport.singleton().getLoader();
            for (HostedType type : types) {
                if (type.getWrapped().isInBaseLayer()) {
                    var priorInfo = getTypecheckInfo(loader, type);
                    if (!priorInfo.installed()) {
                        // no need to validate this hub, as it was not installed
                        continue;
                    }
                    int typeID = type.getTypeID();
                    int numClassTypes = type.getNumClassTypes();
                    int numInterfaceTypes = type.getNumInterfaceTypes();
                    int[] typecheckSlots = type.getOpenTypeWorldTypeCheckSlots();
                    boolean matches = typeID == priorInfo.typeID && numClassTypes == priorInfo.numClassTypes && numInterfaceTypes == priorInfo.numInterfaceTypes &&
                                    Arrays.equals(typecheckSlots, priorInfo.typecheckSlots);
                    if (!matches) {
                        var typeInfo = new TypeCheckInfo(true, typeID, numClassTypes, numInterfaceTypes, typecheckSlots);
                        assert false : Assertions.errorMessage("Mismatch for ", type, priorInfo, typeInfo, Arrays.toString(priorInfo.typecheckSlots),
                                        Arrays.toString(typeInfo.typecheckSlots));

                    }
                }
            }
        }
        return true;
    }

    static TypeCheckInfo getTypecheckInfo(SVMImageLayerLoader loader, HostedType hType) {
        if (hType.getWrapped().isInBaseLayer()) {
            var hubInfo = loader.getDynamicHubInfo(hType.getWrapped());
            var valuesReader = hubInfo.getTypecheckSlotValues();
            int[] typecheckSlots = new int[valuesReader.size()];
            for (int i = 0; i < typecheckSlots.length; i++) {
                typecheckSlots[i] = valuesReader.get(i);
            }
            return new TypeCheckInfo(hubInfo.getInstalled(), hubInfo.getTypecheckId(), hubInfo.getNumClassTypes(), hubInfo.getNumInterfaceTypes(), typecheckSlots);
        } else {
            return null;
        }
    }

    record TypeCheckInfo(boolean installed, int typeID, int numClassTypes, int numInterfaceTypes, int[] typecheckSlots) {
    }

    private static final class LayerTypeCheckInfo implements LayeredImageSingleton {
        final int maxTypeID;

        LayerTypeCheckInfo(int maxTypeID) {
            this.maxTypeID = maxTypeID;
        }

        public int loadTypeID(Collection<HostedType> types) {
            var loader = HostedImageLayerBuildingSupport.singleton().getLoader();
            for (HostedType type : types) {
                TypeCheckInfo info = getTypecheckInfo(loader, type);
                if (info != null) {
                    type.loadTypeID(info.typeID);
                }
            }

            return maxTypeID;
        }

        @Override
        public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
            return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
        }

        @Override
        public PersistFlags preparePersist(ImageSingletonWriter writer) {
            writer.writeInt("maxTypeID", DynamicHubSupport.currentLayer().getMaxTypeId());

            return PersistFlags.CREATE;
        }

        @SuppressWarnings("unused")
        public static Object createFromLoader(ImageSingletonLoader loader) {
            return new LayerTypeCheckInfo(loader.readInt("maxTypeID"));
        }
    }
}
