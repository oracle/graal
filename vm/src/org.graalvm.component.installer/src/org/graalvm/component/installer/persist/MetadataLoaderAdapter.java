/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.graalvm.component.installer.Archive;
import org.graalvm.component.installer.InstallerStopException;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.remote.FileDownloader;

/**
 * Delegation adapter, for easier wrapping a MetadataLoader instance.
 * 
 * @author sdedic
 */
public class MetadataLoaderAdapter implements MetadataLoader {
    private final MetadataLoader delegate;

    public MetadataLoaderAdapter(MetadataLoader delegate) {
        this.delegate = delegate;
    }

    @Override
    public ComponentInfo getComponentInfo() {
        return delegate.getComponentInfo();
    }

    @Override
    public List<InstallerStopException> getErrors() {
        return delegate.getErrors();
    }

    @Override
    public Archive getArchive() throws IOException {
        return delegate.getArchive();
    }

    @Override
    public String getLicenseType() {
        return delegate.getLicenseType();
    }

    @Override
    public String getLicenseID() {
        return delegate.getLicenseID();
    }

    @Override
    public String getLicensePath() {
        return delegate.getLicensePath();
    }

    @Override
    public MetadataLoader infoOnly(boolean only) {
        return delegate.infoOnly(only);
    }

    @Override
    public boolean isNoVerifySymlinks() {
        return delegate.isNoVerifySymlinks();
    }

    @Override
    public void loadPaths() throws IOException {
        delegate.loadPaths();
    }

    @Override
    public Map<String, String> loadPermissions() throws IOException {
        return delegate.loadPermissions();
    }

    @Override
    public Map<String, String> loadSymlinks() throws IOException {
        return delegate.loadSymlinks();
    }

    @Override
    public void setNoVerifySymlinks(boolean noVerifySymlinks) {
        delegate.setNoVerifySymlinks(noVerifySymlinks);
    }

    @Override
    public ComponentInfo completeMetadata() throws IOException {
        return delegate.completeMetadata();
    }

    @Override
    public FileDownloader configureRelatedDownloader(FileDownloader dn) {
        return delegate.configureRelatedDownloader(dn);
    }

    @Override
    public Boolean recordLicenseAccepted(ComponentInfo info, String licenseID, String licenseText, Date d) throws IOException {
        return delegate.recordLicenseAccepted(info, licenseID, licenseText, d);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public Date isLicenseAccepted(ComponentInfo info, String licenseID) {
        return delegate.isLicenseAccepted(info, licenseID);
    }
}
