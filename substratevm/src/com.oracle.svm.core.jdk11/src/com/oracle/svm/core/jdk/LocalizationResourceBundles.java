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

//Checkstyle: stop
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.TimeZone;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ReflectionUtil;

import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.resources.LocaleData;
// Checkstyle: resume

@Platforms(Platform.HOSTED_ONLY.class)
public class LocalizationResourceBundles extends LocalizationSupport {

    static void initialize(LocalizationSupport localizationSupport) {
        /* Allow runtime access to {@link jdk.internal.misc} classes. */
        ModuleUtils.addOpensToAllUnnamed("java.base", "jdk.internal.misc");
        final LocaleProviderAdapter.Type jreLocaleProviderAdapterType = LocaleProviderAdapter.Type.JRE;
        final LocaleData jreLocaleData = new LocaleData(jreLocaleProviderAdapterType);
        final Locale defaultLocale = Locale.getDefault();
        /* CalendarData. */
        final String calendarDataKey = jreLocaleProviderAdapterType.getUtilResourcesPackage() + ".CalendarData";
        final ResourceBundle calendarDataBundle = jreLocaleData.getCalendarData(defaultLocale);
        localizationSupport.addBundleToCache(calendarDataKey, calendarDataBundle);
        /* CurrencyNames. */
        final String currencyNamesKey = jreLocaleProviderAdapterType.getUtilResourcesPackage() + ".CurrencyNames";
        final ResourceBundle currencyNamesBundle = jreLocaleData.getCurrencyNames(defaultLocale);
        localizationSupport.addBundleToCache(currencyNamesKey, currencyNamesBundle);
        /* LocaleNames. */
        final String localeNamesKey = jreLocaleProviderAdapterType.getUtilResourcesPackage() + ".LocaleNames";
        final ResourceBundle localeNamesBundle = jreLocaleData.getLocaleNames(defaultLocale);
        localizationSupport.addBundleToCache(localeNamesKey, localeNamesBundle);
        /* TimeZoneNames. */
        final String timeZoneNamesKey = jreLocaleProviderAdapterType.getUtilResourcesPackage() + ".TimeZoneNames";
        final ResourceBundle timeZoneNamesBundle = jreLocaleData.getTimeZoneNames(defaultLocale);
        localizationSupport.addBundleToCache(timeZoneNamesKey, timeZoneNamesBundle);
        TimeZone.getDefault().getDisplayName();

        /* BreakIteratorInfo. */
        final String breakIteratorInfoKey = jreLocaleProviderAdapterType.getTextResourcesPackage() + ".BreakIteratorInfo";
        final ResourceBundle breakIteratorInfoBundle = jreLocaleData.getBreakIteratorInfo(defaultLocale);
        localizationSupport.addBundleToCache(breakIteratorInfoKey, breakIteratorInfoBundle);
        /* BreakIteratorResources. */
        final String breakIteratorResourcesKey = jreLocaleProviderAdapterType.getTextResourcesPackage() + ".BreakIteratorResources";
        final ResourceBundle breakIteratorResourcesBundle = jreLocaleData.getBreakIteratorResources(defaultLocale);
        localizationSupport.addBundleToCache(breakIteratorResourcesKey, breakIteratorResourcesBundle);
        /* CollationData. */
        final String collationDataKey = jreLocaleProviderAdapterType.getTextResourcesPackage() + ".CollationData";
        final ResourceBundle collationDataBundle = jreLocaleData.getCollationData(defaultLocale);
        localizationSupport.addBundleToCache(collationDataKey, collationDataBundle);
        /* FormatData. */
        /*
         * jreLocaleData.getDateFormatData and jreLocaleData.getNumberFormatData use the same
         * resource bundle, so I only need to get one of them.
         */
        final String formatDataKey = jreLocaleProviderAdapterType.getTextResourcesPackage() + ".FormatData";
        final ResourceBundle formatDataBundle = jreLocaleData.getDateFormatData(defaultLocale);
        localizationSupport.addBundleToCache(formatDataKey, formatDataBundle);
        /* The CLDR FormatData uses a different LocaleProviderAdapterType. */
        final LocaleProviderAdapter.Type cldrLocaleProviderAdapterType = LocaleProviderAdapter.Type.CLDR;
        final LocaleData cldrLocaleData = new LocaleData(cldrLocaleProviderAdapterType);
        final String cldrFormatDataKey = cldrLocaleProviderAdapterType.getTextResourcesPackage() + ".FormatData";
        final ResourceBundle cldrFormatDataBundle = cldrLocaleData.getDateFormatData(defaultLocale);
        localizationSupport.addBundleToCache(cldrFormatDataKey, cldrFormatDataBundle);
        /*
         * sun.util.logging.resources.logging.
         *
         * Loaded as a string name because `mx` can not deal with a class with a lower-case name.
         * Also, using it as a named class generates an "internal proprietary API" warning that can
         * not be suppressed.
         */
        final String sunUtilLoggingResourcesLoggingKey = "sun.util.logging.resources.logging";
        final ResourceBundle sunUtilLoggingResourcesLoggingBundle = getBundleByName(sunUtilLoggingResourcesLoggingKey);
        localizationSupport.addBundleToCache(sunUtilLoggingResourcesLoggingKey, sunUtilLoggingResourcesLoggingBundle);
    }

    private static final Method NEW_RESOURCE_BUNDLE;

    static {
        try {
            // Checkstyle: stop
            NEW_RESOURCE_BUNDLE = ReflectionUtil.lookupMethod(Class.forName("java.util.ResourceBundle$ResourceBundleProviderHelper"), "newResourceBundle", Class.class);
            // Checkstyle: resume
        } catch (ClassNotFoundException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    private static ResourceBundle getBundleByName(String bundleName) {
        Class<? extends ResourceBundle> bundleClass = null;
        try {
            // Checkstyle: stop
            final Class<?> fromName = Class.forName(bundleName);
            // Checkstyle: resume
            bundleClass = fromName.asSubclass(ResourceBundle.class);
        } catch (ClassNotFoundException cnfe) {
            throw VMError.shouldNotReachHere("Could not find ResourceBundle by name:", cnfe);
        } catch (ClassCastException cce) {
            throw VMError.shouldNotReachHere("Class is not a ResourceBundle:", cce);
        }
        try {
            return (ResourceBundle) NEW_RESOURCE_BUNDLE.invoke(null, bundleClass);
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

}
