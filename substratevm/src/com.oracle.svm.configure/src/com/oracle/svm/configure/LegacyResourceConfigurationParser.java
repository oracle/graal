/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure;

import java.net.URI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import com.oracle.svm.configure.config.conditional.ConfigurationConditionResolver;
import com.oracle.svm.util.GlobUtils;
import com.oracle.svm.util.TypeResult;

final class LegacyResourceConfigurationParser<C> extends ResourceConfigurationParser<C> {
    LegacyResourceConfigurationParser(ConfigurationConditionResolver<C> conditionResolver, ResourcesRegistry<C> registry, EnumSet<ConfigurationParserOption> parserOptions) {
        super(conditionResolver, registry, parserOptions);
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        parseTopLevelObject(asMap(json, "first level of document must be an object"), origin);
    }

    private void parseTopLevelObject(EconomicMap<String, Object> obj, Object origin) {
        Object resourcesObject = null;
        Object bundlesObject = null;
        Object globsObject = null;
        MapCursor<String, Object> cursor = obj.getEntries();
        while (cursor.advance()) {
            if (RESOURCES_KEY.equals(cursor.getKey())) {
                resourcesObject = cursor.getValue();
            } else if (BUNDLES_KEY.equals(cursor.getKey())) {
                bundlesObject = cursor.getValue();
            } else if (GLOBS_KEY.equals(cursor.getKey())) {
                globsObject = cursor.getValue();
            }
        }

        if (resourcesObject != null) {
            parseResourcesObject(resourcesObject, origin);
        }
        if (bundlesObject != null) {
            parseBundlesObject(bundlesObject);
        }
        if (globsObject != null) {
            parseGlobsObject(globsObject, origin);
        }
    }

    @Override
    protected UnresolvedConfigurationCondition parseCondition(EconomicMap<String, Object> condition) {
        return parseCondition(condition, false);
    }

    @SuppressWarnings("unchecked")
    private void parseResourcesObject(Object resourcesObject, Object origin) {
        if (resourcesObject instanceof EconomicMap) { // New format
            EconomicMap<String, Object> resourcesObjectMap = (EconomicMap<String, Object>) resourcesObject;
            checkAttributes(resourcesObjectMap, "resource descriptor object", Collections.singleton("includes"), Collections.singleton("excludes"));
            Object includesObject = resourcesObjectMap.get("includes");
            Object excludesObject = resourcesObjectMap.get("excludes");

            List<Object> includes = asList(includesObject, "Attribute 'includes' must be a list of resources");
            for (Object object : includes) {
                parsePatternEntry(object, (condition, pattern) -> registry.addResources(condition, pattern, origin),
                                (condition, module, glob) -> registry.addGlob(condition, module, glob, origin), "'includes' list");
            }

            if (excludesObject != null) {
                List<Object> excludes = asList(excludesObject, "Attribute 'excludes' must be a list of resources");
                for (Object object : excludes) {
                    parsePatternEntry(object, (condition, pattern) -> registry.ignoreResources(condition, pattern, origin), null, "'excludes' list");
                }
            }
        } else { // Old format: may be deprecated in future versions
            List<Object> resources = asList(resourcesObject, "Attribute 'resources' must be a list of resources");
            for (Object object : resources) {
                parsePatternEntry(object, (condition, pattern) -> registry.addResources(condition, pattern, origin),
                                (condition, module, glob) -> registry.addGlob(condition, module, glob, origin), "'resources' list");
            }
        }
    }

    private void parsePatternEntry(Object data, BiConsumer<C, String> resourceRegistry, GlobPatternConsumer<C> globRegistry, String parentType) {
        EconomicMap<String, Object> resource = asMap(data, "Elements of " + parentType + " must be a resource descriptor object");
        checkAttributes(resource, "regex resource descriptor object", Collections.singletonList("pattern"), Collections.singletonList(CONDITIONAL_KEY));
        TypeResult<C> resolvedConfigurationCondition = conditionResolver.resolveCondition(parseCondition(resource, false));
        if (!resolvedConfigurationCondition.isPresent()) {
            return;
        }

        Object valueObject = resource.get("pattern");
        String value = asString(valueObject, "pattern");

        /* Parse fully literal regex as globs */
        if (value.startsWith("\\Q") && value.endsWith("\\E") && value.indexOf("\\E") == value.lastIndexOf("\\E")) {
            String globValue = value.substring("\\Q".length(), value.length() - "\\E".length());
            if (GlobUtils.validatePattern(globValue).isEmpty()) {
                globRegistry.accept(resolvedConfigurationCondition.get(), null, globValue);
                return;
            }
        }

        resourceRegistry.accept(resolvedConfigurationCondition.get(), value);
    }
}
