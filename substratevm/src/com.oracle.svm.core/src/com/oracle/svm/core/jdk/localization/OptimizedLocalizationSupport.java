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

import com.oracle.svm.core.option.SubstrateOptionsParser;
import org.graalvm.collections.Pair;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.spi.LocaleServiceProvider;

//Checkstyle: stop
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import sun.util.locale.provider.LocaleProviderAdapter;
//Checkstyle: resume

public class OptimizedLocalizationSupport extends LocalizationSupport {
    public final Map<Pair<Class<? extends LocaleServiceProvider>, Locale>, LocaleProviderAdapter> adaptersByClass = new HashMap<>();
    public final Map<LocaleProviderAdapter.Type, LocaleProviderAdapter> adaptersByType = new HashMap<>();
    public final Map<Class<? extends LocaleServiceProvider>, Object> providerPools = new HashMap<>();

    final Map<Pair<String, Locale>, ResourceBundle> resourceBundles = new HashMap<>();

    private final String includeResourceBundlesOption = SubstrateOptionsParser.commandArgument(LocalizationFeature.Options.IncludeResourceBundles, "");

    public OptimizedLocalizationSupport(Locale defaultLocale, Set<Locale> locales) {
        super(defaultLocale, locales);
    }

    /**
     * Get cached resource bundle.
     *
     * @param locale this parameter is not currently used.
     */
    public ResourceBundle getCached(String baseName, Locale locale) throws MissingResourceException {
        /*- Try out the whole candidate chain as JVM does */
        for (Locale candidateLocale : control.getCandidateLocales(baseName, locale)) {
            ResourceBundle result = resourceBundles.get(Pair.create(baseName, candidateLocale));
            if (result != null) {
                return result;
            }
        }
        String errorMessage = "Resource bundle not found " + baseName + ", locale " + locale + ". " +
                        "Register the resource bundle using the option " + includeResourceBundlesOption + baseName + ".";
        throw new MissingResourceException(errorMessage, this.getClass().getName(), baseName);

    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public void prepareBundle(String bundleName, ResourceBundle bundle, Locale locale) {
        bundle.keySet();
        this.resourceBundles.put(Pair.create(bundleName, locale), bundle);
    }

    @Override
    public boolean shouldSubstituteLoadLookup(String className) {
        /*- All bundles are stored in the image heap as objects, no need to keep the content around */
        return true;
    }
}
