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
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.AbstractCatalogStorage;
import org.graalvm.component.installer.persist.ComponentPackageLoader;

public class RemotePropertiesStorage extends AbstractCatalogStorage {
    protected static final String PROPERTY_HASH = "hash"; // NOI18N
    private static final String FORMAT_FLAVOUR = "Component.{0}."; // NOI18N

    private final Properties catalogProperties;
    private final String flavourPrefix;

    public RemotePropertiesStorage(Feedback fb, ComponentRegistry localReg, Properties catalogProperties, String graalVersion, URL baseURL) {
        super(localReg, fb, baseURL);
        this.catalogProperties = catalogProperties;
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
}
