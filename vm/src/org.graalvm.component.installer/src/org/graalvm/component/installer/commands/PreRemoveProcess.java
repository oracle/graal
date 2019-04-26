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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.graalvm.component.installer.CommonConstants;
import static org.graalvm.component.installer.CommonConstants.WARN_REBUILD_IMAGES;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;

/**
 *
 * @author sdedic
 */
public class PreRemoveProcess {
    private static final Set<PosixFilePermission> ALL_WRITE_PERMS = PosixFilePermissions.fromString("rwxrwxrwx");

    private final Path installPath;
    private final Feedback feedback;
    private final List<ComponentInfo> infos = new ArrayList<>();
    private boolean rebuildPolyglot;

    private boolean dryRun;
    private boolean ignoreFailedDeletions;
    private Set<String> knownPaths;

    public PreRemoveProcess(Path instPath, Feedback fb) {
        this.feedback = fb.withBundle(PreRemoveProcess.class);
        installPath = instPath;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public PreRemoveProcess setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
        return this;
    }

    public boolean isIgnoreFailedDeletions() {
        return ignoreFailedDeletions;
    }

    public PreRemoveProcess setIgnoreFailedDeletions(boolean ignoreFailedDeletions) {
        this.ignoreFailedDeletions = ignoreFailedDeletions;
        return this;
    }

    public void addComponentInfo(ComponentInfo info) {
        this.infos.add(info);
    }

    /**
     * Process all the components, prints message.
     * 
     * @throws IOException if deletion fails
     */
    public void run() throws IOException {
        for (ComponentInfo ci : infos) {
            processComponent(ci);
        }
        if (rebuildPolyglot && WARN_REBUILD_IMAGES) {
            Path p = SystemUtils.fromCommonString(CommonConstants.PATH_JRE_BIN);
            feedback.output("INSTALL_RebuildPolyglotNeeded", File.separator, installPath.resolve(p).normalize());
        }
    }

    /**
     * Called also from Uninstaller. Will delete one single file, possibly with altering permissions
     * on the parent so the file can be deleted.
     * 
     * @param p file to delete
     * @throws IOException
     */
    void deleteOneFile(Path p) throws IOException {
        try {
            deleteOneFile0(p);
        } catch (IOException ex) {
            if (ignoreFailedDeletions) {
                if (Files.isDirectory(p)) {
                    feedback.error("INSTALL_FailedToDeleteDirectory", ex, p, ex.getLocalizedMessage());
                } else {
                    feedback.error("INSTALL_FailedToDeleteFile", ex, p, ex.getLocalizedMessage());
                }
                return;
            }
            throw ex;
            // throw new UncheckedIOException(ex);
        }
    }

    private void deleteOneFile0(Path p) throws IOException {
        try {
            if (p.equals(installPath)) {
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
                if (d == null) {
                    throw new IOException("Cannot determine parent of " + p);
                }
                if (d.startsWith(installPath) && !d.equals(installPath)) {
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

    /**
     * Also called from Uninstaller.
     * 
     * @param rootPath root path to delete (inclusive)
     * @throws IOException if the deletion fails.
     */
    void deleteContentsRecursively(Path rootPath) throws IOException {
        if (dryRun) {
            return;
        }
        try (Stream<Path> paths = Files.walk(rootPath)) {
            paths.sorted(Comparator.reverseOrder()).forEach((p) -> {
                try {
                    if (shouldDeletePath(p)) {
                        deleteOneFile(p);
                    }
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            });
        } catch (UncheckedIOException ex) {
            throw ex.getCause();
        }
    }

    private boolean shouldDeletePath(Path toDelete) {
        Path rel;
        try {
            rel = installPath.relativize(toDelete);
        } catch (IllegalArgumentException ex) {
            // cannot relativize; avoid to delete such thing.
            return false;
        }
        String relString = SystemUtils.toCommonPath(rel);
        if (Files.isDirectory(toDelete)) {
            relString += "/"; // NOI18N
        }
        return !knownPaths.contains(relString);
    }

    void processComponent(ComponentInfo ci) throws IOException {
        rebuildPolyglot |= ci.isPolyglotRebuild();
        for (String s : ci.getWorkingDirectories()) {
            Path p = installPath.resolve(SystemUtils.fromCommonRelative(s));
            feedback.verboseOutput("UNINSTALL_DeletingDirectoryRecursively", p);
            this.knownPaths = new HashSet<>(ci.getPaths());
            deleteContentsRecursively(p);
        }
    }
}
