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
package com.oracle.svm.core.configure;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.oracle.svm.core.util.json.JSONParser;

public class ResourceConfigurationParser extends ConfigurationParser {
    private final ResourcesRegistry registry;

    public <T> ResourceConfigurationParser(ResourcesRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void parseAndRegister(Reader reader) throws IOException {
        JSONParser parser = new JSONParser(reader);
        Object json = parser.parse();
        parseTopLevelObject(asMap(json, "first level of document must be an object"));
    }

    @SuppressWarnings("unchecked")
    private void parseTopLevelObject(Map<String, Object> obj) {
        Object resourcesObject = null;
        Object bundlesObject = null;
        for (Map.Entry<String, Object> pair : obj.entrySet()) {
            if ("resources".equals(pair.getKey())) {
                resourcesObject = pair.getValue();
            } else if ("bundles".equals(pair.getKey())) {
                bundlesObject = pair.getValue();
            }
        }
        if (resourcesObject != null) {
            if (resourcesObject instanceof Map) { // New format
                Map<String, Object> resourcesObjectMap = (Map<String, Object>) resourcesObject;
                checkAttributes(resourcesObjectMap, "resource descriptor object", Collections.singleton("includes"), Collections.singleton("excludes"));
                Object includesObject = resourcesObjectMap.get("includes");
                Object excludesObject = resourcesObjectMap.get("excludes");

                List<Object> includes = asList(includesObject, "Attribute 'includes' must be a list of resources");
                for (Object object : includes) {
                    parseEntry(object, "pattern", registry::addResources, "resource descriptor object", "'includes' list");
                }

                if (excludesObject != null) {
                    List<Object> excludes = asList(excludesObject, "Attribute 'excludes' must be a list of resources");
                    for (Object object : excludes) {
                        parseEntry(object, "pattern", registry::ignoreResources, "resource descriptor object", "'excludes' list");
                    }
                }
            } else { // Old format: may be deprecated in future versions
                List<Object> resources = asList(resourcesObject, "Attribute 'resources' must be a list of resources");
                for (Object object : resources) {
                    parseEntry(object, "pattern", registry::addResources, "resource descriptor object", "'resources' list");
                }
            }
        }
        if (bundlesObject != null) {
            List<Object> bundles = asList(bundlesObject, "Attribute 'bundles' must be a list of bundles");
            for (Object object : bundles) {
                parseEntry(object, "name", registry::addResourceBundles, "bundle descriptor object", "'bundles' list");
            }
        }
    }

    private void parseEntry(Object data, String valueKey, Consumer<String> resourceRegistry, String expectedType, String parentType) {
        Map<String, Object> resource = asMap(data, "Elements of " + parentType + " must be a " + expectedType);
        checkAttributes(resource, "resource and resource bundle descriptor object", Collections.singleton(valueKey));
        Object valueObject = resource.get(valueKey);
        String value = asString(valueObject, valueKey);
        resourceRegistry.accept(value);
    }
}
