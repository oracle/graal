/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.util.VMError;

// Checkstyle: stop
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.LocaleProviderAdapter.Type;
// Checkstyle: resume

@TargetClass(value = sun.util.locale.provider.LocaleProviderAdapter.class, onlyWith = JDK11OrLater.class)
public final class Target_sun_util_locale_provider_LocaleProviderAdapter_JDK11OrLater {

    @Substitute
    @SuppressWarnings({"unused"})
    public static LocaleProviderAdapter getAdapter(Class<? extends LocaleServiceProvider> providerClass, Locale locale) {
        LocaleProviderAdapter result = Util_sun_util_locale_provider_LocaleProviderAdapter.getByClass(providerClass);
        if (result != null) {
            return result;
        }
        throw VMError.unsupportedFeature("LocaleServiceProviderAdapter.getAdapter:  providerClass: " + providerClass.getName());
    }

    @Substitute
    public static LocaleProviderAdapter forType(Type type) {
        final LocaleProviderAdapter result = Util_sun_util_locale_provider_LocaleProviderAdapter.getByType(type);
        if (result != null) {
            return result;
        }
        throw VMError.unsupportedFeature("JDK11OrLater: LocaleProviderAdapter.forType:  type: " + type.toString());
    }
}

final class Util_sun_util_locale_provider_LocaleProviderAdapter {

    /** A cache of adapters indexed by class. */
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

    static LocaleProviderAdapter getByClass(Class<? extends LocaleServiceProvider> klass) {
        return cachedAdaptersByClass.get(klass);
    }

    /** A cache of adapters indexed by type. */
    static final Map<LocaleProviderAdapter.Type, LocaleProviderAdapter> cachedAdaptersByType = new HashMap<>();
    static {
        try {
            /* A list of types that seem to work. But is this list sufficient? */
            LocaleProviderAdapter.Type[] acceptableTypes = new LocaleProviderAdapter.Type[]{
                            LocaleProviderAdapter.Type.JRE,
                            LocaleProviderAdapter.Type.CLDR
            };
            for (LocaleProviderAdapter.Type type : acceptableTypes) {
                final LocaleProviderAdapter adapter = LocaleProviderAdapter.forType(type);
                cachedAdaptersByType.put(type, adapter);
            }
        } catch (Throwable throwable) {
            throw VMError.shouldNotReachHere(throwable);
        }
    }

    static LocaleProviderAdapter getByType(LocaleProviderAdapter.Type type) {
        return cachedAdaptersByType.get(type);
    }
}
