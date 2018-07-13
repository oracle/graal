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

import org.graalvm.component.installer.MetadataException;
import org.graalvm.component.installer.InstallerStopException;
import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import org.graalvm.component.installer.BundleConstants;
import static org.graalvm.component.installer.BundleConstants.PATH_LICENSE;
import static org.graalvm.component.installer.BundleConstants.META_INF_PATH;
import static org.graalvm.component.installer.BundleConstants.META_INF_PERMISSIONS_PATH;
import static org.graalvm.component.installer.BundleConstants.META_INF_SYMLINKS_PATH;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;

/**
 * Loads information from the component's bundle.
 */
public class ComponentPackageLoader implements Closeable, MetadataLoader {
    private final Feedback feedback;
    private final JarFile jarFile;
    private final Manifest manifest;
    /**
     * Default value producer.
     */
    private final Function<String, String> valueSupplier;

    /**
     * Do not throw exceptions eagerly.
     */
    private boolean infoOnly;

    /**
     * List of errors encountered when reading metadata. Filled only if {@link #infoOnly} =
     * {@code true}.
     */
    private final List<InstallerStopException> errors = new ArrayList<>();
    private final List<String> fileList = new ArrayList<>();

    /**
     * Component ID; temporary.
     */
    private String id;
    private String version;
    private String name;

    /**
     * Path for the LICENSE file; adjusted with version etc.
     */
    private String licensePath;

    /**
     * The produced component info.
     */
    private ComponentInfo info;

    /**
     * If true, will not verify symbolic links.
     *
     * Default false = verify on.
     */
    private boolean noVerifySymlinks;

    static final ResourceBundle BUNDLE = ResourceBundle.getBundle("org.graalvm.component.installer.persist.Bundle");

    public ComponentPackageLoader(JarFile jarFile, Feedback feedback) throws IOException {
        this.feedback = feedback.withBundle(ComponentPackageLoader.class);
        this.jarFile = jarFile;
        manifest = jarFile.getManifest();
        if (manifest == null) {
            throw this.feedback.failure("ERROR_CorruptedPackageMissingMeta", null);
        }
        valueSupplier = (s) -> manifest.getMainAttributes().getValue(s);
    }

    ComponentPackageLoader(Function<String, String> supplier, Feedback feedback) {
        this.feedback = feedback.withBundle(ComponentPackageLoader.class);
        this.valueSupplier = supplier;
        this.jarFile = null;
        this.manifest = null;
    }

    @Override
    public JarFile getJarFile() {
        return jarFile;
    }

    @Override
    public ComponentPackageLoader infoOnly(boolean only) {
        this.infoOnly = only;
        return this;
    }

    private HeaderParser parseHeader(String header) throws MetadataException {
        String s = valueSupplier.apply(header);
        return new HeaderParser(header, s, feedback).mustExist();
    }

    private HeaderParser parseHeader(String header, String defValue) throws MetadataException {
        String s = valueSupplier.apply(header);
        if (s == null) {
            if (defValue == null) {
                return new HeaderParser(header, s, feedback);
            } else {
                return new HeaderParser(header, defValue, feedback);
            }
        }
        return new HeaderParser(header, s, feedback).mustExist();
    }

    @Override
    public ComponentInfo getComponentInfo() {
        if (info == null) {
            return createComponentInfo();
        }
        return info;
    }

    private void parse(Runnable... parts) {
        for (Runnable r : parts) {
            try {
                r.run();
            } catch (MetadataException ex) {
                if (BundleConstants.BUNDLE_ID.equals(ex.getOffendingHeader())) {
                    throw ex;
                }
                if (infoOnly) {
                    errors.add(ex);
                } else {
                    throw ex;
                }
            } catch (InstallerStopException ex) {
                if (infoOnly) {
                    errors.add(ex);
                } else {
                    throw ex;
                }
            }
        }
    }

    @Override
    public List<InstallerStopException> getErrors() {
        return errors;
    }

    private void loadWorkingDirectories() {
        String val = parseHeader(BundleConstants.BUNDLE_WORKDIRS, null).getContents("");
        Set<String> workDirs = new LinkedHashSet<>();
        for (String s : val.split(":")) { // NOI18N
            String p = s.trim();
            if (!p.isEmpty()) {
                workDirs.add(p);
            }
        }
        info.addWorkingDirectories(workDirs);
    }

    public ComponentInfo createComponentInfo() {
        parse(
                        () -> id = parseHeader(BundleConstants.BUNDLE_ID).parseSymbolicName(),
                        () -> name = parseHeader(BundleConstants.BUNDLE_NAME).getContents(id),
                        () -> version = parseHeader(BundleConstants.BUNDLE_VERSION).version(),
                        () -> {
                            info = new ComponentInfo(id, name, version);
                            info.addRequiredValues(parseHeader(BundleConstants.BUNDLE_REQUIRED).parseRequiredCapabilities());
                        },
                        () -> info.setPolyglotRebuild(parseHeader(BundleConstants.BUNDLE_POLYGLOT_PART, null).getBoolean(Boolean.FALSE)),
                        () -> loadWorkingDirectories(),
                        () -> loadMessages()

        );
        return info;
    }

    @Override
    public String getLicensePath() {
        return licensePath;
    }

    private void throwInvalidPermissions() {
        throw feedback.failure("ERROR_PermissionFormat", null);
    }

    @SuppressWarnings("unchecked")
    Map<String, String> parsePermissions(BufferedReader r) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();

        Properties prop = new Properties();
        prop.load(r);

        List<String> paths = new ArrayList<>((Collection<String>) Collections.list(prop.propertyNames()));
        Collections.sort(paths);

        for (String k : paths) {
            String v = prop.getProperty(k, "").trim(); // NOI18N
            if (!v.isEmpty()) {
                try {
                    PosixFilePermissions.fromString(v);
                } catch (IllegalArgumentException ex) {
                    throwInvalidPermissions();
                }
            }
            result.put(k, v);
        }
        return result;
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    Map<String, String> parseSymlinks(Properties links) {
        for (String key : new HashSet<>(links.stringPropertyNames())) {
            Path p = SystemUtils.fromCommonString(key).normalize();
            String prop = (String) links.remove(key);
            links.setProperty(SystemUtils.toCommonPath(p), prop);
        }
        if (noVerifySymlinks) {
            return new HashMap(links);
        }
        // check the links
        for (String s : Collections.list((Enumeration<String>) links.propertyNames())) {
            String l = s;
            Set<String> seen = new HashSet<>();
            while (l != null) {
                if (!seen.add(l)) {
                    throw feedback.failure("ERROR_CircularSymlink", null, l);
                }
                String target = links.getProperty(l);
                Path linkPath = SystemUtils.fromCommonString(l);
                Path targetPath = linkPath.resolveSibling(target).normalize();
                String targetString = SystemUtils.toCommonPath(targetPath);
                if (fileList.contains(targetString)) {
                    break;
                }
                String lt = links.getProperty(targetString);
                if (lt == null) {
                    throw feedback.failure("ERROR_BrokenSymlink", null, target);
                }
                l = targetString;
            }
        }
        return new HashMap(links);
    }

    @Override
    public Map<String, String> loadSymlinks() throws IOException {
        assert info != null;
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
    public void loadPaths() {
        ComponentInfo cinfo = getComponentInfo();
        Set<String> emptyDirectories = new HashSet<>();
        for (JarEntry en : Collections.list(jarFile.entries())) {
            String eName = en.getName();
            if (eName.startsWith(META_INF_PATH)) {
                continue;
            }
            int li = eName.lastIndexOf("/", en.isDirectory() ? eName.length() - 2 : eName.length() - 1);
            if (li > 0) {
                emptyDirectories.remove(eName.substring(0, li + 1));
            }
            if (PATH_LICENSE.equals(eName)) {
                this.licensePath = MessageFormat.format(
                                BUNDLE.getString("LICENSE_Path_translation"),
                                cinfo.getId(),
                                cinfo.getVersionString());
                fileList.add(licensePath);
                cinfo.setLicensePath(licensePath);
                continue;
            }
            if (en.isDirectory()) {
                // directory names always come first
                emptyDirectories.add(eName);
            } else {
                fileList.add(eName);
            }
        }
        fileList.addAll(emptyDirectories);
        // sort empty directories first
        Collections.sort(fileList);
        cinfo.addPaths(fileList);
    }

    @Override
    public boolean isNoVerifySymlinks() {
        return noVerifySymlinks;
    }

    @Override
    public void setNoVerifySymlinks(boolean noVerifySymlinks) {
        this.noVerifySymlinks = noVerifySymlinks;
    }

    @Override
    public void close() throws IOException {
        if (jarFile != null) {
            jarFile.close();
        }
    }

    private void loadMessages() {
        String val = parseHeader(BundleConstants.BUNDLE_MESSAGE_POSTINST, null).getContents(null);
        if (val != null) {
            String text = val.replace("\\n", "\n").replace("\\\\", "\\"); // NOI18N
            info.setPostinstMessage(text);
        }
    }
}
