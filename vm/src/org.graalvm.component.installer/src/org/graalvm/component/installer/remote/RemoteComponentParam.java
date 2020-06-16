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
package org.graalvm.component.installer.remote;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.graalvm.component.installer.Archive;
import org.graalvm.component.installer.ComponentParam;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.InstallerStopException;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.MetadataLoader;

/**
 *
 * @author sdedic
 */
public abstract class RemoteComponentParam implements ComponentParam, MetadataLoader {
    private final URL remoteURL;
    private final String dispName;
    private final String spec;
    private final Feedback feedback;
    private final ComponentInfo catalogInfo;
    private ComponentInfo fileInfo;
    private final boolean progress;
    private boolean verifyJars;
    private MetadataLoader fileLoader;
    private boolean complete;

    protected RemoteComponentParam(ComponentInfo catalogInfo, String dispName, String spec, Feedback feedback, boolean progress) {
        this.catalogInfo = catalogInfo;
        this.dispName = dispName;
        this.spec = spec;
        this.feedback = feedback.withBundle(RemoteComponentParam.class);
        this.progress = progress;
        this.remoteURL = catalogInfo.getRemoteURL();
    }

    protected RemoteComponentParam(URL remoteURL, String dispName, String spec, Feedback feedback, boolean progress) {
        this.catalogInfo = null;
        this.dispName = dispName;
        this.spec = spec;
        this.feedback = feedback.withBundle(RemoteComponentParam.class);
        this.remoteURL = remoteURL;
        this.progress = progress;
    }

    @Override
    public String getSpecification() {
        return spec;
    }

    @Override
    public String getDisplayName() {
        return dispName;
    }

    protected ComponentInfo getCatalogInfo() {
        return catalogInfo;
    }

    @Override
    public MetadataLoader createMetaLoader() throws IOException {
        if (catalogInfo != null) {
            return this;
        } else {
            return createFileLoader();
        }
    }

    public void setVerifyJars(boolean verifyJars) {
        this.verifyJars = verifyJars;
    }

    private Path localPath;

    @Override
    public MetadataLoader createFileLoader() throws IOException {
        if (fileLoader != null) {
            return fileLoader;
        }
        return fileLoader = new DelegateMetaLoader(
                        metadataFromLocal(downloadLocalFile()));
    }

    /**
     * The delegate will ensure the ComponentParam will start to serve LOCAL ComponentInfo just
     * after the delegate metaloader creates it.
     */
    class DelegateMetaLoader implements MetadataLoader {
        private final MetadataLoader delegate;

        DelegateMetaLoader(MetadataLoader delegate) {
            this.delegate = delegate;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public ComponentInfo getComponentInfo() {
            fileInfo = delegate.getComponentInfo();
            if (remoteURL != null) {
                fileInfo.setRemoteURL(remoteURL);
            }
            complete = true;
            return fileInfo;
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
    }

    /**
     * Creates a metaloader for the local file.
     * 
     * @param localFile the locally stored file
     * @return loader reading the local file.
     * @throws IOException error during construction of the loader.
     */
    protected abstract MetadataLoader metadataFromLocal(Path localFile) throws IOException;

    private FileDownloader downloader;

    protected final FileDownloader getDownloader() {
        return downloader;
    }

    protected Path downloadLocalFile() throws IOException {
        if (localPath != null && Files.isReadable(localPath)) {
            return localPath;
        }
        try {
            FileDownloader dn = createDownloader();
            if (catalogInfo != null) {
                dn.setShaDigest(catalogInfo.getShaDigest());
            }
            dn.setDisplayProgress(progress);
            dn.download();
            localPath = dn.getLocalFile().toPath();
            downloader = dn;
        } catch (FileNotFoundException ex) {
            throw feedback.failure("REMOTE_ErrorDownloadingNotExist", ex, spec, remoteURL);
        }
        return localPath;
    }

    protected FileDownloader createDownloader() {
        FileDownloader dn = new FileDownloader(feedback.l10n("REMOTE_ComponentFileLabel", getDisplayName(), getSpecification()), remoteURL, feedback);
        return dn;
    }

    @Override
    public boolean isComplete() {
        return complete;
    }

    @Override
    public void close() throws IOException {
        if (fileLoader != null) {
            fileLoader.close();
        }
        if (localPath != null) {
            try {
                Files.deleteIfExists(localPath);
            } catch (IOException ex) {
                feedback.error("REMOTE_CannotDeleteLocalFile", ex, localPath.toString(), ex.getLocalizedMessage());
            }
        }
    }

    @Override
    public ComponentInfo getComponentInfo() {
        return fileInfo != null ? fileInfo : catalogInfo;
    }

    @Override
    public List<InstallerStopException> getErrors() {
        return Collections.emptyList();
    }

    @Override
    public Archive getArchive() {
        try {
            return createFileLoader().getArchive();
        } catch (IOException ex) {
            throw feedback.failure("REMOTE_ErrorDownloadingComponent", ex, spec, remoteURL, ex.getLocalizedMessage());
        }
    }

    @Override
    public String getLicenseType() {
        if (catalogInfo != null) {
            return catalogInfo.getLicenseType();
        } else {
            return null;
        }
    }

    @Override
    public String getLicenseID() {
        try {
            return createFileLoader().getLicenseID();
        } catch (IOException ex) {
            throw feedback.failure("REMOTE_ErrorDownloadingComponent", ex, spec, remoteURL, ex.getLocalizedMessage());
        }
    }

    @Override
    public String getLicensePath() {
        if (catalogInfo != null) {
            String lt = catalogInfo.getLicenseType();
            String path = catalogInfo.getLicensePath();
            if (lt == null || path != null) {
                return path;
            }
        }
        try {
            return createFileLoader().getLicensePath();
        } catch (IOException ex) {
            throw feedback.failure("REMOTE_ErrorDownloadingComponent", ex, spec, remoteURL, ex.getLocalizedMessage());
        }
    }

    @Override
    public MetadataLoader infoOnly(boolean only) {
        return this;
    }

    @Override
    public boolean isNoVerifySymlinks() {
        return true;
    }

    @Override
    public void loadPaths() {
    }

    @Override
    public Map<String, String> loadPermissions() throws IOException {
        return null;
    }

    @Override
    public Map<String, String> loadSymlinks() throws IOException {
        return null;
    }

    @Override
    public void setNoVerifySymlinks(boolean noVerifySymlinks) {
    }

    @Override
    public String getFullPath() {
        return remoteURL.toString();
    }

    @Override
    public String getShortName() {
        return getComponentInfo().getId();
    }

    protected String getSpec() {
        return spec;
    }

    protected Feedback getFeedback() {
        return feedback;
    }

    protected boolean isProgress() {
        return progress;
    }

    protected boolean isVerifyJars() {
        return verifyJars;
    }

    protected Path getLocalPath() {
        return localPath;
    }
}
