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
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipException;
import org.graalvm.component.installer.Archive;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.ComponentInstaller;
import org.graalvm.component.installer.ComponentParam;
import org.graalvm.component.installer.DependencyException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.InstallerCommand;
import org.graalvm.component.installer.InstallerStopException;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.UserAbortException;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.DistributionType;
import org.graalvm.component.installer.model.Verifier;
import org.graalvm.component.installer.persist.MetadataLoader;

/**
 * Implementation of 'add' command.
 */
public class InstallCommand implements InstallerCommand {
    private static final Logger LOG = Logger.getLogger(InstallCommand.class.getName());

    private static final Map<String, String> OPTIONS = new HashMap<>();

    private CommandInput input;
    private Feedback feedback;
    private boolean ignoreFailures;
    private boolean force;
    private boolean validateBeforeInstall;
    private boolean validateDownload;
    private boolean allowUpgrades;
    // verify archives by default
    private boolean verifyJar = true;
    /**
     * Was a file:// component location encountered ?
     */
    private boolean wasFile;

    private PostInstProcess postinstHelper;

    static {
        OPTIONS.put(Commands.OPTION_DRY_RUN, "");
        OPTIONS.put(Commands.OPTION_FORCE, "");
        OPTIONS.put(Commands.OPTION_REPLACE_COMPONENTS, "");
        OPTIONS.put(Commands.OPTION_REPLACE_DIFFERENT_FILES, "");
        OPTIONS.put(Commands.OPTION_VALIDATE, "");
        OPTIONS.put(Commands.OPTION_VALIDATE_DOWNLOAD, "");
        OPTIONS.put(Commands.OPTION_IGNORE_FAILURES, "");
        OPTIONS.put(Commands.OPTION_FAIL_EXISTING, "");
        OPTIONS.put(Commands.OPTION_NO_DOWNLOAD_PROGRESS, "");
        OPTIONS.put(Commands.OPTION_NO_VERIFY_JARS, "");
        OPTIONS.put(Commands.OPTION_LOCAL_DEPENDENCIES, "");
        OPTIONS.put(Commands.OPTION_NO_DEPENDENCIES, "");

        OPTIONS.put(Commands.LONG_OPTION_DRY_RUN, Commands.OPTION_DRY_RUN);
        OPTIONS.put(Commands.LONG_OPTION_FORCE, Commands.OPTION_FORCE);
        OPTIONS.put(Commands.LONG_OPTION_REPLACE_COMPONENTS, Commands.OPTION_REPLACE_COMPONENTS);
        OPTIONS.put(Commands.LONG_OPTION_REPLACE_DIFFERENT_FILES, Commands.OPTION_REPLACE_DIFFERENT_FILES);
        OPTIONS.put(Commands.LONG_OPTION_VALIDATE, Commands.OPTION_VALIDATE);
        OPTIONS.put(Commands.LONG_OPTION_VALIDATE_DOWNLOAD, Commands.OPTION_VALIDATE_DOWNLOAD);
        OPTIONS.put(Commands.LONG_OPTION_IGNORE_FAILURES, Commands.OPTION_IGNORE_FAILURES);
        OPTIONS.put(Commands.LONG_OPTION_FAIL_EXISTING, Commands.OPTION_FAIL_EXISTING);
        OPTIONS.put(Commands.LONG_OPTION_NO_DOWNLOAD_PROGRESS, Commands.OPTION_NO_DOWNLOAD_PROGRESS);
        OPTIONS.put(Commands.LONG_OPTION_NO_VERIFY_JARS, Commands.OPTION_NO_VERIFY_JARS);
        OPTIONS.put(Commands.LONG_OPTION_LOCAL_DEPENDENCIES, Commands.OPTION_LOCAL_DEPENDENCIES);
        OPTIONS.put(Commands.LONG_OPTION_NO_DEPENDENCIES, Commands.OPTION_NO_DEPENDENCIES);

        OPTIONS.putAll(ComponentInstaller.componentOptions);
    }

    @Override
    public void init(CommandInput commandInput, Feedback feedBack) {
        this.input = commandInput;
        this.feedback = feedBack;

        ignoreFailures = this.input.optValue(Commands.OPTION_IGNORE_FAILURES) != null;
        validateBeforeInstall = this.input.optValue(Commands.OPTION_VALIDATE) != null;
        validateDownload = this.input.optValue(Commands.OPTION_VALIDATE_DOWNLOAD) != null;
        verifyJar = input.optValue(Commands.OPTION_NO_VERIFY_JARS) == null;
        postinstHelper = new PostInstProcess(input, feedBack);
    }

    public boolean isVerifyJar() {
        return verifyJar;
    }

    public void setVerifyJar(boolean verifyJar) {
        this.verifyJar = verifyJar;
    }

    public boolean isAllowUpgrades() {
        return allowUpgrades;
    }

    public void setAllowUpgrades(boolean allowUpgrades) {
        this.allowUpgrades = allowUpgrades;
    }

    @Override
    public Map<String, String> supportedOptions() {
        return OPTIONS;
    }

    public InstallCommand() {
    }

    /**
     * Installers attached to individual parameters.
     */
    Map<ComponentParam, Installer> realInstallers = new LinkedHashMap<>();

    private String current;

    private StringBuilder parameterList = new StringBuilder();

    /**
     * Minimum required GraalVM version for the to-be-installed content.
     */
    private Version minRequiredGraalVersion;

    protected void executionInit() throws IOException {
        input.getLocalRegistry().verifyAdministratorAccess();
        input.existingFiles().setVerifyJars(verifyJar);

        minRequiredGraalVersion = input.getLocalRegistry().getGraalVersion();
    }

    @Override
    public int execute() throws IOException {
        executionInit();
        if (input.optValue(Commands.OPTION_HELP) != null) {
            feedback.output("INSTALL_Help");
            return 0;
        }
        if (!input.hasParameter()) {
            feedback.output("INSTALL_ParametersMissing");
            return 1;
        }
        if (input.optValue(Commands.OPTION_LOCAL_DEPENDENCIES) != null &&
                        input.optValue(Commands.OPTION_FILES) == null) {
            feedback.error("INSTALL_WarnLocalDependencies", null);
        }
        try {
            executeStep(this::prepareInstallation, false);
            if (validateBeforeInstall) {
                return 0;
            }
            executeStep(this::completeInstallers, false);
            executeStep(this::acceptLicenses, false);
            executeStep(this::doInstallation, false);
            // execute the post-install steps for all processed installers
            executeStep(this::printMessages, true);
            /*
             * if (rebuildPolyglot && WARN_REBUILD_IMAGES) { Path p =
             * SystemUtils.fromCommonString(CommonConstants.PATH_JRE_BIN);
             * feedback.output("INSTALL_RebuildPolyglotNeeded", File.separator,
             * input.getGraalHomePath().resolve(p).normalize()); }
             */
        } finally {
            for (Map.Entry<ComponentParam, Installer> e : realInstallers.entrySet()) {
                ComponentParam p = e.getKey();
                Installer i = e.getValue();
                p.close();
                if (i != null) {
                    i.close();
                }
            }
        }
        return 0;
    }

    private Map<String, List<MetadataLoader>> licensesToAccept = new LinkedHashMap<>();

    void addLicenseToAccept(String id, MetadataLoader ldr) {
        licensesToAccept.computeIfAbsent(id, (x) -> new ArrayList<>()).add(ldr);
    }

    protected Version.Match matchInstallVesion() {
        return input.getLocalRegistry().getGraalVersion().match(
                        allowUpgrades ? Version.Match.Type.INSTALLABLE : Version.Match.Type.COMPATIBLE);
    }

    private void addLicenseToAccept(Installer inst, MetadataLoader ldr) {
        if (ldr.getLicenseType() != null) {
            String path = ldr.getLicensePath();
            if (path != null) {
                inst.setLicenseRelativePath(SystemUtils.fromCommonRelative(ldr.getLicensePath()));
            }
            String licId = ldr.getLicenseID();
            if (licId == null) {
                String tp = ldr.getLicenseType();
                if (Pattern.matches("[-_., 0-9A-Za-z]+", tp)) { // NOI18N
                    licId = tp;
                } else {
                    // better make a digest
                    try {
                        MessageDigest dg = MessageDigest.getInstance("SHA-256"); // NOI18N
                        byte[] result = dg.digest(tp.getBytes("UTF-8"));
                        licId = SystemUtils.fingerPrint(result, false);
                    } catch (NoSuchAlgorithmException | UnsupportedEncodingException ex) {
                        feedback.error("INSTALL_CannotDigestLicense", ex, ex.getLocalizedMessage());
                        licId = Integer.toHexString(tp.hashCode());
                    }
                }
            }
            addLicenseToAccept(licId, ldr);
        }
    }

    /**
     * Implied dependencies discovered during preparation.
     */
    private List<ComponentParam> dependencies = new ArrayList<>();
    private Map<String, Collection<ComponentInfo>> dependencyMap = new HashMap<>();

    /**
     * Unresolved dependencies, which will be reported all at once.
     */
    private Set<String> unresolvedDependencies = new HashSet<>();
    private Set<ComponentInfo> knownDeps = new HashSet<>();

    void cleanDependencies() {
        dependencies = new ArrayList<>();
        // do not clean knownDeps, prevents duplicates.
    }

    public List<ComponentParam> getDependencies() {
        return Collections.unmodifiableList(dependencies);
    }

    void addDependencies(ComponentInfo ci) {
        if (input.hasOption(Commands.OPTION_NO_DEPENDENCIES)) {
            return;
        }

        // dependencies are scanned breadth-first; so the deeper dependencies are
        // later in the iterator order. Installers from dependencies will be reversed
        // in registerComponent
        Set<ComponentInfo> deps = new LinkedHashSet<>();

        LOG.log(Level.FINE, "Inspecting dependencies of {0}", ci);
        Set<String> errors = input.getRegistry().findDependencies(ci, true, Boolean.FALSE, deps);
        LOG.log(Level.FINE, "Direct dependencies: {0}, errors: {1}", new Object[]{deps, errors});
        if (errors != null) {
            unresolvedDependencies.addAll(errors);
            for (String s : errors) {
                dependencyMap.computeIfAbsent(s, (id) -> new HashSet<>()).add(ci);
            }
        }

        for (ComponentInfo i : deps) {
            // knownDeps may contain multiple component versions, this
            // will be sorted later, when converting to Installers.
            if (!knownDeps.add(i)) {
                continue;
            }
            ComponentParam p = input.existingFiles().createParam(i.getId(), i);
            dependencies.add(p);
            dependencyMap.computeIfAbsent(i.getId(), (id) -> new HashSet<>()).add(ci);
        }
    }

    void printRequiredComponents() throws IOException {
        if (dependencies.isEmpty()) {
            return;
        }
        feedback.output("INSTALL_RequiredDependencies");
        for (ComponentParam p : dependencies) {
            ComponentInfo ci = p.createMetaLoader().getComponentInfo();
            feedback.output("INSTALL_RequiredDependencyLine", p.getDisplayName(), ci.getId(), ci.getVersion().displayString(),
                            printComponentList(dependencyMap.get(ci.getId())));
        }
    }

    boolean verifyInstaller(Installer inst) {
        ComponentInfo info = inst.getComponentInfo();
        Verifier vrf = inst.createVerifier();
        vrf.setVersionMatch(matchInstallVesion());
        vrf.validateRequirements(info);
        boolean keep = force || vrf.shouldInstall(info);
        if (!keep) {
            // component will be skipped, do not bother with validation
            feedback.output("INSTALL_ComponentAlreadyInstalled", inst.getComponentInfo().getName(), inst.getComponentInfo().getId());
            return false;
        }
        ComponentInfo existing = input.getLocalRegistry().findComponent(info.getId());
        if (existing != null) {
            // will refuse to install existing bundled components:
            if (existing.getDistributionType() != DistributionType.OPTIONAL) {
                throw new DependencyException.Conflict(
                                existing.getId(), info.getVersionString(), existing.getVersionString(),
                                feedback.l10n("INSTALL_CannotReplaceBundledComponent",
                                                existing.getName(), existing, existing.getVersionString()));
            }
        }
        Version minV = vrf.getMinVersion();
        if (minV != null && minV.compareTo(this.minRequiredGraalVersion) > 0) {
            minRequiredGraalVersion = minV;
        }
        addDependencies(info);
        return true;
    }

    String printComponentList(Collection<ComponentInfo> requestors) {
        if (requestors == null || requestors.isEmpty()) {
            return ""; // NOI18N
        }
        StringBuilder sb = new StringBuilder();
        List<ComponentInfo> infos = new ArrayList<>(requestors);
        Collections.sort(infos, (c1, c2) -> c1.getId().compareTo(c2.getId()));
        for (ComponentInfo i : infos) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(feedback.l10n("INSTALL_RequiredDependencyItem", i.getName(), i.getId()));
        }
        return feedback.l10n("INSTALL_RequiredDependencySuffix", sb.toString());
    }

    void checkDependencyErrors() {
        if (unresolvedDependencies.isEmpty()) {
            return;
        }
        feedback.error("INSTALL_UnknownComponents", null);
        List<String> ordered = new ArrayList<>(unresolvedDependencies);
        Collections.sort(ordered);
        for (String s : ordered) {
            feedback.error("INSTALL_UnknownComponentLine", null, s,
                            printComponentList(dependencyMap.get(s)));
        }
        if (!input.getRegistry().isRemoteEnabled()) {
            feedback.error("INSTALL_UnknownComponentsNote1", null, parameterList.toString());
        }
        if (wasFile && !input.hasOption(Commands.OPTION_LOCAL_DEPENDENCIES)) {
            feedback.error("INSTALL_UnknownComponentsNote2", null, parameterList.toString());
        }
        throw feedback.failure("INSTALL_UnresolvedDependencies", null);
    }

    private void appendParameterText() {
        String s = input.peekParameter();
        if (parameterList.length() > 0) {
            parameterList.append(" "); // NOI18N
        }
        parameterList.append(s);
    }

    /**
     * True during dependency processing. Dependencies should be inserted at the start, so their
     * order is reversed (most deepest dependencies first). That means that if a component already
     * exists, it must be reinserted at the start.
     */
    private boolean installDependencies;

    protected boolean registerComponent(Installer inst, ComponentParam p) throws IOException {
        ComponentInfo info = inst.getComponentInfo();
        Installer existing = installerMap.get(info.getId());

        Installer removedInstaller = null;

        if (existing == null) {
            installerMap.put(info.getId(), inst);
            if (installDependencies) {
                installers.add(0, inst);
            } else {
                installers.add(inst);
            }
            return true;
        } else {
            int i = installers.indexOf(existing);
            ComponentInfo exInfo = existing.getComponentInfo();
            int newer = exInfo.getVersion().compareTo(info.getVersion());
            if (newer < 0) {
                feedback.verboseOutput("INSTALL_UsingNewerComponent", info.getId(), info.getName(),
                                info.getVersion().displayString(), exInfo.getVersion().displayString());

                removedInstaller = installerMap.put(info.getId(), inst);
                if (installDependencies) {
                    // must reinsert at the start: later items may depend on this one
                    installers.remove(i);
                    installers.add(0, inst);
                } else {
                    // replace at the same position, to mainain commandline order
                    installers.set(i, inst);
                }
                existing.close();
                if (removedInstaller != null) {
                    realInstallers.remove(p);
                }
                return true;
            } else {
                Installer toReplace = inst.isComplete() ? inst : existing;
                // if dependencies are processed, move the installer to the front
                // of the work queue, to maintain the depenency-first order.
                if (installDependencies) {
                    installers.remove(existing);
                    installers.add(0, toReplace);
                    installerMap.put(info.getId(), toReplace);
                } else if (!existing.isComplete() && inst.isComplete()) {
                    // replace proxy for real installer:
                    installers.set(i, toReplace);
                    installerMap.put(info.getId(), toReplace);
                }

                return false;
            }
        }
    }

    protected void processComponents(Iterable<ComponentParam> toProcess) throws IOException {
        for (Iterator<ComponentParam> it = toProcess.iterator(); it.hasNext();) {
            appendParameterText();
            ComponentParam p = it.next();
            feedback.output(p.isComplete() ? "INSTALL_VerboseProcessingArchive" : "INSTALL_VerboseProcessingComponent", p.getDisplayName());
            current = p.getSpecification();
            MetadataLoader ldr = validateDownload ? p.createFileLoader() : p.createMetaLoader();
            Installer inst = createInstaller(p, ldr);
            if (!verifyInstaller(inst)) {
                continue;
            }
            if (registerComponent(inst, p)) {
                if (p.isComplete()) {
                    // null realInstaller will be handled in completeInstallers() later.
                    addLicenseToAccept(inst, ldr);
                    realInstallers.put(p, inst);
                } else {
                    realInstallers.put(p, null);
                }
            }
            current = null;

            URL remote = ldr.getComponentInfo().getRemoteURL();
            if (remote == null || remote.getProtocol().equalsIgnoreCase("file")) {
                wasFile = true;
            }
        }

    }

    protected void prevalidateInstallers() throws IOException {
        for (Installer i : new ArrayList<>(installers)) {
            if (validateBeforeInstall) {
                current = i.getComponentInfo().getName();
                i.validateAll();
            }
        }
    }

    protected void prepareInstallation() throws IOException {
        processComponents(input.existingFiles());
        // first check after explicit components have been processed.
        checkDependencyErrors();
        if (dependencies.isEmpty()) {
            return;
        }
        // dependencies were scanned recursively; so just one additional pass should
        // be sufficient
        try {
            installDependencies = true;
            processComponents(new ArrayList<>(dependencies));
        } finally {
            installDependencies = false;
        }
        printRequiredComponents();
        checkDependencyErrors();
        prevalidateInstallers();
    }

    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    public void setForce(boolean force) {
        this.force = force;
    }

    public void setValidateBeforeInstall(boolean validateBeforeInstall) {
        this.validateBeforeInstall = validateBeforeInstall;
    }

    public void setValidateDownload(boolean validateDownload) {
        this.validateDownload = validateDownload;
    }

    void executeStep(Step s, boolean close) throws IOException {
        boolean ok = false;
        try {
            s.execute();
            ok = true;
        } catch (ZipException ex) {
            feedback.error("INSTALL_InvalidComponentArchive", ex, current);
            throw ex;
        } catch (UserAbortException ex) {
            throw ex;
        } catch (InstallerStopException | IOException ex) {
            if (ignoreFailures) {
                if (current == null) {
                    feedback.error("INSTALL_IgnoreFailedInstallation", ex,
                                    ex.getLocalizedMessage());
                } else {
                    feedback.error("INSTALL_IgnoreFailedInstallation2", ex,
                                    current, ex.getLocalizedMessage());
                }
            } else {
                if (current != null) {
                    feedback.error("INSTALL_ErrorDuringProcessing", ex, current, ex.getLocalizedMessage());
                }
                throw ex;
            }
        } finally {
            if (!ok) {
                for (Installer inst : executedInstallers) {
                    inst.revertInstall();
                }
            }
            if (close || !ok) {
                for (Installer inst : installers) {
                    try {
                        inst.close();
                    } catch (IOException ex) {
                        // expected
                    }
                }
            }
        }
    }

    interface Step {
        void execute() throws IOException;
    }

    void printMessages() {
        postinstHelper.run();
    }

    /**
     * Creates installers with complete info. Revalidates the installers as they are now complete.
     */
    void completeInstallers() throws IOException {
        List<ComponentParam> allDependencies = new ArrayList<>();
        List<ComponentParam> in = new ArrayList<>(realInstallers.keySet());
        do {
            cleanDependencies();
            completeInstallers0(in);
            allDependencies.addAll(dependencies);
            in = dependencies;
            // print required components prior to download
            printRequiredComponents();
            installDependencies = true;
        } while (!in.isEmpty());
        dependencies = allDependencies;
        checkDependencyErrors();
    }

    void completeInstallers0(List<ComponentParam> in) throws IOException {
        // now fileName real installers for parameters which were omitted
        for (ComponentParam p : in) {
            Installer i = realInstallers.get(p);
            if (i == null) {
                MetadataLoader floader = p.createFileLoader();
                i = createInstaller(p, floader);
                if (!verifyInstaller(i)) {
                    continue;
                }
                addLicenseToAccept(i, floader);
                registerComponent(i, p);

                if (validateBeforeInstall) {
                    current = i.getComponentInfo().getName();
                    i.validateAll();
                } else {
                    if (!force) {
                        i.validateRequirements();
                    }
                }
                realInstallers.put(p, i);
            }
        }
    }

    void doInstallation() throws IOException {
        for (Installer i : installers) {
            current = i.getComponentInfo().getName();
            ensureExistingComponentRemoved(i.getComponentInfo());
            executedInstallers.add(i);

            i.setComponentDirectories(input.getLocalRegistry().getComponentDirectories());
            i.install();
            postinstHelper.addComponentInfo(i.getComponentInfo());
        }
    }

    void ensureExistingComponentRemoved(ComponentInfo info) throws IOException {
        String componentId = info.getId();
        ComponentInfo oldInfo = input.getLocalRegistry().loadSingleComponent(componentId, true);
        if (oldInfo == null) {
            feedback.output("INSTALL_InstallNewComponent",
                            info.getId(), info.getName(), info.getVersionString());
        } else {
            Uninstaller uninstaller = new Uninstaller(feedback,
                            input.getFileOperations(), oldInfo, input.getLocalRegistry());
            uninstaller.setInstallPath(input.getGraalHomePath());
            uninstaller.setDryRun(input.optValue(Commands.OPTION_DRY_RUN) != null);
            uninstaller.setPreservePaths(
                            new HashSet<>(input.getLocalRegistry().getPreservedFiles(oldInfo)));

            feedback.output("INSTALL_RemoveExistingComponent",
                            oldInfo.getId(), oldInfo.getName(), oldInfo.getVersionString(),
                            info.getId(), info.getName(), info.getVersionString());
            uninstaller.uninstall();
        }
    }

    /**
     * Installers for individual component IDs.
     */
    private final Map<String, Installer> installerMap = new HashMap<>();

    /**
     * The installation sequence; dependencies first.
     */
    private final List<Installer> installers = new ArrayList<>();
    private final List<Installer> executedInstallers = new ArrayList<>();

    List<Installer> getInstallers() {
        return installers;
    }

    protected void configureInstaller(Installer inst) {
        inst.setInstallPath(input.getGraalHomePath());
        inst.setDryRun(input.optValue(Commands.OPTION_DRY_RUN) != null);
        force = input.optValue(Commands.OPTION_FORCE) != null;

        inst.setFailOnExisting(input.optValue(Commands.OPTION_FAIL_EXISTING) != null);
        inst.setReplaceComponents(force || input.optValue(Commands.OPTION_REPLACE_COMPONENTS) != null);
        inst.setIgnoreRequirements(force);
        inst.setReplaceDiferentFiles(force || input.optValue(Commands.OPTION_REPLACE_DIFFERENT_FILES) != null);
        if (validateBeforeInstall) {
            inst.setDryRun(true);
        }
    }

    Map<String, String> permissions;
    Map<String, String> symlinks;
    ComponentInfo fullInfo;

    Installer createInstaller(ComponentParam p, MetadataLoader ldr) throws IOException {
        ComponentInfo partialInfo;
        partialInfo = ldr.getComponentInfo();
        feedback.verboseOutput("INSTALL_PrepareToInstall",
                        p.getDisplayName(),
                        partialInfo.getId(),
                        partialInfo.getVersionString(),
                        partialInfo.getName());
        ldr.loadPaths();
        Archive a = null;
        if (p.isComplete()) {
            a = ldr.getArchive();
            a.verifyIntegrity(input);
        }
        Installer inst = new Installer(feedback,
                        input.getFileOperations(),
                        partialInfo, input.getLocalRegistry(),
                        input.getRegistry(), a);
        inst.setPermissions(ldr.loadPermissions());
        inst.setSymlinks(ldr.loadSymlinks());
        configureInstaller(inst);
        return inst;

    }

    /**
     * Forces the user to accept the licenses.
     * 
     * @throws IOException
     */
    void acceptLicenses() throws IOException {
        // disabled for 20.1 release
        // new LicensePresenter(feedback, input.getLocalRegistry(), licensesToAccept).run();
    }

    public Set<String> getUnresolvedDependencies() {
        return Collections.unmodifiableSet(unresolvedDependencies);
    }
}
