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
package org.graalvm.component.installer.persist;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.MetadataException;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.DistributionType;
import org.graalvm.component.installer.model.ManagementStorage;

/**
 * Directory-based implementation of component storage.
 */
public class DirectoryStorage implements ManagementStorage {
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
     * Metadata for natively installed component.
     */
    private static final String NATIVE_COMPONENT_FILE_SUFFIX = ".meta"; // NOI18N

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
     * Template for license accepted records.
     */
    private static final String LICENSE_DIR = "licenses"; // NOI18N'

    /**
     * Template for license accepted records.
     */
    private static final String LICENSE_CONTENTS_NAME = LICENSE_DIR + "/{0}"; // NOI18N'

    /**
     * Template for license accepted records.
     */
    private static final String LICENSE_FILE_TEMPLATE = LICENSE_DIR + "/{0}.accepted/{1}"; // NOI18N'

    /**
     * 
     */
    private static final String BUNDLE_REQUIRED_PREFIX = BundleConstants.BUNDLE_REQUIRED + "-"; // NOI18N
    private static final String BUNDLE_PROVIDED_PREFIX = BundleConstants.BUNDLE_PROVIDED + "-"; // NOI18N

    /**
     * Root of the storage fileName.
     */
    protected final Path registryPath;

    /**
     * GralVM installation home.
     */
    protected final Path graalHomePath;

    /**
     * The environment for reporting errors etc.
     */
    private final Feedback feedback;

    private Properties loaded;

    private ComponentInfo graalCore;

    private static final String GRAALVM_SOURCE = "source"; // NOI18N
    private static final Pattern SOURCE_REVISION = Pattern.compile("\\b([a-z-._]+):([0-9a-f]+)\\b"); // NOI18N
    private static final String REVISION_PREFIX = "source_"; // NOI18N

    private static final String[] REQUIRED_ATTRIBUTES = {
                    CommonConstants.CAP_GRAALVM_VERSION,
                    CommonConstants.CAP_OS_NAME,
                    CommonConstants.CAP_OS_ARCH
    };

    private static final String ENTERPRISE_EDITION = "ee"; // NOI18N
    private static final String VM_ENTERPRISE_COMPONENT = "vm-enterprise:"; // NOI18N

    private String javaVersion;

    public DirectoryStorage(Feedback feedback, Path storagePath, Path graalHomePath) {
        this.feedback = feedback;
        this.registryPath = storagePath;
        this.graalHomePath = graalHomePath;
        this.javaVersion = "" + SystemUtils.getJavaMajorVersion(); // NOI18N
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
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

    public Version getGraalVMVersion() {
        String s = loadGraalVersionInfo().get(CommonConstants.CAP_GRAALVM_VERSION);
        return Version.fromString(s);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> load(InputStream istm) throws IOException {
        Map<String, String> graalAttributes = new HashMap<>();
        Properties props = new Properties();
        props.load(istm);
        String srcText = null;
        for (String key : Collections.list((Enumeration<String>) props.propertyNames())) {
            String val = props.getProperty(key, ""); // MOI18N

            String lowerKey = key.toLowerCase(Locale.ENGLISH);
            if (!val.isEmpty() && val.charAt(0) == '"' && val.length() > 1 && val.charAt(val.length() - 1) == '"') { // MOI18N
                val = val.substring(1, val.length() - 1).trim();
            }
            if (GRAALVM_SOURCE.equals(lowerKey)) {
                Matcher m = SOURCE_REVISION.matcher(val);
                srcText = val;
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
        if (graalAttributes.get(CommonConstants.CAP_EDITION) == null && srcText != null) {
            // Hardcoded, sorry ...
            if (srcText.contains(VM_ENTERPRISE_COMPONENT)) {
                graalAttributes.put(CommonConstants.CAP_EDITION, ENTERPRISE_EDITION); // NOI18N
            } else {
                graalAttributes.put(CommonConstants.CAP_EDITION, CommonConstants.EDITION_CE);
            }
        }
        graalAttributes.putIfAbsent(CommonConstants.CAP_JAVA_VERSION, javaVersion);
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
                if (!Files.isRegularFile(child.toPath())) {
                    return false;
                }
                return child.getName().endsWith(COMPONENT_FILE_SUFFIX) ||
                                child.getName().endsWith(NATIVE_COMPONENT_FILE_SUFFIX);
            }
        });
        if (files != null) {
            Set<String> result = new HashSet<>();
            for (File f : files) {
                String s = registryPath.relativize(f.toPath()).toString();
                int lastDot = s.lastIndexOf('.');
                result.add(s.substring(0, lastDot));
            }
            // GraalVM core is always present
            if (Files.exists(graalHomePath.resolve("bin"))) {
                result.add(BundleConstants.GRAAL_COMPONENT_ID);
            }
            return result;
        } else {
            throw new IllegalArgumentException("File listing of " + d + " returned null.");
        }
    }

    private static String computeTag(Properties data) throws IOException {
        try (StringWriter wr = new StringWriter()) {
            data.store(wr, "");
            // properties store date/time into the stream as a comment. Cannot be disabled
            // programmatically,
            // must filter out.
            return SystemUtils.digestString(wr.toString().replaceAll("#.*\n", ""), false); // NOI18N
        }
    }

    ComponentInfo loadMetadataFrom(InputStream fileStream) throws IOException {
        loaded = new Properties();
        loaded.load(fileStream);

        String serial = loaded.getProperty(BundleConstants.BUNDLE_SERIAL);
        if (serial == null) {
            serial = computeTag(loaded);
        }
        String id = getRequiredProperty(BundleConstants.BUNDLE_ID);
        String name = getRequiredProperty(BundleConstants.BUNDLE_NAME);
        String version = getRequiredProperty(BundleConstants.BUNDLE_VERSION);

        return propertiesToMeta(loaded, new ComponentInfo(id, name, version, serial), feedback);
    }

    public static ComponentInfo propertiesToMeta(Properties loaded, ComponentInfo ci, Feedback fb) {
        String license = loaded.getProperty(BundleConstants.BUNDLE_LICENSE_PATH);
        if (license != null) {
            SystemUtils.checkCommonRelative(null, license);
            ci.setLicensePath(license);
        }
        for (String s : loaded.stringPropertyNames()) {
            if (s.startsWith(BUNDLE_REQUIRED_PREFIX)) {
                String k = s.substring(BUNDLE_REQUIRED_PREFIX.length());
                String v = loaded.getProperty(s, ""); // NOI18N
                ci.addRequiredValue(k, v);
            }
            if (s.startsWith(BUNDLE_PROVIDED_PREFIX)) {
                String k = s.substring(BUNDLE_PROVIDED_PREFIX.length());
                String v = loaded.getProperty(s, ""); // NOI18N
                if (v.length() < 2) {
                    continue;
                }
                String val = v.substring(1);
                Object o;
                switch (v.charAt(0)) {
                    case 'V':
                        o = Version.fromString(val);
                        break;
                    case '"':
                        o = val;
                        break;
                    default:
                        continue;
                }
                ci.provideValue(k, o);
            }
        }
        Set<String> deps = new LinkedHashSet<>();
        for (String s : loaded.getProperty(BundleConstants.BUNDLE_DEPENDENCY, "").split(",")) {
            String p = s.trim();
            if (!p.isEmpty()) {
                deps.add(s.trim());
            }
        }
        if (!deps.isEmpty()) {
            ci.setDependencies(deps);
        }
        if (Boolean.TRUE.toString().equals(loaded.getProperty(BundleConstants.BUNDLE_POLYGLOT_PART, ""))) { // NOI18N
            ci.setPolyglotRebuild(true);
        }
        List<String> ll = new ArrayList<>();
        for (String s : loaded.getProperty(BundleConstants.BUNDLE_WORKDIRS, "").split(":")) {
            String p = s.trim();
            if (!p.isEmpty()) {
                SystemUtils.checkCommonRelative(null, p);
                ll.add(p);
            }
        }
        ci.addWorkingDirectories(ll);
        String licType = loaded.getProperty(BundleConstants.BUNDLE_LICENSE_TYPE);
        if (licType != null) {
            ci.setLicenseType(licType);
        }
        String postInst = loaded.getProperty(BundleConstants.BUNDLE_MESSAGE_POSTINST);
        if (postInst != null) {
            String text = postInst.replace("\\n", "\n").replace("\\\\", "\\"); // NOI18N
            ci.setPostinstMessage(text);
        }
        String u = loaded.getProperty(CommonConstants.BUNDLE_ORIGIN_URL);
        if (u != null) {
            try {
                ci.setRemoteURL(new URL(u));
            } catch (MalformedURLException ex) {
                // ignore
            }
        }
        String dtn = loaded.getProperty(BundleConstants.BUNDLE_COMPONENT_DISTRIBUTION, DistributionType.OPTIONAL.name());
        try {
            ci.setDistributionType(DistributionType.valueOf(dtn.toUpperCase(Locale.ENGLISH)));
        } catch (IllegalArgumentException ex) {
            throw new MetadataException(BundleConstants.BUNDLE_COMPONENT_DISTRIBUTION,
                            fb.withBundle(DirectoryStorage.class).l10n("ERROR_InvalidDistributionType", dtn));
        }
        return ci;
    }

    private ComponentInfo getCoreInfo() {
        if (graalCore != null) {
            return graalCore;
        }
        Version v = getGraalVMVersion();
        ComponentInfo ci = new ComponentInfo(BundleConstants.GRAAL_COMPONENT_ID, feedback.l10n("NAME_GraalCoreComponent"),
                        v.originalString());
        Path cmpFile = registryPath.resolve(SystemUtils.fileName(BundleConstants.GRAAL_COMPONENT_ID + NATIVE_COMPONENT_FILE_SUFFIX));
        if (Files.exists(cmpFile)) {
            ci.setNativeComponent(true);
        }
        graalCore = ci;
        return graalCore;
    }

    @Override
    public Set<ComponentInfo> loadComponentMetadata(String tag) throws IOException {
        Path cmpFile = registryPath.resolve(SystemUtils.fileName(tag + COMPONENT_FILE_SUFFIX));
        boolean nc = false;
        if (!Files.exists(cmpFile)) {
            if (BundleConstants.GRAAL_COMPONENT_ID.equals(tag)) {
                return Collections.singleton(getCoreInfo());
            }
            cmpFile = registryPath.resolve(SystemUtils.fileName(tag + NATIVE_COMPONENT_FILE_SUFFIX));
            if (!Files.exists(cmpFile)) {
                return null;
            }
            nc = true;
        }
        try (InputStream fileStream = Files.newInputStream(cmpFile)) {
            ComponentInfo info = loadMetadataFrom(fileStream);
            info.setInfoPath(cmpFile.toString());
            info.setNativeComponent(nc);
            return Collections.singleton(info);
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
                SystemUtils.checkCommonRelative(null, trimmed);
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
                    SystemUtils.checkCommonRelative(null, t);
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
        // hack: if the component is null, just verify that the user has access to the registry's
        // data
        verifyUserAccess();
        if (info == null) {
            return;
        }
        if (info.isNativeComponent()) {
            return;
        }
        Path cmpFile = registryPath.resolve(SystemUtils.fileName(info.getId() + COMPONENT_FILE_SUFFIX));
        try (OutputStream compFile = Files.newOutputStream(cmpFile, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
            metaToProperties(info).store(compFile, null);
        }
        saveComponentFileList(info);
    }

    public static Properties metaToProperties(ComponentInfo info) {
        SortedProperties p = new SortedProperties();
        p.setProperty(BundleConstants.BUNDLE_ID, info.getId());
        p.setProperty(BundleConstants.BUNDLE_NAME, info.getName());
        p.setProperty(BundleConstants.BUNDLE_VERSION, info.getVersionString());
        String s = info.getTag();
        if (s != null && !s.isEmpty()) {
            p.setProperty(BundleConstants.BUNDLE_SERIAL, s);
        }
        if (info.getLicensePath() != null) {
            p.setProperty(BundleConstants.BUNDLE_LICENSE_PATH, info.getLicensePath());
        }
        if (info.getLicenseType() != null) {
            p.setProperty(BundleConstants.BUNDLE_LICENSE_TYPE, info.getLicenseType());
        }
        for (String k : info.getRequiredGraalValues().keySet()) {
            String v = info.getRequiredGraalValues().get(k);
            if (v == null) {
                v = ""; // NOI18N
            }
            p.setProperty(BUNDLE_REQUIRED_PREFIX + k, v);
        }
        for (String k : info.getProvidedValues().keySet()) {
            Object o = info.getProvidedValue(k, Object.class);
            char t;
            if (o instanceof String) {
                t = '"'; // NOI18N
            } else if (o instanceof Version) {
                t = 'V';
                o = ((Version) o).originalString();
            } else {
                continue;
            }
            p.setProperty(BUNDLE_PROVIDED_PREFIX + k, t + o.toString());
        }
        if (!info.getDependencies().isEmpty()) {
            p.setProperty(BundleConstants.BUNDLE_DEPENDENCY, info.getDependencies().stream().sequential().collect(Collectors.joining(":")));
        }
        if (info.getPostinstMessage() != null) {
            p.setProperty(BundleConstants.BUNDLE_MESSAGE_POSTINST, info.getPostinstMessage());
        }
        if (info.isPolyglotRebuild()) {
            p.setProperty(BundleConstants.BUNDLE_POLYGLOT_PART, Boolean.TRUE.toString());
        }
        if (!info.getWorkingDirectories().isEmpty()) {
            p.setProperty(BundleConstants.BUNDLE_WORKDIRS, info.getWorkingDirectories().stream().sequential().collect(Collectors.joining(":")));
        }
        if (info.getDistributionType() != DistributionType.OPTIONAL) {
            p.setProperty(BundleConstants.BUNDLE_COMPONENT_DISTRIBUTION, info.getDistributionType().name().toLowerCase(Locale.ENGLISH));
        }
        URL u = info.getRemoteURL();
        if (u != null) {
            p.setProperty(CommonConstants.BUNDLE_ORIGIN_URL, u.toString());
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

    private static void checkLicenseID(String licenseID) {
        if (licenseID.contains("/")) {
            throw new IllegalArgumentException("Invalid license ID: " + licenseID);
        }
    }

    @Override
    public Date licenseAccepted(ComponentInfo info, String licenseID) {
        if (!SystemUtils.isLicenseTrackingEnabled()) {
            return null;
        }
        checkLicenseID(licenseID);
        try {
            String fn = MessageFormat.format(LICENSE_FILE_TEMPLATE, licenseID, info.getId());
            Path listFile = registryPath.resolve(SystemUtils.fromCommonRelative(fn));
            if (!Files.isReadable(listFile)) {
                return null;
            }
            return new Date(Files.getLastModifiedTime(listFile).toMillis());
        } catch (IOException ex) {
            throw feedback.failure("ERR_CannotReadAcceptance", ex, licenseID);
        }
    }

    @Override
    public Map<String, Collection<String>> findAcceptedLicenses() {
        if (!SystemUtils.isLicenseTrackingEnabled()) {
            return Collections.emptyMap();
        }
        Path licDir = registryPath.resolve(LICENSE_DIR);
        Map<String, Collection<String>> result = new HashMap<>();
        try {
            if (!Files.isDirectory(licDir)) {
                return Collections.emptyMap();
            }
            Files.walk(licDir).forEach((lp) -> {
                if (!Files.isRegularFile(lp)) {
                    return;
                }
                Path parent = lp.getParent();
                if (parent.equals(licDir)) {
                    return;
                }
                String fn = parent.getFileName().toString();
                if (!fn.endsWith(".accepted")) {
                    return;
                }
                int dot = fn.lastIndexOf('.');
                String id = fn.substring(0, dot);
                result.computeIfAbsent(id, (x) -> new ArrayList<>()).add(lp.getFileName().toString());
            });
        } catch (IOException ex) {
            throw feedback.failure("ERR_CannotReadAcceptance", ex, "(all)");
        }
        return result;
    }

    @Override
    public void recordLicenseAccepted(ComponentInfo info, String licenseID, String licenseText, Date d) throws IOException {
        if (!SystemUtils.isLicenseTrackingEnabled()) {
            return;
        }
        if (licenseID == null) {
            clearRecordedLicenses();
            return;
        }
        checkLicenseID(licenseID);
        String fn = MessageFormat.format(LICENSE_FILE_TEMPLATE, licenseID, info.getId());
        Path listFile = registryPath.resolve(SystemUtils.fromCommonRelative(fn));
        if (listFile == null) {
            throw new IllegalArgumentException(licenseID);
        }
        Path dir = listFile.getParent();
        if (dir == null) {
            throw new IllegalArgumentException(licenseID);
        }
        if (!Files.isDirectory(dir)) {
            // create the directory
            Files.createDirectories(dir);
            Path contentsFile = registryPath.resolve(SystemUtils.fromCommonRelative(
                            MessageFormat.format(LICENSE_CONTENTS_NAME, licenseID)));
            Files.write(contentsFile, Arrays.asList(licenseText.split("\n")));
        }
        String ds = (d == null ? new Date() : d).toString();
        Files.write(listFile, Collections.singletonList(ds), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    void clearRecordedLicenses() throws IOException {
        Path listFile = registryPath.resolve(LICENSE_DIR);
        if (Files.isDirectory(listFile)) {
            try (Stream<Path> paths = Files.walk(listFile)) {
                paths.sorted(Comparator.reverseOrder()).forEach((p) -> {
                    try {
                        if (p.equals(listFile)) {
                            return;
                        }
                        Files.delete(p);
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                });
            } catch (UncheckedIOException ex) {
                throw ex.getCause();
            }
        }
    }

    @Override
    public String licenseText(String licID) {
        Path contentsFile = registryPath.resolve(SystemUtils.fromCommonRelative(
                        MessageFormat.format(LICENSE_CONTENTS_NAME, licID)));
        try {
            return Files.lines(contentsFile).collect(Collectors.joining("\n"));
        } catch (IOException ex) {
            throw feedback.failure("ERR_CannotReadAcceptance", ex, licID);
        }
    }

    void verifyUserAccess() {
        if (Files.isWritable(registryPath)) {
            return;
        }
        try {
            String owner = SystemUtils.findFileOwner(registryPath);
            if (owner != null) {
                throw feedback.failure("ERROR_MustBecomeUser", null, owner);
            }
        } catch (IOException ex) {
            // ignore, use generic message
        }
        throw feedback.failure("ERROR_MustBecomeAdmin", null);
    }
}
