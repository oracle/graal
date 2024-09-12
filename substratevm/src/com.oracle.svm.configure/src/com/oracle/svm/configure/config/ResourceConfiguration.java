/*
 * Copyright (c) 2019, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.core.configure.ConditionalElement;
import com.oracle.svm.core.configure.ConfigurationParser;
import com.oracle.svm.core.configure.ResourceConfigurationParser;
import com.oracle.svm.core.configure.ResourcesRegistry;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.core.util.json.JsonPrinter;
import com.oracle.svm.core.util.json.JsonWriter;

public final class ResourceConfiguration extends ConfigurationBase<ResourceConfiguration, ResourceConfiguration.Predicate> {

    private static final String PROPERTY_BUNDLE = "java.util.PropertyResourceBundle";

    public static class ParserAdapter implements ResourcesRegistry {

        private final ResourceConfiguration configuration;

        ParserAdapter(ResourceConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public void addResources(ConfigurationCondition condition, String pattern) {
            configuration.addResourcePattern(condition, pattern);
        }

        @Override
        public void injectResource(Module module, String resourcePath, byte[] resourceContent) {
            VMError.shouldNotReachHere("Resource injection is only supported via Feature implementation");
        }

        @Override
        public void ignoreResources(ConfigurationCondition condition, String pattern) {
            configuration.ignoreResourcePattern(condition, pattern);
        }

        @Override
        public void addResourceBundles(ConfigurationCondition condition, String baseName) {
            configuration.addBundle(condition, baseName);
        }

        @Override
        public void addResourceBundles(ConfigurationCondition condition, String basename, Collection<Locale> locales) {
            configuration.addBundle(condition, basename, locales);
        }

        @Override
        public void addClassBasedResourceBundle(ConfigurationCondition condition, String basename, String className) {
            configuration.addClassResourceBundle(condition, basename, className);
        }
    }

    public static final class BundleConfiguration {
        public final ConfigurationCondition condition;
        public final String baseName;
        public final Set<String> locales = ConcurrentHashMap.newKeySet();
        public final Set<String> classNames = ConcurrentHashMap.newKeySet();

        private BundleConfiguration(ConfigurationCondition condition, String baseName) {
            this.condition = condition;
            this.baseName = baseName;
        }

        private BundleConfiguration(BundleConfiguration other) {
            this(other.condition, other.baseName);
            locales.addAll(other.locales);
            classNames.addAll(other.classNames);
        }
    }

    private final ConcurrentMap<ConditionalElement<String>, Pattern> addedResources = new ConcurrentHashMap<>();
    private final ConcurrentMap<ConditionalElement<String>, Pattern> ignoredResources = new ConcurrentHashMap<>();
    private final ConcurrentMap<ConditionalElement<String>, BundleConfiguration> bundles = new ConcurrentHashMap<>();

    public ResourceConfiguration() {
    }

    public ResourceConfiguration(ResourceConfiguration other) {
        addedResources.putAll(other.addedResources);
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
        addedResources.keySet().removeAll(other.addedResources.keySet());
        ignoredResources.keySet().removeAll(other.ignoredResources.keySet());
        bundles.keySet().removeAll(other.bundles.keySet());
    }

    @Override
    protected void merge(ResourceConfiguration other) {
        addedResources.putAll(other.addedResources);
        ignoredResources.putAll(other.ignoredResources);
        bundles.putAll(other.bundles);
    }

    @Override
    protected void intersect(ResourceConfiguration other) {
        addedResources.keySet().retainAll(other.addedResources.keySet());
        ignoredResources.keySet().retainAll(other.ignoredResources.keySet());
        bundles.keySet().retainAll(other.bundles.keySet());
    }

    @Override
    protected void removeIf(Predicate predicate) {
        addedResources.entrySet().removeIf(entry -> predicate.testIncludedResource(entry.getKey(), entry.getValue()));
        bundles.entrySet().removeIf(entry -> predicate.testIncludedBundle(entry.getKey(), entry.getValue()));
    }

    @Override
    public void mergeConditional(ConfigurationCondition condition, ResourceConfiguration other) {
        for (Map.Entry<ConditionalElement<String>, Pattern> entry : other.addedResources.entrySet()) {
            addedResources.put(new ConditionalElement<>(condition, entry.getKey().getElement()), entry.getValue());
        }
        for (Map.Entry<ConditionalElement<String>, Pattern> entry : other.ignoredResources.entrySet()) {
            ignoredResources.put(new ConditionalElement<>(condition, entry.getKey().getElement()), entry.getValue());
        }
        for (Map.Entry<ConditionalElement<String>, BundleConfiguration> entry : other.bundles.entrySet()) {
            bundles.put(new ConditionalElement<>(condition, entry.getKey().getElement()), new BundleConfiguration(entry.getValue()));
        }
    }

    public void addResourcePattern(ConfigurationCondition condition, String pattern) {
        addedResources.computeIfAbsent(new ConditionalElement<>(condition, pattern), p -> Pattern.compile(p.getElement()));
    }

    public void ignoreResourcePattern(ConfigurationCondition condition, String pattern) {
        ignoredResources.computeIfAbsent(new ConditionalElement<>(condition, pattern), p -> Pattern.compile(p.getElement()));
    }

    public void addBundle(ConfigurationCondition condition, String basename, Collection<Locale> locales) {
        BundleConfiguration config = getOrCreateBundleConfig(condition, basename);
        for (Locale locale : locales) {
            config.locales.add(locale.toLanguageTag());
        }
    }

    private void addBundle(ConfigurationCondition condition, String baseName) {
        getOrCreateBundleConfig(condition, baseName);
    }

    private void addClassResourceBundle(ConfigurationCondition condition, String basename, String className) {
        getOrCreateBundleConfig(condition, basename).classNames.add(className);
    }

    public void addBundle(ConfigurationCondition condition, List<String> classNames, List<String> locales, String baseName) {
        assert classNames.size() == locales.size() : "Each bundle should be represented by both classname and locale";
        BundleConfiguration config = getOrCreateBundleConfig(condition, baseName);
        for (int i = 0; i < classNames.size(); i++) {
            String className = classNames.get(i);
            String localeTag = locales.get(i);
            if (!className.equals(PROPERTY_BUNDLE)) {
                config.classNames.add(className);
            } else {
                config.locales.add(localeTag);
            }
        }
    }

    private BundleConfiguration getOrCreateBundleConfig(ConfigurationCondition condition, String baseName) {
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
        for (Pattern pattern : addedResources.values()) {
            if (pattern.matcher(s).matches()) {
                return true;
            }
        }
        return false;
    }

    public boolean anyBundleMatches(ConfigurationCondition condition, String bundleName) {
        return bundles.containsKey(new ConditionalElement<>(condition, bundleName));
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('{').indent().newline();
        writer.quote("resources").append(':').append('{').newline();
        writer.quote("includes").append(':');
        JsonPrinter.printCollection(writer, addedResources.keySet(), ConditionalElement.comparator(), (p, w) -> conditionalElementJson(p, w, "pattern"));
        if (!ignoredResources.isEmpty()) {
            writer.append(',').newline();
            writer.quote("excludes").append(':');
            JsonPrinter.printCollection(writer, ignoredResources.keySet(), ConditionalElement.comparator(), (p, w) -> conditionalElementJson(p, w, "pattern"));
        }
        writer.append('}').append(',').newline();
        writer.quote("bundles").append(':');
        JsonPrinter.printCollection(writer, bundles.keySet(), ConditionalElement.comparator(), (p, w) -> printResourceBundle(bundles.get(p), w));
        writer.unindent().newline().append('}');
    }

    @Override
    public ConfigurationParser createParser(boolean strictMetadata) {
        return ResourceConfigurationParser.create(strictMetadata, new ResourceConfiguration.ParserAdapter(this), true);
    }

    private static void printResourceBundle(BundleConfiguration config, JsonWriter writer) throws IOException {
        writer.append('{').indent().newline();
        ConfigurationConditionPrintable.printConditionAttribute(config.condition, writer);
        writer.quote("name").append(':').quote(config.baseName);
        if (!config.locales.isEmpty()) {
            writer.append(',').newline().quote("locales").append(":");
            JsonPrinter.printCollection(writer, config.locales, Comparator.naturalOrder(), (String p, JsonWriter w) -> w.quote(p));
        }
        if (!config.classNames.isEmpty()) {
            writer.append(',').newline().quote("classNames").append(":");
            JsonPrinter.printCollection(writer, config.classNames, Comparator.naturalOrder(), (String p, JsonWriter w) -> w.quote(p));
        }
        writer.unindent().newline().append('}');
    }

    @Override
    public boolean isEmpty() {
        return addedResources.isEmpty() && bundles.isEmpty();
    }

    private static void conditionalElementJson(ConditionalElement<String> p, JsonWriter w, String elementName) throws IOException {
        w.append('{').indent().newline();
        ConfigurationConditionPrintable.printConditionAttribute(p.getCondition(), w);
        w.quote(elementName).append(':').quote(p.getElement());
        w.unindent().newline().append('}');
    }

    public interface Predicate {
        boolean testIncludedResource(ConditionalElement<String> condition, Pattern pattern);

        boolean testIncludedBundle(ConditionalElement<String> condition, BundleConfiguration bundleConfiguration);
    }
}
