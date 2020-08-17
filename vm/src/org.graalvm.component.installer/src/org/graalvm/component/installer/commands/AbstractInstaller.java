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

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.graalvm.component.installer.Archive;
import org.graalvm.component.installer.ComponentCollection;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.FileOperations;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.model.Verifier;

/**
 * Abstract configurable base for installer. The real installer and test stub inherits from this
 * base; are configured by InstallCommand.
 * 
 * @author sdedic
 */
public abstract class AbstractInstaller implements Closeable {
    protected final Feedback feedback;
    protected final ComponentInfo componentInfo;
    protected final ComponentRegistry registry;
    protected final ComponentCollection catalog;
    protected final Archive archive;
    protected final FileOperations fileOps;

    private Map<String, String> permissions = Collections.emptyMap();
    private Map<String, String> symlinks = Collections.emptyMap();
    private Set<String> componentDirectories = Collections.emptySet();
    private final Set<String> trackedPaths = new HashSet<>();
    private Path licenseRelativePath;
    private boolean replaceDiferentFiles;
    private boolean replaceComponents;
    private boolean dryRun;
    private boolean ignoreRequirements;
    private boolean failOnExisting = true;
    private Path installPath;
    private boolean allowUpgrades;

    public AbstractInstaller(Feedback fb, FileOperations fops, ComponentInfo info,
                    ComponentRegistry reg, ComponentCollection cat, Archive a) {
        this.feedback = fb;
        this.componentInfo = info;
        this.registry = reg;
        this.archive = a;
        this.catalog = cat;
        this.fileOps = fops;
    }

    public Archive getArchive() {
        return archive;
    }

    public boolean isComplete() {
        return archive != null;
    }

    public boolean isAllowUpgrades() {
        return allowUpgrades;
    }

    public void setAllowUpgrades(boolean allowUpgrades) {
        this.allowUpgrades = allowUpgrades;
    }

    public boolean isFailOnExisting() {
        return failOnExisting;
    }

    public void setFailOnExisting(boolean failOnExisting) {
        this.failOnExisting = failOnExisting;
    }

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

    public void setSymlinks(Map<String, String> symlinks) {
        this.symlinks = symlinks;
    }

    public abstract void revertInstall();

    public Verifier createVerifier() {
        return new Verifier(feedback, registry, catalog)
                        .ignoreRequirements(ignoreRequirements)
                        .replaceComponents(replaceComponents)
                        .ignoreExisting(!failOnExisting);
    }

    public Verifier validateRequirements() {
        Verifier vrf = createVerifier();
        return vrf.setVersionMatch(registry.getGraalVersion().match(Version.Match.Type.COMPATIBLE))
                        .validateRequirements(componentInfo);
    }

    /**
     * Validates requirements, decides whether to install. Returns false if the component should be
     * skipped.
     *
     * @return true, if the component should be installed
     * @throws IOException
     */
    public abstract boolean validateAll() throws IOException;

    public abstract void validateFiles() throws IOException;

    public abstract void validateSymlinks() throws IOException;

    public abstract void processPermissions() throws IOException;

    public abstract void createSymlinks() throws IOException;

    public Path getInstallPath() {
        return installPath;
    }

    public void setInstallPath(Path installPath) {
        this.installPath = installPath.normalize();
    }

    public Path getLicenseRelativePath() {
        return licenseRelativePath;
    }

    public void setLicenseRelativePath(Path licenseRelativePath) {
        this.licenseRelativePath = licenseRelativePath;
    }

    @Override
    public void close() throws IOException {
        if (archive != null) {
            archive.close();
        }
    }

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public abstract boolean isRebuildPolyglot();

    public Set<String> getComponentDirectories() {
        return componentDirectories;
    }

    public void setComponentDirectories(Set<String> componentDirectories) {
        this.componentDirectories = componentDirectories;
    }

    protected void addTrackedPath(String path) {
        trackedPaths.add(path);
    }

}
