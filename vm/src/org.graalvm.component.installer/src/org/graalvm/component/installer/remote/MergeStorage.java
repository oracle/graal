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
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.IncompatibleException;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.AbstractCatalogStorage;

/**
 * The Storage merges storages of individual providers.
 * 
 * @author sdedic
 */
class MergeStorage extends AbstractCatalogStorage {
    private final Map<ComponentInfo, SoftwareChannel> channelMap = new HashMap<>();
    private final List<SoftwareChannel> channels = new ArrayList<>();

    MergeStorage(ComponentRegistry localRegistry, Feedback feedback) {
        super(localRegistry, feedback, null);
    }

    public void addChannel(SoftwareChannel delegate) {
        channels.add(delegate);
    }

    @Override
    public Set<String> listComponentIDs() throws IOException {
        Set<String> ids = new HashSet<>();
        IncompatibleException incEx = null;
        boolean oneSucceeded = false;
        for (SoftwareChannel del : channels) {
            try {
                ids.addAll(del.getStorage().listComponentIDs());
                oneSucceeded = true;
            } catch (IncompatibleException ex) {
                incEx = ex;
            }
        }
        if (!oneSucceeded && incEx != null) {
            throw incEx;
        }
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
}
