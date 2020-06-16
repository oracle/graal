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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipException;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.CommonConstants;
import org.graalvm.component.installer.ComponentInstaller;
import org.graalvm.component.installer.ComponentParam;
import org.graalvm.component.installer.DependencyException;
import org.graalvm.component.installer.FailedOperationException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.InstallerStopException;
import org.graalvm.component.installer.MetadataException;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.Verifier;
import org.graalvm.component.installer.persist.MetadataLoader;

/**
 * Command to query component bundles.
 */
public class InfoCommand extends QueryCommandBase {
    private static final Map<String, String> OPTIONS = new HashMap<>();

    static {
        OPTIONS.putAll(BASE_OPTIONS);
        OPTIONS.putAll(ComponentInstaller.componentOptions);

        OPTIONS.put(Commands.OPTION_NO_VERIFY_JARS, "");
        OPTIONS.put(Commands.OPTION_FULL_PATHS, "");
        OPTIONS.put(Commands.OPTION_IGNORE_OPEN_ERRORS, "");
        OPTIONS.put(Commands.OPTION_SUPPRESS_TABLE, "");
        OPTIONS.put(Commands.OPTION_NO_DOWNLOAD_PROGRESS, "");

        OPTIONS.put(Commands.LONG_OPTION_NO_VERIFY_JARS, Commands.OPTION_NO_VERIFY_JARS);
        OPTIONS.put(Commands.LONG_OPTION_FULL_PATHS, Commands.OPTION_FULL_PATHS);
        OPTIONS.put(Commands.LONG_OPTION_IGNORE_OPEN_ERRORS, Commands.OPTION_IGNORE_OPEN_ERRORS);
        OPTIONS.put(Commands.LONG_OPTION_SUPPRESS_TABLE, Commands.OPTION_SUPPRESS_TABLE);
        OPTIONS.put(Commands.LONG_OPTION_NO_DOWNLOAD_PROGRESS, Commands.OPTION_NO_DOWNLOAD_PROGRESS);

        OPTIONS.put(Commands.OPTION_VERSION, "s");
        OPTIONS.put(Commands.LONG_OPTION_VERSION, Commands.OPTION_VERSION);
    }

    private boolean ignoreOpenErrors;
    private boolean verifyJar;
    private boolean fullPath;
    private boolean suppressTable;
    private Version.Match versionFilter;

    private final List<ComponentParam> components = new ArrayList<>();
    private final Map<ComponentInfo, MetadataLoader> map = new HashMap<>();
    private final Map<ComponentInfo, ComponentParam> files = new HashMap<>();

    @Override
    public Map<String, String> supportedOptions() {
        return OPTIONS;
    }

    public boolean isSuppressTable() {
        return suppressTable;
    }

    public void setSuppressTable(boolean suppressTable) {
        this.suppressTable = suppressTable;
    }

    public boolean isIgnoreOpenErrors() {
        return ignoreOpenErrors;
    }

    public void setIgnoreOpenErrors(boolean ignoreOpenErrors) {
        this.ignoreOpenErrors = ignoreOpenErrors;
    }

    public boolean isVerifyJar() {
        return verifyJar;
    }

    public void setVerifyJar(boolean verifyJar) {
        this.verifyJar = verifyJar;
    }

    public boolean isFullPath() {
        return fullPath;
    }

    public void setFullPath(boolean fullPath) {
        this.fullPath = fullPath;
    }

    @Override
    public void init(CommandInput commandInput, Feedback feedBack) {
        super.init(commandInput, feedBack);
        ignoreOpenErrors = input.optValue(Commands.OPTION_IGNORE_OPEN_ERRORS) != null;
        verifyJar = input.optValue(Commands.OPTION_NO_VERIFY_JARS) == null;
        suppressTable = input.optValue(Commands.OPTION_SUPPRESS_TABLE) != null;
        fullPath = input.optValue(Commands.OPTION_FULL_PATHS) != null;

        String vf = input.optValue(Commands.OPTION_VERSION);
        if (vf != null) {
            versionFilter = Version.versionFilter(vf);
        }
    }

    void collectComponents() throws IOException {
        for (Iterator<ComponentParam> it = input.existingFiles().matchVersion(versionFilter).allowIncompatible().iterator(); it.hasNext();) {
            ComponentParam cp;
            try {
                cp = it.next();
            } catch (FailedOperationException ex) {
                feedback.error("INFO_InvalidComponent", ex, ex.getLocalizedMessage());
                continue;
            }
            try {
                // verifyjar set to false, as Verifier is not supported in SVM
                // components.add(ldr = new ComponentPackageLoader(new JarFile(f, false),
                // feedback));
                processComponentParam(cp);
            } catch (ZipException ex) {
                if (ignoreOpenErrors) {
                    feedback.error("INFO_ErrorOpeningBundle", ex,
                                    cp.getDisplayName(),
                                    ex.getLocalizedMessage());
                } else {
                    throw ex;
                }
            } catch (MetadataException ex) {
                if (ignoreOpenErrors) {
                    feedback.error("INFO_CorruptedBundleMetadata", ex,
                                    cp.getDisplayName(),
                                    ex.getOffendingHeader(),
                                    ex.getLocalizedMessage());
                } else {
                    throw ex;
                }
            } catch (IOException ex) {
                if (ignoreOpenErrors) {
                    feedback.error("INFO_ErrorReadingBundle", ex,
                                    cp.getDisplayName(),
                                    ex.getLocalizedMessage());
                } else {
                    throw ex;
                }
            }
        }
    }

    @Override
    public int execute() throws IOException {
        if (input.optValue(Commands.OPTION_HELP) != null) {
            feedback.output("INFO_Help");
            return 0;
        }
        if (!input.hasParameter()) {
            feedback.error("INFO_MissingFilename", null);
            return 1;
        }
        try {
            collectComponents();
            printTable = getComponents().size() > 1 && !isVerbose() && !suppressTable;
            super.printComponents();
        } finally {
            for (ComponentParam c : components) {
                try {
                    c.close();
                } catch (IOException ex) {
                    ComponentInfo ci = c.createMetaLoader().getComponentInfo();
                    feedback.error("INFO_ClosingComponent", ex,
                                    ci == null ? c.getSpecification() : shortenComponentId(ci),
                                    ex.getLocalizedMessage());
                }
            }
        }
        return 0;
    }

    void processComponentParam(ComponentParam cp) throws IOException {
        MetadataLoader ldr = cp.createMetaLoader();

        components.add(cp);
        loadComponentDetails(cp, ldr);
        // registerFile(f, ldr.getComponentInfo(), ldr);
        addComponent(cp, ldr.getComponentInfo());

    }

    void registerFile(ComponentParam param, ComponentInfo info, MetadataLoader ldr) {
        files.put(info, param);
        map.put(info, ldr);
    }

    void loadComponentDetails(ComponentParam param, MetadataLoader ldr) throws IOException {
        ldr.infoOnly(true);
        ComponentInfo info = ldr.getComponentInfo();
        registerFile(param, info, ldr);
        if (isListFiles()) {
            ldr.loadPaths();
        }
    }

    @Override
    void printHeader() {
        if (printTable) {
            if (fullPath) {
                feedback.output("INFO_ComponentLongListHeader");
            } else {
                feedback.output("INFO_ComponentShortListHeader");
            }
        }
    }

    protected String filePath(ComponentInfo info) {
        ComponentParam p = files.get(info);
        if (fullPath) {
            return p.getFullPath();
        } else {
            String s = p.getShortName();
            int idx = s.lastIndexOf('.');
            return idx == -1 ? s : s.substring(0, idx);
        }
    }

    @Override
    void printDetails(ComponentParam param, ComponentInfo info) {
        if (printTable) {
            String line = String.format(feedback.l10n("INFO_ComponentShortList"),
                            shortenComponentId(info), val(info.getVersion().displayString()), val(info.getName()),
                            filePath(info));
            feedback.verbatimOut(line, false);
            return;
        } else {
            feedback.output("INFO_ComponentBasicInfo",
                            shortenComponentId(info), val(info.getVersion().displayString()), val(info.getName()),
                            param.getFullPath(), findRequiredGraalVMVersion(info));
            List<String> keys = new ArrayList<>(info.getRequiredGraalValues().keySet());
            keys.remove(CommonConstants.CAP_GRAALVM_VERSION);
            if (!keys.isEmpty() && feedback.verboseOutput("INFO_ComponentRequirementsHeader")) {
                Collections.sort(keys);
                for (String cap : keys) {
                    feedback.verboseOutput("INFO_ComponentRequirement",
                                    getRegistry().localizeCapabilityName(cap),
                                    info.getRequiredGraalValues().get(cap));
                }
            }
            MetadataLoader ldr = map.get(info);
            List<InstallerStopException> errs = ldr.getErrors();
            if (!errs.isEmpty()) {
                feedback.message("INFO_ComponentBroken", files.get(info));
                for (InstallerStopException ex : errs) {
                    feedback.message("INFO_ComponentErrorIndent", ex.getLocalizedMessage());
                }
            }

            Verifier vfy = new Verifier(feedback, input.getLocalRegistry(), catalog).collect(true).validateRequirements(info);
            if (vfy.hasErrors()) {
                feedback.message("INFO_ComponentWillNotInstall", shortenComponentId(info));
                for (DependencyException ex : vfy.getErrors()) {
                    feedback.message("INFO_ComponentDependencyIndent", ex.getLocalizedMessage());
                }
            }

        }
    }

}
