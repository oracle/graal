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
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.ComponentCollection;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.FileOperations;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.Verifier;

/**
 * The working internals of the 'add' command.
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

    public Installer(Feedback feedback, FileOperations fileOps, ComponentInfo componentInfo, ComponentRegistry registry, ComponentCollection collection, Archive a) {
        super(feedback, fileOps, componentInfo, registry, collection, a);
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
                fileOps.deleteFile(p);
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
                fileOps.deleteFile(p);
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
        // assert relative path
        rel = SystemUtils.fromCommonRelative(base, n);
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
        Verifier veri = validateRequirements();
        ComponentInfo existing = registry.findComponent(componentInfo.getId());
        if (existing != null) {
            if (!veri.shouldInstall(componentInfo)) {
                return false;
            }
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
            Path target = fileOps.materialize(translateTargetPath(sl), true);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                checkLinkReplacement(target,
                                translateTargetPath(target, processSymlinks.get(sl)));
            }
        }
    }

    boolean validateOneEntry(Path target, Archive.FileEntry entry) throws IOException {
        if (entry.isDirectory()) {
            // assert relative path
            Path dirPath = fileOps.materialize(SystemUtils.resolveRelative(getInstallPath(), entry.getName()), false);
            // confine into graalvm subdir
            if (Files.exists(dirPath)) {
                if (!Files.isDirectory(dirPath)) {
                    throw new IOException(
                                    feedback.l10n("INSTALL_OverwriteWithDirectory", dirPath));
                }
            }
            return true;
        }
        Path mt = fileOps.materialize(target, false);
        boolean existingFile = mt != null && Files.exists(mt, LinkOption.NOFOLLOW_LINKS);
        if (existingFile) {
            return checkFileReplacement(mt, entry);
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
        archive.completeMetadata(componentInfo);
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
        final String storagePrefix = CommonConstants.PATH_COMPONENT_STORAGE + "/"; // NOI18N
        for (Archive.FileEntry entry : archive) {
            String path = entry.getName();
            if (path.startsWith(storagePrefix) && path.length() > storagePrefix.length()) {
                // disallow to unpack files in the component database (but permit subdirs). Some
                // tools may write there, but
                // GU will manage the storage itself.
                if (path.indexOf('/', storagePrefix.length()) == -1) {
                    continue;
                }
            }
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
            Path dir = fileOps.materialize(parent.resolve(n), true);
            String pathString = SystemUtils.toCommonPath(relativeSubpath) + "/"; // NOI18N

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
            Path mt = fileOps.materialize(target, false);
            Path mt2 = fileOps.materialize(target, true);
            boolean existingFile = mt != null && Files.exists(mt, LinkOption.NOFOLLOW_LINKS);
            String eName = entry.getName();
            if (existingFile) {
                /*
                 * if (checkFileReplacement(target, entry)) {
                 * feedback.verboseOutput("INSTALL_SkipIdenticalFile", eName); return target; //
                 * same file, not replacing, skip to next file }
                 */
                feedback.verboseOutput("INSTALL_ReplacingFile", eName);
            } else {
                filesToDelete.add(mt2);
                feedback.verboseOutput("INSTALL_InstallingFile", eName);
            }
            ensurePathExists(target.getParent());
            addTrackedPath(SystemUtils.toCommonPath(getInstallPath().relativize(target)));
            if (!isDryRun()) {
                fileOps.installFile(target, jarStream);
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
            Path target = getInstallPath().resolve(SystemUtils.fromCommonRelative(s));
            String permissionString = setPermissions.get(s);
            Set<PosixFilePermission> perms;
            if (permissionString != null && !"".equals(permissionString)) {
                perms = PosixFilePermissions.fromString(permissionString);
            } else {
                perms = DEFAULT_CHANGE_PERMISSION;
            }
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                fileOps.setPermissions(target, perms);
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
                Path source = instDir.resolve(SystemUtils.fromCommonRelative(s));
                if (source == null) {
                    continue;
                }
                Path parent = source.getParent();
                if (parent == null) {
                    continue;
                }
                Path target = SystemUtils.fromCommonString(makeSymlinks.get(s));
                Path result = parent.resolve(target);
                if (result == null) {
                    continue;
                }
                result = result.normalize();
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

    @Override
    public String toString() {
        return "Installer[" + componentInfo.getId() + ":" + componentInfo.getName() + "=" + componentInfo.getVersion().displayString() + "]"; // NOI18N
    }

}
