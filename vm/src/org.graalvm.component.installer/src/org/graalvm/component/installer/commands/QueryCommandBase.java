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
import java.net.URISyntaxException;
import java.net.URL;
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
import org.graalvm.component.installer.CommonConstants;
import static org.graalvm.component.installer.CommonConstants.CAP_GRAALVM_VERSION;
import org.graalvm.component.installer.ComponentCollection;
import org.graalvm.component.installer.ComponentParam;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.InstallerCommand;
import org.graalvm.component.installer.Version;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import com.oracle.truffle.tools.utils.json.JSONObject;
import com.oracle.truffle.tools.utils.json.JSONArray;
import static org.graalvm.component.installer.Commands.OPTION_JSON_OUTPUT;
import static org.graalvm.component.installer.CommonConstants.JSON_KEY_COMPONENTS;
import static org.graalvm.component.installer.CommonConstants.JSON_KEY_COMPONENT_GRAALVM;
import static org.graalvm.component.installer.CommonConstants.JSON_KEY_COMPONENT_ID;
import static org.graalvm.component.installer.CommonConstants.JSON_KEY_COMPONENT_NAME;
import static org.graalvm.component.installer.CommonConstants.JSON_KEY_COMPONENT_ORIGIN;
import static org.graalvm.component.installer.CommonConstants.JSON_KEY_COMPONENT_STABILITY;
import static org.graalvm.component.installer.CommonConstants.JSON_KEY_COMPONENT_VERSION;
import static org.graalvm.component.installer.CommonConstants.JSON_KEY_COMPONENT_FILES;

/**
 * List command.
 */
public abstract class QueryCommandBase implements InstallerCommand {

    protected static final Map<String, String> BASE_OPTIONS = new HashMap<>();

    static {
        BASE_OPTIONS.put(OPTION_LIST_FILES, ""); // NOI18N
        BASE_OPTIONS.put(LONG_OPTION_LIST_FILES, OPTION_LIST_FILES); // NOI18N

        BASE_OPTIONS.put(Commands.OPTION_JSON_OUTPUT, "");
        BASE_OPTIONS.put(Commands.LONG_OPTION_JSON_OUTPUT, Commands.OPTION_JSON_OUTPUT);
    }

    @Override
    public Map<String, String> supportedOptions() {
        return BASE_OPTIONS;
    }

    protected CommandInput input;
    protected ComponentRegistry registry;
    protected ComponentCollection catalog;
    protected Feedback feedback;
    protected boolean verbose;
    protected boolean printTable;
    protected boolean listFiles;
    private boolean isJson;
    protected List<ComponentParam> componentParams = new ArrayList<>();
    protected List<ComponentInfo> components = new ArrayList<>();
    protected boolean simpleFormat;

    private JSONObject jsonComponent;

    protected JSONObject getJSONComponent() {
        return jsonComponent;
    }

    public ComponentRegistry getRegistry() {
        return registry;
    }

    protected boolean isJson() {
        return isJson;
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

    protected ComponentCollection initRegistry() {
        this.registry = input.getLocalRegistry();
        if (input.optValue(Commands.OPTION_CATALOG) != null || input.optValue(Commands.OPTION_FOREIGN_CATALOG) != null) {
            return input.getRegistry();
        } else {
            return registry;
        }
    }

    @Override
    public void init(CommandInput commandInput, Feedback feedBack) {
        this.input = commandInput;
        this.catalog = initRegistry();
        this.feedback = feedBack;
        this.isJson = commandInput.optValue(OPTION_JSON_OUTPUT) != null;
        this.feedback.setSilent(isJson);
        listFiles = commandInput.optValue(OPTION_LIST_FILES) != null;
        verbose = commandInput.optValue(Commands.OPTION_VERBOSE) != null;
        printTable = !listFiles && !verbose;
        processOutputFormat();
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
        JSONArray jsonComponents = null;
        if (isJson) {
            assert jsonComponent == null;
            jsonComponents = new JSONArray();
        }
        Iterator<ComponentParam> itpar = componentParams.iterator();
        for (ComponentInfo info : components) {
            if (jsonComponents != null) {
                jsonComponents.put(jsonComponent = new JSONObject());
            }
            printDetails(itpar.next(), info);
            printFileList(info);
            printSeparator(info);
        }
        if (jsonComponents != null) {
            final JSONObject json = new JSONObject().put(JSON_KEY_COMPONENTS, jsonComponents);
            feedback.suppressSilent(() -> feedback.verbatimOut(verbose ? json.toString(2) : json.toString(), false));
            jsonComponent = null;
        }
    }

    void printHeader() {
        if (simpleFormat) {
            feedback.output("LIST_ComponentShortListHeader_Simple@");
            return;
        }
        if (printTable) {
            feedback.output("LIST_ComponentShortListHeader");
        }
    }

    String val(String s) {
        if (simpleFormat) {
            return s == null ? "" : s;
        } else {
            return s == null ? feedback.l10n("LIST_MetadataUnknown") : s;
        }
    }

    protected String shortenComponentId(ComponentInfo info) {
        return registry.shortenComponentId(info);
    }

    @SuppressWarnings("unused")
    void printDetails(ComponentParam param, ComponentInfo info) {
        String org;
        URL u = info.getRemoteURL();
        if (simpleFormat) {
            org = u == null ? "" : u.toString();
        } else if (u == null) {
            org = ""; // NOI18N
        } else if (u.getProtocol().equals("file")) { // NOI18N
            try {
                org = new File(u.toURI()).getAbsolutePath();
            } catch (URISyntaxException ex) {
                // should not happen
                org = u.toString();
            }
        } else {
            org = u.getHost();
        }
        if (isJson) {
            jsonComponent.put(JSON_KEY_COMPONENT_ID, shortenComponentId(info));
            jsonComponent.put(JSON_KEY_COMPONENT_VERSION, info.getVersion().displayString());
            jsonComponent.put(JSON_KEY_COMPONENT_NAME, info.getName());
            jsonComponent.put(JSON_KEY_COMPONENT_GRAALVM, findRequiredGraalVMVersion(info));
            jsonComponent.put(JSON_KEY_COMPONENT_STABILITY, info.getStability().displayName(feedback));
            jsonComponent.put(JSON_KEY_COMPONENT_ORIGIN, u == null ? "" : u);
        } else if (printTable) {
            String fmt = simpleFormat ? "LIST_ComponentShortList_Simple@" : "LIST_ComponentShortList";
            String line = String.format(feedback.l10n(fmt),
                            shortenComponentId(info), info.getVersion().displayString(), info.getName(), org, info.getId(), info.getStability().displayName(feedback));
            feedback.verbatimOut(line, false);
        } else {
            String fmt = simpleFormat ? "LIST_ComponentBasicInfo_Simple@" : "LIST_ComponentBasicInfo";
            feedback.output(fmt,
                            shortenComponentId(info), info.getVersion().displayString(), info.getName(),
                            findRequiredGraalVMVersion(info), u == null ? "" : u, info.getId(), info.getStability().displayName(feedback));
        }
    }

    protected String findRequiredGraalVMVersion(ComponentInfo info) {
        String s = info.getRequiredGraalValues().get(CAP_GRAALVM_VERSION);
        if (s == null) {
            return val(s);
        }
        Version v = Version.fromString(s);
        return v.displayString();
    }

    void printFileList(ComponentInfo info) {
        if (!listFiles) {
            return;
        }
        List<String> files = new ArrayList<>(info.getPaths());
        Collections.sort(files);
        if (isJson) {
            jsonComponent.put(JSON_KEY_COMPONENT_FILES, files);
        } else {
            feedback.output(simpleFormat ? "LIST_ComponentFilesHeader_Simple@" : "LIST_ComponentFilesHeader", files.size());
            for (String s : files) {
                feedback.verbatimOut(s, false);
            }
            if (simpleFormat) {
                feedback.output("LIST_ComponentFilesEnd@");
            }
        }
    }

    void printSeparator(@SuppressWarnings("unused") ComponentInfo info) {
        if (simpleFormat || printTable || isJson) {
            return;
        }
        feedback.verbatimOut("", true); // NOI18N
    }

    void processOutputFormat() {
        if (Boolean.TRUE.toString().equals(System.getProperty(CommonConstants.SYSPROP_SIMPLE_OUTPUT))) {
            simpleFormat = true;
        }
    }
}
