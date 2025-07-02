/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk.localization;

import static com.oracle.svm.util.StringUtil.toDotSeparated;
import static com.oracle.svm.util.StringUtil.toSlashSeparated;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.nativeimage.impl.RuntimeReflectionSupport;
import org.graalvm.nativeimage.impl.RuntimeResourceSupport;

import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.configure.RuntimeConditionSet;
import com.oracle.svm.core.jdk.Resources;
import com.oracle.svm.core.metadata.MetadataTracer;
import com.oracle.svm.core.util.ImageHeapMap;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import jdk.graal.compiler.debug.GraalError;
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.ResourceBundleBasedAdapter;
import sun.util.resources.Bundles;

/**
 * Holder for localization information that is computed during image generation and used at run
 * time.
 *
 * For more details, see LocalizationFeature
 */
public class LocalizationSupport {

    public final Map<String, Charset> charsets = new HashMap<>();

    public final Locale[] allLocales;

    public final Set<String> supportedLanguageTags;

    public final ResourceBundle.Control control = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_DEFAULT);

    private final Bundles.Strategy strategy = getLocaleDataStrategy();

    public final Charset defaultCharset;

    private final EconomicMap<String, RuntimeConditionSet> registeredBundles = ImageHeapMap.create("registeredBundles");

    public LocalizationSupport(Set<Locale> locales, Charset defaultCharset) {
        this.allLocales = locales.toArray(new Locale[0]);
        this.defaultCharset = defaultCharset;
        this.supportedLanguageTags = locales.stream().map(Locale::toString).collect(Collectors.toSet());
    }

    public boolean optimizedMode() {
        return false;
    }

    public boolean jvmMode() {
        return !optimizedMode();
    }

    public boolean substituteLoadLookup() {
        return false;
    }

    public OptimizedLocalizationSupport asOptimizedSupport() {
        GraalError.guarantee(optimizedMode(), "Optimized support only available in optimized localization mode.");
        return ((OptimizedLocalizationSupport) this);
    }

    public Map<String, Object> getBundleContentOf(Object bundle) {
        throw VMError.unsupportedFeature("Resource bundle lookup must be loaded during native image generation: " + bundle.getClass());
    }

    private static Bundles.Strategy getLocaleDataStrategy() {
        try {
            Class<?> localeDataStrategy = ReflectionUtil.lookupClass(false, "sun.util.resources.LocaleData$LocaleDataStrategy");
            Field strategyInstance = ReflectionUtil.lookupField(localeDataStrategy, "INSTANCE");
            return (Bundles.Strategy) strategyInstance.get(null);
        } catch (IllegalAccessException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void prepareBundle(String bundleName, ResourceBundle bundle, Function<String, Optional<Module>> findModule, Locale locale, boolean jdkBundle) {
        /*
         * Class-based bundle lookup happens on every query, but we don't need to register the
         * constructor for a property resource bundle since the class lookup will fail.
         */
        registerRequiredReflectionAndResourcesForBundle(bundleName, Set.of(locale), jdkBundle);
        if (!(bundle instanceof PropertyResourceBundle)) {
            registerNullaryConstructor(bundle.getClass());
        }

        /* Property-based bundle lookup happens only if class-based lookup fails */
        if (bundle instanceof PropertyResourceBundle) {
            String[] bundleNameWithModule = SubstrateUtil.split(bundleName, ":", 2);
            String resourceName;
            String origin = "Added for PropertyResourceBundle: " + bundleName;
            if (bundleNameWithModule.length < 2) {
                resourceName = toSlashSeparated(control.toBundleName(bundleName, locale)).concat(".properties");

                Map<String, Set<Module>> packageToModules = ImageSingletons.lookup(ClassLoaderSupport.class).getPackageToModules();
                Set<Module> modules = packageToModules.getOrDefault(packageName(bundleName), Collections.emptySet());

                for (Module m : modules) {
                    ImageSingletons.lookup(RuntimeResourceSupport.class).addResource(m, resourceName, origin);
                }

                if (modules.isEmpty()) {
                    ImageSingletons.lookup(RuntimeResourceSupport.class).addResource(null, resourceName, origin);
                }
            } else {
                if (findModule != null) {
                    resourceName = toSlashSeparated(control.toBundleName(bundleNameWithModule[1], locale)).concat(".properties");
                    Optional<Module> module = findModule.apply(bundleNameWithModule[0]);
                    String finalResourceName = resourceName;
                    module.ifPresent(m -> ImageSingletons.lookup(RuntimeResourceSupport.class).addResource(m, finalResourceName, origin));
                }
            }
        }
        onBundlePrepared(bundle);
    }

    private static String packageName(String bundleName) {
        String uniformBundleName = toDotSeparated(bundleName);
        int classSep = uniformBundleName.lastIndexOf('.');
        if (classSep == -1) {
            return ""; /* unnamed package */
        }
        return uniformBundleName.substring(0, classSep);
    }

    public void registerRequiredReflectionAndResourcesForBundle(String baseName, Collection<Locale> wantedLocales, boolean jdkBundle) {
        if (!jdkBundle) {
            int i = baseName.lastIndexOf('.');
            if (i > 0) {
                String name = baseName.substring(i + 1) + "Provider";
                String providerName = baseName.substring(0, i) + ".spi." + name;
                ImageSingletons.lookup(RuntimeReflectionSupport.class).registerClassLookup(ConfigurationCondition.alwaysTrue(), providerName);
            }
        }

        for (Locale locale : wantedLocales) {
            registerRequiredReflectionAndResourcesForBundleAndLocale(baseName, locale, jdkBundle);
        }
    }

    public void registerRequiredReflectionAndResourcesForBundleAndLocale(String baseName, Locale baseLocale, boolean jdkBundle) {
        /*
         * Bundles in the sun.(text|util).resources.cldr packages are loaded with an alternative
         * strategy which tries parent aliases defined in CLDRBaseLocaleDataMetaInfo.parentLocales.
         */
        List<Locale> candidateLocales = jdkBundle
                        ? getJDKBundleCandidateLocales(baseName, baseLocale)
                        : control.getCandidateLocales(baseName, baseLocale);

        for (Locale locale : candidateLocales) {
            String bundleWithLocale = jdkBundle ? strategy.toBundleName(baseName, locale) : control.toBundleName(baseName, locale);
            RuntimeReflection.registerClassLookup(bundleWithLocale);
            Class<?> bundleClass = ReflectionUtil.lookupClass(true, bundleWithLocale);
            if (bundleClass != null) {
                registerNullaryConstructor(bundleClass);
            }
            Resources.currentLayer().registerNegativeQuery(bundleWithLocale.replace('.', '/') + ".properties");

            if (jdkBundle) {
                String otherBundleName = Bundles.toOtherBundleName(baseName, bundleWithLocale, locale);
                if (!otherBundleName.equals(bundleWithLocale)) {
                    RuntimeReflection.registerClassLookup(otherBundleName);
                }
            }
        }
    }

    private static List<Locale> getJDKBundleCandidateLocales(String baseName, Locale baseLocale) {
        /*
         * LocaleDataStrategy.getCandidateLocale does some filtering of locales it knows do not have
         * a bundle for the requested base name. We still want to see those locales to be able to
         * register negative queries for them.
         */
        LocaleProviderAdapter.Type adapterType = baseName.contains(".cldr") ? LocaleProviderAdapter.Type.CLDR : LocaleProviderAdapter.Type.JRE;
        ResourceBundleBasedAdapter adapter = ((ResourceBundleBasedAdapter) LocaleProviderAdapter.forType(adapterType));
        return adapter.getCandidateLocales(baseName, baseLocale);
    }

    /**
     * Template method for subclasses to perform additional tasks.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    protected void onBundlePrepared(@SuppressWarnings("unused") ResourceBundle bundle) {

    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @SuppressWarnings("unused")
    protected void onClassBundlePrepared(Class<?> bundleClass) {

    }

    @SuppressWarnings("unused")
    public boolean shouldSubstituteLoadLookup(String className) {
        /*- By default, keep the original code */
        return false;
    }

    @SuppressWarnings("unused")
    public void prepareNonCompliant(Class<?> clazz) throws ReflectiveOperationException {
        /*- By default, there is nothing to do */
    }

    @SuppressWarnings("unused")
    public boolean isNotIncluded(String bundleName) {
        return false;
    }

    public void prepareClassResourceBundle(@SuppressWarnings("unused") String basename, Class<?> bundleClass) {
        registerNullaryConstructor(bundleClass);
        onClassBundlePrepared(bundleClass);
    }

    /**
     * Bundle lookup code tries to reflectively access the default constructor of candidate bundle
     * classes, and then tries to invoke them if they exist. We therefore need to register the
     * default constructor as invoked if it exists, and as queried if it doesn't, which we know will
     * result in a negative query.
     */
    private static void registerNullaryConstructor(Class<?> bundleClass) {
        RuntimeReflection.register(bundleClass);
        Constructor<?> nullaryConstructor;
        try {
            nullaryConstructor = bundleClass.getDeclaredConstructor();
        } catch (NoSuchMethodException e) {
            RuntimeReflection.registerConstructorLookup(bundleClass);
            return;
        }
        RuntimeReflection.register(nullaryConstructor);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void registerBundleLookup(ConfigurationCondition condition, String baseName) {
        RuntimeConditionSet conditionSet = RuntimeConditionSet.emptySet();
        var registered = registeredBundles.putIfAbsent(baseName, conditionSet);
        (registered == null ? conditionSet : registered).addCondition(condition);
    }

    public boolean isRegisteredBundleLookup(String baseName, Locale locale, Object controlOrStrategy) {
        if (baseName == null || locale == null || controlOrStrategy == null) {
            /* Those cases will throw a NullPointerException before any lookup */
            return true;
        }
        if (MetadataTracer.enabled()) {
            MetadataTracer.singleton().traceResourceBundle(baseName);
        }
        if (registeredBundles.containsKey(baseName)) {
            return registeredBundles.get(baseName).satisfied();
        }
        return false;
    }
}
