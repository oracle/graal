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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.spi.LocaleServiceProvider;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.KeepOriginal;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.RecomputeFieldValue.Kind;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.annotate.TargetElement;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;

//Checkstyle: allow reflection

import sun.util.locale.provider.JRELocaleProviderAdapter;
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.LocaleProviderAdapter.Type;
import sun.util.locale.provider.LocaleResources;
import sun.util.locale.provider.LocaleServiceProviderPool;
import sun.util.locale.provider.LocaleServiceProviderPool.LocalizedObjectGetter;

@TargetClass(java.util.Locale.class)
final class Target_java_util_Locale {

    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = DefaultLocaleComputer.class) //
    private static Locale defaultLocale;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = DefaultLocaleComputer.class) //
    private static Locale defaultDisplayLocale;
    @Alias @RecomputeFieldValue(kind = Kind.Custom, declClass = DefaultLocaleComputer.class) //
    private static Locale defaultFormatLocale;

    @Substitute
    private static Object initDefault() {
        throw VMError.unsupportedFeature("The default Locale must be initialized during image generation");
    }

    @Substitute
    private static Object initDefault(Locale.Category category) {
        throw VMError.unsupportedFeature("The default Locale must be initialized during image generation: " + category);
    }
}

final class DefaultLocaleComputer implements RecomputeFieldValue.CustomFieldValueComputer {
    @Override
    public Object compute(MetaAccessProvider metaAccess, ResolvedJavaField original, ResolvedJavaField annotated, Object receiver) {
        return ImageSingletons.lookup(LocalizationFeature.class).imageLocale;
    }
}

@TargetClass(value = sun.util.locale.provider.LocaleProviderAdapter.class)
final class Target_sun_util_locale_provider_LocaleProviderAdapter {

    @Substitute
    @SuppressWarnings({"unused"})
    public static LocaleProviderAdapter getAdapter(Class<? extends LocaleServiceProvider> providerClass, Locale locale) {
        LocaleProviderAdapter result = ImageSingletons.lookup(LocalizationSupport.class).adaptersByClass.get(providerClass);
        if (result != null) {
            return result;
        }
        throw VMError.unsupportedFeature("LocaleProviderAdapter.getAdapter:  providerClass: " + providerClass.getName());
    }

    @Substitute
    public static LocaleProviderAdapter forType(Type type) {
        final LocaleProviderAdapter result = ImageSingletons.lookup(LocalizationSupport.class).adaptersByType.get(type);
        if (result != null) {
            return result;
        }
        throw VMError.unsupportedFeature("LocaleProviderAdapter.forType:  type: " + type.toString());
    }
}

@Substitute
@TargetClass(sun.util.locale.provider.LocaleServiceProviderPool.class)
@SuppressWarnings({"static-method"})
final class Target_sun_util_locale_provider_LocaleServiceProviderPool {

    private final LocaleServiceProvider cachedProvider;

    Target_sun_util_locale_provider_LocaleServiceProviderPool(LocaleServiceProvider cachedProvider) {
        this.cachedProvider = cachedProvider;
    }

    @Substitute
    private static LocaleServiceProviderPool getPool(Class<? extends LocaleServiceProvider> providerClass) {
        LocaleServiceProviderPool result = (LocaleServiceProviderPool) ImageSingletons.lookup(LocalizationSupport.class).providerPools.get(providerClass);
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
    private native <P extends LocaleServiceProvider, S> S getLocalizedObject(LocalizedObjectGetter<P, S> getter, Locale locale, Object... params);

    @KeepOriginal
    private native <P extends LocaleServiceProvider, S> S getLocalizedObject(LocalizedObjectGetter<P, S> getter, Locale locale, String key, Object... params);

    @SuppressWarnings({"unused", "unchecked"})
    @Substitute
    private <P extends LocaleServiceProvider, S> S getLocalizedObjectImpl(LocalizedObjectGetter<P, S> getter, Locale locale, boolean isObjectProvider, String key, Object... params) {
        if (locale == null) {
            throw new NullPointerException();
        }
        return getter.getObject((P) cachedProvider, locale, key, params);
    }

    @KeepOriginal //
    @TargetElement(onlyWith = JDK11OrLater.class) //
    public native <P extends LocaleServiceProvider, S> S getLocalizedObject(LocalizedObjectGetter<P, S> getter,
                    Locale locale,
                    Boolean isObjectProvider,
                    String key,
                    Object... params);

    @KeepOriginal //
    @TargetElement(onlyWith = JDK11To14.class) //
    static native void config(Class<? extends Object> caller, String message);

    /**
     * Since we only put the pre-initialized resource bundles for one locale into the image, it does
     * not make sense to return more than a single Locale here.
     */
    @Substitute
    private static Locale[] getAllAvailableLocales() {
        return new Locale[]{ImageSingletons.lookup(LocalizationFeature.class).imageLocale};
    }

    @Substitute
    private Locale[] getAvailableLocales() {
        return new Locale[]{ImageSingletons.lookup(LocalizationFeature.class).imageLocale};
    }
}

@Delete
@TargetClass(sun.util.locale.provider.AuxLocaleProviderAdapter.class)
final class Target_sun_util_locale_provider_AuxLocaleProviderAdapter {
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
    private ConcurrentMap<?, ?> cache;
    @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ReferenceQueue.class)//
    @Alias//
    private ReferenceQueue<Object> referenceQueue;
}

@TargetClass(JRELocaleProviderAdapter.class)
final class Target_sun_util_locale_provider_JRELocaleProviderAdapter {
    @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    @Alias//
    private ConcurrentMap<String, Set<String>> langtagSets;

    @RecomputeFieldValue(kind = Kind.NewInstance, declClass = ConcurrentHashMap.class)//
    @Alias//
    private ConcurrentMap<Locale, LocaleResources> localeResourcesMap;

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

    @Substitute
    @SuppressWarnings("static-method")
    protected Set<String> createLanguageTagSet(String category) {
        throw VMError.unsupportedFeature("All language tag sets must be created at image build time. Missing category: " + category);
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

}
