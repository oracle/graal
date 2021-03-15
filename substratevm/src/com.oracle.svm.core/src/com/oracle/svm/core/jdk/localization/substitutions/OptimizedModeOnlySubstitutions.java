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

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK11OrLater;
import com.oracle.svm.core.jdk.JDK11To14;
import com.oracle.svm.core.jdk.JDK8OrEarlier;
import com.oracle.svm.core.jdk.localization.LocalizationSupport;
import com.oracle.svm.core.jdk.localization.OptimizedLocalizationSupport;
import com.oracle.svm.core.jdk.localization.substitutions.modes.OptimizedLocaleMode;
import com.oracle.svm.core.util.VMError;
import org.graalvm.collections.Pair;
import org.graalvm.nativeimage.ImageSingletons;

// Checkstyle: stop
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.LocaleServiceProviderPool;
// Checkstyle: resume

import java.util.Locale;
import java.util.spi.LocaleServiceProvider;

public class OptimizedModeOnlySubstitutions {
    @TargetClass(value = sun.util.locale.provider.LocaleProviderAdapter.class, onlyWith = OptimizedLocaleMode.class)
    static final class Target_sun_util_locale_provider_LocaleProviderAdapter {

        @Substitute
        @SuppressWarnings({"unused"})
        public static LocaleProviderAdapter getAdapter(Class<? extends LocaleServiceProvider> providerClass, Locale locale) {
            OptimizedLocalizationSupport support = ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport();
            for (Locale candidateLocale : support.control.getCandidateLocales("", locale)) {
                LocaleProviderAdapter result = support.adaptersByClass.get(Pair.create(providerClass, candidateLocale));
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

    @Substitute
    @TargetClass(value = sun.util.locale.provider.LocaleServiceProviderPool.class, onlyWith = OptimizedLocaleMode.class)
    @SuppressWarnings({"static-method"})
    public static final class Target_sun_util_locale_provider_LocaleServiceProviderPool {

        private final LocaleServiceProvider cachedProvider;

        public Target_sun_util_locale_provider_LocaleServiceProviderPool(LocaleServiceProvider cachedProvider) {
            this.cachedProvider = cachedProvider;
        }

        @Substitute
        private static LocaleServiceProviderPool getPool(Class<? extends LocaleServiceProvider> providerClass) {
            LocaleServiceProviderPool result = (LocaleServiceProviderPool) ImageSingletons.lookup(LocalizationSupport.class).asOptimizedSupport().providerPools.get(providerClass);
            if (result == null) {
                throw VMError.unsupportedFeature("LocaleServiceProviderPool.getPool " + providerClass.getName());
            }
            return result;
        }

        @Substitute
        @TargetElement(onlyWith = JDK8OrEarlier.class)
        private boolean hasProviders() {
            return false;
        }

        @KeepOriginal
        private native <P extends LocaleServiceProvider, S> S getLocalizedObject(LocaleServiceProviderPool.LocalizedObjectGetter<P, S> getter, Locale locale, Object... params);

        @KeepOriginal
        private native <P extends LocaleServiceProvider, S> S getLocalizedObject(LocaleServiceProviderPool.LocalizedObjectGetter<P, S> getter, Locale locale, String key, Object... params);

        @SuppressWarnings({"unused", "unchecked"})
        @Substitute
        private <P extends LocaleServiceProvider, S> S getLocalizedObjectImpl(LocaleServiceProviderPool.LocalizedObjectGetter<P, S> getter, Locale locale, boolean isObjectProvider, String key,
                        Object... params) {
            if (locale == null) {
                throw new NullPointerException();
            }
            return getter.getObject((P) cachedProvider, locale, key, params);
        }

        @KeepOriginal //
        @TargetElement(onlyWith = JDK11OrLater.class) //
        public native <P extends LocaleServiceProvider, S> S getLocalizedObject(LocaleServiceProviderPool.LocalizedObjectGetter<P, S> getter,
                        Locale locale,
                        Boolean isObjectProvider,
                        String key,
                        Object... params);

        @KeepOriginal //
        @TargetElement(onlyWith = JDK11To14.class) //
        static native void config(Class<? extends Object> caller, String message);

        @Substitute
        private static Locale[] getAllAvailableLocales() {
            return ImageSingletons.lookup(LocalizationSupport.class).allLocales;
        }

        @Substitute
        private Locale[] getAvailableLocales() {
            return ImageSingletons.lookup(LocalizationSupport.class).allLocales;
        }
    }

    @Delete
    @TargetClass(value = sun.util.locale.provider.AuxLocaleProviderAdapter.class, onlyWith = OptimizedLocaleMode.class)
    static final class Target_sun_util_locale_provider_AuxLocaleProviderAdapter {
    }
}
