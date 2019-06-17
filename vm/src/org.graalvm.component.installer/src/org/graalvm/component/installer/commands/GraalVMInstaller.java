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
package org.graalvm.component.installer.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.graalvm.component.installer.Archive;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.ComponentCollection;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.FileOperations;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.model.ManagementStorage;

/**
 *
 * @author sdedic
 */
public class GraalVMInstaller extends Installer {
    private static final String SYMLINK_NAME = "active";    // NOI18N

    private Path currentInstallPath;
    private boolean createSymlink;

    public GraalVMInstaller(Feedback feedback, FileOperations fops, ComponentRegistry current,
                    ComponentInfo componentInfo, ComponentCollection collection, Archive a) {
        super(feedback, fops, componentInfo,
                        new ComponentRegistry(feedback,
                                        new TransientStorage(
                                                        componentInfo.getVersion(),
                                                        current.getGraalCapabilities(), componentInfo.getProvidedValues())),
                        collection, a);
    }

    public Path getCurrentInstallPath() {
        return currentInstallPath;
    }

    public Path getInstalledPath() {
        return getInstallPath().resolve(SystemUtils.getGraalVMJDKRoot(registry));
    }

    public void setCurrentInstallPath(Path currentInstallPath) {
        this.currentInstallPath = currentInstallPath.normalize();
    }

    public boolean isCreateSymlink() {
        return createSymlink;
    }

    public void setCreateSymlink(boolean createSymlink) {
        this.createSymlink = createSymlink;
    }

    @Override
    public void install() throws IOException {
        super.install();

        if (Files.getFileStore(getInstallPath()).supportsFileAttributeView("isSymbolicLink")) {
            return;
        }
        createSymlink(findSymlink(getInstalledPath().getParent()).orElse(null));
    }

    Path createSymlink(Path linkFile) {
        Path parent = linkFile == null ? getInstallPath().getParent() : linkFile.getParent();
        if (parent == null) {
            return null;
        }
        Path linkTarget;

        try {
            linkTarget = parent.relativize(getInstallPath());
        } catch (IllegalArgumentException ex) {
            linkTarget = getInstallPath();
        }
        if (linkFile != null) {
            boolean create = false;
            try {
                Files.delete(linkFile);
                create = true;
                Files.createSymbolicLink(linkFile, linkTarget);
            } catch (IOException ex) {
                feedback.error(
                                create ? "UPGRADE_CantCreateNewSymlink" : "UPGRADE_CantDeleteOldSymlink",
                                ex, linkFile, ex.getLocalizedMessage());
            }

        } else if (createSymlink) {
            Path linkSource = parent.resolve(SYMLINK_NAME);
            try {
                Files.createSymbolicLink(linkSource, linkTarget);
            } catch (IOException ex) {
                feedback.error(
                                "UPGRADE_CantCreateNewSymlink",
                                ex, linkFile, ex.getLocalizedMessage());
            }
        }
        return linkFile;
    }

    Optional<Path> findSymlink(Path parentPath) throws IOException {
        return Files.list(parentPath).filter((p) -> {
            if (!Files.isSymbolicLink(p)) {
                return false;
            }
            Path target;
            try {
                target = Files.readSymbolicLink(p);
                return Files.isSameFile(p.resolveSibling(target), currentInstallPath);
            } catch (IOException ex) {
                // OK, symlink unreadable
                return false;
            }
        }).findAny();
    }

    Path existingSymlink() throws IOException {
        Path parentPath = getInstallPath().getParent();
        Optional<Path> existingLink = findSymlink(parentPath);
        if (!existingLink.isPresent()) {
            existingLink = findSymlink(getInstallPath().toRealPath().getParent());
        }
        return existingLink.orElse(null);
    }

    @Override
    void installContent() throws IOException {
        Path registryPath = getInstalledPath().resolve(SystemUtils.fromCommonRelative(CommonConstants.PATH_COMPONENT_STORAGE));
        Files.createDirectories(registryPath);
        super.installContent();
    }

    /**
     * Does not write to the new installation, and extracts version info from the GraalVM core
     * component.
     */
    static class TransientStorage implements ManagementStorage {
        private final Map<String, String> graalCaps = new HashMap<>();

        TransientStorage(Version newGraalVersion, Map<String, String> graalCaps, Map<String, Object> newCaps) {
            this.graalCaps.putAll(graalCaps);
            graalCaps.put(BundleConstants.GRAAL_VERSION, newGraalVersion.toString());
            for (String s : newCaps.keySet()) {
                graalCaps.put(s, newCaps.get(s).toString());
            }
        }

        @Override
        public void deleteComponent(String id) throws IOException {
        }

        @Override
        public Set<String> listComponentIDs() throws IOException {
            return Collections.emptySet();
        }

        @Override
        public ComponentInfo loadComponentFiles(ComponentInfo ci) throws IOException {
            return ci;
        }

        @Override
        public Set<ComponentInfo> loadComponentMetadata(String id) throws IOException {
            // no component is present
            return null;
        }

        @Override
        public Map<String, String> loadGraalVersionInfo() {
            return Collections.unmodifiableMap(graalCaps);
        }

        @Override
        public Map<String, Collection<String>> readReplacedFiles() throws IOException {
            return Collections.emptyMap();
        }

        @Override
        public void saveComponent(ComponentInfo info) throws IOException {
        }

        @Override
        public void updateReplacedFiles(Map<String, Collection<String>> replacedFiles) throws IOException {
        }

        @Override
        public Date licenseAccepted(ComponentInfo info, String licenseID) {
            return null;
        }

        @Override
        public void recordLicenseAccepted(ComponentInfo info, String licenseID, String licenseText, Date d) throws IOException {
        }

        @Override
        public Map<String, Collection<String>> findAcceptedLicenses() {
            return Collections.emptyMap();
        }

        @Override
        public String licenseText(String licID) {
            return "";
        }
    }
}
