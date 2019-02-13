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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;

public class Uninstaller {
    private final Feedback feedback;
    private final ComponentInfo componentInfo;
    private final ComponentRegistry registry;
    private Set<String> preservePaths = Collections.emptySet();
    private boolean dryRun;
    private boolean ignoreFailedDeletions;
    private Path installPath;
    private boolean rebuildPolyglot;

    private final Set<String> directoriesToDelete = new HashSet<>();

    public Uninstaller(Feedback feedback, ComponentInfo componentInfo, ComponentRegistry registry) {
        this.feedback = feedback;
        this.componentInfo = componentInfo;
        this.registry = registry;
    }

    public void uninstall() throws IOException {
        uninstallContent();
        if (!isDryRun()) {
            registry.removeComponent(componentInfo);
        }
    }

    public boolean isRebuildPolyglot() {
        return rebuildPolyglot;
    }

    void deleteContentsRecursively(Path rootPath) throws IOException {
        if (dryRun) {
            return;
        }
        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.sorted(Comparator.reverseOrder()).forEach((p) -> {
                try {
                    deleteOneFile(p, rootPath);
                } catch (IOException ex) {
                    if (ignoreFailedDeletions) {
                        if (Files.isDirectory(p)) {
                            feedback.error("INSTALL_FailedToDeleteDirectory", ex, p, ex.getMessage());
                        } else {
                            feedback.error("INSTALL_FailedToDeleteFile", ex, p, ex.getMessage());
                        }
                        return;
                    }
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private static final Set<PosixFilePermission> ALL_WRITE_PERMS = PosixFilePermissions.fromString("rwxrwxrwx");

    private void deleteOneFile(Path p, Path rootPath) throws IOException {
        try {
            if (p.equals(rootPath)) {
                return;
            }
            Files.deleteIfExists(p);
        } catch (AccessDeniedException ex) {
            // try again to adjust permissions for the file AND the containing
            // directory AND try again:
            PosixFileAttributeView attrs = Files.getFileAttributeView(
                            p, PosixFileAttributeView.class);
            Set<PosixFilePermission> restoreDirPermissions = null;
            if (attrs != null) {
                Files.setPosixFilePermissions(p, ALL_WRITE_PERMS);
                Path d = p.getParent();
                // set the parent directory's permissions, but do not
                // alter permissions outside the to-be-deleted tree:
                if (d.startsWith(rootPath) && !d.equals(rootPath)) {
                    restoreDirPermissions = Files.getPosixFilePermissions(d);
                    Files.setPosixFilePermissions(d, ALL_WRITE_PERMS);
                }
                try {
                    // 2nd try
                    Files.deleteIfExists(p);
                } catch (IOException ex2) {
                    // report the original access denied
                    throw ex;
                } finally {
                    if (restoreDirPermissions != null) {
                        try {
                            Files.setPosixFilePermissions(d, restoreDirPermissions);
                        } catch (IOException ex2) {
                            // do not obscure the result with this exception
                            feedback.error("UNINSTALL_ErrorRestoringPermissions", ex2, p, ex2.getLocalizedMessage());
                        }
                    }
                }
            }
        }
    }

    void uninstallContent() throws IOException {
        // remove all the files occupied by the component
        O: for (String p : componentInfo.getPaths()) {
            if (preservePaths.contains(p)) {
                feedback.verboseOutput("INSTALL_SkippingSharedFile", p);
                continue;
            }
            Path toDelete = installPath.resolve(SystemUtils.fromCommonString(p));
            if (Files.isDirectory(toDelete)) {
                for (String s : preservePaths) {
                    Path x = SystemUtils.fromCommonString(s);
                    if (x.startsWith(p)) {
                        // will not delete directory with something shared or system.
                        continue O;
                    }
                }
                directoriesToDelete.add(p);
                continue;
            }
            feedback.verboseOutput("UNINSTALL_DeletingFile", p);
            if (!dryRun) {
                try {
                    // ignore missing files, handle permissions
                    deleteOneFile(toDelete, installPath);
                } catch (IOException ex) {
                    if (ignoreFailedDeletions) {
                        feedback.error("INSTALL_FailedToDeleteFile", ex, toDelete, ex.getMessage());
                    } else {
                        throw ex;
                    }
                }
            }
        }
        List<String> dirNames = new ArrayList<>(directoriesToDelete);
        for (String s : componentInfo.getWorkingDirectories()) {
            Path p = installPath.resolve(SystemUtils.fromCommonString(s));
            feedback.verboseOutput("UNINSTALL_DeletingDirectoryRecursively", p);
            if (componentInfo.getWorkingDirectories().contains(s)) {
                deleteContentsRecursively(p);
            }
        }
        Collections.sort(dirNames);
        Collections.reverse(dirNames);
        for (String s : dirNames) {
            Path p = installPath.resolve(SystemUtils.fromCommonString(s));
            feedback.verboseOutput("UNINSTALL_DeletingDirectory", p);
            if (!dryRun) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ex) {
                    if (ignoreFailedDeletions) {
                        feedback.error("INSTALL_FailedToDeleteDirectory", ex, p, ex.getMessage());
                    } else {
                        throw ex;
                    }
                }
            }
        }

        rebuildPolyglot = componentInfo.isPolyglotRebuild() ||
                        componentInfo.getPaths().stream().filter(p -> !p.endsWith("/") && p.startsWith(CommonConstants.PATH_POLYGLOT_REGISTRY))
                                        .findAny()
                                        .isPresent();

    }

    public boolean isIgnoreFailedDeletions() {
        return ignoreFailedDeletions;
    }

    public void setIgnoreFailedDeletions(boolean ignoreFailedDeletions) {
        this.ignoreFailedDeletions = ignoreFailedDeletions;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public Path getInstallPath() {
        return installPath;
    }

    public void setInstallPath(Path installPath) {
        this.installPath = installPath;
    }

    public Set<String> getPreservePaths() {
        return preservePaths;
    }

    public void setPreservePaths(Set<String> preservePaths) {
        this.preservePaths = preservePaths;
    }

    public ComponentInfo getComponentInfo() {
        return componentInfo;
    }
}
