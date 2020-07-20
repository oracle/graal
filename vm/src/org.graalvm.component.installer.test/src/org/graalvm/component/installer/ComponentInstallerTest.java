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
package org.graalvm.component.installer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assert;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class ComponentInstallerTest extends CommandTestBase {
    StringBuilder message = new StringBuilder();
    boolean cmdReported;
    String currentCmd;

    void startCommand(String cmd) {
        cmdReported = false;
        currentCmd = cmd;
    }

    void reportOption(String k) {
        if (message.length() > 0) {
            message.append(", ");
        }
        if (!cmdReported) {
            cmdReported = true;
            message.append("Command ").append(currentCmd).append(": ");
        }
        message.append(k);
    }

    /**
     * Checks that no command defines an option that clash with the global one.
     */
    @Test
    public void testOptionClashBetweenCommandAndGlobal() throws Exception {
        ComponentInstaller.initCommands();
        for (String cmd : ComponentInstaller.commands.keySet()) {
            startCommand(cmd);
            InstallerCommand c = ComponentInstaller.commands.get(cmd);
            Map<String, String> opts = c.supportedOptions();
            for (String k : opts.keySet()) {
                String v = opts.get(k);
                if ("X".equals(v)) {
                    continue;
                }
                if (ComponentInstaller.globalOptions.containsKey(k) &&
                                !ComponentInstaller.componentOptions.containsKey(k)) {
                    reportOption(k);
                }
            }
        }
        if (message.length() > 0) {
            Assert.fail("Command options clashes with the global: " + message.toString());
        }
    }

    /**
     * Checks that main/common options are all printed and are consistent.
     */
    @Test
    public void testMainOptionsConsistent() {
        ComponentInstaller.initCommands();
        discoverOptions();
        startCommand("Global");
        String help = ResourceBundle.getBundle(
                        "org.graalvm.component.installer.Bundle").getString("INFO_Usage");
        List<String> lines = new ArrayList<>(Arrays.asList(help.split("\n")));
        while (!lines.get(0).startsWith("Common options:")) {
            lines.remove(0);
        }
        lines.remove(0);
        int index = 0;
        while (index < lines.size()) {
            String tl = lines.get(index).trim();
            if (tl.isEmpty()) {
                break;
            }
            index++;
        }
        lines = lines.subList(0, index);
        Map<String, String> globs = new HashMap<>(ComponentInstaller.globalOptions);
        checkOptions(lines, globs);
        assertTrue("Help inconsistencies found: \n " + String.join("\n", errors), errors.isEmpty());
    }

    /**
     * Checks that the main help reports all commands and all their options.
     */
    @Test
    public void testMainHelpConsistent() {
        ComponentInstaller.initCommands();
        discoverOptions();
        startCommand("Global");
        String help = ResourceBundle.getBundle(
                        "org.graalvm.component.installer.Bundle").getString("INFO_Usage");
        String[] lines = help.split("\n");
        Map<String, InstallerCommand> allCmds = new HashMap<>(ComponentInstaller.commands);
        for (String l : lines) {
            if (!l.startsWith("\tgu ")) {
                continue;
            }
            int oS = l.indexOf('[');
            int oE = l.indexOf(']');
            int sp = l.indexOf(' ', 4);
            String cn = l.substring(4, sp);
            if (cn.startsWith("<")) {
                continue;
            }
            InstallerCommand c = allCmds.remove(cn);
            if (c == null) {
                Assert.fail("Unknown command: " + cn);
            }
            startCommand(cn);
            if (oS == -1 || oE == -1) {
                continue;
            }
            Map<String, String> cmdOptions = new HashMap<>(c.supportedOptions());
            String optString = l.substring(oS + 1, oE);
            if (optString.startsWith("-")) {
                optString = optString.substring(1);
            } else {
                optString = "";
            }
            for (int a = 0; a < optString.length(); a++) {
                char o = optString.charAt(a);
                String s = String.valueOf(o);
                if (cmdOptions.remove(s) == null) {
                    if (!ComponentInstaller.globalOptions.containsKey(s)) {
                        reportOption(s);
                    }
                }
            }
            if (message.length() > 0) {
                Assert.fail("Options do not exist: " + message.toString());
            }
            for (String s : new ArrayList<>(cmdOptions.keySet())) {
                if (s.length() > 1 || "X".equals(cmdOptions.get(s)) || deprecatedOptions.contains(s)) {
                    cmdOptions.remove(s);
                }
            }
            for (String s : cmdOptions.keySet()) {
                reportOption(s);
            }
            if (message.length() > 0) {
                Assert.fail("Options not documented: " + message.toString());
            }
        }
        // filter out "system" commands
        for (Iterator<String> it = allCmds.keySet().iterator(); it.hasNext();) {
            String cmd = it.next();
            if (cmd.startsWith("#")) {
                it.remove();
            }
        }
        if (!allCmds.isEmpty()) {
            Assert.fail("Not all commands documented: " + allCmds);
        }
    }

    List<String> helpLines = new ArrayList<>();
    Set<String> deprecatedOptions = new HashSet<>();
    Set<String> allOptions = new HashSet<>();

    private void discoverOptions() {
        try {
            Field[] flds = Commands.class.getFields();
            for (Field f : flds) {
                if (f.getType() != String.class) {
                    continue;
                }
                if (!(f.getName().startsWith("OPTION_") || f.getName().startsWith("LONG_OPTION_"))) {
                    continue;
                }
                String v = (String) f.get(null);
                allOptions.add(v);
                if (f.getAnnotation(Deprecated.class) != null) {
                    deprecatedOptions.add(v);
                }
            }
        } catch (ReflectiveOperationException ex) {

        }
    }

    List<String> errors = new ArrayList<>();

    private Set<String> checkOptions(List<String> optionLines, Map<String, String> cmdOpts) {
        Set<String> coveredOptions = new HashSet<>();
        for (int i = 0; i < optionLines.size(); i++) {
            String l = optionLines.get(i).trim();
            if (l.startsWith("-")) {
                String[] spl = l.split(",?\\p{Blank}");
                String shOpt = spl[0].trim().substring(1);
                if (shOpt.startsWith("-")) {
                    shOpt = spl[0].trim();
                } else {
                    if (shOpt.length() != 1) {
                        errors.add("Command " + currentCmd + ": Invalid short option: " + shOpt);
                    } else {
                        coveredOptions.add(shOpt);
                    }
                    String def = cmdOpts.get(shOpt);
                    if (def == null) {
                        def = ComponentInstaller.globalOptions.get(shOpt);
                    }
                    if (def == null) {
                        errors.add("Command " + currentCmd + ": Unsupported option: " + shOpt);
                    } else if (deprecatedOptions.contains(shOpt) || def.startsWith("=")) {
                        errors.add("Command " + currentCmd + ": Deperecated option: " + shOpt);
                        continue;
                    }

                    if (spl.length == 1) {
                        errors.add("Command " + currentCmd + ": No explanation for: " + shOpt);
                        continue;
                    }
                    shOpt = spl[1].trim();
                }
                if (shOpt.startsWith("--")) {
                    if (spl.length == 2) {
                        errors.add("Command " + currentCmd + ": No explanation for: " + shOpt);
                        continue;
                    }
                    String longOption = shOpt.substring(2);
                    if (longOption.length() < 2) {
                        errors.add("Command " + currentCmd + ": Long option too short: " + longOption);
                    } else {
                        coveredOptions.add(longOption);
                    }
                    String shopt = cmdOpts.get(longOption);
                    if (shopt == null) {
                        shopt = ComponentInstaller.globalOptions.get(longOption);
                    }
                    if (shopt == null) {
                        errors.add("Command " + currentCmd + ": Long option not found: " + longOption);
                    } else if (!(cmdOpts.containsKey(shopt) || ComponentInstaller.globalOptions.containsKey(shopt))) {
                        errors.add("Command " + currentCmd + ": Long option mapped to bad char: " + longOption);
                    } else if (Character.isLetterOrDigit(shopt.charAt(0))) {
                        if (!l.startsWith("-" + shopt)) {
                            errors.add("Command " + currentCmd + ": Long option with bad short option: " + longOption);
                        }
                    }
                }
            }
        }
        List<String> a = new ArrayList<>(cmdOpts.keySet());
        Collections.sort(a, Collections.reverseOrder());

        for (String s : a) {
            String r = cmdOpts.get(s);
            if (s.length() > 1) {
                r = cmdOpts.get(r);
            }
            if ("X".equals(r)) {
                cmdOpts.remove(s);
            }
        }
        cmdOpts.keySet().removeAll(coveredOptions);
        cmdOpts.keySet().removeAll(deprecatedOptions);
        for (String s : new ArrayList<>(cmdOpts.keySet())) {
            if (!Character.isLetterOrDigit(s.charAt(0))) {
                cmdOpts.remove(s);
            }
        }
        if (!cmdOpts.isEmpty()) {
            errors.add("Command " + currentCmd + ": Option(s) missing in option list - " + cmdOpts.keySet());
        }
        return coveredOptions;
    }

    private void checkCommandAndOptionsList(InstallerCommand cmd) {
        boolean overviewFound = false;
        String prefix = "gu " + currentCmd + " ";
        List<String> optionLines = new ArrayList<>();
        boolean optionBlockStarted = false;
        Set<String> optionsInOverview = new HashSet<>();

        Map<String, String> cmdOpts = new HashMap<>(cmd.supportedOptions());
        cmdOpts.remove(Commands.DO_NOT_PROCESS_OPTIONS);

        Map<String, String> opts = new HashMap<>(cmdOpts);
        for (String l : helpLines) {
            if (l.startsWith(prefix)) {
                if (overviewFound) {
                    errors.add("Command " + currentCmd + ": Duplicate overviews not permitted");
                }
                int optsStart = l.indexOf('[');
                int optsEnd = l.indexOf(']');

                if (optsStart == -1) {
                    if (!opts.isEmpty()) {
                        errors.add("Command " + currentCmd + ": Options block missing");
                    }
                    continue;
                }
                if (optsEnd <= optsStart + 1) {
                    errors.add("Command " + currentCmd + ": Options block malformed");
                }

                String optList = l.substring(optsStart + 1, optsEnd);
                if (optList.startsWith("-")) {
                    optList = optList.substring(1);
                }

                for (int i = 0; i < optList.length(); i++) {
                    String o = optList.substring(i, i + 1);

                    if ("X".equals(opts.get(o))) {
                        errors.add("Command " + currentCmd + ": Disabled option listed - " + o);
                    }

                    if (!(opts.containsKey(o) || ComponentInstaller.globalOptions.containsKey(o))) {
                        errors.add("Command " + currentCmd + ": Unsupported option listed - " + o);
                    }
                    opts.remove(o);
                    optionsInOverview.add(o);
                }

                List<String> oneChars = opts.keySet().stream().filter((s) -> s.length() == 1         // one-liner
                                && !"X".equals(opts.get(s))       // not disabled
                                && !deprecatedOptions.contains(s) // not deprecated
                ).sorted().collect(Collectors.toList());
                if (!oneChars.isEmpty()) {
                    errors.add("Command " + currentCmd + ": Option(s) missing in command overview - " + oneChars);
                }
                overviewFound = true;
            }

            if (l.toLowerCase().endsWith("options:")) {
                optionBlockStarted = true;
            }
            if (optionBlockStarted) {
                if (l.trim().startsWith("-")) {
                    optionLines.add(l.substring(l.indexOf('-')));
                }
            }
        }
        if (!overviewFound) {
            errors.add("Command " + currentCmd + ": Overview line not found");
        }
        checkOptions(optionLines, cmdOpts);
    }

    @Test
    public void testCommandHelpConsistent() throws Exception {
        discoverOptions();

        ComponentInstaller.initCommands();
        Map<String, InstallerCommand> allCmds = new HashMap<>(ComponentInstaller.commands);

        delegateFeedback(new FeedbackAdapter() {
            @Override
            public void output(String bundleKey, Object... params) {
                super.output(bundleKey, params);
                helpLines.addAll(Arrays.asList(reallyl10n(bundleKey, params).split("\n")));
            }

            @Override
            public void message(String bundleKey, Object... params) {
                output(bundleKey, params);
            }

        });
        options.put(Commands.OPTION_HELP, "");
        for (String cmd : allCmds.keySet()) {
            if (cmd.startsWith("#")) {
                continue;
            }
            // exception: rebuild-images delegates help to others:
            if ("rebuild-images".equals(cmd)) {
                continue;
            }

            InstallerCommand cc = allCmds.get(cmd);
            helpLines.clear();
            cc.init(this, this.withBundle(cc.getClass()));
            startCommand(cmd);
            cc.execute();
            checkCommandAndOptionsList(cc);
        }
        assertTrue("Help inconsistencies found: \n " + String.join("\n", errors), errors.isEmpty());
    }
}
