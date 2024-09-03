/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

final class LegacyResourceConfigurationParser<C> extends ResourceConfigurationParser<C> {
    LegacyResourceConfigurationParser(ConfigurationConditionResolver<C> conditionResolver, ResourcesRegistry<C> registry, boolean strictConfiguration) {
        super(conditionResolver, registry, strictConfiguration);
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        parseTopLevelObject(asMap(json, "first level of document must be an object"));
    }

    private void parseTopLevelObject(EconomicMap<String, Object> obj) {
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
            parseResourcesObject(resourcesObject);
        }
        if (bundlesObject != null) {
            parseBundlesObject(bundlesObject);
        }
        if (globsObject != null) {
            parseGlobsObject(globsObject);
        }
    }
}
