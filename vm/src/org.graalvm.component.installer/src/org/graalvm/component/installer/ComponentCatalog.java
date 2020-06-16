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
package org.graalvm.component.installer;

import java.util.Set;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.remote.FileDownloader;

/**
 *
 * @author sdedic
 */
public interface ComponentCatalog extends ComponentCollection {
    ComponentInfo findComponentMatch(String id, Version.Match vmatch, boolean localOnly, boolean exact);

    /**
     * Attempts to resolve dependencies or create dependency closure. The 'installed' parameter
     * controls the search mode:
     * <ul>
     * <li>{@code true}: only search among installed components. Any unresolved dependencies are
     * reported.
     * <li>{@code false}: do not report components, which are already installed.
     * <li>{@code null}: report both installed and uninstalled components.
     * </ul>
     * 
     * @param installed controls handling for installed components.
     * @param start the starting point
     * @param closure if true, makes complete closure of dependencies. False inspects only 1st level
     *            dependencies.
     * @param result set of dependencies.
     * @return will contain ids whose Components could not be found. {@code null} is returned
     *         instead of empty collection for easier test.
     */
    Set<String> findDependencies(ComponentInfo start, boolean closure, Boolean installed, Set<ComponentInfo> result);

    DownloadInterceptor getDownloadInterceptor();

    /**
     * @return True, if emote catalogs are enabled.
     */
    boolean isRemoteEnabled();

    public interface DownloadInterceptor {
        /**
         * Configures the downloader, as appropriate for the catalog item. Note that the Catalog may
         * reject configuration for a ComponentInfo it knows nothing about - will return
         * {@code null}
         * 
         * @param info component for which the Downloader should be configured
         * @param dn the downloader instance
         * @return the configured Downloader or {@code null}, if the ComponentInfo is not known.
         */
        FileDownloader processDownloader(ComponentInfo info, FileDownloader dn);
    }
}
