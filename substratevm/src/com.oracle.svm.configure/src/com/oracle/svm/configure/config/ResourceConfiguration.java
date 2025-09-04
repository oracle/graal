/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.config;

import static com.oracle.svm.configure.ConfigurationParser.BUNDLES_KEY;
import static com.oracle.svm.configure.ConfigurationParser.BUNDLE_KEY;
import static com.oracle.svm.configure.ConfigurationParser.GLOBS_KEY;
import static com.oracle.svm.configure.ConfigurationParser.NAME_KEY;
import static com.oracle.svm.configure.ConfigurationParser.RESOURCES_KEY;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.configure.ConditionalElement;
import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.ConfigurationParser;
import com.oracle.svm.configure.ConfigurationParserOption;
import com.oracle.svm.configure.ResourceConfigurationParser;
import com.oracle.svm.configure.ResourcesRegistry;
import com.oracle.svm.configure.UnresolvedConfigurationCondition;
import com.oracle.svm.configure.config.conditional.ConfigurationConditionResolver;
import com.oracle.svm.util.GlobUtils;
import com.oracle.svm.util.NativeImageResourcePathRepresentation;

import jdk.graal.compiler.util.json.JsonPrinter;
import jdk.graal.compiler.util.json.JsonWriter;

public final class ResourceConfiguration extends ConfigurationBase<ResourceConfiguration, ResourceConfiguration.Predicate> {

    public static class ParserAdapter implements ResourcesRegistry<UnresolvedConfigurationCondition> {

        private final ResourceConfiguration configuration;

        ParserAdapter(ResourceConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public void addResources(UnresolvedConfigurationCondition condition, String pattern, Object origin) {
            configuration.classifyAndAddPattern(new ConditionalElement<>(condition, pattern));
        }

        @Override
        public void addGlob(UnresolvedConfigurationCondition condition, String module, String glob, Object origin) {
            configuration.addedGlobs.add(new ConditionalElement<>(condition, new ResourceEntry(glob, module)));
        }

        @Override
        public void addResourceEntry(Module module, String resourcePath, Object origin) {
            throw new UnsupportedOperationException("Unused function.");
        }

        @Override
        public void addCondition(ConfigurationCondition condition, Module module, String resourcePath) {
            throw new UnsupportedOperationException("Unused function.");
        }

        @Override
        public void injectResource(Module module, String resourcePath, byte[] resourceContent, Object origin) {
            throw new UnsupportedOperationException("Resource injection is only supported via Feature implementation");
        }

        @Override
        public void ignoreResources(UnresolvedConfigurationCondition condition, String pattern, Object origin) {
            configuration.ignoreResourcePattern(condition, pattern);
        }

        @Override
        public void addResourceBundles(UnresolvedConfigurationCondition condition, String baseName) {
            configuration.addBundle(condition, baseName);
        }

        @Override
        public void addResourceBundles(UnresolvedConfigurationCondition condition, String basename, Collection<Locale> locales) {
            configuration.addBundle(condition, basename, locales);
        }

        @Override
        public void addClassBasedResourceBundle(UnresolvedConfigurationCondition condition, String basename, String className) {
            configuration.addClassResourceBundle(condition, basename, className);
        }
    }

    public static final class BundleConfiguration {
        public final UnresolvedConfigurationCondition condition;
        public final String module;
        public final String baseName;
        public final Set<String> locales = ConcurrentHashMap.newKeySet();
        public final Set<String> classNames = ConcurrentHashMap.newKeySet();

        public BundleConfiguration(UnresolvedConfigurationCondition condition, String module, String baseName) {
            this.condition = condition;
            this.module = module;
            this.baseName = baseName;
        }

        public BundleConfiguration(UnresolvedConfigurationCondition condition, String baseName) {
            this(condition, null, baseName);
        }

        private BundleConfiguration(BundleConfiguration other) {
            this(other.condition, other.module, other.baseName);
            locales.addAll(other.locales);
            classNames.addAll(other.classNames);
        }
    }

    public record ResourceEntry(String pattern, String module) {
        public static Comparator<ResourceEntry> comparator() {
            Comparator<ResourceEntry> moduleComparator = Comparator.comparing(ResourceEntry::module, Comparator.nullsFirst(Comparator.naturalOrder()));
            Comparator<ResourceEntry> patternComparator = Comparator.comparing(ResourceEntry::pattern, Comparator.nullsFirst(Comparator.naturalOrder()));
            return moduleComparator.thenComparing(patternComparator);
        }
    }

    private final Set<ConditionalElement<String>> addedResources = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<ConditionalElement<ResourceEntry>> addedGlobs = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final ConcurrentMap<ConditionalElement<String>, Pattern> ignoredResources = new ConcurrentHashMap<>();
    private final ConcurrentMap<ConditionalElement<String>, BundleConfiguration> bundles = new ConcurrentHashMap<>();

    public ResourceConfiguration() {
    }

    public ResourceConfiguration(ResourceConfiguration other) {
        addedGlobs.addAll(other.addedGlobs);
        other.addedResources.forEach(this::classifyAndAddPattern);
        ignoredResources.putAll(other.ignoredResources);
        for (Map.Entry<ConditionalElement<String>, BundleConfiguration> entry : other.bundles.entrySet()) {
            bundles.put(entry.getKey(), new BundleConfiguration(entry.getValue()));
        }
    }

    @Override
    public ResourceConfiguration copy() {
        return new ResourceConfiguration(this);
    }

    @Override
    public void subtract(ResourceConfiguration other) {
        addedGlobs.removeAll(other.addedGlobs);
        addedResources.removeAll(other.addedResources);
        ignoredResources.keySet().removeAll(other.ignoredResources.keySet());
        bundles.keySet().removeAll(other.bundles.keySet());
    }

    @Override
    protected void merge(ResourceConfiguration other) {
        addedGlobs.addAll(other.addedGlobs);
        addedResources.addAll(other.addedResources);
        ignoredResources.putAll(other.ignoredResources);
        bundles.putAll(other.bundles);
    }

    @Override
    protected void intersect(ResourceConfiguration other) {
        addedGlobs.retainAll(other.addedGlobs);
        addedResources.retainAll(other.addedResources);
        ignoredResources.keySet().retainAll(other.ignoredResources.keySet());
        bundles.keySet().retainAll(other.bundles.keySet());
    }

    @Override
    protected void removeIf(Predicate predicate) {
        addedGlobs.removeIf(predicate::testIncludedGlob);
        addedResources.removeIf(predicate::testIncludedResource);
        bundles.entrySet().removeIf(entry -> predicate.testIncludedBundle(entry.getKey(), entry.getValue()));
    }

    @Override
    public void mergeConditional(UnresolvedConfigurationCondition condition, ResourceConfiguration other) {
        for (ConditionalElement<ResourceEntry> entry : other.addedGlobs) {
            addedGlobs.add(new ConditionalElement<>(condition, entry.element()));
        }
        for (ConditionalElement<String> entry : other.addedResources) {
            addedResources.add(new ConditionalElement<>(condition, entry.element()));
        }
        for (Map.Entry<ConditionalElement<String>, Pattern> entry : other.ignoredResources.entrySet()) {
            ignoredResources.put(new ConditionalElement<>(condition, entry.getKey().element()), entry.getValue());
        }
        for (Map.Entry<ConditionalElement<String>, BundleConfiguration> entry : other.bundles.entrySet()) {
            bundles.put(new ConditionalElement<>(condition, entry.getKey().element()), new BundleConfiguration(entry.getValue()));
        }
    }

    public void addResourcePattern(UnresolvedConfigurationCondition condition, String pattern) {
        addedResources.add(new ConditionalElement<>(condition, pattern));
    }

    public void addGlobPattern(UnresolvedConfigurationCondition condition, String pattern, String module) {
        /*
         * prevent patterns discovered by the agent to be written in the non-canonical form. Example
         * canonical path: foo/1.txt; non-canonical path: foo/bar/../1.txt
         */
        String canonicalPattern = NativeImageResourcePathRepresentation.toCanonicalForm(pattern);
        ResourceEntry element = new ResourceEntry(escapePossibleGlobWildcards(canonicalPattern), module);
        addedGlobs.add(new ConditionalElement<>(condition, element));
    }

    private static String escapePossibleGlobWildcards(String pattern) {
        String escapedPattern = pattern;
        for (char wildcard : GlobUtils.ALWAYS_ESCAPED_GLOB_WILDCARDS) {
            escapedPattern = escapedPattern.replace(String.valueOf(wildcard), "\\" + wildcard);
        }

        return escapedPattern;
    }

    private static String convertSimpleRegexToGlob(String pattern) {
        return escapePossibleGlobWildcards(pattern.replace("\\Q", "").replace("\\E", ""));
    }

    private static boolean isModuleIdentifierChar(int c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '+' || c == '-';
    }

    /*
     * pattern starts with <module>\\Q and ends with \\E. Also pattern can't contain more than one
     * quote or anything outside \\Q and \\E. Invalid pattern example "\\Qfoo\\E.*\\Qbar\\E"
     */
    private static boolean isSimpleQuotedPattern(String pattern) {
        String quoteStart = "\\Q";
        String quoteEnd = "\\E";

        /* pattern must have \\Q to be simple */
        int quoteBeginning = pattern.indexOf(quoteStart);
        if (quoteBeginning == -1) {
            return false;
        }

        /* module prefix must be simple without any special meanings */
        if (pattern.chars().limit(quoteBeginning).allMatch(ResourceConfiguration::isModuleIdentifierChar)) {
            return false;
        }

        /* there must be only one quotation, otherwise wildcards can be used in unquoted area */
        if (pattern.lastIndexOf(quoteStart) != quoteBeginning) {
            return false;
        }

        /* nothing can be found after the end of quotation otherwise, we could find wildcard */
        int firstQuoteEnd = pattern.indexOf(quoteEnd);
        int lastQuoteEnd = pattern.lastIndexOf(quoteEnd);
        int expectedQuoteEndPosition = pattern.length() - 1;
        return firstQuoteEnd == lastQuoteEnd && firstQuoteEnd == expectedQuoteEndPosition;
    }

    private void classifyAndAddPattern(ConditionalElement<String> entry) {
        String pattern = entry.element();
        if (isSimpleQuotedPattern(pattern)) {
            String unquotedPattern = convertSimpleRegexToGlob(pattern);
            String module = null;
            int moduleSplitter = pattern.indexOf(':');
            if (moduleSplitter != -1) {
                String[] parts = unquotedPattern.split(":");
                module = parts[0];
                unquotedPattern = parts[1];
            }

            addedGlobs.add(new ConditionalElement<>(entry.condition(), new ResourceEntry(unquotedPattern, module)));
        } else {
            addedResources.add(new ConditionalElement<>(entry.condition(), pattern));
        }
    }

    public void ignoreResourcePattern(UnresolvedConfigurationCondition condition, String pattern) {
        ignoredResources.computeIfAbsent(new ConditionalElement<>(condition, pattern), p -> Pattern.compile(p.element()));
    }

    public void addBundle(UnresolvedConfigurationCondition condition, String basename, Collection<Locale> locales) {
        BundleConfiguration config = getOrCreateBundleConfig(condition, basename);
        for (Locale locale : locales) {
            config.locales.add(locale.toLanguageTag());
        }
    }

    public void addBundle(UnresolvedConfigurationCondition condition, String baseName) {
        getOrCreateBundleConfig(condition, baseName);
    }

    private void addClassResourceBundle(UnresolvedConfigurationCondition condition, String basename, String className) {
        getOrCreateBundleConfig(condition, basename).classNames.add(className);
    }

    private BundleConfiguration getOrCreateBundleConfig(UnresolvedConfigurationCondition condition, String baseName) {
        ConditionalElement<String> key = new ConditionalElement<>(condition, baseName);
        return bundles.computeIfAbsent(key, cond -> new BundleConfiguration(condition, baseName));
    }

    public boolean anyResourceMatches(String s) {
        /*
         * Naive -- if the need arises, we could match in the order of most frequently matched
         * patterns, or somehow merge the patterns into a single big pattern.
         */
        for (Pattern pattern : ignoredResources.values()) {
            if (pattern.matcher(s).matches()) {
                return false;
            }
        }

        for (ConditionalElement<String> pattern : addedResources) {
            if (Pattern.compile(pattern.element()).matcher(s).matches()) {
                return true;
            }
        }

        return false;
    }

    public boolean anyBundleMatches(UnresolvedConfigurationCondition condition, String bundleName) {
        return bundles.containsKey(new ConditionalElement<>(condition, bundleName));
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        JsonPrinter.printCollection(writer, addedGlobs, ConditionalElement.comparator(ResourceEntry.comparator()), (p, w) -> conditionalGlobElementJson(p, w, true), true, bundles.isEmpty());
        if (!bundles.isEmpty()) {
            if (!addedGlobs.isEmpty()) {
                writer.appendSeparator();
            }
            JsonPrinter.printCollection(writer, bundles.keySet(), ConditionalElement.comparator(String::compareTo), (p, w) -> printResourceBundle(bundles.get(p), w, true), false, true);
        }
    }

    @Override
    public void printLegacyJson(JsonWriter writer) throws IOException {
        writer.appendObjectStart();
        printResourcesJson(writer);
        writer.appendSeparator();
        printBundlesJson(writer);
        writer.appendSeparator();
        printGlobsJson(writer);
        writer.appendObjectEnd();
    }

    void printResourcesJson(JsonWriter writer) throws IOException {
        writer.quote(RESOURCES_KEY).appendFieldSeparator().appendObjectStart();
        writer.quote("includes").appendFieldSeparator();
        JsonPrinter.printCollection(writer, addedResources, ConditionalElement.comparator(), ResourceConfiguration::conditionalRegexElementJson);
        if (!ignoredResources.isEmpty()) {
            writer.appendSeparator();
            writer.quote("excludes").appendFieldSeparator();
            JsonPrinter.printCollection(writer, ignoredResources.keySet(), ConditionalElement.comparator(), ResourceConfiguration::conditionalRegexElementJson);
        }
        writer.appendObjectEnd();
    }

    void printBundlesJson(JsonWriter writer) throws IOException {
        writer.quote(BUNDLES_KEY).appendFieldSeparator();
        JsonPrinter.printCollection(writer, bundles.keySet(), ConditionalElement.comparator(), (p, w) -> printResourceBundle(bundles.get(p), w, false));
    }

    void printGlobsJson(JsonWriter writer) throws IOException {
        writer.quote(GLOBS_KEY).appendFieldSeparator();
        JsonPrinter.printCollection(writer, addedGlobs, ConditionalElement.comparator(ResourceEntry.comparator()), (p, w) -> conditionalGlobElementJson(p, w, false));
    }

    @Override
    public ConfigurationParser createParser(boolean combinedFileSchema, EnumSet<ConfigurationParserOption> parserOptions) {
        return ResourceConfigurationParser.create(combinedFileSchema, ConfigurationConditionResolver.identityResolver(), new ParserAdapter(this), parserOptions);
    }

    public static void printResourceBundle(BundleConfiguration config, JsonWriter writer, boolean combinedFile) throws IOException {
        writer.appendObjectStart();
        ConfigurationConditionPrintable.printConditionAttribute(config.condition, writer, combinedFile);
        if (config.module != null) {
            writer.quote("module").appendFieldSeparator().quote(config.module).appendSeparator();
        }
        writer.quote(combinedFile ? BUNDLE_KEY : NAME_KEY).appendFieldSeparator().quote(config.baseName);
        if (!combinedFile && !config.locales.isEmpty()) {
            writer.appendSeparator().quote("locales").appendFieldSeparator();
            JsonPrinter.printCollection(writer, config.locales, Comparator.naturalOrder(), (String p, JsonWriter w) -> w.quote(p));
        }
        if (!combinedFile && !config.classNames.isEmpty()) {
            writer.appendSeparator().quote("classNames").appendFieldSeparator();
            JsonPrinter.printCollection(writer, config.classNames, Comparator.naturalOrder(), (String p, JsonWriter w) -> w.quote(p));
        }
        writer.appendObjectEnd();
    }

    @Override
    public boolean isEmpty() {
        return addedResources.isEmpty() && bundles.isEmpty() && addedGlobs.isEmpty();
    }

    @Override
    public boolean supportsCombinedFile() {
        if (!addedResources.isEmpty() || !ignoredResources.isEmpty()) {
            return false;
        }
        for (ResourceConfiguration.BundleConfiguration bundleConfiguration : bundles.values()) {
            if (!bundleConfiguration.classNames.isEmpty() || !bundleConfiguration.locales.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public static void conditionalGlobElementJson(ConditionalElement<ResourceEntry> p, JsonWriter w, boolean combinedFile) throws IOException {
        String pattern = p.element().pattern();
        String module = p.element().module();
        w.appendObjectStart();
        ConfigurationConditionPrintable.printConditionAttribute(p.condition(), w, combinedFile);
        if (module != null) {
            w.quote("module").appendFieldSeparator().quote(module).appendSeparator();
        }
        w.quote("glob").appendFieldSeparator().quote(pattern);
        w.appendObjectEnd();
    }

    private static void conditionalRegexElementJson(ConditionalElement<String> p, JsonWriter w) throws IOException {
        w.appendObjectStart();
        ConfigurationConditionPrintable.printConditionAttribute(p.condition(), w, false);
        w.quote("pattern").appendFieldSeparator().quote(p.element());
        w.appendObjectEnd();
    }

    public interface Predicate {
        boolean testIncludedResource(ConditionalElement<String> condition);

        boolean testIncludedBundle(ConditionalElement<String> condition, BundleConfiguration bundleConfiguration);

        boolean testIncludedGlob(ConditionalElement<ResourceEntry> entry);
    }
}
