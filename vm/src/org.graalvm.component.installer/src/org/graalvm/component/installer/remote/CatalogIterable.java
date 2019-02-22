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
import java.util.Iterator;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.ComponentIterable;
import org.graalvm.component.installer.ComponentParam;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
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
    private ComponentRegistry remoteRegistry;
    private boolean verifyJars;

    public CatalogIterable(CommandInput input, Feedback feedback, SoftwareChannel fact) {
        this.input = input;
        this.feedback = feedback;
        this.factory = fact;
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
            remoteRegistry = factory.getRegistry();
        }
        return remoteRegistry;
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
                if (getRegistry().findComponent(s.toLowerCase()) == null) {
                    thrownUnknown(s, true);
                }

                info = getRegistry().loadSingleComponent(s.toLowerCase(), false);
                if (info == null) {
                    thrownUnknown(s, true);
                }
            } catch (FailedOperationException ex) {
                thrownUnknown(s, false);
                throw ex;
            }
            boolean progress = input.optValue(Commands.OPTION_NO_DOWNLOAD_PROGRESS) == null;
            return createComponenParam(s, info, progress);
        }
    }

    protected ComponentParam createComponenParam(String cmdLineString, ComponentInfo info, boolean progress) {
        RemoteComponentParam param = new CatalogItemParam(
                        factory,
                        info,
                        feedback.l10n("REMOTE_ComponentFileLabel", cmdLineString),
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
            return channel.configureDownloader(d);
        }

        @Override
        protected MetadataLoader metadataFromLocal(Path localFile) throws IOException {
            return channel.createLocalFileLoader(localFile, isVerifyJars());
        }

        @Override
        public MetadataLoader completeMetadata() throws IOException {
            MetadataLoader ldr = createMetaLoader();
            return channel.completeMetadata(ldr, ldr.getComponentInfo());
        }
    }
}
