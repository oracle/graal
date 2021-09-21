/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import org.graalvm.nativeimage.impl.ConfigurationCondition;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.json.JsonPrinter;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.core.configure.ConditionalElement;
import com.oracle.svm.core.configure.ResourcesRegistry;

public class ResourceConfiguration implements ConfigurationBase {

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
        public void ignoreResources(ConfigurationCondition condition, String pattern) {
            configuration.ignoreResourcePattern(condition, pattern);
        }

        @Override
        public void addResourceBundles(ConfigurationCondition condition, String name) {
            configuration.addBundle(condition, name);
        }

    }

    private final ConcurrentMap<ConditionalElement<String>, Pattern> addedResources = new ConcurrentHashMap<>();
    private final ConcurrentMap<ConditionalElement<String>, Pattern> ignoredResources = new ConcurrentHashMap<>();
    private final Set<ConditionalElement<String>> bundles = ConcurrentHashMap.newKeySet();

    public ResourceConfiguration() {
    }

    public ResourceConfiguration(ResourceConfiguration other) {
        addedResources.putAll(other.addedResources);
        ignoredResources.putAll(other.ignoredResources);
        bundles.addAll(other.bundles);
    }

    public void removeAll(ResourceConfiguration other) {
        addedResources.keySet().removeAll(other.addedResources.keySet());
        ignoredResources.keySet().removeAll(other.ignoredResources.keySet());
        bundles.removeAll(other.bundles);
    }

    public void addResourcePattern(ConfigurationCondition condition, String pattern) {
        addedResources.computeIfAbsent(new ConditionalElement<>(condition, pattern), p -> Pattern.compile(p.getElement()));
    }

    public void ignoreResourcePattern(ConfigurationCondition condition, String pattern) {
        ignoredResources.computeIfAbsent(new ConditionalElement<>(condition, pattern), p -> Pattern.compile(p.getElement()));
    }

    public void addBundle(ConfigurationCondition condition, String bundle) {
        bundles.add(new ConditionalElement<>(condition, bundle));
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
        return bundles.contains(new ConditionalElement<>(condition, bundleName));
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
        JsonPrinter.printCollection(writer, bundles, ConditionalElement.comparator(), (p, w) -> conditionalElementJson(p, w, "name"));
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
}
