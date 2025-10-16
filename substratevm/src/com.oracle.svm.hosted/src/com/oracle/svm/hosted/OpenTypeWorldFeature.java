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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.constraints.UnsupportedFeatureException;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.BaseLayerType;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.hub.DynamicHubSupport;
import com.oracle.svm.sdk.staging.layeredimage.ImageLayerBuildingSupport;
import com.oracle.svm.sdk.staging.hosted.layeredimage.KeyValueLoader;
import com.oracle.svm.sdk.staging.hosted.layeredimage.KeyValueWriter;
import com.oracle.svm.sdk.staging.hosted.layeredimage.LayeredPersistFlags;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.sdk.staging.hosted.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredCallbacks;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredInstallationKind.Independent;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonTrait;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonTraitKind;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonTraits;
import com.oracle.svm.hosted.layeredimage.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.layeredimage.SVMImageLayerLoader;
import com.oracle.svm.hosted.meta.HostedMethod;
import com.oracle.svm.hosted.meta.HostedType;
import com.oracle.svm.hosted.meta.HostedUniverse;
import com.oracle.svm.hosted.meta.TypeCheckBuilder;

import jdk.graal.compiler.debug.Assertions;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@AutomaticallyRegisteredFeature
public class OpenTypeWorldFeature implements InternalFeature {

    private Map<AnalysisType, Set<AnalysisMethod>> typeToDispatchTableMethods;

    @Override
    public boolean isInConfiguration(Feature.IsInConfigurationAccess access) {
        return !SubstrateOptions.useClosedTypeWorldHubLayout();
    }

    @Override
    public void beforeUniverseBuilding(BeforeUniverseBuildingAccess access) {
        if (ImageLayerBuildingSupport.buildingInitialLayer()) {
            ImageSingletons.add(LayerTypeCheckInfo.class, new LayerTypeCheckInfo(0, 0));
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        FeatureImpl.DuringSetupAccessImpl config = (FeatureImpl.DuringSetupAccessImpl) access;
        config.registerOnTypeCreatedCallback(this::calculateOpenTypeWorldDispatchTableMethods);
        typeToDispatchTableMethods = new ConcurrentHashMap<>();
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess access) {
        typeToDispatchTableMethods = null;
        var impl = (FeatureImpl.BeforeCompilationAccessImpl) access;
        for (HostedType type : impl.getUniverse().getTypes()) {
            DynamicHub hub = type.getHub();
            impl.registerAsImmutable(hub.getOpenTypeWorldTypeCheckSlots());
            impl.registerAsImmutable(hub.getOpenTypeWorldInterfaceHashTable());
        }
    }

    public static OpenTypeWorldFeature singleton() {
        return ImageSingletons.lookup(OpenTypeWorldFeature.class);
    }

    public boolean isOpenTypeWorldDispatchTableMethodsCalculated(AnalysisType aType) {
        return typeToDispatchTableMethods.containsKey(aType);
    }

    public Set<AnalysisMethod> getOpenTypeWorldDispatchTableMethods(AnalysisType aType) {
        var result = typeToDispatchTableMethods.get(aType);
        return Objects.requireNonNull(result);
    }

    private void calculateOpenTypeWorldDispatchTableMethods(AnalysisType aType) {
        var result = calculateOpenTypeWorldDispatchTableMethods0(aType);
        Objects.requireNonNull(result);
        var prev = typeToDispatchTableMethods.put(aType, result);
        assert prev == null : prev;
    }

    /*
     * Calculates all methods in this class which should be included in its dispatch table.
     */
    private static Set<AnalysisMethod> calculateOpenTypeWorldDispatchTableMethods0(AnalysisType aType) {
        if (aType.isPrimitive()) {
            return Set.of();
        }
        if (aType.getWrapped() instanceof BaseLayerType) {
            // GR-58587 implement proper support.
            return Set.of();
        }

        var resultSet = new HashSet<AnalysisMethod>();
        for (ResolvedJavaMethod m : aType.getWrapped().getDeclaredMethods(false)) {
            assert !m.isConstructor() : Assertions.errorMessage("Unexpected constructor", m);
            if (m.isStatic()) {
                /* Only looking at member methods */
                continue;
            }
            try {
                AnalysisMethod aMethod = aType.getUniverse().lookup(m);
                assert aMethod != null : m;
                resultSet.add(aMethod);
            } catch (UnsupportedFeatureException t) {
                /*
                 * Methods which are deleted or not available on this platform will throw an error
                 * during lookup - ignore and continue execution
                 *
                 * Note it is not simple to create a check to determine whether calling
                 * universe#lookup will trigger an error by creating an analysis object for a type
                 * not supported on this platform, as creating a method requires, in addition to the
                 * types of its return type and parameters, all of the super types of its return and
                 * parameters to be created as well.
                 */
            }
        }

        return resultSet;
    }

    /**
     * see {@link SharedMethod#getIndirectCallTarget}.
     */
    public void computeIndirectCallTargets(HostedUniverse hUniverse, Map<AnalysisMethod, HostedMethod> methods) {
        Map<HostedType, HostedType[]> allInterfacesMap = new HashMap<>();
        methods.forEach((aMethod, hMethod) -> {
            assert aMethod.isOriginalMethod();

            var aAlias = calculateIndirectCallTarget(allInterfacesMap, hMethod);
            HostedMethod hAlias;
            if (aAlias.equals(aMethod)) {
                hAlias = hMethod;
            } else {
                hAlias = hUniverse.lookup(aAlias);
                assert hAlias != null;
            }

            hMethod.setIndirectCallTarget(hAlias);
        });
    }

    /**
     * For methods where its {@link AnalysisMethod#getDeclaringClass()} does not explicitly declare
     * the method, find an alternative explicit declaration for the method which can be used as an
     * indirect call target. This logic is currently used for deciding the target of
     * virtual/interface calls when using the open type world.
     */
    private AnalysisMethod calculateIndirectCallTarget(Map<HostedType, HostedType[]> allInterfacesMap, HostedMethod hOriginal) {
        AnalysisMethod aOriginal = hOriginal.getWrapped();
        if (hOriginal.isStatic() || hOriginal.isConstructor()) {
            /*
             * Static methods and constructors must always be explicitly declared.
             */
            return aOriginal;
        }

        var declaringClass = hOriginal.getDeclaringClass();
        var dispatchTableMethods = getOpenTypeWorldDispatchTableMethods(declaringClass.getWrapped());

        if (dispatchTableMethods.contains(aOriginal)) {
            return aOriginal;
        }

        for (var interfaceType : getAllInterfaces(allInterfacesMap, declaringClass)) {
            if (interfaceType.equals(declaringClass)) {
                // already checked
                continue;
            }
            dispatchTableMethods = getOpenTypeWorldDispatchTableMethods(interfaceType.getWrapped());
            for (AnalysisMethod candidate : dispatchTableMethods) {
                if (matchingSignature(candidate, aOriginal)) {
                    return candidate;
                }
            }
        }

        /*
         * For some methods (e.g., methods labeled as @PolymorphicSignature or @Delete), we
         * currently do not find matches. However, these methods will not be indirect calls within
         * our generated code, so it is not necessary to determine an accurate virtual/interface
         * call target.
         */
        return aOriginal;
    }

    /**
     * @return All interfaces this type inherits (including itself if it is an interface).
     */
    private static HostedType[] getAllInterfaces(Map<HostedType, HostedType[]> allInterfacesMap, HostedType type) {
        var result = allInterfacesMap.get(type);
        if (result != null) {
            return result;
        }

        Set<HostedType> allInterfaceSet = new HashSet<>();

        if (type.isInterface()) {
            allInterfaceSet.add(type);
        }

        if (type.getSuperclass() != null) {
            allInterfaceSet.addAll(Arrays.asList(getAllInterfaces(allInterfacesMap, type.getSuperclass())));
        }

        for (var i : type.getInterfaces()) {
            allInterfaceSet.addAll(Arrays.asList(getAllInterfaces(allInterfacesMap, i)));
        }

        result = allInterfaceSet.toArray(HostedType[]::new);
        // sort so that we have a consistent order
        Arrays.sort(result, HostedUniverse.TYPE_COMPARATOR);

        allInterfacesMap.put(type, result);
        return result;
    }

    public static boolean matchingSignature(HostedMethod o1, HostedMethod o2) {
        return matchingSignature(o1.wrapped, o2.wrapped);
    }

    private static boolean matchingSignature(AnalysisMethod o1, AnalysisMethod o2) {
        if (o1.equals(o2)) {
            return true;
        }

        if (!o1.getName().equals(o2.getName())) {
            return false;
        }

        return o1.getSignature().equals(o2.getSignature());
    }

    public static TypeCheckBuilder.StartingTypeIDs loadTypeInfo(Collection<HostedType> types) {
        if (ImageLayerBuildingSupport.buildingExtensionLayer()) {
            /*
             * Load analysis must be enabled or otherwise the same Analysis Type id will not be
             * reassigned across layers.
             */
            return ImageSingletons.lookup(LayerTypeCheckInfo.class).loadTypeID(types);
        } else {
            return new TypeCheckBuilder.StartingTypeIDs(0, 1);
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
                    int interfaceID = type.getInterfaceID();
                    int numClassTypes = type.getNumClassTypes();
                    int numInterfaceTypes = type.getNumInterfaceTypes();
                    int[] typecheckSlots = type.getOpenTypeWorldTypeCheckSlots();
                    boolean matches = typeID == priorInfo.typeID && numClassTypes == priorInfo.numClassTypes && numInterfaceTypes == priorInfo.numInterfaceTypes &&
                                    Arrays.equals(typecheckSlots, priorInfo.typecheckSlots);
                    if (!matches) {
                        var typeInfo = new TypeCheckInfo(true, typeID, interfaceID, numClassTypes, numInterfaceTypes, typecheckSlots);
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
            return new TypeCheckInfo(hubInfo.getInstalled(), hubInfo.getTypecheckId(), hubInfo.getInterfaceId(), hubInfo.getNumClassTypes(), hubInfo.getNumIterableInterfaceTypes(), typecheckSlots);
        } else {
            return null;
        }
    }

    record TypeCheckInfo(boolean installed, int typeID, int interfaceID, int numClassTypes, int numInterfaceTypes, int[] typecheckSlots) {
    }

    @SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = LayeredCallbacks.class, layeredInstallationKind = Independent.class)
    private static final class LayerTypeCheckInfo {
        final int maxTypeID;
        final int maxInterfaceID;

        LayerTypeCheckInfo(int maxTypeID, int maxInterfaceID) {
            this.maxTypeID = maxTypeID;
            this.maxInterfaceID = maxInterfaceID;
        }

        public TypeCheckBuilder.StartingTypeIDs loadTypeID(Collection<HostedType> types) {
            var loader = HostedImageLayerBuildingSupport.singleton().getLoader();
            for (HostedType type : types) {
                TypeCheckInfo info = getTypecheckInfo(loader, type);
                if (info != null) {
                    type.loadTypeAndInterfaceID(info.typeID, info.interfaceID);
                }
            }

            return new TypeCheckBuilder.StartingTypeIDs(maxTypeID, maxInterfaceID);
        }
    }

    static class LayeredCallbacks extends SingletonLayeredCallbacksSupplier {
        @Override
        public SingletonTrait getLayeredCallbacksTrait() {
            return new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, new SingletonLayeredCallbacks<>() {
                @Override
                public LayeredPersistFlags doPersist(KeyValueWriter writer, Object singleton) {
                    writer.writeInt("maxTypeID", DynamicHubSupport.currentLayer().getMaxTypeId());
                    writer.writeInt("maxInterfaceID", DynamicHubSupport.currentLayer().getMaxInterfaceId());

                    return LayeredPersistFlags.CREATE;
                }

                @Override
                public Class<? extends LayeredSingletonInstantiator<?>> getSingletonInstantiator() {
                    return SingletonInstantiator.class;
                }
            });
        }
    }

    static class SingletonInstantiator implements SingletonLayeredCallbacks.LayeredSingletonInstantiator<LayerTypeCheckInfo> {
        @Override
        public LayerTypeCheckInfo createFromLoader(KeyValueLoader loader) {
            return new LayerTypeCheckInfo(loader.readInt("maxTypeID"), loader.readInt("maxInterfaceID"));
        }
    }
}
