/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.command;

import org.graalvm.profdiff.core.HotCompilationUnitPolicy;
import org.graalvm.profdiff.parser.args.ArgumentParser;
import org.graalvm.profdiff.parser.args.ProgramArgumentParser;
import org.graalvm.profdiff.parser.args.StringArgument;
import org.graalvm.profdiff.util.Writer;

/**
 * A command that shows a help message for the whole program or a particular command.
 */
public class HelpCommand implements Command {
    private final ProgramArgumentParser programArgumentParser;

    private final ArgumentParser argumentParser;

    private final StringArgument commandArgument;

    public HelpCommand(ProgramArgumentParser programArgumentParser) {
        this.programArgumentParser = programArgumentParser;
        argumentParser = new ArgumentParser();
        commandArgument = argumentParser.addStringArgument("command", null, "the command to show help for");
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "show help for profdiff or a given command";
    }

    @Override
    public ArgumentParser getArgumentParser() {
        return argumentParser;
    }

    @Override
    public void invoke(Writer writer) {
        if (commandArgument.getValue() == null) {
            writer.writeln(programArgumentParser.formatHelp());
            return;
        }
        Command command = null;
        if (programArgumentParser.getCommandGroup().isPresent()) {
            command = programArgumentParser.getCommandGroup().get().getCommandByName(commandArgument.getValue());
        }
        if (command == null) {
            System.err.printf("%s: unknown command %s%n", programArgumentParser.getProg(), commandArgument.getValue());
            if (programArgumentParser.getCommandGroup().isPresent()) {
                System.err.println(programArgumentParser.getCommandGroup().get().formatCommandsHelp());
            }
            return;
        }
        writer.writeln(programArgumentParser.formatHelp(command));
    }

    @Override
    public void setHotCompilationUnitPolicy(HotCompilationUnitPolicy hotCompilationUnitPolicy) {

    }
}
