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
package org.graalvm.component.installer.commands;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.component.installer.Archive;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;

/**
 * The working internals of the 'install' command.
 */
public class Installer extends AbstractInstaller {
    private static final Logger LOG = Logger.getLogger(Installer.class.getName());

    /**
     * Default permisions for files that should have the permissions changed.
     */
    private static final Set<PosixFilePermission> DEFAULT_CHANGE_PERMISSION = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ, PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ, PosixFilePermission.OTHERS_EXECUTE);

    private final List<Path> filesToDelete = new ArrayList<>();
    private final List<Path> dirsToDelete = new ArrayList<>();

    private boolean rebuildPolyglot;
    /**
     * Paths tracked by the component system.
     */
    private final Set<Path> visitedPaths = new HashSet<>();

    public Installer(Feedback feedback, ComponentInfo componentInfo, ComponentRegistry registry, Archive a) {
        super(feedback, componentInfo, registry, a);
    }

    @Override
    public void revertInstall() {
        if (isDryRun()) {
            return;
        }
        LOG.fine("Reverting installation");
        for (Path p : filesToDelete) {
            try {
                LOG.log(Level.FINE, "Deleting: {0}", p);
                feedback.verboseOutput("INSTALL_CleanupFile", p);
                Files.deleteIfExists(p);
            } catch (IOException ex) {
                feedback.error("INSTALL_CannotCleanupFile", ex, p, ex.getLocalizedMessage());
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
                feedback.error("INSTALL_CannotCleanupFile", ex, p, ex.getLocalizedMessage());
            }
        }
    }

    Path translateTargetPath(Archive.FileEntry entry) {
        return translateTargetPath(entry.getName());
    }

    Path translateTargetPath(String n) {
        return translateTargetPath(null, n);
    }
    
    Path translateTargetPath(Path base, String n) {
        Path rel;
        if (BundleConstants.PATH_LICENSE.equals(n)) {
            rel = getLicenseRelativePath();
            if (rel == null) {
                rel = Paths.get(n);
            }
        } else {
            // assert relative path
            rel = SystemUtils.fromCommonRelative(base, n);
        }
        Path p = getInstallPath().resolve(rel).normalize();
        // confine into graalvm subdir
        if (!p.startsWith(getInstallPath())) {
            throw new IllegalStateException(
                            feedback.l10n("INSTALL_WriteOutsideGraalvm", p));
        }
        return p;
    }

    /**
     * Validates requirements, decides whether to install. Returns false if the component should be
     * skipped.
     * 
     * @return true, if the component should be installed
     * @throws IOException
     */
    @Override
    public boolean validateAll() throws IOException {
        validateRequirements();
        ComponentInfo existing = registry.findComponent(componentInfo.getId());
        if (existing != null) {
            return false;
        }
        validateFiles();
        validateSymlinks();
        return true;
    }

    @Override
    public void validateFiles() throws IOException {
        if (archive == null) {
            throw new UnsupportedOperationException();
        }
        for (Archive.FileEntry entry : archive) {
            if (entry.getName().startsWith("META-INF")) {   // NOI18N
                continue;
            }
            feedback.verboseOutput("INSTALL_VerboseValidation", entry.getName());
            validateOneEntry(translateTargetPath(entry), entry);
        }
    }

    @Override
    public void validateSymlinks() throws IOException {
        Map<String, String> processSymlinks = getSymlinks();
        for (String sl : processSymlinks.keySet()) {
            Path target = translateTargetPath(sl);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                checkLinkReplacement(target,
                                translateTargetPath(target, processSymlinks.get(sl)));
            }
        }
    }

    boolean validateOneEntry(Path target, Archive.FileEntry entry) throws IOException {
        if (entry.isDirectory()) {
            // assert relative path
            Path dirPath = getInstallPath().resolve(SystemUtils.fromCommonRelative(entry.getName()));
            // confine into graalvm subdir
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
        assert archive != null : "Must first download / set jar file";
        installContent();
        installFinish();
    }

    void installContent() throws IOException {
        if (archive == null) {
            throw new UnsupportedOperationException();
        }
        // unpack files
        unpackFiles();
        processPermissions();
        createSymlinks();

        List<String> ll = new ArrayList<>(getTrackedPaths());
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
        if (!isDryRun()) {
            registry.addComponent(getComponentInfo());
        }
    }

    void unpackFiles() throws IOException {
        for (Archive.FileEntry entry : archive) {
            installOneEntry(entry);
        }
    }

    void ensurePathExists(Path targetPath) throws IOException {
        if (!visitedPaths.add(targetPath)) {
            return;
        }
        Path parent = getInstallPath();
        if (!targetPath.normalize().startsWith(parent)) {
            throw new IllegalStateException(
                            feedback.l10n("INSTALL_WriteOutsideGraalvm", targetPath));
        }
        Path relative = getInstallPath().relativize(targetPath);
        Path relativeSubpath;
        int count = 0;
        for (Path n : relative) {
            count++;
            relativeSubpath = relative.subpath(0, count);
            Path dir = parent.resolve(n);
            String pathString = relativeSubpath.toString() + "/"; // NOI18N

            // Need to track either directories, which do not exist (and will be created)
            // AND directories created by other components.
            if (!Files.exists(dir) || getComponentDirectories().contains(pathString)) {
                feedback.verboseOutput("INSTALL_CreatingDirectory", dir);
                dirsToDelete.add(dir);
                // add the created directory to the installed file list
                addTrackedPath(pathString);
                if (!Files.exists(dir)) {
                    if (!isDryRun()) {
                        Files.createDirectory(dir);
                    }
                }
            }
            parent = dir;
        }
    }

    Path installOneEntry(Archive.FileEntry entry) throws IOException {
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

    Path installOneFile(Path target, Archive.FileEntry entry) throws IOException {
        // copy contents of the file
        try (InputStream jarStream = archive.getInputStream(entry)) {
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
            addTrackedPath(getInstallPath().relativize(target).toString());
            if (!isDryRun()) {
                Files.copy(jarStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return target;
    }

    @Override
    public void processPermissions() throws IOException {
        Map<String, String> setPermissions = getPermissions();
        List<String> paths = new ArrayList<>(setPermissions.keySet());
        Collections.sort(paths);
        for (String s : paths) {
            // assert relative path
            Path p = getInstallPath().resolve(SystemUtils.fromCommonRelative(s));
            if (Files.exists(p)) {
                String permissionString = setPermissions.get(s);
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

    @Override
    public void createSymlinks() throws IOException {
        if (SystemUtils.isWindows()) {
            return;
        }
        Map<String, String> makeSymlinks = getSymlinks();
        List<String> createdRelativeLinks = new ArrayList<>();
        try {
            List<String> paths = new ArrayList<>(makeSymlinks.keySet());
            Collections.sort(paths);
            Path instDir = getInstallPath();
            for (String s : paths) {
                // assert relative path
                Path source = getInstallPath().resolve(SystemUtils.fromCommonRelative(s));
                Path target = SystemUtils.fromCommonString(makeSymlinks.get(s));
                Path result = source.getParent().resolve(target).normalize();
                if (!result.startsWith(getInstallPath())) {
                    throw new IllegalStateException(
                            feedback.l10n("INSTALL_SymlinkOutsideGraalvm", source, result));
                }
                ensurePathExists(source.getParent());
                createdRelativeLinks.add(s);
                addTrackedPath(s);
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
                feedback.verboseOutput("INSTALL_CreatingSymlink", s, makeSymlinks.get(s));
                if (!isDryRun()) {
                    Files.createSymbolicLink(source, target);
                }
            }
        } catch (UnsupportedOperationException ex) {
            LOG.log(Level.INFO, "Symlinks not supported", ex);
        }
        componentInfo.addPaths(createdRelativeLinks);
    }

    boolean checkLinkReplacement(Path existingPath, Path target) throws IOException {
        boolean replace = isReplaceDiferentFiles();
        if (Files.exists(existingPath, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isSymbolicLink(existingPath)) {
                if (Files.isRegularFile(existingPath) && replace) {
                    return false;
                }
                throw new IOException(
                                feedback.l10n("INSTALL_OverwriteWithLink", existingPath));
            }
        }
        Path p = Files.readSymbolicLink(existingPath);
        if (!target.equals(p)) {
            if (replace) {
                return false;
            }
            throw feedback.failure("INSTALL_ReplacedFileDiffers", null, existingPath);
        }
        return true;
    }

    boolean checkFileReplacement(Path existingPath, Archive.FileEntry entry) throws IOException {
        boolean replace = isReplaceDiferentFiles();
        if (Files.isDirectory(existingPath)) {
            throw new IOException(
                            feedback.l10n("INSTALL_OverwriteWithFile", existingPath));
        }
        if (!Files.isRegularFile(existingPath) || (Files.size(existingPath) != entry.getSize())) {
            if (replace) {
                return false;
            }
            throw feedback.failure("INSTALL_ReplacedFileDiffers", null, existingPath);
        }
        try (ByteChannel is = Files.newByteChannel(existingPath)) {
            if (!archive.checkContentsMatches(is, entry)) {
                if (replace) {
                    return false;
                }
                throw feedback.failure("INSTALL_ReplacedFileDiffers", null, existingPath);
            }
        }
        return true;
    }

    @Override
    public boolean isRebuildPolyglot() {
        return rebuildPolyglot;
    }

}
