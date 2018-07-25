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
package org.graalvm.component.installer.commands;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.Verifier;

/**
 * The working internals of the 'install' command.
 */
public class Installer implements Closeable {
    private static final Logger LOG = Logger.getLogger(Installer.class.getName());

    private static final int CHECKSUM_BUFFER_SIZE = 1024 * 1024;

    /**
     * Default permisions for files that should have the permissions changed.
     */
    private static final Set<PosixFilePermission> DEFAULT_CHANGE_PERMISSION = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE);

    private final Feedback feedback;
    private final ComponentInfo componentInfo;
    private JarFile jarFile;
    private final ComponentRegistry registry;

    private final List<Path> filesToDelete = new ArrayList<>();
    private final List<Path> dirsToDelete = new ArrayList<>();

    private Map<String, String> permissions = Collections.emptyMap();
    private Map<String, String> symlinks = Collections.emptyMap();
    private Path installPath;
    private Path licenseRelativePath;
    private Set<String> componentDirectories = Collections.emptySet();

    private boolean replaceDiferentFiles;
    private boolean replaceComponents;
    private boolean dryRun;
    private boolean ignoreRequirements;
    private boolean rebuildPolyglot;

    /**
     * Paths tracked by the component system.
     */
    private Set<String> trackedPaths = new HashSet<>();
    private Set<Path> visitedPaths = new HashSet<>();

    public boolean isReplaceDiferentFiles() {
        return replaceDiferentFiles;
    }

    public void setReplaceDiferentFiles(boolean replaceDiferentFiles) {
        this.replaceDiferentFiles = replaceDiferentFiles;
    }

    public boolean isReplaceComponents() {
        return replaceComponents;
    }

    public void setReplaceComponents(boolean replaceComponents) {
        this.replaceComponents = replaceComponents;
    }

    public boolean isIgnoreRequirements() {
        return ignoreRequirements;
    }

    public void setIgnoreRequirements(boolean ignoreRequirements) {
        this.ignoreRequirements = ignoreRequirements;
    }

    public Installer(Feedback feedback, ComponentInfo componentInfo, ComponentRegistry registry) {
        this.feedback = feedback;
        this.componentInfo = componentInfo;
        this.registry = registry;
    }

    public ComponentInfo getComponentInfo() {
        return componentInfo;
    }

    public Set<String> getTrackedPaths() {
        return trackedPaths;
    }

    public Map<String, String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Map<String, String> permissions) {
        this.permissions = permissions;
    }

    public Map<String, String> getSymlinks() {
        return symlinks;
    }

    public void setJarFile(JarFile jarFile) {
        this.jarFile = jarFile;
    }

    public void setSymlinks(Map<String, String> symlinks) {
        this.symlinks = symlinks;
    }

    public void revertInstall() {
        if (dryRun) {
            return;
        }
        LOG.fine("Reverting installation");
        for (Path p : filesToDelete) {
            try {
                LOG.log(Level.FINE, "Deleting: {0}", p);
                feedback.verboseOutput("INSTALL_CleanupFile", p);
                Files.deleteIfExists(p);
            } catch (IOException ex) {
                feedback.error("INSTALL_CannotCleanupFile", ex, p, ex.getMessage());
            }
        }
        // reverse the contents of directories, last created first:
        Collections.reverse(dirsToDelete);
        for (Path p : dirsToDelete) {
            try {
                LOG.log(Level.FINE, "Deleting directory: {0}", p);
                feedback.verboseOutput("INSTALL_CleanupDirectory", p);
                Files.deleteIfExists(p);
            } catch (IOException ex) {
                feedback.error("INSTALL_CannotCleanupFile", ex, p, ex.getMessage());
            }
        }
    }

    Path translateTargetPath(ZipEntry entry) {
        return translateTargetPath(entry.getName());
    }

    Path translateTargetPath(String n) {
        Path rel;
        if (BundleConstants.PATH_LICENSE.equals(n)) {
            rel = getLicenseRelativePath();
        } else {
            rel = SystemUtils.fromCommonString(n);
        }
        return getInstallPath().resolve(rel);
    }

    public void validateRequirements() {
        new Verifier(feedback, registry, componentInfo)
                        .ignoreRequirements(ignoreRequirements)
                        .replaceComponents(replaceComponents)
                        .validateRequirements();
    }

    public void validateAll() throws IOException {
        validateFiles();
        validateSymlinks();
        validateRequirements();
    }

    public void validateFiles() throws IOException {
        if (jarFile == null) {
            throw new UnsupportedOperationException();
        }
        for (JarEntry entry : Collections.list(jarFile.entries())) {
            if (entry.getName().startsWith("META-INF")) {   // NOI18N
                continue;
            }
            feedback.verboseOutput("INSTALL_VerboseValidation", entry.getName());
            validateOneEntry(translateTargetPath(entry), entry);
        }
    }

    public void validateSymlinks() throws IOException {
        for (String sl : symlinks.keySet()) {
            Path target = translateTargetPath(sl);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                checkLinkReplacement(target,
                                translateTargetPath(symlinks.get(sl)));
            }
        }
    }

    boolean validateOneEntry(Path target, ZipEntry entry) throws IOException {
        if (entry.isDirectory()) {
            Path dirPath = installPath.resolve(SystemUtils.fromCommonString(entry.getName()));
            if (Files.exists(dirPath)) {
                if (!Files.isDirectory(dirPath)) {
                    throw new IOException(
                                    feedback.l10n("INSTALL_OverwriteWithDirectory", dirPath));
                }
            }
            return true;
        }
        boolean existingFile = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        if (existingFile) {
            return checkFileReplacement(target, entry);
        }
        return false;
    }

    public void install() throws IOException {
        assert jarFile != null : "Must first download / set jar file";
        installContent();
        installFinish();
    }

    void installContent() throws IOException {
        if (jarFile == null) {
            throw new UnsupportedOperationException();
        }
        // unpack files
        unpackFiles();
        processPermissions();
        createSymlinks();

        List<String> ll = new ArrayList<>(trackedPaths);
        Collections.sort(ll);
        // replace paths with the really tracked ones
        componentInfo.setPaths(ll);
        rebuildPolyglot = componentInfo.isPolyglotRebuild() ||
                        ll.stream().filter(p -> p.startsWith(CommonConstants.PATH_POLYGLOT_REGISTRY))
                                        .findAny()
                                        .isPresent();
    }

    void installFinish() throws IOException {
        // installation succeeded, add to component registry
        if (!dryRun) {
            registry.addComponent(getComponentInfo());
        }
    }

    void unpackFiles() throws IOException {
        for (JarEntry entry : Collections.list(jarFile.entries())) {
            installOneEntry(entry);
        }
    }

    void ensurePathExists(Path targetPath) throws IOException {
        if (!visitedPaths.add(targetPath)) {
            return;
        }
        Path relative = installPath.relativize(targetPath);
        Path parent = installPath;
        Path relativeSubpath;
        int count = 0;
        for (Path n : relative) {
            count++;
            relativeSubpath = relative.subpath(0, count);
            Path dir = parent.resolve(n);
            String pathString = relativeSubpath.toString() + "/"; // NOI18N

            // Need to track either directories, which do not exist (and will be created)
            // AND directories created by other components.
            if (!Files.exists(dir) || componentDirectories.contains(pathString)) {
                feedback.verboseOutput("INSTALL_CreatingDirectory", dir);
                dirsToDelete.add(dir);
                // add the created directory to the installed file list
                trackedPaths.add(pathString);
                if (!Files.exists(dir)) {
                    if (!dryRun) {
                        Files.createDirectory(dir);
                    }
                }
            }
            parent = dir;
        }
    }

    Path installOneEntry(JarEntry entry) throws IOException {
        if (entry.getName().startsWith("META-INF")) {   // NOI18N
            return null;
        }
        Path targetPath = translateTargetPath(entry);
        boolean b = validateOneEntry(targetPath, entry);
        if (entry.isDirectory()) {
            ensurePathExists(targetPath);
            return targetPath;
        } else {
            String eName = entry.getName();
            if (b) {
                feedback.verboseOutput("INSTALL_SkipIdenticalFile", eName);
                return targetPath;
            }
            return installOneFile(targetPath, entry);
        }
    }

    Path installOneFile(Path target, JarEntry entry) throws IOException {
        // copy contents of the file
        try (InputStream jarStream = jarFile.getInputStream(entry)) {
            boolean existingFile = Files.exists(target, LinkOption.NOFOLLOW_LINKS);
            String eName = entry.getName();
            if (existingFile) {
                /*
                 * if (checkFileReplacement(target, entry)) {
                 * feedback.verboseOutput("INSTALL_SkipIdenticalFile", eName); return target; //
                 * same file, not replacing, skip to next file }
                 */
                feedback.verboseOutput("INSTALL_ReplacingFile", eName);
            } else {
                filesToDelete.add(target);
                feedback.verboseOutput("INSTALL_InstallingFile", eName);
            }
            ensurePathExists(target.getParent());
            trackedPaths.add(installPath.relativize(target).toString());
            if (!dryRun) {
                Files.copy(jarStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target;
    }

    public void processPermissions() throws IOException {
        List<String> paths = new ArrayList<>(permissions.keySet());
        Collections.sort(paths);
        for (String s : paths) {
            Path p = installPath.resolve(SystemUtils.fromCommonString(s));
            if (Files.exists(p)) {
                String permissionString = permissions.get(s);
                Set<PosixFilePermission> perms;

                if (permissionString != null && !"".equals(permissionString)) {
                    perms = PosixFilePermissions.fromString(permissionString);
                } else {
                    perms = DEFAULT_CHANGE_PERMISSION;
                }
                if (Files.getFileAttributeView(p, PosixFileAttributeView.class) != null) {
                    Files.setPosixFilePermissions(p, perms);
                }
            }
        }
    }

    public void createSymlinks() throws IOException {
        if (SystemUtils.isWindows()) {
            return;
        }
        List<String> createdRelativeLinks = new ArrayList<>();
        try {
            List<String> paths = new ArrayList<>(symlinks.keySet());
            Collections.sort(paths);
            for (String s : paths) {
                Path source = installPath.resolve(SystemUtils.fromCommonString(s));
                Path target = SystemUtils.fromCommonString(symlinks.get(s));
                ensurePathExists(source.getParent());
                createdRelativeLinks.add(s);
                trackedPaths.add(s);
                // TODO: check if the symlink does not exist and if so, whether
                // reads the same. Behaviour similar to file CRC check.
                if (Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
                    if (checkLinkReplacement(source, target)) {
                        feedback.verboseOutput("INSTALL_SkipIdenticalFile", s);
                        filesToDelete.add(source);
                        continue;
                    } else {
                        feedback.verboseOutput("INSTALL_ReplacingFile", s);
                        Files.delete(source);
                    }
                }
                filesToDelete.add(source);
                feedback.verboseOutput("INSTALL_CreatingSymlink", s, symlinks.get(s));
                if (!dryRun) {
                    Files.createSymbolicLink(source, target);
                }
            }
        } catch (UnsupportedOperationException ex) {
            LOG.log(Level.INFO, "Symlinks not supported", ex);
        }
        componentInfo.addPaths(createdRelativeLinks);
    }

    boolean checkLinkReplacement(Path existingPath, Path target) throws IOException {
        if (Files.exists(existingPath, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isSymbolicLink(existingPath)) {
                if (Files.isRegularFile(existingPath) && replaceDiferentFiles) {
                    return false;
                }
                throw new IOException(
                                feedback.l10n("INSTALL_OverwriteWithLink", existingPath));
            }
        }
        Path p = Files.readSymbolicLink(existingPath);
        if (!target.equals(p)) {
            if (replaceDiferentFiles) {
                return false;
            }
            throw feedback.failure("INSTALL_ReplacedFileDiffers", null, existingPath);
        }
        return true;
    }

    boolean checkFileReplacement(Path existingPath, ZipEntry entry) throws IOException {
        if (Files.isDirectory(existingPath)) {
            throw new IOException(
                            feedback.l10n("INSTALL_OverwriteWithFile", existingPath));
        }
        if (!Files.isRegularFile(existingPath) || (Files.size(existingPath) != entry.getSize())) {
            if (replaceDiferentFiles) {
                return false;
            }
            throw feedback.failure("INSTALL_ReplacedFileDiffers", null, existingPath);
        }
        CRC32 crc = new CRC32();
        ByteBuffer bb = null;
        try (ByteChannel is = Files.newByteChannel(existingPath)) {
            bb = ByteBuffer.allocate(CHECKSUM_BUFFER_SIZE);
            while (is.read(bb) >= 0) {
                bb.flip();
                crc.update(bb);
                bb.clear();
            }
        }
        if (crc.getValue() != entry.getCrc()) {
            if (replaceDiferentFiles) {
                return false;
            }
            throw feedback.failure("INSTALL_ReplacedFileDiffers", null, existingPath);
        }
        return true;
    }

    public Path getInstallPath() {
        return installPath;
    }

    public void setInstallPath(Path installPath) {
        this.installPath = installPath;
    }

    public Path getLicenseRelativePath() {
        return licenseRelativePath;
    }

    public void setLicenseRelativePath(Path licenseRelativePath) {
        this.licenseRelativePath = licenseRelativePath;
    }

    @Override
    public void close() throws IOException {
        if (jarFile != null) {
            jarFile.close();
        }
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Set<String> getComponentDirectories() {
        return componentDirectories;
    }

    public void setComponentDirectories(Set<String> componentDirectories) {
        this.componentDirectories = componentDirectories;
    }

    public boolean isRebuildPolyglot() {
        return rebuildPolyglot;
    }

}
