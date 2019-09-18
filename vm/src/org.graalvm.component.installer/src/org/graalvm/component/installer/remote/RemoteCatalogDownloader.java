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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.ComponentCollection;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SoftwareChannel;
import org.graalvm.component.installer.SoftwareChannelSource;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentStorage;

public class RemoteCatalogDownloader implements SoftwareChannel {
    private final CommandInput input;
    private final Feedback feedback;

    private Iterable<SoftwareChannel.Factory> factories;
    private List<SoftwareChannelSource> channelSources;
    private CatalogContents union;
    private String overrideCatalogSpec;
    private String defaultCatalogSpec;

    public RemoteCatalogDownloader(CommandInput in, Feedback out, String overrideCatalogSpec) {
        this.input = in;
        this.feedback = out.withBundle(RemoteCatalogDownloader.class);
        this.overrideCatalogSpec = overrideCatalogSpec;
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

    public void setDefaultCatalog(String defaultCatalogSpec) {
        this.defaultCatalogSpec = defaultCatalogSpec;
    }

    private MergeStorage mergedStorage;

    static final String CAP_CATALOG_URL_SUFFIX = "_" + CommonConstants.CAP_CATALOG_URL; // NOI18N

    @SuppressWarnings("ThrowableResultIgnored")
    List<SoftwareChannelSource> parseChannelSources(String overrideSpec) {
        List<SoftwareChannelSource> sources = new ArrayList<>();

        String[] parts = overrideSpec.split("\\|"); // NOI18N
        for (String s : parts) {
            try {
                sources.add(new SoftwareChannelSource(s));
            } catch (MalformedURLException ex) {
                feedback.error("REMOTE_FailedToParseParameter", ex, s); // NOI18N
            }
        }
        return sources;
    }

    List<SoftwareChannelSource> getChannelSources() {
        if (channelSources != null) {
            return channelSources;
        }
        if (overrideCatalogSpec != null) {
            return channelSources = parseChannelSources(overrideCatalogSpec);
        } else {
            List<SoftwareChannelSource> sources = readChannelSources();
            if (sources.isEmpty()) {
                sources = parseChannelSources(defaultCatalogSpec);
            }
            channelSources = sources;
            return sources;
        }
    }

    private static final Comparator<String> CHANNEL_KEY_COMPARATOR = new Comparator<String>() {
        @Override
        public int compare(String o1, String o2) {
            String k1 = o1.substring(CommonConstants.CAP_CATALOG_PREFIX.length());
            String k2 = o2.substring(CommonConstants.CAP_CATALOG_PREFIX.length());
            int i1 = Integer.MAX_VALUE;
            int i2 = Integer.MAX_VALUE;
            try {
                i1 = Integer.parseInt(k1);
            } catch (NumberFormatException ex) {
            }
            try {
                i2 = Integer.parseInt(k2);
            } catch (NumberFormatException ex) {
            }
            if (i1 != i2) {
                return i1 - i2;
            }
            return k1.compareToIgnoreCase(k2);
        }
    };

    List<SoftwareChannelSource> readChannelSources(String pref, Map<String, String> graalCaps) {
        String prefix = pref + CommonConstants.CAP_CATALOG_PREFIX;
        List<String> orderedKeys = graalCaps.keySet().stream().filter((k) -> {
            String lk = k.toLowerCase(Locale.ENGLISH);
            return lk.startsWith(prefix) && lk.endsWith(CAP_CATALOG_URL_SUFFIX);
        }).map((k) -> k.substring(0, k.length() - CAP_CATALOG_URL_SUFFIX.length())).collect(Collectors.toList());
        Collections.sort(orderedKeys, CHANNEL_KEY_COMPARATOR);

        List<SoftwareChannelSource> sources = new ArrayList<>();
        for (String key : orderedKeys) {
            String url = graalCaps.get(key + CAP_CATALOG_URL_SUFFIX);
            String lab = graalCaps.get(key + "_" + CommonConstants.CAP_CATALOG_LABEL);
            if (url == null) {
                continue;
            }
            SoftwareChannelSource s = new SoftwareChannelSource(url, lab);
            for (String a : graalCaps.keySet()) {
                if (!(a.startsWith(key) && a.length() > key.length() + 1)) {
                    continue;
                }
                String k = a.substring(key.length() + 1).toLowerCase(Locale.ENGLISH);
                switch (k) {
                    case CommonConstants.CAP_CATALOG_LABEL:
                    case CommonConstants.CAP_CATALOG_URL:
                        continue;
                }
                s.setParameter(k, graalCaps.get(a));
            }

            sources.add(s);
        }
        return sources;
    }

    List<SoftwareChannelSource> readChannelSources() {
        List<SoftwareChannelSource> res;

        res = readChannelSources(CommonConstants.ENV_VARIABLE_PREFIX, System.getenv());
        if (res != null && !res.isEmpty()) {
            return res;
        }

        return readChannelSources("", input.getLocalRegistry().getGraalCapabilities()); // NOI18N

    }

    private MergeStorage mergeChannels() {
        if (mergedStorage != null) {
            return mergedStorage;
        }
        mergedStorage = new MergeStorage(input.getLocalRegistry(), feedback);

        for (SoftwareChannelSource spec : getChannelSources()) {
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
