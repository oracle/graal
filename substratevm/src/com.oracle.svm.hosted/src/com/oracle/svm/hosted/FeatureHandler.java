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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.function.Consumer;

import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.graal.GraalFeature;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.IsInConfigurationAccessImpl;

/**
 * Handles the registration and iterations of {@link Feature features}.
 */
public class FeatureHandler {

    public static class Options {
        @Option(help = "Comma-separate list of fully qualified Feature implementation classes")//
        public static final HostedOptionKey<String> Features = new HostedOptionKey<>("");
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

    public void registerFeatures(ImageClassLoader loader) {
        IsInConfigurationAccessImpl access = new IsInConfigurationAccessImpl(this, loader);

        for (Class<?> automaticFeature : loader.findAnnotatedClasses(AutomaticFeature.class)) {
            registerFeature(automaticFeature, access);
        }

        String[] featureNames = Options.Features.getValue().split(",");
        for (String featureName : featureNames) {
            if (!featureName.isEmpty()) {
                try {
                    registerFeature(Class.forName(featureName, true, loader.getClassLoader()), access);
                } catch (ClassNotFoundException e) {
                    throw UserError.abort("feature " + featureName + " class not found on the classpath. Ensure that the name is correct and that the class is on the classpath.");
                }
            }
        }
    }

    /**
     * Instantiates the given feature class and (recursively) all feature classes it requires.
     *
     * @param access
     */
    @SuppressWarnings("unchecked")
    private void registerFeature(Class<?> featureClass, IsInConfigurationAccessImpl access) {
        if (!Feature.class.isAssignableFrom(featureClass)) {
            throw UserError.abort("Class does not implement " + Feature.class.getName() + ": " + featureClass.getName());
        }

        if (registeredFeatures.contains(featureClass)) {
            return;
        }
        /*
         * Immediately add to the registeredFeatures to avoid infinite recursion in case of cyclic
         * dependencies.
         */
        registeredFeatures.add(featureClass);

        Feature feature;
        try {
            Constructor<?> constructor = featureClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            feature = (Feature) constructor.newInstance();
            if (!feature.isInConfiguration(access)) {
                return;
            }
        } catch (NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException ex) {
            throw VMError.shouldNotReachHere(ex);
        }

        /*
         * All features are automatically added to the VMConfiguration, to allow convenient
         * configuration checks.
         */
        ImageSingletons.add((Class<Feature>) feature.getClass(), feature);

        /*
         * First add dependent features so that initializers are executed in order of dependencies.
         */
        for (Class<? extends Feature> requiredFeatureClass : feature.getRequiredFeatures()) {
            registerFeature(requiredFeatureClass, access);
        }

        featureInstances.add(feature);
    }
}
