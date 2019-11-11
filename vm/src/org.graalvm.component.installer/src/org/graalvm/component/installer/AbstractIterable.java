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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.graalvm.component.installer.ComponentCatalog.DownloadInterceptor;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.remote.CatalogIterable;
import org.graalvm.component.installer.remote.FileDownloader;
import org.graalvm.component.installer.remote.RemoteComponentParam;

/**
 * Base class for the different commandline parameters.
 * 
 * @author sdedic
 */
abstract class AbstractIterable implements ComponentIterable {
    protected final CommandInput input;
    protected final Feedback feedback;
    private boolean verifyJars;
    private CommandInput.CatalogFactory remoteFactory;
    private ComponentCatalog remoteCatalog;

    protected AbstractIterable(CommandInput input, Feedback feedback) {
        this.input = input;
        this.feedback = feedback;
    }

    public boolean isVerifyJars() {
        return verifyJars;
    }

    @Override
    public void setVerifyJars(boolean verifyJars) {
        this.verifyJars = verifyJars;
    }

    public void setCatalogFactory(CommandInput.CatalogFactory cFactory) {
        this.remoteFactory = cFactory;
    }

    private ComponentCatalog getRemoteContents() {
        if (remoteCatalog != null) {
            return remoteCatalog;
        }
        if (remoteFactory != null) {
            remoteCatalog = remoteFactory.createComponentCatalog(input, input.getLocalRegistry());
        } else {
            remoteCatalog = new NullCatalog();
        }
        return remoteCatalog;
    }

    @Override
    public ComponentIterable matchVersion(Version.Match m) {
        return this;
    }

    @Override
    public ComponentIterable allowIncompatible() {
        return this;
    }

    @Override
    public ComponentParam createParam(String cmdString, ComponentInfo info) {
        RemoteComponentParam param = new CatalogIterable.CatalogItemParam(
                        getRemoteContents().getDownloadInterceptor(),
                        info,
                        info.getName(),
                        cmdString,
                        feedback,
                        input.optValue(Commands.OPTION_NO_DOWNLOAD_PROGRESS) == null);
        param.setVerifyJars(verifyJars);
        return param;
    }

    private static class NullCatalog implements ComponentCatalog, DownloadInterceptor {

        @Override
        public ComponentInfo findComponentMatch(String id, Version.Match vmatch, boolean localOnly, boolean exact) {
            return null;
        }

        @Override
        public Set<String> findDependencies(ComponentInfo start, boolean closure, Boolean installed, Set<ComponentInfo> result) {
            return new HashSet<>(start.getDependencies());
        }

        @Override
        public FileDownloader processDownloader(ComponentInfo info, FileDownloader dn) {
            return dn;
        }

        @Override
        public DownloadInterceptor getDownloadInterceptor() {
            return this;
        }

        @Override
        public void setAllowDistUpdate(boolean distUpgrade) {
        }

        @Override
        public ComponentInfo findComponentMatch(String id, Version.Match vm, boolean exact) {
            return null;
        }

        @Override
        public String shortenComponentId(ComponentInfo info) {
            return info.getId();
        }

        @Override
        public Collection<String> getComponentIDs() {
            return Collections.emptyList();
        }

        @Override
        public Collection<ComponentInfo> loadComponents(String id, Version.Match selector, boolean filelist) {
            return Collections.emptySet();
        }

        @Override
        public boolean isRemoteEnabled() {
            return false;
        }
    }
}
