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

import static com.oracle.svm.hosted.ImageSingletonsSupportImpl.HostedManagement.SINGLETON_INSTALLATION_FORBIDDEN;
import static com.oracle.svm.hosted.ImageSingletonsSupportImpl.SingletonInfo.FORBIDDEN_SINGLETON_INFO_EMPTY_TRAITS;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.AnnotationExtractor;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;

import com.oracle.svm.core.layeredimagesingleton.LayeredImageSingletonSupport;
import com.oracle.svm.core.util.ConcurrentIdentityHashMap;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.shared.singletons.LayeredPersistFlags;
import com.oracle.svm.shared.singletons.SingletonAccessFlags;
import com.oracle.svm.shared.singletons.traits.AccessSingletonTrait;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.EmptyMetadata;
import com.oracle.svm.shared.singletons.traits.LayeredCallbacksSingletonTrait;
import com.oracle.svm.shared.singletons.traits.LayeredInstallationKindSingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonAccessSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKindSupplier;
import com.oracle.svm.shared.singletons.traits.SingletonTrait;
import com.oracle.svm.shared.singletons.traits.SingletonTraitKind;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.singletons.traits.SingletonTraitsSupplier;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.shared.util.ReflectionUtil;

import jdk.graal.compiler.debug.Assertions;

@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = NoLayeredCallbacks.class)
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
    public Set<Object> getSingletonsWithTrait(SingletonLayeredInstallationKind kind) {
        return HostedManagement.getAndAssertExists().getSingletonsWithTrait(kind);
    }

    @Override
    public Collection<Class<?>> getKeysWithTrait(SingletonLayeredInstallationKind kind) {
        return HostedManagement.getAndAssertExists().getKeysWithTrait(kind);
    }

    @Override
    public <S extends SingletonTrait<?>> S getTraitForUninstalledSingleton(Class<?> key, Class<S> traitClass) {
        SingletonTraitMap map = HostedManagement.getAndAssertExists().getUninstalledSingletonTraitMap(key);
        if (map == null) {
            return null;
        } else {
            return map.getTrait(traitClass).orElse(null);
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

        static final SingletonInfo FORBIDDEN_SINGLETON_INFO_EMPTY_TRAITS = new SingletonInfo(SINGLETON_INSTALLATION_FORBIDDEN, SingletonTraitMap.create().seal());

        public static SingletonInfo forbiddenSingletonInfo(SingletonTrait<?>... traits) {
            if (traits.length == 0) {
                return FORBIDDEN_SINGLETON_INFO_EMPTY_TRAITS;
            }
            SingletonTraitMap traitMap = SingletonTraitMap.create(traits).seal();
            return new SingletonInfo(SINGLETON_INSTALLATION_FORBIDDEN, traitMap);
        }

        Object singleton;
        final SingletonTraitMap traitMap;
        final boolean buildtimeAccessAllowed;
        final boolean runtimeAccessAllowed;

        SingletonInfo(Object singleton, SingletonTraitMap traitMap) {
            assert traitMap.sealed : traitMap;

            this.singleton = singleton;
            this.traitMap = traitMap;

            var accessTrait = traitMap.getTrait(AccessSingletonTrait.class);
            buildtimeAccessAllowed = accessTrait.map(singletonTrait -> {
                SingletonAccessFlags access = singletonTrait.metadata().getAccessFlags();
                return access == SingletonAccessFlags.BUILDTIME_ACCESS_ONLY || access == SingletonAccessFlags.ALL_ACCESS;
            }).orElse(true);
            runtimeAccessAllowed = accessTrait.map(singletonTrait -> {
                SingletonAccessFlags access = singletonTrait.metadata().getAccessFlags();
                return access == SingletonAccessFlags.RUNTIME_ACCESS_ONLY || access == SingletonAccessFlags.ALL_ACCESS;
            }).orElse(true);
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
        private final EnumMap<SingletonTraitKind, SingletonTrait<?>> traitMap;
        private boolean sealed = false;

        private SingletonTraitMap(EnumMap<SingletonTraitKind, SingletonTrait<?>> traitMap) {
            this.traitMap = traitMap;
        }

        /** Create a trait map from given traits. */
        private static SingletonTraitMap create(SingletonTrait<?>... traits) {
            SingletonTraitMap traitMap = create();
            for (SingletonTrait<?> trait : traits) {
                traitMap.addTrait(trait);
            }
            return traitMap;
        }

        /** Create an empty traits map. */
        private static SingletonTraitMap create() {
            return new SingletonTraitMap(new EnumMap<>(SingletonTraitKind.class));
        }

        void addTrait(SingletonTrait<?> value) {
            assert !sealed : "cannot add further traits";
            SingletonTraitKind key = value.kind();
            var prev = traitMap.put(key, value);
            assert prev == null : Assertions.errorMessage(key, value, prev);
        }

        public boolean isEmpty() {
            return traitMap.isEmpty();
        }

        @SuppressWarnings("unchecked")
        private <S extends SingletonTrait<?>> Optional<S> getTrait(SingletonTraitKind key) {
            S trait = (S) traitMap.get(key);
            return Optional.ofNullable(trait);
        }

        public <S extends SingletonTrait<?>> Optional<S> getTrait(Class<S> traitClass) {
            return getTrait(SingletonTrait.asTraitKind(traitClass));
        }

        public boolean containsTrait(Class<? extends SingletonTrait<?>> traitClass) {
            return getTrait(traitClass).isPresent();
        }

        SingletonTraitMap seal() {
            sealed = true;
            return this;
        }

        /**
         * Creates a new {@link SingletonTraitMap} based on the {@link SingletonTraits} assigned to
         * a given singleton.
         */
        static SingletonTraitMap getAnnotatedTraits(Class<?> singletonClass, AnnotationExtractor extractor, boolean layeredBuild) {
            SingletonTraitMap traitMap = SingletonTraitMap.create();
            if (extractor != null) {
                SingletonTraits annotation = extractor.extractAnnotation(singletonClass, SingletonTraits.class);

                if (annotation != null) {
                    if (annotation.access() != null) {
                        var accessSupplierClass = annotation.access();
                        SingletonAccessSupplier accessSupplier = ReflectionUtil.newInstance(accessSupplierClass);
                        AccessSingletonTrait accessTrait = accessSupplier.getAccessTrait();
                        assert accessTrait.kind() == SingletonTraitKind.ACCESS && accessTrait.kind().isInConfiguration(layeredBuild) : accessTrait;
                        traitMap.addTrait(accessTrait);
                    }

                    if (SingletonTraitKind.LAYERED_CALLBACKS.isInConfiguration(layeredBuild) && annotation.layeredCallbacks() != null) {
                        var callbacksSupplierClass = annotation.layeredCallbacks();
                        SingletonLayeredCallbacksSupplier callbacksSupplier = ReflectionUtil.newInstance(callbacksSupplierClass);
                        LayeredCallbacksSingletonTrait callbacksTrait = callbacksSupplier.getLayeredCallbacksTrait();
                        assert callbacksTrait.kind() == SingletonTraitKind.LAYERED_CALLBACKS : callbacksTrait;
                        traitMap.addTrait(callbacksTrait);
                    }

                    if (SingletonTraitKind.LAYERED_INSTALLATION_KIND.isInConfiguration(layeredBuild) && annotation.layeredInstallationKind() != null) {
                        var installationKindSupplierClass = annotation.layeredInstallationKind();
                        SingletonLayeredInstallationKindSupplier installationKindSupplier = ReflectionUtil.newInstance(installationKindSupplierClass);
                        LayeredInstallationKindSingletonTrait installationTrait = installationKindSupplier.getLayeredInstallationKindTrait();
                        assert installationTrait.kind() == SingletonTraitKind.LAYERED_INSTALLATION_KIND : installationTrait;
                        traitMap.addTrait(installationTrait);
                    }

                    if (annotation.other() != null) {
                        for (var traitSupplierClass : annotation.other()) {
                            SingletonTraitsSupplier traitSupplier = ReflectionUtil.newInstance(traitSupplierClass);
                            SingletonTrait<EmptyMetadata> trait = traitSupplier.getTrait();
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
         * a {@link SingletonLayeredCallbacks#doPersist} specified
         * {@link LayeredPersistFlags#FORBIDDEN}.
         */
        static final Object SINGLETON_INSTALLATION_FORBIDDEN = new Object();

        static {
            ImageSingletonsSupport.installSupport(new ImageSingletonsSupportImpl());
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
            UserError.guarantee(singletonDuringImageBuild == null, "Only one native image build can run at a time");
            singletonDuringImageBuild = vmConfig;
            // Now the singleton registry is installed and ImageSingletons.add() can be invoked.
        }

        /**
         * Install singletons from the provided map.
         *
         * @param singletons mapping from singleton objects or {@link SingletonInfo} placeholders to
         *            all the keys they should be mapped to.
         * @return all keys for which a singleton was installed.
         */
        EconomicSet<Class<?>> installSingletons(Map<Object, EconomicSet<Class<?>>> singletons) {
            EconomicSet<Class<?>> installedKeys = EconomicSet.create();
            for (var entry : singletons.entrySet()) {
                Object singletonToInstall = entry.getKey();
                for (Class<?> key : entry.getValue()) {
                    if (singletonToInstall instanceof SingletonInfo forbiddenSingletonInfo) {
                        assert forbiddenSingletonInfo.singleton == SINGLETON_INSTALLATION_FORBIDDEN : forbiddenSingletonInfo.singleton;
                        var prev = configObjects.putIfAbsent(key, forbiddenSingletonInfo);
                        VMError.guarantee(prev == null, "Overwriting key %s existing value: %s", key.getTypeName(), prev);
                    } else {
                        addSingleton(key, singletonToInstall);
                    }
                    installedKeys.add(key);
                }
            }
            return installedKeys;
        }

        public static void clear() {
            singletonDuringImageBuild = null;
        }

        /**
         * @return singletons that have a {@link LayeredCallbacksSingletonTrait}. These singletons
         *         provide a recipe for persisting and loading them in a subsequent layer.
         */
        public static List<Entry<Class<?>, SingletonInfo>> getSingletonsToPersist() {
            return singletonDuringImageBuild.configObjects.entrySet().stream().filter(e -> e.getValue().traitMap.containsTrait(LayeredCallbacksSingletonTrait.class))
                            .sorted(Comparator.comparing(e -> e.getKey().getName()))
                            .toList();
        }

        /**
         * This is the singleton registry. It contains mappings between singleton classes and the
         * corresponding singleton info objects.
         */
        private final Map<Class<?>, SingletonInfo> configObjects;
        private final Map<Object, SingletonTraitMap> singletonToTraitMap;

        private final boolean layeredBuild;
        private final AnnotationExtractor extractor;
        /** Callback to be executed before the singleton is published in the registry. */
        private final BiConsumer<Class<?>, SingletonInfo> singletonRegistrationCallback;
        /** Callback to be executed before a singleton is registered. */
        private final BiConsumer<Object, SingletonTraitMap> singletonValidationCallback;
        /** Mechanism to inject additional traits on singleton registration. */
        private final Function<Class<?>, SingletonTrait<?>[]> singletonTraitInjector;

        public HostedManagement() {
            this(null, null, null, null, false);
        }

        public HostedManagement(AnnotationExtractor extractor, BiConsumer<Class<?>, SingletonInfo> registrationCallback,
                        BiConsumer<Object, SingletonTraitMap> singletonValidationCallback, Function<Class<?>, SingletonTrait<?>[]> singletonTraitInjector, boolean buildingImageLayer) {
            this.configObjects = new ConcurrentHashMap<>();
            this.singletonToTraitMap = new ConcurrentIdentityHashMap<>();
            this.singletonRegistrationCallback = registrationCallback;
            this.singletonValidationCallback = singletonValidationCallback;
            this.singletonTraitInjector = singletonTraitInjector;
            this.layeredBuild = buildingImageLayer;
            this.extractor = extractor;
        }

        <T> void doAdd(Class<T> key, T value) {
            addSingleton(key, value);
        }

        /**
         * Creates or collects the {@link SingletonTraitMap} associated with this singleton before
         * adding the singleton to the internal map.
         */
        private void addSingleton(Class<?> key, Object value) {
            SingletonTraitMap traitMap = singletonToTraitMap.get(value);
            if (traitMap == null) {
                traitMap = SingletonTraitMap.getAnnotatedTraits(value.getClass(), extractor, layeredBuild);
                if (singletonValidationCallback != null) {
                    singletonValidationCallback.accept(value, traitMap);
                }
                /*
                 * We are adding injected traits after checking for forbidden kinds because they do
                 * not adhere to the same restrictions (e.g., sometimes a singleton in a shared
                 * layer will be labeled as ApplicationLayerOnly).
                 */
                if (singletonTraitInjector != null) {
                    for (var trait : singletonTraitInjector.apply(key)) {
                        traitMap.addTrait(trait);
                    }
                }
            }
            SingletonTraitMap candidateTraitMap = traitMap;
            candidateTraitMap.seal();
            traitMap = singletonToTraitMap.computeIfAbsent(value, _ -> candidateTraitMap);
            addSingletonToMap(key, value, traitMap);
        }

        private void addSingletonToMap(Class<?> key, Object value, SingletonTraitMap traitMap) {
            checkKey(key);
            if (value == null) {
                throw UserError.abort("ImageSingletons do not allow null value for key %s", key.getTypeName());
            }

            var installationTrait = traitMap.getTrait(LayeredInstallationKindSingletonTrait.class);
            installationTrait.ifPresent(it -> {
                switch (it.metadata()) {
                    case APP_LAYER_ONLY ->
                        VMError.guarantee(key == value.getClass(), "singleton key %s must match singleton class %s", key, value);
                    case MULTI_LAYER -> {
                        VMError.guarantee(key == value.getClass(), "singleton key %s must match singleton class %s", key, value);
                        var accessTrait = traitMap.getTrait(AccessSingletonTrait.class);
                        boolean runtimeAccess = accessTrait.map(at -> {
                            SingletonAccessFlags access = at.metadata().getAccessFlags();
                            return access == SingletonAccessFlags.RUNTIME_ACCESS_ONLY || access == SingletonAccessFlags.ALL_ACCESS;
                        }).orElse(false);
                        VMError.guarantee(runtimeAccess, "MultiLayer singleton must have runtime access %s %s", key, value);
                    }
                }
            });

            SingletonInfo singletonInfo = new SingletonInfo(value, traitMap);
            if (singletonRegistrationCallback != null) {
                /* Run the singleton registration callback before publishing the singleton. */
                singletonRegistrationCallback.accept(key, singletonInfo);
            }

            Object prevValue = configObjects.putIfAbsent(key, singletonInfo);
            UserError.guarantee(prevValue == null, "ImageSingletons.add must not overwrite existing key %s%nExisting value: %s%nNew value: %s", key.getTypeName(), prevValue, value);
        }

        private static boolean filterOnKind(SingletonInfo singletonInfo, SingletonLayeredInstallationKind kind) {
            /*
             * We must filter out forbidden objects, as they are not actually installed in this
             * image.
             */
            if (singletonInfo.singleton != SINGLETON_INSTALLATION_FORBIDDEN) {
                var trait = singletonInfo.traitMap().getTrait(LayeredInstallationKindSingletonTrait.class);
                if (trait.isPresent()) {
                    return trait.get().metadata() == kind;
                }
            }
            return false;
        }

        Set<Object> getSingletonsWithTrait(SingletonLayeredInstallationKind kind) {
            return Collections.unmodifiableSet(configObjects.values().stream().filter(singletonInfo -> filterOnKind(singletonInfo, kind)).map(SingletonInfo::singleton)
                            .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new IdentityHashMap<>()))));
        }

        Collection<Class<?>> getKeysWithTrait(SingletonLayeredInstallationKind kind) {
            return Collections.unmodifiableSet(configObjects.entrySet().stream().filter(e -> filterOnKind(e.getValue(), kind)).map(Entry::getKey)
                            .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new IdentityHashMap<>()))));
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
            if (singleton == SINGLETON_INSTALLATION_FORBIDDEN) {
                throw UserError.abort("Singleton is forbidden in current layer. Key: %s", key.getTypeName());
            }
            if (!allowMultiLayered) {
                Optional<LayeredInstallationKindSingletonTrait> trait = info.traitMap().getTrait(LayeredInstallationKindSingletonTrait.class);
                trait.ifPresent(t -> {
                    if (t.metadata() == SingletonLayeredInstallationKind.MULTI_LAYER) {
                        throw UserError.abort("Forbidden lookup of MultiLayeredImageSingleton. Use LayeredImageSingletonSupport.lookup if really necessary. Key: %s, object %s", key, singleton);
                    }
                });
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

                SingletonTraits annotation = extractor.extractAnnotation(k, SingletonTraits.class);
                if (annotation != null) {
                    if (annotation.layeredInstallationKind() != null) {
                        var installationKindSupplierClass = annotation.layeredInstallationKind();
                        /*
                         * Initial Layer information should never be injected, as either
                         *
                         * 1) We are building the initial layer, so it should be present in the
                         * configObject which it exists.
                         *
                         * 2) We are building an extension layer, so it is not relevant for this
                         * layer.
                         */
                        if (installationKindSupplierClass != SingletonLayeredInstallationKind.InitialLayerOnly.class) {
                            SingletonLayeredInstallationKindSupplier installationKindSupplier = ReflectionUtil.newInstance(installationKindSupplierClass);
                            LayeredInstallationKindSingletonTrait installationTrait = installationKindSupplier.getLayeredInstallationKindTrait();
                            assert installationTrait.kind() == SingletonTraitKind.LAYERED_INSTALLATION_KIND : installationTrait;
                            return new SingletonInfo(SINGLETON_INSTALLATION_FORBIDDEN, SingletonTraitMap.create(installationTrait).seal());
                        }
                    }
                }

                if (singletonTraitInjector != null) {
                    var traits = singletonTraitInjector.apply(key);
                    if (traits.length != 0) {
                        SingletonTraitMap traitMap = SingletonTraitMap.create(traits);
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
