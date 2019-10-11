/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package org.graalvm.component.installer.persist;

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.MetadataException;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.model.ComponentStorage;

/**
 * Base for different storage of remote component list. The default implementation uses properties
 * resource reachable through the https.
 * 
 * @author sdedic
 */
public abstract class AbstractCatalogStorage implements ComponentStorage {
    protected final ComponentRegistry localRegistry;
    protected final Feedback feedback;
    protected final URL baseURL;

    public AbstractCatalogStorage(ComponentRegistry localRegistry, Feedback feedback, URL baseURL) {
        this.localRegistry = localRegistry;
        this.feedback = feedback;
        this.baseURL = baseURL;
    }

    @Override
    public Map<String, String> loadGraalVersionInfo() {
        return localRegistry.getGraalCapabilities();
    }

    @Override
    public ComponentInfo loadComponentFiles(ComponentInfo ci) throws IOException {
        // files are not supported, yet
        return ci;
    }

    protected byte[] toHashBytes(String comp, String hashS) {
        try {
            return SystemUtils.toHashBytes(hashS);
        } catch (IllegalArgumentException ex) {
            throw new MetadataException(null, feedback.l10n("REMOTE_InvalidHash", comp, ex.getMessage()));
        }
    }
}
