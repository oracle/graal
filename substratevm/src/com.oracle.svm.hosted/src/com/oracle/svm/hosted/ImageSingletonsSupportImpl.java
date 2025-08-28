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
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.AnnotationExtractor;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;

import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
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
    public void freezeLayeredImageSingletonMetadata() {
        HostedManagement.getAndAssertExists().freezeLayeredImageSingletonMetadata();
    }

    @Override
    public Set<Object> getSingletonsWithTrait(SingletonLayeredInstallationKind.InstallationKind kind) {
        return HostedManagement.getAndAssertExists().getSingletonsWithTrait(kind);
    }

    @Override
    public void forbidNewTraitInstallations(SingletonLayeredInstallationKind.InstallationKind kind) {
        HostedManagement.getAndAssertExists().forbidNewTraitInstallations(kind);
    }

    @Override
    public JavaConstant getInitialLayerOnlyImageSingleton(Class<?> key) {
        var loader = HostedImageLayerBuildingSupport.singleton().getSingletonLoader();
        return loader.loadInitialLayerOnlyImageSingleton(key);
    }

    @Override
    public SingletonTrait getTraitForUninstalledSingleton(Class<?> key, SingletonTraitKind kind) {
        SingletonTraitMap map = HostedManagement.getAndAssertExists().getUninstalledSingletonTraitMap(key);
        if (map == null) {
            return null;
        } else {
            return map.getTrait(kind).orElse(null);
        }
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
            assert traitMap.sealed : traitMap;

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
        private boolean sealed = false;

        private SingletonTraitMap(EnumMap<SingletonTraitKind, SingletonTrait> traitMap) {
            this.traitMap = traitMap;
        }

        private static SingletonTraitMap create() {
            return new SingletonTraitMap(new EnumMap<>(SingletonTraitKind.class));
        }

        void addTrait(SingletonTrait value) {
            assert !sealed : "cannot add further traits";
            SingletonTraitKind key = value.kind();
            var prev = traitMap.put(key, value);
            assert prev == null : Assertions.errorMessage(key, value, prev);
        }

        public boolean isEmpty() {
            return traitMap.isEmpty();
        }

        public Optional<SingletonTrait> getTrait(SingletonTraitKind key) {
            return Optional.ofNullable(traitMap.get(key));
        }

        void seal() {
            sealed = true;
        }

        /**
         * Creates a new {@link SingletonTraitMap} based on the {@link SingletonTraits} assigned to
         * a given singleton.
         */
        static SingletonTraitMap getAnnotatedTraits(Class<?> singletonClass, AnnotationExtractor extractor, boolean layeredBuild) {
            SingletonTraitMap traitMap = SingletonTraitMap.create();
            if (extractor != null) {
                SingletonTraits annotation = extractor.extractAnnotation(singletonClass, SingletonTraits.class, false);

                if (annotation != null) {
                    if (annotation.access() != null) {
                        var accessSupplierClass = annotation.access();
                        SingletonAccessSupplier accessSupplier = ReflectionUtil.newInstance(accessSupplierClass);
                        SingletonTrait accessTrait = accessSupplier.getAccessTrait();
                        assert accessTrait.kind() == SingletonTraitKind.ACCESS && accessTrait.kind().isInConfiguration(layeredBuild) : accessTrait;
                        traitMap.addTrait(accessTrait);
                    }

                    if (SingletonTraitKind.LAYERED_CALLBACKS.isInConfiguration(layeredBuild) && annotation.layeredCallbacks() != null) {
                        var callbacksSupplierClass = annotation.layeredCallbacks();
                        SingletonLayeredCallbacksSupplier callbacksSupplier = ReflectionUtil.newInstance(callbacksSupplierClass);
                        SingletonTrait callbacksTrait = callbacksSupplier.getLayeredCallbacksTrait();
                        assert callbacksTrait.kind() == SingletonTraitKind.LAYERED_CALLBACKS : callbacksTrait;
                        traitMap.addTrait(callbacksTrait);
                    }

                    if (SingletonTraitKind.LAYERED_INSTALLATION_KIND.isInConfiguration(layeredBuild) && annotation.layeredInstallationKind() != null) {
                        var installationKindSupplierClass = annotation.layeredInstallationKind();
                        SingletonLayeredInstallationKindSupplier installationKindSupplier = ReflectionUtil.newInstance(installationKindSupplierClass);
                        SingletonTrait installationTrait = installationKindSupplier.getLayeredInstallationKindTrait();
                        assert installationTrait.kind() == SingletonTraitKind.LAYERED_INSTALLATION_KIND : installationTrait;
                        traitMap.addTrait(installationTrait);
                    }

                    if (annotation.other() != null) {
                        for (var traitSupplierClass : annotation.other()) {
                            SingletonTraitsSupplier traitSupplier = ReflectionUtil.newInstance(traitSupplierClass);
                            SingletonTrait trait = traitSupplier.getTrait();
                            if (trait.kind().isInConfiguration(layeredBuild)) {
                                traitMap.addTrait(trait);
                            }
                        }
                    }
                }
            }
            return traitMap;
        }
    }

    /**
     * Management of the {@link ImageSingletons} registry during image generation.
     */
    public static final class HostedManagement {

        /**
         * Marker for ImageSingleton keys which cannot have a value installed. This can happen when
         * a {@link LayeredImageSingleton} specified {@link PersistFlags#FORBIDDEN}.
         */
        private static final Object SINGLETON_INSTALLATION_FORBIDDEN = new Object();
        private static final SingletonInfo FORBIDDEN_SINGLETON_INFO_EMPTY_TRAITS;

        static {
            ImageSingletonsSupport.installSupport(new ImageSingletonsSupportImpl());
            var traitMap = SingletonTraitMap.create();
            traitMap.seal();
            FORBIDDEN_SINGLETON_INFO_EMPTY_TRAITS = new SingletonInfo(SINGLETON_INSTALLATION_FORBIDDEN, traitMap);
        }

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
            Function<SingletonTrait[], Object> forbiddenObjectCreator = (traits) -> {
                if (traits.length == 0) {
                    return FORBIDDEN_SINGLETON_INFO_EMPTY_TRAITS;
                }

                var traitMap = SingletonTraitMap.create();
                for (var trait : traits) {
                    traitMap.addTrait(trait);
                }
                traitMap.seal();
                return new SingletonInfo(SINGLETON_INSTALLATION_FORBIDDEN, traitMap);
            };
            var result = info.loadImageSingletons(forbiddenObjectCreator);
            Set<Class<?>> installedKeys = new HashSet<>();
            for (var entry : result.entrySet()) {
                Object singletonToInstall = entry.getKey();
                for (Class<?> key : entry.getValue()) {
                    if (singletonToInstall instanceof SingletonInfo forbiddenSingletonInfo) {
                        assert forbiddenSingletonInfo.singleton == SINGLETON_INSTALLATION_FORBIDDEN : forbiddenSingletonInfo.singleton;
                        var prev = configObjects.put(key, forbiddenSingletonInfo);
                        VMError.guarantee(prev == null, "Overwriting key %s existing value: %s", key.getTypeName(), prev);
                    } else {
                        addSingleton(key, singletonToInstall);
                    }
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

        private final EnumSet<SingletonLayeredInstallationKind.InstallationKind> forbiddenInstallationKinds;
        private Set<Class<?>> multiLayeredImageSingletonKeys;
        private final boolean layeredBuild;
        private final AnnotationExtractor extractor;
        private final Function<Class<?>, SingletonTrait[]> singletonTraitInjector;

        public HostedManagement() {
            this(null, null);
        }

        public HostedManagement(HostedImageLayerBuildingSupport support, AnnotationExtractor extractor) {
            this.configObjects = new ConcurrentHashMap<>();
            this.singletonToTraitMap = new ConcurrentIdentityHashMap<>();
            this.multiLayeredImageSingletonKeys = ConcurrentHashMap.newKeySet();
            forbiddenInstallationKinds = EnumSet.of(SingletonLayeredInstallationKind.InstallationKind.DISALLOWED);
            if (support != null) {
                this.layeredBuild = support.buildingImageLayer;
                this.singletonTraitInjector = support.getSingletonTraitInjector();
                if (support.buildingImageLayer) {
                    if (!support.buildingApplicationLayer) {
                        forbiddenInstallationKinds.add(SingletonLayeredInstallationKind.InstallationKind.APP_LAYER_ONLY);
                    }
                    if (!support.buildingInitialLayer) {
                        forbiddenInstallationKinds.add(SingletonLayeredInstallationKind.InstallationKind.INITIAL_LAYER_ONLY);
                    }
                }
            } else {
                this.layeredBuild = false;
                this.singletonTraitInjector = null;
            }
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
                if (layeredBuild) {
                    traitMap.getTrait(SingletonTraitKind.LAYERED_INSTALLATION_KIND).ifPresent(trait -> {
                        var kind = SingletonLayeredInstallationKind.getInstallationKind(trait);
                        if (forbiddenInstallationKinds.contains(kind)) {
                            throw VMError.shouldNotReachHere("Singleton with installation kind %s can no longer be added: %s", kind, value);
                        }
                    });
                }
                /*
                 * We are adding injected traits after checking for forbidden kinds because they do
                 * not adhere to the same restrictions (e.g., sometimes a singleton in a shared
                 * layer will be labelled as ApplicationLayerOnly).
                 */
                if (singletonTraitInjector != null) {
                    for (var trait : singletonTraitInjector.apply(key)) {
                        traitMap.addTrait(trait);
                    }
                }
            }
            SingletonTraitMap candidateTraitMap = traitMap;
            candidateTraitMap.seal();
            traitMap = singletonToTraitMap.computeIfAbsent(value, k -> candidateTraitMap);
            addSingletonToMap(key, value, traitMap);
        }

        private void addSingletonToMap(Class<?> key, Object value, SingletonTraitMap traitMap) {
            checkKey(key);
            if (value == null) {
                throw UserError.abort("ImageSingletons do not allow null value for key %s", key.getTypeName());
            }

            if (value instanceof MultiLayeredImageSingleton) {
                multiLayeredImageSingletonKeys.add(key);
            }

            traitMap.getTrait(SingletonTraitKind.LAYERED_INSTALLATION_KIND).ifPresent(trait -> {
                switch (SingletonLayeredInstallationKind.getInstallationKind(trait)) {
                    case APP_LAYER_ONLY, MULTI_LAYER ->
                        VMError.guarantee(key == value.getClass(), "singleton key %s must match singleton class %s", key, value);
                }
            });

            Object prevValue = configObjects.putIfAbsent(key, new SingletonInfo(value, traitMap));
            if (prevValue != null) {
                throw UserError.abort("ImageSingletons.add must not overwrite existing key %s%nExisting value: %s%nNew value: %s", key.getTypeName(), prevValue, value);
            }
        }

        Collection<Class<?>> getMultiLayeredImageSingletonKeys() {
            return multiLayeredImageSingletonKeys;
        }

        void freezeLayeredImageSingletonMetadata() {
            multiLayeredImageSingletonKeys = Set.copyOf(multiLayeredImageSingletonKeys);
        }

        Set<Object> getSingletonsWithTrait(SingletonLayeredInstallationKind.InstallationKind kind) {
            return configObjects.values().stream().filter(singletonInfo -> {
                /*
                 * We must filter out forbidden objects, as they are not actually installed in this
                 * image.
                 */
                if (singletonInfo.singleton != SINGLETON_INSTALLATION_FORBIDDEN) {
                    var optionalTrait = singletonInfo.traitMap().getTrait(SingletonTraitKind.LAYERED_INSTALLATION_KIND);
                    if (optionalTrait.orElse(null) instanceof SingletonTrait trait) {
                        return SingletonLayeredInstallationKind.getInstallationKind(trait) == kind;
                    }
                }
                return false;
            }).map(SingletonInfo::singleton).collect(Collectors.toUnmodifiableSet());
        }

        void forbidNewTraitInstallations(SingletonLayeredInstallationKind.InstallationKind kind) {
            forbiddenInstallationKinds.add(kind);
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

        public SingletonTraitMap getUninstalledSingletonTraitMap(Class<?> key) {
            VMError.guarantee(layeredBuild && SingletonTraitKind.LAYERED_INSTALLATION_KIND.isInConfiguration(layeredBuild));

            SingletonInfo info = configObjects.computeIfAbsent(key, k -> {

                SingletonTraits annotation = extractor.extractAnnotation(k, SingletonTraits.class, false);
                if (annotation != null) {
                    if (annotation.layeredInstallationKind() != null) {
                        var installationKindSupplierClass = annotation.layeredInstallationKind();
                        /*
                         * Initial Layer information should never be injected, as this is something
                         * which occurred in the past and should already be present in configObject
                         * when it exists.
                         */
                        if (installationKindSupplierClass != SingletonLayeredInstallationKind.InitialLayerOnly.class) {
                            SingletonLayeredInstallationKindSupplier installationKindSupplier = ReflectionUtil.newInstance(installationKindSupplierClass);
                            SingletonTrait installationTrait = installationKindSupplier.getLayeredInstallationKindTrait();
                            assert installationTrait.kind() == SingletonTraitKind.LAYERED_INSTALLATION_KIND : installationTrait;
                            SingletonTraitMap traitMap = SingletonTraitMap.create();
                            traitMap.addTrait(installationTrait);
                            traitMap.seal();
                            return new SingletonInfo(SINGLETON_INSTALLATION_FORBIDDEN, traitMap);
                        }
                    }
                }

                if (singletonTraitInjector != null) {
                    var traits = singletonTraitInjector.apply(key);
                    if (traits.length != 0) {
                        SingletonTraitMap traitMap = SingletonTraitMap.create();
                        for (var trait : traits) {
                            traitMap.addTrait(trait);
                        }
                        traitMap.seal();
                        return new SingletonInfo(SINGLETON_INSTALLATION_FORBIDDEN, traitMap);
                    }
                }

                return FORBIDDEN_SINGLETON_INFO_EMPTY_TRAITS;
            });

            VMError.guarantee(info.singleton == SINGLETON_INSTALLATION_FORBIDDEN, "We can only use this for uninstalled singletons");

            return info.traitMap();
        }
    }
}
