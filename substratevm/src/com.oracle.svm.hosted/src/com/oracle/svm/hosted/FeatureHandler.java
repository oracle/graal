/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.IsInConfigurationAccessImpl;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.util.ReflectionUtil.ReflectionUtilError;

/**
 * Handles the registration and iterations of {@link Feature features}.
 */
@SuppressWarnings("deprecation")
public class FeatureHandler {

    public static class Options {
        @APIOption(name = "features") //
        @Option(help = "A comma-separated list of fully qualified Feature implementation classes")//
        public static final HostedOptionKey<String[]> Features = new HostedOptionKey<>(null);
    }

    private final ArrayList<Feature> featureInstances = new ArrayList<>();
    private final HashSet<Class<?>> registeredFeatures = new HashSet<>();

    public void forEachFeature(Consumer<Feature> consumer) {
        for (Feature feature : featureInstances) {
            consumer.accept(feature);
        }
    }

    public void forEachGraalFeature(Consumer<GraalFeature> consumer) {
        for (Feature feature : featureInstances) {
            if (feature instanceof GraalFeature) {
                consumer.accept((GraalFeature) feature);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void registerFeatures(ImageClassLoader loader, DebugContext debug) {
        IsInConfigurationAccessImpl access = new IsInConfigurationAccessImpl(this, loader, debug);

        LinkedHashSet<Class<?>> automaticFeatures = new LinkedHashSet<>(loader.findAnnotatedClasses(AutomaticFeature.class, true));
        Map<Class<?>, Class<?>> specificAutomaticFeatures = new HashMap<>();
        for (Class<?> automaticFeature : automaticFeatures) {

            Class<Feature> mostSpecific = (Class<Feature>) automaticFeature;
            boolean foundMostSpecific = false;
            do {
                List<Class<? extends Feature>> featureSubclasses = loader.findSubclasses(mostSpecific, true);
                featureSubclasses.remove(mostSpecific);
                featureSubclasses.removeIf(o -> !automaticFeatures.contains(o));
                if (featureSubclasses.isEmpty()) {
                    foundMostSpecific = true;
                } else {
                    if (featureSubclasses.size() > 1) {
                        String candidates = featureSubclasses.stream().map(Class::getName).collect(Collectors.joining(" "));
                        VMError.shouldNotReachHere("Ambiguous @AutomaticFeature extension. Conflicting candidates: " + candidates);
                    }
                    mostSpecific = (Class<Feature>) featureSubclasses.get(0);
                }
            } while (!foundMostSpecific);

            if (mostSpecific != automaticFeature) {
                specificAutomaticFeatures.put(automaticFeature, mostSpecific);
            }
        }

        /* Remove specific since they get registered via their base */
        for (Class<?> specific : specificAutomaticFeatures.values()) {
            automaticFeatures.remove(specific);
        }

        Function<Class<?>, Class<?>> specificClassProvider = specificAutomaticFeatures::get;

        for (Class<?> featureClass : automaticFeatures) {
            registerFeature(featureClass, specificClassProvider, access);
        }

        for (String featureName : OptionUtils.flatten(",", Options.Features.getValue())) {
            try {
                registerFeature(Class.forName(featureName, true, loader.getClassLoader()), specificClassProvider, access);
            } catch (ClassNotFoundException e) {
                throw UserError.abort("feature " + featureName + " class not found on the classpath. Ensure that the name is correct and that the class is on the classpath.");
            }
        }
    }

    /**
     * Instantiates the given feature class and (recursively) all feature classes it requires.
     *
     * @param access
     */
    @SuppressWarnings("unchecked")
    private void registerFeature(Class<?> baseFeatureClass, Function<Class<?>, Class<?>> specificClassProvider, IsInConfigurationAccessImpl access) {
        if (!Feature.class.isAssignableFrom(baseFeatureClass)) {
            throw UserError.abort("Class does not implement " + Feature.class.getName() + ": " + baseFeatureClass.getName());
        }

        if (registeredFeatures.contains(baseFeatureClass)) {
            return;
        }

        /*
         * Immediately add to the registeredFeatures to avoid infinite recursion in case of cyclic
         * dependencies.
         */
        registeredFeatures.add(baseFeatureClass);

        Class<?> specificClass = specificClassProvider.apply(baseFeatureClass);
        Class<?> featureClass = specificClass != null ? specificClass : baseFeatureClass;
        Feature feature;
        try {
            feature = (Feature) ReflectionUtil.newInstance(featureClass);
        } catch (ReflectionUtilError ex) {
            throw UserError.abort(ex.getCause(), "Error instantiating Feature class " + featureClass.getTypeName() + ". Ensure the class is not abstract and has a no-argument constructor.");
        }

        if (!feature.isInConfiguration(access)) {
            return;
        }

        /*
         * All features are automatically added to the VMConfiguration, to allow convenient
         * configuration checks.
         */
        ImageSingletons.add((Class<Feature>) baseFeatureClass, feature);

        /*
         * First add dependent features so that initializers are executed in order of dependencies.
         */
        for (Class<? extends Feature> requiredFeatureClass : feature.getRequiredFeatures()) {
            registerFeature(requiredFeatureClass, specificClassProvider, access);
        }

        featureInstances.add(feature);
    }
}
