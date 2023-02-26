/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.component.installer.gds.rest;

import org.graalvm.component.installer.ComponentCatalog.DownloadInterceptor;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.AbstractCatalogStorage;
import org.graalvm.component.installer.remote.FileDownloader;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author odouda
 */
class GDSCatalogStorage extends AbstractCatalogStorage implements DownloadInterceptor {
    private final Map<String, Set<ComponentInfo>> components;

    GDSCatalogStorage(ComponentRegistry localRegistry, Feedback feedback, URL baseURL, Collection<ComponentInfo> artifacts) {
        super(localRegistry, feedback, baseURL);
        components = buildComponentsMap(artifacts);
    }

    @Override
    public Set<String> listComponentIDs() throws IOException {
        return components.keySet();
    }

    @Override
    public Set<ComponentInfo> loadComponentMetadata(String id) throws IOException {
        return components.get(id);
    }

    @Override
    public FileDownloader processDownloader(ComponentInfo info, FileDownloader dn) {
        return dn;
    }

    private static Map<String, Set<ComponentInfo>> buildComponentsMap(Collection<ComponentInfo> artifacts) {
        Map<String, Set<ComponentInfo>> comps = new HashMap<>();
        for (ComponentInfo info : artifacts) {
            comps.computeIfAbsent(info.getId(), i -> new HashSet<>()).add(info);
        }
        return comps;
    }

    Map<String, Set<ComponentInfo>> getComponents() {
        return components;
    }
}
