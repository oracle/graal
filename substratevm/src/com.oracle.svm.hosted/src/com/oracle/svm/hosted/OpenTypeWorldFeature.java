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

import static com.oracle.svm.hosted.image.NativeImage.localSymbolNameForMethod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.heap.ImageLayerLoader;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.BaseLayerType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonLoader;
import com.oracle.svm.core.layeredimagesingleton.ImageSingletonWriter;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.image.NativeImage;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.VTableBuilder;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.options.Option;

@AutomaticallyRegisteredFeature
public class OpenTypeWorldFeature implements InternalFeature {
    public static final class Options {
        @Option(help = "Log discrepancies between layered open world type information. This is an experimental option which will be removed.")//
        public static final HostedOptionKey<Boolean> LogOpenTypeWorldDiscrepancies = new HostedOptionKey<>(false);

        @Option(help = "Throw an error when there are discrepancies between layered open world type information. This is an experimental option which will be removed.")//
        public static final HostedOptionKey<Boolean> ErrorOnOpenTypeWorldDiscrepancies = new HostedOptionKey<>(false);
    }

    @Override
    public boolean isInConfiguration(Feature.IsInConfigurationAccess access) {
        return !SubstrateOptions.closedTypeWorld();
    }

    @Override
    public void beforeUniverseBuilding(BeforeUniverseBuildingAccess access) {
        if (ImageLayerBuildingSupport.buildingInitialLayer()) {
            ImageSingletons.add(LayerTypeInfo.class, new LayerTypeInfo());
        } else {
            assert !(ImageLayerBuildingSupport.buildingImageLayer() && !ImageSingletons.contains(LayerTypeInfo.class)) : "Layered image is missing layer type info";
        }
    }

    private final Set<AnalysisType> triggeredTypes = new HashSet<>();
    private final Set<AnalysisMethod> triggeredMethods = new HashSet<>();

    @Override
    public void duringSetup(DuringSetupAccess access) {
        if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
            var loader = ((FeatureImpl.DuringSetupAccessImpl) access).getUniverse().getImageLayerLoader();
            ImageSingletons.lookup(LayerTypeInfo.class).loader = loader;
        }
    }

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

        if (ImageLayerBuildingSupport.buildingSharedLayer()) {
            ImageSingletons.lookup(LayerTypeInfo.class).persistDispatchTableMethods(impl.getUniverse().getTypes());
        }
    }

    public static int loadTypeInfo(Collection<HostedType> types) {
        if (ImageSingletons.contains(LayerTypeInfo.class) && ImageLayerBuildingSupport.buildingExtensionLayer()) {
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

    public record DispatchInfo(int[] typeCheckInterfaceOrder, int[] itableStartingOffsets, String[] dispatchTables) {

        private Pair<List<Integer>, List<String>> generateLists() {
            assert typeCheckInterfaceOrder.length == itableStartingOffsets.length : Assertions.errorMessage(typeCheckInterfaceOrder, itableStartingOffsets);

            ArrayList<Integer> list = new ArrayList<>();
            list.add(typeCheckInterfaceOrder.length);
            Arrays.stream(typeCheckInterfaceOrder).forEach(list::add);
            Arrays.stream(itableStartingOffsets).forEach(list::add);

            return Pair.create(list, Arrays.asList(dispatchTables));
        }

        private static DispatchInfo fromLists(List<Integer> list, List<String> dispatchTables) {
            int numInterfaces = list.get(0);
            int startingOffset = 1;
            int[] typeCheckInterfaceOrder = list.subList(startingOffset, startingOffset + numInterfaces).stream().mapToInt(i -> i).toArray();
            startingOffset += numInterfaces;
            int[] itableStartingOffsets = list.subList(startingOffset, startingOffset + numInterfaces).stream().mapToInt(i -> i).toArray();

            return new DispatchInfo(typeCheckInterfaceOrder, itableStartingOffsets, dispatchTables.toArray(new String[0]));
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            DispatchInfo that = (DispatchInfo) object;
            return Arrays.equals(typeCheckInterfaceOrder, that.typeCheckInterfaceOrder) && Arrays.equals(itableStartingOffsets, that.itableStartingOffsets) &&
                            Arrays.equals(dispatchTables, that.dispatchTables);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(typeCheckInterfaceOrder);
            result = 31 * result + Arrays.hashCode(itableStartingOffsets);
            result = 31 * result + Arrays.hashCode(dispatchTables);
            return result;
        }

        @Override
        public String toString() {
            return "DispatchInfo{" +
                            "typeCheckInterfaceOrder=" + Arrays.toString(typeCheckInterfaceOrder) +
                            ", itableStartingOffsets=" + Arrays.toString(itableStartingOffsets) +
                            ", dispatchTables=" + Arrays.toString(dispatchTables) +
                            '}';
        }
    }

    public static void persistDispatchInfo(Collection<HostedType> types) {
        if (ImageSingletons.contains(LayerTypeInfo.class)) {
            ImageSingletons.lookup(LayerTypeInfo.class).persistDispatchInfo(types);
        }
    }

    public static void persistMethodInfo(Collection<HostedMethod> methods) {
        if (ImageSingletons.contains(LayerTypeInfo.class)) {
            ImageSingletons.lookup(LayerTypeInfo.class).persistMethodInfo(methods);
        }
    }

    public static void persistDispatchTable(HostedType type, List<HostedMethod> table) {
        if (ImageSingletons.contains(LayerTypeInfo.class)) {
            ImageSingletons.lookup(LayerTypeInfo.class).persistDispatchTable(type, table);
        }
    }

    public static Set<AnalysisMethod> loadDispatchTable(AnalysisType type) {
        return ImageSingletons.lookup(LayerTypeInfo.class).loadDispatchTableMethods(type);
    }

    private static class LayerTypeInfo implements LayeredImageSingleton {
        Map<Integer, TypeInfo> identifierToTypeInfo = new HashMap<>();
        Map<Integer, DispatchInfo> identifierToDispatchInfo = new HashMap<>();
        Map<Integer, List<String>> identifierToSymDispatchTable = new HashMap<>();
        Map<Integer, List<Integer>> identifierToIdDispatchTable = new HashMap<>();
        Map<String, Integer> symbolToVTableIdx = new HashMap<>();
        Map<AnalysisType, Set<AnalysisMethod>> currentDispatchTableMaps = new HashMap<>();
        ImageLayerLoader loader;
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

        public void persistDispatchTableMethods(Collection<HostedType> types) {
            for (HostedType type : types) {
                if (isTypeReachable(type)) {
                    AnalysisType aType = type.getWrapped();
                    Set<AnalysisMethod> dispatchTable = aType.getOpenTypeWorldDispatchTableMethods();
                    var previous = currentDispatchTableMaps.put(aType, dispatchTable);
                    assert previous == null;
                }
            }
        }

        public Set<AnalysisMethod> loadDispatchTableMethods(AnalysisType type) {
            assert type.getWrapped() instanceof BaseLayerType : type;
            /*
             * If there was a race for the identifier id, it is possible no map exists for the base
             * layer type.
             */
            List<Integer> methodIDs = identifierToIdDispatchTable.get(type.getId());
            if (methodIDs == null) {
                return Set.of();
            }

            return Set.of(methodIDs.stream().map(mid -> loader.getAnalysisMethod(mid)).toArray(AnalysisMethod[]::new));
        }

        private static boolean logErrorMessages() {
            return SubstrateUtil.assertionsEnabled() || Options.LogOpenTypeWorldDiscrepancies.getValue();
        }

        private static boolean generateErrorMessage() {
            return logErrorMessages() || Options.ErrorOnOpenTypeWorldDiscrepancies.getValue();
        }

        private static boolean isTypeReachable(HostedType type) {
            var result = type.getWrapped().isReachable();
            assert type.getTypeID() != -1 : type;
            return result;
        }

        public void persistTypeInfo(Collection<HostedType> types) {
            for (HostedType type : types) {
                if (isTypeReachable(type)) {
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

        public void persistDispatchTable(HostedType type, List<HostedMethod> table) {
            if (isTypeReachable(type)) {
                int identifierID = type.getWrapped().getId();
                List<String> newTable = table.stream().map(NativeImage::localSymbolNameForMethod).toList();
                var priorTable = identifierToSymDispatchTable.get(identifierID);
                if (priorTable == null) {
                    identifierToSymDispatchTable.put(identifierID, newTable);
                } else {
                    if (!newTable.equals(priorTable)) {
                        if (generateErrorMessage()) {
                            StringBuilder sb = new StringBuilder();
                            Set<String> priorSet = new HashSet<>(priorTable);
                            Set<String> newSet = new HashSet<>(newTable);
                            sb.append(String.format("%n%nMismatch in dispatch table for %s: prior-size: %s new size: %s%n", type.getName(), priorSet.size(), newSet.size()));
                            sb.append("Methods present in priorTable not present in newTable\n");
                            priorSet.stream().filter(Predicate.not(newSet::contains)).forEach(sym -> sb.append(sym).append("\n"));
                            sb.append("Methods present in newTable not present in priorTable\n");
                            newSet.stream().filter(Predicate.not(priorSet::contains)).forEach(sym -> sb.append(sym).append("\n"));
                            sb.append("End Results\n");
                            String errorMessage = sb.toString();
                            if (logErrorMessages()) {
                                System.out.println(errorMessage);
                            }
                            if (Options.ErrorOnOpenTypeWorldDiscrepancies.getValue()) {
                                throw VMError.shouldNotReachHere(errorMessage);
                            }
                        }
                    }
                }
            }
        }

        private static void printDispatchTableDifference(DispatchInfo priorInfo, DispatchInfo newInfo) {
            System.out.println("Describing difference");
            var priorTables = priorInfo.dispatchTables;
            var newTables = newInfo.dispatchTables;

            if (priorTables.length != newTables.length) {
                System.out.printf("Different length %s %s%n%n", priorTables.length, newTables.length);
                return;
            }

            for (int i = 0; i < priorTables.length; i++) {
                var priorTarget = priorTables[i];
                var newTarget = newTables[i];
                if (!priorTarget.equals(newTarget)) {
                    System.out.printf("Difference at index %s: prior: %s new: %s%n", i, priorTarget, newTarget);
                }
            }
            System.out.println("\n");
        }

        public void persistDispatchInfo(Collection<HostedType> types) {
            int numErrors = 0;
            for (HostedType type : types) {
                if (isTypeReachable(type)) {
                    if (VTableBuilder.needsDispatchTable(type)) {
                        int identifierID = type.getWrapped().getId();
                        var newDispatchInfo = type.generateDispatchInfo();
                        var priorInfo = identifierToDispatchInfo.get(identifierID);
                        if (priorInfo == null) {
                            identifierToDispatchInfo.put(identifierID, newDispatchInfo);
                        } else {
                            if (!newDispatchInfo.equals(priorInfo)) {
                                numErrors++;
                                if (generateErrorMessage()) {
                                    String message = String.format("%n%nError %s%nDispatch Info Mismatch: %s%nprior: %s%n%nnew: %s", numErrors, type.getName(), priorInfo, newDispatchInfo);
                                    if (logErrorMessages()) {
                                        System.out.println(message);
                                        printDispatchTableDifference(priorInfo, newDispatchInfo);
                                    }
                                    if (Options.ErrorOnOpenTypeWorldDiscrepancies.getValue()) {
                                        throw VMError.shouldNotReachHere(message);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (numErrors > 0 && generateErrorMessage()) {
                /*
                 * Note to get here ErrorOnOpenTypeWorldDiscrepancies cannot be true.
                 */
                System.out.println("Total num errors: " + numErrors);
            }
        }

        public void persistMethodInfo(Collection<HostedMethod> methods) {
            for (HostedMethod method : methods) {
                if (method.hasVTableIndex() && isTypeReachable(method.getDeclaringClass())) {
                    int vTableIndex = method.getVTableIndex();
                    String key = localSymbolNameForMethod(method);
                    var priorIdx = symbolToVTableIdx.get(key);
                    if (priorIdx == null) {
                        symbolToVTableIdx.put(key, vTableIndex);
                    } else {
                        if (priorIdx != vTableIndex) {
                            if (generateErrorMessage()) {
                                String message = String.format("VTable Index Mismatch %s. prior: %s new: %s", method.format("%H.%n"), priorIdx, vTableIndex);
                                if (logErrorMessages()) {
                                    System.out.println(message);
                                }
                                if (Options.ErrorOnOpenTypeWorldDiscrepancies.getValue()) {
                                    throw VMError.shouldNotReachHere(message);
                                }
                            }
                        }
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

        private static String getDispatchInfoKey(int id) {
            return String.format("DispatchInfo-%s", id);
        }

        private static String getSymbolInfoKey(int id) {
            return String.format("SymbolInfo-%s", id);
        }

        private static String getSymDispatchTableId(int id) {
            return String.format("SymDispatchTable-%s", id);
        }

        private static String getIdDispatchTableId(int id) {
            return String.format("IdDispatchTable-%s", id);
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
            writer.writeInt("maxTypeID", DynamicHubSupport.singleton().getMaxTypeId());

            for (int identifierID : typeIdentifierIds) {
                var typeInfo = identifierToTypeInfo.get(identifierID);
                assert typeInfo != null;
                writer.writeIntList(getTypeInfoKey(identifierID), typeInfo.toIntList());
            }

            /*
             * Currently we keep track of dispatch tables, vtable index assignments, and per-type
             * dispatch tables to validate open type world virtual dispatch implementation. As the
             * layered analysis information becomes more stable, much of this tracking can be
             * removed (GR-57248).
             */

            var dispatchIdentifierIds = identifierToDispatchInfo.keySet().stream().sorted().toList();
            writer.writeIntList("dispatchIdentifierIds", dispatchIdentifierIds);

            for (int identifierID : dispatchIdentifierIds) {
                var dispatchInfo = identifierToDispatchInfo.get(identifierID);
                assert dispatchInfo != null;
                var lists = dispatchInfo.generateLists();
                writer.writeIntList(getDispatchInfoKey(identifierID), lists.getLeft());
                writer.writeStringList(getSymbolInfoKey(identifierID), lists.getRight());
            }

            ArrayList<String> symNames = new ArrayList<>();
            ArrayList<Integer> vtableIdxs = new ArrayList<>();
            for (var entry : symbolToVTableIdx.entrySet()) {
                symNames.add(entry.getKey());
                vtableIdxs.add(entry.getValue());
            }
            writer.writeStringList("symNames", symNames);
            writer.writeIntList("vtableIdx", vtableIdxs);

            var symDispatchTableIds = identifierToSymDispatchTable.keySet().stream().sorted().toList();
            writer.writeIntList("symDispatchTableIds", symDispatchTableIds);
            for (int identifierID : symDispatchTableIds) {
                var dispatchTable = identifierToSymDispatchTable.get(identifierID);
                assert dispatchTable != null;
                writer.writeStringList(getSymDispatchTableId(identifierID), dispatchTable);
            }

            var idDispatchTableKeys = currentDispatchTableMaps.keySet().stream().sorted(Comparator.comparingInt(AnalysisType::getId)).toList();
            writer.writeIntList("idDispatchTableIds", idDispatchTableKeys.stream().map(AnalysisType::getId).toList());
            for (AnalysisType aType : idDispatchTableKeys) {
                var dispatchTable = currentDispatchTableMaps.get(aType).stream().map(AnalysisMethod::getId).sorted().toList();
                writer.writeIntList(getIdDispatchTableId(aType.getId()), dispatchTable);
            }

            return PersistFlags.CREATE;
        }

        @SuppressWarnings("unused")
        public static Object createFromLoader(ImageSingletonLoader loader) {
            var info = new LayerTypeInfo();
            info.maxTypeID = loader.readInt("maxTypeID");
            List<Integer> typeIdentifierIds = loader.readIntList("typeIdentifierIds");
            for (var identifierID : typeIdentifierIds) {
                Object previous = info.identifierToTypeInfo.put(identifierID, TypeInfo.fromIntList(loader.readIntList(getTypeInfoKey(identifierID))));
                assert previous == null : previous;
            }

            List<Integer> dispatchIdentifierIds = loader.readIntList("dispatchIdentifierIds");
            for (var identifierID : dispatchIdentifierIds) {
                Object previous = info.identifierToDispatchInfo.put(identifierID,
                                DispatchInfo.fromLists(loader.readIntList(getDispatchInfoKey(identifierID)), loader.readStringList(getSymbolInfoKey(identifierID))));
                assert previous == null : previous;
            }

            var symNameIterator = loader.readStringList("symNames").iterator();
            var vtableIdxIterator = loader.readIntList("vtableIdx").iterator();
            while (symNameIterator.hasNext()) {
                String symName = symNameIterator.next();
                int vtableIndex = vtableIdxIterator.next();

                var previous = info.symbolToVTableIdx.put(symName, vtableIndex);
                assert previous == null : previous;
            }

            List<Integer> symDispatchTableIds = loader.readIntList("symDispatchTableIds");
            for (var identifierID : symDispatchTableIds) {
                List<String> dispatchTable = loader.readStringList(getSymDispatchTableId(identifierID));
                var previous = info.identifierToSymDispatchTable.put(identifierID, dispatchTable);
                assert previous == null : previous;
            }

            List<Integer> idDispatchTableIds = loader.readIntList("idDispatchTableIds");
            for (var identifierID : idDispatchTableIds) {
                List<Integer> dispatchTable = loader.readIntList(getIdDispatchTableId(identifierID));
                var previous = info.identifierToIdDispatchTable.put(identifierID, dispatchTable);
                assert previous == null : previous;
            }

            return info;
        }
    }
}
