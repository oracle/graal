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
package com.oracle.svm.core.jdk.localization.substitutions;

import java.util.Locale;
import java.util.spi.LocaleServiceProvider;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.jdk.localization.LocalizationSupport;
import com.oracle.svm.core.jdk.localization.OptimizedLocalizationSupport;
import com.oracle.svm.core.jdk.localization.OptimizedLocalizationSupport.AdaptersByClassKey;
import com.oracle.svm.core.jdk.localization.substitutions.modes.OptimizedLocaleMode;
import com.oracle.svm.core.util.VMError;

import sun.util.locale.provider.LocaleProviderAdapter;

@TargetClass(value = sun.util.locale.provider.LocaleProviderAdapter.class, onlyWith = OptimizedLocaleMode.class)
final class Target_sun_util_locale_provider_LocaleProviderAdapter_OptimizedLocaleMode {

    @Substitute
    @SuppressWarnings({"unused"})
    public static LocaleProviderAdapter getAdapter(Class<? extends LocaleServiceProvider> providerClass, Locale locale) {
        OptimizedLocalizationSupport support = ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport();
        for (Locale candidateLocale : support.control.getCandidateLocales("", locale)) {
            LocaleProviderAdapter result = support.adaptersByClass.get(new AdaptersByClassKey(providerClass, candidateLocale));
            if (result != null) {
                return result;
            }
        }
        throw VMError.unsupportedFeature("LocaleProviderAdapter.getAdapter:  providerClass: " + providerClass.getName() + ", locale: " + locale);
    }

    @Substitute
    public static LocaleProviderAdapter forType(LocaleProviderAdapter.Type type) {
        final LocaleProviderAdapter result = ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().adaptersByType.get(type);
        if (result != null) {
            return result;
        }
        throw VMError.unsupportedFeature("LocaleProviderAdapter.forType:  type: " + type.toString());
    }
}
