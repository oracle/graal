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

import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.ServiceLoader;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.ComponentCollection;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentStorage;

public class RemoteCatalogDownloader implements SoftwareChannel {
    private final CommandInput input;
    private final Feedback feedback;

    private Iterable<SoftwareChannel.Factory> factories;
    private final List<String> catLocations;
    private CatalogContents union;

    public RemoteCatalogDownloader(CommandInput in, Feedback out, String catLocations) {
        this.input = in;
        this.feedback = out.withBundle(RemoteCatalogDownloader.class);

        this.catLocations = Arrays.asList(catLocations.split("\\|"));
        factories = ServiceLoader.load(SoftwareChannel.Factory.class);
    }

    // tests only
    public RemoteCatalogDownloader(CommandInput in, Feedback out, URL catalogURL) {
        this(in, out, catalogURL.toString());
    }

    // for testing only
    void setChannels(Iterable<SoftwareChannel.Factory> chan) {
        this.factories = chan;
    }

    private MergeStorage mergedStorage;

    private MergeStorage mergeChannels() {
        if (mergedStorage != null) {
            return mergedStorage;
        }
        mergedStorage = new MergeStorage(input.getLocalRegistry(), feedback);

        for (String spec : catLocations) {
            SoftwareChannel ch = null;
            for (SoftwareChannel.Factory f : factories) {
                ch = f.createChannel(spec, input, feedback);
                if (ch != null) {
                    break;
                }
            }
            if (ch != null) {
                mergedStorage.addChannel(ch);
            }
        }
        return mergedStorage;
    }

    SoftwareChannel delegate(ComponentInfo ci) {
        return mergeChannels().getOrigin(ci);
    }

    public ComponentCollection getRegistry() {
        if (union == null) {
            union = new CatalogContents(feedback, mergeChannels(), input.getLocalRegistry());
            // get errors early
            union.getComponentIDs();
        }
        return union;
    }

    @Override
    public FileDownloader configureDownloader(ComponentInfo cInfo, FileDownloader dn) {
        return delegate(cInfo).configureDownloader(cInfo, dn);
    }

    @Override
    public ComponentStorage getStorage() {
        return mergeChannels();
    }
}
