/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.feature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.shared.option.FutureDefaultsOptionsSupport;

/**
 * Temporary feature registration support shared by the builder and guest contexts while
 * {@code InternalFeatureBridge} exists. Once the bridge is removed, the builder's
 * {@code FeatureHandler} will operate on {@code JVMCIFeature} and the guest's
 * {@code GuestFeatureHandler} will operate on {@link Feature}, so this class must be removed.
 */
public abstract class FeatureHandlerBase {

    protected final ArrayList<Feature> featureInstances = new ArrayList<>();
    /** Plain {@code --features} classes published as singletons only for compatibility. */
    protected final ArrayList<Class<?>> compatibilityPublishedFeatureSingletons = new ArrayList<>();
    private final Map<Class<?>, Feature> activeFeatureInstances = new HashMap<>();
    /** Feature classes that were seen by registration, including inactive features. */
    protected final EconomicSet<Class<?>> registeredFeatures = EconomicSet.create();

    public final boolean containsFeature(Class<?> featureClass) {
        return registeredFeatures.contains(featureClass);
    }

    /**
     * Instantiates the given feature class and (recursively) all feature classes it requires.
     */
    protected final void registerFeature(Class<?> baseFeatureClass, Function<Class<?>, Class<?>> specificClassProvider, Feature.IsInConfigurationAccess access,
                    Feature.OnRegistrationAccess onRegistrationAccess, boolean publishExplicitFeatureSingleton, boolean printFeatures) {
        if (!Feature.class.isAssignableFrom(baseFeatureClass)) {
            throw invalidFeatureClass(baseFeatureClass);
        }

        if (registeredFeatures.contains(baseFeatureClass)) {
            publishExplicitFeatureSingletonIfAlreadyActive(baseFeatureClass, publishExplicitFeatureSingleton, printFeatures);
            return;
        }

        /*
         * Immediately add to the registeredFeatures to avoid infinite recursion in case of cyclic
         * dependencies.
         */
        registeredFeatures.add(baseFeatureClass);

        Class<?> specificClass = specificClassProvider == null ? null : specificClassProvider.apply(baseFeatureClass);
        Class<?> featureClass = specificClass != null ? specificClass : baseFeatureClass;
        Feature feature = instantiateFeature(featureClass);

        try {
            if (!feature.isInConfiguration(access)) {
                return;
            }
        } catch (Throwable t) {
            throw handleFeatureError(feature, t);
        }
        try {
            feature.onRegistration(onRegistrationAccess);
        } catch (Throwable t) {
            throw handleFeatureError(feature, t);
        }
        activeFeatureInstances.put(baseFeatureClass, feature);
        publishExplicitFeatureSingleton(baseFeatureClass, feature, publishExplicitFeatureSingleton, printFeatures);
        /*
         * First add dependent features so that initializers are executed in order of dependencies.
         */
        List<Class<? extends Feature>> requiredFeatures;
        try {
            requiredFeatures = feature.getRequiredFeatures();
        } catch (Throwable t) {
            throw handleFeatureError(feature, t);
        }
        for (Class<? extends Feature> requiredFeatureClass : requiredFeatures) {
            registerFeature(requiredFeatureClass, specificClassProvider, access, onRegistrationAccess, publishExplicitFeatureSingleton, printFeatures);
        }

        featureInstances.add(feature);
    }

    /**
     * A feature can be registered before an explicit registration reaches it, for example through
     * automatic registration or as another feature's dependency. The later explicit registration
     * must still process the already active instance instead of returning silently.
     */
    private void publishExplicitFeatureSingletonIfAlreadyActive(Class<?> baseFeatureClass, boolean publishExplicitFeatureSingleton, boolean printFeatures) {
        Feature feature = activeFeatureInstances.get(baseFeatureClass);
        if (feature != null) {
            publishExplicitFeatureSingleton(baseFeatureClass, feature, publishExplicitFeatureSingleton, printFeatures);
        }
    }

    /**
     * Preserves compatibility for directly user-specified {@code --features} entries, and for the
     * active plain hosted {@link Feature} dependencies reached from those entries, when they relied
     * on the former blanket feature singleton registration. Internal features and features reached
     * only through automatic discovery must opt in via {@link Feature#onRegistration} instead.
     * When the explicit feature singleton registration future-default is enabled, this compatibility
     * publication is skipped so builds only fail if code actually requires the feature singleton.
     */
    @SuppressWarnings("unchecked")
    private void publishExplicitFeatureSingleton(Class<?> baseFeatureClass, Feature feature, boolean publishExplicitFeatureSingleton, boolean printFeatures) {
        if (publishExplicitFeatureSingleton && !isInternalFeature(feature) && !ImageSingletons.contains(baseFeatureClass)) {
            if (FutureDefaultsOptionsSupport.singleton().explicitFeatureSingletonRegistration()) {
                return;
            }
            if (printFeatures) {
                compatibilityPublishedFeatureSingletons.add(baseFeatureClass);
            }
            ImageSingletons.add((Class<Feature>) baseFeatureClass, feature);
        }
    }

    protected abstract boolean isInternalFeature(Feature feature);

    protected abstract RuntimeException invalidFeatureClass(Class<?> featureClass);

    protected abstract Feature instantiateFeature(Class<?> featureClass);

    protected abstract Error handleFeatureError(Feature feature, Throwable throwable);
}
