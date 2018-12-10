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
package com.oracle.svm.core.jdk;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.spi.LocaleServiceProvider;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

// Checkstyle: stop
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.LocaleProviderAdapter.Type;
// Checkstyle: resume

/** The JDK-8 or earlier substitution for sun.util.locale.provider.LocaleProviderAdapter. */
@TargetClass(value = sun.util.locale.provider.LocaleProviderAdapter.class, onlyWith = JDK8OrEarlier.class)
public final class Target_sun_util_locale_provider_LocaleProviderAdapter_JDK8OrEarlier {

    @Substitute
    @SuppressWarnings({"unused"})
    public static LocaleProviderAdapter getAdapter(Class<? extends LocaleServiceProvider> providerClass, Locale locale) {
        LocaleProviderAdapter result = Util_sun_util_locale_provider_LocaleProviderAdapter.cachedAdaptersByClass.get(providerClass);
        if (result == null) {
            throw VMError.unsupportedFeature("LocaleServiceProviderAdapter.getAdapter " + providerClass.getName());
        }
        return result;
    }

    @Alias //
    private static LocaleProviderAdapter jreLocaleProviderAdapter;

    @Substitute
    public static LocaleProviderAdapter forType(Type type) {
        if (type == Type.JRE) {
            return jreLocaleProviderAdapter;
        } else {
            throw VMError.unsupportedFeature("LocaleProviderAdapter.forType: " + type);
        }
    }
}

final class Util_sun_util_locale_provider_LocaleProviderAdapter {

    static final Map<Class<? extends LocaleServiceProvider>, LocaleProviderAdapter> cachedAdaptersByClass = new HashMap<>();
    static {
        try {
            Class<LocaleServiceProvider>[] spiClasses = Target_sun_util_locale_provider_LocaleServiceProviderPool.spiClasses();
            for (Class<LocaleServiceProvider> providerClass : spiClasses) {
                LocaleProviderAdapter adapter = LocaleProviderAdapter.getAdapter(providerClass, Locale.getDefault());
                cachedAdaptersByClass.put(providerClass, adapter);
            }
        } catch (Throwable ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}
