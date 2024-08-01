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

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.options.Option;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.graal.pointsto.reports.ReportUtils;
import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeatureServiceRegistration;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.option.APIOption;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.SubstrateOptionsParser;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.UserError.UserException;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.VMError.HostedError;
import com.oracle.svm.hosted.FeatureImpl.IsInConfigurationAccessImpl;
import com.oracle.svm.util.LogUtils;
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
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> Features = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.buildWithCommaDelimiter());

        private static List<String> userEnabledFeatures() {
            return Options.Features.getValue().values();
        }

        @Option(help = "Allow using deprecated @AutomaticFeature annotation. If set to false, an error is shown instead of a warning.")//
        public static final HostedOptionKey<Boolean> AllowDeprecatedAutomaticFeature = new HostedOptionKey<>(true);
    }

    private final ArrayList<Feature> featureInstances = new ArrayList<>();
    private final HashSet<Class<?>> registeredFeatures = new HashSet<>();

    public void forEachFeature(Consumer<Feature> consumer) {
        for (Feature feature : featureInstances) {
            try {
                consumer.accept(feature);
            } catch (Throwable t) {
                throw handleFeatureError(feature, t);
            }
        }
    }

    public boolean containsFeature(Class<?> c) {
        return registeredFeatures.contains(c);
    }

    public void forEachGraalFeature(Consumer<InternalFeature> consumer) {
        for (Feature feature : featureInstances) {
            if (feature instanceof InternalFeature) {
                consumer.accept((InternalFeature) feature);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void registerFeatures(ImageClassLoader loader, DebugContext debug) {
        IsInConfigurationAccessImpl access = new IsInConfigurationAccessImpl(this, loader, debug);

        LinkedHashSet<Class<?>> automaticFeatures = new LinkedHashSet<>();
        NativeImageSystemClassLoader nativeImageSystemClassLoader = NativeImageSystemClassLoader.singleton();
        for (var serviceRegistration : ServiceLoader.load(AutomaticallyRegisteredFeatureServiceRegistration.class, nativeImageSystemClassLoader.defaultSystemClassLoader)) {
            Class<?> annotatedFeatureClass = loader.findClass(serviceRegistration.getClassName()).getOrFail();
            /*
             * For simplicity, we do not look at the Platforms annotation ourselves and instead
             * check if the ImageClassLoader found that class too.
             */
            if (loader.findSubclasses(annotatedFeatureClass, true).contains(annotatedFeatureClass)) {
                automaticFeatures.add(annotatedFeatureClass);
            }
        }
        for (Class<?> annotatedFeatureClass : loader.findAnnotatedClasses(AutomaticallyRegisteredFeature.class, true)) {
            if (!automaticFeatures.contains(annotatedFeatureClass)) {
                throw UserError.abort("Feature " + annotatedFeatureClass + " annotated with @" + AutomaticallyRegisteredFeature.class.getSimpleName() + " was not properly registered as a service. " +
                                "Either the annotation processor did not run for the project containing the feature, or the class is not on the class path of the image generator. " +
                                "The annotation is only for internal usage. Applications should register a feature using the option " +
                                SubstrateOptionsParser.commandArgument(Options.Features, annotatedFeatureClass.getName()));
            }
        }

        for (var annotatedFeatureClass : loader.findAnnotatedClasses(AutomaticFeature.class, true)) {
            String msg = "Feature " + annotatedFeatureClass + " is annotated with the deprecated annotation @" + AutomaticFeature.class.getSimpleName() + ". " +
                            "Support for this annotation will be removed in a future version of GraalVM. " +
                            "Applications should register a feature using the option " + SubstrateOptionsParser.commandArgument(Options.Features, annotatedFeatureClass.getName());
            if (Options.AllowDeprecatedAutomaticFeature.getValue()) {
                LogUtils.warning(msg);
            } else {
                throw UserError.abort(msg);
            }
            automaticFeatures.add(annotatedFeatureClass);
        }

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
                        VMError.shouldNotReachHere("Ambiguous @AutomaticallyRegisteredFeature / @AutomaticFeature extension. Conflicting candidates: " + candidates);
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

        for (String featureName : Options.userEnabledFeatures()) {
            Class<?> featureClass;
            try {
                featureClass = Class.forName(featureName, true, loader.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw UserError.abort("Feature %s class not found on the classpath. Ensure that the name is correct and that the class is on the classpath.", featureName);
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
        /* Avoid wrapping UserErrors and VMErrors. */
        if (throwable instanceof UserException userError) {
            throw userError;
        }
        if (throwable instanceof HostedError vmError) {
            throw vmError;
        }

        String featureClassName = feature.getClass().getName();
        String throwableClassName = throwable.getClass().getName();
        if (InternalFeature.class.isAssignableFrom(feature.getClass())) {
            throw VMError.shouldNotReachHere("InternalFeature defined by %s unexpectedly failed with a(n) %s".formatted(featureClassName, throwableClassName), throwable);
        }
        throw UserError.abort(throwable, "Feature defined by %s unexpectedly failed with a(n) %s. Please report this problem to the authors of %s.", featureClassName, throwableClassName,
                        featureClassName);
    }
}
