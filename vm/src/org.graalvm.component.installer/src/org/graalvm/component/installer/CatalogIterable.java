/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.jar.JarFile;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.ComponentPackageLoader;
import org.graalvm.component.installer.persist.FileDownloader;
import org.graalvm.component.installer.persist.MetadataLoader;

public class CatalogIterable implements ComponentIterable {
    private final CommandInput input;
    private final Feedback feedback;
    private final Supplier<ComponentRegistry> registrySupplier;
    private ComponentRegistry remoteRegistry;
    private boolean verifyJars;

    public CatalogIterable(CommandInput input, Feedback feedback, Supplier<ComponentRegistry> remote) {
        this.input = input;
        this.feedback = feedback;
        this.registrySupplier = remote;
    }

    public boolean isVerifyJars() {
        return verifyJars;
    }

    @Override
    public void setVerifyJars(boolean verifyJars) {
        this.verifyJars = verifyJars;
    }

    @Override
    public Iterator<ComponentParam> iterator() {
        return new It();
    }

    ComponentRegistry getRegistry() {
        if (remoteRegistry == null) {
            remoteRegistry = registrySupplier.get();
        }
        return remoteRegistry;
    }

    private class It implements Iterator<ComponentParam> {

        @Override
        public boolean hasNext() {
            return input.hasParameter();
        }

        @Override
        public ComponentParam next() {
            String s = input.nextParameter();

            if (getRegistry().findComponent(s.toLowerCase()) == null) {
                throw feedback.failure("REMOTE_UnknownComponentId", null, s);
            }

            ComponentInfo info = getRegistry().loadSingleComponent(s.toLowerCase(), false);
            if (info == null) {
                throw feedback.failure("REMOTE_UnknownComponentId", null, s);
            }
            boolean progress = input.optValue(Commands.OPTION_NO_DOWNLOAD_PROGRESS) == null;
            RemoteComponentParam param = new RemoteComponentParam(
                            info,
                            feedback.l10n("REMOTE_ComponentFileLabel", s),
                            s,
                            feedback, progress);
            param.setVerifyJars(verifyJars);
            return param;
        }
    }

    static class RemoteComponentParam implements ComponentParam, MetadataLoader {
        private final URL remoteURL;
        private final String dispName;
        private final String spec;
        private final Feedback feedback;
        private final ComponentInfo catalogInfo;
        private ComponentInfo fileInfo;
        private final boolean progress;

        private boolean verifyJars;
        private JarFile file;
        private MetadataLoader fileLoader;
        private boolean complete;

        RemoteComponentParam(ComponentInfo catalogInfo, String dispName, String spec, Feedback feedback, boolean progress) {
            this.catalogInfo = catalogInfo;
            this.dispName = dispName;
            this.spec = spec;
            this.feedback = feedback;
            this.progress = progress;
            this.remoteURL = catalogInfo.getRemoteURL();
        }

        RemoteComponentParam(URL remoteURL, String dispName, String spec, Feedback feedback, boolean progress) {
            this.catalogInfo = null;
            this.dispName = dispName;
            this.spec = spec;
            this.feedback = feedback;
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

        @Override
        public MetadataLoader createFileLoader() throws IOException {
            if (fileLoader != null) {
                return fileLoader;
            }

            JarFile f = getFile();
            fileLoader = new ComponentPackageLoader(f, feedback) {
                @Override
                public ComponentInfo createComponentInfo() {
                    ComponentInfo i = super.createComponentInfo();
                    i.setRemoteURL(remoteURL);
                    complete = true;
                    fileInfo = i;
                    return i;
                }

            };
            this.file = f;
            return fileLoader;
        }

        @Override
        public JarFile getFile() throws IOException {
            if (file != null) {
                return file;
            }
            FileDownloader dn = new FileDownloader(
                            feedback.l10n("REMOTE_ComponentFileLabel", spec),
                            remoteURL, feedback);
            if (catalogInfo != null) {
                dn.setShaDigest(catalogInfo.getShaDigest());
            }
            dn.setDisplayProgress(progress);
            try {
                dn.download();
                file = new JarFile(dn.getLocalFile(), verifyJars);
            } catch (FileNotFoundException ex) {
                throw feedback.failure("REMOTE_ErrorDownloadingNotExist", ex, spec, remoteURL);
            }
            return file;
        }

        @Override
        public boolean isComplete() {
            return complete;
        }

        @Override
        public void close() throws IOException {
            if (fileLoader != null) {
                fileLoader.close();
            } else if (file != null) {
                file.close();
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
        public JarFile getJarFile() {
            try {
                return getFile();
            } catch (IOException ex) {
                throw feedback.failure("REMOTE_ErrorDownloadingComponent", ex, spec, remoteURL, ex.getLocalizedMessage());
            }
        }

        @Override
        public String getLicensePath() {
            return null;
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
            return remoteURL.getFile();
        }
    }
}
