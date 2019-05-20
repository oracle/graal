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
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.oracle.svm.core.util.json.JSONParser;
import com.oracle.svm.core.util.json.JSONParserException;

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

    private void parseTopLevelObject(Map<String, Object> obj) {
        Object resourcesObject = null;
        Object bundlesObject = null;
        for (Map.Entry<String, Object> pair : obj.entrySet()) {
            if ("resources".equals(pair.getKey())) {
                resourcesObject = pair.getValue();
            } else if ("bundles".equals(pair.getKey())) {
                bundlesObject = pair.getValue();
            } else {
                throw new JSONParserException("Unknown attribute '" + pair.getKey() + "' (supported attributes: name) in resource definition");
            }
        }
        if (resourcesObject != null) {
            List<Object> resources = asList(resourcesObject, "Attribute 'resources' must be a list of resources");
            for (Object object : resources) {
                parseEntry(object, "pattern", registry::addResources, "resource descriptor object", "'resources' list");
            }
        }
        if (bundlesObject != null) {
            List<Object> bundles = asList(bundlesObject, "Attribute 'bundles' must be a list of bundles");
            for (Object object : bundles) {
                parseEntry(object, "name", registry::addResourceBundles, "bundle descriptor object", "'bundles' list");
            }
        }
    }

    private static void parseEntry(Object data, String valueKey, Consumer<String> registry, String expectedType, String parentType) {
        Map<String, Object> resource = asMap(data, "Elements of " + parentType + " must be a " + expectedType);
        Object valueObject = null;
        for (Map.Entry<String, Object> pair : resource.entrySet()) {
            if (valueKey.equals(pair.getKey())) {
                valueObject = pair.getValue();
            } else {
                throw new JSONParserException("Unknown attribute '" + pair.getKey() + "' (supported attributes: '" + valueKey + "') in " + expectedType);
            }
        }
        if (valueObject == null) {
            throw new JSONParserException("Missing attribute '" + valueKey + "' in " + expectedType);
        }
        String value = asString(valueObject, valueKey);
        registry.accept(value);
    }
}
