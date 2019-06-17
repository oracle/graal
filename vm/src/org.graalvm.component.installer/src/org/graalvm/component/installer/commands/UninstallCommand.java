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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import org.graalvm.component.installer.BundleConstants;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Commands;
import org.graalvm.component.installer.CommonConstants;
import static org.graalvm.component.installer.CommonConstants.WARN_REBUILD_IMAGES;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.InstallerCommand;
import org.graalvm.component.installer.InstallerStopException;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.model.ComponentInfo;

public class UninstallCommand implements InstallerCommand {

    static final Map<String, String> OPTIONS = new HashMap<>();

    static {
        OPTIONS.put(Commands.OPTION_DRY_RUN, "");
        OPTIONS.put(Commands.OPTION_FORCE, "");
        OPTIONS.put(Commands.OPTION_IGNORE_FAILURES, "");

        OPTIONS.put(Commands.LONG_OPTION_DRY_RUN, Commands.OPTION_DRY_RUN);
        OPTIONS.put(Commands.LONG_OPTION_FORCE, Commands.OPTION_FORCE);
        OPTIONS.put(Commands.LONG_OPTION_IGNORE_FAILURES, Commands.OPTION_IGNORE_FAILURES);
    }

    private final Map<String, ComponentInfo> toUninstall = new LinkedHashMap<>();
    private Feedback feedback;
    private CommandInput input;
    private boolean ignoreFailures;
    private ComponentRegistry registry;
    private boolean rebuildPolyglot;

    @Override
    public Map<String, String> supportedOptions() {
        return OPTIONS;
    }

    @Override
    public void init(CommandInput commandInput, Feedback feedBack) {
        this.input = commandInput;
        this.feedback = feedBack;
    }

    @Override
    public int execute() throws IOException {
        input.getLocalRegistry().verifyAdministratorAccess();

        this.registry = input.getLocalRegistry();

        ignoreFailures = input.optValue(Commands.OPTION_FORCE) != null;

        if (input.optValue(Commands.OPTION_HELP) != null) {
            feedback.output("UNINSTALL_Help");
            return 0;
        }
        if (!input.hasParameter()) {
            feedback.output("UNINSTALL_ParametersMissing");
            return 1;
        }

        String compId;
        while ((compId = input.nextParameter()) != null) {
            if (toUninstall.containsKey(compId)) {
                continue;
            }
            ComponentInfo info = input.getLocalRegistry().loadSingleComponent(compId.toLowerCase(), true);
            if (info == null) {
                throw feedback.failure("UNINSTALL_UnknownComponentId", null, compId);
            }
            if (info.getId().equals(BundleConstants.GRAAL_COMPONENT_ID)) {
                throw feedback.failure("UNINSTALL_CoreComponent", null, compId);
            }
            if (info.isNativeComponent()) {
                throw feedback.failure("UNINSTALL_NativeComponent", null, compId);
            }
            toUninstall.put(compId, info);
        }

        try {
            for (ComponentInfo info : toUninstall.values()) {
                try {
                    uninstallSingleComponent(info);
                } catch (InstallerStopException | IOException ex) {
                    if (ignoreFailures) {
                        feedback.error("UNINSTALL_IgnoreFailed", ex,
                                        info.getId(), ex.getLocalizedMessage());
                    } else {
                        feedback.error("UNINSTALL_ErrorDuringProcessing", ex, info.getId());
                        throw ex;
                    }
                }
            }
        } finally {
            if (rebuildPolyglot && WARN_REBUILD_IMAGES) {
                Path p = SystemUtils.fromCommonString(CommonConstants.PATH_JRE_BIN);
                feedback.output("INSTALL_RebuildPolyglotNeeded", File.separator, input.getGraalHomePath().resolve(p).normalize());
            }
        }
        return 0;
    }

    void uninstallSingleComponent(ComponentInfo info) throws IOException {
        Uninstaller inst = new Uninstaller(feedback, input.getFileOperations(),
                        info, input.getLocalRegistry());
        configureInstaller(inst);

        feedback.output("UNINSTALL_UninstallingComponent",
                        info.getId(), info.getName(), info.getVersionString());
        rebuildPolyglot |= info.isPolyglotRebuild();
        inst.uninstall();
    }

    private void configureInstaller(Uninstaller inst) {
        inst.setInstallPath(input.getGraalHomePath());
        inst.setDryRun(input.optValue(Commands.OPTION_DRY_RUN) != null);
        inst.setIgnoreFailedDeletions(input.optValue(Commands.OPTION_IGNORE_FAILURES) != null);
        inst.setPreservePaths(
                        new HashSet<>(registry.getPreservedFiles(inst.getComponentInfo())));
    }

}
