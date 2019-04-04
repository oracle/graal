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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.graalvm.component.installer.CommandInput;
import org.graalvm.component.installer.Commands;
import static org.graalvm.component.installer.Commands.LONG_OPTION_LIST_FILES;
import static org.graalvm.component.installer.Commands.OPTION_LIST_FILES;
import static org.graalvm.component.installer.CommonConstants.CAP_GRAALVM_VERSION;
import org.graalvm.component.installer.ComponentParam;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.InstallerCommand;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;

/**
 * List command.
 */
public abstract class QueryCommandBase implements InstallerCommand {
    protected static final Map<String, String> BASE_OPTIONS = new HashMap<>();

    static {
        BASE_OPTIONS.put(OPTION_LIST_FILES, ""); // NOI18N
        BASE_OPTIONS.put(LONG_OPTION_LIST_FILES, OPTION_LIST_FILES); // NOI18N
    }

    @Override
    public Map<String, String> supportedOptions() {
        return BASE_OPTIONS;
    }

    protected CommandInput input;
    protected ComponentRegistry registry;
    protected Feedback feedback;
    protected boolean verbose;
    protected boolean printTable;
    protected boolean listFiles;
    protected List<ComponentParam> componentParams = new ArrayList<>();
    protected List<ComponentInfo> components = new ArrayList<>();

    public ComponentRegistry getRegistry() {
        return registry;
    }

    public void setRegistry(ComponentRegistry registry) {
        this.registry = registry;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public void setFeedback(Feedback feedback) {
        this.feedback = feedback;
    }

    public boolean isListFiles() {
        return listFiles;
    }

    public void setListFiles(boolean listFiles) {
        this.listFiles = listFiles;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    protected ComponentRegistry initRegistry() {
        if (input.optValue(Commands.OPTION_CATALOG) != null ||
                        input.optValue(Commands.OPTION_FOREIGN_CATALOG) != null) {
            return input.getRegistry();
        } else {
            return input.getLocalRegistry();
        }
    }

    @Override
    public void init(CommandInput commandInput, Feedback feedBack) {
        this.input = commandInput;
        this.registry = initRegistry();
        this.feedback = feedBack;
        listFiles = commandInput.optValue(OPTION_LIST_FILES) != null;
        verbose = commandInput.optValue(Commands.OPTION_VERBOSE) != null;
        printTable = !listFiles && !verbose;
    }

    protected void addComponent(ComponentParam param, ComponentInfo info) {
        componentParams.add(param);
        components.add(info);
    }

    public List<ComponentInfo> getComponents() {
        return components;
    }

    protected void printComponents() {
        printHeader();
        Iterator<ComponentParam> itpar = componentParams.iterator();
        for (ComponentInfo info : components) {
            printDetails(itpar.next(), info);
            printFileList(info);
            printSeparator(info);
        }
    }

    void printHeader() {
        if (printTable) {
            feedback.output("LIST_ComponentShortListHeader");
        }
    }

    String val(String s) {
        return s == null ? feedback.l10n("LIST_MetadataUnknown") : s;
    }

    protected String shortenComponentId(ComponentInfo info) {
        return registry.shortenComponentId(info);
    }

    @SuppressWarnings("unused")
    void printDetails(ComponentParam param, ComponentInfo info) {
        if (printTable) {
            String line = String.format(feedback.l10n("LIST_ComponentShortList"),
                            shortenComponentId(info), info.getVersionString(), info.getName());
            feedback.verbatimOut(line, false);
            return;
        } else {
            feedback.output("LIST_ComponentBasicInfo",
                            shortenComponentId(info), info.getVersionString(), info.getName(),
                            findRequiredGraalVMVersion(info));
        }
    }

    protected String findRequiredGraalVMVersion(ComponentInfo info) {
        return val(info.getRequiredGraalValues().get(CAP_GRAALVM_VERSION));
    }

    void printFileList(ComponentInfo info) {
        if (!listFiles) {
            return;
        }
        List<String> files = new ArrayList<>(info.getPaths());
        feedback.output("LIST_ComponentFilesHeader", files.size());
        Collections.sort(files);
        for (String s : files) {
            feedback.verbatimOut(s, false);
        }
    }

    void printSeparator(@SuppressWarnings("unused") ComponentInfo info) {
        if (printTable) {
            return;
        }
        feedback.verbatimOut("", true); // NOI18N
    }
}
