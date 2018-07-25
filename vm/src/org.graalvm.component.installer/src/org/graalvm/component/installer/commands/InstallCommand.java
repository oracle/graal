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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipException;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.CommonConstants;
import static org.graalvm.component.installer.CommonConstants.WARN_REBUILD_IMAGES;
import org.graalvm.component.installer.ComponentParam;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.InstallerCommand;
import org.graalvm.component.installer.InstallerStopException;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.persist.MetadataLoader;

/**
 * Implementation of 'install' command.
 */
public class InstallCommand implements InstallerCommand {
    private static final Map<String, String> OPTIONS = new HashMap<>();

    private CommandInput input;
    private Feedback feedback;
    private boolean ignoreFailures;
    private boolean force;
    private boolean rebuildPolyglot;
    private boolean validateBeforeInstall;
    private boolean validateDownload;

    static {
        OPTIONS.put(Commands.OPTION_DRY_RUN, "");
        OPTIONS.put(Commands.OPTION_FORCE, "");
        OPTIONS.put(Commands.OPTION_REPLACE_COMPONENTS, "");
        OPTIONS.put(Commands.OPTION_REPLACE_DIFFERENT_FILES, "");
        OPTIONS.put(Commands.OPTION_VALIDATE, "");
        OPTIONS.put(Commands.OPTION_VALIDATE_DOWNLOAD, "");
        OPTIONS.put(Commands.OPTION_IGNORE_FAILURES, "");

        OPTIONS.put(Commands.LONG_OPTION_DRY_RUN, Commands.OPTION_DRY_RUN);
        OPTIONS.put(Commands.LONG_OPTION_FORCE, Commands.OPTION_FORCE);
        OPTIONS.put(Commands.LONG_OPTION_REPLACE_COMPONENTS, Commands.OPTION_REPLACE_COMPONENTS);
        OPTIONS.put(Commands.LONG_OPTION_REPLACE_DIFFERENT_FILES, Commands.OPTION_REPLACE_DIFFERENT_FILES);
        OPTIONS.put(Commands.LONG_OPTION_VALIDATE, Commands.OPTION_VALIDATE);
        OPTIONS.put(Commands.LONG_OPTION_VALIDATE_DOWNLOAD, Commands.OPTION_VALIDATE_DOWNLOAD);
        OPTIONS.put(Commands.LONG_OPTION_IGNORE_FAILURES, Commands.OPTION_IGNORE_FAILURES);
    }

    @Override
    public void init(CommandInput commandInput, Feedback feedBack) {
        this.input = commandInput;
        this.feedback = feedBack;

        ignoreFailures = this.input.optValue(Commands.OPTION_IGNORE_FAILURES) != null;
        validateBeforeInstall = this.input.optValue(Commands.OPTION_VALIDATE) != null;
        validateDownload = this.input.optValue(Commands.OPTION_VALIDATE_DOWNLOAD) != null;
    }

    @Override
    public Map<String, String> supportedOptions() {
        return OPTIONS;
    }

    public InstallCommand() {
    }

    List<ComponentParam> components = new ArrayList<>();
    Map<ComponentParam, Installer> realInstallers = new LinkedHashMap<>();

    private String current;

    @Override
    public int execute() throws IOException {
        if (input.optValue(Commands.OPTION_HELP) != null) {
            feedback.output("INSTALL_Help");
            return 0;
        }
        if (!input.hasParameter()) {
            feedback.output("INSTALL_ParametersMissing");
            return 1;
        }
        executeStep(this::prepareInstallation, false);
        if (validateBeforeInstall) {
            return 0;
        }
        executeStep(this::completeInstallers, false);
        executeStep(this::doInstallation, false);
        // execute the post-install steps for all processed installers
        executeStep(this::printMessages, true);
        if (rebuildPolyglot && WARN_REBUILD_IMAGES) {
            Path p = SystemUtils.fromCommonString(CommonConstants.PATH_JRE_BIN);
            feedback.output("INSTALL_RebuildPolyglotNeeded", File.separator, input.getGraalHomePath().resolve(p).normalize());
        }
        return 0;
    }

    void prepareInstallation() throws IOException {
        for (ComponentParam p : input.existingFiles()) {

            feedback.output("INSTALL_VerboseProcessingArchive", /* feedback.translateFilename( */p.getDisplayName());
            current = p.getSpecification();
            Installer inst = createInstaller(p,
                            validateDownload ? p.createFileLoader() : p.createMetaLoader());
            installers.add(inst);
            if (p.isComplete()) {
                realInstallers.put(p, inst);
            } else {
                realInstallers.put(p, null);
            }
            current = null;
        }

        for (Installer i : installers) {
            if (validateBeforeInstall) {
                current = i.getComponentInfo().getName();
                i.validateAll();
            } else {
                if (!force) {
                    i.validateRequirements();
                }
            }
        }
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

    private interface Step {
        void execute() throws IOException;
    }

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\$\\{([\\p{Alnum}_-]+)\\}");

    String replaceTokens(ComponentInfo info, String message) {
        Map<String, String> tokens = new HashMap<>();
        tokens.putAll(info.getRequiredGraalValues());
        tokens.putAll(input.getLocalRegistry().getGraalCapabilities());
        tokens.put(CommonConstants.TOKEN_GRAALVM_PATH, input.getGraalHomePath().normalize().toString());

        Matcher m = TOKEN_PATTERN.matcher(message);
        StringBuilder result = null;
        int start = 0;
        int last = 0;
        while (m.find(start)) {
            String token = m.group(1);
            String val = tokens.get(token);
            if (val != null) {
                if (result == null) {
                    result = new StringBuilder();
                }
                result.append(message.substring(last, m.start()));
                result.append(val);
                last = m.end();
            }
            start = m.end();
        }

        if (result == null) {
            return message;
        } else {
            result.append(message.substring(last));
            return result.toString();
        }
    }

    void printMessages() {
        for (Installer i : executedInstallers) {
            String msg = i.getComponentInfo().getPostinstMessage();
            if (msg != null) {
                String replaced = replaceTokens(i.getComponentInfo(), msg);
                // replace potential fileName etc
                feedback.verbatimOut(replaced, false);
                // add some newlines
                feedback.verbatimOut("", false);
            }
        }
    }

    /**
     * Creates installers with complete info. Revalidates the installers as they are now complete.
     */
    void completeInstallers() throws IOException {
        // now fileName real installers for parameters which were omitted
        for (ComponentParam p : new ArrayList<>(realInstallers.keySet())) {
            Installer i = realInstallers.get(p);
            if (i == null) {
                i = createInstaller(p, p.createFileLoader());
                installers.add(i);

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
        for (Installer i : realInstallers.values()) {
            current = i.getComponentInfo().getName();
            ensureExistingComponentRemoved(i.getComponentInfo());
            executedInstallers.add(i);

            i.setComponentDirectories(input.getLocalRegistry().getComponentDirectories());
            i.install();

            rebuildPolyglot |= i.isRebuildPolyglot();
        }
    }

    public boolean isRebuildPolyglot() {
        return rebuildPolyglot;
    }

    void ensureExistingComponentRemoved(ComponentInfo info) throws IOException {
        String componentId = info.getId();
        ComponentInfo oldInfo = input.getLocalRegistry().loadSingleComponent(componentId, true);
        if (oldInfo == null) {
            feedback.output("INSTALL_InstallNewComponent",
                            info.getId(), info.getName(), info.getVersionString());
        } else {
            Uninstaller uninstaller = new Uninstaller(feedback, oldInfo, input.getLocalRegistry());
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

    private final List<Installer> installers = new ArrayList<>();
    private final List<Installer> executedInstallers = new ArrayList<>();

    private void configureInstaller(Installer inst) {
        inst.setInstallPath(input.getGraalHomePath());
        inst.setDryRun(input.optValue(Commands.OPTION_DRY_RUN) != null);
        force = input.optValue(Commands.OPTION_FORCE) != null;

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
                        /* feedback.translateFilename( */ p.getDisplayName(),
                        partialInfo.getId(),
                        partialInfo.getVersionString(),
                        partialInfo.getName());
        ldr.loadPaths();
        Installer inst = new Installer(feedback, partialInfo, input.getLocalRegistry());
        String path = ldr.getLicensePath();
        if (path != null) {
            inst.setLicenseRelativePath(SystemUtils.fromCommonString(ldr.getLicensePath()));
        }
        inst.setPermissions(ldr.loadPermissions());
        inst.setSymlinks(ldr.loadSymlinks());
        if (p.isComplete()) {
            inst.setJarFile(p.getFile());
        }
        configureInstaller(inst);
        return inst;

    }
}
