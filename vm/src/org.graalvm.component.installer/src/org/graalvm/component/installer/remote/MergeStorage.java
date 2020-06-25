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

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.graalvm.component.installer.ComponentCatalog;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.IncompatibleException;
import org.graalvm.component.installer.InstallerStopException;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.SoftwareChannelSource;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.AbstractCatalogStorage;

/**
 * The Storage merges storages of individual providers.
 * 
 * @author sdedic
 */
public class MergeStorage extends AbstractCatalogStorage implements ComponentCatalog.DownloadInterceptor {
    private final Map<ComponentInfo, SoftwareChannel> channelMap = new HashMap<>();
    private final List<SoftwareChannel> channels = new ArrayList<>();
    private final Map<SoftwareChannel, SoftwareChannelSource> channelInfos = new HashMap<>();

    private boolean ignoreCatalogErrors;

    public MergeStorage(ComponentRegistry localRegistry, Feedback feedback) {
        super(localRegistry, feedback, null);
    }

    public void addChannel(SoftwareChannelSource info, SoftwareChannel delegate) {
        channels.add(delegate);
        channelInfos.put(delegate, info);
    }

    public boolean isIgnoreCatalogErrors() {
        return ignoreCatalogErrors;
    }

    public void setIgnoreCatalogErrors(boolean ignoreCatalogErrors) {
        this.ignoreCatalogErrors = ignoreCatalogErrors;
    }

    private void reportError(Exception exc, SoftwareChannel errChannel) {
        if (exc == null) {
            return;
        }
        // the previous error is overwritten, so at least report it before it is
        // forgot:
        SoftwareChannelSource info = channelInfos.get(errChannel);
        String l = info.getLabel();
        if (l == null) {
            l = info.getLocationURL();
        }
        feedback.error("REMOTE_CannotLoadChannel", exc, l, exc.getLocalizedMessage());
    }

    private boolean idsLoaded;

    @Override
    public Set<String> listComponentIDs() throws IOException {
        Set<String> ids = new HashSet<>();
        List<Exception> savedEx = new ArrayList<>();
        List<SoftwareChannel> errChannels = new ArrayList<>();
        boolean oneSucceeded = false;
        Exception toThrow = null;
        for (SoftwareChannel del : new ArrayList<>(channels)) {
            try {
                ids.addAll(del.getStorage().listComponentIDs());
                oneSucceeded = true;
            } catch (IncompatibleException ex) {
                savedEx.add(ex);
                errChannels.add(del);
                channels.remove(del);
            } catch (IOException | FailedOperationException ex) {
                if (!isIgnoreCatalogErrors()) {
                    throw ex;
                }
                if (!idsLoaded) {
                    reportError(ex, del);
                }
                toThrow = ex;
                channels.remove(del);
            }
        }
        if (!oneSucceeded || ids.isEmpty()) {
            for (int i = 0; i < savedEx.size(); i++) {
                reportError(toThrow = savedEx.get(i), errChannels.get(i));
            }
            if (toThrow instanceof IOException) {
                throw (IOException) toThrow;
            } else if (toThrow != null) {
                throw (InstallerStopException) toThrow;
            }
        }
        idsLoaded = true;
        return ids;
    }

    List<SoftwareChannel> getChannels() {
        return channels;
    }

    @Override
    public Set<ComponentInfo> loadComponentMetadata(String id) throws IOException {
        Set<ComponentInfo> cis = new HashSet<>();
        for (SoftwareChannel swch : channels) {
            Set<ComponentInfo> newInfos = swch.getStorage().loadComponentMetadata(id);
            if (newInfos == null || newInfos.isEmpty()) {
                continue;
            }
            newInfos.removeAll(cis);
            for (ComponentInfo ci : newInfos) {
                channelMap.put(ci, swch);
            }
            cis.addAll(newInfos);
            break;
        }
        return cis;
    }

    public SoftwareChannel getOrigin(ComponentInfo ci) {
        return channelMap.get(ci);
    }

    @Override
    public FileDownloader processDownloader(ComponentInfo info, FileDownloader dn) {
        SoftwareChannel orig = getOrigin(info);
        return orig != null ? orig.configureDownloader(info, dn) : dn;
    }
}
