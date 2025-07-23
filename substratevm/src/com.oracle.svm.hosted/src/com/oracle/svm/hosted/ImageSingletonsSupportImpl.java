/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.AnnotationExtractor;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;

import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.core.layeredimagesingleton.ApplicationLayerOnlyImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.InitialLayerOnlyImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingleton.PersistFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonBuilderFlags;
import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimagesingleton.LoadedLayeredImageSingletonInfo;
import com.oracle.svm.core.layeredimagesingleton.MultiLayeredImageSingleton;
import com.oracle.svm.core.traits.BuiltinTraits;
import com.oracle.svm.core.traits.InjectedSingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonAccess;
import com.oracle.svm.core.traits.SingletonAccessSupplier;
import com.oracle.svm.core.traits.SingletonLayeredCallbacks;
import com.oracle.svm.core.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKind;
import com.oracle.svm.core.traits.SingletonLayeredInstallationKindSupplier;
import com.oracle.svm.core.traits.SingletonTrait;
import com.oracle.svm.core.traits.SingletonTraitKind;
import com.oracle.svm.core.traits.SingletonTraits;
import com.oracle.svm.core.traits.SingletonTraitsSupplier;
import com.oracle.svm.core.util.ConcurrentIdentityHashMap;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.imagelayer.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.imagelayer.SVMImageLayerSingletonLoader;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.Assertions;
import jdk.vm.ci.meta.JavaConstant;

public final class ImageSingletonsSupportImpl extends ImageSingletonsSupport implements LayeredImageSingletonSupport {

    @Override
    public <T> void add(Class<T> key, T value) {
        HostedManagement.getAndAssertExists().doAdd(key, value);
    }

    @Override
    public <T> T lookup(Class<T> key) {
        return HostedManagement.getAndAssertExists().doLookup(key, true, false);
    }

    @Override
    public <T> T lookup(Class<T> key, boolean accessRuntimeOnly, boolean accessMultiLayer) {
        return HostedManagement.getAndAssertExists().doLookup(key, !accessRuntimeOnly, accessMultiLayer);
    }

    @Override
    public Collection<Class<?>> getMultiLayeredImageSingletonKeys() {
        return HostedManagement.getAndAssertExists().getMultiLayeredImageSingletonKeys();
    }

    @Override
    public Collection<Class<?>> getFutureLayerAccessibleImageSingletonKeys() {
        return HostedManagement.getAndAssertExists().getFutureLayerAccessibleImageSingletonKeys();
    }

    @Override
    public void freezeLayeredImageSingletonMetadata() {
        HostedManagement.getAndAssertExists().freezeLayeredImageSingletonMetadata();
    }

    @Override
    public boolean isInitialLayerOnlyImageSingleton(Class<?> key) {
        var loader = HostedImageLayerBuildingSupport.singleton().getSingletonLoader();
        return loader.isInitialLayerOnlyImageSingleton(key);
    }

    @Override
    public JavaConstant getInitialLayerOnlyImageSingleton(Class<?> key) {
        var loader = HostedImageLayerBuildingSupport.singleton().getSingletonLoader();
        return loader.loadInitialLayerOnlyImageSingleton(key);
    }

    @Override
    public boolean contains(Class<?> key) {
        HostedManagement hm = HostedManagement.get();
        if (hm == null) {
            return false;
        } else {
            return hm.doContains(key);
        }
    }

    /**
     * Value linked to each singleton key class from {@link HostedManagement#configObjects}. Beyond
     * the singleton value itself, this container also references the singleton's trait map and
     * provides fast lookups on if the singleton has builtime & runtime access permissions.
     */
    public static final class SingletonInfo {
        Object singleton;
        final SingletonTraitMap traitMap;
        final boolean buildtimeAccessAllowed;
        final boolean runtimeAccessAllowed;

        SingletonInfo(Object singleton, SingletonTraitMap traitMap) {
            this.singleton = singleton;
            this.traitMap = traitMap;

            var accessTrait = traitMap.getTrait(SingletonTraitKind.ACCESS);
            buildtimeAccessAllowed = accessTrait.map(singletonTrait -> SingletonAccess.getAccess(singletonTrait).contains(LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS)).orElse(true);
            runtimeAccessAllowed = accessTrait.map(singletonTrait -> SingletonAccess.getAccess(singletonTrait).contains(LayeredImageSingletonBuilderFlags.RUNTIME_ACCESS)).orElse(true);
        }

        public Object singleton() {
            return singleton;
        }

        public SingletonTraitMap traitMap() {
            return traitMap;
        }
    }

    /**
     * Stores the traits associated with a given singleton.
     */
    public static final class SingletonTraitMap {
        private final EnumMap<SingletonTraitKind, SingletonTrait> traitMap;

        private SingletonTraitMap(EnumMap<SingletonTraitKind, SingletonTrait> traitMap) {
            this.traitMap = traitMap;
        }

        void addTrait(SingletonTrait value) {
            SingletonTraitKind key = value.kind();
            var prev = traitMap.put(key, value);
            assert prev == null;
        }

        public Optional<SingletonTrait> getTrait(SingletonTraitKind key) {
            return Optional.ofNullable(traitMap.get(key));
        }

        /**
         * Creates a new {@link SingletonTraitMap} based on the {@link SingletonTraits} assigned to
         * a given singleton.
         */
        static SingletonTraitMap getAnnotatedTraits(Class<?> singletonClass, AnnotationExtractor extractor, boolean layeredBuild) {
            if (extractor != null) {
                SingletonTraits annotation = extractor.extractAnnotation(singletonClass, SingletonTraits.class, false);

                if (annotation != null) {
                    EnumMap<SingletonTraitKind, SingletonTrait> traitMap = new EnumMap<>(SingletonTraitKind.class);

                    if (annotation.access() != null) {
                        var accessSupplierClass = annotation.access();
                        SingletonAccessSupplier accessSupplier = ReflectionUtil.newInstance(accessSupplierClass);
                        SingletonTrait accessTrait = accessSupplier.getAccessTrait();
                        assert accessTrait.kind() == SingletonTraitKind.ACCESS && accessTrait.kind().isInConfiguration(layeredBuild) : accessTrait;
                        var prev = traitMap.put(accessTrait.kind(), accessTrait);
                        assert prev == null : Assertions.errorMessage("Added multiple access traits", accessTrait, prev);
                    }

                    if (SingletonTraitKind.LAYERED_CALLBACKS.isInConfiguration(layeredBuild) && annotation.layeredCallbacks() != null) {
                        var callbacksSupplierClass = annotation.layeredCallbacks();
                        SingletonLayeredCallbacksSupplier callbacksSupplier = ReflectionUtil.newInstance(callbacksSupplierClass);
                        SingletonTrait callbacksTrait = callbacksSupplier.getLayeredCallbacksTrait();
                        assert callbacksTrait.kind() == SingletonTraitKind.LAYERED_CALLBACKS : callbacksTrait;
                        var prev = traitMap.put(callbacksTrait.kind(), callbacksTrait);
                        assert prev == null : Assertions.errorMessage("Added multiple layered callbacks traits", callbacksTrait, prev);
                    }

                    if (SingletonTraitKind.LAYERED_INSTALLATION_KIND.isInConfiguration(layeredBuild) && annotation.layeredInstallationKind() != null) {
                        var installationKindSupplierClass = annotation.layeredInstallationKind();
                        SingletonLayeredInstallationKindSupplier installationKindSupplier = ReflectionUtil.newInstance(installationKindSupplierClass);
                        SingletonTrait installationTrait = installationKindSupplier.getLayeredInstallationKindTrait();
                        assert installationTrait.kind() == SingletonTraitKind.LAYERED_INSTALLATION_KIND : installationTrait;
                        var prev = traitMap.put(installationTrait.kind(), installationTrait);
                        assert prev == null : Assertions.errorMessage("Added multiple layered installation kind traits", installationTrait, prev);
                    }

                    if (annotation.other() != null) {
                        for (var traitSupplierClass : annotation.other()) {
                            SingletonTraitsSupplier traitSupplier = ReflectionUtil.newInstance(traitSupplierClass);
                            SingletonTrait traitInfo = traitSupplier.getTrait();
                            if (traitInfo.kind().isInConfiguration(layeredBuild)) {
                                var prev = traitMap.put(traitInfo.kind(), traitInfo);
                                assert prev == null : Assertions.errorMessage("Added multiple traits for singleton kind", traitInfo, prev);
                            }
                        }
                    }

                    if (!traitMap.isEmpty()) {
                        return new SingletonTraitMap(traitMap);
                    }
                }
            }

            return new SingletonTraitMap(new EnumMap<>(SingletonTraitKind.class));
        }
    }

    /**
     * Management of the {@link ImageSingletons} registry during image generation.
     */
    public static final class HostedManagement {

        static {
            ImageSingletonsSupport.installSupport(new ImageSingletonsSupportImpl());
        }

        /**
         * Marker for ImageSingleton keys which cannot have a value installed. This can happen when
         * a {@link LayeredImageSingleton} specified {@link PersistFlags#FORBIDDEN}.
         */
        private static final Object SINGLETON_INSTALLATION_FORBIDDEN = new Object();

        /**
         * The {@link ImageSingletons} removes static state from the image generator, and in theory
         * would allow multiple image builds to run at the same time in the same HotSpot VM. But in
         * practice, this is not possible because JDK state would leak between image builds. So it
         * is OK (and better for performance) to store all the {@link ImageSingletons} in a static
         * field here.
         */
        private static HostedManagement singletonDuringImageBuild;

        private static HostedManagement getAndAssertExists() {
            HostedManagement result = get();
            assert result != null;
            return result;
        }

        public static HostedManagement get() {
            return singletonDuringImageBuild;
        }

        public static void install(HostedManagement vmConfig) {
            install(vmConfig, null);
        }

        public static void install(HostedManagement vmConfig, HostedImageLayerBuildingSupport support) {
            UserError.guarantee(singletonDuringImageBuild == null, "Only one native image build can run at a time");
            singletonDuringImageBuild = vmConfig;

            if (support != null) {
                /*
                 * Note we are intentionally adding this singleton early as build flags may depend
                 * on it. We also intentionally do not mark this singleton as a LayerImageSingleton
                 * to prevent circular dependency complications.
                 */
                singletonDuringImageBuild.addSingleton(ImageLayerBuildingSupport.class, support);
            } else {
                /*
                 * Create a placeholder ImageLayerBuilding support to indicate this is not a layered
                 * build.
                 */
                singletonDuringImageBuild.addSingleton(ImageLayerBuildingSupport.class, new ImageLayerBuildingSupport(false, false, false) {
                });
            }
            if (support != null && support.getSingletonLoader() != null) {
                /*
                 * Note eventually this may need to be moved to a later point after the Options
                 * Image Singleton is installed.
                 */
                singletonDuringImageBuild.installPriorSingletonInfo(support.getSingletonLoader());
            } else {
                singletonDuringImageBuild.addSingleton(LoadedLayeredImageSingletonInfo.class, new LoadedLayeredImageSingletonInfo(Set.of()));
            }
        }

        private void installPriorSingletonInfo(SVMImageLayerSingletonLoader info) {
            var result = info.loadImageSingletons(SINGLETON_INSTALLATION_FORBIDDEN);
            Set<Class<?>> installedKeys = new HashSet<>();
            for (var entry : result.entrySet()) {
                Object singletonToInstall = entry.getKey();
                for (Class<?> key : entry.getValue()) {
                    addSingleton(key, singletonToInstall);
                    installedKeys.add(key);
                }
            }

            // document what was installed during loading
            addSingleton(LoadedLayeredImageSingletonInfo.class, new LoadedLayeredImageSingletonInfo(Set.copyOf(installedKeys)));
        }

        public static void clear() {
            singletonDuringImageBuild = null;
        }

        public static void persist() {
            var list = singletonDuringImageBuild.configObjects.entrySet().stream().filter(e -> e.getValue().traitMap.getTrait(SingletonTraitKind.LAYERED_CALLBACKS).isPresent())
                            .sorted(Comparator.comparing(e -> e.getKey().getName()))
                            .toList();
            HostedImageLayerBuildingSupport.singleton().getWriter().writeImageSingletonInfo(list);
        }

        private final Map<Class<?>, SingletonInfo> configObjects;
        private final Map<Object, SingletonTraitMap> singletonToTraitMap;
        private Set<Class<?>> multiLayeredImageSingletonKeys;
        private Set<Class<?>> futureLayerAccessibleImageSingletonKeys;
        private final boolean layeredBuild;
        private final AnnotationExtractor extractor;

        public HostedManagement() {
            this(false, null);
        }

        public HostedManagement(boolean layeredBuild, AnnotationExtractor extractor) {
            this.configObjects = new ConcurrentHashMap<>();
            this.singletonToTraitMap = new ConcurrentIdentityHashMap<>();
            this.multiLayeredImageSingletonKeys = ConcurrentHashMap.newKeySet();
            this.futureLayerAccessibleImageSingletonKeys = ConcurrentHashMap.newKeySet();
            this.layeredBuild = layeredBuild;
            this.extractor = extractor;
        }

        <T> void doAdd(Class<T> key, T value) {
            addSingleton(key, value);
        }

        /**
         * GR-66797 remove all reference to layered image singletons.
         * <p>
         * Temporary feature to convert the legacy {@link LayeredImageSingleton} interface into
         * singleton traits. This will be removed once all singletons are converted to use the new
         * {@link SingletonTraits} API.
         */
        private void injectLayeredInformation(Object singleton, SingletonTraitMap traitMap) {
            if (singleton instanceof LayeredImageSingleton layeredImageSingleton) {
                if (traitMap.getTrait(SingletonTraitKind.ACCESS).isEmpty()) {
                    var flags = layeredImageSingleton.getImageBuilderFlags();
                    SingletonTrait accessTrait;
                    if (flags.equals(LayeredImageSingletonBuilderFlags.ALL_ACCESS)) {
                        accessTrait = BuiltinTraits.ALL_ACCESS;
                    } else if (flags.equals(LayeredImageSingletonBuilderFlags.BUILDTIME_ACCESS_ONLY)) {
                        accessTrait = BuiltinTraits.BUILDTIME_ONLY;
                    } else {
                        VMError.guarantee(flags.equals(LayeredImageSingletonBuilderFlags.RUNTIME_ACCESS_ONLY));
                        accessTrait = BuiltinTraits.RUNTIME_ONLY;
                    }
                    traitMap.addTrait(accessTrait);
                }
                if (layeredBuild) {
                    if (traitMap.getTrait(SingletonTraitKind.LAYERED_CALLBACKS).isEmpty()) {
                        SingletonLayeredCallbacks action = new InjectedSingletonLayeredCallbacks(layeredImageSingleton);
                        traitMap.addTrait(new SingletonTrait(SingletonTraitKind.LAYERED_CALLBACKS, action));
                    }
                }
            }
        }

        /**
         * Creates or collects the {@link SingletonTraitMap} associated with this singleton before
         * adding the singleton to the internal map.
         */
        private void addSingleton(Class<?> key, Object value) {
            SingletonTraitMap traitMap = singletonToTraitMap.get(value);
            if (traitMap == null) {
                traitMap = SingletonTraitMap.getAnnotatedTraits(value.getClass(), extractor, layeredBuild);
                injectLayeredInformation(value, traitMap);
                traitMap.getTrait(SingletonTraitKind.ACCESS).ifPresent(trait -> {
                    LayeredImageSingletonBuilderFlags.verifyImageBuilderFlags(value, SingletonAccess.getAccess(trait));
                });
                if (layeredBuild) {
                    traitMap.getTrait(SingletonTraitKind.LAYERED_INSTALLATION_KIND).ifPresent(SingletonLayeredInstallationKind::validate);
                }
            }
            SingletonTraitMap candidateTraitMap = traitMap;
            traitMap = singletonToTraitMap.computeIfAbsent(value, k -> candidateTraitMap);
            addSingletonToMap(key, value, traitMap);
        }

        private void addSingletonToMap(Class<?> key, Object value, SingletonTraitMap traitMap) {
            checkKey(key);
            if (value == null) {
                throw UserError.abort("ImageSingletons do not allow null value for key %s", key.getTypeName());
            }

            if (value instanceof LayeredImageSingleton singleton) {
                if (singleton instanceof MultiLayeredImageSingleton || ApplicationLayerOnlyImageSingleton.isSingletonInstanceOf(singleton)) {

                    if (singleton instanceof MultiLayeredImageSingleton && ApplicationLayerOnlyImageSingleton.isSingletonInstanceOf(singleton)) {
                        throw UserError.abort("Singleton cannot implement both %s and %s. singleton: %s", MultiLayeredImageSingleton.class, ApplicationLayerOnlyImageSingleton.class, singleton);
                    }

                    if (ApplicationLayerOnlyImageSingleton.isSingletonInstanceOf(singleton) && !ImageLayerBuildingSupport.lastImageBuild()) {
                        throw UserError.abort("Application layer only image singleton can only be installed in the final layer: %s", singleton);
                    }

                    if (singleton instanceof MultiLayeredImageSingleton) {
                        multiLayeredImageSingletonKeys.add(key);
                    }
                }

                if (singleton instanceof InitialLayerOnlyImageSingleton initial && initial.accessibleInFutureLayers()) {
                    futureLayerAccessibleImageSingletonKeys.add(key);
                }
            }

            Object prevValue = configObjects.putIfAbsent(key, new SingletonInfo(value, traitMap));

            if (prevValue != null) {
                throw UserError.abort("ImageSingletons.add must not overwrite existing key %s%nExisting value: %s%nNew value: %s", key.getTypeName(), prevValue, value);
            }
        }

        Collection<Class<?>> getMultiLayeredImageSingletonKeys() {
            return multiLayeredImageSingletonKeys;
        }

        Collection<Class<?>> getFutureLayerAccessibleImageSingletonKeys() {
            return futureLayerAccessibleImageSingletonKeys;
        }

        void freezeLayeredImageSingletonMetadata() {
            multiLayeredImageSingletonKeys = Set.copyOf(multiLayeredImageSingletonKeys);
            futureLayerAccessibleImageSingletonKeys = Set.copyOf(futureLayerAccessibleImageSingletonKeys);
        }

        /**
         * @param buildtimeAccess If true, only allow lookup of singletons with buildtime access
         *            permissions. If false, only allow lookup of singletons within runtime access
         *            permissions.
         */
        <T> T doLookup(Class<T> key, boolean buildtimeAccess, boolean allowMultiLayered) {
            checkKey(key);
            SingletonInfo info = configObjects.get(key);
            if (info == null) {
                var others = configObjects.keySet().stream()//
                                .filter(c -> c.getName().equals(key.getName()))//
                                .map(c -> c.getClassLoader().getName() + "/" + c.getTypeName())//
                                .toList();
                if (others.isEmpty()) {
                    throw UserError.abort("ImageSingletons do not contain key %s", key.getTypeName());
                }
                throw UserError.abort("ImageSingletons do not contain key %s/%s but does contain the following key(s): %s",
                                key.getClassLoader().getName(), key.getTypeName(),
                                String.join(", ", others));
            }
            boolean allowedAccess = buildtimeAccess ? info.buildtimeAccessAllowed : info.runtimeAccessAllowed;
            if (!allowedAccess) {
                throw UserError.abort("Singleton cannot be accessed. Key: %s, Access type: %s", key.getTypeName(), buildtimeAccess ? "BUILD_TIME" : "RUN_TIME");
            }

            VMError.guarantee(info.singleton() != null);
            Object singleton = info.singleton();
            if (!allowMultiLayered && singleton instanceof MultiLayeredImageSingleton) {
                throw UserError.abort("Forbidden lookup of MultiLayeredImageSingleton. Use LayeredImageSingletonSupport.lookup if really necessary. Key: %s, object %s", key, singleton);
            }
            return key.cast(singleton);
        }

        boolean doContains(Class<?> key) {
            checkKey(key);
            var value = configObjects.get(key);
            return value != null && value.singleton() != SINGLETON_INSTALLATION_FORBIDDEN;
        }

        private static void checkKey(Class<?> key) {
            if (key == null) {
                throw UserError.abort("ImageSingletons do not allow null keys");
            }
        }
    }
}
