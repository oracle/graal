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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            ImageSingletons.add(LayerTypeCheckInfo.class, new LayerTypeCheckInfo());
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

    public static void persistTypeInfo(Collection<HostedType> types) {
        if (ImageLayerBuildingSupport.buildingImageLayer()) {
            ImageSingletons.lookup(LayerTypeCheckInfo.class).persistTypeInfo(types);
        }
    }

    record TypeCheckInfo(int typeID, int numClassTypes, int numInterfaceTypes, int[] typecheckSlots) {
        private List<Integer> toIntList() {
            ArrayList<Integer> list = new ArrayList<>();
            list.add(typeID);
            list.add(numClassTypes);
            list.add(numInterfaceTypes);
            Arrays.stream(typecheckSlots).forEach(list::add);

            return list;
        }

        private static TypeCheckInfo fromIntList(List<Integer> list) {
            int typeID = list.get(0);
            int numClassTypes = list.get(1);
            int numInterfaceTypes = list.get(2);
            int[] typecheckSlots = list.subList(3, list.size()).stream().mapToInt(i -> i).toArray();
            return new TypeCheckInfo(typeID, numClassTypes, numInterfaceTypes, typecheckSlots);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TypeCheckInfo typeCheckInfo = (TypeCheckInfo) o;
            return typeID == typeCheckInfo.typeID && numClassTypes == typeCheckInfo.numClassTypes && numInterfaceTypes == typeCheckInfo.numInterfaceTypes &&
                            Arrays.equals(typecheckSlots, typeCheckInfo.typecheckSlots);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(typeID, numClassTypes, numInterfaceTypes);
            result = 31 * result + Arrays.hashCode(typecheckSlots);
            return result;
        }
    }

    private static final class LayerTypeCheckInfo implements LayeredImageSingleton {
        Map<Integer, TypeCheckInfo> identifierToTypeInfo = new HashMap<>();
        int maxTypeID = 0;

        public int loadTypeID(Collection<HostedType> types) {
            ArrayList<Integer> usedIDs = new ArrayList<>();
            for (HostedType type : types) {
                int identifierID = type.getWrapped().getId();
                TypeCheckInfo info = identifierToTypeInfo.get(identifierID);
                if (info != null) {
                    usedIDs.add(info.typeID);
                    type.loadTypeID(info.typeID);
                }
            }

            return maxTypeID;
        }

        public void persistTypeInfo(Collection<HostedType> types) {
            for (HostedType type : types) {
                /*
                 * Currently we are calculating type id information for all types. However, for
                 * types not tracked across layers, the type ID may not be the same in different
                 * layers.
                 */
                assert type.getTypeID() != -1 : type;
                if (type.getWrapped().isTrackedAcrossLayers()) {
                    int identifierID = type.getWrapped().getId();
                    int typeID = type.getTypeID();
                    int numClassTypes = type.getNumClassTypes();
                    int numInterfaceTypes = type.getNumInterfaceTypes();
                    int[] typecheckSlots = type.getOpenTypeWorldTypeCheckSlots();
                    var priorInfo = identifierToTypeInfo.get(identifierID);
                    var newTypeInfo = new TypeCheckInfo(typeID, numClassTypes, numInterfaceTypes, typecheckSlots);
                    if (priorInfo == null) {
                        identifierToTypeInfo.put(identifierID, newTypeInfo);
                    } else {
                        assert newTypeInfo.equals(priorInfo) : Assertions.errorMessage("Mismatch for ", type, priorInfo, newTypeInfo, Arrays.toString(priorInfo.typecheckSlots),
                                        Arrays.toString(newTypeInfo.typecheckSlots));
                    }
                }
            }
        }

        @Override
        public EnumSet<LayeredImageSingletonBuilderFlags> getImageBuilderFlags() {
            return LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY;
        }

        private static String getTypeInfoKey(int id) {
            return String.format("TypeInfo-%s", id);
        }

        @Override
        public PersistFlags preparePersist(ImageSingletonWriter writer) {
            /*
             * Note all that is strictly needed to restore the typecheck information is the
             * (identifierID -> typeID) mappings. In the future we can compact the amount of
             * information we store.
             */
            var typeIdentifierIds = identifierToTypeInfo.keySet().stream().sorted().toList();
            writer.writeIntList("typeIdentifierIds", typeIdentifierIds);
            writer.writeInt("maxTypeID", DynamicHubSupport.currentLayer().getMaxTypeId());

            for (int identifierID : typeIdentifierIds) {
                var typeInfo = identifierToTypeInfo.get(identifierID);
                assert typeInfo != null;
                writer.writeIntList(getTypeInfoKey(identifierID), typeInfo.toIntList());
            }

            return PersistFlags.CREATE;
        }

        @SuppressWarnings("unused")
        public static Object createFromLoader(ImageSingletonLoader loader) {
            var info = new LayerTypeCheckInfo();
            info.maxTypeID = loader.readInt("maxTypeID");
            List<Integer> typeIdentifierIds = loader.readIntList("typeIdentifierIds");
            for (var identifierID : typeIdentifierIds) {
                Object previous = info.identifierToTypeInfo.put(identifierID, TypeCheckInfo.fromIntList(loader.readIntList(getTypeInfoKey(identifierID))));
                assert previous == null : previous;
            }

            return info;
        }
    }
}
