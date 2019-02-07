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
package org.graalvm.component.installer.remote;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ServiceLoader;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.MetadataLoader;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.model.ComponentInfo;

public class RemoteCatalogDownloader implements SoftwareChannel {
    private final CommandInput input;
    private final Feedback feedback;
    private final String catalogString;

    private ComponentRegistry catalog;
    private Iterable<SoftwareChannel> channels;
    private SoftwareChannel delegate;

    public RemoteCatalogDownloader(CommandInput in, Feedback out, String catLocation) {
        this.input = in;
        this.feedback = out.withBundle(RemoteCatalogDownloader.class);

        this.catalogString = catLocation;
        channels = ServiceLoader.load(SoftwareChannel.class);
    }

    public RemoteCatalogDownloader(CommandInput in, Feedback out, URL catalogURL) {
        this(in, out, catalogURL.toString());
    }

    // for testing only
    void setChannels(Iterable<SoftwareChannel> chan) {
        this.channels = chan;
    }

    public ComponentRegistry get() {
        if (catalog == null) {
            catalog = openCatalog();
        }
        return catalog;
    }

    SoftwareChannel delegate() {
        if (delegate != null) {
            return delegate;
        }
        for (SoftwareChannel ch : channels) {
            if (ch.setupLocation(catalogString)) {
                this.delegate = ch;
            }
        }
        if (delegate == null) {
            throw feedback.failure("REMOTE_CannotHandleLocation", null, catalogString);
        }
        delegate.init(input, feedback);
        return delegate;
    }

    @SuppressWarnings("unchecked")
    public ComponentRegistry openCatalog() {
        return delegate().getRegistry();
    }

    @Override
    public boolean setupLocation(String urlString) {
        for (SoftwareChannel ch : channels) {
            if (ch.setupLocation(catalogString)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void init(CommandInput input, Feedback output) {
    }

    @Override
    public ComponentRegistry getRegistry() {
        return delegate().getRegistry();
    }

    @Override
    public MetadataLoader createLocalFileLoader(Path localFile, boolean verify) throws IOException {
        return delegate.createLocalFileLoader(localFile, verify);
    }

    @Override
    public FileDownloader configureDownloader(FileDownloader dn) {
        return delegate.configureDownloader(dn);
    }

    @Override
    public MetadataLoader completeMetadata(MetadataLoader ldr, ComponentInfo info) throws IOException {
        return delegate.completeMetadata(ldr, info);
    }
}
