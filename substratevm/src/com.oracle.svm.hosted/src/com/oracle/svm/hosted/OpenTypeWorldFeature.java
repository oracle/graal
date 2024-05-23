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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LoadedLayeredImageSingletonInfo;
import com.oracle.svm.hosted.meta.HostedType;

import jdk.graal.compiler.debug.Assertions;

@AutomaticallyRegisteredFeature
public class OpenTypeWorldFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(Feature.IsInConfigurationAccess access) {
        return !SubstrateOptions.closedTypeWorld();
    }

    @Override
    public void beforeUniverseBuilding(BeforeUniverseBuildingAccess access) {
        if (SVMImageLayerSupport.singleton().persistImageSingletons() && !LoadedLayeredImageSingletonInfo.singleton().handledDuringLoading(LayerTypeInfo.class)) {
            ImageSingletons.add(LayerTypeInfo.class, new LayerTypeInfo());
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
        if (ImageSingletons.contains(LayerTypeInfo.class) && SVMImageLayerSupport.singleton().loadAnalysis()) {
            /*
             * Load analysis must be enabled or otherwise the same Analysis Type id will not be
             * reassigned across layers.
             */
            return ImageSingletons.lookup(LayerTypeInfo.class).loadTypeID(types);
        }

        return 0;
    }

    public static void persistTypeInfo(Collection<HostedType> types) {
        if (ImageSingletons.contains(LayerTypeInfo.class)) {
            ImageSingletons.lookup(LayerTypeInfo.class).persistTypeInfo(types);
        }
    }

    record TypeInfo(int typeID, int numClassTypes, int numInterfaceTypes, int[] typecheckSlots) {
        private List<Integer> toIntList() {
            ArrayList<Integer> list = new ArrayList<>();
            list.add(typeID);
            list.add(numClassTypes);
            list.add(numInterfaceTypes);
            Arrays.stream(typecheckSlots).forEach(list::add);

            return list;
        }

        private static TypeInfo fromIntList(List<Integer> list) {
            int typeID = list.get(0);
            int numClassTypes = list.get(1);
            int numInterfaceTypes = list.get(2);
            int[] typecheckSlots = list.subList(3, list.size()).stream().mapToInt(i -> i).toArray();
            return new TypeInfo(typeID, numClassTypes, numInterfaceTypes, typecheckSlots);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TypeInfo typeInfo = (TypeInfo) o;
            return typeID == typeInfo.typeID && numClassTypes == typeInfo.numClassTypes && numInterfaceTypes == typeInfo.numInterfaceTypes && Arrays.equals(typecheckSlots, typeInfo.typecheckSlots);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(typeID, numClassTypes, numInterfaceTypes);
            result = 31 * result + Arrays.hashCode(typecheckSlots);
            return result;
        }
    }

    private static class LayerTypeInfo implements LayeredImageSingleton {
        Map<Integer, TypeInfo> identifierToTypeInfo = new HashMap<>();
        int maxTypeID = 0;

        public int loadTypeID(Collection<HostedType> types) {
            ArrayList<Integer> usedIDs = new ArrayList<>();
            for (HostedType type : types) {
                int identifierID = type.getWrapped().getId();
                TypeInfo info = identifierToTypeInfo.get(identifierID);
                if (info != null) {
                    usedIDs.add(info.typeID);
                    type.loadTypeID(info.typeID);
                }
            }

            return maxTypeID;
        }

        public void persistTypeInfo(Collection<HostedType> types) {
            for (HostedType type : types) {
                if (type.getTypeID() != -1) {
                    int identifierID = type.getWrapped().getId();
                    int typeID = type.getTypeID();
                    int numClassTypes = type.getNumClassTypes();
                    int numInterfaceTypes = type.getNumInterfaceTypes();
                    int[] typecheckSlots = type.getOpenTypeWorldTypeCheckSlots();
                    var priorInfo = identifierToTypeInfo.get(identifierID);
                    var newTypeInfo = new TypeInfo(typeID, numClassTypes, numInterfaceTypes, typecheckSlots);
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
        public EnumSet<ImageBuilderFlags> getImageBuilderFlags() {
            return EnumSet.of(ImageBuilderFlags.BUILDTIME_ACCESS);
        }

        @Override
        public PersistFlags preparePersist(ImageSingletonWriter writer) {
            /*
             * Note all that is strictly needed to restore the typecheck information is the
             * (identifierID -> typeID) mappings. In the future we can compact the amount of
             * information we store.
             */
            var identifierIDs = identifierToTypeInfo.keySet().stream().sorted().toList();
            writer.writeIntList("identifierIDs", identifierIDs);
            writer.writeInt("maxTypeID", DynamicHubSupport.singleton().getMaxTypeId());

            for (int identifierID : identifierIDs) {
                var typeInfo = identifierToTypeInfo.get(identifierID);
                writer.writeIntList(Integer.toString(identifierID), typeInfo.toIntList());
            }

            return PersistFlags.CREATE;
        }

        @SuppressWarnings("unused")
        public static Object createFromLoader(ImageSingletonLoader loader) {
            var info = new LayerTypeInfo();
            info.maxTypeID = loader.readInt("maxTypeID");
            List<Integer> identifierIDs = loader.readIntList("identifierIDs");
            for (var identifierID : identifierIDs) {
                var previous = info.identifierToTypeInfo.put(identifierID, TypeInfo.fromIntList(loader.readIntList(Integer.toString(identifierID))));
                assert previous == null : previous;
            }
            return info;
        }
    }
}
