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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
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
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.DistributionType;

public class UninstallCommand implements InstallerCommand {

    static final Map<String, String> OPTIONS = new HashMap<>();

    static {
        OPTIONS.put(Commands.OPTION_DRY_RUN, "");
        OPTIONS.put(Commands.OPTION_FORCE, "");
        OPTIONS.put(Commands.OPTION_IGNORE_FAILURES, "");
        OPTIONS.put(Commands.OPTION_UNINSTALL_DEPENDENT, "");
        OPTIONS.put(Commands.OPTION_NO_DEPENDENCIES, "");

        OPTIONS.put(Commands.LONG_OPTION_DRY_RUN, Commands.OPTION_DRY_RUN);
        OPTIONS.put(Commands.LONG_OPTION_FORCE, Commands.OPTION_FORCE);
        OPTIONS.put(Commands.LONG_OPTION_IGNORE_FAILURES, Commands.OPTION_IGNORE_FAILURES);
        OPTIONS.put(Commands.LONG_OPTION_UNINSTALL_DEPENDENT, Commands.OPTION_UNINSTALL_DEPENDENT);
        OPTIONS.put(Commands.LONG_OPTION_NO_DEPENDENCIES, Commands.OPTION_NO_DEPENDENCIES);
    }

    private final Map<String, ComponentInfo> toUninstall = new LinkedHashMap<>();
    private List<ComponentInfo> uninstallSequence = new ArrayList<>();
    private Feedback feedback;
    private CommandInput input;
    private boolean ignoreFailures;
    private ComponentRegistry registry;
    private boolean rebuildPolyglot;
    private boolean removeDependent;
    private boolean breakDependent;
    private Map<ComponentInfo, Collection<ComponentInfo>> brokenDependencies = new HashMap<>();

    @Override
    public Map<String, String> supportedOptions() {
        return OPTIONS;
    }

    @Override
    public void init(CommandInput commandInput, Feedback feedBack) {
        this.input = commandInput;
        this.feedback = feedBack;
        this.registry = input.getLocalRegistry();
        setIgnoreFailures(input.optValue(Commands.OPTION_FORCE) != null);
        setBreakDependent(input.optValue(Commands.OPTION_FORCE) != null);
        setRemoveDependent(input.optValue(Commands.OPTION_UNINSTALL_DEPENDENT) != null);
    }

    public boolean isIgnoreFailures() {
        return ignoreFailures;
    }

    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    public boolean isRemoveDependent() {
        return removeDependent;
    }

    public void setRemoveDependent(boolean removeDependent) {
        this.removeDependent = removeDependent;
    }

    public boolean isBreakDependent() {
        return breakDependent;
    }

    public void setBreakDependent(boolean breakDependent) {
        this.breakDependent = breakDependent;
    }

    public Collection<ComponentInfo> getUninstallComponents() {
        return new ArrayList<>(toUninstall.values());
    }

    public List<ComponentInfo> getUninstallSequence() {
        return Collections.unmodifiableList(uninstallSequence);
    }

    public Map<ComponentInfo, Collection<ComponentInfo>> getBrokenDependencies() {
        return Collections.unmodifiableMap(brokenDependencies);
    }

    void prepareUninstall() {
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
                throw feedback.failure("UNINSTALL_NativeComponent", null, info.getId(), info.getName());
            }
            if (info.getDistributionType() != DistributionType.OPTIONAL) {
                throw feedback.failure("UNINSTALL_BundledComponent", null, info.getId(), info.getName());
            }
            toUninstall.put(compId, info);
        }

        if (input.hasOption(Commands.OPTION_NO_DEPENDENCIES)) {
            return;
        }

        for (ComponentInfo u : toUninstall.values()) {
            Set<ComponentInfo> br = registry.findDependentComponents(u, true);
            if (!br.isEmpty()) {
                brokenDependencies.put(u, br);
            }
        }
    }

    void checkBrokenDependencies() {
        if (brokenDependencies.isEmpty()) {
            return;
        }
        Set<ComponentInfo> uninstalled = new HashSet<>(toUninstall.values());
        // get all broken components, excluding the ones scheduled for uninstall
        Stream<ComponentInfo> stm = brokenDependencies.values().stream().flatMap((col) -> col.stream()).filter((c) -> !uninstalled.contains(c));
        // if all broken are uninstalled -> OK
        if (!stm.findFirst().isPresent()) {
            return;
        }

        List<ComponentInfo> sorted = new ArrayList<>(brokenDependencies.keySet());
        P printer;
        boolean warning = removeDependent || breakDependent;
        if (warning) {
            printer = feedback::output;
            feedback.output(removeDependent ? "UNINSTALL_BrokenDependenciesRemove" : "UNINSTALL_BrokenDependenciesWarn");
        } else {
            printer = (a, b) -> feedback.error(a, null, b);
            feedback.error("UNINSTALL_BrokenDependencies", null);
        }
        Comparator<ComponentInfo> c = (a, b) -> a.getId().compareToIgnoreCase(b.getId());
        Collections.sort(sorted, c);
        for (ComponentInfo i : sorted) {
            List<ComponentInfo> deps = new ArrayList<>(brokenDependencies.get(i));
            deps.removeAll(uninstalled);
            if (deps.isEmpty()) {
                continue;
            }
            Collections.sort(sorted, c);

            if (!warning) {
                printer.print("UNINSTALL_BreakDepSource", i.getName(), i.getId());
            }
            for (ComponentInfo d : deps) {
                printer.print("UNINSTALL_BreakDepTarget", d.getName(), d.getId());
            }
        }
        if (warning) {
            return;
        }
        throw feedback.failure("UNINSTALL_BreakDependenciesTerminate", null);
    }

    void includeAndOrderComponents() {
        Set<ComponentInfo> allBroken = new LinkedHashSet<>();
        if (!(input.hasOption(Commands.OPTION_NO_DEPENDENCIES) || breakDependent)) {
            for (Collection<ComponentInfo> ii : brokenDependencies.values()) {
                allBroken.addAll(ii);
            }
            for (ComponentInfo ci : allBroken) {
                Set<ComponentInfo> br = registry.findDependentComponents(ci, true);
                if (!br.isEmpty()) {
                    allBroken.addAll(br);
                    brokenDependencies.put(ci, br);
                }
            }

            if (removeDependent) {
                Set<ComponentInfo> newBroken = new HashSet<>();
                for (ComponentInfo i : allBroken) {
                    ComponentInfo full = registry.loadSingleComponent(i.getId(), true);
                    newBroken.add(full);
                    toUninstall.put(i.getId(), full);
                }
                allBroken = newBroken;
            }
        }

        List<ComponentInfo> leaves = new ArrayList<>(toUninstall.values());
        leaves.removeAll(allBroken);

        List<ComponentInfo> ordered = new ArrayList<>(toUninstall.size());
        ordered.addAll(leaves);

        int top = leaves.size();
        for (ComponentInfo ci : allBroken) {
            Set<ComponentInfo> check = new HashSet<>();
            CatalogContents.findDependencies(ci, true, Boolean.TRUE, check, (a, b, c, d) -> registry.findComponentMatch(a, b, true));
            int i;
            for (i = ordered.size(); i > top; i--) {
                ComponentInfo c = ordered.get(i - 1);
                if (check.contains(c)) {
                    break;
                }
            }
            ordered.add(i, ci);
        }
        Collections.reverse(ordered);
        this.uninstallSequence = ordered;
    }

    interface P {
        void print(String key, Object... params);
    }

    @Override
    public int execute() throws IOException {
        registry.verifyAdministratorAccess();
        if (input.optValue(Commands.OPTION_HELP) != null) {
            feedback.output("UNINSTALL_Help");
            return 0;
        }
        if (!input.hasParameter()) {
            feedback.output("UNINSTALL_ParametersMissing");
            return 1;
        }
        prepareUninstall();
        checkBrokenDependencies();
        includeAndOrderComponents();
        try {
            for (ComponentInfo info : uninstallSequence) {
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
        doUninstallSingle(inst);
    }

    // overriden in tests
    void doUninstallSingle(Uninstaller inst) throws IOException {
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
