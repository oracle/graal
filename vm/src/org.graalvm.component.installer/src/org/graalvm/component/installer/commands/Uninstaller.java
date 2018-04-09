/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer.commands;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;

/**
 *
 * @author sdedic
 */
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

    void uninstallContent() throws IOException {
        // remove all the files occupied by the component
        O: for (String p : componentInfo.getPaths()) {
            if (preservePaths.contains(p)) {
                feedback.verboseOutput("INSTALL_SkippingSharedFile", p);
                continue;
            }
            Path toDelete = installPath.resolve(p);
            if (Files.isDirectory(toDelete)) {
                for (String s : preservePaths) {
                    Path x = Paths.get(s);
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
                    // ignore missing files
                    Files.deleteIfExists(toDelete);
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
        Collections.sort(dirNames);
        Collections.reverse(dirNames);
        for (String s : dirNames) {
            Path p = installPath.resolve(s);
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
                    componentInfo.getPaths().stream()
                        .filter(
                            (p) -> 
                                !p.endsWith("/") &&
                                p.startsWith(CommonConstants.PATH_POLYGLOT_REGISTRY))
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
