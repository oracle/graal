/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.ComponentCollection;
import org.graalvm.component.installer.ComponentIterable;
import org.graalvm.component.installer.ComponentParam;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.FileIterable;
import org.graalvm.component.installer.FileIterable.FileComponent;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.UnknownVersionException;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.MetadataLoader;

/**
 * Interprets installer arguments as entries from a catalog.
 * 
 * @author sdedic
 */
public class CatalogIterable implements ComponentIterable {
    private final CommandInput input;
    private final Feedback feedback;
    private final SoftwareChannel factory;
    private final ComponentCollection remoteRegistry;
    private boolean verifyJars;
    private boolean incompatible;

    public CatalogIterable(CommandInput input, Feedback feedback, ComponentCollection remoteRegistry, SoftwareChannel fact) {
        this.input = input;
        this.feedback = feedback.withBundle(CatalogIterable.class);
        this.factory = fact;
        this.remoteRegistry = remoteRegistry;
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

    ComponentCollection getRegistry() {
        return remoteRegistry;
    }

    @Override
    public ComponentIterable allowIncompatible() {
        incompatible = true;
        return this;
    }

    private Version.Match versionFilter;

    @Override
    public ComponentIterable matchVersion(Version.Match m) {
        this.versionFilter = m;
        return this;
    }

    private ComponentParam latest(String s, Collection<ComponentInfo> infos) {
        List<ComponentInfo> ordered = new ArrayList<>(infos);
        Collections.sort(ordered, ComponentInfo.versionComparator().reversed());
        boolean progress = input.optValue(Commands.OPTION_NO_DOWNLOAD_PROGRESS) == null;
        return createComponentParam(s, ordered.get(0), progress);
    }

    private class It implements Iterator<ComponentParam> {
        private void thrownUnknown(String fname, boolean throwUnknown) {
            File f = new File(fname);
            if (f.exists() && f.isFile()) {
                throw feedback.failure("REMOTE_UnknownComponentMaybeFile", null, fname);
            } else if (throwUnknown) {
                throw feedback.failure("REMOTE_UnknownComponentId", null, fname);
            }
        }

        @Override
        public boolean hasNext() {
            return input.hasParameter();
        }

        @Override
        public ComponentParam next() {
            String s = input.nextParameter();
            ComponentInfo info;
            try {
                Version.Match[] m = new Version.Match[1];
                String id = Version.idAndVersion(s, m);
                if (m[0].getType() == Version.Match.Type.MOSTRECENT && versionFilter != null) {
                    m[0] = versionFilter;
                }
                try {
                    info = getRegistry().findComponent(id, m[0]);
                } catch (UnknownVersionException ex) {
                    // could not find anything to match the user version against
                    if (ex.getCandidate() == null) {
                        throw feedback.failure("REMOTE_NoSpecificVersion", ex, id, m[0].getVersion().displayString());
                    } else {
                        throw feedback.failure("REMOTE_NoSpecificVersion2", ex, id, m[0].getVersion().displayString(), ex.getCandidate().displayString());
                    }
                }
                if (info == null) {
                    // must be already initialized
                    Version gv = input.getLocalRegistry().getGraalVersion();
                    Version.Match selector = gv.match(Version.Match.Type.INSTALLABLE);
                    Collection<ComponentInfo> infos = remoteRegistry.loadComponents(id, selector, false);
                    if (infos != null && !infos.isEmpty()) {
                        if (incompatible) {
                            return latest(s, infos);
                        }
                        String rvs = infos.iterator().next().getRequiredGraalValues().get(BundleConstants.GRAAL_VERSION);
                        Version rv = Version.fromString(rvs);
                        if (rv.compareTo(gv) > 0) {
                            throw feedback.failure("REMOTE_UpgradeGraalVMCore", null, id, rvs);
                        }
                        if (m[0].getType() == Version.Match.Type.EXACT) {
                            throw feedback.failure("REMOTE_NoSpecificVersion", null, id, m[0].getVersion().displayString());
                        }
                    }
                    // last try, catch obsolete components:
                    infos = remoteRegistry.loadComponents(id, Version.NO_VERSION.match(Version.Match.Type.GREATER), false);
                    if (infos != null && !infos.isEmpty()) {
                        if (incompatible) {
                            return latest(s, infos);
                        }
                        throw feedback.failure("REMOTE_IncompatibleComponentVersion", null, id);
                    }
                    thrownUnknown(s, true);
                }
            } catch (FailedOperationException ex) {
                thrownUnknown(s, false);
                throw ex;
            }
            boolean progress = input.optValue(Commands.OPTION_NO_DOWNLOAD_PROGRESS) == null;
            return createComponentParam(s, info, progress);
        }
    }

    @Override
    public ComponentParam createParam(String cmdString, ComponentInfo info) {
        boolean progress = input.optValue(Commands.OPTION_NO_DOWNLOAD_PROGRESS) == null;
        return createComponentParam(cmdString, info, progress);
    }

    protected ComponentParam createComponentParam(String cmdLineString, ComponentInfo info, boolean progress) {
        RemoteComponentParam param = new CatalogItemParam(
                        factory,
                        info,
                        info.getName(),
                        cmdLineString,
                        feedback, progress);
        param.setVerifyJars(verifyJars);
        return param;
    }

    public static class CatalogItemParam extends RemoteComponentParam {
        final SoftwareChannel channel;

        public CatalogItemParam(SoftwareChannel channel, ComponentInfo catalogInfo, String dispName, String spec, Feedback feedback, boolean progress) {
            super(catalogInfo, dispName, spec, feedback, progress);
            this.channel = channel;
        }

        @Override
        protected FileDownloader createDownloader() {
            FileDownloader d = super.createDownloader();
            return channel.configureDownloader(getCatalogInfo(), d);
        }

        @Override
        protected MetadataLoader metadataFromLocal(Path localFile) throws IOException {
            FileComponent fc = new FileIterable.FileComponent(localFile.toFile(), isVerifyJars(), getFeedback());
            return fc.createFileLoader();
            // return channel.createLocalFileLoader(getCatalogInfo(), localFile, isVerifyJars());
        }

        @Override
        public ComponentInfo completeMetadata() throws IOException {
            return createFileLoader().completeMetadata();
        }
    }
}
