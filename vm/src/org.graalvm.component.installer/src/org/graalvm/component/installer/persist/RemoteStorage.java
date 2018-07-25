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
package org.graalvm.component.installer.persist;

import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.MetadataException;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.model.ComponentStorage;

public class RemoteStorage implements ComponentStorage {
    private final Properties catalogProperties;
    private final String flavourPrefix;
    private final ComponentRegistry localRegistry;
    private final Feedback feedback;
    private final URL baseURL;

    private static final String PROPERTY_HASH = "hash"; // NOI18N
    private static final String FORMAT_FLAVOUR = "Component.{0}."; // NOI18N

    public RemoteStorage(Feedback fb, ComponentRegistry localReg, Properties catalogProperties, String graalVersion, URL baseURL) {
        this.catalogProperties = catalogProperties;
        this.localRegistry = localReg;
        this.feedback = fb;
        this.baseURL = baseURL;
        flavourPrefix = MessageFormat.format(FORMAT_FLAVOUR, graalVersion);
    }

    @Override
    public Set<String> listComponentIDs() throws IOException {
        Set<String> ret = new HashSet<>();
        for (String s : catalogProperties.stringPropertyNames()) {
            if (!s.startsWith(flavourPrefix)) {
                continue;
            }
            String rest = s.substring(flavourPrefix.length());
            if (rest.indexOf('-') == -1) {
                // got a component ID
                ret.add(rest.toLowerCase());
            }
        }
        return ret;
    }

    @Override
    public Map<String, String> loadGraalVersionInfo() {
        return localRegistry.getGraalCapabilities();
    }

    @Override
    public ComponentInfo loadComponentFiles(ComponentInfo ci) throws IOException {
        // files are not supported, yet
        return ci;
    }

    private byte[] toHashBytes(String comp, String hashS) {
        return toHashBytes(comp, hashS, feedback);
    }

    public static byte[] toHashBytes(String comp, String hashS, Feedback fb) {
        String val = hashS.trim();
        if (val.length() < 4) {
            throw fb.failure("REMOTE_InvalidHash", null, comp, val);
        }
        char c = val.charAt(2);
        boolean divided = !((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f')); // NOI18N
        boolean lenOK;
        int s;

        if (divided) {
            lenOK = (val.length() + 1) % 3 == 0;
            s = (val.length() + 1) / 3;
        } else {
            lenOK = (val.length()) % 2 == 0;
            s = (val.length() + 1) / 2;
        }
        if (!lenOK) {
            throw fb.failure("REMOTE_InvalidHash", null, comp, val);
        }
        byte[] digest = new byte[s];
        int dI = 0;
        for (int i = 0; i + 1 < val.length(); i += 2) {
            int b;
            try {
                b = Integer.parseInt(val.substring(i, i + 2), 16);
            } catch (NumberFormatException ex) {
                throw new MetadataException(null, fb.l10n("REMOTE_InvalidHash", comp, val));
            }
            if (b < 0) {
                throw new MetadataException(null, fb.l10n("REMOTE_InvalidHash", comp, val));
            }
            digest[dI++] = (byte) b;
            if (divided) {
                i++;
            }
        }
        return digest;
    }

    @Override
    public ComponentInfo loadComponentMetadata(String id) throws IOException {
        URL downloadURL;
        String s = catalogProperties.getProperty(flavourPrefix + id.toLowerCase());
        if (s == null) {
            return null;
        }
        // try {
        downloadURL = new URL(baseURL, s);
        // } catch (MalformedURLException ex) {
        // throw feedback.failure("REMOTE_InvalidDownloadURL", ex, s, ex.getLocalizedMessage());
        // }
        String prefix = flavourPrefix + id.toLowerCase() + "-"; // NOI18N
        String hashS = catalogProperties.getProperty(prefix + PROPERTY_HASH);
        byte[] hash = hashS == null ? null : toHashBytes(id, hashS);

        ComponentPackageLoader ldr = new ComponentPackageLoader(
                        new PrefixedPropertyReader(prefix, catalogProperties),
                        feedback);
        ComponentInfo info = ldr.createComponentInfo();
        info.setRemoteURL(downloadURL);
        info.setShaDigest(hash);
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

    @Override
    public void deleteComponent(String id) throws IOException {
        throw new UnsupportedOperationException("Read only"); // NOI18N
    }

    @Override
    public Map<String, Collection<String>> readReplacedFiles() throws IOException {
        throw new UnsupportedOperationException("Should not be called."); // NOI18N
    }

    @Override
    public void saveComponent(ComponentInfo info) throws IOException {
        throw new UnsupportedOperationException("Should not be called."); // NOI18N
    }

    @Override
    public void updateReplacedFiles(Map<String, Collection<String>> replacedFiles) throws IOException {
        throw new UnsupportedOperationException("Should not be called."); // NOI18N
    }
}
