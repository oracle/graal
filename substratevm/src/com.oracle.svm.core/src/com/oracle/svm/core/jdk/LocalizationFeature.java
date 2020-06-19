/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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

// Checkstyle: stop

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.text.spi.BreakIteratorProvider;
import java.text.spi.CollatorProvider;
import java.text.spi.DateFormatProvider;
import java.text.spi.DateFormatSymbolsProvider;
import java.text.spi.DecimalFormatSymbolsProvider;
import java.text.spi.NumberFormatProvider;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.spi.CalendarDataProvider;
import java.util.spi.CalendarNameProvider;
import java.util.spi.CurrencyNameProvider;
import java.util.spi.LocaleNameProvider;
import java.util.spi.LocaleServiceProvider;
import java.util.spi.TimeZoneNameProvider;

import com.oracle.svm.core.util.UserError;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.util.ModuleSupport;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.ResourceBundleBasedAdapter;
import sun.util.resources.LocaleData;

// Checkstyle: resume

public abstract class LocalizationFeature implements Feature {

    /**
     * The Locale that the native image is built for. Currently, switching the Locale at run time is
     * not supported because the resource bundles are only included for one Locale. We use the
     * Locale that is set for the image generator.
     */
    protected final Locale imageLocale = Locale.getDefault();

    protected LocalizationSupport support;

    public static class Options {
        @Option(help = "Comma separated list of bundles to be included into the image.", type = OptionType.User)//
        public static final HostedOptionKey<String[]> IncludeResourceBundles = new HostedOptionKey<>(null);

        @Option(help = "Make all hosted charsets available at run time")//
        public static final HostedOptionKey<Boolean> AddAllCharsets = new HostedOptionKey<>(false);
    }

    /**
     * Many subclasses of {@link Charset} initialize encoding and decoding tables lazily. They all
     * follow the same pattern: the methods "initc2b" and/or "initb2c" perform the initialization,
     * and then set a field "c2bInitialized" or "b2cInitialized" to true. We run the initialization
     * eagerly by creating an encoder and decoder during image generation in
     * {@link LocalizationFeature#addCharset}. So we know that the "init*" methods do nothing, and
     * we replace calls to them with nothing, i.e,, remove calls to them.
     *
     * We could do all this with individual {@link Substitute method substitutions}, but it would
     * require a lot of substitution methods that all look the same.
     */
    public static final class CharsetNodePlugin implements NodePlugin {

        @Override
        public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod method, ValueNode[] args) {
            if ((method.getName().equals("initc2b") || method.getName().equals("initb2c")) &&
                            b.getMetaAccess().lookupJavaType(Charset.class).isAssignableFrom(method.getDeclaringClass())) {

                /*
                 * Verify that the "*Initialized" field corresponding with the method was set to
                 * true, i.e., that initialization was done eagerly.
                 */
                ResolvedJavaType charsetType = method.getDeclaringClass();
                ResolvedJavaField initializedField = findStaticField(charsetType, method.getName().substring(4, 7) + "Initialized");
                if (!b.getConstantReflection().readFieldValue(initializedField, null).asBoolean()) {
                    String charsetName = charsetType.getUnqualifiedName();
                    try {
                        Charset charset = Charset.forName(charsetName);
                        addCharset(charset);
                    } catch (UnsupportedCharsetException e) {
                        throw VMError.shouldNotReachHere("Could not find non-initialized charset " + charsetType.getSourceFileName(), e);
                    }
                }

                /* We "handled" the method invocation by doing nothing. */
                return true;
            }
            return false;
        }

        private static ResolvedJavaField findStaticField(ResolvedJavaType declaringClass, String name) {
            for (ResolvedJavaField field : declaringClass.getStaticFields()) {
                if (field.getName().equals(name)) {
                    return field;
                }
            }
            throw VMError.shouldNotReachHere();
        }
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess arg0) {
        support = new LocalizationSupport();
        ImageSingletons.add(LocalizationSupport.class, support);
        ImageSingletons.add(LocalizationFeature.class, this);

        addCharsets();
        addProviders();
        addResourceBundles();
    }

    /**
     * The JDK performs dynamic lookup of charsets by name, which leads to dynamic class loading. We
     * cannot do that, because we need to know all classes ahead of time to perform our static
     * analysis. Therefore, we load and register all standard charsets here. Features that require
     * more than this can add additional charsets.
     */
    private static void addCharsets() {
        if (Options.AddAllCharsets.getValue()) {
            for (Charset c : Charset.availableCharsets().values()) {
                addCharset(c);
            }
        } else {
            addCharset(Charset.defaultCharset());
            addCharset(Charset.forName("US-ASCII"));
            addCharset(Charset.forName("ISO-8859-1"));
            addCharset(Charset.forName("UTF-8"));
            addCharset(Charset.forName("UTF-16BE"));
            addCharset(Charset.forName("UTF-16LE"));
            addCharset(Charset.forName("UTF-16"));
        }
    }

    public static void addCharset(Charset charset) {
        Map<String, Charset> charsets = ImageSingletons.lookup(LocalizationSupport.class).charsets;
        charsets.put(charset.name().toLowerCase(), charset);
        for (String name : charset.aliases()) {
            charsets.put(name.toLowerCase(), charset);
        }

        /* Eagerly initialize all the tables necessary for decoding / encoding. */
        charset.newDecoder();
        if (charset.canEncode()) {
            charset.newEncoder();
        }
    }

    /*
     * LocaleServiceProviderPool.spiClasses does not contain all the classes we need, so we list
     * them manually here.
     */
    private static final List<Class<? extends LocaleServiceProvider>> spiClasses = Arrays.asList(
                    BreakIteratorProvider.class,
                    CollatorProvider.class,
                    DateFormatProvider.class,
                    DateFormatSymbolsProvider.class,
                    DecimalFormatSymbolsProvider.class,
                    NumberFormatProvider.class,
                    CurrencyNameProvider.class,
                    LocaleNameProvider.class,
                    TimeZoneNameProvider.class,
                    CalendarDataProvider.class,
                    CalendarNameProvider.class);

    protected List<Class<? extends LocaleServiceProvider>> getSpiClasses() {
        return spiClasses;
    }

    private void addProviders() {
        for (Class<? extends LocaleServiceProvider> providerClass : getSpiClasses()) {
            LocaleProviderAdapter adapter = Objects.requireNonNull(LocaleProviderAdapter.getAdapter(providerClass, imageLocale));

            support.adaptersByClass.put(providerClass, adapter);
            LocaleProviderAdapter existing = support.adaptersByType.put(adapter.getAdapterType(), adapter);
            assert existing == null || existing == adapter : "Overwriting adapter type with a different adapter";

            LocaleServiceProvider provider = Objects.requireNonNull(adapter.getLocaleServiceProvider(providerClass));
            support.providerPools.put(providerClass, new Target_sun_util_locale_provider_LocaleServiceProviderPool(provider));
        }
    }

    protected void addResourceBundles() {
        addBundleToCache(localeData(java.util.spi.CalendarDataProvider.class).getCalendarData(imageLocale));
        addBundleToCache(localeData(java.util.spi.CurrencyNameProvider.class).getCurrencyNames(imageLocale));
        addBundleToCache(localeData(java.util.spi.LocaleNameProvider.class).getLocaleNames(imageLocale));
        addBundleToCache(localeData(java.util.spi.TimeZoneNameProvider.class).getTimeZoneNames(imageLocale));
        addBundleToCache(localeData(java.text.spi.BreakIteratorProvider.class).getBreakIteratorInfo(imageLocale));
        addBundleToCache(localeData(java.text.spi.BreakIteratorProvider.class).getCollationData(imageLocale));
        addBundleToCache(localeData(java.text.spi.DateFormatProvider.class).getDateFormatData(imageLocale));
        addBundleToCache(localeData(java.text.spi.NumberFormatProvider.class).getNumberFormatData(imageLocale));
        /* Note that JDK 11 support overrides this method to register more bundles. */

        final String[] alwaysRegisteredResourceBundles = new String[]{
                        "sun.util.logging.resources.logging"
        };
        for (String bundleName : alwaysRegisteredResourceBundles) {
            addBundleToCache(bundleName);
        }

        for (String bundleName : OptionUtils.flatten(",", Options.IncludeResourceBundles.getValue())) {
            addBundleToCache(bundleName);
        }
    }

    protected LocaleData localeData(Class<? extends LocaleServiceProvider> providerClass) {
        return ((ResourceBundleBasedAdapter) LocaleProviderAdapter.getAdapter(providerClass, imageLocale)).getLocaleData();
    }

    protected void addBundleToCache(ResourceBundle bundle) {
        addBundleToCache(bundle.getBaseBundleName(), bundle);
    }

    public void addBundleToCache(String bundleName) {
        if (bundleName.isEmpty()) {
            return;
        }

        ResourceBundle resourceBundle;
        try {
            resourceBundle = ModuleSupport.getResourceBundle(bundleName, imageLocale, Thread.currentThread().getContextClassLoader());
        } catch (MissingResourceException mre) {
            if (!bundleName.contains("/")) {
                throw mre;
            }
            // Due to a possible bug in the JDK, bundle names not following proper naming convention
            // need to be
            // converted to fully qualified class names before loading can succeed.
            // see GR-24211
            String dotBundleName = bundleName.replace("/", ".");
            resourceBundle = ModuleSupport.getResourceBundle(dotBundleName, imageLocale, Thread.currentThread().getContextClassLoader());
        }
        UserError.guarantee(resourceBundle != null, "The bundle named: %s, has not been found. " + "" +
                        "If the bundle is part of a module, verify the bundle name is a fully qualified class name. Otherwise " +
                        "verify the bundle path is accessible in the classpath.", bundleName);
        addBundleToCache(bundleName, resourceBundle);
    }

    private void addBundleToCache(String bundleName, ResourceBundle bundle) {
        /*
         * Ensure that the bundle contents are loaded. We need to walk the whole bundle parent chain
         * down to the root.
         */
        for (ResourceBundle cur = bundle; cur != null; cur = getParent(cur)) {
            RuntimeClassInitialization.initializeAtBuildTime(cur.getClass());
            cur.keySet();
        }

        support.resourceBundles.put(bundleName, bundle);
    }

    /*
     * The field ResourceBundle.parent is not public. There is a backdoor to access it via
     * SharedSecrets, but the package of SharedSecrets changed from JDK 8 to JDK 11 so it is
     * inconvenient to use it. Reflective access is easier.
     */
    private static final Field PARENT_FIELD = ReflectionUtil.lookupField(ResourceBundle.class, "parent");

    private static ResourceBundle getParent(ResourceBundle bundle) {
        try {
            return (ResourceBundle) PARENT_FIELD.get(bundle);
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }
}
