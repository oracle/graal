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

import java.io.File;
import java.io.FileFilter;
import org.graalvm.component.installer.model.ComponentStorage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;

/**
 * Directory-based implementation of component storage.
 */
public class DirectoryStorage implements ComponentStorage {
    public static final String META_LICENSE_FILE = "LICENSE_PATH"; // NOI18N

    /**
     * Relative fileName for the "release" Graalvm metadata.
     */
    private static final String PATH_RELEASE_FILE = "release";

    /**
     * Suffix for the component metadata files, including comma.
     */
    private static final String COMPONENT_FILE_SUFFIX = ".component"; // NOI18N

    /**
     * Suffix for the filelist metadata files.
     */
    private static final String LIST_FILE_SUFFIX = ".filelist"; // NOI18N

    /**
     * The "replaced files" metadata fileName relative to the registry.
     */
    private static final String PATH_REPLACED_FILES = "replaced-files.properties"; // NOI18N

    /**
     * 
     */
    private static final String BUNDLE_REQUIRED_PREFIX = BundleConstants.BUNDLE_REQUIRED + "-"; // NOI18N

    /**
     * Root of the storage fileName.
     */
    private final Path registryPath;

    /**
     * GralVM installation home.
     */
    private final Path graalHomePath;

    /**
     * The environment for reporting errors etc.
     */
    private final Feedback feedback;

    private Properties loaded;

    private static final String GRAALVM_SOURCE = "source"; // NOI18N
    private static final Pattern SOURCE_REVISION = Pattern.compile("\\b([a-z-._]+):([0-9a-f]+)\\b"); // NOI18N
    private static final String REVISION_PREFIX = "source_"; // NOI18N

    private static final String[] REQUIRED_ATTRIBUTES = {
                    CommonConstants.CAP_GRAALVM_VERSION,
                    CommonConstants.CAP_OS_NAME,
                    CommonConstants.CAP_OS_ARCH
    };

    public DirectoryStorage(Feedback feedback, Path storagePath, Path graalHomePath) {
        this.feedback = feedback;
        this.registryPath = storagePath;
        this.graalHomePath = graalHomePath;
    }

    @Override
    public Map<String, String> loadGraalVersionInfo() {
        Path graalVersionFile = graalHomePath.resolve(SystemUtils.fromCommonString(PATH_RELEASE_FILE));
        try (InputStream istm = Files.newInputStream(graalVersionFile)) { // NOI18N
            return load(istm);
        } catch (IOException ex) {
            throw feedback.failure("ERROR_ReadingRealeaseFile", ex, graalVersionFile, ex.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> load(InputStream istm) throws IOException {
        Map<String, String> graalAttributes = new HashMap<>();
        Properties props = new Properties();
        props.load(istm);
        for (String key : Collections.list((Enumeration<String>) props.propertyNames())) {
            String val = props.getProperty(key, ""); // MOI18N

            String lowerKey = key.toLowerCase();
            if (val.charAt(0) == '"' && val.length() > 1 && val.charAt(val.length() - 1) == '"') { // MOI18N
                val = val.substring(1, val.length() - 1).trim();
            }
            if (GRAALVM_SOURCE.equals(lowerKey)) {
                Matcher m = SOURCE_REVISION.matcher(val);
                while (m.find()) {
                    if (m.group(1) == null || m.group(2) == null || m.group(1).isEmpty() || m.group(2).isEmpty()) {
                        throw feedback.failure("ERROR_ReleaseSourceRevisions", null, graalHomePath);
                    }
                    graalAttributes.put(REVISION_PREFIX + m.group(1), m.group(2));
                }
            } else {
                graalAttributes.put(lowerKey, val);
            }
        }
        for (String a : REQUIRED_ATTRIBUTES) {
            if (!graalAttributes.containsKey(a)) {
                throw feedback.failure("STORAGE_InvalidReleaseFile", null);
            }
        }
        return graalAttributes;
    }

    /**
     * Loads list of components.
     * 
     * @return component IDs
     * @throws IOException
     */
    @Override
    public Set<String> listComponentIDs() throws IOException {
        if (!Files.exists(registryPath)) {
            return Collections.emptySet();
        }
        File d = registryPath.toFile();
        File[] files = d.listFiles(new FileFilter() {
            @Override
            public boolean accept(File child) {
                return Files.isRegularFile(child.toPath()) && child.getName().endsWith(COMPONENT_FILE_SUFFIX);
            }
        });
        Set<String> result = new HashSet<>();
        for (File f : files) {
            String s = registryPath.relativize(f.toPath()).toString();
            int e = s.length() - COMPONENT_FILE_SUFFIX.length();
            result.add(s.substring(0, e));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    ComponentInfo loadMetadataFrom(InputStream fileStream) throws IOException {
        ComponentInfo ci;
        loaded = new Properties();
        loaded.load(fileStream);

        String id = getRequiredProperty(BundleConstants.BUNDLE_ID);
        String name = getRequiredProperty(BundleConstants.BUNDLE_NAME);
        String version = getRequiredProperty(BundleConstants.BUNDLE_VERSION);

        String license = loaded.getProperty(META_LICENSE_FILE);

        ci = new ComponentInfo(id, name, version);
        if (license != null) {
            ci.setLicensePath(license);
        }
        for (String s : Collections.list((Enumeration<String>) loaded.propertyNames())) {
            if (s.startsWith(BUNDLE_REQUIRED_PREFIX)) {
                String k = s.substring(BUNDLE_REQUIRED_PREFIX.length());
                String v = loaded.getProperty(s, "");
                ci.addRequiredValue(k, v);
            }
        }
        ci.setPolyglotRebuild(
                        Boolean.TRUE.toString().equals(loaded.getProperty(BundleConstants.BUNDLE_POLYGLOT_PART, "")));
        List<String> ll = new ArrayList<>();
        for (String s : loaded.getProperty(BundleConstants.BUNDLE_WORKDIRS, "").split(":")) {
            String p = s.trim();
            if (!p.isEmpty()) {
                ll.add(p);
            }
        }
        ci.addWorkingDirectories(ll);
        return ci;
    }

    @Override
    public ComponentInfo loadComponentMetadata(String tag) throws IOException {
        Path cmpFile = registryPath.resolve(SystemUtils.fileName(tag + COMPONENT_FILE_SUFFIX));
        if (!Files.exists(cmpFile)) {
            return null;
        }
        try (InputStream fileStream = Files.newInputStream(cmpFile)) {
            ComponentInfo info = loadMetadataFrom(fileStream);
            info.setInfoPath(cmpFile.toString());
            return info;
        }
    }

    /**
     * Loads component files into its metadata.
     * 
     * @param ci the component metadata
     * @return initialized ComponentInfo
     * @throws IOException on I/O errors
     */
    @Override
    public ComponentInfo loadComponentFiles(ComponentInfo ci) throws IOException {
        String tag = ci.getId();
        Path listFile = registryPath.resolve(SystemUtils.fileName(tag + LIST_FILE_SUFFIX));
        if (!Files.exists(listFile)) {
            return ci;
        }
        List<String> s = Files.readAllLines(listFile);
        // throw away duplicities, sort.
        Set<String> result = new HashSet<>(s.size());
        for (String e : s) {
            String trimmed = e.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        s = new ArrayList<>(result);
        Collections.sort(s);
        ci.addPaths(s);
        return ci;
    }

    private String getRequiredProperty(String key) {
        String val = loaded.getProperty(key);
        if (val == null) {
            throw feedback.failure("STORAGE_CorruptedComponentStorage", null);
        }
        return val;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Collection<String>> readReplacedFiles() throws IOException {
        Path replacedPath = registryPath.resolve(SystemUtils.fromCommonString(PATH_REPLACED_FILES));
        Map<String, Collection<String>> result = new HashMap<>();
        Properties props = new Properties();
        if (!Files.exists(replacedPath)) {
            return result;
        }
        try (InputStream is = Files.newInputStream(replacedPath)) {
            props.load(is);
        }
        for (String s : Collections.list((Enumeration<String>) props.propertyNames())) {
            String files = props.getProperty(s, ""); // NOI18N
            Collection<String> unsorted = new HashSet<>();
            for (String x : files.split(" *, *")) { // NOI18N
                String t = x.trim();
                if (!t.isEmpty()) {
                    unsorted.add(t);
                }
            }
            List<String> components = new ArrayList<>(unsorted);
            Collections.sort(components);
            result.put(s, components);
        }
        return result;
    }

    @Override
    public void updateReplacedFiles(Map<String, Collection<String>> replacedFiles) throws IOException {
        Properties props = new SortedProperties();
        for (String k : replacedFiles.keySet()) {
            List<String> ids = new ArrayList<>(replacedFiles.get(k));
            Collections.sort(ids);
            props.setProperty(k, String.join(",", ids));
        }
        Path replacedPath = registryPath.resolve(SystemUtils.fromCommonString(PATH_REPLACED_FILES));
        if (props.isEmpty()) {
            Files.deleteIfExists(replacedPath);
        } else {
            try (OutputStream os = Files.newOutputStream(replacedPath,
                            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                props.store(os, null);
            }
        }
    }

    /**
     * Deletes component's files.
     * 
     * @param id component id
     * @throws IOException
     */
    @Override
    public void deleteComponent(String id) throws IOException {
        Path compFile = registryPath.resolve(SystemUtils.fileName(id + COMPONENT_FILE_SUFFIX));
        Path listFile = registryPath.resolve(SystemUtils.fileName(id + LIST_FILE_SUFFIX));
        Files.deleteIfExists(compFile);
        Files.deleteIfExists(listFile);
    }

    /**
     * Will persist component's metadata.
     * 
     * @param info
     * @throws IOException on failure
     */
    @Override
    public void saveComponent(ComponentInfo info) throws IOException {
        assert info != null;
        Path cmpFile = registryPath.resolve(SystemUtils.fileName(info.getId() + COMPONENT_FILE_SUFFIX));
        try (OutputStream compFile = Files.newOutputStream(cmpFile, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
            metaToProperties(info).store(compFile, null);
        }
        saveComponentFileList(info);
    }

    Properties metaToProperties(ComponentInfo info) {
        SortedProperties p = new SortedProperties();
        p.setProperty(BundleConstants.BUNDLE_ID, info.getId());
        p.setProperty(BundleConstants.BUNDLE_NAME, info.getName());
        p.setProperty(BundleConstants.BUNDLE_VERSION, info.getVersionString());
        if (info.getLicensePath() != null) {
            p.setProperty(META_LICENSE_FILE, info.getLicensePath());
        }
        for (String k : info.getRequiredGraalValues().keySet()) {
            String v = info.getRequiredGraalValues().get(k);
            if (v == null) {
                v = ""; // NOI18N
            }
            p.setProperty(BUNDLE_REQUIRED_PREFIX + k, v);
        }
        if (info.isPolyglotRebuild()) {
            p.setProperty(BundleConstants.BUNDLE_POLYGLOT_PART, Boolean.TRUE.toString());
        }
        if (!info.getWorkingDirectories().isEmpty()) {
            p.setProperty(BundleConstants.BUNDLE_WORKDIRS, info.getWorkingDirectories().stream().sequential().collect(Collectors.joining(":")));
        }
        return p;
    }

    void saveComponentFileList(ComponentInfo info) throws IOException {
        Path listFile = registryPath.resolve(SystemUtils.fileName(info.getId() + LIST_FILE_SUFFIX));
        List<String> entries = new ArrayList<>(new HashSet<>(info.getPaths()));
        Collections.sort(entries);

        Files.write(listFile, entries, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
    }
}
