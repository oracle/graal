/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.profdiff.args;

import java.util.Formatter;

import org.graalvm.profdiff.command.Command;

/**
 * Assembles and parses program arguments. This is the root parser of the program. The root parser
 * may contain a command group as the last positional argument.
 */
public class ProgramArgumentParser extends ArgumentParser {
    /**
     * The name of the program.
     */
    private final String prog;

    /**
     * The description of the program.
     */
    private final String description;

    /**
     * Constructs an argument parser.
     *
     * @param prog the name of the program
     * @param description the description of the program
     */
    public ProgramArgumentParser(String prog, String description) {
        this.prog = prog;
        this.description = description;
    }

    /**
     * Gets the name of the program.
     */
    public String getProg() {
        return prog;
    }

    /**
     * Formats a help message for the program. Includes a usage string, the description of the
     * program, and a listing of option/positional/command arguments.
     *
     * @return a help message for the program
     */
    public String formatHelp() {
        Formatter fmt = new Formatter();
        fmt.format("usage: %s", prog);
        if (!optionArguments.isEmpty()) {
            fmt.format(" %s", formatOptionUsage());
        }
        if (!positionalArguments.isEmpty()) {
            fmt.format(" %s", formatPositionalUsage());
        }
        fmt.format("%n%n%s%n", description);
        if (!optionArguments.isEmpty()) {
            fmt.format("%n%s", formatOptionHelp());
        }
        if (!positionalArguments.isEmpty()) {
            fmt.format("%n%s", formatPositionalHelp());
        }
        if (getCommandGroup().isPresent()) {
            fmt.format("%n%s", getCommandGroup().get().formatCommandsHelp());
        }
        return fmt.toString();
    }

    /**
     * Formats a help message for a given command of the program. Includes a usage string, the
     * description of the command, and a listing of option/positional/command arguments for the
     * given command.
     *
     * @param command the command for which the help message is formatted
     * @return a help message for the command
     */
    public String formatHelp(Command command) {
        Formatter fmt = new Formatter();
        fmt.format("usage: %s", prog);
        if (!optionArguments.isEmpty()) {
            fmt.format(" %s", formatOptionUsage());
        }
        if (!positionalArguments.isEmpty()) {
            fmt.format(" %s", formatPositionalUsage(command));
        }
        ArgumentParser commandParser = command.getArgumentParser();
        if (!commandParser.getOptionArguments().isEmpty()) {
            fmt.format(" %s", command.getArgumentParser().formatOptionUsage());
        }
        if (!commandParser.getPositionalArguments().isEmpty()) {
            fmt.format(" %s", commandParser.formatPositionalUsage());
        }
        fmt.format("%n%n%s%n", command.getDescription());
        if (!commandParser.getOptionArguments().isEmpty()) {
            fmt.format("%n%s", commandParser.formatOptionHelp());
        }
        if (!commandParser.getPositionalArguments().isEmpty()) {
            fmt.format("%n%s", commandParser.formatPositionalHelp());
        }
        return fmt.toString();
    }

    /**
     * Adds a positional argument that expects a command name. The caller should populate the
     * returned {@link CommandGroup} with commands. This is a required argument. During parsing, the
     * selected command parses the rest of the arguments (i.e., those values that come after the
     * value of this argument). Only one command group per parser is possible, and it must be the
     * last positional argument.
     *
     * @param name the name of the command group
     * @param help the help message for the command group
     * @return the added command group
     */
    public CommandGroup addCommandGroup(String name, String help) {
        if (name.startsWith(Argument.OPTION_PREFIX)) {
            throw new RuntimeException("Command group must be a positional argument, i.e., the name must not start with " +
                            Argument.OPTION_PREFIX + ".");
        }
        CommandGroup subparserGroup = new CommandGroup(name, help);
        addArgument(subparserGroup);
        return subparserGroup;
    }
}
