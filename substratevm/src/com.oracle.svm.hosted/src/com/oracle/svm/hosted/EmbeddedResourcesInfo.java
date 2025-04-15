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
package com.oracle.svm.hosted;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.jdk.Resources;

@Platforms(Platform.HOSTED_ONLY.class)
public class EmbeddedResourcesInfo {

    record SourceAndOrigin(String source, Object origin) {
    }

    private final ConcurrentHashMap<Resources.ModuleResourceKey, List<SourceAndOrigin>> registeredResources = new ConcurrentHashMap<>();

    public ConcurrentHashMap<Resources.ModuleResourceKey, List<SourceAndOrigin>> getRegisteredResources() {
        return registeredResources;
    }

    public static EmbeddedResourcesInfo singleton() {
        return ImageSingletons.lookup(EmbeddedResourcesInfo.class);
    }

    public void declareResourceAsRegistered(Module module, String resource, String source, Object origin) {
        if (!ImageSingletons.lookup(ResourcesFeature.class).collectEmbeddedResourcesInfo()) {
            return;
        }

        Resources.ModuleResourceKey key = Resources.createStorageKey(module, resource);

        /*
         * If we already have an entry with this key, and it was a NEGATIVE_QUERY, the new resource
         * would either be ignored if it was another NEGATIVE_QUERY or it will override existing
         * NEGATIVE_QUERY for that key. In both cases we can remove previous NEGATIVE_QUERY since it
         * will be replaced with either new NEGATIVE_QUERY or with resource that exists.
         */
        List<SourceAndOrigin> existingEntries = registeredResources.get(key);
        if (existingEntries != null && existingEntries.size() == 1 && isNegativeQuery(existingEntries.get(0))) {
            registeredResources.remove(key);
        }

        registeredResources.compute(key, (k, v) -> {
            if (v == null) {
                ArrayList<SourceAndOrigin> newValue = new ArrayList<>();
                newValue.add(new SourceAndOrigin(source, origin));
                return newValue;
            }

            /*
             * We have to avoid duplicated sources here. In case when declaring resource that comes
             * from module as registered, we don't have information whether the resource is already
             * registered or not. That check is performed later in {@link Resources.java#addEntry},
             * so we have to perform same check here, to avoid duplicates when collecting
             * information about resource.
             */
            boolean found = false;
            for (var existing : v) {
                if (existing.source.equals(source)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                v.add(new SourceAndOrigin(source, origin));
            }
            return v;
        });
    }

    private static boolean isNegativeQuery(SourceAndOrigin entry) {
        return entry.source().equalsIgnoreCase("") && ((String) entry.origin()).equalsIgnoreCase("");
    }
}
