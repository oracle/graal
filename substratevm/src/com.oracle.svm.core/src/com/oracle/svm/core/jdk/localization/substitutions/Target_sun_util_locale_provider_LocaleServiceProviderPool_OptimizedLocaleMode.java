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

import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.jdk.JDK11OrEarlier;
import com.oracle.svm.core.jdk.localization.LocalizationSupport;
import com.oracle.svm.core.jdk.localization.substitutions.modes.OptimizedLocaleMode;
import com.oracle.svm.core.util.VMError;

import sun.util.locale.provider.LocaleServiceProviderPool;

@Substitute
@TargetClass(value = sun.util.locale.provider.LocaleServiceProviderPool.class, onlyWith = OptimizedLocaleMode.class)
@SuppressWarnings({"static-method"})
public final class Target_sun_util_locale_provider_LocaleServiceProviderPool_OptimizedLocaleMode {

    private final LocaleServiceProvider cachedProvider;

    public Target_sun_util_locale_provider_LocaleServiceProviderPool_OptimizedLocaleMode(LocaleServiceProvider cachedProvider) {
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
    public native <P extends LocaleServiceProvider, S> S getLocalizedObject(LocaleServiceProviderPool.LocalizedObjectGetter<P, S> getter,
                    Locale locale,
                    Boolean isObjectProvider,
                    String key,
                    Object... params);

    @KeepOriginal //
    @TargetElement(onlyWith = JDK11OrEarlier.class) //
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
