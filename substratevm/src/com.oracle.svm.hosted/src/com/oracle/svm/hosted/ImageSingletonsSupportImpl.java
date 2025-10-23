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
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.impl.AnnotationExtractor;
import org.graalvm.nativeimage.impl.ImageSingletonsSupport;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.layeredimage.LayeredImageSingletonSupport;
import com.oracle.svm.core.layeredimage.LoadedLayeredImageSingletonInfo;
import com.oracle.svm.core.util.ConcurrentIdentityHashMap;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.layeredimage.HostedImageLayerBuildingSupport;
import com.oracle.svm.hosted.layeredimage.SVMImageLayerSingletonLoader;
import com.oracle.svm.sdk.staging.hosted.layeredimage.LayeredPersistFlags;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonAccess;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonAccessFlags;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonAccessSupplier;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredCallbacks;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredCallbacksSupplier;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredInstallationKind;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonLayeredInstallationKindSupplier;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonTrait;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonTraitKind;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonTraits;
import com.oracle.svm.sdk.staging.hosted.traits.SingletonTraitsSupplier;
import com.oracle.svm.sdk.staging.layeredimage.ImageLayerBuildingSupport;
import com.oracle.svm.sdk.staging.layeredimage.MultiLayeredImageSingleton;
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
    public Set<Object> getSingletonsWithTrait(SingletonLayeredInstallationKind.InstallationKind kind) {
        return HostedManagement.getAndAssertExists().getSingletonsWithTrait(kind);
    }

    @Override
    public Collection<Class<?>> getKeysWithTrait(SingletonLayeredInstallationKind.InstallationKind kind) {
        return HostedManagement.getAndAssertExists().getKeysWithTrait(kind);
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
            buildtimeAccessAllowed = accessTrait.map(singletonTrait -> {
                SingletonAccessFlags access = SingletonAccess.getAccess(singletonTrait);
                return access == SingletonAccessFlags.BUILDTIME_ACCESS_ONLY || access == SingletonAccessFlags.ALL_ACCESS;
            }).orElse(true);
            runtimeAccessAllowed = accessTrait.map(singletonTrait -> {
                SingletonAccessFlags access = SingletonAccess.getAccess(singletonTrait);
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
         * a {@link SingletonLayeredCallbacks#doPersist} specified
         * {@link LayeredPersistFlags#FORBIDDEN}.
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
                singletonDuringImageBuild.addSingleton(ImageLayerBuildingSupport.class, new ImageLayerBuildingSupport(false, false, false, MultiLayeredImageSingleton.UNUSED_LAYER_NUMBER) {
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

        public static void persistSingletonInfo() {
            var list = singletonDuringImageBuild.configObjects.entrySet().stream().filter(e -> e.getValue().traitMap.getTrait(SingletonTraitKind.LAYERED_CALLBACKS).isPresent())
                            .sorted(Comparator.comparing(e -> e.getKey().getName()))
                            .toList();
            HostedImageLayerBuildingSupport.singleton().getWriter().writeImageSingletonInfo(list);
        }

        private final Map<Class<?>, SingletonInfo> configObjects;
        private final Map<Object, SingletonTraitMap> singletonToTraitMap;
        /**
         * Tracks the status of singletons for which a registration callback needs to be executed
         * upon installation. The key will always be the singleton object, and the value will be
         * either a {@link Boolean} or {@link Lock} based on whether the callback's execution is
         * still in progress or has completed.
         */
        private final Map<Object, Object> singletonRegistrationCallbackStatus;

        private final EnumSet<SingletonLayeredInstallationKind.InstallationKind> forbiddenInstallationKinds;
        private final boolean layeredBuild;
        private final boolean extensionLayerBuild;
        private final AnnotationExtractor extractor;
        private final Function<Class<?>, SingletonTrait[]> singletonTraitInjector;
        private final SVMImageLayerSingletonLoader singletonLoader;

        public HostedManagement() {
            this(null, null);
        }

        public HostedManagement(HostedImageLayerBuildingSupport support, AnnotationExtractor extractor) {
            this.configObjects = new ConcurrentHashMap<>();
            this.singletonToTraitMap = new ConcurrentIdentityHashMap<>();
            forbiddenInstallationKinds = EnumSet.of(SingletonLayeredInstallationKind.InstallationKind.DISALLOWED);
            if (support != null) {
                this.layeredBuild = support.buildingImageLayer;
                this.extensionLayerBuild = support.buildingImageLayer && !support.buildingInitialLayer;
                this.singletonTraitInjector = support.getSingletonTraitInjector();
                this.singletonLoader = support.getSingletonLoader();
                this.singletonRegistrationCallbackStatus = extensionLayerBuild ? new ConcurrentIdentityHashMap<>() : null;
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
                this.extensionLayerBuild = false;
                this.singletonTraitInjector = null;
                this.singletonLoader = null;
                this.singletonRegistrationCallbackStatus = null;
            }
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
            traitMap = singletonToTraitMap.computeIfAbsent(value, _ -> candidateTraitMap);
            addSingletonToMap(key, value, traitMap);
        }

        private void addSingletonToMap(Class<?> key, Object value, SingletonTraitMap traitMap) {
            checkKey(key);
            if (value == null) {
                throw UserError.abort("ImageSingletons do not allow null value for key %s", key.getTypeName());
            }

            traitMap.getTrait(SingletonTraitKind.LAYERED_INSTALLATION_KIND).ifPresent(trait -> {
                switch (SingletonLayeredInstallationKind.getInstallationKind(trait)) {
                    case APP_LAYER_ONLY ->
                        VMError.guarantee(key == value.getClass(), "singleton key %s must match singleton class %s", key, value);
                    case MULTI_LAYER -> {
                        VMError.guarantee(key == value.getClass(), "singleton key %s must match singleton class %s", key, value);
                        boolean runtimeAccess = traitMap.getTrait(SingletonTraitKind.ACCESS)
                                        .map(singletonTrait -> {
                                            SingletonAccessFlags access = SingletonAccess.getAccess(singletonTrait);
                                            return access == SingletonAccessFlags.RUNTIME_ACCESS_ONLY || access == SingletonAccessFlags.ALL_ACCESS;
                                        }).orElse(false);
                        VMError.guarantee(runtimeAccess, "MultiLayer singleton must have runtime access %s %s", key, value);
                    }
                }
            });

            /* Run onSingletonRegistration hook if needed. */
            if (extensionLayerBuild) {
                if (singletonLoader.hasRegistrationCallback(key)) {
                    synchronizeRegistrationCallbackExecution(value, new Runnable() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public void run() {
                            var trait = traitMap.getTrait(SingletonTraitKind.LAYERED_CALLBACKS).get();
                            SubstrateUtil.cast(trait.metadata(), SingletonLayeredCallbacks.class).onSingletonRegistration(singletonLoader.getImageSingletonLoader(key), value);
                        }
                    });
                }
            }

            Object prevValue = configObjects.putIfAbsent(key, new SingletonInfo(value, traitMap));
            if (prevValue != null) {
                throw UserError.abort("ImageSingletons.add must not overwrite existing key %s%nExisting value: %s%nNew value: %s", key.getTypeName(), prevValue, value);
            }
        }

        /**
         * Ensures the provided registrationCallback will execute only once per a singleton.
         * Regardless of which thread executes the registrationCallback, this method will not return
         * until the registrationCallback has been executed.
         */
        private void synchronizeRegistrationCallbackExecution(Object singleton, Runnable registrationCallback) {
            while (true) {
                var status = singletonRegistrationCallbackStatus.get(singleton);
                if (status == null) {
                    // create a lock for other threads to wait on
                    ReentrantLock lock = new ReentrantLock();
                    lock.lock();
                    try {
                        status = singletonRegistrationCallbackStatus.computeIfAbsent(singleton, _ -> lock);
                        if (status != lock) {
                            // failed to install lock. Repeat loop.
                            continue;
                        }

                        // Run registrationCallback
                        registrationCallback.run();

                        // the registrationCallback has finished - update its status
                        var prev = singletonRegistrationCallbackStatus.put(singleton, Boolean.TRUE);
                        VMError.guarantee(prev == lock);
                    } finally {
                        lock.unlock();
                    }
                } else if (status instanceof Lock lock) {
                    lock.lock();
                    try {
                        /*
                         * Once the lock can be acquired we know the registrationCallback has been
                         * completed and we can proceed.
                         */
                        assert singletonRegistrationCallbackStatus.get(singleton) == Boolean.TRUE;
                    } finally {
                        lock.unlock();
                    }
                } else {
                    // the registrationCallback has already completed
                    assert status == Boolean.TRUE;
                }
                /* At this point the registrationCallback has executed so it is safe to proceed. */
                break;
            }
        }

        private static boolean filterOnKind(SingletonInfo singletonInfo, SingletonLayeredInstallationKind.InstallationKind kind) {
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
        }

        Set<Object> getSingletonsWithTrait(SingletonLayeredInstallationKind.InstallationKind kind) {
            return Collections.unmodifiableSet(configObjects.values().stream().filter(singletonInfo -> filterOnKind(singletonInfo, kind)).map(SingletonInfo::singleton)
                            .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new IdentityHashMap<>()))));
        }

        Collection<Class<?>> getKeysWithTrait(SingletonLayeredInstallationKind.InstallationKind kind) {
            return Collections.unmodifiableSet(configObjects.entrySet().stream().filter(e -> filterOnKind(e.getValue(), kind)).map(Map.Entry::getKey)
                            .collect(Collectors.toCollection(() -> Collections.newSetFromMap(new IdentityHashMap<>()))));
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
            if (singleton == SINGLETON_INSTALLATION_FORBIDDEN) {
                throw UserError.abort("Singleton is forbidden in current layer. Key: %s", key.getTypeName());
            }
            if (!allowMultiLayered) {
                var trait = info.traitMap().getTrait(SingletonTraitKind.LAYERED_INSTALLATION_KIND);
                trait.ifPresent(t -> {
                    if (SingletonLayeredInstallationKind.getInstallationKind(t) == SingletonLayeredInstallationKind.InstallationKind.MULTI_LAYER) {
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

                SingletonTraits annotation = extractor.extractAnnotation(k, SingletonTraits.class, false);
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
