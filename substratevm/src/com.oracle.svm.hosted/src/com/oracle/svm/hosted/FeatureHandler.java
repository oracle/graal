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

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeatureServiceRegistration;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.util.InterruptImageBuilding;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.hosted.FeatureImpl.IsInConfigurationAccessImpl;
import com.oracle.svm.shared.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.shared.option.APIOption;
import com.oracle.svm.shared.option.AccumulatingLocatableMultiOptionValue;
import com.oracle.svm.shared.option.HostedOptionKey;
import com.oracle.svm.shared.option.SubstrateOptionsParser;
import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.shared.util.ReflectionUtil.ReflectionUtilError;
import com.oracle.svm.shared.util.VMError;
import com.oracle.svm.shared.util.VMError.HostedError;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.options.Option;
import jdk.vm.ci.meta.MetaAccessProvider;
import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.impl.APIDeprecationSupport;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Handles the registration and iterations of {@link Feature features}.
 */
public class FeatureHandler {

    public static class Options {
        @APIOption(name = "features") //
        @Option(help = "A comma-separated list of fully qualified Feature implementation classes")//
        public static final HostedOptionKey<AccumulatingLocatableMultiOptionValue.Strings> Features = new HostedOptionKey<>(AccumulatingLocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

        private static List<String> userEnabledFeatures() {
            return Options.Features.getValue().values();
        }

    }

    private final ArrayList<Feature> featureInstances = new ArrayList<>();
    private final EconomicSet<Class<?>> registeredFeatures = EconomicSet.create();

    public void forEachFeature(Consumer<Feature> consumer) {
        for (Feature feature : featureInstances) {
            try {
                if (!ImageSingletons.lookup(APIDeprecationSupport.class).isUserEnabledFeaturesStarted() && Options.userEnabledFeatures().contains(feature.getClass().getName())) {
                    ImageSingletons.lookup(APIDeprecationSupport.class).setUserEnabledFeaturesStarted(true);
                }
                consumer.accept(feature);
            } catch (Throwable t) {
                throw handleFeatureError(feature, t);
            }
        }
        ImageSingletons.lookup(APIDeprecationSupport.class).setUserEnabledFeaturesStarted(false);
    }

    public void forEachGraalFeature(Consumer<InternalFeature> consumer) {
        for (Feature feature : featureInstances) {
            if (feature instanceof InternalFeature) {
                consumer.accept((InternalFeature) feature);
            }
        }
    }

    public boolean containsFeature(Class<?> c) {
        return registeredFeatures.contains(c);
    }

    @SuppressWarnings("unchecked")
    public void registerFeatures(ImageClassLoader loader, MetaAccessProvider originalMetaAccess, DebugContext debug) {
        IsInConfigurationAccessImpl access = new IsInConfigurationAccessImpl(this, loader, originalMetaAccess, debug);

        AutomaticallyRegisteredFeatureLoader automaticFeatureLoader = new AutomaticallyRegisteredFeatureLoader(loader);
        LinkedHashSet<Class<?>> automaticFeatures = automaticFeatureLoader.loadRegisteredClasses();
        automaticFeatureLoader.verifyGeneratedRegistrations(automaticFeatures);

        Map<Class<?>, Class<?>> specificAutomaticFeatures = new HashMap<>();
        for (Class<?> automaticFeature : automaticFeatures) {
            List<Class<?>> mostSpecificFeatures = automaticFeatureLoader.findMostSpecificClasses(automaticFeature, automaticFeatures);
            if (mostSpecificFeatures.size() > 1) {
                String candidates = mostSpecificFeatures.stream().map(Class::getName).collect(Collectors.joining(", "));
                throw UserError.abort("Ambiguous @%s extension for %s. Expected one most-specific annotated class, but found %s.",
                                AutomaticallyRegisteredFeature.class.getSimpleName(), automaticFeature.getName(), candidates);
            }
            Class<?> mostSpecificFeature = mostSpecificFeatures.getFirst();
            if (mostSpecificFeature != automaticFeature) {
                specificAutomaticFeatures.put(automaticFeature, mostSpecificFeature);
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

        List<ClassLoader> featureClassLoaders = loader.classLoaderSupport.getClassLoaders();
        for (String featureName : Options.userEnabledFeatures()) {
            Class<?> featureClass = null;
            for (ClassLoader featureClassLoader : featureClassLoaders) {
                try {
                    featureClass = Class.forName(featureName, true, featureClassLoader);
                    break;
                } catch (ClassNotFoundException e) {
                    /* Ignore */
                }
            }
            if (featureClass == null) {
                throw UserError.abort("User-enabled Feature %s class not found. Ensure that the name is correct and that the class is on the class- or module-path.", featureName);
            }
            registerFeature(featureClass, specificClassProvider, access);
        }
        if (NativeImageOptions.PrintFeatures.getValue()) {
            ReportUtils.report("feature information", SubstrateOptions.reportsPath(), "feature_info", "csv", out -> {
                out.println("Feature, Required Features");
                dumpAllFeatures(out);
            });
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
            throw UserError.abort("Class does not implement %s: %s", Feature.class.getName(), baseFeatureClass.getName());
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
            throw UserError.abort(ex.getCause(), "Error instantiating Feature class %s. Ensure the class is not abstract and has a no-argument constructor.", featureClass.getTypeName());
        }

        try {
            if (!feature.isInConfiguration(access)) {
                return;
            }
        } catch (Throwable t) {
            throw handleFeatureError(feature, t);
        }

        /*
         * All features are automatically added to the VMConfiguration, to allow convenient
         * configuration checks.
         */
        ImageSingletons.add((Class<Feature>) baseFeatureClass, feature);

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
            registerFeature(requiredFeatureClass, specificClassProvider, access);
        }

        featureInstances.add(feature);
    }

    public List<Feature> getUserSpecificFeatures() {
        ClassLoaderSupport classLoaderSupport = ImageSingletons.lookup(ClassLoaderSupport.class);
        List<String> userEnabledFeatures = Options.userEnabledFeatures();
        return featureInstances.stream()
                        .filter(f -> (!(f instanceof InternalFeature) || !((InternalFeature) f).isHidden()) &&
                                        (classLoaderSupport.isNativeImageClassLoader(f.getClass().getClassLoader()) || userEnabledFeatures.contains(f.getClass().getName())))
                        .collect(Collectors.toList());
    }

    public void dumpAllFeatures(PrintWriter out) {
        featureInstances.stream().sorted(Comparator.comparing(f -> f.getClass().getTypeName())).forEachOrdered(f -> {
            out.print(f.getClass().getTypeName());
            String requiredFeaturesString = f.getRequiredFeatures().stream()
                            .map(Class::getTypeName)
                            .collect(Collectors.joining(" ", "[", "]"));
            out.print(", ");
            out.println(requiredFeaturesString);
        });
    }

    private static UserException handleFeatureError(Feature feature, Throwable throwable) {
        /* Avoid wrapping UserError, VMError, and InterruptImageBuilding throwables. */
        if (throwable instanceof UserException userError) {
            throw userError;
        }
        if (throwable instanceof HostedError vmError) {
            throw vmError;
        }
        if (throwable instanceof InterruptImageBuilding iib) {
            throw iib;
        }

        String featureClassName = feature.getClass().getName();
        String throwableClassName = throwable.getClass().getName();
        if (InternalFeature.class.isAssignableFrom(feature.getClass())) {
            throw VMError.shouldNotReachHere("InternalFeature defined by %s unexpectedly failed with a(n) %s".formatted(featureClassName, throwableClassName), throwable);
        }
        throw UserError.abort(throwable, "Feature defined by %s unexpectedly failed with a(n) %s. Please report this problem to the authors of %s.",
                        featureClassName, throwableClassName, featureClassName);
    }

    private static final class AutomaticallyRegisteredFeatureLoader extends AutomaticallyRegisteredClassSupport<AutomaticallyRegisteredFeatureServiceRegistration, AutomaticallyRegisteredFeature> {
        private AutomaticallyRegisteredFeatureLoader(ImageClassLoader loader) {
            super(loader);
        }

        @Override
        protected Class<AutomaticallyRegisteredFeatureServiceRegistration> serviceRegistrationClass() {
            return AutomaticallyRegisteredFeatureServiceRegistration.class;
        }

        @Override
        protected Class<AutomaticallyRegisteredFeature> annotationClass() {
            return AutomaticallyRegisteredFeature.class;
        }

        @Override
        protected Error missingClassError(Throwable cause, String className) {
            throw UserError.abort(cause,
                            "Could not load automatically registered feature class %s from generated service metadata. " +
                                            "Either the annotation processor did not run for the project containing the feature, or the class is not on the class path of the image generator. " +
                                            "Applications should register a feature using the option %s.",
                            className, SubstrateOptionsParser.commandArgument(Options.Features, className));
        }

        @Override
        protected Error missingGeneratedRegistrationError(Class<?> annotatedClass) {
            throw UserError.abort("Feature %s annotated with @%s was not properly registered as a service. " +
                            "Either the annotation processor did not run for the project containing the feature, or the class is not on the class path of the image generator. " +
                            "The annotation is only for internal usage. Applications should register a feature using the option %s",
                            annotatedClass, AutomaticallyRegisteredFeature.class.getSimpleName(), SubstrateOptionsParser.commandArgument(Options.Features, annotatedClass.getName()));
        }

        @Override
        protected Error staleGeneratedRegistrationError(Class<?> registeredClass) {
            throw UserError.abort("Class %s was registered as an @%s service but is no longer annotated. Clean and rebuild the affected project to refresh generated annotation-processor outputs.",
                            registeredClass.getName(), AutomaticallyRegisteredFeature.class.getSimpleName());
        }
    }
}
