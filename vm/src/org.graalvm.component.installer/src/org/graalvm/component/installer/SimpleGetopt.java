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
package org.graalvm.component.installer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import static org.graalvm.component.installer.Commands.DO_NOT_PROCESS_OPTIONS;

/**
 *
 * @author sdedic
 */
public class SimpleGetopt {
    private LinkedList<String> parameters;
    private final Map<String, String> globalOptions;
    private final Map<String, Map<String, String>> commandOptions = new HashMap<>();

    private final Map<String, String> optValues = new HashMap<>();
    private final LinkedList<String> positionalParameters = new LinkedList<>();

    private String command;
    private boolean ignoreUnknownCommands;
    private boolean unknownCommand;

    private List<String> unknownOptions;

    private Map<String, String> abbreviations = new HashMap<>();
    private final Map<String, Map<String, String>> commandAbbreviations = new HashMap<>();

    public SimpleGetopt(Map<String, String> globalOptions) {
        this.globalOptions = globalOptions;
    }

    public void setParameters(LinkedList<String> parameters) {
        this.parameters = parameters;
    }

    public SimpleGetopt ignoreUnknownOptions(boolean ignore) {
        this.unknownOptions = ignore ? new ArrayList<>() : null;
        return this;
    }

    public SimpleGetopt ignoreUnknownCommands(boolean ignore) {
        this.ignoreUnknownCommands = ignore;
        return this;
    }

    public List<String> getUnknownOptions() {
        return unknownOptions == null ? Collections.emptyList() : unknownOptions;
    }

    // overridable by tests
    public RuntimeException err(String messageKey, Object... args) {
        throw ComponentInstaller.err(messageKey, args);
    }

    private String findCommand(String cmdString) {
        String cmd = cmdString;
        if (cmd.isEmpty()) {
            if (ignoreUnknownCommands) {
                return null;
            }
            throw err("ERROR_MissingCommand"); // NOI18N
        }
        String selCommand = null;
        for (String s : commandOptions.keySet()) {
            if (s.startsWith(cmdString)) {
                if (selCommand != null) {
                    throw err("ERROR_AmbiguousCommand", cmdString, selCommand, s);
                }
                selCommand = s;
                if (s.length() == cmdString.length()) {
                    break;
                }
            }
        }
        if (selCommand == null) {
            if (ignoreUnknownCommands) {
                unknownCommand = true;
                command = cmdString;
                return null;
            }
            throw err("ERROR_UnknownCommand", cmdString); // NOI18N
        }
        command = selCommand;
        return command;
    }

    private static final String NO_ABBREV = "**no-abbrev"; // NOI18N

    private boolean hasCommand() {
        return command != null && !unknownCommand;
    }

    @SuppressWarnings("StringEquality")
    Map<String, String> computeAbbreviations(Collection<String> optNames) {
        Map<String, String> result = new HashMap<>();

        for (String o : optNames) {
            if (o.length() < 2) {
                continue;
            }
            result.put(o, NO_ABBREV);
            for (int i = 2; i < o.length(); i++) {
                String s = o.substring(0, i);

                String fullName = result.get(s);
                if (fullName == null) {
                    result.put(s, o);
                } else {
                    result.put(s, NO_ABBREV);
                }
            }
        }
        // final Object noAbbrevMark = NO_ABBREV;
        for (Iterator<Entry<String, String>> ens = result.entrySet().iterator(); ens.hasNext();) {
            Entry<String, String> en = ens.next();
            // cannot use comparison to NO_ABBREV directly because of FindBugs + mx gate combo.
            if (NO_ABBREV.equals(en.getValue())) {
                ens.remove();
            }
        }
        return result;
    }

    Collection<String> getAllOptions() {
        Set<String> s = new HashSet<>();
        s.addAll(globalOptions.keySet());
        for (Map<String, String> cmdOpts : commandOptions.values()) {
            s.addAll(cmdOpts.keySet());
        }
        // discard short option stubs when only long option exists.
        for (Iterator<String> it = s.iterator(); it.hasNext();) {
            String opt = it.next();
            if (opt.length() == 1 && !Character.isLetterOrDigit(opt.charAt(0))) {
                it.remove();
            }
        }
        return s;
    }

    void computeAbbreviations() {
        abbreviations = computeAbbreviations(globalOptions.keySet());

        for (String c : commandOptions.keySet()) {
            Set<String> names = new HashSet<>(commandOptions.get(c).keySet());
            names.addAll(globalOptions.keySet());

            Map<String, String> commandAbbrevs = computeAbbreviations(names);
            commandAbbreviations.put(c, commandAbbrevs);
        }
    }

    public void process() {
        computeAbbreviations();
        while (true) {
            String p = parameters.peek();
            if (p == null) {
                break;
            }
            if (!p.startsWith("-")) { // NOI18N
                if (command == null) {
                    findCommand(parameters.poll());
                    Map<String, String> cOpts = commandOptions.get(command);
                    if (cOpts != null) {
                        for (String s : optValues.keySet()) {
                            if (s.length() > 1) {
                                continue;
                            }
                            if ("X".equals(cOpts.get(s))) {
                                unknownOption(s, command);
                                break;
                            }
                        }
                        if (cOpts.containsKey(DO_NOT_PROCESS_OPTIONS)) { // NOI18N
                            // terminate all processing, the rest are positional params
                            positionalParameters.addAll(parameters);
                            break;
                        }
                    } else {
                        positionalParameters.add(p);
                    }
                } else {
                    positionalParameters.add(parameters.poll());
                }
                continue;
            } else if (p.length() == 1 || "--".equals(p)) {
                // dash alone, or double-dash terminates option search.
                parameters.poll();
                positionalParameters.addAll(parameters);
                break;
            }
            String param = parameters.poll();
            boolean nextParam = p.startsWith("--"); // NOI18N
            String optName;
            int optCharIndex = 1;
            while (optCharIndex < param.length()) {
                if (nextParam) {
                    optName = param.substring(2);
                    param = processOptSpec(optName, optCharIndex, param, nextParam);
                } else {
                    optName = param.substring(optCharIndex, optCharIndex + 1);
                    optCharIndex += optName.length();
                    param = processOptSpec(optName, optCharIndex, param, nextParam);
                }
                // hack: if "help" option (hardcoded) is present, terminate
                if (optValues.get("h") != null) {
                    return;
                }
                if (nextParam) {
                    break;
                }
            }
        }
    }

    private void unknownOption(String option, String cmd) {
        if (unknownOptions == null) {
            if (cmd == null) {
                throw err("ERROR_UnsupportedGlobalOption", option); // NOI18N
            } else {
                throw err("ERROR_UnsupportedOption", option, cmd); // NOI18N
            }
        } else {
            unknownOptions.add(option);
        }

    }

    private String processOptSpec(String o, int optCharIndex, String optParam, boolean nextParam) {
        String param = optParam;
        String optSpec = null;
        String optName = o;
        Map<String, String> cmdSpec = null;

        if (hasCommand()) {
            Map<String, String> cmdAbbrevs = commandAbbreviations.get(command);
            String fullO = cmdAbbrevs.get(optName);
            if (fullO != null) {
                optName = fullO;
            }
            cmdSpec = commandOptions.get(command);
            String c = cmdSpec.get(optName);
            if (c != null && optName.length() > 1) {
                optSpec = cmdSpec.get(c);
                optName = c;
            } else {
                optSpec = c;
            }
        }
        if (optSpec == null) {
            String fullO = abbreviations.get(optName);
            if (fullO != null) {
                optName = fullO;
            }
            String c = globalOptions.get(optName);
            if (c != null && optName.length() > 1) {
                optSpec = globalOptions.get(c);
                optName = c;
            } else {
                optSpec = c;
            }
        }
        if (optSpec != null && optSpec.startsWith("=")) {
            String s = optSpec.substring(1);
            String nspec = null;
            if (cmdSpec != null) {
                nspec = cmdSpec.get(s);
            }
            if (nspec == null) {
                nspec = globalOptions.get(s);
            }
            if (nspec != null) {
                optSpec = nspec;
                optName = s;
            }
        }
        if (optSpec == null) {
            if (unknownCommand) {
                return param;
            }
            unknownOption(optName, command);
            return param;
        }
        // no support for parametrized options now
        String optVal = "";
        switch (optSpec) {
            case "s":
                if (nextParam) {
                    optVal = parameters.poll();
                    if (optVal == null) {
                        throw err("ERROR_OptionNeedsParameter", o, command); // NOI18N
                    }
                } else {
                    if (optCharIndex < param.length()) {
                        optVal = param.substring(optCharIndex);
                        param = "";
                    } else if (parameters.isEmpty()) {
                        throw err("ERROR_OptionNeedsParameter", o, command); // NOI18N
                    } else {
                        optVal = parameters.poll();
                    }
                }
                break;
            case "X":
                unknownOption(o, command);
                return param;
            case "":
                break;
        }
        optValues.put(optName, optVal); // NOI18N
        return param;
    }

    public String getCommand() {
        return command;
    }

    public void addCommandOptions(String commandName, Map<String, String> optSpec) {
        commandOptions.put(commandName, new HashMap<>(optSpec));
    }

    // test only
    void addCommandOption(String commandName, String optName, String optVal) {
        commandOptions.get(commandName).put(optName, optVal);
    }

    public Map<String, String> getOptValues() {
        return optValues;
    }

    public LinkedList<String> getPositionalParameters() {
        return positionalParameters;
    }
}
