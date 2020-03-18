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
package org.graalvm.component.installer.jar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.graalvm.component.installer.Archive;
import static org.graalvm.component.installer.BundleConstants.META_INF_PATH;
import static org.graalvm.component.installer.BundleConstants.META_INF_PERMISSIONS_PATH;
import static org.graalvm.component.installer.BundleConstants.META_INF_SYMLINKS_PATH;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.ComponentPackageLoader;

/**
 *
 * @author sdedic
 */
public class JarMetaLoader extends ComponentPackageLoader {
    private final JarFile jarFile;
    @SuppressWarnings("unused")
    // TODO: refactor construction
    private final Feedback fb;

    public JarMetaLoader(JarFile jarFile, Feedback feedback) throws IOException {
        this(jarFile, null, feedback);
    }

    public JarMetaLoader(JarFile jarFile, String serial, Feedback feedback) throws IOException {
        super(serial, new ManifestValues(jarFile), feedback);
        this.jarFile = jarFile;
        this.fb = feedback.withBundle(JarMetaLoader.class);
    }

    private static class ManifestValues implements Function<String, String> {
        private final Manifest mf;
        private final Attributes mainAttributes;

        ManifestValues(JarFile jf) throws IOException {
            this.mf = jf.getManifest();
            this.mainAttributes = mf == null ? null : mf.getMainAttributes();
        }

        @Override
        public String apply(String t) {
            return mainAttributes == null ? null : mainAttributes.getValue(t);
        }

    }

    @Override
    public void close() throws IOException {
        super.close();
        jarFile.close();
    }

    @Override
    public Archive getArchive() {
        return new JarArchive(jarFile);
    }

    @Override
    public Map<String, String> loadPermissions() throws IOException {
        JarEntry permEntry = jarFile.getJarEntry(META_INF_PERMISSIONS_PATH);
        if (permEntry == null) {
            return Collections.emptyMap();
        }
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                        jarFile.getInputStream(permEntry), "UTF-8"))) {
            Map<String, String> permissions = parsePermissions(r);
            return permissions;
        }
    }

    @Override
    public void loadPaths() {
        ComponentInfo cinfo = getComponentInfo();
        Set<String> emptyDirectories = new HashSet<>();
        List<String> files = new ArrayList<>();
        for (JarEntry en : Collections.list(jarFile.entries())) {
            String eName = en.getName();
            if (eName.startsWith(META_INF_PATH)) {
                continue;
            }
            int li = eName.lastIndexOf("/", en.isDirectory() ? eName.length() - 2 : eName.length() - 1);
            if (li > 0) {
                emptyDirectories.remove(eName.substring(0, li + 1));
            }
            if (en.isDirectory()) {
                // directory names always come first
                emptyDirectories.add(eName);
            } else {
                files.add(eName);
            }
        }
        addFiles(new ArrayList<>(emptyDirectories));
        // sort empty directories first
        Collections.sort(files);
        cinfo.addPaths(files);
        addFiles(files);
    }

    @Override
    public Map<String, String> loadSymlinks() throws IOException {
        JarEntry symEntry = jarFile.getJarEntry(META_INF_SYMLINKS_PATH);
        if (symEntry == null) {
            return Collections.emptyMap();
        }
        Properties links = new Properties();
        try (InputStream istm = jarFile.getInputStream(symEntry)) {
            links.load(istm);
        }
        return parseSymlinks(links);
    }

    @Override
    public String getLicenseID() {
        String licPath = getLicensePath();
        if (licPath == null) {
            return null;
        }
        JarEntry je = jarFile.getJarEntry(licPath);
        if (je == null) {
            return null;
        }
        long crc = je.getCrc();
        return Long.toHexString(crc);
    }
}
