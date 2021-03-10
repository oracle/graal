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
package com.oracle.svm.core.jdk.localization;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IllformedLocaleException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.spi.CalendarDataProvider;
import java.util.spi.CalendarNameProvider;
import java.util.spi.CurrencyNameProvider;
import java.util.spi.LocaleNameProvider;
import java.util.spi.LocaleServiceProvider;
import java.util.spi.TimeZoneNameProvider;

import com.oracle.svm.core.jdk.localization.compression.GzipBundleCompression;
import com.oracle.svm.core.jdk.localization.substitutions.OptimizedModeOnlySubstitutions;
import org.graalvm.collections.Pair;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.option.OptionUtils;
import com.oracle.svm.core.util.UserError;
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

/**
 * LocalizationFeature is the core class of SVM localization support. It contains all the options
 * that can be used to configure how localization in the resulting image should work. One can
 * specify what charsets, locales and resource bundles should be accessible. The runtime data for
 * localization is stored in an image singleton of type {@link LocalizationSupport} or one of its
 * subtypes.
 *
 * In case of ResourceBundles, one can also specify how bundles should be handled, because currently
 * there are two different modes.
 *
 * The first approach is using a simple in memory map instead of the original JDK lookup. This
 * simpler implementation leads to image size savings for smaller images such as hello world, but
 * could cause compatibility issues and maintenance overhead. It is implemented in
 * {@link OptimizedLocalizationSupport}.
 * 
 * The second approach removes some of our substitutions and relies on the original JVM
 * implementation instead. This approach is consistent by design, which solves compatibility issues
 * and reduces maintenance overhead. Unfortunately, the default way of storing bundle data in
 * getContents methods, see {@link sun.text.resources.FormatData} for example, is not very AOT
 * friendly. Compiling these methods is time consuming and results in a bloated image (183 MB
 * HelloWorld with all locales). Therefore, the bundle content itself is again stored in the image
 * heap by default and furthermore is compressed to reduce the image size, see
 * {@link BundleContentSubstitutedLocalizationSupport} and {@link GzipBundleCompression}.
 *
 * @author d-kozak
 * @see LocalizationSupport
 * @see OptimizedLocalizationSupport
 * @see BundleContentSubstitutedLocalizationSupport
 */
public abstract class LocalizationFeature implements Feature {

    protected final boolean optimizedMode = optimizedMode();

    private final boolean substituteLoadLookup = Options.LocalizationSubstituteLoadLookup.getValue();

    protected final boolean trace = Options.TraceLocalizationFeature.getValue();

    public static boolean optimizedMode() {
        return Options.LocalizationOptimizedMode.getValue();
    }

    public static boolean jvmMode() {
        return !optimizedMode();
    }

    /**
     * The Locale that the native image is built for. Currently, switching the Locale at run time is
     * not supported because the resource bundles are only included for one Locale. We use the
     * Locale that is set for the image generator.
     */
    protected Locale defaultLocale = Locale.getDefault();

    protected List<Locale> locales;

    protected LocalizationSupport support;

    public static class Options {
        @Option(help = "Comma separated list of bundles to be included into the image.", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> IncludeResourceBundles = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

        @Option(help = "Make all hosted charsets available at run time")//
        public static final HostedOptionKey<Boolean> AddAllCharsets = new HostedOptionKey<>(false);

        @Option(help = "Default locale of the image, by the default it is the same as the default locale of the image builder.", type = OptionType.User)//
        public static final HostedOptionKey<String> DefaultLocale = new HostedOptionKey<>(Locale.getDefault().toLanguageTag());

        @Option(help = "Comma separated list of locales to be included into the image. The default locale is included in the list automatically if not present.", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> IncludeLocales = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

        @Option(help = "Make all hosted locales available at run time.", type = OptionType.User)//
        public static final HostedOptionKey<Boolean> IncludeAllLocales = new HostedOptionKey<>(false);

        @Option(help = "Optimize the resource bundle lookup using a simple map.", type = OptionType.User)//
        public static final HostedOptionKey<Boolean> LocalizationOptimizedMode = new HostedOptionKey<>(true);

        @Option(help = "Store the resource bundle content more efficiently in the fallback mode.", type = OptionType.User)//
        public static final HostedOptionKey<Boolean> LocalizationSubstituteLoadLookup = new HostedOptionKey<>(true);

        @Option(help = "When enabled, localization feature details are printed.", type = OptionType.Debug)//
        public static final HostedOptionKey<Boolean> TraceLocalizationFeature = new HostedOptionKey<>(false);
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
        locales = processLocalesOption();
        defaultLocale = parseLocaleFromTag(Options.DefaultLocale.getValue());
        UserError.guarantee(defaultLocale != null, "Invalid default locale %s", Options.DefaultLocale.getValue());
        if (!locales.contains(defaultLocale)) {
            locales.add(defaultLocale);
        }
        support = selectLocalizationSupport();
        ImageSingletons.add(LocalizationSupport.class, support);
        ImageSingletons.add(LocalizationFeature.class, this);

        addCharsets();
        if (optimizedMode) {
            /*
             * Providers are only preprocessed in the optimized mode.
             */
            addProviders();
        }
    }

    private LocalizationSupport selectLocalizationSupport() {
        if (optimizedMode) {
            return new OptimizedLocalizationSupport(defaultLocale, locales);
        } else if (substituteLoadLookup) {
            return new BundleContentSubstitutedLocalizationSupport(defaultLocale, locales);
        }
        return new LocalizationSupport(defaultLocale, locales);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        addResourceBundles();
    }

    /**
     * @return locale for given tag or null for invalid ones
     */
    private static Locale parseLocaleFromTag(String tag) {
        try {
            return new Locale.Builder().setLanguageTag(tag).build();
        } catch (IllformedLocaleException ex) {
            return null;
        }
    }

    private static List<Locale> processLocalesOption() {
        if (Options.IncludeAllLocales.getValue()) {
            return Arrays.asList(Locale.getAvailableLocales());
        }
        List<Locale> locales = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String tag : Options.IncludeLocales.getValue().values()) {
            Locale locale = parseLocaleFromTag(tag);
            if (locale != null) {
                locales.add(locale);
            } else {
                invalid.add(tag);
            }
        }
        if (!invalid.isEmpty()) {
            throw UserError.abort("Invalid locales specified: %s", invalid);
        }
        return locales;
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
        OptimizedLocalizationSupport optimizedLocalizationSupport = support.asOptimizedSupport();
        for (Class<? extends LocaleServiceProvider> providerClass : getSpiClasses()) {
            LocaleProviderAdapter adapter = Objects.requireNonNull(LocaleProviderAdapter.getAdapter(providerClass, defaultLocale));
            LocaleServiceProvider provider = Objects.requireNonNull(adapter.getLocaleServiceProvider(providerClass));
            optimizedLocalizationSupport.providerPools.put(providerClass, new OptimizedModeOnlySubstitutions.Target_sun_util_locale_provider_LocaleServiceProviderPool(provider));
        }

        for (Locale locale : locales) {
            for (Locale candidateLocale : optimizedLocalizationSupport.control.getCandidateLocales("", locale)) {
                for (Class<? extends LocaleServiceProvider> providerClass : getSpiClasses()) {
                    LocaleProviderAdapter adapter = Objects.requireNonNull(LocaleProviderAdapter.getAdapter(providerClass, candidateLocale));

                    optimizedLocalizationSupport.adaptersByClass.put(Pair.create(providerClass, candidateLocale), adapter);
                    LocaleProviderAdapter existing = optimizedLocalizationSupport.adaptersByType.put(adapter.getAdapterType(), adapter);
                    assert existing == null || existing == adapter : "Overwriting adapter type with a different adapter";

                }
            }
        }
    }

    protected void addResourceBundles() {
        for (Locale locale : locales) {
            prepareBundle(localeData(java.util.spi.CalendarDataProvider.class, locale).getCalendarData(locale), locale);
            prepareBundle(localeData(java.util.spi.CurrencyNameProvider.class, locale).getCurrencyNames(locale), locale);
            prepareBundle(localeData(java.util.spi.LocaleNameProvider.class, locale).getLocaleNames(locale), locale);
            prepareBundle(localeData(java.util.spi.TimeZoneNameProvider.class, locale).getTimeZoneNames(locale), locale);
            prepareBundle(localeData(java.text.spi.BreakIteratorProvider.class, locale).getBreakIteratorInfo(locale), locale);
            prepareBundle(localeData(java.text.spi.BreakIteratorProvider.class, locale).getCollationData(locale), locale);
            prepareBundle(localeData(java.text.spi.DateFormatProvider.class, locale).getDateFormatData(locale), locale);
            prepareBundle(localeData(java.text.spi.NumberFormatProvider.class, locale).getNumberFormatData(locale), locale);
            /* Note that JDK 11 support overrides this method to register more bundles. */
        }

        final String[] alwaysRegisteredResourceBundles = new String[]{
                        "sun.util.logging.resources.logging",
                        "sun.util.resources.TimeZoneNames"
        };
        for (String bundleName : alwaysRegisteredResourceBundles) {
            prepareBundle(bundleName);
        }

        for (String bundleName : OptionUtils.flatten(",", Options.IncludeResourceBundles.getValue())) {
            prepareBundle(bundleName);
        }
    }

    protected LocaleData localeData(Class<? extends LocaleServiceProvider> providerClass, Locale locale) {
        return ((ResourceBundleBasedAdapter) LocaleProviderAdapter.getAdapter(providerClass, locale)).getLocaleData();
    }

    protected void prepareBundle(ResourceBundle bundle, Locale locale) {
        prepareBundle(bundle.getBaseBundleName(), bundle, locale);
    }

    public void prepareBundle(String bundleName) {
        if (bundleName.isEmpty()) {
            return;
        }

        List<Locale> wantedLocales = locales;
        int splitIndex = bundleName.indexOf('_');
        if (splitIndex != -1) {
            Locale locale = splitIndex + 1 < bundleName.length() ? parseLocaleFromTag(bundleName.substring(splitIndex + 1)) : Locale.ROOT;
            if (locale == null) {
                trace("Cannot parse wanted locale " + bundleName.substring(splitIndex + 1) + ", default will be used instead.");
                locale = defaultLocale;
            }
            /*- Get rid of locale specific substring. */
            bundleName = bundleName.substring(0, splitIndex);
            wantedLocales = Collections.singletonList(locale);
        }

        boolean somethingFound = false;
        for (Locale locale : wantedLocales) {
            ResourceBundle resourceBundle;
            try {
                resourceBundle = ModuleSupport.getResourceBundle(bundleName, locale, Thread.currentThread().getContextClassLoader());
            } catch (MissingResourceException mre) {
                if (!bundleName.contains("/")) {
                    // fallthrough
                    continue;
                }
                // Due to a possible bug in the JDK, bundle names not following proper naming
                // convention
                // need to be
                // converted to fully qualified class names before loading can succeed.
                // see GR-24211
                String dotBundleName = bundleName.replace("/", ".");
                try {
                    resourceBundle = ModuleSupport.getResourceBundle(dotBundleName, locale, Thread.currentThread().getContextClassLoader());
                } catch (MissingResourceException ex) {
                    // fallthrough
                    continue;
                }
            }
            somethingFound = true;
            prepareBundle(bundleName, resourceBundle, locale);
        }

        if (!somethingFound) {
            String errorMessage = "The bundle named: " + bundleName + ", has not been found. " +
                            "If the bundle is part of a module, verify the bundle name is a fully qualified class name. Otherwise " +
                            "verify the bundle path is accessible in the classpath.";
            trace(errorMessage);
        }
    }

    private void prepareBundle(String bundleName, ResourceBundle bundle, Locale locale) {
        trace("Adding bundle " + bundleName);
        /*
         * Ensure that the bundle contents are loaded. We need to walk the whole bundle parent chain
         * down to the root.
         */
        for (ResourceBundle cur = bundle; cur != null; cur = getParent(cur)) {
            /* Register all bundles with their corresponding locales */
            support.prepareBundle(bundleName, cur, cur.getLocale());
        }

        /*
         * Finally, register the requested bundle with requested locale (Requested might be more
         * specific than the actual bundle locale
         */
        support.prepareBundle(bundleName, bundle, locale);
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

    protected void trace(String msg) {
        if (trace) {
            // Checkstyle: stop
            System.out.println(msg);
            // Checkstyle: resume
        }
    }
}
