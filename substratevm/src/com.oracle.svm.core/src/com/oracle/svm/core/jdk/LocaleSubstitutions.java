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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.spi.LocaleServiceProvider;

import org.graalvm.compiler.serviceprovider.GraalServices;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.util.VMError;

//Checkstyle: allow reflection

import sun.util.locale.provider.JRELocaleProviderAdapter;
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.LocaleProviderAdapter.Type;
import sun.util.locale.provider.LocaleResources;
import sun.util.locale.provider.LocaleServiceProviderPool;
import sun.util.locale.provider.LocaleServiceProviderPool.LocalizedObjectGetter;

@TargetClass(java.util.Locale.class)
final class Target_java_util_Locale {

    static {
        /*
         * Ensure that default locales are initialized, so that we do not have to do it at run time.
         */
        Locale.getDefault();
        for (Locale.Category category : Locale.Category.values()) {
            Locale.getDefault(category);
        }
    }

    @Substitute
    private static Object initDefault() {
        throw VMError.unsupportedFeature("initalization of Locale");
    }

    @Substitute
    private static Object initDefault(Locale.Category category) {
        throw VMError.unsupportedFeature("initalization of Locale with category " + category);
    }
}

@Substitute
@TargetClass(sun.util.locale.provider.LocaleServiceProviderPool.class)
@SuppressWarnings({"unchecked"})
final class Target_sun_util_locale_provider_LocaleServiceProviderPool {

    /*
     * We make our own caches, which are full populated during native image generation, to avoid any
     * dynamic resource loading at run time. This is a conservative handling of locale-specific
     * parts, but good enough for now.
     */
    private static final Map<Class<? extends LocaleServiceProvider>, Object> cachedPools;

    private final LocaleServiceProvider cachedProvider;

    @Platforms(Platform.HOSTED_ONLY.class)
    protected static Class<LocaleServiceProvider>[] spiClasses() {
        /*
         * LocaleServiceProviderPool.spiClasses does not contain all the classes we need, so we list
         * them manually here.
         */
        return (Class<LocaleServiceProvider>[]) new Class<?>[]{
                        java.text.spi.BreakIteratorProvider.class,
                        java.text.spi.CollatorProvider.class,
                        java.text.spi.DateFormatProvider.class,
                        java.text.spi.DateFormatSymbolsProvider.class,
                        java.text.spi.DecimalFormatSymbolsProvider.class,
                        java.text.spi.NumberFormatProvider.class,
                        java.util.spi.CurrencyNameProvider.class,
                        java.util.spi.LocaleNameProvider.class,
                        java.util.spi.TimeZoneNameProvider.class,
                        java.util.spi.CalendarDataProvider.class,
                        java.util.spi.CalendarNameProvider.class};
    }

    static {
        cachedPools = new HashMap<>();
        try {
            for (Class<LocaleServiceProvider> providerClass : spiClasses()) {
                final LocaleProviderAdapter lda = LocaleProviderAdapter.forJRE();
                final LocaleServiceProvider provider = lda.getLocaleServiceProvider(providerClass);
                assert provider != null : "Target_sun_util_locale_provider_LocaleServiceProviderPool: There should be no null LocaleServiceProviders.";
                cachedPools.put(providerClass, new Target_sun_util_locale_provider_LocaleServiceProviderPool(provider));
            }
        } catch (Throwable ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    Target_sun_util_locale_provider_LocaleServiceProviderPool(LocaleServiceProvider cachedProvider) {
        this.cachedProvider = cachedProvider;
    }

    @Substitute
    private static LocaleServiceProviderPool getPool(Class<? extends LocaleServiceProvider> providerClass) {
        LocaleServiceProviderPool result = (LocaleServiceProviderPool) cachedPools.get(providerClass);
        if (result == null) {
            throw VMError.unsupportedFeature("LocaleServiceProviderPool.getPool " + providerClass.getName());
        }
        return result;
    }

    @Substitute
    @SuppressWarnings({"static-method"})
    @TargetElement(onlyWith = JDK8OrEarlier.class)
    private boolean hasProviders() {
        return false;
    }

    @KeepOriginal
    private native <P extends LocaleServiceProvider, S> S getLocalizedObject(LocalizedObjectGetter<P, S> getter, Locale locale, Object... params);

    @KeepOriginal
    private native <P extends LocaleServiceProvider, S> S getLocalizedObject(LocalizedObjectGetter<P, S> getter, Locale locale, String key, Object... params);

    @SuppressWarnings({"unused"})
    @Substitute
    private <P extends LocaleServiceProvider, S> S getLocalizedObjectImpl(LocalizedObjectGetter<P, S> getter, Locale locale, boolean isObjectProvider, String key, Object... params) {
        if (locale == null) {
            throw new NullPointerException();
        }
        return getter.getObject((P) cachedProvider, locale, key, params);
    }

    @KeepOriginal //
    @TargetElement(onlyWith = JDK9OrLater.class) //
    public native <P extends LocaleServiceProvider, S> S getLocalizedObject(LocalizedObjectGetter<P, S> getter,
                    Locale locale,
                    Boolean isObjectProvider,
                    String key,
                    Object... params);
}

@TargetClass(sun.util.locale.provider.LocaleProviderAdapter.class)
final class Target_sun_util_locale_provider_LocaleProviderAdapter {

    @Substitute
    @SuppressWarnings({"unused"})
    public static LocaleProviderAdapter getAdapter(Class<? extends LocaleServiceProvider> providerClass, Locale locale) {
        LocaleProviderAdapter result = Util_sun_util_locale_provider_LocaleProviderAdapter.cachedAdapters.get(providerClass);
        if (result == null) {
            throw VMError.unsupportedFeature("LocaleServiceProviderAdapter.getAdapter " + providerClass.getName());
        }
        return result;
    }

    @Alias //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    private static LocaleProviderAdapter jreLocaleProviderAdapter;

    @Substitute
    public static LocaleProviderAdapter forType(Type type) {
        if (GraalServices.Java8OrEarlier) {
            if (type == Type.JRE) {
                return jreLocaleProviderAdapter;
            } else {
                throw VMError.unsupportedFeature("LocaleProviderAdapter.forType: " + type);
            }
        } else {
            /*
             * TODO: If I only wanted the JRE adapter, I could cache the result of calling
             * `LocaleProviderAdapter.forJRE()` during image building.
             */
            throw VMError.unsupportedFeature("JDK9OrLater: LocaleProviderAdapter.forType: " + type);
        }
    }
}

final class Util_sun_util_locale_provider_LocaleProviderAdapter {

    static final Map<Class<? extends LocaleServiceProvider>, LocaleProviderAdapter> cachedAdapters;

    static {
        cachedAdapters = new HashMap<>();

        try {
            Class<LocaleServiceProvider>[] spiClasses = Target_sun_util_locale_provider_LocaleServiceProviderPool.spiClasses();
            for (Class<LocaleServiceProvider> providerClass : spiClasses) {
                LocaleProviderAdapter adapter = LocaleProviderAdapter.getAdapter(providerClass, Locale.getDefault());
                cachedAdapters.put(providerClass, adapter);
            }

        } catch (Throwable ex) {
            throw VMError.shouldNotReachHere(ex);
        }

    }
}

@Delete
@TargetClass(sun.util.locale.provider.AuxLocaleProviderAdapter.class)
final class Target_sun_util_locale_provider_AuxLocaleProviderAdapter {
}

@TargetClass(java.util.TimeZone.class)
final class Target_java_util_TimeZone {

    @Substitute
    private static TimeZone getDefaultRef() {
        return Util_java_util_TimeZone.defaultZone;
    }

    @Substitute
    private static void setDefault(TimeZone zone) {
        Util_java_util_TimeZone.defaultZone = zone;
    }

    @Substitute
    public static TimeZone getTimeZone(String id) {
        for (TimeZone zone : Util_java_util_TimeZone.zones) {
            if (zone.getID().equals(id)) {
                return zone;
            }
        }
        return Util_java_util_TimeZone.zones.get(0);
    }
}

final class Util_java_util_TimeZone {

    protected static final List<TimeZone> zones;
    protected static TimeZone defaultZone = TimeZone.getDefault();

    static {
        defaultZone = TimeZone.getDefault();
        zones = new ArrayList<>();
        /* The first entry must be GMT, it is returned when no other match found. */
        zones.add(TimeZone.getTimeZone("GMT"));
        zones.add(TimeZone.getTimeZone("UTC"));
        zones.add(defaultZone);
    }
}

@TargetClass(sun.util.locale.provider.TimeZoneNameUtility.class)
final class Target_sun_util_locale_provider_TimeZoneNameUtility {

    @Alias @RecomputeFieldValue(kind = Kind.FromAlias)//
    static ConcurrentHashMap<Locale, SoftReference<String[][]>> cachedZoneData = new ConcurrentHashMap<>();

    @Alias @RecomputeFieldValue(kind = Kind.FromAlias)//
    static Map<String, SoftReference<Map<Locale, String[]>>> cachedDisplayNames = new ConcurrentHashMap<>();
}

@TargetClass(java.text.BreakIterator.class)
final class Target_java_text_BreakIterator {

    @Substitute
    private static BreakIterator getWordInstance(Locale locale) {
        assert locale == Locale.getDefault();
        return (BreakIterator) Util_java_text_BreakIterator.WORD_INSTANCE.clone();
    }

    @Substitute
    private static BreakIterator getLineInstance(Locale locale) {
        assert locale == Locale.getDefault();
        return (BreakIterator) Util_java_text_BreakIterator.LINE_INSTANCE.clone();
    }

    @Substitute
    private static BreakIterator getCharacterInstance(Locale locale) {
        assert locale == Locale.getDefault();
        return (BreakIterator) Util_java_text_BreakIterator.CHARACTER_INSTANCE.clone();
    }

    @Substitute
    private static BreakIterator getSentenceInstance(Locale locale) {
        assert locale == Locale.getDefault();
        return (BreakIterator) Util_java_text_BreakIterator.SENTENCE_INSTANCE.clone();
    }
}

@TargetClass(LocaleResources.class)
final class Target_sun_util_locale_provider_LocaleResources {
    @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    @Alias//
    private ConcurrentMap<?, ?> cache = new ConcurrentHashMap<>();
    @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ReferenceQueue.class)//
    @Alias//
    private ReferenceQueue<Object> referenceQueue = new ReferenceQueue<>();
}

@TargetClass(JRELocaleProviderAdapter.class)
final class Target_sun_util_locale_provider_JRELocaleProviderAdapter {
    @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    @Alias//
    private final ConcurrentMap<String, Set<String>> langtagSets = new ConcurrentHashMap<>();

    @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    @Alias//
    private final ConcurrentMap<Locale, LocaleResources> localeResourcesMap = new ConcurrentHashMap<>();

    @Alias //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    static Boolean isNonENSupported;

    @Substitute //
    @TargetElement(onlyWith = JDK8OrEarlier.class) //
    private static boolean isNonENLangSupported() {
        /*
         * The original implementation performs lazily initialization that looks at the file system
         * (a certain .jar file being present). That cannot work in a native image, and even worse
         * it makes file access methods reachable in very basic images.
         */
        VMError.guarantee(isNonENSupported != null, "isNonENSupported must be initialized during image generation");
        return isNonENSupported;
    }
}

final class Util_java_text_BreakIterator {
    static final BreakIterator WORD_INSTANCE = BreakIterator.getWordInstance();
    static final BreakIterator LINE_INSTANCE = BreakIterator.getLineInstance();
    static final BreakIterator CHARACTER_INSTANCE = BreakIterator.getCharacterInstance();
    static final BreakIterator SENTENCE_INSTANCE = BreakIterator.getSentenceInstance();
}

/** Dummy class to have a class with the file's name. */
public final class LocaleSubstitutions {

    public static void registerTimeZone(TimeZone zone) {
        Util_java_util_TimeZone.zones.add(zone);
    }
}
