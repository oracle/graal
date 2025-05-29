/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2024, 2024, Red Hat Inc. All rights reserved.
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

package com.oracle.svm.core.dcmd;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.BasedOnJDKFile;

@BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/diagnosticCommand.hpp#L42-L57")
public class HelpDCmd extends AbstractDCmd {
    private static final DCmdOption<String> COMMAND_NAME = new DCmdOption<>(String.class, "command name", "The name of the command for which we want help", false, null);
    private static final DCmdOption<Boolean> PRINT_ALL = new DCmdOption<>(Boolean.class, "-all", "Show help for all commands", false, false);

    @Platforms(Platform.HOSTED_ONLY.class)
    public HelpDCmd() {
        super("help", "For more information about a specific command use 'help <command>'. With no argument this will show a list of available commands. 'help -all' will show help for all commands.",
                        Impact.Low, new DCmdOption<?>[]{COMMAND_NAME}, new DCmdOption<?>[]{PRINT_ALL},
                        new String[]{
                                        "$ jcmd <pid> help Thread.dump_to_file"
                        });
    }

    @Override
    @BasedOnJDKFile("https://github.com/openjdk/jdk/blob/jdk-24+18/src/hotspot/share/services/diagnosticCommand.cpp#L188-L232")
    public String execute(DCmdArguments args) throws Throwable {
        if (args.get(PRINT_ALL)) {
            String lineBreak = System.lineSeparator();
            StringBuilder response = new StringBuilder();
            for (DCmd cmd : DCmdSupport.singleton().getCommands()) {
                response.append(cmd.getName()).append(lineBreak);
                response.append("\t").append(cmd.getDescription()).append(lineBreak);
                response.append(lineBreak);
            }
            return response.toString();
        }

        String commandName = args.get(COMMAND_NAME);
        if (commandName == null) {
            String lineBreak = System.lineSeparator();
            StringBuilder response = new StringBuilder("The following commands are available:").append(lineBreak);
            for (DCmd cmd : DCmdSupport.singleton().getCommands()) {
                response.append(cmd.getName()).append(lineBreak);
            }
            response.append(lineBreak);
            response.append("For more information about a specific command use 'help <command>'.");
            return response.toString();
        }

        DCmd cmd = DCmdSupport.singleton().getCommand(commandName);
        if (cmd == null) {
            throw new IllegalArgumentException("Help unavailable : '" + commandName + "' : No such command");
        }
        return cmd.getHelp();
    }
}
