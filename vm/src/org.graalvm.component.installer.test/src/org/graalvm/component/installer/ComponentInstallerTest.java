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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ResourceBundle;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author sdedic
 */
public class ComponentInstallerTest extends TestBase {
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
                if (ComponentInstaller.globalOptions.containsKey(k)) {
                    reportOption(k);
                }
            }
        }
        if (message.length() > 0) {
            Assert.fail("Command options clashes with the global: " + message.toString());
        }
    }

    /**
     * Checks that the main help reports all commands and all their options.
     */
    @Test
    public void testMainHelpConsistent() {
        ComponentInstaller.initCommands();
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
                if (s.length() > 1 || "X".equals(cmdOptions.get(s))) {
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
}
