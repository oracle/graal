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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.graalvm.component.installer.Archive;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.ComponentCatalog;
import org.graalvm.component.installer.ComponentCollection;
import org.graalvm.component.installer.ComponentInstaller;
import org.graalvm.component.installer.ComponentIterable;
import org.graalvm.component.installer.ComponentParam;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.FileOperations;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.UnknownVersionException;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.DirectoryStorage;
import org.graalvm.component.installer.persist.MetadataLoader;
import org.graalvm.component.installer.remote.CatalogIterable;

/**
 * Drives the GraalVM core upgrade process.
 * 
 * @author sdedic
 */
public class UpgradeProcess implements AutoCloseable {
    private final CommandInput input;
    private final Feedback feedback;
    private final ComponentCollection catalog;

    private final Set<String> existingComponents = new HashSet<>();
    private final Set<ComponentParam> addComponents = new HashSet<>();
    private final Set<ComponentInfo> migrated = new HashSet<>();
    private final Set<String> explicitIds = new HashSet<>();

    private ComponentInfo targetInfo;
    private Path newInstallPath;
    private Path newGraalHomePath;
    private MetadataLoader metaLoader;
    private boolean allowMissing;
    private ComponentRegistry newGraalRegistry;
    private Version minVersion = Version.NO_VERSION;

    public UpgradeProcess(CommandInput input, Feedback feedback, ComponentCollection catalog) {
        this.input = input;
        this.feedback = feedback.withBundle(UpgradeProcess.class);
        this.catalog = catalog;
        resetExistingComponents();
    }

    final void resetExistingComponents() {
        existingComponents.clear();
        existingComponents.addAll(input.getLocalRegistry().getComponentIDs().stream().filter((id) -> {
            ComponentInfo info = input.getLocalRegistry().findComponent(id);
            // only auto-include the 'leaf' components
            return info != null && input.getLocalRegistry().findDependentComponents(info, false).isEmpty();
        }).collect(Collectors.toList()));
        existingComponents.remove(BundleConstants.GRAAL_COMPONENT_ID);
    }

    /**
     * Adds a component to install to the upgraded core.
     * 
     * @param info the component to install.
     */
    public void addComponent(ComponentParam info) throws IOException {
        addComponents.add(info);
        explicitIds.add(info.createMetaLoader().getComponentInfo().getId());
    }

    public Set<ComponentParam> addedComponents() {
        return addComponents;
    }

    public boolean isAllowMissing() {
        return allowMissing;
    }

    public void setAllowMissing(boolean allowMissing) {
        this.allowMissing = allowMissing;
    }

    Path getNewInstallPath() {
        return newInstallPath;
    }

    public List<ComponentParam> allComponents() throws IOException {
        Set<String> ids = new HashSet<>();
        ArrayList<ComponentParam> allComps = new ArrayList<>(addedComponents());
        for (ComponentParam p : allComps) {
            ids.add(p.createMetaLoader().getComponentInfo().getId());
        }
        for (ComponentInfo mig : migrated) {
            if (ids.contains(mig.getId())) {
                continue;
            }
            allComps.add(input.existingFiles().createParam(mig.getId(), mig));
        }
        return allComps;
    }

    /**
     * Access to {@link ComponentRegistry} in the new instance.
     * 
     * @return registry in the new instance.
     */
    public ComponentRegistry getNewGraalRegistry() {
        return newGraalRegistry;
    }

    /**
     * Finds parent path for the new GraalVM installation. Note that on MacOs X the "JAVA_HOME" is
     * below the installation root, so on MacOS X the installation root is returned.
     * 
     * @return installation root for the core package.
     */
    Path findGraalVMParentPath() {
        Path vmRoot = input.getGraalHomePath().normalize();
        if (vmRoot.getNameCount() == 0) {
            return null;
        }
        Path skipPath = SystemUtils.getGraalVMJDKRoot(input.getLocalRegistry());
        Path skipped = vmRoot;
        while (skipPath != null && skipped != null && skipPath.getNameCount() > 0 &&
                        Objects.equals(skipPath.getFileName(), skipped.getFileName())) {
            skipPath = skipPath.getParent();
            skipped = skipped.getParent();
        }
        if (skipPath == null || skipPath.getNameCount() == 0) {
            vmRoot = skipped;
        }
        Path parent = vmRoot.getParent();
        // ensure the parent directory is still writable:
        if (parent != null && !Files.isWritable(parent)) {
            throw feedback.failure("UPGRADE_DirectoryNotWritable", null, parent);
        }
        return parent;
    }

    /**
     * Defines name for the install path. The GraalVM core package may define "edition" capability,
     * which places "ee" in the name.
     * 
     * @param graal new Graal core component
     * @return Path to the installation directory
     */
    Path createInstallName(ComponentInfo graal) {
        Path base = findGraalVMParentPath();
        String ed = graal.getProvidedValue(CommonConstants.CAP_EDITION, String.class);
        if (ed == null) {
            ed = input.getLocalRegistry().getGraalCapabilities().get(CommonConstants.CAP_EDITION);
        }
        String dirName = feedback.l10n(
                        ed == null ? "UPGRADE_GraalVMDirName@" : "UPGRADE_GraalVMDirNameEdition@",
                        graal.getVersion().displayString(),
                        ed);
        return base.resolve(SystemUtils.fileName(dirName));
    }

    /**
     * Prepares the installation of the core Component. Returns {@code false} if the upgrade is not
     * necessary or not found.
     * 
     * @param info
     * @return true, if the graalvm should be updated.
     * @throws IOException
     */
    boolean prepareInstall(ComponentInfo info) throws IOException {
        Version min = input.getLocalRegistry().getGraalVersion();
        if (info == null) {
            feedback.message("UPGRADE_NoUpdateFound", min.displayString());
            return false;
        }
        if (min.compareTo(info.getVersion()) >= 0) {
            feedback.message("UPGRADE_NoUpdateLatestVersion", min.displayString());
            migrated.clear();
            return false;
        }

        Path reported = createInstallName(info);
        // there's a slight chance this will be different from the final name ...
        feedback.output("UPGRADE_PreparingInstall", info.getVersion().displayString(), reported);
        failIfDirectotyExistsNotEmpty(reported);

        // force download
        ComponentParam param = input.existingFiles().createParam("core", info);
        metaLoader = param.createFileLoader();
        ComponentInfo completeInfo = metaLoader.completeMetadata();
        newInstallPath = createInstallName(completeInfo);
        newGraalHomePath = newInstallPath;
        failIfDirectotyExistsNotEmpty(newInstallPath);

        if (!reported.equals(newInstallPath)) {
            feedback.error("UPGRADE_WarningEditionDifferent", null, info.getVersion().displayString(), newInstallPath);
        }

        existingComponents.addAll(input.getLocalRegistry().getComponentIDs());
        existingComponents.remove(BundleConstants.GRAAL_COMPONENT_ID);
        return true;
    }

    void failIfDirectotyExistsNotEmpty(Path target) throws IOException {
        if (!Files.exists(target)) {
            return;
        }
        if (!Files.isDirectory(target)) {
            throw feedback.failure("UPGRADE_TargetExistsNotDirectory", null, target);
        }
        Path ghome = target.resolve(SystemUtils.getGraalVMJDKRoot(input.getLocalRegistry()));
        Path relFile = ghome.resolve("release");
        if (Files.isReadable(relFile)) {
            Version targetVersion = null;
            try {
                ComponentRegistry reg = createRegistryFor(ghome);
                targetVersion = reg.getGraalVersion();
            } catch (FailedOperationException ex) {
                // ignore
            }
            if (targetVersion != null) {
                throw feedback.failure("UPGRADE_TargetExistsContainsGraalVM", null, target, targetVersion.displayString());
            }
        }
        if (Files.list(target).findFirst().isPresent()) {
            throw feedback.failure("UPGRADE_TargetExistsNotEmpty", null, target);
        }
    }

    /**
     * Completes the component info, loads symlinks, permissions. Same as
     * {@link InstallCommand#createInstaller}.
     */
    GraalVMInstaller createGraalVMInstaller(ComponentInfo info) throws IOException {
        ComponentParam p = input.existingFiles().createParam(info.getId(), info);
        MetadataLoader ldr = p.createFileLoader();
        ldr.loadPaths();
        if (p.isComplete()) {
            Archive a;
            a = ldr.getArchive();
            a.verifyIntegrity(input);
        }
        ComponentInfo completeInfo = ldr.getComponentInfo();
        targetInfo = completeInfo;
        metaLoader = ldr;

        GraalVMInstaller gvmInstaller = new GraalVMInstaller(feedback,
                        input.getFileOperations(),
                        input.getLocalRegistry(), completeInfo, catalog,
                        metaLoader.getArchive());
        gvmInstaller.setCurrentInstallPath(input.getGraalHomePath());
        gvmInstaller.setInstallPath(newInstallPath);
        gvmInstaller.setPermissions(ldr.loadPermissions());
        gvmInstaller.setSymlinks(ldr.loadSymlinks());
        newGraalHomePath = gvmInstaller.getInstalledPath();
        return gvmInstaller;
    }

    public boolean installGraalCore(ComponentInfo info) throws IOException {
        if (!prepareInstall(info)) {
            return false;
        }

        GraalVMInstaller gvmInstaller = createGraalVMInstaller(info);

        feedback.output("UPGRADE_InstallingCore", info.getVersion().displayString(), newInstallPath.toString());
        gvmInstaller.install();

        Path installed = gvmInstaller.getInstalledPath();
        newGraalRegistry = createRegistryFor(installed);
        migrateLicenses();
        return true;
    }

    private ComponentRegistry createRegistryFor(Path home) {
        DirectoryStorage dst = new DirectoryStorage(
                        feedback.withBundle(ComponentInstaller.class),
                        home.resolve(SystemUtils.fromCommonRelative(CommonConstants.PATH_COMPONENT_STORAGE)),
                        home);
        dst.setJavaVersion(input.getLocalRegistry().getJavaVersion());
        return new ComponentRegistry(feedback, dst);
    }

    /**
     * Checks if the candidate GraalVM satisfies all dependencies of added components. Added
     * components are those specified on the commandline;
     * 
     * @param candidate candidate GraalVM component
     * @return broken components
     */
    Collection<ComponentInfo> satisfiedAddedComponents(ComponentInfo candidate) throws IOException {
        List<ComponentInfo> broken = new ArrayList<>();
        Version gv = candidate.getVersion();
        Version.Match satisfies = gv.match(Version.Match.Type.COMPATIBLE);
        for (ComponentParam param : addComponents) {
            ComponentInfo in = param.createMetaLoader().getComponentInfo();
            String vs = in.getRequiredGraalValues().get(BundleConstants.GRAAL_VERSION);
            Version cv = Version.fromString(vs);
            if (!satisfies.test(cv)) {
                broken.add(in);
                if (minVersion.compareTo(cv) < 0) {
                    minVersion = cv;
                }
            }
        }
        return broken;
    }

    Set<ComponentInfo> findInstallables(ComponentInfo graal) {
        Version gv = graal.getVersion();
        Version.Match satisfies = gv.match(Version.Match.Type.COMPATIBLE);
        Set<ComponentInfo> ret = new HashSet<>();
        for (String id : existingComponents) {
            if (explicitIds.contains(id)) {
                continue;
            }
            Collection<ComponentInfo> cis = catalog.loadComponents(id, satisfies, false);
            if (cis == null || cis.isEmpty()) {
                continue;
            }
            List<ComponentInfo> versions = new ArrayList<>(cis);
            ret.add(versions.get(versions.size() - 1));
        }
        return ret;
    }

    public ComponentInfo getTargetInfo() {
        return targetInfo;
    }

    public ComponentInfo findGraalVersion(Version.Match minimum) throws IOException {
        Version.Match filter;
        if (minimum.getType() == Version.Match.Type.MOSTRECENT) {
            filter = minimum.getVersion().match(Version.Match.Type.INSTALLABLE);
        } else {
            filter = minimum;
        }
        Collection<ComponentInfo> graals;
        try {
            graals = catalog.loadComponents(BundleConstants.GRAAL_COMPONENT_ID,
                            filter, false);
            if (graals == null || graals.isEmpty()) {
                return null;
            }
        } catch (UnknownVersionException ex) {
            // could not find anything to match the user version against
            if (ex.getCandidate() == null) {
                throw feedback.failure("UPGRADE_NoSpecificVersion", ex, filter.getVersion().displayString());
            } else {
                throw feedback.failure("UPGRADE_NoSpecificVersion2", ex, filter.getVersion().displayString(), ex.getCandidate().displayString());
            }
        }
        List<ComponentInfo> versions = new ArrayList<>(graals);
        Collections.sort(versions, ComponentInfo.versionComparator().reversed());
        for (Iterator<ComponentInfo> it = versions.iterator(); it.hasNext();) {
            ComponentInfo candidate = it.next();
            Collection<ComponentInfo> broken = satisfiedAddedComponents(candidate);
            if (!broken.isEmpty()) {
                it.remove();
            }
        }
        if (versions.isEmpty()) {
            throw feedback.failure("UPGRADE_NoVersionSatisfiesComponents", null, minVersion.toString());
        }

        Set<ComponentInfo> installables = null;
        Set<ComponentInfo> first = null;
        ComponentInfo result = null;
        Set<String> toMigrate = new HashSet<>(existingComponents);
        toMigrate.removeAll(explicitIds);
        for (Iterator<ComponentInfo> it = versions.iterator(); it.hasNext();) {
            ComponentInfo candidate = it.next();
            Set<ComponentInfo> instCandidates = findInstallables(candidate);
            if (first == null) {
                first = instCandidates;
            }
            if (allowMissing || instCandidates.size() == toMigrate.size()) {
                installables = instCandidates;
                result = candidate;
                break;
            }
        }
        if (installables == null) {
            if (!allowMissing) {
                throw feedback.failure("UPGRADE_ComponentsCannotMigrate", null);
            }
            if (versions.isEmpty()) {
                throw feedback.failure("UPGRADE_NoVersionSatisfiesComponents", null);
            }
            result = versions.get(0);
            installables = first;
        }
        migrated.clear();
        // if the result GraalVM is identical to current, do not migrate anything.
        if (result != null && !input.getLocalRegistry().getGraalVersion().equals(result.getVersion())) {
            migrated.addAll(installables);
            targetInfo = result;
        }
        return result;
    }

    public boolean didUpgrade() {
        return newGraalRegistry != null;
    }

    /*
     * public void identifyMigratedCompoents(ComponentInfo target) { if
     * (!satisfiedAddedComponents(target)) { throw
     * feedback.failure("UPGRADE_NoVersionSatisfiesComponents", null); } this.targetInfo = target;
     * this.addComponents.addAll(findInstallables(target)); }
     */

    public void migrateLicenses() {
        if (!SystemUtils.isLicenseTrackingEnabled()) {
            return;
        }
        feedback.output("UPGRADE_MigratingLicenses", input.getLocalRegistry().getGraalVersion().displayString(),
                        targetInfo.getVersion().displayString());
        for (Map.Entry<String, Collection<String>> e : input.getLocalRegistry().getAcceptedLicenses().entrySet()) {
            String compId = e.getKey();

            for (String licId : e.getValue()) {
                try {
                    String t = input.getLocalRegistry().licenseText(licId);
                    ComponentInfo info = input.getLocalRegistry().findComponent(compId);
                    Date d = input.getLocalRegistry().isLicenseAccepted(info, licId);
                    newGraalRegistry.acceptLicense(info, licId, t, d);
                } catch (FailedOperationException ex) {
                    feedback.error("UPGRADE_CannotMigrateLicense", ex, compId, licId);
                }
            }
        }
    }

    protected InstallCommand configureInstallCommand(InstallCommand instCommand) throws IOException {
        List<ComponentParam> params = new ArrayList<>();
        // add migrated components
        params.addAll(allComponents());
        if (params.isEmpty()) {
            return null;
        }
        instCommand.init(new InputDelegate(params), feedback);
        instCommand.setAllowUpgrades(true);
        instCommand.setForce(true);
        return instCommand;
    }

    public void installAddedComponents() throws IOException {
        // install all the components
        InstallCommand ic = configureInstallCommand(new InstallCommand());
        if (ic != null) {
            ic.execute();
        }
    }

    @Override
    public void close() throws IOException {
        for (ComponentParam p : allComponents()) {
            p.close();
        }
        if (metaLoader != null) {
            metaLoader.close();
        }
    }

    /**
     * The class provides a new local registry in the new installation, and a new component
     * registry, for the target graalvm version.
     */
    class InputDelegate implements CommandInput {
        private final List<ComponentParam> params;
        private int index;
        private ComponentCatalog remoteRegistry;

        InputDelegate(List<ComponentParam> params) {
            this.params = params;
        }

        @Override
        public FileOperations getFileOperations() {
            return input.getFileOperations();
        }

        @Override
        public CatalogFactory getCatalogFactory() {
            return input.getCatalogFactory();
        }

        @Override
        public ComponentIterable existingFiles() throws FailedOperationException {
            return new ComponentIterable() {
                @Override
                public void setVerifyJars(boolean verify) {
                    input.existingFiles().setVerifyJars(verify);
                }

                @Override
                public ComponentParam createParam(String cmdString, ComponentInfo info) {
                    return new CatalogIterable.CatalogItemParam(
                                    getRegistry().getDownloadInterceptor(),
                                    info,
                                    info.getName(),
                                    cmdString,
                                    feedback,
                                    input.optValue(Commands.OPTION_NO_DOWNLOAD_PROGRESS) == null);
                }

                @Override
                public Iterator<ComponentParam> iterator() {
                    return new Iterator<ComponentParam>() {
                        boolean init;

                        @Override
                        public boolean hasNext() {
                            if (!init) {
                                init = true;
                                index = 0;
                            }
                            return index < params.size();
                        }

                        @Override
                        public ComponentParam next() {
                            if (index >= params.size()) {
                                throw new NoSuchElementException();
                            }
                            return params.get(index++);
                        }

                    };
                }

                @Override
                public ComponentIterable matchVersion(Version.Match m) {
                    return this;
                }

                @Override
                public ComponentIterable allowIncompatible() {
                    return this;
                }
            };
        }

        @Override
        public String requiredParameter() throws FailedOperationException {
            if (index >= params.size()) {
                throw feedback.failure("UPGRADE_MissingParameter", null);
            }
            return nextParameter();
        }

        @Override
        public String nextParameter() {
            if (!hasParameter()) {
                return null;
            }
            return params.get(index++).getSpecification();
        }

        @Override
        public String peekParameter() {
            if (!hasParameter()) {
                return null;
            }
            return params.get(index).getSpecification();
        }

        @Override
        public boolean hasParameter() {
            return params.size() > index;
        }

        @Override
        public Path getGraalHomePath() {
            return didUpgrade() ? newGraalHomePath : input.getGraalHomePath();
        }

        @Override
        public ComponentCatalog getRegistry() {
            if (remoteRegistry == null) {
                remoteRegistry = input.getCatalogFactory().createComponentCatalog(this, getLocalRegistry());
            }
            return remoteRegistry;
        }

        @Override
        public ComponentRegistry getLocalRegistry() {
            if (newGraalRegistry != null) {
                return newGraalRegistry;
            } else {
                return input.getLocalRegistry();
            }
        }

        @Override
        public String optValue(String option) {
            return input.optValue(option);
        }

        @Override
        public String getParameter(String key, boolean cmdLine) {
            return input.getParameter(key, cmdLine);
        }

        @Override
        public Map<String, String> parameters(boolean cmdLine) {
            return input.parameters(cmdLine);
        }
    }
}
