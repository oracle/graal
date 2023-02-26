/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.jdk.localization;

import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.text.spi.BreakIteratorProvider;
import java.text.spi.CollatorProvider;
import java.text.spi.DateFormatProvider;
import java.text.spi.DateFormatSymbolsProvider;
import java.text.spi.DecimalFormatSymbolsProvider;
import java.text.spi.NumberFormatProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;
import java.util.spi.CalendarDataProvider;
import java.util.spi.CalendarNameProvider;
import java.util.spi.CurrencyNameProvider;
import java.util.spi.LocaleNameProvider;
import java.util.spi.LocaleServiceProvider;
import java.util.spi.ResourceBundleControlProvider;
import java.util.spi.TimeZoneNameProvider;

import org.graalvm.collections.Pair;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import org.graalvm.compiler.nodes.graphbuilderconf.NodePlugin;
import org.graalvm.compiler.options.Option;
import org.graalvm.compiler.options.OptionType;
import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.impl.RuntimeClassInitializationSupport;

import com.oracle.svm.core.ClassLoaderSupport;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.jdk.localization.BundleContentSubstitutedLocalizationSupport;
import com.oracle.svm.core.jdk.localization.LocalizationSupport;
import com.oracle.svm.core.jdk.localization.OptimizedLocalizationSupport;
import com.oracle.svm.core.jdk.localization.compression.GzipBundleCompression;
import com.oracle.svm.core.jdk.localization.substitutions.Target_sun_util_locale_provider_LocaleServiceProviderPool_OptimizedLocaleMode;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.LocatableMultiOptionValue;
import com.oracle.svm.core.util.UserError;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.FeatureImpl.DuringAnalysisAccessImpl;
import com.oracle.svm.hosted.FeatureImpl.DuringSetupAccessImpl;
import com.oracle.svm.hosted.NativeImageOptions;
import com.oracle.svm.util.ReflectionUtil;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;
import sun.text.spi.JavaTimeDateTimePatternProvider;
import sun.util.cldr.CLDRLocaleProviderAdapter;
import sun.util.locale.LocaleObjectCache;
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.ResourceBundleBasedAdapter;
import sun.util.resources.LocaleData;

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
 * The second approach relies on the original JVM implementation instead. This approach is
 * consistent by design, which solves compatibility issues and reduces maintenance overhead.
 * Unfortunately, the default way of storing bundle data in getContents methods, see
 * {@code sun.text.resources.FormatData} for example, is not very AOT friendly. Compiling these
 * methods is time consuming and results in a bloated image (183 MB HelloWorld with all locales).
 * Therefore, the bundle content itself is again stored in the image heap by default and furthermore
 * is compressed to reduce the image size, see {@link BundleContentSubstitutedLocalizationSupport}
 * and {@link GzipBundleCompression}.
 *
 * @author d-kozak
 * @see LocalizationSupport
 * @see OptimizedLocalizationSupport
 * @see BundleContentSubstitutedLocalizationSupport
 */
@AutomaticallyRegisteredFeature
public class LocalizationFeature implements InternalFeature {

    protected final boolean optimizedMode = Options.LocalizationOptimizedMode.getValue();

    private final boolean substituteLoadLookup = Options.LocalizationSubstituteLoadLookup.getValue();

    protected final boolean trace = Options.TraceLocalizationFeature.getValue();

    private final ForkJoinPool compressionPool = Options.LocalizationCompressInParallel.getValue() ? new ForkJoinPool(NativeImageOptions.NumberOfThreads.getValue()) : null;

    /**
     * The Locale that the native image is built for.
     */
    protected Locale defaultLocale = Locale.getDefault();

    private Charset defaultCharset;

    protected Set<Locale> allLocales;

    protected LocalizationSupport support;

    private Function<String, Class<?>> findClassByName;

    private Field baseLocaleCacheField;
    private Field localeCacheField;
    private Field candidatesCacheField;
    private Field localeObjectCacheMapField;
    private Field langAliasesCacheField;
    private Field parentLocalesMapField;

    public static class Options {
        @Option(help = "Comma separated list of bundles to be included into the image.", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> IncludeResourceBundles = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.commaSeparated());

        @Option(help = "Make all hosted charsets available at run time")//
        public static final HostedOptionKey<Boolean> AddAllCharsets = new HostedOptionKey<>(false);

        @Option(help = "Default locale of the image, by the default it is the same as the default locale of the image builder.", type = OptionType.User, //
                        deprecated = true, deprecationMessage = "Please switch to using system properties such as -Duser.country=CH -Duser.language=de")//
        public static final HostedOptionKey<String> DefaultLocale = new HostedOptionKey<>(Locale.getDefault().toLanguageTag());

        @Option(help = "Default charset of the image, by the default it is the same as the default charset of the image builder.", type = OptionType.User)//
        public static final HostedOptionKey<String> DefaultCharset = new HostedOptionKey<>(Charset.defaultCharset().name());

        @Option(help = "Comma separated list of locales to be included into the image. The default locale is included in the list automatically if not present.", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> IncludeLocales = new HostedOptionKey<>(LocatableMultiOptionValue.Strings.commaSeparated());

        @Option(help = "Make all hosted locales available at run time.", type = OptionType.User)//
        public static final HostedOptionKey<Boolean> IncludeAllLocales = new HostedOptionKey<>(false);

        @Option(help = "Optimize the resource bundle lookup using a simple map.", type = OptionType.User)//
        public static final HostedOptionKey<Boolean> LocalizationOptimizedMode = new HostedOptionKey<>(false);

        @Option(help = "Store the resource bundle content more efficiently in the fallback mode.", type = OptionType.User)//
        public static final HostedOptionKey<Boolean> LocalizationSubstituteLoadLookup = new HostedOptionKey<>(true);

        @Option(help = "Regular expressions matching which bundles should be compressed.", type = OptionType.User)//
        public static final HostedOptionKey<LocatableMultiOptionValue.Strings> LocalizationCompressBundles = new HostedOptionKey<>(new LocatableMultiOptionValue.Strings());

        @Option(help = "Compress the bundles in parallel.", type = OptionType.Expert)//
        public static final HostedOptionKey<Boolean> LocalizationCompressInParallel = new HostedOptionKey<>(true);

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
    public void afterRegistration(AfterRegistrationAccess access) {
        findClassByName = access::findClassByName;
        allLocales = processLocalesOption();
        if (Options.DefaultLocale.hasBeenSet()) {
            defaultLocale = LocalizationSupport.parseLocaleFromTag(Options.DefaultLocale.getValue());
            UserError.guarantee(defaultLocale != null, "Invalid default locale %s", Options.DefaultLocale.getValue());
        }
        try {
            defaultCharset = Charset.forName(Options.DefaultCharset.getValue());
            VMError.guarantee(defaultCharset.name().equals(Options.DefaultCharset.getValue()),
                            "Failed to locate charset " + Options.DefaultCharset.getValue() + ", instead " + defaultCharset.name() + " was provided");
        } catch (IllegalCharsetNameException | UnsupportedCharsetException ex) {
            throw UserError.abort(ex, "Invalid default charset %s", Options.DefaultCharset.getValue());
        }
        allLocales.add(defaultLocale);
        support = selectLocalizationSupport();
        ImageSingletons.add(LocalizationSupport.class, support);

        addCharsets();
        if (optimizedMode) {
            /*
             * Providers are only preprocessed in the optimized mode.
             */
            addProviders();
        }
    }

    @Override
    public void duringSetup(DuringSetupAccess a) {
        DuringSetupAccessImpl access = (DuringSetupAccessImpl) a;
        if (optimizedMode) {
            access.registerObjectReplacer(this::eagerlyInitializeBundles);
        }
        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            langAliasesCacheField = access.findField(CLDRLocaleProviderAdapter.class, "langAliasesCache");
            parentLocalesMapField = access.findField(CLDRLocaleProviderAdapter.class, "parentLocalesMap");
        }
        if (JavaVersionUtil.JAVA_SPEC >= 17) {
            baseLocaleCacheField = access.findField("sun.util.locale.BaseLocale$Cache", "CACHE");
            localeCacheField = access.findField("java.util.Locale$Cache", "LOCALECACHE");
        } else {
            baseLocaleCacheField = access.findField("sun.util.locale.BaseLocale", "CACHE");
            localeCacheField = access.findField("java.util.Locale", "LOCALECACHE");
        }
        candidatesCacheField = access.findField("java.util.ResourceBundle$Control", "CANDIDATES_CACHE");
        localeObjectCacheMapField = access.findField(LocaleObjectCache.class, "map");

        String reason = "All ResourceBundleControlProvider that are registered as services end up as objects in the image heap, and are therefore registered to be initialized at image build time";
        ServiceLoader.load(ResourceBundleControlProvider.class).stream()
                        .forEach(provider -> ImageSingletons.lookup(RuntimeClassInitializationSupport.class).initializeAtBuildTime(provider.type(), reason));
    }

    /**
     * In the optimized localization support, the bundles are stored in a map. In order to make the
     * getContents methods unreachable, the bundles are initialized eagerly and the lookup methods
     * are substituted. However, if there are bundle instances somewhere in the heap that were not
     * put in the map, they won't be initialized and therefore accessing their content will cause
     * runtime failures. Therefore, we visit each object in the heap and if it is a ResourceBundle,
     * we eagerly initialize it.
     */
    private Object eagerlyInitializeBundles(Object object) {
        assert optimizedMode : "Should only be triggered in the optimized mode.";
        if (object instanceof ResourceBundle) {
            ResourceBundle bundle = (ResourceBundle) object;
            try {
                /*
                 * getKeys can be null for ResourceBundle.NONEXISTENT_BUNDLE, which causes the
                 * keySet method to crash.
                 */
                if (bundle.getKeys() != null) {
                    bundle.keySet();
                }
            } catch (Exception ex) {
                trace("Failed to eagerly initialize bundle " + bundle + ", " + bundle.getBaseBundleName() + ", reason " + ex.getClass() + " " + ex.getMessage());
            }
        }
        return object;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private LocalizationSupport selectLocalizationSupport() {
        if (optimizedMode) {
            return new OptimizedLocalizationSupport(defaultLocale, allLocales, defaultCharset);
        } else if (substituteLoadLookup) {
            List<String> requestedPatterns = Options.LocalizationCompressBundles.getValue().values();
            return new BundleContentSubstitutedLocalizationSupport(defaultLocale, allLocales, defaultCharset, requestedPatterns, compressionPool);
        }
        return new LocalizationSupport(defaultLocale, allLocales, defaultCharset);
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        addResourceBundles();
    }

    @Override
    public void duringAnalysis(DuringAnalysisAccess a) {
        DuringAnalysisAccessImpl access = (DuringAnalysisAccessImpl) a;
        scanLocaleCache(access, baseLocaleCacheField);
        scanLocaleCache(access, localeCacheField);
        scanLocaleCache(access, candidatesCacheField);
        if (JavaVersionUtil.JAVA_SPEC >= 11) {
            access.rescanRoot(langAliasesCacheField);
            access.rescanRoot(parentLocalesMapField);
        }
    }

    private void scanLocaleCache(DuringAnalysisAccessImpl access, Field cacheFieldField) {
        access.rescanRoot(cacheFieldField);

        Object localeCache;
        try {
            localeCache = cacheFieldField.get(null);
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
        if (localeCache != null) {
            access.rescanField(localeCache, localeObjectCacheMapField);
        }
    }

    @Override
    public void afterAnalysis(AfterAnalysisAccess access) {
        if (compressionPool != null) {
            compressionPool.shutdown();
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private static Set<Locale> processLocalesOption() {
        Set<Locale> locales = new HashSet<>();
        if (Options.IncludeAllLocales.getValue()) {
            Collections.addAll(locales, Locale.getAvailableLocales());
            /*- Fallthrough to also allow adding custom locales */
        }
        List<String> invalid = new ArrayList<>();
        for (String tag : Options.IncludeLocales.getValue().values()) {
            Locale locale = LocalizationSupport.parseLocaleFromTag(tag);
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
    @Platforms(Platform.HOSTED_ONLY.class)
    private void addCharsets() {
        if (Options.AddAllCharsets.getValue()) {
            for (Charset c : Charset.availableCharsets().values()) {
                addCharset(c);
            }
        } else {
            addCharset(defaultCharset);
            addCharset(StandardCharsets.US_ASCII);
            addCharset(StandardCharsets.ISO_8859_1);
            addCharset(StandardCharsets.UTF_8);
            addCharset(StandardCharsets.UTF_16BE);
            addCharset(StandardCharsets.UTF_16LE);
            addCharset(StandardCharsets.UTF_16);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
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
                    JavaTimeDateTimePatternProvider.class,
                    CalendarDataProvider.class,
                    CalendarNameProvider.class);

    @Platforms(Platform.HOSTED_ONLY.class)
    private void addProviders() {
        OptimizedLocalizationSupport optimizedLocalizationSupport = support.asOptimizedSupport();
        for (Class<? extends LocaleServiceProvider> providerClass : spiClasses) {
            LocaleProviderAdapter adapter = Objects.requireNonNull(LocaleProviderAdapter.getAdapter(providerClass, defaultLocale));
            LocaleServiceProvider provider = Objects.requireNonNull(adapter.getLocaleServiceProvider(providerClass));
            optimizedLocalizationSupport.providerPools.put(providerClass, new Target_sun_util_locale_provider_LocaleServiceProviderPool_OptimizedLocaleMode(provider));
        }

        for (Locale locale : allLocales) {
            for (Locale candidateLocale : optimizedLocalizationSupport.control.getCandidateLocales("", locale)) {
                for (Class<? extends LocaleServiceProvider> providerClass : spiClasses) {
                    LocaleProviderAdapter adapter = Objects.requireNonNull(LocaleProviderAdapter.getAdapter(providerClass, candidateLocale));

                    optimizedLocalizationSupport.adaptersByClass.put(Pair.create(providerClass, candidateLocale), adapter);
                    LocaleProviderAdapter existing = optimizedLocalizationSupport.adaptersByType.put(adapter.getAdapterType(), adapter);
                    assert existing == null || existing == adapter : "Overwriting adapter type with a different adapter";

                }
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected void addResourceBundles() {
        for (Locale locale : allLocales) {
            prepareBundle(localeData(java.util.spi.CalendarDataProvider.class, locale).getCalendarData(locale), locale);
            prepareBundle(localeData(java.util.spi.CurrencyNameProvider.class, locale).getCurrencyNames(locale), locale);
            prepareBundle(localeData(java.util.spi.LocaleNameProvider.class, locale).getLocaleNames(locale), locale);
            prepareBundle(localeData(java.util.spi.TimeZoneNameProvider.class, locale).getTimeZoneNames(locale), locale);
            prepareBundle(localeData(java.text.spi.BreakIteratorProvider.class, locale).getBreakIteratorInfo(locale), locale);
            prepareBundle(localeData(java.text.spi.BreakIteratorProvider.class, locale).getCollationData(locale), locale);
            prepareBundle(localeData(java.text.spi.DateFormatProvider.class, locale).getDateFormatData(locale), locale);
            prepareBundle(localeData(java.text.spi.NumberFormatProvider.class, locale).getNumberFormatData(locale), locale);
            prepareBundle(localeData(java.text.spi.BreakIteratorProvider.class, locale).getBreakIteratorResources(locale), locale);
        }

        final String[] alwaysRegisteredResourceBundles = new String[]{
                        "sun.text.resources.FormatData",
                        "sun.util.logging.resources.logging",
                        "sun.util.resources.TimeZoneNames"
        };
        for (String bundleName : alwaysRegisteredResourceBundles) {
            prepareBundle(bundleName);
        }

        for (String bundleName : Options.IncludeResourceBundles.getValue().values()) {
            processRequestedBundle(bundleName);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected LocaleData localeData(Class<? extends LocaleServiceProvider> providerClass, Locale locale) {
        return ((ResourceBundleBasedAdapter) LocaleProviderAdapter.getAdapter(providerClass, locale)).getLocaleData();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void processRequestedBundle(String input) {
        int splitIndex = input.indexOf('_');
        boolean specificLocaleRequested = splitIndex != -1;
        if (!specificLocaleRequested) {
            prepareBundle(input, allLocales);
            return;
        }
        Locale locale = splitIndex + 1 < input.length() ? LocalizationSupport.parseLocaleFromTag(input.substring(splitIndex + 1)) : Locale.ROOT;
        if (locale == null) {
            trace("Cannot parse wanted locale " + input.substring(splitIndex + 1) + ", default will be used instead.");
            locale = defaultLocale;
        }
        /*- Get rid of locale specific suffix. */
        String baseName = input.substring(0, splitIndex);
        prepareBundle(baseName, Collections.singletonList(locale));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void prepareClassResourceBundle(String basename, String className) {
        Class<?> bundleClass = findClassByName.apply(className);
        UserError.guarantee(ResourceBundle.class.isAssignableFrom(bundleClass), "%s is not a subclass of ResourceBundle", bundleClass.getName());
        trace("Adding class based resource bundle: " + className + " " + bundleClass);
        support.prepareClassResourceBundle(basename, bundleClass);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void prepareBundle(String baseName) {
        prepareBundle(baseName, allLocales);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void prepareBundle(String baseName, Collection<Locale> wantedLocales) {
        if (baseName.isEmpty()) {
            return;
        }

        boolean somethingFound = false;
        for (Locale locale : wantedLocales) {
            List<ResourceBundle> resourceBundle;
            try {
                resourceBundle = ImageSingletons.lookup(ClassLoaderSupport.class).getResourceBundle(baseName, locale);
            } catch (MissingResourceException mre) {
                continue;
            }
            somethingFound |= !resourceBundle.isEmpty();
            for (ResourceBundle bundle : resourceBundle) {
                prepareBundle(baseName, bundle, locale);
            }
        }

        if (!somethingFound) {
            /*
             * Try non-compliant class-based bundles. These bundles can't be looked up by the normal
             * ResourceBundle lookup process, e.g. because they don't have default constructors.
             */
            Class<?> clazz = findClassByName.apply(baseName);
            if (clazz != null && ResourceBundle.class.isAssignableFrom(clazz)) {
                trace("Found non-compliant class-based bundle " + clazz);
                somethingFound = true;
                support.prepareNonCompliant(clazz);
            }
        }

        if (!somethingFound) {
            String errorMessage = "The bundle named: " + baseName + ", has not been found. " +
                            "If the bundle is part of a module, verify the bundle name is a fully qualified class name. Otherwise " +
                            "verify the bundle path is accessible in the classpath.";
            System.out.println(errorMessage);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected void prepareBundle(ResourceBundle bundle, Locale locale) {
        prepareBundle(bundle.getBaseBundleName(), bundle, locale);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private void prepareBundle(String bundleName, ResourceBundle bundle, Locale locale) {
        trace("Adding bundle " + bundleName + ", locale " + locale);
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

    @Platforms(Platform.HOSTED_ONLY.class)
    private static ResourceBundle getParent(ResourceBundle bundle) {
        try {
            return (ResourceBundle) PARENT_FIELD.get(bundle);
        } catch (ReflectiveOperationException ex) {
            throw VMError.shouldNotReachHere(ex);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    protected void trace(String msg) {
        if (trace) {
            System.out.println(msg);
        }
    }
}
