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
package com.oracle.svm.core.configure;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.core.TypeResult;
import com.oracle.svm.core.jdk.localization.LocalizationSupport;

import jdk.graal.compiler.util.json.JsonParserException;

public abstract class ResourceConfigurationParser<C> extends ConfigurationParser {
    protected final ResourcesRegistry<C> registry;

    protected final ConfigurationConditionResolver<C> conditionResolver;

    public static <C> ResourceConfigurationParser<C> create(boolean strictMetadata, ConfigurationConditionResolver<C> conditionResolver, ResourcesRegistry<C> registry, boolean strictConfiguration) {
        if (strictMetadata) {
            return new ResourceMetadataParser<>(conditionResolver, registry, strictConfiguration);
        } else {
            return new LegacyResourceConfigurationParser<>(conditionResolver, registry, strictConfiguration);
        }
    }

    protected ResourceConfigurationParser(ConfigurationConditionResolver<C> conditionResolver, ResourcesRegistry<C> registry, boolean strictConfiguration) {
        super(strictConfiguration);
        this.registry = registry;
        this.conditionResolver = conditionResolver;
    }

    protected void parseBundlesObject(Object bundlesObject) {
        List<Object> bundles = asList(bundlesObject, "Attribute 'bundles' must be a list of bundles");
        for (Object bundle : bundles) {
            parseBundle(bundle);
        }
    }

    @SuppressWarnings("unchecked")
    protected void parseResourcesObject(Object resourcesObject) {
        if (resourcesObject instanceof EconomicMap) { // New format
            EconomicMap<String, Object> resourcesObjectMap = (EconomicMap<String, Object>) resourcesObject;
            checkAttributes(resourcesObjectMap, "resource descriptor object", Collections.singleton("includes"), Collections.singleton("excludes"));
            Object includesObject = resourcesObjectMap.get("includes");
            Object excludesObject = resourcesObjectMap.get("excludes");

            List<Object> includes = asList(includesObject, "Attribute 'includes' must be a list of resources");
            for (Object object : includes) {
                parsePatternEntry(object, registry::addResources, "'includes' list");
            }

            if (excludesObject != null) {
                List<Object> excludes = asList(excludesObject, "Attribute 'excludes' must be a list of resources");
                for (Object object : excludes) {
                    parsePatternEntry(object, registry::ignoreResources, "'excludes' list");
                }
            }
        } else { // Old format: may be deprecated in future versions
            List<Object> resources = asList(resourcesObject, "Attribute 'resources' must be a list of resources");
            for (Object object : resources) {
                parsePatternEntry(object, registry::addResources, "'resources' list");
            }
        }
    }

    private void parseBundle(Object bundle) {
        EconomicMap<String, Object> resource = asMap(bundle, "Elements of 'bundles' list must be a bundle descriptor object");
        checkAttributes(resource, "bundle descriptor object", Collections.singletonList("name"), Arrays.asList("locales", "classNames", "condition"));
        String basename = asString(resource.get("name"));
        TypeResult<C> resolvedConfigurationCondition = conditionResolver.resolveCondition(parseCondition(resource, false));
        if (!resolvedConfigurationCondition.isPresent()) {
            return;
        }
        Object locales = resource.get("locales");
        if (locales != null) {
            List<Locale> asList = asList(locales, "Attribute 'locales' must be a list of locales")
                            .stream()
                            .map(ResourceConfigurationParser::parseLocale)
                            .collect(Collectors.toList());
            if (!asList.isEmpty()) {
                registry.addResourceBundles(resolvedConfigurationCondition.get(), basename, asList);
            }

        }
        Object classNames = resource.get("classNames");
        if (classNames != null) {
            List<Object> asList = asList(classNames, "Attribute 'classNames' must be a list of classes");
            for (Object o : asList) {
                String className = asString(o);
                registry.addClassBasedResourceBundle(resolvedConfigurationCondition.get(), basename, className);
            }
        }
        if (locales == null && classNames == null) {
            /* If nothing more precise is specified, register in every included locale */
            registry.addResourceBundles(resolvedConfigurationCondition.get(), basename);
        }
    }

    private static Locale parseLocale(Object input) {
        String localeTag = asString(input);
        Locale locale = LocalizationSupport.parseLocaleFromTag(localeTag);
        if (locale == null) {
            throw new JsonParserException(localeTag + " is not a valid locale tag");
        }
        return locale;
    }

    private void parsePatternEntry(Object data, BiConsumer<C, String> resourceRegistry, String parentType) {
        EconomicMap<String, Object> resource = asMap(data, "Elements of " + parentType + " must be a resource descriptor object");
        checkAttributes(resource, "regex resource descriptor object", Collections.singletonList("pattern"), Collections.singletonList(CONDITIONAL_KEY));
        TypeResult<C> resolvedConfigurationCondition = conditionResolver.resolveCondition(parseCondition(resource, false));
        if (!resolvedConfigurationCondition.isPresent()) {
            return;
        }

        Object valueObject = resource.get("pattern");
        String value = asString(valueObject, "pattern");
        resourceRegistry.accept(resolvedConfigurationCondition.get(), value);
    }

    protected void parseGlobsObject(Object globsObject) {
        List<Object> globs = asList(globsObject, "Attribute 'globs' must be a list of glob patterns");
        for (Object object : globs) {
            parseGlobEntry(object, registry::addGlob);
        }
    }

    private interface GlobPatternConsumer<T> {
        void accept(T a, String b, String c);
    }

    private void parseGlobEntry(Object data, GlobPatternConsumer<C> resourceRegistry) {
        EconomicMap<String, Object> globObject = asMap(data, "Elements of 'globs' list must be a glob descriptor objects");
        checkAttributes(globObject, "glob resource descriptor object", Collections.singletonList(GLOB_KEY), List.of(CONDITIONAL_KEY, MODULE_KEY));
        TypeResult<C> resolvedConfigurationCondition = conditionResolver.resolveCondition(parseCondition(globObject, false));
        if (!resolvedConfigurationCondition.isPresent()) {
            return;
        }

        Object moduleObject = globObject.get(MODULE_KEY);
        String module = asNullableString(moduleObject, MODULE_KEY);

        Object valueObject = globObject.get(GLOB_KEY);
        String value = asString(valueObject, GLOB_KEY);
        resourceRegistry.accept(resolvedConfigurationCondition.get(), module, value);
    }
}
