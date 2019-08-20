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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.FileOperations;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;

public class Uninstaller {
    private final Feedback feedback;
    private final ComponentInfo componentInfo;
    private final ComponentRegistry registry;
    private final FileOperations fileOps;

    private PreRemoveProcess preRemove;
    private Set<String> preservePaths = Collections.emptySet();
    private boolean dryRun;
    private boolean ignoreFailedDeletions;
    private Path installPath;
    private boolean rebuildPolyglot;

    private final Set<String> directoriesToDelete = new HashSet<>();

    public Uninstaller(Feedback feedback, FileOperations fops, ComponentInfo componentInfo, ComponentRegistry registry) {
        this.feedback = feedback;
        this.componentInfo = componentInfo;
        this.registry = registry;
        this.fileOps = fops;
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

    void uninstallContent() throws IOException {
        preRemove = new PreRemoveProcess(installPath, fileOps, feedback)
                        .setDryRun(isDryRun())
                        .setIgnoreFailedDeletions(isIgnoreFailedDeletions());
        // remove all the files occupied by the component
        O: for (String p : componentInfo.getPaths()) {
            if (preservePaths.contains(p)) {
                feedback.verboseOutput("INSTALL_SkippingSharedFile", p);
                continue;
            }
            // assert relative path
            Path toDelete = installPath.resolve(SystemUtils.fromCommonRelative(p));
            if (Files.isDirectory(toDelete)) {
                for (String s : preservePaths) {
                    Path x = SystemUtils.fromCommonRelative(s);
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
                // ignore missing files, handle permissions
                preRemove.deleteOneFile(toDelete);
            }
        }
        List<String> dirNames = new ArrayList<>(directoriesToDelete);
        preRemove.processComponent(componentInfo);
        Collections.sort(dirNames);
        Collections.reverse(dirNames);
        for (String s : dirNames) {
            Path p = installPath.resolve(SystemUtils.fromCommonRelative(s));
            feedback.verboseOutput("UNINSTALL_DeletingDirectory", p);
            if (!dryRun) {
                try {
                    fileOps.deleteFile(p);
                } catch (IOException ex) {
                    if (ignoreFailedDeletions) {
                        feedback.error("INSTALL_FailedToDeleteDirectory", ex, p, ex.getLocalizedMessage());
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
