/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Function;
import java.util.spi.LocaleServiceProvider;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.UserError;
import com.oracle.svm.util.LocaleUtil;
import com.oracle.svm.util.ReflectionUtil;

import sun.util.locale.provider.LocaleProviderAdapter;

public class OptimizedLocalizationSupport extends LocalizationSupport {

    public record AdaptersByClassKey(Class<? extends LocaleServiceProvider> providerClass, Locale candidateLocale) {
    }

    public final Map<AdaptersByClassKey, LocaleProviderAdapter> adaptersByClass = new HashMap<>();
    public final Map<LocaleProviderAdapter.Type, LocaleProviderAdapter> adaptersByType = new HashMap<>();
    public final Map<Class<? extends LocaleServiceProvider>, Object> providerPools = new HashMap<>();

    private record BundleCacheKey(String bundleName, Locale locale) {
    }

    final Map<BundleCacheKey, ResourceBundle> resourceBundles = new HashMap<>();

    public OptimizedLocalizationSupport(Set<Locale> locales, Charset defaultCharset) {
        super(locales, defaultCharset);
    }

    @Override
    public boolean optimizedMode() {
        return true;
    }

    /**
     * Get cached resource bundle.
     *
     * @param locale this parameter is not currently used.
     */
    public ResourceBundle getCached(String baseName, Locale locale) throws MissingResourceException {
        /*- Try out the whole candidate chain as JVM does */
        for (Locale candidateLocale : control.getCandidateLocales(baseName, locale)) {
            ResourceBundle result = resourceBundles.get(new BundleCacheKey(baseName, candidateLocale));
            if (result != null) {
                return result;
            }
        }
        String errorMessage = "Resource bundle not found " + baseName + ", locale " + locale + ". " +
                        "Register the resource bundle using the option -H:IncludeResourceBundles=" + baseName + ".";
        throw new MissingResourceException(errorMessage, this.getClass().getName(), baseName);

    }

    private final Field bundleNameField = ReflectionUtil.lookupField(ResourceBundle.class, "name");
    private final Field bundleLocaleField = ReflectionUtil.lookupField(ResourceBundle.class, "locale");

    @Override
    public void prepareClassResourceBundle(String basename, Class<?> bundleClass) {
        try {
            ResourceBundle bundle = ((ResourceBundle) ReflectionUtil.newInstance(bundleClass));
            Locale locale = extractLocale(bundleClass);
            /*- Set the basename and locale to be consistent with JVM lookup process */
            bundleNameField.set(bundle, basename);
            bundleLocaleField.set(bundle, locale);

            // override in this class does not use findModule
            prepareBundle(basename, bundle, null, locale, false);
        } catch (ReflectionUtil.ReflectionUtilError | ReflectiveOperationException e) {
            throw UserError.abort(e, "Failed to instantiated bundle from class %s, reason %s", bundleClass, e.getCause().getMessage());
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public void prepareBundle(String bundleName, ResourceBundle bundle, Function<String, Optional<Module>> findModule, Locale locale, boolean jdkBundle) {
        bundle.keySet();
        this.resourceBundles.put(new BundleCacheKey(bundleName, locale), bundle);
    }

    private static Locale extractLocale(Class<?> bundleClass) {
        String name = bundleClass.getName();
        int split = name.lastIndexOf('_');
        if (split == -1) {
            return Locale.ROOT;
        }
        return LocaleUtil.parseLocaleFromTag(name.substring(split + 1));
    }

    @Override
    public boolean shouldSubstituteLoadLookup(String className) {
        /*- All bundles are stored in the image heap as objects, no need to keep the content around */
        return true;
    }
}
