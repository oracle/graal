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
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import com.oracle.svm.configure.ConfigurationBase;
import com.oracle.svm.configure.json.JsonPrinter;
import com.oracle.svm.configure.json.JsonWriter;
import com.oracle.svm.core.configure.ResourcesRegistry;

public class ResourceConfiguration implements ConfigurationBase {

    public static class ParserAdapter implements ResourcesRegistry {
        private final ResourceConfiguration configuration;

        public ParserAdapter(ResourceConfiguration configuration) {
            this.configuration = configuration;
        }

        @Override
        public void addResources(String pattern) {
            configuration.addResourcePattern(pattern);
        }

        @Override
        public void ignoreResources(String pattern) {
            configuration.ignoreResourcePattern(pattern);
        }

        @Override
        public void addResourceBundles(String name) {
            configuration.addBundle(name);
        }
    }

    private final ConcurrentMap<String, Pattern> addedResources = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Pattern> ignoredResources = new ConcurrentHashMap<>();
    private final ConcurrentHashMap.KeySetView<String, Boolean> bundles = ConcurrentHashMap.newKeySet();

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

    public void addResourcePattern(String pattern) {
        addedResources.computeIfAbsent(pattern, Pattern::compile);
    }

    public void ignoreResourcePattern(String pattern) {
        ignoredResources.computeIfAbsent(pattern, Pattern::compile);
    }

    public void addBundle(String bundle) {
        bundles.add(bundle);
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

    public boolean anyBundleMatches(String s) {
        return bundles.contains(s);
    }

    @Override
    public void printJson(JsonWriter writer) throws IOException {
        writer.append('{').indent().newline();
        writer.quote("resources").append(':').append('{').newline();
        writer.quote("includes").append(':');
        JsonPrinter.printCollection(writer, addedResources.keySet(), Comparator.naturalOrder(), (String p, JsonWriter w) -> w.append('{').quote("pattern").append(':').quote(p).append('}'));
        if (!ignoredResources.isEmpty()) {
            writer.append(',').newline();
            writer.quote("excludes").append(':');
            JsonPrinter.printCollection(writer, ignoredResources.keySet(), Comparator.naturalOrder(), (String p, JsonWriter w) -> w.append('{').quote("pattern").append(':').quote(p).append('}'));
        }
        writer.append('}').append(',').newline();
        writer.quote("bundles").append(':');
        JsonPrinter.printCollection(writer, bundles, Comparator.naturalOrder(), (String p, JsonWriter w) -> w.append('{').quote("name").append(':').quote(p).append('}'));
        writer.unindent().newline().append('}');
    }

    @Override
    public boolean isEmpty() {
        return addedResources.isEmpty() && bundles.isEmpty();
    }

}
