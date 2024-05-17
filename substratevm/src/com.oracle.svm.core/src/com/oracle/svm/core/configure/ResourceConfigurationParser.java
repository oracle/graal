/*
 * Copyright (c) 2019, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.configure;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.impl.ConfigurationCondition;
import org.graalvm.util.json.JSONParserException;

import com.oracle.svm.core.jdk.localization.LocalizationSupport;
import com.oracle.svm.core.util.VMError;

public class ResourceConfigurationParser extends ConfigurationParser {
    private final ResourcesRegistry registry;

    public ResourceConfigurationParser(ResourcesRegistry registry, boolean strictConfiguration) {
        super(strictConfiguration);
        this.registry = registry;
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        parseTopLevelObject(asMap(json, "first level of document must be an object"));
    }

    @SuppressWarnings("unchecked")
    private void parseTopLevelObject(EconomicMap<String, Object> obj) {
        Object resourcesObject = null;
        Object bundlesObject = null;
        Object globsObject = null;
        MapCursor<String, Object> cursor = obj.getEntries();
        while (cursor.advance()) {
            if ("resources".equals(cursor.getKey())) {
                resourcesObject = cursor.getValue();
            } else if ("bundles".equals(cursor.getKey())) {
                bundlesObject = cursor.getValue();
            } else if ("globs".equals(cursor.getKey())) {
                globsObject = cursor.getValue();
            }
        }

        if (globsObject != null) {
            List<Object> globs = asList(globsObject, "Attribute 'globs' must be a list of glob patterns");
            for (Object object : globs) {
                parseGlobEntry(object, registry::addResources);
            }
        }

        if (resourcesObject != null) {
            if (resourcesObject instanceof EconomicMap) { // New format
                EconomicMap<String, Object> resourcesObjectMap = (EconomicMap<String, Object>) resourcesObject;
                checkAttributes(resourcesObjectMap, "resource descriptor object", Collections.singleton("includes"), Collections.singleton("excludes"));
                Object includesObject = resourcesObjectMap.get("includes");
                Object excludesObject = resourcesObjectMap.get("excludes");

                List<Object> includes = asList(includesObject, "Attribute 'includes' must be a list of resources");
                for (Object object : includes) {
                    parseStringEntry(object, "pattern", registry::addResources, "resource descriptor object", "'includes' list");
                }

                if (excludesObject != null) {
                    List<Object> excludes = asList(excludesObject, "Attribute 'excludes' must be a list of resources");
                    for (Object object : excludes) {
                        parseStringEntry(object, "pattern", registry::ignoreResources, "resource descriptor object", "'excludes' list");
                    }
                }
            } else { // Old format: may be deprecated in future versions
                List<Object> resources = asList(resourcesObject, "Attribute 'resources' must be a list of resources");
                for (Object object : resources) {
                    parseStringEntry(object, "pattern", registry::addResources, "resource descriptor object", "'resources' list");
                }
            }
        }
        if (bundlesObject != null) {
            List<Object> bundles = asList(bundlesObject, "Attribute 'bundles' must be a list of bundles");
            for (Object bundle : bundles) {
                parseBundle(bundle);
            }
        }
    }

    private void parseBundle(Object bundle) {
        EconomicMap<String, Object> resource = asMap(bundle, "Elements of 'bundles' list must be a bundle descriptor object");
        checkAttributes(resource, "bundle descriptor object", Collections.singletonList("name"), Arrays.asList("locales", "classNames", "condition"));
        String basename = asString(resource.get("name"));
        ConfigurationCondition condition = parseCondition(resource);
        Object locales = resource.get("locales");
        if (locales != null) {
            List<Locale> asList = asList(locales, "Attribute 'locales' must be a list of locales")
                            .stream()
                            .map(ResourceConfigurationParser::parseLocale)
                            .collect(Collectors.toList());
            if (!asList.isEmpty()) {
                registry.addResourceBundles(condition, basename, asList);
            }

        }
        Object classNames = resource.get("classNames");
        if (classNames != null) {
            List<Object> asList = asList(classNames, "Attribute 'classNames' must be a list of classes");
            for (Object o : asList) {
                String className = asString(o);
                registry.addClassBasedResourceBundle(condition, basename, className);
            }
        }
        if (locales == null && classNames == null) {
            /* If nothing more precise is specified, register in every included locale */
            registry.addResourceBundles(condition, basename);
        }
    }

    private static Locale parseLocale(Object input) {
        String localeTag = asString(input);
        Locale locale = LocalizationSupport.parseLocaleFromTag(localeTag);
        if (locale == null) {
            throw new JSONParserException(localeTag + " is not a valid locale tag");
        }
        return locale;
    }

    private void parseStringEntry(Object data, String valueKey, BiConsumer<ConfigurationCondition, String> resourceRegistry, String expectedType, String parentType) {
        EconomicMap<String, Object> resource = asMap(data, "Elements of " + parentType + " must be a " + expectedType);
        checkAttributes(resource, "resource and resource bundle descriptor object", Collections.singletonList(valueKey), Collections.singletonList(CONDITIONAL_KEY));
        ConfigurationCondition condition = parseCondition(resource);
        Object valueObject = resource.get(valueKey);
        String value = asString(valueObject, valueKey);
        resourceRegistry.accept(condition, value);
    }

    private void parseGlobEntry(Object data, BiConsumer<ConfigurationCondition, String> resourceRegistry) {
        EconomicMap<String, Object> globObject = asMap(data, "Elements of 'globs' list must be glob descriptor objects");
        checkAttributes(globObject, "resource and resource bundle descriptor object", Collections.singletonList(GLOB_KEY), List.of(CONDITIONAL_KEY, MODULE_KEY));
        ConfigurationCondition condition = parseCondition(globObject);

        Object moduleObject = globObject.get(MODULE_KEY);
        String module = moduleObject == null ? "" : asString(moduleObject);

        Object valueObject = globObject.get(GLOB_KEY);
        String value = asString(valueObject, GLOB_KEY);
        resourceRegistry.accept(condition, globToRegex(module, value));
    }

    public static String globToRegex(String module, String glob) {
        return (module == null || module.isEmpty() ? "" : module + ":") + globToRegex(glob);
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder();

        int quoteStartIndex = 0;
        Wildcard currentWildcard = Wildcard.START;
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (currentWildcard.couldAdvance(c)) {
                if (currentWildcard.isCycleStart() && quoteStartIndex != i) {
                    /* we start processing a new wildcard so quote previous content */
                    sb.append(quoteValue(glob.substring(quoteStartIndex, i)));
                }

                if (currentWildcard.isCycleEnd()) {
                    /* dump content because cycle ends and prepare for the new wildcard */
                    sb.append(currentWildcard.convertToRegex());
                    currentWildcard = currentWildcard.startNewCycle();
                }

                currentWildcard = currentWildcard.next();
                quoteStartIndex = i + 1;
                continue;
            }

            /* we processed whole wildcard so append it and prepare for the new wildcard */
            sb.append(currentWildcard.convertToRegex());
            currentWildcard = currentWildcard.startNewCycle();
        }

        if (quoteStartIndex < glob.length()) {
            /* we have something after last wildcard that is not quoted */
            sb.append(quoteValue(glob.substring(quoteStartIndex)));
        } else if (!currentWildcard.isCycleStart()) {
            /* we have some wildcard at the end that is not appended */
            sb.append(currentWildcard.convertToRegex());
        }

        return sb.toString();
    }

    /**
     * This enum acts like a state machine that helps to identify glob wildcards. Since one star can
     * be followed by another, we are starting a new cycle to track which wildcard will be generated
     * in the end.
     *
     * We can check if the next character can advance the current cycle by calling
     * {@link #couldAdvance}. If we can advance in the cycle, we should call {@link #next} to get
     * next state of the cycle. Once we can't proceed with the given character, cycle for this
     * wildcard ends, so we should use {@link #convertToRegex} to transform current wildcard into
     * regex. Whenever previous cycle is finished, we should prepare the state machine for the next
     * possible wildcard by calling {@link #startNewCycle}.
     */
    private enum Wildcard {
        START,
        STAR,
        DOUBLE_STAR,
        DOUBLE_STAR_SLASH;

        public boolean couldAdvance(char c) {
            return c == '*' || (this.equals(Wildcard.DOUBLE_STAR) && c == '/');
        }

        public Wildcard next() {
            return values()[(this.ordinal() + 1) % values().length];
        }

        public Wildcard startNewCycle() {
            return Wildcard.START;
        }

        public boolean isCycleStart() {
            return this.equals(Wildcard.START);
        }

        public boolean isCycleEnd() {
            return this.equals(Wildcard.DOUBLE_STAR_SLASH);
        }

        public String convertToRegex() {
            switch (this) {
                case START -> {
                    return "";
                }
                case STAR -> {
                    return "[^/]*";
                }
                case DOUBLE_STAR -> {
                    return ".*";
                }
                case DOUBLE_STAR_SLASH -> {
                    return "[^/]*(/|$))*";
                }

                default -> throw VMError.shouldNotReachHere("Unsupported conversion of: " + this);
            }
        }
    }

    private static String quoteValue(String value) {
        return "\\Q" + value + "\\E";
    }
}
