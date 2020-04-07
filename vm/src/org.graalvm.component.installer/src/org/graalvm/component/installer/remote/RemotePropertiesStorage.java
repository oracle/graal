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

import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.AbstractCatalogStorage;
import org.graalvm.component.installer.persist.ComponentPackageLoader;

public class RemotePropertiesStorage extends AbstractCatalogStorage {
    protected static final String PROPERTY_HASH = "hash"; // NOI18N
    private static final String FORMAT_FLAVOUR = "Component.{0}"; // NOI18N
    private static final String FORMAT_SINGLE_VERSION = "Component.{0}_{1}."; // NOI18N

    private final Properties catalogProperties;
    private final String flavourPrefix;
    private final String singleVersionPrefix;
    private final Version graalVersion;

    private Map<String, Properties> filteredComponents;

    public RemotePropertiesStorage(Feedback fb, ComponentRegistry localReg, Properties catalogProperties, String graalSelector, Version gVersion, URL baseURL) {
        super(localReg, fb, baseURL);
        this.catalogProperties = catalogProperties;
        flavourPrefix = MessageFormat.format(FORMAT_FLAVOUR, graalSelector);
        graalVersion = gVersion != null ? gVersion : localReg.getGraalVersion();
        singleVersionPrefix = MessageFormat.format(FORMAT_SINGLE_VERSION,
                        graalVersion.originalString(), graalSelector);
    }

    /**
     * Returns properties relevant for a specific component ID. May return properties for several
     * versions of the component. Return {@code null} if the component does not exist at all.
     * 
     * @param id component ID.
     * @return Properties or {@code null} if component was not found.
     */
    Properties filterPropertiesForVersions(String id) {
        splitPropertiesToComponents();
        return filteredComponents.get(id);
    }

    private void splitPropertiesToComponents() {
        if (filteredComponents != null) {
            return;
        }
        filteredComponents = new HashMap<>();

        // known prefixes. Version will not be parsed again for therse.
        Set<String> knownPrefixes = new HashSet<>();

        // already accepted prefixes
        Set<String> acceptedPrefixes = new HashSet<>();

        for (String s : catalogProperties.stringPropertyNames()) {
            String cid;
            String pn;

            int slashPos = s.indexOf('/');
            int secondSlashPos = s.indexOf('/', slashPos + 1);
            int l;

            if (slashPos != -1 && secondSlashPos != -1) {
                if (!s.startsWith(flavourPrefix)) {
                    continue;
                }
                pn = s.substring(slashPos + 1);

                String pref = s.substring(0, secondSlashPos);

                int lastSlashPos = s.indexOf('/', secondSlashPos + 1);
                if (lastSlashPos == -1) {
                    lastSlashPos = secondSlashPos;
                }
                l = lastSlashPos + 1;
                if (knownPrefixes.add(pref)) {
                    try {
                        Version vn = Version.fromString(s.substring(slashPos + 1, secondSlashPos));
                        if (acceptsVersion(vn)) {
                            acceptedPrefixes.add(pref);
                        }
                    } catch (IllegalArgumentException ex) {
                        feedback.verboseOutput("REMOTE_BadComponentVersion", pn);
                    }
                }
                if (!acceptedPrefixes.contains(pref)) {
                    // ignore obsolete versions
                    continue;
                }

            } else {
                if (!s.startsWith(singleVersionPrefix)) {
                    continue;
                }
                // versionless component
                l = singleVersionPrefix.length();
                // normalized version
                pn = graalVersion.toString() + "/" + s.substring(l);
            }
            int dashPos = s.indexOf("-", l);
            if (dashPos == -1) {
                dashPos = s.length();
            }
            cid = s.substring(l, dashPos);
            String patchedCid = cid.replace("_", "-");
            String patchedPn = pn.replace(cid, patchedCid);
            filteredComponents.computeIfAbsent(patchedCid, (unused) -> new Properties()).setProperty(patchedPn, catalogProperties.getProperty(s));
        }
    }

    boolean acceptsVersion(Version vers) {
        // also accept other minor patc
        return graalVersion.installVersion().compareTo(vers.installVersion()) <= 0;
    }

    @Override
    public Set<String> listComponentIDs() throws IOException {
        Set<String> ret = new HashSet<>();
        splitPropertiesToComponents();
        ret.addAll(filteredComponents.keySet());
        return ret;
    }

    @Override
    public Set<ComponentInfo> loadComponentMetadata(String id) throws IOException {
        Properties compProps = filterPropertiesForVersions(id);
        if (compProps == null) {
            return null;
        }
        Map<String, ComponentInfo> infos = new HashMap<>();
        Set<String> processedPrefixes = new HashSet<>();

        for (String s : compProps.stringPropertyNames()) {
            int slashPos = s.indexOf('/');
            int anotherSlashPos = s.indexOf('/', slashPos + 1);

            String vS = s.substring(0, slashPos);
            String identity = anotherSlashPos == -1 ? vS : s.substring(0, anotherSlashPos);
            if (!processedPrefixes.add(identity)) {
                continue;
            }
            try {
                Version.fromString(vS);
            } catch (IllegalArgumentException ex) {
                feedback.verboseOutput("REMOTE_BadComponentVersion", s);
                continue;
            }
            ComponentInfo ci = createVersionedComponent(identity + "/", compProps, id,
                            anotherSlashPos == -1 ? "" : s.substring(slashPos + 1, anotherSlashPos));
            // just in case the catalog info is broken
            if (ci != null) {
                infos.put(identity, ci);
            }
        }

        return new HashSet<>(infos.values());
    }

    private ComponentInfo createVersionedComponent(String versoPrefix, Properties filtered, String id, String tag) throws IOException {
        URL downloadURL;
        String s = filtered.getProperty(versoPrefix + id.toLowerCase());
        if (s == null) {
            return null;
        }
        // try {
        downloadURL = new URL(baseURL, s);
        String prefix = versoPrefix + id.toLowerCase() + "-"; // NOI18N
        String hashS = filtered.getProperty(prefix + PROPERTY_HASH);
        byte[] hash = hashS == null ? null : toHashBytes(id, hashS);

        ComponentPackageLoader ldr = new ComponentPackageLoader(tag,
                        new PrefixedPropertyReader(prefix, filtered),
                        feedback);
        ComponentInfo info = ldr.createComponentInfo();
        info.setRemoteURL(downloadURL);
        info.setShaDigest(hash);
        info.setOrigin(baseURL.toString());
        return info;
    }

    static class PrefixedPropertyReader implements Function<String, String> {
        private final String compPrefix;
        private final Properties props;

        PrefixedPropertyReader(String compPrefix, Properties props) {
            this.compPrefix = compPrefix;
            this.props = props;
        }

        @Override
        public String apply(String t) {
            return props.getProperty(compPrefix + t);
        }
    }
}
