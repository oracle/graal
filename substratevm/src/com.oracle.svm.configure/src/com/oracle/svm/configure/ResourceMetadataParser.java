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
package com.oracle.svm.configure;

import java.net.URI;
import java.util.EnumSet;
import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.svm.configure.config.conditional.ConfigurationConditionResolver;

final class ResourceMetadataParser<C> extends ResourceConfigurationParser<C> {
    ResourceMetadataParser(ConfigurationConditionResolver<C> conditionResolver, ResourcesRegistry<C> registry, EnumSet<ConfigurationParserOption> parserOptions) {
        super(conditionResolver, registry, parserOptions);
    }

    @Override
    public void parseAndRegister(Object json, URI origin) {
        Object resourcesJson = getFromGlobalFile(json, RESOURCES_KEY);
        if (resourcesJson != null) {
            List<Object> globsAndBundles = asList(resourcesJson, "'resources' section must be a list of glob pattern or bundle descriptors");
            for (Object object : globsAndBundles) {
                EconomicMap<String, Object> globOrBundle = asMap(object, "Elements of 'resources' list must be glob pattern or bundle descriptor objects");
                if (globOrBundle.containsKey(GLOB_KEY)) {
                    parseGlobEntry(object, (condition, module, glob) -> registry.addGlob(condition, module, glob, origin));
                } else if (globOrBundle.containsKey(BUNDLE_KEY)) {
                    parseBundle(globOrBundle, true);
                }
            }
        }
        Object bundlesJson = getFromGlobalFile(json, BUNDLES_KEY);
        if (bundlesJson != null) {
            parseBundlesObject(bundlesJson);
        }
    }

    @Override
    protected UnresolvedConfigurationCondition parseCondition(EconomicMap<String, Object> condition) {
        return parseCondition(condition, true);
    }
}
